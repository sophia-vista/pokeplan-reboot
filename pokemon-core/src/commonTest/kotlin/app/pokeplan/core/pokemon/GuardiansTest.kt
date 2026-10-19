package app.pokeplan.core.pokemon

import app.pokeplan.core.dex.Dex
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
    fun slotsOpenWithRank() {
        assertEquals(0, Guardians.slots(0))
        assertEquals(0, Guardians.slots(3))
        assertEquals(1, Guardians.slots(4))
        assertEquals(1, Guardians.slots(5))
        assertEquals(2, Guardians.slots(6))
        assertEquals(3, Guardians.slots(9))
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
        val state = PlayerState(pokemon = listOf(pokemon(Ids.PIKACHU).copy(id = "p"), mew), guardians = listOf("g1", "missing"))
        assertEquals(listOf(mew), state.guardianPokemon)
    }
}
