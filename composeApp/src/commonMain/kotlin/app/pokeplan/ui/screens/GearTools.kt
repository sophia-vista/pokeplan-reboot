package app.pokeplan.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import app.pokeplan.ui.launcher.Calculator
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
@Composable
internal fun FlashlightTool(model: AppModel, onUse: () -> Unit) {
    val torch = model.torch?.takeIf { it.available() }
    var on by remember { mutableStateOf(torch?.isOn() == true) }
    // The light's state is the hardware's: another app or the system switching it shows here at once. Leaving the tool turns it off, so it is never left on by accident.
    DisposableEffect(torch) {
        val stop = torch?.observe { on = it }
        onDispose { stop?.invoke(); torch?.release() }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Text(if (torch != null) "The phone's flashlight." else "This device has no flashlight, so the screen lights up white.", style = MaterialTheme.typography.bodyLarge)
            PixelButton(if (on) "Turn off" else "Turn on", {
                if (torch != null) on = torch.set(!on) else on = !on
                onUse()
            }, Modifier.fillMaxWidth(), if (on) Pixel.InkSoft else Pixel.Blue)
        }
    }
    if (on && torch == null) Dialog({ on = false }, DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)) {
        Box(Modifier.fillMaxSize().background(Color.White).clickable { on = false }, contentAlignment = Alignment.BottomCenter) {
            Text("Tap to turn off", Modifier.padding(bottom = 48.dp), style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
        }
    }
}

// ----- Calculator and unit converter ------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CalculatorTool(onUse: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        TabButton("Calculator", page == 0, { page = 0 })
        TabButton("Units", page == 1, { page = 1 })
    }
    if (page == 0) PocketCalculator(onUse) else UnitConverterPage(onUse)
}

@Composable
private fun PocketCalculator(onUse: () -> Unit) {
    var calc by remember { mutableStateOf(Calculator()) }
    var presses by remember { mutableIntStateOf(0) }
    fun press(action: Calculator.() -> Unit) { calc.action(); presses++; if (calc.display != "0") onUse() }
    val rows: List<List<Pair<String, Calculator.() -> Unit>>> = listOf(
        listOf<Pair<String, Calculator.() -> Unit>>("C" to { clear() }, "Del" to { delete() }, "+/-" to { negate() }, "÷" to { operate('/') }),
        listOf<Pair<String, Calculator.() -> Unit>>("7" to { digit('7') }, "8" to { digit('8') }, "9" to { digit('9') }, "×" to { operate('x') }),
        listOf<Pair<String, Calculator.() -> Unit>>("4" to { digit('4') }, "5" to { digit('5') }, "6" to { digit('6') }, "−" to { operate('-') }),
        listOf<Pair<String, Calculator.() -> Unit>>("1" to { digit('1') }, "2" to { digit('2') }, "3" to { digit('3') }, "+" to { operate('+') }),
        listOf<Pair<String, Calculator.() -> Unit>>("0" to { digit('0') }, "." to { point() }, "=" to { equals() }),
    )
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            // presses is read so the display follows each key.
            Text(if (presses >= 0) calc.display else "", Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineMedium, maxLines = 1)
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    row.forEachIndexed { i, (label, action) ->
                        PixelButton(label, { press(action) }, Modifier.weight(if (row.size == 3 && i == 0) 2f else 1f), if (label in "÷×−+=") Pixel.Blue else Pixel.InkSoft, compact = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun UnitConverterPage(onUse: () -> Unit) {
    var kindIndex by remember { mutableIntStateOf(0) }
    val kind = Units.kinds[kindIndex]
    var fromIndex by remember(kindIndex) { mutableIntStateOf(0) }
    var toIndex by remember(kindIndex) { mutableIntStateOf(1.coerceAtMost(kind.units.lastIndex)) }
    var text by remember { mutableStateOf("1") }
    val value = text.replace(',', '.').toDoubleOrNull()
    val result = value?.let { Units.convert(it, kind.units[fromIndex], kind.units[toIndex]) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            FieldLabel("What to measure")
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Units.kinds.forEachIndexed { i, k -> TabButton(k.label, i == kindIndex, { kindIndex = i }) }
            }
            PixelField(
                text, { text = it.take(16); onUse() }, Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), label = "Amount",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                OptionPicker("From", kind.units.map { "${it.symbol} (${it.name})" }, fromIndex, Modifier.weight(1f)) { fromIndex = it }
                OptionPicker("To", kind.units.map { "${it.symbol} (${it.name})" }, toIndex, Modifier.weight(1f)) { toIndex = it }
            }
            Text(
                if (result == null) "Type a number" else "${plain(value!!)} ${kind.units[fromIndex].symbol} = ${plain(result)} ${kind.units[toIndex].symbol}",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/** A number without a long tail of digits: up to 6 decimals, trailing zeros cut. */
internal fun plain(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "?"
    val rounded = (value * 1e6).roundToLong() / 1e6
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString().trimEnd('0').trimEnd('.')
}

/** A labelled drop-down of [options] showing the one at [selected]. */
@Composable
private fun OptionPicker(label: String, options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        FieldLabel(label)
        Box {
            PixelButton(options.getOrElse(selected) { "" }, { open = true }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            DropdownMenu(open, { open = false }) {
                options.forEachIndexed { i, option -> DropdownMenuItem({ Text(option) }, { onSelect(i); open = false }) }
            }
        }
    }
}

// ----- Currency ---------------------------------------------------------------------------------------------------------

/** Converts money at the day's rates (Frankfurter, the European Central Bank's), keeping the last rates for when there is no connection. */
@Composable
internal fun CurrencyTool(model: AppModel, onUse: () -> Unit) {
    val rates = model.data.gearRates
    val today = model.store.today.toString()
    var status by remember { mutableStateOf("") }
    val client = remember { RatesClient() }
    // One client for as long as the tool is open, closed when it is left.
    DisposableEffect(client) { onDispose { client.close() } }
    val scope = rememberCoroutineScope()
    fun refresh() = scope.launch {
        status = "Fetching today's rates"
        val fresh = client.latest(model.store.nowMillis)
        // A failed fetch never touches the rates already kept.
        if (fresh != null) { model.store.saveGearRates(fresh); status = "" } else status = (client.lastError ?: "Offline") + (if (rates != null) " Showing the last rates." else "")
    }
    LaunchedEffect(Unit) { if (rates == null || rates.date < today.take(10)) refresh() }
    TextBox {
        if (rates == null) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(status.ifEmpty { "No rates yet. Connect to the internet to fetch them." }, style = MaterialTheme.typography.bodyLarge)
                PixelButton("Fetch rates", { refresh() }, Modifier.fillMaxWidth(), Pixel.Blue)
            }
            return@TextBox
        }
        val codes = remember(rates) { (COMMON_CURRENCIES.filter { it in rates.perUsd } + rates.perUsd.keys.sorted().filter { it !in COMMON_CURRENCIES }) }
        var fromIndex by remember(codes) { mutableIntStateOf(codes.indexOf("USD").coerceAtLeast(0)) }
        var toIndex by remember(codes) { mutableIntStateOf(codes.indexOf("EUR").coerceAtLeast(0)) }
        var text by remember { mutableStateOf("1") }
        val amount = text.replace(',', '.').toDoubleOrNull()
        val result = amount?.let { convertCurrency(it, codes[fromIndex], codes[toIndex], rates) }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelField(
                text, { text = it.take(14); onUse() }, Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), label = "Amount",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                OptionPicker("From", codes, fromIndex, Modifier.weight(1f)) { fromIndex = it }
                OptionPicker("To", codes, toIndex, Modifier.weight(1f)) { toIndex = it }
            }
            Text(if (result == null) "Type a number" else "${twoDecimals(amount!!)} ${codes[fromIndex]} = ${twoDecimals(result)} ${codes[toIndex]}", style = MaterialTheme.typography.titleMedium)
            Text(if (status.isNotEmpty()) status else ratesFreshness(rates, model.store.nowMillis), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            PixelButton("Refresh rates", { refresh() }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        }
    }
}

private val COMMON_CURRENCIES = listOf("USD", "EUR", "GBP", "JPY", "PHP", "AUD", "CAD", "CHF", "CNY", "INR", "KRW", "SGD", "HKD", "MXN", "BRL")

/** A money amount to two decimals. */
internal fun twoDecimals(value: Double): String {
    val cents = (abs(value) * 100).roundToLong()
    val whole = cents / 100
    val fraction = (cents % 100).toString().padStart(2, '0')
    return (if (value < 0 && cents != 0L) "-" else "") + "$whole.$fraction"
}

// ----- Picker -----------------------------------------------------------------------------------------------------------

/** Picks one thing from a short list you type; the list stays on this device. */
@Composable
internal fun PickerTool(model: AppModel, onUse: () -> Unit) {
    val options = model.data.pickerOptions
    var text by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    val reduce = model.data.settings.reduceMotion
    val scope = rememberCoroutineScope()
    fun pick() {
        if (options.isEmpty() || picking) return
        picking = true
        scope.launch {
            // A quick roll through the list before it settles, unless motion is off.
            if (!reduce) repeat(8) { shown = options.random(); delay(70L + it * 18L) }
            shown = options.random()
            picking = false
            onUse()
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelField(
                text, { text = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, label = "Add something to pick from",
            )
            PixelButton("Add", { model.store.setPickerOptions(options + text); text = "" }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true, enabled = text.isNotBlank() && options.size < app.pokeplan.app.GameStore.MAX_PICKER_OPTIONS)
            options.forEach { option ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(option, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    PixelButton("Remove", { model.store.setPickerOptions(options - option) }, color = Pixel.InkSoft, compact = true)
                }
            }
            if (options.isEmpty()) Text("Add two or more things, then pick.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
    shown?.let { TextBox { Text(it, Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall) } }
    PixelButton(if (picking) "Picking" else "Pick one", { pick() }, Modifier.fillMaxWidth(), Pixel.Blue, enabled = options.size >= 2 && !picking)
}

// ----- Coin toss --------------------------------------------------------------------------------------------------------

/** Heads or tails: a coin that spins and lands. */
@Composable
internal fun CoinTool(model: AppModel, onUse: () -> Unit) {
    val spin = remember { Animatable(0f) }
    var side by remember { mutableStateOf<String?>(null) }
    var flipping by remember { mutableStateOf(false) }
    val reduce = model.data.settings.reduceMotion
    val scope = rememberCoroutineScope()
    TextBox {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Box(Modifier.height(120.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                ItemIcon("bottle-cap", Modifier.size(96.dp).graphicsLayer { rotationX = spin.value }, size = 96.dp, description = "Coin")
            }
            Text(side ?: "Flip it", style = MaterialTheme.typography.headlineSmall)
            PixelButton(if (flipping) "Flipping" else "Toss the coin", {
                if (!flipping) scope.launch {
                    flipping = true
                    val heads = Random.nextBoolean()
                    if (!reduce) {
                        // Four whole turns from the last whole turn, plus a half turn for tails, so the coin always lands showing its face.
                        val to = (spin.value / 360f).toInt() * 360f + 1440f + if (heads) 0f else 180f
                        spin.animateTo(to, tween(900))
                    }
                    side = if (heads) "Heads" else "Tails"
                    flipping = false
                    onUse()
                }
            }, Modifier.fillMaxWidth(), Pixel.Blue, enabled = !flipping)
        }
    }
}
