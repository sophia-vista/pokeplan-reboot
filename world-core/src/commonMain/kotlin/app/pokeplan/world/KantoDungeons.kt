package app.pokeplan.world

import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.Items

/**
 * The nine dungeons of the Kanto layer (docs/world.md, "Kanto, the first region layer"), from Pokémon Red, Blue, FireRed and LeafGreen.
 * Their wild tables are Kanto's regional dex (species 1 to 151), by each place's habitat in the games; legendaries are never found
 * (they are quests, [Legends]). Floors, floor times, milestones and the rank gates are starting values *(tune)*, checked with
 * `:world-core:simulateWorldEconomy`. [DungeonDef.chapter] is a chapter of [KantoStory].
 */
object KantoDungeons {
    private val K = WorldLayer.KANTO

    val all: List<DungeonDef> = listOf(
        DungeonDef(
            "viridian-forest", "Viridian Forest", WorldRegion.GREENLANDS, floors = 5, favored = setOf(PokemonType.BUG, PokemonType.GRASS, PokemonType.FLYING),
            baseFloorSeconds = 1800, needs = ExplorerRank.NORMAL, layer = K, chapter = 1,
            wild = listOf(WildEntry(10, 5), WildEntry(13, 5), WildEntry(16, 4), WildEntry(11, 3, fromFloor = 3), WildEntry(14, 3, fromFloor = 3), WildEntry(25, 1, fromFloor = 4)),
            milestones = mapOf(3 to MilestoneReward(Items.berry("sweet"), 2), 5 to MilestoneReward(Items.RARE_CANDY, 1, poke = 100)),
        ),
        DungeonDef(
            "mt-moon", "Mt. Moon", WorldRegion.UNDERGROUND, floors = 8, favored = setOf(PokemonType.ROCK, PokemonType.NORMAL, PokemonType.GROUND),
            baseFloorSeconds = 2880, needs = ExplorerRank.NORMAL, layer = K, chapter = 1,
            wild = listOf(WildEntry(41, 5), WildEntry(74, 4), WildEntry(46, 3, fromFloor = 2), WildEntry(35, 2, fromFloor = 3), WildEntry(39, 2, fromFloor = 4), WildEntry(75, 1, fromFloor = 7)),
            milestones = mapOf(4 to MilestoneReward(Items.berry("dry"), 2), 8 to MilestoneReward(Items.RARE_CANDY, 2, poke = 200)),
        ),
        DungeonDef(
            "rock-tunnel", "Rock Tunnel", WorldRegion.UNDERGROUND, floors = 10, favored = setOf(PokemonType.ROCK, PokemonType.GROUND, PokemonType.FIGHTING),
            baseFloorSeconds = 4320, needs = ExplorerRank.BRONZE, layer = K, chapter = 2,
            wild = listOf(WildEntry(41, 4), WildEntry(74, 4), WildEntry(66, 4), WildEntry(95, 3, fromFloor = 3), WildEntry(75, 2, fromFloor = 6), WildEntry(67, 1, fromFloor = 8)),
            milestones = mapOf(5 to MilestoneReward(Items.berry("spicy"), 2), 10 to MilestoneReward(Items.RARE_CANDY, 3, poke = 400)),
        ),
        DungeonDef(
            "pokemon-tower", "Pokémon Tower", WorldRegion.ANCIENT, floors = 12, favored = setOf(PokemonType.GHOST, PokemonType.PSYCHIC),
            baseFloorSeconds = 5400, needs = ExplorerRank.SILVER, layer = K, chapter = 3,
            wild = listOf(WildEntry(92, 5), WildEntry(104, 3), WildEntry(96, 3, fromFloor = 3), WildEntry(93, 3, fromFloor = 4), WildEntry(105, 1, fromFloor = 8), WildEntry(94, 1, fromFloor = 10)),
            milestones = mapOf(6 to MilestoneReward(Items.berry("sour"), 3), 12 to MilestoneReward(Items.RARE_CANDY, 3, poke = 700)),
        ),
        DungeonDef(
            "power-plant", "Power Plant", WorldRegion.UNDERGROUND, floors = 12, favored = setOf(PokemonType.ELECTRIC, PokemonType.STEEL),
            baseFloorSeconds = 5400, needs = ExplorerRank.SILVER, layer = K, chapter = 3,
            wild = listOf(WildEntry(100, 5), WildEntry(81, 5), WildEntry(25, 3), WildEntry(101, 2, fromFloor = 5), WildEntry(82, 2, fromFloor = 6), WildEntry(125, 1, fromFloor = 9)),
            milestones = mapOf(6 to MilestoneReward(Items.berry("dry"), 3), 12 to MilestoneReward(Items.RARE_CANDY, 3, poke = 700)),
        ),
        DungeonDef(
            "seafoam-islands", "Seafoam Islands", WorldRegion.FROZEN, floors = 14, favored = setOf(PokemonType.WATER, PokemonType.ICE),
            baseFloorSeconds = 7200, needs = ExplorerRank.GOLD, layer = K, chapter = 3,
            wild = listOf(WildEntry(86, 5), WildEntry(79, 4), WildEntry(116, 3), WildEntry(90, 3), WildEntry(41, 2), WildEntry(87, 2, fromFloor = 6), WildEntry(80, 1, fromFloor = 9), WildEntry(124, 1, fromFloor = 10)),
            milestones = mapOf(7 to MilestoneReward(Items.berry("bitter"), 3), 14 to MilestoneReward(Items.RARE_CANDY, 4, poke = 1_000)),
        ),
        DungeonDef(
            "mt-ember", "Mt. Ember", WorldRegion.MOUNTAINS, floors = 16, favored = setOf(PokemonType.FIRE, PokemonType.ROCK),
            baseFloorSeconds = 9000, needs = ExplorerRank.GOLD, layer = K, chapter = 4,
            wild = listOf(WildEntry(58, 5), WildEntry(77, 4), WildEntry(37, 3), WildEntry(109, 3), WildEntry(88, 3), WildEntry(126, 2, fromFloor = 7), WildEntry(38, 1, fromFloor = 12)),
            milestones = mapOf(8 to MilestoneReward(Items.berry("spicy"), 4), 16 to MilestoneReward(Items.RARE_CANDY, 5, poke = 1_500)),
        ),
        DungeonDef(
            "victory-road", "Victory Road", WorldRegion.MOUNTAINS, floors = 18, favored = setOf(PokemonType.FIGHTING, PokemonType.ROCK, PokemonType.DRAGON),
            baseFloorSeconds = 10800, needs = ExplorerRank.PLATINUM, layer = K, chapter = 4,
            wild = listOf(WildEntry(66, 4), WildEntry(74, 3), WildEntry(95, 3), WildEntry(42, 3), WildEntry(67, 3, fromFloor = 3), WildEntry(111, 3), WildEntry(75, 2, fromFloor = 5), WildEntry(147, 2, fromFloor = 8), WildEntry(112, 1, fromFloor = 12), WildEntry(148, 1, fromFloor = 12)),
            milestones = mapOf(9 to MilestoneReward(Items.berry("sour"), 4), 18 to MilestoneReward(Items.RARE_CANDY, 6, poke = 2_000)),
        ),
        DungeonDef(
            "cerulean-cave", "Cerulean Cave", WorldRegion.OTHERWORLDLY, floors = 20, favored = setOf(PokemonType.PSYCHIC, PokemonType.GHOST, PokemonType.DRAGON),
            baseFloorSeconds = 14400, needs = ExplorerRank.DIAMOND, layer = K, chapter = 5, guardian = true,
            wild = listOf(WildEntry(42, 4), WildEntry(64, 3), WildEntry(132, 3), WildEntry(40, 3), WildEntry(47, 3), WildEntry(97, 2, fromFloor = 5), WildEntry(85, 2, fromFloor = 6), WildEntry(112, 2, fromFloor = 8), WildEntry(113, 1, fromFloor = 10), WildEntry(130, 1, fromFloor = 12)),
            milestones = mapOf(10 to MilestoneReward(Items.berry("sweet"), 5), 20 to MilestoneReward(Items.RARE_CANDY, 8, poke = 4_000)),
        ),
    )
}

/**
 * Kanto's story, "The Genetic Crisis" (docs/regional-storylines.md): arrival, investigation, awakening, confrontation, resolution. A
 * chapter opens by rank and by how many different Kanto dungeons have been cleared, and is never lost.
 */
object KantoStory {
    val chapters: List<Chapter> = listOf(
        Chapter(1, "Pallet Town", "A new explorer sets out from Pallet Town. Pokémon in the wild behave strangely.", "Viridian Forest and Mt. Moon", ExplorerRank.NORMAL, 0),
        Chapter(2, "Rocket Shadows", "Team Rocket's traces turn up in the caves, and the people say the experiments were never stopped.", "Rock Tunnel", ExplorerRank.BRONZE, 2),
        Chapter(3, "Spirits and Birds", "Pokémon Tower mourns, the Power Plant hums and the Seafoam caves chill. The legendary birds stir.", "Pokémon Tower, Power Plant, Seafoam Islands and the bird quests", ExplorerRank.SILVER, 3),
        Chapter(4, "The League Road", "Mt. Ember burns and Victory Road waits: the road to the Pokémon League, and to the truth about the experiments.", "Mt. Ember and Victory Road", ExplorerRank.GOLD, 5),
        Chapter(5, "The Ultimate Pokémon", "Beyond Cerulean City, a cave holds what Rocket made from Mew.", "Cerulean Cave, Mewtwo and Mew", ExplorerRank.PLATINUM, 8),
    )

    /** The Guardian: Mewtwo, found at the end of Cerulean Cave. */
    const val GUARDIAN = "Mewtwo"
    const val GUARDIAN_BONUS = "Psychic Mastery: Kanto expeditions find Pokémon a little more often"
}
