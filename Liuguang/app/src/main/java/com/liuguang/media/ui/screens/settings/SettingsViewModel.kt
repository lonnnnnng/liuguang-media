package com.liuguang.media.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liuguang.media.BuildConfig
import com.liuguang.media.data.repository.AppUpdateCheckResult
import com.liuguang.media.data.repository.AppUpdateInfo
import com.liuguang.media.data.repository.AppUpdateRepository
import com.liuguang.media.data.repository.HistoryRepository
import com.liuguang.media.data.repository.LiveRepository
import com.liuguang.media.data.repository.NetworkSettings
import com.liuguang.media.data.repository.NetworkSettingsRepository
import com.liuguang.media.data.repository.PodcastRepository
import com.liuguang.media.data.repository.RadioRepository
import com.liuguang.media.data.repository.SiteRepository
import com.liuguang.media.data.repository.ThemeMode
import com.liuguang.media.data.repository.ThemeSettings
import com.liuguang.media.data.repository.ThemeSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUpdateUiState(
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val isChecking: Boolean = false,
    val updateInfo: AppUpdateInfo? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val installFile: File? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val siteRepository: SiteRepository,
    private val liveRepository: LiveRepository,
    private val radioRepository: RadioRepository,
    private val podcastRepository: PodcastRepository,
    private val appUpdateRepository: AppUpdateRepository,
    private val networkSettingsRepository: NetworkSettingsRepository,
    private val themeSettingsRepository: ThemeSettingsRepository
) : ViewModel() {
    private val _maintenanceMessage = MutableStateFlow<String?>(null)
    val maintenanceMessage: StateFlow<String?> = _maintenanceMessage.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _updateUiState = MutableStateFlow(SettingsUpdateUiState())
    val updateUiState: StateFlow<SettingsUpdateUiState> = _updateUiState.asStateFlow()

    val networkSettings: StateFlow<NetworkSettings> = networkSettingsRepository.settings
    val themeSettings: StateFlow<ThemeSettings> = themeSettingsRepository.settings

    fun saveThemeMode(mode: ThemeMode) {
        themeSettingsRepository.updateThemeMode(mode)
        _toastMessage.value = "主题已切换为：${mode.displayName}"
    }

    fun saveNetworkSettings(
        videoTimeoutSeconds: String,
        liveTimeoutSeconds: String,
        autoCheckIntervalMinutes: String
    ): Boolean {
        val videoTimeout = videoTimeoutSeconds.trim().toIntOrNull()
        val liveTimeout = liveTimeoutSeconds.trim().toIntOrNull()
        val autoCheckInterval = autoCheckIntervalMinutes.trim().takeIf { it.isNotBlank() }?.toIntOrNull()

        if (videoTimeout == null || liveTimeout == null || autoCheckIntervalMinutes.trim().let { it.isNotBlank() && autoCheckInterval == null }) {
            _maintenanceMessage.value = "网络设置保存失败：请输入有效数字。"
            return false
        }

        networkSettingsRepository.updateSettings(
            videoTimeoutSeconds = videoTimeout,
            liveTimeoutSeconds = liveTimeout,
            autoCheckIntervalMinutes = autoCheckInterval
        )
        _maintenanceMessage.value = "网络设置已保存。"
        return true
    }

    fun resetApp() {
        viewModelScope.launch {
            runCatching {
                historyRepository.clearAllHistory()
                siteRepository.resetToDefaults()
                liveRepository.resetToDefaults()
                radioRepository.resetToDefaults()
                podcastRepository.clearAllSubscriptions()
            }.onSuccess {
                _maintenanceMessage.value = "已清空播放历史和全部源。"
            }.onFailure { error ->
                _maintenanceMessage.value = "重置失败：${error.message ?: "未知错误"}"
            }
        }
    }

    fun checkForUpdates() {
        val state = _updateUiState.value
        if (state.isChecking || state.isDownloading) return

        viewModelScope.launch {
            _updateUiState.update {
                it.copy(
                    isChecking = true,
                    installFile = null
                )
            }

            appUpdateRepository.checkForUpdate(BuildConfig.VERSION_NAME)
                .onSuccess { result ->
                    when (result) {
                        is AppUpdateCheckResult.NoUpdate -> {
                            _updateUiState.update {
                                it.copy(
                                    isChecking = false,
                                    updateInfo = null,
                                    downloadProgress = 0,
                                    installFile = null
                                )
                            }
                            _toastMessage.value = "已是最新版本：${result.currentVersion}"
                        }
                        is AppUpdateCheckResult.UpdateAvailable -> {
                            _updateUiState.update {
                                it.copy(
                                    isChecking = false,
                                    updateInfo = result.info,
                                    downloadProgress = 0,
                                    installFile = null
                                )
                            }
                        }
                    }
                }
                .onFailure { error ->
                    _updateUiState.update { it.copy(isChecking = false) }
                    _maintenanceMessage.value = "检测更新失败：${error.message ?: "未知错误"}"
                }
        }
    }

    fun downloadUpdate() {
        val state = _updateUiState.value
        val info = state.updateInfo ?: return
        if (state.isChecking || state.isDownloading) return

        viewModelScope.launch {
            _updateUiState.update {
                it.copy(
                    isDownloading = true,
                    downloadProgress = 0,
                    installFile = null
                )
            }

            appUpdateRepository.downloadApk(info) { progress ->
                _updateUiState.update {
                    it.copy(downloadProgress = progress.coerceIn(0, 100))
                }
            }
                .onSuccess { file ->
                    _updateUiState.update {
                        it.copy(
                            isDownloading = false,
                            downloadProgress = 100,
                            installFile = file
                        )
                    }
                }
                .onFailure { error ->
                    _updateUiState.update {
                        it.copy(
                            isDownloading = false,
                            downloadProgress = 0,
                            installFile = null
                        )
                    }
                    _maintenanceMessage.value = "下载更新失败：${error.message ?: "未知错误"}"
                }
        }
    }

    fun dismissUpdateDialog() {
        if (_updateUiState.value.isDownloading) return
        _updateUiState.update {
            it.copy(
                updateInfo = null,
                downloadProgress = 0,
                installFile = null
            )
        }
    }

    fun consumeInstallFile() {
        _updateUiState.update {
            it.copy(
                updateInfo = null,
                downloadProgress = 0,
                installFile = null
            )
        }
    }

    fun reportInstallLaunchFailed(error: Throwable) {
        _updateUiState.update {
            it.copy(
                updateInfo = null,
                downloadProgress = 0,
                installFile = null
            )
        }
        _maintenanceMessage.value = "打开安装器失败：${error.message ?: "未知错误"}"
    }

    fun consumeMaintenanceMessage() {
        _maintenanceMessage.value = null
    }

    fun consumeToastMessage() {
        _toastMessage.value = null
    }
}
