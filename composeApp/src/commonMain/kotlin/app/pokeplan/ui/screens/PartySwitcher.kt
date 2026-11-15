package app.pokeplan.ui.screens

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

/** The party id (`PartyIds`) of a module's region: where its party, its lead and its Guardian are kept. */
internal fun partyIdOf(region: Region): String = when (region) {
    Region.CARE -> PartyIds.CARE; Region.HEAL -> PartyIds.HEAL; Region.MINT -> PartyIds.MINT; Region.BASE -> PartyIds.BASE; Region.LINK -> PartyIds.LINK; else -> PartyIds.PLAN
}

/** A module's short name: "Plan", "Care", "Heal", "Mint", "Base" or "Link". */
internal fun moduleName(region: Region): String = if (region == Region.BASE) "Base" else region.label.removeSuffix(" Region")

/** A module's short name from its party id, for the detail page and the Guardian lists. */
internal fun moduleNameOf(partyId: String): String = partyId.replaceFirstChar { it.uppercase() }

/**
 * Chooses which module's party to look at. Six names do not fit as tabs on a phone (they came out as "Pl...", "Ca..."), and a dropdown would hide the choices, so it works
 * the way the games' box screens do: the module's full name and count in its own colour between two arrows that go to the one before and the one after (they wrap round),
 * six small squares for where you are (and a swipe along the party does the same), and a tap on the name opens all six in a list at once, with their leads and Guardians,
 * to jump to one. Nothing is only in the colour: the name and the count are always written.
 */
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
        // Where you are among the six: a square each, filled for the open one.
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

/** A swipe sideways over [content] goes to the next module's party (left) or the one before (right), like the games' box screens; the arrows do the same. */
internal fun Modifier.swipeBetween(key: Any?, onPrevious: () -> Unit, onNext: () -> Unit): Modifier = pointerInput(key) {
    var travelled = 0f
    detectHorizontalDragGestures(
        onDragStart = { travelled = 0f },
        onDragEnd = { if (travelled > SWIPE_PX) onPrevious() else if (travelled < -SWIPE_PX) onNext() },
        onHorizontalDrag = { _, dx -> travelled += dx },
    )
}

private const val SWIPE_PX = 120f

/** What [p] gives [partyId]'s module as its Guardian, in words: "+10% Visitor luck, +5% Berry finder". */
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

/** On a Guardian's own page: which module it is the Guardian of (a chip each, the one it holds filled; a tap sets it, a tap on the filled one empties the slot). */
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

/**
 * A module's Guardian slot, under its party: who it is and what it gives this module, with a button to choose or change it from the legendaries and mythicals owned. A Guardian is
 * a normal Pokémon (it is in the PC, can be in any party or World team) that can also be one module's Guardian; choosing it takes it from where it was. Hidden until the player
 * owns one.
 */
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
