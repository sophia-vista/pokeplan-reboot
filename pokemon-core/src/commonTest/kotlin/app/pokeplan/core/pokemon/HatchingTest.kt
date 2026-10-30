package app.pokeplan.core.pokemon

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.Gender
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.dex.Region
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HatchingTest {
    private val twentyFive = 25

    private fun hatch(rng: Random, region: Region = Region.KANTO, party: Int = 0, deep: Boolean = false, id: String = "x") =
        Hatching.hatch(id, twentyFive, deep, region, party, 1_000L, rng)

    @Test
    fun sameSeedGivesTheSamePokemon() {
        assertEquals(hatch(Random(5)), hatch(Random(5)))
    }

    @Test
    fun hatchlingsAreValidAndFromTheRegion() {
        val rng = Random(11)
        for (region in Region.entries.filter { Dex.regionDex(it).isNotEmpty() }) repeat(150) {
            val p = hatch(rng, region)
            assertTrue(Dex.regionDex(region).any { it.id == p.speciesId }, "${p.nickname} not in $region")
            assertFalse(p.species.isSpecial)
            assertTrue(p.ivs.values.all { it in 0..31 })
            assertEquals(StatSpread.ZERO, p.evs)
            assertEquals(1, p.level)
            assertEquals(FriendshipRules.START, p.friendship)
            val form = p.form
            assertTrue(p.ability in form.abilities || p.ability == form.hiddenAbility, "${p.ability} for ${form.identifier}")
            assertEquals(p.species.name, p.nickname)
            assertEquals(1_000L, p.caughtAtMillis)
        }
    }

    @Test
    fun partyHoldsSixThenOverflowsToThePC() {
        assertTrue(hatch(Random(1), party = 5).inParty)
        assertFalse(hatch(Random(1), party = 6).inParty)
    }

    @Test
    fun alolaHatchesAlolanForms() {
        val rng = Random(3)
        val rattata = generateSequence { hatch(rng, Region.ALOLA) }.take(2000).firstOrNull { it.speciesId == Ids.RATTATA }
        assertEquals("rattata-alola", rattata?.formIdentifier)
    }

    private fun create(speciesId: Int, rng: Random = Random(2)) =
        Hatching.create("x", HatchOption(speciesId, Dex.defaultForm(speciesId).identifier), 0, 0, 25, rng)

    @Test
    fun genderFollowsTheSpeciesRatio() {
        assertNull(create(Ids.DITTO).gender, "genderless")
        repeat(30) {
            assertEquals(Gender.MALE, create(Ids.HITMONLEE, Random(it)).gender)
            assertEquals(Gender.FEMALE, create(Ids.KANGASKHAN, Random(it)).gender)
        }
        val rng = Random(8)
        val female = (1..4000).count { create(Ids.PIKACHU, rng).gender == Gender.FEMALE }
        assertTrue(female in 1800..2200, "Pikachu is 50% female: $female")
    }

    @Test
    fun cosmeticFormsAppearOnlyForSpeciesThatHaveThem() {
        val rng = Random(4)
        val unown = (1..400).map { create(Ids.UNOWN, rng).cosmeticForm }
        assertTrue(unown.count { it != null } > 300)
        assertTrue(unown.any { it == null }, "the plain form is one of the variants")
        assertNull(create(Ids.PIKACHU).cosmeticForm)
    }

    @Test
    fun shinyRateIsAboutOneIn192WithASmallLongSessionBonus() {
        val rng = Random(21)
        val n = 200_000
        val short = (1..n).count { Shiny.roll(rng, 5) }
        assertTrue(short in 930..1150, "5 minute sessions: $short of $n")
        val long = (1..n).count { Shiny.roll(rng, 240) }
        assertTrue(long in 1420..1700, "4 hour sessions: $long of $n (expected about 1,560)")
        assertTrue(Shiny.chance(240, 2.0) > Shiny.chance(240))
    }

    @Test
    fun personalityIsStored() {
        val seen = (1..50).map { hatch(Random(it)).personality }.toSet()
        assertTrue(seen.size > 40)
        assertTrue(seen.all { it in 0 until Hatching.PERSONALITY_RANGE })
    }
}
