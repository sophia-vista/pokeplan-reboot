package app.pokeplan.app.heal

import app.pokeplan.heal.HealData
import app.pokeplan.heal.HealRules
import app.pokeplan.heal.MedicationStatus
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

/** One reminder of the player's own entered medication schedule, or of a count they set that has run low. */
data class MedicationReminder(val medicationId: String, val time: LocalTime, val day: LocalDate, val atMillis: Long, val lowSupply: Boolean = false) {
    val kind: String get() = if (lowSupply) KIND_LOW else KIND_DOSE

    val ref: String get() = if (lowSupply) "medlow-$medicationId" else "med-$medicationId-" + timeText(time)

    companion object {
        const val KIND_DOSE = "medication"
        const val KIND_LOW = "medlow"

        fun timeText(t: LocalTime) = t.hour.toString().padStart(2, '0') + t.minute.toString().padStart(2, '0')

        fun medicationId(ref: String, lowSupply: Boolean): String =
            if (lowSupply) ref.removePrefix("medlow-") else ref.removePrefix("med-").substringBeforeLast("-")

        fun timeOf(ref: String): LocalTime? {
            val text = ref.substringAfterLast('-')
            if (text.length != 4) return null
            val h = text.substring(0, 2).toIntOrNull() ?: return null
            val m = text.substring(2, 4).toIntOrNull() ?: return null
            return runCatching { LocalTime(h, m) }.getOrNull()
        }
    }
}

object HealReminders {
    const val DAYS_AHEAD = 2

    fun upcoming(heal: HealData, app: AppData, nowMillis: Long, zone: TimeZone): List<HydrationReminder> {
        if (heal.settings.hydrationReminders.isEmpty()) return emptyList()
        val focus = ReminderPolicy.focusWindow(app.focus)
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
        val focus = ReminderPolicy.focusWindow(app.focus)
        val at = LocalDateTime(day, heal.settings.huaweiRemindAt).toEpochMillis(zone)
        val request = ReminderRequest(ReminderModule.HEAL, HuaweiReadReminder.REF, "huawei", at, occurrence = day.toString())
        return HuaweiReadReminder(day, ReminderPolicy.deliverAt(request, app.settings.quietHours, zone, focus))
    }

    fun huaweiStillWanted(heal: HealData, day: LocalDate, today: LocalDate, zone: TimeZone): Boolean {
        val due = huaweiDueDay(heal, today, zone) ?: return false
        return day >= due
    }

    /** The player's own schedule and low-supply count: one reminder per unlogged scheduled time, and one a day when low. */
    fun medicationUpcoming(heal: HealData, app: AppData, nowMillis: Long, zone: TimeZone): List<MedicationReminder> {
        val medications = heal.medications.filter { it.remind && it.status == MedicationStatus.ACTIVE }
        if (medications.isEmpty()) return emptyList()
        val focus = ReminderPolicy.focusWindow(app.focus)
        val today = nowMillis.toLocalDateTime(zone).date
        return (0..DAYS_AHEAD).flatMap { offset ->
            val day = today.plus(DatePeriod(days = offset))
            medications.flatMap { m ->
                val times = HealRules.medicationTimesOn(m, day)
                val logged = heal.doses.filter { it.medicationId == m.id && it.day == day }.mapTo(HashSet()) { it.time }
                val doses = times.filter { it !in logged }.mapNotNull { time ->
                    val at = LocalDateTime(day, time).toEpochMillis(zone)
                    if (at <= nowMillis) return@mapNotNull null
                    val request = ReminderRequest(ReminderModule.HEAL, MedicationReminder(m.id, time, day, at).ref, MedicationReminder.KIND_DOSE, at, occurrence = day.toString())
                    MedicationReminder(m.id, time, day, ReminderPolicy.deliverAt(request, app.settings.quietHours, zone, focus))
                }
                val low = times.lastOrNull()?.takeIf { HealRules.medicationLowSupply(m) }?.let { time ->
                    val at = LocalDateTime(day, time).toEpochMillis(zone)
                    if (at <= nowMillis) return@let null
                    val request = ReminderRequest(ReminderModule.HEAL, MedicationReminder(m.id, time, day, at, lowSupply = true).ref, MedicationReminder.KIND_LOW, at, occurrence = day.toString())
                    MedicationReminder(m.id, time, day, ReminderPolicy.deliverAt(request, app.settings.quietHours, zone, focus), lowSupply = true)
                }
                doses + listOfNotNull(low)
            }
        }.filter { it.atMillis > nowMillis }.sortedBy { it.atMillis }
    }

    fun medicationStillWanted(heal: HealData, request: ReminderRequest): Boolean {
        val low = request.kind == MedicationReminder.KIND_LOW
        val m = heal.medications.firstOrNull { it.id == MedicationReminder.medicationId(request.sourceRef, low) } ?: return false
        if (!m.remind || m.status != MedicationStatus.ACTIVE) return false
        val day = runCatching { LocalDate.parse(request.occurrence) }.getOrNull() ?: return false
        if (!HealRules.medicationRunning(m, day)) return false
        if (low) return HealRules.medicationLowSupply(m)
        val time = MedicationReminder.timeOf(request.sourceRef) ?: return false
        if (time !in m.times) return false
        return heal.doses.none { it.medicationId == m.id && it.day == day && it.time == time }
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
        }) + HealReminders.medicationUpcoming(heal(), app(), nowMillis, zone).map {
            ReminderRequest(module, it.ref, it.kind, it.atMillis, occurrence = it.day.toString())
        }
    }

    private fun time(ref: String): LocalTime? {
        if (!ref.startsWith("hyd-") || ref.length != 8) return null
        val h = ref.substring(4, 6).toIntOrNull() ?: return null
        val m = ref.substring(6, 8).toIntOrNull() ?: return null
        return runCatching { LocalTime(h, m) }.getOrNull()
    }

    override fun isStillWanted(request: ReminderRequest): Boolean = runCatching {
        if (request.kind == MedicationReminder.KIND_DOSE || request.kind == MedicationReminder.KIND_LOW) return HealReminders.medicationStillWanted(heal(), request)
        if (request.sourceRef == HuaweiReadReminder.REF) return HealReminders.huaweiStillWanted(heal(), LocalDate.parse(request.occurrence), today(), zone)
        HealReminders.stillWanted(heal(), time(request.sourceRef) ?: return false, LocalDate.parse(request.occurrence), today())
    }.getOrDefault(false)

    override fun present(request: ReminderRequest): NudgeText? = when (request.kind) {
        MedicationReminder.KIND_DOSE -> NudgeText("A medication is due", "")
        MedicationReminder.KIND_LOW -> NudgeText("A refill may be near", "")
        else -> if (request.sourceRef == HuaweiReadReminder.REF) NudgeText("Time to read Huawei Health", "") else NudgeText("A moment for a drink?", "")
    }

    override fun execute(request: ReminderRequest, action: String): Boolean = false
}
