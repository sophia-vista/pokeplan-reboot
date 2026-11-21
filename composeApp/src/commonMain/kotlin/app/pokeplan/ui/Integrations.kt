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

    fun walk(done: (String?) -> Unit)

    fun turnOff()

    fun openScreenReadingSettings()

    fun openNotificationSettings()

    fun openAppInfo()
}
