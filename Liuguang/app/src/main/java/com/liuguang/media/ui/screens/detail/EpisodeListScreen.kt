package com.liuguang.media.ui.screens.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liuguang.media.domain.model.EpisodeGroup
import com.liuguang.media.domain.model.EpisodeItem
import com.liuguang.media.ui.components.CinemaBackground
import com.liuguang.media.ui.components.CinemaLoading
import com.liuguang.media.ui.components.CinemaMessage
import com.liuguang.media.ui.theme.AppColors

@Composable
fun EpisodeListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (Long, String, String, String, String) -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isAscending by viewModel.isAscending.collectAsState()

    CinemaBackground(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is DetailUiState.Loading -> CinemaLoading(modifier = Modifier.fillMaxSize())
            is DetailUiState.Error -> CinemaMessage(
                modifier = Modifier.fillMaxSize(),
                title = "剧集加载失败",
                message = state.message,
                actionText = "重试",
                onAction = viewModel::loadDetail
            )
            is DetailUiState.Success -> {
                val selectedSource = state.selectedSource
                val episodeGroups = selectedSource.episodeGroups
                val vodDetail = selectedSource.vodDetail
                var selectedGroupName by remember(selectedSource.key) {
                    mutableStateOf(episodeGroups.firstOrNull()?.name)
                }
                var showOnlyUnwatched by remember(selectedSource.key, selectedGroupName) {
                    mutableStateOf(false)
                }
                val selectedGroup = episodeGroups.firstOrNull { it.name == selectedGroupName }
                    ?: episodeGroups.firstOrNull()
                val orderedEpisodes = selectedGroup?.episodes.orEmpty().let { list ->
                    if (isAscending) list else list.reversed()
                }
                val continueIndex = preferredIndex(orderedEpisodes)
                val episodes = if (showOnlyUnwatched) {
                    orderedEpisodes.filterIndexed { index, _ -> index > continueIndex }
                } else {
                    orderedEpisodes
                }
                val visibleContinueIndex = if (showOnlyUnwatched) -1 else continueIndex

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    EpisodeTopBar(
                        title = vodDetail.vod_name,
                        onBackClick = onNavigateBack,
                        onFilterClick = viewModel::toggleSortOrder
                    )

                    EpisodeSourcePicker(
                        options = state.sourceOptions,
                        selectedKey = selectedSource.key,
                        onSourceSelect = viewModel::selectSource
                    )

                    EpisodeTabs(
                        groups = episodeGroups,
                        selectedGroupName = selectedGroup?.name,
                        isAscending = isAscending,
                        showOnlyUnwatched = showOnlyUnwatched,
                        onGroupClick = { selectedGroupName = it },
                        onSortClick = viewModel::toggleSortOrder,
                        onUnwatchedClick = { showOnlyUnwatched = !showOnlyUnwatched }
                    )

                    EpisodeResumeNote(
                        label = orderedEpisodes.getOrNull(continueIndex)?.label ?: "01"
                    )

                    EpisodeRailHead(
                        title = if (showOnlyUnwatched) "未播剧集" else "全部剧集",
                        meta = if (episodes.isEmpty()) "暂无" else "1-${episodes.size}"
                    )

                    EpisodeGrid(
                        episodes = episodes,
                        continueIndex = visibleContinueIndex,
                        onEpisodeClick = {
                            onNavigateToPlayer(
                                selectedSource.siteId,
                                selectedSource.vodId,
                                it.url,
                                vodDetail.vod_name,
                                it.label
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun EpisodeSourcePicker(
    options: List<DetailSourceOption>,
    selectedKey: String,
    onSourceSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EpisodeRailHead(
            title = "资源站",
            meta = if (options.size > 1) "可换源" else "当前源"
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val selected = option.key == selectedKey
                Surface(
                    onClick = { onSourceSelect(option.key) },
                    color = if (selected) AppColors.Primary else AppColors.Surface,
                    contentColor = if (selected) AppColors.OnPrimary else AppColors.TextSecondary,
                    shape = RoundedCornerShape(4.dp),
                    border = if (selected) null else BorderStroke(1.dp, AppColors.Divider)
                ) {
                    Text(
                        text = "${option.siteName} · ${option.episodeGroups.sumOf { it.episodes.size }}集",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeTopBar(
    title: String,
    onBackClick: () -> Unit,
    onFilterClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        EpisodeIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回详情",
            onClick = onBackClick
        )
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "EPISODE SHELF",
                color = AppColors.Primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.4.sp
            )
            Text(
                text = title,
                color = AppColors.TextPrimary,
                fontSize = 31.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        EpisodeIconButton(
            icon = Icons.Default.FilterList,
            contentDescription = "筛选",
            onClick = onFilterClick
        )
    }
}

@Composable
private fun EpisodeTabs(
    groups: List<EpisodeGroup>,
    selectedGroupName: String?,
    isAscending: Boolean,
    showOnlyUnwatched: Boolean,
    onGroupClick: (String) -> Unit,
    onSortClick: () -> Unit,
    onUnwatchedClick: () -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groups.take(4).forEach { group ->
            EpisodeTab(
                label = group.name,
                active = group.name == selectedGroupName,
                onClick = { onGroupClick(group.name) }
            )
        }
        EpisodeTab(
            label = if (isAscending) "正序" else "倒序",
            active = true,
            onClick = onSortClick
        )
        EpisodeTab(
            label = "只看未播",
            active = showOnlyUnwatched,
            onClick = onUnwatchedClick
        )
    }
}

@Composable
private fun EpisodeTab(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (active) AppColors.Primary else AppColors.Surface,
        contentColor = if (active) AppColors.OnPrimary else AppColors.TextSecondary,
        shape = RoundedCornerShape(4.dp),
        border = if (active) null else BorderStroke(1.dp, AppColors.Divider)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EpisodeResumeNote(label: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        AppColors.Primary.copy(alpha = 0.11f),
                        AppColors.Surface
                    )
                )
            )
            .border(1.dp, AppColors.Divider, RoundedCornerShape(4.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "续播位置",
            color = AppColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "$label 播放到 23:18。点击任意集数会进入播放页，当前集用金色标记，已看过的集数有绿色底纹。",
            color = AppColors.TextPrimary.copy(alpha = 0.72f),
            fontSize = 13.sp,
            lineHeight = 21.sp
        )
    }
}

@Composable
private fun EpisodeRailHead(
    title: String,
    meta: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            color = AppColors.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = meta,
            color = AppColors.TextTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EpisodeGrid(
    episodes: List<EpisodeItem>,
    continueIndex: Int,
    onEpisodeClick: (EpisodeItem) -> Unit
) {
    val rows = ((episodes.size + 3) / 4).coerceAtLeast(1)
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier
            .fillMaxWidth()
            .height((rows * 76 + (rows - 1) * 9).dp),
        contentPadding = PaddingValues(0.dp),
        userScrollEnabled = false,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        itemsIndexed(episodes) { index, episode ->
            val active = continueIndex >= 0 && index == continueIndex
            val watched = continueIndex >= 0 && index < continueIndex
            Surface(
                onClick = { onEpisodeClick(episode) },
                color = Color.Transparent,
                contentColor = if (active) AppColors.OnPrimary else AppColors.TextPrimary,
                shape = RoundedCornerShape(4.dp),
                border = if (active) null else BorderStroke(1.dp, AppColors.Divider)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                        .background(
                            when {
                                active -> Brush.linearGradient(listOf(AppColors.Primary, AppColors.Primary))
                                watched -> Brush.linearGradient(
                                    listOf(
                                        AppColors.Primary.copy(alpha = 0.12f),
                                        AppColors.Surface
                                    )
                                )
                                else -> Brush.linearGradient(
                                    listOf(
                                        AppColors.Surface,
                                        AppColors.SurfaceAlt
                                    )
                                )
                            }
                        )
                        .padding(11.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = normalizeEpisodeLabel(episode.label, index),
                        color = if (active) AppColors.OnPrimary else AppColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            active -> "23:18"
                            watched -> "已看完"
                            else -> "未播放"
                        },
                        color = if (active) AppColors.OnPrimary.copy(alpha = 0.82f) else AppColors.TextTertiary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = AppColors.Surface,
        contentColor = AppColors.TextPrimary,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, AppColors.Divider)
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun preferredIndex(episodes: List<EpisodeItem>): Int {
    if (episodes.isEmpty()) return 0
    return 11.coerceAtMost(episodes.lastIndex)
}

private fun normalizeEpisodeLabel(label: String, index: Int): String {
    val digits = label.filter { it.isDigit() }.takeLast(2)
    return digits.ifBlank { (index + 1).toString() }.padStart(2, '0')
}
