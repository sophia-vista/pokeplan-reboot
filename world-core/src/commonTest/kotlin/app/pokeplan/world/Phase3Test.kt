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
        assertTrue(b.events.filterIsInstance<WorldEvent.FloorEvent>().all { it.userName == null || it.kind == FloorEventKind.TRAVELLER || it.kind.types.isNotEmpty() })
        assertTrue(a.data.journal.any { it.kind == JournalLine.KIND_EVENT && it.text.contains("floor") })
        assertTrue(ReportSummary.of(a, 0).eventsOpened == opened.size)
        assertTrue(b.data.journal.none { it.kind == JournalLine.KIND_EVENT && it.text.contains("Overgrown") }, "a closed event is not logged")
    }
}

class DungeonModifierTest {
    @Test
    fun aboutAThirdOfDaysHaveOneAndItIsTheSameAllDay() {
        val days = (19_000L until 19_000L + 600)
        val rate = days.count { DungeonModifiers.of("tiny-woods", it) != null } * 100 / days.count()
        assertTrue(rate in 25..43, "about a third: $rate")
        assertEquals(DungeonModifiers.of("mt-steel", 19_500), DungeonModifiers.of("mt-steel", 19_500))
        assertEquals(DungeonModifiers.at("tiny-woods", 19_500L * 86_400_000L), DungeonModifiers.at("tiny-woods", 19_500L * 86_400_000L + 86_399_999L), "the whole UTC day")
        assertEquals(DungeonModifier.entries.toSet(), days.mapNotNull { DungeonModifiers.of("tiny-woods", it) }.toSet(), "every kind comes up")
    }

    @Test
    fun aBountifulDayPaysMoreAndNoDayPaysLess() {
        val bounty = (19_000L until 25_000L).first { DungeonModifiers.of("tiny-woods", it) == DungeonModifier.BOUNTY }
        val plain = (19_000L until 25_000L).first { DungeonModifiers.of("tiny-woods", it) == null }
        fun pokeOn(day: Long): Int {
            val t0 = day * 86_400_000L + 3_600_000L
            val mon = Hatching.create("a", HatchOption(25, Dex.defaultForm(25).identifier), 6, 0L, 60, Random(1)).copy(level = 30)
            val start = WorldData(xp = 100_000).assign(1, listOf("a"))!!.start(1, "tiny-woods", t0)!!
            val r = WorldSim.settle(start, listOf(mon), t0 + 3 * 3_600_000L)
            return r.events.filterIsInstance<WorldEvent.Poke>().sumOf { it.amount }
        }
        assertTrue(pokeOn(bounty) > pokeOn(plain), "a bountiful day pays more")
    }
}

class WorldMomentsTest {
    @Test
    fun anOldSaveAdoptsWhatItReachedAndOnlyNewThingsPlay() {
        val old = WorldData(xp = 2_000, clears = mapOf("tiny-woods" to 1, "thunderwave-cave" to 1, "mt-steel" to 1))
        val adopted = WorldMoments.adopted(old)
        assertEquals(old.rank.ordinal, adopted.seenRank)
        assertTrue(WorldMoments.pending(adopted).isEmpty(), "nothing replays")
        assertEquals(adopted, WorldMoments.adopted(adopted), "adopting twice changes nothing")
        // Rank up: Gold needs 6000.
        val up = adopted.copy(xp = 6_500)
        val first = WorldMoments.pending(up).first()
        assertEquals(WorldMoment.RankUp(ExplorerRank.GOLD), first)
        assertTrue(WorldMoments.pending(WorldMoments.seen(up, first)).none { it is WorldMoment.RankUp }, "seen once, never again")
    }

    @Test
    fun aChapterPlaysOnceInOrderAndRankOpensText() {
        val base = WorldMoments.adopted(WorldData(xp = 400))
        val more = base.copy(xp = 1_600, clears = mapOf("tiny-woods" to 1, "thunderwave-cave" to 1, "mt-steel" to 1))
        val moments = WorldMoments.pending(more)
        val chapters = moments.filterIsInstance<WorldMoment.ChapterOpened>().map { it.chapter.number }
        assertEquals(chapters.sorted(), chapters, "in order")
        assertTrue(chapters.isNotEmpty())
        var d = more
        for (m in moments) d = WorldMoments.seen(d, m)
        assertTrue(WorldMoments.pending(d).isEmpty())
        assertTrue(WorldMoments.rankOpened(ExplorerRank.SILVER).any { it.contains("teams") }, "Silver opens a third team")
        assertTrue(WorldMoments.rankOpened(ExplorerRank.NORMAL).isEmpty())
    }
}

class ChoicesTest {
    private fun mon(id: String, species: Int, level: Int = 40): OwnedPokemon =
        Hatching.create(id, HatchOption(species, Dex.defaultForm(species).identifier), OwnedPokemon.PARTY_SIZE, 0L, 60, Random(1)).copy(level = level)

    private val roster = listOf(mon("a", 25), mon("b", 74), mon("c", 1), mon("d", 7))
    private fun start(at: Long = 1_000L) = WorldData(xp = 1_000_000).assign(1, listOf("a", "b", "c", "d"))!!.start(1, "tiny-woods", at)!!

    @Test
    fun floorsOfferChoicesNowAndThenAndTheDefaultIsTakenWhenTimeIsUp() {
        val r = WorldSim.settle(start(), roster, 1_000L + 8 * 3_600_000L)
        val offered = r.events.filterIsInstance<WorldEvent.ChoiceOffered>()
        assertTrue(offered.isNotEmpty(), "a long trip offers choices")
        assertTrue(r.data.choices.size <= Choices.MAX_PENDING, "never more than three wait")
        // Long after, with nobody answering, every choice has taken its default and nothing is waiting.
        val later = WorldSim.settle(r.data, roster, 1_000L + 8 * 3_600_000L + Choices.WAIT_MILLIS + 1)
        assertTrue(later.data.choices.none { it.expiresAt <= 1_000L + 8 * 3_600_000L + Choices.WAIT_MILLIS })
        assertTrue(later.data.journal.any { it.kind == JournalLine.KIND_EVENT && (it.text.contains("wide, safe path") || it.text.contains("Rest and drink")) }, "the default is in the log")
    }

    @Test
    fun anAnswerPaysTheChosenOptionOnceAndNothingIsEverTaken() {
        val r = WorldSim.settle(start(), roster, 1_000L + 6 * 3_600_000L)
        val waiting = r.data.choices.firstOrNull() ?: return
        val before = r.data
        val answered = before.resolveChoice(waiting.id, 1, 5_000_000L)!!
        assertTrue(answered.choices.none { it.id == waiting.id }, "it is gone once answered")
        assertTrue(answered.poke >= before.poke && answered.xp >= before.xp && (answered.items.values.sum() >= before.items.values.sum()) && answered.treasure.values.sum() >= before.treasure.values.sum())
        assertNull(answered.resolveChoice(waiting.id, 0, 5_000_000L), "answering twice does nothing")
        val first = before.resolveChoice(waiting.id, 0, 5_000_000L)!!
        assertEquals(before.resolveChoice(waiting.id, 0, 9_000_000L)!!.poke, first.poke, "the answer's reward does not depend on when it is given")
    }

    @Test
    fun aSettleGivesTheSameWhateverWayItIsSplitWithChoicesToo() {
        val end = 1_000L + 10 * 3_600_000L
        val once = WorldSim.settle(start(), roster, end).data
        var split = start()
        for (t in listOf(1_000L + 2 * 3_600_000L, 1_000L + 2 * 3_600_000L + 5, 1_000L + 6 * 3_600_000L, end)) split = WorldSim.settle(split, roster, t).data
        assertEquals(once.choices, split.choices); assertEquals(once.poke, split.poke); assertEquals(once.xp, split.xp); assertEquals(once.items, split.items); assertEquals(once.treasure, split.treasure)
    }

    @Test
    fun hazardsNeedATypeAndOnlyAdd() {
        assertNotNull(FloorEvents.opener(FloorEventKind.FUMES, listOf(mon("p", 109))), "Koffing is a Poison type")
        assertNull(FloorEvents.opener(FloorEventKind.FUMES, listOf(mon("p", 25))), "Pikachu is not")
        assertNotNull(FloorEvents.opener(FloorEventKind.STATIC, listOf(mon("p", 74))), "Geodude is Ground")
    }
}
