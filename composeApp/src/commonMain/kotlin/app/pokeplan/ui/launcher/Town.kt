package app.pokeplan.ui.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlin.time.TimeMark
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.pokeplan.app.BuddyScene
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.animations
import app.pokeplan.ui.loadedAnimations
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Emotion
import app.pokeplan.ui.SceneImage
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.SpriteAction
import app.pokeplan.ui.StatusIcon
import app.pokeplan.ui.StatusKind
import app.pokeplan.ui.orNull
import app.pokeplan.ui.CacheBudget
import app.pokeplan.ui.BitmapCache
import app.pokeplan.ui.rememberFadeIn
import app.pokeplan.ui.theme.Pixel
import androidx.compose.foundation.background
import app.pokeplan.ui.theme.PmdText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.decodeToImageBitmap
import pokeplan.composeapp.generated.resources.Res
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Where the ground is on a scene backdrop, as fractions of the screen's height. */
private const val SCENE_TOP = 0.14f
private const val SCENE_BOTTOM = 0.74f

/** How many Pokémon walk the town at once: the buddy first, then the highest levels, so a big collection never slows the home screen. */
internal const val TOWN_CAP = 24

/** The size of a dungeon tile, in the pixels of the art. */
private const val TILE = 24

/** How many tiles wide the screen is, to the nearest whole pixel scale. */
private const val TILES_ACROSS = 14f

/**
 * The town's pixel scale: screen pixels per pixel of the art, a whole number. The tiles, the Pokémon and every position
 * use it, so all the art in the town has the same pixel size and sits on the same pixel grid.
 */
internal fun townScale(widthPx: Float): Int = (widthPx / (TILE * TILES_ACROSS)).roundToInt().coerceAtLeast(1)

/**
 * Who lives in the town (LAUNCHER.md 6.1): the buddy first, then the Guardian, then the favourites, then the others; inside each of the last
 * two, Pokémon with a sprite sheet ([hasSprite]) before those without one, then by level (highest first), up to [TOWN_CAP]. At most one Guardian
 * walks the town ([shownGuardian] among [guardianIds]); the other Guardians stay in their modules.
 */
internal fun townResidents(
    all: List<OwnedPokemon>, buddyId: String?, cap: Int = TOWN_CAP, favorites: Set<String> = emptySet(), hasSprite: (OwnedPokemon) -> Boolean = { true },
    guardianIds: Collection<String> = emptySet(), rotation: Int = 0,
): List<OwnedPokemon> {
    val shown = shownGuardian(all, guardianIds, buddyId, rotation)?.id
    return all.filter { it.id !in guardianIds || it.id == shown }.sortedWith(
        compareByDescending<OwnedPokemon> { it.id == buddyId }.thenByDescending { it.id == shown }.thenByDescending { it.id in favorites }.thenByDescending { hasSprite(it) }
            .thenByDescending { it.level }.thenBy { it.id },
    ).take(cap.coerceAtMost(TOWN_CAP))
}

/**
 * The home screen's town: a real scene or a town of a dungeon's tiles, with every Pokémon you own (up to [TOWN_CAP])
 * wandering its ground with its own idle and walking animations. A tap gives its cry and a hop. With Reduce motion they
 * stand still. Everything is drawn at one whole-number [townScale] without smoothing, on one pixel grid.
 */
@Composable
internal fun Town(
    model: AppModel, scene: String, active: Boolean, onMenu: (OwnedPokemon) -> Unit, modifier: Modifier = Modifier, size: Int = TOWN_CAP, weather: Weather = Weather.CLEAR,
    screens: Int = 1, interactive: Boolean = true, gather: Int = 0, mood: BuddyMood? = null,
    /** Whether now and then one of them chirps (its cry, quietly, with a hop); see [TownChirps]. */
    chirps: Boolean = false,
    /** Which of the [screens] is Home: the one a gathering calls to and where meetings happen. */
    home: Int = 0,
    /** Where widgets cover the town (x in screens, y as a fraction of the height): a Pokémon prefers to walk elsewhere. */
    avoid: List<Rect> = emptyList(),
    /** Hands taps on Pokémon to the town when something drawn over it (the widget pages) would otherwise swallow them. */
    tapper: TownTapper? = null,
    /** Which Guardian of the day is shown when the buddy is not one (see [shownGuardian]); a change of it is a departure and an arrival. */
    rotation: Int = 0,
    /** The weather the Pokémon react to (a twinkle for one at home in it, a drop of sweat for one that is not). */
    reactions: Boolean = true,
) {
    val data = model.data
    // The sprite sheets are read once; until then everyone counts as having one, and the town re-picks when they arrive.
    val sheets by produceState(loadedAnimations()) { value = animations() }
    val guardianIds = data.player.moduleGuardians.values
    val residents = remember(data.player.pokemon, data.player.buddy?.id, size, data.favorites, sheets, guardianIds, rotation) {
        townResidents(data.player.pokemon, data.player.buddy?.id, size, data.favorites, hasSprite = { m -> sheets?.containsKey(m.form.id) ?: true }, guardianIds = guardianIds, rotation = rotation)
    }
    val guardian = shownGuardian(residents, guardianIds, data.player.buddy?.id, rotation)
    // When the Guardian changes (the day turned, or the buddy became one), the old one walks off the edge of the screen and the new one walks in; the first one shown just stands there.
    var seen by remember { mutableStateOf(guardian) }
    var departing by remember { mutableStateOf<OwnedPokemon?>(null) }
    var arriving by remember { mutableStateOf<String?>(null) }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current.density
        val widthPx = maxWidth.value * density
        val heightPx = maxHeight.value * density
        // The town may be [screens] screens wide: the launcher's pages are windows onto it, and a Pokémon can wander from one to the next.
        val screenPx = widthPx / screens
        val pixel = townScale(screenPx)
        androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
            repeat(screens) { TownBackdrop(scene, Modifier.weight(1f).fillMaxHeight(), weather = weather) }
        }
        // The night tint is one sheet over every screen of the town, not one per backdrop, so it reaches the right edge whatever the backdrops' widths.
        val dark = BuddyScene.darkness(model.store.now.hour, model.store.now.minute)
        if (dark > 0f) Box(Modifier.matchParentSize().background(Pixel.Night.copy(alpha = dark.coerceIn(0f, 1f))))
        val dungeon = TownScenes.isDungeon(scene)
        val top = if (dungeon) TownLayout.WALK_TOP else SCENE_TOP
        val bottom = if (dungeon) TownLayout.WALK_BOTTOM else SCENE_BOTTOM
        val calm = data.settings.reduceMotion || !active
        val life = remember { TownLife() }
        val darkNow = dark
        val scope = rememberCoroutineScope()
        LaunchedEffect(guardian?.id) {
            val before = seen
            seen = guardian
            if (before == null || guardian == null || before.id == guardian.id) return@LaunchedEffect
            // Only a Pokémon that is still owned has anywhere to walk off from; with Reduce motion, or off the screen, the change is just a change.
            if (!calm && data.player.pokemon.any { it.id == before.id }) departing = before
            if (!calm) arriving = guardian.id
        }
        LaunchedEffect(arriving) {
            val id = arriving ?: return@LaunchedEffect
            // It has walked in: the ones near it notice, a little (the Guardian is not startled back).
            delay(2_500)
            life.startleAround(id, residents.filter { !TownBehavior.asleep(it, darkNow) }.map { it.id }, Random(id.hashCode()), chance = 70, max = 3)
            delay(8_000)
            if (arriving == id) arriving = null
        }
        SideEffect {
            life.ripple = { source ->
                if (!calm && interactive) scope.launch { life.startleAround(source, residents.filter { !TownBehavior.asleep(it, darkNow) }.map { it.id }, Random.Default, chance = 40, max = 2) }
            }
        }
        TownMeetings(model, life, residents, calm || !interactive, mood, home, guardian?.id)
        TownGuardianAwe(life, residents, guardian, data.player.buddy?.id, calm || !interactive || arriving != null || departing != null, mood, home)
        TownChirps(model, life, residents, guardian?.id, data.player.buddy?.id, enabled = chirps && !calm && interactive)
        TownWeatherReactions(model, life, residents, weather, enabled = reactions && !calm && interactive)
        val boxPx = TILE * 3 * pixel
        if (tapper != null) SideEffect {
            tapper.hit = hit@{ at ->
                // Front ones first: they are drawn over the ones behind.
                val ids = residents.mapNotNull { m -> life.spots[m.id]?.let { m.id to it() } }.sortedByDescending { it.second.y }
                for ((id, spot) in ids) {
                    val feetX = screenPx * spot.x
                    val feetY = heightPx * (top + (bottom - top) * spot.y)
                    if (at.x in (feetX - boxPx / 2)..(feetX + boxPx / 2) && at.y in (feetY - boxPx)..feetY) { life.pets[id]?.invoke(); return@hit true }
                }
                false
            }
        }
        val buddyId = data.player.buddy?.id
        residents.forEachIndexed { i, mon ->
            key(mon.id) {
                Resident(
                    model, mon, screenPx, heightPx, pixel, top, bottom, calm = calm, index = i, life = life, asleep = TownBehavior.asleep(mon, darkNow), screens = screens, interactive = interactive, gather = gather,
                    onMenu = onMenu, home = home, avoid = avoid, tapperOn = tapper != null, buddy = mon.id == buddyId, guardian = mon.id == guardian?.id, entering = mon.id == arriving,
                    exposed = mon.id == buddyId || mon.id == guardian?.id || mon.id in data.favorites,
                )
            }
        }
        // The Guardian that is leaving walks off the edge and is gone.
        departing?.let { gone -> key("leaving-" + gone.id) {
            Resident(
                model, gone, screenPx, heightPx, pixel, top, bottom, calm = calm, index = 0, life = life, asleep = false, screens = screens, interactive = false, gather = gather,
                onMenu = onMenu, home = home, avoid = avoid, tapperOn = tapper != null, buddy = false, guardian = true, leaving = true, onGone = { departing = null }, exposed = false,
            )
        } }
        // The weather is over everything and never takes a touch.
        WeatherLayer(weather, pixel, dungeon, calm, Modifier.fillMaxSize().zIndex(10f))
    }
}

@Composable
private fun Resident(
    model: AppModel, mon: OwnedPokemon, widthPx: Float, heightPx: Float, pixel: Int, groundTop: Float, groundBottom: Float, calm: Boolean, index: Int, life: TownLife, asleep: Boolean, screens: Int,
    interactive: Boolean, gather: Int, onMenu: (OwnedPokemon) -> Unit, home: Int, avoid: List<Rect>, tapperOn: Boolean, buddy: Boolean, guardian: Boolean, exposed: Boolean,
    /** Walks in from the edge of Home instead of standing where it is. */
    entering: Boolean = false,
    /** Walks off the edge of Home, then calls [onGone]. */
    leaving: Boolean = false, onGone: () -> Unit = {},
) {
    val random = remember(mon.id) { Random(mon.id.hashCode()) }
    val anchor = remember(mon.id, screens, home, buddy) { TownBehavior.anchor(mon.id, screens, home, buddy) }
    val pace = remember(mon.id) { TownBehavior.pace(mon.id) }
    // Position as fractions of the ground: x across the screen, y from the back of the ground (0) to the front (1).
    val spot = remember(mon.id) { Animatable(if (entering) TownBehavior.entrance(random, home) else Offset(random.nextFloat() * screens, random.nextFloat()), Offset.VectorConverter) }
    var facingLeft by remember(mon.id) { mutableStateOf(false) }
    var hopKey by remember(mon.id) { mutableIntStateOf(0) }
    var hopping by remember(mon.id) { mutableStateOf(false) }
    var grazing by remember(mon.id) { mutableStateOf(false) }
    // How it is walking now (null while it stands): to the side, towards the viewer or away, whichever way it mostly goes.
    var walk by remember(mon.id) { mutableStateOf<SpriteAction?>(null) }
    // The last gathering this one answered, so a meeting or a sleep does not send it off again.
    var gathered by remember(mon.id) { mutableIntStateOf(gather) }
    var entered by remember(mon.id) { mutableStateOf(!entering) }
    val scope = rememberCoroutineScope()
    SideEffect { life.spots[mon.id] = { spot.value } }
    val avoidNow by rememberUpdatedState(avoid)
    val taps = remember(mon.id) { mutableListOf<TimeMark>() }
    /** A short feeling over its head, if nothing else holds it now. */
    fun feel(icon: StatusKind, millis: Long = 1_100) {
        if (mon.id in life.holds) return
        scope.launch {
            life.holds[mon.id] = Hold(icon)
            try { delay(millis) } finally { life.holds.remove(mon.id) }
        }
    }
    // A tap (from the Pokémon itself, or handed over by the launcher): its cry and one of a few brief reactions; the ones next to it may notice.
    SideEffect {
        life.pets[mon.id] = {
            taps.removeAll { it.elapsedNow() > 2.seconds }
            taps += TimeSource.Monotonic.markNow()
            when (TownBehavior.tapReaction(random, taps.size)) {
                TapReaction.HOP -> { model.cry(mon.speciesId); hopping = true; hopKey++ }
                TapReaction.HAPPY -> { model.cry(mon.speciesId); hopping = true; hopKey++; feel(StatusKind.TWINKLE) }
                TapReaction.TURN -> { facingLeft = !facingLeft; model.cry(mon.speciesId); hopping = true; hopKey++ }
                // Tapped over and over it is flustered and says nothing, rather than a cry per tap.
                TapReaction.FLUSTERED -> feel(StatusKind.SWEAT, 1_400)
            }
            life.ripple(mon.id)
        }
    }
    // Somewhere to walk to that no widget covers, if a few tries find one.
    fun pickTarget(): Offset {
        var t = TownBehavior.target(random, anchor, screens, guardian)
        repeat(10) {
            val fy = groundTop + (groundBottom - groundTop) * t.y
            val up = fy - TILE * 1.5f * pixel / heightPx
            if (avoidNow.none { r -> t.x in r.left..r.right && (fy in r.top..r.bottom || up in r.top..r.bottom) }) return t
            t = TownBehavior.target(random, anchor, screens, guardian)
        }
        return t
    }
    val hold = life.holds[mon.id]
    // A Pokémon stands still while it sleeps or meets another.
    val still = asleep || hold != null
    LaunchedEffect(hold?.hop) { if (hold != null && hold.hop > 0) { hopping = true; hopKey++ } }
    // Waking up: a hop and a stretch, then a bite of breakfast.
    var wasAsleep by remember(mon.id) { mutableStateOf(asleep) }
    LaunchedEffect(asleep) {
        if (wasAsleep && !asleep && !calm) { hopping = true; hopKey++; delay(1_000); grazing = true; hopKey++ }
        wasAsleep = asleep
    }
    suspend fun strollTo(to: Offset, speed: Float = pace) {
        val dx = to.x - spot.value.x
        val dy = to.y - spot.value.y
        facingLeft = dx < 0
        // The ground is deeper than it looks (a step in depth covers more screen than a step across), so weigh it up before choosing a side or a front view.
        walk = if (abs(dx) * 2f >= abs(dy)) SpriteAction.WALK_SIDE else if (dy < 0) SpriteAction.WALK_UP else SpriteAction.WALK_DOWN
        // A steady stroll, about 6 seconds for the width of the screen, a little quicker or slower for each Pokémon.
        val seconds = (0.8f + abs(dx) * 6f + abs(dy) * 2f) / speed
        spot.animateTo(to, tween((seconds * 1000).toInt(), easing = LinearEasing))
        walk = null
    }
    // Leaving the town: off the edge of Home at a walk, then gone. (With Reduce motion it is simply gone.)
    LaunchedEffect(leaving, calm) {
        if (!leaving) return@LaunchedEffect
        if (!calm) strollTo(TownBehavior.exit(spot.value, home), 0.9f)
        onGone()
    }
    // Walking stops with Reduce motion and whenever the launcher is out of sight, and starts again when it is back.
    if (!calm && !leaving) LaunchedEffect(mon.id, still, gather) {
        if (still) {
            walk = null
            // Meeting someone: step up to them, then stand turned towards them.
            hold?.approach?.let { to ->
                facingLeft = hold.faceLeft ?: facingLeft
                walk = SpriteAction.WALK_SIDE
                spot.animateTo(to, tween(600, easing = LinearEasing))
                walk = null
            }
            return@LaunchedEffect
        }
        // Walked in from the edge: on into the main screen.
        if (!entered) {
            entered = true
            strollTo(Offset(home + 0.15f + random.nextFloat() * 0.7f, random.nextFloat()), 0.85f)
        }
        // Called together (three taps on the screen): each one, after a moment of its own, strolls over to the main screen, where it can be touched.
        if (gather != gathered) {
            gathered = gather
            delay(random.nextLong(1500))
            strollTo(Offset(home + 0.06f + random.nextFloat() * 0.88f, random.nextFloat()))
        } else delay(300L + index * 150L)
        while (true) {
            when (val beat = TownBehavior.next(random, guardian, facingLeft)) {
                is Beat.Stand -> delay(beat.millis)
                is Beat.Turn -> { facingLeft = beat.left; delay(beat.millis) }
                Beat.Graze -> { grazing = true; hopKey++; delay(2_800) }
                Beat.Hop -> { hopping = true; hopKey++; delay(1_200) }
                is Beat.Roam -> repeat(beat.legs) { leg ->
                    // The town has no side walls: a Pokémon may walk off one edge and come back in at the other.
                    strollTo(pickTarget())
                    val x = spot.value.x
                    if (x > screens + 0.15f) spot.snapTo(Offset(x - (screens + 0.3f), spot.value.y)) else if (x < -0.15f) spot.snapTo(Offset(x + (screens + 0.3f), spot.value.y))
                    // A look about between the legs, now and then the other way.
                    if (leg < beat.legs - 1) { if (random.nextInt(3) == 0) facingLeft = !facingLeft; delay(500L + random.nextLong(900)) }
                }
            }
        }
    }
    // Every Pokémon is drawn at the town's pixel scale in a box of three tiles, standing on its feet; the front ones are drawn over those behind.
    val box = TILE * 3 * pixel
    // The depth only in coarse steps for the drawing order, and the position read where it is drawn: a walking Pokémon moves without recomposing every frame.
    val depthRow by remember { derivedStateOf { (spot.value.y * 24).toInt() } }
    val sizeDp = (box / LocalDensity.current.density).dp
    Box(
        Modifier.offset {
            val at = spot.value
            IntOffset(snap(widthPx * at.x, pixel) - box / 2, snap(heightPx * groundTop + heightPx * (groundBottom - groundTop) * at.y, pixel) - box)
        }.size(sizeDp).zIndex(depthRow / 24f)
            // Only on the main screen can a Pokémon be touched; wandering over the other pages, it is just scenery.
            .then(if (interactive) Modifier.pointerInput(mon.id, tapperOn) {
                detectTapGestures(onLongPress = { onMenu(mon) }, onTap = if (tapperOn) null else ({ life.pets[mon.id]?.invoke() }))
            } else Modifier)
            // Screen readers get the buddy, the Guardian and the favourites by name, with the tap and the menu as actions; the rest of the crowd is scenery.
            .then(if (exposed && interactive) Modifier.semantics(mergeDescendants = true) {
                contentDescription = "${mon.nickname}, level ${mon.level}"
                role = Role.Button
                onClick("Pet ${mon.nickname}") { life.pets[mon.id]?.invoke(); true }
                onLongClick("Open ${mon.nickname}'s menu") { onMenu(mon); true }
            } else Modifier.clearAndSetSemantics {}),
    ) {
        AnimatedSprite(
            mon.form.id, mon.isShiny, mon.nickname, Modifier.fillMaxSize(), size = sizeDp, animate = !calm && !(hold != null && walk == null), ground = 0.dp,
            action = if (hopping) SpriteAction.HOP else if (asleep) SpriteAction.SLEEP else if (grazing) SpriteAction.EAT else walk ?: if (hold != null) SpriteAction.WALK_SIDE else SpriteAction.IDLE, actionKey = hopKey, onActionDone = { hopping = false; grazing = false },
            fixedScale = pixel, mirror = if (hold != null) hold.faceLeft ?: facingLeft else facingLeft && (walk == null || walk == SpriteAction.WALK_SIDE),
        )
        // Over its head: the Zz of sleep, or what the meeting makes it feel.
        val icon = if (hold != null) hold.icon else if (asleep) StatusKind.SLEEP else null
        if (icon != null) key(icon) {
            // Just over its head: the Pokémon stands at the bottom of the box, so the icon is measured up from its feet, not down from the top.
            StatusIcon(icon, pixel, Modifier.align(Alignment.BottomCenter).offset(y = (-ICON_RISE * pixel / LocalDensity.current.density).dp), loop = icon == StatusKind.SLEEP, animate = !calm)
        }
    }
}

/** How far above its feet an icon sits, in art pixels (a Pokémon is about this tall); a bigger icon sits a little higher. */
private const val ICON_RISE = 22

/** Where the night starts for the town: the darkness at which the sleepy ones lie down. */
internal const val NIGHT = 0.5f

/** Whether this Pokémon is one of those that sleep at night: a third of them, always the same ones. */
internal fun sleepsAtNight(mon: OwnedPokemon): Boolean = (mon.id.hashCode() and 0x7fffffff) % 3 == 0

/**
 * What a Pokémon does instead of walking: it walks to [approach] if given, then stands turned to [faceLeft] (towards whoever
 * it is meeting), shows [icon] over its head and hops once for each new [hop].
 */
internal class Hold(val icon: StatusKind?, val hop: Int = 0, val faceLeft: Boolean? = null, val approach: Offset? = null)

/** What the residents are doing together: where each stands (for meetings) and who has stopped to meet someone. */
internal class TownLife {
    val spots = mutableMapOf<String, () -> Offset>()
    val holds = mutableStateMapOf<String, Hold>()
    /** What a tap on each does (its cry and a brief reaction). */
    val pets = mutableMapOf<String, () -> Unit>()
    /** Called with the id of a Pokémon that was tapped: the ones next to it may notice (set by the town). */
    var ripple: (String) -> Unit = {}
}

/**
 * The ones among [candidates] standing near [sourceId] are startled for a moment (the alert mark and a hop, turned towards it), each with [chance] in a hundred, at most [max] of them.
 * Their holds are taken off in a finally, as every hold is.
 */
internal suspend fun TownLife.startleAround(sourceId: String, candidates: List<String>, random: Random, chance: Int, max: Int) {
    val at = spots[sourceId]?.invoke() ?: return
    val near = candidates.filter { it != sourceId && it !in holds }.filter { id ->
        val p = spots[id]?.invoke() ?: return@filter false
        abs(p.x - at.x) < STARTLE_RANGE_X && abs(p.y - at.y) < STARTLE_RANGE_Y && random.nextInt(100) < chance
    }.take(max)
    if (near.isEmpty()) return
    try {
        for (id in near) { val p = spots[id]?.invoke() ?: continue; holds[id] = Hold(StatusKind.ALERT, hop = 1, faceLeft = at.x < p.x) }
        delay(1_100)
    } finally {
        near.forEach { holds.remove(it) }
    }
}

private const val STARTLE_RANGE_X = 0.16f
private const val STARTLE_RANGE_Y = 0.2f

/** The launcher's way to a tap on a Pokémon when widget pages are drawn over the town: [hit] takes a point in the town's pixels and says whether a Pokémon was there. */
internal class TownTapper { var hit: (Offset) -> Boolean = { false } }

/**
 * Two Pokémon that walk up to each other stop and react, like in a Mystery Dungeon town: a greeting (one is curious, the
 * other startled), a quarrel (one is cross, the other startled) or both startled at once, with the matching emote sounds
 * (not more than one every few seconds, and not in Reduce motion or while the launcher is out of sight). Each pair meets
 * again after a while.
 */
@Composable
private fun TownMeetings(model: AppModel, life: TownLife, residents: List<OwnedPokemon>, calm: Boolean, mood: BuddyMood?, home: Int, guardianId: String?) {
    val scope = rememberCoroutineScope()
    val buddy = model.data.player.buddy?.id
    if (!calm) LaunchedEffect(residents.map { it.id }) {
        val random = Random(residents.hashCode())
        val last = mutableMapOf<String, kotlin.time.TimeMark>()
        // They meet now and then, not all day: one meeting at a time, and a pause of half a minute or more after each.
        var nextMeeting = TimeSource.Monotonic.markNow() + (10 + random.nextInt(15)).seconds
        while (true) {
            delay(900)
            if (!nextMeeting.hasPassedNow()) continue
            // The Guardian takes no part in these meetings (the others notice it in TownGuardianAwe, and rarely): it would be stopped by every Pokémon that walked past.
            val dark = BuddyScene.darkness(model.store.now.hour, model.store.now.minute)
            val free = residents.filter { it.id !in life.holds && it.id != guardianId && !TownBehavior.asleep(it, dark) }
            for (a in free) for (b in free) {
                if (a.id >= b.id || a.id in life.holds || b.id in life.holds) continue
                val pa = life.spots[a.id]?.invoke() ?: continue
                val pb = life.spots[b.id]?.invoke() ?: continue
                if (pa.x !in (home + 0.05f)..(home + 0.95f) || pb.x !in (home + 0.05f)..(home + 0.95f) || abs(pa.x - pb.x) > 0.09f || abs(pa.y - pb.y) > 0.12f) continue
                val pair = a.id + b.id
                if (last[pair]?.let { it.elapsedNow() < 180.seconds } == true || random.nextInt(10) >= 5) continue
                last[pair] = TimeSource.Monotonic.markNow()
                nextMeeting = TimeSource.Monotonic.markNow() + (30 + random.nextInt(40)).seconds
                val withBuddy = a.id == buddy || b.id == buddy
                val other = if (a.id == buddy) b else a
                // The one on the left speaks first.
                val (first, second) = if (pa.x <= pb.x) a to b else b to a
                // They step up to each other, side by side at the midpoint, and face one another.
                val mid = Offset((pa.x + pb.x) / 2f, (pa.y + pb.y) / 2f)
                val towardsFirst = Offset(mid.x - 0.045f, mid.y)
                val towardsSecond = Offset(mid.x + 0.045f, mid.y)
                scope.launch {
                    when (random.nextInt(10)) {
                        in 0..5 -> {
                            life.holds[first.id] = Hold(StatusKind.QUESTION, faceLeft = false, approach = towardsFirst); life.holds[second.id] = Hold(null, faceLeft = true, approach = towardsSecond)
                            delay(700)
                            if (withBuddy) mood?.meet(other, Emotion.NORMAL, Emotion.NORMAL)
                            delay(800)
                            life.holds[second.id] = Hold(StatusKind.ALERT, hop = 1, faceLeft = true)
                            if (withBuddy) mood?.meet(other, Emotion.SURPRISED, Emotion.SURPRISED)
                            delay(900)
                            // Then they are pleased to meet.
                            life.holds[first.id] = Hold(StatusKind.TWINKLE, faceLeft = false); life.holds[second.id] = Hold(StatusKind.TWINKLE, faceLeft = true)
                            if (withBuddy) mood?.meet(other, Emotion.HAPPY, Emotion.JOYOUS)
                        }
                        in 6..7 -> {
                            life.holds[first.id] = Hold(StatusKind.CROSS, faceLeft = false, approach = towardsFirst); life.holds[second.id] = Hold(null, faceLeft = true, approach = towardsSecond)
                            delay(700)
                            // The one that is cross is the other Pokémon if the buddy is not the one that started it.
                            if (withBuddy) mood?.meet(other, if (first.id == buddy) Emotion.NORMAL else Emotion.DETERMINED, if (first.id == buddy) Emotion.DETERMINED else Emotion.SURPRISED)
                            delay(700)
                            life.holds[second.id] = Hold(StatusKind.SWEAT, hop = 1, faceLeft = true)
                        }
                        else -> {
                            life.holds[first.id] = Hold(StatusKind.ZAP, hop = 1, faceLeft = false, approach = towardsFirst); life.holds[second.id] = Hold(StatusKind.ZAP, hop = 1, faceLeft = true, approach = towardsSecond)
                            if (withBuddy) mood?.meet(other, Emotion.SURPRISED, Emotion.SURPRISED)
                        }
                    }
                    delay(1800)
                    life.holds.remove(first.id)
                    life.holds.remove(second.id)
                }
            }
        }
    }
}

/** [value] rounded to a multiple of [pixel], so a position always falls on the pixel grid of the art. */
internal fun snap(value: Float, pixel: Int): Int = (value / pixel).roundToInt() * pixel

private val atlases = BitmapCache(CacheBudget.TILES)

/** The town's backdrop: a real Pokémon scene (whole-number scale, no smoothing), or a town laid out from a dungeon's tiles. */
@Composable
internal fun TownBackdrop(scene: String, modifier: Modifier = Modifier, darkness: Float = 0f, weather: Weather = Weather.CLEAR) {
    if (TownScenes.isDungeon(scene)) TileTown(scene, modifier, darkness, weather) else SceneImage(scene, modifier.graphicsLayer {}, darkness, crisp = true)
}

/**
 * A town of a dungeon's tiles (assets/tiles): [TownLayout]'s map drawn at the [townScale], without smoothing, centred on a
 * multiple of the scale, with the day's darkness over it like [SceneImage].
 */
@Composable
private fun TileTown(name: String, modifier: Modifier, darkness: Float, weather: Weather) {
    val atlas by produceState(atlases[name], name) {
        // The key may have changed under the same composable: start from what is cached for the new one, never the old one's image.
        value = atlases[name]
        if (value == null) {
            value = orNull { Res.readBytes("files/tiles/$name.png").decodeToImageBitmap() }
            atlases[name] = value
        }
    }
    // The dungeon's weather grades the colours of its tiles (WeatherGrade.kt); the picture shows as it was until the graded one is made.
    val shown by produceState(atlas, atlas, weather) { value = atlas?.let { gradedFor(name, it, weather) } }
    val fade by rememberFadeIn(atlas != null)
    // The map is worked out once per size (not on every draw) and the backdrop has a layer of its own, so a walking sprite's frame never draws every tile again.
    Box(
        modifier.graphicsLayer {}.drawWithCache {
            val pixel = townScale(size.width)
            val tile = TILE * pixel
            val cols = (size.width / tile).toInt() + 2
            val rows = (size.height / tile).toInt() + 1
            val seed = name.hashCode()
            val map = TownLayout.build(cols, rows, seed)
            val shift = ((cols * tile - size.width) / 2).toInt() / pixel * pixel
            onDrawBehind {
                val image = shown ?: atlas ?: return@onDrawBehind drawRect(PmdPalette.Loading)
                for (y in 0 until rows) for (x in 0 until cols) {
                    val (ax, ay) = map.atlas(x, y, seed)
                    drawImage(
                        image, srcOffset = IntOffset(ax * TILE, ay * TILE), srcSize = IntSize(TILE, TILE),
                        dstOffset = IntOffset(x * tile - shift, y * tile), dstSize = IntSize(tile, tile), alpha = fade, filterQuality = FilterQuality.None,
                    )
                }
                if (darkness > 0f) drawRect(Pixel.Night.copy(alpha = darkness.coerceIn(0f, 1f)), Offset.Zero, Size(size.width, size.height))
            }
        },
    )
}
