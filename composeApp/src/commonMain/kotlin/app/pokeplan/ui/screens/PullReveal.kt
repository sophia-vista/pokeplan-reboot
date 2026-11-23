package app.pokeplan.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import app.pokeplan.ui.BackHandler
import app.pokeplan.ui.theme.Spacing

/** A hidden-by-default block (search, filters) opens and closes in this long: snappy, and skipped under Reduce motion. */
private const val REVEAL_MS = 140

/**
 * The pull-down that puts away a screen's search or filters until they are wanted (the Modules search
 * is the model). Pull a list down at its top to open; a swipe up, the close key or Back puts it away.
 *
 * Build one with [rememberPullReveal], attach [connection] with `Modifier.nestedScroll`, and draw the
 * block inside a [PullRevealSlot] at the top of the same scrolling column.
 */
@Stable
class PullRevealState internal constructor(private val scroll: ScrollState, private val threshold: Float, private val keepOpen: () -> Boolean, private val onClose: () -> Unit) {
    var open by mutableStateOf(false)
        private set
    private var pulled = 0f

    fun show() { open = true }

    fun close() { open = false; onClose() }

    /** A drag down at the top opens it; a swipe up with nothing typed in it puts it away. Both are consumed so the page does not bounce first. */
    val connection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || scroll.value > 0 || available.y <= 0f) {
                pulled = 0f
                return Offset.Zero
            }
            if (open) return Offset(0f, available.y)
            pulled += available.y
            if (pulled < threshold) return Offset(0f, available.y)
            pulled = 0f
            open = true
            return Offset(0f, available.y)
        }

        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || !open || scroll.value > 0 || keepOpen() || available.y >= 0f) return Offset.Zero
            close()
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            pulled = 0f
            return Velocity.Zero
        }
    }
}

/** [keepOpen] is true while the block holds something the person typed (a swipe up then leaves it); [onClose] clears it. */
@Composable
fun rememberPullReveal(scroll: ScrollState, keepOpen: () -> Boolean = { false }, onClose: () -> Unit = {}): PullRevealState {
    val threshold = with(LocalDensity.current) { Spacing.XXL.toPx() }
    val state = remember(scroll, threshold) { PullRevealState(scroll, threshold, keepOpen, onClose) }
    BackHandler(enabled = state.open, onBack = state::close)
    return state
}

/** The scrolling column's modifier that listens for the pull. */
fun Modifier.pullReveal(state: PullRevealState): Modifier = nestedScroll(state.connection)

/** The hidden block: its bottom gap sits inside the animation, so the list starts at the page inset while it is hidden. */
@Composable
fun PullRevealSlot(state: PullRevealState, reduceMotion: Boolean, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(
        visible = state.open,
        enter = if (reduceMotion) EnterTransition.None else expandVertically(tween(REVEAL_MS), expandFrom = Alignment.Top) + fadeIn(tween(REVEAL_MS)),
        exit = if (reduceMotion) ExitTransition.None else shrinkVertically(tween(REVEAL_MS), shrinkTowards = Alignment.Top) + fadeOut(tween(REVEAL_MS)),
    ) {
        Column(Modifier.padding(bottom = Spacing.M), verticalArrangement = Arrangement.spacedBy(Spacing.M), content = content)
    }
}
