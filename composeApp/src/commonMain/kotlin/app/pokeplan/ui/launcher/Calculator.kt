package app.pokeplan.ui.launcher

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The utilities page's pocket calculator: digits, a point, + - x /, equals, clear, delete and sign. It works like a desk
 * calculator (one operation at a time, `2 + 3 x 4` is 20), and a divide by zero shows Error until the next key.
 */
class Calculator {
    private var accumulator: Double? = null
    private var operator: Char? = null
    private var entry = "0"
    private var fresh = true
    private var failed = false

    /** What the display shows now. */
    val display: String get() = if (failed) "Error" else entry

    fun digit(d: Char) {
        require(d in '0'..'9')
        if (failed) clear()
        entry = if (fresh || entry == "0") d.toString() else if (entry.length < MAX_DIGITS) entry + d else entry
        fresh = false
    }

    fun point() {
        if (failed) clear()
        if (fresh) { entry = "0."; fresh = false } else if ('.' !in entry) entry += "."
    }

    fun delete() {
        if (failed) return clear()
        if (fresh) return
        entry = if (entry.length <= 1 || (entry.length == 2 && entry.startsWith("-"))) "0" else entry.dropLast(1)
        if (entry == "0") fresh = true
    }

    fun negate() {
        if (failed) return
        if (entry != "0") entry = if (entry.startsWith("-")) entry.drop(1) else "-$entry"
    }

    /** One of + - x / (also * for x). */
    fun operate(op: Char) {
        require(op in "+-x*/")
        if (failed) clear()
        if (!fresh || accumulator == null) settle()
        operator = if (op == '*') 'x' else op
        fresh = true
    }

    fun equals() {
        if (failed) return clear()
        settle()
        operator = null
        fresh = true
    }

    fun clear() {
        accumulator = null; operator = null; entry = "0"; fresh = true; failed = false
    }

    /** Applies the waiting operation to what was typed, keeping the result as the number so far. */
    private fun settle() {
        val typed = entry.toDouble()
        val before = accumulator
        val op = operator
        val result = if (before == null || op == null) typed else when (op) {
            '+' -> before + typed
            '-' -> before - typed
            'x' -> before * typed
            else -> if (typed == 0.0) { failed = true; return } else before / typed
        }
        accumulator = result
        entry = format(result)
    }

    companion object {
        const val MAX_DIGITS = 12

        /** A number written plainly: whole numbers without a point, others rounded to 8 decimals with no trailing zeros. */
        fun format(value: Double): String {
            if (value.isNaN() || value.isInfinite() || abs(value) >= 1e15) return "Error"
            val rounded = (value * 1e8).roundToLong() / 1e8
            if (rounded == rounded.toLong().toDouble()) return rounded.toLong().toString()
            return rounded.toString().trimEnd('0').trimEnd('.')
        }
    }
}
