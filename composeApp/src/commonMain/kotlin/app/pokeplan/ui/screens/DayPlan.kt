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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import app.pokeplan.plan.Category
import app.pokeplan.plan.DayLoad
import app.pokeplan.plan.DayTimeline
import app.pokeplan.plan.PlanEvent
import app.pokeplan.plan.isOwn
import app.pokeplan.plan.Task
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.SectionLabel
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
private fun duration(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

/** A calendar event's own colour, or grey. */
private fun eventColor(e: CalendarEvent): Color = e.color?.let { Color(it or 0xFF000000L) } ?: Pixel.Gray

/** The colour of PokéPlan's own events on the hours. */
private val OwnEventColor = Color(0xFF0D9488)

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
private data class Draft(val title: String, val day: LocalDate, val start: Int, val minutes: Int)

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
internal fun DayView(model: AppModel, hidden: Set<String>, startWeek: Boolean = false) {
    val store = model.store
    var week by remember { mutableStateOf(startWeek) }
    var offset by remember { mutableStateOf(0) }
    var weekOffset by remember { mutableStateOf(0) }
    var picked by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<Draft?>(null) }
    val days = if (week) (0 until 7).map { store.today.plus(DatePeriod(days = weekOffset * 7 + it)) }
    else listOf(store.today.plus(DatePeriod(days = offset)))
    val tasks = model.data.tasks.filter { it.id !in hidden }
    val source = model.calendars
    val shown = model.data.shownCalendars
    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    LaunchedEffect(source, shown, days.first(), days.size) {
        events = if (source == null || shown.isEmpty()) emptyList()
        else withContext(Dispatchers.Default) { source.events(shown, days.first(), days.last()) }
    }
    val own = model.data.events
    val shownEvents = events + own.map { it.toCalendarEvent() }
    val plans: List<DayTimeline.Plan>
    val tray: List<Task>
    val inbox: List<Task>
    val firstHour: Int
    val endHour: Int
    if (week) {
        val w = DayTimeline.week(tasks, days.first(), store.now, events = shownEvents)
        plans = w.days; tray = w.tray; inbox = w.inbox; firstHour = w.firstHour; endHour = w.endHour
    } else {
        val p = DayTimeline.build(tasks, days.first(), store.now, events = shownEvents)
        plans = listOf(p); tray = p.tray; inbox = p.inbox; firstHour = p.firstHour; endHour = p.endHour
    }
    val chosen = tasks.firstOrNull { it.id == picked && it.isOpen }
    val chosenEvent = own.firstOrNull { it.id == picked }
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
            d.item.event -> store.updateEvent(d.item.id, day = target.day, start = at)
            d.resize -> store.setEstimate(d.item.id, target.minutes)
            else -> store.scheduleTask(d.item.id, target.day, at)
        }
        picked = null
    }

    Box(Modifier.fillMaxWidth().onGloballyPositioned { grid.page = it }) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                TabButton("Day", !week, { week = false })
                TabButton("Week", week, { week = true })
                Spacer(Modifier.weight(1f))
                PixelButton("New event", {
                    val day = days.first()
                    val start = if (day == store.today) ((store.now.hour + 1) * 60).coerceIn(firstHour * 60, DayTimeline.LAST_START) else 9 * 60
                    draft = Draft("", day, start, PlanEvent.DEFAULT_MINUTES)
                    picked = null
                }, color = OwnEventColor, compact = true)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (week) {
                    PixelButton("Earlier", { weekOffset = 0 }, color = Pixel.InkSoft, enabled = weekOffset > 0, compact = true)
                    Text(if (weekOffset == 0) "Next 7 days" else "The week after", style = MaterialTheme.typography.titleSmall)
                    PixelButton("Later", { weekOffset = 1 }, color = Pixel.InkSoft, enabled = weekOffset < 1, compact = true)
                } else {
                    PixelButton("Earlier", { offset -= 1 }, color = Pixel.InkSoft, enabled = offset > 0, compact = true)
                    Text(
                        when (offset) { 0 -> "Today"; 1 -> "Tomorrow"; else -> days.first().short() },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    PixelButton("Later", { offset += 1 }, color = Pixel.InkSoft, enabled = offset < DAYS_AHEAD, compact = true)
                }
            }

            if (!week) {
                val plan = plans.single()
                val load = DayLoad.compute(plan.blocks.map { it.task }.filter { it.isOpen } + plan.tray, busyMinutes = plan.busyMinutes)
                Text(
                    "${duration(load.plannedMinutes)} planned, ${duration(load.freeMinutes)} awake." +
                        if (load.level == DayLoad.Level.OVER) " That's a lot, move something?" else "",
                    style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
                )
            }

            val allDay = plans.flatMap { p -> p.allDay.map { p.day to it } }
            if (allDay.isNotEmpty()) {
                SectionLabel("All day")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    allDay.forEach { (day, e) -> EventChip(e, if (week) dayNames[day.dayOfWeek.ordinal] else null) }
                }
            }

            draft?.let { d -> NewEventPanel(d, days.size > 1, { draft = it }, { store.addEvent(d.title, d.day, LocalTime(d.start / 60, d.start % 60), d.minutes); draft = null }) { draft = null } }
            if (chosen != null) PickedPanel(model, chosen, days) { picked = null }
            if (chosenEvent != null) EventPanel(model, chosenEvent, days.size > 1) { picked = null }

            TrayChips("No time yet", tray, picked, pick, grid, ::drop)
            TrayChips("From the inbox", inbox, picked, pick, grid, ::drop)
            if (chosen == null && chosenEvent == null && draft == null) {
                Text(
                    "Hold a task and drag it to an hour, or tap it, then an hour. Drag a block's lower edge to change how long it takes.",
                    style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
                )
            }

            if (week) WeekHeader(days, store.today) { day -> offset = store.today.daysUntil(day); week = false }
            val drafted = draft?.let { Target(it.day, it.start, it.minutes) }
            Hours(model, plans, firstHour, endHour, compact = week, picked, pick, own.associateBy { it.id }, drafted, grid, ::drop) { day, hour ->
                val d = draft
                if (d != null) draft = d.copy(day = day, start = hour * 60, minutes = PlanEvent.fit(hour * 60, d.minutes))
                else {
                    chosen?.let { store.scheduleTask(it.id, day, LocalTime(hour, 0)) }
                    chosenEvent?.let { store.updateEvent(it.id, day = day, start = LocalTime(hour, 0)) }
                    picked = null
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

/** The week's day names over their columns; tapping one opens that day. */
@Composable
private fun WeekHeader(days: List<LocalDate>, today: LocalDate, open: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = LabelWidth)) {
        days.forEach { day ->
            val isToday = day == today
            Column(
                Modifier
                    .weight(1f)
                    .clip(blockShape)
                    .background(if (isToday) Pixel.Red.copy(alpha = 0.12f) else Color.Transparent, blockShape)
                    .clickable(role = Role.Button, onClickLabel = "Open the day") { open(day) }
                    .semantics(mergeDescendants = true) { contentDescription = day.short() }
                    .padding(vertical = Spacing.XS),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(dayNames[day.dayOfWeek.ordinal].take(2), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                Text("${day.day}", style = MaterialTheme.typography.titleSmall, color = if (isToday) Pixel.Red else Pixel.Ink)
            }
        }
    }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrayChips(label: String, tasks: List<Task>, picked: String?, pick: (String) -> Unit, grid: Grid, drop: (Drag) -> Unit) {
    if (tasks.isEmpty()) return
    SectionLabel(label)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        tasks.forEach { t -> key(t.id) {
            val title = if (t.title.length > 24) t.title.take(23).trimEnd() + "…" else t.title
            val anchor = remember { Anchor() }
            TabButton(
                title, picked == t.id, { pick(t.id) },
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
    Box(Modifier.fillMaxWidth().height(HourHeight * hours.size)) {
        hours.forEachIndexed { i, h ->
            Box(
                Modifier
                    .offset(y = HourHeight * i)
                    .fillMaxWidth()
                    .height(HourHeight)
                    .drawBehind { drawLine(line, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) },
            ) {
                Text(clock(LocalTime(h, 0)), Modifier.padding(top = Spacing.XS), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
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
                if (column > 0) Box(Modifier.offset(x = x).width(1.dp).fillMaxHeight().background(line))
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
                        b, picked == b.task.id, compact, grid.drag?.item?.id == b.task.id, { pick(b.task.id) }, grid, drop,
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
                        b, compact, mine, picked == b.event.id, grid.drag?.item?.id == b.event.id, { pick(b.event.id) }, grid, drop,
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
                        Box(Modifier.offset(x = x, y = HourHeight * ((now - origin) / 60f)).width(columnWidth).height(2.dp).background(Pixel.Red))
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

/** The lower edge of a block that resizes it when dragged. */
private val ResizeEdge = 12.dp

@Composable
private fun TimelineBlock(
    block: DayTimeline.Block,
    selected: Boolean,
    compact: Boolean,
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
            .clip(blockShape)
            .background(color.copy(alpha = if (t.isOpen) 0.18f else 0.08f), blockShape)
            .border(if (selected) 2.dp else 1.dp, if (selected) Pixel.Ink else color.copy(alpha = if (t.isOpen) 0.8f else 0.4f), blockShape)
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
            .padding(horizontal = if (compact) 0.dp else Spacing.S, vertical = 2.dp),
    ) {
        if (compact) {
            ItemIcon(taskIcon(t), Modifier.align(Alignment.TopCenter), size = 20.dp, description = null)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                ItemIcon(taskIcon(t), size = 24.dp, description = null)
                Text(
                    "${clock(start)} ${t.title}" + if (t.isOpen) "" else ", done",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (t.isOpen) Pixel.Ink else Pixel.InkSoft,
                    maxLines = if (block.endMinute - block.startMinute >= 45) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
    own: PlanEvent?,
    selected: Boolean,
    lifted: Boolean,
    onClick: () -> Unit,
    grid: Grid,
    drop: (Drag) -> Unit,
    modifier: Modifier,
) {
    val e = block.event
    val color = if (own != null) OwnEventColor else eventColor(e)
    val start = LocalTime(block.startMinute / 60, block.startMinute % 60)
    val end = if (block.endMinute >= 24 * 60) LocalTime(0, 0) else LocalTime(block.endMinute / 60, block.endMinute % 60)
    val anchor = remember { Anchor() }
    val edge = with(LocalDensity.current) { ResizeEdge.toPx() }
    Box(
        modifier
            .alpha(if (lifted) 0.4f else 1f)
            .clip(blockShape)
            .background(color.copy(alpha = if (own != null) 0.16f else 0.10f), blockShape)
            .border(if (selected) 2.dp else 1.dp, if (selected) Pixel.Ink else color.copy(alpha = 0.6f), blockShape)
            .semantics(mergeDescendants = true) {
                contentDescription = "${if (own != null) "Event" else "Calendar"}: ${e.title}, ${clock(start)} to ${clock(end)}"
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
            .padding(horizontal = if (compact) 0.dp else Spacing.S, vertical = 2.dp),
    ) {
        if (!compact) Text(
            "${clock(start)} ${e.title}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (own != null) Pixel.Ink else Pixel.InkSoft,
            maxLines = if (block.endMinute - block.startMinute >= 45) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            Box(Modifier.align(Alignment.BottomCenter).width(16.dp).height(3.dp).background(Pixel.Ink, RoundedCornerShape(2.dp)))
        }
    }
}

/** Setting up a new event: its title, then when (tap an hour, or nudge it), then Add. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewEventPanel(draft: Draft, week: Boolean, change: (Draft) -> Unit, add: () -> Unit, cancel: () -> Unit) {
    val at = LocalTime(draft.start / 60, draft.start % 60)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("New event", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                draft.title, { change(draft.copy(title = it)) }, Modifier.fillMaxWidth(), singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge, label = { Text("What") },
            )
            Text(
                "${draft.day.short()}, ${clock(at)} for ${duration(draft.minutes)}. Tap ${if (week) "a day's hour" else "an hour"} to change it. Events never pay.",
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelButton("-15", { change(draft.copy(start = shifted(at, -15).let { it.hour * 60 + it.minute }).let { it.copy(minutes = PlanEvent.fit(it.start, it.minutes)) }) }, color = Pixel.InkSoft, compact = true)
                PixelButton("+15", { change(draft.copy(start = shifted(at, 15).let { it.hour * 60 + it.minute }).let { it.copy(minutes = PlanEvent.fit(it.start, it.minutes)) }) }, color = Pixel.InkSoft, compact = true)
                PixelButton("Shorter", { change(draft.copy(minutes = PlanEvent.fit(draft.start, draft.minutes - DayTimeline.SNAP_MINUTES))) }, color = Pixel.InkSoft, enabled = draft.minutes > DayTimeline.SNAP_MINUTES, compact = true)
                PixelButton("Longer", { change(draft.copy(minutes = PlanEvent.fit(draft.start, draft.minutes + DayTimeline.SNAP_MINUTES))) }, color = Pixel.InkSoft, compact = true)
                PixelButton("Add", add, color = Pixel.Green, enabled = draft.title.isNotBlank(), compact = true)
                PixelButton("Cancel", cancel, color = Pixel.InkSoft, compact = true)
            }
        }
    }
}

/** What to do with a picked event of PokéPlan's own: rename it, nudge it or its length, or delete it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventPanel(model: AppModel, event: PlanEvent, week: Boolean, close: () -> Unit) {
    val store = model.store
    var title by remember(event.id) { mutableStateOf(event.title) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            OutlinedTextField(
                title, { title = it }, Modifier.fillMaxWidth(), singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge, label = { Text("Event") },
            )
            Text(
                "${if (week) event.day.short() + ", " else "At "}${clock(event.start)} for ${duration(event.minutes)}. Tap another hour to move it.",
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelButton("-15", { store.updateEvent(event.id, start = shifted(event.start, -15)) }, color = Pixel.InkSoft, compact = true)
                PixelButton("+15", { store.updateEvent(event.id, start = shifted(event.start, 15)) }, color = Pixel.InkSoft, compact = true)
                PixelButton("Shorter", { store.updateEvent(event.id, minutes = event.minutes - DayTimeline.SNAP_MINUTES) }, color = Pixel.InkSoft, enabled = event.minutes > DayTimeline.SNAP_MINUTES, compact = true)
                PixelButton("Longer", { store.updateEvent(event.id, minutes = event.minutes + DayTimeline.SNAP_MINUTES) }, color = Pixel.InkSoft, compact = true)
                PixelButton("Delete", { store.deleteEvent(event.id); close() }, color = Pixel.Red, compact = true)
                PixelButton("Done", { store.updateEvent(event.id, title = title); close() }, color = Pixel.Green, compact = true)
            }
        }
    }
}

/** An all-day calendar event, above the hours. */
@Composable
private fun EventChip(e: CalendarEvent, day: String? = null) {
    val color = eventColor(e)
    val title = (if (day != null) "$day: " else "") + if (e.title.length > 24) e.title.take(23).trimEnd() + "…" else e.title
    Text(
        title,
        Modifier
            .clip(blockShape)
            .background(color.copy(alpha = 0.10f), blockShape)
            .border(1.dp, color.copy(alpha = 0.6f), blockShape)
            .semantics { contentDescription = "Calendar, all day: ${e.title}" }
            .padding(horizontal = Spacing.S, vertical = Spacing.XS),
        style = MaterialTheme.typography.bodyMedium,
        color = Pixel.InkSoft,
        maxLines = 1,
    )
}
