package app.pokeplan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.pokeplan.plan.Rank
import app.pokeplan.ui.theme.*
import app.pokeplan.ui.screens.TabButton
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ImageInfo
import kotlin.test.Test

@OptIn(ExperimentalComposeUiApi::class)
/**
 * Measures, on a rendered image, whether text is centred by its letters in the box it sits in (the owner saw labels sitting low and
 * asked for it to be computed): the gap above the ink and below it must differ by no more than 1 dp. The Pokémon DP font sits low in
 * its line box and the dialogue bitmap font a pixel high, which `PixelTheme` and `PmdFont.BASELINE` correct.
 */
class TextCentreTest {
    private fun measure(name: String, face: Color, content: @Composable () -> Unit) {
        // (at 2x density: 1 dp is 2 px)
        val scene = ImageComposeScene(width = 400, height = 160, density = Density(2f, 1f)) {
            PixelTheme { Box(Modifier.background(Color.Magenta).size(200.dp, 80.dp), contentAlignment = Alignment.Center) { content() } }
        }
        var n = 0L
        repeat(40) { scene.render(n); n += 50_000_000; Thread.sleep(15) }
        val img = scene.render(n)
        val bmp = Bitmap.makeFromImage(img)
        val w = bmp.width; val h = bmp.height
        fun px(x: Int, y: Int) = bmp.getColor(x, y)
        val mag = 0xFFFF00FF.toInt()
        // frame bbox: all non-magenta
        var x0 = w; var x1 = -1; var y0 = h; var y1 = -1
        for (y in 0 until h) for (x in 0 until w) if (px(x, y) != mag) { x0 = minOf(x0, x); x1 = maxOf(x1, x); y0 = minOf(y0, y); y1 = maxOf(y1, y) }
        val faceInt = (face.value shr 32).toInt().let { 0xFF000000.toInt() or ((face.red * 255).toInt() shl 16) or ((face.green * 255).toInt() shl 8) or (face.blue * 255).toInt() }
        // text bbox: pixels inside the frame (inset 8 px for border) that are not the face colour
        var tx0 = w; var tx1 = -1; var ty0 = h; var ty1 = -1
        val inset = 8
        for (y in y0 + inset..y1 - inset) for (x in x0 + inset..x1 - inset) {
            val c = px(x, y)
            val dr = Math.abs(((c shr 16) and 255) - ((faceInt shr 16) and 255)); val dg = Math.abs(((c shr 8) and 255) - ((faceInt shr 8) and 255)); val db = Math.abs((c and 255) - (faceInt and 255))
            if (dr + dg + db > 90) { tx0 = minOf(tx0, x); tx1 = maxOf(tx1, x); ty0 = minOf(ty0, y); ty1 = maxOf(ty1, y) }
        }
        val topGap = ty0 - y0; val bottomGap = y1 - ty1
        kotlin.test.assertTrue(Math.abs(topGap - bottomGap) / 2.0 <= 2.0, "$name: top=$topGap bottom=$bottomGap (px at 2x); text is not centred")
    }

    @Test
    fun textIsCentredByItsLetters() {
        for ((n, pick) in listOf<Pair<String, @Composable () -> androidx.compose.ui.text.TextStyle>>(
            "bodyLarge16" to { androidx.compose.material3.MaterialTheme.typography.bodyLarge }, "bodyMedium14" to { androidx.compose.material3.MaterialTheme.typography.bodyMedium },
            "titleSmall18" to { androidx.compose.material3.MaterialTheme.typography.titleSmall }, "titleLarge24" to { androidx.compose.material3.MaterialTheme.typography.titleLarge },
            "labelMedium14" to { androidx.compose.material3.MaterialTheme.typography.labelMedium },
        )) measure("T-$n", Pixel.Panel) { Box(Modifier.size(120.dp, 48.dp).background(Pixel.Panel), contentAlignment = Alignment.Center) { androidx.compose.material3.Text("Value Hod", style = pick(), color = Pixel.Ink) } }
        measure("button", Pixel.Panel) { PixelButton("Done", {}, color = Pixel.Blue) }
        measure("button-compact", Pixel.Panel) { PixelButton("Done", {}, color = Pixel.Blue, compact = true) }
        measure("tab", Pixel.Panel) { TabButton("Week", false, {}) }
        measure("rank", rankColor(Rank.C)) { RankBadge(Rank.C) }
        measure("field", Pixel.Panel) { PixelField("Value", {}, Modifier.padding(0.dp)) }
        measure("field-text", Pixel.Panel) { PixelField("1000", {}, singleLine = true) }
    }
}
