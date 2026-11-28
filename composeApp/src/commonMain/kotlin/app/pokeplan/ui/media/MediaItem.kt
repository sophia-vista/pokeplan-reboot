package app.pokeplan.ui.media

import app.pokeplan.ui.theme.ModuleAccent
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Where a picture's bytes come from. The viewer asks for them only when a page is near the screen, off the UI thread, and never owns
 * what it reads: a source must not write, move or delete anything. Null means the picture is unavailable (gone, revoked, unreadable).
 *
 * The shared engine knows no Android type. An Android `Uri` source is a one-line adapter in `androidApp` (`ContentResolver.openInputStream`)
 * and a stored photo is [MediaSources.base64]; the engine treats both the same.
 */
fun interface MediaSource {
    suspend fun read(): ByteArray?
}

object MediaSources {
    fun bytes(bytes: ByteArray): MediaSource = MediaSource { bytes }

    /** A picture the save keeps as Base64 (LINK photos, MINT QR codes and receipts); empty text is an unavailable picture. */
    @OptIn(ExperimentalEncodingApi::class)
    fun base64(data: String): MediaSource = MediaSource { if (data.isEmpty()) null else runCatching { Base64.decode(data) }.getOrNull() }
}

/**
 * One picture for the viewer. Only what every module shares lives here; a module's own records (a memory, a transaction) stay in the
 * module and reach the viewer as [title], [description], [dateLabel] and, for the Info panel only, [metadata].
 *
 * [dateLabel] is text the module already formatted, not an instant: LINK dates are flexible ("Summer 2019"), so a timestamp would lie.
 */
data class MediaItem(
    val id: String,
    val source: MediaSource,
    val mimeType: String? = null,
    val title: String? = null,
    val description: String? = null,
    val dateLabel: String? = null,
    val fileName: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    /** Overrides the config's fit mode for this picture. */
    val fitMode: ImageFitMode? = null,
)

enum class ImageFitMode { FIT_CENTER, FILL_CENTER }

enum class ViewerBackdrop {
    /** Near black, for photos. */
    DARK,

    /** White, for QR codes, which scan from a light field. */
    LIGHT,
}

/** An extra key a module adds to the viewer's bar ("Open memory"); it gets the picture on show. */
class ViewerAction(val label: String, val closesViewer: Boolean = true, val onClick: (MediaItem) -> Unit)

/** What the viewer offers for one opening; the module decides, the engine never needs to change. */
data class PhotoViewerConfig(
    val allowZoom: Boolean = true,
    val allowShare: Boolean = false,
    val showImageCounter: Boolean = true,
    val showMetadata: Boolean = false,
    val fitMode: ImageFitMode = ImageFitMode.FIT_CENTER,
    /** Adds a Fit / Fill key. */
    val allowFitToggle: Boolean = false,
    val backdrop: ViewerBackdrop = ViewerBackdrop.DARK,
    /** Draw hard pixel edges instead of smoothing, for QR codes and documents. */
    val crisp: Boolean = false,
    /** Raise the screen to full brightness and keep it on while the viewer is open (a QR code about to be scanned). */
    val brightScreen: Boolean = false,
    /** The module whose colour the [actions] and Share keys take. */
    val module: ModuleAccent? = null,
    val actions: List<ViewerAction> = emptyList(),
    /** What the system calls the picture when it is shared: "Receive money QR code", "Receipt". */
    val shareLabel: String = "picture",
)
