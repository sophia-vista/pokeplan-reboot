package app.pokeplan.app.mint

import app.pokeplan.mint.BillMonth
import app.pokeplan.mint.Installments
import app.pokeplan.mint.MintData
import app.pokeplan.mint.MintRules
import app.pokeplan.mint.MonthlyBills
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

data class BillReminder(val billId: String, val dueDay: LocalDate, val atMillis: Long)

object MintReminders {
    fun upcoming(mint: MintData, app: AppData, nowMillis: Long, zone: TimeZone): List<BillReminder> {
        val settings = mint.settings
        if (!settings.billReminders) return emptyList()
        val focus = ReminderPolicy.focusWindow(app.focus)
        val today = nowMillis.toLocalDateTime(zone).date
        val lead = settings.billReminderDays.coerceIn(0, MintStore.MAX_REMIND_DAYS)
        val to = today.plus(lead + DAYS_AHEAD, DateTimeUnit.DAY)
        val installments = mint.accounts.filter { Installments.allowedFor(it.type) }.flatMap { account ->
            MonthlyBills.open(mint, account.id, today).filter { it.dueDay >= today && it.dueDay <= to }.map { bill ->
                val day = bill.dueDay.minus(lead, DateTimeUnit.DAY)
                val at = LocalDateTime(day, LocalTime(settings.billReminderHour.coerceIn(0, 23), 0)).toEpochMillis(zone)
                val ref = billRef(account.id, bill.month)
                val request = ReminderRequest(ReminderModule.MINT, ref, KIND, at, occurrence = bill.dueDay.toString())
                BillReminder(ref, bill.dueDay, ReminderPolicy.deliverAt(request, app.settings.quietHours, zone, focus))
            }
        }
        return (installments + mint.bills.filter { it.remind }.flatMap { bill ->
            MintRules.occurrences(mint, bill, today, to, today).filter { it.state != OccurrenceState.PAID && it.state != OccurrenceState.SKIPPED }.map { o ->
                val day = o.payDay.minus(lead, DateTimeUnit.DAY)
                val at = LocalDateTime(day, LocalTime(settings.billReminderHour.coerceIn(0, 23), 0)).toEpochMillis(zone)
                val request = ReminderRequest(ReminderModule.MINT, bill.id, KIND, at, occurrence = o.dueDay.toString())
                BillReminder(bill.id, o.dueDay, ReminderPolicy.deliverAt(request, app.settings.quietHours, zone, focus))
            }
        }).filter { it.atMillis > nowMillis }.sortedBy { it.atMillis }
    }

    fun billRef(accountId: String, month: BillMonth): String = "credit:$accountId:${month.year}-${month.month}"

    fun parseBillRef(ref: String): Pair<String, BillMonth>? {
        if (!ref.startsWith("credit:")) return null
        val rest = ref.removePrefix("credit:")
        val at = rest.lastIndexOf(':')
        if (at <= 0) return null
        val (y, m) = rest.substring(at + 1).split('-').takeIf { it.size == 2 }?.let { it[0].toIntOrNull() to it[1].toIntOrNull() } ?: return null
        return if (y != null && m != null && m in 1..12) rest.substring(0, at) to BillMonth(y, m) else null
    }

    fun stillWanted(mint: MintData, billId: String, due: LocalDate, today: LocalDate): Boolean {
        if (!mint.settings.billReminders) return false
        if (billId.startsWith("plan:")) return false
        parseBillRef(billId)?.let { (accountId, month) ->
            val bill = MonthlyBills.open(mint, accountId, today).firstOrNull { it.month == month } ?: return false
            return bill.dueDay == due
        }
        val bill = mint.bills.firstOrNull { it.id == billId }?.takeIf { it.remind } ?: return false
        val o = MintRules.occurrences(mint, bill, due, due, today).firstOrNull() ?: return false
        return o.state != OccurrenceState.PAID && o.state != OccurrenceState.SKIPPED
    }

    fun text(mint: MintData, billId: String): NudgeText {
        val name = (parseBillRef(billId)?.let { (accountId, _) -> mint.accounts.firstOrNull { it.id == accountId }?.name } ?: mint.bills.firstOrNull { it.id == billId }?.name)
            ?.takeIf { mint.settings.billReminderNames && it.isNotBlank() }
        return NudgeText(if (name != null) "$name is coming up" else "A bill is coming up", "")
    }

    const val PUBLIC_TEXT = "A bill is coming up"
    const val KIND = "bill"
    const val DAYS_AHEAD = 2
}

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

    override fun execute(request: ReminderRequest, action: String): Boolean = false
}
