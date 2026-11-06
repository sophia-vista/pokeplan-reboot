package app.pokeplan.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.nio.file.Files
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataStoreTest {
    private fun pokemon(id: String, species: Int = 25) = OwnedPokemonRecord(
        id = id, speciesId = species, formIdentifier = "pikachu", nickname = id, nature = "HARDY", ability = "Static", gender = null,
        ivs = Spread(1, 2, 3, 4, 5, 6), evs = Spread.ZERO, level = 5, candyProgress = 0, totalCandies = 0, friendship = 70,
        isShiny = false, heldItem = null, teraType = null, cosmeticForm = null, personality = 0, caughtAtMillis = 1L,
    )

    @Test
    fun aConnectionWithoutForeignKeysIsRefused() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { setProperty("foreign_keys", "false") })
        val e = assertFailsWith<IllegalStateException> { DataStore.open(driver) }
        assertTrue("foreign keys" in e.message!!)
    }

    @Test
    fun theOwnershipRulesHoldBecauseForeignKeysAreOn() {
        val store = JvmDatabase.open()
        store.pokemon.add(pokemon("a"))
        assertFailsWith<Exception>("an owned id that does not exist") { store.pokemon.setParty(ModuleIds.PLAN, listOf("ghost")) }
        assertFailsWith<IllegalArgumentException>("a seventh member") { store.pokemon.setParty(ModuleIds.PLAN, List(7) { "a" }) }
        assertFailsWith<IllegalArgumentException>("the same Pokémon twice") { store.pokemon.setParty(ModuleIds.PLAN, listOf("a", "a")) }
        assertFailsWith<IllegalArgumentException>("a module with no party") { store.pokemon.setParty("WORLD", listOf("a")) }
        store.pokemon.setParty(ModuleIds.PLAN, listOf("a"))
        store.pokemon.setParty(ModuleIds.CARE, listOf("a")) // the same Pokémon may be in several modules' parties
        store.pokemon.setBuddy("a")
        store.pokemon.setGuardians(listOf("a"))
        store.pokemon.release("a")
        assertEquals(emptyList(), store.pokemon.party(ModuleIds.PLAN))
        assertEquals(emptyList(), store.pokemon.party(ModuleIds.CARE))
        assertNull(store.pokemon.buddy())
        assertEquals(emptyList(), store.pokemon.guardians())
    }

    @Test
    fun aFullPartyKeepsItsOrderAndTheLeadIsSlotOne() {
        val store = JvmDatabase.open()
        val ids = List(6) { "p$it" }
        ids.forEach { store.pokemon.add(pokemon(it)) }
        store.pokemon.setParty(ModuleIds.HEAL, ids.reversed())
        assertEquals(ids.reversed(), store.pokemon.party(ModuleIds.HEAL))
        assertEquals("p5", store.pokemon.lead(ModuleIds.HEAL))
    }

    @Test
    fun settlingTheSameEventTwicePaysOnce() {
        val store = JvmDatabase.open()
        val event = RewardEventInput("plan:t1:task-done", "PLAN", "t1", "task-done", "v1", 10, poke = 30, items = mapOf("rare-candy" to 2))
        assertTrue(store.economy.settle(event) is Settlement.Applied)
        val again = store.economy.settle(event)
        assertTrue(again is Settlement.AlreadySettled)
        assertEquals(2, again.entries.size)
        assertEquals(30L, store.economy.poke())
        assertEquals(mapOf("rare-candy" to 2L), store.economy.bag())
        assertEquals(emptyList(), store.economy.reconcile())
    }

    @Test
    fun aSpendThatCannotBeCoveredChangesNothing() {
        val store = JvmDatabase.open()
        store.economy.settle(RewardEventInput("e1", "PLAN", "t", "task-done", "v1", 1, poke = 10, items = mapOf("berry" to 1)))
        assertFailsWith<InsufficientFundsException> {
            store.economy.settle(RewardEventInput("e2", "WORLD", "shop", "buy", "v1", 2, poke = -5, items = mapOf("berry" to -2)))
        }
        assertEquals(10L, store.economy.poke())
        assertEquals(mapOf("berry" to 1L), store.economy.bag())
        assertEquals(false, store.economy.hasEvent("e2"))
    }

    @Test
    fun aFailureInTheMiddleOfATransactionLeavesNothingBehind() {
        val store = JvmDatabase.open()
        assertFailsWith<IllegalStateException> {
            store.transaction {
                store.pokemon.add(pokemon("a"))
                store.economy.settle(RewardEventInput("e1", "PLAN", "t", "task-done", "v1", 1, poke = 10))
                error("interrupted")
            }
        }
        assertEquals(0L, store.pokemon.count())
        assertEquals(0L, store.economy.poke())
        assertEquals(false, store.economy.hasEvent("e1"))
    }

    @Test
    fun aReversalIsACompensatingEntryAndHappensOnce() {
        val store = JvmDatabase.open()
        store.economy.settle(RewardEventInput("e1", "PLAN", "t", "task-done", "v1", 1, poke = 40, items = mapOf("berry" to 3)))
        store.economy.reverse("r1", "e1", "undone", 2)
        assertEquals(0L, store.economy.poke())
        assertEquals(emptyMap(), store.economy.bag())
        assertEquals(listOf(-40L, -3L), store.economy.ledgerOf("r1").map { it.delta }.sortedBy { it })
        assertEquals(emptyList(), store.economy.reconcile())
        assertFailsWith<IllegalStateException> { store.economy.reverse("r2", "e1", "again", 3) }
    }

    @Test
    fun theLedgerCannotBeEditedOrEmptiedEvenByRawSql() {
        val store = JvmDatabase.open()
        store.economy.settle(RewardEventInput("e1", "PLAN", "t", "task-done", "v1", 1, poke = 40))
        assertFailsWith<Exception> { store.driver.execute(null, "UPDATE reward_ledger_entry SET delta = 99", 0) }
        assertFailsWith<Exception> { store.driver.execute(null, "DELETE FROM reward_ledger_entry", 0) }
        assertEquals(40L, store.economy.ledgerOf("e1").single().delta)
    }

    @Test
    fun aNewerSchemaIsLeftAloneAndAnOlderOneIsCreatedFresh() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { setProperty("foreign_keys", "true") })
        driver.execute(null, "PRAGMA user_version = 99", 0)
        assertFailsWith<NewerSchemaException> { DataStore.open(driver) }
        val tables = driver.executeQuery(null, "SELECT count(*) FROM sqlite_master WHERE type = 'table'", { c ->
            QueryResult.Value(if (c.next().value) c.getLong(0) else null)
        }, 0).value
        assertEquals(0L, tables, "an unreadable newer database is not rewritten")
    }

    @Test
    fun aBackupIsAConsistentCopyThatOpensAndPassesTheChecks() {
        val dir = Files.createTempDirectory("pokelife-backup").toFile()
        try {
            val store = JvmDatabase.open(File(dir, "live.db").path)
            store.pokemon.add(pokemon("a"))
            store.pokemon.setParty(ModuleIds.PLAN, listOf("a"))
            store.economy.settle(RewardEventInput("e1", "PLAN", "t", "task-done", "v1", 1, poke = 12))
            val copy = File(dir, "backup.db")
            store.backupTo(copy.path)
            val restored = JvmDatabase.open(copy.path)
            assertEquals(listOf("a"), restored.pokemon.party(ModuleIds.PLAN))
            assertEquals(12L, restored.economy.poke())
            assertEquals(emptyList(), restored.integrityIssues())
            assertEquals(emptyList(), restored.economy.reconcile())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun sha256MatchesTheKnownVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.hex(""))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.hex("abc"))
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
    }
}
