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
    var value: T = storage.load()?.let { text -> decode(text) ?: empty().also { storage.keepAside(text) } } ?: empty()
        private set

    private val listeners = mutableListOf<(T) -> Unit>()

    /** Calls [listener] after every change. Returns a function that stops listening. */
    fun observe(listener: (T) -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    /** Saves [next] and tells the listeners. */
    fun write(next: T) {
        value = next
        storage.save(json.encodeToString(serializer, next))
        notifyListeners()
    }

    /**
     * Picks up a change another process made to the save (an Android widget). Nothing is written back, so the app never
     * overwrites a widget's tap with its older copy, and a save caught half-written changes nothing.
     */
    fun reload() {
        val fresh = storage.load()?.let(::decode) ?: return
        if (fresh == value) return
        value = fresh
        notifyListeners()
    }

    private fun notifyListeners() = listeners.toList().forEach { it(value) }

    // A SerializationException is an IllegalArgumentException, and so is a rule's `require` failing on a decoded value.
    private fun decode(text: String): T? = try {
        tidy(json.decodeFromString(serializer, text))
    } catch (e: IllegalArgumentException) {
        null
    }
}

/** A new id for something a store makes: the time and a random part, so two devices never make the same one. */
internal fun newId(nowMillis: Long, rng: Random): String = "${nowMillis.toString(36)}-${rng.nextInt(Int.MAX_VALUE).toString(36)}"
