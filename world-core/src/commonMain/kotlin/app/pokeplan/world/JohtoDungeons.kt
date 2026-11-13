package app.pokeplan.world

import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.Items

/**
 * The nine dungeons of the Johto layer (docs/pokelife/MODULES/WORLD.md section 14, "Johto"), from Pokémon Gold, Silver, Crystal, HeartGold and SoulSilver. Their
 * wild tables are Johto's own species (the Johto dex, 152 to 251, with the Kanto species that live there), by each place's habitat in
 * the games; legendaries are never found (they are quests, [Legends]; the three beasts and Celebi are side legendaries of the modules).
 * Floors, floor times, milestones and the rank gates are starting values *(tune)*. [DungeonDef.chapter] is a chapter of [JohtoStory].
 */
object JohtoDungeons {
    private val J = WorldLayer.JOHTO

    val all: List<DungeonDef> = listOf(
        DungeonDef(
            "sprout-tower", "Sprout Tower", WorldRegion.ANCIENT, floors = 5, favored = setOf(PokemonType.GRASS, PokemonType.GHOST, PokemonType.NORMAL),
            baseFloorSeconds = 1800, needs = ExplorerRank.NORMAL, layer = J, chapter = 1,
            wild = listOf(WildEntry(69, 5), WildEntry(19, 5), WildEntry(92, 3, fromFloor = 2), WildEntry(70, 2, fromFloor = 4), WildEntry(163, 2)),
            milestones = mapOf(3 to MilestoneReward(Items.berry("sweet"), 2), 5 to MilestoneReward(Items.RARE_CANDY, 1, poke = 100)),
        ),
        DungeonDef(
            "ilex-forest", "Ilex Forest", WorldRegion.GREENLANDS, floors = 8, favored = setOf(PokemonType.BUG, PokemonType.GRASS, PokemonType.POISON),
            baseFloorSeconds = 2880, needs = ExplorerRank.NORMAL, layer = J, chapter = 1,
            wild = listOf(WildEntry(10, 4), WildEntry(13, 4), WildEntry(43, 4), WildEntry(41, 3), WildEntry(46, 3, fromFloor = 3), WildEntry(11, 2, fromFloor = 4), WildEntry(14, 2, fromFloor = 4), WildEntry(44, 1, fromFloor = 7)),
            milestones = mapOf(4 to MilestoneReward(Items.berry("sour"), 2), 8 to MilestoneReward(Items.RARE_CANDY, 2, poke = 200)),
        ),
        DungeonDef(
            "union-cave", "Union Cave", WorldRegion.UNDERGROUND, floors = 10, favored = setOf(PokemonType.GROUND, PokemonType.ROCK, PokemonType.WATER),
            baseFloorSeconds = 4320, needs = ExplorerRank.BRONZE, layer = J, chapter = 2,
            wild = listOf(WildEntry(41, 4), WildEntry(74, 4), WildEntry(27, 3), WildEntry(194, 3), WildEntry(95, 3, fromFloor = 3), WildEntry(75, 2, fromFloor = 6), WildEntry(28, 1, fromFloor = 8)),
            milestones = mapOf(5 to MilestoneReward(Items.berry("dry"), 2), 10 to MilestoneReward(Items.RARE_CANDY, 3, poke = 400)),
        ),
        DungeonDef(
            "ruins-of-alph", "Ruins of Alph", WorldRegion.ANCIENT, floors = 12, favored = setOf(PokemonType.PSYCHIC, PokemonType.ROCK, PokemonType.GROUND),
            baseFloorSeconds = 5400, needs = ExplorerRank.SILVER, layer = J, chapter = 3,
            wild = listOf(WildEntry(201, 5), WildEntry(177, 4), WildEntry(202, 2, fromFloor = 3), WildEntry(235, 2, fromFloor = 4), WildEntry(178, 2, fromFloor = 7), WildEntry(41, 2), WildEntry(63, 1, fromFloor = 9)),
            milestones = mapOf(6 to MilestoneReward(Items.berry("spicy"), 3), 12 to MilestoneReward(Items.RARE_CANDY, 3, poke = 700)),
        ),
        DungeonDef(
            "slowpoke-well", "Slowpoke Well", WorldRegion.AQUATIC, floors = 12, favored = setOf(PokemonType.WATER, PokemonType.PSYCHIC, PokemonType.POISON),
            baseFloorSeconds = 5400, needs = ExplorerRank.SILVER, layer = J, chapter = 3,
            wild = listOf(WildEntry(79, 5), WildEntry(41, 4), WildEntry(72, 3), WildEntry(194, 3), WildEntry(195, 2, fromFloor = 6), WildEntry(42, 2, fromFloor = 5), WildEntry(80, 1, fromFloor = 9)),
            milestones = mapOf(6 to MilestoneReward(Items.berry("sour"), 3), 12 to MilestoneReward(Items.RARE_CANDY, 3, poke = 700)),
        ),
        DungeonDef(
            "mt-mortar", "Mt. Mortar", WorldRegion.MOUNTAINS, floors = 14, favored = setOf(PokemonType.FIGHTING, PokemonType.ROCK, PokemonType.WATER),
            baseFloorSeconds = 7200, needs = ExplorerRank.GOLD, layer = J, chapter = 4,
            wild = listOf(WildEntry(66, 4), WildEntry(74, 4), WildEntry(41, 3), WildEntry(183, 3), WildEntry(236, 2, fromFloor = 4), WildEntry(67, 2, fromFloor = 6), WildEntry(106, 1, fromFloor = 10), WildEntry(107, 1, fromFloor = 10)),
            milestones = mapOf(7 to MilestoneReward(Items.berry("bitter"), 3), 14 to MilestoneReward(Items.RARE_CANDY, 4, poke = 1_000)),
        ),
        DungeonDef(
            "burned-tower", "Burned Tower", WorldRegion.ANCIENT, floors = 16, favored = setOf(PokemonType.FIRE, PokemonType.GHOST, PokemonType.POISON),
            baseFloorSeconds = 9000, needs = ExplorerRank.GOLD, layer = J, chapter = 4,
            wild = listOf(WildEntry(109, 5), WildEntry(19, 4), WildEntry(41, 3), WildEntry(92, 3), WildEntry(126, 2, fromFloor = 6), WildEntry(110, 2, fromFloor = 8), WildEntry(93, 1, fromFloor = 10), WildEntry(20, 1, fromFloor = 10)),
            milestones = mapOf(8 to MilestoneReward(Items.berry("spicy"), 4), 16 to MilestoneReward(Items.RARE_CANDY, 5, poke = 1_500)),
        ),
        DungeonDef(
            "whirl-islands", "Whirl Islands", WorldRegion.AQUATIC, floors = 18, favored = setOf(PokemonType.WATER, PokemonType.FLYING, PokemonType.ICE),
            baseFloorSeconds = 10800, needs = ExplorerRank.PLATINUM, layer = J, chapter = 4,
            wild = listOf(WildEntry(86, 4), WildEntry(72, 4), WildEntry(41, 3), WildEntry(116, 3), WildEntry(98, 2, fromFloor = 4), WildEntry(42, 3, fromFloor = 5), WildEntry(87, 2, fromFloor = 8), WildEntry(117, 1, fromFloor = 12), WildEntry(73, 1, fromFloor = 10)),
            milestones = mapOf(9 to MilestoneReward(Items.berry("sweet"), 4), 18 to MilestoneReward(Items.RARE_CANDY, 6, poke = 2_000)),
        ),
        DungeonDef(
            "tin-tower", "Tin Tower", WorldRegion.SKY, floors = 20, favored = setOf(PokemonType.FLYING, PokemonType.FIRE, PokemonType.GHOST),
            baseFloorSeconds = 14400, needs = ExplorerRank.DIAMOND, layer = J, chapter = 5, guardian = true,
            wild = listOf(WildEntry(92, 4), WildEntry(93, 3, fromFloor = 3), WildEntry(19, 3), WildEntry(177, 3), WildEntry(178, 2, fromFloor = 8), WildEntry(21, 2), WildEntry(22, 1, fromFloor = 12), WildEntry(94, 1, fromFloor = 14)),
            milestones = mapOf(10 to MilestoneReward(Items.berry("bitter"), 5), 20 to MilestoneReward(Items.RARE_CANDY, 8, poke = 4_000)),
        ),
    )
}

/**
 * Johto's story, "The Sacred Balance" (docs/regional-storylines.md): the burned tower, the legendary beasts, the sky and the sea. A
 * chapter opens by rank and by how many different Johto dungeons have been cleared, and is never lost.
 */
object JohtoStory {
    val chapters: List<Chapter> = listOf(
        Chapter(1, "New Bark Town", "A new explorer sets out from New Bark Town. The old stories of Johto say the sky and the sea are in balance.", "Sprout Tower and Ilex Forest", ExplorerRank.NORMAL, 0),
        Chapter(2, "Old Stories", "The people of Violet City and Union Cave speak of a time before the towers, and of what was lost.", "Union Cave", ExplorerRank.BRONZE, 2),
        Chapter(3, "Echoes of Alph", "The Ruins of Alph and the Slowpoke Well hum with a strange power. Something is out of balance.", "Ruins of Alph and Slowpoke Well", ExplorerRank.SILVER, 3),
        Chapter(4, "The Burned Tower", "Mt. Mortar roars, the Burned Tower smoulders and the Whirl Islands churn. The guardian of the sea stirs.", "Mt. Mortar, Burned Tower, Whirl Islands and Lugia", ExplorerRank.GOLD, 5),
        Chapter(5, "The Rainbow Wings", "At the top of the Tin Tower, the guardian of the sky waits for one who can restore the harmony of the sky and the sea.", "Tin Tower and Ho-Oh", ExplorerRank.PLATINUM, 8),
    )

    /** The Guardian: Ho-Oh, at the top of the Tin Tower. */
    const val GUARDIAN = "Ho-Oh"
    const val GUARDIAN_BONUS = "Sacred Fire: Johto expeditions find more treasure and Pokémon"
}
