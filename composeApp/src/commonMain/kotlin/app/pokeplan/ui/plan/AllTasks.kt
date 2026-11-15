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
import app.pokeplan.ui.screens.label

/** Rows shown at most; a search or a filter narrows the rest down. */
private const val SHOWN = 60

/**
 * Every task in one place, for finding one: a search over title and notes, then filters by status, category, list and
 * rank (tap a chosen chip again to drop it). Open tasks are shown by default.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AllTasksView(model: AppModel, tasks: List<Task>, card: @Composable (Task, LocalDate?) -> Unit, onAdd: () -> Unit) {
    var filter by remember { mutableStateOf(TaskFilter()) }
    val lists = model.store.activeLists
    var filtering by remember { mutableStateOf(false) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelField(
                    filter.query, { filter = filter.copy(query = it) }, Modifier.weight(1f), singleLine = true,
                    placeholder = "Search",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
                // The Done preset (finished and won't-do tasks, latest first), as Things' Logbook; tap again for open tasks.
                TabButton("Done", filter.status == TaskFilter.Status.FINISHED, { filter = filter.copy(status = if (filter.status == TaskFilter.Status.FINISHED) TaskFilter.Status.OPEN else TaskFilter.Status.FINISHED) })
                PixelButton("Filter", { filtering = !filtering }, compact = true)
            }
            if (filtering) Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            FieldLabel("Status")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                TaskFilter.Status.entries.forEach { st -> TabButton(st.label, filter.status == st, { filter = filter.copy(status = st) }) }
            }
            FieldLabel("Category")
            CategoryChips(filter.category, allowNone = true) { filter = filter.copy(category = it) }
            if (lists.isNotEmpty()) {
                FieldLabel("List")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    val choices = listOf("Inbox" to TaskFilter.INBOX) + lists.map { it.name to it.id }
                    choices.forEach { (name, id) -> TabButton(name, filter.listId == id, { filter = filter.copy(listId = if (filter.listId == id) null else id) }) }
                }
            }
            FieldLabel("Difficulty")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.CenterVertically) {
                Rank.entries.forEach { r -> RankBadge(r, selected = filter.rank == r, onClick = { filter = filter.copy(rank = if (filter.rank == r) null else r) }) }
            }
            if (filter.isNarrowed) PixelButton("Clear all", { filter = TaskFilter() }, color = Pixel.InkSoft, compact = true)
            }
        }
    }
    val found = filter.apply(tasks)
    if (found.size > SHOWN) Text("Showing the first $SHOWN of ${found.size}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    if (found.isEmpty()) EmptyState(
        if (filter.status == TaskFilter.Status.FINISHED && filter.copy(status = TaskFilter.Status.OPEN) == TaskFilter()) "Nothing finished yet" else if (filter.isNarrowed) "Nothing matches" else "No open tasks",
        icon = "scope-lens",
        action = if (filter.isNarrowed) "Clear filters" to { filter = TaskFilter() } else "Add a task" to onAdd,
    ) else TaskGroup(null, found.take(SHOWN), card = card)
}
