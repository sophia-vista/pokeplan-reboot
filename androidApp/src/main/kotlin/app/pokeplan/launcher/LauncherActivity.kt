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
import app.pokeplan.platform.AndroidCryPlayer
import app.pokeplan.platform.AndroidHaptics
import app.pokeplan.MainActivity
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.Place
import app.pokeplan.ui.launcher.AppCategory
import app.pokeplan.ui.launcher.HostedWidgetSpec
import app.pokeplan.ui.launcher.LauncherApp
import app.pokeplan.ui.launcher.LauncherHost
import app.pokeplan.ui.launcher.LauncherPrefs
import app.pokeplan.ui.launcher.LauncherScreen
import app.pokeplan.ui.launcher.hostedWidgetIds
import app.pokeplan.widgetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherActivity : ComponentActivity(), LauncherHost {
    private lateinit var launcherApps: LauncherApps
    private lateinit var users: UserManager
    private val store by lazy { widgetStore(this) }

    private var entries: Map<String, LauncherActivityInfo> = emptyMap()
    private var apps by mutableStateOf<List<LauncherApp>>(emptyList())
    private var prefs by mutableStateOf(LauncherPrefs())
    private var isDefault by mutableStateOf(false)
    private lateinit var model: AppModel

    private var safeMode = false
    private var safeView: SafeModeView? = null

    private var homePresses by mutableIntStateOf(0)

    private var active by mutableStateOf(true)
    private var widgetEpoch by mutableIntStateOf(0)
    private var reloading: Job? = null
    private val staleIcons = mutableSetOf<String>()
    private var lastReload = 0L
    private val icons = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
    private val specs = HashMap<Int, HostedWidgetSpec?>()

    private val packageChanges = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) { widgetEpoch++; reloadApps() }
        override fun onPackageAdded(packageName: String, user: UserHandle) { staleIcons += packageName; widgetEpoch++; reloadApps() }
        override fun onPackageChanged(packageName: String, user: UserHandle) { staleIcons += packageName; widgetEpoch++; reloadApps() }
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) { widgetEpoch++; reloadApps() }
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) { widgetEpoch++; reloadApps() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        users = getSystemService(Context.USER_SERVICE) as UserManager
        if (app.pokeplan.CrashGuard.safeMode(this)) {
            safeMode = true
            launcherApps.registerCallback(packageChanges)
            reloadApps()
            val view = SafeModeView(
                this, ::open,
                onCopyLog = { app.pokeplan.CrashGuard.copyLog(this) },
                onTryAgain = { app.pokeplan.CrashGuard.clear(this); recreate() },
            )
            safeView = view
            setContentView(view.root)
            return
        }
        prefs = LauncherPrefs.decode(getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREFS_KEY, null))
        model = AppModel(store, cries = AndroidCryPlayer(this), haptics = AndroidHaptics(this), calendars = app.pokeplan.platform.AndroidCalendars(this))
        launcherApps.registerCallback(packageChanges)
        releaseUnusedWidgetId()
        reloadApps()
        setContent { LauncherScreen(model, apps, prefs, ::savePrefs, isDefault, this, homePresses, active) }
        keepFocusScreen()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homePresses++
        if (!safeMode) keepFocusScreen()
    }

    override fun onStart() {
        super.onStart()
        active = true
        app.pokeplan.huawei.HuaweiComponents.rebind(this)
        if (safeMode) return
        try { widgetHost.startListening() } catch (e: Exception) {  }
    }

    override fun onStop() {
        active = false
        if (safeMode) return super.onStop()
        try { widgetHost.stopListening() } catch (e: Exception) { }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (safeMode) return reloadApps()
        reloadSave()
        isDefault = isDefaultHome()
        reloadApps()
    }

    override fun onDestroy() {
        launcherApps.unregisterCallback(packageChanges)
        super.onDestroy()
    }

    private fun reloadSave() {
        val now = System.currentTimeMillis()
        if (now - lastReload < 400) return
        lastReload = now
        store.reload()
    }

    private fun keepFocusScreen() {
        reloadSave()
        if (store.data.focus?.isFinished == false) {
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

    private fun reloadApps() {
        reloading?.cancel()
        reloading = lifecycleScope.launch {
            delay(150)
            val found = withContext(Dispatchers.Default) {
                try {
                    users.userProfiles.flatMap { user -> launcherApps.getActivityList(null, user) }
                } catch (e: Exception) {
                    null
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
            if (next == apps && staleIcons.isEmpty()) return@launch
            val stale = staleIcons.toSet()
            staleIcons.clear()
            icons.snapshot().keys.filter { it !in entries || stale.any { pkg -> it.startsWith("$pkg/") } }.forEach { icons.remove(it) }
            specs.clear()
            apps = next
            safeView?.show(next)
        }
    }

    private fun key(info: LauncherActivityInfo) = "${info.componentName.flattenToString()}|${users.getSerialNumberForUser(info.user)}"

    override suspend fun icon(key: String): ImageBitmap? {
        icons.get(key)?.let { return it }
        val info = entries[key] ?: return null
        return withContext(Dispatchers.Default) {
            try {
                val size = (resources.displayMetrics.density * 40).toInt().coerceAtLeast(40)
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val drawable = info.getBadgedIcon(0)
                if (drawable is android.graphics.drawable.AdaptiveIconDrawable) {
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
            reloadApps()
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

    private val widgetManager by lazy { android.appwidget.AppWidgetManager.getInstance(this) }
    private val widgetHost by lazy { android.appwidget.AppWidgetHost(this, WIDGET_HOST_ID) }
    private var pendingWidget: Int = -1
    private var onWidgetPicked: ((Int) -> Unit)? = null

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != CONFIGURE_WIDGET) return
        val id = pendingWidget
        if (id < 0) return
        if (resultCode == RESULT_OK) onWidgetPicked?.invoke(id) else widgetHost.deleteAppWidgetId(id)
        setPendingWidget(-1)
    }

    private val chooseWidget = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        val recovered = pendingWidget < 0
        val id = if (recovered) getSharedPreferences(PREFS, MODE_PRIVATE).getInt(PREFS_PENDING_WIDGET, -1) else pendingWidget
        if (recovered || result.resultCode != RESULT_OK || id < 0) { if (id >= 0) widgetHost.deleteAppWidgetId(id); setPendingWidget(-1); return@registerForActivityResult }
        val info = widgetManager.getAppWidgetInfo(id)
        if (info?.configure != null) {
            try {
                widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, CONFIGURE_WIDGET, null)
            } catch (e: Exception) {
                onWidgetPicked?.invoke(id)
                setPendingWidget(-1)
            }
        } else {
            onWidgetPicked?.invoke(id)
            setPendingWidget(-1)
        }
    }

    private fun setPendingWidget(id: Int) {
        pendingWidget = id
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(PREFS_PENDING_WIDGET, id).apply()
        if (id < 0) onWidgetPicked = null
    }

    private fun releaseUnusedWidgetId() {
        val saved = getSharedPreferences(PREFS, MODE_PRIVATE)
        val id = saved.getInt(PREFS_PENDING_WIDGET, -1)
        if (id >= 0 && id !in prefs.hostedWidgetIds()) widgetHost.deleteAppWidgetId(id)
        if (id >= 0) saved.edit().putInt(PREFS_PENDING_WIDGET, -1).apply()
    }

    override fun pickWidget(onPicked: (Int) -> Unit) {
        val id = widgetHost.allocateAppWidgetId()
        setPendingWidget(id)
        onWidgetPicked = onPicked
        try {
            chooseWidget.launch(Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_PICK).putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        } catch (e: Exception) {
            widgetHost.deleteAppWidgetId(id)
            setPendingWidget(-1)
        }
    }

    override fun releaseWidget(id: Int) = widgetHost.deleteAppWidgetId(id)

    override fun widgetLabel(id: Int): String? { widgetEpoch; return widgetManager.getAppWidgetInfo(id)?.loadLabel(packageManager) }

    override val hostsWidgets: Boolean get() = true

    override fun widgetSpec(id: Int): HostedWidgetSpec? {
        widgetEpoch
        if (specs.containsKey(id)) return specs[id]
        return readSpec(id).also { specs[id] = it }
    }

    private fun readSpec(id: Int): HostedWidgetSpec? {
        val info = widgetManager.getAppWidgetInfo(id) ?: return null
        val density = resources.displayMetrics.density
        fun dp(px: Int) = (px / density).toInt()
        val max = android.os.Build.VERSION.SDK_INT >= 31
        return HostedWidgetSpec(
            dp(info.minWidth), dp(info.minHeight), dp(info.minResizeWidth), dp(info.minResizeHeight),
            resizeHorizontal = info.resizeMode and android.appwidget.AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0,
            resizeVertical = info.resizeMode and android.appwidget.AppWidgetProviderInfo.RESIZE_VERTICAL != 0,
            maxResizeWidthDp = if (max) dp(info.maxResizeWidth) else 0, maxResizeHeightDp = if (max) dp(info.maxResizeHeight) else 0,
        )
    }

    @androidx.compose.runtime.Composable
    override fun HostedWidget(id: Int, size: Int, widthDp: Int, heightDp: Int, modifier: androidx.compose.ui.Modifier) {
        val info = widgetManager.getAppWidgetInfo(id) ?: return
        val spec = widgetSpec(id) ?: return
        val onGrid = heightDp > 0 && widthDp > 0
        val height = if (onGrid) heightDp else (spec.minHeightDp.coerceAtLeast(48) * (1f + size.coerceIn(0, 2) / 2f)).toInt().coerceAtMost(640)
        androidx.compose.runtime.key(id, info.provider) {
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
                    if (onGrid) view.tellSize(widthDp, heightDp, widthDp, heightDp)
                    else view.tellSize(spec.minWidthDp, spec.minHeightDp, spec.minWidthDp * 2, height)
                },
            )
        }
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

    override fun openBaseSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    override fun openPokemon(id: String) {
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, Place.COLLECTION.name).putExtra(MainActivity.EXTRA_POKEMON, id))
    }

    @android.annotation.SuppressLint("WrongConstant")
    override fun expandNotifications() {
        try {
            val bar = getSystemService("statusbar") ?: return
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(bar)
        } catch (e: Exception) {  }
    }

    override fun openApp(place: Place) {
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, place.name))
    }

    private companion object {
        const val PREFS = "launcher"
        const val PREFS_KEY = "prefs"
        const val PREFS_PENDING_WIDGET = "pendingWidget"
        const val WIDGET_HOST_ID = 4242
        const val CONFIGURE_WIDGET = 4243
    }
}

private fun android.appwidget.AppWidgetHostView.tellSize(minW: Int, minH: Int, maxW: Int, maxH: Int) {
    if (android.os.Build.VERSION.SDK_INT >= 31) {
        updateAppWidgetSize(android.os.Bundle(), listOf(android.util.SizeF(minW.toFloat(), minH.toFloat()), android.util.SizeF(maxW.toFloat(), maxH.toFloat())))
    } else {
        @Suppress("DEPRECATION")
        updateAppWidgetSize(null, minW, minH, maxW, maxH)
    }
}
