package app.pokeplan.ui.media

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class PhotoViewerEngineTest {
    private fun item(id: String) = MediaItem(id, MediaSources.bytes(byteArrayOf(1)))

    @Test
    fun controllerOpensClampsTheStartAndCloses() {
        val c = PhotoViewerController()
        assertFalse(c.isOpen)
        c.open(listOf(item("a"), item("b"), item("c")), initialIndex = 9)
        assertEquals(2, c.session!!.index)
        c.open(listOf(item("a")), initialIndex = -3)
        assertEquals(0, c.session!!.index)
        c.close()
        assertNull(c.session)
    }

    @Test
    fun anEmptyCollectionOpensWithNothingOnShow() {
        val c = PhotoViewerController()
        c.open(emptyList())
        assertNull(c.session!!.current)
        assertFalse(c.session!!.hasNext)
    }

    @Test
    fun pagesTurnAndThePanelAndControlsToggle() {
        val s = PhotoViewerState(listOf(item("a"), item("b")), 0, PhotoViewerConfig(fitMode = ImageFitMode.FILL_CENTER))
        assertEquals(ImageFitMode.FILL_CENTER, s.fit)
        s.markZoomed(true)
        s.notice = "sent"
        s.show(1)
        assertEquals(1, s.index)
        assertFalse(s.zoomed)
        assertNull(s.notice)
        assertFalse(s.hasNext)
        s.toggleInfo()
        assertTrue(s.backClosesInfo())
        assertFalse(s.backClosesInfo())
        s.toggleControls()
        assertFalse(s.controlsVisible)
        s.toggleFit()
        assertEquals(ImageFitMode.FIT_CENTER, s.fit)
    }

    @Test
    fun fitContainsAndFillCovers() {
        assertEquals(0.5f, ViewerMath.baseScale(100f, 200f, 200f, 200f, ImageFitMode.FIT_CENTER))
        assertEquals(1f, ViewerMath.baseScale(100f, 200f, 200f, 200f, ImageFitMode.FILL_CENTER))
        assertEquals(1f, ViewerMath.baseScale(0f, 0f, 10f, 10f, ImageFitMode.FIT_CENTER))
    }

    @Test
    fun aQuarterTurnSwapsTheSidesAndFillCoversExactlyTheViewport() {
        // A 200 x 100 picture in a 100 x 100 viewport: fit draws 100 x 50; turned a quarter it draws 50 x 100.
        assertEquals(100f to 50f, ViewerMath.drawnSize(100f, 100f, 200f, 100f, ImageFitMode.FIT_CENTER, 0))
        assertEquals(50f to 100f, ViewerMath.drawnSize(100f, 100f, 200f, 100f, ImageFitMode.FIT_CENTER, 1))
        assertEquals(100f to 50f, ViewerMath.drawnSize(100f, 100f, 200f, 100f, ImageFitMode.FIT_CENTER, 2))
        assertEquals(100f to 100f, ViewerMath.drawnSize(100f, 100f, 200f, 100f, ImageFitMode.FILL_CENTER, 1))
    }

    @Test
    fun rotationStartsFromTheStoredTurnAndIsKeptPerPicture() {
        val a = item("a").copy(rotation = 3)
        val b = item("b").copy(rotation = -1)
        val s = PhotoViewerState(listOf(a, b), 0, PhotoViewerConfig())
        assertEquals(3, s.rotationOf(a))
        assertEquals(3, s.rotationOf(b))
        assertEquals(0, s.rotate(a))
        assertEquals(1, s.rotate(a))
        s.show(1)
        s.show(0)
        assertEquals(1, s.rotationOf(a))
        assertEquals(3, s.rotationOf(b))
    }

    @Test
    fun aPictureNeverLeavesTheViewportEdge() {
        val z = ZoomState()
        z.transform(0f, 0f, 2f, 5000f, -5000f, 100f, 100f, 100f, 100f)
        assertEquals(2f, z.scale)
        assertEquals(50f, z.tx)
        assertEquals(-50f, z.ty)
        assertTrue(z.zoomed)
    }

    @Test
    fun zoomingAboutAPointKeepsThatPointStill() {
        val z = ZoomState()
        z.transform(40f, 0f, 2f, 0f, 0f, 400f, 400f, 100f, 100f)
        // The point 40 right of centre stays 40 right of centre: tx = 40 - 2 * 40 = -40.
        assertEquals(-40f, z.tx)
    }

    @Test
    fun pinchingBackOutResets() {
        val z = ZoomState()
        z.transform(10f, 10f, 3f, 0f, 0f, 400f, 400f, 100f, 100f)
        z.transform(10f, 10f, 0.2f, 0f, 0f, 400f, 400f, 100f, 100f)
        assertEquals(1f, z.scale)
        assertEquals(0f, z.tx)
        assertFalse(z.zoomed)
    }

    @Test
    fun scaleStopsAtTheMaximumAndDoubleTapToggles() {
        val z = ZoomState()
        z.transform(0f, 0f, 100f, 0f, 0f, 400f, 400f, 100f, 100f)
        assertEquals(ViewerMath.MAX_SCALE, z.scale)
        z.toggle(0f, 0f, 400f, 400f, 100f, 100f)
        assertFalse(z.zoomed)
        z.toggle(0f, 0f, 400f, 400f, 100f, 100f)
        assertEquals(ViewerMath.DOUBLE_TAP_SCALE, z.scale)
    }

    @Test
    fun base64SourcesReadOrReportUnavailable() = runBlocking {
        assertNull(MediaSources.base64("").read())
        assertNull(MediaSources.base64("%%%").read())
        assertEquals(listOf<Byte>(1, 2, 3), MediaSources.base64("AQID").read()!!.toList())
    }

    /** A 2 x 1 picture, red then blue, turned clockwise: the cell each colour lands in is the proof. */
    @Test
    fun turnedMovesPixelsClockwise() {
        val src = ImageBitmap(2, 1)
        androidx.compose.ui.graphics.Canvas(src).apply {
            drawRect(androidx.compose.ui.geometry.Rect(0f, 0f, 1f, 1f), androidx.compose.ui.graphics.Paint().apply { color = androidx.compose.ui.graphics.Color.Red })
            drawRect(androidx.compose.ui.geometry.Rect(1f, 0f, 2f, 1f), androidx.compose.ui.graphics.Paint().apply { color = androidx.compose.ui.graphics.Color.Blue })
        }
        assertSame(src, src.turned(0))
        assertSame(src, src.turned(4))
        fun at(t: Int): List<androidx.compose.ui.graphics.Color> {
            val out = src.turned(t)
            val m = out.toPixelMap()
            return (0 until out.height).flatMap { y -> (0 until out.width).map { x -> m[x, y] } }
        }
        val r = androidx.compose.ui.graphics.Color.Red
        val b = androidx.compose.ui.graphics.Color.Blue
        assertEquals(listOf(r, b), at(1))
        assertEquals(listOf(b, r), at(2))
        assertEquals(listOf(b, r), at(3))
        assertEquals(listOf(b, r), at(-1))
        assertEquals(1, src.turned(1).width)
        assertEquals(2, src.turned(1).height)
    }
}
