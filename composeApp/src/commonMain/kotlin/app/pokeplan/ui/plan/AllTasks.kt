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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskFilterBox(model: AppModel, filter: TaskFilter, onChange: (TaskFilter) -> Unit, withStatus: Boolean) {
    var filtering by remember { mutableStateOf(false) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelField(
                    filter.query, { onChange(filter.copy(query = it)) }, Modifier.weight(1f), singleLine = true,
                    placeholder = "Search",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
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
                val lists = model.store.activeLists
                if (lists.isNotEmpty()) {
                    FieldLabel("List")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        (lists.map { it.id to it.name } + (TaskFilter.INBOX to "No list")).forEach { (id, name) ->
                            TabButton(name, filter.listId == id, { onChange(filter.copy(listId = if (filter.listId == id) null else id)) })
                        }
                    }
                }
                FieldLabel("Difficulty")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.CenterVertically) {
                    Rank.entries.forEach { r -> RankBadge(r, selected = filter.rank == r, onClick = { onChange(filter.copy(rank = if (filter.rank == r) null else r)) }) }
                }
            }
            if (filter.isNarrowed) PixelButton("Clear all", { onChange(TaskFilter()) }, color = Pixel.InkSoft, compact = true)
        }
    }
}
