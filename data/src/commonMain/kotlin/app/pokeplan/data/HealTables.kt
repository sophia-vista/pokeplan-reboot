package app.pokeplan.data

import app.cash.sqldelight.db.QueryResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class HealTables(private val store: DataStore) {
    private val q get() = store.db.dataQueries
    private val rows = RecordRows(store)

    fun isSealed(): Boolean = q.meta(META_HEAL_SEALED).executeAsOneOrNull() == SEALED

    private fun codec(): RecordRows.Codec? {
        if (!isSealed()) return null
        val c = store.healCipher ?: throw HealLockedException()
        return RecordRows.Codec({ c.seal(it) }, { c.open(it) ?: throw HealLockedException() })
    }

    @Volatile private var openChecked = false

    fun isActive(): Boolean = q.meta(META_HEAL_TABLES).executeAsOneOrNull() == ACTIVE

    fun read(): String? = store.transaction { assemble()?.let { JSON.encodeToString(JsonElement.serializer(), it) } }

    fun write(document: String) {
        val parsed = parse(document)
        store.transaction {
            if (isSealed()) {
                if (store.healCipher == null) throw HealLockedException()
                if (!openChecked) { if (locked()) keepLockedAside(); openChecked = true }
            }
            replaceRows(parsed)
        }
    }

    fun locked(): Boolean {
        if (!isSealed()) return false
        val c = store.healCipher ?: return true
        return SLICES.any { slice -> rows.records(slice.table).any { c.open(it.json) == null } } || rows.state("heal_state").values.any { c.open(it) == null }
    }

    fun readOrNull(): String? = if (locked()) null else read()

    fun snapshot(): String? = readOrNull()?.let { text -> if (isSealed()) (store.healCipher ?: throw HealLockedException()).seal(text) else text }

    private fun keepLockedAside() {
        val raw = JsonObject(SLICES.associate { it.table to JsonArray(rows.records(it.table).map { r -> JsonPrimitive(r.json) }) } + ("heal_state" to JsonArray(rows.state("heal_state").values.map { JsonPrimitive(it) })))
        val text = JSON.encodeToString(JsonElement.serializer(), raw)
        q.liveWrite("aside:heal:locked:" + Sha256.hex(text).take(16), text, 0L)
        for (slice in SLICES) store.driver.execute(null, "DELETE FROM ${slice.table}", 0)
        store.driver.execute(null, "DELETE FROM heal_state", 0)
    }

    fun seal(nowMillis: Long): Boolean {
        if (isSealed() || !isActive()) return false
        val c = store.healCipher ?: return false
        store.transaction {
            val before = assemble()
            for (slice in SLICES) sealTable(slice.table, c)
            sealTable("heal_state", c)
            sealKeptAside(c)
            q.setMeta(META_HEAL_SEALED, SEALED)
            q.setMeta(META_HEAL_SEALED_AT, nowMillis.toString())
            val back = assemble()
            check(back == before) { "the Heal records did not read back equal after sealing" }
            check(SLICES.all { s -> rows.records(s.table).all { RowCipher.isSealed(it.json) && it.day == null } } && rows.state("heal_state").values.all(RowCipher::isSealed)) { "a Heal row is still in clear" }
        }
        return true
    }

    private fun sealTable(table: String, c: RowCipher) {
        val state = table == "heal_state"
        val plain = if (state) rows.state(table).map { it.key to it.value } else rows.records(table).map { it.key to it.json }
        for ((key, json) in plain) {
            if (RowCipher.isSealed(json)) continue
            store.driver.execute(null, if (state) "UPDATE $table SET json = ? WHERE key = ?" else "UPDATE $table SET json = ?, day = NULL WHERE key = ?", 2) { bindString(0, c.seal(json)); bindString(1, key) }
        }
    }

    private fun sealKeptAside(c: RowCipher) {
        val copies = mutableListOf<Triple<String, String, Long>>()
        store.driver.executeQuery(null, "SELECT name, json, updated_at FROM live_document WHERE name LIKE 'aside:heal:%'", { cur ->
            while (cur.next().value) copies += Triple(cur.getString(0)!!, cur.getString(1)!!, cur.getLong(2)!!)
            QueryResult.Unit
        }, 0)
        for ((name, json, at) in copies) if (!name.startsWith("aside:heal:locked:") && !RowCipher.isSealed(json)) q.liveWrite(name, c.seal(json), at)
    }

    fun scrub() {
        runCatching { store.driver.execute(null, "VACUUM", 0) }
        runCatching { store.driver.executeQuery(null, "PRAGMA wal_checkpoint(TRUNCATE)", { cur -> while (cur.next().value) Unit; QueryResult.Unit }, 0) }
    }

    fun activate(document: String?, nowMillis: Long): Boolean {
        if (isActive()) return false
        val parsed = document?.let(::parse)
        store.transaction {
            if (parsed != null) {
                replaceRows(parsed)
                val back = assemble()
                check(back != null && canonical(back) == canonical(parsed)) { "the Heal records did not read back equal" }
            }
            q.setMeta(META_HEAL_TABLES, ACTIVE)
            q.setMeta(META_HEAL_TABLES_AT, nowMillis.toString())
        }
        return parsed != null
    }

    fun readKind(field: String): String {
        val slice = SLICES.firstOrNull { it.field == field } ?: error("unknown kind of HEAL record: $field")
        return store.transaction { JSON.encodeToString(JsonElement.serializer(), JsonArray(rows.records(slice.table, codec()).map { JSON.parseToJsonElement(it.json) })) }
    }

    fun erase(field: String) {
        val slice = SLICES.firstOrNull { it.field == field } ?: error("unknown kind of HEAL record: $field")
        store.transaction { store.driver.execute(null, "DELETE FROM ${slice.table}", 0) }
    }

    fun clear() {
        store.transaction {
            for (slice in SLICES) store.driver.execute(null, "DELETE FROM ${slice.table}", 0)
            store.driver.execute(null, "DELETE FROM heal_state", 0)
        }
    }

    private fun replaceRows(document: JsonObject) {
        val codec = codec()
        for (slice in SLICES) rows.diffRecords(slice.table, rowsOf(slice, document[slice.field] as? JsonArray), codec)
        val state = document.filterKeys { key -> SLICES.none { it.field == key && document[key] is JsonArray } }
        rows.diffState("heal_state", state.mapValues { JSON.encodeToString(JsonElement.serializer(), it.value) }, codec)
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
        val codec = codec()
        for ((key, json) in rows.state("heal_state", codec)) fields[key] = JSON.parseToJsonElement(json)
        var any = fields.isNotEmpty()
        for (slice in SLICES) {
            val records = rows.records(slice.table, codec).map { JSON.parseToJsonElement(it.json) }
            if (records.isNotEmpty()) { fields[slice.field] = JsonArray(records); any = true }
        }
        return if (any) JsonObject(fields) else null
    }

    private class Slice(val field: String, val table: String, val dayField: String)

    companion object {
        const val META_HEAL_TABLES = "heal_store"
        const val META_HEAL_TABLES_AT = "heal_store_since"
        const val META_HEAL_SEALED = "heal_sealed"
        const val META_HEAL_SEALED_AT = "heal_sealed_since"
        const val SEALED = "sealed"
        const val ACTIVE = "tables"

        private val JSON = Json { encodeDefaults = true }

        private val SLICES = listOf(
            Slice("activities", "heal_activity", "day"),
            Slice("hydration", "heal_hydration", "day"),
            Slice("sleep", "heal_sleep", "day"),
            Slice("measurements", "heal_measurement", "day"),
            Slice("goals", "heal_goal", "startDay"),
            Slice("steps", "heal_steps", "day"),
            Slice("vitals", "heal_vital", "day"),
        )

        val KINDS: List<String> = SLICES.map { it.field }

        private fun parse(text: String): JsonObject = JSON.parseToJsonElement(text) as? JsonObject ?: error("the Heal document is not a JSON object")

        private fun canonical(document: JsonObject): JsonObject =
            JsonObject(document.filterNot { (key, value) -> SLICES.any { it.field == key } && (value as? JsonArray)?.isEmpty() == true })
    }
}
