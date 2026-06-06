package com.liuguang.media.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.liuguang.media.ui.theme.AppColors
import com.liuguang.media.ui.theme.Dimens

@Composable
fun ShimmerEffect(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val shimmerColors = listOf(
        AppColors.SurfaceAlt.copy(alpha = 0.54f),
        AppColors.SurfaceRaised.copy(alpha = 0.86f),
        AppColors.SurfaceAlt.copy(alpha = 0.54f)
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim.value - 200f, translateAnim.value - 200f),
        end = Offset(translateAnim.value, translateAnim.value)
    )

    Box(
        modifier = modifier.background(brush)
    )
}

@Composable
fun VodCardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        ShimmerEffect(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
        )
        Spacer(modifier = Modifier.height(Dimens.paddingSmall))
        ShimmerEffect(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(14.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        ShimmerEffect(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(14.dp)
        )
    }
}
