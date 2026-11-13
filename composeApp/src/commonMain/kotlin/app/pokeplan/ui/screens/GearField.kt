package app.pokeplan.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.pokeplan.app.Waypoint
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.gear.Geo
import app.pokeplan.ui.gear.NetworkCheck
import app.pokeplan.ui.gear.QrCode
import app.pokeplan.ui.gear.RatesClient
import app.pokeplan.ui.gear.SensorMath
import app.pokeplan.ui.gear.SoundMath
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.launch
import kotlin.time.TimeSource


/** Waypoints: mark where the phone is (one reading, asked for when you press the button; nothing is tracked) and see how far and which way each saved place is. */
@Composable
internal fun WaypointsTool(model: AppModel, onUse: () -> Unit) {
    val feed = model.services.location
    if (feed == null) return Missing("No location on this device")
    val store = model.store
    val gear = model.data.gear
    val clipboard = LocalClipboardManager.current
    var here by remember { mutableStateOf<app.pokeplan.ui.gear.Fix?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    fun locate(save: Boolean) {
        busy = true; message = "Looking for a fix. Outdoors with a clear sky is best."
        feed.fix { fix, reason ->
            busy = false
            if (fix == null) { message = reason ?: "No fix."; return@fix }
            here = fix
            message = "Accurate to about ${fix.accuracyMeters.toInt()} m."
            if (save) store.changeGear { g ->
                val n = g.waypoints.size + 1
                g.copy(waypoints = (listOf(Waypoint("w${fix.atMillis}", "Place $n", fix.latitude, fix.longitude, fix.accuracyMeters, fix.atMillis)) + g.waypoints).take(Waypoint.MAX))
            }
            onUse()
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                PixelButton(if (busy) "Looking..." else "Mark this place", { if (!busy) locate(true) }, Modifier.weight(1f), Pixel.Tone, compact = true, enabled = !busy)
                PixelButton("Where am I", { if (!busy) locate(false) }, Modifier.weight(1f), Pixel.InkSoft, compact = true, enabled = !busy)
            }
            if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodyMedium)
            here?.let { Text("You: ${Geo.coordinates(it.latitude, it.longitude)}", style = MaterialTheme.typography.bodyLarge) }
        }
    }
    if (gear.waypoints.isEmpty()) Text("No places saved yet.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    gear.waypoints.forEach { w ->
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                if (editing == w.id) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                        PixelField(editText, { editText = it.take(24) }, Modifier.weight(1f), singleLine = true, placeholder = "Name")
                        PixelIconButton("check", "Save the name", { store.changeGear { g -> g.copy(waypoints = g.waypoints.map { if (it.id == w.id) it.copy(name = editText.trim().ifEmpty { it.name }) else it }) }; editing = null })
                    }
                } else Text(w.name, style = MaterialTheme.typography.titleSmall)
                Text(Geo.coordinates(w.latitude, w.longitude), style = MaterialTheme.typography.bodyMedium)
                here?.let { h ->
                    val d = Geo.distanceMeters(h.latitude, h.longitude, w.latitude, w.longitude)
                    val b = Geo.bearingDegrees(h.latitude, h.longitude, w.latitude, w.longitude).toFloat()
                    Text("From you: ${Geo.distanceText(d)}, ${SensorMath.cardinal(b)} (${b.toInt()}°)", style = MaterialTheme.typography.bodyLarge, color = Pixel.Green)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    PixelIconButton("pencil", "Rename ${w.name}", { editing = w.id; editText = w.name })
                    PixelIconButton("copy", "Copy the map link for ${w.name}", { clipboard.setText(AnnotatedString(Geo.mapLink(w.latitude, w.longitude))) })
                    PixelIconButton("trash", "Delete ${w.name}", { store.changeGear { g -> g.copy(waypoints = g.waypoints.filterNot { it.id == w.id }) } })
                }
            }
        }
    }
}

/** The Sound meter: a level with its quietest, average and loudest, the pitch it hears and a spectrum, only while the tool is open. */
@Composable
internal fun SoundTool(model: AppModel, onUse: () -> Unit) {
    val feed = model.services.sound
    if (feed == null) return Missing("No microphone on this device")
    val store = model.store
    val offset = model.data.gear.soundOffsetDb
    var dbFs by remember { mutableStateOf<Float?>(null) }
    var low by remember { mutableStateOf<Float?>(null) }
    var high by remember { mutableStateOf<Float?>(null) }
    var sum by remember { mutableStateOf(0.0) }
    var count by remember { mutableStateOf(0) }
    var pitch by remember { mutableStateOf<Float?>(null) }
    var bands by remember { mutableStateOf(FloatArray(0)) }
    var problem by remember { mutableStateOf("") }
    DisposableEffect(feed) {
        var smooth: Float? = null
        val stop = feed.start({ frame, rate ->
            val level = SoundMath.dbFs(frame)
            smooth = smooth?.let { it + (level - it) * 0.35f } ?: level
            dbFs = smooth
            val s = smooth!!
            // Kept below full scale, so changing the offset moves the quietest, average and loudest with the reading.
            if (s > -119f) { low = low?.let { minOf(it, s) } ?: s; high = high?.let { maxOf(it, s) } ?: s; sum += s; count++ }
            val spec = SoundMath.spectrum(frame, 1024)
            pitch = SoundMath.dominantHz(spec, rate)
            // Twenty-four bands, each a block of bins, on a gentle curve so the low pitches are not squashed.
            bands = FloatArray(24) { b ->
                val from = (spec.size * (b / 24f) * (b / 24f)).toInt(); val to = maxOf(from + 1, (spec.size * ((b + 1) / 24f) * ((b + 1) / 24f)).toInt())
                (spec.slice(from until minOf(to, spec.size)).maxOrNull() ?: 0.0).toFloat()
            }
        }, { problem = it })
        if (stop == null) problem = "No microphone was found."
        onUse()
        onDispose { stop?.invoke() }
    }
    if (problem.isNotEmpty()) return Missing(problem)
    val est = dbFs?.let { SoundMath.estimate(it, offset) }
    TextBox {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(est?.let { "${it.toInt()} dB" } ?: "Listening", style = MaterialTheme.typography.displaySmall)
            est?.let { Text(SoundMath.describe(it), style = MaterialTheme.typography.bodyLarge) }
            if (count > 0) Text("Quietest ${SoundMath.estimate(low!!, offset).toInt()}, average ${SoundMath.estimate((sum / count).toFloat(), offset).toInt()}, loudest ${SoundMath.estimate(high!!, offset).toInt()} dB", style = MaterialTheme.typography.bodyMedium)
            Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                val n = bands.size.coerceAtLeast(1); val w = size.width / n
                val peak = (bands.maxOrNull() ?: 0f).coerceAtLeast(2f)
                bands.forEachIndexed { i, v ->
                    val h = (v / peak).coerceIn(0f, 1f) * size.height
                    drawRect(Pixel.Blue, Offset(i * w + 1f, size.height - h), Size(w - 2f, h))
                }
            }
            pitch?.let { Text("Pitch: ${it.toInt()} Hz, about ${SoundMath.noteName(it)}", style = MaterialTheme.typography.bodyLarge) }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelIconButton("minus", "One decibel lower", { store.changeGear { it.copy(soundOffsetDb = (it.soundOffsetDb - 1f).coerceAtLeast(60f)) } })
                Text("Offset ${offset.toInt()}", style = MaterialTheme.typography.bodyMedium)
                PixelIconButton("plus", "One decibel higher", { store.changeGear { it.copy(soundOffsetDb = (it.soundOffsetDb + 1f).coerceAtMost(120f)) } })
                PixelIconButton("reload", "Start the quietest and loudest again", { low = null; high = null; sum = 0.0; count = 0 })
            }
        }
    }
}

/** QR codes: make one from text (drawn here, nothing sent anywhere) or scan one with the camera. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QrTool(model: AppModel, onUse: () -> Unit) {
    val scanner = model.services.scanner
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    var level by remember { mutableStateOf(QrCode.Level.M) }
    var scanned by remember { mutableStateOf<String?>(null) }
    var scanNote by remember { mutableStateOf("") }
    val code = remember(text, level) { if (text.isEmpty()) null else QrCode.encode(text, level) }
    TextBox {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.S), horizontalAlignment = Alignment.CenterHorizontally) {
            PixelField(text, { text = it.take(400); onUse() }, Modifier.fillMaxWidth(), label = "Text or link for the code")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                QrCode.Level.entries.forEach { l -> TabButton(when (l) { QrCode.Level.L -> "Low repair"; QrCode.Level.M -> "Medium"; QrCode.Level.Q -> "High"; QrCode.Level.H -> "Highest" }, level == l, { level = l }) }
            }
            if (text.isNotEmpty() && code == null) Text("That is too long for a code at this level (${text.encodeToByteArray().size} bytes; the most is ${QrCode.capacity(level)}). Pick Low repair or shorten it.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Red)
            code?.let { c ->
                Canvas(Modifier.size(240.dp).background(Color.White)) {
                    val quiet = 4; val cells = c.size + quiet * 2; val cell = size.minDimension / cells
                    for (y in 0 until c.size) for (x in 0 until c.size) if (c.dark(x, y)) drawRect(Color.Black, Offset((x + quiet) * cell, (y + quiet) * cell), Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
    if (scanner != null) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PixelButton("Scan a code", { scanNote = ""; scanner.scan { r -> scanned = r; scanNote = if (r == null) "Nothing was scanned." else ""; onUse() } }, Modifier.fillMaxWidth(), Pixel.Tone, compact = true)
            if (scanNote.isNotEmpty()) Text(scanNote, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            scanned?.let { r ->
                val reading = app.pokeplan.ui.gear.QrPayload.read(r)
                Text(reading.kind.label, style = MaterialTheme.typography.titleSmall)
                Text(r, style = MaterialTheme.typography.bodyLarge)
                reading.warnings.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.Danger) }
                Text("Nothing was opened. Check it before you use it.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    PixelIconButton("copy", "Copy what was scanned", { clipboard.setText(AnnotatedString(r)) })
                    PixelIconButton("arrow-up", "Make a code from it", { text = r.take(400) })
                }
            }
        }
    }
}

/** The Network check: how the phone is connected now and, when asked, how quickly the internet answers. */
@Composable
internal fun NetworkTool(model: AppModel, onUse: () -> Unit) {
    val feed = model.services.network
    var info by remember { mutableStateOf(feed?.info()) }
    var result by remember { mutableStateOf<NetworkCheck.Result?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(feed) { onUse() }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            val i = info
            if (i != null) {
                Text("Connection: ${i.kind}", style = MaterialTheme.typography.titleSmall)
                if (i.online) Text((if (i.metered) "Metered" else "Not metered") + if (i.validated) ", online" else ", not confirmed", style = MaterialTheme.typography.bodyMedium)
                PixelIconButton("reload", "Look again", { info = feed?.info() })
            }
            PixelButton(if (busy) "Checking..." else "Check the connection", {
                if (!busy) scope.launch {
                    busy = true; onUse()
                    val http = RatesClient.newHttpClient()
                    val times = mutableListOf<Long?>()
                    repeat(3) {
                        val t = TimeSource.Monotonic.markNow()
                        times += try { http.get("https://www.cloudflare.com/cdn-cgi/trace").bodyAsText(); t.elapsedNow().inWholeMilliseconds } catch (e: Exception) { null }
                    }
                    http.close()
                    result = NetworkCheck.Result(times); busy = false
                }
            }, Modifier.fillMaxWidth(), Pixel.Tone, compact = true, enabled = !busy)
            result?.let { r ->
                Text(NetworkCheck.verdict(r), style = MaterialTheme.typography.bodyLarge)
                r.median?.let { Text("Answers in about $it ms" + (r.jitter?.let { j -> ", varying by up to $j ms" } ?: "") + ".", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
