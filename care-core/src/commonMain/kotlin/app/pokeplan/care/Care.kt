package app.pokeplan.care

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

/**
 * A self-care routine the user defines (drink water, stretch, wind down), with a time or none, on some weekdays or every day.
 * No streaks: [CareTrends] shows rolling counts and personal bests instead.
 */
@Serializable
data class Routine(
    val id: String,
    val title: String,
    /** A real item icon (an id in `assets/items`), or none. */
    val icon: String? = null,
    val time: LocalTime? = null,
    /** The weekdays it is meant for; empty means every day. */
    val days: Set<DayOfWeek> = emptySet(),
    val createdOn: LocalDate,
    val completions: Set<LocalDate> = emptySet(),
    /** The last day a completion paid, kept apart from [completions] so undoing and ticking again never pays twice. */
    val paidOn: LocalDate? = null,
    val archived: Boolean = false,
) {
    fun isDueOn(date: LocalDate): Boolean = !archived && date >= createdOn && (days.isEmpty() || date.dayOfWeek in days)

    fun doneOn(date: LocalDate): Boolean = date in completions

    /** Whether a completion on [date] can pay: the first of the day, even after an undo. The daily cap is checked apart. */
    fun paysOn(date: LocalDate): Boolean = date !in completions && date != paidOn

    fun complete(date: LocalDate): Routine = copy(completions = completions + date)

    fun paid(date: LocalDate): Routine = copy(paidOn = date)

    fun undo(date: LocalDate): Routine = copy(completions = completions - date)
}

/** The emoji scale of a mood check-in. Private: it never leaves the device unless cloud sync is on, and never earns. */
@Serializable
enum class Mood(val emoji: String, val label: String) {
    AWFUL("😣", "Awful"),
    LOW("😕", "Low"),
    OKAY("😐", "Okay"),
    GOOD("🙂", "Good"),
    GREAT("😄", "Great"),
    ;

    /** 1 (awful) to 5 (great), for the trend. */
    val score: Int get() = ordinal + 1
}

@Serializable
data class MoodCheckIn(
    val id: String,
    val atMillis: Long,
    val day: LocalDate,
    val mood: Mood,
    val note: String? = null,
    val tags: Set<String> = emptySet(),
)

/** A finished calm break (the breathing moment); [paid] is whether it earned anything. */
@Serializable
data class CalmBreak(
    val atMillis: Long,
    val day: LocalDate,
    val seconds: Int,
    val paid: Boolean = false,
)

/** An answer to a gratitude or reflection prompt. Writing earns nothing. */
@Serializable
data class Reflection(
    val id: String,
    val day: LocalDate,
    val promptId: String,
    val text: String,
)

/** Everything Care keeps, saved apart from Plan (`care.json` on the device, `care.saves` online when sync is on). */
@Serializable
data class CareData(
    val routines: List<Routine> = emptyList(),
    val moods: List<MoodCheckIn> = emptyList(),
    val breaks: List<CalmBreak> = emptyList(),
    val reflections: List<Reflection> = emptyList(),
    /** Prompts skipped today, so the next one comes up. */
    val skippedPrompts: Map<LocalDate, Int> = emptyMap(),
    /** The weeks (their first day) whose "kept a rhythm" milestone has paid. */
    val rhythmWeeks: Set<LocalDate> = emptySet(),
    val settings: CareSettings = CareSettings(),
)

@Serializable
data class CareSettings(
    /** Gentle routine reminders; each can still be dismissed. */
    val reminders: Boolean = true,
    /** Cloud sync is opt-in: off, Care stays on this device. */
    val cloudSync: Boolean = false,
)

/** Care's limits on what pays. *(tune)* */
object CareRules {
    /** Routines that pay each day; more can be ticked, they just earn nothing. */
    const val ROUTINES_PAID_PER_DAY = 5

    /** Calm breaks that pay each day. */
    const val BREAKS_PAID_PER_DAY = 3

    /** A calm break is 1 to 2 minutes; shorter ones are kept but don't pay, longer ones count as the maximum. */
    const val BREAK_MIN_SECONDS = 60
    const val BREAK_MAX_SECONDS = 120

    /** Days in a week with a routine done or a calm break that make "kept a rhythm". */
    const val RHYTHM_DAYS = 4

    fun weekStartOf(date: LocalDate, weekStart: DayOfWeek): LocalDate =
        date.plus(DatePeriod(days = -((date.dayOfWeek.isoDayNumber - weekStart.isoDayNumber + 7) % 7)))

    fun routinesPaidOn(data: CareData, date: LocalDate): Int = data.routines.count { it.paidOn == date }

    fun breaksPaidOn(data: CareData, date: LocalDate): Int = data.breaks.count { it.day == date && it.paid }

    /** The days of the week starting [weekStart] with some care done in them. */
    fun careDays(data: CareData, weekStart: LocalDate): Set<LocalDate> {
        val week = (0..6).map { weekStart.plus(DatePeriod(days = it)) }.toSet()
        return (data.routines.flatMap { it.completions } + data.breaks.map { it.day }).filter { it in week }.toSet()
    }

    /** Whether the week starting [weekStart] has just earned its rhythm milestone and it hasn't paid yet. */
    fun rhythmDue(data: CareData, weekStart: LocalDate): Boolean =
        weekStart !in data.rhythmWeeks && careDays(data, weekStart).size >= RHYTHM_DAYS
}
