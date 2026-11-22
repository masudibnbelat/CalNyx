// BALL PANEL TWO-WAY SYNC (2026-11-21) - REPLACE BallPanelActivity.kt.
// The Screen Record button now reads the live SpyScreenRecordService state:
// if a screen recording is already running (started from the ball or
// from inside Third Eye), the button shows Stop (inverted circle) and
// tapping it STOPS the recording instead of starting a second one.
// Nothing else changed: quick record still uses the saved Mic +
// Quality from Third Eye, camera recording still blocks it.
// FILE: app/src/main/java/com/calnyx/app/ball/BallPanelActivity.kt
// NEW FILE - Ball mini panel as a transparent Activity.
// Why an Activity, not a Service overlay: Jetpack Compose +
// Material 3 need a real Activity lifecycle. A Service overlay
// cannot host Compose reliably, and it cannot request runtime
// permissions (that is why Flashlight never worked - a Service
// can only toast, it cannot ask for Camera permission).
// The Activity is translucent (see manifest), so it looks like
// a small panel, not a full screen. It never appears in recents.
// Design: NO title, NO X button, NO labels, NO percent, NO
// Home. Center card. Colours and sizes come from the theme
// and the window (CalNyxTheme + % of minDim, 2026-11-20):
// the card is the theme's background, the circles are
// surfaceContainer. Icons only, in 3 rows of 3, in the
// user's order (the Screenshot icon is a vector drawn in
// this file, ScreenshotIcon below, traced from the user's
// image - no PNG file needed).
// (column by column):
// Column 1 = Power, Lock, Rotate;
// Column 2 = Ringtone sound, Call sound, Media sound;
// Column 3 = Screen Record, Flashlight, Screenshot.
// SCREEN RECORD (2026-11-20): the 9th item, a red record dot
// (top-right). Tap it and the system's own screen-capture
// dialog appears (Start now) - one tap there and the recording
// starts, with the mic + quality from the Blue Film settings.
// It uses the saved settings, so it is quick: no opening the
// app, no settings. While a camera recording runs, it refuses
// with a toast instead of starting a second recording.
// Animation is minimal on purpose: one quick fade + tiny
// scale-in (150ms), nothing else, so it stays light.
// Actions - all use the phone's OWN UI, no custom copies:
// - Screenshot: the phone's official screenshot via
//   AppLockService GLOBAL_ACTION_TAKE_SCREENSHOT (Android 9+).
//   Panel closes first (400ms) so it is not in the picture,
//   then the system shows its own screenshot preview.
// - Lock / Power: via AppLockService, need Accessibility on. If
//   it is off, the panel shows a banner with an "Open Settings"
//   button instead of failing silently with a toast.
// - Flashlight: CameraManager torch. Permission is requested
//   from THIS Activity, so it actually works now. When on, its
//   circle is inverted (white circle, black icon).
// - Sound (Media / Ringtone / Call): tapping an icon shows the
//   phone's OWN volume panel via AudioManager.adjustStreamVolume
//   with FLAG_SHOW_UI. No custom sliders - the system panel is
//   the real one, and the volume it sets is permanent, exactly
//   like the hardware buttons.
// Dismiss: tap the dim background, or Back. No X button.
// PANEL4: the panel runs in its OWN task (taskAffinity in the
// manifest), so closing it returns to the app you were in, never
// to CalNyx. The ball never opens the app. The panel also shows
// on the lock screen (setShowWhenLocked), like the ball itself
// (FLAG_SHOW_WHEN_LOCKED in the service).
// Design polish: 28dp
// corners, 60dp icon circles, divider between Tools and Sound.
// No grip handle bar on top any more (removed - the user asked
// for it gone), no extra bar - the panel is the UI.
// ROTATE 4-STATE (2026-11-22, LETTER + SYNC 2026-11-22): the
// Rotate icon is one button, four states, always the NEXT
// state from where it is now:
//   Auto -> Up 0 deg -> Down 180 deg -> Normal -> Auto ...
// The state is a BIG LETTER in the MIDDLE of the circle, so
// it reads at a glance (the small top-right badge was too
// small to see, and Up / Down showed no number at all):
// - Auto: "A" in the middle, circle inverted.
// - Up 0 deg: "U" in the middle, circle inverted.
// - Down 180 deg: "D" in the middle, circle inverted.
// - Normal: NO letter, circle NOT inverted - the plain
//   rotate icon, nothing active.
// NORMAL (user's definition): the phone's own default - the
// screen sits at 0 deg and stays there, but a video player
// can still force landscape while it plays. The old Normal
// (leave the system setting untouched) was WRONG: after Down
// the screen stayed at 180 deg. See OrientationService.
// THE PANEL STAYS OPEN after a rotate tap (2026-11-22): the
// letter updates in place, so the user can tap through the
// whole cycle without reopening the ball every time - the
// old 300ms close is what made it feel like "too many
// clicks". The toast still names the new state. After every
// tap the guard is refreshed, so it tracks the new mode.
// It uses OrientationService (the same overlay-enforced
// rotation as the Hardcore screen), so Up / Down hold outside
// the app too, and both places read the same saved mode.
package com.calnyx.app.ball

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.calnyx.app.spy.SpyService
import com.calnyx.app.spy.SpyScreenRecordService
import com.calnyx.app.ball.FloatingBallService
import com.calnyx.app.moan.MoanControlPrefs
import com.calnyx.app.applock.AppLockManager
import com.calnyx.app.applock.AppLockService
import com.calnyx.app.theme.CalNyxRed
import com.calnyx.app.theme.CalNyxTheme
import com.calnyx.app.hardcore.OrientationGuard
import com.calnyx.app.hardcore.OrientationService
import com.calnyx.app.navigation.Screen

class BallPanelActivity : ComponentActivity() {

    private var torchOn by mutableStateOf(false)
    // The back camera with a flash, found once and reused.
    private var flashCameraId: String? = null
    // The torch's real state, from the system. Android has no
    // "is the torch on" query, so the panel listens for changes
    // (the system also reports the current state when the
    // callback is registered). The panel itself never turns
    // the torch off - it stays on until the user turns it off
    // by hand.
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            try {
                if (cameraId == flashCameraId) {
                    torchOn = enabled
                    MoanControlPrefs.setTorchOn(this@BallPanelActivity, enabled)
                }
            } catch (_: Exception) {
            }
        }
    }
    private var accessibilityOn by mutableStateOf(false)
    // ROTATE 4-STATE (2026-11-22): the current rotate mode, one
    // of "auto" | "up" | "down" | "normal". Read from the saved
    // mode on open, so the icon shows the real state even if
    // it was last changed from the Hardcore screen.
    private var rotateMode by mutableStateOf("auto")
    // SCREEN RECORD SYNC (2026-11-21): true while a screen recording
    // runs, wherever it was started (ball panel or Third Eye screen).
    // The panel reads the live service state on open / resume, so a
    // recording started from inside Third Eye shows here as Stop.
    private var isScreenRecording by mutableStateOf(false)
    private var isScreenStarting by mutableStateOf(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            toggleTorch()
        } else {
            Toast.makeText(
                this,
                "Allow Camera permission for flashlight.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // SCREEN RECORD (2026-11-20): quick record from the ball.
    // The system asks once per recording, with its own dialog
    // (Android's rule - the "Start now" step cannot be skipped).
    // OK = the service starts with the granted token, using the
    // mic + quality saved in the Blue Film settings; the panel
    // closes. Cancel = the panel stays open.
    private val screenRecordLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            SpyScreenRecordService.start(
                this,
                result.resultCode,
                result.data!!,
                SpyService.savedMicEnabled(this),
                SpyService.savedQuality(this)
            )
            finish()
        } else {
            Toast.makeText(
                this,
                "Screen recording was not allowed.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Show on the lock screen too, like the ball.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        }
        accessibilityOn = AppLockManager.isAccessibilityEnabled(this)
        rotateMode = OrientationService.getSavedMode(this)
            .takeIf { it == "auto" || it == "up" || it == "down" || it == "normal" }
            ?: "auto"
        isScreenRecording = SpyScreenRecordService.isRecording
        isScreenStarting = SpyScreenRecordService.isStarting
        // Torch: show the current state when the panel opens.
        // The saved state is the starting point; the torch
        // callback below corrects it from the system if the
        // torch was changed elsewhere (e.g. Quick Settings).
        torchOn = MoanControlPrefs.isTorchOn(this)
        try {
            val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            flashCameraId = findFlashCamera(cm)
            cm.registerTorchCallback(torchCallback, mainHandler)
        } catch (_: Exception) {
        }
        setContent {
            // THEME VARIABLES + % SIZES (2026-11-20): the panel
            // used to carry its own hardcoded dark/light scheme
            // (#0E0E0E / white) and fixed dp sizes. Now it reads
            // the app theme (CalNyxTheme) and sizes itself as a
            // % of the window, so it follows the theme and fits
            // any screen. The card is the theme's background
            // (dark #1F1F1F - the theme's darkest grey; the
            // panel's exact #0E0E0E is not in the palette), the
            // circles are surfaceContainer. The colour names
            // (cardBg / onCard / thumbBg) are kept, so the
            // button call sites below do not change.
            CalNyxTheme {
                val colorScheme = MaterialTheme.colorScheme
                val cardBg = colorScheme.background
                val onCard = colorScheme.onBackground
                val thumbBg = colorScheme.surfaceContainer
                // Minimal animation: one quick fade + tiny scale-in.
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(150)) +
                        scaleIn(
                            initialScale = 0.97f,
                            animationSpec = androidx.compose.animation.core.tween(150)
                        )
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { finish() },
                        contentAlignment = Alignment.Center
                    ) {
                        val minDim = minOf(maxWidth, maxHeight)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth(0.82f)
                                // Consume taps on the card so they do
                                // not fall through to the dim background
                                // (which closes the panel).
                                .clickable {},

                            shape = RoundedCornerShape(minDim * 0.071f),
                            color = cardBg,
                            contentColor = onCard,
                            // The card colour is the picture's
                            // colour: background + the tonal tint.
                            // A Surface tints its colour with the
                            // theme's surface tint, and that tint is
                            // what makes this card the lighter grey
                            // in the user's screenshot (measured
                            // #333333) instead of flat background.
                            // The toolbar's card is the same Surface
                            // with the same tint, so the two windows
                            // cannot differ. NO shadow (the user
                            // wants the flat look from the picture):
                            // the shadow is 0, only the tint stays.
                            tonalElevation = minDim * 0.02f,
                            shadowElevation = 0.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    horizontal = minDim * 0.031f,
                                    vertical = minDim * 0.031f
                                ),
                                verticalArrangement = Arrangement.spacedBy(minDim * 0.010f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Icon-only grid, 3 rows of 3. No titles,
                                // no percent, no Home. The user's order
                                // is column by column: Column 1 = Power,
                                // Lock, Rotate; Column 2 = Ringtone, Call,
                                // Media; Column 3 = Screen Record (red dot,
                                // top-right), Flashlight, Screenshot.
                                // Ringtone / Media / Call open the
                                // phone's own volume panel.
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    ActionButton(
                                        label = "Power",
                                        icon = Icons.Default.PowerSettingsNew,
                                        thumbBg = thumbBg,
                                        onCard = onCard,
                                        cardBg = cardBg,
                                        minDim = minDim,
                                        onClick = { doPower() }
                                    )
                                    ActionButton(
                                        label = "Ringtone",
                                        icon = Icons.Default.Notifications,
                                        thumbBg = thumbBg,
                                        onCard = onCard,
                                        cardBg = cardBg,
                                        minDim = minDim,
                                        onClick = { showSystemVolume(AudioManager.STREAM_RING) }
                                    )
                                    ActionButton(
                                        label = "Screen Record",
                                        icon = if (isScreenRecording || isScreenStarting) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                                        thumbBg = thumbBg,
                                        onCard = onCard,
                                        cardBg = cardBg,
                                        minDim = minDim,
                                        active = isScreenRecording || isScreenStarting,
                                        tint = if (isScreenRecording || isScreenStarting) null else CalNyxRed,
                                        onClick = { doScreenRecord() }
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    ActionButton(
                                        label = "Lock",
                                        icon = Icons.Default.Lock,
                                        thumbBg = thumbBg,
                                        onCard = onCard,
                                        cardBg = cardBg,
                                        minDim = minDim,
                                        onClick = { doLock() }
                                    )
                                    ActionButton(
                                        label = "Call",
                                        icon = Icons.Default.Call,
                                        thumbBg = thumbBg,
                                        onCard = onCard,
                                        cardBg = cardBg,
                                        minDim = minDim,
                                        onClick = { showSystemVolume(AudioManager.STREAM_VOICE_CALL) }
                                    )
                                    ActionButton(
                                        label = "Flashlight",
                                        icon = if (torchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                                        thumbBg = thumbBg,
                                        onCard = onCard,
                                        cardBg = cardBg,
                                        minDim = minDim,
                                        active = torchOn,
                                        onClick = { onFlashlightClick() }
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    ActionButton(
                                        label = "Rotate",
                                        icon = Icons.Default.ScreenRotation,
                                        thumbBg = thumbBg,
                                        onCard = onCard,
                                        cardBg = cardBg,
                                        minDim = minDim,
                                        // Auto + Up + Down look "on"
                                        // (inverted circle) with a
                                        // big letter in the middle:
                                        // A = Auto, U = Up, D =
                                        // Down. Normal looks "off"
                                        // - plain rotate icon, no
                                        // letter, circle not
                                        // inverted. The letter is
                                        // the state, so it must be
                                        // big enough to read.
                                        active = rotateMode != "normal",
                                        letter = when (rotateMode) {
                                            "auto" -> "A"
                                            "up" -> "U"
                                            "down" -> "D"
                                            else -> null // "normal"
                                        },
                                        onClick = { doRotateToggle() }
                                    )
                                    ActionButton(
                                        label = "Media",
                                        icon = Icons.Default.MusicNote,
                                        thumbBg = thumbBg,
                                        onCard = onCard,
                                        cardBg = cardBg,
                                        minDim = minDim,
                                        onClick = { showSystemVolume(AudioManager.STREAM_MUSIC) }
                                    )
                                    ActionButton(
                                        label = "Screenshot",
                                        icon = ScreenshotIcon,
                                        thumbBg = thumbBg,
                                        onCard = onCard,
                                        cardBg = cardBg,
                                        minDim = minDim,
                                        onClick = { doScreenshot() }
                                    )
                                }
                                // Accessibility banner - only when off.
                                // Lock, Power and Screenshot all need it.
                                if (!accessibilityOn) {
                                    Spacer(modifier = Modifier.height(minDim * 0.031f))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(minDim * 0.031f))
                                            .background(thumbBg)
                                            .padding(minDim * 0.031f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.WarningAmber,
                                            contentDescription = null,
                                            modifier = Modifier.size(minDim * 0.056f),
                                            tint = onCard
                                        )
                                        Spacer(modifier = Modifier.width(minDim * 0.026f))
                                        Text(
                                            text = "Lock, Power and Screenshot need Accessibility.",
                                            fontSize = (minDim * 0.033f).value.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(minDim * 0.020f))
                                    Button(
                                        onClick = { openAccessibilitySettings() },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(minDim * 0.031f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = onCard,
                                            contentColor = cardBg
                                        )
                                    ) {
                                        Text("Open Settings", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // User may have just enabled Accessibility in Settings.
        accessibilityOn = AppLockManager.isAccessibilityEnabled(this)
        rotateMode = OrientationService.getSavedMode(this)
            .takeIf { it == "auto" || it == "up" || it == "down" || it == "normal" }
            ?: "auto"
        isScreenRecording = SpyScreenRecordService.isRecording
        isScreenStarting = SpyScreenRecordService.isStarting
    }

    override fun onDestroy() {
        super.onDestroy()
        // The torch is NOT touched here on purpose: closing
        // the panel must never turn it off. It stays on until
        // the user turns it off by hand (this panel's toggle
        // or the system's own torch tile). Only the callback
        // is unregistered.
        try {
            val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.unregisterTorchCallback(torchCallback)
        } catch (_: Exception) {
        }
    }

    private fun doScreenshot() {
        // Hide the ball first so it is not in the picture
        // either - it comes back by itself after 2 seconds.
        // (No FLAG_SECURE: it made the system refuse EVERY
        // screenshot on this phone.) Close the panel first so
        // it is not in the picture, then use the phone's
        // OFFICIAL screenshot.
        FloatingBallService.hide(this, 2000)
        finish()
        mainHandler.postDelayed({
            if (!AppLockService.requestOfficialScreenshot()) {
                Toast.makeText(
                    this,
                    "Turn on Accessibility for CalNyx first.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, 400)
    }

    private fun doScreenRecord() {
        // TWO-WAY SYNC (2026-11-21): if a screen recording is already
        // running (started from the ball, or from inside Third Eye),
        // this same button STOPS it instead of starting a second one.
        // The panel closes so the user sees the recording stop.
        if (SpyScreenRecordService.isRecording || SpyScreenRecordService.isStarting) {
            SpyScreenRecordService.stop(this)
            finish()
            return
        }
        // Quick record: no opening the app, no settings - the
        // mic + quality come from the Third Eye settings as
        // saved. Two recordings at once would be a mess, so
        // while a camera recording runs this refuses.
        if (SpyService.isRecording || SpyService.isStarting) {
            Toast.makeText(
                this,
                "Stop the camera recording first.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val manager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            screenRecordLauncher.launch(manager.createScreenCaptureIntent())
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Screen recording is not available on this phone.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun doRotateToggle() {
        // ROTATE 4-STATE (2026-11-22): one icon, four states,
        // always the NEXT one from where it is now:
        // Auto -> Up (0 deg) -> Down (180 deg) -> Normal -> Auto.
        // Uses OrientationService, the same overlay-enforced
        // rotation as the Hardcore screen, so Up / Down hold
        // outside the app too. The panel STAYS OPEN: the letter
        // updates in place (rotateMode is Compose state), so
        // the user can tap through the whole cycle without
        // reopening the ball. The toast names the new state.
        // The guard is refreshed so it tracks the new mode -
        // without this it would keep watching the old one.
        val next = when (rotateMode) {
            "auto" -> "up"
            "up" -> "down"
            "down" -> "normal"
            else -> "auto" // "normal" -> "auto"
        }
        if (OrientationService.apply(this, next)) {
            rotateMode = next
            try {
                OrientationGuard.refresh(this)
            } catch (_: Exception) {
            }
            Toast.makeText(
                this,
                when (next) {
                    "auto" -> "Auto rotation"
                    "up" -> "Up 0 deg"
                    "down" -> "Down 180 deg"
                    else -> "Normal"
                },
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                this,
                "Allow overlay permission or Modify system settings for CalNyx.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun doLock() {
        if (AppLockService.requestLockScreen()) {
            finish()
        } else {
            accessibilityOn = AppLockManager.isAccessibilityEnabled(this)
            Toast.makeText(
                this,
                "Turn on Accessibility for CalNyx first.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun doPower() {
        if (AppLockService.requestPowerDialog()) {
            finish()
        } else {
            accessibilityOn = AppLockManager.isAccessibilityEnabled(this)
            Toast.makeText(
                this,
                "Turn on Accessibility for CalNyx first.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun onFlashlightClick() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        toggleTorch()
    }

    private fun toggleTorch() {
        try {
            val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = flashCameraId ?: findFlashCamera(cm)?.also { flashCameraId = it } ?: run {
                Toast.makeText(this, "This phone has no flash.", Toast.LENGTH_SHORT).show()
                return
            }
            torchOn = !torchOn
            cm.setTorchMode(id, torchOn)
            MoanControlPrefs.setTorchOn(this, torchOn)
            Toast.makeText(
                this,
                if (torchOn) "Flashlight on" else "Flashlight off",
                Toast.LENGTH_SHORT
            ).show()
        } catch (_: Exception) {
            torchOn = false
            MoanControlPrefs.setTorchOn(this, false)
            Toast.makeText(
                this,
                "Flashlight did not work on this phone.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun findFlashCamera(cm: CameraManager): String? {
        return try {
            cm.cameraIdList.firstOrNull { camId ->
                try {
                    cm.getCameraCharacteristics(camId)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } catch (_: Exception) {
                    false
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun showSystemVolume(stream: Int) {
        // Show the phone's OWN volume panel. The volume it sets
        // is permanent, exactly like the hardware buttons.
        try {
            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.adjustStreamVolume(
                stream,
                AudioManager.ADJUST_SAME,
                AudioManager.FLAG_SHOW_UI
            )
        } catch (_: Exception) {
        }
        // Let the system panel take over, then close this panel.
        mainHandler.postDelayed({ finish() }, 300)
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {
        }
    }
}

// SCREENSHOT ICON (2026-11-20): the user's own icon, redrawn
// as a vector (SVG-style) so no PNG file is needed. Traced
// from his image, measured piece by piece: a dashed capture
// frame - a + at top-left and bottom-right, a rounded corner
// at top-right and bottom-left, and a dash in the middle of
// each side. One stroked path, round caps, stroke 1.8. It is
// drawn BLACK, like every Material icon - the panel's tint
// turns it WHITE on a dark background and dark on a light
// one, so it stays visible either way.
private val ScreenshotIcon: ImageVector = ImageVector.Builder(
    name = "Screenshot",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).addPath(
    pathData = listOf(
        // Top-left + : horizontal, then vertical.
        PathNode.MoveTo(0.9f, 4.0f),
        PathNode.LineTo(7.0f, 4.0f),
        PathNode.MoveTo(4.0f, 0.9f),
        PathNode.LineTo(4.0f, 7.0f),
        // Top dash.
        PathNode.MoveTo(10.75f, 3.75f),
        PathNode.LineTo(13.25f, 3.75f),
        // Top-right rounded corner.
        PathNode.MoveTo(16.9f, 3.75f),
        PathNode.LineTo(18.9f, 3.75f),
        PathNode.QuadTo(20.2f, 3.75f, 20.2f, 5.05f),
        PathNode.LineTo(20.2f, 7.0f),
        // Left dash.
        PathNode.MoveTo(3.7f, 10.8f),
        PathNode.LineTo(3.7f, 13.2f),
        // Right dash.
        PathNode.MoveTo(20.2f, 10.8f),
        PathNode.LineTo(20.2f, 13.2f),
        // Bottom-left rounded corner.
        PathNode.MoveTo(3.7f, 17.0f),
        PathNode.LineTo(3.7f, 18.9f),
        PathNode.QuadTo(3.7f, 20.2f, 5.0f, 20.2f),
        PathNode.LineTo(7.0f, 20.2f),
        // Bottom dash.
        PathNode.MoveTo(10.75f, 20.2f),
        PathNode.LineTo(13.25f, 20.2f),
        // Bottom-right + : horizontal, then vertical.
        PathNode.MoveTo(17.0f, 20.0f),
        PathNode.LineTo(23.1f, 20.0f),
        PathNode.MoveTo(20.0f, 17.0f),
        PathNode.LineTo(20.0f, 23.1f)
    ),
    stroke = SolidColor(Color.Black),
    strokeLineWidth = 1.8f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round
).build()

@androidx.compose.runtime.Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    thumbBg: Color,
    onCard: Color,
    cardBg: Color,
    minDim: Dp,
    active: Boolean = false,
    rotated: Boolean = false,
    // Fixed icon colour, when set (the red record dot).
    // Otherwise the icon follows the active state.
    tint: Color? = null,
    // Small badge letter on the top-right of the circle.
    // Kept for other buttons; the Rotate button no longer
    // uses it - its state is the big letter in the middle.
    badge: String? = null,
    // Big letter in the MIDDLE of the circle, replacing the
    // icon: "A" = Auto, "U" = Up, "D" = Down. Null = show
    // the icon as usual (Normal: plain rotate icon).
    letter: String? = null,
    onClick: () -> Unit
) {
    // Icon only - no label text. When active (flashlight on,
    // or rotate Down), the circle is inverted so the state is
    // visible. When rotated, the icon itself is turned 180 deg.
    // Sizes are % of the window (56dp circle / 36dp icon / 4dp
    // padding on the user's phone), like the toolbar. The badge
    // is % too, so it stays small on any screen.
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable { onClick() }
            .padding(minDim * 0.010f),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(minDim * 0.143f)
                .clip(CircleShape)
                .background(if (active) onCard else thumbBg),
            contentAlignment = Alignment.Center
        ) {
            if (letter != null) {
                // The letter IS the icon now: big, bold, in
                // the middle of the circle, so the state
                // reads at a glance. Dark on the inverted
                // circle, light on the plain one.
                Text(
                    text = letter,
                    fontSize = (minDim * 0.075f).value.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (active) cardBg else onCard
                )
            } else {
                Icon(
                    icon,
                    contentDescription = label,
                    modifier = Modifier.size(minDim * 0.092f).rotate(if (rotated) 180f else 0f),
                    tint = tint ?: if (active) cardBg else onCard
                )
            }
        }
        // Badge: a tiny circle on the top-right edge of the
        // main circle, card colour with theme text, so it
        // reads on both the inverted and the plain circle.
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(minDim * 0.048f)
                    .clip(CircleShape)
                    .background(cardBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge,
                    fontSize = (minDim * 0.030f).value.sp,
                    fontWeight = FontWeight.Bold,
                    color = onCard
                )
            }
        }
    }
}
