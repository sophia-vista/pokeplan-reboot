package app.pokeplan.ui.integrations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.pokeplan.app.integrations.IntegrationStore
import app.pokeplan.app.integrations.RecordRef
import app.pokeplan.app.integrations.SourceSnapshot
import app.pokeplan.link.LinkRules
import app.pokeplan.link.PartialDate
import app.pokeplan.plan.PlanEvent
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.link.MemoryForm
import app.pokeplan.ui.link.MemoryPreset
import app.pokeplan.ui.screens.FormBox
import app.pokeplan.ui.screens.Module
import app.pokeplan.ui.screens.PixelDropdown
import app.pokeplan.ui.screens.short
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.Spacing
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** What "Save as Memory" starts a memory with: the event's title, its day and the people tied to it. The player can change all of it. */
internal data class EventMemoryDraft(val title: String, val day: LocalDate, val personIds: List<String>)

/** Memories can be linked unless LINK is locked (a locked module shows nothing of itself elsewhere). */
internal fun linkMemoriesAvailable(model: AppModel): Boolean = Module.LINK.name !in model.data.settings.lockedModules

/**
 * An event's memories (REP-088, LINK.md 3.4 and 6.3): the memories attached to it, **Save as Memory** once the event has started (a
 * memory form already filled from the event), and **Attach a memory** for one the player already keeps. Only the memory's id is
 * stored, in the integration document under [key]; the memory and the event stay independent, and a key detaches explicitly
 * (with Undo). Shows nothing when there is nothing to show or do.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EventMemoryLinks(model: AppModel, key: String, snapshot: SourceSnapshot?, draft: EventMemoryDraft?) {
    if (!linkMemoriesAvailable(model)) return
    val link = model.link
    val store = model.integrations
    val accent = ModuleAccent.LINK.accent
    val memories = link.memories()
    val liveById = memories.associateBy { it.id }
    val records = model.integrationData.enrichmentOf(key)?.records.orEmpty()
    val linked = records.filter { it.module == RecordRef.MEMORY }.mapNotNull { liveById[it.id] }
    val full = records.size >= IntegrationStore.MAX_LINKS
    val canSave = draft != null && draft.day <= model.store.today && !full
    val options = memories.filter { m -> linked.none { it.id == m.id } }
    if (linked.isEmpty() && !canSave && (options.isEmpty() || full)) return
    var adding by remember(key) { mutableStateOf(false) }
    FieldLabel("Memories")
    if (linked.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        linked.forEach { m ->
            PixelButton("${link.memoryLabel(m)}  ×", {
                store.unlinkMemory(key, m.id)
                model.offerUndo("Detached a memory") { store.linkMemory(key, m.id, snapshot) }
            }, color = accent, compact = true)
        }
    }
    if (adding && draft != null) {
        val live = model.linkData.people.map { it.id }.toSet()
        FormBox {
            MemoryForm(
                model, existing = null,
                preset = MemoryPreset(draft.title, PartialDate(draft.day.year, draft.day.monthNumber, draft.day.dayOfMonth), draft.personIds.filter { it in live }),
                onSaved = { saved -> store.linkMemory(key, saved.id, snapshot) },
                onDone = { adding = false },
            )
        }
    } else if (!full) {
        if (canSave) PixelButton("Save as Memory", { adding = true }, Modifier.fillMaxWidth(), accent, compact = true)
        if (options.isNotEmpty()) PixelDropdown(
            listOf<String?>(null) + options.map { it.id }, null,
            { id -> if (id == null) "Attach a memory" else options.firstOrNull { it.id == id }?.let { link.memoryLabel(it) }.orEmpty() },
            { id -> if (id != null) store.linkMemory(key, id, snapshot) }, Modifier.fillMaxWidth(), key = accent, label = "Memory",
        )
    }
}

/** The days a memory's page offers to attach a PLAN event from: the last 90 days and the next 30. */
private const val PAST_DAYS = 90
private const val AHEAD_DAYS = 30
private const val EVENTS_OFFERED = 20

/**
 * On a memory's page (REP-088): the PLAN events it is attached to (a key detaches one, with Undo) and **Attach to an event** for a
 * PLAN event near it. The event is never edited; the link lives only in the integration document.
 */
@Composable
internal fun MemoryEventLinks(model: AppModel, memoryId: String) {
    if (!linkMemoriesAvailable(model)) return
    val store = model.integrations
    val accent = ModuleAccent.LINK.accent
    val events = model.data.events
    val keys = store.eventsOfMemory(memoryId)
    val attached = keys.mapNotNull { k -> events.firstOrNull { it.id == k } }
    val today = model.store.today
    val from = today.minus(DatePeriod(days = PAST_DAYS))
    val to = today.plus(DatePeriod(days = AHEAD_DAYS))
    val options = events.filter { e -> e.id !in keys && e.day in from..to && PlanEvent.seriesOf(e.id) == e.id }
        .sortedByDescending { it.day }.take(EVENTS_OFFERED)
    if (attached.isEmpty() && options.isEmpty()) return
    FieldLabel("Calendar event")
    if (attached.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        attached.forEach { e ->
            PixelButton("${e.title} · ${e.day.short()}  ×", {
                store.unlinkMemory(e.id, memoryId)
                model.offerUndo("Detached from an event") { store.linkMemory(e.id, memoryId) }
            }, Modifier.fillMaxWidth(), accent, compact = true)
        }
    }
    if (options.isNotEmpty()) PixelDropdown(
        listOf<String?>(null) + options.map { it.id }, null,
        { id -> if (id == null) "Attach to an event" else options.firstOrNull { it.id == id }?.let { "${it.title} · ${it.day.short()}" }.orEmpty() },
        { id -> if (id != null) store.linkMemory(id, memoryId) }, Modifier.fillMaxWidth(), key = accent,
    )
}
