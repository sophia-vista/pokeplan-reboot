package app.pokeplan.world

data class Chapter(val number: Int, val title: String, val summary: String, val unlocks: String, val needsRank: ExplorerRank, val needsDungeons: Int)

object Story {
    const val GUARDIAN = "Primal Dialga"

    const val GUARDIAN_SPECIES = 483
    const val GUARDIAN_FORM = "dialga-primal"
    const val GUARDIAN_BONUS = "Temporal Mastery: every expedition climbs 10% faster"

    fun chaptersOf(layer: WorldLayer): List<Chapter> = when (layer) {
        WorldLayer.RESCUE -> RescueStory.chapters
        WorldLayer.EXPLORERS -> ExplorersStory.chapters
        WorldLayer.KANTO -> KantoStory.chapters
        WorldLayer.JOHTO -> JohtoStory.chapters
        WorldLayer.HOENN -> HoennStory.chapters
        WorldLayer.SINNOH -> SinnohStory.chapters
        WorldLayer.UNOVA -> UnovaStory.chapters
        WorldLayer.KALOS -> KalosStory.chapters
        WorldLayer.ALOLA -> AlolaStory.chapters
        WorldLayer.GALAR -> GalarStory.chapters
        WorldLayer.PALDEA -> PaldeaStory.chapters
        WorldLayer.HISUI -> HisuiStory.chapters
    }

    fun guardianBonus(layer: WorldLayer): String = when (layer) {
        WorldLayer.RESCUE -> RescueStory.GUARDIAN_BONUS
        WorldLayer.EXPLORERS -> GUARDIAN_BONUS
        WorldLayer.KANTO -> KantoStory.GUARDIAN_BONUS
        WorldLayer.JOHTO -> JohtoStory.GUARDIAN_BONUS
        WorldLayer.HOENN -> HoennStory.GUARDIAN_BONUS
        WorldLayer.SINNOH -> SinnohStory.GUARDIAN_BONUS
        WorldLayer.UNOVA -> UnovaStory.GUARDIAN_BONUS
        WorldLayer.KALOS -> KalosStory.GUARDIAN_BONUS
        WorldLayer.ALOLA -> AlolaStory.GUARDIAN_BONUS
        WorldLayer.GALAR -> GalarStory.GUARDIAN_BONUS
        WorldLayer.PALDEA -> PaldeaStory.GUARDIAN_BONUS
        WorldLayer.HISUI -> HisuiStory.GUARDIAN_BONUS
    }

    fun dungeonsCleared(data: WorldData, layer: WorldLayer = WorldLayer.RESCUE): Int = Dungeons.of(layer).count { (data.clears[it.id] ?: 0) > 0 }

    fun opened(data: WorldData, layer: WorldLayer = WorldLayer.RESCUE): Int =
        chaptersOf(layer).count { data.rank >= it.needsRank && dungeonsCleared(data, layer) >= it.needsDungeons }

    fun complete(data: WorldData, layer: WorldLayer = WorldLayer.RESCUE): Boolean = data.layerDone(layer)

    fun next(data: WorldData, layer: WorldLayer = WorldLayer.RESCUE): Chapter? = chaptersOf(layer).getOrNull(opened(data, layer))
}
