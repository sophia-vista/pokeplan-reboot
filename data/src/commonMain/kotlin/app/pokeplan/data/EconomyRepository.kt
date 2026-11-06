package app.pokeplan.data

/** A spend or reversal that would take the Poké balance or a Bag stack below zero. Nothing was changed. */
class InsufficientFundsException(val asset: String, val have: Long, val delta: Long) :
    IllegalStateException("not enough $asset: have $have, change $delta")

/** An event to settle once. [eventId] is the idempotency key (`<module>:<source record>:<kind>[:<occurrence>]`). */
data class RewardEventInput(
    val eventId: String,
    val sourceModule: String,
    val sourceRecordId: String,
    val eventKind: String,
    val policyVersion: String,
    val occurredAt: Long,
    val poke: Long = 0,
    val items: Map<String, Long> = emptyMap(),
    val metadataJson: String? = null,
    /** A minimal, privacy-free payload for downstream consumers, written in the same transaction. */
    val outboxJson: String? = null,
)

data class LedgerEntry(val entryId: Long, val eventId: String, val asset: String, val delta: Long, val reversesEntryId: Long?)

sealed interface Settlement {
    val eventId: String
    val entries: List<LedgerEntry>

    /** The event was new and has been applied. */
    data class Applied(override val eventId: String, override val entries: List<LedgerEntry>) : Settlement

    /** The same key had been settled before: its recorded result is returned and nothing changed. */
    data class AlreadySettled(override val eventId: String, override val entries: List<LedgerEntry>) : Settlement
}

/**
 * The one Bag, the one Poké balance and the append-only ledger behind them.
 *
 * Balances change only here, only with a ledger entry in the same transaction (DATA_AND_MIGRATIONS.md section 8).
 */
class EconomyRepository internal constructor(private val store: DataStore) {
    private val q get() = store.db.dataQueries

    fun poke(): Long = q.pokeBalance().executeAsOneOrNull() ?: 0L

    fun bag(): Map<String, Long> = q.bag().executeAsList().associate { it.item_id to it.quantity }

    fun quantity(itemId: String): Long = q.bagQuantity(itemId).executeAsOneOrNull() ?: 0L

    fun ledgerOf(eventId: String): List<LedgerEntry> = q.ledgerOfEvent(eventId).executeAsList().map {
        LedgerEntry(it.entry_id, it.event_id, it.asset, it.delta, it.reverses_entry_id)
    }

    fun hasEvent(eventId: String): Boolean = q.eventById(eventId).executeAsOneOrNull() != null

    /**
     * Settles [event] atomically: records it, appends its ledger entries and moves the balances. Settling a key that
     * already exists returns its recorded entries and changes nothing, so a retry after a crash cannot pay twice.
     * A change that would go below zero throws [InsufficientFundsException] and leaves everything as it was.
     */
    fun settle(event: RewardEventInput): Settlement = store.transaction {
        if (hasEvent(event.eventId)) return@transaction Settlement.AlreadySettled(event.eventId, ledgerOf(event.eventId))
        val deltas = buildMap {
            if (event.poke != 0L) put(Assets.POKE, event.poke)
            event.items.filterValues { it != 0L }.forEach { (id, n) -> put(Assets.item(id), n) }
        }
        apply(event.eventId, event.sourceModule, event.sourceRecordId, event.eventKind, event.policyVersion, event.occurredAt, event.metadataJson, deltas.mapValues { it.value to null })
        event.outboxJson?.let { q.insertOutbox("out:${event.eventId}", event.eventKind, 1, it, event.occurredAt) }
        Settlement.Applied(event.eventId, ledgerOf(event.eventId))
    }

    /**
     * Reverses a settled event with a compensating event of its own (it never reuses the original key and never edits
     * the ledger). An event can be reversed once.
     */
    fun reverse(reversalEventId: String, originalEventId: String, reason: String, occurredAt: Long): Settlement = store.transaction {
        if (hasEvent(reversalEventId)) return@transaction Settlement.AlreadySettled(reversalEventId, ledgerOf(reversalEventId))
        val original = ledgerOf(originalEventId)
        check(original.isNotEmpty()) { "nothing to reverse: $originalEventId has no ledger entries" }
        check(original.none { q.ledgerReversalOf(it.entryId).executeAsList().isNotEmpty() }) { "$originalEventId was already reversed" }
        apply(
            reversalEventId, "ECONOMY", originalEventId, "reversal", q.eventById(originalEventId).executeAsOne().policy_version, occurredAt,
            """{"reverses":"$originalEventId","reason":${jsonString(reason)}}""",
            original.associate { it.asset to (-it.delta to it.entryId) },
        )
        Settlement.Applied(reversalEventId, ledgerOf(reversalEventId))
    }

    /** Where the stored balances disagree with the ledger; empty means they reconcile. */
    fun reconcile(): List<String> {
        val totals = q.ledgerTotals().executeAsList().associate { it.asset to (it.total ?: 0L) }
        val problems = mutableListOf<String>()
        if (poke() != (totals[Assets.POKE] ?: 0L)) problems += "poke: balance ${poke()} but the ledger sums to ${totals[Assets.POKE] ?: 0L}"
        val bag = bag()
        for (id in (bag.keys + totals.keys.filter { it.startsWith("item:") }.map { it.removePrefix("item:") }).toSortedSet()) {
            val ledger = totals[Assets.item(id)] ?: 0L
            if ((bag[id] ?: 0L) != ledger) problems += "item $id: bag ${bag[id] ?: 0L} but the ledger sums to $ledger"
        }
        return problems
    }

    private fun apply(
        eventId: String, module: String, record: String, kind: String, policy: String, at: Long, metadata: String?,
        deltas: Map<String, Pair<Long, Long?>>,
    ) {
        q.ensureBalance()
        for ((asset, d) in deltas) {
            val have = if (asset == Assets.POKE) poke() else quantity(asset.removePrefix("item:"))
            if (have + d.first < 0) throw InsufficientFundsException(asset, have, d.first)
        }
        q.insertEvent(eventId, module, record, kind, policy, at, metadata)
        for ((asset, d) in deltas) {
            q.insertLedgerEntry(eventId, asset, d.first, d.second)
            if (asset == Assets.POKE) q.addPoke(d.first) else {
                val item = asset.removePrefix("item:")
                q.ensureStack(item)
                q.addToBag(d.first, item)
            }
        }
        q.dropEmptyStacks()
    }
}

internal fun jsonString(s: String): String = buildString {
    append('"')
    for (c in s) when {
        c == '"' -> append("\\\"")
        c == '\\' -> append("\\\\")
        c < ' ' -> append("\\u" + c.code.toString(16).padStart(4, '0'))
        else -> append(c)
    }
    append('"')
}
