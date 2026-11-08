package app.pokeplan.app

import app.pokeplan.core.reward.Feature
import app.pokeplan.core.reward.TrainerRank
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RankRevealTest {
    private val storage = MemoryStorage()
    private val s = GameStore(storage, object : Clock { override fun nowMillis() = 1_760_000_000_000L }, rng = Random(1)).also { it.start("Ash", 25) }

    private fun setPoints(points: Int) {
        storage.save(GameStore.json.encodeToString(AppData.serializer(), s.data.copy(player = s.data.player.copy(trainerPoints = points))))
        s.reload()
    }

    @Test
    fun dayOneShowsOnlyTheBasics() {
        assertTrue(s.sees(Feature.TASKS))
        assertFalse(s.sees(Feature.BAG))
        assertTrue(s.newlyOpened().isEmpty())
    }

    @Test
    fun ownedGuardianOpensTheGuardiansScreenAtAnyRank() {
        assertFalse(s.sees(Feature.GUARDIANS), "locked at rank 0 with no Guardian")
        s.grantPokemon(151, "test")   // Mew
        assertTrue(s.sees(Feature.GUARDIANS), "one legendary or mythical is enough")
        assertTrue(s.guardianCandidates.any { it.speciesId == 151 }, "and the screen lists it")
        assertFalse(s.sees(Feature.LEGENDARY_QUESTS), "nothing else opens early")
    }

    @Test
    fun aRankUpListsWhatOpenedOnce() {
        setPoints(TrainerRank.thresholds[2])
        assertTrue(s.sees(Feature.HELD_ITEMS))
        assertEquals(Feature.at(1) + Feature.at(2), s.newlyOpened(), "both ranks reached since the last card")
        s.markRankSeen()
        assertTrue(s.newlyOpened().isEmpty())
        setPoints(TrainerRank.thresholds[3])
        assertEquals(Feature.at(3), s.newlyOpened())
    }
}
