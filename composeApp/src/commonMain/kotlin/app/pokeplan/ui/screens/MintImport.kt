package app.pokeplan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.pokeplan.app.Integrations
import app.pokeplan.mint.AccountStatus
import app.pokeplan.mint.Money
import app.pokeplan.mint.StatementImport
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Report
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.Notice
import app.pokeplan.ui.theme.NoticeTone
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.theme.ToggleRow
import app.pokeplan.ui.theme.ModuleAccent

/**
 * Mint → Import (INTEGRATIONS_AND_PRIVACY.md sections 7 and 14, MINT.md section 10): a statement file the player chooses, a preview with the columns they can correct,
 * rows kept or left out, and each import removable on its own. Below it, the honest status of every provider: none is connected.
 */
@Composable
internal fun MintImportView(model: AppModel) {
    val mint = model.mint
    val chooser = model.textFiles
    val data = model.mintData
    val accounts = data.accounts.filter { it.status == AccountStatus.ACTIVE }
    var accountId by remember { mutableStateOf<String?>(null) }
    var fileName by remember { mutableStateOf("") }
    var text by remember { mutableStateOf<String?>(null) }
    var columns by remember { mutableStateOf<StatementImport.Columns?>(null) }
    var order by remember { mutableStateOf<StatementImport.DateOrder?>(null) }
    var flipped by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var note by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf<String?>(null) }
    val chosen = accounts.firstOrNull { it.id == accountId } ?: accounts.singleOrNull()

    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Import a statement")
            if (chooser == null) Text("Choosing a file is not available on this device.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            else if (accounts.isEmpty()) Text("Add an open account first. Imported rows go into one account.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            else {
                Text("Pick the account, then a CSV file from your bank or e-wallet app. The file is read on this phone, once, and not kept. Mint never asks for a password, PIN or code.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                accounts.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        pair.forEach { a -> TabButton(a.name, chosen?.id == a.id, { accountId = a.id }, Modifier.weight(1f), key = ModuleAccent.MINT.accent) }
                        if (pair.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                }
                PixelButton(if (text == null) "Choose a file" else "Choose another file", {
                    chooser.choose { name, body, problem ->
                        if (body == null) note = problem ?: "That file could not be read."
                        else { text = body; fileName = name.orEmpty(); columns = null; order = null; flipped = emptySet(); note = null }
                    }
                }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, enabled = chosen != null, compact = true)
            }
            note?.let { Report(model, it) }
        }
    }

    val body = text
    if (body != null && chosen != null) {
        val read = remember(body, chosen.id, columns, order, data.transactions.size) { mint.previewStatement(body, chosen.id, columns, order) }
        if (read.refused != null) {
            Report(model, read.refused!!, tone = NoticeTone.WARNING)
        } else {
            val header = read.table.first()
            val cols = read.columns
            TextBox {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                    PixelTitle("Check the columns")
                    Picker("Date", header, cols.date) { columns = cols.copy(date = it) }
                    Picker("What it was", header, cols.description) { columns = cols.copy(description = it) }
                    if (cols.debit == null && cols.credit == null) {
                        Picker("Amount", header, cols.amount) { columns = cols.copy(amount = it) }
                        ToggleRow("Charges are shown as plus", cols.flip, detail = "Turn on for a card statement that lists purchases as positive amounts.") { columns = cols.copy(flip = it) }
                        PixelButton("It has separate money out and money in columns", { columns = cols.copy(amount = null, debit = 0.coerceAtMost(header.lastIndex), credit = 1.coerceAtMost(header.lastIndex)) }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                    } else {
                        Picker("Money out", header, cols.debit) { columns = cols.copy(debit = it) }
                        Picker("Money in", header, cols.credit) { columns = cols.copy(credit = it) }
                        PixelButton("It has one amount column", { columns = cols.copy(debit = null, credit = null, amount = StatementImport.guess(header).amount ?: 0.coerceAtMost(header.lastIndex)) }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                    }
                    Picker("Bank's reference (optional)", header, cols.reference) { columns = cols.copy(reference = it) }
                    Text("Dates are written:", style = MaterialTheme.typography.bodyLarge)
                    StatementImport.DateOrder.entries.forEach { o ->
                        TabButton("${o.label}, like ${o.example}", read.order == o, { order = o }, Modifier.fillMaxWidth(), key = ModuleAccent.MINT.accent)
                    }
                    if (!read.orderCertain) Notice("This file's dates could be read either way. Check the preview below before you add anything.", tone = NoticeTone.WARNING)
                }
            }
            val preview = read.preview
            if (preview == null) Notice("Choose which column is the date and which is the amount.", tone = NoticeTone.WARNING)
            else {
                val keep = preview.rows.filter { it.state != StatementImport.RowState.PROBLEM && it.state != StatementImport.RowState.DUPLICATE && (it.keptByDefault != (it.line in flipped)) }.map { it.line }.toSet()
                TextBox {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                        PixelTitle("Preview")
                        Text(
                            "${preview.new} new, ${preview.duplicates} already added, ${preview.possible} may match a record you wrote, ${preview.problems} unreadable." +
                                (if (preview.firstDay != null) " ${preview.firstDay} to ${preview.lastDay}." else ""),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (preview.possible > 0) Text("A row that may match one of yours is left out unless you tap it. Mint never merges or deletes your own records.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                        preview.rows.take(60).forEach { r ->
                            val included = r.line in keep
                            val tag = when (r.state) {
                                StatementImport.RowState.NEW -> if (included) "Add" else "Left out"
                                StatementImport.RowState.DUPLICATE -> "Already added"
                                StatementImport.RowState.POSSIBLE -> if (included) "Add anyway" else "May match yours"
                                StatementImport.RowState.PROBLEM -> "Line ${r.line}"
                            }
                            val togglable = r.state == StatementImport.RowState.NEW || r.state == StatementImport.RowState.POSSIBLE
                            ListRow(onClick = if (togglable) ({ flipped = if (r.line in flipped) flipped - r.line else flipped + r.line }) else null) {
                                if (r.state == StatementImport.RowState.PROBLEM) Text(r.problem, style = MaterialTheme.typography.bodyLarge)
                                else {
                                    Text(r.description.ifBlank { "No description" }, style = MaterialTheme.typography.bodyLarge)
                                    Text("${r.day}  ·  ${model.mint.show(r.signedMinor, chosen.currency)}  ·  $tag", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                                }
                            }
                        }
                        if (preview.rows.size > 60) Text("…and ${preview.rows.size - 60} more rows, handled the same way.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                        Text("Imported records are marked as imported, start unreviewed, and never earn a find. Reconciling still uses the records you wrote.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                            PixelButton(if (keep.isEmpty()) "Nothing to add" else "Add ${keep.size}", {
                                note = mint.importStatement(preview, keep, fileName.ifBlank { "statement.csv" }).note
                                text = null
                            }, Modifier.weight(1f), ModuleAccent.MINT.accent, enabled = keep.isNotEmpty(), compact = true)
                            PixelButton("Cancel", { text = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                        }
                    }
                }
            }
        }
    }

    val batches = mint.importBatches()
    if (batches.isNotEmpty()) {
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                PixelTitle("Earlier imports")
                batches.forEach { b ->
                    val account = data.accounts.firstOrNull { it.id == b.accountId }?.name.orEmpty()
                    ListRow {
                        Text(b.label.ifBlank { "Statement" }, style = MaterialTheme.typography.bodyLarge)
                        Text("${b.importedMillis.shortDate()}  ·  $account  ·  ${b.count} records" + (if (b.unreviewed > 0) ", ${b.unreviewed} unreviewed" else ""), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    }
                    if (asking == b.id) {
                        Text("Delete the ${b.count} records of this import? Records you wrote stay. This cannot be undone.", style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                            PixelButton("Delete them", { mint.removeImportBatch(b.id); asking = null }, Modifier.weight(1f), Pixel.Danger, compact = true)
                            PixelButton("Keep them", { asking = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                        }
                    } else Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                        if (b.unreviewed > 0) PixelButton("Mark reviewed", { mint.markBatchReviewed(b.id) }, Modifier.weight(1f), ModuleAccent.MINT.accent, compact = true)
                        PixelButton("Delete this import", { asking = b.id }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                }
            }
        }
    }

    Integrations.mint().forEach { IntegrationCard(it) }
}

/** Cycles through the file's columns, then "none". */
@Composable
private fun Picker(label: String, header: List<String>, value: Int?, onChange: (Int?) -> Unit) {
    val shown = value?.let { header.getOrNull(it)?.ifBlank { "Column ${it + 1}" } } ?: "none"
    PixelButton("$label: $shown", { onChange(when { value == null -> 0; value + 1 > header.lastIndex -> null; else -> value + 1 }) }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
}
