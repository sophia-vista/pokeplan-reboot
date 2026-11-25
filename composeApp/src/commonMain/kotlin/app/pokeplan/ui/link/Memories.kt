package app.pokeplan.ui.link

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import app.pokeplan.link.Era
import app.pokeplan.link.LinkData
import app.pokeplan.link.LinkRules
import app.pokeplan.link.Memory
import app.pokeplan.link.PartialDate
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Report
import app.pokeplan.ui.screens.Actions
import app.pokeplan.ui.screens.FormBox
import app.pokeplan.ui.screens.FormGroup
import app.pokeplan.ui.screens.FormSave
import app.pokeplan.ui.screens.FormStack
import app.pokeplan.ui.screens.ModuleTabs
import app.pokeplan.ui.screens.PixelDropdown
import app.pokeplan.ui.screens.RecordRow
import app.pokeplan.ui.screens.TabButton
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.theme.NoticeTone
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.theme.ToggleRow

private val accent: Color @Composable get() = ModuleAccent.LINK.accent

private val numberKeys = KeyboardOptions(keyboardType = KeyboardType.Number)
private val wordKeys = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
private val sentenceKeys = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private const val NO_YEAR = "No year"
private const val NO_MONTH = "Month"

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) =
    TabButton(label, selected, onClick, modifier, key = accent)

/** The display names of the people a memory names, in the order it names them. */
private fun peopleLine(data: LinkData, ids: List<String>): String =
    ids.mapNotNull { id -> data.people.firstOrNull { it.id == id }?.let(LinkRules::displayName) }.joinToString(", ")

/** A memory's one line in a list: what the date knows, the place, who was there. */
private fun memoryLine(data: LinkData, m: Memory): String =
    listOf(LinkRules.dateLabel(m.date), m.place, peopleLine(data, m.personIds)).filter { it.isNotBlank() }.joinToString(" · ")

private fun eraLine(data: LinkData, era: Era): String {
    val count = LinkRules.memoriesInEra(data, era.id).size
    // The body font has no en dash, so the range reads with a hyphen.
    val range = LinkRules.eraRangeLabel(era).replace('–', '-')
    return listOf(range, if (count == 1) "1 memory" else "$count memories").joinToString(" · ")
}

@Composable
internal fun MemoriesScreen(model: AppModel) {
    val link = model.link
    val data = model.linkData
    val memories = link.memories()
    val eras = link.eras()
    var tab by remember { mutableStateOf(0) }
    var openId by remember { mutableStateOf<String?>(null) }
    var openEraId by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }

    val onThisDay = link.onThisDay()
    if (onThisDay.isNotEmpty()) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("On this day")
            onThisDay.forEach { m ->
                RecordRow(m.title, listOf(LinkRules.dateLabel(m.date), peopleLine(data, m.personIds)).filter { it.isNotBlank() }.joinToString(" · "))
            }
        }
    }
    ModuleTabs(listOf("Timeline", "Chapters"), tab, accent) { tab = it; openId = null; openEraId = null; adding = false }
    if (tab == 0) {
        if (memories.isEmpty()) Text("No memories yet.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        else TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                LinkRules.yearGroups(memories).forEach { (year, group) ->
                    FieldLabel(year?.toString() ?: NO_YEAR)
                    group.forEach { m ->
                        RecordRow(
                            m.title, memoryLine(data, m),
                            onClick = { openId = if (openId == m.id) null else m.id },
                            onClickLabel = if (openId == m.id) "Close the memory" else "Open the memory",
                        )
                        if (openId == m.id) Column(Modifier.padding(bottom = Spacing.S)) {
                            MemoryForm(model, existing = m, onDone = { openId = null })
                        }
                    }
                }
            }
        }
        if (adding) FormBox { MemoryForm(model, existing = null, onDone = { adding = false }) }
        else PixelButton("Add a memory", { adding = true }, Modifier.fillMaxWidth(), accent, compact = true)
    } else {
        if (eras.isEmpty()) Text("No chapters yet.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        else TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                eras.forEach { era ->
                    RecordRow(
                        era.title, eraLine(data, era),
                        onClick = { openEraId = if (openEraId == era.id) null else era.id },
                        onClickLabel = if (openEraId == era.id) "Close the chapter" else "Open the chapter",
                    )
                    if (openEraId == era.id) Column(verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(bottom = Spacing.S)) {
                        link.memoriesInEra(era.id).forEach { m -> RecordRow(m.title, LinkRules.dateLabel(m.date)) }
                        EraForm(model, era, onDone = { openEraId = null })
                    }
                }
            }
        }
        if (adding) FormBox { EraForm(model, existing = null, onDone = { adding = false }) }
        else PixelButton("Add an era", { adding = true }, Modifier.fillMaxWidth(), accent, compact = true)
    }
}

/** The typed parts of a partial date, so a form can edit one without inventing what the player left blank. */
private class DateInput(initial: PartialDate) {
    var year by mutableStateOf(initial.year?.toString().orEmpty())
    var month by mutableStateOf(initial.month)
    var day by mutableStateOf(initial.day?.toString().orEmpty())
    var approximate by mutableStateOf(initial.approximate)

    fun value(): PartialDate = PartialDate(
        year = year.toIntOrNull(),
        month = month,
        day = day.toIntOrNull(),
        approximate = approximate,
    )
}

/** A year, a month and a day, any of which the player can leave unknown; the rules judge what is missing. */
@Composable
private fun PartialDateEditor(input: DateInput, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        FieldLabel(label)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.Bottom) {
            PixelField(
                input.year, { input.year = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f),
                label = "Year", placeholder = "unknown", singleLine = true, keyboardOptions = numberKeys,
            )
            PixelDropdown(
                listOf<Int?>(null) + (1..12).toList(), input.month,
                { m -> if (m == null) NO_MONTH else MONTHS[m - 1] }, { m -> input.month = m },
                Modifier.weight(1.2f), key = accent, label = "Month",
            )
            PixelField(
                input.day, { input.day = it.filter(Char::isDigit).take(2) }, Modifier.weight(0.8f),
                label = "Day", singleLine = true, keyboardOptions = numberKeys,
            )
        }
        ToggleRow("Around then", input.approximate) { input.approximate = it }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MemoryForm(model: AppModel, existing: Memory?, presetPersonId: String? = null, onDone: () -> Unit) {
    val link = model.link
    val data = model.linkData
    val key = existing?.id ?: "new"
    var title by remember(key) { mutableStateOf(existing?.title.orEmpty()) }
    var note by remember(key) { mutableStateOf(existing?.note.orEmpty()) }
    val date = remember(key) { DateInput(existing?.date ?: PartialDate()) }
    var place by remember(key) { mutableStateOf(existing?.place.orEmpty()) }
    var time by remember(key) { mutableStateOf(existing?.time.orEmpty()) }
    var personIds by remember(key) { mutableStateOf(existing?.personIds ?: listOfNotNull(presetPersonId)) }
    var eraIds by remember(key) { mutableStateOf(existing?.eraIds.orEmpty()) }
    var message by remember(key) { mutableStateOf<String?>(null) }
    FormStack {
        if (existing == null) PixelTitle("Add a memory")
        FormGroup {
            PixelField(title, { title = it.take(LinkRules.MEMORY_TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Title", singleLine = true, keyboardOptions = sentenceKeys)
            PixelField(note, { note = it.take(LinkRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Note", minLines = 2, keyboardOptions = sentenceKeys)
            PartialDateEditor(date, "Date")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelField(place, { place = it.take(LinkRules.FIELD_MAX) }, Modifier.weight(1f), label = "Place", singleLine = true, keyboardOptions = wordKeys)
                PixelField(time, { time = it.take(32) }, Modifier.weight(0.7f), label = "Time", singleLine = true)
            }
        }
        if (data.people.isNotEmpty()) {
            FieldLabel("People")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                data.people.forEach { p -> Chip(LinkRules.displayName(p), p.id in personIds, { personIds = if (p.id in personIds) personIds - p.id else personIds + p.id }) }
            }
        }
        if (data.eras.isNotEmpty()) {
            FieldLabel("Chapters")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                data.eras.forEach { e -> Chip(e.title, e.id in eraIds, { eraIds = if (e.id in eraIds) eraIds - e.id else eraIds + e.id }) }
            }
        }
        FormSave("Save", {
            val d = date.value()
            val out = if (existing == null) link.addMemory(title, d, note, time, place, personIds, eraIds)
            else link.updateMemory(existing.copy(title = title, note = note, date = d, time = time, place = place, personIds = personIds, eraIds = eraIds))
            if (out.record == null) message = out.refused else { message = null; model.haptic(); onDone() }
        }, accent, enabled = title.isNotBlank() || note.isNotBlank())
        if (existing == null) PixelButton("Cancel", onDone, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        else Actions {
            PixelButton("Cancel", onDone, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            PixelButton("Delete", {
                val before = model.linkData
                link.deleteMemory(existing.id)
                onDone()
                model.offerUndo("Deleted: ${existing.title}") { link.restore(before) }
            }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        message?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

@Composable
internal fun EraForm(model: AppModel, existing: Era?, onDone: () -> Unit) {
    val link = model.link
    val key = existing?.id ?: "new"
    var title by remember(key) { mutableStateOf(existing?.title.orEmpty()) }
    var note by remember(key) { mutableStateOf(existing?.note.orEmpty()) }
    val start = remember(key) { DateInput(existing?.start ?: PartialDate()) }
    val end = remember(key) { DateInput(existing?.end ?: PartialDate()) }
    var message by remember(key) { mutableStateOf<String?>(null) }
    FormStack {
        if (existing == null) PixelTitle("Add a chapter")
        FormGroup {
            PixelField(title, { title = it.take(LinkRules.NAME_MAX) }, Modifier.fillMaxWidth(), label = "Title", singleLine = true, keyboardOptions = wordKeys)
            PixelField(note, { note = it.take(LinkRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Note", minLines = 2, keyboardOptions = sentenceKeys)
            PartialDateEditor(start, "Start")
            PartialDateEditor(end, "End")
        }
        FormSave("Save", {
            val out = if (existing == null) link.addEra(title, start.value(), end.value(), note)
            else link.updateEra(existing.copy(title = title, note = note, start = start.value(), end = end.value()))
            if (out.record == null) message = out.refused else { message = null; model.haptic(); onDone() }
        }, accent, enabled = title.isNotBlank())
        if (existing == null) PixelButton("Cancel", onDone, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        else Actions {
            PixelButton("Cancel", onDone, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            PixelButton("Delete", {
                val before = model.linkData
                link.deleteEra(existing.id)
                onDone()
                model.offerUndo("Deleted: ${existing.title}") { link.restore(before) }
            }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        message?.let { Report(model, it, NoticeTone.WARNING) }
    }
}
