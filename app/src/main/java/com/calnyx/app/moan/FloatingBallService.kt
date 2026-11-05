// FILE: app/src/main/java/com/calnyx/app/moan/FloatingBallService.kt
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
package com.calnyx.app.moan

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
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.calnyx.app.R

class FloatingBallService : Service() {

    companion object {
        const val ACTION_START = "com.calnyx.app.moan.START_BALL"
        const val ACTION_STOP = "com.calnyx.app.moan.STOP_BALL"
        const val ACTION_UPDATE = "com.calnyx.app.moan.UPDATE_BALL"
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "moan_ball"

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
    }

    private var windowManager: WindowManager? = null
    private var ballView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private fun dpToPx(dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()

    // Clamp the ball fully inside the screen, for the given size.
    private fun clampPosition(p: WindowManager.LayoutParams, sizePx: Int) {
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        p.x = p.x.coerceIn(0, (screenW - sizePx).coerceAtLeast(0))
        p.y = p.y.coerceIn(0, (screenH - sizePx).coerceAtLeast(0))
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
        if (intent?.action == ACTION_UPDATE) {
            // In-place update only: resize / re-alpha the ball
            // that is already showing. If there is no ball yet,
            // create it. Never stop / recreate the service here.
            if (ballView != null) {
                applyPrefsToBall()
            } else {
                showBall()
            }
        } else {
            showBall()
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (savedX >= 0) savedX else screenW - sizePx - dpToPx(16)
            y = if (savedY >= 0) savedY else screenH / 2
        }
        clampPosition(p, sizePx)
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
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                    p.x = startX + dx
                    p.y = startY + dy
                    clampPosition(p, sizePx)
                    try {
                        wm.updateViewLayout(view, p)
                    } catch (_: Exception) {
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        // Tap: open ONLY the panel. The ball must
                        // NEVER open the app itself. If the panel
                        // cannot open, just toast - no fallback
                        // to CalNyx.
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
                    } else {
                        MoanControlPrefs.setBallPosition(this, p.x, p.y)
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
        clampPosition(p, sizePx)
        if (ballView == null) return
        try {
            windowManager?.updateViewLayout(view, p)
        } catch (_: Exception) {
        }
    }

    private fun removeBall() {
        try {
            val view = ballView
            if (view != null) windowManager?.removeView(view)
        } catch (_: Exception) {
        }
        ballView = null
        params = null
    }

    override fun onDestroy() {
        removeBall()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
