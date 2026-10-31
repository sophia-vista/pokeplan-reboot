package app.pokeplan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pokeplan.app.GearHelper
import app.pokeplan.core.dex.Dex
import app.pokeplan.core.pokemon.GearTool
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.theme.ChoiceRow
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import kotlinx.coroutines.delay

/**
 * The Pokégear (docs/ecosystem.md, "Pokégear: utilities and helper Pokémon"): a hub of handy tools reachable from anywhere.
 * Every tool works from the first day; a helper Pokémon (one of the tool's signature line, or a loaner from a Ranger
 * request) is flavour only: its sprite beside the tool and one reaction when the tool is used. Nothing here pays anything
 * or is read by any other module.
 */
@Composable
fun GearScreen(model: AppModel, start: GearTool? = null, onClose: () -> Unit) {
    var open by remember { mutableStateOf(start) }
    // Each use of a tool pings its helper, which answers with its one reaction.
    var used by remember { mutableIntStateOf(0) }
    val tool = open
    if (tool == null) {
        SectionScaffold("Pokégear", null, onClose, icon = "explorer-kit") {
            MenuGrid(GearTool.entries.map { MenuEntry(it.name, it.icon, it.label) }) { key -> open = GearTool.valueOf(key) }
        }
    } else {
        SectionScaffold(tool.label, "Pokégear", { open = null }, icon = tool.icon) {
            HelperBar(model, tool, used)
            val onUse: () -> Unit = { used++ }
            when (tool) {
                GearTool.FLASHLIGHT -> FlashlightTool(model, onUse)
                GearTool.CALCULATOR -> CalculatorTool(onUse)
                GearTool.CURRENCY -> CurrencyTool(model, onUse)
                GearTool.PICKER -> PickerTool(model, onUse)
                GearTool.COIN -> CoinTool(model, onUse)
            }
        }
    }
}

/** The helper of a tool: its sprite and name, a way to change it, and a Ranger request when there is none. [used] changes on each use of the tool. */
@Composable
private fun HelperBar(model: AppModel, tool: GearTool, used: Int) {
    val store = model.store
    val helper = store.gearHelper(tool)
    var choosing by remember(tool) { mutableStateOf(false) }
    var ranger by remember(tool) { mutableStateOf(false) }
    // One emoji over the helper after each use, then it fades.
    var reaction by remember(tool) { mutableStateOf(false) }
    LaunchedEffect(used) { if (used > 0) { reaction = true; delay(1400); reaction = false } }
    val reduce = model.data.settings.reduceMotion
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Box(Modifier.size(72.dp)) {
                    when (helper) {
                        is GearHelper.Owned -> AnimatedSprite(helper.pokemon.form.id, helper.pokemon.isShiny, helper.pokemon.nickname, size = 72.dp, animate = !reduce)
                        is GearHelper.Loaner -> AnimatedSprite(Dex.defaultForm(helper.speciesId).id, false, Dex.species(helper.speciesId).name, size = 72.dp, animate = !reduce)
                        GearHelper.None -> ItemIcon(tool.icon, Modifier.align(Alignment.Center), size = 48.dp, description = null)
                    }
                    if (reaction && helper !is GearHelper.None) Text(reactionFor(tool), Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-4).dp), style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    when (helper) {
                        is GearHelper.Owned -> {
                            Text("${helper.pokemon.nickname} helps", style = MaterialTheme.typography.titleSmall)
                            Text("${Dex.species(helper.pokemon.speciesId).name} can help ${tool.label}.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                        }
                        is GearHelper.Loaner -> {
                            Text("${Dex.species(helper.speciesId).name} is lending a hand", style = MaterialTheme.typography.titleSmall)
                            Text("On loan from a Ranger until you have one of your own.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                        }
                        GearHelper.None -> {
                            Text("No helper yet", style = MaterialTheme.typography.titleSmall)
                            Text("The tool works without one.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                if (store.gearCandidates(tool).isNotEmpty()) PixelButton(if (choosing) "Done" else "Change helper", { choosing = !choosing }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                if (helper is GearHelper.None) PixelButton("Ask a Ranger", { ranger = !ranger }, Modifier.weight(1f), Pixel.Blue, compact = true)
            }
            if (choosing) {
                FieldLabel("Who helps ${tool.label}")
                store.gearCandidates(tool).forEach { p ->
                    ChoiceRow(selected = helper is GearHelper.Owned && helper.pokemon.id == p.id, onClick = { store.assignGearHelper(tool, p.id) }, lead = { AnimatedSprite(p.form.id, p.isShiny, p.nickname, size = 40.dp, animate = false) }) {
                        Text("${p.nickname}, level ${p.level}", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (model.data.gearHelpers[tool.name] != null) PixelButton("The best friend helps", { store.clearGearHelper(tool) }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            }
            if (ranger && helper is GearHelper.None) RangerStyler(model, tool) { ranger = false }
        }
    }
}

private fun reactionFor(tool: GearTool): String = when (tool) {
    GearTool.FLASHLIGHT -> "💡"
    GearTool.CALCULATOR -> "👍"
    GearTool.CURRENCY -> "💰"
    GearTool.PICKER -> "✨"
    GearTool.COIN -> "🎉"
}
