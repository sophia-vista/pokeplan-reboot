package app.pokeplan.ui

import app.pokeplan.ui.world.WorldSection
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
import app.pokeplan.app.care.CareStore
import app.pokeplan.app.heal.HealStore
import app.pokeplan.app.plan.BaseLinkOwner
import app.pokeplan.app.base.BaseStore
import app.pokeplan.app.link.LinkStore
import app.pokeplan.app.mint.MintStore
import app.pokeplan.base.BaseData
import app.pokeplan.link.LinkData
import app.pokeplan.ui.base.BaseSection
import app.pokeplan.ui.link.LinkSection
import app.pokeplan.mint.MintData
import app.pokeplan.ui.mint.MintSection
import app.pokeplan.heal.HealData
import app.pokeplan.ui.heal.HealSection
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.care.CareData
import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.Storage
import app.pokeplan.ui.care.CareSection
import app.pokeplan.ui.pokemon.CollectionScreen
import app.pokeplan.ui.screens.Cutscene
import app.pokeplan.ui.screens.CutsceneHost
import app.pokeplan.ui.plan.FocusWatch
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


/**
 * The bottom tabs, each with the real game item that stands for it. Home, Pokémon and Me are the shared world; each life
 * module (Plan, and the ones to come) lives in Modules (docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md).
 */
enum class Tab(val label: String, val icon: String) {
    HOME("Home", "town-map"), MODULES("Modules", "explorer-kit"), COLLECTION("Pokémon", "poke-ball"), ME("Me", "member-card"),
}

/**
 * Where the app can be asked to go: a tab, or a module's screen inside Modules. Widgets, shortcuts and notifications name
 * it ("PLANNER", "FOCUS", "CARE"), so the names stay as they are.
 */
enum class Place(val tab: Tab) {
    HOME(Tab.HOME), PLAN(Tab.MODULES), PLANNER(Tab.MODULES), FOCUS(Tab.MODULES), COLLECTION(Tab.COLLECTION), PARTY(Tab.COLLECTION), ME(Tab.ME), CARE(Tab.MODULES), HEAL(Tab.MODULES), MINT(Tab.MODULES), BASE(Tab.MODULES), LINK(Tab.MODULES), GEAR(Tab.HOME), WORLD(Tab.MODULES),
}

/** The store plus its current data as Compose state, so screens redraw when anything changes. */
class AppModel(
    val store: GameStore, val motion: MotionSource? = null,
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
    /** The Heal module's own store, apart from Plan's and Care's saves (private health records); it pays Field Research into [store] with a minimal event. */
    val heal: HealStore = HealStore(MemoryStorage(), Clock { store.nowMillis }, store),
    /** The Mint module's own store (private financial records); it pays Ledger Research into [store] with a minimal event that holds no figure. */
    val mint: MintStore = MintStore(MemoryStorage(), Clock { store.nowMillis }, store),
    /** The Home module's own store (private household records); it pays Habitat Discovery into [store] with a minimal event that holds no household word. */
    val base: BaseStore = BaseStore(MemoryStorage(), Clock { store.nowMillis }, store),
    /** The Link module's own store (private people, notes, dates and follow-ups); it pays Postcard Trail into [store] with a minimal event that holds no word of a person. */
    val link: LinkStore = LinkStore(MemoryStorage(), Clock { store.nowMillis }, store),
    /** The phone's flashlight for the Pokégear; null where there is none (the tool then lights the screen). */
    val torch: Torch? = null,
    /** What this device can do for the Pokégear's hardware tools, found out at run time; [NoCapabilities] where there is no hardware tool (desktop, web). */
    val capabilities: app.pokeplan.ui.gear.DeviceCapabilities = app.pokeplan.ui.gear.NoCapabilities,
    /** The phone's motion and pressure sensors for the Compass, Level and Barometer; null where there are none. */
    val sensors: app.pokeplan.ui.gear.SensorFeed? = null,
    /** The phone's location, microphone, network and code scanner for the Pokégear's field tools; each is null where there is none. */
    val services: app.pokeplan.ui.gear.GearServices = app.pokeplan.ui.gear.GearServices(),
    /** The platform's backups (Android); null where there are none. */
    val backups: BackupAccess? = null,
    /** The phone's screen lock for Mint's optional gate; null where there is none (desktop, web), and then the gate is not offered. */
    val gate: DeviceGate? = null,
    /** Health Connect for HEAL's optional, read-only import; null where there is none (desktop, web). */
    val health: HealthSource? = null,
    /** Chooses a statement file for MINT's CSV import; null where there is none, and then the import is not offered. */
    val textFiles: TextFileChooser? = null,
    /** Reads Huawei Health's screens and notification for HEAL's probe; null where there is none (desktop, web). */
    val huawei: HuaweiReader? = null,
    /** The phone's contacts for LINK's optional import, read-only; null where there are none (desktop, web), and then the import is not offered. */
    val contacts: ContactSource? = null,
    /** Picks and shrinks one image for MINT's account QR; null where there is none (desktop, web), and then the QR is not offered. */
    val images: ImageChooser? = null,
    /** Full brightness and the share sheet for MINT's big QR; null where there is none (desktop, web), and then the QR is shown without them. */
    val imageViewer: ImageViewer? = null,
    /** Lists and starts installed apps for MINT's 'Open the wallet's app'; null where there is none (desktop, web), and then it is not offered. */
    val appLauncher: AppLauncher? = null,
) {
    /** When each module behind the screen lock was last opened with it and last left (millis), by module name; see `ScreenLockGate` in screens/ModuleGate.kt. */
    val moduleUnlockedAt: MutableMap<String, Long> = mutableMapOf()
    val moduleLeftAt: MutableMap<String, Long> = mutableMapOf()

    /** Whether the Pokégear is open: a screen over whichever tab is showing, reached from the button at the top of every tab. */
    var gearOpen: Boolean by mutableStateOf(false)

    /** A light vibration, if the platform can and Haptics is on in Settings. */
    fun haptic() {
        if (data.settings.haptics) haptics?.tick()
    }

    /** Plays [speciesId]'s cry if the platform can and Sound is on, with the [voice] (the cry as recorded unless given; see [CryVoice]). */
    /** [pokemon]'s own cry: the recorded one with the small detune that is its alone, so two of a species do not sound the same, at the level a plain cry has (the voice's gain is shown against the loudest). */
    fun cry(pokemon: app.pokeplan.core.pokemon.OwnedPokemon) = cry(pokemon.speciesId, CryVoice.individual(pokemon.id) * CryVoice.Voice(gain = CryVoice.LOUD))

    fun cry(speciesId: Int, voice: CryVoice.Voice = CryVoice.NEUTRAL) {
        val volume = data.settings.criesLevel
        if (volume <= 0f) return
        // A voice's gain is shown against the loudest (CryVoice.LOUD): the player has no headroom above full volume, so a default cry in a story plays a
        // sixth under the Sound level and a loud one at it.
        if (voice == CryVoice.NEUTRAL) cries?.play(speciesId, volume)
        else cries?.playVoiced(speciesId, volume * (voice.gain / CryVoice.LOUD).coerceIn(0f, 1f), voice.pitch, voice.speed)
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

    var data: AppData by mutableStateOf(store.data)
        private set

    var careData: CareData by mutableStateOf(care.data)
        private set

    var healData: HealData by mutableStateOf(heal.data)
        private set

    var mintData: MintData by mutableStateOf(mint.data)
        private set

    /** The screen open inside Heal (null shows Heal's menu). */
    var healSection: HealSection? by mutableStateOf(null)

    /** The screen open inside Mint (null shows Mint's menu), and the kind of entry a quick action asked the Activity screen to start with. */
    var mintSection: MintSection? by mutableStateOf(null)
    var mintStartType: app.pokeplan.mint.TxType? by mutableStateOf(null)
    /** The account a "Pay card" action asked the Activity screen to send the money to (read once, then cleared). */
    var mintStartTo: String? by mutableStateOf(null)

    var baseData: BaseData by mutableStateOf(base.data)
        private set

    /** The screen open inside Home (null shows Home's menu). */
    var baseSection: BaseSection? by mutableStateOf(null)

    var linkData: LinkData by mutableStateOf(link.data)
        private set

    /** The screen open inside Link (null shows Link's menu). */
    var linkSection: LinkSection? by mutableStateOf(null)

    /** The person whose own screen is open inside Link's People (null shows the directory), and the tab it opens on (0 Overview, 1 Profile, 2 Notes, 3 Follow-ups). */
    var linkPersonId: String? by mutableStateOf(null)
    var linkPersonTab: Int by mutableStateOf(0)

    /** The one line the feedback bar shows (see [Feedback]); say it with [say] or keep it up with [Report]. */
    var feedback: Feedback? by mutableStateOf(null)
        private set
    private var feedbackSerial = 0L

    /** Tells the player how something went, once: [text] shows in the feedback bar for a moment ([FeedbackKind]) and goes. */
    fun say(text: String, kind: FeedbackKind = FeedbackKind.SAVED) = showFeedback(text, kind, sticky = false)

    internal fun showFeedback(text: String, kind: FeedbackKind, sticky: Boolean) {
        // An error already up stays over a plain saved line that arrives with it.
        val current = feedback
        if (current != null && current.kind == FeedbackKind.ERROR && kind == FeedbackKind.SAVED && current.text != text) return
        feedback = Feedback(text, kind, sticky, ++feedbackSerial)
    }

    internal fun dropFeedback(text: String) { if (feedback?.text == text) feedback = null }
    internal fun dropFeedback(serial: Long) { if (feedback?.serial == serial) feedback = null }

    /** A task just deleted, which the undo bar can bring back for a few seconds. */
    var deletedTask: Task? by mutableStateOf(null)

    /** The unsent words in each quick-add box (by where it files), kept while the player visits another screen and comes back; in memory only. */
    val quickAddDrafts = mutableMapOf<String, String>()

    /** Anything else just deleted (a budget, a bill, a drink): the undo bar shows it for a few seconds and [UndoItem.undo] puts it back. */
    var undoItem: app.pokeplan.ui.screens.UndoItem? by mutableStateOf(null)

    /** Offers [undo] under [label] in the undo bar, replacing an earlier offer. Take [snapshot]s before the change, restore them in [undo]. */
    fun offerUndo(label: String, undo: () -> Unit) { undoItem = app.pokeplan.ui.screens.UndoItem(label, undo) }

    /** A calm break in progress (its start in epoch millis, its length in seconds), kept here so leaving Care's screen for a moment does not lose it. */
    var calmBreakStartedAt: Long? by mutableStateOf(null)
    var calmBreakSeconds: Int by mutableStateOf(60)

    /** A task picked on Home or in the Planner to focus on; the Focus screen offers it when it opens. */
    var focusTaskId: String? by mutableStateOf(null)

    /** Set by the widget's "+" and the app icon's "Add task" shortcut: the Planner opens on Today with the keyboard up. */
    var addTaskRequested: Boolean by mutableStateOf(false)

    /** Text another app shared to PokéLife: the Planner's quick add opens with it typed in (not added until the player says so). Used once. */
    var sharedText: String? by mutableStateOf(null)

    /** A Pokémon the Pokémon tab should open on (the buddy's level chip on Home, the widget's chip). Used once. */
    var openPokemonId: String? by mutableStateOf(null)

    /** Set by the "Party" shortcut: the Pokémon tab opens on the Party page. Used once. */
    var openParty: Boolean by mutableStateOf(false)

    /** The module open in the Modules tab (null shows the list of modules), kept while other tabs are open. */
    var module: Module? by mutableStateOf(null)

    /** The screen open inside Plan (null shows Plan's menu). */
    var planSection: PlanSection? by mutableStateOf(null)

    /** The view open in Plan's Tasks workspace (Open by default). */
    var tasksView: app.pokeplan.ui.plan.TasksView by mutableStateOf(app.pokeplan.ui.plan.TasksView.TODAY)

    /** The list Plan's Tasks are narrowed to (null: all), and whether the Lists row under the view pills is open (folded by default). */

    /** The place open inside Care (null is Today, the landing; Routines is a page under Today). */
    var careSection: CareSection? by mutableStateOf(null)
    /** Set by Care's "Write" quick action: Reflect opens on the journal instead of the diary card (read once, then cleared). */
    var reflectJournal: Boolean by mutableStateOf(false)
    var worldSection: WorldSection? by mutableStateOf(null)

    /** The dungeon the World team editor is choosing a team for (UI only, not saved; null = the team's own, else the next one to explore). */
    var worldTeamDungeon: String? by mutableStateOf(null)

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
        // A reminder's "Done" may have changed a module's records while the app is open (the game's own save is picked up in store.tick()).
        care.sync(); heal.sync(); mint.sync(); base.sync(); link.sync()
        now = store.nowMillis
    }

    /** Only the clock moves: for the launcher, which shows the time and settles nothing (the app owns the save). */
    fun tickClock() {
        now = store.nowMillis
    }

    /** Opens a module's screen for [place] (Plan's page, Planner, Focus, Care) inside Modules; Focus only where there is one ([focusTab]). */
    fun open(place: Place, focusTab: Boolean) {
        when (place) {
            Place.PLAN -> { module = Module.PLAN; planSection = null }
            Place.PLANNER -> { module = Module.PLAN; planSection = null }
            Place.FOCUS -> { module = Module.PLAN; planSection = if (focusTab) PlanSection.FOCUS else null }
            Place.CARE -> { module = Module.CARE; careSection = null }
            Place.HEAL -> { module = Module.HEAL; healSection = null }
            Place.MINT -> { module = Module.MINT; mintSection = null }
            Place.BASE -> { module = Module.BASE; baseSection = null }
            Place.LINK -> { module = Module.LINK; linkSection = null; linkPersonId = null }
            Place.PARTY -> openParty = true
            Place.GEAR -> gearOpen = true
            Place.WORLD -> { module = Module.WORLD; worldSection = null }
            else -> {}
        }
    }

    init {
        store.observe { data = it }
        care.observe { careData = it }
        heal.observe { healData = it }
        mint.observe { mintData = it }
        // A PLAN task linked to a HOME chore is a projection: HOME completes and pays it, and the task follows (PRODUCT_BIBLE.md section 4).
        store.registerLinkOwner(BaseLinkOwner(base))
        base.observe { baseData = it; store.syncLinks() }
        link.observe { linkData = it }
    }
}

/** A request to open the app on a place, a Pokémon, or the add-task box. */
data class AppLaunch(val place: Place?, val pokemon: String? = null, val addTask: Boolean = false, val sharedText: String? = null)

/**
 * The whole app. [storage] is where the save lives on this platform; [focusTab] is false on desktop and the web, where
 * strict focus makes no sense (people work in other windows there).
 */
@Composable
fun App(
    storage: Storage,
    /** Where the Care module keeps its own save (`care.json`); in memory only when the platform gives none. */
    careStorage: Storage? = null,
    /** Where the Heal module keeps its records (its own tables on Android); in memory only when the platform gives none. */
    healStorage: Storage? = null,
    /** Where the Mint module keeps its records (its own tables on Android); in memory only when the platform gives none. */
    mintStorage: Storage? = null,
    /** Where the Home module keeps its records (its own tables on Android); in memory only when the platform gives none. */
    baseStorage: Storage? = null,
    /** Where the Link module keeps its records (its own tables on Android); in memory only when the platform gives none. */
    linkStorage: Storage? = null,
    focusTab: Boolean = true,
    motion: MotionSource? = null,
    start: Place = Place.HOME,
    startPokemon: String? = null,
    startAddTask: Boolean = false,
    startSharedText: String? = null,
    /** Where the app was asked to go while it was already open (a widget tap or a shortcut). */
    launches: Flow<AppLaunch>? = null,
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
    backups: BackupAccess? = null,
    deviceGate: DeviceGate? = null,
    health: HealthSource? = null,
    textFiles: TextFileChooser? = null,
    huawei: HuaweiReader? = null,
    contacts: ContactSource? = null,
    images: ImageChooser? = null,
    imageViewer: ImageViewer? = null,
    appLauncher: AppLauncher? = null,
    /** Called after every change to the saved data (the Android app uses it to keep its widgets up to date). */
    onDataChanged: ((AppData) -> Unit)? = null,
    /** Called after every change to Care's save (the Android app keeps routine reminders and the Care widget up to date). */
    onCareChanged: ((CareData) -> Unit)? = null,
    /** Called after every change to Heal's records (the Android app keeps hydration reminders up to date). It receives the settings' reminder times only through the data; nothing else leaves Heal. */
    onHealChanged: ((HealData) -> Unit)? = null,
    /** Called after every change to Mint's records (the Android app keeps bill reminders up to date). Only the host's own reminder code looks inside; nothing is sent anywhere. */
    onMintChanged: ((MintData) -> Unit)? = null,
    /** Called after every change to Home's records (the Android app keeps Home's reminders up to date). Only the host's own reminder code looks inside; nothing is sent anywhere. */
    onBaseChanged: ((BaseData) -> Unit)? = null,
    /** Called after every change to Link's records (the Android app keeps Link's reminders up to date). Only the host's own reminder code looks inside; nothing is sent anywhere. */
    onLinkChanged: ((LinkData) -> Unit)? = null,
    /** Called when the app switches between the light and dark colours, so the host can match the system bar icons. */
    onDark: ((Boolean) -> Unit)? = null,
) {
    val model = remember {
        val clock = Clock { SystemClock.System.now().toEpochMilliseconds() }
        val store = GameStore(storage, clock)
        val care = CareStore(careStorage ?: MemoryStorage(), clock, store, weekStart = { store.data.settings.weekStart })
        val heal = HealStore(healStorage ?: MemoryStorage(), clock, store, weekStart = { store.data.settings.weekStart })
        val mint = MintStore(mintStorage ?: MemoryStorage(), clock, store, weekStart = { store.data.settings.weekStart })
        val base = BaseStore(baseStorage ?: MemoryStorage(), clock, store)
        val link = LinkStore(linkStorage ?: MemoryStorage(), clock, store)
        AppModel(store, motion, cries, haptics, files, calendars, phone, care, heal, mint, base, link, torch, capabilities, sensors, services, backups, deviceGate, health, textFiles, huawei, contacts, images, imageViewer, appLauncher).also { it.openPokemonId = startPokemon; it.addTaskRequested = startAddTask; it.sharedText = startSharedText; it.open(start, focusTab) }
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
    if (onHealChanged != null) DisposableEffect(model.heal) {
        val stop = model.heal.observe(onHealChanged)
        onHealChanged(model.heal.data)
        onDispose(stop)
    }
    if (onMintChanged != null) DisposableEffect(model.mint) {
        val stop = model.mint.observe(onMintChanged)
        onMintChanged(model.mint.data)
        onDispose(stop)
    }
    if (onBaseChanged != null) DisposableEffect(model.base) {
        val stop = model.base.observe(onBaseChanged)
        onBaseChanged(model.base.data)
        onDispose(stop)
    }
    if (onLinkChanged != null) DisposableEffect(model.link) {
        val stop = model.link.observe(onLinkChanged)
        onLinkChanged(model.link.data)
        onDispose(stop)
    }
    if (externalChanges != null) LaunchedEffect(externalChanges) {
        externalChanges.collect {
            store.reload()
            model.care.reload()
            model.heal.reload()
            model.mint.reload()
            model.base.reload()
            model.link.reload()
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
                CompositionLocalProvider(LocalReduceMotion provides model.data.settings.reduceMotion, app.pokeplan.ui.plan.LocalTaskLists provides model.data.lists, app.pokeplan.ui.screens.LocalBuddy provides model.data.player.buddy, LocalUiSound provides model::tap) { MainShell(model, focusTab, start, launches) }
            }
        }
    }
}

/** The window width from which the tabs move to a side rail, and the width the page column never exceeds (design system: breakpoints). */
private val EXPANDED_WIDTH = 840.dp
private val CONTENT_MAX_WIDTH = 640.dp

/** Tapping the Modules tab while it is already open returns to the list of modules, the way a bottom tab usually does. */
private fun backToModuleList(model: AppModel, tapped: Tab, current: Tab) {
    if (tapped == Tab.MODULES && current == Tab.MODULES) model.module = null
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
            model.sharedText = launch.sharedText
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
        val chrome = remember { androidx.compose.runtime.mutableStateOf(Pixel.Chrome) }
        val page: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier) {
                Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(chrome.value))
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxSize()) {
                        // The tabs crossfade; each keeps its scroll position while another is open.
                        ScreenSwitch(tab) { page ->
                            when (page) {
                                Tab.HOME -> HomeScreen(model, { id -> model.openPokemonId = id; go(Place.COLLECTION) }, go)
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
                        // One bar for how things went, and the undo bar under it: they stack, so neither hides the other.
                        androidx.compose.foundation.layout.Column(Modifier.align(Alignment.BottomCenter)) {
                            FeedbackHost(model)
                            UndoBar(model)
                        }
                        // The Pokégear covers whichever tab is open until it is closed.
                        if (model.gearOpen) Box(Modifier.fillMaxSize().background(Pixel.Paper)) {
                            androidx.compose.runtime.CompositionLocalProvider(app.pokeplan.ui.theme.LocalFrameTone provides app.pokeplan.ui.theme.frameToneOf(app.pokeplan.ui.theme.ModuleAccent.GEAR.accent)) {
                                app.pokeplan.ui.gear.GearScreen(model) { model.gearOpen = false }
                            }
                        }
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
                    Tab.entries.forEach { t -> NavButton(t.label, t.icon, selected = t == tab, onClick = { backToModuleList(model, t, tab); selected = t.name }, modifier = Modifier.fillMaxWidth()) }
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
                            NavButton(t.label, t.icon, selected = t == tab, onClick = { backToModuleList(model, t, tab); selected = t.name }, modifier = Modifier.weight(1f))
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
