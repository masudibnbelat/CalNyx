package com.calnyx.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calnyx.app.CalNyxApp
import com.calnyx.app.data.CalNyxRepository
import com.calnyx.app.data.HistoryEntity
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.objecthunter.exp4j.ExpressionBuilder
import net.objecthunter.exp4j.function.Function as Exp4jFunction

// ANS version, based on OPERATORFIX (2026-11-06). The "Ans" key is
// now a real scientific-calculator Answer:
//  - the display shows the literal token "Ans" (not the number);
//  - it evaluates to the exact value of the last "=" result,
//    stored as a Double (full precision, NOT the rounded display
//    string — formatResult trims to 8 decimals);
//  - "Ans+5", "Ans*2" etc. all calculate; implicit multiplication
//    is made explicit on insert (2Ans -> 2xAns) so it can never
//    silently become 242. Starts at 0, like a real calculator.
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

    // Degree is the DEFAULT angle mode (chosen by the user); the
    // Scientific screen's ANGLE key can still switch to radians.
    private val _isDegrees = MutableStateFlow(true)
    val isDegrees: StateFlow<Boolean> = _isDegrees.asStateFlow()

    private var lastSavedKey: String? = null

    // ANS — the exact value of the last "=" result, full Double
    // precision. The display shows the literal token "Ans";
    // evaluate() hands it to exp4j as a variable. Only "=" may
    // change it, never the live preview. Starts at 0.
    private var ansValue: Double = 0.0

    // The raw Double of the last evaluate() call. calculate()
    // copies it into ansValue.
    private var lastRawResult: Double? = null

    // NTH ROOT (2026-11-08): the root degree the user picks on the
    // Normal screen (default 3 = cubic root). 2..99, saved in
    // SharedPreferences so the picked degree survives reopen.
    // exp4j function: nthroot(3,27) = 3, nthroot(4,16) = 2.
    // Odd root of a negative number is negative (-27 -> -3);
    // an even root of a negative number is not real -> NaN.
    private val rootPrefs =
            application.getSharedPreferences("calnyx_root", Context.MODE_PRIVATE)
    private val _rootDegree =
            MutableStateFlow(rootPrefs.getInt("root_degree", 3).coerceIn(2, 99))
    val rootDegree: StateFlow<Int> = _rootDegree.asStateFlow()

    fun setRootDegree(n: Int) {
        val clamped = n.coerceIn(2, 99)
        _rootDegree.value = clamped
        rootPrefs.edit().putInt("root_degree", clamped).apply()
    }

    private val nthRootFunction =
            object : Exp4jFunction("nthroot", 2) {
                override fun apply(vararg args: Double): Double {
                    val n = args[0]
                    val x = args[1]
                    if (n == 0.0) return Double.NaN
                    if (x < 0.0) {
                        return if (n % 2.0 == 1.0) -((-x).pow(1.0 / n)) else Double.NaN
                    }
                    return x.pow(1.0 / n)
                }
            }

    // Two-argument functions exp4j does not ship with: gcd(12,18), lcm(4,6)
    private val gcdFunction =
            object : Exp4jFunction("gcd", 2) {
                override fun apply(vararg args: Double): Double {
                    val a = abs(args[0].roundToLong())
                    val b = abs(args[1].roundToLong())
                    return gcdLong(a, b).toDouble()
                }
            }

    private val lcmFunction =
            object : Exp4jFunction("lcm", 2) {
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

    // Insert a binary operator (+ - * / ^) so that the result
    // can always be calculated: never two binary operators in a
    // row, except one unary minus after a binary operator.
    // Examples: 5 ×, ÷ -> 5/ ; 5 +, + -> 5+ ; 5 ×, − -> 5*- ;
    // 5*-, ÷ -> 5/ ; empty or (, then ×/÷/+ -> ignored, but
    // − is allowed (negative number: -5, (-5).
    private fun insertOperator(op: String) {
        val current = _expression.value
        val pos = _cursorPosition.value.coerceIn(0, current.length)
        val before = current.substring(0, pos)
        val after = current.substring(pos)
        val binaryOps = "+-*/^"

        // Caret in the middle, just before an operator, after a
        // number: replace THAT operator (5[|]+3, × -> 5×3).
        if (before.isNotEmpty() &&
                        before.last() !in binaryOps &&
                        before.last() != '(' &&
                        after.isNotEmpty() &&
                        after.first() in binaryOps
        ) {
            _expression.value = before + op + after.drop(1)
            _cursorPosition.value = pos + 1
            return
        }

        // Tail is "operator + minus sign", e.g. "5*-":
        // minus again -> ignore; any other operator replaces
        // both (5*-, ÷ -> 5/).
        if (before.length >= 2 &&
                        before.last() == '-' &&
                        before[before.length - 2] in binaryOps
        ) {
            if (op == "-") return
            _expression.value = before.dropLast(2) + op + after
            _cursorPosition.value = pos - 1
            return
        }

        // Tail is a single binary operator, e.g. "5*":
        if (before.isNotEmpty() && before.last() in binaryOps) {
            // Minus after an operator is a sign, not a second
            // operator: 5* -> 5*- (so 5*-3 can be typed).
            if (op == "-" && before.last() != '-') {
                insertAtCursor("-")
                return
            }
            // Any other case: replace the operator (5* -> 5/,
            // and 5- then - does NOT become 5--).
            if (op != "-") {
                _expression.value = before.dropLast(1) + op + after
                _cursorPosition.value = pos
            }
            return
        }

        // No left operand yet: only minus may start a number.
        if (before.isEmpty() || before.last() == '(') {
            if (op == "-") insertAtCursor("-")
            return
        }

        // Normal case: number, ')', '%', '!' etc. before it.
        insertAtCursor(op)
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
        val mapped =
                text.replace("×", "*")
                        .replace("÷", "/")
                        .replace("−", "-")
                        .replace("√", "sqrt(")
                        .replace("π", "pi")
                        .replace(",", "")
                        .replace(" ", "")
                        .replace("\n", "")
        val knownWords =
                setOf(
                        "sqrt",
                        "log10",
                        "asin",
                        "acos",
                        "atan",
                        "sinh",
                        "cosh",
                        "tanh",
                        "log2",
                        "cbrt",
                        "nthroot",
                        "floor",
                        "ceil",
                        "gcd",
                        "lcm",
                        "sin",
                        "cos",
                        "tan",
                        "log",
                        "exp",
                        "abs",
                        "mod",
                        "pi"
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
                        word == "Ans" -> sb.append("Ans")
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
            val startsFresh =
                    (label.length == 1 && (label[0].isDigit() || label == ".")) ||
                            label in
                                    listOf(
                                            "()",
                                            "sin",
                                            "cos",
                                            "tan",
                                            "asin",
                                            "acos",
                                            "atan",
                                            "sinh",
                                            "cosh",
                                            "tanh",
                                            "ln",
                                            "log",
                                            "exp",
                                            "10^",
                                            "√",
                                            "π",
                                            "e",
                                            "abs",
                                            "∛",
                                            "NROOT",
                                            "log2",
                                            "floor",
                                            "ceil",
                                            "gcd",
                                            "lcm"
                                    )
            val chainsFromAnswer =
                    label in
                            listOf(
                                    "+",
                                    "−",
                                    "×",
                                    "÷",
                                    "%",
                                    "^",
                                    "!",
                                    "x²",
                                    "x⁻¹",
                                    "x³",
                                    "EXP",
                                    // E-UPDATE: "=" -এর পর E চাপলে আগের আনসার থেকে
                                    // chain হবে (যেমন: আনসার 5, তারপর E, 2 -> 500)।
                                    // এটা না থাকলে E চাপলে finalResult আটকে থাকত।
                                    "E",
                                    "ROOT"
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
                    val token =
                            listOf(
                                            "asin(",
                                            "acos(",
                                            "atan(",
                                            "log10(",
                                            "sqrt(",
                                            "sinh(",
                                            "cosh(",
                                            "tanh(",
                                            "log2(",
                                            "cbrt(",
                                            "floor(",
                                            "ceil(",
                                            "gcd(",
                                            "lcm(",
                                            "sin(",
                                            "cos(",
                                            "tan(",
                                            "log(",
                                            "exp(",
                                            "10^(",
                                            "abs("
                                    )
                                    .firstOrNull { before.endsWith(it) }
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
                val shouldClose =
                        opens > closes && (last?.isDigit() == true || last == ')' || last == '%')
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
            // NROOT: nth root with the picked degree, e.g. degree 4
            // inserts nthroot(4,  - the user types the number, then
            // ")". After a number / ")" / "%" / "!" a "x" goes in
            // first, so 2 + root key = 2*nthroot(4,16) (like Ans).
            "NROOT" -> {
                val current = _expression.value
                val pos = _cursorPosition.value.coerceIn(0, current.length)
                val before = if (pos > 0) current[pos - 1] else null
                val prefix =
                        if (before != null &&
                                        (before.isDigit() ||
                                                before == ')' ||
                                                before == '%' ||
                                                before == '!')
                        ) "*"
                        else ""
                insertAtCursor(prefix + "nthroot(" + _rootDegree.value + ",")
            }
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
                val hasOperandBefore =
                        before != null &&
                                (before.isDigit() ||
                                        before == ')' ||
                                        before == '%' ||
                                        before == '!')
                insertAtCursor(if (hasOperandBefore) "*10^(" else "10^(")
            }
            // OPERATOR FIX: only operators that can be calculated
            // may appear. Pressing an operator right after another
            // operator REPLACES the old one (5 ×, then ÷ -> 5 ÷),
            // it never appends a second one (never "5×÷").
            // The one exception is minus as a sign: 5 ×, then −
            // -> "5*-", because 5×-3 is a valid calculation.
            "+", "−", "×", "÷", "^" -> {
                val op =
                        when (label) {
                            "×" -> "*"
                            "÷" -> "/"
                            "−" -> "-"
                            else -> label
                        }
                insertOperator(op)
            }
            "%" -> {
                // % is a postfix value, not a binary operator:
                // it needs a number before it. 5%× works, but
                // ×% or %% can never be calculated, so ignore them.
                val current = _expression.value
                val pos = _cursorPosition.value.coerceIn(0, current.length)
                val before = if (pos > 0) current[pos - 1] else null
                if (before != null && before !in "+-*/^(%") {
                    insertAtCursor("%")
                }
            }
            "." -> {
                // DECIMAL FIX (2026-11-07): one dot per number -
                // "5...6" must be impossible. Scan the number
                // being typed on BOTH sides of the caret: back
                // over digits/dots/an E-exponent, forward over
                // digits/dots. If it already has a dot, or the
                // dot would land inside an E-exponent (2E3.),
                // ignore the key. "5+6." works, "5.6" + "." does
                // not, and mid-number caret works too (5|.6).
                val current = _expression.value
                val pos = _cursorPosition.value.coerceIn(0, current.length)
                var hasDot = false
                var hasE = false
                var i = pos - 1
                while (i >= 0) {
                    val c = current[i]
                    if (c.isDigit() || c == '.') {
                        if (c == '.') hasDot = true
                        i--
                    } else if (c == 'E') {
                        hasE = true
                        i--
                    } else break
                }
                var j = pos
                while (j < current.length) {
                    val c = current[j]
                    if (c.isDigit() || c == '.') {
                        if (c == '.') hasDot = true
                        j++
                    } else break
                }
                if (!hasDot && !hasE) insertAtCursor(".")
            }
            "nPr" -> insertAtCursor("P")
            "nCr" -> insertAtCursor("C")
            "Rand" -> {
                val value = String.format("%.6f", Random.nextDouble()).trimEnd('0').trimEnd('.')
                insertAtCursor(value)
            }
            "RanInt" -> insertAtCursor(Random.nextInt(0, 1000).toString())
            else -> {
                // E-UPDATE: "E" এখানে এসে else-তে insertAtCursor("E")
                // হবে — অর্থাৎ expression-এ "2E3" লেখা থাকবে, আর
                // evaluate()-এর E-UPDATE সেটাকে (2*10^3) বানাবে।
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

    // Insert the literal "Ans" token at the caret. After "=" it
    // starts a fresh expression with just "Ans". Implicit
    // multiplication is made explicit so "2Ans" can never
    // silently become 242: a "×" goes in when Ans touches a
    // number, ")", "%", "!" or a letter on either side
    // (2Ans -> 2×Ans, Ans2 -> Ans×2, Ans( -> Ans×(, pi Ans
    // -> pi×Ans). The value itself is NOT inserted — evaluate()
    // resolves the token to ansValue.
    private fun insertAnswer() {
        if (_finalResult.value != null) {
            setExpression("Ans")
            _finalResult.value = null
            updatePreview()
            return
        }
        val current = _expression.value
        val pos = _cursorPosition.value.coerceIn(0, current.length)
        val before = if (pos > 0) current[pos - 1] else null
        val after = if (pos < current.length) current[pos] else null
        val needsTimesBefore =
                before != null &&
                        (before.isDigit() ||
                                before == '.' ||
                                before == ')' ||
                                before == '%' ||
                                before == '!' ||
                                before.isLetter())
        val needsTimesAfter =
                after != null &&
                        (after.isDigit() || after == '.' || after == '(' || after.isLetter())
        val text = buildString {
            if (needsTimesBefore) append('×')
            append("Ans")
            if (needsTimesAfter) append('×')
        }
        insertAtCursor(text)
        updatePreview()
    }

    private fun calculate() {
        val finalValue = evaluate(_expression.value)
        if (finalValue == null) {
            _result.value = "Error"
        } else {
            _result.value = finalValue
            // The exact (unrounded) result becomes the new Answer.
            lastRawResult?.let { ansValue = it }
            saveCurrentToHistory()
            _finalResult.value = finalValue
            setExpression("")
        }
    }

    private fun updatePreview() {
        val current = _expression.value
        // E-UPDATE: 'E' যোগ করা হয়েছে, না হলে "2E3" লিখলেও
        // preview "0" দেখাত, কারণ E-কে operator ধরা হতো না।
        val hasOperator = current.any { it in "+-*/%^()PCE" } || current.contains("mod") || current.contains("Ans")
        _result.value =
                if (!hasOperator) {
                    "0"
                } else {
                    evaluate(current) ?: "0"
                }
    }

    private fun evaluate(raw: String): String? {
        var expr = raw.replace("×", "*").replace("÷", "/").replace("−", "-")

        // E-UPDATE: exp4j বড় হাতের "E" নিজে বোঝে না (ওটা unknown
        // variable হিসেবে fail করত, "=" -এ Error আসত)।
        // তাই exp4j-এর আগে E-notation বদলে নেওয়া হলো:
        //   a E b = a * 10^b, বামে সংখ্যা না থাকলে 1
        //   E2 -> (1*10^2) = 100
        //   2E3 -> (2*10^3) = 2000
        //   5E7 -> (5*10^7) = 50000000
        // "exp(" (ছোট হাতের eˣ) -এর সাথে এটা মিলবে না।
        expr =
                Regex("(\\d+(?:\\.\\d+)?)?E(-?\\d+)").replace(expr) { match ->
                    val coeff = match.groupValues[1].ifEmpty { "1" }
                    val exp = match.groupValues[2]
                    "($coeff*10^$exp)"
                }

        // Phone-calculator percent (see applyPercentRules): after a
        // + or − the % is a share of the base value, everywhere else
        // it is a plain hundredth. This replaces the old blanket
        // "% -> /100", which made 10000-10% come out as 9999.90.
        expr = applyPercentRules(expr)

        // Modulo for number literals: 10mod3 -> 1
        expr =
                Regex("((?:Ans|[\\d.]+))mod((?:Ans|[\\d.]+))").replace(expr) { match ->
                    val a = if (match.groupValues[1] == "Ans") ansValue else match.groupValues[1].toDouble()
                    val b = if (match.groupValues[2] == "Ans") ansValue else match.groupValues[2].toDouble()
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
        expr =
                Regex("(\\d+)P(\\d+)").replace(expr) { match ->
                    val n = match.groupValues[1].toInt()
                    val r = match.groupValues[2].toInt()
                    if (n in 0..20 && r in 0..n) {
                        (factorialLong(n) / factorialLong(n - r)).toString()
                    } else {
                        match.value
                    }
                }
        expr =
                Regex("(\\d+)C(\\d+)").replace(expr) { match ->
                    val n = match.groupValues[1].toInt()
                    val r = match.groupValues[2].toInt()
                    if (n in 0..20 && r in 0..n) {
                        (factorialLong(n) / (factorialLong(r) * factorialLong(n - r))).toString()
                    } else {
                        match.value
                    }
                }

        // Factorial for plain number literals: 5! -> 120 (integers 0..20)
        expr =
                Regex("((?:Ans|\\d+(?:\\.\\d+)?))!").replace(expr) { match ->
                    val operand = match.groupValues[1]
                    val n = if (operand == "Ans") ansValue else operand.toDouble()
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
            val built =
                    ExpressionBuilder(expr)
                            .variable("Ans")
                            .function(gcdFunction)
                            .function(lcmFunction)
                            .function(nthRootFunction)
                            .build()
            built.setVariable("Ans", ansValue)
            val value = built.evaluate()
            // Not a real number (e.g. even root of a negative
            // number) -> Error, never "NaN" / "Infinity" on screen.
            if (value.isNaN() || value.isInfinite()) return null
            lastRawResult = value
            formatResult(value)
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
            val isBinaryPlusMinus =
                    (op == '+' || op == '-') &&
                            opIndex > 0 &&
                            (expr[opIndex - 1].isDigit() ||
                                    expr[opIndex - 1] == '.' ||
                                    expr[opIndex - 1] == ')' ||
                                    expr[opIndex - 1] == '%')

            expr =
                    if (isBinaryPlusMinus) {
                        // % of the whole left side: A op B% -> A op ((A)*B/100)
                        val left = expr.substring(0, opIndex)
                        left + op + "((" + left + ")*" + operand + "/100)" + expr.substring(pct + 1)
                    } else {
                        // Plain hundredth: B% -> (B/100)
                        expr.substring(0, bStart) +
                                "(" +
                                operand +
                                "/100)" +
                                expr.substring(pct + 1)
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
            // FIXED: toLong() saturates past ±9.2e18 and prints a
            // wrong number (Long.MIN/MAX) for big results.
            // BigInteger keeps every digit the Double actually has.
            java.math.BigDecimal.valueOf(resultValue).toBigInteger().toString()
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
