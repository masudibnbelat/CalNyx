// FILE: app/src/main/java/com/calnyx/app/onboarding/OnboardingPrefs.kt
// NEW FILE - first-run onboarding flag. The introduction shows once,
// on the very first open after install, AND once after every app
// update (first open of the new version). Finishing it (Next through
// the last step, or Skip) saves done=true for THAT version, so later
// opens go straight to the Normal calculator. Clearing the app's data
// shows it again.
package com.calnyx.app.onboarding

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object OnboardingPrefs {

    private const val PREFS = "onboarding_prefs"
    private const val KEY_DONE = "onboarding_done"
    private const val KEY_VERSION = "onboarding_version"

    // The version the user finished the introduction for. Compared
    // with the currently installed version: after an update the two
    // differ, so the introduction shows once for the new version.
    // (PackageManager pattern copied from NavGraph's appVersion -
    // BuildConfig is not enabled in build.gradle.kts.)
    private fun currentVersion(context: Context): String {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            info.versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun isDone(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.getBoolean(KEY_DONE, false) &&
                prefs.getString(KEY_VERSION, null) == currentVersion(context)
        } catch (_: Exception) {
            false
        }
    }

    fun setDone(context: Context) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DONE, true)
                .putString(KEY_VERSION, currentVersion(context))
                .apply()
        } catch (_: Exception) {
        }
    }
}
