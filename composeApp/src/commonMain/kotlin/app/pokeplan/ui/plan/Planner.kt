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
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.screens.RecordRow
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
import app.pokeplan.ui.screens.LocalBuddy
import app.pokeplan.ui.screens.LocalReduceMotion
import app.pokeplan.ui.screens.NextUp
import app.pokeplan.ui.screens.PixelDateField
import app.pokeplan.ui.screens.TypedValueField
import app.pokeplan.ui.screens.UpTo
import app.pokeplan.ui.screens.PixelDatePicker
import app.pokeplan.ui.screens.PixelDateTimeField
import app.pokeplan.ui.screens.PixelDurationField
import app.pokeplan.ui.screens.PixelTimeField
import app.pokeplan.ui.screens.PlanSection
import app.pokeplan.ui.screens.ScreenSwitch
import app.pokeplan.ui.screens.SectionScaffold
import app.pokeplan.ui.screens.TabButton
import app.pokeplan.ui.screens.PixelDropdown
import app.pokeplan.ui.screens.FormDisclosure
import app.pokeplan.ui.screens.FormGroup
import app.pokeplan.ui.screens.FormStack
import app.pokeplan.ui.screens.PixelChoice
import app.pokeplan.ui.screens.TaskTitleLine
import app.pokeplan.ui.screens.dayNames
import app.pokeplan.ui.screens.label
import app.pokeplan.ui.screens.long
import app.pokeplan.ui.screens.short

internal enum class AddTo { TODAY, NO_DATE }

@androidx.compose.runtime.Stable
internal class CardHost { var expanded by mutableStateOf<String?>(null) }

internal fun taskCards(model: AppModel, host: CardHost, showList: Boolean = true): @Composable (Task, LocalDate?) -> Unit = { t, day ->
    TaskCard(model, t, day, host.expanded == t.id, { host.expanded = if (host.expanded == t.id) null else t.id }, onDeleted = { model.deletedTask = t; host.expanded = null }, showList = showList)
}

internal fun planTasks(model: AppModel): List<Task> {
    val pending = model.data.pending.map { it.original.id }.toSet()
    return model.data.tasks.filter { it.state != TaskState.DELETED && it.id !in pending }
}

@Composable
internal fun PlanView(model: AppModel, section: PlanSection, onBack: () -> Unit, open: (PlanSection) -> Unit, onFocus: ((Task) -> Unit)?, startWeek: Boolean = false) =
    app.pokeplan.ui.theme.ModuleTone(ModuleAccent.PLAN) { PlanViewBody(model, section, onBack, open, onFocus, startWeek) }

@Composable
private fun PlanViewBody(model: AppModel, section: PlanSection, onBack: () -> Unit, open: (PlanSection) -> Unit, onFocus: ((Task) -> Unit)?, startWeek: Boolean) {
    val pendingIds = model.data.pending.map { it.original.id }.toSet()
        SectionScaffold(section.label, null, onBack = onBack, icon = section.icon, color = ModuleAccent.PLAN.header, trailing = null) {
        when (section) {
            PlanSection.CALENDAR -> DayView(model, pendingIds, startWeek)
            PlanSection.TASKS -> TasksWorkspace(model, onFocus)
            PlanSection.SETTINGS -> app.pokeplan.ui.screens.PlanSettings(model)
            else -> {}
        }
    }
}

private fun nextTask(model: AppModel, tasks: List<Task>, late: Set<Task>): Task? =
    tasks.firstOrNull { it.id == model.data.pickedTaskId } ?: tasks.firstOrNull { it !in late && !TaskViews.isEvening(it) } ?: tasks.firstOrNull { it !in late } ?: tasks.firstOrNull()

@Composable
internal fun PlanOverview(model: AppModel) {
    val store = model.store
    val tasks = store.openTodayTasks()
    val done = store.doneToday()
    val late = TaskViews.overdue(tasks, store.now).toSet()
    val openToday = { model.tasksView = TasksView.TODAY; model.planSection = PlanSection.TASKS }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            if (tasks.isEmpty()) {
                PixelTitle(if (done > 0) "All clear for today" else "Nothing planned for today")
                if (done == 0) PixelButton("Add a task", { model.addTaskRequested = true }, Modifier.fillMaxWidth(), compact = true)
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                    Box(Modifier.weight(1f)) { PixelTitle("Today") }
                    Text("$done of ${tasks.size + done} done", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                }
                val next = nextTask(model, tasks, late)
                val rest = tasks.filter { it.id != next?.id }
                if (rest.isEmpty()) app.pokeplan.ui.theme.PixelBar(done.toFloat() / (tasks.size + done), label = "$done of ${tasks.size + done} done")
                rest.take(5).forEach { t ->
                    RecordRow(
                        t.title, if (t in late) "From before" else "", warn = t in late,
                        lead = { PixelIconButton("check", "Mark ${t.title} done", { model.completeTask(t.id) }, Pixel.Green) },
                        onClick = openToday, onClickLabel = "Open today's tasks",
                    )
                }
                if (rest.size > 5) PixelButton("All ${tasks.size} tasks", openToday, color = Pixel.InkSoft, compact = true)
            }
        }
    }
}

@Composable
internal fun PlanActionItems(model: AppModel, onFocus: ((Task) -> Unit)?) {
    val store = model.store
    val data = model.data
    QuickAddBox(model, AddTo.NO_DATE, titled = true)
    val tasks = store.openTodayTasks()
    val late = TaskViews.overdue(tasks, store.now).toSet()
    val next = nextTask(model, tasks, late)
    if (next != null) NextUp(
        task = next,
        onDone = { model.completeTask(next.id) },
        onFocus = onFocus?.let { f -> { f(next) } },
        onChoose = if (tasks.size > 1 || data.pickedTaskId != next.id) ({ store.pickForMe() }) else null,
        picked = next.id == data.pickedTaskId,
        day = store.today,
    )
}

@Composable
internal fun TodayList(model: AppModel, onFocus: ((Task) -> Unit)?, listId: String? = null) {
    val store = model.store
    val data = model.data
    val host = remember { CardHost() }
    // An open task card closes before the page does.
    host.expanded?.let { UpTo { host.expanded = null } }
    val card = taskCards(model, host)
    val tasks = store.openTodayTasks().filter { listId == null || it.listId == listId }
    val done = if (listId == null) store.doneToday()
    else data.tasks.count { it.listId == listId && it.state == TaskState.DONE && (it.completedAtMillis ?: 0L) >= store.startOfTodayMillis }
    if (tasks.isNotEmpty() || done > 0) {
        val total = tasks.size + done
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Text("$done of $total done", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            app.pokeplan.ui.theme.PixelBar(done.toFloat() / total, Modifier.weight(1f), label = "$done of $total done")
        }
    }
    if (tasks.isEmpty()) {
        if (done > 0) EmptyState("All clear for today", icon = "oaks-parcel", sleeper = true, action = "Add another" to { model.addTaskRequested = true })
        else EmptyState("Nothing planned for today", icon = "oaks-parcel", sleeper = true, action = "Add a task" to { model.addTaskRequested = true })
    }
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
    val movable = late.filter { it.due == null }
    if (before.isNotEmpty()) TaskGroup(
        "From before", before.toList(), color = Pixel.Warning,
        action = if (movable.isEmpty()) null else "Move to today" to { movable.forEach { store.updateTask(it.copy(planned = store.today)) } },
        card = card,
    )
    if (day.isNotEmpty()) TaskGroup(if (before.isEmpty() && evening.isEmpty()) null else "Today", day, dayOf = store.today, card = card)
    if (evening.isNotEmpty()) TaskGroup("This evening", evening, dayOf = store.today, card = card)
    if (done > 0) {
        var showDone by remember { mutableStateOf(false) }
        PixelButton(if (showDone) "Hide done" else "Done today ($done)", { showDone = !showDone }, color = Pixel.InkSoft, compact = true)
        if (showDone) TaskGroup(null, data.tasks.filter { it.state == TaskState.DONE && (it.completedAtMillis ?: 0L) >= store.startOfTodayMillis }.sortedByDescending { it.completedAtMillis }, card = card)
    }
}

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

@Composable
fun EmptyState(
    title: String, icon: String? = null, action: Pair<String, () -> Unit>? = null, sleeper: Boolean = false,
    addAbove: String? = null,
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val action = action ?: addAbove?.let { it to { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Up); Unit } }
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



@Composable
fun DifficultyPicker(selected: Rank, onSelect: (Rank) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        FieldLabel("How hard does it feel?")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Rank.entries.forEach { r -> RankBadge(r, selected = r == selected, onClick = { onSelect(r) }) }
        }
    }
}

internal enum class RepeatKind(val label: String) {
    NEVER("Never"), DAILY("Daily"), WEEKDAYS("Weekdays"), WEEKLY("Weekly"), MONTHLY("Monthly"), YEARLY("Yearly"), CUSTOM("Custom");

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
            repeat is Repeat.Monthly && (repeat.rule is MonthlyRule.DayOfMonth || repeat.rule is MonthlyRule.LastDay) -> MONTHLY
            repeat is Repeat.Yearly && repeat.every == 1 -> YEARLY
            else -> CUSTOM
        }
    }
}

internal fun repeatsMarker(store: GameStore, task: Task): String? =
    store.seriesOf(task)?.recurrence?.let { "repeats " + it.repeat.words(it.start) }

private const val QUICK_ADD_SHARED_MAX = 200

@Composable
private fun DuplicateButton(model: AppModel, task: Task) {
    if (task.link != null) return
    PixelButton("Duplicate", { if (model.store.duplicateTask(task.id) != null) model.say("Copy added.") }, color = Pixel.InkSoft, compact = true)
}

@Composable
internal fun QuickAddBox(model: AppModel, addTo: AddTo, listId: String? = null, titled: Boolean = false) {
    val draftKey = "$addTo:${listId.orEmpty()}"
    var text by remember { mutableStateOf(model.quickAddDrafts[draftKey].orEmpty()) }
    LaunchedEffect(text) { if (text.isEmpty()) model.quickAddDrafts.remove(draftKey) else model.quickAddDrafts[draftKey] = text }
    var rank by remember { mutableStateOf(Rank.C) }
    val defaultReward = model.data.settings.defaultReward
    var reward by remember(defaultReward) { mutableStateOf(defaultReward) }
    var day by remember { mutableStateOf<LocalDate?>(null) }
    var noDate by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(RepeatKind.NEVER) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(model.addTaskRequested) {
        if (model.addTaskRequested) {
            focus.requestFocus()
            model.addTaskRequested = false
        }
    }
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
                else -> parsed.planned ?: if (parsed.due == null && addTo == AddTo.TODAY) model.store.today else null
            }
            val made = model.store.addTask(
                title = parsed.title.ifBlank { text.trim() }, rank = parsed.rank ?: rank,
                planned = planned, due = parsed.due, estimateMinutes = parsed.estimateMinutes, reward = reward,
                listId = listId ?: parsed.listName?.let { model.store.listNamed(it)?.id },
                plannedTime = parsed.plannedTime, evening = parsed.evening,
            )
            repeat.toRepeat(made.planned ?: model.store.today)?.let { model.store.makeRepeating(made.id, it) }
            val filed = made.listId?.let { id -> model.data.lists.firstOrNull { it.id == id }?.name }
            model.say("Added to " + when {
                filed != null -> filed
                made.planned == model.store.today -> "Today"
                made.planned != null -> made.planned!!.short()
                else -> "Tasks"
            })
            text = ""; day = null; noDate = false; picking = false; more = false; repeat = RepeatKind.NEVER; reward = defaultReward
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            if (titled) PixelTitle("Quick actions")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelField(
                    text, { text = it }, Modifier.weight(1f).focusRequester(focus), singleLine = true,
                    placeholder = "Add a task (date, #list)",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                )
                PixelIconButton("plus", "Add", ::add, ModuleAccent.PLAN.accent, enabled = text.isNotBlank())
            }
            val parsed = QuickAdd.parse(text, model.store.today)
            val understood = QuickAdd.hints(if (day != null || noDate) parsed.copy(planned = null) else parsed, model.store.today)
            if (understood.isNotEmpty()) Text("Understood: " + understood.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            if (text.isBlank() && !more) return@Column
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
                PixelChoice(RepeatKind.PICKABLE.toList(), repeat, { it?.label ?: "" }, { k -> if (k != null) repeat = k }, label = "Repeat")
                PixelChoice(app.pokeplan.core.reward.RewardFocus.entries.toList(), reward, { it?.label ?: "" }, { r -> if (r != null) reward = r }, label = "Reward")
            }
        }
    }
}

@Composable
internal fun TaskCard(model: AppModel, task: Task, day: LocalDate?, expanded: Boolean, toggle: () -> Unit, onDeleted: () -> Unit, showList: Boolean = true, startTab: CardTab = CardTab.SCHEDULE) {
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
                val line = if (task.isOpen) taskMeta(task, day, repeatsMarker(store, task)) else ""
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
            var tab by remember(task.id) { mutableStateOf(startTab) }
            Box(Modifier.fillMaxWidth().background(Pixel.Shade.copy(alpha = 0.5f)).padding(Spacing.M)) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                    if (task.state != TaskState.OPEN) {
                        if (task.notes.isNotBlank() || task.subtasks.isNotEmpty()) TaskStepsTab(model, task)
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
                            CardTab.SCHEDULE -> TaskWhenTab(model, task)
                            CardTab.DETAILS -> TaskEditTab(model, task, more = false, onDeleted = onDeleted)
                            CardTab.MORE -> TaskEditTab(model, task, more = true, onDeleted = onDeleted)
                        }
                    }
                }
            }
        }
    }
}

internal enum class CardTab(val label: String) { SCHEDULE("Schedule"), DETAILS("Details"), MORE("More") }

@Composable
private fun TaskWhenTab(model: AppModel, task: Task) {
    val store = model.store
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        FieldLabel("Move to")
        PixelDateField(
            task.planned, store.today, Pixel.Tone,
            { day -> store.updateTask(task.copy(planned = day, plannedTime = if (day == null) null else task.plannedTime)) },
            presets = PlanDays.presets(store.today), noneLabel = "No date", pickLabel = "Pick a day",
            mondayFirst = store.data.settings.weekStartsOnMonday,
        )
        task.instanceDay()?.takeIf { task.movedOccurrence }?.let { from ->
            Text("Moved from ${from.short()}: this one only.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
        PlannedTime(model, task)
        RepeatRow(model, task)
        RemindMe(model, task)
    }
}

@Composable
private fun RankTag(rank: Rank) {
    Box(
        Modifier.border(Sizes.Hairline, Pixel.Border, PixelShape).padding(horizontal = Spacing.XS).semantics { contentDescription = rank.spoken },
        contentAlignment = Alignment.Center,
    ) { Text(rank.letter.toString(), style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft) }
}

@Composable
private fun TaskStepsTab(model: AppModel, task: Task) {
    val store = model.store
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
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

@Composable
private fun TaskEditTab(model: AppModel, task: Task, more: Boolean, onDeleted: () -> Unit) {
    val store = model.store
    var title by remember(task.id) { mutableStateOf(task.title) }
    var rank by remember(task.id) { mutableStateOf(task.rank) }
    var minutes by remember(task.id) { mutableStateOf(task.estimateMinutes ?: 0) }
    var notes by remember(task.id) { mutableStateOf(task.notes) }
    var reward by remember(task.id) { mutableStateOf(task.reward) }
    var dueDay by remember(task.id) { mutableStateOf(task.due?.date) }
    var dueTime by remember(task.id) { mutableStateOf(task.due?.time?.takeIf { it != LocalTime(23, 59) }) }
    FormStack {
        if (!more) {
            FormGroup {
                PixelField(title, { title = it }, Modifier.fillMaxWidth(), label = "Title", singleLine = true, placeholder = "Title")
                PixelField(
                    notes, { notes = it }, Modifier.fillMaxWidth(), label = "Notes", minLines = 2, maxLines = 6, placeholder = "Notes",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                PixelDurationField(minutes.takeIf { it > 0 }, { minutes = it ?: 0 }, Pixel.Tone, label = "Estimate", noneLabel = "No estimate", presets = listOf(15, 30, 45, 60, 90))
                if (store.activeLists.isNotEmpty()) {
                    PixelDropdown(
                        listOf<String?>(null) + store.activeLists.map { it.id }, task.listId,
                        { id -> if (id == null) "No list" else store.activeLists.firstOrNull { it.id == id }?.name ?: "" },
                        { store.moveToList(task.id, it) }, Modifier.fillMaxWidth(), label = "List",
                    )
                }
            }
            FieldLabel("Steps")
            TaskStepsTab(model, task)
        } else {
            FormGroup {
                DifficultyPicker(rank) { rank = it }
                PixelChoice(app.pokeplan.core.reward.RewardFocus.entries.toList(), reward, { it?.label ?: "" }, { r -> if (r != null) reward = r }, label = "Reward")
                GoalChoice(model, task)
                PixelDateTimeField(
                    dueDay, dueTime, store.today, Pixel.Tone, { d, t -> dueDay = d; dueTime = t }, label = "Deadline",
                    noDayLabel = "No deadline", timeNoneLabel = "End of day (23:59)", timePresets = TIME_PRESETS,
                    dayPresets = PlanDays.presets(store.today), pickLabel = "Pick a day",
                )
            }
        }
        val commit by rememberUpdatedState {
            val live = store.data.tasks.firstOrNull { it.id == task.id }
            if (live != null && live.state == TaskState.OPEN) {
                val next = live.copy(
                    title = title.trim().ifEmpty { live.title }, rank = rank, estimateMinutes = minutes.takeIf { it > 0 },
                    notes = notes.trim(), reward = reward,
                    due = dueDay?.let { LocalDateTime(it, dueTime ?: LocalTime(23, 59)) },
                )
                if (next != live) store.updateTask(next)
            }
        }
        LaunchedEffect(title, notes, rank, minutes, dueDay, dueTime, reward) { kotlinx.coroutines.delay(600); commit() }
        DisposableEffect(task.id) { onDispose { commit() } }
        if (more) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            PixelButton("Won't do", { store.wontDo(task.id); model.offerUndo("Won't do: ${task.title}") { store.reopen(task.id) } }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            DuplicateButton(model, task)
            PixelIconButton("trash", "Delete", { store.deleteTask(task.id); onDeleted() }, Pixel.InkSoft)
        }
    }
}

@Composable
private fun GoalChoice(model: AppModel, task: Task) {
    val store = model.store
    val linked = store.goalOf(task.id)
    val goals = model.data.goals.filter { it.completedAtMillis == null || it.id == linked?.id }
    if (goals.isEmpty()) return
    PixelDropdown(
        listOf<String?>(null) + goals.map { it.id }, linked?.id,
        { id -> if (id == null) "None" else goals.firstOrNull { it.id == id }?.title ?: "" },
        { store.linkTaskToGoal(task.id, it) }, Modifier.fillMaxWidth(), label = "Goal",
    )
}

@Composable
internal fun RepeatRow(model: AppModel, task: Task) {
    val store = model.store
    val series = store.seriesOf(task)
    val recurrence = series?.recurrence
    val kind = RepeatKind.of(recurrence?.repeat)
    PixelChoice(RepeatKind.PICKABLE.toList(), kind, { it?.label ?: "" }, { k ->
        if (k != null && k != kind) {
            if (k == RepeatKind.NEVER) store.stopRepeating(task.id)
            else k.toRepeat(recurrence?.start ?: task.planned ?: store.today)?.let { store.makeRepeating(task.id, it) }
        }
    }, label = "Repeat")
    if (kind == RepeatKind.CUSTOM && recurrence != null) {
        Text("Repeats ${recurrence.repeat.words(recurrence.start)}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    }
    val monthly = recurrence?.repeat as? Repeat.Monthly
    if (kind == RepeatKind.MONTHLY && monthly != null) {
        MonthlyPicker(monthly) { store.makeRepeating(task.id, it) }
        return
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
            TabButton(name.take(2), d in on, {
                val next = if (d in on) on - d else on + d
                if (next.isNotEmpty()) store.makeRepeating(task.id, weekly.copy(days = next))
            }, Modifier.semantics { contentDescription = fullDayName(d) })
        }
    }
}

@Composable
internal fun MonthlyPicker(monthly: Repeat.Monthly, onChange: (Repeat.Monthly) -> Unit) {
    FieldLabel("Every")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        listOf(1, 2, 3, 6).forEach { n ->
            TabButton(if (n == 1) "Month" else "$n months", monthly.every == n, { if (monthly.every != n) onChange(monthly.copy(every = n)) })
        }
    }
    FieldLabel("On")
    val day = (monthly.rule as? MonthlyRule.DayOfMonth)?.day
    TypedValueField(
        day, { d -> if (d != null) onChange(monthly.copy(rule = MonthlyRule.DayOfMonth(d))) },
        { it.trim().toIntOrNull() }, { it.toString() },
        placeholder = "Day of the month", takes = "A day from 1 to 31",
        reject = { if (it !in 1..31) "A day from 1 to 31" else null },
    )
    TabButton("Last day", monthly.rule is MonthlyRule.LastDay, { if (monthly.rule !is MonthlyRule.LastDay) onChange(monthly.copy(rule = MonthlyRule.LastDay)) })
}

private fun fullDayName(d: DayOfWeek) = d.name.lowercase().replaceFirstChar { it.uppercase() }

@Composable
private fun PlannedTime(model: AppModel, task: Task) {
    PixelTimeField(
        task.plannedTime, { model.store.setPlannedTime(task.id, it) }, Pixel.Tone, label = "At",
        presets = TIME_PRESETS, noneLabel = "Any time",
    )
}

private val TIME_PRESETS = listOf(LocalTime(9, 0), LocalTime(12, 0), LocalTime(15, 0), LocalTime(19, 0))

fun clock(t: LocalTime) = "${t.hour}:${t.minute.toString().padStart(2, '0')}"

internal fun shifted(t: LocalTime, minutes: Int): LocalTime {
    val total = (t.hour * 60 + t.minute + minutes).coerceIn(0, 23 * 60 + 45)
    return LocalTime(total / 60, total % 60)
}

@Composable
internal fun RemindMe(model: AppModel, task: Task) {
    FieldLabel("Remind me")
    val base = Reminders.baseTime(task)
    if (base == null) {
        val store = model.store
        PixelDateTimeField(
            task.nudge?.date, task.nudge?.time, store.today, Pixel.Tone,
            { d, t -> store.updateTask(task.copy(nudge = d?.let { LocalDateTime(it, t ?: Reminders.DEFAULT_TIME) })) }, label = "At",
            noDayLabel = "No reminder", timeNoneLabel = "9:00", timePresets = TIME_PRESETS,
            dayPresets = PlanDays.presets(store.today), pickLabel = "Pick a day",
        )
        if (!store.data.settings.reminders) Text("Reminders are off in Plan's settings.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
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
    if (!model.store.data.settings.reminders) Text("Reminders are off in Plan's settings.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
}

fun taskMeta(t: Task, day: LocalDate? = null, repeats: String? = null): String = buildList {
    t.due?.let { add(listOfNotNull("due", it.date.takeIf { d -> d != day }?.short(), clock(it.time)).joinToString(" ")) }
        ?: t.planned?.let { p -> listOfNotNull(p.takeIf { it != day }?.short(), t.plannedTime?.let(::clock)).joinToString(" ").takeIf { it.isNotEmpty() }?.let(::add) }
    when {
        t.focusedMinutes > 0 && t.estimateMinutes != null -> add("focused ${t.focusedMinutes} of ${t.estimateMinutes} min")
        t.focusedMinutes > 0 -> add("focused ${t.focusedMinutes} min")
        else -> t.estimateMinutes?.let { add("$it min") }
    }
    if ((t.reminders.isNotEmpty() || t.nudge != null) && t.isOpen) add("reminder")
    if (t.ownedElsewhere) add("from Base")
    if (t.subtasks.isNotEmpty()) add("${t.subtasks.count { it.done }}/${t.subtasks.size} steps")
    if (repeats != null) add(repeats)
}.joinToString(", ")

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
