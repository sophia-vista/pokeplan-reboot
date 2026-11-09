package app.pokeplan.app

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * A store's JSON document in [storage], and the listeners that follow it. This is the one place a save is read: an
 * unreadable one (damaged, or holding something the rules reject) never blocks the app, and never gets overwritten
 * either. It is kept aside with [Storage.keepAside] and the document starts again as [empty]. A readable one goes
 * through [tidy], the store's own check of what it lets in.
 */
internal class SavedDocument<T>(
    private val storage: Storage,
    private val serializer: KSerializer<T>,
    private val json: Json,
    private val tidy: (T) -> T = { it },
    empty: () -> T,
) {
    private val lock = newStoreLock()

    @Volatile
    var value: T = open(empty)
        private set

    private val listeners = mutableListOf<(T) -> Unit>()

    /**
     * The saved document. One that cannot be read is kept aside, and the storage's copy from before its last write is used instead
     * (and written back, so the next write keeps a good copy, not the damaged one); only without one does the document start empty.
     */
    private fun open(empty: () -> T): T {
        val text = storage.load() ?: return empty()
        decode(text)?.let { return it }
        storage.keepAside(text)
        val backup = storage.loadBackup() ?: return empty()
        val restored = decode(backup) ?: return empty()
        storage.save(backup)
        return restored
    }

    /** Calls [listener] after every change. Returns a function that stops listening. */
    fun observe(listener: (T) -> Unit): () -> Unit {
        lock.withLock { listeners += listener }
        return { lock.withLock { listeners -= listener } }
    }

    /**
     * Makes the next document from the current one with [change] and saves it, as one step: another thread's change waits, so
     * neither starts from a document the other has already replaced. Listeners hear of it after the lock is let go.
     */
    fun update(change: (T) -> T) {
        lock.withLock { writeLocked(change(value)) }
        notifyListeners()
    }

    /** Saves [next] and tells the listeners. */
    fun write(next: T) = update { next }

    // The memory follows the disk: a save that throws leaves the document, and every listener, as it was.
    private fun writeLocked(next: T) {
        storage.save(json.encodeToString(serializer, next))
        value = next
    }

    /**
     * Picks up a change another process made to the save (an Android widget). Nothing is written back, so the app never
     * overwrites a widget's tap with its older copy, and a save caught half-written changes nothing.
     */
    fun reload() {
        val changed = lock.withLock {
            val fresh = storage.load()?.let(::decode)
            if (fresh == null || fresh == value) false else { value = fresh; true }
        }
        if (changed) notifyListeners()
    }

    private fun notifyListeners() = lock.withLock { listeners.toList() }.forEach { it(value) }

    // A SerializationException is an IllegalArgumentException, and so is a rule's `require` failing on a decoded value.
    private fun decode(text: String): T? = try {
        tidy(json.decodeFromString(serializer, text))
    } catch (e: IllegalArgumentException) {
        null
    }
}

/** A new id for something a store makes: the time and a random part, so two devices never make the same one. */
internal fun newId(nowMillis: Long, rng: Random): String = "${nowMillis.toString(36)}-${rng.nextInt(Int.MAX_VALUE).toString(36)}"
