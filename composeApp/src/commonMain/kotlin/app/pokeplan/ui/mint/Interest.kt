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
import app.pokeplan.mint.TxType
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

internal fun canEarn(type: AccountType): Boolean = !type.liability && type != AccountType.CASH

internal class EarnDraft(rate: String = "", schedule: EarnSchedule = EarnSchedule.OFF, tax: String = "", autoPost: Boolean = false) {
    var rate by mutableStateOf(rate)
    var schedule by mutableStateOf(schedule)
    var tax by mutableStateOf(tax)
    var autoPost by mutableStateOf(autoPost)

    fun basisPoints(type: AccountType): Pair<Int, Int>? {
        if (!canEarn(type)) return 0 to 0
        val r = if (rate.isBlank()) 0 else percentToBasisPoints(rate) ?: return null
        val t = if (tax.isBlank()) 0 else percentToBasisPoints(tax) ?: return null
        return r to t
    }
}

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
 * The one box that holds every record waiting for the player (MINT.md 4.3E and 10.4): an interest estimate an account
 * made, or a transaction a bank alert the phone read proposed. Either is confirmed, adjusted to the real amount, or
 * skipped; nothing in it is in a balance, a budget or a report until it is confirmed.
 */
@Composable
internal fun PendingReview(model: AppModel) {
    val mint = model.mint
    val capture = model.capture
    var captureProblem by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(model.store.today) { mint.accrueInterest() }
    // The Android parts that read alerts follow the switches, whichever MINT screen is opened first.
    LaunchedEffect(capture, model.mintData.settings.captureNotifications, model.mintData.settings.captureMessages) {
        capture?.watch(model.mintData.settings.captureNotifications, model.mintData.settings.captureMessages)
    }
    // A bank alert the phone read waits in its own sealed box; it becomes a proposal here, where the player is.
    LaunchedEffect(capture, model.store.today) {
        if (capture != null) {
            // One it cannot place (no account matches yet) is said out loud rather than dropped without a word.
            captureProblem = capture.take().mapNotNull { mint.capture(it).reason?.takeIf { reason -> reason != "Already recorded." } }.firstOrNull()
        }
    }
    val waiting = mint.toReview()
    if (waiting.isEmpty() && captureProblem == null) return
    var adjusting by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("To confirm")
            waiting.forEach { t ->
                PendingRow(model, t, adjusting == t.id, amount, { amount = it }, {
                    adjusting = if (adjusting == t.id) null else t.id
                    amount = Money.plain(t.amountMinor, t.currency)
                }, { message = it }) { adjusting = null }
            }
            if (waiting.size > 1) PixelButton("Confirm all", { mint.confirmAllPending(); model.haptic() }, Modifier.fillMaxWidth(), ModuleAccent.MINT.accent, compact = true)
            message?.let { Report(model, it, NoticeTone.WARNING) }
            if (waiting.isNotEmpty()) captureProblem = null else captureProblem?.let { Report(model, it, NoticeTone.WARNING) }
        }
    }
}

@Composable
private fun PendingRow(model: AppModel, t: Transaction, adjusting: Boolean, amount: String, setAmount: (String) -> Unit, toggle: () -> Unit, problem: (String?) -> Unit, done: () -> Unit) {
    val mint = model.mint
    val account = model.mintData.accounts.firstOrNull { it.id == t.accountId }?.name.orEmpty()
    val from = t.capturedFrom
    HorizontalDivider(color = Pixel.Border)
    MintLine(trail = { Text((if (t.type == TxType.INCOME) "+" else "-") + mint.show(t.amountMinor, t.currency), style = MaterialTheme.typography.titleSmall) }) {
        Text(if (from != null) "$from \u00b7 $account" else account, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        Text(if (from != null) t.payee.ifBlank { t.note } else t.note, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft, maxLines = 2)
    }
    if (adjusting) PixelField(
        amount, { setAmount(it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(16)) }, Modifier.fillMaxWidth(),
        label = if (from != null) "Amount the bank moved" else "Amount the bank credited", singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), prefix = t.currency,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
        PixelButton(if (adjusting) "Save" else "Confirm", {
            val minor = if (adjusting) Money.parse(amount, t.currency) else null
            if (adjusting && minor == null) problem("Check the amount.")
            else {
                val out = mint.confirmPending(t.id, minor)
                if (out.record == null) problem(out.refused) else { problem(null); done(); model.haptic() }
            }
        }, Modifier.weight(1f), ModuleAccent.MINT.accent, compact = true)
        PixelButton(if (adjusting) "Cancel" else "Adjust", toggle, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        if (!adjusting) PixelButton("Skip", { mint.skipPending(t.id) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
    }
}
