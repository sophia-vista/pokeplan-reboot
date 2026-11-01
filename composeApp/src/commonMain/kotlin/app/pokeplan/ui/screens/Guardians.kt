package app.pokeplan.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import app.pokeplan.core.pokemon.Guardians
import app.pokeplan.core.reward.TrainerRank
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.TextBox

@Composable
internal fun GuardiansView(model: AppModel, open: (String) -> Unit) {
    val store = model.store
    val slots = store.guardianSlots
    val slotted = model.data.player.guardianPokemon
    val candidates = store.guardianCandidates

    // The screen header already says "Guardians", so the card holds only the one line that matters now.
    TextBox {
        Text(
            if (slots == 0) "A legendary or mythical Pokémon in a Guardian slot gives a big boost. The first slot opens at rank ${TrainerRank.names[4]}."
            else "${slotted.size} of $slots slots used. More open as your rank grows.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    repeat(slots) { i ->
        val g = slotted.getOrNull(i)
        if (g == null) {
            ListRow(lead = { ItemIcon("poke-ball", size = 48.dp) }) {
                Text("Empty slot", style = MaterialTheme.typography.bodyLarge, color = Pixel.InkSoft)
            }
        } else {
            ListRow(
                lead = { Sprite(g.form.id, g.isShiny, g.nickname, size = 64.dp) },
                onClick = { open(g.id) },
                trail = { PixelIconButton("trash", "Remove", { store.unslotGuardian(g.id) }, Pixel.InkSoft) },
            ) {
                Text(g.nickname, style = MaterialTheme.typography.titleMedium)
                Text(perkText(g), style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
            }
        }
    }
    if (candidates.isEmpty()) {
        EmptyState("No Guardians yet", "Legendary and mythical Pokémon come from quests, never from eggs.", icon = "master-ball")
    }
    candidates.filter { c -> slotted.none { it.id == c.id } }.forEach { g ->
        ListRow(
            lead = { Sprite(g.form.id, g.isShiny, g.nickname, size = 64.dp) },
            onClick = { open(g.id) },
            trail = { PixelButton("Slot", { store.slotGuardian(g.id) }, color = Pixel.Blue, enabled = slotted.size < slots, compact = true) },
        ) {
            Text(g.nickname, style = MaterialTheme.typography.titleMedium)
            Text(perkText(g), style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
        }
    }
}

// ----- Quests --------------------------------------------------------------------------------------------------------
