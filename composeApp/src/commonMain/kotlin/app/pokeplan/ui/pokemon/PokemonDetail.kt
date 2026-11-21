package app.pokeplan.ui.pokemon

import app.pokeplan.ui.theme.Sizes
import app.pokeplan.ui.theme.Spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
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
import app.pokeplan.ui.BackHandler
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.SpriteAction
import app.pokeplan.ui.Portrait
import app.pokeplan.ui.Stage
import app.pokeplan.ui.TypePlate
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.HoldToConfirm
import app.pokeplan.ui.theme.ListRow
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.ui.theme.quietClickable
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.ui.screens.Cutscene
import app.pokeplan.ui.screens.dayMonth
import app.pokeplan.ui.screens.label

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
 * One Pokémon, read top to bottom (docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md): who it is, what kind of specimen it is, how it is
 * growing, how it evolves, its role in your world, and where it came from. Transfer, the one step that cannot be undone,
 * comes last.
 */
@Composable
internal fun PokemonDetail(model: AppModel, p: OwnedPokemon, back: () -> Unit) {
    // The page draws a Back key, so the system Back gesture goes the same way (it used to leave the app).
    BackHandler(enabled = true, onBack = back)
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
            // A level gained while this page is open (a Rare Candy) turns it around once (its Rotate animation, else a hop).
            var spin by remember(p.id) { mutableStateOf(false) }
            var spinKey by remember(p.id) { mutableIntStateOf(0) }
            var shownLevel by remember(p.id) { mutableIntStateOf(p.level) }
            LaunchedEffect(p.level) {
                if (p.level > shownLevel && !model.data.settings.reduceMotion) { spin = true; spinKey++ }
                shownLevel = p.level
            }
            Stage("bw08", 252.dp) {
                AnimatedSprite(
                    p.form.id, p.isShiny, p.nickname,
                    Modifier.align(Alignment.TopCenter).padding(top = Spacing.L).quietClickable(onClickLabel = "Hear ${p.nickname}") { model.cry(p) },
                    size = 176.dp, animate = !model.data.settings.reduceMotion,
                    action = if (spin) SpriteAction.ROTATE else SpriteAction.IDLE, actionKey = spinKey, onActionDone = { spin = false },
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
                // Its face, the way a Mystery Dungeon team menu shows each member. The name and the types are in the bar and on the stage.
                Portrait(p.form.id, p.isShiny, "${p.nickname}'s portrait", size = Sizes.SpriteM)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("No. ${p.speciesId.toString().padStart(4, '0')}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    Text("Lv. ${p.level}", style = MaterialTheme.typography.titleMedium)
                }
                if (!renaming) PixelButton("Rename", { renaming = true }, color = Pixel.InkSoft, compact = true)
            }
            if (renaming) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.Bottom) {
                val save = { store.rename(p.id, nickname); renaming = false }
                PixelField(
                    nickname, { nickname = it.take(20) }, Modifier.weight(1f), singleLine = true,
                    label = "Nickname", placeholder = p.species.name,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { save() }),
                )
                PixelIconButton("check", "Save", { save() }, Pixel.Tone)
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
        PixelButton(if (choosing) "Close" else "Choose", { choosing = !choosing }, Modifier.weight(1f), Pixel.Tone, compact = true)
        if (p.moves.isNotEmpty()) PixelButton("Natural", { store.setMoves(p.id, emptyList()) }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
    }
    if (choosing) {
        Text("Up to ${app.pokeplan.core.pokemon.Moveset.MAX}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
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

/** How it is growing: level and Exp. Candies, friendship and how it likes to bond, and its stats. */
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
            Text("Next level: ${p.candyProgress} of $need Exp. Candies", style = soft, color = Pixel.InkSoft)
            PixelBar(p.candyProgress.toFloat() / need, color = Pixel.Yellow, label = "${p.candyProgress} of $need Exp. Candies to the next level") // copy-ok: confirmation
            val have = player.inventory[Items.RARE_CANDY]
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                ItemIcon("rare-candy", size = Sizes.Icon, description = null)
                Text("$have Exp. Candies", style = MaterialTheme.typography.bodyLarge)
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
        PixelBar(p.friendship / 255f, color = Pixel.Pink, label = friendship)
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
                    Box(Modifier.weight(1f)) { PixelBar(values[i] / 300f, color = if (i == 0) Pixel.Green else Pixel.Tone) }
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
        if (p.heldItem == app.pokeplan.core.pokemon.Items.EVERSTONE && options.isNotEmpty()) Text("Holds an Everstone: it will not evolve.", style = soft, color = Pixel.InkSoft)
        // Evolving asks first (the games' "Evolve?"), with Cancel; the scene that follows can still be stopped.
        var asking by remember(p.id) { mutableStateOf<app.pokeplan.core.pokemon.EvolutionOption?>(null) }
        val worldDone = model.data.world.evolutionTrials + model.data.completedTrials
        options.forEach { o ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Sprite(o.target.id, false, o.target.name, size = Sizes.SpriteS)
                Column(Modifier.weight(1f)) {
                    Text(o.target.name, style = MaterialTheme.typography.bodyLarge)
                    if (!o.available) Text("Needs ${o.missing.joinToString()}", style = soft, color = Pixel.InkSoft)
                }
                PixelButton("Evolve", { asking = o }, color = Pixel.Green, enabled = o.available && asking == null, compact = true)
            }
            val key = app.pokeplan.core.pokemon.EvolutionTrials.doneKey(o.evolution, p.id)
            // An Evolution Trial in WORLD: where it is, in the region it belongs to.
            app.pokeplan.world.EvolutionTrialSites.of(o.evolution)?.takeIf { key !in worldDone && p.heldItem != app.pokeplan.core.pokemon.Items.EVERSTONE }?.let { site ->
                val d = app.pokeplan.world.Dungeons.byId(site.dungeonId)
                Text("Evolution Trial: ${site.title}", style = soft)
                Text("Floor ${site.floor}, ${d?.name ?: site.dungeonId} (${d?.layer?.label}), on a team", style = soft, color = Pixel.InkSoft)
            }
            // Done with the phone: upside down, a spin, a walk.
            app.pokeplan.core.pokemon.EvolutionTrials.device(o.evolution)?.takeIf { key !in worldDone && p.heldItem != app.pokeplan.core.pokemon.Items.EVERSTONE }?.let { action ->
                DeviceTrial(model, p, o, action)
            }
            if (asking == o) Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text("Evolve ${p.nickname} into ${o.target.name}?", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PixelButton("Cancel", { asking = null }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    PixelButton("Evolve", {
                        asking = null
                        // The scene makes the evolution at its end (Stop cancels it); this is what it will look like.
                        val after = app.pokeplan.core.pokemon.EvolutionRules.evolve(p, o).pokemon
                        model.cutscene = Cutscene.Evolve(p, after, o)
                    }, Modifier.weight(1f), Pixel.Green, compact = true)
                }
            }
        }
    }
}

/** Its role in your world: which party (or the PC, or the Guardian slots), lead or buddy, its region's dex, and the star. */
@Composable
private fun RoleBox(model: AppModel, p: OwnedPokemon, isGuardian: Boolean) = DetailBox(if (isGuardian) "Guardian" else "Role") {
    val store = model.store
    val player = model.data.player
    val isBuddy = p.id == player.buddyId
    // A legendary or mythical Pokémon is a normal Pokémon (a party, a team, the PC); it can also be one module's Guardian.
    Text(app.pokeplan.app.Places.label(model.data, p.id), style = MaterialTheme.typography.bodyLarge)
    if (isGuardian) GuardianChoice(model, p)
    var moving by remember(p.id) { mutableStateOf(false) }
    // A Pokémon on an expedition under way stays with its team until it ends (the label above says which team).
    val movable = app.pokeplan.app.Places.movable(model.data, p.id)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        PixelButton(if (isBuddy) "Is your buddy" else "Make buddy", { store.setBuddy(p.id); model.cry(p) }, color = Pixel.InkSoft, enabled = !isBuddy && !isGuardian && movable, compact = true)
        PixelButton(if (moving) "Close" else "Move to...", { moving = !moving }, color = Pixel.InkSoft, enabled = movable || moving, compact = true)
        if (!p.species.isSpecial) {
            val starred = store.isFavorite(p.id)
            PixelButton(if (starred) "Starred" else "Star", { store.toggleFavorite(p.id) }, color = if (starred) Pixel.Warning else Pixel.InkSoft, compact = true)
        }
    }
    if (moving && movable) PlacePicker(model, p)
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
                store.isFavorite(p.id) -> Text("Starred Pokémon cannot be transferred. Unstar it first.", style = soft, color = Pixel.InkSoft) // copy-ok: confirmation
                !confirm -> PixelButton("Transfer", { confirm = true }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                else -> {
                    val quote = Transfer.quote(p)
                    Text("Send ${p.nickname} away for ${quote.rareCandies} Exp. Candies and ${quote.coins} Poké?", style = MaterialTheme.typography.bodyLarge) // copy-ok: confirmation
                    quote.warnings.forEach { Text(it, style = soft, color = Pixel.Red) }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        HoldToConfirm("Hold to transfer", { if (store.transfer(p.id)) { model.effect(Sfx.ESCAPE); back() } }, Modifier.weight(1f))
                        PixelButton("Keep ${p.nickname}", { confirm = false }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                }
            }
        }
    }
}

/**
 * Where a Pokémon is kept, as one list instead of a pair of keys for every module (design review, 11 November 2026): the PC and each module's
 * party with how full it is. A Pokémon is in one place only, so moving it takes it from the old one; in the party it is in, the same row
 * makes it that module's lead. A full party is dimmed. The buddy, the Guardian slots and WORLD teams are set where they live.
 */
@Composable
private fun PlacePicker(model: AppModel, p: OwnedPokemon) {
    val store = model.store
    val player = model.data.player
    // Where it is now, from the one place it holds: the PC row says "Here" only for a Pokémon that really is in the PC (not the buddy, a
    // Guardian, a team member or a helper), and its button sends one of those back (the buddy is changed by choosing another).
    val place = store.placeOf(p.id)
    val here = (place as? app.pokeplan.app.Place.Party)?.partyId
    class Slot(val label: String, val partyId: String, val accent: androidx.compose.ui.graphics.Color, val lead: String?, val makeLead: () -> Unit)
    val slots = listOfNotNull(
        Slot("Plan party", PartyIds.PLAN, app.pokeplan.ui.theme.ModuleAccent.PLAN.accent, player.planLead?.id) { store.setPlanLead(p.id) },
        if (model.data.settings.careModule) Slot("Care party", PartyIds.CARE, app.pokeplan.ui.theme.ModuleAccent.CARE.accent, player.careLead?.id) { store.setCareLead(p.id) } else null,
        Slot("Heal party", PartyIds.HEAL, app.pokeplan.ui.theme.ModuleAccent.HEAL.accent, player.healLead?.id) { store.setHealLead(p.id) },
        Slot("Mint party", PartyIds.MINT, app.pokeplan.ui.theme.ModuleAccent.MINT.accent, player.mintLead?.id) { store.setMintLead(p.id) },
        Slot("Base party", PartyIds.BASE, app.pokeplan.ui.theme.ModuleAccent.BASE.accent, player.baseLead?.id) { store.setBaseLead(p.id) },
        Slot("Link party", PartyIds.LINK, app.pokeplan.ui.theme.ModuleAccent.LINK.accent, player.linkLead?.id) { store.setLinkLead(p.id) },
    )
    fun moved(ok: Boolean, toPc: Boolean) { if (ok) model.effect(if (toPc) Sfx.MOVE_DROP else Sfx.RECRUIT) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("PC", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            if (place == app.pokeplan.app.Place.Pc) Text("Here", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            else PixelButton("Move here", { moved(store.sendToPc(p.id), true) }, color = Pixel.InkSoft, enabled = place != app.pokeplan.app.Place.Buddy, compact = true)
        }
        slots.forEach { slot ->
            val count = player.partyOf(slot.partyId).size
            val full = count >= OwnedPokemon.PARTY_SIZE
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text("${slot.label} · $count of ${OwnedPokemon.PARTY_SIZE}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = if (full && here != slot.partyId) Pixel.InkSoft else Pixel.Ink)
                if (here == slot.partyId) PixelButton(if (slot.lead == p.id) "Is lead" else "Make lead", slot.makeLead, color = slot.accent, enabled = slot.lead != p.id, compact = true)
                else PixelButton("Move here", { moved(store.setInParty(p.id, true, slot.partyId), false) }, color = slot.accent, enabled = !full, compact = true)
            }
        }
    }
}
