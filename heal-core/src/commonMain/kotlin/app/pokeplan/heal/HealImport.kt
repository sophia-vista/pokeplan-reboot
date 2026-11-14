package app.pokeplan.heal

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The record types HEAL can read from Health Connect, one Android permission each (INTEGRATIONS_AND_PRIVACY.md 5.1: only the record types the player selects,
 * read only). This list is also the whole of what HEAL imports: [unsupported] says, in words, what it does not and why, so nothing is implied.
 */
@Serializable
enum class HealthKind(val label: String, val reads: String, val kind: HealKindOf) {
    EXERCISE("Exercise sessions", "the type, the start and how long it lasted", HealKindOf.ACTIVITY),
    SLEEP("Sleep sessions", "when you fell asleep and woke up (no stages, no score)", HealKindOf.SLEEP),
    WEIGHT("Weight", "the weight and when it was measured", HealKindOf.MEASUREMENTS),
    BODY_FAT("Body fat", "the percentage and when it was measured", HealKindOf.MEASUREMENTS),
    HYDRATION("Drinks", "the volume and the time", HealKindOf.HYDRATION);

    companion object {
        /** What is **not** imported, and the reason; shown on the connection page so an absent metric is never taken for zero (HEAL.md 10.1, "do not assume"). */
        val unsupported: List<Pair<String, String>> = listOf(
            "Steps, heart rate, blood oxygen, temperature, stress and sleep score from Health Connect" to "only Huawei Health's own pages carry these into HEAL (as history, never judged); Health Connect is asked for none of them.",
            "Heart rate variability (HRV)" to "HEAL has no record for it, so it is not read.",
            "Distance on its own, calories burned" to "HEAL never turns a number into a target; a session carries its own distance.",
            "Sleep stages" to "a night is one start and one end.",
            "Nutrition, medication, symptoms" to "not part of this import.",
        )
    }
}

/** Which of HEAL's own families of record a [HealthKind] lands in. */
enum class HealKindOf { ACTIVITY, SLEEP, MEASUREMENTS, HYDRATION, STEPS, VITALS }

/** A provider-neutral record read from outside (INTEGRATIONS_AND_PRIVACY.md 3.1: no vendor object ever reaches HEAL's domain). [app] is the writing app's package, [id] the provider's own id. */
sealed interface Imported {
    val provider: String
    val app: String
    val id: String
    /** The Health Connect permission this record needs; null for a record only Huawei Health's pages carry (steps, vitals). */
    val kind: HealthKind?
    val family: HealKindOf

    data class Exercise(override val provider: String, override val app: String, override val id: String, val type: ActivityType, val startMillis: Long, val endMillis: Long, val distanceMeters: Int? = null) : Imported {
        override val kind get() = HealthKind.EXERCISE
        override val family get() = HealKindOf.ACTIVITY
    }
    data class Sleep(override val provider: String, override val app: String, override val id: String, val startMillis: Long, val endMillis: Long) : Imported {
        override val kind get() = HealthKind.SLEEP
        override val family get() = HealKindOf.SLEEP
    }
    data class Body(override val provider: String, override val app: String, override val id: String, val type: MeasureType, val value: Double, val unit: String, val atMillis: Long) : Imported {
        override val kind get() = if (type == MeasureType.BODY_FAT) HealthKind.BODY_FAT else HealthKind.WEIGHT
        override val family get() = HealKindOf.MEASUREMENTS
    }
    data class Drink(override val provider: String, override val app: String, override val id: String, val atMillis: Long, val ml: Int) : Imported {
        override val kind get() = HealthKind.HYDRATION
        override val family get() = HealKindOf.HYDRATION
    }
    /** A day's step count as the provider showed it at [atMillis]; the id is one per day, so a later read of the same day updates the count. */
    data class Steps(override val provider: String, override val app: String, override val id: String, val steps: Int, val atMillis: Long) : Imported {
        override val kind: HealthKind? get() = null
        override val family get() = HealKindOf.STEPS
    }
    /** A heart rate, resting heart rate, SpO2, skin temperature, stress or sleep score reading, [value] in the type's own unit (skin temperature in °C). */
    data class Vital(override val provider: String, override val app: String, override val id: String, val type: VitalType, val value: Double, val atMillis: Long) : Imported {
        override val kind: HealthKind? get() = null
        override val family get() = HealKindOf.VITALS
    }
}

/** What one read of a provider gave: the records, the window that was asked for and whether the read failed part-way (a partial read is still imported, and the rest can be retried). */
data class ProviderRead(val records: List<Imported>, val fromMillis: Long, val toMillis: Long, val failed: Boolean = false)

/** One record that will be added, and the id of the record it may duplicate (kept, flagged), if any. [updates] is the id of the record of the same provider and day it brings a newer value for (steps, vitals); null for a new one. */
data class Candidate(val record: Imported, val possibleDuplicateOf: String? = null, val updates: String? = null)

/** What an import would do, shown before anything is saved (HEAL.md 10.3). [alreadyHere] were imported before; [rejected] could not be read as a valid record and are not stored. */
data class ImportPlan(val candidates: List<Candidate>, val alreadyHere: Int, val rejected: Int) {
    fun count(kind: HealthKind) = candidates.count { it.record.kind == kind && it.updates == null }
    /** New records of a family only Huawei Health's pages carry. */
    fun count(family: HealKindOf) = candidates.count { it.record.family == family && it.updates == null }
    /** Records already here that this read brings a newer value for. */
    val updated: Int get() = candidates.count { it.updates != null }
    val flagged: Int get() = candidates.count { it.possibleDuplicateOf != null }
    val isEmpty: Boolean get() = candidates.isEmpty()
}

/**
 * Importing from a provider, pure (INTEGRATIONS_AND_PRIVACY.md 5.1; HEAL.md 10.3 and 10.4). The rules, in order of importance:
 * 1. **Never overwrite or delete a record the player wrote.** A possible match is added beside it, both flagged.
 * 2. **Idempotent:** a record with a provider id that is already here is skipped, so a retry, a second read or a read after a crash adds nothing twice.
 * 3. **No reward from here.** Imported records carry [SourceKind.IMPORTED] and nothing here calls the world. Only a manual session, or one from a provider in
 *    [PAYING_PROVIDERS] that is not flagged as a possible duplicate, can pay (`HealRules.activityPays`), and the store decides that after applying.
 * 4. **Missing is not zero**, and a record that cannot be a valid one (negative, impossible, in the future) is rejected, never repaired.
 */
object HealImport {
    const val PROVIDER_HEALTH_CONNECT = "healthconnect"

    /** Records PokéLife read from Huawei Health's own pages on the phone (INTEGRATIONS_AND_PRIVACY.md 5.2.2). Its sessions pay like manual ones (owner, 14 November 2026). */
    const val PROVIDER_HUAWEI_SCREEN = "huawei-screen"

    /** Providers whose imported sessions Field Research pays exactly like manual ones ([HealRules.activityPays]). Health Connect is not one of them. */
    val PAYING_PROVIDERS: Set<String> = setOf(PROVIDER_HUAWEI_SCREEN)

    private const val MINUTE = 60_000L

    /** The most a read may reach back: Health Connect itself lets an app read only the 30 days before the permission was granted, and HEAL asks for no history permission. */
    const val MAX_DAYS_BACK = 30

    fun plan(data: HealData, read: List<Imported>, nowMillis: Long): ImportPlan {
        val have = (data.activities.mapNotNull { it.provenance } + data.hydration.mapNotNull { it.provenance } + data.sleep.mapNotNull { it.provenance } + data.measurements.mapNotNull { it.provenance } +
            data.steps.mapNotNull { it.provenance } + data.vitals.mapNotNull { it.provenance })
            .map { it.provider to it.externalId }.toHashSet()
        var already = 0; var rejected = 0
        val seen = HashSet<Pair<String, String>>()
        val out = ArrayList<Candidate>()
        for (r in read) {
            val key = r.provider to r.id
            if (!seen.add(key)) { already++; continue }
            if (!valid(r, nowMillis)) { rejected++; continue }
            if (key in have) {
                // Steps and vitals are one per day: a newer value for a day already here updates it; anything else already here is skipped.
                val id = newerThanStored(data, r)
                if (id != null) out += Candidate(r, updates = id) else already++
                continue
            }
            out += Candidate(r, duplicateOf(data, r))
        }
        return ImportPlan(out, already, rejected)
    }

    /** The id of the stored record [r] brings a newer value for, or null: only steps (a larger count) and the once-a-day vitals (a different value) can be updated. */
    private fun newerThanStored(data: HealData, r: Imported): String? = when (r) {
        is Imported.Steps -> data.steps.firstOrNull { it.provenance?.provider == r.provider && it.provenance.externalId == r.id }?.takeIf { r.steps > it.steps }?.id
        is Imported.Vital -> if (r.type == VitalType.HEART_RATE) null
            else data.vitals.firstOrNull { it.provenance?.provider == r.provider && it.provenance.externalId == r.id }?.takeIf { r.atMillis > it.atMillis && r.value != it.value }?.id
        else -> null
    }

    /** When the record happened: the end of a session or night, else its own time. */
    fun happenedAt(r: Imported): Long = when (r) {
        is Imported.Exercise -> r.endMillis
        is Imported.Sleep -> r.endMillis
        is Imported.Body -> r.atMillis
        is Imported.Drink -> r.atMillis
        is Imported.Steps -> r.atMillis
        is Imported.Vital -> r.atMillis
    }

    private fun minutesOf(start: Long, end: Long) = ((end - start) / MINUTE.toDouble()).roundToInt().coerceAtLeast(1)

    private fun valid(r: Imported, now: Long): Boolean = when (r) {
        is Imported.Exercise -> r.endMillis > r.startMillis && r.endMillis <= now + HealRules.FUTURE_SLACK_MILLIS &&
            HealRules.checkActivity(minutesOf(r.startMillis, r.endMillis), r.distanceMeters) is Check.Ok
        is Imported.Sleep -> r.endMillis <= now + HealRules.FUTURE_SLACK_MILLIS && HealRules.checkSleep(r.startMillis, r.endMillis) is Check.Ok
        is Imported.Body -> r.atMillis <= now + HealRules.FUTURE_SLACK_MILLIS && HealRules.checkMeasurement(r.type, r.value, r.unit) is Check.Ok
        is Imported.Drink -> r.atMillis <= now + HealRules.FUTURE_SLACK_MILLIS && HealRules.checkHydration(r.ml) is Check.Ok
        is Imported.Steps -> r.atMillis <= now + HealRules.FUTURE_SLACK_MILLIS && HealRules.checkSteps(r.steps) is Check.Ok
        is Imported.Vital -> r.atMillis <= now + HealRules.FUTURE_SLACK_MILLIS && HealRules.checkVital(r.type, r.value) is Check.Ok
    }

    /** The id of an existing record (of any source) that [r] may be the same event as, by a conservative match on time and size (HEAL.md 10.4, step 3); null when none. */
    private fun duplicateOf(data: HealData, r: Imported): String? = when (r) {
        is Imported.Exercise -> data.activities.firstOrNull { abs(it.startMillis - r.startMillis) <= 2 * MINUTE && abs(it.minutes - minutesOf(r.startMillis, r.endMillis)) <= 2 }?.id
        is Imported.Sleep -> data.sleep.firstOrNull { abs(it.startMillis - r.startMillis) <= 15 * MINUTE && abs(it.endMillis - r.endMillis) <= 15 * MINUTE }?.id
        is Imported.Body -> data.measurements.firstOrNull { m ->
            m.type == r.type && abs(m.atMillis - r.atMillis) <= 5 * MINUTE && (HealUnits.convert(m.value, m.unit, r.unit)?.let { abs(it - r.value) < 0.06 } ?: false)
        }?.id
        is Imported.Drink -> data.hydration.firstOrNull { it.ml == r.ml && abs(it.atMillis - r.atMillis) <= 2 * MINUTE }?.id
        // A step count or a reading is never something the player wrote, and one per day replaces itself, so it can not repeat a record of another source.
        is Imported.Steps, is Imported.Vital -> null
    }

    /**
     * Adds the [plan]'s candidates to [data] and returns the result. [newId] makes a record's id, [dayOf] the local day (fixed at import, like every record).
     * Nothing already in [data] changes except nothing: a flagged match keeps its own record exactly as it was.
     */
    fun apply(data: HealData, plan: ImportPlan, nowMillis: Long, newId: () -> String, dayOf: (Long) -> LocalDate): HealData {
        var d = data
        for (c in plan.candidates) {
            val r = c.record
            val prov = Provenance(r.provider, r.app, r.id, nowMillis, c.possibleDuplicateOf)
            d = when (r) {
                is Imported.Exercise -> {
                    val minutes = minutesOf(r.startMillis, r.endMillis)
                    d.copy(activities = d.activities + ActivityRecord(newId(), r.type, r.startMillis, minutes, dayOf(r.startMillis), r.distanceMeters, "", SourceKind.IMPORTED, nowMillis, paid = false, provenance = prov))
                }
                is Imported.Sleep -> d.copy(sleep = d.sleep + SleepRecord(newId(), r.startMillis, r.endMillis, dayOf(r.endMillis), "", SourceKind.IMPORTED, prov))
                is Imported.Body -> d.copy(measurements = d.measurements + Measurement(newId(), r.type, r.value, r.unit, r.atMillis, dayOf(r.atMillis), source = SourceKind.IMPORTED, provenance = prov))
                is Imported.Drink -> d.copy(hydration = d.hydration + HydrationEntry(newId(), r.atMillis, dayOf(r.atMillis), r.ml, "", SourceKind.IMPORTED, paid = false, provenance = prov))
                is Imported.Steps -> if (c.updates != null) d.copy(steps = d.steps.map { if (it.id == c.updates) it.copy(steps = r.steps, updatedMillis = r.atMillis) else it })
                    else d.copy(steps = d.steps + StepsRecord(newId(), dayOf(r.atMillis), r.steps, r.atMillis, provenance = prov))
                is Imported.Vital -> if (c.updates != null) d.copy(vitals = d.vitals.map { if (it.id == c.updates) it.copy(value = r.value, atMillis = r.atMillis, day = dayOf(r.atMillis)) else it })
                    else d.copy(vitals = d.vitals + VitalReading(newId(), r.type, r.value, r.atMillis, dayOf(r.atMillis), provenance = prov))
            }
        }
        return d
    }

    // --- sources, flags and removal ---------------------------------------------------------------------------------------

    /** The imported records by the app that wrote them (for Health Connect, a package name) and by family, so the player sees what came from where. */
    data class SourceCount(val provider: String, val app: String, val activities: Int, val sleep: Int, val measurements: Int, val hydration: Int, val steps: Int = 0, val vitals: Int = 0) {
        val total get() = activities + sleep + measurements + hydration + steps + vitals
    }

    fun sources(data: HealData): List<SourceCount> {
        val all = data.activities.mapNotNull { it.provenance }.map { it to 0 } + data.sleep.mapNotNull { it.provenance }.map { it to 1 } +
            data.measurements.mapNotNull { it.provenance }.map { it to 2 } + data.hydration.mapNotNull { it.provenance }.map { it to 3 } +
            data.steps.mapNotNull { it.provenance }.map { it to 4 } + data.vitals.mapNotNull { it.provenance }.map { it to 5 }
        return all.groupBy { it.first.provider to it.first.app }.map { (k, v) ->
            SourceCount(k.first, k.second, v.count { it.second == 0 }, v.count { it.second == 1 }, v.count { it.second == 2 }, v.count { it.second == 3 }, v.count { it.second == 4 }, v.count { it.second == 5 })
        }.sortedByDescending { it.total }
    }

    /** Imported records that may repeat one already here, newest first: (record id, family) pairs for the screen to list. */
    fun flagged(data: HealData): List<Pair<String, HealKindOf>> =
        (data.activities.filter { it.provenance?.possibleDuplicateOf != null }.map { it.id to HealKindOf.ACTIVITY } +
            data.sleep.filter { it.provenance?.possibleDuplicateOf != null }.map { it.id to HealKindOf.SLEEP } +
            data.measurements.filter { it.provenance?.possibleDuplicateOf != null }.map { it.id to HealKindOf.MEASUREMENTS } +
            data.hydration.filter { it.provenance?.possibleDuplicateOf != null }.map { it.id to HealKindOf.HYDRATION })

    /** [data] without the imported records of [provider] (and, if given, of [app] only). Records the player wrote are never touched, whatever they were flagged against. */
    fun withoutImported(data: HealData, provider: String, app: String? = null): HealData {
        fun gone(p: Provenance?) = p != null && p.provider == provider && (app == null || p.app == app)
        return data.copy(
            activities = data.activities.filterNot { gone(it.provenance) },
            sleep = data.sleep.filterNot { gone(it.provenance) },
            measurements = data.measurements.filterNot { gone(it.provenance) },
            hydration = data.hydration.filterNot { gone(it.provenance) },
            steps = data.steps.filterNot { gone(it.provenance) },
            vitals = data.vitals.filterNot { gone(it.provenance) },
        )
    }

    /** [data] with the "possible duplicate" flag taken off one record: the player looked and wants both. */
    fun keepBoth(data: HealData, id: String): HealData {
        fun Provenance?.clear() = this?.copy(possibleDuplicateOf = null)
        return data.copy(
            activities = data.activities.map { if (it.id == id) it.copy(provenance = it.provenance.clear()) else it },
            sleep = data.sleep.map { if (it.id == id) it.copy(provenance = it.provenance.clear()) else it },
            measurements = data.measurements.map { if (it.id == id) it.copy(provenance = it.provenance.clear()) else it },
            hydration = data.hydration.map { if (it.id == id) it.copy(provenance = it.provenance.clear()) else it },
        )
    }
}
