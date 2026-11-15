package app.pokeplan.mint

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class InstallmentsTest {
    private val nov10 = LocalDate(2026, 11, 10)

    @Test
    fun principalAddsUpExactlyAndTheLeftoverGoesToTheFirstInstallments() {
        val s = Installments.generate(100_000, 3, PlanEvery.MONTH, nov10, FeeRule.None)
        assertEquals(listOf(33_334L, 33_333L, 33_333L), s.map { it.principalMinor })
        assertEquals(100_000L, s.sumOf { it.principalMinor })
        assertEquals(listOf(LocalDate(2026, 11, 10), LocalDate(2026, 12, 10), LocalDate(2027, 1, 10)), s.map { it.dueDay })
        assertEquals(listOf(1, 2, 3), s.map { it.no })
    }

    @Test
    fun aMonthlyDayThatAMonthLacksFallsOnItsLastDayWithoutDriftingLater() {
        val s = Installments.generate(30_000, 3, PlanEvery.MONTH, LocalDate(2027, 1, 31), FeeRule.None)
        assertEquals(listOf(LocalDate(2027, 1, 31), LocalDate(2027, 2, 28), LocalDate(2027, 3, 31)), s.map { it.dueDay })
    }

    @Test
    fun weeklyAndFortnightlyStepsAreExactDays() {
        assertEquals(listOf(LocalDate(2026, 11, 10), LocalDate(2026, 11, 24)), Installments.generate(1_000, 2, PlanEvery.TWO_WEEKS, nov10, FeeRule.None).map { it.dueDay })
        assertEquals(listOf(LocalDate(2026, 11, 10), LocalDate(2026, 11, 17)), Installments.generate(1_000, 2, PlanEvery.WEEK, nov10, FeeRule.None).map { it.dueDay })
    }

    @Test
    fun feeRulesShareOrChargeOnWhatIsStillUnpaid() {
        assertEquals(listOf(0L, 0L, 0L), Installments.generate(100_000, 3, PlanEvery.MONTH, nov10, FeeRule.None).map { it.feeMinor })
        assertEquals(listOf(250L, 250L, 250L), Installments.generate(100_000, 3, PlanEvery.MONTH, nov10, FeeRule.PerInstallment(250)).map { it.feeMinor })
        assertEquals(listOf(34L, 33L, 33L), Installments.generate(100_000, 3, PlanEvery.MONTH, nov10, FeeRule.Total(100)).map { it.feeMinor })
        // 1.5% a period on what is still unpaid: 100,000 then 66,666 then 33,333 of principal.
        assertEquals(listOf(1_500L, 1_000L, 500L), Installments.generate(100_000, 3, PlanEvery.MONTH, nov10, FeeRule.RatePerPeriod(150)).map { it.feeMinor })
    }

    @Test
    fun aRequestIsCheckedForTheAccountTypeTheCountAndTheFee() {
        assertEquals(Check.Ok, Installments.checkRequest(AccountType.PAY_LATER, 100, 12, FeeRule.None))
        assertEquals(Check.Ok, Installments.checkRequest(AccountType.CREDIT_CARD, 100, 1, FeeRule.RatePerPeriod(0)))
        assert(Installments.checkRequest(AccountType.EWALLET, 100, 3, FeeRule.None) is Check.Refused)
        assert(Installments.checkRequest(AccountType.PAY_LATER, 0, 3, FeeRule.None) is Check.Refused)
        assert(Installments.checkRequest(AccountType.PAY_LATER, 100, 0, FeeRule.None) is Check.Refused)
        assert(Installments.checkRequest(AccountType.PAY_LATER, 100, 37, FeeRule.None) is Check.Refused)
        assert(Installments.checkRequest(AccountType.PAY_LATER, 100, 3, FeeRule.PerInstallment(-1)) is Check.Refused)
        assert(Installments.checkRequest(AccountType.PAY_LATER, 100, 3, FeeRule.RatePerPeriod(10_001)) is Check.Refused)
    }

    @Test
    fun cashFlowCountsARepaymentAsCashOutAndACreditPurchaseNotAtAll() {
        fun acct(id: String, t: AccountType) = FinancialAccount(id, id, t, "PHP", 0, LocalDate(2026, 1, 1))
        fun tx(id: String, type: TxType, amount: Long, from: String, to: String? = null) = Transaction(id, type, amount, "PHP", 0, LocalDate(2026, 11, 7), from, toAccountId = to)
        val d = MintData(
            accounts = listOf(acct("bank", AccountType.BANK), acct("wallet", AccountType.EWALLET), acct("later", AccountType.PAY_LATER)),
            transactions = listOf(
                tx("pay", TxType.INCOME, 100_000, "bank"),
                tx("buy", TxType.EXPENSE, 30_000, "later"), // on credit: not cash
                tx("fee", TxType.FEE, 500, "later"), // on credit: not cash
                tx("repay", TxType.TRANSFER, 10_000, "wallet", "later"), // cash out
                tx("move", TxType.TRANSFER, 5_000, "bank", "wallet"), // between liquid accounts: neither
                tx("coffee", TxType.EXPENSE, 200, "wallet"),
            ),
        )
        val flow = Installments.cashFlow(d, LocalDate(2026, 11, 1), LocalDate(2026, 12, 1), "PHP")
        assertEquals(100_000L, flow.inflowMinor)
        assertEquals(10_200L, flow.outflowMinor)
        assertEquals(89_800L, flow.netMinor)
    }
}
