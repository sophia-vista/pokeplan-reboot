package app.pokeplan.world

enum class WorldLayer(
    val id: String,
    val label: String,
    val blurb: String,
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
    UNOVA("unova", "Unova", "Truth and Ideals: Team Plasma, the twin dragons Reshiram and Zekrom, and Kyurem in the ice."),
    KALOS("kalos", "Kalos", "Life and Destruction: Team Flare's ultimate weapon, Xerneas, Yveltal and Zygarde."),
    ALOLA("alola", "Alola", "The Sun and the Moon: island trials, Ultra Beasts, Solgaleo, Lunala and Necrozma."),
    GALAR("galar", "Galar", "The Darkest Day: Dynamax energy, the hero's sword and shield, Zacian, Zamazenta and Eternatus."),
    PALDEA("paldea", "Paldea", "The Treasure of Area Zero: the Great Crater, paradox Pokémon, Koraidon, Miraidon and Terapagos."),
    HISUI("hisui", "Hisui", "The Ancient Survey: Sinnoh long ago, a rift in the sky and Arceus, the Original One."),
    ;

    val hasMap: Boolean get() = mapName != null || this == RESCUE || this == EXPLORERS

    companion object {
        fun byId(id: String): WorldLayer? = entries.firstOrNull { it.id == id }
    }
}
