package app.pokeplan.plan

/**
 * What Plan's "All tasks" view shows: a search over title and notes, and one choice each for list, rank
 * and status. A null choice means "any". Deleted tasks never match.
 */
data class TaskFilter(
    val query: String = "",
    /** A list's id, [INBOX] for tasks in no list, or null for any list. */
    val listId: String? = null,
    val rank: Rank? = null,
    /** Open by default; [Status.FINISHED] is the Done preset (done and "won't do"); [Status.ANY] is every state. */
    val status: Status = Status.OPEN,
) {
    enum class Status(val label: String) { OPEN("Open"), FINISHED("Done"), ANY("Any") }

    fun matches(task: Task): Boolean {
        if (task.state == TaskState.DELETED) return false
        when (status) {
            Status.OPEN -> if (task.state != TaskState.OPEN) return false
            Status.FINISHED -> if (task.state != TaskState.DONE && task.state != TaskState.WONT_DO) return false
            Status.ANY -> {}
        }
        if (rank != null && task.rank != rank) return false
        if (listId != null && (if (listId == INBOX) task.listId != null else task.listId != listId)) return false
        // Every word must appear in the title or the notes, in any order and any case.
        val text = "${task.title}\n${task.notes}".lowercase()
        return query.lowercase().split(' ', '\n', '\t').filter { it.isNotEmpty() }.all { it in text }
    }

    /** The matches: open tasks first, by the day they are on (undated last), then the finished ones, latest first. */
    fun apply(tasks: List<Task>): List<Task> {
        val (open, rest) = tasks.filter(::matches).partition { it.isOpen }
        val byDay = open.sortedWith(compareBy<Task>({ (it.planned ?: it.due?.date) == null }, { it.planned ?: it.due?.date }, { it.createdAtMillis }))
        return byDay + rest.sortedByDescending { it.completedAtMillis ?: it.deletedAtMillis ?: it.createdAtMillis }
    }

    /** True when anything narrows the list beyond the default (open tasks). */
    val isNarrowed: Boolean get() = this != TaskFilter()

    companion object {
        /** The [listId] of tasks that are in no list. */
        const val INBOX = ""
    }
}
