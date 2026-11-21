package app.pokeplan.data

import app.cash.sqldelight.db.QueryResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * HEAL's records in their own tables from the start (schema version 6, REP-014; docs/pokelife `MODULES/HEAL.md` section 14 and
 * `ARCHITECTURE/DATA_AND_MIGRATIONS.md` section 7.4). HEAL is the first module that never had a document row: the app reads and writes it as one JSON document,
 * `HealData`, so the rules stay pure and tested, and this class is what stands under it, as [CareTables] stands under Care's.
 *
 * **One table family per private kind of record** (`heal_activity`, `heal_hydration`, `heal_sleep`, `heal_measurement`, `heal_goal`, `heal_steps` and `heal_vital` (schema 11, 0.186.0), plus
 * `heal_state` for the settings), so a kind can be read ([readKind]), exported or erased ([erase]) on its own; erasing HEAL ([clear]) touches
 * nothing outside the `heal_*` tables: not the Pokémon, the Bag, the ledger, Plan or Care.
 *
 * **Sealed since 0.154.0** (owner decision, 8 November 2026; `DATA_AND_MIGRATIONS.md` section 10.4.1): Heal's records are the most personal data after Mint's, so
 * [seal] rewrites every row as AES-GCM text under the phone's Keystore key (`DataStore.healCipher`), once, with a read-back, and from then on a row holds neither a
 * measurement, a sleep time nor a date in clear, and every copy of Heal that leaves the tables (a backup, a kept-aside document) is sealed too. Before [seal] has
 * run (an install older than 0.154.0, or a phone whose Keystore gave no key) the rows are read and written in clear exactly as before.
 *
 * It works on the JSON, not on `HealData`, so `:data` stays independent of the module and a field it has never heard of (a newer build's) lands in
 * `heal_state`, or stays inside its record's `json`, instead of being dropped. A write only touches the rows that changed, in one transaction.
 */
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
    private fun validCache(): Cache? = cache?.takeIf { it.sealed == isSealed() && it.cipher === store.healCipher && it.fingerprint == fingerprint() }

    private fun remember(records: Map<String, List<RecordRows.Row>>, state: Map<String, String>) {
        cache = Cache(isSealed(), store.healCipher, fingerprint(), records, state)
    }

    /** Whether the rows are sealed (set once by [seal]); the marker is never cleared, not even by [clear]. */
    fun isSealed(): Boolean = q.meta(META_HEAL_SEALED).executeAsOneOrNull() == SEALED

    /** The codec for the stored rows: none while they are in clear; once sealed, the key is required and a row it cannot open raises [HealLockedException]. */
    private fun codec(): RecordRows.Codec? {
        if (!isSealed()) return null
        val c = store.healCipher ?: throw HealLockedException()
        return RecordRows.Codec({ c.seal(it) }, { c.open(it) ?: throw HealLockedException() })
    }

    @Volatile private var openChecked = false

    /** Whether HEAL lives here (set once by [activate]); before that a `heal` row of `live_document`, if any, is all there is. */
    fun isActive(): Boolean = q.meta(META_HEAL_TABLES).executeAsOneOrNull() == ACTIVE

    /** The HEAL document as it was last written, or null when nothing has been written yet. */
    fun read(): String? = store.transaction { assemble()?.let { JSON.encodeToString(JsonElement.serializer(), it) } }

    /** Saves [document] (the whole HEAL document as JSON): the rows that differ are changed, the rest are left alone. */
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

    /**
     * Whether any stored row of Heal is sealed and cannot be opened with this phone's key (a database copied from another phone, or no key on this run). Always false
     * while the rows are in clear. It never throws, so it is safe inside a transaction (an exception inside a nested one would roll the outer one back).
     */
    fun locked(): Boolean {
        if (!isSealed()) return false
        val c = store.healCipher ?: return true
        if (validCache() != null) return false // the rows were opened with this key and are unchanged since
        return SLICES.any { slice -> rows.records(slice.table).any { c.open(it.json) == null } } || rows.state("heal_state").values.any { c.open(it) == null }
    }

    /** Heal's document, or null when it is empty or locked: the callers that must not fail inside a transaction (or that treat a locked Heal as empty) use this. */
    fun readOrNull(): String? = if (locked()) null else read()

    /**
     * Heal as one text for a backup or a kept-aside copy: sealed again as a whole once the rows are sealed (so a daily backup or a backup file holds no figure or
     * date in clear and opens only on a phone that has the key), as it is before. Null when Heal is empty or locked.
     */
    fun snapshot(): String? = readOrNull()?.let { text -> if (isSealed()) (store.healCipher ?: throw HealLockedException()).seal(text) else text }

    /**
     * Sets aside what cannot be opened, still sealed, before the tables are written again, so an empty Heal on a phone without the key never overwrites the only
     * copy of the records: the rows go to the `aside:heal:locked:<hash>` live document and the tables start empty.
     */
    private fun keepLockedAside() {
        cache = null
        val raw = JsonObject(SLICES.associate { it.table to JsonArray(rows.records(it.table).map { r -> JsonPrimitive(r.json) }) } + ("heal_state" to JsonArray(rows.state("heal_state").values.map { JsonPrimitive(it) })))
        val text = JSON.encodeToString(JsonElement.serializer(), raw)
        q.liveWrite("aside:heal:locked:" + Sha256.hex(text).take(16), text, 0L)
        for (slice in SLICES) store.driver.execute(null, "DELETE FROM ${slice.table}", 0)
        store.driver.execute(null, "DELETE FROM heal_state", 0)
    }

    /**
     * Seals Heal, once (0.154.0): every row of every `heal_*` table is rewritten as sealed text (its date column emptied, the date lives inside the sealed record),
     * every plaintext copy of Heal kept aside by a migration or a restore (`aside:heal:*`) is sealed, and the marker is set, all in one transaction that reads the
     * whole document back with the key and compares it with what was there; any difference rolls everything back and Heal stays as it was. Returns whether it
     * sealed; already sealed, not yet active or no key does nothing (a start without the key tries again at the next one).
     */
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

    /** Rewrites every row of [table] (a record table or the state table) as sealed text. */
    private fun sealTable(table: String, c: RowCipher) {
        val state = table == "heal_state"
        val plain = if (state) rows.state(table).map { it.key to it.value } else rows.records(table).map { it.key to it.json }
        for ((key, json) in plain) {
            if (RowCipher.isSealed(json)) continue
            store.driver.execute(null, if (state) "UPDATE $table SET json = ? WHERE key = ?" else "UPDATE $table SET json = ?, day = NULL WHERE key = ?", 2) { bindString(0, c.seal(json)); bindString(1, key) }
        }
    }

    /** Seals the plaintext copies of Heal that a migration or a restore kept in `live_document` (the ones already sealed, or set aside locked, are left alone). */
    private fun sealKeptAside(c: RowCipher) {
        val copies = mutableListOf<Triple<String, String, Long>>()
        store.driver.executeQuery(null, "SELECT name, json, updated_at FROM live_document WHERE name LIKE 'aside:heal:%'", { cur ->
            while (cur.next().value) copies += Triple(cur.getString(0)!!, cur.getString(1)!!, cur.getLong(2)!!)
            QueryResult.Unit
        }, 0)
        for ((name, json, at) in copies) if (!name.startsWith("aside:heal:locked:") && !RowCipher.isSealed(json)) q.liveWrite(name, c.seal(json), at)
    }

    /**
     * After [seal]: forgets what the old plaintext rows left in the file's free pages and write-ahead log (VACUUM, then a truncating checkpoint). Best effort and
     * outside any transaction; a failure changes nothing about the data.
     */
    fun scrub() {
        runCatching { store.driver.execute(null, "VACUUM", 0) }
        runCatching { store.driver.executeQuery(null, "PRAGMA wal_checkpoint(TRUNCATE)", { cur -> while (cur.next().value) Unit; QueryResult.Unit }, 0) }
    }

    /**
     * Starts HEAL in the tables: [document] (null on a fresh install, the usual case) is split into rows, every row is read back and the assembled
     * document is compared with the one given; the marker is set only when they are equal, all in one transaction, so a difference leaves the
     * database exactly as it was. Returns whether it imported a document; already active does nothing.
     */
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

    /** One kind of record ([HealTables.KINDS] names them) as a JSON array, for export; empty when there is none. */
    fun readKind(field: String): String {
        val slice = SLICES.firstOrNull { it.field == field } ?: error("unknown kind of HEAL record: $field")
        return store.transaction { JSON.encodeToString(JsonElement.serializer(), JsonArray(rows.records(slice.table, codec()).map { JSON.parseToJsonElement(it.json) })) }
    }

    /** Erases one kind of record (every row of its table family) and nothing else, in one transaction. */
    fun erase(field: String) {
        val slice = SLICES.firstOrNull { it.field == field } ?: error("unknown kind of HEAL record: $field")
        cache = null
        store.transaction { store.driver.execute(null, "DELETE FROM ${slice.table}", 0) }
    }

    /** Erases every HEAL record and setting (the whole HEAL save), in one transaction. Nothing outside the `heal_*` tables is touched. */
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

        /** The document fields that are a kind of private record, each with its own table family. */
        val KINDS: List<String> = SLICES.map { it.field }

        private fun parse(text: String): JsonObject = JSON.parseToJsonElement(text) as? JsonObject ?: error("the Heal document is not a JSON object")

        /** The document with what a read back cannot tell apart left out: an empty list of records is not stored (nothing to put in a table). */
        private fun canonical(document: JsonObject): JsonObject =
            JsonObject(document.filterNot { (key, value) -> SLICES.any { it.field == key } && (value as? JsonArray)?.isEmpty() == true })
    }
}
