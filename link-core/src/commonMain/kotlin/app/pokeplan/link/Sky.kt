package app.pokeplan.link

import kotlinx.datetime.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** The twelve tropical signs. [element] and [modality] are the traditional groupings; [ruler] is the classical-modern ruling planet. */
enum class Sign(val label: String, val element: String, val modality: String, val ruler: String) {
    ARIES("Aries", "Fire", "Cardinal", "Mars"), TAURUS("Taurus", "Earth", "Fixed", "Venus"), GEMINI("Gemini", "Air", "Mutable", "Mercury"),
    CANCER("Cancer", "Water", "Cardinal", "Moon"), LEO("Leo", "Fire", "Fixed", "Sun"), VIRGO("Virgo", "Earth", "Mutable", "Mercury"),
    LIBRA("Libra", "Air", "Cardinal", "Venus"), SCORPIO("Scorpio", "Water", "Fixed", "Pluto"), SAGITTARIUS("Sagittarius", "Fire", "Mutable", "Jupiter"),
    CAPRICORN("Capricorn", "Earth", "Cardinal", "Saturn"), AQUARIUS("Aquarius", "Air", "Fixed", "Uranus"), PISCES("Pisces", "Water", "Mutable", "Neptune");

    companion object {
        /** The sign of an ecliptic longitude in degrees. */
        fun ofLongitude(lon: Double): Sign = entries[(floor(((lon % 360.0) + 360.0) % 360.0 / 30.0).toInt()).coerceIn(0, 11)]

        /**
         * The usual newspaper sign for a month and day, when the year (and so the exact Sun) is unknown. The cusp days move by a day from year to year;
         * with a year, [Sky.chart] is exact.
         */
        fun ofDate(month: Int, day: Int): Sign {
            val cut = intArrayOf(20, 19, 21, 20, 21, 21, 23, 23, 23, 23, 22, 22) // first day of the next sign, January to December
            val signs = arrayOf(Sign.CAPRICORN, Sign.AQUARIUS, Sign.PISCES, Sign.ARIES, Sign.TAURUS, Sign.GEMINI, Sign.CANCER, Sign.LEO, Sign.VIRGO, Sign.LIBRA, Sign.SCORPIO, Sign.SAGITTARIUS)
            val m = month.coerceIn(1, 12) - 1
            return if (day >= cut[m]) signs[(m + 1) % 12] else signs[m]
        }
    }
}

/** The bodies of a chart. [NORTH_NODE] is the mean node. */
enum class Body(val label: String) {
    SUN("Sun"), MOON("Moon"), MERCURY("Mercury"), VENUS("Venus"), MARS("Mars"), JUPITER("Jupiter"), SATURN("Saturn"),
    URANUS("Uranus"), NEPTUNE("Neptune"), PLUTO("Pluto"), NORTH_NODE("North Node"),
}

/** Where a body sits: its ecliptic longitude, and whether it appears to move backwards. */
data class Placement(val body: Body, val longitude: Double, val retrograde: Boolean) {
    val sign: Sign get() = Sign.ofLongitude(longitude)
    /** Whole degrees into the sign, 0 to 29. */
    val degree: Int get() = (((longitude % 360.0) + 360.0) % 360.0 % 30.0).toInt()
}

enum class AspectKind(val label: String, val angle: Double, val orb: Double) {
    CONJUNCTION("conjunct", 0.0, 8.0), SEXTILE("sextile", 60.0, 4.0), SQUARE("square", 90.0, 6.0), TRINE("trine", 120.0, 6.0), OPPOSITION("opposite", 180.0, 8.0),
}

data class Aspect(val a: Body, val b: Body, val kind: AspectKind, val orb: Double)

/** A birthplace for the rising sign: latitude north and longitude east, in degrees. [name] is only a label. */
data class Place(val name: String, val latitude: Double, val longitude: Double, val utcOffsetMinutes: Int = 0)

/**
 * A Western natal chart. The rising sign and the houses exist only when the birth time and place were given. When the time is unknown, [maybe] lists
 * each body whose sign changes during that day, with the other sign it could be in, so the chart never claims more than it knows.
 */
data class NatalChart(
    val placements: List<Placement>,
    val ascendant: Double?,
    val midheaven: Double?,
    val aspects: List<Aspect>,
    val timeKnown: Boolean,
    val maybe: Map<Body, Sign>,
) {
    fun of(body: Body): Placement = placements.first { it.body == body }
    val rising: Sign? get() = ascendant?.let { Sign.ofLongitude(it) }

    /** The whole-sign house (1 to 12) a body is in, counted from the rising sign. */
    fun house(body: Body): Int? = rising?.let { r -> ((of(body).sign.ordinal - r.ordinal + 12) % 12) + 1 }
}

/**
 * Low-precision sky maths for a chart "for fun" (LINK.md 2.6): the Sun, the planets from JPL's published mean elements for 1800 to 2050 and the Moon from
 * the Astronomical Almanac's low-precision series. Typical error is well under half a degree (the Moon at worst about 0.3°, Jupiter and Saturn 0.25°,
 * the others under 0.06°), which is more than a sign needs; a body within a fraction of a degree of a sign line may be in the next one. Years outside
 * 1800 to 2050 drift further. Pure arithmetic, no table, no network, and nothing here knows who the chart is for.
 */
object Sky {
    private const val J2000 = 2451545.0
    private fun rad(d: Double) = d * PI / 180.0
    private fun deg(r: Double) = r * 180.0 / PI
    private fun norm(d: Double) = ((d % 360.0) + 360.0) % 360.0

    /** The Julian Day of a calendar day at [minutes] after midnight local time, [utcOffsetMinutes] ahead of UTC. */
    fun julianDay(date: LocalDate, minutes: Int, utcOffsetMinutes: Int): Double {
        var y = date.year
        var m = date.monthNumber
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        val dayStart = floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + date.dayOfMonth + b - 1524.5 // JD at 0h
        return dayStart + (minutes - utcOffsetMinutes) / 1440.0
    }

    // JPL "Approximate positions of the planets", Table 1 (1800 AD to 2050 AD): a, e, I, L, long. perihelion, long. node, each with its change per century.
    private val elements: Map<String, DoubleArray> = mapOf(
        "Mercury" to doubleArrayOf(0.38709927, 0.00000037, 0.20563593, 0.00001906, 7.00497902, -0.00594749, 252.25032350, 149472.67411175, 77.45779628, 0.16047689, 48.33076593, -0.12534081),
        "Venus" to doubleArrayOf(0.72333566, 0.00000390, 0.00677672, -0.00004107, 3.39467605, -0.00078890, 181.97909950, 58517.81538729, 131.60246718, 0.00268329, 76.67984255, -0.27769418),
        "Earth" to doubleArrayOf(1.00000261, 0.00000562, 0.01671123, -0.00004392, -0.00001531, -0.01294668, 100.46457166, 35999.37244981, 102.93768193, 0.32327364, 0.0, 0.0),
        "Mars" to doubleArrayOf(1.52371034, 0.00001847, 0.09339410, 0.00007882, 1.84969142, -0.00813131, -4.55343205, 19140.30268499, -23.94362959, 0.44441088, 49.55953891, -0.29257343),
        "Jupiter" to doubleArrayOf(5.20288700, -0.00011607, 0.04838624, -0.00013253, 1.30439695, -0.00183714, 34.39644051, 3034.74612775, 14.72847983, 0.21252668, 100.47390909, 0.20469106),
        "Saturn" to doubleArrayOf(9.53667594, -0.00125060, 0.05386179, -0.00050991, 2.48599187, 0.00193609, 49.95424423, 1222.49362201, 92.59887831, -0.41897216, 113.66242448, -0.28867794),
        "Uranus" to doubleArrayOf(19.18916464, -0.00196176, 0.04725744, -0.00004397, 0.77263783, -0.00242939, 313.23810451, 428.48202785, 170.95427630, 0.40805281, 74.01692503, 0.04240589),
        "Neptune" to doubleArrayOf(30.06992276, 0.00026291, 0.00859048, 0.00005105, 1.77004347, 0.00035372, -55.12002969, 218.45945325, 44.96476227, -0.32241464, 131.78422574, -0.00508664),
        "Pluto" to doubleArrayOf(39.48211675, -0.00031596, 0.24882730, 0.00005170, 17.14001206, 0.00004818, 238.92903833, 145.20780515, 224.06891629, -0.04062942, 110.30393684, -0.01183482),
    )

    /** Heliocentric x, y, z on the J2000 ecliptic, in astronomical units. */
    private fun helio(name: String, t: Double): DoubleArray {
        val e = elements.getValue(name)
        val a = e[0] + e[1] * t
        val ecc = e[2] + e[3] * t
        val incl = e[4] + e[5] * t
        val l = e[6] + e[7] * t
        val peri = e[8] + e[9] * t
        val node = e[10] + e[11] * t
        var m = norm(l - peri)
        if (m > 180.0) m -= 360.0
        var big = rad(m) + ecc * sin(rad(m))
        repeat(8) { big -= (big - ecc * sin(big) - rad(m)) / (1.0 - ecc * cos(big)) }
        val xp = a * (cos(big) - ecc)
        val yp = a * sqrt(1.0 - ecc * ecc) * sin(big)
        val om = rad(peri - node)
        val co = cos(om); val so = sin(om); val cn = cos(rad(node)); val sn = sin(rad(node)); val ci = cos(rad(incl)); val si = sin(rad(incl))
        return doubleArrayOf(
            (co * cn - so * sn * ci) * xp + (-so * cn - co * sn * ci) * yp,
            (co * sn + so * cn * ci) * xp + (-so * sn + co * cn * ci) * yp,
            so * si * xp + co * si * yp,
        )
    }

    /** General precession in longitude since J2000, in degrees. */
    private fun precession(t: Double) = 1.396971 * t + 0.0003086 * t * t

    private fun longitude(body: Body, t: Double): Double = when (body) {
        Body.SUN -> { val e = helio("Earth", t); norm(deg(atan2(-e[1], -e[0])) + precession(t)) }
        Body.MOON -> {
            fun s(d: Double, c: Double) = sin(rad(d + c * t))
            norm(218.32 + 481267.881 * t + 6.29 * s(135.0, 477198.87) - 1.27 * s(259.3, -413335.36) + 0.66 * s(235.7, 890534.22) +
                0.21 * s(269.9, 954397.74) - 0.19 * s(357.5, 35999.05) - 0.11 * s(186.5, 966404.03))
        }
        Body.NORTH_NODE -> norm(125.04452 - 1934.136261 * t)
        else -> {
            val p = helio(body.label, t)
            val e = helio("Earth", t)
            norm(deg(atan2(p[1] - e[1], p[0] - e[0])) + precession(t))
        }
    }

    private fun obliquity(t: Double) = 23.439291 - 0.0130042 * t

    private fun siderealDegrees(jd: Double): Double {
        val t = (jd - J2000) / 36525.0
        return norm(280.46061837 + 360.98564736629 * (jd - J2000) + 0.000387933 * t * t)
    }

    /** The rising degree (ecliptic longitude) and the midheaven for a Julian Day and a place. */
    fun angles(jd: Double, latitude: Double, longitude: Double): Pair<Double, Double> {
        val t = (jd - J2000) / 36525.0
        val ramc = rad(norm(siderealDegrees(jd) + longitude))
        val eps = rad(obliquity(t))
        val asc = norm(deg(atan2(cos(ramc), -(sin(ramc) * cos(eps) + tan(rad(latitude)) * sin(eps)))))
        val mc = norm(deg(atan2(sin(ramc), cos(ramc) * cos(eps))))
        return asc to mc
    }

    private val chartBodies = Body.entries

    private fun signDiff(a: Double, b: Double) = (a - b + 540.0) % 360.0 - 180.0

    /**
     * The chart for [date]. With [minutes] (minutes after midnight, local clock) and [utcOffsetMinutes] the instant is exact; without, noon is used and
     * [NatalChart.maybe] says which bodies could be in another sign that day. [place] adds the rising sign and midheaven (only with a time).
     */
    fun chart(date: LocalDate, minutes: Int?, utcOffsetMinutes: Int, place: Place?): NatalChart {
        val known = minutes != null
        val jd = julianDay(date, minutes ?: 720, utcOffsetMinutes)
        val t = (jd - J2000) / 36525.0
        val placements = chartBodies.map { b ->
            val lon = longitude(b, t)
            val retro = when (b) {
                Body.SUN, Body.MOON -> false
                Body.NORTH_NODE -> false
                else -> signDiff(longitude(b, t + 0.5 / 36525.0), longitude(b, t - 0.5 / 36525.0)) < 0.0
            }
            Placement(b, lon, retro)
        }
        val maybe = HashMap<Body, Sign>()
        if (!known) {
            val t0 = (julianDay(date, 0, utcOffsetMinutes) - J2000) / 36525.0
            val t1 = (julianDay(date, 1439, utcOffsetMinutes) - J2000) / 36525.0
            for (b in listOf(Body.SUN, Body.MOON, Body.MERCURY, Body.VENUS, Body.MARS)) {
                val s0 = Sign.ofLongitude(longitude(b, t0)); val s1 = Sign.ofLongitude(longitude(b, t1))
                if (s0 != s1) {
                    val noon = Sign.ofLongitude(longitude(b, t))
                    maybe[b] = if (noon == s0) s1 else s0
                }
            }
        }
        val (asc, mc) = if (known && place != null) angles(jd, place.latitude, place.longitude).let { it.first to it.second } else (null to null)
        val aspects = ArrayList<Aspect>()
        val main = placements.filter { it.body != Body.NORTH_NODE }
        for (i in main.indices) for (j in i + 1 until main.size) {
            val sep = abs(signDiff(main[i].longitude, main[j].longitude))
            AspectKind.entries.map { it to abs(sep - it.angle) }.filter { (k, o) -> o <= k.orb }.minByOrNull { it.second }
                ?.let { (k, o) -> aspects += Aspect(main[i].body, main[j].body, k, o) }
        }
        return NatalChart(placements, asc, mc, aspects.sortedBy { it.orb }, known, maybe)
    }
}
