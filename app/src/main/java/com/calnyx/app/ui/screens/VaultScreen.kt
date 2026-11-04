package com.calnyx.app.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private val themeIcons = listOf("⚫", "🌑", "🕳️", "💀", "🔒", "🛡️", "👁️", "🌌")

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
    VaultCategory.PHOTOS    to Pair("🖼️", OffBlack),
    VaultCategory.VIDEOS    to Pair("🎬", OffBlack),
    VaultCategory.DOCUMENTS to Pair("📄", OffBlack),
    VaultCategory.AUDIO     to Pair("🎵", OffBlack),
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
fun VaultScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // MODERN theme: page and cards follow dark/light, but only in
    // off-white / off-black / grey.
    val isDark = isSystemInDarkTheme()
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
    var photoViewerFile by remember { mutableStateOf<File?>(null) }
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

    LaunchedEffect(Unit) {
        selectedIcon = VaultPrefs.getThemeIcon(context) ?: "⚫"
        secretText = VaultPrefs.getSecret(context) ?: ""
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
                if (ok) "Gallery theke muche gece ✓" else "Delete hoyni — gallery theke nije koro",
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
                Toast.makeText(context, result.error ?: "Import hoyni", Toast.LENGTH_LONG).show()
                return@launch
            }
            // Import successful — now ask user about deleting original
            val fileName = result.vaultFile.name
            val status = result.deleteStatus
            if (status != null) {
                pendingDelete = PendingDelete(uri, fileName, status, result.mediaUri ?: uri)
            }
            // FIX2 DEBUG (অস্থায়ী): Failed হলে URI দুটো টোস্টে দেখাবে,
            // যাতে পরের স্ক্রিনশট থেকে বোঝা যায় resolve ঠিক হয়েছে কিনা।
            // Delete ঠিক হয়ে গেলে এই 3 লাইন মুছে দিলেই হবে।
            if (status is DeleteStatus.Failed) {
                // FIX3: আগের DEBUG টোস্টে picker URI এত লম্বা ছিল যে
                // media URI-টা ... দিয়ে কাটা যেত। এখন শুধু ছোট media URI দেখাবে।
                Toast.makeText(
                    context,
                    "DEBUG media=${result.mediaUri}",
                    Toast.LENGTH_LONG
                ).show()
            }
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
                    // Hero Banner — MODERN: pure black gradient, white
                    // text, soft shadow, no colour accents.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(18.dp, RoundedCornerShape(28.dp))
                            .height(168.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF0E0E0E),
                                        Color(0xFF333333)
                                    )
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(28.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(selectedIcon, fontSize = 56.sp)
                            Spacer(Modifier.width(20.dp))
                            Column {
                                Text(
                                    "Black Hole",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    color = Color.White
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    if (totalFiles == 0) "Nothing hidden yet"
                                    else "$totalFiles files secured",
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                Spacer(Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.White.copy(alpha = 0.16f))
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        "PRIVATE  •  HIDDEN",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        letterSpacing = 1.2.sp
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        "FOLDERS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.5.sp
                    )

                    VaultCategory.entries.chunked(2).forEach { rowItems ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowItems.forEach { cat ->
                                val count = folderCounts[cat] ?: 0
                                val (emoji, accentColor) = categoryInfo[cat]!!
                                val firstPhoto = if (cat == VaultCategory.PHOTOS)
                                    VaultManager.listFiles(context, cat).firstOrNull() else null

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .shadow(10.dp, RoundedCornerShape(24.dp))
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(cardBg)
                                        .border(1.dp, cardBorderColor, RoundedCornerShape(24.dp))
                                        .clickable { selectedCategory = cat }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(90.dp)
                                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                            .background(thumbBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (firstPhoto != null) {
                                            VaultPhotoThumbnail(
                                                file = firstPhoto,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Text(emoji, fontSize = 38.sp)
                                        }
                                    }
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (isDark) OffWhite else OffBlack)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                cat.title,
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = onPageColor
                                            )
                                        }
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
                    Spacer(Modifier.height(8.dp))
                }

            } else {
                // ══════════════════════════════════════
                //  INSIDE FOLDER
                // ══════════════════════════════════════
                val (emoji, accentColor) = categoryInfo[category]!!

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
                            .shadow(10.dp, RoundedCornerShape(20.dp))
                            .height(56.dp),
                        shape = RoundedCornerShape(20.dp),
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
                                Text(emoji, fontSize = 56.sp)
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
                                        .clickable { actionFile = f },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (category == VaultCategory.PHOTOS) {
                                        VaultPhotoThumbnail(file = f, modifier = Modifier.fillMaxSize())
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0xFF0D0D1A)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(RoundedCornerShape(50))
                                                    .background(Color.White.copy(alpha = 0.18f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("▶", fontSize = 14.sp, color = Color.White)
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
                                        Text(emoji, fontSize = 24.sp)
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
                Text("Original file delete korbo?", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "\"${pd.fileName}\" vault-e save hoyeche ✓",
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Gallery/Files theke original ta delete korte chao?",
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
                                        else "Delete hoyni — gallery theke nije delete koro",
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
        val (_, accentColor) = categoryInfo[cat]!!
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
                                if (cat == VaultCategory.PHOTOS) {
                                    photoViewerFile = file
                                } else if (!VaultManager.openWithExternalViewer(context, file)) {
                                    Toast.makeText(context, "No viewer app found", Toast.LENGTH_SHORT).show()
                                }
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
                                        if (ok) "Gallery-te restore hoyeche ✓" else "Restore hoyni",
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

    // ── Full photo viewer ──
    val viewer = photoViewerFile
    if (viewer != null) {
        Dialog(
            onDismissRequest = { photoViewerFile = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                VaultPhotoImage(
                    file = viewer,
                    modifier = Modifier.fillMaxSize().align(Alignment.Center)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp)
                        .align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { photoViewerFile = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Text(
                        viewer.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
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
                    "\"${toDelete.name}\" vault theke chirotore muche jabe.",
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

    // ── Settings ──
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            shape = RoundedCornerShape(28.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedIcon, fontSize = 24.sp)
                    Spacer(Modifier.width(10.dp))
                    Text("Settings", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "SECRET CODE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                        OutlinedTextField(
                            value = secretText,
                            onValueChange = { secretText = it },
                            singleLine = true,
                            placeholder = { Text("Enter secret") },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (VaultPrefs.isDefault(context)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⚠️", fontSize = 14.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Default secret still active",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "THEME ICON",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                        themeIcons.chunked(4).forEach { rowIcons ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowIcons.forEach { icon ->
                                    val isSelected = icon == selectedIcon
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .then(
                                                if (isSelected) Modifier.border(
                                                    2.dp,
                                                    MaterialTheme.colorScheme.primary,
                                                    RoundedCornerShape(14.dp)
                                                ) else Modifier
                                            )
                                            .clickable { selectedIcon = icon },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(icon, fontSize = 24.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (secretText.isBlank()) {
                            Toast.makeText(context, "Secret khali rakhা jabe na", Toast.LENGTH_SHORT).show()
                        } else {
                            VaultPrefs.setSecret(context, secretText)
                            VaultPrefs.setThemeIcon(context, selectedIcon)
                            settingsSaveKey++
                            showSettingsDialog = false
                            Toast.makeText(context, "Saved ✓", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("Cancel") }
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
        ) { Text("🖼️", fontSize = 24.sp) }
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

private fun decodeSampled(file: File, reqSize: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > reqSize || bounds.outHeight / sample > reqSize) sample *= 2
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    } catch (_: Exception) { null }
}
