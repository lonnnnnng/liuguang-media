package com.liuguang.media.ui.screens.radiosource

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liuguang.media.data.local.entity.RadioSourceEntity
import com.liuguang.media.ui.components.CinemaBackground
import com.liuguang.media.ui.components.CinemaMessage
import com.liuguang.media.ui.components.PageHeader
import com.liuguang.media.ui.components.SourceCheckResultDialog
import com.liuguang.media.ui.components.SourceEditorDialog
import com.liuguang.media.ui.components.SourceCheckStatusMeta
import com.liuguang.media.ui.components.SourceCompactEnabledSwitch
import com.liuguang.media.ui.components.SourceManagementIconButton
import com.liuguang.media.ui.components.SourcePrimaryActionButton
import com.liuguang.media.ui.theme.AppColors

@Composable
fun RadioSourceManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: RadioSourceManagementViewModel = hiltViewModel()
) {
    val sources by viewModel.sources.collectAsState()
    val checkingSourceId by viewModel.checkingSourceId.collectAsState()
    val checkResultDialog by viewModel.checkResultDialog.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<RadioSourceEntity?>(null) }
    var deletingSource by remember { mutableStateOf<RadioSourceEntity?>(null) }
    val isBusy = checkingSourceId != null

    CinemaBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = "电台源管理",
                onBackClick = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = { showAddDialog = true },
                        enabled = !isBusy
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "添加",
                            tint = if (!isBusy) AppColors.TextPrimary else AppColors.TextTertiary
                        )
                    }
                }
            )

            if (sources.isEmpty()) {
                CinemaMessage(
                    modifier = Modifier.fillMaxSize(),
                    title = "暂无电台源",
                    message = "添加 M3U、PLS、JSON 或 Radio Browser 地址后，电台页会解析列表。",
                    actionText = if (isBusy) null else "添加电台源",
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
                            isChecking = checkingSourceId == source.id,
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
            title = "添加电台源",
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
