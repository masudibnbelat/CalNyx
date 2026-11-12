package com.calnyx.app.launcher

import android.content.Context
import android.os.BatteryManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherScreen(
    refreshKey: Int,
    onOpenCalculator: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val windowW = maxWidth
        val windowH = maxHeight
        val minDim = if (windowW < windowH) windowW else windowH
        val isDark = isSystemInDarkTheme()
        val bgColor = if (isDark) Color(0xFF111111) else Color(0xFFF5F5F5)
        val textColor = if (isDark) Color(0xFFF5F5F5) else Color(0xFF111111)
        val subTextColor = if (isDark) Color(0xFFBBBBBB) else Color(0xFF555555)
        val cardColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
        val context = LocalContext.current

        var query by remember { mutableStateOf("") }
        var hiddenVersion by remember { mutableIntStateOf(0) }
        var favVersion by remember { mutableIntStateOf(0) }
        var selectedApp by remember { mutableStateOf<LauncherAppInfo?>(null) }
        var showHidden by remember { mutableStateOf(false) }
        var clockTapCount by remember { mutableIntStateOf(0) }
        var clockText by remember {
            mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Calendar.getInstance().time))
        }
        var dateText by remember {
            mutableStateOf(SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Calendar.getInstance().time))
        }

        val allApps = remember(refreshKey) { LauncherAppsLoader.load(context) }
        val hiddenSet = remember(hiddenVersion, refreshKey) { LauncherPrefs.getHidden(context) }
        val favSet = remember(favVersion, refreshKey) { LauncherPrefs.getFavorites(context) }
        val iconBitmaps = remember(allApps) {
            allApps.associate { it.packageName to LauncherAppsLoader.drawableToBitmap(it.icon) }
        }
        val visibleApps = remember(allApps, hiddenSet, favSet) {
            allApps.filterNot { hiddenSet.contains(it.packageName) }
                .sortedWith(
                    compareByDescending<LauncherAppInfo> { favSet.contains(it.packageName) }
                        .thenBy { it.label.lowercase() }
                )
        }
        val favorites = remember(visibleApps, favSet) {
            visibleApps.filter { favSet.contains(it.packageName) }
        }
        val filteredApps = remember(visibleApps, query) {
            if (query.isBlank()) visibleApps else visibleApps.filter { it.label.contains(query, ignoreCase = true) }
        }
        val hiddenApps = remember(allApps, hiddenSet) {
            allApps.filter { hiddenSet.contains(it.packageName) }
        }
        val batteryManager = remember {
            context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        }
        val batteryPct = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        LaunchedEffect(Unit) {
            while (true) {
                val cal = Calendar.getInstance()
                clockText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)
                dateText = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(cal.time)
                delay(15000L)
            }
        }

        BackHandler {
            // Home screen: back does nothing.
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .padding(windowW * 0.04f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.clickable {
                        clockTapCount++
                        if (clockTapCount >= 3) {
                            clockTapCount = 0
                            showHidden = true
                        }
                    }
                ) {
                    Text(
                        text = clockText,
                        color = textColor,
                        fontSize = (minDim.value * 0.11f).sp
                    )
                    Text(
                        text = dateText,
                        color = subTextColor,
                        fontSize = (minDim.value * 0.035f).sp
                    )
                    Text(
                        text = "Battery $batteryPct%",
                        color = subTextColor,
                        fontSize = (minDim.value * 0.03f).sp
                    )
                }
                TextButton(onClick = { showHidden = true }) {
                    Text(
                        text = "Hidden",
                        color = textColor,
                        fontSize = (minDim.value * 0.035f).sp
                    )
                }
            }

            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(text = "Search apps", color = subTextColor, fontSize = (minDim.value * 0.035f).sp) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = cardColor,
                    unfocusedContainerColor = cardColor,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(minDim * 0.04f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = windowH * 0.015f)
                    .height(windowH * 0.065f)
            )

            if (favorites.isNotEmpty() && query.isBlank()) {
                Text(
                    text = "Favorites",
                    color = subTextColor,
                    fontSize = (minDim.value * 0.032f).sp,
                    modifier = Modifier.padding(top = windowH * 0.015f, bottom = windowH * 0.008f)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(minDim * 0.03f)
                ) {
                    items(favorites, key = { it.packageName }) { app ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(minDim * 0.18f)
                                .combinedClickable(
                                    onClick = { LauncherAppsLoader.launch(context, app.packageName) },
                                    onLongClick = { selectedApp = app }
                                )
                        ) {
                            val bitmap = iconBitmaps[app.packageName]
                            if (bitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = bitmap,
                                    contentDescription = app.label,
                                    modifier = Modifier.size(minDim * 0.13f)
                                )
                            }
                            Text(
                                text = app.label,
                                color = textColor,
                                fontSize = (minDim.value * 0.028f).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            if (filteredApps.isEmpty()) {
                Text(
                    text = "No apps found",
                    color = subTextColor,
                    fontSize = (minDim.value * 0.035f).sp,
                    modifier = Modifier.padding(top = windowH * 0.03f)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(minDim * 0.02f),
                    verticalArrangement = Arrangement.spacedBy(windowH * 0.015f),
                    contentPadding = PaddingValues(top = windowH * 0.015f, bottom = windowH * 0.015f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.combinedClickable(
                                onClick = { LauncherAppsLoader.launch(context, app.packageName) },
                                onLongClick = { selectedApp = app }
                            )
                        ) {
                            val bitmap = iconBitmaps[app.packageName]
                            if (bitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = bitmap,
                                    contentDescription = app.label,
                                    modifier = Modifier.size(minDim * 0.14f)
                                )
                            }
                            Text(
                                text = app.label,
                                color = textColor,
                                fontSize = (minDim.value * 0.03f).sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = windowH * 0.005f)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardColor, RoundedCornerShape(minDim * 0.04f))
                    .border(1.dp, subTextColor, RoundedCornerShape(minDim * 0.04f))
                    .padding(vertical = windowH * 0.01f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onOpenCalculator) {
                    Text(text = "CalNyx", color = textColor, fontSize = (minDim.value * 0.032f).sp)
                }
                TextButton(onClick = { LauncherAppsLoader.openDialer(context) }) {
                    Text(text = "Phone", color = textColor, fontSize = (minDim.value * 0.032f).sp)
                }
                TextButton(onClick = { LauncherAppsLoader.openCamera(context) }) {
                    Text(text = "Camera", color = textColor, fontSize = (minDim.value * 0.032f).sp)
                }
                TextButton(onClick = { LauncherAppsLoader.openSystemSettings(context) }) {
                    Text(text = "Settings", color = textColor, fontSize = (minDim.value * 0.032f).sp)
                }
            }
        }

        val currentSelected = selectedApp
        if (currentSelected != null) {
            AlertDialog(
                onDismissRequest = { selectedApp = null },
                title = { Text(text = currentSelected.label, color = textColor) },
                text = {
                    Column {
                        TextButton(
                            onClick = {
                                LauncherPrefs.hideApp(context, currentSelected.packageName)
                                hiddenVersion++
                                selectedApp = null
                            }
                        ) {
                            Text(text = "Hide app", color = textColor)
                        }
                        TextButton(
                            onClick = {
                                LauncherPrefs.toggleFavorite(context, currentSelected.packageName)
                                favVersion++
                                selectedApp = null
                            }
                        ) {
                            val favLabel = if (favSet.contains(currentSelected.packageName)) "Remove from Favorites" else "Add to Favorites"
                            Text(text = favLabel, color = textColor)
                        }
                        TextButton(
                            onClick = {
                                LauncherAppsLoader.openAppInfo(context, currentSelected.packageName)
                                selectedApp = null
                            }
                        ) {
                            Text(text = "App info", color = textColor)
                        }
                        TextButton(
                            onClick = {
                                LauncherAppsLoader.uninstall(context, currentSelected.packageName)
                                selectedApp = null
                            }
                        ) {
                            Text(text = "Uninstall", color = Color(0xFFC62828))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedApp = null }) {
                        Text(text = "Close", color = textColor)
                    }
                }
            )
        }

        if (showHidden) {
            AlertDialog(
                onDismissRequest = { showHidden = false },
                title = { Text(text = "Hidden Apps", color = textColor) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(windowH * 0.5f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (hiddenApps.isEmpty()) {
                            Text(text = "No hidden apps", color = subTextColor)
                        } else {
                            for (app in hiddenApps) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = app.label,
                                        color = textColor,
                                        fontSize = (minDim.value * 0.035f).sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = {
                                            LauncherPrefs.unhideApp(context, app.packageName)
                                            hiddenVersion++
                                        }
                                    ) {
                                        Text(text = "Unhide", color = textColor)
                                    }
                                }
                            }
                        }
                        Text(
                            text = "Tip: tap the clock 3 times to open this list quickly.",
                            color = subTextColor,
                            fontSize = (minDim.value * 0.03f).sp,
                            modifier = Modifier.padding(top = windowH * 0.015f)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHidden = false }) {
                        Text(text = "Close", color = textColor)
                    }
                }
            )
        }
    }
}
