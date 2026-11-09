package app.pokeplan.ui.screens

import app.pokeplan.ui.theme.Sizes
import app.pokeplan.ui.theme.Spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pokeplan.app.Origin
import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.Region
import app.pokeplan.core.pokemon.BondStyle
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.pokemon.Stat
import app.pokeplan.core.pokemon.CandyRules
import app.pokeplan.core.pokemon.Guardians
import app.pokeplan.core.pokemon.Items
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
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.PixelIconButton
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

private fun String.titleCase() = lowercase().replaceFirstChar { it.uppercase() }

/** "Adamant (+Attack, −Sp. Atk)", or "Hardy (no stat changes)" for the five neutral natures. */
private fun natureText(p: OwnedPokemon): String {
    val up = p.nature.raised
    val down = p.nature.lowered
    val effect = if (up == null || down == null) "no stat changes" else "+${up.label}, −${down.label}"
    return "${p.nature.name.titleCase()} ($effect)"
}

/** What a bond style likes, for the friendship line (docs/design.md, "Bond styles"). */
private fun bondText(style: BondStyle) = when (style) {
    BondStyle.PRESENCE -> "Bonds fastest through focus sessions as your buddy"
    BondStyle.COMFORT -> "Bonds fastest through petting and Care routines"
    BondStyle.GIFTS -> "Bonds fastest through berries"
    BondStyle.BALANCED -> "Bonds a little faster through everything"
}

/** Its best IVs, the way the games' judge names them ("Speed", "Attack and Speed"). */
private fun bestStats(p: OwnedPokemon): String {
    val best = p.ivs.values.max()
    return Stat.entries.filter { p.ivs[it] == best }.joinToString(" and ") { it.label }
}

/** How it joined, and where: "Hatched from a Task Egg in the Plan Region". */
private fun originText(origin: Origin): String {
    val egg = origin.how == Origin.How.FOCUS_EGG || origin.how == Origin.How.TASK_EGG
    return origin.how.label + (origin.region?.takeIf { egg }?.let { " in the ${it.label}" } ?: "")
}

/** A box on the detail page with its title. */
@Composable
private fun DetailBox(title: String, content: @Composable () -> Unit) = TextBox {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        content()
    }
}

/**
 * One Pokémon, read top to bottom (docs/ecosystem.md, section 11): who it is, what kind of specimen it is, how it is
 * growing, how it evolves, its role in your world, and where it came from. Transfer, the one step that cannot be undone,
 * comes last.
 */
@Composable
internal fun PokemonDetail(model: AppModel, p: OwnedPokemon, back: () -> Unit) {
    val store = model.store
    val player = model.data.player
    val isGuardian = Guardians.canBe(p)
    val stats = store.sees(Feature.STATS_DETAIL)

    Column(Modifier.fillMaxSize()) {
        // The level and species are in the identity box below; the bar names the species only under a nickname.
        ScreenHeader(p.nickname, subtitle = p.species.name.takeIf { it != p.nickname }, onBack = back)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.M),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Stage("bw08", 252.dp) {
                AnimatedSprite(
                    p.form.id, p.isShiny, p.nickname,
                    Modifier.align(Alignment.TopCenter).padding(top = Spacing.L).quietClickable(onClickLabel = "Hear ${p.nickname}") { model.cry(p.speciesId) },
                    size = 176.dp, animate = !model.data.settings.reduceMotion,
                )
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.M), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    p.form.types.forEach { TypePlate(it, width = 84.dp) }
                }
            }
            IdentityBox(model, p)
            TraitsBox(p, stats)
            GrowthBox(model, p, isGuardian, stats)
            EvolutionBox(model, p)
            RoleBox(model, p, isGuardian)
            MovesBox(model, p)
            if (!isGuardian && store.sees(Feature.HELD_ITEMS)) PokemonPerksBox(model, p)
            OriginBox(model, p)
            TransferBox(model, p, back)
        }
    }
}

/** Who it is: its face, Pokédex number, species, level, and its nickname with Rename. */
@Composable
private fun IdentityBox(model: AppModel, p: OwnedPokemon) {
    val store = model.store
    var renaming by remember(p.id) { mutableStateOf(false) }
    var nickname by remember(p.id) { mutableStateOf(p.nickname) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M), verticalAlignment = Alignment.CenterVertically) {
                // Its face, the way a Mystery Dungeon team menu shows each member.
                Portrait(p.form.id, p.isShiny, "${p.nickname}'s portrait", size = Sizes.SpriteM)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("No. ${p.speciesId.toString().padStart(4, '0')}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    Text(p.form.name, style = MaterialTheme.typography.titleMedium)
                    Text("Lv. ${p.level} · ${p.form.types.joinToString(" / ") { it.name.titleCase() }}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            // Under the name, not beside it, so a long name or large text never squeezes it.
            if (!renaming) PixelButton("Rename", { renaming = true }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            else Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                PixelField(
                    nickname, { nickname = it.take(20) }, Modifier.weight(1f), singleLine = true,
                    placeholder = p.species.name,
                )
                PixelIconButton("check", "Save", { store.rename(p.id, nickname); renaming = false }, Pixel.Green)
            }
        }
    }
}

/** One move as a line: its name, then its category and power. */
private fun moveText(m: app.pokeplan.core.dex.MoveInfo): String =
    m.name + " · " + m.category.name.lowercase().replaceFirstChar { it.uppercase() } + (m.power?.let { " · power $it" } ?: "")

/**
 * Its moves (up to four). They are the natural ones, the last it has learned by its level, until some are chosen here; Choose lists what it may be
 * given and a tap puts a move in or out. Only WORLD reads them, to add to a team's power where a move fits the dungeon (WORLD.md 3.2).
 */
@Composable
private fun MovesBox(model: AppModel, p: OwnedPokemon) = DetailBox("Moves") {
    val store = model.store
    var choosing by remember(p.id) { mutableStateOf(false) }
    val active = p.activeMoves
    active.forEach { m ->
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            TypePlate(m.type, width = 56.dp)
            Text(moveText(m), style = MaterialTheme.typography.bodyMedium)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PixelButton(if (choosing) "Close" else "Choose", { choosing = !choosing }, Modifier.weight(1f), Pixel.Blue, compact = true)
        if (p.moves.isNotEmpty()) PixelButton("Natural", { store.setMoves(p.id, emptyList()) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
    }
    if (choosing) {
        Text("Up to ${app.pokeplan.core.pokemon.Moveset.MAX}. Tap to put a move in or out.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        val chosen = active.map { it.identifier }
        app.pokeplan.core.pokemon.Moveset.learnable(p).forEach { m ->
            val on = m.identifier in chosen
            ListRow(
                lead = { TypePlate(m.type, width = 56.dp) },
                onClick = {
                    val next = if (on) chosen - m.identifier else if (chosen.size < app.pokeplan.core.pokemon.Moveset.MAX) chosen + m.identifier else chosen
                    if (next.isNotEmpty() && next != chosen) store.setMoves(p.id, next)
                },
                onClickLabel = if (on) "Remove ${m.name}" else "Add ${m.name}",
            ) {
                Text(moveText(m) + if (on) " (chosen)" else "", style = MaterialTheme.typography.bodyMedium, color = if (on) Pixel.Green else Pixel.Ink)
            }
        }
    }
}

/** What kind of specimen it is: nature, ability, gender, shiny, and the IV judge. The things a good one is hard to get for. */
@Composable
private fun TraitsBox(p: OwnedPokemon, stats: Boolean) = DetailBox("Traits") {
    val body = MaterialTheme.typography.bodyLarge
    Text("Nature: ${natureText(p)}", style = body)
    Text("Ability: ${p.ability}", style = body)
    p.gender?.let { Text("Gender: ${it.name.titleCase()}", style = body) }
    if (p.isShiny) Text("Shiny", style = body, color = Pixel.Warning)
    if (stats) {
        Text("Judge: ${judge(p.ivs.values.sum())}", style = body)
        Text("Its best: ${bestStats(p)}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    }
}

/** How it is growing: level and Rare Candies, friendship and how it likes to bond, and its stats. */
@Composable
private fun GrowthBox(model: AppModel, p: OwnedPokemon, isGuardian: Boolean, stats: Boolean) = DetailBox("Growth") {
    val store = model.store
    val player = model.data.player
    val soft = MaterialTheme.typography.bodyMedium
    when {
        isGuardian -> Text("At full power: Guardians never need candies.", style = soft, color = Pixel.InkSoft)
        p.canFeed -> {
            val partner = player.party.maxOfOrNull { it.level } ?: p.level
            val need = CandyRules.candiesNeeded(p.level, partner)
            Text("Next level: ${p.candyProgress} of $need Rare Candies", style = soft, color = Pixel.InkSoft)
            PixelBar(p.candyProgress.toFloat() / need, color = Pixel.Yellow, label = "${p.candyProgress} of $need Rare Candies to the next level")
            val have = player.inventory[Items.RARE_CANDY]
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                ItemIcon("rare-candy", size = Sizes.Icon, description = null)
                Text("$have Rare Candies", style = MaterialTheme.typography.bodyLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                PixelButton("Feed 1", { store.feedRareCandy(p.id, 1) }, Modifier.weight(1f), enabled = have >= 1, compact = true)
                PixelButton("Feed 10", { store.feedRareCandy(p.id, 10) }, Modifier.weight(1f), enabled = have >= 10, compact = true)
            }
        }
        else -> Text("Max level!", style = MaterialTheme.typography.bodyLarge)
    }
    if (!isGuardian) {
        val friendship = "Friendship ${p.friendship * 100 / 255}%"
        Text(friendship, style = soft, color = Pixel.InkSoft)
        PixelBar(p.friendship / 255f, color = Pixel.Red, label = friendship)
        if (stats) Text(bondText(p.bondStyle), style = soft, color = Pixel.InkSoft)
    }
    if (stats) {
        val values = p.stats.values
        Column(Modifier.padding(top = Spacing.XS), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Stat.entries.forEachIndexed { i, stat ->
                // The nature's raised and lowered stats are marked with an arrow, not only a colour.
                val mark = when (stat) { p.nature.raised -> " ▲"; p.nature.lowered -> " ▼"; else -> "" }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    // Minimum widths, not fixed sizes, so large text grows the labels instead of clipping them.
                    Text(statNames[i] + mark, Modifier.widthIn(min = 92.dp), style = soft)
                    Box(Modifier.weight(1f)) { PixelBar(values[i] / 300f, color = if (i == 0) Pixel.Green else Pixel.Blue) }
                    Text("${values[i]}", Modifier.widthIn(min = 44.dp), style = soft, textAlign = TextAlign.End)
                }
            }
        }
    }
}

/** What it evolves from, and what it can evolve into with Evolve. */
@Composable
private fun EvolutionBox(model: AppModel, p: OwnedPokemon) {
    val store = model.store
    if (!store.sees(Feature.EVOLUTION)) return
    val options = store.evolutionOptions(p.id)
    val from = Dex.evolutionsInto(p.speciesId).firstOrNull()?.let { Dex.species(it.from) }
    if (options.isEmpty() && from == null) return
    DetailBox("Evolution") {
        val soft = MaterialTheme.typography.bodyMedium
        from?.let { Text("Evolves from ${it.name}", style = soft, color = Pixel.InkSoft) }
        if (options.isEmpty()) Text("This is its final form.", style = soft, color = Pixel.InkSoft)
        options.forEach { o ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Sprite(o.target.id, false, o.target.name, size = Sizes.SpriteS)
                Column(Modifier.weight(1f)) {
                    Text(o.target.name, style = MaterialTheme.typography.bodyLarge)
                    if (!o.available) Text("Needs ${o.missing.joinToString()}", style = soft, color = Pixel.InkSoft)
                }
                PixelButton("Evolve", {
                    store.evolve(p.id, o)
                    store.data.player.pokemon.firstOrNull { it.id == p.id }?.let { model.cutscene = Cutscene.Evolve(p, it) }
                }, color = Pixel.Green, enabled = o.available, compact = true)
            }
        }
    }
}

/** Its role in your world: which party (or the PC, or the Guardian slots), lead or buddy, its region's dex, and the star. */
@Composable
private fun RoleBox(model: AppModel, p: OwnedPokemon, isGuardian: Boolean) = DetailBox(if (isGuardian) "Guardian" else "Role") {
    val store = model.store
    val player = model.data.player
    val care = model.data.settings.careModule
    val soft = MaterialTheme.typography.bodyMedium
    val inPlan = p.inParty && p.partyId == PartyIds.PLAN
    val inCare = p.inParty && p.partyId == PartyIds.CARE
    val inHeal = p.inParty && p.partyId == PartyIds.HEAL
    val inMint = p.inParty && p.partyId == PartyIds.MINT
    val isMintLead = p.id == player.mintLead?.id
    val inHome = p.inParty && p.partyId == PartyIds.HOME
    val isHomeLead = p.id == player.homeLead?.id
    val inLink = p.inParty && p.partyId == PartyIds.LINK
    val isLinkLead = p.id == player.linkLead?.id
    val isHealLead = p.id == player.healLead?.id
    val isBuddy = p.id == player.buddyId
    val isLead = p.id == player.careLead?.id
    val isPlanLead = p.id == player.planLead?.id
    // A legendary or mythical Pokémon is a normal Pokémon (a party, a team, the PC); it can also be one module's Guardian.
    Text(app.pokeplan.app.Places.label(model.data, p.id), style = MaterialTheme.typography.bodyLarge)
    if (isGuardian) GuardianChoice(model, p)
    val regions = Region.entries.filter { it.isModule && it.released && (care || it != Region.CARE) }
        .filter { r -> Dex.regionDex(r).any { it.id == p.speciesId } }
    Text(
        if (regions.isEmpty()) "Not in a module region's Pokédex" else "Counts for the " + regions.joinToString(" and ") { it.label } + " Pokédex",
        style = soft, color = Pixel.InkSoft,
    )
    run {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            PixelButton(if (isBuddy) "Is your buddy" else "Make buddy", { store.setBuddy(p.id); model.cry(p.speciesId) }, Modifier.weight(1f), Pixel.Red, enabled = !isBuddy && !isGuardian, compact = true)
            PixelButton(if (inPlan) "To PC" else "To Plan party", { store.setInParty(p.id, !inPlan); model.effect(if (inPlan) Sfx.MOVE_DROP else Sfx.RECRUIT) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            PixelButton(if (isPlanLead) "Is Plan's lead" else "Make Plan lead", { store.setPlanLead(p.id) }, Modifier.fillMaxWidth(), Pixel.Red, enabled = inPlan && !isPlanLead, compact = true)
        }
        if (care) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            PixelButton(if (isLead) "Is Care's lead" else "Make Care lead", { store.setCareLead(p.id) }, Modifier.weight(1f), Pixel.Red, enabled = inCare && !isLead, compact = true)
            PixelButton(if (inCare) "To PC" else "To Care party", { store.setInParty(p.id, !inCare, PartyIds.CARE); model.effect(if (inCare) Sfx.MOVE_DROP else Sfx.RECRUIT) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            PixelButton(if (isHealLead) "Is Heal's lead" else "Make Heal lead", { store.setHealLead(p.id) }, Modifier.weight(1f), Pixel.Red, enabled = inHeal && !isHealLead, compact = true)
            PixelButton(if (inHeal) "To PC" else "To Heal party", { store.setInParty(p.id, !inHeal, PartyIds.HEAL); model.effect(if (inHeal) Sfx.MOVE_DROP else Sfx.RECRUIT) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            PixelButton(if (isMintLead) "Is Mint's lead" else "Make Mint lead", { store.setMintLead(p.id) }, Modifier.weight(1f), Pixel.Red, enabled = inMint && !isMintLead, compact = true)
            PixelButton(if (inMint) "To PC" else "To Mint party", { store.setInParty(p.id, !inMint, PartyIds.MINT); model.effect(if (inMint) Sfx.MOVE_DROP else Sfx.RECRUIT) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            PixelButton(if (isHomeLead) "Is Home's lead" else "Make Home lead", { store.setHomeLead(p.id) }, Modifier.weight(1f), Pixel.Red, enabled = inHome && !isHomeLead, compact = true)
            PixelButton(if (inHome) "To PC" else "To Home party", { store.setInParty(p.id, !inHome, PartyIds.HOME); model.effect(if (inHome) Sfx.MOVE_DROP else Sfx.RECRUIT) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            PixelButton(if (isLinkLead) "Is Link's lead" else "Make Link lead", { store.setLinkLead(p.id) }, Modifier.weight(1f), Pixel.Red, enabled = inLink && !isLinkLead, compact = true)
            PixelButton(if (inLink) "To PC" else "To Link party", { store.setInParty(p.id, !inLink, PartyIds.LINK); model.effect(if (inLink) Sfx.MOVE_DROP else Sfx.RECRUIT) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
        }
    }
    if (!p.species.isSpecial) {
        val starred = store.isFavorite(p.id)
        PixelButton(if (starred) "Starred" else "Star", { store.toggleFavorite(p.id) }, Modifier.fillMaxWidth(), if (starred) Pixel.Warning else Pixel.InkSoft, compact = true)
    }
}

/** Where it came from: how it joined and the day. Pokémon from before origins were kept show the day only. */
@Composable
private fun OriginBox(model: AppModel, p: OwnedPokemon) = DetailBox("Origin") {
    val day = model.store.dayOf(p.caughtAtMillis)
    model.data.origins[p.id]?.let {
        Text(originText(it), style = MaterialTheme.typography.bodyLarge)
        // A friend's egg or a visitor that stayed keeps a small note of who it came from and what it took.
        it.visitor?.takeIf { v -> v.note.isNotBlank() }?.let { v -> Text(v.note, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
    }
    Text("Met on ${day.dayMonth()} ${day.year}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
}

/** Transfer, the one step that cannot be undone: quiet until it is confirmed, so it never draws the eye first. */
@Composable
private fun TransferBox(model: AppModel, p: OwnedPokemon, back: () -> Unit) {
    val store = model.store
    var confirm by remember(p.id) { mutableStateOf(false) }
    val block = store.transferBlock(p.id)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            val soft = MaterialTheme.typography.bodyMedium
            when {
                block != null -> Text(block.reason, style = soft, color = Pixel.InkSoft)
                store.isFavorite(p.id) -> Text("Starred Pokémon cannot be transferred. Unstar it first.", style = soft, color = Pixel.InkSoft)
                !confirm -> PixelButton("Transfer", { confirm = true }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                else -> {
                    val quote = Transfer.quote(p)
                    Text("Send ${p.nickname} away for ${quote.rareCandies} Rare Candies and ${quote.coins} Poké?", style = MaterialTheme.typography.bodyLarge)
                    quote.warnings.forEach { Text(it, style = soft, color = Pixel.Red) }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        PixelButton("Transfer", { if (store.transfer(p.id)) { model.effect(Sfx.ESCAPE); back() } }, Modifier.weight(1f), Pixel.Danger, compact = true)
                        PixelButton("Keep ${p.nickname}", { confirm = false }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                }
            }
        }
    }
}
