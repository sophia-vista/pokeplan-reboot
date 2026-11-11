package app.pokeplan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.Spacing

/**
 * One date field for every module (design review, 11 November 2026): the days people reach for as keys ([presets], days back from
 * [today]), and "Pick a date" opening a month to tap, so a record can be made for any day and no date is ever typed as `2026-11-07`.
 * Days after [max] are dimmed and cannot be chosen (a payment is not made in the future); [min] likewise. [accent] colours the keys.
 */
@Composable
fun PixelDatePicker(
    value: LocalDate, today: LocalDate, accent: Color, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier,
    presets: List<Pair<String, Int>> = listOf("Today" to 0, "Yesterday" to -1), max: LocalDate? = today, min: LocalDate? = null, mondayFirst: Boolean = true, initiallyOpen: Boolean = false,
) {
    var open by remember { mutableStateOf(initiallyOpen) }
    var offset by remember(value, open) { mutableStateOf(LocalDate(value.year, value.monthNumber, 1).let { (value.year * 12 + value.monthNumber) - (today.year * 12 + today.monthNumber) }) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            presets.forEach { (label, days) ->
                val day = today.plus(DatePeriod(days = days))
                TabButton(label, value == day, { onChange(day); open = false }, Modifier.weight(1f), key = accent)
            }
            val custom = presets.none { today.plus(DatePeriod(days = it.second)) == value }
            TabButton(if (custom) value.short() else "Pick a date", custom || open, { open = !open }, Modifier.weight(1.4f), key = accent)
        }
        if (open) {
            val grid = monthGrid(today, offset, mondayFirst)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelIconButton("chevron-left", "Earlier month", { offset -= 1 })
                Text(grid.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                PixelIconButton("chevron-right", "Later month", { offset += 1 })
            }
            Row(Modifier.fillMaxWidth()) {
                (if (mondayFirst) dayNames else dayNames.takeLast(1) + dayNames.dropLast(1)).forEach {
                    Text(it.take(2), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, textAlign = TextAlign.Center)
                }
            }
            grid.weeks.forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    week.forEach { day ->
                        val allowed = (max == null || day <= max) && (min == null || day >= min)
                        val inMonth = day.monthNumber == grid.first.monthNumber
                        TabButton(
                            day.dayOfMonth.toString(), day == value, { onChange(day); open = false },
                            Modifier.weight(1f), enabled = allowed && inMonth, key = if (day == today) Pixel.InkSoft else accent,
                        )
                    }
                }
            }
        }
    }
}
