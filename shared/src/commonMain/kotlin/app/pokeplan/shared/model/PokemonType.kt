package app.pokeplan.shared.model

enum class PokemonType {
    NORMAL, FIRE, WATER, GRASS, ELECTRIC, ICE, FIGHTING, POISON, GROUND, FLYING,
    PSYCHIC, BUG, ROCK, GHOST, DRAGON, DARK, STEEL, FAIRY;

    val label: String get() = name.lowercase().replaceFirstChar { it.uppercase() }
}
