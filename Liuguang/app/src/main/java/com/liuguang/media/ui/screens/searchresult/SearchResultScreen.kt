package com.liuguang.media.ui.screens.searchresult

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liuguang.media.data.remote.VodItem
import com.liuguang.media.ui.components.CinemaBackground
import com.liuguang.media.ui.components.CinemaLoading
import com.liuguang.media.ui.components.CinemaMessage
import com.liuguang.media.ui.components.NetworkImage
import com.liuguang.media.ui.components.PageHeader
import com.liuguang.media.ui.theme.AppColors

@Composable
fun SearchResultScreen(
    keyword: String,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Long, String) -> Unit,
    viewModel: SearchResultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    CinemaBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = "搜索: $keyword",
                onBackClick = onNavigateBack
            )

            when (val state = uiState) {
                is SearchResultUiState.Error -> {
                    SearchSummaryText(summary = state.summary)
                    CinemaMessage(
                        modifier = Modifier.fillMaxSize(),
                        title = "搜索失败",
                        message = state.message,
                        actionText = "重试",
                        onAction = viewModel::search
                    )
                }
                is SearchResultUiState.Success -> {
                    SearchSummaryText(summary = state.summary)
                    if (state.vodList.isEmpty()) {
                        if (state.summary.isSearching) {
                            CinemaLoading(
                                modifier = Modifier.fillMaxSize(),
                                message = "正在并行搜索"
                            )
                        } else {
                            CinemaMessage(
                                modifier = Modifier.fillMaxSize(),
                                title = "未找到相关内容",
                                message = "换个关键词再试。"
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = state.vodList,
                                key = { item -> "${item.siteId}:${item.vod.vod_id}" },
                                contentType = { "search-result-row" }
                            ) { item ->
                                SearchResultRow(
                                    vod = item.vod,
                                    siteName = item.siteName,
                                    onClick = {
                                        onNavigateToDetail(item.siteId, item.vod.vod_id.toString())
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSummaryText(summary: SearchSummary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = AppColors.Primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = summary.text,
            color = AppColors.TextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchResultRow(
    vod: VodItem,
    siteName: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RectangleShape)
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Divider, RectangleShape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(58.dp)
                .height(78.dp)
                .clip(RectangleShape)
                .background(AppColors.SurfaceAlt)
        ) {
            NetworkImage(
                url = vod.vod_pic,
                contentDescription = vod.vod_name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = vod.vod_name,
                color = AppColors.TextPrimary,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(vod.type_name, vod.vod_year, vod.vod_remarks)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { "影视 · 在线" },
                color = AppColors.TextTertiary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = siteName,
                color = AppColors.Primary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
