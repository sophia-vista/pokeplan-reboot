package app.pokeplan.heal

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/**
 * Where a record came from (HEAL.md section 11.1). [MANUAL] is what the player typed. [IMPORTED] came from Health Connect, read once on request (it carries a [Provenance]); [DERIVED] and [ESTIMATED]
 * are not produced yet and exist so a record never has to be migrated when one is.
 */
@Serializable
enum class SourceKind { MANUAL, IMPORTED, DERIVED, ESTIMATED }

/**
 * Where an imported record came from (INTEGRATIONS_AND_PRIVACY.md 5.1, HEAL.md 10.3 and 11.1): the [provider] ("healthconnect"), the [app] that wrote it into that provider (for
 * Health Connect, the writing app's package name, so the player can see which app a record really came from), the provider's own [externalId], when it was
 * [importedMillis], and, when it looked like a record already here, which one ([possibleDuplicateOf]; both are kept until the player chooses). A record the player wrote has none.
 */
@Serializable
data class Provenance(val provider: String, val app: String, val externalId: String, val importedMillis: Long, val possibleDuplicateOf: String? = null)

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
    val provenance: Provenance? = null,
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
    val provenance: Provenance? = null,
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
    val provenance: Provenance? = null,
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
    val provenance: Provenance? = null,
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
 * HEAL's preferences. Field Research (the reward) is **on** by default (owner, 8 November 2026: every acquisition method starts on; HEAL.md section 12.4 said opt-in) and can be turned off without
 * touching any record. Reminders are all off by default (section 13): hydration is the only source built, up to [MAX_REMINDERS] times a day.
 */
@Serializable
data class HealSettings(
    val fieldResearch: Boolean = true,
    val hydrationTargetMl: Int? = null,
    val hydrationReminders: List<LocalTime> = emptyList(),
    val showMeasurementsOnToday: Boolean = false,
    val weightUnit: String = "kg",
    val tutorialSeen: Boolean = false,
    /** The Health Connect record types the player chose to import (a subset of [HealthKind]); empty means not connected. Only a choice: access itself is Android's, and is rechecked each time. */
    val healthKinds: Set<HealthKind> = emptySet(),
    /** When Health Connect was last read successfully, and what it returned then ("last successful sync", HEAL.md 10.3); 0 when never. */
    val healthLastSyncMillis: Long = 0,
    /** How often to remind the player to read Huawei Health, in days (1 every night to 7 once a week); null when that reminder is off (INTEGRATIONS_AND_PRIVACY.md 5.2.2). */
    val huaweiEveryDays: Int? = null,
    /** The time of day that reminder goes off. */
    val huaweiRemindAt: LocalTime = LocalTime(21, 0),
    /** When Huawei Health was last read (0 when never); the reminder counts its days from here. */
    val huaweiLastReadMillis: Long = 0,
    /**
     * What Field Research paid for steps and imports, as `kind|day|n` marks ("steps|2026-11-14|3": the third step amount of that day; "import|2026-11-14|SLEEP": that family's read that
     * day). Kept here, sealed with the other settings, and not in the shared save, so no day or count of steps sits in the clear; they survive deleting a record, so
     * deleting and reading it again can not pay twice. Marks older than [MARK_DAYS] days are dropped.
     */
    val paidMarks: Set<String> = emptySet(),
) {
    companion object { const val MAX_REMINDERS = 3; const val MARK_DAYS = 45 }
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
    /** Daily step counts (0.186.0), read from outside; one per day and provider. */
    val steps: List<StepsRecord> = emptyList(),
    /** Heart rate, resting heart rate, SpO2, skin temperature, Huawei's stress and sleep score (0.186.0), read from outside. */
    val vitals: List<VitalReading> = emptyList(),
)

/**
 * One day's step count, read from outside (Huawei Health's Health page or its step notification). There is one per [day] and provider: a later read of the same day
 * replaces [steps] with the larger count (a day's steps only grow), keeping the record's id. [paidBuckets] is how many [HealRules.STEPS_PER_REWARD] amounts Field
 * Research has paid for this day, set once and never lowered, so editing, deleting or reading again can not pay one twice. History only: HEAL never says whether a count is enough.
 */
@Serializable
data class StepsRecord(
    val id: String,
    val day: LocalDate,
    val steps: Int,
    val updatedMillis: Long,
    val paidBuckets: Int = 0,
    val source: SourceKind = SourceKind.IMPORTED,
    val provenance: Provenance? = null,
)

/**
 * The readings HEAL keeps beside the body measurements, only ever read from outside and shown as the provider showed them: HEAL never calls one good or bad, high or low, and
 * [STRESS] and [SLEEP_SCORE] are Huawei's own number and judgement, not HEAL's. [unit] is what a value is shown with.
 */
@Serializable
enum class VitalType(val label: String, val unit: String) {
    HEART_RATE("Heart rate", "bpm"),
    RESTING_HEART_RATE("Resting heart rate", "bpm"),
    SPO2("Blood oxygen", "%"),
    SKIN_TEMPERATURE("Skin temperature", "°C"),
    STRESS("Huawei stress", ""),
    SLEEP_SCORE("Huawei sleep score", ""),

    // From Huawei Health's All data pages (history, one row per day; 21 November 2026): the page gives a range or a length, so each end is its own reading.
    SPO2_LOW("Blood oxygen, lowest", "%"),
    SPO2_HIGH("Blood oxygen, highest", "%"),
    SKIN_TEMPERATURE_LOW("Skin temperature, lowest", "°C"),
    SKIN_TEMPERATURE_HIGH("Skin temperature, highest", "°C"),
    SLEEP_LENGTH("Sleep length", "min"),

    // Heart rate is kept as a range per day (owner, 21 November 2026), not as one entry per reading. [HEART_RATE] stays for what was saved before and for other providers.
    HEART_RATE_LOW("Heart rate, lowest", "bpm"),
    HEART_RATE_HIGH("Heart rate, highest", "bpm"),
}

/** One such reading. [atMillis] is when PokéLife read it (the Health page shows no time of its own); [day] is that local day, fixed when it was saved. */
@Serializable
data class VitalReading(
    val id: String,
    val type: VitalType,
    val value: Double,
    val atMillis: Long,
    val day: LocalDate,
    val source: SourceKind = SourceKind.IMPORTED,
    val provenance: Provenance? = null,
)

/** The kinds of private record, each kept in its own table family (`heal_activity`, ...); the names are stable. */
enum class HealKinds(val field: String, val label: String) {
    ACTIVITY("activities", "Activity"),
    HYDRATION("hydration", "Hydration"),
    SLEEP("sleep", "Sleep"),
    MEASUREMENTS("measurements", "Body measurements"),
    GOALS("goals", "Goals"),
    STEPS("steps", "Steps"),
    VITALS("vitals", "Heart, oxygen, temperature, stress and sleep score"),
}
