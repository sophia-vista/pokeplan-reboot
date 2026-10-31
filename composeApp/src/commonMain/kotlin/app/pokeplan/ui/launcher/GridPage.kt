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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.Spacing

/** Space kept round each widget inside its cells, so neighbours never touch. */
private val CELL_GAP = 4.dp

/**
 * A page's widgets laid on the grid ([WidgetGrid]): each in its own cells (a fifth of the width wide, [WidgetGrid.ROW_DP] tall
 * rows), clipped to them, the town or the page showing through the empty cells. Scrolls when the widgets run past the screen.
 */
@Composable
internal fun GridCanvas(env: WidgetEnv, page: WidgetPage, modifier: Modifier = Modifier) {
    val widgets = WidgetGrid.onPage(env.prefs.gridWidgets, page)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val inset = Spacing.Page - CELL_GAP
        val column = (maxWidth - inset * 2) / WidgetGrid.COLUMNS
        val row = WidgetGrid.ROW_DP.dp
        Box(Modifier.fillMaxWidth().height(row * WidgetGrid.rows(widgets, page))) {
            widgets.forEach { w ->
                key(w.kind, w.arg) {
                    Box(Modifier.offset(inset + column * w.col, row * w.row).size(column * w.w, row * w.h).padding(CELL_GAP).clipToBounds()) {
                        WidgetBody(w.asSlot(), env)
                    }
                }
            }
        }
    }
}

/** The home page on the grid: the widgets over the town, scrolling when they are taller than the screen. */
@Composable
internal fun GridHomePage(env: WidgetEnv, note: Pair<String, () -> Unit>?) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(vertical = Spacing.L)) {
        if (note != null) PmdBox(env.pixel, Modifier.fillMaxWidth().padding(horizontal = Spacing.Page).padding(bottom = Spacing.M), inset = 1) { PmdMenuLine(note.first, env.pixel, onClick = note.second) }
        GridCanvas(env, WidgetPage.HOME)
    }
}

/** Today on the grid. */
@Composable
internal fun GridTodayPage(env: WidgetEnv) {
    Box(Modifier.fillMaxSize().background(Pixel.Night.copy(alpha = 0.5f)).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Spacing.XXL, bottom = Spacing.XL)) { GridCanvas(env, WidgetPage.TODAY) }
    }
}
