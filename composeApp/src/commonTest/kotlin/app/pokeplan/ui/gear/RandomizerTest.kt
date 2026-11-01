package app.pokeplan.ui.gear

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RandomizerTest {
    private val random = Random(42)

    @Test
    fun anEmptySingleAndDuplicateListsAreHandled() {
        assertNull(Randomizer.pick(emptyList(), emptyMap(), random))
        assertEquals("only", Randomizer.pick(listOf("only"), emptyMap(), random))
        assertEquals("only", Randomizer.pick(listOf("only"), emptyMap(), random, avoid = "only"), "with one option there is nothing else to pick")
        assertTrue(Randomizer.pickMany(emptyList(), emptyMap(), 3, random).isEmpty())
        val long = "x".repeat(200)
        assertEquals(long, Randomizer.pick(listOf(long), emptyMap(), random))
    }

    @Test
    fun weightsChangeTheOddsAndTheLastPickCanBeLeftOut() {
        val weights = mapOf("a" to 1, "b" to 9)
        val counts = (1..10_000).groupingBy { Randomizer.pick(listOf("a", "b"), weights, random)!! }.eachCount()
        assertTrue(counts.getValue("b") in 8_700..9_300, "about nine in ten: $counts")
        val even = (1..10_000).groupingBy { Randomizer.pick(listOf("a", "b", "c"), emptyMap(), random)!! }.eachCount()
        assertTrue(even.values.all { it in 3_000..3_700 }, "even odds: $even")
        assertTrue((1..500).none { Randomizer.pick(listOf("a", "b", "c"), emptyMap(), random, avoid = "b") == "b" })
        // A weight is kept between 1 and 10 whatever is saved.
        assertEquals(1, Randomizer.weightOf("a", mapOf("a" to -5))); assertEquals(10, Randomizer.weightOf("a", mapOf("a" to 99))); assertEquals(1, Randomizer.weightOf("a", emptyMap()))
    }

    @Test
    fun pickingSeveralNeverRepeatsAndShufflingKeepsEveryone() {
        val options = listOf("a", "b", "c", "d")
        repeat(200) {
            val many = Randomizer.pickMany(options, emptyMap(), 3, random)
            assertEquals(3, many.size); assertEquals(3, many.toSet().size); assertTrue(options.containsAll(many))
        }
        assertEquals(4, Randomizer.pickMany(options, emptyMap(), 9, random).toSet().size, "no more than there are")
        assertEquals(options.toSet(), Randomizer.shuffle(options, random).toSet())
    }

    @Test
    fun diceAreReadStrictlyAndRollWithinTheirRange() {
        assertEquals(Triple(2, 6, 3), Randomizer.parseDice("2d6+3")); assertEquals(Triple(1, 20, 0), Randomizer.parseDice("d20")); assertEquals(Triple(3, 8, -1), Randomizer.parseDice(" 3D8 - 1 "))
        listOf("0d6", "21d6", "2d1", "2d1001", "abc", "2d6+", "2d6+5000", "", "d", "2x6", "2d6+3+4", "-2d6").forEach { assertNull(Randomizer.parseDice(it), it) }
        repeat(500) {
            val r = Randomizer.roll("3d6+2", random)!!
            assertEquals(3, r.rolls.size); assertTrue(r.rolls.all { it in 1..6 }); assertTrue(r.total in 5..20)
        }
        assertNull(Randomizer.roll("d1", random))
        assertEquals("d20", Randomizer.roll("1d20", Random(1))!!.spec)
        val fixed = DiceRoll("2d6+3", 6, listOf(4, 5), 3)
        assertEquals(12, fixed.total); assertEquals("2d6+3: 4 + 5 + 3 = 12", fixed.text)
        assertEquals("d20: 17", DiceRoll("d20", 20, listOf(17), 0).text)
        assertEquals("2d4-1: 1 + 2 - 1 = 2", DiceRoll("2d4-1", 4, listOf(1, 2), -1).text)
    }
}
