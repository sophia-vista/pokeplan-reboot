package app.pokeplan.heal

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/**
 * Where a record came from (HEAL.md section 11.1). Only [MANUAL] is produced today: no provider is connected, so nothing is imported,
 * derived or estimated yet; the other kinds exist so a record never has to be migrated when one is.
 */
@Serializable
enum class SourceKind { MANUAL, IMPORTED, DERIVED, ESTIMATED }

/** The kinds of activity a manual entry offers (HEAL.md section 3). */
@Serializable
enum class ActivityType(val label: String) {
    WALK("Walk"), RUN("Run"), CYCLE("Cycle"), SWIM("Swim"), STRENGTH("Strength"), STRETCH("Stretch or mobility"), OTHER("Other"),
}

/**
 * One activity session the user logged: a type, when it started and how long it lasted are enough; distance and a note are optional
 * (HEAL.md section 3). [day] is the local day it started on, fixed when it was saved, so a later change of time zone leaves history where it was.
 * [paid] is set once when Field Research paid for it, and never cleared, so editing or deleting a record can not pay it again.
 */
@Serializable
data class ActivityRecord(
    val id: String,
    val type: ActivityType,
    val startMillis: Long,
    val minutes: Int,
    val day: LocalDate,
    val distanceMeters: Int? = null,
    val note: String = "",
    val source: SourceKind = SourceKind.MANUAL,
    val createdMillis: Long = startMillis,
    val paid: Boolean = false,
) {
    val endMillis: Long get() = startMillis + minutes * 60_000L
}

/** One drink: only a volume, and an optional name for it. Not every drink counts the same, so HEAL never says it hydrated you. */
@Serializable
data class HydrationEntry(
    val id: String,
    val atMillis: Long,
    val day: LocalDate,
    val ml: Int,
    val drink: String = "",
    val source: SourceKind = SourceKind.MANUAL,
    val paid: Boolean = false,
)

/** One finished night's sleep (HEAL.md section 6). [day] is the day the user woke on. Stages are not stored: nothing here infers them. */
@Serializable
data class SleepRecord(
    val id: String,
    val startMillis: Long,
    val endMillis: Long,
    val day: LocalDate,
    val note: String = "",
    val source: SourceKind = SourceKind.MANUAL,
) {
    val minutes: Int get() = ((endMillis - startMillis) / 60_000L).toInt()
}

@Serializable
enum class MeasureType(val label: String, val units: List<String>) {
    WEIGHT("Weight", listOf("kg", "lb")),
    BODY_FAT("Body fat", listOf("%")),
    WAIST("Waist", listOf("cm", "in")),
    HIP("Hip", listOf("cm", "in")),
    OTHER("Other", listOf("")),
}

/** One body measurement with the unit it was entered in; a manual correction is a new record, never an edit of an imported one (section 7). */
@Serializable
data class Measurement(
    val id: String,
    val type: MeasureType,
    val value: Double,
    val unit: String,
    val atMillis: Long,
    val day: LocalDate,
    /** Only for [MeasureType.OTHER]: what the user called it. */
    val label: String = "",
    val method: String = "",
    val source: SourceKind = SourceKind.MANUAL,
)

@Serializable
enum class GoalKind(val label: String, val unit: String) {
    WEEKLY_ACTIVE_MINUTES("Active minutes a week", "min"),
    WEEKLY_ACTIVE_DAYS("Active days a week", "days"),
    DAILY_HYDRATION("Drinks a day (ml)", "ml"),
}

@Serializable
enum class GoalStatus { ACTIVE, PAUSED, COMPLETED, ARCHIVED }

/** An optional target (section 9). Pausing or archiving a goal never touches the records it counts; missing a week costs nothing. */
@Serializable
data class HealGoal(
    val id: String,
    val kind: GoalKind,
    val target: Int,
    val startDay: LocalDate,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val createdMillis: Long = 0,
)

/**
 * HEAL's preferences. Field Research (the reward) is **off** until the player turns it on (HEAL.md section 12.4) and can be turned off again without
 * touching any record. Reminders are all off by default (section 13): hydration is the only source built, up to [MAX_REMINDERS] times a day.
 */
@Serializable
data class HealSettings(
    val fieldResearch: Boolean = false,
    val hydrationTargetMl: Int? = null,
    val hydrationReminders: List<LocalTime> = emptyList(),
    val showMeasurementsOnToday: Boolean = false,
    val weightUnit: String = "kg",
    val tutorialSeen: Boolean = false,
) {
    companion object { const val MAX_REMINDERS = 3 }
}

/**
 * Everything HEAL keeps on this device, as one document that `HealTables` takes apart into one table family per kind of record
 * (so each can be read, exported or erased on its own). Never holds anything about Pokémon or rewards.
 */
@Serializable
data class HealData(
    val activities: List<ActivityRecord> = emptyList(),
    val hydration: List<HydrationEntry> = emptyList(),
    val sleep: List<SleepRecord> = emptyList(),
    val measurements: List<Measurement> = emptyList(),
    val goals: List<HealGoal> = emptyList(),
    val settings: HealSettings = HealSettings(),
)

/** The kinds of private record, each kept in its own table family (`heal_activity`, ...); the names are stable. */
enum class HealKinds(val field: String, val label: String) {
    ACTIVITY("activities", "Activity"),
    HYDRATION("hydration", "Hydration"),
    SLEEP("sleep", "Sleep"),
    MEASUREMENTS("measurements", "Body measurements"),
    GOALS("goals", "Goals"),
}
