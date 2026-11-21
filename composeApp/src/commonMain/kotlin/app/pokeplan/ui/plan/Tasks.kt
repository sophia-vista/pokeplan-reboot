package app.pokeplan.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pokeplan.ui.theme.DialogueShape
import app.pokeplan.ui.Glyph
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.pokeplan.plan.ListKind
import app.pokeplan.plan.Task
import app.pokeplan.plan.TaskFilter
import app.pokeplan.plan.TaskList
import app.pokeplan.plan.TaskState
import app.pokeplan.plan.TaskViews
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.screens.UpTo
import app.pokeplan.ui.screens.TabButton
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.screens.short
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** The views of the one Tasks workspace (PLAN.md section 3): Today is the default, All is every open task grouped by list. */
enum class TasksView(val label: String) { TODAY("Today"), UPCOMING("Upcoming"), ALL("All"), DONE("Done") }

/** Rows shown at most in Done; a search narrows the rest down. */
private const val SHOWN = 60

/**
 * Plan's Tasks workspace: quick add, the four views as pills (Today, Upcoming, All, Done) and the tasks. The Filter on All and Done narrows to one list;
 * lists are made and edited in Plan settings. One engine: a Checklist or Tracker list only
 * changes how its group is titled and that its finished items stay on view. [onFocus] is null where there is no Focus (desktop and the web).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TasksWorkspace(model: AppModel, onFocus: ((Task) -> Unit)?) {
    val store = model.store
    val live = planTasks(model)
    val host = remember { CardHost() }
    // An open task card closes before the page does.
    host.expanded?.let { UpTo { host.expanded = null } }
    val view = model.tasksView
    var filter by remember { mutableStateOf(TaskFilter()) }
    val lists = store.activeLists
    // The list the Filter chose on All or Done (a Checklist or Tracker list then keeps its finished items on view).
    val list = filter.listId?.let { id -> lists.firstOrNull { it.id == id } }
    // A list that was archived or deleted is no longer the one chosen.
    LaunchedEffect(list, filter.listId) { if (filter.listId != null && filter.listId != TaskFilter.INBOX && list == null) filter = filter.copy(listId = null) }

    QuickAddBox(model, if (view == TasksView.TODAY) AddTo.TODAY else AddTo.NO_DATE, listId = if (view == TasksView.ALL) list?.id else null)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        TasksView.entries.forEach { v -> TabButton(v.label, v == view, { model.tasksView = v }) }
    }
    val card = taskCards(model, host)
    val inList = taskCards(model, host, showList = false)
    when (view) {
        TasksView.TODAY -> TodayList(model, onFocus, listId = null)
        TasksView.ALL -> {
            TaskFilterBox(model, filter, { filter = it }, withStatus = false)
            val narrowed = filter
            if (list != null && list.kind != ListKind.TASKS) {
                // A checklist or tracker keeps its finished items on view (a checklist ticks off, then resets).
                val items = live.filter { it.listId == list.id && narrowed.copy(status = TaskFilter.Status.ANY).matches(it) }
                    .sortedWith(compareBy<Task>({ !it.isOpen }, { it.createdAtMillis }))
                if (items.isEmpty()) EmptyState("Nothing in ${list.name}", icon = list.icon)
                else TaskGroup(groupTitle(model, list), items, card = inList)
            } else {
                val groups = TaskViews.outstanding(live, lists.map { it.id }, narrowed)
                if (groups.isEmpty()) EmptyState(
                    if (filter.isNarrowed || list != null) "Nothing matches" else "No open tasks",
                    icon = "journal",
                    action = if (filter.isNarrowed) "Clear filters" to { filter = TaskFilter() } else "Add a task" to { model.addTaskRequested = true },
                ) else groups.forEach { g ->
                    val l = g.listId?.let { id -> lists.firstOrNull { it.id == id } }
                    // Tasks in no list sit in one plain group; with no lists at all there is nothing to group by.
                    val title = if (l != null) groupTitle(model, l) else if (lists.isEmpty() || list != null) null else "No list"
                    TaskGroup(title, g.tasks, card = inList)
                }
            }
        }
        TasksView.UPCOMING -> {
            // By the day each task is on (Todoist's Upcoming), with no cap on how far ahead.
            val shown = TaskViews.upcoming(live, store.now)
            shown.groupBy { it.planned ?: it.due!!.date }.forEach { (day, ts) -> TaskGroup(dayName(model, day), ts, dayOf = day, card = card) }
            if (shown.isEmpty()) EmptyState("Nothing coming up", icon = "timer-ball", action = "Add a task" to { model.addTaskRequested = true })
        }
        TasksView.DONE -> {
            TaskFilterBox(model, filter, { filter = it }, withStatus = false)
            val found = filter.copy(status = TaskFilter.Status.FINISHED).apply(live)
            if (found.size > SHOWN) Text("Showing the first $SHOWN of ${found.size}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            if (found.isEmpty()) EmptyState(if (filter.isNarrowed || list != null) "Nothing matches" else "Nothing finished yet", icon = "scope-lens")
            else TaskGroup(null, found.take(SHOWN), card = card)
        }
    }
}

/** A list's group title: its name, and for a checklist or tracker how many of its items are done. */
private fun groupTitle(model: AppModel, l: TaskList): String {
    if (l.kind == ListKind.TASKS) return l.name
    val items = model.data.tasks.filter { it.listId == l.id && it.state != TaskState.DELETED }
    return "${l.name}, ${items.count { it.state == TaskState.DONE }} of ${items.size} done"
}

/** "Today", "Tomorrow", "Yesterday" or "Wed 28 Oct". */
private fun dayName(model: AppModel, day: LocalDate): String {
    val today = model.store.today
    return when (day) {
        today -> "Today"
        today.plus(DatePeriod(days = 1)) -> "Tomorrow"
        today.minus(DatePeriod(days = 1)) -> "Yesterday"
        else -> day.short()
    }
}
