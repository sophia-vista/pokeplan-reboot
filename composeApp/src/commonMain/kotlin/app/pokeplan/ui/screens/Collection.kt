package app.pokeplan.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import app.pokeplan.app.BuddyScene
import app.pokeplan.app.DexState
import app.pokeplan.app.FormDex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import app.pokeplan.app.PcSort
import app.pokeplan.app.PcView
import app.pokeplan.app.quest.Quest
import app.pokeplan.app.quest.Quests
import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.Region
import app.pokeplan.core.dex.FormInfo
import app.pokeplan.core.dex.SpeciesInfo
import app.pokeplan.core.pokemon.EvolutionRules
import app.pokeplan.core.model.PokemonType
import app.pokeplan.core.pokemon.CandyRules
import app.pokeplan.core.pokemon.Guardians
import app.pokeplan.core.pokemon.PerkBucket
import app.pokeplan.core.pokemon.ItemCategory
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.Nature
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.pokemon.Stat
import app.pokeplan.core.pokemon.StatCalculator
import app.pokeplan.core.pokemon.Transfer
import app.pokeplan.core.reward.Feature
import app.pokeplan.core.reward.TrainerRank
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.spriteCredit
import app.pokeplan.ui.portraitCredit
import app.pokeplan.ui.Portrait
import app.pokeplan.ui.SceneStage
import app.pokeplan.ui.Stage
import app.pokeplan.ui.TypePlate
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.NamePlate
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.quietClickable
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.SectionLabel
import app.pokeplan.ui.theme.TextBox

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
                if (player.party.isEmpty()) EmptyState("Your party is empty", "Hatch an egg with a focus session, or move a Pokémon here from the PC.", icon = "poke-ball")
                else if (store.sees(Feature.PERKS)) PartyPerksCard(model)
                player.party.forEach { p -> PokemonRow(p, p.id == player.buddyId, store.isFavorite(p.id)) { selected = p.id } }
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
private fun PokemonRow(p: OwnedPokemon, buddy: Boolean, favorite: Boolean, onClick: () -> Unit) {
    ListRow(
        lead = { Sprite(p.form.id, p.isShiny, p.nickname, size = 64.dp) },
        onClick = onClick,
        onClickLabel = "Open ${p.nickname}",
        highlight = false,
        trail = {
            Column(horizontalAlignment = Alignment.End) {
                if (buddy) Text("Buddy", style = MaterialTheme.typography.labelMedium, color = Pixel.Red)
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

/** The National Dex split by generation, with the national numbers each generation covers. */
private val generations = listOf(
    "Gen 1" to 1..151, "Gen 2" to 152..251, "Gen 3" to 252..386, "Gen 4" to 387..493, "Gen 5" to 494..649,
    "Gen 6" to 650..721, "Gen 7" to 722..809, "Gen 8" to 810..905, "Gen 9" to 906..1025,
)

private enum class DexFilter(val label: String) { ALL("All"), CAUGHT("Caught"), MISSING("Not caught") }

/** Which list the Pokédex shows, kept by the Pokémon tab so opening an entry and coming back leaves it as it was. */
private data class DexView(val region: Region = Region.HOME, val national: Boolean = false, val generation: Int = 0, val filter: DexFilter = DexFilter.ALL) {
    /** (number shown, species): own order from 1 for a region, the National Dex number for the National tab. */
    fun rows(): List<Pair<Int, SpeciesInfo>> =
        if (national) Dex.species.filter { it.id in generations[generation].second }.map { it.id to it }
        else Dex.regionDex(region).mapIndexed { i, s -> (i + 1) to s }

    val title: String get() = if (national) "National Dex, ${generations[generation].first}" else region.label
}

/** "#001", the games' three digits (four past 999). */
private fun dexNumber(n: Int) = "#" + n.toString().padStart(3, '0')

/**
 * The Pokédex. A region's list is numbered from 1 in its own order (the Home Region starts at 1 too); the National Dex tab
 * uses the real National Dex numbers, a generation at a time. Tapping a species found so far opens its entry.
 */
@Composable
private fun PokedexView(model: AppModel, view: DexView, change: (DexView) -> Unit, open: (Int) -> Unit) {
    val player = model.data.player
    val regions = TrainerRank.unlockedRegions(player.trainerPoints)
    val rows = remember(view.region, view.national, view.generation) { view.rows() }
    val states = rows.associate { (_, s) -> s.id to DexState.of(model.data, s.id) }
    val caught = states.values.count { it.caught }
    val seen = states.values.count { it == DexState.SEEN }
    val shiny = rows.count { (_, sp) -> sp.id in model.data.shinySpecies }
    val (formsHad, formsAll) = remember(model.data, rows) {
        val forms = rows.flatMap { (_, sp) -> Dex.formsOf(sp.id).filter { FormDex.counts(it) } }
        forms.count { FormDex.caught(model.data, it) } to forms.size
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        regions.forEach { r -> TabButton(r.label, !view.national && r == view.region, { change(view.copy(national = false, region = r)) }, Modifier.padding(0.dp)) }
        TabButton("National", view.national, { change(view.copy(national = true)) }, Modifier.padding(0.dp))
    }
    if (view.national) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        generations.forEachIndexed { i, (label, _) -> TabButton(label, i == view.generation, { change(view.copy(generation = i)) }, Modifier.padding(0.dp)) }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${view.title}: $caught of ${rows.size} caught", style = MaterialTheme.typography.titleMedium)
            PixelBar(caught.toFloat() / rows.size.coerceAtLeast(1), color = if (caught == rows.size) Pixel.Warning else Pixel.Red)
            if (seen > 0) Text("$seen more seen on a friend's buddy or in a quest", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            Text("✨ $shiny shiny found", style = MaterialTheme.typography.bodyMedium, color = if (shiny > 0) Pixel.Warning else Pixel.InkSoft)
            Text("Forms: $formsHad of $formsAll caught", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DexFilter.entries.forEach { f -> TabButton(f.label, f == view.filter, { change(view.copy(filter = f)) }, Modifier.padding(0.dp)) }
    }
    val shown = rows.filter { (_, s) ->
        when (view.filter) {
            DexFilter.ALL -> true
            DexFilter.CAUGHT -> states.getValue(s.id).caught
            DexFilter.MISSING -> !states.getValue(s.id).caught
        }
    }
    if (shown.isEmpty()) EmptyState(if (view.filter == DexFilter.CAUGHT) "None caught here yet" else "All caught!", if (view.filter == DexFilter.CAUGHT) "Hatch eggs in this region to fill it in." else "Every Pokémon in this list is yours.")
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        shown.forEach { (number, s) ->
            val state = states.getValue(s.id)
            // A seen species is named; one not found yet stays a mystery, so its name would give it away.
            val name = if (state == DexState.UNKNOWN) "Unknown" else s.name
            Column(
                Modifier.size(width = 76.dp, height = 104.dp).clip(RoundedCornerShape(12.dp)).background(Pixel.Panel)
                    .quietClickable(enabled = state != DexState.UNKNOWN, onClickLabel = "Open entry") { open(s.id) }
                    .semantics(mergeDescendants = true) { contentDescription = "Number $number, $name, ${state.label}" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    // Species not caught yet show as a grey shape: the real sprite, tinted.
                    Sprite(Dex.defaultForm(s.id).id, false, name, size = 64.dp, silhouette = if (state.caught) null else Pixel.Shade)
                    if (state == DexState.OWNED) ItemIcon("poke-ball", Modifier.align(Alignment.TopEnd), size = 20.dp, description = null)
                }
                Text(dexNumber(number) + if (s.id in model.data.shinySpecies) " ✨" else "", style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft)
                // Words, not just the sprite's colour, tell the states apart.
                Text(
                    if (state == DexState.UNKNOWN) "???" else state.label, style = MaterialTheme.typography.labelMedium,
                    color = if (state.caught) Pixel.Ink else Pixel.InkSoft, maxLines = 1,
                )
            }
        }
    }
    Text(
        "A Poké Ball marks the Pokémon in your party or PC now. Seen ones are named; tap one to see where it stands.",
        style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
    )
    Text(
        "About 1 in 128 eggs is shiny. Longer sessions and a Shiny hunter buddy raise the odds.",
        style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
    )
}

/** "0.7 m" from decimetres, "6.9 kg" from hectograms: the games keep both in tenths. */
private fun tenths(value: Int, unit: String) = "${value / 10}.${value % 10} $unit"

/**
 * One species' Pokédex page: its category, size and entry, types, abilities, base stats and evolution line, with Previous
 * and Next through the list it was opened from (skipping species not found yet). Seen species show only their shape.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun DexEntryScreen(model: AppModel, rows: List<Pair<Int, SpeciesInfo>>, id: Int, open: (Int) -> Unit, back: () -> Unit) {
    BackHandler(enabled = true, onBack = back)
    val s = Dex.species(id)
    // The form shown in the scene (one the player has had, picked under Forms) and whether forms show as shinies; the
    // shiny switch stays on through Next, the picked form does not.
    var picked by remember(id) { mutableStateOf<Int?>(null) }
    var shinyView by remember { mutableStateOf(false) }
    val form = picked?.let { p -> Dex.formsOf(id).firstOrNull { it.id == p } } ?: Dex.defaultForm(id)
    val shiny = shinyView && FormDex.shiny(model.data, form)
    val state = DexState.of(model.data, id)
    val number = rows.firstOrNull { it.second.id == id }?.first ?: id
    val found = rows.map { it.second.id }.filter { DexState.of(model.data, it) != DexState.UNKNOWN }
    val at = found.indexOf(id)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("${dexNumber(number)} ${s.name}", subtitle = if (state.caught) s.genus else state.label, icon = "great-ball") {
            PixelButton("Back", back, color = Pixel.InkSoft, compact = true)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // A caught species stands in a scene for its type (the buddy's scenes on Home); a seen one keeps the plain wallpaper,
            // so the scene does not give its type away.
            if (state.caught) SceneStage(BuddyScene.of(form.types), 232.dp) {
                AnimatedSprite(
                    form.id, shiny, form.name,
                    Modifier.align(Alignment.TopCenter).padding(top = 12.dp).quietClickable(onClickLabel = "Hear its cry") { model.cry(id) },
                    size = 160.dp, animate = !model.data.settings.reduceMotion,
                )
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    form.types.forEach { TypePlate(it, width = 84.dp) }
                }
            } else Stage("bw08", 232.dp) {
                Sprite(form.id, false, s.name, Modifier.align(Alignment.Center), size = 160.dp, silhouette = Pixel.Shade)
            }
            if (at >= 0 && found.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                PixelButton("Previous", { open(found[at - 1]) }, Modifier.weight(1f), Pixel.InkSoft, enabled = at > 0, compact = true)
                PixelButton("Next", { open(found[at + 1]) }, Modifier.weight(1f), Pixel.InkSoft, enabled = at < found.size - 1, compact = true)
            }

            if (state.caught) {
                FormsBox(model, id, form.id, shinyView, { shinyView = it }) { picked = it }
                CaughtEntry(model, s, form, open)
            }
            else TextBox { Text("Seen, not caught yet. Its entry opens when one is yours.", style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

/**
 * Every form of a species, as the form dex: the ones the player has had in colour, the rest as black shapes. Regular and
 * Shiny switch the whole row; tapping a form the player has had shows it in the scene above. Forms that cannot be had
 * in PokéPlan yet (Gigantamax and other special forms) are shown faded and are not counted.
 */
@Composable
private fun FormsBox(model: AppModel, speciesId: Int, shownId: Int, shiny: Boolean, setShiny: (Boolean) -> Unit, pick: (Int) -> Unit) {
    val forms = Dex.formsOf(speciesId)
    val counted = forms.filter { FormDex.counts(it) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (forms.size == 1) "Form" else "Forms", style = MaterialTheme.typography.titleMedium)
            Text(
                "${counted.count { FormDex.caught(model.data, it) }} of ${counted.size} caught, " +
                    "✨ ${counted.count { FormDex.shiny(model.data, it) }} of ${counted.size} shiny",
                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TabButton("Regular", !shiny, { setShiny(false) }, Modifier.padding(0.dp))
                TabButton("✨ Shiny", shiny, { setShiny(true) }, Modifier.padding(0.dp))
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                forms.forEach { f ->
                    val caught = FormDex.caught(model.data, f)
                    val found = if (shiny) FormDex.shiny(model.data, f) else caught
                    val canHave = FormDex.counts(f)
                    val name = if (caught) f.name else "???"
                    val shape = RoundedCornerShape(12.dp)
                    Column(
                        Modifier.size(width = 92.dp, height = 104.dp).clip(shape).background(Pixel.Panel)
                            .border(2.dp, if (f.id == shownId && caught) Pixel.Red else Pixel.Border, shape)
                            .quietClickable(enabled = caught, onClickLabel = "Show this form") { pick(f.id) }
                            .alpha(if (canHave) 1f else 0.45f)
                            .semantics(mergeDescendants = true) {
                                contentDescription = (if (caught) f.name else "Unknown form") + ", " +
                                    (if (!canHave) "not in PokéPlan yet" else if (found) "caught" else "not caught yet") + if (shiny) ", shiny" else ""
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // A form not had yet is its real sprite in black: the shape only.
                        Sprite(f.id, shiny && found, name, size = 64.dp, silhouette = if (found) null else Color.Black)
                        Text(
                            name, Modifier.padding(horizontal = 4.dp),
                            style = MaterialTheme.typography.labelMedium, color = if (found) Pixel.Ink else Pixel.InkSoft,
                            maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (counted.size < forms.size) Text(
                "Faded forms (Gigantamax and other special forms) can't be found in PokéPlan yet and don't count.",
                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
            )
        }
    }
}

/** What a caught species' entry tells: the games' Pokédex page plus its base stats and how its line evolves. */
@Composable
private fun CaughtEntry(model: AppModel, s: SpeciesInfo, form: FormInfo, open: (Int) -> Unit) {
    val id = s.id
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(s.genus, style = MaterialTheme.typography.titleMedium)
            Text("Height ${tenths(s.heightDm, "m")}, weight ${tenths(s.weightHg, "kg")}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            Text(Dex.entry(id), style = MaterialTheme.typography.bodyLarge)
            val owned = model.data.player.pokemon.count { it.speciesId == id }
            Text(
                if (owned > 0) "You have $owned in your party and PC." else "Caught before; none in your party or PC now.",
                style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
            )
            if (id in model.data.shinySpecies) Text("✨ You have found a shiny one.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Warning)
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Abilities", style = MaterialTheme.typography.titleMedium)
            Text(
                (form.abilities + listOfNotNull(form.hiddenAbility?.let { "$it (hidden)" })).joinToString(", "),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text("Base stats", style = MaterialTheme.typography.titleMedium)
            val base = form.stats
            listOf(base.hp, base.attack, base.defense, base.spAttack, base.spDefense, base.speed).forEachIndexed { i, v ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(statNames[i], Modifier.size(width = 84.dp, height = 24.dp), style = MaterialTheme.typography.bodyMedium)
                    Box(Modifier.weight(1f)) { PixelBar(v / 200f, color = if (i == 0) Pixel.Green else Pixel.Blue) }
                    Text("$v", Modifier.size(width = 44.dp, height = 24.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
                }
            }
            Text("Total ${base.total}", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
        }
    }
    val line = Dex.lineEvolutions(id)
    if (line.isNotEmpty()) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Evolution", style = MaterialTheme.typography.titleMedium)
            line.forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LineMember(model, e.from, current = e.from == id, open)
                    // How it evolves sits on the arrow between the two, like a label on a signpost.
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("▸", style = MaterialTheme.typography.titleMedium, color = Pixel.InkSoft)
                        Text(EvolutionRules.howTo(e), style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft, textAlign = TextAlign.Center)
                    }
                    LineMember(model, e.to, current = e.to == id, open)
                }
            }
        }
    }
    val credit by produceState<String?>(null, form.id) {
        // The SpriteCollab licence (CC BY-NC 4.0) asks for credit to the artists of the sprite and the portrait.
        val sprite = spriteCredit(form.id)
        val portrait = portraitCredit(form.id)
        value = when {
            sprite != null && sprite == portrait -> "Sprite and portrait by $sprite"
            else -> listOfNotNull(sprite?.let { "Sprite by $it" }, portrait?.let { "portrait by $it" }).joinToString(", ").ifEmpty { null }?.replaceFirstChar { it.uppercase() }
        }
    }
    credit?.let { CreditLine(it) }
}

/** A species in an evolution line: its sprite and name, a grey shape and ??? until found, tap to open its entry. */
@Composable
private fun LineMember(model: AppModel, speciesId: Int, current: Boolean, open: (Int) -> Unit) {
    val state = DexState.of(model.data, speciesId)
    val name = if (state == DexState.UNKNOWN) "???" else Dex.species(speciesId).name
    Column(
        Modifier.size(width = 92.dp, height = 76.dp).clip(RoundedCornerShape(12.dp))
            .background(if (current) Pixel.Shade else Pixel.Panel)
            .quietClickable(enabled = !current && state != DexState.UNKNOWN, onClickLabel = "Open entry") { open(speciesId) }
            .semantics(mergeDescendants = true) { contentDescription = name },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Sprite(Dex.defaultForm(speciesId).id, false, name, size = 52.dp, silhouette = if (state.caught) null else Pixel.Shade)
        Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** The credit SpriteCollab's licence asks for: small and faint, there to be found rather than read on every visit. */
@Composable
private fun CreditLine(credit: String) {
    Text(
        "$credit, PMD SpriteCollab (CC\u00A0BY\u2060-\u2060NC\u00A04.0)", Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 10.sp, lineHeight = 13.sp),
        color = Pixel.Gray.copy(alpha = 0.7f), textAlign = TextAlign.Center,
    )
}

// ----- Guardians -----------------------------------------------------------------------------------------------------

/** "+10% Super Candy chance" for a Guardian's signature perk(s). */
private fun perkText(p: OwnedPokemon): String =
    Guardians.signature(p).entries.joinToString(", ") { (b, v) -> "+${v.toInt()}% ${b.label()}" }

@Composable
private fun GuardiansView(model: AppModel, open: (String) -> Unit) {
    val store = model.store
    val slots = store.guardianSlots
    val slotted = model.data.player.guardianPokemon
    val candidates = store.guardianCandidates

    // The screen header already says "Guardians", so the card holds only the one line that matters now.
    TextBox {
        Text(
            if (slots == 0) "A legendary or mythical Pokémon in a Guardian slot gives a big boost. The first slot opens at rank ${TrainerRank.names[4]}."
            else "${slotted.size} of $slots slots used. More open as your rank grows.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    repeat(slots) { i ->
        val g = slotted.getOrNull(i)
        if (g == null) {
            ListRow(lead = { ItemIcon("poke-ball", size = 48.dp) }) {
                Text("Empty slot", style = MaterialTheme.typography.bodyLarge, color = Pixel.InkSoft)
            }
        } else {
            ListRow(
                lead = { Sprite(g.form.id, g.isShiny, g.nickname, size = 64.dp) },
                onClick = { open(g.id) },
                trail = { PixelButton("Remove", { store.unslotGuardian(g.id) }, color = Pixel.InkSoft, compact = true) },
            ) {
                Text(g.nickname, style = MaterialTheme.typography.titleMedium)
                Text(perkText(g), style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
            }
        }
    }
    if (candidates.isEmpty()) {
        EmptyState("No Guardians yet", "Legendary and mythical Pokémon come from quests, never from eggs.", icon = "master-ball")
    }
    candidates.filter { c -> slotted.none { it.id == c.id } }.forEach { g ->
        ListRow(
            lead = { Sprite(g.form.id, g.isShiny, g.nickname, size = 64.dp) },
            onClick = { open(g.id) },
            trail = { PixelButton("Slot", { store.slotGuardian(g.id) }, color = Pixel.Blue, enabled = slotted.size < slots, compact = true) },
        ) {
            Text(g.nickname, style = MaterialTheme.typography.titleMedium)
            Text(perkText(g), style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
        }
    }
}

// ----- Quests --------------------------------------------------------------------------------------------------------

@Composable
private fun QuestsView(model: AppModel) {
    val store = model.store
    val data = model.data
    val active = store.activeQuests
    val available = store.availableQuests
    val done = data.quests.count { it.value.completedAtMillis != null }
    val locked = Quests.all.filter { it.id !in data.quests && Quests.lockedReason(it, data) != null }.sortedBy { it.unlockRank }.take(3)
    // A quest shows its Pokémon, so the Pokédex counts it as seen (locked ones stay grey shapes).
    val questSpecies = (active + available.take(12)).map { it.speciesId }
    LaunchedEffect(questSpecies) { store.markSeen(questSpecies) }

    TextBox {
        Text(
            "Each legendary and mythical Pokémon has a quest that fills in as you keep doing what you already do. " +
                "No deadlines, up to ${Quests.MAX_ACTIVE} at a time.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    if (active.isEmpty() && available.isEmpty()) {
        EmptyState("No quests yet", "Quests open as your trainer rank grows.", icon = "oaks-letter")
    }
    if (active.isNotEmpty()) SectionLabel("In progress")
    active.forEach { q -> QuestCard(model, q) }
    if (available.isNotEmpty()) SectionLabel("Available")
    available.take(12).forEach { q -> QuestCard(model, q) }
    if (available.size > 12) TextBox { Text("${available.size - 12} more are open. Finish one to see the next.", style = MaterialTheme.typography.bodyMedium) }
    if (locked.isNotEmpty()) {
        SectionLabel("Coming up")
        locked.forEach { q ->
            ListRow(lead = { Sprite(Dex.defaultForm(q.speciesId).id, false, "Locked", size = 56.dp, silhouette = Pixel.Shade) }) {
                Text(q.title, style = MaterialTheme.typography.bodyLarge)
                Text(Quests.lockedReason(q, data) ?: "", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            }
        }
    }
    if (done > 0) Text("$done quest${if (done == 1) "" else "s"} completed", style = MaterialTheme.typography.bodyLarge, color = Pixel.Ink)
}

@Composable
private fun QuestCard(model: AppModel, quest: Quest) {
    val store = model.store
    val state = model.data.quests[quest.id]
    val ready = store.questReady(quest)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Sprite(Dex.defaultForm(quest.speciesId).id, false, Dex.species(quest.speciesId).name, size = 64.dp)
                Column(Modifier.weight(1f)) {
                    Text(Dex.species(quest.speciesId).name, style = MaterialTheme.typography.titleMedium)
                    Text(quest.title, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                }
                if (quest.longTerm) Text("Long-term", style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft)
            }
            Text(quest.story, style = MaterialTheme.typography.bodyMedium)
            store.questProgress(quest).forEach { (req, now) ->
                val shown = now.coerceAtMost(req.target)
                Text("${req.text}: $shown of ${req.target}", style = MaterialTheme.typography.bodyMedium)
                PixelBar(shown.toFloat() / req.target, color = if (shown >= req.target) Pixel.Green else Pixel.Blue, label = "${req.text}: $shown of ${req.target}")
            }
            when {
                state == null -> PixelButton("Take on this quest", { store.acceptQuest(quest.id) }, Modifier.fillMaxWidth(), Pixel.Blue, enabled = store.activeQuests.size < Quests.MAX_ACTIVE, compact = true)
                ready -> PixelButton("Claim ${Dex.species(quest.speciesId).name}", { store.claimQuest(quest.id) }, Modifier.fillMaxWidth(), Pixel.Green)
            }
        }
    }
}

// ----- Bag -----------------------------------------------------------------------------------------------------------

@Composable
private fun BagView(model: AppModel) {
    val store = model.store
    val player = model.data.player
    var open by remember { mutableStateOf<String?>(null) }
    val items = player.inventory.counts.entries.filter { it.value > 0 }.sortedWith(compareBy({ Items.category(it.key)?.ordinal ?: 99 }, { it.key }))
    if (store.sees(Feature.COINS)) TextBox { Text("${player.coins} coins", style = MaterialTheme.typography.titleMedium) }
    if (items.isEmpty()) EmptyState("The bag is empty", "Finish tasks and focus sessions to find candies, berries and more.", icon = "seal-bag")
    items.groupBy { Items.category(it.key) }.forEach { (category, group) ->
        SectionLabel(category.bagLabel())
        group.forEach { (id, n) ->
            val holdable = Items.holdable(id) && store.sees(Feature.HELD_ITEMS)
            val training = Items.category(id) == ItemCategory.TRAINING || Items.category(id) == ItemCategory.TERA
            val usable = holdable || ((!training || store.sees(Feature.TRAINING_ITEMS)) && player.party.any { store.canUse(id, it) })
            val toggle = { open = if (open == id) null else id }
            // A usable item says so with its own Use button, so it does not look the same as one that only sits in the bag.
            ListRow(
                lead = { ItemIcon(id, size = 40.dp) },
                onClick = if (usable) toggle else null,
                onClickLabel = "Use ${Items.name(id)}",
                trail = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                        Text("x$n", style = MaterialTheme.typography.titleMedium)
                        if (usable) PixelButton(if (open == id) "Close" else if (holdable) "Give" else "Use", toggle, color = Pixel.InkSoft, compact = true)
                    }
                },
            ) { Text(Items.name(id), style = MaterialTheme.typography.bodyLarge) }
            if (open == id) UseItemBox(model, id, holdable) { open = null }
        }
    }
}

/**
 * Using one bag item: who gets it, and for a Bottle Cap or Nature Mint which stat or nature. Held items are given to
 * hold (what the Pokémon held before goes back to the bag).
 */
@Composable
private fun UseItemBox(model: AppModel, id: String, holdable: Boolean, close: () -> Unit) {
    val store = model.store
    val party = model.data.player.party
    var target by remember(id) { mutableStateOf<OwnedPokemon?>(null) }
    fun done(ok: Boolean) {
        if (ok) model.effect(Sfx.HEAL)
        target = null
        if (!store.data.player.inventory.has(id)) close()
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val who = target
            when {
                who != null && id == Items.BOTTLE_CAP -> {
                    Text("Which stat should ${who.nickname} max out?", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Stat.entries.filter { who.ivs[it] < StatCalculator.MAX_IV }.forEach { st ->
                            PixelButton(st.label, { done(store.useItem(id, who.id, st)) }, color = Pixel.Blue, compact = true)
                        }
                    }
                }
                who != null && id == Items.NATURE_MINT -> {
                    Text("Which nature for ${who.nickname}?", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Nature.entries.filter { it != who.nature }.forEach { n ->
                            val detail = if (n.raised == null) "" else " (+${n.raised!!.label} -${n.lowered!!.label})"
                            PixelButton(n.name.lowercase().replaceFirstChar { it.uppercase() } + detail, { done(store.useItem(id, who.id, n)) }, color = Pixel.Blue, compact = true)
                        }
                    }
                }
                else -> {
                    Text(if (holdable) "Give to hold:" else "Use on:", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    party.filter { holdable || store.canUse(id, it) }.forEach { p ->
                        val label = if (holdable && p.heldItem != null) "${p.nickname} (holds ${Items.name(p.heldItem!!)})" else p.nickname
                        PixelButton(label, {
                            when {
                                holdable -> done(store.holdItem(p.id, id))
                                id == Items.BOTTLE_CAP || id == Items.NATURE_MINT -> target = p
                                else -> done(store.useItem(id, p.id))
                            }
                        }, Modifier.fillMaxWidth(), Pixel.Blue, compact = true)
                    }
                }
            }
        }
    }
}

private fun ItemCategory?.bagLabel(): String = when (this) {
    ItemCategory.CANDY -> "Candies"
    ItemCategory.VITAMIN -> "Vitamins"
    ItemCategory.TRAINING -> "Training"
    ItemCategory.EVOLUTION -> "Evolution items"
    ItemCategory.HELD -> "Held items"
    ItemCategory.BERRY -> "Berries"
    ItemCategory.LURE -> "Lures"
    ItemCategory.TERA -> "Tera Shards"
    ItemCategory.FORM -> "Forms"
    ItemCategory.KEY -> "Key items"
    null -> "Other"
}

// ----- One Pokémon ---------------------------------------------------------------------------------------------------

private val statNames = listOf("HP", "Attack", "Defense", "Sp. Atk", "Sp. Def", "Speed")

/** The games' "judge" wording for a Pokémon's IVs, by their total (0 to 186). */
private fun judge(total: Int) = when {
    total >= 186 -> "Can't be better"
    total >= 150 -> "Fantastic"
    total >= 120 -> "Very good"
    total >= 90 -> "Pretty good"
    total >= 60 -> "Decent"
    else -> "Not great, but loved all the same"
}

@Composable
private fun PokemonDetail(model: AppModel, p: OwnedPokemon, back: () -> Unit) {
    val store = model.store
    val player = model.data.player
    var renaming by remember { mutableStateOf(false) }
    var nickname by remember(p.id) { mutableStateOf(p.nickname) }
    var confirmTransfer by remember { mutableStateOf(false) }
    val isBuddy = p.id == player.buddyId

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(p.nickname, subtitle = if (p.nickname == p.species.name) "Lv. ${p.level}" else "${p.species.name}, Lv. ${p.level}", onBack = back)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Stage("bw08", 252.dp) {
                AnimatedSprite(
                    p.form.id, p.isShiny, p.nickname,
                    Modifier.align(Alignment.TopCenter).padding(top = 16.dp).quietClickable(onClickLabel = "Hear ${p.nickname}") { model.cry(p.speciesId) },
                    size = 176.dp, animate = !model.data.settings.reduceMotion,
                )
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    p.form.types.forEach { TypePlate(it, width = 84.dp) }
                }
            }
            TextBox {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Its face, the way a Mystery Dungeon team menu shows each member.
                    Portrait(p.form.id, p.isShiny, "${p.nickname}'s portrait", size = 72.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Nature: ${p.nature.name.lowercase().replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodyLarge)
                        Text("Ability: ${p.ability}", style = MaterialTheme.typography.bodyLarge)
                        p.gender?.let { Text("Gender: ${it.name.lowercase().replaceFirstChar { c -> c.uppercase() }}", style = MaterialTheme.typography.bodyLarge) }
                        if (store.sees(Feature.STATS_DETAIL)) Text("Judge: ${judge(p.ivs.values.sum())}", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            if (store.sees(Feature.STATS_DETAIL)) TextBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val values = p.stats.values
                    statNames.forEachIndexed { i, label ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(label, Modifier.size(width = 84.dp, height = 24.dp), style = MaterialTheme.typography.bodyMedium)
                            Box(Modifier.weight(1f)) { PixelBar(values[i] / 300f, color = if (i == 0) Pixel.Green else Pixel.Blue) }
                            Text("${values[i]}", Modifier.size(width = 44.dp, height = 24.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
                        }
                    }
                }
            }

            val isGuardian = Guardians.canBe(p)
            if (isGuardian) {
                TextBox {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Guardian", style = MaterialTheme.typography.titleMedium)
                        Text(perkText(p), style = MaterialTheme.typography.bodyLarge, color = Pixel.Green)
                        Text("Guardians are at full power and stay out of the party. Slot it for its boost.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                        val slotted = p.id in player.guardians
                        PixelButton(
                            if (slotted) "Remove from slot" else "Slot as Guardian",
                            { if (slotted) store.unslotGuardian(p.id) else store.slotGuardian(p.id) },
                            Modifier.fillMaxWidth(), if (slotted) Pixel.InkSoft else Pixel.Blue,
                            enabled = slotted || player.guardians.size < store.guardianSlots, compact = true,
                        )
                    }
                }
            }

            if (!isGuardian) TextBox {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val partner = player.party.maxOfOrNull { it.level } ?: p.level
                    val need = CandyRules.candiesNeeded(p.level, partner)
                    val friendship = "Friendship ${p.friendship * 100 / 255}%"
                    Text(friendship, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    PixelBar(p.friendship / 255f, color = Pixel.Red, label = friendship)
                    if (p.canFeed) {
                        Text("Next level: ${p.candyProgress} of $need Rare Candies", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                        PixelBar(p.candyProgress.toFloat() / need, color = Pixel.Yellow, label = "${p.candyProgress} of $need Rare Candies to the next level")
                        val have = player.inventory[Items.RARE_CANDY]
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            ItemIcon("rare-candy", size = 32.dp)
                            Text("x$have", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            PixelButton("Feed 1", { store.feedRareCandy(p.id, 1) }, enabled = have >= 1, compact = true)
                            PixelButton("Feed 10", { store.feedRareCandy(p.id, 10) }, enabled = have >= 10, compact = true)
                        }
                    } else {
                        Text("Max level!", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            if (!isGuardian && store.sees(Feature.HELD_ITEMS)) PokemonPerksBox(model, p)

            val options = if (store.sees(Feature.EVOLUTION)) store.evolutionOptions(p.id) else emptyList()
            if (options.isNotEmpty()) {
                TextBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Evolution", style = MaterialTheme.typography.titleMedium)
                        options.forEach { o ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Sprite(o.target.id, false, o.target.name, size = 56.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(o.target.name, style = MaterialTheme.typography.bodyLarge)
                                    if (!o.available) Text("Needs ${o.missing.joinToString()}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                                }
                                PixelButton("Evolve", {
                                    store.evolve(p.id, o)
                                    store.data.player.pokemon.firstOrNull { it.id == p.id }?.let { model.cutscene = Cutscene.Evolve(p, it) }
                                }, color = Pixel.Green, enabled = o.available, compact = true)
                            }
                        }
                    }
                }
            }

            TextBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isGuardian) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        PixelButton(if (isBuddy) "Is your buddy" else "Make buddy", { store.setBuddy(p.id); model.cry(p.speciesId) }, Modifier.weight(1f), Pixel.Red, enabled = !isBuddy, compact = true)
                        PixelButton(if (p.inParty) "To PC" else "To party", { store.setInParty(p.id, !p.inParty) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                    if (renaming) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(nickname, { nickname = it.take(20) }, Modifier.weight(1f), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge)
                            PixelButton("Save", { store.rename(p.id, nickname); renaming = false }, color = Pixel.Green, compact = true)
                        }
                    } else {
                        PixelButton("Rename", { renaming = true }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                    }
                    val starred = store.isFavorite(p.id)
                    if (!p.species.isSpecial) {
                        PixelButton(if (starred) "★ Starred" else "☆ Star", { store.toggleFavorite(p.id) }, Modifier.fillMaxWidth(), if (starred) Pixel.Warning else Pixel.InkSoft, compact = true)
                    }
                    val block = store.transferBlock(p.id)
                    if (block != null) {
                        Text(block.reason, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    } else if (starred) {
                        Text("Starred Pokémon cannot be transferred. Unstar it first.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    } else if (!confirmTransfer) {
                        // The one step that cannot be undone stays quiet until it is confirmed, so it never draws the eye first.
                        PixelButton("Transfer", { confirmTransfer = true }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                    } else {
                        val quote = Transfer.quote(p)
                        Text("Send ${p.nickname} away for ${quote.rareCandies} Rare Candies and ${quote.coins} coins?", style = MaterialTheme.typography.bodyLarge)
                        quote.warnings.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.Red) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton("Transfer", { if (store.transfer(p.id)) { model.effect(Sfx.ESCAPE); back() } }, Modifier.weight(1f), Pixel.Danger, compact = true)
                            PixelButton("Keep ${p.nickname}", { confirmTransfer = false }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                        }
                    }
                }
            }
        }
    }
}
