package app.pokeplan.app.world

import app.pokeplan.world.WorldSim
import app.pokeplan.app.AppData
import app.pokeplan.app.World

object WorldAlerts {
    fun nextJobDone(data: AppData, nowMillis: Long): Long? {
        if (!data.settings.worldJobAlerts || data.world.teams.isEmpty() || data.world.missions.isEmpty()) return null
        return WorldSim.jobsDoneAt(data.world, data.player.pokemon).values.filter { it > nowMillis }.minOrNull()
    }

    /**
     * The instant the next expedition floor is due, or null when no team is out.
     *
     * Settlement is deterministic from elapsed time, so an alarm at this instant pays exactly what a poll would have
     * found there: the alarm (and the app coming back to the foreground) are the only things that need to happen.
     */
    fun nextFloorAt(data: AppData, nowMillis: Long): Long? =
        WorldSim.nextFloorAt(data.world, data.player.pokemon, nowMillis)
}
