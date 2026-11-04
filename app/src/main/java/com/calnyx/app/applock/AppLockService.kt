// FILE: app/src/main/java/com/calnyx/app/applock/AppLockService.kt
// UNLOCK FIX VERSION - replace your old AppLockService.kt with this file.
// Root cause of the unlock loop in the video:
// The service blocked on EVERY window change of a locked app.
// After unlock, the app's own next window change (new screen,
// dialog, splash -> main, and CalNyx's own background event)
// was treated as a fresh open, so it blocked again within
// seconds, launched the calculator again, and looped.
// Fix - block ONLY on an app switch, never while using:
//  1. lastForegroundPkg remembers which app was last in front.
//     If the same app is still in front, it is a continuation
//     and is NEVER blocked, even if its session / timer expired.
//  2. CalNyx's own events are ignored completely. CalNyx
//     itself ends the session on resume (see NavGraph), and
//     sets the intervened flag, so a return to the locked app
//     after CalNyx is checked as a switch, not a continuation.
//  3. System overlays (notification shade, keyboard, share
//     sheet) have no launcher intent, so they do not change
//     lastForeground and do not end a session.
//  4. A real switch to another app / home ends the session
//     and starts the grace timer. A return within grace
//     restarts the session; after grace it blocks again.
// Debug: filter logcat with tag CalNyxAppLock to see every
// decision if it still fails.
package com.calnyx.app.applock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AppLockService : AccessibilityService() {

    private var lastForegroundPkg: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = 0
            notificationTimeout = 100
        }
        serviceInfo = info
        lastForegroundPkg = null
        try {
            AppLockManager.endUnlockedSession(this)
            AppLockManager.clearCalNyxIntervened(this)
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // CalNyx itself: ignored completely. Its background /
        // foreground events must NOT kill an unlocked session,
        // otherwise unlock -> WhatsApp -> CalNyx background event
        // would end the session and re-block WhatsApp.
        // Exit to CalNyx is handled by CalNyx itself on resume
        // (NavGraph), which ends the session and sets intervened.
        if (pkg == packageName) return

        if (!AppLockManager.isEnabled(this)) {
            lastForegroundPkg = null
            return
        }

        // System overlay: do not change lastForeground, do not
        // end a session, do not block.
        if (!hasLauncher(pkg)) {
            Log.d("CalNyxAppLock", "overlay ignored pkg=$pkg last=$lastForegroundPkg")
            return
        }

        val intervened = try {
            AppLockManager.isCalNyxIntervened(this)
        } catch (_: Exception) {
            false
        }
        if (intervened) {
            try {
                AppLockManager.clearCalNyxIntervened(this)
            } catch (_: Exception) {
            }
        }

        // Continuation: same app still in front, and CalNyx did
        // not intervene in between. NEVER block here, no matter
        // how long the user has been inside the app.
        if (pkg == lastForegroundPkg && !intervened) {
            Log.d("CalNyxAppLock", "continuation allow pkg=$pkg")
            return
        }

        // App switch.
        val previous = lastForegroundPkg
        lastForegroundPkg = pkg

        var session = try {
            AppLockManager.getUnlockedSession(this)
        } catch (_: Exception) {
            null
        }

        // The previous app was the unlocked session app and the
        // user left it for a real app: session ends, grace starts.
        if (previous != null && previous == session && previous != pkg) {
            Log.d("CalNyxAppLock", "switch away, ending session pkg=$previous")
            try {
                AppLockManager.endUnlockedSession(this)
            } catch (_: Exception) {
            }
            session = null
        }

        if (!AppLockManager.isLocked(this, pkg)) {
            Log.d("CalNyxAppLock", "switch to unlocked app pkg=$pkg")
            return
        }

        // This app itself is the unlocked session app.
        if (session != null && pkg == session) {
            Log.d("CalNyxAppLock", "session allow pkg=$pkg")
            return
        }

        // Grace return: within the auto-lock time after exit.
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
        // CalNyx becomes foreground next, but its events are
        // ignored, so record it here: the next locked-app event
        // must be a switch, not a continuation.
        lastForegroundPkg = packageName
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

    override fun onInterrupt() {}
}
