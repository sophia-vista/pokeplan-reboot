package app.pokeplan.plan

import kotlinx.serialization.Serializable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

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

enum class TaskState { OPEN, DONE, WONT_DO, DELETED }

@Serializable
enum class LinkKind(val ownsCompletion: Boolean) {
    HOME_CHORE(ownsCompletion = true),
    LINK_FOLLOW_UP(ownsCompletion = false),
}

@Serializable
data class TaskLink(val kind: LinkKind, val ref: String, val occurrence: String = "")

@Serializable
data class Subtask(
    val id: String,
    val title: String,
    val done: Boolean = false,
    val createdAtMillis: Long,
    val paid: Boolean = false,
)

@Serializable
data class Task(
    val id: String,
    val title: String,
    val notes: String = "",
    val rank: Rank = Rank.C,
    val listId: String? = null,
    val planned: LocalDate? = null,
    val plannedTime: LocalTime? = null,
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
    val focusedMinutes: Int = 0,
    val paid: Boolean = false,
    val reminders: List<ReminderOffset> = emptyList(),
    val link: TaskLink? = null,
    val nudge: LocalDateTime? = null,
    val reward: app.pokeplan.core.reward.RewardFocus = app.pokeplan.core.reward.RewardFocus.EXP,
) {
    init {
        require(title.isNotBlank()) { "a task needs a title" }
        require(estimateMinutes == null || estimateMinutes > 0) { "estimate must be positive" }
        require(reminders.size <= Reminders.MAX_PER_TASK) { "at most ${Reminders.MAX_PER_TASK} reminders per task" }
    }

    val isOpen: Boolean get() = state == TaskState.OPEN

    val ownedElsewhere: Boolean get() = link?.kind?.ownsCompletion == true
}
