package app.pokeplan.plan

import kotlinx.serialization.Serializable
import app.pokeplan.core.dex.Region
import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.reward.EffortEvent

enum class PauseReason { CALL, EMERGENCY }

enum class FailReason { LEFT_APP, MOVED_PHONE, GAVE_UP }

@Serializable
sealed interface Phase {
    @Serializable
    data object Running : Phase

    @Serializable
    data class Away(val since: Long) : Phase

    @Serializable
    data class Paused(val reason: PauseReason, val since: Long) : Phase

    @Serializable
    data class Completed(val at: Long) : Phase

    @Serializable
    data class Failed(val reason: FailReason, val at: Long) : Phase
}

sealed interface FocusEvent {
    data object Left : FocusEvent

    data object Returned : FocusEvent

    data object ScreenLocked : FocusEvent

    data object CallStarted : FocusEvent

    data object CallEnded : FocusEvent

    data object EmergencyPauseStarted : FocusEvent

    data object EmergencyPauseEnded : FocusEvent

    data object Moved : FocusEvent

    data object GaveUp : FocusEvent
}

@Serializable
data class FocusSession(
    val id: String,
    val startedAtMillis: Long,
    val durationMillis: Long,
    val deepFocus: Boolean,
    val region: Region,
    @kotlinx.serialization.SerialName("buddyIdAtStart") val leadIdAtStart: String?,
    val taskId: String? = null,
    val lure: PokemonType? = null,
    val phase: Phase = Phase.Running,
    val pausedMillis: Long = 0,
    val emergencyPausesUsed: Int = 0,
) {
    val isFinished: Boolean get() = phase is Phase.Completed || phase is Phase.Failed

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

    val halfwayAtMillis: Long? get() = if (durationMillis >= FocusRules.HALFWAY_MIN_MILLIS) startedAtMillis + durationMillis / 2 else null

    fun tick(now: Long): FocusSession {
        if (isFinished) return this
        var s = this
        (s.phase as? Phase.Paused)?.let { p ->
            if (p.reason == PauseReason.EMERGENCY && now - p.since > FocusRules.EMERGENCY_PAUSE_MILLIS) {
                val end = p.since + FocusRules.EMERGENCY_PAUSE_MILLIS
                s = s.copy(phase = Phase.Away(end), pausedMillis = s.pausedMillis + FocusRules.EMERGENCY_PAUSE_MILLIS)
            }
        }
        (s.phase as? Phase.Away)?.let { a ->
            val doneAt = s.startedAtMillis + s.pausedMillis + s.durationMillis
            if (now - a.since > FocusRules.GRACE_MILLIS && doneAt > a.since + FocusRules.GRACE_MILLIS) {
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
        return s.copy(phase = Phase.Running, pausedMillis = s.pausedMillis + (now - p.since).coerceAtLeast(0))
    }

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
    const val GRACE_MILLIS = 10_000L

    const val EMERGENCY_PAUSES_PER_DAY = 2
    const val EMERGENCY_PAUSE_MILLIS = 3 * 60_000L

    const val HALFWAY_MIN_MILLIS = 15 * 60_000L

    val RETRY_MINUTES = FocusDuration.MIN_MINUTES

    const val MAX_MINUTES = 180

    fun justFiveMinutes(): FocusDuration = FocusDuration(0, FocusDuration.MIN_MINUTES, 0)

    const val FAILED_MESSAGE = "That egg went cold. Try again?"

    fun start(
        id: String,
        durationMinutes: Int,
        nowMillis: Long,
        deepFocus: Boolean,
        region: Region,
        leadIdAtStart: String?,
        taskId: String? = null,
        lure: PokemonType? = null,
        emergencyPausesUsedToday: Int = 0,
    ): FocusSession {
        require(durationMinutes >= FocusDuration.MIN_MINUTES) { "a session is at least ${FocusDuration.MIN_MINUTES} minutes" }
        return FocusSession(id, nowMillis, durationMinutes * 60_000L, deepFocus, region, leadIdAtStart, taskId, lure, emergencyPausesUsed = emergencyPausesUsedToday)
    }
}
