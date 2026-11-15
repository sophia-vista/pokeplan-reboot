package app.pokeplan.ui.launcher

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pokeplan.plan.CalendarEvent
import app.pokeplan.plan.TaskState
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Place
import app.pokeplan.ui.Tab
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PmdFont
import app.pokeplan.ui.theme.PmdText
import app.pokeplan.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** What every widget needs from the launcher: the model, the apps, the prefs, the phone, the forecast and what to do on a tap. */
/** How many grid cells wide and tall the widget being drawn is (the stacked layout is 4 wide and as tall as it needs): widgets with a short form use it to pick one. */
internal class Cells(val w: Int, val h: Int) { val narrow get() = w < WidgetGrid.COLUMNS }
internal val LocalCells = androidx.compose.runtime.staticCompositionLocalOf { Cells(WidgetGrid.COLUMNS, 99) }

internal class WidgetEnv(
    val model: AppModel, val apps: List<LauncherApp>, val prefs: LauncherPrefs, val host: LauncherHost, val forecast: Forecast?, val pixel: Int,
    val today: TodayState, val mood: BuddyMood, val saver: Boolean, val editing: Boolean, val onPrefs: (LauncherPrefs) -> Unit, val onEdit: () -> Unit, val onDone: () -> Unit, val onAllApps: () -> Unit, val onFolders: () -> Unit, val onWidgets: () -> Unit, val onGear: () -> Unit, val onOpenFolder: (String) -> Unit, val onForecast: () -> Unit, val onHold: (LauncherApp) -> Unit,
    /** How many pages the launcher has, and a way to show one of them (by its place in the row). */
    val pages: Int = 2, val goToPage: (Int) -> Unit = {},
    /** The battery, read by the launcher once a minute while it is in sight. */
    val battery: BatteryStatus? = null,
)

/** The month and day the calendar and agenda widgets share, so choosing a day in one shows its agenda in the other. */
internal class TodayState(today: LocalDate) {
    var month by mutableStateOf(LocalDate(today.year, today.month, 1))
    var day by mutableStateOf(today)
    /** The phone's calendar events (Google Calendar's among them) around the month shown, read by the launcher; empty until read or without permission. */
    var device by mutableStateOf<List<CalendarEvent>>(emptyList())
    /** Bumped when the calendar permission is answered, so the events are read again. */
    var permissionAnswers by mutableIntStateOf(0)
}

/**
 * One widget: a dialogue box with what its [WidgetSlot.kind] shows. Hold any quiet part of it to edit the widgets on the
 * screen; while editing, each has a bar to drag it by (past an end, it goes to the other part of the page), a size and Remove.
 */
@Composable
internal fun LauncherWidget(slot: WidgetSlot, env: WidgetEnv) {
    var height by remember { mutableStateOf(0) }
    var drag by remember { mutableStateOf(0f) }
    Column(
        // Widgets keep the page margin, except the buddy's portraits, which sit flush against the sides of the screen.
        Modifier.fillMaxWidth().padding(horizontal = if (slot.kind == WidgetKind.BUDDY) 0.dp else Spacing.Page).onSizeChanged { height = it.height }.graphicsLayer { translationY = drag; shadowElevation = 0f }
            .then(if (env.editing) Modifier.border(2.dp, Color.White.copy(alpha = 0.55f)) else Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { env.onEdit() }) }),
    ) {
        if (env.editing) EditBar(slot, env, onDrag = { dy ->
            drag += dy
            // Past half its own height the widget swaps with its neighbour; the bar then carries on from where it was.
            val step = height.coerceAtLeast(1)
            while (drag > step / 2f) { env.onPrefs(env.prefs.moveWidget(slot, 1)); drag -= step }
            while (drag < -step / 2f) { env.onPrefs(env.prefs.moveWidget(slot, -1)); drag += step }
        }, onDragEnd = { drag = 0f })
        WidgetBody(slot, env)
    }
}

@Composable
private fun EditBar(slot: WidgetSlot, env: WidgetEnv, onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
    val pixel = env.pixel
    val density = LocalDensity.current.density
    Row(Modifier.fillMaxWidth().height(Spacing.Target).background(Color.White.copy(alpha = 0.16f)), verticalAlignment = Alignment.CenterVertically) {
        // The handle: hold and drag up or down.
        Box(
            Modifier.weight(1f).height(Spacing.Target).pointerInput(slot.kind, slot.arg) {
                detectVerticalDragGestures(onDragEnd = onDragEnd, onDragCancel = onDragEnd) { change, dy -> change.consume(); onDrag(dy) }
            }.padding(horizontal = pmdRowPad(pixel, density)).semantics { contentDescription = "Drag ${slot.title} up or down" },
            contentAlignment = Alignment.CenterStart,
        ) { PmdText("Drag  ${if (slot.kind == WidgetKind.ANDROID) env.host.widgetLabel(slot.arg.toIntOrNull() ?: -1) ?: slot.title else slot.title}", pixel) }
        if (slot.kind.resizable) Box(
            Modifier.height(Spacing.Target).clickable(role = Role.Button, onClickLabel = "Change the size of ${slot.title}") { env.onPrefs(env.prefs.resizeWidget(slot, slot.size.next())) }.padding(horizontal = Spacing.M),
            contentAlignment = Alignment.Center,
        ) { PmdText(slot.size.label, pixel, alpha = 0.9f) }
        Box(
            Modifier.height(Spacing.Target).clickable(role = Role.Button, onClickLabel = "Remove ${slot.title}") {
                if (slot.kind == WidgetKind.ANDROID) slot.arg.toIntOrNull()?.let { env.host.releaseWidget(it) }
                env.onPrefs(env.prefs.removeWidget(slot))
            }.padding(horizontal = Spacing.M),
            contentAlignment = Alignment.Center,
        ) { PmdText("Remove", pixel, alpha = 0.9f) }
    }
}

/** The bar at the top of a page being edited: add a widget, or finish. */
@Composable
internal fun EditToolbar(env: WidgetEnv, modifier: Modifier = Modifier) {
    PmdBox(env.pixel, modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            PmdMenuLine("Add widget", env.pixel, Modifier.weight(1f), center = true, onClick = env.onWidgets)
            PmdMenuLine("Done", env.pixel, Modifier.weight(1f), center = true, onClick = env.onDone)
        }
    }
}

@Composable
internal fun WidgetBody(slot: WidgetSlot, env: WidgetEnv) {
    when (slot.kind) {
        WidgetKind.CLOCK -> ClockWidget(env)
        WidgetKind.STATUS -> StatusWidget(env)
        WidgetKind.BUDDY -> BuddyWidget(env)
        WidgetKind.NAV -> NavWidget(env)
        WidgetKind.OFTEN -> OftenWidget(env, slot.size)
        WidgetKind.PINNED -> AppsWidget("", LauncherList.pinned(env.apps, env.prefs), env, slot.size, hint = "Press and hold an app in All apps to pin it", icons = env.prefs.icons, onTitle = null)
        WidgetKind.FOLDER -> {
            val folder = env.prefs.folders.firstOrNull { it.name == slot.arg } ?: return
            val inside = folder.apps.mapNotNull { key -> env.apps.firstOrNull { it.key == key } }
            AppsWidget(folder.name, inside, env, slot.size, hint = "Empty: press and hold an app in All apps and choose Add to folder", icons = emptyMap(), onTitle = { env.onOpenFolder(folder.name) })
        }
        WidgetKind.ACTIONS -> ActionsWidget(env)
        WidgetKind.CALENDAR -> CalendarWidget(env, env.today, slot.size)
        WidgetKind.AGENDA -> AgendaWidget(env, env.today, slot.size)
        WidgetKind.FORECAST -> ForecastWidget(env, slot.size)
        WidgetKind.ANDROID -> slot.arg.toIntOrNull()?.let { id ->
            if (env.host.hostsWidgets && env.host.widgetSpec(id) == null) UnavailableWidget(env.pixel)
            else if (LocalGridFill.current) {
                // On the grid the provider gets exactly the cells' room, with no frame of ours around it, and is told that size.
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                    env.host.HostedWidget(id, slot.size.ordinal, maxWidth.value.toInt(), maxHeight.value.toInt(), Modifier.fillMaxSize())
                }
            } else PmdBox(env.pixel, Modifier.fillMaxWidth()) { env.host.HostedWidget(id, slot.size.ordinal, 0, 0, Modifier.fillMaxWidth()) }
        }
    }
}

/** Where another app's widget stood when its app is gone: it keeps its cells, so nothing moves, until the user removes it (hold, then the X). */
@Composable
private fun UnavailableWidget(pixel: Int) {
    PmdBox(pixel, Modifier.fillMaxWidth()) { PmdText("Widget unavailable", pixel, Modifier.padding(horizontal = Spacing.S), alpha = 0.8f) }
}

@Composable
private fun ClockWidget(env: WidgetEnv) {
    val density = LocalDensity.current.density
    val gap = Modifier.padding(horizontal = pmdRowPad(env.pixel, density))
    val battery = env.battery
    PmdBox(env.pixel, Modifier.fillMaxWidth()) {
        // The seconds tick in the model, so reading it keeps the minute right; without seconds only a new minute draws this again.
        val seconds = env.prefs.showSeconds && !env.saver
        val stamp by remember(seconds) { androidx.compose.runtime.derivedStateOf { if (seconds) env.model.now else env.model.now / 60_000L } }
        stamp
        val now = env.model.store.now
        val cells = LocalCells.current
        val time = formatTime(now.hour, now.minute, if (env.prefs.showSeconds && !env.saver) now.second else null, env.prefs.hour12)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Narrower than the screen the date gives way to the time, and the battery to a wider place.
            PmdText(if (cells.w >= 3) "${formatDate(now.date, env.prefs.dateStyle)}   $time" else time, env.pixel, gap)
            Box(Modifier.weight(1f))
            // A battery filled to its charge (empty, one, two or three bars), with a bolt beside it while charging; low and not charging, it turns red.
            if (cells.w >= 4) battery?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (it.charging) app.pokeplan.ui.Glyph("zap", Color.White.copy(alpha = 0.75f), size = 16.dp, description = "Charging")
                    val low = !it.charging && it.percent <= LOW_BATTERY
                    app.pokeplan.ui.Glyph(batteryGlyph(it.percent), if (low) Pixel.Red else Color.White.copy(alpha = 0.75f), size = 20.dp, description = "Battery")
                    PmdText(it.percent.toString(), env.pixel, gap, alpha = 0.75f)
                }
            }
        }
    }
}

/** The battery glyph for a charge of [percent]: empty at 10 or below, then one, two or three bars (Pixelarticons' battery levels). */
internal fun batteryGlyph(percent: Int): String = when {
    percent <= 10 -> "battery"
    percent <= 40 -> "battery-low"
    percent <= 75 -> "battery-medium"
    else -> "battery-full"
}

@Composable
private fun StatusWidget(env: WidgetEnv) {
    val data = env.model.data
    val buddy = data.player.buddy
    if (!data.onboarded || buddy == null) return
    val density = LocalDensity.current.density
    val gap = Modifier.padding(horizontal = pmdRowPad(env.pixel, density))
    PmdBox(env.pixel, Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PmdText("${buddy.nickname}  Lv. ${buddy.level}", env.pixel, gap.clickable(role = Role.Button, onClickLabel = "Open PokéLife") { env.host.openApp(Place.HOME) })
            Box(Modifier.weight(1f))
            val forecast = env.forecast
            if (forecast != null) PmdText("${degrees(forecast.temperature, env.prefs.fahrenheit)}  ${WeatherCodes.label(forecast.code)}", env.pixel, gap.clickable(role = Role.Button, onClickLabel = "Open the forecast") { env.onForecast() }, alpha = 0.9f)
            else PmdText(if (data.focus != null) "Focusing" else "${data.player.pokemon.size} Pokémon", env.pixel, gap, alpha = 0.75f)
        }
    }
}

@Composable
private fun NavWidget(env: WidgetEnv) {
    val data = env.model.data
    if (!data.onboarded) return
    val running = data.focus != null
    // Half the screen or less, or a short box, shows the pictures alone: the names would be cut off.
    val cells = LocalCells.current
    val labels = cells.w >= 4 && cells.h >= 3
    PmdBox(env.pixel, Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            PmdNavItem("PokéLife", Tab.HOME.icon, env.pixel, Modifier.weight(1f), showLabel = labels) { env.host.openApp(if (running) Place.FOCUS else Place.HOME) }
            PmdNavItem("Pokémon", Tab.COLLECTION.icon, env.pixel, Modifier.weight(1f), showLabel = labels) { env.host.openApp(Place.COLLECTION) }
            PmdNavItem("Me", Tab.ME.icon, env.pixel, Modifier.weight(1f), showLabel = labels) { env.host.openApp(Place.ME) }
        }
    }
}

@Composable
private fun OftenWidget(env: WidgetEnv, size: WidgetSize) {
    val suggested = env.prefs.suggested(env.apps)
    if (suggested.isEmpty()) return
    AppsWidget("Often used", suggested, env, size, hint = "", icons = env.prefs.icons, onTitle = null)
}

/** A titled box of apps, four to a row: Favorites, Often used or a folder. Tap an app to open it, press and hold for its sheet; a folder's title opens the folder. */
@Composable
private fun AppsWidget(title: String, apps: List<LauncherApp>, env: WidgetEnv, size: WidgetSize, hint: String, icons: Map<String, String>, onTitle: (() -> Unit)?) {
    val density = LocalDensity.current.density
    val pad = pmdRowPad(env.pixel, density)
    PmdBox(env.pixel, Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            // Favorites has no label (what it is is plain); a folder and Often used carry a small one, and a folder's opens the folder.
            if (title.isNotEmpty()) {
                val titleModifier = Modifier.fillMaxWidth().let { if (onTitle != null) it.clickable(role = Role.Button, onClickLabel = "Open the $title folder") { onTitle() } else it }
                Box(titleModifier.padding(horizontal = pad, vertical = Spacing.S), contentAlignment = Alignment.CenterStart) { PmdText(title, env.pixel, alpha = 0.7f) }
            }
            if (apps.isEmpty()) PmdText(hint, env.pixel, Modifier.padding(horizontal = pad, vertical = Spacing.M), alpha = 0.7f)
            else Column(Modifier.fillMaxWidth()) {
                // Small shows one row of apps, medium two, large all of them (and the rest, on a smaller size, are counted).
                // Half the screen holds two to a row, and a box too short for a name shows the pictures alone.
                val cells = LocalCells.current
                val perRow = if (cells.narrow) 2 else 4
                val labels = cells.h >= 3
                val rows = apps.chunked(perRow)
                val asked = when (size) { WidgetSize.SMALL -> 1; WidgetSize.MEDIUM -> 2; WidgetSize.LARGE -> rows.size }
                // On the grid only as many rows as the box is tall enough for (a row of apps is 80 dp with names, 56 without).
                val fit = ((cells.h * WidgetGrid.ROW_DP - 22) / if (labels) 80 else 56).coerceAtLeast(1)
                val shown = minOf(asked, fit)
                rows.take(shown).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { app -> PinnedApp(app, icons[app.key], env.host, env.pixel, Modifier.weight(1f), onHold = { env.onHold(app) }, showLabel = labels) }
                        repeat(perRow - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
                if (rows.size > shown && cells.h >= 4) PmdText("${apps.size - rows.take(shown).sumOf { it.size }} more", env.pixel, Modifier.padding(horizontal = pad, vertical = Spacing.S), alpha = 0.7f)
            }
        }
    }
}

@Composable
private fun ActionsWidget(env: WidgetEnv) {
    val folders = if (env.prefs.folders.isEmpty()) "Folders" else "Folders ${env.prefs.folders.size}"
    PmdBox(env.pixel, Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            if (LocalCells.current.narrow) {
                // Half the screen: one button to a line, the names whole.
                PmdMenuLine(folders, env.pixel, Modifier.fillMaxWidth(), center = true, icon = "pa-info-box", onClick = env.onFolders)
                PmdMenuLine("All apps", env.pixel, Modifier.fillMaxWidth(), center = true, icon = "pa-layout", onClick = env.onAllApps)
                PmdMenuLine("Widgets", env.pixel, Modifier.fillMaxWidth(), center = true, icon = "pa-grid-2x2-2", onClick = env.onWidgets)
                PmdMenuLine("Gear", env.pixel, Modifier.fillMaxWidth(), center = true, icon = "pa-sliders", onClick = env.onGear)
            } else {
                Row(Modifier.fillMaxWidth()) {
                    PmdMenuLine(folders, env.pixel, Modifier.weight(1f), center = true, icon = "pa-info-box", onClick = env.onFolders)
                    PmdMenuLine("All apps", env.pixel, Modifier.weight(1f), center = true, icon = "pa-layout", onClick = env.onAllApps)
                }
                Row(Modifier.fillMaxWidth()) {
                    PmdMenuLine("Widgets", env.pixel, Modifier.weight(1f), center = true, icon = "pa-grid-2x2-2", onClick = env.onWidgets)
                    PmdMenuLine("Gear", env.pixel, Modifier.weight(1f), center = true, icon = "pa-sliders", onClick = env.onGear)
                }
            }
        }
    }
}

@Composable
private fun ForecastWidget(env: WidgetEnv, size: WidgetSize) {
    val density = LocalDensity.current.density
    val pad = pmdRowPad(env.pixel, density)
    val forecast = env.forecast
    PmdBox(env.pixel, Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Open the forecast") { env.onForecast() }) {
        Column(Modifier.fillMaxWidth()) {
            if (forecast == null) PmdText(if (env.prefs.place == null) "Forecast: choose a place in the launcher settings" else "Forecast: no connection yet", env.pixel, Modifier.padding(horizontal = pad, vertical = Spacing.S), alpha = 0.8f)
            else {
                PmdText("${forecast.place}", env.pixel, Modifier.padding(horizontal = pad, vertical = Spacing.S), alpha = 0.75f)
                PmdText("${degrees(forecast.temperature, env.prefs.fahrenheit)}  ${WeatherCodes.label(forecast.code)}", env.pixel, Modifier.padding(horizontal = pad, vertical = Spacing.S))
                if (size != WidgetSize.SMALL) Row(Modifier.fillMaxWidth().padding(top = Spacing.S)) {
                    forecast.days.drop(1).take(if (size == WidgetSize.MEDIUM) 3 else 4).forEach { day ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                            PmdText(runCatching { dayName(LocalDate.parse(day.date).dayOfWeek).take(3) }.getOrDefault(""), env.pixel, alpha = 0.75f)
                            PmdText(degrees(day.max, env.prefs.fahrenheit), env.pixel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarWidget(env: WidgetEnv, state: TodayState, size: WidgetSize) {
    val model = env.model
    val pixel = env.pixel
    val today = model.store.today
    val density = LocalDensity.current.density
    val open = model.data.tasks.filter { it.state == TaskState.OPEN }
    fun busy(d: LocalDate) = model.data.events.any { it.isOn(d) } || state.device.any { it.isOn(d) } || open.any { it.planned == d || it.due?.date == d }
    PmdBox(pixel, Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PmdMenuLine("Prev", pixel, Modifier.weight(1f).semantics { contentDescription = "Previous month" }, center = true) { state.month = state.month.plus(DatePeriod(months = -1)) }
                Box(Modifier.weight(2f), contentAlignment = Alignment.Center) { PmdText("${monthName(state.month.month).take(3)} ${state.month.year}", pixel) }
                PmdMenuLine("Next", pixel, Modifier.weight(1f).semantics { contentDescription = "Next month" }, center = true) { state.month = state.month.plus(DatePeriod(months = 1)) }
            }
            PmdRule(pixel)
            Row(Modifier.fillMaxWidth().padding(top = Spacing.S)) {
                weekDays(env.prefs.sundayFirst).forEach { Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { PmdText(dayName(it).take(2), pixel, alpha = 0.7f) } }
            }
            // Weeks start on Sunday or Monday as chosen; the first row is padded with the end of the month before.
            val lead = weekDays(env.prefs.sundayFirst).indexOf(state.month.dayOfWeek)
            val days = (0 until 42).map { state.month.plus(DatePeriod(days = it - lead)) }
            // The rows are as tall as the box leaves room for (a box holds [WidgetGrid.ROW_DP] per cell; the header, the rule and the weekday
            // names take about 104 dp): at most Target, at least 24 dp. When even the smallest rows do not all fit, only the weeks around the
            // chosen day (or the month's first week when another month is showing) are shown, so nothing is cut off at any size.
            val all = days.chunked(7).filter { w -> w.any { it.month == state.month.month } }
            val room = (LocalCells.current.h * WidgetGrid.ROW_DP - 104).coerceAtLeast(24)
            val fit = maxOf(1, room / 24)
            val shown = minOf(all.size, fit, if (size == WidgetSize.SMALL) 1 else all.size)
            val centre = all.indexOfFirst { state.day in it }.coerceAtLeast(0)
            val first = (centre - shown / 2).coerceIn(0, all.size - shown)
            val weeks = all.subList(first, first + shown)
            val rowHeight = (room / shown).coerceIn(24, 48).dp
            weeks.forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { d ->
                        val inMonth = d.month == state.month.month
                        Box(
                            Modifier.weight(1f).height(rowHeight)
                                .background(if (d == today) Pixel.Red else if (d == state.day) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                                .clickable(role = Role.Button, onClickLabel = "Show ${d.day} ${monthName(d.month)}") { state.day = d; if (!inMonth) state.month = LocalDate(d.year, d.month, 1) }
                                .semantics { contentDescription = "${d.day} ${monthName(d.month)}" + if (busy(d)) ", has plans" else "" },
                            contentAlignment = Alignment.Center,
                        ) {
                            PmdText(d.day.toString(), pixel, alpha = if (inMonth) 1f else 0.4f)
                            if (busy(d)) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = (2 * pixel / density).dp).size((2 * pixel / density).dp).background(Pixel.Yellow))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaWidget(env: WidgetEnv, state: TodayState, size: WidgetSize) {
    val model = env.model
    val pixel = env.pixel
    val today = model.store.today
    val density = LocalDensity.current.density
    val pad = pmdRowPad(pixel, density)
    val day = state.day
    val open = model.data.tasks.filter { it.state == TaskState.OPEN }
    // The app's own events and the phone's calendars (Google Calendar's among them), all-day ones first and then by the hour; then the open tasks of the day.
    fun eventsOn(d: LocalDate): List<AgendaLine> = (model.data.events.flatMap { it.occurrences(d, d) }.map { e -> AgendaLine(if (e.allDay) "All day" else formatTime(e.start.hour, e.start.minute, null, env.prefs.hour12), e.title, if (e.allDay) -1 else e.start.hour * 60 + e.start.minute) } +
        state.device.filter { it.isOn(d) }.map { e ->
            val from = e.span(d)?.first
            AgendaLine(if (e.allDay || from == null) "All day" else formatTime(from / 60, from % 60, null, env.prefs.hour12), e.shownTitle, if (e.allDay || from == null) -1 else from)
        }).sortedBy { it.sort }
    fun tasksOn(d: LocalDate) = if (d == today) model.store.todayTasks().filter { it.state == TaskState.OPEN } else open.filter { it.planned == d || it.due?.date == d }
    val lines = eventsOn(day)
    val tasks = tasksOn(day)
    // Small lists three lines, medium six, large eight; on the grid, no more whole rows than its cells hold under the frame and the heading, so none is cut in half.
    val cells = LocalCells.current
    val unit = pixel / density
    val fits = if (!LocalGridFill.current) Int.MAX_VALUE
        else ((cells.h * WidgetGrid.ROW_DP - 12 * unit - (PmdFont.LINE * unit + 2 * Spacing.M.value + unit)) / Spacing.Target.value).toInt().coerceAtLeast(1)
    val source = model.calendars
    // The line that asks for Google Calendar takes a row of its own at the bottom.
    val asks = source != null && (!source.permitted() || model.data.shownCalendars.isEmpty())
    val room = (minOf(fits, when (size) { WidgetSize.SMALL -> 3; WidgetSize.MEDIUM -> 6; WidgetSize.LARGE -> 8 }) - if (asks) 1 else 0).coerceAtLeast(1)
    // When not everything fits, the last row says how many more there are.
    val total = lines.size + tasks.size
    val listed = if (total > room) (room - 1).coerceAtLeast(1) else room
    val shownEvents = lines.take(listed)
    val shownTasks = tasks.take((listed - shownEvents.size).coerceAtLeast(0))
    val more = total - shownEvents.size - shownTasks.size
    // A light day leaves room: the next day with anything on it (within a week) fills it, under its own heading, so the box is not mostly empty.
    val spare = room - shownEvents.size - shownTasks.size - (if (lines.isEmpty() && tasks.isEmpty()) 1 else 0) - 1
    val ahead = if (spare <= 0 || more > 0) null else (1..7).asSequence().map { day.plus(DatePeriod(days = it)) }
        .map { d -> Triple(d, eventsOn(d), tasksOn(d)) }.firstOrNull { (_, e, t) -> e.isNotEmpty() || t.isNotEmpty() }
    val aheadEvents = ahead?.second?.take(spare).orEmpty()
    val aheadTasks = ahead?.third?.take(spare - aheadEvents.size).orEmpty()
    // The time column is as wide as the widest time shown, not a share of the row, so a short title is not pushed to the middle.
    val font by produceState(PmdFont.cachedOrNull()) { value = PmdFont.load() }
    val timeWidth = font?.let { f -> ((shownEvents + aheadEvents).maxOfOrNull { f.width(f.clean(it.time)) } ?: 0) * pixel / density }?.dp
    fun label(d: LocalDate) = when (d) { today -> "Today"; today.plus(DatePeriod(days = 1)) -> "Tomorrow"; else -> "${dayName(d.dayOfWeek)} ${d.day} ${monthName(d.month)}" }

    @Composable
    fun EventRow(e: AgendaLine, alpha: Float) {
        Row(Modifier.fillMaxWidth().height(Spacing.Target).padding(horizontal = pad), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PmdText(e.time, pixel, if (timeWidth != null) Modifier.width(timeWidth) else Modifier.weight(0.5f), alpha = 0.75f * alpha)
            PmdText(e.title, pixel, Modifier.weight(1f), alpha = alpha)
        }
    }

    // Completing a task here finishes it and pays it, like the Plan tab; the box is the whole row.
    @Composable
    fun TaskRow(t: app.pokeplan.plan.Task, alpha: Float) {
        Row(
            Modifier.fillMaxWidth().height(Spacing.Target).clickable(role = Role.Checkbox, onClickLabel = "Finish ${t.title}") { model.completeTask(t.id) }
                .padding(horizontal = pad).semantics { contentDescription = "Task ${t.title}, not done" },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            Box(Modifier.size((7 * pixel / density).dp).border((pixel / density).dp, Color.White.copy(alpha = alpha)))
            PmdText(t.title, pixel, Modifier.weight(1f), alpha = alpha)
        }
    }

    // A list reads from the top: on the grid it starts under the frame, not in the middle of its cells.
    PmdBox(pixel, Modifier.fillMaxWidth(), alignTop = true) {
        Column(Modifier.fillMaxWidth()) {
            PmdText(label(day), pixel, Modifier.padding(horizontal = pad, vertical = Spacing.M))
            PmdRule(pixel)
            if (lines.isEmpty() && tasks.isEmpty()) PmdText("Nothing planned", pixel, Modifier.padding(horizontal = pad, vertical = Spacing.M), alpha = 0.7f)
            shownEvents.forEach { EventRow(it, 1f) }
            shownTasks.forEach { TaskRow(it, 1f) }
            if (more > 0) PmdText("$more more", pixel, Modifier.padding(horizontal = pad, vertical = Spacing.S), alpha = 0.7f)
            if (ahead != null) {
                PmdText(label(ahead.first), pixel, Modifier.padding(start = pad, end = pad, top = Spacing.L, bottom = Spacing.S), alpha = 0.7f)
                aheadEvents.forEach { EventRow(it, 0.7f) }
                aheadTasks.forEach { TaskRow(it, 0.7f) }
            }
            // Google Calendar, like the app: needs the calendar permission, and the calendars chosen in PokéLife's settings.
            if (source != null && !source.permitted()) PmdMenuLine("Show Google Calendar", pixel, dim = true) { source.requestPermission { state.permissionAnswers++ } }
            else if (source != null && model.data.shownCalendars.isEmpty()) PmdMenuLine("Choose calendars in PokéLife: Me, Settings", pixel, dim = true) { env.host.openApp(app.pokeplan.ui.Place.ME) }
        }
    }
}

/** One event line of the agenda: its time ("All day" or the hour), its title, and where it sorts (all-day first). */
private class AgendaLine(val time: String, val title: String, val sort: Int)

/** The days of the week in the order a calendar row shows them. */
internal fun weekDays(sundayFirst: Boolean): List<DayOfWeek> =
    if (sundayFirst) listOf(DayOfWeek.SUNDAY) + DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY } else DayOfWeek.entries
