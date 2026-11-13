package app.pokeplan.ui.screens

import app.pokeplan.ui.theme.Sizes
import androidx.compose.foundation.layout.Spacer
import app.pokeplan.ui.theme.ModuleAccent
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
import app.pokeplan.core.pokemon.GearTool
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Glyph
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.theme.ChoiceRow
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import kotlinx.coroutines.delay

/**
 * Gear (docs/pokelife/MODULES/GEAR.md): a hub of handy tools. Every tool works from the first day; a helper Pokémon (one of the
 * tool's signature line that the player chose) is looks only: its sprite over the tool and one reaction when the tool is used.
 * Nothing here pays anything or is read by any other module.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GearScreen(model: AppModel, start: GearTool? = null, onClose: () -> Unit) = app.pokeplan.ui.theme.ModuleTone(app.pokeplan.ui.theme.ModuleAccent.GEAR) { GearBody(model, start, onClose) }

@Composable
private fun GearBody(model: AppModel, start: GearTool?, onClose: () -> Unit) {
    var open by remember { mutableStateOf(start) }
    // Each use of a tool pings its helper, which answers with its one reaction.
    var used by remember { mutableIntStateOf(0) }
    val tool = open
    val store = model.store
    val gear = model.data.gear
    if (tool == null) {
        var query by remember { mutableStateOf("") }
        var category by remember { mutableStateOf<ToolCategory?>(null) }
        SectionScaffold("Gear", null, onClose, icon = "explorer-kit", color = ModuleAccent.GEAR.header) {
            // The home: search first, then what you starred and used lately, then every tool by group. Search works offline.
            PixelField(query, { query = it.take(30) }, Modifier.fillMaxWidth(), singleLine = true, label = "Search")
            fun entry(t: ToolInfo) = MenuEntry(t.tool.name, t.tool.icon, t.tool.label)
            if (query.isNotBlank()) {
                val found = ToolCatalog.search(query, gear.favorites, gear.recents)
                if (found.isEmpty()) {
                    Text("No tool by that name", style = MaterialTheme.typography.titleSmall)
                    PixelButton("Show every tool", { query = "" }, Modifier.fillMaxWidth(), Pixel.Tone, compact = true)
                }
                found.forEach { t -> PixelButton("${t.tool.label}: ${t.description}", { query = ""; open = t.tool }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true) }
            } else {
                val starred = gear.favorites.mapNotNull { n -> GearTool.entries.firstOrNull { it.name == n } }
                if (starred.isNotEmpty()) {
                    FieldLabel("Favourites")
                    MenuGrid(starred.map { entry(ToolCatalog.info(it)) }) { key -> open = GearTool.valueOf(key) }
                }
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
        SectionScaffold(tool.label, null, { open = null }, icon = tool.icon, color = ModuleAccent.GEAR.header) {
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

/**
 * One row over a tool: its helper (only when one was chosen: a small sprite and one reaction after a use), a way to choose one when the player
 * owns a Pokémon that can help, and the star. A tool with no helper shows no placeholder. [used] changes on each use of the tool.
 */
@Composable
private fun HelperBar(model: AppModel, tool: GearTool, used: Int) {
    val store = model.store
    val helper = (store.gearHelper(tool) as? GearHelper.Owned)?.pokemon
    val candidates = store.gearCandidates(tool)
    var choosing by remember(tool) { mutableStateOf(false) }
    var reaction by remember(tool) { mutableStateOf(false) }
    LaunchedEffect(used) { if (used > 0) { reaction = true; delay(1400); reaction = false } }
    val starred = tool.name in model.data.gear.favorites
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        if (helper != null) {
            Box(Modifier.size(Sizes.IconL)) {
                AnimatedSprite(helper.form.id, helper.isShiny, helper.nickname, size = Sizes.IconL, animate = false)
                if (reaction) reactionFor(tool).let { (art, name) -> if (art) ItemIcon(name, Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-4).dp), size = Sizes.Icon, description = null) else Glyph(name, Pixel.Tone, Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-4).dp), size = Sizes.Icon) }
            }
            Text(helper.nickname, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, maxLines = 1)
        } else Spacer(Modifier.weight(1f))
        if (candidates.isNotEmpty()) PixelButton(if (choosing) "Done" else "Helper", { choosing = !choosing }, color = Pixel.InkSoft, compact = true)
        PixelIconButton("star", if (starred) "Starred" else "Star", { store.gearFavorite(tool, !starred) }, if (starred) Pixel.Tone else Pixel.InkSoft)
    }
    if (choosing) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            candidates.forEach { p ->
                ChoiceRow(selected = helper?.id == p.id, onClick = { store.assignGearHelper(tool, p.id); choosing = false }, lead = { AnimatedSprite(p.form.id, p.isShiny, p.nickname, size = Sizes.IconL, animate = false) }) {
                    Column {
                        Text("${p.nickname}, level ${p.level}", style = MaterialTheme.typography.bodyLarge)
                        // Choosing one takes it from where it is (one place only), so a party member or the buddy says so before the tap.
                        if (helper?.id != p.id && store.placeOf(p.id) != app.pokeplan.app.Place.Pc) {
                            Text(app.pokeplan.app.Places.label(model.data, p.id), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                        }
                    }
                }
            }
            if (helper != null) PixelButton("No helper", { store.clearGearHelper(tool); choosing = false }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        }
    }
}

/** The picture over the helper after a use: (true, item id) or (false, Pixelarticons glyph). */
private fun reactionFor(tool: GearTool): Pair<Boolean, String> = when (tool) {
    GearTool.FLASHLIGHT -> false to "zap"
    GearTool.CALCULATOR -> false to "check"
    GearTool.CURRENCY -> true to "amulet-coin"
    GearTool.PICKER -> true to "star-piece"
    GearTool.COIN -> true to "heart-scale"
    GearTool.UNITS -> false to "sliders"
    GearTool.TIMER -> false to "clock"
    GearTool.STOPWATCH -> false to "clock"
    GearTool.EVERYDAY -> false to "clipboard"
    GearTool.WORLD_CLOCK -> false to "globe"
    GearTool.COMPASS -> true to "town-map"
    GearTool.LEVEL -> false to "sliders"
    GearTool.BAROMETER -> false to "cloud"
    GearTool.DEVICE -> false to "battery"
    GearTool.WAYPOINTS -> false to "map-pin"
    GearTool.SOUND -> false to "volume-2"
    GearTool.QR -> false to "grid-2x2-2"
    GearTool.NETWORK -> false to "info-box"
}
