package app.pokeplan.world

import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.Items

object KalosDungeons {
    private val K = WorldLayer.KALOS

    val all: List<DungeonDef> = listOf(
        DungeonDef(
            "santalune-forest", "Santalune Forest", WorldRegion.GREENLANDS, floors = 5, favored = setOf(PokemonType.BUG, PokemonType.GRASS, PokemonType.NORMAL),
            baseFloorSeconds = 1800, needs = ExplorerRank.NORMAL, layer = K, chapter = 1,
            wild = listOf(WildEntry(664, 5), WildEntry(10, 4), WildEntry(13, 4), WildEntry(412, 3), WildEntry(659, 3), WildEntry(406, 2, fromFloor = 2), WildEntry(511, 2, fromFloor = 2), WildEntry(513, 2, fromFloor = 2), WildEntry(515, 2, fromFloor = 2), WildEntry(25, 1, fromFloor = 3)),
            milestones = mapOf(3 to MilestoneReward(Items.berry("sweet"), 2), 5 to MilestoneReward(Items.RARE_CANDY, 1, poke = 100)),
        ),
        DungeonDef(
            "connecting-cave", "Connecting Cave", WorldRegion.UNDERGROUND, floors = 8, favored = setOf(PokemonType.ROCK, PokemonType.GROUND, PokemonType.NORMAL),
            baseFloorSeconds = 2880, needs = ExplorerRank.NORMAL, layer = K, chapter = 1,
            wild = listOf(WildEntry(41, 5), WildEntry(293, 4), WildEntry(290, 3), WildEntry(610, 3, fromFloor = 2), WildEntry(307, 3, fromFloor = 3), WildEntry(298, 2, fromFloor = 3), WildEntry(294, 2, fromFloor = 5), WildEntry(42, 1, fromFloor = 6)),
            milestones = mapOf(4 to MilestoneReward(Items.berry("dry"), 2), 8 to MilestoneReward(Items.RARE_CANDY, 2, poke = 200)),
        ),
        DungeonDef(
            "glittering-cave", "Glittering Cave", WorldRegion.UNDERGROUND, floors = 10, favored = setOf(PokemonType.FIGHTING, PokemonType.ROCK, PokemonType.STEEL),
            baseFloorSeconds = 4320, needs = ExplorerRank.BRONZE, layer = K, chapter = 2,
            wild = listOf(WildEntry(66, 4), WildEntry(74, 4), WildEntry(81, 3), WildEntry(524, 3), WildEntry(597, 3, fromFloor = 3), WildEntry(75, 2, fromFloor = 5), WildEntry(67, 2, fromFloor = 6), WildEntry(95, 1, fromFloor = 7)),
            milestones = mapOf(5 to MilestoneReward(Items.berry("sour"), 3), 10 to MilestoneReward(Items.RARE_CANDY, 3, poke = 400)),
        ),
        DungeonDef(
            "grande-vallee-way", "Grande Vallée Way", WorldRegion.AQUATIC, floors = 12, favored = setOf(PokemonType.WATER, PokemonType.DRAGON, PokemonType.POISON),
            baseFloorSeconds = 5760, needs = ExplorerRank.SILVER, layer = K, chapter = 3,
            wild = listOf(WildEntry(194, 4), WildEntry(270, 4), WildEntry(704, 4), WildEntry(60, 3), WildEntry(283, 3), WildEntry(183, 2, fromFloor = 3), WildEntry(339, 2, fromFloor = 4), WildEntry(705, 2, fromFloor = 6), WildEntry(195, 2, fromFloor = 6), WildEntry(340, 1, fromFloor = 9), WildEntry(706, 1, fromFloor = 10)),
            milestones = mapOf(6 to MilestoneReward(Items.berry("bitter"), 3), 12 to MilestoneReward(Items.RARE_CANDY, 3, poke = 600)),
        ),
        DungeonDef(
            "pokemon-village", "Pokémon Village", WorldRegion.ANCIENT, floors = 14, favored = setOf(PokemonType.GHOST, PokemonType.GRASS, PokemonType.DARK),
            baseFloorSeconds = 7200, needs = ExplorerRank.GOLD, layer = K, chapter = 3,
            wild = listOf(WildEntry(92, 4), WildEntry(708, 3), WildEntry(710, 3), WildEntry(607, 3), WildEntry(353, 3), WildEntry(93, 3, fromFloor = 4), WildEntry(679, 2, fromFloor = 5), WildEntry(608, 2, fromFloor = 7), WildEntry(709, 1, fromFloor = 9), WildEntry(711, 1, fromFloor = 10)),
            milestones = mapOf(7 to MilestoneReward(Items.berry("spicy"), 4), 14 to MilestoneReward(Items.RARE_CANDY, 4, poke = 900)),
        ),
        DungeonDef(
            "frost-cavern", "Frost Cavern", WorldRegion.FROZEN, floors = 16, favored = setOf(PokemonType.ICE, PokemonType.ROCK, PokemonType.STEEL),
            baseFloorSeconds = 9000, needs = ExplorerRank.GOLD, layer = K, chapter = 4,
            wild = listOf(WildEntry(220, 4), WildEntry(712, 4), WildEntry(613, 3), WildEntry(582, 3), WildEntry(459, 3), WildEntry(215, 2, fromFloor = 4), WildEntry(225, 2, fromFloor = 4), WildEntry(221, 2, fromFloor = 6), WildEntry(615, 2, fromFloor = 6), WildEntry(614, 2, fromFloor = 8), WildEntry(713, 1, fromFloor = 10), WildEntry(473, 1, fromFloor = 12)),
            milestones = mapOf(8 to MilestoneReward(Items.berry("dry"), 4), 16 to MilestoneReward(Items.RARE_CANDY, 5, poke = 1_400)),
        ),
        DungeonDef(
            "victory-road-kalos", "Victory Road (Kalos)", WorldRegion.MOUNTAINS, floors = 18, favored = setOf(PokemonType.FIGHTING, PokemonType.ROCK, PokemonType.DRAGON),
            baseFloorSeconds = 10800, needs = ExplorerRank.PLATINUM, layer = K, chapter = 4,
            wild = listOf(WildEntry(111, 3), WildEntry(95, 3), WildEntry(525, 3), WildEntry(207, 3), WildEntry(296, 3), WildEntry(303, 2, fromFloor = 5), WildEntry(443, 2, fromFloor = 6), WildEntry(227, 2, fromFloor = 8), WildEntry(112, 1, fromFloor = 12), WildEntry(297, 1, fromFloor = 10)),
            milestones = mapOf(9 to MilestoneReward(Items.berry("spicy"), 4), 18 to MilestoneReward(Items.RARE_CANDY, 6, poke = 2_000)),
        ),
        DungeonDef(
            "lysandre-labs", "Lysandre Labs", WorldRegion.UNDERGROUND, floors = 18, favored = setOf(PokemonType.DARK, PokemonType.ELECTRIC, PokemonType.POISON),
            baseFloorSeconds = 12000, needs = ExplorerRank.PLATINUM, layer = K, chapter = 5,
            wild = listOf(WildEntry(316, 3), WildEntry(228, 3), WildEntry(261, 3), WildEntry(509, 3), WildEntry(100, 3), WildEntry(81, 3), WildEntry(479, 2, fromFloor = 6), WildEntry(707, 2, fromFloor = 6), WildEntry(510, 2, fromFloor = 8), WildEntry(262, 2, fromFloor = 8), WildEntry(229, 1, fromFloor = 10)),
            milestones = mapOf(9 to MilestoneReward(Items.berry("bitter"), 5), 18 to MilestoneReward(Items.RARE_CANDY, 6, poke = 2_500)),
        ),
        DungeonDef(
            "terminus-cave", "Terminus Cave", WorldRegion.OTHERWORLDLY, floors = 20, favored = setOf(PokemonType.GROUND, PokemonType.DRAGON, PokemonType.DARK),
            baseFloorSeconds = 14400, needs = ExplorerRank.DIAMOND, layer = K, chapter = 5, guardian = true,
            wild = listOf(WildEntry(42, 3), WildEntry(95, 3), WildEntry(246, 3), WildEntry(449, 3), WildEntry(304, 3), WildEntry(247, 2, fromFloor = 8), WildEntry(450, 2, fromFloor = 8), WildEntry(330, 2, fromFloor = 10), WildEntry(305, 2, fromFloor = 10), WildEntry(248, 1, fromFloor = 14), WildEntry(306, 1, fromFloor = 16)),
            milestones = mapOf(10 to MilestoneReward(Items.berry("sweet"), 5), 20 to MilestoneReward(Items.RARE_CANDY, 8, poke = 4_000)),
        ),
    )
}

object KalosStory {
    val chapters: List<Chapter> = listOf(
        Chapter(1, "Vaniville Town", "A new explorer sets out from Vaniville Town. The forests and caves are bright, and the sky above Lumiose City is oddly red.", "Santalune Forest and Connecting Cave", ExplorerRank.NORMAL, 0),
        Chapter(2, "The Mega Evolution", "Old stones and glittering caves hide a power that makes Pokémon change for a moment. Someone else is looking for it too.", "Glittering Cave", ExplorerRank.BRONZE, 2),
        Chapter(3, "Team Flare", "A team in red suits wants a world made only for the beautiful. The Grande Vallée and the Pokémon Village hold clues.", "Grande Vallée Way and Pokémon Village", ExplorerRank.SILVER, 3),
        Chapter(4, "The Frost Cavern", "A cold road leads to the Pokémon League and to the tree of life, where Xerneas waits.", "Frost Cavern, Victory Road and Xerneas", ExplorerRank.GOLD, 5),
        Chapter(5, "The Ultimate Weapon", "Below Lysandre Labs the other legend wakes, and beneath the whole region something older keeps order.", "Lysandre Labs, Terminus Cave, Yveltal and Zygarde", ExplorerRank.PLATINUM, 7),
    )

    const val GUARDIAN = "Zygarde"
    const val GUARDIAN_BONUS = "Order of the Land: Kalos expeditions find treasure a little more often"
}
