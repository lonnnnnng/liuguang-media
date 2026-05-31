package com.liuguang.media.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NetworkSettings(
    val videoSourceTimeoutSeconds: Int = NetworkSettingsRepository.DEFAULT_TIMEOUT_SECONDS,
    val liveSourceTimeoutSeconds: Int = NetworkSettingsRepository.DEFAULT_TIMEOUT_SECONDS,
    val autoCheckIntervalMinutes: Int? = null
) {
    val videoSourceTimeoutMs: Long = videoSourceTimeoutSeconds * 1_000L
    val liveSourceTimeoutMs: Long = liveSourceTimeoutSeconds * 1_000L
}

@Singleton
class NetworkSettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("network_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<NetworkSettings> = _settings.asStateFlow()

    fun currentSettings(): NetworkSettings = _settings.value

    fun updateSettings(
        videoTimeoutSeconds: Int,
        liveTimeoutSeconds: Int,
        autoCheckIntervalMinutes: Int?
    ) {
        val next = NetworkSettings(
            videoSourceTimeoutSeconds = videoTimeoutSeconds.coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS),
            liveSourceTimeoutSeconds = liveTimeoutSeconds.coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS),
            autoCheckIntervalMinutes = autoCheckIntervalMinutes?.coerceAtLeast(1)
        )
        prefs.edit()
            .putInt(KEY_VIDEO_TIMEOUT_SECONDS, next.videoSourceTimeoutSeconds)
            .putInt(KEY_LIVE_TIMEOUT_SECONDS, next.liveSourceTimeoutSeconds)
            .putInt(KEY_AUTO_CHECK_INTERVAL_MINUTES, next.autoCheckIntervalMinutes ?: 0)
            .apply()
        _settings.value = next
    }

    private fun readSettings(): NetworkSettings {
        val autoCheckInterval = prefs.getInt(KEY_AUTO_CHECK_INTERVAL_MINUTES, 0).takeIf { it > 0 }
        return NetworkSettings(
            videoSourceTimeoutSeconds = prefs.getInt(KEY_VIDEO_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS)
                .coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS),
            liveSourceTimeoutSeconds = prefs.getInt(KEY_LIVE_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS)
                .coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS),
            autoCheckIntervalMinutes = autoCheckInterval
        )
    }

    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 10
        const val MIN_TIMEOUT_SECONDS = 1
        const val MAX_TIMEOUT_SECONDS = 60

        private const val KEY_VIDEO_TIMEOUT_SECONDS = "video_timeout_seconds"
        private const val KEY_LIVE_TIMEOUT_SECONDS = "live_timeout_seconds"
        private const val KEY_AUTO_CHECK_INTERVAL_MINUTES = "auto_check_interval_minutes"
    }
}
