package app.pokeplan.data

import app.cash.sqldelight.db.QueryResult
import app.pokeplan.app.AppData
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.app.mint.MintExport
import app.pokeplan.app.mint.MintStore
import app.pokeplan.app.storage.Storage
import app.pokeplan.mint.AccountType
import app.pokeplan.mint.BudgetPeriod
import app.pokeplan.mint.FeeRule
import app.pokeplan.mint.Frequency
import app.pokeplan.mint.PlanEvery
import app.pokeplan.mint.MintKinds
import app.pokeplan.mint.TxType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** MINT's records in their own tables from the start (REP-014): round trip, only changed rows written, one kind erased alone, backup and activation. */
class MintTablesTest {
    private val start = 1_793_000_000_000L
    private class Tables(private val live: LiveDocuments) : Storage {
        override fun load(): String? = live.read("mint")
        override fun save(text: String) = live.write("mint", text, 1)
        override fun keepAside(text: String) = live.keepAside("mint", text, 1)
    }

    private fun cipher(seed: Int = 1) = AesGcmCipher(javax.crypto.spec.SecretKeySpec(ByteArray(32) { (it * 7 + seed).toByte() }, "AES"))
    private fun obj(text: String) = Json.parseToJsonElement(text) as JsonObject
    private fun live(): DataStore = JvmDatabase.open().also { it.mintCipher = cipher() }.also { it.live.activate(null, null, 1); it.live.moveMintToTables(2) }

    private fun count(s: DataStore, table: String): Long =
        s.driver.executeQuery(null, "SELECT count(*) FROM $table", { c -> QueryResult.Value(if (c.next().value) c.getLong(0) else null) }, 0).value ?: 0L

    private val tables = listOf("mint_account", "mint_category", "mint_transaction", "mint_budget", "mint_bill", "mint_bill_payment", "mint_reconciliation", "mint_review")

    private fun countWrites(s: DataStore) {
        s.driver.execute(null, "CREATE TABLE write_log(t TEXT NOT NULL)", 0)
        for (t in tables + "mint_state") {
            s.driver.execute(null, "CREATE TRIGGER log_ins_$t AFTER INSERT ON $t BEGIN INSERT INTO write_log VALUES ('$t'); END", 0)
            s.driver.execute(null, "CREATE TRIGGER log_del_$t AFTER DELETE ON $t BEGIN INSERT INTO write_log VALUES ('$t'); END", 0)
        }
    }

    private fun writes(s: DataStore) = count(s, "write_log")

    private val clock = object : Clock { var millis = start; override fun nowMillis() = millis }
    private fun game() = GameStore(MemoryStorage(), clock, TimeZone.UTC, Random(7)).also { it.start("Sophia", 25) }
    private fun mintOn(store: DataStore, game: GameStore) = MintStore(Tables(store.live), clock, game, TimeZone.UTC, rng = Random(3))

    private fun MintStore.fill(): String {
        val a = assertNotNull(addAccount("Savings", AccountType.BANK, "PHP", 100_000).record).id
        val b = assertNotNull(addAccount("Wallet", AccountType.EWALLET, "PHP").record).id
        record(TxType.EXPENSE, 2_500, a, categoryId = "cat-food", payee = "Market")
        record(TxType.TRANSFER, 10_000, a, toAccountId = b)
        addBudget(10_000, "PHP", BudgetPeriod.MONTHLY, "cat-food")
        addBill("Internet", "PHP", Frequency.MONTHLY, LocalDate(2026, 11, 20), 150_000)
        return a
    }

    @Test
    fun aFreshStartActivatesWithNothingToMoveAndSurvivesRestarts() {
        val s = JvmDatabase.open().also { it.mintCipher = cipher() }.also { it.live.activate(null, null, 1) }
        assertFalse(s.mintTables.isActive())
        s.live.moveMintToTables(5)
        assertTrue(s.mintTables.isActive())
        assertNull(s.live.read("mint"))
        assertFalse("mint" in s.live.names())
        s.live.moveMintToTables(9)
        assertEquals("5", s.db.dataQueries.meta(MintTables.META_MINT_TABLES_AT).executeAsOne(), "the first activation time is kept")
    }

    @Test
    fun aDocumentRestoredBeforeActivationIsImportedReadBackEqualAndKeptAside() {
        val s = JvmDatabase.open().also { it.mintCipher = cipher() }.also { it.live.activate(null, null, 1) }
        val doc = """{"accounts":[{"id":"a1","name":"Cash","type":"CASH","currency":"PHP","openingDay":"2026-11-07"}],"settings":{"hideValues":true},"future":{"x":1}}"""
        s.live.write("mint", doc, 2)
        s.live.moveMintToTables(3)
        assertEquals(obj(doc), obj(s.live.read("mint")!!))
        assertTrue(RowCipher.isSealed(s.live.read("aside:mint:tables:3")!!), "the kept-aside copy is sealed")
        assertEquals(doc, cipher().open(s.live.read("aside:mint:tables:3")!!))
        assertEquals(1, count(s, "mint_account"))
    }

    @Test
    fun anActivationThatCannotFinishLeavesTheDatabaseAsItWas() {
        val s = JvmDatabase.open().also { it.mintCipher = cipher() }.also { it.live.activate(null, null, 1) }
        s.live.write("mint", """{"accounts":[{"id":"a1","name":"Cash","type":"CASH","currency":"PHP","openingDay":"2026-11-07"}]}""", 2)
        s.driver.execute(null, "CREATE TRIGGER no_marker BEFORE INSERT ON app_meta WHEN NEW.key = 'mint_store' BEGIN SELECT RAISE(ABORT, 'no'); END", 0)
        assertFailsWith<Exception> { s.live.moveMintToTables(3) }
        assertFalse(s.mintTables.isActive())
        assertEquals(0, count(s, "mint_account"), "the rows written before the failure were rolled back")
        assertTrue(s.live.read("mint")!!.contains("a1"))
    }

    @Test
    fun mintRunsOnTablesAndEverythingComesBackAfterReopeningTheFileExactly() {
        val f = File.createTempFile("mint", ".db").also { it.delete(); it.deleteOnExit() }
        val g = game()
        val before: app.pokeplan.mint.MintData
        JvmDatabase.open(f.path).also { it.mintCipher = cipher() }.let { s ->
            s.live.activate(null, null, 1); s.live.moveMintToTables(2)
            val mint = mintOn(s, g)
            val a = mint.fill()
            mint.reconcile(a, 80_000, acceptAdjustment = true)
            mint.finishReview()
            mint.setHideValues(true)
            before = mint.data
            assertEquals(2L, count(s, "mint_account")); assertEquals(3L, count(s, "mint_transaction")); assertEquals(1L, count(s, "mint_reconciliation"))
            s.close()
        }
        val again = JvmDatabase.open(f.path).also { it.mintCipher = cipher() }
        assertTrue(again.integrityIssues().isEmpty())
        assertTrue(again.mintTables.isActive())
        assertEquals(before, mintOn(again, g).data, "everything MINT kept is back, field for field, every amount an exact integer")
    }

    @Test
    fun aSaveWritesOnlyTheRowsThatChanged() {
        val s = live()
        val mint = mintOn(s, game())
        val a = mint.fill()
        countWrites(s)
        mint.record(TxType.EXPENSE, 100, a) // one new row in one table
        assertEquals(1L, writes(s))
        mint.setHideValues(true) // the settings row
        assertEquals(2L, writes(s))
        mint.updateTransaction(mint.data.transactions.first().copy(note = "x"))
        assertEquals(3L, writes(s), "only the changed transaction row was replaced")
    }

    @Test
    fun erasingOneKindClearsItsFamilyAndNothingElse() {
        val s = live()
        val g = game()
        val mint = mintOn(s, g)
        mint.fill()
        s.live.write("save", GameStore.json.encodeToString(AppData.serializer(), g.data), 3)
        val saveBefore = s.live.read("save")
        s.mintTables.erase("budgets")
        assertEquals(0, count(s, "mint_budget"))
        assertEquals(2, count(s, "mint_account")); assertEquals(2, count(s, "mint_transaction")); assertEquals(1, count(s, "mint_bill"))
        assertEquals(saveBefore, s.live.read("save"), "the game's save is untouched")
        val fresh = mintOn(s, g)
        fresh.erase(MintKinds.BILLS)
        assertEquals(0, count(s, "mint_bill"))
        assertEquals("[]", s.mintTables.readKind("budgets"))
        assertTrue(s.mintTables.readKind("transactions").contains("Market"))
        assertFailsWith<IllegalStateException> { s.mintTables.erase("nonsense") }
        s.mintTables.clear()
        assertEquals(0L, tables.sumOf { count(s, it) } + count(s, "mint_state"))
        assertEquals(saveBefore, s.live.read("save"))
        assertNull(s.live.read("mint"))
    }

    @Test
    fun noAmountOrNameOfMintEverReachesTheSharedTables() {
        val s = live()
        val g = game()
        s.live.normalizer = GameStore::normalizeDocument
        val mint = mintOn(s, g)
        val a = mint.fill()
        mint.reconcile(a, 80_000, acceptAdjustment = true)
        mint.finishReview()
        s.live.write("save", GameStore.json.encodeToString(AppData.serializer(), g.data), 3)
        val shared = s.live.read("save")!!
        for (secret in listOf("Savings", "Wallet", "Market", "Internet", "80000", "150000", "cat-food")) assertFalse(secret in shared, "the save holds $secret")
        for (table in listOf("reward_event", "reward_ledger_entry", "owned_pokemon", "app_meta")) {
            val text = s.driver.executeQuery(null, "SELECT * FROM $table", { c ->
                val out = StringBuilder()
                while (c.next().value) { var i = 0; while (true) { val v = runCatching { c.getString(i) }.getOrNull() ?: break; out.append(v).append('|'); i++ } }
                QueryResult.Value(out.toString())
            }, 0).value.orEmpty()
            for (secret in listOf("Savings", "Market", "Internet", "80000")) assertFalse(secret in text, "$table holds $secret")
        }
    }

    @Test
    fun theBackupSnapshotCarriesMintAndARestoreOfAnOlderBackupLeavesItAlone() {
        val s = live()
        val g = game()
        val mint = mintOn(s, g)
        s.live.write("save", GameStore.json.encodeToString(AppData.serializer(), g.data), 3)
        mint.addAccount("Kept", AccountType.CASH, "PHP", 5_000)
        val snap = s.live.snapshot()
        assertTrue("mint" in snap && RowCipher.isSealed(snap.getValue("mint")) && "Kept" !in snap.getValue("mint"), "the snapshot holds Mint sealed")
        assertTrue("mint" in s.live.names())
        s.live.replaceAll(mapOf("save" to snap.getValue("save")), 9)
        assertEquals(1, mintOn(s, g).data.accounts.size, "a backup from before Mint existed must not wipe Mint")
        mint.addAccount("Later", AccountType.CASH, "PHP")
        s.live.replaceAll(snap, 11)
        assertEquals(1, mintOn(s, g).data.accounts.size)
        val aside = s.live.read("aside:mint:11")!!
        assertTrue(RowCipher.isSealed(aside) && "Later" !in aside && cipher().open(aside)!!.contains("Later"), "what a restore replaced is kept aside sealed")
    }

    @Test
    fun theBundleKnowsMint() {
        val docs = mapOf("save" to "{}", "mint" to """{"accounts":[]}""")
        val staged = BackupBundle.stage(BackupBundle.encode(docs, DataStore.SCHEMA_VERSION, 7), DataStore.SCHEMA_VERSION)
        assertTrue(staged is BackupBundle.Staged.Ready && staged.documents.keys == setOf("save", "mint"))
    }

    @Test
    fun theExportOfOneKindIsTheRowsOfThatKindAndNothingElse() {
        val s = live()
        val mint = mintOn(s, game())
        mint.fill()
        val exported = obj(MintExport.json(mint.data, setOf(MintKinds.ACCOUNTS)))
        assertEquals(setOf("format", "unit", "accounts", "plans"), exported.keys)
        assertEquals(2, (exported["accounts"] as JsonArray).size)
        // The accounts kind carries the installment plans that belong to them (MINT.md 5.6A).
        assertEquals(obj(s.mintTables.readKind("accounts").let { """{"a":$it}""" })["a"], exported["accounts"], "the table family and the export agree")
    }

    @Test
    fun mintsPartyAndLeadSurviveTheSharedPokemonTablesAndStayOnePlaceEach() {
        val g = game()
        g.completeTutorial(app.pokeplan.app.Tutorials.MINT_TALLY)
        val second = g.data.player.buddyId!!
        assertTrue(g.setInParty(second, true, app.pokeplan.core.pokemon.PartyIds.MINT))
        assertTrue(g.setMintLead(second))
        val doc = GameStore.json.encodeToString(AppData.serializer(), g.data)
        val s = JvmDatabase.open().also { it.mintCipher = cipher() }
        s.live.normalizer = GameStore::normalizeDocument
        s.live.activate(doc, null, 5)
        s.live.movePlanToTables(6)
        s.live.movePokemonToTables(7)
        val back = GameStore.json.decodeFromString(AppData.serializer(), s.live.read("save")!!)
        assertEquals(g.data.player.partyOf("mint").map { it.id }.toSet(), back.player.partyOf("mint").map { it.id }.toSet())
        assertEquals(second, back.player.mintLead?.id, "the chosen lead leads, whatever order the members joined in")
        assertEquals(2, back.player.partyOf("mint").size)
    }

    private fun rawText(s: DataStore): String = (tables + "mint_state").joinToString("\n") { t ->
        val columns = if (t == "mint_state") listOf("key", "json") else listOf("key", "position", "day", "json")
        s.driver.executeQuery(null, "SELECT ${columns.joinToString()} FROM $t", { c ->
            val out = StringBuilder()
            while (c.next().value) { for (i in columns.indices) out.append(runCatching { c.getString(i) }.getOrNull() ?: runCatching { c.getLong(i).toString() }.getOrNull()).append('|'); out.append('\n') }
            QueryResult.Value(out.toString())
        }, 0).value.orEmpty()
    }

    @Test
    fun theStoredRowsHoldNoFigureNameOrDateInClear() {
        val s = live()
        val mint = mintOn(s, game())
        mint.fill()
        mint.setHideValues(true)
        val raw = rawText(s)
        assertTrue(raw.contains("m1."), "rows are sealed")
        for (secret in listOf("Savings", "Wallet", "Market", "Internet", "100000", "2500", "PHP", "2026-", "hideValues", "Salary")) assertFalse(secret in raw, "a row holds $secret in clear")
        assertEquals(0L, s.driver.executeQuery(null, "SELECT count(*) FROM mint_transaction WHERE day IS NOT NULL", { c -> QueryResult.Value(if (c.next().value) c.getLong(0) else 0L) }, 0).value)
    }

    @Test
    fun aBackupSnapshotAndTheAsidesHoldMintOnlySealed() {
        val s = live()
        val g = game()
        val mint = mintOn(s, g)
        mint.fill()
        s.live.write("save", GameStore.json.encodeToString(AppData.serializer(), g.data), 3)
        val snap = s.live.snapshot()
        assertTrue(RowCipher.isSealed(snap.getValue("mint")), "the snapshot's Mint is sealed")
        for (secret in listOf("Savings", "Market", "100000")) assertFalse(secret in snap.getValue("mint"))
        val staged = BackupBundle.stage(BackupBundle.encode(snap, DataStore.SCHEMA_VERSION, 7), DataStore.SCHEMA_VERSION)
        assertTrue(staged is BackupBundle.Staged.Ready && "mint" in staged.documents.keys, "a bundle with sealed Mint passes its checks")
        // Restoring it on the same phone brings Mint back, and what it replaced is kept aside sealed.
        mint.addAccount("Later", AccountType.CASH, "PHP")
        s.live.replaceAll(snap, 11)
        assertEquals(2, mintOn(s, g).data.accounts.size)
        val aside = s.live.read("aside:mint:11")!!
        assertTrue(RowCipher.isSealed(aside) && "Later" !in aside)
        s.live.keepAside("mint", """{"accounts":[]}""", 5)
        assertTrue(RowCipher.isSealed(s.live.read("aside:mint:5")!!))
    }

    @Test
    fun aDatabaseOpenedWithoutTheKeyIsLockedNotWipedAndRestoreOfAForeignBackupKeepsItSealed() {
        val f = File.createTempFile("mint", ".db").also { it.delete(); it.deleteOnExit() }
        val g = game()
        val snap: Map<String, String>
        JvmDatabase.open(f.path).also { it.mintCipher = cipher(1) }.let { s ->
            s.live.activate(null, null, 1); s.live.moveMintToTables(2)
            mintOn(s, g).fill()
            snap = s.live.snapshot()
            s.close()
        }
        // The same file on a phone with another key (a database restored from the cloud): Mint reads as empty, nothing is destroyed.
        val other = JvmDatabase.open(f.path).also { it.mintCipher = cipher(2) }
        assertTrue(other.mintTables.locked())
        assertNull(other.live.read("mint"))
        assertFalse("mint" in other.live.names())
        val fresh = mintOn(other, g)
        assertTrue(fresh.data.accounts.isEmpty())
        fresh.addAccount("New", AccountType.CASH, "PHP", 100) // the first write sets the sealed rows aside first
        assertEquals(1, other.live.names().count { it.startsWith("aside:mint:locked:") })
        assertEquals(1, mintOn(other, g).data.accounts.size)
        // A backup sealed with the first key does not open here either: it is kept aside and the new Mint is untouched.
        other.live.replaceAll(snap, 21)
        assertEquals(1, mintOn(other, g).data.accounts.size)
        assertTrue(other.live.read("aside:mint:locked:21") != null)
        // The right key opens everything again from that backup.
        val home = JvmDatabase.open().also { it.mintCipher = cipher(1) }
        home.live.activate(null, null, 1); home.live.moveMintToTables(2)
        home.live.replaceAll(snap, 5)
        assertEquals(2, mintOn(home, g).data.accounts.size)
    }

    @Test
    fun mintsTablesRefuseToStartWithoutAKey() {
        val s = JvmDatabase.open().also { it.live.activate(null, null, 1) }
        assertFailsWith<IllegalStateException> { s.live.moveMintToTables(2) }
        assertFalse(s.mintTables.isActive())
    }

    @Test
    fun installmentPlansRideInTheSealedStateRowAndComeBackExactlyWithoutANewTable() {
        val f = File.createTempFile("mint", ".db").also { it.delete(); it.deleteOnExit() }
        val g = game()
        val before: app.pokeplan.mint.MintData
        JvmDatabase.open(f.path).also { it.mintCipher = cipher() }.let { s ->
            s.live.activate(null, null, 1); s.live.moveMintToTables(2)
            val mint = mintOn(s, g)
            val wallet = assertNotNull(mint.addAccount("GCash", AccountType.EWALLET, "PHP", 100_000).record).id
            val later = assertNotNull(mint.addAccount("SPayLater", AccountType.PAY_LATER, "PHP", limitMinor = 300_000, statementDay = 20, dueDay = 5).record).id
            val plan = assertNotNull(mint.addPlanPurchase(later, "Headphones", 120_000, null, 3, PlanEvery.MONTH, LocalDate(2026, 12, 1), FeeRule.RatePerPeriod(200)).record)
            assertNotNull(mint.payPlan(plan.id, 42_400, wallet).record)
            before = mint.data
            assertEquals(1, before.plans.size)
            s.close()
        }
        val again = JvmDatabase.open(f.path).also { it.mintCipher = cipher() }
        assertTrue(again.integrityIssues().isEmpty())
        assertEquals(before, mintOn(again, g).data, "the plan, its schedule and its linked records are back field for field")
        // The plan list is in the sealed state row: nothing readable in the file.
        assertFalse(f.readBytes().toString(Charsets.ISO_8859_1).contains("Headphones"), "the plan is sealed")
    }
}
