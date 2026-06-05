package com.liuguang.media.ui.screens.online

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liuguang.media.domain.model.LiveChannel
import com.liuguang.media.ui.components.CinemaBackground
import com.liuguang.media.ui.components.PageHeader
import com.liuguang.media.ui.theme.AppColors

private enum class OnlineLinkMode {
    M3u8,
    M3u
}

@Composable
fun OnlineScreen(
    onNavigateBack: () -> Unit,
    onNavigateToM3u8Player: (String) -> Unit,
    onNavigateToLivePlayer: (LiveChannel) -> Unit,
    viewModel: OnlineViewModel = hiltViewModel()
) {
    val focusManager = LocalFocusManager.current
    var inputUrl by remember { mutableStateOf("") }
    val parseUiState by viewModel.parseUiState.collectAsState()
    val validation = remember(inputUrl) { validateOnlineLink(inputUrl) }
    val mode = remember(inputUrl) { inferOnlineLinkMode(inputUrl) }

    CinemaBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = "在线播放",
                onBackClick = onNavigateBack
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 0.dp,
                    end = 16.dp,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    OnlineIntroCopy()
                }

                item {
                    OnlineInputField(
                        inputUrl = inputUrl,
                        onInputChange = {
                            inputUrl = it
                            viewModel.clearParseResult()
                        }
                    )
                }

                item {
                    OnlineActionButton(
                        enabled = validation.isPlayable,
                        isLoading = parseUiState.isLoading,
                        onActionClick = {
                            focusManager.clearFocus(force = true)
                            val playableUrl = inputUrl.trim()
                            when (mode) {
                                OnlineLinkMode.M3u8 -> onNavigateToM3u8Player(playableUrl)
                                OnlineLinkMode.M3u -> viewModel.parseM3u(playableUrl)
                            }
                        }
                    )
                }

                if (parseUiState.message != null || parseUiState.channels.isNotEmpty()) {
                    item {
                        OnlineParseResultPanel(
                            state = parseUiState,
                            onChannelClick = onNavigateToLivePlayer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineIntroCopy() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RectangleShape)
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Divider, RectangleShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "手动粘贴链接后直接播放",
            color = AppColors.TextPrimary,
            fontSize = 21.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "支持单个 m3u8 视频链接，也支持 m3u 直播列表。输入链接后点击解析播放，应用不会再监听剪切板并自动跳转。",
            color = AppColors.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}

@Composable
private fun OnlineInputField(
    inputUrl: String,
    onInputChange: (String) -> Unit
) {
    OutlinedTextField(
        value = inputUrl,
        onValueChange = onInputChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 124.dp, max = 176.dp),
        label = { Text("播放链接") },
        placeholder = {
            Text(
                text = "https://.../index.m3u8 或 https://.../playlist.m3u",
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        },
        textStyle = TextStyle(
            color = AppColors.TextPrimary,
            fontSize = 13.sp,
            lineHeight = 19.sp
        ),
        singleLine = false,
        minLines = 4,
        maxLines = 6,
        shape = RectangleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AppColors.TextPrimary,
            unfocusedTextColor = AppColors.TextPrimary,
            focusedContainerColor = AppColors.Surface,
            unfocusedContainerColor = AppColors.Surface,
            focusedBorderColor = AppColors.Primary,
            unfocusedBorderColor = AppColors.Divider,
            cursorColor = AppColors.Primary,
            focusedLabelColor = AppColors.Primary,
            unfocusedLabelColor = AppColors.TextSecondary,
            focusedPlaceholderColor = AppColors.TextTertiary,
            unfocusedPlaceholderColor = AppColors.TextTertiary
        )
    )
}

@Composable
private fun OnlineActionButton(
    enabled: Boolean,
    isLoading: Boolean,
    onActionClick: () -> Unit
) {
    Surface(
        onClick = onActionClick,
        enabled = enabled && !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        color = if (enabled && !isLoading) AppColors.Primary else AppColors.SurfaceRaised,
        contentColor = if (enabled && !isLoading) AppColors.OnPrimary else AppColors.TextTertiary,
        shape = RectangleShape,
        border = BorderStroke(1.dp, if (enabled && !isLoading) Color.Transparent else AppColors.Divider)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isLoading) "解析中" else "解析播放",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun OnlineParseResultPanel(
    state: OnlineParseUiState,
    onChannelClick: (LiveChannel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RectangleShape)
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Divider, RectangleShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state.message != null) {
            Text(
                text = state.message,
                color = if (state.channels.isNotEmpty()) AppColors.Primary else AppColors.TextTertiary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        state.channels.take(30).forEach { channel ->
            OnlineChannelRow(
                channel = channel,
                onClick = { onChannelClick(channel) }
            )
        }
    }
}

@Composable
private fun OnlineChannelRow(
    channel: LiveChannel,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        color = AppColors.SurfaceAlt,
        contentColor = AppColors.TextPrimary,
        shape = RectangleShape,
        border = BorderStroke(1.dp, AppColors.Divider)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LiveTv,
                contentDescription = null,
                tint = AppColors.Primary,
                modifier = Modifier.size(18.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = channel.name,
                    color = AppColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${channel.group} · ${channel.format}",
                    color = AppColors.TextTertiary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private data class OnlineLinkValidation(
    val isPlayable: Boolean
)

private fun inferOnlineLinkMode(input: String): OnlineLinkMode {
    val normalized = input.trim().lowercase()
    return if (normalized.endsWith(".m3u") || normalized.contains(".m3u?")) {
        OnlineLinkMode.M3u
    } else {
        OnlineLinkMode.M3u8
    }
}

private fun validateOnlineLink(input: String): OnlineLinkValidation {
    val trimmed = input.trim()
    val lower = trimmed.lowercase()
    val hasScheme = lower.startsWith("http://") || lower.startsWith("https://")
    val hasSupportedPlaylist = lower.contains(".m3u8") || lower.contains(".m3u")
    return OnlineLinkValidation(
        isPlayable = trimmed.isNotBlank() && hasScheme && hasSupportedPlaylist
    )
}
