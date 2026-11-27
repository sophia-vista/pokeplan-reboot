package app.pokeplan.ui.updates

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * In-app updates from the repository's rolling "latest" GitHub release.
 *
 * The repository is public, so the check and the download need no token and the app stores no credential. Every merge to
 * `main` republishes that one release (`.github/workflows/release-apk.yml`) with two assets: the debug APK and
 * `update.json`, a small manifest the app reads first. When a release predates the manifest the app falls back to the
 * GitHub API and the build number in the release title, so an older release can still be read.
 */

/** The GitHub repository the app is published in. */
const val UPDATE_REPO = "sophia-vista/pokeplan-reboot"

/** The rolling release every merge to `main` republishes. */
const val UPDATE_TAG = "latest"

/** The manifest asset the release workflow writes beside the APK. */
const val UPDATE_MANIFEST = "update.json"

/** The APK asset's file name inside the release. */
const val UPDATE_APK = "pokeplan.apk"

const val UPDATE_MANIFEST_URL = "https://github.com/$UPDATE_REPO/releases/download/$UPDATE_TAG/$UPDATE_MANIFEST"

const val UPDATE_RELEASE_API = "https://api.github.com/repos/$UPDATE_REPO/releases/tags/$UPDATE_TAG"

/** The tag of a version's own release, `v0.233.0`. */
fun versionTag(version: String): String = "v$version"

/** Where one asset of a release is downloaded from. */
fun updateAssetUrl(tag: String, name: String): String = "https://github.com/$UPDATE_REPO/releases/download/$tag/$name"

/** The page of one release. */
fun updateReleasePage(tag: String): String = "https://github.com/$UPDATE_REPO/releases/tag/$tag"

private const val UPDATER_USER_AGENT = "PokeLife-Updater"

private val SEMVER = Regex("^(\\d{1,4})\\.(\\d{1,3})\\.(\\d{1,3})$")
private val SHA256 = Regex("^[0-9a-fA-F]{64}$")
private val APK_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,80}\\.apk$")
private val PATCH_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,80}\\.patch\\.gz$")

/**
 * One patch that rebuilds the new APK from an older one: about 4 to 20 MB instead of the whole 116 MB download. The
 * release workflow builds it from the versioned APKs, so the update chain is version to version.
 */
data class UpdatePatch(
    val fromVersion: String,
    val fromBuild: Long = 0,
    val asset: String,
    val sizeBytes: Long = 0,
    val sha256: String = "",
)

/** One published build of PokéLife, as the release's `update.json` describes it. */
data class UpdateRelease(
    val version: String,
    val build: Long = 0,
    val sizeBytes: Long = 0,
    val sha256: String = "",
    val apkName: String = UPDATE_APK,
    val commit: String = "",
    val date: String = "",
    /** The release the assets are downloaded from: `latest`, or a version's own `vX.Y.Z`. */
    val tag: String = UPDATE_TAG,
    val patches: List<UpdatePatch> = emptyList(),
) {
    /** Where the APK is downloaded from: the same release, so a new build can never point at another file. */
    val apkUrl: String get() = updateAssetUrl(tag, apkName)

    /** Where the patch asset is downloaded from. */
    fun patchUrl(patch: UpdatePatch): String = updateAssetUrl(tag, patch.asset)

    /**
     * The patch that applies to this installed build, if the release has one. A build number identifies the versioned
     * APK exactly; a patch with no build number (a release from before the build label was written) matches by version.
     * A patch that does not reproduce the manifest's APK is dropped and the full download is used instead.
     */
    fun patchFor(installedVersion: String, installedBuild: Long): UpdatePatch? =
        patches.firstOrNull { it.fromVersion == installedVersion && it.fromBuild > 0 && it.fromBuild == installedBuild }
            ?: patches.firstOrNull { it.fromVersion == installedVersion && it.fromBuild == 0L }
}

/** The version as three numbers, or null when [text] is not `MAJOR.MINOR.PATCH`. */
fun versionParts(text: String): Triple<Int, Int, Int>? {
    val match = SEMVER.matchEntire(text.trim()) ?: return null
    val (major, minor, patch) = match.destructured
    return Triple(major.toInt(), minor.toInt(), patch.toInt())
}

/** Negative when [a] is older than [b], zero when they are the same release, positive when [a] is newer. */
fun compareVersions(a: String, b: String): Int? {
    val left = versionParts(a) ?: return null
    val right = versionParts(b) ?: return null
    return compareValuesBy(left, right, { it.first }, { it.second }, { it.third })
}

/**
 * Whether [release] can install over a phone at [installedVersion] / [installedBuild]. The Android versionCode comes from
 * VERSION alone, so a newer build number of the same version is still an accepted reinstall (Android keeps the data).
 */
fun isNewerRelease(release: UpdateRelease, installedVersion: String, installedBuild: Long): Boolean {
    val cmp = compareVersions(release.version, installedVersion) ?: return false
    if (cmp != 0) return cmp > 0
    return release.build > 0 && release.build > installedBuild
}

/** Reads the `update.json` the release workflow writes, or null when it is not a manifest this build understands. */
fun parseUpdateManifest(text: String, tag: String = UPDATE_TAG): UpdateRelease? = try {
    val root = Json.parseToJsonElement(text).jsonObject
    manifestRelease(root, tag)
} catch (e: Exception) {
    null
}

private fun manifestRelease(root: JsonObject, tag: String): UpdateRelease? {
    val version = root["version"]?.jsonPrimitive?.contentOrNull?.trim() ?: return null
    versionParts(version) ?: return null
    val apkName = root["apk"]?.jsonPrimitive?.contentOrNull?.trim() ?: UPDATE_APK
    if (!APK_NAME.matches(apkName)) return null
    val build = root["build"]?.jsonPrimitive?.longOrNull ?: 0
    if (build < 0) return null
    val size = root["size"]?.jsonPrimitive?.longOrNull ?: 0
    if (size < 0) return null
    val sha = root["sha256"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase() ?: ""
    // A sha256 that is present but malformed is a damaged manifest, not a release without one.
    if (sha.isNotEmpty() && !SHA256.matches(sha)) return null
    return UpdateRelease(
        version = version,
        build = build,
        sizeBytes = size,
        sha256 = sha,
        apkName = apkName,
        commit = root["commit"]?.jsonPrimitive?.contentOrNull?.trim() ?: "",
        date = root["date"]?.jsonPrimitive?.contentOrNull?.trim() ?: "",
        tag = tag,
        patches = manifestPatches(root),
    )
}

/** A patch entry whose file name or checksum is malformed is dropped; the full download still works. */
private fun manifestPatches(root: JsonObject): List<UpdatePatch> {
    val array = try {
        root["patches"]?.jsonArray ?: return emptyList()
    } catch (e: Exception) {
        return emptyList()
    }
    return array.mapNotNull { element ->
        val patch = element as? JsonObject ?: return@mapNotNull null
        val from = patch["from"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
        versionParts(from) ?: return@mapNotNull null
        val asset = patch["asset"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
        if (!PATCH_NAME.matches(asset)) return@mapNotNull null
        val fromBuild = patch["fromBuild"]?.jsonPrimitive?.longOrNull ?: 0
        val size = patch["size"]?.jsonPrimitive?.longOrNull ?: 0
        val sha = patch["sha256"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase() ?: ""
        if (sha.isNotEmpty() && !SHA256.matches(sha)) return@mapNotNull null
        UpdatePatch(fromVersion = from, fromBuild = fromBuild.coerceAtLeast(0), asset = asset, sizeBytes = size.coerceAtLeast(0), sha256 = sha)
    }
}

/**
 * Reads a GitHub `releases/tags/latest` answer: the fallback for a release that has no `update.json` yet. The workflow
 * titles every build "Latest build (vX.Y.Z, build N)", and the APK is always the `pokeplan.apk` asset.
 */
fun parseReleaseApi(text: String, tag: String = UPDATE_TAG): UpdateRelease? = try {
    val root = Json.parseToJsonElement(text).jsonObject
    if (root["draft"]?.jsonPrimitive?.booleanOrNull == true) return null
    val title = root["name"]?.jsonPrimitive?.contentOrNull ?: root["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
    val version = Regex("v(\\d{1,4}\\.\\d{1,3}\\.\\d{1,3})").find(title)?.groupValues?.get(1) ?: return null
    val build = Regex("build[\\s#]*(\\d+)").find(title)?.groupValues?.get(1)?.toLongOrNull() ?: 0
    val apk = root["assets"]?.jsonArray?.mapNotNull { it as? JsonObject }?.firstOrNull {
        it["name"]?.jsonPrimitive?.contentOrNull == UPDATE_APK
    } ?: return null
    val size = apk["size"]?.jsonPrimitive?.longOrNull ?: 0
    UpdateRelease(version = version, build = build, sizeBytes = size.coerceAtLeast(0), tag = tag)
} catch (e: Exception) {
    null
}

/** A download size in words, e.g. "116 MB"; empty when the release did not say. */
fun updateSize(sizeBytes: Long): String = when {
    sizeBytes <= 0 -> ""
    sizeBytes >= 1024L * 1024 * 1024 -> "${sizeBytes / (1024L * 1024 * 1024)} GB"
    sizeBytes >= 1024L * 1024 -> "${(sizeBytes + 512 * 1024) / (1024L * 1024)} MB"
    else -> "${(sizeBytes + 512) / 1024} KB"
}

/**
 * How much free space an install needs on top of the downloaded APK: the installer copies the file into its own session
 * and then writes the app, so about two more copies, plus a margin for the unpacked parts.
 */
fun installRoomNeeded(apkBytes: Long): Long = apkBytes * 2 + 64L * 1024 * 1024

/** Null when [freeBytes] is enough to install an APK of [apkBytes]; otherwise the words for the screen. */
fun installRoomProblem(apkBytes: Long, freeBytes: Long): String? {
    val needed = installRoomNeeded(apkBytes)
    if (freeBytes >= needed) return null
    val free = updateSize(freeBytes.coerceAtLeast(0)).ifEmpty { "no room" }
    return "Not enough room to install: about ${updateSize(needed)} must be free and ${if (free == "no room") "none is" else "$free is"}. Free some space, then try again."
}

/**
 * Reads the newest published build. It tries the small manifest asset first (no API rate limit); the GitHub API is the
 * fallback for a release that predates the manifest. [problem] says what went wrong in words for the screen.
 */
class UpdateClient(private val http: HttpClient = newHttpClient()) {
    var problem: String? = null
        private set

    suspend fun latest(): UpdateRelease? {
        problem = null
        val manifest = fetch(UPDATE_MANIFEST_URL)
        val rolling = manifest?.let { parseUpdateManifest(it, UPDATE_TAG) }
        val api = if (rolling == null) fetch(UPDATE_RELEASE_API, accept = "application/vnd.github+json") else null
        val release = rolling ?: api?.let { parseReleaseApi(it, UPDATE_TAG) }
        if (release == null) {
            problem = if (api == null && manifest == null) "GitHub could not be reached." else "The release could not be read."
            return null
        }
        // The rolling release carries the newest merge's APK; patches are built from the versioned APKs, which are the
        // builds this updater installs. So read the version's own release when it is there, and fall back to the rolling
        // APK for a release that predates it (no smaller update then, but the plain download still works).
        val tag = versionTag(release.version)
        val versioned = fetch(updateAssetUrl(tag, UPDATE_MANIFEST))?.let { parseUpdateManifest(it, tag) }
        return versioned ?: release
    }

    private suspend fun fetch(url: String, accept: String = "application/json"): String? = try {
        val response = http.get(url) {
            header(HttpHeaders.UserAgent, UPDATER_USER_AGENT)
            header(HttpHeaders.Accept, accept)
        }
        if (response.status.isSuccess()) response.bodyAsText() else null
    } catch (e: Exception) {
        null
    }

    fun close() = http.close()

    companion object {
        fun newHttpClient(): HttpClient = HttpClient {
            install(HttpTimeout) { connectTimeoutMillis = 8_000; requestTimeoutMillis = 20_000; socketTimeoutMillis = 20_000 }
        }
    }
}

/**
 * The Android side of updating: downloading and handing the APK to the system installer. Null on desktop and web, where
 * the app is not installed from a release; the About screen then shows no Updates box.
 */
interface UpdateAccess {
    /** The newest published build, or null with [problem] set when it could not be read. */
    suspend fun check(): UpdateRelease?

    /** What the last check or download ended on, in words for the screen. */
    val problem: String?

    /**
     * Gets the new APK, reporting 0 to 100; true when it is ready to install. A patch for the installed build is
     * downloaded and replayed when the release has one; the whole APK is the fallback.
     */
    suspend fun download(release: UpdateRelease, onProgress: (Int) -> Unit): Boolean

    /** True when a checked, complete APK for [release] is already on the phone. */
    fun downloaded(release: UpdateRelease): Boolean

    /**
     * Hands the APK to the system installer and waits for its answer: false when it could not install or the player
     * cancelled, with [problem] set to the system's own reason. On success the app is replaced, so it may never return.
     */
    suspend fun install(release: UpdateRelease): Boolean

    /** Whether this phone already lets PokéLife install an update. */
    fun canInstall(): Boolean

    /** Opens the phone's "install unknown apps" page for PokéLife. */
    fun openInstallSettings()

    /** Opens the release's page in a browser. */
    fun openReleasePage(release: UpdateRelease)

    /** Tells the player a newer build is ready, once for each version (a notification on Android; nothing elsewhere). */
    fun notifyAvailable(release: UpdateRelease) {}
}
