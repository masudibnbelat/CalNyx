// FILE: app/src/main/java/com/calnyx/app/ui/screens/ClipboardScreen.kt
// REPLACE FILE - Clipboard fix + Material 3 redesign (2026-11-20).
// Target: app/src/main/java/com/calnyx/app/ui/screens/ClipboardScreen.kt
// First package line: package com.calnyx.app.ui.screens
// First composable line: @Composable fun ClipboardScreen(
// WHAT CHANGED (2026-11-20 redesign, behaviour kept):
// 1. NO STATUS CARD any more. At the very top, in the header row
//    itself, sits one small lamp: a BLINKING dot, no text.
//    GREEN blinking while CalNyx's AccessibilityService (App Lock)
//    is enabled, RED blinking while it is not OR the last test
//    capture failed. Tap the lamp while red -> opens Accessibility
//    Settings so it can be turned on. Tap while green -> runs a
//    Test capture (copies a test line, reads it back after 0.9 s
//    - the row must appear at the top of the list; if it does
//    not, the lamp turns red). A newly saved row clears a red
//    lamp by itself. The state refreshes on every resume, so
//    coming back from Settings updates the lamp.
// 2. AUTO-DELETE is now a small chip in the SAME row as the
//    All / Text / Images filter chips (it shows the current value,
//    e.g. "30 days"). Tap it for the radio-list dialog. Filter +
//    search still combine; the list is still the Room Flow, keyed
//    by row id, newest first.
// 3. TAP A CARD to open a Material 3 bottom sheet with the FULL
//    text (selectable - drag to select any part of it) or the full
//    image, plus Copy / Edit / Delete right there in the sheet.
//    Cards still show the 2-line preview in the list.
// 4. COLOURS FROM THE THEME (2026-11-20): no hardcoded colours
//    in this file any more. Everything reads MaterialTheme.
//    colorScheme, whose values live in Color.kt - page bg,
//    cards, dialogs/sheet, text, dim text, plus the green / red
//    exceptions (CalNyxGreen / CalNyxRed, also in Color.kt).
//    Edit the values in Color.kt and this screen follows.
//    Header geometry unchanged: 1.0% H top offset, 15.6% W end
//    clearance, so the title centres with the NavGraph 3-dot
//    circle exactly as before.
// 5. DRAWER SWIPE (universal rule, unchanged): leftward drag on the
//    header, filter row or list opens the drawer. The search field
//    is NOT covered, typing works.
// 6. KEPT AS BEFORE: Room Flow + LIKE search (wildcards escaped
//    now), auto-delete ONLY 1/2/7/30/90/365 days (default 30),
//    cards with date + day + 2-line preview, Copy / Edit / Delete
//    per card, re-Copy inserts a new row on top, image thumbnails
//    decode on Dispatchers.IO (max 1280 px JPEG 85 in storage).
// 7. SMOOTH SCROLL (2026-11-20): image thumbnails live in a
//    screen-level LruCache (20 entries) - first show decodes once,
//    scrolling back reuses the bitmap with no decode. Thumbnails
//    use RGB_565 (half the memory, no alpha needed). The list also
//    sets contentType per item (text vs image) so Compose recycles
//    the right card type while scrolling.
// FIX NOTE: opening this screen still starts ClipboardService and
// calls checkNow() - a copy made while CalNyx was killed appears
// on open. Background copies now go through ClipboardCaptureActivity
// (see ClipboardCapture.kt) instead of a background read that
// Android 10+ always returns null for.
package com.calnyx.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.Settings
import android.util.LruCache
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.calnyx.app.clipboard.ClipboardCapture
import com.calnyx.app.clipboard.ClipboardItem
import com.calnyx.app.clipboard.ClipboardPrefs
import com.calnyx.app.clipboard.ClipboardRepository
import com.calnyx.app.clipboard.ClipboardService
import com.calnyx.app.ui.theme.CalNyxGreen
import com.calnyx.app.ui.theme.CalNyxRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// COLOURS (2026-11-20): this screen takes ALL of its colours from
// the theme (Color.kt) through MaterialTheme.colorScheme - nothing
// is hardcoded here any more. Change a value in Color.kt and this
// screen changes with it. That is the single source of truth.
//   pageBg   = colorScheme.background
//   cardBg   = colorScheme.surfaceContainer
//   dialogBg = colorScheme.surfaceContainerHigh
//   text     = colorScheme.onBackground
//   dim text = colorScheme.onSurfaceVariant
// The green / red exceptions also live in Color.kt (CalNyxGreen /
// CalNyxRed) and are imported below.

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

    // Thumbnail cache (2026-11-20, scroll fix): without it, every
    // image item re-decoded its bitmap from disk each time it
    // scrolled back into view - that disk decode on scroll was the
    // lag. The cache lives for the whole screen, so a decoded
    // thumbnail is reused instantly. 20 entries max, oldest evicted.
    val thumbCache = remember { LruCache<String, Bitmap>(20) }

    // All colours come from the theme (Color.kt) - see the note at
    // the top of this file. darkTheme is kept only because the
    // NavGraph passes it; the theme already knows the mode, so the
    // colours below do not branch on it any more.
    val colorScheme = MaterialTheme.colorScheme
    val pageBg = colorScheme.background
    val cardBg = colorScheme.surfaceContainer
    val dialogBg = colorScheme.surfaceContainerHigh
    val onPageColor = colorScheme.onBackground
    val dimColor = colorScheme.onSurfaceVariant

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }
    var captureOn by remember { mutableStateOf(false) }
    // LAMP (2026-11-20): true when the last test capture failed
    // to save - the lamp turns red until a save proves capture
    // works again (see the test tap + the size effect below).
    var captureFailed by remember { mutableStateOf(false) }
    var retentionDays by remember {
        mutableStateOf(ClipboardPrefs.getRetentionDays(context))
    }
    var showRetentionDialog by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<ClipboardItem?>(null) }
    var editDraft by remember { mutableStateOf("") }
    var deleteItem by remember { mutableStateOf<ClipboardItem?>(null) }
    // Card tapped -> full-text bottom sheet.
    var viewItem by remember { mutableStateOf<ClipboardItem?>(null) }

    // The lamp blinks in BOTH states - green while capture is
    // armed, red while it is off or the last test failed.
    val blinkAlpha by rememberInfiniteTransition(label = "captureLight").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "captureBlink"
    )

    // Fast live list: Room Flow, switched between "all" and LIKE
    // search as the query changes; LazyColumn keys by row id.
    val listFlow = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) repository.observeAll() else repository.search(q)
    }
    val clipItems by listFlow.collectAsState(initial = emptyList())
    val shownItems = when (filter) {
        "TEXT" -> clipItems.filter { it.type == "TEXT" }
        "IMAGE" -> clipItems.filter { it.type == "IMAGE" }
        else -> clipItems
    }

    // SCROLL TO TOP ON ENTRY (2026-11-20): every time this screen is
    // opened, the list glides smoothly back to the very first item
    // (index 0, the newest copy) - wherever it was left. The list
    // state is saveable, so without this the list reopened at the
    // old scroll position. Runs once per entry, only after the
    // items have loaded; new copies arriving while the screen is
    // already open do NOT trigger it.
    val listState = rememberLazyListState()
    var scrolledToTop by remember { mutableStateOf(false) }
    LaunchedEffect(clipItems.isNotEmpty()) {
        if (clipItems.isNotEmpty() && !scrolledToTop) {
            listState.animateScrollToItem(
                index = 0,
                animationSpec = tween(durationMillis = 500)
            )
            scrolledToTop = true
        }
    }

    // On open: start the listener service, prune expired rows, check
    // the capture status, and catch any copy made while CalNyx was
    // killed (checkNow reads the current clip once, in the foreground).
    LaunchedEffect(Unit) {
        ClipboardService.start(context)
        captureOn = ClipboardCapture.isAccessibilityCaptureOn(context)
        scope.launch {
            try {
                repository.pruneExpired()
            } catch (_: Exception) {
            }
        }
        ClipboardService.checkNow(context)
    }

    // A newly saved row proves capture works again: clear a red
    // lamp left by a failed test, without another tap.
    LaunchedEffect(clipItems.size) {
        if (captureFailed && clipItems.isNotEmpty()) captureFailed = false
    }

    // The capture button must be right when the user comes back
    // from Accessibility Settings (or from any other app): refresh
    // the state on every resume, not just on first open.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                captureOn = ClipboardCapture.isAccessibilityCaptureOn(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val windowW = maxWidth
        val windowH = maxHeight
        val minDim = minOf(windowW, windowH)

        // DRAWER SWIPE: leftward drag opens the drawer. Applied to
        // the header, status card, filter row, auto-delete row and
        // the list - never to the search field below, so typing
        // keeps working. Vertical scrolling is untouched: the
        // detector only fires on a clearly leftward drag past 6.7%
        // of minDim, using detectHorizontalDragGestures; NEVER the
        // awaitEachGesture family, which is unresolved in this
        // project's Compose version. Rightward drags are not consumed.
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

        val chipColors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = onPageColor,
            selectedLabelColor = pageBg,
            containerColor = cardBg,
            labelColor = onPageColor
        )

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
                                (if (clipItems.size == 1) "" else "s")
                        },
                        fontSize = (minDim * 0.033f).value.sp,
                        color = dimColor
                    )
                }
                // Capture light, top line, next to the title -
                // just a lamp, no text. GREEN blinking while
                // background capture is armed, RED blinking while
                // it is off OR the last test capture failed.
                // Tap while red -> Accessibility Settings. Tap
                // while green -> Test capture (copies a test line,
                // reads it back - the row must appear at the top;
                // if it does not, the lamp turns red).
                val lightColor = if (captureOn && !captureFailed) CalNyxGreen else CalNyxRed
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            if (!captureOn) {
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: Exception) {
                                }
                            } else {
                                val testText = "CalNyx test " +
                                    SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)
                                        .format(Date(System.currentTimeMillis()))
                                try {
                                    val manager = context.getSystemService(
                                        android.content.Context.CLIPBOARD_SERVICE
                                    ) as? ClipboardManager
                                    manager?.setPrimaryClip(
                                        ClipData.newPlainText("CalNyx test", testText)
                                    )
                                } catch (_: Exception) {
                                }
                                Toast.makeText(
                                    context,
                                    "Test text copied - saving...",
                                    Toast.LENGTH_SHORT
                                ).show()
                                scope.launch {
                                    delay(900L)
                                    ClipboardService.checkNow(context)
                                    // Did the test row arrive? If not,
                                    // capture is broken - red lamp.
                                    delay(1500L)
                                    if (clipItems.none { it.text == testText }) {
                                        captureFailed = true
                                        Toast.makeText(
                                            context,
                                            "Test failed - capture is not working.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        captureFailed = false
                                    }
                                }
                            }
                        }
                        .padding(minDim * 0.022f),
                    contentAlignment = Alignment.Center
                ) {
                    // Soft glow halo behind the lamp.
                    Box(
                        modifier = Modifier
                            .size(minDim * 0.078f)
                            .clip(CircleShape)
                            .background(lightColor.copy(alpha = 0.25f))
                    )
                    // The lamp itself - blinking.
                    Box(
                        modifier = Modifier
                            .size(minDim * 0.039f)
                            .clip(CircleShape)
                            .background(lightColor)
                            .alpha(blinkAlpha)
                    )
                }
            }

            Spacer(modifier = Modifier.height(windowH * 0.016f))

            // Search bar - full width, live filtering. NOT under the
            // drawer swipe detector, so typing and cursor drags work.
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth()
                    .height(windowH * 0.065f),
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

            Spacer(modifier = Modifier.height(windowH * 0.011f))

            // Filter chips + auto-delete, one row: All / Text /
            // Images, then a small chip showing the current
            // auto-delete value. Tap it for the radio-list dialog.
            Row(
                modifier = Modifier.fillMaxWidth().then(drawerSwipe),
                horizontalArrangement = Arrangement.spacedBy(windowW * 0.015f)
            ) {
                FilterChip(
                    selected = filter == "ALL",
                    onClick = { filter = "ALL" },
                    label = {
                        Text(text = "All", fontSize = (minDim * 0.036f).value.sp)
                    },
                    colors = chipColors
                )
                FilterChip(
                    selected = filter == "TEXT",
                    onClick = { filter = "TEXT" },
                    label = {
                        Text(text = "Text", fontSize = (minDim * 0.036f).value.sp)
                    },
                    colors = chipColors
                )
                FilterChip(
                    selected = filter == "IMAGE",
                    onClick = { filter = "IMAGE" },
                    label = {
                        Text(text = "Images", fontSize = (minDim * 0.036f).value.sp)
                    },
                    colors = chipColors
                )
                FilterChip(
                    selected = false,
                    onClick = { showRetentionDialog = true },
                    label = {
                        Text(
                            text = ClipboardPrefs.labelFor(retentionDays),
                            fontSize = (minDim * 0.036f).value.sp
                        )
                    },
                    colors = chipColors
                )
            }

            Spacer(modifier = Modifier.height(windowH * 0.011f))

            if (shownItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).then(drawerSwipe),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.size(minDim * 0.2f)
                                .clip(CircleShape)
                                .background(cardBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ContentPaste,
                                contentDescription = null,
                                tint = dimColor,
                                modifier = Modifier.size(minDim * 0.1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(windowH * 0.014f))
                        Text(
                            text = if (query.isNotBlank()) {
                                "No matches for \"$query\""
                            } else if (filter == "IMAGE") {
                                "No copied images yet"
                            } else if (filter == "TEXT") {
                                "No copied text yet"
                            } else {
                                "Copy anything on your phone —\nit will appear here automatically."
                            },
                            fontSize = (minDim * 0.042f).value.sp,
                            color = dimColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f).then(drawerSwipe),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(windowH * 0.013f)
                ) {
                    items(shownItems, key = { it.id }, contentType = { it.type }) { item ->
                        ClipboardCard(
                            windowW = windowW,
                            windowH = windowH,
                            minDim = minDim,
                            item = item,
                            cardBg = cardBg,
                            onPageColor = onPageColor,
                            dimColor = dimColor,
                            thumbCache = thumbCache,
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
                            onDelete = { deleteItem = item },
                            onView = { viewItem = item }
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
                containerColor = dialogBg,
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
                containerColor = dialogBg,
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
                        Text(text = "Update", color = CalNyxGreen, fontWeight = FontWeight.SemiBold)
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
                containerColor = dialogBg,
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
                        Text(text = "Delete", color = CalNyxRed, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteItem = null }) {
                        Text(text = "Cancel", color = onPageColor)
                    }
                }
            )
        }

        // ---- Full-text modal: tap a card to read the whole copy. ----
        val viewing = viewItem
        if (viewing != null) {
            FullTextSheet(
                windowW = windowW,
                windowH = windowH,
                minDim = minDim,
                item = viewing,
                dialogBg = dialogBg,
                pageBg = pageBg,
                onPageColor = onPageColor,
                dimColor = dimColor,
                onClose = { viewItem = null },
                onCopy = {
                    scope.launch {
                        repository.copyAgain(viewing)
                    }
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                },
                onEdit = {
                    viewItem = null
                    editItem = viewing
                    editDraft = if (viewing.type == "IMAGE") {
                        viewing.note ?: ""
                    } else {
                        viewing.text
                    }
                },
                onDelete = {
                    viewItem = null
                    deleteItem = viewing
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
    thumbCache: LruCache<String, Bitmap>,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onView: () -> Unit
) {
    Card(
        onClick = onView,
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
                ClipboardThumbnail(path = item.imagePath, thumbCache = thumbCache, dimColor = dimColor, windowW = windowW, windowH = windowH, minDim = minDim)
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
                        tint = CalNyxGreen,
                        modifier = Modifier.size(minDim * 0.05f)
                    )
                    Spacer(modifier = Modifier.width(windowW * 0.011f))
                    Text(text = "Edit", color = CalNyxGreen, fontSize = (minDim * 0.036f).value.sp)
                }
                TextButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = CalNyxRed,
                        modifier = Modifier.size(minDim * 0.05f)
                    )
                    Spacer(modifier = Modifier.width(windowW * 0.011f))
                    Text(text = "Delete", color = CalNyxRed, fontSize = (minDim * 0.036f).value.sp)
                }
            }
        }
    }
}

@Composable
private fun ClipboardThumbnail(
    path: String,
    thumbCache: LruCache<String, Bitmap>,
    dimColor: Color,
    windowW: Dp,
    windowH: Dp,
    minDim: Dp
) {
    // No Coil (none is added for this feature): decode a small
    // thumbnail with BitmapFactory, downsampled to ~400 px wide.
    // SCROLL FIX (2026-11-20): the old code had NO cache - every
    // image item decoded its bitmap from disk again each time it
    // scrolled back into view, and reset to a placeholder first.
    // That disk decode on scroll was the lag. Now the bitmap lives
    // in the screen-level LruCache: first show decodes once on
    // Dispatchers.IO, every later show (scroll up/down) reuses it
    // instantly with no decode at all. RGB_565 halves the memory
    // (thumbnails need no alpha), so decoding and drawing are
    // faster too. Text-only lists never had this cost - they
    // already come straight from the Room Flow.
    var bitmap by remember(path) { mutableStateOf(thumbCache.get(path)) }
    var loaded by remember(path) { mutableStateOf(bitmap != null) }
    LaunchedEffect(path) {
        if (bitmap != null) return@LaunchedEffect
        val decoded = withContext(Dispatchers.IO) {
            thumbCache.get(path) ?: try {
                val file = File(path)
                if (!file.exists()) return@withContext null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                if (bounds.outWidth <= 0) return@withContext null
                var sample = 1
                while (bounds.outWidth / sample > 400) sample *= 2
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeFile(path, opts)?.also { thumbCache.put(path, it) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullTextSheet(
    windowW: Dp,
    windowH: Dp,
    minDim: Dp,
    item: ClipboardItem,
    dialogBg: Color,
    pageBg: Color,
    onPageColor: Color,
    dimColor: Color,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    // skipPartiallyExpanded: the sheet opens fully, so a long text
    // is readable at once instead of stopping half-way.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = dialogBg,
        contentColor = onPageColor,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = windowW * 0.044f)
        ) {
            // Title + close.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (item.type == "IMAGE") "Copied image" else "Copied text",
                    fontSize = (minDim * 0.05f).value.sp,
                    fontWeight = FontWeight.Bold,
                    color = onPageColor,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClose) {
                    Text(text = "Close", color = dimColor, fontSize = (minDim * 0.036f).value.sp)
                }
            }
            Spacer(modifier = Modifier.height(windowH * 0.004f))
            Text(
                text = formatClipDate(item.createdAt) + " • " + formatClipTime(item.createdAt),
                fontSize = (minDim * 0.033f).value.sp,
                color = dimColor
            )
            Spacer(modifier = Modifier.height(windowH * 0.014f))

            // Body scrolls, so a very long copy still fits the sheet.
            Column(
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (item.type == "IMAGE" && item.imagePath != null) {
                    ClipboardFullImage(
                        path = item.imagePath,
                        dimColor = dimColor,
                        windowW = windowW,
                        windowH = windowH,
                        minDim = minDim
                    )
                    val note = item.note
                    if (!note.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(windowH * 0.012f))
                        SelectionContainer {
                            Text(
                                text = note,
                                fontSize = (minDim * 0.045f).value.sp,
                                color = onPageColor
                            )
                        }
                    }
                } else {
                    // Selectable: drag across the text to select any
                    // part of it, then copy from the selection menu.
                    SelectionContainer {
                        Text(
                            text = item.text,
                            fontSize = (minDim * 0.045f).value.sp,
                            color = onPageColor
                        )
                    }
                }
                Spacer(modifier = Modifier.height(windowH * 0.016f))
            }

            // Copy / Edit (green) / Delete (red).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(windowW * 0.022f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onCopy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = onPageColor,
                        contentColor = pageBg
                    ),
                    shape = RoundedCornerShape(minDim * 0.5f),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = pageBg,
                        modifier = Modifier.size(minDim * 0.05f)
                    )
                    Spacer(modifier = Modifier.width(windowW * 0.017f))
                    Text(
                        text = "Copy",
                        color = pageBg,
                        fontSize = (minDim * 0.039f).value.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                TextButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = CalNyxGreen,
                        modifier = Modifier.size(minDim * 0.05f)
                    )
                    Spacer(modifier = Modifier.width(windowW * 0.011f))
                    Text(text = "Edit", color = CalNyxGreen, fontSize = (minDim * 0.039f).value.sp)
                }
                TextButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = CalNyxRed,
                        modifier = Modifier.size(minDim * 0.05f)
                    )
                    Spacer(modifier = Modifier.width(windowW * 0.011f))
                    Text(text = "Delete", color = CalNyxRed, fontSize = (minDim * 0.039f).value.sp)
                }
            }
            Spacer(modifier = Modifier.height(windowH * 0.02f))
        }
    }
}

@Composable
private fun ClipboardFullImage(path: String, dimColor: Color, windowW: Dp, windowH: Dp, minDim: Dp) {
    // Full-size view: decoded on Dispatchers.IO, downsampled to
    // ~1080 px wide (the list thumbnail uses ~400 px).
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
                while (bounds.outWidth / sample > 1080) sample *= 2
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
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth()
                .height(windowH * 0.4f)
                .clip(RoundedCornerShape(minDim * 0.033f))
                .background(dimColor.copy(alpha = 0.08f))
        )
    } else if (!loaded) {
        Box(
            modifier = Modifier.fillMaxWidth()
                .height(windowH * 0.4f)
                .clip(RoundedCornerShape(minDim * 0.033f))
                .background(dimColor.copy(alpha = 0.15f))
        )
    } else {
        Box(
            modifier = Modifier.fillMaxWidth()
                .height(windowH * 0.12f)
                .clip(RoundedCornerShape(minDim * 0.033f))
                .background(dimColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Image unavailable", fontSize = (minDim * 0.036f).value.sp, color = dimColor)
        }
    }
}
