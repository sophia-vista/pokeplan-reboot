package app.pokeplan.mint

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals

/** `occurrences` starts at an arithmetic index instead of walking from the first date: the result must be what the walk from index 0 gives. */
class BillOccurrencesWindowTest {
    private fun walk(data: MintData, bill: Bill, from: LocalDate, to: LocalDate, today: LocalDate): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var i = 0
        while (i <= 5_000) {
            val due = MintRules.dueDay(bill, i)
            if (due > to.plus(7, DateTimeUnit.DAY)) break
            val pay = MintRules.payDay(data, bill, due)
            if ((due >= from && due <= to) || (pay >= from && pay <= to)) out += due
            if (bill.frequency == Frequency.ONCE) break
            i++
        }
        return out
    }

    @Test
    fun theWindowedStartGivesTheSameOccurrencesAsWalkingFromTheFirstDate() {
        val data = MintData()
        val firsts = listOf(LocalDate(2024, 1, 31), LocalDate(2024, 2, 29), LocalDate(2025, 3, 15), LocalDate(2026, 1, 1), LocalDate(2026, 6, 30), LocalDate(2026, 12, 31))
        val windows = listOf(
            LocalDate(2026, 1, 1) to LocalDate(2026, 1, 31), LocalDate(2026, 2, 1) to LocalDate(2026, 3, 1), LocalDate(2026, 11, 20) to LocalDate(2026, 12, 20),
            LocalDate(2028, 2, 27) to LocalDate(2028, 3, 2), LocalDate(2023, 1, 1) to LocalDate(2023, 12, 31), LocalDate(2030, 5, 5) to LocalDate(2030, 5, 5),
        )
        val dayLists = listOf(emptyList(), listOf(1, 15), listOf(10, 20, 31), listOf(29, 30))
        for (frequency in Frequency.entries) for (first in firsts) for (days in dayLists) {
            if (frequency != Frequency.SEMI_MONTHLY && days.isNotEmpty()) continue
            for (workday in listOf(false, true)) {
                val bill = Bill("b", "Rent", "PHP", frequency, first, days = days, workday = workday)
                for ((from, to) in windows) {
                    val today = LocalDate(2026, 11, 21)
                    val expected = walk(data, bill, from, to, today)
                    val got = MintRules.occurrences(data, bill, from, to, today).map { it.dueDay }
                    assertEquals(expected, got, "$frequency first=$first days=$days workday=$workday window=$from..$to")
                }
            }
        }
    }
}
