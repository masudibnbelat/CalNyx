// FILE: app/src/main/java/com/calnyx/app/update/UpdateDialog.kt
// UPDATE DIALOG V6 % (2026-11-20) - REPLACE. V5 look, logic,
// states and texts UNCHANGED. Only change: every size is now a %
// of the screen (LocalConfiguration - a dialog has no
// BoxWithConstraints window of its own). Colours were already all
// theme variables in V5 (surface / onSurface / surfaceVariant /
// onSurfaceVariant) - verified, no hardcoded colour anywhere.
// The 403 fallback lives in AppUpdateManager.kt, so this dialog
// only shows the error state when GitHub itself is unreachable.
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
//    CircularProgressIndicator (thin ring, 0.008f minDim stroke).
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
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
    // V6 %: sizes are % of the screen (360dp baseline phone).
    val configuration = LocalConfiguration.current
    val windowW = configuration.screenWidthDp.dp
    val minDim = minOf(windowW, configuration.screenHeightDp.dp)
    val outerPad = windowW * 0.067f
    val surfaceCorner = minDim * 0.067f
    val sidePad = minDim * 0.056f
    val topPad = minDim * 0.067f
    val bottomPad = minDim * 0.056f
    val gapS = minDim * 0.033f
    val gapM = minDim * 0.039f
    val gapL = minDim * 0.044f
    val gapXL = minDim * 0.056f
    val subTopPad = minDim * 0.017f
    val titleFont = (minDim * 0.056f).value.sp
    val subtitleFont = (minDim * 0.036f).value.sp
    val smallFont = (minDim * 0.033f).value.sp
    val buttonFont = (minDim * 0.044f).value.sp
    val badgeSize = minDim * 0.178f
    val badgeIcon = minDim * 0.089f
    val btnH = minDim * 0.144f
    val btnCorner = minDim * 0.044f
    val btnIcon = minDim * 0.056f
    val btnGap = minDim * 0.025f
    val ringSize = minDim * 0.289f
    val ringStroke = minDim * 0.025f
    val ringPctFont = (minDim * 0.061f).value.sp
    val ringSubFont = (minDim * 0.031f).value.sp
    val chipFont = (minDim * 0.039f).value.sp
    val chipPadH = minDim * 0.039f
    val chipPadV = minDim * 0.019f
    val arrowSize = minDim * 0.05f
    val arrowPad = minDim * 0.028f
    val pillFont = (minDim * 0.033f).value.sp
    val pillPadH = minDim * 0.033f
    val pillPadV = minDim * 0.014f
    val notePad = minDim * 0.039f
    val noteCorner = minDim * 0.044f
    val spinnerSize = minDim * 0.122f
    val spinnerStroke = minDim * 0.008f
    val closeSize = minDim * 0.094f
    val closePad = minDim * 0.028f
    val closeIcon = minDim * 0.05f
    val elevation = minDim * 0.017f
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
            shape = RoundedCornerShape(surfaceCorner),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = elevation,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = outerPad)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = sidePad)
                        .padding(top = topPad, bottom = bottomPad),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (val s = state) {
                        UpdateUiState.Checking -> {
                            StatusBadge(Icons.Default.SystemUpdate, badgeSize, badgeIcon)
                            Spacer(modifier = Modifier.height(gapL))
                            StateTitle("Checking for updates", titleFont)
                            StateSubtitle("Looking on GitHub for a newer CalNyx…", subtitleFont, subTopPad)
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(top = gapXL)
                                    .size(spinnerSize),
                                color = MaterialTheme.colorScheme.onSurface,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeWidth = spinnerStroke
                            )
                        }

                        UpdateUiState.UpToDate -> {
                            StatusBadge(Icons.Default.Check, badgeSize, badgeIcon)
                            Spacer(modifier = Modifier.height(gapL))
                            StateTitle("You're up to date", titleFont)
                            StateSubtitle("CalNyx v$current is the latest version", subtitleFont, subTopPad)
                            Spacer(modifier = Modifier.height(gapXL))
                            GhostButton("Close", onDismiss, btnH, btnCorner, buttonFont)
                        }

                        is UpdateUiState.Available -> {
                            StatusBadge(Icons.Default.SystemUpdate, badgeSize, badgeIcon)
                            Spacer(modifier = Modifier.height(gapL))
                            StateTitle("Update available", titleFont)
                            Spacer(modifier = Modifier.height(gapS))
                            VersionChips(current, s.info.version, chipFont, chipPadH, chipPadV, arrowSize, arrowPad)
                            if (s.info.sizeBytes > 0) {
                                Spacer(modifier = Modifier.height(gapS))
                                SizePill(s.info.sizeBytes, pillFont, pillPadH, pillPadV)
                            }
                            if (s.info.notes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(gapM))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(noteCorner))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(notePad)
                                ) {
                                    Text(
                                        text = s.info.notes,
                                        fontSize = subtitleFont,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 8,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(gapXL))
                            PrimaryButton(
                                text = "Download update",
                                icon = Icons.Default.Download,
                                height = btnH,
                                corner = btnCorner,
                                font = buttonFont,
                                iconSize = btnIcon,
                                gap = btnGap
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
                                version = s.info.version,
                                size = ringSize,
                                stroke = ringStroke,
                                pctFont = ringPctFont,
                                subFont = ringSubFont
                            )
                            Spacer(modifier = Modifier.height(gapL))
                            StateTitle("Downloading update", titleFont)
                            StateSubtitle(
                                text = if (s.info.sizeBytes > 0) {
                                    "${formatMb(s.progress.toDouble() * s.info.sizeBytes.toDouble())} MB" +
                                            " of ${formatMb(s.info.sizeBytes.toDouble())} MB"
                                } else {
                                    "Keep the app open until it finishes…"
                                },
                                font = subtitleFont,
                                topPad = subTopPad
                            )
                        }

                        is UpdateUiState.Ready -> {
                            StatusBadge(Icons.Default.Check, badgeSize, badgeIcon)
                            Spacer(modifier = Modifier.height(gapL))
                            StateTitle("Ready to install", titleFont)
                            StateSubtitle("CalNyx v${s.info.version} has been downloaded", subtitleFont, subTopPad)
                            Spacer(modifier = Modifier.height(gapXL))
                            PrimaryButton(
                                text = "Install",
                                icon = Icons.Default.Check,
                                height = btnH,
                                corner = btnCorner,
                                font = buttonFont,
                                iconSize = btnIcon,
                                gap = btnGap
                            ) {
                                AppUpdateManager.installApk(context, s.file)
                            }
                            Text(
                                text = "If Android asks, allow \"Install unknown apps\" " +
                                        "for CalNyx, then tap Install again.",
                                fontSize = smallFont,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = gapS)
                            )
                            TextButton(onClick = onDismiss) {
                                Text(
                                    text = "Later",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is UpdateUiState.Failed -> {
                            StatusBadge(Icons.Default.ErrorOutline, badgeSize, badgeIcon)
                            Spacer(modifier = Modifier.height(gapL))
                            StateTitle("Couldn't check for updates", titleFont)
                            StateSubtitle(s.message, subtitleFont, subTopPad)
                            Spacer(modifier = Modifier.height(gapXL))
                            PrimaryButton(
                                text = "Retry",
                                icon = Icons.Default.Refresh,
                                height = btnH,
                                corner = btnCorner,
                                font = buttonFont,
                                iconSize = btnIcon,
                                gap = btnGap
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
                        .padding(closePad)
                        .size(closeSize)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        )
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(closeIcon),
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
private fun StatusBadge(icon: ImageVector, size: Dp, iconSize: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.surface
        )
    }
}

// Download progress: one plain ring, the percentage in the
// middle. The ring follows the download progress directly -
// no smoothing animation.
@Composable
private fun ProgressRing(
    progress: Float,
    version: String,
    size: Dp,
    stroke: Dp,
    pctFont: TextUnit,
    subFont: TextUnit
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ringStroke = Stroke(width = stroke.toPx(), cap = StrokeCap.Round)
            drawCircle(color = track, style = ringStroke)
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = ringStroke
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = pctFont,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "of v$version",
                fontSize = subFont,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StateTitle(text: String, font: TextUnit) {
    Text(
        text = text,
        fontSize = font,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun StateSubtitle(text: String, font: TextUnit, topPad: Dp) {
    Text(
        text = text,
        fontSize = font,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = topPad)
    )
}

@Composable
private fun VersionChips(
    current: String,
    new: String,
    font: TextUnit,
    padH: Dp,
    padV: Dp,
    arrow: Dp,
    arrowPad: Dp
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        VersionChip(text = "v$current", active = false, font, padH, padV)
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier
                .padding(horizontal = arrowPad)
                .size(arrow),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        VersionChip(text = "v$new", active = true, font, padH, padV)
    }
}

@Composable
private fun VersionChip(text: String, active: Boolean, font: TextUnit, padH: Dp, padV: Dp) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(
                if (active) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(horizontal = padH, vertical = padV)
    ) {
        Text(
            text = text,
            fontSize = font,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SizePill(sizeBytes: Long, font: TextUnit, padH: Dp, padV: Dp) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = padH, vertical = padV)
    ) {
        Text(
            text = "APK size  •  ${formatMb(sizeBytes.toDouble())} MB",
            fontSize = font,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Primary action: one solid inverted colour, rounded, normal
// ripple. No press-in scale, no gradients, no shimmer.
@Composable
private fun PrimaryButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    height: Dp,
    corner: Dp,
    font: TextUnit,
    iconSize: Dp,
    gap: Dp
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.onSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.surface
            )
            Spacer(modifier = Modifier.width(gap))
            Text(
                text = text,
                fontSize = font,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.surface
            )
        }
    }
}

@Composable
private fun GhostButton(
    text: String,
    onClick: () -> Unit,
    height: Dp,
    corner: Dp,
    font: TextUnit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(corner)
    ) {
        Text(
            text = text,
            fontSize = font,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatMb(bytes: Double): String =
    String.format(Locale.US, "%.1f", bytes / (1024.0 * 1024.0))
