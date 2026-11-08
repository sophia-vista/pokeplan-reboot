package app.pokeplan.ui.screens

import app.pokeplan.ui.theme.PixelShape

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import app.pokeplan.plan.Category
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.KeyLabel
import app.pokeplan.ui.theme.PixelKey
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * A category chip: its colour as a dot, so the same six colours as the day view and Growth tell them apart. Used where a
 * category is picked (the task editor, habits, goals) and where tasks are filtered.
 */
@Composable
internal fun CategoryChip(category: Category, selected: Boolean, onClick: () -> Unit) {
    val color = categoryColor(category)
    PixelKey(onClick, key = color, selected = selected, fill = true, role = Role.Tab, horizontal = Spacing.M) { look ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            // The dot keeps the category's colour on a filled key too: white ring so it shows on its own colour.
            Box(Modifier.size(10.dp).background(color, PixelShape).then(if (selected) Modifier.border(2.dp, Color.White, PixelShape) else Modifier))
            KeyLabel(category.label, look)
        }
    }
}

/** All six categories as chips. With [allowNone], tapping the chosen one again clears the choice (a filter); otherwise it stays. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryChips(selected: Category?, allowNone: Boolean = false, onSelect: (Category?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Category.entries.forEach { c ->
            CategoryChip(c, c == selected, { onSelect(if (allowNone && c == selected) null else c) })
        }
    }
}

/**
 * A day, optional: a few presets counted from [today] (label to days ahead), "No date", and, once a day is set, a nudge of
 * [step] days each way with the date between. [label] names the field.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DayPicker(
    label: String, value: LocalDate?, today: LocalDate, presets: List<Pair<String, Int>>, noneLabel: String = "No date",
    step: Int = 1, onChange: (LocalDate?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        FieldLabel(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            TabButton(noneLabel, value == null, { onChange(null) })
            presets.forEach { (name, days) ->
                val day = today.plus(DatePeriod(days = days))
                TabButton(name, value == day, { onChange(day) })
            }
        }
        if (value != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelButton(if (step == 1) "-1 day" else "-$step days", { onChange(value.plus(DatePeriod(days = -step))) }, color = Pixel.InkSoft, compact = true)
            Text(value.short(), style = MaterialTheme.typography.bodyLarge)
            PixelButton(if (step == 1) "+1 day" else "+$step days", { onChange(value.plus(DatePeriod(days = step))) }, color = Pixel.InkSoft, compact = true)
        }
    }
}
