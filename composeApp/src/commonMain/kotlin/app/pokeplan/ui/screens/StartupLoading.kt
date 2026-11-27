package app.pokeplan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.theme.LoadingState
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelTheme
import app.pokeplan.ui.theme.Skeleton
import app.pokeplan.ui.theme.SkeletonPanel
import app.pokeplan.ui.theme.SkeletonRows
import app.pokeplan.ui.theme.Spacing

/**
 * Shown for the moment the database is opened and checked off the main thread (see `MainActivity`). It draws only tokens
 * and text, so it appears before any save, sprite or font-atlas work is done. It is a static skeleton of Home in the order
 * every module's page has (a lead box, the overview, a quick-actions box, the party) with the four bottom tabs under it, so
 * the first real frame fills in shapes that are already there instead of replacing a different screen; one line says what is
 * happening and TalkBack hears only that line.
 */
@Composable
fun StartupLoading(darkTheme: Boolean = isSystemInDarkTheme(), status: String = "Opening your save…") {
    PixelTheme(darkTheme) {
        Box(Modifier.fillMaxSize().background(Pixel.Paper).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.fillMaxSize().widthIn(max = 640.dp).padding(horizontal = Spacing.Page),
                verticalArrangement = Arrangement.spacedBy(Spacing.M),
            ) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(top = Spacing.L).clearAndSetSemantics {},
                    verticalArrangement = Arrangement.spacedBy(Spacing.M),
                ) {
                    SkeletonPanel(height = 72.dp)
                    SkeletonRows(count = 3)
                    SkeletonPanel(height = 96.dp)
                    SkeletonPanel(height = 120.dp)
                }
                LoadingState(status, Modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = status })
                Row(Modifier.fillMaxWidth().padding(bottom = Spacing.S).clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    repeat(4) { Skeleton(Modifier.weight(1f), height = 48.dp) }
                }
            }
        }
    }
}
