package app.pokeplan.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import app.cash.sqldelight.db.QueryResult
import java.io.File

/** One line of the device report. */
data class CheckLine(val name: String, val ok: Boolean, val detail: String)

/**
 * The on-device database checks (docs/pokelife/evidence/REP-006-sqldelight-spike.md section 3), run against a throwaway file
 * in app-private storage that is deleted afterwards. It never opens or touches `pokelife.db` or the JSON save, so running it
 * cannot harm the player's data. Covers the Android driver, foreign keys, WAL, transaction rollback, kill-safe commits, the
 * backup copy (checkpoint then copy) and a timing; the widgets' access and MINT encryption need separate work.
 */
object DeviceSelfCheck {
    private const val NAME = "selfcheck.db"

    fun run(context: Context): List<CheckLine> {
        val out = mutableListOf<CheckLine>()
        fun check(name: String, body: () -> String) {
            out += try { CheckLine(name, true, body()) } catch (t: Throwable) { CheckLine(name, false, t.toString().take(200)) }
        }
        val db = context.getDatabasePath(NAME)
        val copy = File(db.parentFile, "selfcheck-copy.db")
        cleanup(context, copy)
        var store: DataStore? = null
        try {
            check("SQLite version") { SQLiteDatabase.create(null).use { c -> c.rawQuery("select sqlite_version()", null).use { it.moveToFirst(); "${it.getString(0)} on Android ${Build.VERSION.RELEASE}" } } }
            check("Opens, foreign keys enforced, schema created") {
                store = AndroidDatabase.open(context, NAME)
                "schema ${app.pokeplan.data.db.DataDb.Schema.version}, integrity issues: ${store!!.integrityIssues().size}".also { check(store!!.integrityIssues().isEmpty()) { it } }
            }
            val s = store ?: return out
            check("Journal mode (WAL wanted)") { "journal_mode = ${scalar(s, "PRAGMA journal_mode")}" }
            check("Transaction rolls back whole") {
                s.driver.execute(null, "CREATE TABLE IF NOT EXISTS selfcheck (id INTEGER PRIMARY KEY, v TEXT)", 0)
                runCatching { s.transaction { s.driver.execute(null, "INSERT INTO selfcheck (v) VALUES ('a')", 0); error("boom") } }
                val n = scalar(s, "SELECT count(*) FROM selfcheck").toLong()
                check(n == 0L) { "left $n rows" }; "0 rows left after a failed transaction"
            }
            check("Foreign key violation is refused") {
                s.driver.execute(null, "CREATE TABLE IF NOT EXISTS sc_parent (id INTEGER PRIMARY KEY)", 0)
                s.driver.execute(null, "CREATE TABLE IF NOT EXISTS sc_child (id INTEGER PRIMARY KEY, p INTEGER REFERENCES sc_parent(id))", 0)
                val refused = runCatching { s.driver.execute(null, "INSERT INTO sc_child (p) VALUES (99)", 0) }.isFailure
                check(refused) { "an orphan row was accepted" }; "orphan row refused"
            }
            check("Speed: 5000 rows in one transaction") {
                val t = System.nanoTime()
                s.transaction { repeat(5000) { s.driver.execute(null, "INSERT INTO selfcheck (v) VALUES ('x')", 0) } }
                "${(System.nanoTime() - t) / 1_000_000} ms"
            }
            check("Backup copy: checkpoint, copy, reopen, same rows") {
                scalar(s, "PRAGMA wal_checkpoint(TRUNCATE)")
                db.copyTo(copy, overwrite = true)
                SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READONLY).use { c ->
                    val n = c.rawQuery("select count(*) from selfcheck", null).use { it.moveToFirst(); it.getLong(0) }
                    val ok = c.rawQuery("pragma integrity_check", null).use { it.moveToFirst(); it.getString(0) }
                    check(n == 5000L && ok == "ok") { "rows $n, integrity $ok" }; "5000 rows, integrity ok, ${copy.length() / 1024} KB"
                }
            }
            check("Reopen after close keeps committed rows") {
                s.close(); store = null
                val again = AndroidDatabase.open(context, NAME); store = again
                "${scalar(again, "SELECT count(*) FROM selfcheck")} rows after reopen"
            }
        } finally {
            runCatching { store?.close() }
            cleanup(context, copy)
        }
        return out
    }

    private fun scalar(s: DataStore, sql: String): String =
        s.driver.executeQuery(null, sql, { c -> QueryResult.Value(if (c.next().value) c.getString(0) else null) }, 0).value ?: ""

    private fun cleanup(context: Context, copy: File) {
        context.deleteDatabase(NAME)
        copy.delete()
    }
}
