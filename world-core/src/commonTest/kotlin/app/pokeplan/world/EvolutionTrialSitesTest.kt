package app.pokeplan.world

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.pokemon.EvolutionRules
import app.pokeplan.core.pokemon.EvolutionTrials
import app.pokeplan.core.pokemon.Hatching
import app.pokeplan.core.pokemon.OwnedPokemon
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EvolutionTrialSitesTest {
    private fun mon(id: String, form: String, level: Int): OwnedPokemon {
        val f = Dex.form(form)!!
        return Hatching.create(id, HatchOption(f.speciesId, form), OwnedPokemon.PARTY_SIZE, 0L, 60, Random(1)).copy(level = level)
    }

    @Test
    fun everyWorldTrialHasASiteInsideARealDungeon() {
        val needing = Dex.evolutions.filter { EvolutionTrials.needsWorld(it) }
        needing.forEach { e -> assertNotNull(EvolutionTrialSites.of(e), "${EvolutionRules.trialId(e)} has no site") }
        EvolutionTrialSites.all.forEach { site ->
            val d = assertNotNull(Dungeons.byId(site.dungeonId), site.key)
            assertTrue(site.floor in 1..d.floors, site.key)
        }
        assertEquals(EvolutionTrialSites.all.size, EvolutionTrialSites.all.map { it.key }.toSet().size, "one site per evolution")
        assertTrue(EvolutionTrialSites.all.all { s -> needing.any { EvolutionRules.trialId(it) == s.key } }, "no site for an evolution that is not a WORLD trial")
    }

    @Test
    fun theTrialsBelongToTheirRegions() {
        fun layer(key: String) = Dungeons.byId(EvolutionTrialSites.all.single { it.key == key }.dungeonId)!!.layer
        assertEquals(WorldLayer.GALAR, layer("562-867-yamask-galar"))
        assertEquals(WorldLayer.ALOLA, layer("104-105-any-marowak-alola"))
        assertEquals(WorldLayer.HISUI, layer("156-157-any-typhlosion-hisui"))
        assertEquals(WorldLayer.PALDEA, layer("625-983-any"))
        assertEquals(WorldLayer.KALOS, layer("705-706-any"))
    }

    @Test
    fun aMemberThatReachesTheFloorDoesItsTrial() {
        val yamask = mon("y", "yamask-galar", 30)
        val bystander = mon("b", "pikachu", 30)
        val site = EvolutionTrialSites.all.single { it.key == "562-867-yamask-galar" }
        val events = mutableListOf<WorldEvent>()
        val short = EvolutionTrialSites.advance(WorldData(), site.dungeonId, site.floor - 1, listOf(yamask, bystander), events)
        assertTrue(short.evolutionTrials.isEmpty(), "not deep enough")
        val elsewhere = EvolutionTrialSites.advance(WorldData(), "tiny-woods", 5, listOf(yamask), events)
        assertTrue(elsewhere.evolutionTrials.isEmpty(), "the wrong place")
        val done = EvolutionTrialSites.advance(WorldData(), site.dungeonId, site.floor, listOf(yamask, bystander), events)
        val e = Dex.evolutionsFrom(562).first { it.to == 867 }
        assertEquals(setOf(EvolutionTrials.doneKey(e, "y")), done.evolutionTrials)
        assertEquals(1, events.count { it is WorldEvent.TrialDone })
        assertTrue(EvolutionTrialSites.open(yamask, done.evolutionTrials).isEmpty())
    }

    @Test
    fun aLongExpeditionCountsAsTheWalkOfAStepsEvolution() {
        val pawmo = mon("p", Dex.defaultForm(922).identifier, 30)
        val d = EvolutionTrialSites.advance(WorldData(), "tiny-woods", EvolutionTrialSites.WALK_FLOORS, listOf(pawmo), mutableListOf())
        assertEquals(1, d.evolutionTrials.size)
    }
}
