package app.pokeplan.core.reward

import app.pokeplan.core.dex.Region
import app.pokeplan.core.pokemon.Ids
import app.pokeplan.core.pokemon.Inventory
import app.pokeplan.core.pokemon.pokemon
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RewardJsonTest {
    private val json = RewardJson.json
    private val buddy = pokemon(Ids.PIKACHU, level = 40).withHeldItem("lucky-egg")
    private val state = PlayerState(listOf(buddy), buddy.id, Inventory().add("rare-candy", 12).add("lure-fire", 1), coins = 50, eggMeter = 4, trainerPoints = 100, caught = setOf(25, 4))
    private val effort = EffortEvent(30, true, Region.KANTO, newPokemonId = "new-1", nowMillis = 1_700_000_000_000)
    private val achievement = AchievementEvent(4, Region.JOHTO, hourOfDay = 7, firstOfDay = true, onTime = true, newPokemonId = "new-2", nowMillis = 1_700_000_000_000)

    @Test
    fun stateAndEventsRoundTrip() {
        val encoded = json.encodeToString(PlayerState.serializer(), state)
        assertEquals(state, json.decodeFromString(PlayerState.serializer(), encoded))
        val e = json.encodeToString(EffortEvent.serializer(), effort)
        assertEquals(effort, json.decodeFromString(EffortEvent.serializer(), e))
        val a = json.encodeToString(AchievementEvent.serializer(), achievement)
        assertEquals(achievement, json.decodeFromString(AchievementEvent.serializer(), a))
    }

    @Test
    fun theJsonRunMatchesTheEngineForTheSameSeed() {
        val direct = RewardEngine.effort(state, effort, Random(99))
        val viaJson = json.decodeFromString(
            Outcome.serializer(),
            RewardJson.effort(json.encodeToString(PlayerState.serializer(), state), json.encodeToString(EffortEvent.serializer(), effort), 99),
        )
        assertEquals(direct, viaJson)
        val directWork = RewardEngine.achievement(state, achievement, Random(7))
        val viaJsonWork = json.decodeFromString(
            Outcome.serializer(),
            RewardJson.achievement(json.encodeToString(PlayerState.serializer(), state), json.encodeToString(AchievementEvent.serializer(), achievement), 7),
        )
        assertEquals(directWork, viaJsonWork)
    }

    @Test
    fun rewardsCarryAKindTagSoClientsCanTellThemApart() {
        val out = RewardJson.achievement(RewardJson.emptyState(), json.encodeToString(AchievementEvent.serializer(), achievement), 1)
        assertTrue("\"kind\":\"candies\"" in out && "\"kind\":\"coins\"" in out, out.take(300))
    }

    @Test
    fun anEmptyStateIsValidAndBadInputIsRefused() {
        assertEquals(PlayerState(), json.decodeFromString(PlayerState.serializer(), RewardJson.emptyState()))
        assertFailsWith<Exception> { RewardJson.effort(RewardJson.emptyState(), "{}", 1) }
        val bad = json.encodeToString(PlayerState.serializer(), state).replace("\"hp\":31", "\"hp\":99")
        assertFailsWith<Exception> { json.decodeFromString(PlayerState.serializer(), bad) }
    }

    @Test
    fun unknownFieldsFromANewerClientAreIgnored() {
        val withExtra = json.encodeToString(EffortEvent.serializer(), effort).replace("{", "{\"futureField\":1,")
        assertEquals(effort, json.decodeFromString(EffortEvent.serializer(), withExtra))
    }
}
