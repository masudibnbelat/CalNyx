// FILE: app/src/main/java/com/calnyx/app/ThemePrefs.kt
// NEW FILE. Saves the dark / light mode the user last picked, so
// reopening the app (even days later) shows the same mode instead
// of falling back to dark. Plain SharedPreferences — no database
// needed for one boolean. Used by CalNyxNavGraph: it restores the
// saved mode once at startup and saves every change.
package com.calnyx.app

import android.content.Context

object ThemePrefs {
    private const val PREFS = "calnyx_theme_prefs"
    private const val KEY_DARK = "dark_theme"

    // The saved mode, or `default` when nothing was saved yet
    // (first launch). The NavGraph passes its own darkTheme as
    // the default, so first launch behaves exactly as before.
    fun isDarkTheme(context: Context, default: Boolean = true): Boolean {
        return try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_DARK, default)
        } catch (_: Exception) {
            default
        }
    }

    fun setDarkTheme(context: Context, dark: Boolean) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_DARK, dark).apply()
        } catch (_: Exception) {
        }
    }
}
