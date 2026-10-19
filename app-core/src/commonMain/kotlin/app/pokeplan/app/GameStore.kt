package app.pokeplan.app

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.model.PokemonType
import app.pokeplan.core.pokemon.CandyRules
import app.pokeplan.core.pokemon.EvolutionContext
import app.pokeplan.core.pokemon.EvolutionOption
import app.pokeplan.core.pokemon.EvolutionRules
import app.pokeplan.core.pokemon.Guardians
import app.pokeplan.core.pokemon.Hatching
import app.pokeplan.core.pokemon.ItemCategory
import app.pokeplan.core.pokemon.ItemEffects
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.pokemon.Stat
import app.pokeplan.core.pokemon.Transfer
import app.pokeplan.core.pokemon.TransferBlock
import app.pokeplan.core.reward.AchievementEvent
import app.pokeplan.core.reward.EffortEvent
import app.pokeplan.core.reward.Reward
import app.pokeplan.core.reward.RewardEngine
import app.pokeplan.plan.Category
import app.pokeplan.plan.FailReason
import app.pokeplan.plan.FocusEvent
import app.pokeplan.plan.FocusRecord
import app.pokeplan.plan.FocusRules
import app.pokeplan.plan.FocusSession
import app.pokeplan.plan.Goal
import app.pokeplan.plan.Insights
import app.pokeplan.plan.GoalRules
import app.pokeplan.plan.Habit
import app.pokeplan.plan.Milestone
import app.pokeplan.plan.MilestoneResult
import app.pokeplan.plan.Reviews
import app.pokeplan.plan.HabitTarget
import app.pokeplan.plan.Phase
import app.pokeplan.plan.Rank
import app.pokeplan.plan.Recurrence
import app.pokeplan.plan.RecurringTasks
import app.pokeplan.plan.Streaks
import app.pokeplan.plan.Subtask
import app.pokeplan.plan.Task
import app.pokeplan.plan.TaskRules
import app.pokeplan.plan.TaskState
import app.pokeplan.plan.TaskViews
import app.pokeplan.plan.toLocalDateTime
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.random.Random

/** What a focus session turned out to be. */
sealed interface FocusResult {
    data class Hatched(val rewards: List<Reward>) : FocusResult
    data class Failed(val reason: FailReason) : FocusResult
}

/**
 * The app's rules engine for one device: holds the saved [data], applies every action to it with the real game rules
 * (the same ones the server runs), and saves after each change. No UI code in here, so it is tested on the JVM.
 *
 * Rewards are granted locally; once the account is online the server becomes the authority and this store only previews.
 */
class GameStore(
    private val storage: Storage,
    private val clock: Clock,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val rng: Random = Random,
) {
    var data: AppData = load()
        private set

    private val listeners = mutableListOf<(AppData) -> Unit>()

    /** Calls [listener] after every change. Returns a function that stops listening. */
    fun observe(listener: (AppData) -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    private fun load(): AppData {
        val text = storage.load() ?: return AppData()
        return try {
            json.decodeFromString(AppData.serializer(), text)
        } catch (e: Exception) {
            // A damaged save never blocks the app: keep a copy aside and start fresh.
            storage.save(text)
            AppData()
        }
    }

    private fun update(change: (AppData) -> AppData) {
        data = change(data)
        storage.save(json.encodeToString(AppData.serializer(), data))
        listeners.toList().forEach { it(data) }
    }

    // --- time and ids -------------------------------------------------------------------------------------------------

    val nowMillis: Long get() = clock.nowMillis()
    val now: LocalDateTime get() = nowMillis.toLocalDateTime(zone)
    val today: LocalDate get() = now.date
    private val epochDay: Long get() = today.toEpochDays()

    private fun newId(): String = "${nowMillis.toString(36)}-${rng.nextInt(Int.MAX_VALUE).toString(36)}"

    // --- onboarding ---------------------------------------------------------------------------------------------------

    /** Starts as a guest: creates the trainer, the starter (which becomes the buddy) and a first task that pays at once. */
    fun start(trainerName: String, starterSpeciesId: Int) {
        check(!data.onboarded) { "already started" }
        val option = HatchOption(starterSpeciesId, Dex.defaultForm(starterSpeciesId).identifier)
        val starter = Hatching.create(newId(), option, 0, nowMillis, 25, rng).copy(isShiny = false, inParty = true)
        val first = Task(
            id = newId(), title = "Finish your first task", rank = Rank.E, planned = today, tutorial = true, createdAtMillis = nowMillis,
        )
        update {
            it.copy(
                trainerName = trainerName.trim().ifEmpty { "Trainer" }, onboarded = true,
                player = it.player.copy(pokemon = listOf(starter), buddyId = starter.id, caught = setOf(starter.speciesId)),
                tasks = listOf(first),
            )
        }
    }

    fun rename(trainerName: String) = update { it.copy(trainerName = trainerName.trim().ifEmpty { it.trainerName }) }

    fun changeSettings(change: (Settings) -> Settings) = update { it.copy(settings = change(it.settings)) }

    // --- rewards ------------------------------------------------------------------------------------------------------

    private fun log(source: String, rewards: List<Reward>, d: AppData): AppData =
        d.copy(rewardLog = (d.rewardLog + RewardEntry(nowMillis, source, rewards)).takeLast(LOG_SIZE))

    fun markRewardsSeen() = update { d -> d.copy(rewardLog = d.rewardLog.map { it.copy(seen = true) }) }

    private fun achieve(d: AppData, tier: Int, source: String, onTime: Boolean = false, focusedOnIt: Boolean = false): AppData {
        val firstOfDay = today !in d.activeDays
        val event = AchievementEvent(
            tier = tier, region = d.settings.region, hourOfDay = now.hour, firstOfDay = firstOfDay, onTime = onTime,
            focusedOnIt = focusedOnIt, newPokemonId = newId(), nowMillis = nowMillis,
        )
        val outcome = RewardEngine.achievement(d.player, event, rng)
        return log(source, outcome.rewards, d.copy(player = outcome.state, activeDays = d.activeDays + today))
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
    ): Task {
        val task = Task(
            id = newId(), title = title.trim(), notes = notes, rank = rank, category = category, planned = planned, due = due,
            estimateMinutes = estimateMinutes, createdAtMillis = nowMillis,
        )
        update { it.copy(tasks = it.tasks + task) }
        return task
    }

    fun updateTask(task: Task) = update { d -> d.copy(tasks = d.tasks.map { if (it.id == task.id) task else it }) }

    fun deleteTask(id: String) = update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) TaskRules.delete(it, nowMillis) else it }) }

    fun restoreTask(id: String) = update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) TaskRules.restore(it, nowMillis) else it }) }

    fun wontDo(id: String) = update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) TaskRules.wontDo(it, nowMillis) else it }) }

    fun reopen(id: String) = update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) TaskRules.reopen(it) else it }) }

    /** Ticks a task off. The reward is held for the undo window; call [tick] (the UI does, every second) to settle it. */
    fun completeTask(id: String): PendingCompletion {
        val task = data.tasks.first { it.id == id }
        val c = TaskRules.complete(task, nowMillis, zone)
        val pending = PendingCompletion(task, c.task, c.pays, c.tier, c.onTime, c.focusedOnIt, c.finalAtMillis)
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) c.task else it }, pending = d.pending + pending) }
        return pending
    }

    /** Takes back a completion still inside its undo window. */
    fun undoCompletion(taskId: String): Boolean {
        val p = data.pending.firstOrNull { it.original.id == taskId } ?: return false
        if (nowMillis >= p.finalAtMillis) return false
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
                val rewards = listOf(Reward.Candies(app.pokeplan.core.model.CandyType.RARE, TaskRules.SUBTASK_RARE_CANDIES))
                log("Step: ${task.subtasks.first { it.id == subtaskId }.title}", rewards,
                    withTask.copy(player = withTask.player.copy(inventory = bag, coins = withTask.player.coins + overflow)))
            }
        }
        return pays
    }

    fun addSubtask(taskId: String, title: String) = update { d ->
        d.copy(tasks = d.tasks.map { t ->
            if (t.id == taskId && t.subtasks.size < MAX_SUBTASKS) t.copy(subtasks = t.subtasks + Subtask(newId(), title.trim(), createdAtMillis = nowMillis)) else t
        })
    }

    /** A repeating task. Its next instances are created straight away and topped up by [refresh]. */
    fun addRecurring(template: Task, recurrence: Recurrence) {
        val seriesId = newId()
        val series = TaskSeries(template.copy(id = seriesId, seriesId = seriesId, createdAtMillis = nowMillis), recurrence)
        update { it.copy(series = it.series + series) }
        refresh()
    }

    // --- the clock ----------------------------------------------------------------------------------------------------

    /** Settles finished undo windows and the focus timer, and creates upcoming recurring tasks. Cheap to call often. */
    fun tick() {
        settlePending()
        tickFocus()
    }

    /** Creates recurring instances for the next days. Call when the app opens and when the date changes. */
    fun refresh() {
        val created = data.series.flatMap { RecurringTasks.instancesToCreate(it.template, it.recurrence, data.tasks, today, nowMillis) }
        if (created.isNotEmpty()) update { it.copy(tasks = it.tasks + created) }
    }

    private fun settlePending() {
        val due = data.pending.filter { nowMillis >= it.finalAtMillis }
        if (due.isEmpty()) return
        update { d ->
            var next = d.copy(pending = d.pending - due.toSet())
            for (p in due) {
                if (p.pays) next = achieve(next, p.tier, "Task: ${p.done.title}", p.onTime, p.focusedOnIt)
                else next = next.copy(activeDays = next.activeDays)
            }
            next
        }
    }

    // --- habits -------------------------------------------------------------------------------------------------------

    fun addHabit(title: String, target: HabitTarget = HabitTarget.Daily, rank: Rank = Rank.C, category: Category = Category.HEALTH): Habit {
        val habit = Habit(newId(), title.trim(), rank, category, target, today)
        update { it.copy(habits = it.habits + habit) }
        return habit
    }

    /** Marks a habit done for [date]; the first completion of the day pays like a task. */
    fun completeHabit(id: String, date: LocalDate = today) {
        val habit = data.habits.first { it.id == id }
        val pays = habit.paysOn(date) && date == today
        update { d ->
            val next = d.copy(habits = d.habits.map { if (it.id == id) it.complete(date) else it })
            if (pays) achieve(next, habit.rank.tier, "Habit: ${habit.title}") else next
        }
    }

    fun undoHabit(id: String, date: LocalDate = today) =
        update { d -> d.copy(habits = d.habits.map { if (it.id == id) it.undo(date) else it }) }

    // --- goals --------------------------------------------------------------------------------------------------------

    fun addGoal(title: String, category: Category = Category.PERSONAL, targetDate: LocalDate? = null): Goal {
        val goal = Goal(newId(), title.trim(), category, targetDate, createdAtMillis = nowMillis)
        update { it.copy(goals = it.goals + goal) }
        return goal
    }

    fun addMilestone(goalId: String, title: String) = update { d ->
        d.copy(goals = d.goals.map { g -> if (g.id == goalId) GoalRules.addMilestone(g, Milestone(newId(), title.trim(), nowMillis)) else g })
    }

    /** Ticks a milestone. It pays a Super Candy if it is old enough (and fewer than 3 paid today); finishing the goal gives a bonus item. */
    fun completeMilestone(goalId: String, milestoneId: String): MilestoneResult {
        val goal = data.goals.first { it.id == goalId }
        val key = "milestones:$today"
        val result = GoalRules.completeMilestone(goal, milestoneId, nowMillis, data.counters[key] ?: 0)
        update { d ->
            var next = d.copy(goals = d.goals.map { if (it.id == goalId) result.goal else it })
            val gifts = buildList {
                if (result.pays) add(Items.SUPER_CANDY to GoalRules.SUPER_CANDIES_PER_MILESTONE)
                result.bonusItem?.let { add(it to 1) }
            }
            if (result.pays) next = next.copy(counters = next.counters + (key to ((next.counters[key] ?: 0) + 1)), activeDays = next.activeDays + today)
            if (gifts.isNotEmpty()) {
                var bag = next.player.inventory
                var coins = next.player.coins
                gifts.forEach { (item, n) -> bag.addCapped(item, n).let { (b, over) -> bag = b; coins += over } }
                val rewards = gifts.map { (item, n) ->
                    if (item == Items.SUPER_CANDY) Reward.Candies(app.pokeplan.core.model.CandyType.SUPER, n) else Reward.ItemDrop(item, n)
                }
                next = log("Goal: ${goal.title}", rewards, next.copy(player = next.player.copy(inventory = bag, coins = coins)))
            }
            next
        }
        return result
    }

    // --- weekly review and rest days ----------------------------------------------------------------------------------

    val weekStart: LocalDate
        get() = Streaks.weekStartOf(today, if (data.settings.weekStartsOnMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY)

    fun reviewSummary(): Reviews.Summary = Reviews.summary(weekStart, data.tasks, data.focusHistory, data.habits, zone)

    fun canReview(): Boolean = Reviews.canComplete(weekStart, today, data.reviews)

    /** Finishes this week's review: optionally moves unfinished tasks to [carryTo], and pays a berry. */
    fun completeReview(note: String?, carryTo: LocalDate? = null) {
        check(canReview()) { "this week was already reviewed" }
        val carried = if (carryTo != null) reviewSummary().unfinished.map { Reviews.carryForward(it, carryTo) } else emptyList()
        val review = Reviews.complete(weekStart, nowMillis, note, carried, data.reviews)
        val berry = Items.berry(Items.flavors.values.toList()[rng.nextInt(Items.flavors.size)])
        update { d ->
            val byId = carried.associateBy { it.id }
            val next = d.copy(
                tasks = d.tasks.map { byId[it.id] ?: it }, reviews = d.reviews + review,
                player = d.player.copy(inventory = d.player.inventory.add(berry, Reviews.REWARD_BERRIES)),
            )
            log("Weekly review", listOf(Reward.ItemDrop(berry, Reviews.REWARD_BERRIES)), next)
        }
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

    // --- growth -------------------------------------------------------------------------------------------------------

    /** What the last [days] days went into, by category. */
    fun wheel(days: Int = 7): Insights.Wheel =
        Insights.balanceWheel(data.tasks, data.focusHistory, today.minus(DatePeriod(days = days - 1)), today, zone)

    /** Focus minutes and finished tasks for each of the last [weeks] weeks, oldest first. */
    fun trend(weeks: Int = 6): List<Insights.WeekPoint> = Insights.weeklyTrend(data.tasks, data.focusHistory, weeks, today, zone)

    // --- streak -------------------------------------------------------------------------------------------------------

    val streak: Streaks.Result get() = Streaks.daily(data.activeDays, data.restDays, today)

    // --- focus --------------------------------------------------------------------------------------------------------

    /** Starts a strict focus session. */
    fun startFocus(minutes: Int, deepFocus: Boolean, taskId: String? = null, lure: PokemonType? = null): FocusSession {
        check(data.focus == null) { "a session is already running" }
        val session = FocusRules.start(
            id = newId(), durationMinutes = minutes, nowMillis = nowMillis, deepFocus = deepFocus, region = data.settings.region,
            buddyIdAtStart = data.player.buddyId, taskId = taskId, lure = lure, emergencyPausesUsedToday = emergencyPausesToday(),
        )
        update { it.copy(focus = session) }
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
                val event: EffortEvent = session.toEffortEvent(newId(), nowMillis, epochDay)
                // The perks that count are the ones of the buddy when the session started.
                val player = data.player
                val outcome = RewardEngine.effort(if (player.buddyId == session.buddyIdAtStart) player else player, event, rng)
                update { d ->
                    log("Focus: $minutes min", outcome.rewards, d.copy(
                        player = outcome.state, focus = null, activeDays = d.activeDays + today, lastFocusEnd = FocusEnd(nowMillis, true),
                        focusHistory = d.focusHistory + FocusRecord(session.startedAtMillis, minutes, true, session.taskId),
                        tasks = d.tasks.map { if (it.id == session.taskId) it.copy(focusedMinutes = it.focusedMinutes + minutes) else it },
                    ))
                }
                FocusResult.Hatched(outcome.rewards)
            }
            is Phase.Failed -> {
                val minutes = (session.focusedMillis(phase.at) / 60_000L).toInt()
                update { d ->
                    d.copy(
                        focus = null, lastFocusEnd = FocusEnd(nowMillis, false, phase.reason),
                        focusHistory = d.focusHistory + FocusRecord(session.startedAtMillis, minutes, false, session.taskId),
                    )
                }
                FocusResult.Failed(phase.reason)
            }
            else -> null
        }
    }

    fun dismissFocusEnd() = update { it.copy(lastFocusEnd = null) }

    // --- Pokémon ------------------------------------------------------------------------------------------------------

    private fun pokemon(id: String): OwnedPokemon = data.player.pokemon.first { it.id == id }

    private fun replace(p: OwnedPokemon) =
        update { d -> d.copy(player = d.player.copy(pokemon = d.player.pokemon.map { if (it.id == p.id) p else it })) }

    /** Makes a Pokémon the buddy. Guardians (legendaries and mythicals) cannot be buddies. Returns false if refused. */
    fun setBuddy(id: String): Boolean {
        if (Guardians.canBe(pokemon(id))) return false
        update { d -> d.copy(player = d.player.copy(buddyId = id)) }
        return true
    }

    fun rename(id: String, nickname: String) = replace(pokemon(id).withNickname(nickname))

    /** Puts a Pokémon in or out of the party (up to six). Returns false if the party is full or it is the last one. */
    fun setInParty(id: String, inParty: Boolean): Boolean {
        val p = pokemon(id)
        if (inParty && Guardians.canBe(p)) return false
        val partySize = data.player.party.size
        if (inParty && !p.inParty && partySize >= OwnedPokemon.PARTY_SIZE) return false
        if (!inParty && p.inParty && (partySize <= 1 || id == data.player.buddyId)) return false
        replace(p.copy(inParty = inParty))
        return true
    }

    // --- Guardians ----------------------------------------------------------------------------------------------------

    /** How many Guardians the player can slot at their rank. */
    val guardianSlots: Int get() = Guardians.slots(data.player.rank)

    /** Owned legendaries and mythicals, slotted or not. */
    val guardianCandidates: List<OwnedPokemon> get() = data.player.pokemon.filter(Guardians::canBe)

    /** Puts a Guardian in a free slot. Returns false if it is not a Guardian, is already slotted, or every slot is taken. */
    fun slotGuardian(id: String): Boolean {
        val p = pokemon(id)
        if (!Guardians.canBe(p) || id in data.player.guardians || data.player.guardians.size >= guardianSlots) return false
        update { d -> d.copy(player = d.player.copy(guardians = d.player.guardians + id)) }
        return true
    }

    fun unslotGuardian(id: String) = update { d -> d.copy(player = d.player.copy(guardians = d.player.guardians - id)) }

    /** Gives the player a Pokémon outright (a quest reward). It goes to the Guardian collection if it is a legendary or mythical. */
    fun grantPokemon(speciesId: Int, source: String): OwnedPokemon {
        val option = HatchOption(speciesId, Dex.defaultForm(speciesId).identifier)
        val p = Hatching.create(newId(), option, OwnedPokemon.PARTY_SIZE, nowMillis, 60, rng).copy(inParty = false)
        update { d ->
            log(source, listOf(Reward.Hatched(p, fromTask = false)),
                d.copy(player = d.player.copy(pokemon = d.player.pokemon + p, caught = d.player.caught + p.speciesId)))
        }
        return p
    }

    fun setTrainingPlan(id: String, plan: List<Stat>) = replace(pokemon(id).copy(trainingPlan = plan.distinct().take(2)))

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
        Transfer.block(pokemon(id), id == data.player.buddyId, data.player.party.size)

    /** Sends a duplicate away for Rare Candies and coins. Returns false if it may not be transferred. */
    fun transfer(id: String): Boolean {
        if (transferBlock(id) != null) return false
        val p = pokemon(id)
        val quote = Transfer.quote(p)
        update { d ->
            var bag = d.player.inventory.add(Items.RARE_CANDY, quote.rareCandies)
            quote.returnedItem?.let { bag = bag.add(it) }
            d.copy(player = d.player.copy(
                pokemon = d.player.pokemon.filter { it.id != id }, inventory = bag, coins = d.player.coins + quote.coins,
            ))
        }
        return true
    }

    /** Uses a bag item on a Pokémon (vitamins, berries). Returns false if there is none or it does nothing. */
    fun useItem(itemId: String, pokemonId: String): Boolean {
        if (!data.player.inventory.has(itemId)) return false
        val p = pokemon(pokemonId)
        val changed = when {
            itemId in Items.vitamins -> ItemEffects.useVitamin(p, itemId)
            Items.category(itemId) == ItemCategory.BERRY -> ItemEffects.feedBerry(p)
            else -> return false
        }
        update { d ->
            d.copy(player = d.player.copy(
                inventory = d.player.inventory.remove(itemId), pokemon = d.player.pokemon.map { if (it.id == pokemonId) changed else it },
            ))
        }
        return true
    }

    /** Puts items in the bag (gifts, quest rewards, the shop). Anything over the bag limit turns into coins. */
    fun grant(itemId: String, amount: Int) = update { d ->
        val (bag, overflow) = d.player.inventory.addCapped(itemId, amount)
        d.copy(player = d.player.copy(inventory = bag, coins = d.player.coins + overflow))
    }

    // --- views --------------------------------------------------------------------------------------------------------

    fun todayTasks(): List<Task> = TaskViews.today(data.tasks.filter { it.state != TaskState.DELETED }, now)

    companion object {
        const val MAX_SUBTASKS = 5
        const val LOG_SIZE = 50

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
