package app.pokeplan.mint

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.plus

object PhHolidays {
    private val fixed = listOf(
        1 to 1,
        2 to 25,
        4 to 9,
        5 to 1,
        6 to 12,
        8 to 21,
        11 to 1,
        11 to 30,
        12 to 8,
        12 to 24,
        12 to 25,
        12 to 30,
        12 to 31,
    )

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

    fun heroesDay(year: Int): LocalDate {
        var d = LocalDate(year, Month.AUGUST, 31)
        while (d.dayOfWeek != DayOfWeek.MONDAY) d = d.minus(DatePeriod(days = 1))
        return d
    }

    fun of(year: Int): Set<LocalDate> {
        val easter = easter(year)
        return buildSet {
            for ((m, d) in fixed) add(LocalDate(year, m, d))
            add(heroesDay(year))
            add(easter.minus(DatePeriod(days = 3)))
            add(easter.minus(DatePeriod(days = 2)))
            add(easter.minus(DatePeriod(days = 1)))
        }
    }
}

object Payday {
    private fun weekend(d: LocalDate) = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY

    fun isDayOff(d: LocalDate, settings: MintSettings): Boolean =
        weekend(d) || d in settings.daysOff || (settings.builtInHolidays && d in PhHolidays.of(d.year) && d !in settings.holidaysIgnored)

    fun onOrBefore(d: LocalDate, settings: MintSettings): LocalDate {
        var day = d
        var steps = 0
        while (isDayOff(day, settings) && steps < 31) { day = day.minus(DatePeriod(days = 1)); steps++ }
        return day
    }

    fun holidayList(year: Int, settings: MintSettings): List<Pair<LocalDate, Boolean>> =
        PhHolidays.of(year).sorted().map { it to (settings.builtInHolidays && it !in settings.holidaysIgnored) }
}
