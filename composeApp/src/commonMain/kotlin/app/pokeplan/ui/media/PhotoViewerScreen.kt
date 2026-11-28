package app.pokeplan.ui.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.BackHandler
import app.pokeplan.ui.BitmapCache
import app.pokeplan.ui.theme.LoadingState
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val VIEWER_CACHE_BYTES = 64L * 1024 * 1024
private const val ZOOM_STEP = 1.5f

/**
 * The one place the viewer is drawn: put it once at the app root. It shows whatever [PhotoViewerController.open] was last asked for,
 * as a full-screen dialog over the module's own screen, so Back leaves the module exactly as it was.
 */
@Composable
fun PhotoViewerHost(model: AppModel) {
    val state = model.photoViewer.session ?: return
    val viewer = model.imageViewer
    if (state.config.brightScreen) DisposableEffect(state) {
        viewer?.bright(true)
        onDispose { viewer?.bright(false) }
    }
    val close = model.photoViewer::close
    Dialog(close, DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(true) { if (!state.backClosesInfo()) close() }
        PhotoViewerScreen(state, close, share = viewer?.let { v -> { bytes, name -> v.share(bytes, name) } })
    }
}

private sealed interface Load {
    data object Loading : Load
    data object Failed : Load
    class Ready(val bitmap: ImageBitmap) : Load
}

private fun extensionOf(item: MediaItem, bytes: ByteArray): String = when {
    item.fileName?.substringAfterLast('.', "")?.isNotBlank() == true -> item.fileName.substringAfterLast('.').lowercase()
    item.mimeType == "image/jpeg" || (bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) -> "jpg"
    item.mimeType == "image/webp" || (bytes.size > 12 && bytes[0] == 'R'.code.toByte() && bytes[8] == 'W'.code.toByte()) -> "webp"
    else -> "png"
}

private fun fitOf(item: MediaItem, state: PhotoViewerState): ImageFitMode = item.fitMode ?: state.fit

/**
 * The full-screen viewer for one [PhotoViewerState]. Back, a centred counter and a details key on top; the picture, which swipes,
 * pinches, double-taps and pans; one row of icon keys and a quiet caption below. Tapping the picture hides the bars.
 */
@Composable
internal fun PhotoViewerScreen(state: PhotoViewerState, onClose: () -> Unit, share: ((ByteArray, String) -> String)?) {
    val config = state.config
    val light = config.backdrop == ViewerBackdrop.LIGHT
    val scope = rememberCoroutineScope()
    // The cache lives only as long as the viewer, so closing it releases every decoded picture.
    val cache = remember(state) { BitmapCache(VIEWER_CACHE_BYTES) }
    val zooms = remember(state) { HashMap<String, ZoomState>() }
    fun zoomOf(item: MediaItem) = zooms.getOrPut(item.id) { ZoomState() }
    val pager = rememberPagerState(state.index) { state.items.size }
    LaunchedEffect(pager, state) {
        snapshotFlow { pager.settledPage }.collect { page ->
            zooms.values.forEach { it.reset() }
            state.show(page)
        }
    }
    val viewport = remember { mutableStateOf(Offset.Zero) }
    val item = state.current
    // The Zoom keys are the accessible alternative to pinching and double tapping; they zoom about the centre.
    fun stepZoom(it: MediaItem, factor: Float) {
        val bitmap = cache[it.id] ?: return
        val (vw, vh) = viewport.value.let { v -> v.x to v.y }
        val (cw, ch) = ViewerMath.drawnSize(vw, vh, bitmap.width.toFloat(), bitmap.height.toFloat(), fitOf(it, state), state.rotation)
        zoomOf(it).transform(0f, 0f, factor, 0f, 0f, cw, ch, vw, vh)
        state.markZoomed(zoomOf(it).zoomed)
    }
    val hasDetails = config.showMetadata || config.allowFitToggle || config.actions.isNotEmpty()
    val empty = state.items.isEmpty()
    Column(Modifier.fillMaxSize().background(if (light) Color.White else Color(0xFF0B0B10)).safeDrawingPadding()) {
        if (state.controlsVisible || empty) Row(
            Modifier.fillMaxWidth().background(Pixel.Paper).padding(horizontal = Spacing.S, vertical = Spacing.XS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelIconButton("arrow-left", "Close", onClose)
            val counter = if (config.showImageCounter && state.items.size > 1) "${state.index + 1} / ${state.items.size}" else ""
            Text(
                counter, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = Pixel.Ink, textAlign = TextAlign.Center,
            )
            if (hasDetails) PixelIconButton("more-horizontal", if (state.infoOpen) "Hide details" else "Show details", { state.toggleInfo() })
            else Spacer(Modifier.width(Spacing.Target))
        }
        if (state.infoOpen && item != null && hasDetails) Column(
            Modifier.fillMaxWidth().background(Pixel.Paper).padding(horizontal = Spacing.M, vertical = Spacing.S),
            verticalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            if (config.showMetadata) InfoRows(item, cache[item.id])
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                if (config.allowFitToggle) PixelButton(if (state.fit == ImageFitMode.FIT_CENTER) "Fill the screen" else "Fit the screen", { state.toggleFit() }, color = Pixel.InkSoft, compact = true)
                config.actions.forEach { a -> PixelButton(a.label, { if (a.closesViewer) onClose(); a.onClick(item) }, color = config.module?.accent ?: Pixel.Blue, compact = true) }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (empty) Text(
                "Nothing to show", Modifier.align(Alignment.Center).padding(Spacing.L), style = MaterialTheme.typography.bodyLarge,
                color = if (light) Color.Black else Color.White, textAlign = TextAlign.Center,
            ) else HorizontalPager(
                pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, userScrollEnabled = !state.zoomed, key = { state.items[it].id },
            ) { page ->
                val it = state.items[page]
                ViewerPage(it, state, zoomOf(it), cache, light, active = page == pager.currentPage) { v -> viewport.value = v }
            }
        }
        if (state.controlsVisible && item != null) Column(
            Modifier.fillMaxWidth().background(Pixel.Paper).padding(horizontal = Spacing.M, vertical = Spacing.S),
            verticalArrangement = Arrangement.spacedBy(Spacing.XS), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val zoom = zoomOf(item)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.CenterVertically) {
                val many = state.items.size > 1
                if (many) PixelIconButton("chevron-left", "Previous picture", { scope.launch { pager.scrollToPage(state.index - 1) } }, enabled = state.hasPrevious)
                if (config.allowZoom) {
                    PixelIconButton("zoom-out", "Zoom out", { stepZoom(item, 1f / ZOOM_STEP) }, enabled = zoom.zoomed)
                    PixelIconButton("zoom-in", "Zoom in", { stepZoom(item, ZOOM_STEP) }, enabled = zoom.scale < ViewerMath.MAX_SCALE)
                }
                if (config.allowRotation) PixelIconButton("reload", "Rotate", {
                    state.rotate()
                    zoom.reset()
                    state.markZoomed(false)
                })
                if (config.allowShare && share != null) PixelIconButton("share", "Share", {
                    scope.launch {
                        val bytes = withContext(Dispatchers.Default) { runCatching { item.source.read() }.getOrNull() }
                        state.notice = if (bytes == null) "That ${config.shareLabel} could not be shared." else share(bytes, item.fileName ?: "${config.shareLabel.replace(' ', '-')}.${extensionOf(item, bytes)}")
                    }
                }, color = config.module?.accent ?: Pixel.Blue)
                if (many) PixelIconButton("chevron-right", "Next picture", { scope.launch { pager.scrollToPage(state.index + 1) } }, enabled = state.hasNext)
            }
            item.title?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Pixel.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
            item.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
            item.dateLabel?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Pixel.InkSoft, textAlign = TextAlign.Center) }
            state.notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, textAlign = TextAlign.Center) }
        }
    }
}

@Composable
private fun InfoRows(item: MediaItem, bitmap: ImageBitmap?) {
    val rows = buildList {
        item.fileName?.let { add("File" to it) }
        if (bitmap != null) add("Size" to "${bitmap.width} x ${bitmap.height}")
        item.mimeType?.let { add("Type" to it) }
        item.metadata.forEach { (k, v) -> add(k to v) }
    }
    if (rows.isEmpty()) Text("No details", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    else Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        rows.forEach { (k, v) -> Text("$k: $v", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
    }
}

@Composable
private fun ViewerPage(
    item: MediaItem, state: PhotoViewerState, zoom: ZoomState, cache: BitmapCache, light: Boolean, active: Boolean, onViewport: (Offset) -> Unit,
) {
    val load by produceState<Load>(cache[item.id]?.let { Load.Ready(it) } ?: Load.Loading, item.id) {
        if (value is Load.Ready) return@produceState
        val bitmap = withContext(Dispatchers.Default) { runCatching { decodeUpright(item.source.read()) }.getOrNull() }
        value = if (bitmap == null) Load.Failed else { cache[item.id] = bitmap; Load.Ready(bitmap) }
    }
    val ink = if (light) Color.Black else Color.White
    LaunchedEffect(active, zoom.zoomed) { if (active) state.markZoomed(zoom.zoomed) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val l = load) {
            Load.Loading -> LoadingState("Opening the picture", Modifier.padding(Spacing.L), color = ink)
            Load.Failed -> Text("That picture is not available", Modifier.padding(Spacing.L), style = MaterialTheme.typography.bodyLarge, color = ink, textAlign = TextAlign.Center)
            is Load.Ready -> ZoomableImage(l.bitmap, item, state, zoom, if (active) state.rotation else 0, onViewport)
        }
    }
}

@Composable
private fun ZoomableImage(bitmap: ImageBitmap, item: MediaItem, state: PhotoViewerState, zoom: ZoomState, turns: Int, onViewport: (Offset) -> Unit) {
    val config = state.config
    val fit = fitOf(item, state)
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val vw = constraints.maxWidth.toFloat()
        val vh = constraints.maxHeight.toFloat()
        val (cw, ch) = ViewerMath.drawnSize(vw, vh, bitmap.width.toFloat(), bitmap.height.toFloat(), fit, turns)
        val centre = Offset(vw / 2f, vh / 2f)
        SideEffect { onViewport(Offset(vw, vh)) }
        val odd = turns % 2 != 0
        val boxW = if (odd) maxHeight else maxWidth
        val boxH = if (odd) maxWidth else maxHeight
        val description = listOfNotNull(item.title, item.description).joinToString(". ").ifBlank { "Picture ${state.items.indexOf(item) + 1} of ${state.items.size}" }
        Box(
            Modifier.fillMaxSize()
                .pointerInput(item.id, fit, turns, vw, vh, config.allowZoom) {
                    if (!config.allowZoom) return@pointerInput
                    // A pinch always zooms; a single finger pans only once zoomed in, so at scale 1 the drag reaches the pager and turns the page.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val fingers = event.changes.count { it.pressed }
                            if (fingers > 1 || zoom.zoomed) {
                                val c = event.calculateCentroid(useCurrent = false)
                                if (c.isSpecified) {
                                    val pan = event.calculatePan()
                                    zoom.transform(c.x - centre.x, c.y - centre.y, event.calculateZoom(), pan.x, pan.y, cw, ch, vw, vh)
                                    state.markZoomed(zoom.zoomed)
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(item.id, fit, turns, vw, vh, config.allowZoom) {
                    detectTapGestures(
                        onTap = { state.toggleControls() },
                        onDoubleTap = { p -> if (config.allowZoom) { zoom.toggle(p.x - centre.x, p.y - centre.y, cw, ch, vw, vh); state.markZoomed(zoom.zoomed) } },
                    )
                }
                .graphicsLayer { scaleX = zoom.scale; scaleY = zoom.scale; translationX = zoom.tx; translationY = zoom.ty },
            contentAlignment = Alignment.Center,
        ) {
            // A quarter turn draws the picture in a box with the sides swapped and turns that box, so Fit and Fill still fit the screen.
            Image(
                bitmap, description,
                Modifier.requiredSize(boxW, boxH).graphicsLayer { rotationZ = turns * 90f },
                contentScale = if (fit == ImageFitMode.FIT_CENTER) ContentScale.Fit else ContentScale.Crop,
                filterQuality = if (config.crisp) FilterQuality.None else FilterQuality.Medium,
            )
        }
    }
}
