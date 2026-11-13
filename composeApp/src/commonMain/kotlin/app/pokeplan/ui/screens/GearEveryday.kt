package app.pokeplan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import kotlin.math.roundToLong
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.gear.Everyday
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox

/** What a number field says when its text is not a number. */
internal const val NOT_A_NUMBER = "Enter a number, like 12.5."

private enum class Sum(val label: String) { TIP("Tip and split"), PERCENT("Percent"), SALE("Sale and tax"), LOAN("Loan"), SAVINGS("Savings"), TRIP("Trip cost") }

/** Everyday sums: each a few numbers in and a plain answer out, nothing kept. A comma works as the decimal point. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EverydayTool(model: AppModel, onUse: () -> Unit) {
    var sum by remember { mutableStateOf(Sum.TIP) }
    val inputs = remember(sum) { mutableStateOf(List(4) { "" }) }
    var values by inputs
    fun num(i: Int): Double? = values[i].trim().replace(',', '.').toDoubleOrNull()
    // Text that is not a number is said so under its field and gives no answer; it is never read as 0 or as the field's default.
    fun bad(i: Int): Boolean = values[i].isNotBlank() && num(i) == null
    @Composable
    fun field(i: Int, label: String, hint: String? = null) {
        PixelField(values[i], { v -> values = values.toMutableList().also { it[i] = v.take(14) }; onUse() }, Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), label = label, placeholder = hint, error = if (bad(i)) NOT_A_NUMBER else null)
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Sum.entries.forEach { s -> TabButton(s.label, sum == s, { sum = s }) }
            }
            val answer: List<String>? = if (values.indices.any(::bad)) null else when (sum) {
                Sum.TIP -> {
                    field(0, "Bill"); field(1, "Tip percent", "15"); field(2, "People", "1")
                    val people = num(2)?.toInt() ?: 1
                    num(0)?.let { b -> Everyday.tipSplit(b, num(1) ?: 15.0, people)?.let { listOf("Tip ${Everyday.money(it.tip)}", "Total ${Everyday.money(it.total)}", "Each pays ${Everyday.money(it.each)}") } }
                }
                Sum.PERCENT -> {
                    field(0, "Number A"); field(1, "Number B")
                    val a = num(0); val b = num(1)
                    if (a == null || b == null) null else listOfNotNull(
                        Everyday.percentOf(a, b)?.let { "${trim(a)}% of ${trim(b)} is ${trim(it)}" },
                        Everyday.whatPercent(a, b)?.let { "${trim(a)} is ${trim(it)}% of ${trim(b)}" },
                        Everyday.percentChange(a, b)?.let { "From ${trim(a)} to ${trim(b)} is a change of ${trim(it)}%" }, // copy-ok: result
                    )
                }
                Sum.SALE -> {
                    field(0, "Price"); field(1, "Percent off"); field(2, "Tax percent", "0")
                    num(0)?.let { p -> Everyday.sale(p, num(1) ?: 0.0, num(2) ?: 0.0)?.let { listOf("You save ${Everyday.money(it.saved)}", "After the discount ${Everyday.money(it.afterDiscount)}", "Tax ${Everyday.money(it.tax)}", "Total ${Everyday.money(it.total)}") } }
                }
                Sum.LOAN -> {
                    field(0, "Amount borrowed"); field(1, "Yearly interest percent"); field(2, "Years")
                    val p = num(0); val i = num(1); val y = num(2)
                    if (p == null || i == null || y == null) null else Everyday.loan(p, i, y)?.let { listOf("Each month ${Everyday.money(it.monthly)}", "Paid in all ${Everyday.money(it.totalPaid)}", "Of which interest ${Everyday.money(it.interest)}") }
                }
                Sum.SAVINGS -> {
                    field(0, "Start with"); field(1, "Yearly interest percent"); field(2, "Years"); field(3, "Add each month", "0")
                    val s = num(0); val i = num(1); val y = num(2)
                    if (s == null || i == null || y == null) null else Everyday.savings(s, i, y, 12, num(3) ?: 0.0)?.let { listOf("After ${trim(y)} years ${Everyday.money(it)}") }
                }
                Sum.TRIP -> {
                    field(0, "Distance"); field(1, "Distance per litre or gallon"); field(2, "Price of one litre or gallon")
                    val d = num(0); val e = num(1); val p = num(2)
                    if (d == null || e == null || p == null) null else Everyday.tripCost(d, e, p)?.let { listOf("The trip costs ${Everyday.money(it)}") }
                }
            }
            answer?.forEach { Text(it, style = MaterialTheme.typography.titleSmall) }
        }
    }
}

/** A number without trailing zeros, at most two decimals ("12.5", "3"). */
private fun trim(x: Double): String {
    val c = (x * 100).roundToLong() / 100.0
    return if (c == c.toLong().toDouble()) c.toLong().toString() else c.toString()
}
