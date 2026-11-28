package app.pokeplan.app.world

import app.pokeplan.app.testing.FakeClock
import app.pokeplan.core.reward.Reward
import app.pokeplan.core.reward.TownFinds
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage

class TownFindsStoreTest {

    private val utc = TimeZone.UTC
    private fun at(day: Int, hour: Int) = LocalDateTime(2026, 11, day, hour, 0).toInstant(utc).toEpochMilliseconds()
    private val clock = FakeClock(at(14, 9))
    private val s = GameStore(MemoryStorage(), clock, utc, Random(3)).also { it.start("Ash", 25) }

    @Test
    fun eachFindIsCollectedOnceADayIntoTheBagOrAsPoke() {
        var day = 14
        while (s.townFinds().none { it != null }) clock.millis = at(++day, 9)
        val finds = s.townFinds()
        assertEquals(TownFinds.PER_DAY, finds.size)
        finds.forEachIndexed { i, find ->
            val f = find ?: return@forEachIndexed
            val pokeBefore = s.data.player.coins
            val bagBefore = s.data.player.inventory[f.itemId]
            val reward = assertNotNull(s.collectTownFind(i))
            if (f.isPoke) {
                assertEquals(Reward.Poke(f.amount), reward)
                assertEquals(pokeBefore + f.amount, s.data.player.coins)
            } else {
                assertEquals(Reward.ItemDrop(f.itemId, f.amount), reward)
                assertEquals(bagBefore + f.amount, s.data.player.inventory[f.itemId])
            }
            assertNull(s.collectTownFind(i), "gone once collected")
            assertNull(s.townFinds()[i])
        }
        assertTrue(s.data.ledgerNotes.any { it.kind == "town-find" })
        assertNull(s.collectTownFind(TownFinds.PER_DAY), "no fourth find")
        var later = day
        while (later < 28 && s.townFinds().none { it != null }) clock.millis = at(++later, 9)
        assertTrue(s.townFinds().any { it != null }, "a later day lays new finds")
    }

    @Test
    fun theFindsAreTheSameAllDayAndDifferFromDayToDay() {
        val today = TownFinds.forDay(20_000)
        assertEquals(today, TownFinds.forDay(20_000))
        assertNotEquals((0L..30L).map { TownFinds.forDay(20_000 + it) }.toSet().size, 1)
        val laid = (0L..400L).flatMap { TownFinds.forDay(it) }
        val share = laid.count { it != null } * 100.0 / laid.size
        assertTrue(share in (TownFinds.APPEAR_PERCENT - 7.0)..(TownFinds.APPEAR_PERCENT + 7.0), "about $share% of the slots hold a find")
        laid.filterNotNull().forEach { f ->
            assertTrue(f.itemId in TownFinds.weights)
            if (f.isPoke) assertTrue(f.amount in TownFinds.POKE_MIN..TownFinds.POKE_MAX) else assertEquals(1, f.amount)
        }
    }
}
