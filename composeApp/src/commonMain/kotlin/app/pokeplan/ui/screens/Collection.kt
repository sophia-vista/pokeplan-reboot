package app.pokeplan.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import app.pokeplan.app.PcSort
import app.pokeplan.app.PcView
import app.pokeplan.app.quest.Quests
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.Guardians
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.reward.Feature
import app.pokeplan.core.reward.TrainerRank
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.TypePlate
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.quietClickable
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.SectionLabel

private enum class Section(val title: String, val icon: String) {
    PARTY("Party", "poke-ball"), PC("PC boxes", "storage-key"), DEX("Pokédex", "great-ball"), BAG("Bag", "seal-bag"),
    GUARDIANS("Guardians", "master-ball"), QUESTS("Quests", "oaks-letter"),
}

@Composable
fun CollectionScreen(model: AppModel, startSection: Int = -1, startOpen: String? = null) {
    var section by remember { mutableStateOf(Section.entries.getOrNull(startSection)) }
    var selected by remember { mutableStateOf(startOpen) }
    // Kept here, not in the PC section, so opening a Pokémon and coming back leaves the sort and filters as they were.
    var pcView by remember { mutableStateOf(PcView()) }
    var partyTab by remember { mutableStateOf(0) }
    var dexView by remember { mutableStateOf(DexView()) }
    var dexEntry by remember { mutableStateOf<Int?>(null) }
    val player = model.data.player
    val store = model.store

    val open = selected?.let { id -> player.pokemon.firstOrNull { it.id == id } }
    if (open != null) {
        PokemonDetail(model, open) { selected = null }
        return
    }

    dexEntry?.let { id ->
        DexEntryScreen(model, remember(dexView) { dexView.rows() }, id, open = { dexEntry = it }) { dexEntry = null }
        return
    }

    val current = section
    if (current == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Pokémon", subtitle = "${player.pokemon.size} owned, ${player.caught.size} seen", icon = "poke-ball")
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) {
                val ready = store.activeQuests.count { store.questReady(it) }
                MenuGrid(
                    listOfNotNull(
                        MenuEntry("PARTY", "poke-ball", "Party", "${player.party.size} of 6", tint = Pixel.Red),
                        MenuEntry("PC", "storage-key", "PC boxes", "${player.pokemon.count { !it.inParty && !Guardians.canBe(it) }} stored", tint = Pixel.Warning),
                        MenuEntry("DEX", "great-ball", "Pokédex", "${player.caught.size} caught", tint = Pixel.Blue),
                        if (store.sees(Feature.BAG)) MenuEntry("BAG", "seal-bag", "Bag", "${player.inventory.counts.count { it.value > 0 }} items", tint = Pixel.Green) else null,
                        if (!store.sees(Feature.GUARDIANS)) null else MenuEntry("GUARDIANS", "master-ball", "Guardians", if (store.guardianSlots == 0) "opens at rank ${TrainerRank.names[4]}" else "${player.guardians.size} of ${store.guardianSlots} slotted", tint = androidx.compose.ui.graphics.Color(0xFF8B5CF6)),
                        if (!store.sees(Feature.LEGENDARY_QUESTS)) null else MenuEntry("QUESTS", "oaks-letter", "Quests", "${store.activeQuests.size} active", badge = ready, tint = Pixel.Warning),
                    ),
                ) { key -> section = Section.valueOf(key) }
            }
        }
        return
    }

    SectionScaffold(current.title, "Pokémon", onBack = { section = null }, icon = current.icon) {
        when (current) {
            Section.PARTY -> {
                val careOn = model.data.settings.careModule
                val tab = if (careOn) partyTab else 0
                if (careOn) {
                    Segmented(listOf("Plan ${player.party.size}/6", "Care ${player.partyOf(PartyIds.CARE).size}/6"), tab) { partyTab = it }
                }
                if (tab == 0) {
                    if (player.party.isEmpty()) EmptyState("Your party is empty", "Hatch an egg with a focus session, or move a Pokémon here from the PC.", icon = "poke-ball")
                    else PartyPerksCard(model)
                    player.party.forEach { p -> PokemonRow(p, if (p.id == player.buddyId) "Buddy" else null, store.isFavorite(p.id)) { selected = p.id } }
                } else {
                    val care = player.partyOf(PartyIds.CARE)
                    if (care.isEmpty()) EmptyState("Care's party is empty", "Open a Pokémon and choose \"To Care party\". Until then Care warms your Plan buddy. Its first member leads.", icon = "poke-ball")
                    else CarePerksCard(model)
                    care.forEach { p -> PokemonRow(p, if (p.id == player.careLeadId) "Care lead" else null, store.isFavorite(p.id)) { selected = p.id } }
                }
            }
            Section.PC -> {
                val box = player.pokemon.filter { !it.inParty && !Guardians.canBe(it) }
                if (box.isEmpty()) EmptyState("The PC is empty", "New eggs go to your party first, then here once it is full.", icon = "storage-key")
                else {
                    val shown = pcView.apply(box) { store.isFavorite(it) }
                    PcControls(pcView, { pcView = it }, shown.size, box.size)
                    if (shown.isEmpty()) EmptyState("Nothing matches", "Try another type or clear the filters.")
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        shown.forEach { p -> PcTile(p, store.isFavorite(p.id)) { selected = p.id } }
                    }
                }
            }
            Section.DEX -> PokedexView(model, dexView, { dexView = it }) { dexEntry = it }
            Section.BAG -> BagView(model)
            Section.GUARDIANS -> GuardiansView(model) { selected = it }
            Section.QUESTS -> QuestsView(model)
        }
    }
}

@Composable
private fun PokemonRow(p: OwnedPokemon, role: String?, favorite: Boolean, onClick: () -> Unit) {
    ListRow(
        lead = { Sprite(p.form.id, p.isShiny, p.nickname, size = 64.dp) },
        onClick = onClick,
        onClickLabel = "Open ${p.nickname}",
        highlight = false,
        trail = {
            Column(horizontalAlignment = Alignment.End) {
                if (role != null) Text(role, style = MaterialTheme.typography.labelMedium, color = Pixel.Red)
                if (favorite) Text("★ Starred", style = MaterialTheme.typography.labelMedium, color = Pixel.Warning)
            }
        },
    ) {
        Text(p.nickname, style = MaterialTheme.typography.titleMedium)
        Text(if (p.isShiny) "Lv. ${p.level}, ✨ Shiny" else "Lv. ${p.level}", style = MaterialTheme.typography.bodyMedium, color = if (p.isShiny) Pixel.Warning else Pixel.InkSoft)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { p.form.types.forEach { TypePlate(it, width = 56.dp) } }
    }
}

/** Search, sort and filters for the PC boxes. A type plate is dimmed until chosen; choosing it again clears it. */
@Composable
private fun PcControls(view: PcView, change: (PcView) -> Unit, shown: Int, total: Int) {
    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            view.search, { change(view.copy(search = it.take(20))) }, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Search by name") }, textStyle = MaterialTheme.typography.bodyLarge,
        )
        var open by remember { mutableStateOf(false) }
        PixelButton(if (open) "Hide sort and filters" else "Sort and filters", { open = !open }, Modifier.fillMaxWidth(), Pixel.Blue, compact = true)
        if (open) {
            SectionLabel("Sort by")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PcSort.entries.forEach { TabButton(it.label, view.sort == it, { change(view.copy(sort = it)) }) }
            }
            SectionLabel("Show")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TabButton("✨ Shiny", view.shinyOnly, { change(view.copy(shinyOnly = !view.shinyOnly)) })
                TabButton("★ Starred", view.starredOnly, { change(view.copy(starredOnly = !view.starredOnly)) })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PokemonType.entries.forEach { t ->
                    val on = view.type == t
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp)).alpha(if (on || view.type == null) 1f else 0.35f)
                            .clickable(role = Role.Button, onClickLabel = "Show only ${t.label}") { change(view.copy(type = if (on) null else t)) }
                            .padding(2.dp),
                    ) { TypePlate(t, width = 56.dp) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (view.filtering) "$shown of $total" else "$total stored", style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft)
            if (view.filtering) PixelButton("Clear", { change(view.copy(type = null, shinyOnly = false, starredOnly = false, search = "")) }, color = Pixel.InkSoft, compact = true)
        }
    }
}

@Composable
private fun PcTile(p: OwnedPokemon, favorite: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).background(Pixel.Panel).quietClickable(onClickLabel = "Open ${p.nickname}", onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Sprite(p.form.id, p.isShiny, p.nickname, size = 64.dp)
        Text("Lv. ${p.level}" + (if (p.isShiny) " ✨" else "") + (if (favorite) " ★" else ""), style = MaterialTheme.typography.labelMedium)
    }
}

// ----- Pokédex -------------------------------------------------------------------------------------------------------

/** "+10% Super Candy chance" for a Guardian's signature perk(s). */
internal fun perkText(p: OwnedPokemon): String =
    Guardians.signature(p).entries.joinToString(", ") { (b, v) -> "+${v.toInt()}% ${b.label()}" }

internal val statNames = listOf("HP", "Attack", "Defense", "Sp. Atk", "Sp. Def", "Speed")
