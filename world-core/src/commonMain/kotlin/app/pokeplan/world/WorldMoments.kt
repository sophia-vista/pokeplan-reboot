package app.pokeplan.world

/** A moment World stops for: reaching a new exploration rank, or a story chapter opening. */
sealed interface WorldMoment {
    data class RankUp(val rank: ExplorerRank) : WorldMoment
    data class ChapterOpened(val layer: WorldLayer, val chapter: Chapter) : WorldMoment
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
    }

    /** [data] with [moment] counted as seen. */
    fun seen(data: WorldData, moment: WorldMoment): WorldData = when (moment) {
        is WorldMoment.RankUp -> data.copy(seenRank = maxOf(data.seenRank, moment.rank.ordinal))
        is WorldMoment.ChapterOpened -> data.copy(seenChapters = data.seenChapters + (moment.layer.id to maxOf(data.seenChapters[moment.layer.id] ?: 0, moment.chapter.number)))
    }

    /** What the rank [rank] opened, as phrases ("2 expedition teams", "the dungeons ..."); empty for the first rank. */
    fun rankOpened(rank: ExplorerRank): List<String> = ExplorerRank.entries.getOrNull(rank.ordinal - 1)?.nextRankOpens().orEmpty()
}
