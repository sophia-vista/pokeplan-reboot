package app.pokeplan.ui.link

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.pokeplan.link.ContactBirthday
import app.pokeplan.link.ImportKind
import app.pokeplan.link.ImportPick
import app.pokeplan.link.ImportResult
import app.pokeplan.link.ImportRow
import app.pokeplan.link.LinkRules
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.ContactSource
import app.pokeplan.ui.Report
import app.pokeplan.ui.screens.Actions
import app.pokeplan.ui.screens.RecordRow
import app.pokeplan.ui.screens.Rule
import app.pokeplan.ui.screens.TabButton
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.theme.NoticeTone
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.PixelToggle
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val IMPORT_PAGE = 40
private val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private enum class Stage { ASK, DENIED, PICK, DONE }

/** The row's own key: the contact's lookup key, or its name when the phone gave none. */
private fun ImportRow.key() = card.sourceId.ifBlank { card.name }

private fun birthdayText(b: ContactBirthday) = "${b.day} ${monthNames[b.month - 1]}"

private fun ImportRow.detail(): String = listOfNotNull(
    when (kind) {
        ImportKind.NEW -> null
        ImportKind.KNOWN -> kind.label
        ImportKind.MAYBE -> "May be ${matches.joinToString(" or ") { LinkRules.displayName(it) }}"
    },
    card.organization.takeIf { it.isNotBlank() }, card.phones.firstOrNull(), card.birthday?.let { "Birthday ${birthdayText(it)}" },
).joinToString(" · ")

private fun resultLine(r: ImportResult): String = listOfNotNull(
    "Added ${r.added}".takeIf { r.added > 0 },
    "Linked ${r.linked}".takeIf { r.linked > 0 },
    (if (r.birthdays == 1) "1 birthday" else "${r.birthdays} birthdays").takeIf { r.birthdays > 0 },
    "${r.skipped} unchanged".takeIf { r.skipped > 0 },
).joinToString(" · ").ifEmpty { "Nothing to import." }

/**
 * LINK's optional import from the phone's contacts (LINK.md 3.5 and 6.1), in four steps on one screen: say what is read, ask the permission only now, let the
 * user pick and see which contacts are new or may already be here, then write the picks in one save with an Undo. Nothing is picked for the user, a contact that
 * may already be here is a new person unless the user marks it "Same as" one of the matches, and then only that person's empty fields are filled, and a birthday carries its reminder when the user chose one. Denying the permission leaves LINK as it was.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ContactImportView(model: AppModel, source: ContactSource, onClose: () -> Unit) {
    val accent = ModuleAccent.LINK.accent
    var stage by remember { mutableStateOf(if (source.permitted()) Stage.PICK else Stage.ASK) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var query by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(IMPORT_PAGE) }
    var remind by remember { mutableStateOf<Int?>(null) }
    var result by remember { mutableStateOf<ImportResult?>(null) }
    // A contact that may already be here is a new person unless the user says it is the same one (nothing merges by itself).
    var sameAs by remember { mutableStateOf(mapOf<String, String>()) }
    var plan by remember { mutableStateOf<app.pokeplan.link.ImportPlan?>(null) }

    // The address book and the matching against Link both run off the main thread: a large book is not a freeze.
    LaunchedEffect(stage) {
        if (stage == Stage.PICK && plan == null) plan = withContext(Dispatchers.Default) { model.link.planImport(source.contacts()) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        PixelTitle("Import contacts")
        when (stage) {
            Stage.ASK, Stage.DENIED -> {
                // Spec 3.5: say what is read before asking. Names, numbers, emails, companies and birthdays; nothing else.
                Text("Link reads names, phone numbers, emails, companies and birthdays, once, when you choose.", style = MaterialTheme.typography.bodyLarge) // copy-ok: the permission's scope
                if (stage == Stage.DENIED) Report(model, "Contacts were not allowed. Adding people by hand still works.", NoticeTone.WARNING)
                Actions {
                    PixelButton(if (stage == Stage.DENIED) "Ask again" else "Choose contacts", {
                        source.requestPermission { ok -> stage = if (ok) Stage.PICK else Stage.DENIED }
                    }, Modifier.weight(1f), accent, compact = true)
                    PixelButton("Cancel", onClose, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
            }
            Stage.PICK -> {
                val plan = plan
                if (plan == null) {
                    Text("Reading contacts", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    return@Column
                }
                val q = query.trim().lowercase()
                val rows = plan.rows.filter { q.isEmpty() || it.card.name.lowercase().contains(q) || it.card.organization.lowercase().contains(q) }
                val chosen = plan.rows.filter { it.key() in picked }
                if (plan.rows.isEmpty()) {
                    Text("No contacts with a name.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    Actions { PixelButton("Close", onClose, Modifier.weight(1f), Pixel.InkSoft, compact = true) }
                    return@Column
                }
                PixelField(query, { query = it.take(40); shown = IMPORT_PAGE }, Modifier.fillMaxWidth(), placeholder = "Search contacts", singleLine = true)
                val news = plan.rows.filter { it.kind == ImportKind.NEW }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    if (news.isNotEmpty()) PixelButton("Pick ${news.size} new", { picked = picked + news.map { it.key() } }, color = accent, compact = true)
                    if (picked.isNotEmpty()) PixelButton("Clear", { picked = emptySet() }, color = Pixel.InkSoft, compact = true)
                }
                if (plan.unnamed > 0) Text("${plan.unnamed} without a name left out", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                rows.take(shown).forEachIndexed { i, row ->
                    if (i > 0) Rule()
                    val on = row.key() in picked
                    RecordRow(
                        row.card.name, row.detail(), warn = row.kind == ImportKind.MAYBE,
                        onClick = { picked = if (on) picked - row.key() else picked + row.key() }, onClickLabel = if (on) "Leave out ${row.card.name}" else "Pick ${row.card.name}",
                        trail = { PixelToggle(on) },
                    )
                    if (on && row.kind == ImportKind.MAYBE) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            row.matches.forEach { m -> TabButton("Same as ${LinkRules.displayName(m)}", sameAs[row.key()] == m.id, { sameAs = sameAs + (row.key() to m.id) }, key = accent) }
                            TabButton("New person", row.key() !in sameAs, { sameAs = sameAs - row.key() }, key = accent)
                        }
                    }
                }
                if (rows.size > shown) PixelButton("Show ${minOf(IMPORT_PAGE, rows.size - shown)} more of ${rows.size - shown}", { shown += IMPORT_PAGE }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                if (rows.isEmpty()) Text("Nothing matches.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                if (chosen.any { it.card.birthday != null }) {
                    SectionLabel("Remind me of birthdays")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        TabButton("No", remind == null, { remind = null }, key = accent)
                        LinkRules.REMIND_PRESETS.forEach { n -> TabButton(remindLabel(n), remind == n, { remind = n }, key = accent) }
                    }
                }
                Actions {
                    PixelButton(if (chosen.isEmpty()) "Import" else "Import ${chosen.size}", {
                        val before = model.linkData
                        val picks = chosen.map { ImportPick(it.card, linkTo = when (it.kind) {
                            ImportKind.NEW -> null
                            ImportKind.KNOWN -> it.matches.firstOrNull()?.id
                            ImportKind.MAYBE -> sameAs[it.key()]
                        }) }
                        val out = model.link.importContacts(picks, remind)
                        result = out; model.haptic(); stage = Stage.DONE
                        if (out.added + out.linked + out.birthdays > 0) model.offerUndo("Imported contacts") { model.link.restore(before) }
                    }, Modifier.weight(1f), accent, enabled = chosen.isNotEmpty(), compact = true)
                    PixelButton("Cancel", onClose, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
            }
            Stage.DONE -> {
                Text(resultLine(result ?: ImportResult()), style = MaterialTheme.typography.bodyLarge)
                Actions { PixelButton("Done", onClose, Modifier.weight(1f), accent, compact = true) }
            }
        }
    }
}
