package com.liuguang.media.ui.screens.radio

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.liuguang.media.domain.model.RadioStation
import com.liuguang.media.player.AudioPlaybackQueueStore
import com.liuguang.media.player.AudioQueueItem
import com.liuguang.media.ui.components.CinemaBackground
import com.liuguang.media.ui.components.CinemaLoading
import com.liuguang.media.ui.components.CinemaMessage
import com.liuguang.media.ui.components.CinemaSearchInput
import com.liuguang.media.ui.components.NetworkImage
import com.liuguang.media.ui.theme.AppColors

@Composable
fun RadioScreen(
    onNavigateToPlayer: (RadioStation, Long?) -> Unit,
    useOuterBackground: Boolean = true,
    viewModel: RadioViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val currentSourceId by viewModel.currentSourceId.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()
    val groups by viewModel.groups.collectAsState()

    var showSourceSelector by remember { mutableStateOf(false) }
    val currentSourceName = sources.firstOrNull { it.id == currentSourceId }?.name ?: "选择电台源"

    if (useOuterBackground) {
        CinemaBackground(modifier = Modifier.fillMaxSize()) {
            RadioScreenContent(
                uiState = uiState,
                groups = groups,
                selectedGroup = selectedGroup,
                searchQuery = searchQuery,
                currentSourceName = currentSourceName,
                currentSourceId = currentSourceId,
                onSearchChange = viewModel::setSearchQuery,
                onRefreshClick = viewModel::refreshCurrentSource,
                onSourceClick = { showSourceSelector = true },
                onAllClick = viewModel::showAllStations,
                onGroupClick = { group -> viewModel.selectGroup(if (group == selectedGroup) null else group) },
                onRetryClick = viewModel::refreshCurrentSource,
                onNavigateToPlayer = onNavigateToPlayer
            )
        }
    } else {
        RadioScreenContent(
            uiState = uiState,
            groups = groups,
            selectedGroup = selectedGroup,
            searchQuery = searchQuery,
            currentSourceName = currentSourceName,
            currentSourceId = currentSourceId,
            onSearchChange = viewModel::setSearchQuery,
            onRefreshClick = viewModel::refreshCurrentSource,
            onSourceClick = { showSourceSelector = true },
            onAllClick = viewModel::showAllStations,
            onGroupClick = { group -> viewModel.selectGroup(if (group == selectedGroup) null else group) },
            onRetryClick = viewModel::refreshCurrentSource,
            onNavigateToPlayer = onNavigateToPlayer
        )
    }

    if (showSourceSelector) {
        AlertDialog(
            onDismissRequest = { showSourceSelector = false },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            title = { Text("选择电台源") },
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
private fun RadioScreenContent(
    uiState: RadioUiState,
    groups: List<String>,
    selectedGroup: String?,
    searchQuery: String,
    currentSourceName: String,
    currentSourceId: Long?,
    onSearchChange: (String) -> Unit,
    onRefreshClick: () -> Unit,
    onSourceClick: () -> Unit,
    onAllClick: () -> Unit,
    onGroupClick: (String) -> Unit,
    onRetryClick: () -> Unit,
    onNavigateToPlayer: (RadioStation, Long?) -> Unit
) {
    when (val state = uiState) {
        is RadioUiState.Loading -> CinemaLoading(
            modifier = Modifier.fillMaxSize(),
            message = "正在解析电台源"
        )
        else -> {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppColors.Shell)
                ) {
                    RadioSearchRow(
                        searchQuery = searchQuery,
                        onSearchChange = onSearchChange,
                        onRefreshClick = onRefreshClick
                    )

                    RadioTabs(
                        labels = groups.ifEmpty { listOf("音乐", "新闻", "中文", "交通", "综合") },
                        selected = selectedGroup,
                        currentSourceName = currentSourceName,
                        onSourceClick = onSourceClick,
                        onAllClick = onAllClick,
                        onClick = onGroupClick
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when (state) {
                        is RadioUiState.Error -> item {
                            CinemaMessage(
                                title = "电台源连接失败",
                                message = state.message,
                                actionText = "重试",
                                onAction = onRetryClick
                            )
                        }
                        is RadioUiState.Empty -> item {
                            CinemaMessage(
                                title = "暂无电台",
                                message = "当前筛选没有电台，清除搜索或切换电台源再试。"
                            )
                        }
                        is RadioUiState.Success -> {
                            items(
                                items = state.stations,
                                key = { station -> "${station.name}|${station.group}|${station.url}" },
                                contentType = { "radio-station-row" }
                            ) { station ->
                                RadioStationRow(
                                    station = station,
                                    onClick = {
                                        AudioPlaybackQueueStore.setQueue(
                                            items = state.stations.map { it.toAudioQueueItem() },
                                            requestedIndex = state.stations.indexOf(station)
                                        )
                                        onNavigateToPlayer(station, currentSourceId)
                                    }
                                )
                            }
                        }
                        RadioUiState.Loading -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioSearchRow(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onRefreshClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CinemaSearchInput(
            value = searchQuery,
            placeholder = "搜索电台、国家、分类",
            onValueChange = onSearchChange,
            modifier = Modifier.weight(1f),
            horizontalPadding = 0.dp
        )
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(AppColors.Surface)
                .border(1.dp, AppColors.Divider, RoundedCornerShape(4.dp))
                .clickable(onClick = onRefreshClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "刷新电台源",
                tint = AppColors.Primary,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

@Composable
private fun RadioTabs(
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
        RadioSourceChip(
            sourceName = currentSourceName,
            onClick = onSourceClick
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            item {
                RadioTabChip(
                    label = "全部",
                    active = selected == null,
                    onClick = onAllClick
                )
            }
            items(
                items = labels.take(12),
                key = { label -> label },
                contentType = { "radio-group-chip" }
            ) { label ->
                RadioTabChip(
                    label = label,
                    active = label == selected,
                    onClick = { onClick(label) }
                )
            }
        }
    }
}

@Composable
private fun RadioSourceChip(
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
            contentDescription = "切换电台源：$sourceName",
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
private fun RadioTabChip(
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
private fun RadioStationRow(
    station: RadioStation,
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
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            AppColors.Primary.copy(alpha = 0.92f),
                            AppColors.Accent.copy(alpha = 0.86f)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (station.logo.isNotBlank()) {
                NetworkImage(
                    url = station.logo,
                    contentDescription = "${station.name}台标",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(5.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = station.name.take(1).ifBlank { "R" },
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = station.name,
                color = AppColors.TextPrimary,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = radioMeta(station),
                color = AppColors.TextTertiary,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = if (station.bitrate > 0) "${station.bitrate}k" else station.codec.ifBlank { "RADIO" },
            color = AppColors.Success,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "播放",
            tint = AppColors.Primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun radioMeta(station: RadioStation): String {
    return listOf(
        station.group,
        station.country,
        station.codec,
        station.bitrate.takeIf { it > 0 }?.let { "${it}kbps" }
    ).filter { !it.isNullOrBlank() }.joinToString(" · ")
}

private fun RadioStation.toAudioQueueItem(): AudioQueueItem {
    return AudioQueueItem(
        url = url,
        title = name,
        subtitle = radioMeta(this),
        group = group,
        codec = codec,
        bitrate = bitrate,
        artworkUrl = logo
    )
}
