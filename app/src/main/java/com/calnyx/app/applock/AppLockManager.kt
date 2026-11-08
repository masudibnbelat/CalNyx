// FILE: app/src/main/java/com/calnyx/app/applock/AppLockManager.kt
// LOCK-ON-EXIT VERSION + STALE-PENDING TIMESTAMP + DECOY-ACTIVE
// FLAG (2026-11-08, FIX4) - replace your old AppLockManager.kt with
// this file. Only change vs the timestamp version: a SECOND decoy
// flag (KEY_DECOY_ACTIVE) and the isLiveDecoy() helper. WHY: the
// debug Toast proved the old flag is false at "=" time while pending
// is intact - NavGraph clears the OLD flag (KEY_DECOY_LAUNCH) on
// EVERY ON_RESUME, because it uses it as a one-shot signal to pop
// to Normal once per decoy launch. So the unlock decision must never
// read the old flag. The new flag is set/cleared only by the Service
// and by NormalCalculatorScreen - NavGraph never touches it - and
// isLiveDecoy() also requires the block to be less than 10 min old.
// The old flag is left exactly as it was, so NavGraph's one-shot
// pop-to-Normal keeps working.
// replace your old AppLockManager.kt with this file. Only change
// vs LOCK-ON-EXIT: setPending() now also stores the time the block
// was set (KEY_PENDING_AT), and getPendingSetAt() reads it, so the
// Service can clear a block abandoned long ago. Everything else
// below is unchanged.
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
    private const val KEY_PENDING_AT = "pending_package_set_at"
    private const val KEY_SESSION_PKG = "unlocked_session_package"
    private const val KEY_SESSION_STARTED_AT = "unlocked_session_started_at"
    private const val KEY_SESSION_LAST_SEEN = "unlocked_session_last_seen"
    private const val KEY_CALNYX_INTERVENED = "calnyx_intervened"
    private const val KEY_LAST_FOREGROUND = "last_foreground_package"
    private const val KEY_DECOY_LAUNCH = "calnyx_decoy_launch"
    private const val KEY_DECOY_ACTIVE = "decoy_active"

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
            edit.remove(KEY_DECOY_ACTIVE)
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

    // FIX4: the LIVE-decoy flag. Set by the Service at BLOCK, cleared
    // only by the Service (abandon / screen off / stale / manual
    // open) and by NormalCalculatorScreen (Back on decoy, successful
    // unlock, stale cleanup). NavGraph NEVER touches this flag - it
    // only ever reads/clears the OLD decoy-launch flag, which it
    // consumes on every ON_RESUME to pop to Normal once per decoy
    // launch. That consumption is exactly why the old flag read
    // false at "=" time in the debug Toast while pending was intact.
    fun setDecoyActive(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_DECOY_ACTIVE, value).apply()
    }

    fun isDecoyActive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DECOY_ACTIVE, false)

    fun clearDecoyActive(context: Context) {
        prefs(context).edit().remove(KEY_DECOY_ACTIVE).apply()
    }

    // FIX4: the ONLY check the secret-code handler and the decoy
    // Back handler should use. A block is live when a pending app
    // exists, the decoy was launched for it and never abandoned,
    // and the block is less than 10 minutes old (same stale limit
    // as sessions - a block abandoned long ago never unlocks).
    // Deliberately does NOT read isDecoyLaunch(): NavGraph clears
    // that flag on every resume, so it is always false by the time
    // the user types the secret.
    fun isLiveDecoy(context: Context): Boolean {
        val pending = getPending(context) ?: return false
        if (!isDecoyActive(context)) return false
        val setAt = getPendingSetAt(context)
        if (setAt > 0L && System.currentTimeMillis() - setAt > 10L * 60L * 1000L) return false
        return true
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

    // STALE-PENDING FIX (2026-11-08): a pending block now carries
    // the time it was set, so the Service can tell a live decoy
    // from a block abandoned long ago (service restart / reboot).
    // Only this timestamp is new; all other behaviour is unchanged.
    fun setPending(context: Context, packageName: String?) {
        val edit = prefs(context).edit()
        if (packageName == null) {
            edit.remove(KEY_PENDING)
            edit.remove(KEY_PENDING_AT)
        } else {
            edit.putString(KEY_PENDING, packageName)
            edit.putLong(KEY_PENDING_AT, System.currentTimeMillis())
        }
        edit.apply()
    }

    fun getPending(context: Context): String? =
        prefs(context).getString(KEY_PENDING, null)

    fun getPendingSetAt(context: Context): Long =
        prefs(context).getLong(KEY_PENDING_AT, 0L)

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
