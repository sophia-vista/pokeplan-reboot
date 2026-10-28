package app.pokeplan.app

import app.pokeplan.app.quest.Quest
import app.pokeplan.app.quest.Quests
import app.pokeplan.app.quest.Requirement
import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.model.Egg
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
import app.pokeplan.core.pokemon.Nature
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.pokemon.PerkBonuses
import app.pokeplan.core.pokemon.PerkSource
import app.pokeplan.core.pokemon.Perks
import app.pokeplan.core.pokemon.Stat
import app.pokeplan.core.pokemon.Transfer
import app.pokeplan.core.pokemon.TransferBlock
import app.pokeplan.core.reward.AchievementEvent
import app.pokeplan.core.reward.BefriendEvent
import app.pokeplan.core.reward.CareEvent
import app.pokeplan.core.reward.CareKind
import app.pokeplan.core.reward.EffortEvent
import app.pokeplan.core.reward.Feature
import app.pokeplan.core.reward.TrainerRank
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
import app.pokeplan.plan.EventRepeat
import app.pokeplan.plan.PlanEvent
import app.pokeplan.plan.Milestone
import app.pokeplan.plan.MiniQuest
import app.pokeplan.plan.MiniQuests
import app.pokeplan.plan.PickForMe
import app.pokeplan.plan.MilestoneResult
import app.pokeplan.plan.Reviews
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
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
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
) : World {
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

    private fun achieve(d: AppData, tier: Int, source: String, onTime: Boolean = false, focusedOnIt: Boolean = false, picked: Boolean = false): AppData {
        val firstOfDay = streakDay !in d.activeDays
        val warmKey = "warm-$streakDay"
        val warmed = d.counters[warmKey] ?: 0
        val event = AchievementEvent(
            tier = tier, region = d.settings.region, hourOfDay = now.hour, firstOfDay = firstOfDay, onTime = onTime,
            focusedOnIt = focusedOnIt, picked = picked, friendshipTasksToday = warmed, newPokemonId = newId(), nowMillis = nowMillis,
        )
        val outcome = RewardEngine.achievement(d.player, event, rng)
        // Only today's count of friendship-warming work is kept.
        val counters = d.counters.filterKeys { !it.startsWith("warm-") } + (warmKey to warmed + 1)
        return log(source, outcome.rewards, d.copy(player = outcome.state, activeDays = d.activeDays + streakDay, counters = counters))
    }

    /** The Pokémon Care's screens show: Care's lead, or the Plan buddy until Care has one (it takes Care's friendship then). */
    val careBuddy: OwnedPokemon? get() = data.player.careLead ?: data.player.buddy

    /** The day work counts for in streaks: it rolls over at 4am, so a late night still counts for the day before. */
    val streakDay: LocalDate get() = Streaks.streakDay(now)

    /**
     * Pays a Care moment into the shared world, at most [CARE_DAILY_CAP] of each kind a day whatever the Care module asks
     * (ecosystem.md, section 5: each module's share is capped). Returns null when nothing was paid (not started, or capped).
     * The log says only "Care" and the kind, never what the routine was.
     */
    override fun care(event: CareEvent): List<Reward>? {
        if (!data.onboarded) return null
        val key = "care-${event.kind.name}-$today"
        val paidToday = data.counters[key] ?: 0
        if (paidToday >= CARE_DAILY_CAP.getValue(event.kind)) return null
        val outcome = RewardEngine.care(data.player, event, rng)
        update { d ->
            // Only today's counters are kept.
            val counters = d.counters.filterKeys { !it.startsWith("care-") || it.endsWith("-$today") } + (key to paidToday + 1)
            log(CARE_SOURCES.getValue(event.kind), outcome.rewards, d.copy(player = outcome.state, counters = counters))
        }
        return outcome.rewards
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
        val key = "befriend-$today"
        val made = data.counters[key] ?: 0
        if (made >= BEFRIEND_DAILY_CAP) return null
        val ticket = data.eggTickets.stock.firstOrNull()
        val outcome = RewardEngine.befriend(data.player, event, ticket?.let { Random(it.seed) } ?: rng)
        update { d ->
            val counters = d.counters.filterKeys { !it.startsWith("befriend-") } + (key to made + 1)
            val tickets = if (ticket == null) d.eggTickets
            else d.eggTickets.copy(stock = d.eggTickets.stock.drop(1), spent = d.eggTickets.spent + SpentTicket(ticket.id, nowMillis, event.newPokemonId))
            log("Care: made a friend", outcome.rewards, d.copy(player = outcome.state, counters = counters, eggTickets = tickets))
        }
        return outcome.rewards
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
    ): Task {
        val task = Task(
            id = newId(), title = title.trim(), notes = notes, rank = rank, category = category, planned = planned, due = due,
            estimateMinutes = estimateMinutes, createdAtMillis = nowMillis, listId = listId?.takeIf { id -> data.lists.any { it.id == id } },
        )
        update { it.copy(tasks = it.tasks + task) }
        return task
    }

    fun updateTask(task: Task) = update { d -> d.copy(tasks = d.tasks.map { if (it.id == task.id) task else it }) }

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

    /** Puts an event on PokéPlan's own calendar, repeating when [repeat] is set. It never pays. */
    fun addEvent(
        title: String, day: LocalDate, start: LocalTime, minutes: Int = PlanEvent.DEFAULT_MINUTES, color: Long? = null,
        repeat: EventRepeat? = null,
    ): PlanEvent {
        val event = PlanEvent(newId(), title.trim(), day, start, PlanEvent.fit(start.hour * 60 + start.minute, minutes), color = color, repeat = repeat)
        update { it.copy(events = it.events + event) }
        return event
    }

    /** Puts an all-day event on PokéPlan's own calendar, from [first] through [last] ("Home" for a weekend, "WFH" for a day). */
    fun addAllDayEvent(title: String, first: LocalDate, last: LocalDate = first, color: Long? = null, repeat: EventRepeat? = null): PlanEvent {
        val event = PlanEvent(
            newId(), title.trim(), first, LocalTime(0, 0), allDay = true,
            lastDay = maxOf(first, last).takeIf { it > first }, color = color, repeat = repeat,
        )
        update { it.copy(events = it.events + event) }
        return event
    }

    /**
     * Changes an event's title, day, start, length, last day (all-day), colour, or whether it is all-day; the length is kept
     * inside the day. A blank title keeps the old one. Moving an all-day event's first day moves its last day with it.
     *
     * [id] may be an occurrence of a repeating event: the change is then made to the whole series, and moving the
     * occurrence's [day] moves every one (with the series' end and skipped days) by as many days. Returns the id the
     * same occurrence has afterwards.
     */
    fun updateEvent(
        id: String, title: String? = null, day: LocalDate? = null, start: LocalTime? = null, minutes: Int? = null,
        lastDay: LocalDate? = null, color: Long? = null, allDay: Boolean? = null,
    ): String {
        val seriesId = PlanEvent.seriesOf(id)
        val at = PlanEvent.occurrenceDate(id)
        var shown = id
        update { d ->
            d.copy(events = d.events.map { e ->
                if (e.id != seriesId) e
                else {
                    val from = at ?: e.day
                    val to = day ?: from
                    val shift = from.daysUntil(to)
                    val s = start ?: e.start
                    val first = e.day.plusDays(shift)
                    val whole = allDay ?: e.allDay
                    val last = when {
                        !whole -> null
                        lastDay != null -> first.plusDays(to.daysUntil(lastDay))
                        else -> e.lastDay?.plusDays(shift)
                    }
                    if (at != null) shown = PlanEvent.occurrenceId(seriesId, to)
                    e.copy(
                        title = title?.trim()?.ifEmpty { null } ?: e.title,
                        day = first,
                        start = s,
                        minutes = PlanEvent.fit(s.hour * 60 + s.minute, minutes ?: e.minutes),
                        allDay = whole,
                        lastDay = last?.takeIf { it > first },
                        color = color ?: e.color,
                        until = e.until?.plusDays(shift),
                        skipped = e.skipped.map { it.plusDays(shift) }.toSet(),
                    )
                }
            })
        }
        return shown
    }

    /**
     * Moves an event to [day] at [start], as dragging it does. An occurrence of a repeating event moves on its own: it
     * is left out of the series and becomes a one-off event there. Returns the id of the moved event.
     */
    fun moveEvent(id: String, day: LocalDate, start: LocalTime): String {
        val at = PlanEvent.occurrenceDate(id) ?: return updateEvent(id, day = day, start = start)
        val series = data.events.firstOrNull { it.id == PlanEvent.seriesOf(id) } ?: return id
        val single = PlanEvent(
            newId(), series.title, day, if (series.allDay) series.start else start, series.minutes, series.allDay,
            series.lastDay?.let { day.plusDays(series.spanDays) }, series.color,
        ).let { it.copy(minutes = PlanEvent.fit(it.startMinute, it.minutes)) }
        update { d -> d.copy(events = d.events.map { if (it.id == series.id) it.copy(skipped = it.skipped + at) else it } + single) }
        return single.id
    }

    /** Deletes an event; for an occurrence of a repeating event, every one of them. */
    fun deleteEvent(id: String) = update { d -> d.copy(events = d.events.filterNot { it.id == PlanEvent.seriesOf(id) }) }

    /** Deletes just this occurrence of a repeating event (any other event is deleted). */
    fun deleteOccurrence(id: String) {
        val at = PlanEvent.occurrenceDate(id) ?: return deleteEvent(id)
        update { d ->
            d.copy(events = d.events.mapNotNull { e ->
                if (e.id != PlanEvent.seriesOf(id)) e
                else e.copy(skipped = e.skipped + at).takeUnless { left -> left.until?.let { left.startDates(left.day, it).isEmpty() } == true }
            })
        }
    }

    /**
     * Sets how an event repeats (null: it happens once), from the one shown as [id] on. When [id] is a later occurrence
     * of a repeating event, the ones before it stay as they were: the series ends the day before and a new one starts
     * there, so only it and the ones after follow the new rule (a one-off for null). Returns the id it has afterwards.
     */
    fun repeatEvent(id: String, repeat: EventRepeat?): String {
        val seriesId = PlanEvent.seriesOf(id)
        val series = data.events.firstOrNull { it.id == seriesId } ?: return id
        if (repeat == series.repeat) return id
        val at = PlanEvent.occurrenceDate(id) ?: series.day
        val shownId = { e: PlanEvent -> if (repeat == null) e.id else PlanEvent.occurrenceId(e.id, at) }
        if (at == series.day) {
            val changed = series.copy(repeat = repeat, until = series.until.takeIf { repeat != null }, skipped = series.skipped.takeIf { repeat != null }.orEmpty())
            update { d -> d.copy(events = d.events.map { if (it.id == seriesId) changed else it }) }
            return shownId(changed)
        }
        val next = series.copy(
            id = newId(), day = at, lastDay = series.lastDay?.let { at.plusDays(series.spanDays) }, repeat = repeat,
            until = series.until.takeIf { repeat != null }, skipped = series.skipped.filter { repeat != null && it > at }.toSet(),
        )
        val before = series.copy(until = at.plusDays(-1)).takeIf { it.startDates(it.day, at.plusDays(-1)).isNotEmpty() }
        update { d -> d.copy(events = d.events.flatMap { if (it.id == seriesId) listOfNotNull(before, next) else listOf(it) }) }
        return shownId(next)
    }

    private fun LocalDate.plusDays(n: Int): LocalDate = plus(DatePeriod(days = n))

    /** Sets the last day a repeating event can happen on (null: it never ends). */
    fun endEventOn(id: String, until: LocalDate?) = update { d ->
        d.copy(events = d.events.map { if (it.id == PlanEvent.seriesOf(id)) it.copy(until = until?.let { u -> maxOf(u, it.day) }) else it })
    }

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
        val existing = seriesOf(task)
        if (existing != null) {
            val id = existing.template.seriesId!!
            update { d ->
                d.copy(
                    series = d.series.map { if (it.template.seriesId == id) it.copy(recurrence = it.recurrence.copy(repeat = repeat)) else it },
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
        val created = data.series.flatMap { RecurringTasks.instancesToCreate(it.template, it.recurrence, data.tasks, today, nowMillis) }
        if (created.isNotEmpty()) update { it.copy(tasks = it.tasks + created) }
    }

    private fun settlePending() {
        val due = data.pending.filter { nowMillis >= it.finalAtMillis }
        if (due.isEmpty()) return
        update { d ->
            var next = d.copy(pending = d.pending - due.toSet())
            for (p in due) {
                val picked = p.done.id == next.pickedTaskId
                if (p.pays) next = achieve(next, p.tier, "Task: ${p.done.title}", p.onTime, p.focusedOnIt, picked)
                if (picked) next = next.copy(pickedTaskId = null)
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
            val next = d.copy(habits = d.habits.map { if (it.id == id) it.complete(date).let { h -> if (pays) h.paid(date) else h } else it })
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
            if (result.pays) next = next.copy(counters = next.counters + (key to ((next.counters[key] ?: 0) + 1)), activeDays = next.activeDays + streakDay)
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
    // --- nudges: Pick for me, mini-quests, welcome back ---------------------------------------------------------------

    /**
     * "Pick for me": chooses one of today's open tasks at random (another one than the current pick when there is a choice)
     * and remembers it, so finishing it pays [RewardEngine.PICKED_BONUS] on its candies. Returns null when nothing is open.
     */
    fun pickForMe(): Task? {
        val open = todayTasks().filter { it.isOpen && it.id !in data.pending.map { p -> p.original.id } }
        val pick = PickForMe.choose(open.filter { it.id != data.pickedTaskId }.ifEmpty { open }, rng)?.task ?: return null
        update { it.copy(pickedTaskId = pick.id) }
        return pick
    }

    /** Today's three mini-quests (the same for everyone on a date, until rerolled). */
    fun miniQuests(): List<MiniQuest> = MiniQuests.forDay(today, rerolled = "mq-reroll-$today" in data.counters)

    fun miniQuestDone(id: String): Boolean = "mq-$today-$id" in data.counters

    /** One reroll a day, before any of the day's quests is done. */
    fun canRerollMiniQuests(): Boolean = "mq-reroll-$today" !in data.counters && miniQuests().none { miniQuestDone(it.id) }

    fun rerollMiniQuests() {
        if (!canRerollMiniQuests()) return
        update { d -> d.copy(counters = d.counters.filterKeys { !it.startsWith("mq-") } + ("mq-reroll-$today" to 1)) }
    }

    /**
     * Ticks off one of today's mini-quests (honour system): a berry, and [MiniQuests.ALL_THREE_BONUS_BERRIES] more for the
     * third. No streak and no active day: they are small kindnesses, not work. Returns false if it is not today's or done.
     */
    fun completeMiniQuest(id: String): Boolean {
        val quests = miniQuests()
        if (quests.none { it.id == id } || miniQuestDone(id)) return false
        val berry = Items.berry(Items.flavors.values.toList()[rng.nextInt(Items.flavors.size)])
        val allDone = quests.all { it.id == id || miniQuestDone(it.id) }
        val berries = 1 + if (allDone) MiniQuests.ALL_THREE_BONUS_BERRIES else 0
        update { d ->
            // Only today's mini-quest marks are kept.
            val counters = d.counters.filterKeys { !it.startsWith("mq-") || it.contains("-$today") } + ("mq-$today-$id" to 1)
            val (bag, overflow) = d.player.inventory.addCapped(berry, berries)
            log("Mini-quest", listOf(Reward.ItemDrop(berry, berries)), d.copy(
                counters = counters, player = d.player.copy(inventory = bag, coins = d.player.coins + overflow),
            ))
        }
        return true
    }

    /** The oldest open task: what "open the task you've been avoiding" starts 5 minutes of focus on. */
    fun oldestOpenTask(): Task? = data.tasks.filter { it.isOpen && it.seriesId == null }.minByOrNull { it.createdAtMillis }

    private val lastActive: LocalDate? get() = data.activeDays.maxOrNull()

    /** True after [Streaks.WELCOME_BACK_DAYS] or more days away, until the gift is taken (once per absence). */
    fun welcomeBackDue(): Boolean {
        val last = lastActive ?: return false
        return Streaks.welcomeBack(last, streakDay) && "welcome-$last" !in data.counters
    }

    /** Takes the welcome-back gift: [WELCOME_BACK_RARE_CANDIES] Rare Candies and [WELCOME_BACK_BERRIES] berries, no scolding. */
    fun claimWelcomeBack(): List<Reward> {
        if (!welcomeBackDue()) return emptyList()
        val last = lastActive!!
        val berry = Items.berry(Items.flavors.values.toList()[rng.nextInt(Items.flavors.size)])
        val rewards = listOf(Reward.ItemDrop(Items.RARE_CANDY, WELCOME_BACK_RARE_CANDIES), Reward.ItemDrop(berry, WELCOME_BACK_BERRIES))
        update { d ->
            var bag = d.player.inventory
            var coins = d.player.coins
            listOf(Items.RARE_CANDY to WELCOME_BACK_RARE_CANDIES, berry to WELCOME_BACK_BERRIES).forEach { (item, n) ->
                bag.addCapped(item, n).let { (b, over) -> bag = b; coins += over }
            }
            val counters = d.counters.filterKeys { !it.startsWith("welcome-") } + ("welcome-$last" to 1)
            log("Welcome back", rewards, d.copy(counters = counters, player = d.player.copy(inventory = bag, coins = coins)))
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

    val streak: Streaks.Result get() = Streaks.daily(data.activeDays, data.restDays, streakDay, perks.extraForgivenDays, firstDayOfWeek)

    val firstDayOfWeek: DayOfWeek get() = if (data.settings.weekStartsOnMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY

    /** The perks of the buddy, its party and the slotted Guardians as they stand now (no task or session in view). */
    val perks: PerkBonuses get() = data.player.let { Perks.total(it.buddy, it.party, guardians = it.guardianPokemon) }

    /** Where [perks] come from, part by part, for the perks panel. */
    val perkSources: List<PerkSource> get() = data.player.let { Perks.sources(it.buddy, it.party, it.guardianPokemon) }

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
                val outcome = RewardEngine.effort(player.atStartOf(session), event, ticket?.let { Random(it.seed) } ?: rng)
                update { d ->
                    log("Focus: $minutes min", outcome.rewards, d.copy(
                        eggTickets = d.eggTickets.spend(ticket, session.startedAtMillis, event.newPokemonId),
                        player = outcome.state.copy(buddyId = d.player.buddyId), focus = null, activeDays = d.activeDays + streakDay, lastFocusEnd = FocusEnd(nowMillis, true),
                        focusHistory = d.focusHistory + FocusRecord(session.startedAtMillis, minutes, true, session.taskId, session.deepFocus),
                        tasks = d.tasks.map { if (it.id == session.taskId) it.copy(focusedMinutes = it.focusedMinutes + minutes) else it },
                    ))
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
                        focusHistory = d.focusHistory + FocusRecord(session.startedAtMillis, minutes, false, session.taskId, session.deepFocus),
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
        val key = "magic-guard-$weekStart"
        if (minutes < Egg.MIN_MINUTES || key in data.counters) return null
        if (!Perks.total(player.buddy, player.party, guardians = player.guardianPokemon).keepsFailedEgg) return null
        val ticket = data.eggTickets.inSession
        val event = EffortEvent(minutes, false, session.region, session.lure, epochDay, newId(), nowMillis)
        val outcome = RewardEngine.effort(player, event, ticket?.let { Random(it.seed) } ?: rng)
        update { d ->
            log("Magic Guard: $minutes min", outcome.rewards, d.copy(
                eggTickets = d.eggTickets.spend(ticket, session.startedAtMillis, event.newPokemonId),
                player = outcome.state.copy(buddyId = d.player.buddyId), focus = null,
                lastFocusEnd = FocusEnd(nowMillis, true, reason, guarded = true),
                focusHistory = d.focusHistory + FocusRecord(session.startedAtMillis, minutes, false, session.taskId, session.deepFocus),
                counters = d.counters.filterKeys { !it.startsWith("magic-guard-") } + (key to 1),
            ))
        }
        return FocusResult.Hatched(outcome.rewards)
    }

    /** The perks that count for a session are the ones of the buddy when it started, if that Pokémon is still here. */
    private fun app.pokeplan.core.reward.PlayerState.atStartOf(session: FocusSession) =
        if (session.buddyIdAtStart != null && pokemon.any { it.id == session.buddyIdAtStart }) copy(buddyId = session.buddyIdAtStart) else this

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

    /**
     * Puts a Pokémon in or out of a module's party (up to six; one party at a time, so joining another moves it). Returns
     * false if the party is full, or it would leave Plan's party empty or take the buddy out of it.
     */
    fun setInParty(id: String, inParty: Boolean, partyId: String = PartyIds.PLAN): Boolean {
        val p = pokemon(id)
        if (inParty && Guardians.canBe(p)) return false
        val planSize = data.player.party.size
        val staying = inParty && p.inParty && p.partyId == partyId
        if (inParty && !staying && data.player.partyOf(partyId).size >= OwnedPokemon.PARTY_SIZE) return false
        val leavingPlan = p.inParty && p.partyId == PartyIds.PLAN && !(inParty && partyId == PartyIds.PLAN)
        if (leavingPlan && (planSize <= 1 || id == data.player.buddyId)) return false
        update { d ->
            val pokemon = d.player.pokemon.map { if (it.id == id) p.copy(inParty = inParty, partyId = partyId) else it }
            // Care's lead is its first member until the player picks another, and passes on when it leaves.
            val care = pokemon.filter { it.inParty && it.partyId == PartyIds.CARE }
            val lead = d.player.careLeadId?.takeIf { l -> care.any { it.id == l } } ?: care.firstOrNull()?.id
            d.copy(player = d.player.copy(pokemon = pokemon, careLeadId = lead))
        }
        return true
    }

    /** Makes [id], who must be in Care's party, its lead: the buddy that takes Care's friendship and gives its perks. */
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
            Transfer.block(p, id == data.player.buddyId || id == data.player.careLead?.id, if (p.partyId == PartyIds.PLAN) data.player.party.size else OwnedPokemon.PARTY_SIZE)
        }

    /** Whether [feature] has appeared yet at the player's trainer rank (the gradual reveal). */
    fun sees(feature: Feature): Boolean = TrainerRank.isVisible(feature, data.player.trainerPoints)

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
    fun buddyEvent(care: Boolean = false): BuddyEventNow? {
        // On Care's screens only Care's own moments count (a routine, a calm break, a skill, a friend made).
        val recent = data.rewardLog.filter { nowMillis - it.atMillis < EVENT_MILLIS && (!care || it.source.startsWith(CARE_LOG_PREFIX)) }
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
     * Pets the buddy, or [pokemonId] (Care's lead on Care's screens): friendship from [FriendshipRules.Source.PET], up to [PET_DAILY_CAP] a day *(tune)*, shared by all.
     * Returns the friendship gained, 0 when nothing changed (no buddy, or today's petting is used up; nothing is saved then).
     */
    fun pet(pokemonId: String? = null): Int {
        val buddy = (if (pokemonId == null) data.player.buddy else data.player.pokemon.firstOrNull { it.id == pokemonId }) ?: return 0
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
    fun canUse(itemId: String, p: OwnedPokemon): Boolean = when {
        itemId in Items.vitamins || Items.category(itemId) == ItemCategory.BERRY -> true
        itemId == Items.ABILITY_CAPSULE -> p.form.abilities.size == 2 && p.ability in p.form.abilities
        itemId == Items.ABILITY_PATCH -> p.form.hiddenAbility != null && p.ability != p.form.hiddenAbility
        itemId == Items.BOTTLE_CAP -> p.ivs.values.any { it < app.pokeplan.core.pokemon.StatCalculator.MAX_IV }
        itemId == Items.NATURE_MINT || itemId.startsWith("tera-shard-") -> true
        else -> false
    }

    /**
     * Uses a bag item on a Pokémon: vitamins, berries, the Ability Capsule and Patch, and with a [choice] the Bottle Cap
     * (a [Stat]), the Nature Mint (a [Nature]). A Tera Shard sets its own type. Returns false if there is none or it does
     * nothing.
     */
    fun useItem(itemId: String, pokemonId: String, choice: Any? = null): Boolean {
        if (!data.player.inventory.has(itemId)) return false
        val p = pokemon(pokemonId)
        if (!canUse(itemId, p)) return false
        val changed = runCatching {
            when {
                itemId in Items.vitamins -> ItemEffects.useVitamin(p, itemId)
                Items.category(itemId) == ItemCategory.BERRY -> ItemEffects.feedBerry(p)
                itemId == Items.ABILITY_CAPSULE -> ItemEffects.useAbilityCapsule(p)
                itemId == Items.ABILITY_PATCH -> ItemEffects.useAbilityPatch(p)
                itemId == Items.BOTTLE_CAP -> ItemEffects.useBottleCap(p, choice as? Stat ?: return false)
                itemId == Items.NATURE_MINT -> ItemEffects.useNatureMint(p, choice as? Nature ?: return false)
                itemId.startsWith("tera-shard-") -> {
                    val type = PokemonType.entries.first { Items.teraShard(it) == itemId }
                    ItemEffects.useTeraShard(p, type)
                }
                else -> return false
            }
        }.getOrElse { return false }
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
        /** The welcome-back gift after a week or more away. *(tune)* */
        const val WELCOME_BACK_RARE_CANDIES = 5
        const val WELCOME_BACK_BERRIES = 2

        const val MAX_SUBTASKS = 5

        /** How long a reward counts as "just happened" for the buddy. */
        const val EVENT_MILLIS = 5 * 60_000L

        /** Friendship a day that petting can give. */
        const val PET_DAILY_CAP = 10
        const val LOG_SIZE = 50

        /** Friends Care can make a day, one per visitor in the meadow (`Visitors.MAX_VISITORS`). *(tune)* */
        const val BEFRIEND_DAILY_CAP = 4

        /** The most of each Care kind the world pays a day *(tune)*, a backstop to the same limits in CareRules. */
        val CARE_DAILY_CAP: Map<CareKind, Int> = mapOf(CareKind.ROUTINE to 5, CareKind.CALM_BREAK to 3, CareKind.SKILL to 3, CareKind.WEEKLY_RHYTHM to 1)
        /** Every Care entry in the reward log starts with this (the kind of moment only, never what it was). */
        const val CARE_LOG_PREFIX = "Care: "

        private val CARE_SOURCES = mapOf(
            CareKind.ROUTINE to "Care: a routine", CareKind.CALM_BREAK to "Care: a calm break", CareKind.WEEKLY_RHYTHM to "Care: kept a rhythm", CareKind.SKILL to "Care: a skill practised",
        )

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
