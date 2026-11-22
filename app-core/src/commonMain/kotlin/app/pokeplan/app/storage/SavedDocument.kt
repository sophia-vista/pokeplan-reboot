package app.pokeplan.app.storage

import kotlin.concurrent.Volatile
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.random.Random

internal class SavedDocument<T>(
    private val storage: Storage,
    private val serializer: KSerializer<T>,
    private val json: Json,
    private val tidy: (T) -> T = { it },
    empty: () -> T,
) {
    private val lock = newStoreLock()
    private val shareKey = storage.shareKey

    private var seen = 0L

    @Volatile
    var value: T = lock.withLock { open(empty).also { markCurrent() } }
        private set

    private val listeners = mutableListOf<(T) -> Unit>()

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

    fun observe(listener: (T) -> Unit): () -> Unit {
        lock.withLock { listeners += listener }
        return { lock.withLock { listeners -= listener } }
    }

    fun update(change: (T) -> T) {
        lock.withLock {
            catchUpLocked()
            writeLocked(change(value))
        }
        notifyListeners()
    }

    fun sync(): Boolean {
        val changed = lock.withLock { catchUpLocked() }
        if (changed) notifyListeners()
        return changed
    }

    private fun catchUpLocked(): Boolean {
        val key = shareKey ?: return false
        if (SharedDocuments.revision(key) == seen) return false
        markCurrent()
        val fresh = current() ?: return false
        if (fresh == value) return false
        value = fresh
        return true
    }

    private fun current(): T? = (shareKey?.let { SharedDocuments.unsaved(it) } ?: storage.load())?.let(::decode)

    private fun markCurrent() { shareKey?.let { seen = SharedDocuments.revision(it) } }

    fun write(next: T) = update { next }

    private fun writeLocked(next: T) {
        val text = json.encodeToString(serializer, next)
        val onDisk = storage.saved(text)
        value = next
        shareKey?.let { SharedDocuments.keep(it, text.takeUnless { onDisk }); seen = SharedDocuments.written(it) }
    }

    fun reload() {
        val changed = lock.withLock {
            markCurrent()
            val fresh = current()
            if (fresh == null || fresh == value) false else { value = fresh; true }
        }
        if (changed) notifyListeners()
    }

    private fun notifyListeners() = lock.withLock { listeners.toList() }.forEach { it(value) }

    private fun decode(text: String): T? = try {
        tidy(json.decodeFromString(serializer, text))
    } catch (e: IllegalArgumentException) {
        null
    }
}

internal fun newId(nowMillis: Long, rng: Random): String = "${nowMillis.toString(36)}-${rng.nextInt(Int.MAX_VALUE).toString(36)}"
