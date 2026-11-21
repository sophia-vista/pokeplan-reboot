package app.pokeplan.world.story

import app.pokeplan.world.WorldLayer

object StoryCatalog {
    private val episodes: List<StoryEpisode> by lazy {
        listOf(IntroMewChapter.episode()) + RescueChapters.all() + ExplorersChaptersA.all() + ExplorersChaptersB.all() + KantoChapters.all() + JohtoChapters.all() + HoennChapters.all() + SinnohChapters.all() +
            UnovaChapters.all() + KalosChapters.all() + AlolaChapters.all() + GalarChapters.all() + PaldeaChapters.all() + HisuiChapters.all() +
            RescuePostStory.all().map { it.copy(summary = RescuePostStory.summaries[it.chapter].orEmpty()) } +
            ExplorersPostStory.all().map { it.copy(summary = ExplorersPostStory.summaries[it.chapter].orEmpty()) }
    }

    fun all(): List<StoryEpisode> = episodes

    fun intro(): StoryEpisode = episodes.first { it.layer == null }

    fun episodeFor(layer: WorldLayer, chapter: Int): StoryEpisode? = episodes.firstOrNull { it.layer == layer && it.chapter == chapter }

    fun specialsOf(layer: WorldLayer): List<StoryEpisode> = episodes.filter { it.layer == layer && it.special }

    fun chaptersOf(layer: WorldLayer): List<StoryEpisode> = episodes.filter { it.layer == layer && !it.special }

    fun scene(id: String): StoryScene? = episodes.flatMap { it.scenes }.firstOrNull { it.id == id }

    fun peopleMet(scenesDone: Set<String>): List<StoryCharacter> =
        episodes.flatMap { it.scenes }.filter { it.id in scenesDone }.flatMap { it.cast }.filter { it.speciesId != null }.distinctBy { it.id }
            .map { it.copy(displayName = it.displayName.replace("big_", "")) }

    fun problems(): List<String> = episodes.flatMap { ep -> ep.scenes.flatMap { it.problems() } }
}
