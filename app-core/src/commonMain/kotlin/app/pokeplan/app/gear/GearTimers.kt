package app.pokeplan.app.gear

import app.pokeplan.app.AppData
import app.pokeplan.app.GearData
import app.pokeplan.app.GearStopwatch
import app.pokeplan.app.GearTimer
import app.pokeplan.app.storage.Clock

/**
 * The Pokégear's timers and stopwatch as plain rules on the saved [GearData] (no clock of their own: callers pass the time). A direct Pokégear
 * timer is a utility and pays nothing; a Focus session stays Plan's own state and is not one of these. A running timer keeps the clock time it
 * ends at, so it survives the app closing, the process dying and a reboot; if the phone's clock is changed while it runs, the end moves with the
 * clock (the way the phone's own Clock app behaves), and a stopwatch never counts a clock set back as negative time.
 */
object GearTimers {
    const val MAX_TIMERS = 8
    const val MIN_MS = 1_000L
    const val MAX_MS = 99L * 3_600_000

    /** When the next running timer ends after [now], for the one alarm the Android app keeps; null when none is running. */
    fun nextEnd(data: AppData, now: Long): Long? = data.gear.timers.filter { it.running && it.endsAt > now }.minOfOrNull { it.endsAt }

    /** The timers that ran out by [now] and have not been marked finished yet. */
    fun due(data: AppData, now: Long): List<GearTimer> = data.gear.timers.filter { it.running && it.endsAt <= now }

    fun add(g: GearData, id: String, label: String, durationMs: Long): GearData? {
        if (g.timers.size >= MAX_TIMERS || durationMs !in MIN_MS..MAX_MS) return null
        val name = label.trim().take(24).ifEmpty { "Timer" }
        return g.copy(timers = g.timers + GearTimer(id, name, durationMs))
    }

    private fun change(g: GearData, id: String, f: (GearTimer) -> GearTimer): GearData = g.copy(timers = g.timers.map { if (it.id == id) f(it) else it })

    /** Starts an idle or finished timer from its full duration, or resumes a paused one from what was left. A running one is left alone. */
    fun start(g: GearData, id: String, now: Long): GearData = change(g, id) { t ->
        when {
            t.running -> t
            t.paused -> t.copy(endsAt = now + t.pausedLeftMs.coerceAtLeast(1), pausedLeftMs = -1)
            else -> t.copy(endsAt = now + t.durationMs, pausedLeftMs = -1, finished = false)
        }
    }

    fun pause(g: GearData, id: String, now: Long): GearData = change(g, id) { t -> if (t.running) t.copy(pausedLeftMs = (t.endsAt - now).coerceAtLeast(0), endsAt = 0) else t }

    /** Adds [ms] to a running or paused timer's time left; to a finished one it gives a fresh run of [ms] (the "+1 min" of a ringing timer). An idle one gets a longer duration. */
    fun addTime(g: GearData, id: String, ms: Long, now: Long): GearData = change(g, id) { t ->
        when {
            t.finished -> t.copy(finished = false, endsAt = now + ms, pausedLeftMs = -1)
            t.running -> t.copy(endsAt = t.endsAt + ms)
            t.paused -> t.copy(pausedLeftMs = t.pausedLeftMs + ms)
            else -> t.copy(durationMs = (t.durationMs + ms).coerceIn(MIN_MS, MAX_MS))
        }
    }

    /** Renames a timer and sets its length. It goes back to ready (a running or paused one stops), since its time left would no longer mean anything. A length outside the limits changes nothing. */
    fun edit(g: GearData, id: String, label: String, durationMs: Long): GearData {
        if (durationMs !in MIN_MS..MAX_MS) return g
        val name = label.trim().take(24)
        return change(g, id) { it.copy(label = name.ifEmpty { it.label }, durationMs = durationMs, endsAt = 0, pausedLeftMs = -1, finished = false) }
    }

    fun reset(g: GearData, id: String): GearData = change(g, id) { it.copy(endsAt = 0, pausedLeftMs = -1, finished = false) }

    fun remove(g: GearData, id: String): GearData = g.copy(timers = g.timers.filter { it.id != id })

    /** Marks the timers that ran out by [now] as finished (their end is cleared) and says which they were. */
    fun tick(g: GearData, now: Long): Pair<GearData, List<GearTimer>> {
        val ran = g.timers.filter { it.running && it.endsAt <= now }
        if (ran.isEmpty()) return g to emptyList()
        val ids = ran.map { it.id }.toSet()
        return g.copy(timers = g.timers.map { if (it.id in ids) it.copy(finished = true, endsAt = 0, pausedLeftMs = -1) else it }) to ran
    }

    /** Puts every finished timer back to idle (the ringing is dismissed). Nothing else changes, so doing it twice is the same as once. */
    fun dismissFinished(g: GearData): GearData = g.copy(timers = g.timers.map { if (it.finished) it.copy(finished = false) else it })

    // ----- Stopwatch ----------------------------------------------------------------------------------------------

    fun stopwatchStart(g: GearData, now: Long): GearData = if (g.stopwatch.running) g else g.copy(stopwatch = g.stopwatch.copy(runningSince = now))

    fun stopwatchPause(g: GearData, now: Long): GearData =
        if (!g.stopwatch.running) g else g.copy(stopwatch = g.stopwatch.copy(accumulatedMs = g.stopwatch.elapsedAt(now), runningSince = 0))

    /** Marks a lap (the total so far); only while running. */
    fun stopwatchLap(g: GearData, now: Long): GearData =
        if (!g.stopwatch.running) g else g.copy(stopwatch = g.stopwatch.copy(laps = (g.stopwatch.laps + g.stopwatch.elapsedAt(now)).takeLast(200)))

    fun stopwatchReset(g: GearData): GearData = g.copy(stopwatch = GearStopwatch())

    /** Each lap's own time (the difference from the one before), in the order they were taken. */
    fun splits(laps: List<Long>): List<Long> = laps.mapIndexed { i, total -> total - (laps.getOrNull(i - 1) ?: 0L) }
}
