// FILE: app/src/main/java/com/calnyx/app/onboarding/OnboardingPrefs.kt
// NEW FILE - first-run onboarding flag. The introduction shows once,
// on the very first open after install. Finishing it (Next through
// the last step, or Skip) saves done=true, so later opens go straight
// to the Normal calculator. Clearing the app's data shows it again.
package com.calnyx.app.onboarding

import android.content.Context

object OnboardingPrefs {

    private const val PREFS = "onboarding_prefs"
    private const val KEY_DONE = "onboarding_done"

    fun isDone(context: Context): Boolean {
        return try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_DONE, false)
        } catch (_: Exception) {
            false
        }
    }

    fun setDone(context: Context) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_DONE, true).apply()
        } catch (_: Exception) {
        }
    }
}
