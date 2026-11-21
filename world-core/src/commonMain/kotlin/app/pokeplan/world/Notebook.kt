package app.pokeplan.world

object Notebook {
    data class Entry(val speciesId: Int, val met: Boolean, val owned: Boolean)

    fun species(layer: WorldLayer): List<Int> {
        val dungeons = Dungeons.of(layer)
        val wild = dungeons.flatMap { d -> d.wild.map { it.speciesId } }
        val rescued = Missions.all.filter { m -> m.rescued != null && dungeons.any { it.id == m.dungeonId } }.mapNotNull { it.rescued }
        return (wild + rescued).distinct().sorted()
    }

    fun of(layer: WorldLayer, data: WorldData, ownedSpecies: Set<Int>): List<Entry> =
        species(layer).map { Entry(it, it in data.discovered, it in ownedSpecies) }

    fun progress(layer: WorldLayer, data: WorldData): Pair<Int, Int> = species(layer).let { all -> all.count { it in data.discovered } to all.size }
}
