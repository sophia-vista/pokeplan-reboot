package app.pokeplan.app.integrations

import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.newStoreLock
import app.pokeplan.plan.PlanEvent
import kotlinx.datetime.TimeZone

/** What one sync did, for a short note and an Undo: [before] is PLAN's events as they were. */
data class SyncReport(val outcome: SyncOutcome, val applied: Int, val before: List<PlanEvent>, val note: String) {
    /** True when the calendar changed something in PLAN, so an Undo is worth offering. */
    val changedPlan: Boolean get() = applied > 0
}

/**
 * Two-way sync of PLAN's own events with one calendar on the phone (PLAN 8.1, INTEGRATIONS_AND_PRIVACY 6.2). It sits beside both
 * PLAN and the calendar and belongs to neither; it is off until the player turns it on and picks a calendar.
 */
class PlanCalendarSync(
    private val store: GameStore,
    private val integrations: IntegrationStore,
    private val writer: CalendarWriter,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    private val lock = newStoreLock()
    private var running = false

    val state: CalendarSyncState get() = integrations.data.calendarSync

    val enabled: Boolean get() = state.enabled && state.calendarId != null

    fun calendars(): List<TargetCalendar> = writer.writableCalendars()

    /** Turns the sync on for [calendar]; its first run sends the recent events and starts the links. Null when it could not start. */
    fun enable(calendar: TargetCalendar): SyncReport? {
        if (state.calendarId != calendar.id && state.links.isNotEmpty()) disable(removeFromCalendar = false)
        integrations.setCalendarSync(CalendarSyncState(enabled = true, calendarId = calendar.id, calendarName = calendar.name))
        return sync()
    }

    /** Turns it off; [removeFromCalendar] also deletes the events PokéLife put there (and only those). */
    fun disable(removeFromCalendar: Boolean) {
        if (removeFromCalendar) state.links.forEach { writer.delete(it.providerId) }
        integrations.setCalendarSync(CalendarSyncState(calendarId = state.calendarId, calendarName = state.calendarName))
    }

    /** One pass. Null when it is off, already running, or the calendar cannot be read. Safe to call from any thread. */
    fun sync(): SyncReport? {
        val taken = lock.withLock { if (running) false else { running = true; true } }
        if (!taken) return null
        try {
            val s = state
            if (!s.enabled || s.calendarId == null) return null
            val before = store.data.events
            val outcome = CalendarSync.run(before, s, writer, zone(), store.today)
            if (outcome == null) {
                integrations.setCalendarSync(s.copy(lastNote = "The calendar could not be read"))
                return null
            }
            val replace = outcome.changes.filterIsInstance<LocalChange.Replace>().map { it.old to it.new }
            val remove = outcome.changes.filterIsInstance<LocalChange.Remove>().map { it.old }
            val applied = store.applyEventChanges(replace, remove)
            val note = noteOf(outcome)
            integrations.setCalendarSync(s.copy(links = outcome.links, lastSyncMillis = store.nowMillis, lastNote = note))
            return SyncReport(outcome, applied, before, note)
        } finally {
            lock.withLock { running = false }
        }
    }

    private fun noteOf(o: SyncOutcome): String = when {
        o.failed > 0 -> "${o.failed} could not be written"
        o.quiet -> "Up to date"
        else -> listOfNotNull(
            "sent ${o.pushed + o.created}".takeIf { o.pushed + o.created > 0 },
            "got ${o.pulled}".takeIf { o.pulled > 0 },
            "removed ${o.removedHere + o.removedInCalendar}".takeIf { o.removedHere + o.removedInCalendar > 0 },
            "${o.unreadable} kept as here".takeIf { o.unreadable > 0 },
        ).joinToString(", ").replaceFirstChar { it.uppercase() }
    }
}
