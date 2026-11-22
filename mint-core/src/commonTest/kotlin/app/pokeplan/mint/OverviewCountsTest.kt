package app.pokeplan.mint

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class OverviewCountsTest {
    private val today = LocalDate(2026, 11, 16)

    private fun acct(id: String, t: AccountType, opening: Long) = FinancialAccount(id, id, t, "PHP", opening, LocalDate(2026, 1, 1))

    private fun data(window: OverviewWindow = OverviewWindow.DAYS_14, payLater: PayLaterCount = PayLaterCount.WHOLE) = MintData(
        accounts = listOf(acct("bank", AccountType.BANK, 100_000), acct("later", AccountType.PAY_LATER, 90_000)),
        plans = listOf(
            InstallmentPlan(
                "p1", "later", "Phone", "PHP", totalMinor = 90_000, startDay = LocalDate(2026, 11, 20), every = PlanEvery.MONTH,
                installments = Installments.generate(90_000, 3, PlanEvery.MONTH, LocalDate(2026, 11, 20), FeeRule.None),
            ),
        ),
        bills = listOf(Bill("b1", "Internet", "PHP", Frequency.MONTHLY, LocalDate(2026, 11, 18), expectedMinor = 500, accountId = "bank")),
        settings = MintSettings(overviewWindow = window, overviewPayLater = payLater),
    )

    @Test
    fun theWindowsEndWhereTheirNamesSay() {
        assertEquals(14, OverviewWindow.DAYS_14.days(today))
        assertEquals(30, OverviewWindow.DAYS_30.days(today))
        assertEquals(14, OverviewWindow.THIS_MONTH.days(today))
        assertEquals(45, OverviewWindow.NEXT_MONTH.days(today))
        assertEquals(0, OverviewWindow.THIS_MONTH.days(LocalDate(2026, 11, 30)))
    }

    @Test
    fun payLaterCountsInFullByDefaultAndIsTheOldNetPosition() {
        assertEquals(10_000L, MintRules.netPosition(data(), today)["PHP"])
        assertEquals(10_000L, MintRules.netPosition(data())["PHP"])
    }

    @Test
    fun payLaterCanCountOnlyWhatIsDueInTheWindow() {
        assertEquals(70_000L, MintRules.netPosition(data(payLater = PayLaterCount.DUE), today)["PHP"])
        assertEquals(40_000L, MintRules.netPosition(data(OverviewWindow.NEXT_MONTH, PayLaterCount.DUE), today)["PHP"])
    }

    @Test
    fun payLaterCanBeLeftOutAndACardIsNeverTouchedByTheChoice() {
        assertEquals(100_000L, MintRules.netPosition(data(payLater = PayLaterCount.NONE), today)["PHP"])
        val withCard = data(payLater = PayLaterCount.NONE).let { it.copy(accounts = it.accounts + acct("card", AccountType.CREDIT_CARD, 20_000)) }
        assertEquals(80_000L, MintRules.netPosition(withCard, today)["PHP"])
    }

    @Test
    fun theBreakdownLinesAddUpToTheNetPosition() {
        val b = MintRules.breakdown(data(payLater = PayLaterCount.DUE), "PHP", today)
        assertEquals(30_000L, b.liabilities.single().balanceMinor)
        assertEquals(b.assetsMinor - b.liabilitiesMinor, b.netMinor)
        assertEquals(70_000L, b.netMinor)
    }

    @Test
    fun theSummaryFollowsTheWindow() {
        val short = OverviewCounts.summary(data(), today)
        assertEquals(14, short.windowDays)
        assertEquals(2, short.dueCount)
        assertEquals(30_500L, short.dueMinor)
        assertEquals(100_000L - 30_500L, short.availableMinor)
        assertEquals(listOf("Internet", "later"), short.next.map { it.name })
        val long = OverviewCounts.summary(data(OverviewWindow.NEXT_MONTH), today)
        assertEquals(60_000L + 500L * 2, long.dueMinor)
    }

    @Test
    fun aRecurringIncomeIsNeverTakenOffAvailableMoneyOrListedAsDue() {
        val salary = Bill("b2", "Salary", "PHP", Frequency.MONTHLY, LocalDate(2026, 11, 20), expectedMinor = 20_000, accountId = "bank", income = true)
        val d = data().let { it.copy(bills = it.bills + salary) }
        val s = OverviewCounts.summary(d, today)
        assertEquals(2, s.dueCount)
        assertEquals(30_500L, s.dueMinor)
        assertEquals(100_000L - 30_500L, s.availableMinor)
        assertEquals(listOf("Internet", "later"), s.next.map { it.name })
        assertEquals(100_000L - 500L, MintRules.available(d.copy(plans = emptyList()), "PHP", today).availableMinor)
    }
}
