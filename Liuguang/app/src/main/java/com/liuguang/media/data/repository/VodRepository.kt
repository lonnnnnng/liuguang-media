package com.liuguang.media.data.repository

import android.util.Log
import com.google.gson.Gson
import com.liuguang.media.data.remote.VodApiResponse
import com.liuguang.media.data.remote.VodApiService
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VodRepository @Inject constructor(
    private val apiService: VodApiService,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val networkSettingsRepository: NetworkSettingsRepository
) {
    companion object {
        private const val TAG = "VodRepository"
        const val VIDEO_SITE_CHECK_TIMEOUT_MS = 10_000L
        private const val CACHE_TTL_MS = 2 * 60 * 1_000L
    }

    private data class CachedResponse(
        val response: VodApiResponse,
        val timestampMs: Long
    )

    private val responseCache = ConcurrentHashMap<String, CachedResponse>()

    suspend fun getVodList(
        baseUrl: String,
        page: Int? = null,
        typeId: Int? = null,
        keyword: String? = null,
        forceRefresh: Boolean = false,
        timeoutMs: Long = networkSettingsRepository.currentSettings().videoSourceTimeoutMs
    ): Result<VodApiResponse> {
        val cacheKey = buildCacheKey("list", baseUrl, page, typeId, keyword)
        val url = buildString {
            append(baseUrl)
            append("?ac=videolist")
            page?.let { append("&pg=$it") }
            typeId?.let { append("&t=$it") }
            keyword?.let { append("&wd=$it") }
        }
        Log.d(TAG, "getVodList - URL: $url")
        Log.d(TAG, "getVodList - Params: page=$page, typeId=$typeId, keyword=$keyword")

        return executeWithCache(
            cacheKey = cacheKey,
            forceRefresh = forceRefresh,
            label = "getVodList",
            timeoutMs = timeoutMs
        ) {
            apiService.getVodList(
                url = baseUrl,
                page = page,
                typeId = typeId,
                keyword = keyword
            ).also { response ->
                Log.d(TAG, "getVodList - Response: code=${response.code}, total=${response.total}, listSize=${response.list?.size}")
            }
        }
    }

    suspend fun getVodDetail(
        baseUrl: String,
        vodId: String,
        forceRefresh: Boolean = false,
        timeoutMs: Long = networkSettingsRepository.currentSettings().videoSourceTimeoutMs
    ): Result<VodApiResponse> {
        val cacheKey = buildCacheKey("detail", baseUrl, vodId)
        val url = "$baseUrl?ac=detail&ids=$vodId"
        Log.d(TAG, "getVodDetail - URL: $url")
        Log.d(TAG, "getVodDetail - Params: vodId=$vodId")

        return executeWithCache(
            cacheKey = cacheKey,
            forceRefresh = forceRefresh,
            label = "getVodDetail",
            timeoutMs = timeoutMs
        ) {
            apiService.getVodDetail(
                url = baseUrl,
                ids = vodId
            ).also { response ->
                Log.d(TAG, "getVodDetail - Response: code=${response.code}, vodName=${response.list?.firstOrNull()?.vod_name}")
                response.list?.firstOrNull()?.let { vod ->
                    Log.d(TAG, "getVodDetail - vod_play_from: ${vod.vod_play_from}")
                    Log.d(TAG, "getVodDetail - vod_play_url: ${vod.vod_play_url}")
                    Log.d(TAG, "getVodDetail - vod_id: ${vod.vod_id}")
                    Log.d(TAG, "getVodDetail - vod_remarks: ${vod.vod_remarks}")
                }
            }
        }
    }

    suspend fun getCategories(
        baseUrl: String,
        timeoutMs: Long = networkSettingsRepository.currentSettings().videoSourceTimeoutMs
    ): Result<VodApiResponse> = withContext(Dispatchers.IO) {
        try {
            val response = withTimeout(timeoutMs.milliseconds) {
                apiService.getVodList(
                    url = baseUrl,
                    ac = "list",
                    page = 1
                )
            }
            Log.d(TAG, "getCategories - Response: code=${response.code}, classSize=${response.`class`?.size}")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "getCategories - Error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun checkVideoSite(
        baseUrl: String,
        timeoutMs: Long = networkSettingsRepository.currentSettings().videoSourceTimeoutMs
    ): Result<VideoSiteCheckResponse> = withContext(Dispatchers.IO) {
        try {
            withTimeout(timeoutMs.milliseconds) {
                val startedAt = System.currentTimeMillis()
                val normalizedBaseUrl = baseUrl.trim()
                val checkUrl = buildString {
                    append(normalizedBaseUrl)
                    append(if (normalizedBaseUrl.contains("?")) "&" else "?")
                    append("ac=videolist&pg=1")
                }
                val request = Request.Builder()
                    .url(checkUrl)
                    .header("User-Agent", "Mozilla/5.0 Liuguang")
                    .build()
                executeCheckRequest(request, startedAt, timeoutMs).use { response ->
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

                    val apiResponse = try {
                        gson.fromJson(content, VodApiResponse::class.java)
                    } catch (e: Exception) {
                        return@withTimeout Result.failure(
                            SourceDataException(
                                message = "接口返回内容不是有效 JSON",
                                rawContent = content,
                                cause = e
                            )
                        )
                    } ?: return@withTimeout Result.failure(
                        SourceDataException("接口返回内容不是有效 JSON", rawContent = content)
                    )
                    val hasList = !apiResponse.list.isNullOrEmpty()
                    val hasClass = !apiResponse.`class`.isNullOrEmpty()
                    val hasMeta = apiResponse.code != null || apiResponse.total != null || apiResponse.page != null
                    if (!hasList && !hasClass && !hasMeta) {
                        return@withTimeout Result.failure(
                            SourceDataException(
                                message = "接口返回 JSON 不包含影视列表、分类或分页字段",
                                rawContent = content
                            )
                        )
                    }
                    if (!hasList) {
                        return@withTimeout Result.failure(
                            SourceDataException(
                                message = "接口第一页没有返回影片列表，无法验证搜索能力",
                                rawContent = content
                            )
                        )
                    }

                    val searchKeyword = apiResponse.list
                        .orEmpty()
                        .mapNotNull { it.vod_name.takeIf { name -> name.isNotBlank() } }
                        .firstOrNull()
                        ?: return@withTimeout Result.failure(
                            SourceDataException(
                                message = "接口第一页没有可用于搜索验证的影片名称",
                                rawContent = content
                            )
                        )
                    var searchResultCount: Int
                    val searchUrl = buildString {
                        append(normalizedBaseUrl)
                        append(if (normalizedBaseUrl.contains("?")) "&" else "?")
                        append("ac=videolist&pg=1&wd=")
                        append(java.net.URLEncoder.encode(searchKeyword, "UTF-8"))
                    }
                    val searchRequest = Request.Builder()
                        .url(searchUrl)
                        .header("User-Agent", "Mozilla/5.0 Liuguang")
                        .build()
                    executeCheckRequest(searchRequest, startedAt, timeoutMs).use { searchResponse ->
                        val searchContent = searchResponse.body?.string().orEmpty()
                        if (!searchResponse.isSuccessful) {
                            return@withTimeout Result.failure(
                                SourceHttpException(
                                    statusCode = searchResponse.code,
                                    message = "搜索接口 HTTP ${searchResponse.code}",
                                    rawContent = searchContent
                                )
                            )
                        }
                        val searchApiResponse = try {
                            gson.fromJson(searchContent, VodApiResponse::class.java)
                        } catch (e: Exception) {
                            return@withTimeout Result.failure(
                                SourceDataException(
                                    message = "搜索接口返回内容不是有效 JSON",
                                    rawContent = searchContent,
                                    cause = e
                                )
                            )
                        } ?: return@withTimeout Result.failure(
                            SourceDataException("搜索接口返回内容不是有效 JSON", rawContent = searchContent)
                        )
                        searchResultCount = searchApiResponse.list.orEmpty().size
                        if (searchResultCount == 0) {
                            return@withTimeout Result.failure(
                                SourceDataException(
                                    message = "搜索接口没有返回影片列表",
                                    rawContent = searchContent
                                )
                            )
                        }
                    }

                    Result.success(
                        VideoSiteCheckResponse(
                            httpCode = response.code,
                            contentType = response.header("Content-Type"),
                            rawContent = content,
                            response = apiResponse,
                            searchKeyword = searchKeyword,
                            searchResultCount = searchResultCount,
                            latencyMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeCheckRequest(
        request: Request,
        startedAt: Long,
        timeoutMs: Long
    ): Response {
        val remainingMs = timeoutMs - (System.currentTimeMillis() - startedAt)
        if (remainingMs <= 0L) {
            throw InterruptedIOException("检测超时")
        }
        return okHttpClient.newCall(request).apply {
            timeout().timeout(remainingMs, TimeUnit.MILLISECONDS)
        }.execute()
    }

    private suspend fun executeWithCache(
        cacheKey: String,
        forceRefresh: Boolean,
        label: String,
        timeoutMs: Long,
        block: suspend () -> VodApiResponse
    ): Result<VodApiResponse> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            responseCache[cacheKey]
                ?.takeIf { now - it.timestampMs <= CACHE_TTL_MS }
                ?.let {
                    Log.d(TAG, "$label - Cache hit: $cacheKey")
                    return@withContext Result.success(it.response)
                }
        }

        try {
            val response = withTimeout(timeoutMs.milliseconds) {
                block()
            }
            responseCache[cacheKey] = CachedResponse(response, System.currentTimeMillis())
            Result.success(response)
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "$label - Timeout after ${timeoutMs}ms: $cacheKey")
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$label - Error: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun buildCacheKey(vararg parts: Any?): String {
        return parts.joinToString("|") { it?.toString().orEmpty() }
    }
}
