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
    val today: TodayState, val onAllApps: () -> Unit, val onFolders: () -> Unit, val onWidgets: () -> Unit, val onOpenFolder: (String) -> Unit, val onForecast: () -> Unit, val onHold: (LauncherApp) -> Unit,
)

/** The month and day the calendar and agenda widgets share, so choosing a day in one shows its agenda in the other. */
internal class TodayState(today: LocalDate) {
    var month by mutableStateOf(LocalDate(today.year, today.month, 1))
    var day by mutableStateOf(today)
}

/** One widget: a dialogue box with what its [WidgetSlot.kind] shows. Each is its own box, so the user can show, hide and order them (Launcher settings → Widgets). */
@Composable
internal fun LauncherWidget(slot: WidgetSlot, env: WidgetEnv) {
    when (slot.kind) {
        WidgetKind.CLOCK -> ClockWidget(env)
        WidgetKind.STATUS -> StatusWidget(env)
        WidgetKind.NAV -> NavWidget(env)
        WidgetKind.OFTEN -> OftenWidget(env)
        WidgetKind.PINNED -> AppsWidget("Favorites", LauncherList.pinned(env.apps, env.prefs), env, hint = "Press and hold an app in All apps to pin it", icons = env.prefs.icons, onTitle = null)
        WidgetKind.FOLDER -> {
            val folder = env.prefs.folders.firstOrNull { it.name == slot.arg } ?: return
            val inside = folder.apps.mapNotNull { key -> env.apps.firstOrNull { it.key == key } }
            AppsWidget(folder.name, inside, env, hint = "Empty: press and hold an app in All apps and choose Add to folder", icons = emptyMap(), onTitle = { env.onOpenFolder(folder.name) })
        }
        WidgetKind.ACTIONS -> ActionsWidget(env)
        WidgetKind.CALENDAR -> CalendarWidget(env, env.today)
        WidgetKind.AGENDA -> AgendaWidget(env, env.today)
        WidgetKind.FORECAST -> ForecastWidget(env)
        WidgetKind.ANDROID -> slot.arg.toIntOrNull()?.let { id -> PmdBox(env.pixel, Modifier.fillMaxWidth()) { env.host.HostedWidget(id, Modifier.fillMaxWidth()) } }
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
            PmdText("${dayName(now.dayOfWeek).take(3)} ${now.dayOfMonth} ${monthName(now.month).take(3)}   ${two(now.hour)}:${two(now.minute)}", env.pixel, gap)
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
private fun OftenWidget(env: WidgetEnv) {
    val suggested = env.prefs.suggested(env.apps)
    if (suggested.isEmpty()) return
    AppsWidget("Often used", suggested, env, hint = "", icons = env.prefs.icons, onTitle = null)
}

/** A titled box of apps, four to a row: Favorites, Often used or a folder. Tap an app to open it, press and hold for its sheet; a folder's title opens the folder. */
@Composable
private fun AppsWidget(title: String, apps: List<LauncherApp>, env: WidgetEnv, hint: String, icons: Map<String, String>, onTitle: (() -> Unit)?) {
    val density = LocalDensity.current.density
    val pad = pmdRowPad(env.pixel, density)
    PmdBox(env.pixel, Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            val titleModifier = Modifier.fillMaxWidth().height(Spacing.Target).let { if (onTitle != null) it.clickable(role = Role.Button, onClickLabel = "Open the $title folder") { onTitle() } else it }
            Box(titleModifier.padding(horizontal = pad), contentAlignment = Alignment.CenterStart) { PmdText(title, env.pixel, alpha = 0.8f) }
            PmdRule(env.pixel)
            if (apps.isEmpty()) PmdText(hint, env.pixel, Modifier.padding(horizontal = pad, vertical = Spacing.M), alpha = 0.7f)
            else Column(Modifier.fillMaxWidth()) {
                apps.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { app -> PinnedApp(app, icons[app.key], env.host, env.pixel, Modifier.weight(1f), onHold = { env.onHold(app) }) }
                        repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
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
private fun ForecastWidget(env: WidgetEnv) {
    val density = LocalDensity.current.density
    val pad = pmdRowPad(env.pixel, density)
    val forecast = env.forecast
    PmdBox(env.pixel, Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Open the forecast") { env.onForecast() }) {
        Column(Modifier.fillMaxWidth()) {
            if (forecast == null) PmdText(if (env.prefs.place == null) "Forecast: choose a place in the launcher settings" else "Forecast: no connection yet", env.pixel, Modifier.padding(horizontal = pad, vertical = Spacing.S), alpha = 0.8f)
            else {
                PmdText("${forecast.place}", env.pixel, Modifier.padding(horizontal = pad, vertical = Spacing.S), alpha = 0.75f)
                PmdText("${degrees(forecast.temperature, env.prefs.fahrenheit)}  ${WeatherCodes.label(forecast.code)}", env.pixel, Modifier.padding(horizontal = pad, vertical = Spacing.S))
                Row(Modifier.fillMaxWidth().padding(top = Spacing.S)) {
                    forecast.days.drop(1).take(4).forEach { day ->
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
private fun CalendarWidget(env: WidgetEnv, state: TodayState) {
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
                DayOfWeek.entries.forEach { Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { PmdText(dayName(it).take(2), pixel, alpha = 0.7f) } }
            }
            // Weeks start on Monday; the first row is padded with the end of the month before.
            val lead = state.month.dayOfWeek.ordinal
            val days = (0 until 42).map { state.month.plus(DatePeriod(days = it - lead)) }
            days.chunked(7).forEach { week ->
                if (week.any { it.month == state.month.month }) Row(Modifier.fillMaxWidth()) {
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
private fun AgendaWidget(env: WidgetEnv, state: TodayState) {
    val model = env.model
    val pixel = env.pixel
    val today = model.store.today
    val density = LocalDensity.current.density
    val pad = pmdRowPad(pixel, density)
    val day = state.day
    val open = model.data.tasks.filter { it.state == TaskState.OPEN }
    val tasks = if (day == today) model.store.todayTasks().filter { it.state == TaskState.OPEN } else open.filter { it.planned == day || it.due?.date == day }
    val events = model.data.events.flatMap { it.occurrences(day, day) }.sortedWith(compareBy({ !it.allDay }, { it.start }))
    PmdBox(pixel, Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            PmdText(if (day == today) "Today" else "${dayName(day.dayOfWeek)} ${day.dayOfMonth} ${monthName(day.month)}", pixel, Modifier.padding(horizontal = pad, vertical = Spacing.M))
            PmdRule(pixel)
            if (events.isEmpty() && tasks.isEmpty()) PmdText("Nothing planned", pixel, Modifier.padding(horizontal = pad, vertical = Spacing.M), alpha = 0.7f)
            events.forEach { e ->
                Row(Modifier.fillMaxWidth().height(Spacing.Target).padding(horizontal = pad), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                    PmdText(if (e.allDay) "All day" else "${two(e.start.hour)}:${two(e.start.minute)}", pixel, Modifier.weight(0.4f), alpha = 0.75f)
                    PmdText(e.title, pixel, Modifier.weight(1f))
                }
            }
            tasks.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().height(Spacing.Target).clickable(role = Role.Checkbox, onClickLabel = "Finish ${t.title}") { model.completeTask(t.id) }
                        .padding(horizontal = pad).semantics { contentDescription = "Task ${t.title}, not done" },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                ) {
                    Box(Modifier.size((7 * pixel / density).dp).border((pixel / density).dp, Color.White))
                    PmdText(t.title, pixel, Modifier.weight(1f))
                }
            }
        }
    }
}
