package app.pokeplan.world

import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.Items

object UnovaDungeons {
    private val U = WorldLayer.UNOVA

    val all: List<DungeonDef> = listOf(
        DungeonDef(
            "pinwheel-forest", "Pinwheel Forest", WorldRegion.GREENLANDS, floors = 5, favored = setOf(PokemonType.BUG, PokemonType.GRASS, PokemonType.NORMAL),
            baseFloorSeconds = 1800, needs = ExplorerRank.NORMAL, layer = U, chapter = 1,
            wild = listOf(WildEntry(540, 5), WildEntry(543, 4), WildEntry(519, 4), WildEntry(504, 3), WildEntry(585, 2, fromFloor = 2), WildEntry(532, 2, fromFloor = 2), WildEntry(590, 2, fromFloor = 3), WildEntry(588, 2, fromFloor = 3), WildEntry(538, 1, fromFloor = 4), WildEntry(539, 1, fromFloor = 4)),
            milestones = mapOf(3 to MilestoneReward(Items.berry("sweet"), 2), 5 to MilestoneReward(Items.RARE_CANDY, 1, poke = 100)),
        ),
        DungeonDef(
            "wellspring-cave", "Wellspring Cave", WorldRegion.UNDERGROUND, floors = 8, favored = setOf(PokemonType.WATER, PokemonType.ROCK, PokemonType.GROUND),
            baseFloorSeconds = 2880, needs = ExplorerRank.NORMAL, layer = U, chapter = 1,
            wild = listOf(WildEntry(41, 5), WildEntry(527, 5), WildEntry(524, 4), WildEntry(183, 3, fromFloor = 3), WildEntry(535, 3, fromFloor = 3), WildEntry(529, 3, fromFloor = 4), WildEntry(54, 2, fromFloor = 2), WildEntry(532, 2, fromFloor = 3), WildEntry(95, 1, fromFloor = 6)),
            milestones = mapOf(4 to MilestoneReward(Items.berry("dry"), 2), 8 to MilestoneReward(Items.RARE_CANDY, 2, poke = 200)),
        ),
        DungeonDef(
            "relic-castle", "Relic Castle", WorldRegion.ANCIENT, floors = 10, favored = setOf(PokemonType.GROUND, PokemonType.GHOST, PokemonType.ROCK),
            baseFloorSeconds = 4320, needs = ExplorerRank.BRONZE, layer = U, chapter = 2,
            wild = listOf(WildEntry(551, 5), WildEntry(27, 4), WildEntry(554, 3), WildEntry(556, 3), WildEntry(557, 3), WildEntry(559, 2), WildEntry(328, 3, fromFloor = 3), WildEntry(343, 3, fromFloor = 4), WildEntry(562, 3, fromFloor = 3), WildEntry(561, 2, fromFloor = 5), WildEntry(564, 2, fromFloor = 6), WildEntry(566, 2, fromFloor = 6)),
            milestones = mapOf(5 to MilestoneReward(Items.berry("sour"), 3), 10 to MilestoneReward(Items.RARE_CANDY, 3, poke = 400)),
        ),
        DungeonDef(
            "chargestone-cave", "Chargestone Cave", WorldRegion.UNDERGROUND, floors = 12, favored = setOf(PokemonType.ELECTRIC, PokemonType.STEEL, PokemonType.ROCK),
            baseFloorSeconds = 5760, needs = ExplorerRank.SILVER, layer = U, chapter = 3,
            wild = listOf(WildEntry(602, 5), WildEntry(595, 4), WildEntry(81, 4), WildEntry(524, 3), WildEntry(527, 3), WildEntry(599, 3, fromFloor = 2), WildEntry(597, 3, fromFloor = 3), WildEntry(525, 3, fromFloor = 3), WildEntry(82, 2, fromFloor = 6), WildEntry(605, 2, fromFloor = 5), WildEntry(603, 2, fromFloor = 8), WildEntry(462, 1, fromFloor = 10)),
            milestones = mapOf(6 to MilestoneReward(Items.berry("bitter"), 3), 12 to MilestoneReward(Items.RARE_CANDY, 3, poke = 600)),
        ),
        DungeonDef(
            "twist-mountain", "Twist Mountain", WorldRegion.FROZEN, floors = 14, favored = setOf(PokemonType.ICE, PokemonType.ROCK, PokemonType.FIGHTING),
            baseFloorSeconds = 7200, needs = ExplorerRank.GOLD, layer = U, chapter = 3,
            wild = listOf(WildEntry(613, 5), WildEntry(41, 3), WildEntry(529, 3), WildEntry(532, 3), WildEntry(525, 3, fromFloor = 3), WildEntry(582, 3, fromFloor = 3), WildEntry(533, 2, fromFloor = 5), WildEntry(632, 2, fromFloor = 5), WildEntry(614, 2, fromFloor = 7), WildEntry(631, 2, fromFloor = 7), WildEntry(615, 2, fromFloor = 8), WildEntry(534, 1, fromFloor = 11)),
            milestones = mapOf(7 to MilestoneReward(Items.berry("spicy"), 4), 14 to MilestoneReward(Items.RARE_CANDY, 4, poke = 900)),
        ),
        DungeonDef(
            "dragonspiral-tower", "Dragonspiral Tower", WorldRegion.ANCIENT, floors = 16, favored = setOf(PokemonType.DRAGON, PokemonType.FLYING, PokemonType.PSYCHIC),
            baseFloorSeconds = 9000, needs = ExplorerRank.GOLD, layer = U, chapter = 4,
            wild = listOf(WildEntry(622, 4), WildEntry(561, 3), WildEntry(619, 3), WildEntry(207, 2, fromFloor = 4), WildEntry(610, 2, fromFloor = 4), WildEntry(621, 2, fromFloor = 6), WildEntry(627, 2, fromFloor = 6), WildEntry(623, 2, fromFloor = 8), WildEntry(611, 1, fromFloor = 11), WildEntry(628, 1, fromFloor = 12)),
            milestones = mapOf(8 to MilestoneReward(Items.berry("dry"), 4), 16 to MilestoneReward(Items.RARE_CANDY, 5, poke = 1_400)),
        ),
        DungeonDef(
            "victory-road-unova", "Victory Road (Unova)", WorldRegion.MOUNTAINS, floors = 18, favored = setOf(PokemonType.FIGHTING, PokemonType.DRAGON, PokemonType.ROCK),
            baseFloorSeconds = 10800, needs = ExplorerRank.PLATINUM, layer = U, chapter = 4,
            wild = listOf(WildEntry(626, 3), WildEntry(621, 3), WildEntry(526, 3), WildEntry(560, 3), WildEntry(631, 2), WildEntry(632, 2), WildEntry(620, 2, fromFloor = 4), WildEntry(633, 3, fromFloor = 4), WildEntry(553, 2, fromFloor = 7), WildEntry(634, 2, fromFloor = 10), WildEntry(612, 1, fromFloor = 12)),
            milestones = mapOf(9 to MilestoneReward(Items.berry("spicy"), 4), 18 to MilestoneReward(Items.RARE_CANDY, 6, poke = 2_000)),
        ),
        DungeonDef(
            "n-castle", "N's Castle", WorldRegion.ANCIENT, floors = 18, favored = setOf(PokemonType.DARK, PokemonType.STEEL, PokemonType.GHOST),
            baseFloorSeconds = 12000, needs = ExplorerRank.PLATINUM, layer = U, chapter = 5,
            wild = listOf(WildEntry(509, 5), WildEntry(505, 3), WildEntry(624, 4), WildEntry(629, 3), WildEntry(568, 3), WildEntry(607, 3, fromFloor = 3), WildEntry(510, 3, fromFloor = 4), WildEntry(570, 2, fromFloor = 5), WildEntry(569, 2, fromFloor = 8), WildEntry(625, 2, fromFloor = 9), WildEntry(571, 1, fromFloor = 13)),
            milestones = mapOf(9 to MilestoneReward(Items.berry("bitter"), 5), 18 to MilestoneReward(Items.RARE_CANDY, 6, poke = 2_500)),
        ),
        DungeonDef(
            "giant-chasm", "Giant Chasm", WorldRegion.FROZEN, floors = 20, favored = setOf(PokemonType.ICE, PokemonType.DRAGON, PokemonType.PSYCHIC),
            baseFloorSeconds = 14400, needs = ExplorerRank.DIAMOND, layer = U, chapter = 5, guardian = true,
            wild = listOf(WildEntry(615, 3), WildEntry(614, 3), WildEntry(613, 3), WildEntry(583, 3), WildEntry(584, 2, fromFloor = 6), WildEntry(473, 2, fromFloor = 8), WildEntry(606, 2, fromFloor = 8), WildEntry(623, 2, fromFloor = 10), WildEntry(461, 2, fromFloor = 12), WildEntry(635, 1, fromFloor = 16)),
            milestones = mapOf(10 to MilestoneReward(Items.berry("sweet"), 5), 20 to MilestoneReward(Items.RARE_CANDY, 8, poke = 4_000)),
        ),
    )
}

object UnovaStory {
    val chapters: List<Chapter> = listOf(
        Chapter(1, "Nuvema Town", "A new explorer sets out from Nuvema Town. Pinwheel Forest and Wellspring Cave are lively, but a crowd keeps talking about freeing Pokémon.", "Pinwheel Forest and Wellspring Cave", ExplorerRank.NORMAL, 0),
        Chapter(2, "Truth and Ideals", "An old legend tells of two dragons and two kinds of hero, one of truth and one of ideals. The Relic Castle hides its first page.", "Relic Castle", ExplorerRank.BRONZE, 2),
        Chapter(3, "Team Plasma", "A team in knightly robes says Pokémon must be set free from people. Chargestone Cave and Twist Mountain show what it costs.", "Chargestone Cave and Twist Mountain", ExplorerRank.SILVER, 3),
        Chapter(4, "The Dragonspiral Tower", "At the top of the Dragonspiral Tower a white dragon of truth and the road to the League open.", "Dragonspiral Tower, Victory Road and Reshiram", ExplorerRank.GOLD, 5),
        Chapter(5, "The Giant Chasm", "In N's Castle the dark dragon answers, and past the castle a frozen chasm holds the dragon that was left behind.", "N's Castle, Giant Chasm, Zekrom and Kyurem", ExplorerRank.PLATINUM, 7),
    )

    const val GUARDIAN = "Kyurem"
    const val GUARDIAN_BONUS = "Boundary Frost: Unova expeditions find treasure a little more often"
}
