package app.pokeplan

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import app.pokeplan.app.AppData
import app.pokeplan.core.pokemon.ItemCategory
import app.pokeplan.core.pokemon.Items
import app.pokeplan.ui.App
import app.pokeplan.ui.AppLaunch
import app.pokeplan.ui.Tab
import app.pokeplan.widgets.announceBuddyEvent
import app.pokeplan.widgets.updateWidgets
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Fires when the app comes back to the front: a widget may have changed the save meanwhile. */
    private val resumes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** A widget tap or shortcut while the app is already open (the activity is single-instance). */
    private val launches = MutableSharedFlow<AppLaunch>(extraBufferCapacity = 1)
    private var created = false

    private var widgetSignature: Int? = null
    private var widgetRefresh: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The red header colour runs under the status bar (light icons) and the white tab bar under the navigation bar (dark icons).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        window.isNavigationBarContrastEnforced = false
        val storage = saveStorage(this)
        val motion = AccelerometerMotion(this)
        // A widget can open the app on a tab ("FOCUS" for the Start focus widget).
        val first = launchFrom(intent)
        val tab = first.tab ?: Tab.HOME
        setContent {
            App(
                storage, focusTab = true, motion = motion, startTab = tab, startPokemon = first.pokemon, startAddTask = first.addTask, launches = launches, externalChanges = resumes,
                cries = AndroidCryPlayer(this), haptics = AndroidHaptics(this), onDataChanged = ::keepWidgetsCurrent,
            )
        }
    }

    /**
     * Brings the widgets up to date when something they show changes (the buddy, its level, a berry, a session, today's
     * tasks), a moment after the last change so a burst of saves refreshes them once.
     */
    private fun keepWidgetsCurrent(data: AppData) {
        keepScreenOn(data.focus != null)
        val buddy = data.player.buddy
        val signature = listOf(
            buddy?.id, buddy?.form?.id, buddy?.isShiny, buddy?.nickname, buddy?.level, buddy?.candyProgress,
            data.focus != null, data.settings.reduceMotion, data.settings.effectsVolume,
            data.tasks.count { it.isOpen }, data.pending.size,
            data.player.inventory.counts.any { (id, n) -> n > 0 && Items.category(id) == ItemCategory.BERRY },
        ).hashCode()
        if (widgetSignature == null) {
            widgetSignature = signature
            return
        }
        if (signature == widgetSignature) return
        widgetSignature = signature
        widgetRefresh?.cancel()
        widgetRefresh = lifecycleScope.launch {
            delay(800)
            updateWidgets(applicationContext)
        }
    }

    /** The screen stays on while a focus session runs, so the timer stays visible. */
    private fun keepScreenOn(on: Boolean) = runOnUiThread {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun launchFrom(intent: Intent?) = AppLaunch(
        tab = intent?.getStringExtra(EXTRA_TAB)?.let { name -> Tab.entries.firstOrNull { it.name == name } },
        pokemon = intent?.getStringExtra(EXTRA_POKEMON),
        // A shortcut's extra may arrive as a boolean or as text.
        addTask = intent?.extras?.get(EXTRA_ADD_TASK)?.toString() == "true",
    )

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launches.tryEmit(launchFrom(intent))
    }

    override fun onResume() {
        super.onResume()
        // The first resume follows onCreate, which has just read the save.
        if (created) resumes.tryEmit(Unit)
        created = true
    }

    /** Leaving the app: bring the widgets up to date with what just happened. */
    override fun onStop() {
        super.onStop()
        lifecycleScope.launch { announceBuddyEvent(applicationContext) }
    }

    companion object {
        const val EXTRA_TAB = "app.pokeplan.TAB"
        const val EXTRA_POKEMON = "app.pokeplan.POKEMON"
        const val EXTRA_ADD_TASK = "app.pokeplan.ADD_TASK"
    }
}
