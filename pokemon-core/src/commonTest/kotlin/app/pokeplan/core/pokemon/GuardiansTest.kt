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
        val hoOh = Guardians.signature(guardian("ho-oh")) // Fire / Flying
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
    fun guardiansDoNotTakeCandies() {
        assertFalse(guardian("mew").canFeed)
        assertTrue(pokemon(Ids.PIKACHU).canFeed)
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
        val lugia = guardian("lugia")                              // Psychic / Flying
        val lead = pokemon(Ids.PIKACHU, level = 50, friendship = 200)
        val without = CarePerks.total(lead, listOf(lead))
        val with = CarePerks.total(lead, listOf(lead), lugia)
        // Care's buckets: Psychic and Flying both feed Visitor luck; the second type gives half.
        val added = Guardians.SIGNATURE_PERCENT + Guardians.SIGNATURE_PERCENT * Guardians.SECOND_TYPE_SHARE
        assertEquals((without[CareBucket.VISITOR_LUCK] ?: 0.0) + added.coerceAtMost(Guardians.BUCKET_CAP), with.getValue(CareBucket.VISITOR_LUCK), 1e-9)
        // The same Guardian given to Heal changes Heal's buckets by Heal's mapping, not Care's.
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
        val capped = HomePerks.total(party.first(), party)
        val with = HomePerks.total(party.first(), party, groundish)
        for ((b, v) in with) assertTrue(v <= Perks.BUCKET_CAP + Guardians.BUCKET_CAP + 1e-9, "$b $v")
        assertTrue(with.entries.any { (b, v) -> v > (capped[b] ?: 0.0) } || HomePerks.bucket(groundish.form.types.first()) == null)
    }
}
