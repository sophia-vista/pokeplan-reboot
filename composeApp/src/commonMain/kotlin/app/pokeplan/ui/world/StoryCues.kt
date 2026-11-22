package app.pokeplan.ui.world

import app.pokeplan.ui.CryVoice
import app.pokeplan.ui.Sfx
import app.pokeplan.world.story.Actor
import app.pokeplan.world.story.Expression
import app.pokeplan.world.story.SceneType
import app.pokeplan.world.story.StoryScene
import app.pokeplan.ui.screens.long
import app.pokeplan.ui.screens.short

object StoryCues {
    fun opening(scene: StoryScene): List<Sfx> = listOfNotNull(if (scene.type == SceneType.DUNGEON) Sfx.STAIRS_DOWN else null, ambient(scene.background))

    fun ambient(background: String): Sfx? = when (background) {
        "icecave", "desert" -> Sfx.STORY_WIND
        "mountain" -> Sfx.STORY_WIND_HIGH
        "volcanocave" -> Sfx.STORY_FIRE
        "thunderplains" -> Sfx.STORY_ELECTRICITY
        else -> null
    }

    fun emote(symbol: String): Sfx? = when (symbol) {
        "!" -> Sfx.SURPRISE
        "?" -> Sfx.CURIOUS
        else -> null
    }

    fun expression(before: Expression?, now: Expression): Sfx? = if (before == now) null else when (now) {
        Expression.SURPRISED -> Sfx.SURPRISE
        Expression.WORRIED -> Sfx.CONFUSED
        Expression.ANGRY -> Sfx.MAD
        Expression.SAD, Expression.TEARY -> Sfx.SAD
        else -> null
    }

    fun cries(before: Expression?, now: Expression): Boolean = before != now && now in STRONG

    private val STRONG = setOf(Expression.JOYOUS, Expression.SURPRISED, Expression.ANGRY, Expression.SAD, Expression.TEARY, Expression.WORRIED)

    const val CRY_LEAD_MILLIS = 140L

    const val CRY_COOLDOWN_LINES = 3

    fun distance(scene: StoryScene, actor: Actor?): CryVoice.Distance = when {
        actor == null || scene.type == SceneType.DIALOGUE || scene.type == SceneType.CINEMATIC -> CryVoice.Distance.NEAR
        actor.y < 2.0f -> CryVoice.Distance.FAR
        actor.y < 3.4f -> CryVoice.Distance.MEDIUM
        else -> CryVoice.Distance.NEAR
    }

    fun shake(millis: Int): Sfx = if (millis >= 600) Sfx.STORY_RUMBLE_LOUD else Sfx.DAMAGE

    fun flash(): Sfx = Sfx.STORY_RINGING
}
