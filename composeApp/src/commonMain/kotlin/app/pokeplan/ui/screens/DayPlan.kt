package app.pokeplan.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import app.pokeplan.plan.Category
import app.pokeplan.plan.DayLoad
import app.pokeplan.plan.DayTimeline
import app.pokeplan.plan.EventRepeat
import app.pokeplan.plan.PlanEvent
import app.pokeplan.plan.isOwn
import app.pokeplan.plan.Task
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
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

private val HourHeight = 64.dp

private val LabelWidth = 48.dp

private val blockShape = RoundedCornerShape(8.dp)

/** How far ahead the day view goes (recurring tasks are generated 14 days ahead). */
private const val DAYS_AHEAD = 13

internal fun categoryColor(c: Category) = when (c) {
    Category.WORK -> Pixel.Blue
    Category.SCHOOL -> Color(0xFF8B5CF6)
    Category.HOME -> Color(0xFFF59E0B)
    Category.HEALTH -> Pixel.Green
    Category.SOCIAL -> Color(0xFFEC4899)
    Category.PERSONAL -> Pixel.Gray
}

/** The real item icon a task shows on the timeline: its own, or its category's. */
private fun taskIcon(t: Task): String = t.icon ?: when (t.category) {
    Category.WORK -> "member-card"
    Category.SCHOOL -> "scroll-of-waters"
    Category.HOME -> "storage-key"
    Category.HEALTH -> "hp-up"
    Category.SOCIAL -> "friend-ball"
    Category.PERSONAL -> "heart-scale"
}

/** "2 h 15 min", "45 min" */
internal fun duration(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

/** A calendar event's own colour, or grey. */
internal fun eventColor(e: CalendarEvent): Color = e.color?.let { Color(it or 0xFF000000L) } ?: if (e.isOwn) OwnEventColor else Pixel.Gray

/** The colour of PokéPlan's own events on the hours. */
internal val OwnEventColor = Color(0xFF0D9488)

/**
 * What a drag carries: a task, or one of PokéPlan's own events ([event]), with when it is now: [day] and [start]
 * minutes after midnight (null for a task without a time), lasting [minutes].
 */
private data class Item(val id: String, val title: String, val event: Boolean, val day: LocalDate?, val start: Int?, val minutes: Int)

private fun Task.item() = Item(id, title, false, planned, plannedTime?.let { it.hour * 60 + it.minute }, DayTimeline.minutes(this))

private fun PlanEvent.item() = Item(id, title, true, day, startMinute, minutes)

/** What is being dragged and where the finger is (root coordinates). [resize] drags the block's lower edge instead. */
private class Drag(val item: Item, val resize: Boolean, val grabMinutes: Int, pointer: Offset) {
    var pointer by mutableStateOf(pointer)
}

/** A new event being set up: its title so far, and when. */
internal data class Draft(
    val title: String, val day: LocalDate, val start: Int, val minutes: Int,
    val allDay: Boolean = false, val lastDay: LocalDate = day, val color: Long? = null, val repeat: EventRepeat? = null,
) {
    /** The same draft starting on [first], an all-day one keeping its length. */
    fun movedTo(first: LocalDate): Draft = copy(day = first, lastDay = first.plus(DatePeriod(days = day.daysUntil(lastDay))))

    /** Adds it to PokéPlan's own calendar. */
    fun addTo(store: app.pokeplan.app.GameStore) {
        if (allDay) store.addAllDayEvent(title, day, lastDay, color, repeat)
        else store.addEvent(title, day, LocalTime(start / 60, start % 60), minutes, color, repeat)
    }
}

/** Where a drop lands: [day] at [start] minutes after midnight, lasting [minutes]. */
private data class Target(val day: LocalDate, val start: Int, val minutes: Int)

/** Where an element is on screen, so a finger position inside it becomes a root position. */
private class Anchor {
    var coords: LayoutCoordinates? = null
    fun root(local: Offset): Offset = coords?.takeIf { it.isAttached }?.localToRoot(local) ?: local
}

/** The hours on screen: turns a finger position into a day column and a minute, and holds the drag in progress. */
private class Grid {
    var coords: LayoutCoordinates? = null
    var page: LayoutCoordinates? = null
    var days: List<LocalDate> = emptyList()
    var firstMinute = 0
    var hourPx = 1f
    var drag by mutableStateOf<Drag?>(null)

    /** Counts the hours moving under a drag (the page scrolling while the finger stays still), so the preview follows. */
    var scrolled by mutableStateOf(0)

    fun place(c: LayoutCoordinates) {
        coords = c
        if (drag != null) scrolled++
    }

    /** [pointer] in the hours' own coordinates. */
    fun local(pointer: Offset): Offset? = coords?.takeIf { it.isAttached }?.let { pointer - it.positionInRoot() }

    fun minuteAt(y: Float): Int = firstMinute + (y / hourPx * 60).roundToInt()

    /** Where [d] would land now, or null while the finger is off the hours. */
    fun target(d: Drag): Target? {
        scrolled // read, so the preview recomposes when the page scrolls
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

/**
 * Lets the element be dragged: after a long press on touch, or as soon as it moves with the mouse. [onStart] gets the
 * finger in the element's own coordinates and the element's size, [onMove] the finger in root coordinates; [onEnd] says
 * whether it was dropped (released after moving) rather than cancelled. Moving consumes the pointer, so a drag never
 * also counts as a tap, and the page does not scroll under it.
 */
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

/**
 * The day timeline, or seven days side by side: the tasks with a time sit on the hours, the rest wait in a tray. Hold a
 * task (in the tray or on the hours) and drag it to an hour, or tap it and then tap an hour; drag a block's lower edge
 * to change how long it takes. Everything snaps to 15 minutes. PokéPlan's own events (New event) are placed, dragged
 * and resized the same way but never pay. Events from the calendars shown in Settings sit on the hours too, read-only.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DayView(model: AppModel, hidden: Set<String>, startWeek: Boolean = false, startMode: Int = if (startWeek) 1 else 0) {
    val store = model.store
    // Day, Week or Month, as in TickTick and Google Calendar.
    var mode by remember { mutableStateOf(startMode) }
    val week = mode == 1
    var offset by remember { mutableStateOf(0) }
    var weekOffset by remember { mutableStateOf(0) }
    var picked by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<Draft?>(null) }
    var monthOffset by remember { mutableStateOf(0) }
    val month = monthGrid(store.today, monthOffset, model.data.settings.weekStartsOnMonday)
    val days = if (week) (0 until 7).map { store.today.plus(DatePeriod(days = weekOffset * 7 + it)) }
    else listOf(store.today.plus(DatePeriod(days = offset)))
    // The days whose phone events are read: the grid's in a month.
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
    // A repeating event is shown as each of its occurrences on the days shown, each with an id of its own.
    val ownShown = own.flatMap { it.occurrences(range.first(), range.last()) }
    val shownEvents = events + ownShown.map { it.toCalendarEvent() }
    val plans: List<DayTimeline.Plan>
    val tray: List<Task>
    val undated: List<Task>
    val firstHour: Int
    val endHour: Int
    if (week) {
        val w = DayTimeline.week(tasks, days.first(), store.now, events = shownEvents)
        plans = w.days; tray = w.tray; undated = w.undated; firstHour = w.firstHour; endHour = w.endHour
    } else {
        val p = DayTimeline.build(tasks, days.first(), store.now, events = shownEvents)
        plans = listOf(p); tray = p.tray; undated = p.undated; firstHour = p.firstHour; endHour = p.endHour
    }
    val chosen = tasks.firstOrNull { it.id == picked && it.isOpen }
    val chosenEvent = ownShown.firstOrNull { it.id == picked }
    val pick = { id: String -> picked = if (picked == id) null else id; draft = null }

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
            openDay = { day -> offset = store.today.daysUntil(day).coerceIn(0, DAYS_AHEAD); mode = 0 },
            canOpen = { day -> store.today.daysUntil(day) in 0..DAYS_AHEAD },
        )
        return
    }

    Box(Modifier.fillMaxWidth().onGloballyPositioned { grid.page = it }) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            // Like Fantastical and Google Calendar: what is shown and the arrows on one line, the view switch and the one
            // action under it, then (for a day) a strip of the week's days to jump between, then straight to the hours.
            val away = if (week) weekOffset > 0 else offset > 0
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                    Text(
                        if (week) "${days.first().dayMonth()} – ${days.last().dayMonth()}"
                        else when (offset) { 0 -> "Today"; 1 -> "Tomorrow"; else -> dayNames[days.first().dayOfWeek.ordinal] },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        if (week) (if (weekOffset == 0) "Next 7 days" else "The week after") else days.first().short(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                ArrowButton("‹", if (week) "Earlier week" else "Day before", enabled = away) { if (week) weekOffset = 0 else offset -= 1 }
                ArrowButton("›", if (week) "Later week" else "Next day", enabled = if (week) weekOffset < 1 else offset < DAYS_AHEAD) {
                    if (week) weekOffset = 1 else offset += 1
                }
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
                DayStrip(store.today, offset, busy = { day -> tasks.any { it.isOpen && it.planned == day } || own.any { it.isOn(day) } }) { offset = it }
                val plan = plans.single()
                val load = DayLoad.compute(plan.blocks.map { it.task }.filter { it.isOpen } + plan.tray, busyMinutes = plan.busyMinutes)
                Text(
                    "${duration(load.plannedMinutes)} planned · ${duration(load.freeMinutes)} awake" +
                        if (load.level == DayLoad.Level.OVER) ". That's a lot, move something?" else "",
                    style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
                )
            }

            draft?.let { d -> NewEventPanel(d, days.size > 1, { draft = it }, { d.addTo(store); draft = null }) { draft = null } }
            if (chosen != null) PickedPanel(model, chosen, days) { picked = null }
            if (chosenEvent != null) EventPanel(model, chosenEvent, days.size > 1, { picked = it }) { picked = null }

            Tray(tray, undated, picked, hint = chosen == null && chosenEvent == null && draft == null, pick, grid, ::drop)

            // The calendar itself, on one panel: the days (in a week), the all-day row, then the hours.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Pixel.Panel, panelShape)
                    .border(1.dp, Pixel.Border, panelShape)
                    .padding(start = Spacing.XS, end = Spacing.S, top = Spacing.S, bottom = Spacing.M),
                verticalArrangement = Arrangement.spacedBy(Spacing.S),
            ) {
                if (week) WeekHeader(days, store.today) { day -> offset = store.today.daysUntil(day); mode = 0 }
                AllDayRow(plans, picked, pick)
                val drafted = draft?.let { Target(it.day, it.start, it.minutes) }
                Hours(model, plans, firstHour, endHour, compact = week, picked, pick, ownShown.associateBy { it.id }, drafted, grid, ::drop) { day, hour ->
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

/** The task under the finger while it is dragged, just above it, with where it would land. */
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

/** A round ‹ or › button that steps a day or a week. */
@Composable
internal fun ArrowButton(symbol: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(Spacing.Target)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleMedium, color = if (enabled) Pixel.Ink else Pixel.Border)
    }
}

/** A segmented control: one rounded track, the chosen segment raised on it (iOS and Material 3 style). */
@Composable
internal fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val track = RoundedCornerShape(12.dp)
    val thumb = RoundedCornerShape(10.dp)
    Row(Modifier.height(44.dp).background(Pixel.Shade, track).padding(3.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .fillMaxHeight()
                    .clip(thumb)
                    .background(if (on) Pixel.Panel else Color.Transparent, thumb)
                    .border(1.dp, if (on) Pixel.Border else Color.Transparent, thumb)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .semantics { this.selected = on }
                    .padding(horizontal = Spacing.L),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
                    color = if (on) Pixel.Ink else Pixel.InkSoft,
                )
            }
        }
    }
}

/** One day in a strip of days: its short name over its date, the date in a filled circle when chosen, red when today. */
@Composable
private fun DayCell(day: LocalDate, today: LocalDate, chosen: Boolean, busy: Boolean, enabled: Boolean, label: String, onClick: () -> Unit, modifier: Modifier) {
    val isToday = day == today
    Column(
        modifier
            .alpha(if (enabled) 1f else 0.35f)
            .clip(RoundedCornerShape(12.dp))
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
                .size(32.dp)
                .background(
                    when { chosen && isToday -> Pixel.Red; chosen -> Pixel.Ink; else -> Color.Transparent },
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${day.day}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = when { chosen -> if (isToday) Color.White else Pixel.OnInk; isToday -> Pixel.Red; else -> Pixel.Ink },
            )
        }
        Box(Modifier.size(5.dp).background(if (busy) Pixel.InkSoft else Color.Transparent, CircleShape))
    }
}

/**
 * The seven days around the one shown, like Fantastical's day ticker and Apple Calendar's week row: tap a day to open it;
 * a dot marks a day that has something planned. Days past how far the planner looks ahead are greyed out.
 */
@Composable
private fun DayStrip(today: LocalDate, offset: Int, busy: (LocalDate) -> Boolean, open: (Int) -> Unit) {
    val first = offset / 7 * 7
    Row(Modifier.fillMaxWidth()) {
        (first until first + 7).forEach { i ->
            val day = today.plus(DatePeriod(days = i))
            DayCell(day, today, chosen = i == offset, busy = busy(day), enabled = i <= DAYS_AHEAD, "Open the day", { open(i) }, Modifier.weight(1f))
        }
    }
}

/** The week's days over their columns, lined up with the hours; tapping one opens that day. */
@Composable
private fun WeekHeader(days: List<LocalDate>, today: LocalDate, open: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = LabelWidth)) {
        days.forEach { day ->
            DayCell(day, today, chosen = day == today, busy = false, enabled = true, "Open the day", { open(day) }, Modifier.weight(1f))
        }
    }
}

/**
 * All-day calendar events in a band above the hours, as in Google Calendar: in a week an event over several days is one
 * bar across their columns (stacked in lanes when they overlap), in a day they wrap beside the "all day" label. Tapping
 * one of PokéPlan's own events picks it, to edit it.
 */
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

/**
 * An all-day event as a solid bar in its colour with its title in white, as Google Calendar and TickTick draw them; a
 * PokéPlan event can be tapped to edit it.
 */
@Composable
internal fun AllDayBar(e: CalendarEvent, selected: Boolean, pick: (String) -> Unit, modifier: Modifier = Modifier, small: Boolean = false) {
    val shape = RoundedCornerShape(6.dp)
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

/** What to do with the picked task: put it on an hour, nudge it or its length by 15 minutes, or take its time away. */
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
                else "${if (days.size > 1) day.short() + ", " else "At "}${clock(time)} for ${duration(length)}. Tap another hour to move it.",
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
                PixelButton("Cancel", close, color = Pixel.InkSoft, compact = true)
            }
        }
    }
}

/**
 * The tasks still waiting for an hour, on one panel like Structured's inbox and Sunsama's backlog: those planned for the
 * day without a time, then those with no date at all, each a chip with its category's colour. [hint] says how to plan one.
 */
@Composable
private fun Tray(tray: List<Task>, undated: List<Task>, picked: String?, hint: Boolean, pick: (String) -> Unit, grid: Grid, drop: (Drag) -> Unit) {
    if (tray.isEmpty() && undated.isEmpty()) return
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text("To plan", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                Text("${tray.size + undated.size}", style = MaterialTheme.typography.bodyMedium)
            }
            TrayGroup(if (undated.isEmpty()) null else "No time yet", tray, picked, pick, grid, drop)
            TrayGroup("No date", undated, picked, pick, grid, drop)
            if (hint) Text("Hold one and drag it to an hour, or tap it, then an hour.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TrayGroup(label: String?, tasks: List<Task>, picked: String?, pick: (String) -> Unit, grid: Grid, drop: (Drag) -> Unit) {
    if (tasks.isEmpty()) return
    if (label != null) Text(label, style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft)
    // One row that scrolls sideways, so a long tray never pushes the hours off the screen.
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
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

/** A task waiting for an hour: its real item icon and title on a soft chip, filled dark while picked. */
@Composable
private fun TrayChip(t: Task, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val color = categoryColor(t.category)
    Row(
        modifier
            .defaultMinSize(minHeight = 44.dp)
            .widthIn(max = 260.dp)
            .clip(shape)
            .background(if (selected) Pixel.Ink else color.copy(alpha = 0.12f), shape)
            .border(1.dp, if (selected) Pixel.Ink else color.copy(alpha = 0.45f), shape)
            .clickable(role = Role.Button, onClickLabel = "Pick", onClick = onClick)
            .semantics { this.selected = selected }
            .padding(start = Spacing.S, end = Spacing.M),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
    ) {
        ItemIcon(taskIcon(t), size = 24.dp, description = null)
        Text(
            t.title, style = MaterialTheme.typography.labelMedium, color = if (selected) Pixel.OnInk else Pixel.Ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The hours, one column per day in [plans], with the timed tasks and events drawn on top. While a task is picked each
 * hour of each day is a target; while one is dragged, where it would land is outlined and the page scrolls to follow.
 */
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
    // Hour labels sit on their line in a narrow gutter, the lines run only across the days, and a faint dashed line marks
    // each half hour (Google Calendar, Apple Calendar).
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
                                .background(Pixel.Blue.copy(alpha = 0.06f))
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
                        Box(Modifier.offset(x = x - 5.dp, y = y - 5.dp).size(10.dp).background(Pixel.Red, CircleShape))
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
                        .border(2.dp, Pixel.Ink, blockShape),
                )
            }
        }
    }
}

/** How wide a block in a week must be to show its title; a narrower one shows only its icon. */
private val MinTitleWidth = 36.dp

/** The lower edge of a block that resizes it when dragged. */
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
    val color = categoryColor(t.category)
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
                ItemIcon(taskIcon(t), size = 24.dp, description = null)
                Column {
                    BlockTitle(t.title, if (t.isOpen) Pixel.Ink else Pixel.InkSoft, lines = if (minutes >= 60) 2 else 1, strike)
                    if (minutes >= 45) BlockTime(start, block.endMinute)
                }
            }
        }
        if (selected && t.isOpen) {
            Box(Modifier.align(Alignment.BottomCenter).width(16.dp).height(3.dp).background(Pixel.Ink, RoundedCornerShape(2.dp)))
        }
    }
}

/**
 * An event on the hours, with no icon (it is not a task). One from the phone is read-only, in its calendar's colour;
 * one of PokéPlan's own ([own]) can be picked, dragged and resized like a task.
 */
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
            Box(Modifier.align(Alignment.BottomCenter).width(16.dp).height(3.dp).background(Pixel.Ink, RoundedCornerShape(2.dp)))
        }
    }
}

/** The solid strip on a block's left edge, in its colour (Google Calendar, Notion Calendar). */
private val AccentWidth = 4.dp

/** How far an hour label reaches above its line (half its height), so it sits centred on the line. */
private val LabelLift = 9.dp

private val panelShape = RoundedCornerShape(Spacing.CardRadius)

/** A block's look: a soft tint of [color] with a solid accent strip on the left; a dark outline while [selected]. */
private fun Modifier.blockLook(color: Color, fill: Float, accent: Float, selected: Boolean): Modifier = this
    .clip(blockShape)
    .background(color.copy(alpha = fill), blockShape)
    .drawBehind { drawRect(color.copy(alpha = accent), size = Size(AccentWidth.toPx(), size.height)) }
    .let { if (selected) it.border(2.dp, Pixel.Ink, blockShape) else it }

/** A block's title: bold enough to read at a glance; [small] for the narrow columns of a week. */
@Composable
private fun BlockTitle(title: String, color: Color, lines: Int, decoration: TextDecoration, small: Boolean = false) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold, textDecoration = decoration, lineHeight = if (small) 15.sp else 18.sp,
        ),
        color = color,
        maxLines = lines.coerceAtLeast(1),
        // In a week's narrow columns one line is cut off rather than broken letter by letter (Google Calendar's way).
        softWrap = !small,
        overflow = if (small) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}

/** "9:00 – 10:30" under a block's title. */
@Composable
private fun BlockTime(start: LocalTime, endMinute: Int) {
    val end = if (endMinute >= 24 * 60) LocalTime(0, 0) else LocalTime(endMinute / 60, endMinute % 60)
    Text("${clock(start)} – ${clock(end)}", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
}
