package app.pokeplan.app.mint

import app.pokeplan.mint.Bill
import app.pokeplan.mint.BillPayment
import app.pokeplan.mint.Frequency
import app.pokeplan.mint.MintData
import app.pokeplan.mint.MintSettings
import app.pokeplan.mint.PlanStatus
import app.pokeplan.mint.SettleStatus
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
import app.pokeplan.app.reminders.ReminderRequest
import app.pokeplan.app.reminders.ReminderRouter
import app.pokeplan.app.reminders.ReminderSnooze

private fun LocalDate.plusDays(n: Int) = LocalDate.fromEpochDays(toEpochDays() + n)

class MintRemindersTest {
    private val utc = TimeZone.UTC
    private val day = LocalDate(2026, 11, 7)
    private fun at(d: LocalDate, h: Int, m: Int = 0) = LocalDateTime(d, LocalTime(h, m)).toInstant(utc).toEpochMilliseconds()
    private val now = at(day, 8)
    private val app = AppData()
    private val water = Bill("b1", "Water", "PHP", Frequency.MONTHLY, day.plusDays(2), 123_456)
    private fun mint(on: Boolean = true, days: Int = 1, hour: Int = 9, names: Boolean = false, vararg bills: Bill, payments: List<BillPayment> = emptyList()) =
        MintData(bills = bills.toList(), billPayments = payments, settings = MintSettings(billReminders = on, billReminderDays = days, billReminderHour = hour, billReminderNames = names))

    @Test
    fun offUntilTheUserTurnsThemOn() {
        assertTrue(MintReminders.upcoming(mint(false, bills = arrayOf(water)), app, now, utc).isEmpty())
        assertTrue(MintReminders.upcoming(MintData(bills = listOf(water)), app, now, utc).isEmpty(), "the default is off")
        assertTrue(MintReminderAdapter({ MintData(bills = listOf(water)) }, { app }, { day }).requests(now, utc).isEmpty())
    }

    @Test
    fun oneReminderPerOccurrenceAtTheChosenLeadAndHour() {
        val r = MintReminders.upcoming(mint(days = 1, hour = 9, bills = arrayOf(water)), app, now, utc)
        assertEquals(listOf(at(day.plusDays(1), 9)), r.map { it.atMillis }, "one day before the due day, at 9")
        assertEquals(listOf(day.plusDays(2)), r.map { it.dueDay })
        assertEquals(listOf(at(day.plusDays(2), 9)), MintReminders.upcoming(mint(days = 0, bills = arrayOf(water)), app, now, utc).map { it.atMillis })
        assertTrue(MintReminders.upcoming(mint(days = 7, bills = arrayOf(water)), app, now, utc).isEmpty(), "a week before is already past")
    }

    @Test
    fun anOverdueBillGetsNoDailyNagAndASettledOrPausedOneNoReminderAtAll() {
        val overdue = water.copy(firstDue = day.plusDays(-3), frequency = Frequency.ONCE)
        assertTrue(MintReminders.upcoming(mint(bills = arrayOf(overdue)), app, now, utc).isEmpty())
        val paid = BillPayment("p", "b1", day.plusDays(2), SettleStatus.PAID, 123_456, day)
        assertTrue(MintReminders.upcoming(mint(bills = arrayOf(water), payments = listOf(paid)), app, now, utc).isEmpty())
        assertTrue(MintReminders.upcoming(mint(bills = arrayOf(water.copy(status = PlanStatus.PAUSED))), app, now, utc).isEmpty())
        assertTrue(MintReminders.upcoming(mint(bills = arrayOf(water.copy(remind = false))), app, now, utc).isEmpty(), "a bill can switch itself off")
    }

    @Test
    fun theGlobalQuietHoursStillApply() {
        val r = MintReminders.upcoming(mint(hour = 6, bills = arrayOf(water)), app, now, utc)
        assertEquals(listOf(at(day.plusDays(1), 7)), r.map { it.atMillis }, "6:00 is inside the default quiet hours and is held to their end")
    }

    @Test
    fun theRequestAndTheLockScreenCarryNoNameOrAmountAndTheNameNeedsTheUsersLeave() {
        val data = mint(bills = arrayOf(water))
        val adapter = MintReminderAdapter({ data }, { app }, { day })
        val request = adapter.requests(now, utc).single()
        assertEquals(ReminderModule.MINT, request.module)
        assertEquals("b1", request.sourceRef)
        for (secret in listOf("Water", "123456", "PHP")) assertFalse(secret in request.toString() + request.id, "the request holds $secret")
        assertEquals(NudgeText("A bill is coming up", ""), adapter.present(request))
        val named = MintReminderAdapter({ mint(names = true, bills = arrayOf(water)) }, { app }, { day })
        assertEquals("Water is coming up", named.present(request)?.title)
        assertFalse(MintReminders.PUBLIC_TEXT.contains("Water"))
        assertFalse(named.present(request)!!.let { it.title + it.body }.contains("123"), "never an amount")
    }

    @Test
    fun aReminderIsDroppedOnceTheBillIsPaidSkippedDeletedOrTheRemindersAreOff() {
        val request = ReminderRequest(ReminderModule.MINT, "b1", MintReminders.KIND, now, occurrence = day.plusDays(2).toString())
        fun wanted(m: MintData) = MintReminderAdapter({ m }, { app }, { day }).isStillWanted(request)
        assertTrue(wanted(mint(bills = arrayOf(water))))
        assertFalse(wanted(mint(bills = arrayOf(water), payments = listOf(BillPayment("p", "b1", day.plusDays(2), SettleStatus.PAID, 1, day)))))
        assertFalse(wanted(mint(bills = arrayOf(water), payments = listOf(BillPayment("p", "b1", day.plusDays(2), SettleStatus.SKIPPED, 0, day)))))
        assertTrue(wanted(mint(bills = arrayOf(water), payments = listOf(BillPayment("p", "b1", day.plusDays(2), SettleStatus.PARTIAL, 1, day)))), "part paid still owes")
        assertFalse(wanted(mint(bills = emptyArray())))
        assertFalse(wanted(mint(false, bills = arrayOf(water))))
        assertFalse(MintReminderAdapter({ mint(bills = arrayOf(water)) }, { app }, { day }).isStillWanted(request.copy(occurrence = "garbage")))
    }

    @Test
    fun aTapNeverMarksABillPaidAndTheOnlySnoozeIsUntilTomorrow() {
        val data = mint(bills = arrayOf(water))
        val router = ReminderRouter(listOf(MintReminderAdapter({ data }, { app }, { day })))
        val request = ReminderRequest(ReminderModule.MINT, "b1", MintReminders.KIND, now, occurrence = day.plusDays(2).toString())
        assertEquals(ActionResult.NOT_ALLOWED, router.handle(request, ReminderActions.DONE), "a reminder is not a payment")
        assertEquals(ActionResult.SNOOZED, router.handle(request, ReminderActions.TOMORROW))
        assertEquals(ActionResult.DISMISSED, router.handle(request, ReminderActions.DISMISS))
        val until = ReminderSnooze.untilMillis(ReminderModule.MINT, ReminderActions.TOMORROW, now, utc)!!
        assertTrue(until > now && until <= now + 36 * 3_600_000L)
        assertNull(ReminderSnooze.untilMillis(ReminderModule.MINT, ReminderActions.SNOOZE_10, now, utc))
        assertEquals(data, data, "the router touched no record")
    }
}
