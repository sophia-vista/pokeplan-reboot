package app.pokeplan.ui.screens

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalTime
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import app.pokeplan.app.ThemeMode
import androidx.compose.foundation.layout.Row
import app.pokeplan.ui.theme.FieldLabel
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.heading
import app.pokeplan.ui.theme.ToggleRow
import app.pokeplan.ui.theme.Spacing
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.ui.unit.DpSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.PasswordVisualTransformation
import app.pokeplan.app.cloud.SyncStatus
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pokeplan.core.reward.Feature
import app.pokeplan.core.reward.TrainerRank
import app.pokeplan.plan.Category
import app.pokeplan.plan.Insights
import app.pokeplan.plan.DeviceCalendar
import app.pokeplan.plan.GoalRules
import app.pokeplan.plan.Reviews
import app.pokeplan.plan.TaskState
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.AppVersion
import app.pokeplan.ui.display
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.TextBox
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

private enum class MeSection(val label: String, val icon: String) {
    GROWTH("Growth", "exp-share"), GOALS("Goals", "star-piece"), REVIEW("Review", "journal"), FRIENDS("Friends", "friend-ball"), SETTINGS("Settings", "gb-sounds"), HELP("Help", "town-map"),
}

@Composable
fun MeScreen(model: AppModel, startSection: Int = -1) {
    var section by remember { mutableStateOf(MeSection.entries.getOrNull(startSection)) }
    val data = model.data
    val current = section
    if (current == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Me", subtitle = "Rank: ${TrainerRank.nameFor(data.player.trainerPoints)}", icon = "member-card")
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TrainerCard(model)
                MenuGrid(
                    (listOfNotNull(
                        if (model.store.sees(Feature.GROWTH_SCREEN)) MenuEntry("GROWTH", "exp-share", "Growth", "effort and trends", tint = Pixel.Green) else null,
                        if (!model.store.sees(Feature.GOALS)) null else MenuEntry("GOALS", "star-piece", "Goals", if (data.goals.size == 1) "1 goal" else "${data.goals.size} goals", tint = Pixel.Warning),
                        MenuEntry("REVIEW", "journal", "Review", if (model.store.canReview()) "ready this week" else "done this week", badge = if (model.store.canReview()) 1 else 0, tint = Pixel.Blue),
                    ) + (if (model.social != null) listOf(MenuEntry("FRIENDS", "friend-ball", "Friends", if (data.account == null) "sign in first" else "gifts and the weekly board", tint = androidx.compose.ui.graphics.Color(0xFFEC4899))) else emptyList()) + listOf(
                        MenuEntry("SETTINGS", "gb-sounds", "Settings", "name, region, sound", tint = Pixel.InkSoft),
                        MenuEntry("HELP", "town-map", "Help", "how it works", tint = Pixel.Blue),
                    )),
                ) { key -> section = MeSection.valueOf(key) }
            }
        }
        return
    }
    SectionScaffold(current.label, "Me", onBack = { section = null }, icon = current.icon) {
        when (current) {
            MeSection.GROWTH -> GrowthView(model)
            MeSection.GOALS -> GoalsView(model)
            MeSection.REVIEW -> ReviewView(model)
            MeSection.FRIENDS -> FriendsView(model)
            MeSection.SETTINGS -> SettingsView(model)
            MeSection.HELP -> HelpView()
        }
    }
}

@Composable
private fun TrainerCard(model: AppModel) {
    val data = model.data
    val buddy = data.player.buddy
    val streak = model.store.streak
    val rank = TrainerRank.rankFor(data.player.trainerPoints)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (buddy != null) Sprite(buddy.form.id, buddy.isShiny, buddy.nickname, size = 96.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(data.trainerName, style = MaterialTheme.typography.titleMedium)
                    Text("${TrainerRank.names[rank.coerceIn(0, TrainerRank.names.lastIndex)]}, ${data.player.trainerPoints} trainer points", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    if (buddy != null) Text("Buddy: ${buddy.nickname}, Lv. ${buddy.level}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                }
            }
            // Each number says what it counts and over what time, so none is mistaken for another.
            Text("All time: ${plural(data.tasks.count { it.state == TaskState.DONE }, "task")} finished, ${data.focusHistory.filter { it.completed }.sumOf { it.minutes }} min focused", style = MaterialTheme.typography.bodyMedium)
            Text("Streak: ${plural(streak.current, "day")} in a row (best ${streak.longest})", style = MaterialTheme.typography.bodyMedium)
            Text(
                "A day counts with a paid task or a finished focus session. One missed day a week is free, and rest days don't count against you.",
                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
            )
        }
    }
}

private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"

// ----- Growth ---------------------------------------------------------------------------------------------------------

/** The ranges the balance wheel can show (design.md, Growth). */
private val growthRanges = listOf(7, 30, 90)

@Composable
private fun GrowthView(model: AppModel) {
    var days by remember { mutableStateOf(7) }
    val wheel = model.store.wheel(days)
    val trend = model.store.trend(6)
    val weekStart = if (model.data.settings.weekStartsOnMonday) "Monday" else "Sunday"

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        growthRanges.forEach { d -> TabButton("$d days", d == days, { days = d }, Modifier.weight(1f)) }
    }
    Insights.estimateAccuracy(model.data.tasks)?.let { acc ->
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Your estimates", style = MaterialTheme.typography.titleMedium)
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Where your effort went", style = MaterialTheme.typography.titleMedium)
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        trend, { it.tasksDone }, { it.toString() }, Pixel.Blue,
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(note, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            val max = (weeks.maxOfOrNull(value) ?: 0).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                weeks.forEachIndexed { i, w ->
                    val v = value(w)
                    val week = if (i == weeks.lastIndex) "this week" else "week of ${w.weekStart.dayMonth()}"
                    Column(
                        Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "$title, $week: ${label(v)}" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(label(v), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        Box(Modifier.fillMaxWidth().height((80 * v / max + 4).dp).background(color, RoundedCornerShape(6.dp)))
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Goal progress", style = MaterialTheme.typography.titleMedium)
            if (goals.isEmpty()) {
                Text("No goals yet. Add one under Me, Goals.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                return@Column
            }
            Text(
                "${goals.count { it.completedAtMillis != null }} of ${plural(goals.size, "goal")} reached. Progress is milestones done (or linked tasks, for a goal without milestones).",
                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
            )
            goals.filter { it.completedAtMillis == null }.take(5).forEach { g ->
                val pct = (GoalRules.progress(g, model.data.tasks) * 100).roundToInt()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun GoalsView(model: AppModel) {
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    title, { title = it }, Modifier.weight(1f), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
                    placeholder = { Text("A goal, e.g. Learn guitar", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                )
                PixelButton("Add", ::add, enabled = title.isNotBlank())
            }
            PixelButton(if (more) "Fewer options" else "Category and target date", { more = !more }, color = Pixel.InkSoft, compact = true)
            if (more) {
                FieldLabel("Category")
                CategoryChips(category) { category = it ?: category }
                // Optional, and only a date to aim for: nothing happens when it passes.
                DayPicker(
                    "Target date", targetDate, store.today, listOf("In a month" to 30, "In 3 months" to 91, "In 6 months" to 182),
                    noneLabel = "No date", step = 7, onChange = { targetDate = it },
                )
            }
        }
    }
    if (model.data.goals.isEmpty()) {
        TextBox { Text("Goals are bigger aims. Break one into small milestones and each finished one earns a Super Candy.", style = MaterialTheme.typography.bodyLarge) }
    }
    model.data.goals.forEach { g ->
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(g.title, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(10.dp).background(categoryColor(g.category), CircleShape))
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(m.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = if (m.isDone) Pixel.InkSoft else Pixel.Ink)
                        if (!m.isDone) PixelButton("Done", { store.completeMilestone(g.id, m.id) }, color = Pixel.Green, compact = true)
                    }
                }
                var step by remember(g.id) { mutableStateOf("") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        step, { step = it }, Modifier.weight(1f), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
                        placeholder = { Text("Add a milestone", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (step.isNotBlank()) { store.addMilestone(g.id, step); step = "" } }),
                    )
                    PixelButton("Add", { store.addMilestone(g.id, step); step = "" }, enabled = step.isNotBlank(), compact = true)
                }
            }
        }
    }
}

// ----- Weekly review --------------------------------------------------------------------------------------------------

@Composable
private fun ReviewView(model: AppModel) {
    val store = model.store
    RewardBox(model)
    if (!store.canReview()) {
        TextBox { Text("This week's review is done. See you next week!", style = MaterialTheme.typography.bodyLarge) }
        return
    }
    val summary = store.reviewSummary()
    var note by remember { mutableStateOf("") }
    var carry by remember { mutableStateOf(true) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Your week", style = MaterialTheme.typography.titleMedium)
            Text("${summary.tasksDone} tasks finished", style = MaterialTheme.typography.bodyLarge)
            Text("${summary.focusMinutes} minutes focused in ${summary.sessionsCompleted} sessions", style = MaterialTheme.typography.bodyLarge)
            summary.habits.filter { it.completions > 0 }.forEach { Text("${it.title}: ${it.completions} days", style = MaterialTheme.typography.bodyMedium) }
            // Where the finished tasks were, biggest first; a bar for each area that had any.
            val split = summary.categorySplit.filterValues { it > 0 }.entries.sortedByDescending { it.value }
            if (split.isNotEmpty()) {
                Text("Where it went", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                val most = split.first().value
                split.forEach { (c, n) ->
                    Row(
                        Modifier.semantics(mergeDescendants = true) { contentDescription = "${c.label}: ${plural(n, "task")}" },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(Reviews.promptFor(store.weekStart), style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                note, { note = it }, Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = { Text("Optional. Only you see this.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) },
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

@Composable
private fun SettingsView(model: AppModel) {
    val store = model.store
    val settings = model.data.settings
    var name by remember { mutableStateOf(model.data.trainerName) }

    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Trainer name", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val changed = name.isNotBlank() && name.trim() != model.data.trainerName
                OutlinedTextField(
                    name, { name = it.take(24) }, Modifier.weight(1f), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (changed) store.rename(name) }),
                )
                PixelButton("Save", { store.rename(name) }, color = Pixel.Green, enabled = name.isNotBlank() && name.trim() != model.data.trainerName, compact = true)
            }
        }
    }
    if (store.sees(Feature.REGION_PICKER)) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Hatching region", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TrainerRank.unlockedRegions(model.data.player.trainerPoints).forEach { r ->
                    TabButton(r.label, r == settings.region, { store.changeSettings { it.copy(region = r) } })
                }
            }
            Text("A new region opens with each trainer rank.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
    // Settings come in small groups, one card each, so a setting is found by its group rather than by reading a long list.
    SettingsGroup("Focus") {
        ToggleRow("Deep Focus by default", settings.deepFocus) { store.changeSettings { s -> s.copy(deepFocus = it) } }
        Stepper("Usual focus length", "${settings.defaultFocusMinutes} min", 5) { by ->
            store.changeSettings { s -> s.copy(defaultFocusMinutes = (s.defaultFocusMinutes + by).coerceIn(5, 180)) }
        }
    }
    SettingsGroup("Sound and touch") {
        VolumeRow("Sound effects and cries", settings.effectsVolume) { v -> store.changeSettings { s -> s.copy(effectsVolume = v, sound = true) } }
        ToggleRow("Vibration", settings.haptics) { store.changeSettings { s -> s.copy(haptics = it) } }
    }
    SettingsGroup("Look") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ThemeMode.entries.forEach { mode -> TabButton(mode.label, settings.theme == mode, { store.changeSettings { s -> s.copy(theme = mode) } }) }
        }
        ToggleRow("Reduce motion", settings.reduceMotion, detail = "Sprites stand still and nothing wobbles.") { store.changeSettings { s -> s.copy(reduceMotion = it) } }
    }
    SettingsGroup("Notifications and week") {
        ToggleRow("Task reminders", settings.reminders) { store.changeSettings { s -> s.copy(reminders = it) } }
        if (settings.reminders) {
            fun hour(t: LocalTime) = "${t.hour.toString().padStart(2, '0')}:00"
            fun shift(t: LocalTime, by: Int) = LocalTime((t.hour + by + 24) % 24, 0)
            Stepper("Quiet from", hour(settings.quietHours.start), 1) { by ->
                store.changeSettings { s -> s.copy(quietHours = s.quietHours.copy(start = shift(s.quietHours.start, by))) }
            }
            Stepper("Quiet until", hour(settings.quietHours.end), 1) { by ->
                store.changeSettings { s -> s.copy(quietHours = s.quietHours.copy(end = shift(s.quietHours.end, by))) }
            }
            ToggleRow("Repeat until answered", settings.repeatReminders, detail = "An unanswered reminder comes back up to 3 times, 10 minutes apart.") { store.changeSettings { s -> s.copy(repeatReminders = it) } }
            Text("Reminders wait during quiet hours, a focus session and rest days.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
        ToggleRow("Focus halfway alert", settings.focusHalfway, detail = "A quiet note halfway through sessions of 15 minutes or more.") { store.changeSettings { s -> s.copy(focusHalfway = it) } }
        ToggleRow("Focus end alert", settings.focusEnd, detail = "When a session's time is up. These two are the only notifications during focus.") { store.changeSettings { s -> s.copy(focusEnd = it) } }
        ToggleRow("Streak reminder", settings.streakReminder, detail = "In the evening, if nothing has counted yet today and you have a streak going.") { store.changeSettings { s -> s.copy(streakReminder = it) } }
        if (settings.streakReminder) {
            Stepper("Streak reminder at", "${settings.streakReminderHour.toString().padStart(2, '0')}:00", 1) { by ->
                store.changeSettings { s -> s.copy(streakReminderHour = (s.streakReminderHour + by + 24) % 24) }
            }
        }
        ToggleRow("Weekly recap", settings.weeklyRecap, detail = "Sunday evening: tasks finished, focus time and your top category. Skipped after an empty week.") { store.changeSettings { s -> s.copy(weeklyRecap = it) } }
        ToggleRow("Week starts on Monday", settings.weekStartsOnMonday) { store.changeSettings { s -> s.copy(weekStartsOnMonday = it) } }
    }
    CalendarsCard(model)
    AccountCard(model)
    DataCard(model)
    AboutCard()
}

/** Which of the phone's calendars show on Planner → Calendar. Only where the platform can read them (Android). */
@Composable
private fun CalendarsCard(model: AppModel) {
    val source = model.calendars ?: return
    var permitted by remember { mutableStateOf(source.permitted()) }
    var calendars by remember { mutableStateOf<List<DeviceCalendar>?>(null) }
    LaunchedEffect(permitted) { if (permitted) calendars = withContext(Dispatchers.Default) { source.calendars() } }
    val shown = model.data.shownCalendars

    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Calendars", style = MaterialTheme.typography.titleMedium)
            Text(
                "Show events from this phone's calendars, Google Calendar included, on Planner → Calendar. They're read-only and never pay.",
                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
            )
            val list = calendars
            when {
                !permitted -> PixelButton("Choose calendars", { source.requestPermission { permitted = it } }, Modifier.fillMaxWidth(), Pixel.Blue, compact = true)
                list == null -> Unit
                list.isEmpty() -> Text("No calendars on this phone yet.", style = MaterialTheme.typography.bodyMedium)
                else -> list.forEach { c ->
                    val label = if (c.account.isBlank() || c.account == c.name) c.name else "${c.name}, ${c.account}"
                    ToggleRow(label, c.id in shown) { on -> model.store.setShownCalendars(if (on) shown + c.id else shown - c.id) }
                }
            }
        }
    }
}

/** The player's data: a copy to keep, deleting the account, starting over on this device. Each destructive step asks twice. */
@Composable
private fun DataCard(model: AppModel) {
    val store = model.store
    val sync = model.sync
    val signedIn = model.data.account != null
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your data", style = MaterialTheme.typography.titleMedium)
            model.files?.let { files ->
                PixelButton("Export my data", { note = files.save("pokeplan-export-${store.today}.json", store.exportJson()) }, Modifier.fillMaxWidth(), Pixel.Blue, compact = true)
            }
            when (asking) {
                "account" -> {
                    Text("Delete your account? Your cloud save, friends and gifts are removed for good. This device keeps its progress as a guest.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Danger)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelButton("Delete for good", {
                            busy = true
                            scope.launch {
                                note = sync?.deleteAccount() ?: "Your account was deleted."
                                busy = false
                                asking = null
                            }
                        }, Modifier.weight(1f), Pixel.Danger, enabled = !busy, compact = true)
                        PixelButton("Keep it", { asking = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                }
                "device" -> {
                    Text("Erase this device? Every task, Pokémon and setting here is removed and the app starts over. This cannot be undone.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Danger)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelButton("Erase", { store.eraseDevice(); asking = null }, Modifier.weight(1f), Pixel.Danger, compact = true)
                        PixelButton("Keep it", { asking = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (signedIn && sync != null) PixelButton("Delete account", { asking = "account"; note = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    else PixelButton("Erase this device", { asking = "device"; note = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
        }
    }
}

/** A labelled volume slider, 0 to 100 in steps of 10. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text("$value%", style = MaterialTheme.typography.bodyMedium)
        }
        val colors = SliderDefaults.colors(thumbColor = Pixel.Red, activeTrackColor = Pixel.Red, inactiveTrackColor = Pixel.Shade, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent)
        val interaction = remember { MutableInteractionSource() }
        Slider(
            value = value / 100f, onValueChange = { onChange((it * 10).roundToInt() * 10) }, valueRange = 0f..1f, steps = 9,
            colors = colors, interactionSource = interaction,
            thumb = { SliderDefaults.Thumb(interaction, colors = colors, thumbSize = DpSize(24.dp, 24.dp)) },
            modifier = Modifier.semantics { contentDescription = "$label volume $value percent" },
        )
    }
}

/** The app's version, so a bug report or a friend's install can say exactly which build it is. */
@Composable
private fun AboutCard() {
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("About", style = MaterialTheme.typography.titleMedium)
            Text("PokéPlan ${AppVersion.display}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "A private fan project. Pokémon names, sprites and sounds belong to Nintendo, Game Freak and The Pokémon Company.",
                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
            )
        }
    }
}

/** Sign in, sign out and the state of the cloud save. Without a configured project it says online features are not set up. */
@Composable
private fun AccountCard(model: AppModel) {
    val sync = model.sync
    val account = model.data.account
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Account", style = MaterialTheme.typography.titleMedium)
            when {
                sync == null -> Text(
                    "Online features are off in this build. Progress is saved on this device only.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                account == null -> {
                    Text("Sign in to back up your progress and use it on your other devices.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                        label = { Text("Email") })
                    OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                        label = { Text("Password") },
                        // Say the rule up front instead of leaving the buttons grey without a reason.
                        supportingText = { Text("At least 6 characters", style = MaterialTheme.typography.bodyMedium) })
                    message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.Danger) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelButton("Sign in", {
                            busy = true; message = null
                            scope.launch { message = sync.signIn(email.trim(), password); busy = false }
                        }, Modifier.weight(1f), Pixel.Blue, enabled = !busy && email.isNotBlank() && password.length >= 6, compact = true)
                        PixelButton("Create account", {
                            busy = true; message = null
                            scope.launch {
                                val result = sync.signUp(email.trim(), password)
                                message = if (result == "confirm") "Check your email to confirm your account, then sign in." else result
                                busy = false
                            }
                        }, Modifier.weight(1f), Pixel.Green, enabled = !busy && email.isNotBlank() && password.length >= 6, compact = true)
                    }
                }
                else -> {
                    Text("Signed in as ${account.email}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        when (val st = model.syncStatus) {
                            SyncStatus.Syncing -> "Syncing..."
                            is SyncStatus.Offline -> "No connection. Your changes are kept and sent later."
                            is SyncStatus.Refused -> st.message
                            is SyncStatus.Conflict -> "This device and the cloud both have new progress."
                            else -> if (account.unsynced) "Changes waiting to sync" else "Everything is saved to the cloud."
                        },
                        style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
                    )
                    val status = model.syncStatus
                    if (status is SyncStatus.Conflict) {
                        Text("Which should win?", style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton("This device", { scope.launch { sync.resolve(keepThisDevice = true) } }, Modifier.weight(1f), Pixel.Blue, compact = true)
                            PixelButton("The cloud", { scope.launch { sync.resolve(keepThisDevice = false) } }, Modifier.weight(1f), Pixel.Green, compact = true)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton("Sync now", { scope.launch { sync.sync() } }, Modifier.weight(1f), Pixel.Blue, enabled = status != SyncStatus.Syncing, compact = true)
                            PixelButton("Sign out", { sync.signOut() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                        }
                    }
                }
            }
        }
    }
}

/** One group of settings in its own card, under a small heading. */
@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

/** A value nudged down and up by [step] ("-5", "+5"), with its label at the start of the row. */
@Composable
private fun Stepper(label: String, value: String, step: Int, change: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelButton("-$step", { change(-step) }, color = Pixel.InkSoft, compact = true)
            Text(value, style = MaterialTheme.typography.bodyLarge)
            PixelButton("+$step", { change(step) }, color = Pixel.InkSoft, compact = true)
        }
    }
}
