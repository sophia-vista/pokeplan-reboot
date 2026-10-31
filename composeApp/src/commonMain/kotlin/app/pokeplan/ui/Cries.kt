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

    // The Mystery Dungeon UI sounds, cut by tools/sprites/convert_pmd_sfx.py. The first four were named by the owner.
    /** Moving or tapping a card, tab or row; also the switches and the letter scroller's tick. */
    CURSOR("cursor"),
    CONFIRM("confirm"),
    BACK("back"),
    ERROR("error"),

    // The launcher.
    DRAWER_OPEN("drawer-open"),
    DRAWER_CLOSE("drawer-close"),
    LONG_PRESS("long-press"),
    PIN("pin"),
    UNPIN("unpin"),
    HIDE("hide"),
    MOVE_PICKUP("move-pickup"),
    MOVE_DROP("move-drop"),
    UNINSTALL_WARN("uninstall-warn"),

    /** Home pressed during a focus session sends you back to Focus. */
    FOCUS_LOCK("focus-lock"),

    // Game moments.
    HABIT_TICK("habit-tick"),
    FOCUS_START("focus-start"),
    FOCUS_END("focus-end"),
    EGG_GET("egg-get"),
    LEVEL_UP("level-up"),
    RANK_UP("rank-up"),
    CANDY("candy"),
    ITEM_GET("item-get"),

    /** Unlocked something: a new Dex entry, an evolution, a region. */
    UNLOCK("unlock"),
    CALM_BREAK_START("calm-start"),
    CALM_BREAK_END("calm-end"),

    /** Notification sounds (the channel needs them as res/raw files, not through this player). */
    REMINDER("reminder"),
    TIMER_DONE("timer-done"),
}
