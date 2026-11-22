package app.pokeplan.app.gear

import app.pokeplan.app.AppData
import app.pokeplan.app.GearData
import app.pokeplan.app.GearStopwatch
import app.pokeplan.app.GearTimer
import app.pokeplan.app.storage.Clock

object GearTimers {
    const val MAX_TIMERS = 8
    const val MIN_MS = 1_000L
    const val MAX_MS = 99L * 3_600_000

    fun nextEnd(data: AppData, now: Long): Long? = data.gear.timers.filter { it.running && it.endsAt > now }.minOfOrNull { it.endsAt }

    fun due(data: AppData, now: Long): List<GearTimer> = data.gear.timers.filter { it.running && it.endsAt <= now }

    fun add(g: GearData, id: String, label: String, durationMs: Long): GearData? {
        if (g.timers.size >= MAX_TIMERS || durationMs !in MIN_MS..MAX_MS) return null
        val name = label.trim().take(24).ifEmpty { "Timer" }
        return g.copy(timers = g.timers + GearTimer(id, name, durationMs))
    }

    private fun change(g: GearData, id: String, f: (GearTimer) -> GearTimer): GearData = g.copy(timers = g.timers.map { if (it.id == id) f(it) else it })

    fun start(g: GearData, id: String, now: Long): GearData = change(g, id) { t ->
        when {
            t.running -> t
            t.paused -> t.copy(endsAt = now + t.pausedLeftMs.coerceAtLeast(1), pausedLeftMs = -1)
            else -> t.copy(endsAt = now + t.durationMs, pausedLeftMs = -1, finished = false)
        }
    }

    fun pause(g: GearData, id: String, now: Long): GearData = change(g, id) { t -> if (t.running) t.copy(pausedLeftMs = (t.endsAt - now).coerceAtLeast(0), endsAt = 0) else t }

    fun addTime(g: GearData, id: String, ms: Long, now: Long): GearData = change(g, id) { t ->
        when {
            t.finished -> t.copy(finished = false, endsAt = now + ms, pausedLeftMs = -1)
            t.running -> t.copy(endsAt = t.endsAt + ms)
            t.paused -> t.copy(pausedLeftMs = t.pausedLeftMs + ms)
            else -> t.copy(durationMs = (t.durationMs + ms).coerceIn(MIN_MS, MAX_MS))
        }
    }

    fun edit(g: GearData, id: String, label: String, durationMs: Long): GearData {
        if (durationMs !in MIN_MS..MAX_MS) return g
        val name = label.trim().take(24)
        return change(g, id) { it.copy(label = name.ifEmpty { it.label }, durationMs = durationMs, endsAt = 0, pausedLeftMs = -1, finished = false) }
    }

    fun reset(g: GearData, id: String): GearData = change(g, id) { it.copy(endsAt = 0, pausedLeftMs = -1, finished = false) }

    fun remove(g: GearData, id: String): GearData = g.copy(timers = g.timers.filter { it.id != id })

    fun tick(g: GearData, now: Long): Pair<GearData, List<GearTimer>> {
        val ran = g.timers.filter { it.running && it.endsAt <= now }
        if (ran.isEmpty()) return g to emptyList()
        val ids = ran.map { it.id }.toSet()
        return g.copy(timers = g.timers.map { if (it.id in ids) it.copy(finished = true, endsAt = 0, pausedLeftMs = -1) else it }) to ran
    }

    fun dismissFinished(g: GearData): GearData = g.copy(timers = g.timers.map { if (it.finished) it.copy(finished = false) else it })

    fun stopwatchStart(g: GearData, now: Long): GearData = if (g.stopwatch.running) g else g.copy(stopwatch = g.stopwatch.copy(runningSince = now))

    fun stopwatchPause(g: GearData, now: Long): GearData =
        if (!g.stopwatch.running) g else g.copy(stopwatch = g.stopwatch.copy(accumulatedMs = g.stopwatch.elapsedAt(now), runningSince = 0))

    fun stopwatchLap(g: GearData, now: Long): GearData =
        if (!g.stopwatch.running) g else g.copy(stopwatch = g.stopwatch.copy(laps = (g.stopwatch.laps + g.stopwatch.elapsedAt(now)).takeLast(200)))

    fun stopwatchReset(g: GearData): GearData = g.copy(stopwatch = GearStopwatch())

    fun splits(laps: List<Long>): List<Long> = laps.mapIndexed { i, total -> total - (laps.getOrNull(i - 1) ?: 0L) }
}
