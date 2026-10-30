package app.pokeplan.core.pokemon

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.model.PokemonType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OwnedPokemonTest {
    private fun feed(p: OwnedPokemon, times: Int, partner: Int = p.level) =
        (1..times).fold(p) { acc, _ -> acc.feedRareCandy(partner) }

    @Test
    fun enoughCandiesMakeOneLevelAndAddFriendship() {
        val p = feed(pokemon(Ids.BULBASAUR, level = 30, nature = Nature.ADAMANT), 4)
        assertEquals(30, p.level)
        assertEquals(4, p.candyProgress)
        val up = p.feedRareCandy()
        assertEquals(31, up.level)
        assertEquals(0, up.candyProgress)
        assertEquals(5, up.totalCandies)
        assertEquals(FriendshipRules.START + 1, up.friendship)
    }

    @Test
    fun higherLevelsCostMoreCandies() {
        assertEquals(2, CandyRules.candiesNeeded(1, 1))
        assertEquals(2, CandyRules.candiesNeeded(12, 12))
        assertEquals(5, CandyRules.candiesNeeded(30, 30))
        assertEquals(10, CandyRules.candiesNeeded(50, 50))
        assertEquals(34, CandyRules.candiesNeeded(99, 99))
        assertEquals(30, CandyRules.candiesBetween(1, 16), "Charmander to its level 16 evolution")
        assertEquals(106, CandyRules.candiesBetween(1, 36), "Charmeleon's level 36")
        assertEquals(1_249, CandyRules.candiesBetween(1, 100))
    }

    @Test
    fun aLaggingPokemonNeedsHalfTheCandies() {
        assertEquals(4, CandyRules.candiesNeeded(45, 60), "8 halved")
        val p = feed(pokemon(Ids.BULBASAUR, level = 45), 4, partner = 60)
        assertEquals(46, p.level)
        val close = feed(pokemon(Ids.BULBASAUR, level = 45), 4, partner = 50)
        assertEquals(45, close.level, "within 5 levels: no discount")
    }

    @Test
    fun levelStopsAtOneHundred() {
        val p = pokemon(Ids.BULBASAUR, level = 100)
        assertFalse(p.canFeed)
        assertFailsWith<IllegalStateException> { p.feedRareCandy() }
    }

    @Test
    fun friendshipDependsOnBondStyle() {
        assertEquals(4, FriendshipRules.amount(FriendshipRules.Source.PET, BondStyle.COMFORT))
        assertEquals(2, FriendshipRules.amount(FriendshipRules.Source.PET, BondStyle.PRESENCE))
        assertEquals(6, FriendshipRules.amount(FriendshipRules.Source.FOCUS_SESSION, BondStyle.PRESENCE))
        assertEquals(2, FriendshipRules.amount(FriendshipRules.Source.BERRY, BondStyle.GIFTS))
        assertEquals(3, FriendshipRules.amount(FriendshipRules.Source.PET, BondStyle.BALANCED))
        assertEquals(1.0, FriendshipRules.perkMultiplier(0))
        assertEquals(1.2, FriendshipRules.perkMultiplier(255), 1e-9)
    }

    @Test
    fun friendshipIsCapped() {
        val p = pokemon(Ids.PIKACHU, nature = Nature.BOLD, friendship = 254).gainFriendship(FriendshipRules.Source.PET)
        assertEquals(255, p.friendship)
    }

    @Test
    fun evolutionKeepsTheBuildAndTheAbilitySlot() {
        val p = pokemon(Ids.CHARMANDER, level = 16, nature = Nature.ADAMANT, friendship = 120, evs = StatSpread(attack = 40))
        val evolved = p.evolvedInto(5, "charmeleon")
        assertEquals("Charmeleon", evolved.nickname, "default nickname follows")
        assertEquals(p.ivs, evolved.ivs)
        assertEquals(p.evs, evolved.evs)
        assertEquals(Nature.ADAMANT, evolved.nature)
        assertEquals(120, evolved.friendship)
        assertEquals(Dex.form("charmeleon")!!.abilities.first(), evolved.ability)
        assertEquals("Flame", p.withNickname("Flame").evolvedInto(5, "charmeleon").nickname, "custom nickname stays")
    }

    @Test
    fun teraTypeChangesPerkTypesButNotTheRealTypes() {
        val p = pokemon(Ids.PIKACHU)
        assertEquals(listOf(PokemonType.ELECTRIC), p.perkTypes)
        val tera = p.withTeraType(PokemonType.WATER)
        assertEquals(listOf(PokemonType.WATER), tera.perkTypes)
        assertEquals(listOf(PokemonType.ELECTRIC), tera.form.types)
    }
}
