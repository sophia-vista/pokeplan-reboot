package app.pokeplan.app.heal

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
import app.pokeplan.heal.Imported
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
import kotlinx.datetime.atStartOfDayIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.random.Random
import app.pokeplan.app.World
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.SavedDocument
import app.pokeplan.app.storage.Storage
import app.pokeplan.app.storage.newId

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

    /** Picks up what another store in this process (a widget, a notification) wrote; cheap when nothing was. */
    fun sync() = document.sync()

    private fun update(change: (HealData) -> HealData) = document.update(change)

    /** Puts back a [snapshot] taken before a delete, for the undo bar. */
    fun restore(snapshot: HealData) = update { snapshot }

    private fun newId(): String = newId(nowMillis, rng)

    /** Noon of [day] on this phone, for a record made for a day other than today. */
    fun noonOf(day: LocalDate): Long = day.atStartOfDayIn(zone).toEpochMilliseconds() + 12 * 3_600_000L

    private fun dayOf(millis: Long): LocalDate = millis.toLocalDateTime(zone).date

    private fun pay(kind: HealKind, recordId: String, eventKey: String = recordId): List<Reward>? =
        if (!data.settings.fieldResearch) null else world.heal(HealEvent("heal:${kind.name.lowercase()}:$eventKey", kind, nowMillis, recordId))

    // --- activity -----------------------------------------------------------------------------------------------------

    /**
     * Logs an activity that ended at [endedAtMillis] (now by default) and lasted [minutes]. Quick: a type and a duration are enough. It is paid by
     * Field Research when that is on and it qualifies; an older session is kept in history and pays nothing.
     */
    fun logActivity(type: ActivityType, minutes: Int, endedAtMillis: Long = nowMillis, distanceMeters: Int? = null, note: String = ""): Logged<ActivityRecord> {
        (HealRules.checkActivity(minutes, distanceMeters) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val start = endedAtMillis - minutes * 60_000L
        val draft = ActivityRecord(newId(), type, start, minutes, dayOf(start), distanceMeters, note.trim().take(HealRules.NOTE_MAX), createdMillis = nowMillis)
        val rewards = if (HealRules.activityPays(draft, nowMillis, HealRules.activitiesPaidOn(data, draft.day))) pay(HealKind.ACTIVITY, draft.id) else null
        // One write: the record goes in already marked, so a save costs one pass over the sealed rows, not two.
        val record = draft.copy(paid = rewards != null)
        update { it.copy(activities = it.activities + record) }
        return Logged(record, rewards = rewards.orEmpty())
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
        val draft = HydrationEntry(newId(), atMillis, dayOf(atMillis), ml, drink.trim().take(60))
        val rewards = if (HealRules.participationPays(draft, nowMillis, HealRules.participationPaidOn(data, draft.day))) pay(HealKind.PARTICIPATION, draft.id) else null
        val entry = draft.copy(paid = rewards != null)
        update { it.copy(hydration = it.hydration + entry) }
        return Logged(entry, rewards = rewards.orEmpty())
    }

    /** Changes a drink's amount, name or moment (the day follows the moment). Its payment is kept as it was: an edit never pays and never takes back. */
    fun updateDrink(e: HydrationEntry): Logged<HydrationEntry> {
        (HealRules.checkHydration(e.ml) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val old = data.hydration.firstOrNull { it.id == e.id } ?: return Logged(null, "That drink is gone.")
        val next = e.copy(day = dayOf(e.atMillis), drink = e.drink.trim().take(60), paid = old.paid, source = old.source, provenance = old.provenance)
        update { d -> d.copy(hydration = d.hydration.map { if (it.id == e.id) next else it }) }
        return Logged(next)
    }

    fun deleteDrink(id: String) = update { d -> d.copy(hydration = d.hydration.filterNot { it.id == id }) }

    // --- sleep (never pays) -------------------------------------------------------------------------------------------

    fun logSleep(startMillis: Long, endMillis: Long, note: String = ""): Logged<SleepRecord> {
        (HealRules.checkSleep(startMillis, endMillis) as? Check.Refused)?.let { return Logged(null, it.reason) }
        // Only a finished night is a record (section 2.2), as for an imported one.
        if (endMillis > nowMillis + HealRules.FUTURE_SLACK_MILLIS) return Logged(null, "That night is not over yet.")
        val record = SleepRecord(newId(), startMillis, endMillis, dayOf(endMillis), note.trim().take(HealRules.NOTE_MAX))
        update { it.copy(sleep = it.sleep + record) }
        return Logged(record)
    }

    /** Changes when a night began and ended, and its note, under the same rules as a new one. Sleep never pays. */
    fun updateSleep(r: SleepRecord): Logged<SleepRecord> {
        (HealRules.checkSleep(r.startMillis, r.endMillis) as? Check.Refused)?.let { return Logged(null, it.reason) }
        if (r.endMillis > nowMillis + HealRules.FUTURE_SLACK_MILLIS) return Logged(null, "That night is not over yet.")
        val old = data.sleep.firstOrNull { it.id == r.id } ?: return Logged(null, "That night is gone.")
        val next = r.copy(day = dayOf(r.endMillis), note = r.note.trim().take(HealRules.NOTE_MAX), source = old.source, provenance = old.provenance)
        update { d -> d.copy(sleep = d.sleep.map { if (it.id == r.id) next else it }) }
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

    /** Changes a measurement's value, unit, label or moment, under the same rules as a new one. */
    fun updateMeasurement(m: Measurement): Logged<Measurement> {
        (HealRules.checkMeasurement(m.type, m.value, m.unit) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val old = data.measurements.firstOrNull { it.id == m.id } ?: return Logged(null, "That measurement is gone.")
        val next = m.copy(day = dayOf(m.atMillis), label = m.label.trim().take(40), source = old.source, provenance = old.provenance)
        update { d -> d.copy(measurements = d.measurements.map { if (it.id == m.id) next else it }) }
        return Logged(next)
    }

    fun deleteMeasurement(id: String) = update { d -> d.copy(measurements = d.measurements.filterNot { it.id == id }) }

    // --- steps and readings (read from outside, never typed, so only deleted) ------------------------------------------

    fun deleteSteps(id: String) = update { d -> d.copy(steps = d.steps.filterNot { it.id == id }) }
    fun deleteVital(id: String) = update { d -> d.copy(vitals = d.vitals.filterNot { it.id == id }) }

    /** Deletes a day of a reading group (every reading of its types on that day), or that day's steps. */
    fun deleteReadingDay(group: app.pokeplan.heal.ReadingGroup, day: kotlinx.datetime.LocalDate) = update { d ->
        if (group == app.pokeplan.heal.ReadingGroup.STEPS) d.copy(steps = d.steps.filterNot { it.day == day })
        else d.copy(vitals = d.vitals.filterNot { it.type in group.types && it.day == day })
    }

    // --- goals (optional; never pay, never punish) -----------------------------------------------------------------------

    fun addGoal(kind: GoalKind, target: Int): HealGoal? {
        if (!HealRules.goalTargetOk(kind, target)) return null
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
     * it and flagged. Health Connect records are never a Field Research event (HEAL.md 10.3); sessions from a provider in [HealImport.PAYING_PROVIDERS]
     * (Huawei Health's pages) pay exactly like manual ones, oldest first, under the same freshness, length and daily rules. Returns the plan that was applied.
     */
    fun importFromProvider(read: ProviderRead): ImportPlan {
        val plan = previewImport(read)
        val before = data
        var added = HealImport.apply(before, plan, nowMillis, ::newId, ::dayOf)
        val known = before.activities.mapTo(HashSet()) { it.id }
        val paidIds = HashSet<String>()
        added.activities.filter { it.id !in known && HealRules.paysLikeManual(it) }.sortedBy { it.startMillis }.forEach { a ->
            val paidThatDay = HealRules.activitiesPaidOn(added, a.day) + paidIds.count { id -> added.activities.first { it.id == id }.day == a.day }
            if (HealRules.activityPays(a, nowMillis, paidThatDay) && pay(HealKind.ACTIVITY, a.id) != null) paidIds += a.id
        }
        if (paidIds.isNotEmpty()) added = added.copy(activities = added.activities.map { if (it.id in paidIds) it.copy(paid = true) else it })
        added = payStepsAndImports(added, plan)
        val result = added
        val huawei = read.records.any { it.provider == HealImport.PROVIDER_HUAWEI_SCREEN }
        update { if (read.failed || huawei) result else result.copy(settings = result.settings.copy(healthLastSyncMillis = nowMillis)) }
        return plan
    }

    /**
     * Field Research for steps and for reading records in (owner, 14 November 2026). **Steps** pay per amount: one payment for each full [HealRules.STEPS_PER_REWARD] steps of
     * *today*, in order, at most [HealRules.STEPS_PAID_PER_DAY] a day, so a read that shows more steps pays only the new amounts. **A read** pays once a day for each family of
     * record (workouts that do not pay for themselves, sleep, body measurements, drinks, heart rate and the other readings) that brought something new and recent, at most
     * [HealRules.IMPORTS_PAID_PER_DAY] a day. Each payment leaves a mark in the sealed settings, so deleting a record and reading it again pays nothing twice. Needs Field Research on.
     */
    private fun payStepsAndImports(d: HealData, plan: ImportPlan): HealData {
        var data = d
        val today = dayOf(nowMillis)
        val marks = HashSet(HealRules.pruneMarks(data.settings.paidMarks, today))
        // steps: today's record only (the daily limit counts today)
        data.steps.firstOrNull { it.day == today }?.let { rec ->
            var paid = maxOf(rec.paidBuckets, HealRules.stepsPaidOn(data, today))
            val reached = HealRules.stepPayments(rec.steps)
            while (paid < reached) {
                if (pay(HealKind.STEPS, rec.id, "${rec.id}:${paid + 1}") == null) break
                paid++
                marks += HealRules.stepMark(today, paid)
            }
            if (paid != rec.paidBuckets) data = data.copy(steps = data.steps.map { if (it.id == rec.id) it.copy(paidBuckets = paid) else it })
        }
        // reading records in: a family that brought something new, once a day
        val families = plan.candidates.filter { it.updates == null && HealRules.importCounts(it.record.family, it.record.provider, HealImport.happenedAt(it.record), nowMillis) }
            .map { it.record.family }.distinct().sortedBy { it.ordinal }
        var paidToday = marks.count { it.startsWith("import|$today|") }
        for (family in families) {
            if (paidToday >= HealRules.IMPORTS_PAID_PER_DAY) break
            val mark = HealRules.importMark(today, family)
            if (mark in marks) continue
            if (pay(HealKind.IMPORT, newId()) == null) break
            marks += mark
            paidToday++
        }
        return if (marks == data.settings.paidMarks) data else data.copy(settings = data.settings.copy(paidMarks = marks))
    }

    /** The records the Huawei Health reader's captures hold now (INTEGRATIONS_AND_PRIVACY.md 5.2.2), as a read to preview and import like any provider's. */
    fun readHuawei(seen: HuaweiSeen): ProviderRead {
        val records = HuaweiRecords.read(seen, nowMillis, zone)
        val from = records.minOfOrNull { (it as? app.pokeplan.heal.Imported.Exercise)?.startMillis ?: (it as? app.pokeplan.heal.Imported.Sleep)?.startMillis ?: (it as? app.pokeplan.heal.Imported.Body)?.atMillis ?: HealImport.happenedAt(it) } ?: nowMillis
        return ProviderRead(records, from, nowMillis)
    }

    /** What HEAL already holds from Huawei Health, so the next read skips what has nothing new (`HuaweiWalk.Known`). */
    fun huaweiKnown(): HuaweiWalk.Known {
        val d = data
        fun huawei(p: app.pokeplan.heal.Provenance?) = p?.provider == HealImport.PROVIDER_HUAWEI_SCREEN
        return HuaweiWalk.Known(
            today = dayOf(nowMillis),
            newestWorkoutStart = d.activities.filter { huawei(it.provenance) }.maxOfOrNull { it.startMillis },
            nightDays = d.sleep.map { dayOf(it.endMillis) }.toSet(),
            newestDay = app.pokeplan.heal.ReadingGroup.entries.mapNotNull { g ->
                (if (g == app.pokeplan.heal.ReadingGroup.STEPS) d.steps.filter { huawei(it.provenance) }.map { it.day } else d.vitals.filter { it.type in g.types && huawei(it.provenance) }.map { it.day }).maxOrNull()?.let { g to it }
            }.toMap(),
            newestWeightDay = d.measurements.filter { it.type == app.pokeplan.heal.MeasureType.WEIGHT && huawei(it.provenance) }.maxOfOrNull { it.atMillis }?.let { dayOf(it) },
        )
    }

    /** Huawei Health was just read: the reading reminder counts from now. */
    fun markHuaweiRead() = settings { it.copy(huaweiLastReadMillis = nowMillis) }

    /** The reminder to read Huawei Health: every [everyDays] days (1 to 7) at [at], or off with null. */
    fun setHuaweiReminder(everyDays: Int?, at: LocalTime) = settings { it.copy(huaweiEveryDays = everyDays?.coerceIn(1, 7), huaweiRemindAt = at) }

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

    /** The full-device reset: HEAL as new, its records and its settings. */
    fun resetAll() = update { HealData() }

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
        HealKinds.STEPS -> d.copy(steps = emptyList())
        HealKinds.VITALS -> d.copy(vitals = emptyList())
    }

}
