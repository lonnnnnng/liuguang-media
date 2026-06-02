package com.liuguang.media.ui.screens.sitemanagement

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VideoLibrary
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liuguang.media.data.local.entity.VideoSiteEntity
import com.liuguang.media.ui.components.CinemaBackground
import com.liuguang.media.ui.components.CinemaMessage
import com.liuguang.media.ui.components.PageHeader
import com.liuguang.media.ui.components.SourceCheckResultDialog
import com.liuguang.media.ui.components.SourceEditorDialog
import com.liuguang.media.ui.components.SourceUrlEditorDialog
import com.liuguang.media.ui.theme.AppColors
import com.liuguang.media.ui.theme.Dimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DEFAULT_VIDEO_SITE_IMPORT_URL =
    "https://raw.githubusercontent.com/MayLabPro/VideoSource/main/lite.json"

@Composable
fun SiteManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: SiteManagementViewModel = hiltViewModel()
) {
    val sites by viewModel.sites.collectAsState()
    val checkingSiteId by viewModel.checkingSiteId.collectAsState()
    val batchCheckingSiteIds by viewModel.batchCheckingSiteIds.collectAsState()
    val checkResultDialog by viewModel.checkResultDialog.collectAsState()
    val importUiState by viewModel.importUiState.collectAsState()
    val batchCheckUiState by viewModel.batchCheckUiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var editingSite by remember { mutableStateOf<VideoSiteEntity?>(null) }
    var deletingSite by remember { mutableStateOf<VideoSiteEntity?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    val isBusy = checkingSiteId != null || importUiState.isImporting || batchCheckUiState.isChecking

    CinemaBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = "视频源管理",
                onBackClick = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = { showImportDialog = true },
                        enabled = !isBusy
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "URL导入",
                            tint = if (!isBusy) AppColors.TextPrimary else AppColors.TextTertiary
                        )
                    }
                    IconButton(
                        onClick = viewModel::batchCheckSites,
                        enabled = sites.isNotEmpty() && !isBusy
                    ) {
                        if (batchCheckUiState.isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = AppColors.Primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "批量检测",
                                tint = if (sites.isNotEmpty() && !isBusy) AppColors.TextPrimary else AppColors.TextTertiary
                            )
                        }
                    }
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
                    IconButton(
                        onClick = { showClearDialog = true },
                        enabled = sites.isNotEmpty() && !isBusy
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "清空",
                            tint = if (sites.isNotEmpty() && !isBusy) AppColors.TextPrimary else AppColors.TextTertiary
                        )
                    }
                }
            )

            val statusMessage = batchCheckUiState.takeIf { it.isChecking }?.let {
                "${it.message.orEmpty()} (${it.currentIndex}/${it.total})"
            } ?: batchCheckUiState.message ?: importUiState.message
            statusMessage?.let { message ->
                ManagementStatusBanner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    message = message,
                    onDismiss = {
                        viewModel.consumeImportMessage()
                        viewModel.consumeBatchCheckMessage()
                    }
                )
            }

            if (sites.isEmpty()) {
                CinemaMessage(
                    modifier = Modifier.fillMaxSize(),
                    title = if (isBusy) "正在处理视频源" else "暂无视频源",
                    message = "添加资源站接口后，首页和搜索会自动使用启用的源。",
                    actionText = if (isBusy) null else "添加视频源",
                    onAction = if (isBusy) null else ({ showAddDialog = true })
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        items = sites,
                        key = { _, site -> site.id },
                        contentType = { _, _ -> "video-site-row" }
                    ) { index, site ->
                        SiteItem(
                            site = site,
                            canMoveUp = index > 0,
                            canMoveDown = index < sites.lastIndex,
                            onToggleEnabled = { viewModel.toggleEnabled(site) },
                            onEdit = { editingSite = site },
                            onDelete = { deletingSite = site },
                            onMoveUp = { viewModel.moveSiteUp(site) },
                            onMoveDown = { viewModel.moveSiteDown(site) },
                            onCheck = { viewModel.checkSite(site) },
                            onSetDefault = { viewModel.setDefaultSite(site) },
                            isChecking = checkingSiteId == site.id || site.id in batchCheckingSiteIds,
                            isCheckEnabled = !isBusy || checkingSiteId == site.id,
                            actionsEnabled = !isBusy
                        )
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        VideoSiteImportDialog(
            isImporting = importUiState.isImporting,
            onDismiss = { showImportDialog = false },
            onConfirm = { url ->
                viewModel.importSitesFromUrl(url)
                showImportDialog = false
            }
        )
    }

    if (showAddDialog) {
        SourceEditorDialog(
            title = "添加视频源",
            description = "配置 MacCMS 视频接口，保存后会用于片库、搜索和详情解析。",
            nameLabel = "视频源名称",
            urlLabel = "接口地址",
            urlPlaceholder = "https://example.com/api.php/provide/vod/",
            icon = Icons.Default.VideoLibrary,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url ->
                viewModel.addSite(name, url)
                showAddDialog = false
            }
        )
    }

    editingSite?.let { site ->
        SourceEditorDialog(
            title = "编辑视频源",
            initialName = site.name,
            initialUrl = site.apiUrl,
            description = "调整视频源名称或接口地址，不会影响已保存的其他源。",
            nameLabel = "视频源名称",
            urlLabel = "接口地址",
            urlPlaceholder = "https://example.com/api.php/provide/vod/",
            icon = Icons.Default.VideoLibrary,
            onDismiss = { editingSite = null },
            onConfirm = { name, url ->
                viewModel.updateSite(site.copy(name = name, apiUrl = url))
                editingSite = null
            }
        )
    }

    deletingSite?.let { site ->
        ConfirmDialog(
            title = "删除视频源",
            message = "确定要删除 ${site.name} 吗？",
            onDismiss = { deletingSite = null },
            onConfirm = {
                viewModel.deleteSite(site)
                deletingSite = null
            }
        )
    }

    if (showClearDialog) {
        ConfirmDialog(
            title = "清空视频源",
            message = "确定要清空所有视频源吗？可在设置页重置应用恢复默认源。",
            onDismiss = { showClearDialog = false },
            onConfirm = {
                viewModel.clearAllSites()
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
private fun SiteItem(
    site: VideoSiteEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onCheck: () -> Unit,
    onSetDefault: () -> Unit,
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
                        text = site.name,
                        color = AppColors.TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = site.apiUrl,
                        color = AppColors.TextSecondary,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SitePrimaryActionButton(
                        icon = if (site.isDefault) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (site.isDefault) "默认源" else "设为默认源",
                        enabled = actionsEnabled && !site.isDefault,
                        active = site.isDefault,
                        activeTint = Color(0xFFFACC15),
                        onClick = onSetDefault
                    )
                    SitePrimaryActionButton(
                        icon = Icons.Default.CheckCircle,
                        contentDescription = "检测",
                        enabled = isCheckEnabled || isChecking,
                        isLoading = isChecking,
                        onClick = onCheck
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                CompactEnabledSwitch(
                    checked = site.enabled,
                    onToggle = onToggleEnabled,
                    enabled = actionsEnabled
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SiteCheckMeta(
                    site = site,
                    modifier = Modifier.weight(1f),
                    isChecking = isChecking
                )
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    ManagementIconButton(
                        icon = Icons.Default.ArrowUpward,
                        contentDescription = "上移",
                        enabled = actionsEnabled && canMoveUp,
                        onClick = onMoveUp
                    )
                    ManagementIconButton(
                        icon = Icons.Default.ArrowDownward,
                        contentDescription = "下移",
                        enabled = actionsEnabled && canMoveDown,
                        onClick = onMoveDown
                    )
                    ManagementIconButton(
                        icon = Icons.Default.Edit,
                        contentDescription = "编辑",
                        enabled = actionsEnabled,
                        onClick = onEdit
                    )
                    ManagementIconButton(
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
private fun CompactEnabledSwitch(
    checked: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean
) {
    val trackColor = when {
        !enabled -> AppColors.SurfaceRaised
        checked -> AppColors.Primary
        else -> AppColors.SurfaceRaised
    }
    val thumbColor = if (enabled) AppColors.OnPrimary else AppColors.TextTertiary
    Surface(
        modifier = Modifier
            .size(width = 34.dp, height = 19.dp)
            .clickable(enabled = enabled, onClick = onToggle),
        color = trackColor,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (checked) AppColors.Primary else AppColors.DividerStrong)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
        ) {
            Surface(
                modifier = Modifier
                    .size(15.dp)
                    .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart),
                color = thumbColor,
                shape = CircleShape
            ) {}
        }
    }
}

@Composable
private fun SiteCheckMeta(
    site: VideoSiteEntity,
    modifier: Modifier = Modifier,
    isChecking: Boolean
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isChecking) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                color = AppColors.Primary,
                strokeWidth = 1.5.dp
            )
        }
        Text(
            text = if (isChecking) buildAnnotatedString { append("检测中") } else siteCheckMetaText(site),
            modifier = Modifier.weight(1f),
            color = AppColors.Primary,
            fontSize = 9.5.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SitePrimaryActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    active: Boolean = false,
    activeTint: Color = AppColors.Primary,
    onClick: () -> Unit
) {
    val tint = when {
        active -> activeTint
        enabled -> AppColors.TextPrimary
        else -> AppColors.TextTertiary
    }
    Box(
        modifier = Modifier
            .size(width = 24.dp, height = 19.dp)
            .clickable(enabled = enabled && !isLoading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(13.dp),
                color = AppColors.Primary,
                strokeWidth = 1.8.dp
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun ManagementIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clickable(enabled = enabled && !isLoading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                color = AppColors.Primary,
                strokeWidth = 1.7.dp
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = when {
                    active -> AppColors.Primary
                    enabled -> AppColors.TextPrimary
                    else -> AppColors.TextTertiary
                },
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

private fun siteCheckStatusLabel(site: VideoSiteEntity): String {
    if (site.lastCheckTime <= 0L) {
        return "未检测"
    }
    return site.lastCheckStatus.toSiteStatusLabel()
}

private fun siteCheckTimeLabel(site: VideoSiteEntity): String? {
    return site.lastCheckTime.takeIf { it > 0L }?.let(::formatCheckTime)
}

private fun siteLatencyLabel(site: VideoSiteEntity): String? {
    if (site.lastCheckTime <= 0L || site.lastLatencyMs <= 0L) return null
    return "${site.lastLatencyMs}ms"
}

private fun siteCheckMetaText(site: VideoSiteEntity) = buildAnnotatedString {
    withStyle(
        SpanStyle(
            color = siteCheckStatusColor(site),
            fontWeight = FontWeight.Bold
        )
    ) {
        append(siteCheckStatusLabel(site))
    }
    siteLatencyLabel(site)?.let { latency ->
        append(" · ")
        withStyle(
            SpanStyle(
                color = siteLatencyColor(site.lastLatencyMs),
                fontWeight = FontWeight.Bold
            )
        ) {
            append(latency)
        }
    }
    siteCheckTimeLabel(site)?.let { checkTime ->
        append(" · ")
        withStyle(SpanStyle(color = AppColors.TextTertiary)) {
            append(checkTime)
        }
    }
}

private fun siteCheckStatusColor(site: VideoSiteEntity): Color {
    return when {
        site.lastCheckTime <= 0L -> AppColors.TextTertiary
        site.lastCheckStatus == "可用" || site.lastCheckStatus == "可播放" -> AppColors.Success
        else -> AppColors.Error
    }
}

private fun siteLatencyColor(latencyMs: Long): Color {
    return when {
        latencyMs in 1..800 -> AppColors.Success
        latencyMs in 801..2500 -> AppColors.Warning
        latencyMs > 2500 -> AppColors.Error
        else -> AppColors.TextTertiary
    }
}

private fun String.toSiteStatusLabel(): String {
    return when (this) {
        "可用", "可播放" -> this
        else -> "接口异常"
    }
}

private fun formatCheckTime(timestamp: Long): String {
    return SourceCheckTimeFormatter.get().format(Date(timestamp))
}

private object SourceCheckTimeFormatter {
    private val formatter = ThreadLocal<SimpleDateFormat>()

    fun get(): SimpleDateFormat {
        return formatter.get() ?: SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).also {
            formatter.set(it)
        }
    }
}

@Composable
private fun ManagementStatusBanner(
    modifier: Modifier = Modifier,
    message: String,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = AppColors.Primary.copy(alpha = 0.10f),
        contentColor = AppColors.Primary,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.24f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = AppColors.TextPrimary
            )
            TextButton(onClick = onDismiss) {
                Text("关闭", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun VideoSiteImportDialog(
    isImporting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var url by remember { mutableStateOf(DEFAULT_VIDEO_SITE_IMPORT_URL) }

    SourceUrlEditorDialog(
        title = "导入视频源",
        initialUrl = url,
        description = "读取 lite.json 配置中的 name 和 api 字段，并自动跳过已存在地址。",
        urlLabel = "配置地址",
        urlPlaceholder = DEFAULT_VIDEO_SITE_IMPORT_URL,
        helperText = "支持从远程 URL 导入视频源配置，长链接可直接粘贴。",
        icon = Icons.Default.Link,
        confirmText = "导入",
        isConfirming = isImporting,
        dismissEnabled = !isImporting,
        onDismiss = onDismiss,
        onConfirm = { value ->
            url = value
            onConfirm(value)
        }
    )
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
