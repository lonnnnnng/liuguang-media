package com.liuguang.media.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liuguang.media.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SourceImportUiState(
    val isImporting: Boolean = false,
    val currentIndex: Int = 0,
    val total: Int = 0,
    val message: String? = null
)

data class SourceBatchUiState(
    val isRunning: Boolean = false,
    val currentIndex: Int = 0,
    val total: Int = 0,
    val message: String? = null
)

@Composable
fun SourceManagementTopActionButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = Modifier.size(42.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(19.dp),
                color = AppColors.Primary,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) AppColors.TextPrimary else AppColors.TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SourceManagementStatusBanner(
    modifier: Modifier = Modifier,
    message: String,
    progress: Float? = null,
    indeterminateProgress: Boolean = false,
    showDismiss: Boolean = true,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = AppColors.Primary.copy(alpha = 0.10f),
        contentColor = AppColors.Primary,
        shape = RectangleShape,
        border = BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message,
                    modifier = Modifier.weight(1f),
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    color = AppColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (showDismiss) {
                    TextButton(onClick = onDismiss) {
                        Text("关闭", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = AppColors.Primary,
                    trackColor = AppColors.SurfaceRaised
                )
            } else if (indeterminateProgress) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = AppColors.Primary,
                    trackColor = AppColors.SurfaceRaised
                )
            }
        }
    }
}

@Composable
fun SourceOperationProgress(
    message: String,
    currentIndex: Int,
    total: Int,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    val progress = when {
        total <= 0 -> null
        else -> currentIndex.toFloat() / total.toFloat()
    }
    SourceManagementStatusBanner(
        modifier = modifier,
        message = if (total > 0) "$message (${currentIndex.coerceAtMost(total)}/$total)" else message,
        progress = progress,
        indeterminateProgress = total <= 0,
        showDismiss = false,
        onDismiss = {}
    )
}

@Composable
fun SourceManagementEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    primaryActionText: String,
    onPrimaryAction: () -> Unit,
    secondaryActionText: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    actionsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            color = AppColors.Surface,
            shape = RectangleShape,
            border = BorderStroke(1.dp, AppColors.Divider)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    color = AppColors.PrimaryLight,
                    contentColor = AppColors.Primary,
                    shape = RectangleShape,
                    border = BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.18f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = title,
                        color = AppColors.TextPrimary,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = message,
                        color = AppColors.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Button(
                        onClick = onPrimaryAction,
                        enabled = actionsEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.Primary,
                            contentColor = AppColors.OnPrimary,
                            disabledContainerColor = AppColors.SurfaceRaised,
                            disabledContentColor = AppColors.TextTertiary
                        )
                    ) {
                        Text(primaryActionText, fontSize = 13.sp, fontWeight = FontWeight.Black)
                    }
                    if (secondaryActionText != null && onSecondaryAction != null) {
                        OutlinedButton(
                            onClick = onSecondaryAction,
                            enabled = actionsEnabled,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RectangleShape,
                            border = BorderStroke(1.dp, AppColors.DividerStrong),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = AppColors.TextSecondary,
                                disabledContentColor = AppColors.TextTertiary
                            )
                        ) {
                            Text(secondaryActionText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SourceCompactEnabledSwitch(
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
        shape = RectangleShape,
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
                shape = RectangleShape
            ) {}
        }
    }
}

@Composable
fun SourcePrimaryActionButton(
    icon: ImageVector,
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

fun parseNamedSourceLines(
    raw: String,
    fallbackPrefix: String
): List<Pair<String, String>> {
    return raw
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapIndexedNotNull { index, line ->
            val normalized = line.replace('，', ',')
            val parts = normalized.split(",", limit = 2)
            val (name, url) = if (parts.size == 2) {
                parts[0].trim() to parts[1].trim()
            } else {
                "$fallbackPrefix${index + 1}" to normalized.trim()
            }
            url.takeIf { it.startsWith("http", ignoreCase = true) }
                ?.let { name.ifBlank { "$fallbackPrefix${index + 1}" } to it }
        }
        .distinctBy { it.second.trim().trimEnd('/') }
        .toList()
}

@Composable
fun SourceManagementIconButton(
    icon: ImageVector,
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

@Composable
fun SourceCheckStatusMeta(
    lastCheckStatus: String,
    lastCheckTime: Long,
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
            text = if (isChecking) buildAnnotatedString { append("检测中") } else sourceCheckMetaText(lastCheckStatus, lastCheckTime),
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
fun SourceManagementMeta(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    loadingText: String = "处理中"
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                color = AppColors.Primary,
                strokeWidth = 1.5.dp
            )
        }
        Text(
            text = if (isLoading) AnnotatedString(loadingText) else text,
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

fun sourceCheckMetaText(lastCheckStatus: String, lastCheckTime: Long) = buildAnnotatedString {
    withStyle(
        SpanStyle(
            color = sourceCheckStatusColor(lastCheckStatus, lastCheckTime),
            fontWeight = FontWeight.Bold
        )
    ) {
        append(sourceCheckStatusLabel(lastCheckStatus, lastCheckTime))
    }
    sourceCheckTimeLabel(lastCheckTime)?.let { checkTime ->
        append(" · ")
        withStyle(SpanStyle(color = AppColors.TextTertiary)) {
            append(checkTime)
        }
    }
}

fun sourceCheckStatusLabel(lastCheckStatus: String, lastCheckTime: Long): String {
    if (lastCheckTime <= 0L) return "未检测"
    return when (lastCheckStatus) {
        "可用", "可播放" -> lastCheckStatus
        else -> "接口异常"
    }
}

fun sourceCheckStatusColor(lastCheckStatus: String, lastCheckTime: Long): Color {
    return when {
        lastCheckTime <= 0L -> AppColors.TextTertiary
        lastCheckStatus == "可用" || lastCheckStatus == "可播放" -> AppColors.Success
        else -> AppColors.Error
    }
}

fun sourceCheckTimeLabel(timestamp: Long): String? {
    return timestamp.takeIf { it > 0L }?.let(::formatSourceCheckTime)
}

fun formatSourceCheckTime(timestamp: Long): String {
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
