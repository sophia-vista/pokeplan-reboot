package app.pokeplan.ui.screens

import app.pokeplan.ui.theme.Sizes
import app.pokeplan.ui.theme.DialogueShape

import app.pokeplan.ui.theme.PixelShape
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import app.pokeplan.ui.StatusKind
import app.pokeplan.ui.StatusIcon
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
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.sp
import app.pokeplan.app.BuddyEvent
import app.pokeplan.app.BuddyEventNow
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.Emotion
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.Portrait
import app.pokeplan.ui.SceneImage
import app.pokeplan.ui.SpriteAction
import app.pokeplan.ui.hasAction
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * The buddy in its place: a real scene for its type (dimmed at night), the Pokémon itself as the one thing that moves,
 * and nothing else loud. Its own idle animation plays (a gentle bob for species without one), and its SpriteCollab Sleep
 * animation while a focus session runs ([resting]); level and progress sit small in a corner; there is no speech bubble.
 * A real event ([event], once) shows the buddy's portrait with a happy face in the corner and a hop; a pet answers with
 * a hop and one emoji, a berry with its Eat animation and one emoji. Forms without those animations hop in code instead.
 * [guests] (Care's visitors) stand a little behind it on either side, each with its own idle; tapping one is a pat.
 */
@Composable
fun BuddyStage(
    buddy: OwnedPokemon, scene: String, darkness: Float, progress: Float, event: BuddyEventNow?,
    calm: Boolean, resting: Boolean, berry: String?,
    onPet: (avoid: String?) -> String, onFeed: (avoid: String?) -> String, onOpen: () -> Unit, onHaptic: () -> Unit,
    /** A real event is being celebrated: the buddy calls out. */
    onEvent: () -> Unit,
    celebrated: Long, onCelebrated: (Long) -> Unit,
    guests: List<StageGuest> = emptyList(),
    modifier: Modifier = Modifier,
    /** A strip for a module page, where the work comes first: a shorter window and a smaller sprite. */
    compact: Boolean = false,
) {
    val stageHeight = if (compact) 144.dp else 248.dp
    val spriteSize = if (compact) 96.dp else 144.dp
    var taps by remember { mutableIntStateOf(0) }
    var reaction by remember { mutableStateOf<String?>(null) }
    var reactionId by remember { mutableIntStateOf(0) }
    val hop = remember { Animatable(0f) }
    var lastReaction by remember { mutableStateOf<String?>(null) }
    // The one-off animation the reaction plays (a hop, a bite); sleeping through a focus session there is none.
    var action by remember { mutableStateOf(SpriteAction.IDLE) }
    // The face a real event shows in the corner, while its reaction lasts.
    var face by remember { mutableStateOf<Emotion?>(null) }

    // Every reaction is one emoji above the buddy for a moment (an event shows its portrait instead), with a hop or a bite.
    fun react(emoji: String, move: SpriteAction = SpriteAction.HOP, emotion: Emotion? = null) {
        lastReaction = emoji
        reaction = emoji
        face = emotion
        action = if (resting) SpriteAction.IDLE else move
        reactionId++
    }
    LaunchedEffect(reactionId) {
        if (reactionId == 0) return@LaunchedEffect
        // The code hop stands in only for forms without their own animation for the move.
        if (!calm && !resting && !hasAction(buddy.form.id, buddy.isShiny, action)) {
            hop.animateTo(-16f, tween(110))
            hop.animateTo(0f, tween(150))
        }
        delay(if (reaction == BuddyEvent.SHINY.emoji) 3_800 else 2_200)
        reaction = null
        face = null
    }
    // A real event: the buddy's happy face, once per event (not again each time Home is opened).
    LaunchedEffect(event?.atMillis) {
        if (event == null || event.atMillis <= celebrated) return@LaunchedEffect
        onCelebrated(event.atMillis)
        onEvent()
        react(event.event.emoji, emotion = event.event.emotion())
    }

    // Species without an animation drift up and down very slowly so the scene is never frozen.
    val drift by rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(2_600, easing = app.pokeplan.ui.Ease.InOut), RepeatMode.Reverse))
    val bob = if (calm || resting) 0f else -3f * drift

    val shape = PixelShape
    // Tall enough for a whole hop: its frames hold the jump and draw at the idle scale, about 200 dp high at the peak.
    Box(modifier.fillMaxWidth().height(stageHeight).clip(shape).border(Sizes.Hairline, Pixel.Border, shape)) {
        SceneImage(scene, Modifier.matchParentSize(), darkness)
        // A small shadow made of a few flat rows, so it matches the pixels of the sprite.
        Canvas(Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp).size(Sizes.SpriteM, 12.dp)) {
            val rows = floatArrayOf(0.55f, 0.85f, 1f, 0.85f, 0.55f)
            val rowHeight = size.height / rows.size
            rows.forEachIndexed { i, share ->
                drawRect(Pixel.Scrim.copy(alpha = 0.25f), Offset((size.width - size.width * share) / 2, i * rowHeight), Size(size.width * share, rowHeight))
            }
        }
        // Visitors stand behind the buddy, one on each side, above the corner panels so neither covers the other.
        guests.take(2).forEachIndexed { i, guest ->
            StageGuestSprite(guest, calm, Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp).offset(x = if (i == 0) (-108).dp else 108.dp))
        }
        AnimatedSprite(
            buddy.form.id, buddy.isShiny, "${buddy.nickname}, level ${buddy.level}",
            Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp)
                .offset { IntOffset(0, (hop.value + bob).dp.roundToPx()) }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Pet ${buddy.nickname}", role = Role.Button) {
                    onHaptic()
                    react(onPet(lastReaction))
                },
            size = spriteSize, animate = !calm,
            action = if (resting) SpriteAction.SLEEP else action, actionKey = reactionId, onActionDone = { action = SpriteAction.IDLE },
        )

        // The Zz while it sleeps through a focus session.
        if (resting) StatusIcon(StatusKind.SLEEP, (3 * LocalDensity.current.density).roundToInt(), Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp + spriteSize * 0.72f).offset(x = 40.dp), loop = true, animate = !calm)
        if (buddy.isShiny && !calm && !resting) ShinyGlints(Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp).size(spriteSize))

        // Level and progress: small, in the corner, readable on any scene. Its corners are the stage's minus the inset, so
        // the two curves stay concentric.
        Column(
            Modifier.align(Alignment.BottomStart).padding(Spacing.S).clip(PixelShape).background(Pixel.Scrim.copy(alpha = 0.33f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Open ${buddy.nickname}", role = Role.Button, onClick = onOpen)
                // Small to look at, but a full touch target to hit.
                .defaultMinSize(minHeight = 40.dp)
                .padding(horizontal = Spacing.M, vertical = 7.dp)
                .semantics(mergeDescendants = true) { contentDescription = "${buddy.nickname}, level ${buddy.level}, ${(progress * 100).toInt()} percent to the next level" },
        ) {
            Text("${buddy.nickname}  Lv. ${buddy.level}", style = MaterialTheme.typography.labelMedium, color = Color.White)
            Box(Modifier.padding(top = 3.dp).width(84.dp).height(4.dp).background(Color.White.copy(alpha = 0.33f))) {
                Box(Modifier.width((84 * progress).dp).height(4.dp).background(Pixel.Yellow))
            }
        }

        AnimatedVisibility(reaction != null && face == null, Modifier.align(Alignment.Center).padding(bottom = if (compact) 96.dp else 150.dp), enter = fadeIn() + scaleIn(initialScale = 0.5f), exit = fadeOut()) {
            Text(reaction ?: "", fontSize = 30.sp, modifier = Modifier.semantics { contentDescription = "reaction" })
        }
        // A real event shows the buddy's own face in the corner, as the Mystery Dungeon games do beside a line of text.
        AnimatedVisibility(face != null, Modifier.align(Alignment.TopStart).padding(Spacing.S), enter = fadeIn() + scaleIn(initialScale = 0.7f), exit = fadeOut()) {
            Box {
                Portrait(buddy.form.id, buddy.isShiny, "${buddy.nickname} is delighted", emotion = face ?: Emotion.HAPPY, size = 64.dp)
                Text(reaction ?: "", fontSize = 20.sp, modifier = Modifier.align(Alignment.BottomEnd).offset(8.dp, 6.dp))
            }
        }

        if (berry != null) Box(
            Modifier.align(Alignment.BottomEnd).padding(Spacing.S).size(Spacing.Target).clip(DialogueShape).background(Color.White.copy(alpha = 0.8f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Give ${buddy.nickname} a ${Items.get(berry)?.name ?: berry}", role = Role.Button) {
                    onHaptic()
                    react(onFeed(lastReaction), SpriteAction.EAT)
                },
            contentAlignment = Alignment.Center,
        ) { ItemIcon(berry, size = 28.dp, description = Items.get(berry)?.name ?: berry) }
    }
}

/** The face an event shows: glad for a task, overjoyed for a hatch, awed by a shiny. */
private fun BuddyEvent.emotion() = when (this) {
    BuddyEvent.DONE -> Emotion.HAPPY
    BuddyEvent.HATCHED -> Emotion.JOYOUS
    BuddyEvent.SHINY -> Emotion.INSPIRED
}

/**
 * A few small four-point glints that flash around a shiny buddy, one at a time and never all together, like the shine in the
 * games. UI chrome drawn in code (no sprite art): each star is a little pointed shape that grows, then fades.
 */
@Composable
private fun ShinyGlints(modifier: Modifier = Modifier) {
    val loop by rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(7_200, easing = androidx.compose.animation.core.LinearEasing)))
    // Where each glint sits (share of the sprite's box) and when in the loop it flashes.
    val glints = remember { listOf(Triple(0.22f, 0.30f, 0.00f), Triple(0.78f, 0.22f, 0.27f), Triple(0.68f, 0.62f, 0.52f), Triple(0.30f, 0.70f, 0.77f)) }
    Canvas(modifier) {
        glints.forEach { (x, y, start) ->
            val local = (loop - start + 1f) % 1f
            if (local < 0.18f) {
                val k = local / 0.18f
                val power = kotlin.math.sin(k * 3.1415927f)
                val c = Offset(size.width * x, size.height * y)
                fun star(r: Float) = Path().apply {
                    moveTo(c.x, c.y - r)
                    quadraticTo(c.x, c.y, c.x + r, c.y)
                    quadraticTo(c.x, c.y, c.x, c.y + r)
                    quadraticTo(c.x, c.y, c.x - r, c.y)
                    quadraticTo(c.x, c.y, c.x, c.y - r)
                    close()
                }
                val r = size.minDimension * 0.11f * (0.4f + power)
                // A gold halo under a pale core, so the glint shows on light scenes too.
                drawPath(star(r * 1.3f), Color(0xFFFFB300).copy(alpha = power.coerceIn(0f, 1f) * 0.85f))
                drawPath(star(r), Color(0xFFFFFBE0).copy(alpha = power.coerceIn(0f, 1f)))
            }
        }
    }
}
