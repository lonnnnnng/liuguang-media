package com.liuguang.media.ui.screens.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liuguang.media.domain.model.LiveChannel
import com.liuguang.media.ui.components.CinemaBackground
import com.liuguang.media.ui.components.CinemaLoading
import com.liuguang.media.ui.components.CinemaMessage
import com.liuguang.media.ui.components.MediaFilterHeader
import com.liuguang.media.ui.components.MediaFilterOption
import com.liuguang.media.ui.components.NetworkImage
import com.liuguang.media.ui.theme.AppColors

@Composable
fun LiveScreen(
    onNavigateToPlayer: (LiveChannel, Long?) -> Unit,
    viewModel: LiveViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val currentSourceId by viewModel.currentSourceId.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()

    val enabledSources = sources.filter { it.enabled }
    val hasLiveSources = sources.isNotEmpty()
    val hasEnabledLiveSources = enabledSources.isNotEmpty()
    val groups = remember(uiState) { viewModel.getGroups() }

    LaunchedEffect(Unit) {
        viewModel.showAllChannels()
    }

    CinemaBackground(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is LiveUiState.Loading -> {
                CinemaLoading(
                    modifier = Modifier.fillMaxSize(),
                    message = "正在解析直播源"
                )
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    MediaFilterHeader(
                        searchPlaceholder = "搜索频道",
                        searchValue = searchQuery,
                        onSearchValueChange = viewModel::setSearchQuery,
                        filters = liveFilterOptions(groups),
                        selectedFilterKey = selectedGroup,
                        onFilterSelected = { key ->
                            if (key == null) {
                                viewModel.showAllChannels()
                            } else {
                                viewModel.selectGroup(key)
                            }
                        }
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        when (state) {
                            is LiveUiState.Error -> item {
                                CinemaMessage(
                                    title = "直播源连接失败",
                                    message = state.message,
                                    actionText = "重试",
                                    onAction = { currentSourceId?.let { viewModel.selectSource(it) } }
                                )
                            }
                            is LiveUiState.Empty -> item {
                                val title = when {
                                    !hasLiveSources -> "暂无直播源"
                                    !hasEnabledLiveSources -> "直播源未启用"
                                    else -> "暂无频道"
                                }
                                val message = when {
                                    !hasLiveSources -> "请先在我的页面进入直播源管理，添加 M3U 直播源。"
                                    !hasEnabledLiveSources -> "当前所有直播源都已停用，请在直播源管理中启用至少一个源。"
                                    else -> "当前筛选没有频道，清除搜索或切换分组再试。"
                                }
                                CinemaMessage(
                                    title = title,
                                    message = message
                                )
                            }
                            is LiveUiState.Success -> {
                                items(
                                    items = state.channels,
                                    key = { channel -> "${channel.name}|${channel.group}|${channel.url}" },
                                    contentType = { "live-channel-row" }
                                ) { channel ->
                                    ChannelRow(
                                        channel = channel,
                                        onClick = { onNavigateToPlayer(channel, currentSourceId) }
                                    )
                                }
                            }
                            LiveUiState.Loading -> Unit
                        }
                    }
                }
            }
        }
    }
}

private fun liveFilterOptions(groups: List<String>): List<MediaFilterOption> {
    val fallbackGroups = listOf("央视", "卫视", "体育", "电影", "少儿")
    return listOf(MediaFilterOption(null, "全部")) +
        groups.ifEmpty { fallbackGroups }
            .take(10)
            .map { group -> MediaFilterOption(group, group) }
}

@Composable
private fun ChannelRow(
    channel: LiveChannel,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 14.dp, vertical = 2.dp)
            .fillMaxWidth()
            .clip(RectangleShape)
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Divider, RectangleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RectangleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF1E293B),
                            Color(0xFF334155)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.16f), RectangleShape),
            contentAlignment = Alignment.Center
        ) {
            if (channel.logo.isNotBlank()) {
                NetworkImage(
                    url = channel.logo,
                    contentDescription = "${channel.name}台标",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(5.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = channel.name.filter { it.isDigit() }.take(2).ifBlank { channel.name.take(1) },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                color = AppColors.TextPrimary,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${channel.group} · ${channel.format}",
                color = AppColors.TextTertiary,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "LIVE",
            color = AppColors.Success,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.width(4.dp))
        androidx.compose.material3.Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "播放",
            tint = AppColors.Primary,
            modifier = Modifier.size(20.dp)
        )
    }
}
