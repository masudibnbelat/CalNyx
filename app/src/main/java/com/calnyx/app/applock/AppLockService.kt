// FILE: app/src/main/java/com/calnyx/app/applock/AppLockService.kt
// LOCK-ON-EXIT + HOME-FIX VERSION - replace your old
// AppLockService.kt with this file.
// This is the LOCK-ON-EXIT version (PANEL6-FIXED3, no timer,
// Recents is an exit, screen off ends the session) WITH the
// HOME FIX below. Companion requestPowerDialog /
// requestLockScreen / requestOfficialScreenshot + OrientationGuard
// hook unchanged. Do NOT use the plain FIXED2 file for this
// service - it has no companion functions, and BallPanelActivity /
// MoanControlScreen will not compile without them.
// HOME FIX (2026-11-07) - the real reason Home never locked:
// hasLauncher() uses getLaunchIntentForPackage(), which only
// looks for CATEGORY_INFO / CATEGORY_LAUNCHER activities - and a
// home launcher's main activity declares CATEGORY_HOME instead,
// so it returns null for the home screen. The service then hit
// the "not a launchable app - ignore" branch below and NEVER
// ended the session on going Home. The session stayed alive
// forever (until screen off / the 10-min stale guard / switching
// to another launchable app / manually opening CalNyx) - that is
// why the 30s timer "sometimes" locked and why lock-on-exit
// still did not lock after Home. Fix: isHomePackage() resolves
// the default home with ACTION_MAIN + CATEGORY_HOME, and the
// home screen now counts as a real app switch, so going Home
// ends the session at once.
// Apply TOGETHER with AppLockManager.kt (LOCK-ON-EXIT) and
// AppLockScreen.kt from the lock-on-exit folder - those two are
// unchanged, only this Service file is new here.
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
//      -> real exit, end session (now locks at once, no grace).
// 4. Dialog / popup window events (className contains Dialog/Popup)
//    are ignored - they are not an app switch.
// FIXED3 - the lock sometimes never locked. TWO holes, one cause:
// the session only ended when the Service SAW an exit, so an exit it
// never saw left the session alive forever and the app never locked:
// 1. Screen off: locking the phone (power button, screen timeout)
//    inside a locked app fires NO window event. The session stayed
//    alive for hours - next open went straight in, no lock.
//    Fix: a screen-off receiver ends the session.
// 2. Ghost session: service killed (battery / OEM) or reboot while a
//    session is alive. The switch away was missed, the session is
//    still in prefs, next open is allowed. Fix: the session now has
//    a last-seen stamp; a session unseen for 10 minutes AND last seen
//    before this service instance started is a ghost and is cleared
//    (the normal lock check below then blocks). While the app is
//    really in use, window events keep refreshing lastSeen, and a
//    service restart mid-use has only a seconds-long gap - so neither
//    fix can ever fire mid-use. The mid-use rule is untouched.
// LOCK-ON-EXIT: there is no grace any more, so "ends the session"
// above now means "locks immediately" - no free re-entry window.
// Debug: adb logcat -s CalNyxAppLock  (filter by pid for clean log)
package com.calnyx.app.applock

import com.calnyx.app.moan.OrientationGuard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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

    // When THIS service instance started. A session last seen before
    // this moment spans a service restart / reboot.
    private var serviceStartedAt: Long = 0L

    // A session unseen for longer than this is stale - the user left
    // the app through an exit the Service never saw. 10 minutes: far
    // longer than any real pause inside the app, so normal use never
    // trips it.
    private val STALE_SESSION_MS = 10L * 60L * 1000L

    // FIXED3: the screen going off is an exit the window events never
    // show. Screen off ends the session - and with no grace any more,
    // opening the app after screen off always locks.
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_OFF) return
            try {
                val session = AppLockManager.getUnlockedSession(this@AppLockService)
                if (session != null) {
                    Log.d("CalNyxAppLock", "screen off, ending session $session")
                    AppLockManager.endUnlockedSession(this@AppLockService)
                }
            } catch (_: Exception) {
            }
        }
    }

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
        serviceStartedAt = System.currentTimeMillis()
        try {
            registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        } catch (_: Exception) {
        }
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

    // HOME FIX: the default home launcher is a real app switch,
    // even though hasLauncher() says false for it (see the header
    // note). Resolved the standard way, with ACTION_MAIN +
    // CATEGORY_HOME - no manifest change, no new permission.
    private fun isHomePackage(packageName: String): Boolean {
        return try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val info = packageManager.resolveActivity(
                homeIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            info?.activityInfo?.packageName == packageName
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

        // LOCK-ON-EXIT: Recents is an exit. Swiping up to Recents (or
        // the Recents button) shows a SystemUI window, which used to
        // be ignored - so minimising to Recents and tapping the same
        // app back left it unlocked. Now it ends the session, so
        // minimising locks too. The notification shade is NOT Recents
        // (its class is StatusBar / NotificationPanel), so pulling the
        // shade down still never ends a session.
        val clsName = event.className?.toString() ?: ""
        if (clsName.contains("Recents", ignoreCase = true)) {
            if (session != null) {
                Log.d("CalNyxAppLock", "recents, ending session $session")
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
        // EXCEPT the home screen: going Home is a real exit (it ends
        // the session in the switch block below). hasLauncher() says
        // false for the home launcher, so without this check the
        // service ignored every Home exit and the session never ended.
        if (!hasLauncher(pkg) && !isHomePackage(pkg)) {
            Log.d("CalNyxAppLock", "overlay ignored pkg=$pkg last=$lastForegroundPkg")
            return
        }

        // FIXED3: session allow is UNCONDITIONAL while the app is really
        // still in front - no matter how long, no matter if the service
        // restarted, no matter what lastForeground says. NEVER block
        // here. This is the whole mid-use rule. ONE exception: a STALE
        // session (see the FIXED3 note at the top of this file). A ghost
        // session is cleared and the normal lock check below runs
        // (no session, so it blocks). While the app is
        // really in use, window events keep refreshing lastSeen, so
        // this can never fire mid-use.
        if (session != null && pkg == session) {
            val now = System.currentTimeMillis()
            val lastSeen = try {
                AppLockManager.getSessionLastSeen(this)
            } catch (_: Exception) {
                0L
            }
            if (lastSeen > 0L && now - lastSeen > STALE_SESSION_MS &&
                lastSeen < serviceStartedAt
            ) {
                Log.d(
                    "CalNyxAppLock",
                    "stale session pkg=$pkg lastSeen=$lastSeen serviceStarted=$serviceStartedAt"
                )
                try {
                    AppLockManager.clearUnlockedSession(this)
                } catch (_: Exception) {
                }
                // Fall through: the normal lock check below runs.
            } else {
                try {
                    AppLockManager.setSessionLastSeen(this, now)
                } catch (_: Exception) {
                }
                Log.d("CalNyxAppLock", "session allow pkg=$pkg")
                setLastForeground(pkg)
                return
            }
        }

        // Continuation of an UNLOCKED app: harmless, allow.
        if (pkg == lastForegroundPkg && !AppLockManager.isLocked(this, pkg)) {
            Log.d("CalNyxAppLock", "continuation allow unlocked pkg=$pkg")
            return
        }

        // Real app switch. A session ends here (and on screen off,
        // Recents, or a manual CalNyx open above).
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

        // Locked, no session: block. There is no grace return any
        // more - every exit locks, so a locked app with no live
        // session always shows the decoy calculator.
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
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onInterrupt() {}
}
