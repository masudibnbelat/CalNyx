package com.calnyx.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calnyx.app.CalNyxApp
import com.calnyx.app.data.HistoryEntity
import com.calnyx.app.data.NavStateDatabase
import com.calnyx.app.data.SheetEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.objecthunter.exp4j.ExpressionBuilder
import java.util.UUID

// Worksheet/tape state for the CalTape screen, organised in SHEETS.
// Sheets can be created, switched, renamed and deleted. Every
// committed line is a row in the shared Room history
// (calculatorType "caltape", worksheetId = the sheet's id):
//   expression = "+63.5"        (ASCII operator + raw value)
//   result     = the running total after that line
// A subtotal row is stored as expression "TOTAL" with result = the total.
// A line can carry a TEXT NOTE, encoded after a '#' separator:
//   "+5#hello"   or   "TOTAL#toral"
// Lines saved by older versions used the glyphs × ÷ − — those are
// normalised while parsing, so old tapes keep working.
// Totals are always re-folded from the lines, so undo/redo — and any
// line edit or delete — instantly correct every later subtotal.
//
// Operator behaviour is the normal-calculator one: the operator key
// pressed BETWEEN two numbers applies to the SECOND number. So the
// entry is committed with the operator that was pending BEFORE it
// (the first number is a "+" figure), and the pressed key becomes
// the pending operator for the next entry. The pending operator is
// shown on screen the moment it is pressed (showPendingOp), before
// any digit of the next number is typed. Brackets turn the entry
// into an expression, which the enter key evaluates with exp4j.
//
// Percent follows the phone-calculator rule, same as the normal
// calculator: with a + or − pending, % turns the entry into that
// percent OF the running total (total 10000, then −10% -> the entry
// becomes 1000 and commits as −1000, leaving 9000); with × or ÷
// pending it is a plain hundredth. Inside bracket expressions the
// same applyPercentRules rewrite as CalculatorViewModel is used.
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CalTapeViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = (application as CalNyxApp).database.historyDao()
    private val sheetDao = NavStateDatabase.get(application).sheetDao()

    val sheets: StateFlow<List<SheetEntity>> = sheetDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _currentSheetId = MutableStateFlow<String?>(null)
    val currentSheetId: StateFlow<String?> = _currentSheetId

    val lines: StateFlow<List<HistoryEntity>> = _currentSheetId
        .flatMapLatest { sheetId ->
            if (sheetId == null) {
                flowOf(emptyList())
            } else {
                dao.getTapeLines(TAPE_TYPE, sheetId).map { entities -> entities.reversed() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val total: StateFlow<Double> = lines
        .map { entities -> computeTotal(entities) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input

    private val _pendingOp = MutableStateFlow("+")
    val pendingOp: StateFlow<String> = _pendingOp

    // True from the moment an operator key is pressed until the line
    // is finished with "=". The screen shows the pending line (symbol
    // first, digits as they come) whenever this is on.
    private val _showPendingOp = MutableStateFlow(false)
    val showPendingOp: StateFlow<Boolean> = _showPendingOp

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo

    private val redoStack = ArrayDeque<HistoryEntity>()

    init {
        viewModelScope.launch {
            var all = sheetDao.getAll()
            if (all.isEmpty()) {
                // First run with sheets: create Sheet1 and adopt the
                // tape lines saved before sheets existed.
                sheetDao.insert(SheetEntity(id = DEFAULT_SHEET_ID, name = "Sheet1"))
                dao.assignLegacyTapeLines(TAPE_TYPE, DEFAULT_SHEET_ID)
                all = sheetDao.getAll()
            }
            if (_currentSheetId.value == null) {
                _currentSheetId.value = all.first().id
            }
        }
    }

    // ---------------- Sheets ----------------

    fun switchSheet(id: String) {
        if (id == _currentSheetId.value) return
        _currentSheetId.value = id
        _input.value = ""
        _pendingOp.value = "+"
        _showPendingOp.value = false
        clearRedo()
    }

    fun addSheet() {
        viewModelScope.launch {
            val existing = sheetDao.getAll()
            var number = existing.size + 1
            while (existing.any { it.name == "Sheet$number" }) number++
            val sheet = SheetEntity(
                id = UUID.randomUUID().toString(),
                name = "Sheet$number"
            )
            sheetDao.insert(sheet)
            switchSheet(sheet.id)
        }
    }

    // Rename any sheet (Sheet1 included). Blank names are ignored.
    // The sheet row is re-inserted with its createdAt untouched, so
    // its position in the list does not change. The PDF export uses
    // the sheet's current name, so a renamed sheet exports under
    // its new name automatically.
    fun renameSheet(id: String, newName: String) {
        val clean = newName.trim().replace(Regex("\\s+"), " ").take(MAX_SHEET_NAME_LENGTH)
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val sheet = sheetDao.getAll().firstOrNull { it.id == id } ?: return@launch
            if (sheet.name != clean) {
                sheetDao.insert(sheet.copy(name = clean))
            }
        }
    }

    fun deleteSheet(id: String) {
        viewModelScope.launch {
            val all = sheetDao.getAll()
            if (all.size <= 1) return@launch // never delete the last sheet
            dao.clearTapeSheet(TAPE_TYPE, id)
            sheetDao.deleteById(id)
            if (_currentSheetId.value == id) {
                val next = all.first { it.id != id }
                switchSheet(next.id)
            }
        }
    }

    // ---------------- Notes & line editing ----------------

    private fun cleanNote(note: String): String {
        return note
            .replace('#', ' ')
            .replace('\n', ' ')
            .trim()
            .take(MAX_NOTE_LENGTH)
    }

    // Rewrite one committed line's note (empty text removes the note).
    // Notes are edited inline on the line itself.
    fun setLineNote(line: HistoryEntity, note: String) {
        val clean = cleanNote(note)
        val base = splitNote(line.expression).first
        val updated = if (clean.isEmpty()) base else "$base#$clean"
        viewModelScope.launch { dao.insert(line.copy(expression = updated)) }
    }

    // Rewrite one entry line completely — operator, amount and note —
    // after a mistyped line is corrected in the edit panel. Every
    // total is re-folded from the lines, so all later subtotals,
    // the copied text and the PDF correct themselves.
    fun updateLine(line: HistoryEntity, op: String, value: Double, note: String) {
        val clean = cleanNote(note)
        val base = op + plain(value)
        val updated = if (clean.isEmpty()) base else "$base#$clean"
        viewModelScope.launch { dao.insert(line.copy(expression = updated)) }
    }

    // Delete one line from the middle of the tape (edit panel). The
    // line goes onto the redo stack, so the strip's Redo button can
    // bring it back in its exact old position.
    fun deleteLine(line: HistoryEntity) {
        viewModelScope.launch { dao.delete(line) }
        redoStack.addLast(line)
        _canRedo.value = true
    }

    // ---------------- Keys ----------------

    fun onKey(label: String) {
        when (label) {
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9" -> appendDigits(label)
            "." -> appendDot()
            "()" -> appendBracket()
            "⌫" -> _input.value = _input.value.dropLast(1)
            "AC" -> {
                // Two-stage All Clear: first press clears the typed
                // entry; with an empty entry it clears the whole sheet.
                if (_input.value.isNotEmpty()) {
                    _input.value = ""
                } else {
                    clearTape()
                }
            }

            "%" -> {
                if (isExpression(_input.value)) {
                    _input.value += "%"
                } else {
                    val value = _input.value.toDoubleOrNull() ?: return
                    // Phone-calculator percent: with a + or − pending,
                    // the entry becomes that percent OF the running
                    // total (10000 on the tape, −10% -> entry 1000,
                    // committed as −1000, total 9000). With × or ÷
                    // pending it is a plain hundredth.
                    val converted = when (_pendingOp.value) {
                        "+", "-" -> total.value * value / 100.0
                        else -> value / 100.0
                    }
                    _input.value = plain(converted)
                }
            }

            "+" -> onOperator("+")
            "−" -> onOperator("-")
            "×" -> onOperator("*")
            "÷" -> onOperator("/")
            "=" -> onEnter()
        }
    }

    private fun onOperator(pressedOp: String) {
        val entry = _input.value
        when {
            // No number typed yet: the pressed operator simply becomes
            // the pending one (e.g. start a tape with a minus figure)
            // — and it shows on screen right away.
            entry.isEmpty() -> {
                _pendingOp.value = pressedOp
                _showPendingOp.value = true
            }

            isExpression(entry) -> _input.value = entry + pressedOp
            else -> {
                // Commit the typed number with the operator that was
                // pending BEFORE it (the first number commits with
                // "+"), then the pressed operator becomes pending for
                // the NEXT number: 69 [−] 25 [=] gives +69 then −25.
                // The new pending symbol shows immediately.
                val value = entry.toDoubleOrNull() ?: return
                val commitOp = _pendingOp.value
                val newTotal = applyOp(total.value, commitOp, value)
                insertLine(commitOp, value, newTotal)
                _pendingOp.value = pressedOp
                _showPendingOp.value = true
                _input.value = ""
                clearRedo()
            }
        }
    }

    // The blue enter key: evaluates the entry (a plain number or a
    // bracket expression), commits it with the pending operator, then
    // writes the subtotal row.
    private fun onEnter() {
        val value = evaluateEntry()
        if (value != null) {
            val newTotal = applyOp(total.value, _pendingOp.value, value)
            insertLine(_pendingOp.value, value, newTotal)
            _input.value = ""
            clearRedo()
            insertTotalRow(newTotal)
        } else {
            val last = lines.value.lastOrNull()
            if (last != null && splitNote(last.expression).first != "TOTAL") {
                insertTotalRow(total.value)
            }
        }
        _showPendingOp.value = false
    }

    fun undo() {
        val last = lines.value.lastOrNull() ?: return
        viewModelScope.launch { dao.delete(last) }
        redoStack.addLast(last)
        _canRedo.value = true
    }

    fun redo() {
        val entity = redoStack.removeLastOrNull() ?: return
        // Same id + createdAt go back in, so the line returns to its
        // exact old position on the tape.
        viewModelScope.launch { dao.insert(entity) }
        _canRedo.value = redoStack.isNotEmpty()
    }

    fun clearTape() {
        val sheetId = _currentSheetId.value ?: return
        viewModelScope.launch { dao.clearTapeSheet(TAPE_TYPE, sheetId) }
        redoStack.clear()
        _canRedo.value = false
        _input.value = ""
        _pendingOp.value = "+"
        _showPendingOp.value = false
    }

    private fun insertLine(op: String, value: Double, newTotal: Double) {
        val sheetId = _currentSheetId.value ?: return
        viewModelScope.launch {
            dao.insert(
                HistoryEntity(
                    expression = op + plain(value),
                    result = plain(newTotal),
                    calculatorType = TAPE_TYPE,
                    worksheetId = sheetId
                )
            )
        }
    }

    private fun insertTotalRow(totalValue: Double) {
        val sheetId = _currentSheetId.value ?: return
        viewModelScope.launch {
            dao.insert(
                HistoryEntity(
                    expression = "TOTAL",
                    result = plain(totalValue),
                    calculatorType = TAPE_TYPE,
                    worksheetId = sheetId
                )
            )
        }
    }

    private fun appendDigits(digits: String) {
        val entry = _input.value
        if (entry.count { it.isDigit() } + digits.length > 15) return
        _input.value = if (entry == "0") digits else entry + digits
    }

    private fun appendDot() {
        val entry = _input.value
        // Only one dot per number segment inside the entry.
        val segment = entry.split('+', '-', '*', '/', '(', ')', '%').last()
        if (segment.contains(".")) return
        _input.value = if (segment.isEmpty()) entry + "0." else entry + "."
    }

    private fun appendBracket() {
        val entry = _input.value
        val open = entry.count { it == '(' }
        val close = entry.count { it == ')' }
        val last = entry.lastOrNull()
        val canClose = open > close && last != null &&
                (last.isDigit() || last == ')' || last == '%')
        _input.value = entry + if (canClose) ")" else "("
    }

    private fun isExpression(entry: String): Boolean {
        if (entry.any { it == '(' || it == ')' || it == '*' || it == '/' }) {
            return true
        }
        // An operator anywhere after the first (sign) character.
        return entry.drop(1).any { it == '+' || it == '-' }
    }

    private fun evaluateEntry(): Double? {
        val entry = _input.value
        if (entry.isEmpty()) return null
        entry.toDoubleOrNull()?.let { return it }
        return try {
            var expr = applyPercentRules(entry)
            val open = expr.count { it == '(' }
            val close = expr.count { it == ')' }
            if (open > close) expr += ")".repeat(open - close)
            val value = ExpressionBuilder(expr).build().evaluate()
            if (value.isNaN() || value.isInfinite()) null else value
        } catch (e: Exception) {
            null
        }
    }

    // Phone-calculator percent rules, applied as a text rewrite
    // before exp4j sees a bracket expression:
    //   10000-10%  ->  10000-((10000)*10/100)  = 9000
    //   200*10%    ->  200*(10/100)           = 20
    //   50%        ->  (50/100)               = 0.5
    // The percent operand is the number literal or (...) group
    // directly before the %. The same helper lives in
    // CalculatorViewModel for the normal/scientific calculators.
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
                // Nothing the % can take: plain hundredth.
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

    private fun clearRedo() {
        redoStack.clear()
        _canRedo.value = false
    }

    private fun computeTotal(entities: List<HistoryEntity>): Double {
        var running = 0.0
        for (entity in entities) {
            val base = splitNote(entity.expression).first
            if (base == "TOTAL") continue
            val op = normalizeOp(base.firstOrNull())
            val value = base.drop(1).toDoubleOrNull() ?: continue
            running = applyOp(running, op, value)
        }
        return running
    }

    private fun applyOp(totalValue: Double, op: String, value: Double): Double {
        return applyOperator(totalValue, op, value)
    }

    companion object {
        private const val TAPE_TYPE = "caltape"
        private const val DEFAULT_SHEET_ID = "sheet1"
        private const val MAX_NOTE_LENGTH = 48
        private const val MAX_SHEET_NAME_LENGTH = 30

        // One tape step: fold a value into a running total with an
        // operator. Public so the screen can fold the tape itself
        // (total rows, copy text and PDF all read folded values,
        // never stored snapshots).
        fun applyOperator(totalValue: Double, op: String, value: Double): Double {
            return when (op) {
                "+" -> totalValue + value
                "-" -> totalValue - value
                "*" -> totalValue * value
                "/" -> if (value != 0.0) totalValue / value else totalValue
                // Only reachable for lines saved by older versions.
                "^" -> Math.pow(totalValue, value)
                else -> totalValue
            }
        }

        // Older saved lines used the display glyphs; normalise them.
        fun normalizeOp(op: Char?): String {
            return when (op) {
                '−' -> "-"
                '×' -> "*"
                '÷' -> "/"
                null -> "+"
                else -> op.toString()
            }
        }

        // Splits a stored expression into (calculation part, note).
        fun splitNote(expression: String): Pair<String, String> {
            val index = expression.indexOf('#')
            return if (index < 0) {
                expression to ""
            } else {
                expression.substring(0, index) to expression.substring(index + 1)
            }
        }
    }

    private fun plain(value: Double): String {
        return if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }
}
