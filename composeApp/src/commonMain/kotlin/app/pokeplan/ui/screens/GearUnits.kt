package app.pokeplan.ui.screens

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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.gear.CalcEngine
import app.pokeplan.ui.gear.CalcResult
import app.pokeplan.ui.gear.UnitCatalog
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox

/**
 * The unit converter: search a unit or a kind of measure by name, symbol or alias (a slip of a letter is forgiven), pick the kind from the chips
 * (all of them wrap, none is hidden by scrolling), type a number or a small sum, swap the two units, see every unit of the kind at once, star a
 * pair, and choose how many decimals to show. A unit that is easy to mistake says so under the result. Nothing is stored but the stars, the last
 * kind and the decimals, on this phone only.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UnitsTool(model: AppModel, onUse: () -> Unit) {
    val store = model.store
    val gear = model.data.gear
    val clipboard = LocalClipboardManager.current
    var query by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf(gear.unitCategory.takeIf { UnitCatalog.category(it) != null } ?: "length") }
    val category = UnitCatalog.category(categoryId)!!
    var fromIndex by remember(categoryId) { mutableStateOf(0) }
    var toIndex by remember(categoryId) { mutableStateOf(1.coerceAtMost(category.units.lastIndex)) }
    var text by remember { mutableStateOf("1") }
    var all by remember { mutableStateOf(false) }
    val from = category.units[fromIndex]
    val to = category.units[toIndex]
    val parsed = (CalcEngine.evaluate(text.replace(',', '.')) as? CalcResult.Value)?.value
    val result = parsed?.let { UnitCatalog.convert(it, from, to) }
    val pairId = "${category.id}:${from.symbol}>${to.symbol}"

    fun choose(id: String, unitSymbol: String? = null) {
        categoryId = id; store.changeGear { it.copy(unitCategory = id) }
        val c = UnitCatalog.category(id)!!
        if (unitSymbol != null) { fromIndex = c.units.indexOfFirst { it.symbol == unitSymbol }.coerceAtLeast(0); toIndex = (if (fromIndex == 0) 1 else 0).coerceAtMost(c.units.lastIndex) }
        query = ""; onUse()
    }

    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelField(query, { query = it.take(30) }, Modifier.fillMaxWidth(), singleLine = true, label = "Search a unit or kind (lb, oven, feet, Mbps)")
            if (query.isNotBlank()) {
                val hits = UnitCatalog.search(query)
                if (hits.isEmpty()) Text("Nothing by that name.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                hits.forEach { hit ->
                    PixelButton(if (hit.unit == null) "${hit.category.label} (kind)" else "${hit.unit.symbol}: ${hit.unit.name}, ${hit.category.label}", { choose(hit.category.id, hit.unit?.symbol) }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                }
            }
            FieldLabel("What to measure")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                UnitCatalog.categories.forEach { c -> TabButton(c.label, c.id == categoryId, { choose(c.id) }) }
            }
            if (category.note.isNotEmpty()) Text(category.note, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            PixelField(text, { text = it.take(30); onUse() }, Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), label = "Amount (or a sum)")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                OptionPicker("From", category.units.map { "${it.symbol} (${it.name})" }, fromIndex, Modifier.weight(1f), short = category.units.map { it.symbol }) { fromIndex = it }
                OptionPicker("To", category.units.map { "${it.symbol} (${it.name})" }, toIndex, Modifier.weight(1f), short = category.units.map { it.symbol }) { toIndex = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelIconButton("repeat", "Swap", { val f = fromIndex; fromIndex = toIndex; toIndex = f; onUse() }, Modifier.weight(1f), Pixel.InkSoft)
                val starred = pairId in gear.unitFavorites
                PixelButton(if (starred) "Starred" else "Star this pair", { store.changeGear { g -> g.copy(unitFavorites = if (starred) g.unitFavorites - pairId else (g.unitFavorites + pairId).takeLast(20)) } }, Modifier.weight(1f), if (starred) Pixel.Green else Pixel.InkSoft, compact = true)
            }
            Text("${from.name} to ${to.name}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            val shown = result?.let { UnitCatalog.show(it, gear.unitDecimals) }
            Text(
                if (shown == null) "Type a number" else "${UnitCatalog.show(parsed!!, gear.unitDecimals)} ${from.symbol} = $shown ${to.symbol}",
                style = MaterialTheme.typography.titleMedium,
            )
            listOf(from, to).distinct().filter { it.note.isNotEmpty() }.forEach { Text("${it.symbol}: ${it.note}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelIconButton("copy", "Copy", { shown?.let { clipboard.setText(AnnotatedString("$it ${to.symbol}")) } }, Modifier.weight(1f), Pixel.InkSoft, enabled = shown != null)
                PixelButton(if (all) "Hide the rest" else "Every unit", { all = !all }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
            if (all && parsed != null) category.units.filter { it != from }.forEach { u ->
                Text("${UnitCatalog.show(UnitCatalog.convert(parsed, from, u), gear.unitDecimals)} ${u.symbol}  (${u.name})", style = MaterialTheme.typography.bodyMedium)
            }
            FieldLabel("Decimals")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                listOf(0, 2, 4, 6, 8).forEach { d -> PixelButton(d.toString(), { store.changeGear { it.copy(unitDecimals = d) } }, Modifier.weight(1f), if (gear.unitDecimals == d) Pixel.Blue else Pixel.InkSoft, compact = true) }
            }
            if (gear.unitFavorites.isNotEmpty()) {
                FieldLabel("Starred pairs")
                gear.unitFavorites.forEach { id ->
                    val cat = UnitCatalog.category(id.substringBefore(':'))
                    val (f, t) = id.substringAfter(':').split('>').let { it.getOrNull(0).orEmpty() to it.getOrNull(1).orEmpty() }
                    val fi = cat?.units?.indexOfFirst { it.symbol == f } ?: -1
                    val ti = cat?.units?.indexOfFirst { it.symbol == t } ?: -1
                    if (cat != null && fi >= 0 && ti >= 0) PixelButton("$f to $t (${cat.label})", { categoryId = cat.id; fromIndex = fi; toIndex = ti }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                }
            }
        }
    }
}
