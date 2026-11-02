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
import app.pokeplan.ui.describe
import app.pokeplan.ui.AppModel
import app.pokeplan.ui.ItemIcon
import app.pokeplan.ui.Sprite
import app.pokeplan.ui.Portrait
import app.pokeplan.ui.Emotion
import app.pokeplan.ui.Sfx
import app.pokeplan.ui.theme.ChoiceRow
import app.pokeplan.ui.theme.FieldLabel
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import app.pokeplan.ui.theme.PixelField
import app.pokeplan.ui.theme.PixelIconButton
import app.pokeplan.world.WorldData
import app.pokeplan.world.JournalLine
import app.pokeplan.ui.theme.Pixel
import app.pokeplan.ui.theme.PixelBar
import app.pokeplan.ui.theme.PixelButton
import app.pokeplan.ui.theme.ScreenHeader
import app.pokeplan.ui.theme.Spacing
import app.pokeplan.ui.theme.TextBox
import app.pokeplan.world.Dungeons
import app.pokeplan.world.FloorEvents
import app.pokeplan.world.AbilityPerks
import app.pokeplan.world.Facility
import app.pokeplan.world.Research
import app.pokeplan.world.WorldSkill
import app.pokeplan.world.MissionKind
import app.pokeplan.world.Missions
import app.pokeplan.world.ExplorerRank
import app.pokeplan.world.Story
import app.pokeplan.world.Legends
import app.pokeplan.world.bonusText
import app.pokeplan.world.WorldLayer
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
    MAP("Map", "town-map"), MISSIONS("Missions", "journal"), SKILLS("Skills", "ability-capsule"), BASE("Base", "scope-lens"), ARCHIVE("Story archive", "oaks-letter"), TEAMS("Expeditions", "exp-share"), BAG("Finds and Bag", "seal-bag"), SETTINGS("Settings", "storage-key"),
}

@Composable
internal fun WorldScreen(model: AppModel, openPokemon: (String) -> Unit = {}) {
    // The teams climb while the screen is open: settle every few seconds (and once on opening, which counts the time away).
    LaunchedEffect(Unit) { while (true) { model.store.worldSettle(); model.worldNow = model.store.nowMillis; delay(3_000) } }
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
            WorldSection.ARCHIVE -> WorldArchiveView(model)
            WorldSection.TEAMS -> WorldTeamsView(model)
            WorldSection.BAG -> WorldBagView(model)
            WorldSection.SETTINGS -> WorldSettings(model)
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
            // The order of the page: the situation (rank, what the teams brought back, what is happening), the one thing worth doing, then
            // the story and the places to go. The first time, one card says what World is.
            val firstTime = world.teams.none { it.expedition != null || it.members.isNotEmpty() } && world.clears.isEmpty()
            RankCard(model)
            WorldReportCard(model)
            ChoicesCard(model)
            if (firstTime) {
                TextBox {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Text("Start here", style = MaterialTheme.typography.titleSmall)
                        Text("Send a team of your Pokémon into a dungeon. They climb its floors on their own, even while you are away, and bring back berries, treasure and Pokémon who want to join you.", style = MaterialTheme.typography.bodyLarge)
                        PixelButton("Send my strongest into Tiny Woods", { model.store.worldQuickSend("tiny-woods") }, Modifier.fillMaxWidth(), Module.WORLD.look.accent, compact = true)
                    }
                }
            } else {
                PrimaryActionCard(model)
                ExpeditionOverview(model)
                StoryCard(model)
            }
            WorldBanner("town", "Exploration team", "${Dungeons.all.count { world.rank >= it.needs && Story.opened(world, it.layer) >= it.chapter }} of ${Dungeons.all.size} dungeons open")
            // The four places that matter most, big; the rest as small buttons below.
            MenuGrid(
                listOf(
                    MenuEntry(WorldSection.MAP.name, WorldSection.MAP.icon, WorldSection.MAP.label, "${Dungeons.all.size} dungeons", tint = Module.WORLD.look.accent),
                    MenuEntry(WorldSection.TEAMS.name, WorldSection.TEAMS.icon, WorldSection.TEAMS.label, if (out == 0) "no team out" else "$out out", tint = Module.WORLD.look.accent),
                    MenuEntry(WorldSection.MISSIONS.name, WorldSection.MISSIONS.icon, WorldSection.MISSIONS.label, if (!Missions.boardOpen(world)) "opens in the story" else "${world.missions.size} taken", badge = world.missions.count { it.done }, tint = Module.WORLD.look.accent),
                    MenuEntry(WorldSection.BASE.name, WorldSection.BASE.icon, WorldSection.BASE.label, badge = world.researchReady().size, tint = Module.WORLD.look.accent),
                ),
            ) { key -> model.worldSection = WorldSection.valueOf(key) }
            FieldLabel("More")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                listOf(WorldSection.BAG to if (world.finds.isEmpty()) "Finds and Bag" else "Finds and Bag (${world.finds.size})", WorldSection.SKILLS to "Skills", WorldSection.ARCHIVE to "Story archive", WorldSection.SETTINGS to "Settings").forEach { (section, label) ->
                    PixelButton(label, { model.worldSection = section }, color = Module.WORLD.look.accent, compact = true)
                }
            }
        }
    }
}

/** The landing's one big button: what is worth doing now and why (Pokémon to recruit, a job to claim, the next dungeon, or a look at the teams). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrimaryActionCard(model: AppModel) {
    val world = model.data.world
    val layer = model.worldLayer
    val action = app.pokeplan.world.WorldGuide.primaryAction(world, layer)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            WorldLayerSwitch(model)
            Text(if (action.kind == app.pokeplan.world.PrimaryKind.EXPLORE) "Next up in ${layer.label}" else "Next up", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            Text(action.title, style = MaterialTheme.typography.titleSmall)
            Text(action.reason, style = MaterialTheme.typography.bodyMedium)
            when (action.kind) {
                app.pokeplan.world.PrimaryKind.REVIEW_FINDS -> PixelButton("Open Finds", { model.worldSection = WorldSection.BAG }, Modifier.fillMaxWidth(), Module.WORLD.look.accent, compact = true)
                app.pokeplan.world.PrimaryKind.CLAIM_JOB -> PixelButton("Open the board", { model.worldSection = WorldSection.MISSIONS }, Modifier.fillMaxWidth(), Module.WORLD.look.accent, compact = true)
                app.pokeplan.world.PrimaryKind.EXPLORE -> action.dungeonId?.let { id -> PixelButton("Send my strongest", { model.store.worldQuickSend(id) }, Modifier.fillMaxWidth(), Module.WORLD.look.accent, compact = true) }
                app.pokeplan.world.PrimaryKind.CONTINUE -> PixelButton("See the teams", { model.worldSection = WorldSection.TEAMS }, Modifier.fillMaxWidth(), Module.WORLD.look.accent, compact = true)
            }
        }
    }
}

/** The story of the layer on show: the chapter, what it says, and what the next one asks. */
@Composable
private fun StoryCard(model: AppModel) {
    val world = model.data.world
    val layer = model.worldLayer
    val chapters = Story.chaptersOf(layer)
    val opened = Story.opened(world, layer)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("${layer.label} story: chapter $opened, ${chapters[opened - 1].title}", style = MaterialTheme.typography.titleSmall)
            Text(chapters[opened - 1].summary, style = MaterialTheme.typography.bodyMedium)
            if (Story.complete(world, layer)) Text("The story is complete. ${Story.guardianBonus(layer)}.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
            Story.next(world, layer)?.let {
                val cleared = Story.dungeonsCleared(world, layer)
                Text("Next: ${it.title} (${it.needsRank.label} rank, ${it.needsDungeons} different ${layer.label} dungeons cleared: you have $cleared)", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** The exploration team's rank: its points towards the next rank, what the next rank opens, and the Poké in hand. */
@Composable
private fun RankCard(model: AppModel) {
    val world = model.data.world
    val rank = world.rank
    val next = rank.next()
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                ItemIcon(rank.badge, size = 48.dp, description = "${rank.label} rank badge")
                Text("Exploration rank: ${rank.label}", style = MaterialTheme.typography.titleSmall)
            }
            if (next != null) {
                PixelBar(((world.xp - rank.points).toFloat() / (next.points - rank.points)).coerceIn(0f, 1f), Modifier.fillMaxWidth(), Module.WORLD.look.accent, label = "${world.xp} of ${next.points} to ${next.label}")
                Text("${world.xp} of ${next.points} to ${next.label} rank", style = MaterialTheme.typography.bodyMedium)
                rank.nextRankOpens().takeIf { it.isNotEmpty() }?.let { Text("${next.label} rank opens ${it.joinToString(" and ")}.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
            } else Text("Top rank reached.", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                ItemIcon(app.pokeplan.world.PmdArt.POKE_COIN, size = 24.dp, description = null)
                Text("Poké: ${world.poke}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ----- World: the map -------------------------------------------------------------------------------------------------

@Composable
private fun WorldMapView(model: AppModel) {
    val world = model.data.world
    val ready = world.slots().filter { it.members.isNotEmpty() && it.expedition == null }
    // The map: more of it is uncovered with each dungeon cleared; a tap on a marker brings that dungeon to the top of the list.
    var selected by remember { mutableStateOf<String?>(null) }
    val layer = model.worldLayer
    val dungeons = Dungeons.of(layer)
    val clearedCount = dungeons.count { (world.clears[it.id] ?: 0) > 0 }
    // The layer switch: every layer is open from the first day.
    WorldLayerSwitch(model) { selected = null }
    Text(layer.blurb, style = MaterialTheme.typography.bodyMedium)
    val spots = when (layer) { WorldLayer.HOME -> mapSpots; WorldLayer.KANTO -> kantoSpots; WorldLayer.JOHTO -> johtoSpots; WorldLayer.HOENN -> hoennSpots; WorldLayer.SINNOH -> sinnohSpots }
    WorldMapPicture(
        stage = 1 + clearedCount,
        markers = dungeons.mapNotNull { d ->
            spots[d.id]?.let { (x, y) ->
                val open = world.rank >= d.needs && Story.opened(world, layer) >= d.chapter && !(d.postStory && !world.layerDone(layer))
                val exploring = world.teams.any { it.expedition?.dungeonId == d.id }
                MapMarker(d.id, x, y, if (exploring) MarkerState.EXPLORING else if ((world.clears[d.id] ?: 0) > 0) MarkerState.CLEARED else if (open) MarkerState.OPEN else MarkerState.LOCKED, guardian = d.guardian)
            }
        },
        selected = selected, onSelect = { selected = it },
        mapName = layer.mapName, pictureW = layer.mapWidth.toFloat(), pictureH = layer.mapHeight.toFloat(),
    )
    Text(selected?.let { id -> Dungeons.byId(id)?.let { "${it.name}, ${it.region.label}" } } ?: "$clearedCount of ${dungeons.size} dungeons cleared. Tap a marker.", style = MaterialTheme.typography.bodyLarge)
    Text("Diamond: open. Circle with a tick: cleared. Square: not open yet. Circle with an arrow: a team is there. Red ring: a Guardian.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
    LegendsCard(model, layer)
    dungeons.sortedByDescending { it.id == selected }.forEach { d ->
        val open = app.pokeplan.world.WorldGuide.lockReasons(world, d).isEmpty()
        val deepest = world.deepest[d.id] ?: 0
        val wild = d.wild.sortedByDescending { it.weight }.map { it.speciesId }.distinct().take(4)
            .map { TileWalker(Dex.defaultForm(it).id, false, Dex.species(it).name, sample = true) }
        // The dungeon is drawn in its own tiles, the wild Pokémon that live there walking its floor; the text is on its walls.
        TileBox(d.id, wild, animate = !model.data.settings.reduceMotion, modifier = Modifier.alpha(if (open) 1f else 0.75f), scrolls = false) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(d.name, style = MaterialTheme.typography.titleSmall)
                Text("${d.region.label}, ${d.floors} floors, ${d.needs.label} rank", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    d.favored.forEach { TypePlate(it, width = 56.dp) }
                }
                PixelBar(deepest.toFloat() / d.floors, Modifier.fillMaxWidth(), Module.WORLD.look.accent, label = "deepest floor $deepest of ${d.floors}")
                Text("Deepest floor $deepest of ${d.floors}, cleared ${world.clears[d.id] ?: 0} times", style = MaterialTheme.typography.bodyMedium)
                val warn = Color(0xFFFFB4A8)
                // Every requirement not met yet, each on its own line with how far along it is.
                val reasons = app.pokeplan.world.WorldGuide.lockReasons(world, d)
                when {
                    reasons.isNotEmpty() -> reasons.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = warn) }
                    ready.isEmpty() && world.slots().any { it.expedition == null } ->
                        PixelButton("Send my strongest", { model.store.worldQuickSend(d.id) }, color = Module.WORLD.look.accent, compact = true)
                    ready.isEmpty() -> Text("Every team is out. Recall one in Expeditions.", style = MaterialTheme.typography.bodyMedium)
                    else -> {
                        // Why a team suits the place: how long a floor takes it, and how many of its members are of a favoured type.
                        ready.forEach { team ->
                            val members = team.members.mapNotNull { id -> model.data.player.pokemon.firstOrNull { it.id == id } }
                            val fit = WorldSim.suitability(d, members, world.guardianDone, world.legendFaster(d))
                            Text("Team ${team.id}: a floor takes about ${fit.floorMillis / 60_000} min; ${fit.favored} of ${fit.size} like this place.", style = MaterialTheme.typography.bodyMedium)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            ready.forEach { team -> PixelButton("Send team ${team.id}", { model.store.worldStart(team.id, d.id) }, color = Module.WORLD.look.accent, compact = true) }
                        }
                    }
                }
            }
        }
    }
}

/** Where each dungeon is on the world map picture (504 x 336 pixels). */
private val mapSpots = mapOf(
    "tiny-woods" to (235 to 123), "thunderwave-cave" to (179 to 107), "mt-steel" to (347 to 139), "waterfall-cave" to (299 to 67),
    "glacier-palace" to (107 to 291), "ancient-ruins" to (411 to 227), "sky-tower" to (459 to 43), "temporal-tower" to (59 to 179),
    "distortion-rift" to (83 to 75),
)

/** The layer buttons (every layer is open from the first day): the one on show is lit; [onChange] runs after a switch. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorldLayerSwitch(model: AppModel, onChange: () -> Unit = {}) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        WorldLayer.entries.forEach { l ->
            PixelButton(l.label, { model.worldLayer = l; onChange() }, color = if (l == model.worldLayer) Module.WORLD.look.accent else Pixel.InkSoft, compact = true)
        }
    }
}

/** Where each Kanto dungeon is on the Kanto map picture (192 x 128 pixels), read from its towns and routes. */
private val kantoSpots = mapOf(
    "viridian-forest" to (44 to 68), "mt-moon" to (82 to 44), "rock-tunnel" to (153 to 52), "pokemon-tower" to (156 to 67),
    "power-plant" to (150 to 80), "seafoam-islands" to (72 to 121), "mt-ember" to (40 to 122), "victory-road" to (28 to 62),
    "cerulean-cave" to (136 to 40),
)

/** Where each Johto dungeon is on the Johto map picture (144 x 128 pixels), read from its towns and landmarks. */
private val johtoSpots = mapOf(
    "sprout-tower" to (76 to 52), "ilex-forest" to (44 to 52), "union-cave" to (20 to 84), "ruins-of-alph" to (68 to 68),
    "slowpoke-well" to (44 to 84), "mt-mortar" to (76 to 36), "burned-tower" to (60 to 36), "whirl-islands" to (20 to 108),
    "tin-tower" to (70 to 24),
)

/** Where each Hoenn dungeon is on the Hoenn map picture (256 x 152 pixels), read from its towns and landmarks. */
/** Where each Sinnoh dungeon's marker stands on the Platinum Town Map (240 x 200 pixels), placed by eye from where the games put the places. */
private val sinnohSpots = mapOf(
    "eterna-forest" to (42 to 118), "oreburgh-mine" to (75 to 143), "wayward-cave" to (97 to 120), "old-chateau" to (75 to 100),
    "iron-island" to (26 to 82), "mt-coronet" to (97 to 95), "victory-road-sinnoh" to (210 to 127), "spear-pillar" to (97 to 70),
    "distortion-world" to (187 to 23),
)

private val hoennSpots = mapOf(
    "petalburg-woods" to (16 to 77), "granite-cave" to (27 to 128), "meteor-falls" to (50 to 26), "mt-chimney" to (66 to 31),
    "shoal-cave" to (168 to 44), "seafloor-cavern" to (195 to 48), "cave-of-origin" to (180 to 75), "victory-road-hoenn" to (228 to 80),
    "sky-pillar" to (160 to 100),
)

/** The legendary quests of [layer]: what each asks and how far it is, and the ones already given. */
@Composable
private fun LegendsCard(model: AppModel, layer: WorldLayer) {
    val quests = Legends.of(layer)
    if (quests.isEmpty()) return
    val world = model.data.world
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("Legendary quests", style = MaterialTheme.typography.titleSmall)
            quests.forEach { q ->
                val done = world.hasLegend(q.id)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    AnimatedSprite(Dex.defaultForm(q.speciesId).id, false, q.name, size = 48.dp, animate = false)
                    Column(Modifier.weight(1f)) {
                        Text(q.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            when {
                                done -> {
                                    val mon = model.data.player.pokemon.firstOrNull { it.speciesId == q.speciesId }
                                    when {
                                        mon == null -> "Joined your Guardians (no longer in your collection)."
                                        mon.id in model.data.player.guardians -> "Joined you, and slotted as a Guardian too."
                                        mon.id in model.data.world.assigned -> "Joined you and is out exploring with a team."
                                        else -> "Joined you. Slot it as a Guardian in Pokémon to add its Guardian perks."
                                    }
                                }
                                q.afterGuardian && !world.layerDone(layer) -> if (q.clears == 1) q.blurb else "Face the Guardian of ${layer.label} first. ${q.blurb}"
                                else -> "${q.blurb} (${Legends.progress(world, q)} of ${q.clears})"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text("Gives: ${q.bonusText()}${if (done) "" else " (once it joins you)"}", style = MaterialTheme.typography.bodyMedium, color = if (done) Pixel.Green else Pixel.InkSoft)
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
    MissionKind.ESCORT -> "exp-share"
    MissionKind.DEFEAT -> "scope-lens"
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
                        MissionKind.ESCORT -> "Reach $where with a team of at least ${m.count} Pokémon."
                        MissionKind.DEFEAT -> {
                            val here = m.dungeonId?.let { Dungeons.byId(it) }
                            val best = here?.let { d -> world.teams.maxOfOrNull { t -> WorldSim.power(t.members.mapNotNull { id -> model.data.player.pokemon.firstOrNull { it.id == id } }, d) } } ?: 0.0
                            "Reach $where with a team of power ${m.count} or more (your best team: ${best.toInt()})."
                        }
                        MissionKind.DELIVER -> "Reach $where, then hand over ${m.giveCount} x ${Items.name(m.give ?: "")} from your Bag."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text("Reward: ${reward(m)}", style = MaterialTheme.typography.bodyMedium)
                val needed = m.give?.let { it to m.giveCount }
                val haveIt = needed == null || model.data.player.inventory.has(needed.first, needed.second)
                if (p.done && !haveIt) Text("Needs ${needed!!.second} x ${Items.name(needed.first)} in your Bag.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Red)
                if (p.done) PixelButton("Claim", { store.worldClaimMission(p.id) }, Modifier.fillMaxWidth(), Module.WORLD.look.accent, enabled = haveIt, compact = true)
                else PixelButton("Give up", { store.worldAbandonMission(p.id) }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
            }
        }
    }
    FieldLabel("On the board")
    val board = Missions.board(world, model.worldNow.takeIf { it > 0 } ?: store.nowMillis, store.utcOffsetMillis)
    val available = board.filter { it.state == Missions.BoardState.AVAILABLE }
    // Jobs are grouped by region: all of them, or one region's.
    var region by remember { mutableStateOf<app.pokeplan.world.WorldLayer?>(null) }
    val regions = available.map { Missions.layerOf(it.mission) }.distinct()
    if (regions.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        TabButton("All", region == null, { region = null })
        regions.forEach { r -> TabButton(r.label, region == r, { region = r }) }
    }
    val offered = available.filter { region == null || Missions.layerOf(it.mission) == region }
    if (offered.isEmpty()) Text("Nothing new for your rank right now.", style = MaterialTheme.typography.bodyMedium)
    offered.forEach { entry ->
        val m = entry.mission
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    ItemIcon(missionIcon(m.kind), size = 32.dp, description = null)
                    Text("${m.kind.label}: ${m.title}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                }
                Text(m.story, style = MaterialTheme.typography.bodyMedium)
                val place = m.dungeonId?.let { id -> Dungeons.byId(id)?.let { "${it.name}, ${it.layer.label}, floor ${m.floor}" } } ?: "Any dungeon"
                Text("Where: $place. ${entry.repeats}.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                Text("Reward: ${reward(m)}", style = MaterialTheme.typography.bodyMedium)
                if (world.missions.size >= Missions.MAX_ACTIVE) Text("You hold ${Missions.MAX_ACTIVE} jobs: finish or give one up first.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Red)
                PixelButton("Take the job", { store.worldAcceptMission(m.id) }, Modifier.fillMaxWidth(), Module.WORLD.look.accent, compact = true, enabled = world.missions.size < Missions.MAX_ACTIVE)
            }
        }
    }
    // The jobs that cannot be taken now, each with why (rank, a wait, or done for good), out of the way until asked for.
    val others = board.filter { it.state == Missions.BoardState.UNAVAILABLE }
    var showOthers by remember { mutableStateOf(false) }
    if (others.isNotEmpty()) {
        PixelButton(if (showOthers) "Hide the others" else "Not available now (${others.size})", { showOthers = !showOthers }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        if (showOthers) others.forEach { Text("${it.mission.kind.label}: ${it.mission.title}. ${it.reason} ${it.repeats}.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) }
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
                val helpers = world.teams.flatMap { t -> t.members.mapNotNull { id -> model.data.player.pokemon.firstOrNull { it.id == id } } }
                    .filter { it.ability in AbilityPerks.bySkill.getValue(skill) }
                if (helpers.isNotEmpty()) Text("Helped by ${helpers.joinToString { "${it.nickname} (${it.ability})" }}: +${minOf(helpers.size, AbilityPerks.MAX_BONUS)} levels while they explore.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
                else Text("Abilities that help: ${AbilityPerks.bySkill.getValue(skill).sorted().joinToString()}.", style = MaterialTheme.typography.bodyMedium)
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
        // The Camp: the Pokémon recruited from your finds live in its yard (they are in your collection too, with the rest).
        if (f == Facility.CAMP) {
            val data = model.data
            val residents = data.player.pokemon.filter { data.origins[it.id]?.how == Origin.How.WORLD }
            if (residents.isNotEmpty()) CampYard(level, residents.map { TileWalker(it.form.id, it.isShiny, it.nickname) }, animate = !data.settings.reduceMotion)
        }
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
    NotebookCard(model)
    WorldBanner("cafe", "Spinda's Café", "A brew for the road", sky = Color(0xFF29180F), top = true, height = 150.dp)
    var steam by remember { mutableStateOf(0) }
    val play = !(model.data.settings.reduceMotion || model.powerSaving)
    val clock = model.worldNow.takeIf { it > 0 } ?: model.store.nowMillis
    val brewing = world.brewUntil > clock
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("A brew for the road", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                FxBurst("steam", steam, 56.dp, play = play)
            }
            Text("${app.pokeplan.world.Cafe.BERRIES} berries from your Bag make a brew for ${app.pokeplan.world.Cafe.HOURS} hours: 30% more Exploration XP, 5% more treasure chance and 1% more find chance on every floor.", style = MaterialTheme.typography.bodyMedium)
            if (brewing) Text("A brew is working for ${(world.brewUntil - clock) / 60_000} more minutes.", style = MaterialTheme.typography.bodyLarge, color = Pixel.Green)
            var cafeMessage by remember { mutableStateOf("") }
            PixelButton("Brew", { cafeMessage = if (brewing) "A brew is still working." else if (store.worldBrew()) { steam++; "Brewed." } else "You need ${app.pokeplan.world.Cafe.BERRIES} berries in your Bag." }, Modifier.fillMaxWidth(), Module.WORLD.look.accent, compact = true)
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
    FieldLabel("Croagunk Swap Shop")
    var swapFrom by remember { mutableStateOf(app.pokeplan.world.SwapShop.flavors[0]) }
    var swapTo by remember { mutableStateOf(app.pokeplan.world.SwapShop.flavors[1]) }
    var swapMessage by remember { mutableStateOf("") }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("Berry swap", style = MaterialTheme.typography.titleSmall)
            Text("${app.pokeplan.world.SwapShop.GIVE} berries of one flavour from your Bag for 1 of another.", style = MaterialTheme.typography.bodyMedium)
            FieldLabel("Give")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                app.pokeplan.world.SwapShop.flavors.forEach { f -> PixelButton("$f x${model.data.player.inventory[Items.berry(f)]}", { swapFrom = f }, color = if (swapFrom == f) Module.WORLD.look.accent else Pixel.InkSoft, compact = true) }
            }
            FieldLabel("Get")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                app.pokeplan.world.SwapShop.flavors.forEach { f -> PixelButton(f, { swapTo = f }, color = if (swapTo == f) Module.WORLD.look.accent else Pixel.InkSoft, compact = true) }
            }
            PixelIconButton("repeat", "Swap", { swapMessage = if (store.worldSwap(swapFrom, swapTo)) "Swapped." else "Pick two flavours, and have ${app.pokeplan.world.SwapShop.GIVE} to give." }, Module.WORLD.look.accent)
            if (swapMessage.isNotEmpty()) Text(swapMessage, style = MaterialTheme.typography.bodyMedium)
        }
    }
    FieldLabel("Dojo")
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            var hit by remember { mutableStateOf(0) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Training", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                FxBurst("hit", hit, 64.dp, play = play)
            }
            Text("${app.pokeplan.world.Dojo.COST} Poké buys ${app.pokeplan.world.Dojo.SKILL_XP} skill XP in a skill you choose.", style = MaterialTheme.typography.bodyMedium)
            WorldSkill.entries.forEach { skill ->
                PixelButton("Train ${skill.label}", { if (store.worldTrain(skill)) hit++ }, Modifier.fillMaxWidth(), Pixel.InkSoft, enabled = world.poke >= app.pokeplan.world.Dojo.COST && world.skillLevel(skill) < WorldSkill.MAX_LEVEL, compact = true)
            }
        }
    }
    Text("Storage is the shared Bag.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
}

// ----- Expeditions ------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorldTeamsView(model: AppModel) {
    val store = model.store
    val world = model.data.world
    val roster = model.data.player.pokemon
    WorldReportCard(model)
    ChoicesCard(model)
    ExpeditionOverview(model)
    world.slots().forEach { team ->
        var editing by remember(team.id) { mutableStateOf(false) }
        var picked by remember(team.id, team.members) { mutableStateOf(team.members) }
        val members = team.members.mapNotNull { id -> roster.firstOrNull { it.id == id } }
        val outAt = team.expedition?.let { Dungeons.byId(it.dungeonId) }
        // A team that is out is shown in a box made of its dungeon's own tiles; any other team in a plain one.
        val body: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Box(Modifier.size(14.dp).background(teamAccent(team.accent)))
                    Text(team.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                }
                if (members.isEmpty()) Text("No one yet.", style = MaterialTheme.typography.bodyMedium)
                else if (team.expedition == null) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    members.forEach { p -> AnimatedSprite(p.form.id, p.isShiny, p.nickname, size = 48.dp, animate = false) }
                }
                val trip = team.expedition
                if (trip != null) {
                    val dungeon = Dungeons.byId(trip.dungeonId)
                    if (dungeon != null) {
                        val floor = trip.floorsClimbed % dungeon.floors
                        val per = WorldSim.floorMillis(dungeon, WorldSim.power(members, dungeon), world.guardianDone, world.legendFaster(dungeon))
                        Text("Exploring ${dungeon.name}: floor ${floor + 1} of ${dungeon.floors}", style = MaterialTheme.typography.bodyLarge)
                        app.pokeplan.world.DungeonModifiers.at(dungeon.id, model.worldNow.takeIf { it > 0 } ?: store.nowMillis)?.let { m ->
                            Text("Today here: ${m.title}, ${m.text}.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
                        }
                        PixelBar(floor.toFloat() / dungeon.floors, Modifier.fillMaxWidth(), Module.WORLD.look.accent, label = "floor ${floor + 1} of ${dungeon.floors}")
                        Text("A floor takes about ${per / 1000 / 60} min ${per / 1000 % 60} s. It keeps going while you are away, up to 12 hours.", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (outAt != null) SpeedExplainer(members, outAt, world)
                    PixelButton("Recall", { store.worldRecall(team.id) }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                } else {
                    PixelButton(if (editing) "Close" else "Choose the team", { editing = !editing }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                    if (editing) {
                        FieldLabel("Up to ${WorldTeam.MAX_MEMBERS} Pokémon: this team and the 20 strongest idle ones (not on a team, not slotted as a Guardian)")
                        // This team's own members and what is picked stay on the list so they can be taken off; the rest is the 20 strongest idle Pokémon.
                        val shown = (roster.filter { it.id in team.members || it.id in picked } + store.worldIdle().take(20)).distinctBy { it.id }
                        shown.forEach { p ->
                            ChoiceRow(selected = p.id in picked, onClick = { picked = if (p.id in picked) picked - p.id else if (picked.size < WorldTeam.MAX_MEMBERS) picked + p.id else picked }, lead = { AnimatedSprite(p.form.id, p.isShiny, p.nickname, size = 40.dp, animate = false) }) {
                                Text("${p.nickname}, level ${p.level}", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        // A name, an accent colour and saved teams: put one together again in a tap.
                        var name by remember(team.id, team.name) { mutableStateOf(team.name) }
                        FieldLabel("Name (up to ${WorldTeam.NAME_MAX} letters)")
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                            PixelField(name, { name = it.take(WorldTeam.NAME_MAX) }, Modifier.weight(1f), singleLine = true, placeholder = "Team ${team.id}")
                            PixelIconButton("check", "Save the name", { store.worldRenameTeam(team.id, name) }, Pixel.Green, enabled = name.trim() != team.name)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            repeat(WorldTeam.ACCENTS) { i ->
                                Box(
                                    Modifier.size(Spacing.Target).clickable(role = Role.Button, onClickLabel = "Colour ${i + 1}") { store.worldRecolourTeam(team.id, i) }.padding(10.dp)
                                        .background(teamAccent(i)).then(if (i == team.accent) Modifier.border(2.dp, Pixel.Ink) else Modifier),
                                )
                            }
                        }
                        if (world.presets.isNotEmpty()) {
                            FieldLabel("Saved teams")
                            world.presets.forEach { pre ->
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                                    PixelButton("${pre.name} (${pre.members.size})", { if (store.worldApplyPreset(team.id, pre.name)) editing = false }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                                    PixelIconButton("trash", "Delete ${pre.name}", { store.worldDeletePreset(pre.name) })
                                }
                            }
                        }
                        if (team.members.isNotEmpty() && world.presets.size < WorldData.MAX_PRESETS + 1) PixelButton("Save this team as a preset", { store.worldSavePreset(team.id, team.title) }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                        PixelButton("Save the team", { if (store.worldAssign(team.id, picked)) editing = false }, Modifier.fillMaxWidth(), Module.WORLD.look.accent, compact = true)
                    }
                }
            }
        }
        if (outAt != null) TileBox(outAt.id, members.map { TileWalker(it.form.id, it.isShiny, it.nickname) }, animate = !model.data.settings.reduceMotion, scrolls = model.data.settings.worldScroll, content = body) else TextBox { body() }
    }
    // The expedition log: what the teams found and did, kept (newest first) so it is still there after a night away; filter it by kind.
    if (world.journal.isNotEmpty()) TextBox {
        var kind by remember { mutableStateOf<String?>(null) }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            FieldLabel("Expedition log")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                TabButton("All", kind == null, { kind = null })
                JournalLine.KINDS.filter { k -> world.journal.any { it.kind == k } }.forEach { k -> TabButton(journalKindLabel(k), kind == k, { kind = k }) }
            }
            val lines = world.journal.filter { kind == null || it.kind == kind }
            if (lines.isEmpty()) Text("Nothing of that kind yet.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            lines.takeLast(if (kind == null) 8 else 20).reversed().forEach { line ->
                Text(if (line.place.isNotEmpty() && kind != null) "${line.text} (${line.place})" else line.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Text(
        "Side paths: about one floor in ten has something a field move can open (Cut, Surf, Rock Smash, Strength, Flash, Dig, Fly), found by a Pokémon of level ${FloorEvents.MIN_LEVEL} or more that can learn the move. It adds a small bonus; a team that cannot open one goes around and loses nothing. A hazard (fumes, a heat vent, ice, static) is weathered by a member of the right type. Now and then the team also comes to a fork and waits for you to pick a way (the first is taken after 12 hours). Some days a dungeon is also bountiful, in berry season, lucky or curious: a small bonus on its rewards, never a penalty.",
        style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft,
    )
    Text("Explore dungeons on the World map, then recall a team any time; nothing it found is lost.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
}

// ----- Camp -----------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(interactionSource = null, indication = null, onClick = onClick))

/** The recruiting scene: the wild Pokémon's face beside the question ([find]), or, once it has joined, beside the welcome ([newcomer]). */
@Composable
internal fun RecruitCard(find: app.pokeplan.world.Find?, newcomer: app.pokeplan.core.pokemon.OwnedPokemon?, onRecruit: () -> Unit, onLater: () -> Unit, onOk: () -> Unit) {
    if (newcomer != null) {
        TextBox {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Box {
                    Portrait(newcomer.form.id, newcomer.isShiny, newcomer.nickname, emotion = Emotion.JOYOUS, size = 88.dp)
                    FxBurst("welcome", newcomer.id.hashCode().let { if (it == 0) 1 else it }, 88.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Text("${newcomer.nickname} joined your team!", style = MaterialTheme.typography.titleSmall)
                    Text("Level ${newcomer.level}. It lives at the camp and can join any expedition.", style = MaterialTheme.typography.bodyMedium)
                    PixelButton("OK", onOk, Modifier.fillMaxWidth(), Module.WORLD.look.accent, compact = true)
                }
            }
        }
    } else if (find != null) {
        val name = Dex.species(find.speciesId).name
        TextBox {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Portrait(Dex.defaultForm(find.speciesId).id, false, name, emotion = Emotion.NORMAL, size = 88.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Text("A wild $name, met on floor ${find.floor} of ${Dungeons.byId(find.dungeonId)?.name}, wants to join your team. Recruit it?", style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        PixelButton("Recruit", onRecruit, Modifier.weight(1f), Module.WORLD.look.accent, compact = true)
                        PixelButton("Not now", onLater, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    }
                }
            }
        }
    }
}

// ----- Finds and Bag -------------------------------------------------------------------------------------------------

@Composable
private fun WorldBagView(model: AppModel) {
    val store = model.store
    val world = model.data.world
    var message by remember { mutableStateOf("") }
    var sparkle by remember { mutableStateOf(0) }
    // Recruiting is a short scene, as in the dungeon games: the Pokémon's face beside a question, then beside the welcome.
    var meeting by remember { mutableStateOf<Int?>(null) }
    var joined by remember { mutableStateOf<app.pokeplan.core.pokemon.OwnedPokemon?>(null) }
    val meetFind = meeting?.let { world.finds.getOrNull(it) }
    if (joined != null || meetFind != null) {
        RecruitCard(
            meetFind, joined,
            onRecruit = {
                val index = meeting ?: return@RecruitCard
                store.worldRecruit(index)?.let { p -> joined = p; model.cry(p.speciesId); model.effect(Sfx.CONFIRM) }
                meeting = null
            },
            onLater = { meeting = null }, onOk = { joined = null },
        )
    }
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
                    PixelButton("Meet", { joined = null; meeting = i }, color = Module.WORLD.look.accent, compact = true)
                }
            }
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("Treasure and rewards", style = MaterialTheme.typography.titleSmall)
            Text("Everything the dungeons bring back goes straight to your Bag and into your reward history.", style = MaterialTheme.typography.bodyMedium)
            // The latest World entries of the reward history, so what a trip brought is still there after it.
            model.data.rewardLog.filter { it.source.startsWith("World") }.takeLast(4).reversed().forEach { entry ->
                Text("${entry.source.removePrefix("World: ")}: ${entry.rewards.joinToString { it.describe(entry.source) }}", style = MaterialTheme.typography.bodyMedium)
            }
            if (world.treasure.isEmpty()) Text("No treasure boxes waiting.", style = MaterialTheme.typography.bodyMedium)
            world.treasure.entries.sortedBy { it.key }.forEach { (tier, n) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    ItemIcon(app.pokeplan.world.PmdArt.treasureBox(tier.coerceIn(1, 3)), size = 32.dp, description = null)
                    Text("${listOf("", "Plain", "Silver", "Gold")[tier.coerceIn(1, 3)]} treasure box x$n", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    PixelButton("Open", { val found = store.worldOpenTreasure(tier); message = "Inside: " + found.entries.joinToString { (id, c) -> "${Items.name(id)} x$c" }; sparkle++ }, color = Pixel.InkSoft, compact = true)
                }
            }
        }
    }
    if (message.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = Pixel.Green)
        FxBurst("sparkle", sparkle, 40.dp, play = !(model.data.settings.reduceMotion || model.powerSaving))
    }
}

/** A team's accent colour by number (the six a team can choose from). */
private fun teamAccent(i: Int): androidx.compose.ui.graphics.Color = when (i) {
    1 -> Pixel.Green
    2 -> Pixel.Red
    3 -> Pixel.Yellow
    4 -> Pixel.Pink
    5 -> Pixel.Teal
    else -> Pixel.Blue
}

private fun journalKindLabel(kind: String) = when (kind) {
    JournalLine.KIND_FIND -> "Finds"
    JournalLine.KIND_CLEAR -> "Clears"
    JournalLine.KIND_MILESTONE -> "Milestones"
    JournalLine.KIND_EVENT -> "Events"
    JournalLine.KIND_JOB -> "Jobs"
    JournalLine.KIND_GUARDIAN -> "Guardians"
    else -> "Notes"
}

/** The research notebook by region: each layer's species as sprites, met ones in colour, unmet ones as shadows, with a tick for those you own. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotebookCard(model: AppModel) {
    val world = model.data.world
    val owned = remember(model.data.player.pokemon) { model.data.player.pokemon.map { it.speciesId }.toSet() }
    val layers = app.pokeplan.world.WorldLayer.entries.filter { app.pokeplan.world.Notebook.species(it).isNotEmpty() }
    var layer by remember { mutableStateOf(layers.first()) }
    FieldLabel("Notebook")
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                layers.forEach { l ->
                    val (met, all) = app.pokeplan.world.Notebook.progress(l, world)
                    TabButton("${l.label} $met/$all", layer == l, { layer = l })
                }
            }
            val entries = app.pokeplan.world.Notebook.of(layer, world, owned)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                entries.forEach { e ->
                    Box(Modifier.size(48.dp)) {
                        val name = app.pokeplan.core.dex.Dex.species(e.speciesId).name
                        AnimatedSprite(Dex.defaultForm(e.speciesId).id, false, if (e.met) name else "Not met yet", size = 48.dp, animate = false)
                        if (!e.met) Box(Modifier.size(48.dp).background(Pixel.Panel.copy(alpha = 0.85f)))
                        if (e.owned) app.pokeplan.ui.Glyph("check", Pixel.Green, Modifier.align(Alignment.BottomEnd), size = 16.dp)
                    }
                }
            }
            Text("Met: species your teams have found or rescued here. A tick: you have one in your collection.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
    }
}

/** Floor choices waiting for an answer: two ways on, each a reward, with the first taken by itself when the time is up. Nothing is lost either way. */
@Composable
private fun ChoicesCard(model: AppModel) {
    val world = model.data.world
    if (world.choices.isEmpty()) return
    val now = model.worldNow.takeIf { it > 0 } ?: model.store.nowMillis
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("Your team is waiting for you", style = MaterialTheme.typography.titleSmall)
            world.choices.forEach { p ->
                val kind = p.choice ?: return@forEach
                val place = Dungeons.byId(p.dungeonId)?.name ?: p.dungeonId
                val left = ((p.expiresAt - now) / 60_000).coerceAtLeast(0)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    Text("${kind.title}, floor ${p.floor} of $place", style = MaterialTheme.typography.bodyLarge)
                    Text("${kind.story} Without an answer, in ${left / 60} h ${left % 60} min the team takes the first way. Either way it gives a reward.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    PixelButton("${kind.first.label}: ${rewardText(kind.first.reward)}", { model.store.worldChoose(p.id, 0) }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                    PixelButton("${kind.second.label}: ${rewardText(kind.second.reward)}", { model.store.worldChoose(p.id, 1) }, Modifier.fillMaxWidth(), Module.WORLD.look.accent, compact = true)
                }
            }
        }
    }
}

private fun rewardText(r: app.pokeplan.world.EventReward): String = when (r) {
    app.pokeplan.world.EventReward.BERRIES -> "2 berries"
    app.pokeplan.world.EventReward.POKE -> "Poké"
    app.pokeplan.world.EventReward.XP -> "Exploration XP"
    app.pokeplan.world.EventReward.TREASURE -> "a treasure box"
}

// ----- Story archive --------------------------------------------------------------------------------------------------

/**
 * Every story moment reached, to watch again: the ranks, each layer's chapters and the legendary quests that joined. Playing one again changes
 * nothing (it is a scene, not a reward). Chapters not opened yet are not listed, so nothing is given away.
 */
@Composable
private fun WorldArchiveView(model: AppModel) {
    val world = model.data.world
    val moments = app.pokeplan.world.WorldMoments.reached(world)
    WorldBanner("guild-room", "Story archive", "${moments.size} moments to watch again", sky = Color(0xFF101038), top = true)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            FieldLabel("Ranks")
            moments.filterIsInstance<app.pokeplan.world.WorldMoment.RankUp>().forEach { m ->
                ArchiveRow("Rank: ${m.rank.label}", "Exploration rank", m, model, icon = m.rank.badge)
            }
        }
    }
    // The people met in the scenes watched, with their sprites.
    val people = app.pokeplan.world.story.StoryCatalog.peopleMet(world.scenesDone)
    if (people.isNotEmpty()) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            FieldLabel("People you have met")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                people.forEach { c ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedSprite(Dex.defaultForm(c.speciesId!!).id, false, c.displayName, size = 48.dp, animate = false)
                        Text(c.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    WorldLayer.entries.forEach { layer ->
        val here = moments.filter { (it is app.pokeplan.world.WorldMoment.ChapterOpened && it.layer == layer) || (it is app.pokeplan.world.WorldMoment.LegendJoined && it.quest.layer == layer) }
        if (here.isEmpty()) return@forEach
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                FieldLabel(layer.label)
                here.forEach { m ->
                    when (m) {
                        is app.pokeplan.world.WorldMoment.ChapterOpened -> ArchiveRow("Chapter ${m.chapter.number}: ${m.chapter.title}", m.chapter.summary, m, model, icon = "town-map")
                        is app.pokeplan.world.WorldMoment.LegendJoined -> ArchiveRow(m.quest.name, "Joined as a Guardian: ${m.quest.bonusText()}.", m, model, icon = null, speciesId = m.quest.speciesId)
                        else -> {}
                    }
                }
            }
        }
    }
    if (moments.isEmpty()) Text("Nothing yet. Ranks, chapters and legendary Pokémon appear here as you reach them.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
}

@Composable
private fun ArchiveRow(title: String, detail: String, moment: app.pokeplan.world.WorldMoment, model: AppModel, icon: String?, speciesId: Int? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        if (speciesId != null) AnimatedSprite(Dex.defaultForm(speciesId).id, false, title, size = 40.dp, animate = false)
        else if (icon != null) ItemIcon(icon, size = 40.dp, description = null)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
        PixelIconButton("play", "Watch $title again", { model.cutscene = Cutscene.World(moment) })
    }
}
