// FILE: app/src/main/java/com/calnyx/app/ui/screens/IctCalculatorScreen.kt
// % VERSION (2026-11-07) - REPLACE. B/W base, logic UNCHANGED.
// Only change: every size is now a % of the window
// (BoxWithConstraints: windowW/windowH/minDim). Card 44% / keypad
// 56% split was already weight-based. No fixed dp/sp except
// 1.dp hairline borders.
// B/W VERSION - replace your old IctCalculatorScreen.kt with this file.
// Based on the user's pasted IctCalculatorScreen.kt. LOGIC UNCHANGED:
// base switching, IctFraction math, parser, rendering, grouping,
// key enable/disable rules and animations are all exactly as pasted.
// Only these changes:
//  1. BLACK & WHITE ONLY, both dark and light mode: only
//     MaterialTheme.colorScheme neutrals (background, onBackground,
//     surface, onSurface, surfaceVariant, onSurfaceVariant, outline).
//     No primary / primaryContainer / onPrimary / onPrimaryContainer /
//     secondaryContainer / onSecondaryContainer anywhere. Base chip
//     selected = inverted (onBackground bg, background text); "=" key
//     = inverted; AC key = surfaceVariant bg with onSurface text;
//     operator / function / letter keys = surfaceVariant 75%;
//     digit keys = surfaceVariant; caret and result text = onSurface;
//     copy icon = onSurfaceVariant. All Color.White borders replaced
//     with onSurface at 0.12 alpha; the glass-card gradient stays a
//     vertical gradient between surfaceVariant alphas only.
//     EXCEPTION: the divide-by-zero message keeps colors.error —
//     red marks a genuine error state, not decoration.
//  2. DRAWER SWIPE (universal rule): new onOpenDrawer param. A
//     leftward drag past 24dp on the base-chips row or on the keypad
//     opens the app drawer (rightward drags are NOT consumed). It is
//     NOT on the display panel — its expression/result rows scroll
//     horizontally and must keep working.
package com.calnyx.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigInteger
import kotlinx.coroutines.delay

// ICT calculator: arithmetic performed IN the selected base.
// Values are exact fractions (BigInteger numerator/denominator),
// so division produces a true fraction in the same base:
// 1 / 10 in binary = 0.1, FF + 1 in hex = 100.
private enum class IctBase(val label: String, val short: String, val radix: Int) {
    BINARY("Binary", "BIN", 2),
    DECIMAL("Decimal", "DEC", 10),
    OCTAL("Octal", "OCT", 8),
    HEX("Hexadecimal", "HEX", 16)
}

private val ictBases = listOf(IctBase.BINARY, IctBase.DECIMAL, IctBase.OCTAL, IctBase.HEX)

private const val ICT_DIGIT_CHARS = "0123456789ABCDEF"

// The ONE digit-value table for the whole screen: the keypad, the
// base-switch check and the parser all read digits through here,
// so a digit can never be "enabled but unparseable" (hex letters
// included: A-F are worth 10-15).
private fun digitValueOf(c: Char): Int {
    return when (c) {
        in '0'..'9' -> c - '0'
        in 'A'..'F' -> c - 'A' + 10
        in 'a'..'f' -> c - 'a' + 10
        else -> -1
    }
}

// Is this character a legal digit in the base?
private fun ictDigitEnabled(char: Char, base: IctBase): Boolean {
    val value = digitValueOf(char)
    return value >= 0 && value < base.radix
}

// Exact rational number, always normalised (den > 0, gcd removed).
private class IctFraction private constructor(
    val num: BigInteger,
    val den: BigInteger
) {
    operator fun plus(other: IctFraction): IctFraction =
        create(num * other.den + other.num * den, den * other.den)

    operator fun minus(other: IctFraction): IctFraction =
        create(num * other.den - other.num * den, den * other.den)

    operator fun times(other: IctFraction): IctFraction =
        create(num * other.num, den * other.den)

    operator fun div(other: IctFraction): IctFraction {
        if (other.num == BigInteger.ZERO) throw ArithmeticException("DIV0")
        return create(num * other.den, den * other.num)
    }

    operator fun unaryMinus(): IctFraction = create(num.negate(), den)

    companion object {
        fun create(n: BigInteger, d: BigInteger): IctFraction {
            var numerator = n
            var denominator = d
            if (denominator < BigInteger.ZERO) {
                numerator = numerator.negate()
                denominator = denominator.negate()
            }
            val g = numerator.gcd(denominator)
            if (g > BigInteger.ZERO) {
                numerator /= g
                denominator /= g
            }
            return IctFraction(numerator, denominator)
        }

        fun of(n: BigInteger): IctFraction = IctFraction(n, BigInteger.ONE)
    }
}

// Recursive-descent parser: + - (low) -> * / (high) -> factor
// (number, parenthesised expression, unary minus). Numbers are
// literals in the working base, with an optional fractional part,
// built digit by digit through digitValueOf (no radix-string
// parsing anywhere, hex included).
private class IctParser(private val text: String, private val radix: Int) {
    private var pos = 0
    private val bigRadix = BigInteger.valueOf(radix.toLong())

    fun parse(): IctFraction {
        val value = parseExpression()
        if (pos != text.length) throw IllegalArgumentException("trailing input")
        return value
    }

    private fun peek(): Char? = text.getOrNull(pos)

    private fun parseExpression(): IctFraction {
        var value = parseTerm()
        while (true) {
            when (peek()) {
                '+' -> {
                    pos++
                    value += parseTerm()
                }

                '-' -> {
                    pos++
                    value -= parseTerm()
                }

                else -> return value
            }
        }
    }

    private fun parseTerm(): IctFraction {
        var value = parseFactor()
        while (true) {
            when (peek()) {
                '*' -> {
                    pos++
                    value *= parseFactor()
                }

                '/' -> {
                    pos++
                    value /= parseFactor()
                }

                else -> return value
            }
        }
    }

    private fun parseFactor(): IctFraction {
        return when (peek()) {
            '-' -> {
                pos++
                -parseFactor()
            }

            '+' -> {
                pos++
                parseFactor()
            }

            '(' -> {
                pos++
                val inner = parseExpression()
                if (peek() != ')') throw IllegalArgumentException("missing )")
                pos++
                inner
            }

            else -> parseLiteral()
        }
    }

    private fun parseLiteral(): IctFraction {
        val start = pos
        while (pos < text.length && (digitValueOf(text[pos]) != -1 || text[pos] == '.')) {
            pos++
        }
        if (pos == start) throw IllegalArgumentException("number expected")
        val literal = text.substring(start, pos)
        if (literal.count { it == '.' } > 1) throw IllegalArgumentException("two dots")
        literal.forEach { c ->
            if (c != '.' && digitValueOf(c) >= radix) {
                throw IllegalArgumentException("digit not in base")
            }
        }
        val intPart = literal.substringBefore('.')
        val fracPart = if ('.' in literal) literal.substringAfter('.') else ""
        if (intPart.isEmpty() && fracPart.isEmpty()) {
            throw IllegalArgumentException("empty number")
        }
        var intValue = BigInteger.ZERO
        for (c in intPart) {
            intValue = intValue * bigRadix + BigInteger.valueOf(digitValueOf(c).toLong())
        }
        if (fracPart.isEmpty()) return IctFraction.of(intValue)
        var fracValue = BigInteger.ZERO
        for (c in fracPart) {
            fracValue = fracValue * bigRadix + BigInteger.valueOf(digitValueOf(c).toLong())
        }
        val scale = bigRadix.pow(fracPart.length)
        return IctFraction.create(intValue * scale + fracValue, scale)
    }
}

private data class IctRender(val text: String, val exact: Boolean)

// Render a fraction as a literal in the base: integer part plus up
// to 20 fractional digits (1/2 in binary terminates as 0.1;
// 1/3 in decimal runs out and is reported as not exact -> "≈").
private fun renderFraction(value: IctFraction, radix: Int): IctRender {
    val negative = value.num.signum() < 0
    val absNum = value.num.abs()
    var remainder = absNum % value.den
    val intPart = (absNum / value.den).toString(radix).uppercase()
    val sign = if (negative) "-" else ""
    if (remainder == BigInteger.ZERO) return IctRender(sign + intPart, true)
    val bigRadix = BigInteger.valueOf(radix.toLong())
    val fractionDigits = StringBuilder()
    var digitCount = 0
    while (remainder != BigInteger.ZERO && digitCount < 20) {
        remainder *= bigRadix
        val digit = remainder / value.den
        fractionDigits.append(ICT_DIGIT_CHARS[digit.toInt()])
        remainder %= value.den
        digitCount++
    }
    return IctRender(
        sign + intPart + "." + fractionDigits.toString(),
        remainder == BigInteger.ZERO
    )
}

private sealed interface IctEval {
    object None : IctEval
    object DivZero : IctEval
    data class Value(val raw: String, val exact: Boolean) : IctEval
}

// Live evaluation. Anything incomplete (trailing operator, open
// parenthesis) is simply "no result yet" — and division by zero is
// reported as its own state, so the UI can refuse the = key.
private fun evaluateExpression(expression: String, radix: Int): IctEval {
    if (expression.isEmpty()) return IctEval.None
    if (expression.last() in "+-*/(") return IctEval.None
    var balance = 0
    for (c in expression) {
        if (c == '(') balance++
        if (c == ')') {
            balance--
            if (balance < 0) return IctEval.None
        }
    }
    if (balance != 0) return IctEval.None
    return try {
        val rendered = renderFraction(IctParser(expression, radix).parse(), radix)
        IctEval.Value(rendered.text, rendered.exact)
    } catch (e: ArithmeticException) {
        IctEval.DivZero
    } catch (e: Exception) {
        IctEval.None
    }
}

// Group an integer-part digit string for display (binary 4s,
// octal 3s, hex 4s with spaces; decimal 3s with commas).
private fun groupIctDigits(digits: String, base: IctBase): String {
    val group: Int
    val separator: String
    when (base) {
        IctBase.BINARY -> {
            group = 4
            separator = " "
        }

        IctBase.OCTAL -> {
            group = 3
            separator = " "
        }

        IctBase.HEX -> {
            group = 4
            separator = " "
        }

        IctBase.DECIMAL -> {
            group = 3
            separator = ","
        }
    }
    if (digits.length <= group) return digits
    return digits.reversed().chunked(group).joinToString(separator).reversed()
}

// Pretty result for the preview line: grouped integer part,
// fraction grouped the same way for non-decimal bases.
private fun formatIctResult(raw: String, base: IctBase): String {
    val negative = raw.startsWith("-")
    val body = if (negative) raw.drop(1) else raw
    val intPart = body.substringBefore('.')
    val fracPart = if ('.' in body) body.substringAfter('.') else ""
    val groupedInt = groupIctDigits(intPart, base)
    val groupedFrac = if (fracPart.isEmpty()) {
        ""
    } else if (base == IctBase.DECIMAL) {
        fracPart
    } else {
        val size = if (base == IctBase.OCTAL) 3 else 4
        fracPart.chunked(size).joinToString(" ")
    }
    return (if (negative) "-" else "") + groupedInt +
            (if (groupedFrac.isEmpty()) "" else ".$groupedFrac")
}

private fun formatIctExpression(raw: String): String {
    return raw
        .replace("*", "×")
        .replace("/", "÷")
        .replace("-", "−")
}

private enum class IctKeyKind { DIGIT, LETTER, OPERATOR, FUNCTION, CLEAR, EQUALS }

private data class IctKeySpec(
    val id: String,
    val label: String,
    val enabled: Boolean,
    val kind: IctKeyKind
)

@Composable
fun IctCalculatorScreen(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onOpenDrawer: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val clipboardManager = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current

    // (Drawer swipe lives inside BoxWithConstraints now - it needs minDim.)

    var base by remember { mutableStateOf(IctBase.BINARY) }
    // Internal expression is plain ASCII: digits, A-F, . + - * / ( )
    var expression by remember { mutableStateOf("") }

    val eval = remember(expression, base) { evaluateExpression(expression, base.radix) }
    val expressionDisplay = remember(expression) {
        formatIctExpression(expression).ifEmpty { "0" }
    }
    // The answer shows in ONE place only: while an expression is
    // being typed it lives in the preview line, but once "=" has
    // chained the result back into the expression line (or the
    // user has typed a lone number) the preview would just repeat
    // the same value — so it falls back to the placeholder.
    val isLoneNumber = expression.isNotEmpty() &&
            expression.removePrefix("-").all { it.isLetterOrDigit() || it == '.' }
    val resultDisplay = when (val e = eval) {
        is IctEval.Value -> if (isLoneNumber) {
            "—"
        } else {
            (if (e.exact) "= " else "≈ ") + formatIctResult(e.raw, base)
        }
        IctEval.DivZero -> "Can't divide by 0"
        IctEval.None -> "—"
    }

    // Both lines scroll horizontally and follow the end instantly.
    val expressionScrollState = rememberScrollState()
    LaunchedEffect(expressionDisplay, expressionScrollState.maxValue) {
        if (expressionScrollState.value != expressionScrollState.maxValue) {
            expressionScrollState.scrollTo(expressionScrollState.maxValue)
        }
    }
    val resultScrollState = rememberScrollState()
    LaunchedEffect(resultDisplay, resultScrollState.maxValue) {
        if (resultScrollState.value != resultScrollState.maxValue) {
            resultScrollState.scrollTo(resultScrollState.maxValue)
        }
    }

    // Typing animation: the expression gives a small pulse on
    // every change, so typing feels alive.
    val expressionPulse = remember { Animatable(1f) }
    LaunchedEffect(expression) {
        if (expression.isNotEmpty()) {
            expressionPulse.snapTo(1.03f)
            expressionPulse.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 180)
            )
        }
    }

    // Blinking caret at the end of the expression.
    var caretOn by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(530)
            caretOn = !caretOn
        }
    }

    fun switchBase(newBase: IctBase) {
        if (newBase == base) return
        // An expression holding digits that don't exist in the new
        // base can never be calculated there — it is cleared rather
        // than carried into an impossible state.
        val hasIllegalDigit = expression.any { c ->
            (c.isLetterOrDigit()) && !ictDigitEnabled(c, newBase)
        }
        if (hasIllegalDigit) expression = ""
        base = newBase
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun onKey(id: String) {
        val last = expression.lastOrNull()
        val endsOperand = last != null &&
                (last.isLetterOrDigit() || last == ')' || last == '.')
        when (id) {
            "AC" -> expression = ""
            "⌫" -> if (expression.isNotEmpty()) expression = expression.dropLast(1)
            "=" -> {
                val e = eval
                if (e is IctEval.Value) expression = e.raw
            }

            "()" -> {
                if (expression.length >= 120) return
                var balance = 0
                for (c in expression) {
                    if (c == '(') balance++
                    if (c == ')') balance--
                }
                expression = when {
                    balance > 0 && endsOperand -> expression + ")"
                    last != null && (last.isLetterOrDigit() || last == ')') ->
                        expression + "*("

                    else -> expression + "("
                }
            }

            "+", "*", "/" -> {
                if (expression.length >= 120) return
                when {
                    endsOperand -> expression += id
                    last != null && last in "+-*/" ->
                        expression = expression.dropLast(1) + id
                }
            }

            "-" -> {
                if (expression.length >= 120) return
                when {
                    expression.isEmpty() || last == '(' || last == '*' || last == '/' ->
                        expression += "-"

                    endsOperand -> expression += "-"
                    last == '+' -> expression = expression.dropLast(1) + "-"
                }
            }

            "." -> {
                val token = expression.takeLastWhile { it.isLetterOrDigit() || it == '.' }
                if (last != null && last.isLetterOrDigit() && !token.contains('.')) {
                    expression += "."
                }
            }

            else -> {
                // A digit or hex letter
                if (id.length == 1 && ictDigitEnabled(id[0], base)) {
                    if (expression.length >= 120) return
                    expression = if (last == ')') expression + "*" + id else expression + id
                }
            }
        }
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // Which keys may be offered right now — the user's rule:
    // a calculation that isn't possible doesn't get an option.
    val lastChar = expression.lastOrNull()
    val endsOperandNow = lastChar != null &&
            (lastChar.isLetterOrDigit() || lastChar == ')' || lastChar == '.')
    val canBinaryOperator = endsOperandNow
    val canMinus = expression.isEmpty() || lastChar == '(' || lastChar == '*' ||
            lastChar == '/' || endsOperandNow
    val currentToken = expression.takeLastWhile { it.isLetterOrDigit() || it == '.' }
    val canDot = lastChar != null && lastChar.isLetterOrDigit() && !currentToken.contains('.')
    val canEquals = eval is IctEval.Value
    val canEdit = expression.isNotEmpty()

    fun digitSpec(id: String): IctKeySpec {
        val isLetter = id.length == 1 && id[0] in 'A'..'F'
        return IctKeySpec(
            id = id,
            label = id,
            enabled = id.length == 1 && ictDigitEnabled(id[0], base),
            kind = if (isLetter) IctKeyKind.LETTER else IctKeyKind.DIGIT
        )
    }

    val keyRows: List<List<IctKeySpec>> = listOf(
        listOf("7", "8", "9", "A", "B").map { digitSpec(it) },
        listOf("4", "5", "6", "C", "D").map { digitSpec(it) },
        listOf("1", "2", "3", "E", "F").map { digitSpec(it) },
        listOf(
            IctKeySpec("()", "( )", true, IctKeyKind.OPERATOR),
            IctKeySpec("+", "+", canBinaryOperator, IctKeyKind.OPERATOR),
            IctKeySpec("-", "−", canMinus, IctKeyKind.OPERATOR),
            IctKeySpec("*", "×", canBinaryOperator, IctKeyKind.OPERATOR),
            IctKeySpec("/", "÷", canBinaryOperator, IctKeyKind.OPERATOR)
        ),
        listOf(
            IctKeySpec(".", ".", canDot, IctKeyKind.FUNCTION),
            digitSpec("0"),
            IctKeySpec("⌫", "⌫", canEdit, IctKeyKind.FUNCTION),
            IctKeySpec("AC", "AC", canEdit, IctKeyKind.CLEAR),
            IctKeySpec("=", "=", canEquals, IctKeyKind.EQUALS)
        )
    )

    CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            // LAYOUT by percentage: display card 44%, keypad 56%.
            // Inside the card there is NO dead spacer any more —
            // the display panel fills everything under the chips.
            // Background is the plain theme colour — no effects.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val windowW = maxWidth
                val windowH = maxHeight
                val minDim = minOf(windowW, windowH)
                // % SIZES (2026-11-07): sob window-er % - kono fixed
                // dp/sp nei (1.dp hairline chara). Normal 360x760
                // phone-e ager motoi dekhabe.
                val sidePad = windowW * 0.044f   // 16dp
                val topPad = windowH * 0.003f    // 2dp
                val bottomPad = windowH * 0.011f // 8dp
                val titleFont = (minDim.value * 0.061f).sp // 22sp
                val titleTopPad = windowH * 0.016f // 12dp
                val gapSm = windowH * 0.011f     // 8dp
                val cardCorner = minDim * 0.078f // 28dp
                val cardPadH = windowW * 0.044f  // 16dp
                val cardPadV = windowH * 0.016f  // 12dp
                val baseLabelFont = (minDim.value * 0.031f).sp // 11sp
                val gapXs = windowH * 0.005f     // 4dp
                val chipRowH = windowH * 0.058f  // 44dp
                val chipGapH = windowW * 0.022f  // 8dp
                val gapMd = windowH * 0.016f     // 12dp
                val panelCorner = minDim * 0.056f // 20dp
                val panelPadH = windowW * 0.039f // 14dp
                val panelPadTop = windowH * 0.021f // 16dp
                val panelPadBot = windowH * 0.016f // 12dp
                val baseShortFont = (minDim.value * 0.033f).sp // 12sp
                val tagGapW = windowW * 0.028f   // 10dp
                val exprFont = (minDim.value * 0.128f).sp // 46sp
                val exprLineH = (minDim.value * 0.139f).sp // 50sp
                val caretFont = (minDim.value * 0.111f).sp // 40sp
                val gapLg = windowH * 0.018f     // 14dp
                val resultFont = (minDim.value * 0.10f).sp // 36sp
                val resultLineH = (minDim.value * 0.111f).sp // 40sp
                val copyBtn = minDim * 0.10f     // 36dp
                val copyIcon = minDim * 0.05f    // 18dp
                val gapKeypad = windowH * 0.013f // 10dp
                val keyGapV = windowH * 0.012f   // 9dp
                val keyGapH = windowW * 0.025f   // 9dp
                // DRAWER SWIPE (universal rule): a leftward drag past
                // the threshold opens the app drawer, once per
                // gesture. Rightward drags are NOT consumed here.
                // Applied to the base-chips row and the keypad only
                // — never to the display panel, whose rows scroll
                // horizontally and must keep working.
                // pointerInput(minDim): window resize-e detector-ta
                // notun threshold niye abar shuru hoy.
                val drawerSwipe = Modifier.pointerInput(minDim) {
                    val thresholdPx = (minDim * 0.067f).toPx() // 24dp
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
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(start = sidePad, end = sidePad, top = topPad, bottom = bottomPad)
                ) {
                // Header — compact
                IctAnimatedIn(delayMs = 0, minDim = minDim) {
                    Column {
                        Text(
                            text = "ICT Calculator",
                            fontSize = titleFont,
                            fontWeight = FontWeight.Bold,
                            color = colors.onBackground,
                            modifier = Modifier.padding(top = titleTopPad)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(gapSm))

                // ===== 44% — glass card: base chips + FULL display panel
                IctAnimatedIn(modifier = Modifier.weight(0.44f), delayMs = 90, minDim = minDim) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(cardCorner))
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
                                        colors.onSurface.copy(alpha = 0.22f),
                                        colors.onSurface.copy(alpha = 0.05f)
                                    )
                                ),
                                shape = RoundedCornerShape(cardCorner)
                            )
                            .padding(horizontal = cardPadH, vertical = cardPadV)
                    ) {
                        Text(
                            text = "BASE",
                            fontSize = baseLabelFont,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(gapXs))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(chipRowH)
                                .then(drawerSwipe),
                            horizontalArrangement = Arrangement.spacedBy(chipGapH)
                        ) {
                            ictBases.forEach { ictBase ->
                                IctBaseChip(
                                    minDim = minDim,
                                    base = ictBase,
                                    selected = ictBase == base,
                                    modifier = Modifier.weight(1f),
                                    onClick = { switchBase(ictBase) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(gapMd))

                        // Display panel: fills ALL the remaining
                        // card height. Expression + result sit at
                        // its top, a little below the panel edge.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(panelCorner))
                                .background(colors.background.copy(alpha = 0.28f))
                                .border(
                                    width = 1.dp,
                                    color = colors.onSurface.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(panelCorner)
                                )
                                .padding(start = panelPadH, end = panelPadH, top = panelPadTop, bottom = panelPadBot),
                            verticalArrangement = Arrangement.Top
                        ) {
                            // Expression — big, single line, scrollable,
                            // with the typing pulse
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    text = base.short,
                                    fontSize = baseShortFont,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(tagGapW))
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .graphicsLayer {
                                            scaleX = expressionPulse.value
                                            scaleY = expressionPulse.value
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.horizontalScroll(expressionScrollState),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Text(
                                            text = expressionDisplay,
                                            fontSize = exprFont,
                                            lineHeight = exprLineH,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = FontFamily.Monospace,
                                            color = colors.onSurface,
                                            textAlign = TextAlign.End,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Text(
                                            text = "▌",
                                            fontSize = caretFont,
                                            lineHeight = exprLineH,
                                            color = colors.onSurface,
                                            modifier = Modifier.alpha(if (caretOn) 0.9f else 0f)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(gapLg))
                            HorizontalDivider(
                                color = colors.outline.copy(alpha = 0.25f)
                            )
                            Spacer(modifier = Modifier.height(gapLg))

                            // Live result — slides, fades and scales
                            // in on every change; division by zero
                            // shows as an error and disables "="
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
                                                    animationSpec = tween(200),
                                                    initialOffsetY = { it / 2 }
                                                ) + fadeIn(tween(200)) +
                                                        scaleIn(
                                                            animationSpec = tween(200),
                                                            initialScale = 0.92f
                                                        )) togetherWith
                                                        (slideOutVertically(
                                                            animationSpec = tween(150),
                                                            targetOffsetY = { -it / 2 }
                                                        ) + fadeOut(tween(150)) +
                                                                scaleOut(
                                                                    animationSpec = tween(150),
                                                                    targetScale = 0.92f
                                                                ))
                                            },
                                            label = "ictResult"
                                        ) { text ->
                                            Text(
                                                text = text,
                                                fontSize = resultFont,
                                                lineHeight = resultLineH,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = if (eval is IctEval.DivZero) {
                                                    colors.error
                                                } else {
                                                    colors.onSurface
                                                },
                                                textAlign = TextAlign.End,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val e = eval
                                        if (e is IctEval.Value) {
                                            clipboardManager.setText(AnnotatedString(e.raw))
                                        }
                                    },
                                    modifier = Modifier.size(copyBtn)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy result",
                                        tint = colors.onSurfaceVariant,
                                        modifier = Modifier.size(copyIcon)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(gapKeypad))

                // ===== 56% — keypad. Only possible keys are active:
                // digits outside the base, operators with nowhere
                // legal to go, and "=" for an impossible or
                // incomplete expression all stay dim and dead.
                IctAnimatedIn(
                    modifier = Modifier.weight(0.56f).then(drawerSwipe),
                    delayMs = 180,
                    minDim = minDim
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(keyGapV)
                    ) {
                        keyRows.forEach { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(keyGapH)
                            ) {
                                row.forEach { spec ->
                                    IctKey(
                                        minDim = minDim,
                                        spec = spec,
                                        modifier = Modifier.weight(1f),
                                        onLongClick = if (spec.id == "⌫") {
                                            { onKey("AC") }
                                        } else {
                                            null
                                        },
                                        onClick = { onKey(spec.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            } // BoxWithConstraints
        }
    }
}

// One-line base button: BIN / DEC / OCT / HEX.
@Composable
private fun IctBaseChip(
    minDim: Dp, // % base - caller passes window minDim
    base: IctBase,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (selected) colors.onBackground else colors.surfaceVariant.copy(alpha = 0.45f),
        animationSpec = tween(280),
        label = "ictChipBackground"
    )
    val content by animateColorAsState(
        targetValue = if (selected) colors.background else colors.onSurfaceVariant,
        animationSpec = tween(280),
        label = "ictChipContent"
    )
    val chipScale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        animationSpec = tween(280),
        label = "ictChipScale"
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            .scale(chipScale)
            .clip(RoundedCornerShape(minDim * 0.039f))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = base.short,
            fontSize = (minDim.value * 0.042f).sp,
            fontWeight = FontWeight.Bold,
            color = content,
            maxLines = 1
        )
    }
}

// One keypad key: glass tile filling its row height, press scale,
// standard clickable handling. Disabled = the calculation that key
// would start is not possible here, so it is not offered.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IctKey(
    minDim: Dp, // % base - caller passes window minDim
    spec: IctKeySpec,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val containerColor = when (spec.kind) {
        IctKeyKind.EQUALS -> colors.onBackground
        IctKeyKind.CLEAR -> colors.surfaceVariant
        IctKeyKind.OPERATOR, IctKeyKind.FUNCTION ->
            colors.surfaceVariant.copy(alpha = 0.75f)

        IctKeyKind.LETTER -> colors.surfaceVariant.copy(alpha = 0.75f)
        IctKeyKind.DIGIT -> colors.surfaceVariant
    }
    val contentColor = when (spec.kind) {
        IctKeyKind.EQUALS -> colors.background
        IctKeyKind.CLEAR -> colors.onSurface
        IctKeyKind.OPERATOR, IctKeyKind.FUNCTION, IctKeyKind.LETTER ->
            colors.onSurface

        IctKeyKind.DIGIT -> colors.onSurface
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && spec.enabled) 0.92f else 1f,
        animationSpec = tween(110),
        label = "ictKeyPressScale"
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            .scale(pressScale)
            .alpha(if (spec.enabled) 1f else 0.32f)
            .clip(RoundedCornerShape(minDim * 0.044f))
            .background(containerColor)
            .border(
                width = 1.dp,
                color = colors.onSurface.copy(alpha = 0.12f),
                shape = RoundedCornerShape(minDim * 0.044f)
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = spec.enabled,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = spec.label,
            fontSize = if (spec.label == "⌫") (minDim.value * 0.058f).sp else (minDim.value * 0.053f).sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

// Staggered entrance: fades + rises in after a small delay.
@Composable
private fun IctAnimatedIn(
    modifier: Modifier = Modifier,
    delayMs: Int,
    minDim: Dp, // % base - caller passes window minDim
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
        label = "ictEntrance"
    )
    Box(
        modifier = modifier
            .alpha(progress)
            .offset(y = ((1f - progress) * minDim.value * 0.072f).dp)
    ) {
        content()
    }
}
