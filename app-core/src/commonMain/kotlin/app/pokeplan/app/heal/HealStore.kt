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

data class Logged<T>(val record: T?, val refused: String? = null, val rewards: List<Reward> = emptyList())

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

    fun reload() = document.reload()

    fun sync() = document.sync()

    private fun update(change: (HealData) -> HealData) = document.update(change)

    fun restore(snapshot: HealData) = update { snapshot }

    private fun newId(): String = newId(nowMillis, rng)

    fun noonOf(day: LocalDate): Long = day.atStartOfDayIn(zone).toEpochMilliseconds() + 12 * 3_600_000L

    private fun dayOf(millis: Long): LocalDate = millis.toLocalDateTime(zone).date

    private fun pay(kind: HealKind, recordId: String, eventKey: String = recordId): List<Reward>? =
        if (!data.settings.fieldResearch) null else world.heal(HealEvent("heal:${kind.name.lowercase()}:$eventKey", kind, nowMillis, recordId))

    fun logActivity(type: ActivityType, minutes: Int, endedAtMillis: Long = nowMillis, distanceMeters: Int? = null, note: String = ""): Logged<ActivityRecord> {
        (HealRules.checkActivity(minutes, distanceMeters) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val start = endedAtMillis - minutes * 60_000L
        val draft = ActivityRecord(newId(), type, start, minutes, dayOf(start), distanceMeters, note.trim().take(HealRules.NOTE_MAX), createdMillis = nowMillis)
        val rewards = if (HealRules.activityPays(draft, nowMillis, HealRules.activitiesPaidOn(data, draft.day))) pay(HealKind.ACTIVITY, draft.id) else null
        val record = draft.copy(paid = rewards != null)
        update { it.copy(activities = it.activities + record) }
        return Logged(record, rewards = rewards.orEmpty())
    }

    fun updateActivity(a: ActivityRecord): Logged<ActivityRecord> {
        (HealRules.checkActivity(a.minutes, a.distanceMeters) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val old = data.activities.firstOrNull { it.id == a.id } ?: return Logged(null, "That activity is gone.")
        val next = a.copy(paid = old.paid, source = old.source, provenance = old.provenance, day = dayOf(a.startMillis), note = a.note.trim().take(HealRules.NOTE_MAX))
        update { d -> d.copy(activities = d.activities.map { if (it.id == a.id) next else it }) }
        return Logged(next)
    }

    fun deleteActivity(id: String) = update { d -> d.copy(activities = d.activities.filterNot { it.id == id }) }

    fun logDrink(ml: Int, drink: String = "", atMillis: Long = nowMillis): Logged<HydrationEntry> {
        (HealRules.checkHydration(ml) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val draft = HydrationEntry(newId(), atMillis, dayOf(atMillis), ml, drink.trim().take(60))
        val rewards = if (HealRules.participationPays(draft, nowMillis, HealRules.participationPaidOn(data, draft.day))) pay(HealKind.PARTICIPATION, draft.id) else null
        val entry = draft.copy(paid = rewards != null)
        update { it.copy(hydration = it.hydration + entry) }
        return Logged(entry, rewards = rewards.orEmpty())
    }

    fun updateDrink(e: HydrationEntry): Logged<HydrationEntry> {
        (HealRules.checkHydration(e.ml) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val old = data.hydration.firstOrNull { it.id == e.id } ?: return Logged(null, "That drink is gone.")
        val next = e.copy(day = dayOf(e.atMillis), drink = e.drink.trim().take(60), paid = old.paid, source = old.source, provenance = old.provenance)
        update { d -> d.copy(hydration = d.hydration.map { if (it.id == e.id) next else it }) }
        return Logged(next)
    }

    fun deleteDrink(id: String) = update { d -> d.copy(hydration = d.hydration.filterNot { it.id == id }) }

    fun logSleep(startMillis: Long, endMillis: Long, note: String = ""): Logged<SleepRecord> {
        (HealRules.checkSleep(startMillis, endMillis) as? Check.Refused)?.let { return Logged(null, it.reason) }
        if (endMillis > nowMillis + HealRules.FUTURE_SLACK_MILLIS) return Logged(null, "That night is not over yet.")
        val record = SleepRecord(newId(), startMillis, endMillis, dayOf(endMillis), note.trim().take(HealRules.NOTE_MAX))
        update { it.copy(sleep = it.sleep + record) }
        return Logged(record)
    }

    fun updateSleep(r: SleepRecord): Logged<SleepRecord> {
        (HealRules.checkSleep(r.startMillis, r.endMillis) as? Check.Refused)?.let { return Logged(null, it.reason) }
        if (r.endMillis > nowMillis + HealRules.FUTURE_SLACK_MILLIS) return Logged(null, "That night is not over yet.")
        val old = data.sleep.firstOrNull { it.id == r.id } ?: return Logged(null, "That night is gone.")
        val next = r.copy(day = dayOf(r.endMillis), note = r.note.trim().take(HealRules.NOTE_MAX), source = old.source, provenance = old.provenance)
        update { d -> d.copy(sleep = d.sleep.map { if (it.id == r.id) next else it }) }
        return Logged(next)
    }

    fun deleteSleep(id: String) = update { d -> d.copy(sleep = d.sleep.filterNot { it.id == id }) }

    fun logMeasurement(type: MeasureType, value: Double, unit: String, label: String = "", atMillis: Long = nowMillis): Logged<Measurement> {
        (HealRules.checkMeasurement(type, value, unit) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val record = Measurement(newId(), type, value, unit, atMillis, dayOf(atMillis), label.trim().take(40))
        update { it.copy(measurements = it.measurements + record) }
        return Logged(record)
    }

    fun updateMeasurement(m: Measurement): Logged<Measurement> {
        (HealRules.checkMeasurement(m.type, m.value, m.unit) as? Check.Refused)?.let { return Logged(null, it.reason) }
        val old = data.measurements.firstOrNull { it.id == m.id } ?: return Logged(null, "That measurement is gone.")
        val next = m.copy(day = dayOf(m.atMillis), label = m.label.trim().take(40), source = old.source, provenance = old.provenance)
        update { d -> d.copy(measurements = d.measurements.map { if (it.id == m.id) next else it }) }
        return Logged(next)
    }

    fun deleteMeasurement(id: String) = update { d -> d.copy(measurements = d.measurements.filterNot { it.id == id }) }

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

    fun setGoalStatus(id: String, status: GoalStatus) =
        update { d -> d.copy(goals = d.goals.map { if (it.id == id) it.copy(status = status) else it }) }

    fun deleteGoal(id: String) = update { d -> d.copy(goals = d.goals.filterNot { it.id == id }) }

    private fun settings(change: (HealSettings) -> HealSettings) = update { it.copy(settings = change(it.settings)) }

    fun setFieldResearch(on: Boolean) = settings { it.copy(fieldResearch = on) }

    fun setHydrationTarget(ml: Int?) = settings { it.copy(hydrationTargetMl = ml?.takeIf { v -> v in 100..20_000 }) }

    fun setShowMeasurementsOnToday(on: Boolean) = settings { it.copy(showMeasurementsOnToday = on) }

    fun setWeightUnit(unit: String) = settings { it.copy(weightUnit = if (unit == "lb") "lb" else "kg") }

    fun setHydrationReminders(times: List<LocalTime>) = settings { it.copy(hydrationReminders = times.distinct().sorted().take(HealSettings.MAX_REMINDERS)) }

    fun markTutorialSeen() = settings { it.copy(tutorialSeen = true) }

    fun previewImport(read: ProviderRead): ImportPlan = HealImport.plan(data, read.records, nowMillis)

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

    private fun payStepsAndImports(d: HealData, plan: ImportPlan): HealData {
        var data = d
        val today = dayOf(nowMillis)
        val marks = HashSet(HealRules.pruneMarks(data.settings.paidMarks, today))
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
            // The newest day of each All data list: only the rows that list gives (ranges, lengths, steps), not the single value the Health page shows for today.
            newestDay = app.pokeplan.heal.ReadingGroup.entries.mapNotNull { g ->
                val types = when (g) {
                    app.pokeplan.heal.ReadingGroup.HEART -> setOf(app.pokeplan.heal.VitalType.HEART_RATE_LOW, app.pokeplan.heal.VitalType.HEART_RATE_HIGH)
                    app.pokeplan.heal.ReadingGroup.SPO2 -> setOf(app.pokeplan.heal.VitalType.SPO2_LOW, app.pokeplan.heal.VitalType.SPO2_HIGH)
                    app.pokeplan.heal.ReadingGroup.SKIN_TEMPERATURE -> setOf(app.pokeplan.heal.VitalType.SKIN_TEMPERATURE_LOW, app.pokeplan.heal.VitalType.SKIN_TEMPERATURE_HIGH)
                    app.pokeplan.heal.ReadingGroup.SLEEP -> setOf(app.pokeplan.heal.VitalType.SLEEP_LENGTH)
                    else -> g.types.toSet()
                }
                (if (g == app.pokeplan.heal.ReadingGroup.STEPS) d.steps.filter { huawei(it.provenance) }.map { it.day } else d.vitals.filter { it.type in types && huawei(it.provenance) }.map { it.day }).maxOrNull()?.let { g to it }
            }.toMap(),
            newestWeightDay = d.measurements.filter { it.type == app.pokeplan.heal.MeasureType.WEIGHT && huawei(it.provenance) }.maxOfOrNull { it.atMillis }?.let { dayOf(it) },
        )
    }

    /** Huawei Health was just read: the reading reminder counts from now. */
    fun markHuaweiRead() = settings { it.copy(huaweiLastReadMillis = nowMillis) }

    fun setHuaweiReminder(everyDays: Int?, at: LocalTime) = settings { it.copy(huaweiEveryDays = everyDays?.coerceIn(1, 7), huaweiRemindAt = at) }

    fun setHealthKinds(kinds: Set<HealthKind>) = settings { it.copy(healthKinds = kinds, healthLastSyncMillis = if (kinds.isEmpty()) 0 else it.healthLastSyncMillis) }

    fun importedSources(): List<HealImport.SourceCount> = HealImport.sources(data)

    fun possibleDuplicates(): List<Pair<String, HealKindOf>> = HealImport.flagged(data)

    fun keepBoth(id: String) = update { HealImport.keepBoth(it, id) }

    fun removeImported(provider: String = HealImport.PROVIDER_HEALTH_CONNECT, app: String? = null): Int {
        val before = importedSources().filter { it.provider == provider && (app == null || it.app == app) }.sumOf { it.total }
        update { HealImport.withoutImported(it, provider, app) }
        return before
    }

    fun export(kinds: Set<HealKinds> = HealKinds.entries.toSet()): String = HealExport.json(data, kinds)

    fun erase(kind: HealKinds) = update { HealExport.without(it, kind) }

    fun eraseAll() = update { d -> HealKinds.entries.fold(d) { acc, k -> HealExport.without(acc, k) } }

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
