// FILE: app/src/main/java/com/calnyx/app/ui/screens/VaultScreen.kt
// VaultScreen v3 — modern, dark-mode supported.
// Dark-mode fix: the whole screen is wrapped in a Surface with
// contentColor = onBackground, so text is light in dark mode and
// dark in light mode (v2 used a plain Column, so default black
// text stayed black on a dark background — that is what the
// screenshot showed). All cards use tonal Material3 colors
// (primaryContainer / surfaceVariant) that adapt to the theme,
// never a hard-coded black or white.
// Overview: tonal lock header + 2x2 folder cards with counts
// and a photo preview. Inside a folder: photo/video grid,
// document/audio list.
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.calnyx.app.vault.DeleteStatus
import com.calnyx.app.vault.VaultCategory
import com.calnyx.app.vault.VaultManager
import com.calnyx.app.vault.VaultPrefs
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VaultScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // null = folder overview, non-null = inside that folder
    var selectedCategory by remember { mutableStateOf<VaultCategory?>(null) }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var refreshKey by remember { mutableStateOf(0) }
    var pendingImportCategory by remember { mutableStateOf<VaultCategory?>(null) }

    var actionFile by remember { mutableStateOf<File?>(null) }
    var photoViewerFile by remember { mutableStateOf<File?>(null) }
    var renamingFile by remember { mutableStateOf<File?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deletingFile by remember { mutableStateOf<File?>(null) }
    var showSecretDialog by remember { mutableStateOf(false) }
    var secretText by remember { mutableStateOf(VaultPrefs.getSecret(context) ?: "") }
    var pendingDeleteUri by remember { mutableStateOf<Uri?>(null) }

    val folderCounts =
            remember(refreshKey) {
                VaultCategory.entries.associateWith { VaultManager.listFiles(context, it).size }
            }
    val totalFiles = folderCounts.values.sum()

    fun refresh() {
        val cat = selectedCategory
        files = if (cat != null) VaultManager.listFiles(context, cat) else emptyList()
    }

    LaunchedEffect(selectedCategory, refreshKey) { refresh() }

    val deleteConfirmLauncher =
            rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                val uri = pendingDeleteUri
                pendingDeleteUri = null
                if (uri == null) return@rememberLauncherForActivityResult
                scope.launch {
                    val ok =
                            if (result.resultCode == Activity.RESULT_OK) {
                                VaultManager.retryDelete(context, uri)
                            } else false
                    Toast.makeText(
                                    context,
                                    if (ok) "Original file gallery theke muche geche"
                                    else
                                            "Vault-e copy ache, kintu original ekhono phone-e ache — gallery theke nije delete koro",
                                    Toast.LENGTH_LONG
                            )
                            .show()
                }
            }

    val pickerLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                val cat =
                        pendingImportCategory
                                ?: selectedCategory ?: return@rememberLauncherForActivityResult
                scope.launch {
                    val result = VaultManager.importUri(context, uri, cat)
                    if (result.vaultFile == null) {
                        Toast.makeText(context, result.error ?: "Import hoyni", Toast.LENGTH_LONG)
                                .show()
                        return@launch
                    }
                    when (val status = result.deleteStatus) {
                        is DeleteStatus.NeedsConfirmation -> {
                            pendingDeleteUri = uri
                            deleteConfirmLauncher.launch(
                                    IntentSenderRequest.Builder(status.sender).build()
                            )
                            Toast.makeText(
                                            context,
                                            "Vault-e copy hoyeche, gallery theke sorate confirm koro",
                                            Toast.LENGTH_LONG
                                    )
                                    .show()
                        }
                        is DeleteStatus.Failed -> {
                            Toast.makeText(
                                            context,
                                            "Vault-e copy hoyeche, kintu original delete hoyni — gallery theke nije delete koro",
                                            Toast.LENGTH_LONG
                                    )
                                    .show()
                        }
                        else -> {
                            Toast.makeText(
                                            context,
                                            "Gallery theke vault-e move hoyeche",
                                            Toast.LENGTH_SHORT
                                    )
                                    .show()
                        }
                    }
                    refreshKey++
                }
            }

    val category = selectedCategory

    // Dark-mode root: Surface gives every child the theme's
    // onBackground as its default text color.
    Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(12.dp))

            if (category == null) {
                // ---------- OVERVIEW ----------
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                            text = "Hidden Space",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                    )
                    IconButton(
                            onClick = {
                                secretText = VaultPrefs.getSecret(context) ?: ""
                                showSecretDialog = true
                            }
                    ) { Icon(Icons.Default.Settings, contentDescription = "Change secret") }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Tonal lock header — primaryContainer adapts to
                // light and dark theme, no hard-coded colors.
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(28.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                            modifier =
                                    Modifier.size(64.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                    ) {
                        Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = "Locked & Private",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                                text = "Gallery, Files ba onno app-e ei file-gulo dekhabe na.",
                                style = MaterialTheme.typography.bodySmall,
                                color =
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                                alpha = 0.85f
                                        )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                                text =
                                        if (totalFiles == 0) "Ekhono kichu lukano hoyni"
                                        else "$totalFiles ta file lukano ache",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VaultCategory.entries.chunked(2).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowItems.forEach { cat ->
                                val count = folderCounts[cat] ?: 0
                                val initial = cat.title.first().toString()
                                val firstPhoto =
                                        if (cat == VaultCategory.PHOTOS) {
                                            VaultManager.listFiles(context, cat).firstOrNull()
                                        } else null
                                Column(
                                        modifier =
                                                Modifier.weight(1f)
                                                        .clip(RoundedCornerShape(28.dp))
                                                        .background(
                                                                MaterialTheme.colorScheme
                                                                        .surfaceVariant
                                                        )
                                                        .clickable { selectedCategory = cat }
                                                        .padding(16.dp)
                                ) {
                                    if (firstPhoto != null) {
                                        VaultPhotoThumbnail(
                                                file = firstPhoto,
                                                modifier = Modifier.fillMaxWidth().height(84.dp)
                                        )
                                    } else {
                                        Box(
                                                modifier =
                                                        Modifier.size(48.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                        MaterialTheme.colorScheme
                                                                                .primaryContainer
                                                                ),
                                                contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                    initial,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize =
                                                            MaterialTheme.typography
                                                                    .titleMedium
                                                                    .fontSize,
                                                    color =
                                                            MaterialTheme.colorScheme
                                                                    .onPrimaryContainer
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                            cat.title,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                            text = if (count == 0) "Khali" else "$count ta file",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                            text = "Open →",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                            text =
                                    "Kono folder-e dhuke Add chaplei sei dhoroner file vault-e anbe, ar gallery theke soranor chesta korbe.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // ---------- INSIDE A FOLDER ----------
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedCategory = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = category.title,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                                text = if (files.isEmpty()) "Khali" else "${files.size} ta file",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                            onClick = {
                                secretText = VaultPrefs.getSecret(context) ?: ""
                                showSecretDialog = true
                            }
                    ) { Icon(Icons.Default.Settings, contentDescription = "Change secret") }
                }
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                        onClick = {
                            pendingImportCategory = category
                            pickerLauncher.launch(category.pickerMime)
                        },
                        modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add ${category.title.lowercase(Locale.getDefault())}")
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (files.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Ekhane ekhono kichu nei", fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                    text =
                                            "Add chap diye ${category.title.lowercase(Locale.getDefault())} ano.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else if (category == VaultCategory.PHOTOS || category == VaultCategory.VIDEOS) {
                    LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(files.size) { index ->
                            val file = files[index]
                            Box(
                                    modifier =
                                            Modifier.aspectRatio(1f)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(
                                                            MaterialTheme.colorScheme.surfaceVariant
                                                    )
                                                    .clickable { actionFile = file }
                            ) {
                                if (category == VaultCategory.PHOTOS) {
                                    VaultPhotoThumbnail(
                                            file = file,
                                            modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                    ) { Text("VID", fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(files, key = { it.absolutePath }) { file ->
                            Row(
                                    modifier =
                                            Modifier.fillMaxWidth()
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(
                                                            MaterialTheme.colorScheme.surfaceVariant
                                                    )
                                                    .clickable { actionFile = file }
                                                    .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                val short = if (category == VaultCategory.AUDIO) "AUD" else "DOC"
                                Box(
                                        modifier =
                                                Modifier.size(56.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(
                                                                MaterialTheme.colorScheme
                                                                        .primaryContainer
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                            short,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                            text = file.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                            text =
                                                    "${VaultManager.fileSizeString(file)}  •  ${
                                        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                                            .format(Date(file.lastModified()))
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

    // ---- File actions ----
    val file = actionFile
    if (file != null && selectedCategory != null) {
        val cat = selectedCategory!!
        AlertDialog(
                onDismissRequest = { actionFile = null },
                title = { Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                text = {
                    Column {
                        if (cat == VaultCategory.PHOTOS) {
                            VaultPhotoThumbnail(
                                    file = file,
                                    modifier = Modifier.fillMaxWidth().height(220.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(
                                "${VaultManager.fileSizeString(file)} • ${
                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                            .format(Date(file.lastModified()))
                    }"
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                actionFile = null
                                if (cat == VaultCategory.PHOTOS) {
                                    photoViewerFile = file
                                } else if (!VaultManager.openWithExternalViewer(context, file)) {
                                    Toast.makeText(
                                                    context,
                                                    "Kono viewer app pelam na",
                                                    Toast.LENGTH_SHORT
                                            )
                                            .show()
                                }
                            }
                    ) { Text("Open") }
                },
                dismissButton = {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(
                                onClick = {
                                    renamingFile = file
                                    renameText = file.nameWithoutExtension
                                    actionFile = null
                                }
                        ) { Text("Rename") }
                        TextButton(
                                onClick = {
                                    actionFile = null
                                    scope.launch {
                                        val ok = VaultManager.restoreFile(context, file, cat)
                                        Toast.makeText(
                                                        context,
                                                        if (ok) "Gallery-te abar dekhabe"
                                                        else "Restore hoyni, abar try koro",
                                                        Toast.LENGTH_LONG
                                                )
                                                .show()
                                        refreshKey++
                                    }
                                }
                        ) { Text("Restore") }
                        TextButton(
                                onClick = {
                                    deletingFile = file
                                    actionFile = null
                                }
                        ) { Text("Delete") }
                        TextButton(onClick = { actionFile = null }) { Text("Close") }
                    }
                }
        )
    }

    // ---- Full photo viewer ----
    val viewer = photoViewerFile
    if (viewer != null) {
        Dialog(
                onDismissRequest = { photoViewerFile = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                    modifier =
                            Modifier.fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                                    .statusBarsPadding()
                                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { photoViewerFile = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                            text = viewer.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                    )
                }
                VaultPhotoImage(file = viewer, modifier = Modifier.fillMaxSize().weight(1f))
                OutlinedButton(
                        onClick = { photoViewerFile = null },
                        modifier = Modifier.fillMaxWidth()
                ) { Text("Close") }
            }
        }
    }

    // ---- Rename ----
    val toRename = renamingFile
    if (toRename != null) {
        AlertDialog(
                onDismissRequest = { renamingFile = null },
                title = { Text("Rename") },
                text = {
                    OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                if (VaultManager.renameFile(toRename, renameText)) {
                                    Toast.makeText(context, "Rename hoyeche", Toast.LENGTH_SHORT)
                                            .show()
                                } else {
                                    Toast.makeText(
                                                    context,
                                                    "Rename hoyni — nam ta check koro",
                                                    Toast.LENGTH_SHORT
                                            )
                                            .show()
                                }
                                renamingFile = null
                                refreshKey++
                            }
                    ) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { renamingFile = null }) { Text("Cancel") } }
        )
    }

    // ---- Delete confirm ----
    val toDelete = deletingFile
    if (toDelete != null) {
        AlertDialog(
                onDismissRequest = { deletingFile = null },
                title = { Text("Delete?") },
                text = {
                    Text("${toDelete.name} vault theke chirotore muche jabe. Restore kora jabe na.")
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                VaultManager.deleteFile(toDelete)
                                deletingFile = null
                                refreshKey++
                            }
                    ) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { deletingFile = null }) { Text("Cancel") } }
        )
    }

    // ---- Secret change ----
    if (showSecretDialog) {
        AlertDialog(
                onDismissRequest = { showSecretDialog = false },
                title = { Text("Secret hisab bodlao") },
                text = {
                    Column {
                        Text(
                                "Ekhon theke ei hisab-ta type kore = chaplei vault khulbe. " +
                                        "Jemon secret 112233 hole, calculator-e 112233 likhe = chapte hobe."
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                                value = secretText,
                                onValueChange = { secretText = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                        )
                        if (VaultPrefs.isDefault(context)) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                    "Ekhono nijer secret set kora hoyni. Ekhane ba menu-r Hidden Space setup theke nijer ta set kore nio — set korar por theke sudhu setai kaj korbe.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                if (secretText.isBlank()) {
                                    Toast.makeText(
                                                    context,
                                                    "Khali secret rakha jabe na",
                                                    Toast.LENGTH_SHORT
                                            )
                                            .show()
                                } else {
                                    VaultPrefs.setSecret(context, secretText)
                                    showSecretDialog = false
                                    Toast.makeText(
                                                    context,
                                                    "Secret bodle geche",
                                                    Toast.LENGTH_SHORT
                                            )
                                            .show()
                                }
                            }
                    ) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showSecretDialog = false }) { Text("Cancel") }
                }
        )
    }
}

@Composable
private fun VaultPhotoThumbnail(file: File, modifier: Modifier = Modifier) {
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file) { bitmap = withContext(Dispatchers.IO) { decodeSampled(file, 256) } }
    val bmp = bitmap
    if (bmp != null) {
        Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier.clip(RoundedCornerShape(12.dp))
        )
    } else {
        Box(
                modifier =
                        modifier.clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
        ) { Text("IMG", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun VaultPhotoImage(file: File, modifier: Modifier = Modifier) {
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file) { bitmap = withContext(Dispatchers.IO) { decodeSampled(file, 1600) } }
    val bmp = bitmap
    if (bmp != null) {
        Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = modifier
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) { Text("Photo load hoyni") }
    }
}

private fun decodeSampled(file: File, reqSize: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > reqSize || bounds.outHeight / sample > reqSize) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(file.absolutePath, opts)
    } catch (_: Exception) {
        null
    }
}
