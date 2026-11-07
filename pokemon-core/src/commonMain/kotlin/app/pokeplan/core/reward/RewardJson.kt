package app.pokeplan.core.reward

import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * The reward engine over JSON, for the server and for clients that sync. State, events and outcomes are the same JSON in
 * both directions, and the `seed` makes a run replayable: store it with the ledger row and the outcome can be reproduced.
 */
object RewardJson {
    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        classDiscriminator = "kind"
    }

    fun emptyState(): String = json.encodeToString(PlayerState.serializer(), PlayerState())

    fun effort(stateJson: String, eventJson: String, seed: Int): String {
        val state = json.decodeFromString(PlayerState.serializer(), stateJson)
        val event = json.decodeFromString(EffortEvent.serializer(), eventJson)
        return json.encodeToString(Outcome.serializer(), RewardEngine.effort(state, event, Random(seed)))
    }

    fun achievement(stateJson: String, eventJson: String, seed: Int): String {
        val state = json.decodeFromString(PlayerState.serializer(), stateJson)
        val event = json.decodeFromString(AchievementEvent.serializer(), eventJson)
        return json.encodeToString(Outcome.serializer(), RewardEngine.achievement(state, event, Random(seed)))
    }

    fun care(stateJson: String, eventJson: String, seed: Int): String {
        val state = json.decodeFromString(PlayerState.serializer(), stateJson)
        val event = json.decodeFromString(CareEvent.serializer(), eventJson)
        return json.encodeToString(Outcome.serializer(), RewardEngine.care(state, event, Random(seed)))
    }

    fun heal(stateJson: String, eventJson: String, seed: Int): String {
        val state = json.decodeFromString(PlayerState.serializer(), stateJson)
        val event = json.decodeFromString(HealEvent.serializer(), eventJson)
        return json.encodeToString(Outcome.serializer(), RewardEngine.heal(state, event, Random(seed)))
    }
}
