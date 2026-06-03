package com.liuguang.media.ui.screens.podcast

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liuguang.media.data.local.entity.PodcastSubscriptionEntity
import com.liuguang.media.ui.components.CinemaBackground
import com.liuguang.media.ui.components.PageHeader
import com.liuguang.media.ui.components.SourceBulkImportDialog
import com.liuguang.media.ui.components.SourceCheckResultDialog
import com.liuguang.media.ui.components.SourceCheckStatusMeta
import com.liuguang.media.ui.components.SourceCompactEnabledSwitch
import com.liuguang.media.ui.components.SourceManagementEmptyState
import com.liuguang.media.ui.components.SourceManagementIconButton
import com.liuguang.media.ui.components.SourceManagementStatusBanner
import com.liuguang.media.ui.components.SourceManagementTopActionButton
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
    val checkingSubscriptionId by viewModel.checkingSubscriptionId.collectAsState()
    val batchCheckingSubscriptionIds by viewModel.batchCheckingSubscriptionIds.collectAsState()
    val checkResultDialog by viewModel.checkResultDialog.collectAsState()
    val importUiState by viewModel.importUiState.collectAsState()
    val batchUiState by viewModel.batchUiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<PodcastSubscriptionEntity?>(null) }
    var deletingSource by remember { mutableStateOf<PodcastSubscriptionEntity?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    val isBusy = uiState.isAdding ||
        uiState.isRefreshingSubscriptionId != null ||
        checkingSubscriptionId != null ||
        importUiState.isImporting ||
        batchUiState.isRunning

    LaunchedEffect(uiState.isAdding, uiState.message, showAddDialog) {
        if (showAddDialog && !uiState.isAdding && uiState.message?.startsWith("已订阅") == true) {
            showAddDialog = false
        }
    }

    LaunchedEffect(uiState.isRefreshingSubscriptionId, uiState.message, editingSource) {
        if (
            editingSource != null &&
            uiState.isRefreshingSubscriptionId == null &&
            uiState.message?.startsWith("已更新") == true
        ) {
            editingSource = null
        }
    }

    CinemaBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = "播客源管理",
                onBackClick = onNavigateBack,
                actions = {
                    SourceManagementTopActionButton(
                        icon = Icons.Default.Add,
                        contentDescription = "单个新增播客源",
                        enabled = !isBusy,
                        onClick = { showAddDialog = true }
                    )
                    SourceManagementTopActionButton(
                        icon = Icons.Default.Link,
                        contentDescription = "批量导入播客源",
                        enabled = !isBusy,
                        isLoading = importUiState.isImporting,
                        onClick = { showImportDialog = true }
                    )
                    SourceManagementTopActionButton(
                        icon = Icons.Default.CheckCircle,
                        contentDescription = "批量测试连通性",
                        enabled = sources.isNotEmpty() && !isBusy,
                        isLoading = batchUiState.isRunning,
                        onClick = viewModel::batchCheckSubscriptions
                    )
                    SourceManagementTopActionButton(
                        icon = Icons.Default.Delete,
                        contentDescription = "批量删除播客源",
                        enabled = sources.isNotEmpty() && !isBusy,
                        onClick = { showClearDialog = true }
                    )
                }
            )

            val statusMessage = batchUiState.takeIf { it.isRunning }?.let {
                "${it.message.orEmpty()} (${it.currentIndex}/${it.total})"
            } ?: batchUiState.message ?: importUiState.message
            statusMessage?.let { message ->
                SourceManagementStatusBanner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    message = message,
                    onDismiss = {
                        viewModel.consumeImportMessage()
                        viewModel.consumeBatchMessage()
                    }
                )
            }

            if (sources.isEmpty()) {
                SourceManagementEmptyState(
                    modifier = Modifier.fillMaxSize(),
                    title = "暂无播客源",
                    message = "添加 RSS 或 Atom 地址后，音频页会聚合播客节目。",
                    icon = Icons.Default.RssFeed,
                    primaryActionText = "单个新增",
                    onPrimaryAction = { showAddDialog = true },
                    secondaryActionText = "批量导入",
                    onSecondaryAction = { showImportDialog = true },
                    actionsEnabled = !isBusy
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
                    ) { index, source ->
                        PodcastSourceItem(
                            source = source,
                            canMoveUp = index > 0,
                            canMoveDown = index < sources.lastIndex,
                            onToggleEnabled = { viewModel.toggleSubscriptionEnabled(source) },
                            onEdit = { editingSource = source },
                            onDelete = { deletingSource = source },
                            onMoveUp = { viewModel.moveSubscriptionUp(source) },
                            onMoveDown = { viewModel.moveSubscriptionDown(source) },
                            onCheck = { viewModel.checkSubscription(source) },
                            isChecking = checkingSubscriptionId == source.id || source.id in batchCheckingSubscriptionIds,
                            isCheckEnabled = !isBusy || checkingSubscriptionId == source.id,
                            actionsEnabled = !isBusy
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        PodcastSourceEditorDialog(
            title = "添加播客源",
            initialUrl = "",
            source = null,
            isConfirming = uiState.isAdding,
            onDismiss = { showAddDialog = false },
            onConfirm = viewModel::addSubscription
        )
    }

    if (showImportDialog) {
        SourceBulkImportDialog(
            title = "批量导入播客源",
            description = "每行一个播客源地址，支持 RSS / Atom；只有 URL 时会自动解析标题。",
            placeholder = "https://example.com/podcast/feed.xml\nhttps://example.com/show/rss",
            helperText = "导入时会解析订阅标题和节目数量，并自动跳过重复 URL。",
            icon = Icons.Default.RssFeed,
            isImporting = importUiState.isImporting,
            onDismiss = { showImportDialog = false },
            onConfirm = { rawText ->
                viewModel.importSubscriptions(rawText)
                showImportDialog = false
            }
        )
    }

    editingSource?.let { source ->
        PodcastSourceEditorDialog(
            title = "编辑播客源",
            initialUrl = source.url,
            source = source,
            isConfirming = uiState.isRefreshingSubscriptionId == source.id,
            onDismiss = { editingSource = null },
            onConfirm = { url ->
                viewModel.updateSubscription(source.copy(url = url))
            }
        )
    }

    deletingSource?.let { source ->
        ConfirmDialog(
            title = "删除播客源",
            message = "确定删除 ${source.title} 吗？",
            onDismiss = { deletingSource = null },
            onConfirm = {
                viewModel.deleteSubscription(source)
                deletingSource = null
            }
        )
    }

    if (showClearDialog) {
        ConfirmDialog(
            title = "批量删除播客源",
            message = "确定要删除所有播客源吗？音频页将不再聚合播客节目。",
            onDismiss = { showClearDialog = false },
            onConfirm = {
                viewModel.clearAllSubscriptions()
                showClearDialog = false
            }
        )
    }

    checkResultDialog?.let { state ->
        SourceCheckResultDialog(
            state = state,
            onDismiss = viewModel::dismissCheckResultDialog
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
private fun PodcastSourceEditorDialog(
    title: String,
    initialUrl: String,
    source: PodcastSubscriptionEntity?,
    isConfirming: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    SourceUrlEditorDialog(
        title = title,
        initialUrl = initialUrl,
        description = if (source == null) {
            "添加公开可访问的订阅地址，流光会自动解析节目、封面和更新信息。"
        } else {
            "修改后会按新地址重新同步播客信息，并保留源列表中的管理入口。"
        },
        urlLabel = "Feed 地址",
        urlPlaceholder = "https://example.com/podcast/feed.xml",
        helperText = "支持 RSS / Atom。长链接可粘贴后多行编辑。",
        icon = Icons.Default.RssFeed,
        confirmText = if (source == null) "添加并同步" else "保存并刷新",
        isConfirming = isConfirming,
        dismissEnabled = !isConfirming,
        topContent = {
            PodcastEditorGuideCard()
        },
        bottomContent = {
            source?.let { PodcastEditorCurrentCard(it) }
        },
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

@Composable
private fun PodcastEditorGuideCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.PrimaryLight,
        contentColor = AppColors.TextPrimary,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.16f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    color = AppColors.Surface,
                    contentColor = AppColors.Primary,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.14f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.RssFeed,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "播客订阅源",
                        color = AppColors.TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "保存后会同步标题、封面和最新节目。",
                        color = AppColors.TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PodcastFormatChip("RSS")
                PodcastFormatChip("Atom")
                PodcastFormatChip("音频附件")
            }
        }
    }
}

@Composable
private fun PodcastEditorCurrentCard(source: PodcastSubscriptionEntity) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.SurfaceAlt,
        contentColor = AppColors.TextPrimary,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, AppColors.Divider)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "当前订阅",
                    color = AppColors.TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "${source.episodeCount} 期",
                    color = AppColors.Primary,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Text(
                text = source.title,
                color = AppColors.TextPrimary,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = AppColors.TextTertiary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = source.url,
                    modifier = Modifier.weight(1f),
                    color = AppColors.TextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (source.lastRefreshTime > 0L) {
                Text(
                    text = "上次同步 ${formatRefreshTime(source.lastRefreshTime)}",
                    color = AppColors.TextTertiary,
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                )
            }
        }
    }
}

@Composable
private fun PodcastFormatChip(text: String) {
    Surface(
        color = AppColors.Surface,
        contentColor = AppColors.Primary,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier
                .height(26.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = text,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PodcastSourceItem(
    source: PodcastSubscriptionEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onCheck: () -> Unit,
    isChecking: Boolean,
    isCheckEnabled: Boolean,
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = source.title,
                            modifier = Modifier.weight(1f, fill = false),
                            color = AppColors.TextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${source.episodeCount} 期",
                            color = AppColors.Primary,
                            fontSize = 9.5.sp,
                            lineHeight = 12.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1
                        )
                    }
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
                    icon = Icons.Default.CheckCircle,
                    contentDescription = "检测",
                    enabled = isCheckEnabled || isChecking,
                    isLoading = isChecking,
                    onClick = onCheck
                )
                Spacer(modifier = Modifier.width(6.dp))
                SourceCompactEnabledSwitch(
                    checked = source.enabled,
                    onToggle = onToggleEnabled,
                    enabled = actionsEnabled
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SourceCheckStatusMeta(
                    lastCheckStatus = source.lastCheckStatus,
                    lastCheckTime = source.lastCheckTime,
                    modifier = Modifier.weight(1f),
                    isChecking = isChecking
                )
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    SourceManagementIconButton(
                        icon = Icons.Default.ArrowUpward,
                        contentDescription = "上移",
                        enabled = actionsEnabled && canMoveUp,
                        onClick = onMoveUp
                    )
                    SourceManagementIconButton(
                        icon = Icons.Default.ArrowDownward,
                        contentDescription = "下移",
                        enabled = actionsEnabled && canMoveDown,
                        onClick = onMoveDown
                    )
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

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        titleContentColor = AppColors.TextPrimary,
        textContentColor = AppColors.TextSecondary,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
