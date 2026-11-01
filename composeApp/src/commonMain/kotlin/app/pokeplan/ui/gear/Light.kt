package app.pokeplan.ui.gear

/** A colour of the screen light, by saved name, as red, green and blue (0 to 255). */
class LightColor(val id: String, val label: String, val red: Int, val green: Int, val blue: Int, val note: String = "") {
    /** The colour scaled to [brightness] (0.05 to 1), as one number a screen can paint: 0xAARRGGBB with full opacity. */
    fun argb(brightness: Float): Long {
        val b = brightness.coerceIn(MIN_BRIGHTNESS, 1f)
        fun c(v: Int) = (v * b).toInt().coerceIn(0, 255).toLong()
        return 0xFF000000L or (c(red) shl 16) or (c(green) shl 8) or c(blue)
    }

    companion object { const val MIN_BRIGHTNESS = 0.05f }
}

/** The colours the screen light offers: white, a warm one for the eyes at night, red to keep night vision and amber. */
object LightColors {
    val all = listOf(
        LightColor("white", "White", 255, 255, 255), LightColor("warm", "Warm", 255, 214, 153, "Easier on the eyes in the evening."),
        LightColor("red", "Red", 255, 30, 20, "Keeps your eyes used to the dark."), LightColor("amber", "Amber", 255, 160, 20),
    )

    fun byId(id: String): LightColor = all.firstOrNull { it.id == id } ?: all.first()
}

/** The SOS signal as flashes: pairs of (light on, how long in milliseconds), in Morse (three short, three long, three short, then a long rest), one unit [unitMs] long. */
object Sos {
    fun pattern(unitMs: Long = 250): List<Pair<Boolean, Long>> {
        fun letter(dashes: Boolean): List<Pair<Boolean, Long>> {
            val out = mutableListOf<Pair<Boolean, Long>>()
            repeat(3) { i ->
                out += true to (if (dashes) 3 else 1) * unitMs
                if (i < 2) out += false to unitMs
            }
            return out
        }
        return letter(false) + (false to 3 * unitMs) + letter(true) + (false to 3 * unitMs) + letter(false) + (false to 7 * unitMs)
    }

    /** The signal runs for at most this long before it stops by itself, so a forgotten SOS cannot drain the battery. */
    const val MAX_MS = 5 * 60_000L
}
