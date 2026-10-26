package app.pokeplan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import app.pokeplan.plan.TaskState
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.RankBadge
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import kotlinx.datetime.LocalDate

/** Rows shown at most; a search or a filter narrows the rest down. */
private const val SHOWN = 60

/**
 * Every task in one place, for finding one: a search over title and notes, then filters by status, category, list and
 * rank (tap a chosen chip again to drop it). Open tasks are shown by default.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AllTasksView(model: AppModel, tasks: List<Task>, card: @Composable (Task, LocalDate?) -> Unit) {
    var filter by remember { mutableStateOf(TaskFilter()) }
    val lists = model.store.activeLists
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            OutlinedTextField(
                filter.query, { filter = filter.copy(query = it) }, Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = { Text("Search titles and notes", style = MaterialTheme.typography.bodyLarge, color = Pixel.InkSoft) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            FieldLabel("Status")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                listOf("Open" to TaskState.OPEN, "Done" to TaskState.DONE, "Any" to null).forEach { (label, state) ->
                    TabButton(label, filter.state == state, { filter = filter.copy(state = state) })
                }
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
    val found = filter.apply(tasks)
    Text(
        if (found.size > SHOWN) "Showing the first $SHOWN of ${found.size}" else if (found.size == 1) "1 task" else "${found.size} tasks",
        style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
    )
    if (found.isEmpty()) EmptyState(
        if (filter.isNarrowed) "Nothing matches" else "No open tasks",
        if (filter.isNarrowed) "Try fewer filters or other words." else "Add one from Today or the Inbox.",
        icon = "scope-lens",
    ) else TaskGroup(null, found.take(SHOWN), card = card)
}
