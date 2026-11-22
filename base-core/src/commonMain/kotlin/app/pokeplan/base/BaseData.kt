package app.pokeplan.base

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
enum class RepeatKind(val label: String, val unit: String) {
    ONCE("One time", ""), DAILY("Every day", "day"), WEEKLY("Every week", "week"), MONTHLY("Every month", "month"), YEARLY("Every year", "year"),
}

@Serializable
data class Recurrence(val kind: RepeatKind = RepeatKind.ONCE, val every: Int = 1, val weekdays: List<Int> = emptyList())

@Serializable
enum class RecordStatus { ACTIVE, PAUSED, ARCHIVED }

@Serializable
data class BaseArea(val id: String, val name: String, val archived: Boolean = false)

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
    val remind: Boolean = false,
)

@Serializable
enum class EntryStatus { DONE, SKIPPED, DEFERRED }

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

enum class ChoreState(val label: String) { OVERDUE("Overdue"), DUE("Due today"), UPCOMING("Upcoming"), ANYTIME("Whenever"), DONE("Done"), SKIPPED("Skipped"), PAUSED("Paused"), ARCHIVED("Archived") }

data class ChoreView(val chore: Chore, val state: ChoreState, val occurrence: LocalDate?, val dueDay: LocalDate?, val entry: ChoreEntry?)

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
    val remind: Boolean = false,
)

@Serializable
data class StockAdjustment(val id: String, val supplyId: String, val beforeMilli: Long?, val afterMilli: Long?, val reason: String = "", val atMillis: Long = 0)

@Serializable
data class RestockEntry(val id: String, val name: String, val supplyId: String? = null, val createdMillis: Long = 0)

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

@Serializable
data class MaintenanceSchedule(
    val id: String,
    val title: String,
    val assetId: String? = null,
    val instructions: String = "",
    val recurrence: Recurrence = Recurrence(),
    val policy: SchedulePolicy = SchedulePolicy.FIXED,
    val firstDue: LocalDate,
    val nextDue: LocalDate,
    val status: RecordStatus = RecordStatus.ACTIVE,
    val createdMillis: Long = 0,
    val remind: Boolean = false,
)

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
    val remind: Boolean = false,
)

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

@Serializable
data class BaseSettings(
    val progression: Boolean = true,
    val tutorialSeen: Boolean = false,
    val units: List<String> = BaseRules.STARTING_UNITS,
    val hidden: List<String> = emptyList(),
    val areasSeeded: Boolean = false,
    val reminders: Boolean = false,
    val reminderHour: Int = 9,
    val maintenanceLeadDays: Int = 1,
    val warrantyLeadDays: Int = 30,
    val reminderNames: Boolean = false,
)

@Serializable
data class BaseData(
    val areas: List<BaseArea> = emptyList(),
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
    val settings: BaseSettings = BaseSettings(),
    val room: List<Placement> = emptyList(),
)

enum class BaseKinds(val label: String) {
    AREAS("Areas"),
    CHORES("Chores and their history"),
    SUPPLIES("Supplies and the restocking list"),
    ASSETS("Belongings, maintenance and warranties"),
    REFERENCES("Reference notes"),
    ROOM("Secret Base room"),
}
