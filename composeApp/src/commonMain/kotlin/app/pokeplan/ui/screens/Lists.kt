package app.pokeplan.ui.screens

import app.pokeplan.ui.theme.Sizes
import app.pokeplan.ui.theme.DialogueShape

import app.pokeplan.ui.theme.PixelShape
import app.pokeplan.ui.theme.ModuleAccent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pokeplan.plan.Task
import app.pokeplan.plan.TaskList
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.theme.FieldLabel
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import kotlinx.datetime.LocalDate
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow

/** The colour names, for screen readers, in the order of [TaskList.COLORS]. */
private val listColorNames = listOf("Red", "Blue", "Green", "Amber", "Teal", "Purple", "Pink", "Slate")

/** The menu's Lists: one row per list with its icon and count, "New list", and the archived ones folded away. */
@Composable
internal fun ListsSection(model: AppModel, tasks: List<Task>, open: (String) -> Unit) {
    val store = model.store
    var creating by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Lists", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall)
            if (!creating) PixelButton("New list", { creating = true }, color = Pixel.InkSoft, compact = true)
        }
        if (creating) ListEditor(model, null) { made -> creating = false; made?.let(open) }
        val lists = store.activeLists
        ListRows(lists, tasks, open)
        val archived = model.data.lists.filter { it.archived }
        if (archived.isNotEmpty()) {
            PixelButton(if (showArchived) "Hide archived" else "Archived (${archived.size})", { showArchived = !showArchived }, color = Pixel.InkSoft, compact = true)
            if (showArchived) ListRows(archived, tasks, open)
        }
    }
}

@Composable
private fun ListRows(lists: List<TaskList>, tasks: List<Task>, open: (String) -> Unit) {
    if (lists.isEmpty()) return
    val shape = PixelShape
    Column(Modifier.fillMaxWidth().background(Pixel.Panel, shape).border(Sizes.Hairline, Pixel.Border, shape).clip(shape)) {
        lists.forEachIndexed { i, l ->
            if (i > 0) HorizontalDivider(Modifier.padding(start = Spacing.M), color = Pixel.Border)
            val count = tasks.count { it.isOpen && it.listId == l.id }
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Open ${l.name}") { open(l.id) }
                    .defaultMinSize(minHeight = 56.dp).padding(horizontal = Spacing.M, vertical = Spacing.S),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M),
            ) {
                Box(Modifier.size(Sizes.IconL).background(Color(l.color).copy(alpha = 0.16f), app.pokeplan.ui.theme.PixelDisc), contentAlignment = Alignment.Center) {
                    ItemIcon(l.icon, size = Sizes.Icon, description = null)
                }
                Text(l.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (count > 0) Text("$count", style = MaterialTheme.typography.bodyLarge, color = Pixel.InkSoft)
            }
        }
    }
}

/** One list: add straight into it, its tasks grouped by when they are, and Edit for its name, colour, icon and archive. */
@Composable
internal fun ListScreen(model: AppModel, list: TaskList, tasks: List<Task>, card: @Composable (Task, LocalDate?) -> Unit, back: () -> Unit) {
    var editing by remember(list.id) { mutableStateOf(false) }
    SectionScaffold(list.name, if (list.archived) "Archived list" else null, onBack = back, icon = list.icon, color = ModuleAccent.PLAN.header) {
        if (editing) ListEditor(model, list) { kept -> editing = false; if (kept == null) back() }
        else PixelButton("Edit list", { editing = true }, color = Pixel.InkSoft, compact = true)
        if (!list.archived) QuickAddBox(model, AddTo.INBOX, listId = list.id)
        WhenGroups(model, tasks, card)
        if (tasks.isEmpty()) EmptyState("Nothing in ${list.name}", icon = list.icon, action = "Add a task" to { model.addTaskRequested = true })
    }
}

/**
 * Makes a list ([list] null) or edits one: its name, one of eight colours and a real item icon; an existing list can also
 * be archived (hidden from the menu, tasks kept) or deleted (its tasks go back to the Inbox). [done] gets the list's id,
 * or null when it was deleted or nothing was made.
 */
@Composable
private fun ListEditor(model: AppModel, list: TaskList?, done: (String?) -> Unit) {
    val store = model.store
    var name by remember { mutableStateOf(list?.name ?: "") }
    var color by remember { mutableStateOf(list?.color ?: TaskList.COLORS.first()) }
    var icon by remember { mutableStateOf(list?.icon ?: TaskList.ICONS.first()) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelField(
                name, { name = it.take(TaskList.MAX_NAME) }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = "List name",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            )
            FieldLabel("Colour")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                TaskList.COLORS.forEachIndexed { i, c ->
                    val on = c == color
                    Box(
                        Modifier.size(Spacing.Target).clip(DialogueShape)
                            .selectable(on, role = Role.RadioButton) { color = c }
                            .semantics { contentDescription = listColorNames[i] },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(Sizes.Icon).background(Color(c), DialogueShape).border(if (on) 3.dp else 0.dp, if (on) Pixel.Ink else Color.Transparent, DialogueShape))
                    }
                }
            }
            FieldLabel("Icon")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                TaskList.ICONS.forEach { id ->
                    val on = id == icon
                    Box(
                        Modifier.size(Spacing.Target).clip(PixelShape)
                            .background(if (on) Color(color).copy(alpha = 0.2f) else Color.Transparent)
                            .border(if (on) 2.dp else 0.dp, if (on) Color(color) else Color.Transparent, PixelShape)
                            .selectable(on, role = Role.RadioButton) { icon = id }
                            .semantics { contentDescription = id.replace('-', ' ') },
                        contentAlignment = Alignment.Center,
                    ) { ItemIcon(id, size = 36.dp, description = null) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                PixelButton(if (list == null) "Make list" else "Save", {
                    if (list == null) done(store.addList(name, color, icon)?.id)
                    else { store.updateList(list.copy(name = name, color = color, icon = icon)); done(list.id) }
                }, Modifier.weight(1f), enabled = name.isNotBlank(), compact = true)
                PixelIconButton("close", "Cancel", { done(list?.id) }, Pixel.InkSoft)
            }
            if (list != null) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                PixelButton(if (list.archived) "Unarchive" else "Archive", { store.updateList(list.copy(archived = !list.archived)); done(list.id) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                PixelIconButton("trash", "Delete", { val ids = model.data.tasks.filter { it.listId == list.id }.map { it.id }.toSet(); store.deleteList(list.id); done(null); model.offerUndo("Deleted: ${list.name}") { store.restoreList(list, ids) } }, Pixel.InkSoft)
            }
        }
    }
}
