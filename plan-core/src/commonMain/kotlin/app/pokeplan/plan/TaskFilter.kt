package app.pokeplan.plan

data class TaskFilter(
    val query: String = "",
    val listId: String? = null,
    val rank: Rank? = null,
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
        val text = "${task.title}\n${task.notes}".lowercase()
        return query.lowercase().split(' ', '\n', '\t').filter { it.isNotEmpty() }.all { it in text }
    }

    fun apply(tasks: List<Task>): List<Task> {
        val (open, rest) = tasks.filter(::matches).partition { it.isOpen }
        val byDay = open.sortedWith(compareBy<Task>({ (it.planned ?: it.due?.date) == null }, { it.planned ?: it.due?.date }, { it.createdAtMillis }))
        return byDay + rest.sortedByDescending { it.completedAtMillis ?: it.deletedAtMillis ?: it.createdAtMillis }
    }

    val isNarrowed: Boolean get() = this != TaskFilter()

    companion object {
        const val INBOX = ""
    }
}
