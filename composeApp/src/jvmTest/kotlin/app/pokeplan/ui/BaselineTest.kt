package app.pokeplan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.theme.PixelTheme
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Two texts of different sizes on one row (a title and its count, a name and its value) must sit on one baseline, not one centre line (the owner saw
 * "Party" and "6 of 6" at different heights on the phone). Measured on a rendered image with capitals that have no descenders: the ink of both ends on
 * the same row, within 1 dp.
 */
@OptIn(ExperimentalComposeUiApi::class)
class BaselineTest {
    private fun bottoms(content: @Composable RowScope.() -> Unit): Pair<Int, Int> {
        val scene = ImageComposeScene(width = 600, height = 160, density = Density(2f, 1f)) {
            PixelTheme { Row(Modifier.fillMaxSize().background(Color.White), verticalAlignment = Alignment.CenterVertically) { content() } }
        }
        var n = 0L
        repeat(30) { scene.render(n); n += 50_000_000; Thread.sleep(10) }
        val bmp = Bitmap.makeFromImage(scene.render(n))
        fun bottom(x0: Int, x1: Int): Int {
            var last = -1
            for (y in 0 until bmp.height) for (x in x0 until x1) { val c = bmp.getColor(x, y); if (((c shr 16) and 255) + ((c shr 8) and 255) + (c and 255) < 300) last = y }
            return last
        }
        return bottom(0, 280) to bottom(300, 580)
    }

    private val pairs = listOf<Pair<(Typography) -> TextStyle, (Typography) -> TextStyle>>(
        ({ t: Typography -> t.titleSmall } to { t: Typography -> t.labelMedium }),
        ({ t: Typography -> t.bodyLarge } to { t: Typography -> t.bodyMedium }),
        // (24 sp titles are centred by their letters alone, TextCentreTest; they are not set on a row with a smaller text.)
    )

    private fun ends(left: (Typography) -> TextStyle, right: (Typography) -> TextStyle, aligned: Boolean) = bottoms {
        val a = if (aligned) Modifier.width(140.dp).alignByBaseline() else Modifier.width(140.dp)
        Text("HEH", a, style = left(MaterialTheme.typography), color = Color.Black)
        Text("HEH", a, style = right(MaterialTheme.typography), color = Color.Black)
    }

    @Test
    fun differentSizesShareABaselineWhenAligned() {
        for ((l, r) in pairs) {
            val (a, b) = ends(l, r, aligned = true)
            println("aligned: left ink ends at $a, right at $b (px at 2x)")
            assertTrue(Math.abs(a - b) / 2.0 <= 1.0, "baseline: left ink ends at $a, right at $b (px at 2x)")
        }
    }
}
