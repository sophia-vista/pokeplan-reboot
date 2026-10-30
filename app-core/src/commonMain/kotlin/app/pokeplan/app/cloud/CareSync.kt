package app.pokeplan.app.cloud

import app.pokeplan.app.CareStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonPrimitive

/**
 * Care's opt-in cloud copy (docs/ecosystem.md, section 6), apart from the Plan save: off by default, it uploads `care.json`
 * to `care.saves` through the reward function with its own version, the same way [SyncManager] keeps the Plan save. It needs
 * the account [SyncManager] signed in to ([session]); turning it off deletes the copy.
 */
class CareSync(private val care: CareStore, private val cloud: CloudClient, private val session: suspend () -> Session?) {
    private val state = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = state

    /** Turns the copy on and syncs. Returns an error message, or null. */
    suspend fun turnOn(): String? {
        if (session() == null) return "Sign in first, in Me, Settings, Account."
        care.setCloudSync(true)
        sync()
        return (state.value as? SyncStatus.Refused)?.message ?: (state.value as? SyncStatus.Offline)?.message
    }

    /**
     * Turns the copy off: deletes it from the cloud first, so nothing is left there. Returns an error message (the switch
     * stays on), or null.
     */
    suspend fun turnOff(): String? {
        val s = session()
        if (s != null) {
            val r = cloud.careDelete(s)
            if (r is CloudResult.Failure) return r.message
        }
        // Signed out, the copy is the account's to delete (deleting the account removes it too).
        care.setCloudSync(false)
        state.value = SyncStatus.Idle
        return null
    }

    /** Brings this device and the cloud copy together: pulls, then pushes or adopts as needed. Does nothing while off. */
    suspend fun sync() {
        if (!care.data.settings.cloudSync) { state.value = SyncStatus.Idle; return }
        val s = session() ?: run { state.value = SyncStatus.SignedOut; return }
        state.value = SyncStatus.Syncing
        val pulled = when (val r = cloud.carePull(s)) {
            is CloudResult.Ok -> r.value
            is CloudResult.Failure -> { state.value = SyncStatus.of(r); return }
        }
        // The state this device last saw, if it was for this account; another account's starts afresh.
        val seen = care.data.cloud?.takeIf { it.userId == s.userId }
        val serverChanged = pulled.version != (seen?.version ?: 0)
        val localChanged = seen?.unsynced ?: care.hasEntries
        when {
            pulled.data == null -> push(s, pulled.version)
            !serverChanged && !localChanged -> { if (seen == null || seen.unsynced) care.markSynced(s.userId, pulled.version); state.value = SyncStatus.Idle }
            !serverChanged -> push(s, pulled.version)
            !localChanged -> state.value = if (care.adoptCloud(pulled.data, s.userId, pulled.version)) SyncStatus.Idle else SyncStatus.Unreadable
            else -> state.value = SyncStatus.Conflict(pulled.version)
        }
    }

    /** After a [SyncStatus.Conflict]: keep this device's Care (uploading it) or take the cloud's. */
    suspend fun resolve(keepThisDevice: Boolean) {
        val conflict = state.value as? SyncStatus.Conflict ?: return
        val s = session() ?: return
        state.value = SyncStatus.Syncing
        if (keepThisDevice) {
            push(s, conflict.serverVersion)
            return
        }
        when (val r = cloud.carePull(s)) {
            is CloudResult.Ok -> state.value = if (r.value.data?.let { care.adoptCloud(it, s.userId, r.value.version) } != false) SyncStatus.Idle else SyncStatus.Unreadable
            is CloudResult.Failure -> state.value = SyncStatus.of(r)
        }
    }

    private suspend fun push(s: Session, expectedVersion: Int) {
        val sent = care.data
        when (val r = cloud.carePush(s, expectedVersion, care.cloudDocument())) {
            is CloudResult.Ok -> { care.markSynced(s.userId, r.value, sent); state.value = SyncStatus.Idle }
            is CloudResult.Failure -> state.value = when (r.status) {
                409 -> SyncStatus.Conflict((r.body?.get("version") as? JsonPrimitive)?.content?.toIntOrNull() ?: expectedVersion)
                else -> SyncStatus.of(r)
            }
        }
    }

}
