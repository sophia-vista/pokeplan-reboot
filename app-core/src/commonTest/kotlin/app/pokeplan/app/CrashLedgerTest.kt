package app.pokeplan.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashLedgerTest {
    private val now = 1_000_000L

    @Test
    fun threeCrashesWithinAMinuteAreALoopButTwoAreNot() {
        var times = emptyList<Long>()
        times = CrashLedger.record(times, now - 40_000)
        times = CrashLedger.record(times, now - 20_000)
        assertFalse(CrashLedger.inCrashLoop(times, now))
        times = CrashLedger.record(times, now - 5_000)
        assertTrue(CrashLedger.inCrashLoop(times, now))
    }

    @Test
    fun crashesOlderThanAMinuteDoNotCount() {
        val times = listOf(now - 200_000, now - 150_000, now - 100_000, now - 10_000)
        assertFalse(CrashLedger.inCrashLoop(times, now))
        assertEquals(listOf(now - 10_000, now), CrashLedger.record(times, now))
    }

    @Test
    fun theTimesSurviveBeingStoredAsText() {
        val times = listOf(1L, 22L, 333L)
        assertEquals(times, CrashLedger.decode(CrashLedger.encode(times)))
        assertEquals(emptyList(), CrashLedger.decode(null))
        assertEquals(listOf(5L), CrashLedger.decode("5,x,,"))
    }

    @Test
    fun theLogHasNoExceptionMessage() {
        val text = CrashLog.format(now, "0.164.0", "main", IllegalStateException("my secret diary", RuntimeException("and more secrets")))
        assertTrue("IllegalStateException" in text)
        assertTrue("RuntimeException" in text)
        assertFalse("secret" in text)
    }
}
