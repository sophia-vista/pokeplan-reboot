package app.pokeplan.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.pokeplan.care.Bid
import app.pokeplan.care.CareAction
import app.pokeplan.care.PetSpot
import app.pokeplan.care.Reaction
import app.pokeplan.care.Visit
import app.pokeplan.care.VisitStep
import app.pokeplan.care.Visitor
import app.pokeplan.care.Visitors
import app.pokeplan.core.dex.Dex
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.reward.Reward
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.SceneStage
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.SpriteAction
import app.pokeplan.ui.hasAction
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.Spacing
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** A visitor standing in the buddy's scene: its form, its name, and what a tap does (a pat). */
class StageGuest(val formId: Int, val name: String, val onTap: () -> Unit)

/** A visitor beside the buddy: its own idle, and a tap answered with a hop and a heart. Still with Reduce motion. */
@Composable
internal fun StageGuestSprite(guest: StageGuest, calm: Boolean, modifier: Modifier) {
    var action by remember { mutableStateOf(SpriteAction.IDLE) }
    var taps by remember { mutableIntStateOf(0) }
    var heart by remember { mutableStateOf(false) }
    val lift = remember { Animatable(0f) }
    LaunchedEffect(taps) {
        if (taps == 0) return@LaunchedEffect
        heart = true
        if (!calm && !hasAction(guest.formId, false, SpriteAction.HOP)) {
            lift.animateTo(-12f, tween(110))
            lift.animateTo(0f, tween(150))
        }
        delay(1_600)
        heart = false
    }
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        AnimatedSprite(
            guest.formId, false, guest.name,
            Modifier.offset { IntOffset(0, lift.value.dp.roundToPx()) }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Pat ${guest.name}", role = Role.Button) {
                    guest.onTap()
                    if (!calm) action = SpriteAction.HOP
                    taps++
                },
            size = 80.dp, animate = !calm, ground = 6.dp,
            action = action, actionKey = taps, onActionDone = { action = SpriteAction.IDLE },
        )
        AnimatedVisibility(heart, Modifier.align(Alignment.TopCenter).offset(y = (-14).dp), enter = fadeIn() + scaleIn(initialScale = 0.5f), exit = fadeOut()) {
            Text("💕", fontSize = 20.sp)
        }
    }
}

internal fun CareAction.label() = when (this) {
    CareAction.WORDS -> "Kind words"
    CareAction.TIME -> "Sit together"
    CareAction.GIFT -> "Offer a treat"
    CareAction.SERVICE -> "Tidy its spot"
    CareAction.TOUCH -> "Gentle pat"
}

private fun Bid.text() = when (this) {
    Bid.LOOK -> "It looks up at you."
    Bid.NUDGE -> "It nudges your hand."
    Bid.SHOW -> "It brings you something small."
    Bid.PLAY -> "It does a little hop and looks back at you."
    Bid.SETTLE -> "It curls up a little closer."
}

private fun PetSpot.label() = when (this) {
    PetSpot.HEAD -> "its head"
    PetSpot.BACK -> "its back"
    PetSpot.CHIN -> "under its chin"
}

/** Free play: it warms nothing and pays nothing; it is only the visitor's answer. */
private enum class Play(val label: String) { PAT("Pat"), TREAT("Treat"), TAG("Play tag") }

/** What the player can say for "Kind words": warm and plain, and never about how anyone feels. */
private val kindWords = listOf("I'm glad you came.", "Take all the time you need.", "You're welcome here.")

/** How long sitting together takes, held. */
private const val SIT_MILLIS = 1_500

/** Swipes across the meadow that tidy the spot, in widths of the scene. */
private const val TIDY_WIDTHS = 2.5f

/**
 * A visitor up close in the meadow. During a [visit] each kind of care is done with the visitor itself: a pat lands where
 * you tap it (its head, under its chin, or its back), a treat is dragged to it, sitting together is a press held on it,
 * tidying its spot is a few swipes across the meadow, and kind words are picked from three. A bid is answered by tapping it.
 * With no visit it is free play (a pat, a treat, a game of tag), which warms nothing and pays nothing. Every gesture also
 * has a plain action for screen readers, a treat can be tapped instead of dragged, and Reduce motion keeps the visitor still.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VisitorScene(model: AppModel, visitor: Visitor, visit: Visit?, say: (String?) -> Unit, onClose: () -> Unit) {
    val care = model.care
    val reduce = model.data.settings.reduceMotion
    val name = Dex.species(visitor.speciesId).name
    val formId = Dex.form(visitor.formIdentifier)?.id ?: Dex.defaultForm(visitor.speciesId).id
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val visiting = visit != null

    var doing by remember(visitor.speciesId, visiting) { mutableStateOf<CareAction?>(null) }
    var play by remember(visitor.speciesId, visiting) { mutableStateOf<Play?>(null) }
    var action by remember { mutableStateOf(SpriteAction.IDLE) }
    var moves by remember { mutableIntStateOf(0) }
    var emoji by remember { mutableStateOf<String?>(null) }
    var pops by remember { mutableIntStateOf(0) }
    val lift = remember { Animatable(0f) }
    val roam = remember(visitor.speciesId) { Animatable(0f) }
    val hold = remember { Animatable(0f) }
    var holding by remember { mutableStateOf<Job?>(null) }
    var swept by remember { mutableFloatStateOf(0f) }
    var sceneWidth by remember { mutableIntStateOf(0) }
    var spriteBounds by remember { mutableStateOf(Rect.Zero) }

    // A hop or a bite with its own SpriteCollab animation; a small hop in code for forms without one; nothing with Reduce motion.
    fun move(next: SpriteAction) {
        if (reduce) return
        action = next
        moves++
    }
    LaunchedEffect(moves) {
        if (moves == 0 || action != SpriteAction.HOP || hasAction(formId, false, SpriteAction.HOP)) return@LaunchedEffect
        lift.animateTo(-14f, tween(110))
        lift.animateTo(0f, tween(150))
    }
    fun pop(face: String) {
        emoji = face
        pops++
    }
    LaunchedEffect(pops) {
        if (pops == 0) return@LaunchedEffect
        delay(1_600)
        emoji = null
    }

    // One kind of care done: the visitor's answer in sound, movement, a face and a line, and the visit's end when it ends.
    fun answer(step: VisitStep?, given: CareAction) {
        doing = null
        if (step == null) return say("Not now.")
        model.haptic()
        val result = step.result
        when {
            result?.friend == true -> { model.effect(Sfx.POKEMON_CENTER); model.cry(visitor.speciesId); pop("🎉") }
            step.reaction == Reaction.LOVED -> { model.cry(visitor.speciesId); pop("💖") }
            given == CareAction.GIFT && step.reaction != Reaction.FLAT -> { model.effect(Sfx.HEAL); pop("😋") }
            step.reaction == Reaction.LIKED -> { model.effect(Sfx.SELECT); pop("😊") }
            else -> pop("😌")
        }
        when {
            step.reaction == Reaction.LOVED -> move(SpriteAction.HOP)
            given == CareAction.GIFT && step.reaction != Reaction.FLAT -> move(SpriteAction.EAT)
        }
        val hatched = result?.rewards?.filterIsInstance<Reward.Hatched>()?.firstOrNull()?.pokemon
        say(
            when {
                result?.friend == true && result.joined -> "$name wants to stay with you! It joined you."
                result?.friend == true -> "$name left you an Egg before heading home. It hatched: ${hatched?.nickname ?: "a new friend"}!"
                result != null ->
                    if (result.visitor.points == 0) "$name had a quiet visit. Try again tomorrow."
                    else "$name liked the visit. ${Visitors.FRIEND_POINTS - result.visitor.points} more warm days."
                step.visit.berry != null && step.visit.berry == Visitors.likedBerry(visitor.speciesId) -> "$name's favourite berry! It will remember that."
                step.reaction == Reaction.LOVED -> "$name loved that."
                step.reaction == Reaction.LIKED -> "$name liked that."
                else -> "$name already had that today."
            },
        )
    }
    fun act(given: CareAction, spot: PetSpot? = null, berry: String? = null) = answer(care.visitAct(given, spot, berry), given)

    // Free play: a pat, a treat, or bounding over to where the meadow was tapped. Nothing is counted.
    fun played(kind: Play) {
        model.haptic()
        when (kind) {
            Play.PAT -> { model.cry(visitor.speciesId); move(SpriteAction.HOP); pop("💕"); say("$name leans into your hand.") }
            Play.TREAT -> { model.effect(Sfx.HEAL); move(SpriteAction.EAT); pop("😋"); say("$name munches it happily.") }
            Play.TAG -> { move(SpriteAction.HOP); pop("✨"); say("$name bounds over.") }
        }
    }
    fun answerBid() {
        if (care.visitNotice() == null) return
        model.haptic()
        model.effect(Sfx.JUMP)
        move(SpriteAction.HOP)
        pop("🎵")
        say("You noticed. $name looks pleased.")
    }
    fun spotAt(y: Float, height: Int): PetSpot = when {
        y < height * 0.40f -> PetSpot.HEAD
        y < height * 0.62f -> PetSpot.CHIN
        else -> PetSpot.BACK
    }
    fun offered(point: Offset): Boolean = spriteBounds.inflate(with(density) { 24.dp.toPx() }).contains(point)

    val bid = visit?.pendingBid
    val treats = doing == CareAction.GIFT || play == Play.TREAT

    SceneStage("meadow", 208.dp) {
        // The meadow itself: swipes tidy the spot, and in tag a tap anywhere calls the visitor over.
        Box(
            Modifier.matchParentSize().onSizeChanged { sceneWidth = it.width }.pointerInput(doing, play) {
                when {
                    doing == CareAction.SERVICE -> detectHorizontalDragGestures { change, dx ->
                        change.consume()
                        swept += abs(dx) / (sceneWidth * TIDY_WIDTHS).coerceAtLeast(1f)
                        if (swept >= 1f) {
                            swept = 0f
                            act(CareAction.SERVICE)
                        }
                    }
                    play == Play.TAG -> detectTapGestures { at ->
                        val reach = sceneWidth / 2f - 56.dp.toPx()
                        val target = ((at.x - sceneWidth / 2f).coerceIn(-reach, reach)).toDp().value
                        scope.launch { if (reduce) roam.snapTo(target) else roam.animateTo(target, tween(420)) }
                        played(Play.TAG)
                    }
                }
            },
        )
        // The visitor: what a tap or a press on it means depends on the kind of care chosen.
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp)
                .offset { IntOffset(roam.value.dp.roundToPx(), lift.value.dp.roundToPx()) }
                .onGloballyPositioned { spriteBounds = it.boundsInRoot() }
                .pointerInput(doing, play, bid) {
                    when {
                        doing == CareAction.TOUCH -> detectTapGestures { at -> act(CareAction.TOUCH, spotAt(at.y, size.height)) }
                        doing == CareAction.TIME -> detectTapGestures(onPress = {
                            holding = scope.launch {
                                hold.snapTo(0f)
                                hold.animateTo(1f, tween(SIT_MILLIS, easing = LinearEasing))
                                act(CareAction.TIME)
                                hold.snapTo(0f)
                            }
                            tryAwaitRelease()
                            if (hold.value < 1f) {
                                holding?.cancel()
                                scope.launch { hold.animateTo(0f, tween(150)) }
                            }
                        })
                        doing == CareAction.SERVICE || play == Play.TAG -> Unit // the meadow takes these
                        play == Play.PAT -> detectTapGestures { played(Play.PAT) }
                        bid != null -> detectTapGestures { answerBid() }
                        else -> detectTapGestures {
                            model.haptic()
                            model.cry(visitor.speciesId)
                            move(SpriteAction.HOP)
                        }
                    }
                }
                .semantics {
                    contentDescription = name
                    customActions = buildList {
                        if (doing == CareAction.TOUCH) PetSpot.entries.forEach { s -> add(CustomAccessibilityAction("Pat ${s.label()}") { act(CareAction.TOUCH, s); true }) }
                        if (doing == CareAction.TIME) add(CustomAccessibilityAction("Sit together") { act(CareAction.TIME); true })
                        if (doing == CareAction.SERVICE) add(CustomAccessibilityAction("Tidy its spot") { act(CareAction.SERVICE); true })
                        if (play == Play.PAT) add(CustomAccessibilityAction("Pat $name") { played(Play.PAT); true })
                        if (play == Play.TAG) add(CustomAccessibilityAction("Call $name over") { played(Play.TAG); true })
                        if (bid != null && doing == null) add(CustomAccessibilityAction("Answer $name") { answerBid(); true })
                    }
                },
        ) {
            AnimatedSprite(
                formId, false, name, size = 112.dp, animate = !reduce,
                action = action, actionKey = moves, onActionDone = { action = SpriteAction.IDLE },
            )
        }
        AnimatedVisibility(
            emoji != null, Modifier.align(Alignment.TopCenter).padding(top = Spacing.L).offset { IntOffset(roam.value.dp.roundToPx(), 0) },
            enter = fadeIn() + scaleIn(initialScale = 0.5f), exit = fadeOut(),
        ) { Text(emoji ?: "", fontSize = 28.sp) }
        // Treats wait in a row along the top: drag one to the visitor, or tap it.
        if (treats) Row(Modifier.align(Alignment.TopStart).padding(Spacing.S).zIndex(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            DraggableTreat("Treat", null, "a little treat", { p -> if (offered(p)) { if (visiting) act(CareAction.GIFT) else played(Play.TREAT) } }) {
                if (visiting) act(CareAction.GIFT) else played(Play.TREAT)
            }
            if (visiting) care.berries().entries.sortedBy { it.key }.take(4).forEach { (berry, n) ->
                DraggableTreat("$n", berry, Items.name(berry), { p -> if (offered(p)) act(CareAction.GIFT, berry = berry) }) { act(CareAction.GIFT, berry = berry) }
            }
        }
        // How far the visit is, small in the corner like the buddy's level.
        if (visit != null) Text(
            "${visit.actions.size} of ${Visitors.ACTIONS_PER_VISIT}",
            Modifier.align(Alignment.TopEnd).padding(Spacing.S).background(Pixel.Scrim.copy(alpha = 0.33f), RoundedCornerShape(Spacing.CardRadius - Spacing.S))
                .padding(horizontal = Spacing.M, vertical = Spacing.XS)
                .semantics { contentDescription = "${visit.actions.size} of ${Visitors.ACTIONS_PER_VISIT} kinds of care this visit" },
            style = MaterialTheme.typography.labelMedium, color = Color.White,
        )
        val progress = if (doing == CareAction.TIME) hold.value else if (doing == CareAction.SERVICE) swept else 0f
        if (doing == CareAction.TIME || doing == CareAction.SERVICE) Box(Modifier.align(Alignment.BottomStart).padding(Spacing.S).width(120.dp)) {
            PixelBar(progress, color = Pixel.Green, label = if (doing == CareAction.TIME) "Sitting together" else "Tidying")
        }
    }

    Text(
        when {
            doing == CareAction.TOUCH -> "Tap where to pat $name: its head, under its chin, or its back."
            doing == CareAction.TIME -> "Press and hold $name to sit together for a moment."
            doing == CareAction.SERVICE -> "Swipe back and forth across the meadow to tidy $name's spot."
            doing == CareAction.GIFT -> "Drag a treat to $name, or tap one to offer it. Each kind of Pokémon likes one berry flavour."
            doing == CareAction.WORDS -> "What do you say to $name?"
            bid != null -> "${bid.text()} Tap $name to answer."
            visiting -> "Care for $name any way you like, one of each kind a visit. It has a favourite, and little moments it may offer."
            play == Play.PAT -> "Tap $name to pat it."
            play == Play.TREAT -> "Drag the treat to $name, or tap it."
            play == Play.TAG -> "Tap anywhere in the meadow and $name bounds over."
            else -> "Play with $name as long as you like. Playing is just for fun: it never counts as a visit."
        },
        style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    if (doing == CareAction.WORDS) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        kindWords.forEach { words -> PixelButton(words, { act(CareAction.WORDS) }, color = Pixel.Green, compact = true) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        if (visit != null) CareAction.entries.forEach { given ->
            val chosen = doing == given
            PixelButton(
                given.label(), { doing = if (chosen) null else given; swept = 0f },
                color = if (chosen) Pixel.Blue else Pixel.Green, enabled = given !in visit.actions, compact = true,
            )
        } else {
            Play.entries.forEach { kind ->
                PixelButton(kind.label, { play = if (play == kind) null else kind }, color = if (play == kind) Pixel.Blue else Pixel.Green, compact = true)
            }
            PixelIconButton("check", "Done", { say(null); onClose() }, Pixel.InkSoft)
        }
    }
}

/** A treat to drag to the visitor: a berry's real icon with how many are left, or a plain "Treat". Tapping offers it too. */
@Composable
private fun DraggableTreat(label: String, berry: String?, description: String, onDrop: (Offset) -> Unit, onTap: () -> Unit) {
    var drag by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.onGloballyPositioned { origin = it.boundsInRoot().center }
            .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onDrop(origin + drag); drag = Offset.Zero },
                    onDragCancel = { drag = Offset.Zero },
                ) { change, amount -> change.consume(); drag += amount }
            }
            .background(Pixel.Panel, shape).border(1.dp, Pixel.Border, shape)
            .clickable(onClickLabel = "Offer $description", role = Role.Button, onClick = onTap)
            .defaultMinSize(minHeight = Spacing.Target)
            .padding(horizontal = Spacing.M)
            .semantics(mergeDescendants = true) { contentDescription = description; onClick("Offer $description") { onTap(); true } },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
    ) {
        if (berry != null) ItemIcon(berry, size = 28.dp, description = null)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Pixel.Ink)
    }
}
