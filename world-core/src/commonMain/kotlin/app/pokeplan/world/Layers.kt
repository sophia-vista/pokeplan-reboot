package app.pokeplan.world

/**
 * A layer of the World map (docs/world.md, "Kanto, the first region layer"): the Mystery Dungeon world is the home layer, and each
 * mainline region is a layer beside it. Every layer is open from the first day; a layer has its own dungeons, story chapters and
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
    HOME("home", "Mystery Dungeon world", "Explorers' guilds, strange dungeons and a time Guardian."),
    KANTO("kanto", "Kanto", "The Genetic Crisis: Team Rocket's experiments, the legendary birds, Mewtwo and Mew.", "map-kanto", 192, 128),
    JOHTO("johto", "Johto", "The Sacred Balance: the burned tower, the sky and the sea, Ho-Oh and Lugia.", "map-johto", 144, 128),
    ;

    companion object {
        fun byId(id: String): WorldLayer? = entries.firstOrNull { it.id == id }
    }
}
