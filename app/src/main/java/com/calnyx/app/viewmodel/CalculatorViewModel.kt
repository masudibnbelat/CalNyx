package com.calnyx.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calnyx.app.CalNyxApp
import com.calnyx.app.data.CalNyxRepository
import com.calnyx.app.data.HistoryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.objecthunter.exp4j.ExpressionBuilder
import net.objecthunter.exp4j.function.Function as Exp4jFunction
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.random.Random

class CalculatorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CalNyxRepository

    init {
        val db = (application as CalNyxApp).database
        repository = CalNyxRepository(db.worksheetDao(), db.historyDao())
    }

    // Which calculator is using this instance — history entries are
    // saved under this type ("normal", "scientific", ...).
    var calculatorType: String = "normal"

    private val _expression = MutableStateFlow("")
    val expression: StateFlow<String> = _expression.asStateFlow()

    // Editing caret inside the expression (a raw character index,
    // 0..expression.length). The user taps the expression on screen
    // to move it; typing inserts AT the caret and ⌫ deletes BEFORE
    // it, so a mistyped digit in the middle (30001+20000 — the "1"
    // is wrong) can be fixed in place instead of retyping all.
    private val _cursorPosition = MutableStateFlow(0)
    val cursorPosition: StateFlow<Int> = _cursorPosition.asStateFlow()

    private val _result = MutableStateFlow("0")
    val result: StateFlow<String> = _result.asStateFlow()

    // Set when "=" completes a calculation: the expression is cleared
    // from the display and only this answer shows, big.
    private val _finalResult = MutableStateFlow<String?>(null)
    val finalResult: StateFlow<String?> = _finalResult.asStateFlow()

    private val _isDegrees = MutableStateFlow(false)
    val isDegrees: StateFlow<Boolean> = _isDegrees.asStateFlow()

    private var lastSavedKey: String? = null

    // Two-argument functions exp4j does not ship with: gcd(12,18), lcm(4,6)
    private val gcdFunction = object : Exp4jFunction("gcd", 2) {
        override fun apply(vararg args: Double): Double {
            val a = abs(args[0].roundToLong())
            val b = abs(args[1].roundToLong())
            return gcdLong(a, b).toDouble()
        }
    }

    private val lcmFunction = object : Exp4jFunction("lcm", 2) {
        override fun apply(vararg args: Double): Double {
            val a = abs(args[0].roundToLong())
            val b = abs(args[1].roundToLong())
            if (a == 0L || b == 0L) return 0.0
            return (a / gcdLong(a, b) * b).toDouble()
        }
    }

    fun setCursorPosition(position: Int) {
        _cursorPosition.value = position.coerceIn(0, _expression.value.length)
    }

    // Insert text at the caret and move the caret past it.
    private fun insertAtCursor(text: String) {
        val current = _expression.value
        val pos = _cursorPosition.value.coerceIn(0, current.length)
        _expression.value = current.substring(0, pos) + text + current.substring(pos)
        _cursorPosition.value = pos + text.length
    }

    // Replace the whole expression and park the caret (default: end).
    private fun setExpression(text: String, cursor: Int = text.length) {
        _expression.value = text
        _cursorPosition.value = cursor.coerceIn(0, text.length)
    }

    // Delete the raw range [start, end) — used when a selection on
    // screen is cut, or replaced by the next key the user types.
    fun deleteRange(start: Int, end: Int) {
        val current = _expression.value
        val s = start.coerceIn(0, current.length)
        val e = end.coerceIn(s, current.length)
        if (s == e) return
        _finalResult.value = null
        _expression.value = current.removeRange(s, e)
        _cursorPosition.value = s
        updatePreview()
    }

    // Paste clipboard text at the caret. Anything calculator-shaped
    // survives (digits, operators, known functions); everything else
    // is dropped, so pasting "10,000−10%" from the display — or a
    // calculation copied from another app — lands clean.
    fun pasteText(text: String) {
        val clean = sanitizePaste(text)
        if (clean.isEmpty()) return
        if (_finalResult.value != null) {
            setExpression("")
            _finalResult.value = null
        }
        insertAtCursor(clean)
        updatePreview()
    }

    private fun sanitizePaste(text: String): String {
        val mapped = text
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("√", "sqrt(")
            .replace("π", "pi")
            .replace(",", "")
            .replace(" ", "")
            .replace("\n", "")
        val knownWords = setOf(
            "sqrt", "log10", "asin", "acos", "atan", "sinh", "cosh", "tanh",
            "log2", "cbrt", "floor", "ceil", "gcd", "lcm",
            "sin", "cos", "tan", "log", "exp", "abs", "mod", "pi"
        )
        val sb = StringBuilder()
        var i = 0
        while (i < mapped.length) {
            val c = mapped[i]
            when {
                c.isLetter() -> {
                    var j = i
                    while (j < mapped.length && (mapped[j].isLetter() || mapped[j].isDigit())) j++
                    val word = mapped.substring(i, j)
                    when {
                        word == "ln" -> sb.append("log(")
                        word in knownWords -> {
                            sb.append(word)
                            if (word != "pi" && word != "mod") {
                                // Functions take their "(" along; if
                                // the pasted text already has it, it is
                                // consumed instead of doubled.
                                if (j < mapped.length && mapped[j] == '(') j++
                                sb.append('(')
                            }
                        }

                        else -> {
                            // Unknown word — dropped
                        }
                    }
                    i = j
                }

                c in "0123456789.+-*/%()^!" -> {
                    sb.append(c)
                    i++
                }

                else -> i++
            }
        }
        return sb.toString()
    }

    fun onButtonClick(label: String) {
        if (label == "Ans") {
            insertAnswer()
            return
        }

        // Behaviour right after a completed "=" calculation
        val final = _finalResult.value
        if (final != null && label != "=") {
            val startsFresh = (label.length == 1 && (label[0].isDigit() || label == ".")) ||
                    label in listOf(
                "()", "sin", "cos", "tan", "asin", "acos", "atan",
                "sinh", "cosh", "tanh",
                "ln", "log", "exp", "10^", "√", "π", "e", "abs",
                "∛", "log2", "floor", "ceil", "gcd", "lcm"
            )
            val chainsFromAnswer = label in listOf(
                "+", "−", "×", "÷", "%", "^", "!",
                "x²", "x⁻¹", "x³", "EXP", "ROOT"
            )
            when {
                startsFresh -> {
                    setExpression("")
                    _finalResult.value = null
                }

                chainsFromAnswer -> {
                    setExpression(final)
                    _finalResult.value = null
                }

                label == "⌫" || label == "AC" || label == "C" -> {
                    _finalResult.value = null
                }
            }
        }

        when (label) {
            "C", "AC" -> {
                setExpression("")
                _result.value = "0"
                _finalResult.value = null
                lastSavedKey = null
            }

            "⌫" -> {
                // Delete BEFORE the caret — a whole function token
                // (sin(, log10(, ...) goes in one press, like before.
                val current = _expression.value
                val pos = _cursorPosition.value.coerceIn(0, current.length)
                if (pos > 0) {
                    val before = current.substring(0, pos)
                    val token = listOf(
                        "asin(", "acos(", "atan(", "log10(", "sqrt(",
                        "sinh(", "cosh(", "tanh(",
                        "log2(", "cbrt(", "floor(", "ceil(", "gcd(", "lcm(",
                        "sin(", "cos(", "tan(", "log(", "exp(", "10^(", "abs("
                    ).firstOrNull { before.endsWith(it) }
                    val drop = token?.length ?: 1
                    _expression.value = current.removeRange(pos - drop, pos)
                    _cursorPosition.value = pos - drop
                }
            }

            "=" -> calculate()
            "ANGLE" -> {
                _isDegrees.value = !_isDegrees.value
            }

            "()" -> {
                val current = _expression.value
                val pos = _cursorPosition.value.coerceIn(0, current.length)
                val opens = current.count { it == '(' }
                val closes = current.count { it == ')' }
                val last = if (pos > 0) current[pos - 1] else null
                val shouldClose = opens > closes &&
                        (last?.isDigit() == true || last == ')' || last == '%')
                insertAtCursor(if (shouldClose) ")" else "(")
            }

            "sin" -> insertAtCursor("sin(")
            "cos" -> insertAtCursor("cos(")
            "tan" -> insertAtCursor("tan(")
            "asin" -> insertAtCursor("asin(")
            "acos" -> insertAtCursor("acos(")
            "atan" -> insertAtCursor("atan(")
            "sinh" -> insertAtCursor("sinh(")
            "cosh" -> insertAtCursor("cosh(")
            "tanh" -> insertAtCursor("tanh(")
            "ln" -> insertAtCursor("log(")
            "log" -> insertAtCursor("log10(")
            "log2" -> insertAtCursor("log2(")
            "exp" -> insertAtCursor("exp(")
            "10^" -> insertAtCursor("10^(")
            "√" -> insertAtCursor("sqrt(")
            "∛" -> insertAtCursor("cbrt(")
            "floor" -> insertAtCursor("floor(")
            "ceil" -> insertAtCursor("ceil(")
            "gcd" -> insertAtCursor("gcd(")
            "lcm" -> insertAtCursor("lcm(")
            "mod" -> insertAtCursor("mod")
            "π" -> insertAtCursor("pi")
            "abs" -> insertAtCursor("abs(")
            "x²" -> insertAtCursor("^2")
            "x⁻¹" -> insertAtCursor("^(-1)")
            "x³" -> insertAtCursor("^3")
            "ROOT" -> insertAtCursor("^(1/")

            "EXP" -> {
                // Casio-style exponent entry: the digits typed next
                // become a power of ten. After a value it multiplies
                // (2 Exp 3 -> 2*10^(3 = 2000); standing alone it is
                // 1×10^ (Exp 5 -> 10^(5 = 100000).
                val current = _expression.value
                val pos = _cursorPosition.value.coerceIn(0, current.length)
                val before = if (pos > 0) current[pos - 1] else null
                val hasOperandBefore = before != null &&
                        (before.isDigit() || before == ')' || before == '%' || before == '!')
                insertAtCursor(if (hasOperandBefore) "*10^(" else "10^(")
            }

            "nPr" -> insertAtCursor("P")
            "nCr" -> insertAtCursor("C")
            "Rand" -> {
                val value = String.format("%.6f", Random.nextDouble())
                    .trimEnd('0').trimEnd('.')
                insertAtCursor(value)
            }

            "RanInt" -> insertAtCursor(Random.nextInt(0, 1000).toString())
            else -> {
                insertAtCursor(
                    when (label) {
                        "×" -> "*"
                        "÷" -> "/"
                        "−" -> "-"
                        else -> label
                    }
                )
            }
        }
        if (label != "=") {
            updatePreview()
        }
    }

    private fun insertAnswer() {
        val ans = _finalResult.value ?: _result.value
        if (ans == "0" || ans == "Error") return
        if (_finalResult.value != null) {
            setExpression(ans)
            _finalResult.value = null
        } else {
            insertAtCursor(ans)
        }
        updatePreview()
    }

    private fun calculate() {
        val finalValue = evaluate(_expression.value)
        if (finalValue == null) {
            _result.value = "Error"
        } else {
            _result.value = finalValue
            saveCurrentToHistory()
            _finalResult.value = finalValue
            setExpression("")
        }
    }

    private fun updatePreview() {
        val current = _expression.value
        val hasOperator = current.any { it in "+-*/%^()PC" } || current.contains("mod")
        _result.value = if (!hasOperator) {
            "0"
        } else {
            evaluate(current) ?: "0"
        }
    }

    private fun evaluate(raw: String): String? {
        var expr = raw
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")

        // Phone-calculator percent (see applyPercentRules): after a
        // + or − the % is a share of the base value, everywhere else
        // it is a plain hundredth. This replaces the old blanket
        // "% -> /100", which made 10000-10% come out as 9999.90.
        expr = applyPercentRules(expr)

        // Modulo for number literals: 10mod3 -> 1
        expr = Regex("([\\d.]+)mod([\\d.]+)").replace(expr) { match ->
            val a = match.groupValues[1].toDouble()
            val b = match.groupValues[2].toDouble()
            if (b == 0.0) {
                match.value
            } else {
                val remainder = a % b
                if (remainder % 1.0 == 0.0) {
                    remainder.toLong().toString()
                } else {
                    remainder.toString()
                }
            }
        }

        // Permutations / combinations for integer literals:
        // 5P2 -> 20, 5C2 -> 10 (n and r in 0..20, r <= n)
        expr = Regex("(\\d+)P(\\d+)").replace(expr) { match ->
            val n = match.groupValues[1].toInt()
            val r = match.groupValues[2].toInt()
            if (n in 0..20 && r in 0..n) {
                (factorialLong(n) / factorialLong(n - r)).toString()
            } else {
                match.value
            }
        }
        expr = Regex("(\\d+)C(\\d+)").replace(expr) { match ->
            val n = match.groupValues[1].toInt()
            val r = match.groupValues[2].toInt()
            if (n in 0..20 && r in 0..n) {
                (factorialLong(n) / (factorialLong(r) * factorialLong(n - r))).toString()
            } else {
                match.value
            }
        }

        // Factorial for plain number literals: 5! -> 120 (integers 0..20)
        expr = Regex("(\\d+(?:\\.\\d+)?)!").replace(expr) { match ->
            val n = match.groupValues[1].toDouble()
            if (n % 1.0 == 0.0 && n in 0.0..20.0) {
                var factorial = 1L
                for (i in 2..n.toInt()) factorial *= i
                factorial.toString()
            } else {
                match.value
            }
        }

        // Degree mode: convert trig arguments at evaluation time only —
        // the stored/displayed expression is never changed.
        if (_isDegrees.value) {
            expr = Regex("(?<![a-zA-Z])asin\\(").replace(expr, "((180/pi)*asin(")
            expr = Regex("(?<![a-zA-Z])acos\\(").replace(expr, "((180/pi)*acos(")
            expr = Regex("(?<![a-zA-Z])atan\\(").replace(expr, "((180/pi)*atan(")
            expr = Regex("(?<![a-zA-Z])sin\\(").replace(expr, "sin((pi/180)*")
            expr = Regex("(?<![a-zA-Z])cos\\(").replace(expr, "cos((pi/180)*")
            expr = Regex("(?<![a-zA-Z])tan\\(").replace(expr, "tan((pi/180)*")
        }

        while (expr.isNotEmpty() && expr.last() in "+-*/.(PC") {
            expr = expr.dropLast(1)
        }
        if (expr.isBlank()) return null

        val opens = expr.count { it == '(' }
        val closes = expr.count { it == ')' }
        if (closes > opens) return null
        if (opens > closes) {
            expr += ")".repeat(opens - closes)
        }

        return try {
            val built = ExpressionBuilder(expr)
                .function(gcdFunction)
                .function(lcmFunction)
                .build()
            formatResult(built.evaluate())
        } catch (e: Exception) {
            null
        }
    }

    // Phone-calculator percent rules, applied as a text rewrite
    // before exp4j ever sees the expression:
    //   10000-10%  ->  10000-((10000)*10/100)  = 9000
    //   10000+10%  ->  11000
    //   200*10%    ->  200*(10/100)           = 20
    //   200/10%    ->  200/(10/100)           = 2000
    //   50%        ->  (50/100)               = 0.5
    // The percent operand is the number literal or (...) group
    // directly before the %. Chained percents compound on the
    // running left side: 100+10%+20% = 132. The same helper lives in
    // CalTapeViewModel for the tape's bracket expressions.
    private fun applyPercentRules(input: String): String {
        var expr = input
        var guard = 0
        while ('%' in expr && guard++ < 50) {
            val pct = expr.indexOf('%')

            // Find the operand directly before the %.
            var bStart = pct
            var hasOperand = false
            if (pct > 0) {
                val before = expr[pct - 1]
                if (before.isDigit() || before == '.') {
                    var s = pct - 1
                    while (s >= 0 && (expr[s].isDigit() || expr[s] == '.')) s--
                    bStart = s + 1
                    hasOperand = true
                } else if (before == ')') {
                    var depth = 0
                    var s = pct - 1
                    while (s >= 0) {
                        if (expr[s] == ')') depth++
                        if (expr[s] == '(') {
                            depth--
                            if (depth == 0) break
                        }
                        s--
                    }
                    if (s >= 0) {
                        bStart = s
                        hasOperand = true
                    }
                }
            }

            if (!hasOperand) {
                // Nothing the % can take (start of the expression,
                // after "(" or after a letter): plain hundredth.
                expr = expr.replaceRange(pct, pct + 1, "/100")
                continue
            }

            val operand = expr.substring(bStart, pct)
            val opIndex = bStart - 1
            val op = if (opIndex >= 0) expr[opIndex] else null
            val isBinaryPlusMinus = (op == '+' || op == '-') && opIndex > 0 &&
                    (expr[opIndex - 1].isDigit() || expr[opIndex - 1] == '.' ||
                            expr[opIndex - 1] == ')' || expr[opIndex - 1] == '%')

            expr = if (isBinaryPlusMinus) {
                // % of the whole left side: A op B% -> A op ((A)*B/100)
                val left = expr.substring(0, opIndex)
                left + op + "((" + left + ")*" + operand + "/100)" + expr.substring(pct + 1)
            } else {
                // Plain hundredth: B% -> (B/100)
                expr.substring(0, bStart) + "(" + operand + "/100)" + expr.substring(pct + 1)
            }
        }
        return expr
    }

    private fun gcdLong(a: Long, b: Long): Long {
        return if (b == 0L) a else gcdLong(b, a % b)
    }

    private fun factorialLong(n: Int): Long {
        var factorial = 1L
        for (i in 2..n) factorial *= i
        return factorial
    }

    private fun formatResult(resultValue: Double): String {
        return if (resultValue % 1.0 == 0.0) {
            resultValue.toLong().toString()
        } else {
            String.format("%.8f", resultValue).trimEnd('0').trimEnd('.')
        }
    }

    private fun saveCurrentToHistory() {
        val key = _expression.value + "=" + _result.value
        if (key == lastSavedKey) return
        lastSavedKey = key
        val savedExpression = _expression.value
        val savedResult = _result.value
        val savedType = calculatorType
        viewModelScope.launch {
            repository.insertHistory(
                HistoryEntity(
                    expression = savedExpression,
                    result = savedResult,
                    calculatorType = savedType
                )
            )
        }
    }

    fun onHistoryItemClick(resultValue: String) {
        setExpression(resultValue)
        _finalResult.value = null
        updatePreview()
    }

    fun saveToHistory(expression: String, result: String) {
        viewModelScope.launch {
            repository.insertHistory(
                HistoryEntity(
                    expression = expression,
                    result = result,
                    calculatorType = calculatorType
                )
            )
        }
    }
}
