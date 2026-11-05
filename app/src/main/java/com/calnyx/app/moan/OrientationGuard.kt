// FILE: app/src/main/java/com/calnyx/app/moan/OrientationGuard.kt
// NEW FILE - keeps the orientation mode chosen in CalNyx
// (Hardcore screen) applied system-wide, even after the user
// leaves the app.
//
// WHY IT IS NEEDED: on this realme phone the system setting
// (ACCELEROMETER_ROTATION / USER_ROTATION) is reset about 2
// seconds after leaving CalNyx, so 180 deg falls back to 0 deg
// (seen on video: Home screen upside down, then it rotates
// back on its own). The mode IS saved (MoanControlPrefs), but
// saving alone does not re-apply it - this guard watches the
// system setting and puts the saved mode back whenever
// anything changes it.
//
// The guard only runs while a LOCKED mode (up / down) is saved.
// In "auto" mode it stops watching, so the quick-settings tile
// and the sensor work normally. It runs inside the services
// that are already alive (AppLockService / FloatingBallService)
// - no new service, no new notification, no new permission.
// It uses the application context, so it is not tied to one
// service instance; refresh() is idempotent, calling it twice
// is harmless.
//
// ORDER MATTERS: the mode must be saved to prefs BEFORE the
// system setting is written (MoanControlScreen does this), or
// the watcher would see the new system value, compare it with
// the still-old saved value, and put the old mode back.
package com.calnyx.app.moan

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings

object OrientationGuard {

    private var observer: ContentObserver? = null

    private fun savedMode(context: Context): String =
        MoanControlPrefs.getOrientationMode(context)

    // Re-apply the saved mode now, and (un)register the watcher.
    // Call this whenever the mode changes and whenever a host
    // service starts. Safe to call many times.
    fun refresh(context: Context) {
        val appContext = context.applicationContext
        val mode = savedMode(appContext)
        if (mode == "auto") {
            stop(appContext)
            return
        }
        if (readMode(appContext) != mode) {
            setMode(appContext, mode)
        }
        start(appContext)
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        observer?.let {
            try {
                appContext.contentResolver.unregisterContentObserver(it)
            } catch (_: Exception) {
            }
            observer = null
        }
    }

    private fun start(context: Context) {
        if (observer != null) return
        val appContext = context.applicationContext
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val mode = savedMode(appContext)
                if (mode == "auto") return
                // Something (system / another app / quick tile)
                // changed the rotation - put the saved mode back.
                // No loop: after setMode the value already equals
                // the saved mode, so the next onChange does nothing.
                if (readMode(appContext) != mode) {
                    setMode(appContext, mode)
                }
            }
        }
        try {
            val resolver = appContext.contentResolver
            resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
                false, obs
            )
            resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.USER_ROTATION),
                false, obs
            )
            // Broad net as well: any change to the system
            // settings table fires the check, so a reset done
            // through any URI is still caught. The check itself
            // is two cheap reads and only writes on mismatch.
            resolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true, obs
            )
            observer = obs
        } catch (_: Exception) {
            observer = null
        }
    }

    // Read the current system mode. Same logic as
    // MoanControlScreen.readOrientationMode.
    private fun readMode(context: Context): String {
        return try {
            val resolver = context.contentResolver
            val auto = Settings.System.getInt(
                resolver, Settings.System.ACCELEROMETER_ROTATION, 1
            )
            if (auto == 1) "auto"
            else when (Settings.System.getInt(
                resolver, Settings.System.USER_ROTATION, 0
            )) {
                2 -> "down"
                else -> "up"
            }
        } catch (_: Exception) {
            savedMode(context)
        }
    }

    // Write the system setting. Same as MoanControlScreen.
    private fun setMode(context: Context, mode: String): Boolean {
        if (!Settings.System.canWrite(context)) return false
        return try {
            val resolver = context.contentResolver
            when (mode) {
                "up" -> {
                    Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                    Settings.System.putInt(resolver, Settings.System.USER_ROTATION, 0)
                }
                "down" -> {
                    Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                    Settings.System.putInt(resolver, Settings.System.USER_ROTATION, 2)
                }
                else -> {
                    Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
