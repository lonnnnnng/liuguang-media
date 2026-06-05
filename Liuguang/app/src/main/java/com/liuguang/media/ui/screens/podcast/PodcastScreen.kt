package com.liuguang.media.ui.screens.podcast

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Refresh
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
    val selectedSubscription = sources.firstOrNull { it.id == uiState.selectedSubscriptionId }

    BackHandler(enabled = uiState.selectedSubscriptionId != null) {
        viewModel.closeSubscription()
    }

    val content: @Composable () -> Unit = {
        PodcastContent(
            uiState = uiState,
            sources = sources,
            selectedSubscription = selectedSubscription,
            showHeader = showHeader,
            contentBottomPadding = contentBottomPadding,
            onBackClick = if (showBackButton) onNavigateBack else null,
            onSearchChange = viewModel::setSearchQuery,
            onRefreshSourcesClick = viewModel::refreshSubscriptions,
            onSubscriptionClick = viewModel::openSubscription,
            onCloseSubscriptionClick = viewModel::closeSubscription,
            onRefreshSubscriptionClick = { subscription -> viewModel.openSubscription(subscription) },
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
    selectedSubscription: PodcastSubscriptionEntity?,
    showHeader: Boolean,
    contentBottomPadding: Dp,
    onBackClick: (() -> Unit)?,
    onSearchChange: (String) -> Unit,
    onRefreshSourcesClick: () -> Unit,
    onSubscriptionClick: (PodcastSubscriptionEntity) -> Unit,
    onCloseSubscriptionClick: () -> Unit,
    onRefreshSubscriptionClick: (PodcastSubscriptionEntity) -> Unit,
    onEpisodeClick: (PodcastLibraryEpisode) -> Unit
) {
    val subscriptionListState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize()) {
        if (showHeader) {
            PageHeader(
                title = "播客",
                onBackClick = onBackClick,
                actions = {
                    HeaderActionButton(
                        enabled = !uiState.isRefreshingSubscriptions && !uiState.isLoadingFeed,
                        onClick = {
                            selectedSubscription?.let(onRefreshSubscriptionClick)
                                ?: onRefreshSourcesClick()
                        }
                    )
                }
            )
        }

        if (selectedSubscription != null || uiState.selectedSubscriptionId != null) {
            PodcastEpisodeListPage(
                uiState = uiState,
                subscription = selectedSubscription,
                contentBottomPadding = contentBottomPadding,
                onSearchChange = onSearchChange,
                onBackClick = onCloseSubscriptionClick,
                onRefreshClick = {
                    selectedSubscription?.let(onRefreshSubscriptionClick)
                },
                onEpisodeClick = onEpisodeClick
            )
        } else {
            PodcastSubscriptionListPage(
                uiState = uiState,
                sources = sources,
                contentBottomPadding = contentBottomPadding,
                listState = subscriptionListState,
                onSearchChange = onSearchChange,
                onRefreshClick = onRefreshSourcesClick,
                onSubscriptionClick = onSubscriptionClick
            )
        }
    }
}

@Composable
private fun PodcastSubscriptionListPage(
    uiState: PodcastUiState,
    sources: List<PodcastSubscriptionEntity>,
    contentBottomPadding: Dp,
    listState: LazyListState,
    onSearchChange: (String) -> Unit,
    onRefreshClick: () -> Unit,
    onSubscriptionClick: (PodcastSubscriptionEntity) -> Unit
) {
    val enabledSources = sources.filter { it.enabled }
    val visibleSources = filterPodcastSubscriptions(enabledSources, uiState.searchQuery)

    Column(modifier = Modifier.fillMaxSize()) {
        PodcastSearchHeader(
            query = uiState.searchQuery,
            placeholder = "搜索播客栏目",
            isRefreshing = uiState.isRefreshingSubscriptions,
            refreshDescription = "刷新播客栏目",
            onSearchChange = onSearchChange,
            onRefreshClick = onRefreshClick
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = 8.dp, bottom = contentBottomPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when {
                uiState.isRefreshingSubscriptions && sources.isEmpty() -> {
                    item { PodcastLoadingCard(message = "正在刷新播客栏目") }
                }
                enabledSources.isEmpty() -> {
                    item {
                        CinemaMessage(
                            title = "暂无播客栏目",
                            message = "请先在我的页面进入播客源管理，添加 RSS 源后再回来收听。"
                        )
                    }
                }
                visibleSources.isEmpty() -> {
                    item {
                        CinemaMessage(
                            title = "没有匹配栏目",
                            message = "换个关键词再试。"
                        )
                    }
                }
                else -> {
                    items(
                        items = visibleSources,
                        key = { source -> source.id },
                        contentType = { "podcast-subscription-row" }
                    ) { source ->
                        PodcastSubscriptionRow(
                            source = source,
                            onClick = { onSubscriptionClick(source) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PodcastEpisodeListPage(
    uiState: PodcastUiState,
    subscription: PodcastSubscriptionEntity?,
    contentBottomPadding: Dp,
    onSearchChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onEpisodeClick: (PodcastLibraryEpisode) -> Unit
) {
    val feed = uiState.selectedFeed
    val feedTitle = feed?.title?.ifBlank { subscription?.title.orEmpty() } ?: subscription?.title.orEmpty()
    val feedImageUrl = feed?.imageUrl?.ifBlank { subscription?.imageUrl.orEmpty() } ?: subscription?.imageUrl.orEmpty()
    val episodes = feed?.episodes.orEmpty()
    val visibleEpisodes = filterFeedEpisodes(episodes, uiState.searchQuery)

    Column(modifier = Modifier.fillMaxSize()) {
        PodcastFeedHeader(
            title = feedTitle.ifBlank { "播客栏目" },
            imageUrl = feedImageUrl,
            description = feed?.description?.ifBlank { subscription?.description.orEmpty() }
                ?: subscription?.description.orEmpty(),
            episodeCount = feed?.episodes?.size ?: subscription?.episodeCount ?: 0,
            onBackClick = onBackClick
        )

        PodcastSearchHeader(
            query = uiState.searchQuery,
            placeholder = "搜索往期节目",
            isRefreshing = uiState.isLoadingFeed,
            refreshDescription = "刷新往期节目",
            onSearchChange = onSearchChange,
            onRefreshClick = onRefreshClick
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = contentBottomPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when {
                uiState.isLoadingFeed && feed == null -> {
                    item { PodcastLoadingCard(message = "正在获取往期节目") }
                }
                subscription == null -> {
                    item {
                        CinemaMessage(
                            title = "栏目不存在",
                            message = "这个播客栏目可能已被删除，请返回后重新选择。"
                        )
                    }
                }
                feed == null -> {
                    item {
                        CinemaMessage(
                            title = "暂无往期节目",
                            message = "点击刷新重新获取这个栏目的节目。"
                        )
                    }
                }
                visibleEpisodes.isEmpty() -> {
                    item {
                        CinemaMessage(
                            title = "没有匹配节目",
                            message = "清除搜索关键词后再试。"
                        )
                    }
                }
                else -> {
                    items(
                        items = visibleEpisodes,
                        key = { episode -> episode.audioUrl },
                        contentType = { "podcast-feed-episode-row" }
                    ) { episode ->
                        val item = PodcastLibraryEpisode(
                            subscriptionId = subscription.id,
                            feedTitle = feedTitle,
                            feedImageUrl = feedImageUrl,
                            episode = episode
                        )
                        PodcastEpisodeRow(
                            item = item,
                            onClick = {
                                val queueItems = visibleEpisodes.map { visibleEpisode ->
                                    PodcastLibraryEpisode(
                                        subscriptionId = subscription.id,
                                        feedTitle = feedTitle,
                                        feedImageUrl = feedImageUrl,
                                        episode = visibleEpisode
                                    ).toAudioQueueItem()
                                }
                                AudioPlaybackQueueStore.setQueue(
                                    items = queueItems,
                                    requestedIndex = visibleEpisodes.indexOf(episode)
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
private fun PodcastSearchHeader(
    query: String,
    placeholder: String,
    isRefreshing: Boolean,
    refreshDescription: String,
    onSearchChange: (String) -> Unit,
    onRefreshClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Shell)
            .padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CinemaSearchInput(
            value = query,
            placeholder = placeholder,
            onValueChange = onSearchChange,
            modifier = Modifier.weight(1f),
            horizontalPadding = 0.dp
        )
        HeaderActionButton(
            enabled = !isRefreshing,
            contentDescription = refreshDescription,
            onClick = onRefreshClick
        )
    }
}

@Composable
private fun PodcastSubscriptionRow(
    source: PodcastSubscriptionEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 14.dp, vertical = 2.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Divider, RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (source.imageUrl.isNotBlank()) {
            PodcastCover(imageUrl = source.imageUrl, title = source.title, size = 54)
        } else {
            PodcastPlaceholderCover(title = source.title, size = 54)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = source.title.ifBlank { "播客栏目" },
                    color = AppColors.TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (source.episodeCount > 0) "${source.episodeCount} 期" else "待刷新",
                    color = AppColors.Primary,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            val description = source.description.ifBlank { source.url }
            Text(
                text = description,
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subscriptionMeta(source),
                color = AppColors.TextTertiary,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "查看往期节目",
            tint = AppColors.Primary,
            modifier = Modifier.size(21.dp)
        )
    }
}

@Composable
private fun PodcastFeedHeader(
    title: String,
    imageUrl: String,
    description: String,
    episodeCount: Int,
    onBackClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Shell)
            .padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(AppColors.Surface)
                .border(1.dp, AppColors.Divider, RoundedCornerShape(5.dp))
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回播客栏目",
                tint = AppColors.TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        if (imageUrl.isNotBlank()) {
            PodcastCover(imageUrl = imageUrl, title = title, size = 48)
        } else {
            PodcastPlaceholderCover(title = title, size = 48)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = AppColors.TextPrimary,
                fontSize = 16.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (episodeCount > 0) "$episodeCount 期往期节目" else "往期节目",
                color = AppColors.Primary,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 1
            )
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    color = AppColors.TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
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
private fun PodcastPlaceholderCover(title: String, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        AppColors.Primary.copy(alpha = 0.9f),
                        AppColors.Accent.copy(alpha = 0.78f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Podcasts,
            contentDescription = title,
            tint = Color.White,
            modifier = Modifier.size((size * 0.45f).dp)
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
    contentDescription: String = "刷新播客节目",
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
            contentDescription = contentDescription,
            tint = if (enabled) AppColors.Primary else AppColors.TextTertiary,
            modifier = Modifier.size(21.dp)
        )
    }
}

private fun filterPodcastSubscriptions(
    sources: List<PodcastSubscriptionEntity>,
    query: String
): List<PodcastSubscriptionEntity> {
    val keyword = query.trim()
    if (keyword.isBlank()) return sources
    return sources.filter { source ->
        source.title.contains(keyword, ignoreCase = true) ||
            source.description.contains(keyword, ignoreCase = true) ||
            source.url.contains(keyword, ignoreCase = true)
    }
}

private fun filterFeedEpisodes(
    episodes: List<PodcastEpisode>,
    query: String
): List<PodcastEpisode> {
    val keyword = query.trim()
    if (keyword.isBlank()) return episodes
    return episodes.filter { episode ->
        episode.title.contains(keyword, ignoreCase = true) ||
            episode.description.contains(keyword, ignoreCase = true) ||
            episode.pubDate.contains(keyword, ignoreCase = true)
    }
}

private fun subscriptionMeta(source: PodcastSubscriptionEntity): String {
    return listOf(
        source.lastCheckStatus.takeIf { it.isNotBlank() && it != "未检测" },
        source.link.takeIf { it.isNotBlank() } ?: source.url
    ).filterNotNull().joinToString(" · ")
}

private fun podcastMeta(item: PodcastLibraryEpisode): String {
    return listOf(
        item.episode.duration,
        formatPodcastPubDate(item.episode.pubDate)
    ).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun formatPodcastPubDate(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return ""
    val date = parsePodcastPubDate(trimmed) ?: return trimmed
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss EEEE", Locale.CHINA).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }.format(date)
}

private fun parsePodcastPubDate(value: String): Date? {
    val utc = TimeZone.getTimeZone("UTC")
    val patterns = listOf(
        PodcastDatePattern("EEE, dd MMM yyyy HH:mm:ss Z"),
        PodcastDatePattern("EEE, d MMM yyyy HH:mm:ss Z"),
        PodcastDatePattern("EEE, dd MMM yyyy HH:mm:ss zzz"),
        PodcastDatePattern("EEE, d MMM yyyy HH:mm:ss zzz"),
        PodcastDatePattern("EEE, dd MMM yyyy HH:mm Z"),
        PodcastDatePattern("EEE, d MMM yyyy HH:mm Z"),
        PodcastDatePattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", utc),
        PodcastDatePattern("yyyy-MM-dd'T'HH:mm:ss'Z'", utc),
        PodcastDatePattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"),
        PodcastDatePattern("yyyy-MM-dd'T'HH:mm:ssXXX"),
        PodcastDatePattern("yyyy-MM-dd HH:mm:ss", TimeZone.getTimeZone("Asia/Shanghai"))
    )
    return patterns.firstNotNullOfOrNull { datePattern ->
        runCatching {
            SimpleDateFormat(datePattern.pattern, Locale.US).apply {
                isLenient = false
                datePattern.timeZone?.let { timeZone = it }
            }.parse(value)
        }.getOrNull()
    }
}

private data class PodcastDatePattern(
    val pattern: String,
    val timeZone: TimeZone? = null
)

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
