package app.pokeplan.mint

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaydayTest {
    private val s = MintSettings()

    @Test
    fun easterAndHolyWeekAreWorkedOutEachYear() {
        assertEquals(LocalDate(2026, 4, 5), PhHolidays.easter(2026))
        assertEquals(LocalDate(2027, 3, 28), PhHolidays.easter(2027))
        assertTrue(LocalDate(2026, 4, 2) in PhHolidays.of(2026))
        assertTrue(LocalDate(2026, 4, 3) in PhHolidays.of(2026))
        assertEquals(LocalDate(2026, 8, 31), PhHolidays.heroesDay(2026))
        assertTrue(LocalDate(2026, 11, 30) in PhHolidays.of(2026))
    }

    @Test
    fun aPaydayMovesToTheNearestWorkingDayOnOrBefore() {
        assertEquals(LocalDate(2026, 11, 13), Payday.onOrBefore(LocalDate(2026, 11, 15), s))
        assertEquals(LocalDate(2026, 11, 27), Payday.onOrBefore(LocalDate(2026, 11, 30), s))
        assertEquals(LocalDate(2026, 12, 15), Payday.onOrBefore(LocalDate(2026, 12, 15), s))
        assertEquals(LocalDate(2026, 12, 29), Payday.onOrBefore(LocalDate(2026, 12, 30), s))
        assertEquals(LocalDate(2027, 2, 26), Payday.onOrBefore(LocalDate(2027, 2, 28), s))
    }

    @Test
    fun theHolidayListCanBeTurnedOffOrExtended() {
        val own = s.copy(daysOff = listOf(LocalDate(2026, 12, 15)))
        assertEquals(LocalDate(2026, 12, 14), Payday.onOrBefore(LocalDate(2026, 12, 15), own))
        val ignored = s.copy(holidaysIgnored = listOf(LocalDate(2026, 11, 30)))
        assertEquals(LocalDate(2026, 11, 30), Payday.onOrBefore(LocalDate(2026, 11, 30), ignored))
        assertEquals(LocalDate(2026, 11, 30), Payday.onOrBefore(LocalDate(2026, 11, 30), s.copy(builtInHolidays = false)))
        assertFalse(Payday.isDayOff(LocalDate(2026, 11, 30), s.copy(builtInHolidays = false)))
        assertTrue(Payday.isDayOff(LocalDate(2026, 11, 28), s.copy(builtInHolidays = false)))
    }

    private fun salary(first: LocalDate, workday: Boolean = true, days: List<Int> = emptyList()) = Bill(
        "pay", "Salary", "PHP", Frequency.SEMI_MONTHLY, first, 2_500_000, income = true, days = days, workday = workday, estimate = true,
    )

    @Test
    fun aTwiceAMonthScheduleFallsOnThe15thAndTheLastDayWithoutDrifting() {
        val b = salary(LocalDate(2026, 11, 15))
        assertEquals(
            listOf(LocalDate(2026, 11, 15), LocalDate(2026, 11, 30), LocalDate(2026, 12, 15), LocalDate(2026, 12, 30), LocalDate(2027, 1, 15), LocalDate(2027, 1, 30), LocalDate(2027, 2, 15), LocalDate(2027, 2, 28)),
            (0..7).map { MintRules.dueDay(b, it) },
        )
        assertEquals(listOf(LocalDate(2026, 11, 30), LocalDate(2026, 12, 15)), (0..1).map { MintRules.dueDay(salary(LocalDate(2026, 11, 20)), it) })
        assertEquals(listOf(LocalDate(2026, 11, 10), LocalDate(2026, 11, 25), LocalDate(2026, 12, 10)), (0..2).map { MintRules.dueDay(salary(LocalDate(2026, 11, 1), days = listOf(25, 10)), it) })
    }

    @Test
    fun theOccurrenceKeepsTheScheduledDayAsItsKeyAndMovesWhenItIsReallyDue() {
        val data = MintData(bills = listOf(salary(LocalDate(2026, 11, 15))))
        val today = LocalDate(2026, 11, 7)
        val occ = MintRules.occurrences(data, data.bills.single(), today, LocalDate(2026, 12, 31), today)
        assertEquals(listOf(LocalDate(2026, 11, 15), LocalDate(2026, 11, 30), LocalDate(2026, 12, 15), LocalDate(2026, 12, 30)), occ.map { it.dueDay })
        assertEquals(listOf(LocalDate(2026, 11, 13), LocalDate(2026, 11, 27), LocalDate(2026, 12, 15), LocalDate(2026, 12, 29)), occ.map { it.payDay })
        assertEquals(OccurrenceState.OVERDUE, MintRules.occurrence(data, data.bills.single(), LocalDate(2026, 11, 15), LocalDate(2026, 11, 14)).state)
        assertEquals(OccurrenceState.DUE, MintRules.occurrence(data, data.bills.single(), LocalDate(2026, 11, 15), LocalDate(2026, 11, 13)).state)
        val moved = data.copy(settings = data.settings.copy(daysOff = listOf(LocalDate(2026, 11, 13))))
        val again = MintRules.occurrences(moved, moved.bills.single(), today, LocalDate(2026, 11, 20), today).single()
        assertEquals(LocalDate(2026, 11, 15), again.dueDay)
        assertEquals(LocalDate(2026, 11, 12), again.payDay)
        val plain = MintData(bills = listOf(salary(LocalDate(2026, 11, 15), workday = false)))
        assertEquals(LocalDate(2026, 11, 15), MintRules.occurrences(plain, plain.bills.single(), today, LocalDate(2026, 11, 20), today).single().payDay)
    }
}
