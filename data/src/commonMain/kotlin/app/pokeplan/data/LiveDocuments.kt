package app.pokeplan.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

sealed interface Activation {
    data object AlreadyActive : Activation

    data class Imported(val documents: List<String>) : Activation
}

class LiveDocuments(private val store: DataStore) {
    private val q get() = store.db.dataQueries

    @Volatile var normalizer: ((String, Long) -> String)? = null

    fun isActive(): Boolean = q.meta(META_LIVE).executeAsOneOrNull() == LIVE_DATABASE

    private fun inTables(name: String): Boolean = (name == CARE && store.careTables.isActive()) || (name == HEAL && store.healTables.isActive()) || (name == MINT && store.mintTables.isActive()) || (name == BASE && store.baseTables.isActive()) || (name == LINK && store.linkTables.isActive())

    private fun planSplit(name: String): Boolean = name == SAVE && store.planTables.isActive()

    private fun pokemonSplit(name: String): Boolean = planSplit(name) && store.pokemonTables.isActive()

    fun read(name: String): String? = when {
        name == CARE && inTables(name) -> store.careTables.read()
        name == HEAL && inTables(name) -> store.healTables.readOrNull()
        name == MINT && inTables(name) -> store.mintTables.readOrNull()
        name == BASE && inTables(name) -> store.baseTables.read()
        name == LINK && inTables(name) -> store.linkTables.read()
        planSplit(name) -> store.transaction { assembleSave() }
        else -> q.liveRead(name).executeAsOneOrNull()
    }

    private fun assembleSave(): String? {
        val rest = q.liveRead(SAVE).executeAsOneOrNull()?.let { JSON.parseToJsonElement(it) as? JsonObject }
        val plan = store.planTables.read()
        val player = if (store.pokemonTables.isActive()) store.pokemonTables.read() else JsonObject(emptyMap())
        if (rest == null && plan.isEmpty() && player.isEmpty()) return null
        return JSON.encodeToString(JsonObject.serializer(), JsonObject((rest ?: JsonObject(emptyMap())) + plan + player))
    }

    fun write(name: String, json: String, nowMillis: Long) {
        when {
            name == CARE && inTables(name) -> store.careTables.write(json)
            name == HEAL && inTables(name) -> store.healTables.write(json)
            name == MINT && inTables(name) -> store.mintTables.write(json)
            name == BASE && inTables(name) -> store.baseTables.write(json)
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

    private fun writeRest(rest: JsonObject, nowMillis: Long) {
        val text = JSON.encodeToString(JsonObject.serializer(), rest)
        if (q.liveRead(SAVE).executeAsOneOrNull() != text) q.liveWrite(SAVE, text, nowMillis)
    }

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

    fun moveBaseToTables(nowMillis: Long) {
        if (store.baseTables.isActive()) return
        store.transaction {
            val doc = q.liveRead(BASE).executeAsOneOrNull()
            store.baseTables.activate(doc, nowMillis)
            if (doc != null) {
                q.liveWrite("${ASIDE}$BASE:tables:$nowMillis", doc, nowMillis)
                q.liveDelete(BASE)
            }
        }
    }

    fun keepAside(name: String, json: String, nowMillis: Long) {
        write("$ASIDE$name:$nowMillis", asideText(name, json), nowMillis)
    }

    private fun asideText(name: String, json: String): String = when {
        name == MINT -> store.mintCipher!!.seal(json)
        name == HEAL && store.healTables.isSealed() -> (store.healCipher ?: throw HealLockedException()).seal(json)
        else -> json
    }

    private fun cipherFor(name: String): RowCipher? = when (name) {
        MINT -> store.mintCipher
        HEAL -> store.healCipher
        else -> null
    }

    fun eraseKeptCopiesAndHolds() = store.transaction {
        q.liveDeleteAside()
        q.holdDeleteAll()
    }

    fun names(): List<String> = (q.liveNames().executeAsList() + listOfNotNull(CARE.takeIf { store.careTables.isActive() && store.careTables.read() != null }, HEAL.takeIf { store.healTables.isActive() && store.healTables.readOrNull() != null }, MINT.takeIf { store.mintTables.isActive() && store.mintTables.readOrNull() != null }, BASE.takeIf { store.baseTables.isActive() && store.baseTables.read() != null }, LINK.takeIf { store.linkTables.isActive() && store.linkTables.read() != null })).distinct().sorted()

    fun snapshot(): Map<String, String> = store.transaction {
        val docs = q.liveNames().executeAsList().filter { !it.startsWith(ASIDE) }.associateWith { q.liveRead(it).executeAsOne() }.toMutableMap()
        if (store.careTables.isActive()) store.careTables.read()?.let { docs[CARE] = it }
        if (store.healTables.isActive()) store.healTables.snapshot()?.let { docs[HEAL] = it }
        if (store.baseTables.isActive()) store.baseTables.read()?.let { docs[BASE] = it }
        if (store.linkTables.isActive()) store.linkTables.read()?.let { docs[LINK] = it }
        if (store.mintTables.isActive()) store.mintTables.sealedSnapshot()?.let { docs[MINT] = it }
        if (store.planTables.isActive()) assembleSave()?.let { docs[SAVE] = it }
        docs
    }

    fun replaceAll(docs: Map<String, String>, nowMillis: Long): List<String> {
        val left = listOf(SAVE, CARE, HEAL, MINT, BASE, LINK).filter { it !in docs && read(it) != null }.toMutableList()
        store.transaction {
            for ((name, json0) in docs) {
                read(name)?.let { q.liveWrite("$ASIDE$name:$nowMillis", asideText(name, it), nowMillis) }
                val json = if ((name == MINT || name == HEAL) && RowCipher.isSealed(json0)) {
                    cipherFor(name)?.open(json0) ?: run { q.liveWrite("$ASIDE$name:locked:$nowMillis", json0, nowMillis); left += name; continue }
                } else json0
                write(name, if (pokemonSplit(name)) normalizer?.invoke(json, nowMillis) ?: json else json, nowMillis)
            }
        }
        return left
    }

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
        internal fun canonical(save: JsonObject): JsonObject = PokemonTables.canonical(PlanTables.canonical(save))

        const val ASIDE = "aside:"
        const val SAVE = "save"
        const val CARE = "care"
        const val HEAL = "heal"
        const val MINT = "mint"
        const val BASE = "home"
        const val LINK = "link"
        const val INTEGRATIONS = "integrations"
        const val META_LIVE = "live_store"
        const val META_LIVE_AT = "live_store_since"
        const val LIVE_DATABASE = "database"
    }
}
