package app.pokeplan.ui.mint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import app.pokeplan.mint.Money
import app.pokeplan.mint.SharedBalance
import app.pokeplan.mint.SharedEntry
import app.pokeplan.mint.SharedKind
import app.pokeplan.mint.SharedRules
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.plan.EmptyState
import app.pokeplan.ui.screens.FormBox
import app.pokeplan.ui.screens.FormStack
import app.pokeplan.ui.screens.PixelChoice
import app.pokeplan.ui.screens.PixelDateField
import app.pokeplan.ui.screens.short
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox

/** Bills split with other people (MINT.md 5.8A): who owes whom, a new entry, and the history. Display only: no account, transaction or reward. */
@Composable
internal fun SharedView(model: AppModel) {
    val mint = model.mint
    val data = model.mintData
    val balances = SharedRules.balances(data.shared)
    val currency = data.settings.currency
    if (balances.isNotEmpty()) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            MintValueRow("Owed to me", mint.show(SharedRules.owedToMe(balances, currency), currency))
            MintValueRow("I owe", mint.show(SharedRules.iOwe(balances, currency), currency))
        }
    }
    balances.forEach { b -> BalanceRow(model, b) }
    NewEntry(model)
    if (data.shared.isEmpty()) EmptyState("No shared bills yet", icon = "oaks-letter")
    else TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("History")
            data.shared.sortedWith(compareByDescending<SharedEntry> { it.day }.thenByDescending { it.id }).forEach { e ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Column(Modifier.weight(1f)) {
                        Text("${e.person}, ${e.kind.label.lowercase()}", style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text(listOfNotNull(e.day.short(), e.note.takeIf { it.isNotBlank() }).joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, maxLines = 1)
                    }
                    Text(mint.show(e.amountMinor, e.currency), style = MaterialTheme.typography.bodyLarge)
                    PixelIconButton("trash", "Delete the entry with ${e.person}", { mint.deleteShared(e.id) }, Pixel.InkSoft)
                }
            }
        }
    }
}

@Composable
private fun BalanceRow(model: AppModel, b: SharedBalance) {
    TextBox {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(
                if (b.minor > 0) "${b.person} owes me" else "I owe ${b.person}",
                Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1,
            )
            Text(model.mint.show(kotlin.math.abs(b.minor), b.currency), style = MaterialTheme.typography.titleSmall, color = if (b.minor > 0) Pixel.Green else Pixel.Ink)
            PixelButton("Settle up", { model.mint.settleShared(b, model.store.today) }, color = ModuleAccent.MINT.accent, compact = true)
        }
    }
}

@Composable
private fun NewEntry(model: AppModel) {
    val mint = model.mint
    val currency = model.mintData.settings.currency
    var open by remember { mutableStateOf(false) }
    if (!open) {
        PixelButton("New entry", { open = true }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent)
        return
    }
    var person by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(SharedKind.I_PAID) }
    var amount by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(model.store.today) }
    var note by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    val known = SharedRules.balances(model.mintData.shared).map { it.person }
    FormBox {
        FormStack {
            PixelField(
                person, { person = it.take(SharedRules.PERSON_MAX) }, Modifier.fillMaxWidth(), label = "Who", singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            PixelChoice(SharedKind.entries.toList(), kind, { it?.label ?: "" }, { if (it != null) kind = it }, label = "What happened", key = ModuleAccent.MINT.accent)
            PixelField(
                amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16) }, Modifier.fillMaxWidth(), label = when (kind) { SharedKind.I_PAID -> "Their share"; SharedKind.THEY_PAID -> "My share"; else -> "Amount" },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), large = true, prefix = currency, placeholder = "0.00",
            )
            PixelDateField(day, model.store.today, ModuleAccent.MINT.accent, { it?.let { d -> day = d } }, max = model.store.today)
            PixelField(note, { note = it.take(SharedRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Note", singleLine = true)
            problem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.Danger) }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Keep it", {
                    val minor = Money.parse(amount, currency)
                    problem = if (minor == null) "Type the amount." else mint.addShared(person, kind, minor, day, note, currency)
                    if (problem == null) open = false
                }, Modifier.weight(1f), ModuleAccent.MINT.accent, compact = true)
                PixelButton("Cancel", { open = false }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        }
    }
}
