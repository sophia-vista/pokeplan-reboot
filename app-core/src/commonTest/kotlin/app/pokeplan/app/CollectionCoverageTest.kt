package app.pokeplan.app

import app.pokeplan.app.quest.Quests
import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.FormKind
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.dex.Rarity
import app.pokeplan.core.dex.Region
import app.pokeplan.core.pokemon.EvolutionRules
import app.pokeplan.core.pokemon.FormRoute
import app.pokeplan.core.pokemon.FormRoutes
import app.pokeplan.core.pokemon.Hatching
import app.pokeplan.core.pokemon.ItemCategory
import app.pokeplan.core.pokemon.Items
import app.pokeplan.world.KecleonShop
import app.pokeplan.world.WorldLayer
import app.pokeplan.world.Dungeons
import app.pokeplan.world.Legends
import app.pokeplan.world.RegionalForms
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * One audit of the whole collection across the modules and WORLD: every species and every form kind has a way in,
 * and every alternate or Gigantamax form has a route (docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 3.3.1).
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
        // A hatch or a wild find of a species may be one of its separate-encounter variants (FormRoutes.rollVariant).
        val variants = (eggs.map { it.first } + wild.map { it.first } + Dungeons.all.flatMap { d -> d.wild.map { it.speciesId } }).toSet()
            .flatMap { sp -> FormRoutes.variants(sp).map { it.speciesId to it.identifier } }
        val reach = (eggs + wild + legends + variants).toMutableSet()
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

    /** Species a hatch or a WORLD wild find can produce, so one of their variants can be found. */
    private val hatchable: Set<Int> by lazy {
        Region.entries.filter { it.isModule }.flatMap { r -> Rarity.entries.flatMap { HatchRules.pool(r, it) } }.map { it.speciesId }.toSet() +
            Dungeons.all.flatMap { d -> d.wild.filter { !Dex.species(it.speciesId).isSpecial }.map { it.speciesId } }
    }

    private val alternates get() = Dex.forms.filter { it.kind == FormKind.ALTERNATE || it.kind == FormKind.GMAX }

    @Test
    fun everyAlternateAndGigantamaxFormHasARoute() {
        val none = alternates.filter { FormRoutes.route(it) == null }
        assertTrue(none.isEmpty(), "classify in FormRoutes: ${none.map { it.identifier }}")
    }

    @Test
    fun everyFormItemIsSoldHoldableAndShowsItsForm() {
        for (item in FormRoutes.formItems) {
            assertTrue(KecleonShop.formItems.any { it.itemId == item.id }, "${item.id} is not sold")
            assertTrue(Items.holdable(item.id), "${item.id} cannot be held")
            assertEquals(ItemCategory.FORM, Items.category(item.id))
            for ((species, form) in item.forms) {
                val f = Dex.form(form)
                assertEquals(species, f?.speciesId, "$form belongs to species $species")
                val holder = mon(species, Dex.defaultForm(species).identifier).copy(heldItem = item.id)
                assertEquals(form, FormRoutes.heldForm(holder)?.identifier, "${item.id} on species $species")
                assertTrue(form in FormDex.formsOf(holder).map { it.identifier })
            }
        }
        val itemForms = FormRoutes.formItems.flatMap { it.forms.values }.toSet()
        val routed = Dex.forms.filter { FormRoutes.route(it) == FormRoute.ITEM && it.kind == FormKind.ALTERNATE }.map { it.identifier }.toSet()
        assertEquals(routed, itemForms, "every item-routed alternate form has exactly its item")
    }

    @Test
    fun everyGigantamaxFormShowsWithTheDynamaxBandAndTheBandIsFoundInGalar() {
        for (form in Dex.forms.filter { it.kind == FormKind.GMAX && FormRoutes.route(it) == FormRoute.ITEM }) {
            val base = form.identifier.removeSuffix("-gmax")
            assertNotNull(Dex.form(base), "${form.identifier} has a base form $base")
            val holder = mon(form.speciesId, base).copy(heldItem = Items.DYNAMAX_BAND)
            assertEquals(form.identifier, FormRoutes.heldForm(holder)?.identifier)
        }
        val bandSources = Dungeons.all.filter { d -> d.milestones.values.any { it.itemId == Items.DYNAMAX_BAND } }
        assertEquals(listOf("dusty-bowl"), bandSources.map { it.id })
        assertEquals(WorldLayer.GALAR, bandSources.single().layer)
        assertTrue(Items.holdable(Items.DYNAMAX_BAND))
    }

    @Test
    fun everyEvolutionAndEncounterFormHasAWayIn() {
        val missing = alternates.filter { f ->
            when (FormRoutes.route(f)) {
                FormRoute.EVOLUTION -> (f.speciesId to f.identifier) !in reached
                FormRoute.ENCOUNTER, FormRoute.RARE_ENCOUNTER -> f.speciesId !in hatchable && !carriedFromAVariant(f)
                else -> false
            }
        }
        assertTrue(missing.isEmpty(), "no hatch or evolution reaches ${missing.map { it.identifier }}")
    }

    private fun carriedFromAVariant(f: app.pokeplan.core.dex.FormInfo): Boolean =
        alternates.any { from ->
            (FormRoutes.route(from) == FormRoute.ENCOUNTER || FormRoutes.route(from) == FormRoute.RARE_ENCOUNTER) &&
                from.speciesId in hatchable && FormRoutes.carryOver(from, f.speciesId)?.identifier == f.identifier
        }

    @Test
    fun aHatchOfAVariantSpeciesRollsItsVariantsAndEvolutionCarriesThemOver() {
        val rng = Random(5)
        val seen = (1..400).map { FormRoutes.rollVariant(710, Dex.defaultForm(710), rng).identifier }.toSet()
        assertTrue(listOf("pumpkaboo-small", "pumpkaboo-large", "pumpkaboo-super").all { it in seen }, "sizes rolled: $seen")
        assertTrue(Dex.defaultForm(710).identifier in seen, "the base size still rolls: $seen")
        assertEquals("gourgeist-large", FormRoutes.carryOver(Dex.form("pumpkaboo-large")!!, 711)?.identifier)
        assertEquals(Dex.defaultForm(1).identifier, FormRoutes.rollVariant(1, Dex.defaultForm(1), Random(1)).identifier)
    }

    @Test
    fun battleAndBossFormsAreNeverCounted() {
        val notCounted = alternates.filter { FormRoutes.route(it) == FormRoute.BATTLE || FormRoutes.route(it) == FormRoute.BOSS }
        assertTrue(notCounted.isNotEmpty())
        assertTrue(notCounted.none { FormDex.counts(it) })
        assertTrue(alternates.filter { FormRoutes.route(it) !in setOf(FormRoute.BATTLE, FormRoute.BOSS) }.all { FormDex.counts(it) })
    }
}
