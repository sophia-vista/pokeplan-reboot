package app.pokeplan.app.reminders

import app.pokeplan.plan.FocusSession
import app.pokeplan.plan.QuietHours
import app.pokeplan.plan.Reminders
import app.pokeplan.plan.toEpochMillis
import app.pokeplan.plan.toLocalDateTime
import kotlinx.datetime.TimeZone
import app.pokeplan.app.plan.NudgeText

enum class ReminderModule { PLAN, CARE, HEAL, MINT, BASE, LINK;
    companion object {
        fun parse(name: String?): ReminderModule? = if (name == "HOME") BASE else entries.firstOrNull { it.name == name }
    }
}

data class ReminderRequest(
    val module: ReminderModule,
    val sourceRef: String,
    val kind: String,
    val atMillis: Long,
    val deferrable: Boolean = true,
    val occurrence: String = "",
) {
    val id: String get() = listOf(module.name, sourceRef, kind, occurrence).joinToString("|")
}

data class FocusWindow(val startMillis: Long, val endMillis: Long) {
    operator fun contains(t: Long) = t in startMillis until endMillis
}

object ReminderPolicy {
    private const val MAX_PASSES = 4

    /** The stretch a running focus session holds, so a reminder waits for it to end; null when nothing is running. */
    fun focusWindow(session: FocusSession?): FocusWindow? =
        session?.takeIf { !it.isFinished }?.let { FocusWindow(it.startedAtMillis, it.startedAtMillis + it.durationMillis + it.pausedMillis) }

    fun deliverAt(request: ReminderRequest, quiet: QuietHours, zone: TimeZone, focus: FocusWindow?): Long {
        var at = request.atMillis
        repeat(MAX_PASSES) {
            val before = at
            if (request.deferrable && focus != null && at in focus) at = focus.endMillis
            at = Reminders.outOfQuietHours(at.toLocalDateTime(zone), quiet).toEpochMillis(zone)
            if (at == before) return at
        }
        return at
    }

    fun plan(
        requests: List<ReminderRequest>,
        nowMillis: Long,
        quiet: QuietHours,
        zone: TimeZone,
        focus: FocusWindow?,
        horizonMillis: Long = Long.MAX_VALUE,
    ): List<Pair<ReminderRequest, Long>> = requests.associateBy { it.id }.values
        .map { it to deliverAt(it, quiet, zone, focus) }
        .filter { (_, at) -> at > nowMillis && at <= horizonMillis }
        .sortedWith(compareBy({ it.second }, { it.first.id }))
}

interface ReminderSourceAdapter {
    val module: ReminderModule

    fun requests(nowMillis: Long, zone: TimeZone): List<ReminderRequest>

    fun isStillWanted(request: ReminderRequest): Boolean

    fun present(request: ReminderRequest): NudgeText?

    fun execute(request: ReminderRequest, action: String): Boolean

    fun followUp(request: ReminderRequest, nowMillis: Long): ReminderRequest? = null
}
