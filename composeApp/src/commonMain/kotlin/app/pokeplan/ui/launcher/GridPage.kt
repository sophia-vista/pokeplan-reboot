package app.pokeplan.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import app.pokeplan.ui.screens.LocalReduceMotion
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateDpAsState
import app.pokeplan.ui.theme.PmdText
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.Spacing

internal val CELL_GAP = WidgetGrid.CELL_GAP_DP.dp

private data class Gesture(val widget: GridWidget, val resize: Boolean, val startX: Float = 0f, val dx: Float = 0f, val dy: Float = 0f)

private val EDGE_ZONE = 40.dp
private const val EDGE_DWELL_MS = 450L

@Composable
internal fun GridCanvas(env: WidgetEnv, page: WidgetPage, modifier: Modifier = Modifier) {
    val all = env.prefs.gridWidgets
    val density = LocalDensity.current
    val calm = LocalReduceMotion.current
    var gesture by remember { mutableStateOf<Gesture?>(null) }
    val current by rememberUpdatedState(all)
    val live by rememberUpdatedState(env)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val inset = Spacing.Page - CELL_GAP
        val column = (maxWidth - inset * 2) / WidgetGrid.COLUMNS
        val row = WidgetGrid.ROW_DP.dp
        val colPx = with(density) { column.toPx() }
        val rowPx = with(density) { row.toPx() }
        val widthPx = with(density) { maxWidth.toPx() }
        fun commit(next: List<GridWidget>) = live.onPrefs(live.prefs.withGrid(next))
        val columnDp = column.value
        fun limitsOf(w: GridWidget): WidgetLimits =
            if (w.kind == WidgetKind.ANDROID) w.arg.toIntOrNull()?.let { live.host.widgetSpec(it) }?.limits(columnDp) ?: w.kind.limits() else w.kind.limits()
        val shown = gesture?.let { g ->
            val at = current.firstOrNull { it.sameAs(g.widget) } ?: g.widget
            if (g.resize) WidgetGrid.resizedTo(at, g.dx, g.dy, colPx, rowPx, limitsOf(at)).let { (w, h) -> WidgetGrid.resize(current, at, w, h, limitsOf(at)) }
            else WidgetGrid.droppedAt(at, g.dx, g.dy, colPx, rowPx).let { (c, r) -> WidgetGrid.move(current, at, c, r) }
        } ?: all
        val onThis = WidgetGrid.onPage(shown, page)
        val side = gesture?.takeIf { !it.resize }?.let { g ->
            val at = current.firstOrNull { it.sameAs(g.widget) } ?: g.widget
            val x = with(density) { (inset + column * at.col + CELL_GAP).toPx() } + g.startX + g.dx
            val zone = with(density) { EDGE_ZONE.toPx() }
            if (x < zone && live.prefs.pageOrder.indexOf(page) > 0) -1 else if (x > widthPx - zone && live.prefs.pageOrder.indexOf(page) < live.prefs.pageOrder.size - 1) 1 else 0
        } ?: 0
        LaunchedEffect(side, gesture?.widget?.kind) {
            val g = gesture
            if (side != 0 && g != null) {
                delay(EDGE_DWELL_MS)
                val target = live.prefs.pageOrder[live.prefs.pageOrder.indexOf(page) + side]
                live.onPrefs(live.prefs.moveGridWidgetToPage(g.widget, target))
                gesture = null
                live.goToPage(live.prefs.pageOrder.indexOf(target))
            }
        }
        val rows = WidgetGrid.rows(onThis, page) + if (env.editing) 2 else 0
        Box(Modifier.fillMaxWidth().height(row * rows)) {
            gesture?.let { g ->
                val target = if (g.resize) WidgetGrid.resizedTo(g.widget, g.dx, g.dy, colPx, rowPx, limitsOf(g.widget)).let { (w, h) -> g.widget.copy(w = w, h = h) }
                else WidgetGrid.droppedAt(g.widget, g.dx, g.dy, colPx, rowPx).let { (c, r) -> g.widget.copy(col = c, row = r) }
                Box(Modifier.offset(inset + column * target.col, row * target.row).size(column * target.w, row * target.h).padding(CELL_GAP).border(2.dp, Pixel.Yellow))
            }
            onThis.forEach { w ->
                key(w.kind, w.arg) {
                    val g = gesture?.takeIf { it.widget.sameAs(w) }
                    val home = if (g != null && !g.resize) current.firstOrNull { it.sameAs(w) } ?: w else w
                    val x by animateDpAsState(inset + column * home.col, if (calm || g != null) snap() else tween(140))
                    val y by animateDpAsState(row * home.row, if (calm || g != null) snap() else tween(140))
                    Box(
                        Modifier.offset(x, y).size(column * w.w, row * w.h)
                            .zIndex(if (g != null) 2f else 0f)
                            .graphicsLayer { if (g != null && !g.resize) { translationX = g.dx; translationY = g.dy } }
                            .padding(CELL_GAP),
                    ) {
                        Box(
                            Modifier.fillMaxSize().clipToBounds()
                                .then(if (env.editing) Modifier else Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { env.onEdit() }) }),
                        ) {
                            androidx.compose.runtime.CompositionLocalProvider(LocalCells provides Cells(w.w, w.h), LocalGridFill provides true) { WidgetBody(w.asSlot(), env) }
                        }
                        if (env.editing) EditFrame(
                            w, env,
                            onStart = { resize, startX -> gesture = Gesture(w, resize, startX) },
                            onDrag = { dx, dy -> gesture = gesture?.let { it.copy(dx = it.dx + dx, dy = it.dy + dy) } },
                            onEnd = {
                                gesture?.let { done ->
                                    val latest = current.firstOrNull { it.sameAs(done.widget) } ?: done.widget
                                    if (done.resize) WidgetGrid.resizedTo(latest, done.dx, done.dy, colPx, rowPx, limitsOf(latest)).let { (nw, nh) -> commit(WidgetGrid.resize(current, latest, nw, nh, limitsOf(latest))) }
                                    else WidgetGrid.droppedAt(latest, done.dx, done.dy, colPx, rowPx).let { (c, r) -> commit(WidgetGrid.move(current, latest, c, r)) }
                                }
                                gesture = null
                            },
                            onCancel = { gesture = null },
                            nudge = { dc, dr -> commit(WidgetGrid.move(current, w, w.col + dc, w.row + dr)) },
                            grow = { dw, dh -> commit(WidgetGrid.resize(current, w, w.w + dw, w.h + dh, limitsOf(w))) },
                            toPage = { dir ->
                                val order = live.prefs.pageOrder
                                val to = order.indexOf(page) + dir
                                if (to in order.indices) { live.onPrefs(live.prefs.moveGridWidgetToPage(w, order[to])); live.goToPage(to) }
                            },
                        )
                    }
                }
            }
            if (env.editing && onThis.isEmpty()) PmdText("An empty page: Add widget, or drag one here", env.pixel, Modifier.padding(Spacing.Page), alpha = 0.8f)
        }
    }
}

@Composable
private fun EditFrame(
    w: GridWidget, env: WidgetEnv, onStart: (resize: Boolean, startX: Float) -> Unit, onDrag: (Float, Float) -> Unit, onEnd: () -> Unit, onCancel: () -> Unit,
    nudge: (Int, Int) -> Unit, grow: (Int, Int) -> Unit, toPage: (Int) -> Unit,
) {
    val slot = w.asSlot()
    val title = if (w.kind == WidgetKind.ANDROID) env.host.widgetLabel(w.arg.toIntOrNull() ?: -1) ?: slot.title else slot.title
    fun action(label: String, run: () -> Unit) = CustomAccessibilityAction(label) { run(); true }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().border(2.dp, Color.White.copy(alpha = 0.7f)).background(Color.White.copy(alpha = 0.10f))
                .pointerInput(w.kind, w.arg) { detectDragGestures(onDragStart = { onStart(false, it.x) }, onDragEnd = onEnd, onDragCancel = onCancel) { change, amount -> change.consume(); onDrag(amount.x, amount.y) } }
                .semantics {
                    contentDescription = "$title, ${w.w} by ${w.h} cells"
                    customActions = listOf(
                        action("Move up") { nudge(0, -1) }, action("Move down") { nudge(0, 1) }, action("Move left") { nudge(-1, 0) }, action("Move right") { nudge(1, 0) },
                        action("Wider") { grow(1, 0) }, action("Narrower") { grow(-1, 0) }, action("Taller") { grow(0, 1) }, action("Shorter") { grow(0, -1) },
                        action("To the next page") { toPage(1) }, action("To the previous page") { toPage(-1) },
                        action("Remove") { removeIt(env, w) },
                    )
                },
        ) { PmdText(title, env.pixel, Modifier.padding(start = Spacing.XS, top = Spacing.XS).background(Pixel.Night.copy(alpha = 0.8f)).padding(horizontal = Spacing.XS), alpha = 0.95f) }
        Box(
            Modifier.align(Alignment.TopEnd).size(Spacing.Target).clickable(role = Role.Button, onClickLabel = "Remove $title") { removeIt(env, w) },
            contentAlignment = Alignment.Center,
        ) { PmdText("X", env.pixel) }
        Box(
            Modifier.align(Alignment.BottomEnd).size(Spacing.Target)
                .pointerInput(w.kind, w.arg) { detectDragGestures(onDragStart = { onStart(true, it.x) }, onDragEnd = onEnd, onDragCancel = onCancel) { change, amount -> change.consume(); onDrag(amount.x, amount.y) } }
                .semantics { contentDescription = "Resize $title" },
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(16.dp).border(2.dp, Pixel.Yellow).background(Pixel.Night.copy(alpha = 0.6f))) }
    }
}

private fun removeIt(env: WidgetEnv, w: GridWidget) {
    if (w.kind == WidgetKind.ANDROID) w.arg.toIntOrNull()?.let { env.host.releaseWidget(it) }
    env.onPrefs(env.prefs.removeGridWidget(w))
}

@Composable
internal fun GridEditBar(env: WidgetEnv, page: WidgetPage, modifier: Modifier = Modifier) {
    PmdBox(env.pixel, modifier.fillMaxWidth().padding(horizontal = Spacing.Page).padding(bottom = Spacing.S), inset = 1) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()) {
                PmdMenuLine("Add", env.pixel, Modifier.weight(1f), center = true, onClick = env.onWidgets)
                if (env.prefs.canDeletePage(page)) {
                    val at = env.prefs.pageOrder.indexOf(page)
                    PmdMenuLine("Page-", env.pixel, Modifier.weight(1f), center = true) {
                        val next = env.prefs.deletePage(page)
                        env.onPrefs(next)
                        env.goToPage(at.coerceAtMost(next.pageOrder.size - 1))
                    }
                }
                PmdMenuLine("Done", env.pixel, Modifier.weight(1f), center = true, onClick = env.onDone)
            }
            if (env.pages < WidgetPage.MAX) Row(Modifier.fillMaxWidth()) {
                PmdMenuLine("Left+", env.pixel, Modifier.weight(1f), center = true) { env.onPrefs(env.prefs.addPage(left = true)); env.goToPage(0) }
                PmdMenuLine("Right+", env.pixel, Modifier.weight(1f), center = true) { env.onPrefs(env.prefs.addPage(left = false)); env.goToPage(env.pages) }
            }
        }
    }
}

@Composable
internal fun GridHomePage(env: WidgetEnv, note: Pair<String, () -> Unit>?) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(vertical = Spacing.L)) {
        if (note != null) PmdBox(env.pixel, Modifier.fillMaxWidth().padding(horizontal = Spacing.Page).padding(bottom = Spacing.M), inset = 1) { PmdMenuLine(note.first, env.pixel, onClick = note.second) }
        GridCanvas(env, WidgetPage.HOME)
    }
}

@Composable
internal fun GridSidePage(env: WidgetEnv, page: WidgetPage) {
    Box(Modifier.fillMaxSize().background(Pixel.Night.copy(alpha = 0.5f)).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Spacing.XXL, bottom = Spacing.XL)) {
            GridCanvas(env, page)
        }
    }
}
