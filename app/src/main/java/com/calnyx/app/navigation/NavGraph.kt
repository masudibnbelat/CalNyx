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
//  3. ABOUT: the bottom "Contact Developer" item is now
//     "About this app" (small text). Tap it and an About
//     modal opens: app icon + CalNyx + version on top,
//     a Check for updates button (opens the existing
//     UpdateDialog), a description of the app, and below
//     it Contact Developer with the developer's name and
//     email - tap that row to send an email.
// FILE: app/src/main/java/com/calnyx/app/navigation/NavGraph.kt
// DRAWER-THEME VERSION (2026-11-06) - replace your old NavGraph.kt
// with this file. Based on CLIPBOARD. ONLY two drawer changes:
//  1. Header: the app logo circle is GONE. In its place is a
//     sun/moon circle — tap it and the theme changes at once
//     (Moon in light mode -> dark, Sun in dark mode -> light).
//  2. Bottom: the Dark/Light mode toggle is GONE. In its place is
//     "Contact Developer" — tap it and an email to the developer
//     opens (DEVELOPER_EMAIL below, from the user's CV).
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
//     (logo, version, Update icon, tools list, theme
//     toggle), the 2/3 width, the 0.55 scrim, the reopen
//     rule, blackhole hiding.
// RIGHTDRAWER VERSION - replace your old NavGraph.kt with this file.
// SPLIT VERSION - replace your old NavGraph.kt with this file.
// Based on MOAN (DRAWER + FIXED2). Only changes:
// 1. NO TABS. MoanControl is split into two drawer sections:
//    Hardcore (Power + Network + Ball) and Moan (Sound only).
// 2. History drawer entry REMOVED. History route still exists
//    for the Normal calculator, only the drawer entry is gone.
// 3. Drawer is 2/3 of screen width, not full screen. Remaining
//    1/3 is dimmed by a stronger scrim (0.55 black).
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
//    change theme at once), CalNyx, version, Update icon. The old
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Email
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.ui.platform.LocalConfiguration
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
import com.calnyx.app.ThemePrefs
import com.calnyx.app.applock.AppLockManager
import com.calnyx.app.blackhole.BlackHolePickerGuard
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
private val NavCardBlack = Color(0xFF1B1B1B)
private val NavBorderLight = Color(0xFFE9E6DF)
private val NavBorderDark = Color(0xFF2B2B2B)
private val NavThumbLight = Color(0xFFEFEDE8)
private val NavThumbDark = Color(0xFF262626)
// Red is the ONLY extra colour, for specialty only. See header.
private val NavRed = Color(0xFFC62828)

// Developer's email for the drawer's "Contact Developer" item
// (2026-11-06). Taken from the user's own CV. To change it, edit
// this one line — the mail intent below uses it.
private const val DEVELOPER_EMAIL = "masudibnbelat@gmail.com"
private const val DEVELOPER_NAME = "Md. Masud Khan"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalNyxNavGraph(darkTheme: Boolean, onToggleTheme: () -> Unit) {
    val context = LocalContext.current
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
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, navController) {
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
                val route = navController.currentBackStackEntry?.destination?.route
                if (route == Screen.BlackHole.route ||
                    route == Screen.BlackHoleSetup.route ||
                    route == Screen.AppLock.route
                ) {
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
                if (saved != null && saved in calculatorRoutes) {
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
    // Drawer is 2/3 of the screen, not full screen. The remaining
    // 1/3 stays visible behind the scrim, dimmed.
    val drawerWidth = (LocalConfiguration.current.screenWidthDp * 2 / 3).dp
    // Slide + scrim animations for the right drawer.
    val drawerOffset by animateDpAsState(
        targetValue = if (drawerOpen) 0.dp else drawerWidth,
        animationSpec = tween(300), label = "drawerOffset"
    )
    val scrimAlpha by animateFloatAsState(
        targetValue = if (drawerOpen) 0.55f else 0f,
        animationSpec = tween(280), label = "scrimAlpha"
    )
    // The drawer must never stay open on a screen that hides
    // it (BlackHole / BlackHoleSetup / AppLock).
    LaunchedEffect(drawerVisible) { if (!drawerVisible) drawerOpen = false }
    Box(modifier = Modifier.fillMaxSize().background(pageBg)) {
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
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    when (currentRoute) {
                        Screen.History.route -> {
                            // Back stays on the top-LEFT (standard).
                            TopBarCircleButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.size(56.dp))
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
                            Spacer(modifier = Modifier.size(56.dp))
                            TopBarCircleButton(onClick = { drawerOpen = true }) {
                                Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "Menu",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp)
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
                        .background(cardBg)
                        .pointerInput(Unit) {
                            var acc = 0f
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { _, dragAmount ->
                                    acc += dragAmount
                                    if (acc > 60f) {
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
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // Header: theme toggle circle + CalNyx +
                        // version + Update icon. The old app logo
                        // circle is gone (2026-11-06). The circle
                        // shows the Moon in light mode (tap -> dark)
                        // and the Sun in dark mode (tap -> light),
                        // same 52dp circle, same styling. Tap it and
                        // the theme changes at once (drawer closes
                        // so the change is visible immediately).
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(52.dp)
                                    .shadow(6.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(if (darkTheme) NavOffWhite else NavOffBlack)
                                    .clickable { drawerOpen = false; onToggleTheme() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Toggle theme",
                                    tint = if (darkTheme) NavOffBlack else NavOffWhite,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "CalNyx",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = onPageColor
                                )
                                if (appVersion.isNotEmpty()) {
                                    Text(
                                        text = "Version $appVersion",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                            IconButton(onClick = { closeDrawerAndGo(null) { showUpdate = true } }) {
                                Icon(
                                    Icons.Default.Update,
                                    contentDescription = "App update",
                                    tint = onPageColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = if (darkTheme) NavBorderDark else NavBorderLight)
                        Spacer(modifier = Modifier.height(8.dp))
                        // Tools list: icon + title, one row per tool.
                        // To add a feature later, add its Screen to
                        // menuScreens, or add another NavigationDrawerItem
                        // here in the same pattern.
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            menuScreens.forEach { screen ->
                                NavigationDrawerItem(
                                    label = {
                                        // Single line, never wrap: "Number System"
                                        // broke into two lines before. If a title
                                        // is ever too long it ellipsizes instead.
                                        Text(
                                            screen.title,
                                            fontSize = 15.sp,
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
                                            modifier = Modifier.size(24.dp)
                                        )
                                    },
                                    selected = currentRoute == screen.route,
                                    onClick = { closeDrawerAndGo(screen.route) },
                                    badge = {
                                        // Red dot only for the two special
                                        // sections, only when selected.
                                        if (currentRoute == screen.route &&
                                            (screen.route == Screen.Moan.route ||
                                                screen.route == Screen.Hardcore.route)
                                        ) {
                                            Box(
                                                modifier = Modifier.size(8.dp)
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
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        HorizontalDivider(color = if (darkTheme) NavBorderDark else NavBorderLight)
                        Spacer(modifier = Modifier.height(8.dp))
                        // ABOUT THIS APP at the bottom (2026-11-06).
                        // Replaces "Contact Developer" (that now lives
                        // INSIDE the About modal). Small text, tap it
                        // and the About modal opens.
                        NavigationDrawerItem(
                            label = { Text("About this app", fontSize = 13.sp, fontWeight = FontWeight.Medium) },
                            icon = {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = "About this app",
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            selected = false,
                            onClick = {
                                drawerOpen = false
                                showAbout = true
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = Color.Transparent,
                                unselectedContainerColor = Color.Transparent,
                                unselectedIconColor = onPageColor,
                                unselectedTextColor = onPageColor
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
            // Right-edge swipe zone to open the drawer.
            // Only while it is closed; taps pass through
            // (only a leftward drag is claimed).
            if (!drawerOpen) {
                Box(
                    modifier = Modifier.align(Alignment.CenterEnd)
                        .width(28.dp)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            var acc = 0f
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { _, dragAmount ->
                                    acc += dragAmount
                                    if (acc < -60f) {
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
        buttonSize: Dp = 48.dp,
        content: @Composable () -> Unit
) {
    Box(
            modifier =
                    Modifier.size(buttonSize + 8.dp)
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
// mechanism), a description of the app, and Contact Developer
// with the developer's name + email (tap the row to email).
@Composable
private fun AboutDialog(
        appVersion: String,
        darkTheme: Boolean,
        onClose: () -> Unit,
        onCheckUpdate: () -> Unit
) {
    val context = LocalContext.current
    val cardBg = if (darkTheme) NavCardBlack else NavCardWhite
    val onPageColor = if (darkTheme) NavOffWhite else NavOffBlack
    val border = if (darkTheme) NavBorderDark else NavBorderLight
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
    Dialog(onDismissRequest = onClose) {
        Box(
                modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(cardBg)
                        .padding(20.dp)
        ) {
            Column(
                    modifier = Modifier.fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // App icon + name + version.
                if (appIconBitmap != null) {
                    Image(
                            bitmap = appIconBitmap,
                            contentDescription = "CalNyx logo",
                            modifier = Modifier.size(72.dp).clip(CircleShape)
                    )
                } else {
                    Box(
                            modifier = Modifier.size(72.dp)
                                    .clip(CircleShape)
                                    .background(onPageColor),
                            contentAlignment = Alignment.Center
                    ) {
                        Icon(
                                Icons.Default.Calculate,
                                contentDescription = "CalNyx logo",
                                tint = cardBg,
                                modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Text(
                        text = "CalNyx",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = onPageColor
                )
                if (appVersion.isNotEmpty()) {
                    Text(
                            text = "Version $appVersion",
                            fontSize = 13.sp,
                            color = Color.Gray
                    )
                }
                // Update: opens the existing UpdateDialog.
                Box(
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(onPageColor)
                                .clickable { onCheckUpdate() },
                        contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                                Icons.Default.Update,
                                contentDescription = null,
                                tint = cardBg,
                                modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                                text = "Check for updates",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = cardBg
                        )
                    }
                }
                // What the app is.
                Text(
                        text = "CalNyx is an all-in-one calculator for everyday use \u2014 Normal, Scientific, CalTape, Converter, Number System, ICT and Notepad \u2014 with a hidden Black Hole vault for private photos, videos, documents and audio, an App Lock, Sound and Hardware controls, a floating ball, Spy camera recording and a Clipboard history. Everything works offline and stays on this phone.",
                        fontSize = 14.sp,
                        color = onPageColor,
                        textAlign = TextAlign.Center
                )
                HorizontalDivider(color = border)
                // Contact Developer: name + email, tap to email.
                Text(
                        text = "Contact Developer",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = onPageColor
                )
                Row(
                        modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, border, RoundedCornerShape(16.dp))
                                .clickable {
                                    try {
                                        context.startActivity(
                                                Intent.createChooser(
                                                        Intent(
                                                                Intent.ACTION_SENDTO,
                                                                Uri.parse("mailto:$DEVELOPER_EMAIL")
                                                        ),
                                                        "Contact developer"
                                                )
                                        )
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                            Icons.Default.Email,
                            contentDescription = null,
                            tint = onPageColor,
                            modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                                text = DEVELOPER_NAME,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = onPageColor
                        )
                        Text(
                                text = DEVELOPER_EMAIL,
                                fontSize = 13.sp,
                                color = Color.Gray
                        )
                    }
                }
                // Close.
                Box(
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onClose() },
                        contentAlignment = Alignment.Center
                ) {
                    Text(
                            text = "Close",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = onPageColor
                    )
                }
            }
        }
    }
}
