package app.pokeplan.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class MintTables(private val store: DataStore) {
    private val q get() = store.db.dataQueries
    private val rows = RecordRows(store)

    private fun cipher(): RowCipher = store.mintCipher ?: error("Mint's tables need their key (DataStore.mintCipher) before they are used")

    private fun codec() = RecordRows.Codec({ cipher().seal(it) }, { cipher().open(it) ?: throw MintLockedException() })

    @Volatile private var openChecked = false

    fun isActive(): Boolean = q.meta(META_MINT_TABLES).executeAsOneOrNull() == ACTIVE

    fun read(): String? = store.transaction { assemble()?.let { JSON.encodeToString(JsonElement.serializer(), it) } }

    fun write(document: String) {
        val parsed = parse(document)
        store.transaction {
            if (!openChecked) { if (locked()) keepLockedAside(); openChecked = true }
            replaceRows(parsed)
        }
    }

    fun locked(): Boolean {
        val c = store.mintCipher ?: return true
        return SLICES.any { slice -> rows.records(slice.table).any { c.open(it.json) == null } } || rows.state("mint_state").values.any { c.open(it) == null }
    }

    fun readOrNull(): String? = if (locked()) null else read()

    fun sealedSnapshot(): String? = readOrNull()?.let { cipher().seal(it) }

    private fun keepLockedAside() {
        val raw = JsonObject(SLICES.associate { it.table to JsonArray(rows.records(it.table).map { r -> JsonPrimitive(r.json) }) } + ("mint_state" to JsonArray(rows.state("mint_state").values.map { JsonPrimitive(it) })))
        val text = JSON.encodeToString(JsonElement.serializer(), raw)
        store.db.dataQueries.liveWrite("aside:mint:locked:" + Sha256.hex(text).take(16), text, 0L)
        for (slice in SLICES) store.driver.execute(null, "DELETE FROM ${slice.table}", 0)
        store.driver.execute(null, "DELETE FROM mint_state", 0)
    }

    fun activate(document: String?, nowMillis: Long): Boolean {
        if (isActive()) return false
        cipher()
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

    fun readKind(field: String): String {
        val slice = SLICES.firstOrNull { it.field == field } ?: error("unknown kind of MINT record: $field")
        return store.transaction { JSON.encodeToString(JsonElement.serializer(), JsonArray(rows.records(slice.table, codec()).map { JSON.parseToJsonElement(it.json) })) }
    }

    fun erase(field: String) {
        val slice = SLICES.firstOrNull { it.field == field } ?: error("unknown kind of MINT record: $field")
        store.transaction { store.driver.execute(null, "DELETE FROM ${slice.table}", 0) }
    }

    fun clear() {
        store.transaction {
            for (slice in SLICES) store.driver.execute(null, "DELETE FROM ${slice.table}", 0)
            store.driver.execute(null, "DELETE FROM mint_state", 0)
        }
    }

    private fun replaceRows(document: JsonObject) {
        for (slice in SLICES) rows.diffRecords(slice.table, rowsOf(slice, document[slice.field] as? JsonArray), codec())
        val state = document.filterKeys { key -> SLICES.none { it.field == key && document[key] is JsonArray } }
        rows.diffState("mint_state", state.mapValues { JSON.encodeToString(JsonElement.serializer(), it.value) }, codec())
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
        for ((key, json) in rows.state("mint_state", codec())) fields[key] = JSON.parseToJsonElement(json)
        var any = fields.isNotEmpty()
        for (slice in SLICES) {
            val records = rows.records(slice.table, codec()).map { JSON.parseToJsonElement(it.json) }
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

        val KINDS: List<String> = SLICES.map { it.field }

        private fun parse(text: String): JsonObject = JSON.parseToJsonElement(text) as? JsonObject ?: error("the Mint document is not a JSON object")

        private fun canonical(document: JsonObject): JsonObject =
            JsonObject(document.filterNot { (key, value) -> SLICES.any { it.field == key } && (value as? JsonArray)?.isEmpty() == true })
    }
}
