package app.pokeplan.ui.mint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.pokeplan.mint.BreakdownLine
import app.pokeplan.mint.MintRules
import app.pokeplan.mint.breakdown
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.screens.PixelDropdown
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OverviewBreakdownBox(model: AppModel, startCurrency: String, onClose: () -> Unit) {
    val mint = model.mint
    val data = model.mintData
    val today = model.store.today
    val currencies = MintRules.netPosition(data, today).keys.toList().ifEmpty { listOf(startCurrency) }
    var cur by remember { mutableStateOf(startCurrency) }
    val b = MintRules.breakdown(data, cur, today)
    @Composable
    fun lines(rows: List<BreakdownLine>) = rows.forEach { l ->
        val label = listOf(l.name, l.group.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · ") + if (l.counted) "" else " · not in total"
        MintValueRow(label, mint.show(l.balanceMinor, cur), soft = !l.counted)
    }
    TextBox {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Overview breakdown")
            if (currencies.size > 1) PixelDropdown(currencies, cur, { it ?: "" }, { c -> if (c != null) cur = c }, Modifier.fillMaxWidth(), key = ModuleAccent.MINT.accent, label = "Currency")
            SectionLabel("Net position")
            if (b.assets.isNotEmpty()) { Text("Assets", style = MaterialTheme.typography.titleSmall); lines(b.assets) }
            if (b.liabilities.isNotEmpty()) { Text("Liabilities", style = MaterialTheme.typography.titleSmall); lines(b.liabilities) }
            HorizontalDivider(color = Pixel.Border)
            MintValueRow("Net position", mint.show(b.netMinor, cur))
            SectionLabel("Available money")
            MintValueRow("Liquid accounts", mint.show(b.liquidMinor, cur))
            MintValueRow("Bills due (${b.billsCount})", "−" + mint.show(b.billsMinor, cur), soft = true)
            MintValueRow("Credit bills due (${b.installmentsCount})", "−" + mint.show(b.installmentsMinor, cur), soft = true)
            HorizontalDivider(color = Pixel.Border)
            MintValueRow("Available", mint.show(b.availableMinor, cur))
            if (b.creditLines.isNotEmpty()) {
                SectionLabel("Credit lines")
                b.creditLines.forEach { c ->
                    Text(c.name, style = MaterialTheme.typography.titleSmall)
                    MintValueRow("Limit", mint.show(c.limitMinor, cur), soft = true)
                    MintValueRow("Owed", mint.show(c.owedMinor, cur), soft = true)
                    MintValueRow("Left", mint.show(c.leftMinor, cur), soft = true)
                }
            }
            PixelButton("Close", onClose, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, compact = true)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OverviewCountsBox(model: AppModel) {
    val mint = model.mint
    val settings = model.mintData.settings
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            PixelTitle("Overview counts")
            PixelDropdown(app.pokeplan.mint.OverviewWindow.entries.toList(), settings.overviewWindow, { it?.label ?: "" }, { w -> if (w != null) mint.setOverviewWindow(w) }, Modifier.fillMaxWidth(), key = ModuleAccent.MINT.accent, label = "Bills and installments due")
            PixelDropdown(app.pokeplan.mint.PayLaterCount.entries.toList(), settings.overviewPayLater, { it?.label ?: "" }, { c -> if (c != null) mint.setOverviewPayLater(c) }, Modifier.fillMaxWidth(), key = ModuleAccent.MINT.accent, label = "Pay later in net position")
        }
    }
}
