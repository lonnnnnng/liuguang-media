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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.liuguang.media.ui.components.NetworkImage
import com.liuguang.media.ui.components.CinemaSearchInput
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

    var showSourceSelector by remember { mutableStateOf(false) }
    val groups = remember(uiState) { viewModel.getGroups() }
    val currentSourceName = sources.firstOrNull { it.id == currentSourceId }?.name ?: "选择直播源"

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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppColors.Shell)
                    ) {
                        LiveSearchRow(
                            searchQuery = searchQuery,
                            onSearchChange = viewModel::setSearchQuery
                        )

                        SourceTabs(
                            labels = groups.ifEmpty { listOf("央视", "卫视", "体育", "电影", "少儿") },
                            selected = selectedGroup,
                            currentSourceName = currentSourceName,
                            onSourceClick = { showSourceSelector = true },
                            onAllClick = viewModel::showAllChannels,
                            onClick = { group -> viewModel.selectGroup(if (group == selectedGroup) null else group) }
                        )
                    }

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
                                CinemaMessage(
                                    title = "暂无频道",
                                    message = "当前筛选没有频道，清除搜索或切换分组再试。"
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

    if (showSourceSelector) {
        AlertDialog(
            onDismissRequest = { showSourceSelector = false },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            title = { Text("选择直播源") },
            text = {
                Column {
                    sources.filter { it.enabled }.forEach { source ->
                        TextButton(
                            onClick = {
                                viewModel.selectSource(source.id)
                                showSourceSelector = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = source.name,
                                color = if (source.id == currentSourceId) AppColors.Primary else AppColors.TextPrimary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSourceSelector = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun LiveSearchRow(
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CinemaSearchInput(
            value = searchQuery,
            placeholder = "搜索频道",
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            horizontalPadding = 0.dp
        )
    }
}

@Composable
private fun LiveSourceChip(
    sourceName: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(AppColors.PrimaryLight)
            .border(1.dp, AppColors.Primary.copy(alpha = 0.42f), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "切换直播源：$sourceName",
            tint = AppColors.Primary,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = "换源",
            color = AppColors.Primary,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun SourceTabs(
    labels: List<String>,
    selected: String?,
    currentSourceName: String,
    onSourceClick: () -> Unit,
    onAllClick: () -> Unit,
    onClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(top = 2.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiveSourceChip(
            sourceName = currentSourceName,
            onClick = onSourceClick
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            item {
                SourceTabChip(
                    label = "全部",
                    active = selected == null,
                    onClick = onAllClick
                )
            }
            items(
                items = labels.take(10),
                key = { label -> label },
                contentType = { "live-group-chip" }
            ) { label ->
                SourceTabChip(
                    label = label,
                    active = label == selected,
                    onClick = { onClick(label) }
                )
            }
        }
    }
}

@Composable
private fun SourceTabChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) AppColors.Primary else AppColors.Surface)
            .then(
                if (active) {
                    Modifier
                } else {
                    Modifier.border(1.dp, AppColors.Divider, RoundedCornerShape(4.dp))
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = if (active) AppColors.OnPrimary else AppColors.TextPrimary,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1
    )
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
            .clip(RoundedCornerShape(4.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Divider, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF1E293B),
                            Color(0xFF334155)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(4.dp)),
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
