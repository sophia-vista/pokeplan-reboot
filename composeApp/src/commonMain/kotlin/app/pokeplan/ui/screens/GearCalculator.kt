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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import app.pokeplan.app.GearData
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.gear.CalcEngine
import app.pokeplan.ui.gear.CalcResult
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox

/**
 * The Pokégear calculator: one expression with the usual order of operations (`2 + 3 × 4` is 14), brackets, percentages, a scientific panel,
 * a memory slot and an optional history. The expression is a text field, so a number or a whole sum can be pasted, and the keys add to it.
 * The result is shown as you type; an error is shown only after `=` (and says what is wrong). The history is off until the player turns it on
 * and stays on this device. Nothing here pays or records anything else.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CalculatorTool(model: AppModel, onUse: () -> Unit) {
    val store = model.store
    val gear = model.data.gear
    var expr by remember { mutableStateOf("") }
    var answered by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var scientific by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val live = if (expr.isBlank()) null else CalcEngine.evaluate(expr, gear.calcDegrees)
    val liveValue = (live as? CalcResult.Value)

    fun edit(text: String) { expr = text.take(200); answered = false; showError = false; onUse() }
    // After an answer, a digit or function starts a new sum, while an operator carries on from the answer.
    fun key(token: String, startsNew: Boolean = false) {
        expr = (if (answered && startsNew) "" else expr) + token
        answered = false; showError = false; onUse()
    }
    fun equals() {
        if (expr.isBlank()) return
        when (val r = CalcEngine.evaluate(expr, gear.calcDegrees)) {
            is CalcResult.Value -> {
                if (gear.calcHistoryOn) store.changeGear { g -> g.copy(calcHistory = (listOf("$expr = ${r.text}") + g.calcHistory).take(GearData.MAX_HISTORY)) }
                expr = r.text; answered = true; showError = false
            }
            is CalcResult.Error -> showError = true
        }
        onUse()
    }

    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelField(expr, { edit(it) }, Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), label = "Sum")
            Text(
                when {
                    live is CalcResult.Error && showError -> live.message
                    liveValue != null && !answered -> "= ${liveValue.text}"
                    answered -> "Answer"
                    else -> "Type a sum, for example 2 + 3 × 4"
                },
                style = MaterialTheme.typography.titleMedium, color = if (live is CalcResult.Error && showError) Pixel.Red else Pixel.Ink,
            )
            @Composable fun keys(vararg labels: Pair<String, () -> Unit>, accent: Set<String> = emptySet()) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                labels.forEach { (label, action) -> PixelButton(label, action, Modifier.weight(1f), if (label in accent) Pixel.Blue else Pixel.InkSoft, compact = true) }
            }
            val ops = setOf("÷", "×", "−", "+", "=")
            keys("C" to { expr = ""; answered = false; showError = false }, "Del" to { edit(expr.dropLast(1)) }, "(" to { key("(", true) }, ")" to { key(")") }, accent = ops)
            keys("7" to { key("7", true) }, "8" to { key("8", true) }, "9" to { key("9", true) }, "÷" to { key("÷") }, accent = ops)
            keys("4" to { key("4", true) }, "5" to { key("5", true) }, "6" to { key("6", true) }, "×" to { key("×") }, accent = ops)
            keys("1" to { key("1", true) }, "2" to { key("2", true) }, "3" to { key("3", true) }, "−" to { key("−") }, accent = ops)
            keys("0" to { key("0", true) }, "." to { key(".", true) }, "%" to { key("%") }, "+" to { key("+") }, accent = ops)
            keys("Sci" to { scientific = !scientific }, "^" to { key("^") }, "Ans" to { liveValue?.let { key(it.text, true) } }, "=" to { equals() }, accent = ops)
            if (scientific) {
                keys("sin" to { key("sin(", true) }, "cos" to { key("cos(", true) }, "tan" to { key("tan(", true) }, if (gear.calcDegrees) "Deg" to { store.changeGear { it.copy(calcDegrees = false) } } else "Rad" to { store.changeGear { it.copy(calcDegrees = true) } })
                keys("asin" to { key("asin(", true) }, "acos" to { key("acos(", true) }, "atan" to { key("atan(", true) }, "!" to { key("!") })
                keys("ln" to { key("ln(", true) }, "log" to { key("log(", true) }, "sqrt" to { key("sqrt(", true) }, "π" to { key("π", true) })
                keys("M+" to { liveValue?.let { v -> store.changeGear { it.copy(calcMemory = it.calcMemory + v.value) } } }, "M−" to { liveValue?.let { v -> store.changeGear { it.copy(calcMemory = it.calcMemory - v.value) } } }, "MR" to { key(CalcEngine.format(gear.calcMemory), true) }, "MC" to { store.changeGear { it.copy(calcMemory = 0.0) } })
                Text("Angles in ${if (gear.calcDegrees) "degrees" else "radians"}. Memory: ${CalcEngine.format(gear.calcMemory)}.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelIconButton("copy", "Copy", { clipboard.setText(AnnotatedString(liveValue?.text ?: expr)) }, Pixel.InkSoft, enabled = expr.isNotBlank())
                PixelButton(if (gear.calcHistoryOn) "History on" else "History off", { store.changeGear { it.copy(calcHistoryOn = !it.calcHistoryOn) } }, Modifier.weight(1f), if (gear.calcHistoryOn) Pixel.Green else Pixel.InkSoft, compact = true)
            }
            Text(if (gear.calcHistoryOn) "The last sums are kept on this phone only." else "Nothing is kept unless you turn the history on.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            if (gear.calcHistoryOn && gear.calcHistory.isNotEmpty()) {
                FieldLabel("History (tap one to use it)")
                gear.calcHistory.take(8).forEach { line ->
                    PixelButton(line, { expr = line.substringBefore(" = "); answered = false; showError = false }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                }
                PixelButton("Clear the history", { store.changeGear { it.copy(calcHistory = emptyList()) } }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            }
        }
    }
}
