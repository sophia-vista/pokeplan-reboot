package app.pokeplan.ui.launcher

import app.pokeplan.ui.theme.DialogueShape

import app.pokeplan.ui.theme.PixelShape

import kotlinx.datetime.plus
import kotlinx.datetime.minus
import kotlinx.datetime.DatePeriod
import androidx.compose.ui.graphics.toPixelMap
import kotlin.time.Duration.Companion.milliseconds
import app.pokeplan.ui.theme.stairRect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import kotlin.math.abs
import androidx.compose.foundation.gestures.draggable
import app.pokeplan.ui.pressScale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import app.pokeplan.ui.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import app.pokeplan.app.ThemeMode
import androidx.compose.foundation.layout.widthIn
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.entrance
import app.pokeplan.ui.LocalUiSound
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.Glyph
import app.pokeplan.ui.SceneImage
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.produceState
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.theme.focusRing
import app.pokeplan.ui.Place
import app.pokeplan.ui.Tab
import app.pokeplan.ui.screens.LocalReduceMotion
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PmdText
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelTheme
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.theme.focusRing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class BatteryStatus(val percent: Int, val charging: Boolean)

interface LauncherHost {
    suspend fun icon(key: String): ImageBitmap?
    fun open(key: String)
    fun appInfo(key: String)
    fun uninstall(key: String)
    fun openBaseSettings()
    fun openPokemon(id: String)
    fun battery(): BatteryStatus? = null
    fun powerSaving(): Boolean = false
    suspend fun forecast(place: GeoPlace): Forecast? = null
    suspend fun searchPlaces(query: String): List<GeoPlace> = emptyList()
    @Composable
    fun HostedWidget(id: Int, size: Int, widthDp: Int, heightDp: Int, modifier: Modifier) {}
    fun pickWidget(onPicked: (Int) -> Unit) {}
    fun releaseWidget(id: Int) {}
    fun widgetLabel(id: Int): String? = null
    val hostsWidgets: Boolean get() = false
    fun widgetSpec(id: Int): HostedWidgetSpec? = null
    fun openApp(place: Place)
    fun expandNotifications() {}
}

private val rowShape = DialogueShape

private fun rubberPage(x: Float, last: Float = 1f, first: Float = 0f): Float = when {
    x < first -> first + (x - first) / (1f - (x - first) * 4f) * 0.5f
    x > last -> last + (x - last) / (1f + (x - last) * 4f) * 0.5f
    else -> x
}

private fun rubber(pulled: Float): Float = when {
    pulled < 0f -> pulled / (1f - pulled * 4f) * 0.5f
    pulled > 1f -> 1f
    else -> pulled
}

@Composable
private fun LauncherPage(visible: Boolean, calm: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible,
        enter = if (calm) androidx.compose.animation.EnterTransition.None else androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200, easing = app.pokeplan.ui.Ease.Smooth)) +
            androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(260, easing = app.pokeplan.ui.Ease.Smooth)) { it / 12 },
        exit = if (calm) androidx.compose.animation.ExitTransition.None else androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140, easing = app.pokeplan.ui.Ease.Smooth)) +
            androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(180, easing = app.pokeplan.ui.Ease.Smooth)) { it / 12 },
    ) { content() }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LauncherScreen(model: AppModel, apps: List<LauncherApp>, prefs: LauncherPrefs, onPrefs: (LauncherPrefs) -> Unit, isDefault: Boolean, phone: LauncherHost, homePresses: Int = 0, active: Boolean = true) {
    val data = model.data
    val prefsNow by androidx.compose.runtime.rememberUpdatedState(prefs)
    val onPrefsNow by androidx.compose.runtime.rememberUpdatedState(onPrefs)
    val host: LauncherHost = remember(phone) { object : LauncherHost by phone { override fun open(key: String) { onPrefsNow(prefsNow.recordLaunch(key)); phone.open(key) } } }
    val dark = when (data.settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    var charge by remember { mutableStateOf(phone.battery()) }
    var phoneSaving by remember { mutableStateOf(phone.powerSaving()) }
    LaunchedEffect(active) { while (active) { charge = phone.battery(); phoneSaving = phone.powerSaving(); delay(60_000) } }
    val saver = batterySaving(prefs.batterySaver, charge, phoneSaving)
    LaunchedEffect(saver, active) {
        while (active) {
            model.tickClock()
            delay(if (saver) 10_000 else 1000)
        }
    }
    val rotation by remember(model) { androidx.compose.runtime.derivedStateOf { model.now; TownBehavior.rotation(model.store.today.toEpochDays()) } }
    var listOpen by remember { mutableStateOf(false) }
    var gather by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var taps by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val tapper = remember { TownTapper() }
    var lastTap by remember { mutableStateOf<kotlin.time.TimeMark?>(null) }
    var sheetKey by remember { mutableStateOf<String?>(null) }
    var pickingTown by remember { mutableStateOf(false) }
    var menuMon by remember { mutableStateOf<OwnedPokemon?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var foldersOpen by remember { mutableStateOf(false) }
    var widgetsOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var pageNudge by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    fun runAction(action: GestureAction) {
        when (action) {
            GestureAction.NONE -> {}
            GestureAction.APPS -> listOpen = true
            GestureAction.GATHER -> gather++
            GestureAction.POKELIFE -> host.openApp(Place.HOME)
            GestureAction.GEAR -> host.openApp(Place.GEAR)
            GestureAction.SETTINGS -> settingsOpen = true
            GestureAction.FOLDERS -> foldersOpen = true
            GestureAction.WIDGETS -> widgetsOpen = true
            GestureAction.EDIT -> editing = true
            GestureAction.PAGE -> pageNudge++
            GestureAction.NOTIFICATIONS -> host.expandNotifications()
        }
    }
    fun countTap() {
        val job = prefs.action(GestureTrigger.TRIPLE_TAP)
        if (job == GestureAction.NONE) return
        taps = if (lastTap?.let { it.elapsedNow() < 600.milliseconds } == true) taps + 1 else 1
        lastTap = kotlin.time.TimeSource.Monotonic.markNow()
        if (taps >= 3) { runAction(job); taps = 0 }
    }
    val todayState = remember { TodayState(model.store.today) }
    val mood = remember { BuddyMood() }
    val source = model.calendars
    val calendarsShown = data.shownCalendars
    LaunchedEffect(todayState.month, calendarsShown, todayState.permissionAnswers, active) {
        if (source == null || !active) return@LaunchedEffect
        while (true) {
            val first = todayState.month.minus(DatePeriod(days = 7))
            val last = todayState.month.plus(DatePeriod(days = 42))
            todayState.device = if (source.permitted() && calendarsShown.isNotEmpty()) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { runCatching { source.events(calendarsShown, first, last) }.getOrDefault(emptyList()) } else emptyList()
            delay(5 * 60_000L)
        }
    }
    var folderName by remember { mutableStateOf<String?>(null) }
    var forecastOpen by remember { mutableStateOf(false) }
    var placeOpen by remember { mutableStateOf(false) }
    var forecast by remember { mutableStateOf<Forecast?>(null) }
    LaunchedEffect(prefs.place, active, saver) {
        val place = prefs.place
        if (place == null) forecast = null else if (active) while (true) { host.forecast(place)?.let { forecast = it }; delay(if (saver) 120 * 60_000L else 30 * 60_000L) }
    }
    val reveal = remember { androidx.compose.animation.core.Animatable(1f) }
    var settle by remember { mutableStateOf(0) }
    var release by remember { mutableStateOf<Float?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(listOpen, settle) {
        val to = if (listOpen) 0f else 1f
        val velocity = release
        release = null
        when {
            data.settings.reduceMotion -> reveal.snapTo(to)
            velocity != null -> reveal.animateTo(to, androidx.compose.animation.core.spring(dampingRatio = 0.82f, stiffness = 260f), initialVelocity = velocity)
            else -> reveal.animateTo(to, androidx.compose.animation.core.tween(320, easing = app.pokeplan.ui.Ease.Smooth))
        }
    }
    var listHidden by remember { mutableStateOf(false) }
    LaunchedEffect(homePresses) {
        if (homePresses > 0) { listOpen = false; sheetKey = null; pickingTown = false; menuMon = null; settingsOpen = false; foldersOpen = false; folderName = null; forecastOpen = false; placeOpen = false; widgetsOpen = false; editing = false }
    }
    val sheetApp = sheetKey?.let { key -> apps.firstOrNull { it.key == key } }
    PixelTheme(dark) {
        CompositionLocalProvider(LocalReduceMotion provides (data.settings.reduceMotion || saver), LocalUiSound provides model::tap) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(Pixel.Paper)) {
                val pixel = townScale(maxWidth.value * LocalDensity.current.density)
                val page = remember { androidx.compose.animation.core.Animatable(0f) }
                val order = if (prefs.gridMode) prefs.pageOrder else WidgetPage.entries.take(2)
                val pageCount = order.size
                val left = order.indexOf(WidgetPage.HOME)
                val right = pageCount - 1 - left
                val atHome by remember { androidx.compose.runtime.derivedStateOf { abs(page.value) < 0.02f } }
                val current by remember(left) { androidx.compose.runtime.derivedStateOf { (page.value + left + 0.5f).toInt() } }
                val visibleSides by remember(left, right) { androidx.compose.runtime.derivedStateOf { (-left..right).filter { it != 0 && abs(page.value - it) < 1f } } }
                val env = WidgetEnv(
                    model, apps, prefs, host, forecast, pixel, todayState, mood, saver, editing, onPrefs, onEdit = { editing = true }, onDone = { editing = false }, onAllApps = { listOpen = true }, onFolders = { foldersOpen = true }, onWidgets = { widgetsOpen = true; editing = false }, onGear = { host.openApp(Place.GEAR) },
                    onOpenFolder = { folderName = it; foldersOpen = true }, onForecast = { forecastOpen = true }, onHold = { sheetKey = it.key },
                    pages = pageCount, battery = charge,
                    goToPage = { i -> scope.launch { if (data.settings.reduceMotion) page.snapTo((i - left).toFloat()) else page.animateTo((i - left).toFloat(), androidx.compose.animation.core.tween(320, easing = app.pokeplan.ui.Ease.Smooth)) } },
                )
                val widthPx0 = maxWidth.value * LocalDensity.current.density
                val topInset = with(LocalDensity.current) { androidx.compose.foundation.layout.WindowInsets.Companion.safeDrawing.getTop(this).toDp() }
                val avoidRects = remember(prefs.gridMode, prefs.gridWidgets, order, maxWidth, maxHeight, topInset) { if (!prefs.gridMode) emptyList() else {
                    val inset = Spacing.Page - CELL_GAP
                    val column = (maxWidth - inset * 2) / WidgetGrid.COLUMNS
                    order.flatMapIndexed { i, pg ->
                        val top = topInset + if (pg == WidgetPage.HOME) Spacing.L else Spacing.XXL
                        WidgetGrid.onPage(prefs.gridWidgets, pg).map { w ->
                            androidx.compose.ui.geometry.Rect(
                                i + (inset + column * w.col) / maxWidth, (top + WidgetGrid.ROW_DP.dp * w.row) / maxHeight,
                                i + (inset + column * w.right) / maxWidth, (top + WidgetGrid.ROW_DP.dp * w.bottom) / maxHeight,
                            )
                        }
                    }
                } }
                LaunchedEffect(pageNudge) {
                    if (pageNudge == 0) return@LaunchedEffect
                    val to = if (page.value.roundToInt() >= right) -left.toFloat() else page.value.roundToInt() + 1f
                    if (data.settings.reduceMotion) page.snapTo(to) else page.animateTo(to, androidx.compose.animation.core.tween(320, easing = app.pokeplan.ui.Ease.Smooth))
                }
                LaunchedEffect(homePresses) { if (homePresses > 0) page.animateTo(0f, androidx.compose.animation.core.tween(320, easing = app.pokeplan.ui.Ease.Smooth)) }
                Box(
                    Modifier.fillMaxSize().pointerInput(prefs.gestures, prefs.gatherTap) {
                        val double = prefs.action(GestureTrigger.DOUBLE_TAP)
                        val hold = prefs.action(GestureTrigger.LONG_PRESS)
                        detectTapGestures(onLongPress = if (hold == GestureAction.NONE) null else ({ at ->
                            if (abs(page.value) < 0.02f && !editing && !tapper.hit(Offset(at.x + (page.value + left) * widthPx0, at.y))) runAction(hold)
                        }), onDoubleTap = if (double == GestureAction.NONE) null else ({ at ->
                            if (abs(page.value) < 0.02f && !editing && !tapper.hit(Offset(at.x + (page.value + left) * widthPx0, at.y))) runAction(double)
                        }), onTap = { at -> if (abs(page.value) >= 0.02f || editing || !tapper.hit(Offset(at.x + (page.value + left) * widthPx0, at.y))) countTap() })
                    }.pointerInput(prefs.gestures, editing) {
                        var travelled = 0f
                        val reach = 64.dp.toPx()
                        detectVerticalDragGestures(
                            onDragStart = { travelled = 0f }, onDragCancel = { travelled = 0f },
                            onDragEnd = {
                                if (!editing && abs(page.value) < 0.02f) {
                                    if (travelled < -reach) runAction(prefs.action(GestureTrigger.SWIPE_UP)) else if (travelled > reach) runAction(prefs.action(GestureTrigger.SWIPE_DOWN))
                                }
                                travelled = 0f
                            },
                        ) { _, dy -> travelled += dy }
                    }.pointerInput(prefs.gestures, editing) {
                        val reach = 72.dp.toPx()
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            var start: Float? = null
                            var now = 0f
                            do {
                                val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                val down = event.changes.filter { it.pressed }
                                if (down.size >= 2) {
                                    val y = down.map { it.position.y }.average().toFloat()
                                    if (start == null) start = y
                                    now = y
                                }
                            } while (event.changes.any { it.pressed })
                            val travelled = start?.let { now - it } ?: 0f
                            if (!editing && abs(page.value) < 0.02f) {
                                if (travelled < -reach) runAction(prefs.action(GestureTrigger.TWO_FINGER_UP)) else if (travelled > reach) runAction(prefs.action(GestureTrigger.TWO_FINGER_DOWN))
                            }
                        }
                    }.draggable(
                        enabled = prefs.pageSwipe && !editing,
                        orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                        state = androidx.compose.foundation.gestures.rememberDraggableState { dx -> scope.launch { page.snapTo(rubberPage(page.value - dx / widthPx0, right.toFloat(), -left.toFloat())) } },
                        onDragStopped = { velocity ->
                            val perSecond = -velocity / widthPx0
                            val to = (if (abs(perSecond) > 1.2f) (if (perSecond > 0f) kotlin.math.ceil(page.value) else kotlin.math.floor(page.value)) else page.value.roundToInt().toFloat()).coerceIn(-left.toFloat(), right.toFloat())
                            scope.launch {
                                if (data.settings.reduceMotion) page.snapTo(to) else page.animateTo(to, androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 300f), initialVelocity = perSecond)
                            }
                        },
                    ),
                ) {
                    Town(
                        model, prefs.scene, active && prefs.walking && !saver, onMenu = { menuMon = it }, size = prefs.townSize, activity = prefs.activity, mood = mood, chirps = prefs.chirps && !saver, weather = when (prefs.weather) { Weather.LIVE -> forecast?.let { WeatherCodes.town(it.code) } ?: Weather.CLEAR; Weather.GUARDIAN -> guardianWeather(shownGuardian(model.data.player.pokemon, model.data.player.moduleGuardians.values, model.data.player.buddy?.id, rotation)); else -> prefs.weather }, screens = pageCount, home = left, avoid = avoidRects, tapper = tapper, interactive = atHome, gather = gather, rotation = rotation, layoutRoll = prefs.layoutRollOn(model.store.today.toEpochDays().toLong()),
                        modifier = Modifier.layout { measurable, constraints ->
                            val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(constraints.maxWidth * pageCount, constraints.maxHeight))
                            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place((-(page.value + left) * constraints.maxWidth).roundToInt(), 0) }
                        },
                    )
                    Box(Modifier.fillMaxSize().graphicsLayer { translationX = -page.value * widthPx0 }) {
                        if (prefs.gridMode) GridHomePage(env, when {
                            !data.onboarded -> "Open PokéLife to meet your first Pokémon" to { host.openApp(Place.HOME) }
                            !isDefault -> "Make PokéLife your home app" to host::openBaseSettings
                            else -> null
                        }) else HomePage(env, isDefault)
                    }
                    for (k in visibleSides) {
                        androidx.compose.runtime.key(k) { Box(Modifier.fillMaxSize().graphicsLayer { translationX = (k - page.value) * widthPx0 }) { if (prefs.gridMode) GridSidePage(env, order[left + k]) else TodayPage(env) } }
                    }
                }
                val moving by remember { androidx.compose.runtime.derivedStateOf { page.isRunning || abs(page.value - page.value.roundToInt()) > 0.001f } }
                var lingering by remember { mutableStateOf(false) }
                LaunchedEffect(moving) { if (moving) lingering = true else { delay(900); lingering = false } }
                val indicator by androidx.compose.animation.core.animateFloatAsState(if (moving || lingering) 1f else 0f, androidx.compose.animation.core.tween(220, easing = app.pokeplan.ui.Ease.Smooth))
                if (indicator > 0.01f) Row(Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = Spacing.S).graphicsLayer { alpha = indicator }, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    repeat(pageCount) { i -> Box(Modifier.size(width = 16.dp, height = 4.dp).background(Color.White.copy(alpha = if (current == i) 0.9f else 0.3f))) }
                }
                if (prefs.gridMode && editing && !widgetsOpen) Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.BottomCenter) {
                    GridEditBar(env, order[current.coerceIn(0, pageCount - 1)])
                }
                BackHandler(enabled = true) { if (menuMon != null) menuMon = null else if (pickingTown) pickingTown = false else if (placeOpen) placeOpen = false else if (widgetsOpen) widgetsOpen = false else if (editing) editing = false else if (forecastOpen) forecastOpen = false else if (folderName != null) folderName = null else if (foldersOpen) foldersOpen = false else if (settingsOpen) settingsOpen = false else if (sheetApp != null) sheetKey = null else if (listOpen) listOpen = false else if (abs(page.value) > 0.5f) scope.launch { page.animateTo(0f, androidx.compose.animation.core.tween(320, easing = app.pokeplan.ui.Ease.Smooth)) } }
                val widthPx = maxWidth.value * LocalDensity.current.density
                var pulled by remember { mutableStateOf(1f) }
                if (prefs.action(GestureTrigger.EDGE_DRAG) == GestureAction.APPS && !listOpen) Box(
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(24.dp).draggable(
                        orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                        state = androidx.compose.foundation.gestures.rememberDraggableState { dx ->
                            pulled += dx / widthPx
                            scope.launch { reveal.snapTo(rubber(pulled)) }
                        },
                        onDragStarted = { pulled = reveal.value },
                        onDragStopped = { velocity ->
                            val perSecond = velocity / widthPx
                            listOpen = if (abs(perSecond) > 1.2f) perSecond < 0f else reveal.value < 0.5f
                            release = perSecond
                            settle++
                        },
                    ),
                )
                val edgeJob = prefs.action(GestureTrigger.EDGE_DRAG)
                if (edgeJob != GestureAction.APPS && edgeJob != GestureAction.NONE && !listOpen) {
                    var travelled by remember { mutableStateOf(0f) }
                    Box(
                        Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(24.dp).draggable(
                            orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                            state = androidx.compose.foundation.gestures.rememberDraggableState { dx -> travelled += dx },
                            onDragStarted = { travelled = 0f },
                            onDragStopped = { if (travelled < -widthPx * 0.2f) runAction(edgeJob); travelled = 0f },
                        ),
                    )
                }
                val shown = (1f - reveal.value).coerceIn(0f, 1f)
                if (reveal.value < 1f) Box(
                    Modifier.fillMaxSize().graphicsLayer { alpha = shown; translationX = (1f - shown) * 56.dp.toPx() }.then(if (shown < 0.97f && !data.settings.reduceMotion && !saver) Modifier.blur(((1f - shown) * 10f).dp) else Modifier),
                ) {
                    AppListPage(apps, prefs, host, onClose = { listOpen = false }, onHold = { sheetKey = it.key }, onTown = { settingsOpen = true }, startHidden = listHidden, pixel = pixel)
                }
                menuMon?.let { mon -> PokemonMenu(mon, mon.id == model.data.player.buddy?.id, pixel, onOpen = { host.openPokemon(mon.id); menuMon = null }, onBuddy = { model.store.setBuddy(mon.id); menuMon = null }, onClose = { menuMon = null }) }
                val calm = data.settings.reduceMotion
                LauncherPage(settingsOpen, calm) {
                    LauncherSettings(
                        model, prefs, onPrefs, isDefault, apps.count { it.key in prefs.hidden }, pixel, host,
                        onTown = { settingsOpen = false; listOpen = false; pickingTown = true },
                        onHidden = { settingsOpen = false; listHidden = true; listOpen = true },
                        onPlace = { placeOpen = true },
                        onWidgets = { widgetsOpen = true },
                        onClose = { settingsOpen = false },
                    )
                }
                LauncherPage(widgetsOpen, calm) { WidgetsPage(prefs, onPrefs, host, pixel, onClose = { widgetsOpen = false }) }
                LauncherPage(foldersOpen, calm) { FoldersPage(apps, prefs, onPrefs, host, folderName, { folderName = it }, onClose = { foldersOpen = false; folderName = null }, pixel = pixel) }
                LauncherPage(forecastOpen, calm) { ForecastPage(forecast, prefs, pixel, onPlace = { placeOpen = true }, onClose = { forecastOpen = false }) }
                LauncherPage(placeOpen, calm) { PlacePage(prefs, onPrefs, host, pixel, onClose = { placeOpen = false }) }
                LauncherPage(pickingTown, calm) { TownPicker(prefs.scene, onPick = { onPrefs(prefs.withScene(it)); pickingTown = false }, onClose = { pickingTown = false }) }
                if (sheetApp != null) AppSheet(sheetApp, prefs, onPrefs, onClose = { sheetKey = null }, host = host)
            }
        }
    }
}

@Composable
private fun HomePage(env: WidgetEnv, isDefault: Boolean) {
    val data = env.model.data
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(vertical = Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        if (env.editing) EditToolbar(env, Modifier.padding(horizontal = Spacing.Page))
        env.prefs.widgetsIn(WidgetArea.HOME_TOP).forEach { slot -> androidx.compose.runtime.key(slot.kind, slot.arg) { LauncherWidget(slot, env) } }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                val note = when {
                    !data.onboarded -> "Open PokéLife to meet your first Pokémon" to { env.host.openApp(Place.HOME) }
                    !isDefault -> "Make PokéLife your home app" to env.host::openBaseSettings
                    else -> null
                }
                if (note != null) PmdBox(env.pixel, Modifier.fillMaxWidth().padding(horizontal = Spacing.Page), inset = 1) { PmdMenuLine(note.first, env.pixel, onClick = note.second) }
                env.prefs.widgetsIn(WidgetArea.HOME_BOTTOM).forEach { slot -> androidx.compose.runtime.key(slot.kind, slot.arg) { LauncherWidget(slot, env) } }
            }
        }
    }
}

/**
 * One app row as [PinnedApp] lays it out: the picture, the row's padding above and below, and, with a name, the gap and the
 * name's line. The launcher's widgets count the rows that fit from this, so a change to the row moves the fit with it.
 */
internal object PinnedAppRow {
    /** The picture's size. */
    val Icon = 40.dp
    /** The row's padding above and below. */
    val Pad = Spacing.S
    /** The gap under the picture before its name. */
    val LabelGap = Spacing.M

    /** A row's height for this pixel scale: [Icon] and [Pad] always, [LabelGap] and the name's line only when [labelled]. */
    fun height(pixel: Int, density: Float, labelled: Boolean): androidx.compose.ui.unit.Dp =
        Icon + Pad * 2 + if (labelled) LabelGap + (app.pokeplan.ui.theme.PmdFont.LINE * pixel / density).dp else 0.dp
}

/** A pinned app on the home page: its logo (or the game item chosen for it) over its name; tap opens it, press and hold manages it. */@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PinnedApp(app: LauncherApp, item: String?, host: LauncherHost, pixel: Int, modifier: Modifier, onHold: () -> Unit, showLabel: Boolean = true) {
    val ui = LocalUiSound.current
    val density = LocalDensity.current.density
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        modifier.pressScale(source).defaultMinSize(minHeight = PinnedAppRow.height(pixel, density, showLabel)).focusRing(rowShape, Color.White).combinedClickable(interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Button, onLongClick = { ui(Sfx.LONG_PRESS); onHold() }, onClick = { ui(Sfx.CONFIRM); host.open(app.key) })
            .semantics(mergeDescendants = true) {}.padding(vertical = Spacing.S),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        if (item != null) ItemIcon(item, size = 40.dp, description = null) else AppIcon(host, app.key, 40.dp)
        if (showLabel) Box(Modifier.padding(top = Spacing.M).padding(horizontal = (1 * pixel / density).dp)) { PmdText(app.label, pixel) }
    }
}

@Composable
internal fun AppIcon(host: LauncherHost, key: String, size: androidx.compose.ui.unit.Dp) {
    val icon by produceState<ImageBitmap?>(null, key) { value = host.icon(key) }
    val shown = icon
    val edge = remember(shown) { shown?.let { edgeColors.getOrPut(key) { edgeColor(it) } } ?: Color.White.copy(alpha = 0.9f) }
    Box(
        Modifier.size(size).drawWithContent {
            val p = 2.dp.toPx()
            val w = this.size.width
            val h = this.size.height
            val square = stairRect(p, 0f, 0f, w, h, intArrayOf(1, 0))
            if (shown == null) drawPath(square, Color.White.copy(alpha = 0.2f)) else {
                drawPath(square, edge)
                clipPath(square) { this@drawWithContent.drawContent() }
            }
        },
    ) {
        if (shown != null) Image(shown, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    }
}

private val edgeColors = HashMap<String, Color>()

internal fun edgeColor(image: ImageBitmap): Color {
    val map = image.toPixelMap()
    val buckets = HashMap<Int, IntArray>()
    var seen = 0
    fun add(c: Color) {
        if (c.alpha < 0.5f) return
        seen++
        val key = ((c.red * 15).toInt() shl 8) or ((c.green * 15).toInt() shl 4) or (c.blue * 15).toInt()
        val sum = buckets.getOrPut(key) { IntArray(4) }
        sum[0] += (c.red * 255).toInt(); sum[1] += (c.green * 255).toInt(); sum[2] += (c.blue * 255).toInt(); sum[3]++
    }
    for (x in 0 until map.width) { add(map[x, 0]); add(map[x, map.height - 1]) }
    for (y in 1 until map.height - 1) { add(map[0, y]); add(map[map.width - 1, y]) }
    val top = buckets.values.maxByOrNull { it[3] }
    return if (top == null || seen < (map.width + map.height) / 2) Color(0xFF3A3A44) else Color(top[0] / top[3] / 255f, top[1] / top[3] / 255f, top[2] / top[3] / 255f)
}

private sealed interface ListRow {
    data class Header(val letter: Char) : ListRow
    data class Entry(val app: LauncherApp) : ListRow
    data class Destination(val to: LauncherDestination) : ListRow
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun AppListPage(apps: List<LauncherApp>, prefs: LauncherPrefs, host: LauncherHost, onClose: () -> Unit, onHold: (LauncherApp) -> Unit, onTown: () -> Unit = {}, startHidden: Boolean = false, pixel: Int = 2) {
    val ui = LocalUiSound.current
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<AppCategory?>(null) }
    var showHidden by remember { mutableStateOf(startHidden) }
    val categories = remember(apps, prefs) { LauncherList.categories(apps, prefs) }
    if (category != null && categories.none { it.first == category }) category = null
    val anyHidden = prefs.hidden.any { key -> apps.any { it.key == key } }
    if (showHidden && !anyHidden) showHidden = false
    val shown = remember(apps, prefs, query, category, showHidden) { LauncherList.visible(apps, prefs, query, category, hidden = showHidden) }
    val sections = query.isBlank()
    val places = remember(query, category, showHidden) { if (category == null && !showHidden) LauncherSearch.find(query) else emptyList() }
    val rows = remember(shown, sections, places) { places.map<LauncherDestination, ListRow> { ListRow.Destination(it) } + (if (sections) buildList<ListRow> { shown.forEach { app -> if ((lastOrNull() as? ListRow.Entry)?.app?.letter() != app.letter()) add(ListRow.Header(app.letter())); add(ListRow.Entry(app)) } } else shown.map<LauncherApp, ListRow> { ListRow.Entry(it) }) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    BackHandler(enabled = true) { if (query.isNotEmpty()) query = "" else onClose() }
    val soft = Color.White.copy(alpha = 0.7f)
    Box(Modifier.fillMaxSize()) {
        TownBackdrop(prefs.scene, Modifier.fillMaxSize().blur(24.dp))
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)))
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.padding(start = Spacing.Page, end = Spacing.Page, top = Spacing.L, bottom = Spacing.M), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Box(
                    Modifier.weight(1f).height(Spacing.Target).background(Color.White.copy(alpha = 0.14f), rowShape).padding(horizontal = Spacing.L),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) PmdText("Search apps and PokéLife", pixel, alpha = 0.7f)
                    BasicTextField(
                        query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search apps and PokéLife" },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White), cursorBrush = SolidColor(Color.White),
                    )
                }
                Box(Modifier.size(Spacing.Target).clip(rowShape).clickable(role = Role.Button, onClickLabel = "Launcher settings") { ui(Sfx.CURSOR); onTown() }, contentAlignment = Alignment.Center) {
                    Glyph("settings-cog", Color.White, size = 24.dp, description = "Settings")
                }
                Box(Modifier.size(Spacing.Target).clip(rowShape).clickable(role = Role.Button, onClickLabel = "Close") { ui(Sfx.BACK); onClose() }, contentAlignment = Alignment.Center) {
                    Glyph("close", Color.White, size = 24.dp, description = "Close")
                }
            }
            LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = Spacing.Page, vertical = Spacing.XS), horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                item(key = "#all") { Chip("All", "", category == null && !showHidden, pixel) { category = null; showHidden = false } }
                items(categories, key = { (c, _) -> "#c${c.name}" }) { (c, n) -> Chip(c.label, "$n", category == c && !showHidden, pixel, icon = c.icon) { category = c; showHidden = false } }
                if (anyHidden) item(key = "#hidden") { Chip("Hidden", "", showHidden, pixel) { showHidden = true; category = null } }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (rows.isEmpty()) PmdText(if (query.isBlank()) "No apps here" else "Nothing matches " + query.trim(), pixel, Modifier.align(Alignment.Center), alpha = 0.7f)
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = Spacing.Page, end = Spacing.Page + 32.dp, top = Spacing.M, bottom = Spacing.XXL)) {
                    items(rows, key = { row -> when (row) { is ListRow.Header -> "#h${row.letter}"; is ListRow.Entry -> row.app.key; is ListRow.Destination -> "#p${row.to.place.name}" } }) { row ->
                        when (row) {
                            is ListRow.Destination -> Row(
                                Modifier.fillMaxWidth().clip(rowShape).clickable(role = Role.Button, onClickLabel = "Open ${row.to.label} in PokéLife") { ui(Sfx.CONFIRM); host.openApp(row.to.place) }.padding(vertical = Spacing.M, horizontal = Spacing.S),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.L),
                            ) {
                                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { ItemIcon(row.to.icon, size = 32.dp, description = null) }
                                PmdText(row.to.label, pixel, Modifier.weight(1f))
                                PmdText("PokéLife", pixel, alpha = 0.6f)
                            }
                            is ListRow.Header -> PmdText(row.letter.toString(), pixel, Modifier.padding(top = Spacing.XL, bottom = Spacing.S, start = Spacing.S), alpha = 0.7f)
                            is ListRow.Entry -> { val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }; Row(
                                Modifier.fillMaxWidth().pressScale(source).clip(rowShape).combinedClickable(interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Button, onLongClick = { ui(Sfx.LONG_PRESS); onHold(row.app) }, onClick = { ui(Sfx.CONFIRM); host.open(row.app.key) })
                                    .padding(vertical = Spacing.M, horizontal = Spacing.S),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.L),
                            ) {
                                AppIcon(host, row.app.key, 40.dp)
                                PmdText(row.app.label, pixel, Modifier.weight(1f))
                            } }
                        }
                    }
                }
                if (sections && rows.size > 12) LetterScroller(LauncherList.letters(shown).map { it.first }, pixel, Modifier.align(Alignment.CenterEnd)) { letter ->
                    val at = rows.indexOfFirst { it is ListRow.Header && it.letter == letter }
                    if (at >= 0) scope.launch { listState.scrollToItem(at) }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, count: String, selected: Boolean, pixel: Int, icon: String? = null, onClick: () -> Unit) {
    val shape = DialogueShape
    Row(
        Modifier.height(Spacing.Target).clip(shape).background(if (selected) Color.White else Color.White.copy(alpha = 0.14f), shape)
            .clickable(role = Role.Tab, onClick = onClick).padding(horizontal = Spacing.L),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
    ) {
        if (icon != null) ItemIcon(icon, size = 20.dp, description = null)
        PmdText(if (count.isEmpty()) label else "$label $count", pixel, onLight = selected)
    }
}

@Composable
private fun LetterScroller(letters: List<Char>, pixel: Int, modifier: Modifier, onLetter: (Char) -> Unit) {
    if (letters.isEmpty()) return
    var height by remember { mutableStateOf(1f) }
    var last by remember { mutableStateOf<Char?>(null) }
    val ui = LocalUiSound.current
    fun pick(y: Float) {
        val letter = letters[(y / height * letters.size).toInt().coerceIn(0, letters.lastIndex)]
        if (letter != last) { last = letter; ui(Sfx.CURSOR); onLetter(letter) }
    }
    Column(
        modifier.width(32.dp).fillMaxHeight().padding(vertical = Spacing.S)
            .onSizeChanged { height = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(letters) { detectDragGestures(onDragStart = { pick(it.y) }, onDragEnd = { last = null }, onDragCancel = { last = null }) { change, _ -> change.consume(); pick(change.position.y) } }
            .pointerInput(letters) { detectTapGestures { pick(it.y); last = null } },
        verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { PmdText(it.toString(), pixel, alpha = 0.85f) }
    }
}

@Composable
internal fun AppSheet(app: LauncherApp, prefs: LauncherPrefs, onPrefs: (LauncherPrefs) -> Unit, onClose: () -> Unit, host: LauncherHost) {
    var moving by remember { mutableStateOf(false) }
    var foldering by remember { mutableStateOf(false) }
    var choosingIcon by remember { mutableStateOf(false) }
    val pinned = app.key in prefs.pinned
    val hidden = app.key in prefs.hidden
    val none = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(indication = null, interactionSource = none, onClick = onClose)) {
        Box(Modifier.align(Alignment.BottomCenter).entrance().safeDrawingPadding().padding(Spacing.L).clickable(indication = null, interactionSource = none) {}) {
            TextBox {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                        AppIcon(host, app.key, 40.dp)
                        Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (choosingIcon) {
                        Text("Icon on the home page", style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft)
                        PixelButton("The app's own logo", { onPrefs(prefs.withIcon(app.key, null)); onClose() }, Modifier.fillMaxWidth(), if (app.key !in prefs.icons) Pixel.Blue else Pixel.InkSoft, compact = true)
                        Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                            LauncherIcons.items.chunked(6).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                                    row.forEach { item ->
                                        Box(
                                            Modifier.weight(1f).height(48.dp).clip(rowShape).then(if (prefs.icons[app.key] == item) Modifier.border(2.dp, Pixel.Blue, rowShape) else Modifier)
                                                .clickable(role = Role.RadioButton, onClickLabel = "Use $item") { onPrefs(prefs.withIcon(app.key, item)); onClose() },
                                            contentAlignment = Alignment.Center,
                                        ) { ItemIcon(item, size = 32.dp, description = null) }
                                    }
                                    repeat(6 - row.size) { Box(Modifier.weight(1f)) }
                                }
                            }
                        }
                    } else if (foldering) {
                        Text("Add to folder", style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft)
                        prefs.folders.forEach { f ->
                            val inIt = app.key in f.apps
                            PixelButton(if (inIt) "${f.name} (in it: take out)" else f.name, { onPrefs(if (inIt) prefs.removeFromFolder(f.name, app.key) else prefs.addToFolder(f.name, app.key)); onClose() }, Modifier.fillMaxWidth(), if (inIt) Pixel.Blue else Pixel.InkSoft, compact = true)
                        }
                        PixelButton("New folder with ${app.label}", {
                            var n = prefs.folders.size + 1
                            while (prefs.folders.any { it.name == "Folder $n" }) n++
                            onPrefs(prefs.addFolderWidget("Folder $n").addToFolder("Folder $n", app.key)); onClose()
                        }, Modifier.fillMaxWidth(), Pixel.Green, compact = true)
                    } else if (!moving) {
                        if (!hidden) PixelButton(if (pinned) "Unpin from home" else "Pin to home", { onPrefs(if (pinned) prefs.unpin(app.key) else prefs.pin(app.key)); onClose() }, Modifier.fillMaxWidth(), Pixel.Blue, sound = if (pinned) Sfx.UNPIN else Sfx.PIN)
                        PixelButton(if (hidden) "Show again" else "Hide", { onPrefs(if (hidden) prefs.unhide(app.key) else prefs.hide(app.key)); onClose() }, Modifier.fillMaxWidth(), Pixel.InkSoft, sound = if (hidden) Sfx.UNPIN else Sfx.HIDE)
                        PixelButton("Add to folder", { foldering = true }, Modifier.fillMaxWidth(), Pixel.InkSoft)
                        PixelButton("Category: ${prefs.categoryOf(app).label}", { moving = true }, Modifier.fillMaxWidth(), Pixel.InkSoft, sound = Sfx.MOVE_PICKUP)
                        if (pinned) PixelButton("Icon on the home page", { choosingIcon = true }, Modifier.fillMaxWidth(), Pixel.InkSoft)
                        PixelButton("App info", { host.appInfo(app.key); onClose() }, Modifier.fillMaxWidth(), Pixel.InkSoft)
                        if (app.removable) PixelButton("Uninstall", { host.uninstall(app.key); onClose() }, Modifier.fillMaxWidth(), Pixel.Danger, sound = Sfx.UNINSTALL_WARN)
                    } else {
                        Text("Move to", style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft)
                        AppCategory.entries.forEach { c ->
                            PixelButton(c.label, { onPrefs(prefs.move(app, c)); onClose() }, Modifier.fillMaxWidth(), if (c == prefs.categoryOf(app)) Pixel.Blue else Pixel.InkSoft, compact = true, sound = Sfx.MOVE_DROP)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TownPicker(current: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    val none = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(indication = null, interactionSource = none, onClick = onClose)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(Spacing.L).background(Pixel.Panel, DialogueShape)
                .padding(Spacing.L).clickable(indication = null, interactionSource = none) {},
            verticalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            Text("Where is your town?", style = MaterialTheme.typography.titleSmall)
            Column(Modifier.height(420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text("Scenes", style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft)
                TownChoices(TownScenes.scenes, current, onPick)
                listOf("Dungeons" to TownScenes.dungeons, "Rescue Team dungeons" to TownScenes.rescueTeam, "Explorers of Sky dungeons" to TownScenes.explorers).forEach { (heading, list) ->
                    app.pokeplan.ui.ChoiceGroup(heading, list.size, initiallyOpen = heading == "Dungeons" || list.any { it.first == current }) { TownChoices(list, current, onPick) }
                }
            }
            Text("Press and hold the town any time to change it.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
}

@Composable
private fun TownChoices(choices: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
    choices.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            row.forEach { (name, label) ->
                Box(
                    Modifier.weight(1f).height(96.dp).clip(rowShape).then(if (name == current) Modifier.border(3.dp, Pixel.Blue, rowShape) else Modifier)
                        .clickable(role = Role.RadioButton, onClickLabel = "Set the town in $label") { onPick(name) },
                ) {
                    TownBackdrop(name, Modifier.fillMaxSize())
                    Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = Spacing.S, vertical = 2.dp))
                }
            }
            if (row.size == 1) Box(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun PokemonMenu(mon: OwnedPokemon, isBuddy: Boolean, pixel: Int, onOpen: () -> Unit, onBuddy: () -> Unit, onClose: () -> Unit) {
    val none = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(Modifier.fillMaxSize().background(Pixel.Scrim.copy(alpha = 0.4f)).clickable(indication = null, interactionSource = none, onClick = onClose), contentAlignment = Alignment.Center) {
        PmdBox(pixel, Modifier.padding(horizontal = Spacing.XXL).clickable(indication = null, interactionSource = none) {}) {
            Column(Modifier.widthIn(min = 200.dp)) {
                PmdMenuLine("${mon.nickname}  Lv. ${mon.level}", pixel, dim = true) {}
                PmdRule(pixel)
                PmdMenuLine("Open profile", pixel, onClick = onOpen)
                if (!isBuddy) PmdMenuLine("Make buddy", pixel, onClick = onBuddy)
                PmdMenuLine("Close", pixel, dim = true, onClick = onClose)
            }
        }
    }
}

@Composable
internal fun LauncherSettings(
    model: AppModel, prefs: LauncherPrefs, onPrefs: (LauncherPrefs) -> Unit, isDefault: Boolean, hiddenCount: Int, pixel: Int, host: LauncherHost,
    onTown: () -> Unit, onHidden: () -> Unit, onPlace: () -> Unit, onWidgets: () -> Unit, onClose: () -> Unit,
) {
    val settings = model.data.settings
    var dialog by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    fun <T> choose(title: String, options: List<ChoiceOption<T>>, current: T, pick: (T) -> Unit) {
        dialog = { PmdChoiceDialog(title, options, current, pixel, onPick = { pick(it); dialog = null }, onClose = { dialog = null }) }
    }
    fun confirm(title: String, note: String, action: String, run: () -> Unit) {
        dialog = { PmdConfirmDialog(title, note, action, pixel, onConfirm = { run(); dialog = null }, onClose = { dialog = null }) }
    }
    Box(Modifier.fillMaxSize().background(Pixel.Night).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.XL), verticalArrangement = Arrangement.spacedBy(Spacing.XL)) {
            PmdBox(pixel, Modifier.fillMaxWidth()) {
                Column {
                    PmdMenuLine("Back", pixel, onClick = onClose)
                    PmdRule(pixel)
                    PmdMenuLine("Launcher settings", pixel, dim = true, icon = "pa-sliders") {}
                }
            }
            @Composable
            fun Section(title: String, glyph: String, content: @Composable () -> Unit) {
                PmdBox(pixel, Modifier.fillMaxWidth()) {
                    Column {
                        PmdMenuLine(title, pixel, dim = true, icon = "pa-$glyph") {}
                        PmdRule(pixel)
                        content()
                    }
                }
            }
            Section("Town", "map") {
                PmdMenuLine("Scene: ${(TownScenes.all.firstOrNull { it.first == prefs.scene } ?: TownScenes.all.first()).second}", pixel, icon = "pa-layout", onClick = onTown)
                PmdChoiceLine("Pokémon in the town", "${prefs.townSize}", pixel, icon = "pa-heart") {
                    choose("Pokémon in the town", listOf(8, 16, 24).map { ChoiceOption(it, "$it Pokémon") }, prefs.townSize) { onPrefs(prefs.copy(townSize = it)) }
                }
                PmdToggleLine("Pokémon walk about", prefs.walking, pixel, icon = "pa-zap") { onPrefs(prefs.withWalking(it)) }
                if (prefs.walking) PmdVolume("How lively", "pa-sliders", prefs.activity * 10, pixel, min = 1, valueText = { TownBehavior.activityLabel(it) }, onChange = { v -> onPrefs(prefs.withActivity(v / 10)) }, onDone = {})
                PmdToggleLine("Pokémon chirp", prefs.chirps, pixel, icon = "pa-heart") { onPrefs(prefs.withChirps(it)) }
                PmdChoiceLine("Weather", prefs.weather.label, pixel, icon = "pa-cloud") {
                    choose("Weather over the town", Weather.entries.map { ChoiceOption(it, it.label, WEATHER_NOTES[it].orEmpty()) }, prefs.weather) { onPrefs(prefs.copy(weather = it)) }
                }
                if (TownScenes.isDungeon(prefs.scene)) PmdMenuLine("New floor layout", pixel, icon = "pa-reload") { model.effect(Sfx.STAIRS_DOWN); onPrefs(prefs.withNewLayout(model.store.today.toEpochDays().toLong())) }
            }
            Section("Home screen", "home") {
                PmdMenuLine("Widgets: ${prefs.widgets.size}", pixel, icon = "pa-grid-2x2-2", onClick = onWidgets)
                PmdToggleLine("Widget grid (drag, resize)", prefs.gridMode, pixel, icon = "pa-drag-and-drop") { onPrefs(prefs.withGridMode(it)) }
                PmdToggleLine("Swipe across between pages", prefs.pageSwipe, pixel, icon = "pa-sliders") { onPrefs(prefs.withPageSwipe(it)) }
            }
            Section("Gestures", "hand") {
                GestureTrigger.entries.forEach { trigger ->
                    PmdChoiceLine(trigger.label, prefs.action(trigger).label, pixel, icon = "pa-pointer") {
                        choose(trigger.label, trigger.actions.map { ChoiceOption(it, it.label) }, prefs.action(trigger)) { onPrefs(prefs.withGesture(trigger, it)) }
                    }
                }
            }
            Section("Clock and weather", "clock") {
                PmdToggleLine("Show seconds", prefs.showSeconds, pixel, icon = "pa-eye") { onPrefs(prefs.withShowSeconds(it)) }
                PmdMenuLine("Weather place: ${prefs.place?.name ?: "not set"}", pixel, icon = "pa-map-pin", onClick = onPlace)
                PmdChoiceLine("Temperature", if (prefs.fahrenheit) "Fahrenheit" else "Celsius", pixel, icon = "pa-thermometer") {
                    choose("Temperature", listOf(ChoiceOption(false, "Celsius"), ChoiceOption(true, "Fahrenheit")), prefs.fahrenheit) { onPrefs(prefs.withFahrenheit(it)) }
                }
            }
            Section("Battery", "battery") {
                PmdChoiceLine("Battery saver", prefs.batterySaver.label, pixel, icon = "pa-battery") {
                    choose("Battery saver", BatterySaver.entries.map { ChoiceOption(it, it.label, it.note) }, prefs.batterySaver) { onPrefs(prefs.copy(batterySaver = it)) }
                }
            }
            Section("Apps", "layout") {
                if (hiddenCount > 0) PmdMenuLine("Hidden apps: $hiddenCount", pixel, icon = "pa-eye-off", onClick = onHidden)
                PmdMenuLine("Clear pinned apps and custom icons", pixel, dim = prefs.pinned.isEmpty() && prefs.icons.isEmpty(), icon = "pa-trash") {
                    if (prefs.pinned.isNotEmpty() || prefs.icons.isNotEmpty()) confirm(
                        "Clear pinned apps and custom icons?", "${prefs.pinned.size} pinned and ${prefs.icons.size} custom icons are removed from Favorites. The apps stay installed.", "Clear",
                    ) { onPrefs(prefs.withoutPins()) }
                }
                PmdMenuLine(if (isDefault) "Home app settings" else "Make PokéLife your home app", pixel, icon = "pa-settings-cog") { host.openBaseSettings() }
            }
            Section("Reset", "reload") {
                PmdMenuLine("Reset launcher settings", pixel, icon = "pa-trash") {
                    confirm(
                        "Reset launcher settings?", "The town, widgets, pages, gestures, weather and battery saver go back to how they began. Pinned apps, hidden apps, folders and the weather place are kept.", "Reset",
                    ) { onPrefs(prefs.reset()) }
                }
            }
        }
        dialog?.invoke()
    }
}

private val WEATHER_NOTES = mapOf(
    Weather.LIVE to "Follows the forecast for your weather place.",
    Weather.GUARDIAN to "The weather of today's Guardian; a clear sky if it brings none.",
    Weather.CLEAR to "Always a clear sky.",
)

@Composable
private fun PmdVolume(
    label: String, icon: String, value: Int, pixel: Int, onChange: (Int) -> Unit, onDone: () -> Unit,
    min: Int = 0,
    valueText: (Int) -> String = { "$it of 10" },
) {
    val density = LocalDensity.current.density
    val pad = pmdRowPad(pixel, density)
    val level = (value / 10).coerceIn(0, 10)
    var width by remember { mutableStateOf(1f) }
    fun set(x: Float) {
        val block = (x / width * 10).toInt().coerceIn(0, 9)
        onChange(maxOf(min, if (block == 0 && level == 1) 0 else block + 1) * 10)
    }
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.S)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = pad), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((3 * pixel / density).dp)) {
                if (icon.startsWith("pa-")) app.pokeplan.ui.Glyph(icon.removePrefix("pa-"), Color.White, size = 24.dp) else
                app.pokeplan.ui.ItemIcon(icon, size = (9 * pixel / density).dp.coerceAtLeast(24.dp), description = null)
                PmdText(label, pixel)
            }
            PmdText(valueText(level), pixel)
        }
        Row(
            Modifier.fillMaxWidth().height(Spacing.Target).padding(horizontal = pad).onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(level) { detectTapGestures { set(it.x); onDone() } }
                .pointerInput(Unit) { detectHorizontalDragGestures(onDragEnd = onDone, onDragCancel = onDone) { change, _ -> change.consume(); set(change.position.x) } }
                .semantics {
                    contentDescription = "$label ${valueText(level)}"
                    progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(level.toFloat(), 0f..10f, 9)
                    setProgress { target -> onChange(target.toInt().coerceIn(min, 10) * 10); true }
                },
            horizontalArrangement = Arrangement.spacedBy((pixel / density).dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(10) { i -> Box(Modifier.weight(1f).height((5 * pixel / density).dp).background(if (i < level) Pixel.Red else Color.White.copy(alpha = 0.25f))) }
        }
    }
}
