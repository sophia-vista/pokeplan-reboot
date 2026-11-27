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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pokeplan.link.LinkRules
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.OneDriveItem
import app.pokeplan.ui.OneDriveSource
import app.pokeplan.ui.OneDriveState
import app.pokeplan.ui.Report
import app.pokeplan.ui.screens.RecordRow
import app.pokeplan.ui.theme.ModuleAccent
import app.pokeplan.ui.theme.NoticeTone
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelShape
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.theme.focusRing
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.jetbrains.compose.resources.decodeToImageBitmap

private val accent: Color @Composable get() = ModuleAccent.LINK.accent

/** One photo tile's side: big enough to see a photo, small enough to keep two in a row at 540 px. */
private const val TILE = 112

/** The one or two words the Sections menu shows for the optional OneDrive source. */
internal fun oneDriveDetail(state: OneDriveState): String = when (state) {
    OneDriveState.CONNECTED -> "connected"
    OneDriveState.CONNECTING -> "waiting"
    OneDriveState.NEEDS_SIGN_IN -> "check sign-in"
    OneDriveState.NOT_SET_UP, OneDriveState.DISCONNECTED -> "connect"
    OneDriveState.UNAVAILABLE -> "unavailable"
}

/** The MIME type a picture's first bytes say it is; OneDrive gives PNG, JPEG or WebP. */
private fun photoMime(bytes: ByteArray): String = when {
    bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
    bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
    bytes.size > 12 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() -> "image/webp"
    else -> "image/png"
}

/**
 * LINK's optional OneDrive place: connect the read-only source, walk its folders and photos, and add a photo to a
 * memory the player already keeps. Nothing here writes, moves or deletes on OneDrive.
 */
@Composable
internal fun OneDriveScreen(model: AppModel) {
    val source = model.oneDrive
    if (source == null) {
        // The Sections entry is hidden without a source; this is only the desktop and web fallback.
        Text("OneDrive isn't available here.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        return
    }
    var state by remember(source) { mutableStateOf(source.state()) }
    var problem by remember(source) { mutableStateOf<String?>(null) }
    val connect = {
        problem = null
        state = OneDriveState.CONNECTING
        source.connect { next, why ->
            state = next
            problem = why
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        when (state) {
            OneDriveState.NOT_SET_UP -> Text("OneDrive isn't set up on this build.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            OneDriveState.DISCONNECTED, OneDriveState.NEEDS_SIGN_IN -> PixelButton("Connect OneDrive", connect, Modifier.fillMaxWidth(), accent, compact = true)
            OneDriveState.CONNECTING -> {
                Text("Waiting for Microsoft…", style = MaterialTheme.typography.bodyLarge)
                PixelButton("Connect again", connect, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            }
            OneDriveState.CONNECTED -> {
                TextBox {
                    RecordRow(
                        "Connected", "",
                        trail = { PixelButton("Disconnect", { problem = null; source.disconnect { next, why -> state = next; problem = why } }, color = Pixel.InkSoft, compact = true) },
                    )
                }
                OneDriveBrowser(model, source)
            }
            OneDriveState.UNAVAILABLE -> {
                Text("OneDrive isn't available right now.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                PixelButton("Connect again", connect, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            }
        }
        problem?.let { Report(model, it, NoticeTone.WARNING) }
    }
}

/** The folders and photos of the connected drive, one folder at a time; tapping a photo opens the add-to-memory panel. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OneDriveBrowser(model: AppModel, source: OneDriveSource) {
    var folderId by remember { mutableStateOf<String?>(null) }
    var folderName by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<OneDriveItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var problem by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<OneDriveItem?>(null) }
    val thumbs = remember { mutableStateMapOf<String, ImageBitmap>() }
    val tried = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(folderId) {
        loading = true
        problem = null
        source.children(folderId) { list, why ->
            items = list.orEmpty()
            problem = why
            loading = false
        }
    }

    val folder = folderId
    if (folder != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            PixelButton("Up", { picked = null; folderId = null; folderName = null }, color = accent, compact = true)
            Text(folderName.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    val trouble = problem
    val folders = items.filter { it.folder }
    val photos = items.filterNot { it.folder }
    when {
        trouble != null -> Report(model, trouble, NoticeTone.WARNING)
        loading -> Text("Loading…", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        items.isEmpty() -> Text("Nothing here.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        else -> TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                folders.forEach { item ->
                    RecordRow(
                        item.name, if (item.childCount == 1) "1 item" else "${item.childCount} items",
                        onClick = { picked = null; folderId = item.id; folderName = item.name },
                        onClickLabel = "Open the folder",
                    )
                }
                if (photos.isNotEmpty()) FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                    verticalArrangement = Arrangement.spacedBy(Spacing.S),
                ) {
                    photos.forEach { item ->
                        PhotoTile(item, source, thumbs, tried) { picked = if (picked?.id == item.id) null else item }
                    }
                }
            }
        }
    }
    picked?.let { item ->
        AddToMemoryPanel(model, source, item, thumbs) { picked = null }
    }
}

/** One OneDrive photo: its thumbnail once it is fetched (and never twice), or a blank tile, with its name under it. */
@Composable
private fun PhotoTile(
    item: OneDriveItem,
    source: OneDriveSource,
    thumbs: MutableMap<String, ImageBitmap>,
    tried: MutableMap<String, Boolean>,
    onOpen: () -> Unit,
) {
    val bitmap = thumbs[item.id]
    LaunchedEffect(item.id) {
        if (thumbs.containsKey(item.id) || tried[item.id] == true) return@LaunchedEffect
        tried[item.id] = true
        source.thumbnail(item.id) { bytes, _ ->
            if (bytes != null) runCatching { bytes.decodeToImageBitmap() }.getOrNull()?.let { thumbs[item.id] = it }
        }
    }
    Column(
        Modifier.width(TILE.dp).clip(PixelShape).focusRing(PixelShape).clickable(role = Role.Button, onClickLabel = "Open the photo") { onOpen() },
        verticalArrangement = Arrangement.spacedBy(Spacing.XS),
    ) {
        if (bitmap != null) Image(bitmap, item.name, Modifier.fillMaxWidth().aspectRatio(1f).clip(PixelShape), contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(PixelShape).background(Pixel.Shade))
        Text(item.name, style = MaterialTheme.typography.bodySmall, color = Pixel.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** How many memories the destination list shows before "Show all"; a long archive is not walked in one scroll. */
private const val DESTINATIONS_SHOWN = 6

/**
 * A photo chosen from the drive, waiting for the memory it joins. No memory is chosen for the player: the list shows
 * each one's name, date and people, nothing is selected until one is tapped, and the add key stays off until then
 * (owner's Sprint 1 list: the newest memory used to be picked silently).
 */
@OptIn(ExperimentalEncodingApi::class)
@Composable
private fun AddToMemoryPanel(model: AppModel, source: OneDriveSource, item: OneDriveItem, thumbs: MutableMap<String, ImageBitmap>, onClose: () -> Unit) {
    val link = model.link
    val memories = link.memories()
    var memoryId by remember(item.id) { mutableStateOf<String?>(null) }
    var showAll by remember(item.id) { mutableStateOf(false) }
    var message by remember(item.id) { mutableStateOf<String?>(null) }
    var busy by remember(item.id) { mutableStateOf(false) }
    val chosen = memories.firstOrNull { it.id == memoryId }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelTitle("Add to a memory", small = true)
            val bitmap = thumbs[item.id]
            if (bitmap != null) Image(bitmap, item.name, Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height), contentScale = ContentScale.Fit)
            else Box(Modifier.fillMaxWidth().height(160.dp).clip(PixelShape).background(Pixel.Shade))
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (memories.isEmpty()) Text("No memories yet.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            else {
                // A chosen memory stays on the list even past the short cut.
                val visible = if (showAll) memories else memories.take(DESTINATIONS_SHOWN).let { top -> if (chosen != null && chosen !in top) top + chosen else top }
                visible.forEach { m ->
                    val people = m.personIds.mapNotNull { id -> link.data.people.firstOrNull { it.id == id }?.let(LinkRules::displayName) }
                    RecordRow(
                        link.memoryLabel(m), listOf(LinkRules.dateLabel(m.date), people.joinToString(", ")).filter { it.isNotBlank() }.joinToString(" · "),
                        lead = { Text(if (m.id == memoryId) "●" else "○", color = accent) },
                        onClick = { memoryId = m.id; message = null },
                        onClickLabel = if (m.id == memoryId) "Selected" else "Choose this memory",
                    )
                }
                if (!showAll && memories.size > DESTINATIONS_SHOWN) PixelButton("Show all ${memories.size}", { showAll = true }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                PixelButton(if (chosen == null) "Choose a memory first" else "Add to this memory", {
                    val id = memoryId ?: return@PixelButton
                    busy = true
                    source.download(item.id) { bytes, why ->
                        busy = false
                        if (bytes == null) { message = why ?: "That photo could not be read."; return@download }
                        val out = link.addPhoto(id, Base64.encode(bytes), mime = photoMime(bytes), caption = "")
                        if (out.record == null) message = out.refused ?: "That photo could not be saved."
                        else {
                            message = null
                            model.haptic()
                            model.say("Added.")
                            onClose()
                        }
                    }
                }, Modifier.fillMaxWidth(), accent, enabled = memoryId != null && !busy)
            }
            message?.let { Report(model, it, NoticeTone.WARNING) }
            PixelButton("Close", onClose, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        }
    }
}
