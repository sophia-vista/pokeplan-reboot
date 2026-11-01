package app.pokeplan.ui.gear

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorldClockTest {
    private val noon = Instant.parse("2026-11-01T12:00:00Z")

    @Test fun readingsAndOffsets() {
        val manila = WorldClock.reading("Asia/Manila", noon)!!
        assertEquals(20, manila.time.hour); assertEquals(480, manila.offsetMinutes); assertEquals("UTC+8", WorldClock.offsetText(480))
        assertEquals("UTC-3:30", WorldClock.offsetText(-210)); assertEquals("UTC", WorldClock.offsetText(0)); assertEquals("UTC+5:30", WorldClock.offsetText(330))
        assertNull(WorldClock.reading("Mars/Olympus", noon), "an unknown zone is skipped")
        assertTrue(WorldClock.places.all { WorldClock.zone(it.id) != null }, "every bundled place is a real zone")
    }

    @Test fun conversionAcrossTheDateLineAndDaylightSaving() {
        val t = LocalDateTime(2026, 11, 1, 22, 0)
        val tokyo = WorldClock.convert(t, "America/New_York", "Asia/Tokyo")!!
        assertEquals(LocalDate(2026, 11, 2), tokyo.date); assertEquals(12, tokyo.hour, "22:00 in New York (UTC-5 after the clocks go back) is 12:00 next day in Tokyo (UTC+9)")
        assertEquals(LocalDateTime(2026, 7, 1, 17, 0), WorldClock.convert(LocalDateTime(2026, 7, 1, 12, 0), "America/New_York", "Europe/London"), "in summer New York is UTC-4 and London UTC+1")
        assertNull(WorldClock.convert(t, "Nope/Zone", "UTC"))
    }

    @Test fun textAndDateSums() {
        assertEquals("09:05", WorldClock.clockText(LocalDateTime(2026, 1, 1, 9, 5), false)); assertEquals("9:05 AM", WorldClock.clockText(LocalDateTime(2026, 1, 1, 9, 5), true))
        assertEquals("12:00 AM", WorldClock.clockText(LocalDateTime(2026, 1, 1, 0, 0), true)); assertEquals("12:30 PM", WorldClock.clockText(LocalDateTime(2026, 1, 1, 12, 30), true))
        val d = LocalDate(2026, 11, 1)
        assertEquals("tomorrow", WorldClock.dayWord(LocalDate(2026, 11, 2), d)); assertEquals("yesterday", WorldClock.dayWord(LocalDate(2026, 10, 31), d)); assertEquals("in 3 days", WorldClock.dayWord(LocalDate(2026, 11, 4), d))
        assertEquals(365, WorldClock.daysBetween(LocalDate(2026, 1, 1), LocalDate(2027, 1, 1))); assertEquals(LocalDate(2026, 12, 6), WorldClock.addDays(d, 35)); assertEquals(305, WorldClock.dayOfYear(d))
        assertNotNull(WorldClock.startOf(d, "Asia/Manila"))
    }
}
