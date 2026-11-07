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
import app.pokeplan.plan.MonthlyRule
import app.pokeplan.plan.QuickAdd
import app.pokeplan.plan.Rank
import app.pokeplan.plan.ReminderOffset
import app.pokeplan.plan.Reminders
import app.pokeplan.plan.Repeat
import app.pokeplan.plan.Task
import app.pokeplan.plan.TaskState
import app.pokeplan.plan.TaskViews
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.theme.FieldLabel
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.RankBadge
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.theme.title
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.datetime.minus

internal enum class View(val label: String, val icon: String) {
    TODAY("Today", "oaks-parcel"), DAY("Calendar", "town-map"), INBOX("Inbox", "seal-bag"), UPCOMING("Next 7 days", "timer-ball"), TARGETS("Targets", "heart-scale"), DONE("Done", "premier-ball"),
    // Last, because widgets and shortcuts open a view by its index.
    ALL("All tasks", "scope-lens"),
}

@Composable
fun PlannerScreen(model: AppModel, startView: Int = -1, startWeek: Boolean = false, onBack: (() -> Unit)? = null) {
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
        View.TARGETS, View.DAY, View.ALL -> emptyList()
    }.filter { it.id !in pendingIds }

    fun card(showList: Boolean): @Composable (Task, LocalDate?) -> Unit = { t, day ->
        TaskCard(model, t, day, expanded == t.id, { expanded = if (expanded == t.id) null else t.id }, onDeleted = { model.deletedTask = t; expanded = null }, showList = showList)
    }

    // A list or a smart list slides in from the menu and back out; each keeps its scroll position.
    ScreenSwitch(view to openList, depth = { (v, l) -> if (v == null && l == null) 0 else 1 }) { (pageView, pageList) ->
        val list = pageList?.let { id -> model.data.lists.firstOrNull { it.id == id } }
        if (list != null) {
            ListScreen(model, list, TaskViews.inList(live, list.id).filter { it.id !in pendingIds }, card(showList = false)) { openList = null }
            return@ScreenSwitch
        }

        val current = pageView
        if (current == null) {
            val today = tasksFor(View.TODAY)
            val overdue = TaskViews.overdue(today, store.now).size
            val doneToday = store.doneToday()
            Column(Modifier.fillMaxSize()) {
                ScreenHeader("Planner", subtitle = if (today.isEmpty()) "Nothing left for today" else "${today.size} to do today", icon = "journal", onBack = onBack)
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
                                View.TARGETS -> store.frequencyTasks().let { hs -> if (hs.isEmpty()) null to "small things, often" else "${hs.count { store.today in it.completions }}/${hs.size}" to "done today" }
                                View.DONE -> "$doneToday" to "finished today"
                                View.ALL -> "${live.count { it.isOpen }}" to "search and filter"
                            }
                            ListTile(v, count, detail)
                        },
                    ) { view = it }
                    ListsSection(model, live) { openList = it }
                }
            }
            return@ScreenSwitch
        }

        SectionScaffold(current.label, "Planner", onBack = { view = null }, icon = current.icon) {
            RewardBox(model)
            when (current) {
                View.TARGETS -> FrequencyView(model)
                View.DAY -> DayView(model, pendingIds, startWeek)
                View.ALL -> AllTasksView(model, live.filter { it.id !in pendingIds }, card(showList = true))
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
}

private class ListTile(val view: View, val count: String?, val detail: String)

/** The list tint, the same on the menu tile and nowhere else (the lists themselves stay calm). */
private fun View.tint(): Color = when (this) {
    View.TODAY -> Pixel.Red; View.DAY -> Pixel.Teal; View.INBOX -> Pixel.Blue; View.UPCOMING -> Pixel.Warning
    View.TARGETS -> Pixel.Green; View.DONE -> Pixel.Violet; View.ALL -> Pixel.Gray
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
internal fun TaskGroup(
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

internal fun emptyTitle(view: View) = when (view) {
    View.TODAY -> "Nothing planned for today"
    View.INBOX -> "The inbox is empty"
    View.UPCOMING -> "Nothing coming up"
    View.DONE -> "Nothing finished yet"
    View.TARGETS -> "No targets yet"
    View.DAY, View.ALL -> ""
}

internal fun emptyLine(view: View) = when (view) {
    View.TODAY -> "Add one above, or plan one for today from the inbox or the calendar."
    View.INBOX -> "Tasks you add outside a list land here. File them in a list from a task's options."
    View.UPCOMING -> "Tasks planned for the next 7 days show up here."
    View.DONE -> "Tasks you finish show up here, newest first."
    View.TARGETS -> "Targets are small things you want to do often, like three times a week."
    View.DAY, View.ALL -> null
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

/** The ways a task can repeat from the planner; the engine's [Repeat] can say more (every 2 weeks, yearly), these are the common four. */
internal enum class RepeatKind(val label: String) {
    NEVER("Never"), DAILY("Daily"), WEEKDAYS("Weekdays"), WEEKLY("Weekly"), MONTHLY("Monthly"), CUSTOM("Custom");

    /** The repeat to start from [start]: monthly on its day of the month, weekly on the weekday of the start. Null for NEVER and CUSTOM. */
    fun toRepeat(start: LocalDate): Repeat? = when (this) {
        NEVER, CUSTOM -> null
        DAILY -> Repeat.Daily()
        WEEKDAYS -> Repeat.Weekly(days = WORKDAYS)
        WEEKLY -> Repeat.Weekly()
        MONTHLY -> Repeat.Monthly(rule = MonthlyRule.DayOfMonth(start.day))
    }

    /** What the repeat does, in a line. */
    val note: String get() = when (this) {
        DAILY -> "Every day, starting with its day."
        WEEKDAYS -> "Monday to Friday."
        WEEKLY -> "Every week, on the weekday of its first day."
        MONTHLY -> "Every month, on the date of its first day."
        NEVER, CUSTOM -> ""
    }

    companion object {
        private val WORKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        val PICKABLE = listOf(NEVER, DAILY, WEEKDAYS, WEEKLY, MONTHLY)

        fun of(repeat: Repeat?): RepeatKind = when {
            repeat == null -> NEVER
            repeat is Repeat.Daily && repeat.every == 1 -> DAILY
            repeat is Repeat.Weekly && repeat.every == 1 && repeat.days == WORKDAYS -> WEEKDAYS
            repeat is Repeat.Weekly && repeat.every == 1 && repeat.days.size <= 1 -> WEEKLY
            repeat is Repeat.Monthly && repeat.every == 1 -> MONTHLY
            else -> CUSTOM
        }
    }
}

private const val WEEKEND = 100

private const val NO_DATE = -1

/** Days from [today] to the coming Saturday (today on a Saturday or Sunday). */
private fun daysToWeekend(today: LocalDate): Int = today.dayOfWeek.isoDayNumber.let { if (it >= 6) 0 else 6 - it }

/** Add a task with just a title; the chips pick a day and "More options" adds difficulty. "Call mum tomorrow 5pm 30m" is understood too. */
@Composable
internal fun QuickAddBox(model: AppModel, view: View, listId: String? = null) {
    var text by remember { mutableStateOf("") }
    var rank by remember { mutableStateOf(Rank.C) }
    var day by remember { mutableStateOf<Int?>(null) } // null: as typed / the view's default; 0 today; 1 tomorrow; WEEKEND; NO_DATE
    var repeat by remember { mutableStateOf(RepeatKind.NEVER) }
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
            val made = model.store.addTask(
                title = parsed.title.ifBlank { text.trim() }, rank = parsed.rank ?: rank,
                category = parsed.category ?: Category.PERSONAL,
                planned = planned, due = parsed.due, estimateMinutes = parsed.estimateMinutes,
                // "#Groceries" files it in that list; inside a list, the list itself.
                listId = listId ?: parsed.listName?.let { model.store.listNamed(it)?.id },
                // "tomorrow 5pm" keeps its 5pm and "tonight" its evening; both only mean something on a planned day.
                plannedTime = parsed.plannedTime, evening = parsed.evening,
            )
            // A repeating task starts on its day (today when it has none) and the next ones are made ahead.
            repeat.toRepeat(made.planned ?: model.store.today)?.let { model.store.makeRepeating(made.id, it) }
            text = ""; day = null; more = false; repeat = RepeatKind.NEVER
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelField(
                    text, { text = it }, Modifier.weight(1f).focusRequester(focus), singleLine = true,
                    placeholder = "Add a task",
                    // The keyboard's own key adds the task, so the thumb need not travel to the button.
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                )
                PixelIconButton("plus", "Add", ::add, Pixel.Red, enabled = text.isNotBlank())
            }
            // What the line was understood as, so a typed "tomorrow 5pm #Social !B" is never a guess (Tesler: the app does the parsing, and shows it).
            val parsed = QuickAdd.parse(text, model.store.today)
            val understood = QuickAdd.hints(if (day != null) parsed.copy(planned = null) else parsed, model.store.today)
            if (understood.isNotEmpty()) Text("Understood: " + understood.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            // The rest waits until there is something to add, so the box is one field and one button (cta.gallery, plnty).
            if (text.isBlank() && !more) return@Column
            // When: tap a chip to choose a day, tap it again to go back to what the text says ("tomorrow 5pm") or the view's default.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                listOf("Today" to 0, "Tomorrow" to 1, "Weekend" to WEEKEND, "No date" to NO_DATE).forEach { (label, value) ->
                    TabButton(label, day == value, { day = if (day == value) null else value })
                }
            }
            PixelButton(if (more) "Fewer options" else "More options", { more = !more }, color = Pixel.InkSoft, compact = true)
            if (more) {
                DifficultyPicker(rank) { rank = it }
                FieldLabel("Repeat")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    RepeatKind.PICKABLE.forEach { k -> TabButton(k.label, repeat == k, { repeat = k }) }
                }
                if (repeat != RepeatKind.NEVER) Text(repeat.note, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
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
                val kind = store.seriesOf(task)?.let { RepeatKind.of(it.recurrence?.repeat) }
                val line = if (task.isOpen) taskMeta(task, day, kind?.let { if (it == RepeatKind.CUSTOM) "repeats" else "repeats ${it.label.lowercase()}" }) else ""
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
            if (task.state == TaskState.OPEN) PixelIconButton("check", "Done", { model.completeTask(task.id) }, Pixel.Green)
            else PixelButton("Reopen", { store.reopen(task.id) }, color = Pixel.InkSoft, compact = true)
        }
        if (expanded) {
            Box(Modifier.fillMaxWidth().background(Pixel.Shade.copy(alpha = 0.5f)).padding(Spacing.M)) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                    Text("${task.rank.letter}: ${task.rank.title}, ${task.category.label}", style = MaterialTheme.typography.bodyMedium)
                    if (editing) {
                        var title by remember { mutableStateOf(task.title) }
                        var rank by remember { mutableStateOf(task.rank) }
                        var minutes by remember { mutableStateOf(task.estimateMinutes ?: 0) }
                        var category by remember { mutableStateOf(task.category) }
                        var notes by remember { mutableStateOf(task.notes) }
                        var dueDay by remember { mutableStateOf(task.due?.date) }
                        PixelField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true, label = "Title")
                        PixelField(
                            notes, { notes = it }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 6, label = "Notes",
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                        DifficultyPicker(rank) { rank = it }
                        FieldLabel("Category")
                        CategoryChips(category) { category = it ?: category }
                        // A deadline is not the day you plan to do it (that is "Move to"); it keeps its time of day, or 23:59.
                        DayPicker("Deadline", dueDay, store.today, listOf("Today" to 0, "Tomorrow" to 1, "Next week" to 7), noneLabel = "No deadline") { dueDay = it }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            PixelButton("-5", { minutes = (minutes - 5).coerceAtLeast(0) }, color = Pixel.InkSoft, compact = true)
                            Text(if (minutes == 0) "No estimate" else "$minutes min", style = MaterialTheme.typography.bodyLarge)
                            PixelButton("+5", { minutes += 5 }, color = Pixel.InkSoft, compact = true)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            PixelIconButton("check", "Save", {
                                store.updateTask(
                                    task.copy(
                                        title = title.trim().ifEmpty { task.title }, rank = rank, estimateMinutes = minutes.takeIf { it > 0 },
                                        category = category, notes = notes.trim(),
                                        due = dueDay?.let { LocalDateTime(it, task.due?.time ?: LocalTime(23, 59)) },
                                    ),
                                )
                                editing = false
                            }, Pixel.Green)
                            PixelIconButton("close", "Cancel", { editing = false }, Pixel.InkSoft)
                        }
                    } else {
                        if (task.notes.isNotBlank()) Text(task.notes, style = MaterialTheme.typography.bodyLarge)
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
                                    PixelField(
                                        step, { step = it }, Modifier.weight(1f), singleLine = true,
                                        placeholder = "Add a small step",
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { if (step.isNotBlank()) { store.addSubtask(task.id, step); step = "" } }),
                                    )
                                    PixelIconButton("plus", "Add", { store.addSubtask(task.id, step); step = "" }, Pixel.InkSoft, enabled = step.isNotBlank())
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
                            RepeatRow(model, task)
                            HorizontalDivider(color = Pixel.Border)
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                                PixelIconButton("pencil", "Edit", { editing = true }, Pixel.InkSoft)
                                PixelButton("Won't do", { store.wontDo(task.id) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                                PixelIconButton("trash", "Delete", { store.deleteTask(task.id); onDeleted() }, Pixel.Danger)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Whether and how the task repeats: the four common ways, or Never to stop (the days ahead that are still untouched go). */
@Composable
private fun RepeatRow(model: AppModel, task: Task) {
    val store = model.store
    val series = store.seriesOf(task)
    val kind = RepeatKind.of(series?.recurrence?.repeat)
    FieldLabel("Repeat")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        RepeatKind.PICKABLE.forEach { k ->
            TabButton(k.label, kind == k, {
                if (k == RepeatKind.NEVER) store.stopRepeating(task.id)
                else k.toRepeat(series?.recurrence?.start ?: task.planned ?: store.today)?.let { store.makeRepeating(task.id, it) }
            })
        }
    }
    val note = if (kind == RepeatKind.CUSTOM) "A custom repeat. Choose one above to replace it." else kind.note
    if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
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

fun clock(t: LocalTime) = "${t.hour}:${t.minute.toString().padStart(2, '0')}"

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

/** "Mon 19 Oct, 45 min, 1/3 steps, repeats weekly" ([repeats] is the marker for a repeating task); the date is left out when it is [day] (the day the list is grouped on). */
fun taskMeta(t: Task, day: LocalDate? = null, repeats: String? = null): String = buildList {
    t.due?.let { add(listOfNotNull("due", it.date.takeIf { d -> d != day }?.short(), clock(it.time)).joinToString(" ")) }
        ?: t.planned?.let { p -> listOfNotNull(p.takeIf { it != day }?.short(), t.plannedTime?.let(::clock)).joinToString(" ").takeIf { it.isNotEmpty() }?.let(::add) }
    // Estimate versus actual once it has been focused on, so guesses calibrate over time.
    when {
        t.focusedMinutes > 0 && t.estimateMinutes != null -> add("focused ${t.focusedMinutes} of ${t.estimateMinutes} min")
        t.focusedMinutes > 0 -> add("focused ${t.focusedMinutes} min")
        else -> t.estimateMinutes?.let { add("$it min") }
    }
    if (t.reminders.isNotEmpty() && t.isOpen) add("reminder")
    if (t.subtasks.isNotEmpty()) add("${t.subtasks.count { it.done }}/${t.subtasks.size} steps")
    if (repeats != null) add(repeats)
}.joinToString(", ")

/** A list's tasks (or the Inbox's) grouped by when they are, as TickTick shows a list: from before, today, tomorrow... */
@Composable
internal fun WhenGroups(model: AppModel, tasks: List<Task>, card: @Composable (Task, LocalDate?) -> Unit) {
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
