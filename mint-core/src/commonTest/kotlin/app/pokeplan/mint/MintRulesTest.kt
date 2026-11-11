package app.pokeplan.mint

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MintRulesTest {
    private val d = LocalDate(2026, 11, 7)

    private fun account(id: String, type: AccountType = AccountType.BANK, opening: Long = 0, currency: String = "PHP", status: AccountStatus = AccountStatus.ACTIVE) =
        FinancialAccount(id, id, type, currency, opening, d, status = status)

    private fun tx(id: String, type: TxType, minor: Long, account: String, to: String? = null, day: LocalDate = d, cat: String? = null, status: TxStatus = TxStatus.POSTED, up: Boolean = true, currency: String = "PHP") =
        Transaction(id, type, minor, currency, day.toEpochDays() * 86_400_000L, day, account, to, cat, status = status, up = up)

    private fun data(vararg accounts: FinancialAccount, txs: List<Transaction> = emptyList(), bills: List<Bill> = emptyList(), payments: List<BillPayment> = emptyList(), budgets: List<Budget> = emptyList()) =
        MintData(accounts = accounts.toList(), categories = MintRules.seedCategories(), transactions = txs, bills = bills, billPayments = payments, budgets = budgets)

    // --- money ---------------------------------------------------------------------------------------------------------

    @Test
    fun moneyIsParsedExactlyAndNeverRounded() {
        assertEquals(125050L, Money.parse("1,250.50", "PHP"))
        assertEquals(125000L, Money.parse("1250", "PHP"))
        assertEquals(125050L, Money.parse("1.250,50", "PHP"))
        assertEquals(1250L, Money.parse("12,5", "PHP"))
        assertEquals(125000L, Money.parse("1,250", "PHP"))
        assertEquals(1250L, Money.parse("¥1,250", "JPY"))
        assertNull(Money.parse("1.234", "PHP"), "three decimals in a currency with two is refused, not rounded")
        assertNull(Money.parse("abc", "PHP"))
        assertNull(Money.parse("", "PHP"))
        assertNull(Money.parse("1".repeat(20), "PHP"))
        assertEquals(30L, Money.parse("0.10", "USD")!! + Money.parse("0.20", "USD")!!)
    }

    @Test
    fun moneyIsShownWithItsCurrencyAndCanBeHidden() {
        assertEquals("\u20B11,250.50", Money.format(125050, "PHP"))
        assertEquals("\u20B10.05", Money.format(5, "PHP"))
        assertEquals("JPY 1,250", Money.format(1250, "JPY"))
        assertEquals("-\u20B11.00", Money.format(-100, "PHP"))
        assertEquals("\u20B1••••", Money.format(125050, "PHP", hidden = true))
        assertEquals("1250.50", Money.plain(125050, "PHP"))
    }

    // --- balances ------------------------------------------------------------------------------------------------------

    @Test
    fun aBalanceIsTheOpeningBalanceAndPostedTransactionsOnly() {
        val a = account("a", opening = 100_000)
        val s = data(a, txs = listOf(
            tx("1", TxType.EXPENSE, 2_500, "a"), tx("2", TxType.INCOME, 10_000, "a"), tx("3", TxType.FEE, 500, "a"),
            tx("4", TxType.EXPENSE, 9_999, "a", status = TxStatus.PENDING), tx("5", TxType.EXPENSE, 7_777, "a", status = TxStatus.VOIDED),
            tx("6", TxType.EXPENSE, 8_888, "a", status = TxStatus.EXCLUDED),
        ))
        val b = MintRules.balance(s, a)
        assertEquals(100_000 - 2_500 + 10_000 - 500, b.postedMinor)
        assertEquals(-9_999, b.pendingMinor, "pending is shown apart, never mixed into the recorded balance")
    }

    @Test
    fun aTransferMovesBothLegsOnceAndIsNotSpending() {
        val a = account("a", opening = 50_000); val b = account("b", opening = 1_000)
        val s = data(a, b, txs = listOf(tx("t", TxType.TRANSFER, 20_000, "a", to = "b")))
        assertEquals(30_000, MintRules.balance(s, a).postedMinor)
        assertEquals(21_000, MintRules.balance(s, b).postedMinor)
        assertEquals(0, MintRules.spent(s, d, LocalDate(2026, 12, 1), "PHP"))
        assertEquals(0, MintRules.earned(s, d, LocalDate(2026, 12, 1), "PHP"))
        assertEquals(51_000, MintRules.netPosition(s)["PHP"], "money moved between your own accounts changes no total")
    }

    @Test
    fun aCardPurchaseIsOneExpenseAndPayingTheCardIsNotAnother() {
        val bank = account("bank", opening = 100_000); val card = account("card", AccountType.CREDIT_CARD)
        val s = data(bank, card, txs = listOf(tx("buy", TxType.EXPENSE, 30_000, "card"), tx("pay", TxType.TRANSFER, 30_000, "bank", to = "card", day = d)))
        assertEquals(0, MintRules.balance(s, card).postedMinor, "the card is paid off")
        assertEquals(70_000, MintRules.balance(s, bank).postedMinor)
        assertEquals(30_000, MintRules.spent(s, d, LocalDate(2026, 12, 1), "PHP"), "the purchase counted once")
        val onlyBuy = s.copy(transactions = listOf(tx("buy", TxType.EXPENSE, 30_000, "card")))
        assertEquals(30_000, MintRules.balance(onlyBuy, card).postedMinor, "a liability's balance is what is owed")
        assertEquals(100_000 - 30_000, MintRules.netPosition(onlyBuy)["PHP"], "owing reduces the net position")
        assertEquals(100_000, MintRules.available(onlyBuy, "PHP", d).liquidMinor, "a card's balance is never cash")
    }

    @Test
    fun refundsOffsetSpendingAndAreNotIncome() {
        val a = account("a", opening = 0)
        val s = data(a, txs = listOf(tx("1", TxType.EXPENSE, 10_000, "a", cat = "cat-shopping"), tx("2", TxType.REFUND, 4_000, "a", cat = "cat-shopping")))
        assertEquals(6_000, MintRules.spent(s, d, LocalDate(2026, 12, 1), "PHP", "cat-shopping"))
        assertEquals(0, MintRules.earned(s, d, LocalDate(2026, 12, 1), "PHP"))
        assertEquals(listOf<Pair<String?, Long>>("cat-shopping" to 6_000L), MintRules.spendingByCategory(s, d, LocalDate(2026, 12, 1), "PHP"))
    }

    @Test
    fun anAdjustmentMovesTheDisplayedBalanceTheWayItSays() {
        val a = account("a", opening = 10_000); val card = account("c", AccountType.CREDIT_CARD, opening = 5_000)
        val s = data(a, card, txs = listOf(tx("u", TxType.ADJUSTMENT, 500, "a", up = false), tx("v", TxType.ADJUSTMENT, 200, "c", up = true)))
        assertEquals(9_500, MintRules.balance(s, a).postedMinor)
        assertEquals(5_200, MintRules.balance(s, card).postedMinor)
    }

    @Test
    fun differentCurrenciesAreNeverAddedTogether() {
        val s = data(account("p", opening = 1_000), account("u", currency = "USD", opening = 500))
        assertEquals(mapOf("PHP" to 1_000L, "USD" to 500L), MintRules.netPosition(s))
    }

    @Test
    fun aClosedOrArchivedAccountTakesNoNewRecordsAndLeavesThePosition() {
        val closed = account("x", opening = 900, status = AccountStatus.CLOSED)
        val s = data(account("a", opening = 100), closed)
        assertEquals(100L, MintRules.netPosition(s)["PHP"])
        assertTrue(MintRules.checkTransaction(s, tx("t", TxType.EXPENSE, 10, "x")) is Check.Refused)
    }

    @Test
    fun transactionsAreChecked() {
        val s = data(account("a"), account("b"), account("u", currency = "USD"))
        assertTrue(MintRules.checkTransaction(s, tx("1", TxType.EXPENSE, 0, "a")) is Check.Refused)
        assertTrue(MintRules.checkTransaction(s, tx("2", TxType.TRANSFER, 10, "a", to = "a")) is Check.Refused)
        assertTrue(MintRules.checkTransaction(s, tx("3", TxType.TRANSFER, 10, "a", to = "u")) is Check.Refused)
        assertTrue(MintRules.checkTransaction(s, tx("4", TxType.TRANSFER, 10, "a")) is Check.Refused)
        assertTrue(MintRules.checkTransaction(s, tx("5", TxType.EXPENSE, 10, "a", to = "b")) is Check.Refused)
        assertTrue(MintRules.checkTransaction(s, tx("6", TxType.EXPENSE, 10, "a", currency = "USD")) is Check.Refused)
        assertTrue(MintRules.checkTransaction(s, tx("7", TxType.EXPENSE, 10, "a", cat = "cat-salary")) is Check.Refused)
        assertEquals(Check.Ok, MintRules.checkTransaction(s, tx("8", TxType.EXPENSE, 10, "a", cat = "cat-food")))
        assertEquals(Check.Ok, MintRules.checkTransaction(s, tx("9", TxType.TRANSFER, 10, "a", to = "b")))
    }

    // --- budgets -------------------------------------------------------------------------------------------------------

    @Test
    fun aBudgetIsDerivedFromPostedSpendingInItsPeriodAndOverspendingIsJustANumber() {
        val a = account("a")
        val budget = Budget("b", 10_000, "PHP", BudgetPeriod.MONTHLY, "cat-food")
        val s = data(a, budgets = listOf(budget), txs = listOf(
            tx("1", TxType.EXPENSE, 6_000, "a", cat = "cat-food"), tx("2", TxType.EXPENSE, 7_000, "a", cat = "cat-food", day = LocalDate(2026, 11, 30)),
            tx("3", TxType.EXPENSE, 9_000, "a", cat = "cat-food", day = LocalDate(2026, 10, 31)), tx("4", TxType.EXPENSE, 1_000, "a", cat = "cat-fun"),
            tx("5", TxType.TRANSFER, 99_000, "a", to = null),
        ))
        val p = MintRules.progress(s, budget, d)
        assertEquals(LocalDate(2026, 11, 1), p.start); assertEquals(LocalDate(2026, 12, 1), p.endExclusive)
        assertEquals(13_000, p.spentMinor); assertEquals(-3_000, p.remainingMinor); assertEquals(130, p.percent)
    }

    @Test
    fun weeksStartWhereTheyAreAskedTo() {
        val (start, end) = MintRules.periodOf(BudgetPeriod.WEEKLY, LocalDate(2026, 11, 7)) // a Saturday
        assertEquals(LocalDate(2026, 11, 2), start); assertEquals(LocalDate(2026, 11, 9), end)
        assertEquals(LocalDate(2026, 2, 1) to LocalDate(2026, 3, 1), MintRules.periodOf(BudgetPeriod.MONTHLY, LocalDate(2026, 2, 28)))
    }

    // --- bills ---------------------------------------------------------------------------------------------------------

    private fun bill(freq: Frequency, first: LocalDate, expected: Long? = 100_000) = Bill("bill", "Internet", "PHP", freq, first, expected)

    @Test
    fun aMonthlyBillOnThe31stIsDueOnTheLastDayOfShortMonthsAndNeverDrifts() {
        val b = bill(Frequency.MONTHLY, LocalDate(2026, 1, 31))
        assertEquals(LocalDate(2026, 2, 28), MintRules.dueDay(b, 1))
        assertEquals(LocalDate(2026, 3, 31), MintRules.dueDay(b, 2))
        assertEquals(LocalDate(2026, 4, 30), MintRules.dueDay(b, 3))
        assertEquals(LocalDate(2028, 2, 29), MintRules.dueDay(b, 25), "leap year")
        assertEquals(LocalDate(2027, 1, 31), MintRules.dueDay(b, 12))
    }

    @Test
    fun aYearlyBillOnLeapDayIsDueOn28FebInOtherYears() {
        val b = bill(Frequency.YEARLY, LocalDate(2028, 2, 29))
        assertEquals(LocalDate(2029, 2, 28), MintRules.dueDay(b, 1)); assertEquals(LocalDate(2032, 2, 29), MintRules.dueDay(b, 4))
        assertEquals(LocalDate(2026, 11, 14), MintRules.dueDay(bill(Frequency.WEEKLY, LocalDate(2026, 11, 7)), 1))
    }

    @Test
    fun occurrencesCoverARangeAndAOneTimeBillHasOne() {
        val s = data(account("a"))
        val monthly = bill(Frequency.MONTHLY, LocalDate(2026, 9, 15))
        assertEquals(listOf(LocalDate(2026, 10, 15), LocalDate(2026, 11, 15), LocalDate(2026, 12, 15)), MintRules.occurrences(s, monthly, LocalDate(2026, 10, 1), LocalDate(2026, 12, 31), d).map { it.dueDay })
        val once = bill(Frequency.ONCE, LocalDate(2026, 12, 1))
        assertEquals(1, MintRules.occurrences(s, once, LocalDate(2026, 1, 1), LocalDate(2030, 1, 1), d).size)
        assertTrue(MintRules.occurrences(s, monthly.copy(status = PlanStatus.PAUSED), LocalDate(2026, 10, 1), LocalDate(2026, 12, 31), d).isEmpty())
    }

    @Test
    fun anOccurrenceIsUpcomingDueOrOverdueUntilTheUserSettlesIt() {
        val b = bill(Frequency.MONTHLY, LocalDate(2026, 10, 7))
        val s = data(account("a"), bills = listOf(b))
        val states = MintRules.occurrences(s, b, LocalDate(2026, 10, 1), LocalDate(2026, 12, 31), d).associate { it.dueDay to it.state }
        assertEquals(OccurrenceState.OVERDUE, states[LocalDate(2026, 10, 7)]); assertEquals(OccurrenceState.DUE, states[LocalDate(2026, 11, 7)]); assertEquals(OccurrenceState.UPCOMING, states[LocalDate(2026, 12, 7)])
    }

    @Test
    fun payingABillCreatesNoTransactionAndAPartialPaymentKeepsWhatRemains() {
        val b = bill(Frequency.MONTHLY, LocalDate(2026, 11, 7))
        val partial = BillPayment("p", "bill", d, SettleStatus.PARTIAL, 40_000, d)
        val s = data(account("a"), bills = listOf(b), payments = listOf(partial))
        val o = MintRules.occurrence(s, b, d, d)
        assertEquals(OccurrenceState.PARTIAL, o.state); assertEquals(60_000, o.remainingMinor)
        assertTrue(s.transactions.isEmpty())
        val paid = s.copy(billPayments = listOf(partial.copy(status = SettleStatus.PAID, paidMinor = 100_000)))
        assertTrue(MintRules.upcoming(paid, d, 5).none { it.dueDay == d }, "a settled occurrence is not shown again")
        assertEquals(1, MintRules.upcoming(paid.copy(billPayments = emptyList()), d, 5).count { it.dueDay == d })
    }

    @Test
    fun availableMoneyShowsItsBasis() {
        val b = bill(Frequency.MONTHLY, LocalDate(2026, 11, 10), 15_000)
        val s = data(account("a", opening = 100_000), account("c", AccountType.CREDIT_CARD, opening = 50_000), bills = listOf(b))
        val a = MintRules.available(s, "PHP", d)
        assertEquals(100_000, a.liquidMinor); assertEquals(15_000, a.billsMinor); assertEquals(1, a.billsCount); assertEquals(85_000, a.availableMinor)
    }

    // --- Ledger Research -----------------------------------------------------------------------------------------------

    @Test
    fun anEmptyMintNeverPays() {
        val empty = data(account("a", opening = 10_000))
        assertTrue(!MintRules.reconcilePays(empty, "a", d)); assertTrue(!MintRules.reviewPays(empty, d))
    }

    @Test
    fun aReconciliationPaysOncePerAccountPerDayAndTwiceADay() {
        val s = data(account("a"), account("b"), account("c"), txs = listOf(tx("1", TxType.EXPENSE, 100, "a"), tx("2", TxType.EXPENSE, 100, "b"), tx("3", TxType.EXPENSE, 100, "c")))
        assertTrue(MintRules.reconcilePays(s, "a", d))
        val paidA = s.copy(reconciliations = listOf(Reconciliation("r1", "a", d, 0, 0, paid = true)))
        assertTrue(!MintRules.reconcilePays(paidA, "a", d)); assertTrue(MintRules.reconcilePays(paidA, "b", d))
        val two = paidA.copy(reconciliations = paidA.reconciliations + Reconciliation("r2", "b", d, 0, 0, paid = true))
        assertTrue(!MintRules.reconcilePays(two, "c", d), "two a day")
        assertTrue(MintRules.reconcilePays(two, "c", LocalDate(2026, 11, 8)))
    }

    @Test
    fun aReviewPaysOncePerWeekAndTurningProgressionOffPaysNothing() {
        val s = data(account("a"), txs = listOf(tx("1", TxType.EXPENSE, 100, "a")))
        assertTrue(MintRules.reviewPays(s, d))
        assertTrue(!MintRules.reviewPays(s.copy(reviews = listOf(ReviewRecord("v", LocalDate(2026, 11, 3), paid = true))), d), "same week")
        assertTrue(MintRules.reviewPays(s.copy(reviews = listOf(ReviewRecord("v", LocalDate(2026, 11, 1), paid = true))), d), "the week before")
        val off = s.copy(settings = MintSettings(progression = false))
        assertTrue(!MintRules.reviewPays(off, d)); assertTrue(!MintRules.reconcilePays(off, "a", d))
    }

    @Test
    fun adjustmentsMadeByAReconciliationDoNotCountAsHistoryToReconcile() {
        val s = data(account("a"), txs = listOf(tx("1", TxType.ADJUSTMENT, 100, "a").copy(source = TxSource.ADJUSTMENT)))
        assertTrue(!MintRules.reconcilePays(s, "a", d), "a lone adjustment is not an activity to reconcile")
    }

    @Test
    fun searchFindsByWordAmountAndFilters() {
        val a = account("a")
        val t1 = tx("1", TxType.EXPENSE, 12_345, "a", cat = "cat-food").copy(payee = "Wet market")
        val s = data(a, txs = listOf(t1, tx("2", TxType.INCOME, 5_000, "a", cat = "cat-salary")))
        assertEquals(listOf("1"), MintRules.search(s, "market").map { it.id })
        assertEquals(listOf("1"), MintRules.search(s, "123.45").map { it.id })
        assertEquals(listOf("2"), MintRules.search(s, type = TxType.INCOME).map { it.id })
        assertEquals(listOf("1"), MintRules.search(s, categoryId = "cat-food").map { it.id })
        assertEquals(2, MintRules.search(s).size)
    }
}
