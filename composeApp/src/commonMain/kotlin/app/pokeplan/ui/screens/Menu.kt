package app.pokeplan.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.ScreenHeader

/** One entry of a tab's menu: a real item icon, a title, a line of detail, and an optional count badge. */
data class MenuEntry(val key: String, val icon: String, val title: String, val detail: String, val badge: Int = 0)

private val menuShape = RoundedCornerShape(20.dp)

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
            .shadow(4.dp, menuShape, ambientColor = Pixel.Ink.copy(alpha = 0.25f), spotColor = Pixel.Ink.copy(alpha = 0.25f))
            .background(Pixel.Panel, menuShape)
            .clip(menuShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ItemIcon(entry.icon, size = 64.dp)
            Text(entry.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(entry.detail, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, textAlign = TextAlign.Center, maxLines = 2)
        }
        if (entry.badge > 0) {
            Box(Modifier.align(Alignment.TopEnd).size(26.dp).background(Pixel.Red, CircleShape), contentAlignment = Alignment.Center) {
                Text("${entry.badge}", style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
        }
    }
}

/** A screen reached from a menu: the header with a Back button, and scrolling content. The system back gesture also goes back. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SectionScaffold(title: String, subtitle: String?, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(enabled = true, onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, subtitle = subtitle) { PixelButton("Back", onBack, color = Pixel.Ink, compact = true) }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { content() }
    }
}
