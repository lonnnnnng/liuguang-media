package com.liuguang.media.data.repository

import com.liuguang.media.domain.model.PodcastEpisode
import com.liuguang.media.domain.model.PodcastFeed
import com.liuguang.media.domain.model.PodcastLibraryEpisode
import com.liuguang.media.data.local.dao.PodcastSubscriptionDao
import com.liuguang.media.data.local.entity.PodcastSubscriptionEntity
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

@Singleton
class PodcastRepository @Inject constructor(
    private val podcastSubscriptionDao: PodcastSubscriptionDao,
    private val okHttpClient: OkHttpClient
) {
    private companion object {
        const val PODCAST_LIBRARY_PARALLELISM = 4
    }

    fun observeSubscriptions(): Flow<List<PodcastSubscriptionEntity>> = podcastSubscriptionDao.observeAll()

    suspend fun getAllSubscriptions(): List<PodcastSubscriptionEntity> = podcastSubscriptionDao.getAll()

    suspend fun insertSubscriptions(subscriptions: List<PodcastSubscriptionEntity>): List<Long> =
        withContext(Dispatchers.IO) {
            podcastSubscriptionDao.insertAll(subscriptions)
        }

    suspend fun updateSubscriptionRaw(subscription: PodcastSubscriptionEntity) = withContext(Dispatchers.IO) {
        podcastSubscriptionDao.update(subscription)
    }

    suspend fun updateSubscriptions(subscriptions: List<PodcastSubscriptionEntity>) = withContext(Dispatchers.IO) {
        podcastSubscriptionDao.updateAll(subscriptions)
    }

    suspend fun clearAllSubscriptions() = withContext(Dispatchers.IO) {
        podcastSubscriptionDao.clearAll()
    }

    suspend fun addSubscription(url: String): Result<PodcastSubscriptionEntity> = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        fetchPodcastFeed(trimmedUrl).fold(
            onSuccess = { feed ->
                val now = System.currentTimeMillis()
                val existing = podcastSubscriptionDao.getByUrl(trimmedUrl)
                val subscription = PodcastSubscriptionEntity(
                    id = existing?.id ?: 0L,
                    title = feed.title,
                    url = trimmedUrl,
                    description = feed.description,
                    imageUrl = feed.imageUrl,
                    link = feed.link,
                    episodeCount = feed.episodes.size,
                    lastRefreshTime = now,
                    sortOrder = existing?.sortOrder ?: podcastSubscriptionDao.maxSortOrder() + 1,
                    enabled = existing?.enabled ?: true,
                    lastCheckStatus = "可用",
                    lastCheckTime = now
                )
                val id = podcastSubscriptionDao.insert(subscription)
                Result.success(subscription.copy(id = existing?.id ?: id))
            },
            onFailure = { error -> Result.failure(error) }
        )
    }

    suspend fun deleteSubscription(subscription: PodcastSubscriptionEntity) = withContext(Dispatchers.IO) {
        podcastSubscriptionDao.delete(subscription)
    }

    suspend fun updateSubscription(subscription: PodcastSubscriptionEntity): Result<PodcastSubscriptionEntity> = withContext(Dispatchers.IO) {
        fetchPodcastFeed(subscription.url).fold(
            onSuccess = { feed ->
                val refreshed = subscription.copy(
                    title = feed.title,
                    description = feed.description,
                    imageUrl = feed.imageUrl,
                    link = feed.link,
                    episodeCount = feed.episodes.size,
                    lastRefreshTime = System.currentTimeMillis(),
                    lastCheckStatus = "可用",
                    lastCheckTime = System.currentTimeMillis()
                )
                podcastSubscriptionDao.update(refreshed)
                Result.success(refreshed)
            },
            onFailure = { error -> Result.failure(error) }
        )
    }

    suspend fun refreshSubscription(subscription: PodcastSubscriptionEntity): Result<PodcastSubscriptionEntity> = withContext(Dispatchers.IO) {
        fetchPodcastFeed(subscription.url).fold(
            onSuccess = { feed ->
                val refreshed = subscription.copy(
                    title = feed.title,
                    description = feed.description,
                    imageUrl = feed.imageUrl,
                    link = feed.link,
                    episodeCount = feed.episodes.size,
                    lastRefreshTime = System.currentTimeMillis(),
                    lastCheckStatus = "可用",
                    lastCheckTime = System.currentTimeMillis()
                )
                podcastSubscriptionDao.update(refreshed)
                Result.success(refreshed)
            },
            onFailure = { error -> Result.failure(error) }
        )
    }

    suspend fun fetchLibraryEpisodes(limitPerFeed: Int = 8): Result<List<PodcastLibraryEpisode>> = withContext(Dispatchers.IO) {
        try {
            val subscriptions = podcastSubscriptionDao.getEnabled()
            val semaphore = Semaphore(PODCAST_LIBRARY_PARALLELISM)
            val episodes = coroutineScope {
                subscriptions.map { subscription ->
                    async {
                        semaphore.withPermit {
                            fetchPodcastFeed(subscription.url).getOrNull()
                                ?.episodes
                                ?.take(limitPerFeed)
                                ?.map { episode ->
                                    PodcastLibraryEpisode(
                                        subscriptionId = subscription.id,
                                        feedTitle = subscription.title,
                                        feedImageUrl = subscription.imageUrl,
                                        episode = episode
                                    )
                                }
                                .orEmpty()
                        }
                    }
                }.awaitAll().flatten()
            }
            Result.success(
                episodes.sortedByDescending { episode -> parsePubDateMillis(episode.episode.pubDate) }.take(80)
            )
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun fetchPodcastFeed(url: String): Result<PodcastFeed> = withContext(Dispatchers.IO) {
        try {
            Result.success(fetchPodcastFeedResponse(url).feed)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun fetchPodcastImportList(
        url: String,
        timeoutMs: Long = 10_000L
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        if (!trimmedUrl.startsWith("http", ignoreCase = true)) {
            return@withContext Result.failure(IllegalArgumentException("请输入有效的清单 URL"))
        }

        try {
            val request = Request.Builder()
                .url(trimmedUrl)
                .header("User-Agent", "Liuguang/1.0 Android Podcast Import")
                .build()
            okHttpClient.newCall(request).apply {
                timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
            }.execute().use { response ->
                val content = response.body?.string().orEmpty()
                when {
                    !response.isSuccessful -> Result.failure(
                        IllegalStateException("清单请求失败：HTTP ${response.code}")
                    )
                    content.isBlank() -> Result.failure(
                        IllegalStateException("清单内容为空")
                    )
                    else -> Result.success(content)
                }
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun checkPodcastSource(
        url: String,
        timeoutMs: Long = 10_000L
    ): Result<PodcastSourceCheckResponse> = withContext(Dispatchers.IO) {
        try {
            Result.success(fetchPodcastFeedResponse(url, timeoutMs))
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun moveSubscriptionUp(
        subscription: PodcastSubscriptionEntity,
        allSubscriptions: List<PodcastSubscriptionEntity>
    ) = withContext(Dispatchers.IO) {
        val currentIndex = allSubscriptions.indexOfFirst { it.id == subscription.id }
        if (currentIndex > 0) {
            val previous = allSubscriptions[currentIndex - 1]
            podcastSubscriptionDao.update(subscription.copy(sortOrder = previous.sortOrder))
            podcastSubscriptionDao.update(previous.copy(sortOrder = subscription.sortOrder))
        }
    }

    suspend fun moveSubscriptionDown(
        subscription: PodcastSubscriptionEntity,
        allSubscriptions: List<PodcastSubscriptionEntity>
    ) = withContext(Dispatchers.IO) {
        val currentIndex = allSubscriptions.indexOfFirst { it.id == subscription.id }
        if (currentIndex in 0 until allSubscriptions.lastIndex) {
            val next = allSubscriptions[currentIndex + 1]
            podcastSubscriptionDao.update(subscription.copy(sortOrder = next.sortOrder))
            podcastSubscriptionDao.update(next.copy(sortOrder = subscription.sortOrder))
        }
    }

    private fun fetchPodcastFeedResponse(
        url: String,
        timeoutMs: Long = 15_000L
    ): PodcastSourceCheckResponse {
        val startedAt = System.currentTimeMillis()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Liuguang/1.0 Android Podcast")
            .build()
        okHttpClient.newCall(request).apply {
            timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
        }.execute().use { response ->
            val content = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw SourceHttpException(
                    statusCode = response.code,
                    message = "HTTP ${response.code}",
                    rawContent = content
                )
            }
            if (content.isBlank()) {
                throw SourceDataException("订阅源内容为空", rawContent = content)
            }
            return try {
                PodcastSourceCheckResponse(
                    httpCode = response.code,
                    contentType = response.header("Content-Type"),
                    rawContent = content,
                    feed = parseRss(content),
                    latencyMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
                )
            } catch (error: Exception) {
                throw SourceDataException(
                    message = error.message ?: "订阅源解析失败",
                    rawContent = content,
                    cause = error
                )
            }
        }
    }

    private fun parseRss(xml: String): PodcastFeed {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(StringReader(xml))
        }

        var feedTitle = "播客订阅"
        var feedDescription = ""
        var feedImage = ""
        var feedLink = ""
        val episodes = mutableListOf<PodcastEpisode>()
        var insideItem = false
        var currentTag = ""
        var episodeTitle = ""
        var episodeDescription = ""
        var episodeAudioUrl = ""
        var episodeAudioType = ""
        var episodeImage = ""
        var episodePubDate = ""
        var episodeDuration = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name.orEmpty()
                    when (currentTag.lowercase()) {
                        "item", "entry" -> {
                            insideItem = true
                            episodeTitle = ""
                            episodeDescription = ""
                            episodeAudioUrl = ""
                            episodeAudioType = ""
                            episodeImage = ""
                            episodePubDate = ""
                            episodeDuration = ""
                        }
                        "enclosure" -> {
                            if (insideItem) {
                                episodeAudioUrl = parser.getAttributeValue(null, "url").orEmpty()
                                episodeAudioType = parser.getAttributeValue(null, "type").orEmpty()
                            }
                        }
                        "link" -> {
                            val href = parser.getAttributeValue(null, "href").orEmpty()
                            if (insideItem && episodeAudioUrl.isBlank() && isAudioLink(href)) {
                                episodeAudioUrl = href
                                episodeAudioType = parser.getAttributeValue(null, "type").orEmpty()
                            } else if (!insideItem && feedLink.isBlank() && href.isNotBlank()) {
                                feedLink = href
                            }
                        }
                        "image", "itunes:image" -> {
                            val href = parser.getAttributeValue(null, "href").orEmpty()
                            if (href.isNotBlank()) {
                                if (insideItem) episodeImage = href else feedImage = href
                            }
                        }
                        "media:content" -> {
                            if (insideItem && episodeAudioUrl.isBlank()) {
                                val mediaUrl = parser.getAttributeValue(null, "url").orEmpty()
                                val mediaType = parser.getAttributeValue(null, "type").orEmpty()
                                if (isAudioLink(mediaUrl) || mediaType.startsWith("audio/")) {
                                    episodeAudioUrl = mediaUrl
                                    episodeAudioType = mediaType
                                }
                            }
                        }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    val text = parser.text.orEmpty().trim()
                    if (text.isNotBlank()) {
                        if (insideItem) {
                            when (currentTag.lowercase()) {
                                "title" -> episodeTitle += text
                                "description", "summary", "content:encoded" -> episodeDescription += text
                                "pubdate", "published", "updated" -> episodePubDate += text
                                "itunes:duration", "duration" -> episodeDuration += text
                                "link" -> if (episodeAudioUrl.isBlank() && isAudioLink(text)) episodeAudioUrl = text
                            }
                        } else {
                            when (currentTag.lowercase()) {
                                "title" -> feedTitle = text
                                "description", "subtitle" -> feedDescription += text
                                "link" -> if (feedLink.isBlank()) feedLink = text
                                "url" -> if (feedImage.isBlank()) feedImage = text
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name.orEmpty().lowercase()
                    if ((tag == "item" || tag == "entry") && insideItem) {
                        if (episodeAudioUrl.isNotBlank()) {
                            episodes += PodcastEpisode(
                                title = episodeTitle.ifBlank { "未命名节目" }.cleanText(),
                                description = episodeDescription.cleanText(),
                                audioUrl = episodeAudioUrl,
                                audioType = episodeAudioType,
                                imageUrl = episodeImage.ifBlank { feedImage },
                                pubDate = episodePubDate.cleanText(),
                                duration = normalizeDuration(episodeDuration.cleanText())
                            )
                        }
                        insideItem = false
                    }
                    currentTag = ""
                }
            }
            eventType = parser.nextToken()
        }

        if (episodes.isEmpty()) {
            throw IllegalStateException("没有解析到可播放的音频节目")
        }

        return PodcastFeed(
            title = feedTitle.cleanText().ifBlank { "播客订阅" },
            description = feedDescription.cleanText(),
            imageUrl = feedImage,
            link = feedLink,
            episodes = episodes
        )
    }

    private fun isAudioLink(value: String): Boolean {
        val lower = value.lowercase()
        return lower.endsWith(".mp3") ||
            lower.endsWith(".m4a") ||
            lower.endsWith(".aac") ||
            lower.endsWith(".ogg") ||
            lower.endsWith(".opus") ||
            lower.endsWith(".wav") ||
            lower.contains("/audio/")
    }

    private fun normalizeDuration(value: String): String {
        if (value.isBlank()) return ""
        if (":" in value) return value
        val seconds = value.toLongOrNull() ?: return value
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, remainingSeconds)
        } else {
            "%d:%02d".format(minutes, remainingSeconds)
        }
    }

    private fun parsePubDateMillis(value: String): Long {
        if (value.isBlank()) return 0L
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, d MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss"
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.US).parse(value)?.time
            }.getOrNull()
        } ?: 0L
    }

    private fun String.cleanText(): String =
        replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
