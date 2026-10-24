package app.pokeplan.ui

/** What is new in each version, shown once on Home after an update. A version with no entry shows no card. */
object WhatsNew {
    val entries: Map<String, List<String>> = mapOf(
        "0.9.0" to listOf(
            "Plan your hours: Planner, Day. Tap a task, then tap an hour; +15 and -15 fine-tune it.",
        ),
        "0.3.0" to listOf(
            "Dark mode: Settings, Colours (Match phone, Light or Dark).",
            "Search, sort and filter your PC boxes.",
            "A Help screen under Me, and a shiny buddy glints on Home.",
            "The Today and Focus widgets sit on your buddy's scene.",
        ),
        "0.2.0" to listOf(
            "Your buddy lives in a real scene now, with its own idle animation, its cry and emoji reactions.",
            "New widgets: the Buddy widget animates, the Today and Focus widgets look better, and Focus shows a live countdown.",
            "A pixel background that changes through the day, and a Pokédex numbered from 1 with a National tab.",
            "Star a Pokémon to keep it safe, and find shinies in the Shiny Dex.",
            "Sound and music sliders, quick-add chips, grouped bag and long-press shortcuts on the app icon.",
        ),
    )

    fun forVersion(version: String): List<String>? = entries[version]
}
