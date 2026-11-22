package app.pokeplan.ui.plan

import app.pokeplan.plan.TaskList

/**
 * The colour names, for screen readers, in the order of [TaskList.COLORS]. The list editor and the event editor both use this
 * one list, so a colour is announced the same way everywhere and the names stay beside the order they describe.
 */
internal val taskColorNames: List<String> = listOf("Red", "Blue", "Green", "Amber", "Teal", "Purple", "Pink", "Slate")
