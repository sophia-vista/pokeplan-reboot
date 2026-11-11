package app.pokeplan.app

import kotlin.concurrent.Volatile
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
    private val shareKey = storage.shareKey

    /** The shared document's revision this copy matches (see [SharedDocuments]); unused for a storage nothing else opens. */
    private var seen = 0L

    @Volatile
    var value: T = lock.withLock { open(empty).also { markCurrent() } }
        private set

    private val listeners = mutableListOf<(T) -> Unit>()

    /**
     * The saved document. One that cannot be read is kept aside, and the storage's copy from before its last write is used instead
     * (and written back, so the next write keeps a good copy, not the damaged one); only without one does the document start empty.
     */
    private fun open(empty: () -> T): T {
        shareKey?.let { SharedDocuments.unsaved(it) }?.let(::decode)?.let { return it }
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
        lock.withLock {
            catchUpLocked()
            writeLocked(change(value))
        }
        notifyListeners()
    }

    /**
     * Picks up what another store in this process wrote to the same document (a widget's tap, a notification's "Done") and tells the
     * listeners. Cheap when nothing was written, so it can be called on every tick. Returns whether anything changed.
     */
    fun sync(): Boolean {
        val changed = lock.withLock { catchUpLocked() }
        if (changed) notifyListeners()
        return changed
    }

    // If another store wrote the document since this one last read or wrote it, this copy is old: a change must start from the saved one.
    private fun catchUpLocked(): Boolean {
        val key = shareKey ?: return false
        if (SharedDocuments.revision(key) == seen) return false
        markCurrent()
        val fresh = current() ?: return false
        if (fresh == value) return false
        value = fresh
        return true
    }

    // The document as every store in the process should see it: what could not be saved yet, else what is on the disk.
    private fun current(): T? = (shareKey?.let { SharedDocuments.unsaved(it) } ?: storage.load())?.let(::decode)

    private fun markCurrent() { shareKey?.let { seen = SharedDocuments.revision(it) } }

    /** Saves [next] and tells the listeners. */
    fun write(next: T) = update { next }

    // The memory follows the disk: a save that throws leaves the document, and every listener, as it was.
    private fun writeLocked(next: T) {
        val text = json.encodeToString(serializer, next)
        val onDisk = storage.saved(text)
        value = next
        shareKey?.let { SharedDocuments.keep(it, text.takeUnless { onDisk }); seen = SharedDocuments.written(it) }
    }

    /**
     * Picks up a change another process made to the save (an Android widget). Nothing is written back, so the app never
     * overwrites a widget's tap with its older copy, and a save caught half-written changes nothing.
     */
    fun reload() {
        val changed = lock.withLock {
            markCurrent()
            val fresh = current()
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
