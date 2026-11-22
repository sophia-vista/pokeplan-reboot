package app.pokeplan.data

data class HeldReminder(
    val id: String, val module: String, val sourceRef: String, val kind: String, val occurrence: String,
    val atMillis: Long, val reason: String,
)

class ReminderRepository(private val store: DataStore) {
    private val q get() = store.db.dataQueries

    fun hold(held: HeldReminder, nowMillis: Long) {
        store.transaction { q.holdPut(held.id, held.module, held.sourceRef, held.kind, held.occurrence, held.atMillis, held.reason, nowMillis) }
    }

    fun release(id: String) {
        store.transaction { q.holdDelete(id) }
    }

    fun all(): List<HeldReminder> = q.holdAll().executeAsList().map { HeldReminder(it.id, it.module, it.source_ref, it.kind, it.occurrence, it.at_millis, it.reason) }

    fun dropOlderThan(millis: Long) {
        store.transaction { q.holdDeleteBefore(millis) }
    }
}
