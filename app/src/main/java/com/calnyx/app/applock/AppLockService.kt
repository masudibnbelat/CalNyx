// FILE: app/src/main/java/com/calnyx/app/applock/AppLockService.kt
// MIDUSEFIX VERSION - replace your old AppLockService.kt with this file.
// Apply TOGETHER with AppLockManager-MIDUSEFIX.kt and
// NavGraph-MIDUSEFIX.kt (which ends the session when CalNyx
// itself resumes). The calculator file that sets the session
// on unlock (NormalCalculatorScreen-UNLOCK / LIFTED) must
// already be in place.
// Rule, exactly as the user stated: while the user is INSIDE
// a locked app, it NEVER locks, no matter how long. The
// timer starts ONLY when the user really leaves that app,
// and only then does the grace time count.
// Two holes in UNLOCKFIX that could still lock mid-use:
//  1. onServiceConnected() called endUnlockedSession(), so a
//     service restart (Oppo kills background services, an
//     app update, a reboot of the service) turned a live
//     session into a short grace window. After that grace
//     expired, the next screen inside the SAME app blocked.
//     Now a restart NEVER ends a session. The session ends
//     only on a real switch away (below) or on CalNyx resume
//     (NavGraph). After a reboot the first foreground app is
//     not the locked app, so that switch ends it then —
//     privacy after reboot is kept, without breaking mid-use.
//  2. lastForegroundPkg lived only in memory, so a restart
//     forgot it and the next screen inside the same app
//     looked like a fresh open. Now it is persisted in
//     AppLockManager, and restored on connect. On top of
//     that, a session app is allowed by its session alone,
//     even if lastForeground was lost — a session app is
//     only ended by leaving it, never by a restart.
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
        // MIDUSEFIX: restore, do NOT end the session here.
        // A restart while the user is inside a locked app must
        // not start any timer and must not lock that app.
        lastForegroundPkg = try {
            AppLockManager.getLastForegroundPkg(this)
        } catch (_: Exception) {
            null
        }
        Log.d("CalNyxAppLock", "service connected, restored last=$lastForegroundPkg session=" + try {
            AppLockManager.getUnlockedSession(this)
        } catch (_: Exception) {
            null
        })
        // NOTE: intervened is deliberately NOT cleared here.
        // It is consumed on the next locked-app event, so a
        // return after CalNyx is still checked as a switch.
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
            setLastForeground(null)
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

        var session = try {
            AppLockManager.getUnlockedSession(this)
        } catch (_: Exception) {
            null
        }

        // MIDUSEFIX, session first: if this IS the session app
        // and CalNyx did not intervene in between, it is still
        // the same use, no matter how long, and no matter
        // whether the service restarted in between and lost
        // its in-memory lastForeground. NEVER block here.
        if (session != null && pkg == session && !intervened) {
            Log.d("CalNyxAppLock", "session allow pkg=$pkg")
            setLastForeground(pkg)
            return
        }

        // Continuation of an UNLOCKED app: harmless, allow.
        // A locked app without a session is NOT allowed here
        // on continuation alone — it must go through the
        // grace/block check below, otherwise re-locking an
        // app while it is still in front would be bypassed.
        if (pkg == lastForegroundPkg && !intervened && !AppLockManager.isLocked(this, pkg)) {
            Log.d("CalNyxAppLock", "continuation allow unlocked pkg=$pkg")
            return
        }

        // App switch. This is the ONLY place a session ends
        // in this service, and this is where the timer starts:
        // the user really left the session app for another
        // real app. Staying inside the same app, opening a
        // new screen/chat/dialog inside it, the keyboard, or
        // the notification shade never reaches here as a
        // switch away from the session app.
        val previous = lastForegroundPkg
        setLastForeground(pkg)

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

    override fun onInterrupt() {}
}
