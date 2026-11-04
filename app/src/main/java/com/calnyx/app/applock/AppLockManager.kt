// FILE: app/src/main/java/com/calnyx/app/applock/AppLockManager.kt
// MIDUSEFIX VERSION - based on UNLOCKFIX, plus one thing:
// last foreground package is now PERSISTED, not only in memory,
// so a service restart cannot forget which app was in front.
// VaultPrefs এর মতোই SharedPreferences, কোনো সার্ভার নেই, অফলাইন।
package com.calnyx.app.applock

import android.content.Context
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
    private const val KEY_CALNYX_INTERVENED = "calnyx_intervened"
    private const val KEY_LAST_FOREGROUND = "last_foreground_package"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        val edit = prefs(context).edit().putBoolean(KEY_ENABLED, enabled)
        if (!enabled) {
            // Lock off korle purono session / grace-o muche jabe,
            // pore on korle abar secret lagbe.
            edit.remove(KEY_SESSION_PKG)
            edit.remove(KEY_CALNYX_INTERVENED)
            edit.remove(KEY_ALLOWED_PKG)
            edit.remove(KEY_ALLOWED_UNTIL)
            edit.remove(KEY_LAST_FOREGROUND)
        }
        edit.apply()
    }

    // Last launchable app in front, PERSISTED. The service used
    // to keep this only in memory, so a restart forgot it and
    // treated the next screen inside the same app as a fresh
    // open. Now it survives a restart.
    fun getLastForegroundPkg(context: Context): String? =
        prefs(context).getString(KEY_LAST_FOREGROUND, null)

    fun setLastForegroundPkg(context: Context, packageName: String?) {
        val edit = prefs(context).edit()
        if (packageName == null) edit.remove(KEY_LAST_FOREGROUND)
        else edit.putString(KEY_LAST_FOREGROUND, packageName)
        edit.apply()
    }

    fun getLockedPackages(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_LOCKED, emptySet())?.toSet() ?: emptySet()

    fun setLocked(context: Context, packageName: String, locked: Boolean) {
        val set = getLockedPackages(context).toMutableSet()
        if (locked) set.add(packageName) else set.remove(packageName)
        val edit = prefs(context).edit().putStringSet(KEY_LOCKED, set)
        if (!locked && getUnlockedSession(context) == packageName) {
            edit.remove(KEY_SESSION_PKG)
        }
        edit.apply()
    }

    fun isLocked(context: Context, packageName: String): Boolean =
        packageName in getLockedPackages(context)

    // Service লক করা অ্যাপ ধরলে এটা বসায়, যাতে ক্যালকুলেটরে
    // সিক্রেট দিলে কোন অ্যাপটা খুলতে হবে সেটা বোঝা যায়।
    fun setPending(context: Context, packageName: String?) {
        val edit = prefs(context).edit()
        if (packageName == null) edit.remove(KEY_PENDING) else edit.putString(KEY_PENDING, packageName)
        edit.apply()
    }

    fun getPending(context: Context): String? =
        prefs(context).getString(KEY_PENDING, null)

    fun clearPending(context: Context) = setPending(context, null)

    // SESSION — ei-tai asol fix: secret diye unlock korle ei
    // package-ta session-e thakbe, ar jotokhon user ei app-er
    // moddhei thakbe totokhon kokhono lock hobe na, somoy
    // jotoi jak na keno. App theke ber holei session sesh hobe.
    fun setUnlockedSession(context: Context, packageName: String) {
        prefs(context).edit().putString(KEY_SESSION_PKG, packageName).apply()
    }

    fun getUnlockedSession(context: Context): String? =
        prefs(context).getString(KEY_SESSION_PKG, null)

    fun clearUnlockedSession(context: Context) {
        prefs(context).edit().remove(KEY_SESSION_PKG).apply()
    }

    // CalNyx nije resume hole (user locked app theke CalNyx-e
    // fire ele) service CalNyx-er event ignore kore, tai ei flag
    // diye service-ke janano hoy je majhkhane CalNyx esechilo.
    // Service porer locked-app event-e ei flag dekhe abar
    // switch hisebe check korbe, continuation hisebe na.
    fun isCalNyxIntervened(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CALNYX_INTERVENED, false)

    fun setCalNyxIntervened(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_CALNYX_INTERVENED, value).apply()
    }

    fun clearCalNyxIntervened(context: Context) {
        setCalNyxIntervened(context, false)
    }

    // Session sesh hole (app theke ber hole) grace timer suru hoy:
    // ei somoyer moddhe fire asle secret lagbe na, pore asle lagbe.
    // Service-ei eta call kore, jokhon user onno app-e chole jay.
    fun endUnlockedSession(context: Context) {
        val session = getUnlockedSession(context) ?: return
        clearUnlockedSession(context)
        setAllowed(context, session, getUnlockDurationMs(context))
    }

    // Grace window — app theke ber howar POR kotokhon fire asle
    // secret lagbe na. Eta unlock-er somoy theke gona hoy na,
    // tai app-er moddhe thakle eta kokhono lock korabe na.
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

    // Ber howar por kotokhon grace thakbe — App Lock settings
    // theke bodlano jay. Default 30 second: privacy-first.
    // App-er moddhe thakle ei timer chole na, tai majhkhane
    // kokhono lock hobe na; ber howar por ei somoy perolei
    // porer bar abar secret lagbe.
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

    // Settings-এ Accessibility সার্ভিসটা অন আছে কিনা।
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
}
