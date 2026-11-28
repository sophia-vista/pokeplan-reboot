package app.pokeplan.kit

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class PeriodsTest {
    // 2026-11-28 is a Saturday.
    private val saturday = LocalDate(2026, 11, 28)

    @Test fun weekStartsOnMondayByDefault() = assertEquals(LocalDate(2026, 11, 23), Periods.weekStartOf(saturday))

    @Test fun weekStartHonoursTheSetting() {
        assertEquals(LocalDate(2026, 11, 22), Periods.weekStartOf(saturday, DayOfWeek.SUNDAY))
        assertEquals(LocalDate(2026, 11, 28), Periods.weekStartOf(saturday, DayOfWeek.SATURDAY))
    }

    @Test fun weekStartOfTheFirstDayIsItself() = assertEquals(LocalDate(2026, 11, 23), Periods.weekStartOf(LocalDate(2026, 11, 23)))

    @Test fun weekCrossesAYearEnd() = assertEquals(LocalDate(2026, 12, 28), Periods.weekStartOf(LocalDate(2027, 1, 1)))

    @Test fun weekRangeIsHalfOpen() = assertEquals(LocalDate(2026, 11, 23) to LocalDate(2026, 11, 30), Periods.weekRange(saturday))

    @Test fun monthRangeRollsOverTheYear() {
        assertEquals(LocalDate(2026, 12, 1), Periods.monthStart(LocalDate(2026, 12, 31)))
        assertEquals(LocalDate(2026, 12, 1) to LocalDate(2027, 1, 1), Periods.monthRange(LocalDate(2026, 12, 31)))
    }
}
