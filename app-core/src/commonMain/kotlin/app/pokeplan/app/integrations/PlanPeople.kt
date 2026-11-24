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
    fun prune(eventIds: Set<String>, personIds: Set<String>) {
        val next = prune(data, eventIds, personIds)
        if (next != data) document.update { next }
    }

    fun setLinkDates(on: Boolean) = document.update { it.copy(linkDatesInCalendar = on) }

    fun setLinkFollowUps(on: Boolean) = document.update { it.copy(linkFollowUpsInCalendar = on) }

    fun setCalendarSync(state: CalendarSyncState) = document.update { it.copy(calendarSync = state) }

    /** The provider ids of PLAN events written to the phone's calendar: the PLAN calendar hides these copies so each shows once. */
    fun syncedProviderIds(): Set<Long> = data.calendarSync.links.map { it.providerId }.toSet()

    fun resetAll() = document.update { IntegrationData() }

    companion object {
        const val MAX_PEOPLE = 12

        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

        fun prune(d: IntegrationData, eventIds: Set<String>, personIds: Set<String>): IntegrationData {
            val kept = d.eventPeople.mapNotNull { link ->
                if (link.eventId !in eventIds) null
                else link.personIds.filter { it in personIds }.takeIf { it.isNotEmpty() }?.let { link.copy(personIds = it) }
            }
            return if (kept == d.eventPeople) d else d.copy(eventPeople = kept)
        }
    }
}
