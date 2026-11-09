package app.pokeplan.app

import app.pokeplan.core.reward.HealEvent
import app.pokeplan.core.reward.HealKind
import app.pokeplan.core.reward.Reward
import app.pokeplan.heal.ActivityRecord
import app.pokeplan.heal.ActivityType
import app.pokeplan.heal.Check
import app.pokeplan.heal.GoalKind
import app.pokeplan.heal.GoalStatus
import app.pokeplan.heal.HealData
import app.pokeplan.heal.HealGoal
import app.pokeplan.heal.HealImport
import app.pokeplan.heal.HealKindOf
import app.pokeplan.heal.HealthKind
import app.pokeplan.heal.ImportPlan
import app.pokeplan.heal.ProviderRead
import app.pokeplan.heal.HealKinds
import app.pokeplan.heal.HealRules
import app.pokeplan.heal.HealSettings
import app.pokeplan.heal.HydrationEntry
import app.pokeplan.heal.MeasureType
import app.pokeplan.heal.Measurement
import app.pokeplan.heal.SleepRecord
import app.pokeplan.plan.toLocalDateTime
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.random.Random

/** What saving a record did: the [record] (null when it was refused, with the [refused] reason) and what Field Research paid for it, if anything. */
data class Logged<T>(val record: T?, val refused: String? = null, val rewards: List<Reward> = emptyList())

/**
 * The Heal module on this device (docs/pokelife `MODULES/HEAL.md`): its own save in its own [storage], apart from Plan's and Care's, kept by `HealTables`
 * in the live database. Everything in it is private: it reaches the shared world only through [world], with a [HealEvent] that carries a stable id,
 * the module, an opaque record reference, the broad kind and the time, and **never** a duration, a distance, a volume, a measurement or a note.
 *
 * Only participation can earn, and only when the player has turned Field Research on: a session logged just now (see [HealRules.activityPays]) or the day's first
 * drink. Sleep, measurements and goals never pay. A record is paid at the moment it is created and never again: editing it, deleting it or restoring
 * a backup can not replay the payment (the record keeps `paid`, and the event id is remembered by the game).
 */
class HealStore(
    storage: Storage,
    private val clock: Clock,
    private val world: World,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val weekStart: () -> DayOfWeek = { DayOfWeek.MONDAY },
    private val rng: Random = Random,
) {
    private val document = SavedDocument(storage, HealData.serializer(), json, tidy = { tidy(it) }) { HealData() }

    val data: HealData get() = document.value

    fun observe(listener: (HealData) -> Unit): () -> Unit = document.observe(listener)

    private val nowMillis: Long get() = clock.nowMillis()
    val today: LocalDate get() = nowMillis.toLocalDateTime(zone).date
    val thisWeek: LocalDate get() = HealRules.weekStartOf(today, weekStart())

    /** Picks up a change another process made, without writing anything back; see [SavedDocument.reload]. */
    fun reload() = document.reload()

    private fun update(change: (HealData) -> HealData) = document.update(change)

    private fun newId(): String = newId(nowMillis, rng)

    private fun dayOf(millis: Long): LocalDate = millis.toLocalDateTime(zone).date

    private fun pay(kind: HealKind, recordId: String): List<Reward>? =
        if (!data.settings.fieldResearch) null else world.heal(HealEvent("heal:${kind.name.lowercase()}:$recordId", kind, nowMillis, recordId))

    // --- activity -----------------------------------------------------------------------------------------------------

    /**
     * Logs an activity that ended at [endedAtMillis] (now by default) and lasted [minutes]. Quick: a type and a duration are enough. It is paid by
     * Field Research when that is on and it qualifies; an older session is kept in history and pays nothing.
     */
    fun logActivity(type: ActivityType, minutes: Int, endedAtMillis: Long = nowMillis, distanceMeters: Int? = null, note: String = ""): Logged<ActivityRecord> {
        (HealRules.checkActivity(minutes, distanceMeters) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val start = endedAtMillis - minutes * 60_000L
        val record = ActivityRecord(newId(), type, start, minutes, dayOf(start), distanceMeters, note.trim().take(HealRules.NOTE_MAX), createdMillis = nowMillis)
        update { it.copy(activities = it.activities + record) }
        val pays = data.settings.fieldResearch && HealRules.activityPays(record, nowMillis, HealRules.activitiesPaidOn(data, record.day))
        val rewards = if (pays) pay(HealKind.ACTIVITY, record.id) else null
        if (rewards != null) update { d -> d.copy(activities = d.activities.map { if (it.id == record.id) it.copy(paid = true) else it }) }
        return Logged(data.activities.first { it.id == record.id }, rewards = rewards.orEmpty())
    }

    /** Changes what was written down about an activity. Its payment is kept as it was: an edit never pays and never takes back. */
    fun updateActivity(a: ActivityRecord): Logged<ActivityRecord> {
        (HealRules.checkActivity(a.minutes, a.distanceMeters) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val old = data.activities.firstOrNull { it.id == a.id } ?: return Logged(null, "That activity is gone.")
        val next = a.copy(paid = old.paid, source = old.source, provenance = old.provenance, day = dayOf(a.startMillis), note = a.note.trim().take(HealRules.NOTE_MAX))
        update { d -> d.copy(activities = d.activities.map { if (it.id == a.id) next else it }) }
        return Logged(next)
    }

    fun deleteActivity(id: String) = update { d -> d.copy(activities = d.activities.filterNot { it.id == id }) }

    // --- hydration ----------------------------------------------------------------------------------------------------

    /** Logs a drink of [ml]. The day's first one is the only participation Field Research counts, and only when it is on. */
    fun logDrink(ml: Int, drink: String = "", atMillis: Long = nowMillis): Logged<HydrationEntry> {
        (HealRules.checkHydration(ml) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val entry = HydrationEntry(newId(), atMillis, dayOf(atMillis), ml, drink.trim().take(60))
        update { it.copy(hydration = it.hydration + entry) }
        val pays = data.settings.fieldResearch && HealRules.participationPays(entry, nowMillis, HealRules.participationPaidOn(data, entry.day))
        val rewards = if (pays) pay(HealKind.PARTICIPATION, entry.id) else null
        if (rewards != null) update { d -> d.copy(hydration = d.hydration.map { if (it.id == entry.id) it.copy(paid = true) else it }) }
        return Logged(data.hydration.first { it.id == entry.id }, rewards = rewards.orEmpty())
    }

    fun updateDrink(e: HydrationEntry): Logged<HydrationEntry> {
        (HealRules.checkHydration(e.ml) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val old = data.hydration.firstOrNull { it.id == e.id } ?: return Logged(null, "That drink is gone.")
        val next = e.copy(paid = old.paid, source = old.source, provenance = old.provenance, day = dayOf(e.atMillis), drink = e.drink.trim().take(60))
        update { d -> d.copy(hydration = d.hydration.map { if (it.id == e.id) next else it }) }
        return Logged(next)
    }

    fun deleteDrink(id: String) = update { d -> d.copy(hydration = d.hydration.filterNot { it.id == id }) }

    /** The drinks logged today, oldest first. */
    fun drinksToday(): List<HydrationEntry> = data.hydration.filter { it.day == today }.sortedBy { it.atMillis }

    // --- sleep (never pays) -------------------------------------------------------------------------------------------

    fun logSleep(startMillis: Long, endMillis: Long, note: String = ""): Logged<SleepRecord> {
        (HealRules.checkSleep(startMillis, endMillis) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val record = SleepRecord(newId(), startMillis, endMillis, dayOf(endMillis), note.trim().take(HealRules.NOTE_MAX))
        update { it.copy(sleep = it.sleep + record) }
        return Logged(record)
    }

    fun updateSleep(s: SleepRecord): Logged<SleepRecord> {
        (HealRules.checkSleep(s.startMillis, s.endMillis) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val old = data.sleep.firstOrNull { it.id == s.id } ?: return Logged(null, "That night is gone.")
        val next = s.copy(source = old.source, provenance = old.provenance, day = dayOf(s.endMillis), note = s.note.trim().take(HealRules.NOTE_MAX))
        update { d -> d.copy(sleep = d.sleep.map { if (it.id == s.id) next else it }) }
        return Logged(next)
    }

    fun deleteSleep(id: String) = update { d -> d.copy(sleep = d.sleep.filterNot { it.id == id }) }

    // --- body measurements (never pay) -----------------------------------------------------------------------------------

    fun logMeasurement(type: MeasureType, value: Double, unit: String, label: String = "", atMillis: Long = nowMillis): Logged<Measurement> {
        (HealRules.checkMeasurement(type, value, unit) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val record = Measurement(newId(), type, value, unit, atMillis, dayOf(atMillis), label.trim().take(40))
        update { it.copy(measurements = it.measurements + record) }
        return Logged(record)
    }

    fun deleteMeasurement(id: String) = update { d -> d.copy(measurements = d.measurements.filterNot { it.id == id }) }

    // --- goals (optional; never pay, never punish) -----------------------------------------------------------------------

    fun addGoal(kind: GoalKind, target: Int): HealGoal? {
        if (target <= 0 || target > 100_000) return null
        val goal = HealGoal(newId(), kind, target, today, createdMillis = nowMillis)
        update { it.copy(goals = it.goals + goal) }
        return goal
    }

    /** Pauses, resumes, completes or archives a goal; none of these touch the records the goal counts. */
    fun setGoalStatus(id: String, status: GoalStatus) =
        update { d -> d.copy(goals = d.goals.map { if (it.id == id) it.copy(status = status) else it }) }

    fun deleteGoal(id: String) = update { d -> d.copy(goals = d.goals.filterNot { it.id == id }) }

    // --- settings ------------------------------------------------------------------------------------------------------

    private fun settings(change: (HealSettings) -> HealSettings) = update { it.copy(settings = change(it.settings)) }

    /** Turns Field Research (the reward) on or off. Off never touches a record. */
    fun setFieldResearch(on: Boolean) = settings { it.copy(fieldResearch = on) }

    fun setHydrationTarget(ml: Int?) = settings { it.copy(hydrationTargetMl = ml?.takeIf { v -> v in 100..20_000 }) }

    fun setShowMeasurementsOnToday(on: Boolean) = settings { it.copy(showMeasurementsOnToday = on) }

    fun setWeightUnit(unit: String) = settings { it.copy(weightUnit = if (unit == "lb") "lb" else "kg") }

    /** Up to [HealSettings.MAX_REMINDERS] hydration reminder times a day; none means no reminders (the default). */
    fun setHydrationReminders(times: List<LocalTime>) = settings { it.copy(hydrationReminders = times.distinct().sorted().take(HealSettings.MAX_REMINDERS)) }

    fun markTutorialSeen() = settings { it.copy(tutorialSeen = true) }

    // --- Health Connect (an Android platform API, read on request; no record is ever written back) ---------------------------------

    /** What reading [read] would add, skip and reject, for the preview shown before anything is saved. Pure: nothing changes. */
    fun previewImport(read: ProviderRead): ImportPlan = HealImport.plan(data, read.records, nowMillis)

    /**
     * Saves what [read] returned, in one write, and notes the time of the last successful read (a failed or partial read still imports what came and does not
     * move that time). Records already here (same provider id) are skipped, so a retry is harmless; a possible match with a record the player wrote is added beside
     * it and flagged. **Nothing is paid**: imported records are never a Field Research event (HEAL.md 10.3). Returns the plan that was applied.
     */
    fun importFromProvider(read: ProviderRead): ImportPlan {
        val plan = previewImport(read)
        update { d ->
            val added = HealImport.apply(d, plan, nowMillis, ::newId, ::dayOf)
            if (read.failed) added else added.copy(settings = added.settings.copy(healthLastSyncMillis = nowMillis))
        }
        return plan
    }

    /** The kinds the player chose to import; none disconnects HEAL from Health Connect in the app (the screen also revokes Android's permissions). Imported records stay. */
    fun setHealthKinds(kinds: Set<HealthKind>) = settings { it.copy(healthKinds = kinds, healthLastSyncMillis = if (kinds.isEmpty()) 0 else it.healthLastSyncMillis) }

    /** Imported records by the app that wrote them. */
    fun importedSources(): List<HealImport.SourceCount> = HealImport.sources(data)

    /** Imported records that may repeat one already here, as (id, family). */
    fun possibleDuplicates(): List<Pair<String, HealKindOf>> = HealImport.flagged(data)

    /** The player looked at a flagged record and wants both. */
    fun keepBoth(id: String) = update { HealImport.keepBoth(it, id) }

    /** Deletes the imported records of [provider] (one writing app only, if [app] is given). Records the player wrote are never touched. Returns how many were removed. */
    fun removeImported(provider: String = HealImport.PROVIDER_HEALTH_CONNECT, app: String? = null): Int {
        val before = importedSources().filter { it.provider == provider && (app == null || it.app == app) }.sumOf { it.total }
        update { HealImport.withoutImported(it, provider, app) }
        return before
    }

    // --- export and erase (one kind at a time, or all) ----------------------------------------------------------------------

    /** The chosen kinds of record (all by default) as readable JSON, exactly as saved. Nothing of the game is in it. */
    fun export(kinds: Set<HealKinds> = HealKinds.entries.toSet()): String = HealExport.json(data, kinds)

    /** Erases every record of [kind] and nothing else: other kinds, the settings, Pokémon, Poké, the Bag and the other modules are not touched. */
    fun erase(kind: HealKinds) = update { HealExport.without(it, kind) }

    /** Erases all of HEAL's records (not its settings, not what Field Research paid). */
    fun eraseAll() = update { d -> HealKinds.entries.fold(d) { acc, k -> HealExport.without(acc, k) } }

    companion object {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

        internal fun tidy(d: HealData): HealData {
            val s = d.settings
            val reminders = s.hydrationReminders.distinct().sorted().take(HealSettings.MAX_REMINDERS)
            return if (reminders == s.hydrationReminders) d else d.copy(settings = s.copy(hydrationReminders = reminders))
        }
    }
}

/** Export and erase by kind, pure and free of the game: one place that knows which field of the document each kind is. */
object HealExport {
    private val pretty = Json { encodeDefaults = true; prettyPrint = true }

    fun json(data: HealData, kinds: Set<HealKinds>): String {
        val all = HealStore.json.encodeToJsonElement(HealData.serializer(), data) as JsonObject
        val chosen = all.filterKeys { key -> HealKinds.entries.any { it.field == key && it in kinds } }
        return pretty.encodeToString(JsonObject.serializer(), JsonObject(mapOf("format" to kotlinx.serialization.json.JsonPrimitive("pokelife-heal-export")) + chosen))
    }

    fun without(d: HealData, kind: HealKinds): HealData = when (kind) {
        HealKinds.ACTIVITY -> d.copy(activities = emptyList())
        HealKinds.HYDRATION -> d.copy(hydration = emptyList())
        HealKinds.SLEEP -> d.copy(sleep = emptyList())
        HealKinds.MEASUREMENTS -> d.copy(measurements = emptyList())
        HealKinds.GOALS -> d.copy(goals = emptyList())
    }

}
