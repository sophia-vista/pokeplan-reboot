package app.pokeplan.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/**
 * Whether the app is in sight (started, not stopped). A loop that only paints or polls for the screen should run while this is true:
 * a stopped Activity keeps its composition, so a `while (true)` in a `LaunchedEffect` would otherwise keep waking the CPU in the background.
 */
@Composable
internal fun rememberInForeground(): Boolean {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return state.isAtLeast(Lifecycle.State.STARTED)
}

/** Calls [onChange] with whether this element overlaps the window, whenever that changes, so what it shows can stop moving when it is scrolled out of sight. */
@Composable
internal fun Modifier.onScreen(onChange: (Boolean) -> Unit): Modifier {
    val window = LocalWindowInfo.current.containerSize
    var last by remember { mutableStateOf<Boolean?>(null) }
    return onGloballyPositioned { coordinates ->
        val seen = coordinates.boundsInWindow().overlaps(Rect(0f, 0f, window.width.toFloat(), window.height.toFloat()))
        if (seen != last) { last = seen; onChange(seen) }
    }
}

/**
 * Seconds that pass only while [running]: one frame callback at a time, and none at all when it is false. Read the value in a draw or layout
 * block (`{ seconds.value }`) so a frame redraws or re-lays-out instead of recomposing the screen. It carries on from where it stopped.
 */
@Composable
internal fun rememberRunningSeconds(running: Boolean): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (running) {
            var last = withFrameNanos { it }
            while (true) withFrameNanos { now -> seconds.floatValue += (now - last) / 1_000_000_000f; last = now }
        }
    }
    return seconds
}
