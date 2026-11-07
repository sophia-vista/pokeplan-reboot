package app.pokeplan.ui.screens

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pokeplan.core.reward.TrainerRank
import app.pokeplan.plan.TaskState
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.TextBox

/** Me holds only what every module shares; Plan's Growth, Goals and Review are in Modules, Plan. */
private enum class MeSection(val label: String, val icon: String) {
    SETTINGS("Settings", "gb-sounds"), HELP("Help", "town-map"),
}

@Composable
fun MeScreen(model: AppModel, startSection: Int = -1) {
    var section by remember { mutableStateOf(MeSection.entries.getOrNull(startSection)) }
    val data = model.data
    // Into a section and back slides; each page keeps its scroll position.
    ScreenSwitch(section, depth = { if (it == null) 0 else 1 }) { current ->
        if (current == null) {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader("Me", subtitle = "Rank: ${TrainerRank.nameFor(data.player.trainerPoints)}", icon = "member-card")
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.M),
                    verticalArrangement = Arrangement.spacedBy(Spacing.M),
                ) {
                    TrainerCard(model)
                    MenuGrid(
                        listOfNotNull(
                            MenuEntry("SETTINGS", "gb-sounds", "Settings", tint = Pixel.InkSoft),
                            MenuEntry("HELP", "town-map", "Help", tint = Pixel.Blue),
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
        }
    }
}

internal fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"

// ----- Growth ---------------------------------------------------------------------------------------------------------
