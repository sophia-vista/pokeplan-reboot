package app.pokeplan.ui.launcher

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
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import app.pokeplan.app.ThemeMode
import androidx.compose.foundation.layout.widthIn
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.entrance
import app.pokeplan.ui.LocalUiSound
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.ItemIcon
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
    /** The forecast for [place]: a recent one kept on the device, else a fresh one from the network; null when there is neither. */
    suspend fun forecast(place: GeoPlace): Forecast? = null
    /** Places that match [query], for choosing where the forecast is for. */
    suspend fun searchPlaces(query: String): List<GeoPlace> = emptyList()
    /** Opens PokéLife itself on [place] (Focus from the Focus button, Home from the buddy). */
    fun openApp(place: Place)
}

private val rowShape = RoundedCornerShape(14.dp)

/** The page position (0 the town, 1 Today) for a finger at [x]: past either end it stretches a little and no more, like a rubber band. */
private fun rubberPage(x: Float): Float = when {
    x < 0f -> x / (1f - x * 4f) * 0.5f
    x > 1f -> 1f + (x - 1f) / (1f + (x - 1f) * 4f) * 0.5f
    else -> x
}

/** The list's position for a finger at [pulled] (0 open, 1 shut): past the open end it stretches a little and no more, like a rubber band. */
private fun rubber(pulled: Float): Float = when {
    pulled < 0f -> pulled / (1f - pulled * 4f) * 0.5f
    pulled > 1f -> 1f
    else -> pulled
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
    LaunchedEffect(Unit) {
        while (true) {
            model.tick()
            delay(1000)
        }
    }
    var listOpen by remember { mutableStateOf(false) }
    // Three quick taps send every Pokémon to the main screen: each tap within 600 ms of the one before counts, and a third gathers them.
    var gather by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var taps by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var lastTap by remember { mutableStateOf<kotlin.time.TimeMark?>(null) }
    fun countTap() {
        if (!prefs.gatherTap) return
        taps = if (lastTap?.let { it.elapsedNow() < 600.milliseconds } == true) taps + 1 else 1
        lastTap = kotlin.time.TimeSource.Monotonic.markNow()
        if (taps >= 3) { gather++; taps = 0 }
    }
    var sheetKey by remember { mutableStateOf<String?>(null) }
    var pickingTown by remember { mutableStateOf(false) }
    var menuMon by remember { mutableStateOf<OwnedPokemon?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var foldersOpen by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf<String?>(null) }
    var forecastOpen by remember { mutableStateOf(false) }
    var placeOpen by remember { mutableStateOf(false) }
    // The forecast for the chosen place, read from the phone (it keeps a recent one and refreshes it) every half hour while the launcher is in sight.
    var forecast by remember { mutableStateOf<Forecast?>(null) }
    LaunchedEffect(prefs.place, active) {
        val place = prefs.place
        if (place == null) forecast = null else if (active) while (true) { host.forecast(place)?.let { forecast = it }; delay(30 * 60_000L) }
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
        if (homePresses > 0) { listOpen = false; sheetKey = null; pickingTown = false; menuMon = null; settingsOpen = false; foldersOpen = false; folderName = null; forecastOpen = false; placeOpen = false }
    }
    val sheetApp = sheetKey?.let { key -> apps.firstOrNull { it.key == key } }
    PixelTheme(dark) {
        CompositionLocalProvider(LocalReduceMotion provides data.settings.reduceMotion, LocalUiSound provides model::tap) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(Pixel.Paper)) {
                val pixel = townScale(maxWidth.value * LocalDensity.current.density)
                // Two pages side by side: the town with its boxes, and Today (a calendar and the day's agenda and tasks). The town is two screens wide, so its Pokémon can wander over to Today (but only on the main screen can they be touched or meet). Swipe across, or press Home to come back.
                val widthPx0 = maxWidth.value * LocalDensity.current.density
                val page = remember { androidx.compose.animation.core.Animatable(0f) }
                LaunchedEffect(homePresses) { if (homePresses > 0) page.animateTo(0f, androidx.compose.animation.core.tween(320, easing = app.pokeplan.ui.Ease.Smooth)) }
                Box(
                    Modifier.fillMaxSize().pointerInput(prefs.gatherTap) { detectTapGestures(onTap = { countTap() }) }.draggable(
                        enabled = prefs.pageSwipe,
                        orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                        state = androidx.compose.foundation.gestures.rememberDraggableState { dx -> scope.launch { page.snapTo(rubberPage(page.value - dx / widthPx0)) } },
                        onDragStopped = { velocity ->
                            val perSecond = -velocity / widthPx0
                            val to = if (abs(perSecond) > 1.2f) (if (perSecond > 0f) 1f else 0f) else if (page.value > 0.5f) 1f else 0f
                            scope.launch {
                                if (data.settings.reduceMotion) page.snapTo(to) else page.animateTo(to, androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 300f), initialVelocity = perSecond)
                            }
                        },
                    ),
                ) {
                    Town(
                        model, prefs.scene, active && prefs.walking, onMenu = { menuMon = it }, size = prefs.townSize, weather = if (prefs.weather == Weather.LIVE) forecast?.let { WeatherCodes.town(it.code) } ?: Weather.CLEAR else prefs.weather, screens = 2, interactive = page.value < 0.02f, gather = gather,
                        modifier = Modifier.layout { measurable, constraints ->
                            // Two screens wide, placed from the left edge and slid by the page (a required size would be centred instead).
                            val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(constraints.maxWidth * 2, constraints.maxHeight))
                            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place((-page.value * constraints.maxWidth).roundToInt(), 0) }
                        }
                            .then(if (page.value < 0.02f) Modifier.pointerInput(prefs.gatherTap) { detectTapGestures(onTap = { countTap() }) } else Modifier),
                    )
                    Box(Modifier.fillMaxSize().graphicsLayer { translationX = -page.value * widthPx0 }) {
                        HomePage(model, apps, prefs, isDefault, host, forecast, pixel, onAllApps = { listOpen = true }, onFolders = { foldersOpen = true }, onForecast = { forecastOpen = true }, onHold = { sheetKey = it.key })
                    }
                    if (page.value > 0.001f) Box(Modifier.fillMaxSize().graphicsLayer { translationX = (1f - page.value) * widthPx0 }) { TodayPage(model, pixel) }
                }
                // Which page: two small blocks at the top, the current one lit, shown only while the page is moving (and a moment after).
                val moving = page.isRunning || (page.value > 0.001f && page.value < 0.999f)
                var lingering by remember { mutableStateOf(false) }
                LaunchedEffect(moving) { if (moving) lingering = true else { delay(900); lingering = false } }
                val indicator by androidx.compose.animation.core.animateFloatAsState(if (moving || lingering) 1f else 0f, androidx.compose.animation.core.tween(220, easing = app.pokeplan.ui.Ease.Smooth))
                if (indicator > 0.01f) Row(Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = Spacing.S).graphicsLayer { alpha = indicator }, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    repeat(2) { i -> Box(Modifier.size(width = 16.dp, height = 4.dp).background(Color.White.copy(alpha = if ((page.value + 0.5f).toInt() == i) 0.9f else 0.3f))) }
                }
                // Home is the one screen that must never close: back only steps out of the sheet or the list.
                BackHandler(enabled = true) { if (menuMon != null) menuMon = null else if (pickingTown) pickingTown = false else if (placeOpen) placeOpen = false else if (forecastOpen) forecastOpen = false else if (folderName != null) folderName = null else if (foldersOpen) foldersOpen = false else if (settingsOpen) settingsOpen = false else if (sheetApp != null) sheetKey = null else if (listOpen) listOpen = false else if (page.value > 0.5f) scope.launch { page.animateTo(0f, androidx.compose.animation.core.tween(320, easing = app.pokeplan.ui.Ease.Smooth)) } }
                // Drag in from the right edge: the app list comes into focus with the finger, and settles open or shut when it lets go.
                val widthPx = maxWidth.value * LocalDensity.current.density
                // Where the finger would have the list (past either end it keeps going, but the list stretches only a little: rubber).
                var pulled by remember { mutableStateOf(1f) }
                if (prefs.edgeDrag && !listOpen) Box(
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
                // The list comes in from the right and into focus: a short glide (56 dp, not the whole width) while it fades in and its blur clears, following the finger or the spring.
                val shown = (1f - reveal.value).coerceIn(0f, 1f)
                if (reveal.value < 1f) Box(
                    Modifier.fillMaxSize().graphicsLayer { alpha = shown; translationX = (1f - shown) * 56.dp.toPx() }.then(if (shown < 0.97f && !data.settings.reduceMotion) Modifier.blur(((1f - shown) * 10f).dp) else Modifier),
                ) {
                    AppListPage(apps, prefs, host, onClose = { listOpen = false }, onHold = { sheetKey = it.key }, onTown = { settingsOpen = true }, startHidden = listHidden, pixel = pixel)
                }
                menuMon?.let { mon -> PokemonMenu(mon, mon.id == model.data.player.buddy?.id, pixel, onOpen = { host.openPokemon(mon.id); menuMon = null }, onBuddy = { model.store.setBuddy(mon.id); menuMon = null }, onClose = { menuMon = null }) }
                if (settingsOpen) LauncherSettings(
                    model, prefs, onPrefs, isDefault, apps.count { it.key in prefs.hidden }, pixel, host,
                    onTown = { settingsOpen = false; listOpen = false; pickingTown = true },
                    onHidden = { settingsOpen = false; listHidden = true; listOpen = true },
                    onPlace = { placeOpen = true },
                    onClose = { settingsOpen = false },
                )
                if (foldersOpen) FoldersPage(apps, prefs, onPrefs, host, folderName, { folderName = it }, onClose = { foldersOpen = false; folderName = null }, pixel = pixel)
                if (forecastOpen) ForecastPage(forecast, prefs, pixel, onPlace = { placeOpen = true }, onClose = { forecastOpen = false })
                if (placeOpen) PlacePage(prefs, onPrefs, host, pixel, onClose = { placeOpen = false })
                if (pickingTown) TownPicker(prefs.scene, onPick = { onPrefs(prefs.withScene(it)); pickingTown = false }, onClose = { pickingTown = false })
                if (sheetApp != null) AppSheet(sheetApp, prefs, onPrefs, onClose = { sheetKey = null }, host = host)
            }
        }
    }
}

/**
 * PokéLife as the phone's home screen: over the town, at the top a box with the date, the time and the battery and the
 * status box (the buddy, and the weather when a place is set, which opens the forecast), and at the bottom, in reach of a
 * thumb, two dialogue boxes: the app's three places (PokéLife, Pokémon, Me) with their icons, then the apps you open
 * most, your pinned apps, and Folders and All apps.
 */
@Composable
private fun HomePage(
    model: AppModel, apps: List<LauncherApp>, prefs: LauncherPrefs, isDefault: Boolean, host: LauncherHost, forecast: Forecast?, pixel: Int,
    onAllApps: () -> Unit, onFolders: () -> Unit, onForecast: () -> Unit, onHold: (LauncherApp) -> Unit,
) {
    val data = model.data
    val pinned = LauncherList.pinned(apps, prefs)
    val suggested = prefs.suggested(apps)
    val running = data.focus != null
    val buddy = data.player.buddy
    val density = LocalDensity.current.density
    val gap = Modifier.padding(horizontal = (6 * pixel / density).dp)
    var battery by remember { mutableStateOf(host.battery()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); battery = host.battery() } }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = Spacing.Page, vertical = Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        if (prefs.showClock) PmdBox(pixel, Modifier.fillMaxWidth()) {
            // The seconds tick in the model, so reading it keeps the minute right.
            model.now
            val now = model.store.now
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PmdText("${dayName(now.dayOfWeek).take(3)} ${now.dayOfMonth} ${monthName(now.month).take(3)}   ${two(now.hour)}:${two(now.minute)}", pixel, gap)
                Box(Modifier.weight(1f))
                battery?.let { PmdText((if (it.charging) "Charging " else "Battery ") + it.percent, pixel, gap, alpha = 0.75f) }
            }
        }
        if (data.onboarded && buddy != null && prefs.showStatus) PmdBox(pixel, Modifier.fillMaxWidth(), inset = 1) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PmdText("${buddy.nickname}  Lv. ${buddy.level}", pixel, gap.clickable(role = Role.Button, onClickLabel = "Open PokéLife") { host.openApp(Place.HOME) })
                Box(Modifier.weight(1f))
                if (forecast != null) PmdText("${degrees(forecast.temperature, prefs.fahrenheit)}  ${WeatherCodes.label(forecast.code)}", pixel, gap.clickable(role = Role.Button, onClickLabel = "Open the forecast") { onForecast() }, alpha = 0.9f)
                else PmdText(if (running) "Focusing" else "${data.player.pokemon.size} Pokémon", pixel, gap, alpha = 0.75f)
            }
        }
        Box(Modifier.weight(1f))
        val note = when {
            !data.onboarded -> "Open PokéLife to meet your first Pokémon" to { host.openApp(Place.HOME) }
            !isDefault -> "Make PokéLife your home app" to host::openHomeSettings
            else -> null
        }
        if (note != null) PmdBox(pixel, Modifier.fillMaxWidth(), inset = 1) { PmdMenuLine(note.first, pixel, onClick = note.second) }
        if (data.onboarded) PmdBox(pixel, Modifier.fillMaxWidth(), inset = 1) {
            // The three places of the app, with the icons of its tabs.
            Row(Modifier.fillMaxWidth()) {
                PmdNavItem("PokéLife", Tab.HOME.icon, pixel, Modifier.weight(1f)) { host.openApp(if (running) Place.FOCUS else Place.HOME) }
                PmdNavItem("Pokémon", Tab.COLLECTION.icon, pixel, Modifier.weight(1f)) { host.openApp(Place.COLLECTION) }
                PmdNavItem("Me", Tab.ME.icon, pixel, Modifier.weight(1f)) { host.openApp(Place.ME) }
            }
        }
        PmdBox(pixel, Modifier.fillMaxWidth(), inset = 1) {
            Column {
                if (suggested.isNotEmpty()) {
                    PmdText("Often used", pixel, Modifier.padding(horizontal = pmdRowPad(pixel, density), vertical = Spacing.S), alpha = 0.7f)
                    Row(Modifier.fillMaxWidth()) {
                        suggested.forEach { app -> PinnedApp(app, prefs.icons[app.key], host, pixel, Modifier.weight(1f), onHold = { onHold(app) }) }
                        repeat(4 - suggested.size) { Box(Modifier.weight(1f)) }
                    }
                    PmdRule(pixel)
                }
                if (pinned.isEmpty() && suggested.isEmpty()) PmdMenuLine("Press and hold an app in All apps to pin it", pixel, dim = true) { onAllApps() }
                else if (pinned.isNotEmpty()) Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    pinned.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { app -> PinnedApp(app, prefs.icons[app.key], host, pixel, Modifier.weight(1f), onHold = { onHold(app) }) }
                            repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
                PmdRule(pixel)
                Row(Modifier.fillMaxWidth()) {
                    PmdMenuLine(if (prefs.folders.isEmpty()) "Folders" else "Folders ${prefs.folders.size}", pixel, Modifier.weight(1f), center = true, onClick = onFolders)
                    PmdMenuLine("All apps", pixel, Modifier.weight(1f), center = true, onClick = onAllApps)
                }
            }
        }
    }
}

/** A pinned app on the home page: its logo (or the game item chosen for it) over its name; tap opens it, press and hold manages it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedApp(app: LauncherApp, item: String?, host: LauncherHost, pixel: Int, modifier: Modifier, onHold: () -> Unit) {
    val ui = LocalUiSound.current
    val density = LocalDensity.current.density
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        modifier.pressScale(source).defaultMinSize(minHeight = 72.dp).focusRing(rowShape, Color.White).combinedClickable(interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Button, onLongClick = { ui(Sfx.LONG_PRESS); onHold() }, onClick = { ui(Sfx.CONFIRM); host.open(app.key) })
            .semantics(mergeDescendants = true) {}.padding(vertical = Spacing.S),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        if (item != null) ItemIcon(item, size = 40.dp, description = null) else AppIcon(host, app.key, 40.dp)
        Box(Modifier.padding(top = (2 * pixel / density).dp).padding(horizontal = (1 * pixel / density).dp)) { PmdText(app.label, pixel) }
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
    Box(
        Modifier.size(size).drawWithContent {
            val p = 2.dp.toPx()
            val w = this.size.width
            val h = this.size.height
            val inner = stairRect(p, p, p, w - p, h - p, intArrayOf(0))
            if (shown == null) drawPath(inner, Color.White.copy(alpha = 0.2f)) else clipPath(inner) { this@drawWithContent.drawContent() }
            val ring = Path().apply { fillType = PathFillType.EvenOdd; addPath(stairRect(p, 0f, 0f, w, h, intArrayOf(1, 0))); addPath(inner) }
            drawPath(ring, Color.White.copy(alpha = 0.9f))
        },
    ) {
        if (shown != null) Image(shown, contentDescription = null, modifier = Modifier.fillMaxSize().padding(2.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    }
}

private sealed interface ListRow {
    data class Header(val letter: Char) : ListRow
    data class Entry(val app: LauncherApp) : ListRow
}

/** Every app A to Z over the wallpaper, blurred and darkened (the blur shows from Android 12; the dark veil always). */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun AppListPage(apps: List<LauncherApp>, prefs: LauncherPrefs, host: LauncherHost, onClose: () -> Unit, onHold: (LauncherApp) -> Unit, onTown: () -> Unit = {}, startHidden: Boolean = false, pixel: Int = 2) {
    val ui = LocalUiSound.current
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<AppCategory?>(null) }
    var showHidden by remember { mutableStateOf(startHidden) }
    val categories = LauncherList.categories(apps, prefs)
    // A category that emptied (its last app moved or hidden) falls back to all apps.
    if (category != null && categories.none { it.first == category }) category = null
    val anyHidden = prefs.hidden.any { key -> apps.any { it.key == key } }
    if (showHidden && !anyHidden) showHidden = false
    val shown = LauncherList.visible(apps, prefs, query, category, hidden = showHidden)
    val sections = query.isBlank()
    val rows = if (sections) buildList<ListRow> { shown.forEach { app -> if ((lastOrNull() as? ListRow.Entry)?.app?.letter() != app.letter()) add(ListRow.Header(app.letter())); add(ListRow.Entry(app)) } } else shown.map<LauncherApp, ListRow> { ListRow.Entry(it) }
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
                    if (query.isEmpty()) Text("Search apps", style = MaterialTheme.typography.bodyLarge, color = soft)
                    BasicTextField(
                        query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search apps" },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White), cursorBrush = SolidColor(Color.White),
                    )
                }
                Box(Modifier.height(Spacing.Target).clip(rowShape).clickable(role = Role.Button, onClickLabel = "Launcher settings") { ui(Sfx.CURSOR); onTown() }.padding(horizontal = Spacing.S), contentAlignment = Alignment.Center) {
                    PmdText("Settings", pixel)
                }
                Box(Modifier.height(Spacing.Target).clip(rowShape).clickable(role = Role.Button) { ui(Sfx.BACK); onClose() }.padding(horizontal = Spacing.S), contentAlignment = Alignment.Center) {
                    PmdText("Close", pixel)
                }
            }
            LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = Spacing.Page, vertical = Spacing.XS), horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                item { Chip("All", "", category == null && !showHidden, pixel) { category = null; showHidden = false } }
                items(categories) { (c, n) -> Chip(c.label, "$n", category == c && !showHidden, pixel, icon = c.icon) { category = c; showHidden = false } }
                if (anyHidden) item { Chip("Hidden", "", showHidden, pixel) { showHidden = true; category = null } }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (rows.isEmpty()) PmdText(if (query.isBlank()) "No apps here" else "No app matches " + query.trim(), pixel, Modifier.align(Alignment.Center), alpha = 0.7f)
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = Spacing.Page, end = Spacing.Page + 32.dp, top = Spacing.M, bottom = Spacing.XXL)) {
                    items(rows, key = { row -> when (row) { is ListRow.Header -> "#h${row.letter}"; is ListRow.Entry -> row.app.key } }) { row ->
                        when (row) {
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
    val shape = RoundedCornerShape(50)
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
                            onPrefs(prefs.addFolder("Folder $n").addToFolder("Folder $n", app.key)); onClose()
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
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(Spacing.L).background(Pixel.Panel, RoundedCornerShape(24.dp))
                .padding(Spacing.L).clickable(indication = null, interactionSource = none) {},
            verticalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            Text("Where is your town?", style = MaterialTheme.typography.titleSmall)
            Column(Modifier.height(420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text("Scenes", style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft)
                TownChoices(TownScenes.scenes, current, onPick)
                Text("Dungeons", style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft, modifier = Modifier.padding(top = Spacing.S))
                TownChoices(TownScenes.dungeons, current, onPick)
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
    onTown: () -> Unit, onHidden: () -> Unit, onPlace: () -> Unit, onClose: () -> Unit,
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
                    PmdMenuLine("Launcher settings", pixel, dim = true) {}
                }
            }
            PmdBox(pixel, Modifier.fillMaxWidth()) {
                Column {
                    PmdMenuLine("Town: ${(TownScenes.all.firstOrNull { it.first == prefs.scene } ?: TownScenes.all.first()).second}", pixel, onClick = onTown)
                    PmdMenuLine("Pokémon in the town: ${prefs.townSize}", pixel) { onPrefs(prefs.nextTownSize()) }
                    PmdMenuLine("Weather: ${prefs.weather.label}", pixel) { onPrefs(prefs.nextWeather()) }
                    PmdMenuLine("Pokémon walk about: ${onOff(prefs.walking)}", pixel) { onPrefs(prefs.withWalking(!prefs.walking)) }
                    PmdMenuLine("Date and time box: ${onOff(prefs.showClock)}", pixel) { onPrefs(prefs.withShowClock(!prefs.showClock)) }
                    PmdMenuLine("Status box: ${onOff(prefs.showStatus)}", pixel) { onPrefs(prefs.withShowStatus(!prefs.showStatus)) }
                }
            }
            PmdBox(pixel, Modifier.fillMaxWidth()) {
                Column {
                    PmdMenuLine("Gestures", pixel, dim = true) {}
                    PmdRule(pixel)
                    PmdMenuLine("Drag in from the right for apps: ${onOff(prefs.edgeDrag)}", pixel) { onPrefs(prefs.withEdgeDrag(!prefs.edgeDrag)) }
                    PmdMenuLine("Swipe across for Today: ${onOff(prefs.pageSwipe)}", pixel) { onPrefs(prefs.withPageSwipe(!prefs.pageSwipe)) }
                    PmdMenuLine("Tap three times to gather: ${onOff(prefs.gatherTap)}", pixel) { onPrefs(prefs.withGatherTap(!prefs.gatherTap)) }
                }
            }
            PmdBox(pixel, Modifier.fillMaxWidth()) {
                Column {
                    PmdMenuLine("Weather place: ${prefs.place?.name ?: "not set"}", pixel, onClick = onPlace)
                    PmdMenuLine("Temperature: ${if (prefs.fahrenheit) "Fahrenheit" else "Celsius"}", pixel) { onPrefs(prefs.withFahrenheit(!prefs.fahrenheit)) }
                }
            }
            PmdBox(pixel, Modifier.fillMaxWidth()) {
                Column {
                    PmdVolume("Sound effects", effects, pixel, onChange = { v -> model.store.changeSettings { s -> s.copy(effectsVolume = v, sound = true) } }, onDone = { model.effect(Sfx.CONFIRM) })
                    PmdRule(pixel)
                    PmdVolume("Pokémon cries", cries, pixel, onChange = { v -> model.store.changeSettings { s -> s.copy(criesVolume = v, sound = true) } }, onDone = { model.cry(model.data.player.buddy?.speciesId ?: 25) })
                }
            }
            PmdBox(pixel, Modifier.fillMaxWidth()) {
                Column {
                    if (hiddenCount > 0) PmdMenuLine("Hidden apps: $hiddenCount", pixel, onClick = onHidden)
                    PmdMenuLine("Forget pins and icons", pixel, dim = prefs.pinned.isEmpty() && prefs.icons.isEmpty()) { onPrefs(prefs.withoutPins()) }
                    PmdMenuLine(if (isDefault) "Home app settings" else "Make PokéLife your home app", pixel) { host.openHomeSettings() }
                }
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
private fun PmdVolume(label: String, value: Int, pixel: Int, onChange: (Int) -> Unit, onDone: () -> Unit) {
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
            PmdText(label, pixel)
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
