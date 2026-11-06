package app.pokeplan.data

/** A reminder held back from what the modules rebuild themselves: a snooze or a repeat. Opaque references and a time, nothing else. */
data class HeldReminder(
    val id: String, val module: String, val sourceRef: String, val kind: String, val occurrence: String,
    val atMillis: Long, val reason: String,
)

/**
 * The shared reminder feature's persistence (INTEGRATIONS_AND_PRIVACY.md sections 9.8 to 9.10): only what cannot be rebuilt from
 * the modules' own records. One row per logical reminder, so snoozing twice replaces the first snooze rather than adding another.
 */
class ReminderRepository(private val store: DataStore) {
    private val q get() = store.db.dataQueries

    fun hold(held: HeldReminder, nowMillis: Long) {
        store.transaction { q.holdPut(held.id, held.module, held.sourceRef, held.kind, held.occurrence, held.atMillis, held.reason, nowMillis) }
    }

    fun release(id: String) {
        store.transaction { q.holdDelete(id) }
    }

    fun all(): List<HeldReminder> = q.holdAll().executeAsList().map { HeldReminder(it.id, it.module, it.source_ref, it.kind, it.occurrence, it.at_millis, it.reason) }

    /** Forgets holds whose time has long passed (a snooze the phone slept through), so nothing builds up (section 9.3). */
    fun dropOlderThan(millis: Long) {
        store.transaction { q.holdDeleteBefore(millis) }
    }
}
