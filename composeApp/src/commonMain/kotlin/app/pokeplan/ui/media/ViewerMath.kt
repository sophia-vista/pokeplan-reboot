package app.pokeplan.ui.media

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.max
import kotlin.math.min

/** The geometry of zooming and panning, free of Compose so it is tested on its own. */
object ViewerMath {
    const val MAX_SCALE = 8f
    const val DOUBLE_TAP_SCALE = 2.5f

    /** Above this the picture counts as zoomed in, so a drag pans it instead of turning the page. */
    const val ZOOMED = 1.01f

    /** The factor that draws an [iw] x [ih] picture inside a [vw] x [vh] viewport: contained for fit, covering for fill. */
    fun baseScale(vw: Float, vh: Float, iw: Float, ih: Float, fit: ImageFitMode): Float {
        if (vw <= 0f || vh <= 0f || iw <= 0f || ih <= 0f) return 1f
        val across = vw / iw
        val down = vh / ih
        return if (fit == ImageFitMode.FIT_CENTER) min(across, down) else max(across, down)
    }

    /** How far a picture [content] long, drawn at [scale], may slide either way inside a [viewport]. */
    fun limit(scale: Float, viewport: Float, content: Float): Float = max(0f, (content * scale - viewport) / 2f)

    fun clampOffset(offset: Float, scale: Float, viewport: Float, content: Float): Float {
        val l = limit(scale, viewport, content)
        return offset.coerceIn(-l, l)
    }
}

/** Zoom and pan of one picture. The offset is the slide of its centre; the scale grows about the viewport centre. */
class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var tx by mutableFloatStateOf(0f)
        private set
    var ty by mutableFloatStateOf(0f)
        private set

    val zoomed: Boolean get() = scale > ViewerMath.ZOOMED

    /**
     * One pinch or drag step: scale by [zoom] about the point ([fx], [fy]) measured from the viewport centre, then slide by ([panX], [panY]).
     * [cw] x [ch] is the picture's drawn size at scale 1 and [vw] x [vh] the viewport. The picture never leaves the viewport edge.
     */
    fun transform(fx: Float, fy: Float, zoom: Float, panX: Float, panY: Float, cw: Float, ch: Float, vw: Float, vh: Float) {
        val next = (scale * zoom).coerceIn(1f, ViewerMath.MAX_SCALE)
        if (next <= ViewerMath.ZOOMED) {
            reset()
            return
        }
        val ratio = next / scale
        val x = fx - ratio * (fx - tx) + panX
        val y = fy - ratio * (fy - ty) + panY
        scale = next
        tx = ViewerMath.clampOffset(x, next, vw, cw)
        ty = ViewerMath.clampOffset(y, next, vh, ch)
    }

    /** Double tap and the Zoom key: out when zoomed in, in about the tapped point when not. */
    fun toggle(fx: Float, fy: Float, cw: Float, ch: Float, vw: Float, vh: Float) {
        if (zoomed) reset() else transform(fx, fy, ViewerMath.DOUBLE_TAP_SCALE, 0f, 0f, cw, ch, vw, vh)
    }

    fun reset() {
        scale = 1f
        tx = 0f
        ty = 0f
    }
}
