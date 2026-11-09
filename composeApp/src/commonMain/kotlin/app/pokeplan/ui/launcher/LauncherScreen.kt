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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.backhandler.BackHandler
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

/** The battery's charge, 0 to 100, and whether it is charging. */
data class BatteryStatus(val percent: Int, val charging: Boolean)

/** What the launcher screens ask of the phone; Android fills it from `LauncherApps` and the system settings. */
interface LauncherHost {
    /** An app's own icon, drawn off the main thread and cached by the host; null when it has none. */
    suspend fun icon(key: String): ImageBitmap?
    fun open(key: String)
    fun appInfo(key: String)
    fun uninstall(key: String)
    /** Opens Android's own settings page for choosing the home app. */
    fun openHomeSettings()
    /** Opens a Pokémon's page in PokéLife. */
    fun openPokemon(id: String)
    /** The battery now, or null when the phone cannot say. */
    fun battery(): BatteryStatus? = null
    /** Whether the phone's own power saving is on. */
    fun powerSaving(): Boolean = false
    /** The forecast for [place]: a recent one kept on the device, else a fresh one from the network; null when there is neither. */
    suspend fun forecast(place: GeoPlace): Forecast? = null
    /** Places that match [query], for choosing where the forecast is for. */
    suspend fun searchPlaces(query: String): List<GeoPlace> = emptyList()
    /**
     * Shows another app's widget, by its Android widget id. On the widget grid [widthDp] and [heightDp] are the room its
     * cells really give it, and the widget is told exactly that; in a list ([heightDp] 0) it has the width it is given
     * and a height of its own times 1, 1.5 or 2 for [size] 0, 1 or 2. Only a phone can; elsewhere it shows nothing.
     */
    @Composable
    fun HostedWidget(id: Int, size: Int, widthDp: Int, heightDp: Int, modifier: Modifier) {}
    /** Asks the user to choose a widget from the installed apps; [onPicked] gets its id, or is not called if they cancel. */
    fun pickWidget(onPicked: (Int) -> Unit) {}
    /** Lets go of a widget's id when its slot is removed. */
    fun releaseWidget(id: Int) {}
    /** The name of a hosted widget, for the Widgets list. */
    fun widgetLabel(id: Int): String? = null
    /** Whether this device can host other apps' widgets at all (a phone can; the desktop and the web cannot, and show nothing). */
    val hostsWidgets: Boolean get() = false
    /**
     * What widget [id] asks of its host, or null when its provider is gone (the app was uninstalled, disabled or is being
     * updated): the slot then shows a note instead of an empty space and keeps its place until the user removes it.
     */
    fun widgetSpec(id: Int): HostedWidgetSpec? = null
    /** Opens PokéLife itself on [place] (Focus from the Focus button, Home from the buddy). */
    fun openApp(place: Place)
}

private val rowShape = DialogueShape

/** The page position (0 the town, 1 Today and on to [last], and -1 and on to [first] for pages left of Home) for a finger at [x]: past either end it stretches a little and no more, like a rubber band. */
private fun rubberPage(x: Float, last: Float = 1f, first: Float = 0f): Float = when {
    x < first -> first + (x - first) / (1f - (x - first) * 4f) * 0.5f
    x > last -> last + (x - last) / (1f + (x - last) * 4f) * 0.5f
    else -> x
}

/** The list's position for a finger at [pulled] (0 open, 1 shut): past the open end it stretches a little and no more, like a rubber band. */
private fun rubber(pulled: Float): Float = when {
    pulled < 0f -> pulled / (1f - pulled * 4f) * 0.5f
    pulled > 1f -> 1f
    else -> pulled
}

/** A launcher page over the town: it fades and glides in from the right and glides out the same way, instantly under Reduce motion. */
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

/**
 * PokéLife as the phone's home screen: the home page (the buddy, today's tasks, a Focus button, pinned apps) on a Pokémon
 * pixel wallpaper and, one swipe or tap up, every app A to Z over that wallpaper blurred and darkened. It shows and starts
 * what already pays and pays nothing itself.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LauncherScreen(model: AppModel, apps: List<LauncherApp>, prefs: LauncherPrefs, onPrefs: (LauncherPrefs) -> Unit, isDefault: Boolean, phone: LauncherHost, homePresses: Int = 0, active: Boolean = true) {
    val data = model.data
    // Every opening of an app is counted, for the suggestions; everything else goes straight to the phone.
    val prefsNow by androidx.compose.runtime.rememberUpdatedState(prefs)
    val onPrefsNow by androidx.compose.runtime.rememberUpdatedState(onPrefs)
    val host: LauncherHost = remember(phone) { object : LauncherHost by phone { override fun open(key: String) { onPrefsNow(prefsNow.recordLaunch(key)); phone.open(key) } } }
    val dark = when (data.settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // Battery saving: read the charge now and then; while it is on the town stands still, nothing blurs, and the clock ticks slowly.
    var charge by remember { mutableStateOf(phone.battery()) }
    var phoneSaving by remember { mutableStateOf(phone.powerSaving()) }
    LaunchedEffect(active) { while (active) { charge = phone.battery(); phoneSaving = phone.powerSaving(); delay(60_000) } }
    val saver = batterySaving(prefs.batterySaver, charge, phoneSaving)
    LaunchedEffect(saver) {
        while (true) {
            model.tick()
            delay(if (saver) 10_000 else 1000)
        }
    }
    var listOpen by remember { mutableStateOf(false) }
    // Three quick taps send every Pokémon to the main screen: each tap within 600 ms of the one before counts, and a third gathers them.
    var gather by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var taps by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val tapper = remember { TownTapper() }
    var lastTap by remember { mutableStateOf<kotlin.time.TimeMark?>(null) }
    var sheetKey by remember { mutableStateOf<String?>(null) }
    var pickingTown by remember { mutableStateOf(false) }
    var menuMon by remember { mutableStateOf<OwnedPokemon?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    /** Does what the user set a gesture to do. */
    fun runAction(action: GestureAction) {
        when (action) {
            GestureAction.NONE -> {}
            GestureAction.APPS -> listOpen = true
            GestureAction.GATHER -> gather++
            GestureAction.POKELIFE -> host.openApp(Place.HOME)
            GestureAction.GEAR -> host.openApp(Place.GEAR)
            GestureAction.SETTINGS -> settingsOpen = true
        }
    }
    fun countTap() {
        val job = prefs.action(GestureTrigger.TRIPLE_TAP)
        if (job == GestureAction.NONE) return
        taps = if (lastTap?.let { it.elapsedNow() < 600.milliseconds } == true) taps + 1 else 1
        lastTap = kotlin.time.TimeSource.Monotonic.markNow()
        if (taps >= 3) { runAction(job); taps = 0 }
    }
    var foldersOpen by remember { mutableStateOf(false) }
    var widgetsOpen by remember { mutableStateOf(false) }
    // Editing the widgets on the screen (hold one): drag, resize, remove; page swipes are off meanwhile.
    var editing by remember { mutableStateOf(false) }
    val todayState = remember { TodayState(model.store.today) }
    val mood = remember { BuddyMood() }
    // The phone's calendars around the month the calendar widget shows (six weeks, read off the main thread), again after the permission is answered, the chosen calendars change, or every few minutes.
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
    // The forecast for the chosen place, read from the phone (it keeps a recent one and refreshes it) every half hour while the launcher is in sight.
    var forecast by remember { mutableStateOf<Forecast?>(null) }
    LaunchedEffect(prefs.place, active, saver) {
        val place = prefs.place
        if (place == null) forecast = null else if (active) while (true) { host.forecast(place)?.let { forecast = it }; delay(if (saver) 120 * 60_000L else 30 * 60_000L) }
    }
    // The app list slides in from the right: 1 is off the screen, 0 is in place. A drag from the edge moves it with the finger; letting go settles it.
    val reveal = remember { androidx.compose.animation.core.Animatable(1f) }
    var settle by remember { mutableStateOf(0) }
    // How fast the finger was going when it let go (in widths per second, towards the open end negative): the list keeps gliding with it.
    var release by remember { mutableStateOf<Float?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(listOpen, settle) {
        val to = if (listOpen) 0f else 1f
        val velocity = release
        release = null
        when {
            data.settings.reduceMotion -> reveal.snapTo(to)
            // A flick keeps its speed and settles on a soft spring (a touch of give at the open end); a tap on a button glides on the house curve.
            velocity != null -> reveal.animateTo(to, androidx.compose.animation.core.spring(dampingRatio = 0.82f, stiffness = 260f), initialVelocity = velocity)
            else -> reveal.animateTo(to, androidx.compose.animation.core.tween(320, easing = app.pokeplan.ui.Ease.Smooth))
        }
    }
    var listHidden by remember { mutableStateOf(false) }
    // The Home button always comes back to the home page.
    LaunchedEffect(homePresses) {
        if (homePresses > 0) { listOpen = false; sheetKey = null; pickingTown = false; menuMon = null; settingsOpen = false; foldersOpen = false; folderName = null; forecastOpen = false; placeOpen = false; widgetsOpen = false; editing = false }
    }
    val sheetApp = sheetKey?.let { key -> apps.firstOrNull { it.key == key } }
    PixelTheme(dark) {
        CompositionLocalProvider(LocalReduceMotion provides (data.settings.reduceMotion || saver), LocalUiSound provides model::tap) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(Pixel.Paper)) {
                val pixel = townScale(maxWidth.value * LocalDensity.current.density)
                val page = remember { androidx.compose.animation.core.Animatable(0f) }
                // The pages: Home and Today, and on the grid up to three of the user's own, each left of Home or after Today. The position 0 is Home, -1 the page left of it.
                val order = if (prefs.gridMode) prefs.pageOrder else WidgetPage.entries.take(2)
                val pageCount = order.size
                val left = order.indexOf(WidgetPage.HOME)
                val right = pageCount - 1 - left
                val env = WidgetEnv(
                    model, apps, prefs, host, forecast, pixel, todayState, mood, saver, editing, onPrefs, onEdit = { editing = true }, onDone = { editing = false }, onAllApps = { listOpen = true }, onFolders = { foldersOpen = true }, onWidgets = { widgetsOpen = true; editing = false }, onGear = { host.openApp(Place.GEAR) },
                    onOpenFolder = { folderName = it; foldersOpen = true }, onForecast = { forecastOpen = true }, onHold = { sheetKey = it.key },
                    pages = pageCount,
                    goToPage = { i -> scope.launch { if (data.settings.reduceMotion) page.snapTo((i - left).toFloat()) else page.animateTo((i - left).toFloat(), androidx.compose.animation.core.tween(320, easing = app.pokeplan.ui.Ease.Smooth)) } },
                )
                // Two pages side by side: the town with its boxes, and Today (a calendar and the day's agenda and tasks). The town is two screens wide, so its Pokémon can wander over to Today (but only on the main screen can they be touched or meet). Swipe across, or press Home to come back.
                val widthPx0 = maxWidth.value * LocalDensity.current.density
                // Where widgets cover the town, so the Pokémon prefer to walk where they can be seen (on the grid; the old stacked layout has none).
                val topInset = with(LocalDensity.current) { androidx.compose.foundation.layout.WindowInsets.Companion.safeDrawing.getTop(this).toDp() }
                val avoidRects = if (!prefs.gridMode) emptyList() else {
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
                }
                LaunchedEffect(homePresses) { if (homePresses > 0) page.animateTo(0f, androidx.compose.animation.core.tween(320, easing = app.pokeplan.ui.Ease.Smooth)) }
                Box(
                    Modifier.fillMaxSize().pointerInput(prefs.gestures, prefs.gatherTap) {
                        // The widget pages are drawn over the town and swallow touches meant for the Pokémon below, so a tap that lands on one is handed to the town here.
                        val double = prefs.action(GestureTrigger.DOUBLE_TAP)
                        detectTapGestures(onDoubleTap = if (double == GestureAction.NONE) null else ({ at ->
                            if (abs(page.value) < 0.02f && !editing && !tapper.hit(Offset(at.x + (page.value + left) * widthPx0, at.y))) runAction(double)
                        }), onTap = { at -> if (abs(page.value) >= 0.02f || editing || !tapper.hit(Offset(at.x + (page.value + left) * widthPx0, at.y))) countTap() })
                    }.draggable(
                        enabled = prefs.pageSwipe && !editing,
                        orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                        state = androidx.compose.foundation.gestures.rememberDraggableState { dx -> scope.launch { page.snapTo(rubberPage(page.value - dx / widthPx0, right.toFloat(), -left.toFloat())) } },
                        onDragStopped = { velocity ->
                            val perSecond = -velocity / widthPx0
                            // A flick goes on to the next page in its direction; a slow let-go settles on the nearest.
                            val to = (if (abs(perSecond) > 1.2f) (if (perSecond > 0f) kotlin.math.ceil(page.value) else kotlin.math.floor(page.value)) else page.value.roundToInt().toFloat()).coerceIn(-left.toFloat(), right.toFloat())
                            scope.launch {
                                if (data.settings.reduceMotion) page.snapTo(to) else page.animateTo(to, androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 300f), initialVelocity = perSecond)
                            }
                        },
                    ),
                ) {
                    Town(
                        model, prefs.scene, active && prefs.walking && !saver, onMenu = { menuMon = it }, size = prefs.townSize, mood = mood, weather = if (prefs.weather == Weather.LIVE) forecast?.let { WeatherCodes.town(it.code) } ?: Weather.CLEAR else prefs.weather, screens = pageCount, home = left, avoid = avoidRects, tapper = tapper, interactive = abs(page.value) < 0.02f, gather = gather,
                        modifier = Modifier.layout { measurable, constraints ->
                            // As many screens wide as there are pages, placed from the left edge and slid by the page (a required size would be centred instead).
                            val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(constraints.maxWidth * pageCount, constraints.maxHeight))
                            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place((-(page.value + left) * constraints.maxWidth).roundToInt(), 0) }
                        },
                    )
                    Box(Modifier.fillMaxSize().graphicsLayer { translationX = -page.value * widthPx0 }) {
                        if (prefs.gridMode) GridHomePage(env, when {
                            !data.onboarded -> "Open PokéLife to meet your first Pokémon" to { host.openApp(Place.HOME) }
                            !isDefault -> "Make PokéLife your home app" to host::openHomeSettings
                            else -> null
                        }) else HomePage(env, isDefault)
                    }
                    // The other pages, each a screen from the one next to it (left of Home or after it), shown while any of it is on screen.
                    for (k in -left..right) {
                        if (k != 0 && abs(page.value - k) < 1f) androidx.compose.runtime.key(k) { Box(Modifier.fillMaxSize().graphicsLayer { translationX = (k - page.value) * widthPx0 }) { if (prefs.gridMode) GridSidePage(env, order[left + k]) else TodayPage(env) } }
                    }
                }
                // Which page: two small blocks at the top, the current one lit, shown only while the page is moving (and a moment after).
                val moving = page.isRunning || abs(page.value - page.value.roundToInt()) > 0.001f
                var lingering by remember { mutableStateOf(false) }
                LaunchedEffect(moving) { if (moving) lingering = true else { delay(900); lingering = false } }
                val indicator by androidx.compose.animation.core.animateFloatAsState(if (moving || lingering) 1f else 0f, androidx.compose.animation.core.tween(220, easing = app.pokeplan.ui.Ease.Smooth))
                if (indicator > 0.01f) Row(Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = Spacing.S).graphicsLayer { alpha = indicator }, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    repeat(pageCount) { i -> Box(Modifier.size(width = 16.dp, height = 4.dp).background(Color.White.copy(alpha = if ((page.value + left + 0.5f).toInt() == i) 0.9f else 0.3f))) }
                }
                // While editing the grid, its bar floats at the foot of the screen over whichever page is showing (nothing moves to make room for it).
                if (prefs.gridMode && editing && !widgetsOpen) Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.BottomCenter) {
                    GridEditBar(env, order[(page.value.roundToInt() + left).coerceIn(0, pageCount - 1)])
                }
                // Home is the one screen that must never close: back only steps out of the sheet or the list.
                BackHandler(enabled = true) { if (menuMon != null) menuMon = null else if (pickingTown) pickingTown = false else if (placeOpen) placeOpen = false else if (widgetsOpen) widgetsOpen = false else if (editing) editing = false else if (forecastOpen) forecastOpen = false else if (folderName != null) folderName = null else if (foldersOpen) foldersOpen = false else if (settingsOpen) settingsOpen = false else if (sheetApp != null) sheetKey = null else if (listOpen) listOpen = false else if (abs(page.value) > 0.5f) scope.launch { page.animateTo(0f, androidx.compose.animation.core.tween(320, easing = app.pokeplan.ui.Ease.Smooth)) } }
                // Drag in from the right edge: the app list comes into focus with the finger, and settles open or shut when it lets go.
                val widthPx = maxWidth.value * LocalDensity.current.density
                // Where the finger would have the list (past either end it keeps going, but the list stretches only a little: rubber).
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
                            // Fast enough opens or shuts it whichever side of the middle it is on; slowly, the nearer end wins.
                            listOpen = if (abs(perSecond) > 1.2f) perSecond < 0f else reveal.value < 0.5f
                            release = perSecond
                            settle++
                        },
                    ),
                )
                // Set to do something else, the same edge strip runs that job when the finger has gone a good way in.
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
                // The list comes in from the right and into focus: a short glide (56 dp, not the whole width) while it fades in and its blur clears, following the finger or the spring.
                val shown = (1f - reveal.value).coerceIn(0f, 1f)
                if (reveal.value < 1f) Box(
                    Modifier.fillMaxSize().graphicsLayer { alpha = shown; translationX = (1f - shown) * 56.dp.toPx() }.then(if (shown < 0.97f && !data.settings.reduceMotion) Modifier.blur(((1f - shown) * 10f).dp) else Modifier),
                ) {
                    AppListPage(apps, prefs, host, onClose = { listOpen = false }, onHold = { sheetKey = it.key }, onTown = { settingsOpen = true }, startHidden = listHidden, pixel = pixel)
                }
                menuMon?.let { mon -> PokemonMenu(mon, mon.id == model.data.player.buddy?.id, pixel, onOpen = { host.openPokemon(mon.id); menuMon = null }, onBuddy = { model.store.setBuddy(mon.id); menuMon = null }, onClose = { menuMon = null }) }
                // Each page over the town fades and glides in from the right and out the same way (at once under Reduce motion).
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

/**
 * PokéLife as the phone's home screen: the widgets placed at the top of the home page over the town (the date and time and
 * the companion to start with), and at the bottom, in reach of a thumb, the widgets placed there (the shortcuts, often used
 * apps, favorites and the folder and app-list buttons to start with). The user chooses and orders them in Launcher settings → Widgets.
 */
@Composable
private fun HomePage(env: WidgetEnv, isDefault: Boolean) {
    val data = env.model.data
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(vertical = Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        if (env.editing) EditToolbar(env, Modifier.padding(horizontal = Spacing.Page))
        env.prefs.widgetsIn(WidgetArea.HOME_TOP).forEach { slot -> androidx.compose.runtime.key(slot.kind, slot.arg) { LauncherWidget(slot, env) } }
        // The bottom widgets sit at the foot of the screen, and scroll when they are more than fit.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                val note = when {
                    !data.onboarded -> "Open PokéLife to meet your first Pokémon" to { env.host.openApp(Place.HOME) }
                    !isDefault -> "Make PokéLife your home app" to env.host::openHomeSettings
                    else -> null
                }
                if (note != null) PmdBox(env.pixel, Modifier.fillMaxWidth().padding(horizontal = Spacing.Page), inset = 1) { PmdMenuLine(note.first, env.pixel, onClick = note.second) }
                env.prefs.widgetsIn(WidgetArea.HOME_BOTTOM).forEach { slot -> androidx.compose.runtime.key(slot.kind, slot.arg) { LauncherWidget(slot, env) } }
            }
        }
    }
}

/** A pinned app on the home page: its logo (or the game item chosen for it) over its name; tap opens it, press and hold manages it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PinnedApp(app: LauncherApp, item: String?, host: LauncherHost, pixel: Int, modifier: Modifier, onHold: () -> Unit, showLabel: Boolean = true) {
    val ui = LocalUiSound.current
    val density = LocalDensity.current.density
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        modifier.pressScale(source).defaultMinSize(minHeight = if (showLabel) 72.dp else Spacing.Target).focusRing(rowShape, Color.White).combinedClickable(interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Button, onLongClick = { ui(Sfx.LONG_PRESS); onHold() }, onClick = { ui(Sfx.CONFIRM); host.open(app.key) })
            .semantics(mergeDescendants = true) {}.padding(vertical = Spacing.S),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        if (item != null) ItemIcon(item, size = 40.dp, description = null) else AppIcon(host, app.key, 40.dp)
        if (showLabel) Box(Modifier.padding(top = Spacing.M).padding(horizontal = (1 * pixel / density).dp)) { PmdText(app.label, pixel) }
    }
}

/**
 * An app's own icon in a pixel frame: the picture is square (the host gives it without the phone's round or squircle mask),
 * cut to a square with stair-stepped corners and edged by a one-pixel line, like the game's boxes. A quiet square of the same
 * shape stands in while it loads, so scrolling never waits on it.
 */
@Composable
internal fun AppIcon(host: LauncherHost, key: String, size: androidx.compose.ui.unit.Dp) {
    val icon by produceState<ImageBitmap?>(null, key) { value = host.icon(key) }
    val shown = icon
    // The border is the icon's own edge colour, so it melts into the picture and only the pixel corners show. Worked out once per picture (scanning its pixels again on every scroll-in made the list stutter).
    val edge = remember(shown) { shown?.let { edgeColors.getOrPut(key) { edgeColor(it) } } ?: Color.White.copy(alpha = 0.9f) }
    Box(
        Modifier.size(size).drawWithContent {
            val p = 2.dp.toPx()
            val w = this.size.width
            val h = this.size.height
            // The whole square, stair-stepped corners and all, is filled with the icon's own edge colour and the picture drawn over it, so nothing shows through a rounded or see-through corner and no line is left between the picture and its frame.
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

/**
 * The colour an icon's edge mostly is: the most common shade among the outermost ring of its pixels (shades within 1/16 of
 * each other count as one, and see-through pixels are ignored), averaged; a quiet grey when too little of the edge is
 * solid (an icon that is a shape on nothing).
 */
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
    /** A place in PokéLife itself that matches the search (see [LauncherSearch]). */
    data class Destination(val to: LauncherDestination) : ListRow
}

/** Every app A to Z over the wallpaper, blurred and darkened (the blur shows from Android 12; the dark veil always). */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun AppListPage(apps: List<LauncherApp>, prefs: LauncherPrefs, host: LauncherHost, onClose: () -> Unit, onHold: (LauncherApp) -> Unit, onTown: () -> Unit = {}, startHidden: Boolean = false, pixel: Int = 2) {
    val ui = LocalUiSound.current
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<AppCategory?>(null) }
    var showHidden by remember { mutableStateOf(startHidden) }
    val categories = remember(apps, prefs) { LauncherList.categories(apps, prefs) }
    // A category that emptied (its last app moved or hidden) falls back to all apps.
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
                    if (query.isEmpty()) Text("Search apps and PokéLife", style = MaterialTheme.typography.bodyLarge, color = soft)
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

/** The strip of letters on the right edge: touch or drag along it to jump to a letter. */
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

/** The long-press sheet: pin, hide, move to a category, app info, uninstall. */
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

/** Long-press the town: pick the scene it is set in, shown as small pictures. */
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

/** Long-press a Pokémon in the town: its name and level, then what can be done with it, as a dialogue box. */
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

/**
 * The launcher's settings as a screen of their own over the town (not a pop-up): a header with Back, then the town, the
 * walking, the status box, the edge drag, the sound volume, the hidden apps, the pins and the home-app setting.
 */
@Composable
internal fun LauncherSettings(
    model: AppModel, prefs: LauncherPrefs, onPrefs: (LauncherPrefs) -> Unit, isDefault: Boolean, hiddenCount: Int, pixel: Int, host: LauncherHost,
    onTown: () -> Unit, onHidden: () -> Unit, onPlace: () -> Unit, onWidgets: () -> Unit, onClose: () -> Unit,
) {
    fun onOff(on: Boolean) = if (on) "On" else "Off"
    val settings = model.data.settings
    val effects = if (settings.sound) settings.effectsVolume else 0
    val cries = if (!settings.sound) 0 else if (settings.criesVolume < 0) settings.effectsVolume else settings.criesVolume
    Box(Modifier.fillMaxSize().background(Pixel.Night).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.XL), verticalArrangement = Arrangement.spacedBy(Spacing.XL)) {
            PmdBox(pixel, Modifier.fillMaxWidth()) {
                Column {
                    PmdMenuLine("Back", pixel, onClick = onClose)
                    PmdRule(pixel)
                    PmdMenuLine("Launcher settings", pixel, dim = true, icon = "pa-sliders") {}
                }
            }
            // The settings come in sections, each under its own heading with a glyph, so one is found by what it is about.
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
                PmdMenuLine("Pokémon in the town: ${prefs.townSize}", pixel, icon = "pa-heart") { onPrefs(prefs.nextTownSize()) }
                PmdMenuLine("Pokémon walk about: ${onOff(prefs.walking)}", pixel, icon = "pa-zap") { onPrefs(prefs.withWalking(!prefs.walking)) }
                PmdMenuLine("Weather: ${prefs.weather.label}", pixel, icon = "pa-cloud") { onPrefs(prefs.nextWeather()) }
            }
            Section("Home screen", "home") {
                PmdMenuLine("Widgets: ${prefs.widgets.size}", pixel, icon = "pa-grid-2x2-2", onClick = onWidgets)
                PmdMenuLine("Widget grid (drag, resize): ${onOff(prefs.gridMode)}", pixel, icon = "pa-drag-and-drop") { onPrefs(prefs.withGridMode(!prefs.gridMode)) }
                PmdMenuLine("Swipe across between pages: ${onOff(prefs.pageSwipe)}", pixel, icon = "pa-sliders") { onPrefs(prefs.withPageSwipe(!prefs.pageSwipe)) }
            }
            // A gesture and its job: tap one to give it the next job on its list. The edge drag follows the finger and opens the app list; the others run the job the moment they are made.
            Section("Gestures", "hand") {
                GestureTrigger.entries.forEach { trigger ->
                    PmdMenuLine("${trigger.label}: ${prefs.action(trigger).label}", pixel, icon = "pa-pointer") {
                        val jobs = trigger.actions
                        onPrefs(prefs.withGesture(trigger, jobs[(jobs.indexOf(prefs.action(trigger)) + 1) % jobs.size]))
                    }
                }
                PmdMenuLine("Tap a gesture to give it the next job.", pixel, dim = true) {}
            }
            Section("Time and date", "clock") {
                PmdMenuLine("Date: ${prefs.dateStyle.label}", pixel, icon = "pa-calendar") { onPrefs(prefs.nextDateStyle()) }
                PmdMenuLine("Clock: ${if (prefs.hour12) "12 hour" else "24 hour"}", pixel, icon = "pa-clock") { onPrefs(prefs.withHour12(!prefs.hour12)) }
                PmdMenuLine("Seconds: ${onOff(prefs.showSeconds)}", pixel, icon = "pa-eye") { onPrefs(prefs.withShowSeconds(!prefs.showSeconds)) }
                PmdMenuLine("Calendar weeks start on ${if (prefs.sundayFirst) "Sunday" else "Monday"}", pixel, icon = "pa-calendar") { onPrefs(prefs.withSundayFirst(!prefs.sundayFirst)) }
                PmdMenuLine("Weather place: ${prefs.place?.name ?: "not set"}", pixel, icon = "pa-map-pin", onClick = onPlace)
                PmdMenuLine("Temperature: ${if (prefs.fahrenheit) "Fahrenheit" else "Celsius"}", pixel, icon = "pa-thermometer") { onPrefs(prefs.withFahrenheit(!prefs.fahrenheit)) }
            }
            Section("Battery", "battery") {
                PmdMenuLine("Battery saver: ${prefs.batterySaver.label}", pixel, icon = "pa-battery") { onPrefs(prefs.nextBatterySaver()) }
            }
            Section("Sound", "volume-2") {
                PmdVolume("Sound effects", "pa-volume-2", effects, pixel, onChange = { v -> model.store.changeSettings { s -> s.copy(effectsVolume = v, sound = true) } }, onDone = { model.effect(Sfx.CONFIRM) })
                PmdRule(pixel)
                PmdVolume("Pokémon cries", "pa-music", cries, pixel, onChange = { v -> model.store.changeSettings { s -> s.copy(criesVolume = v, sound = true) } }, onDone = { model.cry(model.data.player.buddy?.speciesId ?: 25) })
            }
            Section("Apps", "layout") {
                if (hiddenCount > 0) PmdMenuLine("Hidden apps: $hiddenCount", pixel, icon = "pa-eye-off", onClick = onHidden)
                PmdMenuLine("Forget pins and icons", pixel, dim = prefs.pinned.isEmpty() && prefs.icons.isEmpty(), icon = "pa-trash") { onPrefs(prefs.withoutPins()) }
                PmdMenuLine(if (isDefault) "Home app settings" else "Make PokéLife your home app", pixel, icon = "pa-settings-cog") { host.openHomeSettings() }
            }
        }
    }
}

/**
 * A volume control in the dialogue style: ten blocks you tap or drag across, 0 to 10 (the font has no % sign, so it says
 * "7 of 10"). [onDone] fires when the finger lets go, so the new level can be heard. Its text and bar start and end where a
 * menu line's text does, and the bar's row is a full [Spacing.Target] tall to hit.
 */
@Composable
private fun PmdVolume(label: String, icon: String, value: Int, pixel: Int, onChange: (Int) -> Unit, onDone: () -> Unit) {
    val density = LocalDensity.current.density
    val pad = pmdRowPad(pixel, density)
    val level = (value / 10).coerceIn(0, 10)
    var width by remember { mutableStateOf(1f) }
    // A block at x sets that level; the first block again turns it to 0, so the sound can be switched off.
    fun set(x: Float) {
        val block = (x / width * 10).toInt().coerceIn(0, 9)
        onChange((if (block == 0 && level == 1) 0 else block + 1) * 10)
    }
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.S)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = pad), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((3 * pixel / density).dp)) {
                if (icon.startsWith("pa-")) app.pokeplan.ui.Glyph(icon.removePrefix("pa-"), Color.White, size = 24.dp) else
                app.pokeplan.ui.ItemIcon(icon, size = (9 * pixel / density).dp.coerceAtLeast(24.dp), description = null)
                PmdText(label, pixel)
            }
            PmdText("$level of 10", pixel)
        }
        Row(
            Modifier.fillMaxWidth().height(Spacing.Target).padding(horizontal = pad).onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(level) { detectTapGestures { set(it.x); onDone() } }
                .pointerInput(Unit) { detectHorizontalDragGestures(onDragEnd = onDone, onDragCancel = onDone) { change, _ -> change.consume(); set(change.position.x) } }
                .semantics {
                    contentDescription = "$label $level of 10"
                    progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(level.toFloat(), 0f..10f, 9)
                    setProgress { target -> onChange(target.toInt().coerceIn(0, 10) * 10); true }
                },
            horizontalArrangement = Arrangement.spacedBy((pixel / density).dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(10) { i -> Box(Modifier.weight(1f).height((5 * pixel / density).dp).background(if (i < level) Pixel.Red else Color.White.copy(alpha = 0.25f))) }
        }
    }
}
