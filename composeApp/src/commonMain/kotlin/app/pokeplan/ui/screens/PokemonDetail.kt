package app.pokeplan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.pokemon.CandyRules
import app.pokeplan.core.pokemon.Guardians
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.Nature
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.pokemon.Transfer
import app.pokeplan.core.reward.Feature
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.Portrait
import app.pokeplan.ui.Stage
import app.pokeplan.ui.TypePlate
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.quietClickable
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.TextBox

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
internal fun PokemonDetail(model: AppModel, p: OwnedPokemon, back: () -> Unit) {
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
                        PixelButton(if (p.inParty && p.partyId == PartyIds.PLAN) "To PC" else "To Plan party", { store.setInParty(p.id, !(p.inParty && p.partyId == PartyIds.PLAN)) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                    if (!isGuardian && model.data.settings.careModule) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        val inCare = p.inParty && p.partyId == PartyIds.CARE
                        val isLead = p.id == player.careLeadId
                        PixelButton(if (isLead) "Is Care's lead" else "Make Care lead", { store.setCareLead(p.id) }, Modifier.weight(1f), Pixel.Red, enabled = inCare && !isLead, compact = true)
                        PixelButton(if (inCare) "To PC" else "To Care party", { store.setInParty(p.id, !inCare, PartyIds.CARE) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
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
