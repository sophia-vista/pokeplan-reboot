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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pokeplan.app.PcSort
import app.pokeplan.app.PcView
import app.pokeplan.app.quest.Quest
import app.pokeplan.app.quest.Quests
import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.Region
import app.pokeplan.core.model.PokemonType
import app.pokeplan.core.pokemon.CandyRules
import app.pokeplan.core.pokemon.Guardians
import app.pokeplan.core.pokemon.PerkBucket
import app.pokeplan.core.pokemon.ItemCategory
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.pokemon.Transfer
import app.pokeplan.core.reward.TrainerRank
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.Stage
import app.pokeplan.ui.TypePlate
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.NamePlate
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.ScreenHeader
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
    val player = model.data.player
    val store = model.store

    val open = selected?.let { id -> player.pokemon.firstOrNull { it.id == id } }
    if (open != null) {
        PokemonDetail(model, open) { selected = null }
        return
    }

    val current = section
    if (current == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Pokémon", subtitle = "${player.pokemon.size} owned, ${player.caught.size} seen", icon = "poke-ball")
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) {
                val ready = store.activeQuests.count { store.questReady(it) }
                MenuGrid(
                    listOf(
                        MenuEntry("PARTY", "poke-ball", "Party", "${player.party.size} of 6", tint = Pixel.Red),
                        MenuEntry("PC", "storage-key", "PC boxes", "${player.pokemon.count { !it.inParty && !Guardians.canBe(it) }} stored", tint = Pixel.Warning),
                        MenuEntry("DEX", "great-ball", "Pokédex", "${player.caught.size} caught", tint = Pixel.Blue),
                        MenuEntry("BAG", "seal-bag", "Bag", "${player.inventory.counts.count { it.value > 0 }} items", tint = Pixel.Green),
                        MenuEntry("GUARDIANS", "master-ball", "Guardians", if (store.guardianSlots == 0) "opens at rank ${TrainerRank.names[4]}" else "${player.guardians.size} of ${store.guardianSlots} slotted", tint = androidx.compose.ui.graphics.Color(0xFF8B5CF6)),
                        MenuEntry("QUESTS", "oaks-letter", "Quests", "${store.activeQuests.size} active", badge = ready, tint = Pixel.Warning),
                    ),
                ) { key -> section = Section.valueOf(key) }
            }
        }
        return
    }

    SectionScaffold(current.title, null, onBack = { section = null }, icon = current.icon) {
        when (current) {
            Section.PARTY -> {
                if (player.party.isEmpty()) EmptyState("Your party is empty", "Hatch an egg with a focus session, or move a Pokémon here from the PC.")
                player.party.forEach { p -> PokemonRow(p, p.id == player.buddyId, store.isFavorite(p.id)) { selected = p.id } }
            }
            Section.PC -> {
                val box = player.pokemon.filter { !it.inParty && !Guardians.canBe(it) }
                if (box.isEmpty()) EmptyState("The PC is empty", "New eggs go to your party first, then here once it is full.")
                else {
                    val shown = pcView.apply(box) { store.isFavorite(it) }
                    PcControls(pcView, { pcView = it }, shown.size, box.size)
                    if (shown.isEmpty()) EmptyState("Nothing matches", "Try another type or clear the filters.")
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        shown.forEach { p -> PcTile(p, store.isFavorite(p.id)) { selected = p.id } }
                    }
                }
            }
            Section.DEX -> PokedexView(model)
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
        Modifier.clip(RoundedCornerShape(12.dp)).background(Pixel.Panel).clickable(onClick = onClick).padding(6.dp),
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

/**
 * The Pokédex. A region's list is numbered from 1 in its own order (the Home Region starts at 1 too); the National Dex tab
 * uses the real National Dex numbers, a generation at a time.
 */
@Composable
private fun PokedexView(model: AppModel) {
    val player = model.data.player
    val regions = TrainerRank.unlockedRegions(player.trainerPoints)
    var region by remember { mutableStateOf(Region.HOME) }
    var national by remember { mutableStateOf(false) }
    var generation by remember { mutableStateOf(0) }
    var shown by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // Each row is (number shown, species): own order from 1 for a region, the National Dex number for the National tab.
    val rows = remember(region, national, generation) {
        if (national) Dex.species.filter { it.id in generations[generation].second }.map { it.id to it }
        else Dex.regionDex(region).mapIndexed { i, s -> (i + 1) to s }
    }
    val caught = rows.count { (_, s) -> s.id in player.caught }
    val shiny = rows.count { (_, sp) -> sp.id in model.data.shinySpecies }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        regions.forEach { r -> TabButton(r.label, !national && r == region, { national = false; region = r; shown = null }, Modifier.padding(0.dp)) }
        TabButton("National", national, { national = true; shown = null }, Modifier.padding(0.dp))
    }
    if (national) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        generations.forEachIndexed { i, (label, _) -> TabButton(label, i == generation, { generation = i; shown = null }, Modifier.padding(0.dp)) }
    }
    val title = if (national) "National Dex, ${generations[generation].first}" else region.label
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("$title: $caught of ${rows.size} caught", style = MaterialTheme.typography.titleMedium)
            Text("✨ $shiny shiny found", style = MaterialTheme.typography.bodyMedium, color = if (shiny > 0) Pixel.Warning else Pixel.InkSoft)
        }
    }
    shown?.let { (number, id) ->
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("#$number  ${Dex.species(id).name}", style = MaterialTheme.typography.titleMedium)
                Text(Dex.entry(id), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { (number, s) ->
            val have = s.id in player.caught
            Column(
                Modifier.size(width = 76.dp, height = 88.dp).clip(RoundedCornerShape(12.dp)).background(Pixel.Panel)
                    .clickable(enabled = have) { shown = number to s.id },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Species you do not have yet show as a grey shape: the real sprite, tinted.
                Sprite(Dex.defaultForm(s.id).id, false, if (have) s.name else "Not caught yet", size = 64.dp, silhouette = if (have) null else Pixel.Shade)
                Text("#$number" + if (s.id in model.data.shinySpecies) " ✨" else "", style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft)
            }
        }
    }
    Text(
        "About 1 in 128 eggs is shiny. Longer sessions and a Shiny hunter buddy raise the odds.",
        style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
    )
}

// ----- Guardians -----------------------------------------------------------------------------------------------------

private fun PerkBucket.label() = when (this) {
    PerkBucket.EGG_LUCK -> "Egg luck"
    PerkBucket.CANDY_YIELD -> "Candy yield"
    PerkBucket.SUPER_CANDY_CHANCE -> "Super Candy chance"
    PerkBucket.ITEM_FINDER -> "Item finder"
    PerkBucket.SHINY_HUNTER -> "Shiny hunter"
    PerkBucket.STREAK_KEEPER -> "Streak keeper"
    PerkBucket.COINS -> "Coins"
}

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
        EmptyState("No Guardians yet", "Legendary and mythical Pokémon come from quests, never from eggs.")
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

    TextBox {
        Text(
            "Each legendary and mythical Pokémon has a quest that fills in as you keep doing what you already do. " +
                "No deadlines, up to ${Quests.MAX_ACTIVE} at a time.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    if (active.isEmpty() && available.isEmpty()) {
        EmptyState("No quests yet", "Quests open as your trainer rank grows.")
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
                PixelBar(shown.toFloat() / req.target, color = if (shown >= req.target) Pixel.Green else Pixel.Blue)
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
    TextBox { Text("${player.coins} coins", style = MaterialTheme.typography.titleMedium) }
    if (items.isEmpty()) EmptyState("The bag is empty", "Finish tasks and focus sessions to find candies, berries and more.")
    items.groupBy { Items.category(it.key) }.forEach { (category, group) ->
        SectionLabel(category.bagLabel())
        group.forEach { (id, n) ->
            val usable = id in Items.vitamins || Items.category(id) == ItemCategory.BERRY
            ListRow(
                lead = { ItemIcon(id, size = 40.dp) },
                onClick = if (usable) ({ open = if (open == id) null else id }) else null,
                trail = { Text("x$n", style = MaterialTheme.typography.titleMedium) },
            ) { Text(Items.name(id), style = MaterialTheme.typography.bodyLarge) }
            if (open == id) {
                TextBox {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Use on:", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                        player.party.forEach { p ->
                            PixelButton(p.nickname, { store.useItem(id, p.id); if (!store.data.player.inventory.has(id)) open = null }, Modifier.fillMaxWidth(), Pixel.Blue, compact = true)
                        }
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
        ScreenHeader(p.nickname, subtitle = if (p.nickname == p.species.name) "Lv. ${p.level}" else "${p.species.name}, Lv. ${p.level}") {
            PixelButton("Back", back, color = Pixel.Ink, compact = true)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Stage("bw08", 252.dp) {
                AnimatedSprite(
                    p.form.id, p.isShiny, p.nickname,
                    Modifier.align(Alignment.TopCenter).padding(top = 16.dp).clickable(onClickLabel = "Hear ${p.nickname}", role = Role.Button) { model.cry(p.speciesId) },
                    size = 176.dp, animate = !model.data.settings.reduceMotion,
                )
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    p.form.types.forEach { TypePlate(it, width = 84.dp) }
                }
            }

            TextBox {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Nature: ${p.nature.name.lowercase().replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodyLarge)
                    Text("Ability: ${p.ability}", style = MaterialTheme.typography.bodyLarge)
                    p.gender?.let { Text("Gender: ${it.name.lowercase().replaceFirstChar { c -> c.uppercase() }}", style = MaterialTheme.typography.bodyLarge) }
                    Text("Judge: ${judge(p.ivs.values.sum())}", style = MaterialTheme.typography.bodyLarge)
                }
            }

            TextBox {
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
                    Text("Friendship", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    PixelBar(p.friendship / 255f, color = Pixel.Red)
                    if (p.canFeed) {
                        Text("Next level: ${p.candyProgress} of $need Rare Candies", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                        PixelBar(p.candyProgress.toFloat() / need, color = Pixel.Yellow)
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

            val options = store.evolutionOptions(p.id)
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
                                PixelButton("Evolve", { store.evolve(p.id, o) }, color = Pixel.Green, enabled = o.available, compact = true)
                            }
                        }
                    }
                }
            }

            TextBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isGuardian) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        PixelButton(if (isBuddy) "Is your buddy" else "Make buddy", { store.setBuddy(p.id) }, Modifier.weight(1f), Pixel.Red, enabled = !isBuddy, compact = true)
                        PixelButton(if (p.inParty) "To PC" else "To party", { store.setInParty(p.id, !p.inParty) }, Modifier.weight(1f), Pixel.Blue, compact = true)
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
                        PixelButton("Transfer", { confirmTransfer = true }, Modifier.fillMaxWidth(), Pixel.Red, compact = true)
                    } else {
                        val quote = Transfer.quote(p)
                        Text("Send ${p.nickname} away for ${quote.rareCandies} Rare Candies and ${quote.coins} coins?", style = MaterialTheme.typography.bodyLarge)
                        quote.warnings.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Pixel.Red) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton("Yes", { if (store.transfer(p.id)) back() }, Modifier.weight(1f), Pixel.Red, compact = true)
                            PixelButton("No", { confirmTransfer = false }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                        }
                    }
                }
            }
        }
    }
}
