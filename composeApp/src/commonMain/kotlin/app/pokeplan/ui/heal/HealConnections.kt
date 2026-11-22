package app.pokeplan.ui.heal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import app.pokeplan.app.Integrations
import app.pokeplan.app.ProviderAvailability
import app.pokeplan.heal.HealImport
import app.pokeplan.heal.HealKindOf
import app.pokeplan.heal.HealthKind
import app.pokeplan.heal.ImportPlan
import app.pokeplan.heal.ProviderRead
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Report
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelTitle
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.theme.ToggleRow
import app.pokeplan.ui.pokemon.label
import app.pokeplan.ui.screens.TabButton
import app.pokeplan.ui.screens.label
import app.pokeplan.ui.screens.shortDate

@Composable
internal fun HealConnectionsView(model: AppModel) {
    val heal = model.heal
    val health = model.health
    val settings = model.healData.settings
    val availability = health?.availability() ?: ProviderAvailability.UNSUPPORTED
    var granted by remember { mutableStateOf<Set<HealthKind>>(emptySet()) }
    var draft by remember { mutableStateOf(settings.healthKinds.ifEmpty { HealthKind.entries.toSet() }) }
    var days by remember { mutableStateOf(7) }
    var pending by remember { mutableStateOf<ProviderRead?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var allowing by remember { mutableStateOf(false) }
    var revoking by remember { mutableStateOf(false) }
    var askingDelete by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(health, settings.healthKinds) { health?.granted { granted = it } }

    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Integrations.heal(availability, settings.healthKinds, granted).forEachIndexed { i, info ->
                if (i > 0) HorizontalDivider(color = Pixel.Border)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                    Text(info.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(if (info.cls == app.pokeplan.app.IntegrationClass.UNSUPPORTED) "Not supported" else info.status.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    model.huawei?.let { HuaweiProbeBox(model, it) }

    if (health != null && availability == ProviderAvailability.AVAILABLE) {
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                PixelTitle("Health Connect")
                HealthKind.entries.forEach { k ->
                    ToggleRow(k.label, k in draft) { on -> draft = if (on) draft + k else draft - k }
                }
                val connected = settings.healthKinds.isNotEmpty()
                PixelButton(if (connected) "Change what Heal may read" else "Allow in Health Connect", {
                    heal.setHealthKinds(draft)
                    if (draft.isNotEmpty()) {
                        allowing = true
                        health.request(draft) { granted = it; allowing = false; note = if (it.isEmpty()) "Nothing was allowed. Heal reads nothing, and everything you logged is still here." else null } // copy-ok: result
                    }
                }, Modifier.fillMaxWidth(), Pixel.Tone, enabled = draft.isNotEmpty(), compact = true, loading = allowing)
                if (connected) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                        TabButton("Last 7 days", days == 7, { days = 7 }, Modifier.weight(1f))
                        TabButton("Last 30 days", days == 30, { days = 30 }, Modifier.weight(1f))
                    }
                    PixelButton("Read now", {
                        val kinds = settings.healthKinds.intersect(granted)
                        if (kinds.isEmpty()) note = "Nothing allowed yet"
                        else {
                            busy = true; note = null
                            health.read(kinds, days) { read, problem ->
                                busy = false
                                if (read == null) note = problem ?: "Health Connect could not be read. Nothing was changed." // copy-ok: result
                                else { pending = read; if (read.failed) note = "Only part of it could be read. You can add what came and try again later." } // copy-ok: result
                            }
                        }
                    }, Modifier.fillMaxWidth(), Pixel.Tone, enabled = !busy, compact = true, loading = busy)
                    if (settings.healthLastSyncMillis > 0) Text("Last read ${settings.healthLastSyncMillis.shortDate()}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                }
                val read = pending
                if (read != null) ImportPreview(heal.previewImport(read),
                    onAdd = { val p = heal.importFromProvider(read); pending = null; note = "Added ${p.candidates.size} ${if (p.candidates.size == 1) "record" else "records"}." },
                    onCancel = { pending = null })
                note?.let { Report(model, it) }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    PixelButton("Open Health Connect", { health.openSettings() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    if (connected) PixelButton("Disconnect", {
                        heal.setHealthKinds(emptySet()); pending = null
                        revoking = true
                        health.revoke { ok -> revoking = false; granted = emptySet(); note = if (ok) "Disconnected. Heal reads nothing now. The records already added stay until you delete them." else "Heal stopped reading. Android did not take the permissions back: open Health Connect to do that." } // copy-ok: result
                    }, Modifier.weight(1f), Pixel.Danger, compact = true, loading = revoking)
                }
            }
        }
    }

    val sources = heal.importedSources()
    if (sources.isNotEmpty()) {
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                PixelTitle("Imported records")
                sources.forEach { s ->
                    val parts = listOf("activities" to s.activities, "nights" to s.sleep, "measurements" to s.measurements, "drinks" to s.hydration).filter { it.second > 0 }.joinToString(", ") { "${it.second} ${it.first}" }
                    ListRow {
                        Text(s.app.ifBlank { "Unknown app" }, style = MaterialTheme.typography.bodyLarge)
                        Text(parts, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    }
                    if (askingDelete == s.app) {
                        Text("Delete these ${s.total} records? This cannot be undone.", style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                            PixelButton("Delete", { heal.removeImported(s.provider, s.app); askingDelete = null }, Modifier.weight(1f), Pixel.Danger, compact = true)
                            PixelButton("Keep them", { askingDelete = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                        }
                    } else PixelButton("Delete what ${s.app.ifBlank { "this app" }} added", { askingDelete = s.app }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                }
            }
        }
    }

    val flagged = heal.possibleDuplicates()
    if (flagged.isNotEmpty()) {
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                PixelTitle("Might be the same")
                flagged.forEach { (id, family) ->
                    ListRow {
                        Text(duplicateLine(model, id, family), style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                        PixelButton("Keep both", { heal.keepBoth(id) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                        PixelButton("Delete the import", {
                            val before = model.healData
                            model.offerUndo("Deleted: import") { heal.restore(before) }
                            when (family) {
                                HealKindOf.ACTIVITY -> heal.deleteActivity(id)
                                HealKindOf.SLEEP -> heal.deleteSleep(id)
                                HealKindOf.MEASUREMENTS -> heal.deleteMeasurement(id)
                                HealKindOf.HYDRATION -> heal.deleteDrink(id)
                                HealKindOf.STEPS -> heal.deleteSteps(id)
                                HealKindOf.VITALS -> heal.deleteVital(id)
                            }
                        }, Modifier.weight(1f), Pixel.Danger, compact = true)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ImportPreview(plan: ImportPlan, onAdd: () -> Unit, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        PixelTitle("Ready to add")
        if (plan.isEmpty) Text("Nothing new", style = MaterialTheme.typography.bodyLarge)
        else {
            HealthKind.entries.filter { plan.count(it) > 0 }.forEach { Text("${it.label}: ${plan.count(it)}", style = MaterialTheme.typography.bodyLarge) }
            if (plan.count(HealKindOf.STEPS) > 0) Text("Step counts: ${plan.count(HealKindOf.STEPS)}", style = MaterialTheme.typography.bodyLarge)
            if (plan.count(HealKindOf.VITALS) > 0) Text("Readings: ${plan.count(HealKindOf.VITALS)}", style = MaterialTheme.typography.bodyLarge)
            if (plan.updated > 0) Text("${plan.updated} updated with a newer value", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            if (plan.alreadyHere > 0) Text("${plan.alreadyHere} already here, skipped", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            if (plan.rejected > 0) Text("${plan.rejected} left out", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            if (!plan.isEmpty) PixelButton("Add them", onAdd, Modifier.weight(1f), Pixel.Tone, compact = true)
            PixelButton(if (plan.isEmpty) "Close" else "Not now", onCancel, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
    }
}

private fun duplicateLine(model: AppModel, id: String, family: HealKindOf): String {
    val d = model.healData
    return when (family) {
        HealKindOf.ACTIVITY -> d.activities.firstOrNull { it.id == id }?.let { "${it.type.label}, ${it.minutes} min, ${it.startMillis.shortDate()}" }
        HealKindOf.SLEEP -> d.sleep.firstOrNull { it.id == id }?.let { "Sleep, ${it.minutes / 60} h ${it.minutes % 60} min, woke ${it.endMillis.shortDate()}" }
        HealKindOf.MEASUREMENTS -> d.measurements.firstOrNull { it.id == id }?.let { "${it.type.label} ${app.pokeplan.heal.HealRules.format(it.value)} ${it.unit}, ${it.atMillis.shortDate()}" }
        HealKindOf.HYDRATION -> d.hydration.firstOrNull { it.id == id }?.let { "Drink ${it.ml} ml, ${it.atMillis.shortDate()}" }
        HealKindOf.STEPS -> d.steps.firstOrNull { it.id == id }?.let { "Steps, ${it.updatedMillis.shortDate()}" }
        HealKindOf.VITALS -> d.vitals.firstOrNull { it.id == id }?.let { "${it.type.label}, ${it.atMillis.shortDate()}" }
    } ?: "A record"
}
