package app.pokeplan.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Why the launcher's Pokémon went tile by tile (0.221.0): the walk set the ghost fade with `fade.animateTo(1f, tween(160))` before every step, and an
 * animation to the value an Animatable already has still waits out its whole time. The walk now sets the fade beside the step and only when it changes
 * (`Town.kt`, `roamFloor`); this keeps the fact on record.
 */
class AnimateToSameValueTest {
    private class Clock : MonotonicFrameClock {
        var nanos = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R { nanos += 16_000_000L; return onFrame(nanos) }
    }

    @Test
    fun animatingToTheValueItAlreadyHasStillTakesItsTime() {
        val clock = Clock()
        runBlocking { withContext(clock) { Animatable(1f).animateTo(1f, tween(160)) } }
        assertTrue(clock.nanos >= 160_000_000L, "an animation to the same value took ${clock.nanos / 1_000_000} ms of frames")
    }
}
