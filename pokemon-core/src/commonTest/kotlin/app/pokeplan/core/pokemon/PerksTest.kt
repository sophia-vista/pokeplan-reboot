package app.pokeplan.core.pokemon

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.PokemonType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PerksTest {
    private fun strong(speciesId: Int, level: Int = 100, nature: Nature = Nature.HARDY, friendship: Int = 255, heldItem: String? = null, formIdentifier: String = Dex.defaultForm(speciesId).identifier) =
        pokemon(speciesId, formIdentifier = formIdentifier, level = level, nature = nature, friendship = friendship, heldItem = heldItem)

    @Test
    fun everyTypeHasABucketMatchingTheDesign() {
        assertEquals(18, PokemonType.entries.size)
        val buckets = PokemonType.entries.groupBy { TypePerks.bucket(it) }
        assertEquals(setOf(PokemonType.NORMAL, PokemonType.FLYING, PokemonType.FAIRY), buckets[PerkBucket.EGG_LUCK]!!.toSet())
        assertEquals(4, buckets[PerkBucket.CANDY_YIELD]!!.size)
        assertEquals(3, buckets[PerkBucket.SUPER_CANDY_CHANCE]!!.size)
        assertEquals(setOf(PokemonType.ELECTRIC, PokemonType.ICE), buckets[PerkBucket.SHINY_HUNTER]!!.toSet())
        assertEquals(setOf(PokemonType.WATER, PokemonType.FIRE, PokemonType.POISON), buckets[PerkBucket.STREAK_KEEPER]!!.toSet())
    }

    @Test
    fun everyCuratedAbilityExistsInTheData() {
        val known = (Dex.forms.flatMap { it.abilities } + Dex.forms.mapNotNull { it.hiddenAbility }).toSet()
        val missing = AbilityPerks.table.keys.filter { it !in known }
        assertTrue(missing.isEmpty(), "unknown abilities: $missing")
        assertTrue(AbilityPerks.table.size >= 25)
    }

    @Test
    fun perkGrowsWithLevelAndFriendshipAndStaysBounded() {
        val low = Perks.typePerks(strong(Ids.PIKACHU, level = 5, friendship = 70)).getValue(PerkBucket.SHINY_HUNTER)
        val high = Perks.typePerks(strong(Ids.PIKACHU, level = 100, friendship = 255)).getValue(PerkBucket.SHINY_HUNTER)
        assertTrue(high > low * 2, "$low -> $high")
        for (id in listOf(Ids.PIKACHU, Ids.GARCHOMP, Ids.EEVEE)) {
            val total = Perks.total(strong(id), emptyList())
            for (b in PerkBucket.entries) assertTrue(total[b] <= Perks.BUCKET_CAP, "$id $b ${total[b]}")
        }
    }

    @Test
    fun natureNudgesThePowerStat() {
        val boosted = Perks.typePerks(strong(Ids.PIKACHU, nature = Nature.MODEST)).getValue(PerkBucket.SHINY_HUNTER)
        val neutral = Perks.typePerks(strong(Ids.PIKACHU, nature = Nature.HARDY)).getValue(PerkBucket.SHINY_HUNTER)
        val lowered = Perks.typePerks(strong(Ids.PIKACHU, nature = Nature.GENTLE)).getValue(PerkBucket.SHINY_HUNTER)
        assertTrue(lowered > neutral, "Gentle raises Sp. Def")
        assertEquals(neutral, boosted, 1e-9, "Modest does not touch Sp. Def")
        val mild = Perks.typePerks(strong(Ids.PIKACHU, nature = Nature.NAUGHTY)).getValue(PerkBucket.SHINY_HUNTER)
        assertTrue(mild < neutral, "Naughty lowers Sp. Def")
    }

    @Test
    fun theSecondTypeGivesHalf() {
        val perks = Perks.typePerks(strong(Ids.GARCHOMP))
        assertTrue(perks.getValue(PerkBucket.CANDY_YIELD) > 0 && perks.getValue(PerkBucket.SUPER_CANDY_CHANCE) > 0)
        val sameBucket = Perks.typePerks(strong(Ids.PIKACHU).withTeraType(null))
        assertEquals(1, sameBucket.size)
    }

    @Test
    fun teraTypeReplacesThePerkTypes() {
        val tera = strong(Ids.GARCHOMP).withTeraType(PokemonType.WATER)
        assertEquals(setOf(PerkBucket.STREAK_KEEPER), Perks.typePerks(tera).keys)
    }

    @Test
    fun heldItemAndAbilityAdd() {
        val buddy = strong(Ids.PIKACHU, heldItem = "shiny-charm")
        val plain = Perks.total(strong(Ids.PIKACHU))
        val held = Perks.total(buddy)
        assertEquals(plain[PerkBucket.SHINY_HUNTER] + 4.0, held[PerkBucket.SHINY_HUNTER], 1e-9)
        assertEquals(1.04 + plain[PerkBucket.SHINY_HUNTER] / 100 - 0.0, held.multiplier(PerkBucket.SHINY_HUNTER), 1e-9)
    }

    @Test
    fun abilityPerksOnlyApplyInTheirContext() {
        // Early Bird is a flat candy perk: no reward may depend on the hour or on a first task of the day.
        val earlyBird = strong(Ids.PIKACHU).let { it.withAbility("Early Bird") }
        val plain = Perks.total(strong(Ids.PIKACHU))
        val flat = 5.0 * FriendshipRules.perkMultiplier(255)
        assertEquals(plain[PerkBucket.CANDY_YIELD] + flat, Perks.total(earlyBird)[PerkBucket.CANDY_YIELD], 1e-9)
        assertEquals(plain[PerkBucket.CANDY_YIELD] + flat, Perks.total(earlyBird, ctx = PerkContext(taskRank = 5, sessionMinutes = 15))[PerkBucket.CANDY_YIELD], 1e-9)

        val guts = strong(Ids.PIKACHU).withAbility("Guts")
        assertTrue(Perks.total(guts, ctx = PerkContext(taskRank = 5))[PerkBucket.CANDY_YIELD] > Perks.total(guts, ctx = PerkContext(taskRank = 3))[PerkBucket.CANDY_YIELD])

        val technician = strong(Ids.PIKACHU).withAbility("Technician")
        assertEquals(10.0 * FriendshipRules.perkMultiplier(255), Perks.total(technician, ctx = PerkContext(sessionMinutes = 15))[PerkBucket.COINS], 1e-9)
        assertEquals(0.0, Perks.total(technician, ctx = PerkContext(sessionMinutes = 45))[PerkBucket.COINS], 1e-9)
    }

    @Test
    fun synchronizeAndMagicGuardAreSpecial() {
        assertTrue(Perks.total(strong(Ids.PIKACHU).withAbility("Synchronize"))[PerkBucket.SUPER_CANDY_CHANCE] >= 3.0)
        assertTrue(Perks.total(strong(Ids.PIKACHU).withAbility("Magic Guard")).keepsFailedEgg)
        assertFalse(Perks.total(strong(Ids.PIKACHU)).keepsFailedEgg)
    }

    @Test
    fun partyMembersAssistAtAQuarter() {
        val buddy = strong(Ids.PIKACHU)
        val helper = strong(Ids.GARCHOMP).copy(id = "helper")
        val alone = Perks.total(buddy, emptyList())
        val together = Perks.total(buddy, listOf(buddy, helper))
        val expected = Perks.typePerks(helper).getValue(PerkBucket.CANDY_YIELD) * Perks.ASSIST_SHARE
        assertEquals(alone[PerkBucket.CANDY_YIELD] + expected, together[PerkBucket.CANDY_YIELD], 1e-9)
        val pcOnly = Perks.total(buddy, listOf(helper.copy(inParty = false)))
        assertEquals(alone[PerkBucket.CANDY_YIELD], pcOnly[PerkBucket.CANDY_YIELD], 1e-9)
    }

    @Test
    fun bucketsAreCapped() {
        val buddy = strong(Ids.PIKACHU, heldItem = "shiny-charm")
        val helpers = (1..5).map { strong(Ids.PIKACHU).copy(id = "h$it") }
        val total = Perks.total(buddy.withAbility("Illuminate"), helpers)
        assertTrue(total[PerkBucket.SHINY_HUNTER] <= Perks.BUCKET_CAP)
    }

    @Test
    fun streakKeeperNeedsEnoughStrength() {
        val weak = Perks.total(strong(Ids.CHARMANDER, level = 1, friendship = 70))
        assertEquals(0, weak.extraForgivenDays)
        val strongFire = Perks.total(strong(Ids.CHARMANDER, level = 100, friendship = 255, heldItem = "leftovers"))
        assertEquals(1, strongFire.extraForgivenDays)
    }

    @Test
    fun megaStoneBoostsEggLuckAndShinyOnly() {
        val charizard = 6
        val holding = strong(charizard, heldItem = Items.megaStone("charizard-mega-x"))
        assertTrue(Perks.megaReady(holding))
        assertFalse(Perks.megaReady(strong(charizard, heldItem = "lucky-egg")))
        val normal = Perks.total(holding)
        val mega = Perks.total(holding, ctx = PerkContext(megaActive = true))
        assertEquals(normal[PerkBucket.CANDY_YIELD], mega[PerkBucket.CANDY_YIELD], 1e-9)
    }

    @Test
    fun noBuddyMeansNoPerks() {
        val none = Perks.total(null)
        assertEquals(0.0, none[PerkBucket.CANDY_YIELD])
        assertEquals(1.0, none.multiplier(PerkBucket.EGG_LUCK))
    }
}
