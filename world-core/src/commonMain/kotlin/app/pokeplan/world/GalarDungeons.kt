package app.pokeplan.world

import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.Items

object GalarDungeons {
    private val G = WorldLayer.GALAR

    val all: List<DungeonDef> = listOf(
        DungeonDef(
            "wild-area", "The Wild Area", WorldRegion.GREENLANDS, floors = 8, favored = setOf(PokemonType.GRASS, PokemonType.NORMAL, PokemonType.BUG),
            baseFloorSeconds = 2880, needs = ExplorerRank.NORMAL, layer = G, chapter = 1,
            wild = listOf(WildEntry(819, 5), WildEntry(831, 5), WildEntry(821, 4), WildEntry(109, 3, fromFloor = 2), WildEntry(835, 3), WildEntry(827, 3, fromFloor = 2), WildEntry(10, 3), WildEntry(736, 2, fromFloor = 3), WildEntry(759, 2, fromFloor = 4), WildEntry(822, 1, fromFloor = 6)),
            milestones = mapOf(4 to MilestoneReward(Items.berry("sweet"), 2), 8 to MilestoneReward(Items.RARE_CANDY, 2, poke = 200)),
        ),
        DungeonDef(
            "galar-mine", "Galar Mine", WorldRegion.UNDERGROUND, floors = 8, favored = setOf(PokemonType.ROCK, PokemonType.GROUND, PokemonType.STEEL),
            baseFloorSeconds = 2880, needs = ExplorerRank.NORMAL, layer = G, chapter = 1,
            wild = listOf(WildEntry(524, 5), WildEntry(837, 5), WildEntry(532, 4), WildEntry(527, 4), WildEntry(529, 3, fromFloor = 3), WildEntry(95, 2, fromFloor = 4), WildEntry(557, 3), WildEntry(438, 2, fromFloor = 3), WildEntry(50, 3), WildEntry(525, 2, fromFloor = 6)),
            milestones = mapOf(4 to MilestoneReward(Items.berry("dry"), 2), 8 to MilestoneReward(Items.RARE_CANDY, 2, poke = 200)),
        ),
        DungeonDef(
            "glimwood-tangle", "Glimwood Tangle", WorldRegion.GREENLANDS, floors = 10, favored = setOf(PokemonType.FAIRY, PokemonType.GRASS, PokemonType.POISON),
            baseFloorSeconds = 4320, needs = ExplorerRank.BRONZE, layer = G, chapter = 2,
            wild = listOf(WildEntry(755, 5), WildEntry(856, 4), WildEntry(859, 4), WildEntry(173, 3), WildEntry(840, 3), WildEntry(590, 3), WildEntry(546, 3), WildEntry(761, 2, fromFloor = 4), WildEntry(742, 2, fromFloor = 4), WildEntry(857, 2, fromFloor = 7), WildEntry(860, 2, fromFloor = 7), WildEntry(315, 2, fromFloor = 5)),
            milestones = mapOf(5 to MilestoneReward(Items.berry("sour"), 3), 10 to MilestoneReward(Items.RARE_CANDY, 3, poke = 400)),
        ),
        DungeonDef(
            "dusty-bowl", "Dusty Bowl", WorldRegion.ANCIENT, floors = 12, favored = setOf(PokemonType.GROUND, PokemonType.GHOST, PokemonType.ROCK),
            baseFloorSeconds = 5760, needs = ExplorerRank.SILVER, layer = G, chapter = 3,
            wild = listOf(WildEntry(562, 5), WildEntry(343, 4), WildEntry(449, 3), WildEntry(328, 3), WildEntry(843, 4), WildEntry(874, 2, fromFloor = 6), WildEntry(622, 3, fromFloor = 3), WildEntry(551, 3), WildEntry(749, 3), WildEntry(450, 1, fromFloor = 9)),
            milestones = mapOf(6 to MilestoneReward(Items.berry("bitter"), 3), 12 to MilestoneReward(Items.RARE_CANDY, 3, poke = 600)),
        ),
        DungeonDef(
            "galar-mine-2", "Galar Mine No. 2", WorldRegion.UNDERGROUND, floors = 14, favored = setOf(PokemonType.STEEL, PokemonType.ROCK, PokemonType.POISON),
            baseFloorSeconds = 7200, needs = ExplorerRank.GOLD, layer = G, chapter = 3,
            wild = listOf(WildEntry(524, 4), WildEntry(525, 3, fromFloor = 3), WildEntry(837, 3), WildEntry(838, 3, fromFloor = 5), WildEntry(599, 3), WildEntry(600, 2, fromFloor = 7), WildEntry(679, 2, fromFloor = 4), WildEntry(436, 3), WildEntry(624, 3), WildEntry(533, 3, fromFloor = 5), WildEntry(532, 3), WildEntry(208, 1, fromFloor = 10)),
            milestones = mapOf(7 to MilestoneReward(Items.berry("spicy"), 4), 14 to MilestoneReward(Items.RARE_CANDY, 4, poke = 900)),
        ),
        DungeonDef(
            "route-10", "Route 10", WorldRegion.FROZEN, floors = 16, favored = setOf(PokemonType.ICE, PokemonType.STEEL, PokemonType.ROCK),
            baseFloorSeconds = 9000, needs = ExplorerRank.GOLD, layer = G, chapter = 4,
            wild = listOf(WildEntry(439, 5), WildEntry(872, 4), WildEntry(361, 4), WildEntry(712, 3), WildEntry(875, 2, fromFloor = 4), WildEntry(582, 3), WildEntry(459, 3), WildEntry(613, 3), WildEntry(215, 2, fromFloor = 5), WildEntry(220, 3), WildEntry(614, 1, fromFloor = 9), WildEntry(866, 1, fromFloor = 10), WildEntry(713, 1, fromFloor = 11)),
            milestones = mapOf(8 to MilestoneReward(Items.berry("dry"), 4), 16 to MilestoneReward(Items.RARE_CANDY, 5, poke = 1_400)),
        ),
        DungeonDef(
            "giants-seat", "Giant's Seat", WorldRegion.MOUNTAINS, floors = 18, favored = setOf(PokemonType.ROCK, PokemonType.FIGHTING, PokemonType.DRAGON),
            baseFloorSeconds = 10800, needs = ExplorerRank.PLATINUM, layer = G, chapter = 4,
            wild = listOf(WildEntry(538, 3), WildEntry(539, 3), WildEntry(66, 3), WildEntry(67, 2, fromFloor = 5), WildEntry(111, 3), WildEntry(246, 3), WildEntry(633, 2, fromFloor = 6), WildEntry(610, 2, fromFloor = 4), WildEntry(782, 2, fromFloor = 6), WildEntry(627, 2), WildEntry(629, 2), WildEntry(884, 1, fromFloor = 12)),
            milestones = mapOf(9 to MilestoneReward(Items.berry("spicy"), 4), 18 to MilestoneReward(Items.RARE_CANDY, 6, poke = 2_000)),
        ),
        DungeonDef(
            "slumbering-weald", "Slumbering Weald", WorldRegion.ANCIENT, floors = 18, favored = setOf(PokemonType.STEEL, PokemonType.GHOST, PokemonType.FAIRY),
            baseFloorSeconds = 12000, needs = ExplorerRank.PLATINUM, layer = G, chapter = 5,
            wild = listOf(WildEntry(707, 4), WildEntry(708, 4), WildEntry(710, 4), WildEntry(679, 3), WildEntry(680, 2, fromFloor = 6), WildEntry(854, 3), WildEntry(855, 2, fromFloor = 6), WildEntry(778, 2, fromFloor = 8), WildEntry(302, 2), WildEntry(607, 3), WildEntry(608, 2, fromFloor = 8), WildEntry(609, 1, fromFloor = 13)),
            milestones = mapOf(9 to MilestoneReward(Items.berry("bitter"), 5), 18 to MilestoneReward(Items.RARE_CANDY, 6, poke = 2_500)),
        ),
        DungeonDef(
            "energy-plant", "Energy Plant", WorldRegion.OTHERWORLDLY, floors = 20, favored = setOf(PokemonType.DRAGON, PokemonType.ELECTRIC, PokemonType.POISON),
            baseFloorSeconds = 14400, needs = ExplorerRank.DIAMOND, layer = G, chapter = 5, guardian = true,
            wild = listOf(WildEntry(81, 4), WildEntry(82, 3, fromFloor = 4), WildEntry(462, 1, fromFloor = 10), WildEntry(595, 3), WildEntry(596, 2, fromFloor = 6), WildEntry(848, 3), WildEntry(849, 2, fromFloor = 8), WildEntry(599, 3), WildEntry(600, 2, fromFloor = 6), WildEntry(601, 1, fromFloor = 12), WildEntry(885, 3), WildEntry(886, 2, fromFloor = 8), WildEntry(887, 1, fromFloor = 14)),
            milestones = mapOf(10 to MilestoneReward(Items.berry("sweet"), 5), 20 to MilestoneReward(Items.RARE_CANDY, 8, poke = 4_000)),
        ),
    )
}

object GalarStory {
    val chapters: List<Chapter> = listOf(
        Chapter(1, "The Wild Area", "A new trainer leaves Postwick for the Wild Area. The grass is wide and the mines are close, but the weather feels odd.", "The Wild Area and Galar Mine", ExplorerRank.NORMAL, 0),
        Chapter(2, "Team Yell", "A crowd in loud scarves cheers for one rival and blocks every path. The Glimwood Tangle hides a way round them.", "Glimwood Tangle", ExplorerRank.BRONZE, 2),
        Chapter(3, "Dynamax", "Pokémon swell to the size of buildings. The Dusty Bowl and the deep mine hold the roots of the red energy.", "Dusty Bowl and Galar Mine No. 2", ExplorerRank.SILVER, 3),
        Chapter(4, "The Sword and the Shield", "Past the snowy pass, two old heroes wait at Giant's Seat to see whether you are worthy.", "Route 10, Giant's Seat, Zacian and Zamazenta", ExplorerRank.GOLD, 5),
        Chapter(5, "The Darkest Day", "The Wishing Stars wake something that fell from the sky long ago, and the Energy Plant begins to burn.", "Slumbering Weald, the Energy Plant and Eternatus", ExplorerRank.PLATINUM, 7),
    )

    const val GUARDIAN = "Eternatus"
    const val GUARDIAN_BONUS = "Dynamax Energy: Galar expeditions find treasure a little more often"
}
