package app.pokeplan.world.story

import app.pokeplan.world.WorldLayer

/**
 * The authored episodes, by region and chapter. A chapter with no episode here plays its title card (the summary), as before; one with an
 * episode plays it, and the same episode replays from the Story archive. Content is data: adding a chapter's scenes does not touch the engine.
 */
object StoryCatalog {
    private val episodes: List<StoryEpisode> by lazy { listOf(MysteryDungeonStory.chapter1()) }

    fun all(): List<StoryEpisode> = episodes

    fun episodeFor(layer: WorldLayer, chapter: Int): StoryEpisode? = episodes.firstOrNull { it.layer == layer && it.chapter == chapter }

    fun scene(id: String): StoryScene? = episodes.flatMap { it.scenes }.firstOrNull { it.id == id }

    /** Everything wrong with every authored scene (empty when sound). */
    fun problems(): List<String> = episodes.flatMap { ep -> ep.scenes.flatMap { it.problems() } }
}
