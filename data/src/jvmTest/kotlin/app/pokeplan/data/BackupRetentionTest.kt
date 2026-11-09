package app.pokeplan.data

import java.io.File
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupRetentionTest {
    private val day = 24 * 60 * 60 * 1000L
    // 2 November 2026 was a Monday; day 0 below is that Monday at noon UTC.
    private val monday = 1_793_491_200_000L + 12 * 60 * 60 * 1000L + day // 2 Nov 2026, 12:00 UTC

    @Test
    fun aWeekOfDailyBackupsAndFourWeeklyOnesAreKept() {
        // 60 days of one backup a day, the newest on day 59.
        val taken = (0 until 60).map { monday + it * day }
        val kept = BackupRetention.keep(taken)
        val newest = taken.max()
        assertTrue((0 until 7).all { newest - it * day in kept }, "the last seven days")
        assertEquals(7 + 4, kept.size)
        // The weekly ones are the newest of each of the four weeks before the daily ones.
        val weekly = kept.filter { it < newest - 6 * day }.sortedDescending()
        assertEquals(4, weekly.size)
        assertEquals(4, weekly.map { (it - monday) / (7 * day) }.toSet().size, "four different weeks")
    }

    @Test
    fun twoBackupsOnOneDayKeepTheNewestAndAPhoneThatWasOffKeepsWhatItHas() {
        val morning = monday
        val evening = monday + 6 * 60 * 60 * 1000L
        assertEquals(setOf(evening), BackupRetention.keep(listOf(morning, evening)))
        // Three backups months apart: all stay, none is "too old".
        val sparse = listOf(monday, monday + 40 * day, monday + 100 * day)
        assertEquals(sparse.toSet(), BackupRetention.keep(sparse))
    }

    @Test
    fun theDayIsThePlayersDayNotUtc() {
        // 23:30 and 00:30 UTC are the same day at UTC-5... no: at UTC+2 they are 01:30 and 02:30 of one day, at UTC-5 they are 18:30 and 19:30 of the day before.
        val a = monday + 11 * 60 * 60 * 1000L + 30 * 60 * 1000L // 23:30 UTC
        val b = a + 60 * 60 * 1000L // 00:30 UTC next day
        assertEquals(2, BackupRetention.keep(listOf(a, b), 0).size)
        assertEquals(1, BackupRetention.keep(listOf(a, b), 2 * 60 * 60 * 1000L).size, "one day at UTC+2")
    }
}

class SealedBackupTest {
    private fun dir() = java.nio.file.Files.createTempDirectory("bk-sealed").toFile().also { it.deleteOnExit() }
    private fun cipher(seed: Int) = AesGcmCipher(SecretKeySpec(ByteArray(32) { (it * 5 + seed).toByte() }, "AES"), "pokelife-backup")

    @Test
    fun everyFileIsSealedAndOnlyTheSameKeyReadsItBack() {
        val s = JvmDatabase.open()
        s.live.activate("""{"journal":"my private diary"}""", """{"c":1}""", 0)
        val d = dir()
        val b = LiveBackup(d, sealWith = cipher(1), requireSeal = true)
        assertNotNull(b.take(s.live, 1000))
        val files = d.walkTopDown().filter { it.isFile && it.name.endsWith(".json") }.toList()
        assertTrue(files.isNotEmpty() && files.all { it.readText().startsWith(LiveBackup.SEALED) && "diary" !in it.readText() })
        assertEquals("""{"journal":"my private diary"}""", b.documentsOf(1000)!!["save"])
        assertNull(LiveBackup(d, sealWith = cipher(2)).documentsOf(1000), "another key opens nothing")
        assertNull(LiveBackup(d).documentsOf(1000), "no key opens nothing")
    }

    @Test
    fun withoutAKeyNothingIsWrittenInClearWhenSealingIsRequired() {
        val s = JvmDatabase.open()
        s.live.activate("{}", "{}", 0)
        val d = dir()
        assertNull(LiveBackup(d, requireSeal = true).take(s.live, 1000))
        assertTrue(d.walkTopDown().none { it.isFile })
    }

    @Test
    fun aPlainBackupOfAnEarlierBuildStillRestores() {
        val s = JvmDatabase.open()
        s.live.activate("""{"v":1}""", """{"c":1}""", 0)
        val d = dir()
        LiveBackup(d).take(s.live, 1000)
        s.live.write("save", """{"v":2}""", 2000)
        assertTrue(LiveBackup(d, sealWith = cipher(1)).restore(s.live, 1000, 3000))
        assertEquals("""{"v":1}""", s.live.read("save"))
    }

    @Test
    fun restoreNewestSkipsABackupItCannotOpen() {
        val s = JvmDatabase.open()
        s.live.activate("""{"v":1}""", """{"c":1}""", 0)
        val d = dir()
        val later = 2 * 24 * 60 * 60 * 1000L // another day, or the day's newest backup would replace the first
        LiveBackup(d, sealWith = cipher(1)).take(s.live, 1000)
        s.live.write("save", """{"v":2}""", 1500)
        LiveBackup(d, sealWith = cipher(2)).take(s.live, later) // taken under a key that is gone later
        val b = LiveBackup(d, sealWith = cipher(1))
        assertTrue(b.restoreNewest(s.live, later + 1000))
        assertEquals("""{"v":1}""", s.live.read("save"))
        assertFalse(b.list().isEmpty())
    }
}
