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
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
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

    // DECOY-SHOWING FIX: true only AFTER a CalNyx decoy event has
    // actually been seen in front. Abandon-clearing (switch away,
    // Recents, manual open) must NEVER run before that - the events
    // between a block and the decoy appearing are task-switch noise
    // from the system, not the user leaving. See the header note.
    private var decoyShowing = false

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
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = 0
            notificationTimeout = 0
        }
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
        if (clsName.contains("Recents", ignoreCase = true)) {
            if (session != null) {
                Log.d("CalNyxAppLock", "recents, ending session $session")
                try {
                    AppLockManager.endUnlockedSession(this)
                } catch (_: Exception) {
                }
            }
            // STALE-PENDING FIX: minimising the decoy to Recents
            // abandons the block. Clear it, or the next secret code
            // in the normal calculator would open the old app.
            // DECOY-SHOWING FIX: only once the decoy was actually
            // showing - a Recents window during the block -> decoy
            // task switch is system noise, not the user leaving.
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

    override fun onDestroy() {
        if (instance == this) instance = null
        decoyShowing = false
        hideBlockOverlay()
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onInterrupt() {}
}
