package app.pokeplan.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * MINT's records in their own tables from the start (financial data is the most sensitive Mint keeps: nothing outside the `mint_*` tables ever holds an amount) (schema version 8, REP-014; docs/pokelife `MODULES/MINT.md` section 15 and
 * `ARCHITECTURE/DATA_AND_MIGRATIONS.md` section 7.4). MINT, like HEAL, never had a document row: the app reads and writes it as one JSON document,
 * `MintData`, so the rules stay pure and tested, and this class is what stands under it, as [CareTables] stands under Care's.
 *
 * **One table family per private kind of record** (`mint_account`, `mint_category`, `mint_transaction`, `mint_budget`, `mint_bill`, `mint_bill_payment`,
 * `mint_reconciliation`, `mint_review`, plus `mint_state` for the settings), so a kind can be read ([readKind]), exported or erased ([erase]) on its own; erasing MINT ([clear]) touches
 * nothing outside the `mint_*` tables: not the Pokémon, the Bag, the ledger, Plan or Care.
 *
 * It works on the JSON, not on `MintData`, so `:data` stays independent of the module and a field it has never heard of (a newer build's) lands in
 * `mint_state`, or stays inside its record's `json`, instead of being dropped. A write only touches the rows that changed, in one transaction.
 */
class MintTables(private val store: DataStore) {
    private val q get() = store.db.dataQueries
    private val rows = RecordRows(store)

    /** Whether MINT lives here (set once by [activate]); before that a `mint` row of `live_document`, if any, is all there is. */
    fun isActive(): Boolean = q.meta(META_MINT_TABLES).executeAsOneOrNull() == ACTIVE

    /** The MINT document as it was last written, or null when nothing has been written yet. */
    fun read(): String? = store.transaction { assemble()?.let { JSON.encodeToString(JsonElement.serializer(), it) } }

    /** Saves [document] (the whole MINT document as JSON): the rows that differ are changed, the rest are left alone. */
    fun write(document: String) {
        val parsed = parse(document)
        store.transaction { replaceRows(parsed) }
    }

    /**
     * Starts MINT in the tables: [document] (null on a fresh install, the usual case) is split into rows, every row is read back and the assembled
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
                check(back != null && canonical(back) == canonical(parsed)) { "the Mint records did not read back equal" }
            }
            q.setMeta(META_MINT_TABLES, ACTIVE)
            q.setMeta(META_MINT_TABLES_AT, nowMillis.toString())
        }
        return parsed != null
    }

    /** One kind of record ([MintTables.KINDS] names them) as a JSON array, for export; empty when there is none. */
    fun readKind(field: String): String {
        val slice = SLICES.firstOrNull { it.field == field } ?: error("unknown kind of MINT record: $field")
        return store.transaction { JSON.encodeToString(JsonElement.serializer(), JsonArray(rows.records(slice.table).map { JSON.parseToJsonElement(it.json) })) }
    }

    /** Erases one kind of record (every row of its table family) and nothing else, in one transaction. */
    fun erase(field: String) {
        val slice = SLICES.firstOrNull { it.field == field } ?: error("unknown kind of MINT record: $field")
        store.transaction { store.driver.execute(null, "DELETE FROM ${slice.table}", 0) }
    }

    /** Erases every MINT record and setting (the whole MINT save), in one transaction. Nothing outside the `mint_*` tables is touched. */
    fun clear() {
        store.transaction {
            for (slice in SLICES) store.driver.execute(null, "DELETE FROM ${slice.table}", 0)
            store.driver.execute(null, "DELETE FROM mint_state", 0)
        }
    }

    // --- taking a document apart and putting it back --------------------------------------------------------------------

    private fun replaceRows(document: JsonObject) {
        for (slice in SLICES) rows.diffRecords(slice.table, rowsOf(slice, document[slice.field] as? JsonArray))
        val state = document.filterKeys { key -> SLICES.none { it.field == key && document[key] is JsonArray } }
        rows.diffState("mint_state", state.mapValues { JSON.encodeToString(JsonElement.serializer(), it.value) })
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
        for ((key, json) in rows.state("mint_state")) fields[key] = JSON.parseToJsonElement(json)
        var any = fields.isNotEmpty()
        for (slice in SLICES) {
            val records = rows.records(slice.table).map { JSON.parseToJsonElement(it.json) }
            if (records.isNotEmpty()) { fields[slice.field] = JsonArray(records); any = true }
        }
        return if (any) JsonObject(fields) else null
    }

    private class Slice(val field: String, val table: String, val dayField: String)

    companion object {
        const val META_MINT_TABLES = "mint_store"
        const val META_MINT_TABLES_AT = "mint_store_since"
        const val ACTIVE = "tables"

        private val JSON = Json { encodeDefaults = true }

        private val SLICES = listOf(
            Slice("accounts", "mint_account", "openingDay"),
            Slice("categories", "mint_category", "day"),
            Slice("transactions", "mint_transaction", "day"),
            Slice("budgets", "mint_budget", "day"),
            Slice("bills", "mint_bill", "firstDue"),
            Slice("billPayments", "mint_bill_payment", "dueDay"),
            Slice("reconciliations", "mint_reconciliation", "day"),
            Slice("reviews", "mint_review", "day"),
        )

        /** The document fields that are a kind of private record, each with its own table family. */
        val KINDS: List<String> = SLICES.map { it.field }

        private fun parse(text: String): JsonObject = JSON.parseToJsonElement(text) as? JsonObject ?: error("the Mint document is not a JSON object")

        /** The document with what a read back cannot tell apart left out: an empty list of records is not stored (nothing to put in a table). */
        private fun canonical(document: JsonObject): JsonObject =
            JsonObject(document.filterNot { (key, value) -> SLICES.any { it.field == key } && (value as? JsonArray)?.isEmpty() == true })
    }
}
