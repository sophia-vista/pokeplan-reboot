package app.pokeplan.ui.gear

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

sealed interface CalcResult {
    data class Value(val value: Double, val text: String) : CalcResult
    data class Error(val message: String) : CalcResult
}

object CalcEngine {
    fun evaluate(expression: String, degrees: Boolean = true): CalcResult {
        val text = expression.trim().replace('−', '-').replace('–', '-')
        if (text.isEmpty()) return CalcResult.Error("Type something to work out.")
        return try {
            val parser = Parser(text, degrees)
            val value = parser.parseAll()
            if (value.isNaN()) CalcResult.Error("Not defined.")
            else if (value.isInfinite() || abs(value) > MAX) CalcResult.Error("The number is too large.")
            else CalcResult.Value(value, format(value))
        } catch (e: CalcException) {
            CalcResult.Error(e.message ?: "Check the expression.")
        }
    }

    private const val MAX = 1e300

    fun format(value: Double, group: Boolean = false): String = formatSignificant(value, 12, group)

    fun formatSignificant(value: Double, digits: Int, group: Boolean = false): String {
        val d = digits.coerceIn(1, 12)
        if (value == 0.0) return "0"
        val negative = value < 0
        val x = abs(value)
        var exponent = floor(log10(x)).toInt()
        val top = 10.0.pow(d).toLong()
        var mantissa = round(x / 10.0.pow(exponent - (d - 1))).toLong()
        if (mantissa >= top) { mantissa /= 10; exponent += 1 }
        if (mantissa < top / 10) {
            exponent -= 1
            mantissa = round(x / 10.0.pow(exponent - (d - 1))).toLong()
            if (mantissa >= top) { mantissa /= 10; exponent += 1 }
        }
        var digits = mantissa.toString().trimEnd('0')
        if (digits.isEmpty()) digits = "0"
        val body = when {
            exponent in -7..14 -> plain(digits, exponent, group)
            else -> (if (digits.length > 1) digits[0] + "." + digits.drop(1) else digits) + "e" + exponent
        }
        return if (negative) "-$body" else body
    }

    private fun plain(digits: String, exponent: Int, group: Boolean): String {
        val whole: String
        val fraction: String
        if (exponent >= 0) {
            val padded = if (digits.length <= exponent + 1) digits + "0".repeat(exponent + 1 - digits.length) else digits
            whole = padded.substring(0, exponent + 1)
            fraction = padded.substring(exponent + 1)
        } else {
            whole = "0"
            fraction = "0".repeat(-exponent - 1) + digits
        }
        val grouped = if (group) whole.reversed().chunked(3).joinToString(",").reversed() else whole
        return if (fraction.isEmpty()) grouped else "$grouped.$fraction"
    }

    private class CalcException(message: String) : Exception(message)

    private class Parser(private val s: String, private val degrees: Boolean) {
        private var i = 0

        fun parseAll(): Double {
            val v = expression().first
            skip()
            if (i < s.length) throw CalcException(if (s[i] == ')') "A bracket is closed that was never opened." else "Check the expression.")
            return v
        }

        private fun skip() { while (i < s.length && s[i] == ' ') i++ }
        private fun peek(): Char? { skip(); return s.getOrNull(i) }
        private fun eat(c: Char): Boolean { if (peek() == c) { i++; return true }; return false }

        private fun expression(): Pair<Double, Boolean> {
            var (left, _) = term()
            while (true) {
                val op = peek()
                if (op == '+' || op == '-') {
                    i++
                    val (right, percent) = term()
                    val amount = if (percent) left * right else right
                    left = if (op == '+') left + amount else left - amount
                } else break
            }
            return left to false
        }

        private fun term(): Pair<Double, Boolean> {
            var (left, percent) = unary()
            while (true) {
                val op = peek()
                if (op == '*' || op == '×' || op == 'x' && !followsLetter() || op == '/' || op == '÷') {
                    i++
                    val (right, _) = unary()
                    left = if (op == '/' || op == '÷') { if (right == 0.0) throw CalcException("Cannot divide by zero."); left / right } else left * right
                    percent = false
                } else if (op != null && (op == '(' || op == 'π' || op.isLetter() && op != 'x')) {
                    val (right, _) = unary()
                    left *= right
                    percent = false
                } else break
            }
            return left to percent
        }

        private fun followsLetter(): Boolean = false

        private fun unary(): Pair<Double, Boolean> {
            if (peek() == '-') { i++; val (v, p) = unary(); return -v to p }
            if (peek() == '+') { i++; return unary() }
            return power()
        }

        private fun power(): Pair<Double, Boolean> {
            val (base, percent) = postfix()
            if (eat('^')) {
                val (exponent, _) = unary()
                val r = base.pow(exponent)
                if (r.isNaN()) throw CalcException("Not defined.")
                return r to false
            }
            return base to percent
        }

        private fun postfix(): Pair<Double, Boolean> {
            var v = primary()
            var percent = false
            while (true) {
                if (eat('%')) { v /= 100.0; percent = true }
                else if (eat('!')) { v = factorial(v); percent = false }
                else break
            }
            return v to percent
        }

        private fun factorial(v: Double): Double {
            if (v < 0 || v != floor(v)) throw CalcException("A factorial needs a whole number, zero or more.")
            if (v > 170) throw CalcException("The number is too large.")
            var r = 1.0
            for (n in 2..v.toInt()) r *= n
            return r
        }

        private fun primary(): Double {
            val c = peek() ?: throw CalcException("The expression ends too soon.")
            if (c == '(') {
                i++
                val v = expression().first
                if (!eat(')')) throw CalcException("A bracket is not closed.")
                return v
            }
            if (c.isDigit() || c == '.' || c == ',') return number()
            if (c == 'π') { i++; return PI }
            if (c.isLetter()) {
                val start = i
                while (i < s.length && s[i].isLetter()) i++
                val name = s.substring(start, i).lowercase()
                if (name == "pi") return PI
                if (name == "e") return kotlin.math.E
                if (peek() != '(') throw CalcException("\"$name\" is not something I know.")
                i++
                val argument = expression().first
                if (!eat(')')) throw CalcException("A bracket is not closed.")
                return function(name, argument)
            }
            throw CalcException("Check the expression.")
        }

        private fun number(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == ',')) i++
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                var j = i + 1
                if (j < s.length && (s[j] == '+' || s[j] == '-')) j++
                if (j < s.length && s[j].isDigit()) { while (j < s.length && s[j].isDigit()) j++; i = j }
            }
            val raw = s.substring(start, i).replace(',', '.')
            if (raw.count { it == '.' } > 1) throw CalcException("A number has two decimal points.")
            return raw.toDoubleOrNull() ?: throw CalcException("\"${s.substring(start, i)}\" is not a number.")
        }

        private fun angle(v: Double) = if (degrees) v * PI / 180.0 else v
        private fun back(v: Double) = if (degrees) v * 180.0 / PI else v

        private fun function(name: String, x: Double): Double = when (name) {
            "sin" -> sin(angle(x))
            "cos" -> cos(angle(x))
            "tan" -> {
                val c = cos(angle(x))
                if (abs(c) < 1e-12) throw CalcException("The tangent is not defined here.") else tan(angle(x))
            }
            "asin" -> if (x < -1 || x > 1) throw CalcException("asin needs a number from -1 to 1.") else back(asin(x))
            "acos" -> if (x < -1 || x > 1) throw CalcException("acos needs a number from -1 to 1.") else back(acos(x))
            "atan" -> back(atan(x))
            "ln" -> if (x <= 0) throw CalcException("ln needs a number above zero.") else ln(x)
            "log" -> if (x <= 0) throw CalcException("log needs a number above zero.") else log10(x)
            "sqrt" -> if (x < 0) throw CalcException("The square root of a negative number is not defined here.") else sqrt(x)
            "abs" -> abs(x)
            "exp" -> exp(x)
            else -> throw CalcException("\"$name\" is not something I know.")
        }.let { r ->
            if (abs(r) < 1e-14 && name in setOf("sin", "cos", "tan")) 0.0 else r
        }
    }
}
