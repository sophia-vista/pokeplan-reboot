package app.pokeplan.world.story

import app.pokeplan.world.WorldLayer

enum class SceneType { DIALOGUE, STAGE, DUNGEON, CINEMATIC }

enum class Expression { NORMAL, HAPPY, JOYOUS, INSPIRED, DETERMINED, SURPRISED, WORRIED, SAD, ANGRY, TEARY }

enum class Facing { UP, DOWN, LEFT, RIGHT }

const val BUDDY = "player_buddy"

data class StoryCharacter(val id: String, val displayName: String, val speciesId: Int? = null) {
    val isBuddy: Boolean get() = id == BUDDY
}

sealed interface SceneEvent {
    data class Show(val who: String, val x: Float, val y: Float, val facing: Facing = Facing.RIGHT, val expression: Expression = Expression.NORMAL) : SceneEvent
    data class Hide(val who: String) : SceneEvent
    data class Express(val who: String, val expression: Expression) : SceneEvent
    data class Face(val who: String, val facing: Facing) : SceneEvent
    data class Move(val who: String, val path: List<Pair<Float, Float>>, val millisPerCell: Int = 420) : SceneEvent
    data class Say(val who: String?, val text: String, val expression: Expression? = null) : SceneEvent
    data class Wait(val millis: Int) : SceneEvent
    data class Fade(val toBlack: Boolean, val millis: Int = 500) : SceneEvent
    data class Background(val id: String) : SceneEvent
    data class Item(val itemId: String, val x: Float, val y: Float) : SceneEvent
    data class TakeItem(val itemId: String) : SceneEvent
    data class Emote(val who: String, val mark: String) : SceneEvent
    data class Flag(val name: String) : SceneEvent
    data class Shake(val millis: Int = 450) : SceneEvent
    data class Flash(val white: Boolean = true) : SceneEvent
    data class Letterbox(val on: Boolean) : SceneEvent
    data class Cry(val who: String) : SceneEvent
    data class Sound(val name: String) : SceneEvent
    data class Choice(val prompt: String?, val options: List<StoryChoice>) : SceneEvent
    data class Branch(val flag: String, val events: List<SceneEvent>) : SceneEvent
    data object End : SceneEvent
}

data class StoryChoice(val label: String, val flag: String)

data class StoryScene(
    val id: String,
    val title: String,
    val type: SceneType,
    val background: String,
    val cast: List<StoryCharacter>,
    val events: List<SceneEvent>,
    val dungeonId: String? = null,
)

data class StoryEpisode(val layer: WorldLayer?, val chapter: Int, val title: String, val scenes: List<StoryScene>, val summary: String = "") {
    val key: String get() = "${layer?.id ?: INTRO}:$chapter"

    val special: Boolean get() = layer != null && chapter >= SPECIAL_BASE
}

const val SPECIAL_BASE = 101

const val INTRO = "intro"
