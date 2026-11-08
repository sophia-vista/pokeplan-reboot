package app.pokeplan.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** What activation did, for the caller to log or show. */
sealed interface Activation {
    /** The database was already the live store; nothing was imported. */
    data object AlreadyActive : Activation

    /** The legacy documents were copied in, read back equal, and the marker was set. */
    data class Imported(val documents: List<String>) : Activation
}

/**
 * The live save inside the database (DATA_AND_MIGRATIONS.md section 12.6): the app's own JSON documents, written one
 * transaction at a time. Once [isActive] the database is the only authority and the legacy files are never written again
 * (they stay as the rollback copy). There is no dual write: before activation nothing here is read, after it nothing writes
 * the files.
 */
class LiveDocuments(private val store: DataStore) {
    private val q get() = store.db.dataQueries

    /**
     * The app's own check of a save (what `GameStore` does to every save it loads: numbers back in range, the Poké merge, one place per
     * Pokémon, habits merged), `(document, nowMillis) -> document`, set by the app so `:data` does not depend on its model. It runs on a save
     * that did not come from the app's own writes: the one being moved into the Pokémon tables and a restored backup. Without it those
     * documents are taken as they are, and a save the tables cannot represent is refused.
     */
    @Volatile var normalizer: ((String, Long) -> String)? = null

    fun isActive(): Boolean = q.meta(META_LIVE).executeAsOneOrNull() == LIVE_DATABASE

    /** Whether [name] is kept in tables rather than as a row (Care, once [moveCareToTables] has run). */
    private fun inTables(name: String): Boolean = (name == CARE && store.careTables.isActive()) || (name == HEAL && store.healTables.isActive()) || (name == MINT && store.mintTables.isActive()) || (name == HOME && store.homeTables.isActive()) || (name == LINK && store.linkTables.isActive())

    /** Whether the PLAN part of [name] is kept in tables (the save, once [movePlanToTables] has run); the rest of it stays a row. */
    private fun planSplit(name: String): Boolean = name == SAVE && store.planTables.isActive()

    /** Whether the player's part of [name] is kept in the shared Pokémon, Bag and ledger tables too (once [movePokemonToTables] has run). */
    private fun pokemonSplit(name: String): Boolean = planSplit(name) && store.pokemonTables.isActive()

    fun read(name: String): String? = when {
        name == CARE && inTables(name) -> store.careTables.read()
        name == HEAL && inTables(name) -> store.healTables.readOrNull()
        name == MINT && inTables(name) -> store.mintTables.readOrNull()
        name == HOME && inTables(name) -> store.homeTables.read()
        name == LINK && inTables(name) -> store.linkTables.read()
        planSplit(name) -> store.transaction { assembleSave() }
        else -> q.liveRead(name).executeAsOneOrNull()
    }

    /** The save put back together: its row (what is neither PLAN's nor the player's) with PLAN's fields and the player's from the tables. */
    private fun assembleSave(): String? {
        val rest = q.liveRead(SAVE).executeAsOneOrNull()?.let { JSON.parseToJsonElement(it) as? JsonObject }
        val plan = store.planTables.read()
        val player = if (store.pokemonTables.isActive()) store.pokemonTables.read() else JsonObject(emptyMap())
        if (rest == null && plan.isEmpty() && player.isEmpty()) return null
        return JSON.encodeToString(JsonObject.serializer(), JsonObject((rest ?: JsonObject(emptyMap())) + plan + player))
    }

    /** One document, one transaction. A document kept in tables is split into its rows, and only the rows that changed are written. */
    fun write(name: String, json: String, nowMillis: Long) {
        when {
            name == CARE && inTables(name) -> store.careTables.write(json)
            name == HEAL && inTables(name) -> store.healTables.write(json)
            name == MINT && inTables(name) -> store.mintTables.write(json)
            name == HOME && inTables(name) -> store.homeTables.write(json)
            name == LINK && inTables(name) -> store.linkTables.write(json)
            pokemonSplit(name) -> store.transaction {
                val (plan, rest0) = store.planTables.split(json)
                val (part, rest) = store.pokemonTables.split(rest0)
                store.planTables.write(plan)
                store.pokemonTables.write(part, rest, nowMillis)
                writeRest(rest, nowMillis)
            }
            planSplit(name) -> store.transaction {
                val (plan, rest) = store.planTables.split(json)
                store.planTables.write(plan)
                q.liveWrite(name, JSON.encodeToString(JsonObject.serializer(), rest), nowMillis)
            }
            else -> store.transaction { q.liveWrite(name, json, nowMillis) }
        }
    }

    /**
     * Moves PLAN's part of the save from its row into PLAN's tables (REP-014), once: the rows are written and read back equal to the
     * document, the marker is set, the whole old row is kept under `aside:save:plan:<millis>` (never deleted) and the `save` row keeps
     * only what is not PLAN's, all in one transaction, so a failure leaves the save row as the live save. Does nothing once done, so it
     * can run on every start.
     */
    fun movePlanToTables(nowMillis: Long) {
        if (store.planTables.isActive()) return
        store.transaction {
            val doc = q.liveRead(SAVE).executeAsOneOrNull()
            val rest = store.planTables.activate(doc, nowMillis)
            if (doc != null && rest != null) {
                q.liveWrite("${ASIDE}$SAVE:plan:$nowMillis", doc, nowMillis)
                q.liveWrite(SAVE, JSON.encodeToString(JsonObject.serializer(), rest), nowMillis)
            }
        }
    }

    /** The save row, only when it changed (a write that touched one task need not rewrite World's data). */
    private fun writeRest(rest: JsonObject, nowMillis: Long) {
        val text = JSON.encodeToString(JsonObject.serializer(), rest)
        if (q.liveRead(SAVE).executeAsOneOrNull() != text) q.liveWrite(SAVE, text, nowMillis)
    }

    /**
     * Moves the player's side of the save into the shared Pokémon, Bag and ledger tables (REP-014, schema 6), once, after PLAN's move. The save is
     * first passed through [normalizer] (the same check the app gives every save it loads), then written as rows; the balances become one
     * opening-balance event and nothing is paid. The whole save is read back and compared with the normalized one, the marker is set, the old
     * save is kept under `aside:save:pokemon:<millis>` (never deleted), and the `save` row keeps only what is neither PLAN's nor the player's,
     * all in one transaction: any difference (or a save the tables cannot represent) rolls everything back and the rows stay the live save.
     * Does nothing once done, so it can run on every start.
     */
    fun movePokemonToTables(nowMillis: Long) {
        if (store.pokemonTables.isActive() || !store.planTables.isActive()) return
        store.transaction {
            val doc = assembleSave()
            if (doc == null) {
                store.pokemonTables.beginEmpty(nowMillis)
                return@transaction
            }
            val normalized = normalizer?.invoke(doc, nowMillis) ?: doc
            q.liveWrite("${ASIDE}$SAVE:pokemon:$nowMillis", doc, nowMillis)
            val (plan, rest0) = store.planTables.split(normalized)
            val (part, rest) = store.pokemonTables.split(rest0)
            store.planTables.write(plan)
            store.pokemonTables.begin(part, rest, nowMillis)
            writeRest(rest, nowMillis)
            val back = JSON.parseToJsonElement(assembleSave()!!) as JsonObject
            check(canonical(back) == canonical(JSON.parseToJsonElement(normalized) as JsonObject)) { "the Pokémon, Bag and Poké tables did not read back equal" }
            val problems = store.economy.reconcile()
            check(problems.isEmpty()) { problems.joinToString("; ") }
        }
    }

    /**
     * Moves the Care document from its row into Care's tables (REP-014), once: the rows are written and read back equal to the
     * document, the marker is set, and the old row is kept under `aside:care:tables:<millis>` (never deleted), all in one
     * transaction, so a failure leaves the document row as the live Care save. Does nothing once done, so it can run on every start.
     */
    fun moveCareToTables(nowMillis: Long) {
        if (store.careTables.isActive()) return
        store.transaction {
            val doc = q.liveRead(CARE).executeAsOneOrNull()
            store.careTables.activate(doc, nowMillis)
            if (doc != null) {
                q.liveWrite("${ASIDE}$CARE:tables:$nowMillis", doc, nowMillis)
                q.liveDelete(CARE)
            }
        }
    }

    /**
     * Starts HEAL in its tables (REP-014), once. HEAL has no earlier home, so on a normal start there is nothing to move: the marker is set and every later
     * `write("heal")` goes to the `heal_*` rows. A `heal` document row that already exists (a backup restored before this ran) is imported, read back equal, and kept
     * aside under `aside:heal:tables:<millis>`. A failure leaves the database as it was and is tried again at the next start. Does nothing once done.
     */
    fun moveHealToTables(nowMillis: Long) {
        if (store.healTables.isActive()) return
        store.transaction {
            val doc = q.liveRead(HEAL).executeAsOneOrNull()
            store.healTables.activate(doc, nowMillis)
            if (doc != null) {
                q.liveWrite("${ASIDE}$HEAL:tables:$nowMillis", doc, nowMillis)
                q.liveDelete(HEAL)
            }
        }
    }

    /**
     * Starts MINT in its tables (REP-014), once, exactly as [moveHealToTables] does for HEAL: nothing to move on a normal start, a `mint` document row left by a
     * restore is imported, read back equal and kept aside under `aside:mint:tables:<millis>`. A failure leaves the database as it was. Does nothing once done.
     */
    fun moveMintToTables(nowMillis: Long) {
        if (store.mintTables.isActive()) return
        store.transaction {
            val doc = q.liveRead(MINT).executeAsOneOrNull()
            store.mintTables.activate(doc, nowMillis)
            if (doc != null) {
                q.liveWrite("${ASIDE}$MINT:tables:$nowMillis", store.mintCipher!!.seal(doc), nowMillis)
                q.liveDelete(MINT)
            }
        }
    }

    /**
     * Starts LINK in its tables (REP-014), once, exactly as [moveHomeToTables] does for HOME: nothing to move on a normal start, a `link` document row left by a
     * restore is imported, read back equal and kept aside under `aside:link:tables:<millis>`. A failure leaves the database as it was. Does nothing once done.
     */
    fun moveLinkToTables(nowMillis: Long) {
        if (store.linkTables.isActive()) return
        store.transaction {
            val doc = q.liveRead(LINK).executeAsOneOrNull()
            store.linkTables.activate(doc, nowMillis)
            if (doc != null) {
                q.liveWrite("${ASIDE}$LINK:tables:$nowMillis", doc, nowMillis)
                q.liveDelete(LINK)
            }
        }
    }

    /**
     * Starts HOME in its tables (REP-014), once, exactly as [moveHealToTables] does for HEAL: nothing to move on a normal start, a `home` document row left by a
     * restore is imported, read back equal and kept aside under `aside:home:tables:<millis>`. A failure leaves the database as it was. Does nothing once done.
     */
    fun moveHomeToTables(nowMillis: Long) {
        if (store.homeTables.isActive()) return
        store.transaction {
            val doc = q.liveRead(HOME).executeAsOneOrNull()
            store.homeTables.activate(doc, nowMillis)
            if (doc != null) {
                q.liveWrite("${ASIDE}$HOME:tables:$nowMillis", doc, nowMillis)
                q.liveDelete(HOME)
            }
        }
    }

    /** Keeps a document that could not be read where [write] will not replace it. */
    fun keepAside(name: String, json: String, nowMillis: Long) {
        write("$ASIDE$name:$nowMillis", asideText(name, json), nowMillis)
    }

    /**
     * A document set aside, as it may be stored: Mint's always sealed, Heal's sealed once its rows are (a sealed Heal with no key refuses rather than store a copy in
     * clear), everything else as it is.
     */
    private fun asideText(name: String, json: String): String = when {
        name == MINT -> store.mintCipher!!.seal(json)
        name == HEAL && store.healTables.isSealed() -> (store.healCipher ?: throw HealLockedException()).seal(json)
        else -> json
    }

    /** The key that opens [name] when a backup carries it sealed (the documents that are sealed to the phone), or null. */
    private fun cipherFor(name: String): RowCipher? = when (name) {
        MINT -> store.mintCipher
        HEAL -> store.healCipher
        else -> null
    }

    /**
     * What a full-device erase leaves behind once the stores have written their blank state: every copy kept aside by a migration or a
     * restore (they hold the old documents whole) and every held reminder. Not the live documents (the stores own those) and not the files
     * of the daily backups (the platform deletes those). One transaction.
     */
    fun eraseKeptCopiesAndHolds() = store.transaction {
        q.liveDeleteAside()
        q.holdDeleteAll()
    }

    fun names(): List<String> = (q.liveNames().executeAsList() + listOfNotNull(CARE.takeIf { store.careTables.isActive() && store.careTables.read() != null }, HEAL.takeIf { store.healTables.isActive() && store.healTables.readOrNull() != null }, MINT.takeIf { store.mintTables.isActive() && store.mintTables.readOrNull() != null }, HOME.takeIf { store.homeTables.isActive() && store.homeTables.read() != null }, LINK.takeIf { store.linkTables.isActive() && store.linkTables.read() != null })).distinct().sorted()

    /** Every live document (not the kept-aside ones) as of one moment: a single read transaction. Care's tables are put back together as `care`. */
    fun snapshot(): Map<String, String> = store.transaction {
        val docs = q.liveNames().executeAsList().filter { !it.startsWith(ASIDE) }.associateWith { q.liveRead(it).executeAsOne() }.toMutableMap()
        if (store.careTables.isActive()) store.careTables.read()?.let { docs[CARE] = it }
        // Heal leaves the tables sealed once its rows are (0.152.0), like Mint, so a backup holds no measurement, time or date in clear.
        if (store.healTables.isActive()) store.healTables.snapshot()?.let { docs[HEAL] = it }
        if (store.homeTables.isActive()) store.homeTables.read()?.let { docs[HOME] = it }
        if (store.linkTables.isActive()) store.linkTables.read()?.let { docs[LINK] = it }
        // Mint leaves the tables only sealed: a backup (a daily copy or a file) never holds a figure, a name or a date in clear.
        if (store.mintTables.isActive()) store.mintTables.sealedSnapshot()?.let { docs[MINT] = it }
        if (store.planTables.isActive()) assembleSave()?.let { docs[SAVE] = it }
        docs
    }

    /**
     * Restores [docs] (a backup): in one transaction the documents being replaced are first kept aside, so a restore can itself
     * be undone, then the backup's documents are written. Documents not in [docs] are left alone.
     */
    fun replaceAll(docs: Map<String, String>, nowMillis: Long) {
        store.transaction {
            for ((name, json0) in docs) {
                read(name)?.let { q.liveWrite("$ASIDE$name:$nowMillis", asideText(name, it), nowMillis) }
                // Mint arrives sealed (see snapshot), and so does Heal since 0.152.0. One sealed with another phone's key (or with no key on this run) stays sealed in the
                // aside and changes nothing; a plain one (an older build's) is written.
                val json = if ((name == MINT || name == HEAL) && RowCipher.isSealed(json0)) {
                    cipherFor(name)?.open(json0) ?: run { q.liveWrite("$ASIDE$name:locked:$nowMillis", json0, nowMillis); continue }
                } else json0
                // A backup is not the app's own write: it may be from an older build, so the save goes through the app's check first.
                write(name, if (pokemonSplit(name)) normalizer?.invoke(json, nowMillis) ?: json else json, nowMillis)
            }
        }
    }

    /**
     * Copies the legacy documents ([save], [care]; null when the file does not exist) in, reads every one back and checks it
     * is byte-for-byte what was given, then sets the marker, all in one transaction: a failure leaves the database exactly as
     * it was and the files stay the live store. Already active: does nothing, so it can be called on every start.
     */
    fun activate(save: String?, care: String?, nowMillis: Long): Activation {
        if (isActive()) return Activation.AlreadyActive
        val docs = listOfNotNull(save?.let { SAVE to it }, care?.let { CARE to it })
        store.transaction {
            for ((name, json) in docs) {
                q.liveWrite(name, json, nowMillis)
                check(q.liveRead(name).executeAsOne() == json) { "the copy of '$name' did not read back equal" }
            }
            q.setMeta(META_LIVE, LIVE_DATABASE)
            q.setMeta(META_LIVE_AT, nowMillis.toString())
        }
        return Activation.Imported(docs.map { it.first })
    }

    companion object {
        private val JSON = Json { encodeDefaults = true }
        /** What a read back cannot tell apart is left out of both sides of a comparison (see PlanTables and PokemonTables). */
        internal fun canonical(save: JsonObject): JsonObject = PokemonTables.canonical(PlanTables.canonical(save))

        const val ASIDE = "aside:"
        const val SAVE = "save"
        const val CARE = "care"
        const val HEAL = "heal"
        const val MINT = "mint"
        const val HOME = "home"
        const val LINK = "link"
        const val META_LIVE = "live_store"
        const val META_LIVE_AT = "live_store_since"
        const val LIVE_DATABASE = "database"
    }
}
