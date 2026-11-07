package app.pokeplan.ui

import app.pokeplan.ui.theme.PixelShape

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pokeplan.app.BuddyScene
import app.pokeplan.ui.launcher.FriendAreas
import app.pokeplan.ui.launcher.TownBackdrop
import app.pokeplan.ui.launcher.TownScenes
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.Spacing

/** The page's background choices: the day gradient, then the real scenes, the dungeon towns and the Rescue Team Friend Areas. */
val AppBackgrounds: List<Pair<String, String>> = listOf("day" to "Day") + TownScenes.all + FriendAreas.all

/** The choices in the groups the picker shows, each with its heading (none for the first). */
private val BackgroundGroups: List<Pair<String?, List<Pair<String, String>>>> = listOf(
    null to (listOf("day" to "Day") + TownScenes.scenes),
    "Dungeons" to TownScenes.dungeons,
    "Rescue Team dungeons" to TownScenes.rescueTeam,
    "Rescue Team Friend Areas" to FriendAreas.all,
    "Explorers of Sky dungeons" to TownScenes.explorers,
)

/**
 * The page behind every screen: the time-of-day gradient ([DayBackground], the default) or a scene or dungeon town drawn
 * like the launcher's, under a veil of the page colour so the text on top stays readable.
 */
@Composable
fun AppBackdrop(name: String, hour: Int, minute: Int, dark: Boolean) {
    if (AppBackgrounds.none { it.first == name } || name == "day") {
        DayBackground(hour, minute, dark = dark)
        return
    }
    Box(Modifier.fillMaxSize()) {
        TownBackdrop(name, Modifier.fillMaxSize(), darkness = BuddyScene.darkness(hour, minute))
        Box(Modifier.fillMaxSize().background(Pixel.Paper.copy(alpha = if (dark) 0.78f else 0.6f)))
    }
}

/** Small pictures of the page backgrounds, two to a row, with the chosen one outlined (Settings → Look). */
@Composable
fun BackgroundChoices(current: String, hour: Int, minute: Int, dark: Boolean, onPick: (String) -> Unit) {
    val shape = PixelShape
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        BackgroundGroups.forEach { (heading, choices) ->
            // The long groups start closed (unless the chosen one is in them): only an open group draws its pictures.
            ChoiceGroup(heading, choices.size, initiallyOpen = heading == null || choices.any { it.first == current }) {
                choices.chunked(2).forEach { row -> BackgroundRow(row, current, hour, minute, dark, shape, onPick) }
            }
        }
    }
}

@Composable
private fun BackgroundRow(row: List<Pair<String, String>>, current: String, hour: Int, minute: Int, dark: Boolean, shape: androidx.compose.ui.graphics.Shape, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        row.forEach { (name, label) ->
            Box(
                Modifier.weight(1f).height(72.dp).clip(shape).then(if (name == current) Modifier.border(3.dp, Pixel.Blue, shape) else Modifier)
                    .clickable(role = Role.RadioButton, onClickLabel = "Use $label as the background") { onPick(name) },
            ) {
                if (name == "day") DayBackground(hour, minute, dark = dark) else TownBackdrop(name, Modifier.fillMaxSize())
                Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = Spacing.S, vertical = 2.dp))
            }
        }
        if (row.size == 1) Box(Modifier.weight(1f))
    }
}

/** A heading that opens and closes the choices under it; a group without a heading is always open. */
@Composable
internal fun ChoiceGroup(heading: String?, count: Int, initiallyOpen: Boolean, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(initiallyOpen) }
    if (heading != null) {
        Text(
            (if (open) "▾ " else "▸ ") + "$heading ($count)", style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft,
            modifier = Modifier.padding(top = Spacing.S).clickable(role = Role.Button, onClickLabel = if (open) "Close $heading" else "Open $heading") { open = !open },
        )
    }
    if (open || heading == null) Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) { content() }
}
