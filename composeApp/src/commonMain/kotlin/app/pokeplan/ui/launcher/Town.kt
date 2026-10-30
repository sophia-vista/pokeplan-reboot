package app.pokeplan.ui.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.VectorConverter
import app.pokeplan.app.BuddyScene
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.SceneImage
import app.pokeplan.ui.SpriteAction
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

/** Where the ground is on the town's scene, as fractions of the screen's height. */
private const val GROUND_TOP = 0.26f
private const val GROUND_BOTTOM = 0.56f

/** How many Pokémon walk the town at once: the buddy first, then the highest levels, so a big collection never slows the home screen. */
internal const val TOWN_CAP = 24

/** Who lives in the town: the buddy, then the others by level (highest first), up to [TOWN_CAP]. */
internal fun townResidents(all: List<OwnedPokemon>, buddyId: String?): List<OwnedPokemon> =
    all.sortedWith(compareByDescending<OwnedPokemon> { it.id == buddyId }.thenByDescending { it.level }.thenBy { it.id }).take(TOWN_CAP)

/**
 * The home screen's town: a real scene with every Pokémon you own (up to [TOWN_CAP]) wandering its ground with its own
 * idle animation. A tap gives its cry and a hop. The buddy walks a little larger. With Reduce motion they stand still.
 */
@Composable
internal fun Town(model: AppModel, scene: String, modifier: Modifier = Modifier) {
    val data = model.data
    val residents = townResidents(data.player.pokemon, data.player.buddy?.id)
    BoxWithConstraints(modifier) {
        SceneImage(scene, Modifier.fillMaxSize(), darkness = BuddyScene.darkness(model.store.now.hour, model.store.now.minute))
        residents.forEachIndexed { i, mon ->
            key(mon.id) { Resident(model, mon, isBuddy = mon.id == data.player.buddy?.id, maxWidth, maxHeight, calm = data.settings.reduceMotion, index = i) }
        }
    }
}

@Composable
private fun Resident(model: AppModel, mon: OwnedPokemon, isBuddy: Boolean, width: Dp, height: Dp, calm: Boolean, index: Int) {
    val random = remember(mon.id) { Random(mon.id.hashCode()) }
    // Position as fractions of the ground: x across the screen, y from the back of the ground (0) to the front (1).
    val spot = remember(mon.id) { Animatable(Offset(0.08f + random.nextFloat() * 0.84f, random.nextFloat()), Offset.VectorConverter) }
    var facingLeft by remember(mon.id) { mutableStateOf(random.nextBoolean()) }
    var hopKey by remember(mon.id) { mutableIntStateOf(0) }
    var action by remember(mon.id) { mutableStateOf(SpriteAction.IDLE) }
    if (!calm) LaunchedEffect(mon.id) {
        delay(300L + index * 150L)
        while (true) {
            delay(1500L + random.nextLong(5000))
            val to = Offset(0.08f + random.nextFloat() * 0.84f, random.nextFloat())
            facingLeft = to.x < spot.value.x
            // A steady stroll: about 6 seconds for the width of the screen.
            val seconds = 0.8f + abs(to.x - spot.value.x) * 6f + abs(to.y - spot.value.y) * 2f
            spot.animateTo(to, tween((seconds * 1000).toInt(), easing = LinearEasing))
        }
    }
    // Pokémon at the front of the ground are drawn larger and over those behind, for a little depth.
    val depth = spot.value.y
    val size = (if (isBuddy) 84f else 52f + 16f * depth).dp
    val groundH = height * (GROUND_BOTTOM - GROUND_TOP)
    val x = width * spot.value.x - size / 2
    val y = height * GROUND_TOP + groundH * depth - size
    Box(
        Modifier.offset(x, y).size(size).zIndex(depth + if (isBuddy) 0.01f else 0f)
            .scale(if (facingLeft) 1f else -1f, 1f)
            .pointerInput(mon.id) {
                detectTapGestures {
                    model.cry(mon.speciesId)
                    action = SpriteAction.HOP
                    hopKey++
                }
            },
    ) {
        AnimatedSprite(
            mon.form.id, mon.isShiny, mon.nickname, Modifier.fillMaxSize(), size = size, animate = !calm, ground = 2.dp,
            action = action, actionKey = hopKey, onActionDone = { action = SpriteAction.IDLE },
        )
    }
}
