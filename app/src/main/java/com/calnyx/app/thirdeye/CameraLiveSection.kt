// FILE: app/src/main/java/com/calnyx/app/network/CameraLiveSection.kt
// NEW FILE - Camera Live section for the Third Eye screen (2026-11-21).
// Task 5, Camera Live 1-to-1 (LIVE REDO 2026-11-21). Add ONE call
// in ThirdEyeHubScreen.kt, BELOW the ScreenStreamSection call
// (Screen Share on top, Camera Live below - user's order).
// Do NOT use the old ThirdEyeScreen.kt name - it clashes with the
// live recorder com.calnyx.app.thirdeye.ThirdEyeScreen.
//
//   CameraLiveSection(
//       cardBg = cardBg,
//       cardBorder = cardBorder,
//       onPageColor = onPageColor,
//       thumbBg = thumbBg,
//       minDim = minDim
//   )
//
// Mirrors the 02-stream-link-top ScreenStreamSection (SHARE card
// first, then WATCH; link password auto-fill; % sizes; theme
// colours; no fixed dp/sp except 1.dp borders):
//
//   1. SHARE MY CAMERA - password (mandatory, at least 4
//      characters), quality 480p / 720p pills, Front / Back
//      pills, Start Camera. The phone's camera goes live
//      through the relay and a link appears to copy and send.
//      The link carries Room ID + Password (#pw= fragment),
//      so it opens at once. The camera keeps streaming even
//      if the app is closed or the phone locks - until Stop.
//      "View call" opens the call view: the viewer big, you
//      small (if the viewer camera is off, you are big).
//   2. WATCH A CAMERA - paste someone's link, or type their
//      Room ID - then the password, tap Open, and their camera
//      plays in the app (WebView), full screen. Your own
//      camera toggle is in the viewer: turn it on and the host
//      sees you too (1-to-1, video only, no sound). Closing
//      the viewer turns your camera off again - the watch
//      itself stays saved until "Stop watching".
//   PERSISTENT WATCH: same as Screen Stream - the watch is
//   saved, so leaving the Third Eye screen and coming back
//   reopens it by itself, live, until "Stop watching".
//   Back / X only hides it - the session stays.
//
// The relay URL is CODE-ONLY (CameraLivePrefs): never shown or
// edited in the UI. The share link itself must contain the
// address - that is how a browser reaches the stream - and it
// is only shown while streaming, to be copied and sent.
//
// Self / peer pictures are NOT a live camera preview (there is
// no CameraX PreviewView in this project). They are the latest
// JPEG frames the service captured / received, decoded in the
// UI at most once per frame - good enough to check who is on.
package com.calnyx.app.thirdeye

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import android.hardware.camera2.CameraCharacteristics
import com.calnyx.app.theme.CalNyxRed
import java.net.URLEncoder
import kotlinx.coroutines.delay

private fun formatElapsed(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

// One compact copy row for the live block: a small label, the
// value, and a copy button on the right. Material 3 pieces only,
// % sizes only, colours from the theme - no hardcoded colours.
@Composable
private fun CameraCopyRow(
    label: String,
    value: String,
    onCopy: () -> Unit,
    minDim: Dp,
    onPageColor: Color,
    labelColor: Color,
    rowBg: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(minDim * 0.016f))
            .background(rowBg)
            .padding(horizontal = minDim * 0.028f, vertical = minDim * 0.012f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                fontSize = (minDim * 0.03f).value.sp,
                color = labelColor
            )
            Text(
                value,
                fontSize = (minDim * 0.036f).value.sp,
                fontWeight = FontWeight.SemiBold,
                color = onPageColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(minDim * 0.022f))
        IconButton(
            onClick = onCopy,
            modifier = Modifier.size(minDim * 0.089f)
        ) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "Copy $label",
                modifier = Modifier.size(minDim * 0.044f),
                tint = onPageColor
            )
        }
    }
}

@Composable
fun CameraLiveSection(
    cardBg: Color,
    cardBorder: Color,
    onPageColor: Color,
    thumbBg: Color,
    minDim: Dp
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val buttonBg = onPageColor
    val buttonText = cardBg

    // Relay URL: code-only, from CameraLivePrefs. Never shown
    // or edited in the UI - users must never see the server address.
    val relayUrl = CameraLivePrefs.savedRelayUrl(context)

    var note by remember { mutableStateOf<String?>(null) }

    // SHARE - my camera.
    var sharePassword by remember { mutableStateOf(CameraLivePrefs.savedPassword(context)) }
    var sharePasswordVisible by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf(CameraLivePrefs.savedQuality(context)) }
    var facing by remember { mutableStateOf(CameraLivePrefs.savedFacing(context)) }
    var callViewOpen by remember { mutableStateOf(false) }

    // WATCH - someone else's camera.
    var watchUrl by remember { mutableStateOf("") }
    var watchRoomId by remember { mutableStateOf("") }
    var watchPassword by remember { mutableStateOf("") }
    var watchPasswordVisible by remember { mutableStateOf(false) }
    // baseUrl + room + password of the camera being watched.
    // Restored from the saved session, so leaving the Third Eye
    // screen and coming back reopens it by itself.
    var viewerOpen by remember { mutableStateOf(CameraLivePrefs.loadWatchSession(context)) }

    // Live state from the service, polled like ScreenStreamSection.
    var isStreaming by remember { mutableStateOf(CameraLiveService.isStreaming) }
    var isStarting by remember { mutableStateOf(CameraLiveService.isStarting) }
    var isViewerPublishing by remember { mutableStateOf(CameraLiveService.isViewerPublishing) }
    var isViewerStarting by remember { mutableStateOf(CameraLiveService.isViewerStarting) }
    var roomCode by remember { mutableStateOf(CameraLiveService.roomCode) }
    var streamUrl by remember { mutableStateOf(CameraLiveService.streamUrl) }
    var viewerCount by remember { mutableStateOf(CameraLiveService.viewerCount) }
    var elapsedSec by remember { mutableStateOf(0L) }

    // Latest pictures, decoded at most once per frame. The
    // service replaces the ByteArray reference on every frame,
    // so a reference check is enough to know it changed.
    var viewerBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var selfBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var lastViewerBytes by remember { mutableStateOf<ByteArray?>(null) }
    var lastSelfBytes by remember { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            isStreaming = CameraLiveService.isStreaming
            isStarting = CameraLiveService.isStarting
            isViewerPublishing = CameraLiveService.isViewerPublishing
            isViewerStarting = CameraLiveService.isViewerStarting
            roomCode = CameraLiveService.roomCode
            streamUrl = CameraLiveService.streamUrl
            viewerCount = CameraLiveService.viewerCount
            if (isStreaming || isStarting) {
                facing = CameraLiveService.currentFacing
            }
            if (isStreaming) {
                elapsedSec =
                    (System.currentTimeMillis() - CameraLiveService.startedAtMillis) / 1000
            } else {
                elapsedSec = 0L
            }
            val vb = CameraLiveService.latestViewerJpeg
            if (vb !== lastViewerBytes) {
                lastViewerBytes = vb
                viewerBitmap = vb?.let {
                    BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()
                }
            }
            val sb = CameraLiveService.latestLocalJpeg
            if (sb !== lastSelfBytes) {
                lastSelfBytes = sb
                selfBitmap = sb?.let {
                    BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()
                }
            }
            CameraLiveService.takeError()?.let { note = it }
            delay(500)
        }
    }

    // Camera permission: asked once, then the pending action runs.
    var pendingCameraAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingCameraAction?.invoke()
        } else {
            note = "Camera permission is needed to share your camera."
        }
        pendingCameraAction = null
    }

    fun withCameraPermission(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            pendingCameraAction = action
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun startCamera() {
        if (sharePassword.length < CameraLivePrefs.MIN_PASSWORD_LENGTH) {
            note = "Password must be at least 4 characters."
            return
        }
        if (!relayUrl.startsWith("http://") && !relayUrl.startsWith("https://")) {
            note = "Relay server address is not valid."
            return
        }
        CameraLivePrefs.savePassword(context, sharePassword)
        CameraLivePrefs.saveQuality(context, quality)
        CameraLivePrefs.saveFacing(context, facing)
        note = null
        withCameraPermission {
            CameraLiveService.startHost(context, sharePassword, relayUrl, quality, facing)
        }
    }

    fun setFacing(newFacing: Int) {
        facing = newFacing
        CameraLivePrefs.saveFacing(context, newFacing)
        if (isStreaming || isStarting) {
            CameraLiveService.switchFacing(context)
        }
    }

    fun openWatch() {
        // Two ways in: a pasted link, or a typed Room ID.
        // The link wins when both are filled. A link can carry
        // the password (#pw=), so typing it is optional then.
        val code = if (watchUrl.isNotBlank()) {
            CameraLivePrefs.parseRoomCode(watchUrl)
        } else {
            val typed = watchRoomId.trim().uppercase()
            if (Regex("^[A-Za-z0-9]{6}$").matches(typed)) typed else null
        }
        if (code == null) {
            note = if (watchUrl.isNotBlank()) "That link has no room code."
            else "Type the 6-letter Room ID."
            return
        }
        val effectivePassword =
            CameraLivePrefs.parsePasswordFromLink(watchUrl) ?: watchPassword
        if (effectivePassword.length < CameraLivePrefs.MIN_PASSWORD_LENGTH) {
            note = "Password must be at least 4 characters."
            return
        }
        note = null
        val baseUrl = CameraLivePrefs.parseBaseUrl(watchUrl, relayUrl)
        CameraLivePrefs.saveWatchSession(context, baseUrl, code, effectivePassword)
        viewerOpen = Triple(baseUrl, code, effectivePassword)
    }

    fun closeViewer() {
        // Hiding the viewer turns MY camera off again - the
        // watch itself stays saved until "Stop watching".
        if (isViewerPublishing || isViewerStarting) {
            CameraLiveService.stopViewerPublishing(context)
        }
        viewerOpen = null
    }

    fun toggleOwnCamera(code: String, pw: String) {
        if (isViewerPublishing || isViewerStarting) {
            CameraLiveService.stopViewerPublishing(context)
        } else {
            CameraLivePrefs.saveQuality(context, quality)
            CameraLivePrefs.saveFacing(context, facing)
            withCameraPermission {
                CameraLiveService.startViewerPublishing(
                    context, code, pw, relayUrl, quality, facing
                )
            }
        }
    }

    fun copyText(label: String, value: String) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(label, value))
            note = "$label copied."
        } catch (_: Exception) {
            note = "Could not copy."
        }
    }

    // The password a pasted link already carries (if any) counts
    // for the Open button, so a full link needs no typing at all.
    val linkPasswordLength =
        CameraLivePrefs.parsePasswordFromLink(watchUrl)?.length ?: 0
    val watchReady = (watchUrl.isNotBlank() || watchRoomId.isNotBlank()) &&
        (watchPassword.length >= CameraLivePrefs.MIN_PASSWORD_LENGTH ||
            linkPasswordLength >= CameraLivePrefs.MIN_PASSWORD_LENGTH)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(minDim * 0.012f)
    ) {
        if (note != null) {
            Text(
                text = note ?: "",
                fontSize = (minDim * 0.036f).value.sp,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // SHARE MY CAMERA - password, quality, front / back, Start.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(minDim * 0.044f),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, cardBorder)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(minDim * 0.028f),
                verticalArrangement = Arrangement.spacedBy(minDim * 0.012f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(minDim * 0.1f).clip(CircleShape).background(thumbBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(minDim * 0.05f), tint = onPageColor)
                    }
                    Spacer(modifier = Modifier.width(minDim * 0.033f))
                    Text("Share my camera", fontWeight = FontWeight.SemiBold, fontSize = (minDim * 0.044f).value.sp, color = onPageColor, modifier = Modifier.weight(1f))
                }
                Text(
                    "One person with the link and password can watch your camera, and you can see theirs. It keeps streaming even if the app is closed or the phone locks - until you tap Stop.",
                    fontSize = (minDim * 0.036f).value.sp,
                    color = colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = sharePassword,
                    onValueChange = { sharePassword = it },
                    label = { Text("Camera password") },
                    placeholder = { Text("At least 4 characters") },
                    singleLine = true,
                    visualTransformation = if (sharePasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { sharePasswordVisible = !sharePasswordVisible }) {
                            Icon(
                                if (sharePasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (sharePasswordVisible) "Hide password" else "Show password",
                                modifier = Modifier.size(minDim * 0.056f)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(minDim * 0.033f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = onPageColor,
                        unfocusedBorderColor = cardBorder,
                        cursorColor = onPageColor
                    )
                )
                // QUALITY - only 480p and 720p. No 1080p: it is
                // heavier, slower and eats more battery and relay.
                Text(
                    "Quality",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (minDim * 0.036f).value.sp,
                    color = onPageColor
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("480", "720").forEachIndexed { index, q ->
                        if (index > 0) Spacer(modifier = Modifier.width(minDim * 0.022f))
                        Box(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(minDim * 0.022f))
                                .background(if (quality == q) buttonBg else cardBg)
                                .border(
                                    BorderStroke(1.dp, if (quality == q) buttonBg else cardBorder),
                                    RoundedCornerShape(minDim * 0.022f)
                                )
                                .clickable { quality = q }
                                .padding(vertical = minDim * 0.016f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${q}p",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = (minDim * 0.036f).value.sp,
                                color = if (quality == q) buttonText else onPageColor
                            )
                        }
                    }
                }
                // CAMERA - front or back. Works while streaming
                // too: the service reopens the other lens and the
                // room stays live.
                Text(
                    "Camera",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (minDim * 0.036f).value.sp,
                    color = onPageColor
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    val facingOptions = listOf(
                        CameraCharacteristics.LENS_FACING_FRONT to "Front",
                        CameraCharacteristics.LENS_FACING_BACK to "Back"
                    )
                    facingOptions.forEachIndexed { index, (value, label) ->
                        if (index > 0) Spacer(modifier = Modifier.width(minDim * 0.022f))
                        Box(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(minDim * 0.022f))
                                .background(if (facing == value) buttonBg else cardBg)
                                .border(
                                    BorderStroke(1.dp, if (facing == value) buttonBg else cardBorder),
                                    RoundedCornerShape(minDim * 0.022f)
                                )
                                .clickable { setFacing(value) }
                                .padding(vertical = minDim * 0.016f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = (minDim * 0.036f).value.sp,
                                color = if (facing == value) buttonText else onPageColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(minDim * 0.022f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(minDim * 0.022f))
                            .background(cardBg)
                            .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(minDim * 0.022f))
                            .clickable {
                                setFacing(
                                    if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                                        CameraCharacteristics.LENS_FACING_BACK
                                    } else {
                                        CameraCharacteristics.LENS_FACING_FRONT
                                    }
                                )
                            }
                            .padding(vertical = minDim * 0.016f, horizontal = minDim * 0.022f),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Cameraswitch,
                            contentDescription = "Switch camera",
                            modifier = Modifier.size(minDim * 0.05f),
                            tint = onPageColor
                        )
                    }
                }
                Button(
                    onClick = {
                        if (isStreaming || isStarting) CameraLiveService.stop(context)
                        else startCamera()
                    },
                    enabled = isStreaming || isStarting || sharePassword.length >= CameraLivePrefs.MIN_PASSWORD_LENGTH,
                    modifier = Modifier.fillMaxWidth().height(minDim * 0.12f),
                    shape = RoundedCornerShape(minDim * 0.033f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isStreaming || isStarting) CalNyxRed else buttonBg,
                        contentColor = if (isStreaming || isStarting) Color.White else buttonText
                    )
                ) {
                    Text(
                        when {
                            isStreaming -> "Stop Camera"
                            isStarting -> "Starting..."
                            else -> "Start Camera"
                        },
                        fontSize = (minDim * 0.044f).value.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (isStarting) {
                    Text(
                        "Connecting to the relay server… If the server was asleep, this can take up to a minute.",
                        fontSize = (minDim * 0.036f).value.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                if (isStreaming) {
                    Text(
                        "Live  ${formatElapsed(elapsedSec)}  •  Room ID ${roomCode ?: ""}  •  Watching: $viewerCount",
                        fontSize = (minDim * 0.036f).value.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { callViewOpen = true },
                        modifier = Modifier.fillMaxWidth().height(minDim * 0.12f),
                        shape = RoundedCornerShape(minDim * 0.033f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonBg,
                            contentColor = buttonText
                        )
                    ) {
                        Text(
                            "View call",
                            fontSize = (minDim * 0.044f).value.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    streamUrl?.let { url ->
                        CameraCopyRow(
                            label = "Stream link",
                            value = url,
                            onCopy = { copyText("Link", url) },
                            minDim = minDim,
                            onPageColor = onPageColor,
                            labelColor = colorScheme.onSurfaceVariant,
                            rowBg = thumbBg
                        )
                    }
                    roomCode?.let { code ->
                        CameraCopyRow(
                            label = "Room ID",
                            value = code,
                            onCopy = { copyText("Room ID", code) },
                            minDim = minDim,
                            onPageColor = onPageColor,
                            labelColor = colorScheme.onSurfaceVariant,
                            rowBg = thumbBg
                        )
                    }
                    CameraCopyRow(
                        label = "Password",
                        value = sharePassword,
                        onCopy = { copyText("Password", sharePassword) },
                        minDim = minDim,
                        onPageColor = onPageColor,
                        labelColor = colorScheme.onSurfaceVariant,
                        rowBg = thumbBg
                    )
                }
            }
        }

        // WATCH A CAMERA - paste a link, type the password, Open.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(minDim * 0.044f),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, cardBorder)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(minDim * 0.028f),
                verticalArrangement = Arrangement.spacedBy(minDim * 0.012f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(minDim * 0.1f).clip(CircleShape).background(thumbBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(minDim * 0.05f), tint = onPageColor)
                    }
                    Spacer(modifier = Modifier.width(minDim * 0.033f))
                    Text("Watch a camera", fontWeight = FontWeight.SemiBold, fontSize = (minDim * 0.044f).value.sp, color = onPageColor, modifier = Modifier.weight(1f))
                }
                Text(
                    "Watch someone else's camera. Paste their link, or type their Room ID - then the password, and tap Open.",
                    fontSize = (minDim * 0.036f).value.sp,
                    color = colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = watchUrl,
                    onValueChange = { watchUrl = it },
                    label = { Text("Stream link") },
                    placeholder = { Text("https://…/?room=ABC123") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(minDim * 0.033f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = onPageColor,
                        unfocusedBorderColor = cardBorder,
                        cursorColor = onPageColor
                    )
                )
                // - or - Room ID instead of a link.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f).height(1.dp).background(cardBorder)
                    )
                    Text(
                        "or",
                        fontSize = (minDim * 0.033f).value.sp,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = minDim * 0.022f)
                    )
                    Box(
                        modifier = Modifier.weight(1f).height(1.dp).background(cardBorder)
                    )
                }
                OutlinedTextField(
                    value = watchRoomId,
                    onValueChange = { watchRoomId = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) },
                    label = { Text("Room ID") },
                    placeholder = { Text("ABC123") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(minDim * 0.033f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = onPageColor,
                        unfocusedBorderColor = cardBorder,
                        cursorColor = onPageColor
                    )
                )
                OutlinedTextField(
                    value = watchPassword,
                    onValueChange = { watchPassword = it },
                    label = { Text("Password") },
                    placeholder = { Text("At least 4 characters") },
                    singleLine = true,
                    visualTransformation = if (watchPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { watchPasswordVisible = !watchPasswordVisible }) {
                            Icon(
                                if (watchPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (watchPasswordVisible) "Hide password" else "Show password",
                                modifier = Modifier.size(minDim * 0.056f)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(minDim * 0.033f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = onPageColor,
                        unfocusedBorderColor = cardBorder,
                        cursorColor = onPageColor
                    )
                )
                Button(
                    onClick = { openWatch() },
                    enabled = watchReady,
                    modifier = Modifier.fillMaxWidth().height(minDim * 0.12f),
                    shape = RoundedCornerShape(minDim * 0.033f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonBg,
                        contentColor = buttonText
                    )
                ) {
                    Text(
                        "Open",
                        fontSize = (minDim * 0.044f).value.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                // A hidden watch can be brought back without
                // typing anything: the session is still saved.
                val savedWatch = CameraLivePrefs.loadWatchSession(context)
                if (savedWatch != null && viewerOpen == null) {
                    Button(
                        onClick = { viewerOpen = savedWatch },
                        modifier = Modifier.fillMaxWidth().height(minDim * 0.12f),
                        shape = RoundedCornerShape(minDim * 0.033f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonBg,
                            contentColor = buttonText
                        )
                    ) {
                        Text(
                            "Resume watching Room ID ${savedWatch.second}",
                            fontSize = (minDim * 0.044f).value.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // CALL VIEW (host) - big = viewer, small = self. If the
    // viewer camera is off, there are no viewer frames yet, so
    // you are big instead. This is a view only - Stop is the
    // only action here; the stream itself is stopped from the
    // card or the notification.
    if (callViewOpen && isStreaming) {
        Dialog(
            onDismissRequest = { callViewOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().background(Color.Black)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(minDim * 0.028f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { callViewOpen = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(minDim * 0.067f))
                    }
                    Text(
                        "Room ID ${roomCode ?: ""}  •  Watching: $viewerCount",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = (minDim * 0.044f).value.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f)
                ) {
                    val big = viewerBitmap ?: selfBitmap
                    if (big != null) {
                        Image(
                            bitmap = big,
                            contentDescription = "Viewer camera",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Waiting for the camera…",
                                fontSize = (minDim * 0.044f).value.sp,
                                color = Color.White
                            )
                        }
                    }
                    // Small self view, bottom right.
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .padding(minDim * 0.028f)
                            .size(minDim * 0.30f, minDim * 0.40f)
                            .clip(RoundedCornerShape(minDim * 0.022f))
                            .background(thumbBg)
                            .border(1.dp, Color.White, RoundedCornerShape(minDim * 0.022f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selfBitmap != null) {
                            Image(
                                bitmap = selfBitmap!!,
                                contentDescription = "Your camera",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                "You",
                                fontSize = (minDim * 0.04f).value.sp,
                                color = Color.White
                            )
                        }
                    }
                }
                Button(
                    onClick = {
                        CameraLiveService.stop(context)
                        callViewOpen = false
                    },
                    modifier = Modifier.fillMaxWidth()
                        .padding(minDim * 0.028f)
                        .height(minDim * 0.12f),
                    shape = RoundedCornerShape(minDim * 0.033f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CalNyxRed,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        "Stop Camera",
                        fontSize = (minDim * 0.044f).value.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // VIEWER - full-screen dialog with the host camera in a
    // WebView (same relay viewer page as Screen Stream), your
    // own small self view, and your own Camera On/Off toggle.
    // The password goes in the URL fragment (#pw=...), so it is
    // never sent to the server as part of the address.
    viewerOpen?.let { (baseUrl, code, pw) ->
        val viewerUrl = try {
            "$baseUrl/?room=$code#pw=" + URLEncoder.encode(pw, "UTF-8")
        } catch (_: Exception) {
            "$baseUrl/?room=$code"
        }
        Dialog(
            // Back / X only HIDES the viewer - the watch session
            // stays saved and reopens when the Third Eye screen is
            // opened again. Your own camera turns off when the
            // viewer hides. "Stop watching" below ends it for good.
            onDismissRequest = { closeViewer() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().background(Color.Black)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(minDim * 0.028f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { closeViewer() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(minDim * 0.067f))
                    }
                    Text(
                        "Room ID $code",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = (minDim * 0.044f).value.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = {
                        try {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("$baseUrl/?room=$code")
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                    }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in browser", tint = Color.White, modifier = Modifier.size(minDim * 0.05f))
                    }
                }
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            webViewClient = WebViewClient()
                            loadUrl(viewerUrl)
                        }
                    },
                    onRelease = { webView ->
                        try {
                            webView.stopLoading()
                            webView.destroy()
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier.fillMaxSize().weight(1f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(minDim * 0.028f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Small self view.
                    Box(
                        modifier = Modifier
                            .size(minDim * 0.30f, minDim * 0.40f)
                            .clip(RoundedCornerShape(minDim * 0.022f))
                            .background(thumbBg)
                            .border(1.dp, Color.White, RoundedCornerShape(minDim * 0.022f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selfBitmap != null) {
                            Image(
                                bitmap = selfBitmap!!,
                                contentDescription = "Your camera",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                "Camera off",
                                fontSize = (minDim * 0.036f).value.sp,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(minDim * 0.028f))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(minDim * 0.012f)
                    ) {
                        Button(
                            onClick = { toggleOwnCamera(code, pw) },
                            enabled = !isStreaming && !isStarting,
                            modifier = Modifier.fillMaxWidth().height(minDim * 0.12f),
                            shape = RoundedCornerShape(minDim * 0.033f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isViewerPublishing || isViewerStarting) CalNyxRed else buttonBg,
                                contentColor = if (isViewerPublishing || isViewerStarting) Color.White else buttonText
                            )
                        ) {
                            Icon(
                                if (isViewerPublishing || isViewerStarting) Icons.Default.VideocamOff else Icons.Default.Videocam,
                                contentDescription = null,
                                modifier = Modifier.size(minDim * 0.05f)
                            )
                            Spacer(modifier = Modifier.width(minDim * 0.022f))
                            Text(
                                when {
                                    isViewerPublishing -> "My camera: On"
                                    isViewerStarting -> "Turning on..."
                                    else -> "My camera: Off"
                                },
                                fontSize = (minDim * 0.04f).value.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (isStreaming || isStarting) {
                            Text(
                                "You are sharing your own camera right now - stop it first.",
                                fontSize = (minDim * 0.033f).value.sp,
                                color = Color.White
                            )
                        }
                        Button(
                            onClick = {
                                CameraLivePrefs.clearWatchSession(context)
                                closeViewer()
                                note = "Stopped watching."
                            },
                            modifier = Modifier.fillMaxWidth().height(minDim * 0.12f),
                            shape = RoundedCornerShape(minDim * 0.033f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CalNyxRed,
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                "Stop watching",
                                fontSize = (minDim * 0.04f).value.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
