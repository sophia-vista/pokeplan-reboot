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
    val groups: List<LinkGroup> = emptyList(),
    /** The player's own arrangement of people (ids), used by the "My order" sort; people not listed follow by name. */
    val order: List<String> = emptyList(),
    val reminders: Boolean = false,
    val reminderHour: Int = 9,
    val reminderNames: Boolean = false,
)

@Serializable
data class LinkData(
    val people: List<Person> = emptyList(),
    val notes: List<PersonNote> = emptyList(),
    val dates: List<ImportantDate> = emptyList(),
    val followUps: List<FollowUp> = emptyList(),
    val settings: LinkSettings = LinkSettings(),
)

enum class LinkKinds(val label: String) {
    PEOPLE("People and their notes"),
    DATES("Important dates"),
    FOLLOW_UPS("Follow-ups"),
}

enum class HitKind(val label: String) { PERSON("Person"), NOTE("Note"), DATE("Date"), FOLLOW_UP("Follow-up") }

data class Hit(val kind: HitKind, val id: String, val title: String, val detail: String = "")

data class Upcoming(val date: ImportantDate, val day: LocalDate, val daysAway: Int, val years: Int?)
