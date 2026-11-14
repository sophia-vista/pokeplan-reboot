package app.pokeplan.world

import app.pokeplan.core.pokemon.Items

/**
 * The long-term tracks (docs/pokelife/MODULES/WORLD.md section 14). Each is a small, optional bonus that never changes how long a floor
 * takes (so offline progress stays the same however often the app settles); all numbers are *(tune)*.
 */

/** A skill levels by use: [xpFor] says what raises it, and each level gives [perLevel] of [bonus]. Level 10 is the top. */
enum class WorldSkill(val label: String, val blurb: String, val bonus: String) {
    EXPLORATION("Exploration", "Climbing floors", "2% more Poké per level"),
    FORAGING("Foraging", "Finding berries", "1% more chance of a berry per level"),
    SCOUTING("Scouting", "Spotting wild Pokémon", "0.3% more chance of a find per level"),
    ;

    companion object {
        const val MAX_LEVEL = 10

        /** The skill XP that reaches [level]: 30 × level². */
        fun xpFor(level: Int): Int = 30 * level * level

        fun levelAt(xp: Int): Int = (0..MAX_LEVEL).last { xpFor(it) <= xp }
    }
}

/** A base building bought with Poké, five levels each. The Café, Dojo, shop and storage are not built yet. */
enum class Facility(val label: String, val blurb: String, val bonus: String, val baseCost: Int) {
    CAMP("Camp", "Where recruited Pokémon live and attract treasure.", "1% more chance of a treasure box per level", 400),
    LAB("Research Lab", "Turns species data into permanent lessons.", "10% more Exploration XP per level", 600),
    ;

    companion object { const val MAX_LEVEL = 5 }

    /** The Poké cost of going from [level] to the next one: the base cost tripled for each level already built. */
    fun cost(level: Int): Int = baseCost * (1..level).fold(1) { a, _ -> a * 3 }
}

/** A research milestone: the number of species discovered exploring, and its reward. */
data class ResearchStep(val species: Int, val poke: Int, val itemId: String? = null, val count: Int = 0)

object Research {
    val steps: List<ResearchStep> = listOf(
        ResearchStep(3, 100), ResearchStep(6, 200, Items.berry("sweet"), 3), ResearchStep(10, 400), ResearchStep(15, 800, Items.RARE_CANDY, 1),
        ResearchStep(25, 1_500), ResearchStep(35, 3_000, Items.RARE_CANDY, 2), ResearchStep(45, 6_000, Items.RARE_CANDY, 3),
    )
}

/** Spinda's Café: three berries from the Bag make a brew that lasts [HOURS] hours: 30% more Exploration XP, 5% more treasure chance, 1% more find chance. *(tune)* */
object Cafe {
    const val HOURS = 3
    const val BERRIES = 3
}

/** The Dojo: repeatable training that turns Poké into skill XP. *(tune)* */
object Dojo {
    const val COST = 200
    const val SKILL_XP = 60
}

/** The Croagunk Swap Shop: [GIVE] berries of one flavour for one of another. *(tune)* */
object SwapShop {
    const val GIVE = 3
    val flavors = listOf("sweet", "dry", "sour", "spicy", "bitter")
}

/**
 * Abilities that help a skill (docs/pokelife/MODULES/WORLD.md section 14: skills tie to what a Pokémon can do; the Dex has abilities, not moves).
 * Each member of a team whose ability is on a skill's list counts as one level of that skill while the team explores, up to
 * [MAX_BONUS] levels (the skill's own level may go past 10 that way). *(tune)*
 */
object AbilityPerks {
    const val MAX_BONUS = 3

    val bySkill: Map<WorldSkill, Set<String>> = mapOf(
        WorldSkill.EXPLORATION to setOf("Run Away", "Swift Swim", "Chlorophyll", "Sand Rush", "Slush Rush", "Speed Boost", "Unburden"),
        WorldSkill.FORAGING to setOf("Pickup", "Honey Gather", "Harvest", "Gluttony", "Cheek Pouch"),
        WorldSkill.SCOUTING to setOf("Keen Eye", "Illuminate", "Compound Eyes", "Frisk", "Anticipation", "Forewarn", "Telepathy"),
    )

    /** The levels [skill] gains from [abilities], one for each ability on its list, at most [MAX_BONUS]. */
    fun bonus(abilities: List<String>, skill: WorldSkill): Int = abilities.count { it in bySkill.getValue(skill) }.coerceAtMost(MAX_BONUS)
}
