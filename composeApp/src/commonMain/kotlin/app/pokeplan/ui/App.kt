package app.pokeplan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import app.pokeplan.app.Clock
import app.pokeplan.app.GameStore
import app.pokeplan.app.Storage
import app.pokeplan.app.cloud.CloudClient
import app.pokeplan.app.cloud.CloudConfig
import app.pokeplan.app.cloud.SocialClient
import app.pokeplan.app.cloud.SyncManager
import app.pokeplan.app.cloud.SyncStatus
import io.ktor.client.HttpClient
import app.pokeplan.ui.screens.CollectionScreen
import app.pokeplan.ui.screens.FocusScreen
import app.pokeplan.ui.screens.HomeScreen
import app.pokeplan.ui.screens.MeScreen
import app.pokeplan.ui.screens.OnboardingScreen
import app.pokeplan.ui.screens.PlannerScreen
import app.pokeplan.ui.screens.NavButton
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock as SystemClock

/** The bottom tabs, each with the real game item that stands for it. */
private const val SYNC_DEBOUNCE_MILLIS = 3_000L
private const val SYNC_EVERY_MILLIS = 120_000L

enum class Tab(val label: String, val icon: String, val background: String) {
    HOME("Home", "town-map", "bw01"), PLANNER("Planner", "journal", "bw07"), FOCUS("Focus", "mystery-egg", "bw11"),
    COLLECTION("Pokémon", "poke-ball", "bw15"), ME("Me", "member-card", "bw08"),
}

/** The store plus its current data as Compose state, so screens redraw when anything changes. */
class AppModel(
    val store: GameStore, val motion: MotionSource? = null, val sync: SyncManager? = null, val social: SocialClient? = null,
    private val cries: CryPlayer? = null,
) {
    /** Plays [speciesId]'s cry if the platform can and Sound is on. */
    fun cry(speciesId: Int) {
        val volume = data.settings.effectsLevel
        if (volume > 0f) cries?.play(speciesId, volume)
    }

    /** Where the cloud sync stands, for Settings. */
    var syncStatus: SyncStatus by mutableStateOf(sync?.status?.value ?: SyncStatus.SignedOut)
        internal set

    var data: AppData by mutableStateOf(store.data)
        private set

    /** A task picked on Home or in the Planner to focus on; the Focus screen offers it when it opens. */
    var focusTaskId: String? by mutableStateOf(null)

    /** A Pokémon the Pokémon tab should open on (the buddy's level chip on Home, the widget's chip). Used once. */
    var openPokemonId: String? by mutableStateOf(null)

    /** When the buddy last celebrated an event, so opening Home again does not repeat it. */
    var celebratedAt: Long by mutableStateOf(0L)

    /** The current time, refreshed twice a second so timers redraw. */
    var now: Long by mutableStateOf(store.nowMillis)
        private set

    fun tick() {
        store.tick()
        now = store.nowMillis
    }

    init {
        store.observe { data = it }
    }
}

/**
 * The whole app. [storage] is where the save lives on this platform; [focusTab] is false on desktop and the web, where
 * strict focus makes no sense (people work in other windows there).
 */
@Composable
fun App(
    storage: Storage,
    focusTab: Boolean = true,
    motion: MotionSource? = null,
    startTab: Tab = Tab.HOME,
    startPokemon: String? = null,
    cloud: CloudConfig = CloudSettings.config,
    /** Emits when something outside the app may have changed the save (the Android app coming back after a widget tap). */
    externalChanges: Flow<Unit>? = null,
    cries: CryPlayer? = null,
    /** Called after every change to the saved data (the Android app uses it to keep its widgets up to date). */
    onDataChanged: ((AppData) -> Unit)? = null,
) {
    val model = remember {
        val clock = Clock { SystemClock.System.now().toEpochMilliseconds() }
        val store = GameStore(storage, clock)
        val client = if (cloud.enabled) CloudClient(cloud, HttpClient()) { clock.nowMillis() } else null
        AppModel(store, motion, client?.let { SyncManager(store, it) }, client?.let { SocialClient(it) }, cries).also { it.openPokemonId = startPokemon }
    }
    val store = model.store
    if (onDataChanged != null) DisposableEffect(store) {
        val stop = store.observe(onDataChanged)
        onDispose(stop)
    }
    if (externalChanges != null) LaunchedEffect(externalChanges) { externalChanges.collect { store.reload() } }

    // Cloud sync: follow the status, sync when signed in, and again a moment after any change (and now and then, to hear
    // about changes from other devices).
    val sync = model.sync
    if (sync != null) {
        LaunchedEffect(sync) { sync.status.collect { model.syncStatus = it } }
        val account = model.data.account
        LaunchedEffect(account?.userId) {
            if (account != null) {
                sync.sync()
                while (true) {
                    delay(SYNC_EVERY_MILLIS)
                    sync.sync()
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

    LaunchedEffect(Unit) {
        store.refresh()
        while (true) {
            model.tick()
            delay(500)
        }
    }

    PixelTheme {
        Box(Modifier.fillMaxSize().background(Pixel.Paper)) {
            if (!model.data.onboarded) {
                // The intro's picture runs under the system bars; only its content keeps clear of them.
                PixelBackground("bw12", Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().safeDrawingPadding()) { OnboardingScreen(store) } }
            } else {
                MainShell(model, focusTab, startTab)
            }
        }
    }
}

@Composable
private fun MainShell(model: AppModel, focusTab: Boolean, startTab: Tab) {
    val tabs = Tab.entries.filter { focusTab || it != Tab.FOCUS }
    var selected by rememberSaveable { mutableStateOf(startTab.name) }
    val tab = Tab.valueOf(selected).takeIf { it in tabs } ?: Tab.HOME
    val go = { to: Tab -> selected = to.name }

    // The red header colour continues under the status bar and the white tab bar under the system navigation bar, so the
    // system bars match the app instead of showing the cream page colour. The keyboard lifts the whole column.
    Column(Modifier.fillMaxSize().imePadding()) {
        Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Pixel.Red))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.HOME -> HomeScreen(model, go)
                Tab.PLANNER -> PlannerScreen(model)
                Tab.FOCUS -> FocusScreen(model)
                Tab.COLLECTION -> {
                    val open = remember { model.openPokemonId }
                    LaunchedEffect(Unit) { model.openPokemonId = null }
                    CollectionScreen(model, startOpen = open)
                }
                Tab.ME -> MeScreen(model)
            }
        }
        Column(Modifier.fillMaxWidth().background(Color.White).border(1.dp, Pixel.Border)) {
            // Five labels in one row cannot grow with the system text size without being cut off, so the tab bar's text
            // scales up to 1.25x and its icons and touch targets carry the rest.
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.25f))) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tabs.forEach { t ->
                        NavButton(t.label, t.icon, selected = t == tab, onClick = { go(t) }, modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
