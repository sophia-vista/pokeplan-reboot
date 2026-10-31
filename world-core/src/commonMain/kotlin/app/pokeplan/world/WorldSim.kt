package app.pokeplan.world

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.OwnedPokemon
import kotlin.math.min
import kotlin.random.Random

/** One thing that happened on an expedition, for the report the player reads when they come back. */
sealed interface WorldEvent {
    data class FloorCleared(val dungeonId: String, val floor: Int) : WorldEvent
    data class Found(val speciesId: Int, val dungeonId: String, val floor: Int) : WorldEvent
    data class Item(val itemId: String, val count: Int) : WorldEvent
    data class Poke(val amount: Int) : WorldEvent
    data class Treasure(val tier: Int) : WorldEvent
    data class Milestone(val dungeonId: String, val floor: Int, val reward: MilestoneReward) : WorldEvent
    data class Cleared(val dungeonId: String) : WorldEvent
    data class MissionDone(val missionId: String) : WorldEvent
}

/** What settling did: the new data and, per team, the floors climbed and what happened. */
data class WorldReport(val data: WorldData, val perTeam: Map<Int, List<WorldEvent>>) {
    val events: List<WorldEvent> get() = perTeam.values.flatten()
    val isEmpty: Boolean get() = events.isEmpty()
}

/**
 * The dungeon simulation (docs/world.md, sections 5 and 6). A team climbs one floor after another on its own, and a floor
 * takes [floorMillis] given the team and the dungeon. What each floor gives is rolled from a random generator seeded by the
 * team, the expedition and the floor's number alone, so the result is the same whether the app settles every second or once
 * after a day away (offline progress is exactly as good as staying). Progress while away is capped at [MAX_AWAY_MILLIS].
 *
 * Numbers marked *(tune)* are starting values. Nothing here pays anything itself; the app takes the report and puts items
 * in the Bag, finds in the collection and so on.
 */
object WorldSim {
    /** The longest stretch of away time that counts at once. *(tune)* */
    const val MAX_AWAY_MILLIS = 12L * 60 * 60 * 1000

    /** Exploration XP for each floor: its number plus [XP_BASE]. *(tune)* */
    const val XP_BASE = 4

    /** The chance a floor shows a wild Pokémon that joins the finds, in percent. *(tune)* */
    const val FIND_PERCENT = 3

    /** The chance a floor has a berry, and the chance of a treasure box, in percent. *(tune)* */
    const val BERRY_PERCENT = 70
    const val TREASURE_PERCENT = 6

    /** A team's power: the sum of its members' levels, with 20% more for each member of a type the dungeon favors. */
    fun power(members: List<OwnedPokemon>, dungeon: DungeonDef): Double =
        members.sumOf { p ->
            val favored = p.form.types.any { it in dungeon.favored }
            p.level * if (favored) 1.2 else 1.0
        }

    /** How long a floor takes a team of [power], in milliseconds: faster as the power grows, never under a quarter of the base. */
    fun floorMillis(dungeon: DungeonDef, power: Double): Long {
        val base = dungeon.baseFloorSeconds * 1000L
        val factor = 1.0 + power / 100.0
        return maxOf(base / 4, (base / factor).toLong())
    }

    /**
     * Settles every team at [now]: the floors each has climbed since it was last settled (at most [MAX_AWAY_MILLIS] of them), their
     * finds, items, Poké, treasure, XP and milestones. [roster] is the player's Pokémon, to read the members' levels and types.
     */
    fun settle(data: WorldData, roster: List<OwnedPokemon>, now: Long): WorldReport {
        var out = data
        val perTeam = mutableMapOf<Int, List<WorldEvent>>()
        for (team in data.teams) {
            val trip = team.expedition ?: continue
            val dungeon = Dungeons.byId(trip.dungeonId) ?: continue
            val members = team.members.mapNotNull { id -> roster.firstOrNull { it.id == id } }
            if (members.isEmpty()) continue
            val per = floorMillis(dungeon, power(members, dungeon))
            val until = min(now, trip.settledTo + MAX_AWAY_MILLIS)
            val floors = ((until - trip.settledTo) / per).toInt()
            if (floors <= 0) continue
            val events = mutableListOf<WorldEvent>()
            for (n in 0 until floors) out = climb(out, team.id, trip, dungeon, trip.floorsClimbed + n, trip.settledTo + (n + 1) * per, events)
            // The rest of the time carries over (unless the cap cut it, when the clock jumps to now).
            val settled = if (now - trip.settledTo > MAX_AWAY_MILLIS) now else trip.settledTo + floors * per
            val current = out.team(team.id) ?: team
            out = out.withTeam(current.copy(expedition = trip.copy(settledTo = settled, floorsClimbed = trip.floorsClimbed + floors)))
            perTeam[team.id] = events
        }
        return WorldReport(out.copy(lastSeenAt = now), perTeam)
    }

    /** One floor climbed: the floor number in the dungeon is its index modulo the floors, so a cleared dungeon starts again. */
    private fun climb(data: WorldData, teamId: Int, trip: Expedition, dungeon: DungeonDef, index: Int, at: Long, events: MutableList<WorldEvent>): WorldData {
        val floor = index % dungeon.floors + 1
        val random = Random(seed(teamId, trip, index))
        var d = data
        events += WorldEvent.FloorCleared(dungeon.id, floor)
        d = d.copy(xp = d.xp + XP_BASE + floor, deepest = d.deepest + (dungeon.id to maxOf(floor, d.deepest[dungeon.id] ?: 0)))
        var berries = 0
        if (random.nextInt(100) < BERRY_PERCENT) {
            berries = 1
            val flavor = listOf("sweet", "dry", "sour", "spicy", "bitter").random(random)
            val berry = Items.berry(flavor)
            events += WorldEvent.Item(berry, 1)
            d = d.copy(items = d.items + (berry to (d.items[berry] ?: 0) + 1))
        }
        val poke = 5 + floor * 2 + random.nextInt(5)
        events += WorldEvent.Poke(poke)
        d = d.copy(poke = d.poke + poke)
        if (random.nextInt(100) < TREASURE_PERCENT) {
            val tier = if (floor >= dungeon.floors - 1) 3 else if (floor >= dungeon.floors / 2) 2 else 1
            events += WorldEvent.Treasure(tier)
            d = d.copy(treasure = d.treasure + (tier to (d.treasure[tier] ?: 0) + 1))
        }
        findFor(dungeon, floor, random)?.let { species ->
            events += WorldEvent.Found(species, dungeon.id, floor)
            d = d.copy(finds = d.finds + Find(species, dungeon.id, floor, at), discovered = d.discovered + species)
        }
        dungeon.milestones[floor]?.let { reward ->
            val key = "${dungeon.id}:$floor"
            if (key !in d.milestonesPaid) {
                events += WorldEvent.Milestone(dungeon.id, floor, reward)
                d = d.copy(
                    milestonesPaid = d.milestonesPaid + key, poke = d.poke + reward.poke,
                    items = reward.itemId?.let { d.items + (it to (d.items[it] ?: 0) + reward.count) } ?: d.items,
                )
            }
        }
        d = Missions.advance(d, dungeon.id, floor, berries, at, events)
        if (floor == dungeon.floors) {
            events += WorldEvent.Cleared(dungeon.id)
            d = d.copy(clears = d.clears + (dungeon.id to (d.clears[dungeon.id] ?: 0) + 1))
        }
        return d
    }

    /** The wild Pokémon a floor shows, if it shows one: only ones that can hatch (never a legendary or mythical, which are story discoveries). */
    private fun findFor(dungeon: DungeonDef, floor: Int, random: Random): Int? {
        if (random.nextInt(100) >= FIND_PERCENT) return null
        val pool = dungeon.wild.filter { floor in it.fromFloor..it.toFloor && Dex.speciesOrNull(it.speciesId)?.let { s -> !s.isSpecial } == true && HatchRules.rarity(it.speciesId) != null }
        val total = pool.sumOf { it.weight }
        if (total == 0) return null
        var roll = random.nextInt(total)
        for (entry in pool) { roll -= entry.weight; if (roll < 0) return entry.speciesId }
        return null
    }

    private fun seed(teamId: Int, trip: Expedition, index: Int): Long = (trip.dungeonId.hashCode().toLong() * 31 + teamId) * 1_000_003L + trip.startedAt * 7 + index
}
