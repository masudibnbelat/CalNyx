// FILE: app/src/main/java/com/calnyx/app/applock/AppLockManager.kt
// FIXED2 VERSION - replace your old AppLockManager.kt with this file.
// Apply TOGETHER with AppLockService-FIXED2.kt and NavGraph-FIXED2.kt.
// NormalCalculatorScreen (UNLOCK / LIFTED) that sets the session stays as-is.
//
// Changes vs MIDUSEFIX (only these lines changed, everything else same):
// 1. setUnlockedSession() now also stores session start time and clears
//    the old intervened flag + grace. So a resume that happens during
//    the unlock handoff can never kill a just-created session.
// 2. NEW: decoy flag. Service sets it before launching CalNyx as decoy.
//    NavGraph / Service can tell "CalNyx was launched to block" apart
//    from "user manually opened CalNyx". Only the manual open is an exit.
// 3. NEW: needsRestrictedSetting() + isSideloaded(). Android 13+ blocks
//    Accessibility for sideloaded apps until the user does
//    App info > 3 dots > Allow restricted settings. This helper lets
//    AppLockScreen show that exact help only when it applies.
// 4. endUnlockedSession() also clears session start time.
// Old intervened helpers are kept (for migration) but FIXED2 Service
// no longer depends on them - session allow is unconditional now.
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
    private const val KEY_ALLOWED_PKG = "allowed_package"
    private const val KEY_ALLOWED_UNTIL = "allowed_until"
    private const val KEY_UNLOCK_DURATION_MS = "unlock_duration_ms"
    private const val KEY_SESSION_PKG = "unlocked_session_package"
    private const val KEY_SESSION_STARTED_AT = "unlocked_session_started_at"
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
            edit.remove(KEY_CALNYX_INTERVENED)
            edit.remove(KEY_ALLOWED_PKG)
            edit.remove(KEY_ALLOWED_UNTIL)
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
            .remove(KEY_CALNYX_INTERVENED)
            .remove(KEY_ALLOWED_PKG)
            .remove(KEY_ALLOWED_UNTIL)
            .apply()
    }

    fun getUnlockedSession(context: Context): String? =
        prefs(context).getString(KEY_SESSION_PKG, null)

    fun getSessionStartedAt(context: Context): Long =
        prefs(context).getLong(KEY_SESSION_STARTED_AT, 0L)

    fun clearUnlockedSession(context: Context) {
        prefs(context).edit()
            .remove(KEY_SESSION_PKG)
            .remove(KEY_SESSION_STARTED_AT)
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

    fun endUnlockedSession(context: Context) {
        val session = getUnlockedSession(context) ?: return
        clearUnlockedSession(context)
        setAllowed(context, session, getUnlockDurationMs(context))
    }

    fun setAllowed(context: Context, packageName: String, durationMs: Long = 30_000L) {
        prefs(context).edit()
            .putString(KEY_ALLOWED_PKG, packageName)
            .putLong(KEY_ALLOWED_UNTIL, System.currentTimeMillis() + durationMs)
            .apply()
    }

    fun isAllowed(context: Context, packageName: String): Boolean {
        val p = prefs(context)
        val allowedPkg = p.getString(KEY_ALLOWED_PKG, null)
        val until = p.getLong(KEY_ALLOWED_UNTIL, 0L)
        if (allowedPkg != packageName) return false
        if (System.currentTimeMillis() >= until) return false
        return true
    }

    fun getUnlockDurationMs(context: Context): Long =
        prefs(context).getLong(KEY_UNLOCK_DURATION_MS, 30_000L)

    fun setUnlockDurationMs(context: Context, durationMs: Long) {
        prefs(context).edit().putLong(KEY_UNLOCK_DURATION_MS, durationMs).apply()
    }

    fun clearAllowed(context: Context) {
        prefs(context).edit()
            .remove(KEY_ALLOWED_PKG)
            .remove(KEY_ALLOWED_UNTIL)
            .apply()
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
