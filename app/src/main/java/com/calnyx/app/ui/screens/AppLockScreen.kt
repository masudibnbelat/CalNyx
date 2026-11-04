// FILE: app/src/main/java/com/calnyx/app/ui/screens/AppLockScreen.kt
// SMOOTH VERSION - replace your old AppLockScreen.kt with this file.
// Scroll fix + unlock time setting + black/white design polish.
// (The crash fix from the FIXED version — distinctBy key — is still here.)
// Crash fix: in the earlier version all app icons were
// decoded to bitmaps together on IO. One bad icon / label
// crashed the whole list load, at the same place every time.
// Now:
// 1. Each app in the list load has its own try/catch, a bad
//    app is skipped and the whole list does not crash.
// 2. Icons are no longer loaded together, each row loads its
//    own icon, and a bad one shows only a letter in that row.
// 3. Icons are scaled to 96x96, so they use less memory.
package com.calnyx.app.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calnyx.app.applock.AppLockManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class LaunchableApp(
    val packageName: String,
    val label: String
)

private fun drawableToBitmapScaled(drawable: Drawable): Bitmap? {
    return try {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, 96, 96)
        drawable.draw(canvas)
        bitmap
    } catch (_: Exception) { null }
}

// SMOOTHNESS: before, every time a row scrolled back into
// view its icon loaded from IO again, causing flicker and
// jank. Now an icon stays in the memory cache once loaded,
// so scrolling needs no more IO.
private object AppIconCache {
    private val bitmaps = mutableMapOf<String, Bitmap>()
    private val failed = mutableSetOf<String>()
    @Synchronized fun get(packageName: String): Bitmap? = bitmaps[packageName]
    @Synchronized fun isFailed(packageName: String): Boolean = packageName in failed
    @Synchronized fun put(packageName: String, bitmap: Bitmap?) {
        if (bitmap != null) bitmaps[packageName] = bitmap else failed.add(packageName)
    }
}

@Composable
private fun AppIcon(packageName: String, label: String) {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf(AppIconCache.get(packageName)) }
    var failed by remember(packageName) { mutableStateOf(AppIconCache.isFailed(packageName)) }
    LaunchedEffect(packageName) {
        if (bitmap != null || failed) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val drawable = pm.getApplicationIcon(appInfo)
                drawableToBitmapScaled(drawable)
            } catch (_: Exception) { null }
        }
        AppIconCache.put(packageName, loaded)
        if (loaded != null) bitmap = loaded else failed = true
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().padding(6.dp)
        )
    } else {
        Text(
            label.firstOrNull()?.uppercase() ?: "?",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatDurationMs(ms: Long): String = when {
    ms < 60_000L -> "${ms / 1000L} sec"
    ms % 60_000L == 0L -> "${ms / 60_000L} min"
    else -> "${ms / 1000L} sec"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val pageBg = if (isDark) Color(0xFF0E0E0E) else Color(0xFFF6F4EF)
    val cardBg = if (isDark) Color(0xFF1B1B1B) else Color(0xFFFFFFFF)
    val cardBorderColor = if (isDark) Color(0xFF2B2B2B) else Color(0xFFE9E6DF)
    val onPageColor = if (isDark) Color(0xFFF6F4EF) else Color(0xFF0E0E0E)
    val thumbBg = if (isDark) Color(0xFF262626) else Color(0xFFEFEDE8)

    var isEnabled by remember { mutableStateOf(AppLockManager.isEnabled(context)) }
    var lockedSet by remember { mutableStateOf(AppLockManager.getLockedPackages(context)) }
    var search by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    val accessibilityOn = AppLockManager.isAccessibilityEnabled(context)
    var unlockDuration by remember { mutableStateOf(AppLockManager.getUnlockDurationMs(context)) }
    val durationPresets = listOf(30_000L to "30 sec", 60_000L to "1 min", 300_000L to "5 min")
    var durationMenuExpanded by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var customAmount by remember { mutableStateOf("") }
    var customIsMinutes by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val list = withContext(Dispatchers.IO) {
            try {
                @Suppress("DEPRECATION")
                val pm = context.packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val infos = try {
                    pm.queryIntentActivities(mainIntent, 0)
                } catch (_: Exception) { emptyList() }
                infos.mapNotNull { info ->
                    try {
                        val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                        if (pkg == context.packageName) return@mapNotNull null
                        val label = try {
                            info.loadLabel(pm)?.toString() ?: pkg
                        } catch (_: Exception) { pkg }
                        LaunchableApp(pkg, label)
                    } catch (_: Exception) {
                        // One bad app must not break the whole list
                        null
                    }
                // CRASH FIX: one package can have several launcher
                // activities, e.g. com.android.stk appears twice.
                // Without distinctBy the same key would appear twice
                // in the LazyColumn and crash on scroll/fling with
                // IllegalArgumentException.
                }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
            } catch (_: Exception) {
                emptyList()
            }
        }
        apps = list
        loading = false
    }

    val filtered = remember(apps, search) {
        if (search.isBlank()) apps
        else apps.filter { it.label.contains(search, ignoreCase = true) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = pageBg,
        contentColor = onPageColor
    ) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(Modifier.weight(1f)) {
                    Text("App Lock", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (!isEnabled) "Off"
                        else if (lockedSet.isEmpty()) "On • No apps locked"
                        else if (lockedSet.size == 1) "On • 1 app locked"
                        else "On • ${lockedSet.size} apps locked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // COMPACT: one row only. No "App Lock" text (the
                // header already says it) and no "Auto-lock after"
                // title. Left: time input box, right: App Lock switch.
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp)
                            .clip(CircleShape)
                            .background(thumbBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = onPageColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    ExposedDropdownMenuBox(
                        expanded = durationMenuExpanded,
                        onExpandedChange = { durationMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = formatDurationMs(unlockDuration),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = durationMenuExpanded)
                            },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            modifier = Modifier.width(130.dp).menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = durationMenuExpanded,
                            onDismissRequest = { durationMenuExpanded = false }
                        ) {
                            durationPresets.forEach { (ms, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        unlockDuration = ms
                                        AppLockManager.setUnlockDurationMs(context, ms)
                                        durationMenuExpanded = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Custom time...") },
                                onClick = {
                                    durationMenuExpanded = false
                                    if (unlockDuration % 60_000L == 0L) {
                                        customIsMinutes = true
                                        customAmount = (unlockDuration / 60_000L).toString()
                                    } else {
                                        customIsMinutes = false
                                        customAmount = (unlockDuration / 1000L).toString()
                                    }
                                    showCustomDialog = true
                                }
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = {
                            isEnabled = it
                            AppLockManager.setEnabled(context, it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = pageBg,
                            checkedTrackColor = onPageColor,
                            checkedBorderColor = onPageColor,
                            uncheckedThumbColor = onPageColor.copy(alpha = 0.6f),
                            uncheckedTrackColor = thumbBg,
                            uncheckedBorderColor = cardBorderColor
                        )
                    )
                }

                if (showCustomDialog) {
                    val customValid = run {
                        val amount = customAmount.toLongOrNull() ?: return@run false
                        if (customIsMinutes) amount in 1..1440 else amount in 1..86400
                    }
                    AlertDialog(
                        onDismissRequest = { showCustomDialog = false },
                        title = { Text("Custom time") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = customAmount,
                                    onValueChange = { if (it.all { c -> c.isDigit() }) customAmount = it },
                                    label = { Text("Time") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("Seconds" to false, "Minutes" to true).forEach { (unitLabel, isMinutes) ->
                                        val selected = customIsMinutes == isMinutes
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(percent = 50))
                                                .background(if (selected) onPageColor else cardBg)
                                                .border(1.dp, cardBorderColor, RoundedCornerShape(percent = 50))
                                                .clickable { customIsMinutes = isMinutes }
                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                unitLabel,
                                                fontSize = 14.sp,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                                color = if (selected) pageBg else onPageColor
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                enabled = customValid,
                                onClick = {
                                    val amount = customAmount.toLongOrNull() ?: return@TextButton
                                    val ms = if (customIsMinutes) amount * 60_000L else amount * 1000L
                                    unlockDuration = ms
                                    AppLockManager.setUnlockDurationMs(context, ms)
                                    showCustomDialog = false
                                }
                            ) { Text("Save") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showCustomDialog = false }) { Text("Cancel") }
                        }
                    )
                }

                if (isEnabled && !accessibilityOn) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardBg)
                            .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Accessibility is off",
                            fontWeight = FontWeight.SemiBold,
                            color = onPageColor
                        )
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = onPageColor, contentColor = pageBg)
                        ) { Text("Open Accessibility Settings") }
                    }
                }

            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                singleLine = true,
                placeholder = { Text("Search app") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(8.dp))

                if (loading) {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (filtered.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No apps found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    // SCROLL FIX: before, the LazyColumn was fillMaxSize,
                    // so it did not account for the space above it and
                    // the end of the list was cut off / scrolling was wrong.
                    // Now weight(1f) takes only the remaining space,
                    // and contentPadding keeps the last row from hiding
                    // under the navigation bar. No animation, just smooth scroll.
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered, key = { it.packageName }) { app ->
                            val locked = app.packageName in lockedSet
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .shadow(4.dp, RoundedCornerShape(12.dp))
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(cardBg)
                                    .border(1.dp, cardBorderColor, RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(48.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(thumbBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AppIcon(packageName = app.packageName, label = app.label)
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        app.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        app.packageName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = locked,
                                    onCheckedChange = { checked ->
                                        AppLockManager.setLocked(context, app.packageName, checked)
                                        lockedSet = AppLockManager.getLockedPackages(context)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = pageBg,
                                        checkedTrackColor = onPageColor,
                                        checkedBorderColor = onPageColor,
                                        uncheckedThumbColor = onPageColor.copy(alpha = 0.6f),
                                        uncheckedTrackColor = thumbBg,
                                        uncheckedBorderColor = cardBorderColor
                                    )
                                )
                            }
                        }
                    }
                }
        }
    }
}
