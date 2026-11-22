package app.pokeplan.health

import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import app.pokeplan.app.ProviderAvailability
import app.pokeplan.heal.ActivityType
import app.pokeplan.heal.HealImport
import app.pokeplan.heal.HealthKind
import app.pokeplan.heal.Imported
import app.pokeplan.heal.MeasureType
import app.pokeplan.heal.ProviderRead
import app.pokeplan.ui.HealthSource
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.roundToInt
import kotlin.reflect.KClass

class AndroidHealthConnect(private val activity: ComponentActivity) : HealthSource {
    private var waiting: ((Set<HealthKind>) -> Unit)? = null
    private var asked: Set<HealthKind> = emptySet()

    private val launcher = activity.registerForActivityResult(PermissionController.createRequestPermissionResultContract() as ActivityResultContract<Set<String>, Set<String>>) { granted ->
        val done = waiting; waiting = null
        done?.invoke(kindsOf(granted).intersect(asked))
    }

    private val classes: Map<HealthKind, KClass<out Record>> = mapOf(
        HealthKind.EXERCISE to ExerciseSessionRecord::class, HealthKind.SLEEP to SleepSessionRecord::class, HealthKind.WEIGHT to WeightRecord::class,
        HealthKind.BODY_FAT to BodyFatRecord::class, HealthKind.HYDRATION to HydrationRecord::class,
    )

    private fun permission(kind: HealthKind): String = HealthPermission.getReadPermission(classes.getValue(kind))

    private fun kindsOf(permissions: Set<String>): Set<HealthKind> = HealthKind.entries.filter { permission(it) in permissions }.toSet()

    private fun client(): HealthConnectClient? = if (availability() == ProviderAvailability.AVAILABLE) runCatching { HealthConnectClient.getOrCreate(activity) }.getOrNull() else null

    override fun availability(): ProviderAvailability = when (HealthConnectClient.getSdkStatus(activity)) {
        HealthConnectClient.SDK_AVAILABLE -> ProviderAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> ProviderAvailability.UPDATE_REQUIRED
        else -> ProviderAvailability.UNSUPPORTED
    }

    override fun granted(done: (Set<HealthKind>) -> Unit) {
        val c = client() ?: return done(emptySet())
        activity.lifecycleScope.launch {
            val g = runCatching { kindsOf(c.permissionController.getGrantedPermissions()) }.onFailure { warn("granted", it) }.getOrDefault(emptySet())
            done(g)
        }
    }

    override fun request(kinds: Set<HealthKind>, done: (Set<HealthKind>) -> Unit) {
        if (client() == null || kinds.isEmpty() || waiting != null) return done(emptySet())
        asked = kinds
        waiting = done
        runCatching { launcher.launch(kinds.map(::permission).toSet()) }.onFailure { waiting = null; warn("request", it); done(emptySet()) }
    }

    override fun read(kinds: Set<HealthKind>, days: Int, done: (ProviderRead?, String?) -> Unit) {
        val c = client() ?: return done(null, "Health Connect is not available on this phone right now.")
        val to = Instant.now()
        val from = to.minusSeconds(days.coerceIn(1, HealImport.MAX_DAYS_BACK) * 86_400L)
        activity.lifecycleScope.launch {
            val allowed = runCatching { kindsOf(c.permissionController.getGrantedPermissions()) }.getOrDefault(emptySet())
            val use = kinds.intersect(allowed)
            if (use.isEmpty()) return@launch done(null, "Android has not allowed Heal to read any of these.")
            val out = ArrayList<Imported>()
            var failed = false
            for (kind in use) {
                try {
                    var token: String? = null
                    var pages = 0
                    do {
                        val response = c.readRecords(ReadRecordsRequest(classes.getValue(kind), TimeRangeFilter.between(from, to), pageSize = PAGE, pageToken = token))
                        response.records.forEach { r -> convert(r)?.let { out += it } }
                        token = response.pageToken
                    } while (token != null && ++pages < MAX_PAGES && out.size < MAX_RECORDS)
                    if (token != null) failed = true
                } catch (e: Exception) {
                    warn("read", e); failed = true
                }
            }
            if (failed && out.isEmpty()) done(null, "Health Connect could not be read. Nothing was changed.")
            else done(ProviderRead(out, from.toEpochMilli(), to.toEpochMilli(), failed), null)
        }
    }

    override fun revoke(done: (Boolean) -> Unit) {
        val c = client() ?: return done(false)
        activity.lifecycleScope.launch { done(runCatching { c.permissionController.revokeAllPermissions() }.onFailure { warn("revoke", it) }.isSuccess) }
    }

    override fun openSettings() {
        val intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { activity.startActivity(intent) }.onFailure { warn("settings", it) }
    }

    private fun convert(r: Record): Imported? {
        val id = r.metadata.id
        val app = r.metadata.dataOrigin.packageName
        val p = HealImport.PROVIDER_HEALTH_CONNECT
        return when (r) {
            is ExerciseSessionRecord -> Imported.Exercise(p, app, id, activityOf(r.exerciseType), r.startTime.toEpochMilli(), r.endTime.toEpochMilli())
            is SleepSessionRecord -> Imported.Sleep(p, app, id, r.startTime.toEpochMilli(), r.endTime.toEpochMilli())
            is WeightRecord -> Imported.Body(p, app, id, MeasureType.WEIGHT, r.weight.inKilograms, "kg", r.time.toEpochMilli())
            is BodyFatRecord -> Imported.Body(p, app, id, MeasureType.BODY_FAT, r.percentage.value, "%", r.time.toEpochMilli())
            is HydrationRecord -> Imported.Drink(p, app, id, r.startTime.toEpochMilli(), r.volume.inMilliliters.roundToInt())
            else -> null
        }
    }

    private fun activityOf(type: Int): ActivityType = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING, ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> ActivityType.WALK
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> ActivityType.RUN
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> ActivityType.CYCLE
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> ActivityType.SWIM
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> ActivityType.STRENGTH
        ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING, ExerciseSessionRecord.EXERCISE_TYPE_YOGA, ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> ActivityType.STRETCH
        else -> ActivityType.OTHER
    }

    private fun warn(step: String, e: Throwable) { Log.w("HealthConnect", "$step failed: ${e.javaClass.simpleName}") }

    private companion object {
        const val PAGE = 500
        const val MAX_PAGES = 10
        const val MAX_RECORDS = 5_000
    }
}
