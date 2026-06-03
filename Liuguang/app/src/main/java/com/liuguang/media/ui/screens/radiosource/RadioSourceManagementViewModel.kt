package com.liuguang.media.ui.screens.radiosource

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liuguang.media.data.local.entity.RadioSourceEntity
import com.liuguang.media.data.repository.NetworkSettingsRepository
import com.liuguang.media.data.repository.RadioRepository
import com.liuguang.media.data.repository.RadioSourceCheckResponse
import com.liuguang.media.data.repository.classifySourceCheckFailure
import com.liuguang.media.data.repository.sourceCheckFailureMessage
import com.liuguang.media.data.repository.sourceCheckReturnedContent
import com.liuguang.media.ui.components.SourceBatchUiState
import com.liuguang.media.ui.components.SourceCheckResultDialogState
import com.liuguang.media.ui.components.SourceCheckSummaryItem
import com.liuguang.media.ui.components.SourceImportUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

@HiltViewModel
class RadioSourceManagementViewModel @Inject constructor(
    private val radioRepository: RadioRepository,
    private val networkSettingsRepository: NetworkSettingsRepository
) : ViewModel() {

    val sources: StateFlow<List<RadioSourceEntity>> = radioRepository.observeAllSources()
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
            radioRepository.insertSource(
                RadioSourceEntity(
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

        val parsedSources = com.liuguang.media.ui.components.parseNamedSourceLines(rawText, "电台源")
        if (parsedSources.isEmpty()) {
            _importUiState.value = SourceImportUiState(message = "未识别到有效电台源")
            return
        }

        viewModelScope.launch {
            _importUiState.value = SourceImportUiState(isImporting = true)
            val existingSources = radioRepository.getAllSources()
            val existingUrls = existingSources.map { it.url.normalizeSourceUrl() }.toSet()
            val maxOrder = existingSources.maxOfOrNull { it.sortOrder } ?: 0
            val newSources = parsedSources
                .filterNot { (_, url) -> url.normalizeSourceUrl() in existingUrls }
                .mapIndexed { index, (name, url) ->
                    RadioSourceEntity(
                        name = name,
                        url = url,
                        enabled = true,
                        sortOrder = maxOrder + index + 1
                    )
                }
            if (newSources.isNotEmpty()) {
                radioRepository.insertSources(newSources)
            }
            _importUiState.value = SourceImportUiState(
                isImporting = false,
                message = "新增 ${newSources.size} 个，跳过重复 ${parsedSources.size - newSources.size} 个"
            )
        }
    }

    fun updateSource(source: RadioSourceEntity) {
        viewModelScope.launch {
            radioRepository.updateSource(source)
        }
    }

    fun deleteSource(source: RadioSourceEntity) {
        viewModelScope.launch {
            radioRepository.deleteSource(source)
        }
    }

    fun toggleEnabled(source: RadioSourceEntity) {
        viewModelScope.launch {
            radioRepository.updateSource(source.copy(enabled = !source.enabled))
        }
    }

    fun moveSourceUp(source: RadioSourceEntity) {
        viewModelScope.launch {
            radioRepository.moveSourceUp(source, sources.value)
        }
    }

    fun moveSourceDown(source: RadioSourceEntity) {
        viewModelScope.launch {
            radioRepository.moveSourceDown(source, sources.value)
        }
    }

    fun clearAllSources() {
        if (_checkingSourceId.value != null || _batchUiState.value.isRunning) return
        viewModelScope.launch {
            radioRepository.clearAllSources()
        }
    }

    fun checkSource(source: RadioSourceEntity) {
        if (_checkingSourceId.value != null || _batchUiState.value.isRunning) return

        viewModelScope.launch {
            _checkingSourceId.value = source.id
            try {
                val result = radioRepository.checkRadioSource(
                    source.url,
                    timeoutMs = networkSettingsRepository.currentSettings().liveSourceTimeoutMs
                )
                result.fold(
                    onSuccess = { response ->
                        radioRepository.updateSource(
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
                        radioRepository.updateSource(
                            source.copy(
                                lastCheckStatus = reason.statusText,
                                lastCheckTime = System.currentTimeMillis()
                            )
                        )
                        _checkResultDialog.value = SourceCheckResultDialogState(
                            title = "电台源检测失败",
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
            val currentSources = radioRepository.getAllSources()
            if (currentSources.isEmpty()) return@launch

            val completedCount = AtomicInteger(0)
            val successCount = AtomicInteger(0)
            val failedCount = AtomicInteger(0)
            val timeoutMs = networkSettingsRepository.currentSettings().liveSourceTimeoutMs
            val semaphore = Semaphore(BATCH_CHECK_PARALLELISM)

            _batchUiState.value = SourceBatchUiState(
                isRunning = true,
                total = currentSources.size,
                message = "并行检测 ${currentSources.size} 个电台源"
            )

            try {
                coroutineScope {
                    currentSources.map { source ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                _batchCheckingSourceIds.update { it + source.id }
                                try {
                                    val checkedSource = checkSourceForBatch(source, timeoutMs)
                                    radioRepository.updateSource(checkedSource)
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
        _importUiState.value = _importUiState.value.copy(message = null)
    }

    fun consumeBatchMessage() {
        _batchUiState.value = _batchUiState.value.copy(message = null)
    }

    private fun buildSuccessDialog(
        source: RadioSourceEntity,
        response: RadioSourceCheckResponse
    ): SourceCheckResultDialogState {
        val groupCount = response.stations.map { it.group }.distinct().size
        val sampleStations = response.stations
            .take(8)
            .joinToString("、") { it.name }
            .ifBlank { "无" }
        val codecs = response.stations
            .map { it.codec }
            .distinct()
            .joinToString("、")
            .ifBlank { "未知" }

        return SourceCheckResultDialogState(
            title = "电台源检测成功",
            sourceName = source.name,
            success = true,
            message = "接口可访问，返回内容已成功解析出电台。",
            summary = listOf(
                SourceCheckSummaryItem("检测地址", source.url),
                SourceCheckSummaryItem("HTTP 状态", response.httpCode.toString()),
                SourceCheckSummaryItem("内容类型", response.contentType ?: "未知"),
                SourceCheckSummaryItem("电台数量", response.stations.size.toString()),
                SourceCheckSummaryItem("分组数量", groupCount.toString()),
                SourceCheckSummaryItem("编码格式", codecs),
                SourceCheckSummaryItem("检测延迟", "${response.latencyMs} ms"),
                SourceCheckSummaryItem("样例电台", sampleStations)
            ),
            returnedContent = response.rawContent.toDialogContent()
        )
    }

    private fun String.toDialogContent(maxLength: Int = 4000): String {
        if (length <= maxLength) return this
        return take(maxLength) + "\n\n...返回内容较长，仅展示前 ${maxLength} 字。"
    }

    private suspend fun checkSourceForBatch(source: RadioSourceEntity, timeoutMs: Long): RadioSourceEntity {
        val result = radioRepository.checkRadioSource(source.url, timeoutMs)
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
