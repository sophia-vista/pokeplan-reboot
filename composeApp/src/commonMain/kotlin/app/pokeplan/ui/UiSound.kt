package app.pokeplan.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Plays a UI sound for a tap: buttons, menu cards, tabs and the launcher's rows. Provided by the app's root (`AppModel.tap`),
 * which stays silent when a game sound has just played for the same tap, so a "Done" never chimes twice.
 */
val LocalUiSound = staticCompositionLocalOf<(Sfx) -> Unit> { {} }
