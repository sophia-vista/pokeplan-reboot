package app.pokeplan.core.pokemon

/**
 * The Pokégear's tools (docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md, "Pokégear: utilities and helper Pokémon"). Each tool always works; a helper
 * Pokémon is flavour only: its sprite beside the tool and one reaction when the tool is used. A Pokémon can help a tool
 * if it is in the tool's [signature] line (the species ids, regional forms sharing their species). A tool never pays
 * anything: it records no effort, candy, item, egg or rank point.
 *
 * Loaner helpers (a Ranger request that lent a Pokémon who was never owned) were retired in 0.145.0: a tool shows its own icon until the
 * player chooses an owned Pokémon.
 */
enum class GearTool(val label: String, val icon: String, val signature: Set<Int>) {
    FLASHLIGHT("Flashlight", "thunder-stone", setOf(179, 180, 181)),
    CALCULATOR("Calculator", "link-cord", setOf(137, 233, 474)),
    CURRENCY("Currency", "amulet-coin", setOf(52, 53, 863)),
    PICKER("Picker", "star-piece", setOf(173, 35, 36)),
    COIN("Coin and dice", "bottle-cap", setOf(999, 1000)),

    // Added with the utility suite (0.107.0): their names are saved with the helpers, so they are only ever added at the end.
    UNITS("Units", "scope-lens", setOf(81, 82, 462)),
    TIMER("Timer", "timer-ball", setOf(358, 433)),
    STOPWATCH("Stopwatch", "exp-share", setOf(290, 291)),

    // Added with the specialist toolbox (0.110.0).
    EVERYDAY("Everyday sums", "lucky-egg", setOf(113, 242, 440)),
    WORLD_CLOCK("World clock", "dawn-stone", setOf(163, 164)),
    COMPASS("Compass", "explorer-kit", setOf(21, 22)),
    LEVEL("Level", "moon-stone", setOf(436, 437)),
    BAROMETER("Barometer", "water-stone", setOf(351, 334)),
    DEVICE("Device check", "shiny-stone", setOf(479)),

    // Added with the field tools (0.117.0).
    WAYPOINTS("Waypoints", "dusk-stone", setOf(16, 17, 18)),
    SOUND("Sound meter", "gb-sounds", setOf(293, 294, 295)),
    QR("QR codes", "fire-stone", setOf(201)),
    NETWORK("Network check", "leaf-stone", setOf(125, 466));

    /** Whether a Pokémon of [speciesId] can help this tool. */
    fun helps(speciesId: Int): Boolean = speciesId in signature
}
