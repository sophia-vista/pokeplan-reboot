package app.pokeplan.data

import app.cash.sqldelight.db.QueryResult
import app.pokeplan.app.AppData
import app.pokeplan.app.Clock
import app.pokeplan.app.GameStore
import app.pokeplan.app.HealStore
import app.pokeplan.app.MemoryStorage
import app.pokeplan.app.Storage
import app.pokeplan.heal.ActivityType
import app.pokeplan.heal.MeasureType
import kotlinx.datetime.TimeZone
import java.io.File
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Heal's rows and backups sealed with a key of the phone (owner decision 6, 8 November 2026; 0.152.0): the one-time move of an install that has its records in
 * clear, the same sealing from the start, a key that is missing or another phone's, and the daily and portable backups. Synthetic data only.
 */
class HealSealingTest {
    private val start = 1_793_000_000_000L
    private val clock = object : Clock { override fun nowMillis() = start }
    private val sealer = Pbkdf2Sealer(iterations = 1_000)
    private val phrase = "correct horse battery"

    private class Tables(private val live: LiveDocuments) : Storage {
        override fun load(): String? = live.read("heal")
        override fun save(text: String) = live.write("heal", text, 1)
        override fun keepAside(text: String) = live.keepAside("heal", text, 1)
    }

    private fun key(seed: Int) = AesGcmCipher(SecretKeySpec(ByteArray(32) { (it * 7 + seed).toByte() }, "AES"), "pokelife-heal")
    private fun game() = GameStore(MemoryStorage(), clock, TimeZone.UTC, Random(7)).also { it.start("Sophia", 25) }
    private fun healOn(s: DataStore, g: GameStore) = HealStore(Tables(s.live), clock, g, TimeZone.UTC, rng = Random(3))

    /** A phone whose Heal has been in clear (an install older than 0.152.0) with some records. */
    private fun clearPhone(file: String? = null): Pair<DataStore, GameStore> {
        val s = if (file == null) JvmDatabase.open() else JvmDatabase.open(file)
        s.live.activate(null, null, 1); s.live.moveHealToTables(2)
        val g = game()
        val heal = healOn(s, g)
        heal.logActivity(ActivityType.RUN, 40, distanceMeters = 6500, note = "morning-secret")
        heal.logDrink(300, "tea-secret")
        heal.logSleep(start - 8 * 3_600_000L, start)
        heal.logMeasurement(MeasureType.WEIGHT, 70.5, "kg")
        return s to g
    }

    private fun count(s: DataStore, sql: String): Long =
        s.driver.executeQuery(null, sql, { c -> QueryResult.Value(if (c.next().value) c.getLong(0) else null) }, 0).value ?: 0L

    private val tables = listOf("heal_activity", "heal_hydration", "heal_sleep", "heal_measurement", "heal_goal", "heal_state")

    private fun everyStoredText(s: DataStore): List<String> = tables.flatMap { t ->
        val out = mutableListOf<String>()
        s.driver.executeQuery(null, "SELECT json FROM $t", { c -> while (c.next().value) out += c.getString(0)!!; QueryResult.Unit }, 0)
        out
    }

    @Test
    fun anInstallWithRecordsInClearIsSealedOnceAndReadsBackEqual() {
        val (s, g) = clearPhone()
        val before = healOn(s, g).data
        assertTrue(everyStoredText(s).any { "morning-secret" in it }, "the records start in clear")
        assertNotNull(s.healTables.read()).let { assertTrue("morning-secret" in it) }
        assertEquals(1, count(s, "SELECT count(*) FROM heal_sleep WHERE day IS NOT NULL"), "a clear row keeps its day beside it")

        s.healCipher = key(1)
        assertFalse(s.healTables.isSealed())
        assertTrue(s.healTables.seal(10))
        assertTrue(s.healTables.isSealed())
        assertEquals("10", s.db.dataQueries.meta(HealTables.META_HEAL_SEALED_AT).executeAsOne())

        val texts = everyStoredText(s)
        assertTrue(texts.isNotEmpty() && texts.all { RowCipher.isSealed(it) }, "every row is sealed text")
        for (secret in listOf("morning-secret", "tea-secret", "6500", "70.5", "WEIGHT", "2026")) assertFalse(texts.any { secret in it }, "a row still shows $secret")
        assertEquals(0, count(s, "SELECT count(*) FROM heal_activity WHERE day IS NOT NULL") + count(s, "SELECT count(*) FROM heal_sleep WHERE day IS NOT NULL") + count(s, "SELECT count(*) FROM heal_hydration WHERE day IS NOT NULL") + count(s, "SELECT count(*) FROM heal_measurement WHERE day IS NOT NULL"), "no date is left beside a sealed row")
        assertEquals(before, healOn(s, g).data, "Heal reads back equal to what it was, field for field")

        assertFalse(s.healTables.seal(11), "it seals once")
        assertEquals("10", s.db.dataQueries.meta(HealTables.META_HEAL_SEALED_AT).executeAsOne())

        // And it keeps working sealed: new rows are sealed too, and only a changed row is rewritten.
        val heal = healOn(s, g)
        heal.logDrink(250, "coffee-secret")
        assertTrue(everyStoredText(s).all { RowCipher.isSealed(it) })
        assertEquals(2, healOn(s, g).data.hydration.size)
        s.healTables.scrub() // best effort; the data stays
        assertEquals(2, healOn(s, g).data.hydration.size)
        assertTrue(s.integrityIssues().isEmpty())
    }

    @Test
    fun theKeptAsideCopiesOfAMigrationAreSealedWithTheRows() {
        val s = JvmDatabase.open().also { it.live.activate(null, null, 1) }
        s.live.write("heal", """{"activities":[{"id":"a1","type":"WALK","startMillis":5,"minutes":30,"day":"2026-11-07","note":"aside-secret"}]}""", 2) // a restore before activation
        s.live.moveHealToTables(3)
        assertTrue("aside-secret" in s.live.read("aside:heal:tables:3")!!)
        s.healCipher = key(1)
        assertTrue(s.healTables.seal(4))
        val aside = s.live.read("aside:heal:tables:3")!!
        assertTrue(RowCipher.isSealed(aside) && "aside-secret" !in aside)
        assertEquals("aside-secret", s.healCipher!!.open(aside)!!.let { Regex("aside-secret").find(it)!!.value })
        assertTrue("aside-secret" in s.live.read("heal")!!, "and the live Heal still has it")
    }

    @Test
    fun withoutAKeyHealStaysInClearAndNothingBreaks() {
        val (s, g) = clearPhone()
        assertFalse(s.healTables.seal(10), "no key: nothing is sealed")
        assertFalse(s.healTables.isSealed())
        healOn(s, g).logDrink(100)
        assertEquals(2, healOn(s, g).data.hydration.size, "Heal keeps working in clear for that run")
    }

    @Test
    fun aSealedHealWithNoKeyReadsAsLockedAndWritesNothing() {
        val (s, g) = clearPhone()
        s.healCipher = key(1); s.healTables.seal(10)
        s.healCipher = null // a run on which the Keystore gave no key
        assertTrue(s.healTables.locked())
        assertNull(s.live.read("heal"))
        assertFailsWith<HealLockedException> { s.healTables.write("""{"activities":[]}""") }
        assertFailsWith<HealLockedException> { s.live.keepAside("heal", "{}", 5) }
        assertFalse("heal" in s.live.names())
        assertTrue(s.live.snapshot().none { it.key == "heal" })
        s.healCipher = key(1) // the key is back: nothing was lost
        assertFalse(s.healTables.locked())
        assertEquals(1, healOn(s, g).data.activities.size)
    }

    @Test
    fun aDatabaseCopiedToAnotherPhoneIsLockedAndTheOldRowsAreKeptAsideSealedBeforeAnythingIsWritten() {
        val (s, _) = clearPhone()
        s.healCipher = key(1); s.healTables.seal(10)
        val before = everyStoredText(s).toSet()
        s.healCipher = key(2) // another phone's key opens none of it
        assertTrue(s.healTables.locked())
        assertNull(s.live.read("heal"), "Heal reads as empty, never as garbage")
        val g = game()
        healOn(s, g).logDrink(100) // the first write sets the sealed rows aside, still sealed, then starts empty
        val asides = s.live.names().filter { it.startsWith("aside:heal:locked:") }
        assertEquals(1, asides.size)
        val kept = s.live.read(asides.single())!!
        for (text in before) assertTrue(text in kept, "a sealed row was kept")
        assertFalse("morning-secret" in kept)
        assertEquals(1, healOn(s, g).data.hydration.size)
        assertEquals(0, healOn(s, g).data.activities.size)
    }

    @Test
    fun healLeavesTheTablesSealedInASnapshotAndComesBackOnTheSamePhone() {
        val (s, g) = clearPhone()
        s.healCipher = key(1); s.healTables.seal(10)
        s.live.write("save", GameStore.json.encodeToString(AppData.serializer(), g.data), 3)
        val snap = s.live.snapshot()
        val sealed = snap.getValue("heal")
        assertTrue(RowCipher.isSealed(sealed) && "morning-secret" !in sealed)
        val file = BackupBundle.encode(snap, DataStore.SCHEMA_VERSION, 5)
        assertFalse("morning-secret" in file || "tea-secret" in file, "a backup file holds no Heal in clear")
        val ready = assertIs<BackupBundle.Staged.Ready>(BackupBundle.stage(file, DataStore.SCHEMA_VERSION))
        assertFalse(ready.needsPassphrase)
        healOn(s, g).logDrink(100)
        s.live.replaceAll(ready.documents, 12)
        assertEquals(1, healOn(s, g).data.hydration.size, "the restore brought the backup's Heal back")
        val aside = s.live.read("aside:heal:12")!!
        assertTrue(RowCipher.isSealed(aside), "the Heal it replaced is kept aside sealed")
    }

    @Test
    fun aBackupFromAnotherPhonesKeyIsKeptSealedAndChangesNothing() {
        val (a, _) = clearPhone()
        a.healCipher = key(1); a.healTables.seal(10)
        val snap = a.live.snapshot()
        val (b, gb) = clearPhone()
        b.healCipher = key(2); b.healTables.seal(10)
        val mine = healOn(b, gb).data
        b.live.replaceAll(snap, 20)
        assertEquals(mine, healOn(b, gb).data, "a Heal this phone cannot open is not written over its own")
        assertTrue(RowCipher.isSealed(b.live.read("aside:heal:locked:20")!!))
    }

    @Test
    fun aPlainBackupFromAnOlderBuildIsSealedAsItIsWritten() {
        val (old, _) = clearPhone()
        val oldSnap = old.live.snapshot()
        assertTrue("morning-secret" in oldSnap.getValue("heal"), "an older build's backup holds Heal in clear")
        val (s, g) = clearPhone()
        s.healCipher = key(1); s.healTables.seal(10)
        s.live.replaceAll(oldSnap, 30)
        assertTrue(everyStoredText(s).all { RowCipher.isSealed(it) })
        assertEquals(1, healOn(s, g).data.activities.size)
    }

    @Test
    fun aPassphraseSealedBackupOpensHealOnAnotherPhone() {
        val (home, g) = clearPhone()
        home.healCipher = key(1); home.healTables.seal(10)
        home.live.write("save", GameStore.json.encodeToString(AppData.serializer(), g.data), 3)
        val snap = home.live.snapshot()
        val portable = assertNotNull(BackupBundle.portable(snap, mapOf("heal" to key(1)), phrase, sealer))
        assertTrue(PassphraseSealer.isSealed(portable.getValue("heal")))
        val file = BackupBundle.encode(portable, DataStore.SCHEMA_VERSION, 9)
        for (secret in listOf("morning-secret", "tea-secret", "6500", "70.5")) assertFalse(secret in file, "the file holds $secret in clear")
        val ready = assertIs<BackupBundle.Staged.Ready>(BackupBundle.stage(file, DataStore.SCHEMA_VERSION))
        assertTrue(ready.needsPassphrase)
        assertNull(BackupBundle.unlock(ready, null, sealer))
        assertNull(BackupBundle.unlock(ready, "not the passphrase", sealer))

        val other = JvmDatabase.open().also { it.live.activate(null, null, 1); it.live.moveHealToTables(2); it.healCipher = key(2); it.healTables.seal(3) }
        other.live.replaceAll(assertNotNull(BackupBundle.unlock(ready, phrase, sealer)), 11)
        val back = healOn(other, g).data
        assertEquals("morning-secret", back.activities.single().note)
        assertTrue(other.live.names().none { it.startsWith("aside:heal:locked:") })
        assertTrue(everyStoredText(other).all { RowCipher.isSealed(it) }, "after the restore Heal is sealed to the new phone's key")
    }

    @Test
    fun aHealThatCannotBeOpenedHereCannotBeMadePortable() {
        val (home, _) = clearPhone()
        home.healCipher = key(1); home.healTables.seal(10)
        val snap = home.live.snapshot()
        assertNull(BackupBundle.portable(snap, mapOf("heal" to key(2)), phrase, sealer))
        assertNull(BackupBundle.portable(snap, emptyMap(), phrase, sealer), "no key open on this run")
        assertEquals(mapOf("save" to "{}"), BackupBundle.portable(mapOf("save" to "{}"), emptyMap(), phrase, sealer))
    }

    @Test
    fun mintAndHealTravelTogetherUnderOnePassphrase() {
        val docs = mapOf("mint" to "m1.x", "heal" to """{"activities":[]}""", "save" to "{}")
        val mintKey = AesGcmCipher(SecretKeySpec(ByteArray(32) { (it * 3 + 9).toByte() }, "AES"))
        val sealedMint = mintKey.seal("""{"accounts":[]}""")
        val portable = assertNotNull(BackupBundle.portable(docs + ("mint" to sealedMint), mapOf("mint" to mintKey), phrase, sealer))
        assertTrue(PassphraseSealer.isSealed(portable.getValue("mint")) && PassphraseSealer.isSealed(portable.getValue("heal")), "a clear Heal is sealed under the passphrase too")
        val ready = assertIs<BackupBundle.Staged.Ready>(BackupBundle.stage(BackupBundle.encode(portable, DataStore.SCHEMA_VERSION, 1), DataStore.SCHEMA_VERSION))
        val open = assertNotNull(BackupBundle.unlock(ready, phrase, sealer))
        assertEquals("""{"accounts":[]}""", open.getValue("mint"))
        assertEquals("""{"activities":[]}""", open.getValue("heal"))
    }

    @Test
    fun theDailyCopiesMadeBeforeTheSealAreReplacedByASealedOne() {
        val dir = java.nio.file.Files.createTempDirectory("bk").toFile().also { it.deleteOnExit() }
        val b = LiveBackup(dir, keep = 3, everyMillis = 1000)
        val (s, g) = clearPhone()
        s.live.write("save", GameStore.json.encodeToString(AppData.serializer(), g.data), 3)
        b.take(s.live, 100)
        b.take(s.live, 5_000)
        assertTrue(b.list().all { "morning-secret" in File(dir, "${it.millis}/heal.json").readText() }, "the copies of an older build hold Heal in clear")
        s.healCipher = key(1)
        assertTrue(s.healTables.seal(10))
        assertTrue(b.refreshAfterSealing(s.live, 9_000))
        val left = b.list()
        assertEquals(listOf(9_000L), left.map { it.millis }, "the clear copies are gone and one sealed copy is made")
        assertTrue(RowCipher.isSealed(File(dir, "9000/heal.json").readText()))
        // Restoring that copy brings Heal back.
        healOn(s, g).logDrink(100)
        assertTrue(b.restore(s.live, 9_000, 9_500))
        assertEquals(0, healOn(s, g).data.hydration.size - 1)
    }

    @Test
    fun aFreshInstallIsSealedFromItsFirstRow() {
        val s = JvmDatabase.open().also { it.live.activate(null, null, 1); it.healCipher = key(1); it.live.moveHealToTables(2) }
        assertTrue(s.healTables.seal(3), "nothing to rewrite, the marker is set")
        val g = game()
        healOn(s, g).logActivity(ActivityType.WALK, 20, note = "first-secret")
        assertTrue(everyStoredText(s).all { RowCipher.isSealed(it) && "first-secret" !in it })
        assertEquals("first-secret", healOn(s, g).data.activities.single().note)
        // Export of one kind and erasing it still work through the key.
        assertTrue("first-secret" in s.healTables.readKind("activities"))
        s.healTables.erase("activities")
        assertEquals(0, healOn(s, g).data.activities.size)
    }

    @Test
    fun aSealingThatCannotFinishLeavesTheRowsInClear() {
        val (s, g) = clearPhone()
        s.healCipher = key(1)
        s.driver.execute(null, "CREATE TRIGGER no_seal BEFORE INSERT ON app_meta WHEN NEW.key = 'heal_sealed' BEGIN SELECT RAISE(ABORT, 'no'); END", 0)
        assertFailsWith<Exception> { s.healTables.seal(10) }
        assertFalse(s.healTables.isSealed())
        assertTrue(everyStoredText(s).any { "morning-secret" in it }, "the rows written before the failure were rolled back")
        assertEquals(1, healOn(s, g).data.activities.size)
    }

    @Test
    fun sealedRowsSurviveReopeningTheFile() {
        val f = File.createTempFile("heal", ".db").also { it.delete(); it.deleteOnExit() }
        val (s, g) = clearPhone(f.path)
        val before = healOn(s, g).data
        s.healCipher = key(1); s.healTables.seal(10); s.healTables.scrub()
        s.close()
        val again = JvmDatabase.open(f.path).also { it.healCipher = key(1) }
        assertTrue(again.integrityIssues().isEmpty())
        assertTrue(again.healTables.isSealed())
        assertEquals(before, healOn(again, g).data)
        assertFalse(f.readBytes().decodeToString().contains("morning-secret"), "the file itself no longer holds the note")
    }
}
