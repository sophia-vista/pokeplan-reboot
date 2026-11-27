package app.pokeplan.mint

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The card month a credit line's statement and due days make (MINT.md 5.6B, 5.6D). */
class CardCyclesTest {
    private fun account(id: String, type: AccountType = AccountType.CREDIT_CARD, opening: Long = 0, statementDay: Int = 15, dueDay: Int = 5) =
        FinancialAccount(id, id, type, "PHP", opening, LocalDate(2026, 1, 1), statementDay = statementDay, dueDay = dueDay)

    private fun tx(id: String, type: TxType, minor: Long, account: String, day: LocalDate, to: String? = null, status: TxStatus = TxStatus.POSTED) =
        Transaction(id, type, minor, "PHP", day.toEpochDays() * 86_400_000L, day, account, to, status = status)

    private fun statement(id: String, account: String, close: LocalDate, balance: Long, minimum: Long, due: LocalDate) =
        CreditStatement(id, account, close, balance, minimum, due)

    @Test
    fun theOpenCycleIsWhatTheLastCloseBilled() {
        val card = account("card")
        val data = MintData(
            accounts = listOf(card),
            transactions = listOf(
                tx("p1", TxType.EXPENSE, 100_000, "card", LocalDate(2026, 11, 3)),
                tx("p2", TxType.EXPENSE, 50_000, "card", LocalDate(2026, 11, 20)), // after the close, so in the next cycle
            ),
        )
        val today = LocalDate(2026, 11, 25)
        val cycle = assertNotNull(CardCycles.current(data, card, today))
        assertEquals(LocalDate(2026, 11, 15), cycle.statementDay)
        assertEquals(LocalDate(2026, 12, 5), cycle.dueDay)
        assertEquals(100_000L, cycle.billedMinor, "only what was owed at the close")
        assertEquals(0L, cycle.paidMinor)
        assertEquals(CardCycles.State.UPCOMING, cycle.state(today))
    }

    @Test
    fun paymentsSinceTheCloseAreTrackedAndPartlyPaid() {
        val card = account("card")
        val data = MintData(
            accounts = listOf(card),
            transactions = listOf(
                tx("p1", TxType.EXPENSE, 100_000, "card", LocalDate(2026, 11, 3)),
                tx("pay", TxType.TRANSFER, 30_000, "bank", LocalDate(2026, 11, 20), to = "card"),
            ),
        )
        val cycle = assertNotNull(CardCycles.current(data, card, LocalDate(2026, 11, 25)))
        assertEquals(30_000L, cycle.paidMinor)
        assertEquals(70_000L, cycle.remainingMinor)
        assertEquals(CardCycles.State.PARTLY_PAID, cycle.state(LocalDate(2026, 11, 25)))
    }

    @Test
    fun theRecordedStatementsMinimumIsCarriedAndPaidOffShowsPaid() {
        val card = account("card")
        val due = LocalDate(2026, 12, 5)
        val data = MintData(
            accounts = listOf(card),
            transactions = listOf(tx("p1", TxType.EXPENSE, 100_000, "card", LocalDate(2026, 11, 3))),
            creditStatements = listOf(statement("s1", "card", LocalDate(2026, 11, 15), 100_000, 10_000, due)),
        )
        val cycle = assertNotNull(CardCycles.current(data, card, LocalDate(2026, 11, 25)))
        assertEquals(10_000L, cycle.minimumMinor)
        assertEquals("s1", cycle.statementId)
        assertEquals(CardCycles.State.DUE, cycle.state(due))
        val paid = data.copy(transactions = data.transactions + tx("pay", TxType.TRANSFER, 100_000, "bank", LocalDate(2026, 12, 1), to = "card"))
        assertEquals(CardCycles.State.PAID, assertNotNull(CardCycles.current(paid, card, LocalDate(2026, 12, 6))).state(LocalDate(2026, 12, 6)))
    }

    @Test
    fun anUnpaidDueDayBecomesOverdue() {
        val card = account("card")
        val data = MintData(accounts = listOf(card), transactions = listOf(tx("p1", TxType.EXPENSE, 100_000, "card", LocalDate(2026, 11, 3))))
        val cycle = assertNotNull(CardCycles.current(data, card, LocalDate(2026, 12, 10)))
        assertEquals(CardCycles.State.OVERDUE, cycle.state(LocalDate(2026, 12, 10)))
    }

    @Test
    fun aMonthThatLacksTheDayClampsToItsLastDay() {
        val card = account("card", statementDay = 31, dueDay = 5)
        val data = MintData(accounts = listOf(card), transactions = listOf(tx("p1", TxType.EXPENSE, 10_000, "card", LocalDate(2026, 11, 10))))
        val cycle = assertNotNull(CardCycles.current(data, card, LocalDate(2026, 11, 30)))
        assertEquals(LocalDate(2026, 11, 30), cycle.statementDay, "November has no 31st")
    }

    @Test
    fun anObligationAlreadyABillIsNotShownTwice() {
        val card = account("card")
        val data = MintData(
            accounts = listOf(card),
            transactions = listOf(tx("p1", TxType.EXPENSE, 100_000, "card", LocalDate(2026, 11, 3))),
            bills = listOf(Bill("b1", "SPayLater bill", "PHP", Frequency.MONTHLY, LocalDate(2026, 11, 5), accountId = "card")),
        )
        assertTrue(CardCycles.dueWithin(data, LocalDate(2026, 11, 25), 30).isEmpty(), "the bill already shows this payment")
        assertNotNull(CardCycles.current(data, card, LocalDate(2026, 11, 25)), "the cycle itself still exists")
    }

    @Test
    fun theNextCloseIsTheUpcomingStatement() {
        val card = account("card", dueDay = 5).copy(statementDay = 28)
        assertEquals(LocalDate(2026, 11, 28) to LocalDate(2026, 12, 5), CardCycles.next(card, LocalDate(2026, 11, 27)))
        assertEquals(LocalDate(2026, 12, 28) to LocalDate(2027, 1, 5), CardCycles.next(card, LocalDate(2026, 11, 29)))
        assertNull(CardCycles.next(card.copy(statementDay = 0), LocalDate(2026, 11, 27)))
    }

    @Test
    fun aCycleBeforeTheAccountWasAddedIsNotAStatement() {
        // The account was added on 20 November with an opening balance already owed; its statement day is the 28th and
        // its due day the 5th. Nothing closed in October because the account did not exist then.
        val card = FinancialAccount("card", "card", AccountType.PAY_LATER, "PHP", 500_000, LocalDate(2026, 11, 20), statementDay = 28, dueDay = 5)
        val data = MintData(accounts = listOf(card))
        val today = LocalDate(2026, 11, 27)
        val cycle = CardCycles.current(data, card, today)
        assertNull(cycle, "no statement has closed since the account was added")
        assertTrue(CardCycles.dueWithin(data, today, 30).isEmpty(), "so nothing is shown as overdue")
        // Once a statement closes on the 28th, the cycle is real: due on the 5th of December.
        val later = LocalDate(2026, 12, 1)
        val first = assertNotNull(CardCycles.current(data, card, later))
        assertEquals(LocalDate(2026, 11, 28), first.statementDay)
        assertEquals(LocalDate(2026, 12, 5), first.dueDay)
        assertEquals(CardCycles.State.UPCOMING, first.state(later))
        assertEquals(500_000L, first.billedMinor)
    }

    @Test
    fun aCardWithoutBothDaysHasNoCycle() {
        val card = account("card").copy(statementDay = 0)
        assertNull(CardCycles.current(MintData(accounts = listOf(card)), card, LocalDate(2026, 11, 25)))
        assertTrue(CardCycles.recent(MintData(accounts = listOf(card)), card, LocalDate(2026, 11, 25)).isEmpty())
    }

    @Test
    fun obligationsWithinTheWindowAreListedInDueOrder() {
        val card = account("card", dueDay = 5)
        val later = account("later", statementDay = 20, dueDay = 25)
        val data = MintData(
            accounts = listOf(card, later),
            transactions = listOf(
                tx("p1", TxType.EXPENSE, 100_000, "card", LocalDate(2026, 11, 3)),
                tx("p2", TxType.EXPENSE, 50_000, "later", LocalDate(2026, 11, 10)),
            ),
        )
        val due = CardCycles.dueWithin(data, LocalDate(2026, 11, 25), 30)
        assertEquals(listOf("later", "card"), due.map { it.first.id }, "the 25th is due today, the 5th next month")
        assertEquals(listOf("later"), CardCycles.dueWithin(data, LocalDate(2026, 11, 25), 1).map { it.first.id })
    }
}
