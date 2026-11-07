// NAVBAR % + SCROLLABLE DRAWER VERSION (2026-11-07) - REPLACE NavGraph.kt.
//  Mini/split/floating-e drawer scroll hoto na, karon: (1) drawer width
//  LocalConfiguration (DEVICE width) * 2/3 chilo - window choto holeo
//  drawer device-er moto choura hoto, screen-er baire chole jeto;
//  (2) drawer Column-e kono verticalScroll chilo na, ar Spacer
//  weight(1f) About item-ke niche thele window-er baire pathato.
//  V: drawer width = WINDOW width-er % (normal 66.7%, mini 85%),
//  tools list = weight(1f) + verticalScroll, About = fixed footer
//  (kokhono baire jabe na), header/icon/font/padding/gap - sob %.
//  Top bar-o % -e. Logic/routes/swipe/privacy - kichu bodlayni.
// ONBOARDING VERSION (2026-11-06) - REPLACE NavGraph.kt.
//  1. FIRST RUN: if OnboardingPrefs says the introduction was
//     never finished, CalNyxNavGraph shows OnboardingScreen
//     INSTEAD of the calculator (early return, before any
//     other remember). Finishing it (or Skip) saves done=true
//     and the calculator appears. Clearing app data shows it
//     again. Nothing else in the graph changed.
//  2. ABOUT: the About modal now has a Permissions section
//     (same OnboardingPermissions list as the introduction),
//     with each permission's state and a Grant button, so a
//     permission missed or skipped in the introduction can
//     be granted later from here.
// ABOUT-CRASH FIX (2026-11-06) - REPLACE NavGraph.kt.
//  About tapped -> app closed. Likely cause: the dialog
//  loaded the launcher icon with painterResource(
//  R.mipmap.ic_launcher). On this project the launcher
//  icon is an ADAPTIVE icon (mipmap-anydpi-v26 XML),
//  and painterResource cannot load an adaptive-icon
//  drawable - it throws and the app dies. Fixed by
//  loading the icon through PackageManager instead
//  (getApplicationIcon -> draw into a Bitmap ->
//  asImageBitmap), which works for ANY drawable,
//  adaptive included, with a Calculate-icon fallback
//  if even that fails. No other change.
// SPY + HARDWARE/SOUND + ABOUT VERSION (2026-11-06) - REPLACE NavGraph.kt.
//  1. RENAMED (drawer + page titles only; routes, function
//     names and behaviour unchanged): Hardcore -> Hardware,
//     Moan -> Sound, Blue Film -> Spy.
//  2. DRAWER SWIPE: HardcoreScreen / MoanScreen /
//     BlueFilmScreen now get onOpenDrawer, so a leftward
//     swipe anywhere on Hardware, Sound or Spy opens the
//     drawer (universal drawer rule).
//  3. ABOUT / UPDATE (swapped 2026-11-07): the header
//     top-right icon is now an Info (i) button - tap it and
//     the About modal opens; the bottom footer item is now
//     Update - tap it and the UpdateDialog opens. Function
//     unchanged, only the places swapped. The About modal
//     itself: app icon + CalNyx + version on top,
//     a Check for updates button (opens the existing
//     UpdateDialog), a description of the app, and below
//     it a Developer Contact row - tap it to open
//     masudibnbelat.vercel.app in the browser.
// FILE: app/src/main/java/com/calnyx/app/navigation/NavGraph.kt
// DRAWER-THEME VERSION (2026-11-06) - replace your old NavGraph.kt
// with this file. Based on CLIPBOARD. ONLY two drawer changes:
//  1. Header: the app logo circle is GONE. In its place is a
//     sun/moon circle — tap it and the theme changes at once
//     (Moon in light mode -> dark, Sun in dark mode -> light).
//  2. Bottom: the Dark/Light mode toggle is GONE. In its place is
//     "About this app" - tap it and the About modal opens
//     (WEBSITE_URL below, the developer's website).
//  Everything else is unchanged (drawer items, swipe, privacy,
//  reopen rule, Clipboard, Blue Film folders).
//  1. New drawer item LAST: Clipboard (ContentPaste icon).
//  2. New composable for Screen.Clipboard, passing onOpenDrawer
//     so a leftward swipe anywhere on the Clipboard screen opens
//     this right drawer (universal drawer rule).
//  3. REOPEN RULE - VERIFIED, logic NOT changed: Clipboard is in
//     menuScreens, so it is in persistRoutes, but it is NOT in
//     calculatorRoutes (only Normal / Scientific / CalTape are).
//     The LaunchedEffect(currentRoute) below therefore saves
//     Normal whenever Clipboard is open, so reopening the app
//     always lands on the Normal calculator, like every other
//     tool screen. No change to that logic was needed.
// RIGHTDRAWER VERSION - replace your old NavGraph.kt with this file.
// Based on SPLIT. Only the drawer changes:
//  1. The drawer now opens from the RIGHT, not the left.
//     Material3's ModalNavigationDrawer only opens from the
//     left, so the right drawer is a custom panel (scrim +
//     2/3-wide sheet sliding in from the right edge).
//  2. RIGHT-EDGE SWIPE opens it on every screen (a 28dp
//     strip on the right edge; drag left to open). LEFT-edge
//     swipe no longer opens anything. The 3-dot menu button
//     (top-right) still opens it, and tapping a tool,
//     the scrim, or dragging the panel right closes it.
//  3. Everything else is unchanged: drawer content
//     (logo, version, Info icon, tools list, theme
//     toggle), the 2/3 width, the 0.65 scrim, the reopen
//     rule, blackhole hiding.
// RIGHTDRAWER VERSION - replace your old NavGraph.kt with this file.
// SPLIT VERSION - replace your old NavGraph.kt with this file.
// Based on MOAN (DRAWER + FIXED2). Only changes:
// 1. NO TABS. MoanControl is split into two drawer sections:
//    Hardcore (Power + Network + Ball) and Moan (Sound only).
// 2. History drawer entry REMOVED. History route still exists
//    for the Normal calculator, only the drawer entry is gone.
// 3. Drawer is 2/3 of screen width, not full screen. Remaining
//    1/3 is dimmed by a stronger scrim (0.65 black).
// 4. Colours: white and black only. Red (NavRed) is used in
//    exactly one place - a small red dot as the badge on a
//    selected Moan / Hardcore item, to mark it as special.
//    No other colour anywhere. Theme toggle at the bottom stays.
// 5. REOPEN RULE: only the three calculator screens (Normal,
//    Scientific, CalTape) are remembered across close/reopen.
//    Any other menu screen (Converter, Number System, ICT,
//    Hardcore, Moan) saves Normal instead, so reopening the
//    app always lands on the Normal calculator.
// Based on FIXED2 (ON_RESUME session kill stays removed, AppLock
// Service still owns the session). Only the menu changes:
// 1. Top-right 3-dot button REMOVED. Top-LEFT hamburger (3-bar)
//    button now, same 36dp translucent circle style as before.
// 2. Bottom sheet REMOVED. Left ModalNavigationDrawer opens from
//    the left on hamburger tap (and left-edge swipe). The hamburger
//    button itself sits at the TOP-RIGHT, not top-left: CalTape
//    has its own top-left 3-dot button, and the two bars mixed
//    together. Top-right is the better spot - it is the standard
//    menu corner, it never clashes with a screen's own top-left
//    controls, and the hamburger icon stays (it opens a drawer).
//    History keeps its Back button on the top-left.
// 3. Drawer header (top): theme toggle circle (sun/moon — tap to
//    change theme at once), CalNyx, version, Info icon (opens
//    About). The bottom footer is Update. The old
//    app logo circle was removed here (2026-11-06).
//    Below: tools as a vertical list - icon + title per row.
//    History is in the list too. BlackHole / BlackHoleSetup / AppLock are
//    NOT in the drawer - they stay hidden, no menu trace.
// 4. To add a feature later: add its Screen to menuScreens below,
//    or add a NavigationDrawerItem in the drawer list section.
// Design: off-white / off-black only, no colour accents.

package com.calnyx.app.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.calnyx.app.ThemePrefs
import com.calnyx.app.onboarding.AppPermission
import com.calnyx.app.onboarding.OnboardingPermissions
import com.calnyx.app.onboarding.OnboardingPrefs
import com.calnyx.app.onboarding.PermissionKind
import com.calnyx.app.ui.screens.OnboardingScreen
import com.calnyx.app.applock.AppLockManager
import com.calnyx.app.blackhole.BlackHolePickerGuard
import com.calnyx.app.moan.FloatingBallService
import com.calnyx.app.moan.MoanControlPrefs
import com.calnyx.app.data.NavStateDatabase
import com.calnyx.app.data.NavStateEntity
import com.calnyx.app.ui.screens.BlueFilmScreen
import com.calnyx.app.ui.screens.AppLockScreen
import com.calnyx.app.ui.screens.HardcoreScreen
import com.calnyx.app.ui.screens.MoanScreen
import com.calnyx.app.ui.screens.CalTapeCalculatorScreen
import com.calnyx.app.ui.screens.ClipboardScreen
import com.calnyx.app.ui.screens.ConverterCalculatorScreen
import com.calnyx.app.ui.screens.HistoryScreen
import com.calnyx.app.ui.screens.IctCalculatorScreen
import com.calnyx.app.ui.screens.NormalCalculatorScreen
import com.calnyx.app.ui.screens.NotepadCalculatorScreen
import com.calnyx.app.ui.screens.NumberSystemCalculatorScreen
import com.calnyx.app.ui.screens.ScientificCalculatorScreen
import com.calnyx.app.ui.screens.BlackHoleScreen
import com.calnyx.app.ui.screens.BlackHoleSetupScreen
import com.calnyx.app.update.AppUpdateManager
import com.calnyx.app.update.UpdateDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class Screen(
        val route: String,
        val title: String,
        val subtitle: String,
        val icon: ImageVector
) {
    object Normal : Screen("normal", "Normal", "Everyday calculator", Icons.Default.Calculate)
    object Scientific :
            Screen("scientific", "Scientific", "Advanced functions", Icons.Default.Functions)
    object Notepad : Screen("notepad", "Notepad", "Notes with calculations", Icons.Default.NoteAlt)
    object CalTape :
            Screen("caltape", "CalTape", "Tape with sheets & totals", Icons.Default.ReceiptLong)
    object Converter :
            Screen(
                    "converter",
                    "Converter",
                    "Length, weight, volume & more",
                    Icons.Default.SwapHoriz
            )
    object NumberSystem :
            Screen(
                    "numbersystem",
                    "Number System",
                    "Binary, decimal, octal & hex",
                    Icons.Default.Numbers
            )
    object Ict :
            Screen(
                    "ict",
                    "ICT Calculator",
                    "Binary, octal, decimal & hex arithmetic",
                    Icons.Default.Computer
            )
    object History : Screen("history", "History", "Past calculations", Icons.Default.History)
    object Hardcore :
            Screen("hardcore", "Hardware", "Power, network & ball", Icons.Default.PowerSettingsNew)
    object Moan :
            Screen("moan", "Sound", "Sound", Icons.Default.VolumeUp)
    object BlueFilm :
            Screen("blue_film", "Spy", "Secret camera recorder", Icons.Default.Videocam)
    object Clipboard :
            Screen(
                    "clipboard",
                    "Clipboard",
                    "Copied text & images",
                    Icons.Default.ContentPaste
            )

    // Hidden blackhole — menuScreens-এ যোগ করা হয়নি, তাই মেনুতে
    // দেখাবে না। শুধু Normal calculator-এ সিক্রেট হিসাব
    // লিখে = চাপলেই এই রুটে যাওয়া যাবে।
    object BlackHole : Screen("blackhole", "Black Hole", "Hidden space", Icons.Default.Lock)

    // Setup — blackhole-এর মতোই মেনুতে নেই।
    // Normal calculator-e "=" long-press করলে যাওয়া যাবে।
    // Prothombar purono secret lagbe na, pore bodlate
    // chaile purono secret dite hobe।
    object BlackHoleSetup :
            Screen("blackhole_setup", "Black Hole Setup", "Set hidden space code", Icons.Default.Lock)

    // Hidden app lock — menuScreens-এ যোগ করা হয়নি, তাই মেনুতে
    // দেখাবে না। শুধু BlackHole-এর App Lock কার্ড থেকে যাওয়া যাবে।
    object AppLock :
            Screen("app_lock", "App Lock", "Locked apps", Icons.Default.Lock)
}

val menuScreens =
        listOf(
                Screen.Normal,
                Screen.CalTape,
                Screen.Scientific,
                Screen.Converter,
                Screen.NumberSystem,
                Screen.Ict,
                Screen.Hardcore,
                Screen.Moan,
                Screen.BlueFilm,
                Screen.Clipboard,
        )

// MODERN palette: only off-white / off-black / grey. No purple, no colour accents.
private val NavOffWhite = Color(0xFFF6F4EF)
private val NavOffBlack = Color(0xFF0E0E0E)
private val NavCardWhite = Color(0xFFFFFFFF)
private val NavCardBlack = Color(0xFF262626)
private val NavBorderLight = Color(0xFFE9E6DF)
private val NavBorderDark = Color(0xFF2B2B2B)
private val NavDrawerEdgeDark = Color(0xFF3D3D3D)
private val NavThumbLight = Color(0xFFEFEDE8)
private val NavThumbDark = Color(0xFF262626)
// Red is the ONLY extra colour, for specialty only. See header.
private val NavRed = Color(0xFFC62828)

// Website shown in the About dialog under "Developer Contact"
// (2026-11-07): the developer email was removed and replaced
// with this link. To change it, edit this one line - the
// browser intent below uses it.
private const val WEBSITE_URL = "https://masudibnbelat.vercel.app"

// PRIVACY (2026-11-07): "was backgrounded" flag, PERSISTED in
// SharedPreferences (not just remember) so it survives a process
// death while CalNyx is stopped. Set only on a REAL ON_STOP
// (picker closed, not a config change); the ON_START guard below
// consumes it. commit() on set: the flag must reach disk before a
// possible immediate process kill; apply() on clear is enough.
private const val PRIVACY_PREFS = "calnyx_privacy"
private const val KEY_WAS_BACKGROUNDED = "was_backgrounded"

private fun wasPrivacyBackgrounded(context: Context): Boolean =
    context.getSharedPreferences(PRIVACY_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_WAS_BACKGROUNDED, false)

private fun setPrivacyBackgrounded(context: Context, value: Boolean) {
    val edit = context.getSharedPreferences(PRIVACY_PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_WAS_BACKGROUNDED, value)
    if (value) edit.commit() else edit.apply()
}

@OptIn(ExperimentalMaterial3Api::class)
// Drawer update status (2026-11-07): the footer "Check update"
// item's live state. Top-level on purpose - Kotlin does not
// allow an enum declared inside a function (local enum).
enum class DrawerUpdateStatus { Idle, Checking, UpToDate, Available }

@Composable
fun CalNyxNavGraph(darkTheme: Boolean, onToggleTheme: () -> Unit) {
    val context = LocalContext.current

    // FIRST-RUN INTRODUCTION: show it instead of the calculator
    // until it is finished (or skipped). Early return, before any
    // other remember, so the hook order below never changes.
    var onboardingDone by remember { mutableStateOf(OnboardingPrefs.isDone(context)) }
    if (!onboardingDone) {
        OnboardingScreen(
            darkTheme = darkTheme,
            onFinish = {
                OnboardingPrefs.setDone(context)
                onboardingDone = true
            }
        )
        return
    }

    val navStateDao = remember { NavStateDatabase.get(context).navStateDao() }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // PRIVACY: BlackHole / BlackHoleSetup / AppLock khola rekhe app theke
    // ber hole (onno app, recents, screen lock — jekono ON_STOP),
    // fire asle ar sei private screen dekhabe na. ON_STOP-ei
    // backstack Normal calculator-e firiye deoa hoy, tai fire
    // asar somoy BlackHole-er ek jholok-o dekhabe na. Normal
    // calculator theke locked app unlock korle eta kichu kore na,
    // karon tokhon route Normal-i thake.
    // ON_START GUARD (2026-11-07): ON_STOP pop jodi kono karone na
    // chole (purono NavGraph, pop fail, process death-e backstack
    // restore), tahole fire asar somoy ON_START-e abar Normal-e
    // firiye deoa hoy. Picker theke fira / rotation-e eta fire na.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, navController) {
        // PRIVACY POP (shared by the ON_STOP close and the ON_START
        // guard below): if a private screen is on top, go back to
        // the Normal calculator, never showing the vault again.
        // DECOY POP (2026-11-07): back stack-ke Normal-e ferano,
        // kono shorto chara. Fresh start-e NavHost na thakle
        // (back stack entry null) kichu kore na — tokhon start
        // destination-i (niche, decoy hole Normal) kaj kore.
        val popToNormal: () -> Unit = {
            // Fresh start-e NavHost na thakle (back stack entry
            // null) kichu kore na — tokhon start destination-i
            // (niche, decoy hole Normal) kaj kore.
            if (navController.currentBackStackEntry != null) {
                if (!navController.popBackStack(Screen.Normal.route, false)) {
                    navController.navigate(Screen.Normal.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }
            }
        }
        val popPrivateToNormal: () -> Unit = {
            val route = navController.currentBackStackEntry?.destination?.route
            if (route == Screen.BlackHole.route ||
                route == Screen.BlackHoleSetup.route ||
                route == Screen.AppLock.route
            ) {
                popToNormal()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            // FIXED2: NO ON_RESUME session handling here any more.
            // AppLockService-FIXED2.kt handles CalNyx foreground:
            // decoy launch (pending != null) is not an exit,
            // manual CalNyx open (pending == null, session != null)
            // is an exit. Doing it in NavGraph fired during the
            // unlock handoff and killed a live session mid-use.
            // This observer now only does the PRIVACY ON_STOP below.
            if (event == Lifecycle.Event.ON_RESUME) {
                try {
                    AppLockManager.clearDecoyLaunch(context)
                } catch (_: Exception) {
                }
            }
            // Rotation-e activity recreate hoy, seta ber hoye jaoa na —
            // tai rotation-e BlackHole bondho hobe na, sudhu sotti ber holei hobe.
            // IMPORT FIX (2026-11-06): do NOT close the blackhole while
            // the system file picker (or the system delete-confirm
            // dialog) is open. That picker is a separate activity,
            // so CalNyx goes ON_STOP while it is on screen — but
            // the user has NOT left the blackhole, they are in the
            // middle of adding a file. Closing here popped the
            // BlackHole, the picker result had nowhere to return to,
            // and the import never ran. Real exits (other app,
            // recents, screen lock) have the guard clear, so they
            // still close the blackhole exactly as before.
            if (event == Lifecycle.Event.ON_STOP &&
                !BlackHolePickerGuard.isPickerOpen() &&
                (context as? Activity)?.isChangingConfigurations != true
            ) {
                // REAL backgrounding: remember it (persisted, so it
                // survives a process death while stopped). The
                // ON_START guard below re-checks even if this pop
                // fails or the process dies before it runs.
                setPrivacyBackgrounded(context, true)
                popPrivateToNormal()
            }
            // ON_START GUARD (2026-11-07, defense in depth): the
            // ON_STOP pop above is not enough on its own - if the
            // installed NavGraph is older (no observer), or the
            // pop fails, or the process dies while stopped and the
            // back stack is restored with the vault on top, the
            // vault would show again on reopen. So on every return
            // to foreground after a REAL backgrounding, close a
            // private screen again. Safe by construction:
            // - picker return: ON_STOP had the guard open, so the
            //   flag was never set - the import flow is untouched.
            // - rotation: a config-change ON_STOP never sets the
            //   flag, so rotating inside the vault keeps it open.
            // - unlock handoff: the decoy lands on Normal (and this
            //   guard pops a stale vault to Normal first), so the
            //   secret-code "=" handler runs with pending intact.
            if (event == Lifecycle.Event.ON_START) {
                if (wasPrivacyBackgrounded(context) &&
                    !BlackHolePickerGuard.isPickerOpen()
                ) {
                    popPrivateToNormal()
                }
                // DECOY (2026-11-07): App Lock ekta locked app-e tap
                // koray CalNyx-ke decoy hisebe khuleche. Decoy-te
                // SOBSOMOY Normal calculator dekhate hobe — user
                // jekhane chilo (Converter / Scientific / CalTape)
                // seta kokhono na. Launch-ta SINGLE_TOP, tai minimize
                // kora CalNyx sudhu samne ase, back stack jemon chilo
                // temoni thake — ei pop na thakle last section-tai
                // dekhay. ON_RESUME niche flag clear kore, tai proti
                // decoy launch-e ekbari chole; normal open-e flag
                // set hoy na, tai tokhon kichu kore na.
                if (AppLockManager.isDecoyLaunch(context)) {
                    popToNormal()
                }
                setPrivacyBackgrounded(context, false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var normalHistoryOpen by remember { mutableStateOf(false) }

    // THEME PERSISTENCE (2026-11-06): the mode the user last picked
    // is saved in SharedPreferences (ThemePrefs) and restored on
    // launch, so reopening the app — even days later — shows the
    // same mode (light stays light, dark stays dark) instead of
    // falling back to dark. The parent still owns the live
    // darkTheme state; this just syncs it once at startup and
    // saves every change. One-frame note: on launch the app may
    // show the parent's default for a single frame before this
    // sync flips it to the saved mode; the clean, flash-free fix
    // is to initialise the parent's state from ThemePrefs too.
    LaunchedEffect(Unit) {
        val saved = ThemePrefs.isDarkTheme(context, darkTheme)
        if (saved != darkTheme) onToggleTheme()
    }

    // FLOATING BALL (2026-11-07): the ball was only ever started
    // from the Hardware screen, so after the app was killed or
    // the phone rebooted it stayed gone until the user opened
    // Hardware. Restore it on every app launch when the toggle
    // is ON and the overlay permission is granted. start() is a
    // no-op without the permission, and re-starting an already
    // running service just re-applies the prefs.
    LaunchedEffect(Unit) {
        try {
            if (MoanControlPrefs.isBallEnabled(context) &&
                FloatingBallService.canDrawOverlays(context)
            ) {
                FloatingBallService.start(context)
            }
        } catch (_: Exception) {
        }
    }
    LaunchedEffect(darkTheme) {
        ThemePrefs.setDarkTheme(context, darkTheme)
    }

    var showUpdate by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    // RIGHT DRAWER state - a plain boolean, not DrawerState:
    // Material3's ModalNavigationDrawer only opens from the
    // LEFT, so the right drawer is a custom sliding panel
    // (see the overlay at the bottom of this file). The
    // hamburger, the right-edge swipe and the scrim all
    // drive this one value.
    var drawerOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // DRAWER UPDATE STATUS (2026-11-07, drawer only): the footer
    // "Check update" item is a live status. Auto-check runs once
    // per app launch when a data connection appears (WiFi or
    // mobile data): Idle -> Checking -> UpToDate / Available.
    // A failed check falls back to Idle, so tapping the item
    // retries by hand (the UpdateDialog checks again). Status
    // colours are the user's explicit exception to the
    // black/white-only rule: green = up to date, yellow =
    // checking, red = update available.
    var updateStatus by remember { mutableStateOf(DrawerUpdateStatus.Idle) }
    var latestVersion by remember { mutableStateOf("") }
    var updateChecked by remember { mutableStateOf(false) }
    val updateGreen = Color(0xFF43A047)
    val updateYellow = Color(0xFFFFB300)
    val updateRed = Color(0xFFE53935)

    // Spin for the checking icon (created always, only read
    // while Checking, so it costs nothing in other states).
    val updateSpin by rememberInfiniteTransition(label = "updateSpin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "updateSpinAngle"
    )

    fun isOnline(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (_: Exception) {
            false
        }
    }

    fun runUpdateCheck() {
        if (updateChecked) return
        if (updateStatus == DrawerUpdateStatus.Checking) return
        updateStatus = DrawerUpdateStatus.Checking
        scope.launch {
            try {
                val result = AppUpdateManager.checkForUpdate(context)
                updateStatus = result.fold(
                    onSuccess = { info ->
                        updateChecked = true
                        if (info == null) {
                            DrawerUpdateStatus.UpToDate
                        } else {
                            latestVersion = info.version
                            DrawerUpdateStatus.Available
                        }
                    },
                    onFailure = { DrawerUpdateStatus.Idle }
                )
            } catch (_: Exception) {
                updateStatus = DrawerUpdateStatus.Idle
            }
        }
    }

    // Auto-check: registerDefaultNetworkCallback fires onAvailable
    // at once if a connection is already up, and again whenever
    // one appears later (WiFi / mobile data switched on).
    DisposableEffect(Unit) {
        val cm = try {
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        } catch (_: Exception) {
            null
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (isOnline()) runUpdateCheck()
            }
        }
        try {
            cm?.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {
        }
        onDispose {
            try {
                cm?.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
    }

    // App version from PackageManager (BuildConfig is not enabled
    // in build.gradle.kts, so do not use BuildConfig.VERSION_NAME).
    val appVersion = remember {
        try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            info.versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    // MODERN colours, derived from darkTheme only.
    val pageBg = if (darkTheme) NavOffBlack else NavOffWhite
    val cardBg = if (darkTheme) NavCardBlack else NavCardWhite
    val onPageColor = if (darkTheme) NavOffWhite else NavOffBlack

    // All menu routes (for detecting a tool screen below).
    val persistRoutes = remember { menuScreens.map { it.route }.toSet() }
    // Only these three calculator screens are remembered across
    // close / reopen. A tool screen must NOT be remembered:
    // reopening must land on the Normal calculator.
    val calculatorRoutes = remember {
        setOf(
            Screen.Normal.route,
            Screen.Scientific.route,
            Screen.CalTape.route
        )
    }

    var startRoute by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val saved = navStateDao.observe().first()?.route
        startRoute =
                if (AppLockManager.isDecoyLaunch(context)) {
                    // DECOY (2026-11-07): lock block theke fresh
                    // start — decoy Normal-ei khulbe, mone rakha
                    // calculator-e (Scientific / CalTape) na.
                    Screen.Normal.route
                } else if (saved != null && saved in calculatorRoutes) {
                    saved
                } else {
                    Screen.Normal.route
                }
    }

    LaunchedEffect(currentRoute) {
        val route = currentRoute ?: return@LaunchedEffect
        if (route in calculatorRoutes) {
            // Normal / Scientific / CalTape: remember it, so
            // reopen restores this calculator.
            navStateDao.save(NavStateEntity(route = route))
        } else if (route in persistRoutes) {
            // Converter / Number System / ICT / Hardcore / Moan /
            // BlueFilm / Clipboard: remember Normal instead, so
            // reopen lands on the Normal calculator, not on the tool.
            navStateDao.save(NavStateEntity(route = Screen.Normal.route))
        }
        // History / BlackHole / BlackHoleSetup / AppLock: do not touch
        // the saved route. BlackHole is already popped to Normal
        // on ON_STOP by the privacy observer above.
    }

    fun closeDrawerAndGo(route: String?, after: () -> Unit = {}) {
        drawerOpen = false
        scope.launch {
            // Let the panel slide away before navigating,
            // like ModalNavigationDrawer's close() did.
            delay(280)
            after()
            if (route != null && route != currentRoute) {
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    // Right drawer - header + list. BlackHole / AppLock are NOT listed here.
    val drawerVisible = currentRoute != Screen.BlackHole.route &&
        currentRoute != Screen.BlackHoleSetup.route &&
        currentRoute != Screen.AppLock.route
    // Drawer width/offset/scrim - niche outer BoxWithConstraints-er
    // WINDOW size theke % hisab hobe (device size theke na).
    // The drawer must never stay open on a screen that hides
    // it (BlackHole / BlackHoleSetup / AppLock).
    LaunchedEffect(drawerVisible) { if (!drawerVisible) drawerOpen = false }
    // NAVBAR % ROOT: BoxWithConstraints window-er asol size dey -
    // split-e half height, floating-e choto window - sob % ekhan theke.
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(pageBg)) {
        val windowW = maxWidth
        val windowH = maxHeight
        val isMiniWindow = windowW < 320.dp || windowH < 480.dp
        // Drawer width % of WINDOW: normal 66.7% (2/3), mini-te 85%
        // (choto window-e 2/3 khub soru hoye jeto, item porte kosto hoto).
        val drawerWidth = if (isMiniWindow) windowW * 0.85f else windowW * 0.667f
        val drawerOffset by animateDpAsState(
            targetValue = if (drawerOpen) 0.dp else drawerWidth,
            animationSpec = tween(300), label = "drawerOffset"
        )
        val scrimAlpha by animateFloatAsState(
            targetValue = if (drawerOpen) 0.65f else 0f,
            animationSpec = tween(280), label = "scrimAlpha"
        )
        // Drawer % vars - sob drawerWidth / windowH-er %.
        val drawerPadH = drawerWidth * 0.067f // 16dp / 240dp
        val drawerPadV = windowH * 0.014f // 12dp / 850dp
        val drawerGapH = windowH * 0.019f // 16dp
        val drawerGapS = windowH * 0.009f // 8dp
        val headerCircle = drawerWidth * 0.217f // 52dp
        val headerIcon = headerCircle * 0.54f // 28dp
        val drawerTitleFont = (drawerWidth.value * 0.092f).sp // 22sp
        val drawerVersionFont = (drawerWidth.value * 0.05f).sp // 12sp
        val drawerItemFont = (drawerWidth.value * 0.062f).sp // 15sp
        val drawerAboutFont = (drawerWidth.value * 0.054f).sp // 13sp
        val drawerIconSize = drawerWidth * 0.10f // 24dp
        val drawerBadge = drawerWidth * 0.033f // 8dp
        val drawerCorner = drawerWidth * 0.067f // 16dp
        val drawerShadow = drawerWidth * 0.067f // 16dp
        val drawerBorder = drawerWidth * 0.004f // ~1dp
        val edgeZoneW = windowW * 0.078f // 28dp / 360dp
        val topBarPadH = windowW * 0.028f // 10dp
        val topBarPadV = windowH * 0.005f // 4dp
        val topBarCircle = windowW * 0.133f // 48dp
        val topBarHit = windowW * 0.155f // 56dp
        val topBarIcon = windowW * 0.061f // 22dp
        val start = startRoute
        if (start != null) {
            NavHost(
                    navController = navController,
                    startDestination = start,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = {
                        fadeIn(animationSpec = tween(320)) +
                                slideInHorizontally(animationSpec = tween(320)) { it / 4 }
                    },
                    exitTransition = {
                        fadeOut(animationSpec = tween(200)) +
                                slideOutHorizontally(animationSpec = tween(320)) { -it / 6 }
                    },
                    popEnterTransition = {
                        fadeIn(animationSpec = tween(320)) +
                                slideInHorizontally(animationSpec = tween(320)) { -it / 4 }
                    },
                    popExitTransition = {
                        fadeOut(animationSpec = tween(200)) +
                                slideOutHorizontally(animationSpec = tween(320)) { it / 6 }
                    }
            ) {
                composable(Screen.Normal.route) {
                    NormalCalculatorScreen(
                            darkTheme = darkTheme,
                            onToggleTheme = onToggleTheme,
                            onOpenHistory = { navController.navigate(Screen.History.route) },
                            onHistoryVisibilityChange = { normalHistoryOpen = it },
                            onOpenBlackHole = { navController.navigate(Screen.BlackHole.route) },
                            onOpenBlackHoleSetup = { navController.navigate(Screen.BlackHoleSetup.route) },
                            // SWIPE FIX (2026-11-06): the calculator's own
                            // swipe detector calls this to open the drawer.
                            // A leftward swipe anywhere on the calculator
                            // opens the drawer; a rightward swipe opens
                            // History. The NavGraph's 28dp right-edge zone
                            // stays as well, for the other screens.
                            onOpenDrawer = { drawerOpen = true }
                    )
                }
                composable(Screen.Scientific.route) {
                    ScientificCalculatorScreen(
                        darkTheme = darkTheme,
                        onToggleTheme = onToggleTheme,
                        onOpenDrawer = { drawerOpen = true }
                    )
                }
                composable(Screen.Notepad.route) {
                    NotepadCalculatorScreen(darkTheme = darkTheme, onToggleTheme = onToggleTheme)
                }
                composable(Screen.CalTape.route) {
                    CalTapeCalculatorScreen(
                        darkTheme = darkTheme,
                        onToggleTheme = onToggleTheme,
                        // CALTAPE SWIPE FIX (2026-11-06): a leftward drag
                        // anywhere on CalTape now opens this drawer —
                        // the 28dp right-edge zone alone never fired
                        // there, because CalTape's own Sheets drawer
                        // sits on top of it.
                        onOpenDrawer = { drawerOpen = true }
                    )
                }
                composable(Screen.Converter.route) {
                    ConverterCalculatorScreen(
                        darkTheme = darkTheme,
                        onToggleTheme = onToggleTheme,
                        onOpenDrawer = { drawerOpen = true }
                    )
                }
                composable(Screen.NumberSystem.route) {
                    NumberSystemCalculatorScreen(
                            darkTheme = darkTheme,
                            onToggleTheme = onToggleTheme,
                            onOpenDrawer = { drawerOpen = true }
                    )
                }
                composable(Screen.Ict.route) {
                    IctCalculatorScreen(
                        darkTheme = darkTheme,
                        onToggleTheme = onToggleTheme,
                        onOpenDrawer = { drawerOpen = true }
                    )
                }
                composable(Screen.Hardcore.route) {
                    HardcoreScreen(darkTheme = darkTheme, onOpenDrawer = { drawerOpen = true })
                }
                composable(Screen.Moan.route) {
                    MoanScreen(darkTheme = darkTheme, onOpenDrawer = { drawerOpen = true })
                }
                composable(Screen.BlueFilm.route) {
                    BlueFilmScreen(darkTheme = darkTheme, onOpenDrawer = { drawerOpen = true })
                }
                composable(Screen.Clipboard.route) {
                    ClipboardScreen(
                        darkTheme = darkTheme,
                        onToggleTheme = onToggleTheme,
                        // CLIPBOARD (2026-11-06): leftward swipe
                        // anywhere on the Clipboard screen opens
                        // the drawer (universal drawer rule).
                        onOpenDrawer = { drawerOpen = true }
                    )
                }
                composable(Screen.History.route) {
                    HistoryScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.BlackHole.route) {
                    BlackHoleScreen(
                            onBack = { navController.popBackStack() },
                            onOpenAppLock = { navController.navigate(Screen.AppLock.route) },
                            // THEME (2026-11-06): BlackHole follows the app's
                            // own toggle, not the system theme. No drawer
                            // here — it stays hidden, no menu trace.
                            darkTheme = darkTheme
                    )
                }
                composable(Screen.AppLock.route) {
                    AppLockScreen(
                            onBack = { navController.popBackStack() },
                            // THEME (2026-11-06): app toggle, not system.
                            // No drawer here — hidden screen.
                            darkTheme = darkTheme
                    )
                }
                composable(Screen.BlackHoleSetup.route) {
                    BlackHoleSetupScreen(
                            onBack = { navController.popBackStack() },
                            // THEME (2026-11-06): app toggle, not system.
                            // No drawer here — hidden screen.
                            darkTheme = darkTheme,
                            onSaved = {
                                navController.popBackStack()
                                navController.navigate(Screen.BlackHole.route)
                            }
                    )
                }
            }

            // Top bar — no big bar, no app name on top.
            // Only a floating hamburger (3-bar) on the top-RIGHT
            // (top-left clashed with CalTape's own 3-dot button).
            // History shows Back on the left instead.
            // BlackHole / BlackHoleSetup / AppLock hide it, they have
            // their own back bar.
            AnimatedVisibility(
                    visible =
                            !(currentRoute == Screen.Normal.route && normalHistoryOpen) &&
                                    currentRoute != Screen.BlackHole.route &&
                                    currentRoute != Screen.BlackHoleSetup.route &&
                                    currentRoute != Screen.AppLock.route,
                    enter = fadeIn(animationSpec = tween(250)),
                    exit = fadeOut(animationSpec = tween(250))
            ) {
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .statusBarsPadding()
                                        .padding(horizontal = topBarPadH, vertical = topBarPadV),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    when (currentRoute) {
                        Screen.History.route -> {
                            // Back stays on the top-LEFT (standard).
                            TopBarCircleButton(onClick = { navController.popBackStack() }, buttonSize = topBarCircle) {
                                Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(topBarIcon)
                                )
                            }
                            Spacer(modifier = Modifier.size(topBarHit))
                        }
                        else -> {
                            // DRAWER: 3-DOT menu on the top-RIGHT (the user
                            // asked for the 3-bar hamburger to become a
                            // 3-dot menu). Same translucent circle, now
                            // 48dp with a 56dp hit area — it opens the
                            // right drawer.
                            // Top-left is left empty on purpose: CalTape
                            // has its own top-left 3-dot button, and the
                            // two bars mixed together when both were left.
                            Spacer(modifier = Modifier.size(topBarHit))
                            TopBarCircleButton(onClick = { drawerOpen = true }, buttonSize = topBarCircle) {
                                Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "Menu",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(topBarIcon)
                                )
                            }
                        }
                    }
                }
            }

            if (showUpdate) {
                UpdateDialog(onDismiss = { showUpdate = false })
            }
            if (showAbout) {
                AboutDialog(
                    appVersion = appVersion,
                    darkTheme = darkTheme,
                    screenMin = minOf(windowW, windowH),
                    onClose = { showAbout = false },
                    onCheckUpdate = {
                        showAbout = false
                        showUpdate = true
                    }
                )
            }
        }

        // RIGHT DRAWER - custom panel sliding in from the
        // right. Material3's ModalNavigationDrawer only
        // opens from the left, so this is built by hand:
        // a scrim over the content, a panel 2/3 wide
        // sliding in from the right edge, and a 28dp
        // swipe zone on the right edge that opens it.
        // Left-edge swipe no longer opens anything.
        if (drawerVisible) {
            if (scrimAlpha > 0.01f) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = scrimAlpha))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { drawerOpen = false }
                )
            }
            if (drawerOpen || drawerOffset < drawerWidth) {
                Box(
                    modifier = Modifier.align(Alignment.CenterEnd)
                        .offset(x = drawerOffset)
                        .width(drawerWidth)
                        .fillMaxHeight()
                        .shadow(drawerShadow)
                        .background(cardBg)
                        .border(
                            drawerBorder,
                            if (darkTheme) NavDrawerEdgeDark else NavBorderLight
                        )
                        .pointerInput(drawerWidth) {
                            var acc = 0f
                            val closeThreshold = drawerWidth.toPx() * 0.25f
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { _, dragAmount ->
                                    acc += dragAmount
                                    if (acc > closeThreshold) {
                                        drawerOpen = false
                                        acc = 0f
                                    }
                                },
                                onDragEnd = { acc = 0f },
                                onDragCancel = { acc = 0f }
                            )
                        }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .statusBarsPadding()
                            .padding(horizontal = drawerPadH, vertical = drawerPadV)
                    ) {
                        // Header (FIXED - scroll hoy na): theme circle + CalNyx + version + Update.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(headerCircle)
                                    .shadow(drawerWidth * 0.025f, CircleShape)
                                    .clip(CircleShape)
                                    .background(if (darkTheme) NavOffWhite else NavOffBlack)
                                    .clickable { drawerOpen = false; onToggleTheme() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Toggle theme",
                                    tint = if (darkTheme) NavOffBlack else NavOffWhite,
                                    modifier = Modifier.size(headerIcon)
                                )
                            }
                            Spacer(modifier = Modifier.width(drawerWidth * 0.05f))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "CalNyx",
                                    fontSize = drawerTitleFont,
                                    fontWeight = FontWeight.Bold,
                                    color = onPageColor
                                )
                                if (appVersion.isNotEmpty()) {
                                    Text(
                                        text = "Version $appVersion",
                                        fontSize = drawerVersionFont,
                                        color = Color.Gray
                                    )
                                }
                            }
                            // INFO (header, 2026-11-07): the only i
                            // button in the drawer. Opens the About
                            // dialog. The Update icon moved to the
                            // bottom footer.
                            IconButton(onClick = { closeDrawerAndGo(null) { showAbout = true } }) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = "About this app",
                                    tint = onPageColor,
                                    modifier = Modifier.size(drawerIconSize)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(drawerGapH))
                        HorizontalDivider(color = if (darkTheme) NavBorderDark else NavBorderLight)
                        Spacer(modifier = Modifier.height(drawerGapS))
                        // Tools list (SCROLLABLE - V fix): weight(1f) +
                        // verticalScroll, tai mini/split/floating-e list
                        // jotoi lomba hok, scroll kore sob item-e jaoa jay.
                        // Age ekhane kono scroll chilo na, ar niche
                        // Spacer(weight 1f) About-ke baire thele dito.
                        Column(
                            modifier = Modifier.fillMaxWidth().weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(windowH * 0.005f)
                        ) {
                            menuScreens.forEach { screen ->
                                NavigationDrawerItem(
                                    label = {
                                        Text(
                                            screen.title,
                                            fontSize = drawerItemFont,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = if (currentRoute == screen.route) FontWeight.SemiBold else FontWeight.Medium
                                        )
                                    },
                                    icon = {
                                        Icon(
                                            screen.icon,
                                            contentDescription = screen.title,
                                            modifier = Modifier.size(drawerIconSize)
                                        )
                                    },
                                    selected = currentRoute == screen.route,
                                    onClick = { closeDrawerAndGo(screen.route) },
                                    badge = {
                                        if (currentRoute == screen.route &&
                                            (screen.route == Screen.Moan.route ||
                                                screen.route == Screen.Hardcore.route)
                                        ) {
                                            Box(
                                                modifier = Modifier.size(drawerBadge)
                                                    .clip(CircleShape)
                                                    .background(NavRed)
                                            )
                                        }
                                    },
                                    colors = NavigationDrawerItemDefaults.colors(
                                        selectedContainerColor = if (darkTheme) NavOffWhite else NavOffBlack,
                                        selectedIconColor = if (darkTheme) NavOffBlack else NavOffWhite,
                                        selectedTextColor = if (darkTheme) NavOffBlack else NavOffWhite,
                                        unselectedContainerColor = Color.Transparent,
                                        unselectedIconColor = onPageColor,
                                        unselectedTextColor = onPageColor
                                    ),
                                    shape = RoundedCornerShape(drawerCorner),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        // CHECK UPDATE (FIXED FOOTER - scroll hoy na,
                        // kokhono window-er baire jabe na, mini-teo
                        // sobsomoy dekha jabe). Live status, drawer
                        // only. Idle: "Check update" black/white.
                        // Checking: "Checking update", yellow text +
                        // spinning yellow icon, version hidden.
                        // UpToDate: "Updated" green + current version
                        // below. Available: "Update available" red +
                        // old -> new version below, red. Tap always
                        // opens the UpdateDialog.
                        HorizontalDivider(color = if (darkTheme) NavBorderDark else NavBorderLight)
                        Spacer(modifier = Modifier.height(drawerGapS))
                        NavigationDrawerItem(
                            label = {
                                when (updateStatus) {
                                    DrawerUpdateStatus.Idle -> Text(
                                        "Check update",
                                        fontSize = drawerAboutFont,
                                        fontWeight = FontWeight.Medium
                                    )
                                    DrawerUpdateStatus.Checking -> Text(
                                        "Checking update",
                                        fontSize = drawerAboutFont,
                                        fontWeight = FontWeight.Medium,
                                        color = updateYellow
                                    )
                                    DrawerUpdateStatus.UpToDate -> Column {
                                        Text(
                                            "Updated",
                                            fontSize = drawerAboutFont,
                                            fontWeight = FontWeight.Bold,
                                            color = updateGreen
                                        )
                                        Text(
                                            "v$appVersion",
                                            fontSize = drawerVersionFont,
                                            color = updateGreen
                                        )
                                    }
                                    DrawerUpdateStatus.Available -> Column {
                                        Text(
                                            "Update available",
                                            fontSize = drawerAboutFont,
                                            fontWeight = FontWeight.Bold,
                                            color = updateRed
                                        )
                                        Text(
                                            "v$appVersion → v$latestVersion",
                                            fontSize = drawerVersionFont,
                                            color = updateRed
                                        )
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    Icons.Default.Update,
                                    contentDescription = "App update",
                                    tint = when (updateStatus) {
                                        DrawerUpdateStatus.Idle -> onPageColor
                                        DrawerUpdateStatus.Checking -> updateYellow
                                        DrawerUpdateStatus.UpToDate -> updateGreen
                                        DrawerUpdateStatus.Available -> updateRed
                                    },
                                    modifier = Modifier.size(drawerIconSize)
                                        .then(
                                            if (updateStatus == DrawerUpdateStatus.Checking) {
                                                Modifier.rotate(updateSpin)
                                            } else {
                                                Modifier
                                            }
                                        )
                                )
                            },
                            selected = false,
                            onClick = { closeDrawerAndGo(null) { showUpdate = true } },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = Color.Transparent,
                                unselectedContainerColor = Color.Transparent,
                                unselectedIconColor = onPageColor,
                                unselectedTextColor = onPageColor
                            ),
                            shape = RoundedCornerShape(drawerCorner),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(drawerGapS))
                    }
                }
            }
            // Right-edge swipe zone to open the drawer.
            // Only while it is closed; taps pass through
            // (only a leftward drag is claimed).
            if (!drawerOpen) {
                Box(
                    modifier = Modifier.align(Alignment.CenterEnd)
                        .width(edgeZoneW)
                        .fillMaxHeight()
                        .pointerInput(windowW) {
                            var acc = 0f
                            val openThreshold = windowW.toPx() * 0.17f
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { _, dragAmount ->
                                    acc += dragAmount
                                    if (acc < -openThreshold) {
                                        drawerOpen = true
                                        acc = 0f
                                    }
                                },
                                onDragEnd = { acc = 0f },
                                onDragCancel = { acc = 0f }
                            )
                        }
                )
            }
        }
    }
}

// Top-bar circle button — 48dp visible circle inside a 56dp hit
// area, light translucent surfaceVariant background, no shadow /
// border / black button. (Was 36dp: the user reported on 2026-11-06
// that the 3-dot menu on CalTape was hard to tap — a tap anywhere
// around the dot now opens the drawer.) ONE click handler only, on
// the outer Box, so the Back button can never fire twice.
@Composable
private fun TopBarCircleButton(
        onClick: () -> Unit,
        buttonSize: Dp, // % size - callers pass topBarCircle (window %)
        content: @Composable () -> Unit
) {
    Box(
            modifier =
                    Modifier.size(buttonSize * 1.167f)
                            .clip(CircleShape)
                            .clickable { onClick() },
            contentAlignment = Alignment.Center
    ) {
        Box(
                modifier =
                        Modifier.size(buttonSize)
                                .clip(CircleShape)
                                .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                ),
                contentAlignment = Alignment.Center
        ) { content() }
    }
}

// ABOUT DIALOG (2026-11-06): opened from the drawer's bottom
// "About this app" item. Black / white only, English only.
// Top: app icon + CalNyx + version. Then a Check for updates
// button (opens the existing UpdateDialog - no new update
// mechanism), a description of the app, and a Developer Contact
// row (tap it to open masudibnbelat.vercel.app in the browser).
@Composable
private fun AboutDialog(
        appVersion: String,
        darkTheme: Boolean,
        screenMin: Dp, // % base: screen-er choto dik (windowW/windowH theke)
        onClose: () -> Unit,
        onCheckUpdate: () -> Unit
) {
    val context = LocalContext.current
    val cardBg = if (darkTheme) NavCardBlack else NavCardWhite
    val onPageColor = if (darkTheme) NavOffWhite else NavOffBlack
    val border = if (darkTheme) NavBorderDark else NavBorderLight
    // % SIZES (2026-11-07): About dialog-e ar kono fixed dp/sp nei -
    // sob screenMin-er %. Normal 360dp phone-e ager motoi dekhabe.
    val dlgCorner = screenMin * 0.067f   // 24dp
    val dlgCornerSm = screenMin * 0.044f // 16dp
    val dlgPad = screenMin * 0.056f      // 20dp
    val dlgGap = screenMin * 0.033f      // 12dp
    val dlgGapSm = screenMin * 0.022f    // 8dp
    val dlgIcon = screenMin * 0.20f      // 72dp
    val dlgIconSm = screenMin * 0.10f    // 36dp
    val dlgIconMd = screenMin * 0.061f   // 22dp
    val dlgIconLg = screenMin * 0.067f   // 24dp
    val dlgIconXs = screenMin * 0.056f   // 20dp
    val dlgBtnH = screenMin * 0.133f     // 48dp
    val dlgBtnHSm = screenMin * 0.122f   // 44dp
    val fTitle = (screenMin.value * 0.061f).sp // 22sp
    val fHead = (screenMin.value * 0.044f).sp  // 16sp
    val fBtn = (screenMin.value * 0.042f).sp   // 15sp
    val fBody = (screenMin.value * 0.039f).sp  // 14sp
    val fSub = (screenMin.value * 0.036f).sp   // 13sp
    // App icon WITHOUT painterResource: the launcher icon is
    // an adaptive-icon XML and painterResource throws on it.
    // PackageManager returns the real drawable (adaptive
    // included); draw it into a bitmap once and show that.
    // Null -> the Calculate icon fallback below.
    val appIconBitmap = remember(context) {
        try {
            val drawable = context.packageManager.getApplicationIcon(context.applicationInfo)
            val sizePx = 192
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    // Permissions section state: same list as the introduction.
    // Refreshed after every grant and every return from Settings.
    var permStates by remember { mutableStateOf(OnboardingPermissions.checkAll(context)) }
    fun refreshPerms() {
        permStates = OnboardingPermissions.checkAll(context)
    }
    val aboutRuntimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshPerms() }
    val aboutLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(aboutLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPerms()
        }
        aboutLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { aboutLifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun grantPermission(permission: AppPermission) {
        try {
            if (permission.kind == PermissionKind.RUNTIME) {
                if (permission.runtimePermissions.isNotEmpty()) {
                    aboutRuntimeLauncher.launch(permission.runtimePermissions.toTypedArray())
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
    Dialog(onDismissRequest = onClose) {
        Box(
                modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(dlgCorner))
                        .background(cardBg)
                        .padding(dlgPad)
        ) {
            Column(
                    modifier = Modifier.fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(dlgGap)
            ) {
                // App icon + name + version.
                if (appIconBitmap != null) {
                    Image(
                            bitmap = appIconBitmap,
                            contentDescription = "CalNyx logo",
                            modifier = Modifier.size(dlgIcon).clip(CircleShape)
                    )
                } else {
                    Box(
                            modifier = Modifier.size(dlgIcon)
                                    .clip(CircleShape)
                                    .background(onPageColor),
                            contentAlignment = Alignment.Center
                    ) {
                        Icon(
                                Icons.Default.Calculate,
                                contentDescription = "CalNyx logo",
                                tint = cardBg,
                                modifier = Modifier.size(dlgIconSm)
                        )
                    }
                }
                Text(
                        text = "CalNyx",
                        fontSize = fTitle,
                        fontWeight = FontWeight.Bold,
                        color = onPageColor
                )
                if (appVersion.isNotEmpty()) {
                    Text(
                            text = "Version $appVersion",
                            fontSize = fSub,
                            color = Color.Gray
                    )
                }
                // Update: opens the existing UpdateDialog.
                Box(
                        modifier = Modifier.fillMaxWidth().height(dlgBtnH)
                                .clip(RoundedCornerShape(dlgCornerSm))
                                .background(onPageColor)
                                .clickable { onCheckUpdate() },
                        contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                                Icons.Default.Update,
                                contentDescription = null,
                                tint = cardBg,
                                modifier = Modifier.size(dlgIconXs)
                        )
                        Spacer(modifier = Modifier.width(dlgGapSm))
                        Text(
                                text = "Check for updates",
                                fontSize = fBtn,
                                fontWeight = FontWeight.Bold,
                                color = cardBg
                        )
                    }
                }
                // What the app is.
                Text(
                        text = "CalNyx is an all-in-one calculator for everyday use \u2014 Normal, Scientific, CalTape, Converter, Number System, ICT and Notepad \u2014 with a hidden Black Hole vault for private photos, videos, documents and audio, an App Lock, Sound and Hardware controls, a floating ball, Spy camera recording and a Clipboard history. Everything works offline and stays on this phone.",
                        fontSize = fBody,
                        color = onPageColor,
                        textAlign = TextAlign.Center
                )
                HorizontalDivider(color = border)
                // Permissions - the same list as the first-run
                // introduction. A permission skipped there can be
                // granted here instead. Tap Grant to open the
                // system dialog or the system Settings screen.
                Text(
                        text = "Permissions",
                        fontSize = fHead,
                        fontWeight = FontWeight.Bold,
                        color = onPageColor
                )
                OnboardingPermissions.all().forEach { permission ->
                    val granted = permStates[permission.id] == true
                    val isStop = permission.id == OnboardingPermissions.ID_STOP_NOTIFICATIONS
                    val isToggle = permission.kind == PermissionKind.TOGGLE
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                                permission.icon,
                                contentDescription = null,
                                tint = onPageColor,
                                modifier = Modifier.size(dlgIconMd)
                        )
                        Spacer(modifier = Modifier.width(dlgGap))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                    text = permission.title,
                                    fontSize = fBtn,
                                    fontWeight = FontWeight.Bold,
                                    color = onPageColor
                            )
                            Text(
                                    text = if (granted) {
                                        if (isStop) "Notifications are off"
                                        else if (isToggle) "Hidden"
                                        else "Granted"
                                    } else {
                                        permission.description
                                    },
                                    fontSize = fSub,
                                    color = Color.Gray
                            )
                        }
                        if (isToggle) {
                            // TOGGLE items get a black / white Switch,
                            // never a Grant button. No M3 purple.
                            Switch(
                                    checked = granted,
                                    onCheckedChange = {
                                        OnboardingPermissions.setToggle(context, permission, it)
                                        refreshPerms()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = cardBg,
                                        checkedTrackColor = onPageColor,
                                        uncheckedThumbColor = onPageColor,
                                        uncheckedTrackColor = border
                                    )
                            )
                        } else if (granted) {
                            Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = onPageColor,
                                    modifier = Modifier.size(dlgIconMd)
                            )
                        } else {
                            Text(
                                    text = if (isStop) "Turn off" else "Grant",
                                    fontSize = fBody,
                                    fontWeight = FontWeight.Bold,
                                    color = onPageColor,
                                    modifier = Modifier.clickable { grantPermission(permission) }
                                            .padding(dlgGapSm)
                            )
                        }
                    }
                }
                HorizontalDivider(color = border)
                // Developer Contact: tap to open the website in the browser.
                Text(
                        text = "Developer Contact",
                        fontSize = fHead,
                        fontWeight = FontWeight.Bold,
                        color = onPageColor
                )
                Row(
                        modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(dlgCornerSm))
                                .border(1.dp, border, RoundedCornerShape(dlgCornerSm))
                                .clickable {
                                    try {
                                        context.startActivity(
                                                Intent(
                                                        Intent.ACTION_VIEW,
                                                        Uri.parse(WEBSITE_URL)
                                                )
                                        )
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Could not open the website", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(dlgGap),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                            Icons.Default.Language,
                            contentDescription = null,
                            tint = onPageColor,
                            modifier = Modifier.size(dlgIconLg)
                    )
                    Spacer(modifier = Modifier.width(dlgGap))
                    Text(
                            text = WEBSITE_URL.removePrefix("https://"),
                            fontSize = fBtn,
                            fontWeight = FontWeight.SemiBold,
                            color = onPageColor
                    )
                }
                // Close (black / white filled button, same as
                // Check for updates above: background onPageColor,
                // text cardBg - black in light mode, white in dark).
                Box(
                        modifier = Modifier.fillMaxWidth().height(dlgBtnHSm)
                                .clip(RoundedCornerShape(dlgCornerSm))
                                .background(onPageColor)
                                .clickable { onClose() },
                        contentAlignment = Alignment.Center
                ) {
                    Text(
                            text = "Close",
                            fontSize = fBtn,
                            fontWeight = FontWeight.Medium,
                            color = cardBg
                    )
                }
            }
        }
    }
}
