package app.pokeplan.data

import app.pokeplan.app.GameStore
import app.pokeplan.app.AppData
import app.pokeplan.app.mint.MintStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.app.storage.Storage
import app.pokeplan.mint.AccountType
import kotlinx.datetime.TimeZone
import java.io.File
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OffPhoneBackupTest {
    private val sealer = Pbkdf2Sealer(iterations = 1_000)
    private val phrase = "correct horse battery"
    private val clock = object : Clock { override fun nowMillis() = 1_793_000_000_000L }
    private fun key(seed: Int) = AesGcmCipher(SecretKeySpec(ByteArray(32) { (it * 7 + seed).toByte() }, "AES"))

    private class MintTables(private val live: LiveDocuments) : Storage {
        override fun load(): String? = live.read("mint")
        override fun save(text: String) = live.write("mint", text, 1)
        override fun keepAside(text: String) = live.keepAside("mint", text, 1)
    }

    private fun phone(seed: Int): DataStore = JvmDatabase.open().also { it.mintCipher = key(seed); it.live.activate(null, null, 1); it.live.moveMintToTables(2) }
    private fun game() = GameStore(MemoryStorage(), clock, TimeZone.UTC, Random(7)).also { it.start("Sophia", 25) }
    private fun withMint(): Pair<DataStore, GameStore> {
        val g = game()
        val s = phone(1)
        MintStore(MintTables(s.live), clock, g, TimeZone.UTC, rng = Random(3)).addAccount("Savings", AccountType.BANK, "PHP", 100_000)
        s.live.write("save", GameStore.json.encodeToString(AppData.serializer(), g.data), 3)
        return s to g
    }

    private class DiskFolder(val dir: File) : BackupFolder {
        override fun names() = dir.list()?.toList().orEmpty()
        override fun write(name: String, text: String): Boolean { File(dir, name).writeText(text); return true }
        override fun read(name: String) = File(dir, name).takeIf { it.exists() }?.readText()
        override fun delete(name: String) { File(dir, name).delete() }
    }

    private fun folder() = DiskFolder(kotlin.io.path.createTempDirectory("offphone").toFile())
    private val day = 24 * 60 * 60 * 1000L
    private val t0 = 1_793_000_000_000L

    @Test fun aCopyHoldsNoMintInClearAndRestoresOnAnotherPhoneWithThePassphrase() {
        val (home, _) = withMint()
        val f = folder()
        val result = OffPhoneBackup(f.let { it }, sealer = sealer).run(home.live, mapOf("mint" to key(1)), phrase, DataStore.SCHEMA_VERSION, t0)
        assertEquals(OffPhoneBackup.Result.Done(t0), result)
        val text = assertNotNull(f.read(OffPhoneBackup.nameOf(t0)))
        for (secret in listOf("Savings", "100000")) assertFalse(secret in text, "the copy holds $secret in clear")
        val ready = assertIs<BackupBundle.Staged.Ready>(BackupBundle.stage(text, DataStore.SCHEMA_VERSION))
        assertTrue(ready.needsPassphrase)
        val other = phone(2)
        other.live.replaceAll(assertNotNull(BackupBundle.unlock(ready, phrase, sealer)), t0)
        assertEquals(setOf("Savings"), MintStore(MintTables(other.live), clock, game(), TimeZone.UTC, rng = Random(3)).data.accounts.map { it.name }.toSet())
        assertEquals(null, BackupBundle.unlock(ready, "not the passphrase", sealer))
    }

    @Test fun theNewestSevenStayAndOnlyTheAppsOwnFilesAreEverTouched() {
        val (home, _) = withMint()
        val f = folder()
        File(f.dir, "holiday.jpg").writeText("keep me")
        File(f.dir, "pokelife-backup-notes.json").writeText("keep me too")
        val b = OffPhoneBackup(f, sealer = sealer)
        for (d in 0 until 10) assertIs<OffPhoneBackup.Result.Done>(b.run(home.live, mapOf("mint" to key(1)), phrase, DataStore.SCHEMA_VERSION, t0 + d * day))
        assertEquals((3..9).map { t0 + it * day }.reversed(), b.copies().map { it.millis })
        assertEquals("keep me", File(f.dir, "holiday.jpg").readText())
        assertEquals("keep me too", File(f.dir, "pokelife-backup-notes.json").readText())
    }

    @Test fun aFailedCopyLeavesEarlierOnesUntouched() {
        val (home, _) = withMint()
        val f = folder()
        val b = OffPhoneBackup(f, sealer = sealer)
        b.run(home.live, mapOf("mint" to key(1)), phrase, DataStore.SCHEMA_VERSION, t0)
        val before = f.read(OffPhoneBackup.nameOf(t0))
        val refusing = object : BackupFolder by f { override fun write(name: String, text: String) = false }
        assertIs<OffPhoneBackup.Result.Failed>(OffPhoneBackup(refusing, sealer = sealer).run(home.live, mapOf("mint" to key(1)), phrase, DataStore.SCHEMA_VERSION, t0 + day))
        val cutShort = object : BackupFolder by f { override fun write(name: String, text: String) = f.write(name, text.take(text.length / 2)) }
        assertIs<OffPhoneBackup.Result.Failed>(OffPhoneBackup(cutShort, sealer = sealer).run(home.live, mapOf("mint" to key(1)), phrase, DataStore.SCHEMA_VERSION, t0 + day))
        val throwing = object : BackupFolder by f { override fun write(name: String, text: String): Boolean = error("provider gone") }
        assertIs<OffPhoneBackup.Result.Failed>(OffPhoneBackup(throwing, sealer = sealer).run(home.live, mapOf("mint" to key(1)), phrase, DataStore.SCHEMA_VERSION, t0 + day))
        assertEquals(listOf(OffPhoneBackup.nameOf(t0)), f.names(), "the bad files were removed, the good one is there")
        assertEquals(before, f.read(OffPhoneBackup.nameOf(t0)))
    }

    @Test fun aCopyIsNotMadeWhenMintCannotBeOpenedHereOrThePassphraseIsShort() {
        val (home, _) = withMint()
        val f = folder()
        val b = OffPhoneBackup(f, sealer = sealer)
        assertIs<OffPhoneBackup.Result.Failed>(b.run(home.live, mapOf("mint" to key(2)), phrase, DataStore.SCHEMA_VERSION, t0), "another phone's key")
        assertIs<OffPhoneBackup.Result.Failed>(b.run(home.live, mapOf("mint" to key(1)), "short", DataStore.SCHEMA_VERSION, t0))
        assertTrue(f.names().isEmpty())
    }

    @Test fun aCopyIsDueWhenTheNewestIsOldOrThereIsNone() {
        val (home, _) = withMint()
        val b = OffPhoneBackup(folder(), sealer = sealer)
        assertTrue(b.due(t0))
        b.run(home.live, mapOf("mint" to key(1)), phrase, DataStore.SCHEMA_VERSION, t0)
        assertFalse(b.due(t0 + 10 * 60 * 60 * 1000L))
        assertTrue(b.due(t0 + 21 * 60 * 60 * 1000L))
        assertEquals(t0, b.lastMillis())
    }

    @Test fun namesRoundTripAndForeignNamesAreNotCopies() {
        assertEquals(OffPhoneBackup.Copy(t0, OffPhoneBackup.nameOf(t0)), OffPhoneBackup.parse(OffPhoneBackup.nameOf(t0)))
        assertTrue(OffPhoneBackup.parse("pokelife-backup-2026-11-15-abc.json") == null)
        assertTrue(OffPhoneBackup.parse("pokelife-backup-2001-01-01-$t0.json") == null, "the date must match the time")
        assertTrue(OffPhoneBackup.parse("other.json") == null)
    }
}
