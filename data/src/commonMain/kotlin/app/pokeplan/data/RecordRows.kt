package app.pokeplan.data

import app.cash.sqldelight.db.QueryResult

/**
 * The row-level work the module tables share (CARE's `care_*`, PLAN's `plan_*`): a record table has `key`, `position`, `day` and
 * the record as JSON in `json`; a state table has `key` and `json`. Writing is a diff against what is stored, so a save touches only
 * the rows that changed. Always called inside one of the store's transactions.
 */
internal class RecordRows(private val store: DataStore) {
    class Row(val key: String, val position: Int, val day: String?, val json: String)

    /** What seals and opens a stored text (Mint's rows); null for the tables that keep their records in clear. [open] throws [MintLockedException] for a text it cannot open. */
    class Codec(val seal: (String) -> String, val open: (String) -> String)

    fun records(table: String, codec: Codec? = null): List<Row> {
        val rows = mutableListOf<Row>()
        store.driver.executeQuery(null, "SELECT key, position, day, json FROM $table ORDER BY position, key", { c ->
            while (c.next().value) rows += Row(c.getString(0)!!, c.getLong(1)!!.toInt(), c.getString(2), c.getString(3)!!)
            QueryResult.Unit
        }, 0)
        return if (codec == null) rows else rows.map { Row(it.key, it.position, it.day, codec.open(it.json)) }
    }

    fun diffRecords(table: String, wanted: List<Row>, codec: Codec? = null) {
        val have = records(table, codec).associateBy { it.key }
        val keys = wanted.map { it.key }.toSet()
        for (gone in have.keys - keys) store.driver.execute(null, "DELETE FROM $table WHERE key = ?", 1) { bindString(0, gone) }
        for (row in wanted) {
            val old = have[row.key]
            if (old != null && old.position == row.position && old.day == (if (codec == null) row.day else null) && old.json == row.json) continue
            // A sealed row keeps no date beside it: the day is inside the sealed record, so the table shows neither an amount, a name nor a date.
            val stored = codec?.seal?.invoke(row.json) ?: row.json
            store.driver.execute(null, "INSERT OR REPLACE INTO $table(key, position, day, json) VALUES (?, ?, ?, ?)", 4) {
                bindString(0, row.key); bindLong(1, row.position.toLong()); bindString(2, if (codec == null) row.day else null); bindString(3, stored)
            }
        }
    }

    fun state(table: String, codec: Codec? = null): Map<String, String> {
        val have = mutableMapOf<String, String>()
        store.driver.executeQuery(null, "SELECT key, json FROM $table", { c ->
            while (c.next().value) have[c.getString(0)!!] = c.getString(1)!!
            QueryResult.Unit
        }, 0)
        return if (codec == null) have else have.mapValues { codec.open(it.value) }
    }

    fun diffState(table: String, wanted: Map<String, String>, codec: Codec? = null) {
        val have = state(table, codec)
        for (gone in have.keys - wanted.keys) store.driver.execute(null, "DELETE FROM $table WHERE key = ?", 1) { bindString(0, gone) }
        for ((key, json) in wanted) if (have[key] != json) store.driver.execute(null, "INSERT OR REPLACE INTO $table(key, json) VALUES (?, ?)", 2) { bindString(0, key); bindString(1, codec?.seal?.invoke(json) ?: json) }
    }
}
