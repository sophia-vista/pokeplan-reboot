package app.pokeplan.world

/**
 * A layer of the World map (docs/pokelife/MODULES/WORLD.md section 14, "Kanto, the first region layer"): the two Mystery Dungeon arcs (Rescue Team and
 * Explorers) and each mainline region are layers side by side. Every layer is open from the first day; a layer has its own dungeons, story chapters and
 * Guardian, while teams, rank, Poké and the Bag are shared.
 */
enum class WorldLayer(
    val id: String,
    val label: String,
    val blurb: String,
    /** The map picture (assets/world, a PNG without its extension) and its size in pixels; the home layer's map has stages instead. */
    val mapName: String? = null,
    val mapWidth: Int = 504,
    val mapHeight: Int = 336,
) {
    RESCUE("rescue", "Rescue Team", "Pokémon Square, a rumour about a team that arrived before the disasters, and a meteor."),
    EXPLORERS("explorers", "Explorers", "Treasure Town, the Exploration Guild and a thief of time."),
    KANTO("kanto", "Kanto", "The Genetic Crisis: Team Rocket's experiments, the legendary birds, Mewtwo and Mew.", "map-kanto", 192, 128),
    JOHTO("johto", "Johto", "The Sacred Balance: the burned tower, the sky and the sea, Ho-Oh and Lugia.", "map-johto", 144, 128),
    HOENN("hoenn", "Hoenn", "The Primal Awakening: Team Magma and Team Aqua, Groudon, Kyogre and Rayquaza.", "map-hoenn", 256, 152),
    SINNOH("sinnoh", "Sinnoh", "The Distortion of Creation: Team Galactic, time and space, Dialga, Palkia and Giratina.", "map-sinnoh", 240, 200),
    ;

    companion object {
        fun byId(id: String): WorldLayer? = entries.firstOrNull { it.id == id }
    }
}
