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

    /**
     * Heal's rows as they were last written or read, opened (plain text, in memory only: the same figures the app already holds as its Heal document), so a save need not
     * open every sealed row to learn which ones changed (owner, 21 November 2026: with hundreds of history rows each save took seconds on a phone, one Android Keystore
     * call per row). It is used only while it provably still matches the database: the same sealed state, the same key object, and every table's row count and total
     * text length as when it was made; anything else, and every change made outside [write], throws it away and the rows are opened as before. Nothing is cached
     * for a locked Heal, and nothing sealed is ever written differently.
     */
    private class Cache(val sealed: Boolean, val cipher: Any?, val fingerprint: List<Pair<Long, Long>>, val records: Map<String, List<RecordRows.Row>>, val state: Map<String, String>)

    @Volatile private var cache: Cache? = null

    private val tableNames = SLICES.map { it.table } + "heal_state"

    /** Each table's row count and total text length: cheap, and it needs no key. */
    private fun fingerprint(): List<Pair<Long, Long>> = tableNames.map { t ->
        var n = 0L; var len = 0L
        store.driver.executeQuery(null, "SELECT COUNT(*), COALESCE(SUM(LENGTH(json)), 0) FROM $t", { c -> if (c.next().value) { n = c.getLong(0) ?: 0L; len = c.getLong(1) ?: 0L }; QueryResult.Unit }, 0)
        n to len
    }

    /** The cache, only if it still matches the database and the key; otherwise null. */
    private fun validCache(): Cache? {
        val kept = cache ?: return null
        if (kept.sealed == isSealed() && kept.cipher === store.healCipher && kept.fingerprint == fingerprint()) return kept
        // No longer provably the same rows (another key, or a change behind the cache's back): drop the opened copy instead of keeping plain text around.
        // The fingerprint cannot see a rewrite of the same length; the one connection and [write] being the only writer of these tables is what rules that out.
        cache = null
        return null
    }

    private fun remember(records: Map<String, List<RecordRows.Row>>, state: Map<String, String>) {
        cache = Cache(isSealed(), store.healCipher, fingerprint(), records, state)
    }

    /** Whether the rows are sealed (set once by [seal]); the marker is never cleared, not even by [clear]. */
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
        var pending: Pair<Map<String, List<RecordRows.Row>>, Map<String, String>>? = null
        try {
            store.transaction {
                if (isSealed()) {
                    if (store.healCipher == null) throw HealLockedException() // no key: nothing is written (fail closed)
                    if (!openChecked) { if (locked()) keepLockedAside(); openChecked = true }
                }
                pending = replaceRows(parsed)
            }
        } catch (e: Throwable) { cache = null; throw e }
        pending?.let { (records, state) -> remember(records, state) }
    }

    fun locked(): Boolean {
        if (!isSealed()) return false
        val c = store.healCipher ?: return true
        if (validCache() != null) return false // the rows were opened with this key and are unchanged since
        return SLICES.any { slice -> rows.records(slice.table).any { c.open(it.json) == null } } || rows.state("heal_state").values.any { c.open(it) == null }
    }

    /**
     * Heal's document, or null when it is empty or locked: the callers that must not fail inside a transaction (or that treat a locked Heal as empty) use this.
     * The read itself opens every row, so a row this phone's key cannot open is reported as null (caught inside the transaction, so a transaction around this call is not rolled back); a separate
     * `locked()` pass would decrypt every row a second time (and each open is a Keystore round trip).
     */
    fun readOrNull(): String? = store.transaction {
        // Caught inside the transaction body: an exception leaving a (nested) transaction would roll back the one around it.
        try {
            assemble()?.let { JSON.encodeToString(JsonElement.serializer(), it) }
        } catch (e: HealLockedException) {
            null
        }
    }

    fun snapshot(): String? = readOrNull()?.let { text -> if (isSealed()) (store.healCipher ?: throw HealLockedException()).seal(text) else text }

    private fun keepLockedAside() {
        cache = null
        val raw = JsonObject(SLICES.associate { it.table to JsonArray(rows.records(it.table).map { r -> JsonPrimitive(r.json) }) } + ("heal_state" to JsonArray(rows.state("heal_state").values.map { JsonPrimitive(it) })))
        val text = JSON.encodeToString(JsonElement.serializer(), raw)
        q.liveWrite("aside:heal:locked:" + Sha256.hex(text).take(16), text, 0L)
        for (slice in SLICES) store.driver.execute(null, "DELETE FROM ${slice.table}", 0)
        store.driver.execute(null, "DELETE FROM heal_state", 0)
    }

    fun seal(nowMillis: Long): Boolean {
        if (isSealed() || !isActive()) return false
        val c = store.healCipher ?: return false
        cache = null
        store.transaction {
            val before = assemble(fresh = true)
            for (slice in SLICES) sealTable(slice.table, c)
            sealTable("heal_state", c)
            sealKeptAside(c)
            q.setMeta(META_HEAL_SEALED, SEALED)
            q.setMeta(META_HEAL_SEALED_AT, nowMillis.toString())
            val back = assemble(fresh = true)
            check(back == before) { "the Heal records did not read back equal after sealing" }
            check(SLICES.all { s -> rows.records(s.table).all { RowCipher.isSealed(it.json) && it.day == null } } && rows.state("heal_state").values.all(RowCipher::isSealed)) { "a Heal row is still in clear" }
        }
        cache = null
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
        cache = null
        val parsed = document?.let(::parse)
        store.transaction {
            if (parsed != null) {
                replaceRows(parsed)
                val back = assemble(fresh = true)
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
        cache = null
        store.transaction { store.driver.execute(null, "DELETE FROM ${slice.table}", 0) }
    }

    fun clear() {
        cache = null
        store.transaction {
            for (slice in SLICES) store.driver.execute(null, "DELETE FROM ${slice.table}", 0)
            store.driver.execute(null, "DELETE FROM heal_state", 0)
        }
    }

    // --- taking a document apart and putting it back --------------------------------------------------------------------

    /** Writes the rows that differ and returns what the tables now hold (opened), for [remember]; the caller keeps it only once the transaction has committed. */
    private fun replaceRows(document: JsonObject): Pair<Map<String, List<RecordRows.Row>>, Map<String, String>> {
        val codec = codec()
        val known = validCache()
        val records = LinkedHashMap<String, List<RecordRows.Row>>()
        for (slice in SLICES) {
            val wanted = rowsOf(slice, document[slice.field] as? JsonArray)
            rows.diffRecords(slice.table, wanted, codec, known?.records?.get(slice.table)?.associateBy { it.key })
            // As [RecordRows.records] would give them back: a sealed row keeps no date beside it.
            records[slice.table] = wanted.map { RecordRows.Row(it.key, it.position, if (codec == null) it.day else null, it.json) }
        }
        val state = document.filterKeys { key -> SLICES.none { it.field == key && document[key] is JsonArray } }.mapValues { JSON.encodeToString(JsonElement.serializer(), it.value) }
        rows.diffState("heal_state", state, codec, known?.state)
        return records to state
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

    private fun assemble(fresh: Boolean = false): JsonObject? {
        val fields = mutableMapOf<String, JsonElement>()
        val have = if (fresh) null else validCache()
        val codec = codec()
        val state = have?.state ?: rows.state("heal_state", codec)
        for ((key, json) in state) fields[key] = JSON.parseToJsonElement(json)
        var any = fields.isNotEmpty()
        val read = LinkedHashMap<String, List<RecordRows.Row>>()
        for (slice in SLICES) {
            val list = have?.records?.get(slice.table) ?: rows.records(slice.table, codec)
            read[slice.table] = list
            val records = list.map { JSON.parseToJsonElement(it.json) }
            if (records.isNotEmpty()) { fields[slice.field] = JsonArray(records); any = true }
        }
        // Opened without error (a row that cannot be opened throws above), so the rows are known: keep them for the next save and read.
        if (have == null && !fresh) remember(read, state)
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
