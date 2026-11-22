package app.pokeplan.app.base

import app.pokeplan.base.Recurrence
import app.pokeplan.base.RepeatKind
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.pokeplan.app.GameStore
import app.pokeplan.app.reminders.ActionResult
import app.pokeplan.app.reminders.ReminderActions
import app.pokeplan.app.reminders.ReminderModule
import app.pokeplan.app.reminders.ReminderRouter
import app.pokeplan.app.reminders.ReminderSnooze
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage

class BaseRemindersTest {
    private class FakeClock(var millis: Long) : Clock {
        override fun nowMillis() = millis
        fun advanceDays(n: Int) { millis += n * 86_400_000L }
        fun advanceHours(n: Int) { millis += n * 3_600_000L }
    }

    private val utc = TimeZone.UTC
    private val saturdayEvening = LocalDateTime(2026, 11, 7, 18, 0).toInstant(utc).toEpochMilliseconds()
    private val today = LocalDate(2026, 11, 7)

    private class Setup(val game: GameStore, val home: BaseStore, val clock: FakeClock)

    private fun setup(): Setup {
        val clock = FakeClock(saturdayEvening)
        val game = GameStore(MemoryStorage(), clock, utc, Random(7)).also { it.start("Sophia", 25) }
        return Setup(game, BaseStore(MemoryStorage(), clock, game, utc, rng = Random(3)), clock)
    }

    private fun Setup.upcoming() = BaseReminders.upcoming(home.data, game.data, clock.nowMillis(), utc)
    private fun at(y: Int, m: Int, d: Int, h: Int = 9) = LocalDateTime(y, m, d, h, 0).toInstant(utc).toEpochMilliseconds()

    @Test fun `nothing asks until Home and the record are both turned on, and the first chore reminder turns Home on`() {
        val s = setup()
        val chore = s.home.addChore("Vacuum", Recurrence(RepeatKind.WEEKLY), LocalDate(2026, 11, 8)).record!!
        assertTrue(s.upcoming().isEmpty())
        assertFalse(s.home.data.settings.reminders)
        s.home.setReminders(true)
        assertTrue(s.upcoming().isEmpty(), "the chore's own reminder is still off")
        s.home.setReminders(false)
        s.home.setChoreRemind(chore.id, true)
        assertTrue(s.home.data.settings.reminders, "the first reminder set turns the switch on")
        val r = s.upcoming().single()
        assertEquals(BaseReminderKind.CHORE, r.kind)
        assertEquals(at(2026, 11, 8), r.atMillis)
        s.home.setChoreRemind(chore.id, false)
        assertTrue(s.upcoming().isEmpty())
    }

    @Test fun `an overdue or finished chore asks nothing and a repeating one asks for its next day`() {
        val s = setup()
        s.home.setReminders(true)
        val overdue = s.home.addChore("Bins", Recurrence(RepeatKind.ONCE), LocalDate(2026, 11, 3)).record!!
        s.home.setChoreRemind(overdue.id, true)
        assertTrue(s.upcoming().isEmpty(), "an overdue chore never nags")
        val weekly = s.home.addChore("Vacuum", Recurrence(RepeatKind.WEEKLY), LocalDate(2026, 11, 7)).record!!
        s.home.setChoreRemind(weekly.id, true)
        assertTrue(s.upcoming().isEmpty())
        s.home.complete(weekly.id)
        s.clock.advanceDays(4)
        assertEquals(at(2026, 11, 14), s.upcoming().single().atMillis)
        s.home.complete(weekly.id)
        assertTrue(s.upcoming().none { it.recordId == weekly.id && it.occurrence == "2026-11-14" })
    }

    @Test fun `maintenance and warranties remind before the day by the lead the user chose`() {
        val s = setup()
        s.home.setReminders(true)
        val sch = s.home.addSchedule("Service the boiler", LocalDate(2026, 11, 10)).record!!
        s.home.setScheduleRemind(sch.id, true)
        s.home.setMaintenanceLeadDays(2)
        assertEquals(at(2026, 11, 8), s.upcoming().single().atMillis)
        val asset = s.home.addAsset("Boiler").record!!
        val w = s.home.addWarranty(asset.id, expiryDay = LocalDate(2026, 12, 7)).record!!
        s.home.setWarrantyRemind(w.id, true)
        s.home.setWarrantyLeadDays(40)
        assertEquals(1, s.upcoming().size)
        s.home.setWarrantyLeadDays(29)
        assertEquals(at(2026, 11, 8), s.upcoming().single { it.kind == BaseReminderKind.WARRANTY }.atMillis)
        assertTrue(BaseReminders.stillWanted(s.home.data, BaseReminderKind.MAINTENANCE, sch.id, "2026-11-10", s.home.today))
        s.home.completeMaintenance(sch.id)
        assertFalse(BaseReminders.stillWanted(s.home.data, BaseReminderKind.MAINTENANCE, sch.id, "2026-11-10", s.home.today))
    }

    @Test fun `a low supply asks once and a quieted or restocked one does not`() {
        val s = setup()
        s.home.setReminders(true)
        val sup = s.home.addSupply("Detergent", unit = "kg", quantityMilli = 1000, lowMilli = 500).record!!
        s.home.setSupplyRemind(sup.id, true)
        assertTrue(s.upcoming().isEmpty(), "not low yet")
        s.home.setQuantity(sup.id, 400)
        val r = s.upcoming().single()
        assertEquals(BaseReminderKind.LOW_STOCK, r.kind)
        assertEquals(at(2026, 11, 8), r.atMillis, "the first 09:00 after it became low (it is evening)")
        s.clock.advanceHours(24)
        assertTrue(s.upcoming().isEmpty())
        s.clock.advanceHours(-24)
        s.home.dismissLow(sup.id)
        assertTrue(s.upcoming().isEmpty())
        assertFalse(BaseReminders.stillWanted(s.home.data, BaseReminderKind.LOW_STOCK, sup.id, "", s.home.today))
    }

    @Test fun `pausing deleting or turning Home off stops a reminder that is already planned`() {
        val s = setup()
        s.home.setReminders(true)
        val chore = s.home.addChore("Vacuum", Recurrence(RepeatKind.WEEKLY), LocalDate(2026, 11, 8)).record!!
        s.home.setChoreRemind(chore.id, true)
        val adapter = BaseReminderAdapter({ s.home.data }, { s.game.data }, { s.home.today }) { id -> s.home.complete(id).record != null }
        val request = adapter.requests(s.clock.nowMillis(), utc).single()
        assertTrue(adapter.isStillWanted(request))
        s.home.setChoreStatus(chore.id, app.pokeplan.base.RecordStatus.PAUSED)
        assertFalse(adapter.isStillWanted(request))
        s.home.setChoreStatus(chore.id, app.pokeplan.base.RecordStatus.ACTIVE)
        assertTrue(adapter.isStillWanted(request))
        s.home.setReminders(false)
        assertFalse(adapter.isStillWanted(request))
        s.home.setReminders(true)
        s.home.deleteChore(chore.id)
        assertFalse(adapter.isStillWanted(request))
        assertNull(adapter.present(request))
    }

    @Test fun `the request and the lock screen carry no household word and names need the user's yes`() {
        val s = setup()
        s.home.setReminders(true)
        val chore = s.home.addChore("Clean the secret room", Recurrence(RepeatKind.WEEKLY), LocalDate(2026, 11, 8)).record!!
        s.home.setChoreRemind(chore.id, true)
        val adapter = BaseReminderAdapter({ s.home.data }, { s.game.data }, { s.home.today }) { true }
        val request = adapter.requests(s.clock.nowMillis(), utc).single()
        assertFalse(request.id.contains("secret"), request.id)
        assertEquals("A chore is due", adapter.present(request)?.title)
        s.home.setReminderNames(true)
        assertEquals("Due: Clean the secret room", adapter.present(request)?.title)
    }

    @Test fun `Done on a chore reminder is the chore's own completion and paid once`() {
        val s = setup()
        s.home.setReminders(true)
        val chore = s.home.addChore("Vacuum", Recurrence(RepeatKind.WEEKLY), LocalDate(2026, 11, 8)).record!!
        s.home.setChoreRemind(chore.id, true)
        s.clock.advanceDays(1); s.clock.advanceHours(-10)
        val adapter = BaseReminderAdapter({ s.home.data }, { s.game.data }, { s.home.today }) { id -> s.home.complete(id).record != null }
        val request = adapter.requests(s.clock.nowMillis(), utc).single()
        val router = ReminderRouter(listOf(adapter))
        assertEquals(ActionResult.DONE, router.handle(request, ReminderActions.DONE))
        assertEquals(1, s.game.data.basePaid.size)
        assertEquals(ActionResult.STALE, router.handle(request, ReminderActions.DONE))
        assertEquals(1, s.game.data.basePaid.size)
        assertEquals(ActionResult.DISMISSED, router.handle(request, ReminderActions.DISMISS))
        val sch = s.home.addSchedule("Boiler", today.let { LocalDate(2026, 11, 9) }).record!!
        s.home.setScheduleRemind(sch.id, true)
        val m = adapter.requests(s.clock.nowMillis(), utc).first { it.kind == "maintenance" }
        assertEquals(ActionResult.NOT_ALLOWED, router.handle(m, ReminderActions.DONE))
        assertNotNull(ReminderSnooze.untilMillis(ReminderModule.BASE, ReminderActions.TOMORROW, s.clock.nowMillis(), utc))
    }

    @Test fun `a reminder stored under the old module name HOME is read as BASE`() {
        assertEquals(ReminderModule.BASE, ReminderModule.parse("HOME"))
        assertEquals(ReminderModule.BASE, ReminderModule.parse("BASE"))
        assertEquals(ReminderModule.MINT, ReminderModule.parse("MINT"))
        assertNull(ReminderModule.parse("NOPE"))
    }
}
