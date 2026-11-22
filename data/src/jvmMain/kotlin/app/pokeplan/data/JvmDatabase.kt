package app.pokeplan.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.util.Properties

object JvmDatabase {
    fun open(path: String? = null): DataStore {
        val props = Properties().apply { setProperty("foreign_keys", "true") }
        val driver = if (path == null) JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, props) else JdbcSqliteDriver("jdbc:sqlite:$path", props)
        return DataStore.open(driver)
    }
}

fun DataStore.backupTo(path: String) {
    require("'" !in path) { "the backup path may not contain a quote" }
    driver.execute(null, "VACUUM INTO '$path'", 0)
    java.sql.DriverManager.getConnection("jdbc:sqlite:$path").use { it.createStatement().execute("PRAGMA user_version = ${app.pokeplan.data.db.DataDb.Schema.version}") }
}
