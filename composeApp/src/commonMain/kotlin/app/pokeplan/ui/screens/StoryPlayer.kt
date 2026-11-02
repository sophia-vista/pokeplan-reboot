package app.pokeplan.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.pokeplan.core.dex.Dex
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Emotion
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.Portrait
import app.pokeplan.ui.SceneImage
import app.pokeplan.ui.SpriteAction
import app.pokeplan.ui.orNull
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.world.story.BUDDY
import app.pokeplan.world.story.Expression
import app.pokeplan.world.story.Facing
import app.pokeplan.world.story.SceneEvent
import app.pokeplan.world.story.SceneState
import app.pokeplan.world.story.SceneType
import app.pokeplan.world.story.StoryEpisode
import app.pokeplan.world.story.StoryScene
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.decodeToImageBitmap
import pokeplan.composeapp.generated.resources.Res
import kotlin.math.hypot

/** Columns across a stage or dungeon scene; rows are six. */
private const val GRID_COLUMNS = 10
private const val GRID_ROWS = 6

/** Who a character is on screen: its form, name and whether it is shiny. The buddy is whoever the player chose (never a fixed partner). */
private class Face(val formId: Int, val shiny: Boolean, val name: String)

private fun faceOf(model: AppModel, scene: StoryScene, who: String): Face? {
    val c = scene.cast.firstOrNull { it.id == who } ?: return null
    if (c.isBuddy) {
        val b: OwnedPokemon = model.data.player.buddy ?: model.data.player.pokemon.firstOrNull() ?: return null
        return Face(b.form.id, b.isShiny, b.nickname)
    }
    val species = c.speciesId ?: return null
    return Face(Dex.defaultForm(species).id, false, c.displayName)
}

private fun speakerName(model: AppModel, scene: StoryScene, who: String?): String? = who?.let { faceOf(model, scene, it)?.name }

private fun fill(model: AppModel, text: String): String =
    text.replace("{buddy}", (model.data.player.buddy ?: model.data.player.pokemon.firstOrNull())?.nickname ?: "your buddy").replace("{player}", model.data.trainerName)

private fun emotionOf(e: Expression): Emotion = when (e) {
    Expression.HAPPY -> Emotion.HAPPY; Expression.JOYOUS -> Emotion.JOYOUS; Expression.INSPIRED -> Emotion.INSPIRED
    Expression.DETERMINED -> Emotion.DETERMINED; Expression.SURPRISED, Expression.WORRIED -> Emotion.SURPRISED
    else -> Emotion.NORMAL
}

/**
 * Plays [episode]: its scenes one after another, each from its script (dialogue with portraits, a stage with walking sprites, a scripted
 * walk across a dungeon's own tiles). Tap to advance, Skip for a scene's end state, Close to leave; each scene watched to its end is recorded
 * (a record for the Story archive, never a reward, so replaying changes nothing).
 */
@Composable
internal fun StoryPlayer(model: AppModel, episode: StoryEpisode, onClose: () -> Unit) {
    var index by remember(episode.key) { mutableStateOf(0) }
    val scene = episode.scenes.getOrNull(index)
    if (scene == null) { LaunchedEffect(episode.key) { model.store.worldEpisodeDone(episode.key); onClose() }; return }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.safeDrawingPadding().fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.M, vertical = Spacing.S), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Column(Modifier.weight(1f)) {
                    Text(episode.title, style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Text("${scene.title} (${index + 1} of ${episode.scenes.size})", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
                }
                PixelIconButton("close", "Leave the story", onClose)
            }
            androidx.compose.runtime.key(scene.id) {
                SceneRunner(model, scene, Modifier.weight(1f).widthIn(max = 520.dp).fillMaxWidth()) {
                    model.store.worldSceneDone(scene.id)
                    index++
                }
            }
        }
    }
}

@Composable
private fun SceneRunner(model: AppModel, scene: StoryScene, modifier: Modifier, onEnd: () -> Unit) {
    val still = model.data.settings.reduceMotion
    var state by remember(scene.id) { mutableStateOf(SceneState.start(scene)) }
    val positions = remember(scene.id) { mutableStateMapOf<String, Offset>() }
    val walking = remember(scene.id) { mutableStateMapOf<String, Facing>() }
    var shown by remember(scene.id) { mutableStateOf("") }
    var typing by remember(scene.id) { mutableStateOf(false) }
    var fast by remember(scene.id) { mutableStateOf(false) }
    var skipped by remember(scene.id) { mutableStateOf(false) }
    var blackout by remember(scene.id) { mutableStateOf(0f) }
    val taps = remember(scene.id) { Channel<Unit>(Channel.CONFLATED) }

    LaunchedEffect(scene.id, skipped) {
        if (skipped) { state = SceneState.finalOf(scene); positions.clear(); walking.clear(); blackout = 0f; onEnd(); return@LaunchedEffect }
        for (e in scene.events) {
            when (e) {
                is SceneEvent.Show -> { state = state.apply(e); positions[e.who] = Offset(e.x, e.y) }
                is SceneEvent.Move -> state.actors[e.who]?.let { a ->
                    var cur = positions[e.who] ?: Offset(a.x, a.y)
                    for (p in e.path) {
                        val to = Offset(p.first, p.second)
                        val facing = SceneState.facingOf(cur.x to cur.y, p, state.actors[e.who]?.facing ?: a.facing)
                        state = state.apply(SceneEvent.Face(e.who, facing)); walking[e.who] = facing
                        if (still) positions[e.who] = to
                        else Animatable(cur, Offset.VectorConverter).animateTo(to, tween((hypot(to.x - cur.x, to.y - cur.y) * e.millisPerCell).toInt().coerceAtLeast(1), easing = LinearEasing)) { positions[e.who] = value }
                        cur = to
                    }
                    walking.remove(e.who)
                    state = state.apply(e)
                }
                is SceneEvent.Say -> {
                    state = state.apply(e)
                    val full = fill(model, e.text)
                    typing = true; fast = false; shown = ""
                    for (i in 1..full.length) { if (fast || still) break; shown = full.take(i); delay(24) }
                    shown = full; typing = false
                    taps.receive()
                }
                is SceneEvent.Wait -> if (!still) delay(e.millis.toLong())
                is SceneEvent.Fade -> { val target = if (e.toBlack) 1f else 0f; if (still) blackout = target else Animatable(blackout).animateTo(target, tween(e.millis)) { blackout = value }; state = state.apply(e) }
                is SceneEvent.Emote -> { state = state.apply(e); if (!still) delay(700); state = state.apply(SceneEvent.Emote(e.who, "")) }
                SceneEvent.End -> { state = state.apply(e) }
                else -> state = state.apply(e)
            }
        }
        onEnd()
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Box(Modifier.weight(1f).fillMaxWidth().clickable(remember { MutableInteractionSource() }, indication = null) { if (typing) fast = true else taps.trySend(Unit) }) {
            when (scene.type) {
                SceneType.DIALOGUE -> DialogueView(model, scene, state)
                // A stage or a dungeon is a ten by six grid, centred in the space with a black band round it.
                else -> Box(Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(GRID_COLUMNS.toFloat() / GRID_ROWS)) { StageView(model, scene, state, positions, walking, still) }
            }
            if (blackout > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = blackout)))
        }
        val line = state.line
        TextBox(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                if (line != null) {
                    speakerName(model, scene, line.who)?.let { Text(fill(model, it), style = MaterialTheme.typography.titleSmall, color = Pixel.Blue) }
                    Text(shown, Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyLarge)
                    Text(if (typing) "" else "Tap to continue", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                } else Text(" ", style = MaterialTheme.typography.bodyLarge)
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = Spacing.S), horizontalArrangement = Arrangement.End) { PixelButton("Skip scene", { skipped = true }, color = Pixel.InkSoft, compact = true) }
    }
}

/** A conversation: the background and up to two large portraits, the speaker bright and the other dimmed. */
@Composable
private fun DialogueView(model: AppModel, scene: StoryScene, state: SceneState) {
    Box(Modifier.fillMaxSize()) {
        SceneImage(scene.background, Modifier.fillMaxSize(), darkness = 0.15f)
        val onStage = state.actors.values.toList().take(2)
        onStage.forEachIndexed { i, a ->
            val face = faceOf(model, scene, a.id) ?: return@forEachIndexed
            val speaking = state.line?.who == a.id
            val expression = if (speaking) state.line?.expression ?: a.expression else a.expression
            Box(Modifier.align(if (i == 0) Alignment.BottomStart else Alignment.BottomEnd).padding(horizontal = Spacing.M, vertical = Spacing.S).alpha(if (state.line?.who == null || speaking) 1f else 0.55f)) {
                Portrait(face.formId, face.shiny, face.name, emotion = emotionOf(expression), size = 160.dp, framed = true)
            }
        }
    }
}

/** A stage or a dungeon walk: the place, the items on the floor and the sprites at their cells, walking when they move. */
@Composable
private fun StageView(model: AppModel, scene: StoryScene, state: SceneState, positions: Map<String, Offset>, walking: Map<String, Facing>, still: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cell = maxWidth / GRID_COLUMNS
        if (scene.type == SceneType.DUNGEON && scene.dungeonId != null) DungeonFloor(scene.dungeonId!!) else SceneImage(scene.background, Modifier.fillMaxSize(), darkness = 0.1f)
        state.items.forEach { (id, p) ->
            ItemIcon(id, Modifier.offset(x = cell * p.first - cell * 0.4f, y = cell * p.second - cell * 0.8f), size = cell * 0.8f, description = null)
        }
        // Back to front: the one further down the screen is drawn over the one above.
        state.actors.values.sortedBy { positions[it.id]?.y ?: it.y }.forEach { a ->
            val face = faceOf(model, scene, a.id) ?: return@forEach
            val at = positions[a.id] ?: Offset(a.x, a.y)
            val size = cell * 1.9f
            val move = walking[a.id]
            Box(Modifier.offset(x = cell * at.x - size / 2, y = cell * at.y - size)) {
                AnimatedSprite(
                    face.formId, face.shiny, face.name, size = size, animate = !still, ground = 0.dp,
                    action = when (move) { Facing.UP -> SpriteAction.WALK_UP; Facing.DOWN -> SpriteAction.WALK_DOWN; Facing.LEFT, Facing.RIGHT -> SpriteAction.WALK_SIDE; null -> SpriteAction.IDLE },
                    mirror = move == Facing.LEFT,
                )
                a.emote?.let { Text(it, Modifier.align(Alignment.TopCenter).offset(y = -(cell * 0.2f)), style = MaterialTheme.typography.headlineMedium, color = Color(0xFFFFD33D)) }
            }
        }
    }
}

/** A dungeon's own tiles as a floor with walls above and below, at a whole-number pixel scale, for a scripted walk. */
@Composable
private fun DungeonFloor(dungeonId: String) {
    val tiles = dungeonTiles(dungeonId)
    val atlas by produceState<ImageBitmap?>(null, tiles) { value = orNull { Res.readBytes("files/tiles/$tiles.png").decodeToImageBitmap() } }
    val tileSize = 24
    Canvas(Modifier.fillMaxSize()) {
        val image = atlas ?: return@Canvas drawRect(Color(0xFF2A2A2A))
        val pixel = (size.width / (tileSize * GRID_COLUMNS)).toInt().coerceAtLeast(1)
        val tilePx = tileSize * pixel
        val cols = (size.width / tilePx).toInt() + 2
        val rows = (size.height / tilePx).toInt() + 2
        fun put(ax: Int, ay: Int, x: Int, y: Int) =
            drawImage(image, IntOffset(ax * tileSize, ay * tileSize), IntSize(tileSize, tileSize), IntOffset(x, y), IntSize(tilePx, tilePx), filterQuality = FilterQuality.None)
        for (r in 0 until rows) for (c in 0 until cols) {
            val x = c * tilePx; val y = r * tilePx
            when { r == 0 -> put(1, 2, x, y); r <= 4 -> put(0, 3, x, y); r == 5 -> put(1, 0, x, y); else -> put(1, 1, x, y) }
        }
    }
}
