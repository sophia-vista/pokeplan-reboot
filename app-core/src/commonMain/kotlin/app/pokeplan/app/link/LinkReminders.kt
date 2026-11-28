package app.pokeplan.app.link

import app.pokeplan.link.FollowUpStatus
import app.pokeplan.link.LinkData
import app.pokeplan.link.LinkRules
import app.pokeplan.plan.toEpochMillis
import app.pokeplan.plan.toLocalDateTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import app.pokeplan.app.AppData
import app.pokeplan.app.plan.NudgeText
import app.pokeplan.app.reminders.FocusWindow
import app.pokeplan.app.reminders.ReminderActions
import app.pokeplan.app.reminders.ReminderModule
import app.pokeplan.app.reminders.ReminderPolicy
import app.pokeplan.app.reminders.ReminderRequest
import app.pokeplan.app.reminders.ReminderSourceAdapter

enum class LinkReminderKind(val key: String) {
    DATE("date"),
    FOLLOW_UP("followup"),
    ;

    companion object {
        fun of(key: String): LinkReminderKind? = entries.firstOrNull { it.key == key }
    }
}

data class LinkReminder(val kind: LinkReminderKind, val recordId: String, val occurrence: String, val atMillis: Long)

object LinkReminders {
    const val DAYS_AHEAD = 4
    const val GENERIC = "You have an upcoming reminder"

    fun upcoming(link: LinkData, app: AppData, nowMillis: Long, zone: TimeZone): List<LinkReminder> {
        val settings = link.settings
        if (!settings.reminders) return emptyList()
        val focus = ReminderPolicy.focusWindow(app.focus)
        val today = nowMillis.toLocalDateTime(zone).date
        val limit = nowMillis + DAYS_AHEAD * DAY_MILLIS
        val hour = settings.reminderHour.coerceIn(0, 23)
        fun at(day: LocalDate) = LocalDateTime(day, LocalTime(hour, 0)).toEpochMillis(zone)

        val found = buildList {
            for (d in link.dates) {
                val lead = d.remindDays ?: continue
                val next = LinkRules.next(d, today) ?: continue
                add(LinkReminder(LinkReminderKind.DATE, d.id, next.day.toString(), at(next.day.minus(lead, DateTimeUnit.DAY))))
            }
            for (f in link.followUps) {
                val due = f.due ?: continue
                if (wantsFollowUp(f.remind, f.status, f.converted, due, today)) add(LinkReminder(LinkReminderKind.FOLLOW_UP, f.id, due.toString(), at(due)))
            }
        }
        return found.map { r ->
            val request = ReminderRequest(ReminderModule.LINK, r.recordId, r.kind.key, r.atMillis, occurrence = r.occurrence)
            r.copy(atMillis = ReminderPolicy.deliverAt(request, app.settings.quietHours, zone, focus))
        }.filter { it.atMillis > nowMillis && it.atMillis <= limit }.sortedBy { it.atMillis }
    }

    private fun wantsFollowUp(remind: Boolean, status: FollowUpStatus, converted: Boolean, due: LocalDate, today: LocalDate) =
        remind && status == FollowUpStatus.OPEN && !converted && due >= today

    fun stillWanted(link: LinkData, kind: LinkReminderKind, recordId: String, occurrence: String, today: LocalDate): Boolean {
        if (!link.settings.reminders) return false
        return when (kind) {
            LinkReminderKind.DATE -> link.dates.firstOrNull { it.id == recordId }?.let { d -> d.remindDays != null && LinkRules.next(d, today)?.day?.toString() == occurrence } ?: false
            LinkReminderKind.FOLLOW_UP -> link.followUps.firstOrNull { it.id == recordId }?.let { f -> f.due?.let { due -> due.toString() == occurrence && wantsFollowUp(f.remind, f.status, f.converted, due, today) } } ?: false
        }
    }

    fun text(link: LinkData, kind: LinkReminderKind, recordId: String): NudgeText {
        val named = if (!link.settings.reminderNames) null else when (kind) {
            LinkReminderKind.DATE -> link.dates.firstOrNull { it.id == recordId }?.takeIf { it.title.isNotBlank() }?.let { d -> d.remindDays?.let { "${d.title}, ${LinkRules.inDays(it)}" } ?: d.title }
            LinkReminderKind.FOLLOW_UP -> link.followUps.firstOrNull { it.id == recordId }?.title?.takeIf { it.isNotBlank() }?.let { "Follow up: $it" }
        }
        return NudgeText(named ?: GENERIC, "")
    }

    private const val DAY_MILLIS = 86_400_000L
}

class LinkReminderAdapter(
    private val link: () -> LinkData,
    private val app: () -> AppData,
    private val today: () -> LocalDate,
    private val completeFollowUp: (id: String) -> Boolean,
) : ReminderSourceAdapter {
    override val module = ReminderModule.LINK

    override fun requests(nowMillis: Long, zone: TimeZone): List<ReminderRequest> =
        LinkReminders.upcoming(link(), app(), nowMillis, zone).map { ReminderRequest(module, it.recordId, it.kind.key, it.atMillis, occurrence = it.occurrence) }

    override fun isStillWanted(request: ReminderRequest): Boolean {
        val kind = LinkReminderKind.of(request.kind) ?: return false
        return LinkReminders.stillWanted(link(), kind, request.sourceRef, request.occurrence, today())
    }

    override fun present(request: ReminderRequest): NudgeText? {
        val kind = LinkReminderKind.of(request.kind) ?: return null
        return if (isStillWanted(request)) LinkReminders.text(link(), kind, request.sourceRef) else null
    }

    override fun execute(request: ReminderRequest, action: String): Boolean =
        action == ReminderActions.DONE && request.kind == LinkReminderKind.FOLLOW_UP.key && isStillWanted(request) && completeFollowUp(request.sourceRef)
}
