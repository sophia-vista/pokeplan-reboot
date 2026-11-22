package app.pokeplan.app.base

import app.pokeplan.base.ChoreState
import app.pokeplan.base.BaseData
import app.pokeplan.base.BaseRules
import app.pokeplan.base.RecordStatus
import app.pokeplan.base.RepeatKind
import app.pokeplan.plan.toEpochMillis
import app.pokeplan.plan.toLocalDateTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import app.pokeplan.app.AppData
import app.pokeplan.app.plan.NudgeText
import app.pokeplan.app.reminders.FocusWindow
import app.pokeplan.app.reminders.ReminderActions
import app.pokeplan.app.reminders.ReminderModule
import app.pokeplan.app.reminders.ReminderPolicy
import app.pokeplan.app.reminders.ReminderRequest
import app.pokeplan.app.reminders.ReminderSourceAdapter

enum class BaseReminderKind(val key: String, val publicText: String) {
    CHORE("chore", "A chore is due"),
    MAINTENANCE("maintenance", "Some upkeep is coming up"),
    LOW_STOCK("low", "A supply is running low"),
    WARRANTY("warranty", "A warranty is ending soon"),
    ;

    companion object {
        fun of(key: String): BaseReminderKind? = entries.firstOrNull { it.key == key }
    }
}

data class BaseReminder(val kind: BaseReminderKind, val recordId: String, val occurrence: String, val atMillis: Long)

object BaseReminders {
    const val DAYS_AHEAD = 4

    fun upcoming(home: BaseData, app: AppData, nowMillis: Long, zone: TimeZone): List<BaseReminder> {
        val settings = home.settings
        if (!settings.reminders) return emptyList()
        val session = app.focus?.takeIf { !it.isFinished }
        val focus = session?.let { FocusWindow(it.startedAtMillis, it.startedAtMillis + it.durationMillis + it.pausedMillis) }
        val today = nowMillis.toLocalDateTime(zone).date
        val limit = nowMillis + DAYS_AHEAD * DAY_MILLIS
        val hour = settings.reminderHour.coerceIn(0, 23)
        fun at(day: LocalDate) = LocalDateTime(day, LocalTime(hour, 0)).toEpochMillis(zone)

        val found = buildList {
            for (c in home.chores) if (c.remind && c.status == RecordStatus.ACTIVE) nextChoreDay(home, c.id, today)?.let { add(BaseReminder(BaseReminderKind.CHORE, c.id, it.toString(), at(it))) }
            for (s in home.schedules) if (s.remind && s.status == RecordStatus.ACTIVE) {
                val day = s.nextDue.minus(settings.maintenanceLeadDays.coerceIn(0, BaseStore.MAX_LEAD_DAYS), DateTimeUnit.DAY)
                add(BaseReminder(BaseReminderKind.MAINTENANCE, s.id, s.nextDue.toString(), at(day)))
            }
            for (w in home.warranties) if (w.remind && w.expiryDay != null) {
                val day = w.expiryDay!!.minus(settings.warrantyLeadDays.coerceIn(0, BaseStore.MAX_WARRANTY_LEAD_DAYS), DateTimeUnit.DAY)
                add(BaseReminder(BaseReminderKind.WARRANTY, w.id, w.expiryDay.toString(), at(day)))
            }
            for (s in home.supplies) if (s.remind && BaseRules.isLow(s) && !s.lowDismissed) {
                val since = maxOf(s.createdMillis, home.adjustments.filter { it.supplyId == s.id }.maxOfOrNull { it.atMillis } ?: 0L)
                var day = since.toLocalDateTime(zone).date
                if (at(day) < since) day = day.plus(1, DateTimeUnit.DAY)
                add(BaseReminder(BaseReminderKind.LOW_STOCK, s.id, since.toString(), at(day)))
            }
        }
        return found.map { r ->
            val request = ReminderRequest(ReminderModule.BASE, r.recordId, r.kind.key, r.atMillis, occurrence = r.occurrence)
            r.copy(atMillis = ReminderPolicy.deliverAt(request, app.settings.quietHours, zone, focus))
        }.filter { it.atMillis > nowMillis && it.atMillis <= limit }.sortedBy { it.atMillis }
    }

    private fun nextChoreDay(home: BaseData, choreId: String, today: LocalDate): LocalDate? {
        val chore = home.chores.firstOrNull { it.id == choreId } ?: return null
        val v = BaseRules.view(home, chore, today)
        return when (v.state) {
            ChoreState.OVERDUE, ChoreState.DUE, ChoreState.UPCOMING -> v.dueDay
            ChoreState.DONE, ChoreState.SKIPPED -> if (chore.recurrence.kind != RepeatKind.ONCE) v.dueDay else null
            ChoreState.ANYTIME, ChoreState.PAUSED, ChoreState.ARCHIVED -> null
        }
    }

    fun stillWanted(home: BaseData, kind: BaseReminderKind, recordId: String, occurrence: String, today: LocalDate): Boolean {
        if (!home.settings.reminders) return false
        return when (kind) {
            BaseReminderKind.CHORE -> home.chores.firstOrNull { it.id == recordId }?.let { it.remind && it.status == RecordStatus.ACTIVE && nextChoreDay(home, it.id, today)?.toString() == occurrence } ?: false
            BaseReminderKind.MAINTENANCE -> home.schedules.firstOrNull { it.id == recordId }?.let { it.remind && it.status == RecordStatus.ACTIVE && it.nextDue.toString() == occurrence } ?: false
            BaseReminderKind.WARRANTY -> home.warranties.firstOrNull { it.id == recordId }?.let { it.remind && it.expiryDay?.toString() == occurrence && (it.expiryDay?.let { d -> d >= today } == true) } ?: false
            BaseReminderKind.LOW_STOCK -> home.supplies.firstOrNull { it.id == recordId }?.let { it.remind && BaseRules.isLow(it) && !it.lowDismissed } ?: false
        }
    }

    fun text(home: BaseData, kind: BaseReminderKind, recordId: String): NudgeText {
        val name = if (!home.settings.reminderNames) null else when (kind) {
            BaseReminderKind.CHORE -> home.chores.firstOrNull { it.id == recordId }?.title
            BaseReminderKind.MAINTENANCE -> home.schedules.firstOrNull { it.id == recordId }?.title
            BaseReminderKind.LOW_STOCK -> home.supplies.firstOrNull { it.id == recordId }?.name
            BaseReminderKind.WARRANTY -> home.warranties.firstOrNull { it.id == recordId }?.let { w -> home.assets.firstOrNull { it.id == w.assetId }?.name }
        }?.takeIf { it.isNotBlank() }
        return NudgeText(if (name != null) named(kind, name) else kind.publicText, "")
    }

    private fun named(kind: BaseReminderKind, name: String) = when (kind) {
        BaseReminderKind.CHORE -> "Due: $name"
        BaseReminderKind.MAINTENANCE -> "Coming up: $name"
        BaseReminderKind.LOW_STOCK -> "Running low: $name"
        BaseReminderKind.WARRANTY -> "Warranty ending: $name"
    }

    private const val DAY_MILLIS = 86_400_000L
}

class BaseReminderAdapter(
    private val home: () -> BaseData,
    private val app: () -> AppData,
    private val today: () -> LocalDate,
    private val completeChore: (choreId: String) -> Boolean,
) : ReminderSourceAdapter {
    override val module = ReminderModule.BASE

    override fun requests(nowMillis: Long, zone: TimeZone): List<ReminderRequest> =
        BaseReminders.upcoming(home(), app(), nowMillis, zone).map { ReminderRequest(module, it.recordId, it.kind.key, it.atMillis, occurrence = it.occurrence) }

    override fun isStillWanted(request: ReminderRequest): Boolean {
        val kind = BaseReminderKind.of(request.kind) ?: return false
        return BaseReminders.stillWanted(home(), kind, request.sourceRef, request.occurrence, today())
    }

    override fun present(request: ReminderRequest): NudgeText? {
        val kind = BaseReminderKind.of(request.kind) ?: return null
        return if (isStillWanted(request)) BaseReminders.text(home(), kind, request.sourceRef) else null
    }

    override fun execute(request: ReminderRequest, action: String): Boolean =
        action == ReminderActions.DONE && request.kind == BaseReminderKind.CHORE.key && isStillWanted(request) && completeChore(request.sourceRef)
}
