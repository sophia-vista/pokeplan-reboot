package app.pokeplan.kit

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Week and month boundaries shared by every module (Care, Heal, Mint and Plan each had their own copy). */
object Periods {
    /** The first day of the week (as set in Settings) on or before [day]. */
    fun weekStartOf(day: LocalDate, first: DayOfWeek = DayOfWeek.MONDAY): LocalDate {
        val back = (day.dayOfWeek.ordinal - first.ordinal + 7) % 7
        return day.minus(DatePeriod(days = back))
    }

    /** The first day of [day]'s month. */
    fun monthStart(day: LocalDate): LocalDate = LocalDate(day.year, day.monthNumber, 1)

    /** [start, end) of the week holding [day]. */
    fun weekRange(day: LocalDate, first: DayOfWeek = DayOfWeek.MONDAY): Pair<LocalDate, LocalDate> =
        weekStartOf(day, first).let { it to it.plus(7, DateTimeUnit.DAY) }

    /** [start, end) of the month holding [day]. */
    fun monthRange(day: LocalDate): Pair<LocalDate, LocalDate> =
        monthStart(day).let { it to it.plus(1, DateTimeUnit.MONTH) }
}
