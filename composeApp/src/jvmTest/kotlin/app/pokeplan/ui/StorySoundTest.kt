package app.pokeplan.ui

import app.pokeplan.ui.world.StoryCues
import app.pokeplan.world.story.Expression
import app.pokeplan.world.story.SceneEvent
import app.pokeplan.world.story.StoryCatalog
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StorySoundTest {
    private fun assetsDir(): File = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "assets/sfx") }.first { it.isDirectory }

    @Test
    fun everySoundHasItsFile() {
        val dir = assetsDir()
        Sfx.entries.filter { !it.file.startsWith("jingle-") }
    }

    @Test
    fun everySoundAScriptNamesIsARealOne() {
        val names = Sfx.entries.map { it.name }.toSet()
        val used = StoryCatalog.all().flatMap { it.scenes }.flatMap { it.events }.filterIsInstance<SceneEvent.Sound>().map { it.name }
        used.forEach { assertTrue(it in names, "a script plays $it, which is not a sound") }
    }

    @Test
    fun theCuesAreQuietWhereTheSceneIsCalm() {
        assertNull(StoryCues.expression(Expression.HAPPY, Expression.HAPPY), "no change, no sound")
        assertNull(StoryCues.expression(null, Expression.NORMAL))
        assertNull(StoryCues.expression(Expression.NORMAL, Expression.HAPPY), "a pleased face needs none")
        assertEquals(Sfx.MAD, StoryCues.expression(Expression.NORMAL, Expression.ANGRY))
        assertEquals(Sfx.SURPRISE, StoryCues.expression(null, Expression.SURPRISED))
        assertEquals(Sfx.SURPRISE, StoryCues.emote("!"))
        assertNull(StoryCues.emote("♥"), "an emote with no clip stays silent")
        assertEquals(Sfx.DAMAGE, StoryCues.shake(300))
        assertEquals(Sfx.STORY_RUMBLE_LOUD, StoryCues.shake(800))
    }

    @Test
    fun aStrongFeelingCriesBeforeItIsShownAndACalmOneDoesNot() {
        assertTrue(StoryCues.cries(Expression.NORMAL, Expression.ANGRY))
        assertTrue(StoryCues.cries(null, Expression.SURPRISED))
        assertTrue(!StoryCues.cries(Expression.ANGRY, Expression.ANGRY), "no change, no cry")
        assertTrue(!StoryCues.cries(Expression.NORMAL, Expression.HAPPY) && !StoryCues.cries(Expression.NORMAL, Expression.DETERMINED))
        val stage = StoryCatalog.all().flatMap { it.scenes }.first { it.type == app.pokeplan.world.story.SceneType.STAGE }
        val conversation = StoryCatalog.all().flatMap { it.scenes }.first { it.type == app.pokeplan.world.story.SceneType.DIALOGUE }
        fun at(y: Float) = app.pokeplan.world.story.Actor("a", 5f, y, app.pokeplan.world.story.Facing.DOWN, Expression.NORMAL)
        assertEquals(CryVoice.Distance.FAR, StoryCues.distance(stage, at(1f)))
        assertEquals(CryVoice.Distance.MEDIUM, StoryCues.distance(stage, at(3f)))
        assertEquals(CryVoice.Distance.NEAR, StoryCues.distance(stage, at(5f)))
        assertEquals(CryVoice.Distance.NEAR, StoryCues.distance(conversation, at(1f)), "a conversation is always near")
    }

    @Test
    fun aDungeonSceneOpensWithTheStairsAndAColdPlaceWithWind() {
        val dungeon = StoryCatalog.all().flatMap { it.scenes }.first { it.dungeonId != null && it.type == app.pokeplan.world.story.SceneType.DUNGEON }
        assertTrue(Sfx.STAIRS_DOWN in StoryCues.opening(dungeon))
        assertEquals(Sfx.STORY_WIND, StoryCues.ambient("icecave"))
        assertNull(StoryCues.ambient("meadow"))
    }

    @Test
    fun aCryVoiceIsSmallStableAndPersonal() {
        val a = CryVoice.individual("pikachu-1")
        assertEquals(a, CryVoice.individual("pikachu-1"), "the same every time")
        assertNotEquals(a, CryVoice.individual("pikachu-2"), "two of one species differ")
        for (i in 0 until 200) {
            val v = CryVoice.individual("mon-$i")
            assertTrue(v.pitch in (1 - CryVoice.INDIVIDUAL)..(1 + CryVoice.INDIVIDUAL) && v.speed in 0.97f..1.03f, "mon-$i $v")
        }
        assertEquals(CryVoice.NEUTRAL, CryVoice.mood(Expression.NORMAL))
        assertTrue(CryVoice.mood(Expression.JOYOUS).pitch > 1f && CryVoice.mood(Expression.SAD).pitch < 1f && CryVoice.mood(Expression.SAD).speed < 1f)
        assertEquals(listOf(1f, 1.2f, 0.8f, 0.95f, 0.85f), CryVoice.Condition.entries.map { it.gain })
        assertTrue(CryVoice.mood(Expression.ANGRY).gain == CryVoice.Condition.LOUD.gain && CryVoice.mood(Expression.SAD).gain == CryVoice.Condition.LOW.gain)
        assertTrue(CryVoice.of("x", Expression.NORMAL, CryVoice.Distance.FAR).gain < CryVoice.of("x", Expression.NORMAL, CryVoice.Distance.MEDIUM).gain)
        assertTrue(CryVoice.of("x", Expression.JOYOUS, CryVoice.Distance.NEAR, CryVoice.Condition.LOUD).gain <= CryVoice.LOUD, "never past the loudest")
        Expression.entries.forEach { e ->
            val v = CryVoice.of("x", e)
            assertTrue(v.pitch in CryVoice.MIN..CryVoice.MAX && v.speed in CryVoice.MIN..CryVoice.MAX, "$e $v")
        }
        assertNotNull(CryVoice.of("player_buddy", Expression.WORRIED))
    }
}
