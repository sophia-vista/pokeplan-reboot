package app.pokeplan.heal

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlin.math.abs
import kotlin.math.roundToInt

sealed interface Check {
    data object Ok : Check
    data class Refused(val reason: String) : Check
}

object HealRules {

    const val ACTIVITY_MIN_MINUTES = 1
    const val ACTIVITY_MAX_MINUTES = 24 * 60
    const val MAX_DISTANCE_METERS = 1_000_000
    const val HYDRATION_MIN_ML = 1
    const val HYDRATION_MAX_ML = 5_000
    const val SLEEP_MAX_MINUTES = 24 * 60
    const val NOTE_MAX = 500

    fun checkActivity(minutes: Int, distanceMeters: Int?): Check = when {
        minutes < ACTIVITY_MIN_MINUTES -> Check.Refused("Enter how long it lasted, at least a minute.")
        minutes > ACTIVITY_MAX_MINUTES -> Check.Refused("An activity can last at most a day.")
        distanceMeters != null && distanceMeters !in 0..MAX_DISTANCE_METERS -> Check.Refused("That distance looks wrong.")
        else -> Check.Ok
    }

    fun checkHydration(ml: Int): Check =
        if (ml in HYDRATION_MIN_ML..HYDRATION_MAX_ML) Check.Ok else Check.Refused("Enter between $HYDRATION_MIN_ML and $HYDRATION_MAX_ML ml.")

    fun checkSleep(startMillis: Long, endMillis: Long): Check = when {
        endMillis <= startMillis -> Check.Refused("Waking up has to be after falling asleep.")
        (endMillis - startMillis) / 60_000L > SLEEP_MAX_MINUTES -> Check.Refused("A night can last at most a day.")
        else -> Check.Ok
    }

    fun checkMeasurement(type: MeasureType, value: Double, unit: String): Check = when {
        !value.isFinite() || value <= 0.0 -> Check.Refused("Enter a number above zero.")
        type != MeasureType.OTHER && unit !in type.units -> Check.Refused("That unit does not fit ${type.label.lowercase()}.")
        type == MeasureType.BODY_FAT && value >= 100 -> Check.Refused("Body fat is a percentage under 100.")
        value > 100_000 -> Check.Refused("That number looks wrong.")
        else -> Check.Ok
    }

    const val STEPS_MAX = 200_000

    fun checkSteps(steps: Int): Check = if (steps in 1..STEPS_MAX) Check.Ok else Check.Refused("That is not a step count.")

    fun vitalRange(type: VitalType): ClosedFloatingPointRange<Double> = when (type) {
        VitalType.HEART_RATE, VitalType.HEART_RATE_LOW, VitalType.HEART_RATE_HIGH -> 25.0..250.0
        VitalType.RESTING_HEART_RATE -> 25.0..150.0
        VitalType.SPO2, VitalType.SPO2_LOW, VitalType.SPO2_HIGH -> 50.0..100.0
        VitalType.SKIN_TEMPERATURE, VitalType.SKIN_TEMPERATURE_LOW, VitalType.SKIN_TEMPERATURE_HIGH -> 25.0..45.0
        VitalType.SLEEP_LENGTH -> 30.0..960.0
        VitalType.STRESS -> 1.0..99.0
        VitalType.SLEEP_SCORE -> 1.0..100.0
    }

    fun checkVital(type: VitalType, value: Double): Check =
        if (value.isFinite() && value in vitalRange(type)) Check.Ok else Check.Refused("That does not look like a ${type.label.lowercase()} reading.")

    const val RESEARCH_MIN_MINUTES = 10

    const val ACTIVITIES_PAID_PER_DAY = 3

    const val PARTICIPATION_PAID_PER_DAY = 1

    const val FRESH_MILLIS = 12 * 60 * 60 * 1000L

    const val IMPORT_FRESH_MILLIS = 8 * 24 * 60 * 60 * 1000L

    fun freshFor(activity: ActivityRecord): Long = if (activity.source == SourceKind.IMPORTED) IMPORT_FRESH_MILLIS else FRESH_MILLIS

    const val FUTURE_SLACK_MILLIS = 5 * 60 * 1000L

    fun activityPays(activity: ActivityRecord, nowMillis: Long, paidToday: Int): Boolean =
        paysLikeManual(activity) && activity.minutes >= RESEARCH_MIN_MINUTES &&
            activity.endMillis <= nowMillis + FUTURE_SLACK_MILLIS && activity.endMillis >= nowMillis - freshFor(activity) &&
            paidToday < ACTIVITIES_PAID_PER_DAY

    fun paysLikeManual(activity: ActivityRecord): Boolean = activity.source == SourceKind.MANUAL ||
        (activity.source == SourceKind.IMPORTED && activity.provenance?.let { it.provider in HealImport.PAYING_PROVIDERS && it.possibleDuplicateOf == null } == true)

    fun participationPays(entry: HydrationEntry, nowMillis: Long, paidToday: Int): Boolean =
        entry.source == SourceKind.MANUAL && entry.atMillis <= nowMillis + FUTURE_SLACK_MILLIS &&
            entry.atMillis >= nowMillis - FRESH_MILLIS && paidToday < PARTICIPATION_PAID_PER_DAY

    const val STEPS_PER_REWARD = 2_000

    const val STEPS_PAID_PER_DAY = 5

    fun stepPayments(steps: Int): Int = (steps / STEPS_PER_REWARD).coerceIn(0, STEPS_PAID_PER_DAY)

    const val IMPORTS_PAID_PER_DAY = 3

    fun importCounts(family: HealKindOf, provider: String, atMillis: Long, nowMillis: Long): Boolean =
        family != HealKindOf.STEPS && !(family == HealKindOf.ACTIVITY && provider in HealImport.PAYING_PROVIDERS) &&
            atMillis <= nowMillis + FUTURE_SLACK_MILLIS && atMillis >= nowMillis - IMPORT_FRESH_MILLIS

    fun stepMark(day: LocalDate, n: Int) = "steps|$day|$n"
    fun importMark(day: LocalDate, family: HealKindOf) = "import|$day|${family.name}"

    fun pruneMarks(marks: Set<String>, today: LocalDate): Set<String> {
        val oldest = today.toEpochDays() - HealSettings.MARK_DAYS
        return marks.filterTo(HashSet()) { m -> m.split('|').getOrNull(1)?.let { runCatching { LocalDate.parse(it).toEpochDays() }.getOrNull() }?.let { it >= oldest } ?: false }
    }

    fun stepsPaidOn(data: HealData, day: LocalDate): Int =
        maxOf(data.steps.filter { it.day == day }.maxOfOrNull { it.paidBuckets } ?: 0, data.settings.paidMarks.maxOfOrNull { m -> m.split('|').let { if (it[0] == "steps" && it.getOrNull(1) == day.toString()) it.getOrNull(2)?.toIntOrNull() ?: 0 else 0 } } ?: 0)

    fun activitiesPaidOn(data: HealData, day: LocalDate): Int = data.activities.count { it.day == day && it.paid }
    fun participationPaidOn(data: HealData, day: LocalDate): Int = data.hydration.count { it.day == day && it.paid }

    fun activeMinutes(data: HealData, day: LocalDate): Int? =
        data.activities.filter { it.day == day }.takeIf { it.isNotEmpty() }?.sumOf { it.minutes }

    fun weekActiveMinutes(data: HealData, weekStart: LocalDate): Int? =
        data.activities.filter { it.day in weekDays(weekStart) }.takeIf { it.isNotEmpty() }?.sumOf { it.minutes }

    fun weekActiveDays(data: HealData, weekStart: LocalDate): Int =
        data.activities.filter { it.day in weekDays(weekStart) }.map { it.day }.toSet().size

    fun hydrationOn(data: HealData, day: LocalDate): Int? =
        data.hydration.filter { it.day == day }.takeIf { it.isNotEmpty() }?.sumOf { it.ml }

    fun stepsOn(data: HealData, day: LocalDate): Int? = data.steps.filter { it.day == day }.maxOfOrNull { it.steps }

    fun latestVital(data: HealData, type: VitalType): VitalReading? = data.vitals.filter { it.type == type }.maxByOrNull { it.atMillis }

    fun latestSleep(data: HealData): SleepRecord? = data.sleep.maxByOrNull { it.endMillis }

    fun latestMeasurement(data: HealData, type: MeasureType): Measurement? =
        data.measurements.filter { it.type == type }.maxByOrNull { it.atMillis }

    fun weekStartOf(day: LocalDate, first: DayOfWeek = DayOfWeek.MONDAY): LocalDate {
        val back = (day.dayOfWeek.ordinal - first.ordinal + 7) % 7
        return day.minus(DatePeriod(days = back))
    }

    private fun weekDays(start: LocalDate): ClosedRange<LocalDate> = start..LocalDate.fromEpochDays(start.toEpochDays() + 6)

    data class Progress(val done: Int?, val target: Int, val met: Boolean) {
        val fraction: Float get() = if (done == null || target <= 0) 0f else (done.toFloat() / target).coerceIn(0f, 1f)
    }

    fun goalTargetOk(kind: GoalKind, target: Int): Boolean = target in 1..(if (kind == GoalKind.WEEKLY_ACTIVE_DAYS) 7 else 100_000)

    fun progress(goal: HealGoal, data: HealData, today: LocalDate, first: DayOfWeek = DayOfWeek.MONDAY): Progress {
        val done: Int? = when (goal.kind) {
            GoalKind.WEEKLY_ACTIVE_MINUTES -> weekActiveMinutes(data, weekStartOf(today, first))
            GoalKind.WEEKLY_ACTIVE_DAYS -> weekActiveDays(data, weekStartOf(today, first)).takeIf { it > 0 }
            GoalKind.DAILY_HYDRATION -> hydrationOn(data, today)
        }
        return Progress(done, goal.target, done != null && done >= goal.target)
    }

    const val TREND_MIN_OBSERVATIONS = 3

    fun trendText(data: HealData, type: MeasureType, unit: String): String? {
        val series = data.measurements.filter { it.type == type && it.unit == unit }.sortedBy { it.atMillis }
        if (series.size < TREND_MIN_OBSERVATIONS) return null
        val change = series.last().value - series.first().value
        val move = if (abs(change) < 0.05) "about the same as the first" else "${format(abs(change))} $unit ${if (change > 0) "higher" else "lower"} than the first"
        return "${series.size} entries; the latest, ${format(series.last().value)} $unit, is $move."
    }

    fun format(value: Double): String = if (value == value.roundToInt().toDouble()) value.roundToInt().toString() else ((value * 10).roundToInt() / 10.0).toString()

    fun duration(minutes: Int): String = if (minutes >= 60) "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')} min" else "$minutes min"

    // --- medications and supplements (section 8.1): user-entered, never calculated, never paid -------------------------

    const val MEDICATION_NAME_MAX = 80
    const val MEDICATION_STRENGTH_MAX = 40
    const val MEDICATION_TIMES_MAX = 6
    const val MEDICATION_SUPPLY_MAX = 100_000

    fun checkMedication(m: Medication): Check = when {
        m.name.isBlank() -> Check.Refused("Give it a name.")
        m.name.trim().length > MEDICATION_NAME_MAX -> Check.Refused("Keep the name under $MEDICATION_NAME_MAX letters.")
        m.strength.length > MEDICATION_STRENGTH_MAX -> Check.Refused("That strength is too long.")
        m.instructions.length > NOTE_MAX || m.note.length > NOTE_MAX -> Check.Refused("That text is too long.")
        m.times.size > MEDICATION_TIMES_MAX -> Check.Refused("A day can have at most $MEDICATION_TIMES_MAX times.")
        m.endDay != null && m.endDay < m.startDay -> Check.Refused("The end date is before the start date.")
        m.supplyLeft != null && m.supplyLeft !in 0..MEDICATION_SUPPLY_MAX -> Check.Refused("That supply looks wrong.")
        m.refillAt != null && m.refillAt !in 0..MEDICATION_SUPPLY_MAX -> Check.Refused("That refill point looks wrong.")
        else -> Check.Ok
    }

    /** Whether this medication is being taken on [day]: active, started, and not past its end date. */
    fun medicationRunning(m: Medication, day: LocalDate): Boolean =
        m.status == MedicationStatus.ACTIVE && day >= m.startDay && (m.endDay == null || day <= m.endDay)

    /** The times it is taken on [day]; none when it is paused, stopped, not started or finished. */
    fun medicationTimesOn(m: Medication, day: LocalDate): List<LocalTime> =
        if (!medicationRunning(m, day)) emptyList() else m.times.distinct().sorted()

    fun medicationDosesOn(data: HealData, medicationId: String, day: LocalDate): List<MedicationDose> =
        data.doses.filter { it.medicationId == medicationId && it.day == day }

    /** The scheduled times on [day] with no adherence entry yet. */
    fun medicationDueTimes(data: HealData, m: Medication, day: LocalDate): List<LocalTime> {
        val logged = medicationDosesOn(data, m.id, day).mapTo(HashSet()) { it.time }
        return medicationTimesOn(m, day).filter { it !in logged }
    }

    fun medicationScheduledOn(data: HealData, day: LocalDate): Int = data.medications.sumOf { medicationTimesOn(it, day).size }

    fun medicationTakenOn(data: HealData, day: LocalDate): Int = data.doses.count { it.day == day && it.status == DoseStatus.TAKEN }

    /** A count the player set, at or under the point they set: a refill reminder, never advice. */
    fun medicationLowSupply(m: Medication): Boolean =
        m.status != MedicationStatus.STOPPED && m.supplyLeft != null && m.refillAt != null && m.supplyLeft <= m.refillAt
}

object HealUnits {
    private const val LB_PER_KG = 2.2046226218
    private const val CM_PER_IN = 2.54

    fun convert(value: Double, from: String, to: String): Double? = when {
        from == to -> value
        from == "kg" && to == "lb" -> value * LB_PER_KG
        from == "lb" && to == "kg" -> value / LB_PER_KG
        from == "cm" && to == "in" -> value / CM_PER_IN
        from == "in" && to == "cm" -> value * CM_PER_IN
        else -> null
    }

    fun show(m: Measurement, unit: String): String {
        val converted = convert(m.value, m.unit, unit)
        return if (converted == null) "${HealRules.format(m.value)} ${m.unit}".trim() else "${HealRules.format(converted)} $unit".trim()
    }
}
