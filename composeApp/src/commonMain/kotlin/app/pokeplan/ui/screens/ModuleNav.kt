package app.pokeplan.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Place
import app.pokeplan.ui.base.BaseSection
import app.pokeplan.ui.care.CareSection
import app.pokeplan.ui.heal.HealSection
import app.pokeplan.ui.link.LinkSection
import app.pokeplan.ui.mint.MintSection
import app.pokeplan.ui.theme.KeyLabel
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelKey
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.world.WorldSection

/*
 * Moving around inside the Modules tab (docs/pokelife/FEATURES/DESIGN_SYSTEM.md 6.7, REP-067):
 *  - a module's page is its daily view; its sections wait behind the labelled "Sections" key in its header, in a sheet of rows;
 *  - the labelled "Modules" key opens a sheet to switch to another module in one tap, with a "Jump to" search for any section;
 *  - the Modules tab carries the same search above its list.
 * Nothing here reads a figure, a name or a private word: the search only knows the fixed names of the sections.
 */

/** Whether Focus exists on this platform (false on desktop and the web), for screens that are not handed it. `ModulesScreen` provides it. */
val LocalFocusTab = staticCompositionLocalOf { true }

/**
 * The two keys at the foot of a module's header: **Sections** opens [sections] as rows, **Modules** switches module. [onSection]
 * is given the key of the tapped row.
 */
@Composable
fun ModuleNavKeys(model: AppModel, module: Module, sections: List<MenuEntry>, onSection: (String) -> Unit) {
    var showSections by remember { mutableStateOf(false) }
    var showModules by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        PixelKey({ showSections = true }, key = Pixel.InkSoft, compact = true) { look -> KeyLabel("Sections", look) }
        PixelKey({ showModules = true }, key = Pixel.InkSoft, compact = true) { look -> KeyLabel("Modules", look) }
    }
    if (showSections) NavSheet(module.label, { showSections = false }) {
        MenuRows(sections) { key -> showSections = false; onSection(key) }
    }
    if (showModules) NavSheet("Modules", { showModules = false }) {
        JumpSearch(model, boxed = false, onDone = { showModules = false })
        MenuRows(moduleEntries(model, module)) { key ->
            showModules = false
            if (key != module.name) model.open(Place.valueOf(key), true)
        }
    }
}

/** The modules in the order of the accent rainbow, the open one marked; Care only while it is switched on in Settings. */
private fun moduleEntries(model: AppModel, current: Module?): List<MenuEntry> = listOf(Module.PLAN, Module.MINT, Module.BASE, Module.CARE, Module.HEAL, Module.LINK, Module.WORLD)
    .filter { it != Module.CARE || model.data.settings.careModule }
    .map { m -> MenuEntry(m.name, m.icon, m.label, count = if (m == current) "open" else "") }

/**
 * A sheet that rises from the bottom over the page: one dialogue box with a title, [content] and a Close key. A tap outside it, or
 * the system back gesture, closes it.
 */
@Composable
fun NavSheet(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(Modifier.widthIn(max = 640.dp).padding(Spacing.L).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
                TextBox {
                    Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        PixelTitle(title)
                        content()
                        PixelButton("Close", onClose, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                    }
                }
            }
        }
    }
}

/** One place the search can open: [title] in [area], with the row's [icon]. */
internal class JumpTarget(val title: String, val area: String, val icon: String, val go: () -> Unit)

/** Every module, every section of every module and Gear, by their fixed names. Care only while it is on; Focus and Import only where they exist. */
internal fun jumpTargets(model: AppModel, focus: Boolean): List<JumpTarget> = buildList {
    val careOn = model.data.settings.careModule
    add(JumpTarget("Gear", "Tools", "explorer-kit") { model.gearOpen = true })
    Module.entries.forEach { m ->
        if (m == Module.CARE && !careOn) return@forEach
        add(JumpTarget(m.label, "Module", m.icon) { model.open(Place.valueOf(m.name), focus) })
    }
    PlanSection.entries.filter { it != PlanSection.MORE && (it != PlanSection.FOCUS || focus) }.forEach { s ->
        add(JumpTarget(s.label, "Plan", s.icon) { model.open(Place.PLAN, focus); model.planSection = s })
    }
    if (careOn) CareSection.entries.forEach { s -> add(JumpTarget(s.label, "Care", s.icon) { model.open(Place.CARE, focus); model.careSection = s }) }
    HealSection.entries.forEach { s -> add(JumpTarget(s.label, "Heal", s.icon) { model.open(Place.HEAL, focus); model.healSection = s }) }
    MintSection.entries.filter { it != MintSection.IMPORT || model.textFiles != null }.forEach { s ->
        add(JumpTarget(s.label, "Mint", s.icon) { model.open(Place.MINT, focus); model.mintStartType = null; model.mintSection = s })
    }
    BaseSection.entries.forEach { s -> add(JumpTarget(s.label, "Base", s.icon) { model.open(Place.BASE, focus); model.baseSection = s }) }
    LinkSection.entries.forEach { s -> add(JumpTarget(s.label, "Link", s.icon) { model.open(Place.LINK, focus); model.linkSection = s }) }
    WorldSection.entries.forEach { s -> add(JumpTarget(s.label, "World", s.icon) { model.open(Place.WORLD, focus); model.worldSection = s }) }
}

/** The places that match [query] (a part of a section's or a module's name), the ones that start with it first. */
internal fun jumpMatches(targets: List<JumpTarget>, query: String, limit: Int = 8): List<JumpTarget> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return targets.filter { q in it.title.lowercase() || q in it.area.lowercase() }
        .sortedBy { if (it.title.lowercase().startsWith(q)) 0 else 1 }
        .take(limit)
}

/**
 * "Jump to": a field that opens any module or section by name, for when the way is known and the taps are not wanted. With [boxed]
 * false the rows draw without a box of their own, for use inside a sheet. [onDone] runs after a row is chosen.
 */
@Composable
fun JumpSearch(model: AppModel, boxed: Boolean = true, onDone: () -> Unit = {}) {
    val focus = LocalFocusTab.current
    var query by remember { mutableStateOf("") }
    val found = remember(query, model.data.settings.careModule, focus) { jumpMatches(jumpTargets(model, focus), query) }
    PixelField(query, { query = it.take(30) }, Modifier.fillMaxWidth(), singleLine = true, label = "Jump to")
    if (query.isBlank()) return
    if (found.isEmpty()) {
        Text("Nothing by that name", style = MaterialTheme.typography.bodyLarge, color = Pixel.InkSoft)
        return
    }
    val rows = found.mapIndexed { i, t -> MenuEntry(i.toString(), t.icon, t.title, count = t.area) }
    val open: (String) -> Unit = { key -> found[key.toInt()].go(); onDone() }
    if (boxed) MenuList(rows, open) else MenuRows(rows, open)
}
