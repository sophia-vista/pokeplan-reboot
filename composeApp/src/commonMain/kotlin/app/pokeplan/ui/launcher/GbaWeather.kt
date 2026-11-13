package app.pokeplan.ui.launcher

/**
 * What Red Rescue Team's own code says about the weather layers of its ground screens (pret/pmd-red `src/ground_weather.c`, `src/ground_bg.c`), as
 * numbers the town can draw by. The trace, and which sheet is which layer, is in `docs/pokelife/FEATURES/LAUNCHER.md` section 4.4 and `ASSETS_AND_AUDIO.md` section 5.6; the layers are
 * rebuilt from the game's own files by `tools/weather/decode_bg.py`. Positions are in the game's own pixels (the town scales them by whole numbers).
 */
internal object GbaWeather {
    /** The game runs at the GBA's 59.7275 frames a second; everything below counts these ticks. */
    const val TICK_MILLIS = 1000.0 / 59.7275

    /** The tile animations of a weather layer have 16 frames (`.bpa` of W03P01 and W03P03). */
    const val FRAMES = 16

    /** Snow (W03P01): a frame of its tile animation lasts 4 + 1 ticks (the file says 4; the game moves on when the count has gone below 1). */
    const val SNOW_TICKS = 5

    /** Blizzard (W03P03): the file says 1, so 2 ticks a frame. */
    const val BLIZZARD_TICKS = 2

    /** Clouds (W01, `BLDALPHA` 0x40c: 4 parts the layer, 12 parts the scene below) and the grey layer of kind 15 (W06, the same): a quarter of the layer. */
    const val TEXTURE_ALPHA = 4f / 16f

    /** The tick the town is at after [millis]. */
    fun ticks(millis: Long): Long = (millis / TICK_MILLIS).toLong()

    /** Which of the [FRAMES] frames shows after [millis] when each lasts [ticksPerFrame]. */
    fun frame(millis: Long, ticksPerFrame: Int): Int = ((ticks(millis) / ticksPerFrame) % FRAMES).toInt()

    /**
     * How far the cloud shadows (W01, kind 9) have moved after [tick] ticks, in game pixels: the game adds 2 to a counter each tick and moves by a quarter of it
     * (so 1 pixel every 2 ticks to the right) and 1 to another and moves by a quarter of that (1 pixel every 4 ticks, upwards).
     */
    fun cloudShift(tick: Long): Pair<Int, Int> = (tick / 2).toInt() to -(tick / 4).toInt()

    /** The grey layer of kind 15 (W06) moves 1 pixel to the right every 2 ticks. */
    fun fastTextureShift(tick: Long): Int = (tick / 2).toInt()

    /** The streak layer of kind 14 (W05) moves 1 pixel to the right every 4 ticks. */
    fun slowTextureShift(tick: Long): Int = (tick / 4).toInt()
}
