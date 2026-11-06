// FILE: app/src/main/java/com/calnyx/app/ui/screens/BlueFilmScreen.kt
// REPLACE the V8.1 file. V8.2 - title colour only:
//  The user does not like the red title after all:
//  "blue film title-eo lal color deoa ache, oi lal color
//  ta remove kore shada kalo kor, karon bhalo lagteche
//  na lal." So the "Blue Film" title is back to the normal
//  theme colour (black in light mode, off-white in dark
//  mode) - the dark-red titleRed is gone. Nothing else
//  changed: status bar match, no "Ready", folders, Play,
//  thumbnails, Rename, Delete, Move, Settings all as in
//  V8.1. The recording dot / "Recording" text / Delete
//  icon stay red - those mark a state, not a title.
// REPLACE the V8 file. V8.1 - compile fix ONLY, no design
// or behaviour change:
//  The build failed on `mutableStateSetOf` (Unresolved
//  reference) - that function only exists in Compose
//  Runtime 1.7+, and this project uses an older Compose.
//  Every other error in the log was a knock-on from that
//  one line (the set had no type, so .add/.remove could
//  not be resolved either). The damaged-names state is now
//  a plain `mutableStateOf<Set<String>>(emptySet())`,
//  which exists in every Compose version: add is
//  `damagedNames = damagedNames + name`, remove is
//  `damagedNames = damagedNames - name`. Same behaviour.
// REPLACE the V7 file. V8 changes - design only, from the
// user's screenshot (light mode, 11:24):
//  1. STATUS BAR MATCH: the top strip (clock, battery %,
//     WiFi, network) was pure white while the page below
//     is off-white cream - they did not match. Fixed two
//     ways: the page background is now drawn FIRST, before
//     the status/navigation bar padding, so it covers the
//     area behind the status bar too; AND the window's
//     status/navigation bar colors are set to the same
//     page background, with light/dark icons chosen for the
//     theme. Same in dark mode (dark page, light icons).
//  2. "Ready" REMOVED from the Record card - the card is
//     just the Start/Stop button now (plus the note when
//     there is one). "Recording - mm:ss" and "Starting..."
//     still show while recording / starting.
//  3. "Blue Film" title is now DARK RED (#B71C1C) in BOTH
//     dark and light mode - a deep red, same in both.
//  Nothing else changed: folders, Play, thumbnails,
//  Rename, Delete dialog, Move, Settings all as in V7.
// REPLACE the V6 file. V7 changes - from the user's video:
// Play tap gave ZERO feedback (ripple only, no note, no
// Toast, no player even 1.5s later) on BF_20261106_102802:
//  1. That video is the DAMAGED 10:28 file - its thumbnail
//     is the grey Videocam icon, not a real frame, which
//     proves decrypt fails for it. No code can play it.
//     Now such a video is MARKED on its card in red:
//     "Damaged - cannot play", so the user knows BEFORE
//     tapping instead of tapping and getting nothing.
//  2. A failed Play now shows a Toast with the exact reason
//     (visible wherever the user is), as well as the note.
//     "Nothing happens" is no longer possible: every tap
//     answers, success or failure.
//  3. Date and size are on separate lines in the grid card
//     (the video showed "8...." - the size was truncated).
//  4. Play itself is fixed in the Store (V7): the
//     resolveActivity gate is gone, decrypts are serialized.
//  Everything else from V6 is kept: folders grid, New
//  folder, long-press Move, Rename, Delete dialog, camera
//  toggle only in Settings, lifted header.
// REPLACE the V6 file. V6 changes, from the user's message + screenshot:
//  1. DELETE CONFIRM DIALOG: the two-tap "Tap Delete again"
//     is gone. Tapping Delete now opens a small confirmation
//     dialog - "Delete video?" with the video name, Delete
//     (red) and Cancel. Delete only happens on Delete;
//     Cancel closes it and nothing is deleted.
//  2. FOLDERS ARE BACK, AS A GRID: videos live INSIDE
//     folders, never loose on the page. The page first shows
//     the folders as a 2-column grid - each folder card shows
//     its first video's thumbnail as the cover, its name,
//     and how many videos are inside. Tap a folder to open
//     it; inside, the videos are the same thumbnail grid
//     as V5. "+ New folder" creates more folders.
//  3. MOVE: long-press a video card (press and hold) to
//     move it - a small card lists the other folders, tap
//     one and the video moves there. A hint under the
//     folder title says so.
//  4. CAMERA TOGGLE ONLY ONCE: Back/Front was in both the
//     Settings card AND the Record card. It is now only in
//     Settings (with Quality). The Record card is just
//     Ready + Start/Stop.
//  5. Kept from V5: thumbnails, external-only Play (fast,
//     pre-decrypted in the background), Rename, red Delete,
//     lifted header, dark mode, % sizing, no animation.
//     Every Text has an explicit color, every Icon an
//     explicit tint, screenW = maxWidth and
//     screenH = maxHeight captured once.
package com.calnyx.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraCharacteristics
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.calnyx.app.BlueFilm.BlueFilmService
import com.calnyx.app.BlueFilm.BlueFilmStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// combinedClickable (long-press to move) is an experimental
// foundation API - opt in so the build does not fail on it.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BlueFilmScreen(darkTheme: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pageBg = if (darkTheme) Color(0xFF121212) else Color(0xFFF6F4EF)
    val cardBg = if (darkTheme) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
    val primary = if (darkTheme) Color(0xFFFFFFFF) else Color(0xFF111111)
    val secondary = if (darkTheme) Color(0xFFBDBDBD) else Color(0xFF5A5A5A)
    val cardBorder = if (darkTheme) Color(0xFF3A3A3A) else Color(0xFFE9E6DF)
    val thumbBg = if (darkTheme) Color(0xFF2E2E2E) else Color(0xFFEFEDE8)
    val buttonBg = if (darkTheme) Color(0xFFF6F4EF) else Color(0xFF111111)
    val buttonText = if (darkTheme) Color(0xFF111111) else Color(0xFFFFFFFF)
    val red = if (darkTheme) Color(0xFFEF5350) else Color(0xFFC62828)
    // No red title any more - the user does not like red
    // on the title. It uses the normal theme colour now.

    // Status bar + navigation bar match the page background:
    // the top strip (clock, battery, WiFi, network) must be
    // the same colour as the page below it, in both themes,
    // with icons light in dark mode and dark in light mode.
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

    var isRecording by remember { mutableStateOf(BlueFilmService.isRecording) }
    var isStarting by remember { mutableStateOf(BlueFilmService.isStarting) }
    var elapsedSec by remember { mutableStateOf(0L) }
    var note by remember { mutableStateOf<String?>(null) }
    var deleteFile by remember { mutableStateOf<File?>(null) }
    var facing by remember { mutableStateOf(BlueFilmService.savedFacing(context)) }
    var quality by remember { mutableStateOf(BlueFilmService.savedQuality(context)) }
    var showSettings by remember { mutableStateOf(false) }
    var folders by remember { mutableStateOf(BlueFilmStore.listFolders(context)) }
    var currentFolder by remember { mutableStateOf<String?>(null) }
    var videos by remember { mutableStateOf<List<File>>(emptyList()) }
    var newFolderName by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var moveFile by remember { mutableStateOf<File?>(null) }
    var renameFile by remember { mutableStateOf<File?>(null) }
    var renameText by remember { mutableStateOf("") }
    val thumbBitmaps = remember { mutableStateMapOf<String, ImageBitmap>() }
    // Videos whose decrypt failed in the background prep -
    // shown as "Damaged - cannot play" on their card.
    // Plain Set state on purpose: mutableStateSetOf needs
    // Compose Runtime 1.7+, this project is older.
    var damagedNames by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun refreshAll() {
        folders = BlueFilmStore.listFolders(context)
        videos = if (currentFolder != null) {
            BlueFilmStore.listVideosInFolder(context, currentFolder!!)
        } else {
            emptyList()
        }
    }

    fun openFolder(folder: String) {
        currentFolder = folder
        moveFile = null
        renameFile = null
        deleteFile = null
        videos = BlueFilmStore.listVideosInFolder(context, folder)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            BlueFilmService.start(context, facing)
            note = null
        } else {
            note = "Camera permission was not given."
        }
    }

    fun startRecording() {
        if (isRecording || isStarting) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            BlueFilmService.start(context, facing)
            note = null
        } else {
            cameraLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // External player only. The decrypted temp is usually
    // already prepared by the background prep below, so this
    // is just FileProvider + startActivity - instant.
    // A failed Play ALWAYS answers with a Toast (visible
    // wherever the user is) as well as the note - a tap
    // must never silently do nothing.
    fun playFile(file: File) {
        scope.launch(Dispatchers.IO) {
            val err = BlueFilmStore.playVideoError(context, file)
            withContext(Dispatchers.Main) {
                val message = when (err) {
                    null -> null
                    "no_file" -> "Video file is missing."
                    "decrypt_failed" -> "Video could not be decrypted. This file was damaged when it was recorded and cannot be played. Record a new short video to test."
                    "no_fileprovider" -> "FileProvider path is missing. Replace res/xml/file_paths.xml with the merged one."
                    "no_player" -> "No video player app found on this phone."
                    else -> "Player could not be started."
                }
                note = message
                if (message != null) {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun downloadFile(file: File) {
        note = "Downloading..."
        scope.launch(Dispatchers.IO) {
            val ok = BlueFilmStore.exportToGallery(context, file)
            withContext(Dispatchers.Main) {
                note = if (ok) "Downloaded to Movies / CalNyx." else "Download failed."
            }
        }
    }

    LaunchedEffect(Unit) {
        var wasRecording = false
        while (true) {
            val rec = BlueFilmService.isRecording
            val starting = BlueFilmService.isStarting
            if (rec != isRecording) isRecording = rec
            if (starting != isStarting) isStarting = starting
            if (rec) {
                elapsedSec = (System.currentTimeMillis() - BlueFilmService.startedAtMillis) / 1000
            } else if (wasRecording) {
                elapsedSec = 0L
                refreshAll()
                note = "Saved to Blue Film (encrypted)."
            }
            wasRecording = rec
            delay(500)
        }
    }

    // Background prep, once per video: decrypt to cache (so
    // Play is instant later) + make a thumbnail frame.
    // In the folders view it preps the first video of each
    // folder (the folder cover); inside a folder it preps
    // every video there. Nothing here blocks the UI.
    LaunchedEffect(videos, folders, currentFolder) {
        withContext(Dispatchers.IO) {
            val targets = if (currentFolder != null) {
                videos
            } else {
                folders.mapNotNull { BlueFilmStore.listVideosInFolder(context, it).firstOrNull() }
            }
            for (file in targets) {
                val name = BlueFilmStore.displayName(file)
                if (thumbBitmaps.containsKey(name) || damagedNames.contains(name)) continue
                // Decrypt first: if that fails the file is
                // damaged and can never play - mark it on the
                // card instead of letting the user tap it and
                // get nothing.
                val tmp = BlueFilmStore.prepareTemp(context, file)
                if (tmp == null) {
                    withContext(Dispatchers.Main) { damagedNames = damagedNames + name }
                    continue
                }
                val thumb = BlueFilmStore.prepareThumbnail(context, file)
                if (thumb != null) {
                    val bmp = BitmapFactory.decodeFile(thumb.absolutePath)?.asImageBitmap()
                    if (bmp != null) {
                        withContext(Dispatchers.Main) { thumbBitmaps[name] = bmp }
                    }
                }
            }
        }
    }

    BoxWithConstraints(
        // Background FIRST, then the bar paddings: the page
        // colour covers the area behind the status bar too,
        // so the top strip matches the page below it.
        modifier = Modifier.fillMaxSize()
            .background(pageBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val screenW = maxWidth
        val screenH = maxHeight
        val padX = screenW * 0.04f
        val padY = screenH * 0.015f
        val gap = screenH * 0.015f
        val cardPad = screenW * 0.035f
        val iconBox = screenW * 0.13f
        val thumbH = screenW * 0.30f
        val actionBtn = 36.dp

        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = padX),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            // TOP HEADER - no spacer above it, no top padding.
            // Settings on the LEFT, title next to it.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(iconBox)
                        .clip(CircleShape)
                        .background(thumbBg)
                        .clickable { showSettings = !showSettings },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = primary,
                        modifier = Modifier.size(iconBox * 0.55f)
                    )
                }
                Spacer(modifier = Modifier.width(screenW * 0.03f))
                Text(
                    "Blue Film",
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // SETTINGS CARD - camera toggle lives ONLY here now.
            if (showSettings) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                        .padding(cardPad),
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    Text("Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = primary)
                    Text("Camera", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = primary)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (facing == CameraCharacteristics.LENS_FACING_BACK) buttonBg else cardBg)
                                .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                                .clickable(enabled = !isRecording && !isStarting) {
                                    facing = CameraCharacteristics.LENS_FACING_BACK
                                    BlueFilmService.saveFacing(context, facing)
                                }
                                .padding(vertical = padY),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Back camera", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                color = if (facing == CameraCharacteristics.LENS_FACING_BACK) buttonText else primary)
                        }
                        Spacer(modifier = Modifier.width(screenW * 0.02f))
                        Box(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (facing == CameraCharacteristics.LENS_FACING_FRONT) buttonBg else cardBg)
                                .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                                .clickable(enabled = !isRecording && !isStarting) {
                                    facing = CameraCharacteristics.LENS_FACING_FRONT
                                    BlueFilmService.saveFacing(context, facing)
                                }
                                .padding(vertical = padY),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Front camera", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                color = if (facing == CameraCharacteristics.LENS_FACING_FRONT) buttonText else primary)
                        }
                    }
                    Text("Quality", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = primary)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (quality == 720) buttonBg else cardBg)
                                .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                                .clickable(enabled = !isRecording && !isStarting) {
                                    quality = 720
                                    BlueFilmService.saveQuality(context, 720)
                                }
                                .padding(vertical = padY),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("720p", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                color = if (quality == 720) buttonText else primary)
                        }
                        Spacer(modifier = Modifier.width(screenW * 0.02f))
                        Box(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (quality == 1080) buttonBg else cardBg)
                                .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                                .clickable(enabled = !isRecording && !isStarting) {
                                    quality = 1080
                                    BlueFilmService.saveQuality(context, 1080)
                                }
                                .padding(vertical = padY),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("1080p", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                color = if (quality == 1080) buttonText else primary)
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(buttonBg)
                            .clickable { showSettings = false }
                            .padding(vertical = padY),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Done", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = buttonText)
                    }
                }
            }

            // RECORD CARD - no camera toggle here any more.
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBg)
                    .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                    .padding(cardPad),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                // No "Ready" text any more - the card is just
                // the Start/Stop button. Recording / Starting
                // still show their status while they run.
                if (isRecording || isStarting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (isRecording) "Recording - ${formatElapsed(elapsedSec)}"
                            else "Starting...",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = if (isRecording) red else primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (isRecording) {
                            Box(modifier = Modifier.size(screenW * 0.03f).clip(CircleShape).background(red))
                        }
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(buttonBg)
                        .clickable(enabled = !isStarting) {
                            if (isRecording) BlueFilmService.stop(context) else startRecording()
                        }
                        .padding(vertical = padY * 1.2f),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            modifier = Modifier.size(screenW * 0.06f),
                            tint = if (isRecording) red else buttonText
                        )
                        Spacer(modifier = Modifier.width(screenW * 0.025f))
                        Text(
                            if (isRecording) "Stop Recording" else "Start Recording",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = buttonText,
                            maxLines = 1
                        )
                    }
                }
                if (note != null) {
                    Text(note!!, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = primary,
                        maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }

            // RENAME CARD
            if (renameFile != null) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                        .padding(cardPad),
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    Text("Rename video", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = primary)
                    BasicTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        textStyle = TextStyle(color = primary, fontSize = 14.sp),
                        decorationBox = { inner ->
                            if (renameText.isEmpty()) {
                                Text("Video name", fontSize = 14.sp, color = secondary)
                            }
                            inner()
                        },
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(thumbBg)
                            .padding(horizontal = screenW * 0.03f, vertical = padY)
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(buttonBg)
                                .clickable {
                                    scope.launch(Dispatchers.IO) {
                                        val renamed = BlueFilmStore.renameVideo(context, renameFile!!, renameText)
                                        withContext(Dispatchers.Main) {
                                            if (renamed != null) {
                                                thumbBitmaps.remove(BlueFilmStore.displayName(renameFile!!))
                                                damagedNames = damagedNames - BlueFilmStore.displayName(renameFile!!)
                                                renameFile = null
                                                renameText = ""
                                                refreshAll()
                                                note = "Renamed."
                                            } else {
                                                note = "Could not rename. Use a different name."
                                            }
                                        }
                                    }
                                }
                                .padding(vertical = padY),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Save", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = buttonText)
                        }
                        Spacer(modifier = Modifier.width(screenW * 0.02f))
                        Box(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(cardBg)
                                .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                                .clickable { renameFile = null; renameText = "" }
                                .padding(vertical = padY),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Cancel", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = primary)
                        }
                    }
                }
            }

            // MOVE CARD - opened by long-pressing a video card.
            if (moveFile != null) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                        .padding(cardPad),
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    Text("Move to folder", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = primary)
                    Text(
                        BlueFilmStore.displayName(moveFile!!),
                        fontSize = 13.sp, color = secondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    folders.filter { it != currentFolder }.forEach { target ->
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(buttonBg)
                                .clickable {
                                    scope.launch(Dispatchers.IO) {
                                        val ok = BlueFilmStore.moveVideo(context, moveFile!!, target)
                                        withContext(Dispatchers.Main) {
                                            moveFile = null
                                            refreshAll()
                                            note = if (ok) "Moved to $target." else "Move failed."
                                        }
                                    }
                                }
                                .padding(vertical = padY),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(target, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                color = buttonText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(cardBg)
                            .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                            .clickable { moveFile = null }
                            .padding(vertical = padY),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = primary)
                    }
                }
            }

            if (currentFolder == null) {
                // FOLDERS GRID VIEW
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Folders",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${folders.size} folders",
                        fontSize = 12.sp,
                        color = secondary,
                        maxLines = 1
                    )
                }
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(buttonBg)
                        .clickable { showCreate = !showCreate }
                        .padding(vertical = padY),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (showCreate) "Cancel" else "+ New folder",
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = buttonText)
                }
                if (showCreate) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(cardBg)
                            .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                            .padding(cardPad),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            singleLine = true,
                            textStyle = TextStyle(color = primary, fontSize = 14.sp),
                            decorationBox = { inner ->
                                if (newFolderName.isEmpty()) {
                                    Text("Folder name", fontSize = 14.sp, color = secondary)
                                }
                                inner()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(screenW * 0.02f))
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                .background(buttonBg)
                                .clickable {
                                    if (BlueFilmStore.createFolder(context, newFolderName)) {
                                        newFolderName = ""
                                        showCreate = false
                                        refreshAll()
                                        note = "Folder created."
                                    } else {
                                        note = "Could not create folder. Use a different name."
                                    }
                                }
                                .padding(horizontal = screenW * 0.04f, vertical = padY),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Create", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = buttonText)
                        }
                    }
                }
                if (folders.isEmpty()) {
                    Text("No folders yet.", fontSize = 14.sp, color = secondary)
                }
                folders.chunked(2).forEach { rowFolders ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(screenW * 0.02f)
                    ) {
                        rowFolders.forEach { folder ->
                            val first = BlueFilmStore.listVideosInFolder(context, folder).firstOrNull()
                            val cover = first?.let { thumbBitmaps[BlueFilmStore.displayName(it)] }
                            Column(
                                modifier = Modifier.weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(cardBg)
                                    .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                                    .clickable { openFolder(folder) }
                            ) {
                                if (cover != null) {
                                    Image(
                                        cover,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().height(thumbH)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxWidth()
                                            .height(thumbH)
                                            .background(thumbBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = secondary,
                                            modifier = Modifier.size(screenW * 0.12f)
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(screenW * 0.025f),
                                    verticalArrangement = Arrangement.spacedBy(screenH * 0.004f)
                                ) {
                                    Text(
                                        folder,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${BlueFilmStore.folderCount(context, folder)} videos",
                                        fontSize = 12.sp,
                                        color = secondary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        if (rowFolders.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                // INSIDE A FOLDER - VIDEOS GRID
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { currentFolder = null; moveFile = null; renameFile = null; deleteFile = null; refreshAll() },
                        modifier = Modifier.size(actionBtn)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to folders", tint = primary)
                    }
                    Text(
                        currentFolder!!,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${videos.size} videos",
                        fontSize = 12.sp,
                        color = secondary,
                        maxLines = 1
                    )
                }
                Text(
                    "Long-press a video to move it to another folder.",
                    fontSize = 12.sp,
                    color = secondary,
                    maxLines = 2
                )
                if (videos.isEmpty()) {
                    Text("No videos in this folder yet.", fontSize = 14.sp, color = secondary)
                }
                videos.chunked(2).forEach { rowVideos ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(screenW * 0.02f)
                    ) {
                        rowVideos.forEach { file ->
                            val name = BlueFilmStore.displayName(file)
                            Column(
                                modifier = Modifier.weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(cardBg)
                                    .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                                    .combinedClickable(
                                        onClick = { playFile(file) },
                                        onLongClick = { moveFile = file }
                                    )
                            ) {
                                val bmp = thumbBitmaps[name]
                                if (bmp != null) {
                                    Image(
                                        bmp,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().height(thumbH)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxWidth()
                                            .height(thumbH)
                                            .background(thumbBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Videocam,
                                            contentDescription = null,
                                            tint = secondary,
                                            modifier = Modifier.size(screenW * 0.12f)
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(screenW * 0.025f),
                                    verticalArrangement = Arrangement.spacedBy(screenH * 0.004f)
                                ) {
                                    Text(
                                        name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = primary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        BlueFilmStore.displayDate(file),
                                        fontSize = 11.sp,
                                        color = secondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        BlueFilmStore.sizeText(file),
                                        fontSize = 11.sp,
                                        color = secondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (damagedNames.contains(name)) {
                                        Text(
                                            "Damaged - cannot play",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = red,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { playFile(file) },
                                            modifier = Modifier.size(actionBtn)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = primary)
                                        }
                                        IconButton(
                                            onClick = { downloadFile(file) },
                                            modifier = Modifier.size(actionBtn)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = "Download to gallery", tint = primary)
                                        }
                                        IconButton(
                                            onClick = {
                                                renameFile = file
                                                renameText = name
                                            },
                                            modifier = Modifier.size(actionBtn)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Rename", tint = primary)
                                        }
                                        IconButton(
                                            onClick = { deleteFile = file },
                                            modifier = Modifier.size(actionBtn)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = red)
                                        }
                                    }
                                }
                            }
                        }
                        if (rowVideos.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(padY))
        }

        // DELETE CONFIRM DIALOG
        if (deleteFile != null) {
            AlertDialog(
                onDismissRequest = { deleteFile = null },
                title = {
                    Text(
                        "Delete video?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = primary
                    )
                },
                text = {
                    Text(
                        BlueFilmStore.displayName(deleteFile!!) + " will be deleted.",
                        fontSize = 14.sp,
                        color = secondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val file = deleteFile!!
                        val name = BlueFilmStore.displayName(file)
                        if (BlueFilmStore.deleteVideo(context, file)) {
                            thumbBitmaps.remove(name)
                            damagedNames = damagedNames - name
                            note = "Deleted."
                            refreshAll()
                        } else {
                            note = "Delete failed."
                        }
                        deleteFile = null
                    }) {
                        Text("Delete", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteFile = null }) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = primary)
                    }
                },
                containerColor = cardBg,
                titleContentColor = primary,
                textContentColor = secondary
            )
        }
    }
}

private fun formatElapsed(totalSec: Long): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}
