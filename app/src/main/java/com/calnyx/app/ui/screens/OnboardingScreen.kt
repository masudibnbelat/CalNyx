// FILE: app/src/main/java/com/calnyx/app/ui/screens/OnboardingScreen.kt
// NEW FILE - first-run introduction. Shows once, on the very first
// open after install (NavGraph gates on OnboardingPrefs.isDone).
// 8 steps, swipe is off - move with Next / Back / Skip only:
//   0 Welcome - app logo + name, what the app is.
//   1 Calculators - Normal, Scientific, CalTape, Converter,
//     Number System, ICT, Notepad, and how to use them.
//   2 Black Hole + App Lock - the hidden vault and locked apps.
//   3 Sound & Hardware - sound controls, power, network, ball.
//   4 Spy + Clipboard - secret camera recording, clipboard history.
//   5 Permissions - every permission the app needs, one by one.
//     Each row has a Grant button; granting shows a check. The
//     Next button stays DISABLED until every required permission
//     is granted. Skip (top right) exits the whole introduction.
//   6 Developer - who made the app: name, email, website.
//     Email and website rows are tappable (mail / browser).
//   7 All set - Start opens the calculator.
// English only, black / white only. The permission list comes from
// OnboardingPermissions - the same list the About dialog shows.
// % ONLY (2026-11-07): every size is a % of the window (windowW /
// windowH / minDim from BoxWithConstraints) - no fixed dp/sp
// anywhere except 1.dp hairline borders.
// WEBSITE (2026-11-07): the developer intro on the Welcome page
// is gone - it now introduces the APP instead. The developer
// email was removed everywhere, then brought back ONLY here, in
// the Developer step (user request 2026-11-07 evening). The About
// dialog still links only the website masudibnbelat.vercel.app
// (see NavGraph.kt - its row is now titled "Developer Contact").
package com.calnyx.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.calnyx.app.onboarding.AppPermission
import com.calnyx.app.onboarding.OnboardingPermissions
import com.calnyx.app.onboarding.PermissionKind
import kotlinx.coroutines.launch

// % base for the whole intro: minDim = the window's short side.
private data class IntroDims(val minDim: Dp, val windowW: Dp, val windowH: Dp)

// DEVELOPER (2026-11-07): shown only on the Developer intro step.
private const val DEVELOPER_NAME = "Masud Ibn Belat"
private const val DEVELOPER_EMAIL = "masudibnbelat@gmail.com"
private const val DEVELOPER_WEBSITE_URL = "https://masudibnbelat.vercel.app"
private const val DEVELOPER_WEBSITE_LABEL = "masudibnbelat.vercel.app"

private const val PAGE_COUNT = 8
private const val PAGE_PERMISSIONS = 5
private const val PAGE_DEVELOPER = 6
private const val PAGE_DONE = 7

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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val windowW = maxWidth
        val windowH = maxHeight
        val minDim = minOf(windowW, windowH)
        val d = IntroDims(minDim, windowW, windowH)
    Column(
        modifier = Modifier.fillMaxSize()
            .background(pageBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = d.windowW * 0.056f)
    ) {
        // Top row: page dots on the left, Skip on the right.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = d.windowH * 0.019f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(d.windowW * 0.017f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(PAGE_COUNT) { index ->
                    Box(
                        modifier = Modifier.size(if (index == page) d.minDim * 0.028f else d.minDim * 0.019f)
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
                    fontSize = (d.minDim * 0.042f).value.sp,
                    fontWeight = FontWeight.Medium,
                    color = onPageColor,
                    modifier = Modifier.clickable { onFinish() }.padding(d.minDim * 0.022f)
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
                    .padding(vertical = d.windowH * 0.025f),
                verticalArrangement = Arrangement.spacedBy(d.windowH * 0.019f)
            ) {
                when (pagerPage) {
                    0 -> WelcomePage(onPageColor, thumbBg, cardBg, cardBorder, d)
                    1 -> CalculatorsPage(onPageColor, thumbBg, d)
                    2 -> BlackHolePage(onPageColor, thumbBg, d)
                    3 -> SoundHardwarePage(onPageColor, thumbBg, d)
                    4 -> SpyClipboardPage(onPageColor, thumbBg, d)
                    5 -> PermissionsPage(
                        onPageColor = onPageColor,
                        cardBg = cardBg,
                        cardBorder = cardBorder,
                        permStates = permStates,
                        allRequiredDone = allRequiredDone,
                        onGrant = ::grant,
                        onToggle = ::toggle,
                        d = d
                    )
                    6 -> DeveloperPage(onPageColor, thumbBg, cardBg, cardBorder, d)
                    7 -> DonePage(onPageColor, thumbBg, d)
                }
            }
        }

        // Bottom row: Back + Next / Start.
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = d.windowH * 0.019f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (page > 0) {
                Row(
                    modifier = Modifier.clickable {
                        scope.launch { pagerState.animateScrollToPage(page - 1) }
                    }.padding(d.minDim * 0.022f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = null,
                        tint = onPageColor,
                        modifier = Modifier.size(d.minDim * 0.056f)
                    )
                    Spacer(modifier = Modifier.width(d.windowW * 0.011f))
                    Text(
                        text = "Back",
                        fontSize = (d.minDim * 0.042f).value.sp,
                        fontWeight = FontWeight.Medium,
                        color = onPageColor
                    )
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier.clip(RoundedCornerShape(d.minDim * 0.044f))
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
                    .padding(horizontal = d.windowW * 0.067f, vertical = d.windowH * 0.022f),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (page == PAGE_DONE) "Start" else "Next",
                        fontSize = (d.minDim * 0.044f).value.sp,
                        fontWeight = FontWeight.Bold,
                        color = pageBg
                    )
                    Spacer(modifier = Modifier.width(d.windowW * 0.017f))
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = pageBg,
                        modifier = Modifier.size(d.minDim * 0.056f)
                    )
                }
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
private fun PageTitle(text: String, color: Color, d: IntroDims) {
    Text(
        text = text,
        fontSize = (d.minDim * 0.072f).value.sp,
        fontWeight = FontWeight.Bold,
        color = color
    )
}

@Composable
private fun PageSubtitle(text: String, color: Color, d: IntroDims) {
    Text(
        text = text,
        fontSize = (d.minDim * 0.042f).value.sp,
        color = color.copy(alpha = 0.75f)
    )
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, text: String, onPageColor: Color, thumbBg: Color, d: IntroDims) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(d.minDim * 0.144f).clip(CircleShape).background(thumbBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = onPageColor, modifier = Modifier.size(d.minDim * 0.072f))
        }
        Spacer(modifier = Modifier.width(d.windowW * 0.033f))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = (d.minDim * 0.044f).value.sp, fontWeight = FontWeight.Bold, color = onPageColor)
            Text(text = text, fontSize = (d.minDim * 0.039f).value.sp, color = onPageColor.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun WelcomePage(onPageColor: Color, thumbBg: Color, cardBg: Color, cardBorder: Color, d: IntroDims) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(d.windowH * 0.019f)
    ) {
        Spacer(modifier = Modifier.height(d.windowH * 0.038f))
        AppIconImage(size = d.minDim * 0.267f, bgColor = onPageColor, fgColor = cardBg)
        Text(
            text = "Welcome to CalNyx",
            fontSize = (d.minDim * 0.078f).value.sp,
            fontWeight = FontWeight.Bold,
            color = onPageColor,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Your all-in-one calculator",
            fontSize = (d.minDim * 0.044f).value.sp,
            color = onPageColor.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(d.windowH * 0.013f))
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(d.minDim * 0.044f))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(d.minDim * 0.044f))
                .padding(d.minDim * 0.044f),
            verticalArrangement = Arrangement.spacedBy(d.windowH * 0.013f)
        ) {
            Text(
                text = "What is CalNyx?",
                fontSize = (d.minDim * 0.044f).value.sp,
                fontWeight = FontWeight.Bold,
                color = onPageColor
            )
            Text(
                text = "CalNyx is a calculator that does more than calculate. Behind the everyday calculator it hides a private vault, an app lock, sound and hardware controls, a floating ball, a secret camera recorder and a clipboard history - everything in one app, made to stay private.",
                fontSize = (d.minDim * 0.039f).value.sp,
                color = onPageColor.copy(alpha = 0.8f)
            )
            Text(
                text = "Everything works offline and stays on this phone.",
                fontSize = (d.minDim * 0.039f).value.sp,
                fontWeight = FontWeight.Medium,
                color = onPageColor
            )
        }
    }
}

@Composable
private fun CalculatorsPage(onPageColor: Color, thumbBg: Color, d: IntroDims) {
    PageTitle("Calculators", onPageColor, d)
    PageSubtitle("Seven calculators in one place. Tap one in the menu to open it.", onPageColor, d)
    FeatureRow(Icons.Default.Calculate, "Normal", "Everyday calculator with history and Ans. Swipe right for History.", onPageColor, thumbBg, d)
    FeatureRow(Icons.Default.Functions, "Scientific", "Advanced functions. Pull the keypad down to reveal them.", onPageColor, thumbBg, d)
    FeatureRow(Icons.Default.ReceiptLong, "CalTape", "A tape with sheets and totals. Swipe left on it to open the menu.", onPageColor, thumbBg, d)
    FeatureRow(Icons.Default.SwapHoriz, "Converter", "Length, weight, volume and more.", onPageColor, thumbBg, d)
    FeatureRow(Icons.Default.Numbers, "Number System", "Binary, decimal, octal and hex.", onPageColor, thumbBg, d)
    FeatureRow(Icons.Default.Computer, "ICT", "Binary arithmetic for ICT study.", onPageColor, thumbBg, d)
    FeatureRow(Icons.Default.NoteAlt, "Notepad", "Notes with calculations.", onPageColor, thumbBg, d)
    PageSubtitle("Tip: swipe left anywhere to open the menu.", onPageColor, d)
}

@Composable
private fun BlackHolePage(onPageColor: Color, thumbBg: Color, d: IntroDims) {
    PageTitle("Black Hole & App Lock", onPageColor, d)
    PageSubtitle("Your private space, hidden inside the calculator.", onPageColor, d)
    FeatureRow(Icons.Default.Lock, "Black Hole vault", "Photos, videos, documents and audio, hidden from the gallery and the Files app. Nothing leaves this phone.", onPageColor, thumbBg, d)
    FeatureRow(Icons.Default.Calculate, "How to open it", "Type your secret calculation, then tap =. Long-press = to set or change the secret.", onPageColor, thumbBg, d)
    FeatureRow(Icons.Default.Lock, "App Lock", "Lock any app. Opening it shows the calculator instead, until you type the secret code and tap =.", onPageColor, thumbBg, d)
    PageSubtitle("The vault closes itself the moment you leave the app.", onPageColor, d)
}

@Composable
private fun SoundHardwarePage(onPageColor: Color, thumbBg: Color, d: IntroDims) {
    PageTitle("Sound & Hardware", onPageColor, d)
    PageSubtitle("Your phone's controls, from inside the calculator.", onPageColor, d)
    FeatureRow(Icons.Default.VolumeUp, "Sound", "Ringer modes and Media, Ringtone, Alarm and Call volumes.", onPageColor, thumbBg, d)
    FeatureRow(Icons.Default.PowerSettingsNew, "Hardware", "Power menu, Lock screen, Network switcher and screen orientation.", onPageColor, thumbBg, d)
    FeatureRow(Icons.Default.Calculate, "Floating Ball", "A white ball on top of every app: call, power, lock, flashlight, screenshot and more.", onPageColor, thumbBg, d)
    PageSubtitle("The ball never opens the app by itself - tap it and only its small panel opens.", onPageColor, d)
}

@Composable
private fun SpyClipboardPage(onPageColor: Color, thumbBg: Color, d: IntroDims) {
    PageTitle("Spy & Clipboard", onPageColor, d)
    PageSubtitle("Two quiet tools that work in the background.", onPageColor, d)
    FeatureRow(Icons.Default.Videocam, "Spy", "Secret camera recording with the front or back camera. Videos stay inside the app, never in the gallery.", onPageColor, thumbBg, d)
    FeatureRow(Icons.Default.ContentPaste, "Clipboard", "Everything you copy is saved offline - text and images. Open it from the menu any time.", onPageColor, thumbBg, d)
    PageSubtitle("Both work fully offline.", onPageColor, d)
}

// DEVELOPER (2026-11-07): new step before All set, user request.
// Only three things, in a card like the Welcome page's "What is
// CalNyx?" card: name, email, website. Email and website rows
// are tappable (mail app / browser). % sizes only (IntroDims).
@Composable
private fun DeveloperPage(onPageColor: Color, thumbBg: Color, cardBg: Color, cardBorder: Color, d: IntroDims) {
    val context = LocalContext.current
    PageTitle("Developer", onPageColor, d)
    PageSubtitle("CalNyx is made by one person. Reach out any time.", onPageColor, d)
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(d.minDim * 0.044f))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(d.minDim * 0.044f))
            .padding(d.minDim * 0.044f),
        verticalArrangement = Arrangement.spacedBy(d.windowH * 0.019f)
    ) {
        ContactRow(
            icon = Icons.Default.Person,
            label = "Name",
            value = DEVELOPER_NAME,
            onPageColor = onPageColor,
            thumbBg = thumbBg,
            d = d,
            onClick = null
        )
        ContactRow(
            icon = Icons.Default.Email,
            label = "Email",
            value = DEVELOPER_EMAIL,
            onPageColor = onPageColor,
            thumbBg = thumbBg,
            d = d,
            onClick = {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$DEVELOPER_EMAIL"))
                    )
                } catch (_: Exception) {
                    Toast.makeText(context, "Could not open email", Toast.LENGTH_SHORT).show()
                }
            }
        )
        ContactRow(
            icon = Icons.Default.Language,
            label = "Website",
            value = DEVELOPER_WEBSITE_LABEL,
            onPageColor = onPageColor,
            thumbBg = thumbBg,
            d = d,
            onClick = {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(DEVELOPER_WEBSITE_URL))
                    )
                } catch (_: Exception) {
                    Toast.makeText(context, "Could not open the website", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    PageSubtitle("Tap the email or website row to open it.", onPageColor, d)
}

@Composable
private fun ContactRow(
    icon: ImageVector,
    label: String,
    value: String,
    onPageColor: Color,
    thumbBg: Color,
    d: IntroDims,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(d.minDim * 0.144f).clip(CircleShape).background(thumbBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = onPageColor, modifier = Modifier.size(d.minDim * 0.072f))
        }
        Spacer(modifier = Modifier.width(d.windowW * 0.033f))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = (d.minDim * 0.036f).value.sp, color = onPageColor.copy(alpha = 0.75f))
            Text(text = value, fontSize = (d.minDim * 0.044f).value.sp, fontWeight = FontWeight.Bold, color = onPageColor)
        }
    }
}

@Composable
private fun DonePage(onPageColor: Color, thumbBg: Color, d: IntroDims) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(d.windowH * 0.019f)
    ) {
        Spacer(modifier = Modifier.height(d.windowH * 0.05f))
        Box(
            modifier = Modifier.size(d.minDim * 0.267f).clip(CircleShape).background(onPageColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = thumbBg,
                modifier = Modifier.size(d.minDim * 0.133f)
            )
        }
        Text(
            text = "You're all set",
            fontSize = (d.minDim * 0.078f).value.sp,
            fontWeight = FontWeight.Bold,
            color = onPageColor,
            textAlign = TextAlign.Center
        )
        Text(
            text = "CalNyx is ready. Tap Start to open your calculator.",
            fontSize = (d.minDim * 0.044f).value.sp,
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
    onToggle: (AppPermission, Boolean) -> Unit,
    d: IntroDims
) {
    PageTitle("Permissions", onPageColor, d)
    PageSubtitle(
        "CalNyx needs these to work. Grant each one - Next unlocks when everything required is granted. You can check this list later in About this app.",
        onPageColor,
        d
    )
    var notificationsKeptOn by remember { mutableStateOf(false) }
    OnboardingPermissions.all().forEach { permission ->
        val granted = permStates[permission.id] == true
        val isStop = permission.id == OnboardingPermissions.ID_STOP_NOTIFICATIONS
        val isToggle = permission.kind == PermissionKind.TOGGLE
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(d.minDim * 0.044f))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(d.minDim * 0.044f))
                .padding(d.minDim * 0.033f),
            verticalArrangement = Arrangement.spacedBy(d.windowH * 0.013f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(d.minDim * 0.122f).clip(CircleShape)
                        .background(if (granted) onPageColor else cardBorder),
                    contentAlignment = Alignment.Center
                ) {
                    if (granted) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = cardBg,
                            modifier = Modifier.size(d.minDim * 0.067f)
                        )
                    } else {
                        Icon(
                            permission.icon,
                            contentDescription = null,
                            tint = onPageColor,
                            modifier = Modifier.size(d.minDim * 0.067f)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(d.windowW * 0.033f))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = permission.title,
                            fontSize = (d.minDim * 0.042f).value.sp,
                            fontWeight = FontWeight.Bold,
                            color = onPageColor
                        )
                        if (permission.optional) {
                            Spacer(modifier = Modifier.width(d.windowW * 0.017f))
                            Text(
                                text = "Optional",
                                fontSize = (d.minDim * 0.031f).value.sp,
                                fontWeight = FontWeight.Medium,
                                color = onPageColor.copy(alpha = 0.6f)
                            )
                        }
                    }
                    Text(
                        text = permission.description,
                        fontSize = (d.minDim * 0.036f).value.sp,
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
                        fontSize = (d.minDim * 0.036f).value.sp,
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
                    modifier = Modifier.fillMaxWidth().height(d.windowH * 0.069f)
                        .clip(RoundedCornerShape(d.minDim * 0.033f))
                        .background(onPageColor)
                        .clickable { onGrant(permission) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Grant",
                        fontSize = (d.minDim * 0.042f).value.sp,
                        fontWeight = FontWeight.Bold,
                        color = cardBg
                    )
                }
            }
            if (isStop && !granted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(d.windowW * 0.022f)
                ) {
                    Box(
                        modifier = Modifier.weight(1f).height(d.windowH * 0.069f)
                            .clip(RoundedCornerShape(d.minDim * 0.033f))
                            .background(onPageColor)
                            .clickable { onGrant(permission) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Turn off",
                            fontSize = (d.minDim * 0.042f).value.sp,
                            fontWeight = FontWeight.Bold,
                            color = cardBg
                        )
                    }
                    Box(
                        modifier = Modifier.weight(1f).height(d.windowH * 0.069f)
                            .clip(RoundedCornerShape(d.minDim * 0.033f))
                            .border(1.dp, onPageColor, RoundedCornerShape(d.minDim * 0.033f))
                            .clickable { notificationsKeptOn = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Keep on",
                            fontSize = (d.minDim * 0.042f).value.sp,
                            fontWeight = FontWeight.Medium,
                            color = onPageColor
                        )
                    }
                }
            }
            if (isStop && notificationsKeptOn && !granted) {
                Text(
                    text = "Notifications stay on. You can turn them off later from About this app.",
                    fontSize = (d.minDim * 0.036f).value.sp,
                    color = onPageColor.copy(alpha = 0.75f)
                )
            }
        }
    }
    if (allRequiredDone) {
        Text(
            text = "All required permissions granted - tap Next.",
            fontSize = (d.minDim * 0.039f).value.sp,
            fontWeight = FontWeight.Medium,
            color = onPageColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        Text(
            text = "Grant the remaining permissions to continue, or tap Skip to finish the introduction.",
            fontSize = (d.minDim * 0.039f).value.sp,
            color = onPageColor.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
