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
    CALCULATOR("Calculator", "Sums and unit conversions", "link-cord", setOf(137, 233, 474), 137, "Conversion"),
    CURRENCY("Currency", "Convert money at today's rates", "amulet-coin", setOf(52, 53, 863), 52, "Pay Day"),
    PICKER("Picker", "Pick one thing from a list", "star-piece", setOf(173, 35, 36), 35, "Metronome"),
    COIN("Coin toss", "Heads or tails", "bottle-cap", setOf(999, 1000), 999, "Make It Rain");

    /** Whether a Pokémon of [speciesId] can help this tool. */
    fun helps(speciesId: Int): Boolean = speciesId in signature
}
