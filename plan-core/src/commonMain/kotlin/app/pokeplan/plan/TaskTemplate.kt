package app.pokeplan.plan

import app.pokeplan.core.reward.RewardFocus
import kotlinx.serialization.Serializable

/**
 * A task the player makes again and again (owner list of 27 November 2026, P2): what is worth keeping of it, with no day, no
 * deadline, no reminder and no link, so a new task from it is a fresh one. Kept in PokéLife's save and never paid for.
 */
@Serializable
data class TaskTemplate(
    val id: String,
    val name: String,
    val title: String,
    val notes: String = "",
    val rank: Rank = Rank.C,
    val listId: String? = null,
    val estimateMinutes: Int? = null,
    val steps: List<String> = emptyList(),
    val reward: RewardFocus = RewardFocus.EXP,
) {
    init {
        require(name.isNotBlank()) { "a template needs a name" }
        require(title.isNotBlank()) { "a template needs a task title" }
    }
}

object TaskTemplates {
    const val MAX = 30
    const val MAX_NAME = 40

    /** The template a task makes: [name] defaults to the task's own title. A step's done mark is not kept. */
    fun from(task: Task, id: String, name: String = task.title): TaskTemplate = TaskTemplate(
        id = id, name = name.trim().take(MAX_NAME).ifBlank { task.title.trim().take(MAX_NAME) }, title = task.title.trim(), notes = task.notes,
        rank = task.rank, listId = task.listId, estimateMinutes = task.estimateMinutes, steps = task.subtasks.map { it.title }, reward = task.reward,
    )

    /** Adds [template], or replaces the one of the same name (case aside) so saving again updates rather than piles up. */
    fun saved(all: List<TaskTemplate>, template: TaskTemplate): List<TaskTemplate> {
        val same = all.indexOfFirst { it.name.equals(template.name, ignoreCase = true) }
        return when {
            same >= 0 -> all.toMutableList().also { it[same] = template.copy(id = all[same].id) }
            all.size >= MAX -> all
            else -> all + template
        }
    }
}
