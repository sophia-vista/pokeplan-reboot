package app.pokeplan.app.heal

import app.pokeplan.heal.DoseStatus
import app.pokeplan.heal.HealData
import app.pokeplan.heal.HealSettings
import app.pokeplan.heal.HydrationEntry
import app.pokeplan.heal.Medication
import app.pokeplan.heal.MedicationDose
import app.pokeplan.heal.MedicationStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.pokeplan.app.AppData
import app.pokeplan.app.plan.NudgeText
import app.pokeplan.app.reminders.ActionResult
import app.pokeplan.app.reminders.ReminderActions
import app.pokeplan.app.reminders.ReminderModule
import app.pokeplan.app.reminders.ReminderRouter
import app.pokeplan.app.reminders.ReminderSnooze

private fun LocalDate.plusDays(n: Int) = LocalDate.fromEpochDays(toEpochDays() + n)

class HealRemindersTest {
    private val utc = TimeZone.UTC
    private val day = LocalDate(2026, 11, 7)
    private fun at(d: LocalDate, h: Int, m: Int = 0) = LocalDateTime(d, LocalTime(h, m)).toInstant(utc).toEpochMilliseconds()
    private val now = at(day, 9)
    private val app = AppData()
    private fun heal(times: List<LocalTime>, target: Int? = null, drinks: List<HydrationEntry> = emptyList()) =
        HealData(hydration = drinks, settings = HealSettings(hydrationReminders = times, hydrationTargetMl = target))

    @Test
    fun noReminderUntilThePlayerAddsATime() {
        assertTrue(HealReminders.upcoming(HealData(), app, now, utc).isEmpty())
        assertTrue(HealReminderAdapter({ HealData() }, { app }, { day }).requests(now, utc).isEmpty())
    }

    @Test
    fun eachChosenTimeRemindsOncePerDayAheadAndPassedOnesAreSkipped() {
        val r = HealReminders.upcoming(heal(listOf(LocalTime(8, 0), LocalTime(12, 0))), app, now, utc)
        assertEquals(listOf(at(day, 12), at(day.plusDays(1), 8), at(day.plusDays(1), 12), at(day.plusDays(2), 8), at(day.plusDays(2), 12)), r.map { it.atMillis })
    }

    @Test
    fun theGlobalQuietHoursStillApply() {
        val r = HealReminders.upcoming(heal(listOf(LocalTime(6, 30))), app, now, utc)
        assertTrue(r.all { it.atMillis == at(it.day, 7) }, "6:30 falls in the default quiet hours and is held to their end")
    }

    @Test
    fun aReminderStaysSilentOnceTheirOwnTargetIsMetToday() {
        val drinks = listOf(HydrationEntry("h", now, day, 2000))
        val met = heal(listOf(LocalTime(12, 0)), target = 1500, drinks = drinks)
        assertEquals(listOf(day.plusDays(1), day.plusDays(2)), HealReminders.upcoming(met, app, now, utc).map { it.day })
        assertFalse(HealReminders.stillWanted(met, LocalTime(12, 0), day, day))
        assertTrue(HealReminders.stillWanted(met, LocalTime(12, 0), day.plusDays(1), day))
        assertTrue(HealReminders.stillWanted(heal(listOf(LocalTime(12, 0)), target = 3000, drinks = drinks), LocalTime(12, 0), day, day))
    }

    @Test
    fun theRequestHoldsOnlyAnOpaqueSlotAndTheWordsAreTheSameForEveryone() {
        val data = heal(listOf(LocalTime(12, 0)), target = 2000)
        val adapter = HealReminderAdapter({ data }, { app }, { day })
        val request = adapter.requests(now, utc).first()
        assertEquals(ReminderModule.HEAL, request.module)
        assertEquals("hyd-1200", request.sourceRef)
        assertTrue(adapter.isStillWanted(request))
        assertEquals(NudgeText("A moment for a drink?", ""), adapter.present(request))
        val removed = HealReminderAdapter({ heal(emptyList()) }, { app }, { day })
        assertFalse(removed.isStillWanted(request))
        assertFalse(adapter.isStillWanted(request.copy(sourceRef = "bogus")))
    }

    @Test
    fun noActionOfThisSourceRecordsOrPaysAnything() {
        val data = heal(listOf(LocalTime(12, 0)))
        val adapter = HealReminderAdapter({ data }, { app }, { day })
        val request = adapter.requests(now, utc).first()
        val router = ReminderRouter(listOf(adapter))
        assertEquals(ActionResult.NOT_ALLOWED, router.handle(request, ReminderActions.DONE), "there is no 'done': a reminder is not a record")
        assertEquals(ActionResult.SNOOZED, router.handle(request, ReminderActions.SNOOZE_30))
        assertEquals(ActionResult.DISMISSED, router.handle(request, ReminderActions.DISMISS))
        assertEquals(now + 30 * 60_000L, ReminderSnooze.untilMillis(ReminderModule.HEAL, ReminderActions.SNOOZE_30, now, utc))
        assertNull(ReminderSnooze.untilMillis(ReminderModule.HEAL, ReminderActions.TOMORROW, now, utc))
    }

    private fun medication(
        id: String = "m",
        times: List<LocalTime> = listOf(LocalTime(8, 0), LocalTime(20, 0)),
        remind: Boolean = true,
        status: MedicationStatus = MedicationStatus.ACTIVE,
        supply: Int? = null,
        refill: Int? = null,
        doses: List<MedicationDose> = emptyList(),
    ) = HealData(
        medications = listOf(Medication(id, "Metformin", times = times, startDay = LocalDate(2026, 11, 1), status = status, remind = remind, supplyLeft = supply, refillAt = refill)),
        doses = doses,
    )

    @Test
    fun aMedicationOnlyRemindsWhenThePlayerTurnedItOnAndKeepsTheirTimes() {
        assertTrue(HealReminders.medicationUpcoming(HealData(), app, now, utc).isEmpty())
        assertTrue(HealReminders.medicationUpcoming(medication(remind = false), app, now, utc).isEmpty())
        assertTrue(HealReminders.medicationUpcoming(medication(status = MedicationStatus.PAUSED), app, now, utc).isEmpty())
        val r = HealReminders.medicationUpcoming(medication(), app, now, utc)
        assertEquals(listOf(at(day, 20), at(day.plusDays(1), 8), at(day.plusDays(1), 20), at(day.plusDays(2), 8), at(day.plusDays(2), 20)), r.map { it.atMillis })
        assertTrue(r.all { !it.lowSupply })
        assertEquals("med-m-2000", r.first().ref)
    }

    @Test
    fun aLoggedDoseStopsItsOwnReminderAndTheQuietHoursStillApply() {
        val data = medication(times = listOf(LocalTime(6, 30), LocalTime(20, 0)), doses = listOf(MedicationDose("d", "m", day, LocalTime(20, 0), DoseStatus.TAKEN, now)))
        val r = HealReminders.medicationUpcoming(data, app, now, utc)
        assertEquals(listOf(at(day.plusDays(1), 7), at(day.plusDays(1), 20), at(day.plusDays(2), 7), at(day.plusDays(2), 20)), r.map { it.atMillis }, "today's 6:30 is held to 7:00, its 20:00 is logged, tomorrow's stay")
        val request = HealReminderAdapter({ data }, { app }, { day }).requests(now, utc).first { it.kind == MedicationReminder.KIND_DOSE }
        assertEquals("med-m-0630", request.sourceRef)
        assertEquals(NudgeText("A medication is due", ""), HealReminderAdapter({ data }, { app }, { day }).present(request), "the words name nothing")
    }

    @Test
    fun aLowSupplyCountRemindsOnceADayUntilItIsRefilled() {
        val low = medication(times = listOf(LocalTime(8, 0), LocalTime(20, 0)), supply = 2, refill = 3)
        val r = HealReminders.medicationUpcoming(low, app, now, utc)
        assertEquals(5, r.count { !it.lowSupply }, "each unlogged scheduled time still reminds")
        assertEquals(3, r.count { it.lowSupply }, "one refill nudge a day, at the last time of it")
        assertTrue(r.filter { it.lowSupply }.all { it.time == LocalTime(20, 0) })
        assertEquals(listOf(at(day, 20), at(day.plusDays(1), 8), at(day.plusDays(1), 20), at(day.plusDays(2), 8), at(day.plusDays(2), 20)), r.filter { !it.lowSupply }.map { it.atMillis })
        val filled = low.copy(medications = low.medications.map { it.copy(supplyLeft = 30) })
        assertTrue(HealReminders.medicationUpcoming(filled, app, now, utc).none { it.lowSupply })
    }

    @Test
    fun theMedicationRequestIsOpaqueAndHasNoActionThatRecordsAnything() {
        val data = medication()
        val adapter = HealReminderAdapter({ data }, { app }, { day })
        val request = adapter.requests(now, utc).first()
        assertEquals(ReminderModule.HEAL, request.module)
        assertTrue(adapter.isStillWanted(request))
        val logged = data.copy(doses = listOf(MedicationDose("d", "m", request.occurrence.let(LocalDate::parse), LocalTime(20, 0), DoseStatus.TAKEN, now)))
        assertFalse(HealReminderAdapter({ logged }, { app }, { day }).isStillWanted(request))
        assertFalse(adapter.isStillWanted(request.copy(sourceRef = "med-m-0000")))
        val router = ReminderRouter(listOf(adapter))
        assertEquals(ActionResult.NOT_ALLOWED, router.handle(request, ReminderActions.DONE))
        assertEquals(ActionResult.SNOOZED, router.handle(request, ReminderActions.SNOOZE_30))
    }
}
