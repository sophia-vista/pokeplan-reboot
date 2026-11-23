package app.pokeplan.heal

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

@Serializable
enum class SourceKind { MANUAL, IMPORTED }

@Serializable
data class Provenance(val provider: String, val app: String, val externalId: String, val importedMillis: Long, val possibleDuplicateOf: String? = null)

@Serializable
enum class ActivityType(val label: String) {
    WALK("Walk"), RUN("Run"), CYCLE("Cycle"), SWIM("Swim"), STRENGTH("Strength"), STRETCH("Stretch or mobility"), OTHER("Other"),
}

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

@Serializable
data class Measurement(
    val id: String,
    val type: MeasureType,
    val value: Double,
    val unit: String,
    val atMillis: Long,
    val day: LocalDate,
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

@Serializable
data class HealGoal(
    val id: String,
    val kind: GoalKind,
    val target: Int,
    val startDay: LocalDate,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val createdMillis: Long = 0,
)

@Serializable
enum class MedicationStatus(val label: String) {
    ACTIVE("Taking it"), PAUSED("Paused"), STOPPED("Stopped"),
}

/**
 * A private reference the player typed in themselves: a medication or supplement, its schedule as times of the day
 * (empty means "when I take one", so no reminder), and optionally how many doses they have left. HEAL never decides,
 * checks or suggests a dose; adherence logging is optional and never pays.
 */
@Serializable
data class Medication(
    val id: String,
    val name: String,
    val strength: String = "",
    val instructions: String = "",
    val times: List<LocalTime> = emptyList(),
    val startDay: LocalDate,
    val endDay: LocalDate? = null,
    val status: MedicationStatus = MedicationStatus.ACTIVE,
    val note: String = "",
    val remind: Boolean = false,
    val supplyLeft: Int? = null,
    val refillAt: Int? = null,
    val createdMillis: Long = 0,
)

@Serializable
enum class DoseStatus(val label: String) {
    TAKEN("Taken"), SKIPPED("Skipped"),
}

/** One adherence entry for one scheduled time of one medication. Optional, private, and never a reward. */
@Serializable
data class MedicationDose(
    val id: String,
    val medicationId: String,
    val day: LocalDate,
    val time: LocalTime,
    val status: DoseStatus,
    val atMillis: Long,
)

@Serializable
data class HealSettings(
    val fieldResearch: Boolean = true,
    val hydrationTargetMl: Int? = null,
    val hydrationReminders: List<LocalTime> = emptyList(),
    val showMeasurementsOnToday: Boolean = false,
    val weightUnit: String = "kg",
    val tutorialSeen: Boolean = false,
    val healthKinds: Set<HealthKind> = emptySet(),
    val healthLastSyncMillis: Long = 0,
    val huaweiEveryDays: Int? = null,
    val huaweiRemindAt: LocalTime = LocalTime(21, 0),
    val huaweiLastReadMillis: Long = 0,
    val paidMarks: Set<String> = emptySet(),
) {
    companion object { const val MAX_REMINDERS = 3; const val MARK_DAYS = 45 }
}

@Serializable
data class HealData(
    val activities: List<ActivityRecord> = emptyList(),
    val hydration: List<HydrationEntry> = emptyList(),
    val sleep: List<SleepRecord> = emptyList(),
    val measurements: List<Measurement> = emptyList(),
    val goals: List<HealGoal> = emptyList(),
    val settings: HealSettings = HealSettings(),
    val steps: List<StepsRecord> = emptyList(),
    val vitals: List<VitalReading> = emptyList(),
    val medications: List<Medication> = emptyList(),
    val doses: List<MedicationDose> = emptyList(),
)

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

enum class HealKinds(val fields: List<String>, val label: String) {
    ACTIVITY(listOf("activities"), "Activity"),
    HYDRATION(listOf("hydration"), "Hydration"),
    SLEEP(listOf("sleep"), "Sleep"),
    MEASUREMENTS(listOf("measurements"), "Body measurements"),
    GOALS(listOf("goals"), "Goals"),
    STEPS(listOf("steps"), "Steps"),
    VITALS(listOf("vitals"), "Heart, oxygen, temperature, stress and sleep score"),
    MEDICATIONS(listOf("medications", "doses"), "Medications and doses"),
}
