package app.pokeplan.ui.launcher

enum class BatterySaver(val label: String, val note: String) {
    OFF("Off", "Never saves. The town, the weather and the clock always run at full speed."),
    AUTO("Automatic", "Saves at 20% or lower while not charging, or whenever Android's own battery saver is on."),
    ON("Always", "Always saves: the Pokémon stand still, animations stop, the clock updates every 10 seconds and the forecast every two hours."),
}

fun batterySaving(choice: BatterySaver, battery: BatteryStatus?, phoneSaving: Boolean): Boolean = when (choice) {
    BatterySaver.ON -> true
    BatterySaver.OFF -> false
    BatterySaver.AUTO -> phoneSaving || (battery != null && !battery.charging && battery.percent <= LOW_BATTERY)
}

const val LOW_BATTERY = 20

enum class WidgetArea(val label: String) { HOME_TOP("Top of the home page"), HOME_BOTTOM("Bottom of the home page"), TODAY("Today page") }

enum class WidgetKind(val label: String) {
    CLOCK("Date and time"), STATUS("Companion"), BUDDY("Buddy"), NAV("Shortcuts"), PINNED("Favorites"), OFTEN("Often used"), FOLDER("Folder"),
    ACTIONS("App buttons"), CALENDAR("Calendar"), AGENDA("Agenda and tasks"), FORECAST("Forecast"),
    ANDROID("Android widget");

    val resizable: Boolean get() = this in setOf(PINNED, OFTEN, FOLDER, CALENDAR, AGENDA, FORECAST, ANDROID)
}

enum class WidgetSize(val label: String) { SMALL("Small"), MEDIUM("Medium"), LARGE("Large");
    fun next() = entries[(ordinal + 1) % entries.size]
}

data class WidgetSlot(val area: WidgetArea, val kind: WidgetKind, val arg: String = "", val size: WidgetSize = WidgetSize.LARGE) {
    fun sameAs(o: WidgetSlot) = area == o.area && kind == o.kind && arg == o.arg
    val title: String get() = if (kind == WidgetKind.FOLDER) arg else kind.label
}

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

data class Folder(val name: String, val apps: List<String> = emptyList()) {
    companion object { const val MAX_NAME = 24 }
}

data class GeoPlace(val name: String, val latitude: Double, val longitude: Double)

const val MIN_LAUNCHES = 3
private const val MAX_LAUNCHES = 60

enum class Weather(val label: String) {
    CLEAR("Clear"), LIVE("Live forecast"), GUARDIAN("Guardian's weather"), CLOUDS("Cloud shadows"), RAIN("Rain"), SNOW("Snow"), BLIZZARD("Blizzard"), SANDSTORM("Sandstorm"),
}

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
        fun fromAndroid(category: Int): AppCategory = when (category) {
            0 -> GAMES
            1, 2, 3 -> MEDIA
            4 -> SOCIAL
            5 -> NEWS
            6 -> TRAVEL
            7 -> PRODUCTIVITY
            else -> OTHER
        }

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

        fun guess(declared: Int, packageName: String, label: String, system: Boolean): AppCategory {
            fromAndroid(declared).takeIf { it != OTHER }?.let { return it }
            val text = "$packageName $label".lowercase()
            keywords.firstOrNull { (_, words) -> words.any { it in text } }?.let { return it.first }
            return if (system) TOOLS else OTHER
        }
    }
}

data class LauncherApp(val key: String, val label: String, val category: AppCategory = AppCategory.OTHER, val removable: Boolean = true)

data class LauncherPrefs(
    val pinned: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val moved: Map<String, AppCategory> = emptyMap(),
    val scene: String = TownScenes.DEFAULT,
    val icons: Map<String, String> = emptyMap(),
    val walking: Boolean = true,
    val chirps: Boolean = true,
    val townSize: Int = 24,
    val activity: Int = TownBehavior.ACTIVITY_DEFAULT,
    val layoutDay: Long = 0,
    val layoutRoll: Int = 0,
    val showStatus: Boolean = true,
    val edgeDrag: Boolean = true,
    val pageSwipe: Boolean = true,
    val gatherTap: Boolean = true,
    val gestures: Map<GestureTrigger, GestureAction> = emptyMap(),
    val gridMode: Boolean = true,
    val pageOrder: List<WidgetPage> = WidgetPage.entries.take(WidgetPage.MIN),
    val showClock: Boolean = true,
    val weather: Weather = Weather.LIVE,
    val launches: Map<String, Int> = emptyMap(),
    val folders: List<Folder> = emptyList(),
    val place: GeoPlace? = null,
    val fahrenheit: Boolean = false,
    val batterySaver: BatterySaver = BatterySaver.AUTO,
    val showSeconds: Boolean = false,
    val layout: List<WidgetSlot>? = null,
    val grid: List<GridWidget>? = null,
) {
    val gridWidgets: List<GridWidget> get() = grid ?: WidgetGrid.fromSlots(widgets)
    fun withGrid(next: List<GridWidget>) = copy(grid = next)

    val widgets: List<WidgetSlot> get() = layout ?: defaultWidgets(showClock, showStatus)
    fun widgetsIn(area: WidgetArea): List<WidgetSlot> = widgets.filter { it.area == area }
    private fun withWidgets(next: List<WidgetSlot>) = copy(layout = next)

    fun addWidget(slot: WidgetSlot, limits: WidgetLimits? = null): LauncherPrefs {
        if (slot.kind == WidgetKind.FOLDER && folders.none { it.name == slot.arg }) return this
        if (widgets.any { it.sameAs(slot) }) return this
        val onGrid = grid?.let { g ->
            val l = limits ?: slot.kind.limits()
            WidgetGrid.add(g, GridWidget(if (slot.area == WidgetArea.TODAY) WidgetPage.TODAY else WidgetPage.HOME, slot.kind, slot.arg, 0, 0, l.defW, l.defH))
        }
        return withWidgets(widgets + slot).let { if (onGrid != null) it.withGrid(onGrid) else it }
    }

    fun removeWidget(slot: WidgetSlot): LauncherPrefs = withWidgets(widgets.filterNot { it.sameAs(slot) }).let { p ->
        p.grid?.let { g -> p.withGrid(g.filterNot { it.kind == slot.kind && it.arg == slot.arg && (it.page == WidgetPage.TODAY) == (slot.area == WidgetArea.TODAY) }) } ?: p
    }

    fun removeGridWidget(widget: GridWidget): LauncherPrefs {
        val base = WidgetGrid.remove(grid ?: gridWidgets, widget)
        val twin = widgets.firstOrNull { it.kind == widget.kind && it.arg == widget.arg && (it.area == WidgetArea.TODAY) == (widget.page == WidgetPage.TODAY) }
        return (if (twin != null) withWidgets(widgets.filterNot { it.sameAs(twin) }) else this).withGrid(base)
    }

    fun resizeWidget(slot: WidgetSlot, size: WidgetSize): LauncherPrefs = withWidgets(widgets.map { if (it.sameAs(slot)) it.copy(size = size) else it })

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
    fun withChirps(on: Boolean) = copy(chirps = on)

    fun withNewLayout(epochDay: Long) = copy(layoutDay = epochDay, layoutRoll = if (layoutDay == epochDay) layoutRoll + 1 else 1)

    fun layoutRollOn(epochDay: Long): Int = if (layoutDay == epochDay) layoutRoll else 0
    fun withActivity(level: Int) = copy(activity = level.coerceIn(1, 10))
    fun withShowStatus(on: Boolean) = copy(showStatus = on)
    fun withEdgeDrag(on: Boolean) = copy(edgeDrag = on)
    fun nextBatterySaver() = copy(batterySaver = BatterySaver.entries[(batterySaver.ordinal + 1) % BatterySaver.entries.size])
    fun withShowSeconds(on: Boolean) = copy(showSeconds = on)
    fun withPageSwipe(on: Boolean) = copy(pageSwipe = on)
    fun withGatherTap(on: Boolean) = copy(gatherTap = on)

    fun action(trigger: GestureTrigger): GestureAction = gestures[trigger] ?: when (trigger) {
        GestureTrigger.EDGE_DRAG -> if (edgeDrag) GestureAction.APPS else GestureAction.NONE
        GestureTrigger.TRIPLE_TAP -> if (gatherTap) GestureAction.GATHER else GestureAction.NONE
        GestureTrigger.DOUBLE_TAP -> GestureAction.NONE
        GestureTrigger.SWIPE_UP -> GestureAction.APPS
        else -> GestureAction.NONE
    }

    fun withGesture(trigger: GestureTrigger, action: GestureAction): LauncherPrefs =
        if (action in trigger.actions) copy(gestures = gestures + (trigger to action)) else this
    fun withGridMode(on: Boolean) = copy(gridMode = on)
    val pages: Int get() = pageOrder.size

    fun withPages(n: Int): LauncherPrefs {
        val want = n.coerceIn(WidgetPage.MIN, WidgetPage.MAX)
        var order = pageOrder
        while (order.size < want) order = order + (WidgetPage.entries.first { it !in order })
        while (order.size > want) order = order - order.last { it.ordinal >= WidgetPage.MIN }
        return copy(pageOrder = order)
    }

    val pageList: List<WidgetPage> get() = pageOrder

    val homeIndex: Int get() = pageOrder.indexOf(WidgetPage.HOME)

    fun addPage(left: Boolean): LauncherPrefs {
        if (pageOrder.size >= WidgetPage.MAX) return this
        val fresh = WidgetPage.entries.first { it !in pageOrder }
        return copy(pageOrder = if (left) listOf(fresh) + pageOrder else pageOrder + fresh)
    }

    fun canDeletePage(page: WidgetPage): Boolean = page in pageOrder && page.ordinal >= WidgetPage.MIN

    fun deletePage(page: WidgetPage): LauncherPrefs {
        if (!canDeletePage(page)) return this
        val at = pageOrder.indexOf(page)
        val heir = pageOrder[if (at > 0) at - 1 else at + 1]
        var next = this
        WidgetGrid.onPage(gridWidgets, page).forEach { next = next.moveGridWidgetToPage(it, heir) }
        return next.copy(pageOrder = next.pageOrder - page)
    }

    private fun areaOf(page: WidgetPage) = if (page == WidgetPage.TODAY) WidgetArea.TODAY else WidgetArea.HOME_BOTTOM

    fun addGridWidget(page: WidgetPage, kind: WidgetKind, arg: String = "", limits: WidgetLimits? = null): LauncherPrefs {
        if (gridWidgets.any { it.kind == kind && it.arg == arg }) return this
        if (kind == WidgetKind.FOLDER && folders.none { it.name == arg }) return this
        val base = withGrid(gridWidgets)
        if (page == WidgetPage.HOME || page == WidgetPage.TODAY) return base.addWidget(WidgetSlot(areaOf(page), kind, arg), limits)
        val l = limits ?: kind.limits()
        return base.withGrid(WidgetGrid.add(base.grid!!, GridWidget(page, kind, arg, 0, 0, l.defW, l.defH)))
    }

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

    fun deleteLastPage(): LauncherPrefs = deletePage(pageOrder.last())
    fun withShowClock(on: Boolean) = copy(showClock = on)
    fun withPlace(place: GeoPlace?) = copy(place = place)
    fun withFahrenheit(on: Boolean) = copy(fahrenheit = on)

    fun recordLaunch(key: String): LauncherPrefs {
        val next = launches + (key to (launches[key] ?: 0) + 1)
        return copy(launches = if (next.values.max() > MAX_LAUNCHES) next.mapValues { it.value / 2 }.filterValues { it > 0 } else next)
    }

    fun suggested(apps: List<LauncherApp>, limit: Int = 4): List<LauncherApp> =
        apps.filter { it.key !in pinned && it.key !in hidden && (launches[it.key] ?: 0) >= MIN_LAUNCHES }.sortedWith(compareByDescending<LauncherApp> { launches[it.key] ?: 0 }.thenBy { foldedName(it.label) }).take(limit)

    fun addFolder(name: String): LauncherPrefs {
        val clean = name.trim().take(Folder.MAX_NAME)
        return if (clean.isEmpty() || folders.any { it.name.equals(clean, ignoreCase = true) }) this else copy(folders = folders + Folder(clean))
    }

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
    fun nextTownSize() = copy(townSize = when (townSize) { 8 -> 16; 16 -> 24; else -> 8 })
    fun withoutPins() = copy(pinned = emptyList(), icons = emptyMap())

    fun reset() = LauncherPrefs(pinned = pinned, hidden = hidden, moved = moved, icons = icons, launches = launches, folders = folders, place = place)

    fun withIcon(key: String, item: String?) = if (item == null) copy(icons = icons - key) else if (item in LauncherIcons.items) copy(icons = icons + (key to item)) else this

    fun withScene(name: String) = if (name in TownScenes.all.map { it.first }) copy(scene = name) else this

    fun pin(key: String) = if (key in pinned) this else copy(pinned = pinned + key)
    fun unpin(key: String) = copy(pinned = pinned - key)
    fun hide(key: String) = copy(hidden = hidden + key, pinned = pinned - key)
    fun unhide(key: String) = copy(hidden = hidden - key)

    fun move(app: LauncherApp, category: AppCategory) =
        copy(moved = if (category == app.category) moved - app.key else moved + (app.key to category))

    fun categoryOf(app: LauncherApp): AppCategory = moved[app.key] ?: app.category

    fun encode(): String = buildString {
        if (scene != TownScenes.DEFAULT) append("S ").append(scene).append('\n')
        if (!walking) append("K walking\t0\n")
        if (!chirps) append("K chirps\t0\n")
        if (townSize != 24) append("K townSize\t").append(townSize).append('\n')
        if (activity != TownBehavior.ACTIVITY_DEFAULT) append("K activity\t").append(activity).append('\n')
        if (layoutRoll != 0) append("K layout\t").append(layoutDay).append(',').append(layoutRoll).append('\n')
        if (!showStatus) append("K showStatus\t0\n")
        if (!edgeDrag) append("K edgeDrag\t0\n")
        if (batterySaver != BatterySaver.AUTO) append("K batterySaver\t").append(batterySaver.name).append('\n')
        if (showSeconds) append("K showSeconds\t1\n")
        if (!pageSwipe) append("K pageSwipe\t0\n")
        if (!gatherTap) append("K gatherTap\t0\n")
        gestures.entries.sortedBy { it.key.ordinal }.forEach { (t, a) -> append("K gesture.").append(t.name).append('\t').append(a.name).append('\n') }
        if (!gridMode) append("K gridMode\t0\n")
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
        fun decode(text: String?): LauncherPrefs {
            var prefs = LauncherPrefs()
            text.orEmpty().lineSequence().forEach { line ->
                if (line.length < 3 || line[1] != ' ') return@forEach
                val rest = line.substring(2)
                prefs = when (line[0]) {
                    'P' -> prefs.pin(rest)
                    'H' -> prefs.copy(hidden = prefs.hidden + rest)
                    'S' -> prefs.withScene(rest)
                    'K' -> rest.split('\t').let { f -> if (f.size != 2) prefs else when (f[0]) { "walking" -> prefs.withWalking(f[1] != "0"); "chirps" -> prefs.withChirps(f[1] != "0"); "showStatus" -> prefs.withShowStatus(f[1] != "0"); "edgeDrag" -> prefs.withEdgeDrag(f[1] != "0"); "weather" -> prefs.copy(weather = Weather.entries.firstOrNull { it.name == f[1] } ?: Weather.LIVE); "fahrenheit" -> prefs.withFahrenheit(f[1] != "0"); "pageSwipe" -> prefs.withPageSwipe(f[1] != "0"); "batterySaver" -> prefs.copy(batterySaver = BatterySaver.entries.firstOrNull { it.name == f[1] } ?: BatterySaver.AUTO); "showSeconds" -> prefs.withShowSeconds(f[1] != "0"); "gatherTap" -> prefs.withGatherTap(f[1] != "0"); "gridMode" -> prefs.withGridMode(f[1] != "0"); "pages" -> prefs.withPages(f[1].toIntOrNull() ?: WidgetPage.MIN); "pageOrder" -> f[1].split(',').mapNotNull { n -> WidgetPage.entries.firstOrNull { it.name == n } }.let { o -> if (o.distinct().size == o.size && WidgetPage.HOME in o && WidgetPage.TODAY in o && o.size <= WidgetPage.MAX) prefs.copy(pageOrder = o) else prefs }; "showClock" -> prefs.withShowClock(f[1] != "0"); "townSize" -> prefs.copy(townSize = f[1].toIntOrNull()?.takeIf { it in listOf(8, 16, 24) } ?: 24); "activity" -> prefs.withActivity(f[1].toIntOrNull() ?: TownBehavior.ACTIVITY_DEFAULT); "layout" -> f[1].split(',').let { v -> val d = v.getOrNull(0)?.toLongOrNull(); val r = v.getOrNull(1)?.toIntOrNull(); if (d != null && r != null) prefs.copy(layoutDay = d, layoutRoll = r) else prefs }; else -> if (f[0].startsWith("gesture.")) GestureTrigger.entries.firstOrNull { it.name == f[0].removePrefix("gesture.") }?.let { t -> GestureAction.entries.firstOrNull { it.name == f[1] }?.let { a -> prefs.withGesture(t, a) } } ?: prefs else prefs } }
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

internal fun foldedName(label: String): String = label.trim().lowercase().map { c -> FROM.indexOf(c).let { if (it >= 0) TO[it] else c } }.joinToString("")

fun LauncherApp.letter(): Char = foldedName(label).firstOrNull()?.takeIf { it.isLetter() }?.uppercaseChar() ?: '#'

object LauncherList {
    val byName: Comparator<LauncherApp> = compareBy<LauncherApp> { it.letter() != '#' }.thenBy { foldedName(it.label) }.thenBy { it.key }

    fun visible(apps: List<LauncherApp>, prefs: LauncherPrefs, query: String = "", category: AppCategory? = null, hidden: Boolean = false): List<LauncherApp> {
        val q = foldedName(query)
        val shown = apps.filter { (it.key in prefs.hidden) == hidden && (category == null || prefs.categoryOf(it) == category) }
        if (q.isEmpty()) return shown.sortedWith(byName)
        return shown.filter { app -> foldedName(app.label).contains(q) }
            .sortedWith(compareBy<LauncherApp> { !foldedName(it.label).startsWith(q) }.then(byName))
    }

    fun pinned(apps: List<LauncherApp>, prefs: LauncherPrefs): List<LauncherApp> {
        val byKey = apps.associateBy { it.key }
        return prefs.pinned.mapNotNull { byKey[it] }.filter { it.key !in prefs.hidden }
    }

    fun categories(apps: List<LauncherApp>, prefs: LauncherPrefs): List<Pair<AppCategory, Int>> {
        val shown = apps.filter { it.key !in prefs.hidden }
        return AppCategory.entries.mapNotNull { c -> shown.count { prefs.categoryOf(it) == c }.takeIf { it > 0 }?.let { c to it } }
    }

    fun letters(sorted: List<LauncherApp>): List<Pair<Char, Int>> {
        val out = ArrayList<Pair<Char, Int>>()
        sorted.forEachIndexed { i, app -> if (out.lastOrNull()?.first != app.letter()) out += app.letter() to i }
        return out
    }
}

object TownScenes {
    const val DEFAULT = "meadow"
    val scenes = listOf(
        "meadow" to "Meadow", "city" to "City", "route" to "Route", "forest" to "Forest", "mountain" to "Mountain", "beach" to "Beach",
        "desert" to "Desert", "thunderplains" to "Thunder plains", "icecave" to "Ice cave", "dampcave" to "Damp cave", "volcanocave" to "Volcano cave",
    )

    val dungeons = listOf(
        "lush-prairie" to "Lush Prairie", "apple-woods" to "Apple Woods", "dusk-forest" to "Dusk Forest", "deep-dusk-forest" to "Deep Dusk Forest",
        "deep-dusk-forest-autumn" to "Deep Dusk Forest (autumn)", "drenched-bluff" to "Drenched Bluff", "craggy-coast" to "Craggy Coast", "beach-cave" to "Beach Cave",
        "brine-cave" to "Brine Cave", "crystal-cave" to "Crystal Cave", "crystal-cave-deep" to "Crystal Cave (deep)", "crystal-crossing" to "Crystal Crossing",
        "dark-hill" to "Dark Hill", "dark-hill-deep" to "Dark Hill (deep)", "deep-sealed-ruin" to "Deep Sealed Ruin", "concealed-ruins" to "Concealed Ruins", "amp-plains" to "Amp Plains",
    )

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

    val explorers = listOf(
        "ex-001" to "Beach Cave / Destiny Tower (Explorers)", "ex-002" to "Destiny Tower / Drenched Bluff (Explorers)",
        "ex-003" to "Mt. Bristle / Zero Isle East (Explorers)", "ex-004" to "Destiny Tower / Spring Cave (Explorers)",
        "ex-005" to "Apple Woods / Zero Isle West (Explorers)", "ex-006" to "Craggy Coast / Destiny Tower (Explorers)",
        "ex-007" to "Destiny Tower / Side Path (Explorers)", "ex-008" to "Destiny Tower / Lower Spring Cave (Explorers)", "ex-009" to "Rock Path (Explorers)",
        "ex-010" to "Foggy Forest (Explorers)", "ex-011" to "1st Station Pass / Forest Path (Explorers)",
        "ex-012" to "Spring Cave Depths / Steam Cave (Explorers)", "ex-013" to "Explorers 13", "ex-014" to "Amp Plains (Explorers)",
        "ex-015" to "Far Amp Plains (Explorers)", "ex-016" to "Destiny Tower / Zero Isle South (Explorers)",
        "ex-017" to "Northern Desert / Rescue (Explorers)", "ex-018" to "Northern Desert (Explorers)", "ex-019" to "Quicksand Cave (Explorers)",
        "ex-020" to "Quicksand Pit (Explorers)", "ex-021" to "Explorers 21", "ex-022" to "Crystal Cave (Explorers)", "ex-023" to "Crystal Cave (Explorers)",
        "ex-024" to "Crystal Crossing / Star Cave (Explorers)", "ex-025" to "Destiny Tower / Zero Isle South (Explorers)",
        "ex-026" to "Chasm Cave (Explorers)", "ex-027" to "Chasm Cave (Explorers)", "ex-028" to "Dark Hill (Explorers)", "ex-029" to "Dark Hill (Explorers)",
        "ex-030" to "Sealed Ruin (Explorers)", "ex-031" to "Deep Sealed Ruin / Zero Isle West (Explorers)",
        "ex-032" to "Deep Barren Valley / Sky Peak Summit Pass (Explorers)", "ex-033" to "Dusk Forest (Explorers)",
        "ex-034" to "Dusk Forest / Midnight Forest (Explorers)", "ex-035" to "Deep Dusk Forest (Explorers)",
        "ex-036" to "Deep Dusk Forest / Destiny Tower (Explorers)", "ex-037" to "Treeshroud Forest (Explorers)", "ex-038" to "Treeshroud Forest (Explorers)",
        "ex-039" to "Brine Cave (Explorers)", "ex-040" to "Lower Brine Cave (Explorers)", "ex-041" to "Explorers 41", "ex-042" to "Hidden Land (Explorers)",
        "ex-043" to "Hidden Highland (Explorers)", "ex-044" to "Southern Jungle (Explorers)", "ex-045" to "Temporal Tower (Explorers)",
        "ex-046" to "Temporal Spire (Explorers)", "ex-047" to "Explorers 47", "ex-048" to "Mystifying Forest (Explorers)",
        "ex-049" to "Aegis Cave Pit / Regirock Chamber (Explorers)", "ex-050" to "Concealed Ruins (Explorers)",
        "ex-051" to "Surrounded Sea / Treacherous Waters (Explorers)", "ex-052" to "Deep Miracle Sea / Miracle Sea (Explorers)",
        "ex-053" to "Mt. Travail (Explorers)", "ex-054" to "The Nightmare (Explorers)", "ex-055" to "Spacial Rift (Explorers)",
        "ex-056" to "Deep Spacial Rift / Spacial Rift (Explorers)", "ex-057" to "Dark Crater (Explorers)", "ex-058" to "Deep Dark Crater (Explorers)",
        "ex-059" to "World Abyss / World Abyss Pit (Explorers)", "ex-060" to "Explorers 60", "ex-061" to "Deep Mystery Jungle / Mystery Jungle (Explorers)",
        "ex-062" to "Mystery Jungle (Explorers)", "ex-063" to "Zero Isle East (Explorers)", "ex-064" to "Zero Isle East (Explorers)",
        "ex-065" to "Tiny Meadow (Explorers)", "ex-066" to "3rd Station Pass (Explorers)", "ex-067" to "Destiny Tower (Explorers)", "ex-068" to "Explorers 68",
        "ex-069" to "Explorers 69", "ex-070" to "Explorers 70", "ex-071" to "Explorers 71", "ex-072" to "Explorers 72", "ex-073" to "Explorers 73",
        "ex-074" to "Explorers 74", "ex-075" to "Southeastern Islands (Explorers)", "ex-076" to "Zero Isle Center (Explorers)", "ex-077" to "Explorers 77",
        "ex-078" to "[M:D1]Dummy (Explorers)", "ex-079" to "Explorers 79", "ex-080" to "Deep Star Cave / Star Cave (Explorers)", "ex-081" to "Explorers 81",
        "ex-082" to "Crevice Cave / Crevice Cave Pit (Explorers)", "ex-083" to "Zero Isle Center (Explorers)", "ex-084" to "Oblivion Forest (Explorers)",
        "ex-085" to "Shimmer Desert / Shimmer Desert Pit (Explorers)", "ex-086" to "Explorers 86", "ex-087" to "Explorers 87",
        "ex-088" to "Blizzard Island (Explorers)", "ex-089" to "[M:D1]Dummy (Explorers)", "ex-090" to "Lush Prairie (Explorers)",
        "ex-091" to "Happy Outlook (Explorers)", "ex-092" to "Explorers 92", "ex-093" to "Explorers 93", "ex-094" to "9th Station Pass (Explorers)",
        "ex-095" to "Explorers 95", "ex-096" to "Serenity River / [M:D1]Dummy (Explorers)", "ex-097" to "Zero Isle Center (Explorers)",
        "ex-098" to "Explorers 98", "ex-099" to "Sky Stairway / Sky Stairway Apex (Explorers)", "ex-100" to "Explorers 100", "ex-101" to "Explorers 101",
        "ex-102" to "Boulder Quarry / Labyrinth Cave (Explorers)", "ex-103" to "Eastern Cave / Landslide Cave (Explorers)",
        "ex-104" to "Fortune Ravine (Explorers)", "ex-105" to "2nd Station Pass / Oran Forest (Explorers)",
        "ex-106" to "6th Station Pass / Shimmer Hill (Explorers)", "ex-107" to "Explorers 107", "ex-108" to "Lost Wilderness (Explorers)",
        "ex-109" to "Right Cave Path / Treacherous Waters (Explorers)", "ex-110" to "Inferno Cave (Explorers)",
        "ex-111" to "Mt. Avalanche / Mt. Avalanche Peak (Explorers)", "ex-112" to "Zero Isle Center (Explorers)",
        "ex-113" to "Lake Afar / [M:D1]Dummy (Explorers)", "ex-114" to "Zero Isle Center / [M:D1]Dummy (Explorers)",
        "ex-115" to "Zero Isle Center (Explorers)", "ex-116" to "Zero Isle Center (Explorers)", "ex-117" to "Marine Resort / Zero Isle North (Explorers)",
        "ex-118" to "Bottomless Sea / Bottomless Sea Depths (Explorers)", "ex-119" to "Mt. Mistral (Explorers)", "ex-120" to "Explorers 120",
        "ex-121" to "Explorers 121", "ex-122" to "Left Cave Path (Explorers)", "ex-123" to "Giant Volcano / Giant Volcano Peak (Explorers)",
        "ex-124" to "Zero Isle North (Explorers)", "ex-125" to "World Abyss (Explorers)", "ex-126" to "Fortune Ravine Depths / Ice Aegis Cave (Explorers)",
        "ex-127" to "Registeel Chamber / Steel Aegis Cave (Explorers)", "ex-128" to "Murky Forest (Explorers)", "ex-129" to "Deep Boulder Quarry (Explorers)",
        "ex-130" to "Limestone Cavern (Explorers)", "ex-131" to "Deep Limestone Cavern (Explorers)", "ex-132" to "Barren Valley (Explorers)",
        "ex-133" to "Dark Wasteland (Explorers)", "ex-134" to "Armaldo's Shelter / Hot Spring (Explorers)",
        "ex-135" to "Hot Spring / Temporal Spire (Explorers)", "ex-136" to "Spacial Cliffs (Explorers)", "ex-137" to "Dark Ice Mountain (Explorers)",
        "ex-138" to "Dark Ice Mountain / Dark Ice Mountain Peak (Explorers)", "ex-139" to "Icicle Forest (Explorers)",
        "ex-140" to "Vast Ice Mountain (Explorers)", "ex-141" to "Vast Ice Mountain Peak (Explorers)",
        "ex-142" to "4th Station Pass / 5th Station Clearing (Explorers)", "ex-143" to "7th Station Pass / 8th Station Pass (Explorers)",
        "ex-144" to "Explorers 144",
    )

    val all = scenes + dungeons + rescueTeam + explorers

    fun isDungeon(name: String) = dungeons.any { it.first == name } || rescueTeam.any { it.first == name } || explorers.any { it.first == name }
}

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

    fun file(name: String) = "files/rescue-maps/FRIEND_AREA_" + name.removePrefix("fa-").uppercase().replace('-', '_') + ".png"
}

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

    val all: List<String> get() = items
}


enum class GestureAction(val label: String) {
    NONE("Nothing"), APPS("Open the app list"), GATHER("Gather the Pokémon"), POKELIFE("Open PokéLife"), GEAR("Open Gear"), SETTINGS("Launcher settings"),
    FOLDERS("Open folders"), WIDGETS("Open widgets"), EDIT("Edit the home screen"), PAGE("Go to the next page"), NOTIFICATIONS("Pull down notifications"),
}

enum class GestureTrigger(val label: String, val actions: List<GestureAction>) {
    EDGE_DRAG("Drag in from the right edge", GestureAction.entries),
    TRIPLE_TAP("Tap three times", GestureAction.entries),
    DOUBLE_TAP("Double tap empty space", GestureAction.entries),
    SWIPE_UP("Swipe up", GestureAction.entries),
    SWIPE_DOWN("Swipe down", GestureAction.entries),
    TWO_FINGER_UP("Swipe up with two fingers", GestureAction.entries),
    TWO_FINGER_DOWN("Swipe down with two fingers", GestureAction.entries),
    LONG_PRESS("Press and hold empty space", GestureAction.entries),
}
