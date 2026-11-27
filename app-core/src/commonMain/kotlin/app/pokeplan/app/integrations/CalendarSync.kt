package app.pokeplan.app.integrations

import app.pokeplan.plan.PlanEvent
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.serialization.Serializable

/** A writable calendar on the phone the player can pick as the target (a Google calendar, for one). */
data class TargetCalendar(val id: Long, val name: String, val account: String)

/** What the provider holds for one linked event; [deleted] is a row the provider has marked deleted but not yet purged. */
data class ProviderRow(val id: Long, val deleted: Boolean, val event: ProviderEvent)

/**
 * The phone's Calendar Provider as the sync needs it: no PLAN knowledge, one chosen calendar, and only the rows PokéLife
 * made. [read] returns null when it cannot read at all (permission gone), and the sync then does nothing.
 */
interface CalendarWriter {
    fun writableCalendars(): List<TargetCalendar>

    fun read(calendarId: Long, ids: Set<Long>): Map<Long, ProviderRow>?

    fun insert(calendarId: Long, event: ProviderEvent): Long?

    fun update(id: Long, event: ProviderEvent): Boolean

    fun delete(id: Long): Boolean

    /**
     * Events in [calendarId] the player made there (not invites), whole ones (not a single edited occurrence of a series, and not a
     * series that has edited occurrences), not deleted or cancelled. Empty where the platform cannot say; the sync then imports nothing.
     */
    fun importable(calendarId: Long): List<ProviderRow> = emptyList()

    /**
     * One occurrence of a repeating event changed (an exception row: the series' id, the occurrence's original start, and the
     * occurrence's own fields, with no repeat). False where the platform cannot, or the provider refused. PLAN 8.1B.
     */
    fun saveOccurrence(seriesId: Long, originalStartMillis: Long, event: ProviderEvent): Boolean = false

    /** One occurrence of a repeating event removed (a cancelled exception); the series and its other occurrences stay. */
    fun cancelOccurrence(seriesId: Long, originalStartMillis: Long): Boolean = false
}

/** One PLAN event (by series id) tied to one provider event, and what both held when they last agreed. */
@Serializable
data class SyncLink(val eventId: String, val providerId: Long, val fingerprint: String)

@Serializable
data class CalendarSyncState(
    val enabled: Boolean = false,
    val calendarId: Long? = null,
    val calendarName: String = "",
    val links: List<SyncLink> = emptyList(),
    val lastSyncMillis: Long = 0,
    /** What the last sync did, in a few words the settings card shows; never an event's title. */
    val lastNote: String = "",
    /** Bring in events the player makes in the chosen calendar as PLAN events (owner, 24 November 2026). */
    val importNew: Boolean = true,
)

/** A change the sync wants in PLAN, applied only if the event is still the one it looked at. */
sealed interface LocalChange {
    data class Replace(val old: PlanEvent, val new: PlanEvent, val overwroteLocalEdit: Boolean) : LocalChange
    data class Remove(val old: PlanEvent) : LocalChange

    /** An event the player made in the calendar, now a PLAN event (linked, so it syncs both ways from here). */
    data class Add(val event: PlanEvent) : LocalChange
}

data class SyncOutcome(
    val changes: List<LocalChange>,
    val links: List<SyncLink>,
    val pushed: Int = 0,
    val pulled: Int = 0,
    val created: Int = 0,
    val removedInCalendar: Int = 0,
    val removedHere: Int = 0,
    val imported: Int = 0,
    /** Events already on both sides with the same content, linked instead of copied. */
    val matched: Int = 0,
    /** Linked events the provider holds in a form PLAN cannot show: PLAN's version was kept and written back. */
    val unreadable: Int = 0,
    val failed: Int = 0,
) {
    val quiet: Boolean get() = changes.isEmpty() && pushed + created + removedInCalendar + unreadable + failed + matched == 0
}

/**
 * The two-way sync of PLAN's own events with one calendar on the phone. Pure but for the [CalendarWriter]: PLAN's events and the
 * saved links go in, and the changes for PLAN and the new links come out. Rules (owner, 24 November 2026):
 *  - Only events PokéLife made are ever written, changed or deleted in the calendar; other events there are read-only as before.
 *  - An event changed on one side only goes to the other. Changed on both, the calendar's version wins (the provider keeps no
 *    edit time, and a change here is written at once, so a true tie is the rare case) and the PLAN version is offered back.
 *  - Deleted on one side, deleted on the other; deleted in the calendar but edited here since, kept here and written again.
 *  - New events in the calendar are never imported as PLAN events or tasks, and nothing here pays.
 *  - Colour only goes out (the provider may drop it), and companion, people and rewards never leave PokéLife.
 */
object CalendarSync {
    const val MAX_LINKS = 500

    /** How far back an unlinked event is still worth sending: older ones stay in PokéLife only. */
    const val RECENT_DAYS = 30

    fun run(
        events: List<PlanEvent>, state: CalendarSyncState, writer: CalendarWriter, zone: TimeZone, today: LocalDate, newId: () -> String = { error("no id source") },
    ): SyncOutcome? {
        val calendar = state.calendarId ?: return null
        val remote = writer.read(calendar, state.links.map { it.providerId }.toSet()) ?: return null
        val byId = events.associateBy { it.id }
        val links = mutableListOf<SyncLink>()
        val changes = mutableListOf<LocalChange>()
        var pushed = 0; var pulled = 0; var created = 0; var removedInCalendar = 0; var removedHere = 0; var unreadable = 0; var failed = 0
        var imported = 0; var matched = 0

        for (link in state.links) {
            val local = byId[link.eventId]
            val row = remote[link.providerId]?.takeUnless { it.deleted }
            if (local == null) {
                if (row != null && !writer.delete(link.providerId)) { links += link; failed++ } else if (row != null) removedInCalendar++
                continue
            }
            val localPrint = CalendarEventMapping.fingerprint(local)
            val localChanged = localPrint != link.fingerprint
            if (row == null) {
                if (localChanged) {
                    // Gone in the calendar but edited here since: keep it and write it again.
                    val id = writer.insert(calendar, CalendarEventMapping.toProvider(local, zone))
                    if (id != null) { links += SyncLink(local.id, id, localPrint); created++ } else { links += link; failed++ }
                } else { changes += LocalChange.Remove(local); removedHere++ }
                continue
            }
            val seen = CalendarEventMapping.fromProvider(row.event, zone, local.id)
            if (seen == null) {
                // Held in a form PLAN cannot show: keep PLAN's version and write it back.
                unreadable++
                if (writer.update(link.providerId, CalendarEventMapping.toProvider(local, zone))) { pushed++; links += link.copy(fingerprint = localPrint) } else { failed++; links += link }
                continue
            }
            val seenPrint = CalendarEventMapping.fingerprint(seen)
            val remoteChanged = seenPrint != link.fingerprint
            when {
                localPrint == seenPrint -> links += link.copy(fingerprint = localPrint)
                remoteChanged -> {
                    changes += LocalChange.Replace(local, local.withFieldsOf(seen), overwroteLocalEdit = localChanged)
                    links += link.copy(fingerprint = seenPrint)
                    pulled++
                }
                localChanged -> {
                    if (writer.update(link.providerId, CalendarEventMapping.toProvider(local, zone))) { pushed++; links += link.copy(fingerprint = localPrint) } else { failed++; links += link }
                }
                else -> links += link
            }
        }

        val linked = links.map { it.eventId }.toSet() + state.links.map { it.eventId }.toSet()
        val linkedRows = links.map { it.providerId }.toSet() + state.links.map { it.providerId }.toSet()

        // The player's own events in the calendar that PLAN can show, not yet linked.
        val offered = if (state.importNew) (writer.importable(calendar).filter { !it.deleted && it.id !in linkedRows }) else emptyList()
        val readable = offered.mapNotNull { row -> CalendarEventMapping.fromProvider(row.event, zone, "")?.let { row to it } }
        val taken = mutableSetOf<Long>()

        // The same event already on both sides is linked, never copied: a second copy is what turning the sync off and on, or a
        // crash between two writes, would otherwise leave behind.
        val unlinkedHere = events.filter { it.id !in linked && recent(it, today) }
        val toSend = mutableListOf<PlanEvent>()
        unlinkedHere.forEach { e ->
            val print = CalendarEventMapping.fingerprint(e)
            val twin = readable.firstOrNull { (row, seen) -> row.id !in taken && CalendarEventMapping.fingerprint(seen) == print }
            if (twin != null) { taken += twin.first.id; links += SyncLink(e.id, twin.first.id, print); matched++ } else toSend += e
        }

        val room = (MAX_LINKS - links.size).coerceAtLeast(0)
        toSend.take(room).forEach { e ->
            val id = writer.insert(calendar, CalendarEventMapping.toProvider(e, zone))
            if (id != null) { links += SyncLink(e.id, id, CalendarEventMapping.fingerprint(e)); created++ } else failed++
        }

        // What is left in the calendar is new here: bring it in. It pays nothing and has no companion until the player gives it one.
        val roomForImports = (MAX_LINKS - links.size).coerceAtLeast(0)
        readable.filter { (row, seen) -> row.id !in taken && recent(seen, today) }.take(roomForImports).forEach { (row, seen) ->
            val e = seen.copy(id = newId())
            changes += LocalChange.Add(e)
            links += SyncLink(e.id, row.id, CalendarEventMapping.fingerprint(e))
            imported++
        }
        return SyncOutcome(changes, links, pushed, pulled, created, removedInCalendar, removedHere, imported, matched, unreadable, failed)
    }

    /** Takes the calendar's version of the fields PLAN syncs and keeps everything that is PokéLife's own. */
    private fun PlanEvent.withFieldsOf(seen: PlanEvent): PlanEvent = copy(
        title = seen.title, day = seen.day, start = seen.start, minutes = seen.minutes, allDay = seen.allDay, lastDay = seen.lastDay,
        repeat = seen.repeat, until = seen.until, skipped = seen.skipped, description = seen.description, location = seen.location,
    )

    private fun recent(e: PlanEvent, today: LocalDate): Boolean {
        val cutoff = today.minus(DatePeriod(days = RECENT_DAYS))
        return if (e.repeat != null) (e.until ?: LocalDate(9999, 1, 1)) >= cutoff else e.endDay >= cutoff
    }

    /** A link to an event that is gone on both sides, or a calendar that was changed, is simply dropped. */
    fun unlinked(state: CalendarSyncState): CalendarSyncState = state.copy(links = emptyList())
}
