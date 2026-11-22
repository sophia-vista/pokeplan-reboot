package app.pokeplan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelTheme
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.Spacing

/**
 * Shown for the moment the database is opened and checked off the main thread (see `MainActivity`). It draws only tokens
 * and text, so it appears before any save, sprite or font-atlas work is done.
 */
@Composable
fun StartupLoading(darkTheme: Boolean = isSystemInDarkTheme()) {
    PixelTheme(darkTheme) {
        Box(Modifier.fillMaxSize().background(Pixel.Paper), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelTitle("PokéLife")
                Text("Opening your save…", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
        }
    }
}
