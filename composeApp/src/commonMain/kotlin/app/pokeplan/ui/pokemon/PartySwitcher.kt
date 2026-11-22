package app.pokeplan.ui.pokemon

import app.pokeplan.ui.theme.Sizes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pokeplan.app.Places
import app.pokeplan.core.dex.Region
import app.pokeplan.core.pokemon.CareBucket
import app.pokeplan.core.pokemon.CarePerks
import app.pokeplan.core.pokemon.Guardians
import app.pokeplan.core.pokemon.HealPerks
import app.pokeplan.core.pokemon.BasePerks
import app.pokeplan.core.pokemon.LinkPerks
import app.pokeplan.core.pokemon.MintPerks
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.screens.TabButton
import app.pokeplan.ui.screens.label
import app.pokeplan.ui.screens.short

internal fun partyIdOf(region: Region): String = when (region) {
    Region.CARE -> PartyIds.CARE; Region.HEAL -> PartyIds.HEAL; Region.MINT -> PartyIds.MINT; Region.BASE -> PartyIds.BASE; Region.LINK -> PartyIds.LINK; else -> PartyIds.PLAN
}

internal fun moduleName(region: Region): String = if (region == Region.BASE) "Base" else region.label.removeSuffix(" Region")

internal fun moduleNameOf(partyId: String): String = partyId.replaceFirstChar { it.uppercase() }

@Composable
internal fun PartySwitcher(
    model: AppModel, regions: List<Region>, selected: Region, party: (Region) -> List<OwnedPokemon>, lead: (Region) -> OwnedPokemon?,
    accent: (Region) -> Color, onSelect: (Region) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val at = regions.indexOf(selected).coerceAtLeast(0)
    fun go(by: Int) { onSelect(regions[(at + by + regions.size) % regions.size]); open = false }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            PixelIconButton("arrow-left", "Previous module's party", { go(-1) }, Pixel.InkSoft)
            TabButton(
                "${moduleName(selected)} party · ${party(selected).size} of 6", true, { open = !open }, Modifier.weight(1f).semantics { contentDescription = "${moduleName(selected)} party, ${party(selected).size} of 6. Opens the list of parties." },
                key = accent(selected),
            )
            PixelIconButton("arrow-right", "Next module's party", { go(1) }, Pixel.InkSoft)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S, Alignment.CenterHorizontally)) {
            regions.forEach { r ->
                val on = r == selected
                Box(Modifier.size(10.dp).background(if (on) accent(r) else Color.Transparent).border(Sizes.Line, accent(r)))
            }
        }
        if (open) {
            regions.forEach { r ->
                val members = party(r)
                val guardian = model.data.player.guardianOf(partyIdOf(r))
                ListRow(
                    lead = { (lead(r) ?: members.firstOrNull())?.let { Sprite(it.form.id, it.isShiny, "", size = Sizes.IconL) } ?: Box(Modifier.size(Sizes.IconL)) },
                    onClick = { onSelect(r); open = false },
                ) {
                    Text(moduleName(r) + if (r == selected) " (open)" else "", style = MaterialTheme.typography.titleMedium, color = accent(r))
                    Text(
                        "${members.size} of 6" + (guardian?.let { " · Guardian ${it.nickname}" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
                    )
                }
            }
        }
    }
}

internal fun Modifier.swipeBetween(key: Any?, onPrevious: () -> Unit, onNext: () -> Unit): Modifier = pointerInput(key) {
    var travelled = 0f
    detectHorizontalDragGestures(
        onDragStart = { travelled = 0f },
        onDragEnd = { if (travelled > SWIPE_PX) onPrevious() else if (travelled < -SWIPE_PX) onNext() },
        onHorizontalDrag = { _, dx -> travelled += dx },
    )
}

private const val SWIPE_PX = 120f

internal fun guardianPerkText(p: OwnedPokemon, partyId: String): String {
    fun <B> words(map: Map<B, Double>, label: (B) -> String) = map.entries.joinToString(", ") { (b, v) -> "+${v.toInt()}% ${label(b)}" }
    return when (partyId) {
        PartyIds.CARE -> words(Guardians.signatureIn(p, CarePerks::bucket)) { it.label }
        PartyIds.HEAL -> words(Guardians.signatureIn(p, HealPerks::bucket)) { it.label }
        PartyIds.MINT -> words(Guardians.signatureIn(p, MintPerks::bucket)) { it.label }
        PartyIds.BASE -> words(Guardians.signatureIn(p, BasePerks::bucket)) { it.label }
        PartyIds.LINK -> words(Guardians.signatureIn(p, LinkPerks::bucket)) { it.label }
        else -> words(Guardians.signature(p)) { it.label() }
    }.ifEmpty { "no perk in this module" }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun GuardianChoice(model: AppModel, p: OwnedPokemon) {
    val store = model.store
    SectionLabel("Guardian of")
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Guardians.MODULES.forEach { module ->
            val holds = model.data.player.guardianOf(module)?.id == p.id
            TabButton(moduleNameOf(module), holds, { if (holds) store.unslotGuardian(module) else store.slotGuardian(p.id, module) })
        }
    }
    if (model.data.player.moduleGuardians.containsValue(p.id)) Text(guardianPerkText(p, model.data.player.moduleGuardians.entries.first { it.value == p.id }.key), style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
}

@Composable
internal fun GuardianSlot(model: AppModel, region: Region, accent: Color) {
    val store = model.store
    val partyId = partyIdOf(region)
    val current = model.data.player.guardianOf(partyId)
    val candidates = store.guardianCandidates.filter { it.id != current?.id }
    if (current == null && candidates.isEmpty()) return
    var picking by remember(partyId) { mutableStateOf(false) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            SectionLabel("${moduleName(region)}'s Guardian")
            if (current != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M), verticalAlignment = Alignment.CenterVertically) {
                    Sprite(current.form.id, current.isShiny, current.nickname, size = Sizes.SpriteS)
                    Column(Modifier.weight(1f)) {
                        Text(current.nickname, style = MaterialTheme.typography.titleMedium)
                        Text(guardianPerkText(current, partyId), style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
                    }
                }
            } else {
                Text("Empty", style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                if (candidates.isNotEmpty()) PixelButton(if (picking) "Hide the list" else if (current == null) "Choose a Guardian" else "Change", { picking = !picking }, Modifier.weight(1f), accent, compact = true)
                if (current != null) PixelButton("Remove", { store.unslotGuardian(partyId) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
            }
            if (picking) candidates.forEach { c ->
                ListRow(
                    lead = { Sprite(c.form.id, c.isShiny, c.nickname, size = Sizes.IconL) },
                    onClick = { store.slotGuardian(c.id, partyId); picking = false },
                ) {
                    Text(c.nickname, style = MaterialTheme.typography.titleMedium)
                    Text(guardianPerkText(c, partyId) + " · " + Places.label(model.data, c.id), style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                }
            }
        }
    }
}
