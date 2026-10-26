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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import kotlinx.datetime.isoDayNumber
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.LaunchedEffect
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
import app.pokeplan.plan.ReminderOffset
import app.pokeplan.plan.Reminders
import app.pokeplan.plan.Task
import app.pokeplan.plan.TaskList
import app.pokeplan.plan.TaskState
import app.pokeplan.plan.TaskViews
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.theme.FieldLabel
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.datetime.minus

private enum class View(val label: String, val icon: String) {
    TODAY("Today", "oaks-parcel"), DAY("Calendar", "town-map"), INBOX("Inbox", "seal-bag"), UPCOMING("Next 7 days", "timer-ball"), HABITS("Habits", "heart-scale"), DONE("Done", "premier-ball"),
}

@Composable
fun PlannerScreen(model: AppModel, startView: Int = -1, startWeek: Boolean = false) {
    var view by remember { mutableStateOf(View.entries.getOrNull(startView)) }
    var expanded by remember { mutableStateOf<String?>(null) }
    // A list opened from the menu's Lists section (TickTick's lists); null shows the menu or the smart list in [view].
    var openList by remember { mutableStateOf<String?>(null) }
    val store = model.store
    val live = model.data.tasks.filter { it.state != TaskState.DELETED }
    val pendingIds = model.data.pending.map { it.original.id }.toSet()

    fun tasksFor(v: View): List<Task> = when (v) {
        View.TODAY -> store.todayTasks()
        View.INBOX -> TaskViews.inbox(live)
        View.UPCOMING -> TaskViews.upcoming(live, store.now)
        View.DONE -> live.filter { it.state == TaskState.DONE }.sortedByDescending { it.completedAtMillis }.take(30)
        View.HABITS, View.DAY -> emptyList()
    }.filter { it.id !in pendingIds }

    fun card(showList: Boolean): @Composable (Task, LocalDate?) -> Unit = { t, day ->
        TaskCard(model, t, day, expanded == t.id, { expanded = if (expanded == t.id) null else t.id }, onDeleted = { model.deletedTask = t; expanded = null }, showList = showList)
    }

    val list = openList?.let { id -> model.data.lists.firstOrNull { it.id == id } }
    if (list != null) {
        ListScreen(model, list, TaskViews.inList(live, list.id).filter { it.id !in pendingIds }, card(showList = false)) { openList = null }
        return
    }

    val current = view
    if (current == null) {
        val today = tasksFor(View.TODAY)
        val overdue = TaskViews.overdue(today, store.now).size
        val doneToday = store.doneToday()
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Planner", subtitle = if (today.isEmpty()) "Nothing left for today" else "${today.size} to do today", icon = "journal")
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.L),
                verticalArrangement = Arrangement.spacedBy(Spacing.L),
            ) {
                RewardBox(model)
                QuickAddBox(model, View.INBOX)
                // Smart lists, after Apple Reminders and Things: each tile says how many it holds, so the menu is also a glance at the day.
                ListTiles(
                    View.entries.map { v ->
                        val (count, detail) = when (v) {
                            View.TODAY -> "${today.size}" to if (overdue > 0) "$overdue from before" else "to do"
                            View.DAY -> null to "plan the hours"
                            View.INBOX -> "${tasksFor(v).size}" to "in no list"
                            View.UPCOMING -> "${tasksFor(v).size}" to "after today"
                            View.HABITS -> model.data.habits.let { hs -> if (hs.isEmpty()) null to "small things, often" else "${hs.count { store.today in it.completions }}/${hs.size}" to "done today" }
                            View.DONE -> "$doneToday" to "finished today"
                        }
                        ListTile(v, count, detail)
                    },
                ) { view = it }
                ListsSection(model, live) { openList = it }
            }
        }
        return
    }

    SectionScaffold(current.label, "Planner", onBack = { view = null }, icon = current.icon) {
        RewardBox(model)
        when (current) {
            View.HABITS -> HabitsView(model)
            View.DAY -> DayView(model, pendingIds, startWeek)
            else -> {
                if (current == View.TODAY || current == View.INBOX) QuickAddBox(model, current)
                val shown = tasksFor(current)
                val card = card(showList = true)
                when (current) {
                    View.TODAY -> TodayList(model, shown, card)
                    // Soon by the day each task is on, Done by the day it was finished (Todoist's Upcoming, Things' Logbook).
                    View.UPCOMING -> shown.groupBy { it.planned ?: it.due!!.date }.forEach { (day, ts) -> TaskGroup(dayName(model, day), ts, dayOf = day, card = card) }
                    View.DONE -> shown.groupBy { store.dayOf(it.completedAtMillis ?: 0L) }.forEach { (day, ts) -> TaskGroup(dayName(model, day), ts, card = card) }
                    View.INBOX -> WhenGroups(model, shown, card)
                    else -> if (shown.isNotEmpty()) TaskGroup(null, shown, card = card)
                }
                if (shown.isEmpty() && current != View.TODAY) EmptyState(emptyTitle(current), emptyLine(current), icon = current.icon)
            }
        }
    }
}

private class ListTile(val view: View, val count: String?, val detail: String)

/** The list tint, the same on the menu tile and nowhere else (the lists themselves stay calm). */
private fun View.tint(): Color = when (this) {
    View.TODAY -> Pixel.Red; View.DAY -> Color(0xFF0D9488); View.INBOX -> Pixel.Blue; View.UPCOMING -> Pixel.Warning
    View.HABITS -> Pixel.Green; View.DONE -> Color(0xFF8B5CF6)
}

/** Two tiles to a row: the item icon and the count on top, the name and one quiet line below. */
@Composable
private fun ListTiles(tiles: List<ListTile>, onOpen: (View) -> Unit) {
    val shape = RoundedCornerShape(Spacing.CardRadius)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                row.forEach { tile ->
                    Column(
                        Modifier.weight(1f).fillMaxHeight()
                            .background(Pixel.Panel, shape).border(1.dp, Pixel.Border, shape).clip(shape)
                            .clickable(role = Role.Button, onClickLabel = "Open ${tile.view.label}") { onOpen(tile.view) }
                            .padding(Spacing.M),
                        verticalArrangement = Arrangement.spacedBy(Spacing.XS),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).background(tile.view.tint().copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                                ItemIcon(tile.view.icon, size = 40.dp, description = null)
                            }
                            Spacer(Modifier.weight(1f))
                            if (tile.count != null) Text(tile.count, style = MaterialTheme.typography.headlineMedium)
                        }
                        Text(tile.view.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(tile.detail, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, maxLines = 2)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** "Today", "Tomorrow", "Yesterday" or "Wed 28 Oct". */
private fun dayName(model: AppModel, day: LocalDate): String {
    val today = model.store.today
    return when (day) {
        today -> "Today"
        today.plus(DatePeriod(days = 1)) -> "Tomorrow"
        today.minus(DatePeriod(days = 1)) -> "Yesterday"
        else -> day.short()
    }
}

/**
 * Today, after Things and Todoist: how the day is going, then the tasks from before (with one button to bring them to
 * today), then today's, then "This evening". Every group sits in one panel, rows split by lines.
 */
@Composable
private fun TodayList(model: AppModel, tasks: List<Task>, card: @Composable (Task, LocalDate?) -> Unit) {
    val store = model.store
    val done = store.doneToday()
    if (tasks.isNotEmpty() || done > 0) {
        val total = tasks.size + done
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Text("$done of $total done", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(Pixel.Shade)) {
                if (done > 0) Box(Modifier.fillMaxWidth(done.toFloat() / total).fillMaxHeight().clip(CircleShape).background(Pixel.Green))
            }
        }
    }
    if (tasks.isEmpty()) {
        if (done > 0) EmptyState("All clear for today", "$done finished. The rest can wait for tomorrow.", icon = View.TODAY.icon)
        else EmptyState(emptyTitle(View.TODAY), emptyLine(View.TODAY), icon = View.TODAY.icon)
        return
    }
    val before = TaskViews.overdue(tasks, store.now).toSet()
    val (day, evening) = tasks.filter { it !in before }.partition { !TaskViews.isEvening(it) }
    // Planned for an earlier day can move to today; a deadline that has passed stays as it is (moving the plan would not change it).
    val movable = before.filter { it.due == null }
    if (before.isNotEmpty()) TaskGroup(
        "From before", before.toList(), color = Pixel.Warning,
        action = if (movable.isEmpty()) null else "Move to today" to { movable.forEach { store.updateTask(it.copy(planned = store.today)) } },
        card = card,
    )
    if (day.isNotEmpty()) TaskGroup(if (before.isEmpty() && evening.isEmpty()) null else "Today", day, dayOf = store.today, card = card)
    if (evening.isNotEmpty()) TaskGroup("This evening", evening, dayOf = store.today, card = card)
}

/**
 * A heading with its count and an optional quiet action, then the tasks in one panel. [dayOf] is the day the whole
 * group is on, so the rows leave it out and show only their time.
 */
@Composable
private fun TaskGroup(
    title: String?, tasks: List<Task>, color: Color = Pixel.Ink, dayOf: LocalDate? = null,
    action: Pair<String, () -> Unit>? = null, card: @Composable (Task, LocalDate?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        if (title != null) {
            Row(Modifier.fillMaxWidth().padding(top = Spacing.S), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = color, modifier = Modifier.semantics { heading() })
                Text("${tasks.size}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                Spacer(Modifier.weight(1f))
                if (action != null) PixelButton(action.first, action.second, color = Pixel.InkSoft, compact = true)
            }
        }
        val shape = RoundedCornerShape(Spacing.CardRadius)
        Column(Modifier.fillMaxWidth().background(Pixel.Panel, shape).border(1.dp, Pixel.Border, shape).clip(shape)) {
            tasks.forEachIndexed { i, t ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = Spacing.M), color = Pixel.Border)
                card(t, dayOf)
            }
        }
    }
}

/**
 * What an empty list says, and what to do about it: a small real item icon, a short title, one line, and at most one
 * action, in the game's own words.
 */
@Composable
fun EmptyState(title: String, line: String? = null, icon: String? = null, action: Pair<String, () -> Unit>? = null) {
    TextBox {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
            if (icon != null) ItemIcon(icon, size = 40.dp, description = null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (line != null) Text(line, style = MaterialTheme.typography.bodyLarge)
                if (action != null) PixelButton(action.first, action.second, Modifier.padding(top = Spacing.XS), Pixel.InkSoft, compact = true)
            }
        }
    }
}

private fun emptyTitle(view: View) = when (view) {
    View.TODAY -> "Nothing planned for today"
    View.INBOX -> "The inbox is empty"
    View.UPCOMING -> "Nothing coming up"
    View.DONE -> "Nothing finished yet"
    View.HABITS -> "No habits yet"
    View.DAY -> ""
}

private fun emptyLine(view: View) = when (view) {
    View.TODAY -> null
    View.INBOX -> "Tasks you add outside a list land here. File them in a list from a task's options."
    View.UPCOMING -> "Tasks planned for the next 7 days show up here."
    View.DONE -> null
    View.HABITS -> "Habits are small things you want to do often."
    View.DAY -> null
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

private const val WEEKEND = 100
private const val NO_DATE = -1

/** Days from [today] to the coming Saturday (today on a Saturday or Sunday). */
private fun daysToWeekend(today: LocalDate): Int = today.dayOfWeek.isoDayNumber.let { if (it >= 6) 0 else 6 - it }

/** Add a task with just a title; the chips pick a day and "More options" adds difficulty. "Call mum tomorrow 5pm 30m" is understood too. */
@Composable
private fun QuickAddBox(model: AppModel, view: View, listId: String? = null) {
    var text by remember { mutableStateOf("") }
    var rank by remember { mutableStateOf(Rank.C) }
    var day by remember { mutableStateOf<Int?>(null) } // null: as typed / the view's default; 0 today; 1 tomorrow; WEEKEND; NO_DATE
    val focus = remember { FocusRequester() }
    // "Add task" from a widget or the app icon's shortcut lands here with the keyboard open.
    LaunchedEffect(model.addTaskRequested) {
        if (model.addTaskRequested) {
            focus.requestFocus()
            model.addTaskRequested = false
        }
    }
    var more by remember { mutableStateOf(false) }
    fun add() {
        if (text.isNotBlank()) {
            val parsed = QuickAdd.parse(text, model.store.today)
            val picked = day
            val planned: LocalDate? = when {
                picked == NO_DATE -> null
                picked == WEEKEND -> model.store.today.plus(DatePeriod(days = daysToWeekend(model.store.today)))
                picked != null -> model.store.today.plus(DatePeriod(days = picked))
                else -> parsed.planned ?: if (parsed.due == null && view == View.TODAY) model.store.today else null
            }
            model.store.addTask(
                title = parsed.title.ifBlank { text.trim() }, rank = parsed.rank ?: rank,
                category = parsed.category ?: Category.PERSONAL,
                planned = planned, due = parsed.due, estimateMinutes = parsed.estimateMinutes,
                // "#Groceries" files it in that list; inside a list, the list itself.
                listId = listId ?: parsed.listName?.let { model.store.listNamed(it)?.id },
            )
            text = ""; day = null; more = false
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    text, { text = it }, Modifier.weight(1f).focusRequester(focus), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
                    placeholder = { Text("Add a task", style = MaterialTheme.typography.bodyLarge, color = Pixel.InkSoft) },
                    // The keyboard's own key adds the task, so the thumb need not travel to the button.
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                )
                PixelButton("Add", ::add, color = Pixel.Red, enabled = text.isNotBlank())
            }
            // The rest waits until there is something to add, so the box is one field and one button (cta.gallery, plnty).
            if (text.isBlank() && !more) return@Column
            // When: tap a chip to choose a day, tap it again to go back to what the text says ("tomorrow 5pm") or the view's default.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                listOf("Today" to 0, "Tomorrow" to 1, "Weekend" to WEEKEND, "No date" to NO_DATE).forEach { (label, value) ->
                    TabButton(label, day == value, { day = if (day == value) null else value })
                }
            }
            PixelButton(if (more) "Fewer options" else "More options", { more = !more }, color = Pixel.InkSoft, compact = true)
            if (more) DifficultyPicker(rank) { rank = it }
        }
    }
}

@Composable
private fun TaskCard(model: AppModel, task: Task, day: LocalDate?, expanded: Boolean, toggle: () -> Unit, onDeleted: () -> Unit, showList: Boolean = true) {
    val store = model.store
    val list = task.listId?.let { id -> model.data.lists.firstOrNull { it.id == id } }
    var editing by remember(task.id) { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth()
                .clickable(onClickLabel = if (expanded) "Hide options" else "Show options", role = Role.Button, onClick = toggle)
                .defaultMinSize(minHeight = 64.dp)
                .padding(Spacing.M),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            RankBadge(task.rank)
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 4, overflow = TextOverflow.Ellipsis, color = if (task.isOpen) Pixel.Ink else Pixel.InkSoft)
                // Overdue tasks sit under Today's calm "From before" heading, so the row needs no alarm of its own (docs/design.md, No shame).
                // A finished task's plan no longer matters: the Done list is grouped by the day it was finished.
                val line = if (task.isOpen) taskMeta(task, day) else ""
                // Its list, by name and colour, as TickTick shows it in the smart lists (not inside the list itself).
                val shownList = list?.takeIf { showList }
                if (line.isNotEmpty() || shownList != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    if (shownList != null) {
                        Box(Modifier.size(8.dp).background(Color(shownList.color), CircleShape))
                        Text(shownList.name, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (line.isNotEmpty()) Text(if (shownList != null) "· $line" else line, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                }
            }
            if (task.state == TaskState.OPEN) PixelButton("Done", { model.completeTask(task.id) }, color = Pixel.Green, compact = true)
            else PixelButton("Reopen", { store.reopen(task.id) }, color = Pixel.InkSoft, compact = true)
        }
        if (expanded) {
            Box(Modifier.fillMaxWidth().background(Pixel.Shade.copy(alpha = 0.5f)).padding(Spacing.M)) {
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
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { if (step.isNotBlank()) { store.addSubtask(task.id, step); step = "" } }),
                                    )
                                    PixelButton("Add", { store.addSubtask(task.id, step); step = "" }, enabled = step.isNotBlank(), compact = true)
                                }
                            }
                            FieldLabel("Move to")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                                val today = store.today
                                TabButton("Today", task.planned == today, { store.updateTask(task.copy(planned = today)) })
                                TabButton("Tomorrow", task.planned == today.plus(DatePeriod(days = 1)), { store.updateTask(task.copy(planned = today.plus(DatePeriod(days = 1)))) })
                                TabButton("Next week", task.planned == today.plus(DatePeriod(days = 7)), { store.updateTask(task.copy(planned = today.plus(DatePeriod(days = 7)))) })
                                TabButton("No date", task.planned == null && task.due == null, { store.updateTask(task.copy(planned = null, plannedTime = null, due = null)) })
                            }
                            if (store.activeLists.isNotEmpty()) {
                                FieldLabel("List")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                                    TabButton("Inbox", task.listId == null, { store.moveToList(task.id, null) })
                                    store.activeLists.forEach { l -> TabButton(l.name, task.listId == l.id, { store.moveToList(task.id, l.id) }) }
                                }
                            }
                            PlannedTime(model, task)
                            RemindMe(model, task)
                            HorizontalDivider(color = Pixel.Border)
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

/** A few common times and a 15-minute nudge, rather than a clock dial. */
@Composable
private fun PlannedTime(model: AppModel, task: Task) {
    val store = model.store
    val time = task.plannedTime
    FieldLabel("At")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        TabButton("Any time", time == null, { store.setPlannedTime(task.id, null) })
        TIME_PRESETS.forEach { preset -> TabButton(clock(preset), time == preset, { store.setPlannedTime(task.id, preset) }) }
    }
    if (time != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelButton("-15", { store.setPlannedTime(task.id, shifted(time, -15)) }, color = Pixel.InkSoft, compact = true)
            Text(clock(time), style = MaterialTheme.typography.bodyLarge)
            PixelButton("+15", { store.setPlannedTime(task.id, shifted(time, 15)) }, color = Pixel.InkSoft, compact = true)
        }
    }
}

private val TIME_PRESETS = listOf(LocalTime(9, 0), LocalTime(12, 0), LocalTime(15, 0), LocalTime(19, 0))

internal fun clock(t: LocalTime) = "${t.hour}:${t.minute.toString().padStart(2, '0')}"

/** Moves [t] by [minutes] within the same day (it stops at 0:00 and 23:45). */
internal fun shifted(t: LocalTime, minutes: Int): LocalTime {
    val total = (t.hour * 60 + t.minute + minutes).coerceIn(0, 23 * 60 + 45)
    return LocalTime(total / 60, total % 60)
}

/** Up to three reminders, counted from the planned time or the deadline; a task without a day has nothing to count from. */
@Composable
private fun RemindMe(model: AppModel, task: Task) {
    FieldLabel("Remind me")
    val base = Reminders.baseTime(task)
    if (base == null) {
        Text("Give it a day first.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        ReminderOffset.PRESETS.forEach { offset ->
            val on = offset in task.reminders
            TabButton(offset.label, on, {
                if (on) model.store.setReminders(task.id, task.reminders - offset)
                else if (task.reminders.size < Reminders.MAX_PER_TASK) model.store.setReminders(task.id, task.reminders + offset)
            })
        }
    }
    val time = clock(base.time)
    val note = when {
        !model.store.data.settings.reminders -> "Reminders are off in Settings."
        task.reminders.size >= Reminders.MAX_PER_TASK -> "Up to ${Reminders.MAX_PER_TASK}. Counted from ${base.date.short()} $time."
        else -> "Counted from ${base.date.short()} $time."
    }
    Text(note, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
}

/** "Mon 19 Oct, 45 min, 1/3 steps"; the date is left out when it is [day] (the day the list is grouped on). */
fun taskMeta(t: Task, day: LocalDate? = null): String = buildList {
    t.due?.let { add(listOfNotNull("due", it.date.takeIf { d -> d != day }?.short(), clock(it.time)).joinToString(" ")) }
        ?: t.planned?.let { p -> listOfNotNull(p.takeIf { it != day }?.short(), t.plannedTime?.let(::clock)).joinToString(" ").takeIf { it.isNotEmpty() }?.let(::add) }
    t.estimateMinutes?.let { add("$it min") }
    if (t.reminders.isNotEmpty() && t.isOpen) add("reminder")
    if (t.subtasks.isNotEmpty()) add("${t.subtasks.count { it.done }}/${t.subtasks.size} steps")
}.joinToString(", ")

/** A list's tasks (or the Inbox's) grouped by when they are, as TickTick shows a list: from before, today, tomorrow... */
@Composable
private fun WhenGroups(model: AppModel, tasks: List<Task>, card: @Composable (Task, LocalDate?) -> Unit) {
    val today = model.store.today
    TaskViews.byWhen(tasks, model.store.now).forEach { (w, ts) ->
        TaskGroup(
            w.label, ts, color = if (w == TaskViews.When.BEFORE) Pixel.Warning else Pixel.Ink,
            dayOf = when (w) {
                TaskViews.When.TODAY -> today
                TaskViews.When.TOMORROW -> today.plus(DatePeriod(days = 1))
                else -> null
            },
            card = card,
        )
    }
}

/** The colour names, for screen readers, in the order of [TaskList.COLORS]. */
private val listColorNames = listOf("Red", "Blue", "Green", "Amber", "Teal", "Purple", "Pink", "Slate")

/** The menu's Lists: one row per list with its icon and count, "New list", and the archived ones folded away. */
@Composable
private fun ListsSection(model: AppModel, tasks: List<Task>, open: (String) -> Unit) {
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
        if (lists.isEmpty() && !creating) Text(
            "Make a list for each part of your life (Groceries, Thesis, Home). Tasks in no list stay in the Inbox.",
            style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
        )
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
    val shape = RoundedCornerShape(Spacing.CardRadius)
    Column(Modifier.fillMaxWidth().background(Pixel.Panel, shape).border(1.dp, Pixel.Border, shape).clip(shape)) {
        lists.forEachIndexed { i, l ->
            if (i > 0) HorizontalDivider(Modifier.padding(start = Spacing.M), color = Pixel.Border)
            val count = tasks.count { it.isOpen && it.listId == l.id }
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Open ${l.name}") { open(l.id) }
                    .defaultMinSize(minHeight = 56.dp).padding(horizontal = Spacing.M, vertical = Spacing.S),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M),
            ) {
                Box(Modifier.size(40.dp).background(Color(l.color).copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                    ItemIcon(l.icon, size = 32.dp, description = null)
                }
                Text(l.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (count > 0) Text("$count", style = MaterialTheme.typography.bodyLarge, color = Pixel.InkSoft)
            }
        }
    }
}

/** One list: add straight into it, its tasks grouped by when they are, and Edit for its name, colour, icon and archive. */
@Composable
private fun ListScreen(model: AppModel, list: TaskList, tasks: List<Task>, card: @Composable (Task, LocalDate?) -> Unit, back: () -> Unit) {
    var editing by remember(list.id) { mutableStateOf(false) }
    SectionScaffold(list.name, if (list.archived) "Archived list" else "Planner", onBack = back, icon = list.icon) {
        RewardBox(model)
        if (editing) ListEditor(model, list) { kept -> editing = false; if (kept == null) back() }
        else PixelButton("Edit list", { editing = true }, color = Pixel.InkSoft, compact = true)
        if (!list.archived) QuickAddBox(model, View.INBOX, listId = list.id)
        WhenGroups(model, tasks, card)
        if (tasks.isEmpty()) EmptyState("Nothing in ${list.name}", "Add a task above, or file one here from its options.", icon = list.icon)
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
            OutlinedTextField(
                name, { name = it.take(TaskList.MAX_NAME) }, Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
                label = { Text("List name") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            )
            FieldLabel("Colour")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                TaskList.COLORS.forEachIndexed { i, c ->
                    val on = c == color
                    Box(
                        Modifier.size(Spacing.Target).clip(CircleShape)
                            .selectable(on, role = Role.RadioButton) { color = c }
                            .semantics { contentDescription = listColorNames[i] },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(32.dp).background(Color(c), CircleShape).border(if (on) 3.dp else 0.dp, if (on) Pixel.Ink else Color.Transparent, CircleShape))
                    }
                }
            }
            FieldLabel("Icon")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                TaskList.ICONS.forEach { id ->
                    val on = id == icon
                    Box(
                        Modifier.size(Spacing.Target).clip(RoundedCornerShape(12.dp))
                            .background(if (on) Color(color).copy(alpha = 0.2f) else Color.Transparent)
                            .border(if (on) 2.dp else 0.dp, if (on) Color(color) else Color.Transparent, RoundedCornerShape(12.dp))
                            .selectable(on, role = Role.RadioButton) { icon = id }
                            .semantics { contentDescription = id.replace('-', ' ') },
                        contentAlignment = Alignment.Center,
                    ) { ItemIcon(id, size = 36.dp, description = null) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelButton(if (list == null) "Make list" else "Save", {
                    if (list == null) done(store.addList(name, color, icon)?.id)
                    else { store.updateList(list.copy(name = name, color = color, icon = icon)); done(list.id) }
                }, Modifier.weight(1f), Pixel.Green, enabled = name.isNotBlank(), compact = true)
                PixelButton("Cancel", { done(list?.id) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
            if (list != null) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelButton(if (list.archived) "Unarchive" else "Archive", { store.updateList(list.copy(archived = !list.archived)); done(list.id) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                PixelButton("Delete", { store.deleteList(list.id); done(null) }, Modifier.weight(1f), Pixel.Danger, compact = true)
            }
            if (list != null) Text("Deleting a list moves its tasks to the Inbox.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
}

@Composable
private fun HabitsView(model: AppModel) {
    val store = model.store
    var title by remember { mutableStateOf("") }
    TextBox {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                title, { title = it }, Modifier.weight(1f), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = { Text("New daily habit", style = MaterialTheme.typography.bodyLarge, color = Pixel.InkSoft) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (title.isNotBlank()) { store.addHabit(title, HabitTarget.Daily); title = "" } }),
            )
            PixelButton("Add", { store.addHabit(title, HabitTarget.Daily); title = "" }, color = Pixel.Red, enabled = title.isNotBlank())
        }
    }
    if (model.data.habits.isEmpty()) EmptyState(emptyTitle(View.HABITS), emptyLine(View.HABITS), icon = View.HABITS.icon)
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
