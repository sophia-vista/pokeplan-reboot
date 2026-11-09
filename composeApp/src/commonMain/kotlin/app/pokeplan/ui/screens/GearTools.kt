package app.pokeplan.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import app.pokeplan.ui.theme.dialogueBox
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.gear.RatesClient
import app.pokeplan.ui.gear.ratesFreshness
import app.pokeplan.ui.gear.Units
import app.pokeplan.ui.gear.convertCurrency
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.random.Random

// ----- Flashlight -----------------------------------------------------------------------------------------------------

/** The phone's flashlight where there is one; elsewhere (desktop, web) the screen goes white. */

/** A labelled drop-down of [options] showing the one at [selected]. */
@Composable
internal fun OptionPicker(label: String, options: List<String>, selected: Int, modifier: Modifier = Modifier, short: List<String>? = null, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        FieldLabel(label)
        Box {
            PixelButton((short ?: options).getOrElse(selected) { "" }, { open = true }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            // A pixel list in a dialogue frame, not Material's rounded menu: it opens under the key and scrolls when long.
            if (open) androidx.compose.ui.window.Popup(
                alignment = Alignment.TopStart, offset = androidx.compose.ui.unit.IntOffset(0, with(androidx.compose.ui.platform.LocalDensity.current) { 52.dp.roundToPx() }),
                onDismissRequest = { open = false }, properties = androidx.compose.ui.window.PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier.width(220.dp).heightIn(max = 320.dp).dialogueBox(fill = Pixel.Panel)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = Spacing.M, vertical = 10.dp),
                ) {
                    options.forEachIndexed { i, option ->
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = Spacing.Target)
                                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Choose $option") { onSelect(i); open = false },
                            contentAlignment = Alignment.CenterStart,
                        ) { Text(option, style = MaterialTheme.typography.bodyLarge, color = if (i == selected) Pixel.Blue else Pixel.Ink) }
                    }
                }
            }
        }
    }
}
