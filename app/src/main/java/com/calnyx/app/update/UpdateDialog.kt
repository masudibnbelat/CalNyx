package com.calnyx.app.update

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private sealed interface UpdateUiState {
    object Checking : UpdateUiState
    object UpToDate : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateUiState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}

// The "App update" modal, v2: a centred hero layout where every
// state has its own animated visual — sonar rings while checking,
// a popping badge for news, a big progress ring while downloading,
// a check pop when ready, a shaken error badge on failure. States
// cross-fade/scale through AnimatedContent (keyed by state CLASS,
// so download progress ticks don't retrigger the transition).
@OptIn(ExperimentalMaterial3Api::class)
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
            )
        }
    ) {
        AnimatedContent(
            targetState = state,
            contentKey = { it.javaClass },
            transitionSpec = {
                (fadeIn(tween(280)) + scaleIn(tween(280), initialScale = 0.94f)) togetherWith
                        (fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.97f))
            },
            label = "updateState",
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) { s ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (s) {
                    UpdateUiState.Checking -> {
                        SonarIcon()
                        Spacer(modifier = Modifier.height(18.dp))
                        StateTitle("Checking for updates")
                        StateSubtitle("Looking on GitHub for a newer CalNyx…")
                    }

                    UpdateUiState.UpToDate -> {
                        PopBadge(
                            background = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        StateTitle("You're up to date")
                        StateSubtitle("CalNyx v$current is the latest version")
                        Spacer(modifier = Modifier.height(22.dp))
                        SecondaryButton("Close", onDismiss)
                    }

                    is UpdateUiState.Available -> {
                        PopBadge(
                            background = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                Icons.Default.SystemUpdate,
                                contentDescription = null,
                                modifier = Modifier.size(38.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        StateTitle("Update available")
                        Spacer(modifier = Modifier.height(10.dp))
                        VersionChips(current = current, new = s.info.version)
                        if (s.info.notes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(12.dp)
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
                        Spacer(modifier = Modifier.height(22.dp))
                        PrimaryButton("Download update") {
                            state = UpdateUiState.Downloading(s.info, 0f)
                            scope.launch {
                                try {
                                    val file = AppUpdateManager.downloadApk(
                                        context,
                                        s.info
                                    ) { progress ->
                                        state = UpdateUiState.Downloading(s.info, progress)
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
                            Text("Later")
                        }
                    }

                    is UpdateUiState.Downloading -> {
                        val animatedProgress by animateFloatAsState(
                            targetValue = s.progress,
                            animationSpec = tween(220),
                            label = "downloadRing"
                        )
                        Box(
                            modifier = Modifier.size(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = animatedProgress,
                                modifier = Modifier.size(120.dp),
                                strokeWidth = 7.dp,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${(animatedProgress * 100).toInt()}%",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "of v${s.info.version}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        StateTitle("Downloading update")
                        StateSubtitle("Keep the app open until it finishes…")
                    }

                    is UpdateUiState.Ready -> {
                        PopBadge(
                            background = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        StateTitle("Ready to install")
                        StateSubtitle("CalNyx v${s.info.version} has been downloaded")
                        Spacer(modifier = Modifier.height(22.dp))
                        PrimaryButton("Install") {
                            AppUpdateManager.installApk(context, s.file)
                        }
                        Text(
                            text = "If Android asks, allow \"Install unknown apps\" for CalNyx, then tap Install again.",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        TextButton(onClick = onDismiss) {
                            Text("Later")
                        }
                    }

                    is UpdateUiState.Failed -> {
                        ShakeBadge()
                        Spacer(modifier = Modifier.height(18.dp))
                        StateTitle("Couldn't check for updates")
                        StateSubtitle(s.message)
                        Spacer(modifier = Modifier.height(22.dp))
                        PrimaryButton("Retry") { check() }
                        TextButton(onClick = onDismiss) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

// Checking visual: the update icon sits still while two soft rings
// expand and fade around it, like a sonar sweep.
@Composable
private fun SonarIcon() {
    val transition = rememberInfiniteTransition(label = "sonar")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sonarPhase"
    )
    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        listOf(phase, (phase + 0.5f) % 1f).forEach { p ->
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .graphicsLayer {
                        scaleX = 1f + p * 0.85f
                        scaleY = 1f + p * 0.85f
                        alpha = 0.30f * (1f - p)
                    }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.SystemUpdate,
                contentDescription = null,
                modifier = Modifier.size(38.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// A round icon badge that springs in with a bounce when its state
// appears.
@Composable
private fun PopBadge(
    background: Color,
    icon: @Composable () -> Unit
) {
    val scale = remember { Animatable(0f) }
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
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
    }
}

// Failure visual: the error badge gives a short side-to-side shake
// when it appears.
@Composable
private fun ShakeBadge() {
    val offsetX = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        offsetX.animateTo(
            0f,
            animationSpec = keyframes {
                durationMillis = 420
                0f at 0
                (-12f) at 60
                12f at 140
                (-7f) at 220
                7f at 300
                0f at 420
            }
        )
    }
    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(38.dp),
                tint = MaterialTheme.colorScheme.error
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
        textAlign = TextAlign.Center
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

// v1.0.0  ->  v1.1.0, as two pills with an arrow between them.
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

// Big primary action: 52dp tall, soft corners, and it physically
// presses in under the finger.
@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(120),
        label = "primaryPress"
    )
    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
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
            fontWeight = FontWeight.Medium
        )
    }
}
