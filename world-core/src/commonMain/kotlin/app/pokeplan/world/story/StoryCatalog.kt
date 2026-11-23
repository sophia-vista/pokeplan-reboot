package app.pokeplan.world.story

import app.pokeplan.world.WorldLayer

object StoryCatalog {
    private val episodes: List<StoryEpisode> by lazy {
        listOf(IntroMewChapter.episode()) + MythicalChapters.all() + RescueChapters.all() + ExplorersChaptersA.all() + ExplorersChaptersB.all() + KantoChapters.all() + JohtoChapters.all() + HoennChapters.all() + SinnohChapters.all() +
            UnovaChapters.all() + KalosChapters.all() + AlolaChapters.all() + GalarChapters.all() + PaldeaChapters.all() + HisuiChapters.all() +
            RescuePostStory.all().map { it.copy(summary = RescuePostStory.summaries[it.chapter].orEmpty()) } +
            ExplorersPostStory.all().map { it.copy(summary = ExplorersPostStory.summaries[it.chapter].orEmpty()) }
    }

    fun all(): List<StoryEpisode> = episodes

    fun intro(): StoryEpisode = episodes.first { it.layer == null && it.chapter == 1 }

    /**
     * The seven optional follow-up Mythical introductions (`docs/world-story-rewrite.md`). They belong
     * to no region, like the opening, and are reachable from the story archive rather than from a
     * regional chapter; the table that pairs each one with its recruit is [MythicalChapters.recruits].
     */
    fun mythicalEpisodes(): List<StoryEpisode> = episodes.filter { it.layer == null && it.chapter >= MythicalChapters.FIRST_CHAPTER }

    fun mythical(chapter: Int): StoryEpisode? =
        episodes.firstOrNull { it.layer == null && it.chapter == chapter && chapter >= MythicalChapters.FIRST_CHAPTER }

    fun episodeFor(layer: WorldLayer, chapter: Int): StoryEpisode? = episodes.firstOrNull { it.layer == layer && it.chapter == chapter }

    fun specialsOf(layer: WorldLayer): List<StoryEpisode> = episodes.filter { it.layer == layer && it.special }

    fun chaptersOf(layer: WorldLayer): List<StoryEpisode> = episodes.filter { it.layer == layer && !it.special }

    fun scene(id: String): StoryScene? = episodes.flatMap { it.scenes }.firstOrNull { it.id == id }

    fun peopleMet(scenesDone: Set<String>): List<StoryCharacter> =
        episodes.flatMap { it.scenes }.filter { it.id in scenesDone }.flatMap { it.cast }.filter { it.speciesId != null }.distinctBy { it.id }
            .map { it.copy(displayName = it.displayName.replace("big_", "")) }

    fun problems(): List<String> = episodes.flatMap { ep -> ep.scenes.flatMap { it.problems() } }
}
