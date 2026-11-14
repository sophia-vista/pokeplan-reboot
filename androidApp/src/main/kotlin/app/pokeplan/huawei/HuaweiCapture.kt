package app.pokeplan.huawei

import app.pokeplan.app.HuaweiProbe
import app.pokeplan.app.HuaweiSeen
import app.pokeplan.app.NotificationCapture
import app.pokeplan.app.ScreenCapture

/**
 * What the Huawei Health readers saw, in this process's memory only (INTEGRATIONS_AND_PRIVACY.md 5.2.2, probe stage): never written to disk, never logged,
 * gone when the app's process ends. The two services and the app run in one process, so they share it.
 */
internal object HuaweiCapture {
    private val probe = HuaweiProbe()

    /** The connected screen reader, or null while it is off. */
    @Volatile var screen: HuaweiScreenService? = null

    fun seen(): HuaweiSeen = synchronized(probe) { probe.seen }
    fun addScreen(c: ScreenCapture): Boolean = synchronized(probe) { probe.addScreen(c) }
    fun addNotification(n: NotificationCapture) = synchronized(probe) { probe.addNotification(n) }
    fun clear() = synchronized(probe) { probe.clear() }
}
