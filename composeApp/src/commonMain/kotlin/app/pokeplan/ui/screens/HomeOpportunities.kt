package app.pokeplan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox

/** What a live line's key opens; the screen owns the routing, the selection stays pure. */
enum class HomeLiveAction { WORLD_REPORT, WORLD_BOARD, WORLD_FINDS, MINT_OUTBREAK, CARE_DREAM, LINK_SAFARI, PLAN_CALENDAR }

/** One thing that is live now and will not wait: a short line and the one key that deals with it. */
data class HomeLiveLine(val id: String, val text: String, val action: String, val urgency: Int, val kind: HomeLiveAction)

/**
 * The save-derived facts "Happening now" reads, gathered once and flattened to counts and flags so the
 * selection below is a pure function that can be tested without a store, a clock or a device.
 */
data class HomeLive(
    val reportFloors: Int = 0,
    val floorDue: Boolean = false,
    val floorDueWhere: String = "",
    val jobsReady: Int = 0,
    val finds: Int = 0,
    val outbreaks: Int = 0,
    val dreamers: Int = 0,
    val safariVisits: Int = 0,
    val companionCandies: Int = 0,
    val companionTitle: String = "",
)

/**
 * Home's time-sensitive strip. Only things that are actionable now and will expire are listed, one line
 * each, most urgent first, and nothing at all when the save has nothing live: it is not a dashboard.
 */
object HomeOpportunities {
    /** At most this many lines, so the strip stays a strip. */
    const val SHOWN = 4

    fun select(live: HomeLive): List<HomeLiveLine> {
        val lines = buildList {
            if (live.reportFloors > 0) add(HomeLiveLine("world-report", "Expedition report: ${count(live.reportFloors, "floor", "floors")} climbed", "Open", 5, HomeLiveAction.WORLD_REPORT))
            if (live.jobsReady > 0) add(HomeLiveLine("world-jobs", "${count(live.jobsReady, "job", "jobs")} ready to report", "Report", 5, HomeLiveAction.WORLD_BOARD))
            if (live.outbreaks > 0) add(HomeLiveLine("mint-outbreak", if (live.outbreaks == 1) "An outbreak is ready to claim" else "${count(live.outbreaks, "outbreak", "outbreaks")} ready to claim", "Claim", 4, HomeLiveAction.MINT_OUTBREAK))
            if (live.floorDue) {
                val where = live.floorDueWhere.takeIf { it.isNotBlank() }?.let { " in $it" } ?: ""
                add(HomeLiveLine("world-floor", "A new floor is waiting$where", "Open", 3, HomeLiveAction.WORLD_REPORT))
            }
            if (live.finds > 0) add(HomeLiveLine("world-finds", "${count(live.finds, "Pokémon is", "Pokémon are")} waiting in Finds", "Meet", 3, HomeLiveAction.WORLD_FINDS))
            if (live.dreamers > 0) add(HomeLiveLine("care-dream", "${count(live.dreamers, "dreamer is", "dreamers are")} waiting to visit", "Visit", 2, HomeLiveAction.CARE_DREAM))
            if (live.safariVisits > 0) add(HomeLiveLine("link-safari", "${count(live.safariVisits, "Safari visit is", "Safari visits are")} open", "Visit", 2, HomeLiveAction.LINK_SAFARI))
            if (live.companionCandies > 0) {
                val who = live.companionTitle.takeIf { it.isNotBlank() }?.let { "$it: " } ?: ""
                add(HomeLiveLine("plan-candies", "${who}${count(live.companionCandies, "candy", "candies")} waiting", "Open", 1, HomeLiveAction.PLAN_CALENDAR))
            }
        }
        return lines.sortedByDescending { it.urgency }.take(SHOWN)
    }

    private fun count(n: Int, one: String, many: String): String = if (n == 1) "1 $one" else "$n $many"
}

/** The compact strip. One line per live thing, its key at the end of the line, a full-width key at large text. */
@Composable
internal fun HomeLiveCard(lines: List<HomeLiveLine>, onAction: (HomeLiveAction) -> Unit) {
    if (lines.isEmpty()) return
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Happening now")
            lines.forEach { line ->
                val key = @Composable { PixelButton(line.action, { onAction(line.kind) }, color = toneOf(line.kind), compact = true) }
                if (LocalDensity.current.fontScale > 1.3f) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Text(line.text, style = MaterialTheme.typography.bodyLarge)
                        PixelButton(line.action, { onAction(line.kind) }, Modifier.fillMaxWidth(), toneOf(line.kind), compact = true)
                    }
                } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Text(line.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    key()
                }
            }
        }
    }
}

private fun toneOf(kind: HomeLiveAction) = when (kind) {
    HomeLiveAction.WORLD_REPORT, HomeLiveAction.WORLD_BOARD, HomeLiveAction.WORLD_FINDS -> app.pokeplan.ui.theme.ModuleAccent.WORLD.accent
    HomeLiveAction.MINT_OUTBREAK -> app.pokeplan.ui.theme.ModuleAccent.MINT.accent
    HomeLiveAction.CARE_DREAM -> app.pokeplan.ui.theme.ModuleAccent.CARE.accent
    HomeLiveAction.LINK_SAFARI -> app.pokeplan.ui.theme.ModuleAccent.LINK.accent
    HomeLiveAction.PLAN_CALENDAR -> Pixel.InkSoft
}
