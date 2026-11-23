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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.pokeplan.app.AppData
import app.pokeplan.app.care.CareReminders
import app.pokeplan.app.heal.HealReminders
import app.pokeplan.heal.HealData
import app.pokeplan.mint.MintData
import app.pokeplan.app.base.BaseReminders
import app.pokeplan.app.mint.MintReminders
import app.pokeplan.base.BaseData
import app.pokeplan.link.LinkData
import app.pokeplan.app.link.LinkReminders
import app.pokeplan.app.upcomingReminders
import app.pokeplan.app.world.worldSettle
import app.pokeplan.care.CareData
import app.pokeplan.core.pokemon.ItemCategory
import app.pokeplan.core.pokemon.Items
import app.pokeplan.ui.App
import app.pokeplan.ui.AppLaunch
import app.pokeplan.ui.Place
import app.pokeplan.ui.screens.RecoveryChoice
import app.pokeplan.ui.screens.StartupLoading
import app.pokeplan.reminders.AppAlerts
import app.pokeplan.reminders.SharedReminders
import app.pokeplan.reminders.TASK_REMINDERS_PREFS
import app.pokeplan.widgets.announceBuddyEvent
import app.pokeplan.widgets.updateWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import app.pokeplan.health.AndroidHealthConnect
import app.pokeplan.platform.AccelerometerMotion
import app.pokeplan.platform.AndroidCalendars
import app.pokeplan.platform.AndroidCapabilities
import app.pokeplan.platform.AndroidContacts
import app.pokeplan.platform.AndroidCryPlayer
import app.pokeplan.platform.AndroidDeviceGate
import app.pokeplan.platform.AndroidFileSaver
import app.pokeplan.platform.AndroidHaptics
import app.pokeplan.platform.AndroidPhoneState
import app.pokeplan.platform.AndroidSensorFeed
import app.pokeplan.platform.AndroidImagePicker
import app.pokeplan.platform.AndroidTextFiles
import app.pokeplan.platform.AndroidTorch
import app.pokeplan.platform.androidGearServices

class MainActivity : ComponentActivity() {
    private val resumes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val launches = MutableSharedFlow<AppLaunch>(extraBufferCapacity = 1)
    private var created = false

    private var widgetSignature: Int? = null
    private var widgetRefresh: Job? = null

    /** A change since the widgets were last brought up to date; what makes the update on leaving the app worth doing. */
    private var widgetsDirty = false
    private var reminderSignature: Int? = null
    private var reminderRefresh: Job? = null
    private var appData: AppData? = null
    private var deviceGate: AndroidDeviceGate? = null
    private var careData: CareData? = null
    private var routineSignature: Int? = null
    private var careWidgetSignature: Int? = null
    private var routineRefresh: Job? = null

    private val torch by lazy { AndroidTorch(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        window.isNavigationBarContrastEnforced = false
        val motion = AccelerometerMotion(this)
        val calendars = AndroidCalendars(this)
        val contacts = AndroidContacts(this)
        val gearServices = androidGearServices(this)
        val sensors = AndroidSensorFeed(this)
        val backups = AndroidBackups(this)
        val gate = AndroidDeviceGate(this).also { deviceGate = it }
        val health = AndroidHealthConnect(this)
        val textFiles = AndroidTextFiles(this)
        val images = AndroidImagePicker(this)
        val updates = app.pokeplan.platform.AndroidUpdates(this)
        val first = launchFrom(intent)
        val place = first.place ?: Place.HOME
        setContent {
            var ready by remember { mutableStateOf(false) }
            var recovery by remember { mutableStateOf(false) }
            // Opening the database (integrity check, migrations, Keystore keys) is IO and CPU; do it off the main thread
            // while a small branded screen is shown, instead of blocking the first frame of the app.
            LaunchedEffect(Unit) {
                val app = applicationContext
                val pending = withContext(Dispatchers.IO) {
                    val needsChoice = LiveStore.recoveryPending(app)
                    if (!needsChoice) LiveStore.get(app)
                    needsChoice
                }
                recovery = pending
                ready = true
                // The daily copy is WorkManager's job now; ask for a due one at every start (KEEP: at most one is queued).
                if (!pending) runCatching { BackupWorker.runSoon(app) }
            }
            when {
                !ready -> StartupLoading()
                recovery -> RecoveryChoice(
                    olderFromMillis = LiveStore.olderFilesMillis(this@MainActivity),
                    onOlder = { LiveStore.resolveRecovery(this@MainActivity, useOlder = true); recreate() },
                    onFresh = { LiveStore.resolveRecovery(this@MainActivity, useOlder = false); recreate() },
                )
                else -> {
                    // Opened once: asking the LiveStore for each document on every recomposition would be a handful of
                    // meta queries and wrappers each time the theme or a dialog changes.
                    val storage = remember { saveStorage(this@MainActivity) }
                    val care = remember { careStorage(this@MainActivity) }
                    val heal = remember { healStorage(this@MainActivity) }
                    val mint = remember { mintStorage(this@MainActivity) }
                    val base = remember { baseStorage(this@MainActivity) }
                    val link = remember { linkStorage(this@MainActivity) }
                    App(
                        storage, careStorage = care, healStorage = heal, mintStorage = mint, baseStorage = base, linkStorage = link, focusTab = true, motion = motion, start = place, startPokemon = first.pokemon, startAddTask = first.addTask, startSharedText = first.sharedText, launches = launches, externalChanges = resumes,
                        cries = AndroidCryPlayer(this@MainActivity), haptics = AndroidHaptics(this@MainActivity), files = AndroidFileSaver(this@MainActivity), calendars = calendars, phone = AndroidPhoneState(this@MainActivity), torch = torch, capabilities = AndroidCapabilities(this@MainActivity), sensors = sensors, services = gearServices, backups = backups, deviceGate = gate, health = health, textFiles = textFiles, images = images, imageViewer = app.pokeplan.platform.AndroidImageViewer(this@MainActivity), appLauncher = app.pokeplan.platform.AndroidAppLauncher(this@MainActivity), updates = updates, huawei = app.pokeplan.huawei.AndroidHuaweiReader(this@MainActivity), contacts = contacts, onDataChanged = ::keepWidgetsCurrent, onCareChanged = ::careChanged, onHealChanged = ::healChanged, onMintChanged = ::mintChanged, onBaseChanged = ::homeChanged, onLinkChanged = ::linkChanged, onDark = ::matchSystemBars, asyncModel = true,
                    )
                }
            }
        }
    }

    private fun matchSystemBars(dark: Boolean) {
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = !dark
    }

    private fun keepWidgetsCurrent(data: AppData) {
        keepScreenOn(data.focus != null)
        keepRemindersCurrent(data)
        // Any change to the save (World's timers, a task, the buddy) may show on a widget, whether or not it moved the signature below.
        if (appData != null && data !== appData) widgetsDirty = true
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
        widgetsDirty = true
        widgetRefresh?.cancel()
        widgetRefresh = lifecycleScope.launch {
            delay(800)
            withContext(Dispatchers.IO) { updateWidgets(applicationContext) }
            widgetsDirty = false
        }
    }

    private fun keepRemindersCurrent(data: AppData) {
        val now = System.currentTimeMillis()
        val zone = TimeZone.currentSystemDefault()
        val upcoming = data.upcomingReminders(now, zone)
        val alerts = AppAlerts.plan(data, now, zone)
        if (upcoming.isNotEmpty() || alerts.sessionId != null || alerts.timer != null || data.activeDays.isNotEmpty()) askForNotifications()
        val s = data.settings
        val nudges = listOf(s.streakReminder, s.streakReminderHour, s.weeklyRecap, s.quietHours)
        val signature = listOf(upcoming.map { it.taskId to it.atMillis }, alerts, nudges).hashCode()
        if (signature == reminderSignature) return
        reminderSignature = signature
        reminderRefresh?.cancel()
        reminderRefresh = lifecycleScope.launch {
            delay(800)
            withContext(Dispatchers.IO) {
                SharedReminders.reschedule(applicationContext)
                AppAlerts.schedule(applicationContext, alerts)
            }
        }
    }

    private fun careChanged(data: CareData) {
        careData = data
        keepRoutineRemindersCurrent()
        val signature = listOf(data.routines.map { listOf(it.id, it.title, it.icon, it.time, it.days, it.archived, it.completions.maxOrNull()) }, data.diary.maxOfOrNull { it.day }).hashCode()
        if (careWidgetSignature == null) {
            careWidgetSignature = signature
            return
        }
        if (signature == careWidgetSignature) return
        careWidgetSignature = signature
        widgetsDirty = true
        widgetRefresh?.cancel()
        widgetRefresh = lifecycleScope.launch {
            delay(800)
            withContext(Dispatchers.IO) { updateWidgets(applicationContext) }
            widgetsDirty = false
        }
    }

    private var healSignature: Int? = null
    private var healRefresh: Job? = null

    private fun healChanged(data: HealData) {
        val app = appData ?: return
        val upcoming = HealReminders.upcoming(data, app, System.currentTimeMillis(), TimeZone.currentSystemDefault())
        if (upcoming.isNotEmpty()) askForNotifications()
        val signature = upcoming.map { it.ref to it.atMillis }.hashCode()
        if (healSignature == null) { healSignature = signature; if (upcoming.isEmpty()) return }
        else if (signature == healSignature) return
        healSignature = signature
        healRefresh?.cancel()
        healRefresh = lifecycleScope.launch {
            delay(800)
            withContext(Dispatchers.IO) { SharedReminders.reschedule(applicationContext) }
        }
    }

    private var mintSignature: Int? = null
    private var lastMint: MintData? = null
    private var mintRefresh: Job? = null

    private fun mintChanged(data: MintData) {
        // The Mint widgets show balances and bills that are not part of the main signature.
        if (lastMint != null && data !== lastMint) widgetsDirty = true
        lastMint = data
        val app = appData ?: return
        val upcoming = MintReminders.upcoming(data, app, System.currentTimeMillis(), TimeZone.currentSystemDefault())
        if (upcoming.isNotEmpty()) askForNotifications()
        val signature = upcoming.map { it.billId to it.atMillis }.hashCode()
        if (mintSignature == null) { mintSignature = signature; if (upcoming.isEmpty()) return }
        else if (signature == mintSignature) return
        mintSignature = signature
        mintRefresh?.cancel()
        mintRefresh = lifecycleScope.launch {
            delay(800)
            withContext(Dispatchers.IO) { SharedReminders.reschedule(applicationContext) }
        }
    }

    private var homeSignature: Int? = null
    private var homeRefresh: Job? = null

    private fun homeChanged(data: BaseData) {
        val app = appData ?: return
        val upcoming = BaseReminders.upcoming(data, app, System.currentTimeMillis(), TimeZone.currentSystemDefault())
        if (upcoming.isNotEmpty()) askForNotifications()
        val signature = upcoming.map { Triple(it.recordId, it.kind, it.atMillis) }.hashCode()
        if (homeSignature == null) { homeSignature = signature; if (upcoming.isEmpty()) return }
        else if (signature == homeSignature) return
        homeSignature = signature
        homeRefresh?.cancel()
        homeRefresh = lifecycleScope.launch {
            delay(800)
            withContext(Dispatchers.IO) { SharedReminders.reschedule(applicationContext) }
        }
    }

    private var linkSignature: Int? = null
    private var linkRefresh: Job? = null

    private fun linkChanged(data: LinkData) {
        val app = appData ?: return
        val upcoming = LinkReminders.upcoming(data, app, System.currentTimeMillis(), TimeZone.currentSystemDefault())
        if (upcoming.isNotEmpty() || (data.settings.reminders && (data.dates.any { it.remindDays != null } || data.followUps.any { it.remind }))) askForNotifications()
        val signature = upcoming.map { Triple(it.recordId, it.kind, it.atMillis) }.hashCode()
        if (linkSignature == null) { linkSignature = signature; if (upcoming.isEmpty()) return }
        else if (signature == linkSignature) return
        linkSignature = signature
        linkRefresh?.cancel()
        linkRefresh = lifecycleScope.launch {
            delay(800)
            withContext(Dispatchers.IO) { SharedReminders.reschedule(applicationContext) }
        }
    }

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
            withContext(Dispatchers.IO) { SharedReminders.reschedule(applicationContext) }
        }
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences(TASK_REMINDERS_PREFS, MODE_PRIVATE)
        if (prefs.getBoolean("asked", false)) return
        prefs.edit().putBoolean("asked", true).apply()
        runOnUiThread { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) }
    }

    private fun keepScreenOn(on: Boolean) = runOnUiThread {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun launchFrom(intent: Intent?): AppLaunch {
        val shared = intent?.takeIf { it.action == Intent.ACTION_SEND && it.type?.startsWith("text/") == true }
            ?.let { it.getStringExtra(Intent.EXTRA_TEXT) ?: it.getStringExtra(Intent.EXTRA_SUBJECT) }?.takeIf { it.isNotBlank() }
        return AppLaunch(
            place = if (shared != null) Place.PLANNER else intent?.getStringExtra(EXTRA_TAB)?.let { name -> Place.entries.firstOrNull { it.name == name } },
            pokemon = intent?.getStringExtra(EXTRA_POKEMON),
            addTask = intent?.extras?.get(EXTRA_ADD_TASK)?.toString() == "true",
            sharedText = shared,
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launches.tryEmit(launchFrom(intent))
    }

    override fun onResume() {
        super.onResume()
        app.pokeplan.huawei.AndroidHuaweiReader(this).sync()
        val first = !created
        created = true
        lifecycleScope.launch {
            // Expeditions settle from elapsed time. Coming back to the foreground is one of the two moments worth
            // settling at (the other is the floor alarm), and it replaces the timer the World screen used to run:
            // read and settle off the main thread, then let the open screen reload the settled save.
            withContext(Dispatchers.IO) { runCatching { widgetStore(applicationContext).worldSettle() } }
            if (!first) resumes.tryEmit(Unit)
        }
    }

    override fun onStop() {
        torch.release()
        deviceGate?.left()
        super.onStop()
        val dirty = widgetsDirty
        lifecycleScope.launch {
            announceBuddyEvent(applicationContext, updateWhenQuiet = dirty)
            if (dirty) widgetsDirty = false
        }
    }

    companion object {
        const val EXTRA_TAB = "app.pokeplan.TAB"
        const val EXTRA_POKEMON = "app.pokeplan.POKEMON"
        const val EXTRA_ADD_TASK = "app.pokeplan.ADD_TASK"
    }
}
