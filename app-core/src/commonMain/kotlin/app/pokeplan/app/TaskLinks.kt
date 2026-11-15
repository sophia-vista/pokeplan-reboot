package app.pokeplan.app

import app.pokeplan.base.ChoreState
import app.pokeplan.plan.LinkKind
import app.pokeplan.plan.TaskLink
import kotlinx.datetime.LocalDate

/**
 * What a record that a PLAN task is linked to looks like right now, as its owning module reports it (docs/pokelife `PRODUCT_BIBLE.md` section 4). PLAN keeps
 * only the reference and a projection of this; it never decides the record's state itself.
 */
sealed interface LinkState {
    /** The record no longer exists (or is not the one the link named): the projection goes away, the other module is untouched. */
    data object Gone : LinkState

    /** Waiting to be done: the words and the day to show, and which occurrence this is (a repeating chore moves on to the next one when its turn comes). */
    data class Open(val title: String, val planned: LocalDate?, val occurrence: String) : LinkState

    /** Done by its owner at [atMillis]. */
    data class Done(val atMillis: Long, val occurrence: String) : LinkState

    /** Skipped, paused or archived by its owner: nothing is waiting, nothing was done. */
    data object Inactive : LinkState
}

/**
 * A module that owns the completion of the tasks linked to its records (HOME for a chore). PLAN asks it; it never completes or pays on its own. Completing a
 * linked task from PLAN calls [complete], so the owner records the one real completion and pays its own reward once, and PLAN pays nothing
 * ([app.pokeplan.plan.TaskRules.paysOnCompletion]).
 */
interface LinkOwner {
    val kind: LinkKind

    fun project(link: TaskLink): LinkState

    /** Does the owner's own completion operation for the record. False when it could not (gone, already done, paused). */
    fun complete(link: TaskLink): Boolean

    /** Skips the record's current occurrence in the owner ("won't do" in PLAN). */
    fun skip(link: TaskLink): Boolean

    /** Takes back the owner's completion of the linked occurrence (a reopen in PLAN). What it paid stays paid and is not paid twice. */
    fun undo(link: TaskLink): Boolean
}

/**
 * HOME's side of the link (BASE.md 2.1): the chore is HOME's, its schedule, occurrences and history stay in HOME, and a linked task is a reference by the chore's
 * id. Completing it from PLAN runs [BaseStore.complete], the one completion, which pays Habitat Discovery once for that occurrence however it is ticked.
 */
class BaseLinkOwner(private val home: BaseStore) : LinkOwner {
    override val kind = LinkKind.HOME_CHORE

    override fun project(link: TaskLink): LinkState {
        val v = home.choreView(link.ref) ?: return LinkState.Gone
        val occurrence = v.occurrence?.toString().orEmpty()
        return when (v.state) {
            ChoreState.OVERDUE, ChoreState.DUE, ChoreState.UPCOMING, ChoreState.ANYTIME -> LinkState.Open(v.chore.title, v.dueDay, occurrence)
            ChoreState.DONE -> LinkState.Done(v.entry?.atMillis ?: 0L, occurrence)
            ChoreState.SKIPPED, ChoreState.PAUSED, ChoreState.ARCHIVED -> LinkState.Inactive
        }
    }

    override fun complete(link: TaskLink): Boolean = home.complete(link.ref).record != null

    override fun skip(link: TaskLink): Boolean = home.skip(link.ref)

    override fun undo(link: TaskLink): Boolean {
        val v = home.choreView(link.ref) ?: return false
        val entry = v.entry?.takeIf { it.status == app.pokeplan.base.EntryStatus.DONE } ?: return false
        home.undo(entry.id)
        return true
    }
}
