package app.pokeplan.ui.gear

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

interface SensorFeed {
    fun orientation(onReading: (gravity: FloatArray, field: FloatArray) -> Unit): (() -> Unit)?

    fun gravity(onReading: (FloatArray) -> Unit): (() -> Unit)?

    fun pressure(onReading: (Float) -> Unit): (() -> Unit)?

    fun rotation(onReading: (FloatArray) -> Unit): (() -> Unit)? = null

    fun steps(onReading: (Long) -> Unit, onUnavailable: (String) -> Unit): (() -> Unit)? = null
}

object SensorMath {
    private const val DEG = 180.0 / PI

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

    fun delta(from: Float, to: Float): Float { var d = (to - from) % 360f; if (d > 180f) d -= 360f; if (d < -180f) d += 360f; return d }

    fun smooth(previous: Float?, next: Float, alpha: Float = 0.15f): Float = if (previous == null) next else normalize(previous + delta(previous, next) * alpha)

    private val winds = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")

    fun cardinal(degrees: Float): String = winds[((normalize(degrees) / 22.5f).roundToInt()) % 16]

    data class Tilt(val pitch: Float, val roll: Float) {
        val fromFlat: Float get() = sqrt(pitch * pitch + roll * roll)
    }

    fun tilt(g: FloatArray): Tilt? {
        val x = g[0].toDouble(); val y = g[1].toDouble(); val z = g[2].toDouble()
        if (sqrt(x * x + y * y + z * z) < 1.0) return null
        val pitch = atan2(y, sqrt(x * x + z * z)) * DEG
        val roll = atan2(-x, z) * DEG
        return Tilt(pitch.toFloat(), roll.toFloat())
    }

    fun isLevel(t: Tilt, within: Float = 1f): Boolean = abs(t.pitch) <= within && abs(t.roll) <= within

    fun degreesText(d: Float): String { val t = (abs(d) * 10).roundToInt(); return (if (d < 0 && t != 0) "-" else "") + t / 10 + "." + t % 10 + "°" }

    fun altitudeMeters(hpa: Float, seaLevelHpa: Float = 1013.25f): Float? {
        if (hpa < 300f || hpa > 1100f || seaLevelHpa < 900f || seaLevelHpa > 1100f) return null
        return (44330.0 * (1.0 - (hpa.toDouble() / seaLevelHpa).pow(1.0 / 5.255))).toFloat()
    }

    fun pressureText(hpa: Float): String { val t = (hpa * 10).roundToInt(); return "${t / 10}.${t % 10} hPa" }

    fun pointOnCircle(degrees: Float, r: Float): Pair<Float, Float> {
        val a = (degrees - 90f) / DEG.toFloat()
        return (r * cos(a.toDouble())).toFloat() to (r * sin(a.toDouble())).toFloat()
    }
}
