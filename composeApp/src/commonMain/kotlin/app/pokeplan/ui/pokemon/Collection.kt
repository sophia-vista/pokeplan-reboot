package app.pokeplan.ui.pokemon

import app.pokeplan.ui.Place
import app.pokeplan.ui.theme.PixelShape
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import app.pokeplan.app.PcPlace
import app.pokeplan.app.PcSort
import app.pokeplan.app.Places
import app.pokeplan.app.PcView
import app.pokeplan.app.quest.Quests
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.dex.Region
import app.pokeplan.core.pokemon.Guardians
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.reward.Feature
import app.pokeplan.core.reward.TrainerRank
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Tab
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.TypePlate
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.ToggleRow
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.quietClickable
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.RegionLook
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.plan.EmptyState
import app.pokeplan.ui.screens.MenuEntry
import app.pokeplan.ui.screens.MenuList
import app.pokeplan.ui.screens.QuestsView
import app.pokeplan.ui.screens.ScreenSwitch
import app.pokeplan.ui.screens.SectionScaffold
import app.pokeplan.ui.screens.TabButton
import app.pokeplan.ui.screens.FormLabel
import app.pokeplan.ui.screens.PixelDropdown
import app.pokeplan.ui.screens.label
import app.pokeplan.ui.screens.long
import app.pokeplan.ui.screens.short

private enum class Section(val title: String, val icon: String) {
    PARTY("Party", "poke-ball"), PC("PC boxes", "storage-key"), DEX("Pokédex", "great-ball"), BAG("Bag", "seal-bag"),
    MART("Poké Mart", "amulet-coin"), QUESTS("Quests", "oaks-letter"),
}

private data class CollectionPage(val section: Section?, val dexEntry: Int?, val pokemon: String?) {
    val depth get() = listOfNotNull(section, dexEntry, pokemon).size
}

@Composable
fun CollectionScreen(
    model: AppModel, startSection: Int = -1, startOpen: String? = null, startParty: Boolean = false, startPcEveryone: Boolean = false,
    /** Where Back goes after a Pokémon opened from another tab (Home's buddy chip, a module): back there. */
    onReturnToTab: (Tab) -> Unit = {},
    /** Counts second taps on the Pokémon tab; a new count returns the tab to its menu. */
    resetSignal: Int = 0,
) {
    // Saved, so a section, an open Pokémon or a Pokédex entry is still there after a tab switch (or the app being put away).
    var section by rememberSaveable { mutableStateOf(if (startParty) Section.PARTY else Section.entries.getOrNull(startSection)) }
    var selected by rememberSaveable { mutableStateOf(startOpen) }
    var pcView by remember { mutableStateOf(PcView(everyone = startPcEveryone)) }
    var dexView by remember { mutableStateOf(DexView()) }
    var dexEntry by rememberSaveable { mutableStateOf<Int?>(null) }
    // A jump from outside (Home's buddy chip, a widget, the Party shortcut) wins over the place the tab was left in. It is read
    // here rather than passed in once, so it also works while the tab is already open; the jump is used once.
    LaunchedEffect(model.openPokemonId) { model.openPokemonId?.let { selected = it; model.openPokemonId = null } }
    LaunchedEffect(model.openParty) { if (model.openParty) { section = Section.PARTY; model.openParty = false } }
    // A second tap on the Pokémon tab returns to its menu (the shell counts the taps so a restored place is not reset).
    var handledReset by rememberSaveable { mutableStateOf(resetSignal) }
    LaunchedEffect(resetSignal) { if (resetSignal != handledReset) { handledReset = resetSignal; section = null; selected = null; dexEntry = null } }
    val player = model.data.player
    val store = model.store

    ScreenSwitch(CollectionPage(section, dexEntry, selected), depth = { it.depth }) { page ->
        val open = page.pokemon?.let { id -> player.pokemon.firstOrNull { it.id == id } }
        if (open != null) {
            PokemonDetail(model, open) {
                selected = null
                // A Pokémon opened from another tab goes back there, not to the Pokémon menu.
                model.pokemonReturnTab?.let { tab -> model.pokemonReturnTab = null; onReturnToTab(tab) }
            }
            return@ScreenSwitch
        }

        page.dexEntry?.let { id ->
            DexEntryScreen(model, remember(dexView) { dexView.rows() }, id, open = { dexEntry = it }) { dexEntry = null }
            return@ScreenSwitch
        }

        val current = page.section
        if (current == null) {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader("Pokémon", icon = "poke-ball")
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.M)) {
                    val ready = store.activeQuests.count { store.questReady(it) }
                    MenuList(
                        listOfNotNull(
                            MenuEntry("PARTY", "premier-ball", "Party", count = "${player.party.size} of 6"),
                            MenuEntry("PC", "storage-key", "PC boxes", count = "${player.pokemon.count { inPc(store, it) }}"),
                            MenuEntry("DEX", "great-ball", "Pokédex", count = "${player.caught.size} caught"),
                            if (store.sees(Feature.BAG)) MenuEntry("BAG", "seal-bag", "Bag", count = "${player.inventory.counts.count { it.value > 0 }}") else null,
                            if (store.sees(Feature.COINS)) MenuEntry("MART", "amulet-coin", "Poké Mart", count = "${player.coins} Poké") else null,
                            if (!store.sees(Feature.LEGENDARY_QUESTS)) null else MenuEntry("QUESTS", "oaks-letter", "Quests", count = if (store.activeQuests.isEmpty()) "" else "${store.activeQuests.size}", badge = ready),
                        ),
                    ) { key -> section = Section.valueOf(key) }
                }
            }
            return@ScreenSwitch
        }

        SectionScaffold(current.title, null, onBack = { section = null }, icon = current.icon) {
            when (current) {
                Section.PARTY -> {
                    val regions = listOfNotNull(Region.PLAN, Region.CARE.takeIf { model.data.settings.careModule }, Region.HEAL, Region.MINT, Region.BASE, Region.LINK)
                    var shown by remember { mutableStateOf(Region.PLAN) }
                    val current = shown.takeIf { it in regions } ?: Region.PLAN
                    val at = regions.indexOf(current)
                    PartySwitcher(model, regions, current, { r -> partyOf(player, r) }, { r -> leadOf(player, r) }, ::accentOf) { shown = it }
                    Column(Modifier.fillMaxWidth().swipeBetween(current, { shown = regions[(at - 1 + regions.size) % regions.size] }, { shown = regions[(at + 1) % regions.size] }), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        PartyGroup(model, current, partyOf(player, current), leadOf(player, current)?.id, "Lead", { PerksOf(model, current) }) { selected = it }
                        GuardianSlot(model, current, accentOf(current))
                    }
                }
                Section.PC -> {
                    val places = remember(model.data) { PcPlace.all(store.data) }
                    val placeOf = { id: String -> places[id] ?: PcPlace.PC }
                    val inBox = player.pokemon.count { placeOf(it.id) == PcPlace.PC }
                    if (player.pokemon.isEmpty()) EmptyState("The PC is empty", icon = "storage-key", action = "Start a focus session" to { model.open(Place.FOCUS, true) })
                    else {
                        val total = if (pcView.everyone) player.pokemon.size else inBox
                        val shown = pcView.apply(player.pokemon, placeOf) { store.isFavorite(it) }
                        val boxes = pcView.boxCount(shown.size)
                        val at = pcView.box.coerceIn(0, boxes - 1)
                        PcControls(pcView, { pcView = it }, shown.size, total)
                        if (!pcView.everyone && inBox == 0) EmptyState("The PC is empty", icon = "storage-key", action = "Show all Pokémon" to { pcView = pcView.copy(everyone = true, box = 0) })
                        else if (shown.isEmpty()) EmptyState("Nothing matches", action = "Clear filters" to { pcView = pcView.cleared() })
                        else {
                            if (boxes > 1) PcBoxBar(at, boxes) { pcView = pcView.copy(box = it) }
                            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                                pcView.page(shown).forEach { p ->
                                    val where = if (pcView.everyone) Places.short(store.data, p.id, placeOf(p.id) == PcPlace.LEAD) else null
                                    PcTile(p, store.isFavorite(p.id), where, tall = pcView.everyone) { selected = p.id }
                                }
                            }
                            if (boxes > 1) PcBoxBar(at, boxes) { pcView = pcView.copy(box = it) }
                        }
                    }
                }
                Section.DEX -> PokedexView(model, dexView, { dexView = it }) { dexEntry = it }
                Section.BAG -> BagView(model)
                Section.MART -> MartView(model)
                Section.QUESTS -> QuestsView(model)
            }
        }
    }
}

private fun partyOf(player: app.pokeplan.core.reward.PlayerState, region: Region): List<OwnedPokemon> = when (region) {
    Region.CARE -> player.partyOf(PartyIds.CARE); Region.HEAL -> player.partyOf(PartyIds.HEAL); Region.MINT -> player.partyOf(PartyIds.MINT); Region.BASE -> player.partyOf(PartyIds.BASE); Region.LINK -> player.partyOf(PartyIds.LINK); else -> player.party
}

private fun leadOf(player: app.pokeplan.core.reward.PlayerState, region: Region): OwnedPokemon? = when (region) {
    Region.CARE -> player.careLead; Region.HEAL -> player.healLead; Region.MINT -> player.mintLead; Region.BASE -> player.baseLead; Region.LINK -> player.linkLead; else -> player.planLead
}

private fun accentOf(region: Region) = when (region) { Region.CARE -> RegionLook.CARE; Region.HEAL -> RegionLook.HEAL; Region.MINT -> RegionLook.MINT; Region.BASE -> RegionLook.BASE; Region.LINK -> RegionLook.LINK; else -> RegionLook.PLAN }.accent

@Composable
private fun PerksOf(model: AppModel, region: Region) = when (region) {
    Region.CARE -> CarePerksCard(model); Region.HEAL -> HealPerksCard(model); Region.MINT -> MintPerksCard(model); Region.BASE -> BasePerksCard(model); Region.LINK -> LinkPerksCard(model); else -> PartyPerksCard(model)
}

@Composable
private fun PartyGroup(
    model: AppModel, region: Region, party: List<OwnedPokemon>, leadId: String?, leadLabel: String,
    perks: @Composable () -> Unit, open: (String) -> Unit,
) {
    var showPerks by remember(region) { mutableStateOf(false) }
    if (party.isEmpty()) {
        EmptyState("No one here yet", icon = "poke-ball", sleeper = true, action = "Start a focus session" to { model.open(Place.FOCUS, true) })
        return
    }
    PartyGrid(model, party, leadId, leadLabel, open, tapOpens = true)
    PixelButton(if (showPerks) "Hide perks" else "Perks", { showPerks = !showPerks }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
    if (showPerks) perks()
}

@Composable
private fun PcControls(view: PcView, onChange: (PcView) -> Unit, shown: Int, total: Int) {
    val change = { v: PcView -> onChange(v.copy(box = 0)) }
    Column(Modifier.padding(bottom = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        PixelField(
            view.search, { change(view.copy(search = it.take(20))) }, Modifier.fillMaxWidth(), singleLine = true,
            label = "Search by name",
        )
        ToggleRow("Show all Pokémon", view.everyone) { change(view.copy(everyone = it, where = null)) }
        var open by remember { mutableStateOf(false) }
        PixelButton(if (open) "Hide sort and filters" else "Sort and filters", { open = !open }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        if (open) {
            PixelDropdown(PcSort.entries, view.sort, { it?.label ?: "" }, { s -> if (s != null) change(view.copy(sort = s)) }, Modifier.fillMaxWidth(), label = "Sort by")
            if (view.everyone) {
                PixelDropdown(listOf<PcPlace?>(null) + PcPlace.entries, view.where, { it?.label ?: "All" }, { change(view.copy(where = it)) }, Modifier.fillMaxWidth(), label = "Where")
            }
            FormLabel("Show")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                TabButton("Shiny", view.shinyOnly, { change(view.copy(shinyOnly = !view.shinyOnly)) })
                TabButton("Starred", view.starredOnly, { change(view.copy(starredOnly = !view.starredOnly)) })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                PokemonType.entries.forEach { t ->
                    val on = view.type == t
                    Box(
                        Modifier.clip(PixelShape).alpha(if (on || view.type == null) 1f else 0.35f)
                            .clickable(role = Role.Button, onClickLabel = "Show only ${t.label}") { change(view.copy(type = if (on) null else t)) }
                            .padding(2.dp),
                    ) { TypePlate(t, width = 56.dp) }
                }
            }
        }
        if (view.filtering) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("$shown of $total", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft)
            PixelButton("Clear", { onChange(view.cleared()) }, color = Pixel.InkSoft, compact = true)
        }
    }
}

@Composable
private fun PcBoxBar(at: Int, boxes: Int, go: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.XS), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
        PixelIconButton("arrow-left", "Previous", { go(at - 1) }, Pixel.InkSoft, enabled = at > 0)
        Text("Box ${at + 1} of $boxes", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        PixelIconButton("arrow-right", "Next", { go(at + 1) }, Pixel.InkSoft, enabled = at < boxes - 1)
    }
}

@Composable
private fun PcTile(p: OwnedPokemon, favorite: Boolean, where: String?, tall: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.size(width = 92.dp, height = if (tall) 152.dp else 112.dp).clip(PixelShape).background(Pixel.Panel)
            .quietClickable(onClickLabel = "Open ${p.nickname}", onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${p.nickname}, level ${p.level}" + (if (p.isShiny) ", shiny" else "") + (if (favorite) ", starred" else "") +
                    (where?.let { ", $it" } ?: "")
            }
            .padding(horizontal = Spacing.XS, vertical = Spacing.S),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Sprite(p.form.id, p.isShiny, "", size = 64.dp)
        Text(p.nickname, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "Lv. ${p.level}" + (if (p.isShiny) " · Shiny" else "") + (if (favorite) " · Starred" else ""),
            style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft, maxLines = 1,
        )
        if (where != null) Text(where, style = MaterialTheme.typography.labelMedium, color = Pixel.Blue, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

internal val statNames = listOf("HP", "Attack", "Defense", "Sp. Atk", "Sp. Def", "Speed")

private fun inPc(store: app.pokeplan.app.GameStore, p: OwnedPokemon) = app.pokeplan.app.Places.of(store.data, p.id) == app.pokeplan.app.Place.Pc
