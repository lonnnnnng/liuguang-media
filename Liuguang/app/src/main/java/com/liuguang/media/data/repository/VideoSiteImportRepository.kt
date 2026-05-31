package com.liuguang.media.data.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class ImportedVideoSite(
    val name: String,
    val apiUrl: String
)

data class VideoSiteImportResult(
    val sites: List<ImportedVideoSite>,
    val format: String
)

@Singleton
class VideoSiteImportRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    suspend fun importFromUrl(url: String): Result<VideoSiteImportResult> = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = url.trim().replace("&amp;", "&")
            val request = Request.Builder()
                .url(normalizedUrl)
                .header("User-Agent", "Mozilla/5.0 Liuguang")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                val content = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        SourceHttpException(
                            statusCode = response.code,
                            message = "HTTP ${response.code}",
                            rawContent = content
                        )
                    )
                }
                parseSourcePayload(content)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseSourcePayload(content: String): Result<VideoSiteImportResult> {
        val raw = content.trim()
        if (raw.isBlank()) {
            return Result.failure(SourceDataException("导入地址返回内容为空", rawContent = content))
        }

        return parseJson(raw)
            ?.let { Result.success(it) }
            ?: Result.failure(SourceDataException("返回内容不是有效的视频源 JSON 配置", rawContent = content))
    }

    private fun parseJson(content: String): VideoSiteImportResult? {
        val root = runCatching {
            gson.fromJson(content, JsonObject::class.java)
        }.getOrNull() ?: return null
        val sites = root.entrySet()
            .mapNotNull { entry ->
                val item = entry.value.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val apiUrl = item.get("api")?.asString?.trim().orEmpty()
                if (apiUrl.isBlank()) return@mapNotNull null
                val name = item.get("name")?.asString?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: entry.key
                ImportedVideoSite(
                    name = name,
                    apiUrl = apiUrl
                )
            }
            .distinctBy { it.apiUrl.trimEnd('/') }

        return if (sites.isEmpty()) {
            null
        } else {
            VideoSiteImportResult(sites = sites, format = "lite.json")
        }
    }
}
