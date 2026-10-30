package app.pokeplan.plan

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

/**
 * Turns a line like `Call mum tomorrow 5pm #Social !B` into task fields. Understood words:
 *
 * - `!A` to `!E`: difficulty rank. `#Work` (a category) or `#Groceries` (otherwise a list name).
 * - Dates: `today`, `tomorrow`, `tonight`, weekdays (`friday`, `next mon`: the next one after today), `in 3 days`,
 *   `in 2 weeks`, `2026-11-01`, `oct 25`, `25 oct`.
 * - Times: `5pm`, `5:30pm`, `17:30` (optionally after `at`).
 * - Durations: `30m`, `45 min`, `2h`, `1h30`, `for 90 minutes`.
 * - `by` or `due` before a date or time makes it the deadline instead of the day you plan to do it.
 *
 * Everything else is the title.
 */
object QuickAdd {
    data class Result(
        val title: String,
        val rank: Rank? = null,
        val category: Category? = null,
        val listName: String? = null,
        val planned: LocalDate? = null,
        val plannedTime: LocalTime? = null,
        val due: LocalDateTime? = null,
        val estimateMinutes: Int? = null,
        val evening: Boolean = false,
    )

    private val weekdays = mapOf(
        "mon" to 1, "monday" to 1, "tue" to 2, "tues" to 2, "tuesday" to 2, "wed" to 3, "wednesday" to 3,
        "thu" to 4, "thur" to 4, "thurs" to 4, "thursday" to 4, "fri" to 5, "friday" to 5,
        "sat" to 6, "saturday" to 6, "sun" to 7, "sunday" to 7,
    )
    private val monthNames = mapOf(
        "jan" to 1, "january" to 1, "feb" to 2, "february" to 2, "mar" to 3, "march" to 3, "apr" to 4, "april" to 4,
        "may" to 5, "jun" to 6, "june" to 6, "jul" to 7, "july" to 7, "aug" to 8, "august" to 8,
        "sep" to 9, "sept" to 9, "september" to 9, "oct" to 10, "october" to 10, "nov" to 11, "november" to 11,
        "dec" to 12, "december" to 12,
    )
    private val timeAmPm = Regex("^(\\d{1,2})(?::(\\d{2}))?(am|pm)$", RegexOption.IGNORE_CASE)
    private val time24 = Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$")
    private val durationCompact = Regex("^(\\d+)(m|min|mins|minutes?|h|hr|hrs|hours?)$", RegexOption.IGNORE_CASE)
    private val durationHm = Regex("^(\\d+)h(\\d+)m?$", RegexOption.IGNORE_CASE)
    private val unit = Regex("^(m|min|mins|minutes?|h|hr|hrs|hours?)$", RegexOption.IGNORE_CASE)
    private val iso = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    /** The longest estimate quick add reads: a day. Anything longer, or 0, stays in the title as typed. */
    private const val MAX_ESTIMATE_MINUTES = 24 * 60

    private fun minutesOf(amount: String, unit: String): Int? = estimate(amount.toIntOrNull()?.takeIf { it <= MAX_ESTIMATE_MINUTES }?.let {
        if (unit.lowercase().startsWith("h")) it * 60 else it
    })

    /** The minutes "1h30", "45m" or "2h" stand for, or null when [token] is not a duration quick add accepts. */
    private fun durationOf(token: String): Int? {
        durationHm.matchEntire(token)?.let { m ->
            val hours = m.groupValues[1].toIntOrNull()?.takeIf { it <= 24 } ?: return null
            return estimate(m.groupValues[2].toIntOrNull()?.let { hours * 60 + it })
        }
        return durationCompact.matchEntire(token)?.let { m -> minutesOf(m.groupValues[1], m.groupValues[2]) }
    }

    private fun estimate(minutes: Int?): Int? = minutes?.takeIf { it in 1..MAX_ESTIMATE_MINUTES }

    private fun parseTime(token: String): LocalTime? {
        timeAmPm.matchEntire(token)?.let { m ->
            var hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
            if (hour !in 1..12 || minute > 59) return null
            val pm = m.groupValues[3].lowercase() == "pm"
            hour = hour % 12 + if (pm) 12 else 0
            return LocalTime(hour, minute)
        }
        time24.matchEntire(token)?.let { return LocalTime(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
        return null
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? = try { LocalDate(year, month, day) } catch (e: IllegalArgumentException) { null }

    /** A date starting at token [i]; returns it and how many tokens it used. */
    private fun parseDate(t: List<String>, i: Int, today: LocalDate): Pair<LocalDate, Int>? {
        val w = t[i].lowercase()
        when (w) {
            "today", "tonight" -> return today to 1
            "tomorrow", "tmr", "tmrw" -> return today.plus(DatePeriod(days = 1)) to 1
        }
        weekdays[w]?.let { target ->
            var ahead = (target - today.dayOfWeek.isoDayNumber + 7) % 7
            if (ahead == 0) ahead = 7
            return today.plus(DatePeriod(days = ahead)) to 1
        }
        if (w == "in" && i + 2 <= t.lastIndex) {
            val n = t[i + 1].toIntOrNull()
            val u = t[i + 2].lowercase()
            if (n != null && n in 1..365) {
                if (u == "day" || u == "days") return today.plus(DatePeriod(days = n)) to 3
                if (u == "week" || u == "weeks") return today.plus(DatePeriod(days = 7 * n)) to 3
            }
        }
        if (iso.matches(t[i])) return runCatching { LocalDate.parse(t[i]) }.getOrNull()?.let { it to 1 }
        // "oct 25"
        monthNames[w]?.let { month ->
            if (i + 1 <= t.lastIndex) t[i + 1].toIntOrNull()?.let { day -> return resolveMonthDay(month, day, today)?.let { it to 2 } }
        }
        // "25 oct"
        t[i].toIntOrNull()?.let { day ->
            if (i + 1 <= t.lastIndex) monthNames[t[i + 1].lowercase()]?.let { month -> return resolveMonthDay(month, day, today)?.let { it to 2 } }
        }
        return null
    }

    private fun resolveMonthDay(month: Int, day: Int, today: LocalDate): LocalDate? {
        val thisYear = safeDate(today.year, month, day) ?: return null
        return if (thisYear >= today) thisYear else safeDate(today.year + 1, month, day)
    }

    fun parse(input: String, today: LocalDate): Result {
        val tokens = input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val used = BooleanArray(tokens.size)
        var rank: Rank? = null
        var category: Category? = null
        var listName: String? = null
        var estimate: Int? = null
        var evening = false
        var dueMode = false
        var plannedDate: LocalDate? = null
        var plannedTime: LocalTime? = null
        var dueDate: LocalDate? = null
        var dueTime: LocalTime? = null

        fun use(from: Int, count: Int) { for (k in from until from + count) used[k] = true }
        fun startsDateOrTime(i: Int) = i <= tokens.lastIndex && (parseDate(tokens, i, today) != null || parseTime(tokens[i]) != null)

        var i = 0
        while (i < tokens.size) {
            val tok = tokens[i]
            val lower = tok.lowercase()
            when {
                tok.length == 2 && tok[0] == '!' && Rank.fromLetter(tok[1]) != null -> { rank = Rank.fromLetter(tok[1]); use(i, 1); i++ }
                tok.length > 1 && tok[0] == '#' -> {
                    val name = tok.substring(1)
                    val cat = Category.entries.firstOrNull { it.label.equals(name, ignoreCase = true) }
                    if (cat != null) category = cat else listName = name
                    use(i, 1); i++
                }
                (lower == "by" || lower == "due") && startsDateOrTime(i + 1) -> { dueMode = true; use(i, 1); i++ }
                lower == "at" && i + 1 <= tokens.lastIndex && parseTime(tokens[i + 1]) != null -> { use(i, 1); i++ }
                lower == "next" && i + 1 <= tokens.lastIndex && weekdays.containsKey(tokens[i + 1].lowercase()) -> { use(i, 1); i++ }
                lower == "for" && i + 1 <= tokens.lastIndex && (durationOf(tokens[i + 1]) != null ||
                    (i + 2 <= tokens.lastIndex && unit.matches(tokens[i + 2]) && minutesOf(tokens[i + 1], tokens[i + 2]) != null)) -> { use(i, 1); i++ }
                durationOf(tok) != null -> { estimate = durationOf(tok); use(i, 1); i++ }
                i + 1 <= tokens.lastIndex && unit.matches(tokens[i + 1]) && minutesOf(tok, tokens[i + 1]) != null && parseDate(tokens, i, today) == null -> {
                    estimate = minutesOf(tok, tokens[i + 1]); use(i, 2); i += 2
                }
                parseTime(tok) != null -> {
                    if (dueMode) dueTime = parseTime(tok) else plannedTime = parseTime(tok)
                    use(i, 1); i++
                }
                else -> {
                    val date = parseDate(tokens, i, today)
                    if (date != null) {
                        if (dueMode) dueDate = date.first else plannedDate = date.first
                        if (lower == "tonight") { evening = true; if (plannedTime == null) plannedTime = LocalTime(20, 0) }
                        use(i, date.second); i += date.second
                    } else i++
                }
            }
        }
        if (plannedTime != null && plannedDate == null) plannedDate = today
        val due = if (dueDate != null || dueTime != null) LocalDateTime(dueDate ?: today, dueTime ?: LocalTime(23, 59)) else null
        val title = tokens.filterIndexed { index, _ -> !used[index] }.joinToString(" ")
        return Result(title, rank, category, listName, plannedDate, plannedTime, due, estimate, evening)
    }
}
