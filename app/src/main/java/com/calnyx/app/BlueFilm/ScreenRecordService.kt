// FILE: app/src/main/java/com/calnyx/app/BlueFilm/ScreenRecordService.kt
// NEW FILE - screen recording with on-screen marks (2026-11-20).
//
// Same home as the camera recorder (BlueFilmService), but this
// one records the SCREEN, not the camera:
//  - MediaProjection: the system asks once per recording, with
//    its own dialog (Android's rule - it cannot be skipped, and
//    the user can also stop from the system's own recording
//    chip). The grant token arrives from BlueFilmScreen.
//  - Video + optional mic audio (AAC 128 kbps, same as the
//    camera), 720p / 1080p from the same Settings. Files save
//    to the same Blue Film folders, "SR_" prefix, plain .mp4.
//  - While recording, a transparent full-screen overlay shows
//    the marks (ScreenDrawView: pen / circle / rectangle, red,
//    plus emoji stickers). The overlay is NOT touchable by
//    default, so the phone works normally under it - a video
//    playing underneath keeps playing, and no tap reaches it.
//    Picking a tool in the toolbar makes it touchable until
//    the tool is picked again, cleared, or the ball is tapped:
//    while a tool is active the overlay eats EVERY touch, so
//    nothing underneath reacts - only the mark lands.
//  - The overlay is a View, so it is created on the MAIN
//    thread (the recorder itself runs on a background thread).
//    Adding it from the background thread could swallow
//    touches - that was the pen not drawing.
//  - The floating ball STAYS during recording and opens the
//    recording toolbar (ScreenRecordToolbarActivity) while a
//    recording runs. If the ball is OFF, a small white mini
//    ball appears instead, same job. The ball is brought above
//    the draw overlay so it stays tappable while drawing.
//  - Honest limits: the ball / mini ball / marks are overlays,
//    so they DO appear in the recorded video - that is the
//    price of keeping them tappable while recording. And the
//    system dialog appears every time a recording starts.
package com.calnyx.app.BlueFilm

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Size
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.calnyx.app.moan.FloatingBallService
import com.calnyx.app.moan.MoanControlPrefs
import java.io.File

class ScreenRecordService : Service() {

    companion object {
        const val ACTION_START = "com.calnyx.app.bluefilm.SCREEN_START"
        const val ACTION_STOP = "com.calnyx.app.bluefilm.SCREEN_STOP"
        private const val ACTION_SET_TOOL = "com.calnyx.app.bluefilm.SCREEN_SET_TOOL"
        private const val ACTION_CLEAR_MARKS = "com.calnyx.app.bluefilm.SCREEN_CLEAR_MARKS"
        private const val ACTION_SET_DRAW_MODE = "com.calnyx.app.bluefilm.SCREEN_SET_DRAW_MODE"
        private const val ACTION_SET_EMOJI = "com.calnyx.app.bluefilm.SCREEN_SET_EMOJI"
        private const val PREFS = "bluefilm_prefs"
        private const val KEY_MODE = "record_mode"
        private const val NOTIF_ID = 2002
        private const val CHANNEL_ID = "bluefilm_screen_rec"

        @Volatile
        var isRecording: Boolean = false
            private set

        @Volatile
        var isStarting: Boolean = false
            private set

        @Volatile
        var startedAtMillis: Long = 0L
            private set

        @Volatile
        var currentTool: DrawTool = DrawTool.NONE
            private set

        // The emoji picked in the toolbar, stamped by the EMOJI
        // tool. Kept while the recording runs.
        @Volatile
        var currentEmoji: String = "⭐"
            private set

        // Recording mode: "camera" (default) or "screen".
        fun savedMode(context: Context): String {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_MODE, "camera") ?: "camera"
        }

        fun saveMode(context: Context, mode: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_MODE, mode).apply()
        }

        fun start(context: Context, resultCode: Int, data: Intent, micOn: Boolean, quality: Int) {
            val intent = Intent(context, ScreenRecordService::class.java).apply {
                action = ACTION_START
                putExtra("resultCode", resultCode)
                putExtra("resultData", data)
                putExtra("micOn", micOn)
                putExtra("quality", quality)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenRecordService::class.java).apply {
                action = ACTION_STOP
            }
            ContextCompat.startForegroundService(context, intent)
        }

        // Pick a drawing tool (or NONE to turn drawing off).
        // Updates the shared state at once, then tells the
        // running service to switch the overlay's touch mode.
        fun setTool(context: Context, tool: DrawTool) {
            currentTool = tool
            val intent = Intent(context, ScreenRecordService::class.java).apply {
                action = ACTION_SET_TOOL
                putExtra("tool", tool.name)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        fun clearMarks(context: Context) {
            val intent = Intent(context, ScreenRecordService::class.java).apply {
                action = ACTION_CLEAR_MARKS
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        // Remember the picked emoji for the stamp tool.
        fun setEmoji(context: Context, emoji: String) {
            currentEmoji = emoji
            val intent = Intent(context, ScreenRecordService::class.java).apply {
                action = ACTION_SET_EMOJI
                putExtra("emoji", emoji)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        // Drawing on / off without changing the picked tool.
        // The ball tap calls this with false BEFORE opening the
        // toolbar, or the touchable overlay would sit above the
        // toolbar Activity and eat its taps.
        fun setDrawMode(context: Context, enabled: Boolean) {
            val intent = Intent(context, ScreenRecordService::class.java).apply {
                action = ACTION_SET_DRAW_MODE
                putExtra("enabled", enabled)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        // Open the recording toolbar: drawing off first (see
        // setDrawMode), then the toolbar Activity.
        fun openToolbar(context: Context) {
            setDrawMode(context, false)
            try {
                val intent = Intent(context, ScreenRecordToolbarActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var tmpFile: File? = null
    // MIC: true when the microphone toggle was ON and
    // RECORD_AUDIO was granted at the moment recording started.
    @Volatile
    private var recordAudio: Boolean = false
    private var quality: Int = 720

    @Volatile
    private var isStopping: Boolean = false
    private var recordThread: HandlerThread? = null
    private var recordHandler: Handler? = null
    // Overlay windows are Views - they belong on the MAIN
    // thread. The recorder setup runs on a background thread,
    // so overlay work is posted here. (A View added from a
    // background thread can swallow touches - this was the
    // pen not drawing.)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Draw overlay: full-screen transparent window + the view.
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var drawView: ScreenDrawView? = null
    // Mini ball: only while recording AND the main ball is OFF.
    private var miniBallView: View? = null
    private var miniBallParams: WindowManager.LayoutParams? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            // The user stopped from the system's own recording
            // chip (or the system killed the projection).
            stopRecording()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRecording()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                startRecording(intent)
                return START_NOT_STICKY
            }
            ACTION_SET_TOOL, ACTION_CLEAR_MARKS, ACTION_SET_DRAW_MODE, ACTION_SET_EMOJI -> {
                // Tool intents only make sense while recording.
                // If the service is not running, do not linger.
                if (!isRecording && !isStarting) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                when (intent.action) {
                    ACTION_SET_TOOL -> {
                        val tool = try {
                            DrawTool.valueOf(intent.getStringExtra("tool") ?: "NONE")
                        } catch (_: Exception) {
                            DrawTool.NONE
                        }
                        currentTool = tool
                        drawView?.tool = tool
                        setOverlayTouchable(tool != DrawTool.NONE)
                    }
                    ACTION_CLEAR_MARKS -> drawView?.clearMarks()
                    ACTION_SET_DRAW_MODE ->
                        setOverlayTouchable(intent.getBooleanExtra("enabled", false))
                    ACTION_SET_EMOJI -> {
                        currentEmoji = intent.getStringExtra("emoji") ?: currentEmoji
                        drawView?.stampText = currentEmoji
                    }
                }
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun dpToPx(dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()

    // Video size: the screen's own aspect, scaled so the long
    // side fits the quality (720p -> 1280, 1080p -> 1920).
    // H.264 needs even dimensions.
    private fun chooseVideoSize(quality: Int): Size {
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val longSide = maxOf(screenW, screenH)
        val targetLong = if (quality >= 1080) 1920 else 1280
        val scale = minOf(1f, targetLong.toFloat() / longSide.toFloat())
        var outW = (screenW * scale).toInt()
        var outH = (screenH * scale).toInt()
        if (outW % 2 == 1) outW--
        if (outH % 2 == 1) outH--
        return Size(maxOf(outW, 2), maxOf(outH, 2))
    }

    private fun startRecording(intent: Intent) {
        if (isRecording || isStarting) return
        val resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED)
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("resultData", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("resultData")
        }
        if (resultCode != Activity.RESULT_OK || data == null) {
            stopSelf()
            return
        }
        quality = intent.getIntExtra("quality", 720)
        // MIC: sound only when the toggle was ON and the
        // permission is granted right now. The mic is never
        // opened without the permission.
        recordAudio = intent.getBooleanExtra("micOn", true) &&
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        isStarting = true
        // Foreground FIRST (Android 14 rule: the mediaProjection
        // type must be active before getMediaProjection).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (recordAudio &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            ) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            try {
                startForeground(NOTIF_ID, buildNotification(), fgsType)
            } catch (_: Exception) {
                try {
                    startForeground(NOTIF_ID, buildNotification())
                } catch (_: Exception) {
                }
            }
        } else {
            try {
                startForeground(NOTIF_ID, buildNotification())
            } catch (_: Exception) {
            }
        }
        // Heavy work OFF the main thread from here on.
        val thread = HandlerThread("ScreenRecord").apply { start() }
        recordThread = thread
        recordHandler = Handler(thread.looper)
        recordHandler?.post { startRecordingInternal(resultCode, data) }
    }

    private fun startRecordingInternal(resultCode: Int, data: Intent) {
        try {
            val manager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = manager.getMediaProjection(resultCode, data) ?: run {
                isStarting = false
                cleanup()
                stopSelf()
                return
            }
            mediaProjection = projection
            try {
                projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
            } catch (_: Exception) {
            }
            val metrics = resources.displayMetrics
            val videoSize = chooseVideoSize(quality)
            tmpFile = File(filesDir, "bluefilm/.tmp_sr_${System.currentTimeMillis()}.mp4")
                .apply { parentFile?.mkdirs() }
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder = recorder
            recorder.apply {
                if (recordAudio) {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                }
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                if (recordAudio) {
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(128_000)
                    setAudioSamplingRate(44_100)
                }
                setVideoSize(videoSize.width, videoSize.height)
                setVideoFrameRate(30)
                setVideoEncodingBitRate(if (quality >= 1080) 8_000_000 else 5_000_000)
                setOutputFile(tmpFile!!.absolutePath)
                prepare()
            }
            virtualDisplay = projection.createVirtualDisplay(
                "CalNyxScreenRecord",
                videoSize.width,
                videoSize.height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.surface,
                null,
                null
            ) ?: run {
                isStarting = false
                cleanup()
                stopSelf()
                return
            }
            recorder.start()
            isStarting = false
            isRecording = true
            startedAtMillis = System.currentTimeMillis()
            // Overlay windows are main-thread work - post them.
            // The ball is brought above the overlay so it stays
            // tappable while drawing.
            mainHandler.post {
                addDrawOverlay()
                maybeShowMiniBall()
                try {
                    FloatingBallService.bringToFront(this)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
            isStarting = false
            cleanup()
            stopSelf()
        }
    }

    // The draw overlay: full-screen, transparent, NOT touchable
    // by default (the phone works normally under it). Picking a
    // tool makes it touchable - see setOverlayTouchable.
    // Runs on the MAIN thread only (see mainHandler).
    private fun addDrawOverlay() {
        if (overlayView != null) return
        if (!FloatingBallService.canDrawOverlays(this)) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val view = ScreenDrawView(this)
        // A tool picked before the overlay existed is not lost:
        // the new view starts with the current tool + emoji.
        view.tool = currentTool
        view.stampText = currentEmoji
        drawView = view
        val container = FrameLayout(this)
        container.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        overlayParams = p
        overlayView = container
        try {
            wm.addView(container, p)
        } catch (_: Exception) {
            overlayView = null
            overlayParams = null
            drawView = null
            return
        }
        // A tool was already active when the overlay appeared:
        // make it touchable at once, or the first taps would
        // fall through to the app below.
        if (currentTool != DrawTool.NONE) setOverlayTouchable(true)
    }

    private fun setOverlayTouchable(touchable: Boolean) {
        val view = overlayView ?: return
        val p = overlayParams ?: return
        p.flags = if (touchable) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        try {
            windowManager?.updateViewLayout(view, p)
        } catch (_: Exception) {
        }
        if (touchable) {
            try {
                FloatingBallService.bringToFront(this)
            } catch (_: Exception) {
            }
        }
    }

    private fun removeDrawOverlay() {
        try {
            val view = overlayView
            if (view != null) windowManager?.removeView(view)
        } catch (_: Exception) {
        }
        overlayView = null
        overlayParams = null
        drawView = null
    }

    // Mini ball: a small white ball, same look as the main ball,
    // shown ONLY while recording and the main ball is OFF - so
    // there is always a way to open the toolbar. Draggable, tap
    // opens the toolbar (drawing off first, same as the ball).
    // FLAG_SECURE, like the main ball: it never appears in the
    // recorded video (or a screenshot), only on the live screen.
    private fun maybeShowMiniBall() {
        if (miniBallView != null) return
        if (MoanControlPrefs.isBallEnabled(this)) return
        if (!FloatingBallService.canDrawOverlays(this)) return
        val wm = windowManager
            ?: getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val sizePx = dpToPx(40)
        val view = FrameLayout(this)
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
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val p = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                // HIDE FROM CAPTURE (2026-11-20): FLAG_SECURE -
                // this mini ball never lands in the recorded video
                // (or a screenshot), only on the live screen.
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenW - sizePx - dpToPx(16)
            y = screenH / 2
        }
        miniBallParams = p
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
                    p.x = (startX + dx).coerceIn(0, (screenW - sizePx).coerceAtLeast(0))
                    p.y = (startY + dy).coerceIn(0, (screenH - sizePx).coerceAtLeast(0))
                    try {
                        wm.updateViewLayout(view, p)
                    } catch (_: Exception) {
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        openToolbar(this@ScreenRecordService)
                    }
                    true
                }
                else -> false
            }
        }
        miniBallView = view
        try {
            wm.addView(view, p)
        } catch (_: Exception) {
            miniBallView = null
            miniBallParams = null
        }
    }

    private fun removeMiniBall() {
        try {
            val view = miniBallView
            if (view != null) windowManager?.removeView(view)
        } catch (_: Exception) {
        }
        miniBallView = null
        miniBallParams = null
    }

    private fun stopRecording() {
        synchronized(this) {
            if (isStopping) return
            isStopping = true
        }
        if (!isRecording) {
            isStarting = false
            isStopping = false
            cleanup()
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
            stopSelf()
            return
        }
        try {
            try { mediaRecorder?.stop() } catch (_: Exception) {}
            try { mediaRecorder?.reset() } catch (_: Exception) {}
            try { mediaRecorder?.release() } catch (_: Exception) {}
            mediaRecorder = null
            try { virtualDisplay?.release() } catch (_: Exception) {}
            virtualDisplay = null
            try { mediaProjection?.unregisterCallback(projectionCallback) } catch (_: Exception) {}
            try { mediaProjection?.stop() } catch (_: Exception) {}
            mediaProjection = null
            // Same save pattern as the camera recorder: copy the
            // temp to its final plain .mp4 (size-checked, retry
            // once, then rename).
            val tmp = tmpFile
            if (tmp != null && tmp.exists() && tmp.length() > 0) {
                try {
                    val dest = BlueFilmStore.newScreenVideoFile(this)
                    val tmpDest = File(dest.parentFile, dest.name + ".tmp")
                    var ok = false
                    for (attempt in 0 until 2) {
                        if (ok) break
                        try {
                            if (tmpDest.exists()) tmpDest.delete()
                            tmp.copyTo(tmpDest, overwrite = true)
                            if (tmpDest.exists() && tmpDest.length() == tmp.length()) {
                                ok = tmpDest.renameTo(dest)
                                if (!ok) tmpDest.delete()
                            } else {
                                tmpDest.delete()
                            }
                        } catch (_: Exception) {
                            tmpDest.delete()
                        }
                    }
                } catch (_: Exception) {}
                tmp.delete()
            }
            tmpFile = null
        } catch (_: Exception) {
        } finally {
            isRecording = false
            isStarting = false
            isStopping = false
            startedAtMillis = 0L
            currentTool = DrawTool.NONE
            removeDrawOverlay()
            removeMiniBall()
            try { recordThread?.quitSafely() } catch (_: Exception) {}
            recordThread = null
            recordHandler = null
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
            stopSelf()
        }
    }

    private fun cleanup() {
        try { mediaRecorder?.release() } catch (_: Exception) {}
        mediaRecorder = null
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { mediaProjection?.unregisterCallback(projectionCallback) } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaProjection = null
        tmpFile?.delete()
        tmpFile = null
        isRecording = false
        isStarting = false
        startedAtMillis = 0L
        currentTool = DrawTool.NONE
        removeDrawOverlay()
        removeMiniBall()
        try { recordThread?.quitSafely() } catch (_: Exception) {}
        recordThread = null
        recordHandler = null
    }

    override fun onDestroy() {
        if (isRecording || isStarting) cleanup()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Screen recording", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
            nm.createNotificationChannel(channel)
        }
        val stopIntent = Intent(this, ScreenRecordService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle("CalNyx - Recording screen")
            .setContentText("Screen is recording. Tap Stop to finish.")
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(0, "Stop", stopPending)
            .build()
    }
}
