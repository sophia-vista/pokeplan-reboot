package app.pokeplan.ui.gear

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

object SoundMath {
    fun dbFs(samples: ShortArray, count: Int = samples.size): Float {
        if (count <= 0) return -120f
        var sum = 0.0
        for (i in 0 until count) { val v = samples[i] / 32768.0; sum += v * v }
        val rms = sqrt(sum / count)
        return if (rms < 1e-6) -120f else (20 * log10(rms)).toFloat().coerceAtLeast(-120f)
    }

    fun estimate(dbFs: Float, offset: Float): Float = (dbFs + offset).coerceIn(0f, 130f)

    fun describe(db: Float): String = when {
        db < 30 -> "Very quiet"
        db < 45 -> "Quiet, like a library"
        db < 60 -> "Normal talk"
        db < 75 -> "Busy room or traffic"
        db < 90 -> "Loud, like a vacuum cleaner"
        db < 105 -> "Very loud: long exposure can harm hearing"
        else -> "Painfully loud: protect your ears"
    }

    fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        require(n > 0 && n and (n - 1) == 0 && im.size == n) { "size must be a power of two" }
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val tr = re[i]; re[i] = re[j]; re[j] = tr; val ti = im[i]; im[i] = im[j]; im[j] = ti }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val ur = re[i + k]; val ui = im[i + k]
                    val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                    val nr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }

    fun spectrum(samples: ShortArray, size: Int = 1024): DoubleArray {
        val re = DoubleArray(size); val im = DoubleArray(size)
        val offset = (samples.size - size).coerceAtLeast(0)
        for (i in 0 until size) {
            val s = if (offset + i < samples.size) samples[offset + i] / 32768.0 else 0.0
            re[i] = s * (0.5 - 0.5 * cos(2 * PI * i / (size - 1)))
        }
        fft(re, im)
        return DoubleArray(size / 2) { sqrt(re[it] * re[it] + im[it] * im[it]) }
    }

    fun dominantHz(spectrum: DoubleArray, sampleRate: Int, size: Int = spectrum.size * 2): Float? {
        var best = 1
        for (i in 2 until spectrum.size) if (spectrum[i] > spectrum[best]) best = i
        if (spectrum[best] < 0.5) return null
        val a = spectrum[best - 1]; val b = spectrum[best]; val c = spectrum.getOrElse(best + 1) { b }
        val denom = a - 2 * b + c
        val shift = if (abs(denom) < 1e-9) 0.0 else 0.5 * (a - c) / denom
        return ((best + shift) * sampleRate / size).toFloat()
    }

    fun noteName(hz: Float): String {
        if (hz <= 0f) return ""
        val midi = (69 + 12 * (kotlin.math.ln(hz / 440.0) / kotlin.math.ln(2.0))).let { kotlin.math.round(it).toInt() }
        val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        return names[((midi % 12) + 12) % 12] + (midi / 12 - 1)
    }
}
