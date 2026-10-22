package com.calnyx.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigInteger
import kotlinx.coroutines.delay

// Number systems: conversion is one BigInteger parse in the From
// base and one render in the To base, so all 12 directed pairs
// (BIN/DEC/OCT/HEX both ways) come from the same two lines — and
// the value can be as long as the user cares to type.
private enum class NumberBase(
    val label: String,
    val short: String,
    val radix: Int
) {
    BINARY("Binary", "BIN", 2),
    DECIMAL("Decimal", "DEC", 10),
    OCTAL("Octal", "OCT", 8),
    HEX("Hexadecimal", "HEX", 16)
}

private val allBases = listOf(
    NumberBase.BINARY,
    NumberBase.DECIMAL,
    NumberBase.OCTAL,
    NumberBase.HEX
)

private fun parseNumber(input: String, base: NumberBase): BigInteger? {
    val trimmed = input.trim()
    if (trimmed.isEmpty() || trimmed == "-") return null
    return try {
        BigInteger(trimmed, base.radix)
    } catch (e: NumberFormatException) {
        null
    }
}

// Readable grouping: binary in 4s, octal in 3s, hex in 4s (spaces),
// decimal in 3s (commas). The sign stays in front.
private fun formatGrouped(rawValue: String, base: NumberBase): String {
    val negative = rawValue.startsWith("-")
    val digits = if (negative) rawValue.drop(1) else rawValue
    if (digits.isEmpty()) return rawValue
    val group: Int
    val separator: String
    when (base) {
        NumberBase.BINARY -> {
            group = 4
            separator = " "
        }

        NumberBase.OCTAL -> {
            group = 3
            separator = " "
        }

        NumberBase.HEX -> {
            group = 4
            separator = " "
        }

        NumberBase.DECIMAL -> {
            group = 3
            separator = ","
        }
    }
    if (digits.length <= group) return rawValue
    val chunked = digits.reversed().chunked(group).joinToString(separator).reversed()
    return (if (negative) "-" else "") + chunked
}

// Is this key a legal digit in this base? Digits 0-9 are worth
// 0-9, letters A-F are worth 10-15 — legal when worth < radix.
private fun keyEnabledInBase(key: String, base: NumberBase): Boolean {
    if (key.length != 1) return true
    val c = key[0]
    val worth = when {
        c.isDigit() -> c - '0'
        c in 'A'..'F' -> c - 'A' + 10
        else -> return true
    }
    return worth < base.radix
}

@Composable
fun NumberSystemCalculatorScreen(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val clipboardManager = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current

    var fromBase by remember { mutableStateOf(NumberBase.BINARY) }
    var toBase by remember { mutableStateOf(NumberBase.DECIMAL) }
    var input by remember { mutableStateOf("1010") }
    var swapTurns by remember { mutableFloatStateOf(0f) }

    val value = remember(input, fromBase) { parseNumber(input, fromBase) }
    val resultRaw = remember(value, toBase) { value?.toString(toBase.radix)?.uppercase() }
    val resultDisplay = resultRaw?.let { formatGrouped(it, toBase) } ?: "—"
    val inputDisplay = formatGrouped(input.ifEmpty { "0" }, fromBase)

    // Copy confirmation, in the user's format:
    //   From Binary (BIN) = 1010
    //   To Decimal (DEC) = 10
    // Shown inside the display panel for a couple of seconds.
    var copiedText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(copiedText) {
        if (copiedText != null) {
            delay(2600)
            copiedText = null
        }
    }
    LaunchedEffect(input, fromBase, toBase) {
        copiedText = null
    }

    // Both value lines are horizontally scrollable and follow the
    // end INSTANTLY (no queued scroll animations while typing):
    // the newest digits are always on screen, older ones swipe back.
    val inputScrollState = rememberScrollState()
    LaunchedEffect(inputDisplay, inputScrollState.maxValue) {
        if (inputScrollState.value != inputScrollState.maxValue) {
            inputScrollState.scrollTo(inputScrollState.maxValue)
        }
    }
    val resultScrollState = rememberScrollState()
    LaunchedEffect(resultDisplay, resultScrollState.maxValue) {
        if (resultScrollState.value != resultScrollState.maxValue) {
            resultScrollState.scrollTo(resultScrollState.maxValue)
        }
    }

    // Blinking caret at the end of the input value.
    var caretOn by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(530)
            caretOn = !caretOn
        }
    }

    // Slow aurora drift behind everything (Converter v4 language).
    val drift = rememberInfiniteTransition(label = "nsDrift")
    val driftX by drift.animateFloat(
        initialValue = -26f,
        targetValue = 26f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nsDriftX"
    )
    val driftY by drift.animateFloat(
        initialValue = 18f,
        targetValue = -18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nsDriftY"
    )

    val swapRotation by animateFloatAsState(
        targetValue = swapTurns,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "nsSwapRotation"
    )

    fun swapBases() {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val oldFrom = fromBase
        fromBase = toBase
        toBase = oldFrom
        if (resultRaw != null) input = resultRaw
        swapTurns += 180f
    }

    fun onKey(key: String) {
        when (key) {
            "AC" -> input = ""
            "⌫" -> if (input.isNotEmpty()) input = input.dropLast(1)
            "±" -> input = if (input.startsWith("-")) input.drop(1) else "-$input"
            else -> {
                if (!keyEnabledInBase(key, fromBase)) return
                if (input.replace("-", "").length >= 64) return
                input = when {
                    input == "0" -> key
                    input == "-0" -> "-$key"
                    else -> input + key
                }
            }
        }
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            // Aurora blobs (blur needs Android 12+; below that they
            // are just soft colour washes — still fine)
            Box(
                modifier = Modifier
                    .offset(x = driftX.dp - 70.dp, y = 30.dp + driftY.dp)
                    .size(230.dp)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.30f))
                    .blur(64.dp)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 60.dp - driftX.dp, y = (-40).dp - driftY.dp)
                    .size(260.dp)
                    .clip(CircleShape)
                    .background(colors.tertiary.copy(alpha = 0.26f))
                    .blur(72.dp)
            )

            // LAYOUT by percentage: display card 52%, keypad 48% —
            // the display panel is the tall zone now, the keys are
            // back to a comfortable height.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp)
            ) {
                // Header — compact, two lines, no wasted band
                NsAnimatedIn(delayMs = 0) {
                    Column {
                        Text(
                            text = "Number System",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onBackground
                        )
                        Text(
                            text = "Binary · Decimal · Octal · Hex",
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ===== 52% — glass card: From/To chips + display panel
                NsAnimatedIn(modifier = Modifier.weight(0.52f), delayMs = 90) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        colors.surfaceVariant.copy(alpha = 0.55f),
                                        colors.surfaceVariant.copy(alpha = 0.30f)
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.22f),
                                        Color.White.copy(alpha = 0.05f)
                                    )
                                ),
                                shape = RoundedCornerShape(28.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "FROM",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            allBases.forEach { base ->
                                NsBaseChip(
                                    base = base,
                                    selected = base == fromBase,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        fromBase = base
                                        haptics.performHapticFeedback(
                                            HapticFeedbackType.TextHandleMove
                                        )
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        // TO label only — the double-arrow swap button
                        // that used to sit at its right is removed;
                        // swapping lives in the keypad now.
                        Text(
                            text = "TO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            allBases.forEach { base ->
                                NsBaseChip(
                                    base = base,
                                    selected = base == toBase,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        toBase = base
                                        haptics.performHapticFeedback(
                                            HapticFeedbackType.TextHandleMove
                                        )
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Display panel: a darker inner well that
                        // FILLS the rest of the card — the input area.
                        // The value lines sit at its bottom, big.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(colors.background.copy(alpha = 0.28f))
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.06f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            // Input value — big, single line, scrollable
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    text = "${fromBase.short} · ${fromBase.radix}",
                                    fontSize = 12.sp,
                                    color = colors.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.horizontalScroll(inputScrollState),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Text(
                                            text = inputDisplay,
                                            fontSize = 44.sp,
                                            lineHeight = 48.sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = FontFamily.Monospace,
                                            color = colors.onSurface,
                                            textAlign = TextAlign.End,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Text(
                                            text = "▌",
                                            fontSize = 38.sp,
                                            lineHeight = 48.sp,
                                            color = colors.primary,
                                            modifier = Modifier.alpha(if (caretOn) 0.9f else 0f)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            // Result — big, single line, scrollable too
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${toBase.short} · ${toBase.radix}",
                                    fontSize = 12.sp,
                                    color = colors.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.horizontalScroll(resultScrollState),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AnimatedContent(
                                            targetState = resultDisplay,
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
                                            label = "nsResult"
                                        ) { text ->
                                            Text(
                                                text = text,
                                                fontSize = 34.sp,
                                                lineHeight = 38.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = colors.primary,
                                                textAlign = TextAlign.End,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                                // Copy — only fires with a valid result,
                                // and copies in the From/To format
                                IconButton(
                                    onClick = {
                                        if (resultRaw != null && input.isNotEmpty()) {
                                            val text = "From ${fromBase.label} (${fromBase.short}) = $input\n" +
                                                    "To ${toBase.label} (${toBase.short}) = $resultRaw"
                                            clipboardManager.setText(AnnotatedString(text))
                                            copiedText = text
                                            haptics.performHapticFeedback(
                                                HapticFeedbackType.LongPress
                                            )
                                        }
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy conversion",
                                        tint = colors.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }

                            // Copy confirmation, in the same format
                            AnimatedVisibility(
                                visible = copiedText != null,
                                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                                exit = fadeOut(tween(180)) + shrinkVertically(tween(180))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = copiedText ?: "",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.primary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ===== 48% — keypad. Rows share the zone evenly and
                // every key FILLS its row height (~69dp now).
                // Entrance replays when the From base changes.
                key(fromBase) {
                    NsAnimatedIn(modifier = Modifier.weight(0.48f), delayMs = 60) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val keyRows = listOf(
                                listOf("7", "8", "9", "A", "B"),
                                listOf("4", "5", "6", "C", "D"),
                                listOf("1", "2", "3", "E", "F")
                            )
                            keyRows.forEach { row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    row.forEach { keyLabel ->
                                        NsKey(
                                            text = keyLabel,
                                            enabled = keyEnabledInBase(keyLabel, fromBase),
                                            containerColor = if (keyLabel.length == 1 &&
                                                keyLabel[0] in 'A'..'F'
                                            ) {
                                                colors.secondaryContainer.copy(alpha = 0.75f)
                                            } else {
                                                colors.surfaceVariant.copy(alpha = 0.55f)
                                            },
                                            contentColor = colors.onSurface,
                                            modifier = Modifier.weight(1f),
                                            onClick = { onKey(keyLabel) }
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                NsKey(
                                    text = "±",
                                    enabled = true,
                                    containerColor = colors.secondaryContainer.copy(alpha = 0.75f),
                                    contentColor = colors.onSecondaryContainer,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onKey("±") }
                                )
                                NsKey(
                                    text = "0",
                                    enabled = true,
                                    containerColor = colors.surfaceVariant.copy(alpha = 0.55f),
                                    contentColor = colors.onSurface,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onKey("0") }
                                )
                                NsKey(
                                    text = "⌫",
                                    enabled = true,
                                    containerColor = colors.secondaryContainer.copy(alpha = 0.75f),
                                    contentColor = colors.onSecondaryContainer,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onKey("⌫") },
                                    onLongClick = { onKey("AC") }
                                )
                                // In-keypad swap (the only swap control now)
                                val swapInteraction = remember { MutableInteractionSource() }
                                val swapPressed by swapInteraction.collectIsPressedAsState()
                                val swapScale by animateFloatAsState(
                                    targetValue = if (swapPressed) 0.9f else 1f,
                                    animationSpec = tween(110),
                                    label = "nsSwapKeyScale"
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .scale(swapScale)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(colors.surfaceVariant.copy(alpha = 0.55f))
                                        .rotate(swapRotation)
                                        .clickable(
                                            interactionSource = swapInteraction,
                                            indication = null
                                        ) { swapBases() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.SwapHoriz,
                                        contentDescription = "Swap bases",
                                        tint = colors.onSurface,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                NsKey(
                                    text = "AC",
                                    enabled = true,
                                    containerColor = colors.primaryContainer,
                                    contentColor = colors.onPrimaryContainer,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onKey("AC") }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// One From/To button: ONE line (short name only — BIN / DEC / OCT /
// HEX), so the four buttons sit side by side without cramping.
// The selected one fills with the primary colour and pops a touch.
@Composable
private fun NsBaseChip(
    base: NumberBase,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.surfaceVariant.copy(alpha = 0.45f),
        animationSpec = tween(280),
        label = "nsChipBackground"
    )
    val content by animateColorAsState(
        targetValue = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        animationSpec = tween(280),
        label = "nsChipContent"
    )
    val chipScale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        animationSpec = tween(280),
        label = "nsChipScale"
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            .scale(chipScale)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = base.short,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = content,
            maxLines = 1
        )
    }
}

// One keypad key: glass tile that FILLS its row height, press
// scale, standard clickable handling (tap + optional long-press).
// Disabled keys (illegal digit for the From base) go quiet.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NsKey(
    text: String,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.92f else 1f,
        animationSpec = tween(110),
        label = "nsKeyPressScale"
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            .scale(pressScale)
            .alpha(if (enabled) 1f else 0.32f)
            .clip(RoundedCornerShape(18.dp))
            .background(containerColor)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(18.dp)
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = when (text) {
                "⌫" -> 24.sp
                "AC" -> 20.sp
                else -> 22.sp
            },
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

// Staggered entrance: fades + rises in after a small delay.
@Composable
private fun NsAnimatedIn(
    modifier: Modifier = Modifier,
    delayMs: Int,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        visible = true
    }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "nsEntrance"
    )
    Box(
        modifier = modifier
            .alpha(progress)
            .offset(y = ((1f - progress) * 26f).dp)
    ) {
        content()
    }
}
