package app.pokeplan.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PmdText
import app.pokeplan.ui.theme.Spacing

/**
 * The launcher's widgets, listed by where they sit (the top of the home page, its bottom, the Today page): each with Up,
 * Down and Remove, and Add to put one more in the area. A folder widget is a labelled folder with its apps; "New folder"
 * makes the folder and its widget together, and the folder's apps are chosen from the long-press sheet in All apps.
 */
@Composable
internal fun WidgetsPage(prefs: LauncherPrefs, onPrefs: (LauncherPrefs) -> Unit, host: LauncherHost, pixel: Int, onClose: () -> Unit) {
    val density = LocalDensity.current.density
    val pad = pmdRowPad(pixel, density)
    var adding by remember { mutableStateOf<WidgetArea?>(null) }
    var name by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Pixel.Night).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = Spacing.Page, end = Spacing.Page, top = Spacing.XL, bottom = Spacing.XXL), verticalArrangement = Arrangement.spacedBy(Spacing.XL)) {
            PmdBox(pixel, Modifier.fillMaxWidth()) {
                Column {
                    PmdMenuLine("Back", pixel, onClick = onClose)
                    PmdRule(pixel)
                    PmdMenuLine("Widgets", pixel, dim = true) {}
                }
            }
            if (prefs.gridMode) GridSections(prefs, onPrefs, host, pixel)
            else WidgetArea.entries.forEach { area ->
                val slots = prefs.widgetsIn(area)
                PmdBox(pixel, Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        PmdText(area.label, pixel, Modifier.padding(horizontal = pad, vertical = Spacing.M), alpha = 0.75f)
                        PmdRule(pixel)
                        if (slots.isEmpty()) PmdText("No widgets here", pixel, Modifier.padding(horizontal = pad, vertical = Spacing.M), alpha = 0.7f)
                        slots.forEachIndexed { i, slot ->
                            Row(Modifier.fillMaxWidth().height(Spacing.Target), verticalAlignment = Alignment.CenterVertically) {
                                PmdText(if (slot.kind == WidgetKind.ANDROID) slot.arg.toIntOrNull()?.let { host.widgetLabel(it) } ?: slot.title else slot.title, pixel, Modifier.weight(1f).padding(horizontal = pad))
                                Action("Up", enabled = i > 0, pixel) { onPrefs(prefs.moveWidget(slot, -1)) }
                                Action("Down", enabled = i < slots.lastIndex, pixel) { onPrefs(prefs.moveWidget(slot, 1)) }
                                Action("Remove", enabled = true, pixel) { if (slot.kind == WidgetKind.ANDROID) slot.arg.toIntOrNull()?.let { host.releaseWidget(it) }; onPrefs(prefs.removeWidget(slot)) }
                            }
                        }
                        PmdRule(pixel)
                        PmdMenuLine(if (adding == area) "Close" else "Add a widget", pixel, center = true) { adding = if (adding == area) null else area }
                        if (adding == area) {
                            val placed = prefs.widgets.map { it.kind to it.arg }.toSet()
                            WidgetKind.entries.filter { it != WidgetKind.FOLDER && it != WidgetKind.ANDROID && (it to "") !in placed }.forEach { kind ->
                                PmdMenuLine(kind.label, pixel) { onPrefs(prefs.addWidget(WidgetSlot(area, kind))); adding = null }
                            }
                            PmdMenuLine("Android widget from another app", pixel) { adding = null; host.pickWidget { id -> onPrefs(prefs.addWidget(WidgetSlot(area, WidgetKind.ANDROID, id.toString()), host.startingCells(id))) } }
                            prefs.folders.filter { (WidgetKind.FOLDER to it.name) !in placed }.forEach { f ->
                                PmdMenuLine("Folder: ${f.name}", pixel) { onPrefs(prefs.addWidget(WidgetSlot(area, WidgetKind.FOLDER, f.name))); adding = null }
                            }
                            Column(Modifier.padding(top = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                                PmdField(name, { name = it.take(Folder.MAX_NAME) }, "New folder name")
                                PmdMenuLine("Make the folder widget", pixel, dim = name.isBlank()) {
                                    val made = prefs.addFolder(name)
                                    onPrefs(made.addWidget(WidgetSlot(area, WidgetKind.FOLDER, name.trim().take(Folder.MAX_NAME))))
                                    name = ""; adding = null
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Action(label: String, enabled: Boolean, pixel: Int, onClick: () -> Unit) {
    Box(
        Modifier.height(Spacing.Target).let { if (enabled) it.clickable(role = Role.Button, onClickLabel = label, onClick = onClick) else it }.padding(horizontal = Spacing.S),
        contentAlignment = Alignment.Center,
    ) { PmdText(label, pixel, alpha = if (enabled) 0.9f else 0.3f) }
}

/** The grid's widgets listed by page: each with Remove (moving and resizing are done on the page itself), and Add a widget to put one more on that page. */
@Composable
private fun GridSections(prefs: LauncherPrefs, onPrefs: (LauncherPrefs) -> Unit, host: LauncherHost, pixel: Int) {
    val density = LocalDensity.current.density
    val pad = pmdRowPad(pixel, density)
    var adding by remember { mutableStateOf<WidgetPage?>(null) }
    var name by remember { mutableStateOf("") }
    prefs.pageList.forEach { page ->
        val here = WidgetGrid.onPage(prefs.gridWidgets, page).sortedWith(compareBy({ it.row }, { it.col }))
        PmdBox(pixel, Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                PmdText(page.label, pixel, Modifier.padding(horizontal = pad, vertical = Spacing.M), alpha = 0.75f)
                PmdRule(pixel)
                if (here.isEmpty()) PmdText("No widgets here", pixel, Modifier.padding(horizontal = pad, vertical = Spacing.M), alpha = 0.7f)
                here.forEach { w ->
                    Row(Modifier.fillMaxWidth().height(Spacing.Target), verticalAlignment = Alignment.CenterVertically) {
                        val title = if (w.kind == WidgetKind.ANDROID) w.arg.toIntOrNull()?.let { host.widgetLabel(it) } ?: w.kind.label else if (w.kind == WidgetKind.FOLDER) w.arg else w.kind.label
                        PmdText(title, pixel, Modifier.weight(1f).padding(horizontal = pad))
                        Action("Remove", enabled = true, pixel) { if (w.kind == WidgetKind.ANDROID) w.arg.toIntOrNull()?.let { host.releaseWidget(it) }; onPrefs(prefs.removeGridWidget(w)) }
                    }
                }
                PmdRule(pixel)
                PmdMenuLine(if (adding == page) "Close" else "Add a widget", pixel, center = true) { adding = if (adding == page) null else page }
                if (adding == page) {
                    val placed = prefs.gridWidgets.map { it.kind to it.arg }.toSet()
                    WidgetKind.entries.filter { it != WidgetKind.FOLDER && it != WidgetKind.ANDROID && (it to "") !in placed }.forEach { kind ->
                        PmdMenuLine(kind.label, pixel) { onPrefs(prefs.addGridWidget(page, kind)); adding = null }
                    }
                    PmdMenuLine("Android widget from another app", pixel) { adding = null; host.pickWidget { id -> onPrefs(prefs.addGridWidget(page, WidgetKind.ANDROID, id.toString(), host.startingCells(id))) } }
                    prefs.folders.filter { (WidgetKind.FOLDER to it.name) !in placed }.forEach { f ->
                        PmdMenuLine("Folder: ${f.name}", pixel) { onPrefs(prefs.addGridWidget(page, WidgetKind.FOLDER, f.name)); adding = null }
                    }
                    Column(Modifier.padding(top = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        PmdField(name, { name = it.take(Folder.MAX_NAME) }, "New folder name")
                        PmdMenuLine("Make the folder widget", pixel, dim = name.isBlank()) {
                            val made = prefs.addFolder(name)
                            onPrefs(made.addGridWidget(page, WidgetKind.FOLDER, name.trim().take(Folder.MAX_NAME)))
                            name = ""; adding = null
                        }
                    }
                }
            }
        }
    }
}
