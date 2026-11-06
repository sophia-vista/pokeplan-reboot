package app.pokeplan.data

import app.cash.sqldelight.db.QueryResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * CARE's records in their own tables (schema version 4, REP-014; docs/pokelife `MODULES/CARE.md` section 9 and
 * `ARCHITECTURE/DATA_AND_MIGRATIONS.md` section 7.4). The app still reads and writes Care as one JSON document, `CareData`, so the
 * rules stay pure and tested; this class is what stands under that document. [write] takes the document apart into rows and [read]
 * puts it back together, so the tables are the only authority and nothing is saved twice.
 *
 * It works on the JSON, not on `CareData`, so `:data` stays independent of the module and a field it has never heard of (an
 * older or a newer build's) lands in `care_state`, or stays inside its record's `json`, instead of being dropped.
 *
 * A write only touches the rows that changed: a journal entry added is one insert, a routine ticked is one row in
 * `care_routine_completion`. Everything is one transaction, so a failure leaves the tables exactly as they were.
 */
class CareTables(private val store: DataStore) {
    private val q get() = store.db.dataQueries

    /** Whether Care's records live here (set once by [activate]); before that the Care document is a row of `live_document`. */
    fun isActive(): Boolean = q.meta(META_CARE_TABLES).executeAsOneOrNull() == ACTIVE

    /** The Care document as it was last written, or null when nothing has been written yet. */
    fun read(): String? = store.transaction { assemble()?.let { JSON.encodeToString(JsonElement.serializer(), it) } }

    /** Saves [document] (the whole Care document as JSON): the rows that differ are changed, the rest are left alone. */
    fun write(document: String) {
        val parsed = parse(document)
        store.transaction { replaceRows(parsed) }
    }

    /**
     * Moves Care into the tables: [document] (null on a fresh install) is split into rows, every row is read back, and the
     * assembled document is compared with the one given; the marker is set only when they are equal, all in one transaction, so a
     * difference leaves the database exactly as it was. Returns whether it moved anything; already active does nothing.
     */
    fun activate(document: String?, nowMillis: Long): Boolean {
        if (isActive()) return false
        val parsed = document?.let(::parse)
        store.transaction {
            if (parsed != null) {
                replaceRows(parsed)
                val back = assemble()
                check(back != null && canonical(back) == canonical(parsed)) { "the Care records did not read back equal" }
            }
            q.setMeta(META_CARE_TABLES, ACTIVE)
            q.setMeta(META_CARE_TABLES_AT, nowMillis.toString())
        }
        return parsed != null
    }

    /** Erases every Care record (the whole Care save), in one transaction. */
    fun clear() {
        store.transaction {
            for (slice in SLICES) store.driver.execute(null, "DELETE FROM ${slice.table}", 0)
            store.driver.execute(null, "DELETE FROM care_state", 0)
        }
    }

    // --- taking a document apart and putting it back --------------------------------------------------------------------

    private fun replaceRows(document: JsonObject) {
        for (slice in SLICES) {
            val rows = rowsOf(slice, document[slice.field] as? JsonArray)
            diffRecords(slice.table, rows.map { it.row })
            if (slice == ROUTINES) diffCompletions(rows)
        }
        val state = document.filterKeys { key -> SLICES.none { it.field == key && document[key] is JsonArray } }
        diffState(state.mapValues { JSON.encodeToString(JsonElement.serializer(), it.value) })
    }

    private class Row(val key: String, val position: Int, val day: String?, val json: String)
    private class Built(val row: Row, val completions: List<String>)

    private fun rowsOf(slice: Slice, array: JsonArray?): List<Built> {
        val seen = mutableSetOf<String>()
        return array.orEmpty().mapIndexed { index, element ->
            val obj = element as? JsonObject ?: error("a record of ${slice.field} is not an object")
            var key = (obj["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() } ?: "#$index"
            if (!seen.add(key)) { key = "#$index"; seen.add(key) }
            val day = (obj[slice.dayField] as? JsonPrimitive)?.contentOrNull
            val completions = if (slice == ROUTINES) (obj["completions"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull } else emptyList()
            val body = if (slice == ROUTINES) JsonObject(obj.filterKeys { it != "completions" }) else obj
            Built(Row(key, index, day, JSON.encodeToString(JsonElement.serializer(), body)), completions)
        }
    }

    private fun diffRecords(table: String, wanted: List<Row>) {
        val have = mutableMapOf<String, Row>()
        store.driver.executeQuery(null, "SELECT key, position, day, json FROM $table", { c ->
            while (c.next().value) have[c.getString(0)!!] = Row(c.getString(0)!!, c.getLong(1)!!.toInt(), c.getString(2), c.getString(3)!!)
            QueryResult.Unit
        }, 0)
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

    private fun diffCompletions(routines: List<Built>) {
        val have = mutableSetOf<Pair<String, String>>()
        store.driver.executeQuery(null, "SELECT routine_key, day FROM care_routine_completion", { c ->
            while (c.next().value) have += c.getString(0)!! to c.getString(1)!!
            QueryResult.Unit
        }, 0)
        val wanted = routines.flatMap { r -> r.completions.map { r.row.key to it } }.toSet()
        for ((key, day) in have - wanted) store.driver.execute(null, "DELETE FROM care_routine_completion WHERE routine_key = ? AND day = ?", 2) { bindString(0, key); bindString(1, day) }
        for ((key, day) in wanted - have) store.driver.execute(null, "INSERT INTO care_routine_completion(routine_key, day) VALUES (?, ?)", 2) { bindString(0, key); bindString(1, day) }
    }

    private fun diffState(wanted: Map<String, String>) {
        val have = mutableMapOf<String, String>()
        store.driver.executeQuery(null, "SELECT key, json FROM care_state", { c ->
            while (c.next().value) have[c.getString(0)!!] = c.getString(1)!!
            QueryResult.Unit
        }, 0)
        for (gone in have.keys - wanted.keys) store.driver.execute(null, "DELETE FROM care_state WHERE key = ?", 1) { bindString(0, gone) }
        for ((key, json) in wanted) if (have[key] != json) store.driver.execute(null, "INSERT OR REPLACE INTO care_state(key, json) VALUES (?, ?)", 2) { bindString(0, key); bindString(1, json) }
    }

    private fun assemble(): JsonObject? {
        val fields = mutableMapOf<String, JsonElement>()
        store.driver.executeQuery(null, "SELECT key, json FROM care_state", { c ->
            while (c.next().value) fields[c.getString(0)!!] = JSON.parseToJsonElement(c.getString(1)!!)
            QueryResult.Unit
        }, 0)
        val completions = mutableMapOf<String, MutableList<JsonElement>>()
        store.driver.executeQuery(null, "SELECT routine_key, day FROM care_routine_completion ORDER BY routine_key, day", { c ->
            while (c.next().value) completions.getOrPut(c.getString(0)!!) { mutableListOf() } += JsonPrimitive(c.getString(1)!!)
            QueryResult.Unit
        }, 0)
        var any = fields.isNotEmpty()
        for (slice in SLICES) {
            val records = mutableListOf<JsonElement>()
            store.driver.executeQuery(null, "SELECT key, json FROM ${slice.table} ORDER BY position, key", { c ->
                while (c.next().value) {
                    val obj = JSON.parseToJsonElement(c.getString(1)!!) as JsonObject
                    records += if (slice == ROUTINES) JsonObject(obj + ("completions" to JsonArray(completions[c.getString(0)!!].orEmpty()))) else obj
                }
                QueryResult.Unit
            }, 0)
            if (records.isNotEmpty()) { fields[slice.field] = JsonArray(records); any = true }
        }
        return if (any) JsonObject(fields) else null
    }

    private class Slice(val field: String, val table: String, val dayField: String)

    companion object {
        const val META_CARE_TABLES = "care_store"
        const val META_CARE_TABLES_AT = "care_store_since"
        const val ACTIVE = "tables"

        private val JSON = Json { encodeDefaults = true }

        private val ROUTINES = Slice("routines", "care_routine", "createdOn")
        private val SLICES = listOf(
            ROUTINES,
            Slice("diary", "care_diary_card", "day"),
            Slice("breaks", "care_calm_break", "day"),
            Slice("practices", "care_skill_practice", "day"),
            Slice("journal", "care_journal_entry", "day"),
            Slice("visitors", "care_visitor", "arrivedDay"),
            Slice("dismissed", "care_dismissed_visitor", "arrivedDay"),
        )

        private fun parse(text: String): JsonObject = JSON.parseToJsonElement(text) as? JsonObject ?: error("the Care document is not a JSON object")

        /**
         * The document with what a read back cannot tell apart left out: an empty list of records is not stored (nothing to put
         * in a table), and a routine that had never been done has an empty `completions` array or none; each comes back as
         * the same thing either way. The days of a routine are a set, so their order does not matter.
         */
        private fun canonical(document: JsonObject): JsonObject {
            val kept = document.filterNot { (key, value) -> SLICES.any { it.field == key } && (value as? JsonArray)?.isEmpty() == true }
            val routines = kept["routines"] as? JsonArray ?: return JsonObject(kept)
            val cleaned = routines.map { r ->
                val obj = r as? JsonObject ?: return@map r
                val done = obj["completions"] as? JsonArray ?: return@map obj
                if (done.isEmpty()) JsonObject(obj - "completions") else JsonObject(obj + ("completions" to JsonArray(done.sortedBy { (it as? JsonPrimitive)?.contentOrNull.orEmpty() })))
            }
            return JsonObject(kept + ("routines" to JsonArray(cleaned)))
        }
    }
}
