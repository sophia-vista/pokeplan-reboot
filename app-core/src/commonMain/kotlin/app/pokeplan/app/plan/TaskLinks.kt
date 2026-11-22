package app.pokeplan.app.plan

import app.pokeplan.base.ChoreState
import app.pokeplan.plan.LinkKind
import app.pokeplan.plan.TaskLink
import kotlinx.datetime.LocalDate
import app.pokeplan.app.base.BaseStore

sealed interface LinkState {
    data object Gone : LinkState

    data class Open(val title: String, val planned: LocalDate?, val occurrence: String) : LinkState

    data class Done(val atMillis: Long, val occurrence: String) : LinkState

    data object Inactive : LinkState
}

interface LinkOwner {
    val kind: LinkKind

    fun project(link: TaskLink): LinkState

    fun complete(link: TaskLink): Boolean

    fun skip(link: TaskLink): Boolean

    fun undo(link: TaskLink): Boolean
}

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
