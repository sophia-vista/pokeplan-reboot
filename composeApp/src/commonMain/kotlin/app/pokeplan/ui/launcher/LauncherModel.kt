package app.pokeplan.ui.launcher

/**
 * The launcher's rules, with no UI and no Android: what the app list shows for a search, a category or the letter
 * scroller, and what the user's pins, hidden apps and moved apps do to it. Android fills [LauncherApp] from `LauncherApps`.
 * Everything here describes the apps installed on this device, so it stays on the device and is never synced.
 */

/** The groups of Smart Launcher: the category an app declares (`ApplicationInfo.category`), with "Other" as the fallback. */
enum class AppCategory(val label: String, val icon: String) {
    GAMES("Games", "great-ball"),
    COMMUNICATION("Messages and calls", "gb-sounds"),
    SOCIAL("Social", "friend-ball"),
    MEDIA("Music, video and photos", "premier-ball"),
    PRODUCTIVITY("Productivity", "timer-ball"),
    FINANCE("Money", "amulet-coin"),
    HEALTH("Health and fitness", "rare-candy"),
    SHOPPING("Shopping", "master-ball"),
    TRAVEL("Maps and travel", "town-map"),
    NEWS("News", "soothe-bell"),
    TOOLS("Tools and system", "exp-share"),
    OTHER("Other", "poke-ball");

    companion object {
        /** From Android's `ApplicationInfo.CATEGORY_*` constants: 0 game, 1 audio, 2 video, 3 image, 4 social, 5 news, 6 maps, 7 productivity. */
        fun fromAndroid(category: Int): AppCategory = when (category) {
            0 -> GAMES
            1, 2, 3 -> MEDIA
            4 -> SOCIAL
            5 -> NEWS
            6 -> TRAVEL
            7 -> PRODUCTIVITY
            else -> OTHER
        }

        /** Words in an app's package name or name that place it, tried in this order; most apps declare no category, so this fills the gap. */
        private val keywords: List<Pair<AppCategory, List<String>>> = listOf(
            GAMES to listOf("game", "supercell", "niantic", "pokemon", "mojang", "minecraft", "roblox", "rovio", "ubisoft", "nintendo", "wordle", "chess"),
            COMMUNICATION to listOf("dialer", "contacts", "messag", "sms", "mms", "mail", "telegram", "whatsapp", "signal", "zoom", "teams", "meet", "duo", "viber", "skype", "phone"),
            SOCIAL to listOf("facebook", "instagram", "twitter", "tiktok", "snapchat", "reddit", "discord", "linkedin", "pinterest", "threads", "bluesky", "mastodon", "tumblr"),
            MEDIA to listOf("music", "spotify", "youtube", "netflix", "video", "photos", "gallery", "podcast", "radio", "twitch", "soundcloud", "disney", "camera", "player"),
            PRODUCTIVITY to listOf("docs", "sheets", "slides", "calendar", "notes", "office", "excel", "powerpoint", "notion", "todoist", "tasks", "onenote", "evernote", "pdf", "obsidian", "trello", "drive"),
            FINANCE to listOf("bank", "paypal", "gpay", "googlepay", "samsungpay", "revolut", "wallet", "finance", "invest", "crypto", "coinbase", "venmo", "cashapp", "monzo"),
            HEALTH to listOf("health", "fitness", "fitbit", "strava", "workout", "sleep", "meditat", "yoga", "calm", "steps"),
            SHOPPING to listOf("amazon", "shop", "ebay", "aliexpress", "temu", "shein", "etsy", "walmart", "lazada"),
            TRAVEL to listOf("maps", "uber", "lyft", "grab", "taxi", "transit", "airbnb", "booking", "flight", "travel", "waze", "navigation", "expedia"),
            NEWS to listOf("news", "bbc", "cnn", "reuters", "guardian"),
            TOOLS to listOf("settings", "calculator", "clock", "files", "filemanager", "browser", "chrome", "firefox", "brave", "vending", "weather", "flashlight", "compass", "translate", "vpn", "wallpaper", "keyboard", "authenticator", "password"),
        )

        /**
         * An app's category: the one it declares ([declared], Android's constant), else the first group whose words appear in
         * its [packageName] or [label], else Tools and system for a [system] app and Other for the rest. The user can always move it.
         */
        fun guess(declared: Int, packageName: String, label: String, system: Boolean): AppCategory {
            fromAndroid(declared).takeIf { it != OTHER }?.let { return it }
            val text = "$packageName $label".lowercase()
            keywords.firstOrNull { (_, words) -> words.any { it in text } }?.let { return it.first }
            return if (system) TOOLS else OTHER
        }
    }
}

/**
 * One launchable app. [key] names it uniquely (its component and, for a work profile, the profile) and is what pins and
 * hidden apps remember. [removable] is false for system apps, which have no uninstall.
 */
data class LauncherApp(val key: String, val label: String, val category: AppCategory = AppCategory.OTHER, val removable: Boolean = true)

/** The user's choices, remembered on the device: pinned apps in the order pinned, hidden apps, and apps moved to another category. */
data class LauncherPrefs(
    val pinned: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val moved: Map<String, AppCategory> = emptyMap(),
    /** The scene the home page's town is set in (a name from [TownScenes]). */
    val scene: String = TownScenes.DEFAULT,
) {
    fun withScene(name: String) = if (name in TownScenes.all.map { it.first }) copy(scene = name) else this

    fun pin(key: String) = if (key in pinned) this else copy(pinned = pinned + key)
    fun unpin(key: String) = copy(pinned = pinned - key)
    fun hide(key: String) = copy(hidden = hidden + key, pinned = pinned - key)
    fun unhide(key: String) = copy(hidden = hidden - key)

    /** Moves an app to [category]; moving it back to the category it declares forgets the move. */
    fun move(app: LauncherApp, category: AppCategory) =
        copy(moved = if (category == app.category) moved - app.key else moved + (app.key to category))

    /** An app's category after the user's moves. */
    fun categoryOf(app: LauncherApp): AppCategory = moved[app.key] ?: app.category

    /** One text for a `SharedPreferences` string: a line per choice, `P key`, `H key`, `M key<TAB>CATEGORY` or `S scene`. */
    fun encode(): String = buildString {
        if (scene != TownScenes.DEFAULT) append("S ").append(scene).append('\n')
        pinned.forEach { append("P ").append(it).append('\n') }
        hidden.sorted().forEach { append("H ").append(it).append('\n') }
        moved.entries.sortedBy { it.key }.forEach { (key, category) -> append("M ").append(key).append('\t').append(category.name).append('\n') }
    }

    companion object {
        /** Reads [encode]'s text back; a damaged or unknown line is skipped, so a bad file never stops the launcher. */
        fun decode(text: String?): LauncherPrefs {
            var prefs = LauncherPrefs()
            text.orEmpty().lineSequence().forEach { line ->
                if (line.length < 3 || line[1] != ' ') return@forEach
                val rest = line.substring(2)
                prefs = when (line[0]) {
                    'P' -> prefs.pin(rest)
                    'H' -> prefs.copy(hidden = prefs.hidden + rest)
                    'S' -> prefs.withScene(rest)
                    'M' -> {
                        val at = rest.lastIndexOf('\t')
                        val category = if (at > 0) AppCategory.entries.firstOrNull { it.name == rest.substring(at + 1) } else null
                        if (category == null) prefs else prefs.copy(moved = prefs.moved + (rest.substring(0, at) to category))
                    }
                    else -> prefs
                }
            }
            return prefs
        }
    }
}

private const val FROM = "àáâãäåçèéêëìíîïñòóôõöøùúûüýÿ"
private const val TO = "aaaaaaceeeeiiiinoooooouuuuyy"

/** A name in lower case with the common Latin accents removed, so "Äpfel" files under A and sorts among the As. */
internal fun foldedName(label: String): String = label.trim().lowercase().map { c -> FROM.indexOf(c).let { if (it >= 0) TO[it] else c } }.joinToString("")

/** The letter an app files under in the A to Z list: its first letter, upper case, or `#` for a digit or a symbol. */
fun LauncherApp.letter(): Char = foldedName(label).firstOrNull()?.takeIf { it.isLetter() }?.uppercaseChar() ?: '#'

/** What the app list shows. */
object LauncherList {
    /** A to Z by name, ignoring case; names that start with a digit or a symbol come first, under `#`. */
    val byName: Comparator<LauncherApp> = compareBy<LauncherApp> { it.letter() != '#' }.thenBy { foldedName(it.label) }.thenBy { it.key }

    /**
     * The apps to list: not the hidden ones (unless [hidden] asks for exactly those), filtered by a [query] on the name
     * (names that start with it first) or by [category], A to Z.
     */
    fun visible(apps: List<LauncherApp>, prefs: LauncherPrefs, query: String = "", category: AppCategory? = null, hidden: Boolean = false): List<LauncherApp> {
        val q = foldedName(query)
        val shown = apps.filter { (it.key in prefs.hidden) == hidden && (category == null || prefs.categoryOf(it) == category) }
        if (q.isEmpty()) return shown.sortedWith(byName)
        return shown.filter { app -> foldedName(app.label).contains(q) }
            .sortedWith(compareBy<LauncherApp> { !foldedName(it.label).startsWith(q) }.then(byName))
    }

    /** The pinned apps that are still installed, in the order they were pinned. */
    fun pinned(apps: List<LauncherApp>, prefs: LauncherPrefs): List<LauncherApp> {
        val byKey = apps.associateBy { it.key }
        return prefs.pinned.mapNotNull { byKey[it] }.filter { it.key !in prefs.hidden }
    }

    /** The categories that have an app, in [AppCategory] order, with how many each holds. */
    fun categories(apps: List<LauncherApp>, prefs: LauncherPrefs): List<Pair<AppCategory, Int>> {
        val shown = apps.filter { it.key !in prefs.hidden }
        return AppCategory.entries.mapNotNull { c -> shown.count { prefs.categoryOf(it) == c }.takeIf { it > 0 }?.let { c to it } }
    }

    /** The letters the list has, in order, each with the index of its first app: what the letter scroller jumps to. */
    fun letters(sorted: List<LauncherApp>): List<Pair<Char, Int>> {
        val out = ArrayList<Pair<Char, Int>>()
        sorted.forEachIndexed { i, app -> if (out.lastOrNull()?.first != app.letter()) out += app.letter() to i }
        return out
    }
}

/** The scenes a town can be set in (real Pokémon backdrops from assets/scenes), with their names. */
object TownScenes {
    const val DEFAULT = "meadow"
    val scenes = listOf(
        "meadow" to "Meadow", "city" to "City", "route" to "Route", "forest" to "Forest", "mountain" to "Mountain", "beach" to "Beach",
        "desert" to "Desert", "thunderplains" to "Thunder plains", "icecave" to "Ice cave", "dampcave" to "Damp cave", "volcanocave" to "Volcano cave",
    )

    /** Towns built from the tiles of Mystery Dungeon dungeons (assets/tiles), by atlas name. */
    val dungeons = listOf(
        "lush-prairie" to "Lush Prairie", "apple-woods" to "Apple Woods", "dusk-forest" to "Dusk Forest", "deep-dusk-forest" to "Deep Dusk Forest",
        "deep-dusk-forest-autumn" to "Deep Dusk Forest (autumn)", "drenched-bluff" to "Drenched Bluff", "craggy-coast" to "Craggy Coast", "beach-cave" to "Beach Cave",
        "brine-cave" to "Brine Cave", "crystal-cave" to "Crystal Cave", "crystal-cave-deep" to "Crystal Cave (deep)", "crystal-crossing" to "Crystal Crossing",
        "dark-hill" to "Dark Hill", "dark-hill-deep" to "Dark Hill (deep)", "deep-sealed-ruin" to "Deep Sealed Ruin", "concealed-ruins" to "Concealed Ruins", "amp-plains" to "Amp Plains",
    )

    val all = scenes + dungeons

    fun isDungeon(name: String) = dungeons.any { it.first == name }
}
