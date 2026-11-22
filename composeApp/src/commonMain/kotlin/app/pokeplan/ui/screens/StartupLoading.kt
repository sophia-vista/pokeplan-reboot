package app.pokeplan.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import app.pokeplan.ui.theme.LoadingScreen
import app.pokeplan.ui.theme.PixelTheme

/**
 * Shown for the moment the database is opened and checked off the main thread (see `MainActivity`). It draws only tokens
 * and text, so it appears before any save, sprite or font-atlas work is done; the brand, one line of what is happening
 * and the loading pixels are the whole screen.
 */
@Composable
fun StartupLoading(darkTheme: Boolean = isSystemInDarkTheme(), status: String = "Opening your save…") {
    PixelTheme(darkTheme) { LoadingScreen("PokéLife", status = status) }
}
