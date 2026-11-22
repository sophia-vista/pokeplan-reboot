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

fun GameStore.worldReport(): ReportSummary? = worldSummary?.takeIf { !it.isEmpty }

fun GameStore.worldReportDismiss() { worldSummary = null }

fun GameStore.worldSettle(): WorldReport? {
    val kept = data.world.pruned(data.player.pokemon.map { it.id }.toSet())
    if (kept !== data.world) update { it.copy(world = kept) }
    val before = data.world
    val report = WorldSim.settle(before, data.player.pokemon, nowMillis)
    if (report.isEmpty) {
        if (report.data.teams != before.teams) update { it.copy(world = it.world.copy(teams = report.data.teams)) }
        worldGuardian()
        worldLegends()
        return null
    }
    val id = "world:expedition:" + report.perTeam.keys.sorted().joinToString("+") { t ->
        val from = before.team(t)?.expedition
        "$t.${from?.startedAt}.${from?.floorsClimbed}-${report.data.team(t)?.expedition?.floorsClimbed}"
    }
    worldPay("World: exploring", id, "expedition", "expedition") { d ->
        val partner = d.player.pokemon.filter { it.inParty }.maxOfOrNull { it.level }
        d.copy(world = report.data, player = d.player.copy(pokemon = d.player.pokemon.map { p ->
            val floors = report.memberFloors[p.id] ?: return@map p
            p.gainXp(floors * CandyRules.XP_PER_FLOOR, partner ?: p.level)
        }))
    }
    worldSummary = (worldSummary ?: ReportSummary()) + ReportSummary.of(report, report.data.xp - before.xp, CandyRules.XP_PER_FLOOR)
    worldGuardian()
    worldLegends()
    return report
}

private fun GameStore.worldGuardian() {
    val w = data.world
    if (!w.guardianDone || w.guardianGranted) return
    update { d ->
        if (!d.world.guardianDone || d.world.guardianGranted) d
        else grantIn(d.copy(world = d.world.copy(guardianGranted = true)), app.pokeplan.world.Story.GUARDIAN_SPECIES, GameStore.WORLD_SOURCE, form = app.pokeplan.world.Story.GUARDIAN_FORM).first
    }
}

private fun GameStore.worldLegends() {
    for (quest in app.pokeplan.world.Legends.ready(data.world)) {
        val form = quest.form ?: app.pokeplan.core.dex.Dex.defaultForm(quest.speciesId).identifier
        update { d ->
            if (quest.id in d.world.legends) d
            else {
                val marked = d.copy(world = d.world.copy(legends = d.world.legends + quest.id))
                if (marked.player.pokemon.none { it.speciesId == quest.speciesId && it.form.identifier == form }) grantIn(marked, quest.speciesId, GameStore.WORLD_SOURCE, form = form).first else marked
            }
        }
    }
}

private fun GameStore.worldPay(source: String, eventId: String, ref: String, kind: String, change: (AppData) -> AppData) {
    update { d ->
        val changed = change(d)
        val earned = changed.world.pokeEarned - d.world.pokeEarned
        val staged = changed.world.items
        val paid = mutableListOf<Reward>()
        earned.takeIf { it > 0 }?.let { paid += Reward.Poke(it) }
        staged.forEach { (id, n) -> (n - (d.world.items[id] ?: 0)).takeIf { it > 0 }?.let { paid += Reward.ItemDrop(id, it) } }
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

fun GameStore.worldAssign(team: Int, members: List<String>): Boolean {
    if (members.any { id -> data.player.pokemon.none { it.id == id } }) return false
    val already = data.world.team(team)?.members.orEmpty().toSet()
    val incoming = members.filter { it !in already }
    if (incoming.any { !Places.movable(data, it) }) return false
    if (incoming.fold(data) { d, id -> Places.vacate(d, id) }.world.assign(team, members) == null) return false
    update { d ->
        val moved = incoming.fold(d) { m, id -> Places.vacate(m, id) }
        moved.world.assign(team, members)?.let { moved.copy(player = Places.leads(moved.player), world = it) } ?: d
    }
    return true
}

fun GameStore.worldRenameTeam(team: Int, name: String): Boolean {
    val next = data.world.renameTeam(team, name) ?: return false
    update { it.copy(world = next) }
    return true
}

fun GameStore.worldRecolourTeam(team: Int, accent: Int): Boolean {
    val next = data.world.recolourTeam(team, accent) ?: return false
    update { it.copy(world = next) }
    return true
}

fun GameStore.worldSavePreset(team: Int, name: String): Boolean {
    val members = data.world.team(team)?.members ?: return false
    val next = data.world.savePreset(name, members) ?: return false
    update { it.copy(world = next) }
    return true
}

fun GameStore.worldDeletePreset(name: String) = update { it.copy(world = it.world.deletePreset(name)) }

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

fun GameStore.worldIdle(): List<OwnedPokemon> = data.player.pokemon
    .filter { Places.of(data, it.id) == Place.Pc }
    .sortedWith(compareByDescending<OwnedPokemon> { it.level }.thenBy { it.id })

fun GameStore.worldChoose(id: String, option: Int): Boolean {
    worldSettle()
    val next = data.world.resolveChoice(id, option, nowMillis) ?: return false
    worldPay("World: exploring", "world:choice:$id", id, "choice") { it.copy(world = next) }
    return true
}

fun GameStore.worldSceneDone(id: String) { if (id !in data.world.scenesDone) update { it.copy(world = it.world.copy(scenesDone = it.world.scenesDone + id)) } }

fun GameStore.worldEpisodeDone(key: String, flags: Set<String> = emptySet()) {
    if (key !in data.world.episodesDone || key in data.world.storyResume || !data.world.storyFlags.containsAll(flags)) update { it.copy(world = it.world.copy(episodesDone = it.world.episodesDone + key, storyResume = it.world.storyResume - key, storyFlags = it.world.storyFlags + flags)) }
    val world = data.world
    if (key == app.pokeplan.world.WorldMoments.INTRO_KEY && app.pokeplan.world.story.IntroMewChapter.MEW_JOINED in world.storyFlags && GameStore.MEW_GIFT !in world.storyFlags) {
        update { it.copy(world = it.world.copy(storyFlags = it.world.storyFlags + GameStore.MEW_GIFT, finds = it.world.finds + app.pokeplan.world.Find(151, "tiny-woods", 1, nowMillis))) }
    }
}

fun GameStore.worldStoryProgress(key: String, nextScene: Int, flags: Set<String>) {
    if (data.world.storyResume[key] != nextScene || !data.world.storyFlags.containsAll(flags)) update { it.copy(world = it.world.copy(storyResume = it.world.storyResume + (key to nextScene), storyFlags = it.world.storyFlags + flags)) }
}

fun GameStore.worldMoment(): app.pokeplan.world.WorldMoment? {
    val adopted = app.pokeplan.world.WorldMoments.adopted(data.world)
    if (adopted != data.world) update { it.copy(world = adopted) }
    return app.pokeplan.world.WorldMoments.pending(adopted).firstOrNull()
}

fun GameStore.worldMomentSeen(moment: app.pokeplan.world.WorldMoment) = update { it.copy(world = app.pokeplan.world.WorldMoments.seen(it.world, moment)) }

fun GameStore.worldRecall(team: Int) {
    worldSettle()
    data.world.recall(team)?.let { next -> update { it.copy(world = next) } }
}

fun GameStore.worldRecruit(index: Int): OwnedPokemon? {
    val find = data.world.finds.getOrNull(index) ?: return null
    var mon: OwnedPokemon? = null
    update { d ->
        if (d.world.finds.getOrNull(index) != find) d
        else grantIn(d.copy(world = d.world.withoutFind(index)), find.speciesId, GameStore.WORLD_SOURCE, Origin.How.WORLD, form = find.formIdentifier).also { mon = it.second }.first
    }
    return mon
}

fun GameStore.worldAcceptMission(id: String): Boolean {
    worldSettle()
    val next = data.world.copy(lastSeenAt = nowMillis).acceptMission(id, utcOffsetMillis) ?: return false
    update { it.copy(world = next) }
    return true
}

fun GameStore.worldAbandonMission(id: String) {
    data.world.abandonMission(id)?.let { next -> update { it.copy(world = next) } }
}

fun GameStore.worldClaimMission(id: String): Boolean {
    worldSettle()
    val give = Missions.byId(id)?.takeIf { it.give != null }
    if (give != null && !data.player.inventory.has(give.give!!, give.giveCount)) return false
    val next = data.world.copy(lastSeenAt = nowMillis).claimMission(id, utcOffsetMillis) ?: return false
    worldPay("World: jobs", "world:job:$id:${(nowMillis + utcOffsetMillis).floorDiv(86_400_000L)}", id, "job") { d ->
        d.copy(world = next, player = if (give != null) d.player.copy(inventory = d.player.inventory.remove(give.give!!, give.giveCount)) else d.player)
    }
    return true
}

fun GameStore.worldUpgrade(facility: app.pokeplan.world.Facility): Boolean {
    worldSettle()
    val cost = data.world.upgradeCost(facility)
    val next = data.world.upgrade(facility, data.player.coins) ?: return false
    update { it.copy(world = next, player = it.player.copy(coins = it.player.coins - cost)) }
    return true
}

fun GameStore.worldClaimResearch(species: Int): Boolean {
    worldSettle()
    val next = data.world.claimResearch(species) ?: return false
    worldPay("World: research", "world:research:$species", "research:$species", "research") { it.copy(world = next) }
    return true
}

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

val GameStore.shopDay: Long get() = (nowMillis + utcOffsetMillis).floorDiv(86_400_000L)

fun GameStore.worldBuy(itemId: String): Boolean {
    val offer = app.pokeplan.world.KecleonShop.offer(shopDay, itemId) ?: return false
    if (data.player.coins < offer.price) return false
    update { d ->
        val (bag, extra) = d.player.inventory.addCapped(offer.itemId, offer.count)
        d.copy(player = d.player.copy(coins = d.player.coins - offer.price + extra, inventory = bag))
    }
    return true
}

fun GameStore.worldSwap(from: String, to: String): Boolean {
    if (from == to || from !in app.pokeplan.world.SwapShop.flavors || to !in app.pokeplan.world.SwapShop.flavors) return false
    val give = Items.berry(from)
    if (!data.player.inventory.has(give, app.pokeplan.world.SwapShop.GIVE)) return false
    update { d -> d.copy(player = d.player.copy(inventory = d.player.inventory.remove(give, app.pokeplan.world.SwapShop.GIVE).add(Items.berry(to), 1))) }
    return true
}

fun GameStore.worldTrain(skill: app.pokeplan.world.WorldSkill): Boolean {
    val next = data.world.train(skill, data.player.coins) ?: return false
    update { it.copy(world = next, player = it.player.copy(coins = it.player.coins - app.pokeplan.world.Dojo.COST)) }
    return true
}

fun GameStore.worldOpenTreasure(tier: Int): Map<String, Int> {
    if ((data.world.treasure[tier] ?: 0) <= 0) return emptyMap()
    val random = Random(nowMillis xor (tier.toLong() shl 40) xor data.world.xp.toLong())
    val found = mutableMapOf<String, Int>()
    fun add(id: String, n: Int) { found[id] = (found[id] ?: 0) + n }
    repeat(1 + tier) { add(Items.berry(listOf("sweet", "dry", "sour", "spicy", "bitter").random(random)), 1) }
    if (tier >= 2 && random.nextInt(100) < (if (tier >= 3) 35 else 15)) add(Items.RARE_CANDY, 1)
    if (tier >= 2 && random.nextInt(100) < (if (tier >= 3) 10 else 3)) {
        val missing = Items.megaStones.filter { (data.player.inventory[it]) == 0 }
        add((missing.ifEmpty { Items.megaStones }).random(random), 1)
    }
    worldPay("World: treasure", "world:treasure:$tier:$nowMillis", "treasure:$tier", "treasure") { d ->
        d.copy(world = d.world.copy(
            treasure = d.world.treasure + (tier to d.world.treasure.getValue(tier) - 1),
            items = found.entries.fold(d.world.items) { m, (id, n) -> m + (id to (m[id] ?: 0) + n) },
        ))
    }
    return found
}

fun GameStore.worldRecommend(dungeonId: String, team: Int? = null): app.pokeplan.world.TeamRecommendation? {
    val dungeon = app.pokeplan.world.Dungeons.byId(dungeonId) ?: return null
    val own = team?.let { data.world.team(it) }?.members.orEmpty().mapNotNull { id -> data.player.pokemon.firstOrNull { it.id == id } }
    return app.pokeplan.world.TeamAdvice.recommend(dungeon, (own + worldIdle()).distinctBy { it.id })
}

fun GameStore.worldReplaceTeam(team: Int, dungeonId: String): Boolean {
    worldSettle()
    val slot = data.world.team(team) ?: return false
    if (slot.expedition != null) return false
    val ids = worldRecommend(dungeonId, team)?.ids.orEmpty()
    if (ids.isEmpty()) return false
    return worldAssign(team, ids)
}
