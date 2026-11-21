// FILE: app/src/main/java/com/calnyx/app/network/ScreenStreamService.kt
// NEW FILE - live screen streaming to any browser (2026-11-21).
//
// How it works (the relay is server.js in this same delivery):
//   1. MediaProjection captures the screen into an ImageReader
//      (same system capture dialog as screen recording - Android
//      shows it every time, it cannot be skipped).
//   2. Each frame is scaled down and JPEG-encoded on a background
//      thread, then sent as a binary WebSocket frame to the relay.
//   3. The relay forwards frames to every viewer that joined the
//      room with the right password. A viewer only needs a browser:
//      https://<relay>/?room=ABC123
//   4. No STUN / TURN / port forwarding: phone and viewers both
//      connect OUT to the relay, so it works on mobile data and
//      through any NAT. The price is latency (~1-2 s) and the relay
//      must be running (Render free tier sleeps after ~15 min idle,
//      so the first connection after a sleep takes ~30-60 s to wake).
//
// Password is mandatory, at least 2 characters (enforced here and
// on the relay). The password is never in the share link - the
// viewer types it on the page.
//
// Needs in app/build.gradle.kts (dependencies block):
//   implementation("com.squareup.okhttp3:okhttp:4.12.0")
// And in AndroidManifest.xml (inside <application>):
//   <service android:name=".network.ScreenStreamService"
//       android:foregroundServiceType="mediaProjection"
//       android:exported="false" />
// (FOREGROUND_SERVICE_MEDIA_PROJECTION permission is already needed
//  by ScreenRecordService - if it is declared, do not add it twice.)
//
// Honest limits: the stream stops if the phone locks or the screen
// turns off (MediaProjection ends). Keep the screen on while
// streaming. Sound is NOT streamed - picture only.
package com.calnyx.app.network

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Size
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class ScreenStreamService : Service() {

    companion object {
        const val ACTION_START = "com.calnyx.app.network.SCREEN_STREAM_START"
        const val ACTION_STOP = "com.calnyx.app.network.SCREEN_STREAM_STOP"
        private const val NOTIF_ID = 2003
        private const val CHANNEL_ID = "calnyx_screen_stream"

        @Volatile
        var isStreaming: Boolean = false
            private set

        @Volatile
        var isStarting: Boolean = false
            private set

        // Room code + share link, set when the relay answers "created".
        @Volatile
        var roomCode: String? = null
            private set

        @Volatile
        var streamUrl: String? = null
            private set

        @Volatile
        var viewerCount: Int = 0
            private set

        @Volatile
        var startedAtMillis: Long = 0L
            private set

        // Last failure, for the UI to show once. takeError() reads
        // it and clears it, so it is not shown twice.
        @Volatile
        private var lastError: String? = null

        fun takeError(): String? {
            val e = lastError
            lastError = null
            return e
        }

        fun start(
            context: Context,
            resultCode: Int,
            data: Intent,
            password: String,
            relayUrl: String,
            quality: String
        ) {
            val intent = Intent(context, ScreenStreamService::class.java).apply {
                action = ACTION_START
                putExtra("resultCode", resultCode)
                putExtra("resultData", data)
                putExtra("password", password)
                putExtra("relayUrl", relayUrl)
                putExtra("quality", quality)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenStreamService::class.java).apply {
                action = ACTION_STOP
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var streamThread: HandlerThread? = null
    private var streamHandler: Handler? = null
    private var client: OkHttpClient? = null
    private var socket: WebSocket? = null

    // True only after the relay answered "created" - frames are
    // sent only then, never into the void.
    @Volatile
    private var wsReady: Boolean = false
    @Volatile
    private var isStopping: Boolean = false

    private var password: String = ""
    private var relayUrl: String = ""
    private var jpegQuality: Int = 60
    private var targetWidth: Int = 720
    private var frameIntervalMs: Long = 166L // ~6 fps
    private var lastSentAt: Long = 0L

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            // Screen turned off / locked, or the system ended
            // the capture - the stream cannot continue.
            stopStreaming()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopStreaming()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                startStreaming(intent)
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun startStreaming(intent: Intent) {
        if (isStreaming || isStarting) return
        password = intent.getStringExtra("password") ?: ""
        relayUrl = (intent.getStringExtra("relayUrl") ?: "").trim().trimEnd('/')
        val quality = intent.getStringExtra("quality") ?: "Medium"
        when (quality) {
            "Low" -> { targetWidth = 480; jpegQuality = 45; frameIntervalMs = 250L }
            "High" -> { targetWidth = 960; jpegQuality = 75; frameIntervalMs = 125L }
            else -> { targetWidth = 720; jpegQuality = 60; frameIntervalMs = 166L }
        }
        if (password.length < 2) {
            lastError = "Password must be at least 2 characters."
            stopSelf()
            return
        }
        if (!relayUrl.startsWith("http://") && !relayUrl.startsWith("https://")) {
            lastError = "Set your relay server URL in Settings first."
            stopSelf()
            return
        }
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
        isStarting = true
        // Foreground FIRST (Android 14 rule: the mediaProjection
        // type must be active before getMediaProjection).
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID, buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIF_ID, buildNotification())
            }
        } catch (_: Exception) {
            try { startForeground(NOTIF_ID, buildNotification()) } catch (_: Exception) {}
        }
        val thread = HandlerThread("ScreenStream").apply { start() }
        streamThread = thread
        streamHandler = Handler(thread.looper)
        streamHandler?.post { startStreamingInternal(resultCode, data) }
    }

    private fun startStreamingInternal(resultCode: Int, data: Intent) {
        try {
            val manager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = manager.getMediaProjection(resultCode, data) ?: run {
                isStarting = false
                lastError = "Screen capture was not allowed."
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
            val size = chooseStreamSize(targetWidth)
            val reader = ImageReader.newInstance(
                size.width, size.height, PixelFormat.RGBA_8888, 2
            )
            imageReader = reader
            reader.setOnImageAvailableListener({ r -> onFrameAvailable(r) }, streamHandler)
            virtualDisplay = projection.createVirtualDisplay(
                "CalNyxScreenStream",
                size.width,
                size.height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                null
            ) ?: run {
                isStarting = false
                lastError = "Could not start screen capture."
                cleanup()
                stopSelf()
                return
            }
            connectSocket()
        } catch (_: Exception) {
            isStarting = false
            lastError = "Could not start screen capture."
            cleanup()
            stopSelf()
        }
    }

    // Stream size: the screen's own aspect, scaled so the width
    // fits the quality setting. Even dimensions, like video.
    private fun chooseStreamSize(targetW: Int): Size {
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val scale = if (screenW > targetW) targetW.toFloat() / screenW.toFloat() else 1f
        var outW = (screenW * scale).toInt()
        var outH = (screenH * scale).toInt()
        if (outW % 2 == 1) outW--
        if (outH % 2 == 1) outH--
        return Size(maxOf(outW, 2), maxOf(outH, 2))
    }

    private fun connectSocket() {
        val httpClient = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
        client = httpClient
        val request = Request.Builder()
            .url(ScreenStreamPrefs.wsUrl(relayUrl))
            .build()
        socket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Ask the relay for a room. The password goes in
                // this message only - never in the share link.
                try {
                    webSocket.send(
                        JSONObject()
                            .put("type", "create")
                            .put("password", password)
                            .toString()
                    )
                } catch (_: Exception) {
                    fail("Could not reach the relay server.")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = JSONObject(text)
                    when (msg.optString("type")) {
                        "created" -> {
                            val code = msg.optString("room")
                            if (code.isEmpty()) {
                                fail("Relay did not return a room code.")
                                return
                            }
                            roomCode = code
                            streamUrl = ScreenStreamPrefs.streamLink(relayUrl, code)
                            viewerCount = 0
                            startedAtMillis = System.currentTimeMillis()
                            isStarting = false
                            isStreaming = true
                            wsReady = true
                            updateNotification()
                        }
                        "viewers" -> {
                            viewerCount = msg.optInt("count", viewerCount)
                        }
                        "error" -> {
                            fail(msg.optString("message", "Relay error."))
                        }
                    }
                } catch (_: Exception) {
                    // A bad message is not a reason to kill the stream.
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // The relay never sends us frames - ignore.
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!isStreaming && !isStarting) return
                fail("Could not reach the relay server. Check the server URL.")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (isStreaming || isStarting) stopStreaming()
            }
        })
    }

    private fun fail(message: String) {
        lastError = message
        streamHandler?.post { stopStreaming() } ?: stopStreaming()
    }

    private fun onFrameAvailable(reader: ImageReader) {
        if (!wsReady) {
            // Not joined yet - drop the frame, keep the reader clear.
            try { reader.acquireLatestImage()?.close() } catch (_: Exception) {}
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastSentAt < frameIntervalMs) {
            try { reader.acquireLatestImage()?.close() } catch (_: Exception) {}
            return
        }
        val image: Image = try {
            reader.acquireLatestImage() ?: return
        } catch (_: Exception) {
            return
        }
        lastSentAt = now
        try {
            val bytes = imageToJpeg(image) ?: return
            try { socket?.send(ByteString.of(*bytes)) } catch (_: Exception) {}
        } finally {
            try { image.close() } catch (_: Exception) {}
        }
    }

    // Image -> JPEG bytes. The plane's row stride can be wider
    // than the image (padding), so the bitmap is built at the
    // row width and cropped to the real width.
    private fun imageToJpeg(image: Image): ByteArray? {
        return try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowWidth = if (pixelStride > 0) rowStride / pixelStride else image.width
            val bitmap = Bitmap.createBitmap(
                rowWidth, image.height, Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            val cropped = if (rowWidth != image.width) {
                Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
            } else {
                bitmap
            }
            val out = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
            if (cropped !== bitmap) cropped.recycle()
            bitmap.recycle()
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    private fun stopStreaming() {
        synchronized(this) {
            if (isStopping) return
            isStopping = true
        }
        wsReady = false
        try { socket?.close(1000, null) } catch (_: Exception) {}
        socket = null
        try { client?.dispatcher?.executorService?.shutdown() } catch (_: Exception) {}
        client = null
        cleanup()
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        isStopping = false
        stopSelf()
    }

    private fun cleanup() {
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { mediaProjection?.unregisterCallback(projectionCallback) } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaProjection = null
        try { streamThread?.quitSafely() } catch (_: Exception) {}
        streamThread = null
        streamHandler = null
        isStreaming = false
        isStarting = false
        wsReady = false
        roomCode = null
        streamUrl = null
        viewerCount = 0
        startedAtMillis = 0L
    }

    override fun onDestroy() {
        if (isStreaming || isStarting) {
            wsReady = false
            try { socket?.close(1000, null) } catch (_: Exception) {}
            cleanup()
        }
        super.onDestroy()
    }

    private fun updateNotification() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, buildNotification())
        } catch (_: Exception) {
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Screen stream", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            nm.createNotificationChannel(channel)
        }
        val stopIntent = Intent(this, ScreenStreamService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = roomCode?.let { "Room $it - tap Stop to end the stream." }
            ?: "Connecting to the relay server..."
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle("CalNyx - Streaming screen")
            .setContentText(text)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(0, "Stop", stopPending)
            .build()
    }
}
