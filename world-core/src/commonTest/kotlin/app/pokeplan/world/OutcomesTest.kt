package app.pokeplan.world

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.pokemon.Hatching
import app.pokeplan.core.pokemon.OwnedPokemon
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** WORLD.md 3.6: clear, setback or retreat, deterministic, with nothing rolled back and no one hurt. */
class OutcomesTest {
    private fun mon(id: String, level: Int): OwnedPokemon =
        Hatching.create(id, HatchOption(25, Dex.defaultForm(25).identifier), OwnedPokemon.PARTY_SIZE, 0L, 60, Random(1)).copy(level = level)

    private val steel = Dungeons.byId("mt-steel")!!    // Bronze, so a team can retreat
    private val woods = Dungeons.byId("tiny-woods")!!  // Normal: never worse than a setback

    @Test
    fun theFirstFloorAndAStrongTeamAlwaysClear() {
        assertEquals(Outcomes.Outcome.CLEARED, Outcomes.of(0.0, steel, 1))
        assertEquals(Outcomes.Outcome.CLEARED, Outcomes.of(1_000.0, steel, steel.floors))
        assertEquals(steel.floors, Outcomes.clearsUpTo(1_000.0, steel))
        assertNull(Outcomes.retreatsAt(1_000.0, steel))
    }

    @Test
    fun theThresholdsAreAShareOfWhatTheFloorAsks() {
        val top = Outcomes.required(steel, steel.floors)
        assertEquals(Outcomes.recommended(ExplorerRank.BRONZE).toDouble(), top, 0.0001)
        assertEquals(Outcomes.Outcome.CLEARED, Outcomes.of(top * Outcomes.CLEAR, steel, steel.floors))
        assertEquals(Outcomes.Outcome.SETBACK, Outcomes.of(top * (Outcomes.CLEAR - 0.01), steel, steel.floors))
        assertEquals(Outcomes.Outcome.SETBACK, Outcomes.of(top * Outcomes.RETREAT, steel, steel.floors))
        assertEquals(Outcomes.Outcome.RETREAT, Outcomes.of(top * (Outcomes.RETREAT - 0.01), steel, steel.floors))
        // The deeper the floor, the more it asks.
        assertTrue(Outcomes.required(steel, 2) < Outcomes.required(steel, steel.floors))
    }

    @Test
    fun aNormalRankDungeonNeverMakesATeamTurnBack() {
        assertNull(Outcomes.retreatsAt(0.0, woods))
        assertEquals(Outcomes.Outcome.SETBACK, Outcomes.of(0.0, woods, woods.floors))
    }

    @Test
    fun aWeakTeamTurnsBackSafelyAndKeepsWhatEarlierFloorsPaid() {
        val weak = listOf(mon("a", 2))
        val data = WorldData(xp = 400, clears = mapOf("tiny-woods" to 1)).assign(1, listOf("a"))!!.start(1, "mt-steel", 0L)!!
        val report = WorldSim.settle(data, weak, WorldSim.MAX_AWAY_MILLIS)
        val back = report.events.filterIsInstance<WorldEvent.Retreat>().singleOrNull()
        assertNotNull(back, "the team turned back")
        assertEquals(Outcomes.retreatsAt(WorldSim.power(weak, steel), steel), back.floor)
        assertNull(report.data.team(1)!!.expedition, "home, and free to be sent again at once")
        assertTrue(report.data.team(1)!!.members == listOf("a"), "no one lost")
        assertTrue(report.data.xp > data.xp && report.data.pokeEarned > 0, "the floors before it kept their rewards")
        assertEquals(back.floor - 1, report.memberFloors.getValue("a"), "only floors that were climbed give the member experience")
        assertTrue(report.events.none { it is WorldEvent.Cleared }, "no clear for an unfinished dungeon")
        // Sending the team again works straight away.
        assertNotNull(report.data.start(1, "mt-steel", WorldSim.MAX_AWAY_MILLIS))
    }

    @Test
    fun aSetbackPaysHalfAndGivesNothingElse() {
        // Power just under the clearing share at the top floor but over the retreat share.
        val level = (Outcomes.required(steel, steel.floors) * 0.3).toInt()
        val team = listOf(mon("a", level))
        assertEquals(Outcomes.Outcome.SETBACK, Outcomes.of(WorldSim.power(team, steel), steel, steel.floors))
        // A team that clears everything up to a floor and sets back after it: compare one setback floor with the same floor cleared by a strong team.
        val floor = steel.floors
        val index = floor - 1
        val strong = listOf(mon("a", 100), mon("b", 100), mon("c", 100), mon("d", 100))
        fun settleFloor(roster: List<OwnedPokemon>): WorldReport {
            val trip = Expedition("mt-steel", startedAt = 0, settledTo = 0, floorsClimbed = index)
            val data = WorldData(xp = 400, clears = mapOf("tiny-woods" to 1)).copy(teams = listOf(WorldTeam(1, roster.map { it.id }, trip)))
            val per = WorldSim.floorMillis(steel, WorldSim.power(roster, steel))
            return WorldSim.settle(data, roster, per)
        }
        val weak = settleFloor(team)
        val full = settleFloor(strong)
        assertTrue(weak.events.any { it is WorldEvent.Setback && it.floor == floor })
        assertTrue(weak.events.none { it is WorldEvent.Cleared || it is WorldEvent.Treasure || it is WorldEvent.Found || it is WorldEvent.Milestone }, "no clear, treasure, find or milestone on a setback")
        assertTrue(weak.data.clears["mt-steel"] == null)
        assertTrue(full.events.any { it is WorldEvent.Cleared })
        assertTrue(weak.data.pokeEarned in 1 until full.data.pokeEarned, "half the Poké")
        assertEquals(weak.memberFloors.getValue("a"), 1, "a setback floor still gives the member experience")
    }

    @Test
    fun theSameTeamOnTheSameFloorAlwaysHasTheSameOutcomeWhateverTheSettleFrequency() {
        val roster = listOf(mon("a", 8), mon("b", 8))
        val data = WorldData(xp = 400, clears = mapOf("tiny-woods" to 1)).assign(1, listOf("a", "b"))!!.start(1, "mt-steel", 0L)!!
        val once = WorldSim.settle(data, roster, 6L * 3_600_000)
        var step = data
        var t = 0L
        repeat(6) { t += 3_600_000; step = WorldSim.settle(step, roster, t).data }
        assertEquals(once.data.xp, step.xp)
        assertEquals(once.data.pokeEarned, step.pokeEarned)
        assertEquals(once.data.team(1)!!.expedition, step.team(1)!!.expedition)
    }

    @Test
    fun theTeamPageExplainsWhatToExpect() {
        assertTrue(Outcomes.explain(1_000.0, steel).contains("every floor"))
        val weak = Outcomes.explain(5.0, steel)
        assertTrue(weak.contains("setbacks") && weak.contains("turns back safely"), weak)
        assertTrue(!Outcomes.explain(5.0, woods).contains("turns back"), "a Normal-rank dungeon only has setbacks")
    }
}
