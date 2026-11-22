package app.pokeplan.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.pokeplan.data.db.DataDb

class NewerSchemaException(val found: Long, val supported: Long) :
    IllegalStateException("The database is schema version $found; this build understands up to $supported")

class DataStore private constructor(internal val driver: SqlDriver) {
    internal val db: DataDb = DataDb(driver)

    val pokemon = PokemonRepository(this)
    val economy = EconomyRepository(this)
    val careTables = CareTables(this)
    val planTables = PlanTables(this)
    val healTables = HealTables(this)
    val mintTables = MintTables(this)
    val baseTables = BaseTables(this)
    val linkTables = LinkTables(this)

    @Volatile var mintCipher: RowCipher? = null

    @Volatile var healCipher: RowCipher? = null
    val pokemonTables = PokemonTables(this)
    val live = LiveDocuments(this)
    val reminders = ReminderRepository(this)

    fun <T> transaction(body: () -> T): T = db.transactionWithResult { body() }

    /**
     * Anything the integrity checks object to; an empty list means the file and its references are sound.
     * [full] runs the thorough `integrity_check` (a whole-file scan) for tests and recovery; the app's normal start uses
     * `quick_check`, which catches the same damage without reading every page twice.
     */
    fun integrityIssues(full: Boolean = true): List<String> {
        val problems = mutableListOf<String>()
        driver.executeQuery(null, if (full) "PRAGMA integrity_check" else "PRAGMA quick_check", { c ->
            while (c.next().value) c.getString(0)?.takeIf { it != "ok" }?.let { problems += "integrity: $it" }
            QueryResult.Unit
        }, 0)
        driver.executeQuery(null, "PRAGMA foreign_key_check", { c ->
            while (c.next().value) problems += "foreign key: ${c.getString(0)} row ${c.getLong(1)} -> ${c.getString(2)}"
            QueryResult.Unit
        }, 0)
        return problems
    }

    fun close() = driver.close()

    companion object {
        val SCHEMA_VERSION: Int get() = DataDb.Schema.version.toInt()

        fun open(driver: SqlDriver): DataStore {
            requireForeignKeys(driver)
            prepareSchema(driver)
            return DataStore(driver)
        }

        private fun pragma(driver: SqlDriver, name: String): Long =
            driver.executeQuery(null, "PRAGMA $name", { c -> QueryResult.Value(if (c.next().value) c.getLong(0) else null) }, 0).value ?: 0L

        private fun pragmaCount(driver: SqlDriver, sql: String): Long =
            driver.executeQuery(null, sql, { c -> QueryResult.Value(if (c.next().value) c.getLong(0) else null) }, 0).value ?: 0L

        private fun requireForeignKeys(driver: SqlDriver) {
            check(pragma(driver, "foreign_keys") == 1L) {
                "foreign keys are not enforced on this connection; open the driver with foreign_keys on"
            }
        }

        private fun prepareSchema(driver: SqlDriver) {
            val schema = DataDb.Schema
            val found = pragma(driver, "user_version")
            val transacter = DataDb(driver)
            when {
                found > schema.version -> throw NewerSchemaException(found, schema.version)
                found == 0L -> {
                    check(pragmaCount(driver, "SELECT count(*) FROM sqlite_master") == 0L) {
                        "the database has tables but no schema version; it is not opened or rewritten"
                    }
                    transacter.transaction {
                        schema.create(driver)
                        driver.execute(null, "PRAGMA user_version = ${schema.version}", 0)
                    }
                }
                found < schema.version -> transacter.transaction {
                    schema.migrate(driver, found, schema.version)
                    driver.execute(null, "PRAGMA user_version = ${schema.version}", 0)
                }
            }
        }
    }
}
