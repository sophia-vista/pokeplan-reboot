package app.pokeplan.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pokeplan.app.integrations.EventKeys
import app.pokeplan.app.integrations.IntegrationStore
import app.pokeplan.app.integrations.RecordRef
import app.pokeplan.ui.integrations.EventMemoryDraft
import app.pokeplan.ui.integrations.EventMemoryLinks
import app.pokeplan.app.integrations.SourceSnapshot
import app.pokeplan.app.integrations.enrichmentOf
import app.pokeplan.plan.TaskState
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.integrations.EventPeoplePicker
import app.pokeplan.ui.integrations.linkPeopleAvailable
import app.pokeplan.ui.screens.FormGroup
import app.pokeplan.ui.screens.PixelDropdown
import app.pokeplan.ui.screens.short
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.PixelShape
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import kotlinx.coroutines.delay

/** Which kind of thing a Calendar entry is, said in words so colour is never the only cue (PLAN 8.5). */
internal enum class EntrySource(val label: String) {
    TASK("PLAN task"), EVENT("PLAN event"), GOOGLE("Google Calendar"), PHONE("Phone calendar"), LINK("LINK");
}

@Composable
internal fun SourceTag(source: EntrySource, color: androidx.compose.ui.graphics.Color, detail: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        androidx.compose.foundation.layout.Box(Modifier.size(8.dp).background(color, PixelShape))
        Text(
            if (detail.isNullOrBlank()) source.label else "${source.label} · $detail",
            style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * "Your additions" (PLAN 8.4): people, related tasks, a checklist, private notes and a linked goal, for any calendar event.
 * Everything here is kept in PokéLife under [key], apart from the event, so editing or refreshing the event never touches it.
 * [snapshot] is what the source looks like now; it is stored with the first addition so a note can say what it was about if the event goes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EventEnrichmentSection(model: AppModel, key: String, snapshot: SourceSnapshot?, memoryDraft: EventMemoryDraft? = null) {
    val store = model.integrations
    val saved = model.integrationData.enrichmentOf(key)
    var notes by remember(key) { mutableStateOf(saved?.notes.orEmpty()) }
    LaunchedEffect(notes) {
        delay(600)
        if (notes.trim() != (store.enrichmentOf(key)?.notes ?: "")) store.setNotes(key, notes.trim(), snapshot)
    }
    FormGroup(title = "Your additions") {
        // People: the LINK picker, keyed like everything else here.
        val live = model.linkData.people.map { it.id }.toSet()
        val people = model.integrationData.eventPeople.firstOrNull { it.eventId == key }?.personIds.orEmpty().filter { it in live }
        if (linkPeopleAvailable(model) || people.isNotEmpty()) EventPeoplePicker(model, people) { ids ->
            store.setPeople(key, ids)
            if (snapshot != null && EventKeys.isDevice(key)) store.track(key, snapshot)
        }

        // Related tasks.
        val tasks = model.data.tasks.filter { it.state != TaskState.DELETED }
        val linked = saved?.taskIds.orEmpty().mapNotNull { id -> tasks.firstOrNull { it.id == id } }
        var query by remember(key) { mutableStateOf("") }
        if (linked.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            linked.forEach { t -> PixelButton("${t.title}  ×", { store.unlinkTask(key, t.id) }, color = Pixel.Tone, compact = true) }
        }
        if (linked.size < IntegrationStore.MAX_LINKS) {
            PixelField(query, { query = it }, Modifier.fillMaxWidth(), label = "Link a task", singleLine = true)
            if (query.isNotBlank()) {
                val matches = tasks.filter { it.isOpen && it.id !in linked.map { l -> l.id } && it.title.contains(query.trim(), ignoreCase = true) }.take(5)
                if (matches.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    matches.forEach { t -> PixelButton(t.title, { store.linkTask(key, t.id, snapshot); query = "" }, color = Pixel.InkSoft, compact = true) }
                }
            }
        }

        // Linked goals (other modules' records join the same list as each module offers a picker).
        val goals = model.data.goals
        val linkedGoals = saved?.records.orEmpty().filter { it.module == GOAL }.mapNotNull { r -> goals.firstOrNull { it.id == r.id }?.let { r to it } }
        if (goals.isNotEmpty()) {
            FieldLabel("Goals")
            if (linkedGoals.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                linkedGoals.forEach { (r, g) -> PixelButton("${g.title}  ×", { store.unlinkRecord(key, r) }, color = Pixel.Tone, compact = true) }
            }
            val open = goals.filter { g -> linkedGoals.none { it.second.id == g.id } && g.completedAtMillis == null }
            if (open.isNotEmpty() && linkedGoals.size < IntegrationStore.MAX_LINKS) PixelDropdown(
                listOf<String?>(null) + open.map { it.id }, null,
                { id -> if (id == null) "Link a goal" else open.firstOrNull { it.id == id }?.title ?: "" },
                { id -> if (id != null) store.linkRecord(key, RecordRef(GOAL, id), snapshot) }, Modifier.fillMaxWidth(), label = "Goal",
            )
        }

        // Memories (REP-088): Save as Memory after the event, or attach one the player already keeps.
        EventMemoryLinks(model, key, snapshot, memoryDraft)

        // Checklist.
        FieldLabel("Checklist")
        saved?.checklist.orEmpty().forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelIconButton("check", if (line.done) "Untick ${line.text}" else "Tick ${line.text}", { store.tickLine(key, line.id) }, if (line.done) Pixel.Green else Pixel.InkSoft)
                Text(
                    line.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                    color = if (line.done) Pixel.InkSoft else Pixel.Ink, textDecoration = if (line.done) TextDecoration.LineThrough else TextDecoration.None,
                )
                PixelIconButton("trash", "Remove ${line.text}", { store.removeLine(key, line.id) }, Pixel.InkSoft)
            }
        }
        if ((saved?.checklist?.size ?: 0) < IntegrationStore.MAX_LINES) {
            var line by remember(key) { mutableStateOf("") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelField(
                    line, { line = it }, Modifier.weight(1f), singleLine = true, placeholder = "Add a line",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                PixelIconButton("plus", "Add line", { store.addLine(key, line, model.store.newEventId(), snapshot); line = "" }, Pixel.InkSoft, enabled = line.isNotBlank())
            }
        }

        PixelField(
            notes, { notes = it.take(IntegrationStore.MAX_NOTES) }, Modifier.fillMaxWidth(), label = "Private notes", minLines = 2, maxLines = 6,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
}

private const val GOAL = "goal"

/**
 * Notes whose event is no longer in the calendar (PLAN 8.4): kept, never deleted for the player. Opening one shows what it was and
 * its additions; only the Delete key, and its confirmation, removes it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KeptNotesBox(model: AppModel, startOpen: Boolean = false) {
    val kept = model.integrationData.enrichments.filter { it.unavailable }
    if (kept.isEmpty()) return
    var shown by remember { mutableStateOf(startOpen) }
    var open by remember { mutableStateOf<String?>(if (startOpen) kept.first().key else null) }
    var confirming by remember { mutableStateOf<String?>(null) }
    PixelButton(if (shown) "Hide kept notes" else "Kept notes (${kept.size})", { shown = !shown }, color = Pixel.InkSoft, compact = true)
    if (!shown) return
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            kept.sortedByDescending { it.seen?.day.orEmpty() }.forEach { e ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Column(Modifier.weight(1f)) {
                            Text(e.seen?.title ?: "Event", style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("No longer in the calendar" + (e.seen?.day?.let { d -> runCatching { kotlinx.datetime.LocalDate.parse(d) }.getOrNull()?.let { " · last on ${it.short()}" } } ?: ""), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                        }
                        PixelButton(if (open == e.key) "Close" else "Open", { open = if (open == e.key) null else e.key }, color = Pixel.Tone, compact = true)
                    }
                    if (open == e.key) {
                        EventEnrichmentSection(model, e.key, null)
                        if (confirming == e.key) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                            Text("Delete these notes for good?", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            PixelButton("Delete", { model.integrations.discard(e.key); confirming = null; open = null }, color = Pixel.Red, compact = true)
                            PixelButton("Keep", { confirming = null }, color = Pixel.InkSoft, compact = true)
                        } else PixelButton("Delete for good", { confirming = e.key }, color = Pixel.Red, compact = true)
                    }
                }
            }
        }
    }
}
