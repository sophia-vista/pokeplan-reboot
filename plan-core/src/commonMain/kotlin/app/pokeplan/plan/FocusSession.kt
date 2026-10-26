package app.pokeplan.plan

import kotlinx.serialization.Serializable
import app.pokeplan.core.dex.Region
import app.pokeplan.core.model.PokemonType
import app.pokeplan.core.reward.EffortEvent

enum class PauseReason { CALL, EMERGENCY }

enum class FailReason { LEFT_APP, MOVED_PHONE, GAVE_UP }

/** What a session is doing right now. */
@Serializable
sealed interface Phase {
    @Serializable
    data object Running : Phase

    /** The player left the app; they have [FocusRules.GRACE_MILLIS] from [since] to come back. */
    @Serializable
    data class Away(val since: Long) : Phase

    @Serializable
    data class Paused(val reason: PauseReason, val since: Long) : Phase

    @Serializable
    data class Completed(val at: Long) : Phase

    @Serializable
    data class Failed(val reason: FailReason, val at: Long) : Phase
}

/** Things that happen to a running session. The platform layer turns app lifecycle, calls and sensors into these. */
sealed interface FocusEvent {
    /** Switched to another app or the home screen. */
    data object Left : FocusEvent

    /** Came back to the app. */
    data object Returned : FocusEvent

    /**
     * The screen was locked: this does not count as leaving. The platform may only learn of it just after it reported
     * [Left] (the app pauses as the screen goes off), so a lock during the grace period takes the player back to running.
     */
    data object ScreenLocked : FocusEvent

    data object CallStarted : FocusEvent

    data object CallEnded : FocusEvent

    /** The player started one of their emergency pauses. */
    data object EmergencyPauseStarted : FocusEvent

    data object EmergencyPauseEnded : FocusEvent

    /** The phone moved during a Deep Focus session. */
    data object Moved : FocusEvent

    data object GaveUp : FocusEvent
}

/**
 * A focus session in strict mode. Pure: [apply] takes an event and the current time and returns the next session;
 * [tick] only moves the clock (grace expiring, a long emergency pause ending, the timer finishing).
 * `buddyIdAtStart` and `lure` are locked in when the session starts: perks use the buddy at that moment.
 */
@Serializable
data class FocusSession(
    val id: String,
    val startedAtMillis: Long,
    val durationMillis: Long,
    val deepFocus: Boolean,
    val region: Region,
    val buddyIdAtStart: String?,
    val taskId: String? = null,
    val lure: PokemonType? = null,
    val phase: Phase = Phase.Running,
    /** Time spent paused (calls and emergency pauses), which does not count towards the timer. */
    val pausedMillis: Long = 0,
    /** Emergency pauses already used today, including any in this session. */
    val emergencyPausesUsed: Int = 0,
) {
    val isFinished: Boolean get() = phase is Phase.Completed || phase is Phase.Failed

    /** Focused time so far at [now]: elapsed minus pauses. Time away inside the grace period still counts. */
    fun focusedMillis(now: Long): Long {
        val end = when (val p = phase) {
            is Phase.Completed -> p.at
            is Phase.Failed -> p.at
            else -> now
        }
        val openPause = (phase as? Phase.Paused)?.let { end - it.since } ?: 0
        return (end - startedAtMillis - pausedMillis - openPause).coerceIn(0, durationMillis)
    }

    fun remainingMillis(now: Long): Long = durationMillis - focusedMillis(now)

    /** When the quiet "halfway there" notification fires, for sessions of 15 minutes or more. */
    val halfwayAtMillis: Long? get() = if (durationMillis >= FocusRules.HALFWAY_MIN_MILLIS) startedAtMillis + durationMillis / 2 else null

    fun tick(now: Long): FocusSession {
        if (isFinished) return this
        var s = this
        // An emergency pause that runs past its limit turns into time away.
        (s.phase as? Phase.Paused)?.let { p ->
            if (p.reason == PauseReason.EMERGENCY && now - p.since > FocusRules.EMERGENCY_PAUSE_MILLIS) {
                val end = p.since + FocusRules.EMERGENCY_PAUSE_MILLIS
                s = s.copy(phase = Phase.Away(end), pausedMillis = s.pausedMillis + FocusRules.EMERGENCY_PAUSE_MILLIS)
            }
        }
        (s.phase as? Phase.Away)?.let { a ->
            if (now - a.since > FocusRules.GRACE_MILLIS) {
                return s.copy(phase = Phase.Failed(FailReason.LEFT_APP, a.since + FocusRules.GRACE_MILLIS))
            }
        }
        if (s.phase is Phase.Running || s.phase is Phase.Away) {
            if (s.focusedMillis(now) >= s.durationMillis) {
                val doneAt = s.startedAtMillis + s.pausedMillis + s.durationMillis
                return s.copy(phase = Phase.Completed(doneAt))
            }
        }
        return s
    }

    fun apply(event: FocusEvent, now: Long): FocusSession {
        val s = tick(now)
        if (s.isFinished) return s
        return when (event) {
            FocusEvent.Left -> if (s.phase is Phase.Running) s.copy(phase = Phase.Away(now)) else s
            FocusEvent.Returned -> if (s.phase is Phase.Away) s.copy(phase = Phase.Running) else s
            FocusEvent.ScreenLocked -> if (s.phase is Phase.Away) s.copy(phase = Phase.Running) else s
            FocusEvent.CallStarted -> when (s.phase) {
                is Phase.Running, is Phase.Away -> s.copy(phase = Phase.Paused(PauseReason.CALL, now))
                else -> s
            }
            FocusEvent.CallEnded -> endPause(s, now, PauseReason.CALL)
            FocusEvent.EmergencyPauseStarted ->
                if (s.phase is Phase.Running && s.emergencyPausesUsed < FocusRules.EMERGENCY_PAUSES_PER_DAY)
                    s.copy(phase = Phase.Paused(PauseReason.EMERGENCY, now), emergencyPausesUsed = s.emergencyPausesUsed + 1)
                else s
            FocusEvent.EmergencyPauseEnded -> endPause(s, now, PauseReason.EMERGENCY)
            FocusEvent.Moved -> if (s.deepFocus && (s.phase is Phase.Running || s.phase is Phase.Away)) s.copy(phase = Phase.Failed(FailReason.MOVED_PHONE, now)) else s
            FocusEvent.GaveUp -> s.copy(phase = Phase.Failed(FailReason.GAVE_UP, now))
        }
    }

    private fun endPause(s: FocusSession, now: Long, reason: PauseReason): FocusSession {
        val p = s.phase as? Phase.Paused ?: return s
        if (p.reason != reason) return s
        return s.copy(phase = Phase.Running, pausedMillis = s.pausedMillis + (now - p.since))
    }

    /**
     * The reward event for a completed session. Apply it to a player state whose buddy is [buddyIdAtStart], so the perks are
     * the ones locked in at the start.
     */
    fun toEffortEvent(newPokemonId: String, nowMillis: Long, epochDay: Long): EffortEvent {
        check(phase is Phase.Completed) { "only a completed session earns an egg" }
        return EffortEvent(
            minutes = (durationMillis / 60_000L).toInt() + if (durationMillis % 60_000L > 0) 1 else 0,
            deepFocus = deepFocus,
            region = region,
            lure = lure,
            epochDay = epochDay,
            newPokemonId = newPokemonId,
            nowMillis = nowMillis,
        )
    }
}

object FocusRules {
    /** Time to come back after leaving the app. */
    const val GRACE_MILLIS = 10_000L

    const val EMERGENCY_PAUSES_PER_DAY = 2
    const val EMERGENCY_PAUSE_MILLIS = 3 * 60_000L

    const val HALFWAY_MIN_MILLIS = 15 * 60_000L

    /** After a failure, one tap starts this retry. */
    val RETRY_MINUTES = FocusDuration.MIN_MINUTES

    /** "Just 5 minutes" and the retry are the shortest session there is. */
    fun justFiveMinutes(): FocusDuration = FocusDuration(0, FocusDuration.MIN_MINUTES, 0)

    /** The no-blame message shown after a failed session. */
    const val FAILED_MESSAGE = "That egg went cold. Try again?"

    fun start(
        id: String,
        durationMinutes: Int,
        nowMillis: Long,
        deepFocus: Boolean,
        region: Region,
        buddyIdAtStart: String?,
        taskId: String? = null,
        lure: PokemonType? = null,
        emergencyPausesUsedToday: Int = 0,
    ): FocusSession {
        require(durationMinutes >= FocusDuration.MIN_MINUTES) { "a session is at least ${FocusDuration.MIN_MINUTES} minutes" }
        return FocusSession(id, nowMillis, durationMinutes * 60_000L, deepFocus, region, buddyIdAtStart, taskId, lure, emergencyPausesUsed = emergencyPausesUsedToday)
    }
}
