package app.pokeplan.app

import app.pokeplan.app.quest.Quests
import app.pokeplan.app.quest.Requirement
import app.pokeplan.app.quest.RequirementKind
import app.pokeplan.core.dex.Dex
import app.pokeplan.plan.FocusRecord
import app.pokeplan.plan.Rank
import app.pokeplan.plan.Task
import app.pokeplan.plan.TaskState
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuestsTest {
    private val zone = TimeZone.UTC

    @Test
    fun everyLegendaryClassAndMythicalSpeciesHasExactlyOneQuestExceptTheOnesWorldGives() {
        val covered = app.pokeplan.world.Legends.coveredSpecies
        assertTrue(covered.isNotEmpty() && covered.all { Dex.species(it).isSpecial })
        covered.forEach { assertNull(Quests.forSpecies(it), "World gives ${Dex.species(it).name}, so Plan has no quest for it") }
        val special = Dex.species.filter { it.isSpecial && it.id !in covered }
        assertTrue(special.size > 60, "the data has ${special.size} special species")
        assertEquals(special.map { it.id }.toSet(), Quests.all.map { it.speciesId }.toSet())
        assertEquals(special.size, Quests.all.size)
        assertEquals(Quests.all.size, Quests.all.map { it.id }.toSet().size, "quest ids are unique")
        for (q in Quests.all) {
            assertTrue(q.requirements.isNotEmpty() && q.requirements.all { it.target > 0 }, q.id)
            assertTrue(q.unlockRank in 0..9, q.id)
        }
    }

    @Test
    fun mythicalsGetLongTermQuestsAvailableEarly() {
        for (s in Dex.mythicals.filter { it.id !in app.pokeplan.world.Legends.coveredSpecies }) {
            val q = assertNotNull(Quests.forSpecies(s.id), s.name)
            if (q.id in setOf("uxie", "mesprit", "azelf")) continue
            assertTrue(q.longTerm, s.name)
            assertTrue(q.unlockRank <= 2, "${s.name} is available early")
            assertTrue(q.requirements.first { it.kind == RequirementKind.ACTIVE_DAYS }.target >= 30, "${s.name} takes weeks")
        }
    }

    @Test
    fun theLakeTrioHaveLoreQuestsAboutKnowledgeEmotionAndWillpower() {
        val uxie = Quests.get("uxie")!!
        assertEquals("The Being of Knowledge", uxie.title)
        assertTrue(uxie.requirements.any { it.kind == RequirementKind.MILESTONES_DONE })
        assertTrue(Quests.get("mesprit")!!.requirements.any { it.kind == RequirementKind.HABIT_DAYS })
        assertTrue(Quests.get("azelf")!!.requirements.any { it.kind == RequirementKind.DEEP_FOCUS_SESSIONS })
        assertEquals(Dex.species.first { it.identifier == "azelf" }.id, Quests.get("azelf")!!.speciesId)
    }

    @Test
    fun aQuestIsLockedUntilTheRankAndDexShareAreReached() {
        val legendary = Dex.species.first { it.isLegendary && !it.isMythical && Quests.forSpecies(it.id) != null && Quests.forSpecies(it.id)!!.dexRegion != null && Quests.forSpecies(it.id)!!.dexShare > 0 }
        val q = Quests.forSpecies(legendary.id)!!
        val fresh = AppData()
        assertNotNull(Quests.lockedReason(q, fresh))
        val ranked = AppData(player = fresh.player.copy(trainerPoints = 10_000))
        assertTrue(Quests.lockedReason(q, ranked)!!.startsWith("Catch"), "needs the dex share too")
        val region = q.dexRegion!!
        val caught = Dex.regionDex(region).map { it.id }.toSet()
        assertNull(Quests.lockedReason(q, AppData(player = fresh.player.copy(trainerPoints = 10_000, caught = caught))))
    }

    @Test
    fun noQuestAsksForARetiredRegion() {
        // The games' regions are retired (0.44.0), so a quest's dex share is only ever asked of the Plan or Care Region.
        assertTrue(Quests.all.all { q -> q.dexRegion == null || q.dexRegion!!.released })
    }

    @Test
    fun progressCountsOnlyWhatHappenedAfterAcceptance() {
        val since = 1_000_000L
        val state = QuestState(since)
        fun task(id: String, rank: Rank, doneAt: Long, paid: Boolean = true) =
            Task(id, id, rank = rank, state = TaskState.DONE, createdAtMillis = 0, completedAtMillis = doneAt, paid = paid)
        val data = AppData(
            tasks = listOf(task("old", Rank.A, since - 1), task("hard", Rank.B, since + 1), task("easy", Rank.E, since + 2), task("unpaid", Rank.A, since + 3, paid = false)),
            focusHistory = listOf(
                FocusRecord(since - 5, 25, true, deepFocus = true),
                FocusRecord(since + 5, 25, true, deepFocus = true),
                FocusRecord(since + 6, 30, true),
                FocusRecord(since + 7, 25, false, deepFocus = true),
            ),
        )
        fun count(kind: RequirementKind) = Quests.current(Requirement(kind, 1), state, data, zone, streak = 4)
        assertEquals(2, count(RequirementKind.FOCUS_SESSIONS))
        assertEquals(1, count(RequirementKind.DEEP_FOCUS_SESSIONS))
        assertEquals(55, count(RequirementKind.FOCUS_MINUTES))
        assertEquals(2, count(RequirementKind.TASKS_DONE), "paid tasks since acceptance")
        assertEquals(1, count(RequirementKind.TASKS_DONE_HARD))
        assertEquals(4, count(RequirementKind.STREAK_DAYS))
    }
}
