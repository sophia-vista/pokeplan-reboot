package app.pokeplan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.Spacing

/** One entry of a tab's menu: a real item icon, a title, a line of detail, and an optional count badge. */
data class MenuEntry(val key: String, val icon: String, val title: String, val detail: String, val badge: Int = 0, val tint: Color = Pixel.Blue)

private val menuShape = RoundedCornerShape(16.dp)

/** The menu of a tab: big cards, two to a row, like the start menu of the games. */
@Composable
fun MenuGrid(entries: List<MenuEntry>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { e -> MenuCard(e, Modifier.weight(1f)) { onOpen(e.key) } }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MenuCard(entry: MenuEntry, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .background(Pixel.Panel, menuShape)
            .border(1.dp, Pixel.Border, menuShape)
            .clip(menuShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(72.dp).background(entry.tint.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                ItemIcon(entry.icon, size = 56.dp, description = null)
            }
            Text(entry.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(entry.detail, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, maxLines = 2)
        }
        if (entry.badge > 0) {
            Box(Modifier.align(Alignment.TopEnd).size(26.dp).background(Pixel.Red, CircleShape), contentAlignment = Alignment.Center) {
                Text("${entry.badge}", style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
        }
    }
}

/**
 * A screen reached from a menu: the header with Back at its start, and scrolling content. The system back gesture also
 * goes back. [subtitle] is where the section sits ("Planner"), so the way back is never something to remember.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SectionScaffold(
    title: String, subtitle: String?, onBack: () -> Unit, icon: String? = null, color: Color = Pixel.Red, content: @Composable () -> Unit,
) {
    BackHandler(enabled = true, onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, subtitle = subtitle, icon = icon, onBack = onBack, color = color, trailing = { if (icon != null) ItemIcon(icon, size = 36.dp, description = null) })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) { content() }
    }
}
