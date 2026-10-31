package app.pokeplan.ui.launcher

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import app.pokeplan.plan.TaskState
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Place
import app.pokeplan.ui.Tab
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PmdText
import app.pokeplan.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** What every widget needs from the launcher: the model, the apps, the prefs, the phone, the forecast and what to do on a tap. */
internal class WidgetEnv(
    val model: AppModel, val apps: List<LauncherApp>, val prefs: LauncherPrefs, val host: LauncherHost, val forecast: Forecast?, val pixel: Int,
    val today: TodayState, val mood: BuddyMood, val editing: Boolean, val onPrefs: (LauncherPrefs) -> Unit, val onEdit: () -> Unit, val onDone: () -> Unit, val onAllApps: () -> Unit, val onFolders: () -> Unit, val onWidgets: () -> Unit, val onOpenFolder: (String) -> Unit, val onForecast: () -> Unit, val onHold: (LauncherApp) -> Unit,
)

/** The month and day the calendar and agenda widgets share, so choosing a day in one shows its agenda in the other. */
internal class TodayState(today: LocalDate) {
    var month by mutableStateOf(LocalDate(today.year, today.month, 1))
    var day by mutableStateOf(today)
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
private fun WidgetBody(slot: WidgetSlot, env: WidgetEnv) {
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
        WidgetKind.ANDROID -> slot.arg.toIntOrNull()?.let { id -> PmdBox(env.pixel, Modifier.fillMaxWidth()) { env.host.HostedWidget(id, slot.size.ordinal, Modifier.fillMaxWidth()) } }
    }
}

@Composable
private fun ClockWidget(env: WidgetEnv) {
    val density = LocalDensity.current.density
    val gap = Modifier.padding(horizontal = pmdRowPad(env.pixel, density))
    var battery by remember { mutableStateOf(env.host.battery()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); battery = env.host.battery() } }
    PmdBox(env.pixel, Modifier.fillMaxWidth()) {
        // The seconds tick in the model, so reading it keeps the minute right.
        env.model.now
        val now = env.model.store.now
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PmdText("${formatDate(now.date, env.prefs.dateStyle)}   ${formatTime(now.hour, now.minute, if (env.prefs.showSeconds) now.second else null, env.prefs.hour12)}", env.pixel, gap)
            Box(Modifier.weight(1f))
            battery?.let { PmdText((if (it.charging) "Charging " else "Battery ") + it.percent, env.pixel, gap, alpha = 0.75f) }
        }
    }
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
    PmdBox(env.pixel, Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            PmdNavItem("PokéLife", Tab.HOME.icon, env.pixel, Modifier.weight(1f)) { env.host.openApp(if (running) Place.FOCUS else Place.HOME) }
            PmdNavItem("Pokémon", Tab.COLLECTION.icon, env.pixel, Modifier.weight(1f)) { env.host.openApp(Place.COLLECTION) }
            PmdNavItem("Me", Tab.ME.icon, env.pixel, Modifier.weight(1f)) { env.host.openApp(Place.ME) }
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
                val rows = apps.chunked(4)
                val shown = when (size) { WidgetSize.SMALL -> 1; WidgetSize.MEDIUM -> 2; WidgetSize.LARGE -> rows.size }
                rows.take(shown).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { app -> PinnedApp(app, icons[app.key], env.host, env.pixel, Modifier.weight(1f), onHold = { env.onHold(app) }) }
                        repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
                if (rows.size > shown) PmdText("${apps.size - rows.take(shown).sumOf { it.size }} more", env.pixel, Modifier.padding(horizontal = pad, vertical = Spacing.S), alpha = 0.7f)
            }
        }
    }
}

@Composable
private fun ActionsWidget(env: WidgetEnv) {
    PmdBox(env.pixel, Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            PmdMenuLine(if (env.prefs.folders.isEmpty()) "Folders" else "Folders ${env.prefs.folders.size}", env.pixel, Modifier.weight(1f), center = true, onClick = env.onFolders)
            PmdMenuLine("All apps", env.pixel, Modifier.weight(1f), center = true, onClick = env.onAllApps)
            PmdMenuLine("Widgets", env.pixel, Modifier.weight(1f), center = true, onClick = env.onWidgets)
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
    fun busy(d: LocalDate) = model.data.events.any { it.isOn(d) } || open.any { it.planned == d || it.due?.date == d }
    PmdBox(pixel, Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PmdMenuLine("Prev", pixel, Modifier.weight(1f), center = true) { state.month = state.month.plus(DatePeriod(months = -1)) }
                Box(Modifier.weight(2f), contentAlignment = Alignment.Center) { PmdText("${monthName(state.month.month)} ${state.month.year}", pixel) }
                PmdMenuLine("Next", pixel, Modifier.weight(1f), center = true) { state.month = state.month.plus(DatePeriod(months = 1)) }
            }
            PmdRule(pixel)
            Row(Modifier.fillMaxWidth().padding(top = Spacing.S)) {
                weekDays(env.prefs.sundayFirst).forEach { Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { PmdText(dayName(it).take(2), pixel, alpha = 0.7f) } }
            }
            // Weeks start on Sunday or Monday as chosen; the first row is padded with the end of the month before.
            val lead = weekDays(env.prefs.sundayFirst).indexOf(state.month.dayOfWeek)
            val days = (0 until 42).map { state.month.plus(DatePeriod(days = it - lead)) }
            // Small shows only the week of the chosen day (or the month's first week when another month is showing).
            val weeks = days.chunked(7).filter { w -> w.any { it.month == state.month.month } }.let { all ->
                if (size == WidgetSize.SMALL) listOf(all.firstOrNull { state.day in it } ?: all.first()) else all
            }
            weeks.forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { d ->
                        val inMonth = d.month == state.month.month
                        Box(
                            Modifier.weight(1f).height(Spacing.Target)
                                .background(if (d == today) Pixel.Red else if (d == state.day) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                                .clickable(role = Role.Button, onClickLabel = "Show ${d.dayOfMonth} ${monthName(d.month)}") { state.day = d; if (!inMonth) state.month = LocalDate(d.year, d.month, 1) }
                                .semantics { contentDescription = "${d.dayOfMonth} ${monthName(d.month)}" + if (busy(d)) ", has plans" else "" },
                            contentAlignment = Alignment.Center,
                        ) {
                            PmdText(d.dayOfMonth.toString(), pixel, alpha = if (inMonth) 1f else 0.4f)
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
    val tasks = if (day == today) model.store.todayTasks().filter { it.state == TaskState.OPEN } else open.filter { it.planned == day || it.due?.date == day }
    val events = model.data.events.flatMap { it.occurrences(day, day) }.sortedWith(compareBy({ !it.allDay }, { it.start }))
    // Small lists three lines, medium six, large all.
    val room = when (size) { WidgetSize.SMALL -> 3; WidgetSize.MEDIUM -> 6; WidgetSize.LARGE -> Int.MAX_VALUE }
    val shownEvents = events.take(room)
    val shownTasks = tasks.take((room - shownEvents.size).coerceAtLeast(0))
    PmdBox(pixel, Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            PmdText(if (day == today) "Today" else "${dayName(day.dayOfWeek)} ${day.dayOfMonth} ${monthName(day.month)}", pixel, Modifier.padding(horizontal = pad, vertical = Spacing.M))
            PmdRule(pixel)
            if (events.isEmpty() && tasks.isEmpty()) PmdText("Nothing planned", pixel, Modifier.padding(horizontal = pad, vertical = Spacing.M), alpha = 0.7f)
            shownEvents.forEach { e ->
                Row(Modifier.fillMaxWidth().height(Spacing.Target).padding(horizontal = pad), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                    PmdText(if (e.allDay) "All day" else formatTime(e.start.hour, e.start.minute, null, env.prefs.hour12), pixel, Modifier.weight(0.5f), alpha = 0.75f)
                    PmdText(e.title, pixel, Modifier.weight(1f))
                }
            }
            shownTasks.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().height(Spacing.Target).clickable(role = Role.Checkbox, onClickLabel = "Finish ${t.title}") { model.completeTask(t.id) }
                        .padding(horizontal = pad).semantics { contentDescription = "Task ${t.title}, not done" },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                ) {
                    Box(Modifier.size((7 * pixel / density).dp).border((pixel / density).dp, Color.White))
                    PmdText(t.title, pixel, Modifier.weight(1f))
                }
            }
            if (events.size + tasks.size > shownEvents.size + shownTasks.size) PmdText("${events.size + tasks.size - shownEvents.size - shownTasks.size} more", pixel, Modifier.padding(horizontal = pad, vertical = Spacing.S), alpha = 0.7f)
        }
    }
}

/** The days of the week in the order a calendar row shows them. */
internal fun weekDays(sundayFirst: Boolean): List<DayOfWeek> =
    if (sundayFirst) listOf(DayOfWeek.SUNDAY) + DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY } else DayOfWeek.entries
