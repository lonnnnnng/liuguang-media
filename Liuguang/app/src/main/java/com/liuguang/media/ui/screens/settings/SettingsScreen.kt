package com.liuguang.media.ui.screens.settings

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.liuguang.media.ui.components.CinemaBackground
import com.liuguang.media.ui.theme.AppColors
import java.io.File

@Composable
fun SettingsScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToOnline: () -> Unit,
    onNavigateToPodcastSourceManagement: () -> Unit,
    onNavigateToSiteManagement: () -> Unit,
    onNavigateToLiveSourceManagement: () -> Unit,
    onNavigateToRadioSourceManagement: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var showDisclaimerDialog by remember { mutableStateOf(false) }
    var showNetworkSettingsDialog by remember { mutableStateOf(false) }
    val maintenanceMessage by viewModel.maintenanceMessage.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val updateUiState by viewModel.updateUiState.collectAsState()
    val networkSettings by viewModel.networkSettings.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(toastMessage) {
        val message = toastMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.consumeToastMessage()
    }

    LaunchedEffect(updateUiState.installFile) {
        val apkFile = updateUiState.installFile ?: return@LaunchedEffect
        runCatching {
            installApk(context, apkFile)
        }.onSuccess {
            viewModel.consumeInstallFile()
        }.onFailure { error ->
            viewModel.reportInstallLaunchFailed(error)
        }
    }

    CinemaBackground(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                Text(
                    text = "设置",
                    color = AppColors.TextPrimary,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Default.History,
                        title = "播放历史",
                        subtitle = "继续观看、进度同步、最近播放",
                        onClick = onNavigateToHistory
                    )
                    SettingsItem(
                        icon = Icons.Default.NetworkCheck,
                        title = "网络设置",
                        subtitle = "检测超时、播放切线、自动检测",
                        onClick = { showNetworkSettingsDialog = true }
                    )
                    SettingsItem(
                        icon = Icons.Default.Link,
                        title = "在线播放",
                        subtitle = "粘贴 m3u8 或 m3u 链接解析播放",
                        onClick = onNavigateToOnline
                    )
                    SettingsItem(
                        icon = Icons.Default.VideoLibrary,
                        title = "视频源管理",
                        subtitle = "资源站排序、检测、启用状态",
                        onClick = onNavigateToSiteManagement
                    )
                    SettingsItem(
                        icon = Icons.Default.LiveTv,
                        title = "直播源管理",
                        subtitle = "M3U 源、频道解析、线路刷新",
                        onClick = onNavigateToLiveSourceManagement,
                    )
                    SettingsItem(
                        icon = Icons.Default.Radio,
                        title = "电台源管理",
                        subtitle = "M3U、PLS、JSON、网络电台源",
                        onClick = onNavigateToRadioSourceManagement,
                    )
                    SettingsItem(
                        icon = Icons.Default.RssFeed,
                        title = "播客源管理",
                        subtitle = "管理 RSS 订阅源、刷新与删除",
                        onClick = onNavigateToPodcastSourceManagement
                    )
                    SettingsItem(
                        icon = Icons.Default.Download,
                        title = "检测更新",
                        subtitle = if (updateUiState.isChecking) {
                            "正在检查最新版本"
                        } else {
                            "当前版本 ${updateUiState.currentVersion}"
                        },
                        onClick = viewModel::checkForUpdates
                    )
                    SettingsItem(
                        icon = Icons.Default.RestartAlt,
                        title = "重置应用",
                        subtitle = "清空历史和全部源",
                        onClick = { showResetDialog = true }
                    )
                    SettingsItem(
                        icon = Icons.Default.Report,
                        title = "免责声明",
                        subtitle = "内容来源、播放责任、版权说明",
                        onClick = { showDisclaimerDialog = true },
                        showDivider = false
                    )
                }
            }
        }
    }

    if (showNetworkSettingsDialog) {
        var videoTimeout by remember(networkSettings) {
            mutableStateOf(networkSettings.videoSourceTimeoutSeconds.toString())
        }
        var liveTimeout by remember(networkSettings) {
            mutableStateOf(networkSettings.liveSourceTimeoutSeconds.toString())
        }
        var autoCheckInterval by remember(networkSettings) {
            mutableStateOf(networkSettings.autoCheckIntervalMinutes?.toString().orEmpty())
        }

        AlertDialog(
            onDismissRequest = { showNetworkSettingsDialog = false },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            title = { Text("网络设置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NetworkNumberField(
                        value = videoTimeout,
                        label = "视频源超时（秒）",
                        onValueChange = { videoTimeout = it }
                    )
                    NetworkNumberField(
                        value = liveTimeout,
                        label = "直播源超时（秒）",
                        onValueChange = { liveTimeout = it }
                    )
                    NetworkNumberField(
                        value = autoCheckInterval,
                        label = "自动检测视频源（分钟，可留空）",
                        onValueChange = { autoCheckInterval = it }
                    )
                    Text(
                        text = "搜索、源检测、播放自动切换都会使用这里的超时时间。",
                        color = AppColors.TextTertiary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (viewModel.saveNetworkSettings(videoTimeout, liveTimeout, autoCheckInterval)) {
                            showNetworkSettingsDialog = false
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNetworkSettingsDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            title = { Text("重置应用") },
            text = { Text("确定要清空播放历史和全部源吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetApp()
                    showResetDialog = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    maintenanceMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::consumeMaintenanceMessage,
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            title = { Text("应用维护") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::consumeMaintenanceMessage) {
                    Text("知道了")
                }
            }
        )
    }

    updateUiState.updateInfo?.let { updateInfo ->
        AlertDialog(
            onDismissRequest = viewModel::dismissUpdateDialog,
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            title = { Text("发现新版本") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        VersionLine(label = "当前版本", value = updateInfo.currentVersion)
                        VersionLine(label = "新版本", value = updateInfo.latestVersion)
                        VersionLine(
                            label = "安装包",
                            value = formatApkSize(updateInfo.apkSize)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "更新说明",
                            color = AppColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = updateInfo.releaseNotes,
                            color = AppColors.TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (updateUiState.isDownloading || updateUiState.downloadProgress > 0) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LinearProgressIndicator(
                                progress = { updateUiState.downloadProgress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = AppColors.Primary,
                                trackColor = AppColors.Divider
                            )
                            Text(
                                text = "下载进度 ${updateUiState.downloadProgress}%",
                                color = AppColors.TextTertiary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::downloadUpdate,
                    enabled = !updateUiState.isDownloading
                ) {
                    Text(if (updateUiState.downloadProgress >= 100) "打开安装" else "下载更新")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissUpdateDialog,
                    enabled = !updateUiState.isDownloading
                ) {
                    Text("关闭")
                }
            }
        )
    }

    if (showDisclaimerDialog) {
        AlertDialog(
            onDismissRequest = { showDisclaimerDialog = false },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            title = { Text("免责声明") },
            text = {
                Text(
                    "本应用仅供学习交流使用，所有视频内容均来自互联网，" +
                        "本应用不存储任何视频内容，不对内容的合法性、准确性、完整性负责。" +
                        "请用户自行甄别内容的真实性和合法性，如有侵权请联系删除。"
                )
            },
            confirmButton = {
                TextButton(onClick = { showDisclaimerDialog = false }) {
                    Text("知道了")
                }
            }
        )
    }
}

@Composable
private fun NetworkNumberField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            onValueChange(input.filter { it.isDigit() })
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun VersionLine(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = AppColors.TextTertiary,
            fontSize = 12.sp
        )
        Text(
            text = value,
            color = AppColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .padding(vertical = 0.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Divider, RoundedCornerShape(4.dp))
    ) {
        content()
    }
}

private fun installApk(context: Context, apkFile: File) {
    val apkUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apkFile
    )
    val installIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(apkUri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        clipData = ClipData.newUri(context.contentResolver, "流光更新包", apkUri)
    }
    context.startActivity(installIntent)
}

private fun formatApkSize(size: Long): String {
    if (size <= 0L) return "未知大小"
    val megabytes = size / 1024f / 1024f
    return "%.1f MB".format(megabytes)
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showDivider: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(AppColors.PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AppColors.Primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 9.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = title,
                    color = AppColors.TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = AppColors.TextTertiary,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 52.dp),
                color = AppColors.Divider
            )
        }
    }
}
