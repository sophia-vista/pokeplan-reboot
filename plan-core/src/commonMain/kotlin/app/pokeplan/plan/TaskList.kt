package app.pokeplan.plan

import kotlinx.serialization.Serializable

@Serializable
data class TaskList(
    val id: String,
    val name: String,
    val color: Long = COLORS.first(),
    val icon: String = ICONS.first(),
    val archived: Boolean = false,
    val kind: ListKind = ListKind.TASKS,
    val template: List<String> = emptyList(),
    val reset: ChecklistReset = ChecklistReset.NEVER,
    val resetOn: kotlinx.datetime.LocalDate? = null,
) {
    companion object {
        val COLORS: List<Long> = listOf(0xFFE5484DL, 0xFF3B82F6L, 0xFF22A06BL, 0xFFF59E0BL, 0xFF0D9488L, 0xFF8B5CF6L, 0xFFEC4899L, 0xFF64748BL)

        val ICONS: List<String> = listOf(
            "poke-ball", "great-ball", "premier-ball", "master-ball", "timer-ball", "friend-ball", "journal", "oaks-letter",
            "seal-bag", "town-map", "storage-key", "star-piece", "sweet-apple", "leftovers", "masterpiece-teacup", "gb-sounds",
        )

        const val MAX_NAME = 40
    }
}

@Serializable
enum class ListKind(val label: String, val hint: String) {
    TASKS("Tasks", "dated work"),
    CHECKLIST("Checklist", "tick off, then reset"),
    TRACKER("Tracker", "a long-running count"),
}

@Serializable
enum class ChecklistReset(val label: String) { NEVER("Never"), DAILY("Every day"), WEEKLY("Every week") }
