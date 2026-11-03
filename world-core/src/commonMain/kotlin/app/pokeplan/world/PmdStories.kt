package app.pokeplan.world

/**
 * The Rescue Team arc (docs/world-story-rewrite.md): Pokémon Square, the rumour about the team that arrived before the disasters, and the
 * meteor. Its Guardian dungeon is the Sky Tower. Six chapters; each opens with its rank and the number of this layer's dungeons cleared.
 */
object RescueStory {
    const val GUARDIAN_BONUS = "Sky Guardian: expeditions find 5% more by chance"
    val chapters: List<Chapter> = listOf(
        Chapter(1, "A Pokémon Out of Place", "A Pokémon with no memory of arriving is found, and the first rescue becomes the beginning of a team.", "Basic expeditions", ExplorerRank.NORMAL, 0),
        Chapter(2, "The World Needs Helping", "The team takes more requests as the disasters grow worse.", "The mission board", ExplorerRank.BRONZE, 1),
        Chapter(3, "The Fugitive", "A rumour turns the square against the team.", "More teams", ExplorerRank.BRONZE, 2),
        Chapter(4, "Beyond the Village", "Branded as a danger, the team travels through harsh country with Absol.", "Research and treasure", ExplorerRank.SILVER, 2),
        Chapter(5, "The Sky Is Falling", "The meteor, the legendary birds and Rayquaza.", "Special expeditions", ExplorerRank.PLATINUM, 2),
        Chapter(6, "A Place to Return To", "The square learns the truth, and the team has a home.", "Story completion", ExplorerRank.PLATINUM, 3),
    )
}

/**
 * The Explorers arc: Treasure Town and the Exploration Guild, a thief of time and the hidden future. Its Guardian dungeon is the Temporal
 * Tower (Primal Dialga). Eight chapters.
 */
object ExplorersStory {
    val chapters: List<Chapter> = listOf(
        Chapter(1, "The Beach", "A ring of light drops the team on a beach beside Treasure Town.", "Basic expeditions", ExplorerRank.NORMAL, 0),
        Chapter(2, "Guild Life", "The Guild's work, its residents and the first signs that time is skipping.", "The mission board", ExplorerRank.BRONZE, 1),
        Chapter(3, "The Famous Explorer", "Dusknoir arrives, admired and unsettling.", "More teams", ExplorerRank.SILVER, 1),
        Chapter(4, "The Thief of Time", "Time gears begin to vanish.", "Research and treasure", ExplorerRank.GOLD, 2),
        Chapter(5, "The Hidden Future", "What the future is, and who has seen it.", "Special expeditions", ExplorerRank.PLATINUM, 3),
        Chapter(6, "The World's Edge", "The way to the heart of the trouble.", "Special expeditions", ExplorerRank.PLATINUM, 4),
        Chapter(7, "A Future Worth Fighting For", "The Temporal Tower and its Guardian.", "Story completion", ExplorerRank.DIAMOND, 4),
        Chapter(8, "The Morning After", "After the storm, a morning in Treasure Town.", "Story completion", ExplorerRank.DIAMOND, 5),
    )
}
