package app.pokeplan.shared.dex

import app.pokeplan.shared.model.PokemonType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DexDataTest {
    @Test
    fun hasEveryNationalDexSpeciesThroughGenNine() {
        assertEquals((1..1025).toList(), Dex.species.map { it.id })
        assertEquals(9, Dex.species.maxOf { it.generation })
        assertEquals(Dex.species.size, Dex.species.map { it.name }.toSet().size, "names are unique")
    }

    @Test
    fun everySpeciesHasADefaultFormWithTypesAndStats() {
        for (s in Dex.species) {
            val f = Dex.defaultForm(s.id)
            assertTrue(f.types.size in 1..2, "${s.name} types")
            assertTrue(f.stats.total in 150..800, "${s.name} stats ${f.stats.total}")
            assertTrue(f.abilities.isNotEmpty() || f.hiddenAbility != null, "${s.name} abilities")
        }
    }

    @Test
    fun everyFormPointsAtAKnownSpeciesAndIsKnownByIdentifier() {
        for (f in Dex.forms) {
            assertNotNull(Dex.speciesOrNull(f.speciesId), f.identifier)
            assertEquals(f, Dex.form(f.identifier))
        }
        assertEquals(Dex.species.size, Dex.forms.count { it.kind == FormKind.DEFAULT })
    }

    @Test
    fun knownFormsAreClassified() {
        assertEquals(FormKind.MEGA, Dex.form("charizard-mega-x")?.kind)
        assertEquals(FormKind.MEGA, Dex.form("charizard-mega-y")?.kind)
        assertEquals(FormKind.GMAX, Dex.form("pikachu-gmax")?.kind)
        assertEquals(FormKind.REGIONAL, Dex.form("raichu-alola")?.kind)
        assertEquals(Region.ALOLA, Dex.regionOfForm(Dex.form("raichu-alola")!!))
        assertEquals(Region.SINNOH, Dex.regionOfForm(Dex.form("growlithe-hisui")!!))
        assertEquals(Region.PALDEA, Dex.regionOfForm(Dex.form("wooper-paldea")!!))
        assertEquals(FormKind.GENDER, Dex.form("meowstic-female")?.kind)
        assertEquals(FormKind.ALTERNATE, Dex.form("deoxys-attack")?.kind)
        assertEquals(listOf(PokemonType.ELECTRIC, PokemonType.PSYCHIC), Dex.form("raichu-alola")?.types)
    }

    @Test
    fun cosmeticFormsAreCollected() {
        assertTrue(Dex.species(201).cosmeticForms.size >= 26, "Unown letters")
        assertTrue(Dex.species(666).cosmeticForms.size >= 18, "Vivillon patterns")
        assertTrue(Dex.species(869).cosmeticForms.size >= 50, "Alcremie")
    }

    @Test
    fun legendaryAndMythicalFlagsAndSpecialLists() {
        assertTrue(Dex.species.count { it.isLegendary } >= 60)
        assertTrue(Dex.mythicals.size >= 20)
        val byIdentifier = Dex.species.associateBy { it.identifier }
        for (id in SpecialSpecies.legendaryClass) assertTrue(id in byIdentifier, "unknown special species $id")
        assertTrue(byIdentifier.getValue("iron-treads").isSpecial)
        assertTrue(byIdentifier.getValue("nihilego").isSpecial)
        assertTrue(byIdentifier.getValue("mew").isSpecial)
        assertTrue(!byIdentifier.getValue("dragonite").isSpecial)
    }

    @Test
    fun everySpeciesHasPokedexText() {
        for (s in Dex.species) {
            val text = Dex.entry(s.id)
            assertTrue(text.length in 30..400, "${s.name}: ${text.length} chars")
            assertTrue(!text.contains('\n') && !text.contains("  "), "${s.name} has stray whitespace")
        }
        assertTrue(Dex.entry(1).startsWith("A strange seed") || Dex.entry(1).contains("seed"), Dex.entry(1))
    }

    @Test
    fun genderRatiosArePresent() {
        assertEquals(null, Dex.species(132).femaleEighths, "Ditto is genderless")
        assertEquals(0, Dex.species(106).femaleEighths, "Hitmonlee is male only")
        assertEquals(8, Dex.species(115).femaleEighths, "Kangaskhan is female only")
        assertEquals(4, Dex.species(25).femaleEighths)
    }
}
