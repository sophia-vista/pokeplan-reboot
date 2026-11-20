package app.pokeplan.plan

import kotlinx.serialization.Serializable

/**
 * A list the player makes to file tasks under ("Groceries", "Thesis"), as in TickTick: a name, a colour and a real item
 * icon. A task is in at most one list ([Task.listId]); a task in none is in the Inbox. Archiving hides a list and its tasks
 * from the menu. Lists carry no reward.
 */
@Serializable
data class TaskList(
    val id: String,
    val name: String,
    /** ARGB, one of [COLORS]. */
    val color: Long = COLORS.first(),
    /** An item icon file name (assets/items), one of [ICONS]. */
    val icon: String = ICONS.first(),
    val archived: Boolean = false,
    /** What the list is for (PLAN.md 3.5A): a normal list of tasks, a checklist to tick off and reset, or a tracker of things to finish over time. */
    val kind: ListKind = ListKind.TASKS,
    /** A checklist's saved items ("Save items as template"); "Start from" makes a new checklist with them. Part of the list, no dates. */
    val template: List<String> = emptyList(),
    /** How often a checklist's ticks reset (unticked items carry no penalty); only a [ListKind.CHECKLIST] uses it. */
    val reset: ChecklistReset = ChecklistReset.NEVER,
    /** The day the checklist was last reset (or made), so a reset happens once per boundary. */
    val resetOn: kotlinx.datetime.LocalDate? = null,
) {
    companion object {
        /** Red, blue, green, amber, teal, purple, pink and slate. */
        val COLORS: List<Long> = listOf(0xFFE5484DL, 0xFF3B82F6L, 0xFF22A06BL, 0xFFF59E0BL, 0xFF0D9488L, 0xFF8B5CF6L, 0xFFEC4899L, 0xFF64748BL)

        /** Real item icons a list can wear. */
        val ICONS: List<String> = listOf(
            "poke-ball", "great-ball", "premier-ball", "master-ball", "timer-ball", "friend-ball", "journal", "oaks-letter",
            "seal-bag", "town-map", "storage-key", "star-piece", "sweet-apple", "leftovers", "masterpiece-teacup", "gb-sounds",
        )

        const val MAX_NAME = 40
    }
}

/** The kinds of list. The kind changes presentation and defaults only; every item is a task on the same engine. */
@Serializable
enum class ListKind(val label: String, val hint: String) {
    TASKS("Tasks", "dated work"),
    CHECKLIST("Checklist", "tick off, then reset"),
    TRACKER("Tracker", "a long-running count"),
}

/** When a checklist's ticks reset: never, each day, or each week (on [weekStart]). */
@Serializable
enum class ChecklistReset(val label: String) { NEVER("Never"), DAILY("Every day"), WEEKLY("Every week") }
