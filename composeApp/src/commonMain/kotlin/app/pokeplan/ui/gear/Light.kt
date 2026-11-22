package app.pokeplan.ui.gear

class LightColor(val id: String, val label: String, val red: Int, val green: Int, val blue: Int, val note: String = "") {
    fun argb(brightness: Float): Long {
        val b = brightness.coerceIn(MIN_BRIGHTNESS, 1f)
        fun c(v: Int) = (v * b).toInt().coerceIn(0, 255).toLong()
        return 0xFF000000L or (c(red) shl 16) or (c(green) shl 8) or c(blue)
    }

    companion object { const val MIN_BRIGHTNESS = 0.05f }
}

object LightColors {
    val all = listOf(
        LightColor("white", "White", 255, 255, 255), LightColor("warm", "Warm", 255, 214, 153, "Easier on the eyes in the evening."),
        LightColor("red", "Red", 255, 30, 20, "Keeps your eyes used to the dark."), LightColor("amber", "Amber", 255, 160, 20),
    )

    fun byId(id: String): LightColor = all.firstOrNull { it.id == id } ?: all.first()
}

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

    const val MAX_MS = 5 * 60_000L
}
