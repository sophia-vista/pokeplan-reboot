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
 * The currency converter: a row for each currency you watch (your home currency first, the Philippine peso until you change it), each one an
 * amount you can type in, so typing in any row works out all the others; add a currency by name or code, move or remove one, a rate of your own (what a money changer offered) and a "what it really costs" panel that keeps the reference rate, the
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
        val rows = Currencies.rows(home, gear.currencyBoard, rates.perUsd.keys)
        // One amount is being typed, in one currency: every other row is worked out from it (so rounding never drifts between rows).
        var active by remember { mutableStateOf(rows.firstOrNull() ?: home) }
        var text by remember { mutableStateOf("1000") }
        var adding by remember { mutableStateOf(false) }
        var query by remember { mutableStateOf("") }
        var showCost by remember { mutableStateOf(false) }
        if (active !in rows && rows.isNotEmpty()) active = rows.first()
        val amount = (CalcEngine.evaluate(text.replace(',', '.')) as? CalcResult.Value)?.value
        // The foreign currency for the fees and your own rate: the row being typed in, or else the first that is not home.
        val foreign = active.takeIf { it != home } ?: rows.firstOrNull { it != home } ?: active
        var manualText by remember(foreign) { mutableStateOf(gear.manualRates[foreign]?.let { CalcEngine.format(it) } ?: "") }
        val age = runCatching { LocalDate.parse(rates.date).daysUntil(store.today) }.getOrNull()
        fun saveBoard(next: List<String>) { store.changeGear { it.copy(currencyBoard = next.filter { c -> c != it.homeCurrency }.distinct().take(12)) } }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(status.ifEmpty { ratesFreshness(rates, store.nowMillis) }, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            if (age != null && age > 5) Text("These rates are $age days old. Refresh when you are online before you rely on them.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Red)

            rows.forEachIndexed { i, code ->
                val r = Currencies.rate(active, code, rates, home, gear.manualRates)
                val shown = if (code == active) text else if (amount == null || r == null) "" else Currencies.plain(amount * r, code)
                // The currency's name sits over its row; the amount and its buttons share one centre line.
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    Text(Currencies.label(code) + if (code == home) " (home)" else "", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        PixelField(
                            shown, { active = code; text = it.take(20); onUse() }, Modifier.weight(1f), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        )
                        if (i > 0 && code != home) PixelIconButton("arrow-up", "Move $code up", { saveBoard(rows.filter { it != home }.toMutableList().also { l -> val at = l.indexOf(code); if (at > 0) { l.removeAt(at); l.add(at - 1, code) } }) }, Pixel.InkSoft)
                        if (code != home) PixelIconButton("trash", "Remove $code", { saveBoard(rows.filter { it != home && it != code }) }, Pixel.InkSoft)
                    }
                }
            }
            if (foreign != home) Currencies.rate(home, foreign, rates, home, gear.manualRates)?.let { r ->
                Text("1 $home = ${CalcEngine.formatSignificant(r, 6)} $foreign${if (gear.manualRates[foreign] != null) " (your rate)" else " (reference rate)"}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                PixelButton(if (adding) "Cancel" else "Add a currency", { adding = !adding; query = "" }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                PixelButton(if (active == home) "$active is home" else "Make $active home", { store.changeGear { it.copy(homeCurrency = active) } }, Modifier.weight(1f), if (active == home) Pixel.Green else Pixel.InkSoft, compact = true, enabled = active != home)
            }
            if (adding) {
                PixelField(query, { query = it.take(24) }, Modifier.fillMaxWidth(), singleLine = true, label = "Search a currency (yen, rmb, peso, usd)")
                Currencies.search(query, codes).filter { it.code !in rows }.take(8).forEach { c ->
                    PixelButton(Currencies.label(c.code), { saveBoard(gear.currencyBoard + c.code); active = c.code; adding = false; onUse() }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                }
            }

            // Your own rate for the home pair: what the changer offered, in units of the other currency per one of the home currency.
            if (foreign != home) {
                FieldLabel("A rate you were offered ($foreign per 1 $home)")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    PixelField(manualText, { manualText = it.take(14) }, Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), placeholder = "Your rate")
                    PixelButton("Use", { manualText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }?.let { v -> store.changeGear { g -> g.copy(manualRates = g.manualRates + (foreign to v)) } } }, Modifier.weight(0.5f), Pixel.Blue, compact = true)
                    PixelIconButton("close", "Clear", { manualText = ""; store.changeGear { g -> g.copy(manualRates = g.manualRates - foreign) } }, Pixel.InkSoft)
                }
            }

            PixelButton(if (showCost) "Hide what it really costs" else "What it really costs", { showCost = !showCost }, Modifier.fillMaxWidth(), Pixel.Blue, compact = true)
            if (showCost) {
                var priceText by remember { mutableStateOf("") }
                var percentText by remember { mutableStateOf(if (gear.feePercent > 0) CalcEngine.format(gear.feePercent) else "") }
                var fixedText by remember { mutableStateOf(if (gear.feeFixed > 0) CalcEngine.format(gear.feeFixed) else "") }
                PixelField(priceText, { priceText = it.take(14) }, Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), label = "Price in $foreign")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
                }
            }
            PixelButton("Refresh rates", { refresh() }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        }
    }
}
