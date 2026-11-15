package app.pokeplan.app.plan

import app.pokeplan.plan.Category
import app.pokeplan.plan.Task
import app.pokeplan.plan.TaskState
import app.pokeplan.plan.toEpochMillis
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.pokeplan.app.AppData
import app.pokeplan.app.reminders.ActionResult
import app.pokeplan.app.reminders.FireDecision
import app.pokeplan.app.reminders.FocusWindow
import app.pokeplan.app.reminders.PlanReminderAdapter
import app.pokeplan.app.reminders.ReminderActions
import app.pokeplan.app.reminders.ReminderCoordinator
import app.pokeplan.app.reminders.ReminderPolicy

/** The streak reminder and the Sunday recap on the shared scheduler, through Plan's adapter (they used to have alarms of their own). */
class PlanNudgeRemindersTest {
    private val zone = TimeZone.UTC
    private val sunday = LocalDate(2026, 10, 25)
    private val monday = LocalDate(2026, 10, 26)
    private fun at(d: LocalDate, h: Int, m: Int = 0) = LocalDateTime(d.year, d.monthNumber, d.day, h, m).toEpochMillis(zone)

    private fun after(d: LocalDate, n: Int) = LocalDate.fromEpochDays(d.toEpochDays() + n)

    private fun streaking(today: LocalDate, counted: Boolean = false) =
        AppData(activeDays = buildSet {
            for (i in 1..3) add(LocalDate.fromEpochDays(today.toEpochDays() - i))
            if (counted) add(today)
        })

    private class Box(var data: AppData, var now: Long)

    private fun adapter(box: Box, withClock: Boolean = true) =
        PlanReminderAdapter({ box.data }, if (withClock) ({ box.now }) else null, zone) { true }

    @Test fun `the nudges are requests like any reminder, one streak check a day and the next recap`() {
        val box = Box(streaking(monday), at(monday, 9))
        val requests = adapter(box).requests(box.now, zone)
        val streak = requests.filter { it.kind == "streak" }
        assertEquals(listOf(at(monday, 20), at(after(monday, 1), 20), at(after(monday, 2), 20)), streak.map { it.atMillis })
        assertEquals("2026-10-26", streak.first().occurrence)
        val recap = requests.single { it.kind == "recap" }
        assertEquals(at(LocalDate(2026, 11, 1), 18), recap.atMillis)
        assertEquals("2026-11-01", recap.occurrence)
        // The scheduler plans only what is still to come, in order, with the shared quiet-hours rule.
        val plan = ReminderPolicy.plan(requests, box.now, box.data.settings.quietHours, zone, null, box.now + 3 * 86_400_000L)
        assertEquals(3, plan.size)
    }

    @Test fun `without a clock only task reminders exist, and the switches take the nudges away`() {
        val box = Box(streaking(monday), at(monday, 9))
        assertTrue(adapter(box, withClock = false).requests(box.now, zone).none { it.kind == "streak" || it.kind == "recap" })
        box.data = box.data.copy(settings = box.data.settings.copy(streakReminder = false, weeklyRecap = false))
        assertTrue(adapter(box).requests(box.now, zone).isEmpty())
    }

    @Test fun `no streak request is made inside quiet hours`() {
        val box = Box(streaking(monday), at(monday, 9))
        box.data = box.data.copy(settings = box.data.settings.copy(streakReminderHour = 23))
        assertTrue(adapter(box).requests(box.now, zone).none { it.kind == "streak" })
    }

    @Test fun `a streak reminder fires only while the streak is at risk and only for its own day`() {
        val box = Box(streaking(monday), at(monday, 20))
        val a = adapter(box)
        val request = a.requests(at(monday, 9), zone).first { it.kind == "streak" }
        assertTrue(a.isStillWanted(request))
        assertEquals("Your 3-day streak is waiting", a.present(request)?.title)
        // Something counted: nothing to remind about, whatever was planned.
        box.data = streaking(monday, counted = true)
        assertFalse(a.isStillWanted(request))
        assertNull(a.present(request).takeIf { a.isStillWanted(request) })
        // A rest day takes it away too.
        box.data = streaking(monday).copy(restDays = setOf(monday))
        assertFalse(a.isStillWanted(request))
        // Held out of quiet hours until the next morning it is a different day: the old request is stale.
        box.data = streaking(monday)
        box.now = at(after(monday, 1), 10)
        assertFalse(a.isStillWanted(request))
    }

    @Test fun `the recap fires on its Sunday for a week with something in it, and an empty week stays silent`() {
        val box = Box(AppData(), at(sunday, 18))
        val a = adapter(box)
        val request = a.requests(at(sunday, 9), zone).single { it.kind == "recap" }
        assertEquals("2026-10-25", request.occurrence)
        assertFalse(a.isStillWanted(request), "an empty week is not scolded")
        box.data = AppData(tasks = listOf(Task("a", "a", category = Category.HOME, state = TaskState.DONE, createdAtMillis = 0, completedAtMillis = at(LocalDate(2026, 10, 22), 10))))
        assertTrue(a.isStillWanted(request))
        assertEquals("Your week in PokéLife", a.present(request)?.title)
        box.now = at(monday, 9)
        assertFalse(a.isStillWanted(request), "a recap that is a day late is dropped")
    }

    @Test fun `a focus session holds a nudge to its end, nothing repeats and no action pays`() {
        val box = Box(streaking(monday), at(monday, 20))
        val a = adapter(box)
        val coordinator = ReminderCoordinator(listOf(a))
        val request = a.requests(at(monday, 9), zone).first { it.kind == "streak" }
        val focus = FocusWindow(at(monday, 19, 40), at(monday, 20, 30))
        val decision = coordinator.onFire(request, box.now, zone, box.data.settings.quietHours, focus)
        assertIs<FireDecision.Defer>(decision)
        assertEquals(at(monday, 20, 30), decision.untilMillis)
        assertIs<FireDecision.Show>(coordinator.onFire(request, box.now, zone, box.data.settings.quietHours, null))
        // An unanswered nudge never repeats, and ticking "Done" on one is not an action.
        assertNull(a.followUp(request, box.now))
        assertEquals(ActionResult.NOT_ALLOWED, coordinator.router.handle(request, ReminderActions.DONE))
        assertNotNull(coordinator)
    }
}
