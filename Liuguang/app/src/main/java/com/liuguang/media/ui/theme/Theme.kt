package com.liuguang.media.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

private fun liuguangColorScheme(
    palette: AppColorPalette,
    darkTheme: Boolean
) = if (darkTheme) {
    darkColorScheme(
        primary = palette.primary,
        onPrimary = palette.onPrimary,
        primaryContainer = palette.primaryLight,
        onPrimaryContainer = palette.textPrimary,
        secondary = palette.accent,
        onSecondary = palette.onPrimary,
        secondaryContainer = palette.accentSoft,
        onSecondaryContainer = palette.textPrimary,
        background = palette.background,
        surface = palette.surface,
        surfaceVariant = palette.surfaceAlt,
        surfaceContainer = palette.surface,
        surfaceContainerHigh = palette.surfaceRaised,
        surfaceContainerHighest = palette.surfaceAlt,
        onSurface = palette.textPrimary,
        onBackground = palette.textPrimary,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.divider,
        outlineVariant = palette.dividerStrong,
        error = palette.error,
        onError = palette.onPrimary,
        inverseSurface = palette.textPrimary,
        inverseOnSurface = palette.surface,
        inversePrimary = palette.primary
    )
} else {
    lightColorScheme(
        primary = palette.primary,
        onPrimary = palette.onPrimary,
        primaryContainer = palette.primaryLight,
        onPrimaryContainer = palette.textPrimary,
        secondary = palette.accent,
        onSecondary = palette.onPrimary,
        secondaryContainer = palette.accentSoft,
        onSecondaryContainer = palette.textPrimary,
        background = palette.background,
        surface = palette.surface,
        surfaceVariant = palette.surfaceAlt,
        surfaceContainer = palette.surface,
        surfaceContainerHigh = palette.surfaceRaised,
        surfaceContainerHighest = palette.surfaceAlt,
        onSurface = palette.textPrimary,
        onBackground = palette.textPrimary,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.divider,
        outlineVariant = palette.dividerStrong,
        error = palette.error,
        onError = palette.onPrimary,
        inverseSurface = palette.textPrimary,
        inverseOnSurface = palette.surface,
        inversePrimary = palette.primary
    )
}

private val StableCinemaShapes = Shapes(
    extraSmall = CutCornerShape(0.dp),
    small = CutCornerShape(0.dp),
    medium = CutCornerShape(0.dp),
    large = CutCornerShape(0.dp),
    extraLarge = CutCornerShape(0.dp)
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
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) LiuguangDarkPalette else LiuguangLightPalette
    AppColors.usePalette(palette)

    MaterialTheme(
        colorScheme = liuguangColorScheme(palette, darkTheme),
        shapes = StableCinemaShapes,
        typography = StableCinemaTypography,
        content = content
    )
}
