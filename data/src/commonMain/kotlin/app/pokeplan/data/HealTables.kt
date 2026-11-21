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
        store.transaction {
            if (isSealed()) {
                if (store.healCipher == null) throw HealLockedException() // no key: nothing is written (fail closed)
                if (!openChecked) { if (locked()) keepLockedAside(); openChecked = true }
            }
            replaceRows(parsed)
        }
    }

    /**
     * Whether any stored row of Heal is sealed and cannot be opened with this phone's key (a database copied from another phone, or no key on this run). Always false
     * while the rows are in clear. It never throws, so it is safe inside a transaction (an exception inside a nested one would roll the outer one back).
     */
    fun locked(): Boolean {
        if (!isSealed()) return false
        val c = store.healCipher ?: return true
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

    /** One kind of record ([HealTables.KINDS] names them) as a JSON array, for export; empty when there is none. */
    fun readKind(field: String): String {
        val slice = SLICES.firstOrNull { it.field == field } ?: error("unknown kind of HEAL record: $field")
        return store.transaction { JSON.encodeToString(JsonElement.serializer(), JsonArray(rows.records(slice.table, codec()).map { JSON.parseToJsonElement(it.json) })) }
    }

    /** Erases one kind of record (every row of its table family) and nothing else, in one transaction. */
    fun erase(field: String) {
        val slice = SLICES.firstOrNull { it.field == field } ?: error("unknown kind of HEAL record: $field")
        store.transaction { store.driver.execute(null, "DELETE FROM ${slice.table}", 0) }
    }

    /** Erases every HEAL record and setting (the whole HEAL save), in one transaction. Nothing outside the `heal_*` tables is touched. */
    fun clear() {
        store.transaction {
            for (slice in SLICES) store.driver.execute(null, "DELETE FROM ${slice.table}", 0)
            store.driver.execute(null, "DELETE FROM heal_state", 0)
        }
    }

    // --- taking a document apart and putting it back --------------------------------------------------------------------

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

        /** The document fields that are a kind of private record, each with its own table family. */
        val KINDS: List<String> = SLICES.map { it.field }

        private fun parse(text: String): JsonObject = JSON.parseToJsonElement(text) as? JsonObject ?: error("the Heal document is not a JSON object")

        /** The document with what a read back cannot tell apart left out: an empty list of records is not stored (nothing to put in a table). */
        private fun canonical(document: JsonObject): JsonObject =
            JsonObject(document.filterNot { (key, value) -> SLICES.any { it.field == key } && (value as? JsonArray)?.isEmpty() == true })
    }
}
