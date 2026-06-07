package com.liuguang.media.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liuguang.media.ui.components.CinemaBackground
import com.liuguang.media.ui.components.CinemaMessage
import com.liuguang.media.ui.components.MediaFilterHeader
import com.liuguang.media.ui.components.MediaFilterOption
import com.liuguang.media.ui.components.NetworkImage
import com.liuguang.media.ui.components.ShimmerEffect
import com.liuguang.media.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToDetail: (Long, String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val listState = rememberLazyListState()
    val successState = uiState as? HomeUiState.Success
    val successRows = remember(successState?.vodList) {
        successState?.vodList?.chunked(3).orEmpty()
    }
    val shouldLoadMore by remember(uiState) {
        derivedStateOf {
            val state = uiState as? HomeUiState.Success ?: return@derivedStateOf false
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false

            state.hasMore &&
                !state.isRefreshing &&
                !state.isLoadingMore &&
                !state.isAggregating &&
                layoutInfo.totalItemsCount > 0 &&
                lastVisibleIndex >= layoutInfo.totalItemsCount - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMore()
        }
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        viewModel.startInitialLoad()
    }

    CinemaBackground(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = successState?.isRefreshing == true,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                HomeFixedHeader(
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    onCategorySelected = viewModel::selectCategory,
                    onSearchClick = onNavigateToSearch
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (val state = uiState) {
                        is HomeUiState.Loading -> {
                            homeSkeletonGrid()
                        }
                        is HomeUiState.Error -> {
                            item {
                                CinemaMessage(
                                    title = "片库连接失败",
                                    message = state.message,
                                    actionText = "重试",
                                    onAction = viewModel::refresh
                                )
                            }
                        }
                        is HomeUiState.Empty -> {
                            item {
                                CinemaMessage(
                                    title = "暂无影片",
                                    message = "当前启用的视频源没有返回内容，试试检查视频源配置。"
                                )
                            }
                        }
                        is HomeUiState.Success -> {
                            if (state.isRefreshing) {
                                homeSkeletonGrid()
                            } else {
                                val rows = successRows
                                state.warningMessage?.let { warning ->
                                    item {
                                        Text(
                                            text = warning,
                                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp),
                                            color = AppColors.TextTertiary,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }

                                items(
                                    items = rows,
                                    key = { row -> row.joinToString(separator = "-") { it.key } },
                                    contentType = { "home-vod-row" }
                                ) { row ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        row.forEach { item ->
                                            CinemaVodPoster(
                                                item = item,
                                                onClick = {
                                                    onNavigateToDetail(item.siteId, item.vod.vod_id.toString())
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(3 - row.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }

                                if (state.isLoadingMore) {
                                    item {
                                        HomeLoadMoreFooter(
                                            text = "正在加载更多",
                                            showProgress = true
                                        )
                                    }
                                } else if (!state.hasMore && state.vodList.isNotEmpty()) {
                                    item {
                                        HomeLoadMoreFooter(
                                            text = "已经到底了",
                                            showProgress = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.homeSkeletonGrid(
    rowCount: Int = 4
) {
    items(rowCount, key = { "home-skeleton-row-$it" }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(3) {
                HomeVodPosterSkeleton(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HomeLoadMoreFooter(
    text: String,
    showProgress: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = AppColors.Primary,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.padding(horizontal = 5.dp))
        }
        Text(
            text = text,
            color = AppColors.TextTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun HomeFixedHeader(
    categories: List<HomeCategory>,
    selectedCategoryId: Int?,
    onCategorySelected: (Int?) -> Unit,
    onSearchClick: () -> Unit
) {
    val filters = remember(categories) {
        listOf(MediaFilterOption(key = null, label = "全部")) +
            categories.map { category ->
                MediaFilterOption(key = category.id.toString(), label = category.name)
            }
    }
    MediaFilterHeader(
        searchPlaceholder = "搜索片名",
        onSearchClick = onSearchClick,
        filters = filters,
        selectedFilterKey = selectedCategoryId?.toString(),
        onFilterSelected = { key -> onCategorySelected(key?.toIntOrNull()) }
    )
}

@Composable
private fun HomeVodPosterSkeleton(
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ShimmerEffect(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RectangleShape)
        )
        ShimmerEffect(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth(0.82f)
                .height(14.dp)
                .clip(RectangleShape)
        )
        ShimmerEffect(
            modifier = Modifier
                .padding(top = 5.dp)
                .fillMaxWidth(0.64f)
                .height(12.dp)
                .clip(RectangleShape)
        )
    }
}

@Composable
private fun CinemaVodPoster(
    item: HomeVodItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vod = item.vod
    val typeName = vod.type_name?.takeIf { it.isNotBlank() } ?: "影视"
    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        color = AppColors.Surface,
        contentColor = AppColors.TextPrimary,
        shape = RectangleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Divider),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RectangleShape)
                    .background(AppColors.SurfaceAlt)
            ) {
                NetworkImage(
                    url = vod.vod_pic,
                    contentDescription = vod.vod_name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (!vod.vod_remarks.isNullOrBlank()) {
                    PosterBadge(
                        text = vod.vod_remarks,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    )
                }
                PosterBadge(
                    text = typeName,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                )
            }
            Text(
                text = vod.vod_name,
                modifier = Modifier.padding(top = 8.dp),
                color = AppColors.TextPrimary,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            HomeVodMetaRow(item = item)
        }
    }
}

@Composable
private fun PosterBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.widthIn(max = 78.dp),
        color = AppColors.Primary,
        contentColor = AppColors.OnPrimary,
        shape = RectangleShape
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            fontSize = 9.5.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HomeVodMetaRow(item: HomeVodItem) {
    val area = item.vod.vod_area?.takeIf { it.isNotBlank() } ?: "未知"
    val year = item.vod.vod_year?.takeIf { it.isNotBlank() } ?: "在线"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = area,
            modifier = Modifier.weight(1f),
            color = AppColors.TextTertiary,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = year,
            color = AppColors.TextTertiary,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
