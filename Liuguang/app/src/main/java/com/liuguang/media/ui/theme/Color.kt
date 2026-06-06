package com.liuguang.media.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

data class AppColorPalette(
    val background: Color,
    val shell: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val surfaceRaised: Color,
    val surfaceSoft: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryLight: Color,
    val accent: Color,
    val accentSoft: Color,
    val success: Color,
    val successLight: Color,
    val warning: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val dividerStrong: Color,
    val error: Color,
    val onMedia: Color,
    val mediaScrim: Color,
    val mediaScrimSoft: Color,
    val transparent: Color
) {
    val cream: Color = textPrimary
}

val LiuguangLightPalette = AppColorPalette(
    background = Color(0xFFF5FAFD),
    shell = Color(0xFFECF6FB),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFE6F2F8),
    surfaceRaised = Color(0xFFFFFFFF),
    surfaceSoft = Color(0xFFF0F7FB),
    primary = Color(0xFF1598D4),
    onPrimary = Color(0xFFFFFFFF),
    primaryLight = Color(0x211598D4),
    accent = Color(0xFF20B8C8),
    accentSoft = Color(0x1F20B8C8),
    success = Color(0xFF16885E),
    successLight = Color(0x1F16885E),
    warning = Color(0xFFB7791F),
    textPrimary = Color(0xFF102D3D),
    textSecondary = Color(0xFF526D7C),
    textTertiary = Color(0xFF7C94A1),
    divider = Color(0x1F0D415B),
    dividerStrong = Color(0x330D415B),
    error = Color(0xFFD23B55),
    onMedia = Color(0xFFFFFFFF),
    mediaScrim = Color(0xCC020711),
    mediaScrimSoft = Color(0x99020711),
    transparent = Color(0x00000000)
)

val LiuguangDarkPalette = AppColorPalette(
    background = Color(0xFF050506),
    shell = Color(0xFF090A0C),
    surface = Color(0xFF111214),
    surfaceAlt = Color(0xFF1A1C20),
    surfaceRaised = Color(0xFF22252B),
    surfaceSoft = Color(0xFF15171A),
    primary = Color(0xFFD7DEE8),
    onPrimary = Color(0xFF0A0B0D),
    primaryLight = Color(0x2ED7DEE8),
    accent = Color(0xFFAEB8C5),
    accentSoft = Color(0x24AEB8C5),
    success = Color(0xFF8CCF9E),
    successLight = Color(0x268CCF9E),
    warning = Color(0xFFD8B76A),
    textPrimary = Color(0xFFF3F5F7),
    textSecondary = Color(0xFFB3B8C0),
    textTertiary = Color(0xFF7B818B),
    divider = Color(0x26FFFFFF),
    dividerStrong = Color(0x3BFFFFFF),
    error = Color(0xFFFF7676),
    onMedia = Color(0xFFFFFFFF),
    mediaScrim = Color(0xCC020711),
    mediaScrimSoft = Color(0x99020711),
    transparent = Color(0x00000000)
)

object AppColors {
    private var currentPalette by mutableStateOf(LiuguangLightPalette)

    val palette: AppColorPalette
        get() = currentPalette

    fun usePalette(next: AppColorPalette) {
        currentPalette = next
    }

    val Background get() = palette.background
    val Shell get() = palette.shell
    val Surface get() = palette.surface
    val SurfaceAlt get() = palette.surfaceAlt
    val SurfaceRaised get() = palette.surfaceRaised
    val SurfaceSoft get() = palette.surfaceSoft
    val Primary get() = palette.primary
    val OnPrimary get() = palette.onPrimary
    val PrimaryLight get() = palette.primaryLight
    val Accent get() = palette.accent
    val AccentSoft get() = palette.accentSoft
    val Success get() = palette.success
    val SuccessLight get() = palette.successLight
    val Warning get() = palette.warning
    val TextPrimary get() = palette.textPrimary
    val TextSecondary get() = palette.textSecondary
    val TextTertiary get() = palette.textTertiary
    val Divider get() = palette.divider
    val DividerStrong get() = palette.dividerStrong
    val Error get() = palette.error
    val OnMedia get() = palette.onMedia
    val MediaScrim get() = palette.mediaScrim
    val MediaScrimSoft get() = palette.mediaScrimSoft
    val Transparent get() = palette.transparent
    val Cream get() = palette.cream
}
