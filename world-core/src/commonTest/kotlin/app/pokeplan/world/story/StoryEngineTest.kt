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
        val ch1 = StoryCatalog.intro()
        assertEquals("intro:1", ch1.key)
        assertEquals(setOf(SceneType.STAGE, SceneType.DUNGEON), ch1.scenes.map { it.type }.toSet())
        for (layer in WorldLayer.entries) for (c in app.pokeplan.world.Story.chaptersOf(layer)) {
            val ep = assertNotNull(StoryCatalog.episodeFor(layer, c.number), "${layer.id} chapter ${c.number}")
            assertEquals(c.title, ep.title, "${layer.id}:${c.number} title")
        }
        assertEquals(StoryCatalog.all().size, StoryCatalog.all().map { it.key }.distinct().size)
        assertNull(StoryCatalog.episodeFor(WorldLayer.KANTO, 99), "no such chapter")
        assertEquals(ch1.scenes.first(), StoryCatalog.scene("intro-mew-1"))
    }

    @Test
    fun skippingLandsOnTheSameStateAsPlayingEveryEvent() {
        val scene = StoryCatalog.scene("intro-mew-3")!!
        val end = SceneState.finalOf(scene)
        assertNull(end.line)
        assertTrue(BUDDY !in end.actors, "who walked off is gone")
        assertTrue("mew" in end.actors && "sentret" in end.actors, "who stayed is still there")
        assertTrue(IntroMewChapter.MEW_JOINED in SceneState.finalOf(StoryCatalog.scene("intro-mew-6")!!).flags, "Mew agrees to join in the last test")
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

    @Test
    fun choicesSetFlagsAndBranchesFollowThem() {
        val scene = StoryCatalog.scene("kanto-4-camp")!!
        assertTrue(scene.events.any { it is SceneEvent.Choice } && scene.events.any { it is SceneEvent.Branch })
        val end = SceneState.finalOf(scene)
        assertTrue("protect" in end.flags && "truth" !in end.flags)
        assertTrue("truth" in SceneState.start(scene, setOf("truth")).flags)
        val s = SceneState(background = "x").apply(SceneEvent.Letterbox(true)).apply(SceneEvent.Choice(null, listOf(StoryChoice("A", "a"), StoryChoice("B", "b"))))
        assertTrue(s.letterbox && "a" in s.flags)
        val bad = StoryScene("c", "C", SceneType.DIALOGUE, "city", listOf(StoryCharacter(BUDDY, "B")), listOf(SceneEvent.Choice(null, listOf(StoryChoice("only", "x"))), SceneEvent.End))
        assertTrue(bad.problems().any { it.contains("two or three") })
        assertTrue(StoryCatalog.peopleMet(setOf("intro-mew-4")).map { it.displayName } == listOf("Pelly", "Mew", "Sentret"))
    }

    private val duo = listOf(buddyCharacter(), npc("a", "Ann", 25), npc("b", "Bo", 133))

    @Test
    fun branchesAreCheckedLikeTheTopLevel() {
        val ok = dialogue("t-ok", "t", "bg", duo) { show(BUDDY, 3f, 3f); choice(null, "x" to "fx", "y" to "fy"); branch("fx") { say(BUDDY, "hi") } }
        assertEquals(emptyList(), ok.problems())
        val bad = dialogue("t-bad", "t", "bg", duo) {
            show(BUDDY, 3f, 3f)
            branch("f") { say("ghost", "boo"); say(BUDDY, "x".repeat(200)); walk("b", 1f to 1f); show("b", 7f, 3f) }
        }
        val found = bad.problems().joinToString("\n")
        assertTrue("ghost is not in the cast" in found, found)
        assertTrue("over 160" in found, found)
        assertTrue("b is used before being shown" in found, found)
        assertTrue("changes who is on stage" in found, found)
    }

    @Test
    fun aDialogueShowsAtMostTwoAtOnce() {
        val three = dialogue("t-3", "t", "bg", duo) { show(BUDDY, 3f, 3f); show("a", 5f, 3f); show("b", 7f, 3f); say(BUDDY, "hi") }
        assertTrue(three.problems().any { "at most" in it }, three.problems().toString())
    }
    @Test
    fun theOptionalEpisodesAfterEachArcAreWrittenAndNeverPartOfTheChapters() {
        val rescue = StoryCatalog.specialsOf(WorldLayer.RESCUE)
        val explorers = StoryCatalog.specialsOf(WorldLayer.EXPLORERS)
        assertEquals(listOf(101, 102, 103, 104, 105), rescue.map { it.chapter })
        assertEquals(listOf(101, 102, 103, 104, 105, 106), explorers.map { it.chapter })
        (rescue + explorers).forEach { ep ->
            assertTrue(ep.special && ep.summary.isNotBlank() && ep.scenes.isNotEmpty(), ep.key)
            assertTrue(ep.scenes.flatMap { it.events }.none { it is SceneEvent.TakeItem }, ep.key)
        }
        for (layer in listOf(WorldLayer.RESCUE, WorldLayer.EXPLORERS)) {
            assertTrue(StoryCatalog.chaptersOf(layer).none { it.special })
            assertEquals(app.pokeplan.world.Story.chaptersOf(layer).size, StoryCatalog.chaptersOf(layer).size)
        }
        assertTrue(StoryCatalog.specialsOf(WorldLayer.KANTO).isEmpty())
    }
}
