// FILE: app/src/main/java/com/calnyx/app/BlueFilm/BlueFilmService.kt
// PLAIN VERSION (2026-11-06) - REPLACE the old BlueFilmService.kt.
// Based on V8.2. ONLY change: NO ENCRYPTION on stop any more.
//  - The recorder still writes a plain .mp4 temp file.
//  - On stop the temp is now COPIED (via .tmp, size-checked,
//    retry once, then renamed) straight to its final plain
//    .mp4 name from BlueFilmStore.newVideoFile(). The isStopping
//    guard, the wake lock, Camera2, quality, facing and the
//    notification are all unchanged.
//  - Old encrypted .bfenc videos are converted by
//    BlueFilmStore.ensureMigrated() on first open - not here.
//  - MIC (2026-11-07): video now records WITH microphone audio
//    (AAC, 128 kbps, 44.1 kHz) when RECORD_AUDIO is granted at
//    start time; without it the video records silently, exactly
//    as before. Foreground type becomes camera|microphone only
//    while audio is on. The mic is never opened without the
//    permission, so a denied mic can never crash a recording.
//    Note: Android shows its green mic dot while recording,
//    same as the camera dot - the OS, no app can hide it.
package com.calnyx.app.BlueFilm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.util.Size
import android.view.Surface
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File

class BlueFilmService : Service() {

    companion object {
        const val ACTION_START = "com.calnyx.app.bluefilm.START"
        const val ACTION_STOP = "com.calnyx.app.bluefilm.STOP"
        const val EXTRA_FACING = "camera_facing"
        private const val PREFS = "bluefilm_prefs"
        private const val KEY_FACING = "camera_facing"
        private const val KEY_QUALITY = "video_quality"
        private const val NOTIF_ID = 2001
        private const val CHANNEL_ID = "bluefilm_rec"

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
        var currentFacing: Int = CameraCharacteristics.LENS_FACING_BACK
            private set

        fun savedFacing(context: Context): Int {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_FACING, CameraCharacteristics.LENS_FACING_BACK)
        }

        fun savedQuality(context: Context): Int {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_QUALITY, 720)
        }

        fun saveFacing(context: Context, facing: Int) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_FACING, facing).apply()
        }

        fun saveQuality(context: Context, quality: Int) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_QUALITY, quality).apply()
        }

        fun start(context: Context, facing: Int) {
            saveFacing(context, facing)
            val intent = Intent(context, BlueFilmService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_FACING, facing)
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

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var mediaRecorder: MediaRecorder? = null
    // MIC: true when RECORD_AUDIO was granted at the moment
    // recording started. Set on the main thread in
    // startRecording(), read on the camera thread in
    // startRecordingInternal(). Decides audio + FGS mic type.
    @Volatile
    private var recordAudio: Boolean = false
    private var tmpFile: File? = null

    @Volatile
    private var isStopping: Boolean = false
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var orientationHint = 90
    private var wakeLock: PowerManager.WakeLock? = null

    private val cameraCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            try { camera.close() } catch (_: Exception) {}
            cameraDevice = null
            isStarting = false
            stopRecording()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            try { camera.close() } catch (_: Exception) {}
            cameraDevice = null
            isStarting = false
            stopRecording()
        }
    }

    private val sessionCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            captureSession = session
            try {
                val recorder = mediaRecorder ?: return
                val request = cameraDevice?.createCaptureRequest(
                    CameraDevice.TEMPLATE_RECORD
                )?.apply {
                    addTarget(recorder.surface)
                }?.build() ?: return
                session.setRepeatingRequest(request, null, cameraHandler)
                recorder.start()
                isStarting = false
                isRecording = true
                startedAtMillis = System.currentTimeMillis()
            } catch (_: Exception) {
                isStarting = false
                stopRecording()
            }
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            isStarting = false
            stopRecording()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val facing = intent.getIntExtra(EXTRA_FACING, savedFacing(this))
                currentFacing = facing
                startRecording(facing)
            }
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun chooseCameraId(facing: Int): String? {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        for (id in manager.cameraIdList) {
            val chars = manager.getCameraCharacteristics(id)
            if (chars.get(CameraCharacteristics.LENS_FACING) == facing) return id
        }
        return manager.cameraIdList.firstOrNull()
    }

    private fun chooseVideoSize(map: StreamConfigurationMap?, quality: Int): Size {
        val target = if (quality >= 1080) Size(1920, 1080) else Size(1280, 720)
        val sizes = map?.getOutputSizes(MediaRecorder::class.java)
            ?: return target
        if (sizes.contains(target)) return target
        val fitting = sizes.filter { it.width <= target.width && it.height <= target.height }
        return fitting.maxByOrNull { it.width * it.height }
            ?: sizes.minByOrNull { it.width * it.height }
            ?: target
    }

    private fun startRecording(facing: Int) {
        if (isRecording || isStarting) return
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }
        // MIC: audio only when the permission is granted right
        // now. The screen asks for Camera + Microphone together;
        // if the mic was denied, this stays false and the video
        // records without sound, exactly as before.
        recordAudio = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        isStarting = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Microphone type only when actually recording audio
            // (and only on Android 11+, where the mic type
            // exists). Camera-only otherwise.
            val fgsType = if (recordAudio &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            ) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            startForeground(NOTIF_ID, buildNotification(), fgsType)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
        // Heavy work OFF the main thread from here on.
        val thread = HandlerThread("BlueFilmCamera").apply { start() }
        cameraThread = thread
        cameraHandler = Handler(thread.looper)
        cameraHandler?.post { startRecordingInternal(facing) }
    }

    private fun startRecordingInternal(facing: Int) {
        try {
            // Keep the CPU awake when the screen turns off / locks,
            // otherwise the recorder can stall on some phones.
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, "CalNyx:BlueFilmRecord"
                ).apply { acquire(10 * 60 * 1000L) }
            } catch (_: Exception) {}
            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = chooseCameraId(facing) ?: run {
                isStarting = false
                cleanup()
                stopSelf()
                return
            }
            val chars = manager.getCameraCharacteristics(cameraId)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val videoSize = chooseVideoSize(map, savedQuality(this))
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
            orientationHint = if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                (sensorOrientation + deviceDegrees) % 360
            } else {
                (sensorOrientation - deviceDegrees + 360) % 360
            }
            tmpFile = File(filesDir, "bluefilm/.tmp_${System.currentTimeMillis()}.mp4")
                .apply { parentFile?.mkdirs() }
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder = recorder
            recorder.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                // MIC: audio source BEFORE setOutputFormat,
                // encoder AFTER it. AAC 128 kbps / 44.1 kHz
                // into the same .mp4, so the video has sound.
                if (recordAudio) {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                }
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                if (recordAudio) {
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(128_000)
                    setAudioSamplingRate(44_100)
                }
                setVideoSize(videoSize.width, videoSize.height)
                setVideoFrameRate(30)
                setVideoEncodingBitRate(5_000_000)
                setOrientationHint(orientationHint)
                setOutputFile(tmpFile!!.absolutePath)
                prepare()
            }
            try {
                manager.openCamera(cameraId, cameraCallback, cameraHandler)
            } catch (e: CameraAccessException) {
                isStarting = false
                cleanup()
                stopSelf()
            } catch (e: SecurityException) {
                isStarting = false
                cleanup()
                stopSelf()
            }
        } catch (_: Exception) {
            isStarting = false
            cleanup()
            stopSelf()
        }
    }

    private fun createSession() {
        val recorder = mediaRecorder ?: return
        val device = cameraDevice ?: return
        try {
            @Suppress("DEPRECATION")
            device.createCaptureSession(listOf(recorder.surface), sessionCallback, cameraHandler)
        } catch (_: Exception) {
            isStarting = false
            stopRecording()
        }
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
            try { captureSession?.stopRepeating() } catch (_: Exception) {}
            try { captureSession?.close() } catch (_: Exception) {}
            captureSession = null
            try { mediaRecorder?.stop() } catch (_: Exception) {}
            try { mediaRecorder?.reset() } catch (_: Exception) {}
            try { mediaRecorder?.release() } catch (_: Exception) {}
            mediaRecorder = null
            try { cameraDevice?.close() } catch (_: Exception) {}
            cameraDevice = null
            val tmp = tmpFile
            if (tmp != null && tmp.exists() && tmp.length() > 0) {
                try {
                    val dest = BlueFilmStore.newVideoFile(this)
                    val tmpDest = File(dest.parentFile, dest.name + ".tmp")
                    var ok = false
                    for (attempt in 0 until 2) {
                        if (ok) break
                        try {
                            if (tmpDest.exists()) tmpDest.delete()
                            tmp.copyTo(tmpDest, overwrite = true)
                            // Plain copy: size must match exactly.
                            // Anything else means the copy was cut short.
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
            releaseWakeLock()
            try { cameraThread?.quitSafely() } catch (_: Exception) {}
            cameraThread = null
            cameraHandler = null
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
            stopSelf()
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun cleanup() {
        releaseWakeLock()
        try { captureSession?.close() } catch (_: Exception) {}
        captureSession = null
        try { mediaRecorder?.release() } catch (_: Exception) {}
        mediaRecorder = null
        try { cameraDevice?.close() } catch (_: Exception) {}
        cameraDevice = null
        // Never delete the tmp recording while a stop is in
        // progress - stopRecording() owns tmpFile then.
        if (!isRecording && !isStopping) {
            tmpFile?.delete()
            tmpFile = null
        }
        isRecording = false
        isStarting = false
        startedAtMillis = 0L
        try { cameraThread?.quitSafely() } catch (_: Exception) {}
        cameraThread = null
        cameraHandler = null
    }

    override fun onDestroy() {
        if (isRecording || isStarting) cleanup()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Video recording", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
            nm.createNotificationChannel(channel)
        }
        val stopIntent = Intent(this, BlueFilmService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle("CalNyx - Recording video")
            .setContentText("Camera is recording. Tap Stop to finish.")
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(0, "Stop", stopPending)
            .build()
    }
}
