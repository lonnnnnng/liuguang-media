package com.liuguang.media.data.repository

import com.liuguang.media.data.local.dao.LiveSourceDao
import com.liuguang.media.data.local.DefaultSources
import com.liuguang.media.data.local.entity.LiveSourceEntity
import com.liuguang.media.domain.model.LiveChannel
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveRepository @Inject constructor(
    private val liveSourceDao: LiveSourceDao,
    private val okHttpClient: OkHttpClient,
    private val networkSettingsRepository: NetworkSettingsRepository
) {
    companion object {
        private const val MAX_PARSED_CHANNELS = 5_000
        private const val CHANNEL_CACHE_TTL_MS = 10 * 60 * 1_000L
        private val TVG_NAME_REGEX = """tvg-name="([^"]+)"""".toRegex()
        private val TVG_LOGO_REGEX = """tvg-logo="([^"]+)"""".toRegex()
        private val GROUP_TITLE_REGEX = """group-title="([^"]+)"""".toRegex()
    }

    private data class CachedChannels(
        val channels: List<LiveChannel>,
        val timestampMs: Long
    )

    private val channelCache = ConcurrentHashMap<String, CachedChannels>()

    fun observeAllSources(): Flow<List<LiveSourceEntity>> = liveSourceDao.observeAll()

    suspend fun getEnabledSources(): List<LiveSourceEntity> = liveSourceDao.getEnabled()

    suspend fun insertSource(source: LiveSourceEntity): Long = liveSourceDao.insert(source)

    suspend fun updateSource(source: LiveSourceEntity) = liveSourceDao.update(source)

    suspend fun deleteSource(source: LiveSourceEntity) = liveSourceDao.delete(source)

    suspend fun clearAllSources() = liveSourceDao.clearAll()

    suspend fun resetToDefaults() {
        liveSourceDao.clearAll()
        DefaultSources.liveSources.forEach { source ->
            liveSourceDao.insert(source)
        }
    }

    suspend fun moveSourceUp(source: LiveSourceEntity, allSources: List<LiveSourceEntity>) {
        val currentIndex = allSources.indexOfFirst { it.id == source.id }
        if (currentIndex > 0) {
            val prevSource = allSources[currentIndex - 1]
            liveSourceDao.update(source.copy(sortOrder = prevSource.sortOrder))
            liveSourceDao.update(prevSource.copy(sortOrder = source.sortOrder))
        }
    }

    suspend fun moveSourceDown(source: LiveSourceEntity, allSources: List<LiveSourceEntity>) {
        val currentIndex = allSources.indexOfFirst { it.id == source.id }
        if (currentIndex < allSources.size - 1) {
            val nextSource = allSources[currentIndex + 1]
            liveSourceDao.update(source.copy(sortOrder = nextSource.sortOrder))
            liveSourceDao.update(nextSource.copy(sortOrder = source.sortOrder))
        }
    }

    suspend fun fetchAndParseChannels(
        url: String,
        timeoutMs: Long = networkSettingsRepository.currentSettings().liveSourceTimeoutMs,
        forceRefresh: Boolean = false
    ): Result<List<LiveChannel>> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            if (!forceRefresh) {
                channelCache[url]
                    ?.takeIf { now - it.timestampMs <= CHANNEL_CACHE_TTL_MS }
                    ?.let { return@withContext Result.success(it.channels) }
            }

            withTimeout(timeoutMs.milliseconds) {
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).apply {
                    timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
                }.execute()

                response.use {
                    if (!it.isSuccessful) {
                        return@withTimeout Result.failure(Exception("HTTP ${it.code}"))
                    }

                    val content = it.body?.string() ?: ""
                    val channels = parseM3U(content, url)
                    channelCache[url] = CachedChannels(channels, System.currentTimeMillis())
                    Result.success(channels)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkLiveSource(
        url: String,
        timeoutMs: Long = networkSettingsRepository.currentSettings().liveSourceTimeoutMs
    ): Result<LiveSourceCheckResponse> = withContext(Dispatchers.IO) {
        try {
            withTimeout(timeoutMs.milliseconds) {
                val request = Request.Builder().url(url).build()
                okHttpClient.newCall(request).apply {
                    timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
                }.execute().use { response ->
                    val content = response.body?.string().orEmpty()

                    if (!response.isSuccessful) {
                        return@withTimeout Result.failure(
                            SourceHttpException(
                                statusCode = response.code,
                                message = "HTTP ${response.code}",
                                rawContent = content
                            )
                        )
                    }

                    if (content.isBlank()) {
                        return@withTimeout Result.failure(
                            SourceDataException("接口返回内容为空", rawContent = content)
                        )
                    }

                    val channels = parseM3U(content, url)
                    if (channels.isEmpty()) {
                        return@withTimeout Result.failure(
                            SourceDataException("接口返回内容无法解析出频道", rawContent = content)
                        )
                    }
                    channelCache[url] = CachedChannels(channels, System.currentTimeMillis())

                    Result.success(
                        LiveSourceCheckResponse(
                            httpCode = response.code,
                            contentType = response.header("Content-Type"),
                            rawContent = content,
                            channels = channels
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseM3U(content: String, sourceUrl: String): List<LiveChannel> {
        val channels = mutableListOf<LiveChannel>()
        var currentGroup = "默认"
        var currentName: String? = null
        var currentLogo = ""

        for (line in content.lineSequence()) {
            if (channels.size >= MAX_PARSED_CHANNELS) break
            val trimmed = line.trim()
            when {
                trimmed.startsWith("#EXTINF:") -> {
                    val nameMatch = TVG_NAME_REGEX.find(trimmed)
                    val logoMatch = TVG_LOGO_REGEX.find(trimmed)
                    val groupMatch = GROUP_TITLE_REGEX.find(trimmed)
                    val labelMatch = trimmed.substringAfterLast(",", "")

                    currentName = nameMatch?.groupValues?.get(1) ?: labelMatch.ifBlank { null }
                    currentLogo = logoMatch?.groupValues?.get(1).orEmpty()
                    currentGroup = groupMatch?.groupValues?.get(1) ?: "默认"
                }
                trimmed.startsWith("http") && currentName != null -> {
                    val format = when {
                        trimmed.contains(".m3u8") -> "m3u8"
                        trimmed.contains(".flv") -> "flv"
                        else -> "unknown"
                    }
                    channels.add(LiveChannel(currentName, trimmed, currentGroup, format, currentLogo))
                    currentName = null
                    currentLogo = ""
                }
                trimmed.contains(",") && !trimmed.startsWith("#") -> {
                    // Simple format: 频道名,http://url
                    val parts = trimmed.split(",", limit = 2)
                    if (parts.size == 2 && parts[1].startsWith("http")) {
                        val format = when {
                            parts[1].contains(".m3u8") -> "m3u8"
                            parts[1].contains(".flv") -> "flv"
                            else -> "unknown"
                        }
                        channels.add(LiveChannel(parts[0], parts[1], currentGroup, format))
                    }
                }
            }
        }

        if (channels.isEmpty() && isDirectHlsPlaylist(content, sourceUrl)) {
            channels.add(LiveChannel("播放测试", sourceUrl, "测试频道", "m3u8"))
        }

        return channels
    }

    private fun isDirectHlsPlaylist(content: String, sourceUrl: String): Boolean {
        return sourceUrl.contains(".m3u8", ignoreCase = true) &&
            content.contains("#EXTM3U", ignoreCase = true) &&
            (
                content.contains("#EXT-X-STREAM-INF", ignoreCase = true) ||
                    content.contains("#EXT-X-TARGETDURATION", ignoreCase = true)
            )
    }
}
