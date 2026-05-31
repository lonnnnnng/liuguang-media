package com.liuguang.media.ui.screens.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liuguang.media.data.repository.LiveRepository
import com.liuguang.media.domain.model.LiveChannel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnlineParseUiState(
    val isLoading: Boolean = false,
    val sourceUrl: String = "",
    val channels: List<LiveChannel> = emptyList(),
    val message: String? = null
)

@HiltViewModel
class OnlineViewModel @Inject constructor(
    private val liveRepository: LiveRepository
) : ViewModel() {

    private val _parseUiState = MutableStateFlow(OnlineParseUiState())
    val parseUiState: StateFlow<OnlineParseUiState> = _parseUiState.asStateFlow()

    fun parseM3u(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            _parseUiState.value = OnlineParseUiState(
                isLoading = true,
                sourceUrl = trimmed,
                message = "正在解析频道列表"
            )

            liveRepository.fetchAndParseChannels(trimmed).fold(
                onSuccess = { channels ->
                    _parseUiState.value = OnlineParseUiState(
                        sourceUrl = trimmed,
                        channels = channels,
                        message = if (channels.isEmpty()) "未解析到可播放频道" else "已解析 ${channels.size} 个频道"
                    )
                },
                onFailure = { error ->
                    _parseUiState.value = OnlineParseUiState(
                        sourceUrl = trimmed,
                        message = "解析失败：${error.message ?: "链接不可用"}"
                    )
                }
            )
        }
    }

    fun clearParseResult() {
        _parseUiState.value = OnlineParseUiState()
    }
}
