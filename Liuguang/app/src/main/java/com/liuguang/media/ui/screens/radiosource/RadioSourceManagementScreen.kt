package com.liuguang.media.ui.screens.radiosource

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liuguang.media.data.local.DefaultSources
import com.liuguang.media.data.local.entity.RadioSourceEntity
import com.liuguang.media.ui.components.CinemaBackground
import com.liuguang.media.ui.components.PageHeader
import com.liuguang.media.ui.components.SourceBulkImportDialog
import com.liuguang.media.ui.components.SourceCheckResultDialog
import com.liuguang.media.ui.components.SourceCheckStatusMeta
import com.liuguang.media.ui.components.SourceCompactEnabledSwitch
import com.liuguang.media.ui.components.SourceEditorDialog
import com.liuguang.media.ui.components.SourceManagementEmptyState
import com.liuguang.media.ui.components.SourceManagementIconButton
import com.liuguang.media.ui.components.SourceManagementTopActionButton
import com.liuguang.media.ui.components.SourceOperationProgress
import com.liuguang.media.ui.components.SourcePrimaryActionButton
import com.liuguang.media.ui.theme.AppColors

@Composable
fun RadioSourceManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: RadioSourceManagementViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val sources by viewModel.sources.collectAsState()
    val checkingSourceId by viewModel.checkingSourceId.collectAsState()
    val batchCheckingSourceIds by viewModel.batchCheckingSourceIds.collectAsState()
    val checkResultDialog by viewModel.checkResultDialog.collectAsState()
    val importUiState by viewModel.importUiState.collectAsState()
    val batchUiState by viewModel.batchUiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<RadioSourceEntity?>(null) }
    var deletingSource by remember { mutableStateOf<RadioSourceEntity?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    val isBusy = checkingSourceId != null || importUiState.isImporting || batchUiState.isRunning

    LaunchedEffect(importUiState.isImporting, importUiState.message) {
        val message = importUiState.message ?: return@LaunchedEffect
        if (!importUiState.isImporting) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            if (message.startsWith("新增")) {
                showImportDialog = false
            }
            viewModel.consumeImportMessage()
        }
    }

    LaunchedEffect(batchUiState.isRunning, batchUiState.message) {
        val message = batchUiState.message ?: return@LaunchedEffect
        if (!batchUiState.isRunning) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.consumeBatchMessage()
        }
    }

    CinemaBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = "电台源管理",
                onBackClick = onNavigateBack,
                actions = {
                    SourceManagementTopActionButton(
                        icon = Icons.Default.Add,
                        contentDescription = "新增电台源",
                        enabled = !isBusy,
                        onClick = { showAddDialog = true }
                    )
                    SourceManagementTopActionButton(
                        icon = Icons.Default.Link,
                        contentDescription = "URL导入电台源",
                        enabled = !isBusy,
                        isLoading = importUiState.isImporting,
                        onClick = { showImportDialog = true }
                    )
                    SourceManagementTopActionButton(
                        icon = Icons.Default.CheckCircle,
                        contentDescription = "批量测试连通性",
                        enabled = sources.isNotEmpty() && !isBusy,
                        isLoading = batchUiState.isRunning,
                        onClick = viewModel::batchCheckSources
                    )
                    SourceManagementTopActionButton(
                        icon = Icons.Default.Delete,
                        contentDescription = "批量删除电台源",
                        enabled = sources.isNotEmpty() && !isBusy,
                        onClick = { showClearDialog = true }
                    )
                }
            )

            if (batchUiState.isRunning) {
                SourceOperationProgress(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    message = batchUiState.message ?: "正在检测电台源",
                    currentIndex = batchUiState.currentIndex,
                    total = batchUiState.total
                )
            } else if (checkingSourceId != null) {
                val checkingSourceName = sources.firstOrNull { it.id == checkingSourceId }?.name ?: "电台源"
                SourceOperationProgress(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    message = "正在检测：$checkingSourceName",
                    currentIndex = 0,
                    total = 0
                )
            }

            if (sources.isEmpty()) {
                SourceManagementEmptyState(
                    modifier = Modifier.fillMaxSize(),
                    title = "暂无电台源",
                    message = "新增 M3U、PLS、JSON 或 Radio Browser 地址后，电台页会解析列表。",
                    icon = Icons.Default.Radio,
                    primaryActionText = "新增",
                    onPrimaryAction = { showAddDialog = true },
                    secondaryActionText = "URL导入",
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
                        contentType = { _, _ -> "radio-source-row" }
                    ) { index, source ->
                        RadioSourceItem(
                            source = source,
                            canMoveUp = index > 0,
                            canMoveDown = index < sources.lastIndex,
                            onToggleEnabled = { viewModel.toggleEnabled(source) },
                            onEdit = { editingSource = source },
                            onDelete = { deletingSource = source },
                            onMoveUp = { viewModel.moveSourceUp(source) },
                            onMoveDown = { viewModel.moveSourceDown(source) },
                            onCheck = { viewModel.checkSource(source) },
                            isChecking = checkingSourceId == source.id || source.id in batchCheckingSourceIds,
                            isCheckEnabled = !isBusy || checkingSourceId == source.id,
                            actionsEnabled = !isBusy
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        SourceEditorDialog(
            title = "新增电台源",
            description = "配置网络电台源地址，保存后会用于电台列表聚合与播放。",
            nameLabel = "电台源名称",
            urlLabel = "源地址",
            urlPlaceholder = "https://example.com/radio/list.json",
            icon = Icons.Default.Radio,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url ->
                viewModel.addSource(name, url)
                showAddDialog = false
            }
        )
    }

    if (showImportDialog) {
        SourceBulkImportDialog(
            title = "URL导入电台源",
            description = "每行一个电台源，支持“名称,URL”；只有 URL 时会自动命名。",
            initialValue = DefaultSources.DEFAULT_RADIO_IMPORT_URL,
            placeholder = DefaultSources.DEFAULT_RADIO_IMPORT_URL,
            helperText = "支持 M3U、PLS、JSON、Radio Browser 或直连音频地址，自动跳过重复 URL。",
            icon = Icons.Default.Radio,
            isImporting = importUiState.isImporting,
            bottomContent = {
                if (importUiState.isImporting) {
                    SourceOperationProgress(
                        message = importUiState.message ?: "正在导入电台源",
                        currentIndex = importUiState.currentIndex,
                        total = importUiState.total
                    )
                }
            },
            onDismiss = { showImportDialog = false },
            onConfirm = { rawText ->
                viewModel.importSources(rawText)
            }
        )
    }

    editingSource?.let { source ->
        SourceEditorDialog(
            title = "编辑电台源",
            initialName = source.name,
            initialUrl = source.url,
            description = "调整电台源名称或地址，电台列表会按新地址刷新。",
            nameLabel = "电台源名称",
            urlLabel = "源地址",
            urlPlaceholder = "https://example.com/radio/list.json",
            icon = Icons.Default.Radio,
            onDismiss = { editingSource = null },
            onConfirm = { name, url ->
                viewModel.updateSource(source.copy(name = name, url = url))
                editingSource = null
            }
        )
    }

    deletingSource?.let { source ->
        ConfirmDialog(
            title = "删除电台源",
            message = "确定要删除 ${source.name} 吗？",
            onDismiss = { deletingSource = null },
            onConfirm = {
                viewModel.deleteSource(source)
                deletingSource = null
            }
        )
    }

    if (showClearDialog) {
        ConfirmDialog(
            title = "批量删除电台源",
            message = "确定要删除所有电台源吗？删除后音频页电台列表会显示为空。",
            onDismiss = { showClearDialog = false },
            onConfirm = {
                viewModel.clearAllSources()
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
}

@Composable
private fun RadioSourceItem(
    source: RadioSourceEntity,
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
        shape = RectangleShape,
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
                        text = source.name,
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
