package app.pokeplan.app

import app.pokeplan.care.CareData
import app.pokeplan.care.Routine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderAdaptersTest {
    private val utc = TimeZone.UTC
    private val mon = LocalDate(2026, 10, 19)
    private val now = LocalDateTime(mon, LocalTime(9, 0)).toInstant(utc).toEpochMilliseconds()
    private val water = Routine("w", "Drink water", time = LocalTime(10, 0), createdOn = mon)

    private var care = CareData(routines = listOf(water))
    private val completed = mutableListOf<Pair<String, LocalDate>>()
    private val adapter = CareReminderAdapter({ care }, { AppData() }, { id, day -> completed += id to day; true })
    private val router = ReminderRouter(listOf(adapter))

    @Test
    fun anActionRunsOnlyThroughTheOwnerAndOnlyWhileTheSourceIsWanted() {
        val r = router.rebuild(now, utc).first()
        assertEquals("CARE|w|routine|$mon", r.id)
        assertEquals(ActionResult.DONE, router.handle(r, ReminderActions.DONE))
        assertEquals(listOf("w" to mon), completed)
        care = CareData(routines = listOf(water.complete(mon)))
        assertEquals(ActionResult.STALE, router.handle(r, ReminderActions.DONE), "done elsewhere: a stale action cannot mutate")
        assertEquals(1, completed.size)
    }

    @Test
    fun snoozeAndDismissNeverCompleteOrPayAndSnoozeKeepsTheId() {
        val r = router.rebuild(now, utc).first()
        assertEquals(ActionResult.SNOOZED, router.handle(r, ReminderActions.SNOOZE))
        assertEquals(ActionResult.DISMISSED, router.handle(r, ReminderActions.DISMISS))
        assertTrue(completed.isEmpty())
        val later = router.snoozed(r, r.atMillis + 30 * 60_000)
        assertEquals(r.id, later.id)
        assertEquals(1, ReminderPolicy.plan(listOf(r, later), now, app.pokeplan.plan.QuietHours(), utc, null).size)
    }

    @Test
    fun anUnknownModuleOrActionIsRefused() {
        val r = router.rebuild(now, utc).first()
        assertEquals(ActionResult.UNKNOWN_MODULE, router.handle(r.copy(module = ReminderModule.HEAL), ReminderActions.DONE))
        assertEquals(ActionResult.NOT_ALLOWED, router.handle(r, "other"))
        assertEquals(null, adapter.present(r.copy(sourceRef = "gone")))
    }
}
