package app.pokeplan.app.integrations

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * What the player keeps privately about a calendar event (PLAN 8.4). Opaque ids only: this layer reads neither PLAN's tasks nor
 * another module's records, so each module stays separate. It lives in the `integrations` document, never in PLAN's events and
 * never in the phone's calendar, so a refresh of the provider's event, an edit in Google Calendar or a sync pass cannot touch it.
 */
@Serializable
data class EventEnrichment(
    val key: String,
    val notes: String = "",
    val checklist: List<ChecklistLine> = emptyList(),
    val taskIds: List<String> = emptyList(),
    val records: List<RecordRef> = emptyList(),
    /** What the source event last looked like, so a note whose event has gone can still say what it was about. */
    val seen: SourceSnapshot? = null,
    /** The source event is no longer in the calendar (deleted, or its calendar removed). Nothing here is deleted for that. */
    val unavailable: Boolean = false,
) {
    val isEmpty: Boolean get() = notes.isBlank() && checklist.isEmpty() && taskIds.isEmpty() && records.isEmpty()
}

@Serializable
data class ChecklistLine(val id: String, val text: String, val done: Boolean = false)

/** A record in another module, by module name and id; the title comes from the owning module when the UI shows it. */
@Serializable
data class RecordRef(val module: String, val id: String)

@Serializable
data class SourceSnapshot(val title: String, val day: String, val calendar: String = "")

/** The key an enrichment hangs on: a PLAN event is its series id; a phone-calendar event is its calendar and the provider's event id (shared by every occurrence of a series). */
object EventKeys {
    private const val DEVICE = "device:"

    fun device(calendarId: Long, providerEventId: Long) = "$DEVICE$calendarId:$providerEventId"

    /** The key of a phone-calendar event as the Calendar lists it (its id reads `eventId@begin`); null for anything else. */
    fun ofDeviceEvent(calendarId: Long, displayId: String): String? =
        displayId.substringBefore('@').toLongOrNull()?.takeIf { calendarId >= 0 }?.let { device(calendarId, it) }

    fun isDevice(key: String) = key.startsWith(DEVICE)

    /** (calendar id, provider event id) of a device key. */
    fun parse(key: String): Pair<Long, Long>? {
        if (!isDevice(key)) return null
        val parts = key.removePrefix(DEVICE).split(':')
        val cal = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val id = parts.getOrNull(1)?.toLongOrNull() ?: return null
        return cal to id
    }
}

/** Working out which enrichments have lost their source event, from a read of the calendar. */
object EventSources {
    data class Result(val seen: Map<String, SourceSnapshot>, val missing: Set<String>)

    /**
     * [loaded] is every device event just read (key to what it looks like); [range] the days that read covered; [shown] the calendars
     * it covered. An enrichment is **missing** only when it was last seen inside the range, its calendar was read, the event is not
     * among those loaded, and [exists] says the provider no longer has it (null from [exists] means "cannot tell": nothing is marked).
     * An event that merely moved to another day is therefore never taken for a deleted one.
     */
    fun reconcile(
        data: IntegrationData, loaded: Map<String, SourceSnapshot>, range: ClosedRange<LocalDate>, shown: Set<Long>,
        exists: (calendarId: Long, providerId: Long) -> Boolean?,
    ): Result {
        val seen = loaded.filter { (key, snap) -> data.enrichmentOf(key)?.let { it.seen != snap || it.unavailable } ?: false }
        val missing = data.enrichments.filter { e ->
            val ids = EventKeys.parse(e.key)
            ids != null && !e.unavailable && e.key !in loaded && ids.first in shown &&
                e.seen?.day?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { it in range } == true &&
                exists(ids.first, ids.second) == false
        }.map { it.key }.toSet()
        return Result(seen, missing)
    }
}

fun IntegrationData.enrichmentOf(key: String): EventEnrichment? = enrichments.firstOrNull { it.key == key }
