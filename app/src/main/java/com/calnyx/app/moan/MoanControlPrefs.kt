// FILE: app/src/main/java/com/calnyx/app/moan/MoanControlPrefs.kt
// NEW FILE - Moan Control settings. SharedPreferences only, offline.
// Ball: on/off, size (dp), opacity (%). No server, no account.
package com.calnyx.app.moan

import android.content.Context

object MoanControlPrefs {

    private const val PREFS = "moan_control_prefs"
    private const val KEY_BALL_ENABLED = "ball_enabled"
    private const val KEY_BALL_SIZE_DP = "ball_size_dp"
    private const val KEY_BALL_OPACITY = "ball_opacity_percent"
    private const val KEY_BALL_X = "ball_x"
    private const val KEY_BALL_Y = "ball_y"
    private const val KEY_ORIENTATION = "orientation_mode"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isBallEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BALL_ENABLED, false)

    fun setBallEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BALL_ENABLED, enabled).apply()
    }

    // Ball size 32..64 dp, default 44 dp (as in the screenshot).
    fun getBallSizeDp(context: Context): Int =
        prefs(context).getInt(KEY_BALL_SIZE_DP, 44).coerceIn(32, 64)

    fun setBallSizeDp(context: Context, dp: Int) {
        prefs(context).edit().putInt(KEY_BALL_SIZE_DP, dp.coerceIn(32, 64)).apply()
    }

    // Opacity 30..100 %, default 70 %.
    fun getBallOpacity(context: Context): Int =
        prefs(context).getInt(KEY_BALL_OPACITY, 70).coerceIn(30, 100)

    fun setBallOpacity(context: Context, percent: Int) {
        prefs(context).edit().putInt(KEY_BALL_OPACITY, percent.coerceIn(30, 100)).apply()
    }

    fun getBallX(context: Context): Int = prefs(context).getInt(KEY_BALL_X, -1)
    fun getBallY(context: Context): Int = prefs(context).getInt(KEY_BALL_Y, -1)

    fun setBallPosition(context: Context, x: Int, y: Int) {
        prefs(context).edit().putInt(KEY_BALL_X, x).putInt(KEY_BALL_Y, y).apply()
    }

    // Screen Orientation mode: "up" (0 deg), "down" (180 deg),
    // "auto". This only mirrors what was last set in the app;
    // the real setting lives in the phone's system settings
    // (ACCELEROMETER_ROTATION / USER_ROTATION), so it survives
    // closing the app and reboot, until changed again.
    fun getOrientationMode(context: Context): String =
        prefs(context).getString(KEY_ORIENTATION, "auto") ?: "auto"

    fun setOrientationMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_ORIENTATION, mode).apply()
    }
}
