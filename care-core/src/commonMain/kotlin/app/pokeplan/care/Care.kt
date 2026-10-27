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

/** The feelings a diary card rates, 0 (not at all) to 5 (very strongly), like a DBT diary card. */
@Serializable
enum class Emotion(val emoji: String, val label: String) {
    JOY("😊", "Joy"),
    CALM("😌", "Calm"),
    SADNESS("😢", "Sadness"),
    FEAR("😟", "Worry"),
    ANGER("😠", "Anger"),
    SHAME("😳", "Shame"),
}

/**
 * One day's diary card, after the DBT diary card: how strongly each feeling showed up (0 to 5), any urges the user tracks
 * (0 to 5), the skills used, and a note. One card a day; saving again replaces it. Private: it never earns.
 */
@Serializable
data class DiaryCard(
    val id: String,
    val day: LocalDate,
    val atMillis: Long,
    val feelings: Map<Emotion, Int> = emptyMap(),
    val urges: Map<String, Int> = emptyMap(),
    /** Ids from [Skills]. */
    val skills: Set<String> = emptySet(),
    val note: String? = null,
)

/** A finished calm break (the breathing moment); [paid] is whether it earned anything. */
@Serializable
data class CalmBreak(
    val atMillis: Long,
    val day: LocalDate,
    val seconds: Int,
    val paid: Boolean = false,
)

/** A skill from the library practised once; [paid] is whether it earned anything. */
@Serializable
data class SkillPractice(
    val atMillis: Long,
    val day: LocalDate,
    val skillId: String,
    val paid: Boolean = false,
)

/** A journal entry, written any time; [promptId] when it answers a prompt. Writing earns nothing. */
@Serializable
data class JournalEntry(
    val id: String,
    val atMillis: Long,
    val day: LocalDate,
    val text: String,
    val promptId: String? = null,
    val tags: Set<String> = emptySet(),
)

/** Everything Care keeps, saved apart from Plan (`care.json` on the device, `care.saves` online when sync is on). */
@Serializable
data class CareData(
    val routines: List<Routine> = emptyList(),
    val diary: List<DiaryCard> = emptyList(),
    val breaks: List<CalmBreak> = emptyList(),
    val practices: List<SkillPractice> = emptyList(),
    val journal: List<JournalEntry> = emptyList(),
    /** Prompts skipped today, so the next one comes up. */
    val skippedPrompts: Map<LocalDate, Int> = emptyMap(),
    /** The weeks (their first day) whose "kept a rhythm" milestone has paid. */
    val rhythmWeeks: Set<LocalDate> = emptySet(),
    val settings: CareSettings = CareSettings(),
    /** Seeds this device's visitors (with the day); made the first time they are offered. */
    val visitorSeed: Int = 0,
    /** The day visitors were last offered, and who is visiting now (those being befriended stay, the rest are today's). */
    val visitorsDay: LocalDate? = null,
    val visitors: List<Visitor> = emptyList(),
    /** The day the day's befriend was spent. */
    val befriendDay: LocalDate? = null,
    /** The visit in progress, if one was started and not finished (it ends with the day). */
    val visit: Visit? = null,
    /** Species of visitors the player has cared for, whether or not they became friends (the "met" album). */
    val met: Set<Int> = emptySet(),
)

@Serializable
data class CareSettings(
    /** Gentle routine reminders; each can still be dismissed. */
    val reminders: Boolean = true,
    /** Cloud sync is opt-in: off, Care stays on this device. */
    val cloudSync: Boolean = false,
    /** Urges the user chose to track on the diary card (none by default). */
    val urges: List<String> = emptyList(),
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

    /** Skills practised that pay each day, each skill once. */
    const val SKILLS_PAID_PER_DAY = 3

    /** Days in a week with a routine done, a calm break or a skill practised that make "kept a rhythm". */
    const val RHYTHM_DAYS = 4

    fun weekStartOf(date: LocalDate, weekStart: DayOfWeek): LocalDate =
        date.plus(DatePeriod(days = -((date.dayOfWeek.isoDayNumber - weekStart.isoDayNumber + 7) % 7)))

    fun routinesPaidOn(data: CareData, date: LocalDate): Int = data.routines.count { it.paidOn == date }

    fun breaksPaidOn(data: CareData, date: LocalDate): Int = data.breaks.count { it.day == date && it.paid }

    fun skillsPaidOn(data: CareData, date: LocalDate): Int = data.practices.count { it.day == date && it.paid }

    /** Whether any care paid on [date]: befriending a visitor needs one. */
    fun paidOn(data: CareData, date: LocalDate): Boolean =
        routinesPaidOn(data, date) > 0 || breaksPaidOn(data, date) > 0 || skillsPaidOn(data, date) > 0

    /** Whether practising [skillId] on [date] can pay: not already paid today, and under the day's limit. */
    fun skillPaysOn(data: CareData, skillId: String, date: LocalDate): Boolean =
        data.practices.none { it.day == date && it.skillId == skillId && it.paid } && skillsPaidOn(data, date) < SKILLS_PAID_PER_DAY

    /** The days of the week starting [weekStart] with some care done in them. */
    fun careDays(data: CareData, weekStart: LocalDate): Set<LocalDate> {
        val week = (0..6).map { weekStart.plus(DatePeriod(days = it)) }.toSet()
        return (data.routines.flatMap { it.completions } + data.breaks.map { it.day } + data.practices.map { it.day }).filter { it in week }.toSet()
    }

    /** Whether the week starting [weekStart] has just earned its rhythm milestone and it hasn't paid yet. */
    fun rhythmDue(data: CareData, weekStart: LocalDate): Boolean =
        weekStart !in data.rhythmWeeks && careDays(data, weekStart).size >= RHYTHM_DAYS
}
