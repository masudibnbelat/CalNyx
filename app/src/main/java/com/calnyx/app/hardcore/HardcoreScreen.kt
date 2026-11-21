// FILE: app/src/main/java/com/calnyx/app/ui/screens/HardcoreScreen.kt
// ORIENTATION 3-IN-A-ROW (2026-11-20) - REPLACE. Up / Down / Auto
// are now 3 equal cards in ONE row (the full-width Auto row card
// is gone - it looked unbalanced, with the Check dangling on the
// right). All three use the same OrientationModeCard, so the
// selected one is just the filled card - no Check icon anywhere.
// Nothing else changed: Power, Ball, colours, % sizes, logic.
// HARDCORE (HARDWARE) - SPLIT VERSION (2026-11-20).
//  Split out of MoanControlScreen.kt (2026-11-20): Hardcore
//  (Hardware) here, Moan (Sound) in MoanScreen.kt, and Network
//  (Network Switcher + Open in WhatsApp) in NetworkScreen.kt.
//  DELETE MoanControlScreen.kt from the project, or the
//  HardcoreScreen / MoanScreen declarations will exist twice
//  and the build will fail.
//  In here: Power Menu, Lock Screen, Screen Orientation,
//  Floating Ball. Network Switcher + WhatsApp moved to Network.
// THEME VARIABLES (2026-11-20): every colour comes from the
//  theme (Color.kt) through MaterialTheme.colorScheme - see the
//  palette lines in HardcoreScreen below. Status-bar fix, drawer
//  swipe, orientation guard and ball logic are unchanged.
//  (V6 history: status bar matches the page bg, title on the
//  same line as the top-bar button, compact power cards.)

package com.calnyx.app.hardcore

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calnyx.app.applock.AppLockManager
import com.calnyx.app.applock.AppLockService
import com.calnyx.app.ball.FloatingBallService
import com.calnyx.app.hardcore.OrientationGuard
import com.calnyx.app.hardcore.OrientationService
import com.calnyx.app.moan.MoanControlPrefs
import com.calnyx.app.moan.MoanScreen
import com.calnyx.app.network.NetworkScreen
import com.calnyx.app.navigation.Screen

@Composable
fun HardcoreScreen(
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
            ControlTab(
                darkTheme = darkTheme,
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
private fun ControlTab(
    darkTheme: Boolean,
    cardBg: Color,
    cardBorder: Color,
    onPageColor: Color,
    thumbBg: Color,
    minDim: Dp,
    onNote: (String?) -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val scroll = rememberScrollState()
    var ballOn by remember { mutableStateOf(MoanControlPrefs.isBallEnabled(context)) }
    var ballOpacity by remember { mutableStateOf(MoanControlPrefs.getBallOpacity(context)) }
    var ballSize by remember { mutableStateOf(MoanControlPrefs.getBallSizeDp(context)) }
    // The saved mode is the truth: OrientationService enforces
    // it everywhere (overlay + system setting), even if the
    // system setting alone differs. So the selected button is
    // correct after the app was closed and reopened.
    var orientation by remember { mutableStateOf(MoanControlPrefs.getOrientationMode(context)) }
    // One call does everything: saves the mode, writes the system
    // setting (if WRITE_SETTINGS is granted), and starts / stops
    // the overlay service that enforces it outside the app. No
    // permission wait any more - the overlay enforces the mode
    // even without WRITE_SETTINGS, as long as overlay permission
    // is granted. Shared by all 3 orientation cards below.
    fun applyOrientation(mode: String) {
        orientation = mode
        if (OrientationService.apply(context, mode)) {
            onNote(null)
        } else {
            onNote("Orientation saved but not enforced - allow overlay permission (Floating Ball) or Modify system settings for CalNyx.")
        }
    }

    val accessibilityOn = AppLockManager.isAccessibilityEnabled(context)
    // Ball was requested but overlay permission was missing.
    var ballPending by remember { mutableStateOf(false) }
    // When the user comes back from the overlay Settings page,
    // start the ball automatically if permission is now granted.
    // Also restarts it if the system / OEM killed it while ON.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Every time this screen is shown, make sure the
                // orientation guard is registered and the saved
                // mode is applied. This is the re-registration
                // point if the process was restarted while the
                // user was outside the app.
                try {
                    OrientationGuard.refresh(context)
                } catch (_: Exception) {
                }
                if (ballPending && FloatingBallService.canDrawOverlays(context)) {
                    ballPending = false
                    ballOn = true
                    MoanControlPrefs.setBallEnabled(context, true)
                    FloatingBallService.start(context)
                    onNote(null)
                } else if (ballPending) {
                    ballPending = false
                    ballOn = false
                    MoanControlPrefs.setBallEnabled(context, false)
                    onNote("Overlay permission is still off - ball cannot show.")
                } else if (ballOn && FloatingBallService.canDrawOverlays(context)) {
                    FloatingBallService.start(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(minDim * 0.078f), tint = onPageColor)
            }
            Spacer(modifier = Modifier.width(minDim * 0.033f))
            Text("Hardcore", fontWeight = FontWeight.Bold, fontSize = (minDim * 0.078f).value.sp, color = onPageColor)
        }
        // POWER
        Text("Power", fontSize = (minDim * 0.033f).value.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurfaceVariant)
        // POWER - two square cards now: Power Menu and Lock
        // Screen (Network Switcher moved to the Network screen).
        // IntrinsicSize.Max + fillMaxHeight keep both cards the
        // same height.
        Row(horizontalArrangement = Arrangement.spacedBy(minDim * 0.022f), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
            PowerCard(
                title = "Power Menu",
                icon = Icons.Default.PowerSettingsNew,
                cardBg = cardBg,
                cardBorder = cardBorder,
                onPageColor = onPageColor,
                thumbBg = thumbBg,
                minDim = minDim,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = {
                    if (!accessibilityOn) {
                        onNote("Turn on Accessibility for CalNyx first (App Lock > Open Accessibility Settings).")
                    } else if (AppLockService.requestPowerDialog()) {
                        onNote(null)
                    } else {
                        onNote("Power Menu did not open on this phone.")
                    }
                }
            )
            PowerCard(
                title = "Lock Screen",
                icon = Icons.Default.Lock,
                cardBg = cardBg,
                cardBorder = cardBorder,
                onPageColor = onPageColor,
                thumbBg = thumbBg,
                minDim = minDim,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = {
                    if (!accessibilityOn) {
                        onNote("Turn on Accessibility for CalNyx first (App Lock > Open Accessibility Settings).")
                    } else if (AppLockService.requestLockScreen()) {
                        onNote(null)
                    } else {
                        onNote("Lock Screen did not work on this phone.")
                    }
                }
            )
        }
        // Screen Orientation - 3-IN-A-ROW (2026-11-20): Up / Down /
        // Auto as 3 equal cards in one row. The old full-width Auto
        // row card is gone - it looked unbalanced. Selected card =
        // filled (inverted text), others = card bg. Sob size
        // minDim-er % - jekono screen-e ar rotate korleo design
        // vangbe na.
        Text("Screen Orientation", fontSize = (minDim * 0.033f).value.sp, fontWeight = FontWeight.SemiBold, color = colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(minDim * 0.022f), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
            OrientationModeCard(
                label = "Up 0°",
                icon = Icons.Default.ArrowUpward,
                selected = orientation == "up",
                cardBg = cardBg,
                cardBorder = cardBorder,
                onPageColor = onPageColor,
                selectedContent = colorScheme.background,
                thumbBg = thumbBg,
                minDim = minDim,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { applyOrientation("up") }
            )
            OrientationModeCard(
                label = "Down 180°",
                icon = Icons.Default.ArrowDownward,
                selected = orientation == "down",
                cardBg = cardBg,
                cardBorder = cardBorder,
                onPageColor = onPageColor,
                selectedContent = colorScheme.background,
                thumbBg = thumbBg,
                minDim = minDim,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { applyOrientation("down") }
            )
            OrientationModeCard(
                label = "Auto",
                icon = Icons.Default.Autorenew,
                selected = orientation == "auto",
                cardBg = cardBg,
                cardBorder = cardBorder,
                onPageColor = onPageColor,
                selectedContent = colorScheme.background,
                thumbBg = thumbBg,
                minDim = minDim,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { applyOrientation("auto") }
            )
        }
        // FLOATING BALL - Material 3 Card now (was a hand-made
        // background + border Column). Tight padding, tight spacing
        // and shorter sliders, so the card takes less height.
        // Switch + sliders are already Material 3, only their
        // colours are themed.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(minDim * 0.044f),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, cardBorder)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = minDim * 0.033f, vertical = minDim * 0.022f),
                verticalArrangement = Arrangement.spacedBy(minDim * 0.011f)
            ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Floating Ball", fontWeight = FontWeight.SemiBold, fontSize = (minDim * 0.044f).value.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = ballOn,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colorScheme.background,
                        checkedTrackColor = colorScheme.onBackground,
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = colorScheme.onBackground,
                        uncheckedTrackColor = colorScheme.outline,
                        uncheckedBorderColor = Color.Transparent
                    ),
                    onCheckedChange = { checked ->
                        if (checked && !FloatingBallService.canDrawOverlays(context)) {
                            // No overlay permission yet: keep the toggle
                            // ON, remember the request, open Settings.
                            // ON_RESUME above starts the ball when the
                            // user comes back with permission granted.
                            ballPending = true
                            ballOn = true
                            MoanControlPrefs.setBallEnabled(context, true)
                            onNote("Allow Display over other apps, then come back - the ball will start.")
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                        } else {
                            ballPending = false
                            ballOn = checked
                            MoanControlPrefs.setBallEnabled(context, checked)
                            if (checked) FloatingBallService.start(context) else FloatingBallService.stop(context)
                            onNote(null)
                        }
                    }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Opacity", modifier = Modifier.weight(1f), fontSize = (minDim * 0.042f).value.sp)
                Text("$ballOpacity%", fontWeight = FontWeight.SemiBold, fontSize = (minDim * 0.042f).value.sp)
            }
            Slider(
                value = ballOpacity.toFloat(),
                onValueChange = { ballOpacity = it.toInt() },
                onValueChangeFinished = {
                    MoanControlPrefs.setBallOpacity(context, ballOpacity)
                    if (ballOn) {
                        // In-place update only - the old stop() +
                        // start() restart crashed the app.
                        FloatingBallService.update(context)
                    }
                },
                valueRange = 30f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = colorScheme.onBackground,
                    activeTrackColor = colorScheme.onBackground,
                    inactiveTrackColor = colorScheme.outline
                ),
                modifier = Modifier.fillMaxWidth().height(minDim * 0.067f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ball Size", modifier = Modifier.weight(1f), fontSize = (minDim * 0.042f).value.sp)
                Text("$ballSize dp", fontWeight = FontWeight.SemiBold, fontSize = (minDim * 0.042f).value.sp)
            }
            Slider(
                value = ballSize.toFloat(),
                onValueChange = { ballSize = it.toInt() },
                onValueChangeFinished = {
                    MoanControlPrefs.setBallSizeDp(context, ballSize)
                    if (ballOn) {
                        // In-place update only - the old stop() +
                        // start() restart crashed the app.
                        FloatingBallService.update(context)
                    }
                },
                valueRange = 32f..64f,
                colors = SliderDefaults.colors(
                    thumbColor = colorScheme.onBackground,
                    activeTrackColor = colorScheme.onBackground,
                    inactiveTrackColor = colorScheme.outline
                ),
                modifier = Modifier.fillMaxWidth().height(minDim * 0.067f)
            )
            Text(
                "Tap the ball to open the panel. Drag it to move.",
                fontSize = (minDim * 0.036f).value.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            }
        }
        Spacer(modifier = Modifier.height(minDim * 0.022f))
    }
}

@Composable
private fun PowerCard(
    title: String,
    icon: ImageVector,
    cardBg: Color,
    cardBorder: Color,
    onPageColor: Color,
    thumbBg: Color,
    minDim: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Material 3 Card now (was a hand-made background + border +
    // clickable Column): the card itself handles the tap, with the
    // proper Material ripple. BIG (2026-11-20): icon circle +
    // icon + title sob boro kora - sob size minDim-er % -e.
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(minDim * 0.044f),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(vertical = minDim * 0.039f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(minDim * 0.028f, Alignment.CenterVertically)
        ) {
            Box(
                modifier = Modifier.size(minDim * 0.167f).clip(CircleShape)
                    .background(thumbBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, modifier = Modifier.size(minDim * 0.083f))
            }
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = (minDim * 0.047f).value.sp, color = onPageColor, textAlign = TextAlign.Center)
        }
    }
}

// One big orientation mode card (Up / Down): icon circle on top,
// label below - same language as PowerCard. Selected = filled
// (onPageColor bg, background text), unselected = card bg.
@Composable
private fun OrientationModeCard(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    cardBg: Color,
    cardBorder: Color,
    onPageColor: Color,
    selectedContent: Color,
    thumbBg: Color,
    minDim: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(minDim * 0.044f),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) onPageColor else cardBg
        ),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(vertical = minDim * 0.033f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(minDim * 0.022f, Alignment.CenterVertically)
        ) {
            Box(
                modifier = Modifier.size(minDim * 0.133f).clip(CircleShape)
                    .background(thumbBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(minDim * 0.067f), tint = onPageColor)
            }
            Text(
                label,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = (minDim * 0.047f).value.sp,
                color = if (selected) selectedContent else onPageColor,
                textAlign = TextAlign.Center
            )
        }
    }
}
