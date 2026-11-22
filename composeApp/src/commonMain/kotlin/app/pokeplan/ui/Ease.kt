package app.pokeplan.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import app.pokeplan.ui.screens.LocalReduceMotion

object Ease {
    val Smooth = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val Out = CubicBezierEasing(0.17f, 1f, 0.32f, 0.9999f)
    val Spring = CubicBezierEasing(0.35f, 1.55f, 0.65f, 1f)
    val InOut = CubicBezierEasing(0.66f, 0f, 0.34f, 1f)
}

const val PRESS_SCALE = 0.98f
const val PRESS_MS = 120

@Composable
fun Modifier.pressScale(source: InteractionSource): Modifier {
    if (LocalReduceMotion.current) return this
    val pressed by source.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed) { scale.animateTo(if (pressed) PRESS_SCALE else 1f, tween(PRESS_MS, easing = Ease.Smooth)) }
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

@Composable
fun Modifier.entrance(): Modifier {
    val calm = LocalReduceMotion.current
    val progress = remember { Animatable(if (calm) 1f else 0f) }
    LaunchedEffect(Unit) { if (!calm) progress.animateTo(1f, tween(280, easing = Ease.Smooth)) }
    val rise = 6 * LocalDensity.current.density
    return alpha(progress.value).graphicsLayer { translationY = (1f - progress.value) * rise }
}
