package app.pokeplan.world

import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.Items

/** The broad kinds of place the world map is made of (docs/world.md, section 14). Only the first few are in the MVP. */
enum class WorldRegion(val label: String) { GREENLANDS("Greenlands"), MOUNTAINS("Mountains"), UNDERGROUND("Underground"), AQUATIC("Aquatic"), FROZEN("Frozen"), ANCIENT("Ancient"), SKY("Sky"), OTHERWORLDLY("Otherworldly") }

/** A wild Pokémon a dungeon can show on floors [fromFloor] to [toFloor], with a relative [weight]. */
data class WildEntry(val speciesId: Int, val weight: Int, val fromFloor: Int = 1, val toFloor: Int = Int.MAX_VALUE)

/** A reward a milestone gives (an item id from the shared bag and a count, or Poké). */
data class MilestoneReward(val itemId: String? = null, val count: Int = 0, val poke: Int = 0)

/**
 * A dungeon: [floors] to climb, the wild Pokémon [wild] that can be found, the [favored] types whose members explore it
 * faster, how long a floor takes a team with no power at all ([baseFloorSeconds]), the rank it needs, and a milestone reward for
 * each floor reached the first time. [canon] is false for a place that is PMD-inspired rather than a canon dungeon, and the
 * species tables here are illustrative until they are checked against a source game (docs/world.md, section 5).
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
)

/** The first three dungeons (the MVP's world map): Tiny Woods, Thunderwave Cave and Mt. Steel, from Explorers of Sky. *(tune)* */
object Dungeons {
    val all: List<DungeonDef> = listOf(
        DungeonDef(
            "tiny-woods", "Tiny Woods", WorldRegion.GREENLANDS, floors = 5, favored = setOf(PokemonType.GRASS, PokemonType.BUG, PokemonType.FLYING),
            baseFloorSeconds = 1800, needs = ExplorerRank.NORMAL,
            wild = listOf(WildEntry(10, 5), WildEntry(13, 5), WildEntry(16, 5), WildEntry(19, 4), WildEntry(43, 3, fromFloor = 2), WildEntry(191, 2, fromFloor = 3), WildEntry(25, 1, fromFloor = 4)),
            milestones = mapOf(3 to MilestoneReward(Items.berry("sweet"), 2), 5 to MilestoneReward(Items.RARE_CANDY, 1, poke = 100)),
        ),
        DungeonDef(
            "thunderwave-cave", "Thunderwave Cave", WorldRegion.UNDERGROUND, floors = 8, favored = setOf(PokemonType.ELECTRIC, PokemonType.ROCK, PokemonType.GROUND),
            baseFloorSeconds = 2880, needs = ExplorerRank.NORMAL,
            wild = listOf(WildEntry(41, 5), WildEntry(74, 4), WildEntry(81, 4, fromFloor = 2), WildEntry(100, 3, fromFloor = 3), WildEntry(25, 2, fromFloor = 3), WildEntry(239, 1, fromFloor = 5)),
            milestones = mapOf(4 to MilestoneReward(Items.berry("dry"), 2), 8 to MilestoneReward(Items.RARE_CANDY, 2, poke = 200)),
        ),
        DungeonDef(
            "mt-steel", "Mt. Steel", WorldRegion.MOUNTAINS, floors = 10, favored = setOf(PokemonType.STEEL, PokemonType.ROCK, PokemonType.FIGHTING),
            baseFloorSeconds = 4320, needs = ExplorerRank.BRONZE,
            wild = listOf(WildEntry(74, 5), WildEntry(95, 3, fromFloor = 3), WildEntry(66, 4), WildEntry(227, 1, fromFloor = 7), WildEntry(81, 3), WildEntry(208, 1, fromFloor = 8)),
            milestones = mapOf(5 to MilestoneReward(Items.berry("spicy"), 2), 10 to MilestoneReward(Items.RARE_CANDY, 3, poke = 400)),
        ),
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): DungeonDef? = byId[id]
}
