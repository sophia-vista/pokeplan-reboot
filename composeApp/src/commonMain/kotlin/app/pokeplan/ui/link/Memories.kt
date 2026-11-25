package app.pokeplan.ui.link

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pokeplan.link.Era
import app.pokeplan.link.LinkData
import app.pokeplan.link.LinkRules
import app.pokeplan.link.Memory
import app.pokeplan.link.MemoryPhoto
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
import app.pokeplan.ui.screens.UpTo
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.theme.NoticeTone
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelShape
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.theme.ToggleRow
import app.pokeplan.ui.theme.focusRing
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.jetbrains.compose.resources.decodeToImageBitmap

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

/** A memory's one line in a list: what the date knows, the place, who was there, how many photos it keeps. */
private fun memoryLine(data: LinkData, m: Memory): String =
    listOfNotNull(LinkRules.dateLabel(m.date), m.place, peopleLine(data, m.personIds), photoLine(data, m.id)).filter { it.isNotBlank() }.joinToString(" · ")

/** "1 photo" or "N photos", or null when the memory has none. */
private fun photoLine(data: LinkData, memoryId: String): String? = when (val n = LinkRules.photosOf(data, memoryId).size) {
    0 -> null
    1 -> "1 photo"
    else -> "$n photos"
}

private fun eraLine(data: LinkData, era: Era): String {
    val count = LinkRules.memoriesInEra(data, era.id).size
    // The body font has no en dash, so the range reads with a hyphen.
    val range = LinkRules.eraRangeLabel(era).replace('–', '-')
    return listOf(range, if (count == 1) "1 memory" else "$count memories").joinToString(" · ")
}

/** The MIME type a picture's first bytes say it is; the picker hands back PNG, JPEG or WebP. */
private fun photoMime(bytes: ByteArray): String = when {
    bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
    bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
    bytes.size > 12 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() -> "image/webp"
    else -> "image/png"
}

@OptIn(ExperimentalEncodingApi::class)
private fun decodePhoto(data: String): ImageBitmap? =
    if (data.isEmpty()) null else runCatching { Base64.decode(data).decodeToImageBitmap() }.getOrNull()

/** A photo at thumbnail size; tapping it opens the full view when [onClick] is given. */
@Composable
internal fun PhotoThumb(photo: MemoryPhoto, size: Dp = 96.dp, onClick: (() -> Unit)? = null) {
    val bitmap = remember(photo.data) { decodePhoto(photo.data) }
    PhotoImage(bitmap, photo.caption.ifBlank { "Photo" }, Modifier.size(size), onClick, "Open the photo")
}

@Composable
private fun PhotoImage(bitmap: ImageBitmap?, description: String?, modifier: Modifier, onClick: (() -> Unit)? = null, onClickLabel: String? = null) {
    val base = modifier.clip(PixelShape)
    val box = if (onClick == null) base else base.focusRing(PixelShape).clickable(role = Role.Button, onClickLabel = onClickLabel) { onClick() }
    if (bitmap != null) Image(bitmap, description, box, contentScale = ContentScale.Crop)
    else Box(box.background(Pixel.Shade))
}

/** The full view of one photo: the picture across the width, its caption, and one key to close it. */
@Composable
internal fun PhotoViewer(photo: MemoryPhoto, onClose: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        val bitmap = remember(photo.data) { decodePhoto(photo.data) }
        if (bitmap != null) Image(
            bitmap, photo.caption.ifBlank { "Photo" },
            Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height), contentScale = ContentScale.Fit,
        ) else Text("Could not show it", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        if (photo.caption.isNotBlank()) Text(photo.caption, style = MaterialTheme.typography.bodyLarge)
        PixelButton("Close", onClose, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
    }
}

/** One tile of the archive's gallery: the picture, the memory it belongs to, and its caption or date. */
@Composable
private fun PhotoTile(photo: MemoryPhoto, title: String, detail: String, onOpen: () -> Unit) {
    val bitmap = remember(photo.data) { decodePhoto(photo.data) }
    Column(
        Modifier.width(128.dp).clip(PixelShape).focusRing(PixelShape).clickable(role = Role.Button, onClickLabel = "Open the photo", onClick = onOpen),
        verticalArrangement = Arrangement.spacedBy(Spacing.XS),
    ) {
        PhotoImage(bitmap, photo.caption.ifBlank { title }, Modifier.fillMaxWidth().aspectRatio(1f))
        Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = Pixel.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One stored photo in the memory form: its picture, its caption (edited in one write), and a remove key with Undo. */
@Composable
private fun PhotoRow(model: AppModel, photo: MemoryPhoto) {
    val link = model.link
    var editing by remember(photo.id) { mutableStateOf(false) }
    var draft by remember(photo.id) { mutableStateOf(photo.caption) }
    var message by remember(photo.id) { mutableStateOf<String?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.Top) {
        PhotoThumb(photo, 96.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            if (editing) {
                PixelField(draft, { draft = it.take(LinkRules.PHOTO_CAPTION_MAX) }, Modifier.fillMaxWidth(), label = "Caption", singleLine = true, keyboardOptions = sentenceKeys)
                Actions {
                    PixelButton("Save", {
                        val out = link.updatePhotoCaption(photo.id, draft)
                        if (out.record == null) message = out.refused else { editing = false; message = null }
                    }, Modifier.weight(1f), accent, compact = true)
                    PixelButton("Cancel", { editing = false; draft = photo.caption }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
            } else {
                if (photo.caption.isNotBlank()) Text(photo.caption, style = MaterialTheme.typography.bodyMedium, color = Pixel.Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PixelButton("Caption", { draft = photo.caption; editing = true }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    PixelButton("Remove", {
                        val before = model.linkData
                        link.deletePhoto(photo.id)
                        model.offerUndo("Removed a photo") { link.restore(before) }
                    }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                }
            }
            message?.let { Report(model, it, NoticeTone.WARNING) }
        }
    }
}

/** A photo picked for a memory that is not saved yet; it is kept in the form until Save adds it. */
@Composable
private fun PendingPhotoRow(photo: Pair<String, String>, onRemove: () -> Unit) {
    val bitmap = remember(photo.second) { decodePhoto(photo.second) }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
        PhotoImage(bitmap, "Photo to add", Modifier.size(96.dp))
        PixelButton("Remove", onRemove, Modifier.weight(1f), Pixel.InkSoft, compact = true)
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
    var viewing by remember { mutableStateOf<MemoryPhoto?>(null) }

    val onThisDay = link.onThisDay()
    if (onThisDay.isNotEmpty()) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("On this day")
            onThisDay.forEach { m ->
                RecordRow(m.title, listOf(LinkRules.dateLabel(m.date), peopleLine(data, m.personIds)).filter { it.isNotBlank() }.joinToString(" · "))
            }
        }
    }
    ModuleTabs(listOf("Timeline", "Chapters", "Gallery"), tab, accent) { tab = it; openId = null; openEraId = null; adding = false; viewing = null }
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
    } else if (tab == 1) {
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
    } else {
        val photo = viewing
        if (photo != null) {
            UpTo { viewing = null }
            TextBox { PhotoViewer(photo) { viewing = null } }
        } else {
            val photos = data.photos.sortedByDescending { it.addedMillis }
            if (photos.isEmpty()) Text("No photos yet.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            else TextBox {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    photos.forEach { p ->
                        val memory = data.memories.firstOrNull { it.id == p.memoryId }
                        PhotoTile(p, memory?.title ?: "Photo", p.caption.ifBlank { memory?.let { LinkRules.dateLabel(it.date) }.orEmpty() }) { viewing = p }
                    }
                }
            }
        }
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalEncodingApi::class)
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
    /** A memory the first Save made, so photos picked for a new memory have somewhere to go before the form closes. */
    var madeId by remember(key) { mutableStateOf(existing?.id) }
    val photos = madeId?.let { link.photosOf(it) }.orEmpty()
    val pending = remember(key) { mutableStateListOf<Pair<String, String>>() }
    var photoProblem by remember(key) { mutableStateOf<String?>(null) }
    fun pickPhoto() {
        val images = model.images ?: return
        images.choose { bytes, why ->
            if (bytes == null) { photoProblem = why; return@choose }
            val encoded = Base64.encode(bytes)
            if (madeId == null) { pending += photoMime(bytes) to encoded; photoProblem = null }
            else {
                val out = link.addPhoto(madeId!!, encoded, photoMime(bytes))
                photoProblem = if (out.record == null) (out.refused ?: "That picture could not be saved.") else null
                if (out.record != null) model.haptic()
            }
        }
    }
    FormStack {
        if (existing == null && madeId == null) PixelTitle("Add a memory")
        FormGroup {
            PixelField(title, { title = it.take(LinkRules.MEMORY_TITLE_MAX) }, Modifier.fillMaxWidth(), label = "Title", singleLine = true, keyboardOptions = sentenceKeys)
            PixelField(note, { note = it.take(LinkRules.NOTE_MAX) }, Modifier.fillMaxWidth(), label = "Note", minLines = 2, keyboardOptions = sentenceKeys)
            PartialDateEditor(date, "Date")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PixelField(place, { place = it.take(LinkRules.FIELD_MAX) }, Modifier.weight(1f), label = "Place", singleLine = true, keyboardOptions = wordKeys)
                PixelField(time, { time = it.take(32) }, Modifier.weight(0.7f), label = "Time", singleLine = true)
            }
        }
        if (photos.isNotEmpty() || pending.isNotEmpty() || model.images != null) {
            if (photos.isNotEmpty() || pending.isNotEmpty()) FieldLabel("Photos")
            photos.forEach { photo -> PhotoRow(model, photo) }
            pending.forEachIndexed { i, photo -> PendingPhotoRow(photo) { pending.removeAt(i) } }
            if (model.images != null) PixelButton("Add a photo", { pickPhoto() }, Modifier.fillMaxWidth(), accent, compact = true)
            photoProblem?.let { Report(model, it, NoticeTone.WARNING) }
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
                link.eras().forEach { e -> Chip(e.title, e.id in eraIds, { eraIds = if (e.id in eraIds) eraIds - e.id else eraIds + e.id }) }
            }
        }
        FormSave("Save", {
            val d = date.value()
            val out = if (madeId == null) link.addMemory(title, d, note, time, place, personIds, eraIds)
            else link.updateMemory(
                existing ?: Memory(id = madeId!!, title = title, note = note, date = d, time = time, place = place, personIds = personIds, eraIds = eraIds),
            )
            val saved = out.record
            if (saved == null) { message = out.refused; return@FormSave }
            madeId = saved.id
            var refusal: String? = null
            val left = mutableListOf<Pair<String, String>>()
            pending.forEach { photo ->
                if (refusal != null) left += photo
                else {
                    val add = link.addPhoto(saved.id, photo.second, photo.first)
                    if (add.record == null) { refusal = add.refused ?: "That picture could not be saved."; left += photo }
                }
            }
            pending.clear(); pending += left
            if (refusal != null) { message = refusal; return@FormSave }
            message = null; photoProblem = null; model.haptic(); onDone()
        }, accent, enabled = title.isNotBlank() || note.isNotBlank())
        val deleteId = existing?.id ?: madeId
        if (deleteId == null) PixelButton("Cancel", onDone, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        else Actions {
            PixelButton("Cancel", onDone, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            PixelButton("Delete", {
                val before = model.linkData
                link.deleteMemory(deleteId)
                onDone()
                model.offerUndo("Deleted: ${existing?.title ?: title}") { link.restore(before) }
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
