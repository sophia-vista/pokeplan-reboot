package app.pokeplan.ui.plan

import app.pokeplan.ui.theme.Sizes
import app.pokeplan.ui.theme.PixelShape
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.pokeplan.plan.CalendarEvent
import app.pokeplan.plan.SpanLayout
import app.pokeplan.plan.DaySpan
import app.pokeplan.plan.TaskList
import app.pokeplan.plan.DayTimeline
import app.pokeplan.plan.EventRepeat
import app.pokeplan.plan.PlanEvent
import app.pokeplan.plan.isOwn
import app.pokeplan.plan.Task
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelKey
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlin.math.roundToInt
import app.pokeplan.ui.pokemon.label
import app.pokeplan.ui.screens.TabButton
import app.pokeplan.ui.screens.UpTo
import app.pokeplan.ui.screens.dayMonth
import app.pokeplan.ui.screens.dayNames
import app.pokeplan.ui.screens.label
import app.pokeplan.ui.screens.long
import app.pokeplan.ui.screens.short

private val HourHeight = 64.dp

private val LabelWidth = 48.dp

private val blockShape = PixelShape

val LocalTaskLists = androidx.compose.runtime.staticCompositionLocalOf<List<TaskList>> { emptyList() }

@Composable
internal fun taskColor(t: Task): Color = LocalTaskLists.current.firstOrNull { it.id == t.listId }?.let { Color(it.color) } ?: Pixel.Gray

@Composable
private fun taskIcon(t: Task): String = t.icon ?: LocalTaskLists.current.firstOrNull { it.id == t.listId }?.icon ?: "heart-scale"

internal fun duration(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

internal fun eventColor(e: CalendarEvent): Color = e.color?.let { Color(it or 0xFF000000L) } ?: if (e.isOwn) OwnEventColor else Pixel.Gray

internal val OwnEventColor get() = Pixel.Teal

private data class Item(val id: String, val title: String, val event: Boolean, val day: LocalDate?, val start: Int?, val minutes: Int)

private fun Task.item() = Item(id, title, false, planned, plannedTime?.let { it.hour * 60 + it.minute }, DayTimeline.minutes(this))

private fun PlanEvent.item() = Item(id, title, true, day, startMinute, minutes)

private class Drag(val item: Item, val resize: Boolean, val grabMinutes: Int, pointer: Offset) {
    var pointer by mutableStateOf(pointer)
}

internal data class Draft(
    val title: String, val day: LocalDate, val start: Int, val minutes: Int,
    val allDay: Boolean = false, val lastDay: LocalDate = day, val color: Long? = null, val repeat: EventRepeat? = null,
    val companion: String? = null,
) {
    fun movedTo(first: LocalDate): Draft = copy(day = first, lastDay = first.plus(DatePeriod(days = day.daysUntil(lastDay))))

    fun addTo(store: app.pokeplan.app.GameStore) {
        if (allDay) store.addAllDayEvent(title, day, lastDay, color, repeat)
        else store.addEvent(title, day, LocalTime(start / 60, start % 60), minutes, color, repeat).also { e -> companion?.let { store.setEventCompanion(e.id, it) } }
    }
}

private data class Target(val day: LocalDate, val start: Int, val minutes: Int)

private class Anchor {
    var coords: LayoutCoordinates? = null
    fun root(local: Offset): Offset = coords?.takeIf { it.isAttached }?.localToRoot(local) ?: local
}

private class Grid {
    var coords: LayoutCoordinates? = null
    var page: LayoutCoordinates? = null
    var days: List<LocalDate> = emptyList()
    var firstMinute = 0
    var hourPx = 1f
    var drag by mutableStateOf<Drag?>(null)

    var scrolled by mutableStateOf(0)

    fun place(c: LayoutCoordinates) {
        coords = c
        if (drag != null) scrolled++
    }

    fun local(pointer: Offset): Offset? = coords?.takeIf { it.isAttached }?.let { pointer - it.positionInRoot() }

    fun minuteAt(y: Float): Int = firstMinute + (y / hourPx * 60).roundToInt()

    fun target(d: Drag): Target? {
        scrolled
        val c = coords?.takeIf { it.isAttached } ?: return null
        val p = local(d.pointer) ?: return null
        if (d.resize) {
            val day = d.item.day ?: return null
            val start = d.item.start ?: return null
            return Target(day, start, DayTimeline.resized(start, minuteAt(p.y) - d.grabMinutes))
        }
        if (p.x < 0 || p.x > c.size.width || p.y < -hourPx / 2 || p.y > c.size.height + hourPx / 2) return null
        val column = (p.x / (c.size.width.toFloat() / days.size)).toInt().coerceIn(0, days.lastIndex)
        return Target(days[column], DayTimeline.snap(minuteAt(p.y) - d.grabMinutes), d.item.minutes)
    }
}

private fun Modifier.dragToPlan(
    key: Any,
    anchor: Anchor,
    onStart: (local: Offset, size: IntSize) -> Unit,
    onMove: (root: Offset) -> Unit,
    onEnd: (dropped: Boolean) -> Unit,
): Modifier = onGloballyPositioned { anchor.coords = it }.pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val start = if (down.type == PointerType.Mouse) awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
        else awaitLongPressOrCancellation(down.id)
        if (start == null) return@awaitEachGesture
        onStart(down.position, size)
        onMove(anchor.root(start.position))
        var moved = down.type == PointerType.Mouse
        val released = drag(start.id) { change ->
            change.consume()
            moved = true
            onMove(anchor.root(change.position))
        }
        onEnd(released && moved)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DayView(model: AppModel, hidden: Set<String>, startWeek: Boolean = false, startMode: Int = if (startWeek) 1 else 0) {
    val store = model.store
    var mode by remember { mutableStateOf(startMode) }
    val week = mode == 1
    var offset by remember { mutableStateOf(0) }
    var weekOffset by remember { mutableStateOf(0) }
    var picked by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<Draft?>(null) }
    var monthOffset by remember { mutableStateOf(0) }
    val month = monthGrid(store.today, monthOffset, model.data.settings.weekStart)
    val firstDow = model.data.settings.weekStart
    val narrow = LocalWindowInfo.current.containerSize.width < 600 * LocalDensity.current.density
    val span = if (narrow) 3 else 7
    val weekFirst = if (narrow) store.today.plus(DatePeriod(days = weekOffset * span))
    else app.pokeplan.plan.Streaks.weekStartOf(store.today, firstDow).plus(DatePeriod(days = weekOffset * 7))
    val days = if (week) (0 until span).map { weekFirst.plus(DatePeriod(days = it)) }
    else listOf(store.today.plus(DatePeriod(days = offset)))
    val range = if (mode == 2) month.days else days
    val tasks = model.data.tasks.filter { it.id !in hidden }
    val source = model.calendars
    val shown = model.data.shownCalendars
    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    LaunchedEffect(source, shown, range.first(), range.size) {
        events = if (source == null || shown.isEmpty()) emptyList()
        else withContext(Dispatchers.Default) { source.events(shown, range.first(), range.last()) }
    }
    val own = model.data.events
    val ownShown = own.flatMap { it.occurrences(range.first(), range.last()) }
    val shownEvents = events + ownShown.map { it.toCalendarEvent() }
    val plans: List<DayTimeline.Plan>
    val tray: List<Task>
    val undated: List<Task>
    val firstHour: Int
    val endHour: Int
    if (week) {
        val w = DayTimeline.week(tasks, days.first(), store.now, days = span, events = shownEvents)
        plans = w.days; tray = w.tray; undated = w.undated; firstHour = w.firstHour; endHour = w.endHour
    } else {
        val p = DayTimeline.build(tasks, days.first(), store.now, events = shownEvents)
        plans = listOf(p); tray = p.tray; undated = p.undated; firstHour = p.firstHour; endHour = p.endHour
    }
    val chosen = tasks.firstOrNull { it.id == picked && it.isOpen }
    val chosenEvent = ownShown.firstOrNull { it.id == picked }
    val pick = { id: String -> picked = if (picked == id) null else id; draft = null }
    // A panel opened over the calendar closes before the Calendar section does (a new event's draft is not lost to Back).
    if (draft != null) UpTo { draft = null } else if (chosen != null) UpTo { picked = null } else if (chosenEvent != null) UpTo { picked = null }

    val grid = remember { Grid() }
    grid.days = days
    grid.firstMinute = firstHour * 60
    grid.hourPx = with(LocalDensity.current) { HourHeight.toPx() }
    fun drop(d: Drag) {
        val target = grid.target(d) ?: return
        val at = LocalTime(target.start / 60, target.start % 60)
        when {
            d.item.event && d.resize -> store.updateEvent(d.item.id, minutes = target.minutes)
            d.item.event -> store.moveEvent(d.item.id, target.day, at)
            d.resize -> store.setEstimate(d.item.id, target.minutes)
            else -> store.scheduleTask(d.item.id, target.day, at)
        }
        picked = null
    }

    if (mode == 2) {
        MonthView(
            model, month, tasks, shownEvents, monthOffset, { monthOffset = it }, mode, { mode = it },
            openDay = { day -> offset = store.today.daysUntil(day); mode = 0 },
            canOpen = { true },
        )
        return
    }

    Box(Modifier.fillMaxWidth().onGloballyPositioned { grid.page = it }) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            val away = if (week) weekOffset != 0 else offset != 0
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                    Text(
                        if (week) "${days.first().dayMonth()} – ${days.last().dayMonth()}"
                        else when (offset) { -1 -> "Yesterday"; 0 -> "Today"; 1 -> "Tomorrow"; else -> dayNames[days.first().dayOfWeek.ordinal] },
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                ArrowButton("‹", if (week) (if (narrow) "Earlier days" else "Earlier week") else "Day before", enabled = true) { if (week) weekOffset -= 1 else offset -= 1 }
                ArrowButton("›", if (week) (if (narrow) "Later days" else "Later week") else "Next day", enabled = true) { if (week) weekOffset += 1 else offset += 1 }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                Segmented(listOf("Day", "Week", "Month"), mode) { mode = it }
                if (away) PixelButton("Today", { offset = 0; weekOffset = 0 }, color = Pixel.InkSoft, compact = true)
                Spacer(Modifier.weight(1f))
                PixelButton("New event", {
                    val day = days.first()
                    val start = if (day == store.today) ((store.now.hour + 1) * 60).coerceIn(firstHour * 60, DayTimeline.LAST_START) else 9 * 60
                    draft = Draft("", day, start, PlanEvent.DEFAULT_MINUTES)
                    picked = null
                }, color = OwnEventColor, compact = true)
            }

            if (!week) {
                DayStrip(store.today, offset, firstDow, busy = { day -> tasks.any { it.isOpen && it.planned == day } || own.any { it.isOn(day) } }) { offset = it }
            }

            draft?.let { d -> NewEventPanel(d, { draft = it }, { d.addTo(store); draft = null }, today = store.today, party = model.data.player.partyOf(app.pokeplan.core.pokemon.PartyIds.PLAN)) { draft = null } }
            if (chosen != null) PickedPanel(model, chosen, days) { picked = null }
            if (chosenEvent != null) EventPanel(model, chosenEvent, { picked = it }) { picked = null }

            Tray(tray + undated, picked, pick, grid, ::drop, hint = model.data.tasks.none { it.plannedTime != null })

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Pixel.Panel, panelShape)
                    .border(Sizes.Hairline, Pixel.Border, panelShape)
                    .padding(start = Spacing.XS, end = Spacing.S, top = Spacing.S, bottom = Spacing.M),
                verticalArrangement = Arrangement.spacedBy(Spacing.S),
            ) {
                if (week) WeekHeader(days, store.today) { day -> offset = store.today.daysUntil(day); mode = 0 }
                AllDayRow(plans, picked, pick)
                val drafted = draft?.let { Target(it.day, it.start, it.minutes) }
                Hours(model, plans, firstHour, endHour, compact = week && !narrow, picked, pick, ownShown.associateBy { it.id }, drafted, grid, ::drop) { day, hour ->
                    val d = draft
                    if (d != null) draft = if (d.allDay) d.movedTo(day) else d.copy(day = day, start = hour * 60, minutes = PlanEvent.fit(hour * 60, d.minutes))
                    else {
                        chosen?.let { store.scheduleTask(it.id, day, LocalTime(hour, 0)) }
                        chosenEvent?.let { store.moveEvent(it.id, day, LocalTime(hour, 0)) }
                        picked = null
                    }
                }
            }
        }
        grid.drag?.let { d -> Carried(d, grid) }
    }
}

@Composable
private fun Carried(d: Drag, grid: Grid) {
    val origin = grid.page?.takeIf { it.isAttached }?.positionInRoot() ?: return
    val target = grid.target(d)
    val lift = with(LocalDensity.current) { 56.dp.toPx() }
    val shift = with(LocalDensity.current) { 24.dp.toPx() }
    val label = when {
        target == null -> d.item.title
        d.resize -> "${d.item.title}, ${duration(target.minutes)}"
        else -> "${d.item.title}, ${if (grid.days.size > 1) target.day.short() + " " else ""}${clock(LocalTime(target.start / 60, target.start % 60))}"
    }
    Text(
        if (label.length > 40) label.take(39).trimEnd() + "…" else label,
        Modifier
            .offset { IntOffset((d.pointer.x - origin.x - shift).roundToInt(), (d.pointer.y - origin.y - lift).roundToInt()) }
            .clip(blockShape)
            .background(Pixel.Ink, blockShape)
            .padding(horizontal = Spacing.S, vertical = Spacing.XS),
        style = MaterialTheme.typography.bodyMedium,
        color = Pixel.OnInk,
        maxLines = 1,
    )
}

@Composable
internal fun ArrowButton(symbol: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    PixelKey(onClick, Modifier.semantics { contentDescription = label }, key = Pixel.InkSoft, enabled = enabled, fill = true, sound = app.pokeplan.ui.Sfx.CURSOR, horizontal = Spacing.S) { look ->
        Text(symbol, style = MaterialTheme.typography.titleMedium, color = if (enabled) Pixel.Ink else look.ink)
    }
}

@Composable
internal fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        options.forEachIndexed { i, label -> TabButton(label, i == selected, { onSelect(i) }) }
    }
}

@Composable
private fun DayCell(day: LocalDate, today: LocalDate, chosen: Boolean, busy: Boolean, enabled: Boolean, label: String, onClick: () -> Unit, modifier: Modifier) {
    val isToday = day == today
    Column(
        modifier
            .alpha(if (enabled) 1f else 0.35f)
            .clip(PixelShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = (if (isToday) "Today, " else "") + day.short() + if (busy) ", has plans" else ""
                selected = chosen
            }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(dayNames[day.dayOfWeek.ordinal].take(2), style = MaterialTheme.typography.bodyMedium, color = if (isToday) Pixel.Red else Pixel.InkSoft)
        Box(
            Modifier
                .size(Sizes.Icon)
                .background(
                    when { chosen && isToday -> Pixel.Red; chosen -> Pixel.Ink; else -> Color.Transparent },
                    PixelShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${day.day}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = when { chosen -> if (isToday) Color.White else Pixel.OnInk; isToday -> Pixel.Red; else -> Pixel.Ink },
            )
        }
        Box(Modifier.size(5.dp).background(if (busy) Pixel.InkSoft else Color.Transparent, PixelShape))
    }
}

@Composable
private fun DayStrip(today: LocalDate, offset: Int, weekStart: kotlinx.datetime.DayOfWeek, busy: (LocalDate) -> Boolean, open: (Int) -> Unit) {
    val first = today.daysUntil(app.pokeplan.plan.Streaks.weekStartOf(today.plus(DatePeriod(days = offset)), weekStart))
    Row(Modifier.fillMaxWidth()) {
        (first until first + 7).forEach { i ->
            val day = today.plus(DatePeriod(days = i))
            DayCell(day, today, chosen = i == offset, busy = busy(day), enabled = true, "Open the day", { open(i) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun WeekHeader(days: List<LocalDate>, today: LocalDate, open: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = LabelWidth)) {
        days.forEach { day ->
            DayCell(day, today, chosen = day == today, busy = false, enabled = true, "Open the day", { open(day) }, Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllDayRow(plans: List<DayTimeline.Plan>, picked: String?, pick: (String) -> Unit) {
    if (plans.none { it.allDay.isNotEmpty() }) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            "all day", Modifier.width(LabelWidth).padding(top = Spacing.XS, end = Spacing.S),
            style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, textAlign = TextAlign.End, maxLines = 2,
        )
        if (plans.size == 1) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                plans.single().allDay.forEach { e -> AllDayBar(e, e.id == picked, pick, Modifier.widthIn(max = 220.dp)) }
            }
        } else {
            val spans = plans.flatMap { it.allDay }.distinctBy { it.id }.map { DaySpan(it, it.start.date, it.lastDay) }
            val bars = SpanLayout.lay(spans, plans.map { it.day })
            val lanes = (bars.maxOfOrNull { it.lane } ?: -1) + 1
            BoxWithConstraints(Modifier.weight(1f).height(BarHeight * lanes + BarGap * (lanes - 1))) {
                val column = maxWidth / plans.size
                bars.forEach { b ->
                    AllDayBar(
                        b.item, b.item.id == picked, pick,
                        Modifier.offset(x = column * b.from, y = (BarHeight + BarGap) * b.lane).width(column * (b.to - b.from + 1) - 2.dp),
                    )
                }
            }
        }
    }
}

internal val BarHeight = 22.dp

internal val BarGap = 2.dp

@Composable
internal fun AllDayBar(e: CalendarEvent, selected: Boolean, pick: (String) -> Unit, modifier: Modifier = Modifier, small: Boolean = false) {
    val shape = PixelShape
    Text(
        e.title,
        modifier
            .height(if (small) MonthBarHeight else BarHeight)
            .clip(shape)
            .background(eventColor(e), shape)
            .border(if (selected) 2.dp else 0.dp, if (selected) Pixel.Ink else Color.Transparent, shape)
            .then(if (e.isOwn) Modifier.clickable(role = Role.Button, onClickLabel = "Edit ${e.title}") { pick(e.id) } else Modifier)
            .semantics { contentDescription = "All day: ${e.title}" }
            .padding(horizontal = if (small) 3.dp else Spacing.XS, vertical = if (small) 0.dp else 2.dp),
        style = if (small) MonthTextStyle else MaterialTheme.typography.bodyMedium,
        color = Color.White,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickedPanel(model: AppModel, task: Task, days: List<LocalDate>, close: () -> Unit) {
    val store = model.store
    val day = task.planned
    val time = task.plannedTime?.takeIf { day in days }
    val length = DayTimeline.minutes(task)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(task.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (time == null || day == null) "Tap an hour to plan it."
                else "${if (days.size > 1) day.short() + ", " else "At "}${clock(time)} for ${duration(length)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                if (time != null && day != null) {
                    PixelButton("-15", { store.scheduleTask(task.id, day, shifted(time, -15)) }, color = Pixel.InkSoft, compact = true)
                    PixelButton("+15", { store.scheduleTask(task.id, day, shifted(time, 15)) }, color = Pixel.InkSoft, compact = true)
                    PixelButton("Shorter", { store.setEstimate(task.id, length - DayTimeline.SNAP_MINUTES) }, color = Pixel.InkSoft, enabled = length > DayTimeline.SNAP_MINUTES, compact = true)
                    PixelButton("Longer", { store.setEstimate(task.id, length + DayTimeline.SNAP_MINUTES) }, color = Pixel.InkSoft, compact = true)
                    PixelButton("No time", { store.setPlannedTime(task.id, null); close() }, color = Pixel.InkSoft, compact = true)
                }
                PixelIconButton("close", "Cancel", close, Pixel.InkSoft)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tray(tasks: List<Task>, picked: String?, pick: (String) -> Unit, grid: Grid, drop: (Drag) -> Unit, hint: Boolean) {
    if (tasks.isEmpty()) return
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("To plan", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            if (hint) Text("Tap one, then an hour (or drag).", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                tasks.forEach { t -> key(t.id) {
                    val anchor = remember { Anchor() }
                    TrayChip(
                        t, picked == t.id, { pick(t.id) },
                        Modifier
                            .alpha(if (grid.drag?.item?.id == t.id) 0.4f else 1f)
                            .dragToPlan(
                                t, anchor,
                                onStart = { _, _ -> grid.drag = Drag(t.item(), resize = false, grabMinutes = 0, anchor.root(Offset.Zero)) },
                                onMove = { grid.drag?.pointer = it },
                                onEnd = { dropped -> grid.drag?.let { if (dropped) drop(it) }; grid.drag = null },
                            ),
                    )
                } }
            }
        }
    }
}

@Composable
private fun TrayChip(t: Task, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = PixelShape
    val color = taskColor(t)
    Row(
        modifier
            .defaultMinSize(minHeight = Spacing.Target)
            .widthIn(max = 260.dp)
            .clip(shape)
            .background(if (selected) Pixel.Ink else color.copy(alpha = 0.12f), shape)
            .border(Sizes.Hairline, if (selected) Pixel.Ink else color.copy(alpha = 0.45f), shape)
            .clickable(role = Role.Button, onClickLabel = "Pick", onClick = onClick)
            .semantics { this.selected = selected }
            .padding(start = Spacing.S, end = Spacing.M),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
    ) {
        ItemIcon(taskIcon(t), size = Sizes.IconS, description = null)
        Text(
            t.title, style = MaterialTheme.typography.labelMedium, color = if (selected) Pixel.OnInk else Pixel.Ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Hours(
    model: AppModel,
    plans: List<DayTimeline.Plan>,
    firstHour: Int,
    endHour: Int,
    compact: Boolean,
    picked: String?,
    pick: (String) -> Unit,
    own: Map<String, PlanEvent>,
    draft: Target?,
    grid: Grid,
    drop: (Drag) -> Unit,
    onHour: (LocalDate, Int) -> Unit,
) {
    val hours = (firstHour until endHour).toList()
    val line = Pixel.Border
    val picking = picked != null || draft != null
    val origin = firstHour * 60
    val requester = remember { BringIntoViewRequester() }
    val margin = with(LocalDensity.current) { 48.dp.toPx() }
    val dragging = grid.drag != null
    LaunchedEffect(dragging) {
        while (dragging) {
            val d = grid.drag ?: break
            val c = grid.coords
            val p = grid.local(d.pointer)
            if (c != null && p != null) requester.bringIntoView(Rect(0f, p.y - margin, c.size.width.toFloat(), p.y + margin))
            withFrameNanos { }
        }
    }
    val half = Pixel.Border.copy(alpha = 0.6f)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = LabelLift)
            .height(HourHeight * hours.size)
            .drawBehind {
                val left = LabelWidth.toPx()
                val hourPx = HourHeight.toPx()
                val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                hours.indices.forEach { i ->
                    val y = hourPx * i
                    drawLine(line, Offset(left - Spacing.XS.toPx(), y), Offset(size.width, y), 1.dp.toPx())
                    drawLine(half, Offset(left, y + hourPx / 2), Offset(size.width, y + hourPx / 2), 1.dp.toPx(), pathEffect = dash)
                }
                drawLine(line, Offset(left, size.height), Offset(size.width, size.height), 1.dp.toPx())
            },
    ) {
        hours.forEachIndexed { i, h ->
            Text(
                clock(LocalTime(h, 0)),
                Modifier.offset(y = HourHeight * i - LabelLift).width(LabelWidth - Spacing.S),
                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, textAlign = TextAlign.End, maxLines = 1,
            )
        }
        BoxWithConstraints(
            Modifier
                .padding(start = LabelWidth)
                .fillMaxSize()
                .bringIntoViewRequester(requester)
                .onGloballyPositioned { grid.place(it) },
        ) {
            val columnWidth = maxWidth / plans.size
            plans.forEachIndexed { column, plan ->
                val x = columnWidth * column
                Box(Modifier.offset(x = x).width(1.dp).fillMaxHeight().background(line))
                if (picking) {
                    hours.forEachIndexed { i, h ->
                        val label = (if (plans.size > 1) plan.day.short() + " " else "") + clock(LocalTime(h, 0))
                        Box(
                            Modifier
                                .offset(x = x, y = HourHeight * i)
                                .width(columnWidth)
                                .height(HourHeight)
                                .background(Pixel.Tone.copy(alpha = 0.06f))
                                .clickable(role = Role.Button, onClickLabel = "Plan at $label") { onHour(plan.day, h) }
                                .semantics { contentDescription = label },
                        )
                    }
                }
                plan.blocks.forEach { b -> key(b.task.id) {
                    val laneWidth = columnWidth / b.lanes
                    val height = (HourHeight * ((b.endMinute - b.startMinute) / 60f)).coerceAtLeast(28.dp)
                    TimelineBlock(
                        b, picked == b.task.id, compact, laneWidth >= MinTitleWidth, grid.drag?.item?.id == b.task.id, { pick(b.task.id) }, grid, drop,
                        Modifier
                            .offset(x = x + laneWidth * b.lane, y = HourHeight * ((b.startMinute - origin) / 60f))
                            .width(laneWidth)
                            .height(height)
                            .padding(end = 2.dp, bottom = 2.dp),
                    )
                } }
                plan.events.forEach { b -> key(b.event.id) {
                    val laneWidth = columnWidth / b.lanes
                    val height = (HourHeight * ((b.endMinute - b.startMinute) / 60f)).coerceAtLeast(28.dp)
                    val mine = own[b.event.id]?.takeIf { b.event.isOwn }
                    EventBlock(
                        b, compact, laneWidth >= MinTitleWidth, mine, picked == b.event.id, grid.drag?.item?.id == b.event.id, { pick(b.event.id) }, grid, drop,
                        Modifier
                            .offset(x = x + laneWidth * b.lane, y = HourHeight * ((b.startMinute - origin) / 60f))
                            .width(laneWidth)
                            .height(height)
                            .padding(end = 2.dp, bottom = 2.dp),
                    )
                } }
                if (plan.day == model.store.today) {
                    val now = model.store.now.time.let { it.hour * 60 + it.minute }
                    if (now >= origin && now < endHour * 60) {
                        val y = HourHeight * ((now - origin) / 60f)
                        Box(Modifier.offset(x = x, y = y - 1.dp).width(columnWidth).height(2.dp).background(Pixel.Red))
                        Box(Modifier.offset(x = x - 5.dp, y = y - 5.dp).size(10.dp).background(Pixel.Red, PixelShape))
                    }
                }
            }
            (grid.drag?.let { grid.target(it) } ?: draft)?.let { target ->
                val column = grid.days.indexOf(target.day).takeIf { it >= 0 } ?: return@let
                if (target.start < origin || target.start >= endHour * 60) return@let
                Box(
                    Modifier
                        .offset(x = columnWidth * column, y = HourHeight * ((target.start - origin) / 60f))
                        .width(columnWidth)
                        .height((HourHeight * (target.minutes / 60f)).coerceAtLeast(28.dp))
                        .padding(end = 2.dp, bottom = 2.dp)
                        .background(Pixel.Ink.copy(alpha = 0.08f), blockShape)
                        .border(Sizes.Line, Pixel.Ink, blockShape),
                )
            }
        }
    }
}

private val MinTitleWidth = 36.dp

private val ResizeEdge = 12.dp

@Composable
private fun TimelineBlock(
    block: DayTimeline.Block,
    selected: Boolean,
    compact: Boolean,
    wide: Boolean,
    lifted: Boolean,
    onClick: () -> Unit,
    grid: Grid,
    drop: (Drag) -> Unit,
    modifier: Modifier,
) {
    val t = block.task
    val color = taskColor(t)
    val start = LocalTime(block.startMinute / 60, block.startMinute % 60)
    val anchor = remember { Anchor() }
    val edge = with(LocalDensity.current) { ResizeEdge.toPx() }
    Box(
        modifier
            .alpha(if (lifted) 0.4f else 1f)
            .blockLook(color, fill = if (t.isOpen) 0.2f else 0.08f, accent = if (t.isOpen) 1f else 0.4f, selected)
            .semantics(mergeDescendants = true) {
                contentDescription = "${clock(start)} ${t.title}, ${duration(block.endMinute - block.startMinute)}" + if (t.isOpen) "" else ", done"
            }
            .let { if (t.isOpen) it.clickable(role = Role.Button, onClickLabel = "Pick", onClick = onClick) else it }
            .let {
                if (!t.isOpen) it
                else it.dragToPlan(
                    t, anchor,
                    onStart = { local, size ->
                        val resize = local.y >= size.height - edge
                        val reference = if (resize) block.endMinute else block.startMinute
                        val grab = grid.local(anchor.root(local))?.let { p -> grid.minuteAt(p.y) - reference } ?: 0
                        grid.drag = Drag(t.item(), resize, grab, anchor.root(local))
                    },
                    onMove = { grid.drag?.pointer = it },
                    onEnd = { dropped -> grid.drag?.let { if (dropped) drop(it) }; grid.drag = null },
                )
            }
            .padding(start = AccentWidth + if (compact) 2.dp else Spacing.XS, end = if (compact) 1.dp else Spacing.XS, top = 2.dp, bottom = 2.dp),
    ) {
        val minutes = block.endMinute - block.startMinute
        val strike = if (t.isOpen) TextDecoration.None else TextDecoration.LineThrough
        if (compact) {
            Column {
                ItemIcon(taskIcon(t), size = 18.dp, description = null)
                if (minutes >= 45 && wide) BlockTitle(t.title, if (t.isOpen) Pixel.Ink else Pixel.InkSoft, lines = 1, strike, small = true)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                ItemIcon(taskIcon(t), size = Sizes.IconS, description = null)
                Column {
                    BlockTitle(t.title, if (t.isOpen) Pixel.Ink else Pixel.InkSoft, lines = if (minutes >= 60) 2 else 1, strike)
                    if (minutes >= 45) BlockTime(start, block.endMinute)
                }
            }
        }
        if (selected && t.isOpen) {
            Box(Modifier.align(Alignment.BottomCenter).width(16.dp).height(3.dp).background(Pixel.Ink, androidx.compose.ui.graphics.RectangleShape))
        }
    }
}

@Composable
private fun EventBlock(
    block: DayTimeline.EventBlock,
    compact: Boolean,
    wide: Boolean,
    own: PlanEvent?,
    selected: Boolean,
    lifted: Boolean,
    onClick: () -> Unit,
    grid: Grid,
    drop: (Drag) -> Unit,
    modifier: Modifier,
) {
    val e = block.event
    val color = eventColor(e)
    val start = LocalTime(block.startMinute / 60, block.startMinute % 60)
    val end = if (block.endMinute >= 24 * 60) LocalTime(0, 0) else LocalTime(block.endMinute / 60, block.endMinute % 60)
    val anchor = remember { Anchor() }
    val edge = with(LocalDensity.current) { ResizeEdge.toPx() }
    Box(
        modifier
            .alpha(if (lifted) 0.4f else 1f)
            .blockLook(color, fill = if (own != null) 0.18f else 0.12f, accent = if (own != null) 1f else 0.7f, selected)
            .semantics(mergeDescendants = true) {
                contentDescription = "${if (own != null) "Event" else "Calendar"}: ${e.title}, ${clock(start)} to ${clock(end)}" +
                    (own?.repeat?.let { ", repeats ${it.label.lowercase()}" } ?: "")
            }
            .let {
                if (own == null) it
                else it.clickable(role = Role.Button, onClickLabel = "Pick", onClick = onClick).dragToPlan(
                    own, anchor,
                    onStart = { local, size ->
                        val resize = local.y >= size.height - edge
                        val reference = if (resize) block.endMinute else block.startMinute
                        val grab = grid.local(anchor.root(local))?.let { p -> grid.minuteAt(p.y) - reference } ?: 0
                        grid.drag = Drag(own.item(), resize, grab, anchor.root(local))
                    },
                    onMove = { grid.drag?.pointer = it },
                    onEnd = { dropped -> grid.drag?.let { if (dropped) drop(it) }; grid.drag = null },
                )
            }
            .padding(start = AccentWidth + if (compact) 2.dp else Spacing.S, end = if (compact) 1.dp else Spacing.XS, top = 2.dp, bottom = 2.dp),
    ) {
        val minutes = block.endMinute - block.startMinute
        val ink = if (own != null) Pixel.Ink else Pixel.InkSoft
        if (compact) {
            if (minutes >= 30 && wide) BlockTitle(e.title, ink, lines = 1, TextDecoration.None, small = true)
        } else {
            Column {
                BlockTitle(e.title, ink, lines = if (minutes >= 60) 2 else 1, TextDecoration.None)
                if (minutes >= 45) BlockTime(start, block.endMinute)
            }
        }
        if (selected) {
            Box(Modifier.align(Alignment.BottomCenter).width(16.dp).height(3.dp).background(Pixel.Ink, androidx.compose.ui.graphics.RectangleShape))
        }
    }
}

private val AccentWidth = 4.dp

private val LabelLift = 9.dp

private val panelShape = PixelShape

private fun Modifier.blockLook(color: Color, fill: Float, accent: Float, selected: Boolean): Modifier = this
    .clip(blockShape)
    .background(color.copy(alpha = fill), blockShape)
    .drawBehind { drawRect(color.copy(alpha = accent), size = Size(AccentWidth.toPx(), size.height)) }
    .let { if (selected) it.border(Sizes.Line, Pixel.Ink, blockShape) else it }

@Composable
private fun BlockTitle(title: String, color: Color, lines: Int, decoration: TextDecoration, small: Boolean = false) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold, textDecoration = decoration, lineHeight = if (small) 15.sp else 18.sp,
        ),
        color = color,
        maxLines = lines.coerceAtLeast(1),
        softWrap = !small,
        overflow = if (small) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}

@Composable
private fun BlockTime(start: LocalTime, endMinute: Int) {
    val end = if (endMinute >= 24 * 60) LocalTime(0, 0) else LocalTime(endMinute / 60, endMinute % 60)
    Text("${clock(start)} – ${clock(end)}", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
}
