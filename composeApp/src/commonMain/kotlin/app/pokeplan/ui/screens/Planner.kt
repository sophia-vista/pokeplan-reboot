package app.pokeplan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
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
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.theme.title
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

private enum class View(val label: String, val icon: String) {
    TODAY("Today", "oaks-parcel"), INBOX("Inbox", "seal-bag"), UPCOMING("Soon", "timer-ball"), HABITS("Habits", "heart-scale"), DONE("Done", "premier-ball"),
}

@Composable
fun PlannerScreen(model: AppModel, startView: Int = -1) {
    var view by remember { mutableStateOf(View.entries.getOrNull(startView)) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var deleted by remember { mutableStateOf<Task?>(null) }
    val store = model.store
    val live = model.data.tasks.filter { it.state != TaskState.DELETED }
    val pendingIds = model.data.pending.map { it.original.id }.toSet()

    fun tasksFor(v: View): List<Task> = when (v) {
        View.TODAY -> store.todayTasks()
        View.INBOX -> TaskViews.inbox(live)
        View.UPCOMING -> TaskViews.upcoming(live, store.now)
        View.DONE -> live.filter { it.state == TaskState.DONE }.sortedByDescending { it.completedAtMillis }.take(30)
        View.HABITS -> emptyList()
    }.filter { it.id !in pendingIds }

    /** Shown after a delete: the task can be brought back. */
    @Composable
    fun DeletedNotice() {
        deleted?.let { t ->
            TextBox(accent = Pixel.Warning) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Deleted \"${t.title}\"", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                    PixelButton("Undo", { store.restoreTask(t.id); deleted = null }, color = Pixel.InkSoft, compact = true)
                }
            }
        }
    }

    val current = view
    if (current == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Planner", subtitle = "${tasksFor(View.TODAY).size} to do today", icon = "journal")
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.L),
                verticalArrangement = Arrangement.spacedBy(Spacing.L),
            ) {
                RewardBox(model)
                UndoRows(model)
                DeletedNotice()
                QuickAddBox(model, View.INBOX)
                MenuGrid(
                    View.entries.map { v ->
                        MenuEntry(
                            v.name, v.icon, v.label,
                            when (v) {
                                View.HABITS -> "${model.data.habits.size} habits"
                                View.DONE -> "finished tasks"
                                else -> "${tasksFor(v).size} tasks"
                            },
                            tint = when (v) {
                                View.TODAY -> Pixel.Red; View.INBOX -> Pixel.Blue; View.UPCOMING -> Pixel.Warning
                                View.HABITS -> Pixel.Green; View.DONE -> androidx.compose.ui.graphics.Color(0xFF8B5CF6)
                            },
                        )
                    },
                ) { key -> view = View.valueOf(key) }
            }
        }
        return
    }

    SectionScaffold(current.label, null, onBack = { view = null }, icon = current.icon) {
        RewardBox(model)
        UndoRows(model)
        DeletedNotice()
        if (current == View.HABITS) HabitsView(model) else {
            if (current == View.TODAY || current == View.INBOX) QuickAddBox(model, current)
            val shown = tasksFor(current)
            if (shown.isEmpty()) EmptyState(emptyTitle(current), emptyLine(current))
            shown.forEach { t ->
                TaskCard(model, t, expanded == t.id, { expanded = if (expanded == t.id) null else t.id }, onDeleted = { deleted = t; expanded = null })
            }
        }
    }
}

/** What an empty list says, and what to do about it. */
@Composable
fun EmptyState(title: String, line: String) {
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(line, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun emptyTitle(view: View) = when (view) {
    View.TODAY -> "Nothing planned for today"
    View.INBOX -> "The inbox is empty"
    View.UPCOMING -> "Nothing coming up"
    View.DONE -> "Nothing finished yet"
    View.HABITS -> "No habits yet"
}

private fun emptyLine(view: View) = when (view) {
    View.TODAY -> "Add something above, or enjoy the calm."
    View.INBOX -> "Quick thoughts without a date land here."
    View.UPCOMING -> "Tasks planned for the next 7 days show up here."
    View.DONE -> "Finished tasks are kept here."
    View.HABITS -> "Habits are small things you want to do often."
}

/** What the difficulty ranks mean. Harder tasks earn more; it is how hard it feels, not how urgent it is. */
@Composable
fun DifficultyPicker(selected: Rank, onSelect: (Rank) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text("How hard does it feel?", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            Rank.entries.forEach { r -> RankBadge(r, selected = r == selected, onClick = { onSelect(r) }) }
        }
        Text("${selected.letter}: ${selected.title}. Harder tasks earn more.", style = MaterialTheme.typography.bodyMedium)
    }
}

/** Add a task with just a title; "More options" adds difficulty and a day. "Call mum tomorrow 5pm 30m" is understood too. */
@Composable
private fun QuickAddBox(model: AppModel, view: View) {
    var text by remember { mutableStateOf("") }
    var rank by remember { mutableStateOf(Rank.C) }
    var day by remember { mutableStateOf<Int?>(null) } // null: as typed / the view's default; 0 today; 1 tomorrow
    var more by remember { mutableStateOf(false) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    text, { text = it }, Modifier.weight(1f), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
                    placeholder = { Text("Add a task", style = MaterialTheme.typography.bodyLarge, color = Pixel.InkSoft) },
                )
                PixelButton("Add", {
                    if (text.isNotBlank()) {
                        val parsed = QuickAdd.parse(text, model.store.today)
                        val chosenDay: LocalDate? = day?.let { model.store.today.plus(DatePeriod(days = it)) }
                        val planned = chosenDay ?: parsed.planned ?: if (parsed.due == null && view == View.TODAY) model.store.today else null
                        model.store.addTask(
                            title = parsed.title.ifBlank { text.trim() }, rank = parsed.rank ?: rank,
                            category = parsed.category ?: Category.PERSONAL,
                            planned = planned, due = parsed.due, estimateMinutes = parsed.estimateMinutes,
                        )
                        text = ""; day = null; more = false
                    }
                }, color = Pixel.Red, enabled = text.isNotBlank())
            }
            PixelButton(if (more) "Fewer options" else "More options", { more = !more }, color = Pixel.InkSoft, compact = true)
            if (more) {
                DifficultyPicker(rank) { rank = it }
                Text("When?", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    TabButton("As typed", day == null, { day = null }, Modifier.weight(1f))
                    TabButton("Today", day == 0, { day = 0 }, Modifier.weight(1f))
                    TabButton("Tomorrow", day == 1, { day = 1 }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TaskCard(model: AppModel, task: Task, expanded: Boolean, toggle: () -> Unit, onDeleted: () -> Unit) {
    val store = model.store
    var editing by remember(task.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        ListRow(
            lead = { RankBadge(task.rank) },
            onClick = toggle,
            trail = {
                if (task.state == TaskState.OPEN) PixelButton("Done", { store.completeTask(task.id) }, color = Pixel.Green, compact = true)
                else PixelButton("Reopen", { store.reopen(task.id) }, color = Pixel.InkSoft, compact = true)
            },
        ) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 4)
            val overdue = task.state == TaskState.OPEN && isOverdue(model, task)
            val meta = taskMeta(task)
            val line = listOf(if (overdue) "Overdue" else "", meta).filter { it.isNotEmpty() }.joinToString("  ·  ")
            if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodyMedium, color = if (overdue) Pixel.Danger else Pixel.InkSoft)
        }
        if (expanded) {
            TextBox {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                    Text("${task.rank.letter}: ${task.rank.title}", style = MaterialTheme.typography.bodyMedium)
                    if (editing) {
                        var title by remember { mutableStateOf(task.title) }
                        var rank by remember { mutableStateOf(task.rank) }
                        var minutes by remember { mutableStateOf(task.estimateMinutes ?: 0) }
                        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge, label = { Text("Title") })
                        DifficultyPicker(rank) { rank = it }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            PixelButton("-5", { minutes = (minutes - 5).coerceAtLeast(0) }, color = Pixel.InkSoft, compact = true)
                            Text(if (minutes == 0) "No estimate" else "$minutes min", style = MaterialTheme.typography.bodyLarge)
                            PixelButton("+5", { minutes += 5 }, color = Pixel.InkSoft, compact = true)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            PixelButton("Save", {
                                store.updateTask(task.copy(title = title.trim().ifEmpty { task.title }, rank = rank, estimateMinutes = minutes.takeIf { it > 0 }))
                                editing = false
                            }, Modifier.weight(1f), Pixel.Green, compact = true)
                            PixelButton("Cancel", { editing = false }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                        }
                    } else {
                        task.subtasks.forEach { sub ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(sub.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = if (sub.done) Pixel.InkSoft else Pixel.Ink)
                                if (!sub.done && task.state == TaskState.OPEN) PixelButton("Step done", { store.completeSubtask(task.id, sub.id) }, color = Pixel.Green, compact = true)
                            }
                        }
                        if (task.state == TaskState.OPEN) {
                            var step by remember { mutableStateOf("") }
                            if (task.subtasks.size < GameStore.MAX_SUBTASKS) {
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        step, { step = it }, Modifier.weight(1f), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
                                        placeholder = { Text("Add a small step", style = MaterialTheme.typography.bodyLarge, color = Pixel.InkSoft) },
                                    )
                                    PixelButton("Add", { store.addSubtask(task.id, step); step = "" }, enabled = step.isNotBlank(), compact = true)
                                }
                            }
                            Text("Move to", style = MaterialTheme.typography.bodyLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                                val today = store.today
                                TabButton("Today", task.planned == today, { store.updateTask(task.copy(planned = today)) })
                                TabButton("Tomorrow", task.planned == today.plus(DatePeriod(days = 1)), { store.updateTask(task.copy(planned = today.plus(DatePeriod(days = 1)))) })
                                TabButton("Next week", task.planned == today.plus(DatePeriod(days = 7)), { store.updateTask(task.copy(planned = today.plus(DatePeriod(days = 7)))) })
                                TabButton("No date", task.planned == null && task.due == null, { store.updateTask(task.copy(planned = null, due = null)) })
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                                PixelButton("Edit", { editing = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                                PixelButton("Won't do", { store.wontDo(task.id) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                                PixelButton("Delete", { store.deleteTask(task.id); onDeleted() }, Modifier.weight(1f), Pixel.Danger, compact = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isOverdue(model: AppModel, t: Task): Boolean {
    val now = model.store.now
    val due = t.due
    return if (due != null) due < now else t.planned != null && t.planned!! < now.date
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
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                title, { title = it }, Modifier.weight(1f), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = { Text("New daily habit", style = MaterialTheme.typography.bodyLarge, color = Pixel.InkSoft) },
            )
            PixelButton("Add", { store.addHabit(title, HabitTarget.Daily); title = "" }, color = Pixel.Red, enabled = title.isNotBlank())
        }
    }
    if (model.data.habits.isEmpty()) EmptyState(emptyTitle(View.HABITS), emptyLine(View.HABITS))
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
            Text(if (doneToday) "Done today" else "Streak ${h.streak(store.today, model.data.restDays)}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
