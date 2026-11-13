package app.pokeplan.world

import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.Items

/**
 * The nine dungeons of the Hoenn layer (docs/pokelife/MODULES/WORLD.md section 14, "Hoenn"), from Pokémon Ruby, Sapphire, Emerald, Omega Ruby and Alpha Sapphire.
 * Their wild tables are the Hoenn regional dex, by each place's habitat in the games; legendaries are never found (they are quests,
 * [Legends]). Floors, floor times, milestones and the rank gates are starting values *(tune)*. [DungeonDef.chapter] is a chapter of
 * [HoennStory].
 */
object HoennDungeons {
    private val H = WorldLayer.HOENN

    val all: List<DungeonDef> = listOf(
        DungeonDef(
            "petalburg-woods", "Petalburg Woods", WorldRegion.GREENLANDS, floors = 5, favored = setOf(PokemonType.BUG, PokemonType.GRASS, PokemonType.NORMAL),
            baseFloorSeconds = 1800, needs = ExplorerRank.NORMAL, layer = H, chapter = 1,
            wild = listOf(WildEntry(265, 5), WildEntry(276, 5), WildEntry(285, 4), WildEntry(287, 3), WildEntry(266, 2, fromFloor = 3), WildEntry(268, 2, fromFloor = 3), WildEntry(283, 1, fromFloor = 4)),
            milestones = mapOf(3 to MilestoneReward(Items.berry("sweet"), 2), 5 to MilestoneReward(Items.RARE_CANDY, 1, poke = 100)),
        ),
        DungeonDef(
            "granite-cave", "Granite Cave", WorldRegion.UNDERGROUND, floors = 8, favored = setOf(PokemonType.ROCK, PokemonType.FIGHTING, PokemonType.DARK),
            baseFloorSeconds = 2880, needs = ExplorerRank.NORMAL, layer = H, chapter = 1,
            wild = listOf(WildEntry(41, 5), WildEntry(296, 4), WildEntry(304, 4), WildEntry(63, 3, fromFloor = 2), WildEntry(74, 3), WildEntry(302, 2, fromFloor = 4), WildEntry(305, 1, fromFloor = 7)),
            milestones = mapOf(4 to MilestoneReward(Items.berry("dry"), 2), 8 to MilestoneReward(Items.RARE_CANDY, 2, poke = 200)),
        ),
        DungeonDef(
            "meteor-falls", "Meteor Falls", WorldRegion.UNDERGROUND, floors = 10, favored = setOf(PokemonType.ROCK, PokemonType.DRAGON, PokemonType.WATER),
            baseFloorSeconds = 4320, needs = ExplorerRank.BRONZE, layer = H, chapter = 2,
            wild = listOf(WildEntry(41, 4), WildEntry(337, 3), WildEntry(338, 3), WildEntry(339, 3), WildEntry(42, 3, fromFloor = 3), WildEntry(371, 2, fromFloor = 5), WildEntry(340, 1, fromFloor = 8)),
            milestones = mapOf(5 to MilestoneReward(Items.berry("spicy"), 2), 10 to MilestoneReward(Items.RARE_CANDY, 3, poke = 400)),
        ),
        DungeonDef(
            "mt-chimney", "Mt. Chimney", WorldRegion.MOUNTAINS, floors = 12, favored = setOf(PokemonType.FIRE, PokemonType.GROUND, PokemonType.ROCK),
            baseFloorSeconds = 5400, needs = ExplorerRank.SILVER, layer = H, chapter = 3,
            wild = listOf(WildEntry(322, 5), WildEntry(218, 4), WildEntry(66, 3), WildEntry(41, 3), WildEntry(324, 2, fromFloor = 5), WildEntry(323, 1, fromFloor = 9)),
            milestones = mapOf(6 to MilestoneReward(Items.berry("spicy"), 3), 12 to MilestoneReward(Items.RARE_CANDY, 3, poke = 700)),
        ),
        DungeonDef(
            "shoal-cave", "Shoal Cave", WorldRegion.FROZEN, floors = 12, favored = setOf(PokemonType.WATER, PokemonType.ICE, PokemonType.GHOST),
            baseFloorSeconds = 5400, needs = ExplorerRank.SILVER, layer = H, chapter = 3,
            wild = listOf(WildEntry(41, 4), WildEntry(363, 4), WildEntry(361, 4), WildEntry(278, 3), WildEntry(42, 2, fromFloor = 4), WildEntry(364, 2, fromFloor = 7), WildEntry(362, 1, fromFloor = 10)),
            milestones = mapOf(6 to MilestoneReward(Items.berry("bitter"), 3), 12 to MilestoneReward(Items.RARE_CANDY, 3, poke = 700)),
        ),
        DungeonDef(
            "seafloor-cavern", "Seafloor Cavern", WorldRegion.AQUATIC, floors = 14, favored = setOf(PokemonType.WATER, PokemonType.DARK, PokemonType.POISON),
            baseFloorSeconds = 7200, needs = ExplorerRank.GOLD, layer = H, chapter = 4,
            wild = listOf(WildEntry(41, 4), WildEntry(72, 4), WildEntry(320, 3), WildEntry(318, 3), WildEntry(42, 3, fromFloor = 4), WildEntry(366, 2, fromFloor = 6), WildEntry(369, 1, fromFloor = 9), WildEntry(321, 1, fromFloor = 11)),
            milestones = mapOf(7 to MilestoneReward(Items.berry("sour"), 3), 14 to MilestoneReward(Items.RARE_CANDY, 4, poke = 1_000)),
        ),
        DungeonDef(
            "cave-of-origin", "Cave of Origin", WorldRegion.ANCIENT, floors = 16, favored = setOf(PokemonType.WATER, PokemonType.GROUND, PokemonType.PSYCHIC),
            baseFloorSeconds = 9000, needs = ExplorerRank.GOLD, layer = H, chapter = 4,
            wild = listOf(WildEntry(41, 4), WildEntry(42, 3), WildEntry(344, 3), WildEntry(343, 3), WildEntry(320, 3), WildEntry(371, 2, fromFloor = 6), WildEntry(337, 1, fromFloor = 10), WildEntry(348, 1, fromFloor = 12)),
            milestones = mapOf(8 to MilestoneReward(Items.berry("sweet"), 4), 16 to MilestoneReward(Items.RARE_CANDY, 5, poke = 1_500)),
        ),
        DungeonDef(
            "victory-road-hoenn", "Victory Road (Hoenn)", WorldRegion.MOUNTAINS, floors = 18, favored = setOf(PokemonType.FIGHTING, PokemonType.ROCK, PokemonType.DRAGON),
            baseFloorSeconds = 10800, needs = ExplorerRank.PLATINUM, layer = H, chapter = 4,
            wild = listOf(WildEntry(42, 4), WildEntry(296, 3), WildEntry(297, 2, fromFloor = 5), WildEntry(304, 3), WildEntry(305, 2, fromFloor = 6), WildEntry(302, 2), WildEntry(371, 2, fromFloor = 6), WildEntry(372, 1, fromFloor = 12), WildEntry(303, 1, fromFloor = 9)),
            milestones = mapOf(9 to MilestoneReward(Items.berry("spicy"), 4), 18 to MilestoneReward(Items.RARE_CANDY, 6, poke = 2_000)),
        ),
        DungeonDef(
            "sky-pillar", "Sky Pillar", WorldRegion.SKY, floors = 20, favored = setOf(PokemonType.FLYING, PokemonType.DRAGON, PokemonType.PSYCHIC),
            baseFloorSeconds = 14400, needs = ExplorerRank.DIAMOND, layer = H, chapter = 5, guardian = true,
            wild = listOf(WildEntry(276, 3), WildEntry(277, 3, fromFloor = 4), WildEntry(333, 3), WildEntry(334, 2, fromFloor = 8), WildEntry(371, 3), WildEntry(372, 2, fromFloor = 8), WildEntry(344, 2, fromFloor = 10), WildEntry(375, 1, fromFloor = 14), WildEntry(373, 1, fromFloor = 16)),
            milestones = mapOf(10 to MilestoneReward(Items.berry("bitter"), 5), 20 to MilestoneReward(Items.RARE_CANDY, 8, poke = 4_000)),
        ),
    )
}

/**
 * Hoenn's story, "The Primal Awakening" (docs/regional-storylines.md): Team Magma and Team Aqua, the weather and the three ancient
 * Pokémon. A chapter opens by rank and by how many different Hoenn dungeons have been cleared, and is never lost.
 */
object HoennStory {
    val chapters: List<Chapter> = listOf(
        Chapter(1, "Littleroot Town", "A new explorer sets out from Littleroot Town. Pokémon in the woods and caves behave strangely, and the weather is odd.", "Petalburg Woods and Granite Cave", ExplorerRank.NORMAL, 0),
        Chapter(2, "Signs in the Stone", "Meteor Falls and the old inscriptions speak of a conflict between the land and the sea.", "Meteor Falls", ExplorerRank.BRONZE, 2),
        Chapter(3, "Magma and Aqua", "Two teams want to remake the world: one to enlarge the land, one the sea. Mt. Chimney and Shoal Cave hold clues.", "Mt. Chimney, Shoal Cave and Groudon", ExplorerRank.SILVER, 3),
        Chapter(4, "The Weather Wars", "The sea and the land clash over Seafloor Cavern, the Cave of Origin and the road to the League.", "Seafloor Cavern, Cave of Origin, Victory Road and Kyogre", ExplorerRank.GOLD, 5),
        Chapter(5, "The Sky Pillar", "Only one Pokémon can end the fighting between land and sea, and it waits at the top of the Sky Pillar.", "Sky Pillar and Rayquaza", ExplorerRank.PLATINUM, 8),
    )

    /** The Guardian: Rayquaza, at the top of the Sky Pillar. */
    const val GUARDIAN = "Rayquaza"
    const val GUARDIAN_BONUS = "Delta Stream: Hoenn expeditions find Pokémon a little more often"
}
