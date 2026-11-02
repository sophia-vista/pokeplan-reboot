package app.pokeplan.world.story

import app.pokeplan.world.WorldLayer

/** The four kinds of scene (docs/world-story-engine.md): a conversation, a staged group scene, a scripted dungeon walk and a cinematic. */
enum class SceneType { DIALOGUE, STAGE, DUNGEON, CINEMATIC }

/** How a character looks while speaking. The portraits have the first six; the others fall back to [NORMAL] where a Pokémon has no such face. */
enum class Expression { NORMAL, HAPPY, JOYOUS, INSPIRED, DETERMINED, SURPRISED, WORRIED, SAD, ANGRY, TEARY }

enum class Facing { UP, DOWN, LEFT, RIGHT }

/** The id of the player's selected buddy in a scene: never a fixed partner (spec 3.1). */
const val BUDDY = "player_buddy"

/** Someone in a scene. [speciesId] null means the player's buddy (whoever it is); anyone else is a named Pokémon of a species. */
data class StoryCharacter(val id: String, val displayName: String, val speciesId: Int? = null) {
    val isBuddy: Boolean get() = id == BUDDY
}

/** One step of a scene's script. Positions are in grid cells of the scene (x across, y down). A line's text may use {buddy} and {player}. */
sealed interface SceneEvent {
    data class Show(val who: String, val x: Float, val y: Float, val facing: Facing = Facing.RIGHT, val expression: Expression = Expression.NORMAL) : SceneEvent
    data class Hide(val who: String) : SceneEvent
    data class Express(val who: String, val expression: Expression) : SceneEvent
    data class Face(val who: String, val facing: Facing) : SceneEvent
    /** Walks [who] along [path] (grid cells), [millisPerCell] a cell; they face the way they go. Blocks until they arrive. */
    data class Move(val who: String, val path: List<Pair<Float, Float>>, val millisPerCell: Int = 420) : SceneEvent
    /** A line of dialogue; [who] null is narration. [expression] changes the speaker's face for this line. Waits for a tap. */
    data class Say(val who: String?, val text: String, val expression: Expression? = null) : SceneEvent
    data class Wait(val millis: Int) : SceneEvent
    data class Fade(val toBlack: Boolean, val millis: Int = 500) : SceneEvent
    data class Background(val id: String) : SceneEvent
    /** An item (a PMD2 item id) lying on the floor at a cell, until it is hidden. */
    data class Item(val itemId: String, val x: Float, val y: Float) : SceneEvent
    data class TakeItem(val itemId: String) : SceneEvent
    /** A small mark over a character (an exclamation, a question). */
    data class Emote(val who: String, val mark: String) : SceneEvent
    data class Flag(val name: String) : SceneEvent
    data object End : SceneEvent
}

/** A scene: its [type], where it happens ([background] a scene image name, or for a DUNGEON scene the [dungeonId] whose tiles it is drawn on), who is in it and what happens. */
data class StoryScene(
    val id: String,
    val title: String,
    val type: SceneType,
    val background: String,
    val cast: List<StoryCharacter>,
    val events: List<SceneEvent>,
    val dungeonId: String? = null,
)

/** A chapter's episode: its scenes in order. [key] is "<layer id>:<chapter number>". */
data class StoryEpisode(val layer: WorldLayer, val chapter: Int, val title: String, val scenes: List<StoryScene>) {
    val key: String get() = "${layer.id}:$chapter"
}
