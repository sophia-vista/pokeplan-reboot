package app.pokeplan.ui.screens

import app.pokeplan.ui.theme.Spacing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.reward.Reward
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.Stage
import app.pokeplan.ui.TypePlate
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.TextBox
import kotlinx.coroutines.delay

/** A moment that takes the whole screen for a few seconds, the way the games stop for it: an egg hatching, an evolution. */
sealed interface Cutscene {
    data class Hatch(val pokemon: OwnedPokemon, val fromTask: Boolean) : Cutscene

    data class Evolve(val before: OwnedPokemon, val after: OwnedPokemon) : Cutscene
}

/** The white of the games' flash, and the gold one a shiny gets instead. */
private val FlashWhite = Color.White
private val FlashGold = Color(0xFFFFE27A)

/**
 * Plays [AppModel.cutscene] over the whole app, and starts the hatch of any egg nobody has seen hatch yet: a focus egg, a
 * Task Egg, or one paid for a task ticked on the widget. Each hatch plays once per run of the app.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CutsceneHost(model: AppModel) {
    val waiting = model.data.rewardLog.filter { !it.seen }
        .flatMap { it.rewards.filterIsInstance<Reward.Hatched>() }
        .firstOrNull { it.pokemon.id !in model.playedHatches }
    val idle = model.cutscene == null
    LaunchedEffect(waiting?.pokemon?.id, idle) {
        if (idle && waiting != null) {
            model.playedHatches += waiting.pokemon.id
            model.cutscene = Cutscene.Hatch(waiting.pokemon, waiting.fromTask)
        }
    }
    val scene = model.cutscene ?: return
    val close = { model.cutscene = null }
    BackHandler(enabled = true, onBack = close)
    when (scene) {
        is Cutscene.Hatch -> HatchScene(model, scene, close)
        is Cutscene.Evolve -> EvolutionScene(model, scene, close)
    }
}

/** The dark page a scene plays on. Taps anywhere go to [onTap] and never reach the screen underneath. */
@Composable
private fun SceneFrame(onTap: () -> Unit, flash: Float, flashColor: Color, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.safeDrawingPadding().widthIn(max = 480.dp).fillMaxWidth().padding(Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
        if (flash > 0f) Box(Modifier.fillMaxSize().background(flashColor.copy(alpha = flash)))
    }
}

/** The line under a scene, read out by screen readers as it changes, with the button that ends or skips it. */
@Composable
private fun SceneText(text: String, detail: String?, done: Boolean, skip: () -> Unit, close: () -> Unit) {
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(text, Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (done) PixelButton("OK", close, color = Pixel.Green, compact = true)
                else PixelButton("Skip", skip, color = Pixel.InkSoft, compact = true)
            }
        }
    }
}

/**
 * An egg hatching, as in the games: "Oh?", the egg rocks three times, harder each time, a flash, and the Pokémon is there
 * and calls out. A shiny flashes gold, twice. Reduce motion goes straight to the Pokémon; a tap skips ahead.
 */
@Composable
private fun HatchScene(model: AppModel, scene: Cutscene.Hatch, close: () -> Unit) {
    val p = scene.pokemon
    val still = model.data.settings.reduceMotion
    var hatched by remember(p.id) { mutableStateOf(false) }
    val tilt = remember(p.id) { Animatable(0f) }
    val flash = remember(p.id) { Animatable(0f) }
    LaunchedEffect(p.id, hatched) {
        if (hatched) return@LaunchedEffect
        if (!still) {
            delay(700)
            for (burst in 0 until 3) {
                model.haptic()
                model.effect(Sfx.JUMP)
                val swing = 7f + burst * 5f
                repeat(2 + burst) {
                    tilt.animateTo(swing, tween(80))
                    tilt.animateTo(-swing, tween(80))
                }
                tilt.animateTo(0f, tween(80))
                delay(700L - burst * 200)
            }
            flash.animateTo(1f, tween(300))
        }
        hatched = true
    }
    LaunchedEffect(p.id, hatched) {
        if (!hatched) return@LaunchedEffect
        tilt.snapTo(0f)
        model.cry(p.speciesId)
        model.haptic()
        if (still) {
            flash.snapTo(0f)
            return@LaunchedEffect
        }
        flash.snapTo(1f)
        flash.animateTo(0f, tween(700))
        if (p.isShiny) {
            flash.animateTo(0.6f, tween(200))
            flash.animateTo(0f, tween(500))
        }
    }

    val name = p.nickname
    val egg = if (scene.fromTask) "Task Egg" else "Egg"
    SceneFrame({ hatched = true }, flash.value, if (p.isShiny) FlashGold else FlashWhite) {
        Stage("bw08", 260.dp) {
            if (!hatched) {
                ItemIcon(
                    "mystery-egg",
                    Modifier.align(Alignment.Center).graphicsLayer {
                        rotationZ = tilt.value
                        // Rocks on its base, not around its middle.
                        transformOrigin = TransformOrigin(0.5f, 0.9f)
                    },
                    size = 144.dp, description = "An egg",
                )
            } else {
                AnimatedSprite(p.form.id, p.isShiny, name, Modifier.align(Alignment.TopCenter).padding(top = Spacing.XL), size = 176.dp, animate = !still)
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.M), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    p.form.types.forEach { TypePlate(it, width = 84.dp) }
                }
            }
        }
        SceneText(
            text = when {
                !hatched -> "Oh?"
                p.isShiny -> "✨ A shiny $name hatched from the $egg! ✨"
                else -> "$name hatched from the $egg!"
            },
            detail = if (hatched) p.species.genus.takeIf { it.isNotEmpty() }?.let { "$name, the $it." } else null,
            done = hatched, skip = { hatched = true }, close = close,
        )
    }
}

/**
 * An evolution, as in the games: "What?", the Pokémon turns white and flickers between its old and new shape, faster
 * and faster, then a flash and the new Pokémon calls out. Reduce motion goes straight to the end; a tap skips ahead.
 */
@Composable
private fun EvolutionScene(model: AppModel, scene: Cutscene.Evolve, close: () -> Unit) {
    val before = scene.before
    val after = scene.after
    val still = model.data.settings.reduceMotion
    var done by remember(after) { mutableStateOf(false) }
    var showNew by remember(after) { mutableStateOf(false) }
    val white = remember(after) { Animatable(0f) }
    val flash = remember(after) { Animatable(0f) }
    LaunchedEffect(after, done) {
        if (done) return@LaunchedEffect
        if (!still) {
            delay(1_000)
            white.animateTo(1f, tween(600))
            var gap = 450L
            while (gap > 50) {
                delay(gap)
                showNew = !showNew
                gap = gap * 4 / 5
            }
            showNew = true
            flash.animateTo(1f, tween(250))
        }
        done = true
    }
    LaunchedEffect(after, done) {
        if (!done) return@LaunchedEffect
        showNew = true
        white.snapTo(0f)
        model.cry(after.speciesId)
        model.haptic()
        if (still) {
            flash.snapTo(0f)
        } else {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(700))
        }
        // "Congratulations!": the Pokémon Center jingle, once the cry has had its moment.
        delay(1_200)
        model.effect(Sfx.POKEMON_CENTER)
    }

    SceneFrame({ done = true }, flash.value, FlashWhite) {
        Stage("bw08", 260.dp) {
            if (done) {
                AnimatedSprite(after.form.id, after.isShiny, after.nickname, Modifier.align(Alignment.TopCenter).padding(top = Spacing.XL), size = 176.dp, animate = !still)
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.M), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    after.form.types.forEach { TypePlate(it, width = 84.dp) }
                }
            } else {
                val shown = if (showNew) after else before
                // The real sprite, fading to its white shape: the games' evolution glow.
                Box(Modifier.align(Alignment.Center)) {
                    Sprite(shown.form.id, shown.isShiny, before.nickname, Modifier.alpha(1f - white.value), size = 160.dp)
                    Sprite(shown.form.id, shown.isShiny, before.nickname, Modifier.alpha(white.value), size = 160.dp, silhouette = Color.White)
                }
            }
        }
        SceneText(
            text = if (done) "Congratulations! Your ${before.nickname} evolved into ${after.species.name}!" else "What? ${before.nickname} is evolving!",
            detail = null, done = done, skip = { done = true }, close = close,
        )
    }
}
