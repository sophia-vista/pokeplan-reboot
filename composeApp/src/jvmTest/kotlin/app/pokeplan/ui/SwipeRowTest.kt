package app.pokeplan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.screens.SwipeRow
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
class SwipeRowTest {
    /** Drags one finger across the row from x = 480 by [dx] px (negative is left) and returns how many times the row acted. */
    private fun drag(dx: Float): Int {
        var acted = 0
        val scene = ImageComposeScene(width = 540, height = 300, density = Density(1.5f, 1f)) {
            PixelTheme { Box(Modifier.fillMaxWidth().background(Pixel.Paper)) { SwipeRow({ acted++ }, "Delete entry", reduceMotion = true) { Box(Modifier.fillMaxWidth().height(64.dp)) } } }
        }
        try {
            var nanos = 0L
            fun frame() { scene.render(nanos); nanos += 16_000_000 }
            frame()
            var x = 480f
            val y = 48f
            scene.sendPointerEvent(PointerEventType.Press, Offset(x, y), type = PointerType.Touch); frame()
            val steps = 25
            repeat(steps) {
                x += dx / steps
                scene.sendPointerEvent(PointerEventType.Move, Offset(x, y), type = PointerType.Touch); frame()
            }
            scene.sendPointerEvent(PointerEventType.Release, Offset(x, y), type = PointerType.Touch); frame()
            repeat(5) { frame() }
        } finally { scene.close() }
        return acted
    }

    @Test
    fun aLongSwipeLeftActsOnce() = assertEquals(1, drag(-300f))

    @Test
    fun aShortSwipeLeftLetsGo() = assertEquals(0, drag(-70f))

    @Test
    fun aSwipeRightDoesNothing() = assertEquals(0, drag(300f))
}
