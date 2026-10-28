package app.pokeplan.app

import app.pokeplan.care.CareData
import app.pokeplan.care.CareSettings
import app.pokeplan.care.Routine
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CareRemindersTest {
    private val utc = TimeZone.UTC
    private val monday = LocalDate(2026, 10, 19)
    private fun at(day: LocalDate, h: Int, m: Int = 0) = LocalDateTime(day, LocalTime(h, m)).toInstant(utc).toEpochMilliseconds()
    private val now = at(monday, 9)

    private val water = Routine("w", "Drink water", time = LocalTime(10, 0), createdOn = monday)
    private val stretch = Routine("s", "Stretch", time = LocalTime(8, 0), createdOn = monday)
    private val walk = Routine("k", "Walk", time = LocalTime(18, 0), days = setOf(DayOfWeek.TUESDAY), createdOn = monday)
    private val anyTime = Routine("a", "Tidy up", createdOn = monday)
    private val app = AppData()

    @Test
    fun eachTimedRoutineRemindsAtItsTimeOnItsDays() {
        val care = CareData(routines = listOf(water, stretch, walk, anyTime))
        val r = CareReminders.upcoming(care, app, now, utc)
        // Today's 8:00 has passed; a routine with no time never reminds; the walk is Tuesdays only.
        assertEquals(listOf("w" to monday, "s" to monday.plusDays(1), "w" to monday.plusDays(1), "k" to monday.plusDays(1), "s" to monday.plusDays(2), "w" to monday.plusDays(2)),
            r.map { it.routineId to it.day })
        assertEquals(at(monday, 10), r.first().atMillis)
    }

    @Test
    fun aRoutineDoneTodayOrPutAwayStaysQuiet() {
        val care = CareData(routines = listOf(water.complete(monday), walk.copy(archived = true)))
        val r = CareReminders.upcoming(care, app, now, utc)
        assertTrue(r.none { it.day == monday })
        assertTrue(r.none { it.routineId == "k" })
        assertFalse(CareReminders.stillWanted(care, app, "w", monday))
        assertTrue(CareReminders.stillWanted(care, app, "w", monday.plusDays(1)))
    }

    @Test
    fun nothingWhenCareOrItsRemindersAreOff() {
        val care = CareData(routines = listOf(water))
        assertEquals(emptyList(), CareReminders.upcoming(care.copy(settings = CareSettings(reminders = false)), app, now, utc))
        val careOff = app.copy(settings = app.settings.copy(careModule = false))
        assertEquals(emptyList(), CareReminders.upcoming(care, careOff, now, utc))
        assertFalse(CareReminders.stillWanted(care, careOff, "w", monday))
    }

    @Test
    fun oneInAFocusSessionWaitsForItsEnd() {
        val clock = object : Clock { override fun nowMillis() = at(monday, 9, 50) }
        val game = GameStore(MemoryStorage(), clock, utc, Random(1)).also { it.start("Sophia", 25) }
        game.startFocus(25, deepFocus = false)
        val r = CareReminders.upcoming(CareData(routines = listOf(water)), game.data, clock.nowMillis(), utc)
        assertEquals(at(monday, 10, 15), r.first().atMillis)
    }

    private fun LocalDate.plusDays(n: Int) = LocalDate.fromEpochDays(toEpochDays() + n)
}
