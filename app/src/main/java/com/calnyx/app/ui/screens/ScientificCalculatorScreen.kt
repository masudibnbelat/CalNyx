// FILE: app/src/main/java/com/calnyx/app/ui/screens/ScientificCalculatorScreen.kt
package com.calnyx.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calnyx.app.viewmodel.CalculatorViewModel
import com.calnyx.app.viewmodel.HistoryViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

private enum class SciKeyStyle { FUNCTION, DIGIT, ORANGE, YELLOW, PURPLE, EQUAL }

private data class SciKey(
    val display: String,
    val action: String,
    val shiftDisplay: String? = null,
    val shiftAction: String? = null,
    val style: SciKeyStyle = SciKeyStyle.FUNCTION,
    val weight: Float = 1f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScientificCalculatorScreen(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    viewModel: CalculatorViewModel = viewModel(),
    historyViewModel: HistoryViewModel = viewModel()
) {
    // darkTheme / onToggleTheme kept for the existing NavGraph call.
    LaunchedEffect(Unit) {
        viewModel.calculatorType = "scientific"
    }

    val expression by viewModel.expression.collectAsState()
    val result by viewModel.result.collectAsState()
    val finalResult by viewModel.finalResult.collectAsState()
    val isDegrees by viewModel.isDegrees.collectAsState()
    val history by historyViewModel.history.collectAsState()
    var shiftMode by remember { mutableStateOf(false) }
    var hypMode by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }

    val groupedHistory = remember(history) {
        history.groupBy { dayLabel(it.createdAt) }
    }
    val groupedEntries = remember(groupedHistory) { groupedHistory.entries.toList() }

    fun onKeyPress(key: SciKey) {
        when {
            key.action == "SHIFT" -> shiftMode = !shiftMode
            key.action == "HYP" -> hypMode = !hypMode
            shiftMode && key.shiftAction != null -> {
                viewModel.onButtonClick(key.shiftAction)
                shiftMode = false
            }

            hypMode && key.action in listOf("sin", "cos", "tan") -> {
                viewModel.onButtonClick(key.action + "h")
                hypMode = false
            }

            else -> viewModel.onButtonClick(key.action)
        }
    }

    val functionRows: List<List<SciKey>> = listOf(
        listOf(
            SciKey("SHIFT", "SHIFT", style = SciKeyStyle.YELLOW),
            SciKey(if (isDegrees) "Deg" else "Rad", "ANGLE", style = SciKeyStyle.PURPLE),
            SciKey("x!", "!"),
            SciKey("x⁻¹", "x⁻¹"),
            SciKey("%", "%")
        ),
        listOf(
            SciKey("sin", "sin", shiftDisplay = "sin⁻¹", shiftAction = "asin"),
            SciKey("cos", "cos", shiftDisplay = "cos⁻¹", shiftAction = "acos"),
            SciKey("tan", "tan", shiftDisplay = "tan⁻¹", shiftAction = "atan"),
            SciKey("ln", "ln", shiftDisplay = "eˣ", shiftAction = "exp"),
            SciKey("log", "log", shiftDisplay = "10ˣ", shiftAction = "10^")
        ),
        listOf(
            SciKey("√", "√", shiftDisplay = "x²", shiftAction = "x²"),
            SciKey("xʸ", "^"),
            SciKey("π", "π"),
            SciKey("e", "e"),
            SciKey("|x|", "abs")
        ),
        listOf(
            SciKey("( )", "()"),
            SciKey("Exp", "EXP"),
            SciKey("mod", "mod"),
            SciKey("gcd", "gcd"),
            SciKey("lcm", "lcm")
        ),
        listOf(
            SciKey("hyp", "HYP"),
            SciKey("∛", "∛"),
            SciKey("nPr", "nPr"),
            SciKey("nCr", "nCr"),
            SciKey("Rand", "Rand")
        ),
        listOf(
            SciKey("log₂", "log2"),
            SciKey("⌊x⌋", "floor"),
            SciKey("⌈x⌉", "ceil"),
            SciKey("x√y", "ROOT"),
            SciKey("RanInt", "RanInt")
        )
    )

    val digitRows: List<List<SciKey>> = listOf(
        listOf(
            SciKey("7", "7", style = SciKeyStyle.DIGIT),
            SciKey("8", "8", style = SciKeyStyle.DIGIT),
            SciKey("9", "9", style = SciKeyStyle.DIGIT),
            SciKey("⌫", "⌫", style = SciKeyStyle.ORANGE),
            SciKey("AC", "AC", style = SciKeyStyle.ORANGE)
        ),
        listOf(
            SciKey("4", "4", style = SciKeyStyle.DIGIT),
            SciKey("5", "5", style = SciKeyStyle.DIGIT),
            SciKey("6", "6", style = SciKeyStyle.DIGIT),
            SciKey("×", "×"),
            SciKey("÷", "÷")
        ),
        listOf(
            SciKey("1", "1", style = SciKeyStyle.DIGIT),
            SciKey("2", "2", style = SciKeyStyle.DIGIT),
            SciKey("3", "3", style = SciKeyStyle.DIGIT),
            SciKey("+", "+"),
            SciKey("−", "−")
        ),
        listOf(
            SciKey("0", "0", style = SciKeyStyle.DIGIT),
            SciKey(".", ".", style = SciKeyStyle.DIGIT),
            SciKey("Ans", "Ans"),
            SciKey("=", "=", style = SciKeyStyle.EQUAL, weight = 2f)
        )
    )

    // LCD entrance animation
    val displayAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        displayAnim.animateTo(1f, animationSpec = tween(400))
    }
    // Final-answer pop animation
    val resultPop = remember { Animatable(1f) }
    LaunchedEffect(finalResult) {
        if (finalResult != null) {
            resultPop.snapTo(0.85f)
            resultPop.animateTo(
                1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 6.dp)
    ) {
        // Toolbar sits level with the floating 3-dot menu — no big spacer
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { showHistorySheet = true }) {
                Icon(
                    Icons.Default.History,
                    contentDescription = "History",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = "Scientific",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // LCD display panel — BOTH lines are single-line and scrollable
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFDCE9E2))
                .graphicsLayer {
                    alpha = displayAnim.value
                    translationY = (1f - displayAnim.value) * -16f
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Indicator row — fixed height
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isDegrees) "DEG" else "RAD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF14201A).copy(alpha = 0.55f)
                )
                AnimatedVisibility(
                    visible = shiftMode,
                    enter = fadeIn(tween(150)) + expandHorizontally(tween(150))
                ) {
                    Text(
                        text = "  SHIFT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9C6B1E)
                    )
                }
                AnimatedVisibility(
                    visible = hypMode,
                    enter = fadeIn(tween(150)) + expandHorizontally(tween(150))
                ) {
                    Text(
                        text = "  HYP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9C6B1E)
                    )
                }
            }

            // Expression line — one line, scrollable, font shrinks with length
            val shownExpression = if (finalResult != null) "" else expression
            val expressionFontSize by animateFloatAsState(
                targetValue = when {
                    shownExpression.length <= 12 -> 34f
                    shownExpression.length <= 18 -> 29f
                    shownExpression.length <= 26 -> 24f
                    else -> 20f
                },
                animationSpec = tween(180),
                label = "sciExprFont"
            )
            val expressionScroll = rememberScrollState()
            LaunchedEffect(expression) {
                expressionScroll.animateScrollTo(expressionScroll.maxValue, animationSpec = tween(120))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clipToBounds(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(expressionScroll),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatScientificDisplay(shownExpression, expressionFontSize.sp),
                        fontSize = expressionFontSize.sp,
                        color = Color(0xFF14201A),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Answer / preview line — one line, scrollable, font shrinks
            // with length, hard-clipped so it can never overlap again
            val shownResultText = finalResult ?: result
            val shownResultDisplay = if (shownResultText == "0") {
                ""
            } else {
                formatForDisplay(shownResultText)
            }
            val resultFontSize by animateFloatAsState(
                targetValue = when {
                    finalResult != null && shownResultDisplay.length <= 10 -> 36f
                    finalResult != null && shownResultDisplay.length <= 14 -> 30f
                    finalResult != null && shownResultDisplay.length <= 19 -> 25f
                    finalResult != null -> 21f
                    shownResultDisplay.length <= 19 -> 22f
                    else -> 19f
                },
                animationSpec = tween(180),
                label = "sciResultFont"
            )
            val resultScroll = rememberScrollState()
            LaunchedEffect(shownResultText) {
                resultScroll.animateScrollTo(resultScroll.maxValue, animationSpec = tween(120))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clipToBounds(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(resultScroll),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = shownResultDisplay,
                        fontSize = resultFontSize.sp,
                        fontWeight = if (finalResult != null) FontWeight.Bold else FontWeight.Normal,
                        color = if (finalResult != null) Color(0xFF14201A) else Color(0xFF9C6B1E),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.graphicsLayer {
                            scaleX = resultPop.value
                            scaleY = resultPop.value
                            transformOrigin = TransformOrigin(1f, 0.5f)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Keypad — rows share the remaining height, almost no gaps,
        // rows animate in one after another
        Column(modifier = Modifier.weight(1f)) {
            (functionRows + digitRows).forEachIndexed { rowIndex, row ->
                val rowAnim = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    delay(rowIndex * 35L)
                    rowAnim.animateTo(1f, animationSpec = tween(350))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .graphicsLayer {
                            alpha = rowAnim.value
                            translationY = (1f - rowAnim.value) * 30f
                        },
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    row.forEach { key ->
                        SciKeyCell(
                            key = key,
                            shiftMode = shiftMode,
                            hypMode = hypMode,
                            modifier = Modifier
                                .weight(key.weight)
                                .fillMaxHeight(),
                            onPress = { onKeyPress(it) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
    }

    // History modal — slides up as a bottom sheet
    if (showHistorySheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "History",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { historyViewModel.clearAll() }) {
                        Text(
                            text = "Clear",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (history.isEmpty()) {
                    Text(
                        text = "No history yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 430.dp)
                    ) {
                        groupedEntries.forEach { (day, dayItems) ->
                            item(key = "sheet-header-$day") {
                                Text(
                                    text = day,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                            }
                            items(dayItems) { item ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.onHistoryItemClick(item.result)
                                            showHistorySheet = false
                                        }
                                        .padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = formatForDisplay(item.expression),
                                        fontSize = 17.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.End
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = formatForDisplay(item.result),
                                        fontSize = 28.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.End
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

@Composable
private fun SciKeyCell(
    key: SciKey,
    shiftMode: Boolean,
    hypMode: Boolean,
    modifier: Modifier = Modifier,
    onPress: (SciKey) -> Unit
) {
    val containerColor: Color
    val contentColor: Color
    when (key.style) {
        SciKeyStyle.FUNCTION -> {
            containerColor = MaterialTheme.colorScheme.secondaryContainer
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        }

        SciKeyStyle.DIGIT -> {
            containerColor = MaterialTheme.colorScheme.surfaceVariant
            contentColor = MaterialTheme.colorScheme.onSurface
        }

        SciKeyStyle.ORANGE -> {
            containerColor = Color(0xFFF0A35E)
            contentColor = Color(0xFF26160A)
        }

        SciKeyStyle.YELLOW -> {
            containerColor = if (shiftMode) Color(0xFFFFD93D) else Color(0xFFE9C46A)
            contentColor = Color(0xFF2A2000)
        }

        SciKeyStyle.PURPLE -> {
            containerColor = MaterialTheme.colorScheme.primaryContainer
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        }

        SciKeyStyle.EQUAL -> {
            containerColor = MaterialTheme.colorScheme.primary
            contentColor = MaterialTheme.colorScheme.onPrimary
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = tween(100),
        label = "sciPressScale"
    )
    val animatedContainer by animateColorAsState(
        targetValue = if (isPressed) {
            containerColor.copy(alpha = 0.65f)
        } else {
            containerColor
        },
        animationSpec = tween(90),
        label = "sciPressColor"
    )

    Column(modifier = modifier) {
        // Small yellow shift label above the key (Casio style) —
        // bottom-aligned in a fixed box so it is never clipped
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(13.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = key.shiftDisplay ?: "",
                fontSize = 9.sp,
                lineHeight = 10.sp,
                color = Color(0xFFE9C46A),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
        Button(
            onClick = { onPress(key) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = animatedContainer,
                contentColor = contentColor
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            contentPadding = PaddingValues(0.dp),
            interactionSource = interactionSource
        ) {
            if (key.action == "⌫") {
                Icon(
                    Icons.Default.Backspace,
                    contentDescription = "Delete",
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Text(
                    text = when {
                        shiftMode && key.shiftDisplay != null -> key.shiftDisplay
                        hypMode && key.action in listOf("sin", "cos", "tan") -> key.action + "h"
                        else -> key.display
                    },
                    fontSize = if (key.style == SciKeyStyle.DIGIT) 24.sp else 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

// Groups digits with commas and restores × ÷ − √ π symbols,
// then renders anything after "^" as a real superscript (3^6 -> 3⁶).
private fun formatScientificDisplay(raw: String, baseSize: TextUnit): androidx.compose.ui.text.AnnotatedString {
    val grouped = formatForDisplay(raw)
    return buildAnnotatedString {
        var i = 0
        while (i < grouped.length) {
            if (grouped[i] == '^' && i + 1 < grouped.length) {
                var j = i + 1
                val token = StringBuilder()
                if (grouped[j] == '(') {
                    var depth = 0
                    while (j < grouped.length) {
                        val c = grouped[j]
                        token.append(c)
                        if (c == '(') depth++
                        if (c == ')') {
                            depth--
                            if (depth == 0) {
                                j++
                                break
                            }
                        }
                        j++
                    }
                } else {
                    while (j < grouped.length &&
                        (grouped[j].isDigit() || grouped[j] == '.' || grouped[j] == '−')
                    ) {
                        token.append(grouped[j])
                        j++
                    }
                }
                if (token.isNotEmpty()) {
                    withStyle(
                        SpanStyle(
                            fontSize = baseSize * 0.65f,
                            baselineShift = BaselineShift.Superscript
                        )
                    ) {
                        append(token.toString())
                    }
                    i = j
                } else {
                    append(grouped[i])
                    i++
                }
            } else {
                append(grouped[i])
                i++
            }
        }
    }
}

private fun formatForDisplay(raw: String): String {
    var display = raw
        .replace("sqrt(", "√(")
        .replace("cbrt(", "∛(")
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
