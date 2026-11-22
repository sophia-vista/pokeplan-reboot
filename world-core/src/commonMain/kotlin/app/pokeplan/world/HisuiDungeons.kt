package app.pokeplan.world

import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.Items

object HisuiDungeons {
    private val H = WorldLayer.HISUI

    val all: List<DungeonDef> = listOf(
        DungeonDef(
            "obsidian-fieldlands", "Obsidian Fieldlands", WorldRegion.GREENLANDS, floors = 8, favored = setOf(PokemonType.GRASS, PokemonType.NORMAL, PokemonType.BUG),
            baseFloorSeconds = 2880, needs = ExplorerRank.NORMAL, layer = H, chapter = 1,
            wild = listOf(WildEntry(399, 5), WildEntry(396, 5), WildEntry(403, 3), WildEntry(265, 3), WildEntry(77, 3), WildEntry(234, 3), WildEntry(133, 2, fromFloor = 3), WildEntry(412, 3), WildEntry(401, 3), WildEntry(446, 2, fromFloor = 4), WildEntry(63, 2), WildEntry(190, 2), WildEntry(100, 2, fromFloor = 3)),
            milestones = mapOf(4 to MilestoneReward(Items.berry("sweet"), 2), 8 to MilestoneReward(Items.RARE_CANDY, 2, poke = 200)),
        ),
        DungeonDef(
            "crimson-mirelands", "Crimson Mirelands", WorldRegion.AQUATIC, floors = 8, favored = setOf(PokemonType.WATER, PokemonType.POISON, PokemonType.GRASS),
            baseFloorSeconds = 2880, needs = ExplorerRank.NORMAL, layer = H, chapter = 1,
            wild = listOf(WildEntry(704, 4), WildEntry(705, 2, fromFloor = 6), WildEntry(422, 4), WildEntry(453, 3), WildEntry(54, 3), WildEntry(339, 3), WildEntry(114, 3), WildEntry(455, 3), WildEntry(418, 3), WildEntry(548, 2), WildEntry(315, 2), WildEntry(129, 3)),
            milestones = mapOf(4 to MilestoneReward(Items.berry("sour"), 2), 8 to MilestoneReward(Items.RARE_CANDY, 2, poke = 200)),
        ),
        DungeonDef(
            "cobalt-coastlands", "Cobalt Coastlands", WorldRegion.AQUATIC, floors = 10, favored = setOf(PokemonType.WATER, PokemonType.ICE, PokemonType.FLYING),
            baseFloorSeconds = 4320, needs = ExplorerRank.BRONZE, layer = H, chapter = 2,
            wild = listOf(WildEntry(129, 4), WildEntry(223, 3), WildEntry(211, 3), WildEntry(72, 4), WildEntry(456, 3), WildEntry(550, 3), WildEntry(363, 3), WildEntry(458, 2, fromFloor = 4), WildEntry(425, 3), WildEntry(441, 2), WildEntry(224, 2, fromFloor = 6), WildEntry(130, 1, fromFloor = 8)),
            milestones = mapOf(5 to MilestoneReward(Items.berry("sweet"), 3), 10 to MilestoneReward(Items.RARE_CANDY, 3, poke = 400)),
        ),
        DungeonDef(
            "celestica-ruins", "Celestica Ruins", WorldRegion.ANCIENT, floors = 12, favored = setOf(PokemonType.GHOST, PokemonType.DARK, PokemonType.PSYCHIC),
            baseFloorSeconds = 5760, needs = ExplorerRank.SILVER, layer = H, chapter = 3,
            wild = listOf(WildEntry(355, 4), WildEntry(92, 4), WildEntry(93, 2, fromFloor = 5), WildEntry(200, 3), WildEntry(198, 3), WildEntry(442, 2, fromFloor = 6), WildEntry(280, 3), WildEntry(436, 3), WildEntry(201, 3), WildEntry(408, 2), WildEntry(410, 2), WildEntry(479, 2, fromFloor = 6)),
            milestones = mapOf(6 to MilestoneReward(Items.berry("bitter"), 3), 12 to MilestoneReward(Items.RARE_CANDY, 3, poke = 600)),
        ),
        DungeonDef(
            "coronet-highlands", "Coronet Highlands", WorldRegion.MOUNTAINS, floors = 14, favored = setOf(PokemonType.ROCK, PokemonType.STEEL, PokemonType.GROUND),
            baseFloorSeconds = 7200, needs = ExplorerRank.GOLD, layer = H, chapter = 3,
            wild = listOf(WildEntry(74, 4), WildEntry(75, 2, fromFloor = 4), WildEntry(66, 3), WildEntry(95, 3), WildEntry(111, 3), WildEntry(299, 3), WildEntry(41, 3), WildEntry(207, 3), WildEntry(443, 2, fromFloor = 5), WildEntry(438, 3), WildEntry(81, 3), WildEntry(208, 1, fromFloor = 10)),
            milestones = mapOf(7 to MilestoneReward(Items.berry("spicy"), 4), 14 to MilestoneReward(Items.RARE_CANDY, 4, poke = 900)),
        ),
        DungeonDef(
            "alabaster-icelands", "Alabaster Icelands", WorldRegion.FROZEN, floors = 16, favored = setOf(PokemonType.ICE, PokemonType.ROCK, PokemonType.WATER),
            baseFloorSeconds = 9000, needs = ExplorerRank.GOLD, layer = H, chapter = 4,
            wild = listOf(WildEntry(712, 5), WildEntry(361, 4), WildEntry(459, 4), WildEntry(220, 4), WildEntry(215, 3), WildEntry(37, 3), WildEntry(58, 3), WildEntry(217, 2, fromFloor = 4), WildEntry(461, 2, fromFloor = 6), WildEntry(478, 2, fromFloor = 8), WildEntry(473, 1, fromFloor = 10), WildEntry(713, 1, fromFloor = 12)),
            milestones = mapOf(8 to MilestoneReward(Items.berry("dry"), 4), 16 to MilestoneReward(Items.RARE_CANDY, 5, poke = 1_400)),
        ),
        DungeonDef(
            "turnback-cave", "Turnback Cave", WorldRegion.UNDERGROUND, floors = 18, favored = setOf(PokemonType.GHOST, PokemonType.ROCK, PokemonType.DARK),
            baseFloorSeconds = 10800, needs = ExplorerRank.PLATINUM, layer = H, chapter = 4,
            wild = listOf(WildEntry(355, 3), WildEntry(356, 2, fromFloor = 6), WildEntry(92, 3), WildEntry(93, 2, fromFloor = 5), WildEntry(200, 3), WildEntry(429, 2, fromFloor = 8), WildEntry(442, 3), WildEntry(41, 3), WildEntry(42, 2, fromFloor = 5), WildEntry(169, 1, fromFloor = 10), WildEntry(74, 2), WildEntry(477, 1, fromFloor = 12), WildEntry(570, 2, fromFloor = 3)),
            milestones = mapOf(9 to MilestoneReward(Items.berry("spicy"), 4), 18 to MilestoneReward(Items.RARE_CANDY, 6, poke = 2_000)),
        ),
        DungeonDef(
            "hallowed-tower", "Hallowed Tower", WorldRegion.ANCIENT, floors = 18, favored = setOf(PokemonType.GHOST, PokemonType.FAIRY, PokemonType.PSYCHIC),
            baseFloorSeconds = 12000, needs = ExplorerRank.PLATINUM, layer = H, chapter = 5,
            wild = listOf(WildEntry(201, 4), WildEntry(200, 3), WildEntry(429, 2, fromFloor = 8), WildEntry(280, 3), WildEntry(281, 2, fromFloor = 5), WildEntry(282, 1, fromFloor = 9), WildEntry(358, 3), WildEntry(433, 3), WildEntry(425, 3), WildEntry(426, 2, fromFloor = 7), WildEntry(175, 3), WildEntry(176, 2, fromFloor = 6), WildEntry(468, 1, fromFloor = 12)),
            milestones = mapOf(9 to MilestoneReward(Items.berry("bitter"), 5), 18 to MilestoneReward(Items.RARE_CANDY, 6, poke = 2_500)),
        ),
        DungeonDef(
            "temple-of-sinnoh", "Temple of Sinnoh", WorldRegion.OTHERWORLDLY, floors = 20, favored = setOf(PokemonType.NORMAL, PokemonType.GHOST, PokemonType.PSYCHIC),
            baseFloorSeconds = 14400, needs = ExplorerRank.DIAMOND, layer = H, chapter = 5, guardian = true,
            wild = listOf(WildEntry(201, 4), WildEntry(355, 3), WildEntry(356, 2, fromFloor = 6), WildEntry(92, 3), WildEntry(93, 2, fromFloor = 6), WildEntry(442, 3), WildEntry(436, 3), WildEntry(437, 1, fromFloor = 10), WildEntry(200, 3), WildEntry(429, 2, fromFloor = 8), WildEntry(477, 1, fromFloor = 14), WildEntry(475, 1, fromFloor = 16)),
            milestones = mapOf(10 to MilestoneReward(Items.berry("sweet"), 5), 20 to MilestoneReward(Items.RARE_CANDY, 8, poke = 4_000)),
        ),
    )
}

object HisuiStory {
    val chapters: List<Chapter> = listOf(
        Chapter(1, "The Fieldlands", "A stranger falls out of the sky into the wild land called Hisui. The fields and the marsh are the first thing they see.", "Obsidian Fieldlands and Crimson Mirelands", ExplorerRank.NORMAL, 0),
        Chapter(2, "The Survey Corps", "The Galaxy Expedition Team wants every Pokémon in the Pokédex. The coast is next on its list.", "Cobalt Coastlands", ExplorerRank.BRONZE, 2),
        Chapter(3, "The Frenzied Lords", "The noble Pokémon have gone wild, and the old ruins and highlands hold the plates to calm them.", "Celestica Ruins and Coronet Highlands", ExplorerRank.SILVER, 3),
        Chapter(4, "Time and Space", "Dialga and Palkia are in a rage. Ice and caves lead to the foot of the rift.", "Alabaster Icelands, Turnback Cave, Dialga and Palkia", ExplorerRank.GOLD, 5),
        Chapter(5, "The Original One", "Above the Hallowed Tower the sky has torn. Beyond it, the Temple of Sinnoh waits for whoever finishes the survey.", "Hallowed Tower, the Temple of Sinnoh and Arceus", ExplorerRank.PLATINUM, 7),
    )

    const val GUARDIAN = "Arceus"
    const val GUARDIAN_BONUS = "Original One's Blessing: Hisui expeditions find treasure a little more often"
}
