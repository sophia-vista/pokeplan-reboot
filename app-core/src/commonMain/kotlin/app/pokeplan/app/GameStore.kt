package app.pokeplan.app

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
import app.pokeplan.base.Decorations
import app.pokeplan.core.reward.BaseKind
import app.pokeplan.core.reward.LinkEvent
import app.pokeplan.core.reward.SafariEvent
import app.pokeplan.core.reward.LinkMoment
import app.pokeplan.core.reward.MintEvent
import app.pokeplan.core.reward.MintKind
import app.pokeplan.heal.HealRules
import app.pokeplan.core.reward.EffortEvent
import app.pokeplan.core.reward.Feature
import app.pokeplan.core.reward.TrainerRank
import app.pokeplan.core.reward.DropTable
import app.pokeplan.core.reward.Reward
import app.pokeplan.core.reward.RewardFocus
import app.pokeplan.plan.EventCompanions
import app.pokeplan.core.reward.RewardEngine
import app.pokeplan.plan.ChecklistReset
import app.pokeplan.plan.ListKind
import app.pokeplan.plan.LinkKind
import app.pokeplan.plan.TaskLink
import app.pokeplan.plan.FailReason
import app.pokeplan.plan.FocusEvent
import app.pokeplan.plan.FocusRecord
import app.pokeplan.plan.FocusRules
import app.pokeplan.plan.FocusSession
import app.pokeplan.plan.Goal
import app.pokeplan.plan.Insights
import app.pokeplan.plan.GoalPartyMember
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
import app.pokeplan.plan.TargetConversion
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
import app.pokeplan.app.world.worldBuy
import app.pokeplan.app.world.worldSettle
import app.pokeplan.app.world.worldTrain
import app.pokeplan.app.world.worldUpgrade

sealed interface FocusResult {
    data class Hatched(val rewards: List<Reward>) : FocusResult
    data class Failed(val reason: FailReason) : FocusResult
}

class GameStore(
    storage: Storage,
    private val clock: Clock,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val rng: Random = Random,
) : World {
    private val document = SavedDocument(storage, AppData.serializer(), json, tidy = ::tidy) { AppData() }

    val data: AppData get() = document.value

    fun observe(listener: (AppData) -> Unit): () -> Unit = document.observe(listener)

    fun reload() = document.reload()

    fun sync() = document.sync()

    internal fun update(change: (AppData) -> AppData) = document.update { before ->
        var after = renameBaseLock(foldPoke(before, change(before)))
        val shinyNow = after.player.pokemon.filter { it.isShiny }.map { it.speciesId }.toSet()
        if (!after.shinySpecies.containsAll(shinyNow)) after = after.copy(shinySpecies = after.shinySpecies + shinyNow)
        FormDex.record(after)
    }

    private fun renameBaseLock(d: AppData): AppData =
        if ("HOME" !in d.settings.lockedModules) d else d.copy(settings = d.settings.copy(lockedModules = d.settings.lockedModules - "HOME" + "BASE"))

    private fun foldPoke(before: AppData, after: AppData): AppData {
        val migrated = if (before.pokeMigration == null || after.pokeMigration == null) migratePoke(after) else after
        val earned = migrated.world.pokeEarned
        if (earned == 0) return migrated
        return migrated.copy(player = migrated.player.copy(coins = migrated.player.coins + earned), world = migrated.world.copy(pokeEarned = 0))
    }

    private fun mergeHabits(d: AppData): AppData {
        if (d.habits.isEmpty()) return d
        val known = d.series.map { it.template.id }.toSet()
        val merged = d.habits.filter { it.id !in known }.map { TaskSeries.of(it, nowMillis) }
        return d.copy(series = d.series + merged, habits = emptyList(), legacyHabits = d.legacyHabits + d.habits, habitsMergedAtMillis = d.habitsMergedAtMillis ?: nowMillis)
    }

    private fun convertTargets(d: AppData): AppData {
        if (d.series.none { it.target != null }) return d
        return d.copy(series = d.series.map { s ->
            val target = s.target ?: return@map s
            s.copy(recurrence = TargetConversion.toRecurrence(target, s.createdOn ?: today), target = null)
        })
    }

    private fun migratePoke(d: AppData): AppData {
        if (d.pokeMigration != null) return if (d.world.legacyPoke == 0) d else d.copy(world = d.world.copy(legacyPoke = 0))
        val total = d.player.coins + d.world.legacyPoke
        return d.copy(
            player = d.player.copy(coins = total), world = d.world.copy(legacyPoke = 0),
            pokeMigration = PokeMigration(d.player.coins, d.world.legacyPoke, nowMillis, RewardRules.VERSION),
        )
    }

    fun exportJson(): String {
        val pretty = Json(json) { prettyPrint = true }
        val full = json.encodeToJsonElement(AppData.serializer(), data.copy(shownCalendars = emptySet(), gearRates = null)) as JsonObject
        return pretty.encodeToString(JsonObject.serializer(), JsonObject(full.filterKeys { it !in EXPORT_LEFT_OUT }))
    }

    fun eraseDevice() = update { AppData() }

    private fun tidy(d: AppData): AppData = d.copy(settings = d.settings.clamped().copy(careModule = true), player = migrateRoles(d.player)).let(::migratePoke).let(Places::resolve).let(Places::guardiansToModules).let(::mergeHabits).let(::convertTargets).let(::startDecorations)

    private fun startDecorations(d: AppData): AppData =
        if (d.decorationStarters) d else d.copy(decorationStarters = true, decorations = d.decorations + Decorations.STARTERS.filter { it !in d.decorations })

    private fun migrateRoles(p: app.pokeplan.core.reward.PlayerState): app.pokeplan.core.reward.PlayerState {
        if (p.rolesVersion >= ROLES_VERSION) return p
        val lead = p.buddyId?.takeIf { id -> p.party.any { it.id == id } }
        return p.copy(planLeadId = p.planLeadId ?: lead, rolesVersion = ROLES_VERSION)
    }

    fun setShownCalendars(ids: Set<Long>) = update { it.copy(shownCalendars = ids) }

    val nowMillis: Long get() = clock.nowMillis()
    val now: LocalDateTime get() = nowMillis.toLocalDateTime(zone)
    val today: LocalDate get() = now.date
    private val epochDay: Long get() = today.toEpochDays()

    val startOfTodayMillis: Long get() = LocalDateTime(today, kotlinx.datetime.LocalTime(0, 0)).toEpochMillis(zone)

    fun dayOf(millis: Long): LocalDate = millis.toLocalDateTime(zone).date

    val utcOffsetMillis: Long get() = zone.offsetAt(kotlin.time.Instant.fromEpochMilliseconds(nowMillis)).totalSeconds * 1000L

    internal fun newId(): String = newId(nowMillis, rng)

    fun tutorialOffered(id: String, careBreaks: Int = 0, healActivities: Int = 0, mintRecords: Int = 0, homeRecords: Int = 0, linkRecords: Int = 0): Boolean = data.onboarded && id !in data.tutorials && when (id) {
        Tutorials.PLAN_FOCUS -> data.focusHistory.none { it.completed }
        Tutorials.CARE_BREATH -> careBreaks == 0
        Tutorials.HEAL_WALK -> healActivities == 0
        Tutorials.MINT_TALLY -> mintRecords == 0
        Tutorials.BASE_TIDY -> homeRecords == 0
        Tutorials.LINK_POSTCARD -> linkRecords == 0
        else -> false
    }

    fun completeTutorial(id: String): List<Reward>? {
        if (!data.onboarded || id in data.tutorials) return null
        val (region, party, how) = when (id) {
            Tutorials.PLAN_FOCUS -> Triple(data.settings.region.open, PartyIds.PLAN, Origin.How.FOCUS_EGG)
            Tutorials.CARE_BREATH -> Triple(Region.CARE, PartyIds.CARE, Origin.How.CARE_EGG)
            Tutorials.HEAL_WALK -> Triple(Region.HEAL, PartyIds.HEAL, Origin.How.FIELD_EGG)
            Tutorials.MINT_TALLY -> Triple(Region.MINT, PartyIds.MINT, Origin.How.LEDGER_EGG)
            Tutorials.BASE_TIDY -> Triple(Region.BASE, PartyIds.BASE, Origin.How.HABITAT_EGG)
            Tutorials.LINK_POSTCARD -> Triple(Region.LINK, PartyIds.LINK, Origin.How.POSTCARD_EGG)
            else -> return null
        }
        val outcome = RewardEngine.tutorialEgg(data.player, region, party, newId(), nowMillis, rng)
        update { d -> log((if (id == Tutorials.CARE_BREATH) CARE_LOG_PREFIX else if (id == Tutorials.HEAL_WALK) HEAL_LOG_PREFIX else if (id == Tutorials.MINT_TALLY) MINT_LOG_PREFIX else if (id == Tutorials.BASE_TIDY) BASE_LOG_PREFIX else if (id == Tutorials.LINK_POSTCARD) LINK_LOG_PREFIX else "") + "first egg", outcome.rewards, d.copy(player = outcome.state, tutorials = d.tutorials + id), origin = Origin(how, region)) }
        return outcome.rewards
    }

    fun firstRecordEgg(id: String, records: Int): List<Reward>? = if (records > 0) completeTutorial(id) else null

    fun start(trainerName: String, starterSpeciesId: Int) {
        if (data.onboarded) return
        val option = HatchOption(starterSpeciesId, Dex.defaultForm(starterSpeciesId).identifier)
        val starter = Hatching.create(newId(), option, 0, nowMillis, 25, rng).copy(isShiny = false, inParty = false)
        val first = Task(
            id = newId(), title = "Finish your first task", rank = Rank.E, planned = today, tutorial = true, createdAtMillis = nowMillis,
        )
        update {
            startDecorations(it.copy(
                trainerName = trainerName.trim().ifEmpty { "Trainer" }, onboarded = true,
                player = it.player.copy(pokemon = listOf(starter), buddyId = starter.id, rolesVersion = ROLES_VERSION, caught = setOf(starter.speciesId)),
                placesVersion = Places.VERSION, tasks = listOf(first), origins = mapOf(starter.id to Origin(Origin.How.STARTER)),
            ))
        }
    }

    fun rename(trainerName: String) = update { it.copy(trainerName = trainerName.trim().ifEmpty { it.trainerName }) }

    fun changeSettings(change: (Settings) -> Settings) = update { it.copy(settings = change(it.settings).clamped()) }

    private fun log(source: String, rewards: List<Reward>, d: AppData, seen: Boolean = false, origin: Origin? = null): AppData {
        val joined = rewards.filterIsInstance<Reward.Hatched>()
            .associate { it.pokemon.id to (origin ?: Origin(Origin.How.TASK_EGG, d.settings.region.open)) }
        return d.copy(rewardLog = (d.rewardLog + RewardEntry(nowMillis, source, rewards, seen)).takeLast(LOG_SIZE), origins = d.origins + joined)
    }

    internal fun noted(before: AppData, after: AppData, id: String, module: String, ref: String, kind: String, at: Long, extraPoke: Int = 0): AppData {
        val poke = (after.player.coins - before.player.coins + extraPoke).toLong()
        val items = (before.player.inventory.counts.keys + after.player.inventory.counts.keys)
            .associateWith { (after.player.inventory[it] - before.player.inventory[it]).toLong() }.filterValues { it != 0L }
        if (poke == 0L && items.isEmpty()) return after
        return after.copy(ledgerNotes = (after.ledgerNotes + LedgerNote(id, module.uppercase(), ref, kind, at, poke, items)).takeLast(LEDGER_NOTES_KEPT))
    }

    fun markRewardsSeen() = update { d -> d.copy(rewardLog = d.rewardLog.map { it.copy(seen = true) }) }

    private fun achieve(d: AppData, tier: Int, source: String, eventId: String, ref: String, onTime: Boolean = false, focusedOnIt: Boolean = false, picked: Boolean = false, focus: RewardFocus = RewardFocus.EXP): AppData {
        val firstOfDay = streakDay !in d.activeDays
        val warmed = Counters.WARM.get(d.counters, streakDay)
        val event = AchievementEvent(
            tier = tier, region = d.settings.region.open, hourOfDay = now.hour, firstOfDay = firstOfDay, onTime = onTime,
            focusedOnIt = focusedOnIt, picked = picked, friendshipTasksToday = warmed, newPokemonId = newId(), nowMillis = nowMillis, focus = focus,
        )
        val outcome = RewardEngine.achievement(d.player, event, rng)
        val counters = Counters.WARM.set(d.counters, streakDay, warmed + 1)
        return noted(d, log(source, outcome.rewards, d.copy(player = outcome.state, activeDays = d.activeDays + streakDay, counters = counters)), eventId, "PLAN", ref, "task", nowMillis)
    }

    val careBuddy: OwnedPokemon? get() = data.player.careLead

    val streakDay: LocalDate get() = Streaks.streakDay(now)

    override fun care(event: CareEvent): List<Reward>? {
        if (!data.onboarded) return null
        val tally = Counters.care(event.kind)
        val paidToday = tally.get(data.counters, today)
        if (paidToday >= CARE_DAILY_CAP.getValue(event.kind)) return null
        val outcome = RewardEngine.care(data.player, event, rng)
        val meter = careEggMeter() + RewardEngine.CARE_EGG_POINTS.getValue(event.kind)
        val egg = if (meter >= RewardEngine.CARE_EGG_METER_SIZE) RewardEngine.careEgg(outcome.state, newId(), nowMillis, rng) else null
        val rewards = outcome.rewards + egg?.rewards.orEmpty()
        update { d ->
            val counters = tally.set(d.counters, today, paidToday + 1) + (Counters.CARE_EGG_METER to meter % RewardEngine.CARE_EGG_METER_SIZE)
            val kind = event.kind.name.lowercase()
            noted(d, log(CARE_SOURCES.getValue(event.kind), rewards, d.copy(player = (egg ?: outcome).state, counters = counters), origin = Origin(Origin.How.CARE_EGG, Region.CARE)), "care:$kind:${today.toEpochDays()}:${paidToday + 1}", "CARE", "$kind:${today.toEpochDays()}", kind, event.nowMillis)
        }
        return rewards
    }

    override fun heal(event: HealEvent): List<Reward>? {
        if (!data.onboarded || event.id in data.healPaid) return null
        val tally = Counters.heal(event.kind)
        val paidToday = tally.get(data.counters, today)
        if (paidToday >= HEAL_DAILY_CAP.getValue(event.kind)) return null
        val outcome = RewardEngine.heal(data.player, event, rng)
        val meter = healEggMeter() + RewardEngine.HEAL_EGG_POINTS.getValue(event.kind)
        val egg = if (meter >= RewardEngine.HEAL_EGG_METER_SIZE) RewardEngine.healEgg(outcome.state, newId(), nowMillis, rng) else null
        val rewards = outcome.rewards + egg?.rewards.orEmpty()
        update { d ->
            val counters = tally.set(d.counters, today, paidToday + 1) + (Counters.HEAL_EGG_METER to meter % RewardEngine.HEAL_EGG_METER_SIZE)
            noted(d, log(HEAL_SOURCES.getValue(event.kind), rewards, d.copy(player = (egg ?: outcome).state, counters = counters, healPaid = (d.healPaid + event.id).takeLast(HEAL_PAID_KEPT)), origin = Origin(Origin.How.FIELD_EGG, Region.HEAL)), event.id, event.module, event.ref, event.kind.name.lowercase(), event.nowMillis)
        }
        return rewards
    }

    override fun mint(event: MintEvent): List<Reward>? {
        if (!data.onboarded || event.id in data.mintPaid) return null
        val tally = Counters.mint(event.kind)
        val period = if (event.kind == MintKind.REVIEW) app.pokeplan.mint.MintRules.weekStartOf(today) else today
        val paidInPeriod = tally.get(data.counters, period)
        if (paidInPeriod >= MINT_CAPS.getValue(event.kind)) return null
        val outcome = RewardEngine.mint(data.player, event, rng)
        val meter = mintEggMeter() + RewardEngine.MINT_EGG_POINTS.getValue(event.kind)
        val egg = if (meter >= RewardEngine.MINT_EGG_METER_SIZE) RewardEngine.mintEgg(outcome.state, newId(), nowMillis, rng) else null
        val rewards = outcome.rewards + egg?.rewards.orEmpty()
        val outbreak = RewardEngine.mintOutbreak((egg ?: outcome).state, event, newId(), rng)
        update { d ->
            val counters = tally.set(d.counters, period, paidInPeriod + 1) + (Counters.MINT_EGG_METER to meter % RewardEngine.MINT_EGG_METER_SIZE)
            val pending = (d.outbreaks + listOfNotNull(outbreak)).takeLast(RewardEngine.MAX_PENDING_OUTBREAKS)
            noted(d, log(MINT_SOURCES.getValue(event.kind), rewards, d.copy(player = (egg ?: outcome).state, counters = counters, outbreaks = pending, mintPaid = (d.mintPaid + event.id).takeLast(MINT_PAID_KEPT)), origin = Origin(Origin.How.LEDGER_EGG, Region.MINT)), event.id, event.module, event.ref, event.kind.name.lowercase(), event.nowMillis)
        }
        return rewards
    }

    fun claimOutbreak(outbreakId: String, speciesId: Int): List<Reward>? {
        val outbreak = data.outbreaks.firstOrNull { it.id == outbreakId } ?: return null
        val outcome = RewardEngine.claimMintOutbreak(data.player, outbreak, speciesId, newId(), nowMillis, rng) ?: return null
        update { d ->
            log(MINT_SOURCES.getValue(MintKind.REVIEW), outcome.rewards, d.copy(player = outcome.state, outbreaks = d.outbreaks.filterNot { it.id == outbreakId }), origin = Origin(Origin.How.LEDGER_EGG, Region.MINT))
        }
        return outcome.rewards
    }

    override fun base(event: BaseEvent): List<Reward>? {
        if (!data.onboarded || event.id in data.basePaid) return null
        val tally = Counters.base(event.kind)
        val paidToday = tally.get(data.counters, today)
        if (paidToday >= BASE_CAPS.getValue(event.kind)) return null
        val outcome = RewardEngine.base(data.player, event, rng)
        val meter = homeEggMeter() + RewardEngine.HOME_EGG_POINTS.getValue(event.kind)
        val egg = if (meter >= RewardEngine.HOME_EGG_METER_SIZE) RewardEngine.homeEgg(outcome.state, newId(), nowMillis, rng) else null
        val step = Decorations.advance(decorationMeter(), if (event.kind == BaseKind.CHORE) Decorations.CHORE_POINTS else Decorations.MAINTENANCE_POINTS, Decorations.missing(data.decorations).isEmpty())
        val decoration = if (step.found) Decorations.pick(data.decorations, event.id) else null
        val rewards = outcome.rewards + egg?.rewards.orEmpty() + listOfNotNull(decoration?.let { Reward.Decoration(it.id) })
        update { d ->
            val counters = tally.set(d.counters, today, paidToday + 1) + (Counters.HOME_EGG_METER to meter % RewardEngine.HOME_EGG_METER_SIZE) + (Counters.HOME_DECOR_METER to step.points)
            noted(d, log(BASE_SOURCES.getValue(event.kind), rewards, d.copy(player = (egg ?: outcome).state, counters = counters, decorations = d.decorations + listOfNotNull(decoration?.id), basePaid = (d.basePaid + event.id).takeLast(HOME_PAID_KEPT)), origin = Origin(Origin.How.HABITAT_EGG, Region.BASE)), event.id, event.module, event.ref, event.kind.name.lowercase(), event.nowMillis)
        }
        return rewards
    }

    override fun link(event: LinkEvent): List<Reward>? {
        if (!data.onboarded || event.id in data.linkPaid || linkedTask(LinkKind.LINK_FOLLOW_UP, event.ref) != null) return null
        val tally = Counters.link(event.kind)
        val paidToday = tally.get(data.counters, today)
        if (paidToday >= LINK_CAPS.getValue(event.kind)) return null
        val outcome = RewardEngine.link(data.player, event, rng)
        val meter = linkEggMeter() + RewardEngine.LINK_EGG_POINTS.getValue(event.kind)
        val egg = if (meter >= RewardEngine.LINK_EGG_METER_SIZE) RewardEngine.linkEgg(outcome.state, newId(), nowMillis, rng) else null
        val rewards = outcome.rewards + egg?.rewards.orEmpty()
        update { d ->
            val counters = tally.set(d.counters, today, paidToday + 1) + (Counters.LINK_EGG_METER to meter % RewardEngine.LINK_EGG_METER_SIZE)
            noted(d, log(LINK_SOURCES.getValue(event.kind), rewards, d.copy(player = (egg ?: outcome).state, counters = counters, linkPaid = (d.linkPaid + event.id).takeLast(LINK_PAID_KEPT)), origin = Origin(Origin.How.POSTCARD_EGG, Region.LINK)), event.id, event.module, event.ref, event.kind.name.lowercase(), event.nowMillis)
        }
        return rewards
    }

    override fun safari(event: SafariEvent): List<Reward>? {
        if (!data.onboarded || event.id in data.linkPaid) return null
        val caughtToday = Counters.SAFARI.get(data.counters, today)
        if (caughtToday >= SAFARI_PER_DAY) return null
        val outcome = RewardEngine.safariVisit(data.player, event, newId(), rng) ?: return null
        update { d ->
            val counters = Counters.SAFARI.set(d.counters, today, caughtToday + 1)
            noted(d, log(SAFARI_SOURCE, outcome.rewards, d.copy(player = outcome.state, counters = counters, linkPaid = (d.linkPaid + event.id).takeLast(LINK_PAID_KEPT)), origin = Origin(Origin.How.SAFARI, Region.LINK)), event.id, event.module, event.ref, "safari", event.nowMillis)
        }
        return outcome.rewards
    }

    override fun safariDay(eventId: String): SafariDay = SafariDay(eventId in data.linkPaid, (SAFARI_PER_DAY - Counters.SAFARI.get(data.counters, today)).coerceAtLeast(0))

    fun linkEggMeter(): Int = data.counters[Counters.LINK_EGG_METER] ?: 0

    fun setLinkLead(id: String): Boolean {
        val p = pokemon(id)
        if (!p.inParty || p.partyId != PartyIds.LINK) return false
        update { d -> d.copy(player = d.player.copy(linkLeadId = id)) }
        return true
    }

    override fun ownedDecorations(): Set<String> = data.decorations.toSet()

    fun grantDecoration(id: String): Boolean {
        if (Decorations.get(id) == null || id in data.decorations) return false
        update { d -> d.copy(decorations = d.decorations + id) }
        return true
    }

    fun decorationMeter(): Int = data.counters[Counters.HOME_DECOR_METER] ?: 0

    fun homeEggMeter(): Int = data.counters[Counters.HOME_EGG_METER] ?: 0

    fun setBaseLead(id: String): Boolean {
        val p = pokemon(id)
        if (!p.inParty || p.partyId != PartyIds.BASE) return false
        update { d -> d.copy(player = d.player.copy(baseLeadId = id)) }
        return true
    }

    fun mintEggMeter(): Int = data.counters[Counters.MINT_EGG_METER] ?: 0

    fun setMintLead(id: String): Boolean {
        val p = pokemon(id)
        if (!p.inParty || p.partyId != PartyIds.MINT) return false
        update { d -> d.copy(player = d.player.copy(mintLeadId = id)) }
        return true
    }

    fun healEggMeter(): Int = data.counters[Counters.HEAL_EGG_METER] ?: 0

    fun setHealLead(id: String): Boolean {
        val p = pokemon(id)
        if (!p.inParty || p.partyId != PartyIds.HEAL) return false
        update { d -> d.copy(player = d.player.copy(healLeadId = id)) }
        return true
    }

    fun careEggMeter(): Int = data.counters[Counters.CARE_EGG_METER] ?: 0

    fun martBuy(itemId: String, count: Int): Int {
        val each = Shop.price(itemId) ?: return 0
        val limit = if (Items.category(itemId) == ItemCategory.CANDY) Inventory.MAX_CANDIES else Inventory.MAX_STACK
        val room = limit - data.player.inventory[itemId]
        val n = minOf(count, data.player.coins / each, room)
        if (n <= 0) return 0
        update { d ->
            val player = d.player.copy(coins = d.player.coins - n * each, inventory = d.player.inventory.add(itemId, n))
            val reward = when (itemId) {
                Items.RARE_CANDY -> Reward.Candies(CandyType.RARE, n)
                Items.SUPER_CANDY -> Reward.Candies(CandyType.SUPER, n)
                else -> Reward.ItemDrop(itemId, n)
            }
            noted(d, log("Shop", listOf(reward), d.copy(player = player), seen = true), "app:shop:$nowMillis", "APP", "shop", "purchase", nowMillis)
        }
        return n
    }

    fun buyRareCandies(count: Int): Int = martBuy(Items.RARE_CANDY, count)

    override fun visitorLuck(): Double =
        app.pokeplan.core.pokemon.CarePerks.total(data.player.careLead, data.player.partyOf(PartyIds.CARE), data.player.guardianOf(PartyIds.CARE))[app.pokeplan.core.pokemon.CareBucket.VISITOR_LUCK] ?: 0.0

    override fun berries(): Map<String, Int> =
        data.player.inventory.counts.filter { (id, n) -> n > 0 && Items.category(id) == ItemCategory.BERRY }

    override fun spendBerry(berry: String): Boolean {
        if (!data.onboarded || Items.category(berry) != ItemCategory.BERRY || !data.player.inventory.has(berry)) return false
        update { d -> d.copy(player = d.player.copy(inventory = d.player.inventory.remove(berry))) }
        return true
    }

    override fun befriend(event: BefriendEvent): List<Reward>? {
        if (!data.onboarded) return null
        val made = Counters.BEFRIEND.get(data.counters, today)
        if (made >= BEFRIEND_DAILY_CAP) return null
        val outcome = RewardEngine.befriend(data.player, event, rng)
        update { d ->
            val counters = Counters.BEFRIEND.set(d.counters, today, made + 1)
            noted(d, log(CARE_FRIEND_SOURCE, outcome.rewards, d.copy(player = outcome.state, counters = counters),
                origin = Origin(Origin.How.BEFRIENDED, Region.CARE, visitor = visitorOrigin(event, outcome.rewards))), "care:befriend:${event.newPokemonId}", "CARE", event.visitorId.ifEmpty { event.newPokemonId }, "befriend", event.nowMillis)
        }
        return outcome.rewards
    }

    private fun visitorOrigin(event: BefriendEvent, rewards: List<Reward>): VisitorOrigin? {
        if (event.visitorId.isEmpty()) return null
        val hatched = rewards.filterIsInstance<Reward.Hatched>().firstOrNull()?.pokemon
        val joined = hatched != null && hatched.speciesId == event.speciesId && hatched.friendship >= RewardEngine.BEFRIEND_JOIN_FRIENDSHIP
        val traits = if (joined) emptyList() else VisitorInheritance.plan(event.visitorId, event.newPokemonId).labels()
        val name = Dex.species(event.speciesId).name
        val note = when {
            joined -> "$name came to stay after a visit to Care."
            traits.isEmpty() -> "Left by a $name you befriended."
            else -> "Left by a $name you befriended; it took ${traits.joinToString(", ")}."
        }
        return VisitorOrigin(event.speciesId, event.visitorId, today.toEpochDays(), traits, note)
    }

    fun addTask(
        title: String,
        rank: Rank = Rank.C,
        planned: LocalDate? = null,
        due: LocalDateTime? = null,
        estimateMinutes: Int? = null,
        notes: String = "",
        listId: String? = null,
        plannedTime: kotlinx.datetime.LocalTime? = null,
        evening: Boolean = false,
        reward: RewardFocus? = null,
    ): Task {
        val task = Task(
            id = newId(), title = title.trim(), notes = notes, rank = rank, planned = planned, due = due,
            plannedTime = plannedTime?.takeIf { planned != null }, evening = evening && planned != null,
            estimateMinutes = estimateMinutes, createdAtMillis = nowMillis, listId = listId?.takeIf { id -> data.lists.any { it.id == id } },
            reward = reward ?: data.settings.defaultReward,
        )
        update { it.copy(tasks = it.tasks + task) }
        return task
    }

    fun updateTask(task: Task) = update { d -> d.copy(tasks = d.tasks.map { if (it.id == task.id) task else it }) }

    fun duplicateTask(id: String): Task? {
        val src = data.tasks.firstOrNull { it.id == id && it.state != TaskState.DELETED && it.link == null } ?: return null
        val copy = src.copy(
            id = newId(), seriesId = null, tutorial = false, state = TaskState.OPEN, createdAtMillis = nowMillis, completedAtMillis = null,
            deletedAtMillis = null, focusedMinutes = 0, paid = false, link = null,
            subtasks = src.subtasks.map { it.copy(id = newId(), done = false, paid = false, createdAtMillis = nowMillis) },
        )
        update { it.copy(tasks = it.tasks + copy) }
        return copy
    }

    private val linkOwners = mutableMapOf<LinkKind, LinkOwner>()

    fun registerLinkOwner(owner: LinkOwner) { linkOwners[owner.kind] = owner; syncLinks() }

    fun linkedTask(kind: LinkKind, ref: String): Task? = data.tasks.firstOrNull { it.link?.kind == kind && it.link?.ref == ref && it.state != TaskState.DELETED }

    fun linkChore(choreId: String): Task? {
        linkedTask(LinkKind.HOME_CHORE, choreId)?.let { return it }
        val owner = linkOwners[LinkKind.HOME_CHORE] ?: return null
        val link = TaskLink(LinkKind.HOME_CHORE, choreId)
        val open = owner.project(link) as? LinkState.Open ?: return null
        val task = Task(
            id = newId(), title = open.title, planned = open.planned, createdAtMillis = nowMillis,
            link = link.copy(occurrence = open.occurrence),
        )
        update { it.copy(tasks = it.tasks + task) }
        return task
    }

    override fun followUpTask(followUpId: String): FollowUpTask? =
        linkedTask(LinkKind.LINK_FOLLOW_UP, followUpId)?.let { FollowUpTask(it.id, it.state == TaskState.DONE, it.completedAtMillis) }

    override fun planFollowUp(followUpId: String, title: String, planned: LocalDate?): FollowUpTask? =
        convertFollowUp(followUpId, title, planned)?.let { FollowUpTask(it.id, it.state == TaskState.DONE, it.completedAtMillis) }

    fun convertFollowUp(followUpId: String, title: String, planned: LocalDate? = null, due: LocalDateTime? = null): Task? {
        linkedTask(LinkKind.LINK_FOLLOW_UP, followUpId)?.let { return it }
        val clean = title.trim().ifEmpty { return null }
        val task = Task(
            id = newId(), title = clean, planned = planned, due = due, createdAtMillis = nowMillis,
            link = TaskLink(LinkKind.LINK_FOLLOW_UP, followUpId),
        )
        update { it.copy(tasks = it.tasks + task) }
        return task
    }

    fun unlinkTask(taskId: String) {
        val task = data.tasks.firstOrNull { it.id == taskId && it.link != null } ?: return
        if (task.ownedElsewhere) deleteTask(taskId)
        else update { d -> d.copy(tasks = d.tasks.map { if (it.id == taskId) it.copy(link = null) else it }) }
    }

    fun syncLinks() {
        val now = nowMillis
        val changed = data.tasks.map { t ->
            val link = t.link?.takeIf { t.ownedElsewhere && t.state != TaskState.DELETED } ?: return@map t
            val owner = linkOwners[link.kind] ?: return@map t
            when (val s = owner.project(link)) {
                LinkState.Gone -> TaskRules.delete(t, now)
                LinkState.Inactive -> if (t.state == TaskState.WONT_DO) t else t.copy(state = TaskState.WONT_DO, completedAtMillis = t.completedAtMillis ?: now)
                is LinkState.Open -> t.copy(
                    title = s.title, planned = s.planned, state = TaskState.OPEN, completedAtMillis = null,
                    link = link.copy(occurrence = s.occurrence),
                )
                is LinkState.Done -> if (t.state == TaskState.DONE && link.occurrence == s.occurrence) t
                    else t.copy(state = TaskState.DONE, completedAtMillis = t.completedAtMillis ?: s.atMillis.takeIf { it > 0 } ?: now, link = link.copy(occurrence = s.occurrence))
            }
        }
        if (changed != data.tasks) update { d -> d.copy(tasks = changed) }
    }

    val activeLists: List<TaskList> get() = data.lists.filter { !it.archived }

    fun listNamed(name: String): TaskList? = activeLists.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    fun addList(
        name: String, color: Long = TaskList.COLORS.first(), icon: String = TaskList.ICONS.first(),
        kind: ListKind = ListKind.TASKS, startFrom: String? = null,
    ): TaskList? {
        val clean = name.trim().take(TaskList.MAX_NAME).ifEmpty { return null }
        val items = startFrom?.let { id -> data.lists.firstOrNull { it.id == id }?.template }.orEmpty()
        val list = TaskList(
            newId(), clean, color, icon, kind = if (items.isEmpty()) kind else ListKind.CHECKLIST,
            template = items, resetOn = today,
        )
        update { it.copy(lists = it.lists + list) }
        items.forEach { addTask(it, listId = list.id) }
        return list
    }

    fun saveTemplate(listId: String) = update { d ->
        val items = d.tasks.filter { it.listId == listId && it.state != TaskState.DELETED }.sortedBy { it.createdAtMillis }.map { it.title }.take(MAX_TEMPLATE_ITEMS)
        d.copy(lists = d.lists.map { if (it.id == listId) it.copy(template = items) else it })
    }

    private fun resetChecklists() {
        val weekStart = data.settings.weekStart
        val due = data.lists.filter { l ->
            val last = l.resetOn ?: return@filter false
            l.kind == ListKind.CHECKLIST && when (l.reset) {
                ChecklistReset.NEVER -> false
                ChecklistReset.DAILY -> last < today
                ChecklistReset.WEEKLY -> last < today && (0..6).any { back -> today.minus(DatePeriod(days = back)).let { it > last && it.dayOfWeek == weekStart } }
            }
        }.map { it.id }.toSet()
        if (due.isEmpty()) return
        update { d ->
            d.copy(
                lists = d.lists.map { if (it.id in due) it.copy(resetOn = today) else it },
                tasks = d.tasks.map { t -> if (t.listId in due && t.state == TaskState.DONE) t.copy(state = TaskState.OPEN, completedAtMillis = null, subtasks = t.subtasks.map { it.copy(done = false) }) else t },
            )
        }
    }

    fun updateList(list: TaskList) = update { d ->
        d.copy(lists = d.lists.map { if (it.id == list.id) list.copy(name = list.name.trim().take(TaskList.MAX_NAME).ifEmpty { it.name }) else it })
    }

    fun deleteList(id: String) = update { d ->
        d.copy(lists = d.lists.filter { it.id != id }, tasks = d.tasks.map { if (it.listId == id) it.copy(listId = null) else it })
    }

    fun restoreList(list: TaskList, taskIds: Set<String>) = update { d ->
        d.copy(lists = if (d.lists.any { it.id == list.id }) d.lists else d.lists + list, tasks = d.tasks.map { if (it.id in taskIds) it.copy(listId = list.id) else it })
    }

    fun restoreEvents(events: List<PlanEvent>) = update { it.copy(events = events) }

    fun moveToList(taskId: String, listId: String?) = update { d ->
        d.copy(tasks = d.tasks.map { if (it.id == taskId) it.copy(listId = listId?.takeIf { id -> d.lists.any { l -> l.id == id } }) else it })
    }

    fun setPlannedTime(taskId: String, time: LocalTime?) = update { d ->
        d.copy(tasks = d.tasks.map { t ->
            if (t.id != taskId) t
            else if (time == null) t.copy(plannedTime = null)
            else t.copy(planned = t.planned ?: t.due?.date ?: today, plannedTime = time)
        })
    }

    fun scheduleTask(taskId: String, day: LocalDate, time: LocalTime) = update { d ->
        d.copy(tasks = d.tasks.map { if (it.id == taskId && it.isOpen) it.copy(planned = day, plannedTime = time) else it })
    }

    fun setEstimate(taskId: String, minutes: Int?) = update { d ->
        d.copy(tasks = d.tasks.map { if (it.id == taskId && it.isOpen) it.copy(estimateMinutes = minutes?.takeIf { m -> m > 0 }) else it })
    }

    fun addEvent(
        title: String, day: LocalDate, start: LocalTime, minutes: Int = PlanEvent.DEFAULT_MINUTES, color: Long? = null,
        repeat: EventRepeat? = null,
    ): PlanEvent = PlanCalendar.timed(newId(), title, day, start, minutes, color, repeat).also { e -> update { it.copy(events = it.events + e) } }

    fun addAllDayEvent(title: String, first: LocalDate, last: LocalDate = first, color: Long? = null, repeat: EventRepeat? = null): PlanEvent =
        PlanCalendar.allDay(newId(), title, first, last, color, repeat).also { e -> update { it.copy(events = it.events + e) } }

    fun updateEvent(
        id: String, title: String? = null, day: LocalDate? = null, start: LocalTime? = null, minutes: Int? = null,
        lastDay: LocalDate? = null, color: Long? = null, allDay: Boolean? = null,
    ): String = editEvents { PlanCalendar.update(it, id, title, day, start, minutes, lastDay, color, allDay) }

    fun moveEvent(id: String, day: LocalDate, start: LocalTime): String = editEvents { PlanCalendar.move(it, id, day, start, ::newId) }

    fun deleteEvent(id: String) = update { d -> d.copy(events = PlanCalendar.delete(d.events, id)) }

    fun deleteOccurrence(id: String) = update { d -> d.copy(events = PlanCalendar.deleteOccurrence(d.events, id)) }

    fun repeatEvent(id: String, repeat: EventRepeat?): String = editEvents { PlanCalendar.repeat(it, id, repeat, ::newId) }

    fun endEventOn(id: String, until: LocalDate?) = update { d -> d.copy(events = PlanCalendar.endOn(d.events, id, until)) }

    private fun editEvents(edit: (List<PlanEvent>) -> PlanCalendar.Edit): String {
        val result = edit(data.events)
        val events = restartCompanions(data.events, result.events)
        if (events != data.events) update { it.copy(events = events) }
        return result.shownId
    }

    private fun restartCompanions(before: List<PlanEvent>, after: List<PlanEvent>): List<PlanEvent> {
        val was = before.associateBy { it.id }
        return after.map { e ->
            val old = was[e.id]
            val moved = old != null && (old.day != e.day || old.start != e.start || old.minutes != e.minutes || old.allDay != e.allDay || old.repeat != e.repeat)
            if (e.companionId != null && moved) e.copy(companionSince = nowMillis) else e
        }
    }

    fun setReminders(taskId: String, reminders: List<ReminderOffset>) =
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == taskId) it.copy(reminders = reminders.distinct().take(Reminders.MAX_PER_TASK)) else it }) }

    fun upcomingReminders(): List<DueReminder> = data.upcomingReminders(nowMillis, zone)

    fun deleteTask(id: String) = update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) TaskRules.delete(it, nowMillis) else it }) }

    fun restoreTask(id: String) = update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) TaskRules.restore(it, nowMillis) else it }) }

    fun wontDo(id: String) {
        val task = data.tasks.firstOrNull { it.id == id } ?: return
        val link = task.link?.takeIf { task.ownedElsewhere }
        if (link != null) { if (task.isOpen) linkOwners[link.kind]?.skip(link); return }
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) TaskRules.wontDo(it, nowMillis) else it }) }
    }

    fun reopen(id: String) {
        val task = data.tasks.firstOrNull { it.id == id } ?: return
        val link = task.link?.takeIf { task.ownedElsewhere }
        if (link != null) {
            if (task.state == TaskState.DONE || task.state == TaskState.WONT_DO) linkOwners[link.kind]?.undo(link)
            return
        }
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) TaskRules.reopen(it) else it }) }
    }

    fun completeTask(id: String): PendingCompletion {
        val task = data.tasks.first { it.id == id }
        val link = task.link?.takeIf { task.ownedElsewhere }
        if (link != null) return completeLinked(task, link)
        val c = TaskRules.complete(task, nowMillis, zone)
        val pending = PendingCompletion(task, c.task, c.pays, c.tier, c.onTime, c.focusedOnIt, c.finalAtMillis)
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) c.task else it }, pending = d.pending + pending) }
        return pending
    }

    private fun completeLinked(task: Task, link: TaskLink): PendingCompletion {
        val nothing = PendingCompletion(task, task, false, task.rank.tier, false, false, nowMillis)
        if (!task.isOpen) return nothing
        val owner = linkOwners[link.kind] ?: return nothing
        if (!owner.complete(link)) return nothing
        val done = (data.tasks.firstOrNull { it.id == task.id } ?: task).copy(state = TaskState.DONE, completedAtMillis = nowMillis)
        val pending = PendingCompletion(task, done, false, task.rank.tier, false, false, nowMillis + TaskRules.UNDO_WINDOW_MILLIS)
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == task.id) done else it }, pending = d.pending + pending) }
        return pending
    }

    fun undoCompletion(taskId: String): Boolean {
        val p = data.pending.firstOrNull { it.original.id == taskId } ?: return false
        if (nowMillis >= p.finalAtMillis) return false
        p.original.link?.takeIf { p.original.ownedElsewhere }?.let { link -> linkOwners[link.kind]?.undo(link) }
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == taskId) p.original else it }, pending = d.pending - p) }
        return true
    }

    fun completeSubtask(taskId: String, subtaskId: String): Boolean {
        val task = data.tasks.first { it.id == taskId }
        val (updated, pays) = TaskRules.completeSubtask(task, subtaskId, nowMillis)
        update { d ->
            val withTask = d.copy(tasks = d.tasks.map { if (it.id == taskId) updated else it })
            if (!pays) withTask else {
                val (bag, overflow) = withTask.player.inventory.addCapped(Items.RARE_CANDY, TaskRules.SUBTASK_RARE_CANDIES)
                val rewards = listOf(Reward.Candies(app.pokeplan.core.reward.CandyType.RARE, TaskRules.SUBTASK_RARE_CANDIES))
                noted(withTask, log("Step: ${task.subtasks.first { it.id == subtaskId }.title}", rewards,
                    withTask.copy(player = withTask.player.copy(inventory = bag, coins = withTask.player.coins + overflow))), "plan:step:$taskId:$subtaskId", "PLAN", taskId, "step", nowMillis)
            }
        }
        return pays
    }

    fun addSubtask(taskId: String, title: String) {
        val clean = title.trim().ifEmpty { return }
        update { d ->
            d.copy(tasks = d.tasks.map { t ->
                if (t.id == taskId && t.subtasks.size < MAX_SUBTASKS) t.copy(subtasks = t.subtasks + Subtask(newId(), clean, createdAtMillis = nowMillis)) else t
            })
        }
    }

    fun renameSubtask(taskId: String, subtaskId: String, title: String) {
        val clean = title.trim().ifEmpty { return }
        update { d -> d.copy(tasks = d.tasks.map { t -> if (t.id == taskId) t.copy(subtasks = t.subtasks.map { if (it.id == subtaskId) it.copy(title = clean) else it }) else t }) }
    }

    fun removeSubtask(taskId: String, subtaskId: String) =
        update { d -> d.copy(tasks = d.tasks.map { t -> if (t.id == taskId) t.copy(subtasks = t.subtasks.filterNot { it.id == subtaskId }) else t }) }

    fun reopenSubtask(taskId: String, subtaskId: String) =
        update { d -> d.copy(tasks = d.tasks.map { t -> if (t.id == taskId) t.copy(subtasks = t.subtasks.map { if (it.id == subtaskId) it.copy(done = false) else it }) else t }) }

    fun restoreSubtasks(taskId: String, subtasks: List<Subtask>) =
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == taskId) it.copy(subtasks = subtasks) else it }) }

    fun addRecurring(template: Task, recurrence: Recurrence) {
        val seriesId = newId()
        val series = TaskSeries(template.copy(id = seriesId, seriesId = seriesId, createdAtMillis = nowMillis), recurrence)
        update { it.copy(series = it.series + series) }
        refresh()
    }

    fun seriesOf(task: Task): TaskSeries? =
        data.series.firstOrNull { it.template.seriesId == task.seriesId && task.seriesId != null || it.firstTaskId == task.id }

    fun makeRepeating(taskId: String, repeat: Repeat): Boolean {
        val task = data.tasks.firstOrNull { it.id == taskId && it.isOpen } ?: return false
        if (task.link != null) return false
        val existing = seriesOf(task)
        if (existing != null) {
            val id = existing.template.seriesId!!
            update { d ->
                d.copy(
                    series = d.series.map { if (it.template.seriesId == id) it.copy(recurrence = it.recurrence?.copy(repeat = repeat)) else it },
                    tasks = d.tasks.filterNot { untouchedLaterInstance(it, id) },
                )
            }
        } else {
            val start = task.planned ?: today
            val seriesId = newId()
            val template = task.copy(
                id = seriesId, seriesId = seriesId, planned = null, createdAtMillis = nowMillis, paid = false, focusedMinutes = 0,
                subtasks = task.subtasks.map { it.copy(done = false, paid = false) },
            )
            val recurrence = Recurrence(repeat, start, skipped = setOf(start))
            update { d ->
                d.copy(
                    series = d.series + TaskSeries(template, recurrence, firstTaskId = task.id),
                    tasks = d.tasks.map { if (it.id == task.id && it.planned == null) it.copy(planned = start) else it },
                )
            }
        }
        refresh()
        return true
    }

    fun stopRepeating(taskId: String) {
        val task = data.tasks.firstOrNull { it.id == taskId } ?: return
        val id = seriesOf(task)?.template?.seriesId ?: return
        update { d -> d.copy(series = d.series.filterNot { it.template.seriesId == id }, tasks = d.tasks.filterNot { untouchedLaterInstance(it, id) }) }
    }

    private fun untouchedLaterInstance(t: Task, seriesId: String): Boolean =
        t.seriesId == seriesId && t.isOpen && (t.planned ?: today) > today && t.focusedMinutes == 0 && t.subtasks.none { it.done }

    fun tick() {
        sync()
        settlePending()
        settleEvents()
        tickFocus()
    }

    private fun settleEvents() {
        val due = EventCompanions.due(data.events, now) { it.toEpochMillis(zone) }
        if (due.isEmpty()) return
        update { d ->
            var next = d
            for (x in due) {
                next = next.copy(events = next.events.map { e -> if (e.id == x.eventId) e.copy(paid = e.paid + x.date) else e })
                val pokemon = next.player.pokemon.firstOrNull { it.id == x.companionId }
                if (pokemon == null || !pokemon.inParty || pokemon.partyId != PartyIds.PLAN || !pokemon.canFeed) continue
                val used = Counters.EVENT_CANDIES.get(next.counters, streakDay)
                val candies = minOf(x.candies, EventCompanions.DAILY_CAP - used)
                if (candies <= 0) continue
                val partner = next.player.pokemon.filter { it.inParty }.maxOfOrNull { it.level }
                val grown = pokemon.gainXp(candies * CandyRules.XP_PER_CANDY, partner ?: pokemon.level)
                val rewards = mutableListOf<Reward>(Reward.Exp(candies * CandyRules.XP_PER_CANDY, pokemon.nickname))
                var inventory = next.player.inventory
                var coins = next.player.coins
                if (rng.nextDouble() * 100 < EventCompanions.ITEM_PERCENT) {
                    val drop = DropTable.roll(rng)
                    val (bag, extra) = inventory.addCapped(drop, 1)
                    inventory = bag
                    coins += extra
                    rewards += Reward.ItemDrop(drop, 1)
                }
                val paid = next.copy(
                    player = next.player.copy(pokemon = next.player.pokemon.map { if (it.id == pokemon.id) grown else it }, inventory = inventory, coins = coins),
                    counters = Counters.EVENT_CANDIES.set(next.counters, streakDay, used + candies),
                )
                next = noted(next, log("Event: ${x.title}", rewards, paid), "plan:event:${x.eventId}:${x.date}", "PLAN", x.eventId, "event", nowMillis)
            }
            next
        }
    }

    fun setEventCompanion(id: String, pokemonId: String?) = update { d ->
        val series = PlanEvent.seriesOf(id)
        val valid = pokemonId == null || d.player.pokemon.any { it.id == pokemonId && it.inParty && it.partyId == PartyIds.PLAN }
        if (!valid) d else d.copy(events = d.events.map { e ->
            when {
                e.id != series || e.companionId == pokemonId -> e
                pokemonId == null -> e.copy(companionId = null, companionSince = 0L)
                else -> e.copy(companionId = pokemonId, companionSince = nowMillis)
            }
        })
    }

    fun refresh() {
        val created = data.series.flatMap { s -> s.recurrence?.let { RecurringTasks.instancesToCreate(s.template, it, data.tasks, today, nowMillis) }.orEmpty() }
        if (created.isNotEmpty()) update { it.copy(tasks = it.tasks + created) }
        resetChecklists()
        syncLinks()
    }

    private fun settlePending() {
        val due = data.pending.filter { nowMillis >= it.finalAtMillis }
        if (due.isEmpty()) return
        update { d ->
            var next = d.copy(pending = d.pending - due.toSet())
            for (p in due) {
                val picked = p.done.id == next.pickedTaskId
                if (p.pays) next = achieve(next, p.tier, "Task: ${p.done.title}", "plan:task:${p.done.id}:complete", p.done.id, p.onTime, p.focusedOnIt, picked, p.done.reward)
                if (picked) next = next.copy(pickedTaskId = null)
            }
            next
        }
    }

    fun restoreSeries(series: List<TaskSeries>) = update { it.copy(series = series) }

    fun addGoal(title: String, targetDate: LocalDate? = null): Goal {
        val goal = Goal(newId(), title.trim(), targetDate, createdAtMillis = nowMillis)
        update { it.copy(goals = it.goals + goal) }
        return goal
    }

    fun editGoal(goalId: String, title: String, targetDate: LocalDate?) {
        update { d -> d.copy(goals = d.goals.map { g -> if (g.id == goalId) g.copy(title = title.trim().ifEmpty { g.title }, targetDate = targetDate) else g }) }
    }

    fun goalOf(taskId: String): Goal? = data.goals.firstOrNull { taskId in it.linkedTaskIds }

    fun linkTaskToGoal(taskId: String, goalId: String?) {
        if (data.tasks.none { it.id == taskId }) return
        update { d ->
            d.copy(goals = d.goals.map { g ->
                val linked = g.linkedTaskIds - taskId
                g.copy(linkedTaskIds = if (g.id == goalId) linked + taskId else linked)
            })
        }
    }

    fun deleteGoal(goalId: String) {
        update { d -> d.copy(goals = d.goals.filterNot { it.id == goalId }) }
    }

    fun renameMilestone(goalId: String, milestoneId: String, title: String) {
        val clean = title.trim().ifEmpty { return }
        update { d -> d.copy(goals = d.goals.map { g -> if (g.id == goalId) g.copy(milestones = g.milestones.map { m -> if (m.id == milestoneId) m.copy(title = clean) else m }) else g }) }
    }

    fun deleteMilestone(goalId: String, milestoneId: String) {
        update { d -> d.copy(goals = d.goals.map { g -> if (g.id == goalId) g.copy(milestones = g.milestones.filterNot { it.id == milestoneId }) else g }) }
    }

    fun addMilestone(goalId: String, title: String) {
        val clean = title.trim().ifEmpty { return }
        update { d -> d.copy(goals = d.goals.map { g -> if (g.id == goalId) GoalRules.addMilestone(g, Milestone(newId(), clean, nowMillis)) else g }) }
    }

    fun reopenMilestone(goalId: String, milestoneId: String) =
        update { d -> d.copy(goals = d.goals.map { if (it.id == goalId) GoalRules.reopenMilestone(it, milestoneId) else it }) }

    fun restoreGoals(goals: List<Goal>) = update { it.copy(goals = goals) }

    fun completeMilestone(goalId: String, milestoneId: String): MilestoneResult {
        val goal = data.goals.first { it.id == goalId }
        val paidToday = Counters.MILESTONES.get(data.counters, today)
        val party = data.player.pokemon.filter { it.inParty && it.partyId == PartyIds.PLAN }
            .sortedBy { if (it.id == data.player.planLeadId) 0 else 1 }.take(OwnedPokemon.PARTY_SIZE)
            .map { GoalPartyMember(it.form.id, it.level, it.isShiny) }
        val result = GoalRules.completeMilestone(goal, milestoneId, nowMillis, paidToday, party)
        update { d ->
            var next = d.copy(goals = d.goals.map { if (it.id == goalId) result.goal else it })
            val gifts = buildList {
                if (result.pays) add(Items.SUPER_CANDY to GoalRules.SUPER_CANDIES_PER_MILESTONE)
                result.bonusItem?.let { add(it to 1) }
            }
            if (result.pays) next = next.copy(counters = Counters.MILESTONES.set(next.counters, today, paidToday + 1), activeDays = next.activeDays + streakDay)
            if (gifts.isNotEmpty()) {
                var bag = next.player.inventory
                var coins = next.player.coins
                gifts.forEach { (item, n) -> bag.addCapped(item, n).let { (b, over) -> bag = b; coins += over } }
                val rewards = gifts.map { (item, n) ->
                    if (item == Items.SUPER_CANDY) Reward.Candies(app.pokeplan.core.reward.CandyType.SUPER, n) else Reward.ItemDrop(item, n)
                }
                next = noted(next, log("Goal: ${goal.title}", rewards, next.copy(player = next.player.copy(inventory = bag, coins = coins))), "plan:milestone:$goalId:$milestoneId", "PLAN", goalId, "milestone", nowMillis)
            }
            next
        }
        return result
    }

    val weekStart: LocalDate
        get() = Streaks.weekStartOf(today, firstDayOfWeek)

    fun pickForMe(): Task? {
        val open = openTodayTasks()
        val pick = PickForMe.choose(open.filter { it.id != data.pickedTaskId }.ifEmpty { open }, rng)?.task ?: return null
        update { it.copy(pickedTaskId = pick.id) }
        return pick
    }

    fun miniQuests(): List<MiniQuest> = MiniQuests.forDay(streakDay, rerolled = Counters.MINI_QUEST_REROLL.isSet(data.counters, streakDay))

    fun miniQuestDone(id: String): Boolean = Counters.MINI_QUEST.isSet(data.counters, streakDay, id)

    fun canRerollMiniQuests(): Boolean = !Counters.MINI_QUEST_REROLL.isSet(data.counters, streakDay) && miniQuests().none { miniQuestDone(it.id) }

    fun rerollMiniQuests() {
        if (!canRerollMiniQuests()) return
        update { d -> d.copy(counters = Counters.MINI_QUEST_REROLL.set(d.counters, streakDay, 1)) }
    }

    fun completeMiniQuest(id: String): Boolean {
        val quests = miniQuests()
        if (quests.none { it.id == id } || miniQuestDone(id)) return false
        val day = streakDay
        val berry = Items.randomBerry(rng)
        val allDone = quests.all { it.id == id || miniQuestDone(it.id) }
        val berries = 1 + if (allDone) MiniQuests.ALL_THREE_BONUS_BERRIES else 0
        val candies = MiniQuests.RARE_CANDIES + if (allDone) MiniQuests.ALL_THREE_BONUS_CANDIES else 0
        update { d ->
            val counters = Counters.MINI_QUEST.set(d.counters, day, 1, detail = id)
            val (withBerry, berryOverflow) = d.player.inventory.addCapped(berry, berries)
            val (bag, candyOverflow) = withBerry.addCapped(Items.RARE_CANDY, candies)
            noted(d, log(MINI_QUEST_SOURCE, listOf(Reward.ItemDrop(berry, berries), Reward.Candies(CandyType.RARE, candies)), d.copy(
                counters = counters, player = d.player.copy(inventory = bag, coins = d.player.coins + berryOverflow + candyOverflow),
            ), seen = true), "plan:quest:${day.toEpochDays()}:$id", "PLAN", id, "mini-quest", nowMillis)
        }
        return true
    }

    fun miniQuestRewards(): List<Reward> {
        val since = LocalDateTime(streakDay, kotlinx.datetime.LocalTime(Streaks.ROLLOVER_HOUR, 0)).toEpochMillis(zone)
        return data.rewardLog.filter { it.source == MINI_QUEST_SOURCE && it.atMillis >= since }.flatMap { it.rewards }
    }

    fun oldestOpenTask(): Task? = data.tasks.filter { it.isOpen && it.seriesId == null }.minByOrNull { it.createdAtMillis }

    private val lastActive: LocalDate? get() = data.activeDays.maxOrNull()

    fun welcomeBackDue(): Boolean {
        val last = lastActive ?: return false
        return Streaks.welcomeBack(last, streakDay) && !Counters.WELCOME.isSet(data.counters, last)
    }

    fun claimWelcomeBack(): List<Reward> {
        val last = lastActive?.takeIf { welcomeBackDue() } ?: return emptyList()
        val berry = Items.randomBerry(rng)
        val rewards = listOf(Reward.ItemDrop(Items.RARE_CANDY, WELCOME_BACK_RARE_CANDIES), Reward.ItemDrop(berry, WELCOME_BACK_BERRIES))
        update { d ->
            var bag = d.player.inventory
            var coins = d.player.coins
            listOf(Items.RARE_CANDY to WELCOME_BACK_RARE_CANDIES, berry to WELCOME_BACK_BERRIES).forEach { (item, n) ->
                bag.addCapped(item, n).let { (b, over) -> bag = b; coins += over }
            }
            val counters = Counters.WELCOME.set(d.counters, last, 1)
            noted(d, log("Welcome back", rewards, d.copy(counters = counters, player = d.player.copy(inventory = bag, coins = coins))), "plan:welcome:${last.toEpochDays()}", "PLAN", "absence:${last.toEpochDays()}", "welcome-back", nowMillis)
        }
        return rewards
    }

    fun toggleRestDay(date: LocalDate): Boolean {
        val days = data.restDays
        if (date in days) {
            update { it.copy(restDays = days - date) }
            return true
        }
        if (!Streaks.canAddRestDays(days, setOf(date))) return false
        update { it.copy(restDays = days + date) }
        return true
    }

    val activeQuests: List<Quest>
        get() = data.quests.filterValues { it.completedAtMillis == null }.keys.mapNotNull(Quests::get)

    val availableQuests: List<Quest>
        get() = Quests.all.filter { it.id !in data.quests && Quests.lockedReason(it, data) == null }

    fun questProgress(quest: Quest): List<Pair<Requirement, Int>> {
        val state = data.quests[quest.id] ?: return quest.requirements.map { it to 0 }
        return quest.requirements.map { it to Quests.current(it, state, data, zone, streak.current) }
    }

    fun questReady(quest: Quest): Boolean {
        val state = data.quests[quest.id] ?: return false
        return state.completedAtMillis == null && Quests.isComplete(quest, state, data, zone, streak.current)
    }

    fun acceptQuest(id: String): Boolean {
        val quest = Quests.get(id) ?: return false
        if (id in data.quests || Quests.lockedReason(quest, data) != null || activeQuests.size >= Quests.MAX_ACTIVE) return false
        update { it.copy(quests = it.quests + (id to QuestState(nowMillis))) }
        return true
    }

    fun claimQuest(id: String): OwnedPokemon? {
        val quest = Quests.get(id) ?: return null
        if (!questReady(quest)) return null
        val reward = grantPokemon(quest.speciesId, "Quest: ${quest.title}")
        update { d -> d.copy(quests = d.quests + (id to d.quests.getValue(id).copy(completedAtMillis = nowMillis))) }
        return reward
    }

    fun wheel(days: Int = 7): Insights.Wheel =
        Insights.balanceWheel(data.tasks, data.focusHistory, today.minus(DatePeriod(days = days - 1)), today, zone)

    fun trend(weeks: Int = 6): List<Insights.WeekPoint> = Insights.weeklyTrend(
        data.tasks, data.focusHistory, weeks, today, zone, firstDayOfWeek,
    )

    val streak: Streaks.Result get() = data.streakAt(now)

    val firstDayOfWeek: DayOfWeek get() = data.settings.weekStart

    val perks: PerkBonuses get() = data.perks

    val perkSources: List<PerkSource> get() = data.player.let { Perks.sources(it.planLead, it.party, listOfNotNull(it.guardianOf(PartyIds.PLAN))) }

    fun startFocus(minutes: Int, deepFocus: Boolean, taskId: String? = null, lure: PokemonType? = null): FocusSession {
        data.focus?.let { return it }
        val session = FocusRules.start(
            id = newId(), durationMinutes = minutes, nowMillis = nowMillis, deepFocus = deepFocus, region = data.settings.region.open,
            leadIdAtStart = data.player.planLead?.id, taskId = taskId, lure = lure, emergencyPausesUsedToday = emergencyPausesToday(),
        )
        update { d -> d.copy(focus = session) }
        return session
    }

    private fun emergencyPausesToday(): Int = if (data.emergencyPausesDay == today) data.emergencyPausesUsed else 0

    fun focusEvent(event: FocusEvent): FocusResult? {
        val session = data.focus ?: return null
        val next = session.apply(event, nowMillis)
        if (next != session) update { it.copy(focus = next) }
        return settleFocus()
    }

    fun tickFocus(): FocusResult? {
        val session = data.focus ?: return null
        val next = session.tick(nowMillis)
        if (next != session) update { it.copy(focus = next) }
        return settleFocus()
    }

    private fun settleFocus(): FocusResult? {
        val session = data.focus ?: return null
        val used = session.emergencyPausesUsed
        if (used > emergencyPausesToday()) update { it.copy(emergencyPausesDay = today, emergencyPausesUsed = used) }
        return when (val phase = session.phase) {
            is Phase.Completed -> {
                val minutes = (session.durationMillis / 60_000L).toInt()
                val event: EffortEvent = session.toEffortEvent(newId(), nowMillis, epochDay).let { it.copy(region = it.region.open) }
                val player = data.player
                val outcome = RewardEngine.effort(player.atStartOf(session), event, rng)
                update { d ->
                    noted(d, log("Focus: $minutes min", outcome.rewards, d.copy(
                        player = outcome.state.copy(planLeadId = d.player.planLeadId), focus = null, activeDays = d.activeDays + streakDay, lastFocusEnd = FocusEnd(nowMillis, true, minutes = minutes, plannedMinutes = minutes, taskId = session.taskId, deepFocus = session.deepFocus),
                        focusHistory = d.focusHistory + session.record(minutes, completed = true),
                        tasks = d.tasks.map { if (it.id == session.taskId) it.copy(focusedMinutes = it.focusedMinutes + minutes) else it },
                    ), origin = Origin(Origin.How.FOCUS_EGG, event.region)), "plan:focus:${event.newPokemonId}", "PLAN", event.newPokemonId, "focus", nowMillis)
                }
                FocusResult.Hatched(outcome.rewards)
            }
            is Phase.Failed -> {
                val minutes = (session.focusedMillis(phase.at) / 60_000L).toInt()
                magicGuard(session, phase.reason, minutes)?.let { return it }
                update { d ->
                    d.copy(
                        focus = null, lastFocusEnd = FocusEnd(nowMillis, false, phase.reason, minutes = minutes, plannedMinutes = (session.durationMillis / 60_000L).toInt(), taskId = session.taskId, deepFocus = session.deepFocus),
                        focusHistory = d.focusHistory + session.record(minutes, completed = false),
                    )
                }
                FocusResult.Failed(phase.reason)
            }
            else -> null
        }
    }

    private fun magicGuard(session: FocusSession, reason: FailReason, minutes: Int): FocusResult? {
        val player = data.player.atStartOf(session)
        if (minutes < Egg.MIN_MINUTES || Counters.MAGIC_GUARD.isSet(data.counters, weekStart)) return null
        if (!Perks.total(player.planLead, player.party, guardians = listOfNotNull(player.guardianOf(PartyIds.PLAN))).keepsFailedEgg) return null
        val event = EffortEvent(minutes, false, session.region.open, session.lure, epochDay, newId(), nowMillis)
        val outcome = RewardEngine.effort(player, event, rng)
        update { d ->
            noted(d, log("Magic Guard: $minutes min", outcome.rewards, d.copy(
                player = outcome.state.copy(planLeadId = d.player.planLeadId), focus = null,
                lastFocusEnd = FocusEnd(nowMillis, true, reason, guarded = true, minutes = minutes, plannedMinutes = (session.durationMillis / 60_000L).toInt(), taskId = session.taskId, deepFocus = session.deepFocus),
                focusHistory = d.focusHistory + session.record(minutes, completed = false),
                counters = Counters.MAGIC_GUARD.set(d.counters, weekStart, 1),
            ), origin = Origin(Origin.How.FOCUS_EGG, event.region)), "plan:focus:${event.newPokemonId}", "PLAN", event.newPokemonId, "magic-guard", nowMillis)
        }
        return FocusResult.Hatched(outcome.rewards)
    }

    private fun app.pokeplan.core.reward.PlayerState.atStartOf(session: FocusSession) =
        if (session.leadIdAtStart != null && party.any { it.id == session.leadIdAtStart }) copy(planLeadId = session.leadIdAtStart) else this

    private fun FocusSession.record(minutes: Int, completed: Boolean) = FocusRecord(startedAtMillis, minutes, completed, taskId, deepFocus)

    fun dismissFocusEnd() = update { it.copy(lastFocusEnd = null) }

    private fun pokemon(id: String): OwnedPokemon = data.player.pokemon.first { it.id == id }

    private fun replace(p: OwnedPokemon) =
        update { d -> d.copy(player = d.player.copy(pokemon = d.player.pokemon.map { if (it.id == p.id) p else it })) }

    fun placeOf(id: String): Place = Places.of(data, id)

    internal fun moveTo(id: String, then: (AppData) -> AppData): Boolean {
        if (!Places.movable(data, id)) return false
        update { d -> then(Places.vacate(d, id)).let { r -> r.copy(player = Places.leads(r.player)) } }
        return true
    }

    fun setBuddy(id: String): Boolean {
        if (Guardians.canBe(pokemon(id))) return false
        return moveTo(id) { d -> d.copy(player = d.player.copy(buddyId = id)) }
    }

    fun rename(id: String, nickname: String) = replace(pokemon(id).withNickname(nickname))

    fun setInParty(id: String, inParty: Boolean, partyId: String = PartyIds.PLAN): Boolean {
        val p = pokemon(id)
        val staying = inParty && p.inParty && p.partyId == partyId
        if (inParty && !staying && data.player.partyOf(partyId).size >= OwnedPokemon.PARTY_SIZE) return false
        val leavingPlan = p.inParty && p.partyId == PartyIds.PLAN && !(inParty && partyId == PartyIds.PLAN)
        if (leavingPlan && data.player.party.size <= 1) return false
        if (!inParty) return p.inParty && moveTo(id) { it }
        return moveTo(id) { d -> d.copy(player = d.player.copy(pokemon = d.player.pokemon.map { if (it.id == id) it.copy(inParty = true, partyId = partyId) else it })) }
    }

    fun sendToPc(id: String): Boolean = when (val place = Places.of(data, id)) {
        Place.Pc, Place.Buddy -> false
        is Place.Party -> setInParty(id, false)
        else -> moveTo(id) { it }
    }

    fun setPlanLead(id: String): Boolean {
        val p = pokemon(id)
        if (!p.inParty || p.partyId != PartyIds.PLAN) return false
        update { d -> d.copy(player = d.player.copy(planLeadId = id)) }
        return true
    }

    fun setCareLead(id: String): Boolean {
        val p = pokemon(id)
        if (!p.inParty || p.partyId != PartyIds.CARE) return false
        update { d -> d.copy(player = d.player.copy(careLeadId = id)) }
        return true
    }


    val guardianCandidates: List<OwnedPokemon> get() = data.player.pokemon.filter(Guardians::canBe)

    fun guardianOf(module: String): OwnedPokemon? = data.player.guardianOf(module)

    fun slotGuardian(id: String, module: String): Boolean {
        val p = pokemon(id)
        if (!Guardians.canBe(p) || module !in Guardians.MODULES) return false
        return moveTo(id) { d -> d.copy(player = d.player.copy(moduleGuardians = d.player.moduleGuardians - module + (module to id))) }
    }

    fun unslotGuardian(module: String) = update { d -> d.copy(player = d.player.copy(moduleGuardians = d.player.moduleGuardians - module)) }

    fun grantPokemon(speciesId: Int, source: String, how: Origin.How = Origin.How.QUEST, form: String? = null): OwnedPokemon {
        var granted: OwnedPokemon? = null
        update { d -> grantIn(d, speciesId, source, how, form).also { granted = it.second }.first }
        return granted!!
    }

    internal fun grantIn(d: AppData, speciesId: Int, source: String, how: Origin.How = Origin.How.QUEST, form: String? = null): Pair<AppData, OwnedPokemon> {
        val option = HatchOption(speciesId, form ?: Dex.defaultForm(speciesId).identifier)
        val p = Hatching.create(newId(), option, OwnedPokemon.PARTY_SIZE, nowMillis, 60, rng).copy(inParty = false)
        val after = log(source, listOf(Reward.Hatched(p, fromTask = false)),
            d.copy(player = d.player.copy(pokemon = d.player.pokemon + p, caught = d.player.caught + p.speciesId)), origin = Origin(how))
        return after to p
    }

    fun feedRareCandy(id: String, count: Int = 1): Int {
        var p = pokemon(id)
        val partnerLevel = data.player.pokemon.filter { it.inParty }.maxOfOrNull { it.level } ?: p.level
        var used = 0
        while (used < count && p.canFeed && data.player.inventory.has(Items.RARE_CANDY, used + 1)) {
            p = p.feedRareCandy(partnerLevel)
            used++
        }
        if (used > 0) update { d ->
            val bag = d.player.inventory.remove(Items.RARE_CANDY, used)
            d.copy(player = d.player.copy(inventory = bag, pokemon = d.player.pokemon.map { if (it.id == id) p else it }))
        }
        return used
    }

    fun evolutionOptions(id: String): List<EvolutionOption> {
        val p = pokemon(id)
        val mates = p.partyId?.let { party -> data.player.pokemon.filter { it.partyId == party && it.id != id } }.orEmpty()
        val ctx = EvolutionContext(now.hour, data.player.inventory.counts, data.completedTrials + data.world.evolutionTrials, mates)
        return EvolutionRules.options(p, ctx)
    }

    fun deviceTrialDone(id: String, option: EvolutionOption) {
        if (app.pokeplan.core.pokemon.EvolutionTrials.device(option.evolution) == null) return
        update { d -> d.copy(completedTrials = d.completedTrials + app.pokeplan.core.pokemon.EvolutionTrials.doneKey(option.evolution, id)) }
    }

    fun stepReading(id: String, option: EvolutionOption, counter: Long): Int {
        val walk = data.stepWalks[id]?.reading(counter) ?: StepWalk(counter)
        val key = app.pokeplan.core.pokemon.EvolutionTrials.doneKey(option.evolution, id)
        if (walk.walked >= app.pokeplan.core.pokemon.EvolutionTrials.STEPS) {
            update { d -> d.copy(stepWalks = d.stepWalks - id, completedTrials = d.completedTrials + key) }
        } else if (walk != data.stepWalks[id]) {
            update { d -> d.copy(stepWalks = d.stepWalks + (id to walk)) }
        }
        return walk.walked
    }

    fun evolve(id: String, option: EvolutionOption) {
        val result = EvolutionRules.evolve(pokemon(id), option)
        update { d ->
            var bag = d.player.inventory
            result.consumed.forEach { (item, n) -> bag = bag.remove(item, n) }
            d.copy(player = d.player.copy(
                inventory = bag, pokemon = d.player.pokemon.map { if (it.id == id) result.pokemon else it },
                caught = d.player.caught + result.pokemon.speciesId,
            ))
        }
    }

    fun transferBlock(id: String): TransferBlock? =
        pokemon(id).let { p ->
            Transfer.block(p, id == data.player.buddyId || id == data.player.planLead?.id || id == data.player.careLead?.id || id == data.player.healLead?.id || id == data.player.mintLead?.id || id == data.player.baseLead?.id || id == data.player.linkLead?.id, if (p.partyId == PartyIds.PLAN) data.player.party.size else OwnedPokemon.PARTY_SIZE)
        }

    fun sees(feature: Feature): Boolean =
        TrainerRank.isVisible(feature, data.player.trainerPoints) || (feature == Feature.GUARDIANS && guardianCandidates.isNotEmpty())

    fun newlyOpened(): List<Feature> = ((data.seenRank + 1)..data.player.rank).flatMap { Feature.at(it) }

    fun markRankSeen() = update { it.copy(seenRank = maxOf(it.seenRank, it.player.rank)) }

    fun markVersionSeen(version: String) = update { it.copy(lastSeenVersion = version) }

    fun markSeen(speciesIds: Collection<Int>) {
        if (data.seenSpecies.containsAll(speciesIds)) return
        update { it.copy(seenSpecies = it.seenSpecies + speciesIds) }
    }

    fun isFavorite(id: String): Boolean = id in data.favorites

    internal var worldSummary: ReportSummary? = null

    fun toggleFavorite(id: String) = update { d -> d.copy(favorites = if (id in d.favorites) d.favorites - id else d.favorites + id) }

    fun transfer(id: String): Boolean {
        if (transferBlock(id) != null || id in data.favorites || !Places.movable(data, id)) return false
        val p = pokemon(id)
        val quote = Transfer.quote(p)
        update { before ->
            val d = Places.vacate(before, id)
            var bag = d.player.inventory.add(Items.RARE_CANDY, quote.rareCandies)
            quote.returnedItem?.let { bag = bag.add(it) }
            d.copy(player = d.player.copy(
                pokemon = d.player.pokemon.filter { it.id != id }, inventory = bag, coins = d.player.coins + quote.coins,
            ), origins = d.origins - id)
        }
        return true
    }

    fun doneToday(): Int = data.tasks.count { it.state == TaskState.DONE && (it.completedAtMillis ?: 0L) >= startOfTodayMillis }

    fun buddyEvent(care: Boolean = false): BuddyEventNow? = eventWhere { !care || it.startsWith(CARE_LOG_PREFIX) }

    fun moduleEvent(prefix: String?): BuddyEventNow? = eventWhere { source ->
        if (prefix != null) source.startsWith(prefix)
        else MODULE_LOG_PREFIXES.none { source.startsWith(it) } && source != WORLD_SOURCE && source != MINI_QUEST_SOURCE
    }

    private fun eventWhere(counts: (String) -> Boolean): BuddyEventNow? {
        val recent = data.rewardLog.filter { nowMillis - it.atMillis < EVENT_MILLIS && counts(it.source) }
        val latest = recent.lastOrNull() ?: return null
        val hatches = recent.filter { it.source != WORLD_SOURCE }.flatMap { e -> e.rewards.filterIsInstance<Reward.Hatched>() }
        val event = when {
            hatches.any { it.pokemon.isShiny } -> BuddyEvent.SHINY
            hatches.isNotEmpty() -> BuddyEvent.HATCHED
            else -> BuddyEvent.DONE
        }
        return BuddyEventNow(event, latest.atMillis)
    }

    fun levelProgress(p: OwnedPokemon): Float {
        if (!p.canFeed) return 1f
        val partner = data.player.pokemon.filter { it.inParty }.maxOfOrNull { it.level } ?: p.level
        return ((p.candyProgress + p.xp.toFloat() / CandyRules.XP_PER_CANDY) / CandyRules.candiesNeeded(p.level, partner)).coerceIn(0f, 1f)
    }

    fun pet(pokemonId: String? = null): Int {
        val buddy = (if (pokemonId == null) data.player.buddy else data.player.pokemon.firstOrNull { it.id == pokemonId }) ?: return 0
        completeMiniQuest(MiniQuests.PET_QUEST)
        val gainedToday = Counters.PET.get(data.counters, today)
        if (gainedToday >= PET_DAILY_CAP || buddy.friendship >= FriendshipRules.MAX) return 0
        val petted = buddy.gainFriendship(FriendshipRules.Source.PET)
        val gained = petted.friendship - buddy.friendship
        update { d ->
            val counters = Counters.PET.set(d.counters, today, gainedToday + gained)
            d.copy(counters = counters, player = d.player.copy(pokemon = d.player.pokemon.map { if (it.id == buddy.id) petted else it }))
        }
        return gained
    }

    fun holdItem(pokemonId: String, itemId: String): Boolean {
        val p = pokemon(pokemonId)
        if (!data.player.inventory.has(itemId) || !Items.holdable(itemId) || Guardians.canBe(p) || p.heldItem == itemId) return false
        update { d ->
            var bag = d.player.inventory.remove(itemId)
            p.heldItem?.let { bag = bag.add(it) }
            d.copy(player = d.player.copy(inventory = bag, pokemon = d.player.pokemon.map { if (it.id == pokemonId) p.withHeldItem(itemId) else it }))
        }
        return true
    }

    fun setMoves(pokemonId: String, identifiers: List<String>): Boolean {
        val p = data.player.pokemon.firstOrNull { it.id == pokemonId } ?: return false
        if (!Moveset.canSet(p, identifiers)) return false
        worldSettle()
        update { d -> d.copy(player = d.player.copy(pokemon = d.player.pokemon.map { if (it.id == pokemonId) it.withMoves(identifiers) else it })) }
        return true
    }

    fun takeItem(pokemonId: String) {
        val p = pokemon(pokemonId)
        val item = p.heldItem ?: return
        update { d ->
            d.copy(player = d.player.copy(
                inventory = d.player.inventory.add(item), pokemon = d.player.pokemon.map { if (it.id == pokemonId) p.withHeldItem(null) else it },
            ))
        }
    }

    fun canUse(itemId: String, p: OwnedPokemon): Boolean = ItemEffects.canUse(itemId, p)

    fun useItem(use: ItemUse, pokemonId: String): Boolean {
        if (!data.player.inventory.has(use.itemId)) return false
        val changed = data.player.pokemon.firstOrNull { it.id == pokemonId }?.let { ItemEffects.apply(it, use) } ?: return false
        update { d ->
            d.copy(player = d.player.copy(
                inventory = d.player.inventory.remove(use.itemId), pokemon = d.player.pokemon.map { if (it.id == pokemonId) changed else it },
            ))
        }
        return true
    }

    fun useItem(itemId: String, pokemonId: String): Boolean = useItem(ItemUse.Plain(itemId), pokemonId)

    fun townFinds(): List<TownFinds.Find?> {
        val taken = Counters.TOWN_FINDS.get(data.counters, today)
        return TownFinds.forDay(epochDay).mapIndexed { i, f -> f.takeIf { taken and (1 shl i) == 0 } }
    }

    fun collectTownFind(index: Int): Reward? {
        val find = townFinds().getOrNull(index) ?: return null
        val reward = if (find.isPoke) Reward.Poke(find.amount) else Reward.ItemDrop(find.itemId, find.amount)
        update { d ->
            val taken = Counters.TOWN_FINDS.get(d.counters, today)
            val counters = Counters.TOWN_FINDS.set(d.counters, today, taken or (1 shl index))
            val player = if (find.isPoke) d.player.copy(coins = d.player.coins + find.amount) else {
                val (bag, over) = d.player.inventory.addCapped(find.itemId, find.amount)
                d.player.copy(inventory = bag, coins = d.player.coins + over)
            }
            noted(d, log("Town floor", listOf(reward), d.copy(counters = counters, player = player)), "town:find:$epochDay:$index", "WORLD", "town:$epochDay", "town-find", nowMillis)
        }
        return reward
    }

    fun grant(itemId: String, amount: Int) = update { d ->
        val (bag, overflow) = d.player.inventory.addCapped(itemId, amount)
        d.copy(player = d.player.copy(inventory = bag, coins = d.player.coins + overflow))
    }

    fun todayTasks(): List<Task> = TaskViews.today(data.tasks.filter { it.state != TaskState.DELETED }, now)

    fun openTodayTasks(): List<Task> {
        val pending = data.pending.map { it.original.id }.toSet()
        return todayTasks().filter { it.id !in pending }
    }

    fun buddyBerry(): String? = berries().keys.minOrNull()

    companion object {
        const val ROLES_VERSION = 1

        const val MAX_TEMPLATE_ITEMS = 60

        const val WORLD_SOURCE = "World"
        const val MEW_GIFT = "gift-mew"

        const val MAX_PICKER_OPTIONS = 30

        const val WELCOME_BACK_RARE_CANDIES = 5
        const val WELCOME_BACK_BERRIES = 2

        const val MAX_SUBTASKS = 5

        const val EVENT_MILLIS = 5 * 60_000L

        const val PET_DAILY_CAP = 10

        const val MINI_QUEST_SOURCE = "Mini-quest"

        const val LOG_SIZE = 50

        const val BEFRIEND_DAILY_CAP = 4

        val CARE_DAILY_CAP: Map<CareKind, Int> = mapOf(CareKind.ROUTINE to 5, CareKind.CALM_BREAK to 3, CareKind.SKILL to 3, CareKind.WEEKLY_RHYTHM to 1, CareKind.PLAY to 6)
        const val CARE_LOG_PREFIX = "Care: "

        const val CARE_FRIEND_SOURCE = CARE_LOG_PREFIX + "made a friend"

        val HEAL_DAILY_CAP: Map<HealKind, Int> = mapOf(HealKind.ACTIVITY to HealRules.ACTIVITIES_PAID_PER_DAY, HealKind.PARTICIPATION to HealRules.PARTICIPATION_PAID_PER_DAY, HealKind.STEPS to HealRules.STEPS_PAID_PER_DAY, HealKind.IMPORT to HealRules.IMPORTS_PAID_PER_DAY)

        const val HEAL_LOG_PREFIX = "Heal: "
        private val MODULE_LOG_PREFIXES by lazy { listOf(CARE_LOG_PREFIX, HEAL_LOG_PREFIX, MINT_LOG_PREFIX, BASE_LOG_PREFIX, OLD_BASE_LOG_PREFIX, LINK_LOG_PREFIX) }
        private val HEAL_SOURCES = mapOf(HealKind.ACTIVITY to "Field Research", HealKind.PARTICIPATION to "Field Research", HealKind.STEPS to "Field Research: steps", HealKind.IMPORT to "Field Research: records read").mapValues { HEAL_LOG_PREFIX + it.value }

        val MINT_CAPS: Map<MintKind, Int> = mapOf(MintKind.RECONCILED to app.pokeplan.mint.MintRules.RECONCILES_PAID_PER_DAY, MintKind.REVIEW to app.pokeplan.mint.MintRules.REVIEWS_PAID_PER_WEEK, MintKind.LOGGED to app.pokeplan.mint.MintRules.RECORDS_PAID_PER_DAY)

        const val MINT_LOG_PREFIX = "Mint: "
        private val MINT_SOURCES = mapOf(MintKind.RECONCILED to "Ledger Research", MintKind.REVIEW to "Ledger Research", MintKind.LOGGED to "Ledger Research: records").mapValues { MINT_LOG_PREFIX + it.value }

        val BASE_CAPS: Map<BaseKind, Int> = mapOf(BaseKind.CHORE to app.pokeplan.base.BaseRules.CHORES_PAID_PER_DAY, BaseKind.MAINTENANCE to app.pokeplan.base.BaseRules.MAINTENANCE_PAID_PER_DAY)

        const val BASE_LOG_PREFIX = "Base: "

        const val OLD_BASE_LOG_PREFIX = "Home: "
        private val BASE_SOURCES = mapOf(BaseKind.CHORE to "Habitat Discovery", BaseKind.MAINTENANCE to "Habitat Discovery").mapValues { BASE_LOG_PREFIX + it.value }

        val LINK_CAPS: Map<LinkMoment, Int> = mapOf(LinkMoment.FOLLOW_UP to app.pokeplan.link.LinkRules.FOLLOW_UPS_PAID_PER_DAY)

        const val LINK_LOG_PREFIX = "Link: "

        const val SAFARI_PER_DAY = 5
        const val SAFARI_SOURCE = LINK_LOG_PREFIX + "Friend Safari"
        private val LINK_SOURCES = mapOf(LinkMoment.FOLLOW_UP to "Postcard Trail").mapValues { LINK_LOG_PREFIX + it.value }

        const val LINK_PAID_KEPT = 1000

        const val HOME_PAID_KEPT = 1000

        const val LEDGER_NOTES_KEPT = 50

        const val MINT_PAID_KEPT = 1000

        const val HEAL_PAID_KEPT = 1000

        private val CARE_SOURCES = mapOf(
            CareKind.ROUTINE to "a routine", CareKind.CALM_BREAK to "a calm break", CareKind.WEEKLY_RHYTHM to "kept a rhythm", CareKind.SKILL to "a skill practised", CareKind.PLAY to "time together",
        ).mapValues { CARE_LOG_PREFIX + it.value }

        internal val DEVICE_ONLY = setOf("shownCalendars", "pickerOptions", "gearRates", "gear")

        private val EXPORT_LEFT_OUT = DEVICE_ONLY - setOf("pickerOptions", "gear")

        fun normalizeDocument(text: String, nowMillis: Long): String {
            val storage = MemoryStorage(text)
            val store = GameStore(storage, { nowMillis })
            check(storage.keptAside == null) { "the save cannot be read" }
            return json.encodeToString(AppData.serializer(), store.data)
        }

        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; classDiscriminator = "kind" }

        val starterGroups: List<Pair<String, List<Int>>> = listOf(
            "Kanto" to listOf(1, 4, 7), "Johto" to listOf(152, 155, 158), "Hoenn" to listOf(252, 255, 258),
            "Sinnoh" to listOf(387, 390, 393), "Unova" to listOf(495, 498, 501), "Kalos" to listOf(650, 653, 656),
            "Alola" to listOf(722, 725, 728), "Galar" to listOf(810, 813, 816), "Paldea" to listOf(906, 909, 912),
            "Partners" to listOf(25, 133),
        )

        val starters: List<Int> = starterGroups.flatMap { it.second }
    }
}

sealed interface GearHelper {
    data class Owned(val pokemon: OwnedPokemon) : GearHelper
    data object None : GearHelper
}
