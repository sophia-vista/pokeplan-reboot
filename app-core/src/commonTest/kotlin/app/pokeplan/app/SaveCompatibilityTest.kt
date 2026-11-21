package app.pokeplan.app

import app.pokeplan.core.dex.Region
import kotlinx.serialization.json.JsonObject
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.app.world.worldAssign
import app.pokeplan.app.world.worldSettle
import app.pokeplan.app.world.worldStart

class SaveCompatibilityTest {
    private class Clock(var millis: Long = 1_700_000_000_000L) : app.pokeplan.app.storage.Clock { override fun nowMillis() = millis }

    private fun store(storage: MemoryStorage = MemoryStorage()) = GameStore(storage, Clock(), kotlinx.datetime.TimeZone.UTC, Random(5)).also { if (storage.load() == null) it.start("Sophia", 25) }

    @Test
    fun aSaveMissingAnyOneFieldStillLoadsExceptTheFewTheGameCannotStartWithout() {
        val full = GameStore.json.encodeToJsonElement(AppData.serializer(), store().data) as JsonObject
        val refused = full.keys.filter { key ->
            val without = JsonObject(full.filterKeys { it != key })
            runCatching { GameStore.json.decodeFromJsonElement(AppData.serializer(), without) }.isFailure
        }
        assertTrue(refused.all { it in ALWAYS_THERE }, "fields an old save would not have but are required: ${refused - ALWAYS_THERE}")
    }

    @Test
    fun aSaveFromBeforeWorldAndNewerSettingsLoadsWithTheirDefaults() {
        val full = GameStore.json.encodeToJsonElement(AppData.serializer(), store().data) as JsonObject
        val settings = full["settings"] as JsonObject
        val old = JsonObject(full.filterKeys { it != "world" && it != "rewardLog" } + ("settings" to JsonObject(settings.filterKeys { it != "worldScroll" })))
        val loaded = GameStore.json.decodeFromJsonElement(AppData.serializer(), old)
        assertEquals(app.pokeplan.world.WorldData(), loaded.world)
        assertTrue(loaded.settings.worldScroll)
        assertTrue(loaded.rewardLog.isEmpty())
    }

    @Test
    fun storedNamesDoNotMove() {
        assertEquals("\"HOME\"", GameStore.json.encodeToString(Region.serializer(), Region.PLAN))
        val log = listOf(app.pokeplan.core.reward.Reward.Poke(30), app.pokeplan.core.reward.Reward.Coins(2), app.pokeplan.core.reward.Reward.ItemDrop("rare-candy", 1))
        val ser = kotlinx.serialization.builtins.ListSerializer(app.pokeplan.core.reward.Reward.serializer())
        assertEquals(log, GameStore.json.decodeFromString(ser, GameStore.json.encodeToString(ser, log)))
    }

    @Test
    fun aSaveWithRetiredSettingsStillLoads() {
        // The retire of careModule, worldKeepPartyOut and musicVolume must not stop an older save (Json ignores unknown keys).
        val full = GameStore.json.encodeToJsonElement(AppData.serializer(), store().data) as JsonObject
        val settings = LinkedHashMap<String, JsonElement>(full["settings"] as JsonObject).apply {
            put("careModule", JsonPrimitive(false))
            put("worldKeepPartyOut", JsonPrimitive(true))
            put("musicVolume", JsonPrimitive(0))
        }
        val root = LinkedHashMap<String, JsonElement>(full).apply { put("settings", JsonObject(settings)) }
        val loaded = GameStore.json.decodeFromJsonElement(AppData.serializer(), JsonObject(root))
        assertEquals(GameStore.json.decodeFromJsonElement(Settings.serializer(), full["settings"]!!), loaded.settings)
    }

    @Test
    fun exportHoldsTheWorldAndTheRewardHistoryAndEraseClearsThem() {
        val clock = Clock()
        val store = GameStore(MemoryStorage(), clock, kotlinx.datetime.TimeZone.UTC, Random(5)).also { it.start("Sophia", 25) }
        store.worldAssign(1, listOf(store.data.player.pokemon.first().id))
        store.worldStart(1, "tiny-woods")
        clock.millis += 12L * 3_600_000
        store.worldSettle()
        assertEquals(0, store.data.world.pokeEarned, "what World earned is in the one balance")
        assertTrue(store.data.player.coins > 0)
        val exported = Json.parseToJsonElement(store.exportJson()) as JsonObject
        assertNotNull(exported["world"], "the export has World's save")
        assertNotNull(exported["rewardLog"], "and the reward history")
        assertNull((exported["world"] as JsonObject)["poke"], "World's Poké is not stored")
        assertTrue(((exported["player"] as JsonObject)["coins"].toString().toInt()) > 0)
        store.eraseDevice()
        assertEquals(app.pokeplan.world.WorldData(), store.data.world)
        assertTrue(store.data.rewardLog.isEmpty())
        assertFalse(store.data.onboarded)
    }

    private companion object {
        val ALWAYS_THERE = setOf("player")
    }
}
