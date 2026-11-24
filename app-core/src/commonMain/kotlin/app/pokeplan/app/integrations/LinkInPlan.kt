package app.pokeplan.app.integrations

import app.pokeplan.link.DateKind
import app.pokeplan.link.FollowUpState
import app.pokeplan.link.ImportantDate
import app.pokeplan.link.LinkData
import app.pokeplan.link.LinkRules
import app.pokeplan.plan.CalendarEvent
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/** Where a tap on a LINK entry in the PLAN calendar goes: the person if the entry has one, else the matching LINK place. */
data class LinkTarget(val personId: String?, val followUp: Boolean)

/**
 * LINK dates and follow-ups as read-only PLAN calendar entries. Pure: it reads a [LinkData] and returns [CalendarEvent]s that
 * PLAN draws like any other outside entry. Nothing here is written back to LINK or to the phone's calendar, pays, or reaches
 * a reminder, the launcher or a widget (LINK 2.3, 7).
 */
object LinkInPlan {
    /** The calendar id every entry carries, apart from the phone's calendars (positive) and PLAN's own (-1). */
    const val CALENDAR_ID = -2L

    private const val PREFIX = "link:"

    fun owns(eventId: String): Boolean = eventId.startsWith(PREFIX)

    fun entries(
        data: LinkData, from: LocalDate, to: LocalDate, today: LocalDate, dates: Boolean, followUps: Boolean, color: Long? = null,
    ): List<CalendarEvent> {
        if (to < from) return emptyList()
        val out = mutableListOf<CalendarEvent>()
        if (dates) data.dates.forEach { d -> occurrencesOf(d, from, to).forEach { day -> out += dateEntry(data, d, day, color) } }
        if (followUps) data.followUps.forEach { f ->
            val due = f.due ?: return@forEach
            if (f.converted || due < from || due > to) return@forEach
            val state = LinkRules.state(f, today)
            if (state != FollowUpState.UPCOMING && state != FollowUpState.DUE) return@forEach
            out += allDay("${PREFIX}follow:${f.id}", labelled(data, f.personId, "Follow up: ${f.title}"), due, color)
        }
        return out
    }

    /** Where a tap on [eventId] should go, or null when it is not one of ours or what it pointed at is gone. */
    fun target(data: LinkData, eventId: String): LinkTarget? {
        val parts = eventId.split(':')
        if (parts.size < 3 || parts[0] != "link") return null
        return when (parts[1]) {
            "date" -> data.dates.firstOrNull { it.id == parts[2] }?.let { LinkTarget(it.personId?.takeIf { id -> data.people.any { p -> p.id == id } }, followUp = false) }
            "follow" -> data.followUps.firstOrNull { it.id == parts[2] }?.let { LinkTarget(it.personId?.takeIf { id -> data.people.any { p -> p.id == id } }, followUp = true) }
            else -> null
        }
    }

    private fun occurrencesOf(d: ImportantDate, from: LocalDate, to: LocalDate): List<LocalDate> {
        val years = if (d.annual) (from.year..to.year) else listOfNotNull(d.year)
        return years.map { LinkRules.observed(d.month, d.day, it) }.filter { it in from..to }.filter { day -> d.year == null || !d.annual || day.year >= d.year }
    }

    private fun dateEntry(data: LinkData, d: ImportantDate, day: LocalDate, color: Long?): CalendarEvent {
        val years = d.year?.takeIf { d.annual && d.kind == DateKind.BIRTHDAY }?.let { day.year - it }?.takeIf { it > 0 }
        val title = labelled(data, d.personId, d.title) + (years?.let { " ($it)" } ?: "")
        return allDay("${PREFIX}date:${d.id}:$day", title, day, color)
    }

    private fun labelled(data: LinkData, personId: String?, title: String): String {
        val person = personId?.let { id -> data.people.firstOrNull { it.id == id } } ?: return title
        val name = LinkRules.displayName(person)
        return if (title.contains(name, ignoreCase = true)) title else "$name: $title"
    }

    private fun allDay(id: String, title: String, day: LocalDate, color: Long?) = CalendarEvent(
        id, CALENDAR_ID, title, LocalDateTime(day, LocalTime(0, 0)), LocalDateTime(day.plus(DatePeriod(days = 1)), LocalTime(0, 0)), allDay = true, color = color,
    )
}
