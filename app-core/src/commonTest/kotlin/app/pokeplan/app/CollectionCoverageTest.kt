package app.pokeplan.app

import app.pokeplan.app.quest.Quests
import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.FormKind
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.dex.Rarity
import app.pokeplan.core.dex.Region
import app.pokeplan.core.pokemon.EvolutionRules
import app.pokeplan.core.pokemon.Hatching
import app.pokeplan.world.Dungeons
import app.pokeplan.world.Legends
import app.pokeplan.world.RegionalForms
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One audit of the whole collection across the modules and WORLD: every species and every form kind has a way in,
 * except the kinds that docs/design.md ("Roster") lists as having no source yet. When a source is built for one of
 * those, empty its entry in [noSourceYet] so it is checked like the rest.
 */
class CollectionCoverageTest {
    private var next = 0
    private val rng = Random(11)

    private fun mon(species: Int, form: String) =
        Hatching.create("cover-${next++}", HatchOption(species, form), 0, 0L, 1, rng).copy(level = 100)

    /** Module eggs, home-layer WORLD finds and regional legend quests, closed under evolution. */
    private val reached: Set<Pair<Int, String>> by lazy {
        val eggs = Region.entries.filter { it.isModule }.flatMap { r -> Rarity.entries.flatMap { HatchRules.pool(r, it) } }
            .map { it.speciesId to it.formIdentifier }
        val wild = Dungeons.all.flatMap { d ->
            d.wild.filter { !Dex.species(it.speciesId).isSpecial }
                .flatMap { e -> RegionalForms.inLayer(e.speciesId, d.layer).map { e.speciesId to it.identifier } }
        }
        val legends = Legends.all.mapNotNull { q -> q.form?.let { q.speciesId to it } }
        val reach = (eggs + wild + legends).toMutableSet()
        val queue = ArrayDeque(reach)
        while (queue.isNotEmpty()) {
            val (species, form) = queue.removeFirst()
            for (e in EvolutionRules.applicable(mon(species, form))) {
                val node = e.to to (e.evolvedForm ?: Dex.defaultForm(e.to).identifier)
                if (reach.add(node)) queue += node
            }
        }
        reach
    }

    private val reachedSpecies: Set<Int> by lazy { reached.map { it.first }.toSet() }

    /** Legendary, mythical, Ultra Beast and Paradox species come from a WORLD legend or a Plan quest. */
    private val questSpecies: Set<Int> by lazy { Legends.coveredSpecies + Quests.all.map { it.speciesId } }

    private fun speciesHasAWayIn(id: Int) = id in reachedSpecies || id in questSpecies

    @Test
    fun everySpeciesHasAWayIn() {
        val missing = Dex.species.filter { !speciesHasAWayIn(it.id) }
        assertTrue(missing.isEmpty(), "no way to get ${missing.map { it.name }}")
    }

    @Test
    fun everyDefaultFormOfAnEggSpeciesIsReached() {
        val missing = Dex.species.filter { !it.isSpecial }
            .filter { s -> (s.id to Dex.defaultForm(s.id).identifier) !in reached }
        assertTrue(missing.isEmpty(), "default form never reached: ${missing.map { it.name }}")
    }

    @Test
    fun everyRegionalGenderAndMegaFormHasAWayIn() {
        val missing = Dex.forms.filter { f ->
            when (f.kind) {
                FormKind.REGIONAL -> (f.speciesId to f.identifier) !in reached
                // A female form comes with a female of the species; a Mega form with its stone (Kecleon Shop, treasure boxes).
                FormKind.GENDER, FormKind.MEGA -> !speciesHasAWayIn(f.speciesId)
                else -> false
            }
        }
        assertTrue(missing.isEmpty(), "no way to get ${missing.map { it.identifier }}")
    }

    @Test
    fun onlyTheFormKindsWithNoSourceYetAreUnreachable() {
        val noSourceYet = setOf(FormKind.GMAX, FormKind.ALTERNATE)
        val covered = setOf(FormKind.DEFAULT, FormKind.REGIONAL, FormKind.GENDER, FormKind.MEGA)
        assertEquals(FormKind.entries.toSet(), noSourceYet + covered, "a new form kind needs a source or an entry in noSourceYet")
        assertTrue(noSourceYet.all { k -> Dex.forms.any { it.kind == k } })
    }
}
