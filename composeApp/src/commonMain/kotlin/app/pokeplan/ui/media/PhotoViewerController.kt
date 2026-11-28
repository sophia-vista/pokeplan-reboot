package app.pokeplan.ui.media

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What one opening of the viewer is showing and where in it the person is. Pure state: nothing here draws or loads. */
class PhotoViewerState(val items: List<MediaItem>, initialIndex: Int, val config: PhotoViewerConfig) {
    var index: Int by mutableIntStateOf(initialIndex.coerceIn(0, maxOf(items.lastIndex, 0)))
        private set

    var controlsVisible: Boolean by mutableStateOf(true)
        private set
    var infoOpen: Boolean by mutableStateOf(false)
        private set
    var fit: ImageFitMode by mutableStateOf(config.fitMode)
        private set

    private val rotations = mutableStateMapOf<String, Int>()

    /** The picture on show is zoomed in, so a drag pans it and the pages do not turn. */
    var zoomed: Boolean by mutableStateOf(false)
        private set

    /** One line of feedback ("Choose where to send it."), cleared when the page changes. */
    var notice: String? by mutableStateOf(null)

    val current: MediaItem? get() = items.getOrNull(index)
    val hasPrevious: Boolean get() = index > 0
    val hasNext: Boolean get() = index < items.lastIndex

    fun show(page: Int) {
        val next = page.coerceIn(0, maxOf(items.lastIndex, 0))
        if (next != index) {
            index = next
            notice = null
        }
        zoomed = false
    }

    /** Quarter turns clockwise a picture is turned: what its module stored ([MediaItem.rotation]) plus what was pressed since. A view only: the file is never touched. */
    fun rotationOf(item: MediaItem): Int = rotations[item.id] ?: (((item.rotation % 4) + 4) % 4)

    /** One more quarter turn of [item]; returns the new total (0 to 3). */
    fun rotate(item: MediaItem): Int = ((rotationOf(item) + 1) % 4).also { rotations[item.id] = it }

    fun markZoomed(on: Boolean) { zoomed = on }

    fun toggleControls() { controlsVisible = !controlsVisible }

    fun toggleInfo() { infoOpen = !infoOpen }

    fun toggleFit() { fit = if (fit == ImageFitMode.FIT_CENTER) ImageFitMode.FILL_CENTER else ImageFitMode.FIT_CENTER }

    /** What Back does: closes the Info panel first, and only then the viewer. True when it closed the panel. */
    fun backClosesInfo(): Boolean = infoOpen.also { if (it) infoOpen = false }
}

/**
 * The one door to the viewer. A module calls [open] with its pictures, where to start and what to offer; it never learns how the
 * viewer works. The viewer is a dialog over the module's own screen, so Back returns to exactly the screen and scroll position it left.
 */
class PhotoViewerController {
    var session: PhotoViewerState? by mutableStateOf(null)
        private set

    val isOpen: Boolean get() = session != null

    fun open(images: List<MediaItem>, initialIndex: Int = 0, config: PhotoViewerConfig = PhotoViewerConfig()) {
        session = PhotoViewerState(images, initialIndex, config)
    }

    fun close() { session = null }
}
