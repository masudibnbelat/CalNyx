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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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

// CalTape worksheet, matched to the reference app in both themes:
// running-total tape organised in sheets (side drawer, top-left menu
// button; sheets can be added, renamed and deleted, and the PDF
// exports under the sheet's current name into Downloads/CalTape).
// Each tape row has two zones — the calculation (operator + amount)
// in the left half and a TEXT NOTE in the right half. Tap a line to
// edit its note inline; LONG-PRESS a line to edit the line itself
// (operator, amount, note, or delete it). Line colours follow the
// operator: + and × are GREEN, − and ÷ are RED (symbol and number
// alike), on screen and in the exported PDF. A total row follows its
// value: positive totals are "+"/green, negative totals are "−"/red.
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
    bg = Color(0xFF0F172A),
    keyNum = Color(0xFF1E293B),
    keyFn = Color(0xFF324154),
    keyBack = Color(0xFF465568),
    keyBorder = null,
    strip = Color(0xFF1E293B),
    drawerBg = Color(0xFF131E30),
    text = Color(0xFFF8FAFC),
    dim = Color(0xFF94A3B8),
    green = Color(0xFF30D593),
    red = Color(0xFFF26D6D),
    live = Color(0xFF7CC4EC),
    enter = Color(0xFF2F9BD9)
)

private val LightTapePalette = TapePalette(
    bg = Color(0xFFF4F8FB),
    keyNum = Color(0xFFFFFFFF),
    keyFn = Color(0xFFDFE7EF),
    keyBack = Color(0xFFA9BACB),
    keyBorder = Color(0xFFD8E1EA),
    strip = Color(0xFFDFE7EF),
    drawerBg = Color(0xFFFFFFFF),
    text = Color(0xFF15222E),
    dim = Color(0xFF71879C),
    green = Color(0xFF0AA06E),
    red = Color(0xFFE5484D),
    live = Color(0xFF1D71A0),
    enter = Color(0xFF1D71A0)
)

private val TapeAc = Color(0xFFF39E0D)
private val TapeAcText = Color(0xFF201503)

private val moneyFormat = DecimalFormat("#,##0.00")

private fun fmtMoney(value: Double): String = moneyFormat.format(value)

// The one colour rule for the whole tape: + and × green, − and ÷ red.
private fun opColor(op: String, palette: TapePalette): Color {
    return when (op) {
        "+", "*" -> palette.green
        "-", "/" -> palette.red
        else -> palette.dim
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CalTapeCalculatorScreen(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
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
    val topInset = maxOf(statusTop, 24.dp)

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
                Text(
                    "Rename sheet",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
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
                    Text("Save", color = palette.enter, fontWeight = FontWeight.SemiBold)
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    if (isTotalLine) "Edit subtotal line" else "Edit line",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.text
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (!isTotalLine) {
                    // Operator chips, coloured by the tape rule.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("+", "-", "*", "/").forEach { op ->
                            val selected = editOp == op
                            val chipColor = opColor(op, palette)
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) chipColor else palette.keyFn)
                                    .clickable { editOp = op },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    displayOp(op),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) Color.White else palette.text
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))

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
                    Spacer(modifier = Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = editNote,
                    onValueChange = { editNote = it },
                    singleLine = true,
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(18.dp))

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
                            tint = palette.red,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Delete line",
                            color = palette.red,
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
                        Text("Save", color = palette.enter, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = palette.drawerBg,
                drawerContentColor = palette.text,
                modifier = Modifier.width(280.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Text(
                        "Sheets",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.text
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    sheets.forEach { sheet ->
                        val selected = sheet.id == currentSheetId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) palette.keyFn else Color.Transparent)
                                .clickable {
                                    viewModel.switchSheet(sheet.id)
                                    scope.launch { drawerState.close() }
                                }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                tint = if (selected) palette.enter else palette.dim,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                sheet.name,
                                fontSize = 16.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = palette.text,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    renameTarget = sheet
                                    renameText = sheet.name
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Rename sheet",
                                    tint = palette.dim,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (sheets.size > 1) {
                                IconButton(
                                    onClick = { viewModel.deleteSheet(sheet.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete sheet",
                                        tint = palette.dim,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.addSheet()
                                scope.launch { drawerState.close() }
                            }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = palette.enter,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "New sheet",
                            fontSize = 16.sp,
                            color = palette.enter,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    HorizontalDivider(color = palette.dim.copy(alpha = 0.25f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
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
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = if (lines.isNotEmpty()) palette.red else palette.dim.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Export PDF",
                            fontSize = 16.sp,
                            color = if (lines.isNotEmpty()) palette.text else palette.dim.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        "Saved to Downloads/CalTape",
                        fontSize = 11.sp,
                        color = palette.dim,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.bg)
                .padding(top = topInset)
                .navigationBarsPadding()
        ) {
            // ----- Top row: side-menu button (left) + current sheet name -----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "Sheets",
                        tint = palette.text,
                        modifier = Modifier.size(26.dp)
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
                        fontSize = 17.sp,
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
                    .padding(horizontal = 20.dp)
            ) {
                if (lines.isEmpty() && input.isEmpty() && !showPendingOp) {
                    Text(
                        "Start typing — your worksheet\nwill appear here.",
                        color = palette.dim.copy(alpha = 0.7f),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        state = tapeListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 44.dp)
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
                                        .padding(horizontal = 6.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1.05f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            displayOp(pendingOp),
                                            color = lineColor,
                                            fontSize = 19.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.width(28.dp)
                                        )
                                        Text(
                                            displayEntry(input),
                                            color = lineColor,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = FontFamily.Monospace,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            "▏",
                                            color = lineColor.copy(alpha = cursorAlpha),
                                            fontSize = 20.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
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
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.keyFn)
                ) {
                    Icon(
                        if (tapeExpanded) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                        contentDescription = if (tapeExpanded) "Show keypad" else "Expand tape",
                        tint = palette.text,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Feedback pill (copy / PDF) — fades in above the strip.
                // Wrapped in a Column: an AnimatedVisibility placed
                // directly inside a Box that sits inside a Column picks
                // the ColumnScope overload and fails to compile.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp),
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
                                .clip(RoundedCornerShape(20.dp))
                                .background(palette.green)
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                feedback ?: "",
                                color = Color.White,
                                fontSize = 13.sp,
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
                            .padding(horizontal = 8.dp, vertical = 4.dp),
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
                                modifier = Modifier.size(21.dp)
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
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            fmtMoney(shownTotal),
                            color = palette.text,
                            fontSize = 19.sp,
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
                                        copied -> palette.green
                                        lines.isNotEmpty() -> palette.enter
                                        else -> palette.dim.copy(alpha = 0.4f)
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // ----- Keypad: plain tape keys only -----
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TapeRow(
                            index = 0,
                            introDone = keypadIntroDone,
                            onIntroDone = { keypadIntroDone = true }
                        ) {
                            TapeKey("AC", TapeAc, TapeAcText, palette, Modifier.weight(1f)) { viewModel.onKey("AC") }
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
                            onIntroDone = { keypadIntroDone = true }
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
                            onIntroDone = { keypadIntroDone = true }
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
                            onIntroDone = { keypadIntroDone = true }
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
                            onIntroDone = { keypadIntroDone = true }
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
                                palette.enter,
                                Color.White,
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

@Composable
private fun TapeRow(
    index: Int,
    introDone: Boolean,
    onIntroDone: () -> Unit,
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
            .graphicsLayer {
                alpha = progress.value
                translationY = (1f - progress.value) * 30f
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
    Box(
        modifier = modifier
            .height(56.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .then(
                if (borderColor != null) {
                    Modifier.border(1.dp, borderColor, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // White flash that blooms while the key is pressed.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.White.copy(alpha = flash))
        )
        if (label == "⌫") {
            Icon(
                Icons.Default.Backspace,
                contentDescription = "Backspace",
                tint = textColor,
                modifier = Modifier.size(26.dp)
            )
        } else {
            Text(
                // The bracket key shows a space inside: "( )".
                if (label == "()") "( )" else label,
                color = textColor,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
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
    onNoteTextChange: (String) -> Unit,
    onNoteDone: () -> Unit
) {
    if (isEditing) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.keyFn)
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            BasicTextField(
                value = editingNote,
                onValueChange = onNoteTextChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = palette.text,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(palette.enter),
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
                                fontSize = 14.sp
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
            fontSize = 14.sp,
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
            .clip(RoundedCornerShape(10.dp))
            .background(rowBackground)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1.05f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                displayOp(op),
                color = lineColor,
                fontSize = 19.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(28.dp)
            )
            Text(
                fmtMoney(value),
                color = lineColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        TapeNoteZone(
            note = note,
            isEditing = isEditing,
            editingNote = editingNote,
            focusRequester = focusRequester,
            palette = palette,
            onNoteTextChange = onNoteTextChange,
            onNoteDone = onNoteDone
        )
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
    // A total row follows its value: negative shows "−" in red,
    // positive shows "+" in green.
    val negative = totalValue < 0
    val totalColor = if (negative) palette.red else palette.green
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
            .clip(RoundedCornerShape(10.dp))
            .background(rowBackground)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.05f)) {
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = palette.text.copy(alpha = 0.45f), thickness = 1.dp)
            if (isFinal) {
                Spacer(modifier = Modifier.height(3.dp))
                HorizontalDivider(color = palette.text.copy(alpha = 0.45f), thickness = 1.dp)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (negative) "−" else "+",
                    color = totalColor,
                    fontSize = 19.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(28.dp)
                )
                Text(
                    fmtMoney(abs(totalValue)),
                    color = totalColor,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        TapeNoteZone(
            note = note,
            isEditing = isEditing,
            editingNote = editingNote,
            focusRequester = focusRequester,
            palette = palette,
            onNoteTextChange = onNoteTextChange,
            onNoteDone = onNoteDone
        )
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
// and every line is drawn CENTERED on the page. Lines are coloured
// like the screen: + and × green, − and ÷ red, totals follow their
// value (folded live, like everywhere else); notes ride along.
private fun exportTapePdf(context: Context, sheetName: String, lines: List<HistoryEntity>): Boolean {
    var document: PdfDocument? = null
    return try {
        // (text, android colour) pairs, in tape order.
        val green = android.graphics.Color.rgb(10, 160, 110)
        val red = android.graphics.Color.rgb(229, 72, 77)
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
                styled.add(sign + " " + fmtMoney(abs(running)).padStart(13) + suffix to if (negative) red else green)
            } else {
                val op = CalTapeViewModel.normalizeOp(base.firstOrNull())
                val value = base.drop(1).toDoubleOrNull() ?: 0.0
                running = CalTapeViewModel.applyOperator(running, op, value)
                val color = if (op == "-" || op == "/") red else green
                styled.add(op + " " + fmtMoney(value).padStart(13) + suffix to color)
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

        val greenPaint = tapePaint(green)
        val redPaint = tapePaint(red)
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
                val paint = when (color) {
                    green -> greenPaint
                    red -> redPaint
                    else -> blackPaint
                }
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
