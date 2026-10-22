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
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
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
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private sealed interface UpdateUiState {
    object Checking : UpdateUiState
    object UpToDate : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateUiState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}

// V3: a floating glass card, not a bottom sheet. An aurora-gradient
// hero with rotating orbit rings and drifting particles lives at
// the top; its centre morphs per state (bouncing icon, self-drawing
// check + confetti, gradient progress ring, shaking error). The body
// below staggers in piece by piece and the primary button carries a
// shimmer sweep, so something is always visibly moving.
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
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .graphicsLayer {
                    val e = entry.value
                    scaleX = 0.86f + 0.14f * e
                    scaleY = 0.86f + 0.14f * e
                    alpha = e
                    translationY = (1f - e) * 90f
                }
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Hero(state = state)

                    AnimatedContent(
                        targetState = state,
                        contentKey = { it.javaClass },
                        transitionSpec = {
                            (fadeIn(
                                tween(durationMillis = 320, easing = FastOutSlowInEasing)
                            ) + scaleIn(
                                tween(durationMillis = 320, easing = FastOutSlowInEasing),
                                initialScale = 0.96f
                            )) togetherWith (fadeOut(
                                tween(durationMillis = 150, easing = LinearEasing)
                            ) + scaleOut(
                                tween(durationMillis = 150, easing = LinearEasing),
                                targetScale = 0.98f
                            ))
                        },
                        label = "updateBody",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(top = 20.dp, bottom = 24.dp)
                    ) { s ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            when (s) {
                                UpdateUiState.Checking -> {
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
                                        GradientButton(
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
                                    Spacer(modifier = Modifier.height(16.dp))
                                    FadeSlideIn(delayMillis = 140) {
                                        GradientProgressBar(progress = s.progress)
                                    }
                                }

                                is UpdateUiState.Ready -> {
                                    FadeSlideIn(delayMillis = 0) {
                                        StateTitle("Ready to install")
                                    }
                                    FadeSlideIn(delayMillis = 70) {
                                        StateSubtitle("CalNyx v${s.info.version} has been downloaded")
                                    }
                                    Spacer(modifier = Modifier.height(22.dp))
                                    FadeSlideIn(delayMillis = 150) {
                                        GradientButton(
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
                                    FadeSlideIn(delayMillis = 0) {
                                        StateTitle("Couldn't check for updates")
                                    }
                                    FadeSlideIn(delayMillis = 70) {
                                        StateSubtitle(s.message)
                                    }
                                    Spacer(modifier = Modifier.height(22.dp))
                                    FadeSlideIn(delayMillis = 150) {
                                        GradientButton(
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
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
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

// The animated top zone: drifting aurora blobs + particles + orbit
// rings behind a centre that morphs with the state. The whole hero
// tint cross-fades when the state family changes.
@Composable
private fun Hero(state: UpdateUiState) {
    val isError = state is UpdateUiState.Failed
    val isSuccess = state is UpdateUiState.UpToDate || state is UpdateUiState.Ready
    val heroTarget = when {
        isError -> MaterialTheme.colorScheme.errorContainer
        isSuccess -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val heroColor by animateColorAsState(
        targetValue = heroTarget,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "heroColor"
    )
    val accent = when {
        isError -> MaterialTheme.colorScheme.error
        isSuccess -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    val drift = rememberInfiniteTransition(label = "heroDrift")
    val phase by drift.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "heroPhase"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(188.dp)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        heroColor,
                        MaterialTheme.colorScheme.secondaryContainer,
                        heroColor
                    ),
                    start = Offset.Zero,
                    end = Offset.Infinite
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Drifting aurora blobs
        Box(
            modifier = Modifier
                .size(190.dp)
                .offset {
                    IntOffset(
                        (cos(phase * 6.283f) * 90f).roundToInt() - 130,
                        (sin(phase * 6.283f) * 34f).roundToInt() - 60
                    )
                }
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.25f))
        )
        Box(
            modifier = Modifier
                .size(150.dp)
                .offset {
                    IntOffset(
                        (sin(phase * 6.283f + 2.1f) * 100f).roundToInt() + 120,
                        (cos(phase * 6.283f + 2.1f) * 30f).roundToInt() + 55
                    )
                }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f))
        )

        // Floating particles
        Canvas(modifier = Modifier.fillMaxSize()) {
            val spots = listOf(
                0.12f to 0.28f, 0.24f to 0.72f, 0.82f to 0.24f,
                0.90f to 0.66f, 0.68f to 0.84f, 0.38f to 0.16f
            )
            spots.forEachIndexed { i, (fx, fy) ->
                val wobble = sin(phase * 6.283f + i * 1.7f) * 9f
                drawCircle(
                    color = accent.copy(alpha = 0.35f),
                    radius = 3.5f + (i % 3) * 1.6f,
                    center = Offset(size.width * fx, size.height * fy + wobble)
                )
            }
        }

        // Rotating orbit rings
        Canvas(modifier = Modifier.size(158.dp)) {
            val ringStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(
                color = accent.copy(alpha = 0.16f),
                style = Stroke(width = 2.dp.toPx())
            )
            drawArc(
                color = accent,
                startAngle = phase * 360f,
                sweepAngle = 95f,
                useCenter = false,
                style = ringStroke
            )
            drawArc(
                color = accent.copy(alpha = 0.55f),
                startAngle = -phase * 360f + 140f,
                sweepAngle = 55f,
                useCenter = false,
                topLeft = Offset(14.dp.toPx(), 14.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(
                    size.width - 28.dp.toPx(),
                    size.height - 28.dp.toPx()
                ),
                style = ringStroke
            )
        }

        // Morphing centre
        AnimatedContent(
            targetState = state,
            contentKey = { it.javaClass },
            transitionSpec = {
                (fadeIn(
                    tween(durationMillis = 300, easing = FastOutSlowInEasing)
                ) + scaleIn(
                    tween(durationMillis = 300, easing = FastOutSlowInEasing),
                    initialScale = 0.7f
                )) togetherWith (fadeOut(
                    tween(durationMillis = 140, easing = LinearEasing)
                ) + scaleOut(
                    tween(durationMillis = 140, easing = LinearEasing),
                    targetScale = 0.8f
                ))
            },
            label = "heroCentre"
        ) { s ->
            Box(contentAlignment = Alignment.Center) {
                when (s) {
                    UpdateUiState.Checking -> CentreBadge(accent) { BouncingIcon(accent) }
                    is UpdateUiState.Available -> CentreBadge(accent) {
                        Icon(
                            Icons.Default.SystemUpdate,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = accent
                        )
                    }

                    UpdateUiState.UpToDate, is UpdateUiState.Ready -> {
                        ConfettiBurst(accent)
                        CentreBadge(accent) { AnimatedCheck(accent) }
                    }

                    is UpdateUiState.Downloading -> GradientProgressRing(
                        progress = s.progress,
                        version = s.info.version,
                        accent = accent
                    )

                    is UpdateUiState.Failed -> ShakeBadge()
                }
            }
        }
    }
}

@Composable
private fun CentreBadge(accent: Color, content: @Composable () -> Unit) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio = 0.5f,
                stiffness = Spring.StiffnessMedium
            )
        )
    }
    Box(
        modifier = Modifier
            .size(88.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.5.dp,
                color = accent.copy(alpha = 0.35f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun BouncingIcon(accent: Color) {
    val bounce = rememberInfiniteTransition(label = "iconBounce")
    val y by bounce.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 620, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconBounceY"
    )
    Icon(
        Icons.Default.SystemUpdate,
        contentDescription = null,
        modifier = Modifier
            .size(40.dp)
            .offset { IntOffset(0, y.roundToInt()) },
        tint = accent
    )
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
    Canvas(modifier = Modifier.size(44.dp)) {
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

// One-shot particle burst behind the success check.
@Composable
private fun ConfettiBurst(accent: Color) {
    val burst = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(120)
        burst.animateTo(
            1f,
            animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
        )
    }
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Canvas(modifier = Modifier.size(170.dp)) {
        val colors = listOf(accent, secondary, tertiary)
        val v = burst.value
        if (v <= 0f || v >= 1f) return@Canvas
        for (i in 0 until 12) {
            val angle = (i * 30f) * (3.14159f / 180f)
            val dist = v * 62.dp.toPx()
            drawCircle(
                color = colors[i % 3].copy(alpha = 1f - v),
                radius = (1f - v) * 5.dp.toPx() + 1.5f,
                center = Offset(
                    center.x + cos(angle) * dist,
                    center.y + sin(angle) * dist
                )
            )
        }
    }
}

// Download ring with a gradient sweep stroke and the % in the middle.
@Composable
private fun GradientProgressRing(progress: Float, version: String, accent: Color) {
    val animated by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "ringProgress"
    )
    val track = MaterialTheme.colorScheme.surface
    Box(
        modifier = Modifier.size(126.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(color = track.copy(alpha = 0.85f), style = stroke)
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        accent,
                        Color(0xFFF4B8AD),
                        accent
                    )
                ),
                startAngle = -90f,
                sweepAngle = 360f * animated.coerceIn(0f, 1f),
                useCenter = false,
                style = stroke
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${(animated * 100).toInt()}%",
                fontSize = 24.sp,
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
private fun ShakeBadge() {
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
    val pulse = rememberInfiniteTransition(label = "errorPulse")
    val ringAlpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "errorPulseAlpha"
    )
    Box(
        modifier = Modifier
            .size(88.dp)
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.error.copy(alpha = ringAlpha + 0.35f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.error
        )
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
private fun GradientProgressBar(progress: Float) {
    val animated by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "barProgress"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated.coerceIn(0f, 1f))
                .height(7.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary,
                            Color(0xFFF4B8AD)
                        )
                    )
                )
        )
    }
}

@Composable
private fun StateTitle(text: String) {
    Text(
        text = text,
        fontSize = 21.sp,
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

// Primary action: gradient fill, a shimmer sweep that keeps moving,
// and a physical press-in scale.
@Composable
private fun GradientButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "gradientPress"
    )
    val shimmer = rememberInfiniteTransition(label = "btnShimmer")
    val sweep by shimmer.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "btnSweep"
    )
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(primary, tertiary, primary),
                    start = Offset(sweep * 700f - 350f, 0f),
                    end = Offset(sweep * 700f + 150f, 120f)
                )
            )
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
