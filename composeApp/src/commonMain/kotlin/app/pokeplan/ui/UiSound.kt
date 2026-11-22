package app.pokeplan.ui

import androidx.compose.runtime.staticCompositionLocalOf

val LocalUiSound = staticCompositionLocalOf<(Sfx) -> Unit> { {} }

const val TAP_GAIN = 0.06f
