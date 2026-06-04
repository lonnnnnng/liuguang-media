package com.liuguang.media.ui.screens.sitemanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liuguang.media.data.local.entity.VideoSiteEntity
import com.liuguang.media.data.repository.NetworkSettingsRepository
import com.liuguang.media.data.repository.SiteRepository
import com.liuguang.media.data.repository.VideoSiteImportRepository
import com.liuguang.media.data.repository.VideoSiteCheckResponse
import com.liuguang.media.data.repository.VodRepository
import com.liuguang.media.data.repository.classifySourceCheckFailure
import com.liuguang.media.data.repository.sourceCheckFailureMessage
import com.liuguang.media.data.repository.sourceCheckReturnedContent
import com.liuguang.media.ui.components.SourceCheckResultDialogState
import com.liuguang.media.ui.components.SourceCheckSummaryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

data class SiteImportUiState(
    val isImporting: Boolean = false,
    val currentIndex: Int = 0,
    val total: Int = 0,
    val message: String? = null
)

data class BatchCheckUiState(
    val isChecking: Boolean = false,
    val currentIndex: Int = 0,
    val total: Int = 0,
    val message: String? = null
)

@HiltViewModel
class SiteManagementViewModel @Inject constructor(
    private val siteRepository: SiteRepository,
    private val vodRepository: VodRepository,
    private val importRepository: VideoSiteImportRepository,
    private val networkSettingsRepository: NetworkSettingsRepository
) : ViewModel() {

    val sites: StateFlow<List<VideoSiteEntity>> = siteRepository.observeAllSites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _checkingSiteId = MutableStateFlow<Long?>(null)
    val checkingSiteId: StateFlow<Long?> = _checkingSiteId.asStateFlow()

    private val _batchCheckingSiteIds = MutableStateFlow<Set<Long>>(emptySet())
    val batchCheckingSiteIds: StateFlow<Set<Long>> = _batchCheckingSiteIds.asStateFlow()

    private val _checkResultDialog = MutableStateFlow<SourceCheckResultDialogState?>(null)
    val checkResultDialog: StateFlow<SourceCheckResultDialogState?> = _checkResultDialog.asStateFlow()

    private val _importUiState = MutableStateFlow(SiteImportUiState())
    val importUiState: StateFlow<SiteImportUiState> = _importUiState.asStateFlow()

    private val _batchCheckUiState = MutableStateFlow(BatchCheckUiState())
    val batchCheckUiState: StateFlow<BatchCheckUiState> = _batchCheckUiState.asStateFlow()

    fun addSite(name: String, url: String) {
        viewModelScope.launch {
            val maxOrder = sites.value.maxOfOrNull { it.sortOrder } ?: 0
            siteRepository.insertSite(
                VideoSiteEntity(
                    name = name,
                    apiUrl = url,
                    enabled = true,
                    sortOrder = maxOrder + 1
                )
            )
        }
    }

    fun updateSite(site: VideoSiteEntity) {
        viewModelScope.launch {
            siteRepository.updateSite(site)
        }
    }

    fun deleteSite(site: VideoSiteEntity) {
        viewModelScope.launch {
            siteRepository.deleteSite(site)
        }
    }

    fun toggleEnabled(site: VideoSiteEntity) {
        viewModelScope.launch {
            val enabled = !site.enabled
            siteRepository.updateSite(
                site.copy(
                    enabled = enabled,
                    isDefault = if (!enabled) false else site.isDefault
                )
            )
        }
    }

    fun moveSiteUp(site: VideoSiteEntity) {
        viewModelScope.launch {
            siteRepository.moveSiteUp(site, sites.value)
        }
    }

    fun moveSiteDown(site: VideoSiteEntity) {
        viewModelScope.launch {
            siteRepository.moveSiteDown(site, sites.value)
        }
    }

    fun clearAllSites() {
        viewModelScope.launch {
            siteRepository.clearAllSites()
        }
    }

    fun setDefaultSite(site: VideoSiteEntity) {
        if (_checkingSiteId.value != null || _batchCheckUiState.value.isChecking) return

        viewModelScope.launch {
            siteRepository.setDefaultSite(site.id)
        }
    }

    fun importSitesFromUrl(url: String) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank() || _importUiState.value.isImporting) return

        viewModelScope.launch {
            _importUiState.value = SiteImportUiState(
                isImporting = true,
                message = "正在读取视频源配置"
            )
            val result = importRepository.importFromUrl(trimmedUrl)
            result.fold(
                onSuccess = { importResult ->
                    val existingSites = siteRepository.getAllSites()
                    val existingUrls = existingSites.map { it.apiUrl.normalizeApiUrl() }.toSet()
                    val existingDefault = existingSites.any { it.isDefault }
                    val maxOrder = existingSites.maxOfOrNull { it.sortOrder } ?: 0
                    val newSites = importResult.sites
                        .filterNot { it.apiUrl.normalizeApiUrl() in existingUrls }
                        .mapIndexed { index, imported ->
                            VideoSiteEntity(
                                name = imported.name,
                                apiUrl = imported.apiUrl,
                                enabled = true,
                                sortOrder = maxOrder + index + 1,
                                lastCheckStatus = "未检测",
                                isDefault = !existingDefault && index == 0
                            )
                        }
                    _importUiState.value = SiteImportUiState(
                        isImporting = true,
                        currentIndex = 0,
                        total = newSites.size,
                        message = "正在导入视频源"
                    )
                    newSites.forEachIndexed { index, site ->
                        siteRepository.insertSite(site)
                        _importUiState.value = SiteImportUiState(
                            isImporting = true,
                            currentIndex = index + 1,
                            total = newSites.size,
                            message = "正在导入视频源"
                        )
                    }
                    val skippedCount = importResult.sites.size - newSites.size
                    _importUiState.value = SiteImportUiState(
                        isImporting = false,
                        currentIndex = newSites.size,
                        total = newSites.size,
                        message = "已识别 ${importResult.format} 配置，新增 ${newSites.size} 个源，跳过重复 ${skippedCount} 个。"
                    )
                },
                onFailure = { error ->
                    _importUiState.value = SiteImportUiState(
                        isImporting = false,
                        message = "导入失败：${error.message ?: "无法解析源配置"}"
                    )
                }
            )
        }
    }

    fun checkSite(site: VideoSiteEntity) {
        if (_checkingSiteId.value != null || _batchCheckUiState.value.isChecking) return

        viewModelScope.launch {
            _checkingSiteId.value = site.id
            try {
                val result = vodRepository.checkVideoSite(
                    site.apiUrl,
                    timeoutMs = networkSettingsRepository.currentSettings().videoSourceTimeoutMs
                )
                result.fold(
                    onSuccess = { response ->
                        siteRepository.updateSite(
                            site.copy(
                                lastCheckStatus = "可用",
                                enabled = true,
                                lastCheckTime = System.currentTimeMillis(),
                                lastLatencyMs = response.latencyMs
                            )
                        )
                        _checkResultDialog.value = buildSuccessDialog(site, response)
                    },
                    onFailure = { error ->
                        val reason = classifySourceCheckFailure(error)
                        siteRepository.updateSite(
                            site.copy(
                                lastCheckStatus = reason.statusText,
                                enabled = false,
                                lastCheckTime = System.currentTimeMillis(),
                                lastLatencyMs = 0,
                                isDefault = false
                            )
                        )
                        _checkResultDialog.value = SourceCheckResultDialogState(
                            title = "视频源检测失败",
                            sourceName = site.name,
                            success = false,
                            message = "${reason.label}：${sourceCheckFailureMessage(reason, error)}",
                            summary = listOf(
                                SourceCheckSummaryItem("检测地址", site.apiUrl),
                                SourceCheckSummaryItem("失败分类", reason.label)
                            ),
                            returnedContent = sourceCheckReturnedContent(error)?.toDialogContent()
                        )
                    }
                )
            } finally {
                _checkingSiteId.value = null
            }
        }
    }

    fun batchCheckSites() {
        if (_checkingSiteId.value != null || _batchCheckUiState.value.isChecking) return

        viewModelScope.launch {
            val currentSites = siteRepository.getAllSites()
            if (currentSites.isEmpty()) return@launch

            val successCount = AtomicInteger(0)
            val failedCount = AtomicInteger(0)
            val completedCount = AtomicInteger(0)
            val semaphore = Semaphore(BATCH_CHECK_PARALLELISM)
            _batchCheckUiState.value = BatchCheckUiState(
                isChecking = true,
                total = currentSites.size,
                message = "并行检测 ${currentSites.size} 个视频源"
            )

            try {
                val checkedSites = coroutineScope {
                    currentSites.map { site ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                _batchCheckingSiteIds.update { it + site.id }
                                try {
                                    val checkedSite = checkSiteForBatch(site)
                                    siteRepository.updateSite(checkedSite)

                                    val completed = completedCount.incrementAndGet()
                                    if (checkedSite.enabled && checkedSite.lastLatencyMs > 0L) {
                                        successCount.incrementAndGet()
                                    } else {
                                        failedCount.incrementAndGet()
                                    }
                                    _batchCheckUiState.value = BatchCheckUiState(
                                        isChecking = true,
                                        currentIndex = completed,
                                        total = currentSites.size,
                                        message = "刚完成：${site.name}"
                                    )
                                    checkedSite
                                } finally {
                                    _batchCheckingSiteIds.update { it - site.id }
                                }
                            }
                        }
                    }.awaitAll()
                }

                val reorderedSites = reorderCheckedSites(checkedSites)
                siteRepository.updateSites(reorderedSites)

                _batchCheckUiState.value = BatchCheckUiState(
                    isChecking = false,
                    currentIndex = currentSites.size,
                    total = currentSites.size,
                    message = "批量检测完成：可用 ${successCount.get()} 个，已自动禁用 ${failedCount.get()} 个，已按延迟排序。"
                )
            } finally {
                _batchCheckingSiteIds.value = emptySet()
            }
        }
    }

    fun dismissCheckResultDialog() {
        _checkResultDialog.value = null
    }

    fun consumeImportMessage() {
        _importUiState.value = SiteImportUiState()
    }

    fun consumeBatchCheckMessage() {
        _batchCheckUiState.value = BatchCheckUiState()
    }

    private fun buildSuccessDialog(
        site: VideoSiteEntity,
        response: VideoSiteCheckResponse
    ): SourceCheckResultDialogState {
        val apiResponse = response.response
        val sampleVideos = apiResponse.list.orEmpty()
            .take(5)
            .joinToString("、") { it.vod_name }
            .ifBlank { "无" }
        val sampleClasses = apiResponse.`class`.orEmpty()
            .take(5)
            .joinToString("、") { it.type_name }
            .ifBlank { "无" }

        return SourceCheckResultDialogState(
            title = "视频源检测成功",
            sourceName = site.name,
            success = true,
            message = "接口可访问，返回数据已成功解析。",
            summary = listOf(
                SourceCheckSummaryItem("检测地址", site.apiUrl),
                SourceCheckSummaryItem("HTTP 状态", response.httpCode.toString()),
                SourceCheckSummaryItem("内容类型", response.contentType ?: "未知"),
                SourceCheckSummaryItem("接口状态", listOfNotNull(apiResponse.code?.toString(), apiResponse.msg).joinToString(" / ").ifBlank { "无" }),
                SourceCheckSummaryItem("分页信息", "page=${apiResponse.page ?: "-"} / pagecount=${apiResponse.pagecount ?: "-"} / total=${apiResponse.total ?: "-"}"),
                SourceCheckSummaryItem("列表数量", apiResponse.list.orEmpty().size.toString()),
                SourceCheckSummaryItem("分类数量", apiResponse.`class`.orEmpty().size.toString()),
                SourceCheckSummaryItem("搜索能力", response.searchKeyword?.let { keyword ->
                    "可搜索：$keyword，结果 ${response.searchResultCount ?: 0} 条"
                } ?: "未验证"),
                SourceCheckSummaryItem("检测延迟", "${response.latencyMs} ms"),
                SourceCheckSummaryItem("样例影片", sampleVideos),
                SourceCheckSummaryItem("样例分类", sampleClasses)
            ),
            returnedContent = response.rawContent.toDialogContent()
        )
    }

    private fun String.toDialogContent(maxLength: Int = 4000): String {
        if (length <= maxLength) return this
        return take(maxLength) + "\n\n...返回内容较长，仅展示前 ${maxLength} 字。"
    }

    private fun String.normalizeApiUrl(): String {
        return trim().trimEnd('/')
    }

    private suspend fun checkSiteForBatch(site: VideoSiteEntity): VideoSiteEntity {
        val result = vodRepository.checkVideoSite(
            site.apiUrl,
            timeoutMs = networkSettingsRepository.currentSettings().videoSourceTimeoutMs
        )
        return result.fold(
            onSuccess = { response ->
                site.copy(
                    enabled = true,
                    lastCheckStatus = "可用",
                    lastCheckTime = System.currentTimeMillis(),
                    lastLatencyMs = response.latencyMs
                )
            },
            onFailure = { error ->
                val reason = if (error is TimeoutCancellationException) {
                    "超时"
                } else {
                    classifySourceCheckFailure(error).statusText
                }
                site.copy(
                    enabled = false,
                    lastCheckStatus = reason,
                    lastCheckTime = System.currentTimeMillis(),
                    lastLatencyMs = 0,
                    isDefault = false
                )
            }
        )
    }

    private fun reorderCheckedSites(sites: List<VideoSiteEntity>): List<VideoSiteEntity> {
        val availableSites = sites
            .filter { it.enabled && it.lastLatencyMs > 0L }
            .sortedWith(compareBy<VideoSiteEntity> { it.lastLatencyMs }.thenBy { it.id })
        val unavailableSites = sites
            .filterNot { it.enabled && it.lastLatencyMs > 0L }
            .sortedWith(compareBy<VideoSiteEntity> { it.sortOrder }.thenBy { it.id })
        val defaultSiteId = availableSites.firstOrNull()?.id

        return (availableSites + unavailableSites).mapIndexed { index, site ->
            site.copy(
                sortOrder = index + 1,
                isDefault = site.id == defaultSiteId
            )
        }
    }

    private companion object {
        const val BATCH_CHECK_PARALLELISM = 6
    }
}
