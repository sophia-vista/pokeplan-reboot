package app.pokeplan.ui.world

import app.pokeplan.ui.CryVoice
import app.pokeplan.ui.Sfx
import app.pokeplan.world.story.Actor
import app.pokeplan.world.story.Expression
import app.pokeplan.world.story.SceneType
import app.pokeplan.world.story.StoryScene
import app.pokeplan.ui.screens.long
import app.pokeplan.ui.screens.short

/**
 * The sounds the story engine adds on its own, so a scene is not silent when its script says nothing about sound: the emotes the Mystery Dungeon games
 * use for a feeling (the same clips as the buddy's reactions), the stairs on entering a dungeon, a rumble under a shake, and a soft wind, fire or
 * electricity when the scene's place has one. A script can still say `sound("STORY_TEMPORAL")` for anything particular. This is the one table to change
 * which clip plays when; none of the Explorers of Sky clips has been listened to yet (docs/assets.md).
 */
object StoryCues {
    /** What a scene opens with: the stairs for a dungeon, then the place's ambience. */
    fun opening(scene: StoryScene): List<Sfx> = listOfNotNull(if (scene.type == SceneType.DUNGEON) Sfx.STAIRS_DOWN else null, ambient(scene.background))

    /** The ambience of a place (a scene background), or null for a place with none worth a sound. */
    fun ambient(background: String): Sfx? = when (background) {
        "icecave", "desert" -> Sfx.STORY_WIND
        "mountain" -> Sfx.STORY_WIND_HIGH
        "volcanocave" -> Sfx.STORY_FIRE
        "thunderplains" -> Sfx.STORY_ELECTRICITY
        else -> null
    }

    /** The sound of an emote over a head. */
    fun emote(symbol: String): Sfx? = when (symbol) {
        "!" -> Sfx.SURPRISE
        "?" -> Sfx.CURIOUS
        else -> null
    }

    /** The sound of a speaker's face changing from [before] (null: first time on stage) to [now]; nothing for a calm or pleased face, which need none. */
    fun expression(before: Expression?, now: Expression): Sfx? = if (before == now) null else when (now) {
        Expression.SURPRISED -> Sfx.SURPRISE
        Expression.WORRIED -> Sfx.CONFUSED
        Expression.ANGRY -> Sfx.MAD
        Expression.SAD, Expression.TEARY -> Sfx.SAD
        else -> null
    }

    /**
     * Whether a speaker whose face goes from [before] to [now] cries as it does (what the games' sound design does: the cry comes **immediately before** the
     * feeling is shown, so what is heard and what is seen agree). Only a change into a strong feeling, so a calm or pleased face is silent.
     */
    fun cries(before: Expression?, now: Expression): Boolean = before != now && now in STRONG

    private val STRONG = setOf(Expression.JOYOUS, Expression.SURPRISED, Expression.ANGRY, Expression.SAD, Expression.TEARY, Expression.WORRIED)

    /** How long the cry leads the face, in milliseconds. */
    const val CRY_LEAD_MILLIS = 140L

    /** The fewest lines between two feeling-cries of one speaker, so an argument does not become a chorus. */
    const val CRY_COOLDOWN_LINES = 3

    /** How far a speaker sounds from the viewer: a conversation and a cinematic are near; on a stage the farther up the grid, the farther the voice (Legends: Arceus's near, medium and far cries). */
    fun distance(scene: StoryScene, actor: Actor?): CryVoice.Distance = when {
        actor == null || scene.type == SceneType.DIALOGUE || scene.type == SceneType.CINEMATIC -> CryVoice.Distance.NEAR
        actor.y < 2.0f -> CryVoice.Distance.FAR
        actor.y < 3.4f -> CryVoice.Distance.MEDIUM
        else -> CryVoice.Distance.NEAR
    }

    /** Under a shake: a long one is the ground rumbling, a short one a hit. */
    fun shake(millis: Int): Sfx = if (millis >= 600) Sfx.STORY_RUMBLE_LOUD else Sfx.DAMAGE

    /** A flash of light. */
    fun flash(): Sfx = Sfx.STORY_RINGING
}
