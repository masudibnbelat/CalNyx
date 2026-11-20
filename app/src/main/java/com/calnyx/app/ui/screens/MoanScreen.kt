// FILE: app/src/main/java/com/calnyx/app/ui/screens/MoanScreen.kt
// MOAN (SOUND) - SPLIT VERSION (2026-11-20).
//  Split out of MoanControlScreen.kt (2026-11-20): Hardcore
//  (Hardware) in HardcoreScreen.kt, Moan (Sound) here, and
//  Network (Network Switcher + Open in WhatsApp) in
//  NetworkScreen.kt. DELETE MoanControlScreen.kt from the
//  project, or the HardcoreScreen / MoanScreen declarations
//  will exist twice and the build will fail.
//  In here: Ringer Profile (Sound / Vibrate / Silent) + all
//  audio streams (Media / Ringtone / Alarm / Call), real-time.
// THEME VARIABLES (2026-11-20): every colour comes from the
//  theme (Color.kt) through MaterialTheme.colorScheme - see the
//  palette lines in MoanScreen / SoundTab below. Status-bar
//  fix and drawer swipe are unchanged.

package com.calnyx.app.ui.screens

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class StreamRow(
    val stream: Int,
    val title: String,
    val icon: ImageVector
)

@Composable
fun MoanScreen(
    darkTheme: Boolean,
    onOpenDrawer: () -> Unit = {}
) {
    // All colours come from the theme (Color.kt) - see the note at
    // the top of this file. darkTheme is still used below for the
    // status / navigation bar icon colours.
    val colorScheme = MaterialTheme.colorScheme
    val pageBg = colorScheme.background
    val cardBg = colorScheme.surfaceContainer
    val cardBorder = colorScheme.outline
    val onPageColor = colorScheme.onBackground
    val thumbBg = colorScheme.surfaceVariant
    var note by remember { mutableStateOf<String?>(null) }
    // STATUS BAR + NAVIGATION BAR match the page background: the
    // top strip (clock, battery, WiFi, network) must be the same
    // colour as the page below it, in both themes — it was showing
    // the system default (white in light mode) instead of the page
    // colour. Same fix as Blue Film V8 / Moan V2.
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            window.statusBarColor = pageBg.toArgb()
            window.navigationBarColor = pageBg.toArgb()
            val controller = WindowInsetsControllerCompat(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    // DRAWER SWIPE (universal rule, 2026-11-06): a LEFTWARD
    // drag anywhere on this screen opens the app drawer.
    // Rightward drags are not consumed here. A tap (no real
    // movement) does nothing, so buttons still work.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val windowW = maxWidth
        val windowH = maxHeight
        val minDim = minOf(windowW, windowH)
        val drawerSwipe = Modifier.pointerInput(minDim) {
            val thresholdPx = (minDim * 0.067f).toPx()
        var totalDx = 0f
        var done = false
        detectHorizontalDragGestures(
            onDragStart = { totalDx = 0f; done = false },
            onDragEnd = { totalDx = 0f; done = false },
            onDragCancel = { totalDx = 0f; done = false },
            onHorizontalDrag = { change, dragAmount ->
                if (done) return@detectHorizontalDragGestures
                totalDx += dragAmount
                if (totalDx < -thresholdPx) {
                    done = true
                    onOpenDrawer()
                    change.consume()
                }
            }
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = pageBg, contentColor = onPageColor) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().then(drawerSwipe)) {
            if (note != null) {
                Text(
                    text = note ?: "",
                    fontSize = (minDim * 0.036f).value.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = windowW * 0.044f, vertical = windowH * 0.01f)
                )
            }
            SoundTab(
                cardBg = cardBg,
                cardBorder = cardBorder,
                onPageColor = onPageColor,
                thumbBg = thumbBg,
                minDim = minDim,
                onNote = { note = it }
            )
        }
    }
    }
}

@Composable
private fun SoundTab(
    cardBg: Color,
    cardBorder: Color,
    onPageColor: Color,
    thumbBg: Color,
    minDim: Dp,
    onNote: (String?) -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val audio = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val streams = remember {
        listOf(
            StreamRow(AudioManager.STREAM_MUSIC, "Media", Icons.Default.MusicNote),
            StreamRow(AudioManager.STREAM_RING, "Ringtone", Icons.Default.VolumeUp),
            StreamRow(AudioManager.STREAM_ALARM, "Alarm", Icons.Default.Alarm),
            StreamRow(AudioManager.STREAM_VOICE_CALL, "Call", Icons.Default.Call)
        )
    }
    var volumes by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var ringerMode by remember { mutableStateOf(audio.ringerMode) }
    val scroll = rememberScrollState()

    LaunchedEffect(Unit) {
        val map = mutableMapOf<Int, Int>()
        streams.forEach { row ->
            try {
                map[row.stream] = audio.getStreamVolume(row.stream)
            } catch (_: Exception) {
            }
        }
        volumes = map
        try {
            ringerMode = audio.ringerMode
        } catch (_: Exception) {
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(start = minDim * 0.033f, end = minDim * 0.033f, bottom = minDim * 0.033f),
        verticalArrangement = Arrangement.spacedBy(minDim * 0.022f)
    ) {
        // HEADER - big title with icon, like Blue Film.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(minDim * 0.144f).clip(CircleShape).background(thumbBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(minDim * 0.078f), tint = onPageColor)
            }
            Spacer(modifier = Modifier.width(minDim * 0.033f))
            Text("Moan", fontWeight = FontWeight.Bold, fontSize = (minDim * 0.078f).value.sp, color = onPageColor)
        }
        Text("Ringer Profile", fontSize = (minDim * 0.033f).value.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(minDim * 0.022f), modifier = Modifier.fillMaxWidth()) {
            listOf(
                AudioManager.RINGER_MODE_NORMAL to "Sound" to Icons.Default.VolumeUp,
                AudioManager.RINGER_MODE_VIBRATE to "Vibrate" to Icons.Default.Vibration,
                AudioManager.RINGER_MODE_SILENT to "Silent" to Icons.Default.VolumeOff
            ).forEach { (pair, icon) ->
                val (mode, label) = pair
                val selected = ringerMode == mode
                Box(
                    modifier = Modifier.weight(1f)
                        .clip(RoundedCornerShape(minDim * 0.033f))
                        .background(if (selected) onPageColor else thumbBg)
                        .clickable {
                            try {
                                if (mode == AudioManager.RINGER_MODE_SILENT) {
                                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                                        !nm.isNotificationPolicyAccessGranted
                                    ) {
                                        onNote("Allow Do Not Disturb access for CalNyx first to use Silent.")
                                        try {
                                            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                        }
                                        return@clickable
                                    }
                                }
                                audio.ringerMode = mode
                                ringerMode = mode
                                onNote(null)
                            } catch (_: Exception) {
                                onNote("Ringer mode could not be changed on this phone.")
                            }
                        }
                        .padding(vertical = minDim * 0.028f),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(minDim * 0.011f)) {
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(minDim * 0.056f),
                            tint = if (selected) colorScheme.background else onPageColor
                        )
                        Text(
                            label,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) colorScheme.background else onPageColor,
                            fontSize = (minDim * 0.039f).value.sp
                        )
                    }
                }
            }
        }
        Text("Audio Streams", fontSize = (minDim * 0.033f).value.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurfaceVariant)
        streams.forEach { row ->
            val max = try {
                audio.getStreamMaxVolume(row.stream)
            } catch (_: Exception) {
                15
            }.coerceAtLeast(1)
            val current = volumes[row.stream] ?: 0
            val percent = if (max > 0) (current * 100 / max) else 0
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(minDim * 0.044f))
                    .background(cardBg)
                    .border(1.dp, cardBorder, RoundedCornerShape(minDim * 0.044f))
                    .padding(minDim * 0.033f),
                verticalArrangement = Arrangement.spacedBy(minDim * 0.022f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(minDim * 0.111f).clip(CircleShape).background(thumbBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(row.icon, contentDescription = null, modifier = Modifier.size(minDim * 0.061f))
                    }
                    Spacer(modifier = Modifier.width(minDim * 0.033f))
                    Text(row.title, fontWeight = FontWeight.SemiBold, fontSize = (minDim * 0.044f).value.sp, modifier = Modifier.weight(1f))
                    Text("$percent%", fontWeight = FontWeight.SemiBold, fontSize = (minDim * 0.042f).value.sp)
                }
                Slider(
                    value = current.toFloat(),
                    onValueChange = { value ->
                        val v = value.toInt()
                        volumes = volumes.toMutableMap().apply { put(row.stream, v) }
                        try {
                            audio.setStreamVolume(row.stream, v, 0)
                        } catch (_: Exception) {
                        }
                    },
                    valueRange = 0f..max.toFloat(),
                    steps = (max - 1).coerceAtLeast(0),
                    colors = SliderDefaults.colors(
                        thumbColor = onPageColor,
                        activeTrackColor = onPageColor,
                        inactiveTrackColor = thumbBg
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (row.stream == AudioManager.STREAM_VOICE_CALL) {
                    Text(
                        "Call volume applies during a call.",
                        fontSize = (minDim * 0.033f).value.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(minDim * 0.022f))
    }
}
