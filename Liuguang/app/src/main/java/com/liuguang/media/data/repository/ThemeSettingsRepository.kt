package com.liuguang.media.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val storageValue: String, val displayName: String) {
    Light("light", "亮色"),
    Dark("dark", "深色"),
    System("system", "跟随系统");

    companion object {
        fun fromStorageValue(value: String?): ThemeMode {
            return values().firstOrNull { it.storageValue == value } ?: System
        }
    }
}

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.System
)

@Singleton
class ThemeSettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("theme_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<ThemeSettings> = _settings.asStateFlow()

    fun updateThemeMode(mode: ThemeMode) {
        prefs.edit()
            .putString(KEY_THEME_MODE, mode.storageValue)
            .apply()
        _settings.value = ThemeSettings(mode = mode)
    }

    private fun readSettings(): ThemeSettings {
        return ThemeSettings(
            mode = ThemeMode.fromStorageValue(prefs.getString(KEY_THEME_MODE, ThemeMode.System.storageValue))
        )
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
    }
}
