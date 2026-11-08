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
    val KNOWN = setOf(LiveDocuments.SAVE, LiveDocuments.CARE, LiveDocuments.HEAL, LiveDocuments.MINT, LiveDocuments.HOME, LiveDocuments.LINK)

    /** The documents that leave the phone sealed: Mint always, Heal since 0.152.0 (they are sealed to this phone's keys, or under a passphrase for a portable backup). */
    val SEALED = listOf(LiveDocuments.MINT, LiveDocuments.HEAL)

    sealed interface Staged {
        /** A bundle that passed every check: [documents] are ready to swap in. */
        data class Ready(val documents: Map<String, String>, val schema: Int, val createdAtMillis: Long) : Staged {
            /** The Mint in this backup is sealed under a passphrase (a portable backup): it must be given before [unlock] can open it. */
            val needsPassphrase: Boolean get() = SEALED.any { documents[it]?.let(PassphraseSealer::isSealed) == true }
        }

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
            val ok = if (name in SEALED && (RowCipher.isSealed(doc) || PassphraseSealer.isSealed(doc))) true else try { json.parseToJsonElement(doc) is JsonObject } catch (e: Exception) { false }
            if (!ok) return Staged.Refused("The $name in that backup is damaged.")
        }
        return Staged.Ready(documents, schema, (root["createdAtMillis"] as? JsonPrimitive)?.longOrNull ?: 0L)
    }

    /**
     * [documents] with Mint and Heal re-sealed under [passphrase], for a backup that opens on any phone. They arrive from a snapshot or a daily backup sealed with this
     * phone's keys ([devices], by document name), so each is opened with its key first; null when one cannot be opened here (it came from another phone's key, or the
     * key is not open on this run), because then there is nothing this phone can honestly re-seal. A document that is not sealed (an older backup's, or Heal on a phone
     * whose key was not available) is sealed under the passphrase as it is; a backup with neither is returned as it is. The passphrase is checked by
     * [PassphraseSealer.refusal] before this is called.
     */
    fun portable(documents: Map<String, String>, devices: Map<String, RowCipher>, passphrase: String, sealer: PassphraseSealer): Map<String, String>? {
        var out = documents
        for (name in SEALED) {
            val sealed = documents[name] ?: continue
            val plain = if (RowCipher.isSealed(sealed)) devices[name]?.open(sealed) ?: return null else if (PassphraseSealer.isSealed(sealed)) continue else sealed
            out = out + (name to sealer.seal(plain, passphrase))
        }
        return out
    }

    /**
     * The documents of a staged backup ready to be put back. A portable Mint or Heal is opened with [passphrase] and handed over as its plain JSON, which
     * `LiveDocuments.replaceAll` seals again with this phone's key as it writes it; null when the passphrase is missing or wrong, so nothing is changed.
     */
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
