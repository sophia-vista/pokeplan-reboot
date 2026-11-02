package app.pokeplan.world

/** A moment World stops for: reaching a new exploration rank, or a story chapter opening. */
sealed interface WorldMoment {
    data class RankUp(val rank: ExplorerRank) : WorldMoment
    data class ChapterOpened(val layer: WorldLayer, val chapter: Chapter) : WorldMoment
    /** A legendary quest was completed: its Pokémon joined as a Guardian. */
    data class LegendJoined(val quest: LegendQuest) : WorldMoment
}

/**
 * Which World moments the player has not seen yet, worked out from what is saved (`seenRank`, `seenChapters`) against what the save has
 * reached, so it does not depend on how the progress happened (exploring away from the app, claiming a job, a research reward). A save
 * from before this existed adopts what it already reached without replaying it.
 */
object WorldMoments {
    /** [data] with the rank and chapters it has already reached counted as seen, where nothing was recorded yet (an old save, a layer not looked at). */
    fun adopted(data: WorldData): WorldData {
        var d = data
        if (d.seenRank < 0) d = d.copy(seenRank = d.rank.ordinal)
        val missing = WorldLayer.entries.filter { it.id !in d.seenChapters }
        if (missing.isNotEmpty()) d = d.copy(seenChapters = d.seenChapters + missing.associate { it.id to Story.opened(d, it) })
        if (d.legendsSeen == null) d = d.copy(legendsSeen = d.legends)
        return d
    }

    /** The moments not yet seen, in the order to play them: the rank, then chapters layer by layer, each in order. Call on [adopted] data. */
    fun pending(data: WorldData): List<WorldMoment> = buildList {
        if (data.rank.ordinal > data.seenRank && data.seenRank >= 0) add(WorldMoment.RankUp(data.rank))
        for (layer in WorldLayer.entries) {
            val seen = data.seenChapters[layer.id] ?: continue
            val chapters = Story.chaptersOf(layer)
            for (n in seen + 1..Story.opened(data, layer)) chapters.firstOrNull { it.number == n }?.let { add(WorldMoment.ChapterOpened(layer, it)) }
        }
        val shown = data.legendsSeen ?: return@buildList
        for (q in Legends.all) if (q.id in data.legends && q.id !in shown) add(WorldMoment.LegendJoined(q))
    }

    /** Every moment [data] has reached, in the order of the story (ranks, then each layer's chapters, then its legendary quests): what the archive lists, to play again. */
    fun reached(data: WorldData): List<WorldMoment> = buildList {
        for (r in ExplorerRank.entries) if (r.ordinal <= data.rank.ordinal) add(WorldMoment.RankUp(r))
        for (layer in WorldLayer.entries) {
            Story.chaptersOf(layer).filter { it.number <= Story.opened(data, layer) }.forEach { add(WorldMoment.ChapterOpened(layer, it)) }
            Legends.of(layer).filter { it.id in data.legends }.forEach { add(WorldMoment.LegendJoined(it)) }
        }
    }

    /** [data] with [moment] counted as seen. */
    fun seen(data: WorldData, moment: WorldMoment): WorldData = when (moment) {
        is WorldMoment.RankUp -> data.copy(seenRank = maxOf(data.seenRank, moment.rank.ordinal))
        is WorldMoment.LegendJoined -> data.copy(legendsSeen = (data.legendsSeen ?: data.legends) + moment.quest.id)
        is WorldMoment.ChapterOpened -> data.copy(seenChapters = data.seenChapters + (moment.layer.id to maxOf(data.seenChapters[moment.layer.id] ?: 0, moment.chapter.number)))
    }

    /** What the rank [rank] opened, as phrases ("2 expedition teams", "the dungeons ..."); empty for the first rank. */
    fun rankOpened(rank: ExplorerRank): List<String> = ExplorerRank.entries.getOrNull(rank.ordinal - 1)?.nextRankOpens().orEmpty()
}
