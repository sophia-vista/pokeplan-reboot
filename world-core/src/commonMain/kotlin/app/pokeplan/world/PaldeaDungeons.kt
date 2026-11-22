package app.pokeplan.world

import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.Items

object PaldeaDungeons {
    private val P = WorldLayer.PALDEA

    val all: List<DungeonDef> = listOf(
        DungeonDef(
            "tagtree-thicket", "Tagtree Thicket", WorldRegion.GREENLANDS, floors = 8, favored = setOf(PokemonType.GRASS, PokemonType.BUG, PokemonType.FIGHTING),
            baseFloorSeconds = 2880, needs = ExplorerRank.NORMAL, layer = P, chapter = 1,
            wild = listOf(WildEntry(56, 5), WildEntry(919, 4), WildEntry(917, 4), WildEntry(921, 3), WildEntry(915, 3), WildEntry(187, 3), WildEntry(540, 3), WildEntry(548, 2, fromFloor = 3), WildEntry(396, 3), WildEntry(928, 2, fromFloor = 4), WildEntry(57, 2, fromFloor = 5)),
            milestones = mapOf(4 to MilestoneReward(Items.GIMMIGHOUL_COIN, 25), 8 to MilestoneReward(Items.RARE_CANDY, 2, poke = 200)),
        ),
        DungeonDef(
            "alfornada-cavern", "Alfornada Cavern", WorldRegion.UNDERGROUND, floors = 8, favored = setOf(PokemonType.ROCK, PokemonType.GROUND, PokemonType.FAIRY),
            baseFloorSeconds = 2880, needs = ExplorerRank.NORMAL, layer = P, chapter = 1,
            wild = listOf(WildEntry(74, 5), WildEntry(75, 2, fromFloor = 4), WildEntry(438, 3), WildEntry(744, 3), WildEntry(837, 3), WildEntry(969, 3), WildEntry(622, 3), WildEntry(299, 3), WildEntry(932, 3, fromFloor = 2), WildEntry(50, 2, fromFloor = 3)),
            milestones = mapOf(4 to MilestoneReward(Items.berry("dry"), 2), 8 to MilestoneReward(Items.GIMMIGHOUL_COIN, 30, poke = 200)),
        ),
        DungeonDef(
            "dalizapa-passage", "Dalizapa Passage", WorldRegion.UNDERGROUND, floors = 10, favored = setOf(PokemonType.STEEL, PokemonType.DARK, PokemonType.ROCK),
            baseFloorSeconds = 4320, needs = ExplorerRank.BRONZE, layer = P, chapter = 2,
            wild = listOf(WildEntry(624, 5), WildEntry(625, 2, fromFloor = 6), WildEntry(198, 3), WildEntry(430, 2, fromFloor = 5), WildEntry(215, 3), WildEntry(629, 3), WildEntry(246, 3), WildEntry(957, 3), WildEntry(958, 2, fromFloor = 6), WildEntry(374, 2, fromFloor = 4)),
            milestones = mapOf(5 to MilestoneReward(Items.GIMMIGHOUL_COIN, 40), 10 to MilestoneReward(Items.RARE_CANDY, 3, poke = 400)),
        ),
        DungeonDef(
            "casseroya-lake", "Casseroya Lake", WorldRegion.AQUATIC, floors = 12, favored = setOf(PokemonType.WATER, PokemonType.ELECTRIC, PokemonType.GROUND),
            baseFloorSeconds = 5760, needs = ExplorerRank.SILVER, layer = P, chapter = 3,
            wild = listOf(WildEntry(833, 4), WildEntry(183, 4), WildEntry(194, 4), WildEntry(54, 3), WildEntry(963, 3), WildEntry(960, 3), WildEntry(339, 3), WildEntry(418, 3), WildEntry(580, 3), WildEntry(602, 2, fromFloor = 4), WildEntry(978, 2), WildEntry(977, 1, fromFloor = 10)),
            milestones = mapOf(6 to MilestoneReward(Items.GIMMIGHOUL_COIN, 50), 12 to MilestoneReward(Items.RARE_CANDY, 3, poke = 600)),
        ),
        DungeonDef(
            "asado-desert", "Asado Desert", WorldRegion.GREENLANDS, floors = 14, favored = setOf(PokemonType.GROUND, PokemonType.ROCK, PokemonType.FIRE),
            baseFloorSeconds = 7200, needs = ExplorerRank.GOLD, layer = P, chapter = 3,
            wild = listOf(WildEntry(551, 4), WildEntry(843, 4), WildEntry(331, 4), WildEntry(449, 3), WildEntry(328, 3), WildEntry(335, 2), WildEntry(336, 2), WildEntry(324, 2, fromFloor = 5), WildEntry(322, 3), WildEntry(951, 2, fromFloor = 3), WildEntry(552, 2, fromFloor = 7), WildEntry(749, 2), WildEntry(450, 1, fromFloor = 10), WildEntry(330, 1, fromFloor = 12)),
            milestones = mapOf(7 to MilestoneReward(Items.GIMMIGHOUL_COIN, 60), 14 to MilestoneReward(Items.RARE_CANDY, 4, poke = 900)),
        ),
        DungeonDef(
            "glaseado-mountain", "Glaseado Mountain", WorldRegion.FROZEN, floors = 16, favored = setOf(PokemonType.ICE, PokemonType.ROCK, PokemonType.DRAGON),
            baseFloorSeconds = 9000, needs = ExplorerRank.GOLD, layer = P, chapter = 4,
            wild = listOf(WildEntry(974, 4), WildEntry(872, 4), WildEntry(361, 4), WildEntry(712, 4), WildEntry(613, 3), WildEntry(225, 3), WildEntry(220, 3), WildEntry(459, 3), WildEntry(215, 2, fromFloor = 5), WildEntry(996, 2, fromFloor = 6), WildEntry(975, 2, fromFloor = 9), WildEntry(614, 2, fromFloor = 8), WildEntry(713, 1, fromFloor = 10), WildEntry(473, 1, fromFloor = 12)),
            milestones = mapOf(8 to MilestoneReward(Items.GIMMIGHOUL_COIN, 75), 16 to MilestoneReward(Items.RARE_CANDY, 5, poke = 1_400)),
        ),
        DungeonDef(
            "socarrat-trail", "Socarrat Trail", WorldRegion.MOUNTAINS, floors = 18, favored = setOf(PokemonType.FIGHTING, PokemonType.DRAGON, PokemonType.ROCK),
            baseFloorSeconds = 10800, needs = ExplorerRank.PLATINUM, layer = P, chapter = 4,
            wild = listOf(WildEntry(610, 3), WildEntry(611, 2, fromFloor = 6), WildEntry(633, 3), WildEntry(634, 2, fromFloor = 8), WildEntry(627, 3), WildEntry(628, 2, fromFloor = 9), WildEntry(307, 3), WildEntry(308, 2, fromFloor = 7), WildEntry(447, 3), WildEntry(701, 2), WildEntry(371, 3), WildEntry(372, 1, fromFloor = 10), WildEntry(111, 3)),
            milestones = mapOf(9 to MilestoneReward(Items.GIMMIGHOUL_COIN, 90), 18 to MilestoneReward(Items.RARE_CANDY, 6, poke = 2_000)),
        ),
        DungeonDef(
            "zero-lab", "Zero Lab", WorldRegion.ANCIENT, floors = 18, favored = setOf(PokemonType.STEEL, PokemonType.ELECTRIC, PokemonType.PSYCHIC),
            baseFloorSeconds = 12000, needs = ExplorerRank.PLATINUM, layer = P, chapter = 5,
            wild = listOf(WildEntry(137, 3), WildEntry(233, 2, fromFloor = 6), WildEntry(81, 4), WildEntry(82, 3, fromFloor = 5), WildEntry(374, 3), WildEntry(375, 2, fromFloor = 7), WildEntry(100, 3), WildEntry(101, 2, fromFloor = 6), WildEntry(479, 2), WildEntry(622, 3), WildEntry(623, 2, fromFloor = 8), WildEntry(774, 2), WildEntry(965, 3), WildEntry(966, 1, fromFloor = 8)),
            milestones = mapOf(9 to MilestoneReward(Items.GIMMIGHOUL_COIN, 100), 18 to MilestoneReward(Items.RARE_CANDY, 6, poke = 2_500)),
        ),
        DungeonDef(
            "area-zero", "Area Zero", WorldRegion.OTHERWORLDLY, floors = 20, favored = setOf(PokemonType.DRAGON, PokemonType.PSYCHIC, PokemonType.GHOST),
            baseFloorSeconds = 14400, needs = ExplorerRank.DIAMOND, layer = P, chapter = 5, guardian = true,
            wild = listOf(WildEntry(969, 4), WildEntry(970, 2, fromFloor = 8), WildEntry(885, 3), WildEntry(886, 2, fromFloor = 8), WildEntry(887, 1, fromFloor = 14), WildEntry(996, 3), WildEntry(997, 2, fromFloor = 10), WildEntry(999, 3), WildEntry(442, 2, fromFloor = 6), WildEntry(971, 3), WildEntry(972, 2, fromFloor = 8), WildEntry(856, 3), WildEntry(302, 2)),
            milestones = mapOf(10 to MilestoneReward(Items.GIMMIGHOUL_COIN, 80), 20 to MilestoneReward(Items.GIMMIGHOUL_COIN, 120, poke = 4_000)),
        ),
    )
}

object PaldeaStory {
    val chapters: List<Chapter> = listOf(
        Chapter(1, "Mesagoza", "A new student leaves the academy to find a treasure of their own. The thickets and caves around Mesagoza are only the start.", "Tagtree Thicket and Alfornada Cavern", ExplorerRank.NORMAL, 0),
        Chapter(2, "Team Star", "A school gang in tough jackets guards its bases with a smoke of loud bangs. The Dalizapa Passage runs under all of them.", "Dalizapa Passage", ExplorerRank.BRONZE, 2),
        Chapter(3, "Lake and Desert", "Casseroya Lake glitters and the Asado Desert burns. Both have stories of a monster in the Great Crater.", "Casseroya Lake and Asado Desert", ExplorerRank.SILVER, 3),
        Chapter(4, "The Edge of the Crater", "The mountains are cold, the trails are steep, and something old runs beside you.", "Glaseado Mountain, Socarrat Trail and Koraidon", ExplorerRank.GOLD, 5),
        Chapter(5, "Area Zero", "At the bottom of the Great Crater, the lab and its machine wait for someone to finish what was started.", "Zero Lab, Area Zero and Terapagos", ExplorerRank.PLATINUM, 7),
    )

    const val GUARDIAN = "Terapagos"
    const val GUARDIAN_BONUS = "Terastal Treasure: Paldea expeditions find treasure a little more often"
}
