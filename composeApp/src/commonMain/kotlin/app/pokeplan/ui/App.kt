package app.pokeplan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
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
class AppModel(val store: GameStore, val motion: MotionSource? = null, val sync: SyncManager? = null, val social: SocialClient? = null) {
    /** Where the cloud sync stands, for Settings. */
    var syncStatus: SyncStatus by mutableStateOf(sync?.status?.value ?: SyncStatus.SignedOut)
        internal set

    var data: AppData by mutableStateOf(store.data)
        private set

    /** A task picked on Home or in the Planner to focus on; the Focus screen offers it when it opens. */
    var focusTaskId: String? by mutableStateOf(null)

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
    cloud: CloudConfig = CloudSettings.config,
    /** Emits when something outside the app may have changed the save (the Android app coming back after a widget tap). */
    externalChanges: Flow<Unit>? = null,
) {
    val model = remember {
        val clock = Clock { SystemClock.System.now().toEpochMilliseconds() }
        val store = GameStore(storage, clock)
        val client = if (cloud.enabled) CloudClient(cloud, HttpClient()) { clock.nowMillis() } else null
        AppModel(store, motion, client?.let { SyncManager(store, it) }, client?.let { SocialClient(it) })
    }
    val store = model.store
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
        Box(Modifier.fillMaxSize().background(Pixel.Paper).safeDrawingPadding()) {
            if (!model.data.onboarded) {
                PixelBackground("bw12", Modifier.fillMaxSize()) { OnboardingScreen(store) }
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

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.HOME -> HomeScreen(model, go)
                Tab.PLANNER -> PlannerScreen(model)
                Tab.FOCUS -> FocusScreen(model)
                Tab.COLLECTION -> CollectionScreen(model)
                Tab.ME -> MeScreen(model)
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Color.White).border(1.dp, Pixel.Border)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEach { t ->
                NavButton(t.label, t.icon, selected = t == tab, onClick = { go(t) }, modifier = Modifier.weight(1f))
            }
        }
    }
}
