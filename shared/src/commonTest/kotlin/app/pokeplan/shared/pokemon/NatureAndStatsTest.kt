package app.pokeplan.shared.pokemon

import app.pokeplan.shared.dex.Dex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NatureAndStatsTest {
    @Test
    fun twentyFiveNaturesFiveNeutral() {
        assertEquals(25, Nature.entries.size)
        assertEquals(5, Nature.entries.count { it.isNeutral })
        for (n in Nature.entries.filterNot { it.isNeutral }) {
            assertTrue(n.raised != n.lowered, n.name)
            assertTrue(n.raised != Stat.HP && n.lowered != Stat.HP, "HP is never changed by a nature")
            assertEquals(110, n.percent(n.raised!!))
            assertEquals(90, n.percent(n.lowered!!))
            assertEquals(100, n.percent(Stat.HP))
        }
    }

    @Test
    fun bondStylesFollowTheRaisedStat() {
        val counts = Nature.entries.groupingBy { it.bondStyle }.eachCount()
        assertEquals(8, counts[BondStyle.PRESENCE])
        assertEquals(8, counts[BondStyle.COMFORT])
        assertEquals(4, counts[BondStyle.GIFTS])
        assertEquals(5, counts[BondStyle.BALANCED])
        assertEquals(BondStyle.PRESENCE, Nature.ADAMANT.bondStyle)
        assertEquals(BondStyle.COMFORT, Nature.BOLD.bondStyle)
        assertEquals(BondStyle.GIFTS, Nature.MODEST.bondStyle)
    }

    private val pikachu = Dex.defaultForm(Ids.PIKACHU).stats

    @Test
    fun pikachuAtLevelFifty() {
        val stats = StatCalculator.compute(pikachu, StatSpread.of(31), StatSpread.ZERO, 50, Nature.HARDY)
        assertEquals(110, stats.hp)
        assertEquals(75, stats.attack)
        assertEquals(110, stats.speed)
    }

    @Test
    fun evsAndNatureChangeStats() {
        val evs = StatSpread(speed = 252)
        val jolly = StatCalculator.compute(pikachu, StatSpread.of(31), evs, 50, Nature.JOLLY)
        assertEquals(156, jolly.speed, "(137 + 5) * 1.1")
        assertEquals(63, jolly.spAttack, "lowered stat: (65 + 5) * 0.9")
    }

    @Test
    fun garchompAtLevelHundredMatchesTheGames() {
        val stats = StatCalculator.compute(Dex.defaultForm(Ids.GARCHOMP).stats, StatSpread.of(31), StatSpread.ZERO, 100, Nature.HARDY)
        assertEquals(357, stats.hp)
        assertEquals(296, stats.attack)
    }

    @Test
    fun shedinjaAlwaysHasOneHp() {
        val stats = StatCalculator.compute(Dex.defaultForm(Ids.SHEDINJA).stats, StatSpread.of(31), StatSpread.of(0), 100, Nature.HARDY)
        assertEquals(1, stats.hp)
    }

    @Test
    fun invalidInputsAreRejected() {
        assertFailsWith<IllegalArgumentException> { StatCalculator.compute(pikachu, StatSpread.of(31), StatSpread.ZERO, 101, Nature.HARDY) }
        assertFailsWith<IllegalArgumentException> { pokemon(Ids.PIKACHU, ivs = StatSpread(hp = 32)) }
        assertFailsWith<IllegalArgumentException> { pokemon(Ids.PIKACHU, evs = StatSpread(hp = 253)) }
        assertFailsWith<IllegalArgumentException> { pokemon(Ids.PIKACHU, evs = StatSpread(252, 252, 7)) }
    }
}
