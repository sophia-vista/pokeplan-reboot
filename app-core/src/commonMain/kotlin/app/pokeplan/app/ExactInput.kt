package app.pokeplan.app

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.LocalTime

object ExactInput {

    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    private val ISO = Regex("""(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})""")
    private val COMPACT = Regex("""(\d{4})(\d{2})(\d{2})""")
    private val DAY_FIRST = Regex("""(\d{1,2})[-/.](\d{1,2})[-/.](\d{4})""")
    private val DAY_NAME = Regex("""(\d{1,2})(?:st|nd|rd|th)?\s*-?\s*([a-z]{3,9})\.?,?\s*(\d{4})?""")
    private val NAME_DAY = Regex("""([a-z]{3,9})\.?\s*(\d{1,2})(?:st|nd|rd|th)?,?\s*(\d{4})?""")

    fun parseDate(text: String, reference: LocalDate): LocalDate? {
        val s = text.trim().lowercase()
        if (s.isEmpty()) return null
        when (s) {
            "today" -> return reference
            "tomorrow", "tmr" -> return reference.plusDays(1)
            "yesterday" -> return reference.plusDays(-1)
        }
        ISO.matchEntire(s)?.destructured?.let { (y, m, d) -> return date(y.toInt(), m.toInt(), d.toInt()) }
        COMPACT.matchEntire(s)?.destructured?.let { (y, m, d) -> return date(y.toInt(), m.toInt(), d.toInt()) }
        DAY_FIRST.matchEntire(s)?.destructured?.let { (d, m, y) -> return date(y.toInt(), m.toInt(), d.toInt()) }
        DAY_NAME.matchEntire(s)?.destructured?.let { (d, name, y) ->
            val month = month(name) ?: return null
            return date(y.toIntOrNull() ?: reference.year, month, d.toInt())
        }
        NAME_DAY.matchEntire(s)?.destructured?.let { (name, d, y) ->
            val month = month(name) ?: return null
            return date(y.toIntOrNull() ?: reference.year, month, d.toInt())
        }
        return null
    }

    fun formatDate(d: LocalDate): String = d.toString()

    private fun month(name: String): Int? {
        if (name.length < 3) return null
        val i = MONTHS.indexOfFirst { name.startsWith(it) }
        if (i < 0) return null
        val full = FULL_MONTHS[i]
        return if (full.startsWith(name) || name == "sept") i + 1 else null
    }

    private val FULL_MONTHS = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")

    private fun date(y: Int, m: Int, d: Int): LocalDate? =
        if (y !in 1900..2200 || m !in 1..12 || d !in 1..31) null
        else try { LocalDate(y, m, d) } catch (_: IllegalArgumentException) { null }

    private fun LocalDate.plusDays(n: Int): LocalDate =
        plus(DatePeriod(days = n))

    private val AMPM = Regex("""(\d{1,2})(?:[:.h](\d{2}))?\s*([ap])\.?m?\.?""")
    private val CLOCK = Regex("""(\d{1,2})[:.h](\d{2})""")
    private val DIGITS = Regex("""\d{1,4}""")

    fun parseMinuteOfDay(text: String, allowEndOfDay: Boolean = false): Int? {
        val s = text.trim().lowercase()
        if (s.isEmpty()) return null
        if (s == "noon") return 12 * 60
        if (s == "midnight") return 0
        AMPM.matchEntire(s)?.destructured?.let { (h, m, ap) ->
            val hour = h.toInt()
            val minute = m.ifEmpty { "0" }.toInt()
            if (hour !in 1..12 || minute !in 0..59) return null
            return (hour % 12 + if (ap == "p") 12 else 0) * 60 + minute
        }
        val (hour, minute) = when {
            CLOCK.matches(s) -> CLOCK.matchEntire(s)!!.destructured.let { (h, m) -> h.toInt() to m.toInt() }
            DIGITS.matches(s) -> when (s.length) {
                1, 2 -> s.toInt() to 0
                3 -> s.substring(0, 1).toInt() to s.substring(1).toInt()
                else -> s.substring(0, 2).toInt() to s.substring(2).toInt()
            }
            else -> return null
        }
        if (minute !in 0..59) return null
        if (hour == 24 && minute == 0 && allowEndOfDay) return 24 * 60
        return if (hour in 0..23) hour * 60 + minute else null
    }

    fun parseTime(text: String): LocalTime? = parseMinuteOfDay(text)?.let { LocalTime(it / 60, it % 60) }

    fun formatTime(t: LocalTime): String = formatMinuteOfDay(t.hour * 60 + t.minute)

    fun formatMinuteOfDay(minute: Int): String = (minute / 60).toString().padStart(2, '0') + ":" + (minute % 60).toString().padStart(2, '0')

    private val TOKEN = Regex("""(\d+(?:\.\d+)?)\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)?""")
    private val COLONS = Regex("""(\d+):(\d{1,2})(?::(\d{1,2}))?""")

    fun parseDurationSeconds(text: String, bareUnit: Char = 'm'): Long? {
        val s = text.trim().lowercase()
        if (s.isEmpty()) return null
        COLONS.matchEntire(s)?.destructured?.let { (a, b, c) ->
            val x = a.toLong()
            val y = b.toLong()
            if (y > 59) return null
            return if (c.isNotEmpty()) {
                val z = c.toLong()
                if (z > 59) null else x * 3600 + y * 60 + z
            } else if (bareUnit == 's') x * 60 + y else x * 3600 + y * 60
        }
        var total = 0.0
        var pos = 0
        var seen = 0
        var lastUnit: Char? = null
        while (pos < s.length) {
            while (pos < s.length && (s[pos] == ' ' || s[pos] == ',')) pos++
            if (pos >= s.length) break
            val m = TOKEN.matchAt(s, pos) ?: return null
            val n = m.groupValues[1].toDoubleOrNull() ?: return null
            val unit = m.groupValues[2].firstOrNull()
                ?: when (lastUnit) { 'h' -> 'm'; 'm' -> 's'; else -> bareUnit }
            total += n * when (unit) { 'h' -> 3600.0; 'm' -> 60.0; else -> 1.0 }
            lastUnit = unit
            seen++
            pos = m.range.last + 1
        }
        if (seen == 0) return null
        val whole = total.toLong()
        return if (total - whole < 1e-9 && total >= 0.0) whole else null
    }

    fun parseDurationMinutes(text: String): Int? =
        parseDurationSeconds(text, 'm')?.takeIf { it % 60 == 0L && it / 60 <= 24 * 60 }?.let { (it / 60).toInt() }

    fun formatMinutes(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}
