package app.pokeplan.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import app.pokeplan.plan.Rank
import app.pokeplan.plan.Task
import app.pokeplan.plan.TaskFilter
import app.pokeplan.plan.TaskViews
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.RankBadge
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import kotlinx.datetime.LocalDate
import app.pokeplan.ui.pokemon.label
import app.pokeplan.ui.screens.TabButton
import app.pokeplan.ui.screens.PixelDropdown
import app.pokeplan.ui.screens.label

/** Rows shown at most; a search or a filter narrows the rest down. */
private const val SHOWN = 60

/**
 * The search box with its Filter panel (category, list, difficulty, and with [withStatus] the status and the Done preset), shared by All tasks and
 * Outstanding. Tap a chosen chip again to drop it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskFilterBox(model: AppModel, filter: TaskFilter, onChange: (TaskFilter) -> Unit, withStatus: Boolean) {
    val lists = model.store.activeLists
    var filtering by remember { mutableStateOf(false) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelField(
                    filter.query, { onChange(filter.copy(query = it)) }, Modifier.weight(1f), singleLine = true,
                    placeholder = "Search",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
                // The Done preset (finished and won't-do tasks, latest first), as Things' Logbook; tap again for open tasks.
                if (withStatus) TabButton("Done", filter.status == TaskFilter.Status.FINISHED, { onChange(filter.copy(status = if (filter.status == TaskFilter.Status.FINISHED) TaskFilter.Status.OPEN else TaskFilter.Status.FINISHED)) })
                PixelButton("Filter", { filtering = !filtering }, compact = true)
            }
            if (filtering) Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                if (withStatus) {
                    FieldLabel("Status")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        TaskFilter.Status.entries.forEach { st -> TabButton(st.label, filter.status == st, { onChange(filter.copy(status = st)) }) }
                    }
                }
                FieldLabel("Category")
                CategoryChips(filter.category, allowNone = true) { onChange(filter.copy(category = it)) }
                if (lists.isNotEmpty()) {
                    PixelDropdown(
                        listOf<String?>(null, TaskFilter.INBOX) + lists.map { it.id }, filter.listId,
                        { id -> when (id) { null -> "All"; TaskFilter.INBOX -> "Inbox"; else -> lists.firstOrNull { it.id == id }?.name ?: "" } },
                        { onChange(filter.copy(listId = it)) }, Modifier.fillMaxWidth(), label = "List",
                    )
                }
                FieldLabel("Difficulty")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.CenterVertically) {
                    Rank.entries.forEach { r -> RankBadge(r, selected = filter.rank == r, onClick = { onChange(filter.copy(rank = if (filter.rank == r) null else r)) }) }
                }
                if (filter.isNarrowed) PixelButton("Clear all", { onChange(TaskFilter()) }, color = Pixel.InkSoft, compact = true)
            }
        }
    }
}

/** Every open task across the lists, grouped by list (the Inbox first, then the lists in their saved order), narrowed by the shared filter. */
@Composable
internal fun OutstandingView(model: AppModel, tasks: List<Task>, card: @Composable (Task, LocalDate?) -> Unit, onAdd: () -> Unit) {
    var filter by remember { mutableStateOf(TaskFilter()) }
    TaskFilterBox(model, filter, { filter = it }, withStatus = false)
    val lists = model.store.activeLists
    val groups = TaskViews.outstanding(tasks, lists.map { it.id }, filter)
    if (groups.isEmpty()) EmptyState(
        if (filter.isNarrowed) "Nothing matches" else "Nothing outstanding",
        icon = "journal",
        action = if (filter.isNarrowed) "Clear filters" to { filter = TaskFilter() } else "Add a task" to onAdd,
    ) else groups.forEach { g ->
        TaskGroup(g.listId?.let { id -> lists.firstOrNull { it.id == id }?.name } ?: "Inbox", g.tasks, card = card)
    }
}

/**
 * Every task in one place, for finding one: a search over title and notes, then filters by status, category, list and
 * rank (tap a chosen chip again to drop it). Open tasks are shown by default.
 */
@Composable
internal fun AllTasksView(model: AppModel, tasks: List<Task>, card: @Composable (Task, LocalDate?) -> Unit, onAdd: () -> Unit) {
    var filter by remember { mutableStateOf(TaskFilter()) }
    TaskFilterBox(model, filter, { filter = it }, withStatus = true)
    val found = filter.apply(tasks)
    if (found.size > SHOWN) Text("Showing the first $SHOWN of ${found.size}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    if (found.isEmpty()) EmptyState(
        if (filter.status == TaskFilter.Status.FINISHED && filter.copy(status = TaskFilter.Status.OPEN) == TaskFilter()) "Nothing finished yet" else if (filter.isNarrowed) "Nothing matches" else "No open tasks",
        icon = "scope-lens",
        action = if (filter.isNarrowed) "Clear filters" to { filter = TaskFilter() } else "Add a task" to onAdd,
    ) else TaskGroup(null, found.take(SHOWN), card = card)
}
