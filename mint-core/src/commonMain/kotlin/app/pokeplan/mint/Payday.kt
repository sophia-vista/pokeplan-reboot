package app.pokeplan.mint

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * The Philippine holidays worked out from the year alone (MINT.md 8.3B): the regular holidays fixed by law, Holy Week, and the special non-working days that are
 * declared on the same date most years. Days that move by proclamation (Eid, a one-off day off) are not here; the player adds those in Mint settings.
 */
object PhHolidays {
    private val fixed = listOf(
        1 to 1, // New Year's Day
        2 to 25, // EDSA People Power Anniversary (special)
        4 to 9, // Araw ng Kagitingan
        5 to 1, // Labour Day
        6 to 12, // Independence Day
        8 to 21, // Ninoy Aquino Day (special)
        11 to 1, // All Saints' Day (special)
        11 to 30, // Bonifacio Day
        12 to 8, // Feast of the Immaculate Conception (special)
        12 to 24, // Christmas Eve (special)
        12 to 25, // Christmas Day
        12 to 30, // Rizal Day
        12 to 31, // Last day of the year (special)
    )

    /** Easter Sunday of [year] (the Gregorian computus). */
    fun easter(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate(year, month, day)
    }

    /** National Heroes Day: the last Monday of August. */
    fun heroesDay(year: Int): LocalDate {
        var d = LocalDate(year, Month.AUGUST, 31)
        while (d.dayOfWeek != DayOfWeek.MONDAY) d = d.minus(DatePeriod(days = 1))
        return d
    }

    /** The built-in holidays of [year]. */
    fun of(year: Int): Set<LocalDate> {
        val easter = easter(year)
        return buildSet {
            for ((m, d) in fixed) add(LocalDate(year, m, d))
            add(heroesDay(year))
            add(easter.minus(DatePeriod(days = 3))) // Maundy Thursday
            add(easter.minus(DatePeriod(days = 2))) // Good Friday
            add(easter.minus(DatePeriod(days = 1))) // Black Saturday
        }
    }
}

/** Working days for paydays (MINT.md 8.3B): a weekday that is not a holiday the player counts. */
object Payday {
    private fun weekend(d: LocalDate) = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY

    /** Whether [d] is a day off under [settings]: a weekend, one of the player's own days off, or a built-in holiday they have not turned off. */
    fun isDayOff(d: LocalDate, settings: MintSettings): Boolean =
        weekend(d) || d in settings.daysOff || (settings.builtInHolidays && d in PhHolidays.of(d.year) && d !in settings.holidaysIgnored)

    /** The nearest working day on or before [d] (itself when it is one). At most 31 steps back, so a calendar full of days off cannot loop. */
    fun onOrBefore(d: LocalDate, settings: MintSettings): LocalDate {
        var day = d
        var steps = 0
        while (isDayOff(day, settings) && steps < 31) { day = day.minus(DatePeriod(days = 1)); steps++ }
        return day
    }

    /** The built-in holidays shown in settings for [year], each with whether it is currently counted. */
    fun holidayList(year: Int, settings: MintSettings): List<Pair<LocalDate, Boolean>> =
        PhHolidays.of(year).sorted().map { it to (settings.builtInHolidays && it !in settings.holidaysIgnored) }
}
