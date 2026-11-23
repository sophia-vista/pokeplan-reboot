package app.pokeplan.app.world

import app.pokeplan.app.AppData
import app.pokeplan.world.NextStep
import app.pokeplan.world.WorldGuide
import app.pokeplan.world.WorldLayer

/**
 * The one overview the WORLD menu shows: what is happening now, each fact once. The menu draws it, the section rows
 * keep only their own local detail, and nothing here repeats a count that another row already states.
 */
data class WorldDigest(
    /** Teams climbing right now, with the floor they are on. */
    val out: List<TeamAway>,
    /** Jobs finished and waiting to be reported (only when the board is open). */
    val jobsReady: Int,
    /** Finds waiting to be met; they stay until the player decides. */
    val finds: Int,
    /** Expeditions waiting on a floor event choice. */
    val choices: Int,
    /** Teams with members resting at camp, able to go out. */
    val ready: Int,
    val slots: Int,
    /** The next place worth exploring; only offered when there is nothing else waiting to be done first. */
    val nextStep: NextStep?,
    /** A save that has not sent a team or cleared a dungeon yet: the menu may point at the first adventure. */
    val fresh: Boolean,
) {
    val idle: Boolean get() = out.isEmpty() && jobsReady == 0 && finds == 0 && choices == 0
    /** Whether anything on the menu needs the player: used for the block's title. */
    val needsYou: Boolean get() = jobsReady > 0 || finds > 0 || choices > 0

    companion object {
    fun of(data: AppData, layer: WorldLayer = WorldLayer.RESCUE): WorldDigest {
        val world = data.world
        val out = world.teams.mapNotNull { team ->
            val trip = team.expedition ?: return@mapNotNull null
            val dungeon = app.pokeplan.world.Dungeons.byId(trip.dungeonId) ?: return@mapNotNull null
            TeamAway(team.id, team.title, dungeon.id, trip.floorsClimbed % dungeon.floors + 1, dungeon.floors)
        }
        val ready = world.slots().count { it.expedition == null && it.members.isNotEmpty() }
        val jobs = if (app.pokeplan.world.Missions.boardOpen(world)) world.missions.count { it.done } else 0
        val finds = world.finds.size
        val choices = world.choices.count { it.choice != null }
        val idle = finds == 0 && jobs == 0 && choices == 0 && ready > 0
        return WorldDigest(
            out = out,
            jobsReady = jobs,
            finds = finds,
            choices = choices,
            ready = ready,
            slots = world.teamSlots,
            nextStep = if (idle) WorldGuide.nextStep(world, layer) else null,
            fresh = out.isEmpty() && ready == 0 && world.clears.isEmpty() && world.missions.isEmpty() && finds == 0,
        )
    }
    }
}

data class TeamAway(val id: Int, val title: String, val dungeonId: String, val floor: Int, val floors: Int)
