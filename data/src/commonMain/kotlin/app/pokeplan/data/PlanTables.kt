package app.pokeplan.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * PLAN's records in their own tables (schema version 5, REP-014; docs/pokelife `MODULES/PLAN.md` section 11 and
 * `ARCHITECTURE/DATA_AND_MIGRATIONS.md` section 7.4). PLAN shares the save document (`AppData`) with the player, so unlike CARE it
 * does not own a whole document: [PLAN_FIELDS] are PLAN's part of it, and [LiveDocuments] splits every save into that part (here) and
 * the rest (the `save` row), and puts the two back together on a read. The app still reads and writes one `AppData`, so the rules
 * stay pure and tested, and the tables are the only authority for PLAN: nothing is saved twice.
 *
 * It works on the JSON, not on the model, so `:data` stays independent of `plan-core` and a field it has never heard of stays inside
 * its record (or, at the top level, in the `save` row). A write only touches the rows that changed: a task ticked is one row, the
 * running focus session is one `plan_state` row. Everything is one transaction.
 */
class PlanTables(private val store: DataStore) {
    private val q get() = store.db.dataQueries
    private val rows = RecordRows(store)

    /** Whether PLAN's records live here (set once by [activate]); before that they are inside the `save` row. */
    fun isActive(): Boolean = q.meta(META_PLAN_TABLES).executeAsOneOrNull() == ACTIVE

    /** Takes PLAN's fields out of [document] (the whole save): [split]'s `first` is the part for the tables, `second` the rest. */
    fun split(document: String): Pair<JsonObject, JsonObject> {
        val parsed = parse(document)
        val plan = parsed.filterKeys { it in PLAN_FIELDS }
        return JsonObject(plan) to JsonObject(parsed.filterKeys { it !in PLAN_FIELDS })
    }

    /** Saves PLAN's part of a save: the rows that differ are changed, the rest are left alone. Call it inside a transaction. */
    fun write(plan: JsonObject) {
        for (slice in SLICES) {
            val array = plan[slice.field] as? JsonArray
            rows.diffRecords(slice.table, rowsOf(slice, array))
        }
        diffDays(plan)
        val state = plan.filterKeys { key -> key in STATE_FIELDS }
        rows.diffState("plan_state", state.mapValues { JSON.encodeToString(JsonElement.serializer(), it.value) })
    }

    /** PLAN's fields as stored (only the ones that have a value), to be put back into the save with the rest. */
    fun read(): JsonObject = JsonObject(assemble())

    /**
     * Moves PLAN into the tables: PLAN's part of [document] (null on a fresh install) is split into rows, every row is read back, and
     * the assembled document is compared with the one given; the marker is set only when they are equal, all in one transaction, so a
     * difference leaves the database exactly as it was. Returns the part of [document] that is not PLAN's (what stays in the `save`
     * row), or null when there was no document. Already active: does nothing and returns null.
     */
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

    /** Erases every PLAN record (the whole PLAN save), in one transaction. */
    fun clear() {
        store.transaction {
            for (slice in SLICES) store.driver.execute(null, "DELETE FROM ${slice.table}", 0)
            store.driver.execute(null, "DELETE FROM plan_day", 0)
            store.driver.execute(null, "DELETE FROM plan_state", 0)
        }
    }

    // --- taking a document apart and putting it back --------------------------------------------------------------------

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
            // A series has no id of its own: its template carries it (the series id), and its first day is the start of its rule or its creation.
            Slice("series", "plan_series", { o -> (o["template"] as? JsonObject)?.text("id") }, { o -> (o["recurrence"] as? JsonObject)?.text("start") ?: o.text("createdOn") }),
            Slice("goals", "plan_goal", "id", "targetDate"),
            Slice("reviews", "plan_review", "weekStart", "weekStart"),
            Slice("focusHistory", "plan_focus_record", "startedAtMillis"),
        )

        /** The two sets of days, each a JSON array of dates. */
        private val DAY_SETS = listOf("restDays", "activeDays")

        /** Single values that belong to PLAN: the running session and how it ended, completions waiting to be undone, the pick, the emergency pauses, and the habits kept aside after their merge. */
        private val STATE_FIELDS = setOf("focus", "lastFocusEnd", "pending", "pickedTaskId", "emergencyPausesDay", "emergencyPausesUsed", "legacyHabits", "habitsMergedAtMillis")

        /** Every top-level field of the save that PLAN owns; the rest of the save is not PLAN's. */
        val PLAN_FIELDS: Set<String> = SLICES.map { it.field }.toSet() + DAY_SETS + STATE_FIELDS

        private fun parse(text: String): JsonObject = JSON.parseToJsonElement(text) as? JsonObject ?: error("the save is not a JSON object")

        /**
         * The save with what a read back cannot tell apart left out: an empty list of records or days is not stored, and comes back as
         * the model's own empty default; a day set is a set, so its order does not matter.
         */
        private fun canonical(document: JsonObject): JsonObject = JsonObject(
            document.filterNot { (key, value) -> (key in SLICES.map { it.field } || key in DAY_SETS) && (value as? JsonArray)?.isEmpty() == true }
                .mapValues { (key, value) ->
                    if (key in DAY_SETS && value is JsonArray) JsonArray(value.sortedBy { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }.distinct()) else value
                },
        )
    }
}
