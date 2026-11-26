package app.pokeplan.app.mint

import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.mint.AccountType
import app.pokeplan.mint.MintRules
import app.pokeplan.mint.TxStatus
import app.pokeplan.mint.TxType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The card month, bulk work and the short record history (MINT.md 5.6B, 5.7). */
class MintStatementsAndHistoryTest {
    private class FakeClock(var millis: Long) : Clock {
        override fun nowMillis() = millis
    }

    private val utc = TimeZone.UTC
    private val start = LocalDateTime(2026, 11, 25, 18, 0).toInstant(utc).toEpochMilliseconds()
    private val today = LocalDate(2026, 11, 25)

    private fun store(): Pair<MintStore, FakeClock> {
        val clock = FakeClock(start)
        val game = GameStore(MemoryStorage(), clock, utc, Random(7)).also { it.start("Sophia", 25) }
        return MintStore(MemoryStorage(), clock, game, utc, rng = Random(3)) to clock
    }

    private fun card(m: MintStore, opening: Long = 0): String =
        assertNotNull(m.addAccount("Visa", AccountType.CREDIT_CARD, "PHP", opening, statementDay = 15, dueDay = 5).record).id

    private fun bank(m: MintStore): String = assertNotNull(m.addAccount("BPI", AccountType.BANK, "PHP", 1_000_000).record).id

    @Test
    fun aStatementPaymentIsATransferThatNeverCountsAsSpending() {
        val (m, _) = store()
        val c = card(m)
        val b = bank(m)
        assertNotNull(m.record(TxType.EXPENSE, 100_000, c, payee = "Laptop", atMillis = today.minusDays(20).toEpochMillis()).record)
        val cycle = assertNotNull(m.currentCardCycle(c))
        assertEquals(100_000L, cycle.remainingMinor)
        val out = assertNotNull(m.payStatement(c, 40_000, b, statementId = null).record)
        assertEquals(TxType.TRANSFER, out.type)
        assertEquals(60_000L, assertNotNull(m.currentCardCycle(c)).remainingMinor, "part payment leaves the rest due")
        assertEquals(0L, MintRules.spent(m.data, today, today, "PHP"), "a repayment is not spending")
        assertEquals(960_000L, MintRules.balance(m.data, m.data.accounts.first { it.id == b }).postedMinor, "the paying account falls")
    }

    @Test
    fun aStatementPaymentCannotBeMoreThanIsOwedOrComeFromACreditLine() {
        val (m, _) = store()
        val c = card(m)
        val b = bank(m)
        assertNotNull(m.record(TxType.EXPENSE, 100_000, c).record)
        assertNull(m.payStatement(c, 200_000, b).record, "more than is owed")
        assertNull(m.payStatement(c, 10_000, c).record, "a credit line does not pay itself")
        assertNull(m.payStatement(c, 0, b).record)
    }

    @Test
    fun theCycleCanBecomeTheRecordedStatement() {
        val (m, _) = store()
        val c = card(m)
        assertNotNull(m.record(TxType.EXPENSE, 100_000, c, atMillis = today.minusDays(20).toEpochMillis(), status = TxStatus.POSTED).record)
        val cycle = assertNotNull(m.currentCardCycle(c))
        assertEquals(100_000L, cycle.billedMinor)
        val statement = assertNotNull(m.recordStatementFromCycle(c, 10_000).record)
        assertEquals(cycle.statementDay, statement.statementDay)
        assertEquals(100_000L, statement.statementBalanceMinor)
        assertEquals(10_000L, statement.minimumPaymentMinor)
        assertEquals(cycle.dueDay, statement.dueDay)
        assertNull(m.recordStatementFromCycle(c, 10_000).record, "the same statement is not recorded twice")
    }

    @Test
    fun recordsThatLookTheSameAreListedAsPossibleDuplicates() {
        val (m, _) = store()
        val b = bank(m)
        val wallet = assertNotNull(m.addAccount("GCash", AccountType.EWALLET, "PHP", 1_000_000).record).id
        assertNotNull(m.record(TxType.EXPENSE, 25_000, b, payee = "Coffee").record)
        assertNotNull(m.record(TxType.EXPENSE, 25_000, b, payee = "Coffee").record)
        assertNotNull(m.record(TxType.EXPENSE, 25_000, b, payee = "Coffee", status = TxStatus.PENDING).record)
        assertNotNull(m.record(TxType.EXPENSE, 26_000, b, payee = "Coffee").record)
        assertNotNull(m.record(TxType.TRANSFER, 25_000, b, toAccountId = wallet).record)
        val group = m.duplicates().single()
        assertEquals(25_000L, group.amountMinor)
        assertEquals(2, group.ids.size, "the pending one is not history yet")
    }

    @Test
    fun manyRecordsCanBeCategorisedAtOnceAndTransfersAreLeftAlone() {
        val (m, _) = store()
        val b = bank(m)
        val a = assertNotNull(m.record(TxType.EXPENSE, 10_000, b, payee = "A").record)
        val t = assertNotNull(m.record(TxType.EXPENSE, 20_000, b, payee = "B").record)
        val wallet = assertNotNull(m.addAccount("GCash", AccountType.EWALLET, "PHP", 1_000_000).record).id
        val transfer = assertNotNull(m.record(TxType.TRANSFER, 30_000, b, toAccountId = wallet).record)
        val cat = assertNotNull(m.addCategory("Food", app.pokeplan.mint.CategoryKind.EXPENSE))
        assertEquals(2, m.categorise(setOf(a.id, t.id, transfer.id), cat.id))
        assertEquals(cat.id, m.data.transactions.single { it.id == a.id }.categoryId)
        assertEquals(cat.id, m.data.transactions.single { it.id == t.id }.categoryId)
        assertNull(m.data.transactions.single { it.id == transfer.id }.categoryId, "a transfer stays uncategorised")
        assertEquals(0, m.categorise(setOf(a.id), "nope"))
    }

    @Test
    fun aRefundIsCheckedAgainstTheExpenseAndNeverExceedsIt() {
        val (m, _) = store()
        val b = bank(m)
        val other = assertNotNull(m.addAccount("Cash", AccountType.CASH, "PHP", 0).record).id
        val purchase = assertNotNull(m.record(TxType.EXPENSE, 100_000, b, payee = "Shop").record)
        val part = assertNotNull(m.record(TxType.REFUND, 40_000, b, refundOf = purchase.id).record)
        assertEquals(purchase.id, part.refundOf)
        assertNull(m.record(TxType.REFUND, 70_000, b, refundOf = purchase.id).record, "40 + 70 is more than the expense")
        assertNull(m.record(TxType.REFUND, 10_000, other, refundOf = purchase.id).record, "a refund goes back to the same account")
        val income = assertNotNull(m.record(TxType.INCOME, 5_000, b, payee = "Gift").record)
        assertNull(m.record(TxType.REFUND, 1_000, b, refundOf = income.id).record, "only an expense can be refunded")
    }

    @Test
    fun theRecordHistorySaysWhatChangedAndNewestComesFirst() {
        val (m, clock) = store()
        val b = bank(m)
        val tx = assertNotNull(m.record(TxType.EXPENSE, 10_000, b, payee = "Old name").record)
        val edited = assertNotNull(m.updateTransaction(tx.copy(amountMinor = 12_000, payee = "New name")).record)
        clock.millis += 5_000
        assertTrue(m.setTransactionStatus(tx.id, TxStatus.EXCLUDED))
        val history = m.historyOf(tx.id)
        assertEquals(listOf("status", "edited"), history.map { it.kind })
        val edit = history.first { it.kind == "edited" }
        assertTrue(edit.detail.contains("amount"), edit.detail)
        assertTrue(edit.detail.contains("name"), edit.detail)
        assertNotNull(edited)
    }

    private fun LocalDate.minusDays(n: Int) = LocalDate.fromEpochDays(toEpochDays() - n)
    private fun LocalDate.toEpochMillis(): Long = toEpochDays() * 86_400_000L + 12 * 3_600_000L
}
