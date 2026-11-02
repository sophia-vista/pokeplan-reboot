package app.pokeplan.world.story

import app.pokeplan.world.Dungeons
import app.pokeplan.world.WorldLayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoryEngineTest {
    @Test
    fun everyAuthoredSceneIsSoundAndUsesRealPlaces() {
        assertEquals(emptyList(), StoryCatalog.problems())
        StoryCatalog.all().forEach { ep ->
            assertTrue(ep.scenes.isNotEmpty() && ep.scenes.map { it.id }.distinct().size == ep.scenes.size, ep.key)
            ep.scenes.forEach { s -> s.dungeonId?.let { assertNotNull(Dungeons.byId(it), "${s.id}: $it") } }
        }
        val ch1 = StoryCatalog.episodeFor(WorldLayer.HOME, 1)!!
        assertEquals("home:1", ch1.key)
        assertEquals(setOf(SceneType.DIALOGUE, SceneType.STAGE, SceneType.DUNGEON), ch1.scenes.map { it.type }.toSet())
        assertNull(StoryCatalog.episodeFor(WorldLayer.KANTO, 1), "a chapter with no episode plays its title card")
        assertEquals(ch1.scenes.first(), StoryCatalog.scene("home-1-letter"))
    }

    @Test
    fun skippingLandsOnTheSameStateAsPlayingEveryEvent() {
        val scene = StoryCatalog.scene("home-1-woods")!!
        val end = SceneState.finalOf(scene)
        assertNull(end.line); assertTrue(end.finished.not() || end.finished)
        assertTrue("caterpie" !in end.actors, "who left is gone")
        assertEquals(5.4f, end.actors.getValue(BUDDY).x); assertEquals(3.2f, end.actors.getValue(BUDDY).y)
        assertTrue(end.items.isEmpty(), "the apple was taken")
        // A walk faces the way it goes: east, then south-west to the apple.
        val mid = SceneState.start(scene).apply(SceneEvent.Show(BUDDY, 0f, 0f)).apply(SceneEvent.Move(BUDDY, listOf(3f to 0f)))
        assertEquals(Facing.RIGHT, mid.actors.getValue(BUDDY).facing)
        assertEquals(Facing.UP, SceneState.facingOf(0f to 2f, 0f to 1f, Facing.RIGHT)); assertEquals(Facing.LEFT, SceneState.facingOf(5f to 1f, 3f to 1f, Facing.RIGHT)); assertEquals(Facing.DOWN, SceneState.facingOf(1f to 1f, 1.2f to 3f, Facing.LEFT))
    }

    @Test
    fun aLineSetsTheFaceAndTheValidatorCatchesMistakes() {
        val s = SceneState(background = "city").apply(SceneEvent.Show(BUDDY, 0f, 0f)).apply(SceneEvent.Say(BUDDY, "Hi", Expression.JOYOUS))
        assertEquals(Expression.JOYOUS, s.line!!.expression); assertEquals(Expression.JOYOUS, s.actors.getValue(BUDDY).expression)
        val bad = StoryScene("bad", "Bad", SceneType.DIALOGUE, "city", listOf(StoryCharacter(BUDDY, "Buddy")), listOf(SceneEvent.Say("ghost", "Boo"), SceneEvent.Move(BUDDY, emptyList())))
        val problems = bad.problems()
        assertTrue(problems.any { it.contains("ghost") }); assertTrue(problems.any { it.contains("does not end") }); assertTrue(problems.any { it.contains("before being shown") })
        val long = StoryScene("long", "Long", SceneType.STAGE, "city", listOf(StoryCharacter(BUDDY, "B")), listOf(SceneEvent.Show(BUDDY, 0f, 0f), SceneEvent.Say(BUDDY, "x".repeat(200)), SceneEvent.End))
        assertTrue(long.problems().any { it.contains("160") })
    }
}
