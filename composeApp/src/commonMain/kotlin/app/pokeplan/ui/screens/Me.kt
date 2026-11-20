package app.pokeplan.ui.screens

import app.pokeplan.ui.theme.Sizes
import app.pokeplan.ui.theme.Spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pokeplan.core.reward.TrainerRank
import app.pokeplan.plan.TaskState
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.pokemon.label

/** Me holds what every module shares and every module's stats (owner, 20 November 2026): Plan's progress and Care's trends. New sections go at the end, so a saved index still opens the same one. */
private enum class MeSection(val label: String, val icon: String) {
    SETTINGS("Settings", "gb-sounds"), HELP("Help", "town-map"), PROGRESS("Plan progress", "exp-share"), TRENDS("Care trends", "heart-scale"),
}

@Composable
fun MeScreen(model: AppModel, startSection: Int = -1) {
    var section by remember { mutableStateOf(MeSection.entries.getOrNull(startSection)) }
    val data = model.data
    // Into a section and back slides; each page keeps its scroll position.
    ScreenSwitch(section, depth = { if (it == null) 0 else 1 }) { current ->
        if (current == null) {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader("Me", icon = "member-card")
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.M),
                    verticalArrangement = Arrangement.spacedBy(Spacing.M),
                ) {
                    TrainerCard(model)
                    MenuList(
                        listOf(
                            MenuEntry("PROGRESS", MeSection.PROGRESS.icon, MeSection.PROGRESS.label),
                            MenuEntry("TRENDS", MeSection.TRENDS.icon, MeSection.TRENDS.label),
                            MenuEntry("SETTINGS", "gb-sounds", "Settings"),
                            MenuEntry("HELP", "town-map", "Help"),
                        ),
                    ) { key -> section = MeSection.valueOf(key) }
                }
            }
            return@ScreenSwitch
        }
        SectionScaffold(current.label, null, onBack = { section = null }, icon = current.icon) {
            when (current) {
                MeSection.SETTINGS -> SettingsView(model)
                MeSection.HELP -> HelpView()
                MeSection.PROGRESS -> app.pokeplan.ui.plan.ProgressView(model)
                MeSection.TRENDS -> app.pokeplan.ui.care.CareTrendsView(model)
            }
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
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                if (buddy != null) Sprite(buddy.form.id, buddy.isShiny, buddy.nickname, size = Sizes.SpriteL)
                Text(data.trainerName, style = MaterialTheme.typography.titleMedium)
            }
            StatLine("Rank", TrainerRank.names[rank.coerceIn(0, TrainerRank.names.lastIndex)])
            StatLine("Trainer points", "${data.player.trainerPoints}")
            if (buddy != null) StatLine("Buddy", "${buddy.nickname}, Lv. ${buddy.level}")
            StatLine("Tasks finished", "${data.tasks.count { it.state == TaskState.DONE }}")
            StatLine("Minutes focused", "${data.focusHistory.filter { it.completed }.sumOf { it.minutes }}")
            StatLine("Streak", plural(streak.current, "day"))
            StatLine("Best streak", plural(streak.longest, "day"))
        }
    }
}

/** A label at the start and its value at the end of one line. */
@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

internal fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"

// ----- Growth ---------------------------------------------------------------------------------------------------------
