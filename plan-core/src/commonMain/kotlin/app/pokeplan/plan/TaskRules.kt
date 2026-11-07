package app.pokeplan.plan

import kotlinx.datetime.TimeZone

/** What finishing a task did. [pays] says whether a reward is due once the undo window closes. */
data class Completion(
    val task: Task,
    val pays: Boolean,
    val tier: Int,
    val onTime: Boolean,
    val focusedOnIt: Boolean,
    /** The moment the completion becomes final and the reward is granted. */
    val finalAtMillis: Long,
)

/** The rules for finishing, reopening and deleting tasks. See "Tasks" in docs/design.md. */
object TaskRules {
    /** Completing shows the reward and grants it after this long; undo cancels it. */
    const val UNDO_WINDOW_MILLIS = 10_000L

    const val MAX_PAID_SUBTASKS = 5

    /** A finished subtask pays this many Rare Candies. *(tune)* */
    const val SUBTASK_RARE_CANDIES = 1

    /** Deleted tasks can be restored for this long. */
    const val RESTORE_WINDOW_MILLIS = 30L * 24 * 60 * 60 * 1000

    fun minAgeMillis(rank: Rank): Long = rank.minAgeMinutes * 60_000L

    /**
     * One-off tasks pay once they are old enough; tutorial tasks pay straight away, and so do recurring instances, but only
     * from their own day: ticking off next week's instances today pays nothing, so a repeat can't be farmed ahead.
     */
    fun paysOnCompletion(task: Task, nowMillis: Long, zone: TimeZone = TimeZone.UTC): Boolean {
        if (task.paid) return false
        // One completion, one reward owner: a task whose completion another module owns never pays from PLAN (PRODUCT_BIBLE.md section 4).
        if (task.ownedElsewhere) return false
        if (task.tutorial) return true
        if (task.seriesId != null) return task.planned == null || task.planned <= nowMillis.toLocalDateTime(zone).date
        return nowMillis - task.createdAtMillis >= minAgeMillis(task.rank)
    }

    fun complete(task: Task, nowMillis: Long, zone: TimeZone): Completion {
        check(task.state == TaskState.OPEN) { "only open tasks can be completed" }
        val pays = paysOnCompletion(task, nowMillis, zone)
        val onTime = task.due?.let { nowMillis.toLocalDateTime(zone) <= it } ?: false
        val done = task.copy(state = TaskState.DONE, completedAtMillis = nowMillis, paid = task.paid || pays)
        return Completion(done, pays, task.rank.tier, onTime, task.focusedMinutes > 0, nowMillis + UNDO_WINDOW_MILLIS)
    }

    fun canUndo(completion: Completion, nowMillis: Long): Boolean = nowMillis < completion.finalAtMillis

    /** Cancels a completion inside the undo window: the task is open again and nothing was paid. */
    fun undo(original: Task, completion: Completion, nowMillis: Long): Task {
        check(canUndo(completion, nowMillis)) { "the undo window has closed" }
        return original
    }

    /** Reopening a finished task keeps `paid`, so finishing it again never pays twice. */
    fun reopen(task: Task): Task {
        check(task.state == TaskState.DONE || task.state == TaskState.WONT_DO) { "only finished tasks can be reopened" }
        return task.copy(state = TaskState.OPEN, completedAtMillis = null)
    }

    /** "Won't do": no reward, hidden from stats. */
    fun wontDo(task: Task, nowMillis: Long): Task {
        check(task.isOpen) { "only open tasks can be marked won't do" }
        return task.copy(state = TaskState.WONT_DO, completedAtMillis = nowMillis)
    }

    fun delete(task: Task, nowMillis: Long): Task = task.copy(state = TaskState.DELETED, deletedAtMillis = nowMillis)

    fun canRestore(task: Task, nowMillis: Long): Boolean =
        task.state == TaskState.DELETED && task.deletedAtMillis != null && nowMillis - task.deletedAtMillis < RESTORE_WINDOW_MILLIS

    fun restore(task: Task, nowMillis: Long): Task {
        check(canRestore(task, nowMillis)) { "this task can no longer be restored" }
        return task.copy(state = TaskState.OPEN, deletedAtMillis = null)
    }

    /**
     * Ticks a subtask. It pays [SUBTASK_RARE_CANDIES] if it is old enough (same minimum age as the task's rank), not yet
     * paid, and fewer than [MAX_PAID_SUBTASKS] subtasks of this task have paid. Returns the task and whether this one paid.
     */
    fun completeSubtask(task: Task, subtaskId: String, nowMillis: Long): Pair<Task, Boolean> {
        val sub = task.subtasks.first { it.id == subtaskId }
        check(!sub.done) { "already done" }
        val paidSoFar = task.subtasks.count { it.paid }
        val pays = !task.ownedElsewhere && !sub.paid && paidSoFar < MAX_PAID_SUBTASKS && nowMillis - sub.createdAtMillis >= minAgeMillis(task.rank)
        val updated = task.subtasks.map { if (it.id == subtaskId) it.copy(done = true, paid = it.paid || pays) else it }
        return task.copy(subtasks = updated) to pays
    }
}
