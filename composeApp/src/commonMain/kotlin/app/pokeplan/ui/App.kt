package app.pokeplan.ui

import app.pokeplan.ui.screens.WorldSection
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import app.pokeplan.ui.theme.Spacing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.isSystemInDarkTheme
import app.pokeplan.app.ThemeMode
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pokeplan.app.AppData
import app.pokeplan.app.CareStore
import app.pokeplan.app.Clock
import app.pokeplan.app.MemoryStorage
import app.pokeplan.care.CareData
import app.pokeplan.app.GameStore
import app.pokeplan.app.Storage
import app.pokeplan.app.cloud.CloudClient
import app.pokeplan.app.cloud.CloudConfig
import app.pokeplan.app.cloud.SocialClient
import app.pokeplan.app.cloud.CareSync
import app.pokeplan.app.cloud.SyncManager
import app.pokeplan.app.cloud.SyncStatus
import io.ktor.client.HttpClient
import app.pokeplan.ui.screens.CareSection
import app.pokeplan.ui.screens.CollectionScreen
import app.pokeplan.ui.screens.Cutscene
import app.pokeplan.ui.screens.CutsceneHost
import app.pokeplan.ui.screens.FocusWatch
import app.pokeplan.ui.screens.HomeScreen
import app.pokeplan.ui.screens.MeScreen
import app.pokeplan.ui.screens.Module
import app.pokeplan.ui.screens.LocalReduceMotion
import app.pokeplan.ui.screens.ModulesScreen
import app.pokeplan.ui.screens.ScreenSwitch
import app.pokeplan.ui.screens.OnboardingScreen
import app.pokeplan.ui.screens.PlanSection
import app.pokeplan.ui.screens.NavButton
import app.pokeplan.ui.screens.UndoBar
import app.pokeplan.plan.Task
import app.pokeplan.plan.TaskState
import androidx.compose.ui.Alignment
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock as SystemClock

private const val SYNC_DEBOUNCE_MILLIS = 3_000L
private const val SYNC_EVERY_MILLIS = 120_000L

/**
 * The bottom tabs, each with the real game item that stands for it. Home, Pokémon and Me are the shared world; each life
 * module (Plan, and the ones to come) lives in Modules (docs/ecosystem.md, section 3).
 */
enum class Tab(val label: String, val icon: String) {
    HOME("Home", "town-map"), MODULES("Modules", "explorer-kit"), COLLECTION("Pokémon", "poke-ball"), ME("Me", "member-card"),
}

/**
 * Where the app can be asked to go: a tab, or a module's screen inside Modules. Widgets, shortcuts and notifications name
 * it ("PLANNER", "FOCUS", "CARE"), so the names stay as they are.
 */
enum class Place(val tab: Tab) {
    HOME(Tab.HOME), PLAN(Tab.MODULES), PLANNER(Tab.MODULES), FOCUS(Tab.MODULES), COLLECTION(Tab.COLLECTION), PARTY(Tab.COLLECTION), ME(Tab.ME), CARE(Tab.MODULES), GEAR(Tab.HOME), WORLD(Tab.MODULES),
}

/** The store plus its current data as Compose state, so screens redraw when anything changes. */
class AppModel(
    val store: GameStore, val motion: MotionSource? = null, val sync: SyncManager? = null, val social: SocialClient? = null,
    private val cries: CryPlayer? = null,
    private val haptics: HapticPlayer? = null,
    /** Where the data export goes; null where the platform has no way to hand over a file. */
    val files: FileSaver? = null,
    /** The phone's calendars, read-only; null where there are none to read (desktop, web). */
    val calendars: CalendarSource? = null,
    /** Whether the screen is off or a call is on, for focus sessions; null where the platform cannot tell. */
    val phone: PhoneState? = null,
    /** The Care module's own store, apart from Plan's save; it pays into [store] (the shared world). */
    val care: CareStore = CareStore(MemoryStorage(), Clock { store.nowMillis }, store),
    /** Care's opt-in cloud copy, through the account [sync] signed in to; null without a cloud. */
    val careSync: CareSync? = null,
    /** The phone's flashlight for the Pokégear; null where there is none (the tool then lights the screen). */
    val torch: Torch? = null,
    /** What this device can do for the Pokégear's hardware tools, found out at run time; [NoCapabilities] where there is no hardware tool (desktop, web). */
    val capabilities: app.pokeplan.ui.gear.DeviceCapabilities = app.pokeplan.ui.gear.NoCapabilities,
    /** The phone's motion and pressure sensors for the Compass, Level and Barometer; null where there are none. */
    val sensors: app.pokeplan.ui.gear.SensorFeed? = null,
    /** The phone's location, microphone, network and code scanner for the Pokégear's field tools; each is null where there is none. */
    val services: app.pokeplan.ui.gear.GearServices = app.pokeplan.ui.gear.GearServices(),
) {
    /** Whether the Pokégear is open: a screen over whichever tab is showing, reached from the button at the top of every tab. */
    var gearOpen: Boolean by mutableStateOf(false)

    /** A light vibration, if the platform can and Haptics is on in Settings. */
    fun haptic() {
        if (data.settings.haptics) haptics?.tick()
    }

    /** Plays [speciesId]'s cry if the platform can and Sound is on. */
    fun cry(speciesId: Int) {
        val volume = data.settings.criesLevel
        if (volume > 0f) cries?.play(speciesId, volume)
    }

    /** Plays one of the real game sound effects if the platform can and Sound is on. */
    fun effect(sound: Sfx) {
        val volume = data.settings.effectsLevel
        if (volume > 0f) cries?.effect(sound, volume)
    }

    /**
     * A UI tap (a button, a tab, a chip, a menu row): a very quiet click, [TAP_GAIN] of the Sound effects volume, so it confirms
     * the press without competing with the game's own sounds (an egg, a rank up, a berry), which keep their full volume.
     */
    fun tap(sound: Sfx) {
        val volume = data.settings.effectsLevel * TAP_GAIN
        if (volume > 0f) cries?.effect(sound, volume)
    }

    /** Ticks off a task, with the select blip when it really was open. */
    fun completeTask(id: String) {
        if (store.data.tasks.any { it.id == id && it.state == TaskState.OPEN }) effect(Sfx.SELECT)
        store.completeTask(id)
    }

    /** Where the cloud sync stands, for Settings. */
    var syncStatus: SyncStatus by mutableStateOf(sync?.status?.value ?: SyncStatus.SignedOut)
        internal set

    /** Where Care's cloud copy stands, for Settings → Modules. */
    var careSyncStatus: SyncStatus by mutableStateOf(SyncStatus.Idle)
        internal set

    var data: AppData by mutableStateOf(store.data)
        private set

    var careData: CareData by mutableStateOf(care.data)
        private set

    /** A task just deleted, which the undo bar can bring back for a few seconds. */
    var deletedTask: Task? by mutableStateOf(null)

    /** A task picked on Home or in the Planner to focus on; the Focus screen offers it when it opens. */
    var focusTaskId: String? by mutableStateOf(null)

    /** Set by the widget's "+" and the app icon's "Add task" shortcut: the Planner opens on Today with the keyboard up. */
    var addTaskRequested: Boolean by mutableStateOf(false)

    /** A Pokémon the Pokémon tab should open on (the buddy's level chip on Home, the widget's chip). Used once. */
    var openPokemonId: String? by mutableStateOf(null)

    /** Set by the "Party" shortcut: the Pokémon tab opens on the Party page. Used once. */
    var openParty: Boolean by mutableStateOf(false)

    /** The module open in the Modules tab (null shows the list of modules), kept while other tabs are open. */
    var module: Module? by mutableStateOf(null)

    /** The screen open inside Plan (null shows Plan's menu). */
    var planSection: PlanSection? by mutableStateOf(null)

    /** The screen open inside Care (null shows Care's menu). */
    var careSection: CareSection? by mutableStateOf(null)
    var worldSection: WorldSection? by mutableStateOf(null)

    /** The layer of the World map on show (the home map or Kanto). */
    var worldLayer: app.pokeplan.world.WorldLayer by mutableStateOf(app.pokeplan.world.WorldLayer.RESCUE)

    /** The journal entry being written in Care, kept while the player looks elsewhere in the app (not saved to disk until saved as an entry). */
    var journalDraft: String by mutableStateOf("")

    /** The clock World's screens read (the store's, refreshed every few seconds while a World screen is open), so lists that depend on the day or a brew follow it. */
    var worldNow: Long by mutableStateOf(0L)

    /** The phone's power saver is on: World's animations stand still, like Reduce motion. */
    var powerSaving: Boolean by mutableStateOf(false)

    /** The hatch or evolution playing over the whole app, if any. */
    var cutscene: Cutscene? by mutableStateOf(null)

    /** Hatched Pokémon (by id) whose hatch already played in this run, so one still waiting for its OK plays once. */
    val playedHatches = mutableSetOf<String>()

    /** When the buddy last celebrated an event, so opening Home again does not repeat it. */
    var celebratedAt: Long by mutableStateOf(0L)

    /** The current time, refreshed twice a second so timers redraw. */
    var now: Long by mutableStateOf(store.nowMillis)
        private set

    fun tick() {
        store.tick()
        now = store.nowMillis
    }

    /** Opens a module's screen for [place] (Plan's page, Planner, Focus, Care) inside Modules; Focus only where there is one ([focusTab]). */
    fun open(place: Place, focusTab: Boolean) {
        when (place) {
            Place.PLAN -> { module = Module.PLAN; planSection = null }
            Place.PLANNER -> { module = Module.PLAN; planSection = PlanSection.PLANNER }
            Place.FOCUS -> { module = Module.PLAN; planSection = if (focusTab) PlanSection.FOCUS else null }
            Place.CARE -> { module = Module.CARE; careSection = null }
            Place.PARTY -> openParty = true
            Place.GEAR -> gearOpen = true
            Place.WORLD -> { module = Module.WORLD; worldSection = null }
            else -> {}
        }
    }

    init {
        store.observe { data = it }
        care.observe { careData = it }
    }
}

/** A request to open the app on a place, a Pokémon, or the add-task box. */
data class AppLaunch(val place: Place?, val pokemon: String? = null, val addTask: Boolean = false)

/**
 * The whole app. [storage] is where the save lives on this platform; [focusTab] is false on desktop and the web, where
 * strict focus makes no sense (people work in other windows there).
 */
@Composable
fun App(
    storage: Storage,
    /** Where the Care module keeps its own save (`care.json`); in memory only when the platform gives none. */
    careStorage: Storage? = null,
    focusTab: Boolean = true,
    motion: MotionSource? = null,
    start: Place = Place.HOME,
    startPokemon: String? = null,
    startAddTask: Boolean = false,
    /** Where the app was asked to go while it was already open (a widget tap or a shortcut). */
    launches: Flow<AppLaunch>? = null,
    cloud: CloudConfig = CloudSettings.config,
    /** Emits when something outside the app may have changed the save (the Android app coming back after a widget tap). */
    externalChanges: Flow<Unit>? = null,
    cries: CryPlayer? = null,
    haptics: HapticPlayer? = null,
    files: FileSaver? = null,
    calendars: CalendarSource? = null,
    phone: PhoneState? = null,
    torch: Torch? = null,
    capabilities: app.pokeplan.ui.gear.DeviceCapabilities = app.pokeplan.ui.gear.NoCapabilities,
    sensors: app.pokeplan.ui.gear.SensorFeed? = null,
    services: app.pokeplan.ui.gear.GearServices = app.pokeplan.ui.gear.GearServices(),
    /** Called after every change to the saved data (the Android app uses it to keep its widgets up to date). */
    onDataChanged: ((AppData) -> Unit)? = null,
    /** Called after every change to Care's save (the Android app keeps routine reminders and the Care widget up to date). */
    onCareChanged: ((CareData) -> Unit)? = null,
    /** Called when the app switches between the light and dark colours, so the host can match the system bar icons. */
    onDark: ((Boolean) -> Unit)? = null,
) {
    val model = remember {
        val clock = Clock { SystemClock.System.now().toEpochMilliseconds() }
        val store = GameStore(storage, clock)
        val client = if (cloud.enabled) CloudClient(cloud, HttpClient()) { clock.nowMillis() } else null
        val care = CareStore(careStorage ?: MemoryStorage(), clock, store, weekStart = { store.data.settings.weekStart })
        val sync = client?.let { SyncManager(store, it) }
        val careSync = if (client != null && sync != null) CareSync(care, client) { sync.session() } else null
        AppModel(store, motion, sync, client?.let { SocialClient(it) }, cries, haptics, files, calendars, phone, care, careSync, torch, capabilities, sensors, services).also { it.openPokemonId = startPokemon; it.addTaskRequested = startAddTask; it.open(start, focusTab) }
    }
    // The phone's power saver is read now and then; World's animations stand still while it is on.
    LaunchedEffect(model) { while (true) { model.powerSaving = phone?.powerSaving == true; kotlinx.coroutines.delay(30_000) } }
    val store = model.store
    if (onDataChanged != null) DisposableEffect(store) {
        val stop = store.observe(onDataChanged)
        onDataChanged(store.data) // the state the app starts in counts too (a focus session already running keeps the screen on)
        onDispose(stop)
    }
    if (onCareChanged != null) DisposableEffect(model.care) {
        val stop = model.care.observe(onCareChanged)
        onCareChanged(model.care.data)
        onDispose(stop)
    }
    if (externalChanges != null) LaunchedEffect(externalChanges) {
        externalChanges.collect {
            store.reload()
            model.care.reload()
        }
    }

    // Cloud sync: follow the status, sync when signed in, and again a moment after any change (and now and then, to hear
    // about changes from other devices).
    val sync = model.sync
    if (sync != null) {
        LaunchedEffect(sync) { sync.status.collect { model.syncStatus = it } }
        val account = model.data.account
        LaunchedEffect(account?.userId) {
            if (account != null) {
                sync.sync()
                model.careSync?.sync()
                while (true) {
                    delay(SYNC_EVERY_MILLIS)
                    sync.sync()
                    model.careSync?.sync()
                }
            }
        }
        LaunchedEffect(account?.unsynced, account?.syncedAtMillis) {
            if (account?.unsynced == true && model.syncStatus !is SyncStatus.Conflict && model.syncStatus !is SyncStatus.Refused) {
                delay(SYNC_DEBOUNCE_MILLIS)
                sync.sync()
            }
        }
    }

    // Care's opt-in copy: follow its status, and upload a moment after any change.
    val careSync = model.careSync
    if (careSync != null) {
        LaunchedEffect(careSync) { careSync.status.collect { model.careSyncStatus = it } }
        val careCloud = model.careData.cloud
        LaunchedEffect(careCloud?.unsynced, careCloud?.syncedAtMillis, model.data.account?.userId) {
            if (careCloud?.unsynced == true && model.data.account != null && model.careSyncStatus !is SyncStatus.Conflict && model.careSyncStatus !is SyncStatus.Refused) {
                delay(SYNC_DEBOUNCE_MILLIS)
                careSync.sync()
            }
        }
    }

    LaunchedEffect(Unit) {
        store.refresh()
        while (true) {
            model.tick()
            delay(500)
        }
    }

    val dark = when (model.data.settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    LaunchedEffect(dark) { onDark?.invoke(dark) }
    PixelTheme(dark) {
        Box(Modifier.fillMaxSize().background(Pixel.Paper)) {
            val clock = store.now
            AppBackdrop(model.data.settings.appBackground, clock.hour, clock.minute, dark)
            if (!model.data.onboarded) {
                // The intro's picture runs under the system bars; only its content keeps clear of them.
                PixelBackground("bw12", Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().safeDrawingPadding()) { OnboardingScreen(store) } }
            } else {
                CompositionLocalProvider(LocalReduceMotion provides model.data.settings.reduceMotion, LocalUiSound provides model::tap) { MainShell(model, focusTab, start, launches) }
            }
        }
    }
}

/** The window width from which the tabs move to a side rail, and the width the page column never exceeds (design system: breakpoints). */
private val EXPANDED_WIDTH = 840.dp
private val CONTENT_MAX_WIDTH = 640.dp

@Composable
private fun MainShell(model: AppModel, focusTab: Boolean, start: Place, launches: Flow<AppLaunch>?) {
    var selected by rememberSaveable { mutableStateOf(start.tab.name) }
    val tab = Tab.entries.firstOrNull { it.name == selected } ?: Tab.HOME
    val go = { to: Place ->
        model.open(to, focusTab)
        selected = to.tab.name
    }
    LaunchedEffect(launches) {
        launches?.collect { launch ->
            model.openPokemonId = launch.pokemon
            model.addTaskRequested = launch.addTask
            launch.place?.let(go)
        }
    }

    if (focusTab) FocusWatch(model)

    // The header colour continues under the status bar and the tab bar (which wears it too) under the system navigation bar, so the
    // system bars match the app instead of showing the cream page colour. The keyboard lifts the whole column.
    // Wide windows (tablet, desktop, web) keep the page a phone-width column in the middle and move the tabs to a rail at the side.
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        val rail = maxWidth >= EXPANDED_WIDTH
        // The status bar and the tab bar wear the colour of the header on the page shown.
        val chrome = remember { androidx.compose.runtime.mutableStateOf(Pixel.Red) }
        val page: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier) {
                Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(chrome.value))
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxSize()) {
                        // The tabs crossfade; each keeps its scroll position while another is open.
                        ScreenSwitch(tab) { page ->
                            when (page) {
                                Tab.HOME -> HomeScreen(model, go)
                                Tab.MODULES -> ModulesScreen(model, focusTab) { id -> model.openPokemonId = id; go(Place.COLLECTION) }
                                Tab.COLLECTION -> {
                                    val open = remember { model.openPokemonId }
                                    val party = remember { model.openParty }
                                    LaunchedEffect(Unit) { model.openPokemonId = null; model.openParty = false }
                                    CollectionScreen(model, startParty = party, startOpen = open)
                                }
                                Tab.ME -> MeScreen(model)
                            }
                        }
                        UndoBar(model, Modifier.align(Alignment.BottomCenter))
                        // The Pokégear covers whichever tab is open until it is closed.
                        if (model.gearOpen) Box(Modifier.fillMaxSize().background(Pixel.Paper)) { app.pokeplan.ui.screens.GearScreen(model) { model.gearOpen = false } }
                    }
                }
            }
        }
        // The labels cannot grow with the system text size without being cut off, so the tab text scales up to 1.25x and
        // its icons and touch targets carry the rest.
        val density = LocalDensity.current
        val navDensity = Density(density.density, density.fontScale.coerceAtMost(1.25f))
        CompositionLocalProvider(app.pokeplan.ui.theme.LocalChromeColor provides chrome) {
        if (rail) Row(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalDensity provides navDensity) {
                Column(
                    Modifier.width(96.dp).fillMaxHeight().background(chrome.value)
                        .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars)).padding(horizontal = Spacing.S, vertical = Spacing.M),
                    verticalArrangement = Arrangement.spacedBy(Spacing.S),
                ) {
                    Tab.entries.forEach { t -> NavButton(t.label, t.icon, selected = t == tab, onClick = { selected = t.name }, modifier = Modifier.fillMaxWidth()) }
                }
            }
            page(Modifier.weight(1f).fillMaxHeight())
        } else Column(Modifier.fillMaxSize()) {
            page(Modifier.weight(1f).fillMaxWidth())
            Column(Modifier.fillMaxWidth().background(chrome.value)) {
                CompositionLocalProvider(LocalDensity provides navDensity) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.S, vertical = Spacing.S),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
                    ) {
                        Tab.entries.forEach { t ->
                            NavButton(t.label, t.icon, selected = t == tab, onClick = { selected = t.name }, modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
        }
    }
    CutsceneHost(model)
}
