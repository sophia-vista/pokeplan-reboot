package app.pokeplan.core.dex

/**
 * Species the source data does not flag as legendary but that are treated as legendary-class:
 * Ultra Beasts and Paradox Pokémon. They are quest rewards, never egg drops.
 */
object SpecialSpecies {
    val ultraBeasts: Set<String> = setOf(
        "nihilego", "buzzwole", "pheromosa", "xurkitree", "celesteela", "kartana",
        "guzzlord", "poipole", "naganadel", "stakataka", "blacephalon",
    )

    val paradox: Set<String> = setOf(
        "great-tusk", "scream-tail", "brute-bonnet", "flutter-mane", "slither-wing", "sandy-shocks",
        "roaring-moon", "walking-wake", "gouging-fire", "raging-bolt",
        "iron-treads", "iron-bundle", "iron-hands", "iron-jugulis", "iron-moth", "iron-thorns",
        "iron-valiant", "iron-leaves", "iron-boulder", "iron-crown",
    )

    val legendaryClass: Set<String> = ultraBeasts + paradox
}
