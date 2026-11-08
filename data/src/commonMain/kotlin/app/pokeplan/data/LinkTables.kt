package app.pokeplan.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * LINK's records in their own tables from the start (schema version 10, REP-014; docs/pokelife `MODULES/LINK.md` and
 * `ARCHITECTURE/DATA_AND_MIGRATIONS.md` section 7.4). LINK, like HEAL, never had a document row: the app reads and writes it as one JSON document,
 * `LinkData`, so the rules stay pure and tested, and this class is what stands under it, as [HealTables] stands under Heal's. Nothing is sealed (that is Mint's alone).
 *
 * **One table family per kind of record** (`link_person`, `link_note`, `link_date`, `link_followup`, plus `link_state` for the settings), so a kind can be read ([readKind]), exported or
 * erased ([erase]) on its own; erasing LINK ([clear]) touches nothing outside the `link_*` tables: not the Pokémon, the Bag, the ledger, Plan, Care or Heal.
 *
 * It works on the JSON, not on `LinkData`, so `:data` stays independent of the module and a field it has never heard of (a newer build's) lands in
 * `link_state`, or stays inside its record's `json`, instead of being dropped. A write only touches the rows that changed, in one transaction.
 */
class LinkTables(private val store: DataStore) {
    private val q get() = store.db.dataQueries
    private val rows = RecordRows(store)

    /** Whether LINK lives here (set once by [activate]); before that a `link` row of `live_document`, if any, is all there is. */
    fun isActive(): Boolean = q.meta(META_LINK_TABLES).executeAsOneOrNull() == ACTIVE

    /** The LINK document as it was last written, or null when nothing has been written yet. */
    fun read(): String? = store.transaction { assemble()?.let { JSON.encodeToString(JsonElement.serializer(), it) } }

    /** Saves [document] (the whole LINK document as JSON): the rows that differ are changed, the rest are left alone. */
    fun write(document: String) {
        val parsed = parse(document)
        store.transaction { replaceRows(parsed) }
    }

    /**
     * Starts LINK in the tables: [document] (null on a fresh install, the usual case) is split into rows, every row is read back and the assembled
     * document is compared with the one given; the marker is set only when they are equal, all in one transaction, so a difference leaves the
     * database exactly as it was. Returns whether it imported a document; already active does nothing.
     */
    fun activate(document: String?, nowMillis: Long): Boolean {
        if (isActive()) return false
        val parsed = document?.let(::parse)
        store.transaction {
            if (parsed != null) {
                replaceRows(parsed)
                val back = assemble()
                check(back != null && canonical(back) == canonical(parsed)) { "the Link records did not read back equal" }
            }
            q.setMeta(META_LINK_TABLES, ACTIVE)
            q.setMeta(META_LINK_TABLES_AT, nowMillis.toString())
        }
        return parsed != null
    }

    /** One kind of record ([LinkTables.KINDS] names them) as a JSON array, for export; empty when there is none. */
    fun readKind(field: String): String {
        val slice = SLICES.firstOrNull { it.field == field } ?: error("unknown kind of LINK record: $field")
        return store.transaction { JSON.encodeToString(JsonElement.serializer(), JsonArray(rows.records(slice.table).map { JSON.parseToJsonElement(it.json) })) }
    }

    /** Erases one kind of record (every row of its table family) and nothing else, in one transaction. */
    fun erase(field: String) {
        val slice = SLICES.firstOrNull { it.field == field } ?: error("unknown kind of LINK record: $field")
        store.transaction { store.driver.execute(null, "DELETE FROM ${slice.table}", 0) }
    }

    /** Erases every LINK record and setting (the whole LINK save), in one transaction. Nothing outside the `link_*` tables is touched. */
    fun clear() {
        store.transaction {
            for (slice in SLICES) store.driver.execute(null, "DELETE FROM ${slice.table}", 0)
            store.driver.execute(null, "DELETE FROM link_state", 0)
        }
    }

    // --- taking a document apart and putting it back --------------------------------------------------------------------

    private fun replaceRows(document: JsonObject) {
        for (slice in SLICES) rows.diffRecords(slice.table, rowsOf(slice, document[slice.field] as? JsonArray))
        val state = document.filterKeys { key -> SLICES.none { it.field == key && document[key] is JsonArray } }
        rows.diffState("link_state", state.mapValues { JSON.encodeToString(JsonElement.serializer(), it.value) })
    }

    private fun rowsOf(slice: Slice, array: JsonArray?): List<RecordRows.Row> {
        val seen = mutableSetOf<String>()
        return array.orEmpty().mapIndexed { index, element ->
            val obj = element as? JsonObject ?: error("a record of ${slice.field} is not an object")
            var key = (obj["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() } ?: "#$index"
            if (!seen.add(key)) { key = "#$index"; seen.add(key) }
            RecordRows.Row(key, index, (obj[slice.dayField] as? JsonPrimitive)?.contentOrNull, JSON.encodeToString(JsonElement.serializer(), obj))
        }
    }

    private fun assemble(): JsonObject? {
        val fields = mutableMapOf<String, JsonElement>()
        for ((key, json) in rows.state("link_state")) fields[key] = JSON.parseToJsonElement(json)
        var any = fields.isNotEmpty()
        for (slice in SLICES) {
            val records = rows.records(slice.table).map { JSON.parseToJsonElement(it.json) }
            if (records.isNotEmpty()) { fields[slice.field] = JsonArray(records); any = true }
        }
        return if (any) JsonObject(fields) else null
    }

    private class Slice(val field: String, val table: String, val dayField: String)

    companion object {
        const val META_LINK_TABLES = "link_store"
        const val META_LINK_TABLES_AT = "link_store_since"
        const val ACTIVE = "tables"

        private val JSON = Json { encodeDefaults = true }

        private val SLICES = listOf(
            Slice("people", "link_person", "day"),
            Slice("notes", "link_note", "day"),
            Slice("dates", "link_date", "day"),
            Slice("followUps", "link_followup", "due"),
        )

        /** The document fields that are a kind of private record, each with its own table family. */
        val KINDS: List<String> = SLICES.map { it.field }

        private fun parse(text: String): JsonObject = JSON.parseToJsonElement(text) as? JsonObject ?: error("the Link document is not a JSON object")

        /** The document with what a read back cannot tell apart left out: an empty list of records is not stored (nothing to put in a table). */
        private fun canonical(document: JsonObject): JsonObject =
            JsonObject(document.filterNot { (key, value) -> SLICES.any { it.field == key } && (value as? JsonArray)?.isEmpty() == true })
    }
}
