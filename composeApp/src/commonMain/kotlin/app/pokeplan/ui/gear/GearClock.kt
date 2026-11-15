package app.pokeplan.ui.gear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.gear.WorldClock
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import app.pokeplan.ui.plan.clock
import app.pokeplan.ui.pokemon.label
import app.pokeplan.ui.screens.label

/** The World clock: the time in the places you chose (the first is home), a time converted between them, and two date sums. The list stays on this phone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorldClockTool(model: AppModel, onUse: () -> Unit) {
    val store = model.store
    val gear = model.data.gear
    val hour12 = false
    val ids = gear.clockZones.ifEmpty { WorldClock.starting }
    val instant = Instant.fromEpochMilliseconds(model.now.takeIf { it > 0 } ?: store.nowMillis)
    val readings = ids.mapNotNull { WorldClock.reading(it, instant) }
    val home = readings.firstOrNull()
    var adding by remember { mutableStateOf(false) }
    fun save(next: List<String>) { store.changeGear { it.copy(clockZones = next) }; onUse() }

    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            FieldLabel("Now")
            readings.forEachIndexed { i, r ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name + if (i == 0) " (home)" else "", style = MaterialTheme.typography.bodyLarge)
                        Text(WorldClock.offsetText(r.offsetMinutes) + if (home != null && i > 0) ", " + WorldClock.dayWord(r.time.date, home.time.date) else "", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    }
                    Text(WorldClock.clockText(r.time, hour12), style = MaterialTheme.typography.headlineSmall)
                    if (i > 0) PixelIconButton("close", "Remove ${r.name}", { save(ids - r.id) })
                    else PixelIconButton("arrow-down", "Move ${r.name} down", { if (ids.size > 1) save(ids.drop(1) + ids.first()) })
                }
            }
            PixelButton(if (adding) "Done adding" else "Add a place", { adding = !adding }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            if (adding) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                WorldClock.places.filter { it.id !in ids }.forEach { p -> PixelButton(p.name, { save(ids + p.id) }, color = Pixel.InkSoft, compact = true) }
            }
        }
    }
    val today = store.now.date.let { LocalDate(it.year, it.monthNumber, it.dayOfMonth) }
    ConvertCard(ids, today, onUse)
    DateCard(today, onUse)
}

@Composable
private fun ConvertCard(ids: List<String>, today: LocalDate, onUse: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val from = ids.firstOrNull() ?: return
    val parts = text.trim().split(':', '.')
    val h = parts.getOrNull(0)?.toIntOrNull(); val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            FieldLabel("Convert a time")
            PixelField(text, { text = it.take(5); onUse() }, Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), label = "Time at home", placeholder = "21:30")
            if (h != null && h in 0..23 && m in 0..59) {
                // Today's date, so the answer follows the summer time in force now.
                val base = LocalDateTime(today.year, today.monthNumber, today.dayOfMonth, h, m)
                ids.drop(1).forEach { id ->
                    val there = WorldClock.convert(base, from, id)
                    val name = WorldClock.place(id)?.name ?: id
                    if (there != null) Text("$name: ${WorldClock.clockText(there, false)}" + if (there.date != base.date) (if (there.date > base.date) " next day" else " the day before") else "", style = MaterialTheme.typography.bodyLarge)
                }
            } else if (text.isNotBlank()) Text("Like 21:30", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
}

@Composable
private fun DateCard(today: LocalDate, onUse: () -> Unit) {
    var a by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("") }
    val date = runCatching { LocalDate.parse(a.trim()) }.getOrNull()
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            FieldLabel("Date sums")
            PixelField(a, { a = it.take(10); onUse() }, Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), label = "A date", placeholder = "2027-02-14")
            if (date != null) {
                val d = WorldClock.daysBetween(today, date)
                Text(
                    when { d == 0 -> "That is today."; d > 0 -> "$d days from today (${d / 7} weeks and ${d % 7} days)."; else -> "${-d} days ago." } + " Day ${WorldClock.dayOfYear(date)} of the year, a ${date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}.", // copy-ok: result
                    style = MaterialTheme.typography.bodyLarge,
                )
                PixelField(days, { days = it.take(5); onUse() }, Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = "Add days to it (or minus)")
                days.trim().toIntOrNull()?.takeIf { it in -36500..36500 }?.let { n -> WorldClock.addDays(date, n).let { Text("$n days from then: $it, a ${it.dayOfWeek.name.lowercase().replaceFirstChar { c -> c.uppercase() }}.", style = MaterialTheme.typography.bodyLarge) } }
            } else if (a.isNotBlank()) Text("Type the date as year-month-day, like 2027-02-14.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
}
