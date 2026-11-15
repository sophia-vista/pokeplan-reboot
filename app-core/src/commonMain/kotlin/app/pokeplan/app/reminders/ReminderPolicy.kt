package app.pokeplan.app.reminders

import app.pokeplan.plan.QuietHours
import app.pokeplan.plan.Reminders
import app.pokeplan.plan.toEpochMillis
import app.pokeplan.plan.toLocalDateTime
import kotlinx.datetime.TimeZone
import app.pokeplan.app.plan.NudgeText

/** The modules that may ask for a reminder (INTEGRATIONS_AND_PRIVACY.md, section 9.1). */
enum class ReminderModule { PLAN, CARE, HEAL, MINT, BASE, LINK;
    companion object {
        /** The module a stored name means (a held reminder or an alarm written before the 13 November 2026 rename says "HOME"), or null. */
        fun parse(name: String?): ReminderModule? = if (name == "HOME") BASE else entries.firstOrNull { it.name == name }
    }
}

/**
 * One reminder a module asks for (section 9.2.2). It holds only what scheduling needs: an opaque [sourceRef] (never a title,
 * note, amount or medication), the [kind] of reminder and the instant the module wants. The words shown are resolved from the
 * owning module at delivery time, never stored here.
 */
data class ReminderRequest(
    val module: ReminderModule,
    val sourceRef: String,
    val kind: String,
    val atMillis: Long,
    /** Held while a focus session runs. Every module is deferrable unless the owner approved an exception (section 9.5). */
    val deferrable: Boolean = true,
    /** Tells two occurrences of the same source apart (a day, a bill period); empty when the source has one reminder. */
    val occurrence: String = "",
) {
    /** The stable identity: the same request always has the same id, so submitting it twice sets one alarm (section 9.3). */
    val id: String get() = listOf(module.name, sourceRef, kind, occurrence).joinToString("|")
}

/** A focus session as the reminder feature sees it (section 9.5): only when it runs, never a Plan class. */
data class FocusWindow(val startMillis: Long, val endMillis: Long) {
    operator fun contains(t: Long) = t in startMillis until endMillis
}

/**
 * Delivery rules shared by every module (section 9.3 to 9.5), pure and free of Android. A module toggle can only remove its
 * requests; nothing here lets a module skip the global quiet hours.
 */
object ReminderPolicy {
    /** Passes needed to settle: a focus end inside quiet hours moves to its end, which a single focus window cannot cover again. */
    private const val MAX_PASSES = 4

    /**
     * When [request] may be delivered: held to the end of a focus window it falls in (if deferrable), then out of the global
     * [quiet] hours to their end, and checked again until it is stable (focus ending in quiet hours cannot loop).
     * Never earlier than requested.
     */
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

    /**
     * The requests to have scheduled after [nowMillis]: duplicates by id dropped (the last one wins, so an edit replaces the
     * old request), delivery times applied, nothing in the past or beyond [horizonMillis], earliest first. Returns the request
     * with its delivery instant.
     */
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

/**
 * What each module supplies to the shared reminder feature (section 9.2.3). The scheduler talks only to this, never to a
 * module's repository, and a notification action reaches the source only through [execute].
 */
interface ReminderSourceAdapter {
    val module: ReminderModule

    /** The module's current requests, rebuilt from its own records (recovery after reboot, update or a clock change). */
    fun requests(nowMillis: Long, zone: TimeZone): List<ReminderRequest>

    /** Whether the source still exists and wants this reminder; checked before delivery and before any action. */
    fun isStillWanted(request: ReminderRequest): Boolean

    /** The short text to show, resolved at delivery time; null when the source is gone. */
    fun present(request: ReminderRequest): NudgeText?

    /** Runs a stable action key ("done", "snooze") in the owning module; false when it is stale or not allowed. */
    fun execute(request: ReminderRequest, action: String): Boolean

    /** The reminder to hold after [request] was shown (an unanswered-reminder repeat), or null. The source's own policy, bounded. */
    fun followUp(request: ReminderRequest, nowMillis: Long): ReminderRequest? = null
}
