package com.liuguang.media.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun StateLayout(
    isLoading: Boolean,
    isError: Boolean,
    isEmpty: Boolean,
    errorMessage: String? = null,
    emptyMessage: String = "暂无数据",
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            isLoading -> CinemaLoading(modifier = Modifier.fillMaxSize())
            isError -> CinemaMessage(
                modifier = Modifier.fillMaxSize(),
                title = "加载失败",
                message = errorMessage,
                actionText = if (onRetry != null) "重试" else null,
                onAction = onRetry
            )
            isEmpty -> CinemaMessage(
                modifier = Modifier.fillMaxSize(),
                title = emptyMessage,
                message = "换个关键词或切换资源站再试。",
                actionText = if (onRetry != null) "刷新" else null,
                onAction = onRetry
            )
            else -> {
                content()
            }
        }
    }
}
