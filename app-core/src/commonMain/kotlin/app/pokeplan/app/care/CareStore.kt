package app.pokeplan.app.care

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
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.random.Random
import app.pokeplan.app.World
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.SavedDocument
import app.pokeplan.app.storage.Storage
import app.pokeplan.app.storage.newId

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

    fun reload() = document.reload()

    fun sync() = document.sync()

    private fun update(change: (CareData) -> CareData) = document.update(change)

    fun restore(snapshot: CareData) = update { snapshot }

    private fun newId(): String = newId(nowMillis, rng)

    private fun pay(kind: CareKind): List<Reward>? = world.care(CareEvent(kind, nowMillis))

    private fun rhythm(): List<Reward> {
        if (!CareRules.rhythmDue(data, thisWeek)) return emptyList()
        val rewards = pay(CareKind.WEEKLY_RHYTHM) ?: return emptyList()
        update { it.copy(rhythmWeeks = it.rhythmWeeks + thisWeek) }
        return rewards
    }

    fun addRoutine(title: String, time: LocalTime? = null, days: Set<DayOfWeek> = emptySet(), icon: String? = null): Routine {
        val routine = Routine(newId(), title.trim(), icon, time, days, today)
        update { it.copy(routines = it.routines + routine) }
        return routine
    }

    fun updateRoutine(routine: Routine) = update { d -> d.copy(routines = d.routines.map { if (it.id == routine.id) routine else it }) }

    fun archiveRoutine(id: String, archived: Boolean = true) =
        update { d -> d.copy(routines = d.routines.map { if (it.id == id) it.copy(archived = archived) else it }) }

    fun deleteRoutine(id: String) = update { d -> d.copy(routines = d.routines.filterNot { it.id == id }) }

    fun todayRoutines(): List<Routine> = routinesOn(today)

    fun routinesOn(day: LocalDate): List<Routine> =
        data.routines.filter { it.plannedOn(day) }.sortedWith(compareBy(nullsLast()) { it.time })

    fun routineCount(day: LocalDate = today): Pair<Int, Int> = routinesOn(day).let { list -> list.count { it.doneOn(day) } to list.size }

    private fun tickable(day: LocalDate): Boolean = day == today || day == today.minus(DatePeriod(days = 1))

    fun completeRoutine(id: String, forDay: LocalDate = today): List<Reward> {
        if (!tickable(forDay)) return emptyList()
        val routine = data.routines.first { it.id == id }
        if (routine.doneOn(forDay)) return emptyList()
        if (forDay != today) {
            update { d -> d.copy(routines = d.routines.map { if (it.id == id) it.complete(forDay) else it }) }
            return emptyList()
        }
        val canPay = routine.paysOn(today) && CareRules.routinesPaidOn(data, today) < CareRules.ROUTINES_PAID_PER_DAY
        val rewards = if (canPay) pay(CareKind.ROUTINE) else null
        // Completion and the paid mark in one save (the old code saved Care twice, plus the world write inside pay()).
        update { d ->
            d.copy(routines = d.routines.map { r ->
                if (r.id != id) r else { val done = r.complete(today); if (rewards != null) done.paid(today) else done }
            })
        }
        return rewards.orEmpty() + rhythm()
    }

    fun undoRoutine(id: String, forDay: LocalDate = today) {
        if (!tickable(forDay)) return
        update { d -> d.copy(routines = d.routines.map { if (it.id == id) it.undo(forDay) else it }) }
    }

    fun skipRoutine(id: String, forDay: LocalDate = today) {
        if (!tickable(forDay)) return
        update { d -> d.copy(routines = d.routines.map { if (it.id == id && !it.doneOn(forDay)) it.skip(forDay) else it }) }
    }

    fun unskipRoutine(id: String, forDay: LocalDate = today) =
        update { d -> d.copy(routines = d.routines.map { if (it.id == id) it.unskip(forDay) else it }) }

    fun finishCalmBreak(seconds: Int): List<Reward> {
        val length = seconds.coerceIn(0, CareRules.BREAK_MAX_SECONDS)
        val canPay = length >= CareRules.BREAK_MIN_SECONDS && CareRules.breaksPaidOn(data, today) < CareRules.BREAKS_PAID_PER_DAY
        val rewards = if (canPay) pay(CareKind.CALM_BREAK) else null
        update { it.copy(breaks = it.breaks + CalmBreak(nowMillis, today, length, paid = rewards != null)) }
        return rewards.orEmpty() + rhythm()
    }

    fun practiseSkill(skillId: String): List<Reward> {
        val skill = Skills.byId(skillId) ?: return emptyList()
        if (skill.calmBreak) return emptyList()
        val rewards = if (CareRules.skillPaysOn(data, skillId, today)) pay(CareKind.SKILL) else null
        update { it.copy(practices = it.practices + SkillPractice(nowMillis, today, skillId, paid = rewards != null)) }
        return rewards.orEmpty() + rhythm()
    }

    fun practisedToday(): Set<String> =
        data.practices.filter { it.day == today }.map { it.skillId }.toSet() +
            (if (data.breaks.any { it.day == today }) Skills.all.filter { it.calmBreak }.map { it.id } else emptyList())

    fun berries(): Map<String, Int> = world.berries()

    /** Friends the world can still pay today; at 0 a ready visitor waits for tomorrow. */
    fun befriendsLeft(): Int = world.befriendsLeft()

    fun resting(visitor: Visitor): Boolean = Visitors.resting(visitor, nowMillis)

    fun canVisit(visitor: Visitor): Boolean =
        Visitors.meadow(data.visitors).any { it.id == visitor.id } && !resting(visitor) && (visit == null || visit?.speciesId == visitor.speciesId)

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

    fun arrive() { visitors(); reunion() }

    fun meadow(): List<Visitor> = Visitors.meadow(visitors())
    fun queued(): List<Visitor> = Visitors.queue(visitors())

    fun dismissed(): List<Visitor> = data.dismissed.asReversed()

    val visit: Visit? get() = data.visit

    fun startVisit(speciesId: Int): Visit? {
        visit?.takeIf { it.speciesId == speciesId }?.let { return it }
        val visitor = visitors().firstOrNull { it.speciesId == speciesId } ?: return null
        if (!canVisit(visitor)) return null
        return Visitors.start(speciesId, today).also { v -> update { it.copy(visit = v) } }
    }

    fun visitAct(action: CareAction, spot: PetSpot? = null, berry: String? = null): VisitStep? {
        val visit = visit ?: return null
        val visitor = visitors().firstOrNull { it.speciesId == visit.speciesId } ?: return null
        val (acted, reaction) = Visitors.act(visitor, visit, action, spot)
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

    fun visitNotice(): VisitStep? {
        val visit = visit ?: return null
        if (visit.pendingBid == null) return null
        val visitor = visitors().firstOrNull { it.speciesId == visit.speciesId } ?: return null
        val next = Visitors.notice(visit)
        val warmer = visitor.copy(progress = visitor.progress + (next.warmth - visit.warmth))
        update { d -> d.copy(visit = next, visitors = d.visitors.map { if (it.id == warmer.id) warmer else it }) }
        return VisitStep(Reaction.LIKED, next, if (Visitors.friend(warmer)) leaveVisit() else null)
    }

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
            update { d -> d.copy(visit = null, met = d.met + visit.speciesId) }
            return BefriendResult(visitor, friend = false, rewards = emptyList(), waiting = true)
        }
        val joinedNow = rewards.filterIsInstance<Reward.Hatched>().any { it.pokemon.speciesId == visitor.speciesId && it.pokemon.friendship >= RewardEngine.BEFRIEND_JOIN_FRIENDSHIP }
        val friend = Friend(visitor.speciesId, visitor.formIdentifier, visitor.id, today, joinedNow)
        update { d -> d.copy(visit = null, met = d.met + visit.speciesId, visitors = d.visitors.filterNot { it.id == visitor.id }, friends = d.friends + friend) }
        return BefriendResult(visitor, friend = true, rewards = rewards, joined = joinedNow)
    }

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

    fun restore(id: String): Boolean {
        val visitor = data.dismissed.firstOrNull { it.id == id } ?: return false
        if (data.visitors.size >= Visitors.MAX_VISITORS + Visitors.MAX_QUEUE || data.visitors.any { it.speciesId == visitor.speciesId }) return false
        update { d -> d.copy(dismissed = d.dismissed.filterNot { it.id == id }, visitors = d.visitors + visitor) }
        return true
    }

    fun reunion(): Reunion? {
        val last = data.reunionDay
        if (last == null || today > last) {
            val seed = data.visitorSeed.takeIf { it != 0 } ?: (1 + rng.nextInt(Int.MAX_VALUE - 1))
            val found = Reunions.roll(seed, today, data.friends, data.reunion, data.reunionSeen)
            update { it.copy(visitorSeed = seed, reunionDay = today, reunion = found ?: it.reunion, reunionSeen = if (found == null) it.reunionSeen else it.reunionSeen + (found.friend.visitorId to today)) }
        }
        return data.reunion
    }

    fun greetReunion(): String? {
        val r = reunion() ?: return null
        update { it.copy(reunion = null) }
        return Reunions.greeting(r, app.pokeplan.core.dex.Dex.species(r.friend.speciesId).name)
    }

    fun discoveredLikes(key: String): Set<CareAction> = CarePlay.discovered(data.play[key])

    fun suggestion(key: String): PlayActivity = CarePlay.suggestion(key, data.play[key], today)

    fun surprise(key: String): PlayActivity = CarePlay.surprise(key, nowMillis / 60_000L + (data.play[key]?.times ?: 0) * 1009L)

    fun playWith(pokemonId: String, activity: PlayActivity): Pair<PlayReaction, List<Reward>?> {
        val reaction = CarePlay.reaction(pokemonId, activity)
        val paying = CarePlay.pays(data.play[pokemonId], today)
        val rewards = if (paying) world.care(CareEvent(CareKind.PLAY, nowMillis, pokemonId)) else null
        update { d -> d.copy(play = d.play + (pokemonId to CarePlay.played(d.play[pokemonId], pokemonId, activity, today, paid = rewards != null))) }
        return reaction to rewards
    }

    fun diaryToday(): DiaryCard? = diaryOn(today)

    fun diaryOn(day: LocalDate): DiaryCard? = data.diary.firstOrNull { it.day == day }

    fun saveDiary(feelings: Map<Emotion, Int>, urges: Map<String, Int> = emptyMap(), skills: Set<String> = emptySet(), note: String? = null, forDay: LocalDate = today): DiaryCard {
        val day = if (forDay > today) today else forDay
        val existing = diaryOn(day)
        val card = DiaryCard(
            existing?.id ?: newId(), day, nowMillis,
            feelings.mapValues { it.value.coerceIn(0, 5) },
            urges.filterKeys { it.isNotBlank() }.mapValues { it.value.coerceIn(0, 5) },
            skills.filter { Skills.byId(it) != null }.toSet(),
            note?.trim()?.ifEmpty { null },
            mood = existing?.mood,
        )
        update { d -> d.copy(diary = d.diary.filterNot { it.day == day } + card) }
        return card
    }

    fun setMood(level: Int?) {
        val existing = diaryToday()
        val mood = level?.coerceIn(1, 5)
        val card = (existing ?: DiaryCard(newId(), today, nowMillis)).copy(mood = mood)
        val empty = card.mood == null && card.feelings.isEmpty() && card.urges.isEmpty() && card.skills.isEmpty() && card.note == null
        update { d -> d.copy(diary = d.diary.filterNot { it.day == today } + listOfNotNull(card.takeUnless { empty })) }
    }

    fun moodDays(days: Int = 7): List<Pair<kotlinx.datetime.LocalDate, Int?>> =
        (days - 1 downTo 0).map { back -> today.minus(kotlinx.datetime.DatePeriod(days = back)).let { day -> day to data.diary.firstOrNull { it.day == day }?.mood } }

    fun deleteDiary(id: String) = update { d -> d.copy(diary = d.diary.filterNot { it.id == id }) }

    fun prompt(): Prompt? =
        if (data.journal.any { it.day == today && it.promptId != null }) null else Prompts.forDay(today, data.skippedPrompts[today] ?: 0)

    fun skipPrompt() = update { d -> d.copy(skippedPrompts = mapOf(today to (d.skippedPrompts[today] ?: 0) + 1)) }

    fun write(text: String, promptId: String? = null, tags: Set<String> = emptySet(), atMillis: Long? = null): JournalEntry? {
        if (text.isBlank()) return null
        val at = (atMillis ?: nowMillis).coerceAtMost(nowMillis)
        val entry = JournalEntry(newId(), at, at.toLocalDateTime(zone).date, text.trim(), promptId, tags.map { it.trim() }.filter { it.isNotEmpty() }.toSet())
        update { it.copy(journal = it.journal + entry, journalDraft = "") }
        return entry
    }

    fun setJournalDraft(text: String) {
        if (text == data.journalDraft) return
        document.update { it.copy(journalDraft = text.take(5_000)) }
    }

    fun editEntry(id: String, text: String, atMillis: Long? = null, tags: Set<String>? = null) {
        if (text.isBlank()) return
        update { d ->
            d.copy(journal = d.journal.map { e ->
                if (e.id != id) e else {
                    val at = (atMillis ?: e.atMillis).coerceAtMost(nowMillis)
                    e.copy(text = text.trim(), atMillis = at, day = at.toLocalDateTime(zone).date, tags = tags?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: e.tags)
                }
            })
        }
    }

    fun deleteEntry(id: String) = update { d -> d.copy(journal = d.journal.filterNot { it.id == id }) }

    fun searchJournal(query: String): List<JournalEntry> {
        val q = app.pokeplan.kit.SearchQuery.of(query)
        return data.journal.filter { q.isEmpty || q.matchesAny(it.text) || q.matchesAny(it.tags) }
            .sortedByDescending { it.atMillis }
    }

    fun feelingWeeks(weeks: Int = 8) = CareTrends.feelingWeeks(data.diary, today, weeks, weekStart())

    fun skillsLast7Days(): Int = CareTrends.skillsLast7Days(data, today)

    fun routinesLast7Days(): Int = CareTrends.routinesLast7Days(data.routines, today)

    fun bestWeek(): Int = CareTrends.bestWeek(data.routines, weekStart())

    fun calmMinutesLast7Days(): Int = CareTrends.calmMinutesLast7Days(data.breaks, today)

    fun changeSettings(change: (CareSettings) -> CareSettings) = update { it.copy(settings = change(it.settings)) }

    fun exportJson(): String = Json(json) { prettyPrint = true }.encodeToString(CareData.serializer(), data)

    fun erase() = update { CareData() }

    companion object {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

        internal fun tidy(d: CareData): CareData =
            if (d.visitors.all { Visitors.migrated(it) == it } && d.dismissed.all { Visitors.migrated(it) == it }) d
            else d.copy(visitors = d.visitors.map(Visitors::migrated), dismissed = d.dismissed.map(Visitors::migrated))
    }
}
