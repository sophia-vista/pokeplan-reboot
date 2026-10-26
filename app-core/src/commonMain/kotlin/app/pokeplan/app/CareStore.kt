package app.pokeplan.app

import app.pokeplan.care.CalmBreak
import app.pokeplan.care.CareData
import app.pokeplan.care.CareRules
import app.pokeplan.care.CareSettings
import app.pokeplan.care.CareTrends
import app.pokeplan.care.Mood
import app.pokeplan.care.MoodCheckIn
import app.pokeplan.care.Prompt
import app.pokeplan.care.Prompts
import app.pokeplan.care.Reflection
import app.pokeplan.care.Routine
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
 * Doing earns (a routine, a calm break, a week with a rhythm); entering anything sensitive (a mood, a note, a reflection)
 * never does.
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

    // --- mood, prompts (private, never paid) --------------------------------------------------------------------------

    fun checkIn(mood: Mood, note: String? = null, tags: Set<String> = emptySet()): MoodCheckIn {
        val entry = MoodCheckIn(newId(), nowMillis, today, mood, note?.trim()?.ifEmpty { null }, tags.map { it.trim() }.filter { it.isNotEmpty() }.toSet())
        update { it.copy(moods = it.moods + entry) }
        return entry
    }

    fun deleteCheckIn(id: String) = update { d -> d.copy(moods = d.moods.filterNot { it.id == id }) }

    /** Today's prompt, or null when all are skipped or one is already answered today. */
    fun prompt(): Prompt? =
        if (data.reflections.any { it.day == today }) null else Prompts.forDay(today, data.skippedPrompts[today] ?: 0)

    fun skipPrompt() = update { d -> d.copy(skippedPrompts = mapOf(today to (d.skippedPrompts[today] ?: 0) + 1)) }

    fun reflect(promptId: String, text: String) {
        if (text.isBlank()) return
        update { it.copy(reflections = it.reflections + Reflection(newId(), today, promptId, text.trim())) }
    }

    fun deleteReflection(id: String) = update { d -> d.copy(reflections = d.reflections.filterNot { it.id == id }) }

    // --- trends, settings, export -------------------------------------------------------------------------------------

    fun moodWeeks(weeks: Int = 8) = CareTrends.moodWeeks(data.moods, today, weeks, weekStart())

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
