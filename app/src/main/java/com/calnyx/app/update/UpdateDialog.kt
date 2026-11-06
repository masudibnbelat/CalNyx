// FILE: app/src/main/java/com/calnyx/app/update/UpdateDialog.kt
// REPLACE - V5: lightweight, modern, black / white only.
// What changed vs V4 (logic untouched - same states, same texts,
// same AppUpdateManager calls, same UpdateDialog(onDismiss) API):
//  - REMOVED the card spring-in (Animatable + spring, scale + fade
//    + 60px rise), the AnimatedContent state cross-fade/scale, and
//    the staggered FadeSlideIn on every title, subtitle and button.
//    States now switch instantly - the dialog just appears.
//  - REMOVED the badge pop spring, the infinite breathing pulse
//    while checking, the error shake, and the checkmark that drew
//    itself stroke by stroke. The badge is now one static circle.
//  - REMOVED the bouncing dots. While checking, the only motion
//    left is one thin circular spinner - the one piece that is
//    actually needed, so the wait does not look frozen.
//    V5.1 (2026-11-07): the first V5 used a LinearProgressIndicator
//    here and it looked ancient - replaced with a modern
//    CircularProgressIndicator (thin ring, 3.dp stroke).
//  - REMOVED the progress ring's animateFloatAsState smoothing:
//    the ring now follows the download progress directly (the
//    downloader already reports it per 64KB chunk).
//  - REMOVED the press-in scale on the primary button: plain
//    clickable with the normal ripple instead.
//  - COLOURS: no more primary / primaryContainer / tertiary /
//    tertiaryContainer blue from the theme (that light-blue circle
//    in the screenshot). Everything is onSurface / surface /
//    surfaceVariant now, so the dialog matches the rest of the
//    app in both themes. The badge is an inverted circle
//    (onSurface background, surface icon), the primary button is
//    inverted the same way.
//  - TIGHTER layout: smaller badge (64dp), tighter spacing, so
//    the dialog feels light instead of heavy.
// AppUpdateManager.kt is NOT changed.
package com.calnyx.app.update

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private sealed interface UpdateUiState {
    object Checking : UpdateUiState
    object UpToDate : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateUiState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}

@Composable
fun UpdateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = remember { AppUpdateManager.currentVersion(context) }
    var state by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Checking) }

    fun check() {
        state = UpdateUiState.Checking
        scope.launch {
            AppUpdateManager.checkForUpdate(context).fold(
                onSuccess = { info ->
                    state = if (info == null) {
                        UpdateUiState.UpToDate
                    } else {
                        UpdateUiState.Available(info)
                    }
                },
                onFailure = {
                    state = UpdateUiState.Failed(it.message ?: "Could not check for updates")
                }
            )
        }
    }

    LaunchedEffect(Unit) { check() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 24.dp, bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (val s = state) {
                        UpdateUiState.Checking -> {
                            StatusBadge(Icons.Default.SystemUpdate)
                            Spacer(modifier = Modifier.height(16.dp))
                            StateTitle("Checking for updates")
                            StateSubtitle("Looking on GitHub for a newer CalNyx…")
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(top = 20.dp)
                                    .size(44.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeWidth = 3.dp
                            )
                        }

                        UpdateUiState.UpToDate -> {
                            StatusBadge(Icons.Default.Check)
                            Spacer(modifier = Modifier.height(16.dp))
                            StateTitle("You're up to date")
                            StateSubtitle("CalNyx v$current is the latest version")
                            Spacer(modifier = Modifier.height(20.dp))
                            GhostButton("Close", onDismiss)
                        }

                        is UpdateUiState.Available -> {
                            StatusBadge(Icons.Default.SystemUpdate)
                            Spacer(modifier = Modifier.height(16.dp))
                            StateTitle("Update available")
                            Spacer(modifier = Modifier.height(12.dp))
                            VersionChips(current = current, new = s.info.version)
                            if (s.info.sizeBytes > 0) {
                                Spacer(modifier = Modifier.height(12.dp))
                                SizePill(s.info.sizeBytes)
                            }
                            if (s.info.notes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(14.dp)
                                ) {
                                    Text(
                                        text = s.info.notes,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 8,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            PrimaryButton(
                                text = "Download update",
                                icon = Icons.Default.Download
                            ) {
                                state = UpdateUiState.Downloading(s.info, 0f)
                                scope.launch {
                                    try {
                                        val file = AppUpdateManager.downloadApk(
                                            context,
                                            s.info
                                        ) { progress ->
                                            state = UpdateUiState.Downloading(
                                                s.info,
                                                progress
                                            )
                                        }
                                        state = UpdateUiState.Ready(s.info, file)
                                    } catch (e: Exception) {
                                        state = UpdateUiState.Failed(
                                            e.message ?: "Download failed"
                                        )
                                    }
                                }
                            }
                            TextButton(onClick = onDismiss) {
                                Text(
                                    text = "Later",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is UpdateUiState.Downloading -> {
                            ProgressRing(
                                progress = s.progress,
                                version = s.info.version
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            StateTitle("Downloading update")
                            StateSubtitle(
                                if (s.info.sizeBytes > 0) {
                                    "${formatMb(s.progress.toDouble() * s.info.sizeBytes.toDouble())} MB" +
                                            " of ${formatMb(s.info.sizeBytes.toDouble())} MB"
                                } else {
                                    "Keep the app open until it finishes…"
                                }
                            )
                        }

                        is UpdateUiState.Ready -> {
                            StatusBadge(Icons.Default.Check)
                            Spacer(modifier = Modifier.height(16.dp))
                            StateTitle("Ready to install")
                            StateSubtitle("CalNyx v${s.info.version} has been downloaded")
                            Spacer(modifier = Modifier.height(20.dp))
                            PrimaryButton(
                                text = "Install",
                                icon = Icons.Default.Check
                            ) {
                                AppUpdateManager.installApk(context, s.file)
                            }
                            Text(
                                text = "If Android asks, allow \"Install unknown apps\" " +
                                        "for CalNyx, then tap Install again.",
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                            TextButton(onClick = onDismiss) {
                                Text(
                                    text = "Later",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is UpdateUiState.Failed -> {
                            StatusBadge(Icons.Default.ErrorOutline)
                            Spacer(modifier = Modifier.height(16.dp))
                            StateTitle("Couldn't check for updates")
                            StateSubtitle(s.message)
                            Spacer(modifier = Modifier.height(20.dp))
                            PrimaryButton(
                                text = "Retry",
                                icon = Icons.Default.Refresh
                            ) { check() }
                            TextButton(onClick = onDismiss) {
                                Text(
                                    text = "Close",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        )
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

// One static circular badge: inverted colours (onSurface circle,
// surface icon). No pop, no pulse, no colour morph.
@Composable
private fun StatusBadge(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.surface
        )
    }
}

// Download progress: one plain ring, the percentage in the
// middle. The ring follows the download progress directly -
// no smoothing animation.
@Composable
private fun ProgressRing(progress: Float, version: String) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier.size(104.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(color = track, style = stroke)
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = stroke
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "of v$version",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StateTitle(text: String) {
    Text(
        text = text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun StateSubtitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun VersionChips(current: String, new: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        VersionChip(text = "v$current", active = false)
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        VersionChip(text = "v$new", active = true)
    }
}

@Composable
private fun VersionChip(text: String, active: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(
                if (active) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SizePill(sizeBytes: Long) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = "APK size  •  ${formatMb(sizeBytes.toDouble())} MB",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Primary action: one solid inverted colour, rounded, normal
// ripple. No press-in scale, no gradients, no shimmer.
@Composable
private fun PrimaryButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.surface
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.surface
            )
        }
    }
}

@Composable
private fun GhostButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatMb(bytes: Double): String =
    String.format(Locale.US, "%.1f", bytes / (1024.0 * 1024.0))
