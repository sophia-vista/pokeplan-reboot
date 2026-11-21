package app.pokeplan.ui.launcher

import app.pokeplan.ui.Place
import kotlin.math.ceil
import kotlin.math.floor

class HostedWidgetSpec(
    val minWidthDp: Int, val minHeightDp: Int,
    val minResizeWidthDp: Int = 0, val minResizeHeightDp: Int = 0,
    val resizeHorizontal: Boolean = true, val resizeVertical: Boolean = true,
    val maxResizeWidthDp: Int = 0, val maxResizeHeightDp: Int = 0,
) {
    fun limits(columnDp: Float, gap: Int = WidgetGrid.CELL_GAP_DP): WidgetLimits {
        fun cells(dp: Int, cell: Float) = ceil((dp + 2 * gap) / cell).toInt().coerceAtLeast(1)
        fun most(dp: Int, cell: Float) = floor((dp + 2 * gap) / cell).toInt()
        val row = WidgetGrid.ROW_DP.toFloat()
        val startW = cells(minWidthDp, columnDp).coerceAtMost(WidgetGrid.COLUMNS)
        val startH = cells(minHeightDp, row).coerceIn(MIN_ROWS, MAX_ROWS)
        val lowW = if (resizeHorizontal && minResizeWidthDp > 0) cells(minResizeWidthDp, columnDp).coerceAtMost(startW) else startW
        val lowH = if (resizeVertical && minResizeHeightDp > 0) cells(minResizeHeightDp, row).coerceIn(MIN_ROWS, startH) else startH
        val highW = when {
            !resizeHorizontal -> startW
            maxResizeWidthDp > 0 -> most(maxResizeWidthDp, columnDp).coerceIn(startW, WidgetGrid.COLUMNS)
            else -> WidgetGrid.COLUMNS
        }
        val highH = when {
            !resizeVertical -> startH
            maxResizeHeightDp > 0 -> most(maxResizeHeightDp, row).coerceIn(startH, MAX_ROWS)
            else -> MAX_ROWS
        }
        return WidgetLimits(lowW, lowH, highW, highH, startW, startH)
    }

    private companion object { const val MIN_ROWS = 2; const val MAX_ROWS = 16 }
}

fun LauncherPrefs.hostedWidgetIds(): Set<Int> =
    (widgets.filter { it.kind == WidgetKind.ANDROID }.map { it.arg } + gridWidgets.filter { it.kind == WidgetKind.ANDROID }.map { it.arg }).mapNotNull { it.toIntOrNull() }.toSet()

class LauncherDestination(val label: String, val icon: String, val place: Place, private val words: List<String> = emptyList()) {
    internal fun matches(q: String) = foldedName(label).contains(q) || words.any { foldedName(it).contains(q) }
}

object LauncherSearch {
    val destinations: List<LauncherDestination> = listOf(
        LauncherDestination("PokéLife", "town-map", Place.HOME, listOf("overview", "today", "start")),
        LauncherDestination("Plan", "journal", Place.PLAN, listOf("tasks", "planner", "goals", "review", "habits")),
        LauncherDestination("Focus", "mystery-egg", Place.FOCUS, listOf("timer", "pomodoro", "egg")),
        LauncherDestination("Care", "soothe-bell", Place.CARE, listOf("mood", "routine", "calm", "diary", "meadow")),
        LauncherDestination("Heal", "potion", Place.HEAL, listOf("health", "water", "sleep", "activity", "hydration", "exercise")),
        LauncherDestination("Mint", "amulet-coin", Place.MINT, listOf("money", "budget", "bills", "finance", "accounts")),
        LauncherDestination("Base", "chipped-pot", Place.BASE, listOf("household", "chores", "supplies", "maintenance")),
        LauncherDestination("Link", "link-cord", Place.LINK, listOf("people", "contacts", "dates", "birthday", "follow-up")),
        LauncherDestination("Gear", "explorer-kit", Place.GEAR, listOf("tools", "utilities", "pokegear")),
        LauncherDestination("World", "town-map", Place.WORLD, listOf("dungeon", "expedition", "guild")),
        LauncherDestination("Pokémon", "poke-ball", Place.COLLECTION, listOf("collection", "pokedex", "pc", "box")),
        LauncherDestination("Party", "poke-ball", Place.PARTY, listOf("team", "lead")),
        LauncherDestination("Me", "member-card", Place.ME, listOf("profile", "settings", "backup", "export")),
    )

    fun find(query: String): List<LauncherDestination> {
        val q = foldedName(query)
        if (q.isEmpty()) return emptyList()
        return destinations.filter { it.matches(q) }.sortedBy { if (foldedName(it.label).startsWith(q)) 0 else 1 }
    }
}

fun LauncherHost.startingCells(id: Int): WidgetLimits? = widgetSpec(id)?.limits(WidgetGrid.NOMINAL_COLUMN_DP)
