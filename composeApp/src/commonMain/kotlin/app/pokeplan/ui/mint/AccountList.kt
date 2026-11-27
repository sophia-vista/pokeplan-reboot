package app.pokeplan.ui.mint

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pokeplan.mint.AccountStatus
import app.pokeplan.mint.AccountBalance
import app.pokeplan.mint.MintRules
import app.pokeplan.ui.AppLauncher
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox

private class AccountGroup(val name: String, val rows: List<AccountBalance>)

private fun groupsOf(active: List<AccountBalance>): List<AccountGroup> =
    active.groupBy { it.account.group.trim() }.map { (g, rows) -> AccountGroup(g, rows) }

@Composable
internal fun AccountList(model: AppModel, open: String?, startArranging: Boolean = false, setOpen: (String?) -> Unit) {
    val mint = model.mint
    val data = model.mintData
    // The ledger is re-walked on every local state change (opening a row, Arrange); remember it until the records change.
    val balances = remember(data.accounts, data.transactions) { MintRules.balances(data) }
    val active = balances.filter { it.account.status == AccountStatus.ACTIVE }
    val rest = balances.filter { it.account.status != AccountStatus.ACTIVE }
    if (active.isEmpty() && rest.isEmpty()) return
    var arranging by remember { mutableStateOf(startArranging) }
    val groups = groupsOf(active)
    val headed = active.any { it.account.group.isNotBlank() }
    val mode = arranging && balances.size >= 2
    fun apply(next: List<AccountGroup>) { mint.arrangeAccounts(next.flatMap { g -> g.rows.map { it.account.id } }) }
    fun moveGroup(i: Int, by: Int) { apply(groups.toMutableList().also { it.add(i + by, it.removeAt(i)) }) }
    fun moveRow(gi: Int, ri: Int, by: Int) {
        apply(groups.mapIndexed { k, g -> if (k != gi) g else AccountGroup(g.name, g.rows.toMutableList().also { it.add(ri + by, it.removeAt(ri)) }) })
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        if (balances.size >= 2) PixelButton(if (mode) "Done" else "Arrange", { arranging = !arranging }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        TextBox {
            Column {
                var first = true
                groups.forEachIndexed { gi, g ->
                    if (headed) {
                        if (!first) HorizontalDivider(color = Pixel.Border)
                        first = false
                        GroupHeading(model, g, mode, gi > 0, gi < groups.lastIndex, { moveGroup(gi, -1) }, { moveGroup(gi, 1) })
                    }
                    g.rows.forEachIndexed { ri, b ->
                        if (!first) HorizontalDivider(color = Pixel.Border)
                        first = false
                        AccountRow(model, b, open, setOpen, mode, ri > 0, ri < g.rows.lastIndex, { moveRow(gi, ri, -1) }, { moveRow(gi, ri, 1) })
                    }
                }
                rest.forEach { b ->
                    if (!first) HorizontalDivider(color = Pixel.Border)
                    first = false
                    AccountRow(model, b, open, setOpen, false, false, false, {}, {})
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupHeading(model: AppModel, g: AccountGroup, arranging: Boolean, canUp: Boolean, canDown: Boolean, up: () -> Unit, down: () -> Unit) {
    val mint = model.mint
    val sums = linkedMapOf<String, Long>()
    g.rows.filter { !it.account.hideFromTotal }.forEach { b ->
        sums[b.account.currency] = (sums[b.account.currency] ?: 0L) + if (b.account.type.liability) -b.postedMinor else b.postedMinor
    }
    val subtotal = sums.entries.joinToString(" · ") { (c, m) -> mint.show(m, c) }
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.Target).padding(top = Spacing.S),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.Center, itemVerticalAlignment = Alignment.CenterVertically) {
            FieldLabel(g.name.ifBlank { "Other" })
            if (subtotal.isNotEmpty()) Text(subtotal, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        if (arranging) {
            PixelIconButton("arrow-up", "Move ${g.name.ifBlank { "Other" }} group up", up, Pixel.InkSoft, enabled = canUp)
            PixelIconButton("arrow-down", "Move ${g.name.ifBlank { "Other" }} group down", down, Pixel.InkSoft, enabled = canDown)
        }
    }
}

@Composable
private fun AccountRow(
    model: AppModel, b: AccountBalance, open: String?, setOpen: (String?) -> Unit,
    arranging: Boolean, canUp: Boolean, canDown: Boolean, up: () -> Unit, down: () -> Unit,
) {
    val mint = model.mint
    val a = b.account
    MintLine(
        trail = {
            if (arranging) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelIconButton("arrow-up", "Move ${a.name} up", up, Pixel.InkSoft, enabled = canUp)
                PixelIconButton("arrow-down", "Move ${a.name} down", down, Pixel.InkSoft, enabled = canDown)
            } else Text(mint.show(b.postedMinor, a.currency), style = MaterialTheme.typography.titleSmall)
        },
        onClick = if (arranging) null else ({ setOpen(if (open == a.id) null else a.id) }), onClickLabel = "Show options",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Box(Modifier.size(10.dp).background(accountColor(a.card)))
            Text(a.name, style = MaterialTheme.typography.bodyLarge)
        }
        val meta = listOfNotNull(
            (if (a.suffix.isNotEmpty()) "···${a.suffix}" else a.type.label) + (MintRules.leftToSpend(a, b.postedMinor)?.let { " · ${mint.show(it, a.currency)} left" } ?: ""),
            a.status.name.lowercase().takeIf { a.status != AccountStatus.ACTIVE },
            "pending ${mint.show(b.pendingMinor, a.currency)}".takeIf { b.pendingMinor != 0L },
            "not in total".takeIf { a.hideFromTotal },
        ).joinToString(" · ")
        Text(meta, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        val fresh = mint.accountFreshness(a.id)
        Text(
            buildList {
                fresh?.lastActivity?.let { add("moved " + agoText(it, model.store.today)) }
                add("checked " + (fresh?.lastChecked?.let { agoText(it, model.store.today) } ?: "never"))
            }.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
        )
    }
    if (open == a.id && !arranging) AccountOptions(model, a) { setOpen(null) }
}

@Composable
internal fun AppPick(launcher: AppLauncher, selected: String, onPick: (String) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    if (choosing) AppChooser(launcher, { choosing = false }) { onPick(it); choosing = false }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        FieldLabel("Open app")
        if (selected.isEmpty()) PixelButton("Choose app", { choosing = true }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        else {
            Text(launcher.label(selected) ?: selected, style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelButton("Change", { choosing = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                PixelButton("Remove", { onPick("") }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
        }
    }
}

@Composable
internal fun AppChooser(launcher: AppLauncher, onClose: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val apps = remember { launcher.installed().sortedBy { it.label.lowercase() } }
    val found = apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().padding(Spacing.L), contentAlignment = Alignment.Center) {
            TextBox {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PixelField(query, { query = it.take(40) }, Modifier.fillMaxWidth(), label = "Search apps", singleLine = true)
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(found, key = { it.packageName }) { app ->
                            HorizontalDivider(color = Pixel.Border)
                            Text(
                                app.label, Modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.Target).clickable(role = Role.Button) { onPick(app.packageName) }.padding(vertical = Spacing.S),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                        PixelButton("None", { onPick("") }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                        PixelButton("Cancel", onClose, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                }
            }
        }
    }
}
