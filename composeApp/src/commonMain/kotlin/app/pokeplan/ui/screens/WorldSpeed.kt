package app.pokeplan.ui.screens

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
import app.pokeplan.world.SpeedExplanation
import app.pokeplan.world.WorldData
import app.pokeplan.world.WorldSim

/** A number with one decimal ("152.4"), without `String.format`, which the web build does not have. */
internal fun oneDecimal(x: Double): String = (kotlin.math.round(x * 10) / 10.0).toString()

/** "12 min 30 s", "45 s": a duration in words. */
internal fun durationText(millis: Long): String {
    val seconds = millis / 1000
    return if (seconds >= 60) "${seconds / 60} min" + if (seconds % 60 != 0L) " ${seconds % 60} s" else "" else "$seconds s"
}

/** The steps of [SpeedExplanation] as plain lines, in the order the numbers are used. Abilities are listed last, as help for skills and not for speed. */
internal fun speedLines(e: SpeedExplanation): List<String> = buildList {
    add("Levels of the team: ${e.levels}.")
    add(if (e.favoredMembers == 0) "No one is of a type this place favours (+0)." else "${e.favoredMembers} of the team ${if (e.favoredMembers == 1) "is" else "are"} of a favoured type: +20% of their levels, +${oneDecimal(e.favoredBonus)}.")
    add("Power: ${oneDecimal(e.power)}. A floor takes the base time (${durationText(e.baseMillis)}) divided by 1 + power/100, so ${durationText(e.afterPowerMillis)}${if (e.atMinimum) ", the shortest it can be (a quarter of the base)" else ""}.")
    if (e.masteryPercent > 0) add("Temporal Mastery: ${e.masteryPercent}% faster.")
    if (e.legendPercent > 0) add("Legendary Guardians of this region: ${e.legendPercent}% faster.")
    add("Result: a floor takes about ${durationText(e.floorMillis)}.")
    if (e.skillHelp.isNotEmpty()) add("Abilities help skills, not speed: ${e.skillHelp.entries.joinToString(", ") { "${it.key.label} +${it.value}" }}.")
}

/** A "How fast?" toggle that opens the steps of [members]' speed in [dungeon]. */
@Composable
internal fun SpeedExplainer(members: List<OwnedPokemon>, dungeon: DungeonDef, world: WorldData) {
    var open by remember(dungeon.id, members.map { it.id }) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        PixelButton(if (open) "Hide how fast" else "How fast?", { open = !open }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        if (open) speedLines(WorldSim.explainSpeed(dungeon, members, world)).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

/** What every team is doing, in one place: a line each with where it is and how far, then the Finds waiting. Nothing here is saved or changes any rule. */
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
                    team.members.isEmpty() -> "Empty. Choose a team below."
                    else -> "Ready at home ($names)"
                }
                Text("Team ${team.id}: $line", style = MaterialTheme.typography.bodyMedium)
            }
            if (Missions.boardOpen(world)) {
                val ready = world.missions.count { it.done }
                Text("Jobs: ${world.missions.size} of ${Missions.MAX_ACTIVE} taken" + if (ready > 0) ", $ready ready to claim." else ".", style = MaterialTheme.typography.bodyMedium, color = if (ready > 0) Pixel.Green else Pixel.InkSoft)
            }
            val waiting = world.finds.size
            Text(
                if (waiting == 0) "No Pokémon are waiting to be recruited." else "$waiting ${if (waiting == 1) "Pokémon is" else "Pokémon are"} waiting to be recruited: open Finds and Bag.",
                style = MaterialTheme.typography.bodyMedium, color = if (waiting == 0) Pixel.InkSoft else Pixel.Green,
            )
        }
    }
}

/**
 * What the teams brought back since the player last looked, in three kinds so a chance is never read as a promise: what every floor gives,
 * the fixed milestone rewards, and the chance finds. Berries, Poké and milestone items are already in the Bag; Pokémon wait in Finds.
 * Shown until dismissed; not saved (the reward log is the record).
 */
@Composable
internal fun WorldReportCard(model: AppModel) {
    val report = model.store.worldReport() ?: return
    var gone by remember(report) { mutableStateOf(false) }
    if (gone) return
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("Since you last looked", style = MaterialTheme.typography.titleSmall)
            val where = report.byDungeon.entries.joinToString(", ") { (id, n) -> "${Dungeons.byId(id)?.name ?: id} $n" }
            Text("${report.floors} ${if (report.floors == 1) "floor" else "floors"} climbed ($where).", style = MaterialTheme.typography.bodyMedium)
            Text("Every floor: ${report.xp} Exploration XP and ${report.poke} Poké.", style = MaterialTheme.typography.bodyMedium)
            // The haul at a glance, with the games' own art: the coin for Poké and a box for each treasure tier.
            if (report.poke > 0 || report.treasure.isNotEmpty()) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                if (report.poke > 0) {
                    app.pokeplan.ui.ItemIcon(app.pokeplan.world.PmdArt.POKE_COIN, size = 28.dp, description = null)
                    Text("${report.poke}", style = MaterialTheme.typography.bodyLarge)
                }
                report.treasure.entries.sortedBy { it.key }.forEach { (tier, n) ->
                    app.pokeplan.ui.ItemIcon(app.pokeplan.world.PmdArt.treasureBox(tier.coerceIn(1, 3)), size = 28.dp, description = null)
                    Text("x$n", style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (report.milestones.isNotEmpty()) Text(
                "Milestones (first time on a floor): " + report.milestones.joinToString("; ") { (id, r) ->
                    "${Dungeons.byId(id)?.name ?: id}: " + listOfNotNull(r.itemId?.let { "${app.pokeplan.core.pokemon.Items.name(it)} x${r.count}" }, r.poke.takeIf { it > 0 }?.let { "$it Poké" }).joinToString(", ")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            val chance = buildList {
                if (report.berries > 0) add("${report.berries} ${if (report.berries == 1) "berry" else "berries"}")
                val boxes = report.treasure.values.sum()
                if (boxes > 0) add("$boxes treasure ${if (boxes == 1) "box" else "boxes"} (to open in Finds and Bag)")
            }
            if (chance.isNotEmpty()) Text("Found by chance: ${chance.joinToString(", ")}.", style = MaterialTheme.typography.bodyMedium)
            if (report.cleared.isNotEmpty()) Text("Cleared: ${report.cleared.distinct().joinToString(", ") { Dungeons.byId(it)?.name ?: it }}.", style = MaterialTheme.typography.bodyMedium)
            if (report.jobsDone.isNotEmpty()) Text("Jobs done: ${report.jobsDone.joinToString(", ") { app.pokeplan.world.Missions.byId(it)?.title ?: it }}. Claim them on the board.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
            if (report.eventsOpened > 0 || report.eventsPassed > 0) Text(
                buildString {
                    if (report.eventsOpened > 0) append("${report.eventsOpened} side paths opened with field moves")
                    if (report.eventsOpened > 0 && report.eventsPassed > 0) append("; ")
                    if (report.eventsPassed > 0) append("${report.eventsPassed} passed by (no one who could open them)")
                    append(".")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (report.guardianFaced) Text("A Guardian was faced. Its story is complete.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
            if (report.finds.isNotEmpty()) Text("Waiting to be recruited: ${report.finds.joinToString(", ") { app.pokeplan.core.dex.Dex.species(it).name }}. They stay in Finds until you decide.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
            Text("Berries, Poké and the items above are already in your Bag.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            PixelButton("OK", { model.store.worldReportDismiss(); gone = true }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        }
    }
}
