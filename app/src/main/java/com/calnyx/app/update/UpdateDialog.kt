// FILE: app/src/main/java/com/calnyx/app/update/UpdateDialog.kt
package com.calnyx.app.update

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private sealed interface UpdateUiState {
    object Checking : UpdateUiState
    object UpToDate : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateUiState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}

// V4: simple modern. No hero band, no aurora, no particles — one
// clean card. A circular status badge (or a plain progress ring
// while downloading) sits at the top and morphs with the state;
// title, subtitle and one solid primary button follow. The motion
// that stays is the motion that reads: the card springs in, the
// badge pops, the check draws itself, states cross-fade.
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

    val entry = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entry.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio = 0.72f,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    val e = entry.value
                    scaleX = 0.9f + 0.1f * e
                    scaleY = 0.9f + 0.1f * e
                    alpha = e
                    translationY = (1f - e) * 60f
                }
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                AnimatedContent(
                    targetState = state,
                    contentKey = { it.javaClass },
                    transitionSpec = {
                        (fadeIn(
                            tween(durationMillis = 300, easing = FastOutSlowInEasing)
                        ) + scaleIn(
                            tween(durationMillis = 300, easing = FastOutSlowInEasing),
                            initialScale = 0.97f
                        )) togetherWith (fadeOut(
                            tween(durationMillis = 140, easing = LinearEasing)
                        ) + scaleOut(
                            tween(durationMillis = 140, easing = LinearEasing),
                            targetScale = 0.98f
                        ))
                    },
                    label = "updateContent",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 30.dp, bottom = 24.dp)
                ) { s ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (s) {
                            UpdateUiState.Checking -> {
                                StatusBadge(
                                    container = MaterialTheme.colorScheme.primaryContainer,
                                    pulse = true
                                ) {
                                    Icon(
                                        Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(18.dp))
                                FadeSlideIn(delayMillis = 0) {
                                    StateTitle("Checking for updates")
                                }
                                FadeSlideIn(delayMillis = 70) {
                                    StateSubtitle("Looking on GitHub for a newer CalNyx…")
                                }
                                FadeSlideIn(delayMillis = 140) {
                                    BouncingDots()
                                }
                            }

                            UpdateUiState.UpToDate -> {
                                StatusBadge(
                                    container = MaterialTheme.colorScheme.tertiaryContainer,
                                ) {
                                    AnimatedCheck(MaterialTheme.colorScheme.tertiary)
                                }
                                Spacer(modifier = Modifier.height(18.dp))
                                FadeSlideIn(delayMillis = 0) {
                                    StateTitle("You're up to date")
                                }
                                FadeSlideIn(delayMillis = 70) {
                                    StateSubtitle("CalNyx v$current is the latest version")
                                }
                                Spacer(modifier = Modifier.height(22.dp))
                                FadeSlideIn(delayMillis = 150) {
                                    GhostButton("Close", onDismiss)
                                }
                            }

                            is UpdateUiState.Available -> {
                                StatusBadge(
                                    container = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Icon(
                                        Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(18.dp))
                                FadeSlideIn(delayMillis = 0) {
                                    StateTitle("Update available")
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                FadeSlideIn(delayMillis = 80) {
                                    VersionChips(current = current, new = s.info.version)
                                }
                                if (s.info.sizeBytes > 0) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    FadeSlideIn(delayMillis = 140) {
                                        SizePill(s.info.sizeBytes)
                                    }
                                }
                                if (s.info.notes.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    FadeSlideIn(delayMillis = 200) {
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
                                }
                                Spacer(modifier = Modifier.height(22.dp))
                                FadeSlideIn(delayMillis = 270) {
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
                                }
                                FadeSlideIn(delayMillis = 330) {
                                    TextButton(onClick = onDismiss) {
                                        Text(
                                            text = "Later",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            is UpdateUiState.Downloading -> {
                                ProgressRing(
                                    progress = s.progress,
                                    version = s.info.version,
                                    accent = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                                FadeSlideIn(delayMillis = 0) {
                                    StateTitle("Downloading update")
                                }
                                FadeSlideIn(delayMillis = 70) {
                                    StateSubtitle(
                                        if (s.info.sizeBytes > 0) {
                                            "${formatMb(s.progress.toDouble() * s.info.sizeBytes.toDouble())} MB" +
                                                    " of ${formatMb(s.info.sizeBytes.toDouble())} MB"
                                        } else {
                                            "Keep the app open until it finishes…"
                                        }
                                    )
                                }
                            }

                            is UpdateUiState.Ready -> {
                                StatusBadge(
                                    container = MaterialTheme.colorScheme.tertiaryContainer,
                                ) {
                                    AnimatedCheck(MaterialTheme.colorScheme.tertiary)
                                }
                                Spacer(modifier = Modifier.height(18.dp))
                                FadeSlideIn(delayMillis = 0) {
                                    StateTitle("Ready to install")
                                }
                                FadeSlideIn(delayMillis = 70) {
                                    StateSubtitle("CalNyx v${s.info.version} has been downloaded")
                                }
                                Spacer(modifier = Modifier.height(22.dp))
                                FadeSlideIn(delayMillis = 150) {
                                    PrimaryButton(
                                        text = "Install",
                                        icon = Icons.Default.Check
                                    ) {
                                        AppUpdateManager.installApk(context, s.file)
                                    }
                                }
                                FadeSlideIn(delayMillis = 210) {
                                    Text(
                                        text = "If Android asks, allow \"Install unknown apps\" " +
                                                "for CalNyx, then tap Install again.",
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 12.dp)
                                    )
                                }
                                FadeSlideIn(delayMillis = 260) {
                                    TextButton(onClick = onDismiss) {
                                        Text(
                                            text = "Later",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            is UpdateUiState.Failed -> {
                                ErrorBadge()
                                Spacer(modifier = Modifier.height(18.dp))
                                FadeSlideIn(delayMillis = 0) {
                                    StateTitle("Couldn't check for updates")
                                }
                                FadeSlideIn(delayMillis = 70) {
                                    StateSubtitle(s.message)
                                }
                                Spacer(modifier = Modifier.height(22.dp))
                                FadeSlideIn(delayMillis = 150) {
                                    PrimaryButton(
                                        text = "Retry",
                                        icon = Icons.Default.Refresh
                                    ) { check() }
                                }
                                FadeSlideIn(delayMillis = 210) {
                                    TextButton(onClick = onDismiss) {
                                        Text(
                                            text = "Close",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
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

// One clean circular badge: tinted container, an icon or the
// animated check inside. It pops in with a spring, cross-fades its
// colour when the state family changes, and (while checking)
// breathes gently so the wait feels alive without being busy.
@Composable
private fun StatusBadge(
    container: Color,
    pulse: Boolean = false,
    content: @Composable () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = container,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "badgeBg"
    )
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        pop.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio = 0.55f,
                stiffness = Spring.StiffnessMedium
            )
        )
    }
    val pulseTransition = rememberInfiniteTransition(label = "badgePulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badgePulseScale"
    )
    val scale = pop.value * if (pulse) pulseScale else 1f
    Box(
        modifier = Modifier
            .size(76.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// The error badge: same clean circle, one short shake on entry.
@Composable
private fun ErrorBadge() {
    val offsetX = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        offsetX.animateTo(
            0f,
            animationSpec = keyframes {
                durationMillis = 480
                0f at 0
                (-14f) at 70
                14f at 150
                (-8f) at 240
                8f at 330
                0f at 480
            }
        )
    }
    Box(
        modifier = Modifier
            .size(76.dp)
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.error
        )
    }
}

// A checkmark that draws itself, stroke by stroke.
@Composable
private fun AnimatedCheck(accent: Color) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(140)
        p.animateTo(
            1f,
            animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
        )
    }
    Canvas(modifier = Modifier.size(40.dp)) {
        val start = Offset(size.width * 0.26f, size.height * 0.53f)
        val mid = Offset(size.width * 0.44f, size.height * 0.70f)
        val end = Offset(size.width * 0.76f, size.height * 0.32f)
        val v = p.value
        if (v <= 0.42f) {
            val f = v / 0.42f
            drawLine(
                color = accent,
                start = start,
                end = Offset(
                    start.x + (mid.x - start.x) * f,
                    start.y + (mid.y - start.y) * f
                ),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round
            )
        } else {
            val f = (v - 0.42f) / 0.58f
            drawLine(
                color = accent,
                start = start,
                end = mid,
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = accent,
                start = mid,
                end = Offset(
                    mid.x + (end.x - mid.x) * f,
                    mid.y + (end.y - mid.y) * f
                ),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

// Download progress: one plain ring in the accent colour, the
// percentage in the middle. This is the only progress indicator —
// the MB count below it is text, not a second bar.
@Composable
private fun ProgressRing(progress: Float, version: String, accent: Color) {
    val animated by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "ringProgress"
    )
    val track = MaterialTheme.colorScheme.surfaceVariant
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
                sweepAngle = 360f * animated.coerceIn(0f, 1f),
                useCenter = false,
                style = stroke
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${(animated * 100).toInt()}%",
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

// Staggered entrance for body pieces: fades in while rising.
@Composable
private fun FadeSlideIn(delayMillis: Int, content: @Composable () -> Unit) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        anim.animateTo(
            1f,
            animationSpec = tween(
                durationMillis = 480,
                delayMillis = delayMillis,
                easing = FastOutSlowInEasing
            )
        )
    }
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = anim.value
            translationY = (1f - anim.value) * 56f
        }
    ) {
        content()
    }
}

@Composable
private fun BouncingDots() {
    val dots = rememberInfiniteTransition(label = "waitDots")
    Row(
        modifier = Modifier.padding(top = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        for (i in 0 until 3) {
            val y by dots.animateFloat(
                initialValue = 0f,
                targetValue = -9f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 380,
                        delayMillis = i * 120,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "waitDot$i"
            )
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .offset { IntOffset(0, y.roundToInt()) }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
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
        fontSize = 13.5.sp,
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
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio = 0.55f,
                stiffness = Spring.StiffnessMedium
            )
        )
    }
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(RoundedCornerShape(percent = 50))
            .background(
                if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) MaterialTheme.colorScheme.primary
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

// Primary action: one solid colour, rounded, with a physical
// press-in scale. No gradients, no shimmer.
@Composable
private fun PrimaryButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "primaryPress"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary
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
