package com.liuguang.media.ui.screens.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liuguang.media.data.local.entity.HistoryEntity
import com.liuguang.media.ui.components.CinemaBackground
import com.liuguang.media.ui.components.CinemaMessage
import com.liuguang.media.ui.components.NetworkImage
import com.liuguang.media.ui.components.PageHeader
import com.liuguang.media.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (Long, String, String, String, String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val historyList by viewModel.historyList.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    CinemaBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = "播放历史",
                onBackClick = onNavigateBack,
                actions = {
                    if (historyList.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "清空",
                                tint = AppColors.TextPrimary
                            )
                        }
                    }
                }
            )

            if (historyList.isEmpty()) {
                CinemaMessage(
                    modifier = Modifier.fillMaxSize(),
                    title = "暂无播放历史",
                    message = "从详情页播放一集后，这里会显示续播入口。"
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(contentType = "history-overview") {
                        HistoryOverviewCard(
                            totalCount = historyList.size,
                            lastPlayTime = historyList.firstOrNull()?.lastPlayTime
                        )
                    }
                    items(
                        items = historyList,
                        key = { history -> history.key },
                        contentType = { "history-row" }
                    ) { history ->
                        HistoryItem(
                            history = history,
                            onClick = {
                                onNavigateToPlayer(
                                    history.siteId,
                                    history.vodId,
                                    history.episodeUrl,
                                    history.vodName,
                                    history.episodeLabel
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            title = { Text("清空历史") },
            text = { Text("确定要清空所有播放历史吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearDialog = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun HistoryItem(
    history: HistoryEntity,
    onClick: () -> Unit
) {
    val progress = history.progress()
    val hasProgress = history.durationMs > 0

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = AppColors.SurfaceSoft,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, AppColors.Divider)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(86.dp)
                    .height(122.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(AppColors.SurfaceRaised)
            ) {
                NetworkImage(
                    url = history.vodPic,
                    contentDescription = history.vodName,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                    color = AppColors.Primary,
                    contentColor = AppColors.OnPrimary,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = if (hasProgress) progressLabel(progress) else "续播",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 122.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = history.vodName,
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HistoryChip(
                            text = history.episodeLabel,
                            icon = Icons.Default.PlayArrow,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        HistoryChip(
                            text = formatTime(history.lastPlayTime),
                            icon = Icons.Default.Schedule
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (hasProgress) {
                                "${formatDuration(history.positionMs)} / ${formatDuration(history.durationMs)}"
                            } else {
                                "点击继续播放"
                            },
                            color = AppColors.TextTertiary,
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Button(
                            onClick = onClick,
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppColors.Primary,
                                contentColor = AppColors.OnPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = "继续",
                                fontSize = 12.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = AppColors.Primary,
                        trackColor = AppColors.SurfaceRaised
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryOverviewCard(
    totalCount: Int,
    lastPlayTime: Long?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.PrimaryLight,
        contentColor = AppColors.TextPrimary,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                color = AppColors.Surface,
                contentColor = AppColors.Primary,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.14f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "继续观看",
                    color = AppColors.TextPrimary,
                    fontSize = 17.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = buildString {
                        append("$totalCount 条播放记录")
                        lastPlayTime?.let {
                            append(" · 最近")
                            append(formatTime(it))
                        }
                    },
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HistoryChip(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = AppColors.Surface,
        contentColor = AppColors.TextSecondary,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, AppColors.Divider)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = AppColors.Primary
            )
            Text(
                text = text,
                color = AppColors.TextSecondary,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun HistoryEntity.progress(): Float {
    return if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
}

private fun progressLabel(progress: Float): String {
    return "${(progress * 100).toInt().coerceIn(0, 100)}%"
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000}分钟前"
        diff < 86400_000 -> "${diff / 3600_000}小时前"
        diff < 604800_000 -> "${diff / 86400_000}天前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}
