package app.pokeplan.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Plays a UI sound for a tap: buttons, menu cards, tabs and the launcher's rows. Provided by the app's root (`AppModel.tap`),
 * which stays silent when a game sound has just played for the same tap, so a "Done" never chimes twice.
 */
val LocalUiSound = staticCompositionLocalOf<(Sfx) -> Unit> { {} }

/**
 * How loud a UI tap is, as a share of the Sound effects volume. The owner found 10% still loud and 3% not audible at all, so a
 * tap is 6%: heard in a quiet room, never over a game sound. Tune here; [AppModel.tap] is the only place it is applied.
 */
const val TAP_GAIN = 0.06f
