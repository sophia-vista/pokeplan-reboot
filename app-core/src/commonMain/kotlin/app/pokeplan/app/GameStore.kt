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
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.pokemon.PerkBonuses
import app.pokeplan.core.pokemon.PerkSource
import app.pokeplan.core.pokemon.Perks
import app.pokeplan.core.pokemon.Transfer
import app.pokeplan.core.pokemon.TransferBlock
import app.pokeplan.core.reward.CandyType
import app.pokeplan.core.reward.RewardRules
import app.pokeplan.core.pokemon.Inventory
import app.pokeplan.core.pokemon.Shop
import app.pokeplan.core.reward.AchievementEvent
import app.pokeplan.core.reward.BefriendEvent
import app.pokeplan.core.reward.VisitorInheritance
import app.pokeplan.core.reward.CareEvent
import app.pokeplan.core.reward.CareKind
import app.pokeplan.core.reward.HealEvent
import app.pokeplan.core.reward.HealKind
import app.pokeplan.core.reward.HomeEvent
import app.pokeplan.core.reward.HomeKind
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
import app.pokeplan.plan.Category
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

/** What a focus session turned out to be. */
sealed interface FocusResult {
    data class Hatched(val rewards: List<Reward>) : FocusResult
    data class Failed(val reason: FailReason) : FocusResult
}

/**
 * The app's rules engine for one device: holds the saved [data], applies every action to it with the real game rules
 * and saves after each change. No UI code in here, so it is tested on the JVM.
 *
 * Rewards are granted locally; there is no server (retired in 0.130.0).
 */
class GameStore(
    storage: Storage,
    private val clock: Clock,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val rng: Random = Random,
) : World {
    private val document = SavedDocument(storage, AppData.serializer(), json, tidy = ::tidy) { AppData() }

    val data: AppData get() = document.value

    /** Calls [listener] after every change. Returns a function that stops listening. */
    fun observe(listener: (AppData) -> Unit): () -> Unit = document.observe(listener)

    /** Picks up a change another process made to the save (an Android widget); see [SavedDocument.reload]. */
    fun reload() = document.reload()

    private fun update(change: (AppData) -> AppData) {
        val before = data
        var after = foldPoke(before, change(before))
        // A shiny stays in the Shiny Dex, and a form in the form dex, even if the Pokémon is transferred later.
        val shinyNow = after.player.pokemon.filter { it.isShiny }.map { it.speciesId }.toSet()
        if (!after.shinySpecies.containsAll(shinyNow)) after = after.copy(shinySpecies = after.shinySpecies + shinyNow)
        document.write(FormDex.record(after))
    }

    /**
     * One Poké balance (`PlayerState.coins`), written by the ledger (the live save turns a change of it into a ledger event, REP-014).
     * World's rules hold no balance: what they earn arrives as `WorldData.pokeEarned`, which is added to the coins here and cleared in the
     * same update, and the rules that spend are given the balance and charged by the store (`worldUpgrade`, `worldBuyCandy`, `worldTrain`).
     * [worldPay] reads `pokeEarned` itself, in the same update, for the reward log and the ledger note.
     */
    private fun foldPoke(before: AppData, after: AppData): AppData {
        val migrated = if (before.pokeMigration == null || after.pokeMigration == null) migratePoke(after) else after
        val earned = migrated.world.pokeEarned
        if (earned == 0) return migrated
        return migrated.copy(player = migrated.player.copy(coins = migrated.player.coins + earned), world = migrated.world.copy(pokeEarned = 0))
    }

    /**
     * Habits of an older save become recurring tasks with a frequency target (PLAN.md sections 5 and 11.2): same id, creation day,
     * completion days and payment day, so nothing paid is paid again and a goal that links one still does. The habit list is kept aside in
     * `legacyHabits`, and the merge pays nothing. Idempotent: once the list is empty it does nothing.
     */
    private fun mergeHabits(d: AppData): AppData {
        if (d.habits.isEmpty()) return d
        val known = d.series.map { it.template.id }.toSet()
        val merged = d.habits.filter { it.id !in known }.map { TaskSeries.of(it, nowMillis) }
        return d.copy(series = d.series + merged, habits = emptyList(), legacyHabits = d.legacyHabits + d.habits, habitsMergedAtMillis = d.habitsMergedAtMillis ?: nowMillis)
    }

    /** The one-time merge of Plan's coins and World's Poké, 1:1, recording both sources. Does nothing once it has run. */
    private fun migratePoke(d: AppData): AppData {
        // The World balance of a save is read once (`legacyPoke`), then it is 0 and not written.
        if (d.pokeMigration != null) return if (d.world.legacyPoke == 0) d else d.copy(world = d.world.copy(legacyPoke = 0))
        val total = d.player.coins + d.world.legacyPoke
        return d.copy(
            player = d.player.copy(coins = total), world = d.world.copy(legacyPoke = 0),
            pokeMigration = PokeMigration(d.player.coins, d.world.legacyPoke, nowMillis, RewardRules.VERSION),
        )
    }

    /** Everything PokéPlan keeps on this device, as readable JSON,. */
    fun exportJson(): String {
        val pretty = Json(json) { prettyPrint = true }
        // The player's own data, so the Picker's list is in it; the tickets, the calendars shown and the public rate cache are not.
        val full = json.encodeToJsonElement(AppData.serializer(), data.copy(eggTickets = EggTickets(), shownCalendars = emptySet(), gearRates = null)) as JsonObject
        return pretty.encodeToString(JsonObject.serializer(), JsonObject(full.filterKeys { it !in EXPORT_LEFT_OUT }))
    }

    /** Erases this device's save: back to the welcome screen. */
    fun eraseDevice() = update { AppData() }

    /** What every save that comes in (from the device or the cloud) goes through: numbers back in their range. */
    // The Modules setting is gone from Settings: Care is always shown, so a save that switched it off is switched back on when loaded.
    private fun tidy(d: AppData): AppData = d.copy(settings = d.settings.clamped().copy(careModule = true), player = migrateRoles(d.player)).let(::migratePoke).let(Places::resolve).let(::mergeHabits)

    /**
     * Saves from before the Pokémon roles were split (docs/pokelife POKEMON_ECOSYSTEM.md, section 2.2) used the buddy as
     * Plan's lead. The buddy stays the global buddy and also becomes the lead of Plan's party if it is in it, so nothing
     * changes for the player; from then on the two are chosen separately. Care's lead already was a party lead.
     */
    private fun migrateRoles(p: app.pokeplan.core.reward.PlayerState): app.pokeplan.core.reward.PlayerState {
        if (p.rolesVersion >= ROLES_VERSION) return p
        val lead = p.buddyId?.takeIf { id -> p.party.any { it.id == id } }
        return p.copy(planLeadId = p.planLeadId ?: lead, rolesVersion = ROLES_VERSION)
    }

    /** Shows or hides calendar events from this phone's calendars on the day timeline. */
    fun setShownCalendars(ids: Set<Long>) = update { it.copy(shownCalendars = ids) }

    // --- egg tickets --------------------------------------------------------------------------------------------------


    // --- time and ids -------------------------------------------------------------------------------------------------

    val nowMillis: Long get() = clock.nowMillis()
    val now: LocalDateTime get() = nowMillis.toLocalDateTime(zone)
    val today: LocalDate get() = now.date
    private val epochDay: Long get() = today.toEpochDays()

    /** The first millisecond of today in the player's time zone. */
    val startOfTodayMillis: Long get() = LocalDateTime(today, kotlinx.datetime.LocalTime(0, 0)).toEpochMillis(zone)

    /** The local day [millis] falls on (a finished task's day in the Done list). */
    fun dayOf(millis: Long): LocalDate = millis.toLocalDateTime(zone).date

    /** The player's offset from UTC now, in milliseconds (what turns a UTC day into the player's own). */
    val utcOffsetMillis: Long get() = zone.offsetAt(kotlin.time.Instant.fromEpochMilliseconds(nowMillis)).totalSeconds * 1000L

    private fun newId(): String = newId(nowMillis, rng)

    // --- module tutorials ---------------------------------------------------------------------------------------------

    /**
     * Whether the tutorial [id] is still on offer: not done, and the player has not done the real thing (a finished focus session for
     * Plan's). Care's tutorial also stays off for a player with calm breaks; [careBreaks] is how many Care has recorded (Care keeps its own save).
     */
    fun tutorialOffered(id: String, careBreaks: Int = 0, healActivities: Int = 0, mintRecords: Int = 0, homeRecords: Int = 0, linkRecords: Int = 0): Boolean = data.onboarded && id !in data.tutorials && when (id) {
        Tutorials.PLAN_FOCUS -> data.focusHistory.none { it.completed }
        Tutorials.CARE_BREATH -> careBreaks == 0
        Tutorials.HEAL_WALK -> healActivities == 0
        Tutorials.MINT_TALLY -> mintRecords == 0
        Tutorials.HOME_TIDY -> homeRecords == 0
        Tutorials.LINK_POSTCARD -> linkRecords == 0
        else -> false
    }

    /**
     * Finishes the tutorial [id]: one egg of its module's method hatches into that module's party (Plan: a focus egg from the open region;
     * Care: a Care Egg), once per save. It is no focus session or calm break, so it leaves no history, streak, cap, meter or friendship. Returns
     * the rewards, or null when it is unknown, already done or not started yet.
     */
    fun completeTutorial(id: String): List<Reward>? {
        if (!data.onboarded || id in data.tutorials) return null
        val (region, party, how) = when (id) {
            Tutorials.PLAN_FOCUS -> Triple(data.settings.region.open, PartyIds.PLAN, Origin.How.FOCUS_EGG)
            Tutorials.CARE_BREATH -> Triple(Region.CARE, PartyIds.CARE, Origin.How.CARE_EGG)
            Tutorials.HEAL_WALK -> Triple(Region.HEAL, PartyIds.HEAL, Origin.How.FIELD_EGG)
            Tutorials.MINT_TALLY -> Triple(Region.MINT, PartyIds.MINT, Origin.How.LEDGER_EGG)
            Tutorials.HOME_TIDY -> Triple(Region.HOUSEHOLD, PartyIds.HOME, Origin.How.HABITAT_EGG)
            Tutorials.LINK_POSTCARD -> Triple(Region.LINK, PartyIds.LINK, Origin.How.POSTCARD_EGG)
            else -> return null
        }
        val outcome = RewardEngine.tutorialEgg(data.player, region, party, newId(), nowMillis, rng)
        update { d -> log((if (id == Tutorials.CARE_BREATH) CARE_LOG_PREFIX else if (id == Tutorials.HEAL_WALK) HEAL_LOG_PREFIX else if (id == Tutorials.MINT_TALLY) MINT_LOG_PREFIX else if (id == Tutorials.HOME_TIDY) HOME_LOG_PREFIX else if (id == Tutorials.LINK_POSTCARD) LINK_LOG_PREFIX else "") + "first egg", outcome.rewards, d.copy(player = outcome.state, tutorials = d.tutorials + id), origin = Origin(how, region)) }
        return outcome.rewards
    }

    // --- onboarding ---------------------------------------------------------------------------------------------------

    /** Starts as a guest: creates the trainer, the starter (which becomes the buddy, and is in no party: one place only) and a first task that pays at once. */
    fun start(trainerName: String, starterSpeciesId: Int) {
        check(!data.onboarded) { "already started" }
        val option = HatchOption(starterSpeciesId, Dex.defaultForm(starterSpeciesId).identifier)
        val starter = Hatching.create(newId(), option, 0, nowMillis, 25, rng).copy(isShiny = false, inParty = false)
        val first = Task(
            id = newId(), title = "Finish your first task", rank = Rank.E, planned = today, tutorial = true, createdAtMillis = nowMillis,
        )
        update {
            it.copy(
                trainerName = trainerName.trim().ifEmpty { "Trainer" }, onboarded = true,
                player = it.player.copy(pokemon = listOf(starter), buddyId = starter.id, rolesVersion = ROLES_VERSION, caught = setOf(starter.speciesId)),
                placesVersion = Places.VERSION, tasks = listOf(first), origins = mapOf(starter.id to Origin(Origin.How.STARTER)),
            )
        }
    }

    fun rename(trainerName: String) = update { it.copy(trainerName = trainerName.trim().ifEmpty { it.trainerName }) }

    fun changeSettings(change: (Settings) -> Settings) = update { it.copy(settings = change(it.settings).clamped()) }

    // --- rewards ------------------------------------------------------------------------------------------------------

    /** Logs [rewards]; a Pokémon among them is recorded as having come from [origin] (a Task Egg when not given). */
    private fun log(source: String, rewards: List<Reward>, d: AppData, seen: Boolean = false, origin: Origin? = null): AppData {
        val joined = rewards.filterIsInstance<Reward.Hatched>()
            .associate { it.pokemon.id to (origin ?: Origin(Origin.How.TASK_EGG, d.settings.region.open)) }
        return d.copy(rewardLog = (d.rewardLog + RewardEntry(nowMillis, source, rewards, seen)).takeLast(LOG_SIZE), origins = d.origins + joined)
    }

    /**
     * Notes what a module reward added to the Poké balance and the Bag, under the event's own id, so the database ledger can record the real event
     * (DATA_AND_MIGRATIONS.md section 7.3.0). [before] and [after] are the player's state around the change; a reward that added nothing leaves no note.
     */
    private fun noted(before: AppData, after: AppData, id: String, module: String, ref: String, kind: String, at: Long, extraPoke: Int = 0): AppData {
        val poke = (after.player.coins - before.player.coins + extraPoke).toLong()
        val items = (before.player.inventory.counts.keys + after.player.inventory.counts.keys)
            .associateWith { (after.player.inventory[it] - before.player.inventory[it]).toLong() }.filterValues { it != 0L }
        if (poke == 0L && items.isEmpty()) return after
        return after.copy(ledgerNotes = (after.ledgerNotes + LedgerNote(id, module.uppercase(), ref, kind, at, poke, items)).takeLast(LEDGER_NOTES_KEPT))
    }

    fun markRewardsSeen() = update { d -> d.copy(rewardLog = d.rewardLog.map { it.copy(seen = true) }) }

    private fun achieve(d: AppData, tier: Int, source: String, eventId: String, ref: String, onTime: Boolean = false, focusedOnIt: Boolean = false, picked: Boolean = false): AppData {
        val firstOfDay = streakDay !in d.activeDays
        val warmed = Counters.WARM.get(d.counters, streakDay)
        val event = AchievementEvent(
            tier = tier, region = d.settings.region.open, hourOfDay = now.hour, firstOfDay = firstOfDay, onTime = onTime,
            focusedOnIt = focusedOnIt, picked = picked, friendshipTasksToday = warmed, newPokemonId = newId(), nowMillis = nowMillis,
        )
        val outcome = RewardEngine.achievement(d.player, event, rng)
        val counters = Counters.WARM.set(d.counters, streakDay, warmed + 1)
        return noted(d, log(source, outcome.rewards, d.copy(player = outcome.state, activeDays = d.activeDays + streakDay, counters = counters)), eventId, "PLAN", ref, "task", nowMillis)
    }

    /** The Pokémon Care's screens show: Care's lead, or the Plan buddy until Care has one (it takes Care's friendship then). */
    val careBuddy: OwnedPokemon? get() = data.player.careLead

    /** The day work counts for in streaks: it rolls over at 4am, so a late night still counts for the day before. */
    val streakDay: LocalDate get() = Streaks.streakDay(now)

    /**
     * Pays a Care moment into the shared world, at most [CARE_DAILY_CAP] of each kind a day whatever the Care module asks
     * (ecosystem.md, section 5: each module's share is capped). Returns null when nothing was paid (not started, or capped).
     * The log says only "Care" and the kind, never what the routine was.
     */
    override fun care(event: CareEvent): List<Reward>? {
        if (!data.onboarded) return null
        val tally = Counters.care(event.kind)
        val paidToday = tally.get(data.counters, today)
        if (paidToday >= CARE_DAILY_CAP.getValue(event.kind)) return null
        val outcome = RewardEngine.care(data.player, event, rng)
        // Care's Egg meter: a full one hatches a Care Egg on top of the moment's own rewards.
        val meter = careEggMeter() + RewardEngine.CARE_EGG_POINTS.getValue(event.kind)
        val egg = if (meter >= RewardEngine.CARE_EGG_METER_SIZE) RewardEngine.careEgg(outcome.state, newId(), nowMillis, rng) else null
        val rewards = outcome.rewards + egg?.rewards.orEmpty()
        update { d ->
            val counters = tally.set(d.counters, today, paidToday + 1) + (Counters.CARE_EGG_METER to meter % RewardEngine.CARE_EGG_METER_SIZE)
            // CareEvent has no id of its own: the kind, the day and the count paid that day name it, and the same count never repeats (the tally above is what caps it).
            val kind = event.kind.name.lowercase()
            noted(d, log(CARE_SOURCES.getValue(event.kind), rewards, d.copy(player = (egg ?: outcome).state, counters = counters), origin = Origin(Origin.How.CARE_EGG, Region.CARE)), "care:$kind:${today.toEpochDays()}:${paidToday + 1}", "CARE", "$kind:${today.toEpochDays()}", kind, event.nowMillis)
        }
        return rewards
    }

    /**
     * Pays a HEAL moment into the shared world (Field Research, HEAL.md section 12): once per event id whatever HEAL does with its records, at
     * most [HEAL_DAILY_CAP] of each kind a day, and a full Field Research meter finds a Field Egg on top. The log says only "Heal" and the kind
     * of moment. Nothing the event could tell about the person's body or health exists to be read here.
     */
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

    /**
     * Pays a MINT moment into the shared world (Ledger Research, MINT.md section 16): once per event id whatever MINT does with its records, at most
     * [MINT_CAPS] of each kind in its period (a day for a reconciled account, a week for a review), and a full Ledger Research meter finds a Ledger Egg
     * on top. The log says only "Mint" and the kind of moment. Nothing about money, an account or a provider exists in the event to be read here.
     */
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
        update { d ->
            val counters = tally.set(d.counters, period, paidInPeriod + 1) + (Counters.MINT_EGG_METER to meter % RewardEngine.MINT_EGG_METER_SIZE)
            noted(d, log(MINT_SOURCES.getValue(event.kind), rewards, d.copy(player = (egg ?: outcome).state, counters = counters, mintPaid = (d.mintPaid + event.id).takeLast(MINT_PAID_KEPT)), origin = Origin(Origin.How.LEDGER_EGG, Region.MINT)), event.id, event.module, event.ref, event.kind.name.lowercase(), event.nowMillis)
        }
        return rewards
    }

    /**
     * Pays a HOME moment into the shared world (Habitat Discovery, HOME.md section 9): once per event id whatever HOME does with its records (so an undo
     * and a second completion of the same chore pay once), at most [HOME_CAPS] of each kind a day, and a full Habitat Discovery meter finds a Habitat
     * Egg on top. The log says only "Home" and the kind of moment. Nothing about a chore, an area, a supply or an asset exists in the event to be read here.
     */
    override fun home(event: HomeEvent): List<Reward>? {
        if (!data.onboarded || event.id in data.homePaid) return null
        val tally = Counters.home(event.kind)
        val paidToday = tally.get(data.counters, today)
        if (paidToday >= HOME_CAPS.getValue(event.kind)) return null
        val outcome = RewardEngine.home(data.player, event, rng)
        val meter = homeEggMeter() + RewardEngine.HOME_EGG_POINTS.getValue(event.kind)
        val egg = if (meter >= RewardEngine.HOME_EGG_METER_SIZE) RewardEngine.homeEgg(outcome.state, newId(), nowMillis, rng) else null
        val rewards = outcome.rewards + egg?.rewards.orEmpty()
        update { d ->
            val counters = tally.set(d.counters, today, paidToday + 1) + (Counters.HOME_EGG_METER to meter % RewardEngine.HOME_EGG_METER_SIZE)
            noted(d, log(HOME_SOURCES.getValue(event.kind), rewards, d.copy(player = (egg ?: outcome).state, counters = counters, homePaid = (d.homePaid + event.id).takeLast(HOME_PAID_KEPT)), origin = Origin(Origin.How.HABITAT_EGG, Region.HOUSEHOLD)), event.id, event.module, event.ref, event.kind.name.lowercase(), event.nowMillis)
        }
        return rewards
    }

    /**
     * Pays a LINK moment into the shared world (Postcard Trail, LINK.md section 9): once per event id whatever LINK does with its records (so a reopen and
     * a second completion of the same follow-up pay once), at most [LINK_CAPS] of each kind a day, and a full track finds a Postcard Egg on top. The log
     * says only "Link" and the kind of moment. Nothing about a person, a note, a date or a follow-up exists in the event to be read here.
     */
    override fun link(event: LinkEvent): List<Reward>? {
        // A follow-up converted into a PLAN task is PLAN's: it pays by Plan's rules, once, and LINK never pays for the same follow-up (LINK.md 2.4).
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

    /** Points towards the next Postcard Egg, out of [RewardEngine.LINK_EGG_METER_SIZE]. */
    fun linkEggMeter(): Int = data.counters[Counters.LINK_EGG_METER] ?: 0

    /** Makes [id], who must be in Link's party, its lead: the Pokémon that takes Postcard Trail's friendship and gives its perks. */
    fun setLinkLead(id: String): Boolean {
        val p = pokemon(id)
        if (!p.inParty || p.partyId != PartyIds.LINK) return false
        update { d -> d.copy(player = d.player.copy(linkLeadId = id)) }
        return true
    }

    /** Points towards the next Habitat Egg, out of [RewardEngine.HOME_EGG_METER_SIZE]. */
    fun homeEggMeter(): Int = data.counters[Counters.HOME_EGG_METER] ?: 0

    /** Makes [id], who must be in Home's party, its lead: the Pokémon that takes Habitat Discovery's friendship and gives its perks. */
    fun setHomeLead(id: String): Boolean {
        val p = pokemon(id)
        if (!p.inParty || p.partyId != PartyIds.HOME) return false
        update { d -> d.copy(player = d.player.copy(homeLeadId = id)) }
        return true
    }

    /** Points towards the next Ledger Egg, out of [RewardEngine.MINT_EGG_METER_SIZE]. */
    fun mintEggMeter(): Int = data.counters[Counters.MINT_EGG_METER] ?: 0

    /** Makes [id], who must be in Mint's party, its lead: the Pokémon that takes Ledger Research's friendship and gives its perks. */
    fun setMintLead(id: String): Boolean {
        val p = pokemon(id)
        if (!p.inParty || p.partyId != PartyIds.MINT) return false
        update { d -> d.copy(player = d.player.copy(mintLeadId = id)) }
        return true
    }

    /** Points towards the next Field Egg, out of [RewardEngine.HEAL_EGG_METER_SIZE]. */
    fun healEggMeter(): Int = data.counters[Counters.HEAL_EGG_METER] ?: 0

    /** Makes [id], who must be in Heal's party, its lead: the Pokémon that takes Field Research's friendship and gives its perks. */
    fun setHealLead(id: String): Boolean {
        val p = pokemon(id)
        if (!p.inParty || p.partyId != PartyIds.HEAL) return false
        update { d -> d.copy(player = d.player.copy(healLeadId = id)) }
        return true
    }

    /** Points towards the next Care Egg, out of [RewardEngine.CARE_EGG_METER_SIZE]. */
    fun careEggMeter(): Int = data.counters[Counters.CARE_EGG_METER] ?: 0

    /**
     * The shop: trades [Shop.COINS_PER_RARE_CANDY] coins for each Rare Candy. Returns how many were bought (fewer when the
     * coins or the bag's room run out, 0 for none).
     */
    fun buyRareCandies(count: Int): Int {
        val room = Inventory.MAX_CANDIES - data.player.inventory[Items.RARE_CANDY]
        val n = minOf(count, data.player.coins / Shop.COINS_PER_RARE_CANDY, room)
        if (n <= 0) return 0
        update { d ->
            val player = d.player.copy(coins = d.player.coins - n * Shop.COINS_PER_RARE_CANDY, inventory = d.player.inventory.add(Items.RARE_CANDY, n))
            noted(d, log("Shop", listOf(Reward.Candies(CandyType.RARE, n)), d.copy(player = player), seen = true), "app:shop:$nowMillis", "APP", "shop", "purchase", nowMillis)
        }
        return n
    }

    override fun visitorLuck(): Double =
        app.pokeplan.core.pokemon.CarePerks.total(data.player.careLead, data.player.partyOf(PartyIds.CARE))[app.pokeplan.core.pokemon.CareBucket.VISITOR_LUCK] ?: 0.0

    override fun berries(): Map<String, Int> =
        data.player.inventory.counts.filter { (id, n) -> n > 0 && Items.category(id) == ItemCategory.BERRY }

    override fun spendBerry(berry: String): Boolean {
        if (!data.onboarded || Items.category(berry) != ItemCategory.BERRY || !data.player.inventory.has(berry)) return false
        update { d -> d.copy(player = d.player.copy(inventory = d.player.inventory.remove(berry))) }
        return true
    }

    /**
     * Makes a friend of a Care visitor, once a day: the egg's dice come from the oldest egg ticket (the day's key is
     * `befriend:<epochDay>`, the ticket is spent with it) or, with none, from the device. Returns null when nothing was paid.
     */
    override fun befriend(event: BefriendEvent): List<Reward>? {
        if (!data.onboarded) return null
        val made = Counters.BEFRIEND.get(data.counters, today)
        if (made >= BEFRIEND_DAILY_CAP) return null
        val ticket = data.eggTickets.stock.firstOrNull()
        val outcome = RewardEngine.befriend(data.player, event, ticket?.let { Random(it.seed) } ?: rng)
        update { d ->
            val counters = Counters.BEFRIEND.set(d.counters, today, made + 1)
            val tickets = if (ticket == null) d.eggTickets
            else d.eggTickets.copy(stock = d.eggTickets.stock.drop(1), spent = d.eggTickets.spent + SpentTicket(ticket.id, nowMillis, event.newPokemonId))
            noted(d, log(CARE_FRIEND_SOURCE, outcome.rewards, d.copy(player = outcome.state, counters = counters, eggTickets = tickets),
                origin = Origin(Origin.How.BEFRIENDED, Region.CARE, visitor = visitorOrigin(event, outcome.rewards))), "care:befriend:${event.newPokemonId}", "CARE", event.visitorId.ifEmpty { event.newPokemonId }, "befriend", event.nowMillis)
        }
        return outcome.rewards
    }

    /** The origin note of what a visitor left: the visitor, the day and the traits an egg took from it (none when the visitor itself joined). */
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

    // --- tasks --------------------------------------------------------------------------------------------------------

    /** Adds a task. Everything but the title is optional. */
    fun addTask(
        title: String,
        rank: Rank = Rank.C,
        category: Category = Category.PERSONAL,
        planned: LocalDate? = null,
        due: LocalDateTime? = null,
        estimateMinutes: Int? = null,
        notes: String = "",
        listId: String? = null,
        /** The time of day to do it, only meaningful with a [planned] day (quick add: "tomorrow 5pm"). */
        plannedTime: kotlinx.datetime.LocalTime? = null,
        /** Marks it for "This evening" whatever the time (quick add: "tonight"). */
        evening: Boolean = false,
    ): Task {
        val task = Task(
            id = newId(), title = title.trim(), notes = notes, rank = rank, category = category, planned = planned, due = due,
            plannedTime = plannedTime?.takeIf { planned != null }, evening = evening && planned != null,
            estimateMinutes = estimateMinutes, createdAtMillis = nowMillis, listId = listId?.takeIf { id -> data.lists.any { it.id == id } },
        )
        update { it.copy(tasks = it.tasks + task) }
        return task
    }

    fun updateTask(task: Task) = update { d -> d.copy(tasks = d.tasks.map { if (it.id == task.id) task else it }) }

    // --- links to other modules (PRODUCT_BIBLE.md section 4) -------------------------------------------------------------------

    private val linkOwners = mutableMapOf<LinkKind, LinkOwner>()

    /** Registers the module that owns the completion of the tasks linked to its records (the app does this once for Home). */
    fun registerLinkOwner(owner: LinkOwner) { linkOwners[owner.kind] = owner; syncLinks() }

    /** The live task linked to [kind]'s record [ref] (never a deleted one), if any: a record has at most one. */
    fun linkedTask(kind: LinkKind, ref: String): Task? = data.tasks.firstOrNull { it.link?.kind == kind && it.link?.ref == ref && it.state != TaskState.DELETED }

    /**
     * Makes the responsibility of a HOME chore visible in PLAN, by the user's choice (HOME.md 2.1). The task is a projection: its words and day come from the
     * chore, completing it completes the chore in HOME (which pays once) and PLAN pays nothing. One task per chore: asking again gives the same task. Null when
     * the chore is gone or has nothing waiting.
     */
    fun linkChore(choreId: String): Task? {
        linkedTask(LinkKind.HOME_CHORE, choreId)?.let { return it }
        val owner = linkOwners[LinkKind.HOME_CHORE] ?: return null
        val link = TaskLink(LinkKind.HOME_CHORE, choreId)
        val open = owner.project(link) as? LinkState.Open ?: return null
        val task = Task(
            id = newId(), title = open.title, category = Category.HOME, planned = open.planned, createdAtMillis = nowMillis,
            link = link.copy(occurrence = open.occurrence),
        )
        update { it.copy(tasks = it.tasks + task) }
        return task
    }

    /**
     * The user converts a LINK follow-up into a PLAN task (LINK.md 2.4). From now on PLAN owns the task, its schedule, its completion and its reward, by PLAN's own
     * rules and paid once; the link only lets LINK show this task's status ([linkedTask]) and keeps LINK from paying for the same follow-up. Converting the same
     * follow-up again returns the same task, so it can never exist twice.
     */
    override fun followUpTask(followUpId: String): FollowUpTask? =
        linkedTask(LinkKind.LINK_FOLLOW_UP, followUpId)?.let { FollowUpTask(it.id, it.state == TaskState.DONE, it.completedAtMillis) }

    override fun planFollowUp(followUpId: String, title: String, planned: LocalDate?): FollowUpTask? =
        convertFollowUp(followUpId, title, planned)?.let { FollowUpTask(it.id, it.state == TaskState.DONE, it.completedAtMillis) }

    fun convertFollowUp(followUpId: String, title: String, planned: LocalDate? = null, due: LocalDateTime? = null): Task? {
        linkedTask(LinkKind.LINK_FOLLOW_UP, followUpId)?.let { return it }
        val clean = title.trim().ifEmpty { return null }
        val task = Task(
            id = newId(), title = clean, category = Category.SOCIAL, planned = planned, due = due, createdAtMillis = nowMillis,
            link = TaskLink(LinkKind.LINK_FOLLOW_UP, followUpId),
        )
        update { it.copy(tasks = it.tasks + task) }
        return task
    }

    /**
     * Ends a link by the user's choice. A HOME projection is removed (it can be restored like any deleted task; the chore is untouched, HOME.md 2.1). A converted
     * follow-up stays a normal PLAN task, now without the reference (what it paid stays paid, so it never pays twice).
     */
    fun unlinkTask(taskId: String) {
        val task = data.tasks.firstOrNull { it.id == taskId && it.link != null } ?: return
        if (task.ownedElsewhere) deleteTask(taskId)
        else update { d -> d.copy(tasks = d.tasks.map { if (it.id == taskId) it.copy(link = null) else it }) }
    }

    /**
     * Brings every task linked to a record another module owns in line with that record: words, day and state follow the owner, and nothing is paid or
     * recorded here. A repeating chore's task moves on to the next occurrence; a chore that is gone takes its projection with it (restorable, never the chore's data).
     * Call it when the owner's data changes and when the app opens; it writes nothing when nothing differs.
     */
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

    // --- lists --------------------------------------------------------------------------------------------------------

    /** The lists shown in the menu: not archived, in the order they were made. */
    val activeLists: List<TaskList> get() = data.lists.filter { !it.archived }

    /** The list named [name] (any case), for "#Groceries" in quick-add; null if there is none. */
    fun listNamed(name: String): TaskList? = activeLists.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    /** Makes a list. A blank name is not a list. */
    fun addList(name: String, color: Long = TaskList.COLORS.first(), icon: String = TaskList.ICONS.first()): TaskList? {
        val clean = name.trim().take(TaskList.MAX_NAME).ifEmpty { return null }
        val list = TaskList(newId(), clean, color, icon)
        update { it.copy(lists = it.lists + list) }
        return list
    }

    /** Renames, recolours, re-icons or archives a list. */
    fun updateList(list: TaskList) = update { d ->
        d.copy(lists = d.lists.map { if (it.id == list.id) list.copy(name = list.name.trim().take(TaskList.MAX_NAME).ifEmpty { it.name }) else it })
    }

    /** Deletes a list; its tasks go back to the Inbox, nothing is lost. */
    fun deleteList(id: String) = update { d ->
        d.copy(lists = d.lists.filter { it.id != id }, tasks = d.tasks.map { if (it.listId == id) it.copy(listId = null) else it })
    }

    /** Files a task in a list, or back in the Inbox with null. */
    fun moveToList(taskId: String, listId: String?) = update { d ->
        d.copy(tasks = d.tasks.map { if (it.id == taskId) it.copy(listId = listId?.takeIf { id -> d.lists.any { l -> l.id == id } }) else it })
    }

    /** Plans a task at [time] (null clears it). A task without a day gets its deadline's day, or today. */
    fun setPlannedTime(taskId: String, time: LocalTime?) = update { d ->
        d.copy(tasks = d.tasks.map { t ->
            if (t.id != taskId) t
            else if (time == null) t.copy(plannedTime = null)
            else t.copy(planned = t.planned ?: t.due?.date ?: today, plannedTime = time)
        })
    }

    /** Plans a task on [day] at [time], as the day timeline does when a task is put on an hour. */
    fun scheduleTask(taskId: String, day: LocalDate, time: LocalTime) = update { d ->
        d.copy(tasks = d.tasks.map { if (it.id == taskId && it.isOpen) it.copy(planned = day, plannedTime = time) else it })
    }

    /** Sets how long an open task is expected to take, as resizing its block on the timeline does (null clears it). */
    fun setEstimate(taskId: String, minutes: Int?) = update { d ->
        d.copy(tasks = d.tasks.map { if (it.id == taskId && it.isOpen) it.copy(estimateMinutes = minutes?.takeIf { m -> m > 0 }) else it })
    }

    // --- own calendar events (the rules are in [PlanCalendar]) ---------------------------------------------------------

    /** Puts an event on PokéPlan's own calendar, repeating when [repeat] is set. It never pays. */
    fun addEvent(
        title: String, day: LocalDate, start: LocalTime, minutes: Int = PlanEvent.DEFAULT_MINUTES, color: Long? = null,
        repeat: EventRepeat? = null,
    ): PlanEvent = PlanCalendar.timed(newId(), title, day, start, minutes, color, repeat).also { e -> update { it.copy(events = it.events + e) } }

    /** Puts an all-day event on PokéPlan's own calendar, from [first] through [last] ("Home" for a weekend, "WFH" for a day). */
    fun addAllDayEvent(title: String, first: LocalDate, last: LocalDate = first, color: Long? = null, repeat: EventRepeat? = null): PlanEvent =
        PlanCalendar.allDay(newId(), title, first, last, color, repeat).also { e -> update { it.copy(events = it.events + e) } }

    /** See [PlanCalendar.update]. Returns the id the same occurrence has afterwards. */
    fun updateEvent(
        id: String, title: String? = null, day: LocalDate? = null, start: LocalTime? = null, minutes: Int? = null,
        lastDay: LocalDate? = null, color: Long? = null, allDay: Boolean? = null,
    ): String = editEvents { PlanCalendar.update(it, id, title, day, start, minutes, lastDay, color, allDay) }

    /** See [PlanCalendar.move]. Returns the id of the moved event. */
    fun moveEvent(id: String, day: LocalDate, start: LocalTime): String = editEvents { PlanCalendar.move(it, id, day, start, ::newId) }

    /** Deletes an event; for an occurrence of a repeating event, every one of them. */
    fun deleteEvent(id: String) = update { d -> d.copy(events = PlanCalendar.delete(d.events, id)) }

    /** Deletes just this occurrence of a repeating event (any other event is deleted). */
    fun deleteOccurrence(id: String) = update { d -> d.copy(events = PlanCalendar.deleteOccurrence(d.events, id)) }

    /** See [PlanCalendar.repeat]. Returns the id the event shown as [id] has afterwards. */
    fun repeatEvent(id: String, repeat: EventRepeat?): String = editEvents { PlanCalendar.repeat(it, id, repeat, ::newId) }

    /** Sets the last day a repeating event can happen on (null: it never ends). */
    fun endEventOn(id: String, until: LocalDate?) = update { d -> d.copy(events = PlanCalendar.endOn(d.events, id, until)) }

    private fun editEvents(edit: (List<PlanEvent>) -> PlanCalendar.Edit): String {
        val result = edit(data.events)
        if (result.events != data.events) update { it.copy(events = result.events) }
        return result.shownId
    }

    /** Sets a task's reminders (at most [Reminders.MAX_PER_TASK]). */
    fun setReminders(taskId: String, reminders: List<ReminderOffset>) =
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == taskId) it.copy(reminders = reminders.distinct().take(Reminders.MAX_PER_TASK)) else it }) }

    /** Every reminder still to come, earliest first; none when reminders are switched off. */
    fun upcomingReminders(): List<DueReminder> = data.upcomingReminders(nowMillis, zone)

    fun deleteTask(id: String) = update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) TaskRules.delete(it, nowMillis) else it }) }

    fun restoreTask(id: String) = update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) TaskRules.restore(it, nowMillis) else it }) }

    fun wontDo(id: String) {
        val task = data.tasks.firstOrNull { it.id == id } ?: return
        val link = task.link?.takeIf { task.ownedElsewhere }
        // For a linked chore, "won't do" is the owner's skip; syncLinks then shows it.
        if (link != null) { if (task.isOpen) linkOwners[link.kind]?.skip(link); return }
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) TaskRules.wontDo(it, nowMillis) else it }) }
    }

    fun reopen(id: String) {
        val task = data.tasks.firstOrNull { it.id == id } ?: return
        // A task whose record another module owns is reopened by taking back the owner's completion; syncLinks then shows it open again.
        val link = task.link?.takeIf { task.ownedElsewhere }
        if (link != null) {
            if (task.state == TaskState.DONE || task.state == TaskState.WONT_DO) linkOwners[link.kind]?.undo(link)
            return
        }
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) TaskRules.reopen(it) else it }) }
    }

    /**
     * Ticks a task off. The reward is held for the undo window; call [tick] (the UI does, every second) to settle it.
     *
     * A task linked to a record another module owns (a HOME chore) is not completed by PLAN: the owner does its own completion, once, and pays its own
     * reward; this task then only shows it done and pays nothing (`PRODUCT_BIBLE.md` section 4). If the owner is not available or refuses, nothing is recorded.
     */
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
        // The owner's own save may already have shown the task done through [syncLinks]; the task is read again either way.
        val done = (data.tasks.firstOrNull { it.id == task.id } ?: task).copy(state = TaskState.DONE, completedAtMillis = nowMillis)
        val pending = PendingCompletion(task, done, false, task.rank.tier, false, false, nowMillis + TaskRules.UNDO_WINDOW_MILLIS)
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == task.id) done else it }, pending = d.pending + pending) }
        return pending
    }

    /** Takes back a completion still inside its undo window. */
    fun undoCompletion(taskId: String): Boolean {
        val p = data.pending.firstOrNull { it.original.id == taskId } ?: return false
        if (nowMillis >= p.finalAtMillis) return false
        p.original.link?.takeIf { p.original.ownedElsewhere }?.let { link -> linkOwners[link.kind]?.undo(link) }
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == taskId) p.original else it }, pending = d.pending - p) }
        return true
    }

    /** Ticks a subtask; the first few that are old enough pay one Rare Candy each. */
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

    /** Adds a step to a task, up to [MAX_SUBTASKS]. A blank title is not a step. */
    fun addSubtask(taskId: String, title: String) {
        val clean = title.trim().ifEmpty { return }
        update { d ->
            d.copy(tasks = d.tasks.map { t ->
                if (t.id == taskId && t.subtasks.size < MAX_SUBTASKS) t.copy(subtasks = t.subtasks + Subtask(newId(), clean, createdAtMillis = nowMillis)) else t
            })
        }
    }

    /** A repeating task. Its next instances are created straight away and topped up by [refresh]. */
    fun addRecurring(template: Task, recurrence: Recurrence) {
        val seriesId = newId()
        val series = TaskSeries(template.copy(id = seriesId, seriesId = seriesId, createdAtMillis = nowMillis), recurrence)
        update { it.copy(series = it.series + series) }
        refresh()
    }

    /** The series [task] belongs to (an instance of it, or the plain task it was made from), or null if it does not repeat. */
    fun seriesOf(task: Task): TaskSeries? =
        data.series.firstOrNull { it.template.seriesId == task.seriesId && task.seriesId != null || it.firstTaskId == task.id }

    /**
     * Makes an open task repeat on [repeat], starting from its planned day (today when it has none). The task itself stays
     * the one of its own day (it pays as a one-off task, with the minimum age); the next ones are created ahead. On a task
     * that already repeats, this changes how: the later instances you have not touched are made again on the new schedule.
     */
    fun makeRepeating(taskId: String, repeat: Repeat): Boolean {
        val task = data.tasks.firstOrNull { it.id == taskId && it.isOpen } ?: return false
        // A linked task is one record's projection, not a task of its own to repeat (the chore's own schedule repeats it).
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
            // Its own day is skipped: the task itself is that day's.
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

    /** Stops a task repeating. The task itself stays; instances you have not touched and that are still ahead are removed. */
    fun stopRepeating(taskId: String) {
        val task = data.tasks.firstOrNull { it.id == taskId } ?: return
        val id = seriesOf(task)?.template?.seriesId ?: return
        update { d -> d.copy(series = d.series.filterNot { it.template.seriesId == id }, tasks = d.tasks.filterNot { untouchedLaterInstance(it, id) }) }
    }

    /** An instance of series [seriesId] after today that is still open and has had nothing done to it. */
    private fun untouchedLaterInstance(t: Task, seriesId: String): Boolean =
        t.seriesId == seriesId && t.isOpen && (t.planned ?: today) > today && t.focusedMinutes == 0 && t.subtasks.none { it.done }

    // --- the clock ----------------------------------------------------------------------------------------------------

    /** Settles finished undo windows and the focus timer, and creates upcoming recurring tasks. Cheap to call often. */
    fun tick() {
        settlePending()
        tickFocus()
    }

    /** Creates recurring instances for the next days. Call when the app opens and when the date changes. */
    fun refresh() {
        val created = data.series.flatMap { s -> s.recurrence?.let { RecurringTasks.instancesToCreate(s.template, it, data.tasks, today, nowMillis) }.orEmpty() }
        if (created.isNotEmpty()) update { it.copy(tasks = it.tasks + created) }
        syncLinks()
    }

    private fun settlePending() {
        val due = data.pending.filter { nowMillis >= it.finalAtMillis }
        if (due.isEmpty()) return
        update { d ->
            var next = d.copy(pending = d.pending - due.toSet())
            for (p in due) {
                val picked = p.done.id == next.pickedTaskId
                if (p.pays) next = achieve(next, p.tier, "Task: ${p.done.title}", "plan:task:${p.done.id}:complete", p.done.id, p.onTime, p.focusedOnIt, picked)
                if (picked) next = next.copy(pickedTaskId = null)
            }
            next
        }
    }

    // --- frequency targets (recurring tasks that repeat N times in a period; there is no Habit record any more) ------------

    /** The recurring tasks with a frequency target. */
    fun frequencyTasks(): List<TaskSeries> = data.series.filter { it.isFrequency }

    fun addFrequencyTask(title: String, target: FrequencyTarget = HabitTarget.Daily, rank: Rank = Rank.C, category: Category = Category.HEALTH): TaskSeries {
        val id = newId()
        val series = TaskSeries(
            Task(id = id, seriesId = id, title = title.trim(), rank = rank, category = category, createdAtMillis = nowMillis),
            target = target, createdOn = today,
        )
        update { it.copy(series = it.series + series) }
        return series
    }

    /** Marks a frequency task done for [date]; the first completion of the day pays like a task, and never twice for the same day. */
    fun completeFrequencyTask(id: String, date: LocalDate = today) {
        val series = data.series.first { it.template.id == id && it.isFrequency }
        val habit = checkNotNull(series.asHabit())
        val pays = habit.paysOn(date) && date == today
        update { d ->
            val next = d.copy(series = d.series.map { if (it.template.id == id) it.copy(completions = it.completions + date, paidOn = if (pays) date else it.paidOn) else it })
            if (pays) achieve(next, series.template.rank.tier, "Recurring: ${series.template.title}", "plan:series:$id:${date.toEpochDays()}", id) else next
        }
    }

    fun undoFrequencyTask(id: String, date: LocalDate = today) =
        update { d -> d.copy(series = d.series.map { if (it.template.id == id && it.isFrequency) it.copy(completions = it.completions - date) else it }) }

    /** How far [series] is in its current period: "2 / 3 this week". */
    fun frequencyProgress(series: TaskSeries): FrequencyProgress? = series.target?.let { Frequency.progress(it, series.completions, today, firstDayOfWeek) }

    /** The streak of a frequency task, forgiving and secondary (PLAN.md section 5.4). */
    fun frequencyStreak(series: TaskSeries): Int = series.asHabit()?.streak(today, data.restDays, perks.extraForgivenDays, firstDayOfWeek) ?: 0

    // --- goals --------------------------------------------------------------------------------------------------------

    fun addGoal(title: String, category: Category = Category.PERSONAL, targetDate: LocalDate? = null): Goal {
        val goal = Goal(newId(), title.trim(), category, targetDate, createdAtMillis = nowMillis)
        update { it.copy(goals = it.goals + goal) }
        return goal
    }

    /** Adds a milestone to a goal. A blank title is not a milestone. */
    fun addMilestone(goalId: String, title: String) {
        val clean = title.trim().ifEmpty { return }
        update { d -> d.copy(goals = d.goals.map { g -> if (g.id == goalId) GoalRules.addMilestone(g, Milestone(newId(), clean, nowMillis)) else g }) }
    }

    /** Ticks a milestone. It pays a Super Candy if it is old enough (and fewer than 3 paid today); finishing the goal gives a bonus item. */
    fun completeMilestone(goalId: String, milestoneId: String): MilestoneResult {
        val goal = data.goals.first { it.id == goalId }
        val paidToday = Counters.MILESTONES.get(data.counters, today)
        val result = GoalRules.completeMilestone(goal, milestoneId, nowMillis, paidToday)
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

    // --- weekly review and rest days ----------------------------------------------------------------------------------

    val weekStart: LocalDate
        get() = Streaks.weekStartOf(today, firstDayOfWeek)

    fun reviewSummary(): Reviews.Summary = Reviews.summary(weekStart, data.tasks, data.focusHistory, data.series.mapNotNull { it.asHabit() }, zone)

    fun canReview(): Boolean = Reviews.canComplete(weekStart, today, data.reviews)

    /** Finishes this week's review: optionally moves unfinished tasks to [carryTo], and pays a berry. */
    fun completeReview(note: String?, carryTo: LocalDate? = null) {
        check(canReview()) { "this week was already reviewed" }
        val carried = if (carryTo != null) reviewSummary().unfinished.map { Reviews.carryForward(it, carryTo) } else emptyList()
        val review = Reviews.complete(weekStart, nowMillis, note, carried, data.reviews)
        val berry = Items.randomBerry(rng)
        update { d ->
            val byId = carried.associateBy { it.id }
            val next = d.copy(
                tasks = d.tasks.map { byId[it.id] ?: it }, reviews = d.reviews + review,
                player = d.player.copy(inventory = d.player.inventory.add(berry, Reviews.REWARD_BERRIES)),
            )
            noted(d, log("Weekly review", listOf(Reward.ItemDrop(berry, Reviews.REWARD_BERRIES)), next), "plan:review:${weekStart.toEpochDays()}", "PLAN", "week:${weekStart.toEpochDays()}", "review", nowMillis)
        }
    }

    // --- nudges: Pick for me, mini-quests, welcome back ---------------------------------------------------------------

    /**
     * "Pick for me": chooses one of today's open tasks at random (another one than the current pick when there is a choice)
     * and remembers it, so finishing it pays [RewardEngine.PICKED_BONUS] on its candies. Returns null when nothing is open.
     */
    fun pickForMe(): Task? {
        val open = openTodayTasks()
        val pick = PickForMe.choose(open.filter { it.id != data.pickedTaskId }.ifEmpty { open }, rng)?.task ?: return null
        update { it.copy(pickedTaskId = pick.id) }
        return pick
    }

    /** Today's three mini-quests (the same for everyone on a date, until rerolled). The day turns over at 4am, like streaks. */
    fun miniQuests(): List<MiniQuest> = MiniQuests.forDay(streakDay, rerolled = Counters.MINI_QUEST_REROLL.isSet(data.counters, streakDay))

    fun miniQuestDone(id: String): Boolean = Counters.MINI_QUEST.isSet(data.counters, streakDay, id)

    /** One reroll a day, before any of the day's quests is done. */
    fun canRerollMiniQuests(): Boolean = !Counters.MINI_QUEST_REROLL.isSet(data.counters, streakDay) && miniQuests().none { miniQuestDone(it.id) }

    fun rerollMiniQuests() {
        if (!canRerollMiniQuests()) return
        update { d -> d.copy(counters = Counters.MINI_QUEST_REROLL.set(d.counters, streakDay, 1)) }
    }

    /**
     * Ticks off one of today's mini-quests (honour system): a berry and [MiniQuests.RARE_CANDIES] Rare Candies, and
     * [MiniQuests.ALL_THREE_BONUS_BERRIES] more berries and [MiniQuests.ALL_THREE_BONUS_CANDIES] more candies for the third. No streak and no active day: they are small kindnesses, not work. Returns false if it is not today's or done.
     * The reward is logged as seen: the quest's own card shows it, so the reward box at the top of the page does not repeat it.
     */
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

    /** What today's mini-quests have paid so far, oldest first: the card shows it beside the quests. */
    fun miniQuestRewards(): List<Reward> {
        val since = LocalDateTime(streakDay, kotlinx.datetime.LocalTime(Streaks.ROLLOVER_HOUR, 0)).toEpochMillis(zone)
        return data.rewardLog.filter { it.source == MINI_QUEST_SOURCE && it.atMillis >= since }.flatMap { it.rewards }
    }

    /** The oldest open task: what "open the task you've been avoiding" starts 5 minutes of focus on. */
    fun oldestOpenTask(): Task? = data.tasks.filter { it.isOpen && it.seriesId == null }.minByOrNull { it.createdAtMillis }

    private val lastActive: LocalDate? get() = data.activeDays.maxOrNull()

    /** True after [Streaks.WELCOME_BACK_DAYS] or more days away, until the gift is taken (once per absence). */
    fun welcomeBackDue(): Boolean {
        val last = lastActive ?: return false
        return Streaks.welcomeBack(last, streakDay) && !Counters.WELCOME.isSet(data.counters, last)
    }

    /** Takes the welcome-back gift: [WELCOME_BACK_RARE_CANDIES] Rare Candies and [WELCOME_BACK_BERRIES] berries, no scolding. */
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

    /** Marks or unmarks a rest day; a rest day does not break the streak. Returns false if too many rest days are set. */
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

    // --- quests -------------------------------------------------------------------------------------------------------

    /** Quests accepted and not yet claimed. */
    val activeQuests: List<Quest>
        get() = data.quests.filterValues { it.completedAtMillis == null }.keys.mapNotNull(Quests::get)

    /** Quests that are unlocked and not taken yet. */
    val availableQuests: List<Quest>
        get() = Quests.all.filter { it.id !in data.quests && Quests.lockedReason(it, data) == null }

    /** Progress on each of a quest's requirements: (requirement, current count). */
    fun questProgress(quest: Quest): List<Pair<Requirement, Int>> {
        val state = data.quests[quest.id] ?: return quest.requirements.map { it to 0 }
        return quest.requirements.map { it to Quests.current(it, state, data, zone, streak.current) }
    }

    fun questReady(quest: Quest): Boolean {
        val state = data.quests[quest.id] ?: return false
        return state.completedAtMillis == null && Quests.isComplete(quest, state, data, zone, streak.current)
    }

    /** Takes a quest on. Returns false if it is locked, already taken, or [Quests.MAX_ACTIVE] are in progress. */
    fun acceptQuest(id: String): Boolean {
        val quest = Quests.get(id) ?: return false
        if (id in data.quests || Quests.lockedReason(quest, data) != null || activeQuests.size >= Quests.MAX_ACTIVE) return false
        update { it.copy(quests = it.quests + (id to QuestState(nowMillis))) }
        return true
    }

    /** Claims the reward of a finished quest: the Pokémon joins the collection. Returns it, or null if the quest is not finished. */
    fun claimQuest(id: String): OwnedPokemon? {
        val quest = Quests.get(id) ?: return null
        if (!questReady(quest)) return null
        val reward = grantPokemon(quest.speciesId, "Quest: ${quest.title}")
        update { d -> d.copy(quests = d.quests + (id to d.quests.getValue(id).copy(completedAtMillis = nowMillis))) }
        return reward
    }

    // --- growth -------------------------------------------------------------------------------------------------------

    /** What the last [days] days went into, by category. */
    fun wheel(days: Int = 7): Insights.Wheel =
        Insights.balanceWheel(data.tasks, data.focusHistory, today.minus(DatePeriod(days = days - 1)), today, zone)

    /** Focus minutes and finished tasks for each of the last [weeks] weeks, oldest first. */
    fun trend(weeks: Int = 6): List<Insights.WeekPoint> = Insights.weeklyTrend(
        data.tasks, data.focusHistory, weeks, today, zone, firstDayOfWeek,
    )

    // --- streak -------------------------------------------------------------------------------------------------------

    val streak: Streaks.Result get() = data.streakAt(now)

    val firstDayOfWeek: DayOfWeek get() = data.settings.weekStart

    /** See [AppData.perks]. */
    val perks: PerkBonuses get() = data.perks

    /** Where [perks] come from, part by part, for the perks panel. */
    val perkSources: List<PerkSource> get() = data.player.let { Perks.sources(it.planLead, it.party, it.guardianPokemon) }

    // --- focus --------------------------------------------------------------------------------------------------------

    /** Starts a strict focus session. */
    fun startFocus(minutes: Int, deepFocus: Boolean, taskId: String? = null, lure: PokemonType? = null): FocusSession {
        check(data.focus == null) { "a session is already running" }
        val session = FocusRules.start(
            id = newId(), durationMinutes = minutes, nowMillis = nowMillis, deepFocus = deepFocus, region = data.settings.region.open,
            leadIdAtStart = data.player.planLead?.id, taskId = taskId, lure = lure, emergencyPausesUsedToday = emergencyPausesToday(),
        )
        // The oldest egg ticket fixes the egg's dice now, like the perks; without one the egg rolls on the device.
        update { d ->
            val t = d.eggTickets
            d.copy(focus = session, eggTickets = t.copy(stock = t.stock.drop(1), inSession = t.stock.firstOrNull()))
        }
        return session
    }

    private fun emergencyPausesToday(): Int = if (data.emergencyPausesDay == today) data.emergencyPausesUsed else 0

    /** Passes something that happened (the app was left, a call, a pause) to the running session. */
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
                // The perks that count are the ones of Plan's lead when the session started.
                val player = data.player
                val ticket = data.eggTickets.inSession
                val outcome = RewardEngine.effort(player.atStartOf(session), event, ticket?.let { Random(it.seed) } ?: rng)
                update { d ->
                    noted(d, log("Focus: $minutes min", outcome.rewards, d.copy(
                        eggTickets = d.eggTickets.spend(ticket, session.startedAtMillis, event.newPokemonId),
                        player = outcome.state.copy(planLeadId = d.player.planLeadId), focus = null, activeDays = d.activeDays + streakDay, lastFocusEnd = FocusEnd(nowMillis, true),
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
                        eggTickets = d.eggTickets.spend(d.eggTickets.inSession, session.startedAtMillis, null),
                        focus = null, lastFocusEnd = FocusEnd(nowMillis, false, phase.reason),
                        focusHistory = d.focusHistory + session.record(minutes, completed = false),
                    )
                }
                FocusResult.Failed(phase.reason)
            }
            else -> null
        }
    }

    /**
     * Magic Guard: once a week, a failed session of at least [Egg.MIN_MINUTES] focused minutes still hatches its egg, as a
     * session of the minutes focused (never a Deep Focus one). The session still counts as failed in the history and for
     * the streak. Returns null when the buddy at the start had no Magic Guard, this week's is used, or too little was done.
     */
    private fun magicGuard(session: FocusSession, reason: FailReason, minutes: Int): FocusResult? {
        val player = data.player.atStartOf(session)
        if (minutes < Egg.MIN_MINUTES || Counters.MAGIC_GUARD.isSet(data.counters, weekStart)) return null
        if (!Perks.total(player.planLead, player.party, guardians = player.guardianPokemon).keepsFailedEgg) return null
        val ticket = data.eggTickets.inSession
        val event = EffortEvent(minutes, false, session.region.open, session.lure, epochDay, newId(), nowMillis)
        val outcome = RewardEngine.effort(player, event, ticket?.let { Random(it.seed) } ?: rng)
        update { d ->
            noted(d, log("Magic Guard: $minutes min", outcome.rewards, d.copy(
                eggTickets = d.eggTickets.spend(ticket, session.startedAtMillis, event.newPokemonId),
                player = outcome.state.copy(planLeadId = d.player.planLeadId), focus = null,
                lastFocusEnd = FocusEnd(nowMillis, true, reason, guarded = true),
                focusHistory = d.focusHistory + session.record(minutes, completed = false),
                counters = Counters.MAGIC_GUARD.set(d.counters, weekStart, 1),
            ), origin = Origin(Origin.How.FOCUS_EGG, event.region)), "plan:focus:${event.newPokemonId}", "PLAN", event.newPokemonId, "magic-guard", nowMillis)
        }
        return FocusResult.Hatched(outcome.rewards)
    }

    /** The perks that count for a session are the ones of Plan's lead when it started, if that Pokémon is still in Plan's party. */
    private fun app.pokeplan.core.reward.PlayerState.atStartOf(session: FocusSession) =
        if (session.leadIdAtStart != null && party.any { it.id == session.leadIdAtStart }) copy(planLeadId = session.leadIdAtStart) else this

    private fun FocusSession.record(minutes: Int, completed: Boolean) = FocusRecord(startedAtMillis, minutes, completed, taskId, deepFocus)

    /** A failed session spends its ticket too, so failing never rerolls an egg. */
    private fun EggTickets.spend(ticket: EggTicket?, startedAtMillis: Long, pokemonId: String?): EggTickets =
        if (ticket == null) copy(inSession = null) else copy(inSession = null, spent = spent + SpentTicket(ticket.id, startedAtMillis, pokemonId))

    fun dismissFocusEnd() = update { it.copy(lastFocusEnd = null) }

    // --- Pokémon ------------------------------------------------------------------------------------------------------

    private fun pokemon(id: String): OwnedPokemon = data.player.pokemon.first { it.id == id }

    private fun replace(p: OwnedPokemon) =
        update { d -> d.copy(player = d.player.copy(pokemon = d.player.pokemon.map { if (it.id == p.id) p else it })) }

    /** Where [id] is now: the buddy, a party, a Guardian slot, a World team, a Pokégear helper or the PC (one place only, [Places]). */
    fun placeOf(id: String): Place = Places.of(data, id)

    /** Takes [id] out of the place it is in and runs [then] on the result, in one save; false when it is on an expedition under way. */
    private fun moveTo(id: String, then: (AppData) -> AppData): Boolean {
        if (!Places.movable(data, id)) return false
        update { d -> then(Places.vacate(d, id)).let { r -> r.copy(player = Places.leads(r.player)) } }
        return true
    }

    /**
     * Makes a Pokémon the buddy. Guardians (legendaries and mythicals) cannot be buddies. A Pokémon in a party, on a team or helping a tool
     * is taken from there (one place only) and the previous buddy goes to the PC. Returns false if refused.
     */
    fun setBuddy(id: String): Boolean {
        if (Guardians.canBe(pokemon(id))) return false
        return moveTo(id) { d -> d.copy(player = d.player.copy(buddyId = id)) }
    }

    fun rename(id: String, nickname: String) = replace(pokemon(id).withNickname(nickname))

    /**
     * Puts a Pokémon in or out of a module's party (up to six, in one party only: joining takes it from the buddy, a team, a helper job or
     * another party). Returns false if the party is full, it is a Guardian species, or it is on an expedition under way, or if it would leave
     * Plan's party empty by going out (use a place to move it instead). A lead that leaves passes the lead on to the party's first member.
     */
    fun setInParty(id: String, inParty: Boolean, partyId: String = PartyIds.PLAN): Boolean {
        val p = pokemon(id)
        if (inParty && Guardians.canBe(p)) return false
        val staying = inParty && p.inParty && p.partyId == partyId
        if (inParty && !staying && data.player.partyOf(partyId).size >= OwnedPokemon.PARTY_SIZE) return false
        val leavingPlan = p.inParty && p.partyId == PartyIds.PLAN && !(inParty && partyId == PartyIds.PLAN)
        if (leavingPlan && data.player.party.size <= 1) return false
        if (!inParty) return p.inParty && moveTo(id) { it }
        return moveTo(id) { d -> d.copy(player = d.player.copy(pokemon = d.player.pokemon.map { if (it.id == id) it.copy(inParty = true, partyId = partyId) else it })) }
    }

    /** Makes [id], who must be in Plan's party, its lead: the Pokémon that takes Plan's friendship and gives its perks. */
    fun setPlanLead(id: String): Boolean {
        val p = pokemon(id)
        if (!p.inParty || p.partyId != PartyIds.PLAN) return false
        update { d -> d.copy(player = d.player.copy(planLeadId = id)) }
        return true
    }

    /** Makes [id], who must be in Care's party, its lead: the Pokémon that takes Care's friendship and gives its perks. */
    fun setCareLead(id: String): Boolean {
        val p = pokemon(id)
        if (!p.inParty || p.partyId != PartyIds.CARE) return false
        update { d -> d.copy(player = d.player.copy(careLeadId = id)) }
        return true
    }

    // --- Guardians ----------------------------------------------------------------------------------------------------

    /** How many Guardians the player can slot at their rank. */
    val guardianSlots: Int get() = Guardians.slots(data.player.rank)

    /** Owned legendaries and mythicals, slotted or not. */
    val guardianCandidates: List<OwnedPokemon> get() = data.player.pokemon.filter(Guardians::canBe)

    /** Puts a Guardian in a free slot. Returns false if it is not a Guardian, is already slotted, is on an expedition, or every slot is taken. Slotting takes it off its World team or helper job. */
    fun slotGuardian(id: String): Boolean {
        val p = pokemon(id)
        if (!Guardians.canBe(p) || id in data.player.guardians || data.player.guardians.size >= guardianSlots) return false
        // A Guardian on a World team is taken off it (one place only) unless its team is out on an expedition.
        return moveTo(id) { d -> d.copy(player = d.player.copy(guardians = d.player.guardians + id)) }
    }

    fun unslotGuardian(id: String) = update { d -> d.copy(player = d.player.copy(guardians = d.player.guardians - id)) }

    /** Gives the player a Pokémon outright (a quest reward). It goes to the Guardian collection if it is a legendary or mythical. */
    fun grantPokemon(speciesId: Int, source: String, how: Origin.How = Origin.How.QUEST, form: String? = null): OwnedPokemon {
        val option = HatchOption(speciesId, form ?: Dex.defaultForm(speciesId).identifier)
        val p = Hatching.create(newId(), option, OwnedPokemon.PARTY_SIZE, nowMillis, 60, rng).copy(inParty = false)
        update { d ->
            log(source, listOf(Reward.Hatched(p, fromTask = false)),
                d.copy(player = d.player.copy(pokemon = d.player.pokemon + p, caught = d.player.caught + p.speciesId)), origin = Origin(how))
        }
        return p
    }

    /** Feeds up to [count] Rare Candies. Returns how many were used. */
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
        val ctx = EvolutionContext(now.hour, data.player.inventory.counts, data.completedTrials)
        return EvolutionRules.options(pokemon(id), ctx)
    }

    /** Evolves into one of [evolutionOptions]; uses up what it needs. */
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
            // The "last party member" rule guards Plan's party only; other modules' parties may be emptied.
            Transfer.block(p, id == data.player.buddyId || id == data.player.planLead?.id || id == data.player.careLead?.id || id == data.player.healLead?.id || id == data.player.mintLead?.id || id == data.player.homeLead?.id || id == data.player.linkLead?.id, if (p.partyId == PartyIds.PLAN) data.player.party.size else OwnedPokemon.PARTY_SIZE)
        }

    /**
     * Whether [feature] has appeared yet at the player's trainer rank (the gradual reveal). The Guardians screen also opens as soon as the player owns one
     * Guardian (a legendary or mythical, from the World opening or a quest) whatever the rank, so it is never in a place the player cannot see: the Pokémon
     * lists it there (its slot still waits for the rank) and the PC does not hide it somewhere unreachable.
     */
    fun sees(feature: Feature): Boolean =
        TrainerRank.isVisible(feature, data.player.trainerPoints) || (feature == Feature.GUARDIANS && guardianCandidates.isNotEmpty())

    /** Features opened by ranks the player reached but has not seen the "Rank up" card for, oldest first. */
    fun newlyOpened(): List<Feature> = ((data.seenRank + 1)..data.player.rank).flatMap { Feature.at(it) }

    fun markRankSeen() = update { it.copy(seenRank = maxOf(it.seenRank, it.player.rank)) }

    /** Remembers that the player has seen what is new in [version]. */
    fun markVersionSeen(version: String) = update { it.copy(lastSeenVersion = version) }

    /** Marks species as seen in the Pokédex (a friend's buddy, a quest's Pokémon). Saves nothing when none are new. */
    fun markSeen(speciesIds: Collection<Int>) {
        if (data.seenSpecies.containsAll(speciesIds)) return
        update { it.copy(seenSpecies = it.seenSpecies + speciesIds) }
    }

    fun isFavorite(id: String): Boolean = id in data.favorites

    /** Stars or unstars a Pokémon; a starred one cannot be transferred. */
    // ----- World: the dungeon game (docs/world.md). Its rules are in world-core; this keeps its save and moves what it finds into the shared collection and Bag -----

    /** What the teams brought back since the player last dismissed the report (kept in memory only: the reward log is the record), or null when nothing came. */
    private var worldSummary: ReportSummary? = null

    fun worldReport(): ReportSummary? = worldSummary?.takeIf { !it.isEmpty }

    /** The player has read the report: it starts again from nothing. */
    fun worldReportDismiss() { worldSummary = null }

    /** Settles every World team to now (floors climbed, finds, items) and returns what happened, or null when nothing did. */
    fun worldSettle(): WorldReport? {
        // A Pokémon transferred away leaves its team (an empty team comes home), so no slot is held by someone who is gone.
        val kept = data.world.pruned(data.player.pokemon.map { it.id }.toSet())
        if (kept !== data.world) update { it.copy(world = kept) }
        val before = data.world
        val report = WorldSim.settle(before, data.player.pokemon, nowMillis)
        if (report.isEmpty) {
            // A clock set back moves a team's settle point to now without any floor (WORLD.md 3.1): keep that, or the team waits for the clock to catch up.
            if (report.data.teams != before.teams) update { it.copy(world = it.world.copy(teams = report.data.teams)) }
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

    /** The story's end: the Guardian, Primal Dialga, joins the Guardian collection once (docs/world.md, section 15). The mastery bonus is [app.pokeplan.world.Story.GUARDIAN_BONUS]. */
    private fun worldGuardian() {
        val w = data.world
        if (!w.guardianDone || w.guardianGranted) return
        update { it.copy(world = it.world.copy(guardianGranted = true)) }
        grantPokemon(app.pokeplan.world.Story.GUARDIAN_SPECIES, WORLD_SOURCE, form = app.pokeplan.world.Story.GUARDIAN_FORM)
    }

    /**
     * The legendary quests of the layers (docs/ecosystem.md, "World regions"): each Pokémon whose quest is met (its dungeon cleared enough
     * times, and for the end of a layer's story its Guardian faced) joins the Guardian collection once. They are not eggs; the reward line
     * is in the log.
     */
    private fun worldLegends() {
        for (quest in app.pokeplan.world.Legends.ready(data.world)) {
            update { it.copy(world = it.world.copy(legends = it.world.legends + quest.id)) }
            // Every legendary also has a quest in the Plan side's quest list (Quests): a Pokémon already owned from there is not given twice,
            // and the quest still counts as done in World, so its boost for the layer applies either way.
            // Dialga is the exception: Primal Dialga (the home Guardian) is a form of its own, so owning it does not stand in for Sinnoh's Dialga.
            val own = app.pokeplan.core.dex.Dex.defaultForm(quest.speciesId).identifier
            if (data.player.pokemon.none { it.speciesId == quest.speciesId && it.form.identifier == own }) grantPokemon(quest.speciesId, WORLD_SOURCE)
        }
    }

    /**
     * Applies a World change that pays (floors, a job, research, a treasure box) as ONE write: the change, the Poké it earned
     * (`WorldData.pokeEarned`, folded into the balance by [update]), the items World staged moved into the shared Bag, the line in the reward log
     * and a [LedgerNote] under [eventId], so the database ledger records this event by its own stable id instead of a net change
     * (DATA_AND_MIGRATIONS.md section 7.3.0; WORLD.md 3.1 and 6). Entries of the same [source] within ten minutes share a log line, so a team that
     * climbs while the screen is open does not flood the log.
     */
    private fun worldPay(source: String, eventId: String, ref: String, kind: String, change: (AppData) -> AppData) {
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
                } else (moved.rewardLog + RewardEntry(nowMillis, source, paid, seen = true)).takeLast(LOG_SIZE))
            }
            noted(d, logged, eventId, "WORLD", ref, kind, nowMillis, extraPoke = earned)
        }
    }

    /**
     * Sets who is on World team [team]; false when the rank, the team or the Pokémon do not allow it. A Pokémon newly put on a team is taken
     * from its party, the buddy, a Guardian slot, another team or a helper job (one place only), unless that team is out on an expedition.
     */
    fun worldAssign(team: Int, members: List<String>): Boolean {
        if (members.any { id -> data.player.pokemon.none { it.id == id } }) return false
        val already = data.world.team(team)?.members.orEmpty().toSet()
        val incoming = members.filter { it !in already }
        if (incoming.any { !Places.movable(data, it) }) return false
        var moved = data
        for (id in incoming) moved = Places.vacate(moved, id)
        val next = moved.world.assign(team, members) ?: return false
        update { _ -> moved.copy(player = Places.leads(moved.player), world = next) }
        return true
    }

    /** Names World team [team] (empty goes back to "Team n"). */
    fun worldRenameTeam(team: Int, name: String): Boolean {
        val next = data.world.renameTeam(team, name) ?: return false
        update { it.copy(world = next) }
        return true
    }

    /** Gives World team [team] the accent number [accent]. */
    fun worldRecolourTeam(team: Int, accent: Int): Boolean {
        val next = data.world.recolourTeam(team, accent) ?: return false
        update { it.copy(world = next) }
        return true
    }

    /** Saves who is on World team [team] as a preset called [name]. */
    fun worldSavePreset(team: Int, name: String): Boolean {
        val members = data.world.team(team)?.members ?: return false
        val next = data.world.savePreset(name, members) ?: return false
        update { it.copy(world = next) }
        return true
    }

    fun worldDeletePreset(name: String) = update { it.copy(world = it.world.deletePreset(name)) }

    /** Puts the preset [name] on World team [team]: whoever of it is still owned and in the PC (or already on this team): a Pokémon in a party, slotted or helping is not taken. */
    fun worldApplyPreset(team: Int, name: String): Boolean {
        val onTeam = data.world.team(team)?.members.orEmpty().toSet()
        val owned = data.player.pokemon.map { it.id }.filter { it in onTeam || Places.of(data, it) == Place.Pc }.toSet()
        val next = data.world.applyPreset(team, name, owned) ?: return false
        update { it.copy(world = next) }
        return true
    }

    fun worldStart(team: Int, dungeonId: String): Boolean {
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
    fun worldQuickSend(dungeonId: String): Boolean {
        worldSettle()
        val dungeon = app.pokeplan.world.Dungeons.byId(dungeonId) ?: return false
        val slot = data.world.slots().firstOrNull { it.expedition == null } ?: return false
        val own = slot.members.mapNotNull { id -> data.player.pokemon.firstOrNull { it.id == id } }
        val best = WorldSim.bestTeam(dungeon, (own + worldIdle()).filter { !Guardians.canBe(it) }.distinctBy { it.id })
        if (best.isEmpty()) return false
        if (best.map { it.id }.toSet() != slot.members.toSet() && !worldAssign(slot.id, best.map { it.id })) return false
        return worldStart(slot.id, dungeonId)
    }

    /**
     * The Pokémon free to join a World team, strongest first: in the PC: not the buddy, not in a party, not slotted as a Guardian, not helping a tool and not on a team
     * (one place only; choosing one for a team moves it, so the others are offered by moving them first).
     */
    fun worldIdle(): List<OwnedPokemon> = data.player.pokemon
        .filter { Places.of(data, it.id) == Place.Pc }
        .sortedWith(compareByDescending<OwnedPokemon> { it.level }.thenBy { it.id })

    /** Answers the World choice [id] with [option] (0 or 1), after settling what is due; false when there is no such choice. */
    fun worldChoose(id: String, option: Int): Boolean {
        worldSettle()
        val next = data.world.resolveChoice(id, option, nowMillis) ?: return false
        worldPay("World: exploring", "world:choice:$id", id, "choice") { it.copy(world = next) }
        return true
    }

    /** Counts a story scene as watched to its end (only the record: replaying grants nothing). */
    fun worldSceneDone(id: String) { if (id !in data.world.scenesDone) update { it.copy(world = it.world.copy(scenesDone = it.world.scenesDone + id)) } }

    /** Counts a chapter's episode as watched to its end (and forgets where it left off). */
    fun worldEpisodeDone(key: String, flags: Set<String> = emptySet()) {
        if (key !in data.world.episodesDone || key in data.world.storyResume || !data.world.storyFlags.containsAll(flags)) update { it.copy(world = it.world.copy(episodesDone = it.world.episodesDone + key, storyResume = it.world.storyResume - key, storyFlags = it.world.storyFlags + flags)) }
        // Mew keeps its word: having agreed to join in the opening, it waits in Finds, once (the gift flag stops a replay from giving a second).
        val world = data.world
        if (key == app.pokeplan.world.WorldMoments.INTRO_KEY && app.pokeplan.world.story.IntroMewChapter.MEW_JOINED in world.storyFlags && MEW_GIFT !in world.storyFlags) {
            update { it.copy(world = it.world.copy(storyFlags = it.world.storyFlags + MEW_GIFT, finds = it.world.finds + app.pokeplan.world.Find(151, "tiny-woods", 1, nowMillis))) }
        }
    }

    /** Notes the story flags set so far (answers to choices) and the next scene of an unfinished episode, so it can start there next time. */
    fun worldStoryProgress(key: String, nextScene: Int, flags: Set<String>) {
        if (data.world.storyResume[key] != nextScene || !data.world.storyFlags.containsAll(flags)) update { it.copy(world = it.world.copy(storyResume = it.world.storyResume + (key to nextScene), storyFlags = it.world.storyFlags + flags)) }
    }

    /** The next World moment to show (a new rank, a chapter opening) or null; first counts what an older save already reached as seen. */
    fun worldMoment(): app.pokeplan.world.WorldMoment? {
        val adopted = app.pokeplan.world.WorldMoments.adopted(data.world)
        if (adopted != data.world) update { it.copy(world = adopted) }
        return app.pokeplan.world.WorldMoments.pending(adopted).firstOrNull()
    }

    /** Counts [moment] as shown, so it plays once. */
    fun worldMomentSeen(moment: app.pokeplan.world.WorldMoment) = update { it.copy(world = app.pokeplan.world.WorldMoments.seen(it.world, moment)) }

    /** Brings a team home after settling what it found. */
    fun worldRecall(team: Int) {
        worldSettle()
        data.world.recall(team)?.let { next -> update { it.copy(world = next) } }
    }

    /** Recruits the find at [index] into the shared collection: the same persistent Pokémon as any other (docs/world.md, section 12). Null when there is none. */
    fun worldRecruit(index: Int): OwnedPokemon? {
        val find = data.world.finds.getOrNull(index) ?: return null
        val mon = grantPokemon(find.speciesId, WORLD_SOURCE, Origin.How.WORLD)
        update { it.copy(world = it.world.withoutFind(index)) }
        return mon
    }

    /** Moves the items World found into the shared Bag. Returns how many items moved. */
    fun worldClaimItems(): Int {
        val items = data.world.items
        if (items.isEmpty()) return 0
        update { d -> d.copy(player = d.player.copy(inventory = items.entries.fold(d.player.inventory) { bag, (id, n) -> bag.add(id, n) }), world = d.world.copy(items = emptyMap())) }
        return items.values.sum()
    }

    /** Takes a job from the World mission board; false when it is not offered or three are held. */
    fun worldAcceptMission(id: String): Boolean {
        worldSettle()
        val next = data.world.copy(lastSeenAt = nowMillis).acceptMission(id) ?: return false
        update { it.copy(world = next) }
        return true
    }

    fun worldAbandonMission(id: String) {
        data.world.abandonMission(id)?.let { next -> update { it.copy(world = next) } }
    }

    /** Claims a finished job: Poké and Exploration XP go to World, its item to the shared Bag, and the reward log records it. */
    fun worldClaimMission(id: String): Boolean {
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
    fun worldUpgrade(facility: app.pokeplan.world.Facility): Boolean {
        worldSettle()
        val cost = data.world.upgradeCost(facility)
        val next = data.world.upgrade(facility, data.player.coins) ?: return false
        update { it.copy(world = next, player = it.player.copy(coins = it.player.coins - cost)) }
        return true
    }

    /** Claims the World research milestone at [species] discovered species. */
    fun worldClaimResearch(species: Int): Boolean {
        worldSettle()
        val next = data.world.claimResearch(species) ?: return false
        worldPay("World: research", "world:research:$species", "research:$species", "research") { it.copy(world = next) }
        return true
    }

    /** Brews at the Café: three berries from the Bag, then three hours of luckier floors. False without the berries. */
    fun worldBrew(): Boolean {
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

    /** Buys a Rare Candy at the Kecleon Shop for Poké. False when Poké is short. */
    fun worldBuyCandy(): Boolean {
        val price = data.world.candyPrice
        val next = data.world.buyCandy(data.player.coins) ?: return false
        update { d -> d.copy(world = next, player = d.player.copy(coins = d.player.coins - price, inventory = d.player.inventory.add(Items.RARE_CANDY, 1))) }
        return true
    }

    /** The Swap Shop: three berries of the flavour [from] for one of the flavour [to]. False without the berries or when the flavours are the same. */
    fun worldSwap(from: String, to: String): Boolean {
        if (from == to || from !in app.pokeplan.world.SwapShop.flavors || to !in app.pokeplan.world.SwapShop.flavors) return false
        val give = Items.berry(from)
        if (!data.player.inventory.has(give, app.pokeplan.world.SwapShop.GIVE)) return false
        update { d -> d.copy(player = d.player.copy(inventory = d.player.inventory.remove(give, app.pokeplan.world.SwapShop.GIVE).add(Items.berry(to), 1))) }
        return true
    }

    /** Dojo training: Poké into skill XP. */
    fun worldTrain(skill: app.pokeplan.world.WorldSkill): Boolean {
        val next = data.world.train(skill, data.player.coins) ?: return false
        update { it.copy(world = next, player = it.player.copy(coins = it.player.coins - app.pokeplan.world.Dojo.COST)) }
        return true
    }

    /** Opens a treasure box of [tier]: berries, and from the silver tier a chance of a Rare Candy; what is inside goes to the Bag and the reward log. Returns what was found. *(tune)* */
    fun worldOpenTreasure(tier: Int): Map<String, Int> {
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

    // ----- Pokégear: tools and their helper Pokémon (cosmetic: nothing here pays, records effort or reaches the server) -----

    /** The owned Pokémon who can help [tool], the friendliest first (then the higher level, then by id so it is stable). */
    fun gearCandidates(tool: GearTool): List<OwnedPokemon> =
        data.player.pokemon.filter { tool.helps(it.speciesId) }.sortedWith(compareByDescending<OwnedPokemon> { it.friendship }.thenByDescending { it.level }.thenBy { it.id })

    /** Who helps [tool] now: the one the player chose (while they still own them), else no one. */
    fun gearHelper(tool: GearTool): GearHelper {
        // Only a helper the player chose counts: picking one from the collection by itself would take it out of the PC (one place only).
        val owned = data.gearHelpers[tool.name]?.let { id -> data.player.pokemon.firstOrNull { it.id == id && tool.helps(it.speciesId) } }
        return when {
            owned != null -> GearHelper.Owned(owned)
            else -> GearHelper.None
        }
    }

    /**
     * Makes [pokemonId] the helper of [tool]; false if they are not owned, cannot help it or are on an expedition. A Pokémon helps one tool
     * and is in no other place, so it is taken from its party, the buddy, a team or another tool.
     */
    fun assignGearHelper(tool: GearTool, pokemonId: String): Boolean {
        val mon = data.player.pokemon.firstOrNull { it.id == pokemonId } ?: return false
        if (!tool.helps(mon.speciesId)) return false
        return moveTo(pokemonId) { d -> d.copy(gearHelpers = d.gearHelpers - tool.name + (tool.name to pokemonId)) }
    }

    /** Forgets the chosen helper: it goes back to the PC, and the tool shows its own icon. */
    fun clearGearHelper(tool: GearTool) = update { it.copy(gearHelpers = it.gearHelpers - tool.name) }

    fun setPickerOptions(options: List<String>) = update { it.copy(pickerOptions = options.map { o -> o.trim() }.filter { o -> o.isNotEmpty() }.distinct().take(MAX_PICKER_OPTIONS)) }

    fun saveGearRates(rates: GearRates) = update { it.copy(gearRates = rates) }

    /** Changes what the Pokégear tools remember. Device-only; changes nothing else and pays nothing. */
    fun changeGear(change: (GearData) -> GearData) = update { it.copy(gear = change(it.gear)) }

    // --- Pokégear timers and stopwatch (docs/pokegear-audit.md): utilities that pay nothing ---------------------------------

    fun timerAdd(label: String, durationMs: Long): GearTimer? {
        val id = newId()
        val next = GearTimers.add(data.gear, id, label, durationMs) ?: return null
        update { it.copy(gear = next) }
        return next.timers.first { it.id == id }
    }

    fun timerStart(id: String) = changeGear { GearTimers.start(it, id, nowMillis) }
    fun timerPause(id: String) = changeGear { GearTimers.pause(it, id, nowMillis) }
    fun timerAddTime(id: String, ms: Long) = changeGear { GearTimers.addTime(it, id, ms, nowMillis) }
    fun timerReset(id: String) = changeGear { GearTimers.reset(it, id) }
    fun timerRemove(id: String) = changeGear { GearTimers.remove(it, id) }
    fun timersDismissFinished() = changeGear { GearTimers.dismissFinished(it) }

    /** Marks the timers that ran out as finished and returns them (the screen and the notification call this; calling it again returns none). */
    fun timersTick(): List<GearTimer> {
        val (next, ran) = GearTimers.tick(data.gear, nowMillis)
        if (ran.isNotEmpty()) update { it.copy(gear = next) }
        return ran
    }

    fun stopwatchStart() = changeGear { GearTimers.stopwatchStart(it, nowMillis) }
    fun stopwatchPause() = changeGear { GearTimers.stopwatchPause(it, nowMillis) }
    fun stopwatchLap() = changeGear { GearTimers.stopwatchLap(it, nowMillis) }
    fun stopwatchReset() = changeGear { GearTimers.stopwatchReset(it) }

    /** Notes that [tool] was opened: it goes to the front of the recents (a short list), nothing more. */
    fun gearUsed(tool: GearTool) = changeGear { g -> g.copy(recents = (listOf(tool.name) + g.recents.filter { it != tool.name }).take(GearData.MAX_RECENTS)) }

    /** Stars or unstars a tool. */
    fun gearFavorite(tool: GearTool, on: Boolean) = changeGear { g -> g.copy(favorites = if (on) (g.favorites.filter { it != tool.name } + tool.name) else g.favorites - tool.name) }

    fun toggleFavorite(id: String) = update { d -> d.copy(favorites = if (id in d.favorites) d.favorites - id else d.favorites + id) }

    /** Sends a duplicate away for Rare Candies and coins. Returns false if it may not be transferred (or is starred). */
    fun transfer(id: String): Boolean {
        if (transferBlock(id) != null || id in data.favorites) return false
        val p = pokemon(id)
        val quote = Transfer.quote(p)
        update { d ->
            var bag = d.player.inventory.add(Items.RARE_CANDY, quote.rareCandies)
            quote.returnedItem?.let { bag = bag.add(it) }
            d.copy(player = d.player.copy(
                pokemon = d.player.pokemon.filter { it.id != id }, inventory = bag, coins = d.player.coins + quote.coins,
            ), origins = d.origins - id)
        }
        return true
    }

    /** Tasks finished since the day began. */
    fun doneToday(): Int = data.tasks.count { it.state == TaskState.DONE && (it.completedAtMillis ?: 0L) >= startOfTodayMillis }

    /**
     * The meaningful thing that happened in the last few minutes (an egg hatched, a task or session paid out), or null.
     * The buddy only speaks and celebrates for these; the rest of the time it just idles.
     */
    fun buddyEvent(care: Boolean = false): BuddyEventNow? = eventWhere { !care || it.startsWith(CARE_LOG_PREFIX) }

    /**
     * The moment a module's lead celebrates on that module's page: only what that module paid. [prefix] is the module's log prefix
     * ([HEAL_LOG_PREFIX], [MINT_LOG_PREFIX], [HOME_LOG_PREFIX], [CARE_LOG_PREFIX]); null is Plan, whose rewards carry no prefix (a finished
     * task, a hatched egg), so nothing another module, World or a mini-quest paid counts.
     */
    fun moduleEvent(prefix: String?): BuddyEventNow? = eventWhere { source ->
        if (prefix != null) source.startsWith(prefix)
        else MODULE_LOG_PREFIXES.none { source.startsWith(it) } && source != WORLD_SOURCE && source != MINI_QUEST_SOURCE
    }

    private fun eventWhere(counts: (String) -> Boolean): BuddyEventNow? {
        val recent = data.rewardLog.filter { nowMillis - it.atMillis < EVENT_MILLIS && counts(it.source) }
        val latest = recent.lastOrNull() ?: return null
        // A Pokémon the World gives is not an egg hatching, so it is not a hatch moment.
        val hatches = recent.filter { it.source != WORLD_SOURCE }.flatMap { e -> e.rewards.filterIsInstance<Reward.Hatched>() }
        val event = when {
            hatches.any { it.pokemon.isShiny } -> BuddyEvent.SHINY
            hatches.isNotEmpty() -> BuddyEvent.HATCHED
            else -> BuddyEvent.DONE
        }
        return BuddyEventNow(event, latest.atMillis)
    }

    /** Progress of [p] towards its next level, 0 to 1: the Rare Candies fed so far out of the ones this level needs. */
    fun levelProgress(p: OwnedPokemon): Float {
        if (!p.canFeed) return 1f
        val partner = data.player.pokemon.filter { it.inParty }.maxOfOrNull { it.level } ?: p.level
        return ((p.candyProgress + p.xp.toFloat() / CandyRules.XP_PER_CANDY) / CandyRules.candiesNeeded(p.level, partner)).coerceIn(0f, 1f)
    }

    /**
     * Pets the buddy, or [pokemonId] (Care's lead on Care's screens): friendship from [FriendshipRules.Source.PET], up to [PET_DAILY_CAP] a day *(tune)*, shared by all.
     * Returns the friendship gained, 0 when nothing changed (no buddy, or today's petting is used up; nothing is saved then).
     */
    fun pet(pokemonId: String? = null): Int {
        val buddy = (if (pokemonId == null) data.player.buddy else data.player.pokemon.firstOrNull { it.id == pokemonId }) ?: return 0
        // Petting is the "Pet your buddy" mini-quest, so it ticks itself, even once today's petting friendship is used up.
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

    /**
     * Gives [pokemonId] an item from the bag to hold. Whatever it held before goes back to the bag. Returns false if the bag
     * has none, the item cannot be held, or the Pokémon is a Guardian (they are at full power and hold nothing).
     */
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

    /** Puts [pokemonId]'s held item back in the bag. */
    fun takeItem(pokemonId: String) {
        val p = pokemon(pokemonId)
        val item = p.heldItem ?: return
        update { d ->
            d.copy(player = d.player.copy(
                inventory = d.player.inventory.add(item), pokemon = d.player.pokemon.map { if (it.id == pokemonId) p.withHeldItem(null) else it },
            ))
        }
    }

    /** Whether [itemId] would do anything if used on [p] now (an Ability Capsule on a Pokémon with one ability does not). */
    fun canUse(itemId: String, p: OwnedPokemon): Boolean = ItemEffects.canUse(itemId, p)

    /** Uses a bag item on a Pokémon (see [ItemUse]). Returns false if the bag has none, or it would do nothing. */
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

    /** Uses an item that needs no choice: a vitamin, a berry, the Ability Capsule or Patch, a Tera Shard. */
    fun useItem(itemId: String, pokemonId: String): Boolean = useItem(ItemUse.Plain(itemId), pokemonId)

    /** Puts items in the bag (gifts, quest rewards, the shop). Anything over the bag limit turns into coins. */
    fun grant(itemId: String, amount: Int) = update { d ->
        val (bag, overflow) = d.player.inventory.addCapped(itemId, amount)
        d.copy(player = d.player.copy(inventory = bag, coins = d.player.coins + overflow))
    }

    // --- views --------------------------------------------------------------------------------------------------------

    fun todayTasks(): List<Task> = TaskViews.today(data.tasks.filter { it.state != TaskState.DELETED }, now)

    /** Today's open tasks, less the ones just ticked off and waiting out their undo: the list Home, Plan and the widget show. */
    fun openTodayTasks(): List<Task> {
        val pending = data.pending.map { it.original.id }.toSet()
        return todayTasks().filter { it.id !in pending }
    }

    /** The berry the buddy is fed from the bag (the first by name), wherever it is fed; null with none. */
    fun buddyBerry(): String? = berries().keys.minOrNull()

    companion object {
        /** The version of the Pokémon-roles layout that [migrateRoles] brings a save to. */
        const val ROLES_VERSION = 1

        /** The reward-log source of what World gives (a recruit, the Guardian): not an egg, so no hatch scene or hatch reaction. */
        const val WORLD_SOURCE = "World"
        const val MEW_GIFT = "gift-mew"

        const val MAX_PICKER_OPTIONS = 30

        /** The welcome-back gift after a week or more away. *(tune)* */
        const val WELCOME_BACK_RARE_CANDIES = 5
        const val WELCOME_BACK_BERRIES = 2

        const val MAX_SUBTASKS = 5

        /** How long a reward counts as "just happened" for the buddy. */
        const val EVENT_MILLIS = 5 * 60_000L

        /** Friendship a day that petting can give. */
        const val PET_DAILY_CAP = 10

        /** The reward log's name for a mini-quest's rewards. */
        const val MINI_QUEST_SOURCE = "Mini-quest"

        const val LOG_SIZE = 50

        /** Friends Care can make a day, one per visitor in the meadow (`Visitors.MAX_VISITORS`). *(tune)* */
        const val BEFRIEND_DAILY_CAP = 4

        /** The most of each Care kind the world pays a day *(tune)*, a backstop to the same limits in CareRules. */
        val CARE_DAILY_CAP: Map<CareKind, Int> = mapOf(CareKind.ROUTINE to 5, CareKind.CALM_BREAK to 3, CareKind.SKILL to 3, CareKind.WEEKLY_RHYTHM to 1, CareKind.PLAY to 6)
        /** Every Care entry in the reward log starts with this (the kind of moment only, never what it was). */
        const val CARE_LOG_PREFIX = "Care: "

        /** The reward-log source of a befriended Care visitor, whose gift really is an egg (MODULES/CARE.md section 6). */
        const val CARE_FRIEND_SOURCE = CARE_LOG_PREFIX + "made a friend"

        /** The most of each HEAL kind the world pays a day *(tune)*, a backstop to the same limits in `HealRules`. */
        val HEAL_DAILY_CAP: Map<HealKind, Int> = mapOf(HealKind.ACTIVITY to HealRules.ACTIVITIES_PAID_PER_DAY, HealKind.PARTICIPATION to HealRules.PARTICIPATION_PAID_PER_DAY)

        /** Every HEAL entry in the reward log starts with this (the kind of moment only, never what it was). */
        const val HEAL_LOG_PREFIX = "Heal: "
        private val MODULE_LOG_PREFIXES by lazy { listOf(CARE_LOG_PREFIX, HEAL_LOG_PREFIX, MINT_LOG_PREFIX, HOME_LOG_PREFIX, LINK_LOG_PREFIX) }
        private val HEAL_SOURCES = mapOf(HealKind.ACTIVITY to "Field Research", HealKind.PARTICIPATION to "Field Research").mapValues { HEAL_LOG_PREFIX + it.value }

        /** The most of each MINT kind the world pays in its period *(tune)*, a backstop to the same limits in `MintRules`: reconciled accounts a day, reviews a week. */
        val MINT_CAPS: Map<MintKind, Int> = mapOf(MintKind.RECONCILED to app.pokeplan.mint.MintRules.RECONCILES_PAID_PER_DAY, MintKind.REVIEW to app.pokeplan.mint.MintRules.REVIEWS_PAID_PER_WEEK)

        /** Every MINT entry in the reward log starts with this (the kind of moment only, never what it was). */
        const val MINT_LOG_PREFIX = "Mint: "
        private val MINT_SOURCES = mapOf(MintKind.RECONCILED to "Ledger Research", MintKind.REVIEW to "Ledger Research").mapValues { MINT_LOG_PREFIX + it.value }

        /** The most of each HOME kind the world pays in a day *(tune)*, a backstop to the same limits in `HomeRules`. */
        val HOME_CAPS: Map<HomeKind, Int> = mapOf(HomeKind.CHORE to app.pokeplan.home.HomeRules.CHORES_PAID_PER_DAY, HomeKind.MAINTENANCE to app.pokeplan.home.HomeRules.MAINTENANCE_PAID_PER_DAY)

        /** Every HOME entry in the reward log starts with this (the kind of moment only, never what it was). */
        const val HOME_LOG_PREFIX = "Home: "
        private val HOME_SOURCES = mapOf(HomeKind.CHORE to "Habitat Discovery", HomeKind.MAINTENANCE to "Habitat Discovery").mapValues { HOME_LOG_PREFIX + it.value }

        /** The most of each LINK kind the world pays in a day *(tune)*, a backstop to the same limit in `LinkRules`. */
        val LINK_CAPS: Map<LinkMoment, Int> = mapOf(LinkMoment.FOLLOW_UP to app.pokeplan.link.LinkRules.FOLLOW_UPS_PAID_PER_DAY)

        /** Every LINK entry in the reward log starts with this (the kind of moment only, never what it was). */
        const val LINK_LOG_PREFIX = "Link: "
        private val LINK_SOURCES = mapOf(LinkMoment.FOLLOW_UP to "Postcard Trail").mapValues { LINK_LOG_PREFIX + it.value }

        /** How many paid LINK event ids the save remembers. */
        const val LINK_PAID_KEPT = 1000

        /** How many paid HOME event ids the save remembers. */
        const val HOME_PAID_KEPT = 1000

        /** How many [LedgerNote]s the save keeps; the database settles each as it is written, so only the newest few are ever still waiting. */
        const val LEDGER_NOTES_KEPT = 50

        /** How many paid MINT event ids the save remembers. */
        const val MINT_PAID_KEPT = 1000

        /** How many paid HEAL event ids the save remembers. */
        const val HEAL_PAID_KEPT = 1000

        private val CARE_SOURCES = mapOf(
            CareKind.ROUTINE to "a routine", CareKind.CALM_BREAK to "a calm break", CareKind.WEEKLY_RHYTHM to "kept a rhythm", CareKind.SKILL to "a skill practised", CareKind.PLAY to "time together",
        ).mapValues { CARE_LOG_PREFIX + it.value }

        /** Parts of the save that stay on this device: never uploaded, kept when the cloud save is adopted (see [cloudPart] and [adoptCloud]). */
        internal val DEVICE_ONLY = setOf("eggTickets", "shownCalendars", "pickerOptions", "gearRates", "gear")

        /** What the "Export my data" file leaves out: the same as [DEVICE_ONLY] except the Picker's list and what the Pokégear tools remember, which are the player's own data. */
        private val EXPORT_LEFT_OUT = DEVICE_ONLY - setOf("pickerOptions", "gear")

        /**
         * [text], a save, as the app would hold it after loading it (every rule that tidies a save applied) and serialised again, or an
         * exception when it cannot be read. The live database uses it on a save it did not get from the app's own writes: the one it moves
         * into the shared tables and a restored backup.
         */
        fun normalizeDocument(text: String, nowMillis: Long): String {
            val storage = MemoryStorage(text)
            val store = GameStore(storage, { nowMillis })
            check(storage.keptAside == null) { "the save cannot be read" }
            return json.encodeToString(AppData.serializer(), store.data)
        }

        // "kind" because the rewards have a field called "type" (the same setting the reward engine uses over JSON).
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; classDiscriminator = "kind" }

        /** Every starter Pokémon of the main games, by generation, plus the two partner Pokémon of Let's Go. */
        val starterGroups: List<Pair<String, List<Int>>> = listOf(
            "Kanto" to listOf(1, 4, 7), "Johto" to listOf(152, 155, 158), "Hoenn" to listOf(252, 255, 258),
            "Sinnoh" to listOf(387, 390, 393), "Unova" to listOf(495, 498, 501), "Kalos" to listOf(650, 653, 656),
            "Alola" to listOf(722, 725, 728), "Galar" to listOf(810, 813, 816), "Paldea" to listOf(906, 909, 912),
            "Partners" to listOf(25, 133),
        )

        val starters: List<Int> = starterGroups.flatMap { it.second }
    }
}

/** Who helps a Pokégear tool: an owned Pokémon the player chose, or no one. */
sealed interface GearHelper {
    data class Owned(val pokemon: OwnedPokemon) : GearHelper
    data object None : GearHelper
}
