package app.pokeplan.plan

import kotlinx.serialization.Serializable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * Difficulty rank, E (easiest) to A (hardest). Higher rank pays more. [tier] is the 1 to 5 number the reward engine uses;
 * [minAgeMinutes] is how old a one-off task must be before it pays. S rank exists only for special missions and cannot be picked.
 */
enum class Rank(val letter: Char, val tier: Int, val minAgeMinutes: Int) {
    E('E', 1, 2),
    D('D', 2, 5),
    C('C', 3, 10),
    B('B', 4, 15),
    A('A', 5, 20);

    companion object {
        fun fromLetter(c: Char): Rank? = entries.firstOrNull { it.letter == c.uppercaseChar() }
    }
}

enum class Category(val label: String) {
    WORK("Work"),
    SCHOOL("School"),
    HOME("Home"),
    HEALTH("Health"),
    SOCIAL("Social"),
    PERSONAL("Personal"),
}

enum class TaskState { OPEN, DONE, WONT_DO, DELETED }

/**
 * What a task is linked to in another module (docs/pokelife `PRODUCT_BIBLE.md` section 4, cross-module ownership). A link is a reference by a stable opaque id,
 * never a second copy of the other module's record, and it decides **who owns the one completion and the one reward**:
 *  - [HOME_CHORE]: HOME owns the chore. The task is a projection of the chore's current occurrence: completing it from PLAN asks HOME to complete the chore,
 *    HOME pays Habitat Discovery once, and PLAN pays nothing ([ownsCompletion]).
 *  - [LINK_FOLLOW_UP]: the user converted a LINK follow-up. From then on PLAN owns the task, its completion and its reward (PLAN's rules, paid once);
 *    LINK keeps the person and shows this task's status, and cannot pay for the same follow-up.
 */
@Serializable
enum class LinkKind(val ownsCompletion: Boolean) {
    HOME_CHORE(ownsCompletion = true),
    LINK_FOLLOW_UP(ownsCompletion = false),
}

/** The reference a linked task keeps: the kind, the other module's record id, and (for a repeating chore) the occurrence the task stands for now. */
@Serializable
data class TaskLink(val kind: LinkKind, val ref: String, val occurrence: String = "")

@Serializable
data class Subtask(
    val id: String,
    val title: String,
    val done: Boolean = false,
    val createdAtMillis: Long,
    /** Already paid its flat reward; never pays twice. */
    val paid: Boolean = false,
)

/**
 * A task. Dates and times are wall-clock ("floating") so they keep their local time when the time zone changes.
 * [seriesId] is set for instances of a recurring task, which pay without waiting for the minimum age.
 */
@Serializable
data class Task(
    val id: String,
    val title: String,
    val notes: String = "",
    val rank: Rank = Rank.C,
    val category: Category = Category.PERSONAL,
    val listId: String? = null,
    /** When you intend to do it. */
    val planned: LocalDate? = null,
    val plannedTime: LocalTime? = null,
    /** The deadline. */
    val due: LocalDateTime? = null,
    val estimateMinutes: Int? = null,
    val seriesId: String? = null,
    val tutorial: Boolean = false,
    val evening: Boolean = false,
    val icon: String? = null,
    val subtasks: List<Subtask> = emptyList(),
    val state: TaskState = TaskState.OPEN,
    val createdAtMillis: Long,
    val completedAtMillis: Long? = null,
    val deletedAtMillis: Long? = null,
    /** Minutes of focus sessions started from this task. */
    val focusedMinutes: Int = 0,
    /** The completion reward has been given; reopening and finishing again never pays twice. */
    val paid: Boolean = false,
    /** When to be reminded, relative to the planned time or the deadline ([Reminders.baseTime]). */
    val reminders: List<ReminderOffset> = emptyList(),
    /** The other module's record this task refers to, if any (see [TaskLink]). A task linked to a record its module owns never pays from PLAN. */
    val link: TaskLink? = null,
    /** A reminder with no date on the task (PLAN.md 3.5A): shows on Today from this time, never overdue, fires once. Cleared when the task is done. */
    val nudge: LocalDateTime? = null,
) {
    init {
        require(title.isNotBlank()) { "a task needs a title" }
        require(estimateMinutes == null || estimateMinutes > 0) { "estimate must be positive" }
        require(reminders.size <= Reminders.MAX_PER_TASK) { "at most ${Reminders.MAX_PER_TASK} reminders per task" }
    }

    val isOpen: Boolean get() = state == TaskState.OPEN

    /** True when another module owns this task's completion and reward (a HOME chore link): PLAN only shows it and asks the owner. */
    val ownedElsewhere: Boolean get() = link?.kind?.ownsCompletion == true
}
