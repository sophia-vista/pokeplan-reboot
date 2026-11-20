package app.pokeplan.ui.plan

import app.pokeplan.ui.theme.Sizes
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
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.KeyLabel
import app.pokeplan.ui.theme.PixelKey
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import app.pokeplan.ui.pokemon.label
import app.pokeplan.ui.screens.PixelDateField
import app.pokeplan.ui.screens.label


/**
 * A day, optional: a few presets counted from [today] (label to days ahead), "No date", and "Pick a date" for a month to tap.
 * [label] names the field.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DayPicker(
    label: String, value: LocalDate?, today: LocalDate, presets: List<Pair<String, Int>>, noneLabel: String = "No date",
    onChange: (LocalDate?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        FieldLabel(label)
        // Presets for the common days; "Pick a date" opens the month for any other (the shared picker, with no limit either way).
        PixelDateField(value, today, ModuleAccent.PLAN.accent, onChange, presets = presets, noneLabel = noneLabel)
    }
}
