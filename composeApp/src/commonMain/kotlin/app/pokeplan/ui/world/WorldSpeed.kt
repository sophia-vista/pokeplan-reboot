package app.pokeplan.ui.world

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.pokeplan.ui.AppModel
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.world.DungeonDef
import app.pokeplan.world.Dungeons
import app.pokeplan.world.Missions
import app.pokeplan.world.Outcomes
import app.pokeplan.world.SpeedExplanation
import app.pokeplan.world.WorldData
import app.pokeplan.world.WorldSim
import app.pokeplan.ui.plan.duration
import app.pokeplan.ui.pokemon.label
import app.pokeplan.ui.screens.label
import app.pokeplan.app.world.worldReport
import app.pokeplan.app.world.worldReportDismiss

internal fun oneDecimal(x: Double): String = (kotlin.math.round(x * 10) / 10.0).toString()

internal fun durationText(millis: Long): String {
    val seconds = millis / 1000
    return if (seconds >= 60) "${seconds / 60} min" + if (seconds % 60 != 0L) " ${seconds % 60} s" else "" else "$seconds s"
}

internal fun speedLines(e: SpeedExplanation): List<String> = buildList {
    add("Levels of the team: ${e.levels}.")
    add(if (e.favoredMembers == 0) "No one is of a type this place favours (+0)." else "${e.favoredMembers} of the team ${if (e.favoredMembers == 1) "is" else "are"} of a favoured type: +20% of their levels, +${oneDecimal(e.favoredBonus)}.") // copy-ok: game
    if (e.identityLines.isNotEmpty()) {
        add("Moves, abilities and held items add +${oneDecimal(e.identityBonus)} (a share of each level):") // copy-ok: game
        addAll(e.identityLines)
    } else add("No move, ability or held item of this team helps here (+0).") // copy-ok: game
    add("Power ${oneDecimal(e.power)}: ${durationText(e.afterPowerMillis)} a floor${if (e.atMinimum) " (the shortest)" else ""}")
    if (e.masteryPercent > 0) add("Temporal Mastery: ${e.masteryPercent}% faster.")
    if (e.legendPercent > 0) add("Legendary Guardians of this region: ${e.legendPercent}% faster.")
    add("Result: a floor takes about ${durationText(e.floorMillis)}.")
    if (e.skillHelp.isNotEmpty()) add("Abilities help skills, not speed: ${e.skillHelp.entries.joinToString(", ") { "${it.key.label} +${it.value}" }}.")
}

@Composable
internal fun SpeedExplainer(members: List<OwnedPokemon>, dungeon: DungeonDef, world: WorldData, roster: List<OwnedPokemon> = members) {
    var open by remember(dungeon.id, members.map { it.id }) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        PixelButton(if (open) "Hide how fast" else "How fast?", { open = !open }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        if (open) {
            val explained = WorldSim.explainSpeed(dungeon, members, world, roster)
            speedLines(explained).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(Outcomes.explain(explained.power, dungeon), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun ExpeditionOverview(model: AppModel) {
    val world = model.data.world
    val roster = model.data.player.pokemon
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("All teams", style = MaterialTheme.typography.titleSmall)
            world.slots().forEach { team ->
                val trip = team.expedition
                val names = team.members.mapNotNull { id -> roster.firstOrNull { it.id == id } }.joinToString(", ") { it.nickname }
                val line = when {
                    trip != null -> {
                        val dungeon = Dungeons.byId(trip.dungeonId)
                        val floor = (trip.floorsClimbed % (dungeon?.floors ?: 1)) + 1
                        "Exploring ${dungeon?.name ?: "somewhere"}, floor $floor of ${dungeon?.floors ?: "?"} ($names)"
                    }
                    team.members.isEmpty() -> "Empty"
                    else -> "Ready at home ($names)"
                }
                Text("Team ${team.id}: $line", style = MaterialTheme.typography.bodyMedium)
            }
            if (Missions.boardOpen(world)) {
                val ready = world.missions.count { it.done }
                if (ready > 0) Text("Jobs: $ready ready to report", style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
            }
            val waiting = world.finds.size
            if (waiting > 0) Text("$waiting waiting to be recruited", style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
        }
    }
}

@Composable
internal fun WorldReportCard(model: AppModel) {
    val report = model.store.worldReport() ?: return
    var gone by remember(report) { mutableStateOf(false) }
    if (gone) return
    val floors = "${report.floors} ${if (report.floors == 1) "floor" else "floors"}"
    TextBox {
        app.pokeplan.ui.theme.Accordion("Report", summary = "$floors climbed", startOpen = report.finds.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("$floors climbed", style = MaterialTheme.typography.titleSmall)
            if (report.poke > 0 || report.berries > 0 || report.treasure.isNotEmpty()) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                if (report.poke > 0) {
                    app.pokeplan.ui.ItemIcon(app.pokeplan.world.PmdArt.POKE_COIN, size = 28.dp, description = null)
                    Text("${report.poke}", style = MaterialTheme.typography.bodyLarge)
                }
                if (report.berries > 0) {
                    app.pokeplan.ui.ItemIcon("berry-sweet", size = 28.dp, description = "Berries")
                    Text("x${report.berries}", style = MaterialTheme.typography.bodyLarge)
                }
                report.treasure.entries.sortedBy { it.key }.forEach { (tier, n) ->
                    app.pokeplan.ui.ItemIcon(app.pokeplan.world.PmdArt.treasureBox(tier.coerceIn(1, 3)), size = 28.dp, description = null)
                    Text("x$n", style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (report.cleared.isNotEmpty()) Text("Cleared: ${report.cleared.distinct().joinToString(", ") { Dungeons.byId(it)?.name ?: it }}", style = MaterialTheme.typography.bodyMedium)
            if (report.jobsDone.isNotEmpty()) Text("Jobs done: ${report.jobsDone.joinToString(", ") { app.pokeplan.world.Missions.byId(it)?.title ?: it }}", style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
            if (report.finds.isNotEmpty()) Text("Waiting to be recruited: ${report.finds.joinToString(", ") { app.pokeplan.core.dex.Dex.species(it).name }}", style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
            PixelButton("OK", { model.store.worldReportDismiss(); gone = true }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        }
        }
    }
}
