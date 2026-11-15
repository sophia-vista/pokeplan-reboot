package app.pokeplan.ui.mint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import app.pokeplan.mint.Payday
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.screens.PixelDateField
import app.pokeplan.ui.screens.TabButton
import app.pokeplan.ui.screens.short
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.theme.ToggleRow

/** "Paydays and holidays" (MINT.md 8.3B): which days a working-day bill skips: the Philippine holidays, each switchable, and the player's own days off. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PaydaysAndHolidays(model: AppModel) {
    val mint = model.mint
    val settings = model.mintData.settings
    val today = model.store.today
    var year by remember { mutableStateOf(today.year) }
    var newOff by remember { mutableStateOf(today) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Paydays and holidays")
            ToggleRow("Count Philippine holidays", settings.builtInHolidays) { mint.setBuiltInHolidays(it) }
            SectionLabel("Days off")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                listOf(today.year, today.year + 1).forEach { y -> TabButton("$y", y == year, { year = y }, key = ModuleAccent.MINT.accent) }
            }
            Column(Modifier.alpha(if (settings.builtInHolidays) 1f else 0.5f)) {
                Payday.holidayList(year, settings).forEachIndexed { i, (day, counted) ->
                    if (i > 0) HorizontalDivider(color = Pixel.Border)
                    MintLine(onClick = { mint.setHolidayCounted(day, !counted) }, onClickLabel = if (counted) "Stop counting ${day.short()}" else "Count ${day.short()}", trail = {
                        Text(if (counted) "on" else "off", style = MaterialTheme.typography.titleSmall)
                    }) { Text(day.short(), style = MaterialTheme.typography.bodyLarge) }
                }
            }
            SectionLabel("Your days off")
            PixelDateField(newOff, today, ModuleAccent.MINT.accent, { newOff = it ?: newOff }, presets = emptyList())
            PixelButton("Add", { mint.setDayOff(newOff, true) }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, compact = true, enabled = newOff !in settings.daysOff)
            settings.daysOff.forEach { d ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.M), verticalAlignment = Alignment.CenterVertically) {
                    Text(d.short() + if (d.year != today.year) " ${d.year}" else "", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    PixelIconButton("close", "Remove ${d.short()}", { mint.setDayOff(d, false) }, Pixel.InkSoft)
                }
            }
        }
    }
}
