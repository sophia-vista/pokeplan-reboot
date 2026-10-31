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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
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

/** Space kept round each widget inside its cells, so neighbours never touch. */
private val CELL_GAP = 4.dp

/** A finger on a widget being edited: moving it, or (resize) pulling its corner; [dx] and [dy] are how far it has gone, in px. */
private data class Gesture(val widget: GridWidget, val resize: Boolean, val dx: Float = 0f, val dy: Float = 0f)

/**
 * A page's widgets laid on the grid ([WidgetGrid]): each in its own cells (a fifth of the width wide, [WidgetGrid.ROW_DP] tall
 * rows), clipped to them, the town or the page showing through the empty cells. Scrolls when the widgets run past the screen.
 *
 * Editing (long-press a widget): each widget gets a frame. Drag it anywhere and it snaps to the cell under it when you let go
 * (anything in the way moves down); pull the corner square to resize in whole cells, between the kind's limits; the X removes
 * it. A yellow frame shows where it will land. Every one of those also has an accessibility action, so no gesture is the only way.
 */
@Composable
internal fun GridCanvas(env: WidgetEnv, page: WidgetPage, modifier: Modifier = Modifier) {
    val all = env.prefs.gridWidgets
    val widgets = WidgetGrid.onPage(all, page)
    val density = LocalDensity.current
    var gesture by remember { mutableStateOf<Gesture?>(null) }
    val current by rememberUpdatedState(all)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val inset = Spacing.Page - CELL_GAP
        val column = (maxWidth - inset * 2) / WidgetGrid.COLUMNS
        val row = WidgetGrid.ROW_DP.dp
        val colPx = with(density) { column.toPx() }
        val rowPx = with(density) { row.toPx() }
        fun commit(next: List<GridWidget>) = env.onPrefs(env.prefs.withGrid(next))
        // The canvas is a row taller than its lowest widget while editing, so there is always a free place to drop one lower.
        val rows = WidgetGrid.rows(widgets, page) + if (env.editing) 2 else 0
        Box(Modifier.fillMaxWidth().height(row * rows)) {
            gesture?.let { g ->
                val target = if (g.resize) WidgetGrid.resizedTo(g.widget, g.dx, g.dy, colPx, rowPx).let { (w, h) -> g.widget.copy(w = w, h = h) }
                else WidgetGrid.droppedAt(g.widget, g.dx, g.dy, colPx, rowPx).let { (c, r) -> g.widget.copy(col = c, row = r) }
                Box(Modifier.offset(inset + column * target.col, row * target.row).size(column * target.w, row * target.h).padding(CELL_GAP).border(2.dp, Pixel.Yellow))
            }
            widgets.forEach { w ->
                key(w.kind, w.arg) {
                    val g = gesture?.takeIf { it.widget.sameAs(w) }
                    Box(
                        Modifier.offset(inset + column * w.col, row * w.row).size(column * w.w, row * w.h)
                            .zIndex(if (g != null) 2f else 0f)
                            .graphicsLayer { if (g != null && !g.resize) { translationX = g.dx; translationY = g.dy } }
                            .padding(CELL_GAP),
                    ) {
                        Box(
                            Modifier.fillMaxSize().clipToBounds()
                                .then(if (env.editing) Modifier else Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { env.onEdit() }) }),
                        ) { WidgetBody(w.asSlot(), env) }
                        if (env.editing) EditFrame(
                            w, env,
                            onStart = { resize -> gesture = Gesture(w, resize) },
                            onDrag = { dx, dy -> gesture = gesture?.let { it.copy(dx = it.dx + dx, dy = it.dy + dy) } },
                            onEnd = {
                                gesture?.let { done ->
                                    val latest = current.firstOrNull { it.sameAs(done.widget) } ?: done.widget
                                    if (done.resize) WidgetGrid.resizedTo(latest, done.dx, done.dy, colPx, rowPx).let { (nw, nh) -> commit(WidgetGrid.resize(current, latest, nw, nh)) }
                                    else WidgetGrid.droppedAt(latest, done.dx, done.dy, colPx, rowPx).let { (c, r) -> commit(WidgetGrid.move(current, latest, c, r)) }
                                }
                                gesture = null
                            },
                            onCancel = { gesture = null },
                            nudge = { dc, dr -> commit(WidgetGrid.move(current, w, w.col + dc, w.row + dr)) },
                            grow = { dw, dh -> commit(WidgetGrid.resize(current, w, w.w + dw, w.h + dh)) },
                        )
                    }
                }
            }
        }
    }
}

/** The frame of a widget being edited: drag anywhere on it to move, the corner square to resize, the X to remove. */
@Composable
private fun EditFrame(
    w: GridWidget, env: WidgetEnv, onStart: (resize: Boolean) -> Unit, onDrag: (Float, Float) -> Unit, onEnd: () -> Unit, onCancel: () -> Unit,
    nudge: (Int, Int) -> Unit, grow: (Int, Int) -> Unit,
) {
    val slot = w.asSlot()
    val title = if (w.kind == WidgetKind.ANDROID) env.host.widgetLabel(w.arg.toIntOrNull() ?: -1) ?: slot.title else slot.title
    fun action(label: String, run: () -> Unit) = CustomAccessibilityAction(label) { run(); true }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().border(2.dp, Color.White.copy(alpha = 0.7f)).background(Color.White.copy(alpha = 0.10f))
                .pointerInput(w.kind, w.arg) { detectDragGestures(onDragStart = { onStart(false) }, onDragEnd = onEnd, onDragCancel = onCancel) { change, amount -> change.consume(); onDrag(amount.x, amount.y) } }
                .semantics {
                    contentDescription = "$title, ${w.w} by ${w.h} cells"
                    customActions = listOf(
                        action("Move up") { nudge(0, -1) }, action("Move down") { nudge(0, 1) }, action("Move left") { nudge(-1, 0) }, action("Move right") { nudge(1, 0) },
                        action("Wider") { grow(1, 0) }, action("Narrower") { grow(-1, 0) }, action("Taller") { grow(0, 1) }, action("Shorter") { grow(0, -1) },
                        action("Remove") { removeIt(env, w) },
                    )
                },
        ) { PmdText(title, env.pixel, Modifier.padding(start = Spacing.XS, top = Spacing.XS).background(Pixel.Night.copy(alpha = 0.8f)).padding(horizontal = Spacing.XS), alpha = 0.95f) }
        // Remove, top right.
        Box(
            Modifier.align(Alignment.TopEnd).size(Spacing.Target).clickable(role = Role.Button, onClickLabel = "Remove $title") { removeIt(env, w) },
            contentAlignment = Alignment.Center,
        ) { PmdText("X", env.pixel) }
        // Resize, bottom right: a square to pull.
        Box(
            Modifier.align(Alignment.BottomEnd).size(Spacing.Target)
                .pointerInput(w.kind, w.arg) { detectDragGestures(onDragStart = { onStart(true) }, onDragEnd = onEnd, onDragCancel = onCancel) { change, amount -> change.consume(); onDrag(amount.x, amount.y) } }
                .semantics { contentDescription = "Resize $title" },
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(16.dp).border(2.dp, Pixel.Yellow).background(Pixel.Night.copy(alpha = 0.6f))) }
    }
}

private fun removeIt(env: WidgetEnv, w: GridWidget) {
    if (w.kind == WidgetKind.ANDROID) w.arg.toIntOrNull()?.let { env.host.releaseWidget(it) }
    env.onPrefs(env.prefs.removeGridWidget(w))
}

/** The home page on the grid: the widgets over the town, scrolling when they are taller than the screen. */
@Composable
internal fun GridHomePage(env: WidgetEnv, note: Pair<String, () -> Unit>?) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(vertical = Spacing.L)) {
        if (note != null) PmdBox(env.pixel, Modifier.fillMaxWidth().padding(horizontal = Spacing.Page).padding(bottom = Spacing.M), inset = 1) { PmdMenuLine(note.first, env.pixel, onClick = note.second) }
        if (env.editing) EditToolbar(env, Modifier.padding(horizontal = Spacing.Page).padding(bottom = Spacing.M))
        GridCanvas(env, WidgetPage.HOME)
    }
}

/** Today on the grid. */
@Composable
internal fun GridTodayPage(env: WidgetEnv) {
    Box(Modifier.fillMaxSize().background(Pixel.Night.copy(alpha = 0.5f)).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Spacing.XXL, bottom = Spacing.XL)) {
            if (env.editing) EditToolbar(env, Modifier.padding(horizontal = Spacing.Page).padding(bottom = Spacing.M))
            GridCanvas(env, WidgetPage.TODAY)
        }
    }
}
