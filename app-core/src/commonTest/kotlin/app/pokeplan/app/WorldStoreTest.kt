package app.pokeplan.app

import kotlinx.datetime.TimeZone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorldStoreTest {
    private class Clock(var millis: Long = 1_700_000_000_000L) : app.pokeplan.app.Clock { override fun nowMillis() = millis }

    private fun store(clock: Clock = Clock()) = GameStore(MemoryStorage(), clock, TimeZone.UTC, Random(5)).also { it.start("Sophia", 25) }

    @Test
    fun aTeamExploresWhileTimePassesAndItsFindsJoinTheCollection() {
        val clock = Clock()
        val store = store(clock)
        val team = listOf(store.data.player.pokemon.first().id, store.grantPokemon(25, "test").id, store.grantPokemon(74, "test").id)
        assertTrue(store.worldAssign(1, team))
        assertTrue(store.worldStart(1, "tiny-woods"))
        clock.millis += 8L * 60 * 60 * 1000
        val report = assertNotNull(store.worldSettle())
        assertTrue(report.data.xp > 0)
        // Finds are rare (about 3% of floors), so keep exploring, half a day at a time, until one comes.
        repeat(20) { if (store.data.world.finds.isEmpty()) { clock.millis += 12L * 60 * 60 * 1000; store.worldSettle() } }
        assertTrue(store.data.world.xp > 0 && store.data.world.poke > 0)
        assertTrue(store.data.world.finds.isNotEmpty(), "a few days find someone")
        val before = store.data.player.pokemon.size
        val recruit = assertNotNull(store.worldRecruit(0))
        assertEquals(before + 1, store.data.player.pokemon.size)
        assertEquals(app.pokeplan.app.Origin.How.WORLD, store.data.origins[recruit.id]?.how)
        assertTrue(recruit.id in store.data.player.pokemon.map { it.id }, "the same persistent Pokémon as any other")
        assertNull(store.worldRecruit(999))
    }

    @Test
    fun itemsMoveToTheBagAndTreasureOpensIntoWorldsItems() {
        val clock = Clock()
        val store = store(clock)
        store.worldAssign(1, listOf(store.data.player.pokemon.first().id))
        store.worldStart(1, "tiny-woods")
        clock.millis += 12L * 60 * 60 * 1000
        store.worldSettle()
        val claimed = store.data.world.items
        assertTrue(claimed.values.sum() > 0)
        val before = claimed.keys.associateWith { store.data.player.inventory[it] }
        assertEquals(claimed.values.sum(), store.worldClaimItems())
        assertTrue(store.data.world.items.isEmpty())
        assertEquals(0, store.worldClaimItems())
        claimed.forEach { (id, n) -> assertEquals(before.getValue(id) + n, store.data.player.inventory[id], "$id reached the Bag") }
        // A treasure box found along the way opens once.
        val tier = store.data.world.treasure.keys.firstOrNull()
        if (tier != null) {
            val found = store.worldOpenTreasure(tier)
            assertTrue(found.isNotEmpty())
            assertTrue(store.data.world.items.isNotEmpty())
        }
        assertTrue(store.worldOpenTreasure(3).isEmpty() || store.data.world.treasure[3] != null)
    }

    @Test
    fun theRulesRefuseWhatTheyShould() {
        val store = store()
        assertFalse(store.worldAssign(1, listOf("nobody")), "a Pokémon that is not owned cannot go")
        assertFalse(store.worldStart(1, "tiny-woods"), "an empty team cannot go")
        val id = store.data.player.pokemon.first().id
        assertTrue(store.worldAssign(1, listOf(id)))
        assertFalse(store.worldAssign(2, listOf(id)), "one team at a time")
        assertFalse(store.worldStart(1, "mt-steel"), "Mt. Steel needs a higher rank")
        assertTrue(store.worldStart(1, "tiny-woods"))
        store.worldRecall(1)
        assertNull(store.data.world.team(1)?.expedition)
    }

    @Test
    fun quickSendPicksTheStrongestFreePokemonAndNeverGuardians() {
        val store = store()
        store.grantPokemon(25, "test"); store.grantPokemon(74, "test"); store.grantPokemon(10, "test")
        val legend = store.grantPokemon(150, "test")
        assertTrue(store.worldQuickSend("tiny-woods"))
        val team = store.data.world.team(1)!!
        assertTrue(team.members.isNotEmpty() && team.members.size <= 4 && team.expedition != null)
        assertTrue(legend.id !in team.members, "a Guardian never goes exploring")
        assertTrue(store.worldQuickSend("tiny-woods").not() || store.data.world.team(2) != null)
    }
}
