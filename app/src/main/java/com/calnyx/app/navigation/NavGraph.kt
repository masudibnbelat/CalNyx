// FILE: app/src/main/java/com/calnyx/app/navigation/NavGraph.kt
package com.calnyx.app.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SwapHoriz
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

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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

            // Compact floating top bar — 36dp buttons, কম padding
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

            // Modern menu sheet — বড় simple tile, শুধু icon + title,
            // কোনো description নেই
            if (showMenu) {
                ModalBottomSheet(
                        onDismissRequest = { showMenu = false },
                        sheetState = sheetState,
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        containerColor =
                                if (darkTheme) Color(0xFF241D25)
                                else MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        dragHandle = {
                            Box(
                                    modifier =
                                            Modifier.padding(vertical = 8.dp)
                                                    .width(32.dp)
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(
                                                            MaterialTheme.colorScheme
                                                                    .onSurfaceVariant.copy(
                                                                    alpha = 0.4f
                                                            )
                                                    )
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
                        // Simple header — শুধু title আর দুটো icon button
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "CalNyx", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.weight(1f))
                            TopBarCircleButton(
                                    onClick = { closeMenuAndGo(null) { showUpdate = true } }
                            ) {
                                Icon(
                                        Icons.Default.Download,
                                        contentDescription = "App update",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TopBarCircleButton(onClick = onToggleTheme) {
                                Icon(
                                        if (darkTheme) Icons.Default.DarkMode
                                        else Icons.Default.LightMode,
                                        contentDescription = "Toggle theme",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Big simple tiles — 2 column, শুধু icon + title
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            menuScreens.chunked(2).forEach { rowItems ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    rowItems.forEach { screen ->
                                        BigMenuTile(
                                                title = screen.title,
                                                icon = screen.icon,
                                                active = currentRoute == screen.route,
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

// একটা big simple tile: মাঝখানে বড় icon circle, নিচে শুধু title —
// কোনো subtitle / description / check mark নেই। Active tile টা
// primaryContainer রঙে আলাদা বোঝা যায়।
@Composable
private fun BigMenuTile(
        title: String,
        icon: ImageVector,
        active: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(
            modifier =
                    modifier.clip(RoundedCornerShape(24.dp))
                            .background(
                                    if (active) colors.primaryContainer
                                    else colors.surfaceVariant.copy(alpha = 0.35f)
                            )
                            .clickable(onClick = onClick)
                            .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
                modifier =
                        Modifier.size(58.dp)
                                .clip(CircleShape)
                                .background(
                                        if (active) colors.primary.copy(alpha = 0.25f)
                                        else colors.surfaceVariant
                                ),
                contentAlignment = Alignment.Center
        ) {
            Icon(
                    icon,
                    contentDescription = title,
                    modifier = Modifier.size(28.dp),
                    tint = if (active) colors.primary else colors.onSurface
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
                title,
                fontSize = 15.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = if (active) colors.onPrimaryContainer else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
        )
    }
}
