package app.pokeplan.ui.plan

import app.pokeplan.ui.theme.Sizes
import app.pokeplan.ui.theme.DialogueShape

import app.pokeplan.ui.theme.frameToneOf
import app.pokeplan.ui.theme.dialogueBox
import app.pokeplan.ui.theme.spoken
import androidx.compose.ui.semantics.contentDescription
import app.pokeplan.ui.theme.PixelShape
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.SpriteAction
import app.pokeplan.ui.theme.ModuleAccent
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
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
import app.pokeplan.plan.MonthlyRule
import app.pokeplan.plan.PlanDays
import app.pokeplan.plan.instanceDay
import app.pokeplan.plan.movedOccurrence
import app.pokeplan.plan.words
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.datetime.minus
import app.pokeplan.ui.pokemon.label
import app.pokeplan.ui.screens.CalendarSettings
import app.pokeplan.ui.screens.LocalBuddy
import app.pokeplan.ui.screens.LocalReduceMotion
import app.pokeplan.ui.screens.NextUp
import app.pokeplan.ui.screens.PixelDateField
import app.pokeplan.ui.screens.PixelDatePicker
import app.pokeplan.ui.screens.PixelDateTimeField
import app.pokeplan.ui.screens.PixelDurationField
import app.pokeplan.ui.screens.PixelTimeField
import app.pokeplan.ui.screens.PlanSection
import app.pokeplan.ui.screens.ScreenSwitch
import app.pokeplan.ui.screens.SectionScaffold
import app.pokeplan.ui.screens.TabButton
import app.pokeplan.ui.screens.TaskTitleLine
import app.pokeplan.ui.screens.dayNames
import app.pokeplan.ui.screens.label
import app.pokeplan.ui.screens.long
import app.pokeplan.ui.screens.short

/** Where a quick-add box files a task when the words name no day: Today plans it for today, the Inbox and a list leave it undated. */
internal enum class AddTo { TODAY, INBOX }

/** Which task card is open on a page, so a page opens one card at a time. */
@androidx.compose.runtime.Stable
internal class CardHost { var expanded by mutableStateOf<String?>(null) }

/** A task row that opens into its card; [showList] adds the list's name to the details (off inside that list). */
internal fun taskCards(model: AppModel, host: CardHost, showList: Boolean = true): @Composable (Task, LocalDate?) -> Unit = { t, day ->
    TaskCard(model, t, day, host.expanded == t.id, { host.expanded = if (host.expanded == t.id) null else t.id }, onDeleted = { model.deletedTask = t; host.expanded = null }, showList = showList)
}

/** Plan's tasks as its pages show them: not deleted, and not one just ticked off and waiting out its undo. */
internal fun planTasks(model: AppModel): List<Task> {
    val pending = model.data.pending.map { it.original.id }.toSet()
    return model.data.tasks.filter { it.state != TaskState.DELETED && it.id !in pending }
}

/**
 * One of Plan's task pages behind the Today menu (PLAN.md section 3): Inbox, Upcoming, Lists, Calendar, Targets and All tasks.
 * [open] goes to another section (Upcoming's Calendar key); [toToday] goes back to Today, where the quick-add box is.
 */
@Composable
internal fun PlanView(model: AppModel, section: PlanSection, onBack: () -> Unit, open: (PlanSection) -> Unit, toToday: () -> Unit, startWeek: Boolean = false) =
    app.pokeplan.ui.theme.ModuleTone(ModuleAccent.PLAN) { PlanViewBody(model, section, onBack, open, toToday, startWeek) }

@Composable
private fun PlanViewBody(model: AppModel, section: PlanSection, onBack: () -> Unit, open: (PlanSection) -> Unit, toToday: () -> Unit, startWeek: Boolean) {
    val host = remember { CardHost() }
    val store = model.store
    val live = planTasks(model)
    if (section == PlanSection.LISTS) {
        // A list slides in from the Lists page and back out; each keeps its scroll position.
        var openList by remember { mutableStateOf<String?>(null) }
        ScreenSwitch(openList, depth = { if (it == null) 0 else 1 }) { id ->
            val list = id?.let { i -> model.data.lists.firstOrNull { it.id == i } }
            if (list != null) ListScreen(model, list, TaskViews.inList(live, list.id), taskCards(model, host, showList = false)) { openList = null }
            else SectionScaffold(section.label, null, onBack = onBack, icon = section.icon, color = ModuleAccent.PLAN.header) { ListsSection(model, live) { openList = it } }
        }
        return
    }
    val pendingIds = model.data.pending.map { it.original.id }.toSet()
    val card = taskCards(model, host)
    val calendarKey: (@Composable () -> Unit)? = if (section != PlanSection.UPCOMING) null else ({ PixelButton("Calendar", { open(PlanSection.CALENDAR) }, color = Pixel.InkSoft, compact = true) })
    SectionScaffold(section.label, null, onBack = onBack, icon = section.icon, color = ModuleAccent.PLAN.header, trailing = calendarKey) {
        when (section) {
            PlanSection.TARGETS -> FrequencyView(model)
            PlanSection.CALENDAR -> { DayView(model, pendingIds, startWeek); CalendarSettings(model) }
            PlanSection.ALL -> AllTasksView(model, live, card, onAdd = { model.addTaskRequested = true; toToday() })
            PlanSection.INBOX -> {
                QuickAddBox(model, AddTo.INBOX)
                val shown = TaskViews.inbox(live)
                if (shown.isEmpty()) EmptyState("The inbox is empty", icon = section.icon, addAbove = "Add a task")
                else TaskGroup(null, shown.sortedByDescending { it.createdAtMillis }, card = card)
            }
            PlanSection.UPCOMING -> {
                // By the day each task is on (Todoist's Upcoming), with no cap on how far ahead.
                val shown = TaskViews.upcoming(live, store.now)
                shown.groupBy { it.planned ?: it.due!!.date }.forEach { (day, ts) -> TaskGroup(dayName(model, day), ts, dayOf = day, card = card) }
                if (shown.isEmpty()) EmptyState("Nothing coming up", icon = section.icon, action = "Add a task" to { model.addTaskRequested = true; toToday() })
            }
            else -> {}
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
 * Plan's Today (PLAN.md section 3.1), after Things and Todoist: how the day is going, the task to do next (with Focus on this
 * and Pick for me, +10% candies), the tasks from before (with one button to bring them to today), today's, today's unmet
 * frequency targets, "This evening", and what was finished today folded away. Every group sits in one panel, rows split by
 * lines. [onFocus] is null where there is no Focus (desktop and the web).
 */
@Composable
internal fun TodayList(model: AppModel, onFocus: ((Task) -> Unit)?) {
    val store = model.store
    val data = model.data
    val host = remember { CardHost() }
    val card = taskCards(model, host)
    val tasks = store.openTodayTasks()
    val targets = store.frequencyTasks().filter { store.frequencyDueToday(it) }
    val done = store.doneToday()
    if (tasks.isNotEmpty() || done > 0) {
        val total = tasks.size + done
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Text("$done of $total done", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            app.pokeplan.ui.theme.PixelBar(done.toFloat() / total, Modifier.weight(1f), label = "$done of $total done")
        }
    }
    if (tasks.isEmpty() && targets.isEmpty()) {
        if (done > 0) EmptyState("All clear for today", icon = PlanSection.TODAY_ICON, sleeper = true, action = "Add another" to { model.addTaskRequested = true })
        else EmptyState("Nothing planned for today", icon = PlanSection.TODAY_ICON, sleeper = true, action = "Add a task" to { model.addTaskRequested = true })
    }
    // The picked task, else the first of today's own (an overdue one stays under "From before", with its Move to today).
    val late = TaskViews.overdue(tasks, store.now).toSet()
    val next = tasks.firstOrNull { it.id == data.pickedTaskId } ?: tasks.firstOrNull { it !in late && !TaskViews.isEvening(it) } ?: tasks.firstOrNull { it !in late } ?: tasks.firstOrNull()
    if (next != null) NextUp(
        task = next,
        onDone = { model.completeTask(next.id) },
        onFocus = onFocus?.let { f -> { f(next) } },
        onChoose = if (tasks.size > 1 || data.pickedTaskId != next.id) ({ store.pickForMe() }) else null,
        picked = next.id == data.pickedTaskId,
        day = store.today,
    )
    val rest = tasks.filter { it.id != next?.id }
    val before = TaskViews.overdue(rest, store.now).toSet()
    val (day, evening) = rest.filter { it !in before }.partition { !TaskViews.isEvening(it) }
    // Planned for an earlier day can move to today; a deadline that has passed stays as it is (moving the plan would not change it).
    val movable = late.filter { it.due == null }
    if (before.isNotEmpty()) TaskGroup(
        "From before", before.toList(), color = Pixel.Warning,
        action = if (movable.isEmpty()) null else "Move to today" to { movable.forEach { store.updateTask(it.copy(planned = store.today)) } },
        card = card,
    )
    if (day.isNotEmpty()) TaskGroup(if (before.isEmpty() && evening.isEmpty() && targets.isEmpty()) null else "Today", day, dayOf = store.today, card = card)
    if (targets.isNotEmpty()) TargetGroup(model, targets)
    if (evening.isNotEmpty()) TaskGroup("This evening", evening, dayOf = store.today, card = card)
    // What was finished today, folded away (Reopen is on each row).
    if (done > 0) {
        var showDone by remember { mutableStateOf(false) }
        PixelButton(if (showDone) "Hide done" else "Done today ($done)", { showDone = !showDone }, color = Pixel.InkSoft, compact = true)
        if (showDone) TaskGroup(null, data.tasks.filter { it.state == TaskState.DONE && (it.completedAtMillis ?: 0L) >= store.startOfTodayMillis }.sortedByDescending { it.completedAtMillis }, card = card)
    }
}

/** Today's frequency targets not yet met, as a group of rows with the same check key as a task; the Targets page edits them. */
@Composable
private fun TargetGroup(model: AppModel, targets: List<app.pokeplan.app.TaskSeries>) {
    val store = model.store
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Row(Modifier.fillMaxWidth().padding(top = Spacing.S), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("Targets", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Text("${targets.size}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
        val shape = PixelShape
        Column(Modifier.fillMaxWidth().background(Pixel.Panel, shape).border(Sizes.Hairline, Pixel.Border, shape).clip(shape)) {
            targets.forEachIndexed { i, h ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = Spacing.M), color = Pixel.Border)
                Row(
                    Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp).padding(Spacing.M),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                ) {
                    PixelIconButton("check", "Done", { model.effect(app.pokeplan.ui.Sfx.HABIT_TICK); store.completeFrequencyTask(h.template.id) }, Pixel.Green)
                    Column(Modifier.weight(1f)) {
                        TaskTitleLine(h.template, MaterialTheme.typography.bodyLarge)
                        store.frequencyProgress(h)?.let { Text(it.label(), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
                    }
                }
            }
        }
    }
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
        val shape = PixelShape
        Column(Modifier.fillMaxWidth().background(Pixel.Panel, shape).border(Sizes.Hairline, Pixel.Border, shape).clip(shape)) {
            tasks.forEachIndexed { i, t ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = Spacing.M), color = Pixel.Border)
                card(t, dayOf)
            }
        }
    }
}

/**
 * What an empty list says, and what to do about it: a small real item icon (or the sleeping buddy), a short title, and at most one
 * action. [line] is not drawn (an empty screen needs no explanation; the Help screen has the rules); a screen reader reads it after the title.
 */
@Composable
fun EmptyState(
    title: String, icon: String? = null, action: Pair<String, () -> Unit>? = null, sleeper: Boolean = false,
    /** For a list whose form sits right above it: the button is labelled [addAbove] and moves the focus to the field above, so there is always one next step on the screen. */
    addAbove: String? = null,
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val action = action ?: addAbove?.let { it to { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Up); Unit } }
    // With [sleeper] the global buddy dozes beside the words (its own SpriteCollab Sleep animation, still with Reduce motion).
    val buddy = if (sleeper) LocalBuddy.current else null
    TextBox {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
            if (buddy != null) AnimatedSprite(buddy.form.id, buddy.isShiny, "", size = Sizes.SpriteS, animate = !LocalReduceMotion.current, ground = 4.dp, action = SpriteAction.SLEEP, actionKey = 1)
            else if (icon != null) ItemIcon(icon, size = Sizes.IconL, description = null)
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (action != null) PixelButton(action.first, action.second, Modifier.padding(top = Spacing.XS), Pixel.Tone, compact = true)
            }
        }
    }
}



/** What the difficulty ranks mean. Harder tasks earn more; it is how hard it feels, not how urgent it is. */
@Composable
fun DifficultyPicker(selected: Rank, onSelect: (Rank) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text("How hard does it feel?", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Rank.entries.forEach { r -> RankBadge(r, selected = r == selected, onClick = { onSelect(r) }) }
        }
    }
}

/**
 * The ways a task can repeat from the planner. Weekly then takes the weeks between (every 1 to 4) and the weekdays ([RepeatRow]);
 * the engine's [Repeat] can say more (every 3 days), which shows as CUSTOM in words.
 */
internal enum class RepeatKind(val label: String) {
    NEVER("Never"), DAILY("Daily"), WEEKDAYS("Weekdays"), WEEKLY("Weekly"), MONTHLY("Monthly"), YEARLY("Yearly"), CUSTOM("Custom");

    /** The repeat to start from [start]: monthly on its day of the month, weekly on the weekday of the start, yearly on its date. Null for NEVER and CUSTOM. */
    fun toRepeat(start: LocalDate): Repeat? = when (this) {
        NEVER, CUSTOM -> null
        DAILY -> Repeat.Daily()
        WEEKDAYS -> Repeat.Weekly(days = WORKDAYS)
        WEEKLY -> Repeat.Weekly()
        MONTHLY -> Repeat.Monthly(rule = MonthlyRule.DayOfMonth(start.day))
        YEARLY -> Repeat.Yearly()
    }

    companion object {
        private val WORKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        val PICKABLE = listOf(NEVER, DAILY, WEEKDAYS, WEEKLY, MONTHLY, YEARLY)

        fun of(repeat: Repeat?): RepeatKind = when {
            repeat == null -> NEVER
            repeat is Repeat.Daily && repeat.every == 1 -> DAILY
            repeat is Repeat.Weekly && repeat.every == 1 && repeat.days == WORKDAYS -> WEEKDAYS
            repeat is Repeat.Weekly -> WEEKLY
            repeat is Repeat.Monthly && repeat.every == 1 -> MONTHLY
            repeat is Repeat.Yearly && repeat.every == 1 -> YEARLY
            else -> CUSTOM
        }
    }
}

/** "repeats weekly on Mon, Thu" for a repeating task, null for one that does not repeat. */
internal fun repeatsMarker(store: GameStore, task: Task): String? =
    store.seriesOf(task)?.recurrence?.let { "repeats " + it.repeat.words(it.start) }

/** The most of a shared text that is typed into quick add. */
private const val QUICK_ADD_SHARED_MAX = 200

/** Makes an open copy of [task] (same words, list and steps, nothing paid) and says so; hidden for a task another module owns. */
@Composable
private fun DuplicateButton(model: AppModel, task: Task) {
    if (task.link != null) return
    PixelButton("Duplicate", { if (model.store.duplicateTask(task.id) != null) model.say("Copy added.") }, color = Pixel.InkSoft, compact = true)
}

/** Add a task with just a title; the chips pick a day and "More options" adds difficulty. "Call mum tomorrow 5pm 30m" is understood too. */
@Composable
internal fun QuickAddBox(model: AppModel, addTo: AddTo, listId: String? = null) {
    val draftKey = "$addTo:${listId.orEmpty()}"
    var text by remember { mutableStateOf(model.quickAddDrafts[draftKey].orEmpty()) }
    // The unsent words outlive the screen: leaving for another tab and coming back finds them again.
    LaunchedEffect(text) { if (text.isEmpty()) model.quickAddDrafts.remove(draftKey) else model.quickAddDrafts[draftKey] = text }
    var rank by remember { mutableStateOf(Rank.C) }
    // The day chosen with a key; with none ([day] null and not [noDate]) the day is what the text says or the view's default.
    var day by remember { mutableStateOf<LocalDate?>(null) }
    var noDate by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(RepeatKind.NEVER) }
    val focus = remember { FocusRequester() }
    // "Add task" from a widget or the app icon's shortcut lands here with the keyboard open.
    LaunchedEffect(model.addTaskRequested) {
        if (model.addTaskRequested) {
            focus.requestFocus()
            model.addTaskRequested = false
        }
    }
    // Text shared from another app is typed in for the player to look at, change and add.
    LaunchedEffect(model.sharedText) {
        model.sharedText?.let { shared ->
            text = shared.trim().take(QUICK_ADD_SHARED_MAX)
            focus.requestFocus()
            model.sharedText = null
        }
    }
    var more by remember { mutableStateOf(false) }
    fun add() {
        if (text.isNotBlank()) {
            val parsed = QuickAdd.parse(text, model.store.today)
            val planned: LocalDate? = when {
                noDate -> null
                day != null -> day
                else -> parsed.planned ?: if (parsed.due == null && addTo == AddTo.TODAY && listId == null) model.store.today else null
            }
            val made = model.store.addTask(
                title = parsed.title.ifBlank { text.trim() }, rank = parsed.rank ?: rank,
                // With none in the words, the list's own category (Personal in the Inbox).
                category = parsed.category,
                planned = planned, due = parsed.due, estimateMinutes = parsed.estimateMinutes,
                // "#Groceries" files it in that list; inside a list, the list itself.
                listId = listId ?: parsed.listName?.let { model.store.listNamed(it)?.id },
                // "tomorrow 5pm" keeps its 5pm and "tonight" its evening; both only mean something on a planned day.
                plannedTime = parsed.plannedTime, evening = parsed.evening,
            )
            // A repeating task starts on its day (today when it has none) and the next ones are made ahead.
            repeat.toRepeat(made.planned ?: model.store.today)?.let { model.store.makeRepeating(made.id, it) }
            // Where it went: its list, else its day, else the Inbox.
            val filed = made.listId?.let { id -> model.data.lists.firstOrNull { it.id == id }?.name }
            model.say("Added to " + when {
                filed != null -> filed
                made.planned == model.store.today -> "Today"
                made.planned != null -> made.planned!!.short()
                else -> "Inbox"
            })
            text = ""; day = null; noDate = false; picking = false; more = false; repeat = RepeatKind.NEVER
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelField(
                    text, { text = it }, Modifier.weight(1f).focusRequester(focus), singleLine = true,
                    placeholder = "Buy milk tomorrow #Home",
                    // The keyboard's own key adds the task, so the thumb need not travel to the button.
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                )
                PixelIconButton("plus", "Add", ::add, ModuleAccent.PLAN.accent, enabled = text.isNotBlank())
            }
            // What the line was understood as, so a typed "tomorrow 5pm #Social !B" is never a guess (Tesler: the app does the parsing, and shows it).
            val parsed = QuickAdd.parse(text, model.store.today)
            val understood = QuickAdd.hints(if (day != null || noDate) parsed.copy(planned = null) else parsed, model.store.today)
            if (understood.isNotEmpty()) Text("Understood: " + understood.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            // The rest waits until there is something to add, so the box is one field and one button (cta.gallery, plnty).
            if (text.isBlank() && !more) return@Column
            // When: Plan's day keys (PlanDays); tap one again to go back to what the text says ("tomorrow 5pm") or the view's default.
            val today = model.store.today
            val keyed = PlanDays.presets(today).map { (label, ahead) -> label to today.plus(DatePeriod(days = ahead)) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                keyed.forEach { (label, d) ->
                    TabButton(label, day == d, { day = if (day == d) null else d; noDate = false; picking = false })
                }
                val other = day?.takeIf { d -> keyed.none { it.second == d } }
                TabButton(other?.short() ?: "Pick a day", other != null || picking, { picking = !picking })
                TabButton("No date", noDate, { noDate = !noDate; day = null; picking = false })
            }
            if (picking) {
                PixelDatePicker(
                    day ?: today, today, Pixel.Tone, { day = it; noDate = false; picking = false },
                    presets = emptyList(), max = null, mondayFirst = model.store.data.settings.weekStartsOnMonday, initiallyOpen = true,
                )
            }
            PixelButton(if (more) "Fewer options" else "More options", { more = !more }, color = Pixel.InkSoft, compact = true)
            if (more) {
                DifficultyPicker(rank) { rank = it }
                FieldLabel("Repeat")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    RepeatKind.PICKABLE.forEach { k -> TabButton(k.label, repeat == k, { repeat = k }) }
                }
            }
        }
    }
}

@Composable
internal fun TaskCard(model: AppModel, task: Task, day: LocalDate?, expanded: Boolean, toggle: () -> Unit, onDeleted: () -> Unit, showList: Boolean = true, startTab: CardTab = CardTab.WHEN) {
    val store = model.store
    val list = task.listId?.let { id -> model.data.lists.firstOrNull { it.id == id } }
    Column {
        Row(
            Modifier.fillMaxWidth()
                .clickable(onClickLabel = if (expanded) "Hide options" else "Show options", role = Role.Button, onClick = toggle)
                .defaultMinSize(minHeight = 64.dp)
                .padding(Spacing.M),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            // The check key always comes first, so every row lines up: a finished one is ticked in a quiet colour and reopens it.
            when (task.state) {
                TaskState.OPEN -> PixelIconButton("check", "Done", { model.completeTask(task.id) }, Pixel.Green)
                TaskState.WONT_DO -> PixelIconButton("close", "Reopen", { store.reopen(task.id) }, Pixel.InkSoft)
                else -> PixelIconButton("check", "Reopen", { store.reopen(task.id) }, Pixel.InkSoft)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Text(task.title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge, maxLines = 4, overflow = TextOverflow.Ellipsis, color = if (task.isOpen) Pixel.Ink else Pixel.InkSoft)
                    RankTag(task.rank)
                }
                // Overdue tasks sit under Today's calm "From before" heading, so the row needs no alarm of its own (docs/design.md, No shame).
                // A finished task's plan no longer matters: the Done list is grouped by the day it was finished.
                val line = if (task.isOpen) taskMeta(task, day, repeatsMarker(store, task)) else ""
                // Its list, by name and colour, as TickTick shows it in the smart lists (not inside the list itself).
                val shownList = list?.takeIf { showList }
                if (line.isNotEmpty() || shownList != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    if (shownList != null) {
                        Box(Modifier.size(8.dp).background(Color(shownList.color), PixelShape))
                        Text(shownList.name, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (line.isNotEmpty()) Text(if (shownList != null) "· $line" else line, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                }
            }
        }
        if (expanded) {
            // The open card is a sheet of three tabs instead of one long column of controls: When (where and when it goes), Steps (its notes and small steps) and
            // Edit (its words, difficulty and category, and the ways to be rid of it). A finished task has no tabs: only its notes and steps.
            var tab by remember(task.id) { mutableStateOf(startTab) }
            Box(Modifier.fillMaxWidth().background(Pixel.Shade.copy(alpha = 0.5f)).padding(Spacing.M)) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                    if (task.state != TaskState.OPEN) {
                        if (task.notes.isNotBlank() || task.subtasks.isNotEmpty()) TaskStepsTab(model, task)
                        // A finished task can be reopened or deleted (the undo bar brings a deleted one back for a while).
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                            PixelButton("Reopen", { store.reopen(task.id) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                            DuplicateButton(model, task)
                            PixelIconButton("trash", "Delete", { store.deleteTask(task.id); onDeleted() }, Pixel.InkSoft)
                        }
                    }
                    else {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            CardTab.entries.forEach { t -> TabButton(t.label, tab == t, { tab = t }, Modifier.weight(1f)) }
                        }
                        when (tab) {
                            CardTab.WHEN -> TaskWhenTab(model, task)
                            CardTab.STEPS -> TaskStepsTab(model, task)
                            CardTab.EDIT -> TaskEditTab(model, task, onDeleted = onDeleted)
                        }
                    }
                }
            }
        }
    }
}

internal enum class CardTab(val label: String) { WHEN("When"), STEPS("Steps"), EDIT("Edit") }

/**
 * When and where a task goes: the day and the list, then one "Time and repeat" row that says what is set and opens the time,
 * the reminders and the repeat (most tasks need none of them, so they stay folded).
 */
@Composable
private fun TaskWhenTab(model: AppModel, task: Task) {
    val store = model.store
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        FieldLabel("Move to")
        // Only the planned day goes with "No date"; a deadline is on the Edit tab and stays.
        PixelDateField(
            task.planned, store.today, Pixel.Tone,
            { day -> store.updateTask(task.copy(planned = day, plannedTime = if (day == null) null else task.plannedTime)) },
            presets = PlanDays.presets(store.today), noneLabel = "No date", pickLabel = "Pick a day",
            mondayFirst = store.data.settings.weekStartsOnMonday,
        )
        // Moving one day of a repeating task moves that day only (PLAN.md section 5.2): say so, and where it came from.
        task.instanceDay()?.takeIf { task.movedOccurrence }?.let { from ->
            Text("Moved from ${from.short()}: this one only.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
        if (store.activeLists.isNotEmpty()) {
            FieldLabel("List")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                TabButton("Inbox", task.listId == null, { store.moveToList(task.id, null) })
                store.activeLists.forEach { l -> TabButton(l.name, task.listId == l.id, { store.moveToList(task.id, l.id) }) }
            }
        }
        var more by remember(task.id) { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().clip(PixelShape)
                .clickable(onClickLabel = if (more) "Hide time and repeat" else "Change time and repeat", role = Role.Button) { more = !more }
                .defaultMinSize(minHeight = Spacing.Target).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Time and repeat", style = MaterialTheme.typography.bodyLarge)
                Text(timeAndRepeat(model, task), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
            Text(if (more) "Hide" else "Change", style = MaterialTheme.typography.labelLarge, color = Pixel.Tone)
        }
        if (more) {
            PlannedTime(model, task)
            RemindMe(model, task)
            RepeatRow(model, task)
        }
    }
}

/** What the folded "Time and repeat" row says: "Any time, no reminder, does not repeat" or "At 9:00, 1 reminder, repeats weekly". */
private fun timeAndRepeat(model: AppModel, task: Task): String = listOf(
    task.plannedTime?.let { "At ${clock(it)}" } ?: "Any time",
    when (task.reminders.size) { 0 -> "no reminder"; 1 -> "1 reminder"; else -> "${task.reminders.size} reminders" },
    repeatsMarker(model.store, task) ?: "does not repeat",
).joinToString(", ")

/** A task's difficulty as a small tag after its title: the letter in a thin frame, its meaning read out. */
@Composable
private fun RankTag(rank: Rank) {
    Box(
        Modifier.border(Sizes.Hairline, Pixel.Border, PixelShape).padding(horizontal = Spacing.XS).semantics { contentDescription = rank.spoken },
        contentAlignment = Alignment.Center,
    ) { Text(rank.letter.toString(), style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft) }
}

/** A task's notes and small steps; an open task also gets the field to add a step. */
@Composable
private fun TaskStepsTab(model: AppModel, task: Task) {
    val store = model.store
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        if (task.notes.isNotBlank()) Text(task.notes, style = MaterialTheme.typography.bodyLarge)
        var editing by remember(task.id) { mutableStateOf<String?>(null) }
        task.subtasks.forEach { sub ->
            if (editing == sub.id && task.state == TaskState.OPEN) {
                var text by remember(sub.id) { mutableStateOf(sub.title) }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    PixelField(text, { text = it }, Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                    PixelIconButton("check", "Save step", { store.renameSubtask(task.id, sub.id, text); editing = null }, Pixel.Green, enabled = text.isNotBlank())
                    PixelIconButton("trash", "Remove step", {
                        val before = task.subtasks
                        store.removeSubtask(task.id, sub.id); editing = null
                        model.offerUndo("Removed: ${sub.title}") { store.restoreSubtasks(task.id, before) }
                    }, Pixel.InkSoft)
                }
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                Text(sub.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = if (sub.done) Pixel.InkSoft else Pixel.Ink)
                if (task.state == TaskState.OPEN) {
                    PixelIconButton("pencil", "Edit step", { editing = sub.id }, Pixel.Tone)
                    if (sub.done) PixelIconButton("undo", "Untick step", { store.reopenSubtask(task.id, sub.id) }, Pixel.InkSoft)
                    else PixelButton("Step done", { store.completeSubtask(task.id, sub.id) }, color = Pixel.Tone, compact = true)
                }
            }
        }
        if (task.state == TaskState.OPEN && task.subtasks.size < GameStore.MAX_SUBTASKS) {
            var step by remember(task.id) { mutableStateOf("") }
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
    }
}

/** A task's words, difficulty, category, deadline and estimate, saved as they are made. Won't do and Delete are here, apart from the day-to-day tabs. */
@Composable
private fun TaskEditTab(model: AppModel, task: Task, onDeleted: () -> Unit) {
    val store = model.store
    var title by remember(task.id) { mutableStateOf(task.title) }
    var rank by remember(task.id) { mutableStateOf(task.rank) }
    var minutes by remember(task.id) { mutableStateOf(task.estimateMinutes ?: 0) }
    var category by remember(task.id) { mutableStateOf(task.category) }
    var notes by remember(task.id) { mutableStateOf(task.notes) }
    var dueDay by remember(task.id) { mutableStateOf(task.due?.date) }
    // A deadline keeps its time of day; with none typed it is the end of that day (23:59).
    var dueTime by remember(task.id) { mutableStateOf(task.due?.time?.takeIf { it != LocalTime(23, 59) }) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        PixelField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = "Title")
        PixelField(
            notes, { notes = it }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 6, placeholder = "Notes",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        DifficultyPicker(rank) { rank = it }
        FieldLabel("Category")
        CategoryChips(category) { category = it ?: category }
        GoalChoice(model, task)
        // A deadline is not the day you plan to do it (that is "Move to"); with no time it is the end of that day, 23:59.
        PixelDateTimeField(
            dueDay, dueTime, store.today, Pixel.Tone, { d, t -> dueDay = d; dueTime = t }, label = "Deadline",
            noDayLabel = "No deadline", timeNoneLabel = "End of day (23:59)", timePresets = TIME_PRESETS,
            dayPresets = PlanDays.presets(store.today), pickLabel = "Pick a day",
        )
        PixelDurationField(minutes.takeIf { it > 0 }, { minutes = it ?: 0 }, Pixel.Tone, label = "Estimate", noneLabel = "No estimate", presets = listOf(15, 30, 45, 60, 90))
        // Edits are saved as they are made (a short pause after typing, and when the tab closes), so nothing is lost by switching tabs.
        val commit by rememberUpdatedState {
            val live = store.data.tasks.firstOrNull { it.id == task.id }
            if (live != null && live.state == TaskState.OPEN) {
                val next = live.copy(
                    title = title.trim().ifEmpty { live.title }, rank = rank, estimateMinutes = minutes.takeIf { it > 0 },
                    category = category, notes = notes.trim(),
                    due = dueDay?.let { LocalDateTime(it, dueTime ?: LocalTime(23, 59)) },
                )
                if (next != live) store.updateTask(next)
            }
        }
        LaunchedEffect(title, notes, rank, minutes, category, dueDay, dueTime) { kotlinx.coroutines.delay(600); commit() }
        DisposableEffect(task.id) { onDispose { commit() } }
        HorizontalDivider(color = Pixel.Border)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            PixelButton("Won't do", { store.wontDo(task.id); model.offerUndo("Won't do: ${task.title}") { store.reopen(task.id) } }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            DuplicateButton(model, task)
            PixelIconButton("trash", "Delete", { store.deleteTask(task.id); onDeleted() }, Pixel.InkSoft)
        }
    }
}

/** The goal a task counts toward (PLAN.md section 3.6): None or one of the goals not yet finished. Linking pays nothing. */
@Composable
private fun GoalChoice(model: AppModel, task: Task) {
    val store = model.store
    val linked = store.goalOf(task.id)
    val goals = model.data.goals.filter { it.completedAtMillis == null || it.id == linked?.id }
    if (goals.isEmpty()) return
    FieldLabel("Goal")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        TabButton("None", linked == null, { store.linkTaskToGoal(task.id, null) })
        goals.forEach { g -> TabButton(g.title, linked?.id == g.id, { store.linkTaskToGoal(task.id, g.id) }) }
    }
}

/**
 * Whether and how the task repeats: the common ways, or Never to stop (the days ahead that are still untouched go). Weekly then
 * takes how many weeks apart (every 1 to 4) and which weekdays; one that came from elsewhere (every 3 days) is said in words.
 */
@Composable
internal fun RepeatRow(model: AppModel, task: Task) {
    val store = model.store
    val series = store.seriesOf(task)
    val recurrence = series?.recurrence
    val kind = RepeatKind.of(recurrence?.repeat)
    FieldLabel("Repeat")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        RepeatKind.PICKABLE.forEach { k ->
            TabButton(k.label, kind == k, {
                if (k == kind) return@TabButton
                if (k == RepeatKind.NEVER) store.stopRepeating(task.id)
                else k.toRepeat(recurrence?.start ?: task.planned ?: store.today)?.let { store.makeRepeating(task.id, it) }
            })
        }
    }
    if (kind == RepeatKind.CUSTOM && recurrence != null) {
        Text("Repeats ${recurrence.repeat.words(recurrence.start)}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    }
    val weekly = recurrence?.repeat as? Repeat.Weekly ?: return
    if (kind != RepeatKind.WEEKLY) return
    FieldLabel("Every")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        (1..4).forEach { n ->
            TabButton(if (n == 1) "Week" else "$n weeks", weekly.every == n, { if (weekly.every != n) store.makeRepeating(task.id, weekly.copy(every = n)) })
        }
    }
    FieldLabel("On")
    val on = weekly.days.ifEmpty { setOf(recurrence.start.dayOfWeek) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        PlanDays.week(store.data.settings.weekStart).forEach { d ->
            val name = dayNames[d.ordinal]
            // At least one day stays chosen; tapping the last one does nothing.
            TabButton(name.take(2), d in on, {
                val next = if (d in on) on - d else on + d
                if (next.isNotEmpty()) store.makeRepeating(task.id, weekly.copy(days = next))
            }, Modifier.semantics { contentDescription = fullDayName(d) })
        }
    }
}

private fun fullDayName(d: DayOfWeek) = d.name.lowercase().replaceFirstChar { it.uppercase() }

/** The time of day a task is planned for: typed, a few common times as keys, and small steps. */
@Composable
private fun PlannedTime(model: AppModel, task: Task) {
    PixelTimeField(
        task.plannedTime, { model.store.setPlannedTime(task.id, it) }, Pixel.Tone, label = "At",
        presets = TIME_PRESETS, noneLabel = "Any time",
    )
}

private val TIME_PRESETS = listOf(LocalTime(9, 0), LocalTime(12, 0), LocalTime(15, 0), LocalTime(19, 0))

fun clock(t: LocalTime) = "${t.hour}:${t.minute.toString().padStart(2, '0')}"

/** Moves [t] by [minutes] within the same day (it stops at 0:00 and 23:45). */
internal fun shifted(t: LocalTime, minutes: Int): LocalTime {
    val total = (t.hour * 60 + t.minute + minutes).coerceIn(0, 23 * 60 + 45)
    return LocalTime(total / 60, total % 60)
}

/**
 * Up to three reminders, counted from the planned time or the deadline; a task without a day has nothing to count from. The
 * common offsets are keys, "Other" takes a typed length before (up to a week), and one typed earlier shows as its own key.
 */
@Composable
internal fun RemindMe(model: AppModel, task: Task) {
    FieldLabel("Remind me")
    val base = Reminders.baseTime(task)
    if (base == null) {
        Text("Give it a day first.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        return
    }
    val room = task.reminders.size < Reminders.MAX_PER_TASK
    var typing by remember(task.id) { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        (ReminderOffset.PRESETS + task.reminders).distinct().forEach { offset ->
            val on = offset in task.reminders
            TabButton(offset.label, on, {
                if (on) model.store.setReminders(task.id, task.reminders - offset)
                else if (room) model.store.setReminders(task.id, task.reminders + offset)
            }, enabled = on || room)
        }
        if (room) TabButton("Other", typing, { typing = !typing })
    }
    if (typing && room) {
        var minutes by remember(task.id) { mutableStateOf<Int?>(null) }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            PixelDurationField(minutes, { minutes = it }, Pixel.Tone, Modifier.weight(1f), presets = emptyList(), max = ReminderOffset.MAX_BEFORE_MINUTES)
            PixelButton("Add", {
                val offset = minutes?.let { ReminderOffset.Before(it) }
                if (offset != null && offset !in task.reminders) model.store.setReminders(task.id, task.reminders + offset)
                minutes = null; typing = false
            }, color = Pixel.Tone, compact = true, enabled = minutes != null)
        }
    }
    if (!model.store.data.settings.reminders) Text("Reminders are off in Settings.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
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
    // A projection of a Home chore: Home completes it and pays it, Plan only shows it (PRODUCT_BIBLE.md section 4).
    if (t.ownedElsewhere) add("from Base")
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
