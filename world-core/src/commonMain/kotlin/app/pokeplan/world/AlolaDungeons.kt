package app.pokeplan.world

import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.Items

object AlolaDungeons {
    private val A = WorldLayer.ALOLA

    val all: List<DungeonDef> = listOf(
        DungeonDef(
            "verdant-cavern", "Verdant Cavern", WorldRegion.UNDERGROUND, floors = 5, favored = setOf(PokemonType.NORMAL, PokemonType.GROUND, PokemonType.POISON),
            baseFloorSeconds = 1800, needs = ExplorerRank.NORMAL, layer = A, chapter = 1,
            wild = listOf(WildEntry(734, 5), WildEntry(19, 4), WildEntry(41, 4), WildEntry(52, 3), WildEntry(50, 3, fromFloor = 2), WildEntry(731, 2, fromFloor = 2), WildEntry(10, 2, fromFloor = 3), WildEntry(735, 1, fromFloor = 4), WildEntry(20, 2, fromFloor = 3), WildEntry(88, 2, fromFloor = 4)),
            milestones = mapOf(3 to MilestoneReward(Items.berry("sweet"), 2), 5 to MilestoneReward(Items.RARE_CANDY, 1, poke = 100)),
        ),
        DungeonDef(
            "brooklet-hill", "Brooklet Hill", WorldRegion.AQUATIC, floors = 8, favored = setOf(PokemonType.WATER, PokemonType.BUG, PokemonType.GRASS),
            baseFloorSeconds = 2880, needs = ExplorerRank.NORMAL, layer = A, chapter = 1,
            wild = listOf(WildEntry(60, 4), WildEntry(54, 4), WildEntry(751, 4), WildEntry(283, 3), WildEntry(746, 3, fromFloor = 2), WildEntry(755, 2, fromFloor = 3), WildEntry(747, 2, fromFloor = 4), WildEntry(61, 2, fromFloor = 5), WildEntry(752, 1, fromFloor = 7)),
            milestones = mapOf(4 to MilestoneReward(Items.berry("dry"), 2), 8 to MilestoneReward(Items.RARE_CANDY, 2, poke = 200)),
        ),
        DungeonDef(
            "lush-jungle", "Lush Jungle", WorldRegion.GREENLANDS, floors = 10, favored = setOf(PokemonType.GRASS, PokemonType.BUG, PokemonType.FAIRY),
            baseFloorSeconds = 4320, needs = ExplorerRank.BRONZE, layer = A, chapter = 2,
            wild = listOf(WildEntry(753, 5), WildEntry(761, 4), WildEntry(742, 4), WildEntry(46, 3, fromFloor = 2), WildEntry(732, 3), WildEntry(755, 3), WildEntry(56, 2, fromFloor = 3), WildEntry(762, 2, fromFloor = 5), WildEntry(756, 2, fromFloor = 6), WildEntry(754, 1, fromFloor = 8)),
            milestones = mapOf(5 to MilestoneReward(Items.berry("sour"), 3), 10 to MilestoneReward(Items.RARE_CANDY, 3, poke = 400)),
        ),
        DungeonDef(
            "wela-volcano-park", "Wela Volcano Park", WorldRegion.MOUNTAINS, floors = 12, favored = setOf(PokemonType.FIRE, PokemonType.ROCK, PokemonType.GROUND),
            baseFloorSeconds = 5760, needs = ExplorerRank.SILVER, layer = A, chapter = 3,
            wild = listOf(WildEntry(104, 5), WildEntry(757, 4), WildEntry(744, 3), WildEntry(58, 3, fromFloor = 2), WildEntry(240, 3, fromFloor = 3), WildEntry(105, 2, fromFloor = 6), WildEntry(758, 2, fromFloor = 7), WildEntry(126, 1, fromFloor = 9)),
            milestones = mapOf(6 to MilestoneReward(Items.berry("bitter"), 3), 12 to MilestoneReward(Items.RARE_CANDY, 3, poke = 600)),
        ),
        DungeonDef(
            "vast-poni-canyon", "Vast Poni Canyon", WorldRegion.UNDERGROUND, floors = 14, favored = setOf(PokemonType.DRAGON, PokemonType.ROCK, PokemonType.GROUND),
            baseFloorSeconds = 7200, needs = ExplorerRank.GOLD, layer = A, chapter = 3,
            wild = listOf(WildEntry(782, 4), WildEntry(749, 3), WildEntry(74, 3), WildEntry(66, 3), WildEntry(75, 2, fromFloor = 5), WildEntry(783, 2, fromFloor = 6), WildEntry(750, 2, fromFloor = 7), WildEntry(67, 2, fromFloor = 8), WildEntry(784, 1, fromFloor = 12)),
            milestones = mapOf(7 to MilestoneReward(Items.berry("spicy"), 4), 14 to MilestoneReward(Items.RARE_CANDY, 4, poke = 900)),
        ),
        DungeonDef(
            "mount-lanakila", "Mount Lanakila", WorldRegion.FROZEN, floors = 16, favored = setOf(PokemonType.ICE, PokemonType.ROCK, PokemonType.FIGHTING),
            baseFloorSeconds = 9000, needs = ExplorerRank.GOLD, layer = A, chapter = 4,
            wild = listOf(WildEntry(361, 4), WildEntry(582, 3), WildEntry(215, 3), WildEntry(739, 3), WildEntry(225, 2), WildEntry(359, 2, fromFloor = 5), WildEntry(583, 2, fromFloor = 6), WildEntry(740, 2, fromFloor = 8), WildEntry(362, 2, fromFloor = 9), WildEntry(461, 2, fromFloor = 10), WildEntry(478, 1, fromFloor = 12), WildEntry(27, 2, fromFloor = 5), WildEntry(37, 2, fromFloor = 5)),
            milestones = mapOf(8 to MilestoneReward(Items.berry("dry"), 4), 16 to MilestoneReward(Items.RARE_CANDY, 5, poke = 1_400)),
        ),
        DungeonDef(
            "ancient-poni-path", "Ancient Poni Path", WorldRegion.ANCIENT, floors = 18, favored = setOf(PokemonType.ROCK, PokemonType.GHOST, PokemonType.PSYCHIC),
            baseFloorSeconds = 10800, needs = ExplorerRank.PLATINUM, layer = A, chapter = 4,
            wild = listOf(WildEntry(177, 3), WildEntry(302, 3), WildEntry(303, 3), WildEntry(353, 3), WildEntry(198, 3), WildEntry(299, 3), WildEntry(200, 2, fromFloor = 5), WildEntry(178, 2, fromFloor = 6), WildEntry(354, 2, fromFloor = 8), WildEntry(476, 1, fromFloor = 12)),
            milestones = mapOf(9 to MilestoneReward(Items.berry("spicy"), 4), 18 to MilestoneReward(Items.RARE_CANDY, 6, poke = 2_000)),
        ),
        DungeonDef(
            "aether-paradise", "Aether Paradise", WorldRegion.SKY, floors = 18, favored = setOf(PokemonType.PSYCHIC, PokemonType.STEEL, PokemonType.ELECTRIC),
            baseFloorSeconds = 12000, needs = ExplorerRank.PLATINUM, layer = A, chapter = 5,
            wild = listOf(WildEntry(137, 3), WildEntry(81, 3), WildEntry(63, 3), WildEntry(605, 3), WildEntry(374, 3), WildEntry(233, 2, fromFloor = 6), WildEntry(82, 2, fromFloor = 6), WildEntry(64, 2, fromFloor = 7), WildEntry(375, 2, fromFloor = 8), WildEntry(606, 1, fromFloor = 10), WildEntry(462, 1, fromFloor = 12)),
            milestones = mapOf(9 to MilestoneReward(Items.berry("bitter"), 5), 18 to MilestoneReward(Items.RARE_CANDY, 6, poke = 2_500)),
        ),
        DungeonDef(
            "ultra-megalopolis", "Ultra Megalopolis", WorldRegion.OTHERWORLDLY, floors = 20, favored = setOf(PokemonType.DRAGON, PokemonType.PSYCHIC, PokemonType.DARK),
            baseFloorSeconds = 14400, needs = ExplorerRank.DIAMOND, layer = A, chapter = 5, guardian = true,
            wild = listOf(WildEntry(622, 3), WildEntry(147, 3), WildEntry(371, 3), WildEntry(778, 3), WildEntry(623, 2, fromFloor = 6), WildEntry(148, 2, fromFloor = 6), WildEntry(372, 2, fromFloor = 8), WildEntry(782, 2, fromFloor = 10), WildEntry(149, 1, fromFloor = 14), WildEntry(373, 1, fromFloor = 14)),
            milestones = mapOf(10 to MilestoneReward(Items.berry("sweet"), 5), 20 to MilestoneReward(Items.RARE_CANDY, 8, poke = 4_000)),
        ),
    )
}

object AlolaStory {
    val chapters: List<Chapter> = listOf(
        Chapter(1, "Iki Town", "A new explorer sets out from Iki Town to take the island challenge. A cavern and a stream on Melemele Island are the first trials.", "Verdant Cavern and Brooklet Hill", ExplorerRank.NORMAL, 0),
        Chapter(2, "The Lush Jungle", "The trials of Akala Island lead into a green jungle where something strange hums in the leaves.", "Lush Jungle", ExplorerRank.BRONZE, 2),
        Chapter(3, "Team Skull", "A team in black hoods plays loud music and takes things that are not theirs. Wela Volcano Park and Vast Poni Canyon are on their route.", "Wela Volcano Park and Vast Poni Canyon", ExplorerRank.SILVER, 3),
        Chapter(4, "The Mountain of the Sun", "On the highest peak of Alola the sun and the moon wait for a trainer who can hear them.", "Mount Lanakila, Ancient Poni Path and Solgaleo", ExplorerRank.GOLD, 5),
        Chapter(5, "The Aether Paradise", "The foundation that protects Pokémon hides something in the sky, and beyond it a city of ultra light waits.", "Aether Paradise, Ultra Megalopolis, Lunala and Necrozma", ExplorerRank.PLATINUM, 7),
    )

    const val GUARDIAN = "Necrozma"
    const val GUARDIAN_BONUS = "Prism Light: Alola expeditions find treasure a little more often"
}
