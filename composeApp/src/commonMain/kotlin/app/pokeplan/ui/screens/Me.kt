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
    /** The dashboard of history: the totals, then Plan's progress and Care's trends, which open from it and go back to it. */
    STATS("Stats", "exp-share"),

    /** Settings is a menu of pages (owner, 20 November 2026); each goes back to it. Appended, so a saved index still opens the same one. */
    GENERAL("General", "gb-sounds"), PRIVACY("Privacy and access", "storage-key"), NOTIFICATIONS("Notifications", "soothe-bell"), BACKUPS("Backups", "journal"), ABOUT("About", "member-card"),
}

private val settingsPages = listOf(MeSection.GENERAL, MeSection.PRIVACY, MeSection.NOTIFICATIONS, MeSection.BACKUPS, MeSection.ABOUT)

/** The section Back returns to: Stats for its views, Settings for its pages, otherwise the Me menu. */
private fun parentOf(s: MeSection): MeSection? = when (s) {
    MeSection.PROGRESS, MeSection.TRENDS -> MeSection.STATS
    in settingsPages -> MeSection.SETTINGS
    else -> null
}

@Composable
fun MeScreen(model: AppModel, startSection: Int = -1) {
    var section by remember { mutableStateOf(MeSection.entries.getOrNull(startSection)) }
    val data = model.data
    // Into a section and back slides; each page keeps its scroll position.
    ScreenSwitch(section, depth = { if (it == null) 0 else if (parentOf(it) != null) 2 else 1 }) { current ->
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
                            MenuEntry("STATS", MeSection.STATS.icon, MeSection.STATS.label),
                            MenuEntry("SETTINGS", "gb-sounds", "Settings"),
                            MenuEntry("HELP", "town-map", "Help"),
                        ),
                    ) { key -> section = MeSection.valueOf(key) }
                }
            }
            return@ScreenSwitch
        }
        SectionScaffold(current.label, null, onBack = { section = parentOf(current) }, icon = current.icon) {
            when (current) {
                MeSection.STATS -> StatsView(model) { section = it }
                MeSection.SETTINGS -> MenuList(settingsPages.map { MenuEntry(it.name, it.icon, it.label) }) { key -> section = MeSection.valueOf(key) }
                MeSection.GENERAL -> GeneralSettings(model)
                MeSection.PRIVACY -> PrivacySettings(model)
                MeSection.NOTIFICATIONS -> NotificationSettings(model)
                MeSection.BACKUPS -> { BackupsCard(model); ModuleExportsCard(model); ResetCard(model) }
                MeSection.ABOUT -> AboutCard()
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
            if (buddy != null) StatLine("Buddy", "${buddy.nickname}, Lv. ${buddy.level}")
        }
    }
}

/** Stats: the lifetime totals in one box, then the history views that open from it (a row with its icon, like the Me menu). */
@Composable
private fun StatsView(model: AppModel, open: (MeSection) -> Unit) {
    val data = model.data
    val streak = model.store.streak
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            StatLine("Trainer points", "${data.player.trainerPoints}")
            StatLine("Tasks finished", "${data.tasks.count { it.state == TaskState.DONE }}")
            StatLine("Minutes focused", "${data.focusHistory.filter { it.completed }.sumOf { it.minutes }}")
            StatLine("Streak", plural(streak.current, "day"))
            StatLine("Best streak", plural(streak.longest, "day"))
        }
    }
    MenuList(
        listOf(
            MenuEntry("PROGRESS", MeSection.PROGRESS.icon, MeSection.PROGRESS.label),
            MenuEntry("TRENDS", MeSection.TRENDS.icon, MeSection.TRENDS.label),
        ),
    ) { key -> open(MeSection.valueOf(key)) }
}

/** A label at the start and its value at the end of one line. */
@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
        Text(label, Modifier.weight(1f).alignByBaseline(), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        Text(value, Modifier.alignByBaseline(), style = MaterialTheme.typography.bodyLarge)
    }
}

internal fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"

// ----- Growth ---------------------------------------------------------------------------------------------------------
