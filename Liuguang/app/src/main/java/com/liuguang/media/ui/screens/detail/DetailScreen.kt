package com.liuguang.media.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.liuguang.media.domain.model.EpisodeGroup
import com.liuguang.media.domain.model.EpisodeItem
import com.liuguang.media.ui.components.CinemaBackground
import com.liuguang.media.ui.components.CinemaLoading
import com.liuguang.media.ui.components.CinemaMessage
import com.liuguang.media.ui.components.NetworkImage
import com.liuguang.media.ui.components.PageHeader
import com.liuguang.media.ui.theme.AppColors

@Composable
fun DetailScreen(
    siteId: Long,
    vodId: String,
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (Long, String, String, String, String) -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    @Suppress("UNUSED_VARIABLE")
    val unusedRouteArgs = siteId to vodId
    val uiState by viewModel.uiState.collectAsState()

    CinemaBackground(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is DetailUiState.Loading -> CinemaLoading(modifier = Modifier.fillMaxSize())
            is DetailUiState.Error -> CinemaMessage(
                modifier = Modifier.fillMaxSize(),
                title = "详情加载失败",
                message = state.message,
                actionText = "重试",
                onAction = viewModel::loadDetail
            )
            is DetailUiState.Success -> {
                val selectedSource = state.selectedSource
                val vodDetail = selectedSource.vodDetail
                val episodeGroups = selectedSource.episodeGroups
                var selectedGroupName by remember(selectedSource.key) {
                    mutableStateOf(episodeGroups.firstOrNull()?.name)
                }
                val selectedGroup = episodeGroups.firstOrNull { it.name == selectedGroupName }
                    ?: episodeGroups.firstOrNull()
                val detailBody = vodDetail.vod_content?.takeIf { it.isNotBlank() }
                    ?.let(::cleanDetailBody)
                    ?: "暂时没有剧情简介，先从剧集列表挑一集开看。"

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = {
                        PageHeader(
                            title = vodDetail.vod_name,
                            onBackClick = onNavigateBack,
                            horizontalPadding = 0.dp,
                            topPadding = 10.dp,
                            bottomPadding = 0.dp
                        )

                        DetailOverviewCard(
                            source = selectedSource,
                            selectedGroup = selectedGroup,
                            body = detailBody
                        )

                        if (state.sourceOptions.isNotEmpty()) {
                            DetailProviderSection(
                                options = state.sourceOptions,
                                selectedKey = selectedSource.key,
                                isLoading = state.isLoadingSources,
                                onSourceSelect = viewModel::selectSource
                            )
                        }

                        if (episodeGroups.isNotEmpty()) {
                            DetailSourceSection(
                                groups = episodeGroups,
                                selectedGroupName = selectedGroup?.name,
                                onGroupSelect = { selectedGroupName = it }
                            )
                        }

                        DetailEpisodesSection(
                            episodes = selectedGroup?.episodes.orEmpty(),
                            selectedGroupName = selectedGroup?.name ?: "默认线路",
                            onEpisodeClick = { episode ->
                                onNavigateToPlayer(
                                    selectedSource.siteId,
                                    selectedSource.vodId,
                                    episode.url,
                                    vodDetail.vod_name,
                                    episode.label
                                )
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                    }
                )
            }
        }
    }
}

@Composable
private fun DetailOverviewCard(
    source: DetailSourceOption,
    selectedGroup: EpisodeGroup?,
    body: String
) {
    var moreDialog by remember(source.key) { mutableStateOf<DetailMoreDialog?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        AppColors.Surface,
                        AppColors.SurfaceSoft
                    )
                )
            )
            .border(1.dp, AppColors.Divider, RoundedCornerShape(4.dp))
            .padding(9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .width(108.dp)
                .height(166.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(AppColors.SurfaceAlt)
                .border(1.dp, AppColors.Divider, RoundedCornerShape(4.dp))
        ) {
            NetworkImage(
                url = source.vodDetail.vod_pic,
                contentDescription = source.vodDetail.vod_name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 166.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = source.vodDetail.vod_name,
                color = AppColors.TextPrimary,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            DetailExpandableText(
                text = source.vodDetail.vod_actor?.takeIf { it.isNotBlank() }
                    ?.let { "主演 $it" }
                    ?: source.vodDetail.vod_director?.takeIf { it.isNotBlank() }?.let { "导演 $it" }
                    ?: "已解析当前播放线路，可直接播放或切换剧集",
                maxLines = 1,
                onMoreClick = { text -> moreDialog = DetailMoreDialog("主演信息", text) }
            )
            Text(
                text = "简介",
                color = AppColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
            DetailExpandableText(
                text = body,
                maxLines = 4,
                onMoreClick = { text -> moreDialog = DetailMoreDialog("简介", text) }
            )
            DetailMetaLine(
                items = listOfNotNull(
                    source.siteName,
                    source.vodDetail.vod_remarks?.takeIf { it.isNotBlank() } ?: "在线",
                    source.vodDetail.type_name?.takeIf { it.isNotBlank() },
                    selectedGroup?.episodes?.size?.takeIf { it > 0 }?.let { "更新至 $it" },
                    selectedGroup?.name?.takeIf { it.isNotBlank() }
                )
            )
        }
    }

    moreDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { moreDialog = null },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            title = { Text(dialog.title) },
            text = {
                Text(
                    text = dialog.content,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { moreDialog = null }) {
                    Text("关闭")
                }
            }
        )
    }
}

private data class DetailMoreDialog(
    val title: String,
    val content: String
)

@Composable
private fun DetailExpandableText(
    text: String,
    maxLines: Int,
    onMoreClick: (String) -> Unit
) {
    var overflow by remember(text, maxLines) { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = AppColors.TextPrimary.copy(alpha = 0.72f),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { overflow = it.hasVisualOverflow }
        )
        if (overflow) {
            Text(
                text = "更多",
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clickable { onMoreClick(text) },
                color = AppColors.Primary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailProviderSection(
    options: List<DetailSourceOption>,
    selectedKey: String,
    isLoading: Boolean,
    onSourceSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        DetailRailHead(
            title = "播放源",
            meta = if (isLoading) "正在聚合其他源" else "自动选择最快线路"
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            options.forEach { option ->
                val selected = option.key == selectedKey
                DetailChoicePill(
                    title = option.siteName,
                    meta = "${option.episodeGroups.sumOf { it.episodes.size }}集",
                    selected = selected,
                    minWidth = 64.dp,
                    onClick = { onSourceSelect(option.key) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailMetaLine(items: List<String>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items.forEach { item ->
            Text(
                text = item,
                color = AppColors.TextPrimary.copy(alpha = 0.82f),
                fontSize = 10.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailSourceSection(
    groups: List<EpisodeGroup>,
    selectedGroupName: String?,
    onGroupSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        DetailRailHead(title = "播放线路", meta = "当前资源站内切换")

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            groups.forEach { group ->
                val selected = group.name == selectedGroupName
                DetailChoicePill(
                    title = group.name,
                    meta = "${group.episodes.size}集",
                    selected = selected,
                    minWidth = 58.dp,
                    onClick = { onGroupSelect(group.name) }
                )
            }
        }
    }
}

@Composable
private fun DetailChoicePill(
    title: String,
    meta: String,
    selected: Boolean,
    minWidth: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .widthIn(min = minWidth, max = 156.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (selected) {
                    Brush.linearGradient(listOf(AppColors.Primary, AppColors.Primary))
                } else {
                    Brush.linearGradient(
                        listOf(
                            AppColors.Surface,
                            AppColors.SurfaceAlt
                        )
                    )
                }
            )
            .then(
                if (selected) {
                    Modifier
                } else {
                    Modifier.border(1.dp, AppColors.Divider, RoundedCornerShape(4.dp))
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f, fill = false),
            color = if (selected) AppColors.OnPrimary else AppColors.TextPrimary,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = meta,
            color = if (selected) AppColors.OnPrimary.copy(alpha = 0.82f) else AppColors.TextTertiary,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DetailEpisodesSection(
    episodes: List<EpisodeItem>,
    selectedGroupName: String,
    onEpisodeClick: (EpisodeItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DetailRailHead(
            title = "剧集列表",
            meta = "$selectedGroupName · ${episodes.size}集"
        )

        if (episodes.isEmpty()) {
            Text(
                text = "当前线路暂无可播放剧集",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(AppColors.Surface)
                    .border(1.dp, AppColors.Divider, RoundedCornerShape(4.dp))
                    .padding(14.dp),
                color = AppColors.TextTertiary,
                fontSize = 12.sp
            )
            return@Column
        }

        episodes.chunked(5).forEach { rowEpisodes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowEpisodes.forEach { episode ->
                    DetailEpisodeButton(
                        episode = episode,
                        onClick = { onEpisodeClick(episode) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(5 - rowEpisodes.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DetailEpisodeButton(
    episode: EpisodeItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(26.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        AppColors.Surface,
                        AppColors.SurfaceAlt
                    )
                )
            )
            .border(1.dp, AppColors.Divider, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = episode.label,
            color = AppColors.TextPrimary,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DetailRailHead(
    title: String,
    meta: String
) {
    Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            color = AppColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = meta,
            color = AppColors.TextTertiary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun cleanDetailBody(raw: String): String {
    val plainText = HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
    return plainText
        .replace('\u00A0', ' ')
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
        .ifBlank { "暂时没有剧情简介，先从剧集列表挑一集开看。" }
}
