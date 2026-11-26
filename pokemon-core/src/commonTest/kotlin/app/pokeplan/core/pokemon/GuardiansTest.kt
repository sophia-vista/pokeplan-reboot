package app.pokeplan.core.pokemon

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.reward.PlayerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuardiansTest {
    private fun guardian(identifier: String, id: String = identifier): OwnedPokemon {
        val species = Dex.species.first { it.identifier == identifier }
        return pokemon(species.id).copy(id = id)
    }

    @Test
    fun everyModuleHasOneGuardianSlotInAFixedOrder() {
        assertEquals(listOf("plan", "care", "heal", "mint", "home", "link"), Guardians.MODULES)
    }

    @Test
    fun onlyLegendaryClassAndMythicalPokemonCanBeGuardians() {
        assertTrue(Guardians.canBe(guardian("mew")))
        assertTrue(Guardians.canBe(guardian("uxie")))
        assertFalse(Guardians.canBe(pokemon(Ids.PIKACHU)))
        assertFalse(Guardians.canBe(pokemon(Ids.GARCHOMP)))
    }

    @Test
    fun theLakeTrioFollowTheirLoreNotTheirType() {
        assertEquals(mapOf(PerkBucket.SUPER_CANDY_CHANCE to Guardians.SIGNATURE_PERCENT), Guardians.signature(guardian("uxie")))
        assertEquals(mapOf(PerkBucket.STREAK_KEEPER to Guardians.SIGNATURE_PERCENT), Guardians.signature(guardian("mesprit")))
        assertEquals(mapOf(PerkBucket.CANDY_YIELD to Guardians.SIGNATURE_PERCENT), Guardians.signature(guardian("azelf")))
    }

    @Test
    fun otherGuardiansFollowTheirTypesWithASecondTypeGivingHalf() {
        val hoOh = Guardians.signature(guardian("ho-oh"))
        assertEquals(Guardians.SIGNATURE_PERCENT, hoOh.getValue(PerkBucket.STREAK_KEEPER), 1e-9)
        assertEquals(Guardians.SIGNATURE_PERCENT * Guardians.SECOND_TYPE_SHARE, hoOh.getValue(PerkBucket.EGG_LUCK), 1e-9)
    }

    @Test
    fun guardianPerksStackButStayUnderTheirOwnCap() {
        val three = Guardians.perks(listOf(guardian("uxie", "a"), guardian("mewtwo", "b"), guardian("lugia", "c")))
        for ((_, v) in three) assertTrue(v <= Guardians.BUCKET_CAP)
        assertEquals(Guardians.BUCKET_CAP, three.getValue(PerkBucket.SUPER_CANDY_CHANCE), 1e-9)
    }

    @Test
    fun ordinaryPokemonSlottedAsGuardiansAreIgnored() {
        assertTrue(Guardians.perks(listOf(pokemon(Ids.PIKACHU))).isEmpty())
    }

    @Test
    fun guardianPerksAreAddedOutsideTheOrdinaryCapAndWorkWithoutABuddy() {
        val alone = Perks.total(null, emptyList(), PerkContext(), listOf(guardian("uxie")))
        assertEquals(Guardians.SIGNATURE_PERCENT, alone[PerkBucket.SUPER_CANDY_CHANCE], 1e-9)

        val buddy = pokemon(Ids.GARCHOMP, level = 100, friendship = 255)
        val without = Perks.total(buddy, emptyList())
        val with = Perks.total(buddy, emptyList(), PerkContext(), listOf(guardian("uxie")))
        assertEquals(without[PerkBucket.SUPER_CANDY_CHANCE] + Guardians.SIGNATURE_PERCENT, with[PerkBucket.SUPER_CANDY_CHANCE], 1e-9)
        assertTrue(with[PerkBucket.SUPER_CANDY_CHANCE] <= Perks.BUCKET_CAP + Guardians.BUCKET_CAP)
    }

    @Test
    fun legendsTakeCandiesAndLevelUpLikeAnyPokemon() {
        val mew = guardian("mew")
        assertTrue(mew.canFeed)
        assertTrue(pokemon(Ids.PIKACHU).canFeed)
        val fed = generateSequence(mew) { it.feedRareCandy() }.take(40).last()
        assertTrue(fed.level > mew.level, "candies raise a legend's level")
        assertTrue(mew.gainXp(CandyRules.XP_PER_CANDY * 40).level > mew.level, "XP does too")
        assertFalse(mew.copy(level = CandyRules.MAX_LEVEL).canFeed, "level 100 is the end")
    }

    @Test
    fun legendPowerIsTheBaseAtLevelOneAndDoubleAtLevelHundred() {
        assertEquals(1.0, LegendPower.scale(1), 1e-9)
        assertEquals(2.0, LegendPower.scale(100), 1e-9)
        assertEquals(10, LegendPower.scaled(10, 1))
        assertEquals(20, LegendPower.scaled(10, 100))
        assertEquals(3, LegendPower.scaled(3, 1))
        assertEquals(6, LegendPower.scaled(3, 100))
        assertEquals(4, LegendPower.scaled(3, 50), "rounded down")
        assertEquals(LegendPower.scaled(10, 1), LegendPower.scaled(10, 0), "below 1 counts as 1")
        assertEquals(LegendPower.scaled(10, 100), LegendPower.scaled(10, 500), "above 100 counts as 100")
    }

    @Test
    fun aGuardiansPerkGrowsWithItsLevelUnderTheSameCap() {
        val one = guardian("uxie")
        val hundred = one.copy(level = 100)
        assertEquals(Guardians.SIGNATURE_PERCENT, Guardians.signature(one).getValue(PerkBucket.SUPER_CANDY_CHANCE), 1e-9)
        assertEquals(Guardians.SIGNATURE_PERCENT * 2, Guardians.signature(hundred).getValue(PerkBucket.SUPER_CANDY_CHANCE), 1e-9)
        val hoOh = guardian("ho-oh").copy(level = 100)
        assertEquals(Guardians.SIGNATURE_PERCENT * 2 * Guardians.SECOND_TYPE_SHARE, Guardians.signature(hoOh).getValue(PerkBucket.EGG_LUCK), 1e-9, "the second type scales too")
        assertEquals(Guardians.BUCKET_CAP, Guardians.perks(listOf(hundred)).getValue(PerkBucket.SUPER_CANDY_CHANCE), 1e-9, "the cap still holds")
        val capped = Guardians.addTo(emptyMap<PerkBucket, Double>(), hundred) { PerkBucket.SUPER_CANDY_CHANCE }
        assertEquals(Guardians.BUCKET_CAP, capped.getValue(PerkBucket.SUPER_CANDY_CHANCE), 1e-9)
    }

    @Test
    fun theStateResolvesItsSlottedGuardians() {
        val mew = guardian("mew", "g1")
        val state = PlayerState(pokemon = listOf(pokemon(Ids.PIKACHU).copy(id = "p"), mew), moduleGuardians = mapOf(PartyIds.CARE to "g1", PartyIds.HEAL to "missing"))
        assertEquals(listOf(mew), state.guardianPokemon)
        assertEquals(mew, state.guardianOf(PartyIds.CARE))
        assertEquals(null, state.guardianOf(PartyIds.HEAL), "a Guardian that is not owned any more is none")
        assertEquals(null, state.guardianOf(PartyIds.PLAN))
    }

    @Test
    fun aModulesGuardianAddsItsTypePerkInThatModuleOnly() {
        val lugia = guardian("lugia")
        val lead = pokemon(Ids.PIKACHU, level = 50, friendship = 200)
        val without = CarePerks.total(lead, listOf(lead))
        val with = CarePerks.total(lead, listOf(lead), lugia)
        val added = Guardians.SIGNATURE_PERCENT + Guardians.SIGNATURE_PERCENT * Guardians.SECOND_TYPE_SHARE
        assertEquals((without[CareBucket.VISITOR_LUCK] ?: 0.0) + added.coerceAtMost(Guardians.BUCKET_CAP), with.getValue(CareBucket.VISITOR_LUCK), 1e-9)
        assertTrue(HealPerks.total(lead, listOf(lead), lugia) != HealPerks.total(lead, listOf(lead)) || HealPerks.bucket(PokemonType.PSYCHIC) == null && HealPerks.bucket(PokemonType.FLYING) != null)
    }

    @Test
    fun aGuardianAloneWorksWithNoLeadAndANonGuardianAddsNothing() {
        val uxie = guardian("uxie")
        assertTrue(MintPerks.total(null, emptyList(), uxie).isNotEmpty() || MintPerks.bucket(uxie.form.types.first()) == null, "no lead needed")
        assertTrue(MintPerks.total(null, emptyList()).isEmpty())
        assertTrue(LinkPerks.total(null, emptyList(), pokemon(Ids.PIKACHU)).isEmpty(), "an ordinary Pokémon in the Guardian slot gives nothing")
    }

    @Test
    fun theGuardianPartAddsOnTopOfTheOrdinaryCapButHasItsOwn() {
        val strong = pokemon(Ids.GARCHOMP, level = 100, friendship = 255)
        val party = List(6) { strong.copy(id = "m$it") }
        val groundish = guardian("groudon")
        val capped = BasePerks.total(party.first(), party)
        val with = BasePerks.total(party.first(), party, groundish)
        for ((b, v) in with) assertTrue(v <= Perks.BUCKET_CAP + Guardians.BUCKET_CAP + 1e-9, "$b $v")
        assertTrue(with.entries.any { (b, v) -> v > (capped[b] ?: 0.0) } || BasePerks.bucket(groundish.form.types.first()) == null)
    }
}
