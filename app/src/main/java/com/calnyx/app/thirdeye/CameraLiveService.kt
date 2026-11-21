// FILE: app/src/main/java/com/calnyx/app/network/CameraLiveService.kt
// NEW FILE - Camera Live 1-to-1 (2026-11-21). Task 5.
//
// CAMERA2, NOT CameraX: the project has no CameraX dependency
// in app/build.gradle.kts (no androidx.camera:* lines), so this
// uses Camera2 + ImageReader, the same way BlueFilmService does
// (CameraManager, TEMPLATE_PREVIEW, deprecated
// createCaptureSession(list, callback, handler)). Do NOT add
// CameraX imports - they will not compile without the dependency.
//
// How it works (relay is deliveries/04-relay/server.js - the SAME
// Render relay as Screen Stream, mode "camera"):
//   Host:  {"type":"create","mode":"camera","password":"..."}
//          -> {"type":"created","room":"ABC123","mode":"camera"}
//   Viewer (publishes own camera):
//          {"type":"join-publish","room":"ABC123","password":"..."}
//          -> {"type":"joined","room":"ABC123","mode":"camera"}
//          | {"type":"error","message":"Room full. This Camera Live
//             room already has a viewer."}  (2nd viewer is refused)
//   Host also gets {"type":"peer-joined"} / {"type":"peer-left"}
//   and {"type":"viewers","count":0|1} (1 while the viewer is in).
//   Binary JPEG frames: host frames go to the viewer (+ passive
//   browser watchers); viewer frames go to the host ONLY.
//   Passive watchers (browser / in-app WebView) use the plain
//   {"type":"join"} and see host frames only.
//   Video only - NO microphone, NO audio, ever.
//
//   DEPLOY THE V2 RELAY FIRST: this service only works with
//   deliveries/04-relay/server.js. The old screen-only relay
//   does not know "join-publish" - it would ignore the message
//   and the viewer camera would sit at "Turning on..." until
//   stopped by hand. Hosting alone would still work on the old
//   relay (as a screen room), but nothing two-way would.
//
// RETRY (same as ScreenStreamService): connect timeout 60 s, NO
// read timeout, up to 3 reconnects (5 s apart) while starting,
// so a sleeping Render relay gets time to wake up.
//
// KEEPS RUNNING ON LOCK / SCREEN OFF: foreground service type
// "camera" + a PARTIAL_WAKE_LOCK (no timeout - the user decided
// NO auto-stop after 30 min; the stream runs until the host
// presses Stop). Battery cost is real - the notification always
// shows a Stop action. Camera2 keeps capturing with the screen
// off, unlike MediaProjection, which is why Camera Live survives
// a lock and Screen Stream does not.
//
// MUTUAL EXCLUSION: starting Camera Live stops Screen Stream
// (ScreenStreamService.stop), and the patch file adds the reverse
// line to ScreenStreamSection.startStream().
//
// ROTATION: frames are rotated with the same formula BlueFilm
// uses for its recording orientation hint (sensor orientation
// +/- device rotation). If the remote picture ever looks turned,
// this one value is the fix.
//
// Needs in AndroidManifest.xml (inside <application>):
//   <service android:name=".network.CameraLiveService"
//       android:foregroundServiceType="camera"
//       android:exported="false" />
// CAMERA, FOREGROUND_SERVICE_CAMERA and WAKE_LOCK permissions are
// already declared - do not add them twice.
//
// Honest limits: ~1-2 s latency (same JPEG relay as Screen
// Stream); one mode at a time - a phone cannot host a Camera Live
// room AND publish as a viewer to another room at once (the
// viewer toggle shows a note if hosting); if the viewer joins
// from a browser, there is no viewer camera, so the host sees
// their own camera big.
package com.calnyx.app.thirdeye

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.WindowManager
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

class CameraLiveService : Service() {

    companion object {
        const val ACTION_START_HOST = "com.calnyx.app.thirdeye.CAMERA_LIVE_START_HOST"
        const val ACTION_START_VIEWER = "com.calnyx.app.thirdeye.CAMERA_LIVE_START_VIEWER"
        const val ACTION_STOP = "com.calnyx.app.thirdeye.CAMERA_LIVE_STOP"
        const val ACTION_SWITCH_FACING = "com.calnyx.app.thirdeye.CAMERA_LIVE_SWITCH_FACING"
        private const val NOTIF_ID = 2004
        private const val CHANNEL_ID = "calnyx_camera_live"
        private const val TAG = "CameraLive"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 5000L
        private const val MODE_NONE = 0
        private const val MODE_HOST = 1
        private const val MODE_VIEWER = 2

        @Volatile
        var isStreaming: Boolean = false
            private set

        @Volatile
        var isStarting: Boolean = false
            private set

        @Volatile
        var isViewerPublishing: Boolean = false
            private set

        @Volatile
        var isViewerStarting: Boolean = false
            private set

        // Host room code + share link, set when the relay answers "created".
        @Volatile
        var roomCode: String? = null
            private set

        @Volatile
        var streamUrl: String? = null
            private set

        // Host only: 1 while the one viewer is in, 0 while alone.
        // (Passive browser watchers are NOT counted - see relay.)
        @Volatile
        var viewerCount: Int = 0
            private set

        @Volatile
        var startedAtMillis: Long = 0L
            private set

        @Volatile
        var currentFacing: Int = CameraCharacteristics.LENS_FACING_FRONT
            private set

        // Latest JPEG bytes, for the UI to draw. Host mode: the
        // viewer's frames arrive here (binary WebSocket messages).
        // Both modes: our own camera frames, for the small self view.
        // The UI decodes these (never the service) and only when
        // the reference changes, so a frame is decoded at most once.
        @Volatile
        var latestViewerJpeg: ByteArray? = null
            private set

        @Volatile
        var latestLocalJpeg: ByteArray? = null
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

        fun startHost(
            context: Context,
            password: String,
            relayUrl: String,
            quality: String,
            facing: Int
        ) {
            // Mutual exclusion: Screen Share stops when Camera Live starts.
            ScreenStreamService.stop(context)
            val intent = Intent(context, CameraLiveService::class.java).apply {
                action = ACTION_START_HOST
                putExtra("password", password)
                putExtra("relayUrl", relayUrl)
                putExtra("quality", quality)
                putExtra("facing", facing)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startViewerPublishing(
            context: Context,
            roomCode: String,
            password: String,
            relayUrl: String,
            quality: String,
            facing: Int
        ) {
            val intent = Intent(context, CameraLiveService::class.java).apply {
                action = ACTION_START_VIEWER
                putExtra("roomCode", roomCode)
                putExtra("password", password)
                putExtra("relayUrl", relayUrl)
                putExtra("quality", quality)
                putExtra("facing", facing)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopViewerPublishing(context: Context) {
            stop(context)
        }

        fun stop(context: Context) {
            // CRASH FIX (2026-11-21): do NOT start the service
            // just to stop it. startForegroundService() gives the
            // service ~5 s to call startForeground(), but the
            // ACTION_STOP path never does - so stopping a service
            // that was not running crashed the whole app with
            // ForegroundServiceDidNotStartInTimeException (that is
            // what killed Camera Live's Start Camera tap, via the
            // Screen Stream stop). When nothing is running or
            // starting there is nothing to stop, so skip it.
            if (!isStreaming && !isStarting && !isViewerPublishing && !isViewerStarting) return
            val intent = Intent(context, CameraLiveService::class.java).apply {
                action = ACTION_STOP
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun switchFacing(context: Context) {
            // Same crash fix as stop(): switching the lens on a
            // service that is not running would start it without
            // any startForeground() call, so skip it.
            if (!isStreaming && !isStarting && !isViewerPublishing && !isViewerStarting) return
            val intent = Intent(context, CameraLiveService::class.java).apply {
                action = ACTION_SWITCH_FACING
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var client: OkHttpClient? = null
    private var socket: WebSocket? = null

    // True only after the relay answered "created" (host) or
    // "joined" (viewer publish) - frames are sent only then.
    @Volatile
    private var wsReady: Boolean = false
    @Volatile
    private var isStopping: Boolean = false
    @Volatile
    private var mode: Int = MODE_NONE

    private var password: String = ""
    private var relayUrl: String = ""
    private var roomCodeToJoin: String = ""
    private var jpegQuality: Int = 55
    private var targetWidth: Int = 480
    private var frameIntervalMs: Long = 200L // ~5 fps
    private var lastSentAt: Long = 0L
    private var retryCount: Int = 0
    private var rotationDegrees: Int = 0

    private val cameraCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCaptureSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            try { camera.close() } catch (_: Exception) {}
            cameraDevice = null
            fail("Camera was disconnected.")
        }

        override fun onError(camera: CameraDevice, error: Int) {
            try { camera.close() } catch (_: Exception) {}
            cameraDevice = null
            fail("Camera error ($error).")
        }
    }

    private val sessionCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            captureSession = session
            try {
                val device = cameraDevice ?: return
                val reader = imageReader ?: return
                val request = device.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW
                ).apply {
                    addTarget(reader.surface)
                }.build()
                session.setRepeatingRequest(request, null, cameraHandler)
                Log.i(TAG, "camera session running facing=$currentFacing")
            } catch (_: Exception) {
                fail("Could not start the camera preview.")
            }
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            fail("Could not start the camera preview.")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopStreaming()
                return START_NOT_STICKY
            }
            ACTION_START_HOST -> {
                startHosting(intent)
                return START_NOT_STICKY
            }
            ACTION_START_VIEWER -> {
                startViewerPublishingInternal(intent)
                return START_NOT_STICKY
            }
            ACTION_SWITCH_FACING -> {
                switchFacingInternal()
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun readQualityAndFacing(intent: Intent) {
        when (intent.getStringExtra("quality") ?: "480") {
            "720" -> { targetWidth = 720; jpegQuality = 65; frameIntervalMs = 166L }
            else -> { targetWidth = 480; jpegQuality = 55; frameIntervalMs = 200L }
        }
        currentFacing = intent.getIntExtra(
            "facing", CameraCharacteristics.LENS_FACING_FRONT
        )
    }

    private fun startHosting(intent: Intent) {
        if (isStreaming || isStarting) return
        if (isViewerPublishing || isViewerStarting) {
            // One mode at a time - stop viewer publishing first.
            // NOTE: NOT stopStreaming() - that calls stopSelf(),
            // which would kill this service right after it
            // restarts as host. This only tears the old mode down.
            stopCurrentModeSilently()
        }
        password = intent.getStringExtra("password") ?: ""
        relayUrl = (intent.getStringExtra("relayUrl") ?: "").trim().trimEnd('/')
        readQualityAndFacing(intent)
        if (password.length < CameraLivePrefs.MIN_PASSWORD_LENGTH) {
            lastError = "Password must be at least 4 characters."
            stopSelf()
            return
        }
        if (!relayUrl.startsWith("http://") && !relayUrl.startsWith("https://")) {
            lastError = "Set your relay server URL in Settings first."
            stopSelf()
            return
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            lastError = "Camera permission is needed to share your camera."
            stopSelf()
            return
        }
        mode = MODE_HOST
        retryCount = 0
        isStarting = true
        Log.i(TAG, "startHosting relay=$relayUrl quality=$targetWidth")
        startForegroundCamera()
        acquireWakeLock()
        val thread = HandlerThread("CameraLive").apply { start() }
        cameraThread = thread
        cameraHandler = Handler(thread.looper)
        cameraHandler?.post {
            openCamera(currentFacing)
            connectSocket()
        }
    }

    private fun startViewerPublishingInternal(intent: Intent) {
        if (isViewerPublishing || isViewerStarting) return
        if (isStreaming || isStarting) {
            lastError = "Stop sharing your camera first, then turn on your camera here."
            return
        }
        password = intent.getStringExtra("password") ?: ""
        relayUrl = (intent.getStringExtra("relayUrl") ?: "").trim().trimEnd('/')
        roomCodeToJoin = (intent.getStringExtra("roomCode") ?: "").trim().uppercase()
        readQualityAndFacing(intent)
        if (roomCodeToJoin.length != 6) {
            lastError = "Type the 6-letter Room ID."
            stopSelf()
            return
        }
        if (password.length < CameraLivePrefs.MIN_PASSWORD_LENGTH) {
            lastError = "Password must be at least 4 characters."
            stopSelf()
            return
        }
        if (!relayUrl.startsWith("http://") && !relayUrl.startsWith("https://")) {
            lastError = "Set your relay server URL in Settings first."
            stopSelf()
            return
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            lastError = "Camera permission is needed to share your camera."
            stopSelf()
            return
        }
        mode = MODE_VIEWER
        retryCount = 0
        isViewerStarting = true
        Log.i(TAG, "startViewerPublishing room=$roomCodeToJoin")
        startForegroundCamera()
        acquireWakeLock()
        val thread = HandlerThread("CameraLive").apply { start() }
        cameraThread = thread
        cameraHandler = Handler(thread.looper)
        cameraHandler?.post {
            openCamera(currentFacing)
            connectSocket()
        }
    }

    private fun startForegroundCamera() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID, buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                )
            } else {
                startForeground(NOTIF_ID, buildNotification())
            }
        } catch (_: Exception) {
            try { startForeground(NOTIF_ID, buildNotification()) } catch (_: Exception) {}
        }
    }

    // No timeout on purpose: the user decided NO auto-stop, the
    // stream runs until the host presses Stop. Released in cleanup.
    // If the process dies, the system releases it anyway.
    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "CalNyx:CameraLive"
            ).apply { acquire() }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun chooseCameraId(facing: Int): String? {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        for (id in manager.cameraIdList) {
            val chars = manager.getCameraCharacteristics(id)
            if (chars.get(CameraCharacteristics.LENS_FACING) == facing) return id
        }
        return manager.cameraIdList.firstOrNull()
    }

    private fun chooseCaptureSize(map: StreamConfigurationMap?): Size {
        val sizes = map?.getOutputSizes(ImageReader::class.java)
        if (sizes.isNullOrEmpty()) return Size(targetWidth, targetWidth * 3 / 4)
        val fitting = sizes.filter { it.width <= targetWidth }
        return fitting.maxByOrNull { it.width * it.height }
            ?: sizes.minByOrNull { it.width * it.height }
            ?: Size(targetWidth, targetWidth * 3 / 4)
    }

    private fun openCamera(facing: Int) {
        try {
            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = chooseCameraId(facing) ?: run {
                fail("No camera found on this phone.")
                return
            }
            val chars = manager.getCameraCharacteristics(cameraId)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val size = chooseCaptureSize(map)
            // Rotation for the JPEG frames - same formula BlueFilm
            // uses for its recording orientation hint.
            val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            @Suppress("DEPRECATION")
            val rotation = (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay.rotation
            val deviceDegrees = when (rotation) {
                Surface.ROTATION_0 -> 0
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
            rotationDegrees = if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                (sensorOrientation + deviceDegrees) % 360
            } else {
                (sensorOrientation - deviceDegrees + 360) % 360
            }
            val reader = ImageReader.newInstance(
                size.width, size.height, ImageFormat.YUV_420_888, 2
            )
            imageReader = reader
            reader.setOnImageAvailableListener({ r -> onFrameAvailable(r) }, cameraHandler)
            try {
                manager.openCamera(cameraId, cameraCallback, cameraHandler)
            } catch (e: CameraAccessException) {
                fail("Could not open the camera.")
            } catch (e: SecurityException) {
                fail("Camera permission was denied.")
            }
        } catch (_: Exception) {
            fail("Could not open the camera.")
        }
    }

    private fun createCaptureSession() {
        val device = cameraDevice ?: return
        val reader = imageReader ?: return
        try {
            @Suppress("DEPRECATION")
            device.createCaptureSession(listOf(reader.surface), sessionCallback, cameraHandler)
        } catch (_: Exception) {
            fail("Could not start the camera preview.")
        }
    }

    private fun closeCameraOnly() {
        try { captureSession?.close() } catch (_: Exception) {}
        captureSession = null
        try { cameraDevice?.close() } catch (_: Exception) {}
        cameraDevice = null
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null
    }

    private fun switchFacingInternal() {
        if (mode == MODE_NONE) return
        val newFacing = if (currentFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            CameraCharacteristics.LENS_FACING_BACK
        } else {
            CameraCharacteristics.LENS_FACING_FRONT
        }
        currentFacing = newFacing
        CameraLivePrefs.saveFacing(this, newFacing)
        // Reopen the camera on the new lens. The socket, the room
        // and the stream stay exactly as they are - viewers see a
        // short freeze, not a reconnect.
        cameraHandler?.post {
            closeCameraOnly()
            openCamera(newFacing)
        } ?: run {
            // Not running yet - the saved facing is used at start.
        }
    }

    private fun connectSocket() {
        // Same timeouts as ScreenStreamService: 60 s to connect
        // (Render free tier may be waking up), NO read timeout
        // (the relay sends nothing until the peer joins).
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
        client = httpClient
        val request = Request.Builder()
            .url(CameraLivePrefs.wsUrl(relayUrl))
            .build()
        Log.i(TAG, "connecting to " + CameraLivePrefs.wsUrl(relayUrl))
        socket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket !== socket) return
                retryCount = 0
                try {
                    if (mode == MODE_HOST) {
                        Log.i(TAG, "socket open, creating a camera room")
                        webSocket.send(
                            JSONObject()
                                .put("type", "create")
                                .put("mode", "camera")
                                .put("password", password)
                                .toString()
                        )
                    } else {
                        Log.i(TAG, "socket open, joining room $roomCodeToJoin to publish")
                        webSocket.send(
                            JSONObject()
                                .put("type", "join-publish")
                                .put("room", roomCodeToJoin)
                                .put("password", password)
                                .toString()
                        )
                    }
                } catch (_: Exception) {
                    onSocketFailure()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket !== socket) return
                try {
                    val msg = JSONObject(text)
                    when (msg.optString("type")) {
                        "created" -> {
                            val code = msg.optString("room")
                            if (code.isEmpty()) {
                                fail("Relay did not return a room code.")
                                return
                            }
                            retryCount = 0
                            roomCode = code
                            Log.i(TAG, "created room=$code")
                            streamUrl = CameraLivePrefs.streamLink(relayUrl, code, password)
                            viewerCount = 0
                            startedAtMillis = System.currentTimeMillis()
                            isStarting = false
                            isStreaming = true
                            wsReady = true
                            updateNotification()
                        }
                        "joined" -> {
                            // Viewer publish mode: we are in the room now.
                            retryCount = 0
                            isViewerStarting = false
                            isViewerPublishing = true
                            wsReady = true
                            startedAtMillis = System.currentTimeMillis()
                            updateNotification()
                        }
                        "peer-joined" -> {
                            viewerCount = 1
                            updateNotification()
                        }
                        "peer-left" -> {
                            viewerCount = 0
                            latestViewerJpeg = null
                            updateNotification()
                        }
                        "viewers" -> {
                            viewerCount = msg.optInt("count", viewerCount)
                        }
                        "ended" -> {
                            // Host stopped (viewer mode only): our
                            // publish has nowhere to go any more.
                            fail("The host ended the call.")
                        }
                        "error" -> {
                            Log.e(TAG, "relay error: " + msg.optString("message"))
                            fail(msg.optString("message", "Relay error."))
                        }
                    }
                } catch (_: Exception) {
                    // A bad message is not a reason to kill the stream.
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (webSocket !== socket) return
                if (mode != MODE_HOST) {
                    // Viewer mode: the relay also sends us host
                    // frames on this socket, but the app watches
                    // the host in the WebView, not here - ignore.
                    return
                }
                // Host mode: a binary frame here is the viewer's
                // camera. Keep only the latest - the UI draws it.
                latestViewerJpeg = bytes.toByteArray()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (webSocket !== socket) return
                if (!isStreaming && !isStarting && !isViewerPublishing && !isViewerStarting) return
                Log.e(TAG, "socket failed: " + t.message + " http=" + (response?.code ?: -1), t)
                onSocketFailure()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket !== socket) return
                Log.w(TAG, "socket closed code=$code reason=$reason")
                if (isStarting || isViewerStarting) {
                    onSocketFailure()
                } else if (isStreaming || isViewerPublishing) {
                    stopStreaming()
                }
            }
        })
    }

    // A socket failure while STARTING is usually the relay waking
    // up: retry a few times instead of stopping at once. A failure
    // mid-stream still stops it - the link is already out, and a
    // silent reconnect would leave the peer hanging.
    private fun onSocketFailure() {
        if (isStreaming || isViewerPublishing) {
            fail("Connection to the relay server was lost.")
            return
        }
        if ((isStarting || isViewerStarting) && retryCount < MAX_RETRIES && !isStopping) {
            retryCount++
            Log.w(TAG, "retrying in 5s (attempt $retryCount/$MAX_RETRIES)")
            closeSocket()
            cameraHandler?.postDelayed(
                { if (!isStreaming && !isViewerPublishing && !isStopping) connectSocket() },
                RETRY_DELAY_MS
            )
        } else {
            fail("Could not reach the relay server. It may be waking up - wait a minute and try again.")
        }
    }

    private fun closeSocket() {
        try { socket?.close(1000, null) } catch (_: Exception) {}
        socket = null
        try { client?.dispatcher?.executorService?.shutdown() } catch (_: Exception) {}
        client = null
    }

    private fun fail(message: String) {
        lastError = message
        Log.e(TAG, "fail: $message")
        cameraHandler?.post { stopStreaming() } ?: stopStreaming()
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
            // Our own latest frame, for the small self view.
            latestLocalJpeg = bytes
            try { socket?.send(ByteString.of(*bytes)) } catch (_: Exception) {}
        } finally {
            try { image.close() } catch (_: Exception) {}
        }
    }

    private fun imageToJpeg(image: Image): ByteArray? {
        return try {
            val nv21 = yuv420ToNv21(image) ?: return null
            val yuvImage = YuvImage(
                nv21, ImageFormat.NV21, image.width, image.height, null
            )
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                Rect(0, 0, image.width, image.height), jpegQuality, out
            )
            val jpeg = out.toByteArray()
            if (rotationDegrees == 0) jpeg else rotateJpeg(jpeg) ?: jpeg
        } catch (_: Exception) {
            null
        }
    }

    // YUV_420_888 planes -> one NV21 byte array. The U and V
    // planes can have pixelStride 1 (planar) or 2 (interleaved),
    // and rowStride wider than the width (padding) - both are
    // honoured here instead of assuming a packed layout.
    private fun yuv420ToNv21(image: Image): ByteArray? {
        return try {
            val width = image.width
            val height = image.height
            val ySize = width * height
            val nv21 = ByteArray(ySize + ySize / 2)
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]
            val yBuffer = yPlane.buffer
            var dstPos = 0
            for (row in 0 until height) {
                yBuffer.position(row * yPlane.rowStride)
                yBuffer.get(nv21, dstPos, width)
                dstPos += width
            }
            val uvHeight = height / 2
            val uvWidth = width / 2
            for (row in 0 until uvHeight) {
                for (col in 0 until uvWidth) {
                    val uIndex = row * uPlane.rowStride + col * uPlane.pixelStride
                    val vIndex = row * vPlane.rowStride + col * vPlane.pixelStride
                    nv21[dstPos++] = vPlane.buffer.get(vIndex)
                    nv21[dstPos++] = uPlane.buffer.get(uIndex)
                }
            }
            nv21
        } catch (_: Exception) {
            null
        }
    }

    private fun rotateJpeg(jpeg: ByteArray): ByteArray? {
        return try {
            val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return null
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            val out = ByteArrayOutputStream()
            rotated.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
            if (rotated !== bitmap) rotated.recycle()
            bitmap.recycle()
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    // Tear the current mode down WITHOUT stopping the service
    // itself (no stopForeground, no stopSelf) - used only when
    // switching straight into the other mode.
    private fun stopCurrentModeSilently() {
        wsReady = false
        closeSocket()
        cleanup()
    }

    private fun stopStreaming() {
        synchronized(this) {
            if (isStopping) return
            isStopping = true
        }
        wsReady = false
        closeSocket()
        cleanup()
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        isStopping = false
        stopSelf()
    }

    private fun cleanup() {
        try { cameraHandler?.removeCallbacksAndMessages(null) } catch (_: Exception) {}
        closeCameraOnly()
        releaseWakeLock()
        try { cameraThread?.quitSafely() } catch (_: Exception) {}
        cameraThread = null
        cameraHandler = null
        isStreaming = false
        isStarting = false
        isViewerPublishing = false
        isViewerStarting = false
        wsReady = false
        retryCount = 0
        mode = MODE_NONE
        roomCode = null
        streamUrl = null
        viewerCount = 0
        startedAtMillis = 0L
        latestViewerJpeg = null
        latestLocalJpeg = null
    }

    override fun onDestroy() {
        if (isStreaming || isStarting || isViewerPublishing || isViewerStarting) {
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
                CHANNEL_ID, "Camera Live", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            nm.createNotificationChannel(channel)
        }
        val stopIntent = Intent(this, CameraLiveService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = when (mode) {
            MODE_HOST -> roomCode?.let { "Room $it - tap Stop to end the call." }
                ?: "Connecting to the relay server..."
            MODE_VIEWER -> roomCodeToJoin.let { "Sharing your camera in Room $it - tap Stop to turn it off." }
            else -> "Connecting to the relay server..."
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle("CalNyx - Camera Live")
            .setContentText(text)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(0, "Stop", stopPending)
            .build()
    }
}
