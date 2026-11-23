package app.pokeplan.ui.launcher

import app.pokeplan.ui.rememberCachedImage
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.keyframes
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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import app.pokeplan.ui.Animation
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import pokeplan.composeapp.generated.resources.Res
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private const val SCENE_TOP = 0.14f
private const val SCENE_BOTTOM = 0.74f

internal const val TOWN_CAP = 24

private const val TILE = 24

private const val TILES_ACROSS = 14f

internal fun townScale(widthPx: Float): Int = (widthPx / (TILE * TILES_ACROSS)).roundToInt().coerceAtLeast(1)

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

@Composable
internal fun Town(
    model: AppModel, scene: String, active: Boolean, onMenu: (OwnedPokemon) -> Unit, modifier: Modifier = Modifier, size: Int = TOWN_CAP, weather: Weather = Weather.CLEAR,
    screens: Int = 1, interactive: Boolean = true, gather: Int = 0, mood: BuddyMood? = null,
    chirps: Boolean = false,
    home: Int = 0,
    avoid: List<Rect> = emptyList(),
    tapper: TownTapper? = null,
    rotation: Int = 0,
    reactions: Boolean = true,
    activity: Int = TownBehavior.ACTIVITY_DEFAULT,
    layoutRoll: Int = 0,
) {
    val data = model.data
    val sheets by produceState(loadedAnimations()) { value = animations() }
    val guardianIds = data.player.moduleGuardians.values
    val residents = remember(data.player.pokemon, data.player.buddy?.id, size, data.favorites, sheets, guardianIds, rotation) {
        townResidents(data.player.pokemon, data.player.buddy?.id, size, data.favorites, hasSprite = { m -> sheets?.containsKey(m.form.id) ?: true }, guardianIds = guardianIds, rotation = rotation)
    }
    val guardian = shownGuardian(residents, guardianIds, data.player.buddy?.id, rotation)
    var seen by remember { mutableStateOf(guardian) }
    var departing by remember { mutableStateOf<OwnedPokemon?>(null) }
    var arriving by remember { mutableStateOf<String?>(null) }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current.density
        val widthPx = maxWidth.value * density
        val heightPx = maxHeight.value * density
        val screenPx = widthPx / screens
        val pixel = townScale(screenPx)
        val dungeon = TownScenes.isDungeon(scene)
        val day = model.store.today.toEpochDays().toLong()
        val avoidLatest by rememberUpdatedState(avoid)
        val geo = if (!dungeon) null else {
            val (perScreen, rows) = FloorGeometry.size(screenPx, heightPx, pixel)
            val tilePx = (TILE * pixel).toFloat()
            val floor = remember(scene, day, layoutRoll, screens, home, perScreen, rows, avoid.isEmpty()) {
                DungeonFloor.build(screens, perScreen, rows, TownTiles.seed(scene, day, layoutRoll), home, TownTiles.organic(scene)) { c ->
                    val l = c.x * tilePx / screenPx
                    val r = (c.x + 1) * tilePx / screenPx
                    val t = c.y * tilePx / heightPx
                    val b = (c.y + 1) * tilePx / heightPx
                    avoidLatest.any { w -> l < w.right && w.left < r && t < w.bottom && w.top < b }
                }
            }
            remember(floor, screenPx, heightPx, pixel) { FloorGeometry.of(floor, pixel, screenPx, heightPx) }
        }
        val life = remember { TownLife() }
        life.sheets = sheets
        val pickups = remember { FloorPickups() }
        val finds = if (geo == null) emptyList() else remember(data.counters, day) { model.store.townFinds() }
        fun collect(i: Int, heard: Boolean = true): Boolean {
            val g = geo ?: return false
            val find = finds.getOrNull(i) ?: return false
            if (model.store.collectTownFind(i) == null) return false
            if (heard) model.effect(Sfx.ITEM_GET)
            pickups.add(g.floor.finds[i], find)
            return true
        }
        SideEffect {
            life.finds = if (geo == null) emptyMap() else finds.withIndex().filter { it.value != null }.mapNotNull { (i, _) -> geo.floor.finds.getOrNull(i)?.let { it to i } }.toMap()
            life.pickUp = { i, heard -> collect(i, heard) }
        }
        if (geo != null) {
            LaunchedEffect(geo.floor) { life.revealed.clear() }
            FloorBackdrop(scene, geo.floor, pixel, weather, Modifier.fillMaxSize())
            FloorThings(
                geo, pixel, life.revealed.keys, finds, onScreen = { c -> ((c.x + 0.5f) * geo.tilePx / screenPx).let { it >= home && it < home + 1 } },
                onCollect = ::collect, pickups = pickups, modifier = Modifier.fillMaxSize(),
            )
        } else androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
            repeat(screens) { TownBackdrop(scene, Modifier.weight(1f).fillMaxHeight(), weather = weather) }
        }
        val dark = BuddyScene.darkness(model.store.now.hour, model.store.now.minute)
        if (dark > 0f) Box(Modifier.matchParentSize().background(Pixel.Night.copy(alpha = dark.coerceIn(0f, 1f))))
        val top = if (geo != null) 0f else SCENE_TOP
        val bottom = if (geo != null) 1f else SCENE_BOTTOM
        val calm = data.settings.reduceMotion || !active
        val darkNow = dark
        val scope = rememberCoroutineScope()
        LaunchedEffect(guardian?.id) {
            val before = seen
            seen = guardian
            if (before == null || guardian == null || before.id == guardian.id) return@LaunchedEffect
            if (!calm && data.player.pokemon.any { it.id == before.id }) departing = before
            if (!calm) arriving = guardian.id
        }
        LaunchedEffect(arriving) {
            val id = arriving ?: return@LaunchedEffect
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
        TownMeetings(model, life, residents, calm || !interactive, mood, home, guardian?.id, geo)
        TownGuardianAwe(life, residents, guardian, data.player.buddy?.id, calm || !interactive || arriving != null || departing != null, mood, home)
        TownChirps(model, life, residents, guardian?.id, data.player.buddy?.id, enabled = chirps && !calm && interactive)
        TownWeatherReactions(model, life, residents, weather, enabled = reactions && !calm && interactive)
        val boxPx = TILE * 3 * pixel
        if (tapper != null) SideEffect {
            tapper.hit = hit@{ at ->
                val ids = residents.mapNotNull { m -> life.spots[m.id]?.let { m.id to it() } }.sortedByDescending { it.second.y }
                for ((id, spot) in ids) {
                    val feetX = screenPx * spot.x
                    val feetY = heightPx * (top + (bottom - top) * spot.y)
                    if (at.x in (feetX - boxPx / 2)..(feetX + boxPx / 2) && at.y in (feetY - boxPx)..feetY) { life.pets[id]?.invoke(); return@hit true }
                }
                if (geo != null) finds.forEachIndexed { i, f ->
                    val c = geo.floor.finds.getOrNull(i) ?: return@forEachIndexed
                    if (f == null) return@forEachIndexed
                    val l = c.x * geo.tilePx
                    val t = c.y * geo.tilePx
                    if (at.x in (l - geo.tilePx * 0.3f)..(l + geo.tilePx * 1.3f) && at.y in (t - geo.tilePx * 0.3f)..(t + geo.tilePx * 1.3f)) { collect(i); return@hit true }
                }
                false
            }
        }
        val buddyId = data.player.buddy?.id
        val disguises = remember(residents) { residents.filter { TownQuirks.of(it) == Quirk.DISGUISED }.associate { it.id to TownQuirks.disguiseFor(it, residents) } }
        residents.forEachIndexed { i, mon ->
            key(mon.id) {
                Resident(
                    model, mon, screenPx, heightPx, pixel, top, bottom, calm = calm, index = i, life = life, asleep = TownBehavior.asleep(mon, darkNow), screens = screens, interactive = interactive, gather = gather,
                    onMenu = onMenu, home = home, avoid = avoid, tapperOn = tapper != null, buddy = mon.id == buddyId, guardian = mon.id == guardian?.id, entering = mon.id == arriving,
                    exposed = mon.id == buddyId || mon.id == guardian?.id || mon.id in data.favorites, lively = TownBehavior.liveliness(activity), geo = geo, disguise = disguises[mon.id],
                )
            }
        }
        departing?.let { gone -> key("leaving-" + gone.id) {
            Resident(
                model, gone, screenPx, heightPx, pixel, top, bottom, calm = calm, index = 0, life = life, asleep = false, screens = screens, interactive = false, gather = gather,
                onMenu = onMenu, home = home, avoid = avoid, tapperOn = tapper != null, buddy = false, guardian = true, leaving = true, onGone = { departing = null }, exposed = false, geo = geo,
            )
        } }
        WeatherLayer(weather, pixel, dungeon, calm, Modifier.fillMaxSize().zIndex(10f))
    }
}

@Composable
private fun Resident(
    model: AppModel, mon: OwnedPokemon, widthPx: Float, heightPx: Float, pixel: Int, groundTop: Float, groundBottom: Float, calm: Boolean, index: Int, life: TownLife, asleep: Boolean, screens: Int,
    interactive: Boolean, gather: Int, onMenu: (OwnedPokemon) -> Unit, home: Int, avoid: List<Rect>, tapperOn: Boolean, buddy: Boolean, guardian: Boolean, exposed: Boolean,
    entering: Boolean = false,
    leaving: Boolean = false, onGone: () -> Unit = {},
    lively: Float = TownBehavior.liveliness(TownBehavior.ACTIVITY_DEFAULT),
    geo: FloorGeometry? = null,
    disguise: OwnedPokemon? = null,
) {
    life.movers[mon.id] = moverOf(mon)
    var unmasked by remember(mon.id) { mutableStateOf(false) }
    val shown = if (disguise != null && !unmasked) disguise else mon
    life.sizes[mon.id] = life.sheets?.get(shown.form.id)?.let { it.width to it.height } ?: TownLife.MEDIAN
    val startRandom = remember(mon.id) { Random(mon.id.hashCode()) }
    val random = remember(mon.id) { Random(Random.nextLong()) }
    val mover = remember(mon.form.id) { moverOf(mon) }
    val anchor = remember(mon.id, screens, home, buddy) { TownBehavior.anchor(mon.id, screens, home, buddy) }
    val quirk = remember(mon.id, mon.speciesId) { TownQuirks.of(mon) }
    val pace = remember(mon.id, quirk) { TownBehavior.pace(mon.id) * TownQuirks.pace(quirk) }
    val temper = remember(mon.id, quirk) { TownBehavior.temper(mon.id) * TownQuirks.temper(quirk) }
    val livelyNow by rememberUpdatedState(lively)
    var restless by remember(mon.id) { mutableStateOf(random.nextInt(4) == 0) }
    var heading by remember(mon.id) { mutableStateOf(random.nextFloat() * 2f * kotlin.math.PI.toFloat()) }
    val spot = remember(mon.id, geo?.floor) {
        val start = when {
            geo != null && entering -> geo.spotOf(geo.floor.stairs)
            geo != null -> geo.restingCell(startRandom, mover, crowded = life.crowded(geo, mon.id, life.claimedOn(geo.floor)), roomsOnly = TownQuirks.stays(quirk)).let { c -> life.claims[mon.id] = c; geo.spotOf(c) }
            entering -> TownBehavior.entrance(startRandom, home)
            else -> Offset(startRandom.nextFloat() * screens, startRandom.nextFloat())
        }
        Animatable(start, Offset.VectorConverter)
    }
    var napping by remember(mon.id) { mutableStateOf(false) }
    var ailment by remember(mon.id) { mutableStateOf<Condition?>(null) }
    var ailmentStamp by remember(mon.id) { mutableIntStateOf(0) }
    var spinning by remember(mon.id) { mutableStateOf<SpriteAction?>(null) }
    var slowUntil by remember(mon.id) { mutableStateOf<TimeMark?>(null) }
    var trapFx by remember(mon.id) { mutableStateOf<TrapFx?>(null) }
    var trapFxPlay by remember(mon.id) { mutableIntStateOf(0) }
    val fade = remember(mon.id) { Animatable(1f) }
    var facingLeft by remember(mon.id) { mutableStateOf(false) }
    var hopKey by remember(mon.id) { mutableIntStateOf(0) }
    var hopping by remember(mon.id) { mutableStateOf(false) }
    var grazing by remember(mon.id) { mutableStateOf(false) }
    var walk by remember(mon.id) { mutableStateOf<SpriteAction?>(null) }
    var facing by remember(mon.id) { mutableStateOf(SpriteAction.IDLE) }
    var gathered by remember(mon.id) { mutableIntStateOf(gather) }
    var entered by remember(mon.id) { mutableStateOf(!entering) }
    val scope = rememberCoroutineScope()
    SideEffect {
        life.spots[mon.id] = { spot.value }
        if (asleep || napping) life.sleeping += mon.id else life.sleeping -= mon.id
    }
    val avoidNow by rememberUpdatedState(avoid)
    val taps = remember(mon.id) { mutableListOf<TimeMark>() }
    var ownIcon by remember(mon.id) { mutableStateOf<StatusKind?>(null) }
    var ownStamp by remember(mon.id) { mutableIntStateOf(0) }
    fun feel(icon: StatusKind, millis: Long = 1_100, stay: Boolean = true) {
        if (!stay) {
            val mine = ++ownStamp
            ownIcon = icon
            scope.launch { delay(millis); if (ownStamp == mine) ownIcon = null }
            return
        }
        if (mon.id in life.holds) return
        scope.launch {
            life.holds[mon.id] = Hold(icon)
            try { delay(millis) } finally { life.holds.remove(mon.id) }
        }
    }
    fun afflict(c: Condition) { ailment = c; ailmentStamp++ }
    LaunchedEffect(ailmentStamp) {
        val a = ailment ?: return@LaunchedEffect
        delay(a.seconds * 1_000L)
        ailment = null
        feel(StatusKind.TWINKLE, 1_000)
    }
    SideEffect {
        life.pets[mon.id] = {
            taps.removeAll { it.elapsedNow() > 2.seconds }
            taps += TimeSource.Monotonic.markNow()
            if ((asleep || napping) && disguise == null) { /* a sleeping Pokémon stays asleep: only the ripple answers a tap */ }
            else if (disguise != null && !unmasked) {
                unmasked = true; model.cry(mon); hopping = true; hopKey++; feel(StatusKind.ALERT, 900, stay = false)
            } else when (TownBehavior.tapReaction(random, taps.size)) {
                TapReaction.HOP -> { model.cry(mon); hopping = true; hopKey++ }
                TapReaction.HAPPY -> { model.cry(mon); hopping = true; hopKey++; feel(StatusKind.TWINKLE) }
                TapReaction.TURN -> { if (walk == null) facingLeft = !facingLeft; model.cry(mon); hopping = true; hopKey++ }
                TapReaction.FLUSTERED -> { feel(StatusKind.SWEAT, 1_400); model.effect(Sfx.CONFUSION_STATUS) }
            }
            life.ripple(mon.id)
        }
    }
    val room = TILE * 3f * pixel / widthPx * 0.6f
    fun neighbours(): List<Offset> = life.spots.filterKeys { it != mon.id }.values.map { it() } + life.bound.filterKeys { it != mon.id }.values
    fun pickLeg(short: Boolean): TownBehavior.Leg {
        val others = neighbours()
        var leg = TownBehavior.leg(random, spot.value, heading, anchor, screens, guardian, short, livelyNow)
        repeat(10) {
            val t = leg.to
            val fy = groundTop + (groundBottom - groundTop) * t.y
            val up = fy - TILE * 1.5f * pixel / heightPx
            if (avoidNow.none { r -> t.x in r.left..r.right && (fy in r.top..r.bottom || up in r.top..r.bottom) } && !TownBehavior.crowded(t, others, room)) return leg
            leg = TownBehavior.leg(random, spot.value, heading, anchor, screens, guardian, short, livelyNow)
        }
        return leg
    }
    val hold = life.holds[mon.id]
    val still = asleep || hold != null
    var scare by remember(mon.id) { mutableIntStateOf(0) }
    var scareFrom by remember(mon.id) { mutableStateOf<Offset?>(null) }
    if (quirk == Quirk.TIMID && !calm && !leaving && interactive) LaunchedEffect(mon.id, geo, still) {
        if (still) return@LaunchedEffect
        var last = TimeSource.Monotonic.markNow() - 20.seconds
        while (true) {
            delay(600)
            if (scareFrom != null || last.elapsedNow() < 6.seconds) continue
            val me = spot.value
            val awake = life.spots.filterKeys { it != mon.id && it !in life.sleeping }.values.map { it() }
            val near = if (geo != null) {
                val a = geo.cellOf(me)
                awake.firstOrNull { o -> geo.cellOf(o).let { c -> maxOf(abs(c.x - a.x), abs(c.y - a.y)) <= 2 } }
            } else awake.firstOrNull { TownBehavior.crowded(me, listOf(it), 0.14f) }
            if (near != null) { last = TimeSource.Monotonic.markNow(); scareFrom = near; scare++ }
        }
    }
    LaunchedEffect(unmasked) { if (unmasked) { delay(60_000); unmasked = false } }
    if (guardian && !calm && !leaving && interactive) LaunchedEffect(mon.id) {
        val icon = TownQuirks.aura(mon.form.types)
        delay(8_000L + random.nextLong(15_000))
        while (true) {
            if (mon.id !in life.holds && mon.id !in life.sleeping) { feel(icon, 2_200, stay = false); hopping = true; hopKey++ }
            delay(25_000L + random.nextLong(35_000))
        }
    }
    LaunchedEffect(hold?.hop) { if (hold != null && hold.hop > 0) { hopping = true; hopKey++ } }
    var wasAsleep by remember(mon.id) { mutableStateOf(asleep) }
    LaunchedEffect(asleep) {
        if (asleep) { hopping = false; grazing = false; walk = null }
        if (wasAsleep && !asleep && !calm) { hopping = true; hopKey++; delay(1_000); grazing = true; hopKey++ }
        wasAsleep = asleep
    }
    suspend fun strollTo(target: Offset, speed: Float = pace) {
        val to = TownBehavior.settle(random, target, neighbours(), room, screens)
        val dx = to.x - spot.value.x
        val dy = to.y - spot.value.y
        val seconds = (0.9f + abs(dx) * 7f + abs(dy) * 2.4f) / (speed * (0.85f + 0.3f * livelyNow))
        val millis = (seconds * 1000).toInt()
        val sleepers = life.sleeping.filter { it != mon.id }.mapNotNull { life.spots[it]?.invoke() }
        val raw = TownBehavior.path(random, spot.value, to)
        val way = raw.mapIndexed { i, p -> if (i == raw.lastIndex || sleepers.isEmpty()) p else TownBehavior.clear(p, sleepers, room) }
        life.bound[mon.id] = to
        try {
            for (p in way) {
                val sx = p.x - spot.value.x
                val sy = p.y - spot.value.y
                if (abs(sx) > 0.002f) facingLeft = sx < 0
                walk = if (abs(sx) * 2f >= abs(sy)) SpriteAction.WALK_SIDE else if (sy < 0) SpriteAction.WALK_UP else SpriteAction.WALK_DOWN
                facing = when (walk) { SpriteAction.WALK_SIDE -> SpriteAction.IDLE_SIDE; SpriteAction.WALK_UP -> SpriteAction.IDLE_UP; else -> SpriteAction.IDLE }
                spot.animateTo(p, tween((millis / way.size).coerceAtLeast(60), easing = LinearEasing))
            }
        } finally {
            life.bound.remove(mon.id)
        }
        walk = null
    }
    suspend fun stepTo(g: FloorGeometry, to: Offset, speed: Float = pace) {
        val dx = (to.x - spot.value.x) * widthPx
        val dy = (to.y - spot.value.y) * heightPx
        if (abs(dx) > 1f) facingLeft = dx < 0
        walk = if (abs(dx) >= abs(dy) * 0.6f) SpriteAction.WALK_SIDE else if (dy < 0) SpriteAction.WALK_UP else SpriteAction.WALK_DOWN
        facing = when (walk) { SpriteAction.WALK_SIDE -> SpriteAction.IDLE_SIDE; SpriteAction.WALK_UP -> SpriteAction.IDLE_UP; else -> SpriteAction.IDLE }
        val squares = kotlin.math.hypot(dx, dy) / g.tilePx
        val slow = (if (slowUntil?.hasPassedNow() == false) 0.45f else 1f) * (ailment?.pace ?: 1f)
        val millis = (squares * 520f / (speed * (0.85f + 0.3f * livelyNow) * slow)).toInt().coerceAtLeast(120)
        spot.animateTo(to, tween(millis, easing = LinearEasing))
    }

    suspend fun spring(g: FloorGeometry, at: Cell, trap: FloorTrap, walked: List<Cell>) {
        if (trap != FloorTrap.WONDER) life.revealed[at] = true
        TrapFx.of(trap)?.let { trapFx = it; trapFxPlay++ }
        if (interactive) model.effect(when (trap) {
            FloorTrap.WARP -> Sfx.QUICK_ESCAPE
            FloorTrap.SPIN -> Sfx.CONFUSION_STATUS
            FloorTrap.POISON -> Sfx.SAD
            FloorTrap.SLUMBER, FloorTrap.SLOW -> Sfx.SAD
            FloorTrap.GUST -> Sfx.STORY_WIND
            FloorTrap.TRIP -> Sfx.SURPRISE
            FloorTrap.WONDER -> Sfx.HEAL
        })
        when (trap) {
            FloorTrap.WARP -> {
                feel(StatusKind.ALERT, 500, stay = false)
                delay(400)
                fade.animateTo(0f, tween(260))
                spot.snapTo(g.spotOf(g.restingCell(random, mover, crowded = life.crowded(g, mon.id))))
                fade.animateTo(1f, tween(260))
                feel(StatusKind.QUESTION, 1_400)
            }
            FloorTrap.SPIN -> {
                feel(StatusKind.SWEAT, 1_600)
                repeat(3) {
                    for ((pose, left) in listOf(SpriteAction.IDLE to false, SpriteAction.IDLE_SIDE to false, SpriteAction.IDLE_UP to false, SpriteAction.IDLE_SIDE to true)) {
                        spinning = pose; facingLeft = left; delay(100)
                    }
                }
                spinning = null
                afflict(Condition.CONFUSED)
            }
            FloorTrap.POISON -> { afflict(Condition.POISONED); hopping = true; hopKey++; feel(StatusKind.SWEAT, 1_000) }
            FloorTrap.SLUMBER -> { napping = true; delay(3_500); napping = false; hopping = true; hopKey++ }
            FloorTrap.GUST -> {
                feel(StatusKind.ALERT, 900, stay = false)
                hopping = true; hopKey++
                val (dx, dy) = GUST_DIRECTIONS.random(random)
                val landing = g.floor.blownTo(at, dx, dy, mover, life.standing(g, mon.id))
                if (landing != at) {
                    facingLeft = dx < 0
                    val squares = maxOf(abs(landing.x - at.x), abs(landing.y - at.y))
                    spot.animateTo(g.spotOf(landing), tween(squares * 70 + 250, easing = LinearEasing))
                }
            }
            FloorTrap.SLOW -> { slowUntil = TimeSource.Monotonic.markNow() + 20.seconds; feel(StatusKind.SWEAT, 1_400) }
            FloorTrap.TRIP -> { hopping = true; hopKey++; feel(StatusKind.SWEAT, 1_200) }
            FloorTrap.WONDER -> feel(StatusKind.TWINKLE, 1_200)
        }
    }

    fun swapWith(g: FloorGeometry, other: String): Boolean {
        if (other in life.sleeping || other in life.holds) return false
        val here = g.cellOf(spot.value)
        if (!g.floor.canStand(here, life.movers[other] ?: Mover.WALKER)) return false
        val theirs = life.spots[other]?.invoke()?.let { g.cellOf(it) } ?: return false
        val hold = Hold(null, faceLeft = theirs.x > here.x, approach = g.spotOf(here))
        life.holds[other] = hold
        scope.launch { delay(900); if (life.holds[other] === hold) life.holds.remove(other) }
        return true
    }

    suspend fun roamFloor(g: FloorGeometry, target: Offset, speed: Float = pace, tries: Int = 0, phase: Boolean = false) {
        val others = life.occupiedCells(g, mon.id)
        val going = if (mover.walls && !phase) mover.copy(walls = false) else mover
        val goal = g.nearestResting(g.cellOf(Offset(target.x.coerceIn(0f, screens - 0.01f), target.y.coerceIn(0f, 0.999f))), going, others, life.crowded(g, mon.id)) ?: return
        life.goals[mon.id] = goal
        try {
            val known = life.revealed.keys + g.floor.traps.filterValues { it == FloorTrap.WONDER }.keys
            val standing = life.standing(g, mon.id)
            val from = g.cellOf(spot.value)
            val way = withContext(Dispatchers.Default) { g.floor.path(from, goal, going, known, standing) ?: g.floor.path(from, goal, going, known) ?: if (mover.walls) g.floor.path(from, goal, mover, known) else null } ?: return
            val hit = way.drop(1).indexOfFirst { it in g.floor.traps }
            val walked = if (hit >= 0) way.take(hit + 2) else way
            for (c in walked.drop(1)) {
                if (random.nextInt(100) < TownQuirks.stumble(quirk, ailment == Condition.CONFUSED)) {
                    val here = g.cellOf(spot.value)
                    val taken = life.standing(g, mon.id)
                    val aside = (-1..1).flatMap { dy -> (-1..1).map { dx -> Cell(here.x + dx, here.y + dy) } }.filter { it != here && it != c && g.floor.canRoam(it, going) && it !in g.floor.traps && it !in taken }
                    aside.randomOrNull(random)?.let { to ->
                        stepTo(g, g.spotOf(to), speed)
                        facingLeft = random.nextBoolean(); walk = null
                        delay(250)
                        return roamFloor(g, g.spotOf(goal), speed, phase = phase)
                    }
                }
                val there = life.occupantOf(g, c, mon.id)
                if (there != null && !swapWith(g, there)) {
                    walk = null
                    delay(500)
                    if (life.occupantOf(g, c, mon.id) != null) { if (tries < 2) return roamFloor(g, g.spotOf(goal), speed, tries + 1) else return }
                }
                val shade = if (g.floor.map.at(c.x, c.y) == TownCell.WALL) 0.5f else 1f
                if (fade.targetValue != shade) scope.launch { fade.animateTo(shade, tween(160)) }
                stepTo(g, g.spotOf(c), speed * if (quirk == Quirk.ERRATIC) 0.6f + random.nextFloat() else 1f)
                life.finds[c]?.let { i ->
                    if (life.pickUp(i, interactive && g.spotOf(c).x.let { it >= home && it < home + 1 })) { walk = null; hopping = true; hopKey++; feel(StatusKind.TWINKLE, 900, stay = false); delay(500) }
                }
            }
            walk = null
            if (hit >= 0) spring(g, walked.last(), g.floor.traps.getValue(walked.last()), walked)
        } finally {
            life.goals.remove(mon.id)
            if (fade.value != 1f) withContext(NonCancellable) { fade.snapTo(1f) }
        }
    }

    LaunchedEffect(leaving, calm, geo) {
        if (!leaving) return@LaunchedEffect
        if (!calm) {
            if (geo != null) {
                geo.floor.path(geo.cellOf(spot.value), geo.floor.stairs, Mover.ANYWHERE)?.let { way -> for (c in geo.floor.turns(way).drop(1)) stepTo(geo, geo.spotOf(c), 0.9f) }
                walk = null
                fade.animateTo(0f, tween(400))
            } else strollTo(TownBehavior.exit(spot.value, home), 0.9f)
        }
        onGone()
    }
    if (!calm && !leaving) LaunchedEffect(mon.id, still, gather, geo, scare) {
        if (geo != null && geo.floor.map.at(geo.cellOf(spot.value).x, geo.cellOf(spot.value).y) == TownCell.WALL) {
            if (still) { geo.nearestResting(geo.cellOf(spot.value), mover, emptySet())?.let { spot.snapTo(geo.spotOf(it)) }; fade.snapTo(1f) }
            else roamFloor(geo, spot.value, phase = true)
        }
        if (still) {
            walk = null
            scareFrom = null
            hold?.approach?.takeIf { to -> geo == null || geo.floor.canStand(geo.cellOf(to), mover) }?.let { to ->
                facingLeft = hold.faceLeft ?: facingLeft
                walk = SpriteAction.WALK_SIDE
                spot.animateTo(to, tween(600, easing = LinearEasing))
                walk = null
            }
            return@LaunchedEffect
        }
        scareFrom?.let { from ->
            scareFrom = null
            feel(StatusKind.ALERT, 700, stay = false)
            if (geo != null) {
                val here = geo.cellOf(spot.value)
                val threat = geo.cellOf(from)
                val away = geo.floor.restingCells(mover).filter { c -> maxOf(abs(c.x - threat.x), abs(c.y - threat.y)) >= 7 && maxOf(abs(c.x - here.x), abs(c.y - here.y)) <= 16 }
                away.randomOrNull(random)?.let { roamFloor(geo, geo.spotOf(it), pace * 1.7f) }
            } else strollTo(Offset((spot.value.x + (if (from.x < spot.value.x) 0.3f else -0.3f)).coerceIn(0f, screens.toFloat()), spot.value.y), pace * 1.7f)
            heading = if (from.x < spot.value.x) 0f else kotlin.math.PI.toFloat()
        }
        if (!entered) {
            entered = true
            if (geo != null) roamFloor(geo, geo.spotOf(geo.restingCell(random, mover, home)), 0.85f)
            else strollTo(Offset(home + 0.15f + random.nextFloat() * 0.7f, random.nextFloat()), 0.85f)
        }
        if (gather != gathered && quirk == Quirk.ROOTED) gathered = gather
        else if (gather != gathered) {
            gathered = gather
            delay(random.nextLong(1500))
            if (geo != null) roamFloor(geo, geo.spotOf(geo.restingCell(random, mover, home)))
            else strollTo(Offset(home + 0.06f + random.nextFloat() * 0.88f, random.nextFloat()))
        } else delay(300L + index * 150L + random.nextLong(3_000))
        while (true) {
            restless = TownBehavior.spell(random, restless, livelyNow, temper, guardian)
            val rolled = TownBehavior.next(random, guardian, facingLeft, restless, livelyNow)
            when (val beat = if (quirk == Quirk.ROOTED && rolled is Beat.Roam) Beat.Turn(!facingLeft, 2_000 + random.nextLong(3_000)) else rolled) {
                is Beat.Stand -> delay(beat.millis)
                is Beat.Turn -> { facingLeft = beat.left; delay(beat.millis) }
                Beat.Graze -> { grazing = true; hopKey++; delay(2_800) }
                Beat.Hop -> { hopping = true; hopKey++; delay(1_200) }
                is Beat.Roam -> if (geo != null) repeat(beat.legs) { leg ->
                    val next = TownBehavior.leg(random, spot.value, heading, anchor, screens, guardian, beat.short, livelyNow)
                    val find = life.finds.keys.minByOrNull { c -> abs(c.x - geo.cellOf(spot.value).x) + abs(c.y - geo.cellOf(spot.value).y) }
                    val shortcut = if (mover.walls && !beat.short && random.nextInt(100) < PHASE_PERCENT) {
                        val here = geo.cellOf(spot.value)
                        geo.floor.restingCells(mover).filter { c -> maxOf(abs(c.x - here.x), abs(c.y - here.y)) in 3..9 }.randomOrNull(random)
                    } else null
                    if (shortcut != null) { feel(StatusKind.TWINKLE, 900, stay = false); roamFloor(geo, geo.spotOf(shortcut), phase = true) }
                    else if (find != null && !guardian && !beat.short && random.nextInt(100) < FIND_SEEK_PERCENT) roamFloor(geo, geo.spotOf(find)) else roamFloor(geo, next.to)
                    heading = next.heading
                    if (leg < beat.legs - 1) { if (random.nextInt(4) == 0) facingLeft = !facingLeft; delay(400L + random.nextLong(1_000)) }
                } else repeat(beat.legs) { leg ->
                    val next = pickLeg(beat.short)
                    strollTo(next.to)
                    heading = next.heading
                    val x = spot.value.x
                    if (x > screens + 0.15f) spot.snapTo(Offset(x - (screens + 0.3f), spot.value.y)) else if (x < -0.15f) spot.snapTo(Offset(x + (screens + 0.3f), spot.value.y))
                    if (leg < beat.legs - 1) { if (random.nextInt(4) == 0) facingLeft = !facingLeft; delay(400L + random.nextLong(1_000)) }
                }
            }
        }
    }
    trapFx?.let { fx ->
        key(trapFxPlay) {
            TrapEffect(fx, { spot.value.let { at -> Offset(widthPx * at.x, heightPx * groundTop + heightPx * (groundBottom - groundTop) * at.y) } }, pixel) { trapFx = null }
        }
    }
    val box = TILE * 3 * pixel
    val depthRow by remember { derivedStateOf { (spot.value.y * 24).toInt() } }
    val sizeDp = (box / LocalDensity.current.density).dp
    Box(
        Modifier.offset {
            val at = spot.value
            IntOffset(snap(widthPx * at.x, pixel) - box / 2, snap(heightPx * groundTop + heightPx * (groundBottom - groundTop) * at.y, pixel) - box)
        }.size(sizeDp).zIndex(depthRow / 24f).graphicsLayer { alpha = fade.value }
            .then(if (interactive) Modifier.pointerInput(mon.id, tapperOn) {
                detectTapGestures(onLongPress = { onMenu(mon) }, onTap = if (tapperOn) null else ({ life.pets[mon.id]?.invoke() }))
            } else Modifier)
            .then(if (exposed && interactive) Modifier.semantics(mergeDescendants = true) {
                contentDescription = "${mon.nickname}, level ${mon.level}"
                role = Role.Button
                onClick("Pet ${mon.nickname}") { life.pets[mon.id]?.invoke(); true }
                onLongClick("Open ${mon.nickname}'s menu") { onMenu(mon); true }
            } else Modifier.clearAndSetSemantics {}),
    ) {
        val frame = life.sizes[mon.id] ?: TownLife.MEDIAN
        Canvas(Modifier.fillMaxSize()) { drawShadow(frame.first, pixel) }
        AnimatedSprite(
            shown.form.id, shown.isShiny, shown.nickname, Modifier.fillMaxSize(), size = sizeDp, animate = !calm && !(hold != null && walk == null), ground = 0.dp,
            action = if (asleep || napping) SpriteAction.SLEEP else if (hopping) SpriteAction.HOP else if (grazing) SpriteAction.EAT else spinning ?: walk ?: if (hold != null) SpriteAction.IDLE_SIDE else facing, actionKey = hopKey, onActionDone = { hopping = false; grazing = false },
            fixedScale = pixel, mirror = if (hold != null) hold.faceLeft ?: facingLeft else if (spinning != null) facingLeft && spinning == SpriteAction.IDLE_SIDE else facingLeft && (walk == SpriteAction.WALK_SIDE || (walk == null && facing != SpriteAction.IDLE_UP)),
        )
        val icon = if (hold != null) hold.icon else if (asleep || napping) StatusKind.SLEEP else ownIcon ?: ailment?.icon
        if (icon != null) key(icon) {
            val rise = ((frame.second * 0.8f).toInt()).coerceIn(ICON_RISE_MIN, ICON_RISE_MAX) - (icon.cell - 16) / 2
            StatusIcon(icon, pixel, Modifier.align(Alignment.BottomCenter).offset(y = (-rise * pixel / LocalDensity.current.density).dp), loop = icon == StatusKind.SLEEP || (hold == null && ownIcon == null && icon == ailment?.icon), animate = !calm)
        }
    }
}

private val GUST_DIRECTIONS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, 1 to -1, -1 to 1, -1 to -1)

private const val ICON_RISE_MIN = 16
private const val ICON_RISE_MAX = 52

private fun DrawScope.drawShadow(frameWidth: Int, pixel: Int) {
    val w = (frameWidth * 0.55f).toInt().coerceIn(10, 44) / 2 * 2
    val h = (w / 4).coerceAtLeast(3)
    val cx = size.width / 2f
    val cy = size.height - h * pixel / 2f
    val colour = Color.Black.copy(alpha = 0.28f)
    for (row in 0 until h) {
        val dy = (row + 0.5f) / h * 2f - 1f
        val half = (w / 2f * kotlin.math.sqrt(1f - dy * dy)).toInt().coerceAtLeast(1)
        drawRect(colour, Offset(cx - half * pixel, cy - h * pixel / 2f + row * pixel), Size(half * 2f * pixel, pixel.toFloat()))
    }
}

private const val PHASE_PERCENT = 12

private const val FIND_SEEK_PERCENT = 15

internal const val NIGHT = 0.5f

internal fun sleepsAtNight(mon: OwnedPokemon): Boolean = (mon.id.hashCode() and 0x7fffffff) % 3 == 0

internal class Hold(val icon: StatusKind?, val hop: Int = 0, val faceLeft: Boolean? = null, val approach: Offset? = null)

internal class TownLife {
    val spots = mutableMapOf<String, () -> Offset>()
    val holds = mutableStateMapOf<String, Hold>()
    val pets = mutableMapOf<String, () -> Unit>()
    var ripple: (String) -> Unit = {}

    var finds: Map<Cell, Int> = emptyMap()
    var pickUp: (Int, Boolean) -> Boolean = { _, _ -> false }

    val revealed = mutableStateMapOf<Cell, Boolean>()

    val goals = mutableMapOf<String, Cell>()
    val claims = mutableMapOf<String, Cell>()
    private var claimedFloor: Any? = null

    val movers = mutableMapOf<String, Mover>()
    val bound = mutableMapOf<String, Offset>()
    val sleeping = mutableSetOf<String>()

    fun occupantOf(g: FloorGeometry, c: Cell, self: String): String? = spots.entries.firstOrNull { (id, s) -> id != self && g.cellOf(s()) == c }?.key

    fun standing(g: FloorGeometry, self: String): Set<Cell> = spots.entries.filter { it.key != self }.map { g.cellOf(it.value()) }.toSet()

    var sheets: Map<Int, Animation>? = null
    val sizes = mutableMapOf<String, Pair<Int, Int>>()

    fun occupiedCells(g: FloorGeometry, self: String): Set<Cell> =
        spots.filterKeys { it != self }.values.map { g.cellOf(it()) }.toSet() + goals.filterKeys { it != self }.values

    fun claimedOn(floor: Any): Map<String, Cell> {
        if (claimedFloor !== floor) { claims.clear(); claimedFloor = floor }
        return claims.toMap()
    }

    fun crowded(g: FloorGeometry, self: String, claimed: Map<String, Cell> = claims): (Cell) -> Boolean {
        val others = buildList {
            spots.forEach { (id, s) -> if (id != self) add(id to g.cellOf(s())) }
            goals.forEach { (id, c) -> if (id != self) add(id to c) }
            claimed.forEach { (id, c) -> if (id != self && id !in spots) add(id to c) }
        }
        return { c -> others.any { (id, at) -> overlap(self, c, id, at) } }
    }

    fun overlap(a: String, at: Cell, b: String, to: Cell): Boolean {
        val (wa, ha) = sizes[a] ?: MEDIAN
        val (wb, hb) = sizes[b] ?: MEDIAN
        val gapX = (wa + wb) / 48f + 0.25f
        val dy = (at.y - to.y).toFloat()
        return abs(at.x - to.x) < gapX && dy > -(0.6f * hb / 24f + 0.25f) && dy < 0.6f * ha / 24f + 0.25f
    }

    companion object {
        val MEDIAN = 23 to 27
    }
}

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

internal class TownTapper { var hit: (Offset) -> Boolean = { false } }

@Composable
private fun TownMeetings(model: AppModel, life: TownLife, residents: List<OwnedPokemon>, calm: Boolean, mood: BuddyMood?, home: Int, guardianId: String?, geo: FloorGeometry? = null) {
    val scope = rememberCoroutineScope()
    val buddy = model.data.player.buddy?.id
    if (!calm) LaunchedEffect(residents.map { it.id }) {
        val random = Random(Random.nextLong())
        val last = mutableMapOf<String, kotlin.time.TimeMark>()
        var nextMeeting = TimeSource.Monotonic.markNow() + (10 + random.nextInt(15)).seconds
        while (true) {
            delay(900)
            if (!nextMeeting.hasPassedNow()) continue
            val dark = BuddyScene.darkness(model.store.now.hour, model.store.now.minute)
            val free = residents.filter { it.id !in life.holds && it.id != guardianId && !TownBehavior.asleep(it, dark) }
            val shuffled = free.shuffled(random)
            pairs@ for (a in shuffled) for (b in shuffled) {
                if (a.id >= b.id || a.id in life.holds || b.id in life.holds) continue
                val pa = life.spots[a.id]?.invoke() ?: continue
                val pb = life.spots[b.id]?.invoke() ?: continue
                if (pa.x !in (home + 0.05f)..(home + 0.95f) || pb.x !in (home + 0.05f)..(home + 0.95f)) continue
                val close = if (geo != null) geo.cellOf(pa).let { ca -> geo.cellOf(pb).let { cb -> maxOf(abs(ca.x - cb.x), abs(ca.y - cb.y)) <= 2 } } else abs(pa.x - pb.x) <= 0.09f && abs(pa.y - pb.y) <= 0.12f
                if (!close) continue
                val pair = a.id + b.id
                if (last[pair]?.let { it.elapsedNow() < 180.seconds } == true || random.nextInt(10) >= 5) continue
                last[pair] = TimeSource.Monotonic.markNow()
                nextMeeting = TimeSource.Monotonic.markNow() + (30 + random.nextInt(40)).seconds
                val withBuddy = a.id == buddy || b.id == buddy
                val other = if (a.id == buddy) b else a
                val (first, second) = if (pa.x <= pb.x) a to b else b to a
                val mid = Offset((pa.x + pb.x) / 2f, (pa.y + pb.y) / 2f)
                val towardsFirst = if (geo != null) null else Offset(mid.x - 0.045f, mid.y)
                val towardsSecond = if (geo != null) null else Offset(mid.x + 0.045f, mid.y)
                scope.launch {
                    when (random.nextInt(10)) {
                        in 0..5 -> {
                            life.holds[first.id] = Hold(StatusKind.QUESTION, faceLeft = false, approach = towardsFirst); life.holds[second.id] = Hold(null, faceLeft = true, approach = towardsSecond)
                            delay(700)
                            if (withBuddy) { model.effect(Sfx.CURIOUS); mood?.meet(other, Emotion.NORMAL, Emotion.NORMAL) }
                            delay(800)
                            life.holds[second.id] = Hold(StatusKind.ALERT, hop = 1, faceLeft = true)
                            if (withBuddy) { model.effect(Sfx.SURPRISE); mood?.meet(other, Emotion.SURPRISED, Emotion.SURPRISED) }
                            delay(900)
                            life.holds[first.id] = Hold(StatusKind.TWINKLE, faceLeft = false); life.holds[second.id] = Hold(StatusKind.TWINKLE, faceLeft = true)
                            if (withBuddy) mood?.meet(other, Emotion.HAPPY, Emotion.JOYOUS)
                        }
                        in 6..7 -> {
                            life.holds[first.id] = Hold(StatusKind.CROSS, faceLeft = false, approach = towardsFirst); life.holds[second.id] = Hold(null, faceLeft = true, approach = towardsSecond)
                            delay(700)
                            if (withBuddy) { model.effect(Sfx.MAD); mood?.meet(other, if (first.id == buddy) Emotion.NORMAL else Emotion.DETERMINED, if (first.id == buddy) Emotion.DETERMINED else Emotion.SURPRISED) }
                            delay(700)
                            life.holds[second.id] = Hold(StatusKind.SWEAT, hop = 1, faceLeft = true)
                        }
                        else -> {
                            life.holds[first.id] = Hold(StatusKind.ZAP, hop = 1, faceLeft = false, approach = towardsFirst); life.holds[second.id] = Hold(StatusKind.ZAP, hop = 1, faceLeft = true, approach = towardsSecond)
                            if (withBuddy) { model.effect(Sfx.SHOCK); mood?.meet(other, Emotion.SURPRISED, Emotion.SURPRISED) }
                        }
                    }
                    delay(1800)
                    life.holds.remove(first.id)
                    life.holds.remove(second.id)
                }
                break@pairs
            }
        }
    }
}

internal fun snap(value: Float, pixel: Int): Int = (value / pixel).roundToInt() * pixel

private val atlases = BitmapCache(CacheBudget.TILES)

@Composable
internal fun TownBackdrop(scene: String, modifier: Modifier = Modifier, darkness: Float = 0f, weather: Weather = Weather.CLEAR) {
    if (TownScenes.isDungeon(scene)) TileTown(scene, modifier, darkness, weather) else SceneImage(scene, modifier.graphicsLayer {}, darkness, crisp = true)
}

@Composable
private fun TileTown(name: String, modifier: Modifier, darkness: Float, weather: Weather) {
    val atlas = rememberCachedImage(atlases, name) { orNull { Res.readBytes("files/tiles/$name.png").decodeToImageBitmap() } }
    val shown by produceState(atlas, atlas, weather) { value = atlas?.let { gradedFor(name, it, weather) } }
    val fade by rememberFadeIn(atlas != null)
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
