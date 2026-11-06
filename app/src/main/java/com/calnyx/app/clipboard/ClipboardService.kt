// FILE: app/src/main/java/com/calnyx/app/clipboard/ClipboardService.kt
// NEW FILE - Clipboard feature (2026-11-06).
// Target: app/src/main/java/com/calnyx/app/clipboard/ClipboardService.kt
// First package line: package com.calnyx.app.clipboard
// First class line: class ClipboardService : Service()
// Foreground service that listens for clipboard changes and saves
// every copy into Room. Three capture paths exist (see README):
//  (1) this listener while the service runs,
//  (2) checkNow() when the Clipboard screen opens,
//  (3) BootReceiver + MainActivity start this service.
// HONEST ANDROID LIMIT (do not remove this note): on Android 10+
// the system blocks BACKGROUND clipboard reads. If CalNyx was
// fully killed (swiped away / force-stopped / never opened since
// boot), copies made in that window are NOT caught live — they
// only appear the next time the user opens CalNyx, when checkNow()
// reads the current clip once. The existing AppLock
// AccessibilityService is a separate feature; this clipboard
// capture does NOT depend on it, and no new AccessibilityService
// is added here.
// Notification: a silent, low-importance, ongoing notification
// ("Clipboard running"). On Android 13+ POST_NOTIFICATIONS may
// be denied — startForeground is wrapped in try/catch so the
// service never crashes for that; the listener still runs.
package com.calnyx.app.clipboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
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

class ClipboardService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: ClipboardRepository
    private var clipboardManager: ClipboardManager? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        scope.launch { saveCurrentClip() }
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
            // above still runs while the process is alive.
        }
    }

    /** Reads the current primary clip once and saves it if new. */
    private suspend fun saveCurrentClip() {
        try {
            val manager = clipboardManager
                ?: getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return
            if (!manager.hasPrimaryClip()) return
            val clip: ClipData = manager.primaryClip ?: return
            if (clip.itemCount == 0) return
            val item = clip.getItemAt(0)
            val description = clip.description
            // Image first: a copied image has a Uri with an image MIME type.
            val uri = item.uri
            if (uri != null &&
                (description?.hasMimeType("image/*") == true ||
                    item.text == null)
            ) {
                repository.insertImage(uri)
                return
            }
            val text = item.coerceToText(applicationContext)?.toString() ?: return
            repository.insertText(text)
        } catch (_: SecurityException) {
            // Android 10+ background clipboard read blocked: silent.
            // checkNow() on the next screen open catches it then.
        } catch (_: Exception) {
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
                // Foreground-service start denied (e.g. app was in
                // background on some OEM builds): the screen's
                // checkNow() still covers copies when opened.
            }
        }

        /**
         * Reads the current clip ONCE (foreground, so Android 10+
         * allows it) and saves it if it is new. Called from the
         * Clipboard screen's LaunchedEffect. Safe to call even when
         * the service is not running; never throws.
         */
        fun checkNow(context: Context) {
            val appContext = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val repository = ClipboardRepository(appContext)
                    val manager =
                        appContext.getSystemService(Context.CLIPBOARD_SERVICE)
                            as? ClipboardManager ?: return@launch
                    if (!manager.hasPrimaryClip()) return@launch
                    val clip: ClipData = manager.primaryClip ?: return@launch
                    if (clip.itemCount == 0) return@launch
                    val item = clip.getItemAt(0)
                    val uri = item.uri
                    if (uri != null &&
                        (clip.description?.hasMimeType("image/*") == true ||
                            item.text == null)
                    ) {
                        repository.insertImage(uri)
                        return@launch
                    }
                    val text = item.coerceToText(appContext)?.toString() ?: return@launch
                    repository.insertText(text)
                } catch (_: SecurityException) {
                    // Background read blocked — silent, as documented.
                } catch (_: Exception) {
                }
            }
        }
    }
}
