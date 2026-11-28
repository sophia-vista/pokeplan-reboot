package app.pokeplan.kit

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarsTest {
    @Test fun leapYears() {
        assertTrue(Calendars.isLeap(2028))
        assertTrue(Calendars.isLeap(2000))
        assertFalse(Calendars.isLeap(1900))
        assertFalse(Calendars.isLeap(2026))
    }

    @Test fun monthLengths() {
        assertEquals(29, Calendars.daysInMonth(2028, 2))
        assertEquals(28, Calendars.daysInMonth(2026, 2))
        assertEquals(30, Calendars.daysInMonth(2026, 11))
        assertEquals(31, Calendars.daysInMonth(2026, 12))
    }

    @Test fun monthGapCrossesYears() {
        assertEquals(3, Calendars.monthsBetween(LocalDate(2026, 11, 5), LocalDate(2027, 2, 1)))
        assertEquals(-1, Calendars.monthsBetween(LocalDate(2026, 1, 31), LocalDate(2025, 12, 1)))
    }

    @Test fun clampKeepsTheLastDayOfAShortMonth() {
        assertEquals(LocalDate(2026, 2, 28), Calendars.clampedDate(2026, 2, 31))
        assertEquals(LocalDate(2028, 2, 29), Calendars.clampedDate(2028, 2, 31))
        assertEquals(LocalDate(2026, 3, 31), Calendars.clampedDate(2026, 3, 31))
    }

    @Test fun plusDaysGoesBothWays() {
        assertEquals(LocalDate(2027, 1, 1), Calendars.plusDays(LocalDate(2026, 12, 31), 1))
        assertEquals(LocalDate(2026, 11, 28), Calendars.plusDays(LocalDate(2026, 12, 1), -3))
    }
}
