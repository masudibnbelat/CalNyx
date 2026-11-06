// FILE: app/src/main/java/com/calnyx/app/applock/AppLockService.kt
// FIXED2 + PANEL VERSION - replace your old AppLockService.kt with this file.
// WHY THE BUILD FAILED: BallPanelActivity.kt and MoanControlScreen.kt
// call AppLockService.requestOfficialScreenshot(), requestLockScreen()
// and requestPowerDialog(), but the AppLockService.kt in the project
// is the plain FIXED2 version, which does NOT have those three
// companion methods — hence "Unresolved reference" x5. This file is
// FIXED2 session logic UNCHANGED, plus a companion object holding
// the service instance and those three request methods:
//   requestPowerDialog() -> GLOBAL_ACTION_POWER_DIALOG
//   requestLockScreen()  -> GLOBAL_ACTION_LOCK_SCREEN
//   requestOfficialScreenshot() -> GLOBAL_ACTION_TAKE_SCREENSHOT (API 28+)
// Each returns false when Accessibility is off / service not running,
// and the callers already show "Turn on Accessibility" then.
// Also: onServiceConnected sets instance + OrientationGuard.refresh,
// onDestroy clears instance. Nothing else changed vs FIXED2.
// Debug: adb logcat -s CalNyxAppLock  (filter by pid for clean log)
package com.calnyx.app.applock

import com.calnyx.app.moan.OrientationGuard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AppLockService : AccessibilityService() {

    companion object {
        private var instance: AppLockService? = null

        // Moan Control calls these. False = Accessibility not on /
        // service not running. True = action was sent.
        fun requestPowerDialog(): Boolean {
            return try {
                instance?.performGlobalAction(
                    AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
                ) == true
            } catch (_: Exception) {
                false
            }
        }

        fun requestLockScreen(): Boolean {
            return try {
                instance?.performGlobalAction(
                    AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
                ) == true
            } catch (_: Exception) {
                false
            }
        }

        // Ball panel calls this. Uses the phone's OFFICIAL
        // screenshot (Android 9+). False = Accessibility off
        // or Android 8 and below.
        fun requestOfficialScreenshot(): Boolean {
            return try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
                instance?.performGlobalAction(
                    AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
                ) == true
            } catch (_: Exception) {
                false
            }
        }
    }

    private var lastForegroundPkg: String? = null

    // Packages that are never a real app switch, even though
    // getLaunchIntentForPackage() returns non-null for some of them.
    private val systemIgnore = setOf(
        "android",
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.intentresolver",
        "com.google.android.intentresolver",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.documentsui",
        "com.google.android.documentsui",
        "com.android.providers.media",
        "com.android.providers.downloads",
        "com.google.android.apps.docs"
    )

    // Known keyboards - fallback only. The current IME is read
    // from Settings below, so any keyboard the user uses is covered.
    private val knownKeyboards = setOf(
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.touchtype.swiftkey",
        "com.swiftkey.swiftkey",
        "com.realme.inputmethod",
        "com.coloros.inputmethod",
        "com.oppo.inputmethod",
        "com.xiaomi.inputmethod",
        "com.sohu.inputmethod.sogou",
        "com.baidu.input"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // Orientation guard: re-apply the saved rotation mode
        // (up / down) if the system reset it, and keep watching
        // while a locked mode is saved. No-op in Auto mode.
        try {
            OrientationGuard.refresh(this)
        } catch (_: Exception) {
        }
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = 0
            notificationTimeout = 100
        }
        serviceInfo = info
        lastForegroundPkg = try {
            AppLockManager.getLastForegroundPkg(this)
        } catch (_: Exception) {
            null
        }
        // FIXED2: a stale intervened flag from the old version must
        // never block a live session after a restart / update.
        try {
            AppLockManager.clearCalNyxIntervened(this)
            AppLockManager.clearDecoyLaunch(this)
        } catch (_: Exception) {
        }
        Log.d(
            "CalNyxAppLock",
            "service connected, restored last=$lastForegroundPkg session=" + try {
                AppLockManager.getUnlockedSession(this)
            } catch (_: Exception) {
                null
            }
        )
    }

    private fun setLastForeground(packageName: String?) {
        lastForegroundPkg = packageName
        try {
            AppLockManager.setLastForegroundPkg(this, packageName)
        } catch (_: Exception) {
        }
    }

    private fun hasLauncher(packageName: String): Boolean {
        return try {
            packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (_: Exception) {
            false
        }
    }

    private fun currentImePkg(): String? {
        return try {
            val ime = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            ) ?: return null
            ime.substringBefore("/").takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun isTransient(pkg: String, event: AccessibilityEvent): Boolean {
        if (pkg in systemIgnore) return true
        if (pkg in knownKeyboards) return true
        val ime = currentImePkg()
        if (ime != null && pkg == ime) return true
        val cls = event.className?.toString() ?: return false
        // Dialog / popup / keyboard window - not an app switch.
        if (cls.contains("Dialog", ignoreCase = true)) return true
        if (cls.contains("Popup", ignoreCase = true)) return true
        if (cls.contains("Toast", ignoreCase = true)) return true
        if (cls.contains("InputMethod", ignoreCase = true)) return true
        if (cls.contains("Keyboard", ignoreCase = true)) return true
        if (cls.contains("ChooserActivity", ignoreCase = true)) return true
        if (cls.contains("Permission", ignoreCase = true)) return true
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        if (!AppLockManager.isEnabled(this)) {
            if (pkg != packageName) setLastForeground(null)
            return
        }

        val session = try {
            AppLockManager.getUnlockedSession(this)
        } catch (_: Exception) {
            null
        }
        val pending = try {
            AppLockManager.getPending(this)
        } catch (_: Exception) {
            null
        }

        // FIXED2: CalNyx foreground is handled here, not in NavGraph.
        // Decoy launch (Service just blocked something, pending is set)
        // is NOT an exit. Manual open (no pending, session exists) IS.
        if (pkg == packageName) {
            if (pending != null) {
                Log.d("CalNyxAppLock", "decoy CalNyx ignored pending=$pending")
                setLastForeground(pkg)
                return
            }
            if (session != null) {
                Log.d("CalNyxAppLock", "manual CalNyx open, ending session $session")
                try {
                    AppLockManager.endUnlockedSession(this)
                } catch (_: Exception) {
                }
            }
            setLastForeground(pkg)
            return
        }

        // FIXED2: transient overlay - keyboard, permission dialog,
        // share sheet, picker, GMS dialog. Never change lastForeground,
        // never end a session, never block. This was hole #1.
        if (isTransient(pkg, event)) {
            Log.d("CalNyxAppLock", "transient ignored pkg=$pkg cls=${event.className} last=$lastForegroundPkg session=$session")
            return
        }

        // Not a launchable app (SystemUI etc. without launcher) - ignore.
        if (!hasLauncher(pkg)) {
            Log.d("CalNyxAppLock", "overlay ignored pkg=$pkg last=$lastForegroundPkg")
            return
        }

        // FIXED2: session allow is UNCONDITIONAL. If this IS the session
        // app, it is still the same use, no matter how long, no matter
        // if the service restarted, no matter what lastForeground says.
        // NEVER block here. This is the whole mid-use rule.
        if (session != null && pkg == session) {
            Log.d("CalNyxAppLock", "session allow pkg=$pkg")
            setLastForeground(pkg)
            return
        }

        // Continuation of an UNLOCKED app: harmless, allow.
        if (pkg == lastForegroundPkg && !AppLockManager.isLocked(this, pkg)) {
            Log.d("CalNyxAppLock", "continuation allow unlocked pkg=$pkg")
            return
        }

        // Real app switch. This is the ONLY place a session ends here.
        val previous = lastForegroundPkg
        setLastForeground(pkg)

        if (previous != null && previous == session && previous != pkg) {
            Log.d("CalNyxAppLock", "switch away, ending session pkg=$previous to=$pkg")
            try {
                AppLockManager.endUnlockedSession(this)
            } catch (_: Exception) {
            }
        }

        if (!AppLockManager.isLocked(this, pkg)) {
            Log.d("CalNyxAppLock", "switch to unlocked app pkg=$pkg")
            return
        }

        // Grace return: within auto-lock time after a real exit.
        if (AppLockManager.isAllowed(this, pkg)) {
            Log.d("CalNyxAppLock", "grace allow, restarting session pkg=$pkg")
            try {
                AppLockManager.setUnlockedSession(this, pkg)
                AppLockManager.clearAllowed(this)
            } catch (_: Exception) {
            }
            return
        }

        // Locked, no session, no grace: block.
        Log.d("CalNyxAppLock", "BLOCK pkg=$pkg previous=$previous session=$session")
        AppLockManager.setPending(this, pkg)
        try {
            AppLockManager.setDecoyLaunch(this, true)
        } catch (_: Exception) {
        }
        setLastForeground(packageName)
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        try {
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        if (instance == this) instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}
}
