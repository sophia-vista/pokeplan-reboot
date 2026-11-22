package app.pokeplan.heal

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Heal's readings as modules: a tile per kind, a line per day, ranges for the day (owner, 21 November 2026). */
class ReadingGroupsTest {
    private fun v(id: String, type: VitalType, value: Double, day: String, at: Long = 1) = VitalReading(id, type, value, at, LocalDate.parse(day))
    private val data = HealData(
        steps = listOf(StepsRecord("s1", LocalDate.parse("2026-11-20"), 6842, 1)),
        vitals = listOf(
            v("a", VitalType.HEART_RATE_LOW, 58.0, "2026-11-20"), v("b", VitalType.HEART_RATE_HIGH, 112.0, "2026-11-20"), v("c", VitalType.RESTING_HEART_RATE, 54.0, "2026-11-20"),
            v("d", VitalType.HEART_RATE, 70.0, "2026-11-19"),
            v("e", VitalType.SKIN_TEMPERATURE_LOW, 33.1, "2026-11-20"), v("f", VitalType.SKIN_TEMPERATURE_HIGH, 35.4, "2026-11-20"),
            v("g", VitalType.SLEEP_LENGTH, 442.0, "2026-11-20"), v("h", VitalType.SLEEP_LENGTH, 42.0, "2026-11-20"), v("i", VitalType.SLEEP_SCORE, 81.0, "2026-11-20"),
        ),
    )

    @Test fun onlyGroupsWithDataGetATile() {
        assertEquals(listOf(ReadingGroup.STEPS, ReadingGroup.HEART, ReadingGroup.SKIN_TEMPERATURE, ReadingGroup.SLEEP), ReadingGroups.withData(data))
        assertTrue(ReadingGroups.withData(HealData()).isEmpty())
    }

    @Test fun heartRateIsARangeForTheDayWithRestingBesideIt() {
        val days = ReadingGroups.daily(data, ReadingGroup.HEART)
        assertEquals(listOf("2026-11-20", "2026-11-19"), days.map { it.day.toString() })
        assertEquals("58–112 bpm, resting 54 bpm", days[0].line)
        assertEquals("70 bpm", days[1].line, "a reading saved before ranges still shows, as a one-value range")
    }

    @Test fun otherRangesAndSleepLinesAreWordedPlainly() {
        assertEquals("33.1–35.4 °C", ReadingGroups.latest(data, ReadingGroup.SKIN_TEMPERATURE)!!.line)
        assertEquals("7 h 22 min, 42 min, score 81", ReadingGroups.latest(data, ReadingGroup.SLEEP)!!.line)
        assertEquals("6,842 steps", ReadingGroups.latest(data, ReadingGroup.STEPS)!!.line)
        assertEquals(null, ReadingGroups.latest(data, ReadingGroup.SPO2))
    }
}
