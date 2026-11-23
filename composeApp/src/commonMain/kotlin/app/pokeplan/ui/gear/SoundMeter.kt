package app.pokeplan.ui.gear

/**
 * What the Sound Meter shows after a frame. Built off the UI thread by [SoundMeter]; the composable only
 * assigns this one state object, so the FFT, the peaks and the running average never run on the main thread.
 */
data class SoundReading(
    val dbFs: Float,
    val low: Float,
    val high: Float,
    val average: Float,
    val pitchHz: Float?,
    val bands: List<Float>,
    val samples: Int,
)

/**
 * The Sound Meter's processing, in one plain class that touches no Android API, so it runs on
 * `Dispatchers.Default` and is unit-tested on the JVM.
 *
 * Every microphone frame updates the cheap running values (smoothed level, quietest/loudest/average);
 * the spectrum, pitch and bars are recomputed at most [redrawMillis] apart, and a frame only produces a
 * [SoundReading] when the displayed values actually changed. The meter therefore redraws at about 10 Hz
 * instead of once per audio buffer, and a steady room costs no UI work at all.
 */
class SoundMeter(
    private val spectrumSize: Int = 1024,
    private val bandCount: Int = 24,
    private val redrawMillis: Long = UI_MILLIS,
) {
    companion object {
        /** How often the screen may update: 10 Hz is fast enough for a level meter and cheap to draw. */
        const val UI_MILLIS = 100L
    }

    private var smooth: Float? = null
    private var low: Float? = null
    private var high: Float? = null
    private var sum = 0.0
    private var count = 0
    private var lastEmit = 0L
    private var last: SoundReading? = null
    private var forced = false

    /** Forgets everything, for when the microphone is stopped or the meter is reopened. */
    fun reset() {
        smooth = null; low = null; high = null; sum = 0.0; count = 0; lastEmit = 0L; last = null; forced = false
    }

    /** Restarts the quietest/loudest/average, keeping the smoothed level on screen. */
    fun resetExtremes() { low = null; high = null; sum = 0.0; count = 0; forced = true }

    /**
     * Handles one audio frame. [nowMillis] is a monotonic time so the throttle is independent of the wall clock.
     * Returns the new reading when the display should change, or null when it should stay as it is.
     */
    fun onFrame(frame: ShortArray, sampleRate: Int, nowMillis: Long): SoundReading? {
        val level = SoundMath.dbFs(frame)
        smooth = smooth?.let { it + (level - it) * 0.35f } ?: level
        val s = smooth!!
        if (s > -119f) {
            low = low?.let { minOf(it, s) } ?: s
            high = high?.let { maxOf(it, s) } ?: s
            sum += s
            count++
        }
        if (last != null && nowMillis - lastEmit < redrawMillis) return null
        lastEmit = nowMillis
        val spec = SoundMath.spectrum(frame, spectrumSize)
        val pitch = SoundMath.dominantHz(spec, sampleRate)
        val bands = List(bandCount) { b ->
            val from = (spec.size * (b / bandCount.toFloat()) * (b / bandCount.toFloat())).toInt()
            val to = maxOf(from + 1, (spec.size * ((b + 1) / bandCount.toFloat()) * ((b + 1) / bandCount.toFloat())).toInt())
            (spec.slice(from until minOf(to, spec.size)).maxOrNull() ?: 0.0).toFloat()
        }
        val next = SoundReading(s, low ?: s, high ?: s, if (count > 0) (sum / count).toFloat() else s, pitch, bands, count)
        if (!forced && sameShown(last, next)) return null
        forced = false
        last = next
        return next
    }

    /** True when switching from [previous] to [next] would not change what a person reads: the dB digit, the pitch digit and the bars. */
    internal fun sameShown(previous: SoundReading?, next: SoundReading): Boolean {
        if (previous == null) return false
        if (previous.dbFs.toInt() != next.dbFs.toInt()) return false
        if (previous.pitchHz?.toInt() != next.pitchHz?.toInt()) return false
        if (previous.bands.size != next.bands.size) return false
        return previous.bands.indices.all { (previous.bands[it] * 20f).toInt() == (next.bands[it] * 20f).toInt() }
    }
}
