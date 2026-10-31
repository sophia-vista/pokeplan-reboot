package app.pokeplan.world

import app.pokeplan.core.pokemon.Items

/**
 * The long-term tracks (docs/world.md, sections 10, 12 and 13). Each is a small, optional bonus that never changes how long a floor
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
        ResearchStep(25, 1_500), ResearchStep(40, 3_000, Items.RARE_CANDY, 2), ResearchStep(60, 6_000, Items.RARE_CANDY, 3),
    )
}
