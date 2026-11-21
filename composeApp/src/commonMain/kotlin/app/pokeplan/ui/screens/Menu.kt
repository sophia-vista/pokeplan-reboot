package app.pokeplan.ui.screens

import app.pokeplan.ui.theme.Sizes
import androidx.compose.runtime.remember
import app.pokeplan.ui.pressScale
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import app.pokeplan.ui.BackHandler
import androidx.compose.ui.draw.alpha
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
import app.pokeplan.ui.pokemon.label

data class MenuEntry(val key: String, val icon: String, val title: String, val detail: String = "", val badge: Int = 0, val tint: Color = Pixel.Blue,
    val pending: Boolean = false,
    val sound: app.pokeplan.ui.Sfx = app.pokeplan.ui.Sfx.CURSOR,
    val wide: Boolean = false,
    val count: String = "",
)

private val menuShape = DialogueShape

@Composable
fun MenuGrid(entries: List<MenuEntry>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        val rows = mutableListOf<List<MenuEntry>>()
        var pair = mutableListOf<MenuEntry>()
        entries.forEach { e ->
            if (e.wide) { if (pair.isNotEmpty()) rows += pair.also { pair = mutableListOf() }; rows += listOf(e) }
            else { pair += e; if (pair.size == 2) rows += pair.also { pair = mutableListOf() } }
        }
        if (pair.isNotEmpty()) rows += pair
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M), modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                row.forEach { e -> if (row.size == 1) MenuCard(e, Modifier.fillMaxWidth(), wide = true) { onOpen(e.key) } else MenuCard(e, Modifier.weight(1f)) { onOpen(e.key) } }
            }
        }
    }
}

@Composable
fun MenuList(entries: List<MenuEntry>, onOpen: (String) -> Unit) {
    app.pokeplan.ui.theme.TextBox { MenuRows(entries, onOpen) }
}

@Composable
fun MenuRows(entries: List<MenuEntry>, onOpen: (String) -> Unit) {
    val ui = app.pokeplan.ui.LocalUiSound.current
    Column {
        entries.forEachIndexed { i, e ->
            if (i > 0) androidx.compose.material3.HorizontalDivider(color = Pixel.Border)
            Row(
                Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)
                    .clickable(role = Role.Button, enabled = !e.pending) { ui(e.sound); onOpen(e.key) }
                    .semantics(mergeDescendants = true) { contentDescription = listOf(e.title, e.count).filter { it.isNotEmpty() }.joinToString(", ") }
                    .padding(vertical = Spacing.M),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M),
            ) {
                ItemIcon(e.icon, size = Sizes.Icon, description = null)
                Text(e.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                if (e.badge > 0) Box(Modifier.size(22.dp).background(Pixel.Red, DialogueShape), contentAlignment = Alignment.Center) {
                    Text("${e.badge}", style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
                if (e.count.isNotEmpty()) Text(e.count, style = MaterialTheme.typography.bodyLarge, color = Pixel.InkSoft)
            }
        }
    }
}

@Composable
private fun MenuCard(entry: MenuEntry, modifier: Modifier, wide: Boolean = false, onClick: () -> Unit) {
    val ui = app.pokeplan.ui.LocalUiSound.current
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier
            .let { if (entry.pending) it.alpha(0.5f) else it.pressScale(source) }
            .dialogueBox(tone = frameToneOf(entry.tint))
            .clip(menuShape)
            .focusRing(menuShape)
            .clickable(interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Button, enabled = !entry.pending) { ui(entry.sound); onClick() }
            .semantics { if (entry.pending) contentDescription = entry.title + ", coming later" else if (entry.detail.isNotEmpty()) contentDescription = entry.title + ", " + entry.detail }
            .padding(horizontal = Spacing.XL, vertical = Spacing.XL),
    ) {
        val picture = @Composable {
            Box(Modifier.size(Sizes.SpriteM).background(entry.tint.copy(alpha = 0.12f), app.pokeplan.ui.theme.PixelDisc), contentAlignment = Alignment.Center) {
                ItemIcon(entry.icon, size = Sizes.SpriteS, description = null)
            }
        }
        if (wide) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.L)) {
            picture()
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                PixelTitle(entry.title)
            }
        } else Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            picture()
            PixelTitle(entry.title)
        }
        if (entry.badge > 0) {
            Box(Modifier.align(Alignment.TopEnd).size(26.dp).background(Pixel.Red, DialogueShape), contentAlignment = Alignment.Center) {
                Text("${entry.badge}", style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
        }
    }
}

/**
 * The page one level below the section now shown, if any. A section can open a page of its own (a town's facility, a journal
 * page); while it is open this holds its Back, so the header's Back key and the system Back gesture go up one level at a time
 * instead of leaving the section (and losing the draft or the place).
 */
internal class UpSlot { var up: (() -> Unit)? by mutableStateOf(null) }
private val LocalUpSlot = staticCompositionLocalOf<UpSlot?> { null }

/** The slot for the page one level below the screen now shown; [UpProvider] gives it to the content. */
@Composable
internal fun rememberUpSlot(): UpSlot = remember { UpSlot() }

/** Hands [slot] to the content, so a page inside it can register its Back with [UpTo]. */
@Composable
internal fun UpProvider(slot: UpSlot, content: @Composable () -> Unit) = CompositionLocalProvider(LocalUpSlot provides slot) { content() }

/**
 * Makes [onUp] the screen's one-level-up while this page is shown; the Back that the screen draws and its system Back
 * gesture run it, then the screen's own Back once the page has gone. Call it from the page's own composable, next to the
 * content it belongs to.
 */
@Composable
fun UpTo(onUp: () -> Unit) {
    val slot = LocalUpSlot.current ?: return
    val current by rememberUpdatedState(onUp)
    DisposableEffect(slot) {
        slot.up = { current() }
        onDispose { slot.up = null }
    }
}

/**
 * A screen reached from a menu: the header with Back at its start, and scrolling content. The system back gesture also
 * goes back, and so does a page opened inside the section ([UpTo]). [subtitle] is where the section sits ("Planner"), so
 * the way back is never something to remember.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SectionScaffold(
    title: String, subtitle: String?, onBack: () -> Unit, icon: String? = null, color: Color = Pixel.Chrome,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val slot = rememberUpSlot()
    val back = { slot.up?.invoke() ?: onBack() }
    BackHandler(enabled = true, onBack = back)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, subtitle = subtitle, icon = icon, onBack = back, color = color, trailing = trailing ?: { if (icon != null) ItemIcon(icon, size = 36.dp, description = null) })
        UpProvider(slot) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.L),
                verticalArrangement = Arrangement.spacedBy(Spacing.M),
            ) { content() }
        }
    }
}

val LocalReduceMotion = staticCompositionLocalOf { false }

val LocalBuddy = staticCompositionLocalOf<app.pokeplan.core.pokemon.OwnedPokemon?> { null }

private const val FADE_MS = 180
private const val SLIDE_MS = 220

@Composable
fun <T> ScreenSwitch(target: T, modifier: Modifier = Modifier, depth: (T) -> Int = { 0 }, content: @Composable (T) -> Unit) {
    val reduce = LocalReduceMotion.current
    val saved = rememberSaveableStateHolder()
    AnimatedContent(
        target,
        modifier.fillMaxSize().clipToBounds(),
        transitionSpec = {
            val step = depth(targetState).compareTo(depth(initialState))
            screenTransition(if (reduce) null else step) using null
        },
        label = "screen",
    ) { page ->
        saved.SaveableStateProvider(page.toString()) { content(page) }
    }
}

private fun screenTransition(step: Int?): ContentTransform {
    if (step == null) return EnterTransition.None togetherWith ExitTransition.None
    if (step == 0) return fadeIn(tween(FADE_MS)) togetherWith fadeOut(tween(FADE_MS))
    val slide = tween<IntOffset>(SLIDE_MS, easing = app.pokeplan.ui.Ease.Smooth)
    val enter = slideInHorizontally(slide) { w -> step * w / 8 } + fadeIn(tween(SLIDE_MS))
    val exit = slideOutHorizontally(slide) { w -> -step * w / 8 } + fadeOut(tween(SLIDE_MS / 2))
    return enter togetherWith exit
}
