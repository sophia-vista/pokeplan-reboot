package app.pokeplan.app.heal

import app.pokeplan.heal.HealData
import app.pokeplan.heal.HealRules
import app.pokeplan.plan.QuietHours
import app.pokeplan.plan.toEpochMillis
import app.pokeplan.plan.toLocalDateTime
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import app.pokeplan.app.AppData
import app.pokeplan.app.plan.NudgeText
import app.pokeplan.app.reminders.FocusWindow
import app.pokeplan.app.reminders.ReminderModule
import app.pokeplan.app.reminders.ReminderPolicy
import app.pokeplan.app.reminders.ReminderRequest
import app.pokeplan.app.reminders.ReminderSourceAdapter

data class HydrationReminder(val time: LocalTime, val day: LocalDate, val atMillis: Long) {
    val ref: String get() = ref(time)

    companion object {
        fun ref(time: LocalTime) = "hyd-" + time.hour.toString().padStart(2, '0') + time.minute.toString().padStart(2, '0')
    }
}

data class HuaweiReadReminder(val day: LocalDate, val atMillis: Long) {
    companion object { const val REF = "hw-read" }
}

object HealReminders {
    const val DAYS_AHEAD = 2

    fun upcoming(heal: HealData, app: AppData, nowMillis: Long, zone: TimeZone): List<HydrationReminder> {
        if (heal.settings.hydrationReminders.isEmpty()) return emptyList()
        val session = app.focus?.takeIf { !it.isFinished }
        val focus = session?.let { FocusWindow(it.startedAtMillis, it.startedAtMillis + it.durationMillis + it.pausedMillis) }
        val today = nowMillis.toLocalDateTime(zone).date
        return (0..DAYS_AHEAD).flatMap { offset ->
            val day = today.plus(DatePeriod(days = offset))
            heal.settings.hydrationReminders.filter { stillWanted(heal, it, day, today) }.map { reminder(it, day, app.settings.quietHours, zone, focus) }
        }.filter { it.atMillis > nowMillis }.sortedBy { it.atMillis }
    }

    private fun reminder(time: LocalTime, day: LocalDate, quiet: QuietHours, zone: TimeZone, focus: FocusWindow?): HydrationReminder {
        val at = LocalDateTime(day, time).toEpochMillis(zone)
        val request = ReminderRequest(ReminderModule.HEAL, HydrationReminder.ref(time), "hydration", at, occurrence = day.toString())
        return HydrationReminder(time, day, ReminderPolicy.deliverAt(request, quiet, zone, focus))
    }

    fun huaweiDueDay(heal: HealData, today: LocalDate, zone: TimeZone): LocalDate? {
        val every = heal.settings.huaweiEveryDays ?: return null
        val last = heal.settings.huaweiLastReadMillis.takeIf { it > 0 }?.toLocalDateTime(zone)?.date ?: return today
        return maxOf(today, last.plus(DatePeriod(days = every)))
    }

    fun huaweiUpcoming(heal: HealData, app: AppData, nowMillis: Long, zone: TimeZone): HuaweiReadReminder? {
        val today = nowMillis.toLocalDateTime(zone).date
        var day = huaweiDueDay(heal, today, zone) ?: return null
        if (LocalDateTime(day, heal.settings.huaweiRemindAt).toEpochMillis(zone) <= nowMillis) day = day.plus(DatePeriod(days = 1))
        val session = app.focus?.takeIf { !it.isFinished }
        val focus = session?.let { FocusWindow(it.startedAtMillis, it.startedAtMillis + it.durationMillis + it.pausedMillis) }
        val at = LocalDateTime(day, heal.settings.huaweiRemindAt).toEpochMillis(zone)
        val request = ReminderRequest(ReminderModule.HEAL, HuaweiReadReminder.REF, "huawei", at, occurrence = day.toString())
        return HuaweiReadReminder(day, ReminderPolicy.deliverAt(request, app.settings.quietHours, zone, focus))
    }

    fun huaweiStillWanted(heal: HealData, day: LocalDate, today: LocalDate, zone: TimeZone): Boolean {
        val due = huaweiDueDay(heal, today, zone) ?: return false
        return day >= due
    }

    fun stillWanted(heal: HealData, time: LocalTime, day: LocalDate, today: LocalDate): Boolean {
        if (time !in heal.settings.hydrationReminders) return false
        val target = heal.settings.hydrationTargetMl ?: return true
        return day != today || (HealRules.hydrationOn(heal, day) ?: 0) < target
    }
}

class HealReminderAdapter(
    private val heal: () -> HealData,
    private val app: () -> AppData,
    private val today: () -> LocalDate,
) : ReminderSourceAdapter {
    override val module = ReminderModule.HEAL

    private var zone: TimeZone = TimeZone.currentSystemDefault()

    override fun requests(nowMillis: Long, zone: TimeZone): List<ReminderRequest> {
        this.zone = zone
        return HealReminders.upcoming(heal(), app(), nowMillis, zone).map {
            ReminderRequest(module, it.ref, "hydration", it.atMillis, occurrence = it.day.toString())
        } + listOfNotNull(HealReminders.huaweiUpcoming(heal(), app(), nowMillis, zone)?.let {
            ReminderRequest(module, HuaweiReadReminder.REF, "huawei", it.atMillis, occurrence = it.day.toString())
        })
    }

    private fun time(ref: String): LocalTime? {
        if (!ref.startsWith("hyd-") || ref.length != 8) return null
        val h = ref.substring(4, 6).toIntOrNull() ?: return null
        val m = ref.substring(6, 8).toIntOrNull() ?: return null
        return runCatching { LocalTime(h, m) }.getOrNull()
    }

    override fun isStillWanted(request: ReminderRequest): Boolean = runCatching {
        if (request.sourceRef == HuaweiReadReminder.REF) return HealReminders.huaweiStillWanted(heal(), LocalDate.parse(request.occurrence), today(), zone)
        HealReminders.stillWanted(heal(), time(request.sourceRef) ?: return false, LocalDate.parse(request.occurrence), today())
    }.getOrDefault(false)

    override fun present(request: ReminderRequest): NudgeText? =
        if (request.sourceRef == HuaweiReadReminder.REF) NudgeText("Time to read Huawei Health", "") else NudgeText("A moment for a drink?", "")

    override fun execute(request: ReminderRequest, action: String): Boolean = false
}
