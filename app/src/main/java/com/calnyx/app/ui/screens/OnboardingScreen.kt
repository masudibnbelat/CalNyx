// FILE: app/src/main/java/com/calnyx/app/ui/screens/OnboardingScreen.kt
// NEW FILE - first-run introduction. Shows once, on the very first
// open after install (NavGraph gates on OnboardingPrefs.isDone).
// 7 steps, swipe is off - move with Next / Back / Skip only:
//   0 Welcome - app logo + name, developer introduction.
//   1 Calculators - Normal, Scientific, CalTape, Converter,
//     Number System, ICT, Notepad, and how to use them.
//   2 Black Hole + App Lock - the hidden vault and locked apps.
//   3 Sound & Hardware - sound controls, power, network, ball.
//   4 Spy + Clipboard - secret camera recording, clipboard history.
//   5 Permissions - every permission the app needs, one by one.
//     Each row has a Grant button; granting shows a check. The
//     Next button stays DISABLED until every required permission
//     is granted. Skip (top right) exits the whole introduction.
//   6 All set - Start opens the calculator.
// English only, black / white only. The permission list comes from
// OnboardingPermissions - the same list the About dialog shows.
package com.calnyx.app.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.calnyx.app.onboarding.AppPermission
import com.calnyx.app.onboarding.OnboardingPermissions
import com.calnyx.app.onboarding.PermissionKind
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 7
private const val PAGE_PERMISSIONS = 5
private const val PAGE_DONE = 6

@Composable
fun OnboardingScreen(darkTheme: Boolean, onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { PAGE_COUNT })
    // SMOOTH BLACK (2026-11-07): the intro must never be pitch
    // black. Dark mode is a soft charcoal (#1E1E1E) with lifted
    // cards and borders, so it is easy on the eyes. Light mode
    // is untouched. Status + navigation bar follow pageBg.
    val pageBg = if (darkTheme) Color(0xFF1E1E1E) else Color(0xFFF6F4EF)
    val cardBg = if (darkTheme) Color(0xFF2A2A2A) else Color(0xFFFFFFFF)
    val cardBorder = if (darkTheme) Color(0xFF3D3D3D) else Color(0xFFE9E6DF)
    val onPageColor = if (darkTheme) Color(0xFFF6F4EF) else Color(0xFF0E0E0E)
    val thumbBg = if (darkTheme) Color(0xFF333333) else Color(0xFFEFEDE8)

    // Status + navigation bar match the page background.
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

    // Permission states, refreshed after every grant and every
    // return from a Settings screen (ON_RESUME).
    var permStates by remember { mutableStateOf(OnboardingPermissions.checkAll(context)) }
    fun refreshPerms() {
        permStates = OnboardingPermissions.checkAll(context)
    }
    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshPerms() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPerms()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val page = pagerState.currentPage
    val allRequiredDone = OnboardingPermissions.required().all { permStates[it.id] == true }
    val nextEnabled = page != PAGE_PERMISSIONS || allRequiredDone

    fun toggle(permission: AppPermission, value: Boolean) {
        OnboardingPermissions.setToggle(context, permission, value)
        refreshPerms()
    }

    fun grant(permission: AppPermission) {
        try {
            if (permission.kind == PermissionKind.RUNTIME) {
                if (permission.runtimePermissions.isNotEmpty()) {
                    runtimeLauncher.launch(permission.runtimePermissions.toTypedArray())
                } else {
                    refreshPerms()
                }
            } else {
                val intent = OnboardingPermissions.settingsIntent(context, permission)
                if (intent != null) context.startActivity(intent)
            }
        } catch (_: Exception) {
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(pageBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        // Top row: page dots on the left, Skip on the right.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(PAGE_COUNT) { index ->
                    Box(
                        modifier = Modifier.size(if (index == page) 10.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == page) onPageColor
                                else onPageColor.copy(alpha = 0.3f)
                            )
                    )
                }
            }
            if (page != PAGE_DONE) {
                Text(
                    text = "Skip",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = onPageColor,
                    modifier = Modifier.clickable { onFinish() }.padding(8.dp)
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) { pagerPage ->
            Column(
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (pagerPage) {
                    0 -> WelcomePage(onPageColor, thumbBg, cardBg, cardBorder)
                    1 -> CalculatorsPage(onPageColor, thumbBg)
                    2 -> BlackHolePage(onPageColor, thumbBg)
                    3 -> SoundHardwarePage(onPageColor, thumbBg)
                    4 -> SpyClipboardPage(onPageColor, thumbBg)
                    5 -> PermissionsPage(
                        onPageColor = onPageColor,
                        cardBg = cardBg,
                        cardBorder = cardBorder,
                        permStates = permStates,
                        allRequiredDone = allRequiredDone,
                        onGrant = ::grant,
                        onToggle = ::toggle
                    )
                    6 -> DonePage(onPageColor, thumbBg)
                }
            }
        }

        // Bottom row: Back + Next / Start.
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (page > 0) {
                Row(
                    modifier = Modifier.clickable {
                        scope.launch { pagerState.animateScrollToPage(page - 1) }
                    }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = null,
                        tint = onPageColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Back",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = onPageColor
                    )
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier.clip(RoundedCornerShape(16.dp))
                    .background(
                        if (nextEnabled) onPageColor
                        else onPageColor.copy(alpha = 0.25f)
                    )
                    .clickable(enabled = nextEnabled) {
                        if (page == PAGE_DONE) {
                            onFinish()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(page + 1) }
                        }
                    }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (page == PAGE_DONE) "Start" else "Next",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = pageBg
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = pageBg,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// App icon WITHOUT painterResource (the launcher icon is an
// adaptive-icon XML and painterResource throws on it - same fix
// as the About dialog). PackageManager returns the real drawable;
// draw it into a bitmap once. Null -> Calculate icon fallback.
@Composable
private fun AppIconImage(size: androidx.compose.ui.unit.Dp, bgColor: Color, fgColor: Color) {
    val context = LocalContext.current
    val bitmap: ImageBitmap? = remember(context) {
        try {
            val drawable = context.packageManager.getApplicationIcon(context.applicationInfo)
            val sizePx = 192
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)
            bmp.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "CalNyx logo",
            modifier = Modifier.size(size).clip(CircleShape)
        )
    } else {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Calculate,
                contentDescription = "CalNyx logo",
                tint = fgColor,
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}

@Composable
private fun PageTitle(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        color = color
    )
}

@Composable
private fun PageSubtitle(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 15.sp,
        color = color.copy(alpha = 0.75f)
    )
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, text: String, onPageColor: Color, thumbBg: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(CircleShape).background(thumbBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = onPageColor, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = onPageColor)
            Text(text = text, fontSize = 14.sp, color = onPageColor.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun WelcomePage(onPageColor: Color, thumbBg: Color, cardBg: Color, cardBorder: Color) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        AppIconImage(size = 96.dp, bgColor = onPageColor, fgColor = cardBg)
        Text(
            text = "Welcome to CalNyx",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = onPageColor,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Your all-in-one calculator",
            fontSize = 16.sp,
            color = onPageColor.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Made by Masud Ibn Belat",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = onPageColor
            )
            Text(
                text = "Freelance full-stack developer from Sirajganj, Bangladesh. CalNyx is a calculator, a hidden private vault, an app lock, sound and hardware controls, a floating ball, a secret camera recorder and a clipboard history - in one app.",
                fontSize = 14.sp,
                color = onPageColor.copy(alpha = 0.8f)
            )
            Text(
                text = "Everything works offline and stays on this phone.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = onPageColor
            )
        }
    }
}

@Composable
private fun CalculatorsPage(onPageColor: Color, thumbBg: Color) {
    PageTitle("Calculators", onPageColor)
    PageSubtitle("Seven calculators in one place. Tap one in the menu to open it.", onPageColor)
    FeatureRow(Icons.Default.Calculate, "Normal", "Everyday calculator with history and Ans. Swipe right for History.", onPageColor, thumbBg)
    FeatureRow(Icons.Default.Functions, "Scientific", "Advanced functions. Pull the keypad down to reveal them.", onPageColor, thumbBg)
    FeatureRow(Icons.Default.ReceiptLong, "CalTape", "A tape with sheets and totals. Swipe left on it to open the menu.", onPageColor, thumbBg)
    FeatureRow(Icons.Default.SwapHoriz, "Converter", "Length, weight, volume and more.", onPageColor, thumbBg)
    FeatureRow(Icons.Default.Numbers, "Number System", "Binary, decimal, octal and hex.", onPageColor, thumbBg)
    FeatureRow(Icons.Default.Computer, "ICT", "Binary arithmetic for ICT study.", onPageColor, thumbBg)
    FeatureRow(Icons.Default.NoteAlt, "Notepad", "Notes with calculations.", onPageColor, thumbBg)
    PageSubtitle("Tip: swipe left anywhere to open the menu.", onPageColor)
}

@Composable
private fun BlackHolePage(onPageColor: Color, thumbBg: Color) {
    PageTitle("Black Hole & App Lock", onPageColor)
    PageSubtitle("Your private space, hidden inside the calculator.", onPageColor)
    FeatureRow(Icons.Default.Lock, "Black Hole vault", "Photos, videos, documents and audio, hidden from the gallery and the Files app. Nothing leaves this phone.", onPageColor, thumbBg)
    FeatureRow(Icons.Default.Calculate, "How to open it", "Type your secret calculation, then tap =. Long-press = to set or change the secret.", onPageColor, thumbBg)
    FeatureRow(Icons.Default.Lock, "App Lock", "Lock any app. Opening it shows the calculator instead, until you type the secret code and tap =.", onPageColor, thumbBg)
    PageSubtitle("The vault closes itself the moment you leave the app.", onPageColor)
}

@Composable
private fun SoundHardwarePage(onPageColor: Color, thumbBg: Color) {
    PageTitle("Sound & Hardware", onPageColor)
    PageSubtitle("Your phone's controls, from inside the calculator.", onPageColor)
    FeatureRow(Icons.Default.VolumeUp, "Sound", "Ringer modes and Media, Ringtone, Alarm and Call volumes.", onPageColor, thumbBg)
    FeatureRow(Icons.Default.PowerSettingsNew, "Hardware", "Power menu, Lock screen, Network switcher and screen orientation.", onPageColor, thumbBg)
    FeatureRow(Icons.Default.Calculate, "Floating Ball", "A white ball on top of every app: call, power, lock, flashlight, screenshot and more.", onPageColor, thumbBg)
    PageSubtitle("The ball never opens the app by itself - tap it and only its small panel opens.", onPageColor)
}

@Composable
private fun SpyClipboardPage(onPageColor: Color, thumbBg: Color) {
    PageTitle("Spy & Clipboard", onPageColor)
    PageSubtitle("Two quiet tools that work in the background.", onPageColor)
    FeatureRow(Icons.Default.Videocam, "Spy", "Secret camera recording with the front or back camera. Videos stay inside the app, never in the gallery.", onPageColor, thumbBg)
    FeatureRow(Icons.Default.ContentPaste, "Clipboard", "Everything you copy is saved offline - text and images. Open it from the menu any time.", onPageColor, thumbBg)
    PageSubtitle("Both work fully offline.", onPageColor)
}

@Composable
private fun DonePage(onPageColor: Color, thumbBg: Color) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Box(
            modifier = Modifier.size(96.dp).clip(CircleShape).background(onPageColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = thumbBg,
                modifier = Modifier.size(48.dp)
            )
        }
        Text(
            text = "You're all set",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = onPageColor,
            textAlign = TextAlign.Center
        )
        Text(
            text = "CalNyx is ready. Tap Start to open your calculator.",
            fontSize = 16.sp,
            color = onPageColor.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PermissionsPage(
    onPageColor: Color,
    cardBg: Color,
    cardBorder: Color,
    permStates: Map<String, Boolean>,
    allRequiredDone: Boolean,
    onGrant: (AppPermission) -> Unit,
    onToggle: (AppPermission, Boolean) -> Unit
) {
    PageTitle("Permissions", onPageColor)
    PageSubtitle(
        "CalNyx needs these to work. Grant each one - Next unlocks when everything required is granted. You can check this list later in About this app.",
        onPageColor
    )
    var notificationsKeptOn by remember { mutableStateOf(false) }
    OnboardingPermissions.all().forEach { permission ->
        val granted = permStates[permission.id] == true
        val isStop = permission.id == OnboardingPermissions.ID_STOP_NOTIFICATIONS
        val isToggle = permission.kind == PermissionKind.TOGGLE
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                        .background(if (granted) onPageColor else cardBorder),
                    contentAlignment = Alignment.Center
                ) {
                    if (granted) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = cardBg,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            permission.icon,
                            contentDescription = null,
                            tint = onPageColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = permission.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = onPageColor
                        )
                        if (permission.optional) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Optional",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = onPageColor.copy(alpha = 0.6f)
                            )
                        }
                    }
                    Text(
                        text = permission.description,
                        fontSize = 13.sp,
                        color = onPageColor.copy(alpha = 0.75f)
                    )
                }
            }
            // TOGGLE items get a black / white Switch, never a
            // Grant button. The Switch is B/W only - no M3 purple.
            if (isToggle) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (granted) "Hidden" else "Showing",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = onPageColor.copy(alpha = 0.75f),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = granted,
                        onCheckedChange = { onToggle(permission, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = cardBg,
                            checkedTrackColor = onPageColor,
                            uncheckedThumbColor = onPageColor,
                            uncheckedTrackColor = cardBorder
                        )
                    )
                }
            }
            if (!granted && !isStop && !isToggle) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(onPageColor)
                        .clickable { onGrant(permission) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Grant",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = cardBg
                    )
                }
            }
            if (isStop && !granted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier.weight(1f).height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(onPageColor)
                            .clickable { onGrant(permission) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Turn off",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = cardBg
                        )
                    }
                    Box(
                        modifier = Modifier.weight(1f).height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, onPageColor, RoundedCornerShape(12.dp))
                            .clickable { notificationsKeptOn = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Keep on",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = onPageColor
                        )
                    }
                }
            }
            if (isStop && notificationsKeptOn && !granted) {
                Text(
                    text = "Notifications stay on. You can turn them off later from About this app.",
                    fontSize = 13.sp,
                    color = onPageColor.copy(alpha = 0.75f)
                )
            }
        }
    }
    if (allRequiredDone) {
        Text(
            text = "All required permissions granted - tap Next.",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = onPageColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        Text(
            text = "Grant the remaining permissions to continue, or tap Skip to finish the introduction.",
            fontSize = 14.sp,
            color = onPageColor.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
