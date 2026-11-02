package app.pokeplan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import app.pokeplan.ui.gear.ToolCatalog
import app.pokeplan.ui.gear.ToolCategory
import app.pokeplan.ui.gear.ToolInfo
import app.pokeplan.ui.theme.PixelField
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GearScreen(model: AppModel, start: GearTool? = null, onClose: () -> Unit) {
    var open by remember { mutableStateOf(start) }
    // Each use of a tool pings its helper, which answers with its one reaction.
    var used by remember { mutableIntStateOf(0) }
    val tool = open
    val store = model.store
    val gear = model.data.gear
    if (tool == null) {
        var query by remember { mutableStateOf("") }
        var category by remember { mutableStateOf<ToolCategory?>(null) }
        SectionScaffold("Pokégear", null, onClose, icon = "explorer-kit") {
            // The home: search first, then what you starred and used lately, then every tool by group. Search works offline.
            PixelField(query, { query = it.take(30) }, Modifier.fillMaxWidth(), singleLine = true, label = "Search the tools (lbs, yen, torch, laps)")
            fun entry(t: ToolInfo) = MenuEntry(t.tool.name, t.tool.icon, t.tool.label)
            if (query.isNotBlank()) {
                val found = ToolCatalog.search(query, gear.favorites, gear.recents)
                if (found.isEmpty()) Text("No tool by that name. Units and Currency have their own search for the thing you want to convert.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                found.forEach { t -> PixelButton("${t.tool.label}: ${t.description}", { query = ""; open = t.tool }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true) }
            } else {
                val starred = gear.favorites.mapNotNull { n -> GearTool.entries.firstOrNull { it.name == n } }
                if (starred.isNotEmpty()) {
                    FieldLabel("Favourites")
                    MenuGrid(starred.map { entry(ToolCatalog.info(it)) }) { key -> open = GearTool.valueOf(key) }
                }
                val lately = gear.recents.mapNotNull { n -> GearTool.entries.firstOrNull { it.name == n } }.filter { it !in starred }.take(4)
                if (lately.isNotEmpty()) {
                    FieldLabel("Used lately")
                    MenuGrid(lately.map { entry(ToolCatalog.info(it)) }) { key -> open = GearTool.valueOf(key) }
                }
                FieldLabel("All tools")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    TabButton("All", category == null, { category = null })
                    ToolCategory.entries.forEach { c -> TabButton(c.label, category == c, { category = c }) }
                }
                MenuGrid(ToolCatalog.all.filter { category == null || it.category == category }.map { entry(it) }) { key -> open = GearTool.valueOf(key) }
            }
        }
    } else {
        // Opening a tool puts it first among the ones used lately (a short list kept on this phone).
        LaunchedEffect(tool) { store.gearUsed(tool) }
        SectionScaffold(tool.label, "Pokégear", { open = null }, icon = tool.icon) {
            val starred = tool.name in gear.favorites
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                Text(ToolCatalog.info(tool).description, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                PixelButton(if (starred) "Starred" else "Star", { store.gearFavorite(tool, !starred) }, color = if (starred) Pixel.Green else Pixel.InkSoft, compact = true)
            }
            HelperBar(model, tool, used)
            val onUse: () -> Unit = { used++ }
            when (tool) {
                GearTool.FLASHLIGHT -> FlashlightTool(model, onUse)
                GearTool.CALCULATOR -> CalculatorTool(model, onUse)
                GearTool.CURRENCY -> CurrencyTool(model, onUse)
                GearTool.PICKER -> PickerTool(model, onUse)
                GearTool.COIN -> CoinTool(model, onUse)
                GearTool.UNITS -> UnitsTool(model, onUse)
                GearTool.TIMER -> TimerTool(model, onUse)
                GearTool.STOPWATCH -> StopwatchTool(model, onUse)
                GearTool.EVERYDAY -> EverydayTool(model, onUse)
                GearTool.WORLD_CLOCK -> WorldClockTool(model, onUse)
                GearTool.COMPASS -> CompassTool(model, onUse)
                GearTool.LEVEL -> LevelTool(model, onUse)
                GearTool.BAROMETER -> BarometerTool(model, onUse)
                GearTool.DEVICE -> DeviceTool(model)
                GearTool.WAYPOINTS -> WaypointsTool(model, onUse)
                GearTool.SOUND -> SoundTool(model, onUse)
                GearTool.QR -> QrTool(model, onUse)
                GearTool.NETWORK -> NetworkTool(model, onUse)
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
    val compact = model.data.gear.helperCompact
    // A small strip by default, so the helper never takes the room the tool needs; "Show" opens the full bar and "Make it small" folds it back.
    if (compact) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Box(Modifier.size(40.dp)) {
                when (helper) {
                    is GearHelper.Owned -> AnimatedSprite(helper.pokemon.form.id, helper.pokemon.isShiny, helper.pokemon.nickname, size = 40.dp, animate = false)
                    is GearHelper.Loaner -> AnimatedSprite(Dex.defaultForm(helper.speciesId).id, false, Dex.species(helper.speciesId).name, size = 40.dp, animate = false)
                    GearHelper.None -> ItemIcon(tool.icon, Modifier.align(Alignment.Center), size = 32.dp, description = null)
                }
            }
            Text(when (helper) { is GearHelper.Owned -> "${helper.pokemon.nickname} helps"; is GearHelper.Loaner -> "${Dex.species(helper.speciesId).name} is lending a hand"; GearHelper.None -> "No helper" }, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            PixelButton("Show", { store.changeGear { it.copy(helperCompact = false) } }, color = Pixel.InkSoft, compact = true)
        }
        return
    }
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
                PixelButton("Make it small", { store.changeGear { it.copy(helperCompact = true) } }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
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
    GearTool.UNITS -> "📏"
    GearTool.TIMER -> "⏰"
    GearTool.STOPWATCH -> "⏱"
    GearTool.EVERYDAY -> "🧾"
    GearTool.WORLD_CLOCK -> "🌍"
    GearTool.COMPASS -> "🧭"
    GearTool.LEVEL -> "📐"
    GearTool.BAROMETER -> "🌤"
    GearTool.DEVICE -> "📱"
    GearTool.WAYPOINTS -> "📍"
    GearTool.SOUND -> "🔊"
    GearTool.QR -> "🔳"
    GearTool.NETWORK -> "📶"
}
