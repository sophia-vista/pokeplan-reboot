package app.pokeplan.app.integrations

import app.pokeplan.app.storage.SavedDocument
import app.pokeplan.app.storage.Storage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Which LINK people are tied to which PLAN event. Both ids are opaque here: this layer reads neither module's data, so PLAN
 * and LINK stay separate and neither one carries the other's record. A PLAN event is named by its series id, so one link
 * covers every repeat of it.
 */
@Serializable
data class EventPeople(val eventId: String, val personIds: List<String>)

/** The integration's own document: the links, and what it is allowed to show. Nothing in it is a reward input. */
@Serializable
data class IntegrationData(
    val eventPeople: List<EventPeople> = emptyList(),
    /** Private notes, checklist and links on calendar events, kept apart from the events (PLAN 8.4). */
    val enrichments: List<EventEnrichment> = emptyList(),
    /** LINK dates (birthdays, anniversaries and the rest) as read-only entries in the PLAN calendar. */
    val linkDatesInCalendar: Boolean = true,
    /** Open LINK follow-ups with a due day, the same way. */
    val linkFollowUpsInCalendar: Boolean = true,
    /** The two-way sync of PLAN events with one calendar on the phone (off until the player turns it on). */
    val calendarSync: CalendarSyncState = CalendarSyncState(),
)

class IntegrationStore(storage: Storage) {
    private val document = SavedDocument(storage, IntegrationData.serializer(), json) { IntegrationData() }

    val data: IntegrationData get() = document.value

    fun observe(listener: (IntegrationData) -> Unit): () -> Unit = document.observe(listener)

    fun reload() = document.reload()

    fun peopleOf(eventId: String): List<String> = data.eventPeople.firstOrNull { it.eventId == eventId }?.personIds.orEmpty()

    fun eventsOf(personId: String): List<String> = data.eventPeople.filter { personId in it.personIds }.map { it.eventId }

    /** Replaces an event's people; an empty list removes the link record. Opaque ids, de-duplicated, at most [MAX_PEOPLE]. */
    fun setPeople(eventId: String, personIds: List<String>) {
        val ids = personIds.filter { it.isNotBlank() }.distinct().take(MAX_PEOPLE)
        document.update { d ->
            val others = d.eventPeople.filterNot { it.eventId == eventId }
            d.copy(eventPeople = if (ids.isEmpty()) others else others + EventPeople(eventId, ids))
        }
    }

    /** Drops links to events or people that no longer exist; changes nothing (and writes nothing) when all are live. */
    fun prune(eventIds: Set<String>, personIds: Set<String>, taskIds: Set<String>? = null) {
        val next = prune(data, eventIds, personIds, taskIds)
        if (next != data) document.update { next }
    }

    // Event enrichment (PLAN 8.4): notes, a checklist, related tasks and records, kept per event key.

    fun enrichmentOf(key: String): EventEnrichment? = data.enrichmentOf(key)

    private fun edit(key: String, snapshot: SourceSnapshot? = null, change: (EventEnrichment) -> EventEnrichment) = document.update { d ->
        val current = d.enrichmentOf(key) ?: EventEnrichment(key)
        val next = change(current).let { if (snapshot != null) it.copy(seen = snapshot, unavailable = false) else it }
        val others = d.enrichments.filterNot { it.key == key }
        // A record with nothing left in it (and no people on the event) is dropped.
        d.copy(enrichments = if (next.isEmpty && d.eventPeople.none { it.eventId == key }) others else others + next)
    }

    /** Makes sure a record exists for [key] with what its source looks like now (used when people are tied to a phone-calendar event). */
    fun track(key: String, snapshot: SourceSnapshot) = edit(key, snapshot) { it }

    fun setNotes(key: String, notes: String, snapshot: SourceSnapshot? = null) =
        edit(key, snapshot) { it.copy(notes = notes.take(MAX_NOTES)) }

    fun addLine(key: String, text: String, id: String, snapshot: SourceSnapshot? = null) {
        val clean = text.trim().take(MAX_LINE)
        if (clean.isEmpty()) return
        edit(key, snapshot) { if (it.checklist.size >= MAX_LINES) it else it.copy(checklist = it.checklist + ChecklistLine(id, clean)) }
    }

    fun tickLine(key: String, id: String) = edit(key) { e -> e.copy(checklist = e.checklist.map { if (it.id == id) it.copy(done = !it.done) else it }) }

    fun removeLine(key: String, id: String) = edit(key) { e -> e.copy(checklist = e.checklist.filterNot { it.id == id }) }

    fun linkTask(key: String, taskId: String, snapshot: SourceSnapshot? = null) =
        edit(key, snapshot) { if (taskId in it.taskIds || it.taskIds.size >= MAX_LINKS) it else it.copy(taskIds = it.taskIds + taskId) }

    fun unlinkTask(key: String, taskId: String) = edit(key) { it.copy(taskIds = it.taskIds - taskId) }

    fun linkRecord(key: String, ref: RecordRef, snapshot: SourceSnapshot? = null) =
        edit(key, snapshot) { if (ref in it.records || it.records.size >= MAX_LINKS) it else it.copy(records = it.records + ref) }

    fun unlinkRecord(key: String, ref: RecordRef) = edit(key) { it.copy(records = it.records - ref) }

    // Event and memory (REP-088): a LINK memory is attached to an event by its opaque id, in this document only. Neither record
    // is edited or deleted by the link, by changing the other, or by breaking it; a deleted memory just stops showing.

    /** The ids of the memories attached to the event under [key], oldest link first. */
    fun memoriesOf(key: String): List<String> = enrichmentOf(key)?.records.orEmpty().filter { it.module == RecordRef.MEMORY }.map { it.id }

    /** The event keys a memory is attached to (a PLAN series id, or a phone-calendar key). */
    fun eventsOfMemory(memoryId: String): List<String> = data.enrichments.filter { RecordRef(RecordRef.MEMORY, memoryId) in it.records }.map { it.key }

    fun linkMemory(key: String, memoryId: String, snapshot: SourceSnapshot? = null) = linkRecord(key, RecordRef(RecordRef.MEMORY, memoryId), snapshot)

    fun unlinkMemory(key: String, memoryId: String) = unlinkRecord(key, RecordRef(RecordRef.MEMORY, memoryId))

    /** Remembers what a source event looks like now, for the records that already exist for it (nothing is created). Clears "source unavailable". */
    fun seen(updates: Map<String, SourceSnapshot>) {
        if (updates.isEmpty()) return
        document.update { d ->
            d.copy(enrichments = d.enrichments.map { e -> updates[e.key]?.let { e.copy(seen = it, unavailable = false) } ?: e })
        }
    }

    /** Marks the enrichments of events that are gone from the calendar. Nothing is deleted. */
    fun markUnavailable(keys: Set<String>) {
        if (keys.isEmpty()) return
        document.update { d -> d.copy(enrichments = d.enrichments.map { if (it.key in keys) it.copy(unavailable = true) else it }) }
    }

    /** A PLAN event the calendar deleted (through the sync) keeps its enrichment as a kept note, with what it was. */
    fun keepAsUnavailable(key: String, snapshot: SourceSnapshot) {
        val e = data.enrichmentOf(key) ?: return
        if (e.isEmpty && peopleOf(key).isEmpty()) return
        document.update { d -> d.copy(enrichments = d.enrichments.map { if (it.key == key) it.copy(seen = snapshot, unavailable = true) else it }) }
    }

    /** Kept notes: enrichments whose source event is gone, newest day first. */
    fun kept(): List<EventEnrichment> = data.enrichments.filter { it.unavailable }.sortedByDescending { it.seen?.day.orEmpty() }

    /** Deletes an enrichment for good, with its people: only ever on the player's own tap. */
    fun discard(key: String) = document.update { d -> d.copy(enrichments = d.enrichments.filterNot { it.key == key }, eventPeople = d.eventPeople.filterNot { it.eventId == key }) }

    fun setLinkDates(on: Boolean) = document.update { it.copy(linkDatesInCalendar = on) }

    fun setLinkFollowUps(on: Boolean) = document.update { it.copy(linkFollowUpsInCalendar = on) }

    fun setCalendarSync(state: CalendarSyncState) = document.update { it.copy(calendarSync = state) }

    /** The provider ids of PLAN events written to the phone's calendar: the PLAN calendar hides these copies so each shows once. */
    fun syncedProviderIds(): Set<Long> = data.calendarSync.links.map { it.providerId }.toSet()

    fun resetAll() = document.update { IntegrationData() }

    companion object {
        const val MAX_PEOPLE = 12
        const val MAX_LINKS = 12
        const val MAX_LINES = 30
        const val MAX_LINE = 200
        const val MAX_NOTES = 4_000

        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

        fun prune(d: IntegrationData, eventIds: Set<String>, personIds: Set<String>, taskIds: Set<String>? = null): IntegrationData {
            // A phone-calendar event's key is never in PLAN's events: its people and notes stay until the player discards them (PLAN 8.4).
            val live = { key: String -> key in eventIds || EventKeys.isDevice(key) || d.enrichmentOf(key)?.unavailable == true }
            val kept = d.eventPeople.mapNotNull { link ->
                if (!live(link.eventId)) null
                else link.personIds.filter { it in personIds }.takeIf { it.isNotEmpty() }?.let { link.copy(personIds = it) }
            }
            val notes = d.enrichments.mapNotNull { e ->
                if (!live(e.key)) null
                else if (taskIds == null) e
                else e.copy(taskIds = e.taskIds.filter { it in taskIds }).takeIf { !it.isEmpty || it.unavailable }
            }
            return if (kept == d.eventPeople && notes == d.enrichments) d else d.copy(eventPeople = kept, enrichments = notes)
        }
    }
}
