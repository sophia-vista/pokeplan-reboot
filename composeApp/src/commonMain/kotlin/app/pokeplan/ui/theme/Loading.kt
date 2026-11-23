package app.pokeplan.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.screens.LocalReduceMotion

/**
 * The one loading vocabulary of the app (DESIGN_SYSTEM 5.6 and 6.10). Loading is three pixels, never a spinner or a
 * shimmer: a key or a short wait shows [LoadingDots]; a panel, list or picture window that is not read yet shows a
 * static [Skeleton] of its own shape; a full screen that cannot draw anything else yet uses [LoadingScreen]; a
 * destructive save swap that must block the page uses [LoadingOverlay]. Everything here is tokens and text only, so it
 * renders before any sprite, save or scene is ready.
 */

/** One step of the three loading pixels. */
private const val DOT_STEP_MS = 300L

/**
 * Indeterminate progress: three pixels with the current one lit. It steps from the frame clock's own times, not from an
 * animation tween: a tween's length is scaled by the phone's animation setting (down to nothing when animations are off),
 * so the phase comes straight from frame time and the pixels always step while frames are produced. [LocalReduceMotion]
 * and a phone with animations off hold all three solid instead (5.6), so nothing loops for a player who asked motion to
 * stop.
 */
@Composable
fun LoadingDots(modifier: Modifier = Modifier, color: Color = Pixel.InkSoft, dot: Dp = 6.dp, gap: Dp = 4.dp, description: String? = "Working") {
    val calm = LocalReduceMotion.current
    var phase by remember { mutableIntStateOf(0) }
    var moving by remember { mutableStateOf(true) }
    if (!calm) LaunchedEffect(Unit) {
        moving = (coroutineContext[MotionDurationScale.Key]?.scaleFactor ?: 1f) > 0f
        if (!moving) return@LaunchedEffect
        var start = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (start == 0L) start = now
            phase = (((now - start) / 1_000_000L / DOT_STEP_MS) % 3L).toInt()
        }
    }
    val shown = if (calm || !moving) 3 else phase
    Row(
        if (description == null) modifier else modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { i -> Box(Modifier.size(dot).background(if (shown == 3 || shown == i) color else color.copy(alpha = 0.3f))) }
    }
}

/**
 * A static pixel placeholder with the shape of content that is not read yet. It never shimmers or pulses (12A.5); it
 * only holds the layout so the page does not jump when the real content arrives. [fill] is a fraction of the width the
 * caller gives it; pass a width on [modifier] for a fixed tile.
 */
@Composable
fun Skeleton(modifier: Modifier = Modifier, height: Dp = 16.dp, fill: Float? = null) {
    Box(
        modifier
            .let { if (fill == null) it else it.fillMaxWidth(fill) }
            .height(height)
            .pixelFrame(Pixel.Shade, Pixel.Border)
            .clip(PixelShape)
            .clearAndSetSemantics {},
    )
}

/** One line of skeleton text, [fraction] of the row wide. */
@Composable
fun SkeletonLine(modifier: Modifier = Modifier, fraction: Float = 1f, height: Dp = 12.dp) {
    Skeleton(modifier.fillMaxWidth(fraction), height = height)
}

/** A paragraph of skeleton lines; the last one is short, the way a line of text ends. */
@Composable
fun SkeletonText(modifier: Modifier = Modifier, lines: Int = 3, height: Dp = 12.dp) {
    Column(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "Loading" },
        verticalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        repeat(lines) { i -> SkeletonLine(fraction = if (i == lines - 1 && lines > 1) 0.6f else 1f, height = height) }
    }
}

/** One list row's shape: a square for the icon or sprite and two short lines beside it. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier, leading: Dp = Sizes.Icon) {
    Row(
        modifier.fillMaxWidth().padding(vertical = Spacing.XS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        Skeleton(Modifier.size(leading), height = leading)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            SkeletonLine(fraction = 1f, height = 12.dp)
            SkeletonLine(fraction = 0.55f, height = 12.dp)
        }
    }
}

/** [count] list rows while a list is read: the one node TalkBack hears is "Loading". */
@Composable
fun SkeletonRows(modifier: Modifier = Modifier, count: Int = 3, leading: Dp = Sizes.Icon) {
    Column(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "Loading" },
        verticalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        repeat(count) { SkeletonRow(leading = leading) }
    }
}

/** A framed block the size of a card, a picture window or a summary that is not read yet. */
@Composable
fun SkeletonPanel(modifier: Modifier = Modifier, height: Dp = 96.dp) {
    Skeleton(modifier.fillMaxWidth(), height = height)
}

/** A short wait inside a panel or beside a heading: three pixels and one line of text. */
@Composable
fun LoadingState(text: String, modifier: Modifier = Modifier, color: Color = Pixel.InkSoft) {
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LoadingDots(color = color)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/**
 * A whole screen that has nothing but tokens and words yet: the app opening, a save being checked. [progress] turns the
 * pixels into a determinate [PixelBar] when the wait has a known amount. No sprite, save or scene is touched, so this is
 * the first frame the app can draw.
 */
@Composable
fun LoadingScreen(title: String, modifier: Modifier = Modifier, status: String? = null, progress: Float? = null, detail: String? = null) {
    Box(modifier.fillMaxSize().background(Pixel.Paper), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 320.dp).padding(Spacing.Page)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            PixelTitle(title)
            if (progress != null) PixelBar(progress, Modifier.fillMaxWidth(), Pixel.Blue, label = status) else LoadingDots()
            if (status != null) Text(status, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Pixel.InkSoft)
        }
    }
}

/**
 * Blocks the page while a save is being replaced (restoring a copy): the scrim swallows every tap so nothing can be
 * written over the copy being put back, the panel says what is running, and TalkBack announces it politely.
 */
@Composable
fun LoadingOverlay(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().background(Pixel.Scrim.copy(alpha = 0.55f)).pointerInput(Unit) { detectTapGestures { } }
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.dialogueBox().clip(DialogueShape).padding(horizontal = Spacing.BoxX, vertical = Spacing.BoxY),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LoadingDots()
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
