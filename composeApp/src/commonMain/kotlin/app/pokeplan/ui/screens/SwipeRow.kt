package app.pokeplan.ui.screens

import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.Glyph
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.Spacing
import kotlin.math.roundToInt

/** How far a row is dragged left before letting go counts as the action. */
private val REACH = 96.dp

/**
 * A row that a swipe to the left acts on (delete, with an Undo from the caller), so a list row needs no trailing key
 * for it. The swipe is never the only way (DESIGN_SYSTEM 12A): the row also offers [swipeLabel] as a screen-reader
 * action, and the caller keeps a visible key for the same action inside the row's own page. Under Reduce motion the
 * row snaps back instead of sliding.
 */
@Composable
fun SwipeRow(onSwipe: () -> Unit, swipeLabel: String, reduceMotion: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reach = with(LocalDensity.current) { REACH.toPx() }
    var dragging by remember { mutableStateOf(false) }
    var dragged by remember { mutableFloatStateOf(0f) }
    val shown by animateFloatAsState(if (dragging) dragged else 0f, if (dragging || reduceMotion) snap() else tween(120), label = "swipe")
    Box(
        modifier.fillMaxWidth().clipToBounds().semantics { customActions = listOf(CustomAccessibilityAction(swipeLabel) { onSwipe(); true }) },
    ) {
        if (shown < 0f) Box(Modifier.matchParentSize().padding(end = Spacing.M), contentAlignment = Alignment.CenterEnd) {
            Glyph("trash", Pixel.InkSoft, size = 24.dp)
        }
        Box(
            Modifier.offset { IntOffset(shown.roundToInt(), 0) }.draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> dragged = (dragged + delta).coerceIn(-reach * 1.5f, 0f) },
                onDragStarted = { dragging = true },
                onDragStopped = {
                    val hit = dragged <= -reach
                    dragging = false
                    dragged = 0f
                    if (hit) onSwipe()
                },
            ),
        ) { content() }
    }
}
