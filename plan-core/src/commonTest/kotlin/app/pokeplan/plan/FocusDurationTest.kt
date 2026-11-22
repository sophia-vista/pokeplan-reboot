package app.pokeplan.plan

import app.pokeplan.core.dex.Egg
import app.pokeplan.core.dex.RarityRates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FocusDurationTest {
    @Test
    fun convertsToMillis() {
        assertEquals(3_723_000, FocusDuration(1, 2, 3).millis)
    }

    @Test
    fun secondsRoundMinutesUp() {
        assertEquals(10, FocusDuration(0, 10, 0).minutesRoundedUp)
        assertEquals(11, FocusDuration(0, 10, 1).minutesRoundedUp)
    }

    @Test
    fun minimumIsFiveFullMinutes() {
        assertFalse(FocusDuration(0, 4, 59).isLongEnough)
        assertTrue(FocusDuration(0, 5, 0).isLongEnough)
    }

    @Test
    fun rejectsOutOfRangeFields() {
        assertFailsWith<IllegalArgumentException> { FocusDuration(100, 0, 0) }
        assertFailsWith<IllegalArgumentException> { FocusDuration(0, 60, 0) }
        assertFailsWith<IllegalArgumentException> { FocusDuration(0, 0, -1) }
    }

    @Test
    fun leftoverSecondsRoundTheEggBandUp() {
        assertEquals(Egg.rates(20, false), Egg.rates(FocusDuration(0, 19, 30).eggMinutes, false))
        assertEquals(RarityRates(60, 30, 9, 1), Egg.rates(FocusDuration(0, 5, 0).eggMinutes, false))
    }

    @Test
    fun tooShortSessionsEarnNoEgg() {
        assertFailsWith<IllegalArgumentException> { FocusDuration(0, 4, 59).eggMinutes }
    }
}
