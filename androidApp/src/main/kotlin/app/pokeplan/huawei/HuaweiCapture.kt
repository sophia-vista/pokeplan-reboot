package app.pokeplan.huawei

import app.pokeplan.app.heal.HuaweiProbe
import app.pokeplan.app.heal.HuaweiSeen
import app.pokeplan.app.heal.HuaweiVisit
import app.pokeplan.app.heal.NotificationCapture
import app.pokeplan.app.heal.ScreenCapture
internal object HuaweiCapture {
    private val probe = HuaweiProbe()

    @Volatile var screen: HuaweiScreenService? = null

    fun seen(): HuaweiSeen = synchronized(probe) { probe.seen }
    fun addScreen(c: ScreenCapture): Boolean = synchronized(probe) { probe.addScreen(c) }
    fun addNotification(n: NotificationCapture) = synchronized(probe) { probe.addNotification(n) }
    fun addVisit(v: HuaweiVisit) = synchronized(probe) { probe.addVisit(v) }
    fun log(line: String) = synchronized(probe) { probe.addLog(line) }
    fun clear() = synchronized(probe) { probe.clear() }
}
