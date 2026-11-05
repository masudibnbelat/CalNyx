// FILE: app/src/main/java/com/calnyx/app/BlueFilm/BlueFilmService.kt
// NEW FILE - Blue Film secret screen recorder (foreground
// service, MediaProjection + MediaRecorder).
//
// How it works:
//  - The Blue Film page asks Android for screen-capture
//    permission once (the system dialog). The result is handed
//    to this service, which starts a foreground service and
//    records the screen through a VirtualDisplay into a
//    MediaRecorder.
//  - Because it is a foreground service, recording KEEPS GOING
//    when the app is closed from the screen and when the screen
//    is locked. Stopping is done from the Blue Film page or from
//    the service notification's Stop action.
//  - The raw recording goes to a temp .mp4 in private storage.
//    On stop it is AES-encrypted into the Blue Film folder
//    (.bfenc) and the temp file is deleted, so nothing playable
//    is ever left on the phone. No internet is used at all.
//  - The notification is kept minimal and generic ("CalNyx /
//    Running", low priority, no status-bar icon). Note: Android
//    itself still shows its own small screen-recording chip in
//    the status bar while a recording runs - that is the OS, no
//    app can hide it.
//  - Video only, no microphone audio, so no mic permission is
//    needed. Resolution is the screen size capped at 1080 on
//    the long edge, 30 fps, ~5 Mbps.
package com.calnyx.app.BlueFilm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File

class BlueFilmService : Service() {

    companion object {
        const val ACTION_START = "com.calnyx.app.bluefilm.START"
        const val ACTION_STOP = "com.calnyx.app.bluefilm.STOP"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA = "data"
        private const val NOTIF_ID = 2001
        private const val CHANNEL_ID = "bluefilm_rec"

        @Volatile
        var isRecording: Boolean = false
            private set

        @Volatile
        var startedAtMillis: Long = 0L
            private set

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, BlueFilmService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BlueFilmService::class.java).apply {
                action = ACTION_STOP
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var tmpFile: File? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            // The projection died (permission revoked, etc.).
            stopRecording()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }
                if (data != null) startRecording(resultCode, data) else stopSelf()
            }
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording(resultCode: Int, data: Intent) {
        if (isRecording) return
        // Foreground FIRST, before the heavier setup, so the
        // system never kills the service for being late.
        startForeground(NOTIF_ID, buildNotification())
        try {
            val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as MediaProjectionManager
            val projection = pm.getMediaProjection(resultCode, data)
                ?: run { stopSelf(); return }
            mediaProjection = projection
            projection.registerCallback(
                projectionCallback,
                Handler(Looper.getMainLooper())
            )

            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getMetrics(metrics)
            val scale = if (maxOf(metrics.widthPixels, metrics.heightPixels) > 1080) {
                1080f / maxOf(metrics.widthPixels, metrics.heightPixels)
            } else {
                1f
            }
            val vw = (metrics.widthPixels * scale).toInt()
            val vh = (metrics.heightPixels * scale).toInt()

            tmpFile = File(
                filesDir,
                "bluefilm/.tmp_${System.currentTimeMillis()}.mp4"
            ).apply { parentFile?.mkdirs() }

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder = recorder
            recorder.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(vw, vh)
                setVideoFrameRate(30)
                setVideoEncodingBitRate(5_000_000)
                setOutputFile(tmpFile!!.absolutePath)
                prepare()
            }
            virtualDisplay = projection.createVirtualDisplay(
                "BlueFilm",
                vw,
                vh,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.surface,
                null,
                null
            )
            recorder.start()
            isRecording = true
            startedAtMillis = System.currentTimeMillis()
        } catch (_: Exception) {
            cleanup()
            stopSelf()
        }
    }

    private fun stopRecording() {
        if (!isRecording) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        try {
            try {
                mediaRecorder?.stop()
            } catch (_: Exception) {
            }
            try {
                mediaRecorder?.reset()
            } catch (_: Exception) {
            }
            try {
                mediaRecorder?.release()
            } catch (_: Exception) {
            }
            mediaRecorder = null
            try {
                virtualDisplay?.release()
            } catch (_: Exception) {
            }
            virtualDisplay = null
            try {
                mediaProjection?.unregisterCallback(projectionCallback)
            } catch (_: Exception) {
            }
            try {
                mediaProjection?.stop()
            } catch (_: Exception) {
            }
            mediaProjection = null
            // Encrypt the raw recording into the Blue Film folder,
            // then delete the playable temp file.
            val tmp = tmpFile
            if (tmp != null && tmp.exists() && tmp.length() > 0) {
                try {
                    val dest = BlueFilmStore.newVideoFile(this)
                    BlueFilmCrypto.encryptFile(this, tmp, dest)
                } catch (_: Exception) {
                }
                tmp.delete()
            }
            tmpFile = null
        } catch (_: Exception) {
        } finally {
            isRecording = false
            startedAtMillis = 0L
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun cleanup() {
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {
        }
        mediaRecorder = null
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null
        try {
            mediaProjection?.unregisterCallback(projectionCallback)
        } catch (_: Exception) {
        }
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        mediaProjection = null
        tmpFile?.delete()
        tmpFile = null
        isRecording = false
        startedAtMillis = 0L
    }

    override fun onDestroy() {
        if (isRecording) cleanup()
        super.onDestroy()
    }

    // Minimal, generic notification: low priority, no badge, no
    // status-bar icon, no "Recording" wording. The Stop action
    // lets the user stop from the shade when the app is closed.
    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CalNyx",
                NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false) }
            nm.createNotificationChannel(channel)
        }
        val stopIntent = Intent(this, BlueFilmService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle("CalNyx")
            .setContentText("Running")
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(0, "Stop", stopPending)
            .build()
    }
}
