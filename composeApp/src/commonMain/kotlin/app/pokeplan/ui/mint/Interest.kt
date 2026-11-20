package app.pokeplan.ui.mint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import app.pokeplan.mint.AccountType
import app.pokeplan.mint.EarnSchedule
import app.pokeplan.mint.Money
import app.pokeplan.mint.Transaction
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Report
import app.pokeplan.ui.screens.PixelDropdown
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.theme.NoticeTone
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.theme.ToggleRow

/** Whether an account of [type] can earn interest (MINT.md 4.3E): a deposit account, not cash and not a liability. */
internal fun canEarn(type: AccountType): Boolean = !type.liability && type != AccountType.CASH

/** What the earned-interest fields hold while a form is open; [rate] and [tax] are percent text. */
internal class EarnDraft(rate: String = "", schedule: EarnSchedule = EarnSchedule.OFF, tax: String = "", autoPost: Boolean = false) {
    var rate by mutableStateOf(rate)
    var schedule by mutableStateOf(schedule)
    var tax by mutableStateOf(tax)
    var autoPost by mutableStateOf(autoPost)

    /** The rate and tax in basis points, or null when either is not a percentage. Both are zero for an account that cannot earn. */
    fun basisPoints(type: AccountType): Pair<Int, Int>? {
        if (!canEarn(type)) return 0 to 0
        val r = if (rate.isBlank()) 0 else percentToBasisPoints(rate) ?: return null
        val t = if (tax.isBlank()) 0 else percentToBasisPoints(tax) ?: return null
        return r to t
    }
}

/** The "Interest earned" fields of a deposit account's form: how often the bank credits it, the yearly rate, the tax withheld, and whether estimates post without review. */
@Composable
internal fun EarnFields(type: AccountType, draft: EarnDraft) {
    if (!canEarn(type)) return
    PixelDropdown(EarnSchedule.entries.toList(), draft.schedule, { it?.label ?: "" }, { s -> if (s != null) draft.schedule = s }, Modifier.fillMaxWidth(), key = ModuleAccent.MINT.accent, label = "Interest earned")
    if (draft.schedule != EarnSchedule.OFF) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelField(
                draft.rate, { draft.rate = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) }, Modifier.weight(1f), label = "Yearly rate (%)", singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            PixelField(
                draft.tax, { draft.tax = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5) }, Modifier.weight(1f), label = "Tax withheld (%)", singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        ToggleRow("Post without review", draft.autoPost) { draft.autoPost = it }
    }
}

/**
 * The interest estimates waiting for the player (MINT.md 4.3E): one row per estimate with Confirm, Adjust (type what the bank really credited) and Skip, and a
 * Confirm all. Shown on the Overview only while something waits. Making the estimates is automatic and happens when Mint opens.
 */
@Composable
internal fun InterestReview(model: AppModel) {
    val mint = model.mint
    val data = model.mintData
    LaunchedEffect(model.store.today) { mint.accrueInterest() }
    val waiting = mint.interestToReview()
    if (waiting.isEmpty()) return
    var adjusting by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Interest to confirm")
            waiting.forEach { t -> InterestRow(model, t, adjusting == t.id, amount, { amount = it }, { adjusting = if (adjusting == t.id) null else t.id; amount = Money.plain(t.amountMinor, t.currency) }, { message = it }) { adjusting = null }
            }
            if (waiting.size > 1) PixelButton("Confirm all", { mint.confirmAllInterest(); model.haptic() }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, compact = true)
            message?.let { Report(model, it, NoticeTone.WARNING) }
        }
    }
}

@Composable
private fun InterestRow(model: AppModel, t: Transaction, adjusting: Boolean, amount: String, setAmount: (String) -> Unit, toggle: () -> Unit, problem: (String?) -> Unit, done: () -> Unit) {
    val mint = model.mint
    val account = model.mintData.accounts.firstOrNull { it.id == t.accountId }?.name.orEmpty()
    HorizontalDivider(color = Pixel.Border)
    MintLine(trail = { Text("+" + mint.show(t.amountMinor, t.currency), style = MaterialTheme.typography.titleSmall) }) {
        Text(account, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        Text(t.note, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, maxLines = 2)
    }
    if (adjusting) PixelField(
        amount, { setAmount(it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16)) }, Modifier.fillMaxWidth(), label = "Amount the bank credited", singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), prefix = t.currency,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
        PixelButton(if (adjusting) "Save" else "Confirm", {
            val minor = if (adjusting) Money.parse(amount, t.currency) else null
            if (adjusting && minor == null) problem("Check the amount.")
            else {
                val out = mint.confirmInterest(t.id, minor)
                if (out.record == null) problem(out.refused) else { problem(null); done(); model.haptic() }
            }
        }, Modifier.weight(1f), ModuleAccent.MINT.accent, compact = true)
        PixelButton(if (adjusting) "Cancel" else "Adjust", toggle, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        if (!adjusting) PixelButton("Skip", { mint.skipInterest(t.id) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
    }
}
