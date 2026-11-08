package app.pokeplan.app

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

/** The two things Link may remind about (LINK.md 5). The names are stable: they are part of a reminder's id. */
enum class LinkReminderKind(val key: String) {
    DATE("date"),
    FOLLOW_UP("followup"),
    ;

    companion object {
        fun of(key: String): LinkReminderKind? = entries.firstOrNull { it.key == key }
    }
}

/** A Link reminder: the record, what kind, the occurrence it is for (the day it is observed or due), and when it goes off. */
data class LinkReminder(val kind: LinkReminderKind, val recordId: String, val occurrence: String, val atMillis: Long)

/**
 * LINK's reminders (LINK.md section 5), through the shared scheduler. **All off until the user turns them on**, first for Link as a whole and then for each
 * important date (the day of, or 1, 3 or 7 days before) and each follow-up (on its day), so a contact list never turns into a stream of notices. Each asks once
 * and never repeats; a date that has passed, a follow-up that is overdue, done, cancelled, edited so the day moved, or deleted asks nothing more, and no backlog
 * is rebuilt. A follow-up handed to Plan is no longer Link's to remind about (Plan is its source). Delivery follows the shared [ReminderPolicy] (the global quiet
 * hours and a focus session); delivering, snoozing and dismissing never change a record and never pay or cost anything.
 *
 * The request holds only an opaque record id, the kind and the occurrence's day. The lock screen always says the same generic words; the date's or follow-up's
 * own words show after unlocking only if the user allowed it, and never a person's name, a note or a date's type beyond its title.
 */
object LinkReminders {
    const val DAYS_AHEAD = 4
    const val GENERIC = "You have an upcoming reminder"

    /** The reminders still to come after [nowMillis], earliest first, within the days the scheduler plans over. */
    fun upcoming(link: LinkData, app: AppData, nowMillis: Long, zone: TimeZone): List<LinkReminder> {
        val settings = link.settings
        if (!settings.reminders) return emptyList()
        val session = app.focus?.takeIf { !it.isFinished }
        val focus = session?.let { FocusWindow(it.startedAtMillis, it.startedAtMillis + it.durationMillis + it.pausedMillis) }
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

    /**
     * Whether the reminder of [kind] for [recordId] and [occurrence] should still show: Link's reminders are on, the record exists, still asks, and is still
     * waiting for that very occurrence (the date has not passed or moved, the follow-up is open, not handed to Plan, and its day is the same).
     */
    fun stillWanted(link: LinkData, kind: LinkReminderKind, recordId: String, occurrence: String, today: LocalDate): Boolean {
        if (!link.settings.reminders) return false
        return when (kind) {
            LinkReminderKind.DATE -> link.dates.firstOrNull { it.id == recordId }?.let { d -> d.remindDays != null && LinkRules.next(d, today)?.day?.toString() == occurrence } ?: false
            LinkReminderKind.FOLLOW_UP -> link.followUps.firstOrNull { it.id == recordId }?.let { f -> f.due?.let { due -> due.toString() == occurrence && wantsFollowUp(f.remind, f.status, f.converted, due, today) } } ?: false
        }
    }

    /** What a notification says: the same generic words for every record, or the record's own title when the user allowed it. Never a person, a note or a number. */
    fun text(link: LinkData, kind: LinkReminderKind, recordId: String): NudgeText {
        val named = if (!link.settings.reminderNames) null else when (kind) {
            LinkReminderKind.DATE -> link.dates.firstOrNull { it.id == recordId }?.takeIf { it.title.isNotBlank() }?.let { d -> d.remindDays?.let { "${d.title}, ${LinkRules.inDays(it)}" } ?: d.title }
            LinkReminderKind.FOLLOW_UP -> link.followUps.firstOrNull { it.id == recordId }?.title?.takeIf { it.isNotBlank() }?.let { "Follow up: $it" }
        }
        return NudgeText(named ?: GENERIC, "")
    }

    private const val DAY_MILLIS = 86_400_000L
}

/**
 * LINK's side of the shared reminders. Every request is checked against Link's records before it is shown or acted on. A follow-up's notification may offer
 * "Done" (the follow-up's own completion, which pays Postcard Trail once exactly as ticking it in Link does); every kind offers "Tomorrow". A reminder never
 * edits a date, a person or a note, and dismissing one never changes anything.
 */
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
