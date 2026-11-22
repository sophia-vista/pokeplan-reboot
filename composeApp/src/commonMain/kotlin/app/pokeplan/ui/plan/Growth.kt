package app.pokeplan.ui.plan

import app.pokeplan.ui.screens.FormBox
import app.pokeplan.ui.screens.FormDisclosure
import app.pokeplan.ui.screens.FormSave
import app.pokeplan.ui.screens.FormStack
import app.pokeplan.ui.theme.PixelShape
import app.pokeplan.ui.theme.Spacing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
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
import app.pokeplan.ui.BadgeImage
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.theme.Sizes
import app.pokeplan.plan.Insights
import app.pokeplan.plan.Goal
import app.pokeplan.plan.GoalRules
import app.pokeplan.plan.TaskState
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.semantics.heading
import app.pokeplan.ui.pokemon.label
import app.pokeplan.ui.pokemon.pct
import app.pokeplan.ui.screens.PixelDateField
import app.pokeplan.ui.screens.TabButton
import app.pokeplan.ui.screens.TaskTitleLine
import app.pokeplan.ui.screens.dayMonth
import app.pokeplan.ui.screens.label
import app.pokeplan.ui.screens.plural
import app.pokeplan.ui.screens.short

private val growthRanges = listOf(7, 30, 90)

@Composable
internal fun GrowthView(model: AppModel) {
    var days by remember { mutableStateOf(7) }
    val wheel = model.store.wheel(days)
    val trend = model.store.trend(6)

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
                        Insights.Bias.UNDERESTIMATES -> "Tasks take about $pct% of what you guess" // copy-ok: result
                        Insights.Bias.OVERESTIMATES -> "Tasks take about $pct% of what you guess" // copy-ok: result
                        Insights.Bias.ABOUT_RIGHT -> "Your guesses are about right ($pct% of the estimate)." // copy-ok: result
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Where your effort went")
            val lists = model.data.lists
            val busy = wheel.lists.filter { wheel.score(it) > 0 }.sortedByDescending { wheel.score(it) }
            fun nameOf(id: String?) = lists.firstOrNull { it.id == id }?.name ?: "No list"
            fun colorOf(id: String?) = lists.firstOrNull { it.id == id }?.let { Color(it.color) } ?: Pixel.Gray
            if (busy.isEmpty()) Text("Nothing finished in the last $days days yet.", style = MaterialTheme.typography.bodyLarge) // copy-ok: result
            busy.forEach { c ->
                val tasks = wheel.tasksDone[c] ?: 0
                val minutes = wheel.focusMinutes[c] ?: 0
                val share = (wheel.share(c) * 100).roundToInt()
                Column(Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "${nameOf(c)}: ${plural(tasks, "task")}, $minutes minutes focused, $share percent of your effort"
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Text(nameOf(c), Modifier.width(84.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box(Modifier.weight(1f)) { PixelBar(wheel.share(c).toFloat(), color = colorOf(c)) }
                        Text("$share%", Modifier.width(44.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("${plural(tasks, "task")}, $minutes min", Modifier.padding(start = 92.dp), style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft)
                }
            }
        }
    }
    WeekBars(
        "Tasks finished per week",
        trend, { it.tasksDone }, { it.toString() }, Pixel.Tone,
    )
    WeekBars(
        "Focus per week",
        trend, { it.focusMinutes }, { if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m" }, Pixel.Tone,
    )
    GoalsSummary(model)
}

@Composable
private fun WeekBars(
    title: String, weeks: List<Insights.WeekPoint>, value: (Insights.WeekPoint) -> Int, label: (Int) -> String, color: Color,
) {
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
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

@Composable
private fun GoalsSummary(model: AppModel) {
    val goals = model.data.goals
    if (goals.isEmpty()) return
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Goal progress")
            goals.filter { it.completedAtMillis == null }.take(5).forEach { g ->
                val pct = (GoalRules.progress(g, model.data.tasks) * 100).roundToInt()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Text(g.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Box(Modifier.width(96.dp)) { PixelBar(pct / 100f, color = Pixel.Tone) }
                    Text("$pct%", Modifier.width(44.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GoalsView(model: AppModel) {
    val store = model.store
    var title by remember { mutableStateOf("") }
    var more by remember { mutableStateOf(false) }
    var targetDate by remember { mutableStateOf<LocalDate?>(null) }
    fun add() {
        if (title.isBlank()) return
        store.addGoal(title, targetDate)
        title = ""; more = false; targetDate = null
    }
    FormBox {
        FormStack {
            PixelField(
                title, { title = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = "Goal", placeholder = "e.g. Learn guitar",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
            )
            FormDisclosure("Additional details", more, { more = !more }) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    FieldLabel("Target date")
                    PixelDateField(
                        targetDate, store.today, Pixel.Tone, { targetDate = it },
                        presets = listOf("In a month" to 30, "In 3 months" to 91, "In 6 months" to 182), noneLabel = "No date",
                    )
                }
            }
            FormSave("Add goal", ::add, Pixel.InkSoft, enabled = title.isNotBlank())
        }
    }
    val (done, open) = model.data.goals.partition { it.completedAtMillis != null }
    open.forEach { g -> GoalCard(model, g) }
    if (done.isNotEmpty()) {
        TextBox { PixelTitle("Hall of Fame") }
        GoalRules.hallOfFame(done).forEach { g -> GoalCard(model, g) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalCard(model: AppModel, g: Goal) {
    val store = model.store
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            var editing by remember(g.id) { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                Text(g.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                PixelIconButton("pencil", if (editing) "Close edit" else "Edit goal", { editing = !editing }, Pixel.InkSoft)
            }
            if (editing) GoalEditor(model, g) { editing = false }
            g.targetDate?.let { Text("aiming for ${it.short()}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
            val linked = model.data.tasks.filter { it.id in g.linkedTaskIds && it.state != TaskState.DELETED && it.state != TaskState.WONT_DO }
            val progress = if (g.milestones.isEmpty() && linked.isNotEmpty()) "${linked.count { it.state == TaskState.DONE }} of ${linked.size} tasks"
                else "${g.milestones.count { it.isDone }} of ${g.milestones.size} milestones"
            PixelBar(GoalRules.progress(g, model.data.tasks).toFloat(), color = Pixel.Tone, label = progress)
            BadgeCase(g)
            g.completedAtMillis?.let { done ->
                val day = store.dayOf(done).dayMonth()
                Text(if (g.hallOfFameParty.isEmpty()) "Finished $day" else "Finished $day, with", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    g.hallOfFameParty.forEach { Sprite(it.formId, it.shiny, "Level ${it.level} Pokémon", size = Sizes.SpriteS) }
                }
            }
            g.milestones.forEach { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    Text(m.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = if (m.isDone) Pixel.InkSoft else Pixel.Ink)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BadgeCase(g: Goal) {
    val slots = GoalRules.badgeCase(g)
    val earned = slots.count { it.earned }
    FlowRow(
        Modifier.semantics { contentDescription = "Badges: $earned of ${slots.size}" },
        horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalArrangement = Arrangement.spacedBy(Spacing.XS),
    ) {
        slots.forEach { BadgeImage(it.badgeId, size = Sizes.Icon, silhouette = if (it.earned) null else Pixel.InkSoft.copy(alpha = 0.35f)) }
    }
}

@Composable
private fun GoalEditor(model: AppModel, g: Goal, onClose: () -> Unit) {
    val store = model.store
    var title by remember(g.id) { mutableStateOf(g.title) }
    var target by remember(g.id) { mutableStateOf(g.targetDate) }
    val names = remember(g.id) { mutableStateMapOf<String, String>().apply { g.milestones.forEach { put(it.id, it.title) } } }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        PixelField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = "Title")
        FieldLabel("Target date")
        PixelDateField(
            target, store.today, Pixel.Tone, { target = it },
            presets = listOf("In a month" to 30, "In 3 months" to 91, "In 6 months" to 182), noneLabel = "No date",
        )
        g.milestones.forEach { m ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelField(names[m.id].orEmpty(), { names[m.id] = it }, Modifier.weight(1f), singleLine = true, placeholder = "Milestone")
                PixelIconButton("trash", "Delete milestone", { store.deleteMilestone(g.id, m.id); names.remove(m.id) }, Pixel.InkSoft)
            }
        }
        PixelButton("Save", {
            store.editGoal(g.id, title, target)
            g.milestones.forEach { m -> names[m.id]?.let { n -> if (n.trim() != m.title) store.renameMilestone(g.id, m.id, n) } }
            model.say("Saved.")
            onClose()
        }, Modifier.fillMaxWidth())
        PixelButton("Delete this goal", { val before = model.data.goals; store.deleteGoal(g.id); model.offerUndo("Deleted: ${g.title}") { store.restoreGoals(before) } }, color = Pixel.InkSoft, compact = true)
    }
}

@Composable
internal fun ProgressView(model: AppModel) {
    app.pokeplan.ui.theme.ModuleTone(app.pokeplan.ui.theme.ModuleAccent.PLAN) { GrowthView(model) }
}
