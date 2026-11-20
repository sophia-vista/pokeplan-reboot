package app.pokeplan.link

import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BirthChartTest {
    private fun gap(a: Double, b: Double) = abs((a - b + 540.0) % 360.0 - 180.0)

    @Test fun fourPillarsMatchTheReferenceForEveryVector() {
        BirthVectors.pillars.forEach { v ->
            val c = assertNotNull(BaZi.chart(LocalDate(v[0], v[1], v[2]), v[3], 480), "chart ${v.toList()}")
            val got = listOf(c.year.stem.ordinal, c.year.branch.ordinal, c.month.stem.ordinal, c.month.branch.ordinal, c.day.stem.ordinal, c.day.branch.ordinal, c.hour!!.stem.ordinal, c.hour!!.branch.ordinal)
            assertEquals(v.drop(4), got, "pillars for ${v.take(4)}")
        }
    }

    @Test fun theYearTurnsAtLichunOnTheBirthplaceClock() {
        // Lichun 2000 was 4 Feb 20:40 Beijing time: 1999 (Ji Mao) before, 2000 (Geng Chen) after, whatever the birthplace's offset.
        val d = LocalDate(2000, 2, 4)
        assertEquals("Ji Mao", BaZi.chart(d, 20 * 60 + 39, 480)!!.year.label)
        assertEquals("Geng Chen", BaZi.chart(d, 20 * 60 + 41, 480)!!.year.label)
        assertEquals("Ji Mao", BaZi.chart(d, 12 * 60 + 39, 0)!!.year.label) // 12:39 in Greenwich is 20:39 in Beijing
        assertEquals("Geng Chen", BaZi.chart(d, 12 * 60 + 41, 0)!!.year.label)
    }

    @Test fun anUnknownHourStillGivesThreePillars() {
        val c = BaZi.chart(LocalDate(1990, 11, 1), null, 480)!!
        assertNull(c.hour)
        assertEquals("Geng Wu", c.year.label)
        assertEquals("Geng Wu", c.day.label)
        assertEquals(Stem.GENG, c.dayMaster)
    }

    @Test fun lateRatHourBorrowsTheNextDaysStemButKeepsTheDay() {
        val late = BaZi.chart(LocalDate(1990, 11, 1), 23 * 60 + 30, 480)!!
        val early = BaZi.chart(LocalDate(1990, 11, 2), 30, 480)!!
        assertEquals("Geng Wu", late.day.label)
        assertEquals("Xin Wei", early.day.label)
        assertEquals(late.hour, early.hour)
    }

    @Test fun elementsCountEveryVisibleCharacter() {
        val c = BaZi.chart(LocalDate(1990, 11, 1), 23 * 60, 480)!!
        assertEquals(8, c.elements.values.sum())
        assertEquals(6, BaZi.chart(LocalDate(1990, 11, 1), null, 480)!!.elements.values.sum())
    }

    @Test fun datesOutsideTheTablesAreRefusedNotGuessed() {
        assertNull(BaZi.chart(LocalDate(1850, 5, 5), 600, 480))
        assertNull(BaZi.chart(LocalDate(2150, 5, 5), 600, 480))
        assertNull(BaZi.zodiacYear(LocalDate(1901, 5, 5)))
        assertNull(BaZi.kua(LocalDate(2100, 5, 5), null, 480, BaZi.KuaFormula.A))
    }

    @Test fun theZodiacAnimalTurnsAtChineseNewYear() {
        BirthVectors.lunarYears.forEach { v -> assertEquals(v[3], BaZi.zodiacYear(LocalDate(v[0], v[1], v[2]))!!.lunarYear, "lunar year of ${v.toList()}") }
        // 2000 was a Dragon year starting 5 Feb 2000; 4 Feb 2000 is still a Rabbit (1999).
        assertEquals("Rabbit", BaZi.zodiacYear(LocalDate(2000, 2, 4))!!.animal)
        assertEquals("Dragon", BaZi.zodiacYear(LocalDate(2000, 2, 5))!!.animal)
        assertEquals("Yang Metal Dragon", BaZi.zodiacYear(LocalDate(2000, 6, 1))!!.pillar.words)
    }

    @Test fun kuaFollowsTheDigitFormula() {
        BirthVectors.kua.forEach { v ->
            val f = if (v[3] == 0) BaZi.KuaFormula.A else BaZi.KuaFormula.B
            assertEquals(v[4], BaZi.kua(LocalDate(v[0], v[1], v[2]), null, 480, f)!!.number, "kua ${v.toList()}")
        }
        assertEquals(7, BaZi.kua(LocalDate(1984, 6, 1), null, 480, BaZi.KuaFormula.A)!!.number)
        // Before Lichun a person counts in the year before: 1 Jan 1985 is a 1984 birth.
        assertEquals(7, BaZi.kua(LocalDate(1985, 1, 1), null, 480, BaZi.KuaFormula.A)!!.number)
        val k = BaZi.kua(LocalDate(1984, 6, 1), null, 480, BaZi.KuaFormula.A)!!
        assertEquals(4, k.lucky.size)
        assertTrue(!k.east)
    }

    @Test fun lifePathKeepsTheMasterNumbers() {
        assertEquals(4, BaZi.lifePath(LocalDate(1990, 11, 1)))
        assertEquals(11, BaZi.lifePath(LocalDate(1978, 11, 11))) // 11 + 11 + 7 = 29, 2 + 9 = 11
        assertEquals(22, BaZi.lifePath(LocalDate(1989, 2, 11))) // 2 + 11 + 9
        assertEquals(1, BaZi.lifePath(LocalDate(1981, 8, 1))) // 8 + 1 + 1 = 10
    }

    @Test fun planetsAndLuminariesAreWithinAFractionOfADegree() {
        val bodies = listOf(Body.SUN, Body.MOON, Body.MERCURY, Body.VENUS, Body.MARS, Body.JUPITER, Body.SATURN, Body.URANUS, Body.NEPTUNE, Body.PLUTO)
        BirthVectors.sky.forEach { v ->
            val jd = v[0]
            // Rebuild a calendar day and local clock for the Julian Day (UTC, offset 0) so the public entry point is what is tested.
            val total = ((jd + 0.5) * 1440.0).roundToLong()
            val z = total / 1440
            val minutes = (total % 1440).toInt()
            val date = civil(z)
            val chart = Sky.chart(date, minutes, 0, Place("t", v[1], v[2]))
            bodies.forEachIndexed { i, b -> assertTrue(gap(chart.of(b).longitude, v[5 + i]) < 0.45, "$b at jd $jd: ${chart.of(b).longitude} vs ${v[5 + i]}") }
            assertTrue(gap(chart.ascendant!!, v[3]) < 0.05, "asc at jd $jd: ${chart.ascendant} vs ${v[3]}")
            assertTrue(gap(chart.midheaven!!, v[4]) < 0.05, "mc at jd $jd")
        }
    }

    @Test fun signsAndHousesAreCounted() {
        val c = Sky.chart(LocalDate(2000, 1, 1), 12 * 60, 0, Place("Greenwich", 51.48, 0.0))
        assertEquals(Sign.CAPRICORN, c.of(Body.SUN).sign)
        assertEquals(10, c.of(Body.SUN).degree)
        assertNotNull(c.rising)
        assertEquals(1, c.house(Body.entries.first { c.of(it).sign == c.rising }))
        assertTrue(c.aspects.all { it.orb <= it.kind.orb })
        assertEquals(Sign.CAPRICORN, Sign.ofDate(12, 22))
        assertEquals(Sign.SAGITTARIUS, Sign.ofDate(12, 21))
        assertEquals(Sign.ARIES, Sign.ofDate(3, 21))
        assertEquals(Sign.PISCES, Sign.ofDate(3, 20))
    }

    @Test fun withoutATimeThereIsNoRisingSignAndASignThatMayChangeIsSaid() {
        val c = Sky.chart(LocalDate(2000, 1, 1), null, 0, Place("Greenwich", 51.48, 0.0))
        assertNull(c.ascendant)
        assertNull(c.rising)
        assertNull(c.house(Body.SUN))
        // Over a month the Moon crosses at least one sign line during some day, and the chart says so rather than guessing.
        val crossing = (1..30).count { d -> Sky.chart(LocalDate(2000, 1, d), null, 0, null).maybe.containsKey(Body.MOON) }
        assertTrue(crossing in 1..30)
    }

    // The civil date of a Julian Day Number (Fliegel and Van Flandern), for rebuilding a date in the vectors test.
    private fun civil(jdn: Long): LocalDate {
        var l = jdn + 68569
        val n = 4 * l / 146097
        l -= (146097 * n + 3) / 4
        val i = 4000 * (l + 1) / 1461001
        l = l - 1461 * i / 4 + 31
        val j = 80 * l / 2447
        val d = l - 2447 * j / 80
        l = j / 11
        val m = j + 2 - 12 * l
        val y = 100 * (n - 49) + i + l
        return LocalDate(y.toInt(), m.toInt(), d.toInt())
    }
}
