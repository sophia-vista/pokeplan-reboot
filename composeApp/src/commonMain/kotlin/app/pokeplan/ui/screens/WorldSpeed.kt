package app.pokeplan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.pokeplan.world.SpeedExplanation
import app.pokeplan.world.WorldData
import app.pokeplan.world.WorldSim

/** "12 min 30 s", "45 s": a duration in words. */
internal fun durationText(millis: Long): String {
    val seconds = millis / 1000
    return if (seconds >= 60) "${seconds / 60} min" + if (seconds % 60 != 0L) " ${seconds % 60} s" else "" else "$seconds s"
}

/** The steps of [SpeedExplanation] as plain lines, in the order the numbers are used. Abilities are listed last, as help for skills and not for speed. */
internal fun speedLines(e: SpeedExplanation): List<String> = buildList {
    add("Levels of the team: ${e.levels}.")
    add(if (e.favoredMembers == 0) "No one is of a type this place favours (+0)." else "${e.favoredMembers} of the team ${if (e.favoredMembers == 1) "is" else "are"} of a favoured type: +20% of their levels, +${"%.1f".format(e.favoredBonus)}.")
    add("Power: ${"%.1f".format(e.power)}. A floor takes the base time (${durationText(e.baseMillis)}) divided by 1 + power/100, so ${durationText(e.afterPowerMillis)}${if (e.atMinimum) ", the shortest it can be (a quarter of the base)" else ""}.")
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
            val waiting = world.finds.size
            Text(
                if (waiting == 0) "No Pokémon are waiting to be recruited." else "$waiting ${if (waiting == 1) "Pokémon is" else "Pokémon are"} waiting to be recruited: open Finds and Bag.",
                style = MaterialTheme.typography.bodyMedium, color = if (waiting == 0) Pixel.InkSoft else Pixel.Green,
            )
        }
    }
}
