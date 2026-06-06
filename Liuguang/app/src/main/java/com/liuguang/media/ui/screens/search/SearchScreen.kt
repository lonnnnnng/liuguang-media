package com.liuguang.media.ui.screens.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liuguang.media.ui.components.CinemaBackground
import com.liuguang.media.ui.components.CinemaSearchInput
import com.liuguang.media.ui.components.PageHeader
import com.liuguang.media.ui.theme.AppColors
import com.liuguang.media.ui.theme.Dimens

@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSearchResult: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    var searchText by remember { mutableStateOf("") }
    val searchHistory by viewModel.searchHistory.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun submitSearch(keyword: String = searchText) {
        val trimmed = keyword.trim()
        if (trimmed.isNotBlank()) {
            viewModel.addSearchHistory(trimmed)
            onNavigateToSearchResult(trimmed)
        }
    }

    CinemaBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = "搜索",
                onBackClick = onNavigateBack
            )

            CinemaSearchInput(
                value = searchText,
                placeholder = "输入关键词搜索",
                onValueChange = { searchText = it },
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
                horizontalPadding = 0.dp,
                focusRequester = focusRequester,
                trailingContent = {
                    if (searchText.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { searchText = "" },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "清空",
                                tint = AppColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            )

            SearchPrimaryButton(
                enabled = searchText.isNotBlank(),
                onClick = { submitSearch() }
            )

            if (searchHistory.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "搜索历史",
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.Black
                    )
                    TextButton(onClick = { viewModel.clearSearchHistory() }) {
                        Text("清空")
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 18.dp)
                ) {
                    items(searchHistory) { keyword ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RectangleShape)
                                .background(AppColors.Surface)
                                .border(BorderStroke(1.dp, AppColors.Divider), RectangleShape)
                                .clickable {
                                    searchText = keyword
                                    submitSearch(keyword)
                                }
                                .padding(Dimens.paddingMedium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = AppColors.Primary
                            )
                            Spacer(modifier = Modifier.width(Dimens.paddingMedium))
                            Text(
                                text = keyword,
                                color = AppColors.TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchPrimaryButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 18.dp),
        color = if (enabled) AppColors.Primary else AppColors.SurfaceRaised,
        contentColor = if (enabled) AppColors.OnPrimary else AppColors.TextTertiary,
        shape = RectangleShape,
        border = BorderStroke(1.dp, if (enabled) AppColors.Transparent else AppColors.Divider)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "搜索",
                fontWeight = FontWeight.Black
            )
        }
    }
}
