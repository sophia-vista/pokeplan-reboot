package app.pokeplan.ui

/**
 * Plays a Pokémon's cry (the real game cries, assets/cries) and the few real game sound effects ([Sfx], assets/sfx). Each
 * platform provides one: Android uses MediaPlayer, the web uses an audio element. The desktop build has none (the JVM
 * cannot play Ogg without extra libraries).
 */
fun interface CryPlayer {
    /** Plays the cry of species [speciesId] (its National Dex number) at [volume] (0 to 1). Never throws; a missing file is silent. */
    fun play(speciesId: Int, volume: Float)

    /** Plays [sound] at [volume] (0 to 1), alongside a cry rather than cutting it off. Never throws. */
    fun effect(sound: Sfx, volume: Float) {}
}

/** The real game sound effects in assets/sfx (fetched by tools/sprites/fetch_sfx.py), each kept for one kind of moment. */
enum class Sfx(val file: String) {
    /** The menu "select" blip: a task ticked off. */
    SELECT("select"),

    /** A short hop: each rock of a hatching egg. */
    JUMP("jump"),

    /** The healing chime: a berry or another item given to a Pokémon. */
    HEAL("heal"),

    /** Running from a battle: a Pokémon transferred to the Professor. */
    ESCAPE("escape"),

    /** The Pokémon Center jingle: an evolution's "Congratulations!". */
    POKEMON_CENTER("pokemon-center"),
}
