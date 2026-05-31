package com.liuguang.media.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class AppUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val releaseName: String,
    val releaseNotes: String,
    val apkName: String,
    val apkUrl: String,
    val releaseUrl: String,
    val apkSize: Long
)

sealed class AppUpdateCheckResult {
    data class NoUpdate(
        val currentVersion: String,
        val latestVersion: String
    ) : AppUpdateCheckResult()

    data class UpdateAvailable(
        val info: AppUpdateInfo
    ) : AppUpdateCheckResult()
}

@Singleton
class AppUpdateRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @ApplicationContext
    private val context: Context
) {
    private val downloadClient = okHttpClient.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdate(currentVersion: String): Result<AppUpdateCheckResult> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException(publicReleaseErrorMessage(response.code))
                }

                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    throw IllegalStateException("GitHub Release 响应为空")
                }

                val release = JSONObject(body)
                val rawLatestVersion = release.optString("tag_name")
                    .ifBlank { release.optString("name") }
                    .ifBlank { currentVersion }
                val latestVersion = normalizeVersionName(rawLatestVersion)
                    .ifBlank { currentVersion }

                if (compareVersions(latestVersion, currentVersion) <= 0) {
                    return@runCatching AppUpdateCheckResult.NoUpdate(
                        currentVersion = currentVersion,
                        latestVersion = latestVersion
                    )
                }

                val apkAsset = findApkAsset(release)
                    ?: throw IllegalStateException("新版本未附带 APK 安装包")
                val apkUrl = apkAsset.optString("browser_download_url")
                    .ifBlank { throw IllegalStateException("APK 下载地址为空") }

                AppUpdateCheckResult.UpdateAvailable(
                    AppUpdateInfo(
                        currentVersion = currentVersion,
                        latestVersion = latestVersion,
                        releaseName = release.optString("name")
                            .ifBlank { rawLatestVersion }
                            .ifBlank { latestVersion },
                        releaseNotes = release.optString("body").ifBlank { "暂无更新说明" },
                        apkName = apkAsset.optString("name").ifBlank { "Liuguang-$latestVersion.apk" },
                        apkUrl = apkUrl,
                        releaseUrl = release.optString("html_url"),
                        apkSize = apkAsset.optLong("size", 0L)
                    )
                )
            }
        }
    }

    suspend fun downloadApk(
        info: AppUpdateInfo,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (info.apkUrl.isBlank()) {
                throw IllegalStateException("APK 下载地址为空")
            }
            onProgress(0)

            val request = Request.Builder()
                .url(info.apkUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updateDir, sanitizeFileName(info.apkName)).apply {
                if (exists()) delete()
            }

            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("下载失败：HTTP ${response.code}")
                }

                val body = response.body ?: throw IllegalStateException("下载内容为空")
                val totalBytes = body.contentLength()

                body.byteStream().use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloadedBytes = 0L
                        var lastProgress = 0

                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read

                            val progress = if (totalBytes > 0L) {
                                ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                }
            }

            if (!apkFile.exists() || apkFile.length() <= 0L) {
                throw IllegalStateException("下载文件为空")
            }
            onProgress(100)
            apkFile
        }
    }

    private fun findApkAsset(release: JSONObject): JSONObject? {
        val assets = release.optJSONArray("assets") ?: return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            val contentType = asset.optString("content_type")
            if (name.endsWith(".apk", ignoreCase = true) ||
                contentType == "application/vnd.android.package-archive"
            ) {
                return asset
            }
        }
        return null
    }

    private fun compareVersions(remoteVersion: String, localVersion: String): Int {
        val remoteParts = extractVersionNumbers(remoteVersion)
        val localParts = extractVersionNumbers(localVersion)
        val maxSize = maxOf(remoteParts.size, localParts.size)

        for (index in 0 until maxSize) {
            val remote = remoteParts.getOrElse(index) { 0 }
            val local = localParts.getOrElse(index) { 0 }
            if (remote != local) {
                return remote.compareTo(local)
            }
        }
        return 0
    }

    private fun normalizeVersionName(version: String): String {
        return version.trim().replace(Regex("^[vV]"), "")
    }

    private fun extractVersionNumbers(version: String): List<Int> {
        val numbers = Regex("\\d+")
            .findAll(version)
            .mapNotNull { it.value.toIntOrNull() }
            .toList()
        return numbers.ifEmpty { listOf(0) }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "Liuguang-update.apk" }
            .let { if (it.endsWith(".apk", ignoreCase = true)) it else "$it.apk" }
    }

    private fun publicReleaseErrorMessage(code: Int): String {
        return when (code) {
            404 -> "未找到公开发布版本，请确认 GitHub Release 已发布"
            403 -> "GitHub 请求受限，请稍后重试"
            else -> "GitHub 请求失败：HTTP $code"
        }
    }

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/lonnnnnng/liuguang-media/releases/latest"
        const val USER_AGENT = "Liuguang-Android"
    }
}
