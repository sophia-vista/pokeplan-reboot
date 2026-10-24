package app.pokeplan.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pokeplan.plan.Category
import app.pokeplan.plan.DayLoad
import app.pokeplan.plan.DayTimeline
import app.pokeplan.plan.Task
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

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

/**
 * The day timeline: the day's tasks with a time sit on the hours, the rest wait in a tray. Tap a task (in the tray or
 * on the hours), then tap an hour to plan it there; the 15-minute buttons fine-tune it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DayView(model: AppModel, hidden: Set<String>) {
    val store = model.store
    var offset by remember { mutableStateOf(0) }
    var picked by remember { mutableStateOf<String?>(null) }
    val day = store.today.plus(DatePeriod(days = offset))
    val tasks = model.data.tasks.filter { it.id !in hidden }
    val plan = DayTimeline.build(tasks, day, store.now)
    val chosen = tasks.firstOrNull { it.id == picked && it.isOpen }
    val pick = { t: Task -> picked = if (picked == t.id) null else t.id }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        PixelButton("Earlier", { offset -= 1 }, color = Pixel.InkSoft, enabled = offset > 0, compact = true)
        Text(
            when (offset) { 0 -> "Today"; 1 -> "Tomorrow"; else -> day.short() },
            style = MaterialTheme.typography.titleSmall,
        )
        PixelButton("Later", { offset += 1 }, color = Pixel.InkSoft, enabled = offset < DAYS_AHEAD, compact = true)
    }

    val load = DayLoad.compute(plan.blocks.map { it.task }.filter { it.isOpen } + plan.tray)
    Text(
        "${duration(load.plannedMinutes)} planned, ${duration(load.freeMinutes)} awake." +
            if (load.level == DayLoad.Level.OVER) " That's a lot, move something?" else "",
        style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
    )

    if (chosen != null) PickedPanel(model, chosen, day) { picked = null }

    TrayChips("No time yet", plan.tray, picked, pick)
    TrayChips("From the inbox", plan.inbox, picked, pick)
    if (chosen == null) Text("Tap a task, then tap an hour.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)

    Hours(model, plan, picked, pick) { hour ->
        chosen?.let { store.scheduleTask(it.id, day, LocalTime(hour, 0)) }
        picked = null
    }
}

/** What to do with the picked task: put it on an hour, nudge it by 15 minutes, take its time away, or finish it. */
@Composable
private fun PickedPanel(model: AppModel, task: Task, day: kotlinx.datetime.LocalDate, close: () -> Unit) {
    val store = model.store
    val time = task.plannedTime?.takeIf { task.planned == day }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(task.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (time == null) "Tap an hour to plan it." else "At ${clock(time)}. Tap another hour to move it.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                if (time != null) {
                    PixelButton("-15", { store.scheduleTask(task.id, day, shifted(time, -15)) }, color = Pixel.InkSoft, compact = true)
                    PixelButton("+15", { store.scheduleTask(task.id, day, shifted(time, 15)) }, color = Pixel.InkSoft, compact = true)
                    PixelButton("No time", { store.setPlannedTime(task.id, null); close() }, color = Pixel.InkSoft, compact = true)
                }
                PixelButton("Cancel", close, color = Pixel.InkSoft, compact = true)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrayChips(label: String, tasks: List<Task>, picked: String?, pick: (Task) -> Unit) {
    if (tasks.isEmpty()) return
    SectionLabel(label)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        tasks.forEach { t ->
            val title = if (t.title.length > 24) t.title.take(23).trimEnd() + "…" else t.title
            TabButton(title, picked == t.id, { pick(t) })
        }
    }
}

/** The hours of the day, each one a target while a task is picked, with the timed tasks drawn on top. */
@Composable
private fun Hours(model: AppModel, plan: DayTimeline.Plan, picked: String?, pick: (Task) -> Unit, onHour: (Int) -> Unit) {
    val hours = (plan.firstHour until plan.endHour).toList()
    val line = Pixel.Border
    val picking = picked != null
    Box(Modifier.fillMaxWidth().height(HourHeight * hours.size)) {
        hours.forEachIndexed { i, h ->
            val label = clock(LocalTime(h, 0))
            Box(
                Modifier
                    .offset(y = HourHeight * i)
                    .fillMaxWidth()
                    .height(HourHeight)
                    .drawBehind { drawLine(line, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
                    .let {
                        if (picking) it.background(Pixel.Blue.copy(alpha = 0.06f)).clickable(role = Role.Button, onClickLabel = "Plan at $label") { onHour(h) }
                        else it
                    },
            ) {
                Text(label, Modifier.padding(top = Spacing.XS), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
        }
        BoxWithConstraints(Modifier.padding(start = LabelWidth).fillMaxSize()) {
            val origin = plan.firstHour * 60
            plan.blocks.forEach { b ->
                val laneWidth = maxWidth / b.lanes
                val height = (HourHeight * ((b.endMinute - b.startMinute) / 60f)).coerceAtLeast(28.dp)
                TimelineBlock(
                    b, picked == b.task.id, { pick(b.task) },
                    Modifier
                        .offset(x = laneWidth * b.lane, y = HourHeight * ((b.startMinute - origin) / 60f))
                        .width(laneWidth)
                        .height(height)
                        .padding(end = 2.dp, bottom = 2.dp),
                )
            }
            if (plan.day == model.store.today) {
                val now = model.store.now.time.let { it.hour * 60 + it.minute }
                if (now >= origin && now < plan.endHour * 60) {
                    Box(Modifier.offset(y = HourHeight * ((now - origin) / 60f)).fillMaxWidth().height(2.dp).background(Pixel.Red))
                }
            }
        }
    }
}

@Composable
private fun TimelineBlock(block: DayTimeline.Block, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val t = block.task
    val color = categoryColor(t.category)
    val start = LocalTime(block.startMinute / 60, block.startMinute % 60)
    Row(
        modifier
            .clip(blockShape)
            .background(color.copy(alpha = if (t.isOpen) 0.18f else 0.08f), blockShape)
            .border(if (selected) 2.dp else 1.dp, if (selected) Pixel.Ink else color.copy(alpha = if (t.isOpen) 0.8f else 0.4f), blockShape)
            .let { if (t.isOpen) it.clickable(role = Role.Button, onClickLabel = "Pick", onClick = onClick) else it }
            .padding(horizontal = Spacing.S, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
    ) {
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
