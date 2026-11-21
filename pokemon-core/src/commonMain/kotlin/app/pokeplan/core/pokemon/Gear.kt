package app.pokeplan.core.pokemon

enum class GearTool(val label: String, val icon: String, val signature: Set<Int>) {
    FLASHLIGHT("Flashlight", "thunder-stone", setOf(179, 180, 181)),
    CALCULATOR("Calculator", "link-cord", setOf(137, 233, 474)),
    CURRENCY("Currency", "amulet-coin", setOf(52, 53, 863)),
    PICKER("Picker", "star-piece", setOf(173, 35, 36)),
    COIN("Coin and dice", "bottle-cap", setOf(999, 1000)),

    UNITS("Units", "scope-lens", setOf(81, 82, 462)),
    TIMER("Timer", "timer-ball", setOf(358, 433)),
    STOPWATCH("Stopwatch", "exp-share", setOf(290, 291)),

    EVERYDAY("Everyday sums", "lucky-egg", setOf(113, 242, 440)),
    WORLD_CLOCK("World clock", "dawn-stone", setOf(163, 164)),
    COMPASS("Compass", "explorer-kit", setOf(21, 22)),
    LEVEL("Level", "moon-stone", setOf(436, 437)),
    BAROMETER("Barometer", "water-stone", setOf(351, 334)),
    DEVICE("Device check", "shiny-stone", setOf(479)),

    WAYPOINTS("Waypoints", "dusk-stone", setOf(16, 17, 18)),
    SOUND("Sound meter", "gb-sounds", setOf(293, 294, 295)),
    QR("QR codes", "fire-stone", setOf(201)),
    NETWORK("Network check", "leaf-stone", setOf(125, 466));

    fun helps(speciesId: Int): Boolean = speciesId in signature
}
