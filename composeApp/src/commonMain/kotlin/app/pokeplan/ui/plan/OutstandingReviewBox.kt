package app.pokeplan.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import app.pokeplan.plan.OutstandingReview
import app.pokeplan.plan.OutstandingReview.Choice
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox

/**
 * The light outstanding-task review (PLAN 3.5C): what was left behind, one task at a time and one tap each. Nothing is
 * scolded or paid; every answer can be undone, and *Skip* leaves the task where it is.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OutstandingReviewBox(model: AppModel, close: () -> Unit) {
    val store = model.store
    var skipped by remember { mutableStateOf(setOf<String>()) }
    val left = OutstandingReview.leftovers(planTasks(model), store.now)
    val current = left.firstOrNull { it.id !in skipped }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            if (current == null) {
                PixelTitle(if (left.isEmpty()) "All caught up" else "Nothing else to review")
                PixelButton("Done", close, Modifier.fillMaxWidth(), Pixel.Tone, compact = true)
                return@Column
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Column(Modifier.weight(1f)) { PixelTitle("From before") }
                Text("${left.count { it.id !in skipped }} left", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Text(current.title, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val meta = taskMeta(current)
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Choice.entries.forEach { choice ->
                    PixelButton(choice.label, {
                        val before = current
                        store.reviewLeftover(before.id, choice)
                        model.offerUndo("${choice.label}: ${before.title}") {
                            if (choice == Choice.WONT_DO) store.reopen(before.id) else store.updateTask(before)
                        }
                    }, color = if (choice == Choice.WONT_DO) Pixel.InkSoft else Pixel.Tone, compact = true)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelButton("Skip", { skipped = skipped + current.id }, color = Pixel.InkSoft, compact = true)
                PixelButton("Close", close, color = Pixel.InkSoft, compact = true)
            }
        }
    }
}
