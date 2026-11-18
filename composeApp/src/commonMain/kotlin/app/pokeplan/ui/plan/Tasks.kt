package app.pokeplan.ui.plan

import androidx.compose.foundation.layout.Arrangement
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
import app.pokeplan.ui.screens.TabButton
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.screens.short
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** The views of the one Tasks workspace (PLAN.md section 3): Open is the default, grouped by list. */
enum class TasksView(val label: String) { OPEN("Open"), TODAY("Today"), INBOX("Inbox"), UPCOMING("Upcoming"), DONE("Done") }

/** Rows shown at most in Done; a search narrows the rest down. */
private const val SHOWN = 60

/**
 * Plan's Tasks workspace: quick add into the Inbox (or Today, or the chosen list), the views as chips, the lists as chips with
 * Manage (make, edit, archive), and the tasks. One engine: a Checklist or Tracker list only changes how its group is titled and
 * that its finished items stay on view. [onFocus] is null where there is no Focus (desktop and the web).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TasksWorkspace(model: AppModel, onFocus: ((Task) -> Unit)?) {
    val store = model.store
    val live = planTasks(model)
    val host = remember { CardHost() }
    val view = model.tasksView
    var listId by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(TaskFilter()) }
    var managing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val lists = store.activeLists
    val list = listId?.let { id -> lists.firstOrNull { it.id == id } }
    // A list that was archived or deleted is no longer the one chosen.
    LaunchedEffect(list, listId) { if (listId != null && list == null) { listId = null; editing = false } }
    val scoped = view == TasksView.OPEN || view == TasksView.UPCOMING || view == TasksView.DONE

    QuickAddBox(model, if (view == TasksView.TODAY && list == null) AddTo.TODAY else AddTo.INBOX, listId = list?.id.takeIf { scoped })
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        TasksView.entries.forEach { v -> TabButton(v.label, v == view, { model.tasksView = v }) }
    }
    if (scoped) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            if (lists.isNotEmpty()) TabButton("All lists", list == null, { listId = null; editing = false })
            lists.forEach { l -> TabButton(l.name, l.id == listId, { listId = if (listId == l.id) null else l.id; editing = false }) }
            PixelButton(if (managing) "Close lists" else if (lists.isEmpty()) "New list" else "Manage lists", { managing = !managing }, color = Pixel.InkSoft, compact = true)
        }
        if (managing) ListsSection(model, live) { id -> listId = id; managing = false }
        if (list != null) {
            if (editing) ListEditor(model, list) { kept -> editing = false; if (kept == null) listId = null }
            else PixelButton("Edit ${list.name}", { editing = true }, color = Pixel.InkSoft, compact = true)
        }
    }
    val card = taskCards(model, host)
    val inList = taskCards(model, host, showList = false)
    when (view) {
        TasksView.OPEN -> {
            TaskFilterBox(model, filter, { filter = it }, withStatus = false)
            val narrowed = filter.copy(listId = list?.id)
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
                    TaskGroup(if (l == null) "Inbox" else groupTitle(model, l), g.tasks, card = inList)
                }
            }
        }
        TasksView.TODAY -> TodayList(model, onFocus)
        TasksView.INBOX -> {
            val shown = TaskViews.inbox(live)
            if (shown.isEmpty()) EmptyState("The inbox is empty", icon = "seal-bag", action = "Add a task" to { model.addTaskRequested = true })
            else TaskGroup(null, shown.sortedByDescending { it.createdAtMillis }, card = card)
        }
        TasksView.UPCOMING -> {
            // By the day each task is on (Todoist's Upcoming), with no cap on how far ahead.
            val shown = TaskViews.upcoming(live, store.now).filter { list == null || it.listId == list.id }
            shown.groupBy { it.planned ?: it.due!!.date }.forEach { (day, ts) -> TaskGroup(dayName(model, day), ts, dayOf = day, card = card) }
            if (shown.isEmpty()) EmptyState("Nothing coming up", icon = "timer-ball", action = "Add a task" to { model.addTaskRequested = true })
        }
        TasksView.DONE -> {
            TaskFilterBox(model, filter, { filter = it }, withStatus = false)
            val found = filter.copy(status = TaskFilter.Status.FINISHED, listId = list?.id).apply(live)
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
