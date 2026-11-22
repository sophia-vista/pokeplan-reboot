package app.pokeplan.ui

import app.pokeplan.app.ProviderAvailability
import app.pokeplan.heal.HealthKind
import app.pokeplan.heal.ProviderRead

interface HealthSource {
    fun availability(): ProviderAvailability

    fun granted(done: (Set<HealthKind>) -> Unit)

    fun request(kinds: Set<HealthKind>, done: (Set<HealthKind>) -> Unit)

    fun read(kinds: Set<HealthKind>, days: Int, done: (ProviderRead?, String?) -> Unit)

    fun revoke(done: (Boolean) -> Unit)

    fun openSettings()
}

interface TextFileChooser {
    fun choose(done: (name: String?, text: String?, problem: String?) -> Unit)
}

interface ImageChooser {
    fun choose(done: (png: ByteArray?, problem: String?) -> Unit)
}

interface ImageViewer {
    fun bright(on: Boolean)

    fun share(png: ByteArray, name: String): String
}

data class InstalledApp(val label: String, val packageName: String)

interface AppLauncher {
    fun installed(): List<InstalledApp>

    fun label(packageName: String): String?

    fun open(packageName: String): Boolean
}

data class HuaweiReaderState(
    val installed: Boolean,
    val screenReading: Boolean,
    val notificationReading: Boolean,
    val walking: Boolean,
    val canSwitchItself: Boolean = false,
)

interface HuaweiReader {
    fun state(): HuaweiReaderState

    fun seen(): app.pokeplan.app.heal.HuaweiSeen

    fun clear()

    /**
     * Opens Huawei Health, walks it (INTEGRATIONS_AND_PRIVACY.md 5.2.2, rule 3) and comes back; screen reading is turned on first when [HuaweiReaderState.canSwitchItself]
     * and is off again afterwards. [known] is what HEAL already holds, so a read skips what has nothing new. [done] gets null, or what stopped it in words, on the main thread.
     */
    fun walk(known: app.pokeplan.app.heal.HuaweiWalk.Known, done: (String?) -> Unit)

    fun turnOff()

    fun openScreenReadingSettings()

    fun openNotificationSettings()

    fun openAppInfo()
}
