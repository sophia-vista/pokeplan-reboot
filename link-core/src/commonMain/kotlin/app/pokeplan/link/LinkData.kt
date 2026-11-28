package app.pokeplan.link

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class Person(
    val id: String,
    val name: String,
    val preferred: String = "",
    val phone: String = "",
    val email: String = "",
    val organization: String = "",
    val role: String = "",
    val address: String = "",
    val category: String = "",
    val tags: List<String> = emptyList(),
    val favorite: Boolean = false,
    val createdMillis: Long = 0,
    val updatedMillis: Long = 0,
    val sourceId: String = "",
    val isMe: Boolean = false,
    val profile: Profile = Profile(),
    val avatar: Int = 0,
    /** The groups this person belongs to ([LinkSettings.groups] ids); any number, optional. */
    val groupIds: List<String> = emptyList(),
)

/** A named group of people the player makes (Family, Climbing club). Never a score or a ranking. */
@Serializable
data class LinkGroup(val id: String, val name: String)

/**
 * A date as precisely as the player knows it: a full day, a month, a year, or nothing; [approximate] marks a
 * remembered "around then" without inventing a day. Month without a year and day without a month are not allowed;
 * everything blank is an unknown date.
 */
@Serializable
data class PartialDate(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
    val approximate: Boolean = false,
) {
    val known: Boolean get() = year != null || month != null || day != null
}

/** One memory in the archive: an event, gathering, trip, milestone or everyday experience worth keeping. */
@Serializable
data class Memory(
    val id: String,
    val title: String,
    val note: String = "",
    val date: PartialDate = PartialDate(),
    val time: String = "",
    val place: String = "",
    val personIds: List<String> = emptyList(),
    val eraIds: List<String> = emptyList(),
    val createdMillis: Long = 0,
    val updatedMillis: Long = 0,
)

/** One photo in a memory: the image is a base64 string (PNG, JPEG or WebP), stored once and referenced from its memory. */
@Serializable
data class MemoryPhoto(
    val id: String,
    val memoryId: String,
    val caption: String = "",
    val mime: String = "image/png",
    /** The image bytes as standard base64, with no `data:` prefix. */
    val data: String,
    val addedMillis: Long = 0,
    /** Quarter turns clockwise the player gave the photo (0 to 3); the stored image is never changed, so a turn can always be undone. */
    val rotation: Int = 0,
)

/** A life chapter the player makes by hand; [start] and [end] may each be partial or open. Eras may overlap. */
@Serializable
data class Era(
    val id: String,
    val title: String,
    val note: String = "",
    val start: PartialDate = PartialDate(),
    val end: PartialDate = PartialDate(),
    val createdMillis: Long = 0,
    val updatedMillis: Long = 0,
)

/**
 * An explicit connection between two people the player recorded (REP-084): "family", "colleague", the player's own word. It has no
 * direction (the one word is shown on both profiles), is never inferred, and is never a score. [aId] and [bId] are two different people.
 */
@Serializable
data class PersonConnection(val id: String, val aId: String, val bId: String, val label: String, val createdMillis: Long = 0) {
    fun involves(personId: String): Boolean = aId == personId || bId == personId
    fun otherThan(personId: String): String = if (aId == personId) bId else aId
}

@Serializable
data class PersonNote(val id: String, val personId: String, val text: String, val atMillis: Long = 0)

@Serializable
enum class DateKind(val label: String) { BIRTHDAY("Birthday"), ANNIVERSARY("Anniversary"), MILESTONE("Milestone"), CUSTOM("Date") }

@Serializable
data class ImportantDate(
    val id: String,
    val kind: DateKind,
    val title: String,
    val month: Int,
    val day: Int,
    val year: Int? = null,
    val annual: Boolean = true,
    val personId: String? = null,
    val note: String = "",
    val createdMillis: Long = 0,
    val remindDays: Int? = null,
)

@Serializable
enum class FollowUpStatus { OPEN, COMPLETED, CANCELLED }

@Serializable
data class FollowUp(
    val id: String,
    val title: String,
    val personId: String? = null,
    val due: LocalDate? = null,
    val note: String = "",
    val status: FollowUpStatus = FollowUpStatus.OPEN,
    val createdMillis: Long = 0,
    val updatedMillis: Long = 0,
    val completedMillis: Long? = null,
    val completedDay: LocalDate? = null,
    val paid: Boolean = false,
    val converted: Boolean = false,
    val remind: Boolean = false,
)

enum class FollowUpState(val label: String) { UPCOMING("Upcoming"), DUE("Due"), COMPLETED("Completed"), CANCELLED("Cancelled") }

@Serializable
data class LinkSettings(
    val postcardTrail: Boolean = true,
    val safari: Boolean = true,
    val tutorialSeen: Boolean = false,
    val categories: List<String> = emptyList(),
    /** Set once the one-time repair that gives the main category back to people a group replaced it for has run (0.243.0). */
    val categoriesRestored: Boolean = false,
    val groups: List<LinkGroup> = emptyList(),
    /** The player's own arrangement of people (ids), used by the "My order" sort; people not listed follow by name. */
    val order: List<String> = emptyList(),
    val reminders: Boolean = false,
    val reminderHour: Int = 9,
    val reminderNames: Boolean = false,
    /** The slambook question sets the player customised, one per category; a category without one uses its starting set. */
    val questionSets: List<QuestionSet> = emptyList(),
)

@Serializable
data class LinkData(
    val people: List<Person> = emptyList(),
    val notes: List<PersonNote> = emptyList(),
    val dates: List<ImportantDate> = emptyList(),
    val followUps: List<FollowUp> = emptyList(),
    val memories: List<Memory> = emptyList(),
    val eras: List<Era> = emptyList(),
    val photos: List<MemoryPhoto> = emptyList(),
    val connections: List<PersonConnection> = emptyList(),
    val settings: LinkSettings = LinkSettings(),
)

enum class LinkKinds(val label: String) {
    PEOPLE("People and their notes"),
    DATES("Important dates"),
    FOLLOW_UPS("Follow-ups"),
    MEMORIES("Memories and eras"),
}

/**
 * What a search result is. The order is the order the results are grouped in. [MEMORY], [ERA] and [PHOTO] came with the archive search
 * (Sprint 2): a [PHOTO] hit is a caption, and its id is the id of the memory the photo belongs to, so it opens that memory.
 */
enum class HitKind(val label: String, val plural: String) {
    PERSON("Person", "People"), NOTE("Note", "Notes"), MEMORY("Memory", "Memories"), ERA("Chapter", "Chapters"),
    PHOTO("Photo caption", "Photo captions"), DATE("Date", "Dates"), FOLLOW_UP("Follow-up", "Follow-ups"),
}

data class Hit(val kind: HitKind, val id: String, val title: String, val detail: String = "")

data class Upcoming(val date: ImportantDate, val day: LocalDate, val daysAway: Int, val years: Int?)
