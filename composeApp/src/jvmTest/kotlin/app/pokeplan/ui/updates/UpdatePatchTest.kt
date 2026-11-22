package app.pokeplan.ui.updates

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdatePatchTest {
    // The fixture pair is made by tools/update/make_fixture.py with tools/update/make_patch.py, so this test also proves
    // the script and the applier still agree on the format.
    private fun resource(name: String): ByteArray =
        javaClass.getResourceAsStream("/update/$name")?.readBytes() ?: error("missing test resource /update/$name")

    private fun decoded(name: String): ByteArray = GZIPInputStream(resource(name).inputStream()).readBytes()

    private fun sha256(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun theCommittedPatchRebuildsTheNewApk() {
        val old = resource("old.apk")
        val new = resource("new.apk")
        val plan = assertNotNull(parseUpdatePatch(decoded("update.patch.gz"), old.size.toLong()))
        assertEquals(new.size.toLong(), plan.targetSize)
        assertEquals(sha256(new), plan.targetSha256Hex)

        val out = ByteArrayOutputStream()
        var lastProgress = -1
        val ok = plan.apply(
            readInstalled = { offset, length -> old.copyOfRange(offset.toInt(), offset.toInt() + length) },
            write = { bytes, offset, length -> out.write(bytes, offset, length) },
            onProgress = { lastProgress = it },
        )
        assertTrue(ok)
        assertContentEquals(new, out.toByteArray())
        assertEquals(100, lastProgress)
    }

    @Test
    fun aDamagedPatchIsRefused() {
        val old = resource("old.apk")
        val patch = decoded("update.patch.gz")
        assertNull(parseUpdatePatch(patch.copyOf(patch.size / 2), old.size.toLong()))
        assertNull(parseUpdatePatch(ByteArray(0), old.size.toLong()))
        assertNull(parseUpdatePatch(patch.copyOf().also { it[0] = 'X'.code.toByte() }, old.size.toLong()))
        // A copy that runs past the installed APK is not applied.
        assertNull(parseUpdatePatch(patch, old.size.toLong() / 2))
    }

    @Test
    fun aMissingInstalledByteStopsTheApply() {
        val old = resource("old.apk")
        val new = resource("new.apk")
        val plan = assertNotNull(parseUpdatePatch(decoded("update.patch.gz"), old.size.toLong()))
        val out = ByteArrayOutputStream()
        val ok = plan.apply(
            readInstalled = { _, _ -> null },
            write = { bytes, offset, length -> out.write(bytes, offset, length) },
        )
        // The first op may be a literal; a copy somewhere in the stream must fail.
        assertTrue(!ok || out.size() < new.size)
    }

    /** Only runs when a probe points at two real release APKs and their patch, so CI never needs the 120 MB files. */
    @Test
    fun aRealReleaseApkIsRebuiltWhenAsked() {
        val base = System.getenv("POKELIFE_PATCH_PROBE") ?: return
        val old = File(base, "old.apk")
        val new = File(base, "new.apk")
        val patch = File(base, "patch.patch.gz")
        if (!old.isFile || !new.isFile || !patch.isFile) return
        val plan = assertNotNull(parseUpdatePatch(GZIPInputStream(patch.inputStream()).readBytes(), old.length()))
        assertEquals(new.length(), plan.targetSize)
        assertEquals(sha256(new.readBytes()), plan.targetSha256Hex)
        val out = File(base, "rebuilt.apk")
        java.io.RandomAccessFile(old, "r").use { source ->
            java.io.FileOutputStream(out).use { sink ->
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val ok = plan.apply(
                    readInstalled = { offset, length -> ByteArray(length).also { source.seek(offset); source.readFully(it) } },
                    write = { bytes, offset, length -> sink.write(bytes, offset, length); digest.update(bytes, offset, length) },
                )
                assertTrue(ok)
                assertEquals(sha256(new.readBytes()), digest.digest().joinToString("") { "%02x".format(it) })
            }
        }
        out.delete()
        println("POKELIFE_PATCH_PROBE rebuilt ${new.name}: ${new.length()} bytes, SHA-256 matched")
    }
}
