package app.pokeplan.world.story

import app.pokeplan.world.WorldLayer

/**
 * The authored episodes, by region and chapter. A chapter with no episode here plays its title card (the summary), as before; one with an
 * episode plays it, and the same episode replays from the Story archive. Content is data: adding a chapter's scenes does not touch the engine.
 */
object StoryCatalog {
    private val episodes: List<StoryEpisode> by lazy {
        listOf(IntroMewChapter.episode()) + RescueChapters.all() + ExplorersChapters.all() + KantoChapters.all() + JohtoChapters.all() + HoennChapters.all() + SinnohChapters.all()
    }

    fun all(): List<StoryEpisode> = episodes

    /** The opening, which belongs to no layer. */
    fun intro(): StoryEpisode = episodes.first { it.layer == null }

    fun episodeFor(layer: WorldLayer, chapter: Int): StoryEpisode? = episodes.firstOrNull { it.layer == layer && it.chapter == chapter }

    fun scene(id: String): StoryScene? = episodes.flatMap { it.scenes }.firstOrNull { it.id == id }

    /** The Pokémon characters met in the scenes watched ([scenesDone]), by name, first met first: the archive's people index. */
    fun peopleMet(scenesDone: Set<String>): List<StoryCharacter> =
        episodes.flatMap { it.scenes }.filter { it.id in scenesDone }.flatMap { it.cast }.filter { it.speciesId != null }.distinctBy { it.id }
            .map { it.copy(displayName = it.displayName.replace("big_", "")) }

    /** Everything wrong with every authored scene (empty when sound). */
    fun problems(): List<String> = episodes.flatMap { ep -> ep.scenes.flatMap { it.problems() } }
}
