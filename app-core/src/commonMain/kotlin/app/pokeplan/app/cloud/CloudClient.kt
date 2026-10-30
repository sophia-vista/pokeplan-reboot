package app.pokeplan.app.cloud

import app.pokeplan.app.EggTicket
import app.pokeplan.app.SpentTicket
import app.pokeplan.core.reward.RewardRules
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException

/** Where the Supabase project is. The URL and the anon key are meant to ship in every client; they are not secrets. */
data class CloudConfig(val url: String, val anonKey: String) {
    val enabled: Boolean get() = url.isNotBlank() && anonKey.isNotBlank()
    internal val base: String get() = url.trimEnd('/')
}

class Session(val accessToken: String, val refreshToken: String, val expiresAtMillis: Long, val userId: String, val email: String)

/** What a call to the cloud came back with. A refused call has a [Failure.status] (0 when there was no answer at all). */
sealed interface CloudResult<out T> {
    data class Ok<T>(val value: T) : CloudResult<T>
    data class Failure(val message: String, val status: Int = 0, val body: JsonObject? = null) : CloudResult<Nothing> {
        val offline: Boolean get() = status == 0
    }
}

/** What signing up led to: a session, or a request to confirm the email first. */
sealed interface SignUp {
    data class SignedIn(val session: Session) : SignUp
    data object ConfirmEmail : SignUp
}

/** The cloud save as the server returned it: [data] is null when nothing has been uploaded yet. */
class Pulled(val version: Int, val data: JsonObject?)

/** Talks to Supabase: sign-in over its Auth API and the cloud save through the `reward` Edge Function. */
class CloudClient(private val config: CloudConfig, private val http: HttpClient, private val nowMillis: () -> Long) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun signUp(email: String, password: String): CloudResult<SignUp> =
        call("/auth/v1/signup", credentials(email, password), bearer = null) { body ->
            if (body["access_token"] != null) SignUp.SignedIn(session(body)) else SignUp.ConfirmEmail
        }

    suspend fun signIn(email: String, password: String): CloudResult<Session> =
        call("/auth/v1/token?grant_type=password", credentials(email, password), bearer = null) { session(it) }

    suspend fun refresh(refreshToken: String): CloudResult<Session> =
        call("/auth/v1/token?grant_type=refresh_token", buildJsonObject { put("refresh_token", refreshToken) }, bearer = null) { session(it) }

    suspend fun pull(session: Session): CloudResult<Pulled> =
        call("/functions/v1/reward", buildJsonObject { put("kind", "pull") }, session.accessToken) { body ->
            Pulled(body["version"]!!.jsonPrimitive.int, (body["data"] as? JsonObject))
        }

    /** Saves [document] if the cloud is still at [expectedVersion]. A stale version is a failure with status 409 and the server's `version` in the body. */
    suspend fun push(session: Session, expectedVersion: Int, document: JsonObject): CloudResult<Int> =
        call(
            "/functions/v1/reward",
            buildJsonObject {
                put("kind", "push"); put("expectedVersion", expectedVersion); put("data", document)
                // The rules this app's engine ran, recorded with the snapshot on the server's ledger.
                put("rulesVersion", RewardRules.VERSION)
            },
            session.accessToken,
        ) { it["version"]!!.jsonPrimitive.int }

    /** Care's opt-in copy, apart from the Plan save: [Pulled.data] is null when nothing has been uploaded. */
    suspend fun carePull(session: Session): CloudResult<Pulled> =
        call("/functions/v1/reward", buildJsonObject { put("kind", "care-pull") }, session.accessToken) { body ->
            Pulled(body["version"]!!.jsonPrimitive.int, (body["data"] as? JsonObject))
        }

    /** Saves Care's [document] if the cloud copy is still at [expectedVersion]; a stale one is a 409 with the server's `version`. */
    suspend fun carePush(session: Session, expectedVersion: Int, document: JsonObject): CloudResult<Int> =
        call(
            "/functions/v1/reward",
            buildJsonObject { put("kind", "care-push"); put("expectedVersion", expectedVersion); put("data", document) },
            session.accessToken,
        ) { it["version"]!!.jsonPrimitive.int }

    /** Deletes Care's cloud copy (turning sync off). */
    suspend fun careDelete(session: Session): CloudResult<Unit> =
        call("/functions/v1/reward", buildJsonObject { put("kind", "care-delete") }, session.accessToken) { }

    /** Reports the egg tickets this device spent and gets its unspent stock back, topped up. */
    suspend fun tickets(session: Session, device: String, spent: List<SpentTicket>): CloudResult<List<EggTicket>> =
        call(
            "/functions/v1/reward",
            buildJsonObject {
                put("kind", "tickets"); put("device", device)
                put("spent", json.encodeToJsonElement(ListSerializer(SpentTicket.serializer()), spent))
            },
            session.accessToken,
        ) { json.decodeFromJsonElement(ListSerializer(EggTicket.serializer()), it["tickets"]!!) }

    /** Deletes the account and everything stored for it on the server. */
    suspend fun deleteAccount(session: Session): CloudResult<Unit> =
        call("/functions/v1/reward", buildJsonObject { put("kind", "delete-account"); put("confirm", "DELETE") }, session.accessToken) { }

    private fun credentials(email: String, password: String) = buildJsonObject { put("email", email.trim()); put("password", password) }

    private fun session(body: JsonObject): Session {
        val user = body["user"]?.jsonObject
        return Session(
            accessToken = body["access_token"]!!.jsonPrimitive.content,
            refreshToken = body["refresh_token"]!!.jsonPrimitive.content,
            expiresAtMillis = nowMillis() + (body["expires_in"]?.jsonPrimitive?.intOrNull ?: 3600) * 1000L,
            userId = user?.get("id")?.jsonPrimitive?.contentOrNull ?: "",
            email = user?.get("email")?.jsonPrimitive?.contentOrNull ?: "",
        )
    }

    /**
     * The social functions of the `platform` schema, called over PostgREST as the signed-in player. [function] is the SQL
     * function name; the result is whatever JSON it returns (an array for functions that return rows).
     */
    suspend fun rpc(session: Session, function: String, args: JsonObject = JsonObject(emptyMap())): CloudResult<JsonElement> =
        send("/rest/v1/rpc/$function", args.toString(), session.accessToken, post = true, profile = "platform") { it }

    /** Rows of a table in the `platform` schema (RLS decides which are the player's), e.g. `gifts?opened_at=is.null`. */
    suspend fun rows(session: Session, table: String, query: String): CloudResult<JsonElement> =
        send("/rest/v1/$table?$query", null, session.accessToken, post = false, profile = "platform") { it }

    private suspend fun <T> call(path: String, body: JsonObject, bearer: String?, parse: (JsonObject) -> T): CloudResult<T> =
        send(path, body.toString(), bearer, post = true, profile = null) { element ->
            parse(element as? JsonObject ?: throw IllegalStateException("not an object"))
        }

    private suspend fun <T> send(path: String, body: String?, bearer: String?, post: Boolean, profile: String?, parse: (JsonElement) -> T): CloudResult<T> {
        val (response, text) = try {
            val response = http.request(config.base + path) {
                method = if (post) HttpMethod.Post else HttpMethod.Get
                header("apikey", config.anonKey)
                header("Authorization", "Bearer ${bearer ?: config.anonKey}")
                if (profile != null) {
                    header("Accept-Profile", profile)
                    if (post) header("Content-Profile", profile)
                }
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
            response to response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return CloudResult.Failure("No connection.")
        }
        val element: JsonElement? = try {
            if (text.isBlank()) JsonNull else json.parseToJsonElement(text)
        } catch (e: Exception) {
            null
        }
        val obj = element as? JsonObject
        if (!response.status.isSuccess() || element == null) {
            val message = listOf("error_description", "msg", "message", "error")
                .firstNotNullOfOrNull { obj?.get(it)?.jsonPrimitive?.contentOrNull } ?: "Something went wrong (${response.status.value})."
            return CloudResult.Failure(message, response.status.value, obj)
        }
        return try {
            CloudResult.Ok(parse(element))
        } catch (e: Exception) {
            CloudResult.Failure("The server answered in a way this app did not expect.", response.status.value, obj)
        }
    }
}
