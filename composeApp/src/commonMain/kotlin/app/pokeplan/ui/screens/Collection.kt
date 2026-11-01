package app.pokeplan.ui.screens

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import app.pokeplan.app.PcSort
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
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.TypePlate
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.quietClickable
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.RegionLook
import app.pokeplan.ui.theme.SectionLabel

private enum class Section(val title: String, val icon: String) {
    PARTY("Party", "poke-ball"), PC("PC boxes", "storage-key"), DEX("Pokédex", "great-ball"), BAG("Bag", "seal-bag"),
    GUARDIANS("Guardians", "master-ball"), QUESTS("Quests", "oaks-letter"),
}

/** What the Pokémon tab shows: its menu, a section, a Pokédex entry or one Pokémon; each one opened goes a level deeper. */
private data class CollectionPage(val section: Section?, val dexEntry: Int?, val pokemon: String?) {
    val depth get() = listOfNotNull(section, dexEntry, pokemon).size
}

@Composable
fun CollectionScreen(model: AppModel, startSection: Int = -1, startOpen: String? = null, startParty: Boolean = false) {
    var section by remember { mutableStateOf(if (startParty) Section.PARTY else Section.entries.getOrNull(startSection)) }
    var selected by remember { mutableStateOf(startOpen) }
    // Kept here, not in the PC section, so opening a Pokémon and coming back leaves the sort and filters as they were.
    var pcView by remember { mutableStateOf(PcView()) }
    var dexView by remember { mutableStateOf(DexView()) }
    var dexEntry by remember { mutableStateOf<Int?>(null) }
    val player = model.data.player
    val store = model.store

    // Deeper pages (a section, a Pokédex entry, a Pokémon) slide in and back out; one entry to the next crossfades.
    ScreenSwitch(CollectionPage(section, dexEntry, selected), depth = { it.depth }) { page ->
        val open = page.pokemon?.let { id -> player.pokemon.firstOrNull { it.id == id } }
        if (open != null) {
            PokemonDetail(model, open) { selected = null }
            return@ScreenSwitch
        }

        page.dexEntry?.let { id ->
            DexEntryScreen(model, remember(dexView) { dexView.rows() }, id, open = { dexEntry = it }) { dexEntry = null }
            return@ScreenSwitch
        }

        val current = page.section
        if (current == null) {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader("Pokémon", subtitle = "${player.pokemon.size} owned, ${player.caught.size} seen", icon = "poke-ball")
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.M)) {
                    val ready = store.activeQuests.count { store.questReady(it) }
                    // A new player has one Pokémon: say where the next ones come from.
                    if (player.pokemon.size <= 1) {
                        TextBox(Modifier.padding(bottom = Spacing.M)) {
                            Text(
                                "Your collection grows as you go: finish a focus session or your tasks and eggs hatch into new Pokémon, and Care and World bring their own.",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    MenuGrid(
                        listOfNotNull(
                            MenuEntry("PARTY", "premier-ball", "Party", "${player.party.size} of 6", tint = Pixel.Red),
                            MenuEntry("PC", "storage-key", "PC boxes", "${player.pokemon.count { !it.inParty && !Guardians.canBe(it) }} stored", tint = Pixel.Warning),
                            MenuEntry("DEX", "great-ball", "Pokédex", "${player.caught.size} caught", tint = Pixel.Blue),
                            if (store.sees(Feature.BAG)) MenuEntry("BAG", "seal-bag", "Bag", "${player.inventory.counts.count { it.value > 0 }} items", tint = Pixel.Green) else null,
                            if (!store.sees(Feature.GUARDIANS)) null else MenuEntry("GUARDIANS", "master-ball", "Guardians", if (store.guardianSlots == 0) "opens at rank ${TrainerRank.names[4]}" else "${player.guardians.size} of ${store.guardianSlots} slotted", tint = Pixel.Violet),
                            if (!store.sees(Feature.LEGENDARY_QUESTS)) null else MenuEntry("QUESTS", "oaks-letter", "Quests", "${store.activeQuests.size} active", badge = ready, tint = Pixel.Warning),
                        ),
                    ) { key -> section = Section.valueOf(key) }
                }
            }
            return@ScreenSwitch
        }

        SectionScaffold(current.title, "Pokémon", onBack = { section = null }, icon = current.icon) {
            when (current) {
                Section.PARTY -> {
                    // Every region's party on one page, each under its region with its lead first and its perks after
                    // (docs/design.md, "Parties per module"): one collection, a party per module.
                    PartyGroup(model, Region.PLAN, player.party, player.buddyId, "Buddy", { PartyPerksCard(model) }) { selected = it }
                    if (model.data.settings.careModule) {
                        PartyGroup(model, Region.CARE, player.partyOf(PartyIds.CARE), player.careLeadId, "Lead", { CarePerksCard(model) }) { selected = it }
                    }
                }
                Section.PC -> {
                    // One PC for every region's party (docs/design.md, "Parties per module"), in boxes of 30 like the games.
                    val box = player.pokemon.filter { !it.inParty && !Guardians.canBe(it) }
                    if (box.isEmpty()) EmptyState("The PC is empty", "New eggs go to their region's party first, then here once it is full. One PC serves every region.", icon = "storage-key")
                    else {
                        val shown = pcView.apply(box) { store.isFavorite(it) }
                        val boxes = pcView.boxCount(shown.size)
                        val at = pcView.box.coerceIn(0, boxes - 1)
                        PcControls(pcView, { pcView = it }, shown.size, box.size)
                        if (shown.isEmpty()) EmptyState("Nothing matches", "Try another type or clear the filters.")
                        else {
                            if (boxes > 1) PcBoxBar(at, boxes) { pcView = pcView.copy(box = it) }
                            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                                pcView.page(shown).forEach { p -> PcTile(p, store.isFavorite(p.id)) { selected = p.id } }
                            }
                            if (boxes > 1) PcBoxBar(at, boxes) { pcView = pcView.copy(box = it) }
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
}

/** One region's party: its name and "x of 6", the lead first, then the rest, then the party's perks. */
@Composable
private fun PartyGroup(
    model: AppModel, region: Region, party: List<OwnedPokemon>, leadId: String?, leadLabel: String,
    perks: @Composable () -> Unit, open: (String) -> Unit,
) {
    val store = model.store
    // The region's colour beside its name, as down the edge of its card on Home; the name says which region it is.
    val accent = (if (region == Region.CARE) RegionLook.CARE else RegionLook.PLAN).accent
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Box(Modifier.padding(bottom = 2.dp).size(width = 4.dp, height = 20.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        SectionLabel("${region.label} · ${party.size} of ${OwnedPokemon.PARTY_SIZE}")
    }
    if (party.isEmpty()) {
        val how = if (region == Region.CARE) "Open a Pokémon and choose \"To Care party\", or befriend a visitor. Until then Care warms your Plan buddy. Its first member leads."
        else "Hatch an egg with a focus session, or move a Pokémon here from the PC."
        EmptyState("No one here yet", how, icon = "poke-ball")
        return
    }
    party.sortedByDescending { it.id == leadId }.forEach { p ->
        PokemonRow(p, if (p.id == leadId) leadLabel else null, store.isFavorite(p.id)) { open(p.id) }
    }
    perks()
}

/** A Pokémon in a list: its sprite, its name, and one quiet line (level and types), with its role and marks at the end. */
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
                if (p.isShiny) Text("✨ Shiny", style = MaterialTheme.typography.labelMedium, color = Pixel.Warning)
            }
        },
    ) {
        Text(p.nickname, style = MaterialTheme.typography.titleMedium)
        val types = p.form.types.joinToString(" / ") { it.label }
        Text("Lv. ${p.level} · $types", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    }
}

/** Search, sort and filters for the PC boxes. A type plate is dimmed until chosen; choosing it again clears it. */
@Composable
private fun PcControls(view: PcView, onChange: (PcView) -> Unit, shown: Int, total: Int) {
    // Any new search, sort or filter starts again from the first box.
    val change = { v: PcView -> onChange(v.copy(box = 0)) }
    Column(Modifier.padding(bottom = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        PixelField(
            view.search, { change(view.copy(search = it.take(20))) }, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = "Search by name",
        )
        var open by remember { mutableStateOf(false) }
        // Quiet: the boxes are the page, the controls only help find one.
        PixelButton(if (open) "Hide sort and filters" else "Sort and filters", { open = !open }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        if (open) {
            SectionLabel("Sort by")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                PcSort.entries.forEach { TabButton(it.label, view.sort == it, { change(view.copy(sort = it)) }) }
            }
            SectionLabel("Show")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                TabButton("✨ Shiny", view.shinyOnly, { change(view.copy(shinyOnly = !view.shinyOnly)) })
                TabButton("★ Starred", view.starredOnly, { change(view.copy(starredOnly = !view.starredOnly)) })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
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
            Text(
                (if (view.filtering) "$shown of $total" else "$total stored") + " · by ${view.sort.label}",
                Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft,
            )
            if (view.filtering) PixelButton("Clear", { change(view.copy(type = null, shinyOnly = false, starredOnly = false, search = "")) }, color = Pixel.InkSoft, compact = true)
        }
    }
}

/** Previous and Next between the PC's boxes, with "Box 2 of 5" between them. */
@Composable
private fun PcBoxBar(at: Int, boxes: Int, go: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.XS), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
        PixelButton("Previous", { go(at - 1) }, Modifier.weight(1f), Pixel.InkSoft, enabled = at > 0, compact = true)
        Text("Box ${at + 1} of $boxes", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        PixelButton("Next", { go(at + 1) }, Modifier.weight(1f), Pixel.InkSoft, enabled = at < boxes - 1, compact = true)
    }
}

/** A Pokémon in a box: its sprite, its name and one quiet line (level, with Shiny and Starred in words for screen readers). */
@Composable
private fun PcTile(p: OwnedPokemon, favorite: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.size(width = 92.dp, height = 112.dp).clip(RoundedCornerShape(12.dp)).background(Pixel.Panel)
            .quietClickable(onClickLabel = "Open ${p.nickname}", onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${p.nickname}, level ${p.level}" + (if (p.isShiny) ", shiny" else "") + (if (favorite) ", starred" else "")
            }
            .padding(horizontal = Spacing.XS, vertical = Spacing.S),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Sprite(p.form.id, p.isShiny, "", size = 64.dp)
        Text(p.nickname, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "Lv. ${p.level}" + (if (p.isShiny) " ✨" else "") + (if (favorite) " ★" else ""),
            style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft, maxLines = 1,
        )
    }
}

// ----- Pokédex -------------------------------------------------------------------------------------------------------

/** "+10% Super Candy chance" for a Guardian's signature perk(s). */
internal fun perkText(p: OwnedPokemon): String =
    Guardians.signature(p).entries.joinToString(", ") { (b, v) -> "+${v.toInt()}% ${b.label()}" }

internal val statNames = listOf("HP", "Attack", "Defense", "Sp. Atk", "Sp. Def", "Speed")
