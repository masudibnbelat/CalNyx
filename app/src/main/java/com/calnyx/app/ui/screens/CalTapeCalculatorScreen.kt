package com.calnyx.app.ui.screens

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calnyx.app.data.HistoryEntity
import com.calnyx.app.data.SheetEntity
import com.calnyx.app.viewmodel.CalTapeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// % -BASED RESPONSIVE VERSION (2026-11-07) - REPLACE CalTapeCalculatorScreen.kt.
//  Sob margin/padding/width/height/font % -e, window-er BoxWithConstraints
//  size theke - mini/floating/split/landscape-e auto adjust hobe.
//  Sheets drawer contrast: dark drawerBg 0xFF262626 -> 0xFF333333
//  (page 0xFF0E0E0E theke onek halka, shohoje bojha jabe), light
//  drawerBg 0xFFFFFFFF -> 0xFFE3E1DC (page 0xFFF6F4EF theke garo).
//  Logic/swipe detector (v4)/PDF - kichu bodlayni, shudhu size % -e.
// CalTape worksheet, matched to the reference app in both themes:
// running-total tape organised in sheets (side drawer, top-left menu
// button; sheets can be added, renamed and deleted, and the PDF
// exports under the sheet's current name into Downloads/CalTape).
// Each tape row has two zones — the calculation (operator + amount)
// in the left half and a TEXT NOTE in the right half. Tap a line to
// edit its note inline; LONG-PRESS a line to edit the line itself
// (operator, amount, note, or delete it). BLACK AND WHITE ONLY
// (B/W version): every line, key and the PDF are black/white — the
// operator's SYMBOL (+ − × ÷) is what tells them apart, not a
// colour. A total row follows its value: positive totals show "+",
// negative totals show "−", both in the text colour.
// The pending operator shows the moment its key is pressed, with a
// blinking cursor. Animation throughout: new lines spring in, the
// strip total counts up/down, keys flash on press, the keypad
// staggers in, the sheet name slides on switch. All totals are
// folded live from the lines, so edits correct every later
// subtotal. All key labels bold. All data lives in the local
// Room/SQLite database — the app is fully offline.
private data class TapePalette(
    val bg: Color,
    val keyNum: Color,
    val keyFn: Color,
    val keyBack: Color,
    val keyBorder: Color?,
    val strip: Color,
    val drawerBg: Color,
    val text: Color,
    val dim: Color,
    val green: Color,
    val red: Color,
    val live: Color,
    val enter: Color
)

private val DarkTapePalette = TapePalette(
    bg = Color(0xFF0E0E0E),
    keyNum = Color(0xFF1B1B1B),
    keyFn = Color(0xFF262626),
    keyBack = Color(0xFF262626),
    keyBorder = null,
    strip = Color(0xFF1B1B1B),
    drawerBg = Color(0xFF333333),
    text = Color(0xFFF6F4EF),
    dim = Color(0xFF8A8A8A),
    green = Color(0xFFF6F4EF),
    red = Color(0xFFF6F4EF),
    live = Color(0xFFF6F4EF),
    enter = Color(0xFFF6F4EF)
)

private val LightTapePalette = TapePalette(
    bg = Color(0xFFF6F4EF),
    keyNum = Color(0xFFFFFFFF),
    keyFn = Color(0xFFEFEDE8),
    keyBack = Color(0xFFEFEDE8),
    keyBorder = Color(0xFFE9E6DF),
    strip = Color(0xFFEFEDE8),
    drawerBg = Color(0xFFE3E1DC),
    text = Color(0xFF0E0E0E),
    dim = Color(0xFF6B6B6B),
    green = Color(0xFF0E0E0E),
    red = Color(0xFF0E0E0E),
    live = Color(0xFF0E0E0E),
    enter = Color(0xFF0E0E0E)
)

private val moneyFormat = DecimalFormat("#,##0.00")

private fun fmtMoney(value: Double): String = moneyFormat.format(value)

// B/W rule for the whole tape: every operator is the text colour.
// The SYMBOL (+ − × ÷) tells them apart, not a colour.
private fun opColor(op: String, palette: TapePalette): Color {
    return palette.text
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CalTapeCalculatorScreen(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    viewModel: CalTapeViewModel = viewModel()
) {
    val palette = if (darkTheme) DarkTapePalette else LightTapePalette

    val lines by viewModel.lines.collectAsState()
    val input by viewModel.input.collectAsState()
    val total by viewModel.total.collectAsState()
    val pendingOp by viewModel.pendingOp.collectAsState()
    val showPendingOp by viewModel.showPendingOp.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val sheets by viewModel.sheets.collectAsState()
    val currentSheetId by viewModel.currentSheetId.collectAsState()
    val currentSheet = sheets.firstOrNull { it.id == currentSheetId }

    var tapeExpanded by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var editingLineId by remember { mutableStateOf<String?>(null) }
    var editingNote by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<SheetEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var editTarget by remember { mutableStateOf<HistoryEntity?>(null) }
    var editOp by remember { mutableStateOf("+") }
    var editAmount by remember { mutableStateOf("") }
    var editNote by remember { mutableStateOf("") }
    var keypadIntroDone by remember { mutableStateOf(false) }
    val seenLineIds = remember { mutableSetOf<String>() }
    val editSheetState = rememberModalBottomSheetState()
    val noteFocusRequester = remember { FocusRequester() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Clear the notification/status panel for real: use the actual
    // status-bar inset when the window reports one, and never less
    // than 24.dp (a typical status-bar height).
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // topInsetPct (% - height-er 2.8%) niche BoxWithConstraints-e hobe.

    // The strip total counts up/down to its new value instead of
    // jumping; at rest it snaps to the exact Double.
    val animatedTotal by animateFloatAsState(
        targetValue = total.toFloat(),
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "stripTotalCount"
    )
    val shownTotal = if (abs(animatedTotal - total.toFloat()) < 0.01f) {
        total
    } else {
        animatedTotal.toDouble()
    }

    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(1600)
            feedback = null
        }
    }

    // Bring the keyboard up right on the line being edited.
    LaunchedEffect(editingLineId) {
        if (editingLineId != null) {
            delay(80)
            try {
                noteFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Field not composed yet — the tap target is on screen,
                // so this is only a safety net.
            }
        }
    }

    val lastTotalId = remember(lines) {
        lines.lastOrNull { CalTapeViewModel.splitNote(it.expression).first == "TOTAL" }?.id
    }

    // Folded running total at every TOTAL row, computed live from the
    // lines — so a line edit or delete corrects all later subtotals.
    val foldedTotals = remember(lines) {
        var running = 0.0
        val map = HashMap<String, Double>()
        for (line in lines) {
            val base = CalTapeViewModel.splitNote(line.expression).first
            if (base == "TOTAL") {
                map[line.id] = running
            } else {
                val op = CalTapeViewModel.normalizeOp(base.firstOrNull())
                val value = base.drop(1).toDoubleOrNull() ?: 0.0
                running = CalTapeViewModel.applyOperator(running, op, value)
            }
        }
        map
    }

    val tapeListState = rememberLazyListState()
    LaunchedEffect(lines.size, input, showPendingOp) {
        val lastIndex = lines.size // the in-progress line sits after the tape lines
        if (lastIndex > 0) {
            tapeListState.animateScrollToItem(lastIndex)
        }
    }

    fun saveInlineEdit() {
        val id = editingLineId ?: return
        val line = lines.firstOrNull { it.id == id } ?: return
        viewModel.setLineNote(line, editingNote)
        editingLineId = null
    }

    fun openNoteEditor(line: HistoryEntity) {
        if (editingLineId == line.id) {
            saveInlineEdit()
            return
        }
        if (editingLineId != null) {
            saveInlineEdit()
        }
        editingLineId = line.id
        editingNote = CalTapeViewModel.splitNote(line.expression).second
    }

    fun openLineEditor(line: HistoryEntity) {
        if (editingLineId != null) {
            saveInlineEdit()
        }
        val (base, note) = CalTapeViewModel.splitNote(line.expression)
        editTarget = line
        editNote = note
        if (base != "TOTAL") {
            editOp = CalTapeViewModel.normalizeOp(base.firstOrNull())
            editAmount = base.drop(1)
        }
    }

    // ----- Rename sheet dialog -----
    val target = renameTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = palette.drawerBg,
            titleContentColor = palette.text,
            textContentColor = palette.text,
            title = {
                BoxWithConstraints {
                    Text(
                        "Rename sheet",
                        fontSize = (maxWidth.value * 0.064f).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Sheet name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameSheet(target.id, renameText)
                        renameTarget = null
                    }
                ) {
                    Text("Save", color = palette.text, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel", color = palette.dim)
                }
            }
        )
    }

    // ----- Edit line panel (long-press a line) -----
    val lineToEdit = editTarget
    if (lineToEdit != null) {
        val isTotalLine = CalTapeViewModel.splitNote(lineToEdit.expression).first == "TOTAL"
        ModalBottomSheet(
            onDismissRequest = { editTarget = null },
            sheetState = editSheetState,
            containerColor = palette.drawerBg,
            contentColor = palette.text
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val bw = maxWidth
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = bw * 0.055f)
                        .padding(bottom = bw * 0.078f)
                ) {
                Text(
                    if (isTotalLine) "Edit subtotal line" else "Edit line",
                    fontSize = (bw.value * 0.05f).sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.text
                )
                Spacer(modifier = Modifier.height(bw * 0.044f))

                if (!isTotalLine) {
                    // Operator chips, coloured by the tape rule.
                    Row(horizontalArrangement = Arrangement.spacedBy(bw * 0.022f)) {
                        listOf("+", "-", "*", "/").forEach { op ->
                            val selected = editOp == op
                            val chipColor = opColor(op, palette)
                            Box(
                                modifier = Modifier
                                    .size(bw * 0.133f)
                                    .clip(RoundedCornerShape(bw * 0.033f))
                                    .background(if (selected) chipColor else palette.keyFn)
                                    .clickable { editOp = op },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    displayOp(op),
                                    fontSize = (bw.value * 0.061f).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) palette.bg else palette.text
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(bw * 0.039f))

                    OutlinedTextField(
                        value = editAmount,
                        onValueChange = { newValue ->
                            editAmount = newValue.filter { it.isDigit() || it == '.' }
                        },
                        singleLine = true,
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(bw * 0.033f))
                }

                OutlinedTextField(
                    value = editNote,
                    onValueChange = { editNote = it },
                    singleLine = true,
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(bw * 0.05f))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            viewModel.deleteLine(lineToEdit)
                            editTarget = null
                            feedback = "Line deleted"
                        }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = palette.text,
                            modifier = Modifier.size(bw * 0.05f)
                        )
                        Spacer(modifier = Modifier.width(bw * 0.017f))
                        Text(
                            "Delete line",
                            color = palette.text,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { editTarget = null }) {
                        Text("Cancel", color = palette.dim)
                    }
                    TextButton(
                        onClick = {
                            if (isTotalLine) {
                                viewModel.setLineNote(lineToEdit, editNote)
                            } else {
                                val value = editAmount.toDoubleOrNull()
                                if (value != null) {
                                    viewModel.updateLine(lineToEdit, editOp, value, editNote)
                                }
                            }
                            editTarget = null
                        }
                    ) {
                        Text("Save", color = palette.text, fontWeight = FontWeight.Bold)
                    }
                }
                }
            }
        }
    }

    // APP-DRAWER SWIPE (2026-11-06 fix, V2; Sheets fix 2026-11-07;
    // detector v4, 2026-11-07): the NavGraph's 28dp right-edge zone
    // never fired here - this screen's own ModalNavigationDrawer
    // (Sheets) sits on top of it and eats the gesture. V1 put the
    // detector on an OUTER Box, but that lost too: the drawer's own
    // drag handling sits deeper in the hit path and eats the touch
    // slop before an ancestor detector ever sees it. So the SAME
    // detector also sits on the CONTENT Column inside the drawer
    // (deepest for every touch on tape/keypad/strip) - that one sees
    // the drag first and wins. The outer Box keeps it as a backup;
    // firing twice is harmless (onOpenDrawer just sets a boolean).
    // WHY TWO PHASES: V2 used detectHorizontalDragGestures here and
    // that broke the Sheets drawer - that helper claims the drag the
    // moment the touch slop is passed, so the Sheets drawer
    // (shallower in the hit path) never saw an unconsumed rightward
    // drag and its swipe-to-open stopped firing. V3 replaced it with
    // a manual loop that broke on isConsumed - and THAT broke the
    // app drawer instead: the Sheets drawer ALSO consumes leftward
    // drags at slop (it cannot open leftward, but it still claims
    // them), so the loop stopped before reaching 24.dp. This version
    // does both directions right:
    //  Phase 1 - await horizontal slop WITHOUT consuming anything.
    //  If a scrolling child (a tape row) claims the drag first, the
    //  slop await returns null and we give up: the row scrolls alone
    //  and the drawer stays shut.
    //  Phase 2 - track the rest with the RAW delta (position minus
    //  previousPosition), IGNORING consumption: the Sheets drawer
    //  may consume leftward drags, but it cannot open leftward, so
    //  there is nothing to fight over. Leftward past 24.dp opens
    //  the app drawer (and claims the gesture, like V2 did);
    //  rightward past 24.dp stops without consuming, so the Sheets
    //  drawer gets the whole drag and opens exactly as before.
    //  When the Sheets drawer is already open this does nothing -
    //  its own swipe closes it instead.
    fun drawerSwipePct(windowW: Dp) = Modifier.pointerInput(windowW) {
        val thresholdPx = (windowW * 0.066f).toPx() // width-er 6.6%
        awaitEachGesture {
            val down = awaitFirstDown()
            // onTouchSlopReached is a REQUIRED parameter in this Compose version
            // (BOM 2024.10.01) - there is no one-argument overload.
            val slopChange = awaitHorizontalTouchSlopOrCancellation(down.id) { _, _ -> }
            if (slopChange != null && drawerState.currentValue == DrawerValue.Closed) {
                var totalDx = (slopChange.position - down.position).x
                var done = false
                if (totalDx < -thresholdPx) {
                    done = true
                    onOpenDrawer()
                    slopChange.consume()
                }
                while (!done) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if (drawerState.currentValue != DrawerValue.Closed) break
                    totalDx += (change.position - change.previousPosition).x
                    if (totalDx < -thresholdPx) {
                        done = true
                        onOpenDrawer()
                        change.consume()
                    } else if (totalDx > thresholdPx) {
                        // Rightward: the Sheets drawer's gesture.
                        // Stop without consuming - it gets the drag.
                        break
                    }
                }
            }
        }
    }
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        // ---- % SIZE SYSTEM: window-er asol size (multi-window aware) ----
        val windowW = maxWidth
        val windowH = maxHeight
        val minDim = minOf(windowW, windowH)
        val isLandscape = windowW > windowH
        val isMiniWindow = windowW < 320.dp || windowH < 480.dp
        val isSmallWindow = windowW < 360.dp || windowH < 620.dp
        val isVeryShort = windowH < 420.dp
        val compactFontScale = when {
            minDim < 300.dp -> 0.85f
            minDim < 340.dp -> 0.92f
            else -> 1f
        }
        val topInsetPct = maxOf(statusTop, windowH * 0.028f)
        // Sheets drawer width % of WINDOW: normal 78%, mini 85%.
        val sheetDrawerW = if (isMiniWindow) windowW * 0.85f else windowW * 0.78f
        val sheetPadH = sheetDrawerW * 0.057f
        val sheetPadV = windowH * 0.024f
        val sheetTitleFont = (sheetDrawerW.value * 0.071f).sp
        val sheetItemFont = (sheetDrawerW.value * 0.057f).sp
        val sheetSmallFont = (sheetDrawerW.value * 0.039f).sp
        val sheetIcon = sheetDrawerW * 0.071f
        val sheetBtn = sheetDrawerW * 0.114f
        val sheetBtnIcon = sheetDrawerW * 0.064f
        val sheetCorner = sheetDrawerW * 0.043f
        val sheetRowPadH = sheetDrawerW * 0.036f
        val sheetRowPadV = windowH * 0.012f
        val sheetGapH = windowH * 0.017f
        val sheetGapS = windowH * 0.005f
        val sheetGapFooter = windowH * 0.009f
        // Main content % vars.
        val tapePadH = windowW * 0.055f
        val topRowH = if (isLandscape) windowH * 0.13f else windowH * 0.061f
        val topMenuBtn = windowW * 0.133f
        val topMenuIcon = windowW * 0.072f
        val sheetNameFont = (minDim.value * 0.047f * compactFontScale).sp
        val emptyFont = (minDim.value * 0.042f * compactFontScale).sp
        val tapeBottomPad = windowH * 0.052f
        val stripPadH = windowW * 0.022f
        val stripPadV = windowH * 0.005f
        val stripIcon = windowW * 0.058f
        val stripTotalFont = (minDim.value * 0.053f * compactFontScale).sp
        val keyGapH = windowW * 0.022f
        val keyGapV = windowH * 0.009f
        val keyPadH = windowW * 0.028f
        val keyPadV = windowH * 0.012f
        val keyH = if (isLandscape) windowH * 0.11f else windowH * 0.065f
        val expandIcon = windowW * 0.055f
        val feedbackFont = (minDim.value * 0.036f).sp
        val feedbackIcon = windowW * 0.044f
    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = palette.drawerBg,
                drawerContentColor = palette.text,
                modifier = Modifier.width(sheetDrawerW)
                    .border(
                        sheetDrawerW * 0.004f,
                        if (darkTheme) Color(0xFF5A5A5A) else Color(0xFFC9C5BC),
                        RoundedCornerShape(topEnd = sheetDrawerW * 0.057f, bottomEnd = sheetDrawerW * 0.057f)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = sheetPadH, vertical = sheetPadV)
                ) {
                    Text(
                        "Sheets",
                        fontSize = sheetTitleFont,
                        fontWeight = FontWeight.Bold,
                        color = palette.text
                    )
                    Spacer(modifier = Modifier.height(sheetGapH))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                    sheets.forEach { sheet ->
                        val selected = sheet.id == currentSheetId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(sheetCorner))
                                .background(if (selected) palette.keyFn else Color.Transparent)
                                .clickable {
                                    viewModel.switchSheet(sheet.id)
                                    scope.launch { drawerState.close() }
                                }
                                .padding(horizontal = sheetRowPadH, vertical = sheetRowPadV),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                tint = if (selected) palette.text else palette.dim,
                                modifier = Modifier.size(sheetIcon)
                            )
                            Spacer(modifier = Modifier.width(sheetRowPadH))
                            Text(
                                sheet.name,
                                fontSize = sheetItemFont,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = palette.text,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    renameTarget = sheet
                                    renameText = sheet.name
                                },
                                modifier = Modifier.size(sheetBtn)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Rename sheet",
                                    tint = palette.dim,
                                    modifier = Modifier.size(sheetBtnIcon)
                                )
                            }
                            if (sheets.size > 1) {
                                IconButton(
                                    onClick = { viewModel.deleteSheet(sheet.id) },
                                    modifier = Modifier.size(sheetBtn)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete sheet",
                                        tint = palette.dim,
                                        modifier = Modifier.size(sheetBtnIcon)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(sheetGapS))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(sheetCorner))
                            .clickable {
                                viewModel.addSheet()
                                scope.launch { drawerState.close() }
                            }
                            .padding(horizontal = sheetRowPadH, vertical = sheetRowPadV),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = palette.text,
                            modifier = Modifier.size(sheetIcon)
                        )
                        Spacer(modifier = Modifier.width(sheetRowPadH))
                        Text(
                            "New sheet",
                            fontSize = sheetItemFont,
                            color = palette.text,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    }
                    HorizontalDivider(color = palette.dim.copy(alpha = 0.25f))
                    Spacer(modifier = Modifier.height(sheetGapFooter))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(sheetCorner))
                            .clickable(enabled = lines.isNotEmpty()) {
                                scope.launch { drawerState.close() }
                                val sheetName = currentSheet?.name ?: "Sheet"
                                val tapeLines = lines
                                scope.launch {
                                    val saved = withContext(Dispatchers.IO) {
                                        exportTapePdf(context, sheetName, tapeLines)
                                    }
                                    feedback = if (saved) {
                                        "PDF saved in Downloads/CalTape"
                                    } else {
                                        "PDF export failed"
                                    }
                                }
                            }
                            .padding(horizontal = sheetRowPadH, vertical = sheetRowPadV),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = if (lines.isNotEmpty()) palette.text else palette.dim.copy(alpha = 0.4f),
                            modifier = Modifier.size(sheetIcon)
                        )
                        Spacer(modifier = Modifier.width(sheetRowPadH))
                        Text(
                            "Export PDF",
                            fontSize = sheetItemFont,
                            color = if (lines.isNotEmpty()) palette.text else palette.dim.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        "Saved to Downloads/CalTape",
                        fontSize = sheetSmallFont,
                        color = palette.dim,
                        modifier = Modifier.padding(horizontal = sheetRowPadH, vertical = windowH * 0.002f)
                    )
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.bg)
                .padding(top = topInsetPct)
                .navigationBarsPadding()
                .then(drawerSwipePct(windowW))
        ) {
            // ----- Top row: side-menu button (left) + current sheet name -----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topRowH)
                    .padding(horizontal = windowW * 0.011f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { scope.launch { drawerState.open() } },
                    modifier = Modifier.size(topMenuBtn)
                ) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "Sheets",
                        tint = palette.text,
                        modifier = Modifier.size(topMenuIcon)
                    )
                }
                AnimatedContent(
                    targetState = currentSheet?.name ?: "",
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(250)) +
                                slideInVertically(animationSpec = tween(250)) { it / 2 })
                            .togetherWith(
                                fadeOut(animationSpec = tween(180)) +
                                        slideOutVertically(animationSpec = tween(180)) { -it / 2 }
                            )
                    },
                    label = "sheetName"
                ) { name ->
                    Text(
                        name,
                        fontSize = sheetNameFont,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.text
                    )
                }
            }

            // ----- Worksheet tape -----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = tapePadH)
            ) {
                if (lines.isEmpty() && input.isEmpty() && !showPendingOp) {
                    Text(
                        "Start typing — your worksheet\nwill appear here.",
                        color = palette.dim.copy(alpha = 0.7f),
                        fontSize = emptyFont,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        state = tapeListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = tapeBottomPad)
                    ) {
                        items(lines, key = { it.id }) { line ->
                            val isEditing = line.id == editingLineId
                            if (CalTapeViewModel.splitNote(line.expression).first == "TOTAL") {
                                TapeTotalRow(
                                    line = line,
                                    totalValue = foldedTotals[line.id] ?: 0.0,
                                    isFinal = line.id == lastTotalId,
                                    palette = palette,
                                    isEditing = isEditing,
                                    editingNote = editingNote,
                                    focusRequester = noteFocusRequester,
                                    seenIds = seenLineIds,
                                    onNoteTextChange = { editingNote = it },
                                    onNoteDone = { saveInlineEdit() },
                                    onClick = { openNoteEditor(line) },
                                    onLongClick = { openLineEditor(line) }
                                )
                            } else {
                                TapeEntryRow(
                                    line = line,
                                    palette = palette,
                                    isEditing = isEditing,
                                    editingNote = editingNote,
                                    focusRequester = noteFocusRequester,
                                    seenIds = seenLineIds,
                                    onNoteTextChange = { editingNote = it },
                                    onNoteDone = { saveInlineEdit() },
                                    onClick = { openNoteEditor(line) },
                                    onLongClick = { openLineEditor(line) }
                                )
                            }
                        }
                        item {
                            if (input.isNotEmpty() || showPendingOp) {
                                // The pending line: visible from the
                                // moment an operator key is pressed,
                                // digits join it as they are typed, with
                                // a blinking cursor at the end. Same
                                // two-zone geometry as a committed row,
                                // so nothing ever jumps sideways. Symbol
                                // and number take the operator's colour:
                                // + and × green, − and ÷ red.
                                val lineColor = opColor(pendingOp, palette)
                                val pendingEntrance = remember { Animatable(0f) }
                                LaunchedEffect(Unit) {
                                    pendingEntrance.animateTo(
                                        1f,
                                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                                    )
                                }
                                val cursorTransition = rememberInfiniteTransition(label = "tapeCursor")
                                val cursorAlpha by cursorTransition.animateFloat(
                                    initialValue = 1f,
                                    targetValue = 0f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(530),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "tapeCursorAlpha"
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            alpha = pendingEntrance.value
                                            translationY = (1f - pendingEntrance.value) * 16f
                                        }
                                        .padding(horizontal = windowW * 0.017f, vertical = windowH * 0.006f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1.05f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            displayOp(pendingOp),
                                            color = lineColor,
                                            fontSize = (minDim.value * 0.053f * compactFontScale).sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.width(windowW * 0.078f)
                                        )
                                        Text(
                                            displayEntry(input),
                                            color = lineColor,
                                            fontSize = (minDim.value * 0.055f * compactFontScale).sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = FontFamily.Monospace,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            "▏",
                                            color = lineColor.copy(alpha = cursorAlpha),
                                            fontSize = (minDim.value * 0.055f * compactFontScale).sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(windowW * 0.039f))
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // Floating expand / collapse button, like the reference.
                IconButton(
                    onClick = { tapeExpanded = !tapeExpanded },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = windowH * 0.005f)
                        .clip(RoundedCornerShape(windowW * 0.039f))
                        .background(palette.keyFn)
                ) {
                    Icon(
                        if (tapeExpanded) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                        contentDescription = if (tapeExpanded) "Show keypad" else "Expand tape",
                        tint = palette.text,
                        modifier = Modifier.size(expandIcon)
                    )
                }

                // Feedback pill (copy / PDF) — fades in above the strip.
                // Wrapped in a Column: an AnimatedVisibility placed
                // directly inside a Box that sits inside a Column picks
                // the ColumnScope overload and fails to compile.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = windowH * 0.007f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedVisibility(
                        visible = feedback != null,
                        enter = fadeIn(animationSpec = tween(200)) +
                                slideInVertically(animationSpec = tween(250)) { it / 2 },
                        exit = fadeOut(animationSpec = tween(250)) +
                                slideOutVertically(animationSpec = tween(250)) { it / 2 }
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(windowW * 0.055f))
                                .background(palette.text)
                                .padding(horizontal = windowW * 0.039f, vertical = windowH * 0.008f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = palette.bg,
                                modifier = Modifier.size(feedbackIcon)
                            )
                            Spacer(modifier = Modifier.width(windowW * 0.017f))
                            Text(
                                feedback ?: "",
                                color = palette.bg,
                                fontSize = feedbackFont,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !tapeExpanded,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(200))
            ) {
                Column {
                    // ----- Strip: undo / redo ... total / copy -----
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.strip)
                            .padding(horizontal = stripPadH, vertical = stripPadV),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.undo() },
                            enabled = lines.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.Undo,
                                contentDescription = "Undo",
                                tint = if (lines.isNotEmpty()) palette.text else palette.dim.copy(alpha = 0.4f),
                                modifier = Modifier.size(stripIcon)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.redo() },
                            enabled = canRedo
                        ) {
                            Icon(
                                Icons.Default.Redo,
                                contentDescription = "Redo",
                                tint = if (canRedo) palette.text else palette.dim.copy(alpha = 0.4f),
                                modifier = Modifier.size(stripIcon)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            fmtMoney(shownTotal),
                            color = palette.text,
                            fontSize = stripTotalFont,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(buildTapeText(lines)))
                                feedback = "Tape copied"
                            },
                            enabled = lines.isNotEmpty()
                        ) {
                            AnimatedContent(
                                targetState = feedback == "Tape copied",
                                label = "copyFeedback"
                            ) { copied ->
                                Icon(
                                    if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = "Copy tape",
                                    tint = when {
                                        copied -> palette.text
                                        lines.isNotEmpty() -> palette.text
                                        else -> palette.dim.copy(alpha = 0.4f)
                                    },
                                    modifier = Modifier.size(stripIcon)
                                )
                            }
                        }
                    }

                    // ----- Keypad: plain tape keys only -----
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = keyPadH, vertical = keyPadV),
                        verticalArrangement = Arrangement.spacedBy(keyGapV)
                    ) {
                        TapeRow(
                            index = 0,
                            introDone = keypadIntroDone,
                            onIntroDone = { keypadIntroDone = true },
                            keyH = keyH
                        ) {
                            TapeKey("AC", palette.text, palette.bg, palette, Modifier.weight(1f)) { viewModel.onKey("AC") }
                            TapeKey("⌫", palette.keyBack, palette.text, palette, Modifier.weight(1f)) {
                                viewModel.onKey(
                                    "⌫"
                                )
                            }
                            TapeKey(
                                "%",
                                palette.keyFn,
                                palette.text,
                                palette,
                                Modifier.weight(1f)
                            ) { viewModel.onKey("%") }
                            TapeKey(
                                "÷",
                                palette.keyFn,
                                palette.text,
                                palette,
                                Modifier.weight(1f)
                            ) { viewModel.onKey("÷") }
                        }
                        TapeRow(
                            index = 1,
                            introDone = keypadIntroDone,
                            onIntroDone = { keypadIntroDone = true },
                            keyH = keyH
                        ) {
                            TapeKey(
                                "7",
                                palette.keyNum,
                                palette.text,
                                palette,
                                Modifier.weight(1f)
                            ) { viewModel.onKey("7") }
                            TapeKey(
                                "8",
                                palette.keyNum,
                                palette.text,
                                palette,
                                Modifier.weight(1f)
                            ) { viewModel.onKey("8") }
                            TapeKey(
                                "9",
                                palette.keyNum,
                                palette.text,
                                palette,
                                Modifier.weight(1f)
                            ) { viewModel.onKey("9") }
                            TapeKey(
                                "×",
                                palette.keyFn,
                                palette.text,
                                palette,
                                Modifier.weight(1f)
                            ) { viewModel.onKey("×") }
                        }
                        TapeRow(
                            index = 2,
                            introDone = keypadIntroDone,
                            onIntroDone = { keypadIntroDone = true },
                            keyH = keyH
                        ) {
                            TapeKey(
                                "4",
                                palette.keyNum,
                                palette.text,
                                palette,
                                Modifier.weight(1f)
                            ) { viewModel.onKey("4") }
                            TapeKey(
                                "5",
                                palette.keyNum,
                                palette.text,
                                palette,
                                Modifier.weight(1f)
                            ) { viewModel.onKey("5") }
                            TapeKey(
                                "6",
                                palette.keyNum,
                                palette.text,
                                palette,
                                Modifier.weight(1f)
                            ) { viewModel.onKey("6") }
                            TapeKey(
                                "−",
                                palette.keyFn,
                                palette.text,
                                palette,
                                Modifier.weight(1f)
                            ) { viewModel.onKey("−") }
                        }
                        TapeRow(
                            index = 3,
                            introDone = keypadIntroDone,
                            onIntroDone = { keypadIntroDone = true },
                            keyH = keyH
                        ) {
                            TapeKey(
                                "1",
                                palette.keyNum,
                                palette.text,
                                palette,
                                Modifier.weight(1f)
                            ) { viewModel.onKey("1") }
                            TapeKey(
                                "2",
                                palette.keyNum,
                                palette.text,
                                palette,
                                Modifier.weight(1f)
                            ) { viewModel.onKey("2") }
                            TapeKey(
                                "3",
                                palette.keyNum,
                                palette.text,
                                palette,
                                Modifier.weight(1f)
                            ) { viewModel.onKey("3") }
                            TapeKey(
                                "+",
                                palette.keyFn,
                                palette.text,
                                palette,
                                Modifier.weight(1f)
                            ) { viewModel.onKey("+") }
                        }
                        TapeRow(
                            index = 4,
                            introDone = keypadIntroDone,
                            onIntroDone = { keypadIntroDone = true },
                            keyH = keyH
                        ) {
                            TapeKey(
                                "()",
                                palette.keyFn,
                                palette.text,
                                palette,
                                Modifier.weight(1f),
                                fontSize = 22
                            ) { viewModel.onKey("()") }
                            TapeKey(
                                "0",
                                palette.keyNum,
                                palette.text,
                                palette,
                                Modifier.weight(1f)
                            ) { viewModel.onKey("0") }
                            TapeKey(
                                ".",
                                palette.keyNum,
                                palette.text,
                                palette,
                                Modifier.weight(1f)
                            ) { viewModel.onKey(".") }
                            TapeKey(
                                "=",
                                palette.text,
                                palette.bg,
                                palette,
                                Modifier.weight(1f),
                                fontSize = 28,
                                borderless = true
                            ) { viewModel.onKey("=") }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun TapeRow(
    index: Int,
    introDone: Boolean,
    onIntroDone: () -> Unit,
    keyH: Dp,
    content: @Composable RowScope.() -> Unit
) {
    // Staggered rise-in the first time the keypad appears.
    val progress = remember { Animatable(if (introDone) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!introDone) {
            delay(index * 45L)
            progress.animateTo(1f, animationSpec = tween(300, easing = FastOutSlowInEasing))
            if (index == 4) onIntroDone()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(keyH)
            .graphicsLayer {
                alpha = progress.value
                translationY = (1f - progress.value) * 30f
            },
        horizontalArrangement = Arrangement.spacedBy(keyH * 0.14f),
        content = content
    )
}

@Composable
private fun TapeKey(
    label: String,
    background: Color,
    textColor: Color,
    palette: TapePalette,
    modifier: Modifier = Modifier,
    fontSize: Int = 24,
    borderless: Boolean = false,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(120),
        label = "tapeKeyScale"
    )
    val flash by animateFloatAsState(
        targetValue = if (pressed) 0.16f else 0f,
        animationSpec = tween(if (pressed) 70 else 220),
        label = "tapeKeyFlash"
    )
    val borderColor = if (borderless) null else palette.keyBorder
    BoxWithConstraints(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        val keyCorner = maxWidth * 0.20f
        val keyBorderW = maxWidth * 0.0125f
        val keyFont = (maxWidth.value * fontSize / 80f).sp
        val backIconSize = maxWidth * 0.325f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
                .clip(RoundedCornerShape(keyCorner))
                .background(background)
                .then(
                    if (borderColor != null) {
                        Modifier.border(keyBorderW, borderColor, RoundedCornerShape(keyCorner))
                    } else {
                        Modifier
                    }
                )
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
        // Flash that blooms while the key is pressed (text colour,
        // so it shows in both themes — white was invisible on the
        // light keys).
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(palette.text.copy(alpha = flash))
        )
        if (label == "⌫") {
            Icon(
                Icons.Default.Backspace,
                contentDescription = "Backspace",
                tint = textColor,
                modifier = Modifier.size(backIconSize)
            )
        } else {
            Text(
                // The bracket key shows a space inside: "( )".
                if (label == "()") "( )" else label,
                color = textColor,
                fontSize = keyFont,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        }
    }
}

// The note zone of a row: plain dim text normally; a focused text
// field, right on the line, while that line is being edited.
@Composable
private fun RowScope.TapeNoteZone(
    note: String,
    isEditing: Boolean,
    editingNote: String,
    focusRequester: FocusRequester,
    palette: TapePalette,
    fontSize: TextUnit,
    onNoteTextChange: (String) -> Unit,
    onNoteDone: () -> Unit
) {
    if (isEditing) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape((fontSize.value * 0.57f).dp))
                .background(palette.keyFn)
                .padding(horizontal = (fontSize.value * 0.57f).dp, vertical = (fontSize.value * 0.36f).dp)
        ) {
            BasicTextField(
                value = editingNote,
                onValueChange = onNoteTextChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = palette.text,
                    fontSize = fontSize
                ),
                cursorBrush = SolidColor(palette.text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onNoteDone() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Box {
                        if (editingNote.isEmpty()) {
                            Text(
                                "Write note",
                                color = palette.dim,
                                fontSize = fontSize
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
    } else {
        Text(
            note,
            color = palette.dim,
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TapeEntryRow(
    line: HistoryEntity,
    palette: TapePalette,
    isEditing: Boolean,
    editingNote: String,
    focusRequester: FocusRequester,
    seenIds: MutableSet<String>,
    onNoteTextChange: (String) -> Unit,
    onNoteDone: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val (base, note) = CalTapeViewModel.splitNote(line.expression)
    val op = CalTapeViewModel.normalizeOp(base.firstOrNull())
    val value = base.drop(1).toDoubleOrNull() ?: 0.0
    val lineColor = opColor(op, palette)
    // Entrance: a new line springs up into place, once per line.
    val entrance = remember(line.id) {
        Animatable(if (seenIds.contains(line.id)) 1f else 0f)
    }
    LaunchedEffect(line.id) {
        if (!seenIds.contains(line.id)) {
            seenIds.add(line.id)
            entrance.animateTo(
                1f,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }
    val rowBackground by animateColorAsState(
        targetValue = if (isEditing) palette.keyFn.copy(alpha = 0.45f) else Color.Transparent,
        animationSpec = tween(220),
        label = "entryRowHighlight"
    )
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val rowW = maxWidth // BoxWithConstraintsScope theke dhore rakha - Row/Column content-e (RowScope) maxWidth direct cholena
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = entrance.value
                translationY = (1f - entrance.value) * 28f
                val s = 0.97f + 0.03f * entrance.value
                scaleX = s
                scaleY = s
            }
            .clip(RoundedCornerShape(rowW * 0.028f))
            .background(rowBackground)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = rowW * 0.019f, vertical = rowW * 0.016f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1.05f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                displayOp(op),
                color = lineColor,
                fontSize = (rowW.value * 0.059f).sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(rowW * 0.0875f)
            )
            Text(
                fmtMoney(value),
                color = lineColor,
                fontSize = (rowW.value * 0.0625f).sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.width(rowW * 0.039f))
        TapeNoteZone(
            note = note,
            isEditing = isEditing,
            editingNote = editingNote,
            focusRequester = focusRequester,
            palette = palette,
            fontSize = (rowW.value * 0.044f).sp,
            onNoteTextChange = onNoteTextChange,
            onNoteDone = onNoteDone
        )
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TapeTotalRow(
    line: HistoryEntity,
    totalValue: Double,
    isFinal: Boolean,
    palette: TapePalette,
    isEditing: Boolean,
    editingNote: String,
    focusRequester: FocusRequester,
    seenIds: MutableSet<String>,
    onNoteTextChange: (String) -> Unit,
    onNoteDone: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val note = CalTapeViewModel.splitNote(line.expression).second
    // A total row follows its value: negative shows "−", positive
    // shows "+", both in the text colour (black/white only).
    val negative = totalValue < 0
    val totalColor = palette.text
    // Entrance: a new total springs up into place, once per line.
    val entrance = remember(line.id) {
        Animatable(if (seenIds.contains(line.id)) 1f else 0f)
    }
    LaunchedEffect(line.id) {
        if (!seenIds.contains(line.id)) {
            seenIds.add(line.id)
            entrance.animateTo(
                1f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }
    val rowBackground by animateColorAsState(
        targetValue = if (isEditing) palette.keyFn.copy(alpha = 0.45f) else Color.Transparent,
        animationSpec = tween(220),
        label = "totalRowHighlight"
    )
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val rowW = maxWidth // BoxWithConstraintsScope theke dhore rakha - Row/Column content-e (RowScope) maxWidth direct cholena
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = entrance.value
                translationY = (1f - entrance.value) * 28f
                val s = 0.97f + 0.03f * entrance.value
                scaleX = s
                scaleY = s
            }
            .clip(RoundedCornerShape(rowW * 0.028f))
            .background(rowBackground)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = rowW * 0.019f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.05f)) {
            Spacer(modifier = Modifier.height(rowW * 0.019f))
            HorizontalDivider(color = palette.text.copy(alpha = 0.45f), thickness = 1.dp)
            if (isFinal) {
                Spacer(modifier = Modifier.height(rowW * 0.009f))
                HorizontalDivider(color = palette.text.copy(alpha = 0.45f), thickness = 1.dp)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = rowW * 0.019f, bottom = rowW * 0.006f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (negative) "−" else "+",
                    color = totalColor,
                    fontSize = (rowW.value * 0.059f).sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(rowW * 0.0875f)
                )
                Text(
                    fmtMoney(abs(totalValue)),
                    color = totalColor,
                    fontSize = (rowW.value * 0.066f).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(modifier = Modifier.width(rowW * 0.039f))
        TapeNoteZone(
            note = note,
            isEditing = isEditing,
            editingNote = editingNote,
            focusRequester = focusRequester,
            palette = palette,
            fontSize = (rowW.value * 0.044f).sp,
            onNoteTextChange = onNoteTextChange,
            onNoteDone = onNoteDone
        )
    }
    }
}

// Pretty display glyph for a stored ASCII operator.
private fun displayOp(op: String): String {
    return when (op) {
        "-" -> "−"
        "*" -> "×"
        "/" -> "÷"
        else -> op
    }
}

// The whole tape as plain text, notes included, in the reference
// format. Subtotal values are FOLDED from the lines, never read from
// stored snapshots, so edits are always reflected; a negative total
// gets a "-" sign:
//   +          5.00 hello
//   ---------------
//   +         11.00 toral
private fun buildTapeText(lines: List<HistoryEntity>): String {
    val sb = StringBuilder()
    var running = 0.0
    for (line in lines) {
        val (base, note) = CalTapeViewModel.splitNote(line.expression)
        val suffix = if (note.isNotEmpty()) " $note" else ""
        if (base == "TOTAL") {
            val sign = if (running < 0) "-" else "+"
            sb.append("-".repeat(15)).append('\n')
            sb.append(sign).append(' ').append(fmtMoney(abs(running)).padStart(13)).append(suffix).append('\n')
        } else {
            val op = CalTapeViewModel.normalizeOp(base.firstOrNull())
            val value = base.drop(1).toDoubleOrNull() ?: 0.0
            running = CalTapeViewModel.applyOperator(running, op, value)
            sb.append(op).append(' ').append(fmtMoney(value).padStart(13)).append(suffix).append('\n')
        }
    }
    return sb.toString().trimEnd()
}

// Show the in-progress entry with pretty operator glyphs and grouped
// numbers. The stored entry itself stays ASCII.
private fun displayEntry(raw: String): String {
    val pretty = raw.replace("*", "×").replace("/", "÷").replace("-", "−")
    return pretty.replace(Regex("[0-9]+(\\.[0-9]*)?")) { match ->
        val token = match.value
        val dot = token.indexOf('.')
        val intPart = if (dot >= 0) token.substring(0, dot) else token
        val rest = if (dot >= 0) token.substring(dot) else ""
        groupThousands(intPart) + rest
    }
}

private fun groupThousands(intPart: String): String {
    if (intPart.length <= 3) return intPart
    val sb = StringBuilder()
    var i = intPart.length % 3
    if (i > 0) sb.append(intPart, 0, i)
    while (i < intPart.length) {
        if (sb.isNotEmpty()) sb.append(',')
        sb.append(intPart, i, i + 3)
        i += 3
    }
    return sb.toString()
}

// Builds the tape PDF (built-in Android PdfDocument — no extra
// dependency) and publishes it to the PUBLIC Downloads/CalTape
// folder through MediaStore, named after the sheet, so any file
// manager can find it (the old app-private folder is hidden by
// Android). Returns true on success. The page is A4 (595 × 842 pt)
// and every line is drawn CENTERED on the page. Lines are ALL BLACK
// in the B/W version (the sign, not a colour, tells them apart);
// totals follow their value (folded live, like everywhere else);
// notes ride along.
private fun exportTapePdf(context: Context, sheetName: String, lines: List<HistoryEntity>): Boolean {
    var document: PdfDocument? = null
    return try {
        // (text, android colour) pairs, in tape order — all black
        // in the B/W version.
        val black = android.graphics.Color.BLACK
        val styled = mutableListOf<Pair<String, Int>>()
        var running = 0.0
        for (line in lines) {
            val (base, note) = CalTapeViewModel.splitNote(line.expression)
            val suffix = if (note.isNotEmpty()) " $note" else ""
            if (base == "TOTAL") {
                val negative = running < 0
                val sign = if (negative) "-" else "+"
                styled.add("-".repeat(15) to black)
                styled.add(sign + " " + fmtMoney(abs(running)).padStart(13) + suffix to black)
            } else {
                val op = CalTapeViewModel.normalizeOp(base.firstOrNull())
                val value = base.drop(1).toDoubleOrNull() ?: 0.0
                running = CalTapeViewModel.applyOperator(running, op, value)
                styled.add(op + " " + fmtMoney(value).padStart(13) + suffix to black)
            }
        }
        val dateText = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())

        val pageWidth = 595 // A4 at 72 pt/inch
        val pageHeight = 842
        val lineHeight = 19f
        val linesPerPage = ((pageHeight - 2 * 42f - 44) / lineHeight).toInt()

        val titlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 17f
            color = black
        }
        val dimPaint = Paint().apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            color = android.graphics.Color.GRAY
        }

        fun tapePaint(color: Int) = Paint().apply {
            typeface = Typeface.MONOSPACE
            textSize = 12.5f
            this.color = color
        }

        val blackPaint = tapePaint(black)

        document = PdfDocument()
        var pageNumber = 1
        var index = 0
        while (true) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            // Every text is drawn centered on the page.
            fun drawCentered(text: String, y: Float, paint: Paint) {
                val x = (pageWidth - paint.measureText(text)) / 2f
                canvas.drawText(text, x, y, paint)
            }

            var y = 42f + 6f
            if (pageNumber == 1) {
                drawCentered("CalTape — $sheetName", y, titlePaint)
                y += 20f
                drawCentered(dateText, y, dimPaint)
                y += 26f
            }
            var drawn = 0
            while (index < styled.size && drawn < linesPerPage) {
                val (text, color) = styled[index]
                val paint = blackPaint
                drawCentered(text, y, paint)
                y += lineHeight
                index++
                drawn++
            }
            document.finishPage(page)
            if (index >= styled.size) break
            pageNumber++
        }

        val safeName = sheetName.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val fileName = "CalTape-$safeName.pdf"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Public Downloads/CalTape via MediaStore — visible in
            // every file manager, no permission needed. An older
            // export of the same sheet is removed first, so saving
            // again overwrites instead of piling up "(1)" copies.
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val relativePath = Environment.DIRECTORY_DOWNLOADS + "/CalTape"
            try {
                resolver.delete(
                    collection,
                    "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                    arrayOf(fileName, "$relativePath/")
                )
            } catch (e: Exception) {
                // Nothing to delete — fine.
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            val uri = resolver.insert(collection, values) ?: return false
            val stream = resolver.openOutputStream(uri) ?: return false
            stream.use { out -> document.writeTo(out) }
        } else {
            // Older Android: fall back to the app's Documents folder.
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { out -> document.writeTo(out) }
        }
        document.close()
        true
    } catch (e: Exception) {
        try {
            document?.close()
        } catch (ignored: Exception) {
        }
        false
    }
}
