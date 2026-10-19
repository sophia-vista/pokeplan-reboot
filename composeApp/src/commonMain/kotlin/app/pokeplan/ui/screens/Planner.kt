package app.pokeplan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import app.pokeplan.app.GameStore
import app.pokeplan.plan.Category
import app.pokeplan.plan.HabitTarget
import app.pokeplan.plan.QuickAdd
import app.pokeplan.plan.Rank
import app.pokeplan.plan.Task
import app.pokeplan.plan.TaskState
import app.pokeplan.plan.TaskViews
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.RankBadge
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.TextBox

private enum class View(val label: String) { TODAY("Today"), INBOX("Inbox"), UPCOMING("Soon"), HABITS("Habits"), DONE("Done") }

@Composable
fun PlannerScreen(model: AppModel) {
    var view by remember { mutableStateOf(View.TODAY) }
    var expanded by remember { mutableStateOf<String?>(null) }
    val store = model.store

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Planner", subtitle = "${store.todayTasks().size} to do today")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                View.entries.forEach { v -> TabButton(v.label, v == view, { view = v }, Modifier.weight(1f)) }
            }
            RewardBox(model)
            UndoRows(model)

            if (view == View.HABITS) HabitsView(model) else {
                QuickAddBox(model, view)
                val live = model.data.tasks.filter { it.state != TaskState.DELETED }
                val tasks = when (view) {
                    View.TODAY -> store.todayTasks()
                    View.INBOX -> TaskViews.inbox(live)
                    View.UPCOMING -> TaskViews.upcoming(live, store.now)
                    View.DONE -> live.filter { it.state == TaskState.DONE }.sortedByDescending { it.completedAtMillis }.take(30)
                    View.HABITS -> emptyList()
                }
                val pendingIds = model.data.pending.map { it.original.id }.toSet()
                val shown = tasks.filter { it.id !in pendingIds }
                if (shown.isEmpty()) {
                    TextBox { Text(emptyLine(view), style = MaterialTheme.typography.bodyLarge) }
                }
                shown.forEach { t -> TaskCard(model, t, expanded == t.id, { expanded = if (expanded == t.id) null else t.id }) }
            }
        }
    }
}

private fun emptyLine(view: View) = when (view) {
    View.TODAY -> "Nothing planned for today. Add something above, or enjoy the calm."
    View.INBOX -> "The inbox is empty."
    View.UPCOMING -> "Nothing coming up in the next week."
    View.DONE -> "Nothing finished yet."
    View.HABITS -> ""
}

/** One line to add a task. "Call mum tomorrow 5pm 30m" is understood; the rank badges set how hard it is. */
@Composable
private fun QuickAddBox(model: AppModel, view: View) {
    var text by remember { mutableStateOf("") }
    var rank by remember { mutableStateOf(Rank.C) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    text, { text = it }, Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("New task", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                )
                PixelButton("Add", {
                    if (text.isNotBlank()) {
                        val parsed = QuickAdd.parse(text, model.store.today)
                        val planned = parsed.planned ?: if (parsed.due == null && view == View.TODAY) model.store.today else null
                        model.store.addTask(
                            title = parsed.title.ifBlank { text.trim() }, rank = parsed.rank ?: rank,
                            category = parsed.category ?: Category.PERSONAL,
                            planned = planned, due = parsed.due, estimateMinutes = parsed.estimateMinutes,
                        )
                        text = ""
                    }
                }, color = Pixel.Blue, enabled = text.isNotBlank())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("How hard?", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, modifier = Modifier.padding(end = 4.dp))
                Rank.entries.forEach { r -> RankBadge(r, selected = r == rank, onClick = { rank = r }) }
            }
        }
    }
}

@Composable
private fun TaskCard(model: AppModel, task: Task, expanded: Boolean, toggle: () -> Unit) {
    val store = model.store
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ListRow(
            lead = { RankBadge(task.rank) },
            onClick = toggle,
            trail = {
                if (task.state == TaskState.OPEN) PixelButton("Done", { store.completeTask(task.id) }, color = Pixel.Green, compact = true)
                else PixelButton("Reopen", { store.reopen(task.id) }, color = Pixel.Blue, compact = true)
            },
        ) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            val meta = taskMeta(task)
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
        if (expanded) {
            TextBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    task.subtasks.forEach { sub ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(sub.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = if (sub.done) Pixel.InkSoft else Pixel.Ink)
                            if (!sub.done && task.state == TaskState.OPEN) PixelButton("Step done", { store.completeSubtask(task.id, sub.id) }, color = Pixel.Green, compact = true)
                        }
                    }
                    if (task.state == TaskState.OPEN) {
                        var step by remember { mutableStateOf("") }
                        if (task.subtasks.size < GameStore.MAX_SUBTASKS) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    step, { step = it }, Modifier.weight(1f), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
                                    placeholder = { Text("Add a small step", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) },
                                )
                                PixelButton("Add", { store.addSubtask(task.id, step); step = "" }, enabled = step.isNotBlank(), compact = true)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton("Won't do", { store.wontDo(task.id) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                            PixelButton("Delete", { store.deleteTask(task.id) }, Modifier.weight(1f), Pixel.Red, compact = true)
                        }
                    }
                }
            }
        }
    }
}

/** "Mon 19 Oct  ·  45 min  ·  1/3 steps" */
fun taskMeta(t: Task): String = buildList {
    t.due?.let { add("due ${it.date.short()} ${it.time.hour}:${it.time.minute.toString().padStart(2, '0')}") }
        ?: t.planned?.let { add(it.short()) }
    t.estimateMinutes?.let { add("$it min") }
    if (t.subtasks.isNotEmpty()) add("${t.subtasks.count { it.done }}/${t.subtasks.size} steps")
}.joinToString("  ·  ")

@Composable
private fun HabitsView(model: AppModel) {
    val store = model.store
    var title by remember { mutableStateOf("") }
    TextBox {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                title, { title = it }, Modifier.weight(1f), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = { Text("New daily habit", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) },
            )
            PixelButton("Add", { store.addHabit(title, HabitTarget.Daily); title = "" }, enabled = title.isNotBlank())
        }
    }
    if (model.data.habits.isEmpty()) {
        TextBox { Text("Habits are small things you want to do often. Add one above.", style = MaterialTheme.typography.bodyLarge) }
    }
    model.data.habits.forEach { h ->
        val doneToday = store.today in h.completions
        ListRow(
            lead = { RankBadge(h.rank) },
            trail = {
                if (doneToday) PixelButton("Undo", { store.undoHabit(h.id) }, color = Pixel.InkSoft, compact = true)
                else PixelButton("Done", { store.completeHabit(h.id) }, color = Pixel.Green, compact = true)
            },
        ) {
            Text(h.title, style = MaterialTheme.typography.bodyLarge)
            Text("Streak ${h.streak(store.today, model.data.restDays)}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
}
