// FILE: app/src/main/java/com/calnyx/app/hardcore/OrientationService.kt
// NEW FILE - orientation lock that works OUTSIDE the app.
//
// The old way (OrientationGuard + system setting only) did not
// stick: on this realme the system setting is reset ~2s after
// leaving the app, and the guard only ran while a service was
// alive. This service enforces the mode a second, stronger way:
// a 0x0 invisible overlay view whose LayoutParams.screenOrientation
// is set. The WindowManager applies that orientation to the
// WHOLE display, so it holds in every app, on Home, and on the
// lock screen, until the mode is changed back to Auto in CalNyx.
// That is the same mechanism the reference app used.
//
// It also writes the system setting (ACCELEROMETER_ROTATION /
// USER_ROTATION) when WRITE_SETTINGS is granted, so the mode
// survives even if the overlay is ever removed, and it restores
// the saved mode on service start and on boot (see
// OrientationBootReceiver).
//
// Modes (same strings as MoanControlPrefs): "up" (0 deg),
// "down" (180 deg), "auto", "normal" (2026-11-22).
// Only a locked mode (up / down) keeps this service running;
// in Auto and Normal the service stops itself and removes
// the overlay.
// NORMAL - the user's definition (2026-11-22): the phone's
// own default. The screen sits at 0 deg and STAYS there,
// because the system setting is written to auto-rotate OFF +
// USER_ROTATION 0. A video player (or any app) can still
// force landscape while it plays, because an app's requested
// orientation overrides the system setting - that is the
// whole difference from Auto, which rotates everywhere
// with the sensor.
// The OLD Normal (leave the system setting untouched) was
// WRONG: after Down, removing the overlay left USER_ROTATION
// at 180 deg, so the screen stayed upside down and the user
// saw "Normal = 180 deg". The setting MUST be written to 0.
//
// Permissions: WRITE_SETTINGS for the system-setting write
// (optional - the overlay alone enforces the mode), and
// SYSTEM_ALERT_WINDOW for the overlay (required for the
// outside-the-app enforcement). If neither is granted, apply()
// still saves the mode but returns false, so the UI can tell
// the user what to grant.
package com.calnyx.app.hardcore

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.calnyx.app.R
import com.calnyx.app.moan.MoanControlPrefs

class OrientationService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var currentMode: String = "auto"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundNotification()
        // Restore the saved mode (e.g. after the system killed
        // the service, or after reboot via the BootReceiver).
        val saved = MoanControlPrefs.getOrientationMode(this)
        applyOrientation(saved)
        isServiceActive = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_ORIENTATION) {
            // Stop only: remove the overlay and die. Do NOT
            // re-save a mode here - the caller (apply() in the
            // companion) already saved the mode it wants ("auto"
            // or "normal"). Saving "auto" here used to overwrite
            // a "normal" that was just saved.
            removeOverlay()
            stopSelf()
            return START_NOT_STICKY
        }
        if (action == ACTION_SET_ORIENTATION) {
            val mode = intent.getStringExtra(EXTRA_MODE) ?: "auto"
            applyOrientation(mode)
        } else {
            // Restarted by the system (START_STICKY) or started
            // without an action: restore the saved mode.
            val saved = MoanControlPrefs.getOrientationMode(this)
            applyOrientation(saved)
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Screen Orientation",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps the selected screen orientation fixed across apps"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            try {
                manager?.createNotificationChannel(channel)
            } catch (_: Exception) {
            }
        }
        startForeground(NOTIF_ID, buildNotification(currentMode))
    }

    private fun buildNotification(mode: String): Notification {
        val label = when (mode) {
            "up" -> "Up (0°)"
            "down" -> "Down (180°)"
            "normal" -> "Normal"
            else -> "Auto"
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
            .setContentText("Orientation: $label")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
        if (pending != null) builder.setContentIntent(pending)
        return builder.build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        try {
            manager.notify(NOTIF_ID, buildNotification(currentMode))
        } catch (_: Exception) {
        }
    }

    // The core enforcement. Writes the system setting (if
    // allowed) AND adds / updates / removes the 0x0 overlay
    // whose screenOrientation forces the whole display.
    private fun applyOrientation(mode: String) {
        currentMode = mode
        activeOrientation = mode
        // Persist so it survives app restarts and reboots.
        MoanControlPrefs.setOrientationMode(this, mode)

        // 1. System setting (needs WRITE_SETTINGS).
        // Normal writes it too: auto-rotate OFF + USER_ROTATION
        // 0, or the screen keeps whatever rotation the last
        // locked mode left behind (the "Normal = 180 deg" bug).
        writeSystemSetting(mode)

        // 2. Overlay enforcement (needs SYSTEM_ALERT_WINDOW).
        if (mode == "auto" || mode == "normal") {
            removeOverlay()
        } else if (Settings.canDrawOverlays(this)) {
            val screenOrientation = when (mode) {
                "down" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val params = WindowManager.LayoutParams(
                0,
                0,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                this.screenOrientation = screenOrientation
            }
            if (overlayView == null) {
                overlayView = View(this)
                try {
                    windowManager?.addView(overlayView, params)
                } catch (_: Exception) {
                    overlayView = null
                }
            } else {
                try {
                    windowManager?.updateViewLayout(overlayView, params)
                } catch (_: Exception) {
                }
            }
        }
        updateNotification()
        // Auto / Normal need no enforcement: stop the service
        // instead of lingering with a notification. Locked
        // modes (up / down) keep it running for the overlay.
        if (mode == "auto" || mode == "normal") {
            stopSelf()
        }
    }

    private fun writeSystemSetting(mode: String) {
        try {
            if (!Settings.System.canWrite(this)) return
            val resolver = contentResolver
            when (mode) {
                "up" -> {
                    Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                    Settings.System.putInt(resolver, Settings.System.USER_ROTATION, 0)
                }
                "down" -> {
                    Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                    Settings.System.putInt(resolver, Settings.System.USER_ROTATION, 2)
                }
                "normal" -> {
                    // NORMAL = the phone's own default: 0 deg,
                    // auto-rotate OFF. A video player can still
                    // force landscape - an app's requested
                    // orientation overrides this setting.
                    Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                    Settings.System.putInt(resolver, Settings.System.USER_ROTATION, 0)
                }
                else -> {
                    Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun removeOverlay() {
        try {
            val view = overlayView
            if (view != null) windowManager?.removeView(view)
        } catch (_: Exception) {
        }
        overlayView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceActive = false
        removeOverlay()
    }

    companion object {
        const val ACTION_SET_ORIENTATION = "com.calnyx.app.moan.ACTION_SET_ORIENTATION"
        const val ACTION_STOP_ORIENTATION = "com.calnyx.app.moan.ACTION_STOP_ORIENTATION"
        const val EXTRA_MODE = "extra_orientation_mode"
        private const val NOTIF_ID = 1002
        private const val CHANNEL_ID = "orientation_lock"

        var isServiceActive: Boolean = false
            private set

        var activeOrientation: String = "auto"
            private set

        fun getSavedMode(context: Context): String =
            MoanControlPrefs.getOrientationMode(context)

        // The single entry point for the UI and the BootReceiver.
        // Saves the mode FIRST, writes the system setting if
        // WRITE_SETTINGS is granted, and starts (locked mode) or
        // stops (auto) the overlay service if overlay permission
        // is granted. Returns true if the mode is actually being
        // enforced (system setting written and/or overlay
        // started), false if neither permission is available -
        // the mode is still saved, but nothing enforces it yet.
        fun apply(context: Context, mode: String): Boolean {
            // NORMAL (2026-11-22): save it, write the system
            // setting to 0 deg + auto-rotate OFF (this is what
            // puts the screen back to 0 after Down - removing
            // the overlay alone left it at 180), drop the
            // overlay, stop the service. The setting write is
            // best-effort (needs WRITE_SETTINGS); the overlay
            // stop cannot fail, so always return true, or the
            // panel would show a false "allow permission"
            // toast on the 4th tap.
            if (mode == "normal") {
                MoanControlPrefs.setOrientationMode(context, "normal")
                try {
                    if (Settings.System.canWrite(context)) {
                        val resolver = context.contentResolver
                        Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                        Settings.System.putInt(resolver, Settings.System.USER_ROTATION, 0)
                    }
                } catch (_: Exception) {
                }
                try {
                    context.stopService(
                        Intent(context, OrientationService::class.java)
                    )
                } catch (_: Exception) {
                }
                return true
            }
            MoanControlPrefs.setOrientationMode(context, mode)
            var enforced = false
            // 1. System setting.
            try {
                if (Settings.System.canWrite(context)) {
                    val resolver = context.contentResolver
                    when (mode) {
                        "up" -> {
                            Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                            Settings.System.putInt(resolver, Settings.System.USER_ROTATION, 0)
                        }
                        "down" -> {
                            Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                            Settings.System.putInt(resolver, Settings.System.USER_ROTATION, 2)
                        }
                        else -> {
                            Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1)
                        }
                    }
                    enforced = true
                }
            } catch (_: Exception) {
            }
            // 2. Overlay service (the outside-the-app enforcement).
            if (mode == "auto") {
                stop(context)
            } else if (Settings.canDrawOverlays(context)) {
                val intent = Intent(context, OrientationService::class.java).apply {
                    action = ACTION_SET_ORIENTATION
                    putExtra(EXTRA_MODE, mode)
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                    enforced = true
                } catch (_: Exception) {
                }
            }
            return enforced
        }

        fun stop(context: Context) {
            val intent = Intent(context, OrientationService::class.java).apply {
                action = ACTION_STOP_ORIENTATION
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }
    }
}
