package app.pokeplan.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.jetbrains.compose.resources.decodeToImageBitmap
import pokeplan.composeapp.generated.resources.Res
import kotlin.math.roundToInt

private val cache = mutableMapOf<String, ImageBitmap?>()

/**
 * A real game box wallpaper tiled behind [content], scaled by a whole number so the pixels stay sharp.
 * [name] is a file in assets/backgrounds without the extension ("bw01").
 */
@Composable
fun PixelBackground(name: String, modifier: Modifier = Modifier, scale: Int = 3, content: @Composable BoxScope.() -> Unit) {
    val bitmap by produceState(cache[name], name) {
        if (value == null) {
            value = try {
                Res.readBytes("files/backgrounds/$name.png").decodeToImageBitmap()
            } catch (e: Exception) {
                null
            }
            cache[name] = value
        }
    }
    Box(
        modifier.drawBehind {
            val image = bitmap ?: return@drawBehind
            val step = (scale * density).roundToInt().coerceAtLeast(1)
            // The wallpapers carry a 2 pixel frame; drop it so the tiles run together, and mirror every other tile.
            val inner = IntSize(image.width - 2 * FRAME, image.height - 2 * FRAME)
            val tileW = inner.width * step
            val tileH = inner.height * step
            var row = 0
            var y = 0
            while (y < size.height) {
                var column = 0
                var x = 0
                while (x < size.width) {
                    withTransform({
                        translate(x.toFloat(), y.toFloat())
                        scale(if (column % 2 == 0) 1f else -1f, if (row % 2 == 0) 1f else -1f, Offset(tileW / 2f, tileH / 2f))
                    }) {
                        drawImage(
                            image, srcOffset = IntOffset(FRAME, FRAME), srcSize = inner,
                            dstOffset = IntOffset.Zero, dstSize = IntSize(tileW, tileH), filterQuality = FilterQuality.None,
                        )
                    }
                    x += tileW
                    column++
                }
                y += tileH
                row++
            }
        },
        content = content,
    )
}

private const val FRAME = 2
