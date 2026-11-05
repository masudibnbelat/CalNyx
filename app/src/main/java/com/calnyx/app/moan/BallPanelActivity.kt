// FILE: app/src/main/java/com/calnyx/app/moan/BallPanelActivity.kt
// NEW FILE - Ball mini panel as a transparent Activity.
// Why an Activity, not a Service overlay: Jetpack Compose +
// Material 3 need a real Activity lifecycle. A Service overlay
// cannot host Compose reliably, and it cannot request runtime
// permissions (that is why Flashlight never worked - a Service
// can only toast, it cannot ask for Camera permission).
// The Activity is translucent (see manifest), so it looks like
// a small panel, not a full screen. It never appears in recents.
// Design: NO title, NO X button, NO labels, NO percent, NO
// Home. Center card, max 320dp. Dark mode = black card
// (0xFF0E0E0E), light = white. Icons only, 36dp Material icons
// in 60dp circles, in 2 rows, in the user's order:
// Row 1 = Call sound, Power, Lock, Flashlight;
// Row 2 = Ringtone sound, Media sound, Rotate, Screenshot.
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
// (FLAG_SHOW_WHEN_LOCKED in the service). Design polish: 28dp
// corners, 60dp icon circles, divider between Tools and Sound.
// No grip handle bar on top any more (removed - the user asked
// for it gone), no extra bar - the panel is the UI.
// ROTATE: Row 2, 4th icon is now a Rotate toggle (replaces the
// Settings gear). One icon only: tap = 180 deg (Down), tap
// again = 0 deg (Up). It uses OrientationService (the same
// overlay-enforced rotation as the Hardcore screen), so it
// holds outside the app too. When Down is active the icon is
// upside down and the circle is inverted, so the state is
// visible at a glance. The panel closes after the tap so the
// user sees the screen rotate.
package com.calnyx.app.moan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.calnyx.app.applock.AppLockManager
import com.calnyx.app.applock.AppLockService

class BallPanelActivity : ComponentActivity() {

    private var torchOn by mutableStateOf(false)
    private var accessibilityOn by mutableStateOf(false)
    // Rotate toggle state: true = Down (180 deg) is active.
    private var rotateDown by mutableStateOf(false)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Show on the lock screen too, like the ball.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        }
        accessibilityOn = AppLockManager.isAccessibilityEnabled(this)
        rotateDown = OrientationService.getSavedMode(this) == "down"
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val cardBg = if (darkTheme) Color(0xFF0E0E0E) else Color(0xFFFFFFFF)
            val onCard = if (darkTheme) Color(0xFFF6F4EF) else Color(0xFF0E0E0E)
            val thumbBg = if (darkTheme) Color(0xFF262626) else Color(0xFFEFEDE8)
            val cardBorder = if (darkTheme) Color(0xFF2B2B2B) else Color(0xFFE9E6DF)
            MaterialTheme(
                colorScheme = if (darkTheme) darkColorScheme(
                    background = Color(0xFF0E0E0E),
                    surface = Color(0xFF0E0E0E),
                    onSurface = Color(0xFFF6F4EF),
                    primary = Color(0xFFF6F4EF),
                    onPrimary = Color(0xFF0E0E0E)
                ) else lightColorScheme(
                    background = Color(0xFFF6F4EF),
                    surface = Color(0xFFFFFFFF),
                    onSurface = Color(0xFF0E0E0E),
                    primary = Color(0xFF0E0E0E),
                    onPrimary = Color(0xFFF6F4EF)
                )
            ) {
                // Minimal animation: one quick fade + tiny scale-in.
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(150)) +
                        scaleIn(
                            initialScale = 0.97f,
                            animationSpec = androidx.compose.animation.core.tween(150)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { finish() },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .widthIn(max = 320.dp)
                                .fillMaxWidth(0.88f)
                                // Consume taps on the card so they do
                                // not fall through to the dim background
                                // (which closes the panel).
                                .clickable {},

                            shape = RoundedCornerShape(28.dp),
                            color = cardBg,
                            contentColor = onCard,
                            tonalElevation = 8.dp,
                            shadowElevation = 8.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Icon-only grid, 2 rows. No titles, no
                                // percent, no Home. Row 1 = tools,
                                // Row 2 = sound (tap opens the phone's
                                // own volume panel).
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    ActionButton(
                                        label = "Call",
                                        icon = Icons.Default.Call,
                                        thumbBg = thumbBg,
                                        onCard = onCard,
                                        cardBg = cardBg,
                                        onClick = { showSystemVolume(AudioManager.STREAM_VOICE_CALL) }
                                    )
                                    ActionButton(
                                        label = "Power",
                                        icon = Icons.Default.PowerSettingsNew,
                                        thumbBg = thumbBg,
                                        onCard = onCard,
                                        cardBg = cardBg,
                                        onClick = { doPower() }
                                    )
                                    ActionButton(
                                        label = "Lock",
                                        icon = Icons.Default.Lock,
                                        thumbBg = thumbBg,
                                        onCard = onCard,
                                        cardBg = cardBg,
                                        onClick = { doLock() }
                                    )
                                    ActionButton(
                                        label = "Flashlight",
                                        icon = if (torchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                                        thumbBg = thumbBg,
                                        onCard = onCard,
                                        cardBg = cardBg,
                                        active = torchOn,
                                        onClick = { onFlashlightClick() }
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    ActionButton(
                                        label = "Ringtone",
                                        icon = Icons.Default.Notifications,
                                        thumbBg = thumbBg,
                                        onCard = onCard,
                                        cardBg = cardBg,
                                        onClick = { showSystemVolume(AudioManager.STREAM_RING) }
                                    )
                                    ActionButton(
                                        label = "Media",
                                        icon = Icons.Default.MusicNote,
                                        thumbBg = thumbBg,
                                        onCard = onCard,
                                        cardBg = cardBg,
                                        onClick = { showSystemVolume(AudioManager.STREAM_MUSIC) }
                                    )
                                    ActionButton(
                                        label = "Rotate",
                                        icon = Icons.Default.ScreenRotation,
                                        thumbBg = thumbBg,
                                        onCard = onCard,
                                        cardBg = cardBg,
                                        active = rotateDown,
                                        rotated = rotateDown,
                                        onClick = { doRotateToggle() }
                                    )
                                    ActionButton(
                                        label = "Screenshot",
                                        icon = Icons.Default.CameraAlt,
                                        thumbBg = thumbBg,
                                        onCard = onCard,
                                        cardBg = cardBg,
                                        onClick = { doScreenshot() }
                                    )
                                }
                                // Accessibility banner - only when off.
                                // Lock, Power and Screenshot all need it.
                                if (!accessibilityOn) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(thumbBg)
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.WarningAmber,
                                            contentDescription = null,
                                            modifier = Modifier.size(22.dp),
                                            tint = onCard
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Lock, Power and Screenshot need Accessibility.",
                                            fontSize = 13.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { openAccessibilitySettings() },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
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
    }

    override fun onDestroy() {
        super.onDestroy()
        // Never leave the torch on if the panel is gone.
        if (torchOn) {
            try {
                val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val id = findFlashCamera(cm)
                if (id != null) cm.setTorchMode(id, false)
            } catch (_: Exception) {
            }
            torchOn = false
        }
    }

    private fun doScreenshot() {
        // Close the panel first so it is not in the picture,
        // then use the phone's OFFICIAL screenshot.
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

    private fun doRotateToggle() {
        // One icon, two states: Down (180 deg) <-> Up (0 deg).
        // Uses OrientationService, the same overlay-enforced
        // rotation as the Hardcore screen, so it holds outside
        // the app too. No new rotate feature - this just calls
        // the existing one.
        val next = if (rotateDown) "up" else "down"
        if (OrientationService.apply(this, next)) {
            rotateDown = (next == "down")
            // Close the panel so the user sees the screen rotate.
            mainHandler.postDelayed({ finish() }, 300)
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
            val id = findFlashCamera(cm) ?: run {
                Toast.makeText(this, "This phone has no flash.", Toast.LENGTH_SHORT).show()
                return
            }
            torchOn = !torchOn
            cm.setTorchMode(id, torchOn)
            Toast.makeText(
                this,
                if (torchOn) "Flashlight on" else "Flashlight off",
                Toast.LENGTH_SHORT
            ).show()
        } catch (_: Exception) {
            torchOn = false
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

@androidx.compose.runtime.Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    thumbBg: Color,
    onCard: Color,
    cardBg: Color,
    active: Boolean = false,
    rotated: Boolean = false,
    onClick: () -> Unit
) {
    // Icon only - no label text. When active (flashlight on,
    // or rotate Down), the circle is inverted so the state is
    // visible. When rotated, the icon itself is turned 180 deg.
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable { onClick() }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (active) onCard else thumbBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(36.dp).rotate(if (rotated) 180f else 0f),
                tint = if (active) cardBg else onCard
            )
        }
    }
}
