package app.pokeplan.world

import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.Items

/** The broad kinds of place the world map is made of (docs/pokelife/MODULES/WORLD.md section 14). Only the first few are in the MVP. */
enum class WorldRegion(val label: String) { GREENLANDS("Greenlands"), MOUNTAINS("Mountains"), UNDERGROUND("Underground"), AQUATIC("Aquatic"), FROZEN("Frozen"), ANCIENT("Ancient"), SKY("Sky"), OTHERWORLDLY("Otherworldly") }

/** A wild Pokémon a dungeon can show on floors [fromFloor] to [toFloor], with a relative [weight]. */
data class WildEntry(val speciesId: Int, val weight: Int, val fromFloor: Int = 1, val toFloor: Int = Int.MAX_VALUE)

/** A reward a milestone gives (an item id from the shared bag and a count, or Poké). */
data class MilestoneReward(val itemId: String? = null, val count: Int = 0, val poke: Int = 0)

/**
 * A dungeon: [floors] to climb, the wild Pokémon [wild] that can be found, the [favored] types whose members explore it
 * faster, how long a floor takes a team with no power at all ([baseFloorSeconds]), the rank it needs, and a milestone reward for
 * each floor reached the first time. [canon] is false for a place that is PMD-inspired rather than a canon dungeon, and the
 * species tables here are illustrative until they are checked against a source game (docs/pokelife/MODULES/WORLD.md section 14).
 */
data class DungeonDef(
    val id: String,
    val name: String,
    val region: WorldRegion,
    val floors: Int,
    val favored: Set<PokemonType>,
    val baseFloorSeconds: Int,
    val needs: ExplorerRank,
    val wild: List<WildEntry>,
    val milestones: Map<Int, MilestoneReward>,
    val canon: Boolean = true,
    /** The story chapter that must be open to enter. */
    val chapter: Int = 1,
    /** The last floor of the Guardian's dungeon: clearing it completes the story once. */
    val guardian: Boolean = false,
    /** Opens only after the story of its layer is complete (a Distortion Dungeon). */
    val postStory: Boolean = false,
    /** The layer of the map this dungeon is in; [chapter] and [guardian] are about that layer's story. */
    val layer: WorldLayer = WorldLayer.EXPLORERS,
)

/** The dungeons of the two Mystery Dungeon arcs: Rescue Team (Tiny Woods, Thunderwave Cave, Mt. Steel, Frosty Forest, Sky Tower) and Explorers (Beach Cave to the Temporal Tower), each its own layer. *(tune)* */
object Dungeons {
    /** Every dungeon of every layer, the two Mystery Dungeon arcs first. */
    val all: List<DungeonDef> by lazy { home + KantoDungeons.all + JohtoDungeons.all + HoennDungeons.all + SinnohDungeons.all }

    /** The dungeons of one layer, in the order the map lists them. */
    fun of(layer: WorldLayer): List<DungeonDef> = all.filter { it.layer == layer }

    val home: List<DungeonDef> = listOf(
        DungeonDef(
            "tiny-woods", "Tiny Woods", WorldRegion.GREENLANDS, floors = 5, favored = setOf(PokemonType.GRASS, PokemonType.BUG, PokemonType.FLYING),
            baseFloorSeconds = 1800, needs = ExplorerRank.NORMAL, layer = WorldLayer.RESCUE,
            wild = listOf(WildEntry(10, 5), WildEntry(13, 5), WildEntry(16, 5), WildEntry(19, 4), WildEntry(43, 3, fromFloor = 2), WildEntry(191, 2, fromFloor = 3), WildEntry(25, 1, fromFloor = 4)),
            milestones = mapOf(3 to MilestoneReward(Items.berry("sweet"), 2), 5 to MilestoneReward(Items.RARE_CANDY, 1, poke = 100)),
        ),
        DungeonDef(
            "thunderwave-cave", "Thunderwave Cave", WorldRegion.UNDERGROUND, floors = 8, favored = setOf(PokemonType.ELECTRIC, PokemonType.ROCK, PokemonType.GROUND),
            baseFloorSeconds = 2880, needs = ExplorerRank.NORMAL, layer = WorldLayer.RESCUE,
            wild = listOf(WildEntry(41, 5), WildEntry(74, 4), WildEntry(81, 4, fromFloor = 2), WildEntry(100, 3, fromFloor = 3), WildEntry(25, 2, fromFloor = 3), WildEntry(239, 1, fromFloor = 5)),
            milestones = mapOf(4 to MilestoneReward(Items.berry("dry"), 2), 8 to MilestoneReward(Items.RARE_CANDY, 2, poke = 200)),
        ),
        DungeonDef(
            "mt-steel", "Mt. Steel", WorldRegion.MOUNTAINS, floors = 10, favored = setOf(PokemonType.STEEL, PokemonType.ROCK, PokemonType.FIGHTING),
            baseFloorSeconds = 4320, needs = ExplorerRank.BRONZE, chapter = 2, layer = WorldLayer.RESCUE,
            wild = listOf(WildEntry(74, 5), WildEntry(95, 3, fromFloor = 3), WildEntry(66, 4), WildEntry(227, 1, fromFloor = 7), WildEntry(81, 3), WildEntry(208, 1, fromFloor = 8)),
            milestones = mapOf(5 to MilestoneReward(Items.berry("spicy"), 2), 10 to MilestoneReward(Items.RARE_CANDY, 3, poke = 400)),
        ),
        DungeonDef(
            "beach-cave", "Beach Cave", WorldRegion.AQUATIC, floors = 5, favored = setOf(PokemonType.WATER, PokemonType.GROUND, PokemonType.FLYING),
            baseFloorSeconds = 1800, needs = ExplorerRank.NORMAL,
            wild = listOf(WildEntry(98, 5), WildEntry(90, 4), WildEntry(278, 4), WildEntry(79, 3, fromFloor = 2), WildEntry(120, 2, fromFloor = 3), WildEntry(222, 1, fromFloor = 4)),
            milestones = mapOf(3 to MilestoneReward(Items.berry("sweet"), 2), 5 to MilestoneReward(Items.RARE_CANDY, 1, poke = 100)),
        ),
        DungeonDef(
            "drenched-bluff", "Drenched Bluff", WorldRegion.AQUATIC, floors = 8, favored = setOf(PokemonType.WATER, PokemonType.GROUND, PokemonType.NORMAL),
            baseFloorSeconds = 2880, needs = ExplorerRank.BRONZE, chapter = 2,
            wild = listOf(WildEntry(399, 5), WildEntry(60, 4), WildEntry(183, 4), WildEntry(194, 3, fromFloor = 2), WildEntry(422, 3, fromFloor = 3), WildEntry(453, 1, fromFloor = 6)),
            milestones = mapOf(4 to MilestoneReward(Items.berry("dry"), 2), 8 to MilestoneReward(Items.RARE_CANDY, 2, poke = 200)),
        ),
        DungeonDef(
            "waterfall-cave", "Waterfall Cave", WorldRegion.AQUATIC, floors = 12, favored = setOf(PokemonType.WATER, PokemonType.ICE, PokemonType.GROUND),
            baseFloorSeconds = 5400, needs = ExplorerRank.SILVER, chapter = 3, canon = true,
            wild = listOf(WildEntry(54, 5), WildEntry(60, 5), WildEntry(118, 4), WildEntry(129, 3), WildEntry(194, 4, fromFloor = 3), WildEntry(131, 1, fromFloor = 9)),
            milestones = mapOf(6 to MilestoneReward(Items.berry("sour"), 3), 12 to MilestoneReward(Items.RARE_CANDY, 3, poke = 700)),
        ),
        DungeonDef(
            "crystal-cave", "Crystal Cave", WorldRegion.UNDERGROUND, floors = 12, favored = setOf(PokemonType.ROCK, PokemonType.GROUND, PokemonType.STEEL),
            baseFloorSeconds = 6000, needs = ExplorerRank.SILVER, chapter = 3,
            wild = listOf(WildEntry(74, 5), WildEntry(95, 4), WildEntry(111, 3, fromFloor = 3), WildEntry(207, 3, fromFloor = 3), WildEntry(299, 3, fromFloor = 5), WildEntry(213, 1, fromFloor = 9)),
            milestones = mapOf(6 to MilestoneReward(Items.berry("dry"), 3), 12 to MilestoneReward(Items.RARE_CANDY, 3, poke = 750)),
        ),
        DungeonDef(
            "glacier-palace", "Glacier Palace", WorldRegion.FROZEN, floors = 14, favored = setOf(PokemonType.ICE, PokemonType.WATER, PokemonType.STEEL),
            baseFloorSeconds = 7200, needs = ExplorerRank.GOLD, chapter = 4, canon = false,
            wild = listOf(WildEntry(220, 5), WildEntry(361, 5), WildEntry(215, 3, fromFloor = 4), WildEntry(225, 3), WildEntry(363, 4), WildEntry(362, 1, fromFloor = 10)),
            milestones = mapOf(7 to MilestoneReward(Items.berry("bitter"), 3), 14 to MilestoneReward(Items.RARE_CANDY, 4, poke = 1_000)),
        ),
        DungeonDef(
            "ancient-ruins", "Ancient Ruins", WorldRegion.ANCIENT, floors = 16, favored = setOf(PokemonType.ROCK, PokemonType.PSYCHIC, PokemonType.GROUND),
            baseFloorSeconds = 9000, needs = ExplorerRank.PLATINUM, chapter = 5, canon = false,
            wild = listOf(WildEntry(177, 5), WildEntry(343, 5), WildEntry(63, 3, fromFloor = 3), WildEntry(347, 3, fromFloor = 5), WildEntry(345, 3, fromFloor = 5), WildEntry(138, 2, fromFloor = 7), WildEntry(140, 2, fromFloor = 7), WildEntry(246, 1, fromFloor = 12)),
            milestones = mapOf(8 to MilestoneReward(Items.berry("dry"), 4), 16 to MilestoneReward(Items.RARE_CANDY, 5, poke = 1_500)),
        ),
        DungeonDef(
            "frosty-forest", "Frosty Forest", WorldRegion.FROZEN, floors = 12, favored = setOf(PokemonType.ICE, PokemonType.WATER, PokemonType.DARK),
            baseFloorSeconds = 5400, needs = ExplorerRank.SILVER, chapter = 4, layer = WorldLayer.RESCUE,
            wild = listOf(WildEntry(361, 5), WildEntry(220, 5), WildEntry(86, 4), WildEntry(215, 3, fromFloor = 3), WildEntry(225, 3, fromFloor = 4), WildEntry(359, 1, fromFloor = 9)),
            milestones = mapOf(6 to MilestoneReward(Items.berry("sour"), 3), 12 to MilestoneReward(Items.RARE_CANDY, 3, poke = 700)),
        ),
        DungeonDef(
            "sky-tower", "Sky Tower", WorldRegion.SKY, floors = 18, favored = setOf(PokemonType.FLYING, PokemonType.DRAGON, PokemonType.PSYCHIC),
            baseFloorSeconds = 10800, needs = ExplorerRank.PLATINUM, chapter = 5, canon = true, guardian = true, layer = WorldLayer.RESCUE,
            wild = listOf(WildEntry(276, 5), WildEntry(278, 5), WildEntry(333, 4), WildEntry(371, 2, fromFloor = 6), WildEntry(334, 1, fromFloor = 12), WildEntry(147, 1, fromFloor = 14)),
            milestones = mapOf(9 to MilestoneReward(Items.berry("spicy"), 4), 18 to MilestoneReward(Items.RARE_CANDY, 6, poke = 2_000)),
        ),
        DungeonDef(
            "hidden-land", "Hidden Land", WorldRegion.ANCIENT, floors = 16, favored = setOf(PokemonType.GRASS, PokemonType.PSYCHIC, PokemonType.NORMAL),
            baseFloorSeconds = 9600, needs = ExplorerRank.PLATINUM, chapter = 6,
            wild = listOf(WildEntry(315, 5), WildEntry(357, 4), WildEntry(327, 4), WildEntry(358, 3, fromFloor = 4), WildEntry(352, 2, fromFloor = 6), WildEntry(185, 2, fromFloor = 8)),
            milestones = mapOf(8 to MilestoneReward(Items.berry("sweet"), 4), 16 to MilestoneReward(Items.RARE_CANDY, 5, poke = 1_600)),
        ),
        DungeonDef(
            "temporal-tower", "Temporal Tower", WorldRegion.OTHERWORLDLY, floors = 20, favored = setOf(PokemonType.PSYCHIC, PokemonType.GHOST, PokemonType.DRAGON, PokemonType.STEEL),
            baseFloorSeconds = 14400, needs = ExplorerRank.DIAMOND, chapter = 7, canon = true, guardian = true,
            wild = listOf(WildEntry(92, 5), WildEntry(355, 4), WildEntry(353, 4), WildEntry(200, 3, fromFloor = 5), WildEntry(374, 2, fromFloor = 8), WildEntry(436, 3, fromFloor = 8)),
            milestones = mapOf(10 to MilestoneReward(Items.berry("sweet"), 5), 20 to MilestoneReward(Items.RARE_CANDY, 8, poke = 4_000)),
        ),
        DungeonDef(
            "distortion-rift", "Distortion Rift", WorldRegion.OTHERWORLDLY, floors = 25, favored = setOf(PokemonType.PSYCHIC, PokemonType.GHOST, PokemonType.DARK, PokemonType.DRAGON),
            baseFloorSeconds = 18000, needs = ExplorerRank.DIAMOND, chapter = 7, canon = false, postStory = true,
            wild = listOf(WildEntry(147, 4), WildEntry(246, 4), WildEntry(374, 4), WildEntry(371, 3), WildEntry(443, 3, fromFloor = 8), WildEntry(633, 3, fromFloor = 8)),
            milestones = mapOf(13 to MilestoneReward(Items.berry("bitter"), 5), 25 to MilestoneReward(Items.RARE_CANDY, 10, poke = 8_000)),
        ),
    )

    private val byId: Map<String, DungeonDef> by lazy { all.associateBy { it.id } }

    fun byId(id: String): DungeonDef? = byId[id]
}
