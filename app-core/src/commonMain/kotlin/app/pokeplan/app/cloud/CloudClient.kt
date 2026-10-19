package app.pokeplan.app.cloud

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
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
            buildJsonObject { put("kind", "push"); put("expectedVersion", expectedVersion); put("data", document) },
            session.accessToken,
        ) { it["version"]!!.jsonPrimitive.int }

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

    private suspend fun <T> call(path: String, body: JsonObject, bearer: String?, parse: (JsonObject) -> T): CloudResult<T> {
        val response = try {
            http.post(config.base + path) {
                header("apikey", config.anonKey)
                header("Authorization", "Bearer ${bearer ?: config.anonKey}")
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        } catch (e: Exception) {
            return CloudResult.Failure("No connection.")
        }
        val text = response.bodyAsText()
        val parsed: JsonObject? = try {
            (json.parseToJsonElement(text) as? JsonElement)?.takeIf { it !is JsonNull }?.jsonObject
        } catch (e: Exception) {
            null
        }
        if (!response.status.isSuccess() || parsed == null) {
            val message = listOf("error_description", "msg", "message", "error")
                .firstNotNullOfOrNull { parsed?.get(it)?.jsonPrimitive?.contentOrNull } ?: "Something went wrong (${response.status.value})."
            return CloudResult.Failure(message, response.status.value, parsed)
        }
        return try {
            CloudResult.Ok(parse(parsed))
        } catch (e: Exception) {
            CloudResult.Failure("The server answered in a way this app did not expect.", response.status.value, parsed)
        }
    }
}
