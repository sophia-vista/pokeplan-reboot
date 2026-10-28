package app.pokeplan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import app.pokeplan.app.AppData
import app.pokeplan.app.CareReminders
import app.pokeplan.app.upcomingReminders
import app.pokeplan.care.CareData
import app.pokeplan.core.pokemon.ItemCategory
import app.pokeplan.core.pokemon.Items
import app.pokeplan.ui.App
import app.pokeplan.ui.AppLaunch
import app.pokeplan.ui.Place
import app.pokeplan.reminders.AppAlerts
import app.pokeplan.reminders.RoutineReminders
import app.pokeplan.reminders.TaskReminders
import app.pokeplan.widgets.announceBuddyEvent
import app.pokeplan.widgets.updateWidgets
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

class MainActivity : ComponentActivity() {
    /** Fires when the app comes back to the front: a widget may have changed the save meanwhile. */
    private val resumes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** A widget tap or shortcut while the app is already open (the activity is single-instance). */
    private val launches = MutableSharedFlow<AppLaunch>(extraBufferCapacity = 1)
    private var created = false

    private var widgetSignature: Int? = null
    private var widgetRefresh: Job? = null
    private var reminderSignature: Int? = null
    private var reminderRefresh: Job? = null
    private var appData: AppData? = null
    private var careData: CareData? = null
    private var routineSignature: Int? = null
    private var routineRefresh: Job? = null

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
        val calendars = AndroidCalendars(this)
        // A widget can open the app on a place ("FOCUS" for the Start focus widget).
        val first = launchFrom(intent)
        val place = first.place ?: Place.HOME
        setContent {
            App(
                storage, careStorage = careStorage(this), focusTab = true, motion = motion, start = place, startPokemon = first.pokemon, startAddTask = first.addTask, launches = launches, externalChanges = resumes,
                cries = AndroidCryPlayer(this), haptics = AndroidHaptics(this), files = AndroidFileSaver(this), calendars = calendars, phone = AndroidPhoneState(this), onDataChanged = ::keepWidgetsCurrent, onCareChanged = ::careChanged, onDark = ::matchSystemBars,
            )
        }
    }

    /** The tab bar under the navigation bar is dark in dark mode, so the navigation bar icons turn light. */
    private fun matchSystemBars(dark: Boolean) {
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = !dark
    }

    /**
     * Brings the widgets up to date when something they show changes (the buddy, its level, a berry, a session, today's
     * tasks), a moment after the last change so a burst of saves refreshes them once.
     */
    private fun keepWidgetsCurrent(data: AppData) {
        keepScreenOn(data.focus != null)
        keepRemindersCurrent(data)
        appData = data
        keepRoutineRemindersCurrent()
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

    /**
     * Sets the reminder alarms again when the reminders to come change, a moment after the last change: task reminders,
     * the halfway and end alerts of a running focus session, the streak reminder and the weekly recap.
     */
    private fun keepRemindersCurrent(data: AppData) {
        val now = System.currentTimeMillis()
        val zone = TimeZone.currentSystemDefault()
        val upcoming = data.upcomingReminders(now, zone)
        val alerts = AppAlerts.plan(data, now, zone)
        if (upcoming.isNotEmpty() || alerts.sessionId != null || data.activeDays.isNotEmpty()) askForNotifications()
        val signature = listOf(upcoming.map { it.taskId to it.atMillis }, alerts).hashCode()
        if (signature == reminderSignature) return
        reminderSignature = signature
        reminderRefresh?.cancel()
        reminderRefresh = lifecycleScope.launch {
            delay(800)
            TaskReminders.reschedule(applicationContext, upcoming)
            AppAlerts.schedule(applicationContext, alerts)
        }
    }

    private fun careChanged(data: CareData) {
        careData = data
        keepRoutineRemindersCurrent()
    }

    /** Sets Care's routine reminders again when they change (a routine, a tick, a focus session), a moment after the last change. */
    private fun keepRoutineRemindersCurrent() {
        val app = appData ?: return
        val care = careData ?: return
        val upcoming = CareReminders.upcoming(care, app, System.currentTimeMillis(), TimeZone.currentSystemDefault())
        if (upcoming.isNotEmpty()) askForNotifications()
        val signature = upcoming.map { it.routineId to it.atMillis }.hashCode()
        if (signature == routineSignature) return
        routineSignature = signature
        routineRefresh?.cancel()
        routineRefresh = lifecycleScope.launch {
            delay(800)
            RoutineReminders.reschedule(applicationContext, upcoming)
        }
    }

    /** Android 13 and later ask before an app may notify: asked once, the first time a reminder is set. */
    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("task_reminders", MODE_PRIVATE)
        if (prefs.getBoolean("asked", false)) return
        prefs.edit().putBoolean("asked", true).apply()
        runOnUiThread { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) }
    }

    /** The screen stays on while a focus session runs, so the timer stays visible. */
    private fun keepScreenOn(on: Boolean) = runOnUiThread {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun launchFrom(intent: Intent?) = AppLaunch(
        place = intent?.getStringExtra(EXTRA_TAB)?.let { name -> Place.entries.firstOrNull { it.name == name } },
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
