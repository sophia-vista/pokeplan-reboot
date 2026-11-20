package app.pokeplan.link

import kotlinx.datetime.LocalDate
import kotlin.math.floor

/** The five elements (wu xing). */
enum class Element(val label: String) { WOOD("Wood"), FIRE("Fire"), EARTH("Earth"), METAL("Metal"), WATER("Water") }

/** The ten heavenly stems, with their element and polarity. */
enum class Stem(val pinyin: String, val element: Element, val yang: Boolean) {
    JIA("Jia", Element.WOOD, true), YI("Yi", Element.WOOD, false), BING("Bing", Element.FIRE, true), DING("Ding", Element.FIRE, false),
    WU("Wu", Element.EARTH, true), JI("Ji", Element.EARTH, false), GENG("Geng", Element.METAL, true), XIN("Xin", Element.METAL, false),
    REN("Ren", Element.WATER, true), GUI("Gui", Element.WATER, false);

    val polarity: String get() = if (yang) "Yang" else "Yin"
}

/** The twelve earthly branches, with the animal each carries, its main element and the two-hour clock window it governs. */
enum class Branch(val pinyin: String, val animal: String, val element: Element) {
    ZI("Zi", "Rat", Element.WATER), CHOU("Chou", "Ox", Element.EARTH), YIN("Yin", "Tiger", Element.WOOD), MAO("Mao", "Rabbit", Element.WOOD),
    CHEN("Chen", "Dragon", Element.EARTH), SI("Si", "Snake", Element.FIRE), WU("Wu", "Horse", Element.FIRE), WEI("Wei", "Goat", Element.EARTH),
    SHEN("Shen", "Monkey", Element.METAL), YOU("You", "Rooster", Element.METAL), XU("Xu", "Dog", Element.EARTH), HAI("Hai", "Pig", Element.WATER),
}

/** One pillar: a stem and a branch. */
data class Pillar(val stem: Stem, val branch: Branch) {
    val label: String get() = "${stem.pinyin} ${branch.pinyin}"
    /** "Yang Wood Tiger" style description. */
    val words: String get() = "${stem.polarity} ${stem.element.label} ${branch.animal}"
}

/** The Four Pillars. [hour] is null when the birth hour is unknown. The day master is the day pillar's stem. */
data class BaZiChart(val year: Pillar, val month: Pillar, val day: Pillar, val hour: Pillar?) {
    val dayMaster: Stem get() = day.stem

    /** How many of the five elements the visible stems and branches carry (the branches by their main element). */
    val elements: Map<Element, Int>
        get() {
            val all = listOfNotNull(year, month, day, hour)
            val counts = Element.entries.associateWith { 0 }.toMutableMap()
            all.forEach { counts[it.stem.element] = counts.getValue(it.stem.element) + 1; counts[it.branch.element] = counts.getValue(it.branch.element) + 1 }
            return counts
        }
}

/** The Chinese zodiac year of a birth: the animal and the element and polarity of its stem. */
data class ChineseYear(val lunarYear: Int, val pillar: Pillar) {
    val animal: String get() = pillar.branch.animal
}

/**
 * Chinese calendar arithmetic for LINK's birth profile (LINK.md 2.6): the zodiac animal (the year turns at Chinese New Year), the Four Pillars (BaZi;
 * the year turns at Lichun, the month at each "jie" solar term, the day at midnight), the Kua number and the Western numerology Life Path. It
 * supports 1902 to 2098, the span of [BaZiTables]. A pillar's hour is the two-hour branch the birth clock time falls in, using the clock time
 * as written on the birth record (no true-solar-time correction), converted to Beijing time only to compare against the solar-term instants.
 */
object BaZi {
    const val FIRST_YEAR = BaZiTables.FIRST_YEAR + 1
    const val LAST_YEAR = BaZiTables.LAST_YEAR - 1

    fun supports(year: Int) = year in FIRST_YEAR..LAST_YEAR

    private val jie: Array<IntArray> by lazy {
        BaZiTables.JIE_MINUTES.split('\n').map { row -> row.trim().split(' ').map { it.toInt() }.toIntArray() }.toTypedArray()
    }
    private val newYear: IntArray by lazy { BaZiTables.NEW_YEAR.split(' ').map { it.toInt() }.toIntArray() }

    private fun jieMinutes(year: Int, index: Int): Int = jie[year - BaZiTables.FIRST_YEAR][index]

    /** Days since 1900-01-01 (a Gregorian civil-date count, so it needs no library). */
    private fun dayNumber(date: LocalDate): Long {
        var y = date.year
        val m = date.monthNumber
        if (m <= 2) y -= 1
        val era = floor(y / 400.0).toLong()
        val yoe = y - era * 400
        val mp = (m + 9) % 12
        val doy = (153 * mp + 2) / 5 + date.dayOfMonth - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 693901L // days since 1900-01-01
    }

    /** The Julian Day Number of a civil date, for the 60-day cycle. */
    private fun julianDayNumber(date: LocalDate): Long = dayNumber(date) + 2415021L

    private fun pillarOfIndex(i: Int) = Pillar(Stem.entries[((i % 10) + 10) % 10], Branch.entries[((i % 12) + 12) % 12])

    /** The 0 to 59 place of a day in the sexagenary cycle (0 is Jia Zi). */
    private fun dayCycle(date: LocalDate): Int = (((julianDayNumber(date) + 49) % 60 + 60) % 60).toInt()

    /** The Chinese New Year of a Gregorian year, as that year's date. */
    fun newYearOf(year: Int): LocalDate {
        val v = newYear[year - BaZiTables.FIRST_YEAR]
        return LocalDate(year, v / 100, v % 100)
    }

    /** The Chinese zodiac year of a date of birth: before that year's New Year, the animal is the previous year's. Null outside 1902 to 2098. */
    fun zodiacYear(date: LocalDate): ChineseYear? {
        if (!supports(date.year)) return null
        val ly = if (date < newYearOf(date.year)) date.year - 1 else date.year
        return ChineseYear(ly, Pillar(Stem.entries[(ly - 4).mod(10)], Branch.entries[(ly - 4).mod(12)]))
    }

    /** The Gregorian-year number the BaZi year belongs to: the year changes at Lichun (about 4 February), not on 1 January. */
    private fun baziYear(date: LocalDate, beijingMinutes: Long): Int = if (beijingMinutes < jieMinutes(date.year, 1).toLong()) date.year - 1 else date.year

    /**
     * The Four Pillars for a birth on [date] at [minutes] after midnight on the local clock ([utcOffsetMinutes] ahead of UTC), or without the hour
     * pillar when [minutes] is null. Null outside 1902 to 2098.
     */
    fun chart(date: LocalDate, minutes: Int?, utcOffsetMinutes: Int): BaZiChart? {
        if (!supports(date.year)) return null
        // The instant on the Beijing clock (UTC+8), against which the solar terms are tabled. An unknown hour is taken as noon.
        val local = dayNumber(date) * 1440L + (minutes ?: 720)
        val beijing = local - utcOffsetMinutes + 480L
        val by = baziYear(date, beijing)
        val yearPillar = Pillar(Stem.entries[(by - 4).mod(10)], Branch.entries[(by - 4).mod(12)])
        // The month: the latest jie at or before the instant; before Xiaohan it is still December's Daxue of the year before.
        val ys = if (beijing < jieMinutes(date.year, 0).toLong()) date.year - 1 else date.year
        var idx = 11
        for (i in 0..11) if (jieMinutes(ys, i).toLong() <= beijing) idx = i
        val branchIndex = (idx + 1) % 12 // Xiaohan starts the Ox month, Lichun the Tiger month, ... Daxue the Rat month
        val sinceTiger = (branchIndex - 2).mod(12)
        val monthStem = ((yearPillar.stem.ordinal % 5) * 2 + 2 + sinceTiger) % 10
        val monthPillar = Pillar(Stem.entries[monthStem], Branch.entries[branchIndex])
        val dayPillar = pillarOfIndex(dayCycle(date))
        val hourPillar = minutes?.let { m ->
            val h = m / 60
            val zi = ((h + 1) / 2) % 12
            // The late Rat hour (23:00 to midnight) belongs to the next day's Rat hour for the stem; the day pillar stays the birth date's.
            val dayStem = if (h >= 23) pillarOfIndex(dayCycle(date) + 1).stem else dayPillar.stem
            Pillar(Stem.entries[((dayStem.ordinal % 5) * 2 + zi) % 10], Branch.entries[zi])
        }
        return BaZiChart(yearPillar, monthPillar, dayPillar, hourPillar)
    }

    // --- Kua ---------------------------------------------------------------------------------------------------------------

    /** Which traditional Kua formula to use; the number depends on it. */
    enum class KuaFormula(val label: String) { A("Male formula"), B("Female formula") }

    enum class Direction(val label: String) { N("North"), NE("Northeast"), E("East"), SE("Southeast"), S("South"), SW("Southwest"), W("West"), NW("Northwest") }

    /** A Kua (Ming Gua) number with its group and four auspicious directions, in the order Sheng Qi, Tian Yi, Yan Nian, Fu Wei. */
    data class Kua(val number: Int, val east: Boolean, val lucky: List<Direction>)

    private val kuaDirections = mapOf(
        1 to listOf(Direction.SE, Direction.E, Direction.S, Direction.N),
        2 to listOf(Direction.NE, Direction.W, Direction.NW, Direction.SW),
        3 to listOf(Direction.S, Direction.N, Direction.SE, Direction.E),
        4 to listOf(Direction.N, Direction.S, Direction.E, Direction.SE),
        6 to listOf(Direction.W, Direction.NE, Direction.SW, Direction.NW),
        7 to listOf(Direction.NW, Direction.SW, Direction.NE, Direction.W),
        8 to listOf(Direction.SW, Direction.NW, Direction.W, Direction.NE),
        9 to listOf(Direction.E, Direction.SE, Direction.N, Direction.S),
    )

    private fun digitRoot(n: Int): Int { var x = n; while (x > 9) x = x.toString().sumOf { it - '0' }; return x }

    /** The Kua number, using the BaZi year (so someone born in January or early February counts in the year before). Null outside 1902 to 2098. */
    fun kua(date: LocalDate, minutes: Int?, utcOffsetMinutes: Int, formula: KuaFormula): Kua? {
        if (!supports(date.year)) return null
        val local = dayNumber(date) * 1440L + (minutes ?: 720)
        val year = baziYear(date, local - utcOffsetMinutes + 480L)
        val s = digitRoot((year % 100).toString().sumOf { it - '0' }.let { if (it == 0) 9 else it })
        val raw = when (formula) {
            KuaFormula.A -> if (year < 2000) 10 - s else 9 - s
            KuaFormula.B -> (if (year < 2000) s + 5 else s + 6)
        }
        var k = digitRoot(if (raw <= 0) raw + 9 else raw)
        if (k == 5) k = if (formula == KuaFormula.A) 2 else 8
        val dirs = kuaDirections.getValue(k)
        return Kua(k, k in setOf(1, 3, 4, 9), dirs)
    }

    // --- Numerology --------------------------------------------------------------------------------------------------------

    /** The Life Path number: the digits of the full birth date reduced, keeping 11, 22 and 33. Needs the year. */
    fun lifePath(date: LocalDate): Int {
        fun reduce(n: Int): Int { var x = n; while (x > 9 && x != 11 && x != 22 && x != 33) x = x.toString().sumOf { it - '0' }; return x }
        // Reduce month, day and year separately, then add: the usual method, so 1 Nov 1990 is 11 + 1 + 1 = 13 = 4.
        return reduce(reduce(date.monthNumber) + reduce(date.dayOfMonth) + reduce(date.year))
    }
}
