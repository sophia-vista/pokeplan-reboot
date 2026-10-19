package app.pokeplan.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.pokeplan.plan.HabitTarget
import app.pokeplan.plan.QuickAdd
import app.pokeplan.plan.Rank
import app.pokeplan.plan.Task
import app.pokeplan.plan.TaskState
import app.pokeplan.plan.TaskViews
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.TextBox

private enum class View(val label: String) { TODAY("Today"), INBOX("Inbox"), UPCOMING("Soon"), HABITS("Habits"), DONE("Done") }

@Composable
fun PlannerScreen(model: AppModel) {
    var view by remember { mutableStateOf(View.TODAY) }
    var expanded by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            View.entries.forEach { v ->
                TabButton(v.label, v == view, { view = v }, Modifier.weight(1f))
            }
        }
        RewardBox(model)
        UndoRows(model)

        if (view == View.HABITS) HabitsView(model) else {
            QuickAddBox(model, view)
            val store = model.store
            val live = model.data.tasks.filter { it.state != TaskState.DELETED }
            val tasks = when (view) {
                View.TODAY -> store.todayTasks()
                View.INBOX -> TaskViews.inbox(live)
                View.UPCOMING -> TaskViews.upcoming(live, store.now)
                View.DONE -> live.filter { it.state == TaskState.DONE }.sortedByDescending { it.completedAtMillis }.take(30)
                else -> emptyList()
            }
            val pendingIds = model.data.pending.map { it.original.id }.toSet()
            val shown = tasks.filter { it.id !in pendingIds }
            if (shown.isEmpty()) {
                TextBox(Modifier.fillMaxWidth()) { Text(emptyLine(view), style = MaterialTheme.typography.bodyLarge) }
            }
            shown.forEach { t -> TaskCard(model, t, expanded == t.id, { expanded = if (expanded == t.id) null else t.id }) }
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

/** One line to add a task. "Call mum tomorrow 5pm 30m" is understood; the rank buttons set how hard it is. */
@Composable
private fun QuickAddBox(model: AppModel, view: View) {
    var text by remember { mutableStateOf("") }
    var rank by remember { mutableStateOf(Rank.C) }
    TextBox(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                text, { text = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("New task") },
            )
            Text("Try: read 30m tomorrow", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Rank", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, modifier = Modifier.padding(end = 4.dp))
                Rank.entries.forEach { r ->
                    TabButton(r.letter.toString(), r == rank, { rank = r }, Modifier.weight(1f))
                }
            }
            PixelButton("Add", {
                if (text.isNotBlank()) {
                    val parsed = QuickAdd.parse(text, model.store.today)
                    val planned = parsed.planned ?: if (parsed.due == null && view == View.TODAY) model.store.today else null
                    model.store.addTask(
                        title = parsed.title.ifBlank { text.trim() }, rank = parsed.rank ?: rank,
                        category = parsed.category ?: app.pokeplan.plan.Category.PERSONAL,
                        planned = planned, due = parsed.due, estimateMinutes = parsed.estimateMinutes,
                    )
                    text = ""
                }
            }, Modifier.fillMaxWidth(), Pixel.Blue, enabled = text.isNotBlank())
        }
    }
}

@Composable
private fun TaskCard(model: AppModel, task: Task, expanded: Boolean, toggle: () -> Unit) {
    val store = model.store
    TextBox(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable(onClick = toggle)) {
                    Text(task.title, style = MaterialTheme.typography.bodyLarge)
                    Text(meta(task), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                }
                if (task.state == TaskState.OPEN) PixelButton("Done", { store.completeTask(task.id) }, color = Pixel.Green)
                else PixelButton("Reopen", { store.reopen(task.id) }, color = Pixel.Blue)
            }
            if (expanded) {
                task.subtasks.forEach { sub ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(sub.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = if (sub.done) Pixel.InkSoft else Color.Unspecified)
                        if (!sub.done && task.state == TaskState.OPEN) PixelButton("Step done", { store.completeSubtask(task.id, sub.id) }, color = Pixel.Green)
                    }
                }
                if (task.state == TaskState.OPEN) {
                    var step by remember { mutableStateOf("") }
                    if (task.subtasks.size < app.pokeplan.app.GameStore.MAX_SUBTASKS) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(step, { step = it }, Modifier.weight(1f), singleLine = true, label = { Text("Add a small step") })
                            PixelButton("Add", { store.addSubtask(task.id, step); step = "" }, enabled = step.isNotBlank())
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelButton("Won't do", { store.wontDo(task.id) }, Modifier.weight(1f), Pixel.InkSoft)
                        PixelButton("Delete", { store.deleteTask(task.id) }, Modifier.weight(1f), Pixel.Red)
                    }
                }
            }
        }
    }
}

private fun meta(t: Task): String = buildList {
    add("Rank ${t.rank.letter}")
    t.due?.let { add("due ${it.date.short()} ${it.time.hour}:${it.time.minute.toString().padStart(2, '0')}") }
        ?: t.planned?.let { add(it.short()) }
    t.estimateMinutes?.let { add("$it min") }
    if (t.subtasks.isNotEmpty()) add("${t.subtasks.count { it.done }}/${t.subtasks.size} steps")
}.joinToString("  ·  ")

@Composable
private fun HabitsView(model: AppModel) {
    val store = model.store
    var title by remember { mutableStateOf("") }
    TextBox(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("New daily habit") })
            PixelButton("Add habit", { store.addHabit(title, HabitTarget.Daily); title = "" }, Modifier.fillMaxWidth(), Pixel.Blue, enabled = title.isNotBlank())
        }
    }
    if (model.data.habits.isEmpty()) {
        TextBox(Modifier.fillMaxWidth()) { Text("Habits are small things you want to do often. Add one above.", style = MaterialTheme.typography.bodyLarge) }
    }
    model.data.habits.filter { !it.archived() }.forEach { h ->
        val doneToday = store.today in h.completions
        TextBox(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(h.title, style = MaterialTheme.typography.bodyLarge)
                    Text("Streak ${h.streak(store.today, model.data.restDays)}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                }
                if (doneToday) PixelButton("Undo", { store.undoHabit(h.id) }, color = Pixel.InkSoft)
                else PixelButton("Done", { store.completeHabit(h.id) }, color = Pixel.Green)
            }
        }
    }
}

private fun app.pokeplan.plan.Habit.archived(): Boolean = false
