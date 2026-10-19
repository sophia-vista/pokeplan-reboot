package app.pokeplan.shared.pokemon

import app.pokeplan.shared.dex.Dex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransferTest {
    @Test
    fun yieldsFollowTheRarityOfTheLine() {
        val caterpie = pokemon(10)
        assertEquals(5, Transfer.quote(caterpie).rareCandies)
        assertEquals(10, Transfer.quote(caterpie).coins)
        val dratini = pokemon(147)
        assertEquals(40, Transfer.quote(dratini).rareCandies)
    }

    @Test
    fun fedCandiesPartlyComeBack() {
        val p = pokemon(10).copy(totalCandies = 50)
        assertEquals(5 + 10, Transfer.quote(p).rareCandies)
    }

    @Test
    fun valuablePokemonGetWarningsAndHeldItemsReturn() {
        val p = pokemon(10, heldItem = "lucky-egg").copy(isShiny = true, level = 60)
        val q = Transfer.quote(p)
        assertEquals("lucky-egg", q.returnedItem)
        assertTrue(q.warnings.any { "shiny" in it } && q.warnings.any { "perfect IVs" in it } && q.warnings.any { "level 60" in it })
        assertTrue(Transfer.quote(pokemon(10, ivs = StatSpread.of(5))).warnings.isEmpty())
    }

    @Test
    fun blockedTransfers() {
        val p = pokemon(10)
        assertEquals(TransferBlock.BUDDY, Transfer.block(p, isBuddy = true, partyCount = 3))
        assertEquals(TransferBlock.LAST_PARTY_MEMBER, Transfer.block(p, isBuddy = false, partyCount = 1))
        assertNull(Transfer.block(p.copy(inParty = false), isBuddy = false, partyCount = 1))
        assertNull(Transfer.block(p, isBuddy = false, partyCount = 2))
        val mew = Dex.species.first { it.name == "Mew" }
        assertEquals(TransferBlock.SPECIAL_SPECIES, Transfer.block(pokemon(mew.id), isBuddy = false, partyCount = 5))
        assertFailsWith<IllegalStateException> { Transfer.quote(pokemon(mew.id)) }
    }
}
