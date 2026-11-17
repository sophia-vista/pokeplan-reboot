package app.pokeplan.mint

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonthlyBillsTest {
    private fun account(statement: Int = 0, due: Int = 0) =
        FinancialAccount("a", "SPayLater", AccountType.PAY_LATER, "PHP", openingDay = LocalDate(2026, 1, 1), statementDay = statement, dueDay = due)

    @Test
    fun aPurchaseLandsOnTheFirstDueDayAfterTheStatementItFallsIn() {
        val a = account(statement = 5, due = 15)
        // Bought on the 7th: it is on the 5th of next month's statement, due on the 15th after it.
        assertEquals(LocalDate(2026, 12, 15), MonthlyBills.firstDue(a, LocalDate(2026, 11, 7), PlanEvery.MONTH))
        // Bought on the 3rd: it is on this month's statement (the 5th), due on the 15th.
        assertEquals(LocalDate(2026, 11, 15), MonthlyBills.firstDue(a, LocalDate(2026, 11, 3), PlanEvery.MONTH))
        // Bought on the statement day itself counts for that statement.
        assertEquals(LocalDate(2026, 11, 15), MonthlyBills.firstDue(a, LocalDate(2026, 11, 5), PlanEvery.MONTH))
        // A due day before the statement day belongs to the month after the statement.
        assertEquals(LocalDate(2027, 1, 3), MonthlyBills.firstDue(account(statement = 20, due = 3), LocalDate(2026, 12, 10), PlanEvery.MONTH))
    }

    @Test
    fun withoutAStatementDayItIsTheNextDueDayAndWithoutADueDayAMonthAfter() {
        assertEquals(LocalDate(2026, 11, 15), MonthlyBills.firstDue(account(due = 15), LocalDate(2026, 11, 7), PlanEvery.MONTH))
        assertEquals(LocalDate(2026, 12, 15), MonthlyBills.firstDue(account(due = 15), LocalDate(2026, 11, 15), PlanEvery.MONTH))
        assertEquals(LocalDate(2026, 12, 7), MonthlyBills.firstDue(account(), LocalDate(2026, 11, 7), PlanEvery.MONTH))
        // A weekly plan does not follow the account's due day.
        assertEquals(LocalDate(2026, 11, 14), MonthlyBills.firstDue(account(due = 15), LocalDate(2026, 11, 7), PlanEvery.WEEK))
    }

    @Test
    fun aShortMonthFallsOnItsLastDayAndTheDueDayMovesWithinItsMonth() {
        assertEquals(LocalDate(2027, 2, 28), MonthlyBills.dayOnOrAfter(31, LocalDate(2027, 2, 1)))
        assertEquals(LocalDate(2027, 3, 31), MonthlyBills.dayOnOrAfter(31, LocalDate(2027, 2, 28).let { LocalDate(2027, 3, 1) }))
        assertEquals(LocalDate(2026, 12, 15), MonthlyBills.onDueDay(account(due = 15), LocalDate(2026, 12, 16)))
        assertEquals(LocalDate(2026, 12, 16), MonthlyBills.onDueDay(account(), LocalDate(2026, 12, 16)))
        assertTrue(BillMonth(2026, 12) > BillMonth(2026, 11) && BillMonth(2027, 1) > BillMonth(2026, 12))
    }
}
