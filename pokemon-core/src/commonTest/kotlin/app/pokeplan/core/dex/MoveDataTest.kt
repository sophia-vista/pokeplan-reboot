package app.pokeplan.core.dex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MoveDataTest {
    @Test
    fun knownMovesHaveTheirFacts() {
        val flamethrower = assertNotNull(Dex.move("flamethrower"))
        assertEquals("Flamethrower", flamethrower.name)
        assertEquals(PokemonType.FIRE, flamethrower.type)
        assertEquals(MoveCategory.SPECIAL, flamethrower.category)
        assertEquals(90, flamethrower.power)
        assertEquals(MoveCategory.STATUS, assertNotNull(Dex.move("will-o-wisp")).category)
        assertEquals(null, Dex.move("will-o-wisp").let { it!!.power })
        assertEquals(1, assertNotNull(Dex.move("pound")).id)
    }

    @Test
    fun everyLearnsetMoveExists() {
        for (form in Dex.forms) {
            val set = Dex.learnset(form)
            for (id in set.all) assertNotNull(Dex.move(id), "${form.identifier} learns unknown move $id")
        }
    }

    @Test
    fun everyDefaultFormLearnsSomething() {
        for (s in Dex.species) assertTrue(Dex.learnset(s.id).all.isNotEmpty(), "${s.name} learns nothing")
    }

    @Test
    fun levelUpMovesAreInLevelOrder() {
        for (s in Dex.species) {
            val levels = Dex.learnset(s.id).levelUp.map { it.second }
            assertEquals(levels.sorted(), levels, s.name)
        }
    }

    @Test
    fun charizardLearnsFireMovesAndAMegaUsesItsSpeciesSet() {
        val flame = assertNotNull(Dex.move("flamethrower")).id
        val charizard = Dex.defaultForm(6)
        assertTrue(Dex.learnset(charizard).learns(flame))
        val mega = assertNotNull(Dex.form("charizard-mega-x"))
        assertEquals(Dex.learnset(charizard), Dex.learnset(mega))
    }

    @Test
    fun movesOfATypeAreStrongestFirst() {
        val fire = Dex.movesOfType(PokemonType.FIRE)
        assertTrue(fire.isNotEmpty() && fire.all { it.type == PokemonType.FIRE })
        assertEquals(fire.map { it.power ?: 0 }.sortedDescending(), fire.map { it.power ?: 0 })
    }
}
