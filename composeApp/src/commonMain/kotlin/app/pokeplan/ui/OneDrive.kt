package app.pokeplan.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonObject

/** What LINK knows about the optional Microsoft OneDrive photo source (LINK.md 6.2). Nothing is read before [CONNECTED]. */
enum class OneDriveState { NOT_SET_UP, DISCONNECTED, CONNECTING, CONNECTED, NEEDS_SIGN_IN, UNAVAILABLE }

/** One folder or photo in the player's OneDrive, as the Graph listing gives it. */
data class OneDriveItem(
    val id: String,
    val name: String,
    val folder: Boolean = false,
    val childCount: Int = 0,
    val sizeBytes: Long = 0,
    val mime: String = "",
)

data class OneDriveToken(val access: String, val refresh: String, val expiresInMillis: Long = 0)

/**
 * The optional read-only Microsoft OneDrive photo source. Android has a real implementation; desktop and web pass null.
 * Every call answers on the UI thread with a short problem when it fails; nothing is cached by the interface itself.
 */
interface OneDriveSource {
    fun state(): OneDriveState

    /** Starts Microsoft sign-in; [done] runs when the player returns to the app, connected or with a problem. */
    fun connect(done: (OneDriveState, String?) -> Unit)

    fun disconnect(done: (OneDriveState, String?) -> Unit)

    /** Lists the root (null) or one folder's children, folders first then photos by name. */
    fun children(folderId: String?, done: (List<OneDriveItem>?, String?) -> Unit)

    /** A small cached preview of one photo (a JPEG or PNG), or null with a problem. */
    fun thumbnail(itemId: String, done: (ByteArray?, String?) -> Unit)

    /** The whole picture, for a photo the player adds to a memory; read-only. */
    fun download(itemId: String, done: (ByteArray?, String?) -> Unit)
}

/** The small, testable half of the OneDrive connection: the URLs and JSON, with no Android and no network in it. */
object OneDrive {
    const val REDIRECT_SCHEME = "pokeplan-onedrive"
    const val REDIRECT_URI = "$REDIRECT_SCHEME://auth"
    const val AUTHORITY = "https://login.microsoftonline.com/common/oauth2/v2.0"
    const val GRAPH = "https://graph.microsoft.com/v1.0/me/drive"
    const val SCOPES = "Files.Read offline_access"
    private val json = Json { ignoreUnknownKeys = true }

    /** The Microsoft sign-in page for the authorization-code + PKCE flow. */
    fun authorizeUrl(clientId: String, codeChallenge: String, state: String): String =
        "$AUTHORITY/authorize?" +
            "client_id=${encode(clientId)}" +
            "&response_type=code" +
            "&redirect_uri=${encode(REDIRECT_URI)}" +
            "&response_mode=query" +
            "&scope=${encode(SCOPES)}" +
            "&code_challenge=${encode(codeChallenge)}" +
            "&code_challenge_method=S256" +
            "&state=${encode(state)}"

    fun tokenBody(clientId: String, code: String, codeVerifier: String): String =
        "client_id=${encode(clientId)}" +
            "&grant_type=authorization_code" +
            "&code=${encode(code)}" +
            "&redirect_uri=${encode(REDIRECT_URI)}" +
            "&code_verifier=${encode(codeVerifier)}" +
            "&scope=${encode(SCOPES)}"

    fun refreshBody(clientId: String, refreshToken: String): String =
        "client_id=${encode(clientId)}" +
            "&grant_type=refresh_token" +
            "&refresh_token=${encode(refreshToken)}" +
            "&scope=${encode(SCOPES)}"

    /** The code and state the browser came back with, or null when the redirect is not ours. */
    fun redirect(uri: String): Pair<String, String>? {
        if (!uri.startsWith("$REDIRECT_URI?")) return null
        val query = uri.substringAfter('?').substringBefore('#')
        val parts = query.split('&').mapNotNull { part ->
            val key = part.substringBefore('=', "").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            key to decode(part.substringAfter('=', ""))
        }.toMap()
        if ("error" in parts) return null
        val code = parts["code"] ?: return null
        return code to (parts["state"] ?: "")
    }

    fun parseToken(text: String): OneDriveToken? {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val access = root["access_token"].string() ?: return null
        val refresh = root["refresh_token"].string() ?: ""
        val expires = (root["expires_in"] as? JsonPrimitive)?.longOrNull ?: 0L
        return OneDriveToken(access, refresh, expires * 1000L)
    }

    /** The first sentence of a Graph or Microsoft error, or null. */
    fun problem(text: String): String? {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val error = root["error"] ?: return null
        val message = when (error) {
            is JsonObject -> error["message"].string() ?: (error["code"] as? JsonPrimitive)?.contentOrNull
            else -> (error as? JsonPrimitive)?.contentOrNull
        }
        return message?.take(160)
    }

    /** The children in a Graph listing (`value` array), folders first and then by name. */
    fun parseChildren(text: String): List<OneDriveItem>? {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val value = root["value"] as? JsonArray ?: return null
        return value.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val id = item["id"].string() ?: return@mapNotNull null
            val name = item["name"].string() ?: return@mapNotNull null
            val folder = item["folder"] as? JsonObject
            val file = item["file"] as? JsonObject
            OneDriveItem(
                id = id,
                name = name,
                folder = folder != null,
                childCount = (folder?.get("childCount") as? JsonPrimitive)?.intOrNull ?: 0,
                sizeBytes = (item["size"] as? JsonPrimitive)?.longOrNull ?: 0L,
                mime = (file?.get("mimeType") as? JsonPrimitive)?.contentOrNull.orEmpty(),
            )
        }.sortedWith(compareByDescending<OneDriveItem> { it.folder }.thenBy { it.name.lowercase() })
    }

    fun childrenUrl(folderId: String?): String {
        val base = if (folderId == null) "$GRAPH/root/children" else "$GRAPH/items/${encode(folderId)}/children"
        return "$base?%24select=id,name,size,folder,file,photo&%24top=200"
    }

    fun thumbnailUrl(itemId: String): String = "$GRAPH/items/${encode(itemId)}/thumbnails/0/medium/content"

    fun downloadUrl(itemId: String): String = "$GRAPH/items/${encode(itemId)}/content"

    private fun JsonElement?.string(): String? = (this as? JsonPrimitive)?.contentOrNull

    private fun encode(value: String): String =
        value.replace("%", "%25").replace(" ", "%20").replace("&", "%26").replace("+", "%2B").replace("=", "%3D")
            .replace(":", "%3A").replace("?", "%3F").replace("#", "%23").replace("/", "%2F")

    private fun decode(value: String): String =
        value.replace("+", " ").replace("%2B", "+").replace("%20", " ").replace("%3D", "=").replace("%26", "&")
            .replace("%3A", ":").replace("%3F", "?").replace("%23", "#").replace("%2F", "/").replace("%25", "%")
}
