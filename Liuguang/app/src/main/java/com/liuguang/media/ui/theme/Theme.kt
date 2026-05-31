package com.liuguang.media.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

private val BrightCinemaColorScheme = lightColorScheme(
    primary = AppColors.Primary,
    onPrimary = AppColors.OnPrimary,
    secondary = AppColors.Accent,
    onSecondary = AppColors.OnPrimary,
    background = AppColors.Background,
    surface = AppColors.Surface,
    surfaceVariant = AppColors.SurfaceAlt,
    onSurface = AppColors.TextPrimary,
    onBackground = AppColors.TextPrimary,
    onSurfaceVariant = AppColors.TextSecondary,
    outline = AppColors.Divider,
    error = AppColors.Error,
    onError = AppColors.OnPrimary
)

private val StableCinemaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(6.dp)
)

private val HeitiFontFamily = FontFamily.SansSerif

private val StableCinemaTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.withHeiti(),
        displayMedium = displayMedium.withHeiti(),
        displaySmall = displaySmall.withHeiti(),
        headlineLarge = headlineLarge.withHeiti(),
        headlineMedium = headlineMedium.withHeiti(),
        headlineSmall = headlineSmall.withHeiti(),
        titleLarge = titleLarge.withHeiti(),
        titleMedium = titleMedium.withHeiti(),
        titleSmall = titleSmall.withHeiti(),
        bodyLarge = bodyLarge.withHeiti(),
        bodyMedium = bodyMedium.withHeiti(),
        bodySmall = bodySmall.withHeiti(),
        labelLarge = labelLarge.withHeiti(),
        labelMedium = labelMedium.withHeiti(),
        labelSmall = labelSmall.withHeiti()
    )
}

private fun TextStyle.withHeiti(): TextStyle = copy(fontFamily = HeitiFontFamily)

@Composable
fun LiuguangTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BrightCinemaColorScheme,
        shapes = StableCinemaShapes,
        typography = StableCinemaTypography,
        content = content
    )
}
