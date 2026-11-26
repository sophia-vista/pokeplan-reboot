package app.pokeplan.app.mint

import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.mint.AccountType
import app.pokeplan.mint.TxSource
import app.pokeplan.mint.TxStatus
import app.pokeplan.mint.TxType
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Quick balance checks against statement reconciliation, with a reason and a history (MINT.md 5.8). */
class MintReconciliationTest {
    private class FakeClock(var millis: Long) : Clock {
        override fun nowMillis() = millis
        fun advanceDays(n: Int) { millis += n * 86_400_000L }
    }

    private val utc = TimeZone.UTC
    private val start = LocalDateTime(2026, 11, 25, 18, 0).toInstant(utc).toEpochMilliseconds()

    private fun store(): Pair<MintStore, FakeClock> {
        val clock = FakeClock(start)
        val game = GameStore(MemoryStorage(), clock, utc, Random(7)).also { it.start("Sophia", 25) }
        return MintStore(MemoryStorage(), clock, game, utc, rng = Random(3)) to clock
    }

    private fun bank(m: MintStore): String = assertNotNull(m.addAccount("BPI", AccountType.BANK, "PHP", 100_000).record).id

    @Test
    fun aQuickCheckComparesWithoutRecordingAnything() {
        val (m, _) = store()
        val a = bank(m)
        val check = assertNotNull(m.balanceCheck(a, 90_000))
        assertEquals(100_000L, check.bookMinor)
        assertEquals(90_000L, check.reportedMinor)
        assertEquals(-10_000L, check.differenceMinor)
        assertTrue(!check.matches)
        assertTrue(m.data.reconciliations.isEmpty(), "a quick check writes nothing")
        assertTrue(m.data.transactions.none { it.type == TxType.ADJUSTMENT })
    }

    @Test
    fun aQuickCheckExplainsWhatInMintCouldAccountForTheDifference() {
        val (m, clock) = store()
        val a = bank(m)
        assertNotNull(m.record(TxType.EXPENSE, 10_000, a).record)
        clock.advanceDays(30)
        assertNotNull(m.record(TxType.EXPENSE, 5_000, a).record, "a record from this week")
        assertNotNull(m.record(TxType.EXPENSE, 1_000, a, status = TxStatus.PENDING).record)
        val check = assertNotNull(m.balanceCheck(a, 100_000))
        assertEquals(85_000L, check.bookMinor, "only posted records are in the balance")
        assertTrue(check.causes.any { it.lowercase().contains("pending") }, check.causes.toString())
        assertTrue(check.causes.any { it.lowercase().contains("last week") }, check.causes.toString())
    }

    @Test
    fun anAdjustmentNeedsAReasonAndKeepsItInTheAuditTrail() {
        val (m, _) = store()
        val a = bank(m)
        val refused = m.reconcile(a, 90_000, acceptAdjustment = true)
        assertNull(refused.record)
        assertEquals("Say what the difference is for, in a few words.", refused.refused)
        assertTrue(m.data.reconciliations.isEmpty())

        val out = m.reconcile(a, 90_000, acceptAdjustment = true, reason = "  ATM withdrawal not recorded  ")
        val record = assertNotNull(out.record)
        assertEquals("ATM withdrawal not recorded", record.reason)
        val adjustment = assertNotNull(m.data.transactions.singleOrNull { it.type == TxType.ADJUSTMENT })
        assertEquals("ATM withdrawal not recorded", adjustment.note)
        assertEquals(TxSource.ADJUSTMENT, adjustment.source)
        assertEquals(record.adjustmentTxId, adjustment.id)
        assertEquals(90_000L, assertNotNull(m.balanceCheck(a, 90_000)).bookMinor, "the adjustment is in the books now")
    }

    @Test
    fun aMatchingStatementIsRecordedWithoutAnAdjustmentOrAReason() {
        val (m, _) = store()
        val a = bank(m)
        val record = assertNotNull(m.reconcile(a, 100_000).record)
        assertEquals(100_000L, record.statementMinor)
        assertEquals(100_000L, record.bookMinor)
        assertNull(record.adjustmentTxId)
        assertEquals("", record.reason)
        assertTrue(m.data.transactions.isEmpty(), "nothing to adjust")
    }

    @Test
    fun theHistoryKeepsEveryCheckNewestFirstAndCanBePerAccount() {
        val (m, clock) = store()
        val a = bank(m)
        val other = assertNotNull(m.addAccount("Cash", AccountType.CASH, "PHP", 50_000).record).id
        val first = assertNotNull(m.reconcile(a, 100_000).record)
        clock.advanceDays(1)
        val second = assertNotNull(m.reconcile(a, 95_000, acceptAdjustment = true, reason = "Fee").record)
        clock.advanceDays(1)
        assertNotNull(m.reconcile(other, 50_000).record)
        val history = m.reconciliationHistory(a)
        assertEquals(listOf(second.id, first.id), history.map { it.id })
        assertEquals(3, m.reconciliationHistory().size)
        assertEquals(2, m.reconciliationHistory(a).count { it.accountId == a })
    }
}
