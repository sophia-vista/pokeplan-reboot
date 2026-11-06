package app.pokeplan.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.util.Properties

/** Opens the database on the JVM (tests and tools; the product opens it through [AndroidDatabase]). */
object JvmDatabase {
    /** [path] null keeps it in memory. Foreign keys are switched on for the connection. */
    fun open(path: String? = null): DataStore {
        val props = Properties().apply { setProperty("foreign_keys", "true") }
        val driver = if (path == null) JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, props) else JdbcSqliteDriver("jdbc:sqlite:$path", props)
        return DataStore.open(driver)
    }
}

/**
 * Writes a consistent copy of the whole database to [path] with SQLite's `VACUUM INTO`, which is safe on a live
 * connection (and in WAL mode, unlike copying the file). The target must not exist.
 *
 * JVM only: `VACUUM INTO` needs SQLite 3.27 and Android 8 to 10 ship older versions, so the Android backup
 * (a WAL checkpoint, then a copy under the single writer) is a separate piece of work that needs a device.
 */
fun DataStore.backupTo(path: String) {
    require("'" !in path) { "the backup path may not contain a quote" }
    driver.execute(null, "VACUUM INTO '$path'", 0)
    // VACUUM INTO does not carry the schema version over; stamp it so the copy opens as the schema it is.
    java.sql.DriverManager.getConnection("jdbc:sqlite:$path").use { it.createStatement().execute("PRAGMA user_version = ${app.pokeplan.data.db.DataDb.Schema.version}") }
}
