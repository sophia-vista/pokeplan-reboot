package app.pokeplan.app.plan

import app.pokeplan.plan.Category
import app.pokeplan.plan.FocusRecord
import app.pokeplan.plan.FocusSession
import app.pokeplan.plan.PauseReason
import app.pokeplan.plan.Phase
import app.pokeplan.plan.Task
import app.pokeplan.plan.TaskState
import app.pokeplan.plan.toEpochMillis
import app.pokeplan.core.dex.Region
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.pokeplan.app.AppData
import app.pokeplan.app.Settings

class NudgesTest {
    private val zone = TimeZone.UTC
    private val sunday = LocalDate(2026, 10, 25)
    private val monday = LocalDate(2026, 10, 26)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) = LocalDateTime(date.year, date.monthNumber, date.day, hour, minute).toEpochMillis(zone)

    private fun days(vararg d: LocalDate) = d.toSet()

    private fun streaking(today: LocalDate, counted: Boolean = false) =
        AppData(activeDays = buildSet {
            for (i in 1..3) add(LocalDate.fromEpochDays(today.toEpochDays() - i))
            if (counted) add(today)
        })

    @Test
    fun theStreakIsAtRiskWhenOneIsRunningAndNothingCountedToday() {
        assertTrue(Nudges.streakAtRisk(streaking(monday), at(monday, 20), zone))
    }

    @Test
    fun noReminderOnceSomethingCountedOrWithoutAStreak() {
        assertFalse(Nudges.streakAtRisk(streaking(monday, counted = true), at(monday, 20), zone))
        assertFalse(Nudges.streakAtRisk(AppData(), at(monday, 20), zone), "no streak, nothing to protect")
        assertFalse(Nudges.streakAtRisk(AppData(activeDays = days(LocalDate(2026, 9, 1))), at(monday, 20), zone), "the old streak is gone")
    }

    @Test
    fun noReminderOnARestDayWhenSwitchedOffOrInQuietHours() {
        assertFalse(Nudges.streakAtRisk(streaking(monday).copy(restDays = days(monday)), at(monday, 20), zone))
        val off = streaking(monday).let { it.copy(settings = it.settings.copy(streakReminder = false)) }
        assertFalse(Nudges.streakAtRisk(off, at(monday, 20), zone))
        assertNull(Nudges.nextStreakCheck(off, at(monday, 12), zone))
        assertFalse(Nudges.streakAtRisk(streaking(monday), at(monday, 23), zone), "22:00 to 07:00 is quiet")
    }

    @Test
    fun theStreakCheckIsAtEightInTheEveningByDefault() {
        val data = AppData()
        assertEquals(at(monday, 20), Nudges.nextStreakCheck(data, at(monday, 9), zone))
        assertEquals(at(LocalDate(2026, 10, 27), 20), Nudges.nextStreakCheck(data, at(monday, 20), zone), "strictly after now")
        val seven = data.copy(settings = data.settings.copy(streakReminderHour = 19))
        assertEquals(at(monday, 19), Nudges.nextStreakCheck(seven, at(monday, 9), zone))
    }

    @Test
    fun theRecapIsOnSundayEvening() {
        val data = AppData()
        assertEquals(at(sunday, 18), Nudges.nextRecap(data, at(sunday, 17, 59), zone))
        assertEquals(at(LocalDate(2026, 11, 1), 18), Nudges.nextRecap(data, at(sunday, 18), zone))
        assertEquals(at(LocalDate(2026, 11, 1), 18), Nudges.nextRecap(data, at(monday, 9), zone))
        assertEquals(DayOfWeek.SUNDAY, LocalDate(2026, 11, 1).dayOfWeek)
        assertNull(Nudges.nextRecap(data.copy(settings = data.settings.copy(weeklyRecap = false)), at(monday, 9), zone))
    }

    @Test
    fun theRecapNamesTheWeeksTasksFocusAndTopCategory() {
        fun done(id: String, category: Category, day: LocalDate) =
            Task(id, id, category = category, state = TaskState.DONE, createdAtMillis = 0, completedAtMillis = at(day, 10))
        val data = AppData(
            tasks = listOf(
                done("a", Category.WORK, LocalDate(2026, 10, 20)),
                done("b", Category.HOME, LocalDate(2026, 10, 22)),
                done("c", Category.HOME, LocalDate(2026, 10, 23)),
                done("old", Category.WORK, LocalDate(2026, 10, 10)),
            ),
            focusHistory = listOf(FocusRecord(at(LocalDate(2026, 10, 21), 9), 25, true), FocusRecord(at(LocalDate(2026, 10, 22), 9), 50, true), FocusRecord(at(LocalDate(2026, 10, 22), 12), 25, false)),
        )
        val recap = assertNotNull(Nudges.weeklyRecap(data, at(sunday, 18), zone))
        assertEquals("3 tasks finished, 1 h 15 min of focus, most in Home.", recap.body)
    }

    @Test
    fun anEmptyWeekGetsNoRecapAndNoScolding() {
        assertNull(Nudges.weeklyRecap(AppData(), at(sunday, 18), zone))
    }

    private fun session(minutes: Long, paused: Long = 0, phase: Phase = Phase.Running) =
        FocusSession("s", startedAtMillis = 1_000_000, durationMillis = minutes * 60_000, deepFocus = false, region = Region.PLAN, leadIdAtStart = null, phase = phase, pausedMillis = paused)

    @Test
    fun longSessionsGetHalfwayAndEndAlerts() {
        val s = session(30)
        val alerts = Nudges.focusAlerts(s, Settings(), 1_000_000)
        assertEquals(listOf(FocusAlert.Kind.HALFWAY, FocusAlert.Kind.END), alerts.map { it.kind })
        assertEquals(1_000_000 + 15 * 60_000L, alerts[0].atMillis)
        assertEquals(1_000_000 + 30 * 60_000L, alerts[1].atMillis)
    }

    @Test
    fun shortSessionsOnlyGetTheEndAlert() {
        assertEquals(listOf(FocusAlert.Kind.END), Nudges.focusAlerts(session(10), Settings(), 1_000_000).map { it.kind })
        assertEquals(listOf(FocusAlert.Kind.HALFWAY, FocusAlert.Kind.END), Nudges.focusAlerts(session(15), Settings(), 1_000_000).map { it.kind })
    }

    @Test
    fun focusAlertsFollowSettingsPausesAndTheEndOfTheSession() {
        val s = session(30)
        assertEquals(listOf(FocusAlert.Kind.END), Nudges.focusAlerts(s, Settings(focusHalfway = false), 0).map { it.kind })
        assertEquals(listOf(FocusAlert.Kind.HALFWAY), Nudges.focusAlerts(s, Settings(focusEnd = false), 0).map { it.kind })
        assertEquals(1_000_000 + 25 * 60_000L, Nudges.focusAlerts(session(30, paused = 10 * 60_000L), Settings(), 0).first().atMillis, "pauses push the halfway alert back")
        assertTrue(Nudges.focusAlerts(session(30, phase = Phase.Paused(PauseReason.CALL, 1_500_000)), Settings(), 0).isEmpty(), "the clock is stopped")
        assertTrue(Nudges.focusAlerts(session(30, phase = Phase.Failed(app.pokeplan.plan.FailReason.LEFT_APP, 2_000_000)), Settings(), 0).isEmpty(), "failed or given up cancels")
        assertTrue(Nudges.focusAlerts(null, Settings(), 0).isEmpty())
        assertEquals(listOf(FocusAlert.Kind.END), Nudges.focusAlerts(s, Settings(), 1_000_000 + 20 * 60_000L).map { it.kind }, "halfway has passed")
    }

    @Test
    fun oldSavesLoadWithTheNewSettingsOn() {
        val old = Json { ignoreUnknownKeys = true }.decodeFromString(Settings.serializer(), """{"reminders":false}""")
        assertFalse(old.reminders)
        assertTrue(old.repeatReminders && old.focusHalfway && old.focusEnd && old.streakReminder && old.weeklyRecap)
        assertEquals(20, old.streakReminderHour)
    }
}
