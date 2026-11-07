package app.pokeplan.data

import app.cash.sqldelight.db.QueryResult

/**
 * The row-level work the module tables share (CARE's `care_*`, PLAN's `plan_*`): a record table has `key`, `position`, `day` and
 * the record as JSON in `json`; a state table has `key` and `json`. Writing is a diff against what is stored, so a save touches only
 * the rows that changed. Always called inside one of the store's transactions.
 */
internal class RecordRows(private val store: DataStore) {
    class Row(val key: String, val position: Int, val day: String?, val json: String)

    fun records(table: String): List<Row> {
        val rows = mutableListOf<Row>()
        store.driver.executeQuery(null, "SELECT key, position, day, json FROM $table ORDER BY position, key", { c ->
            while (c.next().value) rows += Row(c.getString(0)!!, c.getLong(1)!!.toInt(), c.getString(2), c.getString(3)!!)
            QueryResult.Unit
        }, 0)
        return rows
    }

    fun diffRecords(table: String, wanted: List<Row>) {
        val have = records(table).associateBy { it.key }
        val keys = wanted.map { it.key }.toSet()
        for (gone in have.keys - keys) store.driver.execute(null, "DELETE FROM $table WHERE key = ?", 1) { bindString(0, gone) }
        for (row in wanted) {
            val old = have[row.key]
            if (old != null && old.position == row.position && old.day == row.day && old.json == row.json) continue
            store.driver.execute(null, "INSERT OR REPLACE INTO $table(key, position, day, json) VALUES (?, ?, ?, ?)", 4) {
                bindString(0, row.key); bindLong(1, row.position.toLong()); bindString(2, row.day); bindString(3, row.json)
            }
        }
    }

    fun state(table: String): Map<String, String> {
        val have = mutableMapOf<String, String>()
        store.driver.executeQuery(null, "SELECT key, json FROM $table", { c ->
            while (c.next().value) have[c.getString(0)!!] = c.getString(1)!!
            QueryResult.Unit
        }, 0)
        return have
    }

    fun diffState(table: String, wanted: Map<String, String>) {
        val have = state(table)
        for (gone in have.keys - wanted.keys) store.driver.execute(null, "DELETE FROM $table WHERE key = ?", 1) { bindString(0, gone) }
        for ((key, json) in wanted) if (have[key] != json) store.driver.execute(null, "INSERT OR REPLACE INTO $table(key, json) VALUES (?, ?)", 2) { bindString(0, key); bindString(1, json) }
    }
}
