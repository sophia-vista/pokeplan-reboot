package app.pokeplan.world

import app.pokeplan.core.pokemon.Items

enum class WorldSkill(val label: String, val blurb: String, val bonus: String) {
    EXPLORATION("Exploration", "Climbing floors", "2% more Poké per level"),
    FORAGING("Foraging", "Finding berries", "1% more chance of a berry per level"),
    SCOUTING("Scouting", "Spotting wild Pokémon", "0.3% more chance of a find per level"),
    ;

    companion object {
        const val MAX_LEVEL = 10

        fun xpFor(level: Int): Int = 30 * level * level

        fun levelAt(xp: Int): Int = (0..MAX_LEVEL).last { xpFor(it) <= xp }
    }
}

enum class Facility(val label: String, val blurb: String, val bonus: String, val baseCost: Int) {
    CAMP("Camp", "Where recruited Pokémon live and attract treasure.", "1% more chance of a treasure box per level", 400),
    LAB("Research Lab", "Turns species data into permanent lessons.", "10% more Exploration XP per level", 600),
    ;

    companion object { const val MAX_LEVEL = 5 }

    fun cost(level: Int): Int = baseCost * (1..level).fold(1) { a, _ -> a * 3 }
}

data class ResearchStep(val species: Int, val poke: Int, val itemId: String? = null, val count: Int = 0)

object Research {
    val steps: List<ResearchStep> = listOf(
        ResearchStep(3, 100), ResearchStep(6, 200, Items.berry("sweet"), 3), ResearchStep(10, 400), ResearchStep(15, 800, Items.RARE_CANDY, 1),
        ResearchStep(25, 1_500), ResearchStep(35, 3_000, Items.RARE_CANDY, 2), ResearchStep(45, 6_000, Items.RARE_CANDY, 3),
    )
}

object Cafe {
    const val HOURS = 3
    const val BERRIES = 3
}

object Dojo {
    const val COST = 200
    const val SKILL_XP = 60
}

object SwapShop {
    const val GIVE = 3
    val flavors = listOf("sweet", "dry", "sour", "spicy", "bitter")
}

object AbilityPerks {
    const val MAX_BONUS = 3

    val bySkill: Map<WorldSkill, Set<String>> = mapOf(
        WorldSkill.EXPLORATION to setOf("Run Away", "Swift Swim", "Chlorophyll", "Sand Rush", "Slush Rush", "Speed Boost", "Unburden"),
        WorldSkill.FORAGING to setOf("Pickup", "Honey Gather", "Harvest", "Gluttony", "Cheek Pouch"),
        WorldSkill.SCOUTING to setOf("Keen Eye", "Illuminate", "Compound Eyes", "Frisk", "Anticipation", "Forewarn", "Telepathy"),
    )

    fun bonus(abilities: List<String>, skill: WorldSkill): Int = abilities.count { it in bySkill.getValue(skill) }.coerceAtMost(MAX_BONUS)
}
