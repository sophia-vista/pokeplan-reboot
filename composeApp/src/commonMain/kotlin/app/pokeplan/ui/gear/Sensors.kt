package app.pokeplan.ui.gear

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The phone's motion and pressure sensors for the Compass, Level and Barometer. The Android app provides one (see `AndroidSensorFeed`); every
 * other platform has none and the tools say so. Each call starts listening and returns what stops it; nothing listens unless a tool is open.
 * Readings are raw (m/s², microtesla, hPa): the maths that turns them into degrees is in [SensorMath], so it can be tested without a phone.
 */
interface SensorFeed {
    /** Gravity as the accelerometer feels it, with the magnetic field (both in the phone's own axes), together. Null when either sensor is missing. */
    fun orientation(onReading: (gravity: FloatArray, field: FloatArray) -> Unit): (() -> Unit)?

    /** Gravity alone (the Level does not need the magnetic sensor). Null without an accelerometer. */
    fun gravity(onReading: (FloatArray) -> Unit): (() -> Unit)?

    /** Air pressure in hPa. Null without a barometer. */
    fun pressure(onReading: (Float) -> Unit): (() -> Unit)?
}

/** The maths behind the sensor tools. */
object SensorMath {
    private const val DEG = 180.0 / PI

    /**
     * The compass heading in degrees from north (0 to 360) of the phone's top edge, from gravity [g] and the magnetic field [m] (the phone's axes,
     * the way Android's own rotation matrix does it), so it is right when the phone is tilted. Null when the two are too weak or too parallel to say.
     */
    fun heading(g: FloatArray, m: FloatArray): Float? {
        val ax = g[0].toDouble(); val ay = g[1].toDouble(); val az = g[2].toDouble()
        val ex = m[0].toDouble(); val ey = m[1].toDouble(); val ez = m[2].toDouble()
        var hx = ey * az - ez * ay; var hy = ez * ax - ex * az; var hz = ex * ay - ey * ax
        val normH = sqrt(hx * hx + hy * hy + hz * hz)
        val normA = sqrt(ax * ax + ay * ay + az * az)
        if (normH < 0.1 || normA < 1e-6) return null
        hx /= normH; hy /= normH; hz /= normH
        val nax = ax / normA; val nay = ay / normA; val naz = az / normA
        val my = naz * hx - nax * hz
        val deg = atan2(hy, my) * DEG
        return normalize(deg.toFloat())
    }

    fun normalize(degrees: Float): Float { val d = degrees % 360f; return if (d < 0f) d + 360f else d }

    /** The shortest signed turn from [from] to [to], -180 to 180. */
    fun delta(from: Float, to: Float): Float { var d = (to - from) % 360f; if (d > 180f) d -= 360f; if (d < -180f) d += 360f; return d }

    /** A reading smoothed towards [next] by [alpha] (0 to 1), going the short way round the circle. */
    fun smooth(previous: Float?, next: Float, alpha: Float = 0.15f): Float = if (previous == null) next else normalize(previous + delta(previous, next) * alpha)

    private val winds = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")

    /** The 16-point compass name of a heading ("NE"). */
    fun cardinal(degrees: Float): String = winds[((normalize(degrees) / 22.5f).roundToInt()) % 16]

    /** How the phone is tilted: [pitch] (top edge up is positive) and [roll] (right edge down is positive), in degrees, from gravity. Null without gravity. */
    data class Tilt(val pitch: Float, val roll: Float) {
        /** The angle from flat. */
        val fromFlat: Float get() = sqrt(pitch * pitch + roll * roll)
    }

    fun tilt(g: FloatArray): Tilt? {
        val x = g[0].toDouble(); val y = g[1].toDouble(); val z = g[2].toDouble()
        if (sqrt(x * x + y * y + z * z) < 1.0) return null
        val pitch = atan2(y, sqrt(x * x + z * z)) * DEG
        val roll = atan2(-x, z) * DEG
        return Tilt(pitch.toFloat(), roll.toFloat())
    }

    /** Whether a tilt counts as level (within [within] degrees on both axes). */
    fun isLevel(t: Tilt, within: Float = 1f): Boolean = abs(t.pitch) <= within && abs(t.roll) <= within

    /** Degrees as the screen shows them, one decimal ("3.4°"). */
    fun degreesText(d: Float): String { val t = (abs(d) * 10).roundToInt(); return (if (d < 0 && t != 0) "-" else "") + t / 10 + "." + t % 10 + "°" }

    /**
     * A rough height in metres above where the pressure would be [seaLevelHpa], by the standard atmosphere. A phone barometer follows the weather
     * as much as the height, so this is a guide, not a surveyor; set the sea-level pressure to today's to improve it.
     */
    fun altitudeMeters(hpa: Float, seaLevelHpa: Float = 1013.25f): Float? {
        if (hpa < 300f || hpa > 1100f || seaLevelHpa < 900f || seaLevelHpa > 1100f) return null
        return (44330.0 * (1.0 - (hpa.toDouble() / seaLevelHpa).pow(1.0 / 5.255))).toFloat()
    }

    /** A pressure with one decimal ("1013.3 hPa"). */
    fun pressureText(hpa: Float): String { val t = (hpa * 10).roundToInt(); return "${t / 10}.${t % 10} hPa" }

    /** A drawn-pointer helper: the (x, y) of a point [r] from the centre at [degrees] clockwise from the top. */
    fun pointOnCircle(degrees: Float, r: Float): Pair<Float, Float> {
        val a = (degrees - 90f) / DEG.toFloat()
        return (r * cos(a.toDouble())).toFloat() to (r * sin(a.toDouble())).toFloat()
    }
}
