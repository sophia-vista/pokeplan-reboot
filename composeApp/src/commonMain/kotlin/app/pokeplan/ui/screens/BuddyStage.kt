package app.pokeplan.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.pokeplan.app.BuddyEventNow
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.SceneImage
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * The buddy in its place: a real scene for its type (dimmed at night), the Pokémon itself as the one thing that moves,
 * and nothing else loud. Its own idle animation plays (a gentle bob for species without one); level and progress sit
 * small in a corner; there is no speech bubble. A short message and a small celebration happen only for a real event
 * ([event], once), and a pet or a berry answers with a hop, hearts and the cry.
 */
@Composable
fun BuddyStage(
    buddy: OwnedPokemon, scene: String, night: Boolean, progress: Float, event: BuddyEventNow?,
    calm: Boolean, resting: Boolean, berry: String?,
    onPet: () -> Unit, onFeed: () -> Unit, celebrated: Long, onCelebrated: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var taps by remember { mutableIntStateOf(0) }
    var celebration by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val hop = remember { Animatable(0f) }

    // A pet or a berry: one hop and hearts.
    LaunchedEffect(taps) {
        if (taps == 0 || calm) return@LaunchedEffect
        hop.animateTo(-16f, tween(110))
        hop.animateTo(0f, tween(150))
    }
    // A real event: its message for a few seconds and two hops with sparkles, once per event.
    LaunchedEffect(event?.atMillis) {
        if (event == null || event.atMillis <= celebrated) return@LaunchedEffect
        onCelebrated(event.atMillis)
        celebration++
        message = event.event.message
        if (!calm) repeat(2) {
            hop.animateTo(-22f, tween(130))
            hop.animateTo(0f, tween(170))
        }
        delay(3_500)
        message = null
    }

    // Species without an animation drift up and down very slowly so the scene is never frozen.
    val drift by rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(2_600, easing = FastOutSlowInEasing), RepeatMode.Reverse))
    val bob = if (calm || resting) 0f else -3f * drift

    val shape = RoundedCornerShape(Spacing.CardRadius)
    Box(modifier.fillMaxWidth().height(216.dp).clip(shape).border(1.dp, Pixel.Border, shape)) {
        SceneImage(scene, Modifier.matchParentSize(), night)
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 38.dp).size(120.dp, 20.dp)
                .drawBehind { drawOval(Color(0x40000000), Offset.Zero, Size(size.width, size.height)) },
        )
        Sparkles(celebration, calm, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
        Hearts(taps, calm, Modifier.align(Alignment.BottomCenter).padding(bottom = 130.dp))
        AnimatedSprite(
            buddy.form.id, buddy.isShiny, "${buddy.nickname}, level ${buddy.level}",
            Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp)
                .offset { IntOffset(0, (hop.value + bob).dp.roundToPx()) }
                .clickable(onClickLabel = "Pet ${buddy.nickname}", role = Role.Button) {
                    onPet()
                    taps++
                },
            size = 144.dp, animate = !calm && !resting,
        )

        // Level and progress: small, in the corner, readable on any scene.
        Column(
            Modifier.align(Alignment.BottomStart).padding(Spacing.S).background(Color(0x55000000), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp)
                .semantics(mergeDescendants = true) { contentDescription = "${buddy.nickname}, level ${buddy.level}, ${(progress * 100).toInt()} percent to the next level" },
        ) {
            Text("${buddy.nickname}  Lv. ${buddy.level}", style = MaterialTheme.typography.labelMedium, color = Color.White)
            Box(Modifier.padding(top = 3.dp).width(84.dp).height(3.dp).background(Color(0x55FFFFFF), CircleShape)) {
                Box(Modifier.width((84 * progress).dp).height(3.dp).background(Pixel.Yellow, CircleShape))
            }
        }

        AnimatedVisibility(message != null, Modifier.align(Alignment.TopCenter).padding(top = Spacing.S), enter = fadeIn(), exit = fadeOut()) {
            Text(
                message ?: "", style = MaterialTheme.typography.labelMedium, color = Pixel.Ink,
                modifier = Modifier.background(Color(0xE6FFFFFF), RoundedCornerShape(50)).padding(horizontal = Spacing.M, vertical = 5.dp),
            )
        }

        if (berry != null) Box(
            Modifier.align(Alignment.BottomEnd).padding(Spacing.S).size(40.dp).background(Color(0xCCFFFFFF), CircleShape)
                .clickable(onClickLabel = "Give ${buddy.nickname} a ${Items.get(berry)?.name ?: berry}", role = Role.Button) {
                    onFeed()
                    taps++
                },
            contentAlignment = Alignment.Center,
        ) { ItemIcon(berry, size = 28.dp, description = Items.get(berry)?.name ?: berry) }
    }
}

/** A few hearts rise from the buddy and fade out after each pet. */
@Composable
private fun Hearts(taps: Int, calm: Boolean, modifier: Modifier = Modifier) {
    val rise = remember { Animatable(1f) }
    LaunchedEffect(taps) {
        if (taps == 0) return@LaunchedEffect
        rise.snapTo(0f)
        rise.animateTo(1f, tween(if (calm) 400 else 900))
    }
    if (rise.value >= 1f) return
    Box(modifier.size(96.dp, 80.dp).graphicsLayer { alpha = 1f - rise.value }) {
        listOf(0.dp to 0f, 40.dp to 0.25f, 72.dp to 0.1f).forEach { (x, delayShare) ->
            val t = ((rise.value - delayShare) / (1f - delayShare)).coerceIn(0f, 1f)
            Canvas(Modifier.offset(x = x, y = 60.dp - (if (calm) 0.dp else 60.dp * t)).size(22.dp)) {
                val w = size.width
                val h = size.height
                drawPath(
                    Path().apply {
                        moveTo(w / 2, h * 0.92f)
                        cubicTo(-w * 0.15f, h * 0.45f, w * 0.15f, -h * 0.1f, w / 2, h * 0.28f)
                        cubicTo(w * 0.85f, -h * 0.1f, w * 1.15f, h * 0.45f, w / 2, h * 0.92f)
                        close()
                    },
                    Pixel.Red,
                )
            }
        }
    }
}

/** A short burst of little stars around the buddy for a real event. */
@Composable
private fun Sparkles(celebration: Int, calm: Boolean, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(celebration) {
        if (celebration == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(if (calm) 500 else 1_400))
    }
    if (progress.value >= 1f) return
    Canvas(modifier.size(170.dp, 110.dp).graphicsLayer { alpha = 1f - progress.value }) {
        val t = progress.value
        listOf(
            Offset(0.12f, 0.70f) to 10f, Offset(0.30f, 0.25f) to 14f, Offset(0.55f, 0.10f) to 9f,
            Offset(0.78f, 0.30f) to 13f, Offset(0.90f, 0.72f) to 10f, Offset(0.5f, 0.55f) to 8f,
        ).forEach { (at, radius) ->
            val c = Offset(at.x * size.width, at.y * size.height - (if (calm) 0f else 26f * t))
            val r = radius * density
            drawPath(
                Path().apply {
                    moveTo(c.x, c.y - r); lineTo(c.x + r * 0.28f, c.y - r * 0.28f); lineTo(c.x + r, c.y)
                    lineTo(c.x + r * 0.28f, c.y + r * 0.28f); lineTo(c.x, c.y + r); lineTo(c.x - r * 0.28f, c.y + r * 0.28f)
                    lineTo(c.x - r, c.y); lineTo(c.x - r * 0.28f, c.y - r * 0.28f); close()
                },
                Pixel.Yellow,
            )
        }
    }
}
