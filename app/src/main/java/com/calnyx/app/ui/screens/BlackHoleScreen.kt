// FILE: app/src/main/java/com/calnyx/app/ui/screens/BlackHoleScreen.kt
// % -BASED RESPONSIVE MODERN VERSION (2026-11-07) - REPLACE BlackHoleScreen.kt.
// Based on the OPENFIX version (Black Hole names: com.calnyx.app.blackhole,
// BlackHoleManager / BlackHoleCategory / BlackHolePrefs / BlackHoleOpener /
// BlackHolePickerGuard / DeleteStatus). All logic is UNCHANGED, only the
// layout/design changed:
//  1. REMOVED the top "N files secured / Private storage" summary card
//     entirely (user: "মার্ক করা কার্ড দরকার নাই") - the screen now
//     starts with the Folders section. totalFiles is gone with it
//     (folderCounts, the per-folder counts, still works).
//  2. MODERN: one corner system (card 5.6% / small 4.4% / button 3.3% /
//     dialog 6.7% of minDim), one shadow (minDim x 1.7%), cards distinct
//     from the page bg via cardBg + 1dp hairline border + shadow.
//     Black/white only, red only for Delete. No emojis, English only.
//  3. % EVERYWHERE: root BoxWithConstraints (windowW / windowH / minDim /
//     isLandscape / isMiniWindow / isSmallWindow). ALL layout sizes are %
//     of window / minDim / cell - paddings, margins, gaps, card heights,
//     icon sizes, fonts, corner radii. Folder grid 2 cols portrait /
//     3-4 landscape; photo grid 3 portrait / 4-5 landscape. Overview
//     scrolls, so floating / split / mini / short windows never clip.
//     No fixed dp/sp size left except: size-class thresholds (dp
//     comparisons, not sizes), 0.dp, and 1.dp hairline borders.
//     (Material3 defaults - AlertDialog internal padding, IconButton
//     touch target, HorizontalDivider 1dp - are framework values, not
//     code values.)
// OPENFIX flow kept as-is: tap = options modal (Open / Download /
// Rename / Restore / Delete); video thumbnails (real frames);
// "Importing... %" bar with .tmp writes; plain storage, instant open;
// migrateEncryptedFilesToPlain() one-time migration. BACKFIX kept:
// system back from a folder goes to the overview first, second back
// to the calculator.
// NO DRAWER SWIPE on this screen: it is a hidden screen - NavGraph
// deliberately hides the drawer and top bar on the BlackHole route,
// and the blackhole must stay hidden with no menu trace. Do NOT add
// any onOpenDrawer param or swipe detector here.
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

    // % LAYOUT - root BoxWithConstraints: every size below is % of
    // the WINDOW (windowW/windowH) or of its smaller side (minDim),
    // so floating / split / mini / landscape all stay unbroken.
    // (dp values here are only size-class THRESHOLDS - comparisons,
    // not sizes.)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val windowW = maxWidth
        val windowH = maxHeight
        val minDim = minOf(windowW, windowH)
        val isLandscape = windowW > windowH
        val isMiniWindow = windowW < 320.dp || windowH < 480.dp
        val isSmallWindow = windowW < 360.dp || windowH < 620.dp

        // Corners - % of minDim (20dp / 16dp / 12dp / 24dp at 360dp)
        val cardCorner = minDim * 0.056f
        val cardCornerSm = minDim * 0.044f
        val btnCorner = minDim * 0.033f
        val dialogCorner = minDim * 0.067f
        val thumbCorner = minDim * 0.039f
        // One shadow - 6dp at 360dp
        val shadowElev = minDim * 0.017f
        // Page padding / gaps - % of window
        val padPageW = if (isSmallWindow) windowW * 0.033f else windowW * 0.044f
        val padTopStartW = windowW * 0.011f
        val padTopEndW = windowW * 0.022f
        val padTopH = windowH * 0.005f
        val gapSectionH = windowH * 0.018f
        val gapRowW = windowW * 0.033f
        val gapCardH = windowH * 0.011f
        val gapSmallH = windowH * 0.013f
        val gapTinyH = windowH * 0.005f
        val gapXsH = windowH * 0.008f
        val gapGrid = windowW * 0.008f
        val gapListH = windowH * 0.011f
        val gapBtnH = windowH * 0.021f
        val gap2H = windowH * 0.003f
        val gapIconW = windowW * 0.039f
        val gapBtnW = windowW * 0.022f
        // Grids adapt to the window
        val folderCols = if (isLandscape) (if (isMiniWindow) 3 else 4) else 2
        val photoCols = if (isLandscape) (if (isMiniWindow) 4 else 5) else 3
        // Fonts - % of minDim
        val titleFont = (minDim.value * 0.056f).sp
        val folderNameFont = (minDim.value * 0.044f).sp
        val bodyFont = (minDim.value * 0.039f).sp
        val smallFont = (minDim.value * 0.036f).sp
        val labelFont = (minDim.value * 0.033f).sp
        val emptyTitleFont = (minDim.value * 0.047f).sp
        val labelSpacing = (minDim.value * 0.0014f).sp
        // Icons - % of minDim
        val iconBar = minDim * 0.067f
        val iconSm = minDim * 0.05f
        val iconMd = minDim * 0.061f
        val iconLg = minDim * 0.083f
        // Card sizes - % of minDim / window
        val folderThumbH = minDim * 0.233f
        val folderIconCircle = minDim * 0.144f
        val appLockCircle = minDim * 0.133f
        val emptyCircle = minDim * 0.20f
        val listThumb = minDim * 0.144f
        val settingsCircle = minDim * 0.10f
        val addBtnH = windowH * 0.074f
        val sheetPhotoH = windowH * 0.237f
        val folderTextPadW = minDim * 0.039f
        val folderTextPadH = windowH * 0.016f
        val appLockPad = minDim * 0.044f
        val listRowPad = minDim * 0.039f

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
                    //  OVERVIEW (summary card REMOVED - the screen
                    //  starts with Folders now)
                    // ══════════════════════════════════════
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = padTopStartW, end = padTopEndW, top = padTopH, bottom = padTopH)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.size(iconBar)
                            )
                        }
                        Text(
                            "Black Hole",
                            fontWeight = FontWeight.Bold,
                            fontSize = titleFont,
                            color = onPageColor,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            secretText = BlackHolePrefs.getSecret(context) ?: ""
                            selectedIcon = BlackHolePrefs.getThemeIcon(context) ?: "⚫"
                            showSettingsDialog = true
                        }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                modifier = Modifier.size(iconBar)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = padPageW),
                        verticalArrangement = Arrangement.spacedBy(gapSectionH)
                    ) {
                        Text(
                            "Folders",
                            fontSize = labelFont,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = labelSpacing
                        )

                        BlackHoleCategory.entries.chunked(folderCols).forEach { rowItems ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(gapRowW),
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
                                            .shadow(shadowElev, RoundedCornerShape(cardCorner))
                                            .clip(RoundedCornerShape(cardCorner))
                                            .background(cardBg)
                                            .border(1.dp, cardBorderColor, RoundedCornerShape(cardCorner))
                                            .clickable { selectedCategory = cat }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(folderThumbH)
                                                .clip(RoundedCornerShape(topStart = cardCorner, topEnd = cardCorner))
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
                                                        .size(folderIconCircle)
                                                        .clip(CircleShape)
                                                        .background(cardBg)
                                                        .border(1.dp, cardBorderColor, CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        categoryIcon,
                                                        contentDescription = null,
                                                        tint = onPageColor,
                                                        modifier = Modifier.size(iconBar)
                                                    )
                                                }
                                            }
                                        }
                                        Column(
                                            modifier = Modifier.padding(horizontal = folderTextPadW, vertical = folderTextPadH)
                                        ) {
                                            Text(
                                                cat.title,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = folderNameFont,
                                                color = onPageColor
                                            )
                                            Spacer(Modifier.height(gap2H))
                                            Text(
                                                if (count == 0) "Empty" else "$count files",
                                                fontSize = smallFont,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                if (rowItems.size < folderCols) {
                                    repeat(folderCols - rowItems.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                        Text(
                            "Security",
                            fontSize = labelFont,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = labelSpacing
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(shadowElev, RoundedCornerShape(cardCorner))
                                .clip(RoundedCornerShape(cardCorner))
                                .background(cardBg)
                                .border(1.dp, cardBorderColor, RoundedCornerShape(cardCorner))
                                .clickable { onOpenAppLock() }
                                .padding(appLockPad)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(appLockCircle)
                                        .clip(CircleShape)
                                        .background(thumbBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = onPageColor,
                                        modifier = Modifier.size(iconMd)
                                    )
                                }
                                Spacer(Modifier.width(gapIconW))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "App Lock",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = folderNameFont,
                                        color = onPageColor
                                    )
                                    Spacer(Modifier.height(gap2H))
                                    val appLockOn = AppLockManager.isEnabled(context)
                                    val appLockCount = AppLockManager.getLockedPackages(context).size
                                    Text(
                                        if (!appLockOn) "Off"
                                        else if (appLockCount == 0) "On • No apps locked yet"
                                        else if (appLockCount == 1) "On • 1 app locked"
                                        else "On • $appLockCount apps locked",
                                        fontSize = smallFont,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(iconBar)
                                )
                            }
                        }
                        Spacer(Modifier.height(gapCardH))
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
                            .padding(start = padTopStartW, end = padTopEndW, top = padTopH, bottom = padTopH)
                    ) {
                        IconButton(onClick = { selectedCategory = null }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.size(iconBar)
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                category.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = titleFont,
                                color = onPageColor
                            )
                            Text(
                                if (files.isEmpty()) "Empty" else "${files.size} files",
                                fontSize = smallFont,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            secretText = BlackHolePrefs.getSecret(context) ?: ""
                            selectedIcon = BlackHolePrefs.getThemeIcon(context) ?: "⚫"
                            showSettingsDialog = true
                        }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                modifier = Modifier.size(iconBar)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = padPageW)
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
                                .shadow(shadowElev, RoundedCornerShape(cardCornerSm))
                                .height(addBtnH),
                            shape = RoundedCornerShape(cardCornerSm),
                            colors = ButtonDefaults.buttonColors(
                                // MODERN: light mode-এ কালো বাটন, dark mode-এ সাদা বাটন
                                containerColor = if (isDark) OffWhite else OffBlack,
                                contentColor = if (isDark) OffBlack else Color.White
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(iconSm))
                            Spacer(Modifier.width(gapBtnW))
                            Text("Add ${category.title.lowercase(Locale.getDefault())}", fontSize = bodyFont)
                        }

                        Spacer(Modifier.height(gapBtnH))

                        if (files.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(gapSmallH)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(emptyCircle)
                                            .clip(CircleShape)
                                            .background(cardBg)
                                            .border(1.dp, cardBorderColor, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            categoryIcon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(iconLg)
                                        )
                                    }
                                    Text(
                                        "Nothing here yet",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = emptyTitleFont,
                                        color = onPageColor
                                    )
                                    Text(
                                        "Tap Add to import files",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = smallFont,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else if (category == BlackHoleCategory.PHOTOS || category == BlackHoleCategory.VIDEOS) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(photoCols),
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(gapGrid),
                                verticalArrangement = Arrangement.spacedBy(gapGrid)
                            ) {
                                items(files.size) { index ->
                                    val f = files[index]
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .shadow(shadowElev, RoundedCornerShape(cardCornerSm))
                                            .clip(RoundedCornerShape(cardCornerSm))
                                            .background(cardBg)
                                            .border(1.dp, cardBorderColor, RoundedCornerShape(cardCornerSm))
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
                                verticalArrangement = Arrangement.spacedBy(gapListH)
                            ) {
                                items(files, key = { it.absolutePath }) { f ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .shadow(shadowElev, RoundedCornerShape(cardCornerSm))
                                            .clip(RoundedCornerShape(cardCornerSm))
                                            .background(cardBg)
                                            .border(1.dp, cardBorderColor, RoundedCornerShape(cardCornerSm))
                                            .clickable { actionFile = f }
                                            .padding(listRowPad),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(listThumb)
                                                .clip(RoundedCornerShape(thumbCorner))
                                                .background(thumbBg),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                categoryIcon,
                                                contentDescription = null,
                                                tint = onPageColor,
                                                modifier = Modifier.size(iconBar)
                                            )
                                        }
                                        Spacer(Modifier.width(gapIconW))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                f.name,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = folderNameFont,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(Modifier.height(gap2H))
                                            Text(
                                                "${BlackHoleManager.fileSizeString(f)}  •  ${
                                                    SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                                        .format(Date(f.lastModified()))
                                                }",
                                                fontSize = smallFont,
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
                shape = RoundedCornerShape(dialogCorner),
                title = {
                    Text("Delete original file?", fontWeight = FontWeight.Bold, fontSize = folderNameFont, color = onPageColor)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(gapCardH)) {
                        Text(
                            "\"${pd.fileName}\" saved to Black Hole",
                            fontWeight = FontWeight.Medium,
                            fontSize = bodyFont,
                            color = onPageColor
                        )
                        Spacer(Modifier.height(gapTinyH))
                        Text(
                            "Delete the original from Gallery / Files as well?",
                            fontSize = bodyFont,
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
                        shape = RoundedCornerShape(btnCorner),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Delete original", fontSize = bodyFont) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text("Keep original", fontSize = bodyFont)
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
                shape = RoundedCornerShape(dialogCorner),
                title = {
                    Text(
                        file.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium, fontSize = folderNameFont, color = onPageColor
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(gapSmallH)) {
                        if (cat == BlackHoleCategory.PHOTOS) {
                            BlackHolePhotoThumbnail(
                                file = file,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(sheetPhotoH)
                                    .clip(RoundedCornerShape(cardCornerSm))
                            )
                        }
                        Text(
                            "${BlackHoleManager.fileSizeString(file)}  •  ${
                                SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                    .format(Date(file.lastModified()))
                            }",
                            fontSize = smallFont,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(gapTinyH)) {
                            FilledTonalButton(
                                onClick = {
                                    actionFile = null
                                    openFile(file)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(btnCorner)
                            ) { Text("Open", fontSize = bodyFont) }

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
                                shape = RoundedCornerShape(btnCorner)
                            ) { Text("Download", fontSize = bodyFont) }

                            FilledTonalButton(
                                onClick = {
                                    renamingFile = file
                                    renameText = file.nameWithoutExtension
                                    actionFile = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(btnCorner)
                            ) { Text("Rename", fontSize = bodyFont) }

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
                                shape = RoundedCornerShape(btnCorner)
                            ) { Text("Restore to Gallery", fontSize = bodyFont) }

                            Button(
                                onClick = { deletingFile = file; actionFile = null },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(btnCorner),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) { Text("Delete permanently", fontSize = bodyFont) }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { actionFile = null }) { Text("Cancel", fontSize = bodyFont) } }
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
                shape = RoundedCornerShape(dialogCorner),
                title = { Text("Opening...", fontSize = folderNameFont, color = onPageColor) },
                text = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = onPageColor,
                                modifier = Modifier.size(iconBar)
                            )
                            Spacer(Modifier.width(gapIconW))
                            Text(
                                opening.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = bodyFont,
                                color = onPageColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        val op = openingProgress
                        if (op != null) {
                            Spacer(Modifier.height(windowH * 0.016f))
                            LinearProgressIndicator(
                                progress = op,
                                modifier = Modifier.fillMaxWidth(),
                                color = onPageColor
                            )
                            Spacer(Modifier.height(gapXsH))
                            Text(
                                "${(op * 100).toInt()}%",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = smallFont
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
                    }) { Text("Cancel", fontSize = bodyFont) }
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
                shape = RoundedCornerShape(dialogCorner),
                title = { Text("Importing...", fontSize = folderNameFont, color = onPageColor) },
                text = {
                    Column {
                        LinearProgressIndicator(
                            progress = imp,
                            modifier = Modifier.fillMaxWidth(),
                            color = onPageColor
                        )
                        Spacer(Modifier.height(gapCardH))
                        Text(
                            "${(imp * 100).toInt()}% — please wait, do not close the app",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = smallFont
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
                shape = RoundedCornerShape(dialogCorner),
                title = { Text("Rename", fontSize = folderNameFont, color = onPageColor) },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        shape = RoundedCornerShape(btnCorner),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = folderNameFont),
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
                    }) { Text("Save", fontSize = bodyFont) }
                },
                dismissButton = { TextButton(onClick = { renamingFile = null }) { Text("Cancel", fontSize = bodyFont) } }
            )
        }

        // ── Delete from Black Hole confirm ──
        val toDelete = deletingFile
        if (toDelete != null) {
            AlertDialog(
                onDismissRequest = { deletingFile = null },
                shape = RoundedCornerShape(dialogCorner),
                title = { Text("Delete permanently?", fontSize = folderNameFont, color = onPageColor) },
                text = {
                    Text(
                        "\"${toDelete.name}\" will be permanently deleted from Black Hole.",
                        fontSize = bodyFont,
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
                        shape = RoundedCornerShape(btnCorner)
                    ) { Text("Delete", fontSize = bodyFont) }
                },
                dismissButton = { TextButton(onClick = { deletingFile = null }) { Text("Cancel", fontSize = bodyFont) } }
            )
        }

        // ── Settings ── MODERN: black/white only. The default
        // AlertDialog/Button colours come from the app theme
        // (pink), so every colour here is set explicitly.
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                containerColor = cardBg,
                shape = RoundedCornerShape(cardCorner),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(settingsCircle)
                                .clip(CircleShape)
                                .background(thumbBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = null,
                                tint = onPageColor,
                                modifier = Modifier.size(iconSm)
                            )
                        }
                        Spacer(Modifier.width(windowW * 0.028f))
                        Text("Settings", fontWeight = FontWeight.Bold, fontSize = titleFont, color = onPageColor)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(gapSmallH)) {
                        Text(
                            "Secret code",
                            fontSize = labelFont,
                            color = onPageColor.copy(alpha = 0.6f),
                            letterSpacing = (minDim.value * 0.0028f).sp
                        )
                        OutlinedTextField(
                            value = secretText,
                            onValueChange = { secretText = it },
                            singleLine = true,
                            placeholder = { Text("Enter secret", fontSize = bodyFont) },
                            shape = RoundedCornerShape(cardCornerSm),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = folderNameFont),
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
                        shape = RoundedCornerShape(cardCornerSm)
                    ) { Text("Save", fontSize = bodyFont) }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showSettingsDialog = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = onPageColor)
                    ) { Text("Cancel", fontSize = bodyFont) }
                }
            )
        }
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
        // % icon: 22% of this thumbnail's own smaller side.
        BoxWithConstraints(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val cellMin = minOf(maxWidth, maxHeight)
            Icon(
                Icons.Filled.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(cellMin * 0.22f)
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
    // % sizes: play badge 29% of this thumbnail's smaller side,
    // play icon 16% of it. RoundedCornerShape(50) is already a
    // percent (50% = fully round).
    BoxWithConstraints(
        modifier = modifier.background(OffBlack),
        contentAlignment = Alignment.Center
    ) {
        val cellMin = minOf(maxWidth, maxHeight)
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
                .size(cellMin * 0.29f)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(cellMin * 0.16f)
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
        // % spinner: 11% of this viewer's own smaller side.
        BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
            val cellMin = minOf(maxWidth, maxHeight)
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(cellMin * 0.111f))
        }
    }
}

// ENCRYPTED UPDATE: আগের BitmapFactory.decodeFile() এখানে আর নেই।
// Encrypted ফাইল সরাসরি decode করলে ছবি আসত না, তাই এখন
// BlackHoleManager মেমোরিতে ডিক্রিপ্ট করে Bitmap বানায়।
private fun decodeSampled(file: File, reqSize: Int): Bitmap? {
    return BlackHoleManager.decodeSampledBitmap(file, reqSize)
}
