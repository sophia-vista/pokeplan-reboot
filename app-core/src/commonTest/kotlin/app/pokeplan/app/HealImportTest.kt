package app.pokeplan.app

import app.pokeplan.core.reward.HealEvent
import app.pokeplan.core.reward.Reward
import app.pokeplan.heal.ActivityType
import app.pokeplan.heal.HealImport
import app.pokeplan.heal.HealKindOf
import app.pokeplan.heal.HealthKind
import app.pokeplan.heal.Imported
import app.pokeplan.heal.MeasureType
import app.pokeplan.heal.ProviderRead
import app.pokeplan.heal.SourceKind
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reading Health Connect (INTEGRATIONS_AND_PRIVACY.md 5.1, HEAL.md 10): previewed, idempotent, provenance-keeping, never a reward, and never touching what the player wrote. */
class HealImportTest {
    private class FakeClock(var millis: Long) : Clock { override fun nowMillis() = millis }
    private class Spy(val inner: GameStore) : World {
        val events = mutableListOf<HealEvent>()
        override fun care(event: app.pokeplan.core.reward.CareEvent) = inner.care(event)
        override fun heal(event: HealEvent): List<Reward>? { events += event; return inner.heal(event) }
    }

    private val utc = TimeZone.UTC
    private val now = LocalDateTime(2026, 11, 7, 18, 0).toInstant(utc).toEpochMilliseconds()
    private val hour = 3_600_000L
    private fun setup(): Pair<HealStore, Spy> {
        val clock = FakeClock(now)
        val game = GameStore(MemoryStorage(), clock, utc, Random(7)).also { it.start("Sophia", 25) }
        val spy = Spy(game)
        return HealStore(MemoryStorage(), clock, spy, utc, rng = Random(3)) to spy
    }

    private val hc = HealImport.PROVIDER_HEALTH_CONNECT
    private fun run(id: String, start: Long = now - 3 * hour, minutes: Int = 30) = Imported.Exercise(hc, "com.huawei.health", id, ActivityType.RUN, start, start + minutes * 60_000L)
    private fun read(vararg r: Imported, failed: Boolean = false) = ProviderRead(r.toList(), now - 7 * 24 * hour, now, failed)

    @Test fun importsWithProvenanceAndPaysNothing() {
        val (heal, spy) = setup()
        val plan = heal.importFromProvider(read(run("r1"), Imported.Sleep(hc, "com.huawei.health", "s1", now - 12 * hour, now - 4 * hour), Imported.Body(hc, "com.scale", "w1", MeasureType.WEIGHT, 61.5, "kg", now - hour), Imported.Drink(hc, "com.x", "d1", now - hour, 250)))
        assertEquals(4, plan.candidates.size)
        val a = heal.data.activities.single()
        assertEquals(SourceKind.IMPORTED, a.source); assertEquals("com.huawei.health", a.provenance?.app); assertEquals("r1", a.provenance?.externalId); assertEquals(30, a.minutes)
        assertEquals(1, heal.data.sleep.size); assertEquals(1, heal.data.measurements.size); assertEquals(1, heal.data.hydration.size)
        assertTrue(spy.events.isEmpty(), "an import is not a Field Research event, even for a session that ended 3 hours ago")
        assertTrue(heal.data.activities.none { it.paid } && heal.data.hydration.none { it.paid })
        assertEquals(now, heal.data.settings.healthLastSyncMillis)
    }

    @Test fun aSecondReadOfTheSameRecordsAddsNothing() {
        val (heal, _) = setup()
        val r = read(run("r1"), run("r2", now - 8 * hour))
        heal.importFromProvider(r)
        val again = heal.importFromProvider(r)
        assertEquals(0, again.candidates.size); assertEquals(2, again.alreadyHere)
        assertEquals(2, heal.data.activities.size)
    }

    @Test fun aRecordLikeOneYouWroteIsAddedBesideItAndFlaggedNeverMerged() {
        val (heal, _) = setup()
        val mine = assertNotNull(heal.logActivity(ActivityType.RUN, 30, endedAtMillis = now - 3 * hour + 30 * 60_000L).record)
        val plan = heal.importFromProvider(read(run("r1")))
        assertEquals(1, plan.flagged)
        assertEquals(2, heal.data.activities.size)
        val mineAfter = heal.data.activities.first { it.id == mine.id }
        assertEquals(mine, mineAfter, "the record the player wrote is exactly as it was")
        val imported = heal.data.activities.first { it.id != mine.id }
        assertEquals(mine.id, imported.provenance?.possibleDuplicateOf)
        assertEquals(listOf(imported.id to HealKindOf.ACTIVITY), heal.possibleDuplicates())
        heal.keepBoth(imported.id)
        assertTrue(heal.possibleDuplicates().isEmpty())
    }

    @Test fun invalidRecordsAreRejectedNotRepairedAndTheRestStillImports() {
        val (heal, _) = setup()
        val plan = heal.importFromProvider(read(
            run("ok"),
            Imported.Exercise(hc, "a", "backwards", ActivityType.WALK, now - hour, now - 2 * hour),
            run("future", now + 5 * hour),
            Imported.Body(hc, "a", "neg", MeasureType.WEIGHT, -3.0, "kg", now - hour),
            Imported.Drink(hc, "a", "huge", now - hour, 90_000),
        ))
        assertEquals(1, plan.candidates.size); assertEquals(4, plan.rejected)
        assertEquals(1, heal.data.activities.size)
    }

    @Test fun aFailedReadStillKeepsWhatCameButDoesNotMoveTheLastSyncTime() {
        val (heal, _) = setup()
        heal.importFromProvider(read(run("r1"), failed = true))
        assertEquals(1, heal.data.activities.size); assertEquals(0L, heal.data.settings.healthLastSyncMillis)
    }

    @Test fun removingImportedDataBySourceLeavesEverythingElse() {
        val (heal, _) = setup()
        val mine = assertNotNull(heal.logActivity(ActivityType.WALK, 20, endedAtMillis = now - 9 * hour).record)
        heal.importFromProvider(read(run("h1"), Imported.Exercise(hc, "com.other", "o1", ActivityType.CYCLE, now - 30 * hour, now - 29 * hour), Imported.Drink(hc, "com.huawei.health", "d1", now - hour, 200)))
        assertEquals(listOf("com.huawei.health" to 2, "com.other" to 1), heal.importedSources().map { it.app to it.total }.sortedByDescending { it.second })
        assertEquals(2, heal.removeImported(app = "com.huawei.health"))
        assertEquals(setOf(mine.id), heal.data.activities.filter { it.provenance == null }.map { it.id }.toSet())
        assertEquals(1, heal.data.activities.count { it.provenance?.app == "com.other" })
        assertEquals(1, heal.removeImported())
        assertTrue(heal.data.activities.all { it.provenance == null })
    }

    @Test fun editingAnImportedRecordKeepsItsSourceAndProvenance() {
        val (heal, _) = setup()
        heal.importFromProvider(read(run("r1")))
        val a = heal.data.activities.single()
        val edited = assertNotNull(heal.updateActivity(a.copy(note = "felt good", source = SourceKind.MANUAL, provenance = null)).record)
        assertEquals(SourceKind.IMPORTED, edited.source); assertNotNull(edited.provenance)
    }

    @Test fun disconnectingKeepsTheImportedRecordsAndClearsTheChoice() {
        val (heal, _) = setup()
        heal.setHealthKinds(setOf(HealthKind.EXERCISE, HealthKind.SLEEP))
        heal.importFromProvider(read(run("r1")))
        heal.setHealthKinds(emptySet())
        assertEquals(1, heal.data.activities.size); assertEquals(emptySet(), heal.data.settings.healthKinds); assertEquals(0L, heal.data.settings.healthLastSyncMillis)
        assertNull(heal.data.activities.single().provenance?.possibleDuplicateOf)
    }
}
