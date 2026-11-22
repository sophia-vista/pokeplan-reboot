package app.pokeplan.ui

fun interface CryPlayer {
    fun play(speciesId: Int, volume: Float)

    fun playVoiced(speciesId: Int, volume: Float, pitch: Float, speed: Float) = play(speciesId, volume)

    fun effect(sound: Sfx, volume: Float) {}
}

enum class Sfx(val file: String, val streams: Boolean = false) {
    SELECT("select"),

    JUMP("jump"),

    HEAL("heal"),

    ESCAPE("escape"),

    EVOLUTION("evolution", streams = true),

    RECRUIT("recruit"),

    STAIRS_DOWN("stairs-down"),

    STAIRS_UP("stairs-up"),

    LEARN("learn"),

    DOOR_ENTER("door-enter"),

    CURSOR("cursor"),
    CONFIRM("confirm"),
    BACK("back"),
    ERROR("error"),

    LONG_PRESS("long-press"),
    PIN("pin"),
    UNPIN("unpin"),
    HIDE("hide"),
    MOVE_PICKUP("move-pickup"),
    MOVE_DROP("move-drop"),
    UNINSTALL_WARN("uninstall-warn"),

    FOCUS_LOCK("focus-lock"),

    HABIT_TICK("habit-tick"),
    FOCUS_START("focus-start"),
    FOCUS_END("focus-end"),
    EGG_GET("egg-get"),
    LEVEL_UP("level-up"),
    RANK_UP("rank-up"),
    CANDY("candy"),
    ITEM_GET("item-get"),

    UNLOCK("unlock"),
    CALM_BREAK_START("calm-start"),
    CALM_BREAK_END("calm-end"),

    MISSION_SUCCESS("jingle-mission-success"),

    TEAM_RECALLED("jingle-decent-sentry-duty"),
    RESEARCH_DONE("jingle-team-charm-jingle"),

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

}
