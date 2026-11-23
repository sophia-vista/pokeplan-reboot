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

internal class Cells(val w: Int, val h: Int) { val narrow get() = w < WidgetGrid.COLUMNS }
internal val LocalCells = androidx.compose.runtime.staticCompositionLocalOf { Cells(WidgetGrid.COLUMNS, 99) }

internal class WidgetEnv(
    val model: AppModel, val apps: List<LauncherApp>, val prefs: LauncherPrefs, val host: LauncherHost, val forecast: Forecast?, val pixel: Int,
    val today: TodayState, val mood: BuddyMood, val saver: Boolean, val editing: Boolean, val onPrefs: (LauncherPrefs) -> Unit, val onEdit: () -> Unit, val onDone: () -> Unit, val onAllApps: () -> Unit, val onWidgets: () -> Unit, val onGear: () -> Unit, val onForecast: () -> Unit, val onHold: (LauncherApp) -> Unit,
    val pages: Int = 2, val goToPage: (Int) -> Unit = {},
    val battery: BatteryStatus? = null,
)

internal class TodayState(today: LocalDate) {
    var month by mutableStateOf(LocalDate(today.year, today.month, 1))
    var day by mutableStateOf(today)
    var device by mutableStateOf<List<CalendarEvent>>(emptyList())
    var permissionAnswers by mutableIntStateOf(0)
}

@Composable
internal fun LauncherWidget(slot: WidgetSlot, env: WidgetEnv) {
    var height by remember { mutableStateOf(0) }
    var drag by remember { mutableStateOf(0f) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = if (slot.kind == WidgetKind.BUDDY) 0.dp else Spacing.Page).onSizeChanged { height = it.height }.graphicsLayer { translationY = drag; shadowElevation = 0f }
            .then(if (env.editing) Modifier.border(2.dp, Color.White.copy(alpha = 0.55f)) else Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { env.onEdit() }) }),
    ) {
        if (env.editing) EditBar(slot, env, onDrag = { dy ->
            drag += dy
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
        WidgetKind.PINNED -> AppsWidget("", LauncherList.pinned(env.apps, env.prefs), env, slot.size, hint = "Press and hold an app in All apps to pin it", icons = env.prefs.icons)
        WidgetKind.ACTIONS -> ActionsWidget(env)
        WidgetKind.CALENDAR -> CalendarWidget(env, env.today, slot.size)
        WidgetKind.AGENDA -> AgendaWidget(env, env.today, slot.size)
        WidgetKind.FORECAST -> ForecastWidget(env, slot.size)
        WidgetKind.ANDROID -> slot.arg.toIntOrNull()?.let { id ->
            if (env.host.hostsWidgets && env.host.widgetSpec(id) == null) UnavailableWidget(env.pixel)
            else if (LocalGridFill.current) {
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                    env.host.HostedWidget(id, slot.size.ordinal, maxWidth.value.toInt(), maxHeight.value.toInt(), Modifier.fillMaxSize())
                }
            } else PmdBox(env.pixel, Modifier.fillMaxWidth()) { env.host.HostedWidget(id, slot.size.ordinal, 0, 0, Modifier.fillMaxWidth()) }
        }
    }
}

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
        val seconds = env.prefs.showSeconds && !env.saver
        val stamp by remember(seconds) { androidx.compose.runtime.derivedStateOf { if (seconds) env.model.now else env.model.now / 60_000L } }
        stamp
        val now = env.model.store.now
        val cells = LocalCells.current
        val time = formatTime(now.hour, now.minute, if (env.prefs.showSeconds && !env.saver) now.second else null, env.model.data.settings.clock12Hour)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PmdText(if (cells.w >= 3) "${formatDate(now.date, env.model.data.settings.dateStyle)}   $time" else time, env.pixel, gap)
            Box(Modifier.weight(1f))
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
    AppsWidget("Often used", suggested, env, size, hint = "", icons = env.prefs.icons)
}

@Composable
private fun AppsWidget(title: String, apps: List<LauncherApp>, env: WidgetEnv, size: WidgetSize, hint: String, icons: Map<String, String>) {
    val density = LocalDensity.current.density
    val pad = pmdRowPad(env.pixel, density)
    PmdBox(env.pixel, Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            if (title.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().padding(horizontal = pad, vertical = Spacing.S), contentAlignment = Alignment.CenterStart) { PmdText(title, env.pixel, alpha = 0.7f) }
            }
            if (apps.isEmpty()) PmdText(hint, env.pixel, Modifier.padding(horizontal = pad, vertical = Spacing.M), alpha = 0.7f)
            else Column(Modifier.fillMaxWidth()) {
                val cells = LocalCells.current
                val perRow = if (cells.narrow) 2 else 4
                val labels = cells.h >= 3
                val rows = apps.chunked(perRow)
                val asked = when (size) { WidgetSize.SMALL -> 1; WidgetSize.MEDIUM -> 2; WidgetSize.LARGE -> rows.size }
                val rowHeight = PinnedAppRow.height(env.pixel, density, labels)
                val line = (PmdFont.LINE * env.pixel / density).dp
                val titleRoom = if (title.isNotEmpty()) Spacing.S * 2 + line else 0.dp
                val boxRoom = pmdBoxRoomY(env.pixel, density) * 2
                val fit = (((cells.h * WidgetGrid.ROW_DP).dp - boxRoom - titleRoom) / rowHeight).toInt().coerceAtLeast(1)
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
    PmdBox(env.pixel, Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            if (LocalCells.current.narrow) {
                PmdMenuLine("All apps", env.pixel, Modifier.fillMaxWidth(), center = true, icon = "pa-layout", onClick = env.onAllApps)
                PmdMenuLine("Widgets", env.pixel, Modifier.fillMaxWidth(), center = true, icon = "pa-grid-2x2-2", onClick = env.onWidgets)
                PmdMenuLine("Gear", env.pixel, Modifier.fillMaxWidth(), center = true, icon = "pa-sliders", onClick = env.onGear)
            } else {
                Row(Modifier.fillMaxWidth()) {
                    PmdMenuLine("All apps", env.pixel, Modifier.weight(1f), center = true, icon = "pa-layout", onClick = env.onAllApps)
                    PmdMenuLine("Widgets", env.pixel, Modifier.weight(1f), center = true, icon = "pa-grid-2x2-2", onClick = env.onWidgets)
                }
                PmdMenuLine("Gear", env.pixel, Modifier.fillMaxWidth(), center = true, icon = "pa-sliders", onClick = env.onGear)
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
                weekDays(env.model.data.settings.weekStart).forEach { Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { PmdText(dayName(it).take(2), pixel, alpha = 0.7f) } }
            }
            val lead = weekDays(env.model.data.settings.weekStart).indexOf(state.month.dayOfWeek)
            val days = (0 until 42).map { state.month.plus(DatePeriod(days = it - lead)) }
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
    fun eventsOn(d: LocalDate): List<AgendaLine> = (model.data.events.flatMap { it.occurrences(d, d) }.map { e -> AgendaLine(if (e.allDay) "All day" else formatTime(e.start.hour, e.start.minute, null, env.model.data.settings.clock12Hour), e.title, if (e.allDay) -1 else e.start.hour * 60 + e.start.minute) } +
        state.device.filter { it.isOn(d) }.map { e ->
            val from = e.span(d)?.first
            AgendaLine(if (e.allDay || from == null) "All day" else formatTime(from / 60, from % 60, null, env.model.data.settings.clock12Hour), e.shownTitle, if (e.allDay || from == null) -1 else from)
        }).sortedBy { it.sort }
    fun tasksOn(d: LocalDate) = if (d == today) model.store.todayTasks().filter { it.state == TaskState.OPEN } else open.filter { it.planned == d || it.due?.date == d }
    val lines = eventsOn(day)
    val tasks = tasksOn(day)
    val cells = LocalCells.current
    val unit = pixel / density
    val fits = if (!LocalGridFill.current) Int.MAX_VALUE
        else ((cells.h * WidgetGrid.ROW_DP - 12 * unit - (PmdFont.LINE * unit + 2 * Spacing.M.value + unit)) / Spacing.Target.value).toInt().coerceAtLeast(1)
    val source = model.calendars
    val asks = source != null && (!source.permitted() || model.data.shownCalendars.isEmpty())
    val room = (minOf(fits, when (size) { WidgetSize.SMALL -> 3; WidgetSize.MEDIUM -> 6; WidgetSize.LARGE -> 8 }) - if (asks) 1 else 0).coerceAtLeast(1)
    val total = lines.size + tasks.size
    val listed = if (total > room) (room - 1).coerceAtLeast(1) else room
    val shownEvents = lines.take(listed)
    val shownTasks = tasks.take((listed - shownEvents.size).coerceAtLeast(0))
    val more = total - shownEvents.size - shownTasks.size
    val spare = room - shownEvents.size - shownTasks.size - (if (lines.isEmpty() && tasks.isEmpty()) 1 else 0) - 1
    val ahead = if (spare <= 0 || more > 0) null else (1..7).asSequence().map { day.plus(DatePeriod(days = it)) }
        .map { d -> Triple(d, eventsOn(d), tasksOn(d)) }.firstOrNull { (_, e, t) -> e.isNotEmpty() || t.isNotEmpty() }
    val aheadEvents = ahead?.second?.take(spare).orEmpty()
    val aheadTasks = ahead?.third?.take(spare - aheadEvents.size).orEmpty()
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
            if (source != null && !source.permitted()) PmdMenuLine("Show Google Calendar", pixel, dim = true) { source.requestPermission { state.permissionAnswers++ } }
            else if (source != null && model.data.shownCalendars.isEmpty()) PmdMenuLine("Choose calendars in PokéLife: Me, Settings", pixel, dim = true) { env.host.openApp(app.pokeplan.ui.Place.ME) }
        }
    }
}

private class AgendaLine(val time: String, val title: String, val sort: Int)

internal fun weekDays(start: DayOfWeek): List<DayOfWeek> {
    val all = DayOfWeek.entries
    val first = all.indexOf(start)
    return all.drop(first) + all.take(first)
}
