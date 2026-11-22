package app.pokeplan.app.reminders

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
import kotlin.test.assertTrue
import app.pokeplan.app.AppData
import app.pokeplan.app.care.CareReminders

class ReminderPolicyTest {
    private fun LocalDate.plusDays(n: Int) = LocalDate.fromEpochDays(toEpochDays() + n)
    private val utc = TimeZone.UTC
    private val mon = LocalDate(2026, 10, 19)
    private fun at(day: LocalDate, h: Int, m: Int = 0) = LocalDateTime(day, LocalTime(h, m)).toInstant(utc).toEpochMilliseconds()
    private val quiet = QuietHours(LocalTime(22, 0), LocalTime(7, 0))
    private fun req(ms: Long, ref: String = "a", module: ReminderModule = ReminderModule.PLAN) = ReminderRequest(module, ref, "due", ms)

    @Test
    fun quietHoursAcrossMidnightMoveToTheirEnd() {
        assertEquals(at(mon.plusDays(1), 7), ReminderPolicy.deliverAt(req(at(mon, 23)), quiet, utc, null))
        assertEquals(at(mon, 7), ReminderPolicy.deliverAt(req(at(mon, 3)), quiet, utc, null))
        assertEquals(at(mon, 12), ReminderPolicy.deliverAt(req(at(mon, 12)), quiet, utc, null))
    }

    @Test
    fun focusHoldsADeferrableReminderButNotOneMarkedOtherwise() {
        val focus = FocusWindow(at(mon, 10), at(mon, 10, 25))
        assertEquals(at(mon, 10, 25), ReminderPolicy.deliverAt(req(at(mon, 10, 10)), quiet, utc, focus))
        assertEquals(at(mon, 10, 10), ReminderPolicy.deliverAt(req(at(mon, 10, 10)).copy(deferrable = false), quiet, utc, focus))
    }

    @Test
    fun focusEndingInQuietHoursSettlesAtTheirEndWithoutLooping() {
        val focus = FocusWindow(at(mon, 21, 40), at(mon, 22, 30))
        assertEquals(at(mon.plusDays(1), 7), ReminderPolicy.deliverAt(req(at(mon, 21, 50)), quiet, utc, focus))
    }

    @Test
    fun planDropsDuplicatesPastAndBeyondHorizonAndKeepsTheLatestEdit() {
        val now = at(mon, 9)
        val first = req(at(mon, 11))
        val edited = req(at(mon, 12))
        val other = req(at(mon, 10), "b", ReminderModule.CARE)
        val past = req(at(mon, 8), "c")
        val far = req(at(mon.plusDays(5), 12), "d")
        val plan = ReminderPolicy.plan(listOf(first, edited, other, past, far), now, quiet, utc, null, horizonMillis = at(mon.plusDays(2), 0))
        assertEquals(listOf("CARE|b|due|", "PLAN|a|due|"), plan.map { it.first.id })
        assertEquals(at(mon, 12), plan.last().second)
        assertTrue(plan.none { it.first.sourceRef in setOf("c", "d") })
    }

    @Test
    fun careRemindersNowRespectTheGlobalQuietHours() {
        val late = Routine("l", "Wind down", time = LocalTime(23, 0), createdOn = mon)
        val r = CareReminders.upcoming(CareData(routines = listOf(late)), AppData(), at(mon, 9), utc)
        assertEquals(at(mon.plusDays(1), 7), r.first().atMillis)
    }
}
