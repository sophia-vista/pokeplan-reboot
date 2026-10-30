package app.pokeplan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import app.pokeplan.app.ThemeMode
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
    HOME(Tab.HOME), PLANNER(Tab.MODULES), FOCUS(Tab.MODULES), COLLECTION(Tab.COLLECTION), ME(Tab.ME), CARE(Tab.MODULES),
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
) {
    /** A light vibration, if the platform can and Haptics is on in Settings. */
    fun haptic() {
        if (data.settings.haptics) haptics?.tick()
    }

    /** Plays [speciesId]'s cry if the platform can and Sound is on. */
    fun cry(speciesId: Int) {
        val volume = data.settings.effectsLevel
        if (volume > 0f) cries?.play(speciesId, volume)
    }

    /** Plays one of the real game sound effects if the platform can and Sound is on. */
    fun effect(sound: Sfx) {
        val volume = data.settings.effectsLevel
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

    /** The module open in the Modules tab (null shows the list of modules), kept while other tabs are open. */
    var module: Module? by mutableStateOf(null)

    /** The screen open inside Plan (null shows Plan's menu). */
    var planSection: PlanSection? by mutableStateOf(null)

    /** The screen open inside Care (null shows Care's menu). */
    var careSection: CareSection? by mutableStateOf(null)

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

    /** Opens a module's screen for [place] (Planner, Focus) inside Modules; Focus only where there is one ([focusTab]). */
    fun open(place: Place, focusTab: Boolean) {
        when (place) {
            Place.PLANNER -> { module = Module.PLAN; planSection = PlanSection.PLANNER }
            Place.FOCUS -> { module = Module.PLAN; planSection = if (focusTab) PlanSection.FOCUS else null }
            Place.CARE -> { module = Module.CARE; careSection = null }
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
        AppModel(store, motion, sync, client?.let { SocialClient(it) }, cries, haptics, files, calendars, phone, care, careSync).also { it.openPokemonId = startPokemon; it.addTaskRequested = startAddTask; it.open(start, focusTab) }
    }
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
            DayBackground(clock.hour, clock.minute, dark = dark)
            if (!model.data.onboarded) {
                // The intro's picture runs under the system bars; only its content keeps clear of them.
                PixelBackground("bw12", Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().safeDrawingPadding()) { OnboardingScreen(store) } }
            } else {
                CompositionLocalProvider(LocalReduceMotion provides model.data.settings.reduceMotion) { MainShell(model, focusTab, start, launches) }
            }
        }
    }
}

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

    // The red header colour continues under the status bar and the white tab bar under the system navigation bar, so the
    // system bars match the app instead of showing the cream page colour. The keyboard lifts the whole column.
    Column(Modifier.fillMaxSize().imePadding()) {
        Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Pixel.Red))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // The bottom tabs crossfade; each keeps its scroll position while another is open.
            ScreenSwitch(tab) { page ->
                when (page) {
                    Tab.HOME -> HomeScreen(model, go)
                    Tab.MODULES -> ModulesScreen(model, focusTab) { id -> model.openPokemonId = id; go(Place.COLLECTION) }
                    Tab.COLLECTION -> {
                        val open = remember { model.openPokemonId }
                        LaunchedEffect(Unit) { model.openPokemonId = null }
                        CollectionScreen(model, startOpen = open)
                    }
                    Tab.ME -> MeScreen(model)
                }
            }
            UndoBar(model, Modifier.align(Alignment.BottomCenter))
        }
        Column(Modifier.fillMaxWidth().background(Pixel.Panel).border(1.dp, Pixel.Border)) {
            // The labels in one row cannot grow with the system text size without being cut off, so the tab bar's text
            // scales up to 1.25x and its icons and touch targets carry the rest.
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.25f))) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Tab.entries.forEach { t ->
                        NavButton(t.label, t.icon, selected = t == tab, onClick = { selected = t.name }, modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
    CutsceneHost(model)
}
