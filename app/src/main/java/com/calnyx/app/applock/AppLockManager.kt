// FILE: app/src/main/java/com/calnyx/app/applock/AppLockManager.kt
// LOCK-ON-EXIT VERSION - replace your old AppLockManager.kt with this file.
// Based on FIXED2 (session lastSeen + decoy flag + restricted-settings
// helpers, all unchanged). Only change: the timer is GONE.
// 1. endUnlockedSession() now ONLY clears the session - no grace
//    window is started, so the app locks the instant you leave it.
// 2. The whole grace machinery is removed: setAllowed / isAllowed /
//    clearAllowed, KEY_ALLOWED_PKG / KEY_ALLOWED_UNTIL, and the
//    unlock-duration setting (KEY_UNLOCK_DURATION_MS,
//    getUnlockDurationMs / setUnlockDurationMs) are deleted.
// 3. Behaviour from now on: unlock a locked app, use it as long as
//    you like (mid-use rule untouched - staying in the same app never
//    locks), and the moment you leave it - Home, Recents, another app,
//    manual CalNyx open, screen off - it is locked again. Next open
//    always shows the decoy calculator.
// 4. The stale-session guard is KEPT: a session unseen for 10 minutes
//    that outlived a service restart / reboot is still a ghost and is
//    cleared by the Service, so an exit the Service never saw can no
//    longer leave an app unlocked forever.
// Apply TOGETHER with AppLockService.kt and AppLockScreen.kt from
// this same folder. NormalCalculatorScreen (UNLOCK / LIFTED) stays
// as-is - it only uses setUnlockedSession / clearUnlockedSession.
package com.calnyx.app.applock

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.text.TextUtils

object AppLockManager {

    private const val PREFS = "app_lock_prefs"
    private const val KEY_ENABLED = "app_lock_enabled"
    private const val KEY_LOCKED = "locked_packages"
    private const val KEY_PENDING = "pending_package"
    private const val KEY_SESSION_PKG = "unlocked_session_package"
    private const val KEY_SESSION_STARTED_AT = "unlocked_session_started_at"
    private const val KEY_SESSION_LAST_SEEN = "unlocked_session_last_seen"
    private const val KEY_CALNYX_INTERVENED = "calnyx_intervened"
    private const val KEY_LAST_FOREGROUND = "last_foreground_package"
    private const val KEY_DECOY_LAUNCH = "calnyx_decoy_launch"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        val edit = prefs(context).edit().putBoolean(KEY_ENABLED, enabled)
        if (!enabled) {
            edit.remove(KEY_SESSION_PKG)
            edit.remove(KEY_SESSION_STARTED_AT)
            edit.remove(KEY_SESSION_LAST_SEEN)
            edit.remove(KEY_CALNYX_INTERVENED)
            edit.remove(KEY_LAST_FOREGROUND)
            edit.remove(KEY_DECOY_LAUNCH)
        }
        edit.apply()
    }

    fun getLastForegroundPkg(context: Context): String? =
        prefs(context).getString(KEY_LAST_FOREGROUND, null)

    fun setLastForegroundPkg(context: Context, packageName: String?) {
        val edit = prefs(context).edit()
        if (packageName == null) edit.remove(KEY_LAST_FOREGROUND)
        else edit.putString(KEY_LAST_FOREGROUND, packageName)
        edit.apply()
    }

    // FIXED2: decoy flag - true only when Service launched CalNyx to block.
    fun setDecoyLaunch(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_DECOY_LAUNCH, value).apply()
    }

    fun isDecoyLaunch(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DECOY_LAUNCH, false)

    fun clearDecoyLaunch(context: Context) {
        prefs(context).edit().remove(KEY_DECOY_LAUNCH).apply()
    }

    fun getLockedPackages(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_LOCKED, emptySet())?.toSet() ?: emptySet()

    fun setLocked(context: Context, packageName: String, locked: Boolean) {
        val set = getLockedPackages(context).toMutableSet()
        if (locked) set.add(packageName) else set.remove(packageName)
        val edit = prefs(context).edit().putStringSet(KEY_LOCKED, set)
        if (!locked && getUnlockedSession(context) == packageName) {
            edit.remove(KEY_SESSION_PKG)
            edit.remove(KEY_SESSION_STARTED_AT)
            edit.remove(KEY_SESSION_LAST_SEEN)
        }
        edit.apply()
    }

    fun isLocked(context: Context, packageName: String): Boolean =
        packageName in getLockedPackages(context)

    fun setPending(context: Context, packageName: String?) {
        val edit = prefs(context).edit()
        if (packageName == null) edit.remove(KEY_PENDING) else edit.putString(KEY_PENDING, packageName)
        edit.apply()
    }

    fun getPending(context: Context): String? =
        prefs(context).getString(KEY_PENDING, null)

    fun clearPending(context: Context) = setPending(context, null)

    // FIXED2: session start time is stored, so a CalNyx resume that
    // happens within 2 seconds of unlock can be ignored as handoff.
    fun setUnlockedSession(context: Context, packageName: String) {
        prefs(context).edit()
            .putString(KEY_SESSION_PKG, packageName)
            .putLong(KEY_SESSION_STARTED_AT, System.currentTimeMillis())
            .putLong(KEY_SESSION_LAST_SEEN, System.currentTimeMillis())
            .remove(KEY_CALNYX_INTERVENED)
            .apply()
    }

    fun getUnlockedSession(context: Context): String? =
        prefs(context).getString(KEY_SESSION_PKG, null)

    fun getSessionStartedAt(context: Context): Long =
        prefs(context).getLong(KEY_SESSION_STARTED_AT, 0L)

    // Last time the Service saw the session app in front. Updated on
    // every session-allow event. The Service uses it to spot a session
    // that outlived the exit it came from (screen off missed, service
    // killed, reboot) - see AppLockService STALE_SESSION_MS.
    fun getSessionLastSeen(context: Context): Long =
        prefs(context).getLong(KEY_SESSION_LAST_SEEN, 0L)

    fun setSessionLastSeen(context: Context, timeMs: Long) {
        prefs(context).edit().putLong(KEY_SESSION_LAST_SEEN, timeMs).apply()
    }

    fun clearUnlockedSession(context: Context) {
        prefs(context).edit()
            .remove(KEY_SESSION_PKG)
            .remove(KEY_SESSION_STARTED_AT)
            .remove(KEY_SESSION_LAST_SEEN)
            .apply()
    }

    fun isCalNyxIntervened(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CALNYX_INTERVENED, false)

    fun setCalNyxIntervened(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_CALNYX_INTERVENED, value).apply()
    }

    fun clearCalNyxIntervened(context: Context) {
        setCalNyxIntervened(context, false)
    }

    // LOCK-ON-EXIT: ending a session only clears it. No grace window
    // is started any more, so there is no "back within X seconds"
    // free pass - the next open of this app always locks.
    fun endUnlockedSession(context: Context) {
        clearUnlockedSession(context)
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        if (Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED, 0
            ) != 1
        ) return false
        val services = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expectedShort = "${context.packageName}/.applock.AppLockService"
        val expectedFull = "${context.packageName}/${context.packageName}.applock.AppLockService"
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(services)
        while (splitter.hasNext()) {
            val service = splitter.next()
            if (service == expectedShort || service == expectedFull) return true
        }
        return false
    }

    // FIXED2: Android 13+ Restricted Settings.
    // True only when the app was NOT installed from Play Store.
    // Play Store installs never need this step.
    fun isSideloaded(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val installer: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    @Suppress("DEPRECATION")
                    pm.getInstallSourceInfo(context.packageName).installingPackageName
                } catch (_: Exception) {
                    @Suppress("DEPRECATION")
                    pm.getInstallerPackageName(context.packageName)
                }
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(context.packageName)
            }
            installer == null || installer != "com.android.vending"
        } catch (_: Exception) {
            true
        }
    }

    fun needsRestrictedSetting(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isSideloaded(context)
}
