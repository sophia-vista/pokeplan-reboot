package app.pokeplan.ui.launcher

/**
 * The launcher's rules, with no UI and no Android: what the app list shows for a search, a category or the letter
 * scroller, and what the user's pins, hidden apps and moved apps do to it. Android fills [LauncherApp] from `LauncherApps`.
 * Everything here describes the apps installed on this device, so it stays on the device and is never synced.
 */

/** When the launcher saves battery: the town stands still, the weather and the clock's seconds stop, nothing blurs and it checks less often. */
enum class BatterySaver(val label: String) { OFF("Off"), AUTO("Automatic"), ON("Always") }

/** Whether battery saving is on, given the choice, the charge and whether the phone's own power saving is on. */
fun batterySaving(choice: BatterySaver, battery: BatteryStatus?, phoneSaving: Boolean): Boolean = when (choice) {
    BatterySaver.ON -> true
    BatterySaver.OFF -> false
    BatterySaver.AUTO -> phoneSaving || (battery != null && !battery.charging && battery.percent <= LOW_BATTERY)
}

/** The charge at or below which Automatic saves battery. */
const val LOW_BATTERY = 20

/** Where a widget goes: the top or the bottom of the home page, or the Today page. */
enum class WidgetArea(val label: String) { HOME_TOP("Top of the home page"), HOME_BOTTOM("Bottom of the home page"), TODAY("Today page") }

/** What a widget shows. [FOLDER] is the folder named by the slot's argument; the others take none. */
enum class WidgetKind(val label: String) {
    CLOCK("Date and time"), STATUS("Companion"), BUDDY("Buddy"), NAV("Shortcuts"), PINNED("Favorites"), OFTEN("Often used"), FOLDER("Folder"),
    ACTIONS("App buttons"), CALENDAR("Calendar"), AGENDA("Agenda and tasks"), FORECAST("Forecast"),
    /** A widget from another app, hosted by the launcher; the slot's argument is its Android widget id. */
    ANDROID("Android widget");

    /** Whether the widget has sizes: the apps widgets show 1, 2 or all rows, the calendar one week or the month, the agenda 3, 6 or all items, the forecast 0, 3 or 4 days, an Android widget its height times 1, 1.5 or 2. */
    val resizable: Boolean get() = this in setOf(PINNED, OFTEN, FOLDER, CALENDAR, AGENDA, FORECAST, ANDROID)
}

/** How much of itself a widget shows: a few rows, more, or everything. What that means depends on the widget (see [WidgetKind.resizable]). */
enum class WidgetSize(val label: String) { SMALL("Small"), MEDIUM("Medium"), LARGE("Large");
    fun next() = entries[(ordinal + 1) % entries.size]
}

/** One widget on a page: its [kind], in an [area], with a folder's name as [arg] for a folder widget, shown at a [size]. The order of the slots is the order on screen. */
data class WidgetSlot(val area: WidgetArea, val kind: WidgetKind, val arg: String = "", val size: WidgetSize = WidgetSize.LARGE) {
    /** The same widget whatever its size: the identity of a slot. */
    fun sameAs(o: WidgetSlot) = area == o.area && kind == o.kind && arg == o.arg
    /** What to call it: a folder widget by its folder. */
    val title: String get() = if (kind == WidgetKind.FOLDER) arg else kind.label
}

/** The widgets a launcher starts with: the date and time and the buddy's portrait on top, shortcuts, the often used apps, favorites and the folder and app-list buttons below, and the calendar and agenda on Today. */
fun defaultWidgets(clock: Boolean = true, status: Boolean = true): List<WidgetSlot> = buildList {
    if (clock) add(WidgetSlot(WidgetArea.HOME_TOP, WidgetKind.CLOCK))
    if (status) add(WidgetSlot(WidgetArea.HOME_TOP, WidgetKind.BUDDY))
    add(WidgetSlot(WidgetArea.HOME_BOTTOM, WidgetKind.NAV))
    add(WidgetSlot(WidgetArea.HOME_BOTTOM, WidgetKind.OFTEN))
    add(WidgetSlot(WidgetArea.HOME_BOTTOM, WidgetKind.PINNED))
    add(WidgetSlot(WidgetArea.HOME_BOTTOM, WidgetKind.ACTIONS))
    add(WidgetSlot(WidgetArea.TODAY, WidgetKind.CALENDAR))
    add(WidgetSlot(WidgetArea.TODAY, WidgetKind.AGENDA))
}

/** A folder the user made: a name and the keys of its apps, in the order they were added. */
data class Folder(val name: String, val apps: List<String> = emptyList()) {
    companion object { const val MAX_NAME = 24 }
}

/** A place on Earth the forecast is for. */
data class GeoPlace(val name: String, val latitude: Double, val longitude: Double)

/** An app is suggested once it has been opened this many times from the launcher. */
const val MIN_LAUNCHES = 3
private const val MAX_LAUNCHES = 60

/** The weather the town can have, drawn from the Mystery Dungeon effects (assets/weather). */
enum class Weather(val label: String) {
    CLEAR("Clear"), LIVE("Live forecast"), CLOUDS("Cloud shadows"), RAIN("Rain"), SNOW("Snow"), BLIZZARD("Blizzard"), SANDSTORM("Sandstorm"),
}

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
    /** An app's icon on the home page when it is not the app's own logo: a real game item (see [LauncherIcons]). */
    val icons: Map<String, String> = emptyMap(),
    /** Whether the town's Pokémon walk about (off: they stand still). */
    val walking: Boolean = true,
    /** How many Pokémon live in the town: 8, 16 or 24. */
    val townSize: Int = 24,
    /** Whether the status box (the companion and the collection) shows at the top of the home page. */
    val showStatus: Boolean = true,
    /** Whether dragging in from the right edge opens the app list. */
    val edgeDrag: Boolean = true,
    /** Whether swiping across the screen moves between the town and Today. */
    val pageSwipe: Boolean = true,
    /** Whether three quick taps send every Pokémon to the main screen. */
    val gatherTap: Boolean = true,
    /** Whether the box with the date, the time and the battery shows at the top of the home page. */
    val showClock: Boolean = true,
    /** The weather over the town: one of the Mystery Dungeon effects, or [Weather.LIVE], which follows the forecast for the chosen place (a clear sky without one). */
    val weather: Weather = Weather.LIVE,
    /** How many times each app was opened from the launcher, for the suggestions: counts only, never what was done in an app. */
    val launches: Map<String, Int> = emptyMap(),
    /** The user's folders, in order. */
    val folders: List<Folder> = emptyList(),
    /** Where the forecast is for; null until one is chosen. */
    val place: GeoPlace? = null,
    /** Whether temperatures show in degrees Fahrenheit (otherwise Celsius). */
    val fahrenheit: Boolean = false,
    /** When the launcher saves battery: never, on its own at 20% or lower (or when the phone's power saving is on), or always. */
    val batterySaver: BatterySaver = BatterySaver.AUTO,
    /** How the clock widget writes the date. */
    val dateStyle: DateStyle = DateStyle.SHORT,
    /** Whether the clock widget uses a 12-hour clock (AM and PM). */
    val hour12: Boolean = false,
    /** Whether the clock widget shows the seconds. */
    val showSeconds: Boolean = false,
    /** Whether the calendar widget's weeks start on Sunday (otherwise Monday). */
    val sundayFirst: Boolean = true,
    /** The widgets, top to bottom in each area; null until the user changes them, which shows [defaultWidgets]. */
    val layout: List<WidgetSlot>? = null,
    /** The widgets on the grid (docs: the Smart Launcher style canvas); null until the grid is first changed, which shows the old [widgets] laid out as a grid. */
    val grid: List<GridWidget>? = null,
) {
    /** The widgets as a grid: the user's, or the stacked layout laid out by [WidgetGrid.fromSlots]. */
    val gridWidgets: List<GridWidget> get() = grid ?: WidgetGrid.fromSlots(widgets)
    fun withGrid(next: List<GridWidget>) = copy(grid = next)

    /** The widgets on screen: the user's, or the starting set (the date and time and buddy boxes follow their old on/off choices). */
    val widgets: List<WidgetSlot> get() = layout ?: defaultWidgets(showClock, showStatus)
    fun widgetsIn(area: WidgetArea): List<WidgetSlot> = widgets.filter { it.area == area }
    private fun withWidgets(next: List<WidgetSlot>) = copy(layout = next)

    /** Adds a widget at the end of its area; a folder widget needs an existing folder, and no widget is added twice. */
    fun addWidget(slot: WidgetSlot): LauncherPrefs {
        if (slot.kind == WidgetKind.FOLDER && folders.none { it.name == slot.arg }) return this
        if (widgets.any { it.sameAs(slot) }) return this
        return withWidgets(widgets + slot)
    }

    fun removeWidget(slot: WidgetSlot): LauncherPrefs = withWidgets(widgets.filterNot { it.sameAs(slot) })

    fun resizeWidget(slot: WidgetSlot, size: WidgetSize): LauncherPrefs = withWidgets(widgets.map { if (it.sameAs(slot)) it.copy(size = size) else it })

    /** Moves a widget one place up (-1) or down (+1) among the widgets of its area. */
    fun moveWidget(slot: WidgetSlot, by: Int): LauncherPrefs {
        val inArea = widgetsIn(slot.area)
        val at = inArea.indexOfFirst { it.sameAs(slot) }
        val to = at + by
        if (at < 0) return this
        if (to !in inArea.indices) return moveAcross(inArea[at], by)
        val reordered = inArea.toMutableList().apply { val moved = removeAt(at); add(to, moved) }
        val queue = reordered.iterator()
        return withWidgets(widgets.map { if (it.area == slot.area) queue.next() else it })
    }

    /** Past the last widget at the top of the home page a widget goes to the bottom's first place, and past the first at the bottom to the top's last. The Today page is its own. */
    private fun moveAcross(slot: WidgetSlot, by: Int): LauncherPrefs {
        val to = when {
            slot.area == WidgetArea.HOME_TOP && by > 0 -> WidgetArea.HOME_BOTTOM
            slot.area == WidgetArea.HOME_BOTTOM && by < 0 -> WidgetArea.HOME_TOP
            else -> return this
        }
        val rest = widgets.filterNot { it.sameAs(slot) }
        val moved = slot.copy(area = to)
        val at = if (to == WidgetArea.HOME_BOTTOM) rest.indexOfFirst { it.area == WidgetArea.HOME_BOTTOM }.let { if (it < 0) rest.size else it } else rest.indexOfLast { it.area == WidgetArea.HOME_TOP } + 1
        return withWidgets(rest.toMutableList().apply { add(at, moved) })
    }

    fun withWalking(on: Boolean) = copy(walking = on)
    fun withShowStatus(on: Boolean) = copy(showStatus = on)
    fun withEdgeDrag(on: Boolean) = copy(edgeDrag = on)
    fun nextBatterySaver() = copy(batterySaver = BatterySaver.entries[(batterySaver.ordinal + 1) % BatterySaver.entries.size])
    fun nextDateStyle() = copy(dateStyle = dateStyle.next())
    fun withHour12(on: Boolean) = copy(hour12 = on)
    fun withShowSeconds(on: Boolean) = copy(showSeconds = on)
    fun withSundayFirst(on: Boolean) = copy(sundayFirst = on)
    fun withPageSwipe(on: Boolean) = copy(pageSwipe = on)
    fun withGatherTap(on: Boolean) = copy(gatherTap = on)
    fun withShowClock(on: Boolean) = copy(showClock = on)
    fun withPlace(place: GeoPlace?) = copy(place = place)
    fun withFahrenheit(on: Boolean) = copy(fahrenheit = on)

    /** Counts an opening of [key]; when one count passes [MAX_LAUNCHES] they are all halved, so what you use now outweighs what you used long ago. */
    fun recordLaunch(key: String): LauncherPrefs {
        val next = launches + (key to (launches[key] ?: 0) + 1)
        return copy(launches = if (next.values.max() > MAX_LAUNCHES) next.mapValues { it.value / 2 }.filterValues { it > 0 } else next)
    }

    /** The apps opened most, not pinned or hidden, opened at least [MIN_LAUNCHES] times, most first. */
    fun suggested(apps: List<LauncherApp>, limit: Int = 4): List<LauncherApp> =
        apps.filter { it.key !in pinned && it.key !in hidden && (launches[it.key] ?: 0) >= MIN_LAUNCHES }.sortedWith(compareByDescending<LauncherApp> { launches[it.key] ?: 0 }.thenBy { foldedName(it.label) }).take(limit)

    /** A new folder called [name] (trimmed, 1 to [Folder.MAX_NAME] characters, not the name of another); this prefs unchanged when it cannot. */
    fun addFolder(name: String): LauncherPrefs {
        val clean = name.trim().take(Folder.MAX_NAME)
        return if (clean.isEmpty() || folders.any { it.name.equals(clean, ignoreCase = true) }) this else copy(folders = folders + Folder(clean))
    }

    /** A new folder and its widget at the bottom of the home page, together; the apps are added from the long-press sheet in All apps. */
    fun addFolderWidget(name: String): LauncherPrefs {
        val made = addFolder(name)
        return if (made == this) this else made.addWidget(WidgetSlot(WidgetArea.HOME_BOTTOM, WidgetKind.FOLDER, name.trim().take(Folder.MAX_NAME)))
    }

    fun renameFolder(old: String, name: String): LauncherPrefs {
        val clean = name.trim().take(Folder.MAX_NAME)
        if (clean.isEmpty() || folders.any { it.name.equals(clean, ignoreCase = true) && it.name != old }) return this
        return copy(folders = folders.map { if (it.name == old) it.copy(name = clean) else it }, layout = layout?.map { if (it.kind == WidgetKind.FOLDER && it.arg == old) it.copy(arg = clean) else it })
    }

    fun deleteFolder(name: String) = copy(folders = folders.filter { it.name != name }, layout = layout?.filterNot { it.kind == WidgetKind.FOLDER && it.arg == name })
    fun addToFolder(name: String, key: String) = copy(folders = folders.map { if (it.name == name && key !in it.apps) it.copy(apps = it.apps + key) else it })
    fun removeFromFolder(name: String, key: String) = copy(folders = folders.map { if (it.name == name) it.copy(apps = it.apps - key) else it })

    fun nextWeather() = copy(weather = Weather.entries[(weather.ordinal + 1) % Weather.entries.size])
    /** The next town size: 8, then 16, then 24, then 8 again. */
    fun nextTownSize() = copy(townSize = when (townSize) { 8 -> 16; 16 -> 24; else -> 8 })
    /** Pins and their icons forgotten; hidden apps and the other choices kept. */
    fun withoutPins() = copy(pinned = emptyList(), icons = emptyMap())

    fun withIcon(key: String, item: String?) = if (item == null) copy(icons = icons - key) else if (item in LauncherIcons.items) copy(icons = icons + (key to item)) else this

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

    /** One text for a `SharedPreferences` string: a line per choice, `P key`, `H key`, `M key<TAB>CATEGORY`, `I key<TAB>item` or `S scene`. */
    fun encode(): String = buildString {
        if (scene != TownScenes.DEFAULT) append("S ").append(scene).append('\n')
        if (!walking) append("K walking\t0\n")
        if (townSize != 24) append("K townSize\t").append(townSize).append('\n')
        if (!showStatus) append("K showStatus\t0\n")
        if (!edgeDrag) append("K edgeDrag\t0\n")
        if (batterySaver != BatterySaver.AUTO) append("K batterySaver\t").append(batterySaver.name).append('\n')
        if (dateStyle != DateStyle.SHORT) append("K dateStyle\t").append(dateStyle.name).append('\n')
        if (hour12) append("K hour12\t1\n")
        if (showSeconds) append("K showSeconds\t1\n")
        if (!sundayFirst) append("K sundayFirst\t0\n")
        if (!pageSwipe) append("K pageSwipe\t0\n")
        if (!gatherTap) append("K gatherTap\t0\n")
        if (!showClock) append("K showClock\t0\n")
        if (weather != Weather.LIVE) append("K weather\t").append(weather.name).append('\n')
        if (fahrenheit) append("K fahrenheit\t1\n")
        place?.let { append("W ").append(it.name.replace('\t', ' ')).append('\t').append(it.latitude).append('\t').append(it.longitude).append('\n') }
        launches.entries.sortedBy { it.key }.forEach { (key, count) -> append("L ").append(key).append('\t').append(count).append('\n') }
        layout?.let { l -> if (l.isEmpty()) append("G -\n") else l.forEach { append("G ").append(it.area.name).append('\t').append(it.kind.name).append('\t').append(it.arg.replace('\t', ' ')).append('\t').append(it.size.name).append('\n') } }
        grid?.let { g -> if (g.isEmpty()) append("X -\n") else g.forEach { append("X ").append(it.page.name).append('\t').append(it.kind.name).append('\t').append(it.arg.replace('\t', ' ')).append('\t').append(it.col).append('\t').append(it.row).append('\t').append(it.w).append('\t').append(it.h).append('\n') } }
        folders.forEach { f -> append("F ").append(f.name.replace('\t', ' ')); f.apps.forEach { append('\t').append(it) }; append('\n') }
        pinned.forEach { append("P ").append(it).append('\n') }
        hidden.sorted().forEach { append("H ").append(it).append('\n') }
        moved.entries.sortedBy { it.key }.forEach { (key, category) -> append("M ").append(key).append('\t').append(category.name).append('\n') }
        icons.entries.sortedBy { it.key }.forEach { (key, item) -> append("I ").append(key).append('\t').append(item).append('\n') }
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
                    'K' -> rest.split('\t').let { f -> if (f.size != 2) prefs else when (f[0]) { "walking" -> prefs.withWalking(f[1] != "0"); "showStatus" -> prefs.withShowStatus(f[1] != "0"); "edgeDrag" -> prefs.withEdgeDrag(f[1] != "0"); "weather" -> prefs.copy(weather = Weather.entries.firstOrNull { it.name == f[1] } ?: Weather.LIVE); "fahrenheit" -> prefs.withFahrenheit(f[1] != "0"); "pageSwipe" -> prefs.withPageSwipe(f[1] != "0"); "batterySaver" -> prefs.copy(batterySaver = BatterySaver.entries.firstOrNull { it.name == f[1] } ?: BatterySaver.AUTO); "dateStyle" -> prefs.copy(dateStyle = DateStyle.entries.firstOrNull { it.name == f[1] } ?: DateStyle.SHORT); "hour12" -> prefs.withHour12(f[1] != "0"); "showSeconds" -> prefs.withShowSeconds(f[1] != "0"); "sundayFirst" -> prefs.withSundayFirst(f[1] != "0"); "gatherTap" -> prefs.withGatherTap(f[1] != "0"); "showClock" -> prefs.withShowClock(f[1] != "0"); "townSize" -> prefs.copy(townSize = f[1].toIntOrNull()?.takeIf { it in listOf(8, 16, 24) } ?: 24); else -> prefs } }
                    'W' -> rest.split('\t').let { f -> val lat = f.getOrNull(1)?.toDoubleOrNull(); val lon = f.getOrNull(2)?.toDoubleOrNull(); if (f.size == 3 && f[0].isNotBlank() && lat != null && lon != null) prefs.withPlace(GeoPlace(f[0], lat, lon)) else prefs }
                    'L' -> rest.lastIndexOf('\t').let { at -> val count = if (at > 0) rest.substring(at + 1).toIntOrNull() else null; if (count != null && count > 0) prefs.copy(launches = prefs.launches + (rest.substring(0, at) to count)) else prefs }
                    'F' -> rest.split('\t').let { f -> if (f[0].isBlank()) prefs else prefs.addFolder(f[0]).let { made -> f.drop(1).filter { it.isNotBlank() }.fold(made) { acc, key -> acc.addToFolder(f[0].trim().take(Folder.MAX_NAME), key) } } }
                    'G' -> if (rest == "-") prefs.copy(layout = prefs.layout ?: emptyList()) else rest.split('\t').let { f ->
                        val area = WidgetArea.entries.firstOrNull { it.name == f[0] }
                        val kind = f.getOrNull(1)?.let { n -> WidgetKind.entries.firstOrNull { it.name == n } }
                        if (area == null || kind == null) prefs else prefs.copy(layout = (prefs.layout ?: emptyList()) + WidgetSlot(area, kind, f.getOrElse(2) { "" }, WidgetSize.entries.firstOrNull { it.name == f.getOrNull(3) } ?: WidgetSize.LARGE))
                    }
                    'X' -> if (rest == "-") prefs.copy(grid = prefs.grid ?: emptyList()) else rest.split('\t').let { f ->
                        val page = WidgetPage.entries.firstOrNull { it.name == f[0] }
                        val kind = f.getOrNull(1)?.let { n -> WidgetKind.entries.firstOrNull { it.name == n } }
                        val n = f.drop(3).map { it.toIntOrNull() }
                        if (page == null || kind == null || n.size != 4 || n.any { it == null }) prefs
                        else prefs.copy(grid = (prefs.grid ?: emptyList()) + GridWidget(page, kind, f.getOrElse(2) { "" }, n[0]!!, n[1]!!, n[2]!!, n[3]!!))
                    }
                    'I' -> rest.lastIndexOf('\t').let { at -> if (at > 0) prefs.withIcon(rest.substring(0, at), rest.substring(at + 1)) else prefs }
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

/** The game items an app's icon can be set to on the home page (real item sprites from assets/items). */
/** The item pictures cut from the Mystery Dungeon 2 complete items sheet (assets/pmd-items, `pmd2-001` to `pmd2-NNN`, listed in its manifest). */
object MysteryDungeonItems {
    const val COUNT = 214
    val names: List<String> = (1..COUNT).map { "pmd2-" + it.toString().padStart(3, '0') }
}

object LauncherIcons {
    val items = listOf(
        "poke-ball", "great-ball", "master-ball", "premier-ball", "timer-ball", "friend-ball", "rare-candy", "super-candy",
        "amulet-coin", "exp-share", "lucky-egg", "mystery-egg", "town-map", "explorer-kit", "member-card", "storage-key",
        "seal-bag", "oaks-letter", "soothe-bell", "gb-sounds", "moon-stone", "sun-stone", "fire-stone", "water-stone",
        "leaf-stone", "thunder-stone", "dusk-stone", "dawn-stone", "shiny-stone", "ice-stone", "berry-sweet", "ability-capsule",
    ) + MysteryDungeonItems.names

    /** The icon names that can be picked, those of the Pokémon games' bag first and then the Mystery Dungeon 2 items sheet. */
    val all: List<String> get() = items
}
