package app.pokeplan.ui.screens

import app.pokeplan.ui.theme.PixelShape
import app.pokeplan.ui.theme.Spacing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import app.pokeplan.ui.theme.FieldLabel
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.pokeplan.ui.theme.ToggleRow
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pokeplan.plan.Category
import app.pokeplan.plan.Insights
import app.pokeplan.plan.Goal
import app.pokeplan.plan.GoalRules
import androidx.compose.runtime.mutableStateMapOf
import app.pokeplan.plan.Reviews
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.TextBox
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** The ranges the balance wheel can show (design.md, Growth). */
private val growthRanges = listOf(7, 30, 90)

@Composable
internal fun GrowthView(model: AppModel) {
    var days by remember { mutableStateOf(7) }
    val wheel = model.store.wheel(days)
    val trend = model.store.trend(6)
    val weekStart = if (model.data.settings.weekStartsOnMonday) "Monday" else "Sunday"

    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        growthRanges.forEach { d -> TabButton("$d days", d == days, { days = d }, Modifier.weight(1f)) }
    }
    Insights.estimateAccuracy(model.data.tasks)?.let { acc ->
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                PixelTitle("Your estimates")
                val pct = (acc.averageRatio * 100).roundToInt()
                Text(
                    when (acc.bias) {
                        Insights.Bias.UNDERESTIMATES -> "Tasks take about $pct% of what you guess. Adding a little time to estimates may help."
                        Insights.Bias.OVERESTIMATES -> "Tasks take about $pct% of what you guess: you're quicker than you think."
                        Insights.Bias.ABOUT_RIGHT -> "Your guesses are about right ($pct% of the estimate)."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text("From ${acc.samples} finished tasks with an estimate and focus time, all time.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Where your effort went")
            Text(
                "The last $days days, today included: tasks finished and minutes focused in each area. The share counts " +
                    "${Insights.MINUTES_PER_TASK_POINT} minutes of focus as one task; focus without a task counts as Personal.",
                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
            )
            // Only the areas with something in them get a row; the quiet ones are named on one line.
            val (busy, quiet) = Category.entries.partition { wheel.score(it) > 0 }
            if (busy.isEmpty()) Text("Nothing finished in the last $days days yet.", style = MaterialTheme.typography.bodyLarge)
            busy.forEach { c ->
                val tasks = wheel.tasksDone[c] ?: 0
                val minutes = wheel.focusMinutes[c] ?: 0
                val share = (wheel.share(c) * 100).roundToInt()
                Column(Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "${c.label}: ${plural(tasks, "task")}, $minutes minutes focused, $share percent of your effort"
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Text(c.label, Modifier.width(84.dp), style = MaterialTheme.typography.bodyMedium)
                        Box(Modifier.weight(1f)) { PixelBar(wheel.share(c).toFloat(), color = categoryColor(c)) }
                        Text("$share%", Modifier.width(44.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("${plural(tasks, "task")}, $minutes min", Modifier.padding(start = 92.dp), style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft)
                }
            }
            if (busy.isNotEmpty() && quiet.isNotEmpty()) {
                Text("Nothing yet in ${quiet.joinToString { it.label }}.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
        }
    }
    WeekBars(
        "Tasks finished per week", "Tasks ticked off in each of the last 6 weeks (weeks start on $weekStart).",
        trend, { it.tasksDone }, { it.toString() }, Pixel.Tone,
    )
    WeekBars(
        "Focus per week", "Minutes from finished sessions only; a session that went cold doesn't count.",
        trend, { it.focusMinutes }, { if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m" }, Pixel.Green,
    )
    GoalsSummary(model)
}

/** A small bar chart of the last weeks, with the number above each bar and the week's first day below it. */
@Composable
private fun WeekBars(
    title: String, note: String, weeks: List<Insights.WeekPoint>, value: (Insights.WeekPoint) -> Int, label: (Int) -> String, color: Color,
) {
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(note, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            val max = (weeks.maxOfOrNull(value) ?: 0).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.Bottom) {
                weeks.forEachIndexed { i, w ->
                    val v = value(w)
                    val week = if (i == weeks.lastIndex) "this week" else "week of ${w.weekStart.dayMonth()}"
                    Column(
                        Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "$title, $week: ${label(v)}" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(label(v), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        Box(Modifier.fillMaxWidth().height((80 * v / max + 4).dp).background(color, PixelShape))
                        Text(if (i == weeks.lastIndex) "now" else w.weekStart.dayMonth(), style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Goal progress, kept apart from tasks and time: a goal moves only when its milestones (or linked tasks) are done. */
@Composable
private fun GoalsSummary(model: AppModel) {
    val goals = model.data.goals
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Goal progress")
            if (goals.isEmpty()) {
                Text("No goals yet. Add one under Plan, Goals.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                return@Column
            }
            Text(
                "${goals.count { it.completedAtMillis != null }} of ${plural(goals.size, "goal")} reached. Progress is milestones done (or linked tasks, for a goal without milestones).",
                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
            )
            goals.filter { it.completedAtMillis == null }.take(5).forEach { g ->
                val pct = (GoalRules.progress(g, model.data.tasks) * 100).roundToInt()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Text(g.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Box(Modifier.width(96.dp)) { PixelBar(pct / 100f, color = Pixel.Warning) }
                    Text("$pct%", Modifier.width(44.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ----- Goals ----------------------------------------------------------------------------------------------------------

@Composable
internal fun GoalsView(model: AppModel) {
    val store = model.store
    var title by remember { mutableStateOf("") }
    var more by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(Category.PERSONAL) }
    var targetDate by remember { mutableStateOf<LocalDate?>(null) }
    fun add() {
        if (title.isBlank()) return
        store.addGoal(title, category, targetDate)
        title = ""; more = false; category = Category.PERSONAL; targetDate = null
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelField(
                    title, { title = it }, Modifier.weight(1f), singleLine = true,
                    placeholder = "A goal, e.g. Learn guitar",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                )
                PixelIconButton("plus", "Add", ::add, Pixel.InkSoft, enabled = title.isNotBlank())
            }
            PixelButton(if (more) "Fewer options" else "Category and target date", { more = !more }, color = Pixel.InkSoft, compact = true)
            if (more) {
                FieldLabel("Category")
                CategoryChips(category) { category = it ?: category }
                // Optional, and only a date to aim for: nothing happens when it passes.
                FieldLabel("Target date")
                PixelDateField(
                    targetDate, store.today, Pixel.Tone, { targetDate = it },
                    presets = listOf("In a month" to 30, "In 3 months" to 91, "In 6 months" to 182), noneLabel = "No date",
                )
            }
        }
    }
    if (model.data.goals.isEmpty()) {
        TextBox { Text("Goals are bigger aims. Break one into small milestones and each finished one earns a Super Candy.", style = MaterialTheme.typography.bodyLarge) }
    }
    model.data.goals.forEach { g ->
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                var editing by remember(g.id) { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    Text(g.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    PixelIconButton("pencil", if (editing) "Close edit" else "Edit goal", { editing = !editing }, Pixel.InkSoft)
                }
                if (editing) GoalEditor(model, g) { editing = false }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Box(Modifier.size(10.dp).background(categoryColor(g.category), PixelShape))
                    Text(
                        listOfNotNull(g.category.label, g.targetDate?.let { "aiming for ${it.short()}" }).joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
                    )
                }
                // The words beside the bar say how close the goal is; the nearer it looks, the likelier it gets finished.
                val done = g.milestones.count { it.isDone }
                val progress = "$done of ${g.milestones.size} milestones"
                PixelBar(GoalRules.progress(g, model.data.tasks).toFloat(), color = Pixel.Green, label = progress)
                if (g.milestones.isNotEmpty()) Text(progress, style = MaterialTheme.typography.bodyMedium)
                g.milestones.forEach { m ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                        Text(m.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = if (m.isDone) Pixel.InkSoft else Pixel.Ink)
                        // Unticking keeps "paid", so ticking again pays nothing more.
                        if (m.isDone) PixelIconButton("undo", "Untick ${m.title}", { store.reopenMilestone(g.id, m.id) }, Pixel.InkSoft)
                        else PixelIconButton("check", "Done", { store.completeMilestone(g.id, m.id) }, Pixel.Green)
                    }
                }
                var step by remember(g.id) { mutableStateOf("") }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    PixelField(
                        step, { step = it }, Modifier.weight(1f), singleLine = true,
                        placeholder = "Add a milestone",
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (step.isNotBlank()) { store.addMilestone(g.id, step); step = "" } }),
                    )
                    PixelIconButton("plus", "Add", { store.addMilestone(g.id, step); step = "" }, Pixel.InkSoft, enabled = step.isNotBlank())
                }
            }
        }
    }
}

/** A goal's title, area and target date, and its milestones' names; Save applies all of it, Delete removes the goal. */
@Composable
private fun GoalEditor(model: AppModel, g: Goal, onClose: () -> Unit) {
    val store = model.store
    var title by remember(g.id) { mutableStateOf(g.title) }
    var category by remember(g.id) { mutableStateOf(g.category) }
    var target by remember(g.id) { mutableStateOf(g.targetDate) }
    val names = remember(g.id) { mutableStateMapOf<String, String>().apply { g.milestones.forEach { put(it.id, it.title) } } }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        PixelField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true, label = "Title")
        FieldLabel("Category")
        CategoryChips(category) { category = it ?: category }
        FieldLabel("Target date")
        PixelDateField(
            target, store.today, Pixel.Tone, { target = it },
            presets = listOf("In a month" to 30, "In 3 months" to 91, "In 6 months" to 182), noneLabel = "No date",
        )
        g.milestones.forEach { m ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelField(names[m.id].orEmpty(), { names[m.id] = it }, Modifier.weight(1f), singleLine = true, label = "Milestone")
                PixelIconButton("trash", "Delete milestone", { store.deleteMilestone(g.id, m.id); names.remove(m.id) }, Pixel.InkSoft)
            }
        }
        PixelButton("Save", {
            store.editGoal(g.id, title, category, target)
            g.milestones.forEach { m -> names[m.id]?.let { n -> if (n.trim() != m.title) store.renameMilestone(g.id, m.id, n) } }
            model.say("Saved.")
            onClose()
        }, Modifier.fillMaxWidth(), Pixel.Green)
        PixelButton("Delete this goal", { val before = model.data.goals; store.deleteGoal(g.id); model.offerUndo("Deleted: ${g.title}") { store.restoreGoals(before) } }, color = Pixel.InkSoft, compact = true)
    }
}

// ----- Weekly review --------------------------------------------------------------------------------------------------

@Composable
internal fun ReviewView(model: AppModel) {
    val store = model.store
    if (!store.canReview()) {
        TextBox { Text("This week's review is done. See you next week!", style = MaterialTheme.typography.bodyLarge) }
        return
    }
    val summary = store.reviewSummary()
    var note by remember { mutableStateOf("") }
    var carry by remember { mutableStateOf(true) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Your week")
            Text("${summary.tasksDone} tasks finished", style = MaterialTheme.typography.bodyLarge)
            Text("${summary.focusMinutes} minutes focused in ${summary.sessionsCompleted} sessions", style = MaterialTheme.typography.bodyLarge)
            summary.habits.filter { it.completions > 0 }.forEach { Text("${it.title}: ${it.completions} days", style = MaterialTheme.typography.bodyMedium) }
            // Where the finished tasks were, biggest first; a bar for each area that had any.
            val split = summary.categorySplit.filterValues { it > 0 }.entries.sortedByDescending { it.value }
            if (split.isNotEmpty()) {
                Text("Where it went", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.S))
                val most = split.first().value
                split.forEach { (c, n) ->
                    Row(
                        Modifier.semantics(mergeDescendants = true) { contentDescription = "${c.label}: ${plural(n, "task")}" },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                    ) {
                        Text(c.label, Modifier.width(84.dp), style = MaterialTheme.typography.bodyMedium)
                        Box(Modifier.weight(1f)) { PixelBar(n.toFloat() / most, color = categoryColor(c)) }
                        Text("$n", Modifier.width(32.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (summary.unfinished.isNotEmpty()) {
                Text("${summary.unfinished.size} not finished yet", style = MaterialTheme.typography.bodyLarge, color = Pixel.InkSoft)
            }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(Reviews.promptFor(store.weekStart), style = MaterialTheme.typography.bodyLarge)
            PixelField(
                note, { note = it }, Modifier.fillMaxWidth(),
                placeholder = "Optional. Only you see this.",
            )
            if (summary.unfinished.isNotEmpty()) {
                ToggleRow("Carry unfinished to tomorrow", carry) { carry = it }
            }
            PixelButton("Finish the review", {
                store.completeReview(note, if (carry) store.today.plus(DatePeriod(days = 1)) else null)
            }, Modifier.fillMaxWidth(), Pixel.Green)
        }
    }
}

// ----- Settings -------------------------------------------------------------------------------------------------------
