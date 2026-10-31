package app.pokeplan.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.theme.DialogueShape
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.dialogueBox
import app.pokeplan.ui.theme.frameToneOf
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.focusRing

/** One entry of a tab's menu: a real item icon, a title, a line of detail, and an optional count badge. */
data class MenuEntry(val key: String, val icon: String, val title: String, val detail: String, val badge: Int = 0, val tint: Color = Pixel.Blue)

private val menuShape = DialogueShape

/** The menu of a tab: big cards, two to a row, like the start menu of the games. */
@Composable
fun MenuGrid(entries: List<MenuEntry>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M), modifier = Modifier.fillMaxWidth()) {
                // A lone last card takes the whole row, laid out sideways, instead of leaving a gap beside it.
                row.forEach { e -> if (row.size == 1) MenuCard(e, Modifier.fillMaxWidth(), wide = true) { onOpen(e.key) } else MenuCard(e, Modifier.weight(1f)) { onOpen(e.key) } }
            }
        }
    }
}

@Composable
private fun MenuCard(entry: MenuEntry, modifier: Modifier, wide: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .dialogueBox(tone = frameToneOf(entry.tint))
            .clip(menuShape)
            .focusRing(menuShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.M, vertical = Spacing.L),
    ) {
        val picture = @Composable {
            Box(Modifier.size(72.dp).background(entry.tint.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                ItemIcon(entry.icon, size = 56.dp, description = null)
            }
        }
        if (wide) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.L)) {
            picture()
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                PixelTitle(entry.title)
                Text(entry.detail, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
        } else Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            picture()
            PixelTitle(entry.title)
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

/** The Reduce motion setting, for screens that move without being handed the model; `App` provides it. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Ordinary navigation, inside the 160 to 240 ms of docs/ecosystem.md, section 11; game moments take longer on their own. */
private const val FADE_MS = 180
private const val SLIDE_MS = 220

/**
 * Switches between screens with the transitions of docs/ecosystem.md, section 11: going deeper ([depth] grows: into a
 * module, a section or a detail) slides a short way in from the end, and coming back slides out the other way; screens
 * at the same depth (the bottom tabs, the sections of a collection) crossfade. With Reduce motion the switch is instant.
 * Each screen keeps its saved state, such as its scroll position, while another is shown, so coming back finds it where
 * it was. [content] must draw the page it is given, not read the current one, because the old page is still drawn
 * while it leaves.
 */
@Composable
fun <T> ScreenSwitch(target: T, modifier: Modifier = Modifier, depth: (T) -> Int = { 0 }, content: @Composable (T) -> Unit) {
    val reduce = LocalReduceMotion.current
    val saved = rememberSaveableStateHolder()
    AnimatedContent(
        target,
        modifier.fillMaxSize().clipToBounds(),
        transitionSpec = {
            val step = depth(targetState).compareTo(depth(initialState))
            // Every page fills the screen, so there is no size to animate.
            screenTransition(if (reduce) null else step) using null
        },
        label = "screen",
    ) { page ->
        // A string key, so the saved state survives on Android, which keeps it in a Bundle.
        saved.SaveableStateProvider(page.toString()) { content(page) }
    }
}

/** [step] is 1 going deeper, -1 coming back, 0 to the side, null for no motion. */
private fun screenTransition(step: Int?): ContentTransform {
    if (step == null) return EnterTransition.None togetherWith ExitTransition.None
    if (step == 0) return fadeIn(tween(FADE_MS)) togetherWith fadeOut(tween(FADE_MS))
    // A short slide, an eighth of the width, under a fade: enough to say which way, never a whole page sweeping past.
    val slide = tween<IntOffset>(SLIDE_MS, easing = FastOutSlowInEasing)
    val enter = slideInHorizontally(slide) { w -> step * w / 8 } + fadeIn(tween(SLIDE_MS))
    val exit = slideOutHorizontally(slide) { w -> -step * w / 8 } + fadeOut(tween(SLIDE_MS / 2))
    return enter togetherWith exit
}
