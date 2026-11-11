package app.pokeplan.app

import app.pokeplan.care.BefriendResult
import app.pokeplan.care.CalmBreak
import app.pokeplan.care.CareAction
import app.pokeplan.care.CareData
import app.pokeplan.care.CarePlay
import app.pokeplan.care.Friend
import app.pokeplan.care.PlayActivity
import app.pokeplan.care.PlayReaction
import app.pokeplan.care.Reunion
import app.pokeplan.care.Reunions
import app.pokeplan.care.CareRules
import app.pokeplan.care.CareSettings
import app.pokeplan.care.CareTrends
import app.pokeplan.care.DiaryCard
import app.pokeplan.care.Emotion
import app.pokeplan.care.JournalEntry
import app.pokeplan.care.PetSpot
import app.pokeplan.care.Prompt
import app.pokeplan.care.Prompts
import app.pokeplan.care.Reaction
import app.pokeplan.care.Routine
import app.pokeplan.care.SkillPractice
import app.pokeplan.care.Visitor
import app.pokeplan.care.Visit
import app.pokeplan.care.VisitStep
import app.pokeplan.care.Visitors
import app.pokeplan.care.Skills
import app.pokeplan.core.reward.BefriendEvent
import app.pokeplan.core.reward.CareEvent
import app.pokeplan.core.reward.CareKind
import app.pokeplan.core.reward.Reward
import app.pokeplan.core.reward.RewardEngine
import app.pokeplan.plan.toLocalDateTime
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.random.Random

/**
 * The Care module on this device (docs/ecosystem.md, section 6): its own save in its own [storage] (`care.json`), apart
 * from Plan's. It reaches the shared world only through [world], with events that carry no text, mood or health value.
 * Doing earns (a routine, a calm break, a practised skill, a week with a rhythm); entering anything sensitive (a diary card,
 * a note, a journal entry) never does.
 */
class CareStore(
    storage: Storage,
    private val clock: Clock,
    private val world: World,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val weekStart: () -> DayOfWeek = { DayOfWeek.MONDAY },
    private val rng: Random = Random,
) {
    private val document = SavedDocument(storage, CareData.serializer(), json, tidy = { tidy(it) }) { CareData() }

    val data: CareData get() = document.value

    fun observe(listener: (CareData) -> Unit): () -> Unit = document.observe(listener)

    private val nowMillis: Long get() = clock.nowMillis()
    val today: LocalDate get() = nowMillis.toLocalDateTime(zone).date
    private val thisWeek: LocalDate get() = CareRules.weekStartOf(today, weekStart())

    /** Picks up a change another process made (a widget), without writing anything back; see [SavedDocument.reload]. */
    fun reload() = document.reload()

    /** Picks up what another store in this process (a widget, a notification) wrote; cheap when nothing was. */
    fun sync() = document.sync()

    private fun update(change: (CareData) -> CareData) = document.update(change)

    private fun newId(): String = newId(nowMillis, rng)

    private fun pay(kind: CareKind): List<Reward>? = world.care(CareEvent(kind, nowMillis))

    /** Pays the week's rhythm milestone if this moment made it. */
    private fun rhythm(): List<Reward> {
        if (!CareRules.rhythmDue(data, thisWeek)) return emptyList()
        val rewards = pay(CareKind.WEEKLY_RHYTHM) ?: return emptyList()
        update { it.copy(rhythmWeeks = it.rhythmWeeks + thisWeek) }
        return rewards
    }

    // --- routines -----------------------------------------------------------------------------------------------------

    fun addRoutine(title: String, time: LocalTime? = null, days: Set<DayOfWeek> = emptySet(), icon: String? = null): Routine {
        val routine = Routine(newId(), title.trim(), icon, time, days, today)
        update { it.copy(routines = it.routines + routine) }
        return routine
    }

    fun updateRoutine(routine: Routine) = update { d -> d.copy(routines = d.routines.map { if (it.id == routine.id) routine else it }) }

    fun archiveRoutine(id: String, archived: Boolean = true) =
        update { d -> d.copy(routines = d.routines.map { if (it.id == id) it.copy(archived = archived) else it }) }

    fun deleteRoutine(id: String) = update { d -> d.copy(routines = d.routines.filterNot { it.id == id }) }

    /** Today's routines, the ones with a time first, in time order. */
    fun todayRoutines(): List<Routine> =
        data.routines.filter { it.isDueOn(today) }.sortedWith(compareBy(nullsLast()) { it.time })

    /**
     * Ticks a routine off for today. The first tick of the day pays, up to [CareRules.ROUTINES_PAID_PER_DAY] routines a
     * day; ticking again after an undo never pays twice. Returns what it earned (empty when nothing).
     */
    fun completeRoutine(id: String): List<Reward> {
        val routine = data.routines.first { it.id == id }
        if (routine.doneOn(today)) return emptyList()
        val canPay = routine.paysOn(today) && CareRules.routinesPaidOn(data, today) < CareRules.ROUTINES_PAID_PER_DAY
        update { d -> d.copy(routines = d.routines.map { if (it.id == id) it.complete(today) else it }) }
        val rewards = if (canPay) pay(CareKind.ROUTINE) else null
        if (rewards != null) update { d -> d.copy(routines = d.routines.map { if (it.id == id) it.paid(today) else it }) }
        return rewards.orEmpty() + rhythm()
    }

    fun undoRoutine(id: String) = update { d -> d.copy(routines = d.routines.map { if (it.id == id) it.undo(today) else it }) }

    // --- calm breaks --------------------------------------------------------------------------------------------------

    /**
     * Records a finished calm break of [seconds]. One of at least [CareRules.BREAK_MIN_SECONDS] pays, up to
     * [CareRules.BREAKS_PAID_PER_DAY] a day. Returns what it earned.
     */
    fun finishCalmBreak(seconds: Int): List<Reward> {
        val length = seconds.coerceIn(0, CareRules.BREAK_MAX_SECONDS)
        val canPay = length >= CareRules.BREAK_MIN_SECONDS && CareRules.breaksPaidOn(data, today) < CareRules.BREAKS_PAID_PER_DAY
        val rewards = if (canPay) pay(CareKind.CALM_BREAK) else null
        update { it.copy(breaks = it.breaks + CalmBreak(nowMillis, today, length, paid = rewards != null)) }
        return rewards.orEmpty() + rhythm()
    }

    // --- skills (practising pays) -------------------------------------------------------------------------------------

    /**
     * Records a practised skill from the library. The first practice of a skill each day pays, up to
     * [CareRules.SKILLS_PAID_PER_DAY] skills a day; paced breathing is practised as a calm break instead. Returns what it earned.
     */
    fun practiseSkill(skillId: String): List<Reward> {
        val skill = Skills.byId(skillId) ?: return emptyList()
        if (skill.calmBreak) return emptyList()
        val rewards = if (CareRules.skillPaysOn(data, skillId, today)) pay(CareKind.SKILL) else null
        update { it.copy(practices = it.practices + SkillPractice(nowMillis, today, skillId, paid = rewards != null)) }
        return rewards.orEmpty() + rhythm()
    }

    /** The skills practised today, by id (calm breaks count as paced breathing). */
    fun practisedToday(): Set<String> =
        data.practices.filter { it.day == today }.map { it.skillId }.toSet() +
            (if (data.breaks.any { it.day == today }) Skills.all.filter { it.calmBreak }.map { it.id } else emptyList())

    // --- visitors (befriending) ---------------------------------------------------------------------------------------
    // docs/pokelife MODULES/CARE.md, section 6: visitors arrive on their own (never because of what was done in Care), four are in the
    // meadow and the rest wait in a queue that never expires, a visitor can be visited at any time, every kind of care warms it at once
    // and nothing is lost by being away. Visitors stay until they are befriended or sent away.

    /** The berries Care can offer as a treat (from the shared bag). */
    fun berries(): Map<String, Int> = world.berries()

    /** Whether [visitor] is resting from a visit just over: it can still be played with, but another visit would not count yet. */
    fun resting(visitor: Visitor): Boolean = Visitors.resting(visitor, nowMillis)

    /** Whether [visitor] can be visited now: it is in the meadow, not resting, and no other visit is on. */
    fun canVisit(visitor: Visitor): Boolean =
        Visitors.meadow(data.visitors).any { it.id == visitor.id } && !resting(visitor) && (visit == null || visit?.speciesId == visitor.speciesId)

    /**
     * The visitors here (the first [Visitors.MAX_VISITORS] are in the meadow, the rest in the queue). The first time it is called
     * on a new day the arrivals since the last are added, up to the room there is; the same on this device all day, so the day's
     * arrivals are saved the first time they are seen.
     */
    fun visitors(): List<Visitor> {
        val last = data.visitorsDay
        if (last == null || today > last) {
            val seed = data.visitorSeed.takeIf { it != 0 } ?: (1 + rng.nextInt(Int.MAX_VALUE - 1))
            val room = Visitors.MAX_VISITORS + Visitors.MAX_QUEUE - data.visitors.size
            val arrivals = Visitors.arrivals(
                seed, last, today, room, data.visitors.map { it.speciesId }.toSet(), world.visitorLuck(), Visitors.lean(data, today),
            )
            update { it.copy(visitorSeed = seed, visitorsDay = today, visitors = it.visitors + arrivals) }
        }
        return data.visitors
    }

    /** The visitors in the meadow, and the ones waiting for a place. */
    fun meadow(): List<Visitor> = Visitors.meadow(visitors())
    fun queued(): List<Visitor> = Visitors.queue(visitors())

    /** Visitors sent away and not asked back, the newest first. */
    fun dismissed(): List<Visitor> = data.dismissed.asReversed()

    /** The visit in progress, if there is one. It stays until it is finished or left. */
    val visit: Visit? get() = data.visit

    /**
     * Starts a visit with a visitor. Null when it cannot be done now: the visitor is gone, in the queue, resting, or another visit
     * is on. A visit already started with that visitor carries on. No other care has to have been done first, and there is no
     * limit to the visits in a day.
     */
    fun startVisit(speciesId: Int): Visit? {
        visit?.takeIf { it.speciesId == speciesId }?.let { return it }
        val visitor = visitors().firstOrNull { it.speciesId == speciesId } ?: return null
        if (!canVisit(visitor)) return null
        return Visitors.start(speciesId, today).also { v -> update { it.copy(visit = v) } }
    }

    /**
     * Does [action] in the visit. What it warms the visitor is added to its progress at once, so leaving never loses it. A treat
     * ([CareAction.GIFT]) can be a [berry] from the shared bag, spent at once; the berry the visitor likes adds a bonus once. The
     * visit ends (see [leaveVisit]) when every kind of care is done or the visitor is warm enough to be a friend. Null when there is
     * no visit to do it in.
     */
    fun visitAct(action: CareAction, spot: PetSpot? = null, berry: String? = null): VisitStep? {
        val visit = visit ?: return null
        val visitor = visitors().firstOrNull { it.speciesId == visit.speciesId } ?: return null
        val (acted, reaction) = Visitors.act(visitor, visit, action, spot)
        // Only a treat the visitor takes (not a repeat) uses up the berry.
        val given = berry?.takeIf { action == CareAction.GIFT && reaction != Reaction.FLAT && visit.berry == null && world.spendBerry(it) }
        val next = if (given != null) acted.copy(berry = given) else acted
        val bonus = given != null && Visitors.berryBonusDue(visitor, given)
        val warmer = visitor.copy(
            progress = visitor.progress + (next.warmth - visit.warmth) + if (bonus) Visitors.BERRY_BONUS else 0,
            berryBonus = visitor.berryBonus || bonus,
        )
        update { d -> d.copy(visit = next, visitors = d.visitors.map { if (it.id == warmer.id) warmer else it }) }
        return VisitStep(reaction, next, if (Visitors.finished(next) || Visitors.friend(warmer)) leaveVisit() else null)
    }

    /** Turns toward the visitor's bid, if it made one: one more warmth. The step has a result when that made it a friend. */
    fun visitNotice(): VisitStep? {
        val visit = visit ?: return null
        if (visit.pendingBid == null) return null
        val visitor = visitors().firstOrNull { it.speciesId == visit.speciesId } ?: return null
        val next = Visitors.notice(visit)
        val warmer = visitor.copy(progress = visitor.progress + (next.warmth - visit.warmth))
        update { d -> d.copy(visit = next, visitors = d.visitors.map { if (it.id == warmer.id) warmer else it }) }
        return VisitStep(Reaction.LIKED, next, if (Visitors.friend(warmer)) leaveVisit() else null)
    }

    /**
     * Ends the visit now, whenever the player likes: a visitor that has become a friend is paid through the world (usually an
     * egg of its family's first stage that hatches at once, rarely the visitor itself) and leaves; any other rests a while. What
     * the visit warmed is already kept. A friend the world cannot pay today ([BefriendResult.waiting]) stays, ready. Null when there is no visit.
     */
    fun leaveVisit(): BefriendResult? {
        val visit = visit ?: return null
        val visitor = data.visitors.firstOrNull { it.speciesId == visit.speciesId }
        if (visitor == null) { update { it.copy(visit = null) }; return null }
        if (!Visitors.friend(visitor)) {
            val rested = visitor.copy(restUntilMillis = nowMillis + Visitors.REST_MILLIS)
            update { d -> d.copy(visit = null, met = d.met + visit.speciesId, visitors = d.visitors.map { if (it.id == rested.id) rested else it }) }
            return BefriendResult(rested, friend = false, rewards = emptyList())
        }
        val rewards = world.befriend(BefriendEvent(visitor.speciesId, visitor.formIdentifier, newId(), nowMillis, visitor.id))
        if (rewards == null) {
            // The world paid nothing now (its daily limit of friends): the visit still ends, and the visitor stays, ready, for the next one.
            update { d -> d.copy(visit = null, met = d.met + visit.speciesId) }
            return BefriendResult(visitor, friend = false, rewards = emptyList(), waiting = true)
        }
        // The visitor itself joins with the high friendship only a direct join starts with; an egg starts low.
        val joinedNow = rewards.filterIsInstance<Reward.Hatched>().any { it.pokemon.speciesId == visitor.speciesId && it.pokemon.friendship >= RewardEngine.BEFRIEND_JOIN_FRIENDSHIP }
        val friend = Friend(visitor.speciesId, visitor.formIdentifier, visitor.id, today, joinedNow)
        update { d -> d.copy(visit = null, met = d.met + visit.speciesId, visitors = d.visitors.filterNot { it.id == visitor.id }, friends = d.friends + friend) }
        return BefriendResult(visitor, friend = true, rewards = rewards, joined = joinedNow)
    }

    /**
     * Sends [speciesId]'s visitor away, on purpose. What it earned stays (a Pokémon or egg already given is never taken back), the
     * place goes to the next in the queue, and it is kept so it can be asked back ([restore]). A visit with it ends.
     */
    fun dismiss(speciesId: Int): Boolean {
        val visitor = data.visitors.firstOrNull { it.speciesId == speciesId } ?: return false
        update { d ->
            d.copy(
                visitors = d.visitors.filterNot { it.id == visitor.id }, visit = d.visit?.takeIf { it.speciesId != speciesId },
                dismissed = (d.dismissed + visitor).takeLast(Visitors.MAX_DISMISSED),
            )
        }
        return true
    }

    /** Asks a dismissed visitor back: it joins the end of the queue with its warmth. False when there is no room or its species is here again. */
    fun restore(id: String): Boolean {
        val visitor = data.dismissed.firstOrNull { it.id == id } ?: return false
        if (data.visitors.size >= Visitors.MAX_VISITORS + Visitors.MAX_QUEUE || data.visitors.any { it.speciesId == visitor.speciesId }) return false
        update { d -> d.copy(dismissed = d.dismissed.filterNot { it.id == id }, visitors = d.visitors + visitor) }
        return true
    }

    // --- reunions (CARE.md section 6.7) -------------------------------------------------------------------------------

    /**
     * The befriended visitor that has come back to say hello, if one has. The first call on a new day may bring one (a chance, never because
     * of what was done in Care); it holds no meadow place and waits, unexpired, until it is greeted.
     */
    fun reunion(): Reunion? {
        val last = data.reunionDay
        if (last == null || today > last) {
            val seed = data.visitorSeed.takeIf { it != 0 } ?: (1 + rng.nextInt(Int.MAX_VALUE - 1))
            val found = Reunions.roll(seed, today, data.friends, data.reunion, data.reunionSeen)
            update { it.copy(visitorSeed = seed, reunionDay = today, reunion = found ?: it.reunion, reunionSeen = if (found == null) it.reunionSeen else it.reunionSeen + (found.friend.visitorId to today)) }
        }
        return data.reunion
    }

    /** Greets the friend who came back: its words, which remember the first visit. Pays nothing and awards no egg or Pokémon (it already gave them). Null when no one is waiting. */
    fun greetReunion(): String? {
        val r = reunion() ?: return null
        update { it.copy(reunion = null) }
        return Reunions.greeting(r, app.pokeplan.core.dex.Dex.species(r.friend.speciesId).name)
    }

    // --- Care Play (CARE.md section 4) --------------------------------------------------------------------------------

    /** What Care Play has found out about the Pokémon [key] (an owned Pokémon's id): only the ways of care it has shown it likes. */
    fun discoveredLikes(key: String): Set<CareAction> = CarePlay.discovered(data.play[key])

    /** An activity to suggest for [key] today. It never expires and ignoring it costs nothing. */
    fun suggestion(key: String): PlayActivity = CarePlay.suggestion(key, data.play[key], today)

    /** "Surprise me": an activity for [key]. */
    fun surprise(key: String): PlayActivity = CarePlay.surprise(key, nowMillis / 60_000L)

    /**
     * Spends time with the owned Pokémon [pokemonId] doing [activity]. It always happens and can be repeated freely; the first time with each
     * Pokémon on a day pays through the shared world (friendship to that Pokémon and a point towards Care's Egg), the rest pay nothing. The
     * way of care it showed it likes becomes known. Returns how it took it, and what the first time paid (null when it paid nothing).
     */
    fun playWith(pokemonId: String, activity: PlayActivity): Pair<PlayReaction, List<Reward>?> {
        val reaction = CarePlay.reaction(pokemonId, activity)
        val paying = CarePlay.pays(data.play[pokemonId], today)
        val rewards = if (paying) world.care(CareEvent(CareKind.PLAY, nowMillis, pokemonId)) else null
        update { d -> d.copy(play = d.play + (pokemonId to CarePlay.played(d.play[pokemonId], pokemonId, activity, today, paid = rewards != null))) }
        return reaction to rewards
    }

    // --- diary card, journal (private, never paid) --------------------------------------------------------------------

    fun diaryToday(): DiaryCard? = data.diary.firstOrNull { it.day == today }

    /** Saves today's diary card, replacing the one saved earlier today. Ratings are kept from 0 to 5. */
    fun saveDiary(feelings: Map<Emotion, Int>, urges: Map<String, Int> = emptyMap(), skills: Set<String> = emptySet(), note: String? = null): DiaryCard {
        val card = DiaryCard(
            diaryToday()?.id ?: newId(), today, nowMillis,
            feelings.mapValues { it.value.coerceIn(0, 5) },
            urges.filterKeys { it.isNotBlank() }.mapValues { it.value.coerceIn(0, 5) },
            skills.filter { Skills.byId(it) != null }.toSet(),
            note?.trim()?.ifEmpty { null },
        )
        update { d -> d.copy(diary = d.diary.filterNot { it.day == today } + card) }
        return card
    }

    fun deleteDiary(id: String) = update { d -> d.copy(diary = d.diary.filterNot { it.id == id }) }

    /** Today's prompt, a starting point for the journal, or null when all are skipped or one is answered today. */
    fun prompt(): Prompt? =
        if (data.journal.any { it.day == today && it.promptId != null }) null else Prompts.forDay(today, data.skippedPrompts[today] ?: 0)

    fun skipPrompt() = update { d -> d.copy(skippedPrompts = mapOf(today to (d.skippedPrompts[today] ?: 0) + 1)) }

    /** Adds a journal entry; [promptId] when it answers today's prompt. A blank entry is not kept. */
    fun write(text: String, promptId: String? = null, tags: Set<String> = emptySet()): JournalEntry? {
        if (text.isBlank()) return null
        val entry = JournalEntry(newId(), nowMillis, today, text.trim(), promptId, tags.map { it.trim() }.filter { it.isNotEmpty() }.toSet())
        update { it.copy(journal = it.journal + entry, journalDraft = "") }
        return entry
    }

    /**
     * Keeps the half-written journal entry on the device so it survives the app being closed. It is written straight to the save
     * without marking Care as changed for the cloud: a draft changing as you type is not worth an upload.
     */
    fun setJournalDraft(text: String) {
        if (text == data.journalDraft) return
        document.update { it.copy(journalDraft = text.take(5_000)) }
    }

    fun editEntry(id: String, text: String) {
        if (text.isBlank()) return
        update { d -> d.copy(journal = d.journal.map { if (it.id == id) it.copy(text = text.trim()) else it }) }
    }

    fun deleteEntry(id: String) = update { d -> d.copy(journal = d.journal.filterNot { it.id == id }) }

    /** Journal entries whose text or tags contain [query], newest first. */
    fun searchJournal(query: String): List<JournalEntry> {
        val q = query.trim()
        return data.journal.filter { q.isEmpty() || it.text.contains(q, ignoreCase = true) || it.tags.any { t -> t.contains(q, ignoreCase = true) } }
            .sortedByDescending { it.atMillis }
    }

    // --- trends, settings, export -------------------------------------------------------------------------------------

    fun feelingWeeks(weeks: Int = 8) = CareTrends.feelingWeeks(data.diary, today, weeks, weekStart())

    fun skillsLast7Days(): Int = CareTrends.skillsLast7Days(data, today)

    fun routinesLast7Days(): Int = CareTrends.routinesLast7Days(data.routines, today)

    fun bestWeek(): Int = CareTrends.bestWeek(data.routines, weekStart())

    fun calmMinutesLast7Days(): Int = CareTrends.calmMinutesLast7Days(data.breaks, today)

    fun changeSettings(change: (CareSettings) -> CareSettings) = update { it.copy(settings = change(it.settings)) }

    /** Everything Care keeps on this device, as readable JSON. */
    fun exportJson(): String = Json(json) { prettyPrint = true }.encodeToString(CareData.serializer(), data)

    /** Erases Care's save on this device. */
    fun erase() = update { CareData() }

    companion object {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

        /** What every Care save that comes in goes through: visitors of an old save get an id and their warm days become progress (kept, never lost). */
        internal fun tidy(d: CareData): CareData =
            if (d.visitors.all { Visitors.migrated(it) == it } && d.dismissed.all { Visitors.migrated(it) == it }) d
            else d.copy(visitors = d.visitors.map(Visitors::migrated), dismissed = d.dismissed.map(Visitors::migrated))
    }
}
