package app.pokeplan.mint

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The review inbox's items, made from the records (MINT.md 9.1). */
class ReviewInboxTest {
    private val today = LocalDate(2026, 11, 27)

    private fun account(id: String, opening: Long = 0, type: AccountType = AccountType.BANK) =
        FinancialAccount(id, id, type, "PHP", opening, LocalDate(2026, 1, 1))

    private fun tx(id: String, type: TxType, minor: Long, account: String, day: LocalDate = today, status: TxStatus = TxStatus.POSTED, categoryId: String? = null, captured: Boolean = false, imported: Boolean = false) =
        Transaction(id, type, minor, "PHP", day.toEpochDays() * 86_400_000L, day, account, categoryId = categoryId, status = status, captureRef = "ref".takeIf { captured }, importBatch = "csv:1".takeIf { imported }, unreviewed = imported)

    private fun data(vararg accounts: FinancialAccount, txs: List<Transaction> = emptyList(), bills: List<Bill> = emptyList(), budgets: List<Budget> = emptyList(), recs: List<Reconciliation> = emptyList()) =
        MintData(accounts = accounts.toList(), categories = MintRules.seedCategories(), transactions = txs, bills = bills, budgets = budgets, reconciliations = recs)

    @Test
    fun recordsWaitingForThePlayerComeFirst() {
        val a = account("a")
        val items = ReviewInbox.items(
            data(a, txs = listOf(tx("p", TxType.EXPENSE, 100, "a", status = TxStatus.PENDING, captured = true))),
            today,
        )
        val confirm = items.first()
        assertEquals(ReviewInbox.Kind.CONFIRM, confirm.kind)
        assertEquals(0, confirm.priority)
    }

    @Test
    fun importedRecordsNotYetReviewedAreNamed() {
        val a = account("a")
        val items = ReviewInbox.items(data(a, txs = listOf(tx("i", TxType.EXPENSE, 100, "a", imported = true))), today)
        assertTrue(items.any { it.kind == ReviewInbox.Kind.IMPORTED && it.id == "imported" })
    }

    @Test
    fun aBillDueWithinTheWeekIsComingUp() {
        val a = account("a")
        val bill = Bill("b", "Internet", "PHP", Frequency.MONTHLY, today.plus(kotlinx.datetime.DatePeriod(days = 3)))
        val items = ReviewInbox.items(data(a, bills = listOf(bill)), today)
        assertTrue(items.any { it.kind == ReviewInbox.Kind.UPCOMING }, items.toString())
    }

    @Test
    fun feesInterestAndBudgetsNearTheirLimitAreOffered() {
        val a = account("a")
        val budget = Budget("bud", 10_000, "PHP", BudgetPeriod.MONTHLY, categoryId = "cat-food")
        val items = ReviewInbox.items(
            data(a, txs = listOf(tx("f", TxType.FEE, 1_500, "a", day = today.minusDays(2)), tx("e", TxType.EXPENSE, 9_600, "a", categoryId = "cat-food")), budgets = listOf(budget)),
            today,
        )
        assertTrue(items.any { it.kind == ReviewInbox.Kind.FEES })
        assertTrue(items.any { it.kind == ReviewInbox.Kind.BUDGET }, items.toString())
    }

    @Test
    fun recordsWithoutACategoryAreOffered() {
        val a = account("a")
        val items = ReviewInbox.items(data(a, txs = listOf(tx("m", TxType.EXPENSE, 2_000, "a"))), today)
        val missing = items.single { it.kind == ReviewInbox.Kind.MISSING }
        assertEquals(1, missing.count)
    }

    @Test
    fun anAccountNeverCheckedHasItsOwnBalanceItem() {
        val a = account("a")
        val items = ReviewInbox.items(data(a, txs = listOf(tx("p", TxType.EXPENSE, 100, "a", categoryId = "cat-food"))), today)
        val balance = items.single { it.kind == ReviewInbox.Kind.BALANCE }
        assertEquals("balance:a", balance.id)
        val checked = data(a, txs = listOf(tx("p", TxType.EXPENSE, 100, "a")), recs = listOf(Reconciliation("r", "a", today, 0, 0)))
        assertTrue(ReviewInbox.items(checked, today).none { it.kind == ReviewInbox.Kind.BALANCE }, "a recent check is enough")
    }

    @Test
    fun aQuietDayHasNothingAndItemsAreOrderedByPriority() {
        assertTrue(ReviewInbox.items(data(account("a")), today).isEmpty())
        val a = account("a")
        val bill = Bill("b", "Net", "PHP", Frequency.MONTHLY, today.plus(kotlinx.datetime.DatePeriod(days = 2)))
        val items = ReviewInbox.items(data(a, txs = listOf(tx("m", TxType.EXPENSE, 100, "a"), tx("f", TxType.FEE, 100, "a")), bills = listOf(bill)), today)
        assertEquals(items.map { it.priority }.sorted(), items.map { it.priority }, "priority order")
        assertEquals(ReviewInbox.Kind.UPCOMING, items.first().kind, "a payment due in two days comes before detail clean-up")
    }

    private fun LocalDate.minusDays(n: Int) = LocalDate.fromEpochDays(toEpochDays() - n)
}
