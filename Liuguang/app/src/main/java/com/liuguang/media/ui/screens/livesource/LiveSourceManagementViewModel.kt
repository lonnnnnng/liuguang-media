package com.liuguang.media.ui.screens.livesource

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liuguang.media.data.local.entity.LiveSourceEntity
import com.liuguang.media.data.repository.LiveSourceCheckResponse
import com.liuguang.media.data.repository.LiveRepository
import com.liuguang.media.data.repository.NetworkSettingsRepository
import com.liuguang.media.data.repository.classifySourceCheckFailure
import com.liuguang.media.data.repository.sourceCheckFailureMessage
import com.liuguang.media.data.repository.sourceCheckReturnedContent
import com.liuguang.media.ui.components.SourceBatchUiState
import com.liuguang.media.ui.components.SourceCheckResultDialogState
import com.liuguang.media.ui.components.SourceCheckSummaryItem
import com.liuguang.media.ui.components.SourceImportUiState
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

@HiltViewModel
class LiveSourceManagementViewModel @Inject constructor(
    private val liveRepository: LiveRepository,
    private val networkSettingsRepository: NetworkSettingsRepository
) : ViewModel() {

    val sources: StateFlow<List<LiveSourceEntity>> = liveRepository.observeAllSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _checkingSourceId = MutableStateFlow<Long?>(null)
    val checkingSourceId: StateFlow<Long?> = _checkingSourceId.asStateFlow()

    private val _batchCheckingSourceIds = MutableStateFlow<Set<Long>>(emptySet())
    val batchCheckingSourceIds: StateFlow<Set<Long>> = _batchCheckingSourceIds.asStateFlow()

    private val _checkResultDialog = MutableStateFlow<SourceCheckResultDialogState?>(null)
    val checkResultDialog: StateFlow<SourceCheckResultDialogState?> = _checkResultDialog.asStateFlow()

    private val _importUiState = MutableStateFlow(SourceImportUiState())
    val importUiState: StateFlow<SourceImportUiState> = _importUiState.asStateFlow()

    private val _batchUiState = MutableStateFlow(SourceBatchUiState())
    val batchUiState: StateFlow<SourceBatchUiState> = _batchUiState.asStateFlow()

    fun addSource(name: String, url: String) {
        viewModelScope.launch {
            val maxOrder = sources.value.maxOfOrNull { it.sortOrder } ?: 0
            liveRepository.insertSource(
                LiveSourceEntity(
                    name = name,
                    url = url,
                    enabled = true,
                    sortOrder = maxOrder + 1
                )
            )
        }
    }

    fun importSources(rawText: String) {
        if (_importUiState.value.isImporting || _batchUiState.value.isRunning) return

        val parsedSources = com.liuguang.media.ui.components.parseNamedSourceLines(rawText, "直播源")
        if (parsedSources.isEmpty()) {
            _importUiState.value = SourceImportUiState(message = "未识别到有效直播源")
            return
        }

        viewModelScope.launch {
            val existingSources = liveRepository.getAllSources()
            val existingUrls = existingSources.map { it.url.normalizeSourceUrl() }.toSet()
            val maxOrder = existingSources.maxOfOrNull { it.sortOrder } ?: 0
            val newSources = parsedSources
                .filterNot { (_, url) -> url.normalizeSourceUrl() in existingUrls }
                .mapIndexed { index, (name, url) ->
                    LiveSourceEntity(
                        name = name,
                        url = url,
                        enabled = true,
                        sortOrder = maxOrder + index + 1
                    )
                }
            _importUiState.value = SourceImportUiState(
                isImporting = true,
                currentIndex = 0,
                total = newSources.size,
                message = "正在导入直播源"
            )
            newSources.forEachIndexed { index, source ->
                liveRepository.insertSource(source)
                _importUiState.value = SourceImportUiState(
                    isImporting = true,
                    currentIndex = index + 1,
                    total = newSources.size,
                    message = "正在导入直播源"
                )
            }
            _importUiState.value = SourceImportUiState(
                isImporting = false,
                currentIndex = newSources.size,
                total = newSources.size,
                message = "新增 ${newSources.size} 个，跳过重复 ${parsedSources.size - newSources.size} 个"
            )
        }
    }

    fun updateSource(source: LiveSourceEntity) {
        viewModelScope.launch {
            liveRepository.updateSource(source)
        }
    }

    fun deleteSource(source: LiveSourceEntity) {
        viewModelScope.launch {
            liveRepository.deleteSource(source)
        }
    }

    fun toggleEnabled(source: LiveSourceEntity) {
        viewModelScope.launch {
            liveRepository.updateSource(source.copy(enabled = !source.enabled))
        }
    }

    fun moveSourceUp(source: LiveSourceEntity) {
        viewModelScope.launch {
            liveRepository.moveSourceUp(source, sources.value)
        }
    }

    fun moveSourceDown(source: LiveSourceEntity) {
        viewModelScope.launch {
            liveRepository.moveSourceDown(source, sources.value)
        }
    }

    fun clearAllSources() {
        if (_checkingSourceId.value != null || _batchUiState.value.isRunning) return
        viewModelScope.launch {
            liveRepository.clearAllSources()
        }
    }

    fun checkSource(source: LiveSourceEntity) {
        if (_checkingSourceId.value != null || _batchUiState.value.isRunning) return

        viewModelScope.launch {
            _checkingSourceId.value = source.id
            try {
                val result = liveRepository.checkLiveSource(
                    source.url,
                    timeoutMs = networkSettingsRepository.currentSettings().liveSourceTimeoutMs
                )
                result.fold(
                    onSuccess = { response ->
                        liveRepository.updateSource(
                            source.copy(
                                lastCheckStatus = "可用",
                                lastCheckTime = System.currentTimeMillis(),
                                enabled = true
                            )
                        )
                        _checkResultDialog.value = buildSuccessDialog(source, response)
                    },
                    onFailure = { error ->
                        val reason = classifySourceCheckFailure(error)
                        liveRepository.updateSource(
                            source.copy(
                                lastCheckStatus = reason.statusText,
                                lastCheckTime = System.currentTimeMillis()
                            )
                        )
                        _checkResultDialog.value = SourceCheckResultDialogState(
                            title = "直播源检测失败",
                            sourceName = source.name,
                            success = false,
                            message = "${reason.label}：${sourceCheckFailureMessage(reason, error)}",
                            summary = listOf(
                                SourceCheckSummaryItem("检测地址", source.url),
                                SourceCheckSummaryItem("失败分类", reason.label)
                            ),
                            returnedContent = sourceCheckReturnedContent(error)?.toDialogContent()
                        )
                    }
                )
            } finally {
                _checkingSourceId.value = null
            }
        }
    }

    fun batchCheckSources() {
        if (_checkingSourceId.value != null || _batchUiState.value.isRunning) return

        viewModelScope.launch {
            val currentSources = liveRepository.getAllSources()
            if (currentSources.isEmpty()) return@launch

            val completedCount = AtomicInteger(0)
            val successCount = AtomicInteger(0)
            val failedCount = AtomicInteger(0)
            val timeoutMs = networkSettingsRepository.currentSettings().liveSourceTimeoutMs
            val semaphore = Semaphore(BATCH_CHECK_PARALLELISM)

            _batchUiState.value = SourceBatchUiState(
                isRunning = true,
                total = currentSources.size,
                message = "并行检测 ${currentSources.size} 个直播源"
            )

            try {
                coroutineScope {
                    currentSources.map { source ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                _batchCheckingSourceIds.update { it + source.id }
                                try {
                                    val checkedSource = checkSourceForBatch(source, timeoutMs)
                                    liveRepository.updateSource(checkedSource)
                                    val completed = completedCount.incrementAndGet()
                                    if (checkedSource.lastCheckStatus == "可用") {
                                        successCount.incrementAndGet()
                                    } else {
                                        failedCount.incrementAndGet()
                                    }
                                    _batchUiState.value = SourceBatchUiState(
                                        isRunning = true,
                                        currentIndex = completed,
                                        total = currentSources.size,
                                        message = "刚完成：${source.name}"
                                    )
                                } finally {
                                    _batchCheckingSourceIds.update { it - source.id }
                                }
                            }
                        }
                    }.awaitAll()
                }

                _batchUiState.value = SourceBatchUiState(
                    isRunning = false,
                    currentIndex = currentSources.size,
                    total = currentSources.size,
                    message = "批量检测完成：可用 ${successCount.get()} 个，异常 ${failedCount.get()} 个"
                )
            } finally {
                _batchCheckingSourceIds.value = emptySet()
            }
        }
    }

    fun dismissCheckResultDialog() {
        _checkResultDialog.value = null
    }

    fun consumeImportMessage() {
        _importUiState.value = SourceImportUiState()
    }

    fun consumeBatchMessage() {
        _batchUiState.value = SourceBatchUiState()
    }

    private fun buildSuccessDialog(
        source: LiveSourceEntity,
        response: LiveSourceCheckResponse
    ): SourceCheckResultDialogState {
        val groupCount = response.channels.map { it.group }.distinct().size
        val sampleChannels = response.channels
            .take(8)
            .joinToString("、") { it.name }
            .ifBlank { "无" }
        val formats = response.channels
            .map { it.format }
            .distinct()
            .joinToString("、")
            .ifBlank { "未知" }

        return SourceCheckResultDialogState(
            title = "直播源检测成功",
            sourceName = source.name,
            success = true,
            message = "接口可访问，返回内容已成功解析出频道。",
            summary = listOf(
                SourceCheckSummaryItem("检测地址", source.url),
                SourceCheckSummaryItem("HTTP 状态", response.httpCode.toString()),
                SourceCheckSummaryItem("内容类型", response.contentType ?: "未知"),
                SourceCheckSummaryItem("频道数量", response.channels.size.toString()),
                SourceCheckSummaryItem("分组数量", groupCount.toString()),
                SourceCheckSummaryItem("播放格式", formats),
                SourceCheckSummaryItem("检测延迟", "${response.latencyMs} ms"),
                SourceCheckSummaryItem("样例频道", sampleChannels)
            ),
            returnedContent = response.rawContent.toDialogContent()
        )
    }

    private fun String.toDialogContent(maxLength: Int = 4000): String {
        if (length <= maxLength) return this
        return take(maxLength) + "\n\n...返回内容较长，仅展示前 ${maxLength} 字。"
    }

    private suspend fun checkSourceForBatch(source: LiveSourceEntity, timeoutMs: Long): LiveSourceEntity {
        val result = liveRepository.checkLiveSource(source.url, timeoutMs)
        return result.fold(
            onSuccess = {
                source.copy(
                    enabled = true,
                    lastCheckStatus = "可用",
                    lastCheckTime = System.currentTimeMillis()
                )
            },
            onFailure = { error ->
                val status = if (error is TimeoutCancellationException) {
                    "超时"
                } else {
                    classifySourceCheckFailure(error).statusText
                }
                source.copy(
                    lastCheckStatus = status,
                    lastCheckTime = System.currentTimeMillis()
                )
            }
        )
    }

    private fun String.normalizeSourceUrl(): String = trim().trimEnd('/')

    private companion object {
        const val BATCH_CHECK_PARALLELISM = 6
    }
}
