package app.pokeplan.app

import app.pokeplan.world.WorldSim

/**
 * When World's "a job is done" notification is due (docs/world.md): at the earliest moment an exploring team will finish an
 * accepted job. The Android side sets one alarm for it, settles World when it goes off and announces only jobs that really are done.
 */
object WorldAlerts {
    /** The next moment after [nowMillis] a job will be done; null when the alert is off, World is not open or no job is on its way. */
    fun nextJobDone(data: AppData, nowMillis: Long): Long? {
        if (!data.settings.worldJobAlerts || data.world.teams.isEmpty() || data.world.missions.isEmpty()) return null
        return WorldSim.jobsDoneAt(data.world, data.player.pokemon).values.filter { it > nowMillis }.minOrNull()
    }
}
