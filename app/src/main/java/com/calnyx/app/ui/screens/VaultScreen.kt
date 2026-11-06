// FILE: app/src/main/java/com/calnyx/app/ui/screens/VaultScreen.kt
// OPENFIX VERSION - replace your old VaultScreen.kt with this file.
// Based on MODERN3 (B/W). Only the open flow changed:
// 1 tap = open externally (was: tap -> sheet -> in-app viewer),
// long-press = action sheet, "Opening..." dialog while loading.
// Same as BACKFIX (system back from a folder goes to the overview
// first), plus the Settings dialog is now modern black/white:
// no theme pink on the Save button or the text field border,
// no extra 'Default secret still active' row, English only.
// Fixes system back from inside a folder: previously it popped
// the whole Vault route to the calculator. Now the first back
// returns to the Black Hole overview, the second back to the
// calculator. Top-bar back already did this; system back now
// does the same.
// Logic is unchanged (encryption, import, restore, rename, delete,
// App Lock navigation). Only the design changed:
//  - No emojis anywhere on this screen. Folder / lock / play
//    icons are Material icons in monochrome circles.
//  - No duplicate "Black Hole" banner title, no PRIVATE pill,
//    no dot bullets, no heavy gradient, no heavy shadows.
//  - Overview is a clean summary card + folder cards + App Lock
//    card, all in off-white / off-black only, same corner and
//    shadow everywhere, so nothing overlaps or sticks out.
//  - All visible text is English only. No Banglish strings.
// You still need: VaultCrypto.kt (new) + VaultManager-ENCRYPTED.kt
// (as VaultManager.kt) + cache-path line in file_paths.xml.
// B/W AUDIT (2026-11-06): app-theme fix — this screen now takes
// darkTheme: Boolean = isSystemInDarkTheme() and uses it instead
// of reading the system theme, so the app's own theme toggle
// controls it. The three Banglish strings are now English
// ("Restored to gallery" / "Restore failed" / the delete-dialog
// text). The one theme-primary text ("saved to vault") is now
// onPageColor. The Delete buttons KEEP colors.error /
// errorContainer red — red marks a delete action, which is
// special. Everything else was already OffWhite / OffBlack /
// CardWhite / CardBlack / Border / ThumbBg or onPageColor /
// onSurfaceVariant; the photo viewer's black background with
// white text is fine. The stored selectedIcon string "⚫" is
// never displayed anywhere, left as-is.
// NO DRAWER SWIPE on this screen: it is a hidden screen —
// NavGraph deliberately hides the drawer and top bar on the
// Vault route, and the vault must stay hidden with no menu
// trace. Do NOT add any onOpenDrawer param or swipe detector here.
package com.calnyx.app.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.calnyx.app.vault.DeleteStatus
import com.calnyx.app.vault.VaultCategory
import com.calnyx.app.vault.VaultManager
import com.calnyx.app.vault.VaultPrefs
import com.calnyx.app.applock.AppLockManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// The old emoji theme-icon picker is removed with the picker below.

// MODERN palette: only off-white / off-black / grey. No purple, pink,
// teal or orange accents anywhere on this screen.
private val OffWhite = Color(0xFFF6F4EF)
private val OffBlack = Color(0xFF0E0E0E)
private val CardWhite = Color(0xFFFFFFFF)
private val CardBlack = Color(0xFF1B1B1B)
private val BorderLight = Color(0xFFE9E6DF)
private val BorderDark = Color(0xFF2B2B2B)
private val ThumbBgLight = Color(0xFFEFEDE8)
private val ThumbBgDark = Color(0xFF262626)

private val categoryInfo = mapOf(
    VaultCategory.PHOTOS    to Icons.Filled.Image,
    VaultCategory.VIDEOS    to Icons.Filled.Videocam,
    VaultCategory.DOCUMENTS to Icons.Filled.Description,
    VaultCategory.AUDIO     to Icons.Filled.MusicNote,
)

// Import এর পরে delete confirm এর জন্য state
private data class PendingDelete(
    val originalUri: Uri,
    val fileName: String,
    val deleteStatus: DeleteStatus,
    // FIX: সিস্টেম পারমিশন এই URI-র জন্য, picker URI-র জন্য না।
    val mediaUri: Uri
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(
    onBack: () -> Unit,
    onOpenAppLock: () -> Unit = {},
    darkTheme: Boolean = isSystemInDarkTheme()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // MODERN theme: page and cards follow dark/light, but only in
    // off-white / off-black / grey.
    val isDark = darkTheme
    val pageBg = if (isDark) OffBlack else OffWhite
    val cardBg = if (isDark) CardBlack else CardWhite
    val cardBorderColor = if (isDark) BorderDark else BorderLight
    val onPageColor = if (isDark) OffWhite else OffBlack
    val thumbBg = if (isDark) ThumbBgDark else ThumbBgLight

    var selectedCategory by remember { mutableStateOf<VaultCategory?>(null) }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var refreshKey by remember { mutableStateOf(0) }
    var pendingImportCategory by remember { mutableStateOf<VaultCategory?>(null) }

    var actionFile by remember { mutableStateOf<File?>(null) }
    // OPENFIX: which file is being opened right now (for the
    // "Opening..." dialog). The old photoViewerFile (in-app viewer)
    // is gone — photos open externally like everything else.
    var openingFile by remember { mutableStateOf<File?>(null) }
    // OPENFIX: one-tap open. scope.launch returns instantly, so the
    // tap never freezes; the suspend open runs on Dispatchers.IO
    // inside VaultOpener. Boolean false = NoViewer or Failed.
    val openFile: (File) -> Unit = { f ->
        scope.launch {
            openingFile = f
            val ok = VaultManager.openWithExternalViewer(context, f)
            openingFile = null
            if (!ok) {
                Toast.makeText(context, "Could not open file", Toast.LENGTH_SHORT).show()
            }
        }
    }
    var renamingFile by remember { mutableStateOf<File?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deletingFile by remember { mutableStateOf<File?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var secretText by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("⚫") }

    // Import এর পরে delete confirm dialog
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
    // System permission dialog (Android 11+ video/audio)
    var pendingSystemDeleteUri by remember { mutableStateOf<Uri?>(null) }

    var settingsSaveKey by remember { mutableStateOf(0) }

    // BACK FIX: folder-er vitore thakle system back (back button /
    // back gesture) age folder theke overview-e firbe, Vault
    // route pop kore calculator-e jabe na. Overview-e thakle
    // BackHandler off, tai tokhon normal back-e calculator-e jabe.
    // Kono dialog khola thakle BackHandler off, tai dialog-i
    // age bondho hobe, folder na.
    BackHandler(
        enabled = selectedCategory != null &&
            actionFile == null &&
            openingFile == null &&
            renamingFile == null &&
            deletingFile == null &&
            !showSettingsDialog &&
            pendingDelete == null
    ) {
        selectedCategory = null
    }

    LaunchedEffect(Unit) {
        selectedIcon = VaultPrefs.getThemeIcon(context) ?: "⚫"
        secretText = VaultPrefs.getSecret(context) ?: ""
        // ENCRYPTED UPDATE: পুরনো plain ফাইল একবার encrypt করো।
        // Already encrypted ফাইল ছোঁয়া হবে না। LaunchedEffect(Unit)
        // শুধু একবার চলে, তাই refreshKey বাড়লেও এটা আবার চলবে না।
        val migrated = VaultManager.migratePlainFilesToEncrypted(context)
        if (migrated > 0) refreshKey++
    }
    LaunchedEffect(settingsSaveKey) {
        if (settingsSaveKey > 0) selectedIcon = VaultPrefs.getThemeIcon(context) ?: "⚫"
    }

    val folderCounts = remember(refreshKey) {
        VaultCategory.entries.associateWith { VaultManager.listFiles(context, it).size }
    }
    val totalFiles = folderCounts.values.sum()

    LaunchedEffect(selectedCategory, refreshKey) {
        val cat = selectedCategory
        files = if (cat != null) VaultManager.listFiles(context, cat) else emptyList()
    }

    // Android 11+ system permission dialog result
    val systemDeleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val uri = pendingSystemDeleteUri ?: return@rememberLauncherForActivityResult
        pendingSystemDeleteUri = null
        scope.launch {
            // FIX2: createDeleteRequest-এর ক্ষেত্রে সিস্টেম নিজেই
            // Confirm চাপলে মুছে দেয়, তাই retry 0 ফেরত দিলেও
            // URI-টা আর না থাকলে সেটাই সফল ধরা হবে।
            val ok = if (result.resultCode == Activity.RESULT_OK) {
                val retried = VaultManager.retryDelete(context, uri)
                retried || !VaultManager.mediaUriExists(context, uri)
            } else false
            Toast.makeText(
                context,
                if (ok) "Deleted from gallery" else "Could not delete — please delete it from the gallery yourself",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // File picker
    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val cat = pendingImportCategory ?: selectedCategory
            ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = VaultManager.importUri(context, uri, cat)
            if (result.vaultFile == null) {
                Toast.makeText(context, result.error ?: "Import failed", Toast.LENGTH_LONG).show()
                return@launch
            }
            // Import successful — now ask user about deleting original
            val fileName = result.vaultFile.name
            val status = result.deleteStatus
            if (status != null) {
                pendingDelete = PendingDelete(uri, fileName, status, result.mediaUri ?: uri)
            }
            // ENCRYPTED UPDATE: DEBUG toast মুছে দেওয়া হয়েছে।
            refreshKey++
        }
    }

    val category = selectedCategory

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = pageBg,
        contentColor = onPageColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            if (category == null) {
                // ══════════════════════════════════════
                //  OVERVIEW
                // ══════════════════════════════════════
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        "Black Hole",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        secretText = VaultPrefs.getSecret(context) ?: ""
                        selectedIcon = VaultPrefs.getThemeIcon(context) ?: "⚫"
                        showSettingsDialog = true
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Summary card — modern: same card style as
                    // everything else, no gradient, no emoji, no pill,
                    // and no duplicate "Black Hole" title (the top bar
                    // already says it). Just the count + a lock icon.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(6.dp, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .background(cardBg)
                            .border(1.dp, cardBorderColor, RoundedCornerShape(20.dp))
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(thumbBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = null,
                                tint = onPageColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (totalFiles == 0) "Nothing hidden yet"
                                else "$totalFiles files secured",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = onPageColor
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Private storage",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        "Folders",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )

                    VaultCategory.entries.chunked(2).forEach { rowItems ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowItems.forEach { cat ->
                                val count = folderCounts[cat] ?: 0
                                val categoryIcon = categoryInfo[cat]!!
                                val firstPhoto = if (cat == VaultCategory.PHOTOS)
                                    VaultManager.listFiles(context, cat).firstOrNull() else null

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .shadow(6.dp, RoundedCornerShape(20.dp))
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(cardBg)
                                        .border(1.dp, cardBorderColor, RoundedCornerShape(20.dp))
                                        .clickable { selectedCategory = cat }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(84.dp)
                                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                                            .background(thumbBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (firstPhoto != null) {
                                            VaultPhotoThumbnail(
                                                file = firstPhoto,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clip(CircleShape)
                                                    .background(cardBg)
                                                    .border(1.dp, cardBorderColor, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    categoryIcon,
                                                    contentDescription = null,
                                                    tint = onPageColor,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                                    ) {
                                        Text(
                                            cat.title,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = onPageColor
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            if (count == 0) "Empty" else "$count files",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Text(
                        "Security",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(6.dp, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .background(cardBg)
                            .border(1.dp, cardBorderColor, RoundedCornerShape(20.dp))
                            .clickable { onOpenAppLock() }
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(thumbBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = onPageColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "App Lock",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = onPageColor
                                )
                                Spacer(Modifier.height(2.dp))
                                val appLockOn = AppLockManager.isEnabled(context)
                                val appLockCount = AppLockManager.getLockedPackages(context).size
                                Text(
                                    if (!appLockOn) "Off"
                                    else if (appLockCount == 0) "On • No apps locked yet"
                                    else if (appLockCount == 1) "On • 1 app locked"
                                    else "On • $appLockCount apps locked",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

            } else {
                // ══════════════════════════════════════
                //  INSIDE FOLDER
                // ══════════════════════════════════════
                val categoryIcon = categoryInfo[category]!!

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    IconButton(onClick = { selectedCategory = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            category.title,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            if (files.isEmpty()) "Empty" else "${files.size} files",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        secretText = VaultPrefs.getSecret(context) ?: ""
                        selectedIcon = VaultPrefs.getThemeIcon(context) ?: "⚫"
                        showSettingsDialog = true
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Button(
                        onClick = {
                            pendingImportCategory = category
                            pickerLauncher.launch(category.pickerMime)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(6.dp, RoundedCornerShape(16.dp))
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            // MODERN: light mode-এ কালো বাটন, dark mode-এ সাদা বাটন
                            containerColor = if (isDark) OffWhite else OffBlack,
                            contentColor = if (isDark) OffBlack else Color.White
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add ${category.title.lowercase(Locale.getDefault())}")
                    }

                    Spacer(Modifier.height(16.dp))

                    if (files.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(cardBg)
                                        .border(1.dp, cardBorderColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        categoryIcon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                                Text(
                                    "Nothing here yet",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "Tap Add to import files",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else if (category == VaultCategory.PHOTOS || category == VaultCategory.VIDEOS) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            items(files.size) { index ->
                                val f = files[index]
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .shadow(6.dp, RoundedCornerShape(16.dp))
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(cardBg)
                                        .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp))
                                        .combinedClickable(
                                            onClick = { openFile(f) },
                                            onLongClick = { actionFile = f }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (category == VaultCategory.PHOTOS) {
                                        VaultPhotoThumbnail(file = f, modifier = Modifier.fillMaxSize())
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(OffBlack),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(RoundedCornerShape(50))
                                                    .background(Color.White.copy(alpha = 0.18f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Filled.PlayArrow,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(files, key = { it.absolutePath }) { f ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .shadow(6.dp, RoundedCornerShape(16.dp))
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(cardBg)
                                        .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp))
                                        .combinedClickable(
                                            onClick = { openFile(f) },
                                            onLongClick = { actionFile = f }
                                        )
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(thumbBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            categoryIcon,
                                            contentDescription = null,
                                            tint = onPageColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            f.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "${VaultManager.fileSizeString(f)}  •  ${
                                                SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                                    .format(Date(f.lastModified()))
                                            }",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════
    //  DELETE CONFIRM DIALOG (after import)
    // ══════════════════════════════════════
    val pd = pendingDelete
    if (pd != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            shape = RoundedCornerShape(24.dp),
            title = {
                Text("Delete original file?", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "\"${pd.fileName}\" saved to vault",
                        fontWeight = FontWeight.Medium,
                        color = onPageColor
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Delete the original from Gallery / Files as well?",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val status = pd.deleteStatus
                        // FIX: আগের বাগ এখানে ছিল — pd.originalUri (picker URI)
                        // দিয়ে retry হতো, তাই সিস্টেম পারমিশন কাজ করত না।
                        // এখন আসল MediaStore URI দিয়ে retry হবে।
                        val uri = pd.mediaUri
                        pendingDelete = null
                        when (status) {
                            is DeleteStatus.Deleted -> {
                                // Already deleted during import
                                Toast.makeText(context, "Deleted ✓", Toast.LENGTH_SHORT).show()
                            }
                            is DeleteStatus.NeedsConfirmation -> {
                                // Android 11+ — launch system permission dialog
                                pendingSystemDeleteUri = uri
                                systemDeleteLauncher.launch(
                                    IntentSenderRequest.Builder(status.sender).build()
                                )
                            }
                            is DeleteStatus.Failed -> {
                                // Try one more time with resolved URI
                                scope.launch {
                                    val ok = VaultManager.retryDelete(context, uri)
                                    Toast.makeText(
                                        context,
                                        if (ok) "Deleted ✓"
                                        else "Could not delete — please delete it from the gallery yourself",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete original") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Keep original")
                }
            }
        )
    }

    // ── File action sheet ──
    val file = actionFile
    if (file != null && selectedCategory != null) {
        val cat = selectedCategory!!
        // categoryIcon is not needed in the action sheet
        AlertDialog(
            onDismissRequest = { actionFile = null },
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (cat == VaultCategory.PHOTOS) {
                        VaultPhotoThumbnail(
                            file = file,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                    }
                    Text(
                        "${VaultManager.fileSizeString(file)}  •  ${
                            SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                .format(Date(file.lastModified()))
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(
                            onClick = {
                                actionFile = null
                                openFile(file)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Open") }

                        FilledTonalButton(
                            onClick = {
                                renamingFile = file
                                renameText = file.nameWithoutExtension
                                actionFile = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Rename") }

                        FilledTonalButton(
                            onClick = {
                                actionFile = null
                                scope.launch {
                                    val ok = VaultManager.restoreFile(context, file, cat)
                                    Toast.makeText(
                                        context,
                                        if (ok) "Restored to gallery" else "Restore failed",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    refreshKey++
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Restore to Gallery") }

                        Button(
                            onClick = { deletingFile = file; actionFile = null },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) { Text("Delete permanently") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { actionFile = null }) { Text("Cancel") } }
        )
    }

    // ── OPENFIX: "Opening..." — shows INSTANTLY on tap, while the
    // decrypt/copy runs on a background thread. No progress = the
    // user thinks the tap did nothing. No dismiss: it closes itself
    // when openFile finishes.
    val opening = openingFile
    if (opening != null) {
        AlertDialog(
            onDismissRequest = {},
            shape = RoundedCornerShape(24.dp),
            title = { Text("Opening...") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = onPageColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        opening.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    // ── Rename ──
    val toRename = renamingFile
    if (toRename != null) {
        AlertDialog(
            onDismissRequest = { renamingFile = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (VaultManager.renameFile(toRename, renameText)) {
                        Toast.makeText(context, "Renamed ✓", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                    }
                    renamingFile = null
                    refreshKey++
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renamingFile = null }) { Text("Cancel") } }
        )
    }

    // ── Delete from vault confirm ──
    val toDelete = deletingFile
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { deletingFile = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Delete permanently?") },
            text = {
                Text(
                    "\"${toDelete.name}\" will be permanently deleted from the vault.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        VaultManager.deleteFile(toDelete)
                        deletingFile = null
                        refreshKey++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletingFile = null }) { Text("Cancel") } }
        )
    }

    // ── Settings ── MODERN: black/white only. The default
    // AlertDialog/Button colours come from the app theme
    // (pink), so every colour here is set explicitly.
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp)
                            .clip(CircleShape)
                            .background(thumbBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = onPageColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Settings", fontWeight = FontWeight.Bold, color = onPageColor)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Secret code",
                        style = MaterialTheme.typography.labelSmall,
                        color = onPageColor.copy(alpha = 0.6f),
                        letterSpacing = 1.sp
                    )
                    OutlinedTextField(
                        value = secretText,
                        onValueChange = { secretText = it },
                        singleLine = true,
                        placeholder = { Text("Enter secret") },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = onPageColor,
                            unfocusedBorderColor = cardBorderColor,
                            focusedTextColor = onPageColor,
                            unfocusedTextColor = onPageColor,
                            cursorColor = onPageColor,
                            focusedPlaceholderColor = onPageColor.copy(alpha = 0.4f),
                            unfocusedPlaceholderColor = onPageColor.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (secretText.isBlank()) {
                            Toast.makeText(context, "Secret cannot be empty", Toast.LENGTH_SHORT).show()
                        } else {
                            VaultPrefs.setSecret(context, secretText)
                            VaultPrefs.setThemeIcon(context, selectedIcon)
                            settingsSaveKey++
                            showSettingsDialog = false
                            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = onPageColor,
                        contentColor = pageBg
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSettingsDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = onPageColor)
                ) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun VaultPhotoThumbnail(file: File, modifier: Modifier = Modifier) {
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file) {
        bitmap = withContext(Dispatchers.IO) { decodeSampled(file, 256) }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun VaultPhotoImage(file: File, modifier: Modifier = Modifier) {
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file) {
        bitmap = withContext(Dispatchers.IO) { decodeSampled(file, 1600) }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(40.dp))
        }
    }
}

// ENCRYPTED UPDATE: আগের BitmapFactory.decodeFile() এখানে আর নেই।
// Encrypted ফাইল সরাসরি decode করলে ছবি আসত না, তাই এখন
// VaultManager মেমোরিতে ডিক্রিপ্ট করে Bitmap বানায়।
private fun decodeSampled(file: File, reqSize: Int): Bitmap? {
    return VaultManager.decodeSampledBitmap(file, reqSize)
}
