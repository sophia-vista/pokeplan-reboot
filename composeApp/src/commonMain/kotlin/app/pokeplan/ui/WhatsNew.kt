package app.pokeplan.ui

/** What is new in each version, shown once on Home after an update. A version with no entry shows no card. */
object WhatsNew {
    val entries: Map<String, List<String>> = mapOf(
        "0.19.4" to listOf(
            "Paging through the Pokédex with Next keeps every Pokémon animated, and tapping a Pokémon no longer flashes a grey box over it.",
        ),
        "0.19.2" to listOf(
            "Your buddy keeps its size when it hops or eats, and party rows no longer flash when tapped.",
        ),
        "0.19.1" to listOf(
            "A cleaner Calendar: a strip of days to jump between, one card of tasks to plan, and easier-to-read blocks on the hours.",
        ),
        "0.19.0" to listOf(
            "Easier to use: Back is at the top left, settings are switches in small groups, and Undo sits just above the tabs.",
            "Focus keeps Start at the bottom of the screen, and the keyboard's Done key adds a task.",
        ),
        "0.18.0" to listOf(
            "Eggs hatch and Pokémon evolve with a scene like in the games, and shinies flash gold.",
            "Every Pokédex species has its own page: category, size, abilities, base stats and its evolution line.",
        ),
        "0.17.0" to listOf(
            "Me, Growth: pick 7, 30 or 90 days, see tasks and focus minutes for each area, and your goals' progress on their own.",
        ),
        "0.16.1" to listOf(
            "Locking the screen or taking a call during Focus no longer loses your egg. The Focus screen now lists the rules.",
        ),
        "0.16.0" to listOf(
            "The Pokédex now shows which Pokémon you own, which you caught before, and which you have only seen on a friend's buddy or a quest.",
        ),
        "0.15.0" to listOf(
            "Your own events: Planner, Calendar, New event. Lessons, lunch and other things that aren't tasks sit on your hours, drag like tasks and never pay.",
        ),
        "0.14.0" to listOf(
            "Drag to plan: Planner, Calendar. Hold a task and drop it on an hour, drag its lower edge to change how long it takes, and switch to Week to plan seven days at once.",
        ),
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
