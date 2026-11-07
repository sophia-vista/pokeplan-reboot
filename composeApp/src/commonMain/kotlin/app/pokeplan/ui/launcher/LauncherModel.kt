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
    /** What each gesture does when the user chose something other than its usual job (see [action]). */
    val gestures: Map<GestureTrigger, GestureAction> = emptyMap(),
    /** Draws the widgets on the grid (the Smart Launcher style canvas: drag, resize, half widths) instead of the old stacked list; on since 0.93.0, and the switch in Launcher settings goes back to the list (docs/launcher-widget-grid.md). */
    val gridMode: Boolean = true,
    /**
     * The pages from left to right: always the home page and Today, and up to three of the user's own ([WidgetPage]), each of which can
     * stand left of Home or after Today; only the grid has more than two. Starts as Home, Today.
     */
    val pageOrder: List<WidgetPage> = WidgetPage.entries.take(WidgetPage.MIN),
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
        // Once the grid has been changed it is the layout that counts, so a new widget goes in its first free spot there too.
        val onGrid = grid?.let { g ->
            val l = slot.kind.limits()
            WidgetGrid.add(g, GridWidget(if (slot.area == WidgetArea.TODAY) WidgetPage.TODAY else WidgetPage.HOME, slot.kind, slot.arg, 0, 0, l.defW, l.defH))
        }
        return withWidgets(widgets + slot).let { if (onGrid != null) it.withGrid(onGrid) else it }
    }

    fun removeWidget(slot: WidgetSlot): LauncherPrefs = withWidgets(widgets.filterNot { it.sameAs(slot) }).let { p ->
        p.grid?.let { g -> p.withGrid(g.filterNot { it.kind == slot.kind && it.arg == slot.arg && (it.page == WidgetPage.TODAY) == (slot.area == WidgetArea.TODAY) }) } ?: p
    }

    /** The grid with [widget] removed, and its layout twin too, so the two never disagree about what is on screen. */
    fun removeGridWidget(widget: GridWidget): LauncherPrefs {
        val base = WidgetGrid.remove(grid ?: gridWidgets, widget)
        val twin = widgets.firstOrNull { it.kind == widget.kind && it.arg == widget.arg && (it.area == WidgetArea.TODAY) == (widget.page == WidgetPage.TODAY) }
        return (if (twin != null) withWidgets(widgets.filterNot { it.sameAs(twin) }) else this).withGrid(base)
    }

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

    /** What [trigger] does: the user's choice, or its usual job (the old on/off switches still count: off is nothing). */
    fun action(trigger: GestureTrigger): GestureAction = gestures[trigger] ?: when (trigger) {
        GestureTrigger.EDGE_DRAG -> if (edgeDrag) GestureAction.APPS else GestureAction.NONE
        GestureTrigger.TRIPLE_TAP -> if (gatherTap) GestureAction.GATHER else GestureAction.NONE
        GestureTrigger.DOUBLE_TAP -> GestureAction.NONE
    }

    /** Makes [trigger] do [action] (only what the trigger can do: the edge drag is the one that follows the finger, so it can open the app list). */
    fun withGesture(trigger: GestureTrigger, action: GestureAction): LauncherPrefs =
        if (action in trigger.actions) copy(gestures = gestures + (trigger to action)) else this
    fun withGridMode(on: Boolean) = copy(gridMode = on)
    /** How many pages there are. */
    val pages: Int get() = pageOrder.size

    /** Grows to [n] pages (new ones at the right end) or shrinks to [n] (the user's own pages from the right end); always between [WidgetPage.MIN] and [WidgetPage.MAX]. */
    fun withPages(n: Int): LauncherPrefs {
        val want = n.coerceIn(WidgetPage.MIN, WidgetPage.MAX)
        var order = pageOrder
        while (order.size < want) order = order + (WidgetPage.entries.first { it !in order })
        while (order.size > want) order = order - order.last { it.ordinal >= WidgetPage.MIN }
        return copy(pageOrder = order)
    }

    /** The pages in order, as many as there are. */
    val pageList: List<WidgetPage> get() = pageOrder

    /** Where Home stands in [pageList]: the number of pages to its left. */
    val homeIndex: Int get() = pageOrder.indexOf(WidgetPage.HOME)

    /** Adds a page of the user's own, at the left of Home's neighbours ([left]: the far left end) or the right end; not past [WidgetPage.MAX]. */
    fun addPage(left: Boolean): LauncherPrefs {
        if (pageOrder.size >= WidgetPage.MAX) return this
        val fresh = WidgetPage.entries.first { it !in pageOrder }
        return copy(pageOrder = if (left) listOf(fresh) + pageOrder else pageOrder + fresh)
    }

    /** Whether [page] can be removed: any page of the user's own, empty or not (Home and Today stay). */
    fun canDeletePage(page: WidgetPage): Boolean = page in pageOrder && page.ordinal >= WidgetPage.MIN

    /** Removes [page] when [canDeletePage]; what is on it moves to the page next to it (the one before, or the one after for the first), so nothing is lost. */
    fun deletePage(page: WidgetPage): LauncherPrefs {
        if (!canDeletePage(page)) return this
        val at = pageOrder.indexOf(page)
        val heir = pageOrder[if (at > 0) at - 1 else at + 1]
        var next = this
        WidgetGrid.onPage(gridWidgets, page).forEach { next = next.moveGridWidgetToPage(it, heir) }
        return next.copy(pageOrder = next.pageOrder - page)
    }

    private fun areaOf(page: WidgetPage) = if (page == WidgetPage.TODAY) WidgetArea.TODAY else WidgetArea.HOME_BOTTOM

    /**
     * Adds a widget to [page], in its first free spot (once only: a widget is on one page). On Home and Today the old stacked layout
     * learns of it too, so switching the grid off shows it; a widget on a page of the user's own lives only on the grid.
     */
    fun addGridWidget(page: WidgetPage, kind: WidgetKind, arg: String = ""): LauncherPrefs {
        if (gridWidgets.any { it.kind == kind && it.arg == arg }) return this
        if (kind == WidgetKind.FOLDER && folders.none { it.name == arg }) return this
        val base = withGrid(gridWidgets)
        if (page == WidgetPage.HOME || page == WidgetPage.TODAY) return base.addWidget(WidgetSlot(areaOf(page), kind, arg))
        val l = kind.limits()
        return base.withGrid(WidgetGrid.add(base.grid!!, GridWidget(page, kind, arg, 0, 0, l.defW, l.defH)))
    }

    /** Moves a widget to another page (making the page first when [page] is past the last one), keeping the old layout's twin of it in step. */
    fun moveGridWidgetToPage(widget: GridWidget, page: WidgetPage): LauncherPrefs {
        val grown = if (page !in pageOrder) copy(pageOrder = pageOrder + page) else this
        val base = grown.withGrid(WidgetGrid.toPage(grown.gridWidgets, widget, page))
        val twin = base.widgets.firstOrNull { it.kind == widget.kind && it.arg == widget.arg }
        return when {
            twin == null && (page == WidgetPage.HOME || page == WidgetPage.TODAY) -> base.copy(layout = base.widgets + WidgetSlot(areaOf(page), widget.kind, widget.arg))
            twin == null -> base
            page == WidgetPage.HOME || page == WidgetPage.TODAY -> base.copy(layout = base.widgets.map { if (it.sameAs(twin)) it.copy(area = if (page == WidgetPage.TODAY) WidgetArea.TODAY else if (twin.area == WidgetArea.TODAY) WidgetArea.HOME_BOTTOM else twin.area) else it })
            else -> base.copy(layout = base.widgets.filterNot { it.sameAs(twin) })
        }
    }

    /** Whether nothing stands on [page] (a page of the user's own can then be deleted). */
    fun pageIsEmpty(page: WidgetPage) = WidgetGrid.onPage(gridWidgets, page).isEmpty()

    /** Takes away the last page when it is one of the user's own and empty. */
    fun deleteLastPage(): LauncherPrefs = deletePage(pageOrder.last())
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
        gestures.entries.sortedBy { it.key.ordinal }.forEach { (t, a) -> append("K gesture.").append(t.name).append('\t').append(a.name).append('\n') }
        if (!gridMode) append("K gridMode\t0\n")
        // The usual order (Home, Today, then Page 3 and on) is saved as a count, as it always was; any other order by name.
        if (pageOrder != WidgetPage.entries.take(pageOrder.size)) append("K pageOrder\t").append(pageOrder.joinToString(",") { it.name }).append('\n')
        else if (pages != WidgetPage.MIN) append("K pages\t").append(pages).append('\n')
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
                    'K' -> rest.split('\t').let { f -> if (f.size != 2) prefs else when (f[0]) { "walking" -> prefs.withWalking(f[1] != "0"); "showStatus" -> prefs.withShowStatus(f[1] != "0"); "edgeDrag" -> prefs.withEdgeDrag(f[1] != "0"); "weather" -> prefs.copy(weather = Weather.entries.firstOrNull { it.name == f[1] } ?: Weather.LIVE); "fahrenheit" -> prefs.withFahrenheit(f[1] != "0"); "pageSwipe" -> prefs.withPageSwipe(f[1] != "0"); "batterySaver" -> prefs.copy(batterySaver = BatterySaver.entries.firstOrNull { it.name == f[1] } ?: BatterySaver.AUTO); "dateStyle" -> prefs.copy(dateStyle = DateStyle.entries.firstOrNull { it.name == f[1] } ?: DateStyle.SHORT); "hour12" -> prefs.withHour12(f[1] != "0"); "showSeconds" -> prefs.withShowSeconds(f[1] != "0"); "sundayFirst" -> prefs.withSundayFirst(f[1] != "0"); "gatherTap" -> prefs.withGatherTap(f[1] != "0"); "gridMode" -> prefs.withGridMode(f[1] != "0"); "pages" -> prefs.withPages(f[1].toIntOrNull() ?: WidgetPage.MIN); "pageOrder" -> f[1].split(',').mapNotNull { n -> WidgetPage.entries.firstOrNull { it.name == n } }.let { o -> if (o.distinct().size == o.size && WidgetPage.HOME in o && WidgetPage.TODAY in o && o.size <= WidgetPage.MAX) prefs.copy(pageOrder = o) else prefs }; "showClock" -> prefs.withShowClock(f[1] != "0"); "townSize" -> prefs.copy(townSize = f[1].toIntOrNull()?.takeIf { it in listOf(8, 16, 24) } ?: 24); else -> if (f[0].startsWith("gesture.")) GestureTrigger.entries.firstOrNull { it.name == f[0].removePrefix("gesture.") }?.let { t -> GestureAction.entries.firstOrNull { it.name == f[1] }?.let { a -> prefs.withGesture(t, a) } } ?: prefs else prefs } }
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

    /** Towns built from the dungeon tilesets of Pokémon Mystery Dungeon: Red Rescue Team (assets/tiles/rr-NN, cut by tools/tiles/extract_rescue_tiles.py). */
    val rescueTeam = listOf(
        "rr-00" to "Silver Trench / Stormy Sea (Rescue Team)", "rr-01" to "Rescue Team 1", "rr-02" to "Silent Chasm (Rescue Team)", "rr-03" to "Rescue Team 3",
        "rr-04" to "Rescue Team 4", "rr-05" to "Rescue Team 5", "rr-06" to "Rescue Team 6", "rr-07" to "Rescue Team 7", "rr-08" to "Rescue Team 8",
        "rr-09" to "Rescue Team 9", "rr-10" to "Rescue Team 10", "rr-11" to "Rescue Team 11", "rr-12" to "Rescue Team 12", "rr-13" to "Rescue Team 13",
        "rr-14" to "Tiny Woods (Rescue Team)", "rr-15" to "Rescue Team 15", "rr-16" to "Rescue Team 16", "rr-17" to "Rescue Team 17",
        "rr-18" to "Rescue Team 18", "rr-19" to "Rescue Team 19", "rr-20" to "Rescue Team 20", "rr-21" to "Rescue Team 21", "rr-22" to "Rescue Team 22",
        "rr-23" to "Rescue Team 23", "rr-24" to "Rescue Team 24", "rr-25" to "Rescue Team 25", "rr-26" to "Rescue Team 26", "rr-27" to "Rescue Team 27",
        "rr-28" to "Meteor Cave (Rescue Team)", "rr-29" to "Rescue Team 29", "rr-30" to "Rescue Team 30", "rr-31" to "Rescue Team 31",
        "rr-32" to "Rescue Team 32", "rr-33" to "Rescue Team 33", "rr-34" to "Rescue Team 34", "rr-35" to "Sky Tower / Sky Tower Summit (Rescue Team)",
        "rr-36" to "Frosty Forest / Frosty Grotto (Rescue Team)", "rr-37" to "Rescue Team 37", "rr-38" to "Rescue Team 38",
        "rr-39" to "Mt Steel (Rescue Team)", "rr-40" to "Mt Steel (Rescue Team)", "rr-41" to "Sinister Woods (Rescue Team)",
        "rr-42" to "Mt Thunder (Rescue Team)", "rr-43" to "Mt Thunder Peak (Rescue Team)", "rr-44" to "Great Canyon (Rescue Team)",
        "rr-45" to "Lapis Cave (Rescue Team)", "rr-46" to "Magma Cavern / Mt Blaze (Rescue Team)", "rr-47" to "Mt Freeze / Mt Freeze Peak (Rescue Team)",
        "rr-48" to "Magma Cavern / Magma Cavern Pit (Rescue Team)", "rr-49" to "Silver Trench / Stormy Sea (Rescue Team)",
        "rr-50" to "Thunderwave Cave (Rescue Team)", "rr-51" to "Rescue Team 51", "rr-52" to "Rescue Team 52", "rr-53" to "Rescue Team 53",
        "rr-54" to "Silver Trench (Rescue Team)", "rr-55" to "Rescue Team 55", "rr-56" to "Rescue Team 56", "rr-57" to "Rescue Team 57",
        "rr-58" to "Rescue Team 58", "rr-59" to "Magma Cavern (Rescue Team)", "rr-60" to "Rescue Team 60", "rr-61" to "Rescue Team 61",
        "rr-62" to "Rescue Team 62", "rr-63" to "Rescue Team 63",
    )

    /** Towns built from the dungeon tilesets of Pokémon Mystery Dungeon: Explorers of Sky (assets/tiles/ex-NNN, cut by tools/tiles/extract_sky_tiles.py; the dungeons they belong to are not named). */
    val explorers = listOf(
        "ex-001" to "Explorers 1", "ex-002" to "Explorers 2", "ex-003" to "Explorers 3", "ex-004" to "Explorers 4", "ex-005" to "Explorers 5",
        "ex-006" to "Explorers 6", "ex-007" to "Explorers 7", "ex-008" to "Explorers 8", "ex-009" to "Explorers 9", "ex-010" to "Explorers 10",
        "ex-011" to "Explorers 11", "ex-012" to "Explorers 12", "ex-013" to "Explorers 13", "ex-014" to "Explorers 14", "ex-015" to "Explorers 15",
        "ex-016" to "Explorers 16", "ex-017" to "Explorers 17", "ex-018" to "Explorers 18", "ex-019" to "Explorers 19", "ex-020" to "Explorers 20",
        "ex-021" to "Explorers 21", "ex-022" to "Explorers 22", "ex-023" to "Explorers 23", "ex-024" to "Explorers 24", "ex-025" to "Explorers 25",
        "ex-026" to "Explorers 26", "ex-027" to "Explorers 27", "ex-028" to "Explorers 28", "ex-029" to "Explorers 29", "ex-030" to "Explorers 30",
        "ex-031" to "Explorers 31", "ex-032" to "Explorers 32", "ex-033" to "Explorers 33", "ex-034" to "Explorers 34", "ex-035" to "Explorers 35",
        "ex-036" to "Explorers 36", "ex-037" to "Explorers 37", "ex-038" to "Explorers 38", "ex-039" to "Explorers 39", "ex-040" to "Explorers 40",
        "ex-041" to "Explorers 41", "ex-042" to "Explorers 42", "ex-043" to "Explorers 43", "ex-044" to "Explorers 44", "ex-045" to "Explorers 45",
        "ex-046" to "Explorers 46", "ex-047" to "Explorers 47", "ex-048" to "Explorers 48", "ex-049" to "Explorers 49", "ex-050" to "Explorers 50",
        "ex-051" to "Explorers 51", "ex-052" to "Explorers 52", "ex-053" to "Explorers 53", "ex-054" to "Explorers 54", "ex-055" to "Explorers 55",
        "ex-056" to "Explorers 56", "ex-057" to "Explorers 57", "ex-058" to "Explorers 58", "ex-059" to "Explorers 59", "ex-060" to "Explorers 60",
        "ex-061" to "Explorers 61", "ex-062" to "Explorers 62", "ex-063" to "Explorers 63", "ex-064" to "Explorers 64", "ex-065" to "Explorers 65",
        "ex-066" to "Explorers 66", "ex-067" to "Explorers 67", "ex-068" to "Explorers 68", "ex-069" to "Explorers 69", "ex-070" to "Explorers 70",
        "ex-071" to "Explorers 71", "ex-072" to "Explorers 72", "ex-073" to "Explorers 73", "ex-074" to "Explorers 74", "ex-075" to "Explorers 75",
        "ex-076" to "Explorers 76", "ex-077" to "Explorers 77", "ex-078" to "Explorers 78", "ex-079" to "Explorers 79", "ex-080" to "Explorers 80",
        "ex-081" to "Explorers 81", "ex-082" to "Explorers 82", "ex-083" to "Explorers 83", "ex-084" to "Explorers 84", "ex-085" to "Explorers 85",
        "ex-086" to "Explorers 86", "ex-087" to "Explorers 87", "ex-088" to "Explorers 88", "ex-089" to "Explorers 89", "ex-090" to "Explorers 90",
        "ex-091" to "Explorers 91", "ex-092" to "Explorers 92", "ex-093" to "Explorers 93", "ex-094" to "Explorers 94", "ex-095" to "Explorers 95",
        "ex-096" to "Explorers 96", "ex-097" to "Explorers 97", "ex-098" to "Explorers 98", "ex-099" to "Explorers 99", "ex-100" to "Explorers 100",
        "ex-101" to "Explorers 101", "ex-102" to "Explorers 102", "ex-103" to "Explorers 103", "ex-104" to "Explorers 104", "ex-105" to "Explorers 105",
        "ex-106" to "Explorers 106", "ex-107" to "Explorers 107", "ex-108" to "Explorers 108", "ex-109" to "Explorers 109", "ex-110" to "Explorers 110",
        "ex-111" to "Explorers 111", "ex-112" to "Explorers 112", "ex-113" to "Explorers 113", "ex-114" to "Explorers 114", "ex-115" to "Explorers 115",
        "ex-116" to "Explorers 116", "ex-117" to "Explorers 117", "ex-118" to "Explorers 118", "ex-119" to "Explorers 119", "ex-120" to "Explorers 120",
        "ex-121" to "Explorers 121", "ex-122" to "Explorers 122", "ex-123" to "Explorers 123", "ex-124" to "Explorers 124", "ex-125" to "Explorers 125",
        "ex-126" to "Explorers 126", "ex-127" to "Explorers 127", "ex-128" to "Explorers 128", "ex-129" to "Explorers 129", "ex-130" to "Explorers 130",
        "ex-131" to "Explorers 131", "ex-132" to "Explorers 132", "ex-133" to "Explorers 133", "ex-134" to "Explorers 134", "ex-135" to "Explorers 135",
        "ex-136" to "Explorers 136", "ex-137" to "Explorers 137", "ex-138" to "Explorers 138", "ex-139" to "Explorers 139", "ex-140" to "Explorers 140",
        "ex-141" to "Explorers 141", "ex-142" to "Explorers 142", "ex-143" to "Explorers 143",
    )

    val all = scenes + dungeons + rescueTeam + explorers

    fun isDungeon(name: String) = dungeons.any { it.first == name } || rescueTeam.any { it.first == name } || explorers.any { it.first == name }
}

/**
 * The 57 Friend Areas of Pokémon Mystery Dungeon: Red Rescue Team (assets/rescue-maps, decoded by tools/sprites/fetch_rescue_maps.py): whole
 * pictures, used as page backgrounds. A key is `fa-` and the area's name in lower case with hyphens; [file] is its picture.
 */
object FriendAreas {
    val all = listOf(
        "fa-aged-chamber-an" to "Aged Chamber (A to N)", "fa-aged-chamber-o-exclaim" to "Aged Chamber (O to !)", "fa-ancient-relic" to "Ancient Relic",
        "fa-beau-plains" to "Beau Plains", "fa-boulder-cave" to "Boulder Cave", "fa-bountiful-sea" to "Bountiful Sea", "fa-crater" to "Crater",
        "fa-cryptic-cave" to "Cryptic Cave", "fa-darkness-ridge" to "Darkness Ridge", "fa-decrepit-lab" to "Decrepit Lab",
        "fa-deep-sea-current" to "Deep Sea Current", "fa-deep-sea-floor" to "Deep Sea Floor", "fa-dragon-cave" to "Dragon Cave", "fa-echo-cave" to "Echo Cave",
        "fa-enclosed-island" to "Enclosed Island", "fa-energetic-forest" to "Energetic Forest", "fa-final-island" to "Final Island",
        "fa-flyaway-forest" to "Flyaway Forest", "fa-frigid-cavern" to "Frigid Cavern", "fa-furnace-desert" to "Furnace Desert",
        "fa-healing-forest" to "Healing Forest", "fa-ice-floe-beach" to "Ice Floe Beach", "fa-jungle" to "Jungle", "fa-legendary-island" to "Legendary Island",
        "fa-magnetic-quarry" to "Magnetic Quarry", "fa-mist-rise-forest" to "Mist Rise Forest", "fa-mt-cleft" to "Mt. Cleft",
        "fa-mt-deepgreen" to "Mt. Deepgreen", "fa-mt-discipline" to "Mt. Discipline", "fa-mt-moonview" to "Mt. Moonview",
        "fa-mushroom-forest" to "Mushroom Forest", "fa-mystic-lake" to "Mystic Lake", "fa-overgrown-forest" to "Overgrown Forest",
        "fa-peanut-swamp" to "Peanut Swamp", "fa-poison-swamp" to "Poison Swamp", "fa-power-plant" to "Power Plant", "fa-rainbow-peak" to "Rainbow Peak",
        "fa-ravaged-field" to "Ravaged Field", "fa-rub-a-dub-river" to "Rub A Dub River", "fa-sacred-field" to "Sacred Field", "fa-safari" to "Safari",
        "fa-scorched-plains" to "Scorched Plains", "fa-seafloor-cave" to "Seafloor Cave", "fa-secretive-forest" to "Secretive Forest",
        "fa-serene-sea" to "Serene Sea", "fa-shallow-beach" to "Shallow Beach", "fa-sky-blue-plains" to "Sky Blue Plains",
        "fa-southern-island" to "Southern Island", "fa-stratos-lookout" to "Stratos Lookout", "fa-tadpole-pond" to "Tadpole Pond",
        "fa-thunder-meadow" to "Thunder Meadow", "fa-transform-forest" to "Transform Forest", "fa-treasure-sea" to "Treasure Sea",
        "fa-turtleshell-pond" to "Turtleshell Pond", "fa-volcanic-pit" to "Volcanic Pit", "fa-waterfall-lake" to "Waterfall Lake",
        "fa-wild-plains" to "Wild Plains",
    )

    fun isArea(name: String) = all.any { it.first == name }

    /** The resource path of a Friend Area's picture. */
    fun file(name: String) = "files/rescue-maps/FRIEND_AREA_" + name.removePrefix("fa-").uppercase().replace('-', '_') + ".png"
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


/** What a launcher gesture can be set to do. */
enum class GestureAction(val label: String) {
    NONE("Nothing"), APPS("Open the app list"), GATHER("Gather the Pokémon"), POKELIFE("Open PokéLife"), GEAR("Open the Pokégear"), SETTINGS("Launcher settings"),
}

/** The gestures that can be given a job, each with the jobs it offers. */
enum class GestureTrigger(val label: String, val actions: List<GestureAction>) {
    EDGE_DRAG("Drag in from the right edge", GestureAction.entries),
    TRIPLE_TAP("Tap three times", GestureAction.entries),
    DOUBLE_TAP("Double tap empty space", GestureAction.entries),
}
