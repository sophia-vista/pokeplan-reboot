package app.pokeplan.app

import app.pokeplan.care.CareData
import app.pokeplan.care.Routine
import app.pokeplan.plan.QuietHours
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderCoordinatorTest {
    private val utc = TimeZone.UTC
    private val mon = LocalDate(2026, 10, 19)
    private fun at(h: Int, m: Int = 0, day: LocalDate = mon) = LocalDateTime(day, LocalTime(h, m)).toInstant(utc).toEpochMilliseconds()
    private val quiet = QuietHours()
    private val water = Routine("w", "Drink water", time = LocalTime(10, 0), createdOn = mon)

    private var care = CareData(routines = listOf(water))
    private val coordinator = ReminderCoordinator(listOf(CareReminderAdapter({ care }, { AppData() }, { _, _ -> true })))

    @Test
    fun heldSnoozeReplacesTheRebuiltRequestAndNeedsItsSourceStillWanting() {
        val base = coordinator.schedule(at(9), utc, quiet, null, emptyList()).first().first
        val snoozed = base.copy(atMillis = at(10, 30))
        val plan = coordinator.schedule(at(10, 5), utc, quiet, null, listOf(snoozed))
        assertEquals(listOf(at(10, 30)), plan.filter { it.first.id == base.id }.map { it.second })
        care = CareData(routines = listOf(water.complete(mon)))
        assertTrue(coordinator.schedule(at(10, 5), utc, quiet, null, listOf(snoozed)).none { it.first.id == base.id }, "done: the snooze goes too")
    }

    @Test
    fun onFireShowsDefersOrDrops() {
        val r = coordinator.schedule(at(9), utc, quiet, null, emptyList()).first().first
        assertIs<FireDecision.Show>(coordinator.onFire(r, at(10), utc, quiet, null))
        val focus = FocusWindow(at(9, 40), at(10, 25))
        assertEquals(FireDecision.Defer(at(10, 25)), coordinator.onFire(r, at(10), utc, quiet, focus))
        assertEquals(FireDecision.Defer(at(7, day = mon.plusDays1())), coordinator.onFire(r, at(23), utc, quiet, null))
        care = CareData(routines = listOf(water.archivedCopy()))
        assertEquals(FireDecision.Drop, coordinator.onFire(r, at(10), utc, quiet, null))
        assertEquals(FireDecision.Drop, coordinator.onFire(r.copy(module = ReminderModule.HEAL), at(10), utc, quiet, null))
    }

    @Test
    fun snoozeLengthsBelongToTheirModule() {
        assertEquals(at(10, 30), ReminderSnooze.untilMillis(ReminderModule.CARE, ReminderActions.SNOOZE_30, at(10), utc))
        assertNull(ReminderSnooze.untilMillis(ReminderModule.CARE, ReminderActions.SNOOZE_10, at(10), utc), "Care offers only its 30 minutes")
        assertEquals(at(10, 10), ReminderSnooze.untilMillis(ReminderModule.PLAN, ReminderActions.SNOOZE_10, at(10), utc))
        assertEquals(at(11), ReminderSnooze.untilMillis(ReminderModule.PLAN, ReminderActions.SNOOZE_60, at(10), utc))
        assertEquals(at(10, 30), ReminderSnooze.untilMillis(ReminderModule.HEAL, ReminderActions.SNOOZE_30, at(10), utc), "Heal offers the same 30 minutes")
        assertNull(ReminderSnooze.untilMillis(ReminderModule.HEAL, ReminderActions.SNOOZE_10, at(10), utc))
        assertNull(ReminderSnooze.untilMillis(ReminderModule.MINT, ReminderActions.SNOOZE, at(10), utc), "a module with no reminders yet offers none")
    }

    private fun LocalDate.plusDays1() = LocalDate.fromEpochDays(toEpochDays() + 1)
    private fun LocalDate.plusDays(n: Int) = LocalDate.fromEpochDays(toEpochDays() + n)
    private fun Routine.archivedCopy() = copy(archived = true)
}

class PlanReminderAdapterTest {
    private val utc = TimeZone.UTC
    private val mon = LocalDate(2026, 10, 19)
    private fun at(h: Int, m: Int = 0) = LocalDateTime(mon, LocalTime(h, m)).toInstant(utc).toEpochMilliseconds()
    private fun task(open: Boolean = true) = app.pokeplan.plan.Task(
        "t1", "Call the bank", planned = mon, plannedTime = LocalTime(14, 0), createdAtMillis = 0,
        reminders = listOf(app.pokeplan.plan.ReminderOffset.AtTime),
        state = if (open) app.pokeplan.plan.TaskState.OPEN else app.pokeplan.plan.TaskState.DONE,
    )
    private var data = AppData(tasks = listOf(task()))
    private val done = mutableListOf<String>()
    private val adapter = PlanReminderAdapter({ data }, complete = { done += it; true })

    @Test
    fun requestsKeepOnlyAnOpaqueReferenceAndTheTaskCompletesOnlyThroughPlan() {
        val r = adapter.requests(at(9), utc).single()
        assertEquals("PLAN|t1|task|${at(14)}", r.id)
        assertTrue("Call the bank" !in r.id, "no title in the stored identity")
        assertEquals("Call the bank", adapter.present(r)?.title)
        assertTrue(adapter.execute(r, ReminderActions.DONE))
        assertEquals(listOf("t1"), done)
        assertTrue(!adapter.execute(r, "other"))
    }

    @Test
    fun anUnansweredReminderRepeatsThreeTimesAtMostAndStopsWhenTheTaskIsDone() {
        var r = adapter.requests(at(9), utc).single()
        val times = mutableListOf<Long>()
        var now = at(14)
        while (true) {
            r = adapter.followUp(r, now) ?: break
            assertTrue(adapter.isStillWanted(r))
            times += r.atMillis; now = r.atMillis
        }
        assertEquals(Nudges.REMINDER_REPEATS, times.size)
        assertEquals(Nudges.REMINDER_REPEAT_MILLIS, times[1] - times[0])
        data = data.copy(tasks = listOf(task(open = false)))
        assertTrue(!adapter.isStillWanted(adapter.requests(at(9), utc).firstOrNull() ?: ReminderRequest(ReminderModule.PLAN, "t1", "task", 1)))
    }

    @Test
    fun switchingRemindersOffCancelsEverything() {
        val r = adapter.requests(at(9), utc).single()
        data = data.copy(settings = data.settings.copy(reminders = false))
        assertTrue(adapter.requests(at(9), utc).isEmpty())
        assertTrue(!adapter.isStillWanted(r))
    }
}
