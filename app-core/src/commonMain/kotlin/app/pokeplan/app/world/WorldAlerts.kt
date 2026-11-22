package app.pokeplan.app.world

import app.pokeplan.world.WorldSim
import app.pokeplan.app.AppData
import app.pokeplan.app.World

object WorldAlerts {
    fun nextJobDone(data: AppData, nowMillis: Long): Long? {
        if (!data.settings.worldJobAlerts || data.world.teams.isEmpty() || data.world.missions.isEmpty()) return null
        return WorldSim.jobsDoneAt(data.world, data.player.pokemon).values.filter { it > nowMillis }.minOrNull()
    }
}
