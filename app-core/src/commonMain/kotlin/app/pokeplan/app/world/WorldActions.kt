package app.pokeplan.app.world

import app.pokeplan.app.quest.Quest
import app.pokeplan.app.quest.Quests
import app.pokeplan.app.quest.Requirement
import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.dex.Egg
import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.dex.Region
import app.pokeplan.core.pokemon.CandyRules
import app.pokeplan.core.pokemon.EvolutionContext
import app.pokeplan.core.pokemon.EvolutionOption
import app.pokeplan.core.pokemon.EvolutionRules
import app.pokeplan.core.pokemon.FriendshipRules
import app.pokeplan.core.pokemon.Guardians
import app.pokeplan.core.pokemon.Hatching
import app.pokeplan.core.pokemon.ItemCategory
import app.pokeplan.core.pokemon.ItemEffects
import app.pokeplan.core.pokemon.ItemUse
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.GearTool
import app.pokeplan.world.Missions
import app.pokeplan.world.WorldReport
import app.pokeplan.world.ReportSummary
import app.pokeplan.world.WorldSim
import app.pokeplan.core.pokemon.Moveset
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.pokemon.PerkBonuses
import app.pokeplan.core.pokemon.PerkSource
import app.pokeplan.core.pokemon.Perks
import app.pokeplan.core.pokemon.Transfer
import app.pokeplan.core.pokemon.TransferBlock
import app.pokeplan.core.reward.CandyType
import app.pokeplan.core.reward.RewardRules
import app.pokeplan.core.reward.TownFinds
import app.pokeplan.core.pokemon.Inventory
import app.pokeplan.core.pokemon.Shop
import app.pokeplan.core.reward.AchievementEvent
import app.pokeplan.core.reward.BefriendEvent
import app.pokeplan.core.reward.VisitorInheritance
import app.pokeplan.core.reward.CareEvent
import app.pokeplan.core.reward.CareKind
import app.pokeplan.core.reward.HealEvent
import app.pokeplan.core.reward.HealKind
import app.pokeplan.core.reward.BaseEvent
import app.pokeplan.core.reward.BaseKind
import app.pokeplan.core.reward.LinkEvent
import app.pokeplan.core.reward.LinkMoment
import app.pokeplan.core.reward.MintEvent
import app.pokeplan.core.reward.MintKind
import app.pokeplan.heal.HealRules
import app.pokeplan.core.reward.EffortEvent
import app.pokeplan.core.reward.Feature
import app.pokeplan.core.reward.TrainerRank
import app.pokeplan.core.reward.Reward
import app.pokeplan.core.reward.RewardEngine
import app.pokeplan.plan.LinkKind
import app.pokeplan.plan.TaskLink
import app.pokeplan.plan.FailReason
import app.pokeplan.plan.FocusEvent
import app.pokeplan.plan.FocusRecord
import app.pokeplan.plan.FocusRules
import app.pokeplan.plan.FocusSession
import app.pokeplan.plan.Goal
import app.pokeplan.plan.Insights
import app.pokeplan.plan.GoalRules
import app.pokeplan.plan.Habit
import app.pokeplan.plan.EventRepeat
import app.pokeplan.plan.PlanCalendar
import app.pokeplan.plan.PlanEvent
import app.pokeplan.plan.Milestone
import app.pokeplan.plan.MiniQuest
import app.pokeplan.plan.MiniQuests
import app.pokeplan.plan.PickForMe
import app.pokeplan.plan.MilestoneResult
import app.pokeplan.plan.Reviews
import app.pokeplan.plan.FrequencyProgress
import app.pokeplan.plan.FrequencyTarget
import app.pokeplan.plan.Frequency
import app.pokeplan.plan.HabitTarget
import app.pokeplan.plan.Phase
import app.pokeplan.plan.Rank
import app.pokeplan.plan.Recurrence
import app.pokeplan.plan.Repeat
import app.pokeplan.plan.DueReminder
import app.pokeplan.plan.ReminderOffset
import app.pokeplan.plan.Reminders
import app.pokeplan.plan.RecurringTasks
import app.pokeplan.plan.Streaks
import app.pokeplan.plan.Subtask
import app.pokeplan.plan.Task
import app.pokeplan.plan.TaskList
import app.pokeplan.plan.TaskRules
import app.pokeplan.plan.TaskState
import app.pokeplan.plan.TaskViews
import app.pokeplan.plan.toEpochMillis
import app.pokeplan.plan.toLocalDateTime
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.random.Random
import app.pokeplan.app.gear.GearTimers
import app.pokeplan.app.plan.LinkOwner
import app.pokeplan.app.plan.LinkState
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.app.storage.SavedDocument
import app.pokeplan.app.storage.Storage
import app.pokeplan.app.storage.newId
import app.pokeplan.app.AppData
import app.pokeplan.app.GameStore
import app.pokeplan.app.LedgerNote
import app.pokeplan.app.Origin
import app.pokeplan.app.Place
import app.pokeplan.app.Places
import app.pokeplan.app.RewardEntry
import app.pokeplan.app.World

/** World's actions on the shared save (the rules are in world-core; docs/pokelife `MODULES/WORLD.md` section 14). Split out of [GameStore]. */
// ----- World: the dungeon game (docs/pokelife/MODULES/WORLD.md section 14). Its rules are in world-core; this keeps its save and moves what it finds into the shared collection and Bag -----

fun GameStore.worldReport(): ReportSummary? = worldSummary?.takeIf { !it.isEmpty }

/** The player has read the report: it starts again from nothing. */
fun GameStore.worldReportDismiss() { worldSummary = null }

/** Settles every World team to now (floors climbed, finds, items) and returns what happened, or null when nothing did. */
fun GameStore.worldSettle(): WorldReport? {
    // A Pokémon transferred away leaves its team (an empty team comes home), so no slot is held by someone who is gone.
    val kept = data.world.pruned(data.player.pokemon.map { it.id }.toSet())
    if (kept !== data.world) update { it.copy(world = kept) }
    val before = data.world
    val report = WorldSim.settle(before, data.player.pokemon, nowMillis)
    if (report.isEmpty) {
        // A clock set back moves a team's settle point to now without any floor (WORLD.md 3.1): keep that, or the team waits for the clock to catch up.
        if (report.data.teams != before.teams) update { it.copy(world = it.world.copy(teams = report.data.teams)) }
        worldGuardian()
        worldLegends()
        return null
    }
    // One write carries the floors, the members' experience, the Poké, the items in the Bag and the ledger note (event id below), so the database
    // settles this climb as one event of its own. The id names each team's expedition and the floors it covers; the cursor moves with them, so a
    // floor has one id and is never settled twice.
    val id = "world:expedition:" + report.perTeam.keys.sorted().joinToString("+") { t ->
        val from = before.team(t)?.expedition
        "$t.${from?.startedAt}.${from?.floorsClimbed}-${report.data.team(t)?.expedition?.floorsClimbed}"
    }
    worldPay("World: exploring", id, "expedition", "expedition") { d ->
        val partner = d.player.pokemon.filter { it.inParty }.maxOfOrNull { it.level }
        d.copy(world = report.data, player = d.player.copy(pokemon = d.player.pokemon.map { p ->
            val floors = report.memberFloors[p.id] ?: return@map p
            // Every Pokémon that climbed floors gets the full experience for each (WORLD.md 5.2): [CandyRules.XP_PER_FLOOR] a floor, not divided among the team.
            p.gainXp(floors * CandyRules.XP_PER_FLOOR, partner ?: p.level)
        }))
    }
    worldSummary = (worldSummary ?: ReportSummary()) + ReportSummary.of(report, report.data.xp - before.xp, CandyRules.XP_PER_FLOOR)
    worldGuardian()
    worldLegends()
    return report
}

/** The story's end: the Guardian, Primal Dialga, joins the Guardian collection once (docs/pokelife/MODULES/WORLD.md section 14). The mastery bonus is [app.pokeplan.world.Story.GUARDIAN_BONUS]. */
private fun GameStore.worldGuardian() {
    val w = data.world
    if (!w.guardianDone || w.guardianGranted) return
    // The flag and the Pokémon are one write that re-checks the flag under the store's lock, so the widget and the app cannot both grant it.
    update { d ->
        if (!d.world.guardianDone || d.world.guardianGranted) d
        else grantIn(d.copy(world = d.world.copy(guardianGranted = true)), app.pokeplan.world.Story.GUARDIAN_SPECIES, GameStore.WORLD_SOURCE, form = app.pokeplan.world.Story.GUARDIAN_FORM).first
    }
}

/**
 * The legendary quests of the layers (docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md, "World regions"): each Pokémon whose quest is met (its dungeon cleared enough
 * times, and for the end of a layer's story its Guardian faced) joins the Guardian collection once. They are not eggs; the reward line
 * is in the log.
 */
private fun GameStore.worldLegends() {
    for (quest in app.pokeplan.world.Legends.ready(data.world)) {
        // Every legendary also has a quest in the Plan side's quest list (Quests): a Pokémon already owned from there is not given twice,
        // and the quest still counts as done in World, so its boost for the layer applies either way.
        // Dialga is the exception: Primal Dialga (the home Guardian) is a form of its own, so owning it does not stand in for Sinnoh's Dialga.
        val own = app.pokeplan.core.dex.Dex.defaultForm(quest.speciesId).identifier
        // One write that re-checks the quest under the store's lock, like the Guardian above.
        update { d ->
            if (quest.id in d.world.legends) d
            else {
                val marked = d.copy(world = d.world.copy(legends = d.world.legends + quest.id))
                if (marked.player.pokemon.none { it.speciesId == quest.speciesId && it.form.identifier == own }) grantIn(marked, quest.speciesId, GameStore.WORLD_SOURCE).first else marked
            }
        }
    }
}

/**
 * Applies a World change that pays (floors, a job, research, a treasure box) as ONE write: the change, the Poké it earned
 * (`WorldData.pokeEarned`, folded into the balance by [update]), the items World staged moved into the shared Bag, the line in the reward log
 * and a [LedgerNote] under [eventId], so the database ledger records this event by its own stable id instead of a net change
 * (DATA_AND_MIGRATIONS.md section 7.3.0; WORLD.md 3.1 and 6). Entries of the same [source] within ten minutes share a log line, so a team that
 * climbs while the screen is open does not flood the log.
 */
private fun GameStore.worldPay(source: String, eventId: String, ref: String, kind: String, change: (AppData) -> AppData) {
    update { d ->
        val changed = change(d)
        val earned = changed.world.pokeEarned - d.world.pokeEarned
        val staged = changed.world.items
        val paid = mutableListOf<Reward>()
        earned.takeIf { it > 0 }?.let { paid += Reward.Poke(it) }
        staged.forEach { (id, n) -> (n - (d.world.items[id] ?: 0)).takeIf { it > 0 }?.let { paid += Reward.ItemDrop(id, it) } }
        // The staging area is emptied into the Bag in the same write (one inventory).
        val moved = changed.copy(
            player = changed.player.copy(inventory = staged.entries.fold(changed.player.inventory) { bag, (id, n) -> bag.add(id, n) }),
            world = changed.world.copy(items = emptyMap()),
        )
        val logged = if (paid.isEmpty()) moved else {
            val last = moved.rewardLog.lastOrNull()
            moved.copy(rewardLog = if (last != null && last.source == source && nowMillis - last.atMillis < 10 * 60_000L) {
                val poke = (last.rewards + paid).filterIsInstance<Reward.Poke>().sumOf { it.amount }
                val items = (last.rewards + paid).filterIsInstance<Reward.ItemDrop>().groupBy { it.itemId }.map { (id, l) -> Reward.ItemDrop(id, l.sumOf { it.amount }) }
                moved.rewardLog.dropLast(1) + last.copy(atMillis = nowMillis, rewards = listOfNotNull(poke.takeIf { it > 0 }?.let { Reward.Poke(it) }) + items)
            } else (moved.rewardLog + RewardEntry(nowMillis, source, paid, seen = true)).takeLast(GameStore.LOG_SIZE))
        }
        noted(d, logged, eventId, "WORLD", ref, kind, nowMillis, extraPoke = earned)
    }
}

/**
 * Sets who is on World team [team]; false when the rank, the team or the Pokémon do not allow it. A Pokémon newly put on a team is taken
 * from its party, the buddy, a Guardian slot, another team or a helper job (one place only), unless that team is out on an expedition.
 */
fun GameStore.worldAssign(team: Int, members: List<String>): Boolean {
    if (members.any { id -> data.player.pokemon.none { it.id == id } }) return false
    val already = data.world.team(team)?.members.orEmpty().toSet()
    val incoming = members.filter { it !in already }
    if (incoming.any { !Places.movable(data, it) }) return false
    if (incoming.fold(data) { d, id -> Places.vacate(d, id) }.world.assign(team, members) == null) return false
    // Built from the document being changed, not from the copy checked above: the save may have moved on in between (a widget, a notification).
    update { d ->
        val moved = incoming.fold(d) { m, id -> Places.vacate(m, id) }
        moved.world.assign(team, members)?.let { moved.copy(player = Places.leads(moved.player), world = it) } ?: d
    }
    return true
}

/** Names World team [team] (empty goes back to "Team n"). */
fun GameStore.worldRenameTeam(team: Int, name: String): Boolean {
    val next = data.world.renameTeam(team, name) ?: return false
    update { it.copy(world = next) }
    return true
}

/** Gives World team [team] the accent number [accent]. */
fun GameStore.worldRecolourTeam(team: Int, accent: Int): Boolean {
    val next = data.world.recolourTeam(team, accent) ?: return false
    update { it.copy(world = next) }
    return true
}

/** Saves who is on World team [team] as a preset called [name]. */
fun GameStore.worldSavePreset(team: Int, name: String): Boolean {
    val members = data.world.team(team)?.members ?: return false
    val next = data.world.savePreset(name, members) ?: return false
    update { it.copy(world = next) }
    return true
}

fun GameStore.worldDeletePreset(name: String) = update { it.copy(world = it.world.deletePreset(name)) }

/** Puts the preset [name] on World team [team]: whoever of it is still owned and in the PC (or already on this team): a Pokémon in a party, slotted or helping is not taken. */
fun GameStore.worldApplyPreset(team: Int, name: String): Boolean {
    val onTeam = data.world.team(team)?.members.orEmpty().toSet()
    val owned = data.player.pokemon.map { it.id }.filter { it in onTeam || Places.of(data, it) == Place.Pc }.toSet()
    val next = data.world.applyPreset(team, name, owned) ?: return false
    update { it.copy(world = next) }
    return true
}

fun GameStore.worldStart(team: Int, dungeonId: String): Boolean {
    worldSettle()
    val next = data.world.start(team, dungeonId, nowMillis) ?: return false
    update { it.copy(world = next) }
    return true
}

/**
 * Sends the strongest team to [dungeonId] in one step: the first team slot that is not out takes the best (up to four) of its own
 * members and the idle Pokémon (see [worldIdle]) **for that dungeon**, so a type the dungeon favors counts for its 20% more
 * ([WorldSim.bestTeam]) rather than levels alone; a Guardian candidate is never picked. The team keeps its members when they are
 * already the best. False when every team is out, no one is free, or the dungeon is not open.
 */
fun GameStore.worldQuickSend(dungeonId: String): Boolean {
    worldSettle()
    val dungeon = app.pokeplan.world.Dungeons.byId(dungeonId) ?: return false
    val slot = data.world.slots().firstOrNull { it.expedition == null } ?: return false
    val own = slot.members.mapNotNull { id -> data.player.pokemon.firstOrNull { it.id == id } }
    val best = WorldSim.bestTeam(dungeon, (own + worldIdle()).distinctBy { it.id })
    if (best.isEmpty()) return false
    if (best.map { it.id }.toSet() != slot.members.toSet() && !worldAssign(slot.id, best.map { it.id })) return false
    return worldStart(slot.id, dungeonId)
}

/**
 * The Pokémon free to join a World team, strongest first: in the PC: not the buddy, not in a party, not slotted as a Guardian, not helping a tool and not on a team
 * (one place only; choosing one for a team moves it, so the others are offered by moving them first).
 */
fun GameStore.worldIdle(): List<OwnedPokemon> = data.player.pokemon
    .filter { Places.of(data, it.id) == Place.Pc }
    .sortedWith(compareByDescending<OwnedPokemon> { it.level }.thenBy { it.id })

/** Answers the World choice [id] with [option] (0 or 1), after settling what is due; false when there is no such choice. */
fun GameStore.worldChoose(id: String, option: Int): Boolean {
    worldSettle()
    val next = data.world.resolveChoice(id, option, nowMillis) ?: return false
    worldPay("World: exploring", "world:choice:$id", id, "choice") { it.copy(world = next) }
    return true
}

/** Counts a story scene as watched to its end (only the record: replaying grants nothing). */
fun GameStore.worldSceneDone(id: String) { if (id !in data.world.scenesDone) update { it.copy(world = it.world.copy(scenesDone = it.world.scenesDone + id)) } }

/** Counts a chapter's episode as watched to its end (and forgets where it left off). */
fun GameStore.worldEpisodeDone(key: String, flags: Set<String> = emptySet()) {
    if (key !in data.world.episodesDone || key in data.world.storyResume || !data.world.storyFlags.containsAll(flags)) update { it.copy(world = it.world.copy(episodesDone = it.world.episodesDone + key, storyResume = it.world.storyResume - key, storyFlags = it.world.storyFlags + flags)) }
    // Mew keeps its word: having agreed to join in the opening, it waits in Finds, once (the gift flag stops a replay from giving a second).
    val world = data.world
    if (key == app.pokeplan.world.WorldMoments.INTRO_KEY && app.pokeplan.world.story.IntroMewChapter.MEW_JOINED in world.storyFlags && GameStore.MEW_GIFT !in world.storyFlags) {
        update { it.copy(world = it.world.copy(storyFlags = it.world.storyFlags + GameStore.MEW_GIFT, finds = it.world.finds + app.pokeplan.world.Find(151, "tiny-woods", 1, nowMillis))) }
    }
}

/** Notes the story flags set so far (answers to choices) and the next scene of an unfinished episode, so it can start there next time. */
fun GameStore.worldStoryProgress(key: String, nextScene: Int, flags: Set<String>) {
    if (data.world.storyResume[key] != nextScene || !data.world.storyFlags.containsAll(flags)) update { it.copy(world = it.world.copy(storyResume = it.world.storyResume + (key to nextScene), storyFlags = it.world.storyFlags + flags)) }
}

/** The next World moment to show (a new rank, a chapter opening) or null; first counts what an older save already reached as seen. */
fun GameStore.worldMoment(): app.pokeplan.world.WorldMoment? {
    val adopted = app.pokeplan.world.WorldMoments.adopted(data.world)
    if (adopted != data.world) update { it.copy(world = adopted) }
    return app.pokeplan.world.WorldMoments.pending(adopted).firstOrNull()
}

/** Counts [moment] as shown, so it plays once. */
fun GameStore.worldMomentSeen(moment: app.pokeplan.world.WorldMoment) = update { it.copy(world = app.pokeplan.world.WorldMoments.seen(it.world, moment)) }

/** Brings a team home after settling what it found. */
fun GameStore.worldRecall(team: Int) {
    worldSettle()
    data.world.recall(team)?.let { next -> update { it.copy(world = next) } }
}

/** Recruits the find at [index] into the shared collection: the same persistent Pokémon as any other (docs/pokelife/MODULES/WORLD.md section 14). Null when there is none. */
fun GameStore.worldRecruit(index: Int): OwnedPokemon? {
    val find = data.world.finds.getOrNull(index) ?: return null
    // Taking the find off the list and granting the Pokémon are one write, so an interruption between them cannot leave a Pokémon both joined and still waiting.
    var mon: OwnedPokemon? = null
    update { d ->
        if (d.world.finds.getOrNull(index)?.speciesId != find.speciesId) d
        else grantIn(d.copy(world = d.world.withoutFind(index)), find.speciesId, GameStore.WORLD_SOURCE, Origin.How.WORLD).also { mon = it.second }.first
    }
    return mon
}

/** Takes a job from the World mission board; false when it is not offered or three are held. */
fun GameStore.worldAcceptMission(id: String): Boolean {
    worldSettle()
    val next = data.world.copy(lastSeenAt = nowMillis).acceptMission(id, utcOffsetMillis) ?: return false
    update { it.copy(world = next) }
    return true
}

fun GameStore.worldAbandonMission(id: String) {
    data.world.abandonMission(id)?.let { next -> update { it.copy(world = next) } }
}

/** Claims a finished job: Poké and Exploration XP go to World, its item to the shared Bag, and the reward log records it. */
fun GameStore.worldClaimMission(id: String): Boolean {
    worldSettle()
    val give = Missions.byId(id)?.takeIf { it.give != null }
    if (give != null && !data.player.inventory.has(give.give!!, give.giveCount)) return false
    val next = data.world.copy(lastSeenAt = nowMillis).claimMission(id, utcOffsetMillis) ?: return false
    // A job can come back on another day, so the day (in the player's zone) is part of its event id.
    worldPay("World: jobs", "world:job:$id:${(nowMillis + utcOffsetMillis).floorDiv(86_400_000L)}", id, "job") { d ->
        d.copy(world = next, player = if (give != null) d.player.copy(inventory = d.player.inventory.remove(give.give!!, give.giveCount)) else d.player)
    }
    return true
}

/** Builds the next level of a World facility for Poké. False when it is at the top or Poké is short. */
fun GameStore.worldUpgrade(facility: app.pokeplan.world.Facility): Boolean {
    worldSettle()
    val cost = data.world.upgradeCost(facility)
    val next = data.world.upgrade(facility, data.player.coins) ?: return false
    update { it.copy(world = next, player = it.player.copy(coins = it.player.coins - cost)) }
    return true
}

/** Claims the World research milestone at [species] discovered species. */
fun GameStore.worldClaimResearch(species: Int): Boolean {
    worldSettle()
    val next = data.world.claimResearch(species) ?: return false
    worldPay("World: research", "world:research:$species", "research:$species", "research") { it.copy(world = next) }
    return true
}

/** Brews at the Café: three berries from the Bag, then three hours of luckier floors. False without the berries. */
fun GameStore.worldBrew(): Boolean {
    worldSettle()
    val bag = data.player.inventory
    val berries = bag.counts.filterKeys { Items.category(it) == app.pokeplan.core.pokemon.ItemCategory.BERRY }.entries.sortedBy { it.key }
    if (berries.sumOf { it.value } < app.pokeplan.world.Cafe.BERRIES) return false
    val brewed = data.world.brew(nowMillis) ?: return false
    var need = app.pokeplan.world.Cafe.BERRIES
    var left = bag
    for ((id, n) in berries) { val take = minOf(n, need); left = left.remove(id, take); need -= take; if (need == 0) break }
    update { d -> d.copy(player = d.player.copy(inventory = left), world = brewed) }
    return true
}

/** Today's day number at the Kecleon Shop (the player's local day), for its daily shelf. */
val GameStore.shopDay: Long get() = (nowMillis + utcOffsetMillis).floorDiv(86_400_000L)

/**
 * Buys [itemId] at the Kecleon Shop for Poké, from the fixed stock or today's shelf ([app.pokeplan.world.KecleonShop]): the price comes off the
 * balance and the items go into the Bag in one update. False when it is not on sale today or Poké is short.
 */
fun GameStore.worldBuy(itemId: String): Boolean {
    val offer = app.pokeplan.world.KecleonShop.offer(shopDay, itemId) ?: return false
    if (data.player.coins < offer.price) return false
    update { d ->
        val (bag, extra) = d.player.inventory.addCapped(offer.itemId, offer.count)
        d.copy(player = d.player.copy(coins = d.player.coins - offer.price + extra, inventory = bag))
    }
    return true
}

/** The Swap Shop: three berries of the flavour [from] for one of the flavour [to]. False without the berries or when the flavours are the same. */
fun GameStore.worldSwap(from: String, to: String): Boolean {
    if (from == to || from !in app.pokeplan.world.SwapShop.flavors || to !in app.pokeplan.world.SwapShop.flavors) return false
    val give = Items.berry(from)
    if (!data.player.inventory.has(give, app.pokeplan.world.SwapShop.GIVE)) return false
    update { d -> d.copy(player = d.player.copy(inventory = d.player.inventory.remove(give, app.pokeplan.world.SwapShop.GIVE).add(Items.berry(to), 1))) }
    return true
}

/** Dojo training: Poké into skill XP. */
fun GameStore.worldTrain(skill: app.pokeplan.world.WorldSkill): Boolean {
    val next = data.world.train(skill, data.player.coins) ?: return false
    update { it.copy(world = next, player = it.player.copy(coins = it.player.coins - app.pokeplan.world.Dojo.COST)) }
    return true
}

/** Opens a treasure box of [tier]: berries, and from the silver tier a chance of a Rare Candy; what is inside goes to the Bag and the reward log. Returns what was found. *(tune)* */
fun GameStore.worldOpenTreasure(tier: Int): Map<String, Int> {
    if ((data.world.treasure[tier] ?: 0) <= 0) return emptyMap()
    val random = Random(nowMillis xor (tier.toLong() shl 40) xor data.world.xp.toLong())
    val found = mutableMapOf<String, Int>()
    fun add(id: String, n: Int) { found[id] = (found[id] ?: 0) + n }
    repeat(1 + tier) { add(Items.berry(listOf("sweet", "dry", "sour", "spicy", "bitter").random(random)), 1) }
    // A Rare Candy: 15% from a silver box, 35% from a gold one (the simulation showed the first guesses of 60% and 175% paid hundreds a year).
    if (tier >= 2 && random.nextInt(100) < (if (tier >= 3) 35 else 15)) add(Items.RARE_CANDY, 1)
    worldPay("World: treasure", "world:treasure:$tier:$nowMillis", "treasure:$tier", "treasure") { d ->
        d.copy(world = d.world.copy(
            treasure = d.world.treasure + (tier to d.world.treasure.getValue(tier) - 1),
            items = found.entries.fold(d.world.items) { m, (id, n) -> m + (id to (m[id] ?: 0) + n) },
        ))
    }
    return found
}

/**
 * The team [TeamAdvice] would put on World team [team] for [dungeonId]: chosen from the idle PC Pokémon and the team's own members, never the
 * buddy, a party, a Guardian slot or another place. Null for an unknown dungeon or team.
 */
fun GameStore.worldRecommend(dungeonId: String, team: Int? = null): app.pokeplan.world.TeamRecommendation? {
    val dungeon = app.pokeplan.world.Dungeons.byId(dungeonId) ?: return null
    val own = team?.let { data.world.team(it) }?.members.orEmpty().mapNotNull { id -> data.player.pokemon.firstOrNull { it.id == id } }
    return app.pokeplan.world.TeamAdvice.recommend(dungeon, (own + worldIdle()).distinctBy { it.id })
}

/** "Replace team": puts the recommended team for [dungeonId] on World team [team] in one action (through [worldAssign], so each pick leaves its old place). False while the team is out, or when there is nobody. */
fun GameStore.worldReplaceTeam(team: Int, dungeonId: String): Boolean {
    worldSettle()
    val slot = data.world.team(team) ?: return false
    if (slot.expedition != null) return false
    val ids = worldRecommend(dungeonId, team)?.ids.orEmpty()
    if (ids.isEmpty()) return false
    return worldAssign(team, ids)
}
