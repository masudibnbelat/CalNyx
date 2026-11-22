// FILE: app/src/main/java/com/calnyx/app/hardcore/OrientationGuard.kt
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
// The guard runs while a LOCKED mode (up / down) is saved,
// and now while "normal" is saved too (2026-11-22). In "auto"
// mode it stops watching, so the quick-settings tile and the
// sensor work normally. NORMAL is enforced as auto-rotate OFF
// + USER_ROTATION 0 - the guard watches those two values and
// puts (0, 0) back if the system resets them (this realme
// resets the setting ~2s after leaving the app). A video
// player forcing landscape does NOT touch the system setting,
// so the guard never fights video rotation. The old Normal
// (leave the setting untouched, guard does nothing) was WRONG:
// after Down the setting stayed at 180 deg and nothing ever
// put it back to 0. Note the system values alone cannot tell
// Normal from Up - both are (0, 0) - so the SAVED mode
// decides, and the guard compares raw values, not readMode().
// It runs inside the services that are already alive
// (AppLockService / FloatingBallService) - no new service,
// no new notification, no new permission.
// It uses the application context, so it is not tied to one
// service instance; refresh() is idempotent, calling it twice
// is harmless.
//
// ORDER MATTERS: the mode must be saved to prefs BEFORE the
// system setting is written (MoanControlScreen does this), or
// the watcher would see the new system value, compare it with
// the still-old saved value, and put the old mode back.
package com.calnyx.app.hardcore

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.calnyx.app.moan.MoanControlPrefs

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
        if (mode == "normal") {
            // NORMAL = (auto-rotate OFF, USER_ROTATION 0).
            // Put (0, 0) back if the system reset it, then
            // keep watching - the realme reset happens ~2s
            // after leaving the app, and the ball / Hardcore
            // screen are not around to re-apply it then.
            val (auto, userRot) = readSystemValues(appContext)
            if (auto != 0 || userRot != 0) {
                setMode(appContext, "normal")
            }
            start(appContext)
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
                if (mode == "normal") {
                    // Compare RAW values: readMode() maps
                    // (0, 0) to "up", which would make the
                    // guard write on every single change.
                    // After setMode the values are (0, 0),
                    // so the next onChange does nothing.
                    val (auto, userRot) = readSystemValues(appContext)
                    if (auto != 0 || userRot != 0) {
                        setMode(appContext, "normal")
                    }
                    return
                }
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

    // The raw system values: (ACCELEROMETER_ROTATION,
    // USER_ROTATION). Normal is (0, 0) - the same numbers as
    // "up", so the mode string alone cannot tell them apart;
    // the SAVED mode decides what (0, 0) means.
    private fun readSystemValues(context: Context): Pair<Int, Int> {
        return try {
            val resolver = context.contentResolver
            val auto = Settings.System.getInt(
                resolver, Settings.System.ACCELEROMETER_ROTATION, 1
            )
            val userRot = Settings.System.getInt(
                resolver, Settings.System.USER_ROTATION, 0
            )
            auto to userRot
        } catch (_: Exception) {
            1 to 0
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
                "normal" -> {
                    // NORMAL = the phone's own default: 0 deg,
                    // auto-rotate OFF. Video players can still
                    // force landscape - they do not touch this.
                    Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                    Settings.System.putInt(resolver, Settings.System.USER_ROTATION, 0)
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
