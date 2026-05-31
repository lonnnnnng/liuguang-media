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
import com.liuguang.media.ui.components.SourceCheckResultDialogState
import com.liuguang.media.ui.components.SourceCheckSummaryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RadioSourceManagementViewModel @Inject constructor(
    private val radioRepository: RadioRepository,
    private val networkSettingsRepository: NetworkSettingsRepository
) : ViewModel() {

    val sources: StateFlow<List<RadioSourceEntity>> = radioRepository.observeAllSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _checkingSourceId = MutableStateFlow<Long?>(null)
    val checkingSourceId: StateFlow<Long?> = _checkingSourceId.asStateFlow()

    private val _checkResultDialog = MutableStateFlow<SourceCheckResultDialogState?>(null)
    val checkResultDialog: StateFlow<SourceCheckResultDialogState?> = _checkResultDialog.asStateFlow()

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

    fun checkSource(source: RadioSourceEntity) {
        if (_checkingSourceId.value != null) return

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
                                lastCheckTime = System.currentTimeMillis()
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

    fun dismissCheckResultDialog() {
        _checkResultDialog.value = null
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
                SourceCheckSummaryItem("样例电台", sampleStations)
            ),
            returnedContent = response.rawContent.toDialogContent()
        )
    }

    private fun String.toDialogContent(maxLength: Int = 4000): String {
        if (length <= maxLength) return this
        return take(maxLength) + "\n\n...返回内容较长，仅展示前 ${maxLength} 字。"
    }
}
