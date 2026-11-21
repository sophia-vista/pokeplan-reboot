package app.pokeplan.ui.launcher

internal object GbaWeather {
    const val TICK_MILLIS = 1000.0 / 59.7275

    const val FRAMES = 16

    const val SNOW_TICKS = 5

    const val BLIZZARD_TICKS = 2

    const val TEXTURE_ALPHA = 4f / 16f

    fun ticks(millis: Long): Long = (millis / TICK_MILLIS).toLong()

    fun frame(millis: Long, ticksPerFrame: Int): Int = ((ticks(millis) / ticksPerFrame) % FRAMES).toInt()

    fun cloudShift(tick: Long): Pair<Int, Int> = (tick / 2).toInt() to -(tick / 4).toInt()

    fun fastTextureShift(tick: Long): Int = (tick / 2).toInt()

    fun slowTextureShift(tick: Long): Int = (tick / 4).toInt()
}
