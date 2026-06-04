package com.liuguang.media.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liuguang.media.ui.theme.AppColors

private val CinemaShape = RoundedCornerShape(4.dp)

@Composable
fun CinemaBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(AppColors.Background)
    ) {
        content()
    }
}

@Composable
fun CinemaTopBar(
    eyebrow: String,
    title: String,
    actionIcon: ImageVector,
    actionDescription: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = eyebrow.uppercase(),
                color = AppColors.Primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.4.sp
            )
            Text(
                text = title,
                color = AppColors.TextPrimary,
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Black
            )
        }

        CinemaIconButton(
            icon = actionIcon,
            contentDescription = actionDescription,
            onClick = onActionClick
        )
    }
}

@Composable
fun CinemaIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Divider, RoundedCornerShape(4.dp))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = AppColors.TextPrimary,
            modifier = Modifier.size(21.dp)
        )
    }
}

@Composable
fun CinemaSectionHeader(
    title: String,
    meta: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            color = AppColors.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = meta,
            color = AppColors.Primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CinemaSearchPill(
    text: String,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 18.dp,
    onClick: (() -> Unit)? = null
) {
    CinemaSearchSurface(
        modifier = modifier,
        horizontalPadding = horizontalPadding,
        onClick = onClick
    ) {
        Text(
            text = text,
            color = AppColors.TextSecondary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CinemaSearchInput(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 18.dp,
    focusRequester: FocusRequester = remember { FocusRequester() },
    trailingContent: @Composable RowScope.() -> Unit = {}
) {
    CinemaSearchSurface(
        modifier = modifier,
        horizontalPadding = horizontalPadding,
        onClick = { focusRequester.requestFocus() }
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            singleLine = true,
            textStyle = TextStyle(
                color = AppColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = Brush.verticalGradient(
                colors = listOf(AppColors.Primary, AppColors.Primary)
            ),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = AppColors.TextSecondary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            }
        )
        trailingContent()
    }
}

data class MediaFilterOption(
    val key: String?,
    val label: String
)

data class MediaFilterAction(
    val label: String,
    val icon: ImageVector,
    val contentDescription: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Composable
fun MediaFilterHeader(
    searchPlaceholder: String,
    searchValue: String = "",
    onSearchValueChange: ((String) -> Unit)? = null,
    onSearchClick: (() -> Unit)? = null,
    filters: List<MediaFilterOption>,
    selectedFilterKey: String?,
    onFilterSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    leadingAction: MediaFilterAction? = null,
    trailingAction: MediaFilterAction? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.Shell)
            .padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MediaSearchField(
                value = searchValue,
                placeholder = searchPlaceholder,
                onValueChange = onSearchValueChange,
                onClick = onSearchClick,
                modifier = Modifier.weight(1f)
            )
            trailingAction?.let { action ->
                MediaFilterActionButton(action = action, compact = true)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leadingAction?.let { action ->
                MediaFilterActionButton(action = action)
            }

            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = filters,
                    key = { option -> option.key ?: "__all__" },
                    contentType = { "media-filter-chip" }
                ) { option ->
                    MediaFilterChip(
                        label = option.label,
                        selected = option.key == selectedFilterKey,
                        onClick = { onFilterSelected(option.key) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaSearchField(
    value: String,
    placeholder: String,
    onValueChange: ((String) -> Unit)?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val canEdit = onValueChange != null
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.DividerStrong, RoundedCornerShape(8.dp))
            .then(
                when {
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    canEdit -> Modifier.clickable { focusRequester.requestFocus() }
                    else -> Modifier
                }
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = AppColors.TextSecondary,
            modifier = Modifier.size(21.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))

        if (canEdit) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange ?: {},
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = TextStyle(
                    color = AppColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = Brush.verticalGradient(
                    colors = listOf(AppColors.Primary, AppColors.Primary)
                ),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = AppColors.TextSecondary,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
        } else {
            Text(
                text = placeholder,
                modifier = Modifier.weight(1f),
                color = AppColors.TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MediaFilterActionButton(
    action: MediaFilterAction,
    compact: Boolean = false
) {
    Row(
        modifier = Modifier
            .height(if (compact) 52.dp else 40.dp)
            .defaultMinSize(minWidth = if (compact) 52.dp else 80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.PrimaryLight)
            .border(1.dp, AppColors.Primary.copy(alpha = 0.34f), RoundedCornerShape(8.dp))
            .clickable(enabled = action.enabled, onClick = action.onClick)
            .padding(horizontal = if (compact) 0.dp else 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = action.contentDescription,
            tint = if (action.enabled) AppColors.Primary else AppColors.TextTertiary,
            modifier = Modifier.size(if (compact) 22.dp else 18.dp)
        )
        if (!compact) {
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = action.label,
                color = if (action.enabled) AppColors.Primary else AppColors.TextTertiary,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MediaFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = label,
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) AppColors.Primary else AppColors.Surface)
            .then(
                if (selected) {
                    Modifier
                } else {
                    Modifier.border(1.dp, AppColors.Divider, RoundedCornerShape(8.dp))
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        color = if (selected) AppColors.OnPrimary else AppColors.TextPrimary,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Black,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun CinemaSearchSurface(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .padding(horizontal = horizontalPadding)
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Divider, RoundedCornerShape(4.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = AppColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        content()
    }
}

@Composable
fun CinemaGlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(CinemaShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = AppColors.Surface,
        shape = CinemaShape,
        border = BorderStroke(1.dp, AppColors.Divider)
    ) {
        content()
    }
}

@Composable
fun CinemaMiniPlayButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CinemaShape)
            .background(AppColors.Primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "播放",
            tint = AppColors.OnPrimary,
            modifier = Modifier.size(27.dp)
        )
    }
}

@Composable
fun CinemaLoading(
    modifier: Modifier = Modifier,
    message: String = "正在连接片库"
) {
    val transition = rememberInfiniteTransition(label = "cinema-loading")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing)
        ),
        label = "loading-ring-rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 760, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loading-pulse"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 320.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(86.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .rotate(rotation)
                ) {
                    val strokeWidth = 4.dp.toPx()
                    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    drawArc(
                        color = AppColors.Primary.copy(alpha = 0.18f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = stroke
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                AppColors.Primary,
                                AppColors.Primary,
                                Color.Transparent
                            )
                        ),
                        startAngle = -90f,
                        sweepAngle = 250f,
                        useCenter = false,
                        style = stroke
                    )
                }

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .scale(pulse)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    AppColors.Primary.copy(alpha = 0.30f),
                                    AppColors.Primary.copy(alpha = 0.06f),
                                    Color.Transparent
                                )
                            )
                        )
                        .border(1.dp, AppColors.Primary.copy(alpha = 0.36f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(AppColors.Primary)
                    )
                }
            }

            Text(
                text = message,
                color = AppColors.TextPrimary,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Black
            )
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) { index ->
                    val alpha = when (index) {
                        0 -> 0.38f
                        1 -> 0.72f
                        else -> 0.38f
                    }
                    Box(
                        modifier = Modifier
                            .size(if (index == 1) 5.dp else 4.dp)
                            .clip(CircleShape)
                            .background(AppColors.Primary.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

@Composable
fun CinemaMessage(
    title: String,
    message: String?,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    CinemaGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 34.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                color = AppColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
            if (actionText != null && onAction != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    onClick = onAction,
                    color = AppColors.Primary,
                    contentColor = AppColors.OnPrimary,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = actionText,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}
