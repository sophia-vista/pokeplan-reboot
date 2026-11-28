package app.pokeplan.app.testing

import app.pokeplan.app.storage.Clock

/** The clock every store test drives by hand; 1_793_000_000_000 is mid-November 2026. */
class FakeClock(var millis: Long = 1_793_000_000_000L) : Clock {
    override fun nowMillis() = millis

    fun advance(ms: Long) { millis += ms }

    fun advanceMinutes(n: Int) { millis += n * 60_000L }

    fun advanceHours(n: Int) { millis += n * 3_600_000L }

    fun advanceDays(n: Int) { millis += n * 86_400_000L }
}
