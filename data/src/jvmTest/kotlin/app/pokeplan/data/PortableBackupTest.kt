package app.pokeplan.data

import app.pokeplan.app.AppData
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.app.mint.MintStore
import app.pokeplan.app.storage.Storage
import app.pokeplan.mint.AccountType
import kotlinx.datetime.TimeZone
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The passphrase-sealed backup of Mint (MINT.md section 22): a backup file that opens on any phone given the passphrase, and only then. Synthetic data only. */
class PortableBackupTest {
    // A cheap count keeps the test fast; the real one is Pbkdf2Sealer.ITERATIONS and is stored in each text.
    private val sealer = Pbkdf2Sealer(iterations = 1_000)
    private val phrase = "correct horse battery"
    private fun key(seed: Int) = AesGcmCipher(SecretKeySpec(ByteArray(32) { (it * 7 + seed).toByte() }, "AES"))
    private val clock = object : Clock { override fun nowMillis() = 1_793_000_000_000L }

    private class Tables(private val live: LiveDocuments) : Storage {
        override fun load(): String? = live.read("mint")
        override fun save(text: String) = live.write("mint", text, 1)
        override fun keepAside(text: String) = live.keepAside("mint", text, 1)
    }

    private fun phone(seed: Int): DataStore = JvmDatabase.open().also { it.mintCipher = key(seed); it.live.activate(null, null, 1); it.live.moveMintToTables(2) }
    private fun game() = GameStore(MemoryStorage(), clock, TimeZone.UTC, Random(7)).also { it.start("Sophia", 25) }
    private fun mintOn(s: DataStore, g: GameStore) = MintStore(Tables(s.live), clock, g, TimeZone.UTC, rng = Random(3))

    @Test
    fun aSealedTextOpensOnlyWithItsPassphraseAndHoldsNothingInClear() {
        val sealed = sealer.seal("""{"accounts":[{"name":"Savings"}]}""", phrase)
        assertTrue(PassphraseSealer.isSealed(sealed) && !RowCipher.isSealed(sealed))
        assertFalse("Savings" in sealed)
        assertEquals("""{"accounts":[{"name":"Savings"}]}""", sealer.open(sealed, phrase))
        assertNull(sealer.open(sealed, "correct horse batterY"), "a wrong passphrase opens nothing")
        assertNull(sealer.open(sealed.dropLast(3) + "AAA", phrase), "a damaged text opens nothing")
        assertNull(sealer.open("not sealed", phrase))
        assertNotEquals(sealed, sealer.seal("""{"accounts":[{"name":"Savings"}]}""", phrase), "a fresh salt and IV every time")
    }

    private fun assertNotEquals(a: String, b: String, message: String) = assertTrue(a != b, message)

    @Test
    fun theSameWordsTypedAnotherWayOpenIt() {
        val sealed = sealer.seal("x", "café au lait 2026")
        assertEquals("x", sealer.open(sealed, "café au lait 2026"), "composed and decomposed accents are the same passphrase")
    }

    @Test
    fun aShortPassphraseIsRefusedAndAnAbsurdCountInAFileIsNotRun() {
        assertNotNull(PassphraseSealer.refusal("short"))
        assertNull(PassphraseSealer.refusal(phrase))
        val sealed = sealer.seal("x", phrase).split('.').toMutableList()
        sealed[2] = "2000000000"
        assertNull(sealer.open(sealed.joinToString("."), phrase), "a file cannot make the phone work for minutes")
    }

    @Test
    fun aPortableBackupOfMintOpensOnAnotherPhoneAndOnlyWithThePassphrase() {
        val g = game()
        val home = phone(1)
        mintOn(home, g).apply { addAccount("Savings", AccountType.BANK, "PHP", 100_000); addAccount("Wallet", AccountType.CASH, "PHP", 2_500) }
        home.live.write("save", GameStore.json.encodeToString(AppData.serializer(), g.data), 3)
        val snap = home.live.snapshot()
        val portable = assertNotNull(BackupBundle.portable(snap, mapOf("mint" to key(1)), phrase, sealer))
        assertTrue(PassphraseSealer.isSealed(portable.getValue("mint")))
        val file = BackupBundle.encode(portable, DataStore.SCHEMA_VERSION, 9)
        for (secret in listOf("Savings", "Wallet", "100000")) assertFalse(secret in file, "the file holds $secret in clear")
        for (secret in listOf("Savings", "Wallet", "100000", "PHP")) assertFalse(secret in portable.getValue("mint"), "Mint's part holds $secret in clear")

        val ready = assertIs<BackupBundle.Staged.Ready>(BackupBundle.stage(file, DataStore.SCHEMA_VERSION))
        assertTrue(ready.needsPassphrase)
        assertNull(BackupBundle.unlock(ready, null, sealer), "no passphrase, nothing opens")
        assertNull(BackupBundle.unlock(ready, "not the passphrase", sealer))

        // A phone with a different Keystore key: the device-sealed form would be locked there; the portable one opens.
        val other = phone(2)
        val docs = assertNotNull(BackupBundle.unlock(ready, phrase, sealer))
        other.live.replaceAll(docs, 11)
        assertEquals(setOf("Savings", "Wallet"), mintOn(other, g).data.accounts.map { it.name }.toSet())
        assertTrue(other.live.names().none { it.startsWith("aside:mint:locked:") })
        assertTrue(RowCipher.isSealed(other.live.snapshot().getValue("mint")), "after the restore Mint is sealed to the new phone again")
    }

    @Test
    fun aBackupFromAnotherPhonesKeyCannotBeMadePortableHere() {
        val home = phone(1)
        mintOn(home, game()).addAccount("Savings", AccountType.BANK, "PHP", 1)
        val snap = home.live.snapshot()
        assertNull(BackupBundle.portable(snap, mapOf("mint" to key(2)), phrase, sealer), "this phone cannot open that Mint, so it cannot honestly re-seal it")
        val noMint = mapOf("save" to "{}")
        assertEquals(noMint, BackupBundle.portable(noMint, mapOf("mint" to key(2)), phrase, sealer), "a backup with no Mint needs no passphrase")
    }

    @Test
    fun aPlainBackupNeedsNoPassphrase() {
        val ready = assertIs<BackupBundle.Staged.Ready>(BackupBundle.stage(BackupBundle.encode(mapOf("save" to "{}", "mint" to "{}"), DataStore.SCHEMA_VERSION, 1), DataStore.SCHEMA_VERSION))
        assertFalse(ready.needsPassphrase)
        assertEquals(ready.documents, BackupBundle.unlock(ready, null, sealer))
    }
}
