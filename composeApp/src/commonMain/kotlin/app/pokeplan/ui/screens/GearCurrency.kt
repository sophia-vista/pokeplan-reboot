package app.pokeplan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.gear.CalcEngine
import app.pokeplan.ui.gear.CalcResult
import app.pokeplan.ui.gear.Currencies
import app.pokeplan.ui.gear.RatesClient
import app.pokeplan.ui.gear.Travel
import app.pokeplan.ui.gear.ratesFreshness
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * The currency converter: your home currency first (the Philippine peso until you change it), search by name or code, a swap, a board of the
 * currencies you watch, a rate of your own (what a money changer offered) and a "what it really costs" panel that keeps the reference rate, the
 * rate you were offered and the fees apart. Rates are the European Central Bank's daily reference rates, kept for offline use with their age
 * and source shown (and a warning when they are old); they are not what a bank, card or changer gives, and the screen says so.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CurrencyTool(model: AppModel, onUse: () -> Unit) {
    val store = model.store
    val gear = model.data.gear
    val rates = model.data.gearRates
    val home = gear.homeCurrency
    var status by remember { mutableStateOf("") }
    val client = remember { RatesClient() }
    DisposableEffect(client) { onDispose { client.close() } }
    val scope = rememberCoroutineScope()
    fun refresh() = scope.launch {
        status = "Fetching today's rates"
        val fresh = client.latest(store.nowMillis)
        // A failed fetch never touches the rates already kept.
        if (fresh != null) { store.saveGearRates(fresh); status = "" } else status = (client.lastError ?: "Offline.") + (if (rates != null) " Showing the last rates." else "")
    }
    LaunchedEffect(Unit) { if (rates == null || rates.date < store.today.toString().take(10)) refresh() }

    TextBox {
        if (rates == null) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(status.ifEmpty { "No rates yet. Connect to the internet once to fetch them; they are kept for when you are offline." }, style = MaterialTheme.typography.bodyLarge)
                PixelButton("Fetch rates", { refresh() }, Modifier.fillMaxWidth(), Pixel.Blue)
            }
            return@TextBox
        }
        val codes = remember(rates) { rates.perUsd.keys.toList() }
        var from by remember { mutableStateOf(gear.currencyPair.substringBefore('>', home).ifEmpty { home }.takeIf { it in rates.perUsd } ?: home) }
        var to by remember { mutableStateOf(gear.currencyPair.substringAfter('>', "").ifEmpty { gear.currencyBoard.firstOrNull() ?: "USD" }.takeIf { it in rates.perUsd } ?: "USD") }
        var text by remember { mutableStateOf("1000") }
        var choosing by remember { mutableStateOf<String?>(null) }
        var query by remember { mutableStateOf("") }
        var showCost by remember { mutableStateOf(false) }
        var manualText by remember(to) { mutableStateOf(gear.manualRates[to]?.let { CalcEngine.format(it) } ?: "") }
        val amount = (CalcEngine.evaluate(text.replace(',', '.')) as? CalcResult.Value)?.value
        val rate = Currencies.rate(from, to, rates, home, gear.manualRates)
        val usingManual = (from == home && gear.manualRates[to] != null) || (to == home && gear.manualRates[from] != null)
        val age = runCatching { LocalDate.parse(rates.date).daysUntil(store.today) }.getOrNull()
        fun savePair(f: String, t: String) { store.changeGear { it.copy(currencyPair = "$f>$t") } }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(status.ifEmpty { ratesFreshness(rates, store.nowMillis) }, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            if (age != null && age > 5) Text("These rates are $age days old. Refresh when you are online before you rely on them.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Red)
            PixelField(text, { text = it.take(20); onUse() }, Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), label = "Amount in ${from} (a number or a sum)")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelButton(from, { choosing = if (choosing == "from") null else "from"; query = "" }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                PixelIconButton("repeat", "Swap", { val f = from; from = to; to = f; savePair(from, to); onUse() }, Modifier.weight(1f), Pixel.Blue)
                PixelButton(to, { choosing = if (choosing == "to") null else "to"; query = "" }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
            Text("${Currencies.label(from)} to ${Currencies.label(to)}. Tap a code to change it.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            if (choosing != null) {
                PixelField(query, { query = it.take(24) }, Modifier.fillMaxWidth(), singleLine = true, label = "Search a currency (yen, rmb, peso, usd)")
                Currencies.search(query, codes).take(8).forEach { c ->
                    PixelButton(Currencies.label(c.code), { if (choosing == "from") from = c.code else to = c.code; choosing = null; savePair(from, to); onUse() }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                }
            }
            Text(
                if (amount == null || rate == null) "Type an amount" else "${Currencies.format(amount, from)} = ${Currencies.format(amount * rate, to)}",
                style = MaterialTheme.typography.titleMedium,
            )
            if (rate != null) Text("1 $from = ${CalcEngine.formatSignificant(rate, 6)} $to${if (usingManual) " (your rate)" else " (reference rate)"}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)

            // Your own rate for the home pair: what the changer offered, in units of the other currency per one of the home currency.
            if (from == home || to == home) {
                val other = if (from == home) to else from
                if (other != home) {
                    FieldLabel("A rate you were offered ($other per 1 $home)")
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        PixelField(manualText, { manualText = it.take(14) }, Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), label = "Your rate")
                        PixelButton("Use", { manualText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }?.let { v -> store.changeGear { g -> g.copy(manualRates = g.manualRates + (other to v)) } } }, Modifier.weight(0.5f), Pixel.Blue, compact = true)
                        PixelIconButton("close", "Clear", { manualText = ""; store.changeGear { g -> g.copy(manualRates = g.manualRates - other) } }, Modifier.weight(0.5f), Pixel.InkSoft)
                    }
                }
            }

            FieldLabel("Your board, for ${amount?.let { Currencies.format(it, from) } ?: "an amount"}")
            gear.currencyBoard.filter { it != from && it in rates.perUsd }.forEach { code ->
                val r = Currencies.rate(from, code, rates, home, gear.manualRates)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Text(if (amount == null || r == null) "$code: ?" else Currencies.format(amount * r, code), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    PixelButton("Show", { to = code; savePair(from, to) }, Modifier.weight(0.4f), Pixel.InkSoft, compact = true)
                    PixelIconButton("trash", "Drop", { store.changeGear { g -> g.copy(currencyBoard = g.currencyBoard - code) } }, Modifier.weight(0.4f), Pixel.InkSoft)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelButton("Add $to", { store.changeGear { g -> if (to in g.currencyBoard || to == from) g else g.copy(currencyBoard = (g.currencyBoard + to).takeLast(12)) } }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                PixelButton(if (from == home) "$from is home" else "Make $from home", { store.changeGear { it.copy(homeCurrency = from) } }, Modifier.weight(1f), if (from == home) Pixel.Green else Pixel.InkSoft, compact = true, enabled = from != home)
            }

            PixelButton(if (showCost) "Hide what it really costs" else "What it really costs", { showCost = !showCost }, Modifier.fillMaxWidth(), Pixel.Blue, compact = true)
            if (showCost) {
                var priceText by remember { mutableStateOf("") }
                var percentText by remember { mutableStateOf(if (gear.feePercent > 0) CalcEngine.format(gear.feePercent) else "") }
                var fixedText by remember { mutableStateOf(if (gear.feeFixed > 0) CalcEngine.format(gear.feeFixed) else "") }
                val foreign = to
                Text("A price in $foreign, paid with $home: the reference estimate against the rate you were offered and the fees.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                PixelField(priceText, { priceText = it.take(14) }, Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), label = "Price in $foreign")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PixelField(percentText, { percentText = it.take(8); store.changeGear { g -> g.copy(feePercent = it.replace(',', '.').toDoubleOrNull()?.takeIf { v -> v >= 0 } ?: 0.0) } }, Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), label = "Fee %")
                    PixelField(fixedText, { fixedText = it.take(10); store.changeGear { g -> g.copy(feeFixed = it.replace(',', '.').toDoubleOrNull()?.takeIf { v -> v >= 0 } ?: 0.0) } }, Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), label = "Fixed fee ($home)")
                }
                val price = priceText.replace(',', '.').toDoubleOrNull()
                val reference = Currencies.rate(home, foreign, rates, home, emptyMap())
                val cost = if (price == null || reference == null || foreign == home) null else Travel.estimate(price, reference, gear.manualRates[foreign], gear.feePercent, gear.feeFixed)
                if (foreign == home) Text("Pick the other currency as the one you are paying in.", style = MaterialTheme.typography.bodyMedium)
                else if (cost == null) Text("Type the price to see it.", style = MaterialTheme.typography.bodyMedium)
                else {
                    Text("Reference estimate: ${Currencies.format(cost.reference, home)}", style = MaterialTheme.typography.bodyLarge)
                    Text("At ${if (gear.manualRates[foreign] != null) "your rate" else "the reference rate"}: ${Currencies.format(cost.atQuotedRate, home)}", style = MaterialTheme.typography.bodyMedium)
                    Text("Fees: ${Currencies.format(cost.fees, home)}", style = MaterialTheme.typography.bodyMedium)
                    Text("You pay about ${Currencies.format(cost.total, home)}, ${Currencies.format(cost.extra, home)} (${CalcEngine.format(kotlin.math.round(cost.extraPercent * 100) / 100)}%) more than the reference.", style = MaterialTheme.typography.titleSmall)
                    Text("An estimate: your bank, card or changer has the final rate and fees.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                }
            }
            PixelButton("Refresh rates", { refresh() }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        }
    }
}
