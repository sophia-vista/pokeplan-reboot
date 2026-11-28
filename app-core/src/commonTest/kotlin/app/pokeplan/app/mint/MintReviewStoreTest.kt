package app.pokeplan.app.mint

import app.pokeplan.app.testing.FakeClock
import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.mint.AccountType
import app.pokeplan.mint.ReviewInbox
import app.pokeplan.mint.TxType
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The review inbox, and what the player puts off (MINT.md 9.1). */
class MintReviewStoreTest {

    private val utc = TimeZone.UTC
    private val start = LocalDateTime(2026, 11, 27, 18, 0).toInstant(utc).toEpochMilliseconds()

    private fun store(): Pair<MintStore, FakeClock> {
        val clock = FakeClock(start)
        val game = GameStore(MemoryStorage(), clock, utc, Random(7)).also { it.start("Sophia", 25) }
        return MintStore(MemoryStorage(), clock, game, utc, rng = Random(3)) to clock
    }

    @Test
    fun anItemCanBePutOffAndComesBackOnItsDay() {
        val (m, clock) = store()
        val a = assertNotNull(m.addAccount("BPI", AccountType.BANK, "PHP", 100_000).record)
        assertNotNull(m.record(TxType.EXPENSE, 2_000, a.id, payee = "Coffee").record)
        val item = m.reviewInbox().single { it.kind == ReviewInbox.Kind.MISSING }
        assertTrue(m.reviewInbox().any { it.id == item.id })

        m.snoozeReview(item.id, 7)
        assertTrue(m.reviewInbox().none { it.id == item.id }, "it is out of sight")
        assertEquals(1, m.reviewSnoozed().size)

        clock.advanceDays(8)
        assertTrue(m.reviewInbox().any { it.id == item.id }, "and back after its day")
        assertTrue(m.reviewSnoozed().isEmpty())
    }

    @Test
    fun anItemCanBeBroughtBackAtOnce() {
        val (m, _) = store()
        val a = assertNotNull(m.addAccount("BPI", AccountType.BANK, "PHP", 100_000).record)
        assertNotNull(m.record(TxType.EXPENSE, 2_000, a.id, payee = "Coffee").record)
        val item = m.reviewInbox().first { it.kind == ReviewInbox.Kind.MISSING }
        m.snoozeReview(item.id, 30)
        m.unsnoozeReview(item.id)
        assertTrue(m.reviewInbox().any { it.id == item.id })
        assertTrue(m.reviewSnoozed().isEmpty())
        assertTrue(m.data.reviewSnoozes.isEmpty())
    }

    @Test
    fun theInboxIsIndependentOfTheWeeklyReviewRecord() {
        val (m, _) = store()
        val a = assertNotNull(m.addAccount("BPI", AccountType.BANK, "PHP", 100_000).record)
        assertNotNull(m.record(TxType.EXPENSE, 2_000, a.id, payee = "Coffee").record)
        assertNotNull(m.finishReview().record)
        assertTrue(m.reviewInbox().isNotEmpty(), "marking the week reviewed does not empty the inbox")
    }
}
