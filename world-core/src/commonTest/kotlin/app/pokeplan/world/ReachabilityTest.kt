package app.pokeplan.world

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.FormKind
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.dex.Rarity
import app.pokeplan.core.dex.Region
import app.pokeplan.core.pokemon.EvolutionRules
import app.pokeplan.core.pokemon.Hatching
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The whole collection has to be reachable: a module region's eggs for the default form, a WORLD wild
 * find of its home layer for the regional form, then evolution. Any species with no route is a bug.
 */
class ReachabilityTest {
    private var next = 0
    private val rng = Random(11)

    private fun mon(species: Int, form: String) =
        Hatching.create("reach-${next++}", HatchOption(species, form), 0, 0L, 1, rng).copy(level = 100)

    private fun starts(): Set<Pair<Int, String>> {
        val eggs = Region.entries.filter { it.isModule }.flatMap { region -> Rarity.entries.flatMap { HatchRules.pool(region, it) } }
            .map { it.speciesId to it.formIdentifier }
        val wild = Dungeons.all.flatMap { dungeon ->
            dungeon.wild.filter { !Dex.species(it.speciesId).isSpecial }.flatMap { entry ->
                RegionalForms.inLayer(entry.speciesId, dungeon.layer).map { entry.speciesId to it.identifier }
            }
        }
        val legs = Legends.all.mapNotNull { quest -> quest.form?.let { quest.speciesId to it } }
        return (eggs + wild + legs).toSet()
    }

    private fun reached(): Set<Pair<Int, String>> {
        val reach = starts().toMutableSet()
        val queue = ArrayDeque(reach)
        while (queue.isNotEmpty()) {
            val (species, form) = queue.removeFirst()
            for (e in EvolutionRules.applicable(mon(species, form))) {
                val node = e.to to (e.evolvedForm ?: Dex.defaultForm(e.to).identifier)
                if (reach.add(node)) queue += node
            }
        }
        return reach
    }

    @Test
    fun everyNonSpecialSpeciesHasAWayIn() {
        val reach = reached()
        val missing = Dex.species.filter { !it.isSpecial && reach.none { r -> r.first == it.id } }
        assertTrue(missing.isEmpty(), "no way to get ${missing.map { it.name }}")
    }

    @Test
    fun everyRegionalFormHasAWayIn() {
        val reach = reached()
        val missing = Dex.forms.filter { it.kind == FormKind.REGIONAL && (it.speciesId to it.identifier) !in reach }
        assertTrue(missing.isEmpty(), "no way to get ${missing.map { it.identifier }}")
        assertTrue((550 to "basculin-white-striped") in reach, "Basculegion's Hisuian Basculin")
    }

    @Test
    fun theTenFormerlyMissingSpeciesAreReachable() {
        val reach = reached().map { it.first }.toSet()
        val once = mapOf(
            414 to "Mothim", 862 to "Obstagoon", 863 to "Perrserker", 864 to "Cursola", 865 to "Sirfetch'd",
            867 to "Runerigus", 902 to "Basculegion", 903 to "Sneasler", 904 to "Overqwil", 980 to "Clodsire",
        )
        once.forEach { (id, name) -> assertTrue(id in reach, name) }
    }

    @Test
    fun aWildFindKeepsTheFormOfItsHomeLayer() {
        assertEquals("rattata-alola", RegionalForms.formFor(19, WorldLayer.ALOLA, Random(0)))
        assertNull(RegionalForms.formFor(19, WorldLayer.KANTO, Random(0)), "a Kanto find is the Kantonian form")
        assertEquals("basculin-white-striped", RegionalForms.formFor(550, WorldLayer.HISUI, Random(0)))
        assertEquals(
            setOf("tauros-paldea-combat-breed", "tauros-paldea-blaze-breed", "tauros-paldea-aqua-breed"),
            RegionalForms.inLayer(128, WorldLayer.PALDEA).map { it.identifier }.toSet(),
        )
        assertEquals("Alolan Rattata", RegionalForms.displayName(19, "rattata-alola"))
        assertEquals("Rattata", RegionalForms.displayName(19, null))
        assertTrue(RegionalForms.inLayer(19, WorldLayer.GALAR).isEmpty())
    }
}
