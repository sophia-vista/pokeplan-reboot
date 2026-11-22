package app.pokeplan.data

import app.cash.sqldelight.db.QueryResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class CareTables(private val store: DataStore) {
    private val q get() = store.db.dataQueries
    private val rows = RecordRows(store)

    fun isActive(): Boolean = q.meta(META_CARE_TABLES).executeAsOneOrNull() == ACTIVE

    fun read(): String? = store.transaction { assemble()?.let { JSON.encodeToString(JsonElement.serializer(), it) } }

    fun write(document: String) {
        val parsed = parse(document)
        store.transaction { replaceRows(parsed) }
    }

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

    fun clear() {
        store.transaction {
            for (slice in SLICES) store.driver.execute(null, "DELETE FROM ${slice.table}", 0)
            store.driver.execute(null, "DELETE FROM care_state", 0)
        }
    }

    private fun replaceRows(document: JsonObject) {
        for (slice in SLICES) {
            val built = rowsOf(slice, document[slice.field] as? JsonArray)
            rows.diffRecords(slice.table, built.map { it.row })
            if (slice == ROUTINES) diffCompletions(built)
        }
        val state = document.filterKeys { key -> SLICES.none { it.field == key && document[key] is JsonArray } }
        diffState(state.mapValues { JSON.encodeToString(JsonElement.serializer(), it.value) })
    }

    private class Built(val row: RecordRows.Row, val completions: List<String>)

    private fun rowsOf(slice: Slice, array: JsonArray?): List<Built> {
        val seen = mutableSetOf<String>()
        return array.orEmpty().mapIndexed { index, element ->
            val obj = element as? JsonObject ?: error("a record of ${slice.field} is not an object")
            var key = (obj["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() } ?: "#$index"
            if (!seen.add(key)) { key = "#$index"; seen.add(key) }
            val day = (obj[slice.dayField] as? JsonPrimitive)?.contentOrNull
            val completions = if (slice == ROUTINES) (obj["completions"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull } else emptyList()
            val body = if (slice == ROUTINES) JsonObject(obj.filterKeys { it != "completions" }) else obj
            Built(RecordRows.Row(key, index, day, JSON.encodeToString(JsonElement.serializer(), body)), completions)
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

    private fun diffState(wanted: Map<String, String>) = rows.diffState("care_state", wanted)

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
