package app.pokeplan.world

import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.Items

/**
 * The nine dungeons of the Sinnoh layer (docs/pokelife/MODULES/WORLD.md section 14, "Sinnoh"), from Pokémon Diamond, Pearl, Platinum, Brilliant Diamond and Shining
 * Pearl. Their wild tables are the Sinnoh regional dex, by each place's habitat in the games; legendaries are never found (they are
 * quests, [Legends]). Floors, floor times, milestones and the rank gates are starting values *(tune)*. [DungeonDef.chapter] is a chapter
 * of [SinnohStory].
 */
object SinnohDungeons {
    private val S = WorldLayer.SINNOH

    val all: List<DungeonDef> = listOf(
        DungeonDef(
            "eterna-forest", "Eterna Forest", WorldRegion.GREENLANDS, floors = 5, favored = setOf(PokemonType.BUG, PokemonType.GRASS, PokemonType.NORMAL),
            baseFloorSeconds = 1800, needs = ExplorerRank.NORMAL, layer = S, chapter = 1,
            wild = listOf(WildEntry(396, 5), WildEntry(399, 5), WildEntry(401, 4), WildEntry(406, 4), WildEntry(412, 3, fromFloor = 2), WildEntry(415, 2, fromFloor = 3), WildEntry(403, 2, fromFloor = 3), WildEntry(417, 2, fromFloor = 2), WildEntry(420, 2, fromFloor = 3), WildEntry(427, 1, fromFloor = 4)),
            milestones = mapOf(3 to MilestoneReward(Items.berry("sweet"), 2), 5 to MilestoneReward(Items.RARE_CANDY, 1, poke = 100)),
        ),
        DungeonDef(
            "oreburgh-mine", "Oreburgh Mine", WorldRegion.UNDERGROUND, floors = 8, favored = setOf(PokemonType.ROCK, PokemonType.GROUND, PokemonType.STEEL),
            baseFloorSeconds = 2880, needs = ExplorerRank.NORMAL, layer = S, chapter = 1,
            wild = listOf(WildEntry(41, 5), WildEntry(74, 5), WildEntry(95, 3, fromFloor = 3), WildEntry(66, 3), WildEntry(403, 3), WildEntry(436, 2, fromFloor = 4), WildEntry(75, 2, fromFloor = 6), WildEntry(207, 1, fromFloor = 5)),
            milestones = mapOf(4 to MilestoneReward(Items.berry("dry"), 2), 8 to MilestoneReward(Items.RARE_CANDY, 2, poke = 200)),
        ),
        DungeonDef(
            "wayward-cave", "Wayward Cave", WorldRegion.UNDERGROUND, floors = 10, favored = setOf(PokemonType.GROUND, PokemonType.ROCK, PokemonType.DRAGON),
            baseFloorSeconds = 4320, needs = ExplorerRank.BRONZE, layer = S, chapter = 2,
            wild = listOf(WildEntry(41, 4), WildEntry(74, 4), WildEntry(75, 3, fromFloor = 3), WildEntry(443, 3, fromFloor = 4), WildEntry(66, 3), WildEntry(95, 2, fromFloor = 5), WildEntry(447, 2, fromFloor = 7), WildEntry(449, 1, fromFloor = 8)),
            milestones = mapOf(5 to MilestoneReward(Items.berry("sour"), 3), 10 to MilestoneReward(Items.RARE_CANDY, 3, poke = 400)),
        ),
        DungeonDef(
            "old-chateau", "Old Chateau", WorldRegion.ANCIENT, floors = 12, favored = setOf(PokemonType.GHOST, PokemonType.DARK, PokemonType.PSYCHIC),
            baseFloorSeconds = 5760, needs = ExplorerRank.SILVER, layer = S, chapter = 3,
            wild = listOf(WildEntry(92, 4), WildEntry(355, 4), WildEntry(200, 3, fromFloor = 3), WildEntry(93, 3, fromFloor = 5), WildEntry(356, 2, fromFloor = 6), WildEntry(442, 1, fromFloor = 8), WildEntry(425, 2, fromFloor = 4), WildEntry(433, 2, fromFloor = 6)),
            milestones = mapOf(6 to MilestoneReward(Items.berry("bitter"), 3), 12 to MilestoneReward(Items.RARE_CANDY, 3, poke = 600)),
        ),
        DungeonDef(
            "iron-island", "Iron Island", WorldRegion.UNDERGROUND, floors = 14, favored = setOf(PokemonType.STEEL, PokemonType.FIGHTING, PokemonType.POISON),
            baseFloorSeconds = 7200, needs = ExplorerRank.GOLD, layer = S, chapter = 3,
            wild = listOf(WildEntry(41, 3), WildEntry(66, 3), WildEntry(95, 3), WildEntry(436, 3), WildEntry(447, 3, fromFloor = 4), WildEntry(451, 2, fromFloor = 5), WildEntry(67, 2, fromFloor = 7), WildEntry(42, 2, fromFloor = 6), WildEntry(453, 2, fromFloor = 6), WildEntry(454, 1, fromFloor = 11)),
            milestones = mapOf(7 to MilestoneReward(Items.berry("spicy"), 4), 14 to MilestoneReward(Items.RARE_CANDY, 4, poke = 900)),
        ),
        DungeonDef(
            "mt-coronet", "Mt. Coronet", WorldRegion.MOUNTAINS, floors = 16, favored = setOf(PokemonType.ROCK, PokemonType.ICE, PokemonType.STEEL),
            baseFloorSeconds = 9000, needs = ExplorerRank.GOLD, layer = S, chapter = 4,
            wild = listOf(WildEntry(41, 3), WildEntry(42, 2, fromFloor = 4), WildEntry(74, 3), WildEntry(75, 2, fromFloor = 5), WildEntry(433, 3, fromFloor = 3), WildEntry(215, 2, fromFloor = 6), WildEntry(361, 3), WildEntry(443, 2, fromFloor = 8), WildEntry(459, 2, fromFloor = 9), WildEntry(35, 2, fromFloor = 4), WildEntry(307, 2, fromFloor = 5)),
            milestones = mapOf(8 to MilestoneReward(Items.berry("dry"), 4), 16 to MilestoneReward(Items.RARE_CANDY, 5, poke = 1_400)),
        ),
        DungeonDef(
            "victory-road-sinnoh", "Victory Road (Sinnoh)", WorldRegion.MOUNTAINS, floors = 18, favored = setOf(PokemonType.FIGHTING, PokemonType.ROCK, PokemonType.DRAGON),
            baseFloorSeconds = 10800, needs = ExplorerRank.PLATINUM, layer = S, chapter = 4,
            wild = listOf(WildEntry(42, 3), WildEntry(67, 3), WildEntry(75, 3), WildEntry(95, 2), WildEntry(447, 3), WildEntry(444, 2, fromFloor = 6), WildEntry(436, 2), WildEntry(437, 2, fromFloor = 10), WildEntry(215, 2, fromFloor = 8)),
            milestones = mapOf(9 to MilestoneReward(Items.berry("spicy"), 4), 18 to MilestoneReward(Items.RARE_CANDY, 6, poke = 2_000)),
        ),
        DungeonDef(
            "spear-pillar", "Spear Pillar", WorldRegion.ANCIENT, floors = 18, favored = setOf(PokemonType.DRAGON, PokemonType.WATER, PokemonType.PSYCHIC),
            baseFloorSeconds = 12000, needs = ExplorerRank.PLATINUM, layer = S, chapter = 5,
            wild = listOf(WildEntry(42, 3), WildEntry(433, 3), WildEntry(436, 3), WildEntry(437, 2, fromFloor = 6), WildEntry(444, 2, fromFloor = 8), WildEntry(358, 2, fromFloor = 8), WildEntry(356, 1, fromFloor = 12)),
            milestones = mapOf(9 to MilestoneReward(Items.berry("bitter"), 5), 18 to MilestoneReward(Items.RARE_CANDY, 6, poke = 2_500)),
        ),
        DungeonDef(
            "distortion-world", "Distortion World", WorldRegion.OTHERWORLDLY, floors = 20, favored = setOf(PokemonType.GHOST, PokemonType.DRAGON, PokemonType.PSYCHIC),
            baseFloorSeconds = 14400, needs = ExplorerRank.DIAMOND, layer = S, chapter = 5, guardian = true,
            wild = listOf(WildEntry(92, 3), WildEntry(93, 3), WildEntry(355, 3), WildEntry(356, 3, fromFloor = 5), WildEntry(200, 3), WildEntry(429, 2, fromFloor = 8), WildEntry(442, 2, fromFloor = 10), WildEntry(478, 1, fromFloor = 14), WildEntry(477, 1, fromFloor = 16)),
            milestones = mapOf(10 to MilestoneReward(Items.berry("sweet"), 5), 20 to MilestoneReward(Items.RARE_CANDY, 8, poke = 4_000)),
        ),
    )
}

/**
 * Sinnoh's story, "The Distortion of Creation" (docs/regional-storylines.md): disturbances in space and time, Team Galactic and the
 * Distortion World. A chapter opens by rank and by how many different Sinnoh dungeons have been cleared, and is never lost.
 */
object SinnohStory {
    val chapters: List<Chapter> = listOf(
        Chapter(1, "Twinleaf Town", "A new explorer sets out from Twinleaf Town. The forests and mines are quiet, but something in the sky is wrong.", "Eterna Forest and Oreburgh Mine", ExplorerRank.NORMAL, 0),
        Chapter(2, "Whispers of Creation", "Old tales say three creators shaped time, space and the world behind them. Wayward Cave hides the first traces.", "Wayward Cave", ExplorerRank.BRONZE, 2),
        Chapter(3, "Team Galactic", "A team in bright uniforms wants to remake the world without spirit. Old Chateau and Iron Island hold clues.", "Old Chateau and Iron Island", ExplorerRank.SILVER, 3),
        Chapter(4, "The Peak of Mt. Coronet", "Time and space tremble above the clouds. The road to the League and the mountain's summit open.", "Mt. Coronet, Victory Road and Dialga", ExplorerRank.GOLD, 5),
        Chapter(5, "The Distortion World", "At the Spear Pillar the creators answer, and behind them a world turned inside out waits for you.", "Spear Pillar, the Distortion World, Palkia and Giratina", ExplorerRank.PLATINUM, 7),
    )

    /** The Guardian: Giratina, in the Distortion World. */
    const val GUARDIAN = "Giratina"
    const val GUARDIAN_BONUS = "Reverse World: Sinnoh expeditions find treasure a little more often"
}
