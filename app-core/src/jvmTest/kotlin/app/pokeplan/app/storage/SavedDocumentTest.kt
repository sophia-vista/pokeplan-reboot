package app.pokeplan.app.storage

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SavedDocumentTest {
    @Serializable
    data class Doc(val n: Int = 0)

    private val json = Json
    private fun document(storage: Storage) = SavedDocument(storage, Doc.serializer(), json) { Doc() }
    private fun folder(): File = Files.createTempDirectory("saved-document").toFile()

    @Test
    fun aSaveThatThrowsLeavesTheDocumentAndItsListenersAsTheyWere() {
        val failing = object : Storage {
            var fail = false
            var text: String? = null
            override fun load() = text
            override fun save(text: String) { if (fail) throw java.io.IOException("disk full"); this.text = text }
            override fun keepAside(text: String) {}
        }
        val doc = document(failing)
        val heard = mutableListOf<Int>()
        doc.observe { heard += it.n }
        doc.write(Doc(1))
        failing.fail = true
        assertFailsWith<java.io.IOException> { doc.write(Doc(2)) }
        assertEquals(Doc(1), doc.value, "the memory never gets ahead of the disk")
        assertEquals(listOf(1), heard)
        failing.fail = false
        doc.write(Doc(3))
        assertEquals(Doc(3), doc.value)
    }

    @Test
    fun anUnreadableFileFallsBackOnTheCopyFromBeforeItsLastWrite() {
        val dir = folder()
        val file = File(dir, "save.json")
        val storage = FileStorage(file)
        val first = document(storage)
        first.write(Doc(1))
        first.write(Doc(2))
        file.writeText("{not json")

        val storage2 = FileStorage(file)
        val reopened = document(storage2)
        assertEquals(Doc(1), reopened.value, "the previous good save, not an empty one")
        assertEquals("{\"n\":1}", file.readText(), "and the file is good again, so the next write does not copy the damaged one over the backup")
        assertTrue(dir.listFiles().orEmpty().any { it.name.startsWith("save.json.unreadable-") && it.readText() == "{not json" }, "the damaged file is kept aside")
        reopened.write(Doc(5))
        assertEquals(Doc(5), document(FileStorage(file)).value)
    }

    @Test
    fun withNoUsableBackupTheDocumentStartsEmptyAndKeepsTheDamagedFile() {
        val storage = MemoryStorage("{not json")
        storage.backup = "{also not json"
        assertEquals(Doc(), document(storage).value)
        assertEquals("{not json", storage.keptAside)
    }

    @Test
    fun aFailedReplaceNeverWritesOverTheLiveSave() {
        val dir = folder()
        val file = File(dir, "save.json")
        val storage = FileStorage(file)
        storage.save("{\"n\":1}")
        File(dir, "save.json.tmp").mkdir()
        assertFailsWith<java.io.IOException> { storage.save("{\"n\":2}") }
        assertEquals("{\"n\":1}", file.readText())
    }

    @Test
    fun changesFromManyThreadsAreAllKept() {
        val doc = document(MemoryStorage())
        val threads = 8
        val each = 200
        val pool = Executors.newFixedThreadPool(threads)
        val go = CountDownLatch(1)
        repeat(threads) { pool.execute { go.await(); repeat(each) { doc.update { it.copy(n = it.n + 1) } } } }
        go.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        assertEquals(threads * each, doc.value.n)
    }

    @Test
    fun theBackupHoldsTheSaveBeforeTheLastWrite() {
        val file = File(folder(), "save.json")
        val storage = FileStorage(file)
        storage.save("one")
        assertEquals(null, storage.loadBackup())
        storage.save("two")
        assertEquals("one", storage.loadBackup())
        assertNotNull(storage.load())
        assertEquals("two", storage.load())
    }
}
