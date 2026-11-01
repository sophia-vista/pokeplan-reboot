package app.pokeplan.world

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.pokemon.Hatching
import app.pokeplan.core.pokemon.OwnedPokemon
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LandingTest {
    private fun mon(id: String, species: Int, level: Int) =
        Hatching.create(id, HatchOption(species, Dex.defaultForm(species).identifier), OwnedPokemon.PARTY_SIZE, 0L, 60, Random(1)).copy(level = level)

    @Test
    fun thePrimaryActionIsTheMostUsefulThingInOrder() {
        // Nothing yet: explore the first dungeon.
        val fresh = WorldGuide.primaryAction(WorldData())
        assertEquals(PrimaryKind.EXPLORE, fresh.kind)
        assertEquals("tiny-woods", fresh.dungeonId)
        // The layer on show decides which dungeon.
        assertEquals("viridian-forest", WorldGuide.primaryAction(WorldData(), WorldLayer.KANTO).dungeonId)
        // A job waiting to be claimed comes before exploring; Pokémon waiting come before both.
        val job = WorldData(xp = 400, missions = listOf(MissionProgress("explore-woods-3", 1, true)))
        assertEquals(PrimaryKind.CLAIM_JOB, WorldGuide.primaryAction(job).kind)
        val find = job.copy(finds = listOf(Find(25, "tiny-woods", 2, 0L)))
        assertEquals(PrimaryKind.REVIEW_FINDS, WorldGuide.primaryAction(find).kind)
        // Every team out: go and look.
        val out = WorldData().assign(1, listOf("a"))!!.assign(2, listOf("b"))!!.start(1, "tiny-woods", 0L)!!.start(2, "tiny-woods", 0L)!!
        assertEquals(PrimaryKind.CONTINUE, WorldGuide.primaryAction(out).kind)
    }

    @Test
    fun theNextRankSaysWhatItOpens() {
        val bronze = ExplorerRank.NORMAL.nextRankOpens()
        assertTrue(bronze.none { "expedition teams" in it }, "Bronze brings no new team: $bronze")
        assertTrue(bronze.any { it.startsWith("the dungeons") && "Rock Tunnel" in it && "Mt. Steel" in it }, bronze.toString())
        val silver = ExplorerRank.BRONZE.nextRankOpens()
        assertTrue(silver.any { "3 expedition teams (now 2)" in it }, silver.toString())
        assertTrue(ExplorerRank.DIAMOND.nextRankOpens().isEmpty(), "nothing after the top rank")
        // Rank gates match the dungeons: every dungeon is named by exactly one rank's list.
        val named = ExplorerRank.entries.flatMap { r -> r.nextRankOpens().filter { it.startsWith("the dungeons") } }
        assertTrue(named.isNotEmpty())
    }

    @Test
    fun theBoardShowsEveryJobWithItsStateAndWhy() {
        val closed = WorldData()
        assertTrue(Missions.board(closed).isEmpty(), "the board is shut before the story's second chapter")
        val open = WorldData(xp = 400, clears = mapOf("tiny-woods" to 1), missions = listOf(MissionProgress("explore-woods-3"), MissionProgress("gather-10", 10, true)), missionsDone = setOf("rescue-vulpix"), missionClaimedDay = mapOf("daily-berries" to 100L))
        val now = 100L * 86_400_000L + 1000
        val board = Missions.board(open, now).associateBy { it.mission.id }
        assertEquals(Missions.BoardState.HELD, board.getValue("explore-woods-3").state)
        assertEquals(Missions.BoardState.DONE, board.getValue("gather-10").state)
        assertEquals("Done for good.", board.getValue("rescue-vulpix").reason)
        assertEquals("Back in 1 day.", board.getValue("daily-berries").reason)
        assertTrue(board.getValue("defeat-storm").reason!!.startsWith("Needs Platinum rank"))
        assertEquals(Missions.BoardState.AVAILABLE, board.getValue("explore-woods-5").state)
        assertEquals("One time", board.getValue("rescue-vulpix").repeats)
        assertEquals("Daily", board.getValue("daily-berries").repeats)
        assertEquals("Weekly", board.getValue("weekly-steel").repeats)
        assertEquals("Repeats every 14 days", board.getValue("explore-woods-5").repeats)
        // The available ones are exactly what the board has always offered.
        assertEquals(Missions.available(open, now).map { it.id }.toSet(), board.values.filter { it.state == Missions.BoardState.AVAILABLE }.map { it.mission.id }.toSet())
    }

    @Test
    fun theReportAddsUpWhatTheSettleReallyGave() {
        val roster = listOf(mon("a", 25, 60), mon("b", 74, 60), mon("c", 10, 60), mon("d", 1, 60))
        val start = WorldData().assign(1, roster.map { it.id })!!.start(1, "tiny-woods", 0L)!!
        val report = WorldSim.settle(start, roster, 6L * 3_600_000)
        val summary = ReportSummary.of(report, report.data.xp - start.xp)
        assertTrue(summary.floors > 0 && summary.floors == report.events.count { it is WorldEvent.FloorCleared })
        assertEquals(summary.xp, report.data.xp - start.xp)
        // Poké: what every floor gave plus the milestones' Poké is all the data gained.
        assertEquals(report.data.poke - start.poke, summary.poke + summary.milestones.sumOf { it.second.poke })
        // Berries found by chance are in the data's items (milestone berries come on top).
        val milestoneBerries = summary.milestones.filter { it.second.itemId?.startsWith("berry") == true }.sumOf { it.second.count }
        assertEquals(report.data.items.filterKeys { it.startsWith("berry") }.values.sum(), summary.berries + milestoneBerries)
        assertEquals(report.data.treasure.values.sum(), summary.treasure.values.sum())
        assertEquals(report.data.finds.size, summary.finds.size)
        // Reports add up, and an empty one is empty.
        val twice = summary + summary
        assertEquals(summary.floors * 2, twice.floors)
        assertTrue(ReportSummary().isEmpty && !summary.isEmpty)
        assertEquals(WorldReport(start, emptyMap()).events.size, 0)
        assertNull(ReportSummary().takeIf { !it.isEmpty })
        assertFalse(summary.guardianFaced)
        assertNotNull(summary.byDungeon["tiny-woods"])
    }
}
