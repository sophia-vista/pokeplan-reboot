package app.pokeplan.shared

import app.pokeplan.shared.model.FocusDuration
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
}
