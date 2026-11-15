package app.pokeplan.app

import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.ItemUse
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.Nature
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.pokemon.PerkBucket
import app.pokeplan.core.pokemon.PerkSource
import app.pokeplan.core.pokemon.Stat
import app.pokeplan.core.pokemon.StatCalculator
import app.pokeplan.core.reward.Reward
import app.pokeplan.plan.FocusEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage

class PerksStoreTest {
    private class FakeClock(var millis: Long) : Clock {
        override fun nowMillis() = millis
        fun advance(ms: Long) { millis += ms }
    }

    private val utc = TimeZone.UTC
    private val start = LocalDateTime(2026, 10, 19, 9, 0).toInstant(utc).toEpochMilliseconds()
    private val storage = MemoryStorage()
    private val clock = FakeClock(start)
    private val s = GameStore(storage, clock, utc, Random(7)).also {
        it.start("Sophia", 25)
        // The perks come from Plan's party, so the starter works there (and is no longer the buddy: one place only).
        it.setInParty(it.data.player.buddyId!!, true)
    }

    /** Changes the save behind the store's back, the way a widget would, and picks it up. */
    private fun edit(change: (AppData) -> AppData) {
        storage.save(GameStore.json.encodeToString(AppData.serializer(), change(s.data)))
        s.reload()
    }

    private fun editBuddy(change: (OwnedPokemon) -> OwnedPokemon) = edit { d ->
        d.copy(player = d.player.copy(pokemon = d.player.pokemon.map { if (it.id == d.player.planLeadId) change(it) else it }))
    }

    private fun give(item: String, n: Int = 1) = edit { d -> d.copy(player = d.player.copy(inventory = d.player.inventory.add(item, n))) }

    private val buddy get() = s.data.player.planLead!!

    @Test
    fun aHeldItemLeavesTheBagAndComesBack() {
        give("lucky-egg")
        assertTrue(s.holdItem(buddy.id, "lucky-egg"))
        assertEquals("lucky-egg", buddy.heldItem)
        assertEquals(0, s.data.player.inventory["lucky-egg"])
        assertTrue(s.perkSources.any { it.kind == PerkSource.Kind.HELD_ITEM && it.bucket == PerkBucket.CANDY_YIELD })

        give("amulet-coin")
        assertTrue(s.holdItem(buddy.id, "amulet-coin"))
        assertEquals("amulet-coin", buddy.heldItem)
        assertEquals(1, s.data.player.inventory["lucky-egg"], "the swapped-out item goes back to the bag")

        s.takeItem(buddy.id)
        assertNull(buddy.heldItem)
        assertEquals(1, s.data.player.inventory["amulet-coin"])
    }

    @Test
    fun onlyHoldableItemsCanBeHeld() {
        give("protein")
        assertFalse(s.holdItem(buddy.id, "protein"))
        assertFalse(s.holdItem(buddy.id, "lucky-egg"), "none in the bag")
        assertTrue(Items.holdable("metal-coat"), "trade evolution items can be held")
    }

    @Test
    fun aBottleCapAndAMintNeedAChoice() {
        editBuddy { it.withIv(Stat.SPEED, 3) }
        give(Items.BOTTLE_CAP); give(Items.NATURE_MINT)
        assertFalse(s.useItem(Items.BOTTLE_CAP, buddy.id), "without a stat it does nothing")
        assertTrue(s.useItem(ItemUse.BottleCap(Stat.SPEED), buddy.id))
        assertEquals(StatCalculator.MAX_IV, buddy.ivs[Stat.SPEED])
        give(Items.BOTTLE_CAP)
        assertFalse(s.useItem(ItemUse.BottleCap(Stat.SPEED), buddy.id), "a perfect IV keeps the cap in the bag")
        assertEquals(1, s.data.player.inventory[Items.BOTTLE_CAP])
        assertFalse(s.useItem(ItemUse.NatureMint(buddy.nature), buddy.id), "the nature it has already")
        val other = Nature.entries.first { it != buddy.nature }
        assertTrue(s.useItem(ItemUse.NatureMint(other), buddy.id))
        assertEquals(other, buddy.nature)
        assertEquals(0, s.data.player.inventory[Items.NATURE_MINT])
    }

    @Test
    fun magicGuardHatchesAFailedSessionOnceAWeek() {
        editBuddy { it.withAbility("Magic Guard") }
        val before = s.data.player.pokemon.size
        s.startFocus(25, deepFocus = false)
        clock.advance(10 * 60_000L)
        s.focusEvent(FocusEvent.GaveUp)
        assertEquals(before + 1, s.data.player.pokemon.size, "the egg hatched anyway")
        assertTrue(s.data.lastFocusEnd!!.guarded)
        assertFalse(s.data.focusHistory.last().completed)
        assertTrue(s.data.rewardLog.last().rewards.any { it is Reward.Hatched })

        s.dismissFocusEnd()
        s.startFocus(25, deepFocus = false)
        clock.advance(10 * 60_000L)
        val second = s.focusEvent(FocusEvent.GaveUp)
        assertIs<FocusResult.Failed>(second, "only once a week")
        assertEquals(before + 1, s.data.player.pokemon.size)
    }

    @Test
    fun magicGuardNeedsTheMinimumEggTime() {
        editBuddy { it.withAbility("Magic Guard") }
        s.startFocus(25, deepFocus = false)
        clock.advance(2 * 60_000L)
        assertIs<FocusResult.Failed>(s.focusEvent(FocusEvent.GaveUp))
    }

    @Test
    fun theStreakKeeperPerkForgivesASecondMissedDay() {
        // Active Monday to Wednesday and Saturday: Thursday and Friday are missed, which needs two free misses.
        val days = listOf(19, 20, 21, 24).map { LocalDate(2026, 10, it) }.toSet()
        clock.millis = LocalDateTime(2026, 10, 24, 12, 0).toInstant(utc).toEpochMilliseconds()
        edit { it.copy(activeDays = days) }
        assertEquals(1, s.streak.current, "one free miss a week: the streak restarts on Saturday")
        // A strong Streak keeper perk: a Water Tera type, a Regenerator ability and Leftovers on a fond buddy.
        give("leftovers")
        s.holdItem(buddy.id, "leftovers")
        editBuddy { it.withAbility("Regenerator").withTeraType(PokemonType.WATER).copy(level = 50, friendship = 255) }
        assertEquals(1, s.perks.extraForgivenDays)
        assertEquals(4, s.streak.current)
    }
}
