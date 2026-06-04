package com.liuguang.media.data.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.liuguang.media.data.local.dao.RadioSourceDao
import com.liuguang.media.data.local.entity.RadioSourceEntity
import com.liuguang.media.domain.model.RadioStation
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class RadioRepository @Inject constructor(
    private val radioSourceDao: RadioSourceDao,
    private val okHttpClient: OkHttpClient,
    private val networkSettingsRepository: NetworkSettingsRepository
) {
    companion object {
        private const val MAX_PARSED_STATIONS = 2_000
        private const val STATION_CACHE_TTL_MS = 10 * 60 * 1_000L
        private val TVG_NAME_REGEX = """tvg-name="([^"]+)"""".toRegex()
        private val TVG_LOGO_REGEX = """tvg-logo="([^"]+)"""".toRegex()
        private val GROUP_TITLE_REGEX = """group-title="([^"]+)"""".toRegex()
        private val PLS_FILE_REGEX = """File\d+=(.+)""".toRegex(RegexOption.IGNORE_CASE)
        private val PLS_TITLE_REGEX = """Title\d+=(.+)""".toRegex(RegexOption.IGNORE_CASE)
    }

    private data class CachedStations(
        val stations: List<RadioStation>,
        val timestampMs: Long
    )

    private val stationCache = ConcurrentHashMap<String, CachedStations>()

    fun observeAllSources(): Flow<List<RadioSourceEntity>> = radioSourceDao.observeAll()

    suspend fun getEnabledSources(): List<RadioSourceEntity> = radioSourceDao.getEnabled()

    suspend fun getAllSources(): List<RadioSourceEntity> = radioSourceDao.getAll()

    suspend fun insertSource(source: RadioSourceEntity): Long = radioSourceDao.insert(source)

    suspend fun insertSources(sources: List<RadioSourceEntity>): List<Long> = radioSourceDao.insertAll(sources)

    suspend fun updateSource(source: RadioSourceEntity) = radioSourceDao.update(source)

    suspend fun updateSources(sources: List<RadioSourceEntity>) = radioSourceDao.updateAll(sources)

    suspend fun deleteSource(source: RadioSourceEntity) = radioSourceDao.delete(source)

    suspend fun clearAllSources() {
        radioSourceDao.clearAll()
        stationCache.clear()
    }

    suspend fun resetToDefaults() {
        radioSourceDao.clearAll()
        stationCache.clear()
    }

    suspend fun moveSourceUp(source: RadioSourceEntity, allSources: List<RadioSourceEntity>) {
        val currentIndex = allSources.indexOfFirst { it.id == source.id }
        if (currentIndex > 0) {
            val previous = allSources[currentIndex - 1]
            radioSourceDao.update(source.copy(sortOrder = previous.sortOrder))
            radioSourceDao.update(previous.copy(sortOrder = source.sortOrder))
        }
    }

    suspend fun moveSourceDown(source: RadioSourceEntity, allSources: List<RadioSourceEntity>) {
        val currentIndex = allSources.indexOfFirst { it.id == source.id }
        if (currentIndex in 0 until allSources.lastIndex) {
            val next = allSources[currentIndex + 1]
            radioSourceDao.update(source.copy(sortOrder = next.sortOrder))
            radioSourceDao.update(next.copy(sortOrder = source.sortOrder))
        }
    }

    suspend fun fetchAndParseStations(
        url: String,
        timeoutMs: Long = networkSettingsRepository.currentSettings().liveSourceTimeoutMs,
        forceRefresh: Boolean = false
    ): Result<List<RadioStation>> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            if (!forceRefresh) {
                stationCache[url]
                    ?.takeIf { now - it.timestampMs <= STATION_CACHE_TTL_MS }
                    ?.let { return@withContext Result.success(it.stations) }
            }

            if (isRawAudioStreamUrl(url)) {
                val stations = listOf(stationFromDirectAudioUrl(url))
                stationCache[url] = CachedStations(stations, now)
                return@withContext Result.success(stations)
            }

            withTimeout(timeoutMs.milliseconds) {
                val response = executeSourceRequest(url, timeoutMs)
                response.use {
                    if (!it.isSuccessful) {
                        return@withTimeout Result.failure(
                            SourceHttpException(
                                statusCode = it.code,
                                message = "HTTP ${it.code}",
                                rawContent = it.body?.string().orEmpty()
                            )
                        )
                    }

                    val contentType = it.header("Content-Type")
                    val stations = if (isDirectAudioResponse(url, contentType)) {
                        listOf(stationFromDirectAudioUrl(url))
                    } else {
                        val content = it.body?.string().orEmpty()
                        parseRadioContent(content, url)
                    }
                    stationCache[url] = CachedStations(stations, System.currentTimeMillis())
                    Result.success(stations)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkRadioSource(
        url: String,
        timeoutMs: Long = networkSettingsRepository.currentSettings().liveSourceTimeoutMs
    ): Result<RadioSourceCheckResponse> = withContext(Dispatchers.IO) {
        try {
            withTimeout(timeoutMs.milliseconds) {
                val startedAt = System.currentTimeMillis()
                if (isRawAudioStreamUrl(url)) {
                    return@withTimeout checkDirectAudioSource(url, timeoutMs)
                }

                executeSourceRequest(url, timeoutMs).use { response ->
                    if (!response.isSuccessful) {
                        val content = response.body?.string().orEmpty()
                        return@withTimeout Result.failure(
                            SourceHttpException(
                                statusCode = response.code,
                                message = "HTTP ${response.code}",
                                rawContent = content
                            )
                        )
                    }

                    val contentType = response.header("Content-Type")
                    if (isDirectAudioResponse(url, contentType)) {
                        val stations = listOf(stationFromDirectAudioUrl(url))
                        val completedAt = System.currentTimeMillis()
                        stationCache[url] = CachedStations(stations, completedAt)
                        return@withTimeout Result.success(
                            RadioSourceCheckResponse(
                                httpCode = response.code,
                                contentType = contentType,
                                rawContent = directAudioRawContent(url, contentType),
                                stations = stations,
                                latencyMs = (completedAt - startedAt).coerceAtLeast(1L)
                            )
                        )
                    }

                    val content = response.body?.string().orEmpty()
                    if (content.isBlank()) {
                        return@withTimeout Result.failure(
                            SourceDataException("接口返回内容为空", rawContent = content)
                        )
                    }

                    val stations = parseRadioContent(content, url)
                    if (stations.isEmpty()) {
                        return@withTimeout Result.failure(
                            SourceDataException("接口返回内容无法解析出电台", rawContent = content)
                        )
                    }
                    val completedAt = System.currentTimeMillis()
                    stationCache[url] = CachedStations(stations, completedAt)
                    Result.success(
                        RadioSourceCheckResponse(
                            httpCode = response.code,
                            contentType = response.header("Content-Type"),
                            rawContent = content,
                            stations = stations,
                            latencyMs = (completedAt - startedAt).coerceAtLeast(1L)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeSourceRequest(url: String, timeoutMs: Long): okhttp3.Response {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Liuguang/1.0 Android Radio")
            .build()
        return okHttpClient.newCall(request).apply {
            timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
        }.execute()
    }

    private fun executeAudioProbeRequest(url: String, timeoutMs: Long): okhttp3.Response {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Liuguang/1.0 Android Radio")
            .header("Range", "bytes=0-0")
            .build()
        return okHttpClient.newCall(request).apply {
            timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
        }.execute()
    }

    private fun checkDirectAudioSource(
        url: String,
        timeoutMs: Long
    ): Result<RadioSourceCheckResponse> {
        val startedAt = System.currentTimeMillis()
        return executeAudioProbeRequest(url, timeoutMs).use { response ->
            val contentType = response.header("Content-Type")
            if (!response.isSuccessful) {
                return@use Result.failure(
                    SourceHttpException(
                        statusCode = response.code,
                        message = "HTTP ${response.code}",
                        rawContent = directAudioRawContent(url, contentType)
                    )
                )
            }

            val stations = listOf(stationFromDirectAudioUrl(url))
            val completedAt = System.currentTimeMillis()
            stationCache[url] = CachedStations(stations, completedAt)
            Result.success(
                RadioSourceCheckResponse(
                    httpCode = response.code,
                    contentType = contentType,
                    rawContent = directAudioRawContent(url, contentType),
                    stations = stations,
                    latencyMs = (completedAt - startedAt).coerceAtLeast(1L)
                )
            )
        }
    }

    private fun parseRadioContent(content: String, sourceUrl: String): List<RadioStation> {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return emptyList()

        val stations = when {
            trimmed.startsWith("[") || trimmed.startsWith("{") -> parseJsonStations(trimmed)
            trimmed.contains("[playlist]", ignoreCase = true) || PLS_FILE_REGEX.containsMatchIn(trimmed) -> parsePlsStations(trimmed)
            trimmed.contains("#EXTM3U", ignoreCase = true) || trimmed.contains("#EXTINF:", ignoreCase = true) -> parseM3uStations(trimmed)
            else -> parseSimpleStations(trimmed)
        }

        if (stations.isNotEmpty()) return stations.take(MAX_PARSED_STATIONS)

        return if (isDirectAudioUrl(sourceUrl)) {
            listOf(stationFromDirectAudioUrl(sourceUrl))
        } else {
            emptyList()
        }
    }

    private fun parseJsonStations(content: String): List<RadioStation> {
        val root = runCatching { JsonParser.parseString(content) }.getOrNull() ?: return emptyList()
        val array = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject && root.asJsonObject.get("stations")?.isJsonArray == true -> root.asJsonObject.getAsJsonArray("stations")
            root.isJsonObject && root.asJsonObject.get("list")?.isJsonArray == true -> root.asJsonObject.getAsJsonArray("list")
            else -> JsonArray()
        }

        return array
            .asSequence()
            .take(MAX_PARSED_STATIONS)
            .mapNotNull { element ->
                val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val url = item.firstString("url_resolved", "url", "stream", "streamUrl", "playUrl").orEmpty()
                if (!url.startsWith("http", ignoreCase = true)) return@mapNotNull null
                val name = item.firstString("name", "title", "stationName").orEmpty().trim()
                if (name.isBlank()) return@mapNotNull null
                RadioStation(
                    name = name,
                    url = url,
                    group = item.firstString("tags", "group", "category", "genre")
                        ?.split(',', '，')
                        ?.firstOrNull()
                        ?.trim()
                        ?.ifBlank { null }
                        ?: item.firstString("country", "language").orEmpty().ifBlank { "默认" },
                    codec = item.firstString("codec", "format").orEmpty().ifBlank { inferRadioCodec(url) },
                    bitrate = item.firstString("bitrate")?.toIntOrNull() ?: 0,
                    logo = item.firstString("favicon", "logo", "tvg_logo").orEmpty(),
                    country = item.firstString("country", "countrycode").orEmpty()
                )
            }
            .distinctBy { it.url }
            .toList()
    }

    private fun parseM3uStations(content: String): List<RadioStation> {
        val stations = mutableListOf<RadioStation>()
        var currentName: String? = null
        var currentGroup = "默认"
        var currentLogo = ""

        for (line in content.lineSequence()) {
            if (stations.size >= MAX_PARSED_STATIONS) break
            val trimmed = line.trim()
            when {
                trimmed.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val label = trimmed.substringAfterLast(",", "").trim()
                    currentName = TVG_NAME_REGEX.find(trimmed)?.groupValues?.get(1)?.trim()
                        ?: label.ifBlank { null }
                    currentLogo = TVG_LOGO_REGEX.find(trimmed)?.groupValues?.get(1).orEmpty()
                    currentGroup = GROUP_TITLE_REGEX.find(trimmed)?.groupValues?.get(1)?.trim()?.ifBlank { null } ?: "默认"
                }
                trimmed.startsWith("http", ignoreCase = true) && currentName != null -> {
                    stations += RadioStation(
                        name = currentName.orEmpty(),
                        url = trimmed,
                        group = currentGroup,
                        codec = inferRadioCodec(trimmed),
                        logo = currentLogo
                    )
                    currentName = null
                    currentLogo = ""
                    currentGroup = "默认"
                }
                trimmed.contains(",") && !trimmed.startsWith("#") -> {
                    val parts = trimmed.split(",", limit = 2)
                    if (parts.size == 2 && parts[1].startsWith("http", ignoreCase = true)) {
                        stations += RadioStation(
                            name = parts[0].trim(),
                            url = parts[1].trim(),
                            group = currentGroup,
                            codec = inferRadioCodec(parts[1])
                        )
                    }
                }
            }
        }

        return stations.distinctBy { it.url }
    }

    private fun parsePlsStations(content: String): List<RadioStation> {
        val files = linkedMapOf<Int, String>()
        val titles = linkedMapOf<Int, String>()
        content.lineSequence().forEach { line ->
            PLS_FILE_REGEX.matchEntire(line.trim())?.let { match ->
                val key = line.substringAfter("File", "").substringBefore("=").toIntOrNull()
                if (key != null) files[key] = match.groupValues[1].trim()
            }
            PLS_TITLE_REGEX.matchEntire(line.trim())?.let { match ->
                val key = line.substringAfter("Title", "").substringBefore("=").toIntOrNull()
                if (key != null) titles[key] = match.groupValues[1].trim()
            }
        }
        return files.entries
            .asSequence()
            .take(MAX_PARSED_STATIONS)
            .filter { it.value.startsWith("http", ignoreCase = true) }
            .map { (index, url) ->
                RadioStation(
                    name = titles[index]?.takeIf { it.isNotBlank() } ?: stationNameFromUrl(url),
                    url = url,
                    group = "默认",
                    codec = inferRadioCodec(url)
                )
            }
            .distinctBy { it.url }
            .toList()
    }

    private fun parseSimpleStations(content: String): List<RadioStation> {
        return content.lineSequence()
            .take(MAX_PARSED_STATIONS)
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isBlank() || trimmed.startsWith("#")) return@mapNotNull null
                val parts = trimmed.split(",", limit = 2)
                when {
                    parts.size == 2 && parts[1].startsWith("http", ignoreCase = true) -> {
                        RadioStation(
                            name = parts[0].trim(),
                            url = parts[1].trim(),
                            group = "默认",
                            codec = inferRadioCodec(parts[1])
                        )
                    }
                    trimmed.startsWith("http", ignoreCase = true) && isDirectAudioUrl(trimmed) -> {
                        RadioStation(
                            name = stationNameFromUrl(trimmed),
                            url = trimmed,
                            group = "默认",
                            codec = inferRadioCodec(trimmed)
                        )
                    }
                    else -> null
                }
            }
            .distinctBy { it.url }
            .toList()
    }

    private fun JsonObject.firstString(vararg names: String): String? {
        return names.firstNotNullOfOrNull { name ->
            get(name)
                ?.takeIf { !it.isJsonNull }
                ?.let { element ->
                    runCatching { element.asString }.getOrNull()
                }
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
    }

    private fun isDirectAudioUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.US).substringBefore("?")
        return lower.endsWith(".mp3") ||
            lower.endsWith(".aac") ||
            lower.endsWith(".ogg") ||
            lower.endsWith(".opus") ||
            lower.endsWith(".flac") ||
            lower.endsWith(".m4a") ||
            lower.endsWith(".m3u8") ||
            lower.endsWith(".pls")
    }

    private fun isRawAudioStreamUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.US).substringBefore("?")
        return lower.endsWith(".mp3") ||
            lower.endsWith(".aac") ||
            lower.endsWith(".ogg") ||
            lower.endsWith(".opus") ||
            lower.endsWith(".flac") ||
            lower.endsWith(".m4a")
    }

    private fun isDirectAudioResponse(url: String, contentType: String?): Boolean {
        return isRawAudioStreamUrl(url) || isAudioStreamContentType(contentType)
    }

    private fun isAudioStreamContentType(contentType: String?): Boolean {
        val lower = contentType?.lowercase(Locale.US).orEmpty()
        if (lower.isBlank()) return false
        val isPlaylist = lower.contains("mpegurl") ||
            lower.contains("m3u") ||
            lower.contains("pls") ||
            lower.contains("scpls")
        return lower.startsWith("audio/") && !isPlaylist
    }

    private fun stationFromDirectAudioUrl(url: String): RadioStation {
        return RadioStation(
            name = stationNameFromUrl(url),
            url = url,
            group = "默认",
            codec = inferRadioCodec(url)
        )
    }

    private fun directAudioRawContent(url: String, contentType: String?): String {
        return buildString {
            append("直连音频流探测成功")
            append("\n地址：")
            append(url)
            append("\n内容类型：")
            append(contentType ?: "未知")
        }
    }

    private fun inferRadioCodec(url: String): String {
        val lower = url.lowercase(Locale.US).substringBefore("?")
        return when {
            lower.endsWith(".mp3") -> "MP3"
            lower.endsWith(".aac") -> "AAC"
            lower.endsWith(".ogg") -> "OGG"
            lower.endsWith(".opus") -> "OPUS"
            lower.endsWith(".flac") -> "FLAC"
            lower.endsWith(".m4a") -> "M4A"
            lower.endsWith(".m3u8") -> "HLS"
            lower.endsWith(".pls") -> "PLS"
            else -> "AUDIO"
        }
    }

    private fun stationNameFromUrl(url: String): String {
        val uri = android.net.Uri.parse(url)
        return uri.host
            ?.split('.')
            ?.firstOrNull { it.length > 2 && it !in setOf("www", "live", "radio", "stream") }
            ?: "网络电台"
    }
}
