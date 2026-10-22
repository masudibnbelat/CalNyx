package com.calnyx.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    onToggleTheme: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val clipboardManager = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current

    var base by remember { mutableStateOf(IctBase.BINARY) }
    // Internal expression is plain ASCII: digits, A-F, . + - * / ( )
    var expression by remember { mutableStateOf("") }

    val eval = remember(expression, base) { evaluateExpression(expression, base.radix) }
    val expressionDisplay = remember(expression) {
        formatIctExpression(expression).ifEmpty { "0" }
    }
    val resultDisplay = when (val e = eval) {
        is IctEval.Value -> (if (e.exact) "= " else "≈ ") + formatIctResult(e.raw, base)
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

    // Slow aurora drift behind everything (Converter v4 language).
    val drift = rememberInfiniteTransition(label = "ictDrift")
    val driftX by drift.animateFloat(
        initialValue = -26f,
        targetValue = 26f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ictDriftX"
    )
    val driftY by drift.animateFloat(
        initialValue = 18f,
        targetValue = -18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ictDriftY"
    )

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
            // Aurora blobs
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

            // LAYOUT by percentage: display card 48%, keypad 52%.
            // Inside the card there is NO dead spacer any more —
            // the display panel fills everything under the chips.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp)
            ) {
                // Header — compact
                IctAnimatedIn(delayMs = 0) {
                    Column {
                        Text(
                            text = "ICT Calculator",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onBackground
                        )
                        Text(
                            text = "Arithmetic in binary · octal · decimal · hex",
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ===== 48% — glass card: base chips + FULL display panel
                IctAnimatedIn(modifier = Modifier.weight(0.48f), delayMs = 90) {
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
                            text = "BASE",
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
                            ictBases.forEach { ictBase ->
                                IctBaseChip(
                                    base = ictBase,
                                    selected = ictBase == base,
                                    modifier = Modifier.weight(1f),
                                    onClick = { switchBase(ictBase) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Display panel: fills ALL the remaining
                        // card height. Expression + result are
                        // centred inside it, big.
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
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Expression — big, single line, scrollable,
                            // with the typing pulse
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    text = base.short,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(10.dp))
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
                                            fontSize = 46.sp,
                                            lineHeight = 50.sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = FontFamily.Monospace,
                                            color = colors.onSurface,
                                            textAlign = TextAlign.End,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Text(
                                            text = "▌",
                                            fontSize = 40.sp,
                                            lineHeight = 50.sp,
                                            color = colors.primary,
                                            modifier = Modifier.alpha(if (caretOn) 0.9f else 0f)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(
                                color = colors.outline.copy(alpha = 0.25f)
                            )
                            Spacer(modifier = Modifier.height(14.dp))

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
                                                fontSize = 36.sp,
                                                lineHeight = 40.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = if (eval is IctEval.DivZero) {
                                                    colors.error
                                                } else {
                                                    colors.primary
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
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy result",
                                        tint = colors.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ===== 52% — keypad. Only possible keys are active:
                // digits outside the base, operators with nowhere
                // legal to go, and "=" for an impossible or
                // incomplete expression all stay dim and dead.
                IctAnimatedIn(modifier = Modifier.weight(0.52f), delayMs = 180) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        keyRows.forEach { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                row.forEach { spec ->
                                    IctKey(
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
        }
    }
}

// One-line base button: BIN / DEC / OCT / HEX.
@Composable
private fun IctBaseChip(
    base: IctBase,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.surfaceVariant.copy(alpha = 0.45f),
        animationSpec = tween(280),
        label = "ictChipBackground"
    )
    val content by animateColorAsState(
        targetValue = if (selected) colors.onPrimary else colors.onSurfaceVariant,
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

// One keypad key: glass tile filling its row height, press scale,
// standard clickable handling. Disabled = the calculation that key
// would start is not possible here, so it is not offered.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IctKey(
    spec: IctKeySpec,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val containerColor = when (spec.kind) {
        IctKeyKind.EQUALS -> colors.primary
        IctKeyKind.CLEAR -> colors.primaryContainer
        IctKeyKind.OPERATOR, IctKeyKind.FUNCTION ->
            colors.secondaryContainer.copy(alpha = 0.75f)

        IctKeyKind.LETTER -> colors.secondaryContainer.copy(alpha = 0.75f)
        IctKeyKind.DIGIT -> colors.surfaceVariant.copy(alpha = 0.55f)
    }
    val contentColor = when (spec.kind) {
        IctKeyKind.EQUALS -> colors.onPrimary
        IctKeyKind.CLEAR -> colors.onPrimaryContainer
        IctKeyKind.OPERATOR, IctKeyKind.FUNCTION, IctKeyKind.LETTER ->
            colors.onSecondaryContainer

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
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(16.dp)
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
            fontSize = if (spec.label == "⌫") 21.sp else 19.sp,
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
            .offset(y = ((1f - progress) * 26f).dp)
    ) {
        content()
    }
}
