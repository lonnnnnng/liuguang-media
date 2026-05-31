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
import com.liuguang.media.ui.components.SourceCheckResultDialogState
import com.liuguang.media.ui.components.SourceCheckSummaryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    private val _checkResultDialog = MutableStateFlow<SourceCheckResultDialogState?>(null)
    val checkResultDialog: StateFlow<SourceCheckResultDialogState?> = _checkResultDialog.asStateFlow()

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

    fun checkSource(source: LiveSourceEntity) {
        if (_checkingSourceId.value != null) return

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
                                lastCheckTime = System.currentTimeMillis()
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

    fun dismissCheckResultDialog() {
        _checkResultDialog.value = null
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
                SourceCheckSummaryItem("样例频道", sampleChannels)
            ),
            returnedContent = response.rawContent.toDialogContent()
        )
    }

    private fun String.toDialogContent(maxLength: Int = 4000): String {
        if (length <= maxLength) return this
        return take(maxLength) + "\n\n...返回内容较长，仅展示前 ${maxLength} 字。"
    }
}
