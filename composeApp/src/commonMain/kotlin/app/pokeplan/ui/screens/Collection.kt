package app.pokeplan.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.Region
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
import app.pokeplan.ui.TypePlate
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.NamePlate
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.TextBox

private enum class Section(val label: String) { PARTY("Party"), PC("PC"), DEX("Dex"), BAG("Bag"), GUARDIANS("Guard") }

@Composable
fun CollectionScreen(model: AppModel, startSection: Int = 0, startOpen: String? = null) {
    var section by remember { mutableStateOf(Section.entries[startSection]) }
    var selected by remember { mutableStateOf(startOpen) }
    val player = model.data.player

    val open = selected?.let { id -> player.pokemon.firstOrNull { it.id == id } }
    if (open != null) {
        PokemonDetail(model, open) { selected = null }
        return
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Pokémon", subtitle = "${player.party.size}/6 in party · ${player.pokemon.size} owned · ${player.caught.size} seen")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Section.entries.forEach { s -> TabButton(s.label, s == section, { section = s }, Modifier.weight(1f)) }
            }
            when (section) {
                Section.PARTY -> {
                    if (player.party.isEmpty()) TextBox { Text("Your party is empty.", style = MaterialTheme.typography.bodyLarge) }
                    player.party.forEach { p -> PokemonRow(p, p.id == player.buddyId) { selected = p.id } }
                }
                Section.PC -> {
                    val box = player.pokemon.filter { !it.inParty && !Guardians.canBe(it) }
                    if (box.isEmpty()) TextBox { Text("The PC is empty. New eggs go to your party first, then here.", style = MaterialTheme.typography.bodyLarge) }
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        box.forEach { p -> PcTile(p) { selected = p.id } }
                    }
                }
                Section.DEX -> PokedexView(model)
                Section.BAG -> BagView(model)
                Section.GUARDIANS -> GuardiansView(model) { selected = it }
            }
        }
    }
}

@Composable
private fun PokemonRow(p: OwnedPokemon, buddy: Boolean, onClick: () -> Unit) {
    ListRow(
        lead = { Sprite(p.form.id, p.isShiny, p.nickname, size = 64.dp) },
        onClick = onClick,
        trail = { if (buddy) Text("Buddy", style = MaterialTheme.typography.labelMedium, color = Pixel.Red) },
    ) {
        Text(p.nickname + if (p.isShiny) "  *" else "", style = MaterialTheme.typography.titleMedium)
        Text("Lv. ${p.level}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { p.form.types.forEach { TypePlate(it, width = 56.dp) } }
    }
}

@Composable
private fun PcTile(p: OwnedPokemon, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).background(Pixel.Panel).clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Sprite(p.form.id, p.isShiny, p.nickname, size = 64.dp)
        Text("Lv. ${p.level}", style = MaterialTheme.typography.labelMedium)
    }
}

// ----- Pokédex -------------------------------------------------------------------------------------------------------

@Composable
private fun PokedexView(model: AppModel) {
    val player = model.data.player
    val regions = TrainerRank.unlockedRegions(player.trainerPoints)
    var region by remember { mutableStateOf(Region.HOME) }
    var shown by remember { mutableStateOf<Int?>(null) }
    val dex = remember(region) { Dex.regionDex(region) }
    val caught = dex.count { it.id in player.caught }

    if (regions.size > 1) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            regions.forEach { r -> TabButton(r.label, r == region, { region = r }, Modifier.padding(0.dp)) }
        }
    }
    TextBox { Text("${region.label}: $caught of ${dex.size} caught", style = MaterialTheme.typography.titleMedium) }
    shown?.let { id ->
        TextBox(accent = Pixel.Yellow) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(Dex.species(id).name, style = MaterialTheme.typography.titleMedium)
                Text(Dex.entry(id), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        dex.forEach { s ->
            val have = s.id in player.caught
            Column(
                Modifier.size(width = 76.dp, height = 88.dp).clip(RoundedCornerShape(12.dp)).background(Pixel.Panel)
                    .clickable(enabled = have) { shown = s.id },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Species you do not have yet show as a grey shape: the real sprite, tinted.
                Sprite(Dex.defaultForm(s.id).id, false, if (have) s.name else "Not caught yet", size = 64.dp, silhouette = if (have) null else Pixel.Shade)
                Text("#${s.id}", style = MaterialTheme.typography.labelMedium, color = Pixel.InkSoft)
            }
        }
    }
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

    TextBox(accent = Pixel.Yellow) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Guardians", style = MaterialTheme.typography.titleMedium)
            Text(
                if (slots == 0) "Legendary and mythical Pokémon watch over you from a Guardian slot, with big boosts. The first slot opens at rank ${TrainerRank.names[4]}."
                else "Slot a legendary or mythical Pokémon for a big boost. Slots: ${slotted.size} of $slots. More open as your rank grows.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
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
        TextBox { Text("No Guardians yet. Legendary and mythical Pokémon come from quests, never from eggs.", style = MaterialTheme.typography.bodyLarge) }
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

// ----- Bag -----------------------------------------------------------------------------------------------------------

@Composable
private fun BagView(model: AppModel) {
    val store = model.store
    val player = model.data.player
    var open by remember { mutableStateOf<String?>(null) }
    val items = player.inventory.counts.entries.filter { it.value > 0 }.sortedWith(compareBy({ Items.category(it.key)?.ordinal ?: 99 }, { it.key }))
    TextBox { Text("${player.coins} coins", style = MaterialTheme.typography.titleMedium) }
    if (items.isEmpty()) TextBox { Text("The bag is empty. Finish tasks to find things.", style = MaterialTheme.typography.bodyLarge) }
    items.forEach { (id, n) ->
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
        ScreenHeader(p.nickname, subtitle = "${p.species.name} · Lv. ${p.level}") {
            PixelButton("Back", back, color = Pixel.Ink, compact = true)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Sprite(p.form.id, p.isShiny, p.nickname, size = 192.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { p.form.types.forEach { TypePlate(it, width = 84.dp) } }
            NamePlate(p.nickname, if (p.isShiny) "shiny" else "Lv. ${p.level}")

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
                TextBox(accent = Pixel.Yellow) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Guardian", style = MaterialTheme.typography.titleMedium)
                        Text(perkText(p), style = MaterialTheme.typography.bodyLarge, color = Pixel.Green)
                        Text("A Guardian is already at full power: no candies, no party. Slot it for its boost.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
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
                TextBox(accent = Pixel.Green) {
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
                    val block = store.transferBlock(p.id)
                    if (block != null) {
                        Text(block.reason, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
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
