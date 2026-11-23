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
import app.pokeplan.ui.updates.UpdateAccess
import app.pokeplan.ui.updates.UpdateRelease
import app.pokeplan.ui.updates.isNewerRelease
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock as SystemClock


enum class Tab(val label: String, val icon: String) {
    HOME("Home", "town-map"), MODULES("Modules", "explorer-kit"), COLLECTION("Pokémon", "poke-ball"), ME("Me", "member-card"),
}

enum class Place(val tab: Tab) {
    HOME(Tab.HOME), PLAN(Tab.MODULES), PLANNER(Tab.MODULES), FOCUS(Tab.MODULES), COLLECTION(Tab.COLLECTION), PARTY(Tab.COLLECTION), ME(Tab.ME), CARE(Tab.MODULES), HEAL(Tab.MODULES), MINT(Tab.MODULES), BASE(Tab.MODULES), LINK(Tab.MODULES), GEAR(Tab.HOME), WORLD(Tab.MODULES),
}

class AppModel(
    val store: GameStore, val motion: MotionSource? = null,
    private val cries: CryPlayer? = null,
    private val haptics: HapticPlayer? = null,
    val files: FileSaver? = null,
    val calendars: CalendarSource? = null,
    val phone: PhoneState? = null,
    val care: CareStore = CareStore(MemoryStorage(), Clock { store.nowMillis }, store),
    val heal: HealStore = HealStore(MemoryStorage(), Clock { store.nowMillis }, store),
    val mint: MintStore = MintStore(MemoryStorage(), Clock { store.nowMillis }, store),
    val base: BaseStore = BaseStore(MemoryStorage(), Clock { store.nowMillis }, store),
    val link: LinkStore = LinkStore(MemoryStorage(), Clock { store.nowMillis }, store),
    val torch: Torch? = null,
    val capabilities: app.pokeplan.ui.gear.DeviceCapabilities = app.pokeplan.ui.gear.NoCapabilities,
    val sensors: app.pokeplan.ui.gear.SensorFeed? = null,
    val services: app.pokeplan.ui.gear.GearServices = app.pokeplan.ui.gear.GearServices(),
    val backups: BackupAccess? = null,
    val gate: DeviceGate? = null,
    val health: HealthSource? = null,
    val textFiles: TextFileChooser? = null,
    val huawei: HuaweiReader? = null,
    val contacts: ContactSource? = null,
    val images: ImageChooser? = null,
    val imageViewer: ImageViewer? = null,
    val appLauncher: AppLauncher? = null,
    val updates: UpdateAccess? = null,
) {
    val moduleUnlockedAt: MutableMap<String, Long> = mutableMapOf()
    val moduleLeftAt: MutableMap<String, Long> = mutableMapOf()

    var gearOpen: Boolean by mutableStateOf(false)

    /** The newest published build, once [checkForUpdate] has read one. */
    var updateRelease: UpdateRelease? by mutableStateOf(null)
        private set

    /** What the last update check could not do, in words for the About screen. */
    var updateProblem: String? by mutableStateOf(null)
        private set

    /** Whether a check has been made in this process, so opening Me or About does not ask GitHub again on every visit. */
    var updateChecked: Boolean by mutableStateOf(false)
        private set

    /** The newer build waiting to be installed, or null. */
    val updateAvailable: UpdateRelease?
        get() = updateRelease?.takeIf { isNewerRelease(it, AppVersion.NAME, AppVersion.BUILD.toLong()) }

    /** Reads the newest published build; false when it could not be read or there is nothing newer. */
    suspend fun checkForUpdate(): Boolean {
        val access = updates ?: return false
        val release = access.check()
        updateRelease = release
        updateProblem = if (release == null) access.problem else null
        updateChecked = true
        updateAvailable?.let { access.notifyAvailable(it) }
        return updateAvailable != null
    }

    fun haptic() {
        if (data.settings.haptics) haptics?.tick()
    }

    fun cry(pokemon: app.pokeplan.core.pokemon.OwnedPokemon) = cry(pokemon.speciesId, CryVoice.individual(pokemon.id) * CryVoice.Voice(gain = CryVoice.LOUD))

    fun cry(speciesId: Int, voice: CryVoice.Voice = CryVoice.NEUTRAL) {
        val volume = data.settings.criesLevel
        if (volume <= 0f) return
        if (voice == CryVoice.NEUTRAL) cries?.play(speciesId, volume)
        else cries?.playVoiced(speciesId, volume * (voice.gain / CryVoice.LOUD).coerceIn(0f, 1f), voice.pitch, voice.speed)
    }

    fun effect(sound: Sfx) {
        val volume = data.settings.effectsLevel
        if (volume > 0f) cries?.effect(sound, volume)
    }

    fun tap(sound: Sfx) {
        val volume = data.settings.effectsLevel * TAP_GAIN
        if (volume > 0f) cries?.effect(sound, volume)
    }

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

    var healSection: HealSection? by mutableStateOf(null)

    var mintSection: MintSection? by mutableStateOf(null)
    var mintStartType: app.pokeplan.mint.TxType? by mutableStateOf(null)
    var mintStartTo: String? by mutableStateOf(null)

    var baseData: BaseData by mutableStateOf(base.data)
        private set

    var baseSection: BaseSection? by mutableStateOf(null)

    var linkData: LinkData by mutableStateOf(link.data)
        private set

    var linkSection: LinkSection? by mutableStateOf(null)

    var linkPersonId: String? by mutableStateOf(null)
    var linkPersonTab: Int by mutableStateOf(0)

    var feedback: Feedback? by mutableStateOf(null)
        private set
    private var feedbackSerial = 0L

    fun say(text: String, kind: FeedbackKind = FeedbackKind.SAVED) = showFeedback(text, kind, sticky = false)

    internal fun showFeedback(text: String, kind: FeedbackKind, sticky: Boolean) {
        val current = feedback
        if (current != null && current.kind == FeedbackKind.ERROR && kind == FeedbackKind.SAVED && current.text != text) return
        feedback = Feedback(text, kind, sticky, ++feedbackSerial)
    }

    internal fun dropFeedback(text: String) { if (feedback?.text == text) feedback = null }
    internal fun dropFeedback(serial: Long) { if (feedback?.serial == serial) feedback = null }

    var deletedTask: Task? by mutableStateOf(null)

    val quickAddDrafts = mutableMapOf<String, String>()

    var undoItem: app.pokeplan.ui.screens.UndoItem? by mutableStateOf(null)

    fun offerUndo(label: String, undo: () -> Unit) { undoItem = app.pokeplan.ui.screens.UndoItem(label, undo) }

    var calmBreakStartedAt: Long? by mutableStateOf(null)
    var calmBreakSeconds: Int by mutableStateOf(60)

    var focusTaskId: String? by mutableStateOf(null)

    var addTaskRequested: Boolean by mutableStateOf(false)

    var sharedText: String? by mutableStateOf(null)

    var openPokemonId: String? by mutableStateOf(null)

    /** The tab a jump to a Pokémon started from: Back from that Pokémon returns there (null for a plain visit to the tab). */
    var pokemonReturnTab: Tab? by mutableStateOf(null)

    var openParty: Boolean by mutableStateOf(false)

    var module: Module? by mutableStateOf(null)

    var planSection: PlanSection? by mutableStateOf(null)

    var tasksView: app.pokeplan.ui.plan.TasksView by mutableStateOf(app.pokeplan.ui.plan.TasksView.TODAY)

    var careSection: CareSection? by mutableStateOf(null)
    var reflectJournal: Boolean by mutableStateOf(false)
    var worldSection: WorldSection? by mutableStateOf(null)

    var worldTeamDungeon: String? by mutableStateOf(null)

    var worldLayer: app.pokeplan.world.WorldLayer by mutableStateOf(app.pokeplan.world.WorldLayer.RESCUE)

    var journalDraft: String by mutableStateOf("")

    var worldNow: Long by mutableStateOf(0L)

    var powerSaving: Boolean by mutableStateOf(false)

    var cutscene: Cutscene? by mutableStateOf(null)

    val playedHatches = mutableSetOf<String>()

    var celebratedAt: Long by mutableStateOf(0L)

    var now: Long by mutableStateOf(store.nowMillis)
        private set

    fun tick() {
        store.tick()
        care.sync(); heal.sync(); mint.sync(); base.sync(); link.sync()
        now = store.nowMillis
    }

    fun tickClock() {
        now = store.nowMillis
    }

    fun open(place: Place, focusTab: Boolean) {
        // A destination replaces the Pokégear when it is open, or the new screen would open behind it; only Gear asks for it.
        if (place != Place.GEAR) gearOpen = false
        when (place) {
            Place.PLAN -> { module = Module.PLAN; planSection = null }
            Place.PLANNER -> { module = Module.PLAN; planSection = null }
            Place.FOCUS -> { module = Module.PLAN; planSection = if (focusTab) PlanSection.FOCUS else null }
            Place.CARE -> { module = Module.CARE; careSection = null }
            Place.HEAL -> { module = Module.HEAL; healSection = null }
            // The entry's kind is a one-shot from a quick action; arriving from outside Mint starts a plain expense.
            Place.MINT -> { module = Module.MINT; mintSection = null; mintStartType = null; mintStartTo = null }
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
        store.registerLinkOwner(BaseLinkOwner(base))
        base.observe { baseData = it; store.syncLinks() }
        link.observe { linkData = it }
    }
}

data class AppLaunch(val place: Place?, val pokemon: String? = null, val addTask: Boolean = false, val sharedText: String? = null)

@Composable
fun App(
    storage: Storage,
    careStorage: Storage? = null,
    healStorage: Storage? = null,
    mintStorage: Storage? = null,
    baseStorage: Storage? = null,
    linkStorage: Storage? = null,
    focusTab: Boolean = true,
    motion: MotionSource? = null,
    start: Place = Place.HOME,
    startPokemon: String? = null,
    startAddTask: Boolean = false,
    startSharedText: String? = null,
    launches: Flow<AppLaunch>? = null,
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
    updates: UpdateAccess? = null,
    onDataChanged: ((AppData) -> Unit)? = null,
    onCareChanged: ((CareData) -> Unit)? = null,
    onHealChanged: ((HealData) -> Unit)? = null,
    onMintChanged: ((MintData) -> Unit)? = null,
    onBaseChanged: ((BaseData) -> Unit)? = null,
    onLinkChanged: ((LinkData) -> Unit)? = null,
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
        AppModel(store, motion, cries, haptics, files, calendars, phone, care, heal, mint, base, link, torch, capabilities, sensors, services, backups, deviceGate, health, textFiles, huawei, contacts, images, imageViewer, appLauncher, updates).also { it.openPokemonId = startPokemon; it.addTaskRequested = startAddTask; it.sharedText = startSharedText; it.open(start, focusTab) }
    }
    val foreground = rememberInForeground()
    // The phone's power saver is read now and then; World's animations stand still while it is on.
    LaunchedEffect(model, foreground) {
        if (!foreground) return@LaunchedEffect
        while (true) { model.powerSaving = phone?.powerSaving == true; kotlinx.coroutines.delay(30_000) }
    }
    val store = model.store
    if (onDataChanged != null) DisposableEffect(store) {
        val stop = store.observe(onDataChanged)
        onDataChanged(store.data)
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

    // Only while the app is in sight: a stopped Activity keeps its composition, and a 2 Hz tick would otherwise go on
    // waking the CPU (and settling events) with the screen off. Coming back to the front picks up where it left off.
    LaunchedEffect(foreground) {
        if (!foreground) return@LaunchedEffect
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
    // Once per app start: a newer build raises a notification (once per version) without a visit to Me.
    LaunchedEffect(model) { if (model.updates != null && !model.updateChecked) model.checkForUpdate() }
    PixelTheme(dark) {
        Box(Modifier.fillMaxSize().background(Pixel.Paper)) {
            val clock = store.now
            AppBackdrop(model.data.settings.appBackground, clock.hour, clock.minute, dark)
            if (!model.data.onboarded) {
                PixelBackground("bw12", Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().safeDrawingPadding()) { OnboardingScreen(store) } }
            } else {
                CompositionLocalProvider(LocalReduceMotion provides model.data.settings.reduceMotion, app.pokeplan.ui.plan.LocalTaskLists provides model.data.lists, app.pokeplan.ui.screens.LocalBuddy provides model.data.player.buddy, LocalUiSound provides model::tap) { MainShell(model, focusTab, start, launches) }
            }
        }
    }
}

private val EXPANDED_WIDTH = 840.dp
private val CONTENT_MAX_WIDTH = 640.dp

@Composable
private fun MainShell(model: AppModel, focusTab: Boolean, start: Place, launches: Flow<AppLaunch>?) {
    var selected by rememberSaveable { mutableStateOf(start.tab.name) }
    val tab = Tab.entries.firstOrNull { it.name == selected } ?: Tab.HOME
    // A second tap on the tab already open returns it to its first screen, the way a bottom tab usually does.
    var collectionTaps by rememberSaveable { mutableStateOf(0) }
    var meTaps by rememberSaveable { mutableStateOf(0) }
    val onTab = { t: Tab ->
        if (t == tab) {
            when (t) {
                Tab.MODULES -> model.module = null
                Tab.COLLECTION -> { model.pokemonReturnTab = null; collectionTaps++ }
                Tab.ME -> meTaps++
                Tab.HOME -> {}
            }
        } else if (t == Tab.COLLECTION) model.pokemonReturnTab = null
        selected = t.name
    }
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

    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        val rail = maxWidth >= EXPANDED_WIDTH
        val chrome = remember { androidx.compose.runtime.mutableStateOf(Pixel.Chrome) }
        val page: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier) {
                Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(chrome.value))
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxSize()) {
                        ScreenSwitch(tab) { page ->
                            when (page) {
                                Tab.HOME -> HomeScreen(model, { id -> model.openPokemonId = id; model.pokemonReturnTab = Tab.HOME; go(Place.COLLECTION) }, go)
                                Tab.MODULES -> ModulesScreen(model, focusTab) { id -> model.openPokemonId = id; model.pokemonReturnTab = Tab.MODULES; go(Place.COLLECTION) }
                                Tab.COLLECTION -> CollectionScreen(model, resetSignal = collectionTaps, onReturnToTab = { selected = it.name })
                                Tab.ME -> MeScreen(model, resetSignal = meTaps)
                            }
                        }
                        androidx.compose.foundation.layout.Column(Modifier.align(Alignment.BottomCenter)) {
                            FeedbackHost(model)
                            UndoBar(model)
                        }
                        if (model.gearOpen) Box(Modifier.fillMaxSize().background(Pixel.Paper)) {
                            androidx.compose.runtime.CompositionLocalProvider(app.pokeplan.ui.theme.LocalFrameTone provides app.pokeplan.ui.theme.frameToneOf(app.pokeplan.ui.theme.ModuleAccent.GEAR.accent)) {
                                app.pokeplan.ui.gear.GearScreen(model) { model.gearOpen = false }
                            }
                        }
                    }
                }
            }
        }
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
                    Tab.entries.forEach { t -> NavButton(t.label, t.icon, selected = t == tab, onClick = { onTab(t) }, modifier = Modifier.fillMaxWidth()) }
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
                            NavButton(t.label, t.icon, selected = t == tab, onClick = { onTab(t) }, modifier = Modifier.weight(1f))
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
