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
import app.pokeplan.ui.screens.PullRevealSlot
import app.pokeplan.ui.screens.PullRevealState
import app.pokeplan.ui.screens.TabButton
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.screens.short
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

enum class TasksView(val label: String) { TODAY("Today"), UPCOMING("Upcoming Tasks"), ALL("All"), DONE("Done") }

private const val SHOWN = 60

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TasksWorkspace(model: AppModel, onFocus: ((Task) -> Unit)?, filter: TaskFilter, onFilter: (TaskFilter) -> Unit, reveal: PullRevealState) {
    val store = model.store
    val live = planTasks(model)
    val host = remember { CardHost() }
    // An open task card closes before the page does.
    host.expanded?.let { UpTo { host.expanded = null } }
    val view = model.tasksView
    val lists = store.activeLists
    val list = filter.listId?.let { id -> lists.firstOrNull { it.id == id } }
    LaunchedEffect(list, filter.listId) { if (filter.listId != null && filter.listId != TaskFilter.INBOX && list == null) onFilter(filter.copy(listId = null)) }
    val reduce = model.data.settings.reduceMotion

    QuickAddBox(model, if (view == TasksView.TODAY) AddTo.TODAY else AddTo.NO_DATE, listId = if (view == TasksView.ALL) list?.id else null)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        TasksView.entries.forEach { v ->
            TabButton(v.label, v == view, {
                model.tasksView = v
                // The list filter is an All/Done filter; leaving those views drops it so no key stays lit wrongly.
                if ((v == TasksView.TODAY || v == TasksView.UPCOMING) && filter.listId != null) onFilter(filter.copy(listId = null))
            })
        }
    }
    // Every list with its outstanding count, one tap away (REP-092) instead of behind the pull-down Filter.
    if (lists.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            TabButton("All lists", filter.listId == null, {
                onFilter(filter.copy(listId = null))
                model.tasksView = if (view == TasksView.DONE) TasksView.DONE else TasksView.ALL
            })
            lists.forEach { l ->
                val count = live.count { it.isOpen && it.listId == l.id }
                TabButton(if (count > 0) "${l.name} $count" else l.name, filter.listId == l.id, {
                    onFilter(filter.copy(listId = l.id))
                    model.tasksView = if (view == TasksView.DONE) TasksView.DONE else TasksView.ALL
                })
            }
        }
    }
    val card = taskCards(model, host)
    val inList = taskCards(model, host, showList = false)
    when (view) {
        TasksView.TODAY -> TodayList(model, onFocus, listId = null)
        TasksView.ALL -> {
            PullRevealSlot(reveal, reduce) { TaskFilterBox(model, filter, onFilter, withStatus = false) }
            val narrowed = filter
            if (list != null && list.kind != ListKind.TASKS) {
                val items = live.filter { it.listId == list.id && narrowed.copy(status = TaskFilter.Status.ANY).matches(it) }
                    .sortedWith(compareBy<Task>({ !it.isOpen }, { it.createdAtMillis }))
                if (items.isEmpty()) EmptyState("Nothing in ${list.name}", icon = list.icon)
                else TaskGroup(groupTitle(model, list), items, card = inList)
            } else {
                val groups = TaskViews.outstanding(live, lists.map { it.id }, narrowed)
                if (groups.isEmpty()) EmptyState(
                    if (filter.isNarrowed || list != null) "Nothing matches" else "No open tasks",
                    icon = "journal",
                    action = if (filter.isNarrowed) "Clear filters" to { onFilter(TaskFilter()) } else "Add a task" to { model.addTaskRequested = true },
                ) else groups.forEach { g ->
                    val l = g.listId?.let { id -> lists.firstOrNull { it.id == id } }
                    val title = if (l != null) groupTitle(model, l) else if (lists.isEmpty() || list != null) null else "No list"
                    TaskGroup(title, g.tasks, card = inList)
                }
            }
        }
        TasksView.UPCOMING -> {
            val shown = TaskViews.upcoming(live, store.now)
            shown.groupBy { it.planned ?: it.due!!.date }.forEach { (day, ts) -> TaskGroup(dayName(model, day), ts, dayOf = day, card = card) }
            if (shown.isEmpty()) EmptyState("Nothing coming up", icon = "timer-ball", action = "Add a task" to { model.addTaskRequested = true })
        }
        TasksView.DONE -> {
            PullRevealSlot(reveal, reduce) { TaskFilterBox(model, filter, onFilter, withStatus = false) }
            val found = filter.copy(status = TaskFilter.Status.FINISHED).apply(live)
            if (found.size > SHOWN) Text("Showing the first $SHOWN of ${found.size}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            if (found.isEmpty()) EmptyState(if (filter.isNarrowed || list != null) "Nothing matches" else "Nothing finished yet", icon = "scope-lens")
            else TaskGroup(null, found.take(SHOWN), card = card)
        }
    }
}

private fun groupTitle(model: AppModel, l: TaskList): String {
    if (l.kind == ListKind.TASKS) return l.name
    val items = model.data.tasks.filter { it.listId == l.id && it.state != TaskState.DELETED }
    return "${l.name}, ${items.count { it.state == TaskState.DONE }} of ${items.size} done"
}

private fun dayName(model: AppModel, day: LocalDate): String {
    val today = model.store.today
    return when (day) {
        today -> "Today"
        today.plus(DatePeriod(days = 1)) -> "Tomorrow"
        today.minus(DatePeriod(days = 1)) -> "Yesterday"
        else -> day.short()
    }
}
