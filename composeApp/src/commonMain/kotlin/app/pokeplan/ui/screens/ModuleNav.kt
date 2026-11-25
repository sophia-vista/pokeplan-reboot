package app.pokeplan.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.PixelKey
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.world.WorldSection

val LocalFocusTab = staticCompositionLocalOf { true }

@Composable
fun SectionsKey(title: String, sections: List<MenuEntry>, onSection: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    PixelKey({ show = true }, key = Pixel.InkSoft, compact = true, horizontal = Spacing.S) { look -> KeyLabel("Sections", look) }
    if (show) NavSheet(title, { show = false }) {
        MenuRows(sections) { key -> show = false; onSection(key) }
    }
}

@Composable
fun NavSheet(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(Modifier.widthIn(max = 640.dp).padding(Spacing.L).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
                Box(Modifier.background(Pixel.Paper, DialogueShape)) {
                    TextBox {
                        Column(Modifier.heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
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

internal class JumpTarget(val title: String, val area: String, val icon: String, val tint: Color, val go: () -> Unit)

/** Every place the Modules search can open, carrying the colour of the module it belongs to. */
internal fun jumpTargets(model: AppModel, focus: Boolean): List<JumpTarget> = buildList {
    add(JumpTarget("Gear", "Tools", "explorer-kit", ModuleAccent.GEAR.accent) { model.gearOpen = true })
    Module.entries.forEach { m ->
        add(JumpTarget(m.label, "Module", m.icon, m.look.accent) { model.open(Place.valueOf(m.name), focus) })
    }
    PlanSection.entries.filter { it != PlanSection.FOCUS || focus }.forEach { s ->
        add(JumpTarget(s.label, "Plan", s.icon, ModuleAccent.PLAN.accent) { model.open(Place.PLAN, focus); model.planSection = s })
    }
    CareSection.entries.forEach { s -> add(JumpTarget(s.label, "Care", s.icon, ModuleAccent.CARE.accent) { model.open(Place.CARE, focus); model.careSection = s }) }
    HealSection.entries.forEach { s -> add(JumpTarget(s.label, "Heal", s.icon, ModuleAccent.HEAL.accent) { model.open(Place.HEAL, focus); model.healSection = s }) }
    MintSection.entries.filter { it != MintSection.IMPORT || model.textFiles != null }.forEach { s ->
        add(JumpTarget(s.label, "Mint", s.icon, ModuleAccent.MINT.accent) { model.open(Place.MINT, focus); model.mintSection = s })
    }
    BaseSection.entries.forEach { s -> add(JumpTarget(s.label, "Base", s.icon, ModuleAccent.BASE.accent) { model.open(Place.BASE, focus); model.baseSection = s }) }
    LinkSection.entries.filter { it != LinkSection.ONEDRIVE || model.oneDrive != null }.forEach { s ->
        add(JumpTarget(s.label, "Link", s.icon, ModuleAccent.LINK.accent) { model.open(Place.LINK, focus); model.linkPersonId = null; model.linkSection = s })
    }
    WorldSection.entries.forEach { s -> add(JumpTarget(s.label, "World", s.icon, ModuleAccent.WORLD.accent) { model.open(Place.WORLD, focus); model.worldSection = s }) }
}

internal fun jumpMatches(targets: List<JumpTarget>, query: String, limit: Int = 8): List<JumpTarget> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return targets.filter { q in it.title.lowercase() || q in it.area.lowercase() }
        .sortedBy { if (it.title.lowercase().startsWith(q)) 0 else 1 }
        .take(limit)
}

/**
 * The "Jump to" field and, once something is typed, the places it matches. The caller owns [query]
 * so it can clear it when the search closes, and the field takes focus as soon as it appears, so
 * typing starts straight away.
 */
@Composable
fun JumpSearch(model: AppModel, query: String, onQuery: (String) -> Unit, onDone: () -> Unit = {}) {
    val focus = LocalFocusTab.current
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { field.requestFocus() }
    val found = remember(query, focus, Pixel.dark) { jumpMatches(jumpTargets(model, focus), query) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        PixelField(query, { onQuery(it.take(30)) }, Modifier.weight(1f).focusRequester(field), singleLine = true, placeholder = "Jump to")
        PixelIconButton("close", "Close search", onDone)
    }
    if (query.isBlank()) return
    if (found.isEmpty()) {
        Text("Nothing by that name", style = MaterialTheme.typography.bodyLarge, color = Pixel.InkSoft)
        return
    }
    val rows = found.mapIndexed { i, t -> MenuEntry(i.toString(), t.icon, t.title, count = t.area, tint = t.tint) }
    val open: (String) -> Unit = { key -> found[key.toInt()].go(); onDone() }
    MenuList(rows, rowTint = true, onOpen = open)
}
