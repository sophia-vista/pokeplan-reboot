package app.pokeplan.kit

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** Calendar arithmetic that Plan, Base, Mint and Link each had a copy of. Pure functions, no domain knowledge. */
object Calendars {
    fun isLeap(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

    fun daysInMonth(year: Int, month: Int): Int = when (month) {
        2 -> if (isLeap(year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    /** Months since year 0, so two dates' months subtract to the gap between them. */
    fun monthIndex(d: LocalDate): Int = d.year * 12 + d.monthNumber - 1

    fun monthsBetween(a: LocalDate, b: LocalDate): Int = monthIndex(b) - monthIndex(a)

    /** [day] of [year]/[month], or the month's last day when the month is shorter (31 January + 1 month = 28/29 February). */
    fun clampedDate(year: Int, month: Int, day: Int): LocalDate = LocalDate(year, month, minOf(day, daysInMonth(year, month)))

    fun plusDays(d: LocalDate, n: Int): LocalDate = d.plus(DatePeriod(days = n))
}
