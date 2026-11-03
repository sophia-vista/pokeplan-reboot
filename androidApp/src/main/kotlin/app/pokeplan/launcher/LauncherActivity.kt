package app.pokeplan.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.lifecycleScope
import app.pokeplan.AndroidCryPlayer
import app.pokeplan.AndroidHaptics
import app.pokeplan.MainActivity
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.Place
import app.pokeplan.ui.launcher.AppCategory
import app.pokeplan.ui.launcher.LauncherApp
import app.pokeplan.ui.launcher.LauncherHost
import app.pokeplan.ui.launcher.LauncherPrefs
import app.pokeplan.ui.launcher.LauncherScreen
import app.pokeplan.widgetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PokéLife as the phone's home screen, opt-in: it does nothing until the user picks it as the home app (the home page
 * has a button to Android's settings for that). The apps come from `LauncherApps` (work profiles included), pins and
 * hidden apps stay on the device, and nothing here pays: the save is only read, and a task ticked here is paid when the
 * app opens, like the widgets'. During a focus session, Home brings the Focus screen back (strict focus).
 */
class LauncherActivity : ComponentActivity(), LauncherHost {
    private lateinit var launcherApps: LauncherApps
    private lateinit var users: UserManager
    private val store by lazy { widgetStore(this) }

    /** The launchable activities by key, for opening; [apps] is the same list in the shape the screens use. */
    private var entries: Map<String, LauncherActivityInfo> = emptyMap()
    private var apps by mutableStateOf<List<LauncherApp>>(emptyList())
    private var prefs by mutableStateOf(LauncherPrefs())
    private var isDefault by mutableStateOf(false)
    private lateinit var model: AppModel

    /** How many times Home was pressed while this was open: the screens go back to the home page on each. */
    private var homePresses by mutableIntStateOf(0)

    /** False while the launcher is out of sight, so the town's Pokémon stop walking and the battery is left alone. */
    private var active by mutableStateOf(true)
    private var reloading: Job? = null
    private var iconsStale = false
    private val icons = object : LruCache<String, ImageBitmap>(256) {}

    private val packageChanges = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reloadApps()
        override fun onPackageAdded(packageName: String, user: UserHandle) { iconsStale = true; reloadApps() }
        override fun onPackageChanged(packageName: String, user: UserHandle) { iconsStale = true; reloadApps() }
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reloadApps()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reloadApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        users = getSystemService(Context.USER_SERVICE) as UserManager
        prefs = LauncherPrefs.decode(getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREFS_KEY, null))
        model = AppModel(store, cries = AndroidCryPlayer(this), haptics = AndroidHaptics(this), calendars = app.pokeplan.AndroidCalendars(this))
        launcherApps.registerCallback(packageChanges)
        reloadApps()
        setContent { LauncherScreen(model, apps, prefs, ::savePrefs, isDefault, this, homePresses, active) }
        keepFocusScreen()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while this is the home app: back to the home page, from the app list, a sheet or the town picker.
        homePresses++
        keepFocusScreen()
    }

    override fun onStart() {
        super.onStart()
        active = true
        try { widgetHost.startListening() } catch (e: Exception) { /* a widget host that cannot listen just shows what it has */ }
    }

    override fun onStop() {
        active = false
        try { widgetHost.stopListening() } catch (e: Exception) { }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // A widget, or the app, may have changed the save, or the user may just have chosen this as the home app.
        store.reload()
        isDefault = isDefaultHome()
        reloadApps()
    }

    override fun onDestroy() {
        launcherApps.unregisterCallback(packageChanges)
        super.onDestroy()
    }

    /** Strict focus: Home during a focus session keeps the Focus screen on instead of leaving it. */
    private fun keepFocusScreen() {
        store.reload()
        if (store.data.focus != null) {
            // The low buzz says Home is shut while the timer runs.
            if (::model.isInitialized) model.effect(Sfx.FOCUS_LOCK)
            openApp(Place.FOCUS)
        }
    }

    private fun isDefaultHome(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
    }

    private fun savePrefs(next: LauncherPrefs) {
        prefs = next
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREFS_KEY, next.encode()).apply()
    }

    /** Reads the app list off the main thread, a moment after the last change so a burst of package events loads it once. */
    private fun reloadApps() {
        reloading?.cancel()
        reloading = lifecycleScope.launch {
            delay(150)
            val found = withContext(Dispatchers.Default) {
                try {
                    users.userProfiles.flatMap { user -> launcherApps.getActivityList(null, user) }
                } catch (e: Exception) {
                    null // keep the list that is showing rather than crash the home screen
                }
            } ?: return@launch
            entries = found.associateBy { key(it) }
            val next = found.map { info ->
                val label = info.label?.toString().orEmpty().ifBlank { info.componentName.packageName }
                val removable = info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0 || info.applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                LauncherApp(
                    key(info), label,
                    AppCategory.guess(info.applicationInfo.category, info.componentName.packageName, label, system = !removable),
                    removable = removable,
                )
            }
            // Home and every return to the launcher reload the list: when nothing changed, keep the list and the drawn icons as they are (re-drawing every icon each time was the slowest part of coming back to the home screen).
            if (next == apps && !iconsStale) return@launch
            // An updated app may have a new icon; one that stayed the same keeps its picture.
            val changed = apps.map { it.key }.toSet() != next.map { it.key }.toSet()
            if (changed || iconsStale) icons.evictAll() else icons.snapshot().keys.filter { it !in entries }.forEach { icons.remove(it) }
            iconsStale = false
            apps = next
        }
    }

    private fun key(info: LauncherActivityInfo) = "${info.componentName.flattenToString()}|${users.getSerialNumberForUser(info.user)}"

    // ----- LauncherHost -----

    override suspend fun icon(key: String): ImageBitmap? {
        icons.get(key)?.let { return it }
        val info = entries[key] ?: return null
        return withContext(Dispatchers.Default) {
            try {
                val size = (resources.displayMetrics.density * 56).toInt().coerceAtLeast(48)
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val drawable = info.getBadgedIcon(0)
                if (drawable is android.graphics.drawable.AdaptiveIconDrawable) {
                    // Without the phone's mask (round or squircle): the layers fill the square, and the launcher cuts it to its own pixel shape. Only the middle two thirds of an adaptive icon are meant to show, so they are drawn 1.5 times as big.
                    val grow = (size * 0.25f).toInt()
                    listOfNotNull(drawable.background, drawable.foreground).forEach { layer -> layer.setBounds(-grow, -grow, size + grow, size + grow); layer.draw(canvas) }
                } else {
                    drawable.setBounds(0, 0, size, size)
                    drawable.draw(canvas)
                }
                bitmap.asImageBitmap().also { icons.put(key, it) }
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun open(key: String) {
        val info = entries[key] ?: return reloadApps()
        try {
            launcherApps.startMainActivity(info.componentName, info.user, null, null)
        } catch (e: Exception) {
            reloadApps() // uninstalled or disabled since the list was read
        }
    }

    override fun appInfo(key: String) {
        val info = entries[key] ?: return
        try {
            launcherApps.startAppDetailsActivity(info.componentName, info.user, null, null)
        } catch (e: Exception) {
            reloadApps()
        }
    }

    override fun uninstall(key: String) {
        val info = entries[key] ?: return
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${info.componentName.packageName}"))
            .putExtra(Intent.EXTRA_USER, info.user).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            appInfo(key)
        }
    }

    // Other apps' widgets: the launcher is their host. The system picker returns a widget id; some widgets then ask to be set up before they can show.
    private val widgetManager by lazy { android.appwidget.AppWidgetManager.getInstance(this) }
    private val widgetHost by lazy { android.appwidget.AppWidgetHost(this, WIDGET_HOST_ID) }
    private var pendingWidget: Int = -1
    private var onWidgetPicked: ((Int) -> Unit)? = null

    private val configureWidget = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pendingWidget
        if (result.resultCode == RESULT_OK) onWidgetPicked?.invoke(id) else widgetHost.deleteAppWidgetId(id)
        pendingWidget = -1
    }

    private val chooseWidget = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pendingWidget
        if (result.resultCode != RESULT_OK || id < 0) { if (id >= 0) widgetHost.deleteAppWidgetId(id); pendingWidget = -1; return@registerForActivityResult }
        val info = widgetManager.getAppWidgetInfo(id)
        if (info?.configure != null) {
            try {
                configureWidget.launch(Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(info.configure).putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            } catch (e: Exception) {
                onWidgetPicked?.invoke(id) // it can be used without its set-up screen
            }
        } else {
            onWidgetPicked?.invoke(id)
            pendingWidget = -1
        }
    }

    override fun pickWidget(onPicked: (Int) -> Unit) {
        val id = widgetHost.allocateAppWidgetId()
        pendingWidget = id
        onWidgetPicked = onPicked
        try {
            chooseWidget.launch(Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_PICK).putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        } catch (e: Exception) {
            widgetHost.deleteAppWidgetId(id)
            pendingWidget = -1
        }
    }

    override fun releaseWidget(id: Int) = widgetHost.deleteAppWidgetId(id)

    override fun widgetLabel(id: Int): String? = widgetManager.getAppWidgetInfo(id)?.loadLabel(packageManager)

    @androidx.compose.runtime.Composable
    override fun HostedWidget(id: Int, size: Int, widthDp: Int, heightDp: Int, modifier: androidx.compose.ui.Modifier) {
        val info = widgetManager.getAppWidgetInfo(id) ?: return
        // On the grid the widget is told the room its cells really give it. In a list it has the height it asks for, times 1, 1.5 or 2 for small, medium and large.
        val onGrid = heightDp > 0 && widthDp > 0
        val height = if (onGrid) heightDp else (info.minHeight.coerceAtLeast(48) * (1f + size.coerceIn(0, 2) / 2f)).toInt().coerceAtMost(640)
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { context ->
                widgetHost.createView(context, id, info).apply {
                    setAppWidget(id, info)
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        if (onGrid) android.view.ViewGroup.LayoutParams.MATCH_PARENT else (height * context.resources.displayMetrics.density).toInt(),
                    )
                }
            },
            modifier = modifier,
            update = { view ->
                if (onGrid) view.updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
                else view.updateAppWidgetSize(null, info.minWidth, info.minHeight, info.minWidth * 2, height)
            },
        )
    }

    private val weather by lazy { WeatherClient(this) }

    override suspend fun forecast(place: app.pokeplan.ui.launcher.GeoPlace) = weather.forecast(place)

    override suspend fun searchPlaces(query: String) = weather.search(query)

    override fun powerSaving(): Boolean = (getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isPowerSaveMode

    override fun battery(): app.pokeplan.ui.launcher.BatteryStatus? {
        val intent = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
        return app.pokeplan.ui.launcher.BatteryStatus(level * 100 / scale, status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL)
    }

    override fun openHomeSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    override fun openPokemon(id: String) {
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, Place.COLLECTION.name).putExtra(MainActivity.EXTRA_POKEMON, id))
    }

    override fun openApp(place: Place) {
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, place.name))
    }

    private companion object {
        const val PREFS = "launcher"
        const val PREFS_KEY = "prefs"
        const val WIDGET_HOST_ID = 4242
    }
}
