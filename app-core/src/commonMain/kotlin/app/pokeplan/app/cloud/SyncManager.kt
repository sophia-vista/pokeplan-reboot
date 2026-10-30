package app.pokeplan.app.cloud

import app.pokeplan.app.GameStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

/** Where the sync stands, for the Settings screen. */
sealed interface SyncStatus {
    data object SignedOut : SyncStatus
    data object Idle : SyncStatus
    data object Syncing : SyncStatus

    /** The last attempt could not reach the cloud; it is tried again later. */
    data class Offline(val message: String) : SyncStatus

    /** The cloud and this device both changed since they last met: the player chooses which to keep. */
    data class Conflict(val serverVersion: Int) : SyncStatus

    /** The cloud refused the save (the message says why). */
    data class Refused(val message: String) : SyncStatus

    companion object {
        /** What a failed call means for the sync: try again later when offline, else the cloud said no. */
        fun of(failure: CloudResult.Failure): SyncStatus = if (failure.offline) Offline(failure.message) else Refused(failure.message)

        /** A cloud save this app cannot read (made by a newer version, or damaged): nothing on this device changes. */
        val Unreadable: SyncStatus = Refused("The cloud save could not be read, so this device kept its own. Update the app and try again.")
    }
}

/**
 * Keeps this device and the cloud save together. The cloud save is one document with a version: this device remembers the
 * version it last saw, so it can tell whether the cloud moved on while it was away. See "Cloud save" in supabase/README.md.
 */
class SyncManager(private val store: GameStore, private val cloud: CloudClient) {
    private val state = MutableStateFlow<SyncStatus>(if (store.data.account == null) SyncStatus.SignedOut else SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = state

    /** Creates an account. Returns an error message, "confirm" if the email has to be confirmed first, or null when signed in. */
    suspend fun signUp(email: String, password: String): String? = when (val r = cloud.signUp(email, password)) {
        is CloudResult.Ok -> when (val s = r.value) {
            is SignUp.SignedIn -> { adopt(s.session); sync(); null }
            SignUp.ConfirmEmail -> "confirm"
        }
        is CloudResult.Failure -> r.message
    }

    suspend fun signIn(email: String, password: String): String? = when (val r = cloud.signIn(email, password)) {
        is CloudResult.Ok -> { adopt(r.value); sync(); null }
        is CloudResult.Failure -> r.message
    }

    fun signOut() {
        store.signOut()
        state.value = SyncStatus.SignedOut
    }

    /**
     * Deletes the account and all its data on the server, then signs out; this device keeps its progress as a guest.
     * Returns an error message, or null when it is done.
     */
    suspend fun deleteAccount(): String? {
        val session = freshSession() ?: return "Sign in again first."
        return when (val r = cloud.deleteAccount(session)) {
            is CloudResult.Ok -> { signOut(); null }
            is CloudResult.Failure -> r.message
        }
    }

    private fun adopt(s: Session) {
        store.signedIn(s.email, s.userId, s.accessToken, s.refreshToken, s.expiresAtMillis)
        state.value = SyncStatus.Idle
    }

    /** Brings this device and the cloud together: pulls, then pushes or adopts as needed. Safe to call whenever. */
    suspend fun sync() {
        val account = store.data.account ?: run { state.value = SyncStatus.SignedOut; return }
        state.value = SyncStatus.Syncing
        val session = freshSession() ?: return

        val pulled = when (val r = cloud.pull(session)) {
            is CloudResult.Ok -> r.value
            is CloudResult.Failure -> { state.value = SyncStatus.of(r); return }
        }
        val serverChanged = pulled.version != account.version
        // A device that was never played on has nothing to lose.
        val localChanged = account.unsynced && store.data.onboarded
        when {
            pulled.data == null -> push(session, pulled.version)
            !serverChanged && !localChanged -> { if (account.unsynced) store.markSynced(pulled.version); state.value = SyncStatus.Idle }
            !serverChanged -> push(session, pulled.version)
            !localChanged -> adopt(pulled.data, pulled.version)
            else -> state.value = SyncStatus.Conflict(pulled.version)
        }
        refillTickets(session)
    }

    /** Reports the spent egg tickets and tops the stock up. A failure waits for the next sync: no egg ever waits on it. */
    private suspend fun refillTickets(session: Session) {
        val (device, spent) = store.ticketReport()
        val r = cloud.tickets(session, device, spent)
        if (r is CloudResult.Ok) store.receivedTickets(r.value, spent)
    }

    /** After a [SyncStatus.Conflict]: keep this device's progress (uploading it) or take the cloud's. */
    suspend fun resolve(keepThisDevice: Boolean) {
        val conflict = state.value as? SyncStatus.Conflict ?: return
        val session = freshSession() ?: return
        if (keepThisDevice) {
            state.value = SyncStatus.Syncing
            push(session, conflict.serverVersion)
            return
        }
        state.value = SyncStatus.Syncing
        when (val r = cloud.pull(session)) {
            is CloudResult.Ok -> r.value.data?.let { adopt(it, r.value.version) } ?: run { state.value = SyncStatus.Idle }
            is CloudResult.Failure -> state.value = SyncStatus.of(r)
        }
    }

    private fun adopt(document: JsonObject, version: Int) {
        state.value = if (store.adoptCloud(document, version)) SyncStatus.Idle else SyncStatus.Unreadable
    }

    private suspend fun push(session: Session, expectedVersion: Int) {
        val document = store.cloudDocument()
        when (val r = cloud.push(session, expectedVersion, document)) {
            is CloudResult.Ok -> { store.markSynced(r.value); state.value = SyncStatus.Idle }
            is CloudResult.Failure -> state.value = when (r.status) {
                409 -> SyncStatus.Conflict((r.body?.get("version") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: expectedVersion)
                else -> SyncStatus.of(r)
            }
        }
    }

    /** The signed-in session, refreshed if it is about to run out, for calls other than the save (friends, gifts). Null if signed out or refused. */
    suspend fun session(): Session? = freshSession()

    /** The stored session, refreshed if it is about to run out. Null (with the status set) if it cannot be. */
    private suspend fun freshSession(): Session? {
        val a = store.data.account ?: return null
        val session = Session(a.accessToken, a.refreshToken, a.expiresAtMillis, a.userId, a.email)
        if (a.expiresAtMillis - store.nowMillis > REFRESH_MARGIN_MILLIS) return session
        return when (val r = cloud.refresh(a.refreshToken)) {
            is CloudResult.Ok -> {
                store.refreshedSession(r.value.accessToken, r.value.refreshToken, r.value.expiresAtMillis)
                Session(r.value.accessToken, r.value.refreshToken, r.value.expiresAtMillis, a.userId, a.email)
            }
            is CloudResult.Failure -> {
                // A refused refresh token means signing in again; no answer just means offline.
                state.value = if (r.offline) SyncStatus.Offline(r.message) else SyncStatus.Refused("Please sign in again. ${r.message}")
                null
            }
        }
    }

    private companion object {
        const val REFRESH_MARGIN_MILLIS = 60_000L
    }
}
