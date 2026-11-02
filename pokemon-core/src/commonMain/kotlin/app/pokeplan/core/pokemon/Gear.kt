package app.pokeplan.core.pokemon

/**
 * The Pokégear's tools (docs/ecosystem.md, "Pokégear: utilities and helper Pokémon"). Each tool always works; a helper
 * Pokémon is flavour only: its sprite beside the tool and one reaction when the tool is used. A Pokémon can help a tool
 * if it is in the tool's [signature] line (the species ids, regional forms sharing their species). Learning the tool's
 * [move] would count too, but the Dex data has no moves yet, so for now only the signature line does. A tool never pays
 * anything: it records no effort, candy, item, egg or rank point.
 *
 * [loaner] is the iconic member of the line that a Ranger request lends when the player owns no one who can help.
 */
enum class GearTool(val label: String, val blurb: String, val icon: String, val signature: Set<Int>, val loaner: Int, val move: String) {
    FLASHLIGHT("Flashlight", "Light for the dark", "thunder-stone", setOf(179, 180, 181), 181, "Flash"),
    CALCULATOR("Calculator", "Sums, percentages and everyday maths", "link-cord", setOf(137, 233, 474), 137, "Conversion"),
    CURRENCY("Currency", "Convert money at today's rates", "amulet-coin", setOf(52, 53, 863), 52, "Pay Day"),
    PICKER("Picker", "Pick one thing from a list", "star-piece", setOf(173, 35, 36), 35, "Metronome"),
    COIN("Coin and dice", "Heads or tails, and dice", "bottle-cap", setOf(999, 1000), 999, "Make It Rain"),

    // Added with the utility suite (0.107.0): their names are saved with the helpers, so they are only ever added at the end.
    UNITS("Units", "Convert lengths, weights and more", "scope-lens", setOf(81, 82, 462), 81, "Magnet Rise"),
    TIMER("Timer", "Count down to the moment", "timer-ball", setOf(358, 433), 433, "Perish Song"),
    STOPWATCH("Stopwatch", "Time it, with laps", "exp-share", setOf(290, 291), 291, "Quick Attack"),

    // Added with the specialist toolbox (0.110.0).
    EVERYDAY("Everyday sums", "Tips, discounts, loans and trip costs", "lucky-egg", setOf(113, 242, 440), 113, "Softboiled"),
    WORLD_CLOCK("World clock", "The time elsewhere, and date sums", "dawn-stone", setOf(163, 164), 163, "Foresight"),
    COMPASS("Compass", "Which way you are facing", "explorer-kit", setOf(21, 22), 21, "Fly"),
    LEVEL("Level", "Is it flat? Tilt in degrees", "moon-stone", setOf(436, 437), 436, "Gyro Ball"),
    BAROMETER("Barometer", "Air pressure and a rough height", "water-stone", setOf(351, 334), 351, "Weather Ball"),
    DEVICE("Device check", "What this phone can do for the Pokégear", "shiny-stone", setOf(479), 479, "Thunder Wave"),

    // Added with the field tools (0.117.0).
    WAYPOINTS("Waypoints", "Mark places and find your way back", "dusk-stone", setOf(16, 17, 18), 16, "Gust"),
    SOUND("Sound meter", "How loud it is, and the pitch", "gb-sounds", setOf(293, 294, 295), 293, "Uproar"),
    QR("QR codes", "Make a code, or scan one", "fire-stone", setOf(201), 201, "Hidden Power"),
    NETWORK("Network check", "Is the connection good?", "leaf-stone", setOf(125, 466), 125, "Charge");

    /** Whether a Pokémon of [speciesId] can help this tool. */
    fun helps(speciesId: Int): Boolean = speciesId in signature
}
