package app.pokeplan.mint

/**
 * Money as integer minor units with an explicit currency scale (MINT.md sections 5.2 and 15.2): parsing and showing never touch a binary
 * floating-point number, so 0.10 + 0.20 is exactly 0.30.
 */
object Money {
    /** Digits after the decimal point: whole units for yen, won and dong, two for everything else Mint offers. */
    fun scale(currency: String): Int = when (currency) { "JPY", "KRW", "VND" -> 0; else -> 2 }

    /** The currencies offered when an account is made; PHP first (MINT.md section 1). Any other three-letter code can be typed. */
    val common: List<String> = listOf("PHP", "USD", "EUR", "JPY", "GBP", "SGD", "AUD", "CAD", "HKD", "KRW")

    /** The most one amount can be, in minor units (a hundred billion of a unit), so sums can never overflow a Long. */
    const val MAX_MINOR: Long = 100_000_000_000_000L

    /**
     * Reads what a person typed: `1250`, `1,250.50`, `1.250,50`, `₱1,250`, `12,5` (a lone comma with one or two digits after it is a decimal
     * comma; with three it is a thousands separator). Returns the amount in minor units, or null when it is not a number, has more decimals
     * than the currency has, is negative, or is too large. Never rounds.
     */
    fun parse(text: String, currency: String): Long? {
        val scale = scale(currency)
        val cleaned = text.trim().filter { it.isDigit() || it == '.' || it == ',' }
        if (cleaned.isEmpty() || cleaned.none { it.isDigit() }) return null
        val lastDot = cleaned.lastIndexOf('.')
        val lastComma = cleaned.lastIndexOf(',')
        val decimalAt = when {
            lastDot >= 0 && lastComma >= 0 -> maxOf(lastDot, lastComma)
            lastDot >= 0 -> if (cleaned.count { it == '.' } == 1) lastDot else -1
            lastComma >= 0 -> {
                val after = cleaned.length - lastComma - 1
                if (cleaned.count { it == ',' } == 1 && after in 1..2 && scale > 0) lastComma else -1
            }
            else -> -1
        }
        val whole: String
        val fraction: String
        if (decimalAt >= 0) {
            whole = cleaned.substring(0, decimalAt).filter { it.isDigit() }
            fraction = cleaned.substring(decimalAt + 1).filter { it.isDigit() }
        } else {
            whole = cleaned.filter { it.isDigit() }
            fraction = ""
        }
        if (fraction.length > scale) return null
        if (whole.length > 15) return null
        var minor = (whole.ifEmpty { "0" }).toLongOrNull() ?: return null
        repeat(scale) { minor = minor * 10 }
        val frac = fraction.padEnd(scale, '0')
        if (scale > 0 && frac.isNotEmpty()) minor += frac.toLong()
        return minor.takeIf { it in 0..MAX_MINOR }
    }

    /** `PHP 1,250.50`. The code, not a symbol: no font has to carry a glyph for it, and it is never ambiguous. [hidden] gives `PHP ••••` (MINT.md section 3.3). */
    fun format(minor: Long, currency: String, hidden: Boolean = false): String {
        if (hidden) return "$currency ••••"
        val negative = minor < 0
        val abs = if (minor == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(minor)
        val scale = scale(currency)
        var divisor = 1L
        repeat(scale) { divisor *= 10 }
        val whole = (abs / divisor).toString().reversed().chunked(3).joinToString(",").reversed()
        val fraction = if (scale == 0) "" else "." + (abs % divisor).toString().padStart(scale, '0')
        return (if (negative) "-" else "") + "$currency $whole$fraction"
    }

    /** The plain number for an edit field: `1250.50`, no separators and no code. */
    fun plain(minor: Long, currency: String): String {
        val scale = scale(currency)
        var divisor = 1L
        repeat(scale) { divisor *= 10 }
        return if (scale == 0) minor.toString() else "${minor / divisor}.${(minor % divisor).toString().padStart(scale, '0')}"
    }
}
