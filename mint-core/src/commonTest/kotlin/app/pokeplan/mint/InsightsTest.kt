package app.pokeplan.mint

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the records say when read together (MINT.md 9, P3 insights). */
class InsightsTest {
    private val today = LocalDate(2026, 11, 27)

    private fun account(id: String, opening: Long = 0) = FinancialAccount(id, id, AccountType.BANK, "PHP", opening, LocalDate(2026, 1, 1))

    private fun tx(id: String, type: TxType, minor: Long, day: LocalDate, payee: String = "", cat: String? = "cat-food", account: String = "a") =
        Transaction(id, type, minor, "PHP", day.toEpochDays() * 86_400_000L, day, account, categoryId = cat, payee = payee)

    private fun data(vararg txs: Transaction) = MintData(accounts = listOf(account("a")), categories = MintRules.seedCategories(), transactions = txs.toList())

    @Test
    fun aMonthlyChargeOfAboutTheSameAmountLooksLikeASubscription() {
        val d = data(
            tx("1", TxType.EXPENSE, 10_000, today.minus(DatePeriod(months = 2)), "Netflix"),
            tx("2", TxType.EXPENSE, 10_000, today.minus(DatePeriod(months = 1)), "Netflix"),
            tx("3", TxType.EXPENSE, 10_000, today, "Netflix"),
            tx("4", TxType.EXPENSE, 5_000, today, "Coffee"),
            tx("5", TxType.EXPENSE, 5_000, today.minus(DatePeriod(days = 3)), "Coffee"),
        )
        val recurring = Insights.recurring(d, today).single()
        assertEquals("Netflix", recurring.payee)
        assertEquals(3, recurring.count)
        assertEquals(10_000L, recurring.averageMinor)
    }

    @Test
    fun aPayeeWithJumpingAmountsIsNotASubscription() {
        val d = data(
            tx("1", TxType.EXPENSE, 10_000, today.minus(DatePeriod(months = 2)), "Shop"),
            tx("2", TxType.EXPENSE, 40_000, today.minus(DatePeriod(months = 1)), "Shop"),
            tx("3", TxType.EXPENSE, 1_000, today, "Shop"),
        )
        assertTrue(Insights.recurring(d, today).isEmpty())
    }

    @Test
    fun thisPeriodIsComparedWithTheOneBefore() {
        val lastMonth = today.minus(DatePeriod(months = 1))
        val d = data(
            tx("1", TxType.EXPENSE, 3_000, lastMonth, "Old"),
            tx("2", TxType.EXPENSE, 5_000, today, "New"),
        )
        val change = Insights.changes(d, BudgetPeriod.MONTHLY, today).single { it.categoryId == "cat-food" }
        assertEquals(5_000L, change.nowMinor)
        assertEquals(3_000L, change.beforeMinor)
        assertEquals(2_000L, change.differenceMinor)
        assertTrue(Insights.trends(d, today).any { it.categoryId == "cat-food" })
    }

    @Test
    fun theMerchantsThatTakeTheMostAreNamed() {
        val d = data(
            tx("1", TxType.EXPENSE, 2_000, today, "Coffee"),
            tx("2", TxType.EXPENSE, 2_000, today, "Coffee"),
            tx("3", TxType.EXPENSE, 12_000, today, "Lazada"),
        )
        val merchants = Insights.merchants(d, today)
        assertEquals("Lazada", merchants.first().payee, "the largest total first")
        assertEquals(16_000L, merchants.sumOf { it.totalMinor })
    }

    @Test
    fun theForecastAddsKnownBillsUsualSpendingAndExpectedIncome() {
        val bill = Bill("b", "Internet", "PHP", Frequency.MONTHLY, today.plus(DatePeriod(days = 5)), expectedMinor = 10_000)
        val income = Bill("i", "Salary", "PHP", Frequency.MONTHLY, today.plus(DatePeriod(days = 10)), expectedMinor = 100_000, income = true)
        val daily = data(tx("1", TxType.EXPENSE, 9_000, today.minus(DatePeriod(days = 10))))
        val d = daily.copy(bills = listOf(bill, income))
        val forecast = Insights.forecast(d, today, 30)
        assertEquals(10_000L, forecast.comingOutMinor)
        assertEquals(100_000L, forecast.comingInMinor)
        assertTrue(forecast.usualSpendingMinor > 0, "the recent pace counts too")
        assertTrue(forecast.netMinor != 0L)
    }

    @Test
    fun availableMoneyIsSpelledOutAsItsCalculation() {
        val lines = Insights.availableLines(MintData(accounts = listOf(account("a", 100_000)), categories = MintRules.seedCategories()), "PHP", today, 30)
        assertTrue(lines.first().contains("1,000.00"), lines.toString())
        assertTrue(lines.any { it.startsWith("Available:") }, lines.toString())
        assertTrue(lines.last().contains("never promises"), lines.last())
    }
}
