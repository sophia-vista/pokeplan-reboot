package app.pokeplan.ui

import androidx.compose.foundation.background
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
import kotlin.time.Clock as SystemClock

/** The bottom tabs, each with the real game item that stands for it. */
enum class Tab(val label: String, val icon: String, val background: String) {
    HOME("Home", "town-map", "bw01"), PLANNER("Planner", "journal", "bw07"), FOCUS("Focus", "mystery-egg", "bw11"),
    COLLECTION("Pokémon", "poke-ball", "bw15"), ME("Me", "member-card", "bw08"),
}

/** The store plus its current data as Compose state, so screens redraw when anything changes. */
class AppModel(val store: GameStore, val motion: MotionSource? = null) {
    var data: AppData by mutableStateOf(store.data)
        private set

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
fun App(storage: Storage, focusTab: Boolean = true, motion: MotionSource? = null) {
    val model = remember { AppModel(GameStore(storage, Clock { SystemClock.System.now().toEpochMilliseconds() }), motion) }
    val store = model.store

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
                MainShell(model, focusTab)
            }
        }
    }
}

@Composable
private fun MainShell(model: AppModel, focusTab: Boolean) {
    val tabs = Tab.entries.filter { focusTab || it != Tab.FOCUS }
    var selected by rememberSaveable { mutableStateOf(Tab.HOME.name) }
    val tab = Tab.valueOf(selected).takeIf { it in tabs } ?: Tab.HOME
    val go = { to: Tab -> selected = to.name }

    Column(Modifier.fillMaxSize()) {
        PixelBackground(tab.background, Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.HOME -> HomeScreen(model, go)
                Tab.PLANNER -> PlannerScreen(model)
                Tab.FOCUS -> FocusScreen(model)
                Tab.COLLECTION -> CollectionScreen(model)
                Tab.ME -> MeScreen(model)
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Pixel.Ink).padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEach { t ->
                NavButton(t.label, t.icon, selected = t == tab, onClick = { go(t) }, modifier = Modifier.weight(1f))
            }
        }
    }
}
