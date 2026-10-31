package app.pokeplan.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.dp
import app.pokeplan.app.Origin
import app.pokeplan.core.dex.Dex
import app.pokeplan.core.pokemon.Items
import app.pokeplan.ui.AnimatedSprite
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.theme.ChoiceRow
import app.pokeplan.ui.theme.FieldLabel
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.world.Dungeons
import app.pokeplan.world.ExplorerRank
import app.pokeplan.world.Story
import app.pokeplan.world.WorldEvent
import app.pokeplan.world.WorldSim
import app.pokeplan.world.WorldTeam
import kotlinx.coroutines.delay

/**
 * World (docs/world.md): the dungeon game. Send expedition teams into dungeons; they climb floors on their own, offline
 * too, and bring back items, treasure and Pokémon to recruit. The map, the expeditions, the camp and the finds and bag are
 * its sections. Its progression (rank, Poké, dungeon depth) is its own; the Pokémon it recruits are the shared collection's.
 */
enum class WorldSection(val label: String, val icon: String) {
    MAP("World", "town-map"), TEAMS("Expeditions", "exp-share"), CAMP("Camp", "friend-ball"), BAG("Finds and Bag", "seal-bag"),
}

@Composable
internal fun WorldScreen(model: AppModel, openPokemon: (String) -> Unit = {}) {
    // The teams climb while the screen is open: settle every few seconds (and once on opening, which counts the time away).
    LaunchedEffect(Unit) { while (true) { model.store.worldSettle(); delay(3_000) } }
    ScreenSwitch(model.worldSection, depth = { if (it == null) 0 else 1 }) { section -> WorldPage(model, section, openPokemon) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WorldPage(model: AppModel, section: WorldSection?, openPokemon: (String) -> Unit) {
    if (section == null) {
        val toModules = { model.module = null }
        BackHandler(enabled = true, onBack = toModules)
        WorldMenu(model, toModules)
        return
    }
    SectionScaffold(section.label, Module.WORLD.label, onBack = { model.worldSection = null }, icon = section.icon, color = Module.WORLD.look.header) {
        when (section) {
            WorldSection.MAP -> WorldMapView(model)
            WorldSection.TEAMS -> WorldTeamsView(model)
            WorldSection.CAMP -> WorldCampView(model, openPokemon)
            WorldSection.BAG -> WorldBagView(model)
        }
    }
}

@Composable
private fun WorldMenu(model: AppModel, onBack: () -> Unit) {
    val world = model.data.world
    val out = world.teams.count { it.expedition != null }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(Module.WORLD.label, subtitle = "Rank: ${world.rank.label}", onBack = onBack, color = Module.WORLD.look.header)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            MenuGrid(
                listOf(
                    MenuEntry(WorldSection.MAP.name, WorldSection.MAP.icon, WorldSection.MAP.label, "${Dungeons.all.size} dungeons", tint = Module.WORLD.look.accent),
                    MenuEntry(WorldSection.TEAMS.name, WorldSection.TEAMS.icon, WorldSection.TEAMS.label, if (out == 0) "no team out" else "$out out", tint = Module.WORLD.look.accent),
                    MenuEntry(WorldSection.CAMP.name, WorldSection.CAMP.icon, WorldSection.CAMP.label, tint = Module.WORLD.look.accent),
                    MenuEntry(WorldSection.BAG.name, WorldSection.BAG.icon, WorldSection.BAG.label, badge = world.finds.size, tint = Module.WORLD.look.accent),
                ),
            ) { key -> model.worldSection = WorldSection.valueOf(key) }
            RankCard(model)
        }
    }
}

/** The exploration team's rank: its points towards the next rank, the story chapter it is on, and the one after. */
@Composable
private fun RankCard(model: AppModel) {
    val world = model.data.world
    val rank = world.rank
    val next = rank.next()
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("Exploration rank: ${rank.label}", style = MaterialTheme.typography.titleSmall)
            if (next != null) {
                PixelBar(((world.xp - rank.points).toFloat() / (next.points - rank.points)).coerceIn(0f, 1f), Modifier.fillMaxWidth(), Module.WORLD.look.accent, label = "${world.xp} of ${next.points} to ${next.label}")
                Text("${world.xp} of ${next.points} to ${next.label} rank", style = MaterialTheme.typography.bodyMedium)
            } else Text("Top rank reached.", style = MaterialTheme.typography.bodyMedium)
            val opened = Story.opened(world)
            Text("Story: chapter $opened, ${Story.chapters[opened - 1].title}", style = MaterialTheme.typography.bodyLarge)
            Story.next(world)?.let { Text("Next: ${it.title} (${it.needsRank.label} rank, ${it.needsClears} dungeon clears)", style = MaterialTheme.typography.bodyMedium) }
            Text("Poké: ${world.poke}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ----- World: the map -------------------------------------------------------------------------------------------------

@Composable
private fun WorldMapView(model: AppModel) {
    val world = model.data.world
    val ready = world.slots().filter { it.members.isNotEmpty() && it.expedition == null }
    Dungeons.all.forEach { d ->
        val open = world.rank >= d.needs
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(d.name, style = MaterialTheme.typography.titleSmall)
                Text("${d.region.label}, ${d.floors} floors. Quick explorers: ${d.favored.joinToString(", ") { it.label }}.", style = MaterialTheme.typography.bodyMedium)
                Text("Deepest floor: ${world.deepest[d.id] ?: 0} of ${d.floors}. Cleared ${world.clears[d.id] ?: 0} times.", style = MaterialTheme.typography.bodyLarge)
                if (!open) Text("Needs ${d.needs.label} rank.", style = MaterialTheme.typography.bodyMedium)
                else if (ready.isEmpty()) Text("Make a team in Expeditions to send it here.", style = MaterialTheme.typography.bodyMedium)
                else Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    ready.forEach { team -> PixelButton("Send team ${team.id}", { model.store.worldStart(team.id, d.id) }, color = Module.WORLD.look.accent, compact = true) }
                }
            }
        }
    }
}

// ----- Expeditions ------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorldTeamsView(model: AppModel) {
    val store = model.store
    val world = model.data.world
    val roster = model.data.player.pokemon
    var report by remember { mutableStateOf<List<String>>(emptyList()) }
    // What the latest settles reported, in a few plain lines.
    LaunchedEffect(world.xp) {
        val f = world.finds.lastOrNull()
        if (f != null) report = listOf("Found a ${Dex.species(f.speciesId).name} on floor ${f.floor} of ${Dungeons.byId(f.dungeonId)?.name}.") + report.take(4)
    }
    world.slots().forEach { team ->
        var editing by remember(team.id) { mutableStateOf(false) }
        var picked by remember(team.id, team.members) { mutableStateOf(team.members) }
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text("Team ${team.id}", style = MaterialTheme.typography.titleSmall)
                val members = team.members.mapNotNull { id -> roster.firstOrNull { it.id == id } }
                if (members.isEmpty()) Text("No one yet.", style = MaterialTheme.typography.bodyMedium)
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    members.forEach { p -> AnimatedSprite(p.form.id, p.isShiny, p.nickname, size = 48.dp, animate = false) }
                }
                val trip = team.expedition
                if (trip != null) {
                    val dungeon = Dungeons.byId(trip.dungeonId)
                    if (dungeon != null) {
                        val floor = trip.floorsClimbed % dungeon.floors
                        val per = WorldSim.floorMillis(dungeon, WorldSim.power(members, dungeon))
                        Text("Exploring ${dungeon.name}: floor ${floor + 1} of ${dungeon.floors}", style = MaterialTheme.typography.bodyLarge)
                        PixelBar(floor.toFloat() / dungeon.floors, Modifier.fillMaxWidth(), Module.WORLD.look.accent, label = "floor ${floor + 1} of ${dungeon.floors}")
                        Text("A floor takes about ${per / 1000 / 60} min ${per / 1000 % 60} s. It keeps going while you are away, up to 12 hours.", style = MaterialTheme.typography.bodyMedium)
                    }
                    PixelButton("Recall", { store.worldRecall(team.id) }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                } else {
                    PixelButton(if (editing) "Close" else "Choose the team", { editing = !editing }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                    if (editing) {
                        FieldLabel("Up to ${WorldTeam.MAX_MEMBERS} Pokémon, none on another team")
                        val others = world.teams.filter { it.id != team.id }.flatMap { it.members }.toSet()
                        roster.filter { it.id !in others }.sortedByDescending { it.level }.take(40).forEach { p ->
                            ChoiceRow(selected = p.id in picked, onClick = { picked = if (p.id in picked) picked - p.id else if (picked.size < WorldTeam.MAX_MEMBERS) picked + p.id else picked }, lead = { AnimatedSprite(p.form.id, p.isShiny, p.nickname, size = 40.dp, animate = false) }) {
                                Text("${p.nickname}, level ${p.level}", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        PixelButton("Save the team", { if (store.worldAssign(team.id, picked)) editing = false }, Modifier.fillMaxWidth(), Module.WORLD.look.accent, compact = true)
                    }
                }
            }
        }
    }
    if (report.isNotEmpty()) TextBox { Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) { FieldLabel("Lately"); report.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) } } }
    Text("Explore dungeons on the World map, then recall a team any time; nothing it found is lost.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
}

// ----- Camp -----------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorldCampView(model: AppModel, openPokemon: (String) -> Unit) {
    val data = model.data
    val residents = data.player.pokemon.filter { data.origins[it.id]?.how == Origin.How.WORLD }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("Species found exploring: ${data.world.discovered.size}", style = MaterialTheme.typography.titleSmall)
            if (residents.isEmpty()) Text("Pokémon you recruit from your finds live here, and in your collection.", style = MaterialTheme.typography.bodyMedium)
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                residents.forEach { p ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.foundation.layout.Box(Modifier.size(72.dp).clickableNoRipple { openPokemon(p.id) }) { AnimatedSprite(p.form.id, p.isShiny, p.nickname, size = 72.dp, animate = !data.settings.reduceMotion) }
                        Text(p.nickname, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(interactionSource = null, indication = null, onClick = onClick))

// ----- Finds and Bag -------------------------------------------------------------------------------------------------

@Composable
private fun WorldBagView(model: AppModel) {
    val store = model.store
    val world = model.data.world
    var message by remember { mutableStateOf("") }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("Exploration Finds", style = MaterialTheme.typography.titleSmall)
            if (world.finds.isEmpty()) Text("Nothing waiting. Teams out exploring bring Pokémon back.", style = MaterialTheme.typography.bodyMedium)
            world.finds.forEachIndexed { i, f ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Sprite(Dex.defaultForm(f.speciesId).id, false, Dex.species(f.speciesId).name, size = 48.dp)
                    Column(Modifier.weight(1f)) {
                        Text(Dex.species(f.speciesId).name, style = MaterialTheme.typography.bodyLarge)
                        Text("Floor ${f.floor}, ${Dungeons.byId(f.dungeonId)?.name}", style = MaterialTheme.typography.bodyMedium)
                    }
                    PixelButton("Recruit", { store.worldRecruit(i)?.let { message = "${it.nickname} joined your collection." } }, color = Module.WORLD.look.accent, compact = true)
                }
            }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("Items", style = MaterialTheme.typography.titleSmall)
            if (world.items.isEmpty()) Text("Berries and more come back from the dungeons.", style = MaterialTheme.typography.bodyMedium)
            world.items.forEach { (id, n) -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) { ItemIcon(id, size = 32.dp, description = null); Text("${Items.name(id)} x$n", style = MaterialTheme.typography.bodyLarge) } }
            if (world.items.isNotEmpty()) PixelButton("Move to the Bag", { val n = store.worldClaimItems(); message = "$n items are in your Bag." }, Modifier.fillMaxWidth(), Module.WORLD.look.accent, compact = true)
            world.treasure.entries.sortedBy { it.key }.forEach { (tier, n) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${listOf("", "Plain", "Silver", "Gold")[tier.coerceIn(1, 3)]} treasure box x$n", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    PixelButton("Open", { val found = store.worldOpenTreasure(tier); message = "Inside: " + found.entries.joinToString { (id, c) -> "${Items.name(id)} x$c" } }, color = Pixel.InkSoft, compact = true)
                }
            }
        }
    }
    if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodyLarge, color = Pixel.Green)
}
