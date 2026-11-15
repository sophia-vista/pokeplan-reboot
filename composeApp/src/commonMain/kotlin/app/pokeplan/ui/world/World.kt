package app.pokeplan.ui.world

import app.pokeplan.ui.theme.Sizes
import app.pokeplan.ui.theme.ModuleAccent
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
import androidx.compose.material3.HorizontalDivider
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
import app.pokeplan.ui.BackHandler
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
import androidx.compose.ui.semantics.contentDescription
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
import app.pokeplan.ui.plan.clock
import app.pokeplan.ui.pokemon.label
import app.pokeplan.ui.screens.Cutscene
import app.pokeplan.ui.screens.MenuEntry
import app.pokeplan.ui.screens.MenuGrid
import app.pokeplan.ui.screens.MenuList
import app.pokeplan.ui.screens.Module
import app.pokeplan.ui.screens.ModuleLeadStage
import app.pokeplan.ui.screens.ScreenSwitch
import app.pokeplan.ui.screens.SectionScaffold
import app.pokeplan.ui.screens.TabButton
import app.pokeplan.ui.screens.WorldSettings
import app.pokeplan.ui.screens.label
import app.pokeplan.ui.screens.long
import app.pokeplan.ui.screens.short
import app.pokeplan.app.world.shopDay
import app.pokeplan.app.world.worldAbandonMission
import app.pokeplan.app.world.worldAcceptMission
import app.pokeplan.app.world.worldApplyPreset
import app.pokeplan.app.world.worldAssign
import app.pokeplan.app.world.worldBrew
import app.pokeplan.app.world.worldBuy
import app.pokeplan.app.world.worldChoose
import app.pokeplan.app.world.worldClaimMission
import app.pokeplan.app.world.worldClaimResearch
import app.pokeplan.app.world.worldDeletePreset
import app.pokeplan.app.world.worldIdle
import app.pokeplan.app.world.worldOpenTreasure
import app.pokeplan.app.world.worldQuickSend
import app.pokeplan.app.world.worldRecommend
import app.pokeplan.app.world.worldReplaceTeam
import app.pokeplan.app.world.worldRecall
import app.pokeplan.app.world.worldRecolourTeam
import app.pokeplan.app.world.worldRecruit
import app.pokeplan.app.world.worldRenameTeam
import app.pokeplan.app.world.worldSavePreset
import app.pokeplan.app.world.worldSettle
import app.pokeplan.app.world.worldStart
import app.pokeplan.app.world.worldSwap
import app.pokeplan.app.world.worldTrain
import app.pokeplan.app.world.worldUpgrade

/**
 * World (docs/pokelife/MODULES/WORLD.md section 14): the dungeon game. Send expedition teams into dungeons; they climb floors on their own, offline
 * too, and bring back items, treasure and Pokémon to recruit. The map, the expeditions, the camp and the finds and bag are
 * its sections. Its progression (rank, Poké, dungeon depth) is its own; the Pokémon it recruits are the shared collection's.
 */
/**
 * World's five destinations (WORLD.md section 2): Town, Atlas ([MAP]), Expeditions ([TEAMS]), Guild ([MISSIONS]) and Journal ([ARCHIVE]).
 * [SKILLS] and [BAG] (Finds) are reached from the Town, [SETTINGS] from the small button under the destinations; they stay sections of their own so a
 * shortcut (the landing's "Open Finds") can open them directly.
 */
enum class WorldSection(val label: String, val icon: String) {
    MAP("Atlas", "town-map"), MISSIONS("Guild", "oaks-parcel"), SKILLS("Skills", "ability-capsule"), TOWN("Town", "scope-lens"), ARCHIVE("Journal", "oaks-letter"), TEAMS("Expeditions", "exp-share"), BAG("Finds", "seal-bag"), SETTINGS("Settings", "storage-key"),
}

private const val NAP_MILLIS = 30_000L
private const val MIN_WAIT_MILLIS = 250L

@Composable
internal fun WorldScreen(model: AppModel, openPokemon: (String) -> Unit = {}) = app.pokeplan.ui.theme.ModuleTone(app.pokeplan.ui.theme.ModuleAccent.WORLD) { WorldBody(model, openPokemon) }

@Composable
private fun WorldBody(model: AppModel, openPokemon: (String) -> Unit) {
    // Settlement is by timestamp (WORLD.md 3.1): once on opening, which counts the time away, then at the moment the next floor of a team that is out
    // is done (the screen waits for that instant; it does not poll). The wait is cut into short naps so a clock that jumped or a phone that slept is noticed.
    LaunchedEffect(Unit) { model.store.worldSettle(); model.worldNow = model.store.nowMillis }
    val roster = model.data.player.pokemon
    val due = remember(model.data.world, roster) { WorldSim.nextFloorAt(model.data.world, roster, model.store.nowMillis) }
    LaunchedEffect(due) {
        if (due == null) return@LaunchedEffect
        while (model.store.nowMillis < due) delay(minOf(due - model.store.nowMillis, NAP_MILLIS).coerceAtLeast(MIN_WAIT_MILLIS))
        model.store.worldSettle()
        model.worldNow = model.store.nowMillis
    }
    ScreenSwitch(model.worldSection, depth = { if (it == null) 0 else 1 }) { section -> WorldPage(model, section, openPokemon) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WorldPage(model: AppModel, section: WorldSection?, openPokemon: (String) -> Unit) {
    if (section == null) {
        val toModules = { model.module = null }
        BackHandler(enabled = true, onBack = toModules)
        WorldMenu(model, toModules, openPokemon)
        return
    }
    SectionScaffold(section.label, null, onBack = { model.worldSection = null }, icon = section.icon, color = Module.WORLD.look.header) {
        when (section) {
            WorldSection.MAP -> WorldMapView(model)
            WorldSection.MISSIONS -> WorldMissionsView(model)
            WorldSection.SKILLS -> WorldSkillsView(model)
            WorldSection.TOWN -> WorldTownView(model)
            WorldSection.ARCHIVE -> WorldJournalView(model)
            WorldSection.TEAMS -> WorldTeamsView(model)
            WorldSection.BAG -> WorldBagView(model)
            WorldSection.SETTINGS -> WorldSettings(model)
        }
    }
}

@Composable
private fun WorldMenu(model: AppModel, onBack: () -> Unit, openPokemon: (String) -> Unit) {
    val world = model.data.world
    val out = world.teams.count { it.expedition != null }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(Module.WORLD.label, onBack = onBack, color = Module.WORLD.look.header)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            // The order of the page: where you are and the one thing worth doing (the region, its chapter, the next step), what the teams
            // are doing and brought back, then the places to go. Rank sits in one quiet strip. The first time, one card says what World is.
            // The lead of the team out exploring (else the first team with members), in World's own scene; nothing before a team has a member.
            val stageTeam = world.teams.firstOrNull { it.expedition != null && it.members.isNotEmpty() } ?: world.teams.firstOrNull { it.members.isNotEmpty() }
            val stageLead = stageTeam?.members?.firstNotNullOfOrNull { id -> model.data.player.pokemon.firstOrNull { it.id == id } }
            ModuleLeadStage(model, Module.WORLD, stageLead, null, openPokemon = openPokemon)
            val firstTime = world.teams.none { it.expedition != null || it.members.isNotEmpty() } && world.clears.isEmpty()
            RankStrip(model)
            WorldReportCard(model)
            ChoicesCard(model)
            if (firstTime) {
                TextBox {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Text("Send a team into a dungeon", style = MaterialTheme.typography.titleSmall)
                        PixelButton("Send my strongest into Tiny Woods", { model.store.worldQuickSend("tiny-woods") }, Modifier.fillMaxWidth(), Module.WORLD.look.accent, compact = true)
                    }
                }
            } else {
                RegionCard(model)
                ExpeditionOverview(model)
            }
            // The five destinations, big (WORLD.md section 2); Settings is the one small button.
            MenuGrid(
                listOf(
                    MenuEntry(WorldSection.TOWN.name, WorldSection.TOWN.icon, WorldSection.TOWN.label, if (world.finds.isEmpty()) "Camp level ${world.facilityLevel(Facility.CAMP)}" else "${world.finds.size} to meet", badge = world.researchReady().size + world.finds.size, tint = ModuleAccent.WORLD.accent),
                    MenuEntry(WorldSection.MAP.name, WorldSection.MAP.icon, WorldSection.MAP.label, "${Dungeons.all.size} dungeons", tint = ModuleAccent.WORLD.accent),
                    MenuEntry(WorldSection.TEAMS.name, WorldSection.TEAMS.icon, WorldSection.TEAMS.label, if (out == 0) "no team out" else "$out out", tint = ModuleAccent.WORLD.accent),
                    MenuEntry(WorldSection.MISSIONS.name, WorldSection.MISSIONS.icon, WorldSection.MISSIONS.label, if (!Missions.boardOpen(world)) "opens in the story" else "${world.missions.size} taken", badge = world.missions.count { it.done }, tint = ModuleAccent.WORLD.accent),
                    MenuEntry(WorldSection.ARCHIVE.name, WorldSection.ARCHIVE.icon, WorldSection.ARCHIVE.label, "${world.discovered.size} species met", tint = ModuleAccent.WORLD.accent),
                ),
            ) { key -> model.worldSection = WorldSection.valueOf(key) }
            PixelButton(WorldSection.SETTINGS.label, { model.worldSection = WorldSection.SETTINGS }, color = ModuleAccent.WORLD.accent, compact = true)
        }
    }
}

/**
 * The landing's main card: the region on show (with its switch), its chapter in one line, what is worth doing now and why (Pokémon to
 * recruit, a job to claim, the next dungeon, or a look at the teams) with its one button, and what the next chapter asks, quietly.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegionCard(model: AppModel) {
    val world = model.data.world
    val layer = model.worldLayer
    val action = app.pokeplan.world.WorldGuide.primaryAction(world, layer)
    val chapters = Story.chaptersOf(layer)
    val opened = Story.opened(world, layer)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            WorldLayerSwitch(model)
            Text("Chapter $opened: ${chapters[opened - 1].title}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            Text(action.title, style = MaterialTheme.typography.titleSmall)
            Text(action.reason, style = MaterialTheme.typography.bodyMedium)
            when (action.kind) {
                app.pokeplan.world.PrimaryKind.REVIEW_FINDS -> PixelButton("Open Finds", { model.worldSection = WorldSection.BAG }, Modifier.fillMaxWidth(), Module.WORLD.look.accent, compact = true)
                app.pokeplan.world.PrimaryKind.CLAIM_JOB -> PixelButton("Open the board", { model.worldSection = WorldSection.MISSIONS }, Modifier.fillMaxWidth(), Module.WORLD.look.accent, compact = true)
                app.pokeplan.world.PrimaryKind.EXPLORE -> action.dungeonId?.let { id -> PixelButton("Send my strongest", { model.store.worldQuickSend(id) }, Modifier.fillMaxWidth(), Module.WORLD.look.accent, compact = true) }
                app.pokeplan.world.PrimaryKind.CONTINUE -> PixelButton("See the teams", { model.worldSection = WorldSection.TEAMS }, Modifier.fillMaxWidth(), Module.WORLD.look.accent, compact = true)
            }
            if (Story.complete(world, layer)) Text("The ${layer.label} story is complete. ${Story.guardianBonus(layer)}.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
            Story.next(world, layer)?.let {
                val cleared = Story.dungeonsCleared(world, layer)
                Text("Next chapter: ${it.title} (${it.needsRank.label} rank, ${it.needsDungeons} ${layer.label} dungeons cleared: $cleared)", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) // copy-ok: game
            }
        }
    }
}

/** The exploration team's rank in one quiet strip: badge, rank, the way to the next one, and the Poké in hand. */
@Composable
private fun RankStrip(model: AppModel) {
    val world = model.data.world
    val rank = world.rank
    val next = rank.next()
    TextBox { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
        ItemIcon(rank.badge, size = Sizes.IconL, description = "${rank.label} rank badge")
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            Text(if (next != null) "${rank.label} rank, ${world.xp} of ${next.points} to ${next.label}" else "${rank.label} rank, the top", style = MaterialTheme.typography.bodyMedium)
            if (next != null) PixelBar(((world.xp - rank.points).toFloat() / (next.points - rank.points)).coerceIn(0f, 1f), Modifier.fillMaxWidth(), Module.WORLD.look.accent, label = "${world.xp} of ${next.points} to ${next.label}")
        }
        ItemIcon(app.pokeplan.world.PmdArt.POKE_COIN, size = Sizes.IconS, description = null)
        Text("${model.data.player.coins}", style = MaterialTheme.typography.bodyMedium)
    } }
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
    val spots = when (layer) { WorldLayer.RESCUE, WorldLayer.EXPLORERS -> mapSpots; WorldLayer.KANTO -> kantoSpots; WorldLayer.JOHTO -> johtoSpots; WorldLayer.HOENN -> hoennSpots; WorldLayer.SINNOH -> sinnohSpots; else -> emptyMap() }
    // A later region with no map art yet shows its dungeons as the list below only.
    if (layer.hasMap) WorldMapPicture(
        stage = 1 + clearedCount * 7 / dungeons.size.coerceAtLeast(1),
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
    Text(selected?.let { id -> Dungeons.byId(id)?.let { "${it.name}, ${it.region.label}" } } ?: "$clearedCount of ${dungeons.size} dungeons cleared", style = MaterialTheme.typography.bodyLarge)
    LegendsCard(model, layer)
    dungeons.sortedByDescending { it.id == selected }.forEach { d ->
        val open = app.pokeplan.world.WorldGuide.lockReasons(world, d).isEmpty()
        val deepest = world.deepest[d.id] ?: 0
        val wild = d.wild.sortedByDescending { it.weight }.map { it.speciesId }.distinct().take(4)
            .map { TileWalker(Dex.defaultForm(it).id, false, Dex.species(it).name, sample = true) }
        dungeonEntrance(d.id)?.let { EntranceStrip(it) }
        // The dungeon is drawn in its own tiles, the wild Pokémon that live there walking its floor; the text is on its walls.
        TileBox(d.id, wild, animate = !model.data.settings.reduceMotion, modifier = Modifier.alpha(if (open) 1f else 0.75f), scrolls = false) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(d.name, style = MaterialTheme.typography.titleSmall)
                Text("${d.region.label}, ${d.floors} floors, ${d.needs.label} rank", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    d.favored.forEach { TypePlate(it, width = 56.dp) }
                }
                PixelBar(deepest.toFloat() / d.floors, Modifier.fillMaxWidth(), Module.WORLD.look.accent, label = "deepest floor $deepest of ${d.floors}")
                (world.clears[d.id] ?: 0).takeIf { it > 0 }?.let { Text("Cleared $it ${if (it == 1) "time" else "times"}", style = MaterialTheme.typography.bodyMedium) }
                val warn = Color(0xFFFFB4A8)
                // Every requirement not met yet, each on its own line with how far along it is.
                val reasons = app.pokeplan.world.WorldGuide.lockReasons(world, d)
                when {
                    reasons.isNotEmpty() -> reasons.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = warn) }
                    ready.isEmpty() && world.slots().any { it.expedition == null } ->
                        PixelButton("Send my strongest", { model.store.worldQuickSend(d.id); model.effect(Sfx.STAIRS_DOWN) }, color = Module.WORLD.look.accent, compact = true)
                    ready.isEmpty() -> Text("Every team is out. Recall one in Expeditions.", style = MaterialTheme.typography.bodyMedium) // copy-ok: game
                    else -> {
                        // Why a team suits the place: how long a floor takes it, and how many of its members are of a favoured type.
                        ready.forEach { team ->
                            val members = team.members.mapNotNull { id -> model.data.player.pokemon.firstOrNull { it.id == id } }
                            val fit = WorldSim.suitability(d, members, world.guardianDone, world.legendFaster(d))
                            Text("Team ${team.id}: a floor takes about ${fit.floorMillis / 60_000} min; ${fit.favored} of ${fit.size} like this place.", style = MaterialTheme.typography.bodyMedium) // copy-ok: game
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            ready.forEach { team -> PixelButton("Send team ${team.id}", { if (model.store.worldStart(team.id, d.id)) model.effect(Sfx.STAIRS_DOWN) }, color = Module.WORLD.look.accent, compact = true) }
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
    "distortion-rift" to (83 to 75), "beach-cave" to (205 to 172), "drenched-bluff" to (156 to 130), "crystal-cave" to (393 to 100),
    "frosty-forest" to (72 to 270), "hidden-land" to (346 to 288),
)

/** The layer buttons (every layer is open from the first day): the one on show is lit; [onChange] runs after a switch. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorldLayerSwitch(model: AppModel, onChange: () -> Unit = {}) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        WorldLayer.entries.forEach { l ->
            TabButton(l.label, l == model.worldLayer, { model.worldLayer = l; onChange() })
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
                    AnimatedSprite(Dex.defaultForm(q.speciesId).id, false, q.name, size = Spacing.Target, animate = false)
                    Column(Modifier.weight(1f)) {
                        Text(q.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            when {
                                done -> "Joined you"
                                q.afterGuardian && !world.layerDone(layer) -> "Face the Guardian of ${layer.label} first"
                                else -> "${Legends.progress(world, q)} of ${q.clears}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (done) Text("Gives: ${q.bonusText()}", style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
                    }
                }
            }
        }
    }
}

/** A job's reward as icons: the Poké coin and amount, the XP, and the item if there is one. */
@Composable
private fun MissionReward(m: app.pokeplan.world.MissionDef) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        ItemIcon(app.pokeplan.world.PmdArt.POKE_COIN, size = Sizes.IconS, description = "Poké")
        Text("${m.poke}", style = MaterialTheme.typography.bodyMedium)
        Text("${m.xp} XP", style = MaterialTheme.typography.bodyMedium)
        m.itemId?.let { ItemIcon(it, size = Sizes.IconS, description = Items.name(it)); Text("x${m.itemCount}", style = MaterialTheme.typography.bodyMedium) }
    }
}

private fun missionIcon(kind: MissionKind): String = when (kind) {
    MissionKind.EXPLORE -> "town-map"
    MissionKind.COLLECT -> "seal-bag"
    MissionKind.RESCUE -> "friend-ball"
    MissionKind.DELIVER -> "journal"
    MissionKind.ESCORT -> "exp-share"
    MissionKind.DEFEAT -> "scope-lens"
    MissionKind.INVESTIGATION -> "scope-lens"
    MissionKind.CHALLENGE -> "exp-share"
    MissionKind.STORY -> "oaks-letter"
}


// ----- Missions -------------------------------------------------------------------------------------------------------

@Composable
private fun WorldMissionsView(model: AppModel) {
    val store = model.store
    val world = model.data.world
    if (!Missions.boardOpen(world)) {
        WorldBanner("map:sky-g01p02a", "The mission board", "Opens at Bronze rank", sky = Color(0xFF101038), top = true)
        return
    }
    WorldBanner("map:sky-g01p02a", "The mission board", "${world.missions.count { it.done }} ready to report, ${world.missions.size} of ${Missions.MAX_ACTIVE} taken", sky = Color(0xFF101038), top = true) // copy-ok: game
        world.missions.forEach { p ->
        val m = Missions.byId(p.id) ?: return@forEach
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    ItemIcon(missionIcon(m.kind), size = Sizes.Icon, description = null)
                    Text(m.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    if (p.done) Text("Done", style = MaterialTheme.typography.bodyLarge, color = Pixel.Green)
                }
                val where = m.dungeonId?.let { "floor ${m.floor} of ${Dungeons.byId(it)?.name}" }
                Text(
                    when (m.kind) {
                        MissionKind.COLLECT -> "Find ${m.count} berries: ${minOf(p.progress, m.count)} so far."
                        MissionKind.RESCUE -> "Reach $where to bring the lost Pokémon home." // copy-ok: game
                        MissionKind.EXPLORE -> "Reach $where."
                        MissionKind.ESCORT -> "Reach $where with a team of at least ${m.count} Pokémon." // copy-ok: game
                        MissionKind.DEFEAT -> {
                            val here = m.dungeonId?.let { Dungeons.byId(it) }
                            val best = here?.let { d -> world.teams.maxOfOrNull { t -> WorldSim.power(t.members.mapNotNull { id -> model.data.player.pokemon.firstOrNull { it.id == id } }, d) } } ?: 0.0
                            "Reach $where with a team of power ${m.count} or more (your best team: ${best.toInt()})." // copy-ok: game
                        }
                        MissionKind.INVESTIGATION -> "Open ${m.count} side paths in ${m.dungeonId?.let { Dungeons.byId(it)?.name }}: ${minOf(p.progress, m.count)} so far." // copy-ok: game
                        MissionKind.CHALLENGE -> "Reach $where with a team of at most ${m.count} ${if (m.count == 1) "Pokémon" else "Pokémon"}." // copy-ok: game
                        MissionKind.STORY -> "Open chapter ${m.count} of the ${Missions.layerOf(m).label} story."
                        MissionKind.DELIVER -> "Reach $where, then hand over ${m.giveCount} x ${Items.name(m.give ?: "")} from your Bag." // copy-ok: game
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                MissionReward(m)
                val needed = m.give?.let { it to m.giveCount }
                val haveIt = needed == null || model.data.player.inventory.has(needed.first, needed.second)
                if (p.done && !haveIt) Text("Needs ${needed!!.second} x ${Items.name(needed.first)} in your Bag.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Red)
                if (p.done) PixelButton("Report back", { if (store.worldClaimMission(p.id)) model.effect(Sfx.MISSION_SUCCESS) }, Modifier.fillMaxWidth(), Module.WORLD.look.accent, enabled = haveIt, compact = true)
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
                    ItemIcon(missionIcon(m.kind), size = Sizes.Icon, description = null)
                    Text("${m.kind.label}: ${m.title}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                }
                if (m.kind == MissionKind.RESCUE && m.rescued != null) RescueCall(m) else Text(m.story, style = MaterialTheme.typography.bodyMedium)
                val place = m.dungeonId?.let { id -> Dungeons.byId(id)?.let { "${it.name}, ${it.layer.label}, floor ${m.floor}" } } ?: "Any dungeon"
                Text("$place, ${entry.repeats}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                MissionReward(m)
                if (world.missions.size >= Missions.MAX_ACTIVE) Text("You hold ${Missions.MAX_ACTIVE} jobs: finish or give one up first.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Red) // copy-ok: game
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
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            WorldSkill.entries.forEachIndexed { i, skill ->
                if (i > 0) HorizontalDivider(color = Pixel.Border)
                val xp = world.skillXp[skill.name] ?: 0
                val level = world.skillLevel(skill)
                Text("${skill.label}, level $level", style = MaterialTheme.typography.titleSmall)
                Text("${skill.blurb}: ${skill.bonus}.", style = MaterialTheme.typography.bodyMedium)
                val helpers = world.teams.flatMap { t -> t.members.mapNotNull { id -> model.data.player.pokemon.firstOrNull { it.id == id } } }
                    .filter { it.ability in AbilityPerks.bySkill.getValue(skill) }
                if (helpers.isNotEmpty()) Text("Helped by ${helpers.joinToString { "${it.nickname} (${it.ability})" }}: +${minOf(helpers.size, AbilityPerks.MAX_BONUS)} levels while they explore.", style = MaterialTheme.typography.bodyMedium, color = Pixel.Green)
                if (level < WorldSkill.MAX_LEVEL) {
                    val from = WorldSkill.xpFor(level)
                    val to = WorldSkill.xpFor(level + 1)
                    PixelBar(((xp - from).toFloat() / (to - from)).coerceIn(0f, 1f), Modifier.fillMaxWidth(), Module.WORLD.look.accent, label = "$xp of $to")
                }
            }
        }
    }
}

/** The pages of the Town: its landing is the headquarters map and a menu, so nothing but the one page you opened is on screen (docs/pokelife/MODULES/WORLD.md section 2.1). */
/** The Town page (a [TownPage] name) a screenshot test opens on; null in the app, which opens on the menu. */
internal var townPageToOpen: String? = null

private enum class TownPage(val label: String, val icon: String) {
    FACILITIES("Facilities", "heart-scale"), CAFE("Café", "soothe-bell"),
    KECLEON("Kecleon Shop", "rare-candy"), SWAP("Swap Shop", "amulet-coin"), DOJO("Dojo", "scope-lens"),
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WorldTownView(model: AppModel) {
    var page by remember { mutableStateOf(townPageToOpen?.let { TownPage.valueOf(it) }) }
    val world = model.data.world
    val open = page
    if (open == null) {
        // The headquarters: the team base of your first partner as big as the screen allows, growing with the Camp, then rank and the places in it.
        WorldMapHero(baseMap(starterSpecies(model.data), world.facilityLevel(Facility.CAMP)), "Exploration base", "Camp level ${world.facilityLevel(Facility.CAMP)} of ${Facility.MAX_LEVEL}")
        MenuGrid(
            TownPage.entries.map { p ->
                val detail = when (p) {
                    TownPage.FACILITIES -> "Camp level ${world.facilityLevel(Facility.CAMP)}"
                    TownPage.CAFE -> "a brew for the road"
                    TownPage.KECLEON -> "stones ${app.pokeplan.world.KecleonShop.STONE_PRICE} Poké"
                    TownPage.SWAP -> "berry for berry"
                    TownPage.DOJO -> "${app.pokeplan.world.Dojo.COST} Poké a lesson"
                }
                MenuEntry(p.name, p.icon, p.label, detail, tint = Module.WORLD.look.accent)
            } + listOf(
                // Finds wait in the Town (WORLD.md 2.1); skills are the explorers' rank work. Both are sections of their own, opened from here.
                MenuEntry(WorldSection.BAG.name, WorldSection.BAG.icon, WorldSection.BAG.label, if (world.finds.isEmpty()) "no one waiting" else "${world.finds.size} to meet", badge = world.finds.size, tint = Module.WORLD.look.accent),
                MenuEntry(WorldSection.SKILLS.name, WorldSection.SKILLS.icon, WorldSection.SKILLS.label, "${app.pokeplan.world.WorldSkill.entries.size} skills", tint = Module.WORLD.look.accent),
            ),
        ) { key -> WorldSection.entries.firstOrNull { it.name == key && (it == WorldSection.BAG || it == WorldSection.SKILLS) }?.let { model.worldSection = it } ?: run { page = TownPage.valueOf(key) } }
        return
    }
    BackHandler(enabled = true) { page = null }
    val store = model.store
    val play = !(model.data.settings.reduceMotion || model.powerSaving)
    PixelButton("Back to the town", { page = null }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
    when (open) {
        TownPage.FACILITIES -> {
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
                if (level < Facility.MAX_LEVEL) PixelButton("Build level ${level + 1}: ${f.cost(level)} Poké", { if (store.worldUpgrade(f)) model.effect(Sfx.UNLOCK) }, Modifier.fillMaxWidth(), Module.WORLD.look.accent, enabled = store.data.player.coins >= f.cost(level), compact = true)
            }
        }
    }
        }
        TownPage.CAFE -> {
    WorldBanner("map:sky-p01p04a", "Spinda's Café", null, sky = Color(0xFF29180F), top = true, height = 150.dp)
    var steam by remember { mutableStateOf(0) }
    var cafeMessage by remember { mutableStateOf(ShopLines.SPINDA_WELCOME) }
    ShopVoice(Keepers.SPINDA, "Spinda", cafeMessage)
    val clock = model.worldNow.takeIf { it > 0 } ?: model.store.nowMillis
    val brewing = world.brewUntil > clock
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("A brew for the road", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                FxBurst("steam", steam, 56.dp, play = play)
            }
            Text("${app.pokeplan.world.Cafe.BERRIES} berries from your Bag make a brew for ${app.pokeplan.world.Cafe.HOURS} hours: 30% more Exploration XP, 5% more treasure chance and 1% more find chance on every floor.", style = MaterialTheme.typography.bodyMedium) // copy-ok: game
            if (brewing) Text("A brew is working for ${(world.brewUntil - clock) / 60_000} more minutes.", style = MaterialTheme.typography.bodyLarge, color = Pixel.Green) // copy-ok: game
            PixelButton("Brew", { cafeMessage = if (brewing) "A brew is still working." else if (store.worldBrew()) { steam++; model.effect(Sfx.EATING); ShopLines.SPINDA_BREWED } else ShopLines.SPINDA_NO_FOOD }, Modifier.fillMaxWidth(), Module.WORLD.look.accent, compact = true)
        }
    }
        }
        TownPage.KECLEON -> {
            WorldBanner("map:sky-t01p01a", "Kecleon Shop", "Items, for Poké", top = true, height = 150.dp)
            var kecleonSays by remember { mutableStateOf(ShopLines.KECLEON_WELCOME) }
            ShopVoice(Keepers.KECLEON, "Kecleon", kecleonSays)
    // Today's shelf first (it changes each day), then the stock that is always sold.
    val day = store.shopDay
    ShopShelf(model, "Today's shelf", app.pokeplan.world.KecleonShop.shelf(day)) { kecleonSays = if (it) ShopLines.KECLEON_BOUGHT else ShopLines.KECLEON_BROKE }
    ShopShelf(model, "Always in stock", app.pokeplan.world.KecleonShop.fixed) { kecleonSays = if (it) ShopLines.KECLEON_BOUGHT else ShopLines.KECLEON_BROKE }
        }
        TownPage.SWAP -> {
            WorldBanner("map:sky-t01p02a", "Croagunk Swap Shop", "Berry for berry", top = true, height = 150.dp)
    var swapFrom by remember { mutableStateOf(app.pokeplan.world.SwapShop.flavors[0]) }
    var swapTo by remember { mutableStateOf(app.pokeplan.world.SwapShop.flavors[1]) }
    var swapMessage by remember { mutableStateOf(ShopLines.SWAP_WELCOME) }
    ShopVoice(Keepers.CROAGUNK, "Croagunk", swapMessage)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text("Berry swap", style = MaterialTheme.typography.titleSmall)
            FieldLabel("Give")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                app.pokeplan.world.SwapShop.flavors.forEach { f -> PixelButton("$f x${model.data.player.inventory[Items.berry(f)]}", { swapFrom = f }, color = if (swapFrom == f) Module.WORLD.look.accent else Pixel.InkSoft, compact = true) }
            }
            FieldLabel("Get")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                app.pokeplan.world.SwapShop.flavors.forEach { f -> PixelButton(f, { swapTo = f }, color = if (swapTo == f) Module.WORLD.look.accent else Pixel.InkSoft, compact = true) }
            }
            PixelIconButton("repeat", "Swap", { swapMessage = if (store.worldSwap(swapFrom, swapTo)) { model.effect(Sfx.CONFIRM); ShopLines.SWAP_DONE } else ShopLines.SWAP_SHORT }, Module.WORLD.look.accent) // copy-ok: game
        }
    }
        }
        TownPage.DOJO -> {
            WorldBanner("makuhita-dojo", "Makuhita's Dojo", "${model.data.player.coins} Poké to spend", height = 150.dp)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            var hit by remember { mutableStateOf(0) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Training", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                FxBurst("hit", hit, 64.dp, play = play)
            }
            WorldSkill.entries.forEach { skill ->
                PixelButton("Train ${skill.label}", { if (store.worldTrain(skill)) { hit++; model.effect(Sfx.LEARN) } }, Modifier.fillMaxWidth(), Pixel.InkSoft, enabled = store.data.player.coins >= app.pokeplan.world.Dojo.COST && world.skillLevel(skill) < WorldSkill.MAX_LEVEL, compact = true)
            }
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
    ChoicesCard(model)
    ExpeditionOverview(model)
    // The dungeons the player can enter on the map on show; the editor suits the team to the chosen one (UI only, not saved).
    val enterable = Dungeons.of(model.worldLayer).filter { app.pokeplan.world.WorldGuide.lockReasons(world, it).isEmpty() }
    val fallback = app.pokeplan.world.WorldGuide.primaryAction(world, model.worldLayer).dungeonId?.let { Dungeons.byId(it) } ?: enterable.firstOrNull() ?: Dungeons.all.first()
    world.slots().forEach { team ->
        var editing by remember(team.id) { mutableStateOf(false) }
        val members = team.members.mapNotNull { id -> roster.firstOrNull { it.id == id } }
        val outAt = team.expedition?.let { Dungeons.byId(it.dungeonId) }
        val chosen = outAt ?: model.worldTeamDungeon?.let { Dungeons.byId(it) } ?: fallback
        // A team that is out is shown in a box made of its dungeon's own tiles; any other team in a plain one.
        val body: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Box(Modifier.size(14.dp).background(teamAccent(team.accent)))
                    Text(team.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    // Few of this team like the chosen place: a quiet mark, no sentence (the editor's Recommend team fixes it).
                    if (app.pokeplan.world.TeamAdvice.isPoorMatch(chosen, members)) {
                        Box(Modifier.semantics { contentDescription = "Few of this team suit ${chosen.name}" }) { app.pokeplan.ui.StatusIcon(app.pokeplan.ui.StatusKind.ALERT, pixel = 2, animate = false) }
                    }
                }
                if (members.isEmpty()) Text("No one yet.", style = MaterialTheme.typography.bodyMedium)
                else if (team.expedition == null) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    members.forEach { p -> AnimatedSprite(p.form.id, p.isShiny, p.nickname, size = Spacing.Target, animate = false) }
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
                        Text("A floor takes about ${per / 1000 / 60} min ${per / 1000 % 60} s", style = MaterialTheme.typography.bodyMedium) // copy-ok: game
                    }
                    if (outAt != null) SpeedExplainer(members, outAt, world)
                    PixelButton("Recall", { store.worldRecall(team.id); model.effect(Sfx.TEAM_RECALLED) }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                } else {
                    PixelButton(if (editing) "Close" else "Choose the team", { editing = !editing }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
                    if (editing) {
                        TeamEditor(model, team, chosen, enterable) { editing = false }
                    }
                }
            }
        }
        if (outAt != null) TileBox(outAt.id, members.map { TileWalker(it.form.id, it.isShiny, it.nickname) }, animate = !model.data.settings.reduceMotion, scrolls = model.data.settings.worldScroll, content = body) else TextBox { body() }
    }
    // Help: side paths (field moves, hazards), forks that wait 12 hours for an answer, and bountiful days are explained in Help.
}

/** The team editor: pick a dungeon, Recommend or Replace the team for it, or choose by hand (favoured types first; search reaches the whole PC). [initialAdvice] and [initialSearch] let a screenshot open it in a state. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TeamEditor(model: AppModel, team: app.pokeplan.world.WorldTeam, chosen: app.pokeplan.world.DungeonDef, enterable: List<app.pokeplan.world.DungeonDef>, initialAdvice: Boolean = false, initialSearch: String = "", onDone: () -> Unit) {
    val store = model.store
    val world = model.data.world
    val roster = model.data.player.pokemon
    var advice by remember(team.id, team.members) { mutableStateOf<app.pokeplan.world.TeamRecommendation?>(if (initialAdvice) store.worldRecommend(chosen.id, team.id) else null) }
    var search by remember(team.id) { mutableStateOf(initialSearch) }
    var picked by remember(team.id, team.members) { mutableStateOf(if (initialAdvice) advice?.ids ?: team.members else team.members) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        FieldLabel("Suit the team to")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            (enterable + chosen).distinctBy { it.id }.forEach { d ->
                PixelButton(d.name, { model.worldTeamDungeon = d.id; advice = null }, color = if (d.id == chosen.id) Module.WORLD.look.accent else Pixel.InkSoft, compact = true)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) { chosen.favored.forEach { TypePlate(it, width = 56.dp) } }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            PixelButton("Recommend team", { advice = store.worldRecommend(chosen.id, team.id)?.also { picked = it.ids } }, Modifier.weight(1f), Module.WORLD.look.accent, compact = true)
            PixelButton("Replace team", { if (store.worldReplaceTeam(team.id, chosen.id)) onDone() }, Modifier.weight(1f), Pixel.InkSoft, enabled = team.expedition == null, compact = true)
        }
        if (advice?.noMatch == true) Text("No one free likes ${chosen.name}: the strongest instead.", style = MaterialTheme.typography.bodyMedium) // copy-ok: game
        FieldLabel("Up to ${WorldTeam.MAX_MEMBERS} Pokémon")
        PixelField(search, { search = it.take(30) }, Modifier.fillMaxWidth(), singleLine = true, placeholder = "Search the PC")
        // The chosen slots stay on top; then the PC with the types the place favours first (the 20 strongest, or, searching, the whole PC by name).
        val idle = store.worldIdle()
        val query = search.trim()
        val pool = if (query.isEmpty()) idle.take(20) else idle.filter { it.nickname.contains(query, ignoreCase = true) || it.species.name.contains(query, ignoreCase = true) }
        val slots = (picked.mapNotNull { id -> roster.firstOrNull { it.id == id } } + roster.filter { it.id in team.members })
        val shown = (slots + pool.sortedByDescending { app.pokeplan.world.TeamAdvice.matching(chosen, it).isNotEmpty() }).distinctBy { it.id }
        shown.forEach { p ->
            val reason = advice?.picks?.firstOrNull { it.pokemon.id == p.id }?.takeIf { p.id in picked }?.reason
            ChoiceRow(selected = p.id in picked, onClick = { picked = if (p.id in picked) picked - p.id else if (picked.size < WorldTeam.MAX_MEMBERS) picked + p.id else picked }, lead = { AnimatedSprite(p.form.id, p.isShiny, p.nickname, size = Sizes.IconL, animate = false) }) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    Text("${p.nickname}, level ${p.level}", style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) { p.form.types.forEach { TypePlate(it, width = 48.dp) } }
                    if (reason != null) Text(reason, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                }
            }
        }
        if (shown.isEmpty() || (query.isNotEmpty() && pool.isEmpty())) Text("No one found.", style = MaterialTheme.typography.bodyMedium)
        // A name, an accent colour and saved teams: put one together again in a tap.
        var name by remember(team.id, team.name) { mutableStateOf(team.name) }
        FieldLabel("Name (up to ${WorldTeam.NAME_MAX} letters)")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            PixelField(name, { name = it.take(WorldTeam.NAME_MAX) }, Modifier.weight(1f), singleLine = true, placeholder = "Team ${team.id}")
            PixelIconButton("check", "Save the name", { store.worldRenameTeam(team.id, name) }, Pixel.Green, enabled = name.trim() != team.name)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            repeat(WorldTeam.ACCENTS) { i ->
                Box(
                    Modifier.size(Spacing.Target).clickable(role = Role.Button, onClickLabel = "Colour ${i + 1}") { store.worldRecolourTeam(team.id, i) }.padding(10.dp)
                        .background(teamAccent(i)).then(if (i == team.accent) Modifier.border(Sizes.Line, Pixel.Ink) else Modifier),
                )
            }
        }
        if (world.presets.isNotEmpty()) {
            FieldLabel("Saved teams")
            world.presets.forEach { pre ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    PixelButton("${pre.name} (${pre.members.size})", { if (store.worldApplyPreset(team.id, pre.name)) onDone() }, Modifier.weight(1f), Pixel.InkSoft, compact = true)
                    PixelIconButton("trash", "Delete ${pre.name}", { store.worldDeletePreset(pre.name) })
                }
            }
        }
        if (team.members.isNotEmpty() && world.presets.size < WorldData.MAX_PRESETS + 1) PixelButton("Save this team as a preset", { store.worldSavePreset(team.id, team.title) }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
        PixelButton("Save the team", { if (store.worldAssign(team.id, picked)) onDone() }, Modifier.fillMaxWidth(), Module.WORLD.look.accent, compact = true)
    }
}

// ----- Camp -----------------------------------------------------------------------------------------------------------

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
                    Text(SkyBarks.healthy(newcomer.speciesId), style = MaterialTheme.typography.bodyLarge)
                    Text("Level ${newcomer.level}", style = MaterialTheme.typography.bodyMedium)
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
                    Text("A wild $name, met on floor ${find.floor} of ${Dungeons.byId(find.dungeonId)?.name}, wants to join your team. Recruit it?", style = MaterialTheme.typography.bodyLarge) // copy-ok: game
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
                store.worldRecruit(index)?.let { p -> joined = p; model.cry(p); model.effect(Sfx.RECRUIT) }
                meeting = null
            },
            onLater = { meeting = null }, onOk = { joined = null },
        )
    }
    val worldLog = model.data.rewardLog.filter { it.source.startsWith("World") }.takeLast(4).reversed()
    if (world.finds.isEmpty() && world.treasure.isEmpty() && worldLog.isEmpty()) Text("Nothing waiting", style = MaterialTheme.typography.titleSmall)
    if (world.finds.isNotEmpty()) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            world.finds.forEachIndexed { i, f ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Sprite(Dex.defaultForm(f.speciesId).id, false, Dex.species(f.speciesId).name, size = Spacing.Target)
                    Column(Modifier.weight(1f)) {
                        Text(Dex.species(f.speciesId).name, style = MaterialTheme.typography.bodyLarge)
                        Text("Floor ${f.floor}, ${Dungeons.byId(f.dungeonId)?.name}", style = MaterialTheme.typography.bodyMedium)
                    }
                    PixelButton("Meet", { joined = null; meeting = i }, color = Module.WORLD.look.accent, compact = true)
                }
            }
        }
    }
    if (world.treasure.isNotEmpty() || worldLog.isNotEmpty()) TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            // The latest World entries of the reward history, so what a trip brought is still there after it.
            worldLog.forEach { entry ->
                val tag = entry.source.removePrefix("World").removePrefix(":").trim().replaceFirstChar { it.uppercase() }
                Text((if (tag.isEmpty()) "" else "$tag: ") + entry.rewards.joinToString { it.describe(entry.source) }, style = MaterialTheme.typography.bodyMedium)
            }
            world.treasure.entries.sortedBy { it.key }.forEach { (tier, n) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    ItemIcon(app.pokeplan.world.PmdArt.treasureBox(tier.coerceIn(1, 3)), size = Sizes.Icon, description = null)
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
    JournalLine.KIND_SETBACK -> "Setbacks"
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
                    Box(Modifier.size(Spacing.Target)) {
                        val name = app.pokeplan.core.dex.Dex.species(e.speciesId).name
                        AnimatedSprite(Dex.defaultForm(e.speciesId).id, false, if (e.met) name else "Not met yet", size = Spacing.Target, animate = false)
                        if (!e.met) Box(Modifier.size(Spacing.Target).background(Pixel.Panel.copy(alpha = 0.85f)))
                        if (e.owned) app.pokeplan.ui.Glyph("check", Pixel.Green, Modifier.align(Alignment.BottomEnd), size = 16.dp)
                    }
                }
            }
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
                    Text("${kind.story} The first way is taken in ${left / 60} h ${left % 60} min.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) // copy-ok: game
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
/** Research (WORLD.md 2.5): the species discovered, the steps that pay and each layer's notebook. A page of the Journal. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResearchPage(model: AppModel) {
    val world = model.data.world
    val store = model.store
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
                else PixelButton("Collect", { if (store.worldClaimResearch(step.species)) model.effect(Sfx.RESEARCH_DONE) }, color = Module.WORLD.look.accent, enabled = reached, compact = true)
            }
        }
    }
    NotebookCard(model)
}

/** The expedition log (WORLD.md 2.5): what the teams found and did, newest first, kept through a night away; filter it by kind. A page of the Journal. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpeditionLogPage(world: WorldData) {
    if (world.journal.isEmpty()) { Text("Nothing yet.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft); return }
    var kind by remember { mutableStateOf<String?>(null) }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                TabButton("All", kind == null, { kind = null })
                JournalLine.KINDS.filter { k -> world.journal.any { it.kind == k } }.forEach { k -> TabButton(journalKindLabel(k), kind == k, { kind = k }) }
            }
            val lines = world.journal.filter { kind == null || it.kind == kind }
            if (lines.isEmpty()) Text("Nothing of that kind yet.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            lines.takeLast(if (kind == null) 20 else 40).reversed().forEach { line ->
                Text(if (line.place.isNotEmpty() && kind != null) "${line.text} (${line.place})" else line.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Dungeon records (WORLD.md 2.5): the deepest floor and the clears of every dungeon entered, and the Pokémon World's finds brought into the collection. */
@Composable
private fun RecordsPage(model: AppModel) {
    val world = model.data.world
    val entered = Dungeons.all.filter { (world.deepest[it.id] ?: 0) > 0 || (world.clears[it.id] ?: 0) > 0 }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            FieldLabel("Dungeons")
            if (entered.isEmpty()) Text("No dungeon entered yet.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
            entered.forEach { d ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Text(d.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text("floor ${world.deepest[d.id] ?: 0} of ${d.floors}, cleared ${world.clears[d.id] ?: 0}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                }
            }
        }
    }
    val recruited = model.data.player.pokemon.filter { model.data.origins[it.id]?.how == Origin.How.WORLD }
    // The game's Adventure Log wording (Explorers of Sky "Adventure Log Entries") for the counters World keeps.
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            FieldLabel("Adventure Log")
            Text("Pokémon that joined you: ${recruited.size}", style = MaterialTheme.typography.bodyLarge)
            Text("Dungeons cleared: ${world.clears.values.sum()}", style = MaterialTheme.typography.bodyLarge)
        }
    }
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            FieldLabel("Recruited")
            if (recruited.isEmpty()) Text("No one has joined from a find yet.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft) // copy-ok: game
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                recruited.forEach { p ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedSprite(p.form.id, p.isShiny, p.nickname, size = Spacing.Target, animate = false)
                        Text(p.nickname, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** The pages of the Journal (WORLD.md section 2.5): a menu, so only the page you opened is on screen. */
private enum class JournalPage(val label: String, val icon: String) {
    LOG("Log", "exp-share"), RESEARCH("Research", "journal"), STORY("Story", "oaks-letter"), RECORDS("Records", "town-map"),
}

/** The Journal: the expedition log, research, the story archive and the records, one at a time. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WorldJournalView(model: AppModel) {
    var page by remember { mutableStateOf<JournalPage?>(null) }
    val world = model.data.world
    val open = page
    if (open == null) {
        MenuList(
            JournalPage.entries.map { p ->
                val count = when (p) {
                    JournalPage.LOG -> "${world.journal.size}"
                    JournalPage.RESEARCH -> "${world.discovered.size}"
                    JournalPage.STORY -> "${app.pokeplan.world.WorldMoments.reached(world).size}"
                    JournalPage.RECORDS -> "${world.clears.values.sum()}"
                }
                MenuEntry(p.name, p.icon, p.label, badge = if (p == JournalPage.RESEARCH) world.researchReady().size else 0, tint = Module.WORLD.look.accent, count = count)
            },
        ) { key -> page = JournalPage.valueOf(key) }
        return
    }
    BackHandler(enabled = true) { page = null }
    PixelButton("Back to the journal", { page = null }, Modifier.fillMaxWidth(), Pixel.InkSoft, compact = true)
    when (open) {
        JournalPage.LOG -> ExpeditionLogPage(world)
        JournalPage.RESEARCH -> ResearchPage(model)
        JournalPage.STORY -> WorldArchiveView(model)
        JournalPage.RECORDS -> RecordsPage(model)
    }
}

@Composable
private fun WorldArchiveView(model: AppModel) {
    val world = model.data.world
    val moments = app.pokeplan.world.WorldMoments.reached(world)
    WorldBanner("guild-room", "Story archive", "${moments.size} moments to watch again", sky = Color(0xFF101038), top = true)
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            if (moments.any { it == app.pokeplan.world.WorldMoment.Welcome }) {
                FieldLabel("The opening")
                ArchiveRow("Mew's Unofficial Welcome", "How the team began", app.pokeplan.world.WorldMoment.Welcome, model, icon = "town-map")
            }
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
                        AnimatedSprite(Dex.defaultForm(c.speciesId!!).id, false, c.displayName, size = Spacing.Target, animate = false)
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
    // The optional episodes after a story: each opens when its layer's story is complete.
    WorldLayer.entries.forEach { layer ->
        val specials = app.pokeplan.world.story.StoryCatalog.specialsOf(layer)
        if (specials.isEmpty() || !world.layerDone(layer)) return@forEach
        TextBox {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                FieldLabel("${layer.label}: after the story")
                specials.forEach { e -> ArchiveRow(e.title, e.summary, app.pokeplan.world.WorldMoment.SpecialEpisode(layer, e.chapter), model, icon = "town-map") }
            }
        }
    }
    if (moments.isEmpty()) Text("Nothing yet.", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
}

@Composable
private fun ArchiveRow(title: String, detail: String, moment: app.pokeplan.world.WorldMoment, model: AppModel, icon: String?, speciesId: Int? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        if (speciesId != null) AnimatedSprite(Dex.defaultForm(speciesId).id, false, title, size = Sizes.IconL, animate = false)
        else if (icon != null) ItemIcon(icon, size = Sizes.IconL, description = null)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
        }
        PixelIconButton("play", "Watch $title again", { model.cutscene = Cutscene.World(moment) })
    }
}

/** A shelf of the Kecleon Shop: each item with its icon, how many the Bag holds, and its price. */
@Composable
private fun ShopShelf(model: AppModel, title: String, offers: List<app.pokeplan.world.ShopOffer>, said: (Boolean) -> Unit = {}) {
    val store = model.store
    TextBox {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            offers.forEach { o ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    app.pokeplan.ui.ItemIcon(o.itemId, size = app.pokeplan.ui.theme.Sizes.Icon)
                    Column(Modifier.weight(1f)) {
                        Text((if (o.count > 1) "${o.count} " else "") + Items.name(o.itemId), style = MaterialTheme.typography.bodyLarge)
                        Text(ShopLines.itemText(Items.name(o.itemId)) ?: "In the Bag: ${model.data.player.inventory[o.itemId]}", style = MaterialTheme.typography.bodyMedium, color = Pixel.InkSoft)
                    }
                    PixelButton("${o.price} Poké", { val bought = store.worldBuy(o.itemId); if (bought) model.effect(Sfx.CONFIRM); said(bought) }, color = Module.WORLD.look.accent, enabled = model.data.player.coins >= o.price, compact = true)
                }
            }
        }
    }
}
