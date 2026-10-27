package app.pokeplan.app

import app.pokeplan.care.BefriendResult
import app.pokeplan.care.CalmBreak
import app.pokeplan.care.CareAction
import app.pokeplan.care.CareData
import app.pokeplan.care.CareRules
import app.pokeplan.care.CareSettings
import app.pokeplan.care.CareTrends
import app.pokeplan.care.DiaryCard
import app.pokeplan.care.Emotion
import app.pokeplan.care.JournalEntry
import app.pokeplan.care.PetSpot
import app.pokeplan.care.Prompt
import app.pokeplan.care.Prompts
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
import app.pokeplan.plan.toLocalDateTime
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * The Care module on this device (docs/ecosystem.md, section 6): its own save in its own [storage] (`care.json`), apart
 * from Plan's. It reaches the shared world only through [world], with events that carry no text, mood or health value.
 * Doing earns (a routine, a calm break, a practised skill, a week with a rhythm); entering anything sensitive (a diary card,
 * a note, a journal entry) never does.
 */
class CareStore(
    private val storage: Storage,
    private val clock: Clock,
    private val world: World,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val weekStart: () -> DayOfWeek = { DayOfWeek.MONDAY },
    private val rng: Random = Random,
) {
    var data: CareData = load()
        private set

    private val listeners = mutableListOf<(CareData) -> Unit>()

    fun observe(listener: (CareData) -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    private val nowMillis: Long get() = clock.nowMillis()
    val today: LocalDate get() = nowMillis.toLocalDateTime(zone).date
    private val thisWeek: LocalDate get() = CareRules.weekStartOf(today, weekStart())

    private fun load(): CareData {
        val text = storage.load() ?: return CareData()
        return try {
            json.decodeFromString(CareData.serializer(), text)
        } catch (e: Exception) {
            CareData()
        }
    }

    /** Picks up a change another process made (a widget), without writing anything back. */
    fun reload() {
        val fresh = load()
        if (fresh == data) return
        data = fresh
        listeners.toList().forEach { it(data) }
    }

    private fun update(change: (CareData) -> CareData) {
        data = change(data)
        storage.save(json.encodeToString(CareData.serializer(), data))
        listeners.toList().forEach { it(data) }
    }

    private fun newId(): String = "${nowMillis.toString(36)}-${rng.nextInt(Int.MAX_VALUE).toString(36)}"

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

    /** Whether today's befriend is still free and some care has paid today (a visitor warms up on days with care). */
    fun canBefriend(): Boolean = data.befriendDay != today && CareRules.paidOn(data, today)

    /** Whether today's befriend has been spent. */
    val befriendSpent: Boolean get() = data.befriendDay == today

    /**
     * The visitors now: the ones warming up that have not wandered off, and today's new ones (2, or 3 after a break), the same
     * on this device all day. The day's offer is made the first time it is asked for.
     */
    fun visitors(): List<Visitor> {
        if (data.visitorsDay != today) {
            val seed = data.visitorSeed.takeIf { it != 0 } ?: (1 + rng.nextInt(Int.MAX_VALUE - 1))
            val earliest = today.toEpochDays() - Visitors.WANDER_DAYS
            val staying = data.visitors.filter { it.points > 0 && (it.lastVisitDay?.toEpochDays() ?: 0L) >= earliest }
            val arrivals = Visitors.offer(
                seed, today, Visitors.count(data.visitorsDay, today), staying.map { it.speciesId }.toSet(),
                world.visitorLuck(), Visitors.lean(data, today),
            )
            update { it.copy(visitorSeed = seed, visitorsDay = today, visitors = staying + arrivals) }
        }
        return data.visitors
    }

    /** The visit in progress today, if there is one. */
    val visit: Visit? get() = data.visit?.takeIf { it.day == today }

    /**
     * Starts a visit with a visitor, which spends today's befriend when it ends. Null when it cannot be done now: the visitor
     * is gone, today's befriend is spent, or no care has paid today. A visit already started with that visitor carries on.
     */
    fun startVisit(speciesId: Int): Visit? {
        visit?.takeIf { it.speciesId == speciesId }?.let { return it }
        if (visitors().none { it.speciesId == speciesId } || !canBefriend()) return null
        return Visitors.start(speciesId, today).also { v -> update { it.copy(visit = v) } }
    }

    /** Does [action] in the visit; the third ends it (see [finishVisit]). Null when there is no visit to do it in. */
    fun visitAct(action: CareAction, spot: PetSpot? = null): VisitStep? {
        val visit = visit ?: return null
        val visitor = visitors().firstOrNull { it.speciesId == visit.speciesId } ?: return null
        val (next, reaction) = Visitors.act(visitor, visit, action, spot)
        update { it.copy(visit = next) }
        return VisitStep(reaction, next, if (Visitors.finished(next)) finishVisit() else null)
    }

    /** Turns toward the visitor's bid, if it made one. */
    fun visitNotice(): Visit? {
        val visit = visit ?: return null
        return Visitors.notice(visit).also { next -> update { it.copy(visit = next) } }
    }

    /**
     * Ends a finished visit: the visitor warms by a day if the visit was warm enough, and at [Visitors.FRIEND_POINTS] it
     * becomes a friend, paid through the world (an egg that hatches at once, rarely the visitor itself). Spends today's
     * befriend. Null when the visit is not finished, or the world paid nothing (it can be tried again).
     */
    fun finishVisit(): BefriendResult? {
        val visit = visit?.takeIf { Visitors.finished(it) } ?: return null
        val visitor = visitors().firstOrNull { it.speciesId == visit.speciesId } ?: return null
        val warmer = visitor.copy(points = visitor.points + if (Visitors.warm(visit)) 1 else 0, lastVisitDay = today)
        if (warmer.points < Visitors.FRIEND_POINTS) {
            update { d -> d.copy(befriendDay = today, visit = null, met = d.met + visit.speciesId, visitors = d.visitors.map { if (it.speciesId == visit.speciesId) warmer else it }) }
            return BefriendResult(warmer, friend = false, rewards = emptyList())
        }
        val rewards = world.befriend(BefriendEvent(visitor.speciesId, visitor.formIdentifier, newId(), nowMillis)) ?: return null
        update { d -> d.copy(befriendDay = today, visit = null, met = d.met + visit.speciesId, visitors = d.visitors.filterNot { it.speciesId == visit.speciesId }) }
        return BefriendResult(warmer, friend = true, rewards = rewards)
    }

    // --- diary card, journal (private, never paid) --------------------------------------------------------------------

    /** Today's diary card, if one was saved. */
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
        update { it.copy(journal = it.journal + entry) }
        return entry
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
    }
}
