package app.pokeplan.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * A backup as one file the player can keep anywhere (docs/pokelife `ARCHITECTURE/DATA_AND_MIGRATIONS.md` section 10): the live documents
 * (`save`, `care`) exactly as the app serialises them, a format version, the database schema they were taken at, the time, and a checksum
 * of the documents. It is the same JSON the app has always saved, so it can also be read by hand.
 *
 * Restoring is staged: [stage] reads and checks the whole file first and returns either the documents or the reason it was refused, and only
 * a [Staged] result is handed to `LiveDocuments.replaceAll`, which swaps in one transaction and keeps what it replaces. A file that is
 * damaged, cut short, from a newer build or not a backup at all changes nothing.
 */
object BackupBundle {
    const val FORMAT = "pokelife-backup"
    const val VERSION = 1

    /** The documents a bundle may carry; anything else is refused rather than written into the live store. */
    val KNOWN = setOf(LiveDocuments.SAVE, LiveDocuments.CARE, LiveDocuments.HEAL, LiveDocuments.MINT)

    sealed interface Staged {
        /** A bundle that passed every check: [documents] are ready to swap in. */
        data class Ready(val documents: Map<String, String>, val schema: Int, val createdAtMillis: Long) : Staged

        /** A bundle that was refused, with a reason in the player's words. Nothing was changed. */
        data class Refused(val reason: String) : Staged
    }

    private val json = Json { prettyPrint = false }

    private fun checksum(documents: Map<String, String>): String =
        Sha256.hex(documents.toSortedMap().entries.joinToString("\u0000") { "${it.key}\u0001${it.value}" })

    fun encode(documents: Map<String, String>, schema: Int, createdAtMillis: Long): String {
        val docs = documents.filterKeys { it in KNOWN }
        return json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("format", FORMAT); put("version", VERSION); put("schema", schema); put("createdAtMillis", createdAtMillis)
            put("sha256", checksum(docs))
            put("documents", buildJsonObject { for ((name, text) in docs.toSortedMap()) put(name, text) })
        })
    }

    /** Reads and checks [text] against the schema this build understands ([supportedSchema]). */
    fun stage(text: String, supportedSchema: Int): Staged {
        val root = try { json.parseToJsonElement(text) as? JsonObject } catch (e: Exception) { null }
            ?: return Staged.Refused("That file is not a PokéLife backup, or it was cut short.")
        if ((root["format"] as? JsonPrimitive)?.contentOrNull != FORMAT) return Staged.Refused("That file is not a PokéLife backup.")
        val version = (root["version"] as? JsonPrimitive)?.intOrNull ?: return Staged.Refused("That backup has no version.")
        if (version > VERSION) return Staged.Refused("That backup was made by a newer PokéLife. Update the app and try again.")
        val schema = (root["schema"] as? JsonPrimitive)?.intOrNull ?: return Staged.Refused("That backup has no schema version.")
        if (schema > supportedSchema) return Staged.Refused("That backup was made by a newer PokéLife. Update the app and try again.")
        val docs = (root["documents"] as? JsonObject) ?: return Staged.Refused("That backup holds no data.")
        val documents = docs.mapValues { (_, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return Staged.Refused("That backup is damaged.") }
        if (documents.isEmpty()) return Staged.Refused("That backup holds no data.")
        if (documents.keys.any { it !in KNOWN }) return Staged.Refused("That backup holds something this version does not know.")
        if (checksum(documents) != (root["sha256"] as? JsonPrimitive)?.contentOrNull) return Staged.Refused("That backup is damaged: its checksum does not match.")
        for ((name, doc) in documents) {
            val ok = try { json.parseToJsonElement(doc) is JsonObject } catch (e: Exception) { false }
            if (!ok) return Staged.Refused("The $name in that backup is damaged.")
        }
        return Staged.Ready(documents, schema, (root["createdAtMillis"] as? JsonPrimitive)?.longOrNull ?: 0L)
    }
}
