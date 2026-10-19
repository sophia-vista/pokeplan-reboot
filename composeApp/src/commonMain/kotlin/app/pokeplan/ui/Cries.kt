package app.pokeplan.ui

/**
 * Plays a Pokémon's cry (the real game cries, assets/cries). Each platform provides one: Android uses MediaPlayer, the
 * web uses an audio element. The desktop build has none (the JVM cannot play Ogg without extra libraries).
 */
fun interface CryPlayer {
    /** Plays the cry of species [speciesId] (its National Dex number). Never throws; a missing file is silent. */
    fun play(speciesId: Int)
}
