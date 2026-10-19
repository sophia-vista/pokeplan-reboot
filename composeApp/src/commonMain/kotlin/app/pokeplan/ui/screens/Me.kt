package app.pokeplan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import app.pokeplan.core.reward.TrainerRank
import app.pokeplan.plan.Category
import app.pokeplan.plan.GoalRules
import app.pokeplan.plan.Reviews
import app.pokeplan.plan.TaskState
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.TextBox
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus

private enum class MeSection(val label: String) { GROWTH("Growth"), GOALS("Goals"), REVIEW("Review"), SETTINGS("Settings") }

@Composable
fun MeScreen(model: AppModel, startSection: Int = 0) {
    var section by remember { mutableStateOf(MeSection.entries[startSection]) }
    val data = model.data
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Me", subtitle = "${data.trainerName} · ${TrainerRank.nameFor(data.player.trainerPoints)}")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TrainerCard(model)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MeSection.entries.forEach { s -> TabButton(s.label, s == section, { section = s }, Modifier.weight(1f)) }
            }
            when (section) {
                MeSection.GROWTH -> GrowthView(model)
                MeSection.GOALS -> GoalsView(model)
                MeSection.REVIEW -> ReviewView(model)
                MeSection.SETTINGS -> SettingsView(model)
            }
        }
    }
}

@Composable
private fun TrainerCard(model: AppModel) {
    val data = model.data
    val buddy = data.player.buddy
    val streak = model.store.streak
    TextBox(accent = Pixel.Red) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (buddy != null) Sprite(buddy.form.id, buddy.isShiny, buddy.nickname, size = 96.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(data.trainerName, style = MaterialTheme.typography.titleMedium)
                Text("${data.player.trainerPoints} trainer points", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                Text("${data.tasks.count { it.state == TaskState.DONE }.let { if (it == 1) "1 task" else "$it tasks" }} done · ${data.focusHistory.filter { it.completed }.sumOf { it.minutes }} min focused", style = MaterialTheme.typography.bodyMedium)
                Text("Streak ${streak.current} (best ${streak.longest}) · ${data.player.caught.size} seen", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ----- Growth ---------------------------------------------------------------------------------------------------------

@Composable
private fun GrowthView(model: AppModel) {
    val wheel = model.store.wheel(7)
    val trend = model.store.trend(6)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("This week by area", style = MaterialTheme.typography.titleMedium)
            Category.entries.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(c.label, Modifier.width(84.dp), style = MaterialTheme.typography.bodyMedium)
                    Box(Modifier.weight(1f)) { PixelBar(wheel.share(c).toFloat(), color = categoryColor(c)) }
                    Text("${(wheel.share(c) * 100).toInt()}%", Modifier.width(44.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("A balanced week is nice, but not required.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Last 6 weeks", style = MaterialTheme.typography.titleMedium)
            val maxTasks = (trend.maxOfOrNull { it.tasksDone } ?: 0).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                trend.forEach { w ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${w.tasksDone}", style = MaterialTheme.typography.labelMedium)
                        Box(Modifier.fillMaxWidth().height((80 * w.tasksDone / maxTasks + 4).dp).background(Pixel.Blue, RoundedCornerShape(6.dp)))
                    }
                }
            }
            Text("Tasks finished each week", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
}

private fun categoryColor(c: Category) = when (c) {
    Category.WORK -> Pixel.Blue
    Category.SCHOOL -> androidx.compose.ui.graphics.Color(0xFF8B5CF6)
    Category.HOME -> androidx.compose.ui.graphics.Color(0xFFF59E0B)
    Category.HEALTH -> Pixel.Green
    Category.SOCIAL -> androidx.compose.ui.graphics.Color(0xFFEC4899)
    Category.PERSONAL -> Pixel.Gray
}

// ----- Goals ----------------------------------------------------------------------------------------------------------

@Composable
private fun GoalsView(model: AppModel) {
    val store = model.store
    var title by remember { mutableStateOf("") }
    TextBox {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                title, { title = it }, Modifier.weight(1f), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = { Text("A goal, e.g. Learn guitar", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) },
            )
            PixelButton("Add", { store.addGoal(title); title = "" }, enabled = title.isNotBlank())
        }
    }
    if (model.data.goals.isEmpty()) {
        TextBox { Text("Goals are bigger aims. Break one into small milestones and each finished one earns a Super Candy.", style = MaterialTheme.typography.bodyLarge) }
    }
    model.data.goals.forEach { g ->
        TextBox(accent = if (g.completedAtMillis != null) Pixel.Green else Pixel.Blue) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(g.title, style = MaterialTheme.typography.titleMedium)
                PixelBar(GoalRules.progress(g, model.data.tasks).toFloat(), color = Pixel.Green)
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
        TextBox(accent = Pixel.Green) { Text("This week's review is done. See you next week!", style = MaterialTheme.typography.bodyLarge) }
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
            if (summary.unfinished.isNotEmpty()) {
                Text("${summary.unfinished.size} not finished yet", style = MaterialTheme.typography.bodyLarge, color = Pixel.InkSoft)
            }
        }
    }
    TextBox(accent = Pixel.Yellow) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(Reviews.promptFor(store.weekStart), style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                note, { note = it }, Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = { Text("Optional. Only you see this.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) },
            )
            if (summary.unfinished.isNotEmpty()) {
                PixelButton(if (carry) "Carry unfinished to tomorrow: yes" else "Carry unfinished to tomorrow: no", { carry = !carry }, Modifier.fillMaxWidth(), if (carry) Pixel.Blue else Pixel.InkSoft, compact = true)
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
                OutlinedTextField(name, { name = it.take(24) }, Modifier.weight(1f), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge)
                PixelButton("Save", { store.rename(name) }, color = Pixel.Green, enabled = name.isNotBlank() && name.trim() != model.data.trainerName, compact = true)
            }
        }
    }
    TextBox {
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
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Toggle("Deep Focus by default", settings.deepFocus) { store.changeSettings { s -> s.copy(deepFocus = it) } }
            Toggle("Sound", settings.sound) { store.changeSettings { s -> s.copy(sound = it) } }
            Toggle("Reduce motion", settings.reduceMotion) { store.changeSettings { s -> s.copy(reduceMotion = it) } }
            Toggle("Week starts on Monday", settings.weekStartsOnMonday) { store.changeSettings { s -> s.copy(weekStartsOnMonday = it) } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Usual focus length", style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButton("-5", { store.changeSettings { s -> s.copy(defaultFocusMinutes = (s.defaultFocusMinutes - 5).coerceAtLeast(5)) } }, color = Pixel.InkSoft, compact = true)
                    Text("${settings.defaultFocusMinutes} min", style = MaterialTheme.typography.bodyLarge)
                    PixelButton("+5", { store.changeSettings { s -> s.copy(defaultFocusMinutes = (s.defaultFocusMinutes + 5).coerceAtMost(180)) } }, color = Pixel.InkSoft, compact = true)
                }
            }
        }
    }
    TextBox(accent = Pixel.Yellow) {
        Text("You are playing as a guest. Your progress is saved on this device only.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        PixelButton(if (on) "On" else "Off", { onChange(!on) }, color = if (on) Pixel.Green else Pixel.InkSoft, compact = true)
    }
}
