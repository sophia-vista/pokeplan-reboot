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
            check("Live documents: activate, write, backup, restore") {
                val live = s.live
                live.activate("{\"a\":1}", "{\"c\":1}", 1)
                check(live.isActive()) { "marker not set" }
                live.write("save", "{\"a\":2}", 2)
                val dir = File(db.parentFile, "selfcheck-backups").also { it.deleteRecursively() }
                try {
                    val b = LiveBackup(dir)
                    check(b.take(live, 3) != null) { "no backup written" }
                    live.write("save", "{\"a\":3}", 4)
                    check(b.restoreNewest(live, 5)) { "no backup to restore" }
                    check(live.read("save") == "{\"a\":2}" && live.read("aside:save:5") == "{\"a\":3}") { "restore did not match" }
                } finally { dir.deleteRecursively() }
                "marker set, backup written and restored, replaced document kept aside"
            }
            check("Care tables: move in, write a change, read back, backup sees it") {
                val live = s.live
                val doc = "{\"routines\":[{\"id\":\"r\",\"title\":\"T\",\"createdOn\":\"2026-11-01\",\"completions\":[\"2026-11-01\"]}],\"journal\":[{\"id\":\"j\",\"atMillis\":1,\"day\":\"2026-11-01\",\"text\":\"x\"}],\"journalDraft\":\"\"}"
                live.write("care", doc, 6)
                live.moveCareToTables(7)
                check(s.careTables.isActive()) { "marker not set" }
                live.write("care", doc.replace("\"journalDraft\":\"\"", "\"journalDraft\":\"d\""), 8)
                val back = live.read("care") ?: error("Care did not read back")
                check("\"journalDraft\":\"d\"" in back && "\"completions\":[\"2026-11-01\"]" in back && "\"text\":\"x\"" in back) { "the tables did not give the document back" }
                check(live.snapshot()["care"] == back) { "a backup would not see Care" }
                "moved into tables, a change written, read back equal, the snapshot has it"
            }
            check("Plan tables: move in, write a change, read back, backup sees it") {
                val live = s.live
                val doc = "{\"player\":{\"coins\":1},\"tasks\":[{\"id\":\"t\",\"title\":\"T\"}],\"activeDays\":[\"2026-11-01\"],\"focus\":null}"
                live.write("save", doc, 9)
                live.movePlanToTables(10)
                check(s.planTables.isActive()) { "marker not set" }
                live.write("save", doc.replace("\"coins\":1", "\"coins\":2").replace("\"title\":\"T\"", "\"title\":\"U\""), 11)
                val back = live.read("save") ?: error("the save did not read back")
                check("\"coins\":2" in back && "\"title\":\"U\"" in back && "\"activeDays\":[\"2026-11-01\"]" in back) { "the tables did not give the save back" }
                check(live.snapshot()["save"] == back) { "a backup would not see the save" }
                "moved into tables, a change written, read back equal, the snapshot has it"
            }
            check("Reminder holds: put, replace, release") {
                val r = s.reminders
                r.hold(HeldReminder("PLAN|t|task|1", "PLAN", "t", "task", "1", 100, "snooze"), 1)
                r.hold(HeldReminder("PLAN|t|task|1", "PLAN", "t", "task", "1", 200, "snooze"), 2)
                check(r.all().map { it.atMillis } == listOf(200L)) { "snoozing twice did not replace" }
                r.release("PLAN|t|task|1")
                check(r.all().isEmpty()) { "release left a row" }
                "one row per reminder, released cleanly"
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
