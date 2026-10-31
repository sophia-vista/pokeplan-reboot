package app.pokeplan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import app.pokeplan.ui.TypePlate
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
import app.pokeplan.world.Facility
import app.pokeplan.world.Research
import app.pokeplan.world.WorldSkill
import app.pokeplan.world.MissionKind
import app.pokeplan.world.Missions
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
    MAP("Map", "town-map"), MISSIONS("Missions", "journal"), SKILLS("Skills", "ability-capsule"), BASE("Base", "scope-lens"), TEAMS("Expeditions", "exp-share"), CAMP("Camp", "friend-ball"), BAG("Finds and Bag", "seal-bag"),
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
            WorldSection.MISSIONS -> WorldMissionsView(model)
            WorldSection.SKILLS -> WorldSkillsView(model)
            WorldSection.BASE -> WorldBaseView(model)
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
            WorldBanner("town", "Exploration team", "${Dungeons.all.count { world.rank >= it.needs && Story.opened(world) >= it.chapter }} of ${Dungeons.all.size} dungeons open")
            MenuGrid(
                listOf(
                    MenuEntry(WorldSection.MAP.name, WorldSection.MAP.icon, WorldSection.MAP.label, "${Dungeons.all.size} dungeons", tint = Module.WORLD.look.accent),
                    MenuEntry(WorldSection.MISSIONS.name, WorldSection.MISSIONS.icon, WorldSection.MISSIONS.label, if (!Missions.boardOpen(world)) "opens in the story" else "${world.missions.size} taken", badge = world.missions.count { it.done }, tint = Module.WORLD.look.accent),
                    MenuEntry(WorldSection.SKILLS.name, WorldSection.SKILLS.icon, WorldSection.SKILLS.label, tint = Module.WORLD.look.accent),
                    MenuEntry(WorldSection.BASE.name, WorldSection.BASE.icon, WorldSection.BASE.label, badge = world.researchReady().size, tint = Module.WORLD.look.accent),
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
            if (Story.complete(world)) Text("The story is complete. ${Story.GUARDIAN_BONUS}.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
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
    WorldBanner("town", "The world map", "${world.clears.values.sum()} dungeon clears so far")
    Dungeons.all.forEach { d ->
        val rankOk = world.rank >= d.needs
        val chapterShut = Story.opened(world) < d.chapter
        val storyShut = d.postStory && !world.guardianDone
        val open = rankOk && !chapterShut && !storyShut
        val deepest = world.deepest[d.id] ?: 0
        TextBox(Modifier.alpha(if (open) 1f else 0.7f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                // The tileset picture when the game has one, else a plain swatch in the region's colour.
                Box(Modifier.width(64.dp).height(132.dp).border(2.dp, Pixel.Ink).background(regionColor(d.region))) {
                    if (d.id in dungeonPictures) WorldPicture("dungeon-${d.id}", Modifier.fillMaxSize(), crop = true)
                    else Text(d.region.label, Modifier.align(Alignment.Center).padding(Spacing.XS), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    if (!open) Box(Modifier.fillMaxSize().background(Color(0x66000000)))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Text(d.name, style = MaterialTheme.typography.titleSmall)
                    Text("${d.region.label}, ${d.floors} floors, ${d.needs.label} rank", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                        d.favored.forEach { TypePlate(it, width = 56.dp) }
                    }
                    PixelBar(deepest.toFloat() / d.floors, Modifier.fillMaxWidth(), Module.WORLD.look.accent, label = "deepest floor $deepest of ${d.floors}")
                    Text("Deepest floor $deepest of ${d.floors}, cleared ${world.clears[d.id] ?: 0} times", style = MaterialTheme.typography.bodyMedium)
                    when {
                        storyShut -> Text("Opens when the story is complete.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Red)
                        !rankOk -> Text("Needs ${d.needs.label} rank.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Red)
                        chapterShut -> Text("Needs story chapter ${d.chapter}: ${Story.chapters[d.chapter - 1].title}.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Red)
                        ready.isEmpty() -> Text("Make a team in Expeditions to send it here.", style = MaterialTheme.typography.bodyMedium)
                        else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            ready.forEach { team -> PixelButton("Send team ${team.id}", { model.store.worldStart(team.id, d.id) }, color = Module.WORLD.look.accent, compact = true) }
                        }
                    }
                }
            }
        }
    }
}

private fun missionIcon(kind: MissionKind): String = when (kind) {
    MissionKind.EXPLORE -> "town-map"
    MissionKind.COLLECT -> "seal-bag"
    MissionKind.RESCUE -> "friend-ball"
    MissionKind.DELIVER -> "journal"
}

private fun regionColor(region: app.pokeplan.world.WorldRegion): Color = when (region) {
    app.pokeplan.world.WorldRegion.GREENLANDS -> Color(0xFF5E9E3F)
    app.pokeplan.world.WorldRegion.MOUNTAINS -> Color(0xFF8A6B4A)
    app.pokeplan.world.WorldRegion.UNDERGROUND -> Color(0xFFC08A3E)
    app.pokeplan.world.WorldRegion.AQUATIC -> Color(0xFF3B84B8)
    app.pokeplan.world.WorldRegion.FROZEN -> Color(0xFF6FB6D6)
    app.pokeplan.world.WorldRegion.ANCIENT -> Color(0xFF8D6A9F)
    app.pokeplan.world.WorldRegion.SKY -> Color(0xFF5C8FD6)
    app.pokeplan.world.WorldRegion.OTHERWORLDLY -> Color(0xFF4B3A7A)
}

// ----- Missions -------------------------------------------------------------------------------------------------------

@Composable
private fun WorldMissionsView(model: AppModel) {
    val store = model.store
    val world = model.data.world
    if (!Missions.boardOpen(world)) {
        WorldBanner("guild-room", "The mission board", "Opens with story chapter 2", sky = Color(0xFF101038), top = true)
        TextBox { Text("The mission board opens with the story's second chapter, Requests and Rescues (Bronze rank and one dungeon clear).", style = MaterialTheme.typography.bodyMedium) }
        return
    }
    fun reward(m: app.pokeplan.world.MissionDef) = buildString {
        append("${m.poke} Poké, ${m.xp} XP")
        m.itemId?.let { append(", ${Items.name(it)} x${m.itemCount}") }
    }
    WorldBanner("guild-room", "The mission board", "${world.missions.count { it.done }} ready to claim, ${world.missions.size} of ${Missions.MAX_ACTIVE} taken", sky = Color(0xFF101038), top = true)
    FieldLabel("Taken (${world.missions.size} of ${Missions.MAX_ACTIVE})")
    if (world.missions.isEmpty()) Text("No jobs taken. Pick one below, then send a team to the dungeon it names.", style = MaterialTheme.typography.bodyMedium)
    world.missions.forEach { p ->
        val m = Missions.byId(p.id) ?: return@forEach
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    ItemIcon(missionIcon(m.kind), size = 32.dp, description = null)
                    Text(m.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    if (p.done) Text("Done", style = MaterialTheme.typography.bodyLarge, color = Pixel.Green)
                }
                val where = m.dungeonId?.let { "floor ${m.floor} of ${Dungeons.byId(it)?.name}" }
                Text(
                    when (m.kind) {
                        MissionKind.COLLECT -> "Find ${m.count} berries: ${minOf(p.progress, m.count)} so far."
                        MissionKind.RESCUE -> "Reach $where to bring the lost Pokémon home."
                        MissionKind.EXPLORE -> "Reach $where."
                        MissionKind.DELIVER -> "Reach $where, then hand over ${m.giveCount} x ${Items.name(m.give ?: "")} from your Bag."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text("Reward: ${reward(m)}", style = MaterialTheme.typography.bodyMedium)
                if (p.done) PixelButton("Claim", { store.worldClaimMission(p.id) }, Modifier.fillMaxWidth(), Module.WORLD.look.accent, compact = true)
                else PixelButton("Give up", { store.worldAbandonMission(p.id) }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            }
        }
    }
    FieldLabel("On the board")
    val offered = Missions.available(world)
    if (offered.isEmpty()) Text("Nothing new for your rank right now.", style = MaterialTheme.typography.bodyMedium)
    offered.forEach { m ->
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    ItemIcon(missionIcon(m.kind), size = 32.dp, description = null)
                    Text("${m.kind.label}${if (m.period == 1) " (daily)" else if (m.period == 7) " (weekly)" else ""}: ${m.title}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                }
                Text(m.story, style = MaterialTheme.typography.bodyMedium)
                Text("Reward: ${reward(m)}", style = MaterialTheme.typography.bodyMedium)
                PixelButton("Take the job", { store.worldAcceptMission(m.id) }, Modifier.fillMaxWidth(), Module.WORLD.look.accent, compact = true, enabled = world.missions.size < Missions.MAX_ACTIVE)
            }
        }
    }
}

// ----- Skills, base and research --------------------------------------------------------------------------------------------

@Composable
private fun WorldSkillsView(model: AppModel) {
    val world = model.data.world
    Text("Skills level as your teams explore, even while you are away.", style = MaterialTheme.typography.bodyMedium)
    WorldSkill.entries.forEach { skill ->
        val xp = world.skillXp[skill.name] ?: 0
        val level = world.skillLevel(skill)
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text("${skill.label}, level $level", style = MaterialTheme.typography.titleSmall)
                Text("${skill.blurb}: ${skill.bonus}.", style = MaterialTheme.typography.bodyMedium)
                if (level < WorldSkill.MAX_LEVEL) {
                    val from = WorldSkill.xpFor(level)
                    val to = WorldSkill.xpFor(level + 1)
                    PixelBar(((xp - from).toFloat() / (to - from)).coerceIn(0f, 1f), Modifier.fillMaxWidth(), Module.WORLD.look.accent, label = "$xp of $to")
                } else Text("Top level.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun WorldBaseView(model: AppModel) {
    val store = model.store
    val world = model.data.world
    WorldBanner("summit", "Exploration base", "${world.poke} Poké to spend", top = true)
    FieldLabel("Base")
    Facility.entries.forEach { f ->
        val level = world.facilityLevel(f)
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text("${f.label}, level $level of ${Facility.MAX_LEVEL}", style = MaterialTheme.typography.titleSmall)
                Text("${f.blurb} ${f.bonus}.", style = MaterialTheme.typography.bodyMedium)
                if (level < Facility.MAX_LEVEL) PixelButton("Build level ${level + 1}: ${f.cost(level)} Poké", { store.worldUpgrade(f) }, Modifier.fillMaxWidth(), Module.WORLD.look.accent, enabled = world.poke >= f.cost(level), compact = true)
            }
        }
    }
    FieldLabel("Research: ${world.discovered.size} species discovered")
    Research.steps.forEach { step ->
        val reached = world.discovered.size >= step.species
        val claimed = step.species in world.researchClaimed
        TextBox {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Column(Modifier.weight(1f)) {
                    Text("${step.species} species", style = MaterialTheme.typography.bodyLarge)
                    Text(buildString { append("${step.poke} Poké"); step.itemId?.let { append(", ${Items.name(it)} x${step.count}") } }, style = MaterialTheme.typography.bodyMedium)
                }
                if (claimed) Text("Done", style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
                else PixelButton("Claim", { store.worldClaimResearch(step.species) }, color = Module.WORLD.look.accent, enabled = reached, compact = true)
            }
        }
    }
    FieldLabel("Spinda's Café")
    val brewing = world.brewUntil > model.store.nowMillis
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("A brew for the road", style = MaterialTheme.typography.titleSmall)
            Text("${app.pokeplan.world.Cafe.BERRIES} berries from your Bag make a brew for ${app.pokeplan.world.Cafe.HOURS} hours: 30% more Exploration XP, 5% more treasure chance and 1% more find chance on every floor.", style = MaterialTheme.typography.bodyMedium)
            if (brewing) Text("A brew is working for ${(world.brewUntil - model.store.nowMillis) / 60_000} more minutes.", style = MaterialTheme.typography.bodyLarge, color = Pixel.Green)
            var cafeMessage by remember { mutableStateOf("") }
            PixelButton("Brew", { cafeMessage = if (store.worldBrew()) "Brewed." else "You need ${app.pokeplan.world.Cafe.BERRIES} berries in your Bag." }, Modifier.fillMaxWidth(), Module.WORLD.look.accent, compact = true)
            if (cafeMessage.isNotEmpty()) Text(cafeMessage, style = MaterialTheme.typography.bodyMedium)
        }
    }
    FieldLabel("Kecleon Shop")
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("Rare Candy", style = MaterialTheme.typography.titleSmall)
            Text("${world.shopCandies} bought so far. Each costs a little more.", style = MaterialTheme.typography.bodyMedium)
            PixelButton("Buy one: ${world.candyPrice} Poké", { store.worldBuyCandy() }, Modifier.fillMaxWidth(), Module.WORLD.look.accent, enabled = world.poke >= world.candyPrice, compact = true)
        }
    }
    FieldLabel("Dojo")
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("Training", style = MaterialTheme.typography.titleSmall)
            Text("${app.pokeplan.world.Dojo.COST} Poké buys ${app.pokeplan.world.Dojo.SKILL_XP} skill XP in a skill you choose.", style = MaterialTheme.typography.bodyMedium)
            WorldSkill.entries.forEach { skill ->
                PixelButton("Train ${skill.label}", { store.worldTrain(skill) }, Modifier.fillMaxWidth(), Pixel.InkSoft, enabled = world.poke >= app.pokeplan.world.Dojo.COST && world.skillLevel(skill) < WorldSkill.MAX_LEVEL, compact = true)
            }
        }
    }
    Text("Storage is the shared Bag; the Swap Shop is not built.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
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
                        val per = WorldSim.floorMillis(dungeon, WorldSim.power(members, dungeon), world.guardianDone)
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
    WorldBanner("cave", "Camp", "${residents.size} residents, camp level ${data.world.facilityLevel(Facility.CAMP)}", sky = Color(0xFF101010), top = true)
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
