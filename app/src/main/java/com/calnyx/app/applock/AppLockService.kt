// FILE: app/src/main/java/com/calnyx/app/applock/AppLockService.kt
// PANEL6 VERSION (based on PANEL3 + OrientationGuard hook) - replace your old AppLockService.kt with this file.
// Based on PANEL2 (FIXED2 session logic unchanged). Only change:
// screenshot now uses the phone's OFFICIAL screenshot via
// GLOBAL_ACTION_TAKE_SCREENSHOT (Android 9+). The system shows
// its own screenshot preview, so the user can see it, share it
// and edit it. The old custom takeScreenshot() + save-to-file
// path is removed - it only showed a toast and the user could
// not tell if a screenshot was taken.
// Based on MOAN (FIXED2 + power/lock). Only addition: screenshot
// for the floating-ball mini panel. All FIXED2 session logic
// unchanged. Screenshot uses takeScreenshot() (Android 11+ only)
// and saves to Pictures/CalNyx. Needs canTakeScreenshot=true in
// app_lock_service_config.xml and Accessibility on. On Android
// 9/10 it returns false and the panel shows a note.
// Apply TOGETHER with AppLockManager-FIXED2.kt and NavGraph-FIXED2.kt.
// NormalCalculatorScreen (UNLOCK / LIFTED) stays as-is.
//
// WHY MIDUSEFIX STILL LOCKED MID-USE - the 3 holes found in your pasted code:
// 1. hasLauncher() was the only filter. Keyboard (Gboard / Samsung /
//    SwiftKey), permission dialog, share sheet, file picker, Play
//    Services dialog ALL have a launch intent, so the service thought
//    "user left WhatsApp for another app" when the keyboard just opened.
//    It ended the session and started the 30s grace. After 30s the next
//    window change inside the SAME app blocked. That is exactly your
//    "kichukkhon por lock hoye jay".
// 2. NavGraph ON_RESUME ended the session on EVERY CalNyx resume. That
//    fires during the unlock handoff too, and the intervened flag it set
//    stayed stuck if the next event was CalNyx itself (ignored by the
//    service, so never consumed). Next session-allow then failed.
// 3. A transient package overwrote lastForegroundPkg, so the "previous
//    == session" check broke on return.
//
// FIXED2 - single source of truth is this Service, not NavGraph:
// 1. Transient packages are ignored BEFORE any session logic: system,
//    permission controller, chooser, documents picker, GMS, and the
//    CURRENT keyboard (read from Settings, not a hard-coded list).
//    They never change lastForeground and never end a session.
// 2. Session allow is UNCONDITIONAL: if pkg == session, allow.
//    No intervened check, no lastForeground check. A session ends
//    only on a real switch to another REAL app, or on a manual
//    CalNyx open - both handled here.
// 3. CalNyx foreground is handled HERE, not in NavGraph:
//    - pending != null (decoy launch to block) -> ignore, do not end.
//    - pending == null + session != null (user manually opened CalNyx)
//      -> real exit, end session, grace starts.
// 4. Dialog / popup window events (className contains Dialog/Popup)
//    are ignored - they are not an app switch.
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
