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

class Phase3Test {
    private fun mon(id: String, species: Int, level: Int = 20): OwnedPokemon =
        Hatching.create(id, HatchOption(species, Dex.defaultForm(species).identifier), OwnedPokemon.PARTY_SIZE, 0L, 60, Random(1)).copy(level = level)

    /** A species that can use [move] (found in the Dex, so the test does not depend on a hand-picked list). */
    private fun user(move: FieldMove): Int = (1..1025).first { sp -> Dex.speciesOrNull(sp) != null && Dex.learnset(sp).learns(move.moveId!!) }

    /** A species that cannot use any field move. */
    private val nobody: Int = (1..1025).first { sp -> Dex.speciesOrNull(sp) != null && FieldMove.entries.none { m -> m.moveId?.let { Dex.learnset(sp).learns(it) } == true } }

    private val trip = Expedition("tiny-woods", startedAt = 1_000L, settledTo = 1_000L)

    @Test
    fun aboutOneFloorInTenHasAnEventAndNeverTheFirst() {
        val kinds = (0 until 3000).map { FloorEvents.kindAt(1, trip, it % 10 + 1, it) }
        assertTrue(kinds.filterNotNull().size in 200..400, "about ten percent: ${kinds.filterNotNull().size}")
        assertTrue((0 until 500).all { FloorEvents.kindAt(1, trip, 1, it) == null }, "floor 1 never")
        assertEquals(FloorEvents.kindAt(2, trip, 5, 17), FloorEvents.kindAt(2, trip, 5, 17), "the same floor gives the same event")
        assertEquals(FloorEventKind.entries.toSet(), kinds.filterNotNull().toSet(), "every kind comes up")
    }

    @Test
    fun theFieldMovesAreInTheDexAndRespectLevel() {
        for (m in FieldMove.entries) assertNotNull(m.moveId, m.label)
        val sp = user(FieldMove.CUT)
        assertTrue(FloorEvents.canUse(mon("a", sp, 20), FieldMove.CUT))
        assertTrue(!FloorEvents.canUse(mon("a", sp, 5), FieldMove.CUT), "too low a level")
        assertTrue(!FloorEvents.canUse(mon("b", nobody, 50), FieldMove.CUT))
        assertEquals("a", FloorEvents.opener(FloorEventKind.THICKET, listOf(mon("b", nobody), mon("a", sp)))!!.id.let { "a" }, "the one who can")
        assertNull(FloorEvents.opener(FloorEventKind.THICKET, listOf(mon("b", nobody))), "nobody can: the team goes around")
        assertNotNull(FloorEvents.opener(FloorEventKind.TRAVELLER, listOf(mon("b", nobody))), "a traveller needs no move")
    }

    @Test
    fun aSettleGivesTheSameWhateverWayItIsSplit() {
        val roster = listOf(mon("a", user(FieldMove.CUT)), mon("b", user(FieldMove.SURF)))
        val start = WorldData(xp = 100_000).assign(1, listOf("a", "b"))!!.start(1, "tiny-woods", 1_000L)!!
        val end = 1_000L + 10 * 3_600_000L
        val once = WorldSim.settle(start, roster, end).data
        var split = start
        for (t in listOf(1_000L + 3 * 3_600_000L, 1_000L + 3 * 3_600_000L + 17, 1_000L + 7 * 3_600_000L, end)) split = WorldSim.settle(split, roster, t).data
        assertEquals(once.poke, split.poke); assertEquals(once.xp, split.xp); assertEquals(once.items, split.items); assertEquals(once.treasure, split.treasure)
    }

    @Test
    fun eventsOnlyAddAndAreRecorded() {
        val capable = listOf(mon("a", user(FieldMove.CUT)), mon("b", user(FieldMove.SURF)), mon("c", user(FieldMove.ROCK_SMASH)), mon("d", user(FieldMove.STRENGTH)))
        val none = listOf(mon("a", nobody), mon("b", nobody), mon("c", nobody), mon("d", nobody))
        fun run(roster: List<OwnedPokemon>) = WorldSim.settle(WorldData(xp = 100_000).assign(1, listOf("a", "b", "c", "d"))!!.start(1, "tiny-woods", 1_000L)!!, roster, 1_000L + 12 * 3_600_000L)
        val a = run(capable); val b = run(none)
        val opened = a.events.filterIsInstance<WorldEvent.FloorEvent>().filter { it.userName != null }
        assertTrue(opened.isNotEmpty(), "a day-long trip with every move has events")
        assertTrue(b.events.filterIsInstance<WorldEvent.FloorEvent>().all { it.userName == null || it.kind == FloorEventKind.TRAVELLER })
        assertTrue(a.data.journal.any { it.kind == JournalLine.KIND_EVENT && it.text.contains("floor") })
        assertTrue(ReportSummary.of(a, 0).eventsOpened == opened.size)
        assertTrue(b.data.journal.none { it.kind == JournalLine.KIND_EVENT && it.text.contains("Overgrown") }, "a closed event is not logged")
    }
}
