// FILE: app/src/main/java/com/calnyx/app/navigation/NavGraph.kt
// MODERN version: top bar আগের মতোই (শুধু 3-dot), menu sheet + tiles black/white, new update icon.
// Navigation logic, routes, vault hiding — all unchanged from user's version.
package com.calnyx.app.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.calnyx.app.data.NavStateDatabase
import com.calnyx.app.data.NavStateEntity
import com.calnyx.app.ui.screens.CalTapeCalculatorScreen
import com.calnyx.app.ui.screens.ConverterCalculatorScreen
import com.calnyx.app.ui.screens.HistoryScreen
import com.calnyx.app.ui.screens.IctCalculatorScreen
import com.calnyx.app.ui.screens.NormalCalculatorScreen
import com.calnyx.app.ui.screens.NotepadCalculatorScreen
import com.calnyx.app.ui.screens.NumberSystemCalculatorScreen
import com.calnyx.app.ui.screens.ScientificCalculatorScreen
import com.calnyx.app.ui.screens.VaultScreen
import com.calnyx.app.ui.screens.VaultSetupScreen
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

    // Hidden vault — menuScreens-এ যোগ করা হয়নি, তাই মেনুতে
    // দেখাবে না। শুধু Normal calculator-এ সিক্রেট হিসাব
    // লিখে = চাপলেই এই রুটে যাওয়া যাবে।
    object Vault : Screen("vault", "Vault", "Hidden space", Icons.Default.Lock)

    // Setup — vault-এর মতোই মেনুতে নেই।
    // Normal calculator-e "=" long-press করলে যাওয়া যাবে।
    // Prothombar purono secret lagbe na, pore bodlate
    // chaile purono secret dite hobe।
    object VaultSetup :
            Screen("vault_setup", "Vault Setup", "Set hidden space code", Icons.Default.Lock)
}

val menuScreens =
        listOf(
                Screen.Normal,
                Screen.CalTape,
                Screen.Scientific,
                Screen.Converter,
                Screen.NumberSystem,
                Screen.Ict,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalNyxNavGraph(darkTheme: Boolean, onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    val navStateDao = remember { NavStateDatabase.get(context).navStateDao() }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var normalHistoryOpen by remember { mutableStateOf(false) }

    var showMenu by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // MODERN colours, derived from darkTheme only.
    val pageBg = if (darkTheme) NavOffBlack else NavOffWhite
    val cardBg = if (darkTheme) NavCardBlack else NavCardWhite
    val onPageColor = if (darkTheme) NavOffWhite else NavOffBlack

    val persistRoutes = remember { menuScreens.map { it.route }.toSet() }

    var startRoute by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val saved = navStateDao.observe().first()?.route
        startRoute =
                if (saved != null && saved in persistRoutes) {
                    saved
                } else {
                    Screen.Normal.route
                }
    }

    LaunchedEffect(currentRoute) {
        val route = currentRoute ?: return@LaunchedEffect
        if (route in persistRoutes) {
            navStateDao.save(NavStateEntity(route = route))
        }
    }

    fun closeMenuAndGo(route: String?, after: () -> Unit = {}) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            showMenu = false
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
                            onOpenVault = { navController.navigate(Screen.Vault.route) },
                            onOpenVaultSetup = { navController.navigate(Screen.VaultSetup.route) }
                    )
                }
                composable(Screen.Scientific.route) {
                    ScientificCalculatorScreen(darkTheme = darkTheme, onToggleTheme = onToggleTheme)
                }
                composable(Screen.Notepad.route) {
                    NotepadCalculatorScreen(darkTheme = darkTheme, onToggleTheme = onToggleTheme)
                }
                composable(Screen.CalTape.route) {
                    CalTapeCalculatorScreen(darkTheme = darkTheme, onToggleTheme = onToggleTheme)
                }
                composable(Screen.Converter.route) {
                    ConverterCalculatorScreen(darkTheme = darkTheme, onToggleTheme = onToggleTheme)
                }
                composable(Screen.NumberSystem.route) {
                    NumberSystemCalculatorScreen(
                            darkTheme = darkTheme,
                            onToggleTheme = onToggleTheme
                    )
                }
                composable(Screen.Ict.route) {
                    IctCalculatorScreen(darkTheme = darkTheme, onToggleTheme = onToggleTheme)
                }
                composable(Screen.History.route) {
                    HistoryScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.Vault.route) {
                    VaultScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.VaultSetup.route) {
                    VaultSetupScreen(
                            onBack = { navController.popBackStack() },
                            onSaved = {
                                navController.popBackStack()
                                navController.navigate(Screen.Vault.route)
                            }
                    )
                }
            }

            // Top bar — আগের মতোই: কোনো বড় বার নেই, কোনো app name নেই,
            // শুধু উপরে ডান কোণে ভাসমান 3-dot মেনু বাটন। History-তে বামে Back।
            // Vault আর VaultSetup-এ এটা লুকানো থাকে, কারণ ওদের
            // নিজের back বার আছে।
            AnimatedVisibility(
                    visible =
                            !(currentRoute == Screen.Normal.route && normalHistoryOpen) &&
                                    currentRoute != Screen.Vault.route &&
                                    currentRoute != Screen.VaultSetup.route,
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
                            TopBarCircleButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        else -> {
                            Spacer(modifier = Modifier.size(36.dp))
                        }
                    }

                    TopBarCircleButton(onClick = { showMenu = true }) {
                        Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // MODERN menu sheet — off-white / off-black, কোনো বেগুনি
            // রঙ নেই। Header-এ CalNyx + ছোট সাবটাইটেল, ডানদিকে
            // আপডেট আর থিম বাটন।
            if (showMenu) {
                ModalBottomSheet(
                        onDismissRequest = { showMenu = false },
                        sheetState = sheetState,
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                        containerColor = cardBg,
                        contentColor = onPageColor,
                        dragHandle = {
                            Box(
                                    modifier =
                                            Modifier.padding(vertical = 10.dp)
                                                    .width(40.dp)
                                                    .height(5.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(Color.Gray.copy(alpha = 0.4f))
                            )
                        }
                ) {
                    Column(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .navigationBarsPadding()
                                            .padding(horizontal = 20.dp)
                                            .padding(bottom = 20.dp)
                    ) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "CalNyx", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = onPageColor)
                                Text(text = "Calculator & tools", fontSize = 12.sp, color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            MenuCircleButton(
                                    onClick = { closeMenuAndGo(null) { showUpdate = true } },
                                    darkTheme = darkTheme
                            ) {
                                Icon(
                                        // MODERN update icon: আগের Download-এর বদলে
                                        // ঘড়ি-তির (Update) আইকন — আপডেট বোঝাতে এটাই পরিষ্কার।
                                        Icons.Default.Update,
                                        contentDescription = "App update",
                                        tint = if (darkTheme) NavOffBlack else NavOffWhite,
                                        modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            MenuCircleButton(
                                    onClick = onToggleTheme,
                                    darkTheme = darkTheme
                            ) {
                                Icon(
                                        if (darkTheme) Icons.Default.DarkMode
                                        else Icons.Default.LightMode,
                                        contentDescription = "Toggle theme",
                                        tint = if (darkTheme) NavOffBlack else NavOffWhite,
                                        modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Big simple tiles — 2 column, শুধু icon + title
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            menuScreens.chunked(2).forEach { rowItems ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    rowItems.forEach { screen ->
                                        BigMenuTile(
                                                title = screen.title,
                                                icon = screen.icon,
                                                active = currentRoute == screen.route,
                                                darkTheme = darkTheme,
                                                onClick = { closeMenuAndGo(screen.route) },
                                                modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        // NOTE: vault setup-এর কোনো বোতাম মেনুতে
                        // রাখা হয়নি, ইচ্ছা করে। মেনুতে দেখালেই
                        // বোঝা যাবে এখানে লুকানো কিছু আছে।
                        // Setup শুধু Normal calculator-e "="
                        // long-press করলে খুলবে।
                    }
                }
            }

            if (showUpdate) {
                UpdateDialog(onDismiss = { showUpdate = false })
            }
        }
    }
}

// Top-bar circle button — আগের মতোই: 36dp, হালকা translucent
// surfaceVariant ব্যাকগ্রাউন্ড, কোনো শ্যাডো/বর্ডার/কালো বাটন নেই।
@Composable
private fun TopBarCircleButton(
        onClick: () -> Unit,
        buttonSize: Dp = 36.dp,
        content: @Composable () -> Unit
) {
    Box(
            modifier =
                    Modifier.size(buttonSize)
                            .clip(CircleShape)
                            .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            ),
            contentAlignment = Alignment.Center
    ) { IconButton(onClick = onClick, modifier = Modifier.size(buttonSize)) { content() } }
}

// Menu header circle button — মেনু শিটের ভেতরে মডার্ন লুক:
// লাইট মোডে কালো বাটন সাদা আইকন, ডার্ক মোডে সাদা বাটন কালো আইকন।
@Composable
private fun MenuCircleButton(
        onClick: () -> Unit,
        buttonSize: Dp = 44.dp,
        darkTheme: Boolean = false,
        content: @Composable () -> Unit
) {
    Box(
            modifier =
                    Modifier.size(buttonSize)
                            .shadow(6.dp, CircleShape)
                            .clip(CircleShape)
                            .background(if (darkTheme) NavOffWhite else NavOffBlack)
                            .border(
                                    1.dp,
                                    if (darkTheme) Color.White.copy(alpha = 0.18f)
                                    else Color.Black.copy(alpha = 0.08f),
                                    CircleShape
                            ),
            contentAlignment = Alignment.Center
    ) { IconButton(onClick = onClick, modifier = Modifier.size(buttonSize)) { content() } }
}

// একটা big simple tile: মাঝখানে বড় icon circle, নিচে শুধু title —
// কোনো subtitle / description / check mark নেই।
// Active tile: লাইটে কালো, ডার্কে সাদা — উল্টো রঙের লেখা/আইকন।
// Inactive tile: সাদা/কালো কার্ড, শ্যাডো + বর্ডার।
@Composable
private fun BigMenuTile(
        title: String,
        icon: ImageVector,
        active: Boolean,
        darkTheme: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
) {
    val tileBg = when {
        active && darkTheme -> NavOffWhite
        active -> NavOffBlack
        darkTheme -> NavCardBlack
        else -> NavCardWhite
    }
    val tileBorder = if (darkTheme) NavBorderDark else NavBorderLight
    val tileText = when {
        active && darkTheme -> NavOffBlack
        active -> NavOffWhite
        darkTheme -> NavOffWhite
        else -> NavOffBlack
    }
    val iconBg = when {
        active && darkTheme -> NavOffBlack.copy(alpha = 0.12f)
        active -> NavOffWhite.copy(alpha = 0.18f)
        darkTheme -> NavThumbDark
        else -> NavThumbLight
    }
    Column(
            modifier =
                    modifier.shadow(8.dp, RoundedCornerShape(24.dp))
                            .clip(RoundedCornerShape(24.dp))
                            .background(tileBg)
                            .border(1.dp, tileBorder, RoundedCornerShape(24.dp))
                            .clickable(onClick = onClick)
                            .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
                modifier =
                        Modifier.size(58.dp)
                                .clip(CircleShape)
                                .background(iconBg),
                contentAlignment = Alignment.Center
        ) {
            Icon(
                    icon,
                    contentDescription = title,
                    modifier = Modifier.size(28.dp),
                    tint = tileText
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
                title,
                fontSize = 15.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = tileText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
        )
    }
}
