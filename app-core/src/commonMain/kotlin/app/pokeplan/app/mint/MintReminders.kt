package app.pokeplan.app.mint

import app.pokeplan.mint.MintData
import app.pokeplan.mint.MintRules
import app.pokeplan.mint.OccurrenceState
import app.pokeplan.plan.toEpochMillis
import app.pokeplan.plan.toLocalDateTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.minus
import app.pokeplan.app.AppData
import app.pokeplan.app.plan.NudgeText
import app.pokeplan.app.reminders.FocusWindow
import app.pokeplan.app.reminders.ReminderModule
import app.pokeplan.app.reminders.ReminderPolicy
import app.pokeplan.app.reminders.ReminderRequest
import app.pokeplan.app.reminders.ReminderSourceAdapter

/** A bill reminder: the bill, the due day it is for, and when it goes off. */
data class BillReminder(val billId: String, val dueDay: LocalDate, val atMillis: Long)

/**
 * MINT's bill reminders (MINT.md section 12): **off until the user turns them on**, one per bill occurrence at the time the user chose (some days before the due
 * day, or on it), never repeated, and never a daily nag for an overdue bill. A bill can switch itself off, and a bill that is paid, skipped, paused or deleted stops
 * asking. Delivery follows the shared [ReminderPolicy] (the global quiet hours and a focus session). The request holds only the bill's own opaque id and the due day:
 * the words are resolved when it fires, and are the same for everyone unless the user allowed the bill's name, so a lock screen shows no amount, balance or merchant.
 * A reminder opens Mint; it never marks anything paid.
 */
object MintReminders {
    /** The reminders still to come after [nowMillis], earliest first, for the days the scheduler plans over. */
    fun upcoming(mint: MintData, app: AppData, nowMillis: Long, zone: TimeZone): List<BillReminder> {
        val settings = mint.settings
        if (!settings.billReminders) return emptyList()
        val session = app.focus?.takeIf { !it.isFinished }
        val focus = session?.let { FocusWindow(it.startedAtMillis, it.startedAtMillis + it.durationMillis + it.pausedMillis) }
        val today = nowMillis.toLocalDateTime(zone).date
        val lead = settings.billReminderDays.coerceIn(0, MintStore.MAX_REMIND_DAYS)
        // A due day this far ahead can have its reminder inside the days the scheduler looks at.
        val to = today.plus(lead + DAYS_AHEAD, DateTimeUnit.DAY)
        return mint.bills.filter { it.remind }.flatMap { bill ->
            MintRules.occurrences(mint, bill, today, to, today).filter { it.state != OccurrenceState.PAID && it.state != OccurrenceState.SKIPPED }.map { o ->
                val day = o.payDay.minus(lead, DateTimeUnit.DAY)
                val at = LocalDateTime(day, LocalTime(settings.billReminderHour.coerceIn(0, 23), 0)).toEpochMillis(zone)
                val request = ReminderRequest(ReminderModule.MINT, bill.id, KIND, at, occurrence = o.dueDay.toString())
                BillReminder(bill.id, o.dueDay, ReminderPolicy.deliverAt(request, app.settings.quietHours, zone, focus))
            }
        }.filter { it.atMillis > nowMillis }.sortedBy { it.atMillis }
    }

    /** Whether the reminder for [billId]'s occurrence due on [due] should still show: reminders are on, the bill is active and asks, and the occurrence is not settled. */
    fun stillWanted(mint: MintData, billId: String, due: LocalDate, today: LocalDate): Boolean {
        if (!mint.settings.billReminders) return false
        val bill = mint.bills.firstOrNull { it.id == billId }?.takeIf { it.remind } ?: return false
        val o = MintRules.occurrences(mint, bill, due, due, today).firstOrNull() ?: return false
        return o.state != OccurrenceState.PAID && o.state != OccurrenceState.SKIPPED
    }

    /** What a notification says: the same words for every bill, or the bill's own name when the user allowed it. Never an amount. */
    fun text(mint: MintData, billId: String): NudgeText {
        val name = mint.bills.firstOrNull { it.id == billId }?.name?.takeIf { mint.settings.billReminderNames && it.isNotBlank() }
        return NudgeText(if (name != null) "$name is coming up" else "A bill is coming up", "")
    }

    /** The lock-screen text, which is the same for everyone whatever the user allowed. */
    const val PUBLIC_TEXT = "A bill is coming up"
    const val KIND = "bill"
    const val DAYS_AHEAD = 2
}

/**
 * MINT's side of the shared reminders: the bill prompt. It offers "Tomorrow" and a dismiss, never "paid": a reminder is not a payment, and marking an occurrence
 * paid is the user's own act in Mint (MINT.md section 12). The bill is checked against Mint's records before it is shown or acted on.
 */
class MintReminderAdapter(
    private val mint: () -> MintData,
    private val app: () -> AppData,
    private val today: () -> LocalDate,
) : ReminderSourceAdapter {
    override val module = ReminderModule.MINT

    override fun requests(nowMillis: Long, zone: TimeZone): List<ReminderRequest> =
        MintReminders.upcoming(mint(), app(), nowMillis, zone).map { ReminderRequest(module, it.billId, MintReminders.KIND, it.atMillis, occurrence = it.dueDay.toString()) }

    override fun isStillWanted(request: ReminderRequest): Boolean = runCatching {
        MintReminders.stillWanted(mint(), request.sourceRef, LocalDate.parse(request.occurrence), today())
    }.getOrDefault(false)

    override fun present(request: ReminderRequest): NudgeText? = if (isStillWanted(request)) MintReminders.text(mint(), request.sourceRef) else null

    /** Only a snooze is offered and the router handles it without touching a record; "paid" is not an action of this source. */
    override fun execute(request: ReminderRequest, action: String): Boolean = false
}
