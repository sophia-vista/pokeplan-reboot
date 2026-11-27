package app.pokeplan.ui.link

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pokeplan.link.LinkRules
import app.pokeplan.link.Memory
import app.pokeplan.link.MemoryPhoto
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.integrations.linkMemoriesAvailable
import app.pokeplan.ui.screens.RecordRow
import app.pokeplan.ui.screens.UpTo
import app.pokeplan.ui.screens.short
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox

private val accent get() = ModuleAccent.LINK.accent

/**
 * The read-first page of one memory (LINK.md 2.3, Sprint 2): its name, when and where, the note, its photos (each opens full size),
 * the people and chapters it belongs to (each opens that record) and the PLAN events it is attached to. Nothing on it writes; **Edit**
 * is the one key that opens the form. Every list, search result and photo that names a memory opens this page.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MemoryDetail(model: AppModel, memory: Memory, onEdit: () -> Unit, onPerson: (String) -> Unit, onChapter: (String) -> Unit) {
    val link = model.link
    val data = model.linkData
    var viewing by remember(memory.id) { mutableStateOf<MemoryPhoto?>(null) }
    val photos = link.photosOf(memory.id)
    val photo = viewing
    if (photo != null) {
        UpTo { viewing = null }
        TextBox { PhotoViewer(photo) { viewing = null } }
        return
    }
    val whenLine = listOf(if (memory.date.year != null) LinkRules.dateLabel(memory.date) else "", memory.time).filter { it.isNotBlank() }.joinToString(" · ")
    val people = memory.personIds.mapNotNull { id -> data.people.firstOrNull { it.id == id } }
    val chapters = memory.eraIds.mapNotNull { id -> data.eras.firstOrNull { it.id == id } }
    // The PLAN events this memory is attached to, as far as they still exist; a gone event simply is not listed.
    val events = if (linkMemoriesAvailable(model)) model.integrations.eventsOfMemory(memory.id).mapNotNull { k -> model.data.events.firstOrNull { it.id == k } } else emptyList()
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle(link.memoryLabel(memory))
            if (whenLine.isNotBlank()) Text(whenLine, style = MaterialTheme.typography.bodyLarge, color = Pixel.Ink)
            if (memory.place.isNotBlank()) Text(memory.place, style = MaterialTheme.typography.bodyLarge, color = Pixel.Ink)
            // A memory named by its note would show it twice; the page shows it once, in full.
            if (memory.note.isNotBlank()) Text(memory.note, style = MaterialTheme.typography.bodyMedium, color = Pixel.Ink)
        }
    }
    if (photos.isNotEmpty()) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            FieldLabel("Photos")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                photos.forEach { ph ->
                    Column(Modifier.width(96.dp), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                        PhotoThumb(ph, 96.dp) { viewing = ph }
                        if (ph.caption.isNotBlank()) Text(ph.caption, style = MaterialTheme.typography.bodySmall, color = Pixel.InkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    if (people.isNotEmpty() || chapters.isNotEmpty() || events.isNotEmpty()) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            if (people.isNotEmpty()) {
                FieldLabel("People")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    people.forEach { p -> PixelButton(LinkRules.displayName(p), { onPerson(p.id) }, color = accent, compact = true) }
                }
            }
            if (chapters.isNotEmpty()) {
                FieldLabel("Chapters")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    chapters.forEach { e -> PixelButton(e.title, { onChapter(e.id) }, color = accent, compact = true) }
                }
            }
            if (events.isNotEmpty()) {
                FieldLabel("Calendar events")
                events.forEach { e -> RecordRow(e.title, e.day.short()) }
            }
        }
    }
    PixelButton("Edit", onEdit, Modifier.fillMaxWidth(), accent, compact = true)
}
