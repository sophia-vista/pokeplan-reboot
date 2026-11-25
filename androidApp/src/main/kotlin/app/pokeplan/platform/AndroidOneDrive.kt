package app.pokeplan.platform

import android.content.Intent
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.activity.ComponentActivity
import app.pokeplan.BuildConfig
import app.pokeplan.ui.OneDrive
import app.pokeplan.ui.OneDriveItem
import app.pokeplan.ui.OneDriveSource
import app.pokeplan.ui.OneDriveState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection

/**
 * The Android half of LINK's optional OneDrive photo source (REP-083, INTEGRATIONS_AND_PRIVACY.md 8.2A).
 *
 * The sign-in is the standard OAuth 2.0 authorization code flow with PKCE: the phone's browser shows Microsoft's own
 * page, Microsoft redirects back to `pokeplan-onedrive://auth`, and the code is exchanged here. The tokens are sealed
 * with an Android Keystore key in the app's private storage, never in the save, an export, a backup or a log, and are
 * deleted on disconnect. Microsoft Graph is only ever read: this class never creates, moves or deletes a file.
 *
 * Builds without an app registration (an empty [BuildConfig.ONEDRIVE_CLIENT_ID]) are plainly [OneDriveState.NOT_SET_UP]
 * and ask for nothing.
 */
class AndroidOneDrive(private val activity: ComponentActivity) : OneDriveSource {
    private val clientId: String = BuildConfig.ONEDRIVE_CLIENT_ID.trim()
    private val json = Json { ignoreUnknownKeys = true }

    /** A sign-in that left for the browser and has not come back yet. */
    private class Pending(val verifier: String, val state: String, val done: (OneDriveState, String?) -> Unit)

    /** What the sealed token file holds: the tokens themselves and whether Microsoft says they are no longer good. */
    @Serializable
    private data class StoredTokens(
        val access: String = "",
        val refresh: String = "",
        val expiryMillis: Long = 0L,
        val stale: Boolean = false,
    )

    private class Snapshot(val tokens: StoredTokens?, val failed: Boolean)

    private sealed interface Access {
        class Ok(val token: String) : Access
        class Failure(val problem: String) : Access
    }

    private class HttpResult(val code: Int, val bytes: ByteArray?) {
        val text: String get() = bytes?.decodeToString().orEmpty()
    }

    private val lock = Any()
    private val keyLock = Any()
    private var key: SecretKey? = null

    /** Guarded by [lock]. */
    private var pending: Pending? = null
    private var loaded = false
    private var stored: StoredTokens? = null
    private var storageFailed = false

    override fun state(): OneDriveState {
        if (clientId.isEmpty()) return OneDriveState.NOT_SET_UP
        if (synchronized(lock) { pending } != null) return OneDriveState.CONNECTING
        val snapshot = snapshot()
        if (snapshot.failed) return OneDriveState.UNAVAILABLE
        val tokens = snapshot.tokens ?: return OneDriveState.DISCONNECTED
        // An expired access token is still a connection while a refresh token can renew it; only a sign-in Microsoft
        // has refused (or an access token with nothing left to renew it) needs the player to connect again.
        if (tokens.stale) return OneDriveState.NEEDS_SIGN_IN
        if (tokens.refresh.isEmpty() && tokens.expiryMillis <= System.currentTimeMillis()) return OneDriveState.NEEDS_SIGN_IN
        return OneDriveState.CONNECTED
    }

    override fun connect(done: (OneDriveState, String?) -> Unit) {
        if (clientId.isEmpty()) {
            onUi { done(OneDriveState.NOT_SET_UP, null) }
            return
        }
        // A sign-in that never came back (the player closed the browser) must not keep every later try from starting.
        val abandoned = synchronized(lock) { pending }
        if (abandoned != null) {
            synchronized(lock) { if (pending === abandoned) pending = null }
            onUi { abandoned.done(OneDriveState.NEEDS_SIGN_IN, SIGN_IN_FAILED) }
        }
        val verifier = randomToken(VERIFIER_BYTES)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val state = randomToken(STATE_BYTES)
        val url = OneDrive.authorizeUrl(clientId, challenge, state)
        synchronized(lock) { pending = Pending(verifier, state, done) }
        val opened = runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        if (opened.isFailure) {
            synchronized(lock) { if (pending?.state == state) pending = null }
            onUi { done(OneDriveState.DISCONNECTED, NO_BROWSER) }
        }
    }

    /**
     * The player is back in the app with [uri] (or another intent, which is ignored). The redirect is only taken for
     * this app's own scheme and only while a sign-in is pending, so an unrelated launch cannot finish or break it.
     */
    fun complete(uri: Uri?) {
        val raw = uri?.toString() ?: return
        if (!raw.startsWith(OneDrive.REDIRECT_URI)) return
        val flow = synchronized(lock) { pending } ?: return
        val error = uri.getQueryParameter("error")
        if (error != null) {
            // Microsoft sent the player back without a code (they pressed Cancel, or the account refused).
            if (uri.getQueryParameter("state").orEmpty() != flow.state) return
            if (!take(flow)) return
            val problem = if (error == "access_denied") CANCELLED else SIGN_IN_FAILED
            onUi { flow.done(OneDriveState.NEEDS_SIGN_IN, problem) }
            return
        }
        val (code, returnedState) = OneDrive.redirect(raw) ?: return
        if (returnedState != flow.state) return
        if (!take(flow)) return
        Thread {
            val (result, problem) = signIn(flow, code)
            onUi { flow.done(result, problem) }
        }.start()
    }

    override fun disconnect(done: (OneDriveState, String?) -> Unit) {
        Thread {
            val cleared = clearPersisted()
            onUi {
                if (cleared) done(state(), null) else done(OneDriveState.UNAVAILABLE, CLEAR_FAILED)
            }
        }.start()
    }

    override fun children(folderId: String?, done: (List<OneDriveItem>?, String?) -> Unit) {
        if (clientId.isEmpty()) {
            onUi { done(null, NOT_SET_UP) }
            return
        }
        Thread {
            val (bytes, problem) = fetch(OneDrive.childrenUrl(folderId), LIST_LIMIT, TOO_BIG_LISTING)
            if (bytes == null) {
                onUi { done(null, problem ?: UNREACHABLE) }
            } else {
                val items = OneDrive.parseChildren(bytes.decodeToString())
                onUi { if (items == null) done(null, UNEXPECTED) else done(items, null) }
            }
        }.start()
    }

    override fun thumbnail(itemId: String, done: (ByteArray?, String?) -> Unit) {
        if (clientId.isEmpty()) {
            onUi { done(null, NOT_SET_UP) }
            return
        }
        Thread {
            val (bytes, problem) = fetch(OneDrive.thumbnailUrl(itemId), THUMBNAIL_LIMIT, TOO_BIG_PREVIEW)
            onUi {
                if (bytes == null || bytes.isEmpty()) done(null, problem ?: UNEXPECTED) else done(bytes, null)
            }
        }.start()
    }

    override fun download(itemId: String, done: (ByteArray?, String?) -> Unit) {
        if (clientId.isEmpty()) {
            onUi { done(null, NOT_SET_UP) }
            return
        }
        Thread {
            val (bytes, problem) = fetch(OneDrive.downloadUrl(itemId), DOWNLOAD_LIMIT, TOO_BIG_PHOTO)
            onUi {
                if (bytes == null || bytes.isEmpty()) done(null, problem ?: UNEXPECTED) else done(bytes, null)
            }
        }.start()
    }

    private fun onUi(block: () -> Unit) {
        activity.runOnUiThread(block)
    }

    /** Takes [flow] out of the pending slot, but only if it is still the one the player is waiting on. */
    private fun take(flow: Pending): Boolean = synchronized(lock) {
        if (pending !== flow) return@synchronized false
        pending = null
        true
    }

    // ---- The token endpoint ----

    private fun signIn(flow: Pending, code: String): Pair<OneDriveState, String?> {
        val response = postForm("${OneDrive.AUTHORITY}/token", OneDrive.tokenBody(clientId, code, flow.verifier))
            ?: return failureState() to UNREACHABLE
        if (response.code !in 200..299) return failureState() to (OneDrive.problem(response.text) ?: SIGN_IN_FAILED)
        val token = OneDrive.parseToken(response.text) ?: return failureState() to SIGN_IN_FAILED
        val sealed = StoredTokens(
            access = token.access,
            refresh = token.refresh,
            expiryMillis = System.currentTimeMillis() + token.expiresInMillis,
        )
        return if (persist(sealed)) OneDriveState.CONNECTED to null else OneDriveState.UNAVAILABLE to SAVE_FAILED
    }

    /** The state to report when a sign-in attempt failed: a still-stored sign-in is stale, no stored one is not set up. */
    private fun failureState(): OneDriveState =
        if (snapshot().tokens == null) OneDriveState.DISCONNECTED else OneDriveState.NEEDS_SIGN_IN

    // ---- Graph reads ----

    /** One HTTPS GET of a Graph URL with a bearer token, renewing the access token once when Microsoft refuses it. */
    private fun fetch(url: String, limit: Int, tooBig: String): Pair<ByteArray?, String?> {
        val first = accessToken()
        if (first is Access.Failure) return null to first.problem
        val one = get(url, (first as Access.Ok).token, limit) ?: return null to UNREACHABLE
        if (one.code != 401) return answer(one, tooBig)
        val second = accessToken(force = true)
        if (second is Access.Failure) return null to second.problem
        val two = get(url, (second as Access.Ok).token, limit) ?: return null to UNREACHABLE
        if (two.code == 401) {
            // Microsoft refused a brand-new access token too: the sign-in itself is no longer good.
            markStale()
            return null to SIGN_IN_AGAIN
        }
        return answer(two, tooBig)
    }

    private fun answer(result: HttpResult, tooBig: String): Pair<ByteArray?, String?> = when {
        result.bytes == null -> null to tooBig
        result.code in 200..299 -> result.bytes to null
        else -> null to (OneDrive.problem(result.text) ?: UNREACHABLE)
    }

    /** A usable access token: the stored one while it is not near its end, else one refreshed (and saved) now. */
    private fun accessToken(force: Boolean = false): Access {
        val snapshot = snapshot()
        if (snapshot.failed) return Access.Failure(STORAGE_UNREADABLE)
        val tokens = snapshot.tokens ?: return Access.Failure(NOT_CONNECTED)
        if (tokens.stale) return Access.Failure(SIGN_IN_AGAIN)
        if (!force && tokens.access.isNotEmpty() && tokens.expiryMillis > System.currentTimeMillis() + FRESH_WINDOW_MILLIS) {
            return Access.Ok(tokens.access)
        }
        return refresh(tokens)
    }

    private fun refresh(tokens: StoredTokens): Access {
        if (tokens.refresh.isEmpty()) {
            markStale()
            return Access.Failure(SIGN_IN_AGAIN)
        }
        val response = postForm("${OneDrive.AUTHORITY}/token", OneDrive.refreshBody(clientId, tokens.refresh))
            ?: return Access.Failure(UNREACHABLE)
        if (response.code !in 200..299) {
            // Only Microsoft saying the refresh token itself is no good makes the sign-in stale; a server answer we
            // cannot act on is reported and tried again next time.
            return if (response.code in 400..499) {
                markStale()
                Access.Failure(SIGN_IN_AGAIN)
            } else {
                Access.Failure(UNREACHABLE)
            }
        }
        val renewed = OneDrive.parseToken(response.text)
        if (renewed == null) {
            markStale()
            return Access.Failure(SIGN_IN_AGAIN)
        }
        persist(
            StoredTokens(
                access = renewed.access,
                // Microsoft rotates the refresh token; keep the old one if this answer did not carry a new one.
                refresh = renewed.refresh.ifEmpty { tokens.refresh },
                expiryMillis = System.currentTimeMillis() + renewed.expiresInMillis,
            ),
        )
        return Access.Ok(renewed.access)
    }

    /** Keeps the tokens but records that Microsoft has refused them, so [state] asks for a reconnect. */
    private fun markStale() {
        val current = snapshot().tokens ?: return
        if (current.stale) return
        persist(current.copy(access = "", expiryMillis = 0L, stale = true))
    }

    // ---- HTTP (HTTPS only) ----

    private fun get(url: String, token: String, limit: Int): HttpResult? {
        if (!url.startsWith("https://")) return null
        var connection: HttpsURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json, image/jpeg, image/png")
            }
            val code = connection.responseCode
            val bytes = if (code in 200..299) {
                connection.inputStream.use { readUpTo(it, limit) }
            } else {
                connection.errorStream?.use { readUpTo(it, ERROR_LIMIT) } ?: ByteArray(0)
            }
            HttpResult(code, bytes)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun postForm(url: String, body: String): HttpResult? {
        if (!url.startsWith("https://")) return null
        var connection: HttpsURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Accept", "application/json")
            }
            connection.outputStream.use { it.write(body.encodeToByteArray()) }
            val code = connection.responseCode
            val bytes = if (code in 200..299) {
                connection.inputStream.use { readUpTo(it, ERROR_LIMIT) } ?: ByteArray(0)
            } else {
                connection.errorStream?.use { readUpTo(it, ERROR_LIMIT) } ?: ByteArray(0)
            }
            HttpResult(code, bytes)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** Reads a body, or null when it is larger than [limit] (a picture or listing too big to take). */
    private fun readUpTo(input: InputStream, limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return out.toByteArray()
            if (out.size() + read > limit) return null
            out.write(buffer, 0, read)
        }
    }

    // ---- The sealed token file ----

    private fun tokensFile(): File = File(activity.filesDir, FILE_NAME)

    private fun snapshot(): Snapshot = synchronized(lock) {
        if (!loaded) loadLocked()
        Snapshot(stored, storageFailed)
    }

    private fun loadLocked() {
        loaded = true
        storageFailed = false
        stored = null
        val file = tokensFile()
        if (!file.isFile) return
        try {
            val raw = file.readBytes()
            if (raw.size <= IV_BYTES) {
                storageFailed = true
                return
            }
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES))
            val plain = cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES).decodeToString()
            stored = json.decodeFromString(StoredTokens.serializer(), plain)
        } catch (_: Exception) {
            // No key, a changed key or a damaged file: nothing can be read, and guessing would be worse than saying so.
            storageFailed = true
        }
    }

    private fun persist(tokens: StoredTokens): Boolean {
        val written = writeSealed(tokens)
        if (written) {
            synchronized(lock) {
                loaded = true
                storageFailed = false
                stored = tokens
            }
        }
        return written
    }

    private fun writeSealed(tokens: StoredTokens): Boolean {
        val file = tokensFile()
        return try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val body = cipher.doFinal(json.encodeToString(StoredTokens.serializer(), tokens).encodeToByteArray())
            val raw = cipher.iv + body
            val temp = File(activity.filesDir, "$FILE_NAME.tmp")
            temp.writeBytes(raw)
            if (!temp.renameTo(file)) {
                temp.delete()
                file.writeBytes(raw)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun clearPersisted(): Boolean {
        val file = tokensFile()
        return try {
            val gone = !file.exists() || file.delete()
            synchronized(lock) {
                loaded = true
                storageFailed = false
                stored = null
            }
            gone
        } catch (_: Exception) {
            false
        }
    }

    private fun key(): SecretKey = synchronized(keyLock) {
        key ?: loadOrCreateKey().also { key = it }
    }

    private fun loadOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    // ---- Randomness for PKCE ----

    private fun randomToken(bytes: Int): String {
        val random = ByteArray(bytes)
        SecureRandom().nextBytes(random)
        return base64Url(random)
    }

    private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private companion object {
        const val FILE_NAME = "onedrive-tokens.bin"
        const val ALIAS = "pokeplan-onedrive"
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val IV_BYTES = 12
        const val TIMEOUT_MILLIS = 30_000
        const val FRESH_WINDOW_MILLIS = 60_000L
        const val VERIFIER_BYTES = 64
        const val STATE_BYTES = 32
        const val LIST_LIMIT = 4 * 1024 * 1024
        const val THUMBNAIL_LIMIT = 2 * 1024 * 1024
        const val DOWNLOAD_LIMIT = 20 * 1024 * 1024
        const val ERROR_LIMIT = 64 * 1024

        const val NOT_SET_UP = "OneDrive is not set up in this build."
        const val NOT_CONNECTED = "OneDrive is not connected."
        const val SIGN_IN_AGAIN = "Microsoft sign-in has expired. Reconnect OneDrive."
        const val SIGN_IN_FAILED = "Microsoft sign-in did not finish."
        const val CANCELLED = "Microsoft sign-in was cancelled."
        const val NO_BROWSER = "This phone has no browser to open the Microsoft sign-in page."
        const val UNREACHABLE = "OneDrive could not be reached."
        const val UNEXPECTED = "OneDrive sent an unexpected answer."
        const val STORAGE_UNREADABLE = "OneDrive could not be read on this phone."
        const val SAVE_FAILED = "OneDrive could not be saved on this phone."
        const val CLEAR_FAILED = "OneDrive could not be cleared on this phone."
        const val TOO_BIG_LISTING = "That folder is too big to show."
        const val TOO_BIG_PREVIEW = "That preview is too big."
        const val TOO_BIG_PHOTO = "That picture is too big to add."
    }
}
