// HARDWARE + SOUND VERSION (2026-11-06) - REPLACE MoanControlScreen.kt.
//  1. RENAMED: drawer + page titles only. "Hardcore" ->
//     "Hardware", "Moan" -> "Sound". Function names,
//     routes, prefs and all behaviour are unchanged.
//  2. DRAWER SWIPE (universal rule): leftward drag anywhere
//     on Hardware / Sound opens the app drawer. The volume
//     sliders keep working: a drag that starts ON a slider
//     moves the slider, it does not open the drawer.
// FILE: app/src/main/java/com/calnyx/app/ui/screens/MoanControlScreen.kt
// V6 - based on V5. The user tested V5 (screenshots 2026-11-06
// 12:13-12:16) and asked for:
// (1) STATUS BAR matches the page background (it was showing the
//     system default — white in light mode — instead of the page
//     colour), in both themes. Same fix as Blue Film V8 / Moan V2.
// (2) The Moan / Hardcore title + icon sit on the SAME line as the
//     NavGraph's top-bar button (3-dot). The tab Columns had 12dp
//     top padding, which pushed the header 16dp below the button.
//     Top padding removed (sides + bottom stay).
// (3) Power cards: less gap between the three cards, and a smaller
//     font / icon so they look neater.
// (4) The WhatsApp Send button is smaller (it was 64dp and looked
//     too big); the input field shrinks with it so they stay level.
// V5 kept underneath: Network Switcher is the third square card in
// the Power row; the WhatsApp section has a title + description and
// a slanted Send icon on the right.
// MOAN + HARDCORE - NO TABS. Two separate screens in this one file:
// - HardcoreScreen: Power + Network Switcher + Floating Ball.
// - MoanScreen: Ringer Profile + all audio streams, real-time.
// NavGraph lists them as two separate drawer sections: Hardcore
// and Moan. History is not in the drawer any more (route still
// exists for the Normal calculator, only the drawer entry is gone).
// Design: off-white / off-black only, English only, title only.
// Android 9..17: all APIs used here exist since API 26 or earlier.
// - Power Menu / Lock Screen: Accessibility GLOBAL_ACTION_POWER_DIALOG
//   and GLOBAL_ACTION_LOCK_SCREEN via AppLockService. Needs
//   Accessibility on. Without it the buttons show a prompt.
// - Screen Orientation: enforced by OrientationService
//   (moan/OrientationService.kt), two ways at once. (1) It
//   writes the system setting ACCELEROMETER_ROTATION /
//   USER_ROTATION when WRITE_SETTINGS is granted. (2) It runs
//   a foreground service with a 0x0 invisible overlay whose
//   LayoutParams.screenOrientation forces the WHOLE display
//   to that orientation - this is what makes it hold outside
//   the app, on Home, and on the lock screen, until Auto is
//   chosen again. The overlay needs SYSTEM_ALERT_WINDOW; the
//   system-setting write needs WRITE_SETTINGS. Either one
//   alone enforces the mode, so the mode works even if only
//   one permission is granted. The saved mode (MoanControlPrefs)
//   is the truth for the selected button, and a BootReceiver
//   restores a locked mode after reboot. In Auto the service
//   stops itself and removes the overlay.
//   NOTE: the home screen / launcher on realme is portrait-
//   locked by the launcher itself - it will not rotate even
//   with 180 deg set. Other apps (WhatsApp, Chrome, ...) DO
//   follow the setting. That is the launcher's own choice,
//   not CalNyx.
// - Network Switcher: opens the system Phone info (Testing)
//   screen directly, no dialer, no call. Tries RadioInfo and
//   TestingSettings in BOTH com.android.settings and
//   com.android.phone - the com.android.phone package is the
//   part the reference app had that CalNyx was missing; on
//   some phones (including this realme) the screen lives
//   there. Then OEM inner-class names, then the secret-code
//   broadcast (the same one the dialer sends for
//   *#*#4636#*#*), then the dialer with the code (tap Call
//   once), then Mobile Network Settings, then Settings.
//   ACTION_CALL is NOT used: on this realme it places a REAL
//   call to *#*#4636#*#* instead of opening Phone info, so
//   it was removed.
//   CalNyx does NOT read IMEI / IMSI / phone number: Android
//   10+ blocks those for normal apps.
// - Orientation guard (OrientationGuard.kt): the saved mode is
//   re-applied by a ContentObserver whenever the system setting
//   changes, because on this realme the setting is reset ~2s
//   after leaving the app. The guard only runs for a locked
//   mode (up / down); in Auto it stops. It lives in the
//   services that are already running (AppLockService /
//   FloatingBallService), and it is re-registered every time
//   this screen resumes. The mode is saved to prefs BEFORE
//   the system setting is written, or the guard would put
//   the old mode back.
// - Sound: AudioManager STREAM_MUSIC / RING / ALARM /
//   VOICE_CALL, setStreamVolume on every slider move (real-time).
//   (The NOTIFICATION stream card was removed at the user's
//   request - it is no longer in the streams list below.)
//   Voice Call volume only takes effect during a call - that is an
//   Android limit, not a bug. Silent needs Notification Policy
//   Access on some phones (DND) - the button opens that setting.
// - WhatsApp (Open in WhatsApp / Click to Chat): a bigger card
//   below Screen Orientation, with a title and a short
//   description, then a taller digits-only input. The round
//   button on the right is a slanted Send icon (paper plane),
//   not the WhatsApp logo. Typing or pasting a number and
//   tapping Send opens that number's WhatsApp chat via
//   https://wa.me/<number> (WhatsAppHelper normalizes it:
//   01XXXXXXXXX -> 8801XXXXXXXXX). The small WhatsApp logo is
//   still the leading icon inside the input field:
//   res/drawable/ic_whatsapp.xml (the real WhatsApp bubble),
//   tinted with the theme colour - black / white only, no
//   WhatsApp green, per the app's palette rule.
//   WhatsAppOpenerActivity (manifest) also picks up a number
//   tapped in ANY other app (tel: link) and opens its
//   WhatsApp chat the same way.
// - Floating Ball: overlay service, needs SYSTEM_ALERT_WINDOW once.
//   BALL FIX: if the user turns the ball on without overlay
//   permission, the toggle stays ON and opens Settings. When they
//   come back (ON_RESUME) and permission is now granted, the ball
//   starts automatically - no second tap needed. If permission is
//   still off, the toggle goes back OFF with a note. The service
//   is also restarted on resume if it was killed (OEM / reboot)
//   while the toggle is ON. Manifest MUST be the MOAN version:
//   without SYSTEM_ALERT_WINDOW + the service declaration in
//   AndroidManifest.xml, canDrawOverlays is always false and the
//   ball can never show.
package com.calnyx.app.ui.screens

import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calnyx.app.R
import com.calnyx.app.applock.AppLockManager
import com.calnyx.app.applock.AppLockService
import com.calnyx.app.moan.FloatingBallService
import com.calnyx.app.moan.OrientationGuard
import com.calnyx.app.moan.OrientationService
import com.calnyx.app.moan.WhatsAppHelper
import com.calnyx.app.moan.MoanControlPrefs

private data class StreamRow(
    val stream: Int,
    val title: String,
    val icon: ImageVector
)

private fun tryStart(context: Context, intent: Intent): Boolean {
    return try {
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}

// Open the system Phone info (Testing) screen.
// Returns: 0 = Phone info opened, 1 = secret-code broadcast
// sent (Phone info should open), 2 = dialer opened with the
// *#*#4636#*#* code (user taps Call, Phone info opens),
// 3 = Mobile Network Settings (Testing blocked on this phone),
// 4 = Settings, -1 = nothing worked.
// The target list is the reference app's method: RadioInfo
// then TestingSettings, in com.android.settings first and
// then com.android.phone. The com.android.phone package is
// the part CalNyx was missing - on some phones (including
// this realme) the screen lives in com.android.phone, not
// com.android.settings, so the settings-only attempts all
// failed. After the four targets come the OEM inner-class
// names, then the secret-code broadcast (the SAME broadcast
// the dialer sends when you type *#*#4636#*#* by hand),
// then the dialer with the code, then Mobile Network
// Settings, then Settings.
// ACTION_CALL is DELIBERATELY NOT used: on this realme it
// does NOT open Phone info - it places a REAL call to
// *#*#4636#*#* (SIM picker, then "Calling..."). The MMI
// code is only intercepted when typed in the dialer by hand,
// not when dialed via an intent.
private fun openPhoneInfo(context: Context): Int {
    // 1-4. RadioInfo / TestingSettings, in both packages.
    // No action, just the component + NEW_TASK - the same
    // shape the reference app uses.
    val targets = listOf(
        ComponentName("com.android.settings", "com.android.settings.RadioInfo"),
        ComponentName("com.android.phone", "com.android.phone.settings.RadioInfo"),
        ComponentName("com.android.settings", "com.android.settings.TestingSettings"),
        ComponentName("com.android.phone", "com.android.phone.settings.TestingSettings")
    )
    for (target in targets) {
        try {
            val intent = Intent().apply {
                component = target
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return 0
        } catch (_: Exception) {
        }
    }
    // 5-6. OEM inner-class names. Some manufacturers host the
    // Testing / RadioInfo screen as a fragment inside the
    // Settings activity (Settings$...Activity). These class
    // names do not exist on stock Android, so they fail
    // silently there and fall through - no harm.
    val innerTargets = listOf(
        ComponentName("com.android.settings", "com.android.settings.Settings\$TestingSettingsActivity"),
        ComponentName("com.android.settings", "com.android.settings.Settings\$RadioInfoActivity")
    )
    for (target in innerTargets) {
        try {
            val intent = Intent().apply {
                component = target
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return 0
        } catch (_: Exception) {
        }
    }
    // 7. Secret-code broadcast - direct, no dialer, no call.
    try {
        val secret = Intent("android.provider.Telephony.SECRET_CODE").apply {
            data = Uri.parse("android_secret_code://4636")
        }
        context.sendBroadcast(secret)
        return 1
    } catch (_: Exception) {
        // Blocked on this phone - fall through to the dialer.
    }
    // 8. Dialer code *#*#4636#*#* - opens the dialer with the
    // code filled in; tap Call and Phone info opens.
    val dial = Intent(
        Intent.ACTION_DIAL,
        Uri.parse("tel:*%23*%234636%23*%23*")
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (tryStart(context, dial)) return 2
    // 9. Mobile Network Settings (Testing blocked).
    val net = Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (tryStart(context, net)) return 3
    // 10. Settings.
    val settings = Intent(Settings.ACTION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (tryStart(context, settings)) return 4
    return -1
}

// Runs openPhoneInfo and tells the user what happened.
private fun openNetworkSwitcher(
    context: Context,
    onNote: (String?) -> Unit
) {
    when (openPhoneInfo(context)) {
        0 -> onNote(null)
        1 -> onNote(null)
        2 -> onNote("Dialer opened with *#*#4636#*#* - tap Call and Phone info will open.")
        3 -> onNote("Phone info is blocked on this phone - Mobile Network Settings opened instead.")
        4 -> onNote("Phone info could not be opened - Settings opened instead.")
        else -> onNote("Phone info could not be opened on this phone.")
    }
}

@Composable
fun HardcoreScreen(
    darkTheme: Boolean,
    onOpenDrawer: () -> Unit = {}
) {
    val pageBg = if (darkTheme) Color(0xFF0E0E0E) else Color(0xFFF6F4EF)
    val cardBg = if (darkTheme) Color(0xFF1B1B1B) else Color(0xFFFFFFFF)
    val cardBorder = if (darkTheme) Color(0xFF2B2B2B) else Color(0xFFE9E6DF)
    val onPageColor = if (darkTheme) Color(0xFFF6F4EF) else Color(0xFF0E0E0E)
    val thumbBg = if (darkTheme) Color(0xFF262626) else Color(0xFFEFEDE8)
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
fun MoanScreen(
    darkTheme: Boolean,
    onOpenDrawer: () -> Unit = {}
) {
    val pageBg = if (darkTheme) Color(0xFF0E0E0E) else Color(0xFFF6F4EF)
    val cardBg = if (darkTheme) Color(0xFF1B1B1B) else Color(0xFFFFFFFF)
    val cardBorder = if (darkTheme) Color(0xFF2B2B2B) else Color(0xFFE9E6DF)
    val onPageColor = if (darkTheme) Color(0xFFF6F4EF) else Color(0xFF0E0E0E)
    val thumbBg = if (darkTheme) Color(0xFF262626) else Color(0xFFEFEDE8)
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
            SoundTab(
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
    val scroll = rememberScrollState()
    var ballOn by remember { mutableStateOf(MoanControlPrefs.isBallEnabled(context)) }
    var ballOpacity by remember { mutableStateOf(MoanControlPrefs.getBallOpacity(context)) }
    var ballSize by remember { mutableStateOf(MoanControlPrefs.getBallSizeDp(context)) }
    // The saved mode is the truth: OrientationService enforces
    // it everywhere (overlay + system setting), even if the
    // system setting alone differs. So the selected button is
    // correct after the app was closed and reopened.
    var orientation by remember { mutableStateOf(MoanControlPrefs.getOrientationMode(context)) }
    // WhatsApp number input (digits only).
    var waNumber by remember { mutableStateOf("") }

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
            Text("Hardware", fontWeight = FontWeight.Bold, fontSize = (minDim * 0.078f).value.sp, color = onPageColor)
        }
        // POWER
        Text("Power", fontSize = (minDim * 0.033f).value.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
        // POWER - three square cards now: Power Menu, Lock Screen
        // and Network Switcher (moved up from its own wide row
        // below). IntrinsicSize.Max + fillMaxHeight keep all
        // three cards the same height even though "Network
        // Switcher" wraps to two lines.
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
            PowerCard(
                title = "Network Switcher",
                icon = Icons.Default.SignalCellularAlt,
                cardBg = cardBg,
                cardBorder = cardBorder,
                onPageColor = onPageColor,
                thumbBg = thumbBg,
                minDim = minDim,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { openNetworkSwitcher(context, onNote) }
            )
        }
        // Screen Orientation - small buttons, compact.
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(minDim * 0.044f))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(minDim * 0.044f))
                .padding(minDim * 0.033f),
            verticalArrangement = Arrangement.spacedBy(minDim * 0.022f)
        ) {
            Text("Screen Orientation", fontWeight = FontWeight.SemiBold, fontSize = (minDim * 0.044f).value.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(minDim * 0.022f), modifier = Modifier.fillMaxWidth()) {
                listOf("up" to "Up 0°", "down" to "Down 180°", "auto" to "Auto").forEach { (mode, label) ->
                    val selected = orientation == mode
                    Box(
                        modifier = Modifier.weight(1f)
                            .clip(RoundedCornerShape(minDim * 0.033f))
                            .background(if (selected) onPageColor else thumbBg)
                            .clickable {
                                // One call does everything: saves the
                                // mode, writes the system setting (if
                                // WRITE_SETTINGS is granted), and
                                // starts / stops the overlay service
                                // that enforces it outside the app.
                                // No permission wait any more - the
                                // overlay enforces the mode even
                                // without WRITE_SETTINGS, as long as
                                // overlay permission is granted.
                                orientation = mode
                                if (OrientationService.apply(context, mode)) {
                                    onNote(null)
                                } else {
                                    onNote("Orientation saved but not enforced - allow overlay permission (Floating Ball) or Modify system settings for CalNyx.")
                                }
                            }
                            .padding(vertical = minDim * 0.028f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) if (darkTheme) Color(0xFF0E0E0E) else Color(0xFFF6F4EF) else onPageColor,
                            fontSize = (minDim * 0.039f).value.sp
                        )
                    }
                }
            }
        }
        // WHATSAPP - Open in WhatsApp (Click to Chat). Bigger
        // now: title + short description on top, then a taller
        // input row. The round button on the right is a slanted
        // Send icon (paper plane) instead of the WhatsApp logo.
        // The small WhatsApp logo stays as the leading icon
        // inside the input field. Black / white only.
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(minDim * 0.044f))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(minDim * 0.044f))
                .padding(minDim * 0.033f),
            verticalArrangement = Arrangement.spacedBy(minDim * 0.022f)
        ) {
            Text("Open in WhatsApp", fontWeight = FontWeight.SemiBold, fontSize = (minDim * 0.044f).value.sp)
            Text(
                "Type a number to open its WhatsApp chat directly.",
                fontSize = (minDim * 0.036f).value.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(minDim * 0.022f),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = waNumber,
                    onValueChange = { waNumber = it.filter { ch -> ch.isDigit() } },
                    placeholder = { Text("01XXXXXXXXX") },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_whatsapp),
                            contentDescription = null,
                            modifier = Modifier.size(minDim * 0.061f)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f).height(minDim * 0.156f),
                    shape = RoundedCornerShape(minDim * 0.033f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = onPageColor,
                        unfocusedBorderColor = cardBorder,
                        cursorColor = onPageColor
                    )
                )
                Box(
                    modifier = Modifier.size(minDim * 0.156f).clip(CircleShape).background(onPageColor)
                        .clickable {
                            if (WhatsAppHelper.openChat(context, waNumber)) {
                                onNote(null)
                            } else {
                                onNote("Type a valid number first.")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Open in WhatsApp",
                        modifier = Modifier.size(minDim * 0.072f),
                        tint = cardBg
                    )
                }
            }
        }
        // FLOATING BALL - compact. Tight padding, tight spacing
        // and shorter sliders, so the card takes less height.
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(minDim * 0.044f))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(minDim * 0.044f))
                .padding(horizontal = minDim * 0.033f, vertical = minDim * 0.022f),
            verticalArrangement = Arrangement.spacedBy(minDim * 0.011f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Floating Ball", fontWeight = FontWeight.SemiBold, fontSize = (minDim * 0.044f).value.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = ballOn,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = if (darkTheme) Color(0xFF0E0E0E) else Color(0xFFF6F4EF),
                        checkedTrackColor = if (darkTheme) Color(0xFFF6F4EF) else Color(0xFF0E0E0E),
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = if (darkTheme) Color(0xFFF6F4EF) else Color(0xFF0E0E0E),
                        uncheckedTrackColor = if (darkTheme) Color(0xFF3A3A3A) else Color(0xFFD8D5CE),
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
                    thumbColor = if (darkTheme) Color(0xFFF6F4EF) else Color(0xFF0E0E0E),
                    activeTrackColor = if (darkTheme) Color(0xFFF6F4EF) else Color(0xFF0E0E0E),
                    inactiveTrackColor = if (darkTheme) Color(0xFF3A3A3A) else Color(0xFFD8D5CE)
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
                    thumbColor = if (darkTheme) Color(0xFFF6F4EF) else Color(0xFF0E0E0E),
                    activeTrackColor = if (darkTheme) Color(0xFFF6F4EF) else Color(0xFF0E0E0E),
                    inactiveTrackColor = if (darkTheme) Color(0xFF3A3A3A) else Color(0xFFD8D5CE)
                ),
                modifier = Modifier.fillMaxWidth().height(minDim * 0.067f)
            )
            Text(
                "Tap the ball to open the panel. Drag it to move.",
                fontSize = (minDim * 0.036f).value.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(minDim * 0.044f))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(minDim * 0.044f))
            .clickable(onClick = onClick)
            .padding(vertical = minDim * 0.028f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(minDim * 0.022f, Alignment.CenterVertically)
    ) {
        Box(
            modifier = Modifier.size(minDim * 0.122f).clip(CircleShape)
                .background(thumbBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, modifier = Modifier.size(minDim * 0.061f))
        }
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = (minDim * 0.036f).value.sp, color = onPageColor, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SoundTab(
    cardBg: Color,
    cardBorder: Color,
    onPageColor: Color,
    thumbBg: Color,
    minDim: Dp,
    onNote: (String?) -> Unit
) {
    val context = LocalContext.current
    val audio = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val streams = remember {
        listOf(
            StreamRow(AudioManager.STREAM_MUSIC, "Media", Icons.Default.MusicNote),
            StreamRow(AudioManager.STREAM_RING, "Ringtone", Icons.Default.VolumeUp),
            StreamRow(AudioManager.STREAM_ALARM, "Alarm", Icons.Default.Alarm),
            StreamRow(AudioManager.STREAM_VOICE_CALL, "Call", Icons.Default.Call)
        )
    }
    var volumes by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var ringerMode by remember { mutableStateOf(audio.ringerMode) }
    val scroll = rememberScrollState()

    LaunchedEffect(Unit) {
        val map = mutableMapOf<Int, Int>()
        streams.forEach { row ->
            try {
                map[row.stream] = audio.getStreamVolume(row.stream)
            } catch (_: Exception) {
            }
        }
        volumes = map
        try {
            ringerMode = audio.ringerMode
        } catch (_: Exception) {
        }
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
                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(minDim * 0.078f), tint = onPageColor)
            }
            Spacer(modifier = Modifier.width(minDim * 0.033f))
            Text("Sound", fontWeight = FontWeight.Bold, fontSize = (minDim * 0.078f).value.sp, color = onPageColor)
        }
        Text("Ringer Profile", fontSize = (minDim * 0.033f).value.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
        Row(horizontalArrangement = Arrangement.spacedBy(minDim * 0.022f), modifier = Modifier.fillMaxWidth()) {
            listOf(
                AudioManager.RINGER_MODE_NORMAL to "Sound" to Icons.Default.VolumeUp,
                AudioManager.RINGER_MODE_VIBRATE to "Vibrate" to Icons.Default.Vibration,
                AudioManager.RINGER_MODE_SILENT to "Silent" to Icons.Default.VolumeOff
            ).forEach { (pair, icon) ->
                val (mode, label) = pair
                val selected = ringerMode == mode
                Box(
                    modifier = Modifier.weight(1f)
                        .clip(RoundedCornerShape(minDim * 0.033f))
                        .background(if (selected) onPageColor else thumbBg)
                        .clickable {
                            try {
                                if (mode == AudioManager.RINGER_MODE_SILENT) {
                                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                                        !nm.isNotificationPolicyAccessGranted
                                    ) {
                                        onNote("Allow Do Not Disturb access for CalNyx first to use Silent.")
                                        try {
                                            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                        }
                                        return@clickable
                                    }
                                }
                                audio.ringerMode = mode
                                ringerMode = mode
                                onNote(null)
                            } catch (_: Exception) {
                                onNote("Ringer mode could not be changed on this phone.")
                            }
                        }
                        .padding(vertical = minDim * 0.028f),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(minDim * 0.011f)) {
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(minDim * 0.056f),
                            tint = if (selected) if (onPageColor == Color(0xFFF6F4EF)) Color(0xFF0E0E0E) else Color(0xFFF6F4EF) else onPageColor
                        )
                        Text(
                            label,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) if (onPageColor == Color(0xFFF6F4EF)) Color(0xFF0E0E0E) else Color(0xFFF6F4EF) else onPageColor,
                            fontSize = (minDim * 0.039f).value.sp
                        )
                    }
                }
            }
        }
        Text("Audio Streams", fontSize = (minDim * 0.033f).value.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
        streams.forEach { row ->
            val max = try {
                audio.getStreamMaxVolume(row.stream)
            } catch (_: Exception) {
                15
            }.coerceAtLeast(1)
            val current = volumes[row.stream] ?: 0
            val percent = if (max > 0) (current * 100 / max) else 0
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(minDim * 0.044f))
                    .background(cardBg)
                    .border(1.dp, cardBorder, RoundedCornerShape(minDim * 0.044f))
                    .padding(minDim * 0.033f),
                verticalArrangement = Arrangement.spacedBy(minDim * 0.022f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(minDim * 0.111f).clip(CircleShape).background(thumbBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(row.icon, contentDescription = null, modifier = Modifier.size(minDim * 0.061f))
                    }
                    Spacer(modifier = Modifier.width(minDim * 0.033f))
                    Text(row.title, fontWeight = FontWeight.SemiBold, fontSize = (minDim * 0.044f).value.sp, modifier = Modifier.weight(1f))
                    Text("$percent%", fontWeight = FontWeight.SemiBold, fontSize = (minDim * 0.042f).value.sp)
                }
                Slider(
                    value = current.toFloat(),
                    onValueChange = { value ->
                        val v = value.toInt()
                        volumes = volumes.toMutableMap().apply { put(row.stream, v) }
                        try {
                            audio.setStreamVolume(row.stream, v, 0)
                        } catch (_: Exception) {
                        }
                    },
                    valueRange = 0f..max.toFloat(),
                    steps = (max - 1).coerceAtLeast(0),
                    colors = SliderDefaults.colors(
                        thumbColor = onPageColor,
                        activeTrackColor = onPageColor,
                        inactiveTrackColor = thumbBg
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (row.stream == AudioManager.STREAM_VOICE_CALL) {
                    Text(
                        "Call volume applies during a call.",
                        fontSize = (minDim * 0.033f).value.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(minDim * 0.022f))
    }
}
