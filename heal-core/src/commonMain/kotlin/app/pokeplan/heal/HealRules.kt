package app.pokeplan.heal

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.math.abs
import kotlin.math.roundToInt

/** Why a record was not accepted, in words for the screen. */
sealed interface Check {
    data object Ok : Check
    data class Refused(val reason: String) : Check
}

/**
 * HEAL's rules, all pure. Three principles run through them (HEAL.md sections 2.2, 6, 9, 12): **missing is not zero** (a day with nothing
 * logged has no total, not a total of 0), **nothing is judged** (no score, no good or bad, no target the app sets for you) and
 * **only participation can ever earn** (what pays is decided here and carries no measurement).
 */
object HealRules {
    // --- what the app accepts --------------------------------------------------------------------------------------------

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

    // --- Field Research: what may pay (HEAL.md section 12) -------------------------------------------------------------------

    /** A session under this long is logged but does not count for Field Research, so a few seconds of walking is not farmed. *(tune)* */
    const val RESEARCH_MIN_MINUTES = 10

    /** Sessions Field Research pays for in a day; the rest are still logged. *(tune)* */
    const val ACTIVITIES_PAID_PER_DAY = 3

    /** Hydration pays at most once a day (section 12.2: bounded, never every entry). *(tune)* */
    const val PARTICIPATION_PAID_PER_DAY = 1

    /** A session pays only while it is fresh: it ended no more than this long ago (so backfilling history never pays, section 10.3). *(tune)* */
    const val FRESH_MILLIS = 12 * 60 * 60 * 1000L

    /** The clock of a phone can be a little ahead of what the user typed; a session ending this far in the future is still today's. */
    const val FUTURE_SLACK_MILLIS = 5 * 60 * 1000L

    /** Whether a just-logged session may be paid: it is long enough, it ended recently and not in the future, and the day's share is not used. */
    fun activityPays(activity: ActivityRecord, nowMillis: Long, paidToday: Int): Boolean =
        activity.source == SourceKind.MANUAL && activity.minutes >= RESEARCH_MIN_MINUTES &&
            activity.endMillis <= nowMillis + FUTURE_SLACK_MILLIS && activity.endMillis >= nowMillis - FRESH_MILLIS &&
            paidToday < ACTIVITIES_PAID_PER_DAY

    /** Whether a just-logged drink may be paid: it is now (not backfilled), and none was paid today. Only for a player who has opted in; the caller checks. */
    fun participationPays(entry: HydrationEntry, nowMillis: Long, paidToday: Int): Boolean =
        entry.source == SourceKind.MANUAL && entry.atMillis <= nowMillis + FUTURE_SLACK_MILLIS &&
            entry.atMillis >= nowMillis - FRESH_MILLIS && paidToday < PARTICIPATION_PAID_PER_DAY

    fun activitiesPaidOn(data: HealData, day: LocalDate): Int = data.activities.count { it.day == day && it.paid }
    fun participationPaidOn(data: HealData, day: LocalDate): Int = data.hydration.count { it.day == day && it.paid }

    // --- reading the records (a total only where something was recorded) ---------------------------------------------------

    /** Active minutes on [day], or null when no activity was logged that day. */
    fun activeMinutes(data: HealData, day: LocalDate): Int? =
        data.activities.filter { it.day == day }.takeIf { it.isNotEmpty() }?.sumOf { it.minutes }

    /** Active minutes in the week starting [weekStart], or null when none was logged in it. */
    fun weekActiveMinutes(data: HealData, weekStart: LocalDate): Int? =
        data.activities.filter { it.day in weekDays(weekStart) }.takeIf { it.isNotEmpty() }?.sumOf { it.minutes }

    fun weekActiveDays(data: HealData, weekStart: LocalDate): Int =
        data.activities.filter { it.day in weekDays(weekStart) }.map { it.day }.toSet().size

    /** Millilitres logged on [day], or null when no drink was logged that day (not 0). */
    fun hydrationOn(data: HealData, day: LocalDate): Int? =
        data.hydration.filter { it.day == day }.takeIf { it.isNotEmpty() }?.sumOf { it.ml }

    /** The most recent finished sleep, or null: an unfinished night is never shown as a total (section 2.2). */
    fun latestSleep(data: HealData): SleepRecord? = data.sleep.maxByOrNull { it.endMillis }

    fun latestMeasurement(data: HealData, type: MeasureType): Measurement? =
        data.measurements.filter { it.type == type }.maxByOrNull { it.atMillis }

    fun weekStartOf(day: LocalDate, first: DayOfWeek = DayOfWeek.MONDAY): LocalDate {
        val back = (day.dayOfWeek.ordinal - first.ordinal + 7) % 7
        return day.minus(DatePeriod(days = back))
    }

    private fun weekDays(start: LocalDate): ClosedRange<LocalDate> = start..LocalDate.fromEpochDays(start.toEpochDays() + 6)

    // --- goals (section 9) --------------------------------------------------------------------------------------------------

    /**
     * Progress towards a goal. [done] is null when nothing was recorded in the period, which a screen shows as "nothing logged yet", never as 0 of
     * the target. Reaching the target can be shown as met; falling short is never shown as failed: it is simply "so far".
     */
    data class Progress(val done: Int?, val target: Int, val met: Boolean) {
        val fraction: Float get() = if (done == null || target <= 0) 0f else (done.toFloat() / target).coerceIn(0f, 1f)
    }

    fun progress(goal: HealGoal, data: HealData, today: LocalDate, first: DayOfWeek = DayOfWeek.MONDAY): Progress {
        val done: Int? = when (goal.kind) {
            GoalKind.WEEKLY_ACTIVE_MINUTES -> weekActiveMinutes(data, weekStartOf(today, first))
            GoalKind.WEEKLY_ACTIVE_DAYS -> weekActiveDays(data, weekStartOf(today, first)).takeIf { it > 0 }
            GoalKind.DAILY_HYDRATION -> hydrationOn(data, today)
        }
        return Progress(done, goal.target, done != null && done >= goal.target)
    }

    // --- trends in words (section 2.3: no causation, no verdict) -----------------------------------------------------------

    /** Observations needed before a measurement shows a trend at all (section 7: only when enough comparable observations exist). */
    const val TREND_MIN_OBSERVATIONS = 3

    /**
     * One neutral sentence about a measurement over time, or null when there are too few comparable (same type and unit) observations. It states the
     * count and the change between the first and the latest, with no direction word that judges ("improved", "worse").
     */
    fun trendText(data: HealData, type: MeasureType, unit: String): String? {
        val series = data.measurements.filter { it.type == type && it.unit == unit }.sortedBy { it.atMillis }
        if (series.size < TREND_MIN_OBSERVATIONS) return null
        val change = series.last().value - series.first().value
        val move = if (abs(change) < 0.05) "about the same as the first" else "${format(abs(change))} $unit ${if (change > 0) "higher" else "lower"} than the first"
        return "${series.size} entries; the latest, ${format(series.last().value)} $unit, is $move."
    }

    fun format(value: Double): String = if (value == value.roundToInt().toDouble()) value.roundToInt().toString() else ((value * 10).roundToInt() / 10.0).toString()

    /** "1 h 05 min" or "45 min". */
    fun duration(minutes: Int): String = if (minutes >= 60) "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')} min" else "$minutes min"
}

/** Unit conversion kept with its meaning (HEAL.md section 11.1): a stored value keeps the unit it was entered in; this only shows it in another. */
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

    /** The value of [m] in [unit] when it can be converted, else as entered. */
    fun show(m: Measurement, unit: String): String {
        val converted = convert(m.value, m.unit, unit)
        return if (converted == null) "${HealRules.format(m.value)} ${m.unit}".trim() else "${HealRules.format(converted)} $unit".trim()
    }
}
