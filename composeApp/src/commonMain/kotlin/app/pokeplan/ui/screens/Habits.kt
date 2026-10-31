package app.pokeplan.ui.screens

import app.pokeplan.ui.Sfx
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.pokeplan.plan.Category
import app.pokeplan.plan.HabitTarget
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.FieldLabel
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.RankBadge
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.theme.title
import kotlinx.datetime.DayOfWeek
import androidx.compose.foundation.layout.size

/** Per-week and per-month habits count weeks and months, the others days. */
private fun streakUnit(target: HabitTarget): String = when (target) {
    is HabitTarget.PerWeek -> " weeks"
    is HabitTarget.PerMonth -> " months"
    else -> ""
}

/** How often a habit is meant to happen, in the words of the picker. */
private enum class HabitKind(val label: String) { DAILY("Every day"), WEEKDAYS("Chosen days"), PER_WEEK("Times a week"), PER_MONTH("Times a month") }

private val WEEK = DayOfWeek.entries

/** "Every day", "Mon Wed Fri", "3 times a week", "10 times a month". */
internal fun habitTargetLabel(target: HabitTarget): String = when (target) {
    HabitTarget.Daily -> "Every day"
    is HabitTarget.Weekdays -> if (target.days.size == 7) "Every day" else WEEK.filter { it in target.days }.joinToString(" ") { dayNames[it.ordinal] }
    is HabitTarget.PerWeek -> if (target.times == 1) "Once a week" else "${target.times} times a week"
    is HabitTarget.PerMonth -> if (target.times == 1) "Once a month" else "${target.times} times a month"
}

@Composable
internal fun HabitsView(model: AppModel) {
    val store = model.store
    var title by remember { mutableStateOf("") }
    var more by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf(HabitKind.DAILY) }
    var days by remember { mutableStateOf(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) }
    var times by remember { mutableStateOf(3) }
    var category by remember { mutableStateOf(Category.HEALTH) }
    fun target(): HabitTarget = when (kind) {
        HabitKind.DAILY -> HabitTarget.Daily
        HabitKind.WEEKDAYS -> HabitTarget.Weekdays(days)
        HabitKind.PER_WEEK -> HabitTarget.PerWeek(times.coerceIn(1, 7))
        HabitKind.PER_MONTH -> HabitTarget.PerMonth(times.coerceIn(1, 31))
    }
    val ready = title.isNotBlank() && (kind != HabitKind.WEEKDAYS || days.isNotEmpty())
    fun add() {
        if (!ready) return
        store.addHabit(title, target(), category = category)
        title = ""; more = false; kind = HabitKind.DAILY; category = Category.HEALTH
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelField(
                    title, { title = it }, Modifier.weight(1f), singleLine = true,
                    placeholder = "New habit",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                )
                PixelButton("Add", ::add, color = Pixel.Red, enabled = ready)
            }
            PixelButton(if (more) "Fewer options" else "How often, and what kind", { more = !more }, color = Pixel.InkSoft, compact = true)
            if (more) {
                FieldLabel("How often")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    HabitKind.entries.forEach { k ->
                        TabButton(k.label, kind == k, {
                            kind = k
                            // The count means something different for a week and a month, so start each at a usual number.
                            if (k == HabitKind.PER_WEEK) times = times.coerceIn(1, 7)
                            if (k == HabitKind.PER_MONTH && times <= 7) times = 10
                        })
                    }
                }
                if (kind == HabitKind.WEEKDAYS) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        WEEK.forEach { d -> TabButton(dayNames[d.ordinal], d in days, { days = if (d in days) days - d else days + d }) }
                    }
                    if (days.isEmpty()) Text("Choose at least one day.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                }
                if (kind == HabitKind.PER_WEEK || kind == HabitKind.PER_MONTH) {
                    val max = if (kind == HabitKind.PER_WEEK) 7 else 31
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        PixelButton("-", { times = (times - 1).coerceAtLeast(1) }, color = Pixel.InkSoft, compact = true)
                        Text(habitTargetLabel(target()), style = MaterialTheme.typography.bodyLarge)
                        PixelButton("+", { times = (times + 1).coerceAtMost(max) }, color = Pixel.InkSoft, compact = true)
                    }
                    Text("Miss one now and then and the streak carries on.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                }
                FieldLabel("Category")
                CategoryChips(category) { category = it ?: category }
            }
        }
    }
    if (model.data.habits.isEmpty()) EmptyState(emptyTitle(View.HABITS), emptyLine(View.HABITS), icon = View.HABITS.icon)
    model.data.habits.forEach { h ->
        val doneToday = store.today in h.completions
        ListRow(
            lead = { RankBadge(h.rank) },
            trail = {
                if (doneToday) PixelButton("Undo", { store.undoHabit(h.id) }, color = Pixel.InkSoft, compact = true)
                else PixelButton("Done", { model.effect(Sfx.HABIT_TICK); store.completeHabit(h.id) }, color = Pixel.Green, compact = true)
            },
        ) {
            Text(h.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOf(
                    if (doneToday) "Done today" else "Streak ${h.streak(store.today, model.data.restDays, store.perks.extraForgivenDays, store.firstDayOfWeek)}${streakUnit(h.target)}",
                    habitTargetLabel(h.target), h.category.label,
                ).joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
