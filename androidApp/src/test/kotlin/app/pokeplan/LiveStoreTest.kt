package app.pokeplan

import app.pokeplan.app.care.CareStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.data.AndroidDatabase
import app.pokeplan.heal.ActivityType
import app.pokeplan.mint.AccountType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
class LiveStoreTest : AndroidTestBase() {
    private val clock = Clock { System.currentTimeMillis() }

    private fun app.pokeplan.app.AppData.timeless() = copy(
        pokeMigration = pokeMigration?.copy(migratedAtMillis = 0),
        habitsMergedAtMillis = habitsMergedAtMillis?.let { 0 },
        series = series.map { it.copy(template = it.template.copy(createdAtMillis = 0)) },
    )

    private fun mainFixtures() = listOf("main-empty.json", "main-typical-week.json", "main-heavy-70-days.json", "main-focus-running-and-pending.json")

    @Test fun aNewInstallStartsOnTheDatabaseAndKeepsWhatWasDoneAcrossAProcessDeath() {
        val app = widgetStore(context)
        val task = app.addTask("Water the plants")
        assertTrue(File(files, "live.active").exists(), "the database is the live store from the first start")
        assertFalse(File(files, "save.json").exists(), "nothing is written to the old file")
        processDeath()
        assertEquals("Water the plants", widgetStore(context).data.tasks.single { it.id == task.id }.title)
    }

    @Test fun everyFixtureIsCarriedOverIntoTheDatabaseUnchanged() {
        for (name in mainFixtures()) {
            startFresh()
            files.listFiles()?.forEach { it.deleteRecursively() }
            context.getDatabasePath(AndroidDatabase.FILE_NAME).parentFile?.listFiles()?.forEach { it.delete() }
            val text = fixture(name)
            File(files, "save.json").writeText(text)
            File(files, "care.json").writeText(fixture("care-week.json"))
            val expected = GameStore(MemoryStorage(text), clock).data.timeless()
            assertEquals(expected, widgetStore(context).data.timeless(), "$name read from the database")
            assertEquals(CareStore(MemoryStorage(fixture("care-week.json")), clock, GameStore(MemoryStorage(text), clock)).data, careStore(context).data, "care-week.json with $name")
            processDeath()
            File(files, "save.json").writeText("{}")
            assertEquals(expected, widgetStore(context).data.timeless(), "$name after a process death")
            assertEquals("{}", File(files, "save.json").readText(), "the old file is never written again")
            processDeath()
        }
    }

    @Test fun aCorruptDatabaseIsMovedAsideAndTheNewestBackupPutBack() {
        val task = widgetStore(context).addTask("Kept by the backup")
        val backups = assertNotNull(LiveStore.backups(context))
        assertNotNull(backups.take(LiveStore.get(context)!!.live, System.currentTimeMillis()), "a backup is taken (sealed with the Keystore key)")
        processDeath()
        val db = context.getDatabasePath(AndroidDatabase.FILE_NAME)
        listOf("-wal", "-shm").forEach { File(db.path + it).delete() }
        db.writeBytes(ByteArray(8192) { (it * 31).toByte() })

        val restored = widgetStore(context).data.tasks.singleOrNull { it.id == task.id }
        val aside = db.parentFile!!.listFiles()!!.filter { it.name.startsWith(AndroidDatabase.FILE_NAME + ".corrupt-") }
        assertEquals(1, aside.size, "the damaged file is kept aside, never deleted: ${db.parentFile!!.list()!!.toList()}")
        assertEquals("Kept by the backup", restored?.title)
    }

    private fun damagedWithNoBackupAndOldFiles(): String {
        widgetStore(context).addTask("Done since the cutover")
        processDeath()
        File(files, "backups").deleteRecursively()
        val old = fixture("main-typical-week.json")
        File(files, "save.json").writeText(old)
        val db = context.getDatabasePath(AndroidDatabase.FILE_NAME)
        listOf("-wal", "-shm").forEach { File(db.path + it).delete() }
        db.writeBytes(ByteArray(8192) { (it * 31).toByte() })
        return old
    }

    @Test fun aDamagedDatabaseWithNoBackupAsksBeforeUsingTheOldFiles() {
        val old = damagedWithNoBackupAndOldFiles()
        assertTrue(LiveStore.recoveryPending(context), "the player is asked")
        assertNull(LiveStore.get(context), "nothing opens while the question is open")
        assertFailsWith<IllegalStateException> { saveStorage(context).load() }
        assertEquals(old, File(files, "save.json").readText(), "the old file is untouched")
        val db = context.getDatabasePath(AndroidDatabase.FILE_NAME)
        assertEquals(1, db.parentFile!!.listFiles()!!.count { it.name.startsWith(AndroidDatabase.FILE_NAME + ".corrupt-") }, "the damaged file is kept aside")
        processDeath()
        assertTrue(LiveStore.recoveryPending(context))
    }

    @Test fun usingTheOlderDataOpensTheDatabaseFromTheOldFiles() {
        val old = damagedWithNoBackupAndOldFiles()
        assertTrue(LiveStore.recoveryPending(context))
        LiveStore.resolveRecovery(context, useOlder = true)
        val expected = GameStore(MemoryStorage(old), clock).data.timeless()
        assertEquals(expected, widgetStore(context).data.timeless())
        assertFalse(LiveStore.recoveryPending(context), "answered")
        assertFalse(File(files, "recovery.choice").exists(), "the answer is used once")
        processDeath()
        assertTrue(widgetStore(context).data.tasks.none { it.title == "Done since the cutover" })
    }

    @Test fun startingFreshOpensAnEmptyDatabaseAndKeepsTheOldFilesAndTheDamagedFile() {
        val old = damagedWithNoBackupAndOldFiles()
        assertTrue(LiveStore.recoveryPending(context))
        LiveStore.resolveRecovery(context, useOlder = false)
        val data = widgetStore(context).data
        assertTrue(data.tasks.isEmpty(), "nothing from the old files")
        assertEquals(old, File(files, "save.json").readText(), "the old files are kept")
        val db = context.getDatabasePath(AndroidDatabase.FILE_NAME)
        assertEquals(1, db.parentFile!!.listFiles()!!.count { it.name.startsWith(AndroidDatabase.FILE_NAME + ".corrupt-") })
        processDeath()
        assertFalse(LiveStore.recoveryPending(context))
        assertTrue(widgetStore(context).data.tasks.isEmpty(), "and it stays fresh after a restart")
    }

    @Test fun aDamagedDatabaseWithNoBackupAndNoOldFilesJustStartsAgain() {
        widgetStore(context).addTask("Gone")
        processDeath()
        File(files, "backups").deleteRecursively()
        val db = context.getDatabasePath(AndroidDatabase.FILE_NAME)
        listOf("-wal", "-shm").forEach { File(db.path + it).delete() }
        db.writeBytes(ByteArray(8192) { (it * 31).toByte() })
        assertFalse(LiveStore.recoveryPending(context), "there is nothing to choose between")
        assertNotNull(LiveStore.get(context))
    }

    @Test fun aDatabaseFromANewerBuildIsLeftAloneAndNothingIsWritten() {
        widgetStore(context).addTask("Written by this build")
        processDeath()
        val db = context.getDatabasePath(AndroidDatabase.FILE_NAME)
        android.database.sqlite.SQLiteDatabase.openDatabase(db.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use { it.version = 9999 }
        val before = db.readBytes()

        assertNull(LiveStore.get(context), "the database is not used this run")
        val storage = saveStorage(context)
        assertFailsWith<IllegalStateException> { storage.load() }
        assertFailsWith<IllegalStateException> { storage.save("{}") }
        assertTrue(before.contentEquals(db.readBytes()), "the newer database is untouched")
        assertFalse(File(files, "save.json").exists(), "the old file is not used instead")
    }

    @Test fun healAndMintRowsAreSealedOnTheDisk() {
        val game = widgetStore(context)
        val heal = healStore(context, game)
        heal.logActivity(ActivityType.entries.first(), 30, note = "Zebracrossingnote")
        mintStore(context, game).addAccount("Quokkabank", AccountType.entries.first(), "PHP")
        processDeath()

        val bytes = databaseBytes()
        assertFalse("Zebracrossingnote" in bytes, "a Heal note never reaches the disk in clear")
        assertFalse("Quokkabank" in bytes, "a Mint account name never reaches the disk in clear")
        val game2 = widgetStore(context)
        assertEquals("Zebracrossingnote", healStore(context, game2).data.activities.single().note)
        assertEquals("Quokkabank", mintStore(context, game2).data.accounts.single().name)
    }

    @Test fun withoutTheKeyHealAndMintFailClosedAndTheirRowsStayForTheNextStart() {
        val game = widgetStore(context)
        healStore(context, game).logActivity(ActivityType.entries.first(), 30, note = "Sealedbeforehand")
        mintStore(context, game).addAccount("Sealedbank", AccountType.entries.first(), "PHP")
        processDeath()

        FakeAndroidKeyStore.available = false
        val locked = widgetStore(context)
        val heal = healStore(context, locked)
        val mint = mintStore(context, locked)
        assertTrue(heal.data.activities.isEmpty(), "Heal shows nothing without its key")
        assertTrue(mint.data.accounts.isEmpty(), "Mint shows nothing without its key")
        heal.logActivity(ActivityType.entries.first(), 10, note = "Typedwhilelocked")
        mint.addAccount("Lockedbank", AccountType.entries.first(), "PHP")
        processDeath()
        assertFalse("Typedwhilelocked" in databaseBytes(), "nothing is written in clear while the key is missing")
        assertFalse("Lockedbank" in databaseBytes())

        FakeAndroidKeyStore.available = true
        val back = widgetStore(context)
        assertEquals(listOf("Sealedbeforehand"), healStore(context, back).data.activities.map { it.note }, "the sealed rows are untouched")
        assertEquals(listOf("Sealedbank"), mintStore(context, back).data.accounts.map { it.name })
    }

    @Test fun theDailyBackupIsSealedAndNotTakenWithoutTheKey() {
        widgetStore(context).addTask("Backupsecretword")
        val backups = assertNotNull(LiveStore.backups(context))
        val folder = assertNotNull(backups.take(LiveStore.get(context)!!.live, System.currentTimeMillis()))
        val text = folder.walkTopDown().filter { it.isFile }.joinToString("\n") { String(it.readBytes(), Charsets.ISO_8859_1) }
        assertFalse("Backupsecretword" in text, "every file of the backup is sealed")
        processDeath()

        FakeAndroidKeyStore.available = false
        val count = File(files, "backups").listFiles()!!.size
        widgetStore(context).addTask("No key this run")
        assertNull(LiveStore.backups(context)!!.take(LiveStore.get(context)!!.live, System.currentTimeMillis() + 1), "no backup is written in clear")
        assertEquals(count, File(files, "backups").listFiles()!!.size)
    }
}
