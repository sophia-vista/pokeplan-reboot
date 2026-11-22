package app.pokeplan.world

sealed interface WorldMoment {
    data object Welcome : WorldMoment
    data class RankUp(val rank: ExplorerRank) : WorldMoment
    data class ChapterOpened(val layer: WorldLayer, val chapter: Chapter) : WorldMoment
    data class LegendJoined(val quest: LegendQuest) : WorldMoment
    data class SpecialEpisode(val layer: WorldLayer, val chapter: Int) : WorldMoment
}

object WorldMoments {
    fun adopted(data: WorldData): WorldData {
        var d = migratedFromHome(data)
        if (d.seenRank < 0) d = d.copy(seenRank = d.rank.ordinal)
        val missing = WorldLayer.entries.filter { it.id !in d.seenChapters }
        if (missing.isNotEmpty()) d = d.copy(seenChapters = d.seenChapters + missing.associate { it.id to Story.opened(d, it) })
        if (d.legendsSeen == null) d = d.copy(legendsSeen = d.legends)
        return d
    }

    fun pending(data: WorldData): List<WorldMoment> = buildList {
        if (INTRO_KEY !in data.episodesDone) add(WorldMoment.Welcome)
        if (data.rank.ordinal > data.seenRank && data.seenRank >= 0) add(WorldMoment.RankUp(data.rank))
        for (layer in WorldLayer.entries) {
            val seen = data.seenChapters[layer.id] ?: continue
            val chapters = Story.chaptersOf(layer)
            for (n in seen + 1..Story.opened(data, layer)) chapters.firstOrNull { it.number == n }?.let { add(WorldMoment.ChapterOpened(layer, it)) }
        }
        val shown = data.legendsSeen ?: return@buildList
        for (q in Legends.all) if (q.id in data.legends && q.id !in shown) add(WorldMoment.LegendJoined(q))
    }

    fun reached(data: WorldData): List<WorldMoment> = buildList {
        if (INTRO_KEY in data.episodesDone) add(WorldMoment.Welcome)
        for (r in ExplorerRank.entries) if (r.ordinal <= data.rank.ordinal) add(WorldMoment.RankUp(r))
        for (layer in WorldLayer.entries) {
            Story.chaptersOf(layer).filter { it.number <= Story.opened(data, layer) }.forEach { add(WorldMoment.ChapterOpened(layer, it)) }
            Legends.of(layer).filter { it.id in data.legends }.forEach { add(WorldMoment.LegendJoined(it)) }
        }
    }

    fun seen(data: WorldData, moment: WorldMoment): WorldData = when (moment) {
        WorldMoment.Welcome -> data
        is WorldMoment.SpecialEpisode -> data
        is WorldMoment.RankUp -> data.copy(seenRank = maxOf(data.seenRank, moment.rank.ordinal))
        is WorldMoment.LegendJoined -> data.copy(legendsSeen = (data.legendsSeen ?: data.legends) + moment.quest.id)
        is WorldMoment.ChapterOpened -> data.copy(seenChapters = data.seenChapters + (moment.layer.id to maxOf(data.seenChapters[moment.layer.id] ?: 0, moment.chapter.number)))
    }

    const val INTRO_KEY = "intro:1"

    fun migratedFromHome(data: WorldData): WorldData {
        if (data.episodesDone.none { it.startsWith("home:") } && data.storyResume.keys.none { it.startsWith("home:") } && "home" !in data.seenChapters) return data
        val opening = "home:1" in data.episodesDone
        return data.copy(
            episodesDone = data.episodesDone.filterNot { it.startsWith("home:") }.toSet() + (if (opening) setOf(INTRO_KEY) else emptySet()),
            storyResume = data.storyResume.filterKeys { !it.startsWith("home:") } + (data.storyResume["home:1"]?.let { mapOf(INTRO_KEY to it) } ?: emptyMap()),
            seenChapters = data.seenChapters - "home",
        )
    }

    fun rankOpened(rank: ExplorerRank): List<String> = ExplorerRank.entries.getOrNull(rank.ordinal - 1)?.nextRankOpens().orEmpty()
}
