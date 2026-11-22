package app.pokeplan.ui.plan

import app.pokeplan.ui.theme.Sizes
import app.pokeplan.ui.theme.DialogueShape

import app.pokeplan.ui.theme.PixelShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pokeplan.plan.CalendarEvent
import app.pokeplan.plan.DaySpan
import app.pokeplan.plan.SpanLayout
import app.pokeplan.plan.Task
import app.pokeplan.plan.isOwn
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import app.pokeplan.ui.pokemon.label
import app.pokeplan.ui.screens.dayMonth
import app.pokeplan.ui.screens.dayNames
import app.pokeplan.ui.screens.label
import app.pokeplan.ui.screens.short

internal class MonthGrid(val first: LocalDate, val days: List<LocalDate>) {
    val weeks: List<List<LocalDate>> get() = days.chunked(7)
    val label: String get() = "${fullMonthNames[first.monthNumber - 1]} ${first.year}"
}

private val fullMonthNames = listOf(
    "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December",
)

internal fun monthGrid(today: LocalDate, offset: Int, mondayFirst: Boolean): MonthGrid {
    val shifted = LocalDate(today.year, today.monthNumber, 1).plus(DatePeriod(months = offset))
    val first = LocalDate(shifted.year, shifted.monthNumber, 1)
    val lead = (first.dayOfWeek.isoDayNumber - (if (mondayFirst) 1 else 7) + 7) % 7
    val length = first.daysUntil(first.plus(DatePeriod(months = 1)))
    val weeks = (lead + length + 6) / 7
    val start = first.minus(DatePeriod(days = lead))
    return MonthGrid(first, (0 until weeks * 7).map { start.plus(DatePeriod(days = it)) })
}

private class MonthItem(val id: String, val title: String, val color: Color, val event: CalendarEvent? = null)

internal val MonthBarHeight = 15.dp
private const val MAX_LANES = 4
private const val MIN_LANES = 2

internal val MonthTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontSize = 10.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MonthView(
    model: AppModel, month: MonthGrid, tasks: List<Task>, events: List<CalendarEvent>,
    offset: Int, setOffset: (Int) -> Unit, mode: Int, setMode: (Int) -> Unit,
    openDay: (LocalDate) -> Unit, canOpen: (LocalDate) -> Boolean,
) {
    val store = model.store
    val today = store.today
    var chosen by remember(month.first) { mutableStateOf(if (today.monthNumber == month.first.monthNumber && today.year == month.first.year) today else month.first) }
    var picked by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<Draft?>(null) }
    val pickedEvent = model.data.events.flatMap { it.occurrences(month.days.first(), month.days.last()) }.firstOrNull { it.id == picked }
    val open = tasks.filter { it.isOpen && (it.planned ?: it.due?.date) != null }

    val spans = events.filter { it.allDay }.map { DaySpan(MonthItem(it.id, it.title, eventColor(it), it), it.start.date, it.lastDay) } +
        events.filter { !it.allDay }.sortedBy { it.start }.map { DaySpan(MonthItem(it.id, it.title, eventColor(it), it), it.start.date, it.start.date) } +
        open.map { t -> (t.planned ?: t.due!!.date).let { d -> DaySpan(MonthItem(t.id, t.title, taskColor(t)), d, d) } }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(month.label, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall)
            ArrowButton("‹", "Month before", enabled = true) { setOffset(offset - 1) }
            ArrowButton("›", "Next month", enabled = true) { setOffset(offset + 1) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            Segmented(listOf("Day", "Week", "Month"), mode, setMode)
            if (offset != 0) PixelButton("Today", { setOffset(0) }, color = Pixel.InkSoft, compact = true)
            Spacer(Modifier.weight(1f))
            PixelButton("New event", { draft = Draft("", chosen, 9 * 60, 60, allDay = true); picked = null }, color = OwnEventColor, compact = true)
        }
        draft?.let { d -> NewEventPanel(d, { draft = it }, { d.addTo(store); draft = null; chosen = d.day }, party = model.data.player.partyOf(app.pokeplan.core.pokemon.PartyIds.PLAN)) { draft = null } }
        if (pickedEvent != null) EventPanel(model, pickedEvent, { picked = it }) { picked = null }

        val shape = PixelShape
        Column(
            Modifier.fillMaxWidth().background(Pixel.Panel, shape).border(Sizes.Hairline, Pixel.Border, shape).padding(Spacing.XS),
            verticalArrangement = Arrangement.spacedBy(Spacing.XS),
        ) {
            Row(Modifier.fillMaxWidth()) {
                month.weeks.first().forEach { d ->
                    Text(dayNames[d.dayOfWeek.ordinal].take(2), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft, textAlign = TextAlign.Center)
                }
            }
            month.weeks.forEach { week ->
                MonthWeek(week, month.first.monthNumber, today, chosen, SpanLayout.lay(spans, week), { chosen = it }) { id -> picked = if (picked == id) null else id; draft = null }
            }
        }
        DayAgenda(chosen, today, events.filter { it.isOn(chosen) }, open.filter { (it.planned ?: it.due?.date) == chosen }, picked, { id -> picked = id; draft = null }, canOpen(chosen)) { openDay(chosen) }
    }
}

@Composable
private fun MonthWeek(
    week: List<LocalDate>, monthNumber: Int, today: LocalDate, chosen: LocalDate, bars: List<SpanLayout.Bar<MonthItem>>,
    choose: (LocalDate) -> Unit, pick: (String) -> Unit,
) {
    val hidden = week.indices.map { i -> bars.count { it.lane >= MAX_LANES && i in it.from..it.to } }
    val lanes = minOf(MAX_LANES, (bars.maxOfOrNull { it.lane } ?: -1) + 1)
    val room = maxOf(lanes, MIN_LANES)
    val more = hidden.any { it > 0 }
    val lane = MonthBarHeight + BarGap
    Column {
        Row(Modifier.fillMaxWidth()) {
            week.forEach { d ->
                val isToday = d == today
                val on = d == chosen
                Box(
                    Modifier.weight(1f).height(30.dp).clickable(role = Role.Button, onClickLabel = "Show ${d.short()}") { choose(d) }
                        .semantics { contentDescription = d.short() + if (isToday) ", today" else "" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(26.dp).clip(DialogueShape)
                            .background(if (isToday) Pixel.Red else if (on) Pixel.Shade else Color.Transparent)
                            .border(if (on && !isToday) 1.dp else 0.dp, if (on && !isToday) Pixel.Ink else Color.Transparent, DialogueShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${d.day}", style = MaterialTheme.typography.labelMedium,
                            color = when { isToday -> Color.White; d.monthNumber != monthNumber -> Pixel.Gray; else -> Pixel.Ink },
                        )
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(lane * room + if (more) MonthBarHeight else 0.dp)) {
            val column = maxWidth / 7
            Row(Modifier.matchParentSize()) {
                week.forEach { d -> Box(Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button, onClickLabel = "Show ${d.short()}") { choose(d) }) }
            }
            bars.filter { it.lane < MAX_LANES }.forEach { b ->
                val place = Modifier.offset(x = column * b.from + 1.dp, y = lane * b.lane).width(column * (b.to - b.from + 1) - 2.dp)
                val e = b.item.event
                if (e != null && e.allDay) AllDayBar(e, false, pick, place, small = true)
                else MonthLabel(b.item, place, pick)
            }
            hidden.forEachIndexed { i, n ->
                if (n > 0) Text(
                    "+$n", Modifier.offset(x = column * i, y = lane * lanes).width(column).height(MonthBarHeight)
                        .clickable(role = Role.Button, onClickLabel = "Show ${week[i].short()}") { choose(week[i]) }
                        .semantics { contentDescription = "$n more on ${week[i].short()}" },
                    style = MonthTextStyle, color = Pixel.InkSoft, textAlign = TextAlign.Center, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MonthLabel(item: MonthItem, modifier: Modifier, pick: (String) -> Unit) {
    val shape = PixelShape
    val own = item.event?.isOwn == true
    Text(
        item.title,
        modifier.height(MonthBarHeight).clip(shape).background(item.color.copy(alpha = 0.2f), shape)
            .then(if (own) Modifier.clickable(role = Role.Button, onClickLabel = "Edit ${item.title}") { pick(item.id) } else Modifier)
            .padding(horizontal = 3.dp),
        style = MonthTextStyle, color = Pixel.Ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayAgenda(
    day: LocalDate, today: LocalDate, events: List<CalendarEvent>, tasks: List<Task>, picked: String?, pick: (String) -> Unit,
    canOpen: Boolean, open: () -> Unit,
) {
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (day == today) "Today, ${day.dayMonth()}" else day.short(), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall)
                if (canOpen) PixelButton("Open day", open, color = Pixel.InkSoft, compact = true)
            }
            val allDay = events.filter { it.allDay }
            val timed = events.filter { !it.allDay }.sortedBy { it.start }
            if (allDay.isEmpty() && timed.isEmpty() && tasks.isEmpty()) {
                Text("Nothing on this day.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                return@Column
            }
            if (allDay.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                allDay.forEach { AllDayBar(it, it.id == picked, pick) }
            }
            timed.forEach { e ->
                Row(
                    Modifier.fillMaxWidth().then(if (e.isOwn) Modifier.clickable(role = Role.Button, onClickLabel = "Edit ${e.title}") { pick(e.id) } else Modifier),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                ) {
                    Box(Modifier.size(10.dp).background(eventColor(e), PixelShape))
                    Text(if (e.start.date == day) clock(e.start.time) else "…", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    Text(e.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            tasks.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Box(Modifier.size(10.dp).border(Sizes.Line, taskColor(t), PixelShape))
                    (t.plannedTime ?: t.due?.time)?.let { Text(clock(it), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
                    Text(t.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
