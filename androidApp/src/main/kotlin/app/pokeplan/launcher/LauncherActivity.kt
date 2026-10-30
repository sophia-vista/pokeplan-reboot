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

    /** How many times Home was pressed while this was open: the screens go back to the home page on each. */
    private var homePresses by mutableIntStateOf(0)
    private var reloading: Job? = null
    private val icons = object : LruCache<String, ImageBitmap>(256) {}

    private val packageChanges = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reloadApps()
        override fun onPackageAdded(packageName: String, user: UserHandle) = reloadApps()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reloadApps()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reloadApps()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reloadApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        users = getSystemService(Context.USER_SERVICE) as UserManager
        prefs = LauncherPrefs.decode(getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREFS_KEY, null))
        val model = AppModel(store, cries = AndroidCryPlayer(this), haptics = AndroidHaptics(this))
        launcherApps.registerCallback(packageChanges)
        reloadApps()
        setContent { LauncherScreen(model, apps, prefs, ::savePrefs, isDefault, this, homePresses) }
        keepFocusScreen()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while this is the home app: back to the home page, from the app list, a sheet or the town picker.
        homePresses++
        keepFocusScreen()
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
        if (store.data.focus != null) openApp(Place.FOCUS)
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
            icons.evictAll()
            apps = found.map { info ->
                val label = info.label?.toString().orEmpty().ifBlank { info.componentName.packageName }
                val removable = info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0 || info.applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                LauncherApp(
                    key(info), label,
                    AppCategory.guess(info.applicationInfo.category, info.componentName.packageName, label, system = !removable),
                    removable = removable,
                )
            }
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
                info.getBadgedIcon(0).apply { setBounds(0, 0, size, size) }.draw(Canvas(bitmap))
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

    override fun openHomeSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    override fun openApp(place: Place) {
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, place.name))
    }

    private companion object {
        const val PREFS = "launcher"
        const val PREFS_KEY = "prefs"
    }
}
