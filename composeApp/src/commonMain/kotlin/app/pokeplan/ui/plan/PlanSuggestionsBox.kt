package app.pokeplan.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.pokeplan.plan.DayTimeline
import app.pokeplan.plan.PlanSuggestions
import app.pokeplan.plan.Suggestion
import app.pokeplan.plan.Task
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.Spacing
import kotlinx.datetime.LocalDate

/**
 * Optional planning suggestions (PLAN 3.4A): a tap on "Suggest times" proposes a free hour for each task in the tray, using only
 * the gaps between what is already on the day. Nothing is scheduled until the player accepts a row (or all of them).
 */
@Composable
internal fun PlanSuggestionsBox(model: AppModel, candidates: List<Task>, plan: DayTimeline.Plan, day: LocalDate, open: Boolean = false) {
    if (candidates.isEmpty()) return
    val store = model.store
    fun suggest() = PlanSuggestions.suggest(
        candidates, plan.blocks.map { it.startMinute until it.endMinute } + plan.events.map { it.startMinute until it.endMinute }, day, store.now,
    )
    var shown by remember(day) { mutableStateOf<List<Suggestion>?>(if (open) suggest() else null) }
    val byId = candidates.associateBy { it.id }
    val rows = shown?.filter { it.taskId in byId }
    if (rows == null) {
        PixelButton("Suggest times", { shown = suggest() }, color = Pixel.InkSoft, compact = true)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        if (rows.isEmpty()) Text("No free time found.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        rows.forEach { s ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${clock(s.start)} · ${byId.getValue(s.taskId).title}", Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text("${s.minutes} min", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                PixelIconButton("check", "Plan ${byId.getValue(s.taskId).title} at ${clock(s.start)}", { store.acceptSuggestions(listOf(s)) }, Pixel.Green)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            if (rows.size > 1) PixelButton("Plan all", { store.acceptSuggestions(rows) }, color = Pixel.Tone, compact = true)
            PixelButton(if (rows.isEmpty()) "Close" else "Dismiss", { shown = null }, color = Pixel.InkSoft, compact = true)
        }
    }
}
