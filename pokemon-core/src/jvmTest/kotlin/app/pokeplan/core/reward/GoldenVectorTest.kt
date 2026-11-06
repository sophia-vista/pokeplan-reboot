package app.pokeplan.core.reward

import app.pokeplan.core.dex.Region
import app.pokeplan.core.pokemon.Ids
import app.pokeplan.core.pokemon.Inventory
import app.pokeplan.core.pokemon.pokemon
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * One fixed input and the exact JSON the engine must give for it. The same file is checked on JavaScript by
 * `tools/server/smoke.mjs`, so the app (JVM / web) and the server (JS) can never drift apart.
 * Regenerate after a deliberate rule change: `UPDATE_GOLDEN=1 ./gradlew :pokemon-core:jvmTest --tests "*GoldenVectorTest*"`.
 * New outcomes need a new [RewardRules.VERSION]: regenerating refuses to write them under the version already in the file.
 */
class GoldenVectorTest {
    private val file = File("../tools/server/golden.json")
    private val json = RewardJson.json

    private fun build(): JsonObject {
        val buddy = pokemon(Ids.PIKACHU, level = 40).withHeldItem("lucky-egg").copy(inParty = true) // in Plan's party, where its lead is
        val state = PlayerState(listOf(buddy), buddy.id, Inventory().add("rare-candy", 12).add("lure-fire", 1), coins = 50, eggMeter = 4, trainerPoints = 100, caught = setOf(25, 4), planLeadId = buddy.id, rolesVersion = 1)
        val stateJson = json.encodeToString(PlayerState.serializer(), state)
        val effort = json.encodeToString(EffortEvent.serializer(), EffortEvent(30, true, Region.KANTO, epochDay = 11, newPokemonId = "new-1", nowMillis = 1_700_000_000_000))
        val work = json.encodeToString(AchievementEvent.serializer(), AchievementEvent(4, Region.JOHTO, hourOfDay = 7, firstOfDay = true, onTime = true, newPokemonId = "new-2", nowMillis = 1_700_000_000_000))
        val care = json.encodeToString(CareEvent.serializer(), CareEvent(CareKind.CALM_BREAK, nowMillis = 1_700_000_000_000))
        return buildJsonObject {
            put("rulesVersion", RewardRules.VERSION)
            put("state", stateJson)
            put("effortEvent", effort)
            put("effortSeed", 42)
            put("effortOutcome", RewardJson.effort(stateJson, effort, 42))
            put("achievementEvent", work)
            put("achievementSeed", 7)
            put("achievementOutcome", RewardJson.achievement(stateJson, work, 7))
            put("careEvent", care)
            put("careSeed", 3)
            put("careOutcome", RewardJson.care(stateJson, care, 3))
        }
    }

    @Test
    fun theEngineMatchesTheGoldenFile() {
        val actual = build()
        if (System.getenv("UPDATE_GOLDEN") != null) {
            val old = if (file.exists()) json.parseToJsonElement(file.readText()) as JsonObject else null
            val outcomesChanged = old != null && listOf("effortOutcome", "achievementOutcome", "careOutcome").any { it in old && old[it] != actual[it] }
            check(!outcomesChanged || old!!["rulesVersion"] != actual["rulesVersion"]) {
                "the outcomes changed: bump RewardRules.VERSION (now ${RewardRules.VERSION}) before regenerating tools/server/golden.json"
            }
            file.parentFile.mkdirs()
            file.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), actual) + "\n")
        }
        val expected = json.parseToJsonElement(file.readText()) as JsonObject
        for (key in actual.keys) {
            assertEquals((expected.getValue(key) as JsonPrimitive).content, (actual.getValue(key) as JsonPrimitive).content, "$key differs from tools/server/golden.json")
        }
    }
}
