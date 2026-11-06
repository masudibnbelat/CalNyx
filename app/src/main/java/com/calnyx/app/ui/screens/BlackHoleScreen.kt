// FILE: app/src/main/java/com/calnyx/app/ui/screens/BlackHoleScreen.kt
// OPENFIX VERSION - replace your old BlackHoleScreen.kt with this file.
// Based on MODERN3 (B/W). Only the open flow changed:
// Tap = options modal (Open / Download / Rename / Restore / Delete).
// The moment the modal opens, the preview is pre-warmed in the
// background, so tapping Open usually shows the player chooser at
// once, with no loading. "Opening..." shows only if the preview
// is not ready yet. OPENFIX 2 (2026-11-06).
// OPENFIX 3 (2026-11-06): video thumbnails (real frames, not black
// boxes); import shows a real "Importing... 47%" bar and writes to
// a .tmp file first, so one upload can never appear as two files
// with one opening to nothing; "Opening..." shows a real % bar too
// (decrypt runs on a 64KB buffer now, so big videos are faster).
// OPENFIX 4 (2026-11-06): ENCRYPTION REMOVED by the user's choice.
// Files are stored PLAIN in app internal storage (still hidden from
// gallery/Files, still deleted from the gallery on import, still
// protected by the app lock) — so opening is INSTANT: no decrypt,
// no preview copy, no loading, no background job, no Cancel needed
// for plain files. Old encrypted files are converted to plain once
// by migrateEncryptedFilesToPlain() below; until then they open
// through the old decrypt path. BlackHoleCrypto.kt stays in the project
// ONLY for that one-time migration.
// Same as BACKFIX (system back from a folder goes to the overview
// first), plus the Settings dialog is now modern black/white:
// no theme pink on the Save button or the text field border,
// no extra 'Default secret still active' row, English only.
// Fixes system back from inside a folder: previously it popped
// the whole BlackHole route to the calculator. Now the first back
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
// You still need: BlackHoleCrypto.kt (new) + BlackHoleManager-ENCRYPTED.kt
// (as BlackHoleManager.kt) + cache-path line in file_paths.xml.
// B/W AUDIT (2026-11-06): app-theme fix — this screen now takes
// darkTheme: Boolean = isSystemInDarkTheme() and uses it instead
// of reading the system theme, so the app's own theme toggle
// controls it. The three Banglish strings are now English
// ("Restored to gallery" / "Restore failed" / the delete-dialog
// text). The one theme-primary text ("saved to Black Hole") is now
// onPageColor. The Delete buttons KEEP colors.error /
// errorContainer red — red marks a delete action, which is
// special. Everything else was already OffWhite / OffBlack /
// CardWhite / CardBlack / Border / ThumbBg or onPageColor /
// onSurfaceVariant; the photo viewer's black background with
// white text is fine. The stored selectedIcon string "⚫" is
// never displayed anywhere, left as-is.
// NO DRAWER SWIPE on this screen: it is a hidden screen —
// NavGraph deliberately hides the drawer and top bar on the
// BlackHole route, and the blackhole must stay hidden with no menu
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
import androidx.compose.foundation.clickable
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
import com.calnyx.app.blackhole.DeleteStatus
import com.calnyx.app.blackhole.BlackHoleCategory
import com.calnyx.app.blackhole.BlackHoleManager
import com.calnyx.app.blackhole.BlackHoleOpener
import com.calnyx.app.blackhole.BlackHolePickerGuard
import com.calnyx.app.blackhole.BlackHolePrefs
import com.calnyx.app.applock.AppLockManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    BlackHoleCategory.PHOTOS    to Icons.Filled.Image,
    BlackHoleCategory.VIDEOS    to Icons.Filled.Videocam,
    BlackHoleCategory.DOCUMENTS to Icons.Filled.Description,
    BlackHoleCategory.AUDIO     to Icons.Filled.MusicNote,
)

// Import এর পরে delete confirm এর জন্য state
private data class PendingDelete(
    val originalUri: Uri,
    val fileName: String,
    val deleteStatus: DeleteStatus,
    // FIX: সিস্টেম পারমিশন এই URI-র জন্য, picker URI-র জন্য না।
    val mediaUri: Uri
)

@Composable
fun BlackHoleScreen(
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

    var selectedCategory by remember { mutableStateOf<BlackHoleCategory?>(null) }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var refreshKey by remember { mutableStateOf(0) }
    var pendingImportCategory by remember { mutableStateOf<BlackHoleCategory?>(null) }

    var actionFile by remember { mutableStateOf<File?>(null) }
    // OPENFIX: which file is being opened right now (for the
    // "Opening..." dialog). The old photoViewerFile (in-app viewer)
    // is gone — photos open externally like everything else.
    var openingFile by remember { mutableStateOf<File?>(null) }
    // OPENFIX 3: real progress 0f..1f for the "Opening..." bar
    // (null = no open running). Set from the IO thread by the
    // decrypt callback; Compose state is thread-safe.
    var openingProgress by remember { mutableStateOf<Float?>(null) }
    // OPENFIX 4: the running open job, so the dialog's Cancel button
    // can stop it. (Plain files open instantly, so Cancel only ever
    // matters for an old still-encrypted file.)
    var openJob by remember { mutableStateOf<Job?>(null) }
    // OPENFIX 3: real progress 0f..1f for the "Importing..." bar
    // (null = no import running). While non-null the Add button is
    // disabled and a second picker result is ignored, so one tap
    // can never start two imports of the same video.
    var importingProgress by remember { mutableStateOf<Float?>(null) }
    // OPENFIX: open from the modal. scope.launch returns instantly,
    // so the tap never freezes; the suspend open runs on
    // Dispatchers.IO inside BlackHoleOpener. Boolean false = NoViewer
    // or Failed. The preview was usually pre-warmed already (see
    // the LaunchedEffect below), so the chooser appears at once.
    val openFile: (File) -> Unit = { f ->
        openJob = scope.launch {
            openingFile = f
            openingProgress = 0f
            val ok = BlackHoleManager.openWithExternalViewer(context, f) { p ->
                openingProgress = p
            }
            openingFile = null
            openingProgress = null
            openJob = null
            if (!ok) {
                Toast.makeText(context, "Could not open file", Toast.LENGTH_SHORT).show()
            }
        }
    }
    // OPENFIX 4: the OPENFIX 2 pre-warm is gone — it existed only to
    // hide the decrypt time, and with encryption removed there is
    // nothing to pre-warm. A plain file opens instantly on tap.
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
    // back gesture) age folder theke overview-e firbe, BlackHole
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
        selectedIcon = BlackHolePrefs.getThemeIcon(context) ?: "⚫"
        secretText = BlackHolePrefs.getSecret(context) ?: ""
        // OPENFIX 4: encryption removed (user choice 2026-11-06).
        // Old encrypted files are converted to plain ONCE here, so
        // they open instantly afterwards. Already-plain files are
        // untouched. LaunchedEffect(Unit) runs only once.
        val migrated = BlackHoleManager.migrateEncryptedFilesToPlain(context)
        if (migrated > 0) refreshKey++
    }
    LaunchedEffect(settingsSaveKey) {
        if (settingsSaveKey > 0) selectedIcon = BlackHolePrefs.getThemeIcon(context) ?: "⚫"
    }

    val folderCounts = remember(refreshKey) {
        BlackHoleCategory.entries.associateWith { BlackHoleManager.listFiles(context, it).size }
    }
    val totalFiles = folderCounts.values.sum()

    LaunchedEffect(selectedCategory, refreshKey) {
        val cat = selectedCategory
        files = if (cat != null) BlackHoleManager.listFiles(context, cat) else emptyList()
    }

    // Android 11+ system permission dialog result
    val systemDeleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // IMPORT FIX: the system delete-confirm dialog is over —
        // the privacy close may fire again from now on.
        BlackHolePickerGuard.end()
        val uri = pendingSystemDeleteUri ?: return@rememberLauncherForActivityResult
        pendingSystemDeleteUri = null
        scope.launch {
            // FIX2: createDeleteRequest-এর ক্ষেত্রে সিস্টেম নিজেই
            // Confirm চাপলে মুছে দেয়, তাই retry 0 ফেরত দিলেও
            // URI-টা আর না থাকলে সেটাই সফল ধরা হবে।
            val ok = if (result.resultCode == Activity.RESULT_OK) {
                val retried = BlackHoleManager.retryDelete(context, uri)
                retried || !BlackHoleManager.mediaUriExists(context, uri)
            } else false
            Toast.makeText(
                context,
                if (ok) "Deleted from gallery" else "Could not delete — please delete it from the gallery yourself",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // File picker
    // OPENFIX 3: an import already running ignores a second picker
    // result, and the progress bar shows the real % while it runs.
    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        // IMPORT FIX: the picker is closed (picked or cancelled) —
        // clear the guard FIRST, before anything can return early.
        BlackHolePickerGuard.end()
        if (uri == null) return@rememberLauncherForActivityResult
        if (importingProgress != null) return@rememberLauncherForActivityResult
        val cat = pendingImportCategory ?: selectedCategory
            ?: return@rememberLauncherForActivityResult
        scope.launch {
            importingProgress = 0f
            try {
                val result = BlackHoleManager.importUri(context, uri, cat) { p ->
                    importingProgress = p
                }
                if (result.blackHoleFile == null) {
                    Toast.makeText(context, result.error ?: "Import failed", Toast.LENGTH_LONG).show()
                    return@launch
                }
                // Import successful — now ask user about deleting original
                val fileName = result.blackHoleFile.name
                val status = result.deleteStatus
                if (status != null) {
                    pendingDelete = PendingDelete(uri, fileName, status, result.mediaUri ?: uri)
                }
                // ENCRYPTED UPDATE: DEBUG toast মুছে দেওয়া হয়েছে।
                refreshKey++
            } finally {
                importingProgress = null
            }
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
                        secretText = BlackHolePrefs.getSecret(context) ?: ""
                        selectedIcon = BlackHolePrefs.getThemeIcon(context) ?: "⚫"
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

                    BlackHoleCategory.entries.chunked(2).forEach { rowItems ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowItems.forEach { cat ->
                                val count = folderCounts[cat] ?: 0
                                val categoryIcon = categoryInfo[cat]!!
                                val firstPhoto = if (cat == BlackHoleCategory.PHOTOS)
                                    BlackHoleManager.listFiles(context, cat).firstOrNull() else null

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
                                            BlackHolePhotoThumbnail(
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
                        secretText = BlackHolePrefs.getSecret(context) ?: ""
                        selectedIcon = BlackHolePrefs.getThemeIcon(context) ?: "⚫"
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
                        // OPENFIX 3: disabled while an import runs —
                        // no double-tap = no duplicate file.
                        enabled = importingProgress == null,
                        onClick = {
                            pendingImportCategory = category
                            // IMPORT FIX: the picker is a separate
                            // activity — CalNyx goes ON_STOP while it
                            // is open. Set the guard so NavGraph's
                            // privacy close does NOT pop the blackhole.
                            BlackHolePickerGuard.begin()
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
                    } else if (category == BlackHoleCategory.PHOTOS || category == BlackHoleCategory.VIDEOS) {
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
                                        .clickable { actionFile = f },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (category == BlackHoleCategory.PHOTOS) {
                                        BlackHolePhotoThumbnail(file = f, modifier = Modifier.fillMaxSize())
                                    } else {
                                        // OPENFIX 3: real video frame,
                                        // not a black box. While the
                                        // frame is being made (first
                                        // time only, background), the
                                        // old black box + play icon
                                        // shows, so the grid never
                                        // looks broken.
                                        BlackHoleVideoThumbnail(file = f, modifier = Modifier.fillMaxSize())
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
                                        .clickable { actionFile = f }
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
                                            "${BlackHoleManager.fileSizeString(f)}  •  ${
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
                        "\"${pd.fileName}\" saved to Black Hole",
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
                                // IMPORT FIX: same guard — the system
                                // confirm dialog must not close the blackhole.
                                BlackHolePickerGuard.begin()
                                systemDeleteLauncher.launch(
                                    IntentSenderRequest.Builder(status.sender).build()
                                )
                            }
                            is DeleteStatus.Failed -> {
                                // Try one more time with resolved URI
                                scope.launch {
                                    val ok = BlackHoleManager.retryDelete(context, uri)
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
                    if (cat == BlackHoleCategory.PHOTOS) {
                        BlackHolePhotoThumbnail(
                            file = file,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                    }
                    Text(
                        "${BlackHoleManager.fileSizeString(file)}  •  ${
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
                                actionFile = null
                                scope.launch {
                                    val ok = BlackHoleManager.downloadFile(context, file, cat)
                                    Toast.makeText(
                                        context,
                                        if (ok) "Downloaded" else "Download failed",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Download") }

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
                                    val ok = BlackHoleManager.restoreFile(context, file, cat)
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
    // OPENFIX 3: shows the REAL % (linear bar + "47%") while the
    // preview is being prepared; the spinner only shows before the
    // first progress report arrives.
    val opening = openingFile
    if (opening != null) {
        AlertDialog(
            onDismissRequest = {},
            shape = RoundedCornerShape(24.dp),
            title = { Text("Opening...") },
            text = {
                Column {
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
                    val op = openingProgress
                    if (op != null) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = op,
                            modifier = Modifier.fillMaxWidth(),
                            color = onPageColor
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${(op * 100).toInt()}%",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {},
            // OPENFIX 4: Cancel button. Stops the open job (only ever
            // needed for an old still-encrypted file — plain files
            // open instantly, so the dialog barely appears for them).
            dismissButton = {
                TextButton(onClick = {
                    openJob?.cancel()
                    openJob = null
                    openingFile = null
                    openingProgress = null
                }) { Text("Cancel") }
            }
        )
    }

    // ── OPENFIX 3: "Importing..." — the old code imported with no
    // feedback at all, so the user could not tell whether the upload
    // was running, and tapped Add again — creating the duplicate
    // file. Now the real % shows, and the Add button is disabled
    // until the import finishes.
    val imp = importingProgress
    if (imp != null) {
        AlertDialog(
            onDismissRequest = {},
            shape = RoundedCornerShape(24.dp),
            title = { Text("Importing...") },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = imp,
                        modifier = Modifier.fillMaxWidth(),
                        color = onPageColor
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${(imp * 100).toInt()}% — please wait, do not close the app",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
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
                    if (BlackHoleManager.renameFile(toRename, renameText)) {
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

    // ── Delete from Black Hole confirm ──
    val toDelete = deletingFile
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { deletingFile = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Delete permanently?") },
            text = {
                Text(
                    "\"${toDelete.name}\" will be permanently deleted from Black Hole.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        BlackHoleManager.deleteFile(toDelete)
                        // OPENFIX 3: drop the cached preview + video
                        // frame too, so a deleted file leaves nothing
                        // decrypted behind in cache.
                        BlackHoleOpener.dropCaches(context, toDelete)
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
                            BlackHolePrefs.setSecret(context, secretText)
                            BlackHolePrefs.setThemeIcon(context, selectedIcon)
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
private fun BlackHolePhotoThumbnail(file: File, modifier: Modifier = Modifier) {
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

// OPENFIX 3: video thumbnail — a real frame, not a black box.
// The frame is made on Dispatchers.IO (decrypt + extract, cached
// as a JPEG after the first time), so the grid never freezes.
// While the frame is being made, the old black box + play icon
// shows. The play icon stays on top of the frame too.
@Composable
private fun BlackHoleVideoThumbnail(file: File, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file) {
        bitmap = withContext(Dispatchers.IO) {
            BlackHoleOpener.videoThumbnail(context, file)
        }
    }
    val bmp = bitmap
    Box(
        modifier = modifier.background(OffBlack),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
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

@Composable
private fun BlackHolePhotoImage(file: File, modifier: Modifier = Modifier) {
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
// BlackHoleManager মেমোরিতে ডিক্রিপ্ট করে Bitmap বানায়।
private fun decodeSampled(file: File, reqSize: Int): Bitmap? {
    return BlackHoleManager.decodeSampledBitmap(file, reqSize)
}
