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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
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
import com.calnyx.app.ui.screens.HistoryScreen
import com.calnyx.app.ui.screens.NormalCalculatorScreen
import com.calnyx.app.ui.screens.NotepadCalculatorScreen
import com.calnyx.app.ui.screens.ScientificCalculatorScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String, val subtitle: String, val icon: ImageVector) {
    object Normal : Screen("normal", "Normal", "Everyday calculator", Icons.Default.Calculate)
    object Scientific : Screen("scientific", "Scientific", "Advanced functions", Icons.Default.Functions)
    object Notepad : Screen("notepad", "Notepad", "Notes with calculations", Icons.Default.NoteAlt)
    object CalTape : Screen("caltape", "CalTape", "Tape with sheets & totals", Icons.Default.ReceiptLong)
    object History : Screen("history", "History", "Past calculations", Icons.Default.History)
}

// Only these pages appear in the menu, and only these are remembered
// as the "last active page". (Financial, Matrix and Business were
// removed entirely, on the user's call.)
val menuScreens = listOf(
    Screen.Normal,
    Screen.CalTape,
    Screen.Scientific,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalNyxNavGraph(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val navStateDao = remember { NavStateDatabase.get(context).navStateDao() }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var normalHistoryOpen by remember { mutableStateOf(false) }

    // Menu bottom sheet
    var showMenu by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val persistRoutes = remember { menuScreens.map { it.route }.toSet() }

    // Read the saved page once; the NavHost is only created after this
    // arrives, so a cold reopen lands directly on the last active page.
    // A saved page that no longer exists falls back to Normal.
    var startRoute by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val saved = navStateDao.observe().first()?.route
        startRoute = if (saved != null && saved in persistRoutes) {
            saved
        } else {
            Screen.Normal.route
        }
    }

    // Save the page on every navigation change.
    LaunchedEffect(currentRoute) {
        val route = currentRoute ?: return@LaunchedEffect
        if (route in persistRoutes) {
            navStateDao.save(NavStateEntity(route = route))
        }
    }

    fun closeMenuAndGo(route: String?) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            showMenu = false
            if (route != null && route != currentRoute) {
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
                        onHistoryVisibilityChange = { normalHistoryOpen = it }
                    )
                }
                composable(Screen.Scientific.route) {
                    ScientificCalculatorScreen(
                        darkTheme = darkTheme,
                        onToggleTheme = onToggleTheme
                    )
                }
                composable(Screen.Notepad.route) {
                    NotepadCalculatorScreen(
                        darkTheme = darkTheme,
                        onToggleTheme = onToggleTheme
                    )
                }
                composable(Screen.CalTape.route) {
                    CalTapeCalculatorScreen(
                        darkTheme = darkTheme,
                        onToggleTheme = onToggleTheme
                    )
                }
                composable(Screen.History.route) {
                    HistoryScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            // Floating top bar: back button on the History route, menu
            // button on the right — both on soft circular backdrops.
            // While the Normal calculator's history area is open (HALF
            // or FULL), the WHOLE bar fades away completely.
            AnimatedVisibility(
                visible = !(currentRoute == Screen.Normal.route && normalHistoryOpen),
                enter = fadeIn(animationSpec = tween(250)),
                exit = fadeOut(animationSpec = tween(250))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
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
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        else -> {
                            Spacer(modifier = Modifier.size(42.dp))
                        }
                    }

                    TopBarCircleButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Menu — a modern bottom sheet instead of the old dropdown:
            // a brand header, one big rounded row per calculator with
            // its description, and a real Switch for the theme (the
            // sheet stays open, so the flip is visible live).
            if (showMenu) {
                ModalBottomSheet(
                    onDismissRequest = { showMenu = false },
                    sheetState = sheetState,
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 20.dp)
                    ) {
                        Text(
                            text = "CalNyx",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        Text(
                            text = "Choose a calculator",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 14.dp)
                        )

                        menuScreens.forEach { screen ->
                            SheetMenuItem(
                                title = screen.title,
                                subtitle = screen.subtitle,
                                icon = screen.icon,
                                active = currentRoute == screen.route,
                                onClick = { closeMenuAndGo(screen.route) }
                            )
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
                        )

                        // Theme row with a real switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onToggleTheme() }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (darkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                                    contentDescription = "Theme",
                                    modifier = Modifier.size(21.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (darkTheme) "Dark theme" else "Light theme",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "App appearance",
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = darkTheme,
                                onCheckedChange = { onToggleTheme() }
                            )
                        }
                    }
                }
            }
        }
    }
}

// Small circular backdrop button for the floating top bar.
@Composable
private fun TopBarCircleButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick) {
            content()
        }
    }
}

// One menu row in the bottom sheet: icon inside a tinted circle, name
// + description, and the ACTIVE page filled with a pill + peach check.
@Composable
private fun SheetMenuItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) colors.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    if (active) colors.primary.copy(alpha = 0.30f)
                    else colors.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = title,
                modifier = Modifier.size(21.dp),
                tint = if (active) colors.primary else colors.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 16.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = if (active) colors.onPrimaryContainer else colors.onSurface
            )
            Text(
                subtitle,
                fontSize = 12.5.sp,
                color = colors.onSurfaceVariant
            )
        }
        if (active) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colors.primary
            )
        }
    }
}
