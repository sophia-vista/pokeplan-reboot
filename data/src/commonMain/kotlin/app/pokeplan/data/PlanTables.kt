package app.pokeplan.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class PlanTables(private val store: DataStore) {
    private val q get() = store.db.dataQueries
    private val rows = RecordRows(store)

    fun isActive(): Boolean = q.meta(META_PLAN_TABLES).executeAsOneOrNull() == ACTIVE

    fun split(document: String): Pair<JsonObject, JsonObject> {
        val parsed = parse(document)
        val plan = parsed.filterKeys { it in PLAN_FIELDS }
        return JsonObject(plan) to JsonObject(parsed.filterKeys { it !in PLAN_FIELDS })
    }

    fun write(plan: JsonObject) {
        for (slice in SLICES) {
            val array = plan[slice.field] as? JsonArray
            rows.diffRecords(slice.table, rowsOf(slice, array))
        }
        diffDays(plan)
        val state = plan.filterKeys { key -> key in STATE_FIELDS }
        rows.diffState("plan_state", state.mapValues { JSON.encodeToString(JsonElement.serializer(), it.value) })
    }

    fun read(): JsonObject = JsonObject(assemble())

    fun activate(document: String?, nowMillis: Long): JsonObject? {
        if (isActive()) return null
        var rest: JsonObject? = null
        store.transaction {
            if (document != null) {
                val parsed = parse(document)
                val (plan, others) = split(document)
                write(plan)
                val back = JsonObject(parsed.filterKeys { it !in PLAN_FIELDS } + assemble())
                check(canonical(back) == canonical(parsed)) { "the PLAN records did not read back equal" }
                rest = others
            }
            q.setMeta(META_PLAN_TABLES, ACTIVE)
            q.setMeta(META_PLAN_TABLES_AT, nowMillis.toString())
        }
        return rest
    }

    fun clear() {
        store.transaction {
            for (slice in SLICES) store.driver.execute(null, "DELETE FROM ${slice.table}", 0)
            store.driver.execute(null, "DELETE FROM plan_day", 0)
            store.driver.execute(null, "DELETE FROM plan_state", 0)
        }
    }

    private fun rowsOf(slice: Slice, array: JsonArray?): List<RecordRows.Row> {
        val seen = mutableSetOf<String>()
        return array.orEmpty().mapIndexed { index, element ->
            val obj = element as? JsonObject ?: error("a record of ${slice.field} is not an object")
            var key = slice.keyOf(obj)?.takeIf { it.isNotEmpty() } ?: "#$index"
            if (!seen.add(key)) { key = "#$index"; seen.add(key) }
            RecordRows.Row(key, index, slice.dayOf(obj), JSON.encodeToString(JsonElement.serializer(), obj))
        }
    }

    private fun diffDays(plan: JsonObject) {
        val have = mutableSetOf<Pair<String, String>>()
        store.driver.executeQuery(null, "SELECT kind, day FROM plan_day", { c ->
            while (c.next().value) have += c.getString(0)!! to c.getString(1)!!
            app.cash.sqldelight.db.QueryResult.Unit
        }, 0)
        val wanted = DAY_SETS.flatMap { field -> (plan[field] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.map { field to it } }.toSet()
        for ((kind, day) in have - wanted) store.driver.execute(null, "DELETE FROM plan_day WHERE kind = ? AND day = ?", 2) { bindString(0, kind); bindString(1, day) }
        for ((kind, day) in wanted - have) store.driver.execute(null, "INSERT INTO plan_day(kind, day) VALUES (?, ?)", 2) { bindString(0, kind); bindString(1, day) }
    }

    private fun assemble(): Map<String, JsonElement> {
        val fields = mutableMapOf<String, JsonElement>()
        for ((key, json) in rows.state("plan_state")) fields[key] = JSON.parseToJsonElement(json)
        for (slice in SLICES) {
            val records = rows.records(slice.table).map { JSON.parseToJsonElement(it.json) }
            if (records.isNotEmpty()) fields[slice.field] = JsonArray(records)
        }
        val days = mutableMapOf<String, MutableList<JsonElement>>()
        store.driver.executeQuery(null, "SELECT kind, day FROM plan_day ORDER BY kind, day", { c ->
            while (c.next().value) days.getOrPut(c.getString(0)!!) { mutableListOf() } += JsonPrimitive(c.getString(1)!!)
            app.cash.sqldelight.db.QueryResult.Unit
        }, 0)
        for ((kind, list) in days) fields[kind] = JsonArray(list)
        return fields
    }

    private class Slice(val field: String, val table: String, val keyOf: (JsonObject) -> String?, val dayOf: (JsonObject) -> String? = { null }) {
        constructor(field: String, table: String, keyField: String, dayField: String? = null) :
            this(field, table, { it.text(keyField) }, { o -> dayField?.let { f -> o.text(f) } })
    }

    companion object {
        const val META_PLAN_TABLES = "plan_store"
        const val META_PLAN_TABLES_AT = "plan_store_since"
        const val ACTIVE = "tables"

        private val JSON = Json { encodeDefaults = true }

        private fun JsonObject.text(field: String): String? = (this[field] as? JsonPrimitive)?.contentOrNull

        private val SLICES = listOf(
            Slice("tasks", "plan_task", "id", "planned"),
            Slice("lists", "plan_list", "id"),
            Slice("events", "plan_event", "id", "day"),
            Slice("series", "plan_series", { o -> (o["template"] as? JsonObject)?.text("id") }, { o -> (o["recurrence"] as? JsonObject)?.text("start") ?: o.text("createdOn") }),
            Slice("goals", "plan_goal", "id", "targetDate"),
            Slice("reviews", "plan_review", "weekStart", "weekStart"),
            Slice("focusHistory", "plan_focus_record", "startedAtMillis"),
        )

        private val DAY_SETS = listOf("restDays", "activeDays")

        private val STATE_FIELDS = setOf("focus", "lastFocusEnd", "pending", "pickedTaskId", "emergencyPausesDay", "emergencyPausesUsed", "habits", "legacyHabits", "habitsMergedAtMillis")

        val PLAN_FIELDS: Set<String> = SLICES.map { it.field }.toSet() + DAY_SETS + STATE_FIELDS

        private fun parse(text: String): JsonObject = JSON.parseToJsonElement(text) as? JsonObject ?: error("the save is not a JSON object")

        internal fun canonical(document: JsonObject): JsonObject = JsonObject(
            document.filterNot { (key, value) -> (key in SLICES.map { it.field } || key in DAY_SETS) && (value as? JsonArray)?.isEmpty() == true }
                .mapValues { (key, value) ->
                    if (key in DAY_SETS && value is JsonArray) JsonArray(value.sortedBy { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }.distinct()) else value
                },
        )
    }
}
