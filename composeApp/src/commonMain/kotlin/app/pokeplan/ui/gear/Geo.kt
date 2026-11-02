package app.pokeplan.ui.gear

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** Distances and directions between two places on the Earth, for the Waypoints tool. A sphere, so good to about half a percent. */
object Geo {
    private const val EARTH_METERS = 6_371_008.8
    private const val RAD = PI / 180.0

    /** The great-circle distance in metres (haversine). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = (lat2 - lat1) * RAD
        val dLon = (lon2 - lon1) * RAD
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1 * RAD) * cos(lat2 * RAD) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** The initial bearing from the first place to the second, in degrees from north, 0 to 360. */
    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = (lon2 - lon1) * RAD
        val y = sin(dLon) * cos(lat2 * RAD)
        val x = cos(lat1 * RAD) * sin(lat2 * RAD) - sin(lat1 * RAD) * cos(lat2 * RAD) * cos(dLon)
        val deg = atan2(y, x) / RAD
        return (deg % 360 + 360) % 360
    }

    /** Whether a latitude and longitude are real. */
    fun valid(lat: Double, lon: Double): Boolean = lat.isFinite() && lon.isFinite() && abs(lat) <= 90 && abs(lon) <= 180

    private fun fixed(v: Double, decimals: Int): String {
        var scale = 1L
        repeat(decimals) { scale *= 10 }
        val t = (abs(v) * scale).roundToLong()
        return (t / scale).toString() + "." + (t % scale).toString().padStart(decimals, '0')
    }

    /** "14.5995° N, 120.9842° E". */
    fun coordinates(lat: Double, lon: Double, decimals: Int = 4): String =
        "${fixed(lat, decimals)}° ${if (lat >= 0) "N" else "S"}, ${fixed(lon, decimals)}° ${if (lon >= 0) "E" else "W"}"

    /** "850 m" below a kilometre, "1.2 km" up to 100 km, then whole kilometres. */
    fun distanceText(meters: Double): String = when {
        meters < 1000 -> "${meters.roundToInt()} m"
        meters < 100_000 -> "${fixed(meters / 1000, 1)} km"
        else -> "${(meters / 1000).roundToInt()} km"
    }

    /** A "geo:" link a map app understands for a place. */
    fun mapLink(lat: Double, lon: Double): String = "geo:${fixed(lat, 5).let { if (lat < 0) "-$it" else it }},${fixed(lon, 5).let { if (lon < 0) "-$it" else it }}"
}
