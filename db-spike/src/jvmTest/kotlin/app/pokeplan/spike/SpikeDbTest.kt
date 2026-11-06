package app.pokeplan.spike

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The questions of REP-006, answered on the JVM with the same SQLite engine and generated code the Android driver uses. */
class SpikeDbTest {
    private fun driver(path: String? = null): SqlDriver {
        val props = Properties().apply { setProperty("foreign_keys", "true") }
        return if (path == null) JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, props) else JdbcSqliteDriver("jdbc:sqlite:$path", props)
    }

    private fun fresh(): Pair<SqlDriver, SpikeDb> {
        val d = driver()
        SpikeDb.Schema.create(d)
        return d to SpikeDb(d)
    }

    private fun SqlDriver.scalar(sql: String): Long =
        executeQuery(null, sql, { c -> QueryResult.Value(if (c.next().value) c.getLong(0) else null) }, 0).value ?: error("no row")

    @Test
    fun theGeneratedSchemaCreatesAndFeedsTypedQueries() {
        val (_, db) = fresh()
        db.spikeQueries.insertPokemon("a", 25, 5, "Sparky")
        db.spikeQueries.setParty("PLAN", 1, "a")
        assertEquals(listOf(1L to "a"), db.spikeQueries.partyOf("PLAN").executeAsList().map { it.slot to it.owned_id })
    }

    @Test
    fun aSecondSettlementOfTheSameEventRollsTheWholeTransactionBack() {
        val (_, db) = fresh()
        val q = db.spikeQueries
        q.ensureBalance()
        fun pay(id: String, poke: Long) = db.transaction {
            q.settle(id, "PLAN", poke, 1L) // the primary key refuses a repeat, before the balance is touched
            q.addPoke(poke)
        }
        pay("e1", 30)
        assertFailsWith<Exception> { pay("e1", 30) }
        assertEquals(30L, q.balance().executeAsOne())
        assertEquals(1L, q.ledgerCount().executeAsOne())
    }

    @Test
    fun aFailureInTheMiddleOfATransactionLeavesNothingBehind() {
        val (_, db) = fresh()
        val q = db.spikeQueries
        q.ensureBalance()
        assertFailsWith<IllegalStateException> {
            db.transaction {
                q.addPoke(50)
                q.settle("e2", "PLAN", 50, 1L)
                error("interrupted")
            }
        }
        assertEquals(0L, q.balance().executeAsOne())
        assertEquals(0L, q.ledgerCount().executeAsOne())
    }

    @Test
    fun constraintsHoldWhereTheCodeCouldForgetThem() {
        val (_, db) = fresh()
        val q = db.spikeQueries
        q.insertPokemon("a", 25, 5, null)
        assertFailsWith<Exception>("seventh slot") { q.setParty("PLAN", 7, "a") }
        assertFailsWith<Exception>("an owned id that does not exist") { q.setParty("PLAN", 1, "ghost") }
        q.setParty("PLAN", 1, "a")
        assertFailsWith<Exception>("the same Pokémon twice in one party") { q.setParty("PLAN", 2, "a") }
        q.setParty("CARE", 1, "a") // the same Pokémon in a different module's party is allowed
        q.releasePokemon("a")
        assertEquals(0, q.partyOf("PLAN").executeAsList().size, "releasing removes it from every party")
        assertEquals(0, q.partyOf("CARE").executeAsList().size)
    }

    @Test
    fun aVersionOneFileMigratesToVersionTwoAndKeepsItsRows() {
        val file = File.createTempFile("spike", ".db").also { it.deleteOnExit() }
        driver(file.path).use { d ->
            d.execute(null, "CREATE TABLE owned_pokemon (id TEXT NOT NULL PRIMARY KEY, species INTEGER NOT NULL, level INTEGER NOT NULL DEFAULT 1)", 0)
            d.execute(null, "CREATE TABLE bag_stack (item_id TEXT NOT NULL PRIMARY KEY, count INTEGER NOT NULL CHECK (count >= 0))", 0)
            d.execute(null, "CREATE TABLE poke_balance (id INTEGER NOT NULL PRIMARY KEY CHECK (id = 1), amount INTEGER NOT NULL CHECK (amount >= 0))", 0)
            d.execute(null, "CREATE TABLE reward_ledger (event_id TEXT NOT NULL PRIMARY KEY, source_module TEXT NOT NULL, poke INTEGER NOT NULL, settled_at INTEGER NOT NULL)", 0)
            d.execute(null, "INSERT INTO owned_pokemon(id, species, level) VALUES ('old', 133, 12)", 0)
            d.execute(null, "INSERT INTO poke_balance(id, amount) VALUES (1, 400)", 0)
        }
        driver(file.path).use { d ->
            assertEquals(2L, SpikeDb.Schema.version)
            SpikeDb.Schema.migrate(d, 1, SpikeDb.Schema.version).value
            val db = SpikeDb(d)
            db.spikeQueries.setParty("PLAN", 1, "old")
            assertEquals(400L, db.spikeQueries.balance().executeAsOne())
            assertEquals(12L, d.scalar("SELECT level FROM owned_pokemon WHERE id = 'old'"))
            assertTrue(d.scalar("SELECT count(*) FROM pragma_table_info('owned_pokemon') WHERE name = 'nickname'") == 1L)
        }
    }
}
