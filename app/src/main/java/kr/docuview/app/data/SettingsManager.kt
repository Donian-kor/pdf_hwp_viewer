package kr.docuview.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserSettings(
    val restoreLastPage: Boolean = true,
    val compactMode: Boolean = false,
    val keepScreenOn: Boolean = true,
    val themeMode: String = "SYSTEM", // "SYSTEM", "LIGHT", "DARK"
)

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("docuview_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private fun loadSettings(): UserSettings {
        return UserSettings(
            restoreLastPage = prefs.getBoolean(KEY_RESTORE_LAST_PAGE, true),
            compactMode = prefs.getBoolean(KEY_COMPACT_MODE, false),
            keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true),
            themeMode = prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM",
        )
    }

    fun setRestoreLastPage(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RESTORE_LAST_PAGE, enabled).apply()
        _settings.value = _settings.value.copy(restoreLastPage = enabled)
    }


    fun setCompactMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_COMPACT_MODE, enabled).apply()
        _settings.value = _settings.value.copy(compactMode = enabled)
    }

    fun setKeepScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
        _settings.value = _settings.value.copy(keepScreenOn = enabled)
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    companion object {
        private const val KEY_RESTORE_LAST_PAGE = "restore_last_page"
        private const val KEY_COMPACT_MODE = "compact_mode"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
