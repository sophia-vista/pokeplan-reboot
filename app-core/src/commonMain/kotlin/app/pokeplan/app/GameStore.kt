package app.pokeplan.app

import app.pokeplan.app.quest.Quest
import app.pokeplan.app.quest.Quests
import app.pokeplan.app.quest.Requirement
import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.model.PokemonType
import app.pokeplan.core.pokemon.CandyRules
import app.pokeplan.core.pokemon.EvolutionContext
import app.pokeplan.core.pokemon.EvolutionOption
import app.pokeplan.core.pokemon.EvolutionRules
import app.pokeplan.core.pokemon.FriendshipRules
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
import app.pokeplan.plan.PlanEvent
import app.pokeplan.plan.Milestone
import app.pokeplan.plan.MilestoneResult
import app.pokeplan.plan.Reviews
import app.pokeplan.plan.HabitTarget
import app.pokeplan.plan.Phase
import app.pokeplan.plan.Rank
import app.pokeplan.plan.Recurrence
import app.pokeplan.plan.DueReminder
import app.pokeplan.plan.ReminderOffset
import app.pokeplan.plan.Reminders
import app.pokeplan.plan.RecurringTasks
import app.pokeplan.plan.Streaks
import app.pokeplan.plan.Subtask
import app.pokeplan.plan.Task
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
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.Instant
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

    /**
     * Picks up a change another process made to the save (an Android widget). Nothing is written back, so the app never
     * overwrites a widget's tap with its older copy.
     */
    fun reload() {
        val text = storage.load() ?: return
        val fresh = try {
            json.decodeFromString(AppData.serializer(), text)
        } catch (e: Exception) {
            return
        }
        if (fresh == data) return
        data = fresh
        listeners.toList().forEach { it(data) }
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
        val before = data
        var after = change(before)
        // Anything but the account itself (and what stays on the device) changing means the cloud copy is out of date.
        val account = after.account
        if (account != null && account == before.account && after.cloudPart() != before.cloudPart()) {
            after = after.copy(account = account.copy(unsynced = true))
        }
        // A shiny stays in the Shiny Dex, and a form in the form dex, even if the Pokémon is transferred later.
        val shinyNow = after.player.pokemon.filter { it.isShiny }.map { it.speciesId }.toSet()
        if (!after.shinySpecies.containsAll(shinyNow)) after = after.copy(shinySpecies = after.shinySpecies + shinyNow)
        after = FormDex.record(after)
        data = after
        storage.save(json.encodeToString(AppData.serializer(), data))
        listeners.toList().forEach { it(data) }
    }

    // --- cloud account ------------------------------------------------------------------------------------------------

    /** Remembers a signed-in session. Progress made as a guest counts as not yet in the cloud. */
    fun signedIn(email: String, userId: String, accessToken: String, refreshToken: String, expiresAtMillis: Long) = update { d ->
        val same = d.account?.userId == userId
        d.copy(account = Account(email, userId, accessToken, refreshToken, expiresAtMillis, if (same) d.account!!.version else 0, if (same) d.account!!.unsynced else true))
    }

    fun refreshedSession(accessToken: String, refreshToken: String, expiresAtMillis: Long) =
        update { d -> d.copy(account = d.account?.copy(accessToken = accessToken, refreshToken = refreshToken, expiresAtMillis = expiresAtMillis)) }

    /**
     * Signs out. The progress on this device stays; it just stops syncing. The egg tickets were issued to the account, so
     * they go (the device id stays).
     */
    fun signOut() = update { it.copy(account = null, eggTickets = EggTickets(device = it.eggTickets.device)) }

    /** Everything PokéPlan keeps on this device, as readable JSON, without the sign-in tokens. */
    fun exportJson(): String {
        val pretty = Json(json) { prettyPrint = true }
        return pretty.encodeToString(JsonObject.serializer(), cloudDocument())
    }

    /** Erases this device's save: back to the welcome screen. A signed-in account must be signed out or deleted first. */
    fun eraseDevice() {
        check(data.account == null) { "sign out first" }
        update { AppData() }
    }

    /** This device's save as the document the cloud stores (everything but the account and what stays on the device). */
    fun cloudDocument(): JsonObject {
        val full = json.encodeToJsonElement(AppData.serializer(), data.cloudPart()) as JsonObject
        return JsonObject(full.filterKeys { it !in DEVICE_ONLY })
    }

    private fun AppData.cloudPart(): AppData = copy(account = null, eggTickets = EggTickets(), shownCalendars = emptySet())

    /** Shows or hides calendar events from this phone's calendars on the day timeline. */
    fun setShownCalendars(ids: Set<Long>) = update { it.copy(shownCalendars = ids) }

    /** The cloud accepted the document at [version]. */
    fun markSynced(version: Int) = update { d -> d.copy(account = d.account?.copy(version = version, unsynced = false, syncedAtMillis = nowMillis)) }

    /** Replaces this device's progress with the cloud's. The account stays. */
    fun adoptCloud(document: JsonObject, version: Int) {
        val incoming = json.decodeFromJsonElement(AppData.serializer(), document)
        update { d ->
            incoming.copy(
                account = d.account?.copy(version = version, unsynced = false, syncedAtMillis = nowMillis),
                eggTickets = d.eggTickets, shownCalendars = d.shownCalendars,
            )
        }
    }

    // --- egg tickets --------------------------------------------------------------------------------------------------

    /** This device's id and the spent tickets to report on the next sync. Makes the device id the first time. */
    fun ticketReport(): Pair<String, List<SpentTicket>> {
        if (data.eggTickets.device == null) update { d -> d.copy(eggTickets = d.eggTickets.copy(device = newId())) }
        return data.eggTickets.device!! to data.eggTickets.spent
    }

    /**
     * The server took the [reported] spends (accepted or refused, either way they are done) and sent the unspent [stock].
     * A ticket spent while the call was out stays spent.
     */
    fun receivedTickets(stock: List<EggTicket>, reported: List<SpentTicket>) = update { d ->
        val t = d.eggTickets
        val reportedIds = reported.map { it.id }.toSet()
        val spent = t.spent.filter { it.id !in reportedIds }
        val used = spent.map { it.id }.toSet() + reportedIds + listOfNotNull(t.inSession?.id)
        d.copy(eggTickets = t.copy(stock = stock.filter { it.id !in used }, spent = spent))
    }

    // --- time and ids -------------------------------------------------------------------------------------------------

    val nowMillis: Long get() = clock.nowMillis()
    val now: LocalDateTime get() = nowMillis.toLocalDateTime(zone)
    val today: LocalDate get() = now.date
    private val epochDay: Long get() = today.toEpochDays()

    /** The first millisecond of today in the player's time zone. */
    val startOfTodayMillis: Long get() = LocalDateTime(today, kotlinx.datetime.LocalTime(0, 0)).toEpochMillis(zone)

    /** The local day [millis] falls on (a finished task's day in the Done list). */
    fun dayOf(millis: Long): LocalDate = millis.toLocalDateTime(zone).date

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

    /** Puts an event on PokéPlan's own calendar. It never pays. */
    fun addEvent(title: String, day: LocalDate, start: LocalTime, minutes: Int = PlanEvent.DEFAULT_MINUTES): PlanEvent {
        val event = PlanEvent(newId(), title.trim(), day, start, PlanEvent.fit(start.hour * 60 + start.minute, minutes))
        update { it.copy(events = it.events + event) }
        return event
    }

    /** Changes an event's title, day, start or length; the length is kept inside the day. A blank title keeps the old one. */
    fun updateEvent(id: String, title: String? = null, day: LocalDate? = null, start: LocalTime? = null, minutes: Int? = null) = update { d ->
        d.copy(events = d.events.map { e ->
            if (e.id != id) e
            else {
                val s = start ?: e.start
                e.copy(
                    title = title?.trim()?.ifEmpty { null } ?: e.title,
                    day = day ?: e.day,
                    start = s,
                    minutes = PlanEvent.fit(s.hour * 60 + s.minute, minutes ?: e.minutes),
                )
            }
        })
    }

    fun deleteEvent(id: String) = update { d -> d.copy(events = d.events.filterNot { it.id == id }) }

    /** Sets a task's reminders (at most [Reminders.MAX_PER_TASK]). */
    fun setReminders(taskId: String, reminders: List<ReminderOffset>) =
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == taskId) it.copy(reminders = reminders.distinct().take(Reminders.MAX_PER_TASK)) else it }) }

    /** Every reminder still to come, earliest first; none when reminders are switched off. */
    fun upcomingReminders(): List<DueReminder> = data.upcomingReminders(nowMillis, zone)

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
        data.tasks, data.focusHistory, weeks, today, zone, if (data.settings.weekStartsOnMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY,
    )

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
                val event: EffortEvent = session.toEffortEvent(newId(), nowMillis, epochDay)
                // The perks that count are the ones of the buddy when the session started.
                val player = data.player
                val ticket = data.eggTickets.inSession
                val outcome = RewardEngine.effort(
                    if (player.buddyId == session.buddyIdAtStart) player else player, event, ticket?.let { Random(it.seed) } ?: rng,
                )
                update { d ->
                    log("Focus: $minutes min", outcome.rewards, d.copy(
                        eggTickets = d.eggTickets.spend(ticket, session.startedAtMillis, event.newPokemonId),
                        player = outcome.state, focus = null, activeDays = d.activeDays + today, lastFocusEnd = FocusEnd(nowMillis, true),
                        focusHistory = d.focusHistory + FocusRecord(session.startedAtMillis, minutes, true, session.taskId, session.deepFocus),
                        tasks = d.tasks.map { if (it.id == session.taskId) it.copy(focusedMinutes = it.focusedMinutes + minutes) else it },
                    ))
                }
                FocusResult.Hatched(outcome.rewards)
            }
            is Phase.Failed -> {
                val minutes = (session.focusedMillis(phase.at) / 60_000L).toInt()
                update { d ->
                    d.copy(
                        eggTickets = d.eggTickets.spend(d.eggTickets.inSession, session.startedAtMillis, null),
                        focus = null, lastFocusEnd = FocusEnd(nowMillis, false, phase.reason),
                        focusHistory = d.focusHistory + FocusRecord(session.startedAtMillis, minutes, false, session.taskId, session.deepFocus),
                    )
                }
                FocusResult.Failed(phase.reason)
            }
            else -> null
        }
    }

    /** A failed session spends its ticket too, so failing never rerolls an egg. */
    private fun EggTickets.spend(ticket: EggTicket?, startedAtMillis: Long, pokemonId: String?): EggTickets =
        if (ticket == null) copy(inSession = null) else copy(inSession = null, spent = spent + SpentTicket(ticket.id, startedAtMillis, pokemonId))

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

    /** Remembers that the player has seen what is new in [version]. */
    fun markVersionSeen(version: String) = update { it.copy(lastSeenVersion = version) }

    /** Marks species as seen in the Pokédex (a friend's buddy, a quest's Pokémon). Saves nothing when none are new. */
    fun markSeen(speciesIds: Collection<Int>) {
        if (data.seenSpecies.containsAll(speciesIds)) return
        update { it.copy(seenSpecies = it.seenSpecies + speciesIds) }
    }

    fun isFavorite(id: String): Boolean = id in data.favorites

    /** Stars or unstars a Pokémon; a starred one cannot be transferred. */
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
            ))
        }
        return true
    }

    /** Tasks finished since the day began. */
    fun doneToday(): Int = data.tasks.count { it.state == TaskState.DONE && (it.completedAtMillis ?: 0L) >= startOfTodayMillis }

    /**
     * The meaningful thing that happened in the last few minutes (an egg hatched, a task or session paid out), or null.
     * The buddy only speaks and celebrates for these; the rest of the time it just idles.
     */
    fun buddyEvent(): BuddyEventNow? {
        val recent = data.rewardLog.filter { nowMillis - it.atMillis < EVENT_MILLIS }
        val latest = recent.lastOrNull() ?: return null
        val hatches = recent.flatMap { e -> e.rewards.filterIsInstance<Reward.Hatched>() }
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
        return (p.candyProgress.toFloat() / CandyRules.candiesNeeded(p.level, partner)).coerceIn(0f, 1f)
    }

    /**
     * Pets the buddy: friendship from [FriendshipRules.Source.PET], up to [PET_DAILY_CAP] a day *(tune)*.
     * Returns the friendship gained, 0 when nothing changed (no buddy, or today's petting is used up; nothing is saved then).
     */
    fun pet(): Int {
        val buddy = data.player.buddy ?: return 0
        val key = "pet-$today"
        val gainedToday = data.counters[key] ?: 0
        if (gainedToday >= PET_DAILY_CAP || buddy.friendship >= FriendshipRules.MAX) return 0
        val petted = buddy.gainFriendship(FriendshipRules.Source.PET)
        val gained = petted.friendship - buddy.friendship
        update { d ->
            // Only today's counter is kept.
            val counters = d.counters.filterKeys { !it.startsWith("pet-") } + (key to gainedToday + gained)
            d.copy(counters = counters, player = d.player.copy(pokemon = d.player.pokemon.map { if (it.id == buddy.id) petted else it }))
        }
        return gained
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

        /** How long a reward counts as "just happened" for the buddy. */
        const val EVENT_MILLIS = 5 * 60_000L

        /** Friendship a day that petting can give. */
        const val PET_DAILY_CAP = 10
        const val LOG_SIZE = 50

        /** Parts of the save that stay on this device: never uploaded, kept when the cloud save is adopted. */
        private val DEVICE_ONLY = setOf("account", "eggTickets", "shownCalendars")

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
