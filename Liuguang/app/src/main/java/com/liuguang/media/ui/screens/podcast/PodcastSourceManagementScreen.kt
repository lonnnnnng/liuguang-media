package com.liuguang.media.ui.screens.podcast

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liuguang.media.data.local.entity.PodcastSubscriptionEntity
import com.liuguang.media.ui.components.CinemaBackground
import com.liuguang.media.ui.components.CinemaMessage
import com.liuguang.media.ui.components.PageHeader
import com.liuguang.media.ui.components.SourceManagementIconButton
import com.liuguang.media.ui.components.SourceManagementMeta
import com.liuguang.media.ui.components.SourcePrimaryActionButton
import com.liuguang.media.ui.components.SourceUrlEditorDialog
import com.liuguang.media.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PodcastSourceManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: PodcastViewModel = hiltViewModel()
) {
    val sources by viewModel.subscriptions.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<PodcastSubscriptionEntity?>(null) }
    var deletingSource by remember { mutableStateOf<PodcastSubscriptionEntity?>(null) }
    val isBusy = uiState.isRefreshingSubscriptions || uiState.isRefreshingSubscriptionId != null || uiState.isAdding

    CinemaBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = "播客源管理",
                onBackClick = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = { showAddDialog = true },
                        enabled = !isBusy
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "添加播客源",
                            tint = if (!isBusy) AppColors.TextPrimary else AppColors.TextTertiary
                        )
                    }
                    IconButton(
                        onClick = viewModel::refreshSubscriptions,
                        enabled = sources.isNotEmpty() && !isBusy
                    ) {
                        if (uiState.isRefreshingSubscriptions) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = AppColors.Primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新播客源",
                                tint = if (sources.isNotEmpty() && !isBusy) AppColors.TextPrimary else AppColors.TextTertiary
                            )
                        }
                    }
                }
            )

            if (sources.isEmpty()) {
                CinemaMessage(
                    modifier = Modifier.fillMaxSize(),
                    title = "暂无播客源",
                    message = "添加 RSS 地址后即可在音频页和这里管理播客订阅。",
                    actionText = if (isBusy) null else "添加播客源",
                    onAction = if (isBusy) null else ({ showAddDialog = true })
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        items = sources,
                        key = { _, source -> source.id },
                        contentType = { _, _ -> "podcast-source-row" }
                    ) { _, source ->
                        PodcastSourceItem(
                            source = source,
                            onEdit = { editingSource = source },
                            onDelete = { deletingSource = source },
                            onRefresh = { viewModel.refreshSubscriptionItem(source) },
                            isRefreshing = uiState.isRefreshingSubscriptionId == source.id,
                            actionsEnabled = !isBusy
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        SourceUrlEditorDialog(
            title = "添加播客源",
            initialUrl = "",
            description = "粘贴播客 RSS 或 Atom 订阅地址，添加后会自动解析节目列表。",
            urlLabel = "订阅地址",
            urlPlaceholder = "https://example.com/podcast/feed.xml",
            onDismiss = { showAddDialog = false },
            onConfirm = { url ->
                viewModel.addSubscription(url)
                showAddDialog = false
            }
        )
    }

    editingSource?.let { source ->
        SourceUrlEditorDialog(
            title = "编辑播客源",
            initialUrl = source.url,
            description = "修改订阅地址后会重新读取播客信息，并更新节目数量和封面。",
            urlLabel = "订阅地址",
            urlPlaceholder = "https://example.com/podcast/feed.xml",
            onDismiss = { editingSource = null },
            onConfirm = { url ->
                viewModel.updateSubscription(source.copy(url = url))
                editingSource = null
            }
        )
    }

    deletingSource?.let { source ->
        AlertDialog(
            onDismissRequest = { deletingSource = null },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            title = { Text("删除播客源") },
            text = { Text("确定删除 ${source.title} 吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSubscription(source)
                    deletingSource = null
                }) {
                    Text("删除", color = AppColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSource = null }) {
                    Text("取消")
                }
            }
        )
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
private fun PodcastSourceItem(
    source: PodcastSubscriptionEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    actionsEnabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.Surface,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, AppColors.Divider)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = source.title,
                        color = AppColors.TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = source.url,
                        color = AppColors.TextSecondary,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SourcePrimaryActionButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = "刷新",
                    enabled = actionsEnabled || isRefreshing,
                    isLoading = isRefreshing,
                    onClick = onRefresh
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SourceManagementMeta(
                    text = podcastMetaText(source),
                    modifier = Modifier.weight(1f),
                    isLoading = isRefreshing,
                    loadingText = "刷新中"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    SourceManagementIconButton(
                        icon = Icons.Default.Edit,
                        contentDescription = "编辑",
                        enabled = actionsEnabled,
                        onClick = onEdit
                    )
                    SourceManagementIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = "删除",
                        enabled = actionsEnabled,
                        onClick = onDelete
                    )
                }
            }
        }
    }
}

private fun formatRefreshTime(value: Long): String {
    if (value <= 0) return "未刷新"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
}

private fun podcastMetaText(source: PodcastSubscriptionEntity) = buildAnnotatedString {
    withStyle(
        SpanStyle(
            color = AppColors.Success,
            fontWeight = FontWeight.Bold
        )
    ) {
        append("${source.episodeCount} 期")
    }
    append(" · ")
    withStyle(SpanStyle(color = AppColors.TextTertiary)) {
        append(formatRefreshTime(source.lastRefreshTime))
    }
}
