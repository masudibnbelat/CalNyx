// FILE: app/src/main/java/com/calnyx/app/clipboard/ClipboardPrefs.kt
// NEW FILE - Clipboard feature (2026-11-06).
// Target: app/src/main/java/com/calnyx/app/clipboard/ClipboardPrefs.kt
// First package line: package com.calnyx.app.clipboard
// First object line: object ClipboardPrefs
// Auto-delete retention. ONLY these six options exist, exactly as
// the user asked — no "Never" and no custom number:
// 1 day, 2 days, 1 week (7 days), 30 days, 90 days, 1 year (365).
// Default is 30 days. Changing the value prunes immediately
// (the screen calls repository.pruneExpired() after saving).
package com.calnyx.app.clipboard

import android.content.Context

object ClipboardPrefs {

    private const val PREFS_NAME = "calnyx_clipboard_prefs"
    private const val KEY_RETENTION_DAYS = "retention_days"

    const val DEFAULT_RETENTION_DAYS = 30

    // The ONLY retention options. Days -> label shown in the dialog.
    val RETENTION_OPTIONS = listOf(1, 2, 7, 30, 90, 365)

    fun labelFor(days: Int): String {
        return when (days) {
            1 -> "1 day"
            2 -> "2 days"
            7 -> "1 week"
            30 -> "30 days"
            90 -> "90 days"
            365 -> "1 year"
            else -> "$days days"
        }
    }

    fun getRetentionDays(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val days = prefs.getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS)
        // Guard: an old/unknown stored value falls back to the default.
        return if (days in RETENTION_OPTIONS) days else DEFAULT_RETENTION_DAYS
    }

    fun setRetentionDays(context: Context, days: Int) {
        if (days !in RETENTION_OPTIONS) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_RETENTION_DAYS, days)
            .apply()
    }
}
