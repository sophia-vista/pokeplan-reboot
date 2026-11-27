package app.pokeplan.app.mint

import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.mint.AccountStatus
import app.pokeplan.mint.AccountType
import app.pokeplan.mint.BudgetPeriod
import app.pokeplan.mint.ReviewInbox
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

/** Budgets that carry over and pace, and the account lifecycle and freshness (MINT.md 7.3, 4.4, 4.6). */
class MintInsightsBudgetsTest {
    private class FakeClock(var millis: Long) : Clock {
        override fun nowMillis() = millis
    }

    private val utc = TimeZone.UTC
    private val start = LocalDateTime(2026, 11, 27, 18, 0).toInstant(utc).toEpochMilliseconds()
    private val today = LocalDate(2026, 11, 27)

    private fun store(): Pair<MintStore, FakeClock> {
        val clock = FakeClock(start)
        val game = GameStore(MemoryStorage(), clock, utc, Random(7)).also { it.start("Sophia", 25) }
        return MintStore(MemoryStorage(), clock, game, utc, rng = Random(3)) to clock
    }

    private fun millisAt(day: LocalDate): Long = day.toEpochDays() * 86_400_000L + 12 * 3_600_000L

    @Test
    fun aRolloverBudgetCarriesThePeriodBeforeIt() {
        val (m, _) = store()
        val a = assertNotNull(m.addAccount("BPI", AccountType.BANK, "PHP", 100_000).record)
        val budget = assertNotNull(m.addBudget(10_000, "PHP", BudgetPeriod.MONTHLY, "cat-food").record)
        assertNotNull(m.record(TxType.EXPENSE, 4_000, a.id, categoryId = "cat-food", atMillis = millisAt(LocalDate(2026, 10, 10))).record)
        assertEquals(0L, app.pokeplan.mint.MintRules.progress(m.data, m.data.budgets.single { it.id == budget.id }, today).carriedMinor, "no rollover, no carry")
        // A budget older than the period it carries from (made before this was kept: no creation day).
        m.restore(m.data.copy(budgets = m.data.budgets.map { it.copy(createdDay = null) }))
        assertTrue(m.setBudgetRollover(budget.id, true))
        assertEquals(6_000L, app.pokeplan.mint.MintRules.progress(m.data, m.data.budgets.single { it.id == budget.id }, today).carriedMinor)
        assertEquals(16_000L, app.pokeplan.mint.MintRules.progress(m.data, m.data.budgets.single { it.id == budget.id }, today).remainingMinor)
    }

    @Test
    fun aNewBudgetCarriesNothingFromBeforeItExisted() {
        val (m, _) = store()
        val budget = assertNotNull(m.addBudget(10_000, "PHP", BudgetPeriod.MONTHLY, "cat-food").record)
        assertTrue(m.setBudgetRollover(budget.id, true))
        assertEquals(0L, app.pokeplan.mint.MintRules.progress(m.data, m.data.budgets.single(), today).carriedMinor, "made today: no full period before it")
        assertEquals(10_000L, app.pokeplan.mint.MintRules.progress(m.data, m.data.budgets.single(), today).remainingMinor)
    }

    @Test
    fun thePaceSaysWhereThePeriodIsHeading() {
        val (m, _) = store()
        val a = assertNotNull(m.addAccount("BPI", AccountType.BANK, "PHP", 100_000).record)
        val budget = assertNotNull(m.addBudget(30_000, "PHP", BudgetPeriod.MONTHLY, "cat-food").record)
        assertNotNull(m.record(TxType.EXPENSE, 10_000, a.id, categoryId = "cat-food", atMillis = millisAt(LocalDate(2026, 11, 10))).record)
        val progress = app.pokeplan.mint.MintRules.progress(m.data, m.data.budgets.single { it.id == budget.id }, today)
        assertEquals(10_000L, progress.spentMinor)
        val pace = app.pokeplan.mint.MintRules.pace(progress, today)
        assertNotNull(pace)
        assertTrue(pace > 10_000L && pace < 13_000L, "spread over the month: $pace")
    }

    @Test
    fun aSuggestedBudgetIsTheUsualMonthlySpending() {
        val (m, _) = store()
        val a = assertNotNull(m.addAccount("BPI", AccountType.BANK, "PHP", 100_000).record)
        assertNotNull(m.record(TxType.EXPENSE, 6_000, a.id, categoryId = "cat-food", atMillis = millisAt(LocalDate(2026, 10, 5))).record)
        assertNotNull(m.record(TxType.EXPENSE, 6_000, a.id, categoryId = "cat-food", atMillis = millisAt(LocalDate(2026, 9, 5))).record)
        val suggestion = m.suggestedBudget("cat-food", "PHP")
        assertTrue(suggestion > 0, "some average is offered: $suggestion")
    }

    @Test
    fun anAccountClosesWithItsDayAndReopens() {
        val (m, _) = store()
        val a = assertNotNull(m.addAccount("BPI", AccountType.BANK, "PHP", 100_000).record)
        assertEquals("A closing date cannot be in the future.", m.closeAccount(a.id, LocalDate(2026, 12, 1)), "a future closing day is refused")
        assertNull(m.closeAccount(a.id, today))
        assertEquals(AccountStatus.CLOSED, m.data.accounts.single().status)
        assertEquals(today, m.data.accounts.single().closedDay)
        assertEquals("This account is already closed.", m.closeAccount(a.id, today))
        assertNull(m.reopenAccount(a.id))
        assertEquals(AccountStatus.ACTIVE, m.data.accounts.single().status)
        assertNull(m.data.accounts.single().closedDay)
    }

    @Test
    fun freshnessSaysWhenItMovedAndWhenItWasChecked() {
        val (m, _) = store()
        val a = assertNotNull(m.addAccount("BPI", AccountType.BANK, "PHP", 100_000).record)
        assertNotNull(m.record(TxType.EXPENSE, 2_000, a.id, atMillis = millisAt(LocalDate(2026, 11, 20))).record)
        val fresh = assertNotNull(m.accountFreshness(a.id))
        assertEquals(LocalDate(2026, 11, 20), fresh.lastActivity)
        assertNull(fresh.lastChecked)
        assertNotNull(m.reconcile(a.id, 98_000).record)
        assertEquals(today, assertNotNull(m.accountFreshness(a.id)).lastChecked)
    }

    @Test
    fun anAccountWithHistorySaysWhyItCannotBeDeleted() {
        val (m, _) = store()
        val a = assertNotNull(m.addAccount("BPI", AccountType.BANK, "PHP", 100_000).record)
        assertNull(m.accountDeletionReason(a.id), "an empty account can go")
        assertNotNull(m.record(TxType.EXPENSE, 2_000, a.id).record)
        assertNotNull(m.accountDeletionReason(a.id))
        assertEquals(m.accountDeletionReason(a.id), m.deleteAccount(a.id), "the delete refuses with the same words")
    }

    @Test
    fun theReviewSaysHowOftenToCheckABalance() {
        val (m, _) = store()
        val a = assertNotNull(m.addAccount("BPI", AccountType.BANK, "PHP", 100_000).record)
        assertNotNull(m.record(TxType.EXPENSE, 2_000, a.id, categoryId = "cat-food", atMillis = millisAt(today.minusDays(10))).record)
        m.setReviewStaleDays(30)
        assertTrue(m.reviewInbox().any { it.kind == ReviewInbox.Kind.BALANCE }, "never checked, so it asks")
        assertNotNull(m.reconcile(a.id, 98_000).record)
        assertTrue(m.reviewInbox().none { it.kind == ReviewInbox.Kind.BALANCE }, "just checked, so it stops asking")
    }

    private fun LocalDate.minusDays(n: Int) = LocalDate.fromEpochDays(toEpochDays() - n)
}
