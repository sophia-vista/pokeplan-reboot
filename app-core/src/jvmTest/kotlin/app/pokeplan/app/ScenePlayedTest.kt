package app.pokeplan.app

import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.core.reward.Reward
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.TimeZone

class ScenePlayedTest {
    private class Clock(var millis: Long) : app.pokeplan.app.storage.Clock { override fun nowMillis() = millis }

    @Test
    fun playedArrivalSceneSurvivesARestartButTheRewardStaysUnseen() {
        val storage = MemoryStorage()
        val store = GameStore(storage, Clock(1_700_000_000_000L), TimeZone.UTC, Random(7))
        store.start("Sophia", 25)
        val entry = store.data.rewardLog.first { e -> e.rewards.any { it is Reward.Hatched } }
        val id = entry.rewards.filterIsInstance<Reward.Hatched>().first().pokemon.id
        assertFalse(entry.scenePlayed)

        store.markScenePlayed(id)

        val reopened = GameStore(MemoryStorage(storage.text), Clock(1_700_000_100_000L), TimeZone.UTC, Random(7))
        val after = reopened.data.rewardLog.first { e -> e.rewards.any { it is Reward.Hatched } }
        assertTrue(after.scenePlayed, "the played flag is saved, so opening the app does not replay the scene")
        assertFalse(after.seen, "the reward card still waits for its OK")
        assertEquals(entry.rewards, after.rewards)
    }
}
