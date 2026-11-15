package app.pokeplan.app.mint

import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.reward.MintEvent
import app.pokeplan.core.reward.MintKind
import app.pokeplan.core.reward.Reward
import app.pokeplan.mint.AccountStatus
import app.pokeplan.mint.AccountType
import app.pokeplan.mint.BudgetPeriod
import app.pokeplan.mint.Frequency
import app.pokeplan.mint.MintKinds
import app.pokeplan.mint.MintRules
import app.pokeplan.mint.OccurrenceState
import app.pokeplan.mint.PlanStatus
import app.pokeplan.mint.SettleStatus
import app.pokeplan.mint.TxStatus
import app.pokeplan.mint.TxType
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
}
