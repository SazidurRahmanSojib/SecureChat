package com.example.securechat.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode(val label: String) {
    SYSTEM("System default"),
    LIGHT("Light"),
    DARK("Dark")
}

/** Small app preferences kept on this phone. Compose screens update when they change. */
object AppSettings {

    private const val PREFS = "securechat_settings"
    private const val KEY_THEME = "theme"
    private const val KEY_NOTIFICATIONS_ASKED = "notifications_asked"

    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val saved = prefs(context).getString(KEY_THEME, ThemeMode.SYSTEM.name)
        themeMode = ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.SYSTEM
    }

    fun setTheme(context: Context, mode: ThemeMode) {
        themeMode = mode
        prefs(context).edit().putString(KEY_THEME, mode.name).apply()
    }

    fun notificationsAsked(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTIFICATIONS_ASKED, false)

    fun markNotificationsAsked(context: Context) {
        prefs(context).edit().putBoolean(KEY_NOTIFICATIONS_ASKED, true).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
