// FILE: app/src/main/java/com/calnyx/app/clipboard/ClipboardService.kt
// REPLACE FILE - Clipboard fix (2026-11-20).
// Target: app/src/main/java/com/calnyx/app/clipboard/ClipboardService.kt
// First package line: package com.calnyx.app.clipboard
// First class line: class ClipboardService : Service()
//
// WHAT CHANGED vs the 2026-11-06 version (3 fixes):
// 1. ROOT CAUSE: the listener no longer tries to READ the clipboard
//    itself. On Android 10+ a background read returns null, so the
//    old saveCurrentClip() silently saved nothing - that was the
//    whole bug. Now the listener is only a SIGNAL: it tries one
//    read via ClipboardCapture.saveNow() (works when CalNyx happens
//    to be focused), and on BLOCKED it launches the transparent
//    ClipboardCaptureActivity, which becomes focused and reads.
//    The duplicated read code that used to live here AND in
//    checkNow() is gone - both call ClipboardCapture.saveNow().
// 2. checkNow() is unchanged in behaviour (foreground read on
//    screen open) but now delegates to the same saveNow(), so the
//    screen and the service can no longer double-save one copy:
//    dedup is persistent in ClipboardCapture, shared by all callers.
// 3. Nothing else changed: same channel ("clipboard", LOW), same
//    notification id 1001, same dataSync foreground type, same
//    START_STICKY, same prune-on-start. Manifest already declares
//    the service + FOREGROUND_SERVICE_DATA_SYNC - no new permission.
package com.calnyx.app.clipboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.calnyx.app.applock.AppLockService

class ClipboardService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: ClipboardRepository
    private var clipboardManager: ClipboardManager? = null

    // Signal only. The read happens in ClipboardCapture.saveNow()
    // when focused, or in ClipboardCaptureActivity when blocked.
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        scope.launch {
            val result = try {
                ClipboardCapture.saveNow(applicationContext)
            } catch (_: Exception) {
                SaveResult.BLOCKED
            }
            if (result == SaveResult.BLOCKED) {
                ClipboardCapture.tryCapture(applicationContext)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = ClipboardRepository(applicationContext)
        createChannel()
        startAsForeground()
        clipboardManager =
            getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipListener)
        // Prune on every service start, so old rows never linger
        // just because the screen was not opened.
        scope.launch {
            try {
                repository.pruneExpired()
            } catch (_: Exception) {
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: if the system kills the service, it is
        // recreated so clipboard listening resumes.
        return START_STICKY
    }

    override fun onDestroy() {
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Clipboard",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps clipboard auto-save running"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun startAsForeground() {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Clipboard running")
            .setContentText("Copied text and images are saved in CalNyx")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                @Suppress("DEPRECATION")
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            // Android 13+ POST_NOTIFICATIONS denied, or a device
            // restriction: never crash for this. The listener
            // above still runs while the process is alive, and
            // AppLockService (already running) re-triggers capture.
        }
    }

    companion object {
        private const val CHANNEL_ID = "clipboard"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, ClipboardService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                // Foreground-service start denied from the background
                // (Android 12+ BackgroundServiceStartNotAllowed, or
                // an OEM restriction). The capture activity - which
                // IS allowed to start from AppLockService - is the
                // path that still works then; it reads directly and
                // the service is restarted from the Clipboard screen.
            }
        }

        /**
         * Reads the current clip ONCE. Call from a focused context
         * (Clipboard screen open, CalNyx in front): Android 10+
         * allows the read there. Safe to call even when the service
         * is not running; never throws. One copy saved while CalNyx
         * was fully killed appears here, on next open.
         */
        fun checkNow(context: Context) {
            val appContext = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    ClipboardCapture.saveNow(appContext)
                } catch (_: Exception) {
                }
            }
        }
    }
}
