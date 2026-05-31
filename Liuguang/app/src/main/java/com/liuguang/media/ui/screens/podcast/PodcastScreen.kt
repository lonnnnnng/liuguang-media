package com.liuguang.media.ui.screens.podcast

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
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liuguang.media.data.local.entity.PodcastSubscriptionEntity
import com.liuguang.media.domain.model.PodcastEpisode
import com.liuguang.media.domain.model.PodcastLibraryEpisode
import com.liuguang.media.player.AudioPlaybackQueueStore
import com.liuguang.media.player.AudioQueueItem
import com.liuguang.media.ui.components.CinemaBackground
import com.liuguang.media.ui.components.CinemaMessage
import com.liuguang.media.ui.components.CinemaSearchInput
import com.liuguang.media.ui.components.NetworkImage
import com.liuguang.media.ui.components.PageHeader
import com.liuguang.media.ui.theme.AppColors

@Composable
fun PodcastScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (PodcastEpisode, String, String) -> Unit,
    useOuterBackground: Boolean = true,
    showBackButton: Boolean = true,
    showHeader: Boolean = true,
    contentBottomPadding: Dp = 96.dp,
    viewModel: PodcastViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val sources by viewModel.subscriptions.collectAsState()

    val content: @Composable () -> Unit = {
        PodcastContent(
            uiState = uiState,
            sources = sources,
            showHeader = showHeader,
            contentBottomPadding = contentBottomPadding,
            onBackClick = if (showBackButton) onNavigateBack else null,
            onSearchChange = viewModel::setSearchQuery,
            onRefreshClick = viewModel::refreshLibrary,
            onAllClick = { viewModel.selectSource(null) },
            onSourceClick = viewModel::selectSource,
            onRetryClick = viewModel::refreshLibrary,
            onEpisodeClick = { item ->
                onNavigateToPlayer(
                    item.episode,
                    item.feedTitle,
                    item.feedImageUrl
                )
            }
        )
    }

    if (useOuterBackground) {
        CinemaBackground(modifier = Modifier.fillMaxSize()) {
            content()
        }
    } else {
        content()
    }

    uiState.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            title = { Text("提示") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearMessage) {
                    Text("知道了")
                }
            }
        )
    }
}

@Composable
private fun PodcastContent(
    uiState: PodcastUiState,
    sources: List<PodcastSubscriptionEntity>,
    showHeader: Boolean,
    contentBottomPadding: Dp,
    onBackClick: (() -> Unit)?,
    onSearchChange: (String) -> Unit,
    onRefreshClick: () -> Unit,
    onAllClick: () -> Unit,
    onSourceClick: (Long?) -> Unit,
    onRetryClick: () -> Unit,
    onEpisodeClick: (PodcastLibraryEpisode) -> Unit
) {
    val visibleEpisodes = filterPodcastEpisodes(
        episodes = uiState.libraryEpisodes,
        sources = sources,
        selectedSourceId = uiState.selectedSourceId,
        query = uiState.searchQuery
    )

    Column(modifier = Modifier.fillMaxSize()) {
        if (showHeader) {
            PageHeader(
                title = "播客",
                onBackClick = onBackClick,
                actions = {
                    HeaderActionButton(
                        enabled = !uiState.isRefreshingLibrary,
                        onClick = onRefreshClick
                    )
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.Shell)
        ) {
            PodcastSearchRow(
                searchQuery = uiState.searchQuery,
                isRefreshing = uiState.isRefreshingLibrary,
                onSearchChange = onSearchChange,
                onRefreshClick = onRefreshClick
            )

            PodcastTabs(
                sources = sources,
                selectedSourceId = uiState.selectedSourceId,
                onAllClick = onAllClick,
                onSourceClick = onSourceClick
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = contentBottomPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when {
                uiState.isRefreshingLibrary && uiState.libraryEpisodes.isEmpty() -> {
                    item { PodcastLoadingCard(message = "正在聚合播客节目") }
                }
                sources.isEmpty() -> {
                    item {
                        CinemaMessage(
                            title = "暂无播客源",
                            message = "请先在我的页面进入播客源管理，添加 RSS 源后再回来收听。"
                        )
                    }
                }
                uiState.libraryEpisodes.isEmpty() -> {
                    item {
                        CinemaMessage(
                            title = "暂无播客节目",
                            message = "当前播客源没有可播放节目，刷新后再试。",
                            actionText = "刷新",
                            onAction = onRetryClick
                        )
                    }
                }
                visibleEpisodes.isEmpty() -> {
                    item {
                        CinemaMessage(
                            title = "没有匹配节目",
                            message = "清除搜索关键词或切换播客源再试。"
                        )
                    }
                }
                else -> {
                    items(
                        items = visibleEpisodes,
                        key = { item -> "${item.subscriptionId}|${item.episode.audioUrl}" },
                        contentType = { "podcast-library-episode-row" }
                    ) { item ->
                        PodcastEpisodeRow(
                            item = item,
                            onClick = {
                                AudioPlaybackQueueStore.setQueue(
                                    items = visibleEpisodes.map { it.toAudioQueueItem() },
                                    requestedIndex = visibleEpisodes.indexOf(item)
                                )
                                onEpisodeClick(item)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PodcastSearchRow(
    searchQuery: String,
    isRefreshing: Boolean,
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
            placeholder = "搜索播客、节目、关键词",
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
                .clickable(enabled = !isRefreshing, onClick = onRefreshClick),
            contentAlignment = Alignment.Center
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    color = AppColors.Primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "刷新播客节目",
                    tint = AppColors.Primary,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}

@Composable
private fun PodcastTabs(
    sources: List<PodcastSubscriptionEntity>,
    selectedSourceId: Long?,
    onAllClick: () -> Unit,
    onSourceClick: (Long?) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(top = 2.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            PodcastTabChip(
                label = "全部",
                active = selectedSourceId == null,
                onClick = onAllClick
            )
        }
        items(
            items = sources,
            key = { source -> source.id },
            contentType = { "podcast-source-chip" }
        ) { source ->
            PodcastTabChip(
                label = source.title.ifBlank { "播客源" },
                active = source.id == selectedSourceId,
                onClick = { onSourceClick(source.id) }
            )
        }
    }
}

@Composable
private fun PodcastTabChip(
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
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun PodcastEpisodeRow(
    item: PodcastLibraryEpisode,
    onClick: () -> Unit
) {
    val imageUrl = item.episode.imageUrl.ifBlank { item.feedImageUrl }
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
        if (imageUrl.isNotBlank()) {
            PodcastCover(imageUrl = imageUrl, title = item.episode.title, size = 40)
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                AppColors.Primary.copy(alpha = 0.92f),
                                AppColors.Accent.copy(alpha = 0.86f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Headphones,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.episode.title,
                color = AppColors.TextPrimary,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.feedTitle,
                color = AppColors.TextSecondary,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val meta = podcastMeta(item)
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    color = AppColors.TextTertiary,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = item.episode.audioType.ifBlank { "AUDIO" }.uppercase(),
            color = AppColors.Success,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            modifier = Modifier.width(48.dp)
        )
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "播放",
            tint = AppColors.Primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun PodcastCover(imageUrl: String, title: String, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(AppColors.SurfaceAlt)
    ) {
        NetworkImage(
            url = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun PodcastLoadingCard(message: String) {
    Row(
        modifier = Modifier
            .padding(horizontal = 14.dp, vertical = 2.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Divider, RoundedCornerShape(4.dp))
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = AppColors.Primary,
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = message,
            color = AppColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun HeaderActionButton(
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Divider, RoundedCornerShape(4.dp))
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "刷新播客节目",
            tint = if (enabled) AppColors.Primary else AppColors.TextTertiary,
            modifier = Modifier.size(21.dp)
        )
    }
}

private fun filterPodcastEpisodes(
    episodes: List<PodcastLibraryEpisode>,
    sources: List<PodcastSubscriptionEntity>,
    selectedSourceId: Long?,
    query: String
): List<PodcastLibraryEpisode> {
    val sourceNames = sources.associate { it.id to it.title }
    val keyword = query.trim()
    return episodes.filter { item ->
        val matchesSource = selectedSourceId == null || item.subscriptionId == selectedSourceId
        val matchesKeyword = keyword.isBlank() ||
            item.episode.title.contains(keyword, ignoreCase = true) ||
            item.feedTitle.contains(keyword, ignoreCase = true) ||
            item.episode.description.contains(keyword, ignoreCase = true) ||
            sourceNames[item.subscriptionId].orEmpty().contains(keyword, ignoreCase = true)
        matchesSource && matchesKeyword
    }
}

private fun podcastMeta(item: PodcastLibraryEpisode): String {
    return listOf(
        item.episode.duration,
        item.episode.pubDate
    ).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun PodcastLibraryEpisode.toAudioQueueItem(): AudioQueueItem {
    val imageUrl = episode.imageUrl.ifBlank { feedImageUrl }
    return AudioQueueItem(
        url = episode.audioUrl,
        title = episode.title,
        subtitle = feedTitle,
        group = feedTitle,
        codec = episode.audioType.ifBlank { "Podcast" },
        bitrate = 0,
        artworkUrl = imageUrl,
        mediaId = stablePodcastMediaId()
    )
}

private fun PodcastLibraryEpisode.stablePodcastMediaId(): String {
    return "podcast-${subscriptionId}-${episode.audioUrl.hashCode()}"
}
