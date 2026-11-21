package app.pokeplan.link

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Everything LINK keeps is a record the user chose to write (docs/pokelife `MODULES/LINK.md` section 4): people, notes about them, important dates and
 * follow-ups. There is no score, no frequency and no inferred closeness anywhere in it, and nothing here is ever sent to a person. Derived facts (a
 * follow-up is due, a date is coming) are worked out from these records and the day by [LinkRules], never stored.
 */
@Serializable
data class Person(
    val id: String,
    val name: String,
    /** What the user calls them, if different (searchable). */
    val preferred: String = "",
    val phone: String = "",
    val email: String = "",
    val organization: String = "",
    val role: String = "",
    val address: String = "",
    /** The user's own relationship word ("family", "friend", ...), or empty. */
    val category: String = "",
    val tags: List<String> = emptyList(),
    /** A convenience for finding someone fast, never a ranking (LINK.md 2.1). */
    val favorite: Boolean = false,
    val createdMillis: Long = 0,
    val updatedMillis: Long = 0,
    /**
     * Where an imported person came from (LINK.md 6.1): the phone contact's lookup key, kept only so a later import can tell it has seen this contact.
     * Empty for a person the user typed. The phone's address book is never LINK's source of truth, and this never leaves LINK.
     */
    val sourceId: String = "",
    /** The one person who is the user (LINK.md 2.6): same profile as anyone, pinned first in the directory. At most one. */
    val isMe: Boolean = false,
    /** The slambook half: socials, likes, answers, personality and birth details (LINK.md 2.6). Lives in the person's JSON, so there is no schema step. */
    val profile: Profile = Profile(),
    /** The Pokémon the user chose as this person's picture (a species id, 0 for none). Cosmetic and private to LINK: it feeds nothing and never leaves. */
    val avatar: Int = 0,
)

/** A dated line of context about a person (LINK.md 2.2). Private to LINK: it never leaves in a notification or an event. */
@Serializable
data class PersonNote(val id: String, val personId: String, val text: String, val atMillis: Long = 0)

@Serializable
enum class DateKind(val label: String) { BIRTHDAY("Birthday"), ANNIVERSARY("Anniversary"), MILESTONE("Milestone"), CUSTOM("Date") }

/**
 * An important date (LINK.md 2.3): month and day, a year only when known, annual or once. [personId] is optional, so a date can stand alone.
 * A one-time date needs its year. February 29 is kept as it is and is observed on February 28 in a year that has no 29th ([LinkRules.observed]).
 */
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
    /** Days before the date a reminder goes off (one of [LinkRules.REMIND_PRESETS]; 0 is the day itself), or null for none. Off until the user chooses (LINK.md 5). */
    val remindDays: Int? = null,
)

@Serializable
enum class FollowUpStatus { OPEN, COMPLETED, CANCELLED }

/**
 * A lightweight follow-up (LINK.md 2.4): only the title is required. [due] is optional. [paid] says Postcard Trail paid for this one completion; it
 * stays set when the follow-up is reopened, so completing it again never pays a second time.
 */
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
    /**
     * Set the moment the user turns it into a PLAN task and never cleared: from then on PLAN owns the task, its completion and its reward, and LINK pays
     * nothing for it, even if the task is later deleted (LINK.md 2.4 and 9.2).
     */
    val converted: Boolean = false,
    /** Asks the shared reminders for one nudge on [due]. Off until the user turns it on; never once the follow-up is a Plan task (Plan is then the source). */
    val remind: Boolean = false,
)

/** What a follow-up is today, neutrally named (LINK.md 2.4): nothing here is a warning. */
enum class FollowUpState(val label: String) { UPCOMING("Upcoming"), DUE("Due"), COMPLETED("Completed"), CANCELLED("Cancelled") }

/** LINK's own settings. [postcardTrail] is the optional Pokémon feature (LINK.md 9.1): off never touches a record. */
@Serializable
data class LinkSettings(
    val postcardTrail: Boolean = true,
    /** Friend Safari (LINK.md 9.5): each person's small pool of Pokémon. Off here or with [postcardTrail] and no Safari is offered; no record is touched. */
    val safari: Boolean = true,
    val tutorialSeen: Boolean = false,
    /** Relationship words the user added beside the starting ones. */
    val categories: List<String> = emptyList(),
    /** The master switch for Link's reminders (LINK.md 5): **off** until the user switches it on or chooses the first reminder on a date (which switches it on), and even then a date or follow-up asks only when its own reminder is on. */
    val reminders: Boolean = false,
    /** The hour (0 to 23) a reminder goes off on its day. */
    val reminderHour: Int = 9,
    /** Whether a notification may say which date or follow-up after the phone is unlocked. The lock screen never does. */
    val reminderNames: Boolean = false,
)

/** Everything LINK keeps on this device as one document that `LinkTables` takes apart into one table family per kind of record. It holds nothing about Pokémon or rewards. */
@Serializable
data class LinkData(
    val people: List<Person> = emptyList(),
    val notes: List<PersonNote> = emptyList(),
    val dates: List<ImportantDate> = emptyList(),
    val followUps: List<FollowUp> = emptyList(),
    val settings: LinkSettings = LinkSettings(),
)

/** The kinds of private record, each kept in its own table family (`link_person`, ...); the names are stable. */
enum class LinkKinds(val label: String) {
    PEOPLE("People and their notes"),
    DATES("Important dates"),
    FOLLOW_UPS("Follow-ups"),
}

enum class HitKind(val label: String) { PERSON("Person"), NOTE("Note"), DATE("Date"), FOLLOW_UP("Follow-up") }

/** A search result: its kind, the record's id (a note's hit opens its person), a title and one line saying why it matched. */
data class Hit(val kind: HitKind, val id: String, val title: String, val detail: String = "")

/** An occurrence of an important date: the day it is observed and, for a date with a known year, the age or anniversary number it marks. */
data class Upcoming(val date: ImportantDate, val day: LocalDate, val daysAway: Int, val years: Int?)
