package app.pokeplan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
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
import app.pokeplan.ui.theme.DialogueShape
import app.pokeplan.ui.theme.KeyLabel
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
 *  - a module's page is its daily view, right under a one-row header (Back, the title, a small "Sections" key);
 *  - the "Sections" key opens a sheet of compact rows, one per section;
 *  - the bottom Modules tab switches module (tapping it again returns to the list), so the page carries no module switcher;
 *  - the Modules tab carries a "Jump to" field that opens any section by its fixed name.
 * Nothing here reads a figure, a name or a private word: the search only knows the fixed names of the sections.
 */

/** Whether Focus exists on this platform (false on desktop and the web), for screens that are not handed it. `ModulesScreen` provides it. */
val LocalFocusTab = staticCompositionLocalOf { true }

/**
 * The small key at the end of a module header's one row: **Sections** opens [sections] as rows in a sheet titled [title]. [onSection]
 * is given the key of the tapped row. Quiet on purpose (the page under the header is the point); a screen reader hears "Sections".
 */
@Composable
fun SectionsKey(title: String, sections: List<MenuEntry>, onSection: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    PixelKey({ show = true }, key = Pixel.InkSoft, compact = true, horizontal = Spacing.S) { look -> KeyLabel("Sections", look) }
    if (show) NavSheet(title, { show = false }) {
        MenuRows(sections) { key -> show = false; onSection(key) }
    }
}

/**
 * A sheet that rises from the bottom over the page: one dialogue box with a title, [content] and a Close key. A tap outside it, or
 * the system back gesture, closes it.
 */
@Composable
fun NavSheet(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // An opaque page colour under the box: the dialogue box alone lets the page behind show through the rows.
            Box(Modifier.widthIn(max = 640.dp).padding(Spacing.L).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
                Box(Modifier.background(Pixel.Paper, DialogueShape)) {
                    TextBox {
                        Column(Modifier.heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            // Title and rows scroll; the Close key stays in view under them.
                            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                                PixelTitle(title)
                                content()
                            }
                            PixelButton("Close", onClose, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                        }
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
    PlanSection.entries.filter { it != PlanSection.FOCUS || focus }.forEach { s ->
        add(JumpTarget(s.label, "Plan", s.icon) { model.open(Place.PLAN, focus); model.planSection = s })
    }
    if (careOn) CareSection.entries.forEach { s -> add(JumpTarget(s.label, "Care", s.icon) { model.open(Place.CARE, focus); model.careSection = s }) }
    HealSection.entries.forEach { s -> add(JumpTarget(s.label, "Heal", s.icon) { model.open(Place.HEAL, focus); model.healSection = s }) }
    MintSection.entries.filter { it != MintSection.IMPORT || model.textFiles != null }.forEach { s ->
        add(JumpTarget(s.label, "Mint", s.icon) { model.open(Place.MINT, focus); model.mintSection = s })
    }
    BaseSection.entries.forEach { s -> add(JumpTarget(s.label, "Base", s.icon) { model.open(Place.BASE, focus); model.baseSection = s }) }
    LinkSection.entries.forEach { s -> add(JumpTarget(s.label, "Link", s.icon) { model.open(Place.LINK, focus); model.linkPersonId = null; model.linkSection = s }) }
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
 * "Jump to": a field that opens any module or section by name, for when the way is known and the taps are not wanted. [onDone] runs
 * after a row is chosen.
 */
@Composable
fun JumpSearch(model: AppModel, onDone: () -> Unit = {}) {
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
    MenuList(rows, open)
}
