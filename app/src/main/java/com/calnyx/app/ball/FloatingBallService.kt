// SNAP + TUCK (2026-11-22): the ball never rests mid-screen any more.
// On drag release it glides (~260ms, decelerate) to the NEARER edge
// (left / right by centre ratio) and stays fully visible there. Push
// it past the edge it is docked on and it TUCKS: 2/3 hidden in the
// wall, 1/3 visible outside. A tap on the tucked ball only slides
// it back out to the edge - it never opens the panel / toolbar.
// Dragging a tucked ball inward undocks it. Docked edge + tucked
// flag are persisted in MoanControlPrefs, so a restart restores
// the same state. Lock / panel / recording / hide-show logic below
// is unchanged.
// LOCK-SCREEN FIX (2026-11-21): the floating ball must NOT show on the
// lock screen (FLAG_SHOW_WHEN_LOCKED removed from showBall). The user
// must unlock first. The ball still stays during screen recording while unlocked.
// FILE: app/src/main/java/com/calnyx/app/ball/FloatingBallService.kt
// CRASHFIX VERSION - white ball, NO border, shadow only.
// Size / opacity changes NO LONGER stop + restart the service.
// That restart (stopSelf, then startForegroundService right
// after) crashed the app when the user moved the size / opacity
// slider. Now the slider sends ACTION_UPDATE and the running
// service re-applies size + opacity to the existing ball view
// in place (updateViewLayout) - no destroy, no recreate, no
// foreground-service restart, so no crash, and the ball does
// not blink away and back.
// Ball: totally white fill, no stroke/border. Shadow via elevation
// (8dp) so it stays visible on white. Still clamped inside screen.
// Tap opens BallPanelActivity (transparent Compose Material 3
// panel). The old View-based panel is removed - Compose needs a
// real Activity, and only an Activity can request Camera
// permission for the flashlight. Size / opacity come from
// MoanControlPrefs. Tap = panel, drag = move.
package com.calnyx.app.ball

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.calnyx.app.spy.SpyScreenRecordService
import com.calnyx.app.R
import com.calnyx.app.moan.MoanControlPrefs
import com.calnyx.app.hardcore.OrientationGuard

class FloatingBallService : Service() {

    companion object {
        const val ACTION_START = "com.calnyx.app.moan.START_BALL"
        const val ACTION_STOP = "com.calnyx.app.moan.STOP_BALL"
        const val ACTION_UPDATE = "com.calnyx.app.moan.UPDATE_BALL"
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "moan_ball"
        private const val SNAP_ANIM_MS = 260L

        fun canDrawOverlays(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }

        fun start(context: Context) {
            if (!canDrawOverlays(context)) return
            val intent = Intent(context, FloatingBallService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingBallService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        // Re-apply size / opacity to the ball that is already
        // showing, WITHOUT stopping or restarting the service.
        // Uses plain startService (not startForegroundService):
        // the service is already running, so this only delivers
        // the intent to onStartCommand - no foreground-service
        // restart, no WindowManager destroy / recreate race.
        fun update(context: Context) {
            val intent = Intent(context, FloatingBallService::class.java).apply {
                action = ACTION_UPDATE
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        // HIDE / SHOW (2026-11-20): the ball is hidden instead
        // of FLAG_SECURE (see showBall) - FLAG_SECURE made the
        // system refuse EVERY screenshot on this phone ("Cannot
        // capture screenshot. The page contains personal
        // information."), even on the plain home screen. So the
        // ball is removed from the screen when it must not be
        // captured, and put back after:
        //   hide(context, reshowAfterMs): reshowAfterMs > 0
        //   re-shows the ball after that delay (the Screenshot
        //   button: hide, shoot, ball comes back by itself);
        //   reshowAfterMs = 0 stays hidden until show().
        //   (The screen recorder NO LONGER hides the ball -
        //   2026-11-20, the tools are needed during recording.)
        // Both are no-ops when the ball is off in settings.
        private const val ACTION_HIDE = "com.calnyx.app.moan.HIDE_BALL"
        private const val ACTION_SHOW = "com.calnyx.app.moan.SHOW_BALL"
        private const val EXTRA_RESHOW_AFTER_MS = "reshow_after_ms"

        fun hide(context: Context, reshowAfterMs: Long = 0L) {
            if (!MoanControlPrefs.isBallEnabled(context)) return
            val intent = Intent(context, FloatingBallService::class.java).apply {
                action = ACTION_HIDE
                putExtra(EXTRA_RESHOW_AFTER_MS, reshowAfterMs)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        fun show(context: Context) {
            if (!MoanControlPrefs.isBallEnabled(context)) return
            val intent = Intent(context, FloatingBallService::class.java).apply {
                action = ACTION_SHOW
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        // SCREEN RECORD (2026-11-20): bring the ball above the
        // draw overlay, without restarting the service. The
        // recorder calls this after adding its overlay and
        // whenever a tool is picked, so the ball stays tappable
        // while drawing. Plain startService: the service is
        // already running, so this only delivers the intent to
        // onStartCommand.
        private const val ACTION_BRING_TO_FRONT = "com.calnyx.app.moan.BRING_BALL_TO_FRONT"

        fun bringToFront(context: Context) {
            val intent = Intent(context, FloatingBallService::class.java).apply {
                action = ACTION_BRING_TO_FRONT
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }
    }

    private var windowManager: WindowManager? = null
    // SNAP + TUCK state: the running glide animation (cancelled on
    // the next touch down / remove) and whether the ball is tucked.
    private var snapAnimator: ValueAnimator? = null
    private var isTucked: Boolean = false
    private var ballView: View? = null
    private var params: WindowManager.LayoutParams? = null

    // Delayed re-show for hide(reshowAfterMs). Cancelled on
    // show() and onDestroy().
    private val mainHandler = Handler(Looper.getMainLooper())
    private var reshowRunnable: Runnable? = null

    private fun dpToPx(dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()

    // SNAP + TUCK: docked = fully visible at the edge, tucked =
    // 2/3 hidden in the wall, 1/3 visible outside.
    private fun dockedX(leftSide: Boolean, sizePx: Int): Int {
        val screenW = resources.displayMetrics.widthPixels
        return if (leftSide) 0 else (screenW - sizePx).coerceAtLeast(0)
    }

    private fun tuckedX(leftSide: Boolean, sizePx: Int): Int {
        val screenW = resources.displayMetrics.widthPixels
        return if (leftSide) -(sizePx * 2 / 3) else screenW - sizePx / 3
    }

    private fun isLeftSide(x: Int, sizePx: Int): Boolean {
        val screenW = resources.displayMetrics.widthPixels
        return x + sizePx / 2 < screenW / 2
    }

    // Clamp while DRAGGING: the ball may sink 2/3 into the wall
    // (that is how a tuck starts), but never further.
    private fun clampDragPosition(p: WindowManager.LayoutParams, sizePx: Int) {
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        p.x = p.x.coerceIn(-(sizePx * 2 / 3), (screenW - sizePx / 3).coerceAtLeast(0))
        p.y = p.y.coerceIn(0, (screenH - sizePx).coerceAtLeast(0))
    }

    // Clamp the ball fully inside the screen, for the given size.
    // Used on start / size change when the ball is NOT tucked.
    private fun clampPosition(p: WindowManager.LayoutParams, sizePx: Int) {
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        p.x = p.x.coerceIn(0, (screenW - sizePx).coerceAtLeast(0))
        p.y = p.y.coerceIn(0, (screenH - sizePx).coerceAtLeast(0))
    }

    private fun cancelSnapAnimation() {
        try {
            snapAnimator?.cancel()
        } catch (_: Exception) {
        }
        snapAnimator = null
    }

    // Light snap animation: glide to targetX in ~260ms with a
    // decelerate ease, then run onDone (which persists the
    // settled position). No bounce, no overshoot - calm only.
    private fun animateBallTo(targetX: Int, tucked: Boolean, onDone: (() -> Unit)? = null) {
        val view = ballView ?: run { onDone?.invoke(); return }
        val p = params ?: run { onDone?.invoke(); return }
        val wm = windowManager ?: run { onDone?.invoke(); return }
        cancelSnapAnimation()
        val fromX = p.x
        if (fromX == targetX) {
            isTucked = tucked
            onDone?.invoke()
            return
        }
        val animator = ValueAnimator.ofInt(fromX, targetX).apply {
            duration = SNAP_ANIM_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                try {
                    p.x = it.animatedValue as Int
                    wm.updateViewLayout(view, p)
                } catch (_: Exception) {
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    isTucked = tucked
                    snapAnimator = null
                    onDone?.invoke()
                }

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    snapAnimator = null
                }
            })
        }
        snapAnimator = animator
        animator.start()
    }

    // Settle after a drag release: pushed past the edge -> tuck;
    // otherwise snap to the nearer edge, fully visible. Persists
    // the docked edge + tucked flag once the glide finishes.
    private fun settleAfterDrag(sizePx: Int) {
        val p = params ?: return
        val screenW = resources.displayMetrics.widthPixels
        val leftSide = isLeftSide(p.x, sizePx)
        val docked = dockedX(leftSide, sizePx)
        if (p.x < 0 || p.x > screenW - sizePx) {
            val target = tuckedX(leftSide, sizePx)
            animateBallTo(target, true) {
                MoanControlPrefs.setBallPosition(this, docked, p.y)
                MoanControlPrefs.setBallTucked(this, true)
            }
        } else {
            animateBallTo(docked, false) {
                MoanControlPrefs.setBallPosition(this, docked, p.y)
                MoanControlPrefs.setBallTucked(this, false)
            }
        }
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Ball",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            try {
                nm.createNotificationChannel(channel)
            } catch (_: Exception) {
            }
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pending = launchIntent?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("CalNyx")
            .setContentText("Floating Ball is on")
            .setOngoing(true)
        if (pending != null) builder.setContentIntent(pending)
        return builder.build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Orientation guard: re-apply the saved rotation mode
        // (up / down) if the system reset it. Same package, no
        // import needed. No-op in Auto mode.
        try {
            OrientationGuard.refresh(this)
        } catch (_: Exception) {
        }
        if (intent?.action == ACTION_STOP) {
            removeBall()
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_BRING_TO_FRONT) {
            // Re-add the ball above the draw overlay. No
            // re-foreground: the service is already running.
            bringBallToFront()
            return START_STICKY
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIF_ID,
                    createNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIF_ID, createNotification())
            }
        } catch (_: Exception) {
            try {
                startForeground(NOTIF_ID, createNotification())
            } catch (_: Exception) {
            }
        }
        when (intent?.action) {
            ACTION_HIDE -> {
                // Remove the ball now. If a re-show delay was
                // given, put it back after that delay (only if
                // the ball is still on in settings).
                removeBall()
                reshowRunnable?.let { mainHandler.removeCallbacks(it) }
                reshowRunnable = null
                val reshowAfter = intent.getLongExtra(EXTRA_RESHOW_AFTER_MS, 0L)
                if (reshowAfter > 0) {
                    val r = Runnable {
                        if (MoanControlPrefs.isBallEnabled(this)) showBall()
                    }
                    reshowRunnable = r
                    mainHandler.postDelayed(r, reshowAfter)
                }
            }
            ACTION_SHOW -> {
                reshowRunnable?.let { mainHandler.removeCallbacks(it) }
                reshowRunnable = null
                if (MoanControlPrefs.isBallEnabled(this)) showBall()
            }
            ACTION_UPDATE -> {
                // In-place update only: resize / re-alpha the ball
                // that is already showing. If there is no ball yet,
                // create it. Never stop / recreate the service here.
                if (ballView != null) {
                    applyPrefsToBall()
                } else {
                    showBall()
                }
            }
            else -> showBall()
        }
        return START_STICKY
    }

    private fun showBall() {
        if (!canDrawOverlays(this)) return
        if (ballView != null) {
            applyPrefsToBall()
            return
        }
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val sizeDp = MoanControlPrefs.getBallSizeDp(this)
        val sizePx = dpToPx(sizeDp)
        val view = FrameLayout(this)
        // Totally white, NO border. Shadow only, via elevation.
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFFFFFFFF.toInt())
        }
        view.background = bg
        view.elevation = dpToPx(8).toFloat()
        view.outlineProvider = ViewOutlineProvider.BACKGROUND
        view.clipToOutline = false

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val savedX = MoanControlPrefs.getBallX(this)
        val savedY = MoanControlPrefs.getBallY(this)
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val p = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            type,
            // NO FLAG_SECURE here (2026-11-20): it made the
            // system refuse EVERY screenshot on this phone
            // ("Cannot capture screenshot. The page contains
            // personal information."), even on the plain home
            // screen. The ball is hidden with hide() instead
            // when it must not be captured.
            // LOCK-SCREEN FIX (2026-11-21): the ball must NOT
            // show on the lock screen - no FLAG_SHOW_WHEN_LOCKED.
            // The user must unlock first. The ball still stays
            // during screen recording while unlocked.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (savedX >= 0) savedX else screenW - sizePx - dpToPx(16)
            y = if (savedY >= 0) savedY else screenH / 2
        }
        // SNAP + TUCK: restore the tucked state - the saved X is
        // always the docked edge, the flag says if it was tucked.
        isTucked = try {
            MoanControlPrefs.isBallTucked(this)
        } catch (_: Exception) {
            false
        }
        if (isTucked) {
            p.x = tuckedX(isLeftSide(p.x, sizePx), sizePx)
            p.y = p.y.coerceIn(0, (screenH - sizePx).coerceAtLeast(0))
        } else {
            clampPosition(p, sizePx)
        }
        params = p
        applyPrefsToBall(view, p)
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    cancelSnapAnimation()
                    downX = event.rawX
                    downY = event.rawY
                    startX = p.x
                    startY = p.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) {
                        moved = true
                        // Dragging a tucked ball inward undocks it.
                        isTucked = false
                    }
                    p.x = startX + dx
                    p.y = startY + dy
                    clampDragPosition(p, sizePx)
                    try {
                        wm.updateViewLayout(view, p)
                    } catch (_: Exception) {
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        if (isTucked) {
                            // TUCK: a tap on the tucked ball only
                            // slides it back out to the edge - it
                            // must NOT open the panel / toolbar.
                            val leftSide = isLeftSide(p.x, sizePx)
                            val docked = dockedX(leftSide, sizePx)
                            animateBallTo(docked, false) {
                                MoanControlPrefs.setBallPosition(this, docked, p.y)
                                MoanControlPrefs.setBallTucked(this, false)
                            }
                        } else if (SpyScreenRecordService.isRecording) {
                            // While a screen recording runs, the ball
                            // opens the recording toolbar, not the
                            // panel. Otherwise: open ONLY the panel.
                            // The ball must NEVER open the app itself.
                            // If the panel cannot open, just toast -
                            // no fallback to CalNyx.
                            try {
                                SpyScreenRecordService.openToolbar(this@FloatingBallService)
                            } catch (_: Exception) {
                            }
                        } else {
                            try {
                                val panel = Intent(
                                    this@FloatingBallService,
                                    BallPanelActivity::class.java
                                ).apply {
                                    addFlags(
                                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    )
                                }
                                startActivity(panel)
                            } catch (_: Exception) {
                                try {
                                    Toast.makeText(
                                        this@FloatingBallService,
                                        "Panel could not open.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } catch (_: Exception) {
                                }
                            }
                        }
                    } else {
                        // Drag released: snap to the nearer edge,
                        // or tuck if pushed past the edge.
                        settleAfterDrag(sizePx)
                    }
                    true
                }
                else -> false
            }
        }
        ballView = view
        try {
            wm.addView(view, p)
        } catch (_: Exception) {
            ballView = null
        }
    }

    private fun applyPrefsToBall() {
        val view = ballView ?: return
        val p = params ?: return
        applyPrefsToBall(view, p)
    }

    private fun applyPrefsToBall(view: View, p: WindowManager.LayoutParams) {
        val sizePx = dpToPx(MoanControlPrefs.getBallSizeDp(this))
        p.width = sizePx
        p.height = sizePx
        view.alpha = MoanControlPrefs.getBallOpacity(this) / 100f
        // Size changed while tucked: keep it tucked, recomputed
        // for the new size. Otherwise stay fully inside.
        if (isTucked) {
            p.x = tuckedX(isLeftSide(p.x, sizePx), sizePx)
            p.y = p.y.coerceIn(0, (resources.displayMetrics.heightPixels - sizePx).coerceAtLeast(0))
        } else {
            clampPosition(p, sizePx)
        }
        if (ballView == null) return
        try {
            windowManager?.updateViewLayout(view, p)
        } catch (_: Exception) {
        }
    }

    private fun removeBall() {
        cancelSnapAnimation()
        try {
            val view = ballView
            if (view != null) windowManager?.removeView(view)
        } catch (_: Exception) {
        }
        ballView = null
        params = null
    }

    // SCREEN RECORD (2026-11-20): re-add the ball above
    // everything else. The draw overlay is added after the
    // ball, so without this the ball would sit underneath it
    // and could not be tapped while drawing. A fresh addView
    // always lands on top, at the saved position.
    private fun bringBallToFront() {
        removeBall()
        showBall()
    }

    override fun onDestroy() {
        reshowRunnable?.let { mainHandler.removeCallbacks(it) }
        reshowRunnable = null
        removeBall()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
