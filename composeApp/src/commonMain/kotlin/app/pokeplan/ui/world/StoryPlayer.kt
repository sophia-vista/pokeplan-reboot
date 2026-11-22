package app.pokeplan.ui.world

import app.pokeplan.ui.launcher.drawInnerCorners
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
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
import app.pokeplan.ui.CryVoice
import app.pokeplan.ui.Emotion
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.Portrait
import app.pokeplan.ui.SceneImage
import app.pokeplan.ui.SpriteAction
import app.pokeplan.ui.isGroundMap
import app.pokeplan.ui.orNull
import app.pokeplan.ui.rememberScene
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
import app.pokeplan.world.story.StoryChoice
import app.pokeplan.world.story.StoryEpisode
import app.pokeplan.world.story.StoryScene
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.decodeToImageBitmap
import pokeplan.composeapp.generated.resources.Res
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import app.pokeplan.ui.pokemon.label
import app.pokeplan.ui.screens.label
import app.pokeplan.app.world.worldEpisodeDone
import app.pokeplan.app.world.worldSceneDone
import app.pokeplan.app.world.worldStoryProgress

private const val GRID_COLUMNS = 10
private const val GRID_ROWS = 6

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
    Expression.DETERMINED -> Emotion.DETERMINED; Expression.SURPRISED -> Emotion.SURPRISED
    Expression.WORRIED -> Emotion.WORRIED; Expression.SAD -> Emotion.SAD; Expression.ANGRY -> Emotion.ANGRY; Expression.TEARY -> Emotion.TEARY
    Expression.NORMAL -> Emotion.NORMAL
}

@Composable
internal fun StoryPlayer(model: AppModel, episode: StoryEpisode, onClose: () -> Unit, fromStart: Boolean = false) {
    var index by remember(episode.key) { mutableStateOf(if (fromStart) 0 else (model.data.world.storyResume[episode.key] ?: 0).coerceIn(0, episode.scenes.lastIndex)) }
    var flags by remember(episode.key) { mutableStateOf(model.data.world.storyFlags) }
    val scene = episode.scenes.getOrNull(index)
    if (scene == null) { LaunchedEffect(episode.key) { model.store.worldEpisodeDone(episode.key, flags); onClose() }; return }
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
                SceneRunner(model, scene, flags, Modifier.weight(1f).widthIn(max = 520.dp).fillMaxWidth()) { newFlags ->
                    flags = newFlags
                    model.store.worldSceneDone(scene.id)
                    if (index + 1 < episode.scenes.size) model.store.worldStoryProgress(episode.key, index + 1, newFlags)
                    index++
                }
            }
        }
    }
}

@Composable
private fun SceneRunner(model: AppModel, scene: StoryScene, startFlags: Set<String>, modifier: Modifier, onEnd: (Set<String>) -> Unit) {
    val still = model.data.settings.reduceMotion
    var state by remember(scene.id) { mutableStateOf(SceneState.start(scene, startFlags)) }
    var question by remember(scene.id) { mutableStateOf<SceneEvent.Choice?>(null) }
    val answers = remember(scene.id) { Channel<StoryChoice>(Channel.CONFLATED) }
    var shake by remember(scene.id) { mutableStateOf(0f) }
    var flash by remember(scene.id) { mutableStateOf(0f) }
    val positions = remember(scene.id) { mutableStateMapOf<String, Offset>() }
    val walking = remember(scene.id) { mutableStateMapOf<String, Facing>() }
    var shown by remember(scene.id) { mutableStateOf("") }
    var typing by remember(scene.id) { mutableStateOf(false) }
    var fast by remember(scene.id) { mutableStateOf(false) }
    var skipped by remember(scene.id) { mutableStateOf(false) }
    var blackout by remember(scene.id) { mutableStateOf(0f) }
    val taps = remember(scene.id) { Channel<Unit>(Channel.CONFLATED) }

    LaunchedEffect(scene.id, skipped) {
        if (skipped) { val end = SceneState.finalOf(scene, startFlags); state = end; positions.clear(); walking.clear(); blackout = 0f; onEnd(end.flags); return@LaunchedEffect }
        fun buddyOf() = model.data.player.buddy ?: model.data.player.pokemon.firstOrNull()
        fun speciesOf(who: String): Int? = faceSpecies(scene, who) ?: if (who == app.pokeplan.world.story.BUDDY) buddyOf()?.speciesId else null
        fun voiceSeed(who: String): String = if (who == app.pokeplan.world.story.BUDDY) buddyOf()?.id ?: who else who
        var lineNo = 0
        val criedAt = mutableMapOf<String, Int>()
        if (!still) StoryCues.opening(scene).forEach { model.effect(it) }
        suspend fun runEvent(e: SceneEvent) {
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
                    val said = e.expression
                    val who = e.who
                    lineNo++
                    if (who != null && said != null) {
                        val before = state.actors[who]?.expression
                        val species = speciesOf(who)
                        if (species != null && StoryCues.cries(before, said) && lineNo - (criedAt[who] ?: -StoryCues.CRY_COOLDOWN_LINES) >= StoryCues.CRY_COOLDOWN_LINES) {
                            criedAt[who] = lineNo
                            model.cry(species, CryVoice.of(voiceSeed(who), said, StoryCues.distance(scene, state.actors[who])))
                            if (!still) delay(StoryCues.CRY_LEAD_MILLIS)
                        } else StoryCues.expression(before, said)?.let { model.effect(it) }
                    }
                    state = state.apply(e)
                    val full = fill(model, e.text)
                    typing = true; fast = false; shown = ""
                    for (i in 1..full.length) { if (fast || still) break; shown = full.take(i); delay(24) }
                    shown = full; typing = false
                    taps.receive()
                    model.tap(app.pokeplan.ui.Sfx.CURSOR)
                }
                is SceneEvent.Wait -> if (!still) delay(e.millis.toLong())
                is SceneEvent.Fade -> { val target = if (e.toBlack) 1f else 0f; if (still) blackout = target else Animatable(blackout).animateTo(target, tween(e.millis)) { blackout = value }; state = state.apply(e) }
                is SceneEvent.Emote -> { state = state.apply(e); StoryCues.emote(e.mark)?.let { model.effect(it) }; if (!still) delay(700); state = state.apply(SceneEvent.Emote(e.who, "")) }
                SceneEvent.End -> { state = state.apply(e) }
                is SceneEvent.Shake -> if (!still) { model.effect(StoryCues.shake(e.millis)); for (k in 0 until e.millis / 50) { shake = if (k % 2 == 0) 6f else -6f; delay(50) }; shake = 0f }
                is SceneEvent.Flash -> if (!still) { model.effect(StoryCues.flash()); flash = 1f; Animatable(1f).animateTo(0f, tween(450)) { flash = value } }
                is SceneEvent.Cry -> {
                    val voice = CryVoice.of(voiceSeed(e.who), state.actors[e.who]?.expression, StoryCues.distance(scene, state.actors[e.who]))
                    (speciesOf(e.who) ?: buddyOf()?.speciesId)?.let { model.cry(it, voice) }
                }
                is SceneEvent.Sound -> runCatching { app.pokeplan.ui.Sfx.valueOf(e.name) }.getOrNull()?.let { model.effect(it) }
                is SceneEvent.Choice -> {
                    e.prompt?.let { state = state.apply(SceneEvent.Say(null, it)); shown = fill(model, it) }
                    question = e
                    val picked = answers.receive()
                    question = null
                    state = state.apply(SceneEvent.Flag(picked.flag))
                }
                is SceneEvent.Branch -> if (e.flag in state.flags) for (n in e.events) runEvent(n)
                else -> state = state.apply(e)
            }
        }
        for (e in scene.events) runEvent(e)
        onEnd(state.flags)
    }

    BoxWithConstraints(modifier) {
    val widthPx = maxWidth.value * LocalDensity.current.density
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Box(Modifier.weight(1f).fillMaxWidth().clickable(remember { MutableInteractionSource() }, indication = null) { if (typing) fast = true else if (question == null) taps.trySend(Unit) }) {
            when (scene.type) {
                SceneType.DIALOGUE -> DialogueView(model, scene, state, still)
                SceneType.CINEMATIC -> Box(Modifier.fillMaxSize().offset(x = shake.dp)) { CinematicView(model, scene, state, positions, walking, still) }
                else -> if (onMap(scene)) StageView(model, scene, state, positions, walking, still)
                else Box(Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(GRID_COLUMNS.toFloat() / GRID_ROWS)) { StageView(model, scene, state, positions, walking, still) }
            }
            if (flash > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash)))
            if (blackout > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = blackout)))
        }
        val line = state.line
        val boxPixel = app.pokeplan.ui.launcher.townScale(widthPx)
        app.pokeplan.ui.launcher.PmdBox(boxPixel, Modifier.fillMaxWidth().height(112.dp)) {
            val name = line?.who?.let { speakerName(model, scene, it) }?.let { fill(model, it) }
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    if (name != null) { pushStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFFFFE27A))); append("$name: "); pop() }
                    append(if (line != null) shown else "")
                },
                Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyLarge, color = Color.White,
            )
        }
        question?.let { q ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                q.options.forEach { o -> PixelButton(o.label, { answers.trySend(o) }, Modifier.fillMaxWidth(), Pixel.Blue, compact = true) }
            }
        }
        Text(if (line != null && !typing && question == null) "Tap to continue" else " ", Modifier.padding(horizontal = Spacing.S), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f))
        Row(Modifier.fillMaxWidth().padding(bottom = Spacing.S), horizontalArrangement = Arrangement.End) { PixelButton("Skip scene", { skipped = true }, color = Pixel.InkSoft, compact = true) }
    }
    }
}

@Composable
private fun DialogueView(model: AppModel, scene: StoryScene, state: SceneState, still: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cell = maxWidth / GRID_COLUMNS
        val onStage = state.actors.values.toList().take(2)
        if (onMap(scene)) {
            val spots = onStage.indices.map { Offset(if (it == 0) 3.6f else 6.4f, 3.6f) }
            MapStage(scene.background, spots, null, still) { map ->
                onStage.forEachIndexed { i, a ->
                    val face = faceOf(model, scene, a.id) ?: return@forEachIndexed
                    Box(Modifier.offset { map.feet(spots[i].x, spots[i].y) - IntOffset(map.box / 2, map.box) }.size(map.dp(map.box))) {
                        AnimatedSprite(face.formId, face.shiny, face.name, size = map.dp(map.box), animate = !still, ground = 0.dp, mirror = i == 1, fixedScale = map.pixel)
                    }
                }
            }
        } else {
            SceneImage(scene.background, Modifier.fillMaxSize(), darkness = 0.1f)
            onStage.forEachIndexed { i, a ->
                val face = faceOf(model, scene, a.id) ?: return@forEachIndexed
                val size = cell * 2.4f
                Box(Modifier.offset(x = cell * (if (i == 0) 3.0f else 7.0f) - size / 2, y = maxHeight * 0.66f - size)) {
                    AnimatedSprite(face.formId, face.shiny, face.name, size = size, animate = !still, ground = 0.dp, mirror = i == 1)
                }
            }
        }
        val speaker = state.line?.who?.let { who -> onStage.indexOfFirst { it.id == who }.takeIf { it >= 0 }?.let { it to onStage[it] } }
        speaker?.let { (i, a) ->
            val face = faceOf(model, scene, a.id) ?: return@let
            val expression = state.line?.expression ?: a.expression
            Box(Modifier.align(if (i == 0) Alignment.BottomStart else Alignment.BottomEnd).padding(horizontal = Spacing.S, vertical = Spacing.S)) {
                Portrait(face.formId, face.shiny, face.name, emotion = emotionOf(expression), size = 88.dp, framed = true)
            }
        }
    }
}

private const val MAP_TILE = 24
private const val SPRITE_BOX = MAP_TILE * 3

private const val STAGE_CELL = 40f

private const val SPRITE_MIDDLE = 20f

private class StageGrid(val cell: Float, private val x0: Float, private val y0: Float) {
    fun mapX(x: Float) = x0 + x * cell
    fun mapY(y: Float) = y0 + y * cell

    companion object {
        fun of(mapWidth: Int, mapHeight: Int): StageGrid {
            val cell = min(STAGE_CELL, min(mapWidth / GRID_COLUMNS.toFloat(), mapHeight / GRID_ROWS.toFloat()))
            return StageGrid(cell, (mapWidth - cell * GRID_COLUMNS) / 2, (mapHeight - cell * GRID_ROWS) / 2)
        }
    }
}

private class MapScope(val pixel: Int, private val originX: Int, private val originY: Int, private val grid: StageGrid, private val density: Float) {
    val box: Int = SPRITE_BOX * pixel

    fun feet(x: Float, y: Float) = IntOffset((grid.mapX(x).roundToInt() - originX) * pixel, (grid.mapY(y).roundToInt() - originY) * pixel)

    fun dp(px: Int): Dp = (px / density).dp
}

private fun onMap(scene: StoryScene) = scene.dungeonId == null && isGroundMap(scene.background)

@Composable
private fun MapStage(
    background: String, actorsAt: List<Offset>, speakerAt: Offset?, still: Boolean, modifier: Modifier = Modifier.fillMaxSize(),
    darkness: Float = 0.1f, zoom: Int = 0, content: @Composable BoxScope.(MapScope) -> Unit,
) {
    val image = rememberScene(background)
    BoxWithConstraints(modifier.clipToBounds()) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        if (image == null) { Box(Modifier.fillMaxSize().background(Color(0xFF8FCB7F))); return@BoxWithConstraints }
        val pixel = max(max((w / 256f).roundToInt(), ceil(w / image.width.toFloat()).toInt()), ceil(h / image.height.toFloat()).toInt()).coerceAtLeast(1) + zoom
        val viewW = ceil(w / pixel.toFloat()).toInt().coerceAtMost(image.width)
        val viewH = ceil(h / pixel.toFloat()).toInt().coerceAtMost(image.height)
        val grid = remember(image) { StageGrid.of(image.width, image.height) }
        fun mapAt(p: Offset) = Offset(grid.mapX(p.x), grid.mapY(p.y) - SPRITE_MIDDLE)
        val points = actorsAt.map(::mapAt)
        val target = when {
            points.isEmpty() -> Offset(image.width / 2f, image.height / 2f)
            else -> {
                val left = points.minOf { it.x }; val right = points.maxOf { it.x }; val top = points.minOf { it.y }; val bottom = points.maxOf { it.y }
                if (right - left + SPRITE_BOX <= viewW * 0.85f && bottom - top + SPRITE_BOX <= viewH * 0.85f) Offset((left + right) / 2, (top + bottom) / 2)
                else speakerAt?.let(::mapAt) ?: Offset(points.sumOf { it.x.toDouble() }.toFloat() / points.size, points.sumOf { it.y.toDouble() }.toFloat() / points.size)
            }
        }
        val look by animateValueAsState(target, Offset.VectorConverter, animationSpec = if (still) snap() else tween(500))
        val originX = (look.x - viewW / 2f).roundToInt().coerceIn(0, image.width - viewW)
        val originY = (look.y - viewH / 2f).roundToInt().coerceIn(0, image.height - viewH)
        Canvas(Modifier.fillMaxSize()) {
            val src = IntSize(viewW, viewH)
            drawImage(image, IntOffset(originX, originY), src, IntOffset.Zero, IntSize(src.width * pixel, src.height * pixel), filterQuality = FilterQuality.None)
            if (darkness > 0f) drawRect(Pixel.Night.copy(alpha = darkness.coerceIn(0f, 1f)))
        }
        content(MapScope(pixel, originX, originY, grid, LocalDensity.current.density))
    }
}

private fun actionOf(move: Facing?) = when (move) {
    Facing.UP -> SpriteAction.WALK_UP; Facing.DOWN -> SpriteAction.WALK_DOWN; Facing.LEFT, Facing.RIGHT -> SpriteAction.WALK_SIDE; null -> SpriteAction.IDLE
}

private fun spotsOf(state: SceneState, positions: Map<String, Offset>): List<Offset> = state.actors.values.map { positions[it.id] ?: Offset(it.x, it.y) }

@Composable
private fun MapActors(model: AppModel, scene: StoryScene, state: SceneState, positions: Map<String, Offset>, walking: Map<String, Facing>, map: MapScope, still: Boolean, big: Boolean = false) {
    state.actors.values.sortedBy { positions[it.id]?.y ?: it.y }.forEach { a ->
        val face = faceOf(model, scene, a.id) ?: return@forEach
        val scale = if (big && a.id.startsWith("big_")) 2 else 1
        val boxPx = map.box * scale
        val move = walking[a.id]
        Box(Modifier.offset { (positions[a.id] ?: Offset(a.x, a.y)).let { at -> map.feet(at.x, at.y) } - IntOffset(boxPx / 2, boxPx) }.size(map.dp(boxPx))) {
            AnimatedSprite(
                face.formId, face.shiny, face.name, size = map.dp(boxPx), animate = !still, ground = 0.dp, action = actionOf(move),
                mirror = move == Facing.LEFT, fixedScale = map.pixel * scale,
            )
            a.emote?.let { Text(it, Modifier.align(Alignment.BottomCenter).offset(y = -map.dp(map.pixel * 44 * scale)), style = MaterialTheme.typography.headlineMedium, color = Color(0xFFFFD33D)) }
        }
    }
}

@Composable
private fun StageView(model: AppModel, scene: StoryScene, state: SceneState, positions: Map<String, Offset>, walking: Map<String, Facing>, still: Boolean) {
    if (onMap(scene)) {
        val speaker = state.line?.who?.let { who -> state.actors[who] }?.let { positions[it.id] ?: Offset(it.x, it.y) }
        MapStage(scene.background, spotsOf(state, positions), speaker, still) { map ->
            state.items.forEach { (id, p) -> ItemIcon(id, Modifier.offset { map.feet(p.first, p.second) - IntOffset(map.box / 6, map.box / 3) }, size = map.dp(map.box / 3), description = null) }
            MapActors(model, scene, state, positions, walking, map, still)
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cell = maxWidth / GRID_COLUMNS
        if (scene.type == SceneType.DUNGEON && scene.dungeonId != null) DungeonFloor(scene.dungeonId!!) else SceneImage(scene.background, Modifier.fillMaxSize(), darkness = 0.1f)
        state.items.forEach { (id, p) ->
            ItemIcon(id, Modifier.offset(x = cell * p.first - cell * 0.4f, y = cell * p.second - cell * 0.8f), size = cell * 0.8f, description = null)
        }
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

@Composable
private fun DungeonFloor(dungeonId: String) {
    val tiles = dungeonTiles(dungeonId)
    val atlas by produceState<ImageBitmap?>(null, tiles) { value = orNull { Res.readBytes("files/tiles/$tiles.png").decodeToImageBitmap() } }
    val tileSize = 24
    val alternates = remember(atlas) { atlas?.let { app.pokeplan.ui.launcher.alternatesFit(it) } ?: booleanArrayOf(true, false, false) }
    Canvas(Modifier.fillMaxSize()) {
        val image = atlas ?: return@Canvas drawRect(Color(0xFF2A2A2A))
        val pixel = (size.width / (tileSize * GRID_COLUMNS)).toInt().coerceAtLeast(1)
        val tilePx = tileSize * pixel
        val cols = (size.width / tilePx).toInt() + 2
        val rows = (size.height / tilePx).toInt() + 2
        fun put(ax: Int, ay: Int, x: Int, y: Int) =
            drawImage(image, IntOffset(ax * tileSize, ay * tileSize), IntSize(tileSize, tileSize), IntOffset(x, y), IntSize(tilePx, tilePx), filterQuality = FilterQuality.None)
        val seed = dungeonId.hashCode()
        val fit = alternates
        val map = app.pokeplan.ui.launcher.DungeonFloorLayout.build(cols, rows, seed, floorRows = 4)
        val shift = ((cols * tilePx - size.width) / 2).toInt() / pixel * pixel
        for (r in 0 until rows) for (c in 0 until cols) {
            val (ax, ay) = map.atlas(c, r, seed).let { t -> if (t.second == 3 && !fit[t.first]) 0 to 3 else t }
            put(ax, ay, c * tilePx - shift, r * tilePx)
            map.innerCorners(c, r).let { corners -> if (corners != 0) drawInnerCorners(image, corners, c * tilePx - shift, r * tilePx, tileSize, pixel) }
        }
    }
}

private fun faceSpecies(scene: StoryScene, who: String): Int? = scene.cast.firstOrNull { it.id == who }?.speciesId

@Composable
private fun CinematicView(model: AppModel, scene: StoryScene, state: SceneState, positions: Map<String, Offset>, walking: Map<String, Facing>, still: Boolean) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (onMap(scene)) {
            MapStage(scene.background, spotsOf(state, positions), state.line?.who?.let { who -> state.actors[who] }?.let { positions[it.id] ?: Offset(it.x, it.y) }, still, darkness = 0.3f, zoom = 1) { map ->
                MapActors(model, scene, state, positions, walking, map, still, big = true)
            }
        } else Box(Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(GRID_COLUMNS.toFloat() / GRID_ROWS)) {
            if (scene.dungeonId != null) DungeonFloor(scene.dungeonId!!) else SceneImage(scene.background, Modifier.fillMaxSize(), darkness = 0.3f)
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val cell = maxWidth / GRID_COLUMNS
                state.actors.values.sortedBy { positions[it.id]?.y ?: it.y }.forEach { a ->
                    val face = faceOf(model, scene, a.id) ?: return@forEach
                    val at = positions[a.id] ?: Offset(a.x, a.y)
                    val size = cell * if (a.id.startsWith("big_")) 4.6f else 2.2f
                    val move = walking[a.id]
                    Box(Modifier.offset(x = cell * at.x - size / 2, y = cell * at.y - size)) {
                        AnimatedSprite(
                            face.formId, face.shiny, face.name, size = size, animate = !still, ground = 0.dp,
                            action = when (move) { Facing.UP -> SpriteAction.WALK_UP; Facing.DOWN -> SpriteAction.WALK_DOWN; Facing.LEFT, Facing.RIGHT -> SpriteAction.WALK_SIDE; null -> SpriteAction.IDLE },
                            mirror = move == Facing.LEFT,
                        )
                    }
                }
            }
        }
        if (state.letterbox) { Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(36.dp).background(Color.Black)); Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(36.dp).background(Color.Black)) }
    }
}
