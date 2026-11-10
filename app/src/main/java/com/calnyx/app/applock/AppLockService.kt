// BYPASS FIX (2026-11-08) - split-screen / floating window /
// PiP / uninstall. Base: the FIX4 file - everything below the
// FIX4 header is unchanged except the decoy launch, which is
// now one launchDecoy() helper used by every block path.
// WHY SPLIT / FLOATING BYPASSED THE LOCK: this Service only
// listened to TYPE_WINDOW_STATE_CHANGED. Entering split-screen
// or a floating window from Recents only RESIZES an
// already-running app's window - that fires
// TYPE_WINDOWS_CHANGED, never a state change - so the block
// never fired and the locked app stayed usable. Now
// TYPE_WINDOWS_CHANGED is listened to as well, and every window
// event scans the live window list (getWindows()) for a locked
// app, including windows that are not focused. When a block
// happens in multi-window, Home is pressed first to exit the
// split / freeform (a decoy started inside a split would land
// next to the locked app), and the decoy launches ~350ms later
// with the black overlay covering the gap. Fullscreen blocks
// are unchanged and still launch the decoy at once.
// RECENTS FIX (2026-11-08) - CORRECTION: the user NEVER asked
// for the CALCULATOR to be hidden from Recents - it must stay
// visible there, as before. FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
// is REMOVED from launchDecoy() again (it was added in the
// bypass fix by mistake). What the user asked for is the
// LOCKED apps hidden from Recents, never floating, never split.
// Honest limit, unchanged: no normal app can DELETE another
// app's Recents card with an API (ActivityManager.removeTask()
// needs signature-level REMOVE_TASKS, system only). So the
// card is dismissed the only way left to a normal app: when
// Recents opens, the Service finds each locked app's card by
// its label and swipes it away (up - the dismiss direction on
// Pixel / Samsung / realme). BEST EFFORT on purpose: if this
// phone's Recents uses another gesture, or a card node is not
// exposed, that card stays - and tapping it still blocks, as
// before. Recents still ends the session, and entering split /
// floating FROM Recents is blocked by the window scan.
// SCAN THROTTLE FIX (same delivery): a scan skipped by the
// 500ms throttle used to eat its window, so a floating window
// appearing right after Recents closed could slip through
// until the NEXT window event (which may never come while the
// user just taps inside the floating app). A skipped scan now
// schedules one re-scan 550ms later, and a Recents-skipped
// scan no longer counts against the throttle at all.
// RECENTS DISMISS FIX2 (2026-11-08): the first dismiss was
// tested on this realme and the locked cards STAYED. Three
// real causes, all fixed here: (1) isRecentsActive() checked
// the window ROOT's className for "Recents" - on ColorOS the
// recents root is not named that, so the check vetoed a real
// Recents screen. Now a flag set by the Recents branch itself
// (which uses the event className - the detection that already
// works) plus the active window's package decide. (2) The label
// search used rootInActiveWindow only - in Recents the active
// window can be the focused task's window, while the labels +
// cards live in the SystemUI recents window. Now EVERY live
// window is searched. (3) On ColorOS the app label sits ABOVE
// its card, not inside it, so walking up from the label never
// reached the card. Now the card is also looked for UNDER the
// label (big view, same horizontal centre, below it).
// Also new: the window scan runs once when the service
// (re)connects, so a locked app already floating / in
// split-screen when the service starts is blocked without
// waiting for the next window event.
// RECENTS DISMISS FIX3 (2026-11-08): the user tested FIX2 and
// the locked app STILL showed in Recents, STILL floated and
// STILL split - all three at once. Two possible causes, both
// covered here: (1) the phone may still be running the OLD
// service code (Accessibility never toggled after apprun) -
// a one-time "App lock v3 active" toast on service connect
// now PROVES the new code is running; if it never shows, no
// fix can work until the toggle is done. (2) ColorOS may not
// deliver TYPE_WINDOWS_CHANGED for split / floating
// poll removes the first cause entirely: every 1.2s the
// live window list is checked, no event needed. The poll skips
// while the screen is off and while the decoy is showing.
// RECENTS DISMISS FIX4 (2026-11-08): the user's video proves
// the normal tap lock works (Baenhi -> decoy -> 69= -> Baenhi
// opens) but the card STAYS in Recents, and floating / split
// from the Recents card menu still open with no lock. Four
// concrete weaknesses, all in this file: (1) isRecentsActive()
// required the ACTIVE window to be SystemUI - in Recents the
// active window can be the focused task's window instead, so
// the check vetoed a real Recents screen and the dismiss
// never ran. Inside the first 5s after Recents opens the flag
// alone is now enough. (2) The poll was gated on
// !decoyShowing, and that flag can stick true after an unlock
// (pending is cleared by the unlock before the branch that
// clears the flag runs) - the poll then died silently. It now
// skips only while a block is really in progress (decoy
// showing AND pending set). (3) The dismiss ran only once,
// 700ms after Recents opens - if the cards were still
// rendering, that one pass missed. It now runs twice (700ms
// and 2000ms). (4) The card search could pick the full-screen
// Recents container instead of the card (it is the biggest
// ancestor / biggest view under the label), so the swipe
// landed on the container, not the card. Views >=95% of the
// screen width are now excluded from the card pick. The label
// search depth cap is raised 12 -> 20 for ColorOS trees.
// DIAG BUILD (v5, 2026-11-08): the 23:02 logcat proves the
// tap block works (7 correct blocks of Baenhi) but contains
// NO Recents branch firing at all - no "recents, ending
// session", no dismiss line - even though the user opened
// Recents, and only systemui FrameLayout events marked
// transient. So on this ColorOS, opening Recents does NOT
// fire a state change whose className contains "Recents".
// This build only ADDS logging (no behaviour change, except
// the version toast): every state-change event (pkg + class),
// the live window list on every systemui state change (type
// / pkg / root class per window), dismiss entry / abort
// reasons, and scan skip reasons. One reproduce (Recents 3s,
// then Floating window from the card menu) + the same logcat
// will show exactly what the service sees, and the real fix
// follows from that instead of another guess.
// ROOT CAUSE FOUND (FIX6, 2026-11-08): the diag log proves it.
// onServiceConnected() builds a BRAND-NEW
// AccessibilityServiceInfo and assigns it, which replaces the
// XML config entirely - and that object has
// canRetrieveWindowContent = false (fresh-object default) and
// flags = 0 (no FLAG_RETRIEVE_INTERACTIVE_WINDOWS). So the
// service could NOT retrieve window content at all:
// getWindows() returned null/empty (the diag build's WIN lines
// were empty on all five systemui events) and
// rootInActiveWindow returned null. That is why the window
// scan never saw split / floating, the Recents dismiss never
// found a label, and only the tap block worked (it needs just
// event.packageName). FIX6 sets FLAG_RETRIEVE_INTERACTIVE_WINDOWS
// in onServiceConnected (the canRetrieveWindowContent field
// itself is read-only from code; the flag is the programmatic
// equivalent). It also stops depending on the event
// className for Recents - the diag log shows no state change
// on this phone ever carries "Recents" (Recents fires
// systemui FrameLayout events). The 1.2s poll now detects the
// Recents window directly (root class contains "Recents", or
// a SystemUI window containing the exact "Close all" button
// this phone shows in Recents) and runs the same Recents
// handling the event branch used to do. CORRECTION (same day):
// canRetrieveWindowContent is read-only from code (Kotlin
// 'val', set only from the XML config), so the programmatic
// route is FLAG_RETRIEVE_INTERACTIVE_WINDOWS alone - it
// enables getWindows() / getRootInActiveWindow() the same way.
// FIX7 (2026-11-08): the proof came from the repo itself.
// app/src/main/res/xml/app_lock_service_config.xml says
// android:canRetrieveWindowContent="false" - explicitly.
// And the flag alone did not bring the windows back (the
// user tested after the toggle and nothing changed). So
// FIX7 does BOTH, and fixes the code side properly: the XML
// is changed to "true" (plus typeWindowsChanged and
// flagRetrieveInteractiveWindows), and onServiceConnected
// no longer builds a fresh AccessibilityServiceInfo (which
// would throw the XML value away again) - it takes the info
// the system already built from the XML, changes only the
// settable fields, and puts it back. The toggle OFF/ON after
// apprun is what makes the system re-read the XML.
// UNINSTALL - ACCESSIBILITY LEVEL ONLY: when an uninstall
// confirmation window appears for a locked app (package
// installer / Settings / Play Store), Back dismisses the
// dialog and the decoy calculator shows instead - the app can
// only be uninstalled after it is unlocked from inside the
// calculator (that starts a session, and the check then allows
// it). CalNyx itself is never protected, and there is no
// device-admin / device-owner code. An uninstall that shows NO
// confirmation dialog (e.g. adb) cannot be intercepted -
// Android gives a normal app no hook for that.
// No manifest change, no new permission.
// FILE: app/src/main/java/com/calnyx/app/applock/AppLockService.kt
// DECOY-ACTIVE FLAG FIX (2026-11-08, FIX4) - replace your old
// AppLockService.kt with this file. Base: the DECOY-GRACE file
// (FIX3). ONLY change vs FIX3: the new decoy_active flag is set
// at BLOCK and cleared everywhere the old flag is cleared, and
// notificationTimeout is 0 instead of 100 so block events are not
// throttled. WHY (proved by the debug Toast): the Toast showed
// pending=com.aistudio.bdutility.qkzp, flag=false, enabled=true,
// locked=true - pending intact, old flag false. NavGraph clears
// the OLD flag on EVERY ON_RESUME (it uses it as a one-shot signal
// to pop to Normal once per decoy launch), so by the time the user
// types the secret the old flag is always false. The unlock decision
// must never read it - NormalCalculatorScreen now uses
// AppLockManager.isLiveDecoy(), which reads the NEW flag that
// NavGraph never touches. The old flag is still set/cleared exactly
// as before, so NavGraph's pop-to-Normal keeps working.
// DECOY-GRACE FIX (2026-11-08, FIX3) - replace your old
// AppLockService.kt with this file. Base: the DECOY-SHOWING file
// (FIX2) delivered just before this one - this file CONTAINS all of
// FIX2, so it replaces it. ONLY this Service changes; AppLockManager.kt
// and NormalCalculatorScreen.kt from the first (flash + stale-pending)
// delivery stay exactly as-is - do NOT re-apply them.
// WHY FIX2 STILL FAILED (user's video, 1:45 PM): the locked prayer
// app shows, the decoy calculator arrives more than 3s after the
// block, and in between the user pressed Home. FIX2's 3s grace had
// already expired by the time the decoy event arrived, and the Home
// press had overwritten lastForeground - so the decoy was misread
// as a MANUAL CalNyx open, pending + flag were cleared, and the
// secret code opened Black Hole instead of unlocking. The decoy
// activity is simply slow to start on this phone, and 3s was far
// too short a window for it. The grace is now 10 minutes
// (STALE_SESSION_MS, the same "stale" limit already used for
// sessions): a block is only stale after 10 min. Abandonment still
// clears pending at once when the decoy WAS showing (Home / Recents
// / switch after decoyShowing), so the original stale-pending bug
// does not come back - the long grace only covers a block whose
// decoy never got a chance to show.
// DECOY-SHOWING FIX (2026-11-08, FIX2) - replace your old
// AppLockService.kt with this file. Base: the FLASH + STALE-PENDING
// file delivered just before this one. ONLY this Service changes;
// AppLockManager.kt and NormalCalculatorScreen.kt from that delivery
// stay exactly as-is - do NOT re-apply them.
// WHY THE FIRST FIX BROKE UNLOCK: its abandon logic cleared pending
// on ANY window event after a block. But during the block -> decoy
// task switch the system itself fires intermediate events (launcher
// / Recents / task-switch windows). Those were wrongly read as "the
// user left the decoy", so pending + flag were cleared and the black
// overlay was hidden BEFORE the decoy even appeared - hence the
// locked app still flashed, AND the secret code then opened Black
// Hole instead of unlocking. Now abandon-clearing only runs AFTER
// the decoy has actually been seen in front (decoyShowing), and a
// CalNyx event within 10 min of a block is always treated as the decoy,
// even if a transition event overwrote lastForeground meanwhile.
// FLASH + STALE-PENDING FIX (2026-11-08) - replace your old
// AppLockService.kt with this file. Base: LOCK-ON-EXIT + HOME-FIX.
// No manifest change, no new permission, no new file needed.
// 1. FLASH: when a locked app is blocked, the Service now puts a
//    full-screen opaque BLACK overlay over it (TYPE_ACCESSIBILITY_OVERLAY,
//    an AccessibilityService may use it with no extra permission) in
//    the SAME event that blocks it, BEFORE the decoy calculator
//    activity starts. The overlay eats touches, so nothing under it
//    can be tapped. It is removed the moment the decoy calculator
//    comes to the front, or after 5s as a safety net. Honest limit:
//    Android only tells an AccessibilityService AFTER the locked
//    app's window exists, so a zero-frame block is impossible for
//    any non-system app - but with the overlay the user sees black,
//    never the locked app's content, and no tap reaches it.
// 2. STALE PENDING: pending used to live forever once set. Leaving
//    the decoy by Home / Recents / another app / screen off, or
//    opening CalNyx by its icon afterwards, never cleared it - so
//    the NEXT secret code in the normal calculator opened that old
//    app instead of Black Hole. Now pending is cleared on every
//    abandon path (switch away from CalNyx, Recents, screen off,
//    manual CalNyx open), the decoy flag is only cleared on connect
//    when there is no pending (or it is older than 10 min), and
//    pending now carries a timestamp for that stale check.
// Apply TOGETHER with AppLockManager.kt from this same folder
// (pending timestamp) and NormalCalculatorScreen.kt from this same
// folder (secret code only unlocks on a LIVE decoy).
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
import android.graphics.Color
import android.graphics.PixelFormat
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

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

    // DECOY-SHOWING FIX: true only AFTER a CalNyx decoy event has
    // actually been seen in front. Abandon-clearing (switch away,
    // Recents, manual open) must NEVER run before that - the events
    // between a block and the decoy appearing are task-switch noise
    // from the system, not the user leaving. See the header note.
    private var decoyShowing = false

    // FIX3: bump this on every service-code delivery. The first
    // connect of a new version shows one "App lock vN active"
    // toast, so the user can see the new code is really running.
    private val SERVICE_CODE_VERSION = 7

    // How long ago the current pending block was set, in ms.
    // Long.MAX_VALUE when there is no timestamp (pending set by an
    // older Manager build). Used only for the decoy grace below.
    private fun pendingAgeMs(): Long {
        return try {
            val setAt = AppLockManager.getPendingSetAt(this)
            if (setAt <= 0L) Long.MAX_VALUE
            else System.currentTimeMillis() - setAt
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }

    // When THIS service instance started. A session last seen before
    // this moment spans a service restart / reboot.
    private var serviceStartedAt: Long = 0L

    // A session unseen for longer than this is stale - the user left
    // the app through an exit the Service never saw. 10 minutes: far
    // longer than any real pause inside the app, so normal use never
    // trips it.
    private val STALE_SESSION_MS = 10L * 60L * 1000L

    // FLASH FIX: opaque black cover shown the instant a locked app
    // is blocked, until the decoy calculator is on top. Added in
    // the same event as the block, so the locked app's content is
    // never readable and no tap reaches it. TYPE_ACCESSIBILITY_OVERLAY
    // needs no extra permission for an AccessibilityService and
    // exists since API 26 (this app's minSdk). FLAG_NOT_FOCUSABLE
    // (and NOT FLAG_NOT_TOUCHABLE) so the overlay eats touches.
    // Auto-hide after 5s is only a safety net if the decoy never
    // arrives; the normal path hides it on the decoy event.
    private var blockOverlayView: View? = null
    private val overlayHandler = Handler(Looper.getMainLooper())
    private val overlayAutoHide = Runnable { hideBlockOverlay() }

    private fun showBlockOverlay() {
        try {
            if (blockOverlayView != null) {
                overlayHandler.removeCallbacks(overlayAutoHide)
                overlayHandler.postDelayed(overlayAutoHide, 5000L)
                return
            }
            val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
            val view = View(this)
            view.setBackgroundColor(Color.BLACK)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE
            )
            wm.addView(view, params)
            blockOverlayView = view
            overlayHandler.removeCallbacks(overlayAutoHide)
            overlayHandler.postDelayed(overlayAutoHide, 5000L)
        } catch (_: Exception) {
            blockOverlayView = null
        }
    }

    private fun hideBlockOverlay() {
        try {
            overlayHandler.removeCallbacks(overlayAutoHide)
            val view = blockOverlayView ?: return
            blockOverlayView = null
            val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            try {
                wm?.removeView(view)
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------------
    // BYPASS FIX (2026-11-08): split-screen / floating window /
    // PiP / uninstall. See the file header. Everything here is
    // additive: the FIX4 block / session / overlay logic is not
    // changed, only the decoy launch is factored out into
    // launchDecoy() so every block path uses it.
    // ---------------------------------------------------------------

    // Uninstall flows live in these packages. The two ColorOS /
    // OPPO names are OEM variants - harmless if a given phone
    // does not have them, they simply never match.
    private val installerPkgs = setOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.coloros.packageinstaller",
        "com.oppo.packageinstaller",
        "com.android.settings",
        "com.android.vending"
    )

    private var lastWindowScanAt: Long = 0L
    private var lastBlockPkg: String? = null
    private var lastBlockAt: Long = 0L
    private val blockHandler = Handler(Looper.getMainLooper())
    private val uninstallHandler = Handler(Looper.getMainLooper())
    private var uninstallCheckPending: Boolean = false
    // SCAN RESCAN (2026-11-08): one delayed re-scan after a scan
    // that was skipped (throttle / Recents). At most one queued.
    private var windowRescanPending: Boolean = false
    // RECENTS DISMISS (2026-11-08): one delayed dismiss run per
    // Recents open, at most. A second Recents open schedules again.
    private var recentsDismissScheduled: Boolean = false
    // RECENTS FLAG (FIX2): set when the Recents branch fires (the
    // event className detection that works on this phone). The
    // dismiss run is valid for 20s after that.
    private var recentsOpenSince: Long = 0L

    // One decoy launch for every block path (state change, window
    // scan, uninstall). CORRECTION (2026-11-08): NO
    // FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS here any more - the user
    // wants the CALCULATOR visible in Recents, as before. Only
    // the LOCKED apps are hidden from Recents (swipe-dismiss
    // below), never the calculator.
    private fun launchDecoy() {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
        )
        try {
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    // Shared block: pending + decoy flags + black overlay, exactly
    // as the old inline block did, then the decoy. Re-block of the
    // SAME package is throttled (1500ms) ONLY while its block is
    // still pending - after an abandon clears pending, a fresh tap
    // blocks at once, so the throttle can never leave a hole.
    private fun blockLockedApp(pkg: String) {
        val now = System.currentTimeMillis()
        try {
            if (lastBlockPkg == pkg && now - lastBlockAt < 1500L &&
                AppLockManager.getPending(this) == pkg
            ) return
        } catch (_: Exception) {
        }
        lastBlockPkg = pkg
        lastBlockAt = now
        Log.d("CalNyxAppLock", "BLOCK pkg=$pkg multiWindow=" + isInMultiWindow())
        AppLockManager.setPending(this, pkg)
        try {
            AppLockManager.setDecoyLaunch(this, true)
            AppLockManager.setDecoyActive(this, true)
        } catch (_: Exception) {
        }
        setLastForeground(packageName)
        // FLASH FIX: cover the locked app at once, before the decoy
        // activity starts. NO_ANIMATION on the decoy intent also
        // removes the slide transition the locked app could peek
        // through.
        showBlockOverlay()
        if (isInMultiWindow()) {
            // Split / freeform: a decoy started now would land
            // INSIDE the split, next to the locked app. Home exits
            // split-screen / freeform on Android, and the overlay
            // covers the gap until the decoy arrives (~350ms).
            try {
                performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            } catch (_: Exception) {
            }
            blockHandler.postDelayed({
                try {
                    if (AppLockManager.getPending(this) == pkg) launchDecoy()
                } catch (_: Exception) {
                }
            }, 350L)
        } else {
            launchDecoy()
        }
    }

    // True when any app window is not fullscreen: a split-screen
    // divider, two application windows, or an application window
    // whose bounds are smaller than the screen (freeform / PiP).
    private fun isInMultiWindow(): Boolean {
        return try {
            val wins = windows ?: return false
            var appWindows = 0
            val dm = resources.displayMetrics
            val screenW = dm.widthPixels
            val screenH = dm.heightPixels
            val rect = Rect()
            for (win in wins) {
                if (win.type == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER) return true
                if (win.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    appWindows++
                    try {
                        win.getBoundsInScreen(rect)
                        if (rect.width() > 0 && rect.height() > 0 &&
                            (rect.width() < screenW * 0.95f || rect.height() < screenH * 0.95f)
                        ) return true
                    } catch (_: Exception) {
                    }
                }
            }
            appWindows >= 2
        } catch (_: Exception) {
            false
        }
    }

    // FIX3 (2026-11-08): the window-event scan missed split /
    // floating on this phone twice - either ColorOS does not
    // deliver TYPE_WINDOWS_CHANGED for those transitions, or
    // the service on the phone is still the old code. A short
    // poll removes the first cause entirely: every 1.2s the
    // live window list is checked, no event needed. Battery
    // cost is one tiny window-list read per 1.2s while the
    // screen is on. It stops with the service (blockHandler
    // callbacks are removed in onDestroy) and it never runs
    // while the decoy is showing, so it cannot fight the decoy.
    private val pollRunnable = object : Runnable {
        override fun run() {
            try {
                if (AppLockManager.isEnabled(this@AppLockService)) {
                    val pm = getSystemService(POWER_SERVICE) as? PowerManager
                    if (pm == null || pm.isInteractive) {
                        // FIX6: Recents has no reliable event on
                        // this phone - detect its window directly,
                        // every tick. This also ends the session on
                        // Recents, so tapping a card afterwards
                        // blocks instead of opening with the old
                        // session still live.
                        try {
                            if (isRecentsWindowPresent()) onRecentsDetected("com.android.systemui")
                        } catch (_: Exception) {
                        }
                        // Skip the scan only while a block is
                        // genuinely in progress AND the decoy is
                        // showing. The old gate (any decoyShowing)
                        // could stick true after an unlock and
                        // silently kill the poll forever.
                        val decoyBlocking = decoyShowing && try {
                            AppLockManager.getPending(this@AppLockService) != null
                        } catch (_: Exception) {
                            false
                        }
                        if (!decoyBlocking) scanWindowsForLockedApps()
                    }
                }
            } catch (_: Exception) {
            } finally {
                blockHandler.postDelayed(this, 1200L)
            }
        }
    }

    // DIAG (v5): log every live window's type / package / root
    // class. Called once per systemui state change - bounded,
    // and it shows exactly what Recents looks like on this
    // phone (which window is the Recents window, what its root
    // is named). Remove after the diagnosis.
    private fun logWindowList() {
        try {
            val wins = windows ?: return
            for (win in wins) {
                val root = try {
                    win.root
                } catch (_: Exception) {
                    null
                }
                val cls = try {
                    root?.className?.toString()
                } catch (_: Exception) {
                    null
                }
                val p = try {
                    root?.packageName?.toString()
                } catch (_: Exception) {
                    null
                }
                Log.d("CalNyxAppLock", "WIN type=${win.type} pkg=$p cls=$cls")
                try {
                    root?.recycle()
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    // One delayed re-scan after a skipped scan (550ms > the 500ms
    // throttle, so one retry always runs). The chain ends by
    // itself: a re-scan that really runs schedules nothing.
    private fun scheduleWindowRescan() {
        if (windowRescanPending) return
        windowRescanPending = true
        blockHandler.postDelayed({
            windowRescanPending = false
            try {
                if (AppLockManager.isEnabled(this)) scanWindowsForLockedApps()
            } catch (_: Exception) {
            }
        }, 550L)
    }

    // WINDOWS_CHANGED scan: look at every live window, not just
    // the focused one - in split-screen BOTH apps have windows,
    // and in freeform / PiP the locked app's window is the small
    // one. Throttled to once per 500ms; WINDOWS_CHANGED floods.
    // While Recents is open the scan is skipped on purpose: its
    // cards are snapshots, not live windows, and the state-changed
    // Recents branch already ends the session there - but that
    // skip must NOT eat the throttle window (see below).
    private fun scanWindowsForLockedApps() {
        val now = System.currentTimeMillis()
        if (now - lastWindowScanAt < 500L) {
            scheduleWindowRescan()
            return
        }
        val locked = try {
            AppLockManager.getLockedPackages(this)
        } catch (_: Exception) {
            return
        }
        if (locked.isEmpty()) return
        val pkgs = mutableListOf<String>()
        var sawRecents = false
        var sawInstaller = false
        try {
            val wins = windows ?: return
            for (win in wins) {
                val root = try {
                    win.root
                } catch (_: Exception) {
                    null
                } ?: continue
                try {
                    val cls = root.className?.toString() ?: ""
                    if (cls.contains("Recents", ignoreCase = true)) sawRecents = true
                    val p = root.packageName?.toString()
                    if (p != null) {
                        pkgs.add(p)
                        if (p in installerPkgs) sawInstaller = true
                    }
                } finally {
                    try {
                        root.recycle()
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
            return
        }
        if (sawRecents) {
            // Recents open: do not count this as a scan (the
            // throttle is untouched) and re-check shortly after,
            // in case a floating window appears as Recents closes.
            Log.d("CalNyxAppLock", "SCAN skip: recents open pkgs=$pkgs")
            scheduleWindowRescan()
            return
        }
        lastWindowScanAt = now
        // An uninstall dialog appearing may only fire a windows
        // change, not a state change - let the delayed scan below
        // look at it too.
        if (sawInstaller) scheduleUninstallCheck()
        val session = try {
            AppLockManager.getUnlockedSession(this)
        } catch (_: Exception) {
            null
        }
        for (p in pkgs) {
            if (p == packageName) continue
            if (p !in locked) continue
            if (session != null && session == p) {
                Log.d("CalNyxAppLock", "SCAN skip locked pkg=$p (live session)")
                continue
            }
            Log.d("CalNyxAppLock", "SCAN found locked pkg=$p pkgs=$pkgs")
            blockLockedApp(p)
            return
        }
    }

    // FIX6: Recents detection that does not depend on the event
    // className (on this ColorOS no state change ever carries
    // "Recents" - Recents fires systemui FrameLayout events).
    // A Recents window is present when any live window's root
    // class contains "Recents", or when a SystemUI window
    // contains the exact "Close all" button this phone shows in
    // Recents. Checked from the poll, so it works even when no
    // event fires at all.
    private fun isRecentsWindowPresent(): Boolean {
        return try {
            val wins = windows ?: return false
            for (win in wins) {
                val root = try {
                    win.root
                } catch (_: Exception) {
                    null
                } ?: continue
                try {
                    val cls = root.className?.toString() ?: ""
                    if (cls.contains("Recents", ignoreCase = true)) return true
                    val p = root.packageName?.toString() ?: ""
                    if (p == "com.android.systemui" && hasExactText(root, "Close all")) return true
                } finally {
                    try {
                        root.recycle()
                    } catch (_: Exception) {
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    // Exact-text search (no prefix matching - a node reading
    // just "Close" must NOT count as the Recents "Close all"
    // button).
    private fun hasExactText(node: AccessibilityNodeInfo?, text: String, depth: Int = 0): Boolean {
        if (node == null || depth > 20) return false
        if (node.text?.toString().equals(text, ignoreCase = true)) return true
        if (node.contentDescription?.toString().equals(text, ignoreCase = true)) return true
        for (i in 0 until node.childCount) {
            val child = try {
                node.getChild(i)
            } catch (_: Exception) {
                null
            } ?: continue
            if (hasExactText(child, text, depth + 1)) return true
        }
        return false
    }

    // Shared Recents handling: what the event branch used to do
    // inline. Called from the branch (when the className does
    // carry "Recents") and from the poll (when a Recents window
    // is found directly). pkg is the event package, or
    // "com.android.systemui" from the poll.
    private fun onRecentsDetected(pkg: String) {
        val session = try {
            AppLockManager.getUnlockedSession(this)
        } catch (_: Exception) {
            null
        }
        if (session != null) {
            Log.d("CalNyxAppLock", "recents, ending session $session")
            try {
                AppLockManager.endUnlockedSession(this)
            } catch (_: Exception) {
            }
        }
        val pending = try {
            AppLockManager.getPending(this)
        } catch (_: Exception) {
            null
        }
        if (decoyShowing && pending != null) {
            decoyShowing = false
            try {
                AppLockManager.clearPending(this)
                AppLockManager.clearDecoyLaunch(this)
                AppLockManager.clearDecoyActive(this)
            } catch (_: Exception) {
            }
            hideBlockOverlay()
        }
        recentsOpenSince = System.currentTimeMillis()
        scheduleRecentsDismiss()
        setLastForeground(pkg)
    }

    // RECENTS DISMISS (2026-11-08, best effort): Android gives a
    // normal app NO API to delete another app's Recents card
    // (removeTask needs signature-level REMOVE_TASKS). The only
    // route left is the Accessibility one: when Recents is open,
    // find each locked app's card by its label and swipe it away
    // (up - the dismiss direction on Pixel / Samsung / realme).
    // If this phone's Recents uses another gesture, or a card node
    // is not exposed, that card stays - and tapping it still
    // blocks, as before. The swipe is aimed ONLY at a card whose
    // label matches a locked app exactly (or a truncated label),
    // and it only fires while Recents is still the active window,
    // so it cannot swipe anything else.
    private fun scheduleRecentsDismiss() {
        if (recentsDismissScheduled) return
        recentsDismissScheduled = true
        blockHandler.postDelayed({
            dismissLockedRecentsCards()
        }, 700L)
        // Second attempt: the cards may still be rendering at
        // 700ms, or the first pass may have missed a label. A
        // second pass is a no-op when the first already removed
        // the cards (no label found, nothing queued).
        blockHandler.postDelayed({
            recentsDismissScheduled = false
            dismissLockedRecentsCards()
        }, 2000L)
    }

    private fun isRecentsActive(): Boolean {
        // Flag first: set by the Recents branch, which uses the
        // event className - the detection that already works on
        // this phone.
        if (recentsOpenSince <= 0L) return false
        val elapsed = System.currentTimeMillis() - recentsOpenSince
        if (elapsed > 20000L) return false
        // The dismiss run starts 700ms after Recents opens and
        // its staggered swipes end by ~3s. Inside that window
        // the flag alone is enough - the active-window checks
        // below misfire on this phone (in Recents the active
        // window can be the focused task's window, not
        // SystemUI), and a misfire used to abort the whole
        // dismiss.
        if (elapsed < 5000L) return true
        // After that, require a live signal: the active window
        // is SystemUI / Recents, or ANY live window root is
        // named Recents.
        return try {
            val root = rootInActiveWindow
            if (root != null) {
                try {
                    val pkg = root.packageName?.toString() ?: ""
                    val cls = root.className?.toString() ?: ""
                    if (pkg.contains("systemui", ignoreCase = true) ||
                        pkg.contains("recents", ignoreCase = true) ||
                        cls.contains("Recents", ignoreCase = true)
                    ) return true
                } finally {
                    try {
                        root.recycle()
                    } catch (_: Exception) {
                    }
                }
            }
            val wins = try {
                windows
            } catch (_: Exception) {
                null
            } ?: return false
            for (win in wins) {
                val r = try {
                    win.root
                } catch (_: Exception) {
                    null
                } ?: continue
                try {
                    if ((r.className?.toString() ?: "").contains("Recents", ignoreCase = true)) return true
                } finally {
                    try {
                        r.recycle()
                    } catch (_: Exception) {
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun labelMatches(text: String, label: String): Boolean {
        val t = text.trim()
        if (t.equals(label, ignoreCase = true)) return true
        // Truncated card label ("Faceb..."): the visible text is a
        // prefix of the real label and at least 5 chars long.
        val stripped = t.trimEnd('…', '.').trim()
        return stripped.length >= 5 && label.startsWith(stripped, ignoreCase = true)
    }

    // Depth-limited search for a node whose text / description is
    // a locked app's label. Returns the node (caller recycles the
    // root, not this node). Nodes are deliberately NOT recycled
    // inside this search: the match may be a descendant, and
    // recycling the path would invalidate it. A dismiss run opens
    // only a few dozen nodes, once per Recents open.
    private fun findLabelNode(node: AccessibilityNodeInfo?, label: String, depth: Int = 0): AccessibilityNodeInfo? {
        if (node == null || depth > 20) return null
        val text = node.text?.toString()
        if (text != null && labelMatches(text, label)) return node
        val desc = node.contentDescription?.toString()
        if (desc != null && labelMatches(desc, label)) return node
        for (i in 0 until node.childCount) {
            val child = try {
                node.getChild(i)
            } catch (_: Exception) {
                null
            } ?: continue
            val found = findLabelNode(child, label, depth + 1)
            if (found != null) return found
        }
        return null
    }

    // Walk up from the label node to the card itself: the biggest
    // ancestor that is still card-sized (at least 25% of the
    // screen wide and 12% tall). The label text node itself is
    // far too small to aim a swipe at. On this phone (realme /
    // ColorOS) the label sits ABOVE the card, not inside it, so
    // the ancestor walk alone finds nothing - then fall back to
    // the biggest card-sized view in the same window that sits
    // under the label (same horizontal centre, below it).
    private fun cardBoundsFor(node: AccessibilityNodeInfo?, root: AccessibilityNodeInfo?): Rect? {
        var current = node
        var best: Rect? = null
        var bestArea = 0
        var steps = 0
        val dm = resources.displayMetrics
        val minW = (dm.widthPixels * 0.25f).toInt()
        val minH = (dm.heightPixels * 0.12f).toInt()
        // A real card is never (nearly) full-screen wide; the
        // full-screen Recents container is, and it used to win
        // as "biggest ancestor", so the swipe landed on the
        // container instead of the card.
        val maxW = (dm.widthPixels * 0.95f).toInt()
        val rect = Rect()
        while (current != null && steps++ < 8) {
            try {
                current.getBoundsInScreen(rect)
                val area = rect.width() * rect.height()
                if (rect.width() >= minW && rect.width() < maxW && rect.height() >= minH && area > bestArea) {
                    best = Rect(rect)
                    bestArea = area
                }
            } catch (_: Exception) {
            }
            current = try {
                current.parent
            } catch (_: Exception) {
                null
            }
        }
        if (best != null) return best
        return findBigCardUnder(node, root)
    }

    // Fallback for ColorOS layout: the card is the biggest view
    // in this window that is at least 40% x 40% of the screen,
    // sits under the label and shares its horizontal centre
    // (within 15% of the screen width). Nodes are not recycled
    // here: at most ~200 nodes, once per Recents open.
    private fun findBigCardUnder(labelNode: AccessibilityNodeInfo?, root: AccessibilityNodeInfo?): Rect? {
        if (labelNode == null || root == null) return null
        val dm = resources.displayMetrics
        val labelRect = Rect()
        try {
            labelNode.getBoundsInScreen(labelRect)
        } catch (_: Exception) {
            return null
        }
        val labelCx = labelRect.centerX()
        val labelBottom = labelRect.bottom
        val minW = (dm.widthPixels * 0.40f).toInt()
        val minH = (dm.heightPixels * 0.40f).toInt()
        val maxW = (dm.widthPixels * 0.95f).toInt()
        val maxDx = (dm.widthPixels * 0.15f).toInt()
        val topSlop = (dm.heightPixels * 0.05f).toInt()
        var best: Rect? = null
        var bestArea = 0
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var visited = 0
        while (stack.isNotEmpty() && visited++ < 200) {
            val n = stack.removeFirst()
            val r = Rect()
            try {
                n.getBoundsInScreen(r)
                if (r.width() >= minW && r.width() < maxW && r.height() >= minH &&
                    kotlin.math.abs(r.centerX() - labelCx) <= maxDx &&
                    r.top >= labelBottom - topSlop
                ) {
                    val area = r.width() * r.height()
                    if (area > bestArea) {
                        best = Rect(r)
                        bestArea = area
                    }
                }
            } catch (_: Exception) {
            }
            for (i in 0 until n.childCount) {
                try {
                    n.getChild(i)?.let { stack.add(it) }
                } catch (_: Exception) {
                }
            }
        }
        return best
    }

    private fun swipeUpToDismiss(rect: Rect) {
        try {
            val cx = rect.centerX().toFloat()
            val startY = rect.centerY() + rect.height() * 0.30f
            val endY = rect.centerY() - rect.height() * 0.45f
            val path = Path()
            path.moveTo(cx, startY)
            path.lineTo(cx, endY)
            val desc = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, 300L))
                .build()
            dispatchGesture(desc, null, null)
        } catch (_: Exception) {
        }
    }

    private fun dismissLockedRecentsCards() {
        try {
            if (!AppLockManager.isEnabled(this)) return
            if (!isRecentsActive()) {
                Log.d("CalNyxAppLock", "DISMISS abort: recents not active")
                return
            }
            val locked = AppLockManager.getLockedPackages(this)
            Log.d("CalNyxAppLock", "DISMISS run locked=${locked.size}")
            if (locked.isEmpty()) return
            // Search EVERY live window, not just rootInActiveWindow:
            // in Recents the active window can be the focused task's
            // window, while the labels + cards live in the SystemUI
            // recents window. Each window is searched on its own,
            // and the card is looked for in that same window.
            var delayMs = 0L
            var dismissed = 0
            val wins = try {
                windows
            } catch (_: Exception) {
                null
            } ?: return
            for (win in wins) {
                if (dismissed >= 5) break
                if (!isRecentsActive()) return
                val root = try {
                    win.root
                } catch (_: Exception) {
                    null
                } ?: continue
                try {
                    for (pkg in locked) {
                        if (dismissed >= 5) break
                        val label = appLabel(pkg) ?: continue
                        val labelNode = findLabelNode(root, label) ?: continue
                        val bounds = cardBoundsFor(labelNode, root) ?: continue
                        val rect = Rect(bounds)
                        val at = delayMs
                        blockHandler.postDelayed({
                            try {
                                if (isRecentsActive()) swipeUpToDismiss(rect)
                            } catch (_: Exception) {
                            }
                        }, at)
                        delayMs += 450L
                        dismissed++
                    }
                } finally {
                    try {
                        root.recycle()
                    } catch (_: Exception) {
                    }
                }
            }
            Log.d("CalNyxAppLock", "recents dismiss queued $dismissed locked cards")
        } catch (_: Exception) {
        }
    }

    // UNINSTALL: the confirmation dialog may not be populated yet
    // when its window first appears, so check again after 400ms.
    // At most one check is ever queued.
    private fun scheduleUninstallCheck() {
        if (uninstallCheckPending) return
        try {
            if (AppLockManager.getLockedPackages(this).isEmpty()) return
        } catch (_: Exception) {
            return
        }
        uninstallCheckPending = true
        uninstallHandler.postDelayed({
            uninstallCheckPending = false
            checkUninstallWindow()
        }, 400L)
    }

    private fun appLabel(pkg: String): String? {
        return try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info)?.toString()?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    // Depth-limited, null-safe node search. Children are recycled
    // after they are read; the caller recycles the root.
    private fun nodeContainsText(node: AccessibilityNodeInfo?, text: String, depth: Int = 0): Boolean {
        if (node == null || depth > 12) return false
        val nodeText = node.text?.toString()
        if (nodeText != null && nodeText.contains(text, ignoreCase = true)) return true
        val desc = node.contentDescription?.toString()
        if (desc != null && desc.contains(text, ignoreCase = true)) return true
        for (i in 0 until node.childCount) {
            val child = try {
                node.getChild(i)
            } catch (_: Exception) {
                null
            } ?: continue
            try {
                if (nodeContainsText(child, text, depth + 1)) return true
            } finally {
                try {
                    child.recycle()
                } catch (_: Exception) {
                }
            }
        }
        return false
    }

    // Look at the active window: is it an uninstall confirmation
    // for a locked app? A package-installer window showing a locked
    // app's label IS an uninstall (that app has no other window
    // there). Settings / Play Store need a confirmation signal too
    // ("Cancel" / "Do you want to uninstall"), so merely opening a
    // locked app's App-info page does NOT trigger the lock.
    // A live unlocked session for that app allows the uninstall -
    // the user unlocked it from the calculator first, as intended.
    private fun checkUninstallWindow() {
        try {
            if (!AppLockManager.isEnabled(this)) return
            val locked = AppLockManager.getLockedPackages(this)
            if (locked.isEmpty()) return
            val root = try {
                rootInActiveWindow
            } catch (_: Exception) {
                null
            } ?: return
            try {
                val rootPkg = root.packageName?.toString() ?: return
                if (rootPkg !in installerPkgs) return
                val isInstallerWindow = rootPkg == "com.android.packageinstaller" ||
                    rootPkg == "com.google.android.packageinstaller" ||
                    rootPkg == "com.coloros.packageinstaller" ||
                    rootPkg == "com.oppo.packageinstaller"
                val session = try {
                    AppLockManager.getUnlockedSession(this)
                } catch (_: Exception) {
                    null
                }
                for (pkg in locked) {
                    if (session != null && session == pkg) continue
                    val label = appLabel(pkg) ?: continue
                    val labelFound = nodeContainsText(root, label) ||
                        nodeContainsText(root, pkg)
                    if (!labelFound) continue
                    if (!isInstallerWindow) {
                        val confirming = nodeContainsText(root, "Cancel") ||
                            nodeContainsText(root, "Do you want to uninstall") ||
                            nodeContainsText(root, "uninstall this app")
                        if (!confirming) continue
                    }
                    blockUninstall(pkg)
                    return
                }
            } finally {
                try {
                    root.recycle()
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    // Uninstall of a locked app, no live session: dismiss the
    // confirmation with Back, then show the decoy calculator -
    // the app can only be uninstalled after it is unlocked from
    // inside the calculator (which starts a session, and the
    // check above then allows the uninstall).
    private fun blockUninstall(pkg: String) {
        try {
            if (AppLockManager.getUnlockedSession(this) == pkg) return
        } catch (_: Exception) {
            return
        }
        Log.d("CalNyxAppLock", "BLOCK uninstall pkg=$pkg")
        try {
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        } catch (_: Exception) {
        }
        AppLockManager.setPending(this, pkg)
        try {
            AppLockManager.setDecoyLaunch(this, true)
            AppLockManager.setDecoyActive(this, true)
        } catch (_: Exception) {
        }
        setLastForeground(packageName)
        showBlockOverlay()
        blockHandler.postDelayed({
            try {
                if (AppLockManager.getPending(this) == pkg) launchDecoy()
            } catch (_: Exception) {
            }
        }, 300L)
    }

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
                // STALE-PENDING FIX: screen off also abandons a decoy
                // that was showing. Without this, pending stayed and
                // the next secret code opened the old app.
                if (AppLockManager.getPending(this@AppLockService) != null) {
                    AppLockManager.clearPending(this@AppLockService)
                    AppLockManager.clearDecoyLaunch(this@AppLockService)
                    AppLockManager.clearDecoyActive(this@AppLockService)
                }
                this@AppLockService.decoyShowing = false
                this@AppLockService.hideBlockOverlay()
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
        // FIX7 (2026-11-08): do NOT build a fresh
        // AccessibilityServiceInfo here. A fresh object has
        // canRetrieveWindowContent = false and replaces the XML
        // config entirely - that was the root cause (the XML
        // itself also said "false", and the flag alone did not
        // bring the windows back). Take the info the system
        // already built from the XML (which FIX7 sets to
        // canRetrieveWindowContent = true), change only the
        // settable fields, and put it back - the XML capability
        // survives.
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOWS_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.notificationTimeout = 0
        serviceInfo = info
        serviceStartedAt = System.currentTimeMillis()
        decoyShowing = false
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
        // STALE-PENDING FIX: do NOT clear the decoy flag blindly any
        // more. If a block is still pending, the decoy may still be
        // showing and the user may still type the secret into it -
        // clearing the flag would make the calculator open Black Hole
        // instead of unlocking. Only clear it when there is no
        // pending at all, or the pending block is older than the
        // 10-minute stale guard (service restart / reboot long after
        // the decoy was abandoned).
        try {
            AppLockManager.clearCalNyxIntervened(this)
            val pendingNow = AppLockManager.getPending(this)
            if (pendingNow == null) {
                AppLockManager.clearDecoyLaunch(this)
                AppLockManager.clearDecoyActive(this)
            } else {
                val setAt = AppLockManager.getPendingSetAt(this)
                if (setAt > 0L && System.currentTimeMillis() - setAt > STALE_SESSION_MS) {
                    Log.d("CalNyxAppLock", "stale pending on connect, clearing $pendingNow")
                    AppLockManager.clearPending(this)
                    AppLockManager.clearDecoyLaunch(this)
                    AppLockManager.clearDecoyActive(this)
                }
            }
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
        // FIX2: a locked app may already be floating / in
        // split-screen when the service (re)starts - scan once
        // now, so it is blocked without waiting for the next
        // window event.
        try {
            scanWindowsForLockedApps()
        } catch (_: Exception) {
        }
        // FIX3: start the 1.2s poll (split / floating safety net).
        blockHandler.postDelayed(pollRunnable, 1200L)
        // FIX3: prove the new code is actually running. The first
        // time this version connects it shows one short toast -
        // if the user never sees it after toggling Accessibility,
        // the phone is still running the OLD service code and no
        // fix can work.
        try {
            val prefs = getSharedPreferences("calnyx_applock_service", MODE_PRIVATE)
            if (prefs.getInt("service_code_version", 0) != SERVICE_CODE_VERSION) {
                prefs.edit().putInt("service_code_version", SERVICE_CODE_VERSION).apply()
                Toast.makeText(this, "App lock v7 active", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
        }
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
        val eventType = event?.eventType ?: return
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return
        // BYPASS FIX: a window entering split-screen / freeform /
        // PiP only resizes - it fires WINDOWS_CHANGED, never a
        // state change - so the old code never saw it. Scan the
        // live window list for a locked app instead.
        if (eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            if (!AppLockManager.isEnabled(this)) return
            scanWindowsForLockedApps()
            return
        }
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
        // STALE-PENDING FIX (2026-11-08): "pending != null" alone no
        // longer means "this is the decoy". A pending block survives
        // in prefs after the decoy was abandoned (Home, Recents,
        // another app, screen off), so a LATER manual CalNyx open
        // was wrongly treated as the decoy - the session was never
        // ended and pending was never cleared, and the next secret
        // code then opened the old app instead of Black Hole.
        // DECOY-SHOWING FIX (FIX2): the first version of this fix
        // cleared pending on ANY event after a block - but during
        // the block -> decoy task switch the system itself fires
        // intermediate events (launcher / Recents / task-switch
        // windows), and those cleared the block and hid the overlay
        // before the decoy even appeared. So now: a CalNyx event is
        // a decoy when lastForeground is already CalNyx (set at
        // block time) OR when the block was set less than 10 min ago
        // (FIX3: the decoy activity can take several seconds to
        // start on this phone, and the user may press Home in the
        // meantime - 3s was far too short, and the video shows the
        // decoy arriving after the grace had expired. A block is
        // only stale after 10 min, the same limit as sessions).
        // And abandon-clearing below only runs once decoyShowing is
        // true - a CalNyx event after that, with an old pending, is
        // a genuine manual open of a stale block.
        if (pkg == packageName) {
            if (pending != null && (lastForegroundPkg == packageName || pendingAgeMs() < STALE_SESSION_MS)) {
                Log.d("CalNyxAppLock", "decoy CalNyx, hiding overlay pending=$pending")
                decoyShowing = true
                hideBlockOverlay()
                setLastForeground(pkg)
                return
            }
            if (pending != null) {
                Log.d("CalNyxAppLock", "manual CalNyx open, clearing stale pending=$pending")
                try {
                    AppLockManager.clearPending(this)
                    AppLockManager.clearDecoyLaunch(this)
                    AppLockManager.clearDecoyActive(this)
                } catch (_: Exception) {
                }
            }
            decoyShowing = false
            hideBlockOverlay()
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
        // DIAG (v5): every state-change event the service sees.
        // This alone shows what Recents actually fires on this
        // phone (the Recents branch above needs "Recents" in
        // the class name, and the 23:02 log had none).
        Log.d("CalNyxAppLock", "EVENT pkg=$pkg cls=$clsName")
        if (pkg == "com.android.systemui") logWindowList()
        if (clsName.contains("Recents", ignoreCase = true)) {
            onRecentsDetected(pkg)
            return
        }

        // BYPASS FIX (uninstall): an uninstall confirmation is a
        // window of the package installer / Settings / Play Store.
        // Schedule a delayed scan of that window - do NOT return
        // here, Settings is a real app switch and must still end
        // a session below like any other app.
        if (pkg in installerPkgs) scheduleUninstallCheck()

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
                decoyShowing = false
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

        // STALE-PENDING FIX: leaving the decoy calculator for a real
        // app (Home, another app, notification target) abandons the
        // block. The pending app is NOT the app being switched to
        // here (that switch would be the unlock, handled by the
        // session-allow branch above), so clear it - otherwise the
        // next secret code in the normal calculator opens the old app.
        // DECOY-SHOWING FIX: only once the decoy was actually
        // showing. Before that, this same event is just task-switch
        // noise (e.g. the launcher window during block -> decoy)
        // and clearing here is what broke unlock in the first fix.
        if (decoyShowing && previous == packageName && pending != null && pkg != pending) {
            Log.d("CalNyxAppLock", "decoy abandoned, clearing pending=$pending to=$pkg")
            decoyShowing = false
            try {
                AppLockManager.clearPending(this)
                AppLockManager.clearDecoyLaunch(this)
                AppLockManager.clearDecoyActive(this)
            } catch (_: Exception) {
            }
            hideBlockOverlay()
        }

        if (!AppLockManager.isLocked(this, pkg)) {
            Log.d("CalNyxAppLock", "switch to unlocked app pkg=$pkg")
            return
        }

        // Locked, no session: block. There is no grace return any
        // more - every exit locks, so a locked app with no live
        // session always shows the decoy calculator.
        Log.d("CalNyxAppLock", "BLOCK pkg=$pkg previous=$previous session=$session")
        blockLockedApp(pkg)
    }

    override fun onDestroy() {
        if (instance == this) instance = null
        decoyShowing = false
        hideBlockOverlay()
        try {
            blockHandler.removeCallbacksAndMessages(null)
            uninstallHandler.removeCallbacksAndMessages(null)
            windowRescanPending = false
            recentsDismissScheduled = false
        } catch (_: Exception) {
        }
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onInterrupt() {}
}
