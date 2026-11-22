package app.pokeplan.ui.updates

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdatesTest {
    private val sha = "a".repeat(64)
    private val patchSha = "b".repeat(64)

    private fun manifest(
        version: String = "0.232.0",
        build: Long = 344,
        size: Long = 121_591_547,
        sha256: String = sha,
        apk: String = "pokeplan.apk",
        patches: String = "",
    ) = """{"version":"$version","build":$build,"apk":"$apk","size":$size,"sha256":"$sha256","commit":"abc1234","date":"2026-11-22","patches":[$patches]}"""

    private fun patch(from: String = "0.231.1", fromBuild: Long = 343, asset: String = "patch-0.231.1-to-0.232.0.patch.gz", size: Long = 4_461_963, sha256: String = patchSha) =
        """{"from":"$from","fromBuild":$fromBuild,"asset":"$asset","size":$size,"sha256":"$sha256"}"""

    private fun api(name: String = "Latest build (v0.232.0, build 350)") =
        """{"name":"$name","tag_name":"latest","draft":false,"prerelease":true,"assets":[{"name":"pokeplan.apk","size":121591547,"browser_download_url":"https://github.com/x/y/releases/download/latest/pokeplan.apk"}]}"""

    private val versionedManifest = manifest(apk = "pokeplan-v0.232.0.apk", patches = patch())
    private val versionedUrl = updateAssetUrl(versionTag("0.232.0"), UPDATE_MANIFEST)

    @Test
    fun aManifestIsRead() {
        val release = assertNotNull(parseUpdateManifest(manifest()))
        assertEquals("0.232.0", release.version)
        assertEquals(344L, release.build)
        assertEquals(121_591_547L, release.sizeBytes)
        assertEquals(sha, release.sha256)
        assertEquals(UPDATE_TAG, release.tag)
        assertEquals(updateAssetUrl(UPDATE_TAG, "pokeplan.apk"), release.apkUrl)
        assertTrue(release.patches.isEmpty())
    }

    @Test
    fun aVersionedManifestCarriesItsPatch() {
        val release = assertNotNull(parseUpdateManifest(versionedManifest, tag = versionTag("0.232.0")))
        assertEquals("v0.232.0", release.tag)
        assertEquals("pokeplan-v0.232.0.apk", release.apkName)
        assertEquals("https://github.com/$UPDATE_REPO/releases/download/v0.232.0/pokeplan-v0.232.0.apk", release.apkUrl)
        val entry = assertNotNull(release.patches.singleOrNull())
        assertEquals("0.231.1", entry.fromVersion)
        assertEquals(343L, entry.fromBuild)
        assertEquals(4_461_963L, entry.sizeBytes)
        assertEquals("https://github.com/$UPDATE_REPO/releases/download/v0.232.0/patch-0.231.1-to-0.232.0.patch.gz", release.patchUrl(entry))
        assertEquals(entry, release.patchFor("0.231.1", 343))
        assertNull(release.patchFor("0.231.1", 342))
    }

    @Test
    fun aPatchWithoutABuildMatchesTheVersion() {
        val release = assertNotNull(parseUpdateManifest(manifest(patches = patch(fromBuild = 0, from = "0.230.0")), tag = "v0.232.0"))
        assertNotNull(release.patchFor("0.230.0", 355))
        assertNotNull(release.patchFor("0.230.0", 0))
    }

    @Test
    fun brokenPatchEntriesAreDroppedNotTheWholeManifest() {
        val release = assertNotNull(
            parseUpdateManifest(
                manifest(patches = listOf(patch(asset = "../bad.patch.gz"), patch(asset = "other.bin"), patch(sha256 = "short"), patch()).joinToString(",")),
            ),
        )
        assertEquals(1, release.patches.size)
    }

    @Test
    fun aBrokenManifestIsRefused() {
        assertNull(parseUpdateManifest("not json"))
        assertNull(parseUpdateManifest("""{"version":"0.229","build":1}"""))
        assertNull(parseUpdateManifest("""{"version":"0.229.0","build":1,"sha256":"short"}"""))
        assertNull(parseUpdateManifest(manifest(apk = "../pokeplan.apk")))
        assertNull(parseUpdateManifest(manifest(apk = "other.bin")))
        assertNull(parseUpdateManifest(manifest(build = -1)))
        // No checksum at all is a release from before the manifest carried one, not a damaged one.
        assertNotNull(parseUpdateManifest(manifest(sha256 = "")))
    }

    @Test
    fun theApiAnswerIsTheFallback() {
        val release = assertNotNull(parseReleaseApi(api()))
        assertEquals("0.232.0", release.version)
        assertEquals(350L, release.build)
        assertEquals(121_591_547L, release.sizeBytes)
        assertEquals(UPDATE_TAG, release.tag)
        assertNull(parseReleaseApi("""{"name":"Latest build","assets":[]}"""))
        // A versioned release has an APK named after the version, so the fallback does not guess.
        assertNull(parseReleaseApi("""{"name":"PokéPlan v0.230.0","assets":[{"name":"pokeplan-v0.230.0.apk"}]}"""))
    }

    @Test
    fun versionsCompareInOrder() {
        assertEquals(0, compareVersions("0.229.0", "0.229.0"))
        assertTrue(compareVersions("0.230.0", "0.229.9")!! > 0)
        assertTrue(compareVersions("0.229.0", "0.229.1")!! < 0)
        assertTrue(compareVersions("1.0.0", "0.999.999")!! > 0)
        assertNull(compareVersions("0.229", "0.229.0"))
        assertNull(compareVersions("dev", "0.229.0"))
    }

    @Test
    fun aNewerVersionOrBuildCountsAsAnUpdate() {
        fun release(version: String, build: Long) = UpdateRelease(version, build)
        assertTrue(isNewerRelease(release("0.229.0", 339), "0.228.3", 330))
        assertTrue(isNewerRelease(release("0.229.0", 340), "0.229.0", 339))
        assertFalse(isNewerRelease(release("0.229.0", 339), "0.229.0", 339))
        assertFalse(isNewerRelease(release("0.228.9", 999), "0.229.0", 1))
        // A local build says "dev" (build 0) and is never older than the release of its own version.
        assertTrue(isNewerRelease(release("0.229.0", 339), "0.229.0", 0))
    }

    @Test
    fun sizesAreInWords() {
        assertEquals("", updateSize(0))
        assertEquals("116 MB", updateSize(121_525_880L))
        assertEquals("1 KB", updateSize(700L))
        assertEquals("1 GB", updateSize(1024L * 1024 * 1024))
    }

    @Test
    fun tagsAndPagesAreBuiltFromTheVersion() {
        assertEquals("v0.232.0", versionTag("0.232.0"))
        assertEquals("https://github.com/$UPDATE_REPO/releases/tag/v0.232.0", updateReleasePage("v0.232.0"))
    }

    @Test
    fun theClientPrefersTheVersionedManifest() = runTest {
        val http = HttpClient(MockEngine { request ->
            when (request.url.toString()) {
                UPDATE_MANIFEST_URL -> respond(manifest(apk = "pokeplan.apk"))
                versionedUrl -> respond(versionedManifest)
                else -> respondError(HttpStatusCode.NotFound)
            }
        })
        val client = UpdateClient(http)
        val release = assertNotNull(client.latest())
        assertEquals("v0.232.0", release.tag)
        assertEquals("pokeplan-v0.232.0.apk", release.apkName)
        assertNotNull(release.patchFor("0.231.1", 343))
        assertNull(client.problem)
    }

    @Test
    fun theClientFallsBackToTheRollingReleaseFromTheApi() = runTest {
        val http = HttpClient(MockEngine { request ->
            when (request.url.toString()) {
                UPDATE_MANIFEST_URL -> respondError(HttpStatusCode.NotFound)
                UPDATE_RELEASE_API -> respond(api())
                else -> respondError(HttpStatusCode.NotFound)
            }
        })
        val client = UpdateClient(http)
        val release = assertNotNull(client.latest())
        assertEquals("0.232.0", release.version)
        assertEquals(UPDATE_TAG, release.tag)
        assertNull(client.problem)
    }

    @Test
    fun theClientSendsTheRollingReleaseWhenTheVersionHasNoManifest() = runTest {
        val http = HttpClient(MockEngine { request ->
            if (request.url.toString() == UPDATE_MANIFEST_URL) respond(manifest()) else respondError(HttpStatusCode.NotFound)
        })
        val client = UpdateClient(http)
        val release = assertNotNull(client.latest())
        assertEquals(UPDATE_TAG, release.tag)
        assertTrue(release.patches.isEmpty())
    }

    @Test
    fun theClientSaysWhenGitHubCannotBeReached() = runTest {
        val http = HttpClient(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) })
        val client = UpdateClient(http)
        assertNull(client.latest())
        assertEquals("GitHub could not be reached.", client.problem)
    }
}
