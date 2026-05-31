package com.liuguang.media.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
