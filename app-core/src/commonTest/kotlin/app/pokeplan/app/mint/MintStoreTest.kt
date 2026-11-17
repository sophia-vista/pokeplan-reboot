package app.pokeplan.app.mint

import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.reward.MintEvent
import app.pokeplan.core.reward.MintKind
import app.pokeplan.core.reward.Reward
import app.pokeplan.mint.AccountStatus
import app.pokeplan.mint.AccountType
import app.pokeplan.mint.BudgetPeriod
import app.pokeplan.mint.FeeKind
import app.pokeplan.mint.FeeRule
import app.pokeplan.mint.Frequency
import app.pokeplan.mint.BillMonth
import app.pokeplan.mint.InstallmentState
import app.pokeplan.mint.Installments
import app.pokeplan.mint.PlanEvery
import app.pokeplan.mint.MintKinds
import app.pokeplan.mint.MintRules
import app.pokeplan.mint.breakdown
import app.pokeplan.mint.OccurrenceState
import app.pokeplan.mint.PlanStatus
import app.pokeplan.mint.SettleStatus
import app.pokeplan.mint.TxStatus
import app.pokeplan.mint.TxType
import app.pokeplan.mint.TransactionSplit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.pokeplan.app.AppData
import app.pokeplan.app.GameStore
import app.pokeplan.app.Place
import app.pokeplan.app.Tutorials
import app.pokeplan.app.World
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage

class MintStoreTest {
    private class FakeClock(var millis: Long) : Clock {
        override fun nowMillis() = millis
        fun advanceDays(n: Int) { millis += n * 86_400_000L }
    }

    private val utc = TimeZone.UTC
    private val saturday = LocalDateTime(2026, 11, 7, 18, 0).toInstant(utc).toEpochMilliseconds()

    /** A world that records every event it is sent, so a test can see exactly what crosses the boundary. */
    private class Spy(val inner: GameStore) : World {
        val events = mutableListOf<MintEvent>()
        override fun care(event: app.pokeplan.core.reward.CareEvent) = inner.care(event)
        override fun mint(event: MintEvent): List<Reward>? { events += event; return inner.mint(event) }
    }

    private class Setup(val game: GameStore, val mint: MintStore, val spy: Spy, val clock: FakeClock)

    private fun setup(started: Boolean = true): Setup {
        val clock = FakeClock(saturday)
        val game = GameStore(MemoryStorage(), clock, utc, Random(7))
        if (started) game.start("Sophia", 25)
        val spy = Spy(game)
        return Setup(game, MintStore(MemoryStorage(), clock, spy, utc, rng = Random(3)), spy, clock)
    }

    private val Setup.candies get() = game.data.player.inventory[app.pokeplan.core.pokemon.Items.RARE_CANDY]

    /** One bank account with some history, which is what makes an organising moment worth paying for. */
    private fun Setup.withHistory(): String {
        val a = assertNotNull(mint.addAccount("Savings", AccountType.BANK, "PHP", 100_000).record).id
        assertNotNull(mint.record(TxType.EXPENSE, 2_500, a, categoryId = "cat-food", payee = "Market").record)
        return a
    }

    @Test
    fun editingAnAccountChangesItsDetailsAndOpeningBalanceButNotItsCurrencyOnceItHasHistory() {
        val s = setup()
        val fresh = assertNotNull(s.mint.addAccount("Wallet", AccountType.CASH, "PHP", 1_000).record)
        // No history yet: everything can be corrected, the currency included, and the shown balance is derived from the new opening.
        val edited = assertNotNull(s.mint.updateAccount(fresh.copy(name = " Pocket ", institution = "Me", group = "Daily", suffix = "42", openingMinor = 5_000, currency = "usd", type = AccountType.BANK)).record)
        assertEquals(listOf("Pocket", "Me", "Daily", "42", "USD", AccountType.BANK, 5_000L), listOf(edited.name, edited.institution, edited.group, edited.suffix, edited.currency, edited.type, edited.openingMinor))
        assertEquals(5_000, MintRules.balance(s.mint.data, s.mint.data.accounts.single()).postedMinor)
        // With history the currency is fixed, an asset cannot become a liability, and a bad figure is refused without changing anything.
        val id = s.withHistory()
        val a = s.mint.data.accounts.first { it.id == id }
        assertTrue(s.mint.hasHistory(id))
        val again = assertNotNull(s.mint.updateAccount(a.copy(currency = "JPY", type = AccountType.CREDIT_CARD, openingMinor = 90_000)).record)
        assertEquals(listOf("PHP", AccountType.BANK, 90_000L), listOf(again.currency, again.type, again.openingMinor))
        assertEquals(90_000 - 2_500, MintRules.balance(s.mint.data, again).postedMinor, "the balance follows the corrected opening; no record was rewritten")
        assertNotNull(s.mint.updateAccount(a.copy(openingMinor = -1)).refused)
        assertNotNull(s.mint.updateAccount(a.copy(name = "  ")).refused)
        assertEquals(90_000L, s.mint.data.accounts.first { it.id == id }.openingMinor)
        assertNotNull(s.mint.updateAccount(a.copy(id = "nope")).refused)
        assertTrue(s.spy.events.isEmpty(), "editing an account earns and sends nothing")
    }

    @Test
    fun aFreshMintHasTheStartingCategoriesAndNothingElse() {
        val s = setup()
        assertTrue(s.mint.data.categories.size >= 10 && s.mint.data.accounts.isEmpty() && s.mint.data.transactions.isEmpty())
        assertTrue(s.mint.data.settings.progression && !s.mint.data.settings.hideValues)
    }

    @Test
    fun anExpenseNeverPaysAndTheWorldHearsNothing() {
        val s = setup()
        val before = s.candies
        s.withHistory()
        s.mint.record(TxType.INCOME, 50_000, s.mint.data.accounts.single().id, categoryId = "cat-salary")
        assertTrue(s.spy.events.isEmpty(), "money moving is never an event")
        assertEquals(before, s.candies)
        assertTrue(s.game.data.mintPaid.isEmpty())
    }

    @Test
    fun reconcilingPaysOnceAndTheEventCarriesNothingButTheContract() {
        val s = setup()
        val a = s.withHistory()
        val out = s.mint.reconcile(a, 97_500)
        val r = assertNotNull(out.record)
        assertTrue(r.paid && out.rewards.any { it is Reward.Candies })
        assertTrue(s.candies!! > 0)
        val e = s.spy.events.single()
        val text = GameStore.json.encodeToString(MintEvent.serializer(), e)
        assertEquals(MintKind.RECONCILED, e.kind)
        assertEquals("mint", e.module)
        assertEquals(e.ref, e.id.substringAfterLast(':'))
        for (secret in listOf("97500", "100000", "2500", "Savings", "Market", "PHP", a)) assertTrue(secret !in text, "the event leaked $secret: $text")
        // Nothing about the money is in the shared save or its reward log either.
        val shared = GameStore.json.encodeToString(AppData.serializer(), s.game.data)
        for (secret in listOf("Savings", "Market", "97500", "cat-food")) assertTrue(secret !in shared, "the shared save holds $secret")
        assertTrue(s.game.data.rewardLog.all { "PHP" !in it.source && "Market" !in it.source })
    }

    @Test
    fun aDifferenceIsRecordedOnlyWhenAcceptedAndAsAnExplicitAdjustment() {
        val s = setup()
        val a = s.withHistory()
        val refused = s.mint.reconcile(a, 90_000)
        assertNull(refused.record); assertNotNull(refused.refused)
        assertTrue(s.mint.data.reconciliations.isEmpty() && s.mint.data.transactions.none { it.type == TxType.ADJUSTMENT })
        val accepted = assertNotNull(s.mint.reconcile(a, 90_000, acceptAdjustment = true).record)
        val adj = s.mint.data.transactions.single { it.type == TxType.ADJUSTMENT }
        assertEquals(accepted.adjustmentTxId, adj.id)
        assertEquals(7_500, adj.amountMinor); assertFalse(adj.up)
        assertEquals(90_000L, s.mint.bookBalance(a))
    }

    @Test
    fun theSameAccountPaysOncePerDayAndAReplayOfTheEventPaysNothing() {
        val s = setup()
        val a = s.withHistory()
        val first = assertNotNull(s.mint.reconcile(a, 97_500).record)
        val again = assertNotNull(s.mint.reconcile(a, 97_500).record)
        assertTrue(first.paid && !again.paid)
        assertEquals(1, s.spy.events.size)
        assertNull(s.game.mint(s.spy.events.single()), "an event id pays once whatever Mint does")
        s.clock.advanceDays(1)
        assertTrue(assertNotNull(s.mint.reconcile(a, 97_500).record).paid, "the next day pays again")
    }

    @Test
    fun anEmptyMintPaysNothingAndTurningProgressionOffKeepsEverything() {
        val s = setup()
        val empty = assertNotNull(s.mint.addAccount("Cash", AccountType.CASH, "PHP", 1_000).record).id
        assertFalse(assertNotNull(s.mint.reconcile(empty, 1_000).record).paid, "no history, no pay")
        assertFalse(assertNotNull(s.mint.finishReview().record).paid, "nothing to review")
        val a = s.withHistory()
        s.mint.setProgression(false)
        assertFalse(assertNotNull(s.mint.reconcile(a, 97_500).record).paid)
        assertFalse(assertNotNull(s.mint.finishReview().record).paid)
        assertTrue(s.spy.events.isEmpty())
        assertEquals(2, s.mint.data.accounts.size, "every record is still there")
    }

    @Test
    fun aReviewPaysOncePerWeekAndTheMeterFindsALedgerEggIntoMintsParty() {
        val s = setup()
        val a = s.withHistory()
        assertTrue(assertNotNull(s.mint.finishReview().record).paid)
        assertFalse(assertNotNull(s.mint.finishReview().record).paid, "same week")
        assertEquals(RewardEngineMeter.REVIEW, s.game.mintEggMeter())
        // A reconciled account adds 2 more points: 3 + 2 crosses the 4-point meter.
        s.mint.reconcile(a, 97_500)
        val egg = s.game.data.player.partyOf(PartyIds.MINT).single()
        assertEquals(egg.id, s.game.data.player.mintLead?.id)
        assertEquals(app.pokeplan.core.dex.Region.MINT, s.game.data.origins[egg.id]?.region)
        assertEquals(1, s.game.mintEggMeter(), "the meter carries over")
        s.clock.advanceDays(7)
        assertTrue(assertNotNull(s.mint.finishReview().record).paid, "the next week pays")
    }

    private object RewardEngineMeter { val REVIEW = app.pokeplan.core.reward.RewardEngine.MINT_EGG_POINTS.getValue(MintKind.REVIEW) }

    @Test
    fun nothingPaysBeforeAGameHasStarted() {
        val s = setup(started = false)
        val a = s.withHistory()
        assertFalse(assertNotNull(s.mint.reconcile(a, 97_500).record).paid, "no trainer yet: Mint still works, the world pays nothing")
    }

    // --- the records ----------------------------------------------------------------------------------------------------------

    @Test
    fun aTransferIsOneRecordAndIsRefusedWhenOneSided() {
        val s = setup()
        val a = assertNotNull(s.mint.addAccount("A", AccountType.BANK, "PHP", 50_000).record).id
        val b = assertNotNull(s.mint.addAccount("B", AccountType.EWALLET, "PHP").record).id
        assertNotNull(s.mint.record(TxType.TRANSFER, 20_000, a, toAccountId = b).record)
        assertNull(s.mint.record(TxType.TRANSFER, 20_000, a).record)
        assertNull(s.mint.record(TxType.TRANSFER, 20_000, a, toAccountId = a).record)
        assertEquals(1, s.mint.data.transactions.size)
        assertEquals(30_000L, s.mint.bookBalance(a)); assertEquals(20_000L, s.mint.bookBalance(b))
    }

    @Test
    fun anAccountWithHistoryIsArchivedNotDeletedAndAClosedOneTakesNoRecords() {
        val s = setup()
        val a = s.withHistory()
        assertNotNull(s.mint.deleteAccount(a))
        assertEquals(1, s.mint.data.accounts.size)
        s.mint.setAccountStatus(a, AccountStatus.CLOSED)
        assertNull(s.mint.record(TxType.EXPENSE, 100, a).record)
        assertEquals(1, s.mint.data.transactions.size, "history stays")
        val fresh = assertNotNull(s.mint.addAccount("Empty", AccountType.CASH, "PHP").record).id
        assertNull(s.mint.deleteAccount(fresh))
    }

    @Test
    fun aCategoryInUseIsArchivedNotDeleted() {
        val s = setup()
        s.withHistory()
        assertNotNull(s.mint.deleteCategory("cat-food"))
        s.mint.setCategoryArchived("cat-food", true)
        assertTrue(s.mint.data.categories.first { it.id == "cat-food" }.archived)
        assertNull(s.mint.deleteCategory("cat-fun"))
    }

    @Test
    fun excludingOrVoidingKeepsTheRecordButLeavesItOutOfSpending() {
        val s = setup()
        val a = s.withHistory()
        val t = s.mint.data.transactions.single()
        val month = LocalDate(2026, 11, 1) to LocalDate(2026, 12, 1)
        assertEquals(2_500, MintRules.spent(s.mint.data, month.first, month.second, "PHP"))
        s.mint.setTransactionStatus(t.id, TxStatus.EXCLUDED)
        assertEquals(0, MintRules.spent(s.mint.data, month.first, month.second, "PHP"))
        assertEquals(100_000L, s.mint.bookBalance(a))
        assertEquals(1, s.mint.data.transactions.size)
    }

    @Test
    fun aBudgetIsDerivedAndOneBudgetPerCategoryAndPeriod() {
        val s = setup()
        s.withHistory()
        val b = assertNotNull(s.mint.addBudget(10_000, "PHP", BudgetPeriod.MONTHLY, "cat-food").record)
        assertNull(s.mint.addBudget(5_000, "PHP", BudgetPeriod.MONTHLY, "cat-food").record)
        val p = MintRules.progress(s.mint.data, b, s.mint.today)
        assertEquals(2_500, p.spentMinor); assertEquals(25, p.percent)
        s.mint.setBudgetStatus(b.id, PlanStatus.PAUSED)
        assertEquals(PlanStatus.PAUSED, s.mint.data.budgets.single().status)
    }

    @Test
    fun splitTransactionsReconcileExactlyAndFeedCategoryBudgets() {
        val s = setup()
        val m = s.mint
        val wallet = assertNotNull(m.addAccount("Wallet", AccountType.EWALLET, "PHP", 10_000).record).id
        val food = m.data.categories.first { it.id == "cat-food" }
        val health = m.data.categories.first { it.id == "cat-health" }
        val tx = assertNotNull(m.record(
            TxType.EXPENSE, 1_000, wallet, payee = "Market",
            splits = listOf(TransactionSplit(food.id, 600), TransactionSplit(health.id, 400)),
        ).record)

        assertEquals(1_000L, MintRules.spent(m.data, LocalDate(2026, 11, 1), LocalDate(2026, 12, 1), "PHP"))
        assertEquals(600L, MintRules.spent(m.data, LocalDate(2026, 11, 1), LocalDate(2026, 12, 1), "PHP", food.id))
        assertEquals(400L, MintRules.spent(m.data, LocalDate(2026, 11, 1), LocalDate(2026, 12, 1), "PHP", health.id))
        assertEquals(mapOf(food.id to 600L, health.id to 400L), MintRules.spendingByCategory(
            m.data, LocalDate(2026, 11, 1), LocalDate(2026, 12, 1), "PHP",
        ).associate { it.first!! to it.second })
        assertNotNull(m.updateTransaction(tx.copy(splits = listOf(TransactionSplit(food.id, 999)))).refused)
        assertNull(m.record(
            TxType.EXPENSE, 1_000, wallet,
            splits = listOf(TransactionSplit(food.id, 600), TransactionSplit(health.id, 300)),
        ).record)
    }

    @Test
    fun savingsTargetsUseChosenAccountBalancesWithoutDoubleCounting() {
        val s = setup()
        val m = s.mint
        val first = assertNotNull(m.addAccount("Wallet", AccountType.EWALLET, "PHP", 80_000).record)
        val second = assertNotNull(m.addAccount("Savings", AccountType.BANK, "PHP", 40_000).record)
        val target = assertNotNull(m.addSavingsTarget("Emergency", 150_000, "PHP", listOf(first.id, second.id), LocalDate(2027, 12, 31)).record)

        val progress = MintRules.savingsProgress(m.data, target)
        assertEquals(120_000L, progress.savedMinor)
        assertEquals(30_000L, progress.remainingMinor)
        assertEquals(80, progress.percent)
        assertNotNull(m.addSavingsTarget("Other", 50_000, "PHP", listOf(first.id)).refused)
        assertNotNull(m.updateAccount(first.copy(type = AccountType.CREDIT_CARD)).refused)
        assertNotNull(m.deleteAccount(first.id))

        assertNull(m.setSavingsTargetStatus(target.id, PlanStatus.ARCHIVED))
        assertEquals("USD", assertNotNull(m.updateAccount(first.copy(currency = "USD")).record).currency)
        assertNotNull(m.setSavingsTargetStatus(target.id, PlanStatus.ACTIVE))
        val export = m.export(setOf(MintKinds.BUDGETS))
        assertTrue(export.contains("Emergency"))
        m.erase(MintKinds.BUDGETS)
        assertTrue(m.data.savingsTargets.isEmpty())
    }

    @Test
    fun receiptImagesStayOnTheTransactionAndFollowItsExportAndDeleteLifecycle() {
        val s = setup()
        val m = s.mint
        val wallet = assertNotNull(m.addAccount("Wallet", AccountType.EWALLET, "PHP", 10_000).record).id
        val tx = assertNotNull(m.record(TxType.EXPENSE, 1_000, wallet).record)
        val receipt = MintRules.PNG_BASE64_PREFIX + "AAAA"

        assertEquals(receipt, assertNotNull(m.updateTransaction(tx.copy(receiptImage = receipt)).record).receiptImage)
        assertTrue(m.export(setOf(MintKinds.TRANSACTIONS)).contains(receipt))
        assertTrue(m.deleteTransaction(tx.id))
        assertTrue(m.data.transactions.isEmpty())
    }

    @Test
    fun aBudgetsAmountAndPeriodCanChangeButNotIntoADuplicate() {
        val s = setup()
        val monthly = assertNotNull(s.mint.addBudget(10_000, "PHP", BudgetPeriod.MONTHLY, "cat-food").record)
        val weekly = assertNotNull(s.mint.addBudget(2_000, "PHP", BudgetPeriod.WEEKLY, null).record)
        val changed = assertNotNull(s.mint.updateBudget(monthly.id, 3_000, BudgetPeriod.WEEKLY).record)
        assertEquals(3_000, changed.plannedMinor); assertEquals(BudgetPeriod.WEEKLY, changed.period)
        assertEquals(changed, s.mint.data.budgets.first { it.id == monthly.id })
        assertNotNull(s.mint.updateBudget(weekly.id, 2_000, BudgetPeriod.MONTHLY).record, "the overall budget is its own kind")
        assertNull(s.mint.addBudget(1_000, "PHP", BudgetPeriod.WEEKLY, "cat-food").record)
        val another = assertNotNull(s.mint.addBudget(9_000, "PHP", BudgetPeriod.MONTHLY, "cat-food").record)
        assertNotNull(s.mint.updateBudget(another.id, 9_000, BudgetPeriod.WEEKLY).refused, "two budgets for food every week")
        assertEquals(BudgetPeriod.MONTHLY, s.mint.data.budgets.first { it.id == another.id }.period)
        assertNotNull(s.mint.updateBudget(another.id, -5, BudgetPeriod.MONTHLY).refused)
        assertNotNull(s.mint.updateBudget("gone", 5, BudgetPeriod.MONTHLY).refused)
    }

    @Test
    fun aSettledOccurrenceStaysReachableToSettleAgain() {
        val s = setup()
        val bill = assertNotNull(s.mint.addBill("Water", "PHP", Frequency.MONTHLY, LocalDate(2026, 10, 7), 80_000).record)
        val due = LocalDate(2026, 11, 7)
        assertTrue(s.mint.settle(bill.id, due, SettleStatus.PAID, 80_000))
        assertTrue(s.mint.settle(bill.id, LocalDate(2026, 10, 7), SettleStatus.SKIPPED))
        assertTrue(MintRules.upcoming(s.mint.data, s.mint.today).none { it.dueDay == due }, "a paid occurrence leaves the list to settle")
        assertEquals(listOf(due, LocalDate(2026, 10, 7)), MintRules.settled(s.mint.data, s.mint.today).map { it.dueDay }, "but is kept, latest first")
        assertTrue(s.mint.settle(bill.id, due, SettleStatus.PAID, 75_000), "the amount paid is put right")
        val again = MintRules.settled(s.mint.data, s.mint.today).first()
        assertEquals(75_000L, again.payment?.paidMinor); assertEquals(1, s.mint.data.billPayments.count { it.dueDay == due })
        assertTrue(s.mint.settle(bill.id, due, SettleStatus.PARTIAL, 40_000))
        assertTrue(MintRules.settled(s.mint.data, s.mint.today).none { it.dueDay == due }, "partly paid is back in the list to settle")
        assertEquals(40_000L, MintRules.upcoming(s.mint.data, s.mint.today).first { it.dueDay == due }.remainingMinor)
    }

    @Test
    fun payingABillMakesNoTransactionAndCanBeTakenBack() {
        val s = setup()
        val bill = assertNotNull(s.mint.addBill("Internet", "PHP", Frequency.MONTHLY, LocalDate(2026, 11, 7), 150_000).record)
        val due = LocalDate(2026, 11, 7)
        assertEquals(OccurrenceState.DUE, MintRules.occurrence(s.mint.data, bill, due, s.mint.today).state)
        assertTrue(s.mint.settle(bill.id, due, SettleStatus.PARTIAL, 50_000))
        assertEquals(100_000L, MintRules.occurrence(s.mint.data, bill, due, s.mint.today).remainingMinor)
        assertTrue(s.mint.settle(bill.id, due, SettleStatus.PAID, 150_000))
        assertEquals(1, s.mint.data.billPayments.size, "settling again replaces the earlier settlement")
        assertTrue(s.mint.data.transactions.isEmpty(), "marking paid fabricates no transaction")
        s.mint.unsettle(bill.id, due)
        assertEquals(OccurrenceState.DUE, MintRules.occurrence(s.mint.data, bill, due, s.mint.today).state)
        assertTrue(s.mint.settle(bill.id, due, SettleStatus.PARTIAL, 150_000))
        assertEquals(OccurrenceState.PAID, MintRules.occurrence(s.mint.data, bill, due, s.mint.today).state, "a part payment that covers the bill is paid, not left to settle at zero")
        assertTrue(s.spy.events.isEmpty())
    }

    @Test
    fun hidingValuesCoversTheStoresOwnFormatting() {
        val s = setup()
        assertEquals("\u20B11,250.50", s.mint.show(125_050, "PHP"))
        s.mint.setHideValues(true)
        assertEquals("\u20B1••••", s.mint.show(125_050, "PHP"))
    }

    @Test
    fun exportHoldsOnlyTheChosenKindsAndNothingOfTheGame() {
        val s = setup()
        s.withHistory()
        val onlyTx = s.mint.export(setOf(MintKinds.TRANSACTIONS))
        assertTrue("Market" in onlyTx && "Savings" !in onlyTx && "minor units" in onlyTx)
        assertTrue("pokemon" !in s.mint.export().lowercase() && "pokelife-mint-export" in s.mint.export())
    }

    @Test
    fun erasingAccountsTakesTheirRecordsAndNothingElse() {
        val s = setup()
        s.withHistory()
        s.mint.addBudget(10_000, "PHP", BudgetPeriod.MONTHLY, null)
        val pokemon = s.game.data.player.pokemon.size
        s.mint.erase(MintKinds.ACCOUNTS)
        assertTrue(s.mint.data.accounts.isEmpty() && s.mint.data.transactions.isEmpty())
        assertEquals(1, s.mint.data.budgets.size, "other kinds stay")
        assertEquals(pokemon, s.game.data.player.pokemon.size)
        s.mint.eraseAll()
        assertTrue(s.mint.data.budgets.isEmpty() && s.mint.data.categories.isNotEmpty())
    }

    // --- the tutorial ---------------------------------------------------------------------------------------------------------

    @Test
    fun theMintTallyGivesOneLedgerPokemonIntoMintsPartyOnceWithNoRecordMeterOrCap() {
        val s = setup()
        assertTrue(s.game.tutorialOffered(Tutorials.MINT_TALLY))
        val rewards = assertNotNull(s.game.completeTutorial(Tutorials.MINT_TALLY))
        val egg = rewards.filterIsInstance<Reward.Hatched>().single().pokemon
        assertEquals(Place.Party(PartyIds.MINT), s.game.placeOf(egg.id))
        assertEquals(egg.id, s.game.data.player.mintLead?.id)
        assertEquals(app.pokeplan.core.dex.Region.MINT, s.game.data.origins[egg.id]?.region)
        assertEquals(0, s.game.mintEggMeter(), "no meter")
        assertTrue(s.game.data.mintPaid.isEmpty() && s.game.data.activeDays.isEmpty(), "no streak, no event, no cap used")
        assertTrue(s.mint.data.accounts.isEmpty() && s.mint.data.transactions.isEmpty(), "no Mint record")
        assertFalse(s.game.tutorialOffered(Tutorials.MINT_TALLY))
        assertNull(s.game.completeTutorial(Tutorials.MINT_TALLY))
        assertEquals(2, s.game.data.player.pokemon.size)
    }

    @Test
    fun aPlayerWhoAlreadyKeepsRecordsIsNotOfferedTheTally() {
        val s = setup()
        assertFalse(s.game.tutorialOffered(Tutorials.MINT_TALLY, mintRecords = 1))
    }

    @Test
    fun theTallyDoesNotMoveTheBuddyAndAPokemonJoiningMintsPartyLeavesItsOldPlace() {
        val s = setup()
        val buddy = s.game.data.player.buddyId
        s.game.completeTutorial(Tutorials.MINT_TALLY)
        assertEquals(buddy, s.game.data.player.buddyId)
        assertTrue(s.game.data.player.party.isEmpty() && s.game.data.player.partyOf(PartyIds.CARE).isEmpty())
        assertTrue(s.game.setInParty(buddy!!, true, PartyIds.MINT))
        assertEquals(Place.Party(PartyIds.MINT), s.game.placeOf(buddy))
        assertNull(s.game.data.player.buddyId)
        assertTrue(s.game.setMintLead(buddy))
        assertEquals(buddy, s.game.data.player.mintLead?.id)
    }

    @Test
    fun aBillsScheduleCanBeCorrectedUntilAPaymentIsRecordedAndASettlementKeepsTheDayEntered() {
        val s = setup()
        val bill = assertNotNull(s.mint.addBill("Rent", "PHP", Frequency.MONTHLY, LocalDate(2026, 11, 7), 800_000).record)
        val fixed = assertNotNull(s.mint.updateBill(bill.copy(firstDue = LocalDate(2026, 11, 15), frequency = Frequency.WEEKLY)).record)
        assertEquals(LocalDate(2026, 11, 15), fixed.firstDue)
        assertEquals(Frequency.WEEKLY, fixed.frequency)
        val due = LocalDate(2026, 11, 15)
        assertTrue(s.mint.settle(bill.id, due, SettleStatus.PAID, 800_000, paidDay = LocalDate(2026, 11, 6)))
        assertEquals(LocalDate(2026, 11, 6), s.mint.data.billPayments.single().settledDay)
        assertFalse(s.mint.settle(bill.id, due, SettleStatus.PAID, 800_000, paidDay = s.mint.today.plus(kotlinx.datetime.DatePeriod(days = 1))), "a payment is not made in the future")
        assertNull(s.mint.updateBill(fixed.copy(firstDue = LocalDate(2026, 12, 1))).record, "settled occurrences are never rewritten")
        assertNotNull(s.mint.updateBill(fixed.copy(name = "Flat rent")).record)
    }

    @Test
    fun paidRecordItWritesTheLinkedExpenseAndSettlesTheBillInOneStep() {
        val s = setup()
        val acct = assertNotNull(s.mint.addAccount("Savings", AccountType.BANK, "PHP", 100_000).record).id
        val other = assertNotNull(s.mint.addAccount("Wallet", AccountType.CASH, "PHP", 5_000).record).id
        val due = LocalDate(2026, 11, 10)
        val bill = assertNotNull(s.mint.addBill("Internet", "PHP", Frequency.MONTHLY, due, 150_000, accountId = acct).record)
        // The bill's own account is the default.
        val tx = assertNotNull(s.mint.settleAndRecord(bill.id, due, 150_000).record)
        assertEquals(acct, tx.accountId)
        assertEquals(TxType.EXPENSE, tx.type)
        assertEquals(bill.id, tx.billId)
        assertEquals(due, tx.billDueDay)
        val payment = s.mint.data.billPayments.single()
        assertEquals(SettleStatus.PAID, payment.status)
        assertEquals(tx.id, payment.txId)
        assertEquals(100_000 - 150_000, MintRules.balance(s.mint.data, s.mint.data.accounts.first { it.id == acct }).postedMinor, "balance fell by the bill")
        // Another account can be chosen, a part payment stays partial, a settling again replaces the first, and a refused amount writes nothing.
        val before = s.mint.data
        assertNull(s.mint.settleAndRecord(bill.id, due, 0, other).record)
        assertEquals(before, s.mint.data)
        assertNull(s.mint.settleAndRecord(bill.id, due, 100, "nobody").record)
        val later = assertNotNull(s.mint.settleAndRecord(bill.id, due, 50_000, other).record)
        assertEquals(other, later.accountId)
        assertEquals(SettleStatus.PARTIAL, s.mint.data.billPayments.single().status)
        // Settling without recording still works and makes no transaction.
        val b2 = assertNotNull(s.mint.addBill("Water", "PHP", Frequency.MONTHLY, due, 30_000).record)
        val txs = s.mint.data.transactions.size
        assertTrue(s.mint.settle(b2.id, due, SettleStatus.PAID, 30_000))
        assertEquals(txs, s.mint.data.transactions.size)
        // No account at all (the bill has none, none chosen) is a refusal.
        assertNull(s.mint.settleAndRecord(b2.id, LocalDate(2026, 12, 10), 30_000).record)
    }

    @Test
    fun aCardStyleIsCosmeticAndAnUnknownOneReadsAsPlain() {
        val s = setup()
        val a = assertNotNull(s.mint.addAccount("Wallet", AccountType.EWALLET, "PHP", 5_000, card = "blue").record)
        assertEquals("blue", a.card)
        val before = MintRules.balances(s.mint.data).single().postedMinor
        val edited = assertNotNull(s.mint.updateAccount(a.copy(card = "violet")).record)
        assertEquals("violet", edited.card)
        assertEquals(before, MintRules.balances(s.mint.data).single().postedMinor)
        // A style this build does not know is dropped to the plain card, never kept as free text.
        assertEquals("", assertNotNull(s.mint.addAccount("Odd", AccountType.CASH, "PHP", card = "neon-pink").record).card)
        // A custom colour is kept as capitals #RRGGBB; text that is not a colour is dropped to the plain card.
        assertEquals("#3A7BD5", assertNotNull(s.mint.addAccount("Mine", AccountType.CASH, "PHP", card = "#3a7bd5").record).card)
        assertEquals("", assertNotNull(s.mint.addAccount("Bad", AccountType.CASH, "PHP", card = "#12345").record).card)
        assertEquals("", assertNotNull(s.mint.addAccount("Bad2", AccountType.CASH, "PHP", card = "#GGGGGG").record).card)
        assertEquals(0x3A7BD5, app.pokeplan.mint.CardStyle.customRgb("#3A7BD5"))
        assertNull(app.pokeplan.mint.CardStyle.customRgb("blue"))
    }

    @Test
    fun aRecurringIncomeIsRecordedAsIncomeWhenConfirmedAndNeverOnItsOwn() {
        val s = setup()
        val acct = assertNotNull(s.mint.addAccount("Savings", AccountType.BANK, "PHP", 0).record).id
        val due = LocalDate(2026, 11, 7)
        val salary = assertNotNull(s.mint.addBill("Salary", "PHP", Frequency.MONTHLY, due, 2_500_000, "cat-salary", acct, income = true).record)
        assertTrue(salary.income)
        // Nothing is posted by the due day itself.
        assertTrue(s.mint.data.transactions.isEmpty())
        val tx = assertNotNull(s.mint.settleAndRecord(salary.id, due, 2_500_000).record)
        assertEquals(TxType.INCOME, tx.type)
        assertEquals(salary.id, tx.billId)
        assertEquals(2_500_000L, MintRules.balances(s.mint.data).single().postedMinor)
        // A bill still writes an expense, and editing a schedule's text never flips its direction.
        val bill = assertNotNull(s.mint.addBill("Water", "PHP", Frequency.MONTHLY, due, 50_000, accountId = acct).record)
        assertEquals(TxType.EXPENSE, assertNotNull(s.mint.settleAndRecord(bill.id, due, 50_000).record).type)
        assertTrue(assertNotNull(s.mint.updateBill(salary.copy(name = "Pay", income = false)).record).income)
    }

    @Test
    fun anAccountKeepsOneSmallPngAsItsReceiveQrAndOnlyThat() {
        val s = setup()
        val acct = assertNotNull(s.mint.addAccount("GCash", AccountType.EWALLET, "PHP", 168_880).record)
        assertEquals("", acct.receiveQr)
        val png = MintRules.PNG_BASE64_PREFIX + "AAAA"
        assertEquals(png, assertNotNull(s.mint.setReceiveQr(acct.id, png).record).receiveQr)
        // Editing the account keeps the picture, and it changes no balance.
        assertEquals(png, assertNotNull(s.mint.updateAccount(s.mint.data.accounts.single().copy(name = "GCash 2")).record).receiveQr)
        assertEquals(168_880L, MintRules.balances(s.mint.data).single().postedMinor)
        // Not a PNG, too big, text that is not base64, and a missing account are all refused and change nothing.
        assertNull(s.mint.setReceiveQr(acct.id, "R0lGODlhAQABAAAAACw=").record)
        // A JPEG or WebP screenshot is kept as it is.
        assertNotNull(s.mint.setReceiveQr(acct.id, "/9j/4AAQSkZJRgABAQ==").record)
        assertNotNull(s.mint.setReceiveQr(acct.id, "UklGRiQAAABXRUJQ").record)
        assertNotNull(s.mint.setReceiveQr(acct.id, png).record)
        assertNull(s.mint.setReceiveQr(acct.id, MintRules.PNG_BASE64_PREFIX + "A".repeat(MintRules.QR_MAX_CHARS)).record)
        assertNull(s.mint.setReceiveQr(acct.id, MintRules.PNG_BASE64_PREFIX + "<script>").record)
        assertNull(s.mint.setReceiveQr("nobody", png).record)
        assertEquals(png, s.mint.data.accounts.single().receiveQr)
        // Empty removes it.
        assertEquals("", assertNotNull(s.mint.setReceiveQr(acct.id, "").record).receiveQr)
    }

    @Test
    fun accountsCanBeArrangedHiddenFromTotalAndGetTheirOwnApp() {
        val s = setup()
        val a = assertNotNull(s.mint.addAccount("A", AccountType.BANK, "PHP", 100_000, group = "Daily").record)
        val b = assertNotNull(s.mint.addAccount("B", AccountType.EWALLET, "PHP", 50_000, group = "Daily").record)
        val c = assertNotNull(s.mint.addAccount("C", AccountType.BANK, "PHP", 20_000, group = "Savings").record)
        // Arrange: the named accounts swap places; one that is not named keeps its slot.
        assertTrue(s.mint.arrangeAccounts(listOf(c.id, a.id)))
        assertEquals(listOf("C", "B", "A"), s.mint.data.accounts.map { it.name })
        assertFalse(s.mint.arrangeAccounts(listOf(a.id, a.id)))
        assertFalse(s.mint.arrangeAccounts(listOf(a.id, "nobody")))
        assertEquals(listOf("C", "B", "A"), s.mint.data.accounts.map { it.name })
        // Hide from total: the balance stays on the account but net position and available money leave it out.
        assertEquals(170_000L, MintRules.netPosition(s.mint.data)["PHP"])
        val hid = assertNotNull(s.mint.updateAccount(s.mint.data.accounts.single { it.id == c.id }.copy(hideFromTotal = true)).record)
        assertTrue(hid.hideFromTotal)
        assertEquals(150_000L, MintRules.netPosition(s.mint.data)["PHP"])
        assertEquals(150_000L, MintRules.available(s.mint.data, "PHP", LocalDate(2026, 11, 7)).liquidMinor)
        assertEquals(20_000L, MintRules.balances(s.mint.data).single { it.account.id == c.id }.postedMinor)
        // The account's own app: a package name only; anything else is dropped.
        assertEquals("com.example.wallet", assertNotNull(s.mint.updateAccount(s.mint.data.accounts.single { it.id == a.id }.copy(appPackage = " com.example.wallet ")).record).appPackage)
        assertEquals("", assertNotNull(s.mint.updateAccount(s.mint.data.accounts.single { it.id == a.id }.copy(appPackage = "rm -rf /")).record).appPackage)
    }

    @Test
    fun aCreditCardKeepsAStartingBalanceOwedAndALimitThatIsInformationOnly() {
        val s = setup()
        val card = assertNotNull(s.mint.addAccount("Maya Black", AccountType.CREDIT_CARD, "PHP", 12_000, limitMinor = 50_000).record)
        assertEquals(50_000L, card.limitMinor)
        val owed = MintRules.balances(s.mint.data).single().postedMinor
        assertEquals(12_000L, owed)
        assertEquals(38_000L, MintRules.leftToSpend(card, owed))
        assertEquals(0L, MintRules.leftToSpend(card, 60_000)) // over the limit shows nothing left
        // A limit is no asset and no cash: net position is minus what is owed, and a limit is in no total.
        assertEquals(-12_000L, MintRules.netPosition(s.mint.data)["PHP"])
        // Only a credit card has one, never a negative one; an edit keeps it and can clear it.
        assertNull(s.mint.addAccount("Wallet", AccountType.EWALLET, "PHP", limitMinor = 100).record)
        assertNull(s.mint.addAccount("Bad", AccountType.CREDIT_CARD, "PHP", limitMinor = -5).record)
        assertEquals(20_000L, assertNotNull(s.mint.updateAccount(card.copy(limitMinor = 20_000)).record).limitMinor)
        assertEquals(0L, assertNotNull(s.mint.updateAccount(card.copy(limitMinor = 0)).record).limitMinor)
        assertNull(MintRules.leftToSpend(s.mint.data.accounts.single(), 1))
    }

    @Test
    fun aCreditCardKeepsStatementAndDueDaysAndFindsTheNextOne() {
        val s = setup()
        val card = assertNotNull(s.mint.addAccount("Maya Black", AccountType.CREDIT_CARD, "PHP", 12_000, limitMinor = 50_000, statementDay = 15, dueDay = 31).record)
        assertEquals(15, card.statementDay)
        assertEquals(31, card.dueDay)
        assertNull(s.mint.addAccount("X", AccountType.CREDIT_CARD, "PHP", dueDay = 32).record)
        assertNull(s.mint.addAccount("Wallet", AccountType.EWALLET, "PHP", statementDay = 5).record)
        assertEquals(10, assertNotNull(s.mint.updateAccount(card.copy(dueDay = 10)).record).dueDay)
        assertEquals(0, assertNotNull(s.mint.updateAccount(card.copy(statementDay = 0, dueDay = 0)).record).dueDay)
        // The next day on or after today; a day the month lacks falls on its last day; none is null.
        assertEquals(LocalDate(2026, 11, 15), MintRules.nextOnDay(15, LocalDate(2026, 11, 15)))
        assertEquals(LocalDate(2026, 12, 5), MintRules.nextOnDay(5, LocalDate(2026, 11, 15)))
        assertEquals(LocalDate(2026, 11, 30), MintRules.nextOnDay(31, LocalDate(2026, 11, 15)))
        assertEquals(LocalDate(2027, 2, 28), MintRules.nextOnDay(31, LocalDate(2027, 2, 1)))
        assertEquals(LocalDate(2027, 1, 20), MintRules.nextOnDay(20, LocalDate(2026, 12, 31)))
        assertNull(MintRules.nextOnDay(0, LocalDate(2026, 11, 15)))
    }

    @Test
    fun statementsTrackMinimumsAndPostOneGraceAdjustedDailyInterestEstimate() {
        val s = setup()
        val m = s.mint
        val card = assertNotNull(m.addAccount(
            "Visa", AccountType.CREDIT_CARD, "PHP", 100_000, annualAprBasisPoints = 3_650, interestGraceDays = 2,
        ).record)
        assertNull(m.addAccount(
            "Cash", AccountType.CASH, "PHP", annualAprBasisPoints = 3_650,
        ).record, "non-credit accounts cannot have an interest estimate")
        s.clock.advanceDays(8)
        val first = assertNotNull(m.recordCreditStatement(
            card.id, LocalDate(2026, 11, 15), 100_000, 5_000, LocalDate(2026, 11, 25),
        ).record)
        assertNull(first.estimatedInterestTxId, "there is no prior statement period to estimate")

        s.clock.advanceDays(16)
        assertNotNull(m.record(TxType.EXPENSE, 10_000, card.id).record)
        s.clock.advanceDays(14)
        val second = assertNotNull(m.recordCreditStatement(
            card.id, LocalDate(2026, 12, 15), 110_000, 5_500, LocalDate(2026, 12, 25),
        ).record)
        val interest = assertNotNull(m.data.transactions.firstOrNull { it.id == second.estimatedInterestTxId })
        assertEquals(1_950L, interest.amountMinor)
        assertEquals(FeeKind.INTEREST, interest.feeKind)
        assertEquals("Simple daily APR/365 estimate; not a lender quote.", interest.note)
        assertEquals(111_950L, MintRules.balance(m.data, card).postedMinor)
        assertNotNull(m.recordCreditStatement(card.id, LocalDate(2026, 12, 15), 110_000, 5_500, LocalDate(2026, 12, 25)).refused)
        assertNotNull(m.recordCreditStatement(card.id, LocalDate(2026, 12, 20), 110_000, 5_500, LocalDate(2026, 12, 25)).refused)
    }

    @Test
    fun aPayLaterPurchaseWithInstallmentsFlowsThroughSpendingCashFlowAndNetWorthWithoutDoubleCounting() {
        val s = setup()
        val m = s.mint
        val wallet = assertNotNull(m.addAccount("GCash", AccountType.EWALLET, "PHP", 100_000).record).id
        val later = assertNotNull(m.addAccount("SPayLater", AccountType.PAY_LATER, "PHP", 0, limitMinor = 300_000, statementDay = 20, dueDay = 5).record)
        val nov = LocalDate(2026, 11, 1)
        val dec = LocalDate(2026, 12, 1)
        fun net() = MintRules.netPosition(m.data)["PHP"]!!
        fun owed() = MintRules.balances(m.data).single { it.account.id == later.id }.postedMinor
        fun spent() = MintRules.spent(m.data, nov, dec, "PHP")
        fun cash() = Installments.cashFlow(m.data, nov, dec, "PHP")

        // The purchase: one expense at the full price; the liability rises; no cash moves.
        val plan = assertNotNull(m.addPlanPurchase(later.id, "Headphones", 120_000, null, 3, PlanEvery.MONTH, LocalDate(2026, 12, 1), FeeRule.RatePerPeriod(200)).record)
        assertEquals(listOf(40_000L, 40_000L, 40_000L), plan.installments.map { it.principalMinor })
        assertEquals(listOf(2_400L, 1_600L, 800L), plan.installments.map { it.feeMinor })
        assertEquals(120_000L, owed())
        assertEquals(120_000L, spent())
        assertEquals(0L, cash().outflowMinor)
        assertEquals(-20_000L, net())
        assertEquals(180_000L, MintRules.leftToSpend(m.data.accounts.single { it.id == later.id }, owed()))
        assertEquals(1, m.data.transactions.count { it.type == TxType.EXPENSE })
        // What is coming due reduces available money like a bill.
        val avail = MintRules.available(m.data, "PHP", m.today, 30)
        assertEquals(42_400L, avail.installmentsMinor)
        assertEquals(100_000L - 42_400L, avail.availableMinor)

        // Early payment of the first installment from the wallet: a transfer, plus its fee recorded; no second expense.
        val p1 = assertNotNull(m.payPlan(plan.id, 42_400, wallet).record)
        assertEquals(TxType.TRANSFER, p1.type)
        assertEquals(wallet, p1.accountId)
        assertEquals(later.id, p1.toAccountId)
        assertEquals(1, m.data.transactions.count { it.type == TxType.EXPENSE })
        assertEquals(2_400L, m.data.transactions.single { it.type == TxType.FEE }.amountMinor)
        assertEquals(80_000L, owed())
        assertEquals(122_400L, spent())
        assertEquals(42_400L, cash().outflowMinor)
        assertEquals(-22_400L, net())
        assertEquals(InstallmentState.PAID, m.planView(plan.id)!!.installments[0].state)

        // A partial payment lands on the next installment; its fee is recorded and the rest stays due.
        assertNotNull(m.payPlan(plan.id, 10_000, wallet).record)
        val v = m.planView(plan.id)!!
        assertEquals(InstallmentState.PARTIAL, v.installments[1].state)
        assertEquals(31_600L, v.installments[1].remainingMinor)
        assertEquals(71_600L, owed())
        assertEquals(-24_000L, net())

        // More than is left is refused; so is paying from a credit account.
        assertNull(m.payPlan(plan.id, 1_000_000, wallet).record)
        assertNull(m.payPlan(plan.id, 100, later.id).record)

        // A late fee is owed with its installment and counts in spending as a fee, not as the purchase.
        assertNotNull(m.addLateFee(plan.id, 2, 500).record)
        assertEquals(72_100L, owed())
        assertEquals(124_500L, spent())
        assertEquals(500L, m.creditActivity(later.id, nov, dec).lateFeesMinor)

        // A refund cancels the last unpaid installment, lowers the outstanding and lowers spending.
        assertNotNull(m.refundPlan(plan.id, 40_000).record)
        assertEquals(32_100L, owed())
        assertEquals(84_500L, spent())
        val r = m.planView(plan.id)!!
        assertEquals(InstallmentState.CANCELLED, r.installments[2].state)
        assertEquals(32_100L, r.remainingMinor) // the plan and the ledger agree
        assertNull(m.refundPlan(plan.id, 90_000).record)

        // Paying the rest closes the plan and the liability.
        assertNotNull(m.payPlan(plan.id, 32_100, wallet).record)
        assertEquals(0L, owed())
        assertTrue(m.planView(plan.id)!!.finished)
        val act = m.creditActivity(later.id, nov, dec)
        assertEquals(120_000L, act.purchasesMinor)
        assertEquals(40_000L, act.refundsMinor)
        assertEquals(42_400L + 10_000L + 32_100L, act.repaymentsMinor)
        // The purchase record cannot be deleted or voided while its plan exists.
        val purchase = m.data.transactions.single { it.type == TxType.EXPENSE }
        assertFalse(m.deleteTransaction(purchase.id))
        assertFalse(m.setTransactionStatus(purchase.id, TxStatus.VOIDED))
        assertFalse(m.setTransactionStatus(purchase.id, TxStatus.EXCLUDED))
        assertEquals(TxStatus.POSTED, m.data.transactions.single { it.id == purchase.id }.status)
        assertNull(m.updateTransaction(purchase.copy(amountMinor = 1)).record)
        assertNull(m.updateTransaction(purchase.copy(accountId = wallet)).record)
    }

    @Test
    fun payLaterRepaymentsAllocateAcrossPlansAndRemainPossibleAfterClosingTheAccount() {
        val m = setup().mint
        val wallet = assertNotNull(m.addAccount("Wallet", AccountType.EWALLET, "PHP", 100_000).record).id
        val later = assertNotNull(m.addAccount("Pay later", AccountType.PAY_LATER, "PHP").record)
        val first = assertNotNull(m.addExistingPlan(
            later.id, "First", listOf(app.pokeplan.mint.Installment(1, LocalDate(2026, 12, 1), 5_000)),
        ).record)
        val second = assertNotNull(m.addExistingPlan(
            later.id, "Second", listOf(app.pokeplan.mint.Installment(1, LocalDate(2026, 12, 8), 8_000)),
        ).record)

        assertNotNull(m.record(TxType.TRANSFER, 7_000, wallet, later.id).refused)
        assertNotNull(m.updateAccount(later.copy(type = AccountType.LOAN)).refused)
        assertNotNull(m.deleteAccount(later.id))
        m.setAccountStatus(later.id, AccountStatus.CLOSED)

        val payment = assertNotNull(m.payPlans(later.id, 7_000, wallet).record)
        assertEquals(
            listOf(first.id to 5_000L, second.id to 2_000L),
            payment.planAllocations.map { it.planId to it.amountMinor },
        )
        assertEquals(0L, m.planView(first.id)!!.remainingMinor)
        assertEquals(6_000L, m.planView(second.id)!!.remainingMinor)
    }

    @Test
    fun voidingARepaymentReopensTheInstallmentAndAnInstallmentCanBeBilledAndEdited() {
        val s = setup()
        val m = s.mint
        val wallet = assertNotNull(m.addAccount("Wallet", AccountType.EWALLET, "PHP", 100_000).record).id
        val card = assertNotNull(m.addAccount("Visa", AccountType.CREDIT_CARD, "PHP").record).id
        val plan = assertNotNull(m.addPlanPurchase(card, "Phone", 60_000, null, 2, PlanEvery.MONTH, LocalDate(2026, 12, 1), FeeRule.PerInstallment(300), FeeKind.INTEREST).record)
        // Bill it: the fee is recorded as interest, once.
        val fee = assertNotNull(m.billInstallmentFee(plan.id, 1).record)
        assertEquals(FeeKind.INTEREST, fee.feeKind)
        assertNull(m.billInstallmentFee(plan.id, 1).record)
        assertEquals(30_300L, m.planView(plan.id)!!.installments[0].remainingMinor)
        // A recorded fee or a payment locks that installment; an unpaid one can move its day and fee.
        assertFalse(m.updateInstallment(plan.id, 1, LocalDate(2026, 12, 2), 0))
        assertTrue(m.updateInstallment(plan.id, 2, LocalDate(2027, 1, 5), 29_800, 100))
        assertEquals(59_800L, m.data.plans.single().totalMinor)
        assertEquals(59_800L, m.data.transactions.single { it.id == plan.purchaseTxId }.amountMinor)
        val pay = assertNotNull(m.payPlan(plan.id, 30_300, wallet).record)
        assertEquals(InstallmentState.PAID, m.planView(plan.id)!!.installments[0].state)
        m.setTransactionStatus(pay.id, TxStatus.VOIDED)
        assertEquals(InstallmentState.UPCOMING, m.planView(plan.id)!!.installments[0].state)
        // Deleting the plan keeps every record, unlinked.
        val before = m.data.transactions.size
        m.deletePlan(plan.id)
        assertEquals(before, m.data.transactions.size)
        assertTrue(m.data.transactions.all { it.planId == null })
        assertTrue(m.data.plans.isEmpty())
    }

    @Test
    fun anUntouchedPlanCanChangeItsNameAmountAndScheduleUntilARecordLocksIt() {
        val s = setup()
        val m = s.mint
        val card = assertNotNull(m.addAccount("Visa", AccountType.CREDIT_CARD, "PHP").record).id
        val plan = assertNotNull(m.addPlanPurchase(card, "Headphones", 60_000, null, 2, PlanEvery.MONTH, LocalDate(2026, 12, 1)).record)

        assertTrue(m.canEditPlanTerms(plan.id))
        val edited = assertNotNull(m.updatePlanTerms(
            plan.id, "New phone", 90_000, 3, PlanEvery.WEEK, LocalDate(2026, 12, 10),
            FeeRule.PerInstallment(200), FeeKind.FEE,
        ).record)
        assertEquals("New phone", edited.name)
        assertEquals(90_000L, edited.totalMinor)
        assertEquals(3, edited.installments.size)
        assertEquals(PlanEvery.WEEK, edited.every)
        assertEquals(LocalDate(2026, 12, 10), edited.startDay)
        assertEquals(90_000L, m.data.transactions.single { it.id == plan.purchaseTxId }.amountMinor)

        assertNotNull(m.billInstallmentFee(plan.id, 1).record)
        assertFalse(m.canEditPlanTerms(plan.id))
        assertNotNull(m.updatePlanTerms(
            plan.id, "Locked", 80_000, 2, PlanEvery.MONTH, LocalDate(2027, 1, 1),
            FeeRule.None, FeeKind.FEE,
        ).refused)
    }

    @Test
    fun installmentsAlreadyOwedMakeAScheduleWithoutSpendingAndAddToTheStartingBalance() {
        val s = setup()
        val m = s.mint
        val wallet = assertNotNull(m.addAccount("GCash", AccountType.EWALLET, "PHP", 100_000).record).id
        val later = assertNotNull(m.addAccount("SPayLater", AccountType.PAY_LATER, "PHP", 0, limitMinor = 300_000).record).id
        val nov = LocalDate(2026, 11, 1)
        val dec = LocalDate(2026, 12, 1)
        val rows = listOf(
            app.pokeplan.mint.Installment(0, LocalDate(2026, 11, 20), 50_000),
            app.pokeplan.mint.Installment(0, LocalDate(2026, 12, 20), 50_000, 500),
            app.pokeplan.mint.Installment(0, LocalDate(2027, 1, 20), 30_000),
        )
        fun owed() = MintRules.balances(m.data).single { it.account.id == later }.postedMinor
        val plan = assertNotNull(m.addExistingPlan(later, "Old SPayLater", rows).record)
        assertNull(plan.purchaseTxId)
        assertEquals(130_000L, plan.totalMinor)
        assertEquals(listOf(1, 2, 3), plan.installments.map { it.no })
        // Owed rises by what the schedule asks; nothing is spending, no record exists.
        assertEquals(130_000L, owed())
        assertEquals(0L, MintRules.spent(m.data, nov, dec, "PHP"))
        assertTrue(m.data.transactions.isEmpty())
        assertEquals(-130_000L, MintRules.netPosition(m.data)["PHP"]!! - 100_000L)
        assertEquals(130_500L, m.planView(plan.id)!!.remainingMinor) // the second month's fee is still to come
        // Pay the first month from the wallet: a transfer, owed falls by that amount, still no spending.
        assertNotNull(m.payPlan(plan.id, 50_000, wallet).record)
        assertEquals(80_000L, owed())
        assertEquals(0L, MintRules.spent(m.data, nov, dec, "PHP"))
        assertEquals(InstallmentState.PAID, m.planView(plan.id)!!.installments[0].state)
        // The second month's fee is recorded when billed and is the only spending; it is owed on top.
        assertNotNull(m.billInstallmentFee(plan.id, 2).record)
        assertEquals(500L, MintRules.spent(m.data, nov, dec, "PHP"))
        assertEquals(80_500L, owed())
        // Leaving the opening balance alone (the player typed the total there already) adds nothing; a refund and a delete work on it.
        val card = assertNotNull(m.addAccount("Visa", AccountType.CREDIT_CARD, "PHP", 40_000).record).id
        val plan2 = assertNotNull(m.addExistingPlan(card, "Visa plan", listOf(app.pokeplan.mint.Installment(0, LocalDate(2026, 12, 5), 40_000)), addToStartingBalance = false).record)
        assertEquals(40_000L, MintRules.balances(m.data).single { it.account.id == card }.postedMinor)
        assertNotNull(m.refundPlan(plan2.id, 10_000).record)
        assertEquals(30_000L, MintRules.balances(m.data).single { it.account.id == card }.postedMinor)
        assertEquals(30_000L, m.planView(plan2.id)!!.remainingMinor)
        m.deletePlan(plan2.id)
        assertEquals(30_000L, MintRules.balances(m.data).single { it.account.id == card }.postedMinor)
        // Refusals: a wallet, no rows, a zero amount, out of order.
        assertNull(m.addExistingPlan(wallet, "x", rows).record)
        assertNull(m.addExistingPlan(later, "x", emptyList()).record)
        assertNull(m.addExistingPlan(later, "x", listOf(app.pokeplan.mint.Installment(0, LocalDate(2026, 12, 1), 0))).record)
        assertNull(m.addExistingPlan(later, "x", rows.reversed()).record)
    }

    @Test
    fun anEstimatedTwiceAMonthSalaryIsRecordedAtTheRealAmountOnItsScheduledDay() {
        val s = setup()
        val m = s.mint
        val acct = assertNotNull(m.addAccount("Payroll", AccountType.BANK, "PHP", 0).record).id
        val pay = assertNotNull(m.addBill("Salary", "PHP", Frequency.SEMI_MONTHLY, LocalDate(2026, 11, 15), 2_500_000, "cat-salary", acct, income = true, days = listOf(30, 15), workday = true, estimate = true).record)
        assertEquals(emptyList(), pay.days) // 15 and 30 is the default and is not stored
        assertTrue(pay.estimate && pay.workday && pay.income)
        s.clock.advanceDays(7) // Saturday 14 November: the 15th is paid on Friday the 13th, which has passed
        // Nothing posts by itself: the income appears only when it is confirmed, at the real amount (not the estimate).
        assertTrue(m.data.transactions.isEmpty())
        val tx = assertNotNull(m.settleAndRecord(pay.id, LocalDate(2026, 11, 15), 2_431_550, paidDay = LocalDate(2026, 11, 13)).record)
        assertEquals(TxType.INCOME, tx.type)
        assertEquals(2_431_550L, tx.amountMinor)
        assertEquals(LocalDate(2026, 11, 13), tx.day)
        assertEquals(OccurrenceState.PAID, MintRules.occurrence(m.data, pay, LocalDate(2026, 11, 15), m.today).state)
        // Days off are settings: add one, turn one built-in day off; changing a schedule after payments is refused.
        m.setDayOff(LocalDate(2026, 12, 15), true)
        assertEquals(LocalDate(2026, 12, 14), MintRules.payDay(m.data, pay, LocalDate(2026, 12, 15)))
        m.setHolidayCounted(LocalDate(2026, 11, 30), false)
        assertEquals(LocalDate(2026, 11, 30), MintRules.payDay(m.data, pay, LocalDate(2026, 11, 30)))
        m.setBuiltInHolidays(false)
        assertEquals(LocalDate(2026, 12, 30), MintRules.payDay(m.data, pay, LocalDate(2026, 12, 30)))
        assertNull(m.updateBill(pay.copy(days = listOf(10, 25))).record)
        assertNotNull(m.updateBill(pay.copy(estimate = false, workday = false)).record)
    }

    @Test
    fun anUnpaidMonthlyBillAsksForOneReminderLikeABillAndStopsWhenPaid() {
        val s = setup()
        val m = s.mint
        val wallet = assertNotNull(m.addAccount("GCash", AccountType.EWALLET, "PHP", 500_000).record).id
        val later = assertNotNull(m.addAccount("SPayLater", AccountType.PAY_LATER, "PHP").record).id
        val plan = assertNotNull(m.addPlanPurchase(later, "Headphones", 120_000, null, 3, PlanEvery.MONTH, LocalDate(2026, 11, 9)).record)
        val app = s.game.data
        val zone = TimeZone.UTC
        val none = MintReminders.upcoming(m.data, app, s.clock.nowMillis(), zone)
        assertTrue(none.isEmpty(), "reminders are off until the player turns them on")
        m.setBillReminders(true)
        val due = MintReminders.upcoming(m.data, app, s.clock.nowMillis(), zone)
        assertEquals(1, due.size)
        val ref = MintReminders.billRef(later, BillMonth(2026, 11))
        assertEquals(ref, due.single().billId)
        assertEquals(later to BillMonth(2026, 11), MintReminders.parseBillRef(ref))
        assertTrue(MintReminders.stillWanted(m.data, ref, LocalDate(2026, 11, 9), m.today))
        // The words are the same generic ones unless the player allowed names; never an amount.
        assertEquals("A bill is coming up", MintReminders.text(m.data, ref).title)
        m.setBillReminderNames(true)
        assertEquals("SPayLater is coming up", MintReminders.text(m.data, ref).title)
        // Paid: no longer wanted.
        assertNotNull(m.payPlan(plan.id, 40_000, wallet).record)
        assertFalse(MintReminders.stillWanted(m.data, ref, LocalDate(2026, 11, 9), m.today))
        assertFalse(MintReminders.stillWanted(m.data, "plan:nobody:1", LocalDate(2026, 11, 9), m.today))
        assertFalse(MintReminders.stillWanted(m.data, MintReminders.billRef("nobody", BillMonth(2026, 11)), LocalDate(2026, 11, 9), m.today))
    }

    private fun Setup.payLaterWithTwoPurchases(): Triple<String, String, Pair<app.pokeplan.mint.InstallmentPlan, app.pokeplan.mint.InstallmentPlan>> {
        val wallet = assertNotNull(mint.addAccount("GCash", AccountType.EWALLET, "PHP", 500_000).record).id
        val later = assertNotNull(mint.addAccount("SPayLater", AccountType.PAY_LATER, "PHP", statementDay = 5, dueDay = 15).record).id
        // Today is 7 Nov: the purchase is on the 5 Dec statement and its first installment is due on the 15th, with no date typed.
        val a = assertNotNull(mint.addPlanPurchase(later, "Headphones", 30_000, null, 3, PlanEvery.MONTH, null).record)
        val b = assertNotNull(mint.addExistingPlan(
            later, "Existing", listOf(app.pokeplan.mint.Installment(1, LocalDate(2026, 12, 16), 5_000), app.pokeplan.mint.Installment(2, LocalDate(2027, 1, 16), 5_000)),
        ).record)
        return Triple(wallet, later, a to b)
    }

    @Test
    fun everyPurchaseDueInAMonthIsOneBillAndTheFirstDueDayComesFromTheAccount() {
        val s = setup()
        val (_, later, plans) = s.payLaterWithTwoPurchases()
        assertEquals(listOf(LocalDate(2026, 12, 15), LocalDate(2027, 1, 15), LocalDate(2027, 2, 15)), plans.first.installments.map { it.dueDay })
        val bills = s.mint.openBills(later)
        assertEquals(listOf(BillMonth(2026, 12), BillMonth(2027, 1), BillMonth(2027, 2)), bills.map { it.month })
        val december = bills.first()
        assertEquals(15_000L, december.totalMinor)
        assertEquals(LocalDate(2026, 12, 15), december.dueDay)
        assertEquals(listOf("Headphones", "Existing"), december.lines.map { it.planName })
        assertEquals(listOf(10_000L, 5_000L), december.lines.map { it.remainingMinor })
        // The purchase is still inspectable on its own.
        assertEquals(30_000L, s.mint.planView(plans.first.id)!!.remainingMinor)
    }

    @Test
    fun purchasesInOneMonthShareOneReminder() {
        val s = setup()
        val later = assertNotNull(s.mint.addAccount("SPayLater", AccountType.PAY_LATER, "PHP").record).id
        assertNotNull(s.mint.addPlanPurchase(later, "Headphones", 120_000, null, 3, PlanEvery.MONTH, LocalDate(2026, 11, 9)).record)
        assertNotNull(s.mint.addPlanPurchase(later, "Desk lamp", 60_000, null, 3, PlanEvery.MONTH, LocalDate(2026, 11, 10)).record)
        s.mint.setBillReminders(true)
        val reminders = MintReminders.upcoming(s.mint.data, s.game.data, s.clock.nowMillis(), TimeZone.UTC)
        assertEquals(listOf(MintReminders.billRef(later, BillMonth(2026, 11))), reminders.map { it.billId })
        assertEquals(LocalDate(2026, 11, 9), reminders.single().dueDay)
    }

    @Test
    fun payingABillSettlesItsMonthAcrossPurchasesBeforeAnyPurchaseGoesAhead() {
        val s = setup()
        val (wallet, later, plans) = s.payLaterWithTwoPurchases()
        // The old plan-first order gave the headphones' January installment before the other purchase's December one.
        val payment = assertNotNull(s.mint.payPlans(later, 15_000, wallet).record)
        assertEquals(listOf(plans.first.id to 10_000L, plans.second.id to 5_000L), payment.planAllocations.map { it.planId to it.amountMinor })
        assertEquals(listOf(BillMonth(2027, 1), BillMonth(2027, 2)), s.mint.openBills(later).map { it.month })
        assertEquals(InstallmentState.PAID, s.mint.planView(plans.second.id)!!.installments[0].state)
        // Nothing is left of December; paying it again is refused.
        assertNotNull(s.mint.payBill(later, BillMonth(2026, 12), 100, wallet).refused)
    }

    @Test
    fun payingALaterBillFirstFillsThatMonthThenSpillsForwardInDueOrder() {
        val s = setup()
        val (wallet, later, plans) = s.payLaterWithTwoPurchases()
        val january = BillMonth(2027, 1)
        val payment = assertNotNull(s.mint.payBill(later, january, 17_000, wallet).record)
        // January's 15,000 first (the headphones, then the other), then 2,000 forward to December's earliest line.
        assertEquals(
            listOf(Triple(plans.first.id, 2, 10_000L), Triple(plans.second.id, 2, 5_000L), Triple(plans.first.id, 1, 2_000L)),
            payment.planAllocations.map { Triple(it.planId, it.no, it.amountMinor) },
        )
        assertEquals(InstallmentState.PAID, s.mint.planView(plans.first.id)!!.installments[1].state)
        assertEquals(InstallmentState.PARTIAL, s.mint.planView(plans.first.id)!!.installments[0].state)
        assertEquals(15_000L - 2_000L, s.mint.openBills(later).first().remainingMinor)
        assertTrue(s.mint.openBills(later).none { it.month == january })
        // Voiding the payment reopens the bill by itself.
        s.mint.setTransactionStatus(payment.id, TxStatus.VOIDED)
        assertEquals(15_000L, s.mint.openBills(later).first { it.month == january }.remainingMinor)
    }

    @Test
    fun alignmentMovesOnlyUnpaidMonthlyInstallmentsOntoTheAccountsDueDay() {
        val s = setup()
        val (wallet, later, plans) = s.payLaterWithTwoPurchases()
        assertEquals(2, s.mint.alignInstallmentsToDueDay(later))
        assertEquals(listOf(LocalDate(2026, 12, 15), LocalDate(2027, 1, 15)), s.mint.planView(plans.second.id)!!.installments.map { it.dueDay })
        assertEquals(0, s.mint.alignInstallmentsToDueDay(later))
        assertEquals(15_000L, s.mint.openBills(later).first().totalMinor)
        assertNotNull(wallet)
    }

    @Test
    fun theOverviewBreakdownAddsUpToNetPositionAndAvailableMoney() {
        val s = setup()
        val m = s.mint
        val wallet = assertNotNull(m.addAccount("GCash", AccountType.EWALLET, "PHP", 100_000, group = "Daily").record).id
        val saving = assertNotNull(m.addAccount("Savings", AccountType.BANK, "PHP", 50_000).record)
        val later = assertNotNull(m.addAccount("SPayLater", AccountType.PAY_LATER, "PHP", 0, limitMinor = 300_000).record).id
        assertNotNull(m.updateAccount(saving.copy(hideFromTotal = true)))
        assertNotNull(m.addPlanPurchase(later, "Headphones", 120_000, null, 3, PlanEvery.MONTH, LocalDate(2026, 11, 20)).record)
        val d = MintRules.breakdown(m.data, "PHP", m.today)
        assertEquals(listOf("GCash", "Savings"), d.assets.map { it.name })
        assertEquals(listOf(true, false), d.assets.map { it.counted })
        assertEquals(100_000L, d.assetsMinor) // the hidden savings are left out
        assertEquals(120_000L, d.liabilitiesMinor)
        assertEquals(MintRules.netPosition(m.data)["PHP"], d.netMinor)
        assertEquals(-20_000L, d.netMinor)
        assertEquals(MintRules.available(m.data, "PHP", m.today).availableMinor, d.availableMinor)
        assertEquals(40_000L, d.installmentsMinor)
        assertEquals(100_000L - 40_000L, d.availableMinor)
        assertEquals(1, d.creditLines.size)
        assertEquals(180_000L, d.creditLines.single().leftMinor)
        assertEquals(wallet, d.assets.first().accountId)
    }
}
