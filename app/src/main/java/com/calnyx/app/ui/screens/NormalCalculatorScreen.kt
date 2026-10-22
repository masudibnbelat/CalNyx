package com.calnyx.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calnyx.app.viewmodel.CalculatorViewModel
import com.calnyx.app.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class HistoryPanelState { CLOSED, HALF, FULL }

@Composable
fun NormalCalculatorScreen(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onOpenHistory: () -> Unit,
    onHistoryVisibilityChange: (Boolean) -> Unit = {},
    viewModel: CalculatorViewModel = viewModel(),
    historyViewModel: HistoryViewModel = viewModel()
) {
    // darkTheme kept for the NavGraph call; onToggleTheme / onOpenHistory
    // are kept for the same call — the floating top bar lives in NavGraph
    // and is hidden via onHistoryVisibilityChange while history is open.
    val expression by viewModel.expression.collectAsState()
    val cursorPosition by viewModel.cursorPosition.collectAsState()
    val result by viewModel.result.collectAsState()
    val finalResult by viewModel.finalResult.collectAsState()
    val isDegrees by viewModel.isDegrees.collectAsState()
    val history by historyViewModel.history.collectAsState()
    var scientificExpanded by remember { mutableStateOf(false) }
    var inverseMode by remember { mutableStateOf(false) }
    var panelState by remember { mutableStateOf(HistoryPanelState.CLOSED) }
    val currentPanelState by rememberUpdatedState(panelState)

    // Text selection over the expression, in RAW character indices.
    // Long-press selects the token under the finger; the two handles
    // drag to extend it. While a selection is live, the next key
    // replaces it — exactly like a text field.
    var selActive by remember { mutableStateOf(false) }
    var selStart by remember { mutableIntStateOf(0) }
    var selEnd by remember { mutableIntStateOf(0) }

    fun handleKeyLabel(label: String) {
        if (selActive && selEnd > selStart) {
            val s = selStart
            val e = selEnd
            selActive = false
            if (label == "⌫") {
                viewModel.deleteRange(s, e)
                return
            }
            if (label != "=") {
                viewModel.deleteRange(s, e)
            }
        } else if (selActive) {
            selActive = false
        }
        viewModel.onButtonClick(label)
    }

    // A selection never outlives the expression it points into.
    LaunchedEffect(expression) {
        if (selActive && (selStart > expression.length || selEnd > expression.length)) {
            selActive = false
        }
    }
    LaunchedEffect(finalResult) {
        if (finalResult != null) selActive = false
    }

    val historyOpen = panelState == HistoryPanelState.HALF
    val historyFull = panelState == HistoryPanelState.FULL
    val compactKeypad = scientificExpanded || historyOpen

    // Fonts shrink together with the heights: HALF history squeezes
    // the most, the scientific panel a little less.
    val displayScale = when {
        historyOpen -> 0.55f
        scientificExpanded -> 0.78f
        else -> 1f
    }

    // SMOOTHNESS FIX: the display height and the key font size are
    // ANIMATED values now, not instant mode switches. Previously the
    // display jumped between weight(1f) and a fixed 88/116dp, the keys
    // jumped between aspectRatio circles and fillMaxHeight pills, and
    // three different springs (panel visibility, display
    // animateContentSize, top spacer) fought over the same height —
    // that fight is what made the keys grow, then shrink, while the
    // history panel was being pulled open.
    // REBALANCE: the display weight is SMALL (0.30) — the display
    // takes only the space it needs, there is no big empty void at
    // the top, and everything else goes to the keypad so the keys
    // stay as large as the screen width allows.
    val displayWeightAnim by animateFloatAsState(
        targetValue = when {
            historyOpen -> 0.20f
            scientificExpanded -> 0.32f
            else -> 0.30f
        },
        animationSpec = spring(
            dampingRatio = 0.9f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "displayWeight"
    )
    val fontScaleAnim by animateFloatAsState(
        targetValue = when {
            historyOpen -> 0.72f
            scientificExpanded -> 0.85f
            else -> 1f
        },
        animationSpec = tween(300),
        label = "keyFontScale"
    )

    val groupedHistory = remember(history) {
        history.groupBy { dayLabel(it.createdAt) }
    }
    val groupedEntries = remember(groupedHistory) { groupedHistory.entries.toList() }

    val keypadAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        keypadAnim.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio = 0.8f,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    val sectionAnim = remember { Animatable(0f) }
    LaunchedEffect(panelState) {
        onHistoryVisibilityChange(panelState != HistoryPanelState.CLOSED)
        if (panelState != HistoryPanelState.CLOSED) {
            // Opening history collapses the scientific panel, so the
            // keypad can never be crushed to zero height.
            scientificExpanded = false
            sectionAnim.snapTo(0f)
            sectionAnim.animateTo(
                1f,
                animationSpec = spring(
                    dampingRatio = 0.9f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    // Scientific rows stagger in when the panel opens.
    val sciAnim = remember { Animatable(0f) }
    LaunchedEffect(scientificExpanded) {
        if (scientificExpanded) {
            sciAnim.snapTo(0f)
            sciAnim.animateTo(
                1f,
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    // While history is open the top bar is hidden, so its clearance
    // space is animated away and given to the history + keypad.
    // Same spring as the panel animations, so everything moves as
    // one system instead of three springs fighting.
    val topSpacerHeight by animateDpAsState(
        targetValue = if (panelState == HistoryPanelState.CLOSED) 56.dp else 6.dp,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
        label = "topSpacerHeight"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp)
        ) {
            // Clearance for the floating top bar drawn by NavGraph
            Spacer(modifier = Modifier.height(topSpacerHeight))

            // History area — part of the main layout, NOT an overlay.
            // In HALF it wraps its content and stays small (list capped
            // at 200dp, then it scrolls); in FULL it fills the screen.
            AnimatedVisibility(
                visible = panelState != HistoryPanelState.CLOSED,
                enter = expandVertically(
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(tween(300)),
                exit = shrinkVertically(
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
                ) + fadeOut(tween(250)),
                modifier = Modifier.then(
                    if (historyFull) Modifier.weight(1f)
                    else Modifier
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (historyFull) Modifier.fillMaxHeight() else Modifier)
                        .graphicsLayer {
                            alpha = sectionAnim.value
                            translationY = (1f - sectionAnim.value) * -30f
                        }
                ) {
                    if (history.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (historyFull) Modifier.weight(1f)
                                    else Modifier.height(90.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No history yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (historyFull) Modifier.weight(1f)
                                    else Modifier.heightIn(max = 200.dp)
                                )
                                .padding(horizontal = 20.dp)
                        ) {
                            groupedEntries.forEachIndexed { groupIndex, (day, dayItems) ->
                                item(key = "panel-header-$day") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 2.dp, bottom = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = day,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        // Clear button right beside the
                                        // first group header ("Today")
                                        if (groupIndex == 0) {
                                            Spacer(modifier = Modifier.weight(1f))
                                            TextButton(
                                                onClick = { historyViewModel.clearAll() }
                                            ) {
                                                Text(
                                                    text = "Clear",
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                                itemsIndexed(dayItems) { itemIndex, item ->
                                    val itemAnim = remember(item.id) { Animatable(0f) }
                                    LaunchedEffect(item.id) {
                                        itemAnim.animateTo(
                                            1f,
                                            animationSpec = tween(
                                                durationMillis = 350,
                                                delayMillis = minOf(itemIndex * 40 + groupIndex * 30, 320)
                                            )
                                        )
                                    }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selActive = false
                                                viewModel.onHistoryItemClick(item.result)
                                                panelState = HistoryPanelState.CLOSED
                                            }
                                            .padding(vertical = if (historyFull) 10.dp else 8.dp)
                                            .graphicsLayer {
                                                alpha = itemAnim.value
                                                translationX = (1f - itemAnim.value) * 60f
                                            },
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            text = formatForDisplay(item.expression),
                                            fontSize = if (historyFull) 20.sp else 18.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.End
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = formatForDisplay(item.result),
                                            fontSize = if (historyFull) 34.sp else 28.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.End
                                        )
                                    }
                                }
                                item(key = "panel-divider-$day") {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Bottom bar of the history area — just a divider and
                    // the drag handle pill (no text):
                    // drag down: HALF -> FULL | drag up: FULL -> HALF -> CLOSED
                    // tap: HALF -> FULL, FULL -> CLOSED
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                var totalDrag = 0f
                                var stepped = false
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        totalDrag = 0f
                                        stepped = false
                                    },
                                    onVerticalDrag = { _, dragAmount ->
                                        totalDrag += dragAmount
                                        if (!stepped) {
                                            when {
                                                totalDrag > 40f &&
                                                        currentPanelState == HistoryPanelState.HALF -> {
                                                    panelState = HistoryPanelState.FULL
                                                    stepped = true
                                                }

                                                totalDrag < -40f &&
                                                        currentPanelState == HistoryPanelState.FULL -> {
                                                    panelState = HistoryPanelState.HALF
                                                    stepped = true
                                                }

                                                totalDrag < -40f &&
                                                        currentPanelState == HistoryPanelState.HALF -> {
                                                    panelState = HistoryPanelState.CLOSED
                                                    stepped = true
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                            .clickable {
                                panelState = if (panelState == HistoryPanelState.HALF) {
                                    HistoryPanelState.FULL
                                } else {
                                    HistoryPanelState.CLOSED
                                }
                            }
                    ) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(56.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                    )
                            )
                        }
                    }
                }
            }

            // Calculator area (display + keypad) — collapses away in FULL.
            // A vertical drag ANYWHERE in this area (display, buttons,
            // gaps) steps the history panel: down opens it further,
            // up closes it. Taps still reach the buttons untouched.
            AnimatedVisibility(
                visible = panelState != HistoryPanelState.FULL,
                enter = expandVertically(
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(tween(300)),
                exit = shrinkVertically(
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
                ) + fadeOut(tween(250)),
                modifier = Modifier.then(
                    when (panelState) {
                        HistoryPanelState.CLOSED -> Modifier.weight(1f)
                        HistoryPanelState.HALF -> Modifier.weight(1f)
                        HistoryPanelState.FULL -> Modifier
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectPanelDragGestures(
                                onStepDown = {
                                    panelState = when (currentPanelState) {
                                        HistoryPanelState.CLOSED -> HistoryPanelState.HALF
                                        HistoryPanelState.HALF -> HistoryPanelState.FULL
                                        HistoryPanelState.FULL -> HistoryPanelState.FULL
                                    }
                                },
                                onStepUp = {
                                    if (currentPanelState == HistoryPanelState.HALF) {
                                        panelState = HistoryPanelState.CLOSED
                                    }
                                }
                            )
                        }
                ) {

                    // Display. Its height is an ANIMATED layout weight
                    // now (displayWeightAnim) — as the weight glides,
                    // the display and the keypad below resize together,
                    // continuously, in one motion. No fixed heights, no
                    // animateContentSize fighting the panel spring.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(displayWeightAnim)
                            .padding(horizontal = 10.dp),
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.End
                    ) {
                        AnimatedContent(
                            targetState = finalResult,
                            transitionSpec = {
                                (fadeIn(tween(250)) +
                                        slideInVertically(
                                            animationSpec = tween(250),
                                            initialOffsetY = { it / 3 }
                                        )) togetherWith fadeOut(tween(180))
                            },
                            label = "displayMode",
                            modifier = Modifier.fillMaxWidth()
                        ) { final ->
                            if (final != null) {
                                // Completed calculation: only the answer, big —
                                // shrunk to fit the width, like the expression
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    val formattedFinal = remember(final) { formatForDisplay(final) }
                                    var finalWidthPx by remember { mutableIntStateOf(0) }
                                    val finalMeasurer = rememberTextMeasurer()
                                    val finalFitSp = remember(formattedFinal, finalWidthPx, displayScale) {
                                        measureFitFontSize(
                                            finalMeasurer,
                                            formattedFinal,
                                            finalWidthPx,
                                            maxSp = 88f * displayScale,
                                            minSp = 20f * displayScale
                                        )
                                    }
                                    val finalFontSize by animateFloatAsState(
                                        targetValue = finalFitSp,
                                        animationSpec = tween(320),
                                        label = "finalFontSize"
                                    )
                                    val finalScrollState = rememberScrollState()
                                    LaunchedEffect(final) {
                                        finalScrollState.animateScrollTo(finalScrollState.maxValue)
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onSizeChanged { finalWidthPx = it.width }
                                            .horizontalScroll(finalScrollState),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Text(
                                            text = formattedFinal,
                                            fontSize = finalFontSize.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.End,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(if (compactKeypad) 6.dp else 12.dp))
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    // Tap-to-edit: the visible text is
                                    // formatted (commas, ×, √) while the
                                    // stored expression is raw ASCII.
                                    // formatWithMap keeps a boundary map
                                    // between the two, so a tap lands the
                                    // caret on the exact character. The
                                    // caret itself is drawn by the Text,
                                    // inside its own draw pass (see below).
                                    val formatted = remember(expression) { formatWithMap(expression) }
                                    var expressionLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
                                    val currentLayout by rememberUpdatedState(expressionLayout)
                                    val currentFormatted by rememberUpdatedState(formatted)
                                    val currentExpression by rememberUpdatedState(expression)

                                    // Google-style shrink-to-fit: the
                                    // font is exactly as big as the
                                    // whole expression allows — measured,
                                    // not guessed from the length — and
                                    // only shrinks to the 20sp floor
                                    // before scrolling starts.
                                    var displayWidthPx by remember { mutableIntStateOf(0) }
                                    val textMeasurer = rememberTextMeasurer()
                                    val expressionFitSp = remember(formatted.text, displayWidthPx, displayScale) {
                                        measureFitFontSize(
                                            textMeasurer,
                                            formatted.text,
                                            displayWidthPx,
                                            maxSp = 80f * displayScale,
                                            minSp = 20f * displayScale
                                        )
                                    }
                                    val animatedFontSize by animateFloatAsState(
                                        targetValue = expressionFitSp,
                                        animationSpec = tween(220),
                                        label = "expressionFontSize"
                                    )

                                    // Filled by the Text's onTextLayout in
                                    // its layout phase; read by the Text's
                                    // own draw phase in the same frame.
                                    val caretHolder = remember { CaretLayoutHolder() }

                                    // The caret blink — a plain continuous
                                    // blink, read inside the draw pass.
                                    val caretTransition = rememberInfiniteTransition(label = "cursor")
                                    val caretAlpha by caretTransition.animateFloat(
                                        initialValue = 1f,
                                        targetValue = 0f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(530),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "cursorAlpha"
                                    )
                                    val caretColor = MaterialTheme.colorScheme.primary
                                    val clipboardManager = LocalClipboardManager.current

                                    val scrollState = rememberScrollState()
                                    // Keep the caret visible. LAG FIX:
                                    // following the end while typing is
                                    // an INSTANT scrollTo now — a 120ms
                                    // animateScrollTo per keystroke
                                    // queued up behind fast typing and
                                    // made the whole screen feel laggy.
                                    // Only a caret placed mid-expression
                                    // by a tap still animates into view.
                                    LaunchedEffect(expression, cursorPosition) {
                                        val lr = expressionLayout
                                        if (expression.isEmpty()) {
                                            // nothing to scroll
                                        } else if (cursorPosition >= expression.length || lr == null) {
                                            scrollState.scrollTo(scrollState.maxValue)
                                        } else {
                                            val safeOffset = formatted.rawToFmt[
                                                cursorPosition.coerceIn(0, expression.length)
                                            ].coerceAtMost(lr.layoutInput.text.length)
                                            val cx = lr.getHorizontalPosition(safeOffset, true)
                                            val view = scrollState.viewportSize
                                            when {
                                                cx < scrollState.value -> scrollState.animateScrollTo(
                                                    cx.toInt().coerceAtLeast(0),
                                                    animationSpec = tween(120)
                                                )

                                                view > 0 && cx > scrollState.value + view - 24 ->
                                                    scrollState.animateScrollTo(
                                                        (cx - view + 24).toInt(),
                                                        animationSpec = tween(120)
                                                    )
                                            }
                                        }
                                    }

                                    // Selection action bar — Copy takes
                                    // the FORMATTED slice (commas and
                                    // glyphs), so it pastes prettily
                                    // anywhere; paste is sanitised back
                                    // by the ViewModel.
                                    AnimatedVisibility(
                                        visible = selActive,
                                        enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                                        exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(percent = 50))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .padding(horizontal = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (selEnd > selStart) {
                                                    TextButton(onClick = {
                                                        val from = formatted.rawToFmt[
                                                            selStart.coerceIn(0, expression.length)
                                                        ]
                                                        val to = formatted.rawToFmt[
                                                            selEnd.coerceIn(0, expression.length)
                                                        ]
                                                        clipboardManager.setText(
                                                            AnnotatedString(
                                                                formatted.text.substring(
                                                                    from,
                                                                    to.coerceAtMost(formatted.text.length)
                                                                )
                                                            )
                                                        )
                                                    }) { Text("Copy", fontSize = 14.sp) }
                                                    TextButton(onClick = {
                                                        val from = formatted.rawToFmt[
                                                            selStart.coerceIn(0, expression.length)
                                                        ]
                                                        val to = formatted.rawToFmt[
                                                            selEnd.coerceIn(0, expression.length)
                                                        ]
                                                        clipboardManager.setText(
                                                            AnnotatedString(
                                                                formatted.text.substring(
                                                                    from,
                                                                    to.coerceAtMost(formatted.text.length)
                                                                )
                                                            )
                                                        )
                                                        viewModel.deleteRange(selStart, selEnd)
                                                        selActive = false
                                                    }) { Text("Cut", fontSize = 14.sp) }
                                                }
                                                TextButton(onClick = {
                                                    val clip = clipboardManager.getText()?.text
                                                    if (!clip.isNullOrEmpty()) {
                                                        if (selEnd > selStart) {
                                                            viewModel.deleteRange(selStart, selEnd)
                                                        }
                                                        viewModel.pasteText(clip)
                                                        selActive = false
                                                    }
                                                }) { Text("Paste", fontSize = 14.sp) }
                                                if (expression.isNotEmpty()) {
                                                    TextButton(onClick = {
                                                        selStart = 0
                                                        selEnd = expression.length
                                                    }) { Text("All", fontSize = 14.sp) }
                                                }
                                                TextButton(onClick = { selActive = false }) {
                                                    Text("Done", fontSize = 14.sp)
                                                }
                                            }
                                        }
                                    }
                                    if (selActive) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }

                                    // Single line only — never wraps, end stays visible
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onSizeChanged { displayWidthPx = it.width }
                                            .horizontalScroll(scrollState, enabled = !selActive),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Box {
                                            Text(
                                                text = formatted.text,
                                                fontSize = animatedFontSize.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = TextAlign.End,
                                                maxLines = 1,
                                                softWrap = false,
                                                onTextLayout = {
                                                    expressionLayout = it
                                                    caretHolder.layout = it
                                                },
                                                modifier = Modifier
                                                    .widthIn(min = 56.dp)
                                                    .pointerInput(Unit) {
                                                        detectTapGestures(
                                                            onTap = { offset ->
                                                                val lr = currentLayout
                                                                    ?: return@detectTapGestures
                                                                val fmt = currentFormatted
                                                                selActive = false
                                                                val fmtOffset = lr.getOffsetForPosition(offset)
                                                                    .coerceIn(0, fmt.text.length)
                                                                viewModel.setCursorPosition(fmt.fmtToRaw[fmtOffset])
                                                            },
                                                            onLongPress = { offset ->
                                                                val lr = currentLayout
                                                                    ?: return@detectTapGestures
                                                                val fmt = currentFormatted
                                                                val rawExpr = currentExpression
                                                                if (rawExpr.isEmpty()) {
                                                                    // Nothing to select —
                                                                    // still open the bar,
                                                                    // for Paste
                                                                    selStart = 0
                                                                    selEnd = 0
                                                                    selActive = true
                                                                } else {
                                                                    val fmtOffset = lr.getOffsetForPosition(offset)
                                                                        .coerceIn(0, fmt.text.length)
                                                                    val rawBoundary = fmt.fmtToRaw[fmtOffset]
                                                                    val charIndex = rawBoundary
                                                                        .coerceIn(0, rawExpr.length - 1)
                                                                    val range = tokenRangeAt(rawExpr, charIndex)
                                                                    selStart = range.first
                                                                    selEnd = range.second
                                                                    selActive = true
                                                                }
                                                            }
                                                        )
                                                    }
                                                    .drawWithContent {
                                                        drawContent()
                                                        // Selection highlight and
                                                        // caret, drawn by the Text
                                                        // itself in the same frame
                                                        // whose layout is in the
                                                        // holder — always in sync
                                                        // with the glyphs. The caret
                                                        // is BIGGER now (4dp wide,
                                                        // 95% of the line height):
                                                        // the old 3dp / 88% read
                                                        // as too small.
                                                        val lr = caretHolder.layout
                                                        if (lr != null &&
                                                            lr.layoutInput.text.text == formatted.text
                                                        ) {
                                                            if (selActive && selEnd > selStart) {
                                                                val sx = lr.getHorizontalPosition(
                                                                    formatted.rawToFmt[
                                                                        selStart.coerceIn(0, expression.length)
                                                                    ],
                                                                    true
                                                                )
                                                                val ex = lr.getHorizontalPosition(
                                                                    formatted.rawToFmt[
                                                                        selEnd.coerceIn(0, expression.length)
                                                                    ],
                                                                    true
                                                                )
                                                                drawRect(
                                                                    color = caretColor.copy(alpha = 0.30f),
                                                                    topLeft = Offset(sx, 0f),
                                                                    size = Size(ex - sx, size.height)
                                                                )
                                                            } else if (!selActive) {
                                                                val caretOffset = formatted.rawToFmt[
                                                                    cursorPosition.coerceIn(0, expression.length)
                                                                ]
                                                                val cx = if (formatted.text.isEmpty()) {
                                                                    0f
                                                                } else {
                                                                    lr.getHorizontalPosition(caretOffset, true)
                                                                }
                                                                val caretHeight = size.height * 0.95f
                                                                drawRect(
                                                                    color = caretColor,
                                                                    topLeft = Offset(
                                                                        cx - 2.dp.toPx(),
                                                                        (size.height - caretHeight) / 2f
                                                                    ),
                                                                    size = Size(4.dp.toPx(), caretHeight),
                                                                    alpha = caretAlpha
                                                                )
                                                            }
                                                        }
                                                    }
                                            )

                                            // Drag handles at both ends of
                                            // the selection
                                            if (selActive && formatted.text.isNotEmpty()) {
                                                val lr = expressionLayout
                                                if (lr != null &&
                                                    lr.layoutInput.text.text == formatted.text
                                                ) {
                                                    val startX = lr.getHorizontalPosition(
                                                        formatted.rawToFmt[
                                                            selStart.coerceIn(0, expression.length)
                                                        ],
                                                        true
                                                    )
                                                    val endX = lr.getHorizontalPosition(
                                                        formatted.rawToFmt[
                                                            selEnd.coerceIn(0, expression.length)
                                                        ],
                                                        true
                                                    )
                                                    SelectionHandle(
                                                        currentX = startX,
                                                        modifier = Modifier.offset {
                                                            IntOffset(
                                                                (startX - 10.dp.toPx()).roundToInt(),
                                                                lr.size.height - 10.dp.toPx().roundToInt()
                                                            )
                                                        }
                                                    ) { targetX ->
                                                        val fmtOffset = lr.getOffsetForPosition(
                                                            Offset(
                                                                targetX.coerceAtLeast(0f),
                                                                lr.size.height / 2f
                                                            )
                                                        ).coerceIn(0, formatted.text.length)
                                                        selStart = formatted.fmtToRaw[fmtOffset]
                                                            .coerceIn(0, selEnd)
                                                    }
                                                    SelectionHandle(
                                                        currentX = endX,
                                                        modifier = Modifier.offset {
                                                            IntOffset(
                                                                (endX - 10.dp.toPx()).roundToInt(),
                                                                lr.size.height - 10.dp.toPx().roundToInt()
                                                            )
                                                        }
                                                    ) { targetX ->
                                                        val fmtOffset = lr.getOffsetForPosition(
                                                            Offset(
                                                                targetX.coerceAtLeast(0f),
                                                                lr.size.height / 2f
                                                            )
                                                        ).coerceIn(0, formatted.text.length)
                                                        selEnd = formatted.fmtToRaw[fmtOffset]
                                                            .coerceIn(selStart, expression.length)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Live result preview (peach) — slides
                                    // up into place as the value changes.
                                    // Its font follows the CONTINUOUS
                                    // fontScaleAnim, so it glides with the
                                    // rest of the layout instead of jumping.
                                    AnimatedContent(
                                        targetState = result,
                                        transitionSpec = {
                                            (slideInVertically(
                                                animationSpec = tween(180),
                                                initialOffsetY = { it / 2 }
                                            ) + fadeIn(tween(180))) togetherWith
                                                    (slideOutVertically(
                                                        animationSpec = tween(140),
                                                        targetOffsetY = { -it / 2 }
                                                    ) + fadeOut(tween(140)))
                                        },
                                        label = "resultPreview",
                                        modifier = Modifier.fillMaxWidth()
                                    ) { currentResult ->
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            if (currentResult != "0" && expression.isNotEmpty()) {
                                                Text(
                                                    text = formatForDisplay(currentResult),
                                                    fontSize = (34f * fontScaleAnim).sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    textAlign = TextAlign.End,
                                                    maxLines = 1,
                                                    modifier = Modifier.padding(
                                                        top = if (compactKeypad) 4.dp else 8.dp
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(if (compactKeypad) 6.dp else 12.dp))
                                }
                            }
                        }
                    }

                    // History handle — one tap opens the history area.
                    // It gently pulses so it reads as "draggable".
                    if (panelState == HistoryPanelState.CLOSED) {
                        val handleTransition = rememberInfiniteTransition(label = "handlePulse")
                        val handleAlpha by handleTransition.animateFloat(
                            initialValue = 0.22f,
                            targetValue = 0.45f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1400),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "handleAlpha"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { panelState = HistoryPanelState.HALF }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(56.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = handleAlpha)
                                    )
                            )
                        }
                    }

                    // Scientific expand toggle — the arrow rotates smoothly.
                    // Row trimmed (52 -> 44dp): the saved space goes to
                    // the keypad.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp)
                    ) {
                        val chevronRotation by animateFloatAsState(
                            targetValue = if (scientificExpanded) 180f else 0f,
                            animationSpec = tween(300),
                            label = "chevronRotation"
                        )
                        IconButton(
                            onClick = { scientificExpanded = !scientificExpanded },
                            modifier = Modifier.size(if (compactKeypad) 40.dp else 44.dp)
                        ) {
                            Icon(
                                Icons.Default.ExpandMore,
                                contentDescription = "Scientific functions",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(26.dp)
                                    .graphicsLayer { rotationZ = chevronRotation }
                            )
                        }
                    }

                    // Scientific panel — pill rows, like the reference.
                    // The old ^ pill is now Exp (Casio-style ×10^ entry).
                    // Rows stagger in on open (sciAnim), instead of the
                    // whole panel appearing at once.
                    AnimatedVisibility(
                        visible = scientificExpanded,
                        enter = expandVertically(animationSpec = tween(280)) + fadeIn(tween(280)),
                        exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(tween(220))
                    ) {
                        val scientificRows: List<List<Pair<String, String>>> = listOf(
                            listOf(
                                "√" to "√",
                                "π" to "π",
                                "Exp" to "EXP",
                                "!" to "!"
                            ),
                            listOf(
                                (if (isDegrees) "Deg" else "Rad") to "ANGLE",
                                (if (inverseMode) "sin⁻¹" else "sin") to (if (inverseMode) "asin" else "sin"),
                                (if (inverseMode) "cos⁻¹" else "cos") to (if (inverseMode) "acos" else "cos"),
                                (if (inverseMode) "tan⁻¹" else "tan") to (if (inverseMode) "atan" else "tan")
                            ),
                            listOf(
                                "Inv" to "INV",
                                "e" to "e",
                                (if (inverseMode) "eˣ" else "ln") to (if (inverseMode) "exp" else "ln"),
                                (if (inverseMode) "10ˣ" else "log") to (if (inverseMode) "10^" else "log")
                            )
                        )
                        Column {
                            scientificRows.forEachIndexed { rowIndex, row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            val p = (sciAnim.value * 1.5f - rowIndex * 0.15f)
                                                .coerceIn(0f, 1f)
                                            alpha = p
                                            translationX = (1f - p) * 40f
                                        },
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    row.forEach { (displayText, label) ->
                                        ScientificPillButton(
                                            text = displayText,
                                            active = label == "INV" && inverseMode,
                                            compact = historyOpen,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                when (label) {
                                                    "INV" -> inverseMode = !inverseMode
                                                    "ANGLE" -> viewModel.onButtonClick("ANGLE")
                                                    else -> handleKeyLabel(label)
                                                }
                                            }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }

                    // Keypad — ALWAYS weight-based rows and true-circle
                    // keys (see CalculatorButton): in every panel state
                    // the rows share the leftover height evenly and the
                    // circles simply glide bigger/smaller with the
                    // layout. NO spacers between the rows and only a
                    // 6dp horizontal gap: every saved dp goes into the
                    // circles, which end up limited by the cell WIDTH
                    // (~81dp) — as big as they can be. On first show
                    // the rows rise in one after another (spring).
                    // There is no ⌫ key any more: a TAP on AC deletes one
                    // character, a LONG-PRESS on AC clears everything.
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        val buttons = listOf(
                            listOf("AC", "()", "^", "÷"),
                            listOf("7", "8", "9", "×"),
                            listOf("4", "5", "6", "−"),
                            listOf("1", "2", "3", "+"),
                            listOf("0", ".", "%", "=")
                        )

                        buttons.forEachIndexed { rowIndex, row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .graphicsLayer {
                                        val p = (keypadAnim.value * 1.6f - rowIndex * 0.13f)
                                            .coerceIn(0f, 1f)
                                        alpha = p
                                        translationY = (1f - p) * 36f
                                    },
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                row.forEach { label ->
                                    CalculatorButton(
                                        text = label,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        isOperator = label in listOf("()", "%", "÷", "×", "−", "+", "^"),
                                        fontScale = fontScaleAnim,
                                        onLongClick = if (label == "AC") {
                                            {
                                                selActive = false
                                                viewModel.onButtonClick("AC")
                                            }
                                        } else {
                                            null
                                        },
                                        onClick = {
                                            // AC tap = delete one character
                                            handleKeyLabel(
                                                if (label == "AC") "⌫" else label
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

/**
 * Light-feel vertical drag for the history panel, watched in the
 * Initial pass so it works ANYWHERE over the calculation area:
 *  - taps pass through to the buttons untouched
 *  - horizontal drags are left alone (expression scrolling)
 *  - once a vertical drag is recognised, the remaining events are
 *    consumed, so a drag starting on a button never clicks it
 *  - one step per gesture, fired after only ~30px past the touch
 *    slop — a light flick is enough
 */
private suspend fun PointerInputScope.detectPanelDragGestures(
    onStepDown: () -> Unit,
    onStepUp: () -> Unit
) {
    awaitPointerEventScope {
        while (true) {
            val downEvent = awaitPointerEvent(PointerEventPass.Initial)
            val downChange = downEvent.changes.firstOrNull { it.pressed } ?: continue
            val pointerId = downChange.id
            var totalX = 0f
            var totalY = 0f
            var dragging = false
            var horizontal = false
            var stepped = false
            var tracking = true
            while (tracking) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == pointerId }
                if (change == null) {
                    tracking = false
                } else if (!change.pressed) {
                    if (dragging) change.consume()
                    tracking = false
                } else {
                    val delta = change.positionChange()
                    totalX += delta.x
                    totalY += delta.y
                    if (!dragging && !horizontal) {
                        if (abs(totalY) > viewConfiguration.touchSlop &&
                            abs(totalY) > abs(totalX)
                        ) {
                            dragging = true
                        } else if (abs(totalX) > viewConfiguration.touchSlop &&
                            abs(totalX) > abs(totalY)
                        ) {
                            horizontal = true
                        }
                    }
                    if (dragging) {
                        change.consume()
                        if (!stepped) {
                            when {
                                totalY > 30f -> {
                                    onStepDown()
                                    stepped = true
                                }

                                totalY < -30f -> {
                                    onStepUp()
                                    stepped = true
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Google-style shrink-to-fit: glyph widths scale linearly with the
// font size, so measuring the text once at a 100sp reference gives
// the exact size that fills the available width. The size is
// clamped — never above maxSp, never below minSp; below the floor
// the caller scrolls instead.
private fun measureFitFontSize(
    measurer: TextMeasurer,
    text: String,
    availablePx: Int,
    maxSp: Float,
    minSp: Float
): Float {
    if (availablePx <= 0 || text.isEmpty()) return maxSp
    val widthAt100 = measurer.measure(
        text = AnnotatedString(text),
        style = TextStyle(
            fontSize = 100.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = FontFamily.Default
        ),
        softWrap = false
    ).size.width
    if (widthAt100 <= 0) return maxSp
    return (100f * availablePx / widthAt100 * 0.99f).coerceIn(minSp, maxSp)
}

// One selection drag handle: a filled circle the user pulls along
// the expression; every drag position is reported back as a target
// X in the Text's own coordinate space.
@Composable
private fun SelectionHandle(
    currentX: Float,
    modifier: Modifier = Modifier,
    onDragTo: (Float) -> Unit
) {
    val currentXState by rememberUpdatedState(currentX)
    val dragToState by rememberUpdatedState(onDragTo)
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .pointerInput(Unit) {
                var anchor = 0f
                var total = 0f
                detectDragGestures(
                    onDragStart = {
                        anchor = currentXState
                        total = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        total += dragAmount.x
                        dragToState(anchor + total)
                    }
                )
            }
    )
}

// The token under a long-press, as (start, endExclusive) in raw
// indices: a whole number run, a function name with its "(", or a
// single operator / parenthesis character.
private fun tokenRangeAt(raw: String, index: Int): Pair<Int, Int> {
    if (raw.isEmpty()) return 0 to 0
    val i = index.coerceIn(0, raw.length - 1)
    val c = raw[i]
    return when {
        c.isDigit() || c == '.' -> {
            var s = i
            while (s > 0 && (raw[s - 1].isDigit() || raw[s - 1] == '.')) s--
            var e = i + 1
            while (e < raw.length && (raw[e].isDigit() || raw[e] == '.')) e++
            s to e
        }

        c.isLetter() -> {
            var s = i
            while (s > 0 && raw[s - 1].isLetter()) s--
            var e = i + 1
            while (e < raw.length && raw[e].isLetter()) e++
            if (e < raw.length && raw[e] == '(') e++
            s to e
        }

        else -> i to i + 1
    }
}

@Composable
private fun ScientificPillButton(
    text: String,
    active: Boolean,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(110),
        label = "pillPressScale"
    )
    val baseColor = MaterialTheme.colorScheme.secondaryContainer.copy(
        alpha = if (active) 1f else 0.55f
    )
    val animatedColor by animateColorAsState(
        targetValue = if (isPressed) lerp(baseColor, Color.White, 0.15f) else baseColor,
        animationSpec = tween(if (isPressed) 80 else 220),
        label = "pillBackground"
    )
    Button(
        onClick = onClick,
        modifier = modifier
            .height(if (compact) 38.dp else 46.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        shape = RoundedCornerShape(percent = 50),
        colors = ButtonDefaults.buttonColors(
            containerColor = animatedColor,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        contentPadding = PaddingValues(0.dp),
        interactionSource = interactionSource
    ) {
        Text(
            text = text,
            fontSize = if (compact) 15.sp else 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalculatorButton(
    text: String,
    modifier: Modifier = Modifier,
    isOperator: Boolean = false,
    compact: Boolean = false,
    fontScale: Float = 1f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        text == "=" -> MaterialTheme.colorScheme.primary
        text == "AC" || text == "C" -> MaterialTheme.colorScheme.primaryContainer
        isOperator -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        text == "=" -> MaterialTheme.colorScheme.onPrimary
        text == "AC" || text == "C" -> MaterialTheme.colorScheme.onPrimaryContainer
        isOperator -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = tween(110),
        label = "pressScale"
    )
    // A soft light flash blooms over the key while it is pressed.
    val animatedBackground by animateColorAsState(
        targetValue = if (isPressed) lerp(backgroundColor, Color.White, 0.16f) else backgroundColor,
        animationSpec = tween(if (isPressed) 80 else 220),
        label = "buttonBackground"
    )

    // ONE implementation for every key now. Previously AC went
    // through a plain Box branch and every other key through a
    // Material Button branch — two different sizing behaviours in
    // the same keypad. The key fills its cell; the visible circle is
    // sized by the SMALLER of the cell's width/height and centred,
    // so it is a true circle in every panel state and resize is
    // continuous — never an aspectRatio <-> fillMaxHeight switch.
    // (The compact parameter is kept for call-site compatibility;
    // sizing is driven by fontScale now.)
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val diameter = minOf(maxWidth, maxHeight)
        Box(
            modifier = Modifier
                .size(diameter)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clip(CircleShape)
                .background(animatedBackground)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            contentAlignment = Alignment.Center
        ) {
            CalculatorButtonContent(text, contentColor, fontScale)
        }
    }
}

@Composable
private fun CalculatorButtonContent(
    text: String,
    contentColor: Color,
    fontScale: Float
) {
    if (text == "⌫") {
        Icon(
            Icons.Default.Backspace,
            contentDescription = "Delete",
            tint = contentColor,
            modifier = Modifier.size((34f * fontScale).dp.coerceAtLeast(20.dp))
        )
    } else {
        Text(
            text = if (text == "()") "( )" else text,
            color = contentColor,
            fontSize = when {
                text == "AC" || text == "()" -> (25f * fontScale).sp
                text == "%" -> (29f * fontScale).sp
                text.length > 1 -> (21f * fontScale).sp
                else -> (37f * fontScale).sp
            },
            fontWeight = FontWeight.Bold
        )
    }
}

private fun dayLabel(timestamp: Long): String {
    val itemCalendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        isSameDay(itemCalendar, today) -> "Today"
        isSameDay(itemCalendar, yesterday) -> "Yesterday"
        else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun isSameDay(a: Calendar, b: Calendar): Boolean {
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

private fun formatForDisplay(raw: String): String {
    var display = raw
        .replace("sqrt(", "√(")
        .replace("log10(", "log(")
        .replace("pi", "π")
        .replace("*", "×")
        .replace("/", "÷")
        .replace("-", "−")
    return display.replace(Regex("\\d+(\\.\\d+)?")) { match ->
        val parts = match.value.split(".")
        val grouped = parts[0].reversed().chunked(3).joinToString(",").reversed()
        if (parts.size > 1) grouped + "." + parts[1] else grouped
    }
}

// Plain (non-snapshot) holder for the expression's latest text
// layout: the Text writes it in its layout phase and reads it back
// in its own draw phase of the same frame.
private class CaretLayoutHolder {
    var layout: TextLayoutResult? = null
}

// The formatted expression plus the boundary map in both directions:
// fmtToRaw[f] = the raw boundary at formatted boundary f, and
// rawToFmt[r] = the formatted boundary for raw boundary r. The text
// produced is exactly formatForDisplay's output. Multi-character
// tokens (sqrt(, log10(, pi) behave atomically — boundaries inside
// them point at the token start — and grouping commas consume no
// raw position.
private data class FormattedExpression(
    val text: String,
    val fmtToRaw: IntArray,
    val rawToFmt: IntArray
)

private fun formatWithMap(raw: String): FormattedExpression {
    val sb = StringBuilder()
    val fmtToRaw = mutableListOf(0)

    // Append one display token: interior boundaries point at the
    // token's raw start, the final boundary at its raw end.
    fun emit(token: String, rawStart: Int, rawEnd: Int) {
        token.forEachIndexed { index, ch ->
            sb.append(ch)
            fmtToRaw.add(if (index == token.lastIndex) rawEnd else rawStart)
        }
    }

    var i = 0
    while (i < raw.length) {
        when {
            raw.startsWith("sqrt(", i) -> {
                emit("√(", i, i + 5)
                i += 5
            }

            raw.startsWith("log10(", i) -> {
                emit("log(", i, i + 6)
                i += 6
            }

            raw.startsWith("pi", i) -> {
                emit("π", i, i + 2)
                i += 2
            }

            raw[i] == '*' -> {
                emit("×", i, i + 1)
                i += 1
            }

            raw[i] == '/' -> {
                emit("÷", i, i + 1)
                i += 1
            }

            raw[i] == '-' -> {
                emit("−", i, i + 1)
                i += 1
            }

            raw[i].isDigit() -> {
                var j = i
                while (j < raw.length && (raw[j].isDigit() || raw[j] == '.')) j++
                val segment = raw.substring(i, j)
                val dot = segment.indexOf('.')
                val intPart = if (dot >= 0) segment.substring(0, dot) else segment
                val rest = if (dot >= 0) segment.substring(dot) else ""
                intPart.forEachIndexed { index, ch ->
                    if (index > 0 && (intPart.length - index) % 3 == 0) {
                        emit(",", i + index, i + index)
                    }
                    emit(ch.toString(), i + index, i + index + 1)
                }
                rest.forEachIndexed { index, ch ->
                    val rawPos = i + intPart.length + index
                    emit(ch.toString(), rawPos, rawPos + 1)
                }
                i = j
            }

            else -> {
                emit(raw[i].toString(), i, i + 1)
                i += 1
            }
        }
    }

    // Reverse map: each raw boundary takes the LAST formatted
    // boundary that does not pass it (so a caret after a digit sits
    // after any grouping comma before that digit).
    val rawToFmt = IntArray(raw.length + 1)
    var f = 0
    for (r in 0..raw.length) {
        while (f + 1 < fmtToRaw.size && fmtToRaw[f + 1] <= r) f++
        rawToFmt[r] = f
    }

    return FormattedExpression(sb.toString(), fmtToRaw.toIntArray(), rawToFmt)
}
