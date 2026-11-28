package app.pokeplan.care

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

@Serializable
data class Routine(
    val id: String,
    val title: String,
    val icon: String? = null,
    val time: LocalTime? = null,
    val days: Set<DayOfWeek> = emptySet(),
    val createdOn: LocalDate,
    val completions: Set<LocalDate> = emptySet(),
    val paidOn: LocalDate? = null,
    val archived: Boolean = false,
    val skipped: Set<LocalDate> = emptySet(),
) {
    fun isDueOn(date: LocalDate): Boolean = !archived && date >= createdOn && (days.isEmpty() || date.dayOfWeek in days)

    fun skippedOn(date: LocalDate): Boolean = date in skipped

    fun plannedOn(date: LocalDate): Boolean = isDueOn(date) && date !in skipped

    fun doneOn(date: LocalDate): Boolean = date in completions

    fun paysOn(date: LocalDate): Boolean = date !in completions && date != paidOn

    fun complete(date: LocalDate): Routine = copy(completions = completions + date, skipped = skipped - date)

    fun skip(date: LocalDate): Routine = copy(skipped = skipped + date, completions = completions - date)

    fun unskip(date: LocalDate): Routine = copy(skipped = skipped - date)

    fun paid(date: LocalDate): Routine = copy(paidOn = date)

    fun undo(date: LocalDate): Routine = copy(completions = completions - date)
}

@Serializable
enum class Emotion(val emoji: String, val label: String) {
    JOY("😊", "Joy"),
    CALM("😌", "Calm"),
    SADNESS("😢", "Sadness"),
    FEAR("😟", "Worry"),
    ANGER("😠", "Anger"),
    SHAME("😳", "Shame"),
}

@Serializable
data class DiaryCard(
    val id: String,
    val day: LocalDate,
    val atMillis: Long,
    val feelings: Map<Emotion, Int> = emptyMap(),
    val urges: Map<String, Int> = emptyMap(),
    val skills: Set<String> = emptySet(),
    val note: String? = null,
    val mood: Int? = null,
)

@Serializable
data class CalmBreak(
    val atMillis: Long,
    val day: LocalDate,
    val seconds: Int,
    val paid: Boolean = false,
)

@Serializable
data class SkillPractice(
    val atMillis: Long,
    val day: LocalDate,
    val skillId: String,
    val paid: Boolean = false,
)

@Serializable
data class JournalEntry(
    val id: String,
    val atMillis: Long,
    val day: LocalDate,
    val text: String,
    val promptId: String? = null,
    val tags: Set<String> = emptySet(),
)

@Serializable
data class CareData(
    val routines: List<Routine> = emptyList(),
    val diary: List<DiaryCard> = emptyList(),
    val breaks: List<CalmBreak> = emptyList(),
    val practices: List<SkillPractice> = emptyList(),
    val journal: List<JournalEntry> = emptyList(),
    val journalDraft: String = "",
    val skippedPrompts: Map<LocalDate, Int> = emptyMap(),
    val rhythmWeeks: Set<LocalDate> = emptySet(),
    val settings: CareSettings = CareSettings(),
    val visitorSeed: Int = 0,
    val visitorsDay: LocalDate? = null,
    val visitors: List<Visitor> = emptyList(),
    val dismissed: List<Visitor> = emptyList(),
    val visit: Visit? = null,
    val met: Set<Int> = emptySet(),
    val friends: List<Friend> = emptyList(),
    val reunion: Reunion? = null,
    val reunionDay: LocalDate? = null,
    val reunionSeen: Map<String, LocalDate> = emptyMap(),
    val play: Map<String, PlayRecord> = emptyMap(),
)

@Serializable
data class CareSettings(
    val reminders: Boolean = true,
    val urges: List<String> = emptyList(),
)

object CareRules {
    const val ROUTINES_PAID_PER_DAY = 5

    const val BREAKS_PAID_PER_DAY = 3

    const val BREAK_MIN_SECONDS = 60
    const val BREAK_MAX_SECONDS = 120

    const val SKILLS_PAID_PER_DAY = 3

    const val RHYTHM_DAYS = 4

    /** "Kept a rhythm" moments that pay: one a week, since the rhythm is a weekly milestone (CARE.md 5). */
    const val RHYTHMS_PAID_PER_WEEK = 1

    /** Care Play moments that pay each day, one per Pokémon (CARE.md 4). */
    const val PLAYS_PAID_PER_DAY = 6

    fun weekStartOf(date: LocalDate, weekStart: DayOfWeek): LocalDate = app.pokeplan.kit.Periods.weekStartOf(date, weekStart)

    fun routinesPaidOn(data: CareData, date: LocalDate): Int = data.routines.count { it.paidOn == date }

    fun breaksPaidOn(data: CareData, date: LocalDate): Int = data.breaks.count { it.day == date && it.paid }

    fun skillsPaidOn(data: CareData, date: LocalDate): Int = data.practices.count { it.day == date && it.paid }

    fun skillPaysOn(data: CareData, skillId: String, date: LocalDate): Boolean =
        data.practices.none { it.day == date && it.skillId == skillId && it.paid } && skillsPaidOn(data, date) < SKILLS_PAID_PER_DAY

    fun careDays(data: CareData, weekStart: LocalDate): Set<LocalDate> {
        val week = (0..6).map { weekStart.plus(DatePeriod(days = it)) }.toSet()
        return (data.routines.flatMap { it.completions } + data.breaks.map { it.day } + data.practices.map { it.day }).filter { it in week }.toSet()
    }

    fun rhythmDue(data: CareData, weekStart: LocalDate): Boolean =
        weekStart !in data.rhythmWeeks && careDays(data, weekStart).size >= RHYTHM_DAYS
}
