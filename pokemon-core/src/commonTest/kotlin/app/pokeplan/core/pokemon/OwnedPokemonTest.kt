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
    fun tenCandiesMakeOneLevelAndAddFriendship() {
        val p = feed(pokemon(Ids.BULBASAUR, nature = Nature.ADAMANT), 9)
        assertEquals(1, p.level)
        assertEquals(9, p.candyProgress)
        val up = p.feedRareCandy()
        assertEquals(2, up.level)
        assertEquals(0, up.candyProgress)
        assertEquals(10, up.totalCandies)
        assertEquals(FriendshipRules.START + 1, up.friendship)
    }

    @Test
    fun aLaggingPokemonNeedsHalfTheCandies() {
        val p = feed(pokemon(Ids.BULBASAUR), 5, partner = 40)
        assertEquals(2, p.level)
        val close = feed(pokemon(Ids.BULBASAUR), 5, partner = 6)
        assertEquals(1, close.level, "within 5 levels: no discount")
    }

    @Test
    fun levelStopsAtOneHundred() {
        val p = pokemon(Ids.BULBASAUR, level = 100)
        assertFalse(p.canFeed)
        assertFailsWith<IllegalStateException> { p.feedRareCandy() }
    }

    @Test
    fun trainingPlanSplitsEvsAndRespectsCaps() {
        val p = pokemon(Ids.PIKACHU).withTrainingPlan(listOf(Stat.ATTACK, Stat.SPEED))
        val trained = p.gainEvs(5)
        assertEquals(3, trained.evs.attack)
        assertEquals(2, trained.evs.speed)
        assertEquals(StatSpread.ZERO, pokemon(Ids.PIKACHU).gainEvs(5).evs, "no plan, no EVs")

        val nearCap = pokemon(Ids.PIKACHU, evs = StatSpread(attack = 251)).withTrainingPlan(listOf(Stat.ATTACK))
        assertEquals(252, nearCap.gainEvs(5).evs.attack)

        val nearTotal = pokemon(Ids.PIKACHU, evs = StatSpread(hp = 252, defense = 252, spDefense = 4)).withTrainingPlan(listOf(Stat.SPEED))
        assertEquals(2, nearTotal.gainEvs(5).evs.speed, "only 2 points of the 510 budget left")
    }

    @Test
    fun trainingPlanHoldsAtMostTwoDifferentStats() {
        assertFailsWith<IllegalArgumentException> { pokemon(Ids.PIKACHU).withTrainingPlan(listOf(Stat.HP, Stat.ATTACK, Stat.SPEED)) }
        assertFailsWith<IllegalArgumentException> { pokemon(Ids.PIKACHU).withTrainingPlan(listOf(Stat.HP, Stat.HP)) }
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
