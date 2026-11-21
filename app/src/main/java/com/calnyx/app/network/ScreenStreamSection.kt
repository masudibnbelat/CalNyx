// FILE: app/src/main/java/com/calnyx/app/network/ScreenStreamSection.kt
// NEW FILE - Screen Stream section for the Network screen (2026-11-21).
// Add ONE call in NetworkScreen.kt, in NetworkContent, after the
// "Open in WhatsApp" Card and before the final Spacer:
//
//   ScreenStreamSection(
//       cardBg = cardBg,
//       cardBorder = cardBorder,
//       onPageColor = onPageColor,
//       thumbBg = thumbBg,
//       minDim = minDim
//   )
//
// Same package as NetworkScreen, so no import is needed for it.
// Two clean cards, % sizes and theme colours like the rest of
// the Network screen, no fixed dp/sp except 1.dp borders:
//
//   1. WATCH A SCREEN - two ways in: paste someone else's link,
//      or type their Room ID - then the password, tap Open, and
//      their screen plays in the app (WebView). The password
//      travels as a #pw= fragment, so it is never sent to the
//      server in the URL.
//   MINIMAL PASS (2026-11-21): everything made smaller - tighter
//      card padding, gaps, titles, icons, buttons and fonts - so
//      the section takes less room and feels light. Still % sizes
//      only, still Material 3, still theme colours only.
//   2. SHARE MY SCREEN - type a password (mandatory, at least
//      2 characters), tap Start Stream, the phone's screen goes
//      live through the relay server and a link appears to copy
//      and send to anyone. Anyone opening it in any browser
//      types the password and watches. Quality (Low / Medium /
//      High) sits right in the card as three small pills - no
//      Settings button, no expander (2026-11-21).
//   LIVE BLOCK REDESIGN (2026-11-21): while streaming, three
//      compact copy rows - Stream link, Room ID, Password - each
//      with its own copy button. "Room" is now called "Room ID"
//      everywhere. Tight spacing, % sizes only, all colours from
//      the theme (colorScheme + the card colours passed in).
//
// RELAY URL IS CODE-ONLY (2026-11-21): the server address lives
// only in ScreenStreamPrefs.DEFAULT_RELAY_URL - it is NEVER
// shown or edited in the app UI, so users never see it. If the
// relay ever moves, change that one line in the code.
// The share link itself must contain the address (that is how
// a browser reaches the stream) - that link is only shown while
// streaming, to be copied and sent.
//
// The system capture dialog appears every time a stream starts
// (Android's rule). The stream stops if the phone locks or the
// screen turns off - keep the screen on while streaming.
// Sound is NOT streamed - picture only.
package com.calnyx.app.network

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.ScreenShare
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
private fun StreamCopyRow(
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
fun ScreenStreamSection(
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

    // Relay URL: code-only, from ScreenStreamPrefs. Never shown
    // or edited in the UI - users must never see the server address.
    val relayUrl = ScreenStreamPrefs.savedRelayUrl(context)

    var note by remember { mutableStateOf<String?>(null) }

    // WATCH - someone else's stream.
    var watchUrl by remember { mutableStateOf("") }
    var watchRoomId by remember { mutableStateOf("") }
    var watchPassword by remember { mutableStateOf("") }
    var watchPasswordVisible by remember { mutableStateOf(false) }
    // baseUrl + room + password of the stream being watched.
    var viewerOpen by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    // SHARE - my stream.
    var sharePassword by remember { mutableStateOf(ScreenStreamPrefs.savedPassword(context)) }
    var sharePasswordVisible by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf(ScreenStreamPrefs.savedQuality(context)) }
    var waitingProjection by remember { mutableStateOf(false) }

    // Live state from the service, polled like Third Eye does.
    var isStreaming by remember { mutableStateOf(ScreenStreamService.isStreaming) }
    var isStarting by remember { mutableStateOf(ScreenStreamService.isStarting) }
    var roomCode by remember { mutableStateOf(ScreenStreamService.roomCode) }
    var streamUrl by remember { mutableStateOf(ScreenStreamService.streamUrl) }
    var viewerCount by remember { mutableStateOf(ScreenStreamService.viewerCount) }
    var elapsedSec by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            isStreaming = ScreenStreamService.isStreaming
            isStarting = ScreenStreamService.isStarting
            roomCode = ScreenStreamService.roomCode
            streamUrl = ScreenStreamService.streamUrl
            viewerCount = ScreenStreamService.viewerCount
            if (isStreaming) {
                elapsedSec =
                    (System.currentTimeMillis() - ScreenStreamService.startedAtMillis) / 1000
            } else {
                elapsedSec = 0L
            }
            ScreenStreamService.takeError()?.let { note = it }
            delay(500)
        }
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        waitingProjection = false
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenStreamService.start(
                context, result.resultCode, result.data!!,
                sharePassword, relayUrl, quality
            )
        } else {
            note = "Screen sharing was not allowed."
        }
    }

    fun startStream() {
        if (sharePassword.length < 2) {
            note = "Password must be at least 2 characters."
            return
        }
        if (!relayUrl.startsWith("http://") && !relayUrl.startsWith("https://")) {
            note = "Relay server address is not valid."
            return
        }
        ScreenStreamPrefs.savePassword(context, sharePassword)
        ScreenStreamPrefs.saveQuality(context, quality)
        note = null
        val manager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        waitingProjection = true
        try {
            projectionLauncher.launch(manager.createScreenCaptureIntent())
        } catch (_: Exception) {
            waitingProjection = false
            note = "Screen sharing is not available on this phone."
        }
    }

    fun openWatch() {
        // Two ways in: a pasted link, or a typed Room ID.
        // The link wins when both are filled.
        val code = if (watchUrl.isNotBlank()) {
            ScreenStreamPrefs.parseRoomCode(watchUrl)
        } else {
            val typed = watchRoomId.trim().uppercase()
            if (Regex("^[A-Za-z0-9]{6}$").matches(typed)) typed else null
        }
        if (code == null) {
            note = if (watchUrl.isNotBlank()) "That link has no room code."
            else "Type the 6-letter Room ID."
            return
        }
        if (watchPassword.length < 2) {
            note = "Password must be at least 2 characters."
            return
        }
        note = null
        viewerOpen = Triple(
            ScreenStreamPrefs.parseBaseUrl(watchUrl, relayUrl),
            code,
            watchPassword
        )
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

        // WATCH A SCREEN - paste a link, type the password, Open.
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
                    Text("Watch a screen", fontWeight = FontWeight.SemiBold, fontSize = (minDim * 0.044f).value.sp, color = onPageColor, modifier = Modifier.weight(1f))
                }
                Text(
                    "Watch someone else's screen. Paste their link, or type their Room ID - then the password, and tap Open.",
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
                    placeholder = { Text("At least 2 characters") },
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
                    enabled = (watchUrl.isNotBlank() || watchRoomId.isNotBlank()) && watchPassword.length >= 2,
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
            }
        }

        // SHARE MY SCREEN - password, then Start Stream.
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
                        Icon(Icons.Default.ScreenShare, contentDescription = null, modifier = Modifier.size(minDim * 0.05f), tint = onPageColor)
                    }
                    Spacer(modifier = Modifier.width(minDim * 0.033f))
                    Text("Share my screen", fontWeight = FontWeight.SemiBold, fontSize = (minDim * 0.044f).value.sp, color = onPageColor, modifier = Modifier.weight(1f))
                }
                Text(
                    "Anyone with the link and password can watch in any browser. Keep the screen on while streaming.",
                    fontSize = (minDim * 0.036f).value.sp,
                    color = colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = sharePassword,
                    onValueChange = { sharePassword = it },
                    label = { Text("Stream password") },
                    placeholder = { Text("At least 2 characters") },
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
                // QUALITY - always visible, three small pills.
                // No Settings button and no expander: this is the
                // only setting, and the pills take very little room.
                Text(
                    "Quality",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (minDim * 0.036f).value.sp,
                    color = onPageColor
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("Low", "Medium", "High").forEachIndexed { index, q ->
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
                                q,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = (minDim * 0.036f).value.sp,
                                color = if (quality == q) buttonText else onPageColor
                            )
                        }
                    }
                }
                Button(
                    onClick = {
                        if (isStreaming || isStarting) ScreenStreamService.stop(context)
                        else startStream()
                    },
                    enabled = !waitingProjection && (isStreaming || isStarting || sharePassword.length >= 2),
                    modifier = Modifier.fillMaxWidth().height(minDim * 0.12f),
                    shape = RoundedCornerShape(minDim * 0.033f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isStreaming || isStarting) CalNyxRed else buttonBg,
                        contentColor = if (isStreaming || isStarting) Color.White else buttonText
                    )
                ) {
                    Text(
                        when {
                            isStreaming -> "Stop Streaming"
                            isStarting || waitingProjection -> "Starting..."
                            else -> "Start Stream"
                        },
                        fontSize = (minDim * 0.044f).value.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                // Status + error, right under the button where the
                // user is looking - the note at the top of the
                // section is too far up to notice while starting.
                if (isStarting || waitingProjection) {
                    Text(
                        "Connecting to the relay server… If the server was asleep, this can take up to a minute.",
                        fontSize = (minDim * 0.036f).value.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                if (note != null) {
                    Text(
                        text = note ?: "",
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
                    streamUrl?.let { url ->
                        StreamCopyRow(
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
                        StreamCopyRow(
                            label = "Room ID",
                            value = code,
                            onCopy = { copyText("Room ID", code) },
                            minDim = minDim,
                            onPageColor = onPageColor,
                            labelColor = colorScheme.onSurfaceVariant,
                            rowBg = thumbBg
                        )
                    }
                    StreamCopyRow(
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
    }

    // VIEWER - full-screen dialog with the stream in a WebView.
    // The password goes in the URL fragment (#pw=...), so it is
    // never sent to the server as part of the address.
    viewerOpen?.let { (baseUrl, code, pw) ->
        val viewerUrl = try {
            "$baseUrl/?room=$code#pw=" + URLEncoder.encode(pw, "UTF-8")
        } catch (_: Exception) {
            "$baseUrl/?room=$code"
        }
        Dialog(
            onDismissRequest = { viewerOpen = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().background(Color.Black)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(minDim * 0.028f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewerOpen = null }) {
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
            }
        }
    }
}
