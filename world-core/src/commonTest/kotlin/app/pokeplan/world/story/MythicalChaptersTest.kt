package app.pokeplan.world.story

import app.pokeplan.core.dex.Dex
import app.pokeplan.world.WorldLayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The seven follow-up Mythical introductions (`docs/world-story-rewrite.md`).
 *
 * They are optional, self-contained episodes that belong to no region, like the opening, so these
 * checks pin the contract the story archive and the save rely on: the catalog holds them, each one
 * is sound, its last scene sets its join flag, and the single [MythicalChapters.recruits] table is
 * the only place the episode key, the join flag and the joining species are written down.
 */
class MythicalChaptersTest {
    private val titles = listOf(
        "The Wrong Door", "The Perfect Run", "One More Try", "A Little Wish", "The Unnoticed Path", "The Long Way Around", "A Dream with Teeth",
    )
    private val species = listOf(720, 494, 251, 385, 802, 492, 491)
    private val flags = listOf(
        "mythical-hoopa-joined", "mythical-victini-joined", "mythical-celebi-joined", "mythical-jirachi-joined",
        "mythical-marshadow-joined", "mythical-shaymin-joined", "mythical-darkrai-joined",
    )

    @Test
    fun everyMythicalFollowUpIsInTheCatalogAndSound() {
        val recruits = MythicalChapters.recruits
        assertEquals(7, recruits.size)
        assertEquals(titles, recruits.map { it.title })
        assertEquals(species, recruits.map { it.speciesId })
        assertEquals(flags, recruits.map { it.joinFlag })
        assertEquals((2..8).toList(), recruits.map { it.chapter })
        assertEquals(emptyList(), StoryCatalog.problems())

        val episodes = StoryCatalog.mythicalEpisodes()
        assertEquals(7, episodes.size)
        recruits.forEach { recruit ->
            val ep = assertNotNull(StoryCatalog.mythical(recruit.chapter), recruit.key)
            assertEquals(recruit.key, ep.key)
            assertEquals(recruit.title, ep.title)
            assertEquals(recruit.summary, ep.summary)
            assertNull(ep.layer, "a Mythical follow-up belongs to no region")
            assertTrue(ep.scenes.size in 4..8, "${recruit.key}: ${ep.scenes.size} scenes")
            assertEquals(emptyList(), ep.scenes.flatMap { it.problems() }, recruit.key)
            assertTrue(ep.scenes.map { it.id }.distinct().size == ep.scenes.size, recruit.key)
        }
        // The opening is still chapter 1 and is never counted as a follow-up.
        assertEquals("intro:1", StoryCatalog.intro().key)
        assertTrue(StoryCatalog.mythical(1) == null)
        assertEquals(episodes.map { it.key }, StoryCatalog.all().filter { it.layer == null && it.chapter >= 2 }.map { it.key })
    }

    @Test
    fun everyFollowUpEndsWithItsMythicalJoining() {
        MythicalChapters.recruits.forEach { recruit ->
            val ep = assertNotNull(StoryCatalog.mythical(recruit.chapter), recruit.key)
            val last = ep.scenes.last()
            val joined = SceneState.finalOf(last, SceneState.start(last).flags).flags
            // The joining is a character decision in the closing scene, exactly like Mew's.
            assertTrue(recruit.joinFlag in joined, "${recruit.key}: the last scene does not set ${recruit.joinFlag}")
            assertEquals(1, ep.scenes.flatMap { it.events }.count { it == SceneEvent.Flag(recruit.joinFlag) }, "${recruit.key}: the join flag is set once")
            assertTrue(ep.scenes.flatMap { it.events }.none { it is SceneEvent.TakeItem }, "${recruit.key}: a story scene must not take an item")
            ep.scenes.forEach { scene ->
                assertTrue(scene.events.none { it == SceneEvent.Flag(recruit.joinFlag) } || scene == last, "${recruit.key}: only the last scene joins")
            }
        }
    }

    @Test
    fun theRecruitTableIsTheOnlyMappingAndItIsConsistent() {
        val recruits = MythicalChapters.recruits
        assertEquals(recruits.size, recruits.map { it.chapter }.distinct().size, "chapters")
        assertEquals(recruits.size, recruits.map { it.key }.distinct().size, "episode keys")
        assertEquals(recruits.size, recruits.map { it.joinFlag }.distinct().size, "join flags")
        assertEquals(recruits.size, recruits.map { it.speciesId }.distinct().size, "species")
        recruits.forEach { recruit ->
            assertEquals(recruit, MythicalChapters.recruitFor(recruit.key))
            assertEquals(recruit, MythicalChapters.recruitForChapter(recruit.chapter))
            assertEquals(StoryCatalog.mythical(recruit.chapter)?.title, recruit.title)
            assertEquals(Dex.species(recruit.speciesId).name, recruit.name)
            assertTrue(recruit.joinFlag.startsWith("mythical-") && recruit.joinFlag.endsWith("-joined"), recruit.joinFlag)
        }
        assertNull(MythicalChapters.recruitFor("intro:1"))
        assertNull(MythicalChapters.recruitForChapter(1))
    }

    @Test
    fun theFollowUpsDoNotDisturbTheRegionalCampaignsOrTheirSpecials() {
        // A layerless follow-up is not a regional chapter and not a layer's "after the story" episode.
        for (layer in WorldLayer.entries) {
            assertTrue(StoryCatalog.chaptersOf(layer).none { it.layer == null }, layer.id)
            assertTrue(StoryCatalog.specialsOf(layer).none { it.layer == null }, layer.id)
            assertTrue(StoryCatalog.chaptersOf(layer).size == app.pokeplan.world.Story.chaptersOf(layer).size, layer.id)
        }
        assertEquals(emptyList(), StoryCatalog.specialsOf(WorldLayer.KANTO))
        assertEquals(listOf(101, 102, 103, 104, 105), StoryCatalog.specialsOf(WorldLayer.RESCUE).map { it.chapter })
        assertEquals(listOf(101, 102, 103, 104, 105, 106), StoryCatalog.specialsOf(WorldLayer.EXPLORERS).map { it.chapter })
        // Every scene id in the whole catalog is unique, and no two episodes share a key.
        val scenes = StoryCatalog.all().flatMap { it.scenes }
        assertEquals(scenes.size, scenes.map { it.id }.distinct().size, "a scene id is used twice")
        assertEquals(StoryCatalog.all().size, StoryCatalog.all().map { it.key }.distinct().size, "an episode key is used twice")
    }
}
