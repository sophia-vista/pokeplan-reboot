package app.pokeplan.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pokeplan.app.BuddyScene
import app.pokeplan.ui.launcher.TownBackdrop
import app.pokeplan.ui.launcher.TownScenes
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.Spacing

/** The page's background choices: the day gradient, then the real scenes and the dungeon towns. */
val AppBackgrounds: List<Pair<String, String>> = listOf("day" to "Day") + TownScenes.all

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
    val shape = RoundedCornerShape(14.dp)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        AppBackgrounds.chunked(2).forEach { row ->
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
    }
}
