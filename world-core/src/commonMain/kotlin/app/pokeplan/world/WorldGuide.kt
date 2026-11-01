package app.pokeplan.world

/** What to do next in World, and why. [dungeonId] is the dungeon to go to when the step is to explore one that is open. */
data class NextStep(val title: String, val reason: String, val dungeonId: String? = null)

/**
 * The World landing's one suggestion (docs/world.md, "show the next recommended destination and why"): the first open dungeon
 * not yet cleared, else what the nearest closed dungeon is waiting for, else a line about what an all-cleared World still offers.
 * Plain data in, plain words out, so it is tested without a screen.
 */
object WorldGuide {
    fun nextStep(data: WorldData, layer: WorldLayer = WorldLayer.HOME): NextStep {
        val dungeons = Dungeons.of(layer)
        fun isOpen(d: DungeonDef) = data.rank >= d.needs && Story.opened(data, layer) >= d.chapter && !(d.postStory && !data.layerDone(layer))
        dungeons.firstOrNull { isOpen(it) && (data.clears[it.id] ?: 0) == 0 }?.let { d ->
            return NextStep("Explore ${d.name}", if (dungeons.none { (data.clears[it.id] ?: 0) > 0 }) "It is the first place to explore." else "It is open at your rank and you have not cleared it yet.", d.id)
        }
        dungeons.firstOrNull { !isOpen(it) }?.let { d ->
            val reason = when {
                d.postStory && !data.layerDone(layer) -> "It opens when the story is complete."
                data.rank < d.needs -> "Reach ${d.needs.label} rank for ${d.name}: ${d.needs.points - data.xp} more Exploration XP."
                else -> {
                    val chapter = Story.chaptersOf(layer)[d.chapter - 1]
                    val cleared = Story.dungeonsCleared(data, layer)
                    "${d.name} needs story chapter ${d.chapter}, ${chapter.title}: clear ${(chapter.needsDungeons - cleared).coerceAtLeast(1)} more different dungeon${if (chapter.needsDungeons - cleared > 1) "s" else ""}."
                }
            }
            return NextStep("Work towards ${d.name}", reason)
        }
        return NextStep("Everything is cleared", "Keep exploring for finds, research, jobs and treasure.")
    }
}
