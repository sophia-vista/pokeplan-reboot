package app.pokeplan.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

object BackupBundle {
    const val FORMAT = "pokelife-backup"
    const val VERSION = 1

    val KNOWN = setOf(LiveDocuments.SAVE, LiveDocuments.CARE, LiveDocuments.HEAL, LiveDocuments.MINT, LiveDocuments.BASE, LiveDocuments.LINK)

    val SEALED = listOf(LiveDocuments.MINT, LiveDocuments.HEAL)

    sealed interface Staged {
        data class Ready(val documents: Map<String, String>, val schema: Int, val createdAtMillis: Long) : Staged {
            val needsPassphrase: Boolean get() = SEALED.any { documents[it]?.let(PassphraseSealer::isSealed) == true }
        }

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
            val ok = if (name in SEALED && (RowCipher.isSealed(doc) || PassphraseSealer.isSealed(doc))) true else try { json.parseToJsonElement(doc) is JsonObject } catch (e: Exception) { false }
            if (!ok) return Staged.Refused("The $name in that backup is damaged.")
        }
        return Staged.Ready(documents, schema, (root["createdAtMillis"] as? JsonPrimitive)?.longOrNull ?: 0L)
    }

    fun portable(documents: Map<String, String>, devices: Map<String, RowCipher>, passphrase: String, sealer: PassphraseSealer): Map<String, String>? {
        var out = documents
        for (name in SEALED) {
            val sealed = documents[name] ?: continue
            val plain = if (RowCipher.isSealed(sealed)) devices[name]?.open(sealed) ?: return null else if (PassphraseSealer.isSealed(sealed)) continue else sealed
            out = out + (name to sealer.seal(plain, passphrase))
        }
        return out
    }

    fun unlock(ready: Staged.Ready, passphrase: String?, sealer: PassphraseSealer): Map<String, String>? {
        if (!ready.needsPassphrase) return ready.documents
        var out = ready.documents
        for (name in SEALED) {
            val text = out[name] ?: continue
            if (!PassphraseSealer.isSealed(text)) continue
            out = out + (name to (passphrase?.let { sealer.open(text, it) } ?: return null))
        }
        return out
    }
}
