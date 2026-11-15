package app.pokeplan.app

import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.pokemon.Shop
import app.pokeplan.core.reward.CareEvent
import app.pokeplan.core.reward.CareKind
import app.pokeplan.core.reward.Reward
import app.pokeplan.core.reward.RewardEngine
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage

/** Rules version 12: Care Eggs, Care's candies and the coin shop. */
class GenerousEconomyTest {
    private class FakeClock(var millis: Long) : Clock {
        override fun nowMillis() = millis
    }

    private val utc = TimeZone.UTC
    private val clock = FakeClock(LocalDateTime(2026, 10, 19, 9, 0).toInstant(utc).toEpochMilliseconds())
    private val s = GameStore(MemoryStorage(), clock, utc, Random(3)).also { it.start("Ash", 25) }

    private fun candies() = s.data.player.inventory[Items.RARE_CANDY]

    @Test
    fun aFullCareEggMeterHatchesACareEggIntoCaresParty() {
        val owned = s.data.player.pokemon.size
        s.care(CareEvent(CareKind.ROUTINE, clock.millis))
        s.care(CareEvent(CareKind.ROUTINE, clock.millis))
        assertEquals(2, s.careEggMeter())
        assertEquals(owned, s.data.player.pokemon.size)
        val rewards = s.care(CareEvent(CareKind.CALM_BREAK, clock.millis))!!
        assertTrue(rewards.any { it is Reward.Hatched }, "1 + 1 + 2 points fill the meter of 3")
        assertEquals(1, s.careEggMeter(), "the rest carries over")
        assertEquals(owned + 1, s.data.player.pokemon.size)
        assertEquals(PartyIds.CARE, s.data.player.pokemon.last().partyId)
    }

    @Test
    fun careMomentsPayRareCandies() {
        val before = candies()
        s.care(CareEvent(CareKind.ROUTINE, clock.millis))
        assertEquals(before + RewardEngine.CARE_CANDIES.getValue(CareKind.ROUTINE), candies())
    }

    @Test
    fun coinsBuyRareCandiesAtTheShop() {
        val task = s.addTask("Write", app.pokeplan.plan.Rank.A, planned = s.today)
        clock.millis += 3 * 60 * 60_000L
        s.completeTask(task.id)
        clock.millis += 20_000L
        s.tick() // past the Undo window: the task pays its coins
        val coins = s.data.player.coins
        val before = candies()
        val n = s.buyRareCandies(1_000_000)
        assertTrue(n > 0)
        assertEquals(coins / Shop.COINS_PER_RARE_CANDY, n, "as many as the coins allow")
        assertEquals(before + n, candies())
        assertEquals(coins % Shop.COINS_PER_RARE_CANDY, s.data.player.coins)
        assertEquals(0, s.buyRareCandies(1), "nothing left to spend")
    }
}
