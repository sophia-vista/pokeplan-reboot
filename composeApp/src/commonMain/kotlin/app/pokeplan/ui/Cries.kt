package app.pokeplan.ui

/**
 * Plays a Pokémon's cry (the real game cries, assets/cries) and the few real game sound effects ([Sfx], assets/sfx). Each
 * platform provides one: Android uses MediaPlayer, the web uses an audio element. The desktop build has none (the JVM
 * cannot play Ogg without extra libraries).
 */
fun interface CryPlayer {
    /** Plays the cry of species [speciesId] (its National Dex number) at [volume] (0 to 1). Never throws; a missing file is silent. */
    fun play(speciesId: Int, volume: Float)

    /**
     * Plays the cry with a voice: [pitch] and [speed] are 1 for the cry as recorded (see [CryVoice]). A player that cannot shift a sound plays it
     * as recorded, which is why this has a default and [play] stays the one method every player must have. Never throws.
     */
    fun playVoiced(speciesId: Int, volume: Float, pitch: Float, speed: Float) = play(speciesId, volume)

    /** Plays [sound] at [volume] (0 to 1), alongside a cry rather than cutting it off. Never throws. */
    fun effect(sound: Sfx, volume: Float) {}
}

/** The real game sound effects in assets/sfx (fetched by tools/sprites/fetch_sfx.py), each kept for one kind of moment. */
enum class Sfx(val file: String, val streams: Boolean = false) {
    /** The menu "select" blip: a task ticked off. */
    SELECT("select"),

    /** A short hop: each rock of a hatching egg. */
    JUMP("jump"),

    /** The healing chime: a berry or another item given to a Pokémon. */
    HEAL("heal"),

    /** Running from a battle: a Pokémon transferred to the Professor. */
    ESCAPE("escape"),

    /** The opening of the games' evolution music, 6.5 seconds, faded out: "What? it is evolving". Not the healing jingle. */
    EVOLUTION("evolution", streams = true),

    /** A monster joins the team (Red Rescue Team's own sound for it): a Pokémon hatches, arrives, is befriended or joins a party. */
    RECRUIT("recruit"),

    /** Going down the stairs: a team sets out on an expedition. */
    STAIRS_DOWN("stairs-down"),

    /** Going up the stairs: a team is recalled. */
    STAIRS_UP("stairs-up"),

    /** A monster learns a new move: a skill is trained or practised. */
    LEARN("learn"),

    /** A door opened: a module is entered from the Modules list. */
    DOOR_ENTER("door-enter"),

    // The Mystery Dungeon UI sounds, cut by tools/sprites/convert_pmd_sfx.py. The first four were named by the owner.
    /** Moving or tapping a card, tab or row; also the switches and the letter scroller's tick. */
    CURSOR("cursor"),
    CONFIRM("confirm"),
    BACK("back"),
    ERROR("error"),

    // The launcher.
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

    /** A World job claimed: the Explorers of Sky "mission success" jingle (assets/sky-sfx, rendered from the game's sound data). */
    MISSION_SUCCESS("jingle-mission-success"),

    // The story's sounds: Explorers of Sky's own cutscene effects, levelled to sit with the cries by tools/sprites/convert_story_sfx.py.
    // None of them has been listened to yet; the table in [StoryCues] is the one place to change which plays when.
    STORY_RUMBLE("story-rumble"),
    STORY_RUMBLE_LOUD("story-rumble-loud"),
    STORY_WIND("story-wind"),
    STORY_WIND_HIGH("story-wind-high"),
    STORY_FLOOD("story-flood"),
    STORY_ELECTRICITY("story-electricity"),
    STORY_ELECTRICITY_LONG("story-electricity-long"),
    STORY_FIRE("story-fire"),
    STORY_HOT_WATER("story-hot-water"),
    STORY_OMINOUS("story-ominous"),
    STORY_RINGING("story-ringing"),
    STORY_VISION("story-vision"),
    STORY_TEMPORAL("story-temporal"),
    STORY_HUMMING("story-humming"),
    STORY_STONESHIP("story-stoneship"),
    STORY_TEAM_CHARM("story-team-charm"),
    STORY_BEACH_DUSK("story-beach-dusk"),

    // Emotes, for the buddy's reactions.
    DAMAGE("damage"),
    SURPRISE("surprise"),
    CONFUSION_STATUS("confusion-status"),
    QUICK_ESCAPE("quick-escape"),
    SHOCK("shock"),
    URGENT_SHOCK("urgent-shock"),
    CURIOUS("curious"),
    CONFUSED("confused"),
    SAD("sad"),
    MAD("mad"),
    GREAT_SHOCK("great-shock"),
    EATING("eating"),

    // The notification sound is not played through this player: the channel needs it as res/raw/pokelife_notify.ogg (the same clip as
    // assets/sfx/reminder.ogg). assets/sfx also keeps pokemon-center, drawer-open, drawer-close and timer-done for later; the build does not bundle them.
}
