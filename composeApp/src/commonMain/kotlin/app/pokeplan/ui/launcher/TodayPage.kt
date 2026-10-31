package app.pokeplan.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PmdText
import app.pokeplan.ui.theme.Spacing
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus

/**
 * The launcher's second page: a month calendar and the agenda of the chosen day, events first and then the tasks planned
 * or due that day, each with a box to tick (today's also list what is overdue, as the Plan tab does). It reads the app's own
 * Planner, never the phone's calendar, so it needs no permission. Tap a day to see its agenda; Prev and Next change month.
 */
@Composable
internal fun TodayPage(model: AppModel, pixel: Int) {
    val today = model.store.today
    var month by remember { mutableStateOf(LocalDate(today.year, today.month, 1)) }
    var day by remember { mutableStateOf(today) }
    val data = model.data
    val density = LocalDensity.current.density
    val open = data.tasks.filter { it.state == TaskState.OPEN }
    fun tasksOn(d: LocalDate) = if (d == today) model.store.todayTasks().filter { it.state == TaskState.OPEN } else open.filter { it.planned == d || it.due?.date == d }
    fun eventsOn(d: LocalDate) = data.events.flatMap { it.occurrences(d, d) }.sortedWith(compareBy({ !it.allDay }, { it.start }))
    fun busy(d: LocalDate) = data.events.any { it.isOn(d) } || open.any { it.planned == d || it.due?.date == d }

    Box(Modifier.fillMaxSize().background(Pixel.Night.copy(alpha = 0.78f)).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = Spacing.Page, end = Spacing.Page, top = Spacing.XXL, bottom = Spacing.XL), verticalArrangement = Arrangement.spacedBy(Spacing.XL)) {
            PmdBox(pixel, Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        PmdMenuLine("Prev", pixel, Modifier.weight(1f)) { month = month.plus(DatePeriod(months = -1)) }
                        PmdText("${monthName(month.month)} ${month.year}", pixel, Modifier.weight(2f))
                        PmdMenuLine("Next", pixel, Modifier.weight(1f), center = true) { month = month.plus(DatePeriod(months = 1)) }
                    }
                    PmdRule(pixel)
                    Row(Modifier.fillMaxWidth().padding(top = Spacing.S)) {
                        DayOfWeek.entries.forEach { Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { PmdText(dayName(it).take(2), pixel, alpha = 0.7f) } }
                    }
                    // Weeks start on Monday; the first row is padded with the end of the month before.
                    val lead = month.dayOfWeek.ordinal
                    val days = (0 until 42).map { month.plus(DatePeriod(days = it - lead)) }
                    days.chunked(7).forEach { week ->
                        if (week.any { it.month == month.month }) Row(Modifier.fillMaxWidth()) {
                            week.forEach { d ->
                                val inMonth = d.month == month.month
                                Box(
                                    Modifier.weight(1f).height(Spacing.Target)
                                        .background(if (d == today) Pixel.Red else if (d == day) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                                        .clickable(role = Role.Button, onClickLabel = "Show ${d.dayOfMonth} ${monthName(d.month)}") { day = d; if (!inMonth) month = LocalDate(d.year, d.month, 1) }
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
            PmdBox(pixel, Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    PmdText(if (day == today) "Today" else "${dayName(day.dayOfWeek)} ${day.dayOfMonth} ${monthName(day.month)}", pixel, Modifier.padding(horizontal = pmdRowPad(pixel, density), vertical = Spacing.M))
                    PmdRule(pixel)
                    val events = eventsOn(day)
                    val tasks = tasksOn(day)
                    if (events.isEmpty() && tasks.isEmpty()) PmdText("Nothing planned", pixel, Modifier.padding(horizontal = pmdRowPad(pixel, density), vertical = Spacing.M), alpha = 0.7f)
                    events.forEach { e ->
                        Row(Modifier.fillMaxWidth().height(Spacing.Target).padding(horizontal = pmdRowPad(pixel, density)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                            PmdText(if (e.allDay) "All day" else "${two(e.start.hour)}:${two(e.start.minute)}", pixel, Modifier.weight(0.4f), alpha = 0.75f)
                            PmdText(e.title, pixel, Modifier.weight(1f))
                        }
                    }
                    tasks.forEach { t ->
                        Row(
                            Modifier.fillMaxWidth().height(Spacing.Target).clickable(role = Role.Checkbox, onClickLabel = "Finish ${t.title}") { model.completeTask(t.id) }
                                .padding(horizontal = pmdRowPad(pixel, density)).semantics { contentDescription = "Task ${t.title}, not done" },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                        ) {
                            Box(Modifier.size((7 * pixel / density).dp).border((pixel / density).dp, Color.White))
                            PmdText(t.title, pixel, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

private fun two(n: Int) = n.toString().padStart(2, '0')

private fun dayName(d: DayOfWeek) = d.name.lowercase().replaceFirstChar { it.uppercase() }

private fun monthName(m: Month) = m.name.lowercase().replaceFirstChar { it.uppercase() }
