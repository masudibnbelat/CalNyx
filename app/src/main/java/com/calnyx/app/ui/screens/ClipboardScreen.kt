// FILE: app/src/main/java/com/calnyx/app/ui/screens/ClipboardScreen.kt
// REPLACE FILE - Clipboard feature (2026-11-06).
// 2026-11-06: header moved up onto the NavGraph 3-dot line
// (spacer removed, 1.0% H top offset so the 13.3% icon centres
// exactly with the 3-dot circle, 15.6% W end clearance kept); image
// thumbnails now decode on Dispatchers.IO, not the main thread.
// Target: app/src/main/java/com/calnyx/app/ui/screens/ClipboardScreen.kt
// First package line: package com.calnyx.app.ui.screens
// First composable line: @Composable fun ClipboardScreen(
// Every copy made anywhere on the phone auto-saves here (see
// ClipboardService + README-CLIPBOARD.md for the honest Android
// 10+ background limit). Newest first, same text twice = two rows.
// DESIGN: black / white only, in BOTH themes, following the app's
// darkTheme toggle (NOT the system theme):
//   light: bg 0xFFF6F4EF, card white, text 0xFF0E0E0E
//   dark:  bg 0xFF0E0E0E, card 0xFF1B1B1B, text 0xFFF6F4EF
// EXPLICIT USER EXCEPTION to the black/white rule: the Edit
// button is GREEN (0xFF2E7D32) and the Delete button is RED
// (0xFFC62828). Everything else stays black/white. No emojis,
// English only.
// DRAWER SWIPE (universal rule): a LEFTWARD drag on the header,
// the auto-delete row, or the list opens the app drawer via
// onOpenDrawer(). The search field is deliberately NOT covered —
// typing and cursor drags must keep working — and vertical list
// scrolling is untouched (the detector only fires on a clearly
// leftward drag past 6.7% of minDim, using detectHorizontalDragGestures;
// NEVER the awaitEachGesture family, which is unresolved in this
// project's Compose version). Rightward drags are not consumed.
// SEARCH is fast by design: Room Flow (observeAll / search with
// LIKE) + LazyColumn with key = item id, so only visible cards
// compose and the list updates the moment a new copy lands.
package com.calnyx.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.calnyx.app.clipboard.ClipboardItem
import com.calnyx.app.clipboard.ClipboardPrefs
import com.calnyx.app.clipboard.ClipboardRepository
import com.calnyx.app.clipboard.ClipboardService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ClipBgLight = Color(0xFFF6F4EF)
private val ClipBgDark = Color(0xFF0E0E0E)
private val ClipCardLight = Color(0xFFFFFFFF)
private val ClipCardDark = Color(0xFF1B1B1B)
private val ClipTextLight = Color(0xFF0E0E0E)
private val ClipTextDark = Color(0xFFF6F4EF)
private val ClipDimLight = Color(0xFF6B6B6B)
private val ClipDimDark = Color(0xFF8A8A8A)
// The user's explicit exceptions: Edit is green, Delete is red.
private val ClipEditGreen = Color(0xFF2E7D32)
private val ClipDeleteRed = Color(0xFFC62828)

@Composable
fun ClipboardScreen(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    // onToggleTheme is kept for the NavGraph call signature; the
    // theme toggle itself lives in the drawer, as on other screens.
    @Suppress("UNUSED_PARAMETER")
    val unusedToggle = onToggleTheme

    val context = LocalContext.current
    val repository = remember(context) { ClipboardRepository(context) }
    val scope = rememberCoroutineScope()

    val pageBg = if (darkTheme) ClipBgDark else ClipBgLight
    val cardBg = if (darkTheme) ClipCardDark else ClipCardLight
    val onPageColor = if (darkTheme) ClipTextDark else ClipTextLight
    val dimColor = if (darkTheme) ClipDimDark else ClipDimLight

    var query by remember { mutableStateOf("") }
    var retentionDays by remember {
        mutableStateOf(ClipboardPrefs.getRetentionDays(context))
    }
    var showRetentionDialog by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<ClipboardItem?>(null) }
    var editDraft by remember { mutableStateOf("") }
    var deleteItem by remember { mutableStateOf<ClipboardItem?>(null) }

    // Fast live list: Room Flow, switched between "all" and LIKE
    // search as the query changes; LazyColumn keys by row id.
    val listFlow = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) repository.observeAll() else repository.search(q)
    }
    val clipItems by listFlow.collectAsState(initial = emptyList())

    // On open: start the listener service, prune expired rows, and
    // catch any copy made while CalNyx was killed (checkNow reads
    // the current clip once, in the foreground).
    LaunchedEffect(Unit) {
        ClipboardService.start(context)
        scope.launch {
            try {
                repository.pruneExpired()
            } catch (_: Exception) {
            }
        }
        ClipboardService.checkNow(context)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val windowW = maxWidth
        val windowH = maxHeight
        val minDim = minOf(windowW, windowH)

        // DRAWER SWIPE: leftward drag opens the drawer. Applied to the
        // header, the auto-delete row and the list — never to the
        // search field below, so typing keeps working.
        val drawerSwipe = Modifier.pointerInput(minDim) {
            val thresholdPx = (minDim * 0.067f).toPx()
            var totalDx = 0f
            var done = false
            detectHorizontalDragGestures(
                onDragStart = { totalDx = 0f; done = false },
                onDragEnd = { totalDx = 0f; done = false },
                onDragCancel = { totalDx = 0f; done = false },
                onHorizontalDrag = { change, dragAmount ->
                    if (done) return@detectHorizontalDragGestures
                    totalDx += dragAmount
                    if (totalDx < -thresholdPx) {
                        done = true
                        onOpenDrawer()
                        change.consume()
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = windowW * 0.044f)
        ) {
            // Header sits on the same line as the NavGraph hamburger
            // (top-right): no spacer above it. The NavGraph top row
            // and the 3-dot circle sit just below the status bar, so
            // the header Row uses the same 1.0% H top offset, and the
            // 15.6% W end padding keeps the title clear of the
            // hamburger button.
            // Header: Clipboard icon in an inverted circle + title.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = windowH * 0.01f, end = windowW * 0.156f).then(drawerSwipe),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(minDim * 0.133f)
                        .clip(CircleShape)
                        .background(onPageColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ContentPaste,
                        contentDescription = "Clipboard",
                        tint = pageBg,
                        modifier = Modifier.size(minDim * 0.072f)
                    )
                }
                Spacer(modifier = Modifier.width(windowW * 0.033f))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Clipboard",
                        fontSize = (minDim * 0.061f).value.sp,
                        fontWeight = FontWeight.Bold,
                        color = onPageColor
                    )
                    Text(
                        text = if (clipItems.isEmpty()) {
                            "Nothing copied yet"
                        } else {
                            "${clipItems.size} item" +
                                (if (clipItems.size == 1) "" else "s") +
                                " • Auto-save on"
                        },
                        fontSize = (minDim * 0.033f).value.sp,
                        color = dimColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(windowH * 0.018f))

            // Search bar — full width, live filtering. NOT under the
            // drawer swipe detector, so typing and cursor drags work.
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = "Search copied text...", color = dimColor) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = dimColor)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = dimColor)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(minDim * 0.044f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = cardBg,
                    unfocusedContainerColor = cardBg,
                    focusedTextColor = onPageColor,
                    unfocusedTextColor = onPageColor,
                    cursorColor = onPageColor,
                    focusedBorderColor = onPageColor,
                    unfocusedBorderColor = dimColor.copy(alpha = 0.4f)
                )
            )

            Spacer(modifier = Modifier.height(windowH * 0.013f))

            // Auto-delete setting: small button showing the current
            // choice; tap opens the radio-list dialog. Changing it
            // prunes immediately (see the dialog below).
            Row(
                modifier = Modifier.fillMaxWidth().then(drawerSwipe),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Auto-delete:",
                    fontSize = (minDim * 0.036f).value.sp,
                    color = dimColor
                )
                Spacer(modifier = Modifier.width(windowW * 0.022f))
                OutlinedButton(onClick = { showRetentionDialog = true }) {
                    Text(
                        text = ClipboardPrefs.labelFor(retentionDays),
                        fontSize = (minDim * 0.036f).value.sp,
                        color = onPageColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(windowH * 0.015f))

            if (clipItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).then(drawerSwipe),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (query.isBlank()) {
                            "Copy anything on your phone —\nit will appear here automatically."
                        } else {
                            "No matches for \"$query\""
                        },
                        fontSize = (minDim * 0.042f).value.sp,
                        color = dimColor,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f).then(drawerSwipe),
                    verticalArrangement = Arrangement.spacedBy(windowH * 0.013f)
                ) {
                    items(clipItems, key = { it.id }) { item ->
                        ClipboardCard(
                            windowW = windowW,
                            windowH = windowH,
                            minDim = minDim,
                            item = item,
                            cardBg = cardBg,
                            onPageColor = onPageColor,
                            dimColor = dimColor,
                            onCopy = {
                                scope.launch {
                                    repository.copyAgain(item)
                                }
                            },
                            onEdit = {
                                editItem = item
                                editDraft = if (item.type == "IMAGE") {
                                    item.note ?: ""
                                } else {
                                    item.text
                                }
                            },
                            onDelete = { deleteItem = item }
                        )
                    }
                    item(key = "clipboard-bottom-space") {
                        Spacer(modifier = Modifier.height(windowH * 0.01f))
                    }
                }
            }
        }

        // ---- Auto-delete dialog: ONLY the six allowed options. ----
        if (showRetentionDialog) {
            AlertDialog(
                onDismissRequest = { showRetentionDialog = false },
                containerColor = cardBg,
                title = {
                    Text(text = "Auto-delete", color = onPageColor, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text(
                            text = "Copies older than this are deleted automatically.",
                            fontSize = (minDim * 0.039f).value.sp,
                            color = dimColor
                        )
                        Spacer(modifier = Modifier.height(windowH * 0.01f))
                        ClipboardPrefs.RETENTION_OPTIONS.forEach { days ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(minDim * 0.033f))
                                    .padding(vertical = windowH * 0.003f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = retentionDays == days,
                                    onClick = {
                                        retentionDays = days
                                        ClipboardPrefs.setRetentionDays(context, days)
                                        // Prune immediately with the new setting.
                                        scope.launch {
                                            try {
                                                repository.pruneExpired()
                                            } catch (_: Exception) {
                                            }
                                        }
                                        showRetentionDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = onPageColor,
                                        unselectedColor = dimColor
                                    )
                                )
                                Text(
                                    text = ClipboardPrefs.labelFor(days),
                                    fontSize = (minDim * 0.042f).value.sp,
                                    color = onPageColor
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showRetentionDialog = false }) {
                        Text(text = "Cancel", color = onPageColor)
                    }
                }
            )
        }

        // ---- Edit dialog: full text editable (text rows) or the image
        // note/caption (image rows). Update saves, Cancel discards. ----
        val editing = editItem
        if (editing != null) {
            AlertDialog(
                onDismissRequest = { editItem = null },
                containerColor = cardBg,
                title = {
                    Text(
                        text = if (editing.type == "IMAGE") "Edit note" else "Edit text",
                        color = onPageColor,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    OutlinedTextField(
                        value = editDraft,
                        onValueChange = { editDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = if (editing.type == "IMAGE") {
                                    "Add a note for this image..."
                                } else {
                                    "Copied text..."
                                },
                                color = dimColor
                            )
                        },
                        singleLine = false,
                        minLines = 3,
                        maxLines = 10,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = cardBg,
                            unfocusedContainerColor = cardBg,
                            focusedTextColor = onPageColor,
                            unfocusedTextColor = onPageColor,
                            cursorColor = onPageColor,
                            focusedBorderColor = onPageColor,
                            unfocusedBorderColor = dimColor.copy(alpha = 0.4f)
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val draft = editDraft.trim()
                            scope.launch {
                                try {
                                    if (editing.type == "IMAGE") {
                                        repository.updateItem(
                                            editing.copy(
                                                note = draft.ifEmpty { null }
                                            )
                                        )
                                    } else if (draft.isNotEmpty()) {
                                        repository.updateItem(editing.copy(text = draft))
                                    }
                                } catch (_: Exception) {
                                }
                            }
                            editItem = null
                        }
                    ) {
                        // Edit action is green, per the user's request.
                        Text(text = "Update", color = ClipEditGreen, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editItem = null }) {
                        Text(text = "Cancel", color = onPageColor)
                    }
                }
            )
        }

        // ---- Delete confirm dialog: Yes/No, Delete is red. ----
        val deleting = deleteItem
        if (deleting != null) {
            AlertDialog(
                onDismissRequest = { deleteItem = null },
                containerColor = cardBg,
                title = {
                    Text(text = "Delete this item?", color = onPageColor, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        text = "This will be permanently deleted from the clipboard.",
                        fontSize = (minDim * 0.039f).value.sp,
                        color = dimColor
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                try {
                                    repository.deleteItem(deleting)
                                } catch (_: Exception) {
                                }
                            }
                            deleteItem = null
                        }
                    ) {
                        Text(text = "Delete", color = ClipDeleteRed, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteItem = null }) {
                        Text(text = "Cancel", color = onPageColor)
                    }
                }
            )
        }
    }
}

@Composable
private fun ClipboardCard(
    windowW: Dp,
    windowH: Dp,
    minDim: Dp,
    item: ClipboardItem,
    cardBg: Color,
    onPageColor: Color,
    dimColor: Color,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(minDim * 0.044f),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = minDim * 0.006f)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(windowW * 0.039f)) {
            // Top line: date + day, time on the right.
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = formatClipDate(item.createdAt),
                    fontSize = (minDim * 0.033f).value.sp,
                    fontWeight = FontWeight.Medium,
                    color = dimColor,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatClipTime(item.createdAt),
                    fontSize = (minDim * 0.033f).value.sp,
                    color = dimColor
                )
            }
            Spacer(modifier = Modifier.height(windowH * 0.01f))

            if (item.type == "IMAGE" && item.imagePath != null) {
                ClipboardThumbnail(path = item.imagePath, dimColor = dimColor, windowW = windowW, windowH = windowH, minDim = minDim)
                Spacer(modifier = Modifier.height(windowH * 0.008f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = "Image",
                        tint = dimColor,
                        modifier = Modifier.size(minDim * 0.044f)
                    )
                    Spacer(modifier = Modifier.width(windowW * 0.017f))
                    Text(
                        text = "Image",
                        fontSize = (minDim * 0.036f).value.sp,
                        fontWeight = FontWeight.Medium,
                        color = dimColor
                    )
                }
                val note = item.note
                if (!note.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(windowH * 0.005f))
                    Text(
                        text = note,
                        fontSize = (minDim * 0.042f).value.sp,
                        color = onPageColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                // 2-line preview of the copied text.
                Text(
                    text = item.text,
                    fontSize = (minDim * 0.042f).value.sp,
                    color = onPageColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(windowH * 0.008f))

            // Copy / Edit (green) / Delete (red).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCopy) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = onPageColor,
                        modifier = Modifier.size(minDim * 0.05f)
                    )
                    Spacer(modifier = Modifier.width(windowW * 0.011f))
                    Text(text = "Copy", color = onPageColor, fontSize = (minDim * 0.036f).value.sp)
                }
                TextButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = ClipEditGreen,
                        modifier = Modifier.size(minDim * 0.05f)
                    )
                    Spacer(modifier = Modifier.width(windowW * 0.011f))
                    Text(text = "Edit", color = ClipEditGreen, fontSize = (minDim * 0.036f).value.sp)
                }
                TextButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = ClipDeleteRed,
                        modifier = Modifier.size(minDim * 0.05f)
                    )
                    Spacer(modifier = Modifier.width(windowW * 0.011f))
                    Text(text = "Delete", color = ClipDeleteRed, fontSize = (minDim * 0.036f).value.sp)
                }
            }
        }
    }
}

@Composable
private fun ClipboardThumbnail(path: String, dimColor: Color, windowW: Dp, windowH: Dp, minDim: Dp) {
    // No Coil (none is added for this feature): decode a small
    // thumbnail with BitmapFactory, downsampled to ~400 px wide.
    // Decoded on Dispatchers.IO: decoding inside remember() ran
    // on the main thread, so every image card froze the list
    // while the screen opened. Text-only lists never had this
    // cost — they already come straight from the Room Flow.
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }
    var loaded by remember(path) { mutableStateOf(false) }
    LaunchedEffect(path) {
        bitmap = null
        loaded = false
        val decoded = withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                if (!file.exists()) return@withContext null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                if (bounds.outWidth <= 0) return@withContext null
                var sample = 1
                while (bounds.outWidth / sample > 400) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(path, opts)
            } catch (_: Exception) {
                null
            }
        }
        bitmap = decoded
        loaded = true
    }
    val shown = bitmap
    if (shown != null) {
        androidx.compose.foundation.Image(
            bitmap = shown.asImageBitmap(),
            contentDescription = "Copied image",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth()
                .height(windowH * 0.175f)
                .clip(RoundedCornerShape(minDim * 0.033f))
        )
    } else if (!loaded) {
        // Still decoding: neutral placeholder, no text yet.
        Box(
            modifier = Modifier.fillMaxWidth()
                .height(windowH * 0.175f)
                .clip(RoundedCornerShape(minDim * 0.033f))
                .background(dimColor.copy(alpha = 0.15f))
        )
    } else {
        Box(
            modifier = Modifier.fillMaxWidth()
                .height(windowH * 0.08f)
                .clip(RoundedCornerShape(minDim * 0.033f))
                .background(dimColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Image unavailable", fontSize = (minDim * 0.036f).value.sp, color = dimColor)
        }
    }
}

private fun formatClipDate(timestamp: Long): String {
    return SimpleDateFormat("dd MMM yyyy • EEEE", Locale.ENGLISH).format(Date(timestamp))
}

private fun formatClipTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(timestamp))
}
