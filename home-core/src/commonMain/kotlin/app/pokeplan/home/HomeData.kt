package app.pokeplan.home

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Everything HOME keeps is a household record (docs/pokelife `MODULES/HOME.md` section 5): areas, chores and what was done about them, supplies,
 * assets, maintenance, warranties and reference notes. Derived facts (a chore is overdue, a supply is low, a service is due) are never stored: they
 * are worked out from these records and the date, by [HomeRules].
 */
@Serializable
enum class RepeatKind(val label: String, val unit: String) {
    ONCE("One time", ""), DAILY("Every day", "day"), WEEKLY("Every week", "week"), MONTHLY("Every month", "month"), YEARLY("Every year", "year"),
}

/**
 * How a chore or a maintenance schedule repeats, from its first date (HOME.md 4.2): daily, on chosen weekdays, weekly, monthly or yearly, every
 * [every] units. Nothing cron-like; a month shorter than the first date's day uses its last day. [weekdays] are ISO numbers (1 Monday to 7 Sunday)
 * and only mean something for [RepeatKind.WEEKLY]; empty means the weekday of the first date.
 */
@Serializable
data class Recurrence(val kind: RepeatKind = RepeatKind.ONCE, val every: Int = 1, val weekdays: List<Int> = emptyList())

@Serializable
enum class RecordStatus { ACTIVE, PAUSED, ARCHIVED }

/** An optional label for where something is (HOME.md 4.1), never a hierarchy. Archiving keeps every record that names it. */
@Serializable
data class HomeArea(val id: String, val name: String, val archived: Boolean = false)

/**
 * A household responsibility (HOME.md 4.2). Only the title is required. [firstDue] is the date of the first occurrence (null: an unscheduled chore
 * that is done once whenever); a repeating chore needs one. What happened to each occurrence is in [ChoreEntry], so editing a chore never rewrites history.
 */
@Serializable
data class Chore(
    val id: String,
    val title: String,
    val instructions: String = "",
    val areaId: String? = null,
    val category: String = "",
    val recurrence: Recurrence = Recurrence(),
    val firstDue: LocalDate? = null,
    val minutes: Int? = null,
    val status: RecordStatus = RecordStatus.ACTIVE,
    val createdMillis: Long = 0,
    val updatedMillis: Long = 0,
)

@Serializable
enum class EntryStatus { DONE, SKIPPED, DEFERRED }

/**
 * What the user did about one occurrence of a chore: done, skipped, or put off to [deferTo]. The occurrence is named by [dueDay] (for an unscheduled
 * chore, the day it was done), so one occurrence has at most one entry and an undo is just removing it. [paid] is set once Habitat Discovery paid.
 */
@Serializable
data class ChoreEntry(
    val id: String,
    val choreId: String,
    val dueDay: LocalDate,
    val status: EntryStatus,
    val day: LocalDate,
    val atMillis: Long = 0,
    val note: String = "",
    val deferTo: LocalDate? = null,
    val paid: Boolean = false,
)

/** A chore's state for today. */
enum class ChoreState(val label: String) { OVERDUE("Overdue"), DUE("Due today"), UPCOMING("Upcoming"), ANYTIME("Whenever"), DONE("Done"), SKIPPED("Skipped"), PAUSED("Paused"), ARCHIVED("Archived") }

/**
 * One chore as it stands today. [occurrence] names the occurrence that matters now (the key an entry is saved under); [dueDay] is when it is due, which
 * differs from it only for one put off. [entry] is what was done about it, if anything.
 */
data class ChoreView(val chore: Chore, val state: ChoreState, val occurrence: LocalDate?, val dueDay: LocalDate?, val entry: ChoreEntry?)

/**
 * A consumable (HOME.md 4.3). [quantityMilli] is in thousandths of the [unit] (so 1.5 kg is 1500 and a count of 3 is 3000: no floating point), or
 * null when unknown, which is **not** zero and is never low. [lowMilli] is the optional threshold; with none nothing is inferred. [lowDismissed]
 * quiets the current low-stock notice and is cleared by the next quantity change.
 */
@Serializable
data class Supply(
    val id: String,
    val name: String,
    val category: String = "",
    val quantityMilli: Long? = null,
    val unit: String = "",
    val lowMilli: Long? = null,
    val areaId: String? = null,
    val notes: String = "",
    val lowDismissed: Boolean = false,
    val archived: Boolean = false,
    val createdMillis: Long = 0,
    val updatedMillis: Long = 0,
)

/** A light record of one change of quantity (HOME.md 4.3), bounded by [HomeRules.ADJUSTMENTS_KEPT]. Not a consumption ledger. */
@Serializable
data class StockAdjustment(val id: String, val supplyId: String, val beforeMilli: Long?, val afterMilli: Long?, val reason: String = "", val atMillis: Long = 0)

/** A manual entry on the restocking list; [supplyId] names a supply the user put there by hand. Low supplies are on the list without an entry. */
@Serializable
data class RestockEntry(val id: String, val name: String, val supplyId: String? = null, val createdMillis: Long = 0)

/**
 * A durable thing in the household (HOME.md 4.4): an appliance, a piece of furniture, a tool. Assets are HOME's durable inventory and its maintenance
 * subjects at once, so a thing has one identity. The [serial] is sensitive reference data and never leaves HOME. Cost and price are not kept here.
 */
@Serializable
data class Asset(
    val id: String,
    val name: String,
    val category: String = "",
    val brand: String = "",
    val model: String = "",
    val serial: String = "",
    val purchaseDay: LocalDate? = null,
    val areaId: String? = null,
    val notes: String = "",
    val archived: Boolean = false,
    val createdMillis: Long = 0,
    val updatedMillis: Long = 0,
)

@Serializable
enum class SchedulePolicy(val label: String) { FIXED("On the calendar"), AFTER_DONE("After it is done") }

/**
 * A recurring (or one-time) piece of maintenance (HOME.md 4.5), for an asset or for the household in general. [nextDue] is where the schedule stands;
 * finishing it records a [MaintenanceRecord] and moves [nextDue] by the [policy] without rewriting any past record.
 */
@Serializable
data class MaintenanceSchedule(
    val id: String,
    val title: String,
    val assetId: String? = null,
    val instructions: String = "",
    val recurrence: Recurrence = Recurrence(),
    val policy: SchedulePolicy = SchedulePolicy.FIXED,
    /** The date it started: the anchor a calendar schedule counts from, so a month-end date never drifts. */
    val firstDue: LocalDate,
    val nextDue: LocalDate,
    val status: RecordStatus = RecordStatus.ACTIVE,
    val createdMillis: Long = 0,
)

/** One piece of maintenance that was done. [cost] is informational text and never an accounting record or a transaction (HOME.md 4.5). */
@Serializable
data class MaintenanceRecord(
    val id: String,
    val title: String,
    val assetId: String? = null,
    val scheduleId: String? = null,
    val day: LocalDate,
    val notes: String = "",
    val cost: String = "",
    val atMillis: Long = 0,
    val paid: Boolean = false,
)

/** Warranty details the user typed in (HOME.md 4.6). Expiry is informational; nothing is inferred and expired ones are kept. */
@Serializable
data class Warranty(
    val id: String,
    val assetId: String,
    val provider: String = "",
    val coverage: String = "",
    val startDay: LocalDate? = null,
    val expiryDay: LocalDate? = null,
    val reference: String = "",
    val notes: String = "",
    val createdMillis: Long = 0,
)

/** A household note (HOME.md 4.7): a title, some text, and optionally an area or an asset. Not a wiki. */
@Serializable
data class Reference(
    val id: String,
    val title: String,
    val text: String = "",
    val category: String = "",
    val areaId: String? = null,
    val assetId: String? = null,
    val createdMillis: Long = 0,
    val updatedMillis: Long = 0,
)

/**
 * HOME's preferences. [progression] is Habitat Discovery (the reward): on by default because only an abstract event (a chore or a piece of
 * maintenance done) ever leaves HOME, and off at any time without touching a record.
 */
@Serializable
data class HomeSettings(
    val progression: Boolean = true,
    val tutorialSeen: Boolean = false,
    val units: List<String> = HomeRules.STARTING_UNITS,
    /** Overview sections the user folded away: "chores", "supplies", "maintenance". */
    val hidden: List<String> = emptyList(),
    /** Whether the starting areas were offered once (so deleting them all does not bring them back). */
    val areasSeeded: Boolean = false,
)

/** Everything HOME keeps on this device, as one document that `HomeTables` takes apart into one table family per kind of record. It holds nothing about Pokémon or rewards. */
@Serializable
data class HomeData(
    val areas: List<HomeArea> = emptyList(),
    val chores: List<Chore> = emptyList(),
    val entries: List<ChoreEntry> = emptyList(),
    val supplies: List<Supply> = emptyList(),
    val adjustments: List<StockAdjustment> = emptyList(),
    val restock: List<RestockEntry> = emptyList(),
    val assets: List<Asset> = emptyList(),
    val schedules: List<MaintenanceSchedule> = emptyList(),
    val maintenance: List<MaintenanceRecord> = emptyList(),
    val warranties: List<Warranty> = emptyList(),
    val references: List<Reference> = emptyList(),
    val settings: HomeSettings = HomeSettings(),
)

/** The kinds of private record, each kept in its own table family (`home_area`, ...); the names are stable. */
enum class HomeKinds(val label: String) {
    AREAS("Areas"),
    CHORES("Chores and their history"),
    SUPPLIES("Supplies and the restocking list"),
    ASSETS("Belongings, maintenance and warranties"),
    REFERENCES("Reference notes"),
}
