package app.pokeplan.world

/**
 * The research notebook of one layer, worked out from what is already saved (no new field): the species its dungeons can
 * show (their wild tables and the Pokémon its jobs rescue), each marked as met (`WorldData.discovered`) and whether you own one.
 */
object Notebook {
    data class Entry(val speciesId: Int, val met: Boolean, val owned: Boolean)

    /** Every species [layer]'s dungeons can show, by Dex number. */
    fun species(layer: WorldLayer): List<Int> {
        val dungeons = Dungeons.of(layer)
        val wild = dungeons.flatMap { d -> d.wild.map { it.speciesId } }
        val rescued = Missions.all.filter { m -> m.rescued != null && dungeons.any { it.id == m.dungeonId } }.mapNotNull { it.rescued }
        return (wild + rescued).distinct().sorted()
    }

    /** [layer]'s notebook: each species with whether it was met and whether one is in the collection ([ownedSpecies]). */
    fun of(layer: WorldLayer, data: WorldData, ownedSpecies: Set<Int>): List<Entry> =
        species(layer).map { Entry(it, it in data.discovered, it in ownedSpecies) }

    /** How many of [layer]'s species were met, and how many there are. */
    fun progress(layer: WorldLayer, data: WorldData): Pair<Int, Int> = species(layer).let { all -> all.count { it in data.discovered } to all.size }
}
