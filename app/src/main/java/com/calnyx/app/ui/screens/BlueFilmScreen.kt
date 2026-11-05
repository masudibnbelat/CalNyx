// FILE: app/src/main/java/com/calnyx/app/ui/screens/BlueFilmScreen.kt
// NEW FILE - the Blue Film page (secret screen recorder).
//
// One page, two parts:
//  1. Record card: a big Start / Stop button, a live timer and
//     a status line. Tapping Start asks Android for
//     screen-capture permission once (the system dialog); the
//     BlueFilmService then records in the background, so it
//     keeps going when the app is closed and the screen is
//     locked. No internet is used at all - fully offline.
//  2. Recordings: the in-app folder. Every recording is an
//     AES-encrypted .bfenc file in private storage, so nothing
//     shows in the gallery. Each row shows its date and size,
//     and can be Played (decrypted to a temp file and opened
//     with any video player), Downloaded to the gallery
//     (decrypted to Movies/CalNyx), or Deleted (two taps).
// Colours are black / white only; red marks recording only.
package com.calnyx.app.ui.screens

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calnyx.app.BlueFilm.BlueFilmService
import com.calnyx.app.BlueFilm.BlueFilmStore
import java.io.File
import kotlinx.coroutines.delay

@Composable
fun BlueFilmScreen(darkTheme: Boolean) {
    val context = LocalContext.current
    val cardBg = if (darkTheme) Color(0xFF1B1B1B) else Color(0xFFFFFFFF)
    val onPageColor = if (darkTheme) Color(0xFFF6F4EF) else Color(0xFF0E0E0E)
    val cardBorder = if (darkTheme) Color(0xFF2B2B2B) else Color(0xFFE9E6DF)
    val thumbBg = if (darkTheme) Color(0xFF262626) else Color(0xFFEFEDE8)
    val red = Color(0xFFC62828)

    var isRecording by remember { mutableStateOf(BlueFilmService.isRecording) }
    var elapsedSec by remember { mutableStateOf(0L) }
    var videos by remember { mutableStateOf(BlueFilmStore.listVideos(context)) }
    var note by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    val projectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
            as MediaProjectionManager
    }
    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            BlueFilmService.start(context, result.resultCode, result.data!!)
            isRecording = true
            note = null
        } else {
            note = "Screen capture permission was not given."
        }
    }

    // Live timer while recording; refresh the list when it ends.
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (BlueFilmService.isRecording) {
                elapsedSec =
                    (System.currentTimeMillis() - BlueFilmService.startedAtMillis) / 1000
                delay(1000)
            }
            isRecording = false
            elapsedSec = 0L
            videos = BlueFilmStore.listVideos(context)
            note = "Saved to Blue Film (encrypted)."
        }
    }

    fun refresh() {
        videos = BlueFilmStore.listVideos(context)
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // RECORD CARD
        Column(
            modifier = Modifier.fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(thumbBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Blue Film", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(
                        if (isRecording) "Recording - ${formatElapsed(elapsedSec)}"
                        else "Secret screen recorder",
                        fontSize = 13.sp,
                        color = if (isRecording) red else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isRecording) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
                if (isRecording) {
                    Box(
                        modifier = Modifier.size(12.dp).clip(CircleShape).background(red)
                    )
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(onPageColor)
                    .clickable {
                        if (isRecording) {
                            BlueFilmService.stop(context)
                        } else {
                            captureLauncher.launch(
                                projectionManager.createScreenCaptureIntent()
                            )
                        }
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (isRecording) red else cardBg
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        if (isRecording) "Stop Recording" else "Start Recording",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = cardBg
                    )
                }
            }
            Text(
                "Offline. Keeps recording when the app is closed and when the screen is locked. Videos stay here, encrypted - they never go to the gallery unless you download one.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (note != null) {
                Text(note!!, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // RECORDINGS - the in-app folder.
        Text("Recordings", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        if (videos.isEmpty()) {
            Text(
                "No recordings yet.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        videos.forEach { file ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBg)
                    .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(thumbBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        BlueFilmStore.displayName(file),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        "${BlueFilmStore.displayDate(file)}  -  ${BlueFilmStore.sizeText(file)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    if (!BlueFilmStore.playVideo(context, file)) {
                        note = "Could not open a video player."
                    }
                }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                }
                IconButton(onClick = {
                    if (BlueFilmStore.exportToGallery(context, file)) {
                        note = "Downloaded to Movies / CalNyx."
                    } else {
                        note = "Download failed."
                    }
                }) {
                    Icon(Icons.Default.Download, contentDescription = "Download to gallery")
                }
                IconButton(onClick = {
                    if (pendingDelete == file.absolutePath) {
                        if (BlueFilmStore.deleteVideo(file)) {
                            note = "Deleted."
                            refresh()
                        } else {
                            note = "Delete failed."
                        }
                        pendingDelete = null
                    } else {
                        pendingDelete = file.absolutePath
                        note = "Tap Delete again to confirm."
                    }
                }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = if (pendingDelete == file.absolutePath) red else onPageColor
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

private fun formatElapsed(totalSec: Long): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}
